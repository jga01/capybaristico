package com.jamestiago.capycards.game.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlayCardCommand.class, name = "PLAY_CARD"),
        @JsonSubTypes.Type(value = AttackCommand.class, name = "ATTACK"),
        @JsonSubTypes.Type(value = ActivateAbilityCommand.class, name = "ACTIVATE_ABILITY"),
        @JsonSubTypes.Type(value = EndTurnCommand.class, name = "END_TURN"),
        @JsonSubTypes.Type(value = GameOverCommand.class, name = "GAME_OVER") // Added for forfeits/disconnects
})
public abstract class GameCommand {
    public String gameId;
    public String playerId;

    // Default constructor for Jackson
    protected GameCommand() {
    }

    protected GameCommand(String gameId, String playerId) {
        this.gameId = gameId;
        this.playerId = playerId;
    }

    public abstract String getCommandType();
}