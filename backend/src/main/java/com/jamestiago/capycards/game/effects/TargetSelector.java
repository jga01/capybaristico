package com.jamestiago.capycards.game.effects;

public enum TargetSelector {
    SELF,
    CONTROLLER, // The player controlling this card/effect
    OPPONENT_PLAYER,
    
    // Card Targets
    TARGETED_CARD,          // User selects one card (usually opponent's)
    TARGETED_OWN_CARD,      // User selects one of their own cards
    RANDOM_OPPONENT_CARD_ON_FIELD,
    RANDOM_OWN_CARD_ON_FIELD,
    ALL_OPPONENT_CARDS_ON_FIELD,
    ALL_OWN_CARDS_ON_FIELD,
    ALL_CARDS_ON_FIELD,     // Includes self
    ALL_OTHER_CARDS_ON_FIELD, // Excludes self
    ATTACKING_CARD,         // The card that declared the attack
    DEFENDING_CARD,         // The card that was chosen as defender
    TRIGGERING_CARD,        // The card instance that triggered this effect (often SELF)
    
    // Specific types/synergies
    ALL_CAPYBARA_ON_FIELD,
    ALL_NON_CAPYBARA_ON_FIELD
    // ... more as needed
}
