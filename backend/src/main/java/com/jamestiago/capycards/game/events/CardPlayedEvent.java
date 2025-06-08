package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class CardPlayedEvent extends GameEvent {
    public final String playerId;
    public final CardInstanceDTO card;
    public final int fromHandIndex;
    public final int toFieldSlot;
    public final int newHandSize;

    public CardPlayedEvent(String gameId, int turnNumber, String playerId, CardInstanceDTO card, int fromHandIndex,
            int toFieldSlot, int newHandSize) {
        super(gameId, turnNumber);
        this.playerId = playerId;
        this.card = card;
        this.fromHandIndex = fromHandIndex;
        this.toFieldSlot = toFieldSlot;
        this.newHandSize = newHandSize;
    }
}