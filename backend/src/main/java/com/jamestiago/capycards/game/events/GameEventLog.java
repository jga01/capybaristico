package com.jamestiago.capycards.game.events;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_event_log", indexes = {
        @Index(name = "idx_game_event_log_game_id", columnList = "gameId")
})
public class GameEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String gameId;

    @Column(nullable = false, updatable = false)
    private long eventSequence; // For strict ordering

    @Column(nullable = false, updatable = false)
    private Instant eventTimestamp;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String eventData; // The JSON representation of the event

    // Constructors, Getters, and Setters
    public GameEventLog() {
    }

    public GameEventLog(String gameId, long eventSequence, Instant eventTimestamp, String eventType, String eventData) {
        this.gameId = gameId;
        this.eventSequence = eventSequence;
        this.eventTimestamp = eventTimestamp;
        this.eventType = eventType;
        this.eventData = eventData;
    }

    // Getters and Setters...
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public long getEventSequence() {
        return eventSequence;
    }

    public void setEventSequence(long eventSequence) {
        this.eventSequence = eventSequence;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }
}