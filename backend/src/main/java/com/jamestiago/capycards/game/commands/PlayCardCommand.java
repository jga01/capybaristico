package com.jamestiago.capycards.game.commands;

public final class PlayCardCommand extends GameCommand {
    public int handCardIndex;
    public int targetFieldSlot;

    // Default constructor for Jackson
    public PlayCardCommand() {
        super();
    }

    public PlayCardCommand(String gameId, String playerId, int handCardIndex, int targetFieldSlot) {
        super(gameId, playerId);
        this.handCardIndex = handCardIndex;
        this.targetFieldSlot = targetFieldSlot;
    }

    @Override
    public String getCommandType() {
        return "PLAY_CARD";
    }
}