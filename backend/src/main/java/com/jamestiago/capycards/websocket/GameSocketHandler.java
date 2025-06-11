package com.jamestiago.capycards.websocket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameStateMapper;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.commands.GameCommand;
import com.jamestiago.capycards.game.commands.GameOverCommand;
import com.jamestiago.capycards.game.dto.GameLobbyInfo;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import com.jamestiago.capycards.service.GameService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class GameSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(GameSocketHandler.class);
    private final SocketIOServer server;
    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final Map<String, GameLobbyInfo> openLobbies = new ConcurrentHashMap<>();
    // This map is now informational. The source of truth for player-session mapping
    // is on the socket object itself.
    private final Map<UUID, String> clientToGameOrLobbyIdMap = new ConcurrentHashMap<>();
    private final Lock lobbyLock = new ReentrantLock();

    @Autowired
    public GameSocketHandler(SocketIOServer server, GameService gameService, ObjectMapper objectMapper) {
        this.server = server;
        this.gameService = gameService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void init() {
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());

        server.addEventListener("create_lobby", CreateLobbyRequest.class, onCreateLobbyRequested());
        server.addEventListener("get_open_lobbies", Object.class, onGetOpenLobbiesRequested());
        server.addEventListener("join_lobby", JoinLobbyRequest.class, onJoinLobbyRequested());
        server.addEventListener("game_command", Map.class, onGameCommandReceived());
        server.addEventListener("create_ai_game", CreateLobbyRequest.class, onCreateAiGameRequested());

        try {
            server.start();
            logger.info("Socket.IO server started successfully.");
        } catch (Exception e) {
            logger.error("Could not start Socket.IO server", e);
        }
    }

    @PreDestroy
    private void destroy() {
        if (server != null) {
            server.stop();
            logger.info("Socket.IO server stopped.");
        }
    }

    private ConnectListener onConnected() {
        return client -> {
            logger.info("CLIENT CONNECTED: {} from IP: {}", client.getSessionId(), client.getRemoteAddress());
            client.sendEvent("connection_ack", "Connection successful. Welcome to CapyCards.");
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            UUID sessionId = client.getSessionId();
            logger.info("CLIENT DISCONNECTED: {}", sessionId);

            String gameOrLobbyId = clientToGameOrLobbyIdMap.get(sessionId);
            String disconnectedPlayerId = client.get("playerId");

            if (gameOrLobbyId != null) {
                lobbyLock.lock();
                try {
                    GameLobbyInfo openLobby = openLobbies.get(gameOrLobbyId);
                    if (openLobby != null && openLobby.getCreatorSessionId() != null
                            && openLobby.getCreatorSessionId().equals(sessionId)) {
                        openLobbies.remove(gameOrLobbyId);
                        logger.info("Lobby {} removed because creator disconnected.", gameOrLobbyId);
                        broadcastOpenLobbiesUpdate();
                    }
                } finally {
                    lobbyLock.unlock();
                }

                if (disconnectedPlayerId != null) {
                    Game game = gameService.getGame(gameOrLobbyId);
                    if (game != null && !game.getGameState().name().contains("GAME_OVER")) {
                        MDC.put("gameId", game.getGameId());
                        try {
                            Player disconnectedPlayer = game.getPlayerById(disconnectedPlayerId);
                            if (disconnectedPlayer != null) {
                                logger.warn("Player {} ({}) disconnected from active game {}. Triggering forfeit.",
                                        disconnectedPlayer.getDisplayName(), sessionId, gameOrLobbyId);
                                Player opponent = game.getOpponent(disconnectedPlayer);
                                if (opponent != null) {
                                    String reason = disconnectedPlayer.getDisplayName() + " has disconnected.";
                                    GameOverCommand forfeitCommand = new GameOverCommand(game.getGameId(),
                                            opponent.getPlayerId(), reason);
                                    gameService.handleCommand(forfeitCommand);
                                    cleanupAfterGame(game);
                                }
                            }
                        } finally {
                            MDC.remove("gameId");
                        }
                    }
                }
            }
            clientToGameOrLobbyIdMap.remove(sessionId);
        };
    }

    private DataListener<CreateLobbyRequest> onCreateLobbyRequested() {
        return (client, data, ackSender) -> {
            UUID sessionId = client.getSessionId();
            String displayName = data.getDisplayName();
            logger.info("CREATE_LOBBY request from client: {} (Display Name: {})", sessionId, displayName);

            if (clientToGameOrLobbyIdMap.containsKey(sessionId)) {
                logger.warn("Client {} attempting to create lobby while already in a game/lobby.", sessionId);
                client.sendEvent("lobby_error", "You are already in a game or lobby.");
                return;
            }

            String lobbyId = UUID.randomUUID().toString();
            GameLobbyInfo newLobby = new GameLobbyInfo(lobbyId, displayName, sessionId);

            lobbyLock.lock();
            try {
                openLobbies.put(lobbyId, newLobby);
                clientToGameOrLobbyIdMap.put(sessionId, lobbyId);
            } finally {
                lobbyLock.unlock();
            }

            client.joinRoom(lobbyId);
            logger.info("Lobby {} created by {}. Client joined room.", lobbyId, displayName);
            client.sendEvent("lobby_created",
                    Map.of("lobbyId", lobbyId, "message", "Lobby created. Waiting for opponent."));
            broadcastOpenLobbiesUpdate();
        };
    }

    private DataListener<Object> onGetOpenLobbiesRequested() {
        return (client, data, ackSender) -> {
            logger.debug("Client {} requested list of open lobbies.", client.getSessionId());
            List<GameLobbyInfo> lobbies;
            lobbyLock.lock();
            try {
                lobbies = new ArrayList<>(openLobbies.values());
            } finally {
                lobbyLock.unlock();
            }
            client.sendEvent("open_lobbies_list", lobbies);
        };
    }

    private DataListener<JoinLobbyRequest> onJoinLobbyRequested() {
        return (client, data, ackSender) -> {
            UUID joinerSessionId = client.getSessionId();
            String lobbyIdToJoin = data.getLobbyId();
            String joinerDisplayName = data.getDisplayName();
            logger.info("JOIN_LOBBY request from client: {} to lobby: {}", joinerSessionId, lobbyIdToJoin);

            if (clientToGameOrLobbyIdMap.containsKey(joinerSessionId)) {
                client.sendEvent("lobby_error", "You are already in a game or lobby.");
                return;
            }

            GameLobbyInfo targetLobby;
            SocketIOClient creatorClient;

            lobbyLock.lock();
            try {
                targetLobby = openLobbies.remove(lobbyIdToJoin);
                if (targetLobby == null) {
                    client.sendEvent("join_lobby_failed", "Lobby not found or already full.");
                    return;
                }
                creatorClient = server.getClient(targetLobby.getCreatorSessionId());
                if (creatorClient == null || !creatorClient.isChannelOpen()) {
                    logger.error("Creator client for lobby {} not found or disconnected. Lobby is invalid.",
                            lobbyIdToJoin);
                    client.sendEvent("join_lobby_failed", "Lobby creator has disconnected.");
                    broadcastOpenLobbiesUpdate();
                    return;
                }
            } finally {
                lobbyLock.unlock();
            }

            broadcastOpenLobbiesUpdate();

            String creatorDisplayName = targetLobby.getCreatorDisplayName();
            Game game = gameService.createNewGame(creatorDisplayName, joinerDisplayName);
            String gameId = game.getGameId();

            MDC.put("gameId", gameId);
            try {
                Player p1 = game.getPlayer1();
                Player p2 = game.getPlayer2();

                // Store player ID on the socket session for later use
                creatorClient.set("playerId", p1.getPlayerId());
                clientToGameOrLobbyIdMap.put(creatorClient.getSessionId(), gameId);
                creatorClient.leaveRoom(lobbyIdToJoin);
                creatorClient.joinRoom(gameId);

                client.set("playerId", p2.getPlayerId());
                clientToGameOrLobbyIdMap.put(joinerSessionId, gameId);
                client.joinRoom(gameId);

                logger.info("Game ready. P1: {}, P2: {}. Broadcasting initial state and starting events.",
                        p1.getDisplayName(), p2.getDisplayName());

                gameService.startGame(game);

                GameStateResponse p1InitialState = GameStateMapper.createGameStateResponse(game, p1.getPlayerId(),
                        "Initial game state.");
                creatorClient.sendEvent("game_ready", p1InitialState);

                GameStateResponse p2InitialState = GameStateMapper.createGameStateResponse(game, p2.getPlayerId(),
                        "Initial game state.");
                client.sendEvent("game_ready", p2InitialState);

            } finally {
                MDC.remove("gameId");
            }
        };
    }

    private DataListener<CreateLobbyRequest> onCreateAiGameRequested() {
        return (client, data, ackSender) -> {
            UUID sessionId = client.getSessionId();
            String displayName = data.getDisplayName();
            logger.info("CREATE_AI_GAME request from client: {} (Display Name: {})", sessionId, displayName);

            if (clientToGameOrLobbyIdMap.containsKey(sessionId)) {
                logger.warn("Client {} attempting to create AI game while already in a game/lobby.", sessionId);
                client.sendEvent("lobby_error", "You are already in a game or lobby.");
                return;
            }

            Game game = gameService.createAiGame(displayName);
            String gameId = game.getGameId();
            MDC.put("gameId", gameId);
            try {
                Player humanPlayer = game.getPlayer1().isAi() ? game.getPlayer2() : game.getPlayer1();

                client.set("playerId", humanPlayer.getPlayerId());
                clientToGameOrLobbyIdMap.put(sessionId, gameId);
                client.joinRoom(gameId);

                logger.info("AI Game ready. Human: {}, AI: {}. Broadcasting initial state and starting events.",
                        humanPlayer.getDisplayName(), game.getOpponent(humanPlayer).getDisplayName());

                gameService.startGame(game);

                GameStateResponse initialState = GameStateMapper.createGameStateResponse(game,
                        humanPlayer.getPlayerId(), "AI game started.");
                client.sendEvent("game_ready", initialState);

            } finally {
                MDC.remove("gameId");
            }
        };
    }

    private DataListener<Map> onGameCommandReceived() {
        return (client, data, ackSender) -> {
            GameCommand command = null;
            try {
                command = objectMapper.convertValue(data, GameCommand.class);
                MDC.put("gameId", command.gameId);

                String expectedGameId = clientToGameOrLobbyIdMap.get(client.getSessionId());
                String expectedPlayerId = client.get("playerId");

                if (expectedPlayerId == null || !expectedPlayerId.equals(command.playerId)
                        || !command.gameId.equals(expectedGameId)) {
                    logger.warn("Session {} sent an unauthorized command. Denying.", client.getSessionId());
                    client.sendEvent("command_rejected", "Authorization failed for command.");
                    return;
                }

                gameService.handleCommand(command);

            } catch (Exception e) {
                logger.error("Error processing game_command from session {}. Data: {}", client.getSessionId(), data, e);
                client.sendEvent("command_rejected", "Internal server error processing your command.");
            } finally {
                MDC.remove("gameId");
            }
        };
    }

    private void broadcastOpenLobbiesUpdate() {
        List<GameLobbyInfo> lobbies;
        lobbyLock.lock();
        try {
            lobbies = new ArrayList<>(openLobbies.values());
        } finally {
            lobbyLock.unlock();
        }
        logger.debug("Broadcasting open_lobbies_update with {} lobbies.", lobbies.size());
        server.getBroadcastOperations().sendEvent("open_lobbies_update", lobbies);
    }

    private void cleanupAfterGame(Game game) {
        String gameId = game.getGameId();
        MDC.put("gameId", gameId);
        try {
            logger.info("Cleaning up sockets and mappings for ended game {}", gameId);
            server.getRoomOperations(gameId).getClients().forEach(client -> {
                clientToGameOrLobbyIdMap.remove(client.getSessionId());
                client.leaveRoom(gameId);
                logger.debug("Client {} removed from game {} mappings and room.", client.getSessionId(), gameId);
            });
            gameService.removeGame(gameId);
        } finally {
            MDC.remove("gameId");
        }
    }

    public static class CreateLobbyRequest {
        private String displayName;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class JoinLobbyRequest {
        private String lobbyId;
        private String displayName;

        public String getLobbyId() {
            return lobbyId;
        }

        public void setLobbyId(String lobbyId) {
            this.lobbyId = lobbyId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}