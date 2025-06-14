package com.jamestiago.capycards.service;

import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameEngine;
import com.jamestiago.capycards.game.ai.AICommandGenerator;
import com.jamestiago.capycards.game.ai.BoardEvaluator;
import com.jamestiago.capycards.game.commands.EndTurnCommand;
import com.jamestiago.capycards.game.commands.GameCommand;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final GameService gameService;
    private ExecutorService aiThreadPool;

    private static final int AI_ACTION_DELAY_MS = 1200; // Delay between AI actions
    private static final int AI_INITIAL_THINK_DELAY_MS = 1500; // Delay before the first action

    public AIService(@Lazy GameService gameService, GameEngine gameEngine) {
        this.gameService = gameService;
    }

    @PostConstruct
    public void init() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        aiThreadPool = Executors.newFixedThreadPool(Math.max(1, availableProcessors / 2));
        logger.info("AIService initialized with a thread pool of size {}.", Math.max(1, availableProcessors / 2));
    }

    @PreDestroy
    public void shutdown() {
        aiThreadPool.shutdown();
        try {
            if (!aiThreadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                aiThreadPool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            aiThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("AIService thread pool shut down.");
    }

    /**
     * Public entry point for an AI to take its turn. This starts the decision loop.
     */
    public void takeTurn(String gameId, String aiPlayerId) {
        aiThreadPool.submit(() -> {
            try {
                Thread.sleep(AI_INITIAL_THINK_DELAY_MS);
                planAndExecuteNextStep(gameId, aiPlayerId);
            } catch (InterruptedException e) {
                logger.warn("AI initial thinking process was interrupted for game {}", gameId);
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * The core AI logic loop. It decides on ONE action, executes it, and then
     * schedules the next decision.
     */
    private void planAndExecuteNextStep(String gameId, String aiPlayerId) {
        Game currentGame = gameService.getGame(gameId);
        if (isGameInvalidForAITurn(currentGame, aiPlayerId)) {
            logger.warn("[{}] AI turn is over or game is invalid. Stopping decision loop.", gameId);
            return;
        }

        // Decide the single best move from the current state.
        GameCommand bestCommand = decideNextSingleMove(currentGame, aiPlayerId);

        logger.info("[{}] AI decided on next command: {}", gameId, bestCommand.getCommandType());

        // Execute the command
        gameService.handleCommand(bestCommand);

        // If the best move was NOT to end the turn, schedule the next decision step.
        if (!(bestCommand instanceof EndTurnCommand)) {
            aiThreadPool.submit(() -> {
                try {
                    Thread.sleep(AI_ACTION_DELAY_MS);
                    planAndExecuteNextStep(gameId, aiPlayerId); // Recursive call for the next step
                } catch (InterruptedException e) {
                    logger.warn("AI action delay was interrupted for game {}", gameId);
                    Thread.currentThread().interrupt();
                }
            });
        }
        // If the command was EndTurn, the loop naturally terminates.
    }

    private GameCommand decideNextSingleMove(Game game, String aiPlayerId) {
        List<GameCommand> possibleCommands = AICommandGenerator.generateValidCommands(game, aiPlayerId);
        GameCommand bestCommand = null;

        double bestScore = -Double.MAX_VALUE;

        bestCommand = new EndTurnCommand(game.getGameId(), aiPlayerId);

        double endTurnScore = BoardEvaluator.evaluate(game, aiPlayerId);
        bestScore = endTurnScore;

        for (GameCommand command : possibleCommands) {
            if (command instanceof EndTurnCommand) {
                continue;
            }

            Game simulationGame = new Game(game);

            double scoreAfterMove = BoardEvaluator.evaluate(simulationGame, aiPlayerId);
            logger.trace("AI simulation: Command {} -> Score {}", command.getCommandType(), scoreAfterMove);

            if (scoreAfterMove >= bestScore) {
                bestScore = scoreAfterMove;
                bestCommand = command;
            }
        }

        return bestCommand;
    }

    private boolean isGameInvalidForAITurn(Game game, String aiPlayerId) {
        if (game == null || game.getGameState().name().contains("GAME_OVER") || game.getCurrentPlayer() == null) {
            return true;
        }
        return !game.getCurrentPlayer().getPlayerId().equals(aiPlayerId);
    }
}