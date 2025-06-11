import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { useSocket } from './SocketContext';
import {
    emitCreateLobby, emitGetOpenLobbies, emitJoinLobby,
    onLobbyCreated, offLobbyCreated, onLobbyError, offLobbyError,
    onOpenLobbiesList, offOpenLobbiesList, onOpenLobbiesUpdate, offOpenLobbiesUpdate,
    onJoinLobbyFailed, offJoinLobbyFailed,
    emitCreateAiGame // New import
} from '../services/socketService';
import { log } from '../services/loggingService';
const LobbyContext = createContext(null);
export const useLobby = () => {
    return useContext(LobbyContext);
};
export const LobbyProvider = ({ children }) => {
    const { socket, isConnected } = useSocket();
    const [displayName, setDisplayName] = useState('');
    const [lobbyId, setLobbyId] = useState(null);
    const [openLobbies, setOpenLobbies] = useState([]);
    const [error, setError] = useState('');
    const [isJoining, setIsJoining] = useState(false);
    const [waitingMessage, setWaitingMessage] = useState('');
    const handleLobbyCreated = useCallback((data) => {
        setLobbyId(data.lobbyId);
        setWaitingMessage(data.message || `Lobby ${data.lobbyId} created. Waiting...`);
        setError('');
    }, []);

    const handleLobbyError = useCallback((errorMessage) => {
        setError(errorMessage);
        setIsJoining(false);
    }, []);

    const handleOpenLobbiesUpdate = useCallback((lobbies) => setOpenLobbies(lobbies || []), []);

    const handleJoinFailed = useCallback((errorMessage) => {
        setError(errorMessage);
        setIsJoining(false);
    }, []);

    useEffect(() => {
        if (!isConnected || !socket) {
            setOpenLobbies([]);
            return;
        }

        emitGetOpenLobbies();
        onLobbyCreated(handleLobbyCreated);
        onLobbyError(handleLobbyError);
        onOpenLobbiesList(handleOpenLobbiesUpdate); // Use the same handler for initial list and updates
        onOpenLobbiesUpdate(handleOpenLobbiesUpdate);
        onJoinLobbyFailed(handleJoinFailed);

        return () => {
            offLobbyCreated();
            offLobbyError();
            offOpenLobbiesList();
            offOpenLobbiesUpdate();
            offJoinLobbyFailed();
        };
    }, [isConnected, socket, handleLobbyCreated, handleLobbyError, handleOpenLobbiesUpdate, handleJoinFailed]);

    const createLobby = useCallback(() => {
        if (!displayName.trim()) { setError('Please enter a display name.'); return; }
        if (!isConnected) { setError('Not connected to the server.'); return; }
        log('ACTION: Create Lobby', { displayName });
        setError('');
        emitCreateLobby({ displayName });
    }, [displayName, isConnected]);

    const joinLobby = useCallback((lobbyIdToJoin) => {
        if (!displayName.trim()) { setError('Please enter a display name to join.'); return; }
        if (!isConnected) { setError('Not connected to the server.'); return; }
        log('ACTION: Join Lobby', { lobbyId: lobbyIdToJoin, displayName });
        setError('');
        setIsJoining(true);
        emitJoinLobby({ lobbyId: lobbyIdToJoin, displayName });
    }, [displayName, isConnected]);

    const createAiGame = useCallback(() => {
        if (!displayName.trim()) { setError('Please enter a display name.'); return; }
        if (!isConnected) { setError('Not connected to the server.'); return; }
        log('ACTION: Create AI Game', { displayName });
        setError('');
        setIsJoining(true); // Re-use the joining flag to disable buttons
        emitCreateAiGame({ displayName });
    }, [displayName, isConnected]);

    const refreshLobbies = useCallback(() => {
        if (isConnected) {
            setError('');
            emitGetOpenLobbies();
        } else {
            setError("Not connected to server.");
        }
    }, [isConnected]);

    const resetLobbyState = useCallback(() => {
        setLobbyId(null);
        setError('');
        setIsJoining(false);
        setWaitingMessage('');
    }, []);

    const value = useMemo(() => ({
        displayName, setDisplayName,
        lobbyId,
        openLobbies,
        error,
        isJoining,
        waitingMessage,
        createLobby,
        joinLobby,
        refreshLobbies,
        resetLobbyState,
        createAiGame, // Expose the new function
    }), [displayName, lobbyId, openLobbies, error, isJoining, waitingMessage, createLobby, joinLobby, refreshLobbies, resetLobbyState, createAiGame]);

    return (
        <LobbyContext.Provider value={value}>
            {children}
        </LobbyContext.Provider>
    );
};