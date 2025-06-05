package com.jamestiago.capycards.game.dto;

import com.jamestiago.capycards.game.Game;

public class ActionResultDTO {
    private boolean success;
    private String message;
    private String gameId; // Useful for GameSocketHandler to refetch the game
    private String currentPlayerIdAfterAction; // To update GameStateResponse in handler
    private boolean gameEnded; // Flag if the action ended the game

    public ActionResultDTO(boolean success, String message, String gameId, String currentPlayerIdAfterAction, boolean gameEnded) {
        this.success = success;
        this.message = message;
        this.gameId = gameId;
        this.currentPlayerIdAfterAction = currentPlayerIdAfterAction;
        this.gameEnded = gameEnded;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getGameId() { return gameId; }
    public String getCurrentPlayerIdAfterAction() { return currentPlayerIdAfterAction; }
    public boolean didGameEnd() { return gameEnded; }

    // Static factory methods
    public static ActionResultDTO success(String message, Game game) {
        return new ActionResultDTO(
            true,
            message,
            game.getGameId(),
            game.getCurrentPlayer() != null ? game.getCurrentPlayer().getPlayerId() : null,
            game.getGameState().name().contains("GAME_OVER")
        );
    }

    public static ActionResultDTO failure(String message, String gameId, Game gameBeforeAction) {
         // If game object is available, can pass currentPlayerId from it
        return new ActionResultDTO(
            false,
            message,
            gameId,
            gameBeforeAction != null && gameBeforeAction.getCurrentPlayer() != null ? gameBeforeAction.getCurrentPlayer().getPlayerId() : null,
            gameBeforeAction != null && gameBeforeAction.getGameState().name().contains("GAME_OVER") // game might not have ended if action failed
        );
    }
     public static ActionResultDTO failure(String message, String gameId) {
        return new ActionResultDTO(false, message, gameId, null, false);
    }
}