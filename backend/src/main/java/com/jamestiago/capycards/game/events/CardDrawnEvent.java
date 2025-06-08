package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class CardDrawnEvent extends GameEvent {
    public final String playerId;
    public final CardInstanceDTO card; // Only sent to the drawing player
    public final int newHandSize;

    public CardDrawnEvent(String gameId, int turnNumber, String playerId, CardInstanceDTO card, int newHandSize) {
        super(gameId, turnNumber);
        this.playerId = playerId;
        this.card = card; // This will be null for the opponent
        this.newHandSize = newHandSize;
    }
}