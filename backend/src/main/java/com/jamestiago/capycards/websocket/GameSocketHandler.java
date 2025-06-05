package com.jamestiago.capycards.websocket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.dto.ActionResultDTO;
import com.jamestiago.capycards.game.dto.GameActionRequest;
import com.jamestiago.capycards.game.dto.GameLobbyInfo;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import com.jamestiago.capycards.service.GameService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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

    private final Map<String, GameLobbyInfo> openLobbies = new ConcurrentHashMap<>();
    // Maps SocketIOClient's SessionID (UUID) to our internal Player object
    private final Map<UUID, Player> clientToPlayerMap = new ConcurrentHashMap<>();
    // Maps SocketIOClient's SessionID (UUID) to GameID or LobbyID string
    private final Map<UUID, String> clientToGameOrLobbyIdMap = new ConcurrentHashMap<>();
    private final Lock lobbyLock = new ReentrantLock();

    @Autowired
    public GameSocketHandler(SocketIOServer server, GameService gameService) {
        this.server = server;
        this.gameService = gameService;
    }

    @PostConstruct
    private void init() {
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());
        server.addEventListener("create_lobby", CreateLobbyRequest.class, onCreateLobbyRequested());
        server.addEventListener("get_open_lobbies", Object.class, onGetOpenLobbiesRequested());
        server.addEventListener("join_lobby", JoinLobbyRequest.class, onJoinLobbyRequested());
        server.addEventListener("game_action", GameActionRequest.class, onGameActionReceived());

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
            client.sendEvent("connection_ack", "Connection successful. You can create or join a lobby.");
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            UUID sessionId = client.getSessionId();
            logger.info("CLIENT DISCONNECTED: {}", sessionId);

            String gameOrLobbyId = clientToGameOrLobbyIdMap.remove(sessionId);
            Player disconnectedPlayer = clientToPlayerMap.remove(sessionId);

            if (gameOrLobbyId != null) {
                boolean lobbyWasRemoved = false;
                lobbyLock.lock();
                try {
                    GameLobbyInfo openLobby = openLobbies.get(gameOrLobbyId);
                    // Check if the disconnected client was the creator of this lobby
                    if (openLobby != null && openLobby.getCreatorSessionId() != null
                            && openLobby.getCreatorSessionId().equals(sessionId)) {
                        openLobbies.remove(gameOrLobbyId);
                        logger.info("Lobby {} removed because creator {} (session {}) disconnected.", gameOrLobbyId,
                                openLobby.getCreatorDisplayName(), sessionId);
                        lobbyWasRemoved = true;
                    }
                } finally {
                    lobbyLock.unlock();
                }

                if (lobbyWasRemoved) {
                    broadcastOpenLobbiesUpdate();
                }

                // If the disconnected player was in an active game
                if (disconnectedPlayer != null) {
                    Game game = gameService.getGame(gameOrLobbyId); // gameOrLobbyId is the gameId here
                    if (game != null && !game.getGameState().name().contains("GAME_OVER")) {
                        logger.info("Player {} ({}) disconnected from active game {}.",
                                disconnectedPlayer.getDisplayName(), sessionId, gameOrLobbyId);

                        // Forfeit the game for the disconnected player
                        Player opponent = game.getOpponent(disconnectedPlayer);
                        if (opponent != null) {
                            // Update game state to make opponent win
                            if (game.getPlayer1() == disconnectedPlayer) {
                                game.setGameState(Game.GameState.GAME_OVER_PLAYER_2_WINS); // Player 2 (opponent) wins
                            } else {
                                game.setGameState(Game.GameState.GAME_OVER_PLAYER_1_WINS); // Player 1 (opponent) wins
                            }
                            logger.info("Game {} ended. {} wins due to {}'s disconnection.", game.getGameId(),
                                    opponent.getDisplayName(), disconnectedPlayer.getDisplayName());

                            // Notify the opponent
                            SocketIOClient opponentClient = findClientForPlayer(opponent.getPlayerId());
                            if (opponentClient != null) {
                                GameStateResponse opponentNotification = GameStateResponse.success(
                                        game.getGameId(),
                                        disconnectedPlayer.getDisplayName() + " has disconnected. You win!",
                                        game, // Pass the updated game state
                                        opponent.getPlayerId());
                                opponentClient.sendEvent("game_state_update", opponentNotification); // Or a specific
                                                                                                     // "opponent_disconnected_win"
                            }
                        }
                        // Game service should remove the game after it's over
                        gameService.removeGame(game.getGameId());
                        // Clean up mappings for the opponent as well, if they are still connected but
                        // game is over
                        if (opponent != null) {
                            SocketIOClient opponentClient = findClientForPlayer(opponent.getPlayerId());
                            if (opponentClient != null) {
                                clientToGameOrLobbyIdMap.remove(opponentClient.getSessionId());
                                clientToPlayerMap.remove(opponentClient.getSessionId());
                                opponentClient.leaveRoom(game.getGameId());
                            }
                        }
                    } else if (game != null && game.getGameState().name().contains("GAME_OVER")) {
                        // Game was already over, just log and ensure cleanup
                        logger.info("Player {} ({}) disconnected from already ended game {}.",
                                disconnectedPlayer.getDisplayName(), sessionId, gameOrLobbyId);
                    }
                }
            }
            client.leaveRoom(gameOrLobbyId); // Ensure client leaves room if they were in one
        };
    }

    private DataListener<CreateLobbyRequest> onCreateLobbyRequested() {
        return (client, data, ackSender) -> {
            UUID sessionId = client.getSessionId();
            String displayName = data.getDisplayName();
            logger.info("CREATE_LOBBY request from client: {} (Display Name: {})", sessionId, displayName);

            if (clientToGameOrLobbyIdMap.containsKey(sessionId)) {
                logger.warn("Client {} attempting to create lobby while already associated with game/lobby {}.",
                        sessionId, clientToGameOrLobbyIdMap.get(sessionId));
                client.sendEvent("lobby_error", "You are already in a game or lobby.");
                ackSender.sendAckData(Map.of("success", false, "message", "Already in game/lobby."));
                return;
            }

            String lobbyId = UUID.randomUUID().toString();
            GameLobbyInfo newLobby = new GameLobbyInfo(lobbyId, displayName, sessionId);

            lobbyLock.lock();
            try {
                openLobbies.put(lobbyId, newLobby);
                clientToGameOrLobbyIdMap.put(sessionId, lobbyId); // Map session to lobbyId
            } finally {
                lobbyLock.unlock();
            }

            client.joinRoom(lobbyId);
            logger.info("Lobby {} created by {} ({}). Client joined room.", lobbyId, displayName, sessionId);
            client.sendEvent("lobby_created",
                    Map.of("lobbyId", lobbyId, "message", "Lobby created. Waiting for opponent."));
            ackSender.sendAckData(Map.of("success", true, "lobbyId", lobbyId));
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
            logger.info("JOIN_LOBBY request from client: {} (Display Name: {}) to lobby: {}", joinerSessionId,
                    joinerDisplayName, lobbyIdToJoin);

            if (clientToGameOrLobbyIdMap.containsKey(joinerSessionId)) {
                client.sendEvent("lobby_error", "You are already in a game or lobby."); // Send specific error to client
                ackSender.sendAckData(Map.of("success", false, "message", "Already in game/lobby."));
                return;
            }

            GameLobbyInfo targetLobby;
            SocketIOClient creatorClient = null;

            lobbyLock.lock();
            try {
                targetLobby = openLobbies.get(lobbyIdToJoin);
                if (targetLobby == null) {
                    client.sendEvent("join_lobby_failed", "Lobby not found.");
                    ackSender.sendAckData(Map.of("success", false, "message", "Lobby not found."));
                    return;
                }
                if (targetLobby.getCurrentPlayerCount() >= 2) {
                    client.sendEvent("join_lobby_failed", "Lobby is full or already started.");
                    ackSender.sendAckData(Map.of("success", false, "message", "Lobby is full or already started."));
                    return;
                }
                if (targetLobby.getCreatorSessionId().equals(joinerSessionId)) {
                    client.sendEvent("lobby_error", "You cannot join your own lobby.");
                    ackSender.sendAckData(Map.of("success", false, "message", "Cannot join your own lobby."));
                    return;
                }

                creatorClient = server.getClient(targetLobby.getCreatorSessionId());
                if (creatorClient == null || !creatorClient.isChannelOpen()) {
                    logger.error("Creator client (session {}) for lobby {} not found or disconnected. Removing lobby.",
                            targetLobby.getCreatorSessionId(), lobbyIdToJoin);
                    openLobbies.remove(lobbyIdToJoin); // Remove inconsistent lobby
                    broadcastOpenLobbiesUpdate(); // Notify other clients
                    client.sendEvent("join_lobby_failed", "Lobby creator not found or disconnected. Lobby removed.");
                    ackSender.sendAckData(
                            Map.of("success", false, "message", "Lobby creator not found or disconnected."));
                    return;
                }
                openLobbies.remove(lobbyIdToJoin); // Successfully found, remove from open lobbies
            } finally {
                lobbyLock.unlock();
            }

            // Game Creation and Start
            try {
                String creatorDisplayName = targetLobby.getCreatorDisplayName();
                Game game = gameService.createNewGame(creatorDisplayName, joinerDisplayName);
                String actualGameId = game.getGameId();

                Player p1 = game.getPlayer1();
                Player p2 = game.getPlayer2();

                // Creator (P1)
                clientToPlayerMap.put(creatorClient.getSessionId(), p1);
                clientToGameOrLobbyIdMap.put(creatorClient.getSessionId(), actualGameId); // Update from lobbyId to
                                                                                          // gameId
                creatorClient.leaveRoom(lobbyIdToJoin);
                creatorClient.joinRoom(actualGameId);

                // Joiner (P2)
                clientToPlayerMap.put(joinerSessionId, p2);
                clientToGameOrLobbyIdMap.put(joinerSessionId, actualGameId); // Map joiner to gameId
                client.joinRoom(actualGameId);

                logger.info(
                        "Game {} started. Creator: {} (Client {}), Joiner: {} (Client {}). Broadcasting initial state.",
                        actualGameId, p1.getDisplayName(), creatorClient.getSessionId(), p2.getDisplayName(),
                        client.getSessionId());

                GameStateResponse p1InitialState = GameStateResponse.success(actualGameId,
                        "Opponent joined! You are Player 1.", game, p1.getPlayerId());
                creatorClient.sendEvent("game_start", p1InitialState);
                GameStateResponse p2InitialState = GameStateResponse.success(actualGameId,
                        "Joined game! You are Player 2.", game, p2.getPlayerId());
                client.sendEvent("game_start", p2InitialState);

                ackSender.sendAckData(
                        Map.of("success", true, "gameId", actualGameId, "message", "Successfully joined game."));
                broadcastOpenLobbiesUpdate(); // Lobbies list has changed
            } catch (Exception e) {
                logger.error("Error starting game after join for lobby {}: {}", lobbyIdToJoin, e.getMessage(), e);
                client.sendEvent("join_lobby_failed", "Error starting game: " + e.getMessage());
                ackSender.sendAckData(Map.of("success", false, "message", "Error starting game."));
                // Consider re-adding lobby if game creation failed catastrophically
                // lobbyLock.lock(); try { if(!openLobbies.containsKey(lobbyIdToJoin))
                // openLobbies.put(lobbyIdToJoin, targetLobby); } finally { lobbyLock.unlock();
                // }
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

    private DataListener<GameActionRequest> onGameActionReceived() {
        return (client, requestData, ackSender) -> {
            UUID sessionId = client.getSessionId();
            String gameId = requestData.getGameId();
            String actionPlayerId = requestData.getPlayerId();

            logger.debug("Received game_action from session {} for game {} by player {}: {}", sessionId, gameId,
                    actionPlayerId, requestData.getActionType());

            if (gameId == null || actionPlayerId == null) {
                client.sendEvent("action_error",
                        GameStateResponse.error(gameId, "Missing gameId or playerId in request."));
                return;
            }

            Player expectedPlayerForSession = clientToPlayerMap.get(sessionId);
            String expectedGameIdForSession = clientToGameOrLobbyIdMap.get(sessionId);

            if (expectedPlayerForSession == null || !expectedPlayerForSession.getPlayerId().equals(actionPlayerId)
                    || !gameId.equals(expectedGameIdForSession)) {
                logger.warn(
                        "Session {} sent action for player {} in game {}, but is mapped to player {} in game {}. Denying action.",
                        sessionId, actionPlayerId, gameId,
                        (expectedPlayerForSession != null ? expectedPlayerForSession.getPlayerId() : "null"),
                        expectedGameIdForSession);
                client.sendEvent("action_error",
                        GameStateResponse.error(gameId, "Action authorization failed. Mismatched session."));
                return;
            }

            Game game = gameService.getGame(gameId);
            if (game == null) {
                logger.warn("Game {} not found for action from session {}. It might have ended or been removed.",
                        gameId, sessionId);
                client.sendEvent("action_error", GameStateResponse.error(gameId, "Game not found. It may have ended."));
                // If game is not found, it means it's over or removed. Client should handle
                // this (e.g. return to lobby screen)
                // No further state update needed if game object is gone.
                return;
            }
            if (game.getGameState().name().contains("GAME_OVER")) {
                logger.warn("Action received for game {} which is already over (State: {}). Ignoring.", gameId,
                        game.getGameState());
                // Send a final game state to confirm it's over, or a specific "game_is_over"
                // event.
                // For now, action_error will tell client something is wrong.
                GameStateResponse errorResponse = GameStateResponse.error(gameId,
                        "Game is already over. No further actions allowed.");
                errorResponse.setCurrentGameState(game.getGameState());
                client.sendEvent("action_error", errorResponse);
                return;
            }

            ActionResultDTO actionResult = gameService.handleGameAction(requestData);
            Game gameAfterAction = gameService.getGame(gameId); // Re-fetch

            if (!actionResult.isSuccess()) {
                logger.warn("Action error from GameService for game {}: {}. Sending error to acting client {}.", gameId,
                        actionResult.getMessage(), sessionId);
                // Send specific error message back to the client who performed the action
                GameStateResponse errorResponse = GameStateResponse.error(gameId, actionResult.getMessage());
                errorResponse.setCurrentPlayerId(actionResult.getCurrentPlayerIdAfterAction());
                errorResponse.setCurrentGameState(gameAfterAction != null ? gameAfterAction.getGameState() : null); // Send
                                                                                                                    // current
                                                                                                                    // game
                                                                                                                    // state
                                                                                                                    // with
                                                                                                                    // error
                client.sendEvent("action_error", errorResponse);
                return;
            }

            // Action was successful
            if (gameAfterAction == null && !actionResult.didGameEnd()) {
                // This is an unexpected state: action succeeded but game disappeared AND
                // actionResult didn't say it ended.
                logger.error(
                        "CRITICAL: Game {} became null after a successful action but actionResult.didGameEnd was false. Message: {}",
                        gameId, actionResult.getMessage());
                client.sendEvent("action_error", GameStateResponse.error(gameId,
                        "Internal server error: Game state inconsistent after action."));
                return;
            }

            // If game ended as part of this action, gameAfterAction might be null if
            // GameService removed it.
            // Or gameAfterAction might exist but its state is GAME_OVER.
            boolean gameHasTrulyEnded = actionResult.didGameEnd();
            Game finalStateReference = gameHasTrulyEnded && gameAfterAction == null ? game : gameAfterAction; // Use
                                                                                                              // 'game'
                                                                                                              // (before
                                                                                                              // removal)
                                                                                                              // for
                                                                                                              // final
                                                                                                              // state
                                                                                                              // if
                                                                                                              // removed
                                                                                                              // This is
                                                                                                              // tricky.
                                                                                                              // Best if
                                                                                                              // GameService
                                                                                                              // doesn't
                                                                                                              // remove
                                                                                                              // immediately.
                                                                                                              // Let's
                                                                                                              // assume
                                                                                                              // GameService
                                                                                                              // does
                                                                                                              // NOT
                                                                                                              // remove
                                                                                                              // the
                                                                                                              // game
                                                                                                              // instance
                                                                                                              // on end,
                                                                                                              // only
                                                                                                              // marks
                                                                                                              // state.
                                                                                                              // Handler
                                                                                                              // removes.

            if (finalStateReference == null) { // Should not happen if GameService doesn't remove immediately
                logger.error("CRITICAL: finalStateReference is null for game {}. Action was {}. Result message: {}",
                        gameId, requestData.getActionType(), actionResult.getMessage());
                // Fallback or error to clients
                return;
            }

            // Broadcast tailored updates
            Player p1 = finalStateReference.getPlayer1();
            Player p2 = finalStateReference.getPlayer2();
            SocketIOClient clientP1 = findClientForPlayer(p1.getPlayerId());
            SocketIOClient clientP2 = findClientForPlayer(p2.getPlayerId());

            String finalMessageToBroadcast = actionResult.getMessage();

            if (clientP1 != null) {
                GameStateResponse p1Response = GameStateResponse.success(gameId, finalMessageToBroadcast,
                        finalStateReference, p1.getPlayerId());
                clientP1.sendEvent("game_state_update", p1Response);
            }
            if (clientP2 != null) {
                GameStateResponse p2Response = GameStateResponse.success(gameId, finalMessageToBroadcast,
                        finalStateReference, p2.getPlayerId());
                clientP2.sendEvent("game_state_update", p2Response);
            }

            logger.debug(
                    "Broadcasted tailored game_state_update to players in game {} after action by {} with message: {}",
                    gameId, actionPlayerId, finalMessageToBroadcast);

            if (gameHasTrulyEnded) {
                logger.info("Game {} has ended. State: {}. Cleaning up client mappings.", gameId,
                        finalStateReference.getGameState());
                // Remove game from GameService AFTER sending final state
                gameService.removeGame(gameId);

                // Clean up mappings and rooms for both players
                if (clientP1 != null) {
                    clientToPlayerMap.remove(clientP1.getSessionId());
                    clientToGameOrLobbyIdMap.remove(clientP1.getSessionId());
                    clientP1.leaveRoom(gameId);
                }
                if (clientP2 != null) {
                    clientToPlayerMap.remove(clientP2.getSessionId());
                    clientToGameOrLobbyIdMap.remove(clientP2.getSessionId());
                    clientP2.leaveRoom(gameId);
                }
            }
        };
    }

    private SocketIOClient findClientForPlayer(String playerId) {
        for (Map.Entry<UUID, Player> entry : clientToPlayerMap.entrySet()) {
            if (entry.getValue().getPlayerId().equals(playerId)) {
                SocketIOClient client = server.getClient(entry.getKey());
                if (client != null && client.isChannelOpen()) {
                    return client;
                }
            }
        }
        return null;
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