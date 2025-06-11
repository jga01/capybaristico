package com.jamestiago.capycards.game.ai;

import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameStateMapper;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.commands.*;
import com.jamestiago.capycards.game.dto.AbilityInfoDTO;
import java.util.ArrayList;
import java.util.List;

public class AICommandGenerator {
    public static List<GameCommand> generateValidCommands(Game game, String aiPlayerId) {
        List<GameCommand> commands = new ArrayList<>();
        Player aiPlayer = game.getPlayerById(aiPlayerId);
        Player opponent = game.getOpponent(aiPlayer);

        if (aiPlayer == null || opponent == null || !game.getCurrentPlayer().getPlayerId().equals(aiPlayerId)) {
            return commands; // Not AI's turn or invalid state
        }

        // 1. Generate PlayCardCommands
        for (int handIndex = 0; handIndex < aiPlayer.getHand().size(); handIndex++) {
            CardInstance cardInHand = aiPlayer.getHand().get(handIndex);
            if (cardInHand.getDefinition().isDirectlyPlayable()) {
                for (int fieldIndex = 0; fieldIndex < Player.MAX_FIELD_SIZE; fieldIndex++) {
                    if (aiPlayer.getField().get(fieldIndex) == null) {
                        commands.add(new PlayCardCommand(game.getGameId(), aiPlayerId, handIndex, fieldIndex));
                    }
                }
            }
        }

        // 2. Generate AttackCommands
        if (aiPlayer.canDeclareAttack()) {
            for (int attackerIndex = 0; attackerIndex < aiPlayer.getField().size(); attackerIndex++) {
                CardInstance attacker = aiPlayer.getField().get(attackerIndex);
                if (attacker != null && !attacker.isExhausted()
                        && !attacker.getBooleanEffectFlag("status_cannot_attack_AURA")) {
                    for (int defenderIndex = 0; defenderIndex < opponent.getField().size(); defenderIndex++) {
                        CardInstance defender = opponent.getField().get(defenderIndex);
                        if (defender != null && !defender.getBooleanEffectFlag("status_cannot_be_targeted_AURA")) {
                            commands.add(new AttackCommand(game.getGameId(), aiPlayerId, attackerIndex, defenderIndex));
                        }
                    }
                }
            }
        }

        // 3. Generate ActivateAbilityCommands
        for (int fieldIndex = 0; fieldIndex < aiPlayer.getField().size(); fieldIndex++) {
            CardInstance sourceCard = aiPlayer.getField().get(fieldIndex);
            if (sourceCard != null && !sourceCard.isExhausted()) {
                List<AbilityInfoDTO> abilities = GameStateMapper
                        .parseAbilities(sourceCard.getDefinition().getEffectConfiguration());

                for (AbilityInfoDTO ability : abilities) {
                    String requiresTarget = ability.getRequiresTarget();
                    if (requiresTarget == null || requiresTarget.equalsIgnoreCase("NONE")) {
                        commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                sourceCard.getInstanceId(), null, ability.getIndex()));
                    } else {
                        // For targeted abilities, generate commands for each valid target
                        if (requiresTarget.equalsIgnoreCase("ANY_FIELD_CARD")
                                || requiresTarget.equalsIgnoreCase("OPPONENT_FIELD_CARD")) {
                            for (CardInstance targetCard : opponent.getField()) {
                                if (targetCard != null) {
                                    commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                            sourceCard.getInstanceId(), targetCard.getInstanceId(),
                                            ability.getIndex()));
                                }
                            }
                        }
                        if (requiresTarget.equalsIgnoreCase("ANY_FIELD_CARD")
                                || requiresTarget.equalsIgnoreCase("OWN_FIELD_CARD")) {
                            for (CardInstance targetCard : aiPlayer.getField()) {
                                if (targetCard != null) {
                                    commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                            sourceCard.getInstanceId(), targetCard.getInstanceId(),
                                            ability.getIndex()));
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Always add EndTurnCommand as a final option
        commands.add(new EndTurnCommand(game.getGameId(), aiPlayerId));

        return commands;
    }
}