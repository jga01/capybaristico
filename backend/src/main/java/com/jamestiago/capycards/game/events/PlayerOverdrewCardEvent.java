package com.jamestiago.capycards.game.events;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;

/**
 * Event specifically for when a player attempts to draw but their hand is full,
 * causing the drawn card to be discarded immediately.
 */
public final class PlayerOverdrewCardEvent extends GameEvent {
    public final String playerId;
    public final CardInstanceDTO discardedCard; // The card that was drawn and then discarded.
    public final int newDeckSize;
    public final int newDiscardPileSize;

    public PlayerOverdrewCardEvent(String gameId, int turnNumber, String playerId, CardInstanceDTO discardedCard,
            int newDeckSize, int newDiscardPileSize) {
        super(gameId, turnNumber);
        this.playerId = playerId;
        this.discardedCard = discardedCard;
        this.newDeckSize = newDeckSize;
        this.newDiscardPileSize = newDiscardPileSize;
    }
}