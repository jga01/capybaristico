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

        switch (countType.toUpperCase()) {
            case "FRIENDLY_CARDS_WITH_TYPE":
                String typeToCount = (String) valueMap.get("typeName");
                if (typeToCount == null)
                    return 0;

                // Count friendly cards with the specified type, excluding the source card
                // itself.
                return (int) owner.getFieldInternal().stream()
                        .filter(c -> c != null && !c.getInstanceId().equals(effectSource.getInstanceId())
                                && c.hasType(typeToCount))
                        .count();
            case "OTHER_FRIENDLY_CARDS":
                return (int) owner.getFieldInternal().stream()
                        .filter(c -> c != null && !c.getInstanceId().equals(effectSource.getInstanceId()))
                        .count();
            default:
                logger.warn("Unknown dynamic count type: {}", countType);
                return 0;
        }
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
            // Add more contexts as needed
            default:
                logger.warn("Unknown cardContext in stat value resolver: {}", cardContext);
                return null;
        }

        if (targetCard == null) {
            logger.warn("Could not resolve stat value: target card for context '{}' not found.", cardContext);
            return null;
        }

        switch (statName) {
            case "ATK":
                return targetCard.getCurrentAttack();
            case "DEF":
                return targetCard.getCurrentDefense();
            case "LIFE":
                return targetCard.getCurrentLife();
            default:
                logger.warn("Unknown statName in stat value resolver: {}", statName);
                return null;
        }
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