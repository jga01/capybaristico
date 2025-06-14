package com.jamestiago.capycards.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.model.Rarity;
import com.jamestiago.capycards.repository.CardRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
public class CardDataSeeder {
    private static final Logger logger = LoggerFactory.getLogger(CardDataSeeder.class);

    private final CardRepository cardRepository;
    private final ObjectMapper objectMapper;

    public CardDataSeeder(CardRepository cardRepository, ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Transactional
    public void seedCards() {
        logger.info("Starting card data seeding process...");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:card_definitions/*.json");
            logger.info("Found {} card definition files to process.", resources.length);

            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    JsonNode rootNode = objectMapper.readTree(inputStream);
                    
                    String cardId = rootNode.get("cardId").asText();
                    Optional<Card> existingCardOpt = cardRepository.findByCardId(cardId);
                    
                    Card card = existingCardOpt.orElseGet(Card::new);
                    
                    card.setCardId(cardId);
                    card.setName(rootNode.get("name").asText());
                    card.setType(rootNode.get("type").asText());
                    card.setInitialLife(rootNode.get("initialLife").asInt());
                    card.setAttack(rootNode.get("attack").asInt());
                    card.setDefense(rootNode.get("defense").asInt());
                    card.setEffectText(rootNode.get("effectText").asText());
                    card.setRarity(Rarity.valueOf(rootNode.get("rarity").asText()));
                    card.setImageUrl(rootNode.get("imageUrl").asText());
                    card.setFlavorText(rootNode.path("flavorText").asText(null));
                    card.setDirectlyPlayable(rootNode.path("isDirectlyPlayable").asBoolean(true));

                    // Serialize the effectConfiguration part of the JSON back into a string
                    JsonNode effectConfigNode = rootNode.get("effectConfiguration");
                    if (effectConfigNode != null) {
                        card.setEffectConfiguration(objectMapper.writeValueAsString(effectConfigNode));
                    }

                    cardRepository.save(card);
                    logger.trace("Successfully seeded/updated card: {}", card.getName());
                }
            }
            logger.info("Card data seeding process completed successfully.");
        } catch (IOException e) {
            logger.error("Failed to read card definition resources.", e);
        }
    }
}