package com.jamestiago.capycards.repository;

import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.model.Rarity; // If you want to find by Rarity
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository // Marks this interface as a Spring Data repository
public interface CardRepository extends JpaRepository<Card, Long> {
    // JpaRepository<EntityType, IdType>
    // Spring Data JPA will automatically implement basic CRUD operations:
    // - save(Card card)
    // - findById(Long id)
    // - findAll()
    // - deleteById(Long id)
    // - count()
    // - existsById(Long id)
    // ...and more.

    // --- Custom Query Methods (Spring Data JPA derives queries from method names)
    // ---

    /**
     * Finds a card by its unique string cardId.
     *
     * @param cardId The unique string identifier of the card.
     * @return An Optional containing the Card if found, or an empty Optional.
     */
    Optional<Card> findByCardId(String cardId);

    /**
     * Finds all cards with a specific name.
     * (Names might not be unique, so this returns a List)
     *
     * @param name The name of the card.
     * @return A List of Cards with the given name.
     */
    List<Card> findByName(String name);

    /**
     * Finds all cards of a specific rarity.
     *
     * @param rarity The Rarity enum value.
     * @return A List of Cards with the given rarity.
     */
    List<Card> findByRarity(Rarity rarity);

    /**
     * Finds all cards that include a specific type string.
     * Note: If 'type' is a comma-separated string, this requires a LIKE query.
     * Example: "Unit, Capybara"
     *
     * @param type The type string to search for (e.g., "Capybara", "Spell").
     * @return A List of Cards containing the specified type.
     */
    List<Card> findByTypeContainingIgnoreCase(String type);

    // You can add more custom finder methods as needed, for example:
    // List<Card> findByAttackGreaterThan(int attackValue);
    // List<Card> findByInitialLifeLessThanEqual(int lifeValue);
}
