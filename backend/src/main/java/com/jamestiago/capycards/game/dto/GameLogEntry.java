package com.jamestiago.capycards.game.dto; // Or a dedicated log package

import java.time.Instant;

public class GameLogEntry {
    private long timestamp; // Milliseconds since epoch, or Instant.toString()
    private int turnNumber;
    private String actingPlayerId; // Optional, if relevant
    private String actingPlayerDisplayName; // Optional
    private LogEntryType type;
    private String message;
    // Optional: For more structured data for specific log types
    // private String sourceCardInstanceId;
    // private String targetCardInstanceId;
    // private int value; // e.g., damage dealt, life healed

    public enum LogEntryType {
        GAME_START,
        TURN_START,
        CARD_PLAYED,
        ATTACK_DECLARED,
        COMBAT_DAMAGE, // Could be more granular (damage dealt, damage taken)
        EFFECT_ACTIVATED,
        EFFECT_RESOLVED, // For effects that have a clear resolution message
        CARD_DESTROYED,
        CARD_STAT_CHANGE, // Buffs, debuffs
        PLAYER_DRAW,
        PLAYER_DISCARD,
        GAME_EVENT, // Generic important event
        WIN_CONDITION_MET,
        ERROR // Server-side game error relevant to log
    }

    // Constructor
    public GameLogEntry(int turnNumber, String actingPlayerId, String actingPlayerDisplayName, LogEntryType type,
            String message) {
        this.timestamp = Instant.now().toEpochMilli();
        this.turnNumber = turnNumber;
        this.actingPlayerId = actingPlayerId;
        this.actingPlayerDisplayName = actingPlayerDisplayName;
        this.type = type;
        this.message = message;
    }

    public GameLogEntry(int turnNumber, LogEntryType type, String message) {
        this(turnNumber, null, null, type, message);
    }

    // Getters (and setters if needed, though likely immutable after creation)
    public long getTimestamp() {
        return timestamp;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public String getActingPlayerId() {
        return actingPlayerId;
    }

    public String getActingPlayerDisplayName() {
        return actingPlayerDisplayName;
    }

    public LogEntryType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        // Simple string representation for server-side logging or basic client display
        return String.format("T%d [%s] %s: %s",
                turnNumber,
                type,
                actingPlayerDisplayName != null ? actingPlayerDisplayName : "System",
                message);
    }
}