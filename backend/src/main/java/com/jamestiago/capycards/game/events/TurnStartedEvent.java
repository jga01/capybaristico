package com.jamestiago.capycards.game.events;

public final class TurnStartedEvent extends GameEvent {
    public final String newTurnPlayerId;
    public final int newTurnNumber;

    public TurnStartedEvent(String gameId, int turnNumber, String newTurnPlayerId) {
        // The `turnNumber` parameter now correctly represents the turn that *ended*.
        super(gameId, turnNumber);
        this.newTurnPlayerId = newTurnPlayerId;
        // The event is responsible for calculating the new turn number.
        this.newTurnNumber = turnNumber + 1;
    }
}