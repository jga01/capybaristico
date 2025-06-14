import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { useSocket } from './SocketContext';
import { onGameReady, offGameReady, onGameStateUpdate, offGameStateUpdate, onCommandRejected, offCommandRejected } from '../services/socketService';
import { initLogger, log } from '../services/loggingService';

const GameContext = createContext(null);

export const useGame = () => {
    return useContext(GameContext);
};

export const GameProvider = ({ children }) => {
    const { socket } = useSocket();
    const [gameId, setGameId] = useState(null);
    const [playerId, setPlayerId] = useState(null);
    const [initialGameState, setInitialGameState] = useState(null);
    const [eventBatch, setEventBatch] = useState(null);
    const [commandError, setCommandError] = useState('');
    const [isGameOver, setIsGameOver] = useState(false);
    const [gameOverMessage, setGameOverMessage] = useState('');

    const handleGameReady = useCallback((initialState) => {
        initLogger();
        log('EVENT: Game is ready. Received initial state.', initialState);
        setInitialGameState(initialState);
        setGameId(initialState.gameId);
        setPlayerId(initialState.viewingPlayerPerspectiveId);
        setIsGameOver(false);
        setCommandError('');
    }, []);

    const handleGameStateUpdate = useCallback((data) => {
        if (data && data.newState && data.events) {
            setInitialGameState(data.newState); // Just overwrite the state
            setEventBatch(data.events);        // Use events for animation queue

            const gameOverEvent = data.events.find(e => e.eventType === 'GAME_OVER');
            if (gameOverEvent) {
                setIsGameOver(true);
                setGameOverMessage(gameOverEvent.reason || "The game has ended.");
            }
        }
    }, []);

    const handleCommandRejected = useCallback((message) => {
        setCommandError(message || "Your action was rejected by the server.");
    }, []);

    useEffect(() => {
        if (!socket) return;

        onGameReady(handleGameReady);
        onGameStateUpdate(handleGameStateUpdate);
        onCommandRejected(handleCommandRejected);

        return () => {
            offGameReady();
            offGameStateUpdate();
            offCommandRejected();
        };
    }, [socket, handleGameReady, handleGameStateUpdate, handleCommandRejected]);

    const resetGameState = useCallback(() => {
        setGameId(null);
        setPlayerId(null);
        setInitialGameState(null);
        setEventBatch(null);
        setCommandError('');
        setIsGameOver(false);
        setGameOverMessage('');
    }, []);

    const value = useMemo(() => ({
        gameId,
        playerId,
        initialGameState,
        eventBatch,
        commandError,
        isGameOver,
        gameOverMessage,
        clearCommandError: () => setCommandError(''),
        resetGameState,
    }), [gameId, playerId, initialGameState, eventBatch, commandError, isGameOver, gameOverMessage, resetGameState]);

    return (
        <GameContext.Provider value={value}>
            {children}
        </GameContext.Provider>
    );
};