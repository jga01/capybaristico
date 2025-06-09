package com.jamestiago.capycards.game;

import com.jamestiago.capycards.model.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CardInstance {
    private final String instanceId;
    private final Card cardDefinition;
    // Base stats from definition
    private int baseLife;
    private int baseAttack;
    private int baseDefense;

    // Current dynamic state
    private int currentLife;
    private boolean isExhausted;

    // New Data-Driven Effect State
    private final Map<String, Object> effectFlags = new ConcurrentHashMap<>();
    public final Map<String, Integer> temporaryStatBuffs = new ConcurrentHashMap<>(); // e.g., "ATK" -> 2
    public final Map<String, Integer> auraStatBuffs = new ConcurrentHashMap<>(); // e.g., "ATK" -> 1

    private CardInstance lastDamageSourceCard = null;

    // Key: Turn number to execute on. Value: List of effect configurations.
    private final Map<Integer, List<Map<String, Object>>> scheduledActions = new ConcurrentHashMap<>();

    public CardInstance(Card cardDefinition) {
        this.instanceId = UUID.randomUUID().toString();
        this.cardDefinition = cardDefinition;

        // Set base stats
        this.baseLife = cardDefinition.getInitialLife();
        this.baseAttack = cardDefinition.getAttack();
        this.baseDefense = cardDefinition.getDefense();

        // Initialize current stats
        this.currentLife = this.baseLife;
        this.isExhausted = true; // Default to exhausted on creation
    }

    public void addScheduledAction(int onTurnNumber, Map<String, Object> effectConfig) {
        this.scheduledActions.computeIfAbsent(onTurnNumber, k -> new ArrayList<>()).add(effectConfig);
    }

    public List<Map<String, Object>> getScheduledActionsForTurn(int turnNumber) {
        return this.scheduledActions.get(turnNumber);
    }

    public void clearScheduledActionsForTurn(int turnNumber) {
        this.scheduledActions.remove(turnNumber);
    }

    // Copy constructor for simulations
    public CardInstance(CardInstance other) {
        this.instanceId = other.instanceId;
        this.cardDefinition = other.cardDefinition; // Definitions are immutable
        this.baseLife = other.baseLife;
        this.baseAttack = other.baseAttack;
        this.baseDefense = other.baseDefense;
        this.currentLife = other.currentLife;
        this.isExhausted = other.isExhausted;
        this.lastDamageSourceCard = other.lastDamageSourceCard; // Shallow copy is fine here

        // Deep copy the maps
        this.effectFlags.putAll(other.effectFlags);
        this.temporaryStatBuffs.putAll(other.temporaryStatBuffs);

        other.scheduledActions.forEach((turn, effects) -> {
            this.scheduledActions.put(turn, new ArrayList<>(effects));
        });
    }

    // --- New Flag Management ---
    public void setEffectFlag(String flagName, Object value) {
        effectFlags.put(flagName, value);
    }

    public Object getEffectFlag(String flagName) {
        return effectFlags.get(flagName);
    }

    /**
     * Gets an effect flag's value, or a default value if the flag is not set.
     * 
     * @param flagName     The name of the flag.
     * @param defaultValue The value to return if the flag is not found.
     * @return The flag's value or the default value.
     */
    public Object getEffectFlagOrDefault(String flagName, Object defaultValue) {
        return effectFlags.getOrDefault(flagName, defaultValue);
    }

    public boolean getBooleanEffectFlag(String flagName) {
        Object value = effectFlags.get(flagName);
        return value instanceof Boolean && (Boolean) value;
    }

    public void removeEffectFlag(String flagName) {
        effectFlags.remove(flagName);
    }

    public Map<String, Object> getAllEffectFlags() {
        return this.effectFlags;
    }

    /**
     * Resets flags and buffs that are meant to be temporary.
     * Called at the start of the card controller's turn.
     * This now clears per-turn flags and buffs.
     */
    public void resetTurnSpecificState() {
        // Clear flags marked with "TURN" duration (or by naming convention)
        // For simplicity, we'll use a naming convention for now: flags ending in
        // "ThisTurn".
        effectFlags.keySet().removeIf(key -> key.endsWith("ThisTurn"));

        // Clear all temporary buffs (as they are all "until end of turn" for now)
        temporaryStatBuffs.clear();
    }

    // --- New Buff Management ---
    public void addTemporaryBuff(String stat, int amount) {
        temporaryStatBuffs.merge(stat.toUpperCase(), amount, Integer::sum);
    }

    public void clearTemporaryBuffs() {
        temporaryStatBuffs.clear();
    }

    /**
     * Clears buffs applied by auras. Called before every aura recalculation.
     */
    public void clearAuraBuffs() {
        auraStatBuffs.clear();
    }

    /**
     * Clears flags applied by auras. Called before every aura recalculation.
     * Uses a naming convention where aura-applied flags end with "_AURA".
     */
    public void clearAuraFlags() {
        effectFlags.keySet().removeIf(key -> key.endsWith("_AURA"));
    }

    public void addAuraBuff(String stat, int amount) {
        auraStatBuffs.merge(stat.toUpperCase(), amount, Integer::sum);
    }

    // --- Stat Getters (Now incorporating buffs) ---
    public int getCurrentAttack() {
        return Math.max(0, this.baseAttack
                + temporaryStatBuffs.getOrDefault("ATK", 0)
                + auraStatBuffs.getOrDefault("ATK", 0));
    }

    public int getCurrentDefense() {
        return Math.max(0, this.baseDefense
                + temporaryStatBuffs.getOrDefault("DEF", 0)
                + auraStatBuffs.getOrDefault("DEF", 0));
    }

    // --- Core Stat Setters/Methods ---
    public int getCurrentLife() {
        return currentLife;
    }

    public void setCurrentLife(int currentLife) {
        // Cap life at the original max life, potentially plus buffs to max life
        int maxLife = this.baseLife
                + temporaryStatBuffs.getOrDefault("MAX_LIFE", 0)
                + auraStatBuffs.getOrDefault("MAX_LIFE", 0);
        this.currentLife = Math.max(0, Math.min(maxLife, currentLife));
    }

    public void setBaseAttack(int newBaseAttack) {
        this.baseAttack = Math.max(0, newBaseAttack);
    }

    public void setBaseDefense(int newBaseDefense) {
        this.baseDefense = Math.max(0, newBaseDefense);
    }

    public void setBaseLife(int newBaseLife) {
        this.baseLife = Math.max(0, newBaseLife);
    }

    /**
     * Applies damage to this card.
     *
     * @param amount The amount of damage.
     * @param source The CardInstance that is the source of this damage.
     */
    public void takeDamage(int amount, CardInstance source) {
        if (amount > 0) {
            this.currentLife -= amount;
            if (this.currentLife < 0)
                this.currentLife = 0;
            this.lastDamageSourceCard = source;
        }
    }

    public void heal(int amount) {
        if (amount > 0) {
            int maxLife = this.baseLife
                    + temporaryStatBuffs.getOrDefault("MAX_LIFE", 0)
                    + auraStatBuffs.getOrDefault("MAX_LIFE", 0);
            this.currentLife = Math.min(maxLife, this.currentLife + amount);
        }
    }

    // --- Legacy methods for compatibility or direct access ---

    // The old resetTurnSpecificFlags is replaced by resetTurnSpecificState
    @Deprecated
    public void resetTurnSpecificFlags() {
        resetTurnSpecificState();
    }

    public CardInstance getLastDamageSourceCard() {
        return lastDamageSourceCard;
    }

    public Card getDefinition() {
        return cardDefinition;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public boolean isExhausted() {
        return isExhausted;
    }

    public void setExhausted(boolean exhausted) {
        isExhausted = exhausted;
    }

    public boolean isDestroyed() {
        return this.currentLife <= 0;
    }

    public boolean hasType(String typeNameToFind) {
        if (cardDefinition == null || cardDefinition.getType() == null || typeNameToFind == null) {
            return false;
        }
        String[] types = cardDefinition.getType().toLowerCase().split("\\s*,\\s*");
        return Arrays.asList(types).contains(typeNameToFind.toLowerCase());
    }

    @Override
    public String toString() {
        return "CardInstance{" +
                "name=" + (cardDefinition != null ? cardDefinition.getName() : "N/A") +
                ", instanceId='" + instanceId + '\'' +
                ", L=" + currentLife + ", A=" + getCurrentAttack() + ", D=" + getCurrentDefense() +
                ", exh=" + isExhausted +
                '}';
    }
}