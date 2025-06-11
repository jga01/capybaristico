package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class CardAddedToDeckEvent extends GameEvent {
    public final String playerId;
    public final CardInstanceDTO card; // The card that was added
    public final int newDeckSize;
    public final String placement; // "SHUFFLE", "TOP", "BOTTOM"

    public CardAddedToDeckEvent(String gameId, int turnNumber, String playerId, CardInstanceDTO card, int newDeckSize,
            String placement) {
        super(gameId, turnNumber);
        this.playerId = playerId;
        this.card = card;
        this.newDeckSize = newDeckSize;
        this.placement = placement;
    }
}