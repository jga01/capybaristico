package com.jamestiago.capycards.game.dto;

public class AbilityInfoDTO {
    private int index;
    private String name;
    private String description;
    private String requiresTarget; // e.g., "ANY_FIELD_CARD", "NONE"

    // Default constructor for Jackson
    public AbilityInfoDTO() {
    }

    public AbilityInfoDTO(int index, String name, String description, String requiresTarget) {
        this.index = index;
        this.name = name;
        this.description = description;
        this.requiresTarget = requiresTarget;
    }

    // Getters and Setters
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequiresTarget() {
        return requiresTarget;
    }

    public void setRequiresTarget(String requiresTarget) {
        this.requiresTarget = requiresTarget;
    }
}