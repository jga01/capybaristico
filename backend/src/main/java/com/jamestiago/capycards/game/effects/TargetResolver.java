package com.jamestiago.capycards.game.effects;

import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TargetResolver {

    private static final Random random = new Random();

    /**
     * Resolves a target selector string into a list of CardInstance targets.
     * 
     * @param targetSelectorString The string from the JSON config (e.g., "SELF",
     *                             "ALL_ENEMY_CARDS_ON_FIELD").
     * @param sourceCard           The card whose effect is being processed.
     * @param sourceOwner          The player who owns the source card.
     * @param triggerContext       A map containing context-specific data (e.g.,
     *                             event source, event target).
     * @param game                 The main game object.
     * @return A list of resolved CardInstance targets.
     */
    public static List<CardInstance> resolveCardTargets(String targetSelectorString, CardInstance sourceCard,
            Player sourceOwner, Map<String, Object> triggerContext, Game game) {
        if (targetSelectorString == null || targetSelectorString.isEmpty()) {
            return Collections.emptyList();
        }

        Player opponent = game.getOpponent(sourceOwner);
        List<CardInstance> potentialTargets = new ArrayList<>();

        switch (targetSelectorString.toUpperCase()) {
            case "SELF":
                if (sourceCard != null)
                    potentialTargets.add(sourceCard);
                break;

            case "ALL_ENEMY_CARDS_ON_FIELD":
                if (opponent != null) {
                    potentialTargets.addAll(
                            opponent.getFieldInternal().stream().filter(c -> c != null).collect(Collectors.toList()));
                }
                break;

            case "ALL_FRIENDLY_CARDS_ON_FIELD":
                if (sourceOwner != null) {
                    potentialTargets.addAll(sourceOwner.getFieldInternal().stream().filter(c -> c != null)
                            .collect(Collectors.toList()));
                }
                break;

            case "RANDOM_FRIENDLY_CARD_ON_FIELD":
                if (sourceOwner != null) {
                    List<CardInstance> friendlyCards = sourceOwner.getFieldInternal().stream().filter(c -> c != null)
                            .collect(Collectors.toList());
                    if (!friendlyCards.isEmpty()) {
                        potentialTargets.add(friendlyCards.get(random.nextInt(friendlyCards.size())));
                    }
                }
                break;

            case "FRIENDLY_CARD_WITH_ID":
                if (sourceOwner != null && triggerContext != null && triggerContext.containsKey("cardId")) {
                    String cardIdToFind = (String) triggerContext.get("cardId");
                    sourceOwner.getFieldInternal().stream()
                            .filter(c -> c != null && c.getDefinition().getCardId().equals(cardIdToFind))
                            .findFirst()
                            .ifPresent(potentialTargets::add);
                }
                break;

            case "ENEMY_CARD_WITH_ID":
                if (opponent != null && triggerContext != null && triggerContext.containsKey("cardId")) {
                    String cardIdToFind = (String) triggerContext.get("cardId");
                    opponent.getFieldInternal().stream()
                            .filter(c -> c != null && c.getDefinition().getCardId().equals(cardIdToFind))
                            .findFirst()
                            .ifPresent(potentialTargets::add);
                }
                break;

            case "ALL_CARDS_ON_FIELD":
                if (sourceOwner != null)
                    potentialTargets.addAll(sourceOwner.getFieldInternal().stream().filter(c -> c != null)
                            .collect(Collectors.toList()));
                if (opponent != null)
                    potentialTargets.addAll(
                            opponent.getFieldInternal().stream().filter(c -> c != null).collect(Collectors.toList()));
                break;

            case "ALL_NON_CAPYBARA_CARDS_ON_FIELD":
                Stream<CardInstance> allCardsStream = Stream.empty();
                if (sourceOwner != null) {
                    allCardsStream = Stream.concat(allCardsStream, sourceOwner.getFieldInternal().stream());
                }
                if (opponent != null) {
                    allCardsStream = Stream.concat(allCardsStream, opponent.getFieldInternal().stream());
                }
                potentialTargets.addAll(
                        allCardsStream
                                .filter(c -> c != null && !c.hasType("Capybara"))
                                .collect(Collectors.toList()));
                break;

            case "EVENT_SOURCE": // The card that caused the event (e.g., the damage dealer)
                if (triggerContext != null && triggerContext.get("eventSource") instanceof CardInstance) {
                    potentialTargets.add((CardInstance) triggerContext.get("eventSource"));
                }
                break;

            case "EVENT_TARGET": // The card that was the target of the event (e.g., the card played)
                if (triggerContext != null && triggerContext.get("eventTarget") instanceof CardInstance) {
                    potentialTargets.add((CardInstance) triggerContext.get("eventTarget"));
                }
                break;

            case "ACTIVATION_CONTEXT_TARGET":
                if (triggerContext != null && triggerContext.containsKey("targetCardInstanceId")) {
                    String targetId = (String) triggerContext.get("targetCardInstanceId");
                    CardInstance targetCard = game.findCardInstanceFromAnyField(targetId);
                    if (targetCard != null) {
                        potentialTargets.add(targetCard);
                    }
                }
                break;

            default:
                // Handle direct instanceId or list of instanceIds later if needed
                break;
        }

        // Filter out destroyed cards unless the effect specifically targets them
        return potentialTargets.stream().filter(c -> c != null && !c.isDestroyed()).collect(Collectors.toList());
    }

    // A resolvePlayerTargets method could be added here later if needed
}