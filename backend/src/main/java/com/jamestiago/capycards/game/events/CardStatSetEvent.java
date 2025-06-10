package com.jamestiago.capycards.game.events;

public final class CardStatSetEvent extends GameEvent {
    public final String targetInstanceId;
    public final String stat; // "ATK", "DEF", "MAX_LIFE", "LIFE"
    public final int value;

    public CardStatSetEvent(String gameId, int turnNumber, String targetInstanceId, String stat, int value) {
        super(gameId, turnNumber);
        this.targetInstanceId = targetInstanceId;
        this.stat = stat;
        this.value = value;
    }
}