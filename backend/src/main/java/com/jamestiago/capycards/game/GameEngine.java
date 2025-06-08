package com.jamestiago.capycards.game;

import com.jamestiago.capycards.game.commands.*;
import com.jamestiago.capycards.game.events.*;
import com.jamestiago.capycards.game.effects.EffectProcessor;
import com.jamestiago.capycards.game.effects.EffectTrigger;
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

        List<GameEvent> generatedEvents;
        if (command instanceof PlayCardCommand cmd) {
            generatedEvents = handlePlayCard(game, cmd);
        } else if (command instanceof AttackCommand cmd) {
            generatedEvents = handleAttack(game, cmd);
        } else if (command instanceof EndTurnCommand cmd) {
            generatedEvents = handleEndTurn(game, cmd);
        } else if (command instanceof ActivateAbilityCommand cmd) {
            generatedEvents = handleActivateAbility(game, cmd);
        } else if (command instanceof GameOverCommand cmd) {
            generatedEvents = List.of(new GameOverEvent(game.getGameId(), game.getTurnNumber(),
                    cmd.winnerPlayerId, cmd.reason));
        } else {
            logger.warn("No handler found for command type: {}", command.getCommandType());
            generatedEvents = List.of();
        }

        logger.trace("[{}] Command {} resulted in {} events.", game.getGameId(), command.getCommandType(),
                generatedEvents.size());
        return generatedEvents;
    }

    private List<GameEvent> handlePlayCard(Game game, PlayCardCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Player player = game.getPlayerById(cmd.playerId);

        // Validation
        if (player == null || cmd.handCardIndex < 0 || cmd.handCardIndex >= player.getHand().size()
                || cmd.targetFieldSlot < 0 || cmd.targetFieldSlot >= Player.MAX_FIELD_SIZE
                || player.getField().get(cmd.targetFieldSlot) != null) {
            return List.of();
        }

        CardInstance cardToPlay = player.getHand().get(cmd.handCardIndex);

        // 1. Generate the primary event.
        CardPlayedEvent playedEvent = new CardPlayedEvent(
                game.getGameId(),
                game.getTurnNumber(),
                cmd.playerId,
                GameStateMapper.mapCardInstanceToDTO(cardToPlay),
                cmd.handCardIndex,
                cmd.targetFieldSlot,
                player.getHand().size() - 1);
        events.add(playedEvent);

        // --- BUGFIX: Create a simulation where the card is on the field BEFORE
        // checking ON_PLAY triggers ---
        Game tempGame = new Game(game);
        tempGame.apply(playedEvent); // Apply the play event to the simulation

        // 2. Check for "ON_PLAY" triggers using the simulated state.
        CardInstance cardInTempState = tempGame.findCardInstanceFromAnyField(cardToPlay.getInstanceId());
        if (cardInTempState != null) {
            Map<String, Object> context = new HashMap<>();
            context.put("eventTarget", cardInTempState); // The card played is the target of the ON_PLAY event.
            // Pass the updated tempGame to the processor
            events.addAll(effectProcessor.processTrigger(tempGame, EffectTrigger.ON_PLAY, cardInTempState,
                    tempGame.getPlayerById(cmd.playerId), context));
        }

        // 3. Check for any deaths caused by the effects, using the simulated game
        // state.
        events.addAll(checkForDeaths(tempGame, events));

        return events;
    }

    private List<GameEvent> handleAttack(Game game, AttackCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Player attackerPlayer = game.getPlayerById(cmd.playerId);
        Player defenderPlayer = game.getOpponent(attackerPlayer);

        if (attackerPlayer == null || defenderPlayer == null)
            return List.of();
        if (cmd.attackerFieldIndex < 0 || cmd.attackerFieldIndex >= Player.MAX_FIELD_SIZE)
            return List.of();
        if (cmd.defenderFieldIndex < 0 || cmd.defenderFieldIndex >= Player.MAX_FIELD_SIZE)
            return List.of();

        CardInstance attacker = attackerPlayer.getField().get(cmd.attackerFieldIndex);
        CardInstance defender = defenderPlayer.getField().get(cmd.defenderFieldIndex);

        if (attacker == null || defender == null || attacker.isExhausted() || !attackerPlayer.canDeclareAttack()) {
            return List.of(); // Invalid attack
        }

        // 1. Declare Attack & Trigger ON_ATTACK_DECLARE
        events.add(new AttackDeclaredEvent(game.getGameId(), game.getTurnNumber(), attackerPlayer.getPlayerId(),
                attacker.getInstanceId(), defender.getInstanceId()));
        Map<String, Object> attackDeclareContext = new HashMap<>();
        attackDeclareContext.put("eventTarget", defender);
        events.addAll(effectProcessor.processTrigger(game, EffectTrigger.ON_ATTACK_DECLARE, attacker, attackerPlayer,
                attackDeclareContext));

        // 2. Resolve Combat Damage
        int defenderLifeBefore = defender.getCurrentLife();
        int attackPower = attacker.getCurrentAttack();
        if (attacker.getBooleanEffectFlag("double_damage_this_attack")) {
            attackPower *= 2;
            events.add(new CardFlagChangedEvent(game.getGameId(), game.getTurnNumber(), attacker.getInstanceId(),
                    "double_damage_this_attack", null, "PERMANENT"));
        }
        int defensePower = defender.getCurrentDefense();
        int damageToDeal = Math.max(0, attackPower - defensePower);

        // 3. Generate Damage Event
        events.add(new CombatDamageDealtEvent(game.getGameId(), game.getTurnNumber(), attacker.getInstanceId(),
                defender.getInstanceId(), attackPower, damageToDeal, defenderLifeBefore,
                Math.max(0, defenderLifeBefore - damageToDeal)));

        // 3.5. Trigger ON_DAMAGE_TAKEN_OF_ANY for all other cards on the field
        if (damageToDeal > 0) {
            List<CardInstance> allCards = new ArrayList<>();
            allCards.addAll(attackerPlayer.getFieldInternal().stream().filter(c -> c != null).toList());
            allCards.addAll(defenderPlayer.getFieldInternal().stream().filter(c -> c != null).toList());

            for (CardInstance observerCard : allCards) {
                if (!observerCard.getInstanceId().equals(defender.getInstanceId())) {
                    Player observerOwner = game.getOwnerOfCardInstance(observerCard);
                    if (observerOwner != null) {
                        Map<String, Object> context = new HashMap<>();
                        context.put("eventTarget", defender);
                        context.put("eventSource", attacker);
                        context.put("damageAmount", damageToDeal);
                        events.addAll(effectProcessor.processTrigger(game, EffectTrigger.ON_DAMAGE_TAKEN_OF_ANY,
                                observerCard, observerOwner, context));
                    }
                }
            }
        }

        // 4. Trigger ON_DAMAGE_DEALT and ON_DAMAGE_TAKEN effects
        Map<String, Object> damageDealtContext = new HashMap<>();
        damageDealtContext.put("eventTarget", defender);
        damageDealtContext.put("damageAmount", damageToDeal);
        if (defenderLifeBefore - damageToDeal <= 0) {
            damageDealtContext.put("targetIsDestroyed", true);
        }
        events.addAll(effectProcessor.processTrigger(game, EffectTrigger.ON_DAMAGE_DEALT, attacker, attackerPlayer,
                damageDealtContext));

        Map<String, Object> damageTakenContext = new HashMap<>();
        damageTakenContext.put("eventSource", attacker);
        damageTakenContext.put("damageAmount", damageToDeal);
        events.addAll(effectProcessor.processTrigger(game, EffectTrigger.ON_DAMAGE_TAKEN, defender, defenderPlayer,
                damageTakenContext));

        return events;
    }

    private List<GameEvent> handleActivateAbility(Game game, ActivateAbilityCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Player player = game.getPlayerById(cmd.playerId);
        CardInstance sourceCard = game.findCardInstanceFromAnyField(cmd.sourceCardInstanceId);

        if (player == null || sourceCard == null)
            return List.of();

        events.add(new AbilityActivatedEvent(game.getGameId(), game.getTurnNumber(),
                cmd.sourceCardInstanceId, cmd.targetCardInstanceId, cmd.abilityOptionIndex));

        Map<String, Object> context = new HashMap<>();
        context.put("targetCardInstanceId", cmd.targetCardInstanceId);
        context.put("abilityOptionIndex", cmd.abilityOptionIndex);

        events.addAll(effectProcessor.processTrigger(game, EffectTrigger.ACTIVATED, sourceCard, player, context));
        events.addAll(checkForDeaths(game, events));

        return events;
    }

    private List<GameEvent> handleEndTurn(Game game, EndTurnCommand cmd) {
        List<GameEvent> events = new ArrayList<>();
        Player endingPlayer = game.getPlayerById(cmd.playerId);
        if (endingPlayer == null || game.getCurrentPlayer() != endingPlayer)
            return List.of();

        for (CardInstance card : endingPlayer.getField()) {
            if (card != null) {
                events.addAll(effectProcessor.processTrigger(game, EffectTrigger.END_OF_TURN_SELF, card, endingPlayer,
                        new HashMap<>()));
            }
        }
        events.addAll(checkForDeaths(game, events));

        events.add(new TurnEndedEvent(game.getGameId(), game.getTurnNumber(), endingPlayer.getPlayerId()));

        Player nextPlayer = game.getOpponent(endingPlayer);
        int currentTurnNumber = game.getTurnNumber();

        if (nextPlayer.getDeck().isEmpty()) {
            events.add(new GameOverEvent(game.getGameId(), currentTurnNumber + 1, endingPlayer.getPlayerId(),
                    nextPlayer.getDisplayName() + " decked out."));
            return events;
        }

        events.add(new TurnStartedEvent(game.getGameId(), currentTurnNumber, nextPlayer.getPlayerId()));

        if (nextPlayer.getHand().size() < Player.MAX_HAND_SIZE) {
            if (nextPlayer.getDeck().isEmpty()) {
                events.add(new GameOverEvent(game.getGameId(), currentTurnNumber + 1, endingPlayer.getPlayerId(),
                        nextPlayer.getDisplayName() + " ran out of cards while drawing."));
                return events;
            }
            CardInstance cardToDraw = nextPlayer.getDeck().getCards().get(0);
            events.add(new PlayerDrewCardEvent(
                    game.getGameId(), currentTurnNumber + 1, nextPlayer.getPlayerId(),
                    GameStateMapper.mapCardInstanceToDTO(cardToDraw),
                    nextPlayer.getHand().size() + 1,
                    nextPlayer.getDeck().size() - 1));
        }

        Game tempGameForTurnStart = new Game(game);
        List<GameEvent> allEventsSoFar = new ArrayList<>(events);
        for (GameEvent e : allEventsSoFar) {
            tempGameForTurnStart.apply(e);
        }

        Player nextPlayerInTempState = tempGameForTurnStart.getPlayerById(nextPlayer.getPlayerId());
        if (nextPlayerInTempState != null) {
            for (CardInstance cardInNewTurn : nextPlayerInTempState.getField()) {
                if (cardInNewTurn != null) {
                    events.addAll(effectProcessor.processTrigger(tempGameForTurnStart, EffectTrigger.START_OF_TURN_SELF,
                            cardInNewTurn,
                            nextPlayerInTempState, new HashMap<>()));
                }
            }
        }

        events.addAll(checkForDeaths(game, events));

        return events;
    }

    public List<GameEvent> checkForDeaths(Game game, List<GameEvent> existingEvents) {
        List<GameEvent> deathEvents = new ArrayList<>();

        // Create the simulation state *once* at the beginning.
        Game tempGame = new Game(game);
        for (GameEvent e : existingEvents) {
            tempGame.apply(e);
        }

        boolean changed;
        do {
            changed = false;
            List<CardInstance> cardsToCheck = new ArrayList<>();
            if (tempGame.getPlayer1() != null)
                cardsToCheck.addAll(tempGame.getPlayer1().getField().stream().filter(c -> c != null).toList());
            if (tempGame.getPlayer2() != null)
                cardsToCheck.addAll(tempGame.getPlayer2().getField().stream().filter(c -> c != null).toList());

            for (CardInstance cardInTempState : cardsToCheck) {
                if (cardInTempState.isDestroyed()) {
                    boolean alreadyProcessed = deathEvents.stream()
                            .filter(e -> e instanceof CardDestroyedEvent)
                            .anyMatch(
                                    e -> ((CardDestroyedEvent) e).card.getInstanceId()
                                            .equals(cardInTempState.getInstanceId()));

                    if (!alreadyProcessed) {
                        Player owner = tempGame.getOwnerOfCardInstance(cardInTempState);
                        if (owner == null) {
                            logger.warn(
                                    "Could not find owner for destroyed card {} in temp state. Skipping death triggers.",
                                    cardInTempState.getInstanceId());
                            continue;
                        }

                        // Generate and immediately apply the destroy event
                        CardDestroyedEvent destroyEvent = new CardDestroyedEvent(tempGame.getGameId(),
                                tempGame.getTurnNumber(), GameStateMapper.mapCardInstanceToDTO(cardInTempState),
                                owner.getPlayerId());
                        deathEvents.add(destroyEvent);
                        tempGame.apply(destroyEvent);

                        // Trigger ON_DEATH for the card itself and apply results immediately
                        List<GameEvent> onDeathTriggers = effectProcessor.processTrigger(tempGame,
                                EffectTrigger.ON_DEATH, cardInTempState, owner, new HashMap<>());
                        for (GameEvent triggerEvent : onDeathTriggers) {
                            deathEvents.add(triggerEvent);
                            tempGame.apply(triggerEvent);
                        }

                        // Trigger ON_DEATH_OF_ANY for all OTHER cards and apply results immediately
                        for (CardInstance observerCard : cardsToCheck) {
                            if (!observerCard.getInstanceId().equals(cardInTempState.getInstanceId())) {
                                CardInstance observerInTempState = tempGame
                                        .findCardInstanceFromAnyField(observerCard.getInstanceId());
                                if (observerInTempState != null && !observerInTempState.isDestroyed()) {
                                    Player observerOwner = tempGame.getOwnerOfCardInstance(observerInTempState);
                                    if (observerOwner != null) {
                                        Map<String, Object> context = new HashMap<>();
                                        context.put("eventTarget", cardInTempState);
                                        List<GameEvent> onDeathOfAnyTriggers = effectProcessor.processTrigger(tempGame,
                                                EffectTrigger.ON_DEATH_OF_ANY, observerInTempState, observerOwner,
                                                context);
                                        for (GameEvent triggerEvent : onDeathOfAnyTriggers) {
                                            deathEvents.add(triggerEvent);
                                            tempGame.apply(triggerEvent);
                                        }
                                    }
                                }
                            }
                        }
                        changed = true; // A death was found, loop again for chain reactions.
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

        if (command instanceof AttackCommand) {
            CardInstance attacker = player.getField().get(((AttackCommand) command).attackerFieldIndex);
            if (attacker != null) {
                if (attacker.getBooleanEffectFlag("canAttackAgain")) {
                    return true;
                }
                if (attacker.isExhausted()) {
                    logger.warn("[{}] Command ATTACK rejected: Attacking card '{}' is exhausted.", game.getGameId(),
                            attacker.getDefinition().getName());
                    return false;
                }
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