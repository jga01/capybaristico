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
import java.util.stream.Stream;

@Service
public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);
    private final EffectProcessor effectProcessor;

    public GameEngine() {
        this.effectProcessor = new EffectProcessor();
    }

    private record CommandResult(List<GameEvent> events, Game simulatedGame) {
        CommandResult(List<GameEvent> events, Game simulatedGame) {
            this.events = events;
            this.simulatedGame = new Game(simulatedGame);
        }
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

        Game gameAfterAction = new Game(result.simulatedGame());
        List<GameEvent> allEvents = new ArrayList<>(result.events());

        // Step 1: Process auras based on the state immediately after the command's
        // action.
        List<GameEvent> auraEvents = processAuras(gameAfterAction);
        allEvents.addAll(auraEvents);

        // Step 2: Check for any deaths that resulted from the command and initial aura
        // updates.
        // This method applies the death events to the 'gameAfterAction' simulation
        // internally.
        List<GameEvent> deathEvents = checkForDeaths(gameAfterAction);
        allEvents.addAll(deathEvents);

        // Step 3: If any deaths occurred, the board state has changed,
        // so we MUST recalculate auras to remove any buffs from now-dead cards.
        if (!deathEvents.isEmpty()) {
            logger.trace("[{}] Deaths occurred. Recalculating auras.", game.getGameId());
            List<GameEvent> postDeathAuraEvents = processAuras(gameAfterAction);
            allEvents.addAll(postDeathAuraEvents);

            // It's possible for an aura change to kill a card (e.g. an effect that sets
            // life based on another card that just died)
            // so we do one final death check. This handles complex chain reactions.
            List<GameEvent> finalDeathCheckEvents = checkForDeaths(gameAfterAction);
            if (!finalDeathCheckEvents.isEmpty()) {
                logger.trace("[{}] Additional deaths found after post-death aura update. Adding events.",
                        game.getGameId());
                allEvents.addAll(finalDeathCheckEvents);
            }
        }

        logger.trace("[{}] Command {} resulted in {} total events.", game.getGameId(), command.getCommandType(),
                allEvents.size());
        return allEvents;
    }

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
                    for (GameEvent e : onAnyDamageEvents) {
                        subsequentEvents.add(e);
                        simulatedGame.apply(e);
                    }
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

        if (!cardToPlay.getDefinition().isDirectlyPlayable()) {
            logger.warn("Player {} attempted to illegally play card '{}' from hand.", cmd.playerId,
                    cardToPlay.getDefinition().getName());
            return new CommandResult(List.of(), game);
        }

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
                attackerPlayer.getPlayerId(), attacker.getInstanceId(), attacker.getDefinition().getName(),
                defender.getInstanceId(), defender.getDefinition().getName());
        events.add(declaredEvent);
        tempGame.apply(declaredEvent);

        Map<String, Object> defendContext = new HashMap<>();
        defendContext.put("eventSource", attacker);
        List<GameEvent> onDefendEvents = effectProcessor.processTrigger(tempGame, EffectTrigger.ON_DEFEND, defender,
                defenderPlayer, defendContext);
        for (GameEvent e : onDefendEvents) {
            events.add(e);
            tempGame.apply(e);
        }

        Map<String, Object> attackDeclareContext = new HashMap<>();
        attackDeclareContext.put("eventTarget", defender);
        List<GameEvent> onAttackEvents = effectProcessor.processTrigger(tempGame, EffectTrigger.ON_ATTACK_DECLARE,
                attacker, attackerPlayer, attackDeclareContext);
        for (GameEvent e : onAttackEvents) {
            events.add(e);
            tempGame.apply(e);
        }

        CardInstance attackerAfterEffect = tempGame.findCardInstanceFromAnyField(attacker.getInstanceId());
        if (attackerAfterEffect == null || attackerAfterEffect.isDestroyed()) {
            logger.debug("Attacker {} was destroyed by its own ON_ATTACK_DECLARE effect. Aborting attack.",
                    attacker.getDefinition().getName());
            return new CommandResult(events, tempGame);
        }

        CardInstance defenderInSim = tempGame.findCardInstanceFromAnyField(defender.getInstanceId());
        if (defenderInSim == null || defenderInSim.isDestroyed()) {
            logger.debug("Defender {} was destroyed by an ON_DEFEND effect. Aborting attack.",
                    defender.getDefinition().getName());
            return new CommandResult(events, tempGame);
        }

        int defenderLifeBefore = defenderInSim.getCurrentLife();
        int attackPower = attackerAfterEffect.getCurrentAttack();
        if (attackerAfterEffect.getBooleanEffectFlag("double_damage_this_attack")) {
            attackPower *= 2;
            CardFlagChangedEvent flagEvent = new CardFlagChangedEvent(game.getGameId(), game.getTurnNumber(),
                    attackerAfterEffect.getInstanceId(), "double_damage_this_attack", null, "PERMANENT");
            events.add(flagEvent);
            tempGame.apply(flagEvent);
        }

        int damageToDeal = effectProcessor.calculateFinalDamage(tempGame, attackPower, attackerAfterEffect,
                defenderInSim, "ATTACK");

        CombatDamageDealtEvent damageEvent = new CombatDamageDealtEvent(game.getGameId(), game.getTurnNumber(),
                attackerAfterEffect.getInstanceId(), defenderInSim.getInstanceId(), "ATTACK", attackPower, damageToDeal,
                defenderLifeBefore,
                Math.max(0, defenderInSim.getCurrentLife() - damageToDeal));
        events.add(damageEvent);
        tempGame.apply(damageEvent);

        List<GameEvent> subsequentEvents = processDamageTriggers(attackerAfterEffect, defenderInSim, damageToDeal,
                tempGame);
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

        if (nextPlayer.getDeck().isEmpty()) {
            String reason = nextPlayer.getDisplayName() + " cannot draw a card and has lost.";
            GameOverEvent gameOverEvent = new GameOverEvent(game.getGameId(), currentTurnNumber + 1,
                    endingPlayer.getPlayerId(), reason);
            events.add(gameOverEvent);
            tempGame.apply(gameOverEvent); // Apply to simulation to prevent further actions
            return new CommandResult(events, tempGame);
        }

        TurnStartedEvent startedEvent = new TurnStartedEvent(game.getGameId(), currentTurnNumber,
                nextPlayer.getPlayerId());
        events.add(startedEvent);
        tempGame.apply(startedEvent);

        int newTurnNumber = startedEvent.newTurnNumber;

        if (nextPlayer.getHand().size() < Player.MAX_HAND_SIZE && !nextPlayer.getDeck().isEmpty()) {
            CardInstance cardToDraw = nextPlayer.getDeck().getCards().get(0);
            PlayerDrewCardEvent drewEvent = new PlayerDrewCardEvent(
                    game.getGameId(), newTurnNumber, nextPlayer.getPlayerId(),
                    GameStateMapper.mapCardInstanceToDTO(cardToDraw),
                    nextPlayer.getHand().size() + 1,
                    nextPlayer.getDeck().size() - 1);
            events.add(drewEvent);
            tempGame.apply(drewEvent);
        }

        List<GameEvent> startOfTurnScheduledEvents = processScheduledActions(tempGame, nextPlayer);
        for (GameEvent e : startOfTurnScheduledEvents) {
            events.add(e);
            tempGame.apply(e);
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

    private boolean isCardDead(CardInstance card) {
        if (card.getBooleanEffectFlag("can_survive_lethal")) {
            // Custom death condition for Realistjames
            return (card.getCurrentLife() + card.getCurrentDefense()) <= 0;
        }
        return card.isDestroyed(); // Standard condition: life <= 0
    }

    private List<GameEvent> checkForDeaths(Game simulatedGame) {
        List<GameEvent> deathEvents = new ArrayList<>();
        boolean changed;

        int maxIterations = 30;
        int iterationCount = 0;

        do {
            iterationCount++;
            if (iterationCount > maxIterations) {
                logger.error("[{}] Infinite loop detected in checkForDeaths! Forcing game over.",
                        simulatedGame.getGameId());
                deathEvents.add(new GameOverEvent(simulatedGame.getGameId(), simulatedGame.getTurnNumber(), null,
                        "Game ended due to an unresolved infinite effect loop."));
                break;
            }

            changed = false;
            List<CardInstance> cardsToCheck = Stream.concat(
                    simulatedGame.getPlayer1().getFieldInternal().stream(),
                    simulatedGame.getPlayer2().getFieldInternal().stream())
                    .filter(c -> c != null)
                    .toList();

            for (CardInstance cardInSim : cardsToCheck) {
                if (isCardDead(cardInSim)) {
                    boolean alreadyProcessed = deathEvents.stream()
                            .filter(e -> e instanceof CardDestroyedEvent)
                            .anyMatch(e -> ((CardDestroyedEvent) e).card.getInstanceId()
                                    .equals(cardInSim.getInstanceId()));

                    if (!alreadyProcessed) {
                        Player owner = simulatedGame.getOwnerOfCardInstance(cardInSim);
                        if (owner == null)
                            continue;

                        // Phase 1: Fire ON_DEATH triggers for the dying card itself.
                        CardInstance killer = cardInSim.getLastDamageSourceCard();
                        Map<String, Object> deathContext = new HashMap<>();
                        deathContext.put("eventTarget", cardInSim);
                        if (killer != null) {
                            deathContext.put("eventSource",
                                    simulatedGame.findCardInstanceFromAnyField(killer.getInstanceId()));
                        }
                        List<GameEvent> onDeathTriggers = effectProcessor.processTrigger(simulatedGame,
                                EffectTrigger.ON_DEATH, cardInSim, owner, deathContext);
                        for (GameEvent triggerEvent : onDeathTriggers) {
                            deathEvents.add(triggerEvent);
                            simulatedGame.apply(triggerEvent);
                        }

                        // Phase 1.5: Re-check if the card saved itself.
                        // If the card is no longer dead after its own death triggers resolved, it has
                        // saved itself.
                        if (!isCardDead(cardInSim)) {
                            logger.debug("Card {} saved itself from death with an ON_DEATH trigger.",
                                    cardInSim.getDefinition().getName());
                            // Mark that the state has changed and break to restart the main `do-while`
                            // loop.
                            // This correctly handles the state change and prevents wrongful destruction.
                            changed = true;
                            break;
                        }

                        // Phase 2: Fire ON_DEATH_OF_ANY for all other cards.
                        List<CardInstance> observers = new ArrayList<>(cardsToCheck);
                        for (CardInstance observerCard : observers) {
                            if (!observerCard.getInstanceId().equals(cardInSim.getInstanceId())) {
                                CardInstance observerInSim = simulatedGame
                                        .findCardInstanceFromAnyField(observerCard.getInstanceId());
                                if (observerInSim != null && !isCardDead(observerInSim)) {
                                    Player observerOwner = simulatedGame.getOwnerOfCardInstance(observerInSim);
                                    List<GameEvent> onDeathOfAnyTriggers = effectProcessor.processTrigger(simulatedGame,
                                            EffectTrigger.ON_DEATH_OF_ANY, observerInSim, observerOwner, deathContext);
                                    for (GameEvent triggerEvent : onDeathOfAnyTriggers) {
                                        deathEvents.add(triggerEvent);
                                        simulatedGame.apply(triggerEvent);
                                    }
                                }
                            }
                        }

                        // Phase 3: Create and apply the CardDestroyedEvent to officially remove the
                        // card.
                        CardDestroyedEvent destroyEvent = new CardDestroyedEvent(simulatedGame.getGameId(),
                                simulatedGame.getTurnNumber(), GameStateMapper.mapCardInstanceToDTO(cardInSim),
                                owner.getPlayerId());
                        deathEvents.add(destroyEvent);
                        simulatedGame.apply(destroyEvent);
                        changed = true;

                        break;
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

        if (player.isAi()) {
            logger.trace("Bypassing turn validation for AI player command.");
            return true;
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