package com.jamestiago.capycards.admin;

import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.repository.CardRepository;
import com.jamestiago.capycards.service.CardDataSeeder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin/cards")
public class CardAdminController {
    private final CardRepository cardRepository;
    private final CardDataSeeder cardDataSeeder;

    public CardAdminController(CardRepository cardRepository, CardDataSeeder cardDataSeeder) {
        this.cardRepository = cardRepository;
        this.cardDataSeeder = cardDataSeeder;
    }

    @GetMapping
    public List<Card> getAllCards() {
        List<Card> cards = cardRepository.findAll();
        cards.sort(Comparator.comparing(Card::getCardId));
        return cards;
    }

    @PostMapping
    public Card createCard(@RequestBody Card card) {
        if (cardRepository.findByCardId(card.getCardId()).isPresent()) {
            throw new IllegalArgumentException("Card with cardId " + card.getCardId() + " already exists.");
        }
        return cardRepository.save(card);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Card> getCardById(@PathVariable Long id) {
        return cardRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Card> updateCard(@PathVariable Long id, @RequestBody Card cardDetails) {
        return cardRepository.findById(id).map(card -> {
            card.setCardId(cardDetails.getCardId());
            card.setName(cardDetails.getName());
            card.setType(cardDetails.getType());
            card.setInitialLife(cardDetails.getInitialLife());
            card.setAttack(cardDetails.getAttack());
            card.setDefense(cardDetails.getDefense());
            card.setEffectText(cardDetails.getEffectText());
            card.setEffectConfiguration(cardDetails.getEffectConfiguration());
            card.setRarity(cardDetails.getRarity());
            card.setImageUrl(cardDetails.getImageUrl());
            card.setFlavorText(cardDetails.getFlavorText());
            card.setDirectlyPlayable(cardDetails.isDirectlyPlayable());
            Card updatedCard = cardRepository.save(card);
            return ResponseEntity.ok(updatedCard);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable Long id) {
        if (!cardRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        cardRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reload-definitions")
    public ResponseEntity<String> reloadCardDefinitions() {
        try {
            cardDataSeeder.seedCards();
            return ResponseEntity.ok("Card definitions reloaded from JSON files successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error reloading card definitions: " + e.getMessage());
        }
    }
}