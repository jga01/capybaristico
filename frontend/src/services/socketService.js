import { io } from 'socket.io-client';

// The URL of your backend Socket.IO server
const SOCKET_SERVER_URL = 'http://localhost:9092';

let socket = null; 

export const connectSocket = () => {
    if (socket && socket.connected) {
        console.log('Socket already connected.');
        return socket;
    }

    console.log('Attempting to connect to Socket.IO server at', SOCKET_SERVER_URL);
    socket = io(SOCKET_SERVER_URL, {});

    socket.on('connect', () => {
        console.log('Socket connected successfully! ID:', socket.id);
    });

    socket.on('disconnect', (reason) => {
        console.log('Socket disconnected:', reason);
        if (reason === 'io server disconnect') {
            socket.connect(); 
        }
    });

    socket.on('connect_error', (error) => {
        console.error('Socket connection error:', error);
    });

    socket.on('connection_ack', (message) => {
        console.log('Server acknowledgement:', message);
    });

    return socket;
};

export const disconnectSocket = () => {
    if (socket && socket.connected) {
        console.log('Disconnecting socket...');
        socket.disconnect();
        socket = null;
    }
};

export const getSocket = () => {
    if (!socket || !socket.connected) {
        console.warn('Socket not connected or not initialized. Call connectSocket() first.');
    }
    return socket;
};

// --- LOBBY ACTIONS ---
export const emitCreateLobby = (data) => {
    const currentSocket = getSocket();
    if (currentSocket && currentSocket.connected) {
        currentSocket.emit('create_lobby', data, (ack) => {
            console.log('Create Lobby Ack:', ack);
            if (ack && !ack.success) {
                alert(`Lobby creation error: ${ack.message || 'Unknown error'}`);
            }
        });
    } else {
        console.error('Socket not connected. Cannot emit create_lobby.');
        alert('Not connected to server. Please try again.');
    }
};

export const onLobbyCreated = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('lobby_created', callback);
};
export const offLobbyCreated = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('lobby_created');
};

export const onLobbyError = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('lobby_error', callback);
};
export const offLobbyError = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('lobby_error');
};

export const emitGetOpenLobbies = () => {
    const currentSocket = getSocket();
    if (currentSocket && currentSocket.connected) {
        currentSocket.emit('get_open_lobbies');
    } else {
        console.error('Socket not connected. Cannot emit get_open_lobbies.');
    }
};

export const onOpenLobbiesList = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('open_lobbies_list', callback);
};
export const offOpenLobbiesList = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('open_lobbies_list');
};

export const onOpenLobbiesUpdate = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('open_lobbies_update', callback);
};
export const offOpenLobbiesUpdate = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('open_lobbies_update');
};

export const emitJoinLobby = (data) => {
    const currentSocket = getSocket();
    if (currentSocket && currentSocket.connected) {
        currentSocket.emit('join_lobby', data, (ack) => {
            console.log('Join Lobby Ack:', ack);
            if (ack && !ack.success) {
                 alert(`Join lobby error: ${ack.message || 'Unknown error from ack'}`);
            }
        });
    } else {
        console.error('Socket not connected. Cannot emit join_lobby.');
        alert('Not connected to server. Please try again.');
    }
};

export const onJoinLobbyFailed = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('join_lobby_failed', callback);
};
export const offJoinLobbyFailed = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('join_lobby_failed');
};

// --- GAME START/UPDATE LISTENERS ---
export const onGameStart = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('game_start', callback);
};
export const offGameStart = () => {
     const currentSocket = getSocket();
     if (currentSocket) currentSocket.off('game_start');
};

export const onGameStateUpdate = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.on('game_state_update', callback);
};
export const offGameStateUpdate = () => {
     const currentSocket = getSocket();
     if (currentSocket) currentSocket.off('game_state_update');
};

/**
 * Emits a generic 'game_action' to the server.
 * @param {object} actionData - The action data, e.g., 
 *  { gameId, playerId, actionType: 'PLAY_CARD', handCardIndex, targetFieldSlot }
 *  { gameId, playerId, actionType: 'END_TURN' }
 */
export const emitGameAction = (actionData) => {
    const currentSocket = getSocket();
    if (currentSocket && currentSocket.connected) {
        console.log('Emitting game_action:', actionData);
        currentSocket.emit('game_action', actionData, (ack) => {
            // The backend currently doesn't send an ack for 'game_action' itself,
            // it sends 'game_state_update' or 'action_error'.
            // This ack callback here is mostly for socket.io's own ack mechanism if enabled.
            // For now, we can just log it.
            if (ack) console.log('Game Action Ack:', ack);
        });
    } else {
        console.error('Socket not connected. Cannot emit game_action.');
        alert('Not connected to server. Action not sent.');
    }
};

// Listener for action errors (if backend sends specific error events for actions)
export const onActionError = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) {
        currentSocket.on('action_error', callback); // Assuming backend emits 'action_error'
    }
};
export const offActionError = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('action_error');
};