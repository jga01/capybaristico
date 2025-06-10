package com.jamestiago.capycards.game.events;

public final class TurnStartedEvent extends GameEvent {
    public final String newTurnPlayerId;
    public final int newTurnNumber;

    public TurnStartedEvent(String gameId, int turnNumber, String newTurnPlayerId) {
        // Both turnNumber and newTurnNumber now refer to the new turn for consistency.
        super(gameId, turnNumber + 1);
        this.newTurnPlayerId = newTurnPlayerId;
        this.newTurnNumber = turnNumber + 1;
    }
}