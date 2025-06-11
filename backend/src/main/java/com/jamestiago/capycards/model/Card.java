package com.jamestiago.capycards.model; // Adjust to your actual package

import jakarta.persistence.*; // For JPA annotations

@Entity
@Table(name = "cards") // Specifies the database table this entity maps to
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // For auto-incrementing IDs managed by the DB (like SERIAL)
    private Long id; // Internal database primary key

    @Column(name = "card_id", nullable = false, unique = true)
    private String cardId; // Your unique string identifier for the card

    @Column(nullable = false)
    private String name;

    @Column
    private String type; // e.g., "Capybara, Fire", "Undead, Spell"

    @Column(name = "initial_life", nullable = false)
    private Integer initialLife;

    @Column(nullable = false)
    private Integer attack;

    @Column(nullable = false)
    private Integer defense;

    // MODIFIED: Replaced @Lob with a more specific column definition for PostgreSQL
    // compatibility.
    @Column(name = "effect_text", columnDefinition = "TEXT")
    private String effectText;

    // MODIFIED: Replaced @Lob with a more specific column definition.
    @Column(name = "effect_configuration", columnDefinition = "TEXT")
    private String effectConfiguration;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING) // Store enum as string in DB
    private Rarity rarity;

    @Column(name = "image_url")
    private String imageUrl;

    // MODIFIED: Replaced @Lob with a more specific column definition.
    @Column(name = "flavor_text", columnDefinition = "TEXT")
    private String flavorText;

    @Column(name = "is_directly_playable", nullable = false, columnDefinition = "boolean default true")
    private boolean isDirectlyPlayable = true;

    public Card() {
    }

    // Constructor for creating new card definitions
    public Card(String cardId, String name, String type, Integer initialLife, Integer attack, Integer defense,
            String effectText, String effectConfiguration,
            Rarity rarity, String imageUrl, String flavorText) {
        this.cardId = cardId;
        this.name = name;
        this.type = type;
        this.initialLife = initialLife;
        this.attack = attack;
        this.defense = defense;
        this.effectText = effectText;
        this.effectConfiguration = effectConfiguration;
        this.rarity = rarity;
        this.imageUrl = imageUrl;
        this.flavorText = flavorText;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getInitialLife() {
        return initialLife;
    }

    public void setInitialLife(Integer initialLife) {
        this.initialLife = initialLife;
    }

    public Integer getAttack() {
        return attack;
    }

    public void setAttack(Integer attack) {
        this.attack = attack;
    }

    public Integer getDefense() {
        return defense;
    }

    public void setDefense(Integer defense) {
        this.defense = defense;
    }

    public String getEffectText() {
        return effectText;
    }

    public void setEffectText(String effectText) {
        this.effectText = effectText;
    }

    public String getEffectConfiguration() {
        return effectConfiguration;
    }

    public void setEffectConfiguration(String effectConfiguration) {
        this.effectConfiguration = effectConfiguration;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public void setRarity(Rarity rarity) {
        this.rarity = rarity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getFlavorText() {
        return flavorText;
    }

    public void setFlavorText(String flavorText) {
        this.flavorText = flavorText;
    }

    public boolean isDirectlyPlayable() {
        return isDirectlyPlayable;
    }

    public void setDirectlyPlayable(boolean directlyPlayable) {
        isDirectlyPlayable = directlyPlayable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Card card = (Card) o;
        return cardId != null ? cardId.equals(card.cardId) : card.cardId == null;
    }

    @Override
    public int hashCode() {
        return cardId != null ? cardId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Card{" +
                "id=" + id +
                ", cardId='" + cardId + '\'' +
                ", name='" + name + '\'' +
                ", rarity=" + rarity +
                '}';
    }
}