package com.jamestiago.capycards.game.dto;

import java.util.stream.Collectors;

// Assuming GameState enum is in com.yourusername.yourcardgame.game.Game
import com.jamestiago.capycards.game.Game.GameState;

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
    public static GameStateResponse success(String gameId, String message, com.jamestiago.capycards.game.Game game, String forWhosePlayerId) {
        GameStateResponse response = new GameStateResponse();
        response.setSuccess(true);
        response.setGameId(gameId);
        response.setMessage(message);
        // Map Game object to GameStateResponse fields
        response.setCurrentGameState(game.getGameState());
        if (game.getCurrentPlayer() != null) {
            response.setCurrentPlayerId(game.getCurrentPlayer().getPlayerId());
        }
        response.setTurnNumber(game.getTurnNumber());
        response.setViewingPlayerPerspectiveId(forWhosePlayerId); // Set the perspective

        // Player 1 is game.getPlayer1(), Player 2 is game.getPlayer2()
        // The 'isViewingPlayerPerspective' boolean now depends on 'forWhosePlayerId'
        response.setPlayer1State(mapPlayerToDTO(game.getPlayer1(), game.getPlayer1().getPlayerId().equals(forWhosePlayerId)));
        response.setPlayer2State(mapPlayerToDTO(game.getPlayer2(), game.getPlayer2().getPlayerId().equals(forWhosePlayerId)));

        return response;
    }

    public static GameStateResponse error(String gameId, String errorMessage) {
        GameStateResponse response = new GameStateResponse();
        response.setSuccess(false);
        response.setGameId(gameId);
        response.setMessage(errorMessage);
        
        return response;
    }

    // Helper method to map Player game object to PlayerStateDTO
    // This needs to be fleshed out based on what client needs to see.
    private static PlayerStateDTO mapPlayerToDTO(com.jamestiago.capycards.game.Player player, boolean isThisPlayerTheViewer) {
        if (player == null) return null;
        PlayerStateDTO dto = new PlayerStateDTO();
        dto.setPlayerId(player.getPlayerId());
        dto.setDisplayName(player.getDisplayName());
        dto.setDeckSize(player.getDeck().size());
        dto.setDiscardPileSize(player.getDiscardPile().size());
        dto.setHandSize(player.getHand().size());
        dto.setAttacksDeclaredThisTurn(player.getAttacksDeclaredThisTurn());

        // Map Hand
        if (isThisPlayerTheViewer) { // Only send full hand details to the owner
            dto.setHand(player.getHand().stream()
                    .map(GameStateResponse::mapCardInstanceToDTO)
                    .collect(Collectors.toList()));
        } else {
            // For opponent, don't send card details, just placeholders or count (already have handSize)
             dto.setHand(java.util.Collections.emptyList()); // Or list of "hidden card" DTOs with only instanceId for key
        }

        // Map Field
        dto.setField(player.getField().stream()
                .map(GameStateResponse::mapCardInstanceToDTO) // Handles nulls by returning null DTO
                .collect(java.util.stream.Collectors.toList()));
        return dto;
    }

    private static CardInstanceDTO mapCardInstanceToDTO(com.jamestiago.capycards.game.CardInstance cardInstance) {
        if (cardInstance == null) return null; // Important for empty field slots

        CardInstanceDTO dto = new CardInstanceDTO();
        dto.setInstanceId(cardInstance.getInstanceId());
        com.jamestiago.capycards.model.Card def = cardInstance.getDefinition();
        if (def != null) {
            dto.setCardId(def.getCardId());
            dto.setName(def.getName());
            dto.setType(def.getType());
            dto.setEffectText(def.getEffectText());
            dto.setRarity(def.getRarity());
            dto.setImageUrl(def.getImageUrl());
        }
        dto.setCurrentLife(cardInstance.getCurrentLife());
        dto.setCurrentAttack(cardInstance.getCurrentAttack());
        dto.setCurrentDefense(cardInstance.getCurrentDefense());
        // dto.setExhausted(cardInstance.isExhausted());
        return dto;
    }


    // Getters and Setters
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public GameState getCurrentGameState() { return currentGameState; }
    public void setCurrentGameState(GameState currentGameState) { this.currentGameState = currentGameState; }
    public String getCurrentPlayerId() { return currentPlayerId; }
    public GameStateResponse setCurrentPlayerId(String currentPlayerId) { this.currentPlayerId = currentPlayerId; return this; }
    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }
    public PlayerStateDTO getPlayer1State() { return player1State; }
    public void setPlayer1State(PlayerStateDTO player1State) { this.player1State = player1State; }
    public PlayerStateDTO getPlayer2State() { return player2State; }
    public void setPlayer2State(PlayerStateDTO player2State) { this.player2State = player2State; }
    public String getViewingPlayerPerspectiveId() { return viewingPlayerPerspectiveId; }
    public void setViewingPlayerPerspectiveId(String viewingPlayerPerspectiveId) { this.viewingPlayerPerspectiveId = viewingPlayerPerspectiveId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
