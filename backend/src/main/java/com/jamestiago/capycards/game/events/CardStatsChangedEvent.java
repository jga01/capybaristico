package com.jamestiago.capycards.game.events;

public final class CardStatsChangedEvent extends GameEvent {
    public final String targetInstanceId;
    public final int newAttack;
    public final int newDefense;
    public final int newLife;
    public final String reason; // e.g., "BUFF", "DAMAGE", "TRANSFORM"

    public CardStatsChangedEvent(String gameId, int turnNumber, String targetInstanceId, int newAttack, int newDefense,
            int newLife, String reason) {
        super(gameId, turnNumber);
        this.targetInstanceId = targetInstanceId;
        this.newAttack = newAttack;
        this.newDefense = newDefense;
        this.newLife = newLife;
        this.reason = reason;
    }
}