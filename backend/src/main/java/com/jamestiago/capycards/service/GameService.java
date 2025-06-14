package com.jamestiago.capycards.service;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameEngine;
import com.jamestiago.capycards.game.GameStateMapper;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.ai.AIPlayer;
import com.jamestiago.capycards.game.commands.GameCommand;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import com.jamestiago.capycards.game.events.GameEvent;
import com.jamestiago.capycards.game.events.GameEventLog;
import com.jamestiago.capycards.game.events.PlayerDrewCardEvent;
import com.jamestiago.capycards.game.events.GameStartedEvent;
import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.repository.CardRepository;
import com.jamestiago.capycards.repository.GameEventLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
  private final GameEventLogRepository eventLogRepository;
  private final GameEngine gameEngine;
  private final AIService aiService;
  private final SocketIOServer socketServer;
  private final ObjectMapper objectMapper;

  public GameService(CardRepository cardRepository, GameEngine gameEngine, @Lazy AIService aiService,
      SocketIOServer socketServer, ObjectMapper objectMapper, GameEventLogRepository eventLogRepository) {
    this.cardRepository = cardRepository;
    this.gameEngine = gameEngine;
    this.aiService = aiService;
    this.socketServer = socketServer;
    this.objectMapper = objectMapper;
    this.eventLogRepository = eventLogRepository;
    logger.info("GameService initialized.");
  }

  @PostConstruct
  public void initialize() {
    loadAllCardDefinitions();
    reconstructActiveGames();
  }

  public void loadAllCardDefinitions() {
    this.allCardDefinitions = cardRepository.findAll();
    if (this.allCardDefinitions.isEmpty()) {
      logger.error("CRITICAL: No card definitions found in the database after seeding! The game cannot start.");
    } else {
      logger.info("Successfully loaded {} card definitions from the database.", allCardDefinitions.size());
    }
  }
  
  private void reconstructActiveGames() {
    logger.info("Searching for active games to reconstruct...");
    List<String> activeGameIds = eventLogRepository.findAllActiveGameIds();
    int reconstructedCount = 0;
    for (String gameId : activeGameIds) {
      try {
        reconstructGame(gameId);
        reconstructedCount++;
      } catch (Exception e) {
        logger.error("Failed to reconstruct game {}", gameId, e);
      }
    }
    logger.info("Reconstruction complete. {} active games restored.", reconstructedCount);
  }
  
  public Game reconstructGame(String gameId) throws Exception {
    logger.info("Reconstructing game state for gameId: {}", gameId);
    List<GameEventLog> eventLogs = eventLogRepository.findByGameIdOrderByEventSequenceAsc(gameId);
    if (eventLogs.isEmpty()) {
      throw new IllegalStateException("No events found for gameId: " + gameId);
    }
  
    // Pass the definitions map to the constructor
    Map<String, Card> definitionsMap = allCardDefinitions.stream()
      .collect(Collectors.toConcurrentMap(Card::getCardId, c -> c));
    Game game = new Game(gameId, definitionsMap);
  
    for (GameEventLog eventLog : eventLogs) {
      GameEvent event = objectMapper.readValue(eventLog.getEventData(), GameEvent.class);
      game.apply(event);
    }
  
    if (game.getGameState().name().contains("GAME_OVER")) {
      logger.info("Game {} was already over. Not adding to active games.", gameId);
      return game;
    }
  
    activeGames.put(gameId, game);
    gameLocks.put(gameId, new ReentrantLock());
    logger.info("Successfully reconstructed game {}. Current state: {}", gameId, game.getGameState());
    return game;
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

  public Game createAiGame(String playerDisplayName) {
    if (allCardDefinitions == null || allCardDefinitions.isEmpty()) {
      throw new IllegalStateException("Card definitions not loaded. Cannot create game.");
    }
    Player humanPlayer = new Player(playerDisplayName, generatePlayerDeck());
    Player aiPlayer = new AIPlayer(generatePlayerDeck());

    // For AI games, let the human always go first for a better user experience.
    Game newGame = new Game(humanPlayer, aiPlayer, allCardDefinitions);

    newGame.setGameState(Game.GameState.WAITING_FOR_PLAYERS);
    activeGames.put(newGame.getGameId(), newGame);
    gameLocks.put(newGame.getGameId(), new ReentrantLock());

    MDC.put("gameId", newGame.getGameId());
    logger.info("New AI game object created. Human: {}, AI: {}", playerDisplayName, aiPlayer.getDisplayName());
    MDC.remove("gameId");

    return newGame;
  }

  public void startGame(Game game) {
    GameStartedEvent startEvent = new GameStartedEvent(
        game.getGameId(),
        0,
        game.getPlayer1().getPlayerId(),
        game.getPlayer2().getPlayerId(),
        game.getPlayer1().getPlayerId()); // Player 1 always starts
    List<GameEvent> startupEvents = new ArrayList<>();
    startupEvents.add(startEvent);

    // Apply and broadcast the start event
    applyAndBroadcast(game, startupEvents);
    logger.info("Game {} started with {} initial events.", game.getGameId(), startupEvents.size());
  }

  public Game getGame(String gameId) {
    return activeGames.get(gameId);
  }

  @Transactional
  public void handleCommand(GameCommand command) {
    Lock lock = gameLocks.get(command.gameId);
    if (lock == null) {
      logger.error("No lock found for game: {}", command.gameId);
      return;
    }
    lock.lock();
    try {
      Game game = activeGames.get(command.gameId);
      if (game == null) {
        logger.warn("Attempted to handle command for non-existent game: {}", command.gameId);
        return;
      }

      // Log the complete game state BEFORE the command is processed.
      try {
        String stateBefore = objectMapper.writeValueAsString(
            GameStateMapper.createGameStateResponse(game, "SERVER_VIEW", "Pre-command state"));
        logger.info("STATE BEFORE CMD [{}]: {}", command.getCommandType(), stateBefore);
      } catch (Exception e) {
        logger.error("Failed to serialize pre-command game state for logging.", e);
      }

      Player playerBeforeCommand = game.getCurrentPlayer();

      List<GameEvent> events = gameEngine.processCommand(game, command);

      if (events.isEmpty()) {
        logger.warn("Command {} from player {} for game {} resulted in NO events. Action was likely invalid.",
            command.getCommandType(), command.playerId, game.getGameId());
        return;
      }

      applyAndBroadcast(game, events);

      Player playerAfterCommand = game.getCurrentPlayer();
      if (playerAfterCommand != null && playerAfterCommand.isAi()
          && !game.getGameState().name().contains("GAME_OVER")) {
        if (playerBeforeCommand == null
            || !playerBeforeCommand.getPlayerId().equals(playerAfterCommand.getPlayerId())) {
          logger.info("[{}] Turn has passed to AI player {}. Triggering AI action.", game.getGameId(),
              playerAfterCommand.getPlayerId());
          aiService.takeTurn(game.getGameId(), playerAfterCommand.getPlayerId());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Transactional
  private void applyAndBroadcast(Game game, List<GameEvent> events) {
    if (events == null || events.isEmpty())
      return;

    // Save events to the database
    List<GameEventLog> logsToSave = new ArrayList<>();
    for (GameEvent event : events) {
      try {
        String eventJson = objectMapper.writeValueAsString(event);
        GameEventLog log = new GameEventLog(
            game.getGameId(),
            game.getNextEventSequence() + logsToSave.size(),
            Instant.now(),
            event.getClass().getSimpleName(),
            eventJson);
        logsToSave.add(log);
      } catch (Exception e) {
        logger.error("[{}] CRITICAL: Failed to serialize game event for persistence. Aborting save.", game.getGameId(),
            e);
        return;
      }
    }
    eventLogRepository.saveAll(logsToSave);

    // Apply events to the master game state
    for (GameEvent event : events) {
      game.apply(event);
    }
    logger.debug("[{}] Applied and persisted {} events. New game state: {}", game.getGameId(), events.size(),
        game.getGameState());

    // The new payload will contain both the new state and the events for animation
    socketServer.getRoomOperations(game.getGameId()).getClients().forEach(c -> {
      String viewingPlayerId = (String) c.get("playerId");
      if (viewingPlayerId != null) {
        // Create the authoritative new state for this player's perspective
        GameStateResponse newState = GameStateMapper.createGameStateResponse(game, viewingPlayerId, "State updated.");

        // Tailor events to hide private information (like cards drawn by opponent)
        List<GameEvent> tailoredEvents = tailorEventsForPlayer(events, viewingPlayerId);
        List<Map<String, Object>> tailoredEventData = convertEventsToMapList(tailoredEvents);

        // Send a new, combined event
        c.sendEvent("game_state_update", Map.of(
            "newState", newState,
            "events", tailoredEventData));
      }
    });

    logger.debug("[{}] Broadcasted new state and {} events to room.", game.getGameId(), events.size());
  }

  private List<GameEvent> tailorEventsForPlayer(List<GameEvent> originalEvents, String targetPlayerId) {
    return originalEvents.stream().map(event -> {
      if (event instanceof PlayerDrewCardEvent drawEvent) {
        if (!drawEvent.playerId.equals(targetPlayerId)) {
          return new PlayerDrewCardEvent(
              drawEvent.gameId,
              drawEvent.turnNumber,
              drawEvent.playerId,
              null, // Hide the card
              drawEvent.newHandSize,
              drawEvent.newDeckSize);
        }
      }
      return event; // Return all other events as is
    }).collect(Collectors.toList());
  }

  private List<Map<String, Object>> convertEventsToMapList(List<GameEvent> events) {
    return events.stream()
        .map(event -> objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() {
        }))
        .collect(Collectors.toList());
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