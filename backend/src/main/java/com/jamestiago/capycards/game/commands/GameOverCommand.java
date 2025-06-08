package com.jamestiago.capycards.game.commands;

// This is a special command, not typically sent by a player, but used internally
// or by server logic (like disconnects) to trigger the end of a game.
public final class GameOverCommand extends GameCommand {
    public final String winnerPlayerId;
    public final String reason;

    public GameOverCommand(String gameId, String winnerPlayerId, String reason) {
        // The "playerId" for a system-generated command can be null or a system ID.
        super(gameId, null);
        this.winnerPlayerId = winnerPlayerId;
        this.reason = reason;
    }

    @Override
    public String getCommandType() {
        return "GAME_OVER";
    }
}