package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

public final class CardTransformedEvent extends GameEvent {
    public final String originalInstanceId;
    public final CardInstanceDTO newCardDto;

    public CardTransformedEvent(String gameId, int turnNumber, String originalInstanceId, CardInstanceDTO newCardDto) {
        super(gameId, turnNumber);
        this.originalInstanceId = originalInstanceId;
        this.newCardDto = newCardDto;
    }
}