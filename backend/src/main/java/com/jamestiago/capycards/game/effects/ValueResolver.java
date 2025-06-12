package com.jamestiago.capycards.game.effects;

import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class ValueResolver {
    private static final Logger logger = LoggerFactory.getLogger(ValueResolver.class);

    public ValueResolver() {
    }

    @SuppressWarnings("unchecked")
    public Integer resolveValue(Object valueSource, CardInstance effectSource, Player owner,
            Map<String, Object> context) {
        if (valueSource instanceof Integer) {
            return (Integer) valueSource;
        }
        if (!(valueSource instanceof Map)) {
            logger.warn("Could not resolve value: source is not an Integer or a Map. Got: {}",
                    valueSource != null ? valueSource.getClass().getName() : "null");
            return null;
        }

        Map<String, Object> valueMap = (Map<String, Object>) valueSource;
        String type = (String) valueMap.get("source");
        if (type == null) {
            logger.warn("Could not resolve value: value map is missing 'source' key.");
            return null;
        }

        Game game = (Game) context.get("game"); // Get the game object from context

        switch (type.toUpperCase()) {
            case "STAT":
                return resolveStatValue(valueMap, effectSource, owner, context);
            case "EVENT_DATA":
                return resolveEventData(valueMap, context);
            case "FLAG_VALUE":
                return resolveFlagValue(valueMap, effectSource, owner, context);
            case "DYNAMIC_COUNT":
                return resolveDynamicCount(valueMap, effectSource, owner, game);
            default:
                logger.warn("Unknown value source type: {}", type);
                return null;
        }
    }

    private Integer resolveDynamicCount(Map<String, Object> valueMap, CardInstance effectSource, Player owner,
            Game game) {
        if (game == null) {
            logger.warn("DynamicCount resolver requires 'game' in context.");
            return 0;
        }
        String countType = (String) valueMap.get("countType");
        if (countType == null)
            return 0;

        long count = 0;
        switch (countType.toUpperCase()) {
            case "FRIENDLY_CARDS_WITH_TYPE":
                String typeToCount = (String) valueMap.get("typeName");
                if (typeToCount == null)
                    return 0;
                count = owner.getFieldInternal().stream()
                        .filter(c -> c != null && !c.getInstanceId().equals(effectSource.getInstanceId())
                                && c.hasType(typeToCount))
                        .count();
                break;
            case "OTHER_FRIENDLY_CARDS":
                count = owner.getFieldInternal().stream()
                        .filter(c -> c != null && !c.getInstanceId().equals(effectSource.getInstanceId()))
                        .count();
                break;
            case "HIGHEST_LIFE_ON_FIELD_EXCLUDING_SELF":
                int maxLife = 0;
                for (Player p : new Player[] { game.getPlayer1(), game.getPlayer2() }) {
                    if (p != null) {
                        for (CardInstance card : p.getFieldInternal()) {
                            if (card != null && !card.getInstanceId().equals(effectSource.getInstanceId())) {
                                if (card.getCurrentLife() > maxLife) {
                                    maxLife = card.getCurrentLife();
                                }
                            }
                        }
                    }
                }
                Integer maxValue = (Integer) valueMap.get("maxValue");
                if (maxValue != null) {
                    return Math.min(maxLife, maxValue);
                }
                return maxLife;
            default:
                logger.warn("Unknown dynamic count type: {}", countType);
                return 0;
        }

        // Apply multiplier if it exists
        if (valueMap.containsKey("multiplier")) {
            Integer multiplier = (Integer) valueMap.get("multiplier");
            return (int) count * multiplier;
        }

        return (int) count;
    }

    private Integer resolveStatValue(Map<String, Object> valueMap, CardInstance effectSource, Player owner,
            Map<String, Object> context) {
        String statName = ((String) valueMap.get("statName")).toUpperCase();
        String cardContext = (String) valueMap.get("cardContext");

        CardInstance targetCard = null;
        switch (cardContext.toUpperCase()) {
            case "SELF":
                targetCard = effectSource;
                break;
            case "EVENT_TARGET":
                targetCard = (CardInstance) context.get("eventTarget");
                break;
            case "EVENT_SOURCE":
                targetCard = (CardInstance) context.get("eventSource");
                break;
            default:
                logger.warn("Unknown cardContext in stat value resolver: {}", cardContext);
                return null;
        }

        if (targetCard == null) {
            logger.warn("Could not resolve stat value: target card for context '{}' not found.", cardContext);
            return null;
        }

        Integer result = switch (statName) {
            case "ATK" -> targetCard.getCurrentAttack();
            case "DEF" -> targetCard.getCurrentDefense();
            case "LIFE" -> targetCard.getCurrentLife();
            case "BASE_ATK" -> targetCard.getBaseAttack();
            case "BASE_DEF" -> targetCard.getBaseDefense();
            case "BASE_LIFE" -> targetCard.getBaseLife();
            default -> {
                logger.warn("Unknown statName in stat value resolver: {}", statName);
                yield null;
            }
        };

        if (result != null && valueMap.containsKey("multiplier")) {
            // Support for float/double multipliers for things like "half life"
            Object multiplierObj = valueMap.get("multiplier");
            if (multiplierObj instanceof Double) {
                result = (int) (result * (Double) multiplierObj);
            } else if (multiplierObj instanceof Integer) {
                result = result * (Integer) multiplierObj;
            }
        }

        return result;
    }

    private Integer resolveEventData(Map<String, Object> valueMap, Map<String, Object> context) {
        String key = (String) valueMap.get("key");
        Object data = context.get(key);
        if (data instanceof Integer) {
            return (Integer) data;
        }
        logger.warn("Could not resolve event data for key '{}': data not found or not an Integer.", key);
        return null;
    }

    private Integer resolveFlagValue(Map<String, Object> valueMap, CardInstance effectSource, Player owner,
            Map<String, Object> context) {
        String flagName = (String) valueMap.get("flagName");
        String cardContext = (String) valueMap.get("cardContext");

        CardInstance targetCard = null;
        switch (cardContext.toUpperCase()) {
            case "SELF":
                targetCard = effectSource;
                break;
            case "EVENT_TARGET":
                targetCard = (CardInstance) context.get("eventTarget");
                break;
            case "EVENT_SOURCE":
                targetCard = (CardInstance) context.get("eventSource");
                break;
            default:
                logger.warn("Unknown cardContext in flag value resolver: {}", cardContext);
                return null;
        }

        if (targetCard == null) {
            logger.warn("Could not resolve flag value: target card for context '{}' not found.", cardContext);
            return null;
        }

        Object flagValue = targetCard.getEffectFlagOrDefault(flagName, 0);
        if (flagValue instanceof Integer) {
            return (Integer) flagValue;
        }

        logger.warn("Flag '{}' on card {} is not an Integer.", flagName, targetCard.getDefinition().getName());
        return 0; // Default to 0 if not an integer
    }
}