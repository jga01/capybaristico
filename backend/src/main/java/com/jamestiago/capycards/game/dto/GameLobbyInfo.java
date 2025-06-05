package com.jamestiago.capycards.game.dto;

import java.util.UUID; // For session ID

public class GameLobbyInfo {
    private String lobbyId;
    private String creatorDisplayName;
    private UUID creatorSessionId; // Store the creator's socket session ID
    private long creationTimeMillis;
    private int currentPlayerCount;

    // Constructor updated to include creatorSessionId
    public GameLobbyInfo(String lobbyId, String creatorDisplayName, UUID creatorSessionId) {
        this.lobbyId = lobbyId;
        this.creatorDisplayName = creatorDisplayName;
        this.creatorSessionId = creatorSessionId;
        this.creationTimeMillis = System.currentTimeMillis();
        this.currentPlayerCount = 1;
    }

    // Getters
    public String getLobbyId() { return lobbyId; }
    public String getCreatorDisplayName() { return creatorDisplayName; }
    public UUID getCreatorSessionId() { return creatorSessionId; }
    public long getCreationTimeMillis() { return creationTimeMillis; }
    public int getCurrentPlayerCount() { return currentPlayerCount; }

    // Setter for player count (might not be needed if only used for display)
    // public void setCurrentPlayerCount(int currentPlayerCount) { this.currentPlayerCount = currentPlayerCount; }
}
