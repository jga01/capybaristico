package com.jamestiago.capycards.game; // Adjust to your actual package

import java.util.Collections;
import java.util.LinkedList; // LinkedList is good for frequent removeFirst (draw) operations
import java.util.List;
import java.util.Random;

public class Deck {

    private LinkedList<CardInstance> cards; // Using LinkedList for efficient draw from top
    private static final Random random = new Random(); // For shuffling

    // Constructor: takes a list of CardInstances that will form the deck
    public Deck(List<CardInstance> initialCards) {
        if (initialCards == null) {
            this.cards = new LinkedList<>();
        } else {
            this.cards = new LinkedList<>(initialCards);
        }
    }

    /**
     * Shuffles the cards in the deck.
     */
    public void shuffle() {
        if (cards != null && !cards.isEmpty()) {
            Collections.shuffle(cards, random);
        }
    }

    /**
     * Draws a card from the top of the deck.
     *
     * @return The CardInstance drawn, or null if the deck is empty.
     */
    public CardInstance draw() {
        if (isEmpty()) {
            return null; // No cards left to draw
        }
        return cards.removeFirst(); // Removes and returns the first element (top of the deck)
    }

    /**
     * Checks if the deck is empty.
     *
     * @return true if the deck has no cards, false otherwise.
     */
    public boolean isEmpty() {
        return cards == null || cards.isEmpty();
    }

    /**
     * Gets the current number of cards remaining in the deck.
     *
     * @return The number of cards in the deck.
     */
    public int size() {
        return cards != null ? cards.size() : 0;
    }

    /**
     * Adds a card to the bottom of the deck.
     * (Useful for effects that return cards to the deck)
     *
     * @param card The CardInstance to add.
     */
    public void addCardToBottom(CardInstance card) {
        if (card != null) {
            if (cards == null) {
                cards = new LinkedList<>();
            }
            cards.addLast(card);
        }
    }

    /**
     * Adds a card to the top of the deck.
     * (Useful for specific effects)
     * 
     * @param card The CardInstance to add.
     */
    public void addCardToTop(CardInstance card) {
        if (card != null) {
            if (cards == null) {
                cards = new LinkedList<>();
            }
            cards.addFirst(card);
        }
    }

    /**
     * Returns a view of the cards in the deck.
     * Be cautious with modifying this list directly if not intended.
     *
     * @return A list of CardInstances in the deck.
     */
    public List<CardInstance> getCards() {
        // Returning a copy or unmodifiable list is safer if external modification is a
        // concern
        // For now, let's return the direct list, but be mindful.
        // return Collections.unmodifiableList(cards);
        return cards;
    }

    @Override
    public String toString() {
        return "Deck{size=" + size() + '}';
    }
}