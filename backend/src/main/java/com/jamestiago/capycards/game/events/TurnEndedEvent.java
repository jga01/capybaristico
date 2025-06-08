package com.jamestiago.capycards.game.events;

public final class TurnEndedEvent extends GameEvent {
    public final String endedTurnPlayerId;

    public TurnEndedEvent(String gameId, int turnNumber, String endedTurnPlayerId) {
        super(gameId, turnNumber);
        this.endedTurnPlayerId = endedTurnPlayerId;
    }
}