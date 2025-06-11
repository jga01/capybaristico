package com.jamestiago.capycards.game.ai;

import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.model.Card;
import java.util.List;

public class AIPlayer extends Player {
    public AIPlayer(List<Card> cardDefinitionsForDeck) {
        // The display name can be randomized or have different levels
        super("OloBot", cardDefinitionsForDeck);
    }

    @Override
    public boolean isAi() {
        return true;
    }
}