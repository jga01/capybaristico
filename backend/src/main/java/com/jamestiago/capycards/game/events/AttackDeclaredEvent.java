package com.jamestiago.capycards.game.events;

public final class AttackDeclaredEvent extends GameEvent {
    public final String attackerPlayerId;
    public final String attackerInstanceId;
    public final String attackerCardName;
    public final String defenderInstanceId;
    public final String defenderCardName;

    public AttackDeclaredEvent(String gameId, int turnNumber, String attackerPlayerId, String attackerInstanceId,
            String attackerCardName,
            String defenderInstanceId, String defenderCardName) {
        super(gameId, turnNumber);
        this.attackerPlayerId = attackerPlayerId;
        this.attackerInstanceId = attackerInstanceId;
        this.attackerCardName = attackerCardName;
        this.defenderInstanceId = defenderInstanceId;
        this.defenderCardName = defenderCardName;
    }
}