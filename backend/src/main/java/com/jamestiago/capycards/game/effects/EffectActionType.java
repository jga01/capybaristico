package com.jamestiago.capycards.game.effects;

public enum EffectActionType {
    DEAL_DAMAGE,
    DEAL_AOE_DAMAGE,
    HEAL_TARGET,
    HEAL_SELF,
    BUFF_STAT,      // ATK, DEF, LIFE
    DEBUFF_STAT,
    DRAW_CARDS,
    DESTROY_CARD,
    RETURN_TO_HAND,
    SPECIAL_SUMMON, // Summon a card from deck/discard/etc.
    TRANSFORM,
    APPLY_STATUS,   // e.g., STUN, IMMUNITY_TO_TYPE
    CHECK_CONDITION_AND_TRIGGER_SUB_EFFECT, // For complex conditional effects
    ROLL_DICE,
    FLIP_COIN
    // ... more as needed
}
