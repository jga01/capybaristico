package com.jamestiago.capycards.repository;

import com.jamestiago.capycards.game.events.GameEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameEventLogRepository extends JpaRepository<GameEventLog, Long> {

    /**
     * Finds all event logs for a specific game, ordered by their sequence number.
     * This is the primary method for reconstructing a game's state.
     */
    List<GameEventLog> findByGameIdOrderByEventSequenceAsc(String gameId);

    /**
     * Finds all unique gameIds that do not have a 'GAME_OVER' event.
     * This is useful for finding active games on server startup.
     */
    @Query("SELECT DISTINCT e.gameId FROM GameEventLog e WHERE e.gameId NOT IN (SELECT e2.gameId FROM GameEventLog e2 WHERE e2.eventType = 'GameOverEvent')")
    List<String> findAllActiveGameIds();
}