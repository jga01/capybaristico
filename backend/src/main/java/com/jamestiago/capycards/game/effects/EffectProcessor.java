package com.jamestiago.capycards.game.effects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.dto.CardInstanceDTO;
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
        context.put("game", game);

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
            case "TRIGGER_SOURCE_IS_SELF": {
                CardInstance eventSource = (CardInstance) context.get("eventSource");
                result = eventSource != null && eventSource.getInstanceId().equals(source.getInstanceId());
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
    public List<GameEvent> executeAction(Game game, Map<String, Object> effectConfig, CardInstance source,
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

        if (params != null && params.containsKey("context")) {
            context.putAll((Map<String, Object>) params.get("context"));
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
                case TRANSFORM_CARD -> handleTransformCard(game, params, source, owner, context);
                case SCHEDULE_ACTION -> handleScheduleAction(game, params, source, owner, context);
                case VANISH -> handleVanish(game, params, source, owner, context);
                case REAPPEAR -> handleReappear(game, params, source, owner, context);
                case MODIFY_FLAG -> handleModifyFlag(game, params, source, owner, context);
                case DESTROY_CARD -> handleDestroyCard(game, params, source, owner, context);
                case APPLY_AURA_BUFF -> handleApplyAuraBuff(game, params, source, owner, context);
                case CHAINED_EFFECTS -> handleChainedEffects(game, params, source, owner, context);
                case CHOOSE_RANDOM_EFFECT -> handleChooseRandomEffect(game, params, source, owner, context);
                case MODIFY_INCOMING_DAMAGE -> List.of(); // Handled elsewhere
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
    private List<GameEvent> handleScheduleAction(Game game, Map<String, Object> params, CardInstance source,
            Player owner, Map<String, Object> context) {
        // This action does not generate an event. It directly modifies the card's
        // instance state.
        Integer delay = (Integer) params.get("delayInTurns");
        Map<String, Object> scheduledEffect = (Map<String, Object>) params.get("scheduledEffect");
        if (delay == null || scheduledEffect == null)
            return List.of();

        int executionTurn = game.getTurnNumber() + delay;
        source.addScheduledAction(executionTurn, scheduledEffect);

        return List.of();
    }

    private List<GameEvent> handleVanish(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        // Vanishing is simple: remove from board, put into limbo.
        // We assume the target is SELF for now.
        return List.of(new CardVanishedEvent(
                game.getGameId(), game.getTurnNumber(), source.getInstanceId(), owner.getPlayerId()));
    }

    private List<GameEvent> handleReappear(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        // Find an empty slot on the owner's field.
        int targetSlot = -1;
        List<CardInstance> field = owner.getFieldInternal();
        for (int i = 0; i < field.size(); i++) {
            if (field.get(i) == null) {
                targetSlot = i;
                break;
            }
        }
        if (targetSlot == -1) {
            // Field is full, card is lost. Could generate a different event here.
            return List.of(new GameLogMessageEvent(game.getGameId(), game.getTurnNumber(),
                    source.getDefinition().getName() + " tried to reappear, but the field was full!", "WARN"));
        }
        return List.of(new CardReappearedEvent(
                game.getGameId(), game.getTurnNumber(), GameStateMapper.mapCardInstanceToDTO(source),
                owner.getPlayerId(), targetSlot));
    }

    private List<GameEvent> handleTransformCard(Game game, Map<String, Object> params, CardInstance source,
            Player owner, Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        // In a transformation, the target is usually the source card itself.
        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);
        String newCardId = (String) params.get("newCardId");
        if (newCardId == null) {
            logger.warn("TRANSFORM_CARD action requires a 'newCardId' parameter.");
            return events;
        }

        // We don't have access to the full definition here, so we'll create a
        // placeholder DTO.
        // The Game.apply method will use the cardId to get the full definition.
        CardInstanceDTO newCardDto = new CardInstanceDTO();
        newCardDto.setCardId(newCardId);

        // Allow the effect to specify the starting life of the new form.
        // If not specified, it will default to the base life from its definition.
        // For Chemadam, we want it to have its default life.
        Integer startingLife = (Integer) params.get("startingLife");

        for (CardInstance target : targets) {
            // If starting life isn't specified in the effect, we can't determine it here.
            // We'll set it to a default (e.g., 1) and let Game.apply figure out the real
            // value from the new definition.
            newCardDto.setCurrentLife(startingLife != null ? startingLife : 1);

            events.add(new CardTransformedEvent(
                    game.getGameId(),
                    game.getTurnNumber(),
                    target.getInstanceId(),
                    newCardDto));
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private List<GameEvent> handleApplyAuraBuff(Game game, Map<String, Object> params, CardInstance source,
            Player owner, Map<String, Object> context) {
        // This action does not generate events. It directly mutates the simulation
        // state's aura buffs.
        // The final state change will be captured by a single CardStatsChangedEvent
        // later.

        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);
        List<Map<String, Object>> buffs = (List<Map<String, Object>>) params.get("buffs");
        Map<String, Object> flags = (Map<String, Object>) params.get("flags");

        for (CardInstance target : targets) {
            if (buffs != null) {
                for (Map<String, Object> buff : buffs) {
                    String stat = (String) buff.get("stat");
                    Integer amount = (Integer) buff.get("amount");
                    if (stat != null && amount != null) {
                        target.addAuraBuff(stat, amount);
                    }
                }
            }

            if (flags != null) {
                for (Map.Entry<String, Object> flagEntry : flags.entrySet()) {
                    target.setEffectFlag(flagEntry.getKey(), flagEntry.getValue());
                }
            }
        }
        return List.of(); // No events are generated here.
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
        context.put("game", game);
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
        context.put("game", game);
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
        context.put("game", game);
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
        // Subtract the target's standard defense from the already-modified damage.
        actualDamage -= target.getCurrentDefense();

        // Now, return the final value, ensuring it's not negative.
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

    // <<< ADD THE NEW HANDLER METHOD >>>
    private List<GameEvent> handleModifyFlag(Game game, Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<GameEvent> events = new ArrayList<>();
        List<CardInstance> targets = TargetResolver.resolveCardTargets((String) params.get("targets"), source, owner,
                context, game);
        String flagName = (String) params.get("flagName");
        // "mode" can be "INCREMENT", "DECREMENT", or "SET"
        String mode = ((String) params.getOrDefault("mode", "INCREMENT")).toUpperCase();

        context.put("game", game);
        Integer amount;
        if (params.containsKey("amount")) {
            ValueResolver resolver = new ValueResolver();
            context.put("game", game);
            amount = resolver.resolveValue(params.get("amount"), source, owner, context);
        } else {
            amount = 1; // Default to 1 if the 'amount' key is not present
        }

        if (amount == null) {
            // This now only triggers if 'amount' was specified but couldn't be resolved,
            // which is a more meaningful error.
            amount = 1;
        }

        for (CardInstance target : targets) {
            int currentValue = 0;
            Object flagValue = target.getEffectFlag(flagName);
            if (flagValue instanceof Integer) {
                currentValue = (Integer) flagValue;
            }

            int newValue = switch (mode) {
                case "DECREMENT" -> currentValue - amount;
                case "SET" -> amount;
                default -> currentValue + amount; // Default to INCREMENT
            };

            // This action generates a CardFlagChangedEvent, which the Game.apply method
            // will use to update the state.
            events.add(new CardFlagChangedEvent(
                    game.getGameId(),
                    game.getTurnNumber(),
                    target.getInstanceId(),
                    flagName,
                    newValue,
                    "PERMANENT" // Numeric flags are usually permanent until reset
            ));
        }
        return events;
    }
}