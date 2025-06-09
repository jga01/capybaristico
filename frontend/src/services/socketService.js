// frontend/src/services/socketService.js

import { io } from 'socket.io-client';
import { log } from './loggingService';

// The hardcoded URL is removed. The connection will now be established
// relative to the host that served the web page, which is what we want
// for the reverse proxy to work correctly.
// const SOCKET_SERVER_URL = 'http://0.0.0.0:9092'; // DELETED

let socket = null;

export const connectSocket = () => {
    if (socket && socket.connected) {
        console.log('Socket already connected.');
        return socket;
    }

    // This now connects to the same host that served the page, using a specific path.
    // On an HTTPS page, this automatically attempts a secure WebSocket (wss://) connection.
    // This path '/socket.io/' must match the 'location' block in your Nginx config.
    console.log('Attempting to connect to Socket.IO server via current host...');
    socket = io({
        path: '/socket.io/'
    });

    socket.on('connect', () => {
        const message = 'Socket connected successfully!';
        console.log(`${message} ID:`, socket.id);
        log(message, { id: socket.id });
    });

    socket.on('disconnect', (reason) => {
        const message = 'Socket disconnected';
        console.log(message, reason);
        log(message, { reason });
        socket = null;
        // The auto-reconnect logic in socket.io-client is usually sufficient.
        // Manually calling connect() here can lead to loops under some conditions.
    });

    socket.on('connect_error', (error) => {
        const message = 'Socket connection error';
        console.error(message, error);
        log(message, { error: error.message });
    });

    socket.on('connection_ack', (message) => {
        console.log('Server acknowledgement:', message);
        log('ON connection_ack', { message });
    });

    return socket;
};

export const disconnectSocket = () => {
    if (socket && socket.connected) {
        log('ACTION: Disconnecting socket...');
        socket.disconnect();
        socket = null;
    }
};

export const getSocket = () => {
    if (!socket) {
        console.warn('Socket not initialized. Call connectSocket() first.');
    }
    return socket;
};

// --- LOBBY ACTIONS ---
export const emitCreateLobby = (data) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket.connected) {
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
    if (!currentSocket) return;
    if (currentSocket.connected) {
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
    if (!currentSocket) return;
    if (currentSocket.connected) {
        log('EMIT join_lobby', data);
        currentSocket.emit('join_lobby', data, (ack) => {
            log('ACK for join_lobby', ack);
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

// --- GAME STATE & EVENT LISTENERS ---
// Listener for the initial full game state when a game is ready
export const onGameReady = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) {
        currentSocket.on('game_ready', (data) => {
            log('ON game_ready', data);
            callback(data);
        });
    }
};
export const offGameReady = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('game_ready');
};

// Listener for the stream of events during gameplay
export const onGameEvents = (callback) => {
    const currentSocket = getSocket();
    if (currentSocket) {
        currentSocket.on('game_events', (data) => {
            log('ON game_events', data);
            callback(data);
        });
    }
};
export const offGameEvents = () => {
    const currentSocket = getSocket();
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
    if (currentSocket.connected) {
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
    if (currentSocket) {
        currentSocket.on('command_rejected', (data) => {
            log('ON command_rejected', data);
            callback(data);
        });
    }
};
export const offCommandRejected = () => {
    const currentSocket = getSocket();
    if (currentSocket) currentSocket.off('command_rejected');
};