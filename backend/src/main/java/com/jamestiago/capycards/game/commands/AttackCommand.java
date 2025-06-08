package com.jamestiago.capycards.game.commands;

public final class AttackCommand extends GameCommand {
    public final int attackerFieldIndex;
    public final int defenderFieldIndex;

    public AttackCommand(String gameId, String playerId, int attackerFieldIndex, int defenderFieldIndex) {
        super(gameId, playerId);
        this.attackerFieldIndex = attackerFieldIndex;
        this.defenderFieldIndex = defenderFieldIndex;
    }

    @Override
    public String getCommandType() {
        return "ATTACK";
    }
}