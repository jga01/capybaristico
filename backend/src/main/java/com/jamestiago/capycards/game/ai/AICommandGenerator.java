package com.jamestiago.capycards.game.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.commands.*;
import com.jamestiago.capycards.game.dto.AbilityInfoDTO;
import com.jamestiago.capycards.game.effects.EffectProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class AICommandGenerator {
    // A single ObjectMapper instance can be reused.
    private static final ObjectMapper objectMapper = new ObjectMapper();
    // The EffectProcessor is now needed to check conditions. It's stateless, so we
    // can make it static.
    private static final EffectProcessor effectProcessor = new EffectProcessor();

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
                String jsonConfig = sourceCard.getDefinition().getEffectConfiguration();
                if (jsonConfig == null || jsonConfig.isBlank())
                    continue;

                try {
                    List<Map<String, Object>> effectConfigs = objectMapper.readValue(jsonConfig, new TypeReference<>() {
                    });

                    for (Map<String, Object> effectConfig : effectConfigs) {
                        if (!"ACTIVATED".equalsIgnoreCase((String) effectConfig.get("trigger"))) {
                            continue;
                        }

                        AbilityInfoDTO ability = new AbilityInfoDTO(
                                (Integer) effectConfig.get("abilityOptionIndex"),
                                (String) effectConfig.get("name"),
                                (String) effectConfig.get("description"),
                                (String) effectConfig.get("requiresTarget"));

                        String requiresTarget = ability.getRequiresTarget();

                        // Helper function to check condition and add command
                        BiConsumer<String, Integer> checkAndAdd = (targetId, abilityIndex) -> {
                            Map<String, Object> context = new HashMap<>();
                            context.put("game", game); // Add game object to context
                            context.put("abilityOptionIndex", abilityIndex);
                            if (targetId != null) {
                                context.put("targetCardInstanceId", targetId);
                            }

                            // Directly call the now-public method in EffectProcessor
                            if (effectProcessor.checkCondition(game, effectConfig, sourceCard, aiPlayer, context)) {
                                commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                        sourceCard.getInstanceId(), targetId, abilityIndex));
                            }
                        };

                        if (requiresTarget == null || requiresTarget.equalsIgnoreCase("NONE")) {
                            checkAndAdd.accept(null, ability.getIndex());
                        } else {
                            if (requiresTarget.equalsIgnoreCase("ANY_FIELD_CARD")
                                    || requiresTarget.equalsIgnoreCase("OPPONENT_FIELD_CARD")) {
                                opponent.getField().stream().filter(Objects::nonNull)
                                        .forEach(targetCard -> checkAndAdd.accept(targetCard.getInstanceId(),
                                                ability.getIndex()));
                            }
                            if (requiresTarget.equalsIgnoreCase("ANY_FIELD_CARD")
                                    || requiresTarget.equalsIgnoreCase("OWN_FIELD_CARD")) {
                                aiPlayer.getField().stream().filter(Objects::nonNull)
                                        .forEach(targetCard -> checkAndAdd.accept(targetCard.getInstanceId(),
                                                ability.getIndex()));
                            }
                        }
                    }
                } catch (IOException e) {
                    // Log error or handle it, for now we just skip this card's abilities
                    continue;
                }
            }
        }

        // 4. Always add EndTurnCommand as a final option
        commands.add(new EndTurnCommand(game.getGameId(), aiPlayerId));

        return commands;
    }
}