package com.jamestiago.capycards.game.commands;

public final class ActivateAbilityCommand extends GameCommand {
    public final String sourceCardInstanceId;
    public final String targetCardInstanceId; // Optional
    public final Integer abilityOptionIndex; // Optional

    public ActivateAbilityCommand(String gameId, String playerId, String sourceCardInstanceId,
            String targetCardInstanceId, Integer abilityOptionIndex) {
        super(gameId, playerId);
        this.sourceCardInstanceId = sourceCardInstanceId;
        this.targetCardInstanceId = targetCardInstanceId;
        this.abilityOptionIndex = abilityOptionIndex;
    }

    @Override
    public String getCommandType() {
        return "ACTIVATE_ABILITY";
    }
}