package com.jamestiago.capycards.game.events;

public final class AbilityActivatedEvent extends GameEvent {
    public final String sourceId;
    public final String targetId; // Can be null
    public final Integer abilityIndex; // Can be null

    public AbilityActivatedEvent(String gameId, int turnNumber, String sourceId, String targetId,
            Integer abilityIndex) {
        super(gameId, turnNumber);
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.abilityIndex = abilityIndex;
    }
}