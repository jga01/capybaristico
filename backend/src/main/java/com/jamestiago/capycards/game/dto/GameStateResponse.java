package com.jamestiago.capycards.game.dto;

import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Game.GameState;
import com.jamestiago.capycards.game.GameStateMapper;

public class GameStateResponse {
    private String gameId;
    private GameState currentGameState; // e.g., PLAYER_1_TURN, GAME_OVER_PLAYER_1_WINS
    private String currentPlayerId; // ID of the player whose turn it is
    private int turnNumber;
    private PlayerStateDTO player1State;
    private PlayerStateDTO player2State;

    private String viewingPlayerPerspectiveId; // The Player ID of the client this response is for

    // For error messages or general messages
    private boolean success;
    private String message;

    public GameStateResponse() {
    }

    // Static factory methods for convenience
    public static GameStateResponse success(String gameId, String message, Game game, String forWhosePlayerId) {
        return GameStateMapper.createGameStateResponse(game, forWhosePlayerId, message);
    }

    public static GameStateResponse error(String gameId, String errorMessage) {
        GameStateResponse response = new GameStateResponse();
        response.setSuccess(false);
        response.setGameId(gameId);
        response.setMessage(errorMessage);

        return response;
    }

    // Getters and Setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public GameState getCurrentGameState() {
        return currentGameState;
    }

    public void setCurrentGameState(GameState currentGameState) {
        this.currentGameState = currentGameState;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public GameStateResponse setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
        return this;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public PlayerStateDTO getPlayer1State() {
        return player1State;
    }

    public void setPlayer1State(PlayerStateDTO player1State) {
        this.player1State = player1State;
    }

    public PlayerStateDTO getPlayer2State() {
        return player2State;
    }

    public void setPlayer2State(PlayerStateDTO player2State) {
        this.player2State = player2State;
    }

    public String getViewingPlayerPerspectiveId() {
        return viewingPlayerPerspectiveId;
    }

    public void setViewingPlayerPerspectiveId(String viewingPlayerPerspectiveId) {
        this.viewingPlayerPerspectiveId = viewingPlayerPerspectiveId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}