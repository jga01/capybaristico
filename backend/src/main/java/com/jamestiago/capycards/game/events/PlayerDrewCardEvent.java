package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class PlayerDrewCardEvent extends GameEvent {
    public final String playerId;
    public final CardInstanceDTO card; // Null for opponent
    public final int newHandSize;
    public final int newDeckSize;

    public PlayerDrewCardEvent(String gameId, int turnNumber, String playerId, CardInstanceDTO card, int newHandSize,
            int newDeckSize) {
        super(gameId, turnNumber);
        this.playerId = playerId;
        this.card = card;
        this.newHandSize = newHandSize;
        this.newDeckSize = newDeckSize;
    }
}