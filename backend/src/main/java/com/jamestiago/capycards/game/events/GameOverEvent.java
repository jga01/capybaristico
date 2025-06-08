package com.jamestiago.capycards.game.events;

public final class GameOverEvent extends GameEvent {
    public final String winnerPlayerId; // Can be null for a draw
    public final String reason;

    public GameOverEvent(String gameId, int turnNumber, String winnerPlayerId, String reason) {
        super(gameId, turnNumber);
        this.winnerPlayerId = winnerPlayerId;
        this.reason = reason;
    }
}