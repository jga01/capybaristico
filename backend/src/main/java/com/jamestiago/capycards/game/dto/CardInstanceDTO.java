package com.jamestiago.capycards.game.dto;

import com.jamestiago.capycards.model.Rarity; // Assuming Rarity is in model
import java.util.List;

public class CardInstanceDTO {
    private String instanceId; // Unique ID of this card instance on the field/hand
    private String cardId; // From CardDefinition (e.g., "C001")
    private String name;
    private String type;
    private int currentLife;
    private int currentAttack;
    private int currentDefense;
    private String effectText;
    private Rarity rarity;
    private String imageUrl;
    private boolean isExhausted;
    private List<AbilityInfoDTO> abilities;

    public CardInstanceDTO() {
    }

    // Getters and Setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    public int getCurrentLife() {
        return currentLife;
    }

    public void setCurrentLife(int currentLife) {
        this.currentLife = currentLife;
    }

    public int getCurrentAttack() {
        return currentAttack;
    }

    public void setCurrentAttack(int currentAttack) {
        this.currentAttack = currentAttack;
    }

    public int getCurrentDefense() {
        return currentDefense;
    }

    public void setCurrentDefense(int currentDefense) {
        this.currentDefense = currentDefense;
    }

    public String getEffectText() {
        return effectText;
    }

    public void setEffectText(String effectText) {
        this.effectText = effectText;
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

    public boolean getIsExhausted() {
        return isExhausted;
    }

    public void setIsExhausted(boolean isExhausted) {
        this.isExhausted = isExhausted;
    }

    public List<AbilityInfoDTO> getAbilities() {
        return abilities;
    }

    public void setAbilities(List<AbilityInfoDTO> abilities) {
        this.abilities = abilities;
    }
}
