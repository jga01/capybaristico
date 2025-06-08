package com.jamestiago.capycards.game.effects;

public enum EffectActionType {
    // Core Actions
    DEAL_DAMAGE,
    HEAL_TARGET,
    BUFF_STAT,
    DEBUFF_STAT,
    DRAW_CARDS,
    DESTROY_CARD,
    TRANSFORM_CARD,

    // State/Flag Manipulation
    APPLY_FLAG,
    REMOVE_FLAG,
    MODIFY_FLAG, // For incrementing/decrementing numeric flags

    // Advanced/Composite Actions
    CHAINED_EFFECTS,
    CHOOSE_RANDOM_EFFECT,
    MODIFY_INCOMING_DAMAGE, // Defensive continuous effect
    APPLY_AURA_BUFF, // Continuous aura effect

    // Meta Actions
    GAME_LOG_MESSAGE,

    // Placeholders for very complex logic that may still be needed
    CUSTOM_LOGIC
}