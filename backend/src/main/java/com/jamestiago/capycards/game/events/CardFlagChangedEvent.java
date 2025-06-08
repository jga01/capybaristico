package com.jamestiago.capycards.game.events;

public final class CardFlagChangedEvent extends GameEvent {
    public final String targetInstanceId;
    public final String flagName;
    public final Object value;
    public final String duration; // e.g., "PERMANENT", "TURN"

    public CardFlagChangedEvent(String gameId, int turnNumber, String targetInstanceId, String flagName, Object value,
            String duration) {
        super(gameId, turnNumber);
        this.targetInstanceId = targetInstanceId;
        this.flagName = flagName;
        this.value = value;
        this.duration = duration;
    }
}