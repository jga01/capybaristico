package com.jamestiago.capycards.game.events;

public final class AttackDeclaredEvent extends GameEvent {
    public final String attackerPlayerId;
    public final String attackerInstanceId;
    public final String defenderInstanceId;

    public AttackDeclaredEvent(String gameId, int turnNumber, String attackerPlayerId, String attackerInstanceId,
            String defenderInstanceId) {
        super(gameId, turnNumber);
        this.attackerPlayerId = attackerPlayerId;
        this.attackerInstanceId = attackerInstanceId;
        this.defenderInstanceId = defenderInstanceId;
    }
}