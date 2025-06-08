package com.jamestiago.capycards.game.events;

public final class CombatDamageDealtEvent extends GameEvent {
    public final String attackerInstanceId;
    public final String defenderInstanceId;
    public final int damageAmount;
    public final int damageAfterDefense;
    public final int defenderLifeBefore;
    public final int defenderLifeAfter;

    public CombatDamageDealtEvent(String gameId, int turnNumber, String attackerInstanceId, String defenderInstanceId,
            int damageAmount, int damageAfterDefense, int defenderLifeBefore, int defenderLifeAfter) {
        super(gameId, turnNumber);
        this.attackerInstanceId = attackerInstanceId;
        this.defenderInstanceId = defenderInstanceId;
        this.damageAmount = damageAmount;
        this.damageAfterDefense = damageAfterDefense;
        this.defenderLifeBefore = defenderLifeBefore;
        this.defenderLifeAfter = defenderLifeAfter;
    }
}