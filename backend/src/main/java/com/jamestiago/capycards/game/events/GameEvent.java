package com.jamestiago.capycards.game.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GameStartedEvent.class, name = "GAME_STARTED"),
        @JsonSubTypes.Type(value = TurnStartedEvent.class, name = "TURN_STARTED"),
        @JsonSubTypes.Type(value = CardPlayedEvent.class, name = "CARD_PLAYED"),
        @JsonSubTypes.Type(value = AttackDeclaredEvent.class, name = "ATTACK_DECLARED"),
        @JsonSubTypes.Type(value = CombatDamageDealtEvent.class, name = "COMBAT_DAMAGE_DEALT"),
        @JsonSubTypes.Type(value = CardStatsChangedEvent.class, name = "CARD_STATS_CHANGED"),
        @JsonSubTypes.Type(value = CardHealedEvent.class, name = "CARD_HEALED"),
        @JsonSubTypes.Type(value = CardDestroyedEvent.class, name = "CARD_DESTROYED"),
        @JsonSubTypes.Type(value = TurnEndedEvent.class, name = "TURN_ENDED"),
        @JsonSubTypes.Type(value = GameOverEvent.class, name = "GAME_OVER"),
        @JsonSubTypes.Type(value = AbilityActivatedEvent.class, name = "ABILITY_ACTIVATED"),
        @JsonSubTypes.Type(value = CardBuffedEvent.class, name = "CARD_BUFFED"),
        @JsonSubTypes.Type(value = CardDebuffedEvent.class, name = "CARD_DEBUFFED"),
        @JsonSubTypes.Type(value = CardFlagChangedEvent.class, name = "CARD_FLAG_CHANGED"),
        @JsonSubTypes.Type(value = CardTransformedEvent.class, name = "CARD_TRANSFORMED"),
        @JsonSubTypes.Type(value = GameLogMessageEvent.class, name = "GAME_LOG_MESSAGE"),
        @JsonSubTypes.Type(value = PlayerDrewCardEvent.class, name = "PLAYER_DREW_CARD"),
        @JsonSubTypes.Type(value = PlayerOverdrewCardEvent.class, name = "PLAYER_OVERDREW_CARD"),
        @JsonSubTypes.Type(value = CardVanishedEvent.class, name = "CARD_VANISHED"),
        @JsonSubTypes.Type(value = CardReappearedEvent.class, name = "CARD_REAPPEARED"),
        @JsonSubTypes.Type(value = CardStatSetEvent.class, name = "CARD_STAT_SET"),
        @JsonSubTypes.Type(value = CardAddedToDeckEvent.class, name = "CARD_ADDED_TO_DECK")
})
public abstract class GameEvent {
    public final long timestamp;
    public final String gameId;
    public final int turnNumber;

    protected GameEvent(String gameId, int turnNumber) {
        this.timestamp = System.currentTimeMillis();
        this.gameId = gameId;
        this.turnNumber = turnNumber;
    }
}