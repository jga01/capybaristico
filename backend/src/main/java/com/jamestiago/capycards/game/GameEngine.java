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

@Service
public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);
    private final EffectProcessor effectProcessor;

    public GameEngine() {
        this.effectProcessor = new EffectProcessor();
    }

    /**
     * A private record to wrap the result of command processing, containing both
     * the list of generated events and the final state of the simulation.
     */
    private record CommandResult(List<GameEvent> events, Game simulatedGame) {
        CommandResult(List<GameEvent> events, Game simulatedGame) {
            this.events = events;
            this.simulatedGame = new Game(simulatedGame); // Ensure we have a final state copy
        }
    }

    // A helper record for comparing card stats before and after aura processing.
    private record CardStats(int attack, int defense) {
        public CardStats(CardInstance card) {
            this(card.getCurrentAttack(), card.getCurrentDefense());
        }
    }

    // Main entry point for processing any command
    public List<GameEvent> processCommand(Game game, GameCommand command) {
        logger.trace("[{}] Processing command: {} from player {}", game.getGameId(), command.getCommandType(),
                command.playerId);
        if (!isCommandValid(game, command)) {
            logger.warn("[{}] Invalid command received: {} by {} for game {}. Current player is {}.",
                    game.getGameId(), command.getCommandType(), command.playerId, game.getGameId(),
                    game.getCurrentPlayer() != null ? game.getCurrentPlayer().getPlayerId() : "N/A");
            return List.of(); // Return empty list for invalid commands
        }

        CommandResult result = switch (command) {
            case PlayCardCommand cmd -> handlePlayCard(game, cmd);
            case AttackCommand cmd -> handleAttack(game, cmd);
            case EndTurnCommand cmd -> handleEndTurn(game, cmd);
            case ActivateAbilityCommand cmd -> handleActivateAbility(game, cmd);
            case GameOverCommand cmd -> new CommandResult(List.of(
                    new GameOverEvent(game.getGameId(), game.getTurnNumber(), cmd.winnerPlayerId, cmd.reason)),
                    game);
            default -> {
                logger.warn("No handler found for command type: {}", command.getCommandType());
                yield new CommandResult(new ArrayList<>(), game);
            }
        };

        // --- AURA PROCESSING LOGIC ---
        // After an action, but before checking for deaths, re-calculate all auras.
        Game gameAfterAction = new Game(result.simulatedGame()); // Copy state before auras
        List<GameEvent> auraEvents = processAuras(gameAfterAction); // Process auras on the copy

        // Combine the event lists
        List<GameEvent> allEvents = new ArrayList<>(result.events());
        allEvents.addAll(auraEvents);

        // After getting all events from the primary action and auras, check for any
        // deaths.
        allEvents.addAll(checkForDeaths(gameAfterAction)); // Use the state *after* auras were applied

        logger.trace("[{}] Command {} resulted in {} total events.", game.getGameId(), command.getCommandType(),
                allEvents.size());
        return allEvents;
    }

    /**
     * NEW HELPER METHOD (FIX #3)
     * Centralizes the processing of observer triggers (ON_DAMAGE_TAKEN_OF_ANY)
     * and participant triggers (ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN).
     * This ensures that damage from ANY source (attacks, effects) is handled
     * consistently.
     */
    private List<GameEvent> processDamageTriggers(CardInstance attacker, CardInstance defender, int damageDealt,
            Game simulatedGame) {
        List<GameEvent> subsequentEvents = new ArrayList<>();
        Player attackerOwner = simulatedGame.getOwnerOfCardInstance(attacker);
        Player defenderOwner = simulatedGame.getOwnerOfCardInstance(defender);

        // 1. Trigger ON_DAMAGE_TAKEN_OF_ANY for all other cards
        if (damageDealt > 0) {
            List<CardInstance> allCards = new ArrayList<>();
            allCards.addAll(simulatedGame.getPlayer1().getFieldInternal().stream().filter(c -> c != null).toList());
            allCards.addAll(simulatedGame.getPlayer2().getFieldInternal().stream().filter(c -> c != null).toList());

            for (CardInstance observerCard : allCards) {
                // Observers don't trigger on themselves or the primary defender
                if (!observerCard.getInstanceId().equals(defender.getInstanceId())) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("eventTarget", defender);
                    context.put("eventSource", attacker);
                    context.put("damageAmount", damageDealt);
                    List<GameEvent> onAnyDamageEvents = effectProcessor.processTrigger(simulatedGame,
                            EffectTrigger.ON_DAMAGE_TAKEN_OF_ANY, observerCard,
                            simulatedGame.getOwnerOfCardInstance(observerCard), context);
                    for (GameEvent e : onAnyDamageEvents) {
                        subsequentEvents.add(e);
                        simulatedGame.apply(e); // Apply immediately to keep simulation state consistent
                    }
                }
            }
        }

        // 2. Trigger ON_DAMAGE_DEALT and ON_DAMAGE_TAKEN on the participants
        Map<String, Object> damageContext = new HashMap<>();
        damageContext.put("damageAmount", damageDealt);
        damageContext.put("eventTarget", defender);
        damageContext.put("eventSource", attacker);
        if (defender.isDestroyed()) {
            damageContext.put("targetIsDestroyed", true);
        }

        List<GameEvent> onDealtEvents = effectProcessor.processTrigger(simulatedGame, EffectTrigger.ON_DAMAGE_DEALT,
                attacker, attackerOwner, damageContext);
        for (GameEvent e : onDealtEvents) {
            subsequentEvents.add(e);
            simulatedGame.apply(e);
        }

        List<GameEvent> onTakenEvents = effectProcessor.processTrigger(simulatedGame, EffectTrigger.ON_DAMAGE_TAKEN,
                defender, defenderOwner, damageContext);
        for (GameEvent e : onTakenEvents) {
            subsequentEvents.add(e);
            simulatedGame.apply(e);
        }

        return subsequentEvents;
    }

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

        for (GameEvent event : events) {
            simulatedGame.apply(event);
        }

        return events;
    }

    private List<GameEvent> processScheduledActions(Game simulatedGame, Player forPlayer) {
        List<GameEvent> events = new ArrayList<>();
        int currentTurn = simulatedGame.getTurnNumber();

        for (CardInstance card : forPlayer.getFieldInternal()) {
            if (card == null)
                continue;
            List<Map<String, Object>> actions = card.getScheduledActionsForTurn(currentTurn);
            if (actions != null) {
                for (Map<String, Object> effectConfig : new ArrayList<>(actions)) {
                    events.addAll(effectProcessor.executeAction(simulatedGame, effectConfig, card, forPlayer,
                            new HashMap<>()));
                }
                card.clearScheduledActionsForTurn(currentTurn);
            }
        }

        List<CardInstance> limboCardsToProcess = new ArrayList<>(simulatedGame.getCardsInLimbo().values());
        for (CardInstance card : limboCardsToProcess) {
            if (simulatedGame.getOwnerOfCardInstance(card) == forPlayer) {
                List<Map<String, Object>> actions = card.getScheduledActionsForTurn(currentTurn);
                if (actions != null) {
                    for (Map<String, Object> effectConfig : new ArrayList<>(actions)) {
                        events.addAll(effectProcessor.executeAction(simulatedGame, effectConfig, card, forPlayer,
                                new HashMap<>()));
                    }
                    card.clearScheduledActionsForTurn(currentTurn);
                }
            }
        }

        return events;
    }

    private CommandResult handlePlayCard(Game game, PlayCardCommand cmd) {
        Player player = game.getPlayerById(cmd.playerId);
        if (player == null || cmd.handCardIndex < 0 || cmd.handCardIndex >= player.getHand().size()
                || cmd.targetFieldSlot < 0 || cmd.targetFieldSlot >= Player.MAX_FIELD_SIZE
                || player.getField().get(cmd.targetFieldSlot) != null) {
            return new CommandResult(List.of(), game);
        }

        List<GameEvent> events = new ArrayList<>();
        Game tempGame = new Game(game);

        CardInstance cardToPlay = player.getHand().get(cmd.handCardIndex);

        CardPlayedEvent playedEvent = new CardPlayedEvent(
                game.getGameId(),
                game.getTurnNumber(),
                cmd.playerId,
                GameStateMapper.mapCardInstanceToDTO(cardToPlay),
                cmd.handCardIndex,
                cmd.targetFieldSlot,
                player.getHand().size() - 1);
        events.add(playedEvent);
        tempGame.apply(playedEvent);

        CardInstance cardInTempState = tempGame.findCardInstanceFromAnyField(cardToPlay.getInstanceId());
        if (cardInTempState != null) {
            Map<String, Object> context = new HashMap<>();
            context.put("eventTarget", cardInTempState);
            List<GameEvent> onPlayEvents = effectProcessor.processTrigger(tempGame, EffectTrigger.ON_PLAY,
                    cardInTempState, tempGame.getPlayerById(cmd.playerId), context);
            for (GameEvent e : onPlayEvents) {
                events.add(e);
                tempGame.apply(e);
            }
        }

        return new CommandResult(events, tempGame);
    }

    private CommandResult handleAttack(Game game, AttackCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Game tempGame = new Game(game);

        Player attackerPlayer = tempGame.getPlayerById(cmd.playerId);
        Player defenderPlayer = tempGame.getOpponent(attackerPlayer);

        if (attackerPlayer == null || defenderPlayer == null)
            return new CommandResult(List.of(), game);
        if (cmd.attackerFieldIndex < 0 || cmd.attackerFieldIndex >= Player.MAX_FIELD_SIZE)
            return new CommandResult(List.of(), game);
        if (cmd.defenderFieldIndex < 0 || cmd.defenderFieldIndex >= Player.MAX_FIELD_SIZE)
            return new CommandResult(List.of(), game);

        CardInstance attacker = attackerPlayer.getField().get(cmd.attackerFieldIndex);
        CardInstance defender = defenderPlayer.getField().get(cmd.defenderFieldIndex);

        boolean hasAttackAgainFlag = attacker != null && attacker.getBooleanEffectFlag("canAttackAgainThisTurn");

        if (attacker == null || defender == null || (!hasAttackAgainFlag && attacker.isExhausted())
                || (!hasAttackAgainFlag && !attackerPlayer.canDeclareAttack())) {
            return new CommandResult(List.of(), game);
        }

        if (hasAttackAgainFlag) {
            CardFlagChangedEvent flagEvent = new CardFlagChangedEvent(game.getGameId(), game.getTurnNumber(),
                    attacker.getInstanceId(), "canAttackAgainThisTurn", null, "TURN");
            events.add(flagEvent);
            tempGame.apply(flagEvent);
        }

        AttackDeclaredEvent declaredEvent = new AttackDeclaredEvent(game.getGameId(), game.getTurnNumber(),
                attackerPlayer.getPlayerId(), attacker.getInstanceId(), defender.getInstanceId());
        events.add(declaredEvent);
        tempGame.apply(declaredEvent);

        Map<String, Object> attackDeclareContext = new HashMap<>();
        attackDeclareContext.put("eventTarget", defender);
        List<GameEvent> onAttackEvents = effectProcessor.processTrigger(tempGame, EffectTrigger.ON_ATTACK_DECLARE,
                attacker, attackerPlayer, attackDeclareContext);
        for (GameEvent e : onAttackEvents) {
            events.add(e);
            tempGame.apply(e);
        }

        CardInstance attackerInSim = tempGame.findCardInstanceFromAnyField(attacker.getInstanceId());
        CardInstance defenderInSim = tempGame.findCardInstanceFromAnyField(defender.getInstanceId());

        int defenderLifeBefore = defender.getCurrentLife();
        int attackPower = attackerInSim.getCurrentAttack();
        if (attackerInSim.getBooleanEffectFlag("double_damage_this_attack")) {
            attackPower *= 2;
            CardFlagChangedEvent flagEvent = new CardFlagChangedEvent(game.getGameId(), game.getTurnNumber(),
                    attackerInSim.getInstanceId(), "double_damage_this_attack", null, "PERMANENT");
            events.add(flagEvent);
            tempGame.apply(flagEvent);
        }
        int defensePower = defenderInSim.getCurrentDefense();
        int damageToDeal = Math.max(0, attackPower - defensePower);

        CombatDamageDealtEvent damageEvent = new CombatDamageDealtEvent(game.getGameId(), game.getTurnNumber(),
                attackerInSim.getInstanceId(), defenderInSim.getInstanceId(), attackPower, damageToDeal,
                defenderLifeBefore,
                Math.max(0, defenderInSim.getCurrentLife() - damageToDeal));
        events.add(damageEvent);
        tempGame.apply(damageEvent);

        // REFACTORED (FIX #3): Use the new helper method for all damage-related
        // triggers
        List<GameEvent> subsequentEvents = processDamageTriggers(attackerInSim, defenderInSim, damageToDeal, tempGame);
        events.addAll(subsequentEvents);

        return new CommandResult(events, tempGame);
    }

    private CommandResult handleActivateAbility(Game game, ActivateAbilityCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Game tempGame = new Game(game);

        Player player = tempGame.getPlayerById(cmd.playerId);
        CardInstance sourceCard = tempGame.findCardInstanceFromAnyField(cmd.sourceCardInstanceId);

        if (player == null || sourceCard == null)
            return new CommandResult(List.of(), game);

        AbilityActivatedEvent activatedEvent = new AbilityActivatedEvent(game.getGameId(), game.getTurnNumber(),
                cmd.sourceCardInstanceId, cmd.targetCardInstanceId, cmd.abilityOptionIndex);
        events.add(activatedEvent);
        tempGame.apply(activatedEvent);

        Map<String, Object> context = new HashMap<>();
        context.put("targetCardInstanceId", cmd.targetCardInstanceId);
        context.put("abilityOptionIndex", cmd.abilityOptionIndex);

        List<GameEvent> abilityEvents = effectProcessor.processTrigger(tempGame, EffectTrigger.ACTIVATED, sourceCard,
                player, context);
        for (GameEvent e : abilityEvents) {
            events.add(e);
            tempGame.apply(e);
        }

        return new CommandResult(events, tempGame);
    }

    private CommandResult handleEndTurn(Game game, EndTurnCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Game tempGame = new Game(game);

        Player endingPlayer = tempGame.getPlayerById(cmd.playerId);
        if (endingPlayer == null || tempGame.getCurrentPlayer() != endingPlayer)
            return new CommandResult(List.of(), game);

        for (CardInstance card : endingPlayer.getField()) {
            if (card != null) {
                List<GameEvent> endOfTurnEvents = effectProcessor.processTrigger(tempGame,
                        EffectTrigger.END_OF_TURN_SELF, card, endingPlayer, new HashMap<>());
                for (GameEvent e : endOfTurnEvents) {
                    events.add(e);
                    tempGame.apply(e);
                }
            }
        }

        TurnEndedEvent endedEvent = new TurnEndedEvent(game.getGameId(), game.getTurnNumber(),
                endingPlayer.getPlayerId());
        events.add(endedEvent);
        tempGame.apply(endedEvent);

        Player nextPlayer = tempGame.getOpponent(endingPlayer);
        int currentTurnNumber = game.getTurnNumber();

        if (nextPlayer.getDeck().isEmpty() && nextPlayer.getHand().isEmpty()) {
            String reason = nextPlayer.getDisplayName() + " has no cards left to draw.";
            events.add(new GameOverEvent(game.getGameId(), currentTurnNumber + 1, endingPlayer.getPlayerId(), reason));
            return new CommandResult(events, tempGame);
        }

        TurnStartedEvent startedEvent = new TurnStartedEvent(game.getGameId(), currentTurnNumber,
                nextPlayer.getPlayerId());
        events.add(startedEvent);
        tempGame.apply(startedEvent);

        List<GameEvent> startOfTurnScheduledEvents = processScheduledActions(tempGame, nextPlayer);
        for (GameEvent e : startOfTurnScheduledEvents) {
            events.add(e);
            tempGame.apply(e);
        }

        if (nextPlayer.getHand().size() < Player.MAX_HAND_SIZE && !nextPlayer.getDeck().isEmpty()) {
            CardInstance cardToDraw = nextPlayer.getDeck().getCards().get(0);
            PlayerDrewCardEvent drewEvent = new PlayerDrewCardEvent(
                    game.getGameId(), currentTurnNumber + 1, nextPlayer.getPlayerId(),
                    GameStateMapper.mapCardInstanceToDTO(cardToDraw),
                    nextPlayer.getHand().size() + 1,
                    nextPlayer.getDeck().size() - 1);
            events.add(drewEvent);
            tempGame.apply(drewEvent);
        }

        Player nextPlayerInSim = tempGame.getPlayerById(nextPlayer.getPlayerId());
        for (CardInstance card : nextPlayerInSim.getField()) {
            if (card != null) {
                List<GameEvent> startOfTurnEvents = effectProcessor.processTrigger(tempGame,
                        EffectTrigger.START_OF_TURN_SELF, card, nextPlayerInSim, new HashMap<>());
                for (GameEvent e : startOfTurnEvents) {
                    events.add(e);
                    tempGame.apply(e);
                }
            }
        }
        return new CommandResult(events, tempGame);
    }

    private List<GameEvent> checkForDeaths(Game simulatedGame) {
        List<GameEvent> deathEvents = new ArrayList<>();
        boolean changed;
        do {
            changed = false;
            List<CardInstance> cardsToCheck = new ArrayList<>();
            if (simulatedGame.getPlayer1() != null)
                cardsToCheck.addAll(simulatedGame.getPlayer1().getField().stream().filter(c -> c != null).toList());
            if (simulatedGame.getPlayer2() != null)
                cardsToCheck.addAll(simulatedGame.getPlayer2().getField().stream().filter(c -> c != null).toList());

            for (CardInstance cardInSim : cardsToCheck) {
                if (cardInSim.isDestroyed()) {
                    boolean alreadyProcessed = deathEvents.stream()
                            .filter(e -> e instanceof CardDestroyedEvent)
                            .anyMatch(
                                    e -> ((CardDestroyedEvent) e).card.getInstanceId()
                                            .equals(cardInSim.getInstanceId()));

                    if (!alreadyProcessed) {
                        Player owner = simulatedGame.getOwnerOfCardInstance(cardInSim);
                        if (owner == null) {
                            logger.warn(
                                    "Could not find owner for destroyed card {} in temp state. Skipping death triggers.",
                                    cardInSim.getInstanceId());
                            continue;
                        }

                        CardDestroyedEvent destroyEvent = new CardDestroyedEvent(simulatedGame.getGameId(),
                                simulatedGame.getTurnNumber(), GameStateMapper.mapCardInstanceToDTO(cardInSim),
                                owner.getPlayerId());
                        deathEvents.add(destroyEvent);
                        simulatedGame.apply(destroyEvent);
                        changed = true;

                        CardInstance killer = cardInSim.getLastDamageSourceCard();
                        Map<String, Object> deathContext = new HashMap<>();
                        if (killer != null) {
                            deathContext.put("eventSource",
                                    simulatedGame.findCardInstanceFromAnyField(killer.getInstanceId()));
                        }

                        List<GameEvent> onDeathTriggers = effectProcessor.processTrigger(simulatedGame,
                                EffectTrigger.ON_DEATH, cardInSim, owner, deathContext);

                        // FIX #3: Process triggers for any damage caused by this death
                        for (GameEvent triggerEvent : onDeathTriggers) {
                            deathEvents.add(triggerEvent);
                            simulatedGame.apply(triggerEvent);

                            if (triggerEvent instanceof CombatDamageDealtEvent damageFromEffect) {
                                CardInstance damageSource = simulatedGame
                                        .findCardInstanceFromAnyField(damageFromEffect.attackerInstanceId);
                                CardInstance damageTarget = simulatedGame
                                        .findCardInstanceFromAnyField(damageFromEffect.defenderInstanceId);

                                if (damageSource != null && damageTarget != null) {
                                    List<GameEvent> damageObserverEvents = processDamageTriggers(damageSource,
                                            damageTarget, damageFromEffect.damageAfterDefense, simulatedGame);
                                    deathEvents.addAll(damageObserverEvents);
                                }
                            }
                        }

                        List<CardInstance> observers = new ArrayList<>(cardsToCheck);
                        for (CardInstance observerCard : observers) {
                            if (!observerCard.getInstanceId().equals(cardInSim.getInstanceId())) {
                                CardInstance observerInSim = simulatedGame
                                        .findCardInstanceFromAnyField(observerCard.getInstanceId());
                                if (observerInSim != null && !observerInSim.isDestroyed()) {
                                    Player observerOwner = simulatedGame.getOwnerOfCardInstance(observerInSim);
                                    Map<String, Object> context = new HashMap<>();
                                    context.put("eventTarget", cardInSim);
                                    List<GameEvent> onDeathOfAnyTriggers = effectProcessor.processTrigger(simulatedGame,
                                            EffectTrigger.ON_DEATH_OF_ANY, observerInSim, observerOwner, context);
                                    for (GameEvent triggerEvent : onDeathOfAnyTriggers) {
                                        deathEvents.add(triggerEvent);
                                        simulatedGame.apply(triggerEvent);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } while (changed);

        return deathEvents;
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

            // FIX #2 START: Check defender for targeting immunities
            CardInstance defender = game.getOpponent(player).getField().get(cmd.defenderFieldIndex);
            if (defender == null) {
                logger.warn("[{}] Command ATTACK rejected: No card at defender field index {}.", game.getGameId(),
                        cmd.defenderFieldIndex);
                return false;
            }

            Game tempGameForValidation = new Game(game);
            processAuras(tempGameForValidation);
            CardInstance defenderInSim = tempGameForValidation.findCardInstanceFromAnyField(defender.getInstanceId());

            if (defenderInSim != null && defenderInSim.getBooleanEffectFlag("status_cannot_be_targeted_AURA")) {
                logger.warn("[{}] Command ATTACK rejected: Card '{}' cannot be targeted by attacks due to an effect.",
                        game.getGameId(), defender.getDefinition().getName());
                return false;
            }
            // FIX #2 END

            if (attacker.getBooleanEffectFlag("status_cannot_attack_AURA")) {
                logger.warn("[{}] Command ATTACK rejected: Card '{}' cannot attack due to a restriction.",
                        game.getGameId(), attacker.getDefinition().getName());
                return false;
            }

            if (attacker.getBooleanEffectFlag("canAttackAgainThisTurn")) {
                return true;
            }

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