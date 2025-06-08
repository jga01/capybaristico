package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class CardDestroyedEvent extends GameEvent {
    public final CardInstanceDTO card;
    public final String ownerPlayerId;

    public CardDestroyedEvent(String gameId, int turnNumber, CardInstanceDTO card, String ownerPlayerId) {
        super(gameId, turnNumber);
        this.card = card;
        this.ownerPlayerId = ownerPlayerId;
    }
}