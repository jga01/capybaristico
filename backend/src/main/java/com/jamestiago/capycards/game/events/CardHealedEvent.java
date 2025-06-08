package com.jamestiago.capycards.game.events;

public final class CardHealedEvent extends GameEvent {
    public final String targetInstanceId;
    public final int amount;
    public final int lifeAfter;

    public CardHealedEvent(String gameId, int turnNumber, String targetInstanceId, int amount, int lifeAfter) {
        super(gameId, turnNumber);
        this.targetInstanceId = targetInstanceId;
        this.amount = amount;
        this.lifeAfter = lifeAfter;
    }
}