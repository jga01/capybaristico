package com.jamestiago.capycards.game.events;

public final class CardBuffedEvent extends GameEvent {
    public final String targetInstanceId;
    public final String stat; // "ATK", "DEF", "MAX_LIFE"
    public final int amount;
    public final boolean isPermanent;
    public final int statAfter;

    public CardBuffedEvent(String gameId, int turnNumber, String targetInstanceId, String stat, int amount,
            boolean isPermanent, int statAfter) {
        super(gameId, turnNumber);
        this.targetInstanceId = targetInstanceId;
        this.stat = stat;
        this.amount = amount;
        this.isPermanent = isPermanent;
        this.statAfter = statAfter;
    }
}