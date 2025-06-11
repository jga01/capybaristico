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

        // --- MODIFICATION START: Proactive Win/Loss Condition Check ---
        // Check if the opponent has any cards on the field. If not, this is a winning
        // state.
        boolean opponentHasCards = opponent.getField().stream().anyMatch(Objects::nonNull);
        if (!opponentHasCards) {
            // This is a simplification. A more complex check might see if the opponent can
            // play a card.
            // For now, an empty field is a strong indicator of a win.
            return Double.MAX_VALUE;
        }

        // Check if the AI has no cards on the field. This is a losing state.
        boolean aiHasCards = aiPlayer.getField().stream().anyMatch(Objects::nonNull);
        if (!aiHasCards) {
            return Double.MIN_VALUE;
        }
        // --- MODIFICATION END ---

        // The evaluation is the difference between the AI's board score and the
        // opponent's.
        double aiScore = calculatePlayerScore(aiPlayer);
        double opponentScore = calculatePlayerScore(opponent);

        return aiScore - opponentScore;
    }

    private static double calculatePlayerScore(Player player) {
        double score = 0;

        // Score for cards on the field
        for (CardInstance card : player.getField()) {
            if (card != null) {
                // Life is the most important base stat for board presence.
                score += card.getCurrentLife();
                // Attack is weighted more heavily than defense as it represents threat.
                score += card.getCurrentAttack() * 1.5;
                // Defense contributes to survivability.
                score += card.getCurrentDefense();

                // A small bonus for cards that can still act this turn.
                if (!card.isExhausted()) {
                    score += 2;
                }
            }
        }

        // Score for cards in hand (represents future potential).
        // Each card in hand is worth a bit, as it's a future option.
        score += player.getHand().size() * 3.0;

        // A significant penalty for having an empty deck, representing fatigue risk.
        if (player.getDeck().isEmpty()) {
            score -= 50;
        }

        return score;
    }
}