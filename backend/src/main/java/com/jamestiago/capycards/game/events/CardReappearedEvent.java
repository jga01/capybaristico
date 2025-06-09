package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class CardReappearedEvent extends GameEvent {
    public final CardInstanceDTO card;
    public final String ownerPlayerId;
    public final int toFieldSlot;

    public CardReappearedEvent(String gameId, int turnNumber, CardInstanceDTO card, String ownerPlayerId,
            int toFieldSlot) {
        super(gameId, turnNumber);
        this.card = card;
        this.ownerPlayerId = ownerPlayerId;
        this.toFieldSlot = toFieldSlot;
    }
}