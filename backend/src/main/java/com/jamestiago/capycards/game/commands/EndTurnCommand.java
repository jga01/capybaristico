package com.jamestiago.capycards.game.commands;

public final class EndTurnCommand extends GameCommand {
    public EndTurnCommand(String gameId, String playerId) {
        super(gameId, playerId);
    }

    @Override
    public String getCommandType() {
        return "END_TURN";
    }
}