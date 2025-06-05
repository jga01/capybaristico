package com.jamestiago.capycards.game;

import com.jamestiago.capycards.model.Card;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects; // For Objects.nonNull
import java.util.UUID;

public class Player {
    private final String playerId;
    private String displayName;

    private Deck deck;
    private List<CardInstance> hand;
    private List<CardInstance> field;
    private List<CardInstance> discardPile;

    public static final int MAX_HAND_SIZE = 5;
    public static final int MAX_FIELD_SIZE = 4;
    public static final int MAX_ATTACKS_PER_TURN = 2; // Define rule here

    private int attacksDeclaredThisTurn = 0;

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

    public boolean drawCard() {
        if (deck.isEmpty()) {
            return false;
        }
        CardInstance drawnCard = deck.draw();
        if (drawnCard != null) {
            hand.add(drawnCard);
            return true;
        }
        return false;
    }

    public boolean drawUntilHandIsFull(int targetHandSize) {
        int cardsToDraw = targetHandSize - hand.size();
        if (cardsToDraw <= 0) {
            return true;
        }

        for (int i = 0; i < cardsToDraw; i++) {
            if (hand.size() >= targetHandSize)
                break;
            if (deck.isEmpty())
                return false;
            if (!drawCard())
                return false;
        }

        return hand.size() >= targetHandSize || (cardsToDraw > 0 && deck.isEmpty());
    }

    public boolean playCardFromHandToField(int handIndex, int fieldSlotIndex) {
        if (handIndex < 0 || handIndex >= hand.size())
            return false;
        if (fieldSlotIndex < 0 || fieldSlotIndex >= MAX_FIELD_SIZE)
            return false;
        if (field.get(fieldSlotIndex) != null)
            return false;

        CardInstance cardToPlay = hand.remove(handIndex);
        field.set(fieldSlotIndex, cardToPlay);
        return true;
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

    public void resetTurnActions() {
        this.attacksDeclaredThisTurn = 0;
        for (CardInstance card : field) {
            if (card != null) {
                card.setExhausted(false);
                card.resetTurnSpecificState(); // Updated method call

                // Clear "1 turn" status effects at the start of the owner's turn
                card.removeEffectFlag("status_silenced");
            }
        }
    }

    public int findEmptyFieldSlot() {
        for (int i = 0; i < MAX_FIELD_SIZE; i++) {
            if (field.get(i) == null) {
                return i;
            }
        }
        return -1; // No empty slot
    }

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
        return Collections.unmodifiableList(new ArrayList<>(field));
    }

    public List<CardInstance> getFieldInternal() {
        return this.field;
    }

    public List<CardInstance> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isFieldEmpty() {
        return field.stream().allMatch(Objects::isNull);
    }

    public CardInstance removeCardFromField(int fieldSlotIndex) {
        if (fieldSlotIndex >= 0 && fieldSlotIndex < MAX_FIELD_SIZE) {
            CardInstance card = field.get(fieldSlotIndex);
            if (card != null) {
                field.set(fieldSlotIndex, null);
                return card;
            }
        }
        return null;
    }

    public void addCardToDiscardPile(CardInstance card) {
        if (card != null)
            this.discardPile.add(card);
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