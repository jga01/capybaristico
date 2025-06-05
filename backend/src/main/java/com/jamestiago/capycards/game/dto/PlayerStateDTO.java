package com.jamestiago.capycards.game.dto;

import java.util.List;

public class PlayerStateDTO {
    private String playerId;
    private String displayName;
    private List<CardInstanceDTO> hand; // For the viewing player, this is their actual hand
                                        // For the opponent, this might just be handSize or obfuscated cards
    private int handSize; // Always useful, especially for opponent
    private List<CardInstanceDTO> field; // Nulls can represent empty slots
    private int deckSize;
    private int discardPileSize;
    // private int lifePoints; // If players have direct life

    public PlayerStateDTO() {}

    // Getters and Setters
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<CardInstanceDTO> getHand() { return hand; }
    public void setHand(List<CardInstanceDTO> hand) { this.hand = hand; }
    public int getHandSize() { return handSize; }
    public void setHandSize(int handSize) { this.handSize = handSize; }
    public List<CardInstanceDTO> getField() { return field; }
    public void setField(List<CardInstanceDTO> field) { this.field = field; }
    public int getDeckSize() { return deckSize; }
    public void setDeckSize(int deckSize) { this.deckSize = deckSize; }
    public int getDiscardPileSize() { return discardPileSize; }
    public void setDiscardPileSize(int discardPileSize) { this.discardPileSize = discardPileSize; }
}
