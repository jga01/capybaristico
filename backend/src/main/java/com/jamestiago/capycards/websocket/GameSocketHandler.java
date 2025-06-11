package com.jamestiago.capycards.websocket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameStateMapper;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.commands.GameCommand;
import com.jamestiago.capycards.game.commands.GameOverCommand;
import com.jamestiago.capycards.game.dto.GameLobbyInfo;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import com.jamestiago.capycards.game.events.GameEvent;
import com.jamestiago.capycards.game.events.PlayerDrewCardEvent;
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
import java.util.stream.Collectors;

@Component
public class GameSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(GameSocketHandler.class);
    private final SocketIOServer server;
    private final GameService gameService;
    private final ObjectMapper objectMapper; // Will be injected by Spring

    private final Map<String, GameLobbyInfo> openLobbies = new ConcurrentHashMap<>();
    private final Map<UUID, Player> clientToPlayerMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientToGameOrLobbyIdMap = new ConcurrentHashMap<>();
    private final Lock lobbyLock = new ReentrantLock();

    @Autowired
    public GameSocketHandler(SocketIOServer server, GameService gameService, ObjectMapper objectMapper) {
        this.server = server;
        this.gameService = gameService;
        this.objectMapper = objectMapper; // Use the injected, fully-configured mapper
    }

    @PostConstruct
    private void init() {
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());

        server.addEventListener("create_lobby", CreateLobbyRequest.class, onCreateLobbyRequested());
        server.addEventListener("get_open_lobbies", Object.class, onGetOpenLobbiesRequested());
        server.addEventListener("join_lobby", JoinLobbyRequest.class, onJoinLobbyRequested());
        server.addEventListener("game_command", Map.class, onGameCommandReceived());

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

    /**
     * Converts a list of GameEvent objects to a list of Maps.
     * This uses the Spring-configured ObjectMapper to ensure that polymorphic
     * type information (like 'eventType') is correctly included.
     * 
     * @param events The list of GameEvent objects.
     * @return A list of Maps ready for serialization.
     */
    private List<Map<String, Object>> convertEventsToMapList(List<GameEvent> events) {
        return events.stream()
                .map(event -> objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() {
                }))
                .collect(Collectors.toList());
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
            Player disconnectedPlayer = clientToPlayerMap.get(sessionId);

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

                if (disconnectedPlayer != null) {
                    Game game = gameService.getGame(gameOrLobbyId);
                    if (game != null && !game.getGameState().name().contains("GAME_OVER")) {
                        // Put gameId in MDC for this disconnection event
                        MDC.put("gameId", game.getGameId());
                        try {
                            logger.warn("Player {} ({}) disconnected from active game {}. Triggering forfeit.",
                                    disconnectedPlayer.getDisplayName(), sessionId, gameOrLobbyId);

                            Player opponent = game.getOpponent(disconnectedPlayer);
                            if (opponent != null) {
                                String reason = disconnectedPlayer.getDisplayName() + " has disconnected.";
                                GameOverCommand forfeitCommand = new GameOverCommand(game.getGameId(),
                                        opponent.getPlayerId(), reason);

                                List<GameEvent> forfeitEvents = gameService.handleCommand(forfeitCommand);
                                if (!forfeitEvents.isEmpty()) {
                                    List<Map<String, Object>> eventData = convertEventsToMapList(forfeitEvents);
                                    logger.debug("Broadcasting DISCONNECT events to game room {}: {}", game.getGameId(),
                                            eventData);
                                    server.getRoomOperations(game.getGameId()).sendEvent("game_events", eventData);
                                    cleanupAfterGame(game);
                                }
                            }
                        } finally {
                            MDC.remove("gameId"); // Clean up MDC
                        }
                    }
                }
            }
            clientToGameOrLobbyIdMap.remove(sessionId);
            clientToPlayerMap.remove(sessionId);
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

            // Set MDC for the game starting logs
            MDC.put("gameId", gameId);
            try {
                Player p1 = game.getPlayer1();
                Player p2 = game.getPlayer2();

                clientToPlayerMap.put(creatorClient.getSessionId(), p1);
                clientToGameOrLobbyIdMap.put(creatorClient.getSessionId(), gameId);
                creatorClient.leaveRoom(lobbyIdToJoin);
                creatorClient.joinRoom(gameId);

                clientToPlayerMap.put(joinerSessionId, p2);
                clientToGameOrLobbyIdMap.put(joinerSessionId, gameId);
                client.joinRoom(gameId);

                logger.info("Game ready. P1: {}, P2: {}. Broadcasting initial state and starting events.",
                        p1.getDisplayName(), p2.getDisplayName());

                List<GameEvent> startEvents = gameService.startGame(game);

                GameStateResponse p1InitialState = GameStateMapper.createGameStateResponse(game, p1.getPlayerId(),
                        "Initial game state.");
                logger.debug("-> Sending 'game_ready' to P1 ({})", p1.getDisplayName());
                creatorClient.sendEvent("game_ready", p1InitialState);

                GameStateResponse p2InitialState = GameStateMapper.createGameStateResponse(game, p2.getPlayerId(),
                        "Initial game state.");
                logger.debug("-> Sending 'game_ready' to P2 ({})", p2.getDisplayName());
                client.sendEvent("game_ready", p2InitialState);

                List<Map<String, Object>> eventData = convertEventsToMapList(startEvents);
                logger.debug("-> Broadcasting STARTUP events to game room: {}", eventData);
                server.getRoomOperations(gameId).sendEvent("game_events", eventData);
            } finally {
                MDC.remove("gameId");
            }
        };
    }

    private List<GameEvent> tailorEventsForPlayer(List<GameEvent> originalEvents, String targetPlayerId) {
        return originalEvents.stream().map(event -> {
            if (event instanceof PlayerDrewCardEvent drawEvent) {
                // If the event is for a different player, hide the card details
                if (!drawEvent.playerId.equals(targetPlayerId)) {
                    return new PlayerDrewCardEvent(
                            drawEvent.gameId,
                            drawEvent.turnNumber,
                            drawEvent.playerId,
                            null, // Hide the card
                            drawEvent.newHandSize,
                            drawEvent.newDeckSize);
                }
            }
            return event; // Return all other events as is
        }).collect(Collectors.toList());
    }

    private DataListener<Map> onGameCommandReceived() {
        return (client, data, ackSender) -> {
            UUID sessionId = client.getSessionId();
            GameCommand command = null;
            try {
                // Deserialize first to get the gameId for MDC
                command = objectMapper.convertValue(data, GameCommand.class);

                // Put gameId into the Mapped Diagnostic Context for logging
                MDC.put("gameId", command.gameId);

                logger.trace("RAW << game_command from [{}]: {}", sessionId, data);
                logger.debug("<< Parsed [game_command] from {}: Type: {}, Payload: {}", sessionId,
                        command.getCommandType(), data);

                String expectedGameId = clientToGameOrLobbyIdMap.get(sessionId);
                Player expectedPlayer = clientToPlayerMap.get(sessionId);

                if (expectedPlayer == null || !expectedPlayer.getPlayerId().equals(command.playerId)
                        || !command.gameId.equals(expectedGameId)) {
                    logger.warn("Session {} sent an unauthorized command. Denying.", sessionId);
                    client.sendEvent("command_rejected", "Authorization failed for command.");
                    return; // Return within the try block
                }

                List<GameEvent> resultingEvents = gameService.handleCommand(command);

                if (resultingEvents.isEmpty()) {
                    logger.warn("Command {} from session {} resulted in NO events. Action was likely invalid.",
                            command.getCommandType(), sessionId);
                    client.sendEvent("command_rejected", "Your action was invalid or had no effect.");
                    return; // Return within the try block
                }

                logger.debug("CMD PROCESSED. Generated {} events.", resultingEvents.size());

                // ----- MODIFICATION START -----
                // Instead of broadcasting, we find each client and send a tailored event list.
                Game game = gameService.getGame(command.gameId);
                if (game != null) {
                    server.getRoomOperations(game.getGameId()).getClients().forEach(c -> {
                        Player p = clientToPlayerMap.get(c.getSessionId());
                        if (p != null) {
                            List<GameEvent> tailoredEvents = tailorEventsForPlayer(resultingEvents, p.getPlayerId());
                            List<Map<String, Object>> tailoredEventData = convertEventsToMapList(tailoredEvents);
                            logger.trace(">> Sending {} tailored 'game_events' to {} ({}): {}", tailoredEvents.size(),
                                    p.getDisplayName(), c.getSessionId(), tailoredEventData);
                            c.sendEvent("game_events", tailoredEventData);
                        } else {
                            logger.warn("Could not find player mapping for client {} in game {}", c.getSessionId(),
                                    game.getGameId());
                        }
                    });

                    if (game.getGameState().name().contains("GAME_OVER")) {
                        cleanupAfterGame(game);
                    }
                }
                // ----- MODIFICATION END -----

            } catch (Exception e) {
                logger.error("Error processing game_command from session {}. Data: {}", sessionId, data, e);
                client.sendEvent("command_rejected", "Internal server error processing your command.");
            } finally {
                // Ensure the MDC is cleared, regardless of success or failure
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
        // Set MDC for cleanup logs to go to the correct file
        MDC.put("gameId", gameId);
        try {
            logger.info("Cleaning up sockets and mappings for ended game {}", gameId);

            server.getRoomOperations(gameId).getClients().forEach(client -> {
                clientToPlayerMap.remove(client.getSessionId());
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