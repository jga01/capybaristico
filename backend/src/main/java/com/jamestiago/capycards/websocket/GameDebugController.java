package com.jamestiago.capycards.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameStateMapper;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import com.jamestiago.capycards.game.events.GameEventLog;
import com.jamestiago.capycards.repository.GameEventLogRepository;
import com.jamestiago.capycards.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/debug")
public class GameDebugController {

    private final GameService gameService;
    private final GameEventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    public GameDebugController(GameService gameService, GameEventLogRepository eventLogRepository,
            ObjectMapper objectMapper) {
        this.gameService = gameService;
        this.eventLogRepository = eventLogRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/game/{gameId}/history")
    public ResponseEntity<?> getGameHistory(@PathVariable String gameId) {
        try {
            Game reconstructedGame = gameService.reconstructGame(gameId);
            GameStateResponse finalState = GameStateMapper.createGameStateResponse(reconstructedGame, "SERVER_VIEW",
                    "Reconstructed final state.");

            List<GameEventLog> eventLogs = eventLogRepository.findByGameIdOrderByEventSequenceAsc(gameId);

            List<Map<String, Object>> eventsAsJsonObjects = eventLogs.stream().map(log -> {
                try {
                    return objectMapper.readValue(log.getEventData(), new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception e) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("error", "Failed to parse event data");
                    errorMap.put("rawData", log.getEventData());
                    return errorMap;
                }
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "reconstructedGameState", finalState,
                    "eventHistory", eventsAsJsonObjects));

        } catch (Exception e) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Game not found or failed to reconstruct.", "message", e.getMessage()));
        }
    }
}