package com.jamestiago.capycards.game;

import com.jamestiago.capycards.game.commands.*;
import com.jamestiago.capycards.game.effects.EffectProcessor;
import com.jamestiago.capycards.game.effects.EffectTrigger;
import com.jamestiago.capycards.game.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);
    private final EffectProcessor effectProcessor;

    public GameEngine() {
        this.effectProcessor = new EffectProcessor();
    }

    private record CardStats(int attack, int defense) {
        public CardStats(CardInstance card) {
            this(card.getCurrentAttack(), card.getCurrentDefense());
        }
    }

    public List<GameEvent> processCommand(Game game, GameCommand command) {
        logger.trace("[{}] Processing command: {} from player {}", game.getGameId(), command.getCommandType(),
                command.playerId);
        if (!isCommandValid(game, command)) {
            logger.warn("[{}] Invalid command received: {} by {} for game {}. Current player is {}.",
                    game.getGameId(), command.getCommandType(), command.playerId, game.getGameId(),
                    game.getCurrentPlayer() != null ? game.getCurrentPlayer().getPlayerId() : "N/A");
            return List.of();
        }

        // --- START OF MODIFICATION ---
        List<GameEvent> allEvents = new ArrayList<>();
        Game tempGame = new Game(game); // Create one simulation game for the whole process

        // 1. Process the initial command
        List<GameEvent> commandEvents = switch (command) {
            case PlayCardCommand cmd -> handlePlayCard(tempGame, cmd);
            case AttackCommand cmd -> handleAttack(tempGame, cmd);
            case EndTurnCommand cmd -> handleEndTurn(tempGame, cmd);
            case ActivateAbilityCommand cmd -> handleActivateAbility(tempGame, cmd);
            case GameOverCommand cmd -> List.of(
                    new GameOverEvent(game.getGameId(), game.getTurnNumber(), cmd.winnerPlayerId, cmd.reason));
            default -> {
                logger.warn("No handler found for command type: {}", command.getCommandType());
                yield new ArrayList<GameEvent>();
            }
        };

        // 2. Apply initial events and start the resolution loop
        allEvents.addAll(applyAndResolve(tempGame, commandEvents));

        logger.trace("[{}] Command {} resulted in {} total events.", game.getGameId(), command.getCommandType(),
                allEvents.size());
        return allEvents;
    }

    /**
     * New central loop. Applies a batch of events, then checks for auras and
     * deaths,
     * adding any new events to the queue and repeating until no new events are
     * generated.
     */
    private List<GameEvent> applyAndResolve(Game simulatedGame, List<GameEvent> newEvents) {
        List<GameEvent> resolvedEvents = new ArrayList<>(newEvents);
        List<GameEvent> eventsToProcess = new ArrayList<>(newEvents);

        int maxIterations = 10; // Safety break for catastrophic loops
        int currentIteration = 0;

        while (!eventsToProcess.isEmpty() && currentIteration < maxIterations) {
            currentIteration++;

            // Apply the latest batch of events
            for (GameEvent event : eventsToProcess) {
                simulatedGame.apply(event);
            }

            // Clear the queue for the next wave
            eventsToProcess.clear();

            // Check for state-based changes (Auras, Deaths)
            List<GameEvent> auraEvents = processAuras(simulatedGame);
            if (!auraEvents.isEmpty()) {
                resolvedEvents.addAll(auraEvents);
                eventsToProcess.addAll(auraEvents);
                // If auras changed things, we need to re-apply and re-check from the top
                continue;
            }

            List<GameEvent> deathEvents = checkForDeaths(simulatedGame);
            if (!deathEvents.isEmpty()) {
                resolvedEvents.addAll(deathEvents);
                eventsToProcess.addAll(deathEvents);
                // Deaths are significant, loop again to check for resulting auras/triggers
                continue;
            }
        }

        if (currentIteration >= maxIterations) {
            logger.error("[{}] Maximum resolution depth reached. Game may be in an unstable state. Forcing resolution.",
                    simulatedGame.getGameId());
            resolvedEvents.add(new GameLogMessageEvent(simulatedGame.getGameId(), simulatedGame.getTurnNumber(),
                    "ERROR: Unstable effect loop detected.", "ERROR"));
        }

        return resolvedEvents;
    }
    // --- END OF MODIFICATION ---

    private List<GameEvent> processDamageTriggers(CardInstance attacker, CardInstance defender, int damageDealt,
            Game simulatedGame) {
        List<GameEvent> subsequentEvents = new ArrayList<>();
        Player attackerOwner = simulatedGame.getOwnerOfCardInstance(attacker);
        Player defenderOwner = simulatedGame.getOwnerOfCardInstance(defender);

        if (damageDealt > 0) {
            List<CardInstance> allCards = new ArrayList<>();
            allCards.addAll(simulatedGame.getPlayer1().getFieldInternal().stream().filter(c -> c != null).toList());
            allCards.addAll(simulatedGame.getPlayer2().getFieldInternal().stream().filter(c -> c != null).toList());

            for (CardInstance observerCard : allCards) {
                if (!observerCard.getInstanceId().equals(defender.getInstanceId())) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("eventTarget", defender);
                    context.put("eventSource", attacker);
                    context.put("damageAmount", damageDealt);
                    List<GameEvent> onAnyDamageEvents = effectProcessor.processTrigger(simulatedGame,
                            EffectTrigger.ON_DAMAGE_TAKEN_OF_ANY, observerCard,
                            simulatedGame.getOwnerOfCardInstance(observerCard), context);
                    subsequentEvents.addAll(onAnyDamageEvents);
                }
            }
        }

        Map<String, Object> damageContext = new HashMap<>();
        damageContext.put("damageAmount", damageDealt);
        damageContext.put("eventTarget", defender);
        damageContext.put("eventSource", attacker);
        if (defender.isDestroyed()) {
            damageContext.put("targetIsDestroyed", true);
        }

        List<GameEvent> onDealtEvents = effectProcessor.processTrigger(simulatedGame, EffectTrigger.ON_DAMAGE_DEALT,
                attacker, attackerOwner, damageContext);
        subsequentEvents.addAll(onDealtEvents);

        List<GameEvent> onTakenEvents = effectProcessor.processTrigger(simulatedGame, EffectTrigger.ON_DAMAGE_TAKEN,
                defender, defenderOwner, damageContext);
        subsequentEvents.addAll(onTakenEvents);

        return subsequentEvents;
    }

    private List<GameEvent> handlePlayCard(Game tempGame, PlayCardCommand cmd) {
        Player player = tempGame.getPlayerById(cmd.playerId);
        List<GameEvent> events = new ArrayList<>();
        if (player == null || cmd.handCardIndex < 0 || cmd.handCardIndex >= player.getHand().size()
                || cmd.targetFieldSlot < 0 || cmd.targetFieldSlot >= Player.MAX_FIELD_SIZE
                || player.getField().get(cmd.targetFieldSlot) != null) {
            return events;
        }

        CardInstance cardToPlay = player.getHand().get(cmd.handCardIndex);
        if (!cardToPlay.getDefinition().isDirectlyPlayable()) {
            logger.warn("Player {} attempted to illegally play card '{}' from hand.", cmd.playerId,
                    cardToPlay.getDefinition().getName());
            return events;
        }

        CardPlayedEvent playedEvent = new CardPlayedEvent(
                tempGame.getGameId(),
                tempGame.getTurnNumber(),
                cmd.playerId,
                GameStateMapper.mapCardInstanceToDTO(cardToPlay),
                cmd.handCardIndex,
                cmd.targetFieldSlot,
                player.getHand().size() - 1);
        events.add(playedEvent);
        tempGame.apply(playedEvent); // Apply immediately to get the card on the board for triggers

        CardInstance cardInTempState = tempGame.findCardInstanceFromAnyField(cardToPlay.getInstanceId());
        if (cardInTempState != null) {
            Map<String, Object> context = new HashMap<>();
            context.put("eventTarget", cardInTempState);
            events.addAll(
                    effectProcessor.processTrigger(tempGame, EffectTrigger.ON_PLAY, cardInTempState, player, context));
        }

        return events;
    }

    private List<GameEvent> handleAttack(Game tempGame, AttackCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        // Removed CommandResult return type

        Player attackerPlayer = tempGame.getPlayerById(cmd.playerId);
        Player defenderPlayer = tempGame.getOpponent(attackerPlayer);

        if (attackerPlayer == null || defenderPlayer == null || cmd.attackerFieldIndex < 0
                || cmd.attackerFieldIndex >= Player.MAX_FIELD_SIZE || cmd.defenderFieldIndex < 0
                || cmd.defenderFieldIndex >= Player.MAX_FIELD_SIZE) {
            return events;
        }

        CardInstance attacker = attackerPlayer.getField().get(cmd.attackerFieldIndex);
        CardInstance defender = defenderPlayer.getField().get(cmd.defenderFieldIndex);

        boolean hasAttackAgainFlag = attacker != null && attacker.getBooleanEffectFlag("canAttackAgainThisTurn");

        if (attacker == null || defender == null || (!hasAttackAgainFlag && attacker.isExhausted())
                || (!hasAttackAgainFlag && !attackerPlayer.canDeclareAttack())) {
            return events;
        }

        // The rest of this method is simplified to just generate events, not apply
        // them.
        // The new applyAndResolve loop will handle application.

        if (hasAttackAgainFlag) {
            events.add(new CardFlagChangedEvent(tempGame.getGameId(), tempGame.getTurnNumber(),
                    attacker.getInstanceId(), "canAttackAgainThisTurn", null, "TURN"));
        }

        events.add(new AttackDeclaredEvent(tempGame.getGameId(), tempGame.getTurnNumber(), attackerPlayer.getPlayerId(),
                attacker.getInstanceId(), attacker.getDefinition().getName(), defender.getInstanceId(),
                defender.getDefinition().getName()));

        Map<String, Object> defendContext = new HashMap<>();
        defendContext.put("eventSource", attacker);
        events.addAll(effectProcessor.processTrigger(tempGame, EffectTrigger.ON_DEFEND, defender, defenderPlayer,
                defendContext));

        Map<String, Object> attackDeclareContext = new HashMap<>();
        attackDeclareContext.put("eventTarget", defender);
        events.addAll(effectProcessor.processTrigger(tempGame, EffectTrigger.ON_ATTACK_DECLARE, attacker,
                attackerPlayer, attackDeclareContext));

        // Create a temporary game to check the state after these initial triggers
        Game gameAfterTriggers = new Game(tempGame);
        for (GameEvent e : events)
            gameAfterTriggers.apply(e);

        CardInstance attackerAfterEffect = gameAfterTriggers.findCardInstanceFromAnyField(attacker.getInstanceId());
        CardInstance defenderInSim = gameAfterTriggers.findCardInstanceFromAnyField(defender.getInstanceId());

        if (attackerAfterEffect == null || attackerAfterEffect.isDestroyed() || defenderInSim == null
                || defenderInSim.isDestroyed()) {
            return events; // Attack fizzles
        }

        int defenderLifeBefore = defenderInSim.getCurrentLife();
        int attackPower = attackerAfterEffect.getCurrentAttack();
        if (attackerAfterEffect.getBooleanEffectFlag("double_damage_this_attack")) {
            attackPower *= 2;
            events.add(new CardFlagChangedEvent(tempGame.getGameId(), tempGame.getTurnNumber(),
                    attackerAfterEffect.getInstanceId(), "double_damage_this_attack", null, "PERMANENT"));
        }

        int damageToDeal = effectProcessor.calculateFinalDamage(gameAfterTriggers, attackPower, attackerAfterEffect,
                defenderInSim, "ATTACK");

        events.add(new CombatDamageDealtEvent(tempGame.getGameId(), tempGame.getTurnNumber(),
                attackerAfterEffect.getInstanceId(), defenderInSim.getInstanceId(), "ATTACK", attackPower, damageToDeal,
                defenderLifeBefore, Math.max(0, defenderInSim.getCurrentLife() - damageToDeal)));

        // Process damage triggers based on this event
        List<GameEvent> subsequentEvents = processDamageTriggers(attackerAfterEffect, defenderInSim, damageToDeal,
                gameAfterTriggers);
        events.addAll(subsequentEvents);

        return events;
    }

    private List<GameEvent> handleActivateAbility(Game tempGame, ActivateAbilityCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Player player = tempGame.getPlayerById(cmd.playerId);
        CardInstance sourceCard = tempGame.findCardInstanceFromAnyField(cmd.sourceCardInstanceId);
        if (player == null || sourceCard == null)
            return events;

        events.add(new AbilityActivatedEvent(tempGame.getGameId(), tempGame.getTurnNumber(), cmd.sourceCardInstanceId,
                cmd.targetCardInstanceId, cmd.abilityOptionIndex));

        Map<String, Object> context = new HashMap<>();
        context.put("targetCardInstanceId", cmd.targetCardInstanceId);
        context.put("abilityOptionIndex", cmd.abilityOptionIndex);
        events.addAll(effectProcessor.processTrigger(tempGame, EffectTrigger.ACTIVATED, sourceCard, player, context));

        return events;
    }

    private List<GameEvent> handleEndTurn(Game tempGame, EndTurnCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Player endingPlayer = tempGame.getPlayerById(cmd.playerId);
        if (endingPlayer == null || tempGame.getCurrentPlayer() != endingPlayer)
            return events;

        // End of turn triggers for the current player
        for (CardInstance card : endingPlayer.getField()) {
            if (card != null) {
                events.addAll(effectProcessor.processTrigger(tempGame, EffectTrigger.END_OF_TURN_SELF, card,
                        endingPlayer, new HashMap<>()));
            }
        }

        events.add(new TurnEndedEvent(tempGame.getGameId(), tempGame.getTurnNumber(), endingPlayer.getPlayerId()));

        Player nextPlayer = tempGame.getOpponent(endingPlayer);
        int currentTurnNumber = tempGame.getTurnNumber();

        Player.DrawOutcome outcome = nextPlayer.drawCardWithOutcome();
        switch (outcome.result()) {
            case DECK_EMPTY:
                String reason = nextPlayer.getDisplayName() + " cannot draw a card and has lost.";
                events.add(new GameOverEvent(tempGame.getGameId(), currentTurnNumber + 1, endingPlayer.getPlayerId(),
                        reason));
                return events; // Game is over, no more events
            case SUCCESS:
                events.add(new TurnStartedEvent(tempGame.getGameId(), currentTurnNumber, nextPlayer.getPlayerId()));
                events.add(new PlayerDrewCardEvent(
                        tempGame.getGameId(), currentTurnNumber + 1, nextPlayer.getPlayerId(),
                        GameStateMapper.mapCardInstanceToDTO(outcome.cardDrawn()),
                        nextPlayer.getHand().size(), nextPlayer.getDeck().size()));
                break;
            case HAND_FULL:
                events.add(new TurnStartedEvent(tempGame.getGameId(), currentTurnNumber, nextPlayer.getPlayerId()));
                events.add(new PlayerOverdrewCardEvent(
                        tempGame.getGameId(), currentTurnNumber + 1, nextPlayer.getPlayerId(),
                        GameStateMapper.mapCardInstanceToDTO(outcome.cardDrawn()),
                        nextPlayer.getDeck().size(), nextPlayer.getDiscardPile().size()));
                break;
        }

        // Apply these events to a new temp state to correctly process start-of-turn
        // effects
        Game gameAtTurnStart = new Game(tempGame);
        for (GameEvent e : events)
            gameAtTurnStart.apply(e);

        // Start of turn triggers for the new player
        events.addAll(processScheduledActions(gameAtTurnStart, nextPlayer));
        for (CardInstance card : nextPlayer.getField()) {
            if (card != null) {
                events.addAll(effectProcessor.processTrigger(gameAtTurnStart, EffectTrigger.START_OF_TURN_SELF, card,
                        nextPlayer, new HashMap<>()));
            }
        }
        return events;
    }

    private boolean isCardDead(CardInstance card) {
        if (card.getBooleanEffectFlag("can_survive_lethal")) {
            return (card.getCurrentLife() + card.getCurrentDefense()) <= 0;
        }
        return card.isDestroyed();
    }

    // This now only checks for deaths and generates triggers. It doesn't loop
    // internally.
    private List<GameEvent> checkForDeaths(Game simulatedGame) {
        List<GameEvent> deathEvents = new ArrayList<>();
        List<CardInstance> cardsToCheck = Stream.concat(
                simulatedGame.getPlayer1().getFieldInternal().stream(),
                simulatedGame.getPlayer2().getFieldInternal().stream())
                .filter(c -> c != null && isCardDead(c))
                .collect(Collectors.toList());

        for (CardInstance cardInSim : cardsToCheck) {
            Player owner = simulatedGame.getOwnerOfCardInstance(cardInSim);
            if (owner == null)
                continue;

            // This card is marked for death. Process its ON_DEATH triggers.
            Map<String, Object> deathContext = new HashMap<>();
            deathContext.put("eventTarget", cardInSim);
            if (cardInSim.getLastDamageSourceCard() != null) {
                deathContext.put("eventSource", simulatedGame
                        .findCardInstanceFromAnyField(cardInSim.getLastDamageSourceCard().getInstanceId()));
            }
            deathEvents.addAll(effectProcessor.processTrigger(simulatedGame, EffectTrigger.ON_DEATH, cardInSim, owner,
                    deathContext));

            // Check if the card saved itself
            Game tempStateAfterSave = new Game(simulatedGame);
            for (GameEvent e : deathEvents)
                tempStateAfterSave.apply(e);
            CardInstance cardAfterSave = tempStateAfterSave.findCardInstanceFromAnyField(cardInSim.getInstanceId());
            if (cardAfterSave != null && !isCardDead(cardAfterSave)) {
                logger.debug("Card {} saved itself from death with an ON_DEATH trigger.",
                        cardAfterSave.getDefinition().getName());
                continue; // It's alive, move to the next potential dead card
            }

            // It's still dead. Process ON_DEATH_OF_ANY for all other cards.
            List<CardInstance> observers = new ArrayList<>();
            observers.addAll(simulatedGame.getPlayer1().getFieldInternal().stream()
                    .filter(c -> c != null && !c.getInstanceId().equals(cardInSim.getInstanceId())).toList());
            observers.addAll(simulatedGame.getPlayer2().getFieldInternal().stream()
                    .filter(c -> c != null && !c.getInstanceId().equals(cardInSim.getInstanceId())).toList());

            for (CardInstance observerCard : observers) {
                deathEvents.addAll(effectProcessor.processTrigger(simulatedGame, EffectTrigger.ON_DEATH_OF_ANY,
                        observerCard, simulatedGame.getOwnerOfCardInstance(observerCard), deathContext));
            }

            // Finally, add the official destruction event
            deathEvents.add(new CardDestroyedEvent(simulatedGame.getGameId(), simulatedGame.getTurnNumber(),
                    GameStateMapper.mapCardInstanceToDTO(cardInSim), owner.getPlayerId()));
        }

        return deathEvents.stream().distinct().collect(Collectors.toList());
    }

    // (Other methods like `processAuras`, `processScheduledActions` remain the
    // same)
    private List<GameEvent> processAuras(Game simulatedGame) {
        List<GameEvent> events = new ArrayList<>();
        List<CardInstance> allCardsOnField = new ArrayList<>();
        if (simulatedGame.getPlayer1() != null)
            allCardsOnField
                    .addAll(simulatedGame.getPlayer1().getFieldInternal().stream().filter(c -> c != null).toList());
        if (simulatedGame.getPlayer2() != null)
            allCardsOnField
                    .addAll(simulatedGame.getPlayer2().getFieldInternal().stream().filter(c -> c != null).toList());

        Map<String, CardStats> statsBeforeAuras = new HashMap<>();
        allCardsOnField.forEach(card -> statsBeforeAuras.put(card.getInstanceId(), new CardStats(card)));

        allCardsOnField.forEach(CardInstance::clearAuraBuffs);
        allCardsOnField.forEach(CardInstance::clearAuraFlags);

        for (CardInstance sourceCard : allCardsOnField) {
            Player owner = simulatedGame.getOwnerOfCardInstance(sourceCard);
            if (owner != null) {
                effectProcessor.processTrigger(simulatedGame, EffectTrigger.CONTINUOUS_AURA, sourceCard, owner,
                        new HashMap<>());
            }
        }

        for (CardInstance card : allCardsOnField) {
            CardStats before = statsBeforeAuras.get(card.getInstanceId());
            CardStats after = new CardStats(card);

            if (before != null && !before.equals(after)) {
                events.add(new CardStatsChangedEvent(
                        simulatedGame.getGameId(),
                        simulatedGame.getTurnNumber(),
                        card.getInstanceId(),
                        after.attack, after.defense, card.getCurrentLife(),
                        "AURA_UPDATE"));
            }
        }

        return events;
    }

    private List<GameEvent> processScheduledActions(Game simulatedGame, Player forPlayer) {
        List<GameEvent> events = new ArrayList<>();
        int currentTurn = simulatedGame.getTurnNumber();

        // Process cards on field
        for (CardInstance card : forPlayer.getFieldInternal()) {
            if (card == null)
                continue;
            List<Map<String, Object>> actions = card.getScheduledActionsForTurn(currentTurn);
            if (actions != null && !actions.isEmpty()) {
                for (Map<String, Object> effectConfig : new ArrayList<>(actions)) {
                    events.addAll(effectProcessor.executeAction(simulatedGame, effectConfig, card, forPlayer,
                            new HashMap<>()));
                }
                card.clearScheduledActionsForTurn(currentTurn);
            }
        }

        // Process cards in limbo owned by the player
        simulatedGame.getCardsInLimbo().values().stream()
                .filter(entry -> entry.getValue().equals(forPlayer.getPlayerId()))
                .map(Map.Entry::getKey)
                .forEach(card -> {
                    List<Map<String, Object>> actions = card.getScheduledActionsForTurn(currentTurn);
                    if (actions != null && !actions.isEmpty()) {
                        for (Map<String, Object> effectConfig : new ArrayList<>(actions)) {
                            events.addAll(effectProcessor.executeAction(simulatedGame, effectConfig, card, forPlayer,
                                    new HashMap<>()));
                        }
                        card.clearScheduledActionsForTurn(currentTurn);
                    }
                });

        return events;
    }

    private boolean isCommandValid(Game game, GameCommand command) {
        if (game.getGameState().name().contains("GAME_OVER")) {
            logger.warn("[{}] Command {} rejected: Game is already over.", game.getGameId(), command.getCommandType());
            return false;
        }

        if (command instanceof GameOverCommand)
            return true;

        Player player = game.getPlayerById(command.playerId);
        if (player == null) {
            logger.error("[{}] Command {} validation failed: Player ID {} not found in game.", game.getGameId(),
                    command.getCommandType(), command.playerId);
            return false;
        }

        if (game.getCurrentPlayer() != player) {
            logger.warn("[{}] Command {} rejected: It is not player {}'s turn.", game.getGameId(),
                    command.getCommandType(), player.getDisplayName());
            return false;
        }

        if (command instanceof AttackCommand cmd) {
            if (cmd.attackerFieldIndex < 0 || cmd.attackerFieldIndex >= Player.MAX_FIELD_SIZE) {
                logger.warn("[{}] Command ATTACK rejected: Invalid attacker field index {}.", game.getGameId(),
                        cmd.attackerFieldIndex);
                return false;
            }

            CardInstance attacker = player.getField().get(cmd.attackerFieldIndex);
            if (attacker == null) {
                logger.warn("[{}] Command ATTACK rejected: No card at attacker field index {}.", game.getGameId(),
                        cmd.attackerFieldIndex);
                return false;
            }

            CardInstance defender = game.getOpponent(player).getField().get(cmd.defenderFieldIndex);
            if (defender == null) {
                logger.warn("[{}] Command ATTACK rejected: No card at defender field index {}.", game.getGameId(),
                        cmd.defenderFieldIndex);
                return false;
            }

            if (defender.getBooleanEffectFlag("status_cannot_be_targeted_AURA")) {
                logger.warn("[{}] Command ATTACK rejected: Card '{}' cannot be targeted by attacks due to an effect.",
                        game.getGameId(), defender.getDefinition().getName());
                return false;
            }
            if (attacker.getBooleanEffectFlag("status_cannot_attack_AURA")) {
                logger.warn("[{}] Command ATTACK rejected: Card '{}' cannot attack due to a restriction.",
                        game.getGameId(), attacker.getDefinition().getName());
                return false;
            }

            if (attacker.getBooleanEffectFlag("canAttackAgainThisTurn"))
                return true;
            if (attacker.isExhausted()) {
                logger.warn("[{}] Command ATTACK rejected: Attacking card '{}' is exhausted.", game.getGameId(),
                        attacker.getDefinition().getName());
                return false;
            }
            if (!player.canDeclareAttack()) {
                logger.warn("[{}] Command ATTACK rejected: Player '{}' has no attacks left this turn.",
                        game.getGameId(), player.getDisplayName());
                return false;
            }
        }

        return true;
    }
}