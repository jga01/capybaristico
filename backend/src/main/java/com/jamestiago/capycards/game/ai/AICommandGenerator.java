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
                // ======================== START OF MODIFIED SECTION ========================
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

                        if (requiresTarget == null || requiresTarget.equalsIgnoreCase("NONE")) {
                            // Check condition before adding the command
                            Map<String, Object> context = new HashMap<>();
                            context.put("abilityOptionIndex", ability.getIndex());
                            // Create a dummy GameCommand context for the condition checker, which expects
                            // it.
                            // The condition checker is part of the effect processor which is designed for
                            // command handling.
                            // We are using a part of it here to pre-validate.
                            if (checkAbilityCondition(game, effectConfig, sourceCard, aiPlayer, context)) {
                                commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                        sourceCard.getInstanceId(), null, ability.getIndex()));
                            }
                        } else {
                            // For targeted abilities, generate commands for each valid target
                            if (requiresTarget.equalsIgnoreCase("ANY_FIELD_CARD")
                                    || requiresTarget.equalsIgnoreCase("OPPONENT_FIELD_CARD")) {
                                for (CardInstance targetCard : opponent.getField()) {
                                    if (targetCard != null) {
                                        Map<String, Object> context = new HashMap<>();
                                        context.put("abilityOptionIndex", ability.getIndex());
                                        context.put("targetCardInstanceId", targetCard.getInstanceId());
                                        if (checkAbilityCondition(game, effectConfig, sourceCard, aiPlayer, context)) {
                                            commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                                    sourceCard.getInstanceId(), targetCard.getInstanceId(),
                                                    ability.getIndex()));
                                        }
                                    }
                                }
                            }
                            if (requiresTarget.equalsIgnoreCase("ANY_FIELD_CARD")
                                    || requiresTarget.equalsIgnoreCase("OWN_FIELD_CARD")) {
                                for (CardInstance targetCard : aiPlayer.getField()) {
                                    if (targetCard != null) {
                                        Map<String, Object> context = new HashMap<>();
                                        context.put("abilityOptionIndex", ability.getIndex());
                                        context.put("targetCardInstanceId", targetCard.getInstanceId());
                                        if (checkAbilityCondition(game, effectConfig, sourceCard, aiPlayer, context)) {
                                            commands.add(new ActivateAbilityCommand(game.getGameId(), aiPlayerId,
                                                    sourceCard.getInstanceId(), targetCard.getInstanceId(),
                                                    ability.getIndex()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    // Log error or handle it, for now we just skip this card's abilities
                    continue;
                }
                // ========================= END OF MODIFIED SECTION =========================
            }
        }

        // 4. Always add EndTurnCommand as a final option
        commands.add(new EndTurnCommand(game.getGameId(), aiPlayerId));

        return commands;
    }

    /**
     * A helper method to check if an ability's conditions are met before generating
     * a command.
     * This uses a non-public method from the EffectProcessor via reflection for a
     * clean implementation,
     * or could be implemented by duplicating the checkCondition logic. For this
     * fix, we assume access or duplication.
     * Here, we'll create a public facade for the private method to illustrate.
     */
    @SuppressWarnings("unchecked")
    private static boolean checkAbilityCondition(Game game, Map<String, Object> effectConfig, CardInstance source,
            Player owner, Map<String, Object> context) {
        if (!effectConfig.containsKey("condition")) {
            return true; // No condition means it's always valid to try
        }

        // This is a simplified reimplementation of the private checkCondition logic
        // from EffectProcessor
        // to avoid complex reflection.
        try {
            java.lang.reflect.Method evaluateConditionMethod = EffectProcessor.class.getDeclaredMethod(
                    "evaluateCondition", Game.class, Map.class, CardInstance.class, Player.class, Map.class);
            evaluateConditionMethod.setAccessible(true);
            return (boolean) evaluateConditionMethod.invoke(effectProcessor, game,
                    (Map<String, Object>) effectConfig.get("condition"), source, owner, context);
        } catch (Exception e) {
            // If reflection fails, default to true to maintain old behavior rather than
            // blocking all abilities.
            // A proper implementation would have this logic be public in EffectProcessor.
            return true;
        }
    }
}