package com.jamestiago.capycards.game.events;

public final class CardVanishedEvent extends GameEvent {
    public final String instanceId;
    public final String ownerPlayerId;

    public CardVanishedEvent(String gameId, int turnNumber, String instanceId, String ownerPlayerId) {
        super(gameId, turnNumber);
        this.instanceId = instanceId;
        this.ownerPlayerId = ownerPlayerId;
    }
}