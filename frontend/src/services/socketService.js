import { io } from 'socket.io-client';
import { log } from './loggingService';

// The URL of your backend Socket.IO server
const SOCKET_SERVER_URL = 'http://192.168.96.44:9092';

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
        socket = null;
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
    if (!currentSocket) return;
    if (currentSocket && currentSocket.connected) {
        log('EMIT create_lobby', data);
        currentSocket.emit('create_lobby', data, (ack) => {
            log('ACK for create_lobby', ack);
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
    if (!currentSocket) return;
    if (currentSocket) currentSocket.on('lobby_created', callback);
};
export const offLobbyCreated = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('lobby_created');
};

export const onLobbyError = (callback) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.on('lobby_error', callback);
};
export const offLobbyError = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('lobby_error');
};

export const emitGetOpenLobbies = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket && currentSocket.connected) {
        currentSocket.emit('get_open_lobbies');
    } else {
        console.error('Socket not connected. Cannot emit get_open_lobbies.');
    }
};

export const onOpenLobbiesList = (callback) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.on('open_lobbies_list', callback);
};
export const offOpenLobbiesList = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('open_lobbies_list');
};

export const onOpenLobbiesUpdate = (callback) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.on('open_lobbies_update', callback);
};
export const offOpenLobbiesUpdate = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('open_lobbies_update');
};

export const emitJoinLobby = (data) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
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
    if (!currentSocket) return;
    if (currentSocket) currentSocket.on('join_lobby_failed', callback);
};
export const offJoinLobbyFailed = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('join_lobby_failed');
};

// --- GAME STATE & EVENT LISTENERS ---
// Listener for the initial full game state when a game is ready
export const onGameReady = (callback) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) {
        currentSocket.on('game_ready', (data) => {
            // --- LOGGING ---
            log('ON game_ready', data);
            callback(data);
        });
    }
};
export const offGameReady = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('game_ready');
};

// Listener for the stream of events during gameplay
export const onGameEvents = (callback) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) {
        currentSocket.on('game_events', (data) => {
            // --- LOGGING ---
            log('ON game_events', data);
            callback(data);
        });
    }
};
export const offGameEvents = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('game_events');
};


/**
 * Emits a 'game_command' to the server.
 * @param {object} commandData - The command data, including commandType
 * e.g., { gameId, playerId, commandType: 'PLAY_CARD', handCardIndex, targetFieldSlot }
 * e.g., { gameId, playerId, commandType: 'END_TURN' }
 */
export const emitGameCommand = (commandData) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket && currentSocket.connected) {
        // --- LOGGING ---
        log('EMIT game_command', commandData);
        currentSocket.emit('game_command', commandData);
    } else {
        console.error('Socket not connected. Cannot emit game_command.');
        alert('Not connected to server. Action not sent.');
    }
};

// Listener for when the server rejects a command
export const onCommandRejected = (callback) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) {
        currentSocket.on('command_rejected', (data) => {
            // --- LOGGING ---
            log('ON command_rejected', data);
            callback(data);
        });
    }
};
export const offCommandRejected = () => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket) currentSocket.off('command_rejected');
};