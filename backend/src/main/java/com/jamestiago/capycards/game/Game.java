package com.jamestiago.capycards.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.jamestiago.capycards.game.effects.EffectProcessor;
import com.jamestiago.capycards.game.effects.EffectTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Game {
    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private final String gameId;
    private Player player1;
    private Player player2;
    private Player currentPlayer;
    private GameState gameState;
    private int turnNumber;

    // New game-wide flag storage
    private final Map<String, Object> gameFlags = new ConcurrentHashMap<>();

    private int player1FieldEmptyConsecutiveTurns;
    private int player2FieldEmptyConsecutiveTurns;

    private List<CardInstance> graveyardPlayer1 = new ArrayList<>();
    private List<CardInstance> graveyardPlayer2 = new ArrayList<>();

    public enum GameState {
        WAITING_FOR_PLAYERS,
        INITIAL_DRAW,
        PLAYER_1_TURN,
        PLAYER_2_TURN,
        GAME_OVER_PLAYER_1_WINS,
        GAME_OVER_PLAYER_2_WINS,
        GAME_OVER_DRAW
    }

    private EffectProcessor effectProcessor;

    public Game(Player p1, Player p2) {
        this.gameId = UUID.randomUUID().toString();
        this.player1 = p1;
        this.player2 = p2;
        this.gameState = GameState.WAITING_FOR_PLAYERS;
        this.turnNumber = 0;
        this.player1FieldEmptyConsecutiveTurns = 0;
        this.player2FieldEmptyConsecutiveTurns = 0;
        this.effectProcessor = new EffectProcessor(this);
    }

    public Map<String, Object> getGameFlags() {
        return gameFlags;
    }

    // Setter for GameState, primarily for disconnect scenario
    public void setGameState(GameState gameState) {
        if (this.gameState.name().contains("GAME_OVER") && !gameState.name().contains("GAME_OVER")) {
            // Prevent changing from a game over state to a non-game over state, unless by
            // specific design
            System.err.println(
                    "Attempted to change game state from GAME_OVER to " + gameState + ". This might be an error.");
            return;
        }
        this.gameState = gameState;
    }

    public void startGame() {
        if (player1 == null || player2 == null) {
            System.err.println("Cannot start game: Not all players are present.");
            return;
        }
        this.currentPlayer = player1; // Player 1 starts
        this.gameState = GameState.INITIAL_DRAW; // Indicate initial setup phase
        this.turnNumber = 1; // Game is now on Turn 1

        // Players draw their initial hand of 5 cards.
        // The new drawUntilHandIsFull handles this perfectly for initial draw as well.
        System.out.println("Setting up initial hands...");
        if (!player1.drawUntilHandIsFull(Player.MAX_HAND_SIZE)) {
            // This should not happen with a fresh 20-card deck unless MAX_HAND_SIZE > 20
            System.err.println("CRITICAL: Player 1 failed initial draw. Game cannot start properly.");
            this.gameState = GameState.GAME_OVER_PLAYER_2_WINS; // P1 decks out on setup
            return;
        }
        if (!player2.drawUntilHandIsFull(Player.MAX_HAND_SIZE)) {
            System.err.println("CRITICAL: Player 2 failed initial draw. Game cannot start properly.");
            this.gameState = GameState.GAME_OVER_PLAYER_1_WINS; // P2 decks out on setup
            return;
        }

        player1.resetTurnActions();
        player2.resetTurnActions();

        this.graveyardPlayer1.clear(); // Clear potential revival lists on new game
        this.graveyardPlayer2.clear();

        // Transition to Player 1's first turn. P1 does NOT draw again here.
        advanceToPlayerTurn(this.currentPlayer, true); // true for isFirstTurnOfGame
        System.out.println("Game " + gameId + " started. Current player: " + currentPlayer.getDisplayName() + " (Turn "
                + turnNumber + ")");
    }

    private void advanceToPlayerTurn(Player player, boolean isFirstTurnOfGameAndThisIsP1) {
        if (this.gameState.name().contains("GAME_OVER"))
            return;

        this.currentPlayer = player;
        if (player == player1) {
            this.gameState = GameState.PLAYER_1_TURN;
        } else {
            this.gameState = GameState.PLAYER_2_TURN;
        }

        this.currentPlayer.resetTurnActions(); // Unexhaust cards, reset attack/play flags

        // START_OF_TURN effects
        Map<String, Object> startTurnContext = new HashMap<>();
        for (CardInstance card : this.currentPlayer.getFieldInternal()) {
            if (card != null) {
                effectProcessor.processTrigger(EffectTrigger.START_OF_TURN_SELF, card, this.currentPlayer,
                        startTurnContext);
            }
        }
        checkAllFieldsForDestroyedCards();

        handleStartOfTurnRevivals(player); // Revival logic for cards like Train

        // Draw Phase for the current player:
        // Player 1 on their very first turn of the game does NOT draw.
        // All other players on all other turns draw up to MAX_HAND_SIZE.
        if (!(isFirstTurnOfGameAndThisIsP1 && player == player1)) {
            System.out.println(currentPlayer.getDisplayName() + " starts their turn. Drawing up to "
                    + Player.MAX_HAND_SIZE + " cards.");
            if (!this.currentPlayer.drawUntilHandIsFull(Player.MAX_HAND_SIZE)) {
                // Deck out condition: player needed to draw but couldn't.
                System.out.println(this.currentPlayer.getDisplayName() + " decked out attempting to draw! "
                        + getOpponent(this.currentPlayer).getDisplayName() + " wins!");
                this.gameState = (this.currentPlayer == player1) ? GameState.GAME_OVER_PLAYER_2_WINS
                        : GameState.GAME_OVER_PLAYER_1_WINS;
                return; // Game ends
            }
        } else {
            System.out.println(
                    currentPlayer.getDisplayName() + " starts their first turn (P1T1). No draw phase this turn.");
        }
    }

    public void triggerOnSummon(CardInstance summonedCard, Player summoner) {
        Map<String, Object> context = new HashMap<>();
        context.put("eventTarget", summonedCard);
        context.put("eventSource", summoner);

        for (Player p : List.of(player1, player2)) {
            if (p == null)
                continue;
            for (CardInstance card : p.getFieldInternal()) {
                if (card != null && !card.getInstanceId().equals(summonedCard.getInstanceId())) {
                    effectProcessor.processTrigger(EffectTrigger.ON_SUMMON, card, p, context);
                }
            }
        }
    }

    public void processAllAuras() {
        logger.trace("Processing all continuous auras...");
        for (Player p : List.of(player1, player2)) {
            if (p == null)
                continue;
            for (CardInstance card : p.getFieldInternal()) {
                if (card != null) {
                    effectProcessor.processTrigger(EffectTrigger.CONTINUOUS_AURA, card, p, new HashMap<>());
                }
            }
        }
    }

    private void handleStartOfTurnRevivals(Player player) {
        // This logic is now part of a CUSTOM_LOGIC effect for Train, but we'll keep the
        // revival framework here.
        // It now checks for a generic "revive_next_turn" flag.
        List<CardInstance> revivalGraveyard = (player == player1) ? graveyardPlayer1 : graveyardPlayer2;
        List<CardInstance> toReviveThisTurn = new ArrayList<>();

        for (CardInstance card : revivalGraveyard) {
            if (card.getBooleanEffectFlag("revive_next_turn")) {
                toReviveThisTurn.add(card);
            }
        }

        if (!toReviveThisTurn.isEmpty()) {
            logger.info("Player {} has cards to revive at start of turn: {}", player.getDisplayName(),
                    toReviveThisTurn.stream().map(c -> c.getDefinition().getName()).collect(Collectors.toList()));
        }

        for (CardInstance cardToRevive : toReviveThisTurn) {
            int emptySlot = player.findEmptyFieldSlot();

            if (emptySlot != -1) {
                cardToRevive.setCurrentLife(cardToRevive.getDefinition().getInitialLife());
                cardToRevive.setExhausted(true);
                cardToRevive.removeEffectFlag("revive_next_turn"); // Reset flag

                player.getFieldInternal().set(emptySlot, cardToRevive);
                revivalGraveyard.remove(cardToRevive);

                logger.info("{}'s {} has returned to the field in slot {}!", player.getDisplayName(),
                        cardToRevive.getDefinition().getName(), emptySlot);

                Map<String, Object> playContext = new HashMap<>();
                playContext.put("isRevive", true);
                effectProcessor.processTrigger(EffectTrigger.ON_PLAY, cardToRevive, player, playContext);
                triggerOnSummon(cardToRevive, player);
                processAllAuras();
                checkAllFieldsForDestroyedCards();
            } else {
                logger.info("{}'s {} could not return, field is full. It goes to the normal discard pile.",
                        player.getDisplayName(), cardToRevive.getDefinition().getName());
                player.addCardToDiscardPile(cardToRevive);
                cardToRevive.removeEffectFlag("revive_next_turn");
                revivalGraveyard.remove(cardToRevive);
            }
        }
    }

    public void endTurn() {
        if (gameState.name().contains("GAME_OVER")) {
            System.out.println("Game is already over.");
            return;
        }

        Player playerWhoseTurnEnded = this.currentPlayer;
        System.out.println("Player " + playerWhoseTurnEnded.getDisplayName() + " ends their turn.");

        // END_OF_TURN effects
        Map<String, Object> endTurnContext = new HashMap<>();
        for (CardInstance card : playerWhoseTurnEnded.getFieldInternal()) {
            if (card != null) {
                effectProcessor.processTrigger(EffectTrigger.END_OF_TURN_SELF, card, playerWhoseTurnEnded,
                        endTurnContext);
            }
        }
        checkAllFieldsForDestroyedCards();

        playerWhoseTurnEnded.discardDownToMaxHandSize();

        if (playerWhoseTurnEnded.isFieldEmpty()) {
            if (playerWhoseTurnEnded == player1)
                player1FieldEmptyConsecutiveTurns++;
            else
                player2FieldEmptyConsecutiveTurns++;

            if (player1FieldEmptyConsecutiveTurns >= 2) {
                this.gameState = GameState.GAME_OVER_PLAYER_2_WINS;
                return;
            } else if (player2FieldEmptyConsecutiveTurns >= 2) {
                this.gameState = GameState.GAME_OVER_PLAYER_1_WINS;
                return;
            }
        } else {
            if (playerWhoseTurnEnded == player1)
                player1FieldEmptyConsecutiveTurns = 0;
            else
                player2FieldEmptyConsecutiveTurns = 0;
        }

        Player nextPlayer = getOpponent(playerWhoseTurnEnded);
        if (playerWhoseTurnEnded == player2)
            this.turnNumber++;
        this.currentPlayer = nextPlayer;

        advanceToPlayerTurn(currentPlayer, false);
    }

    public String playerPlaysCard(Player player, int handIndex, int fieldSlotIndex) {
        if (gameState.name().contains("GAME_OVER"))
            return "Game is already over.";
        if (player != currentPlayer || !isPlayerTurnState(player))
            return "Not your turn or invalid game state.";
        if (handIndex < 0 || handIndex >= player.getHand().size())
            return "Invalid hand index.";

        CardInstance cardToPlayInstance = player.getHand().get(handIndex);
        if (cardToPlayInstance == null)
            return "Card at hand index is null.";

        boolean success = player.playCardFromHandToField(handIndex, fieldSlotIndex);

        if (success) {
            CardInstance playedCardOnField = player.getField().get(fieldSlotIndex);
            if (playedCardOnField != null) {
                playedCardOnField.setExhausted(true);
                logger.info("{} played {}. It is now exhausted.", player.getDisplayName(),
                        playedCardOnField.getDefinition().getName());

                Map<String, Object> playContext = new HashMap<>();
                playContext.put("eventTarget", playedCardOnField); // The card itself is the target of the ON_PLAY event
                effectProcessor.processTrigger(EffectTrigger.ON_PLAY, playedCardOnField, player, playContext);
                triggerOnSummon(playedCardOnField, player);
                processAllAuras();
                checkAllFieldsForDestroyedCards();
            }
            return null;
        } else {
            return "Could not play card " + cardToPlayInstance.getDefinition().getName() + " to field slot "
                    + fieldSlotIndex + ". (Slot occupied or invalid index).";
        }
    }

    public String playerAttacks(Player attackingPlayer, int attackerFieldIndex, Player defendingPlayer,
            int defenderFieldIndex) {
        if (gameState.name().contains("GAME_OVER"))
            return "Game is already over.";
        if (attackingPlayer != currentPlayer || !isPlayerTurnState(attackingPlayer))
            return "Not your turn.";
        if (!attackingPlayer.canDeclareAttack()
                && !attackingPlayer.getField().get(attackerFieldIndex).getBooleanEffectFlag("canAttackAgain"))
            return "Maximum number of attacks reached this turn.";

        CardInstance attacker = attackingPlayer.getField().get(attackerFieldIndex);
        CardInstance defender = defendingPlayer.getField().get(defenderFieldIndex);

        if (attacker == null)
            return "Selected attacker slot is empty.";
        if (defender == null)
            return "Selected defender slot is empty.";
        if (attacker.isExhausted())
            return attacker.getDefinition().getName() + " is exhausted.";

        Map<String, Object> attackContext = new HashMap<>();
        attackContext.put("eventTarget", defender);
        effectProcessor.processTrigger(EffectTrigger.ON_ATTACK_DECLARE, attacker, attackingPlayer, attackContext);
        checkAllFieldsForDestroyedCards();

        attacker = attackingPlayer.getField().get(attackerFieldIndex); // Re-fetch in case it was modified/destroyed
        defender = defendingPlayer.getField().get(defenderFieldIndex);
        if (attacker == null || attacker.isDestroyed())
            return "Attacker destroyed by pre-attack effect.";
        if (defender == null || defender.isDestroyed())
            return "Defender destroyed by pre-attack effect.";
        if (attacker.isExhausted())
            return attacker.getDefinition().getName() + " became exhausted by a pre-attack effect.";

        Map<String, Object> defendContext = new HashMap<>();
        defendContext.put("eventSource", attacker);
        effectProcessor.processTrigger(EffectTrigger.ON_DEFEND, defender, defendingPlayer, defendContext);
        checkAllFieldsForDestroyedCards();

        attacker = attackingPlayer.getField().get(attackerFieldIndex); // Re-fetch again
        defender = defendingPlayer.getField().get(defenderFieldIndex);
        if (attacker == null || attacker.isDestroyed() || defender == null || defender.isDestroyed()) {
            return "Attacker or Defender destroyed by pre-combat effects.";
        }

        int damageToDeal = Math.max(0, attacker.getCurrentAttack() - defender.getCurrentDefense());

        // Bridge for Crazysoup's double damage effect
        if (attacker.getBooleanEffectFlag("double_damage_activeThisAttack")) {
            logger.info("{}'s attack is doubled!", attacker.getDefinition().getName());
            damageToDeal *= 2;
            attacker.removeEffectFlag("double_damage_activeThisAttack");
        }

        logger.info("{} (ATK:{}) attacks {} (DEF:{}, L:{}) for {} damage.", attacker.getDefinition().getName(),
                attacker.getCurrentAttack(), defender.getDefinition().getName(), defender.getCurrentDefense(),
                defender.getCurrentLife(), damageToDeal);

        if (damageToDeal > 0) {
            effectProcessor.applyDamage(defender, damageToDeal, attacker, defendingPlayer, attackingPlayer);
        }

        if (attacker.getBooleanEffectFlag("canAttackAgain")) {
            logger.info("{} attacks again due to an effect!", attacker.getDefinition().getName());
            attacker.removeEffectFlag("canAttackAgain");
        } else {
            attackingPlayer.incrementAttacksDeclaredThisTurn();
        }
        attacker.setExhausted(true);

        checkAllFieldsForDestroyedCards();
        return null;
    }

    public String playerActivatesAbility(Player activatingPlayer, String sourceCardInstanceId,
            String targetCardInstanceId, Integer abilityOptionIndex) {
        if (gameState.name().contains("GAME_OVER"))
            return "Game is already over.";
        if (activatingPlayer != currentPlayer || !isPlayerTurnState(activatingPlayer))
            return "Not your turn.";

        CardInstance sourceCard = findCardInstanceFromAnyField(sourceCardInstanceId);
        if (sourceCard == null || getOwnerOfCardInstance(sourceCard) != activatingPlayer) {
            return "Source card for ability not found on your field.";
        }

        // Enforce Silence status
        if (sourceCard.getBooleanEffectFlag("status_silenced")) {
            return sourceCard.getDefinition().getName() + " is Silenced and cannot use abilities.";
        }

        Map<String, Object> context = new HashMap<>();
        context.put("abilityOptionIndex", abilityOptionIndex);
        context.put("targetCardInstanceId", targetCardInstanceId); // TargetResolver will use this

        // The EffectProcessor will now handle all logic including "once per turn"
        // checks.
        effectProcessor.processTrigger(EffectTrigger.ACTIVATED, sourceCard, activatingPlayer, context);
        checkAllFieldsForDestroyedCards();

        // We can return a generic success message, and let the processor's logging
        // handle details.
        // A more advanced implementation might have the processor return a result
        // object.
        return sourceCard.getDefinition().getName() + " attempted to activate an ability.";
    }

    public CardInstance findCardInstanceFromAnyField(String instanceId) {
        if (instanceId == null)
            return null;
        for (Player player : List.of(player1, player2)) {
            if (player != null && player.getFieldInternal() != null) {
                for (CardInstance card : player.getFieldInternal()) {
                    if (card != null && card.getInstanceId().equals(instanceId)) {
                        return card;
                    }
                }
            }
        }
        return null;
    }

    public void checkAllFieldsForDestroyedCards() {
        boolean cardDestroyedThisPass;
        int iterationGuard = 0;
        do {
            cardDestroyedThisPass = false;
            iterationGuard++;
            if (iterationGuard > 10) {
                logger.error("checkAllFieldsForDestroyedCards looped too many times. Breaking.");
                break;
            }

            List<CardInstance> allCardsOnField = new ArrayList<>();
            if (player1 != null)
                allCardsOnField.addAll(player1.getFieldInternal());
            if (player2 != null)
                allCardsOnField.addAll(player2.getFieldInternal());

            for (CardInstance card : allCardsOnField) {
                if (card != null && card.isDestroyed()) {
                    // Check for 98pm's delayed death
                    if (card.getBooleanEffectFlag("survives_until_next_check")) {
                        card.removeEffectFlag("survives_until_next_check");
                        logger.info("98pm's delayed death effect wore off. It is now destroyed.");
                    } else if ("98PM_DELAYED_DEATH".equals(card.getDefinition().getEffectConfiguration())) {
                        card.setEffectFlag("survives_until_next_check", true);
                        logger.info("98pm reached 0 life, but will survive until the next death check.");
                        continue; // Skip destruction this pass
                    }

                    Player owner = getOwnerOfCardInstance(card);
                    if (owner == null)
                        continue;
                    int fieldIndex = owner.getFieldInternal().indexOf(card);
                    if (fieldIndex == -1)
                        continue; // Already processed

                    CardInstance removedCard = owner.removeCardFromField(fieldIndex);
                    if (removedCard == null)
                        continue;

                    logger.info("{}'s {} ({}) was destroyed.", owner.getDisplayName(),
                            removedCard.getDefinition().getName(), removedCard.getInstanceId());

                    Map<String, Object> deathContext = new HashMap<>();
                    deathContext.put("lastDamageSource", removedCard.getLastDamageSourceCard());
                    deathContext.put("eventTarget", removedCard);

                    // Trigger for the card that died
                    effectProcessor.processTrigger(EffectTrigger.ON_DEATH, removedCard, owner, deathContext);

                    // Trigger for all other cards on the field about this death
                    for (Player p : List.of(player1, player2)) {
                        if (p == null)
                            continue;
                        for (CardInstance listenerCard : p.getFieldInternal()) {
                            if (listenerCard != null
                                    && !listenerCard.getInstanceId().equals(removedCard.getInstanceId())) {
                                // deathContext already contains the card that died as "eventTarget"
                                effectProcessor.processTrigger(EffectTrigger.ON_DEATH_OF_ANY, listenerCard, p,
                                        deathContext);
                            }
                        }
                    }

                    if (removedCard.getBooleanEffectFlag("revive_next_turn")) {
                        if (owner == player1)
                            graveyardPlayer1.add(removedCard);
                        else
                            graveyardPlayer2.add(removedCard);
                        logger.info("{} will attempt to return.", removedCard.getDefinition().getName());
                    } else {
                        owner.addCardToDiscardPile(removedCard);
                    }
                    cardDestroyedThisPass = true;
                }
            }

            if (cardDestroyedThisPass) {
                processAllAuras(); // Re-check auras after cards have been removed
            }

        } while (cardDestroyedThisPass && !gameState.name().contains("GAME_OVER"));
    }

    public Player getOwnerOfCardInstance(CardInstance cardInstance) {
        if (cardInstance == null)
            return null;
        if (player1 != null && player1.getFieldInternal().stream()
                .anyMatch(c -> c != null && c.getInstanceId().equals(cardInstance.getInstanceId())))
            return player1;
        if (player2 != null && player2.getFieldInternal().stream()
                .anyMatch(c -> c != null && c.getInstanceId().equals(cardInstance.getInstanceId())))
            return player2;
        return null;
    }

    private boolean isPlayerTurnState(Player player) {
        return (player == player1 && gameState == GameState.PLAYER_1_TURN) ||
                (player == player2 && gameState == GameState.PLAYER_2_TURN);
    }

    public String getGameId() {
        return gameId;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public GameState getGameState() {
        return gameState;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public Player getOpponent(Player player) {
        if (player == player1)
            return player2;
        if (player == player2)
            return player1;
        return null;
    }
}