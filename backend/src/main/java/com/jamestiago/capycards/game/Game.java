package com.jamestiago.capycards.game;

import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.game.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Game {
    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private final String gameId;
    private Player player1;
    private Player player2;
    private Player currentPlayer;
    private GameState gameState;
    private int turnNumber;
    private final Map<String, Object> gameFlags = new ConcurrentHashMap<>();
    private transient final Map<String, Card> allCardDefinitions;

    private final Map<String, CardInstance> cardsInLimbo = new ConcurrentHashMap<>();

    public enum GameState {
        WAITING_FOR_PLAYERS,
        INITIAL_DRAW,
        PLAYER_1_TURN,
        PLAYER_2_TURN,
        GAME_OVER_PLAYER_1_WINS,
        GAME_OVER_PLAYER_2_WINS,
        GAME_OVER_DRAW
    }

    /**
     * Original constructor for creating a new game instance.
     */
    public Game(Player p1, Player p2, List<Card> allCardDefinitions) {
        this.gameId = UUID.randomUUID().toString();
        this.player1 = p1;
        this.player2 = p2;
        this.gameState = GameState.WAITING_FOR_PLAYERS;
        this.turnNumber = 0;
        if (allCardDefinitions != null) {
            this.allCardDefinitions = allCardDefinitions.stream()
                    .collect(Collectors.toConcurrentMap(Card::getCardId, Function.identity()));
        } else {
            this.allCardDefinitions = new ConcurrentHashMap<>();
        }
        logger.trace("[{}] New Game instance created. P1: {}, P2: {}", gameId, p1.getDisplayName(),
                p2.getDisplayName());
    }

    /**
     * Copy constructor for creating a deep copy for simulations.
     */
    public Game(Game other) {
        this.gameId = other.gameId;
        this.turnNumber = other.turnNumber;
        this.gameState = other.gameState;
        this.allCardDefinitions = other.allCardDefinitions;

        other.cardsInLimbo.forEach((id, card) -> this.cardsInLimbo.put(id, new CardInstance(card)));

        // Use the Player copy constructor
        this.player1 = new Player(other.player1);
        this.player2 = new Player(other.player2);

        // Ensure currentPlayer reference points to the new copied player object
        if (other.currentPlayer != null) {
            this.currentPlayer = (other.currentPlayer.getPlayerId().equals(this.player1.getPlayerId()))
                    ? this.player1
                    : this.player2;
        } else {
            this.currentPlayer = null;
        }

        // Deep copy game flags
        this.gameFlags.putAll(other.gameFlags);

        logger.trace("[{}] Cloned game instance for simulation.", this.gameId);
    }

    public void addCardToLimbo(CardInstance card) {
        if (card != null) {
            this.cardsInLimbo.put(card.getInstanceId(), card);
        }
    }

    public CardInstance removeCardFromLimbo(String instanceId) {
        return this.cardsInLimbo.remove(instanceId);
    }

    // The core method for mutating game state. It trusts the event completely.
    public void apply(GameEvent event) {
        logger.trace("[{}] APPLYING event: {} | Content: {}", gameId, event.getClass().getSimpleName(),
                event.toString());
        if (event instanceof GameStartedEvent e) {
            applyGameStarted(e);
        } else if (event instanceof TurnStartedEvent e) {
            applyTurnStarted(e);
        } else if (event instanceof CardDrawnEvent e) {
            applyCardDrawn(e);
        } else if (event instanceof PlayerDrewCardEvent e) {
            applyPlayerDrewCard(e);
        } else if (event instanceof CardPlayedEvent e) {
            applyCardPlayed(e);
        } else if (event instanceof AttackDeclaredEvent e) {
            applyAttackDeclared(e);
        } else if (event instanceof CombatDamageDealtEvent e) {
            applyCombatDamageDealt(e);
        } else if (event instanceof CardDestroyedEvent e) {
            applyCardDestroyed(e);
        } else if (event instanceof TurnEndedEvent e) {
            applyTurnEnded(e);
        } else if (event instanceof GameOverEvent e) {
            applyGameOver(e);
        } else if (event instanceof CardStatsChangedEvent e) {
            applyCardStatsChanged(e);
        } else if (event instanceof CardHealedEvent e) {
            applyCardHealed(e);
        } else if (event instanceof CardBuffedEvent e) {
            applyCardBuffed(e);
        } else if (event instanceof CardDebuffedEvent e) {
            applyCardDebuffed(e);
        } else if (event instanceof CardFlagChangedEvent e) {
            applyCardFlagChanged(e);
        } else if (event instanceof CardTransformedEvent e) {
            applyCardTransformed(e);
        } else if (event instanceof CardVanishedEvent e) {
            applyCardVanished(e);
        } else if (event instanceof CardReappearedEvent e) {
            applyCardReappeared(e);
        }
        // Events like GameLogMessageEvent or AbilityActivatedEvent don't mutate state,
        // so no handler needed.
        updateInternalGameState();
        logger.trace("[{}] FINISHED applying event: {}. Current turn: {}, Current player: {}, Game state: {}", gameId,
                event.getClass().getSimpleName(), this.turnNumber,
                this.currentPlayer != null ? this.currentPlayer.getDisplayName() : "None", this.gameState);
    }

    private void applyCardVanished(CardVanishedEvent event) {
        Player owner = getPlayerById(event.ownerPlayerId);
        if (owner == null)
            return;

        CardInstance card = owner.removeCardFromFieldByInstanceId(event.instanceId);
        if (card != null) {
            // We move it to limbo instead of the discard pile.
            addCardToLimbo(card);
        }
    }

    private void applyCardReappeared(CardReappearedEvent event) {
        Player owner = getPlayerById(event.ownerPlayerId);
        if (owner == null)
            return;

        CardInstance card = removeCardFromLimbo(event.card.getInstanceId());
        if (card != null) {
            // Refresh its state and place it on the field
            card.setExhausted(true);
            card.resetTurnSpecificState();
            owner.getFieldInternal().set(event.toFieldSlot, card);
        }
    }

    private void applyGameStarted(GameStartedEvent event) {
        this.turnNumber = 1;
        this.currentPlayer = getPlayerById(event.startingPlayerId);
        if (this.currentPlayer != null) {
            // The initial draw is now part of the game logic, not the service.
            this.currentPlayer.initialDraw(Player.MAX_HAND_SIZE);
            getOpponent(this.currentPlayer).initialDraw(Player.MAX_HAND_SIZE);
            this.gameState = this.currentPlayer == player1 ? GameState.PLAYER_1_TURN : GameState.PLAYER_2_TURN;
        } else {
            logger.error("Could not find starting player with ID {}", event.startingPlayerId);
        }
    }

    private void applyTurnStarted(TurnStartedEvent event) {
        this.turnNumber = event.newTurnNumber;
        this.currentPlayer = getPlayerById(event.newTurnPlayerId);
        if (this.currentPlayer != null) {
            this.currentPlayer.resetTurnActions();
        }
    }

    private void applyCardDrawn(CardDrawnEvent event) {
        Player p = getPlayerById(event.playerId);
        if (p != null) {
            p.drawCard();
        }
    }

    private void applyPlayerDrewCard(PlayerDrewCardEvent event) {
        Player p = getPlayerById(event.playerId);
        if (p != null) {
            p.drawCard();
        }
    }

    private void applyCardPlayed(CardPlayedEvent event) {
        Player p = getPlayerById(event.playerId);
        if (p != null) {
            CardInstance card = p.playCardFromHandToField(event.fromHandIndex, event.toFieldSlot);
            if (card != null) {
                // Summoning sickness
                card.setExhausted(true);
            }
        }
    }

    private void applyAttackDeclared(AttackDeclaredEvent event) {
        Player attackerPlayer = getPlayerById(event.attackerPlayerId);
        if (attackerPlayer != null) {
            // Correctly increment the counter here
            attackerPlayer.incrementAttacksDeclaredThisTurn();

            CardInstance attackerCard = findCardInstanceFromAnyField(event.attackerInstanceId);
            if (attackerCard != null) {
                // Correctly exhaust the card here
                attackerCard.setExhausted(true);
            }
        }
    }

    private void applyCombatDamageDealt(CombatDamageDealtEvent event) {
        CardInstance defender = findCardInstanceFromAnyField(event.defenderInstanceId);
        if (defender != null) {
            // The event contains the final life total, so we just set it.
            defender.setCurrentLife(event.defenderLifeAfter);
        }
    }

    private void applyCardStatsChanged(CardStatsChangedEvent event) {
        CardInstance card = findCardInstanceFromAnyField(event.targetInstanceId);
        if (card != null) {
            // Note: This applies the final calculated stat. It doesn't re-run the logic.
            // This is correct for this architecture.
            card.setBaseAttack(event.newAttack);
            card.setBaseDefense(event.newDefense);
            card.setCurrentLife(event.newLife);
        }
    }

    private void applyCardHealed(CardHealedEvent event) {
        CardInstance card = findCardInstanceFromAnyField(event.targetInstanceId);
        if (card != null) {
            // The event now contains the correct final life total.
            card.setCurrentLife(event.lifeAfter);
        }
    }

    private void applyCardDestroyed(CardDestroyedEvent event) {
        Player owner = getPlayerById(event.ownerPlayerId);
        if (owner != null) {
            owner.removeCardFromFieldByInstanceId(event.card.getInstanceId());
        }
    }

    private void applyTurnEnded(TurnEndedEvent event) {
        Player p = getPlayerById(event.endedTurnPlayerId);
        if (p != null) {
            p.discardDownToMaxHandSize();
        }
    }

    private void applyGameOver(GameOverEvent event) {
        if (event.winnerPlayerId == null) {
            this.gameState = GameState.GAME_OVER_DRAW;
        } else if (player1 != null && event.winnerPlayerId.equals(player1.getPlayerId())) {
            this.gameState = GameState.GAME_OVER_PLAYER_1_WINS;
        } else if (player2 != null && event.winnerPlayerId.equals(player2.getPlayerId())) {
            this.gameState = GameState.GAME_OVER_PLAYER_2_WINS;
        } else {
            this.gameState = GameState.GAME_OVER_DRAW;
            logger.warn("GameOverEvent with unknown winnerId: {}", event.winnerPlayerId);
        }
    }

    private void applyCardBuffed(CardBuffedEvent event) {
        CardInstance card = findCardInstanceFromAnyField(event.targetInstanceId);
        if (card == null)
            return;

        if (event.isPermanent) {
            switch (event.stat.toUpperCase()) {
                case "ATK" -> card.setBaseAttack(card.getDefinition().getAttack() + event.amount);
                case "DEF" -> card.setBaseDefense(card.getDefinition().getDefense() + event.amount);
                case "MAX_LIFE" -> {
                    card.setBaseLife(card.getDefinition().getInitialLife() + event.amount);
                    card.heal(event.amount); // Also heal for the amount of max life gained
                }
            }
        } else {
            card.addTemporaryBuff(event.stat, event.amount);
        }
    }

    private void applyCardDebuffed(CardDebuffedEvent event) {
        CardInstance card = findCardInstanceFromAnyField(event.targetInstanceId);
        if (card == null)
            return;

        // Treat debuffs as negative buffs
        int negativeAmount = -Math.abs(event.amount);
        if (event.isPermanent) {
            switch (event.stat.toUpperCase()) {
                case "ATK" -> card.setBaseAttack(card.getDefinition().getAttack() + negativeAmount);
                case "DEF" -> card.setBaseDefense(card.getDefinition().getDefense() + negativeAmount);
                case "MAX_LIFE" -> card.setBaseLife(card.getDefinition().getInitialLife() + negativeAmount);
            }
        } else {
            card.addTemporaryBuff(event.stat, negativeAmount);
        }
    }

    private void applyCardFlagChanged(CardFlagChangedEvent event) {
        CardInstance card = findCardInstanceFromAnyField(event.targetInstanceId);
        if (card != null) {
            if (event.value == null) {
                card.removeEffectFlag(event.flagName);
            } else {
                card.setEffectFlag(event.flagName, event.value);
            }
        }
    }

    private void applyCardTransformed(CardTransformedEvent event) {
        CardInstance originalCard = findCardInstanceFromAnyField(event.originalInstanceId);
        if (originalCard == null)
            return;

        Player owner = getOwnerOfCardInstance(originalCard);
        if (owner == null)
            return;

        // Find the full definition of the new card form
        Card newCardDefinition = allCardDefinitions.get(event.newCardDto.getCardId());
        if (newCardDefinition == null) {
            logger.error("TRANSFORM FAILED: Card definition for '{}' not found.", event.newCardDto.getCardId());
            return;
        }

        // Find the slot of the original card
        int slotIndex = -1;
        for (int i = 0; i < owner.getFieldInternal().size(); i++) {
            CardInstance card = owner.getFieldInternal().get(i);
            if (card != null && card.getInstanceId().equals(event.originalInstanceId)) {
                slotIndex = i;
                break;
            }
        }

        if (slotIndex != -1) {
            // Create a new CardInstance from the DEFINITION
            CardInstance newCardState = new CardInstance(newCardDefinition);

            // Carry over relevant state from the DTO in the event
            // This allows effects to set the transformed card's starting health
            newCardState.setCurrentLife(event.newCardDto.getCurrentLife());
            newCardState.setExhausted(true); // Transformed cards usually suffer from summoning sickness

            // Overwrite the card in the field slot
            owner.getFieldInternal().set(slotIndex, newCardState);
        }
    }

    private void updateInternalGameState() {
        if (this.gameState.name().contains("GAME_OVER"))
            return;

        if (this.currentPlayer == player1) {
            this.gameState = GameState.PLAYER_1_TURN;
        } else if (this.currentPlayer == player2) {
            this.gameState = GameState.PLAYER_2_TURN;
        }
    }

    // --- UTILITY METHODS ---

    // <<< ADD THIS GETTER METHOD >>>
    public Map<String, CardInstance> getCardsInLimbo() {
        return cardsInLimbo;
    }

    public CardInstance findCardInstanceFromAnyField(String instanceId) {
        if (instanceId == null)
            return null;
        for (Player player : List.of(player1, player2)) {
            if (player != null && player.getField() != null) {
                for (CardInstance card : player.getField()) {
                    if (card != null && card.getInstanceId().equals(instanceId)) {
                        return card;
                    }
                }
            }
        }
        return null;
    }

    public Player getPlayerById(String playerId) {
        if (player1 != null && player1.getPlayerId().equals(playerId))
            return player1;
        if (player2 != null && player2.getPlayerId().equals(playerId))
            return player2;
        return null;
    }

    public Player getOwnerOfCardInstance(CardInstance cardInstance) {
        if (cardInstance == null)
            return null;
        String instanceId = cardInstance.getInstanceId();
        if (player1 != null && player1.getFieldInternal().stream()
                .anyMatch(c -> c != null && c.getInstanceId().equals(instanceId)))
            return player1;
        if (player2 != null && player2.getFieldInternal().stream()
                .anyMatch(c -> c != null && c.getInstanceId().equals(instanceId)))
            return player2;
        return null;
    }

    public Player getOpponent(Player player) {
        if (player == null)
            return null;
        if (player == player1)
            return player2;
        if (player == player2)
            return player1;
        return null;
    }

    // Getters
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

    public Map<String, Object> getGameFlags() {
        return gameFlags;
    }

    // Setters used for initialization by GameService before the game starts
    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void setCurrentPlayer(Player currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }
}