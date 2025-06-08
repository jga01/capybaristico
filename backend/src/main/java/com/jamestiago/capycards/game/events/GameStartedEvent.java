package com.jamestiago.capycards.game.events;

/**
 * A simple event to mark the beginning of the game's event log.
 * The initial state is sent separately via the 'game_ready' socket event.
 */
public final class GameStartedEvent extends GameEvent {
    public final String player1Id;
    public final String player2Id;
    public final String startingPlayerId;

    public GameStartedEvent(String gameId, int turnNumber, String p1Id, String p2Id, String startingPlayerId) {
        super(gameId, turnNumber);
        this.player1Id = p1Id;
        this.player2Id = p2Id;
        this.startingPlayerId = startingPlayerId;
    }
}