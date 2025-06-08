package com.jamestiago.capycards.game.effects;

public enum EffectTrigger {
    ON_PLAY, // When this card is played from hand to field
    ON_DEATH, // When this card is destroyed and sent to discard
    ON_ATTACK_DECLARE, // When this card declares an attack
    ON_DEFEND, // When this card is chosen as a defender
    ON_DAMAGE_DEALT, // When this card deals damage (combat or effect)
    ON_DAMAGE_TAKEN, // When this card takes damage (combat or effect)
    ON_SUMMON, // When any card (or specific type) is summoned by either player
    END_OF_TURN_SELF, // At the end of this card's controller's turn
    START_OF_TURN_SELF, // At the start of this card's controller's turn
    ACTIVATED, // Player must choose to activate this effect
    ON_DEATH_OF_ANY, // When any card on the field is destroyed
    ON_DAMAGE_TAKEN_OF_ANY,

    // Special trigger checked at specific moments, not in the main trigger loop
    CONTINUOUS_DEFENSIVE, // Checked by EffectProcessor.applyDamage before damage is calculated
    CONTINUOUS_AURA // Checked by Game.processAllAuras whenever field state changes
}