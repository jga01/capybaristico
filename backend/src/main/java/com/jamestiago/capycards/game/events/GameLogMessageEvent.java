package com.jamestiago.capycards.game.events;

public final class GameLogMessageEvent extends GameEvent {
    public final String message;
    public final String level; // e.g., "INFO", "WARN", "EFFECT"

    public GameLogMessageEvent(String gameId, int turnNumber, String message, String level) {
        super(gameId, turnNumber);
        this.message = message;
        this.level = level;
    }
}