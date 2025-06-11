package com.jamestiago.capycards.game.ai;

import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import java.util.Objects;

public class BoardEvaluator {
    // A higher score is better for the AI.
    public static double evaluate(Game game, String aiPlayerId) {
        Player aiPlayer = game.getPlayerById(aiPlayerId);
        Player opponent = game.getOpponent(aiPlayer);

        if (aiPlayer == null || opponent == null) {
            return 0.0;
        }

        // --- MODIFICATION: Check for immediate win/loss conditions ---
        boolean opponentHasField = opponent.getField().stream().anyMatch(Objects::nonNull);
        boolean opponentHasHand = opponent.getHand().size() > 0;
        boolean opponentHasDeck = !opponent.getDeck().isEmpty();

        // If opponent has no cards on field, no cards in hand, and no deck, it's a
        // guaranteed win.
        if (!opponentHasField && !opponentHasHand && !opponentHasDeck) {
            return Double.MAX_VALUE;
        }

        boolean aiHasField = aiPlayer.getField().stream().anyMatch(Objects::nonNull);
        // If AI has no cards and no way to play any, it's a loss.
        if (!aiHasField && aiPlayer.getHand().isEmpty()) {
            return -Double.MAX_VALUE;
        }

        double aiScore = calculatePlayerScore(aiPlayer);
        double opponentScore = calculatePlayerScore(opponent);

        // --- MODIFICATION: Add bonus for having more board control ---
        // Having more cards than the opponent is a significant advantage.
        long cardAdvantage = aiPlayer.getField().stream().filter(Objects::nonNull).count() -
                opponent.getField().stream().filter(Objects::nonNull).count();

        return (aiScore - opponentScore) + (cardAdvantage * 10.0);
    }

    private static double calculatePlayerScore(Player player) {
        double score = 0;

        // Score for cards on the field
        for (CardInstance card : player.getField()) {
            if (card != null) {
                // MODIFICATION: Weighting changed to be more aggressive.
                // Life is valuable, but threat (Attack) is more so.
                score += card.getCurrentLife() * 1.2;
                score += card.getCurrentAttack() * 1.8; // Attack is a higher priority.
                score += card.getCurrentDefense() * 0.8; // Defense is less important than life.

                // Bonus for having special abilities or positive flags
                if (card.getDefinition().getEffectConfiguration() != null
                        && !card.getDefinition().getEffectConfiguration().isBlank()) {
                    score += 3;
                }
                if (!card.getAllEffectFlags().isEmpty()) {
                    score += 2;
                }

                if (!card.isExhausted()) {
                    score += 2;
                }
            }
        }

        // Score for cards in hand (represents future potential).
        score += player.getHand().size() * 3.0;

        // A significant penalty for having an empty deck, representing fatigue risk.
        if (player.getDeck().isEmpty()) {
            score -= 50;
        }

        return score;
    }
}