package com.jamestiago.capycards.game.effects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

public class EffectProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EffectProcessor.class);
    private final Game game;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();
    private final ValueResolver valueResolver;

    public EffectProcessor(Game game) {
        this.game = game;
        this.valueResolver = new ValueResolver(game);
    }

    public void processTrigger(EffectTrigger triggerType, CardInstance sourceCard, Player sourceOwner,
            Map<String, Object> triggerContext) {
        // Enforce Silence status
        if (sourceCard.getBooleanEffectFlag("status_silenced")) {
            logger.debug("Effect of {} blocked by Silence status.", sourceCard.getDefinition().getName());
            return;
        }

        String jsonConfig = sourceCard.getDefinition().getEffectConfiguration();
        if (jsonConfig == null || jsonConfig.trim().isEmpty() || jsonConfig.trim().equals("[]")) {
            return;
        }

        try {
            List<Map<String, Object>> effectConfigs = objectMapper.readValue(jsonConfig, new TypeReference<>() {
            });

            for (Map<String, Object> effectConfig : effectConfigs) {
                // Add the global trigger type to the context for custom logic handlers
                triggerContext.put("triggerType", triggerType);

                String configTrigger = (String) effectConfig.get("trigger");
                // CONTINUOUS_AURA and CUSTOM_LOGIC (if untriggered) are special cases
                if ((triggerType == EffectTrigger.CONTINUOUS_AURA && triggerType.name().equalsIgnoreCase(configTrigger))
                        ||
                        (configTrigger == null
                                && "CUSTOM_LOGIC".equalsIgnoreCase((String) effectConfig.get("action")))) {
                    executeAction(effectConfig, sourceCard, sourceOwner, triggerContext);
                    continue;
                }

                // Standard trigger matching
                if (configTrigger == null || !triggerType.name().equalsIgnoreCase(configTrigger)) {
                    continue;
                }

                logger.trace("Card {} matched trigger {}", sourceCard.getDefinition().getName(), triggerType);

                if (triggerType == EffectTrigger.ACTIVATED) {
                    Integer expectedIndex = (Integer) effectConfig.get("abilityOptionIndex");
                    Integer actualIndex = (Integer) triggerContext.get("abilityOptionIndex");
                    if (expectedIndex != null && !expectedIndex.equals(actualIndex)) {
                        continue;
                    }
                }

                if (checkCondition(effectConfig, sourceCard, sourceOwner, triggerContext)) {
                    executeAction(effectConfig, sourceCard, sourceOwner, triggerContext);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to parse effectConfiguration for card {}: {}", sourceCard.getDefinition().getCardId(),
                    e.getMessage());
        }
    }

    private CardInstance getTargetFromContext(Map<String, Object> context) {
        CardInstance target = (CardInstance) context.get("eventTarget");
        if (target == null && context.containsKey("targetCardInstanceId")) {
            String targetId = (String) context.get("targetCardInstanceId");
            target = game.findCardInstanceFromAnyField(targetId);
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private boolean checkCondition(Map<String, Object> effectConfig, CardInstance source, Player owner,
            Map<String, Object> context) {
        if (!effectConfig.containsKey("condition")) {
            return true; // No condition means it always passes
        }
        Map<String, Object> condition = (Map<String, Object>) effectConfig.get("condition");
        String type = (String) condition.get("type");
        Map<String, Object> params = (Map<String, Object>) condition.get("params");
        if (type == null)
            return true;

        switch (type.toUpperCase()) {
            case "SELF_HAS_FLAG": {
                if (params == null)
                    return true;
                String flagName = (String) params.get("flagName");
                boolean mustBeAbsent = params.containsKey("mustBeAbsent") && (boolean) params.get("mustBeAbsent");
                boolean flagPresent = source.getEffectFlag(flagName) != null;
                return mustBeAbsent != flagPresent;
            }

            case "SOURCE_HAS_TYPE": {
                if (params == null)
                    return false;
                CardInstance eventSource = (CardInstance) context.get("eventSource");
                if (eventSource == null)
                    return false;
                String typeName = (String) params.get("typeName");
                return eventSource.hasType(typeName);
            }

            case "SOURCE_HAS_CARD_ID": {
                if (params == null)
                    return false;
                CardInstance eventSource = (CardInstance) context.get("eventSource");
                if (eventSource == null)
                    return false;
                String cardId = (String) params.get("cardId");
                return cardId != null && cardId.equals(eventSource.getDefinition().getCardId());
            }

            case "TARGET_IS_DESTROYED": {
                CardInstance eventTarget = (CardInstance) context.get("eventTarget");
                return eventTarget != null && eventTarget.isDestroyed();
            }

            case "TARGET_HAS_TYPE": {
                if (params == null)
                    return false;
                CardInstance target = getTargetFromContext(context);
                if (target == null)
                    return false;
                String typeName = (String) params.get("typeName");
                return target.hasType(typeName);
            }

            case "TARGET_HAS_CARD_ID": {
                if (params == null)
                    return false;
                CardInstance target = getTargetFromContext(context);
                if (target == null)
                    return false;
                String cardId = (String) params.get("cardId");
                return cardId != null && cardId.equals(target.getDefinition().getCardId());
            }

            case "EVENT_TARGET_HAS_CARD_ID": { // Legacy, prefer TARGET_HAS_CARD_ID
                if (params == null)
                    return false;
                CardInstance eventTarget = (CardInstance) context.get("eventTarget");
                if (eventTarget == null)
                    return false;
                String cardId = (String) params.get("cardId");
                return cardId != null && cardId.equals(eventTarget.getDefinition().getCardId());
            }

            case "FRIENDLY_CARD_IN_PLAY": {
                if (params == null)
                    return false;
                String cardId = (String) params.get("cardId");
                return owner.getFieldInternal().stream()
                        .anyMatch(c -> c != null && c.getDefinition().getCardId().equals(cardId));
            }

            case "FRIENDLY_CARD_WITH_TYPE_IN_PLAY": {
                if (params == null)
                    return false;
                String typeName = (String) params.get("typeName");
                // Ensure we don't count the card itself for its own buff
                return owner.getFieldInternal().stream()
                        .anyMatch(c -> c != null && !c.getInstanceId().equals(source.getInstanceId())
                                && c.hasType(typeName));
            }

            case "LOGIC_OPERATOR": {
                if (params == null)
                    return true;
                String operator = ((String) params.get("operator")).toUpperCase();
                List<Map<String, Object>> subConditions = (List<Map<String, Object>>) params.get("conditions");
                if (subConditions == null || subConditions.isEmpty())
                    return true;

                switch (operator) {
                    case "AND":
                        return subConditions.stream()
                                .allMatch(sub -> this.checkCondition(Map.of("condition", sub), source, owner, context));
                    case "OR":
                        return subConditions.stream()
                                .anyMatch(sub -> this.checkCondition(Map.of("condition", sub), source, owner, context));
                    case "NOT":
                        return !this.checkCondition(Map.of("condition", subConditions.get(0)), source, owner, context);
                    default:
                        logger.warn("Unknown logic operator: {}", operator);
                        return true;
                }
            }

            case "VALUE_COMPARISON": {
                if (params == null)
                    return false;
                Object sourceValue = params.get("sourceValue");
                Object targetValue = params.get("targetValue");
                String operator = (String) params.get("operator");

                Integer resolvedSourceValue = valueResolver.resolveValue(sourceValue, source, owner, context);
                Integer resolvedTargetValue = valueResolver.resolveValue(targetValue, source, owner, context);

                if (resolvedSourceValue != null && resolvedTargetValue != null) {
                    int val1 = resolvedSourceValue;
                    int val2 = resolvedTargetValue;
                    switch (operator.toUpperCase()) {
                        case "GREATER_THAN":
                            return val1 > val2;
                        case "LESS_THAN":
                            return val1 < val2;
                        case "GREATER_THAN_OR_EQUAL":
                            return val1 >= val2;
                        case "LESS_THAN_OR_EQUAL":
                            return val1 <= val2;
                        case "EQUALS":
                            return val1 == val2;
                        case "NOT_EQUALS":
                            return val1 != val2;
                        default:
                            return false;
                    }
                }
                return false;
            }

            default:
                logger.warn("Unknown condition type: {}", type);
                return true;
        }
    }

    @SuppressWarnings("unchecked")
    private void executeAction(Map<String, Object> effectConfig, CardInstance source, Player owner,
            Map<String, Object> context) {
        String actionName = (String) effectConfig.get("action");
        Map<String, Object> params = (Map<String, Object>) effectConfig.get("params");
        if (actionName == null)
            return;

        logger.debug("Executing action '{}' for card {}", actionName, source.getDefinition().getName());

        // Pass any "context" from the effect config into the main trigger context
        if (effectConfig.containsKey("context")) {
            context.putAll((Map<String, Object>) effectConfig.get("context"));
        }

        switch (actionName.toUpperCase()) {
            case "MODIFY_STAT":
                handleModifyStat(params, source, owner, context);
                break;
            case "DEAL_DAMAGE":
                handleDealDamage(params, source, owner, context);
                break;
            case "HEAL":
                handleHeal(params, source, owner, context);
                break;
            case "DRAW_CARDS":
                handleDrawCards(params, source, owner, context);
                break;
            case "DESTROY_CARD":
                handleDestroyCard(params, source, owner, context);
                break;
            case "APPLY_AURA_BUFF":
                handleApplyAuraBuff(params, source, owner);
                break;
            case "TRANSFORM_CARD":
                handleTransformCard(params, source, owner, context);
                break;
            case "CHOOSE_RANDOM_EFFECT":
                handleChooseRandomEffect(params, source, owner, context);
                break;
            case "CHAINED_EFFECTS":
                handleChainedEffects(params, source, owner, context);
                break;
            case "APPLY_STATUS":
                handleApplyStatus(params, source, owner, context);
                break;
            case "REMOVE_FLAG":
                handleRemoveFlag(params, source, owner, context);
                break;
            case "MODIFY_FLAG":
                handleModifyFlag(params, source, owner, context);
                break;
            case "MODIFY_INCOMING_DAMAGE": // This is a marker action for applyDamage, no direct execution here
                break;
            case "CUSTOM_LOGIC":
                handleCustomLogic((String) params.get("logicKey"), source, owner, context);
                break;
            default:
                logger.warn("Unknown action name: {}", actionName);
        }

        if (effectConfig.containsKey("flags")) {
            Map<String, Object> flags = (Map<String, Object>) effectConfig.get("flags");
            if (flags.containsKey("oncePerInstanceId")) {
                source.setEffectFlag((String) flags.get("oncePerInstanceId"), true);
            }
            if (flags.containsKey("setInstanceFlags")) {
                List<Map<String, Object>> instanceFlags = (List<Map<String, Object>>) flags.get("setInstanceFlags");
                for (Map<String, Object> flagInfo : instanceFlags) {
                    String name = (String) flagInfo.get("name");
                    Object value = flagInfo.get("value");
                    String duration = (String) flagInfo.get("duration");
                    if ("TURN".equalsIgnoreCase(duration)) {
                        name = name + "ThisTurn";
                    }
                    source.setEffectFlag(name, value);
                    logger.debug("Set instance flag '{}' to '{}' on {}", name, value, source.getDefinition().getName());
                }
            }
        }
    }

    private void handleModifyStat(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        String stat = ((String) params.get("stat")).toUpperCase();
        Integer amount = valueResolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null)
            return;

        boolean isPermanent = params.get("isPermanent") != null && (boolean) params.get("isPermanent");

        for (CardInstance target : targets) {
            if (isPermanent) {
                switch (stat) {
                    case "ATK":
                        target.setBaseAttack(target.getCurrentAttack() + amount);
                        break;
                    case "DEF":
                        target.setBaseDefense(target.getCurrentDefense() + amount);
                        break;
                    case "MAX_LIFE":
                        target.setBaseLife(target.getDefinition().getInitialLife() + amount);
                        break;
                    case "LIFE":
                        target.heal(amount);
                        break; // Permanent life change is healing or damage
                }
            } else {
                target.addTemporaryBuff(stat, amount);
            }
            logger.info("Stat {} of {} modified by {}.", stat, target.getDefinition().getName(), amount);
        }
    }

    private void handleDealDamage(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        Integer amount = valueResolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null)
            return;

        for (CardInstance target : targets) {
            Player targetOwner = game.getOwnerOfCardInstance(target);
            this.applyDamage(target, amount, source, targetOwner, owner);
        }
    }

    private void handleHeal(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        Integer amount = valueResolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null)
            return;

        for (CardInstance target : targets) {
            target.heal(amount);
            logger.info("{} healed {} for {} life.", source.getDefinition().getName(), target.getDefinition().getName(),
                    amount);
        }
    }

    private void handleDrawCards(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        Integer amount = valueResolver.resolveValue(params.get("amount"), source, owner, context);
        if (amount == null)
            return;

        logger.info("{}'s effect causes {} to draw {} cards.", source.getDefinition().getName(), owner.getDisplayName(),
                amount);
        for (int i = 0; i < amount; i++) {
            if (owner.getHand().size() < Player.MAX_HAND_SIZE) {
                owner.drawCard();
            }
        }
    }

    private void handleDestroyCard(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        for (CardInstance target : targets) {
            logger.info("{}'s effect destroys {}.", source.getDefinition().getName(), target.getDefinition().getName());
            target.setCurrentLife(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleApplyAuraBuff(Map<String, Object> params, CardInstance source, Player owner) {
        String synergyCardId = (String) params.get("synergyCardId");
        String synergyType = (String) params.get("synergyType");
        String flagName = (String) params.get("flagName");
        List<Map<String, Object>> buffs = (List<Map<String, Object>>) params.get("buffs");
        if (flagName == null || buffs == null)
            return;
        if (synergyCardId == null && synergyType == null)
            return;

        boolean isBuffCurrentlyActive = source.getBooleanEffectFlag(flagName);

        // Check for presence of synergy card or type
        boolean synergyIsPresent;
        if (synergyCardId != null) {
            synergyIsPresent = owner.getFieldInternal().stream()
                    .anyMatch(c -> c != null && synergyCardId.equals(c.getDefinition().getCardId()));
        } else { // synergyType must not be null here
            synergyIsPresent = owner.getFieldInternal().stream()
                    .anyMatch(c -> c != null && !c.getInstanceId().equals(source.getInstanceId())
                            && c.hasType(synergyType));
        }

        if (synergyIsPresent && !isBuffCurrentlyActive) {
            // Apply buffs
            source.setEffectFlag(flagName, true);
            for (Map<String, Object> buff : buffs) {
                String stat = ((String) buff.get("stat")).toUpperCase();
                int amount = (int) buff.get("amount");
                source.addTemporaryBuff(stat, amount);
                if ("LIFE".equals(stat) || "MAX_LIFE".equals(stat)) {
                    source.heal(amount);
                }
            }
            logger.info("{}'s aura activates. Buffs applied.", source.getDefinition().getName());

        } else if (!synergyIsPresent && isBuffCurrentlyActive) {
            // Remove buffs
            source.removeEffectFlag(flagName);
            for (Map<String, Object> buff : buffs) {
                String stat = ((String) buff.get("stat")).toUpperCase();
                int amount = (int) buff.get("amount");
                source.addTemporaryBuff(stat, -amount);
            }
            int maxLife = source.getDefinition().getInitialLife()
                    + source.temporaryStatBuffs.getOrDefault("MAX_LIFE", 0);
            if (source.getCurrentLife() > maxLife) {
                source.setCurrentLife(maxLife);
            }
            logger.info("{}'s aura deactivates. Buffs removed.", source.getDefinition().getName());
        }
    }

    private void handleTransformCard(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);

        for (CardInstance target : targets) {
            logger.info("Transforming card: {}", target.getDefinition().getName());
            if (params.containsKey("baseAttack")) {
                target.setBaseAttack((Integer) params.get("baseAttack"));
            }
            if (params.containsKey("baseDefense")) {
                target.setBaseDefense((Integer) params.get("baseDefense"));
            }
            if (params.containsKey("baseLife")) {
                int newBaseLife = (Integer) params.get("baseLife");
                target.setBaseLife(newBaseLife);
                // Adjust current life to be no more than the new base life
                if (target.getCurrentLife() > newBaseLife) {
                    target.setCurrentLife(newBaseLife);
                }
            }
            // Future enhancement: change card type
            // if (params.containsKey("newType")) {
            // target.getDefinition().setType((String) params.get("newType"));
            // }
            logger.info("New stats for {}: L:{} A:{} D:{}", target.getDefinition().getName(), target.getCurrentLife(),
                    target.getCurrentAttack(), target.getCurrentDefense());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleChooseRandomEffect(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) params.get("choices");
        if (choices == null || choices.isEmpty()) {
            logger.warn("CHOOSE_RANDOM_EFFECT action for {} has no choices.", source.getDefinition().getName());
            return;
        }

        int choiceIndex = random.nextInt(choices.size());
        Map<String, Object> chosenEffect = choices.get(choiceIndex);

        logger.info("{}'s random effect chose option {} out of {}.", source.getDefinition().getName(), choiceIndex + 1,
                choices.size());

        // Recursively call executeAction with the chosen effect block
        // The chosen effect block does not need a "trigger" key
        this.executeAction(chosenEffect, source, owner, context);
    }

    @SuppressWarnings("unchecked")
    private void handleChainedEffects(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        List<Map<String, Object>> effects = (List<Map<String, Object>>) params.get("effects");
        if (effects == null || effects.isEmpty()) {
            logger.warn("CHAINED_EFFECTS action for {} has no effects to chain.", source.getDefinition().getName());
            return;
        }

        logger.info("{} is executing a chain of {} effects.", source.getDefinition().getName(), effects.size());
        for (Map<String, Object> chainedEffect : effects) {
            this.executeAction(chainedEffect, source, owner, context);
        }
    }

    private void handleApplyStatus(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        String statusName = (String) params.get("statusName");

        for (CardInstance target : targets) {
            String flagName = "status_" + statusName.toLowerCase();
            target.setEffectFlag(flagName, true);
            logger.info("{} applied status '{}' to {}.", source.getDefinition().getName(), statusName,
                    target.getDefinition().getName());
        }
    }

    private void handleRemoveFlag(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        String flagName = (String) params.get("flagName");

        for (CardInstance target : targets) {
            target.removeEffectFlag(flagName);
            logger.info("Removed flag '{}' from {}.", flagName, target.getDefinition().getName());
        }
    }

    private void handleModifyFlag(Map<String, Object> params, CardInstance source, Player owner,
            Map<String, Object> context) {
        String targetSelector = (String) params.get("targets");
        List<CardInstance> targets = TargetResolver.resolveCardTargets(targetSelector, source, owner, context, game);
        String flagName = (String) params.get("flagName");
        String operation = ((String) params.get("operation")).toUpperCase();

        for (CardInstance target : targets) {
            Object currentValueObj = target.getEffectFlagOrDefault(flagName, 0);
            int currentValue = (currentValueObj instanceof Integer) ? (Integer) currentValueObj : 0;

            switch (operation) {
                case "INCREMENT":
                    target.setEffectFlag(flagName, currentValue + 1);
                    logger.debug("Incremented flag '{}' on {} to {}", flagName, target.getDefinition().getName(),
                            currentValue + 1);
                    break;
                case "DECREMENT":
                    target.setEffectFlag(flagName, currentValue - 1);
                    logger.debug("Decremented flag '{}' on {} to {}", flagName, target.getDefinition().getName(),
                            currentValue - 1);
                    break;
                case "SET":
                    Integer value = valueResolver.resolveValue(params.get("value"), source, owner, context);
                    if (value != null) {
                        target.setEffectFlag(flagName, value);
                        logger.debug("Set flag '{}' on {} to {}", flagName, target.getDefinition().getName(), value);
                    }
                    break;
                default:
                    logger.warn("Unknown MODIFY_FLAG operation: {}", operation);
            }
        }
    }

    public void applyDamage(CardInstance target, int baseDamage, CardInstance source, Player targetOwner,
            Player sourceOwner) {
        if (target == null || target.isDestroyed() || baseDamage <= 0)
            return;

        int actualDamage = baseDamage;
        String sourceName = (source != null && source.getDefinition() != null) ? source.getDefinition().getName()
                : "Effect";

        // Defensive effect check
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
                        defensiveContext.put("damageAmount", baseDamage); // For VALUE_COMPARISON
                        if (checkCondition(config, target, targetOwner, defensiveContext)) {
                            Map<String, Object> params = (Map<String, Object>) config.get("params");
                            String mode = (String) params.get("mode");
                            if ("SET_ABSOLUTE".equalsIgnoreCase(mode)) {
                                actualDamage = (int) params.get("amount");
                                logger.info("{}'s defensive effect changed incoming damage to {}",
                                        target.getDefinition().getName(), actualDamage);
                            } else if ("REDUCE_BY".equalsIgnoreCase(mode)) {
                                actualDamage -= (int) params.get("amount");
                                logger.info("{}'s defensive effect reduced incoming damage by {} to {}",
                                        target.getDefinition().getName(), (int) params.get("amount"), actualDamage);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                /* ignore */ }
        }

        if (actualDamage <= 0) {
            logger.info("Damage to {} from {} was reduced to 0 or less.", target.getDefinition().getName(), sourceName);
            return;
        }

        String targetName = target.getDefinition().getName();
        logger.info("Applying {} damage to {} from {}", actualDamage, targetName, sourceName);
        target.takeDamage(actualDamage, source);
        logger.info("{} new life: {}", targetName, target.getCurrentLife());

        Map<String, Object> takenContext = new HashMap<>();
        takenContext.put("eventSource", source);
        this.processTrigger(EffectTrigger.ON_DAMAGE_TAKEN, target, targetOwner, takenContext);

        if (source != null && sourceOwner != null && !source.isDestroyed()) {
            Map<String, Object> dealtContext = new HashMap<>();
            dealtContext.put("eventTarget", target);
            this.processTrigger(EffectTrigger.ON_DAMAGE_DEALT, source, sourceOwner, dealtContext);
        }
    }

    private void handleCustomLogic(String logicKey, CardInstance source, Player owner, Map<String, Object> context) {
        if (logicKey == null)
            return;
        EffectTrigger trigger = (EffectTrigger) context.get("triggerType");

        switch (logicKey) {
            case "KAHINA":
                handleKahina(source, owner, context, trigger);
                break;
            case "SWETTIE":
                handleSwettie(source, owner, context, trigger);
                break;
            case "FLOPPY":
                handleFloppy(source, owner, context, trigger);
                break;
            case "TOMATE":
                handleTomate(source, owner, context, trigger);
                break;
            case "FAMOUSBUILDER":
                handleFamousBuilder(source, owner, context, trigger);
                break;
            case "OLIVIO":
                handleOlivio(source, owner, context, trigger);
                break;
            case "APPLEOFECHO":
                handleAppleOfEcho(source, owner, context, trigger);
                break;
            case "CHEMADAM_SPECIALS": // Keeping existing partial migrations
                // This logic is for Floppy/Crazysoup transformation, which is complex.
                break;
            case "GLOIRE_REVIVE_AND_LIFE_COPY":
                // This logic is for revival and life copy, which is complex.
                break;
            case "PH_HEAL":
                // All of PH's logic is now generic. This can be removed from card JSON.
                break;
            case "KIZER_HEAL":
                // All of Kizer's logic is now generic. This can be removed from card JSON.
                break;
            case "MENINO_VENENO":
                // This logic handles Menino's attack restriction and instant death.
                break;
            default:
                logger.trace("No specific handler for custom logic key: {}", logicKey);
                break;
        }
    }

    private void handleKahina(CardInstance kahina, Player owner, Map<String, Object> context, EffectTrigger trigger) {
        if (trigger == EffectTrigger.ON_DEATH_OF_ANY) {
            kahina.heal(1);
            logger.info("Another card was destroyed, Kahina heals to {}.", kahina.getCurrentLife());
        }

        boolean isInBerserk = kahina.getBooleanEffectFlag("kahina_berserk");
        if (!isInBerserk && kahina.getCurrentLife() >= 50) {
            kahina.setEffectFlag("kahina_berserk", true);
            kahina.setBaseAttack(50);
            logger.info("Kahina reaches 50 life and enters berserk mode! ATK is now 50.");
        }

        if (isInBerserk && trigger == EffectTrigger.ON_DAMAGE_DEALT) {
            CardInstance target = (CardInstance) context.get("eventTarget");
            if (target != null && target.isDestroyed()) {
                logger.info("Kahina killed a card while in berserk mode and dies as a consequence.");
                kahina.setCurrentLife(0);
            }
        }

        if (trigger == EffectTrigger.ON_DAMAGE_DEALT) {
            CardInstance target = (CardInstance) context.get("eventTarget");
            if (target != null && target.hasType("Capybara")) {
                logger.info("Kahina damaged a Capybara and dies instantly.");
                kahina.setCurrentLife(0);
            }
        }
    }

    private void handleSwettie(CardInstance swettie, Player owner, Map<String, Object> context, EffectTrigger trigger) {
        // Scottish Von ability
        if (trigger == EffectTrigger.ACTIVATED && context.get("abilityOptionIndex").equals(0)) {
            if (swettie.getBooleanEffectFlag("swettie_side_b_used")) {
                swettie.setEffectFlag("swettie_side_b_used", false); // Revive side B
                swettie.setCurrentLife(swettie.getCurrentLife() / 2);
                swettie.addTemporaryBuff("ATK", -swettie.getCurrentAttack()); // Cannot attack this turn
                logger.info("Swettie uses Scottish Von, reviving her other side at the cost of half her life.");
            } else {
                logger.info("Swettie tried to use Scottish Von, but her other side is still active.");
            }
            return;
        }

        // Death trigger to flip sides
        if (trigger == EffectTrigger.ON_DEATH) {
            if (!swettie.getBooleanEffectFlag("swettie_side_b_used")) {
                swettie.setEffectFlag("swettie_side_b_used", true);
                swettie.heal(swettie.getDefinition().getInitialLife()); // Full heal for the "other side"
                // This 'isActuallyDead' flag would require a change in Game.java's death loop.
                // For now, the heal effectively prevents death for the first time.
                context.put("isActuallyDead", false);
                logger.info("Swettie's first side is defeated! She flips to her other side.");
            } else {
                context.put("isActuallyDead", true); // Second side defeated, she really dies.
                logger.info("Swettie's second side is defeated! She is now removed from the game.");
            }
        }
    }

    private void handleFloppy(CardInstance floppy, Player owner, Map<String, Object> context, EffectTrigger trigger) {
        if (trigger == EffectTrigger.START_OF_TURN_SELF) {
            floppy.removeEffectFlag("killsThisTurn_Floppy");
        }

        if (trigger == EffectTrigger.ON_DAMAGE_DEALT) {
            CardInstance target = (CardInstance) context.get("eventTarget");
            if (target != null && target.isDestroyed()) {
                if (!target.hasType("European") && !target.hasType("Capybara")) {
                    floppy.setEffectFlag("canAttackAgain", true);
                    logger.info("Floppy killed a non-European/Capybara, can attack again.");
                }
                int kills = (int) floppy.getEffectFlagOrDefault("killsThisTurn_Floppy", 0) + 1;
                floppy.setEffectFlag("killsThisTurn_Floppy", kills);
                logger.info("Floppy now has {} kills this turn.", kills);

                if (kills >= 2) {
                    floppy.setBaseAttack(8);
                    floppy.setBaseDefense(floppy.getCurrentDefense() + 4); // Permanent DEF buff
                    floppy.heal(8);
                    floppy.removeEffectFlag("killsThisTurn_Floppy");
                    logger.info("Floppy killed 2 cards, stats changed!");
                }
            }
        }
    }

    private void handleTomate(CardInstance tomate, Player owner, Map<String, Object> context, EffectTrigger trigger) {
        // Summoning condition ("appears when 3 cards left in deck") is too complex for
        // EffectProcessor.
        // It would require a modification to the core game draw loop.
        // We will only implement the on-play and turn-based effects here.

        if (trigger == EffectTrigger.ON_PLAY) {
            logger.info("Tomate enters the field, granting +5 DEF to all friendly cards.");
            List<CardInstance> friendlyCards = TargetResolver.resolveCardTargets("ALL_FRIENDLY_CARDS_ON_FIELD", tomate,
                    owner, context, game);
            for (CardInstance card : friendlyCards) {
                card.addTemporaryBuff("DEF", 5);
            }
        }

        if (trigger == EffectTrigger.START_OF_TURN_SELF) {
            int turnCounter = (int) tomate.getEffectFlagOrDefault("tomate_turn_counter", 0) + 1;
            tomate.setEffectFlag("tomate_turn_counter", turnCounter);

            if (turnCounter >= 4) {
                List<CardInstance> friendlyCards = owner.getFieldInternal().stream()
                        .filter(c -> c != null && !c.getInstanceId().equals(tomate.getInstanceId()))
                        .collect(Collectors.toList());
                if (!friendlyCards.isEmpty() && owner.getHand().size() < Player.MAX_HAND_SIZE) {
                    CardInstance cardToDuplicate = friendlyCards.get(random.nextInt(friendlyCards.size()));
                    CardInstance newCard = new CardInstance(cardToDuplicate.getDefinition());
                    owner.getHand().add(newCard);
                    logger.info("Tomate's effect activates, duplicating {} and adding it to {}'s hand.",
                            newCard.getDefinition().getName(), owner.getDisplayName());
                    tomate.setEffectFlag("tomate_turn_counter", 0); // Reset counter
                }
            }
        }
    }

    private void handleFamousBuilder(CardInstance builder, Player owner, Map<String, Object> context,
            EffectTrigger trigger) {
        if (trigger == EffectTrigger.ON_PLAY) {
            logger.info("Famousbuilder enters play, activating a random interference effect.");
            Player opponent = game.getOpponent(owner);
            if (opponent == null)
                return;

            List<CardInstance> enemyCards = opponent.getFieldInternal().stream().filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (enemyCards.isEmpty())
                return;

            CardInstance target = enemyCards.get(random.nextInt(enemyCards.size()));

            if (random.nextBoolean()) {
                // Effect A: Reduce DEF to 0
                target.addTemporaryBuff("DEF", -target.getCurrentDefense());
                logger.info("Famousbuilder reduces {}'s DEF to 0 for the turn.", target.getDefinition().getName());
            } else {
                // Effect B: Silence
                target.setEffectFlag("status_silenced", true);
                logger.info("Famousbuilder silences {} for the turn.", target.getDefinition().getName());
            }
        }
    }

    private void handleOlivio(CardInstance olivio, Player owner, Map<String, Object> context, EffectTrigger trigger) {
        // Effect 1: Buff on Capybara death
        if (trigger == EffectTrigger.ON_DEATH_OF_ANY) {
            CardInstance deadCard = (CardInstance) context.get("eventTarget");
            if (deadCard != null && deadCard.hasType("Capybara")) {
                olivio.setBaseAttack(olivio.getCurrentAttack() + 2);
                olivio.heal(5);
                logger.info("A Capybara has fallen. Olivio grows stronger. New stats: A:{} L:{}",
                        olivio.getCurrentAttack(), olivio.getCurrentLife());
            }
        }

        // Effect 2 & 3: Kahina death counter and once-per-game AoE
        if (trigger == EffectTrigger.START_OF_TURN_SELF) {
            owner.getFieldInternal().stream()
                    .filter(c -> c != null && "CAP001".equals(c.getDefinition().getCardId()))
                    .findFirst()
                    .ifPresent(kahina -> {
                        int counter = (int) olivio.getEffectFlagOrDefault("olivio_kahina_counter", 0) + 1;
                        olivio.setEffectFlag("olivio_kahina_counter", counter);
                        if (counter >= 3) {
                            logger.info("Three turns have passed. Olivio's presence causes Kahina to perish.");
                            kahina.setCurrentLife(0);
                        }
                    });
        }

        if (trigger == EffectTrigger.ACTIVATED) {
            String gameAoeFlag = "olivio_aoe_used_" + game.getGameId();
            if (!(boolean) game.getGameFlags().getOrDefault(gameAoeFlag, false)) {
                logger.info("Olivio unleashes his once-per-game roar!");
                List<CardInstance> targets = TargetResolver.resolveCardTargets("ALL_NON_CAPYBARA_CARDS_ON_FIELD",
                        olivio, owner, context, game);
                for (CardInstance target : targets) {
                    applyDamage(target, 3, olivio, game.getOwnerOfCardInstance(target), owner);
                }
                game.getGameFlags().put(gameAoeFlag, true);
            } else {
                logger.info("Olivio has already used his roar this game.");
            }
        }
    }

    private void handleAppleOfEcho(CardInstance apple, Player owner, Map<String, Object> context,
            EffectTrigger trigger) {
        // This is a simplified simulation. A true implementation requires engine
        // changes to handle a "limbo" zone.
        if (trigger == EffectTrigger.START_OF_TURN_SELF) {
            boolean isVanished = apple.getBooleanEffectFlag("apple_is_vanished");
            if (isVanished) {
                int returnTurn = (int) apple.getEffectFlagOrDefault("apple_return_turn", 0);
                if (game.getTurnNumber() >= returnTurn) {
                    logger.info("AppleofEcho returns from the void, stronger!");
                    apple.setBaseAttack(apple.getDefinition().getAttack() * 2);
                    apple.removeEffectFlag("apple_is_vanished");
                    // Note: a real implementation would need to find an empty slot and place him
                    // back on the field.
                }
            } else {
                int turnCounter = (int) apple.getEffectFlagOrDefault("apple_turn_counter", 0) + 1;
                apple.setEffectFlag("apple_turn_counter", turnCounter);
                if (turnCounter >= 5) {
                    logger.info("AppleofEcho has been on the field for 5 turns and vanishes!");
                    apple.setEffectFlag("apple_is_vanished", true);
                    apple.setEffectFlag("apple_return_turn", game.getTurnNumber() + 3);
                    // Note: a real implementation would remove him from the field here into a
                    // special zone.
                }
            }
        }
    }
}