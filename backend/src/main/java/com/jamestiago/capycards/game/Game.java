package com.jamestiago.capycards.game;

import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.game.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.AbstractMap;
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
    // A card in limbo now stores its owner's ID
    private final Map<String, Map.Entry<CardInstance, String>> cardsInLimbo = new ConcurrentHashMap<>();

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

        // Deep copy limbo
        other.cardsInLimbo.forEach((id, entry) -> {
            CardInstance copiedCard = new CardInstance(entry.getKey());
            String ownerId = entry.getValue();
            this.cardsInLimbo.put(id, new AbstractMap.SimpleEntry<>(copiedCard, ownerId));
        });

        this.player1 = new Player(other.player1);
        this.player2 = new Player(other.player2);

        if (other.currentPlayer != null) {
            this.currentPlayer = (other.currentPlayer.getPlayerId().equals(this.player1.getPlayerId()))
                    ? this.player1
                    : this.player2;
        } else {
            this.currentPlayer = null;
        }

        this.gameFlags.putAll(other.gameFlags);

        fixupCardReferences();

        logger.trace("[{}] Cloned game instance for simulation.", this.gameId);
    }

    private void fixupCardReferences() {
        // Create a map of all old instance IDs to new card instances
        Map<String, CardInstance> allNewCards = new ConcurrentHashMap<>();
        player1.getFieldInternal().forEach(c -> {
            if (c != null)
                allNewCards.put(c.getInstanceId(), c);
        });
        player2.getFieldInternal().forEach(c -> {
            if (c != null)
                allNewCards.put(c.getInstanceId(), c);
        });
        cardsInLimbo.values().forEach(entry -> allNewCards.put(entry.getKey().getInstanceId(), entry.getKey()));

        // Iterate through all new cards and update their lastDamageSourceCard reference
        allNewCards.values().forEach(card -> {
            CardInstance oldSource = card.getLastDamageSourceCard();
            if (oldSource != null) {
                CardInstance newSource = allNewCards.get(oldSource.getInstanceId());
                card.setLastDamageSourceCard(newSource); // Update the reference
            }
        });
    }

    public void addCardToLimbo(CardInstance card, String ownerId) {
        if (card != null && ownerId != null) {
            this.cardsInLimbo.put(card.getInstanceId(), new AbstractMap.SimpleEntry<>(card, ownerId));
        }
    }

    public CardInstance removeCardFromLimbo(String instanceId) {
        Map.Entry<CardInstance, String> entry = this.cardsInLimbo.remove(instanceId);
        return (entry != null) ? entry.getKey() : null;
    }

    // The core method for mutating game state. It trusts the event completely.
    public void apply(GameEvent event) {
        logger.trace("[{}] APPLYING event: {} | Content: {}", gameId, event.getClass().getSimpleName(),
                event.toString());
        if (event instanceof GameStartedEvent e) {
            applyGameStarted(e);
        } else if (event instanceof TurnStartedEvent e) {
            applyTurnStarted(e);
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
        } else if (event instanceof CardHealedEvent e) {
            applyCardHealed(e);
        } else if (event instanceof CardBuffedEvent e) {
            applyCardBuffed(e);
        } else if (event instanceof CardDebuffedEvent e) {
            applyCardDebuffed(e);
        } else if (event instanceof CardStatSetEvent e) {
            applyCardStatSet(e);
        } else if (event instanceof CardFlagChangedEvent e) {
            applyCardFlagChanged(e);
        } else if (event instanceof CardTransformedEvent e) {
            applyCardTransformed(e);
        } else if (event instanceof CardVanishedEvent e) {
            applyCardVanished(e);
        } else if (event instanceof CardReappearedEvent e) {
            applyCardReappeared(e);
        } else if (event instanceof CardAddedToDeckEvent e) {
            applyCardAddedToDeck(e);
        }
        updateInternalGameState();
        logger.trace("[{}] FINISHED applying event: {}. Current turn: {}, Current player: {}, Game state: {}", gameId,
                event.getClass().getSimpleName(), this.turnNumber,
                this.currentPlayer != null ? this.currentPlayer.getDisplayName() : "None", this.gameState);
    }

    private void applyCardVanished(CardVanishedEvent event) {
        Player owner = getPlayerById(event.ownerPlayerId);
        if (owner == null)
            return;

        CardInstance card = owner.removeCardFromFieldByInstanceId(event.instanceId, false); // Don't add to discard
        if (card != null) {
            addCardToLimbo(card, owner.getPlayerId());
        }
    }

    private void applyCardReappeared(CardReappearedEvent event) {
        Player owner = getPlayerById(event.ownerPlayerId);
        if (owner == null)
            return;

        CardInstance card = removeCardFromLimbo(event.card.getInstanceId());
        if (card != null) {
            card.setExhausted(true);
            card.resetTurnSpecificState();
            owner.getFieldInternal().set(event.toFieldSlot, card);
        }
    }

    private void applyGameStarted(GameStartedEvent event) {
        this.turnNumber = 1;
        this.currentPlayer = getPlayerById(event.startingPlayerId);
        if (this.currentPlayer != null) {
            this.currentPlayer.initialDraw(Player.MAX_HAND_SIZE);
            getOpponent(this.currentPlayer).initialDraw(Player.MAX_HAND_SIZE - 1);
            this.gameState = this.currentPlayer == player1 ? GameState.PLAYER_1_TURN : GameState.PLAYER_2_TURN;
        } else {
            logger.error("Could not find starting player with ID {}", event.startingPlayerId);
        }
    }

    private void applyTurnStarted(TurnStartedEvent event) {
        this.turnNumber = event.newTurnNumber;
        this.currentPlayer = getPlayerById(event.newTurnPlayerId);
        if (this.currentPlayer != null) {
            this.currentPlayer.startOfTurnReset();
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
                card.setExhausted(true);
            }
        }
    }

    private void applyAttackDeclared(AttackDeclaredEvent event) {
        Player attackerPlayer = getPlayerById(event.attackerPlayerId);
        if (attackerPlayer != null) {
            attackerPlayer.incrementAttacksDeclaredThisTurn();
            CardInstance attackerCard = findCardInstanceFromAnyField(event.attackerInstanceId);
            if (attackerCard != null) {
                attackerCard.setExhausted(true);
            }
        }
    }

    private void applyCombatDamageDealt(CombatDamageDealtEvent event) {
        CardInstance defender = findCardInstanceFromAnyField(event.defenderInstanceId);
        CardInstance attacker = findCardInstanceFromAnyField(event.attackerInstanceId);
        if (defender != null) {
            defender.setCurrentLife(event.defenderLifeAfter);
            defender.setLastDamageSourceCard(attacker);
        }
    }

    private void applyCardHealed(CardHealedEvent event) {
        CardInstance card = findCardInstanceAnywhere(event.targetInstanceId);
        if (card != null) {
            card.setCurrentLife(event.lifeAfter);
        }
    }

    private void applyCardDestroyed(CardDestroyedEvent event) {
        Player owner = getPlayerById(event.ownerPlayerId);
        if (owner != null) {
            owner.removeCardFromFieldByInstanceId(event.card.getInstanceId(), true); // Add to discard
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
        CardInstance card = findCardInstanceAnywhere(event.targetInstanceId);
        if (card == null)
            return;

        if (event.isPermanent) {
            switch (event.stat.toUpperCase()) {
                case "ATK" -> card.setBaseAttack(card.getBaseAttack() + event.amount);
                case "DEF" -> card.setBaseDefense(card.getBaseDefense() + event.amount);
                case "MAX_LIFE" -> {
                    card.setBaseLife(card.getBaseLife() + event.amount);
                    card.heal(event.amount);
                }
            }
        } else {
            card.addTemporaryBuff(event.stat, event.amount);
        }
    }

    private void applyCardDebuffed(CardDebuffedEvent event) {
        CardInstance card = findCardInstanceAnywhere(event.targetInstanceId);
        if (card == null)
            return;
        int negativeAmount = -Math.abs(event.amount);
        if (event.isPermanent) {
            switch (event.stat.toUpperCase()) {
                case "ATK" -> card.setBaseAttack(card.getBaseAttack() + negativeAmount);
                case "DEF" -> card.setBaseDefense(card.getBaseDefense() + negativeAmount);
                case "MAX_LIFE" -> card.setBaseLife(card.getBaseLife() + negativeAmount);
            }
        } else {
            card.addTemporaryBuff(event.stat, negativeAmount);
        }
    }

    private void applyCardStatSet(CardStatSetEvent event) {
        CardInstance card = findCardInstanceAnywhere(event.targetInstanceId);
        if (card == null)
            return;
        switch (event.stat.toUpperCase()) {
            case "ATK" -> card.setBaseAttack(event.value);
            case "DEF" -> card.setBaseDefense(event.value);
            case "MAX_LIFE" -> card.setBaseLife(event.value);
            case "LIFE" -> card.setCurrentLife(event.value);
        }
    }

    private void applyCardFlagChanged(CardFlagChangedEvent event) {
        CardInstance card = findCardInstanceAnywhere(event.targetInstanceId);
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
        Card newCardDefinition = allCardDefinitions.get(event.newCardDto.getCardId());
        if (newCardDefinition == null) {
            logger.error("TRANSFORM FAILED: Card definition for '{}' not found.", event.newCardDto.getCardId());
            return;
        }

        int slotIndex = -1;
        for (int i = 0; i < owner.getFieldInternal().size(); i++) {
            CardInstance card = owner.getFieldInternal().get(i);
            if (card != null && card.getInstanceId().equals(event.originalInstanceId)) {
                slotIndex = i;
                break;
            }
        }

        if (slotIndex != -1) {
            CardInstance newCardState = new CardInstance(newCardDefinition);

            // Use life from event DTO if present, otherwise use definition's initial life
            Integer lifeFromEvent = event.newCardDto.getCurrentLife();
            if (lifeFromEvent != null && lifeFromEvent > 0) {
                newCardState.setCurrentLife(lifeFromEvent);
            } else {
                newCardState.setCurrentLife(newCardDefinition.getInitialLife());
            }

            newCardState.setExhausted(true);
            owner.getFieldInternal().set(slotIndex, newCardState);
        }
    }

    private void applyCardAddedToDeck(CardAddedToDeckEvent event) {
        Player p = getPlayerById(event.playerId);
        if (p == null || event.card == null) {
            return;
        }
        Card definition = allCardDefinitions.get(event.card.getCardId());
        if (definition != null) {
            CardInstance newCard = new CardInstance(definition);
            newCard.setInstanceId(event.card.getInstanceId()); // Ensure consistent ID

            switch (event.placement.toUpperCase()) {
                case "TOP":
                    p.getDeck().addCardToTop(newCard);
                    break;
                case "BOTTOM":
                    p.getDeck().addCardToBottom(newCard);
                    break;
                case "SHUFFLE":
                default:
                    p.getDeck().addCardToBottom(newCard);
                    p.getDeck().shuffle();
                    break;
            }
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

    public Map<String, Map.Entry<CardInstance, String>> getCardsInLimbo() {
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

    public CardInstance findCardInstanceAnywhere(String instanceId) {
        if (instanceId == null)
            return null;
        // Check fields first
        CardInstance card = findCardInstanceFromAnyField(instanceId);
        if (card != null)
            return card;

        // Check all players' hands and decks
        for (Player p : List.of(player1, player2)) {
            if (p != null) {
                for (CardInstance c : p.getHandInternal()) {
                    if (c != null && c.getInstanceId().equals(instanceId))
                        return c;
                }
                for (CardInstance c : p.getDeck().getCards()) {
                    if (c != null && c.getInstanceId().equals(instanceId))
                        return c;
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

        if (player1 != null
                && player1.getFieldInternal().stream().anyMatch(c -> c != null && c.getInstanceId().equals(instanceId)))
            return player1;
        if (player2 != null
                && player2.getFieldInternal().stream().anyMatch(c -> c != null && c.getInstanceId().equals(instanceId)))
            return player2;

        // Check limbo
        Map.Entry<CardInstance, String> limboEntry = cardsInLimbo.get(instanceId);
        if (limboEntry != null) {
            return getPlayerById(limboEntry.getValue());
        }

        return null;
    }

    public Player getOpponent(Player player) {
        if (player == null)
            return null;
        return player == player1 ? player2 : player1;
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

    public Map<String, Object> getGameFlags() {
        return gameFlags;
    }

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