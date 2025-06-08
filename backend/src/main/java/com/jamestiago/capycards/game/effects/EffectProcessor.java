package com.jamestiago.capycards.game.effects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameStateMapper;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class EffectProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EffectProcessor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Random random = new Random(); // Add the static Random instance

    public EffectProcessor() {
    }

    public List<GameEvent> processTrigger(Game game, EffectTrigger triggerType, CardInstance sourceCard,
            Player sourceOwner,
            Map<String, Object> triggerContext) {

        List<GameEvent> generatedEvents = new ArrayList<>();

        if (sourceCard.getBooleanEffectFlag("status_silenced")) {
            logger.debug("Effect of {} blocked by Silence status.", sourceCard.getDefinition().getName());
            return generatedEvents;
        }

        String jsonConfig = sourceCard.getDefinition().getEffectConfiguration();
        if (jsonConfig == null || jsonConfig.trim().isEmpty() || jsonConfig.trim().equals("[]")) {
            return generatedEvents;
        }

        try {
            List<Map<String, Object>> effectConfigs = objectMapper.readValue(jsonConfig, new TypeReference<>() {
            });

            for (Map<String, Object> effectConfig : effectConfigs) {
                triggerContext.put("triggerType", triggerType);
                String configTrigger = (String) effectConfig.get("trigger");

                if (configTrigger == null || !triggerType.name().equalsIgnoreCase(configTrigger)) {
                    continue;
                }

                if (triggerType == EffectTrigger.ACTIVATED) {
                    Integer expectedIndex = (Integer) effectConfig.get("abilityOptionIndex");
                    Integer actualIndex = (Integer) triggerContext.get("abilityOptionIndex");
                    if (expectedIndex != null && !expectedIndex.equals(actualIndex)) {
                        continue;
                    }
                }

                if (checkCondition(game, effectConfig, sourceCard, sourceOwner, triggerContext)) {
                    generatedEvents.addAll(executeAction(game, effectConfig, sourceCard, sourceOwner, triggerContext));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to parse effectConfiguration for card {}: {}", sourceCard.getDefinition().getCardId(),
                    e.getMessage());
        }

        return generatedEvents;
    }

    private CardInstance getTargetFromContext(Game game, Map<String, Object> context) {
        CardInstance target = (CardInstance) context.get("eventTarget");
        if (target == null && context.containsKey("targetCardInstanceId")) {
            String targetId = (String) context.get("targetCardInstanceId");
            target = game.findCardInstanceFromAnyField(targetId);
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private boolean checkCondition(Game game, Map<String, Object> effectConfig, CardInstance source, Player owner,
            Map<String, Object> context) {
        if (!effectConfig.containsKey("condition")) {
            return true;
        }
        Map<String, Object> condition = (Map<String, Object>) effectConfig.get("condition");
        String type = (String) condition.get("type");
        Map<String, Object> params = (Map<String, Object>) condition.get("params");
        if (type == null)
            return true;

        ValueResolver valueResolver = new ValueResolver();

        logger.trace("Checking condition '{}' for card '{}'.", type, source.getDefinition().getName());
        boolean result = false; // Default to false

        switch (type.toUpperCase()) {
            case "SELF_HAS_FLAG": {
                if (params == null)
                    return true;
                String flagName = (String) params.get("flagName");
                boolean mustBeAbsent = params.containsKey("mustBeAbsent") && (boolean) params.get("mustBeAbsent");
                boolean flagPresent = source.getEffectFlag(flagName) != null;
                result = mustBeAbsent != flagPresent;
                break;
            }
            case "SOURCE_HAS_TYPE": {
                if (params == null)
                    break;
                CardInstance eventSource = (CardInstance) context.get("eventSource");
                if (eventSource == null)
                    break;
                String typeName = (String) params.get("typeName");
                result = eventSource.hasType(typeName);
                break;
            }
            case "TARGET_IS_DESTROYED": {
                CardInstance eventTarget = (CardInstance) context.get("eventTarget");
                result = eventTarget != null && eventTarget.isDestroyed();
                break;
            }
            case "TARGET_HAS_TYPE": {
                if (params == null)
                    break;
                CardInstance target = getTargetFromContext(game, context);
                if (target == null)
                    break;
                String typeName = (String) params.get("typeName");
                result = target.hasType(typeName);
                break;
            }
            case "SOURCE_HAS_CARD_ID": {
                if (params == null)
                    break;
                CardInstance eventSource = (CardInstance) context.get("eventSource");
                if (eventSource == null)
                    break;
                String cardId = (String) params.get("cardId");
                result = eventSource.getDefinition().getCardId().equals(cardId);
                break;
            }
            case "FRIENDLY_CARD_IN_PLAY": {
                if (params == null)
                    break;
                String cardId = (String) params.get("cardId");
                boolean mustBeAbsent = params.containsKey("mustBeAbsent") && (boolean) params.get("mustBeAbsent");
                boolean cardIsPresent = owner.getFieldInternal().stream()
                        .anyMatch(c -> c != null && c.getDefinition().getCardId().equals(cardId));
                result = mustBeAbsent != cardIsPresent;
                break;
            }
            case "VALUE_COMPARISON": {
                if (params == null)
                    break;
                Object sourceValue = params.get("sourceValue");
                Object targetValue = params.get("targetValue");
                String operator = (String) params.get("operator");

                Integer resolvedSourceValue = valueResolver.resolveValue(sourceValue, source, owner, context);
                Integer resolvedTargetValue = valueResolver.resolveValue(targetValue, source, owner, context);

                if (resolvedSourceValue != null && resolvedTargetValue != null) {
                    int val1 = resolvedSourceValue;
                    int val2 = resolvedTargetValue;
                    switch (operator.toUpperCase()) {
                        case "GREATER_THAN" -> {
                            result = val1 > val2;
                        }
                        case "LESS_THAN" -> {
                            result = val1 < val2;
                        }
                        case "EQUALS" -> {
                            result = val1 == val2;
                        }
                        default -> {
                            break;
                        }
                    }
                }
                break;
            }
            default:
                logger.warn("Unknown condition type: {}", type);
                result = true;
                break;
        }
        logger.trace("Condition '{}' for card '{}' evaluated to: {}", type, source.getDefinition().getName(), result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<GameEvent> executeAction(Game game, Map<String, Object> effectConfig, CardInstance source,
            Player owner, Map<String, Object> context) {
        String actionName = (String) effectConfig.get("action");
        Map<String, Object> params = (Map<String, Object>) effectConfig.get("params");
        if (actionName == null)
            return new ArrayList<>();

        if ("CUSTOM_LOGIC".equalsIgnoreCase(actionName)) {
            logger.warn("CUSTOM_LOGIC is a placeholder and should be replaced. Card: {}",
                    source.getDefinition().getCardId());
            return List.of(new GameLogMessageEvent(game.getGameId(), game.getTurnNumber(),
                    "Card " + source.getDefinition().getName() + " tried to use a custom effect.", "WARN"));
        }

        logger.trace("Executing action '{}' for card {} with params: {}", actionName, source.getDefinition().getName(),
                params);

        if (effectConfig.containsKey("context")) {
            context.putAll((Map<String, Object>) effectConfig.get("context"));
        }

        try {
            EffectActionType actionType = EffectActionType.valueOf(actionName.toUpperCase());
            return switch (actionType) {
                case DEAL_DAMAGE -> handleDealDamage(game, params, source, owner, context);
                case HEAL_TARGET -> handleHeal(game, params, source, owner, context);
                case BUFF_STAT -> handleBuffStat(game, params, source, owner, context, false);
                case DEBUFF_STAT -> handleBuffStat(game, params, source, owner, context, true);
                case DRAW_CARDS -> handleDrawCards(game, params, owner, context);
                case APPLY_FLAG -> handleApplyFlag(game, params, source, owner, context);
                case DESTROY_CARD -> handleDestroyCard(game, params, source, owner, context);
                case CHAINED_EFFECTS -> handleChainedEffects(game, params, source, owner, context);
                case CHOOSE_RANDOM_EFFECT -> handleChooseRandomEffect(game, params, source, owner, context);
                case MODIFY_INCOMING_DAMAGE, APPLY_AURA_BUFF -> List.of(); // Handled elsewhere
                default -> {
                    logger.warn("Unhandled action type in EffectProcessor: {}", actionName);
                    yield List.of();
                }
            };
        } catch (IllegalArgumentException e) {
            logger.error("Invalid action name in JSON config: {}", actionName);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<GameEvent> handleChainedEffects(Game game, Map<String, Object> params, CardInstance source,
            Player owner, Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        List<Map<String, Object>> effects = (List<Map<String, Object>>) params.get("effects");
        if (effects == null)
            return events;

        for (Map<String, Object> effectConfig : effects) {
            events.addAll(executeAction(game, effectConfig, source, owner, context));
        }
        return events;
    }

    private List<GameEvent> handleDestroyCard(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);
        for (CardInstance target : targets) {
            // A destroy action is just lethal damage.
            events.add(new CombatDamageDealtEvent(
                    game.getGameId(), game.getTurnNumber(),
                    source.getInstanceId(), target.getInstanceId(),
                    999, 999, target.getCurrentLife(), 0));
        }
        return events;
    }

    private List<GameEvent> handleApplyFlag(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);
        String flagName = (String) params.get("flagName");
        Object value = params.get("value");
        String duration = (String) params.getOrDefault("duration", "PERMANENT");

        for (CardInstance target : targets) {
            events.add(new CardFlagChangedEvent(game.getGameId(), game.getTurnNumber(), target.getInstanceId(),
                    flagName, value, duration));
        }
        return events;
    }

    private List<GameEvent> handleDrawCards(Game game, Map<String, Object> params, Player owner,
            Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        int amount = (int) params.getOrDefault("amount", 1);
        for (int i = 0; i < amount; i++) {
            if (owner.getDeck().size() - i <= 0)
                break; // Stop if deck will be empty
            // We peek at the card for the DTO but the actual draw happens in Game.apply()
            CardInstance cardToDraw = owner.getDeck().getCards().get(i);
            events.add(new PlayerDrewCardEvent(
                    game.getGameId(), game.getTurnNumber(), owner.getPlayerId(),
                    GameStateMapper.mapCardInstanceToDTO(cardToDraw),
                    owner.getHand().size() + i + 1,
                    owner.getDeck().size() - i - 1));
        }
        return events;
    }

    private List<GameEvent> handleBuffStat(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context, boolean isDebuff) {
        List<GameEvent> events = new ArrayList<>();
        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);
        String stat = (String) params.get("stat");
        boolean isPermanent = (boolean) params.getOrDefault("isPermanent", true);

        ValueResolver resolver = new ValueResolver();
        Integer amount = resolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null)
            return events;

        int finalAmount = isDebuff ? -Math.abs(amount) : Math.abs(amount);

        for (CardInstance target : targets) {
            int statAfter = 0;
            switch (stat.toUpperCase()) {
                case "ATK":
                    statAfter = target.getCurrentAttack() + finalAmount;
                    break;
                case "DEF":
                    statAfter = target.getCurrentDefense() + finalAmount;
                    break;
                case "MAX_LIFE":
                    statAfter = target.getCurrentLife() + finalAmount;
                    break; // Approximated
            }
            if (isDebuff) {
                events.add(new CardDebuffedEvent(game.getGameId(), game.getTurnNumber(), target.getInstanceId(), stat,
                        Math.abs(finalAmount), isPermanent, statAfter));
            } else {
                events.add(new CardBuffedEvent(game.getGameId(), game.getTurnNumber(), target.getInstanceId(), stat,
                        finalAmount, isPermanent, statAfter));
            }
        }
        return events;
    }

    private List<GameEvent> handleHeal(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);

        ValueResolver resolver = new ValueResolver();
        Integer amount = resolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null || amount <= 0)
            return events;

        for (CardInstance target : targets) {
            int maxLife = target.getDefinition().getInitialLife()
                    + target.temporaryStatBuffs.getOrDefault("MAX_LIFE", 0);
            int lifeAfter = Math.min(maxLife, target.getCurrentLife() + amount);
            events.add(new CardHealedEvent(game.getGameId(), game.getTurnNumber(), target.getInstanceId(), amount,
                    lifeAfter));
        }
        return events;
    }

    private List<GameEvent> handleDealDamage(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);

        ValueResolver valueResolver = new ValueResolver();
        Integer amount = valueResolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null || amount <= 0)
            return events;

        for (CardInstance target : targets) {
            int finalDamage = calculateFinalDamage(game, amount, source, target);

            events.add(new com.jamestiago.capycards.game.events.CombatDamageDealtEvent(
                    game.getGameId(),
                    game.getTurnNumber(),
                    source.getInstanceId(),
                    target.getInstanceId(),
                    amount, // raw damage
                    finalDamage,
                    target.getCurrentLife(),
                    Math.max(0, target.getCurrentLife() - finalDamage)));
        }
        return events;
    }

    private int calculateFinalDamage(Game game, int baseDamage, CardInstance source, CardInstance target) {
        int actualDamage = baseDamage;
        Player targetOwner = game.getOwnerOfCardInstance(target);
        String configJson = target.getDefinition().getEffectConfiguration();

        if (configJson != null && !configJson.isBlank()) {
            try {
                List<Map<String, Object>> effectConfigs = objectMapper.readValue(configJson, new TypeReference<>() {
                });
                for (Map<String, Object> config : effectConfigs) {
                    if ("CONTINUOUS_DEFENSIVE".equalsIgnoreCase((String) config.get("trigger"))
                            && "MODIFY_INCOMING_DAMAGE".equalsIgnoreCase((String) config.get("action"))) {
                        Map<String, Object> defensiveContext = new HashMap<>();
                        defensiveContext.put("eventSource", source);
                        defensiveContext.put("damageAmount", baseDamage);
                        if (checkCondition(game, config, target, targetOwner, defensiveContext)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> params = (Map<String, Object>) config.get("params");
                            String mode = (String) params.get("mode");
                            if ("SET_ABSOLUTE".equalsIgnoreCase(mode)) {
                                actualDamage = (int) params.get("amount");
                            } else if ("REDUCE_BY".equalsIgnoreCase(mode)) {
                                actualDamage -= (int) params.get("amount");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                /* ignore */ }
        }
        return Math.max(0, actualDamage);
    }

    // NEW METHOD TO HANDLE THE LOGIC
    @SuppressWarnings("unchecked")
    private List<GameEvent> handleChooseRandomEffect(Game game, Map<String, Object> params, CardInstance source,
            Player owner, Map<String, Object> context) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) params.get("choices");

        if (choices == null || choices.isEmpty()) {
            logger.warn("CHOOSE_RANDOM_EFFECT for {} had no choices.", source.getDefinition().getName());
            return List.of();
        }

        // Select a random effect configuration from the list
        int randomIndex = random.nextInt(choices.size());
        Map<String, Object> chosenEffectConfig = choices.get(randomIndex);

        logger.trace("Randomly chose effect #{} for {}: {}", randomIndex, source.getDefinition().getName(),
                chosenEffectConfig.get("action"));

        // Recursively call executeAction with the chosen effect.
        // This allows the chosen effect to be any other action (e.g., DEAL_DAMAGE,
        // APPLY_FLAG).
        return executeAction(game, chosenEffectConfig, source, owner, context);
    }
}