package com.jamestiago.capycards.service;

import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameEngine;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.commands.GameCommand;
import com.jamestiago.capycards.game.events.GameEvent;
import com.jamestiago.capycards.game.events.GameStartedEvent;
import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class GameService {
  private static final Logger logger = LoggerFactory.getLogger(GameService.class);
  private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
  private final Map<String, Lock> gameLocks = new ConcurrentHashMap<>();
  private List<Card> allCardDefinitions;
  private final CardRepository cardRepository;
  private final GameEngine gameEngine;

  @Autowired
  public GameService(CardRepository cardRepository, GameEngine gameEngine) {
    this.cardRepository = cardRepository;
    this.gameEngine = gameEngine;
    logger.info("GameService initialized.");
  }

  @PostConstruct
  public void initializeCardDefinitions() {
    loadAllCardDefinitions();
  }

  // Reloads card definitions from the database after the seeder has run.
  public void loadAllCardDefinitions() {
    this.allCardDefinitions = cardRepository.findAll();
    if (this.allCardDefinitions.isEmpty()) {
      logger.error("CRITICAL: No card definitions found in the database after seeding! The game cannot start.");
    } else {
      logger.info("Successfully loaded {} card definitions from the database.", allCardDefinitions.size());
    }
  }

  private List<Card> generatePlayerDeck() {
    if (allCardDefinitions == null || allCardDefinitions.isEmpty()) {
      throw new IllegalStateException("Card definitions not loaded.");
    }
    List<Card> playableCards = allCardDefinitions.stream()
        .filter(Card::isDirectlyPlayable)
        .collect(Collectors.toList());

    List<Card> deckInProgress = new ArrayList<>();
    // A simple way to create a deck of 20 cards. For a real game, you'd have
    // deck-building rules.
    for (int i = 0; i < 20; i++) {
      deckInProgress.add(playableCards.get(i % playableCards.size()));
    }
    Collections.shuffle(deckInProgress);
    return deckInProgress;
  }

  public Game createNewGame(String player1DisplayName, String player2DisplayName) {
    if (allCardDefinitions == null || allCardDefinitions.isEmpty()) {
      throw new IllegalStateException("Card definitions not loaded. Cannot create game.");
    }

    Player player1 = new Player(player1DisplayName, generatePlayerDeck());
    Player player2 = new Player(player2DisplayName, generatePlayerDeck());

    Game newGame = new Game(player1, player2, allCardDefinitions);

    newGame.setGameState(Game.GameState.WAITING_FOR_PLAYERS);
    activeGames.put(newGame.getGameId(), newGame);
    gameLocks.put(newGame.getGameId(), new ReentrantLock());

    MDC.put("gameId", newGame.getGameId());
    logger.info("New game object created. P1: {}, P2: {}", player1DisplayName, player2DisplayName);
    MDC.remove("gameId");

    return newGame;
  }

  public List<GameEvent> startGame(Game game) {
    GameStartedEvent startEvent = new GameStartedEvent(
        game.getGameId(),
        0,
        game.getPlayer1().getPlayerId(),
        game.getPlayer2().getPlayerId(),
        game.getPlayer1().getPlayerId());

    List<GameEvent> startupEvents = new ArrayList<>();
    startupEvents.add(startEvent);

    game.apply(startEvent);

    logger.info("Game {} started with {} initial events.", game.getGameId(), startupEvents.size());
    return startupEvents;
  }

  public Game getGame(String gameId) {
    return activeGames.get(gameId);
  }

  public List<GameEvent> handleCommand(GameCommand command) {
    Lock lock = gameLocks.get(command.gameId);
    if (lock == null) {
      logger.error("No lock found for game: {}", command.gameId);
      return List.of();
    }

    lock.lock();
    try {
      Game game = activeGames.get(command.gameId);
      if (game == null) {
        logger.warn("Attempted to handle command for non-existent game: {}", command.gameId);
        return List.of();
      }

      logger.debug("[{}] Service received command: {}", command.gameId, command.getCommandType());

      List<GameEvent> events = gameEngine.processCommand(game, command);

      if (events.isEmpty()) {
        logger.warn("Command {} from session for game {} resulted in NO events. Action was likely invalid.",
            command.getCommandType(), game.getGameId());
        return List.of();
      }

      logger.debug("[{}] Engine produced {} events. Applying them to game state.", game.getGameId(), events.size());

      for (GameEvent event : events) {
        game.apply(event);
      }

      logger.debug("[{}] Finished applying events. New game state: {}", game.getGameId(), game.getGameState());

      return events;
    } finally {
      lock.unlock();
    }
  }

  public void removeGame(String gameId) {
    MDC.put("gameId", gameId);
    Game removedGame = activeGames.remove(gameId);
    gameLocks.remove(gameId); // Also remove the lock
    if (removedGame != null) {
      logger.info("Game {} has ended and is now removed from active games. This log file is now complete.", gameId);
    } else {
      logger.warn("Attempted to remove non-existent game: {}", gameId);
    }
    MDC.remove("gameId");
  }
}