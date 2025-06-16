package com.jamestiago.capycards.admin;

import com.jamestiago.capycards.game.effects.EffectActionType;
import com.jamestiago.capycards.game.effects.EffectTrigger;
import com.jamestiago.capycards.model.Rarity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/effect-options")
public class EffectOptionsController {

    @GetMapping
    public Map<String, List<String>> getEffectOptions() {
        return Map.of(
                "triggers", getEnumNames(EffectTrigger.class),
                "actions", getEnumNames(EffectActionType.class),
                "targetSelectors", getTargetSelectors(),
                "conditionTypes", getConditionTypes(),
                "valueSourceTypes", getValueSourceTypes(),
                "statTypes", getStatTypes(),
                "rarities", getEnumNames(Rarity.class));
    }

    private <E extends Enum<E>> List<String> getEnumNames(Class<E> enumClass) {
        return Stream.of(enumClass.getEnumConstants())
                .map(Enum::name)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getTargetSelectors() {
        // Based on TargetResolver.java logic
        return Stream.of(
                "SELF", "ALL_ENEMY_CARDS_ON_FIELD", "ALL_FRIENDLY_CARDS_ON_FIELD",
                "RANDOM_FRIENDLY_CARD_ON_FIELD", "RANDOM_ENEMY_CARD_ON_FIELD", "ALL_CARDS_ON_FIELD",
                "EVENT_SOURCE", "EVENT_TARGET", "ACTIVATION_CONTEXT_TARGET",
                "ALL_NON_CAPYBARA_CARDS_ON_FIELD", "FRIENDLY_CARD_WITH_ID",
                "ENEMY_CARD_WITH_ID", "ALL_FRIENDLY_CARDS_IN_DECK", "RANDOM_FRIENDLY_CARD_IN_DECK").sorted()
                .collect(Collectors.toList());
    }

    private List<String> getConditionTypes() {
        // Based on EffectProcessor.java evaluateCondition logic
        return Stream.of(
                "ALL_OF", "ANY_OF", "SELF_HAS_FLAG", "TARGET_HAS_TYPE", "TRIGGER_SOURCE_IS_SELF",
                "SOURCE_HAS_TYPE", "TARGET_IS_DESTROYED", "SOURCE_HAS_CARD_ID", "FRIENDLY_CARD_IN_PLAY",
                "ENEMY_CARD_IN_PLAY", "VALUE_COMPARISON").sorted().collect(Collectors.toList());
    }

    private List<String> getValueSourceTypes() {
        // Based on ValueResolver.java
        return Stream.of(
                "STAT", "EVENT_DATA", "FLAG_VALUE", "DYNAMIC_COUNT").sorted().collect(Collectors.toList());
    }

    private List<String> getStatTypes() {
        return Stream.of(
                "ATK", "DEF", "LIFE", "BASE_ATK", "BASE_DEF", "BASE_LIFE", "MAX_LIFE").sorted()
                .collect(Collectors.toList());
    }
}