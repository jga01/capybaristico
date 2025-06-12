package com.jamestiago.capycards.game;

import com.jamestiago.capycards.model.Card;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class Player {
    private final String playerId;
    private String displayName;
    private Deck deck;
    private List<CardInstance> hand;
    private List<CardInstance> field;
    private List<CardInstance> discardPile;
    public static final int MAX_HAND_SIZE = 5;
    public static final int MAX_FIELD_SIZE = 4;
    public static final int MAX_ATTACKS_PER_TURN = 2;

    private int attacksDeclaredThisTurn = 0;

    public enum DrawResult {
        SUCCESS,
        HAND_FULL,
        DECK_EMPTY
    }

    public record DrawOutcome(DrawResult result, CardInstance cardDrawn) {
    }

    /**
     * Original constructor for creating a new player at the start of a game.
     */
    public Player(String displayName, List<Card> cardDefinitionsForDeck) {
        this.playerId = UUID.randomUUID().toString();
        this.displayName = displayName;

        List<CardInstance> deckCards = new ArrayList<>();
        for (Card cardDef : cardDefinitionsForDeck) {
            deckCards.add(new CardInstance(cardDef));
        }
        this.deck = new Deck(deckCards);
        this.deck.shuffle();

        this.hand = new ArrayList<>();
        this.field = new ArrayList<>(Collections.nCopies(MAX_FIELD_SIZE, null));
        this.discardPile = new ArrayList<>();
    }

    /**
     * Copy constructor for creating a deep copy for simulations.
     */
    public Player(Player other) {
        this.playerId = other.playerId;
        this.displayName = other.displayName;
        this.attacksDeclaredThisTurn = other.attacksDeclaredThisTurn;

        // Deep copy mutable collections
        this.deck = new Deck(other.deck); // Assumes Deck has a copy constructor
        this.hand = other.hand.stream().map(CardInstance::new).collect(Collectors.toList());
        this.field = other.field.stream().map(c -> c != null ? new CardInstance(c) : null)
                .collect(Collectors.toList());
        this.discardPile = other.discardPile.stream().map(CardInstance::new).collect(Collectors.toList());
    }

    /**
     * Method to identify if a player is controlled by AI.
     * Overridden by the AIPlayer subclass.
     * 
     * @return false for a human player.
     */
    public boolean isAi() {
        return false;
    }

    /**
     * Draws a card and returns an object detailing the outcome.
     * 
     * @return DrawOutcome object with the result and the card instance.
     */
    public DrawOutcome drawCardWithOutcome() {
        if (deck.isEmpty()) {
            return new DrawOutcome(DrawResult.DECK_EMPTY, null);
        }
        CardInstance drawnCard = deck.draw();
        if (drawnCard != null && hand.size() < MAX_HAND_SIZE) {
            hand.add(drawnCard);
            return new DrawOutcome(DrawResult.SUCCESS, drawnCard);
        } else if (drawnCard != null) {
            discardPile.add(drawnCard); // Discard if hand is full
            return new DrawOutcome(DrawResult.HAND_FULL, drawnCard);
        }
        // This case should not be reached if deck is not empty, but as a fallback:
        return new DrawOutcome(DrawResult.DECK_EMPTY, null);
    }

    // Returns the drawn card, or null if deck is empty
    public CardInstance drawCard() {
        if (deck.isEmpty()) {
            return null;
        }
        CardInstance drawnCard = deck.draw();
        if (drawnCard != null && hand.size() < MAX_HAND_SIZE) {
            hand.add(drawnCard);
        } else if (drawnCard != null) {
            discardPile.add(drawnCard); // Discard if hand is full
        }
        return drawnCard;
    }

    public void initialDraw(int targetHandSize) {
        for (int i = 0; i < targetHandSize; i++) {
            if (deck.isEmpty())
                break;
            drawCard();
        }
    }

    // Returns the played card instance for event generation
    public CardInstance playCardFromHandToField(int handIndex, int fieldSlotIndex) {
        if (handIndex < 0 || handIndex >= hand.size())
            return null;
        if (fieldSlotIndex < 0 || fieldSlotIndex >= MAX_FIELD_SIZE)
            return null;
        if (field.get(fieldSlotIndex) != null)
            return null;

        CardInstance cardToPlay = hand.remove(handIndex);
        field.set(fieldSlotIndex, cardToPlay);
        return cardToPlay;
    }

    public void discardDownToMaxHandSize() {
        while (hand.size() > MAX_HAND_SIZE) {
            if (!hand.isEmpty()) {
                CardInstance cardToDiscard = hand.remove(hand.size() - 1);
                discardPile.add(cardToDiscard);
            } else {
                break;
            }
        }
    }

    public int getAttacksDeclaredThisTurn() {
        return attacksDeclaredThisTurn;
    }

    public boolean canDeclareAttack() {
        return attacksDeclaredThisTurn < MAX_ATTACKS_PER_TURN;
    }

    public void incrementAttacksDeclaredThisTurn() {
        this.attacksDeclaredThisTurn++;
    }

    public void startOfTurnReset() {
        this.attacksDeclaredThisTurn = 0;
        for (CardInstance card : field) {
            if (card != null) {
                card.setExhausted(false);
                card.resetTurnSpecificState();
            }
        }
    }

    public CardInstance removeCardFromFieldByInstanceId(String instanceId, boolean addToDiscard) {
        for (int i = 0; i < field.size(); i++) {
            CardInstance card = field.get(i);
            if (card != null && card.getInstanceId().equals(instanceId)) {
                field.set(i, null);
                if (addToDiscard) {
                    discardPile.add(card);
                }
                return card;
            }
        }
        return null;
    }

    // Getters
    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Deck getDeck() {
        return deck;
    }

    public List<CardInstance> getHand() {
        return Collections.unmodifiableList(hand);
    }

    public List<CardInstance> getField() {
        return Collections.unmodifiableList(field);
    }

    public List<CardInstance> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    // Internal mutable getters for engine/game state manipulation.
    public List<CardInstance> getHandInternal() {
        return this.hand;
    }

    public List<CardInstance> getFieldInternal() {
        return this.field;
    }

    @Override
    public String toString() {
        return "Player{" +
                "playerId='" + playerId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", handSize=" + hand.size() +
                ", fieldSize=" + field.stream().filter(Objects::nonNull).count() +
                ", deckSize=" + deck.size() +
                ", discardSize=" + discardPile.size() +
                '}';
    }
}