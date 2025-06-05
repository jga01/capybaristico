package com.jamestiago.capycards.game.dto;

public class GameActionRequest {
    private String gameId; // Game this action pertains to
    private String playerId; // Player performing the action (their session/player ID)
    private ActionType actionType;

    // Fields specific to certain actions (can be null if not applicable)
    // For PLAY_CARD
    private Integer handCardIndex; // Index of the card in hand
    private Integer targetFieldSlot; // Index of the field slot to play to

    // For ATTACK
    private Integer attackerFieldIndex; // Index of the attacking card on player's field
    private Integer defenderFieldIndex; // Index of the defending card on opponent's field
    // private String targetPlayerId; // Could be used if attacking player directly,
    // not in your rules

    // For ACTIVATE_ABILITY
    private String sourceCardInstanceId; // Instance ID of the card on field activating the ability
    private String targetCardInstanceId; // Instance ID of the card targeted by the ability (if any)
    private Integer abilityOptionIndex; // Index for abilities with multiple effects/choices (e.g., GGaego choice 0, 1,
                                        // 2)

    public enum ActionType {
        PLAY_CARD,
        ATTACK, // Could be more granular: DECLARE_ATTACKERS, CHOOSE_TARGETS
        ACTIVATE_ABILITY, // Generic ability activation
        END_TURN,
        // Other actions like SURRENDER, CHAT_MESSAGE (if applicable)
    }

    // Constructors
    public GameActionRequest() {
    }

    // Getters and Setters (Lombok can also generate these)
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public Integer getHandCardIndex() {
        return handCardIndex;
    }

    public void setHandCardIndex(Integer handCardIndex) {
        this.handCardIndex = handCardIndex;
    }

    public Integer getTargetFieldSlot() {
        return targetFieldSlot;
    }

    public void setTargetFieldSlot(Integer targetFieldSlot) {
        this.targetFieldSlot = targetFieldSlot;
    }

    public Integer getAttackerFieldIndex() {
        return attackerFieldIndex;
    }

    public void setAttackerFieldIndex(Integer attackerFieldIndex) {
        this.attackerFieldIndex = attackerFieldIndex;
    }

    public Integer getDefenderFieldIndex() {
        return defenderFieldIndex;
    }

    public void setDefenderFieldIndex(Integer defenderFieldIndex) {
        this.defenderFieldIndex = defenderFieldIndex;
    }

    public String getSourceCardInstanceId() {
        return sourceCardInstanceId;
    }

    public void setSourceCardInstanceId(String sourceCardInstanceId) {
        this.sourceCardInstanceId = sourceCardInstanceId;
    }

    public String getTargetCardInstanceId() {
        return targetCardInstanceId;
    }

    public void setTargetCardInstanceId(String targetCardInstanceId) {
        this.targetCardInstanceId = targetCardInstanceId;
    }

    public Integer getAbilityOptionIndex() {
        return abilityOptionIndex;
    }

    public void setAbilityOptionIndex(Integer abilityOptionIndex) {
        this.abilityOptionIndex = abilityOptionIndex;
    }

    // toString for debugging
    @Override
    public String toString() {
        return "GameActionRequest{" +
                "gameId='" + gameId + '\'' +
                ", playerId='" + playerId + '\'' +
                ", actionType=" + actionType +
                (handCardIndex != null ? ", handCardIndex=" + handCardIndex : "") +
                (targetFieldSlot != null ? ", targetFieldSlot=" + targetFieldSlot : "") +
                (attackerFieldIndex != null ? ", attackerFieldIndex=" + attackerFieldIndex : "") +
                (defenderFieldIndex != null ? ", defenderFieldIndex=" + defenderFieldIndex : "") +
                (sourceCardInstanceId != null ? ", sourceCardInstanceId='" + sourceCardInstanceId + '\'' : "") +
                (targetCardInstanceId != null ? ", targetCardInstanceId='" + targetCardInstanceId + '\'' : "") +
                (abilityOptionIndex != null ? ", abilityOptionIndex=" + abilityOptionIndex : "") +
                '}';
    }
}