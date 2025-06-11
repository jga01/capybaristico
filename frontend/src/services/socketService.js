import { io } from 'socket.io-client';
import { log } from './loggingService';

const SOCKET_SERVER_URL = import.meta.env.VITE_SOCKET_SERVER_URL;

let socket = null;

export const connectSocket = () => {
    if (socket && socket.connected) {
        console.log('Socket already connected.');
        return socket;
    }

    console.log(`Attempting to connect to Socket.IO server at ${SOCKET_SERVER_URL}...`);
    // When connecting to a full production URL, you need to specify the path
    // so the reverse proxy can correctly route the request.
    socket = io(SOCKET_SERVER_URL, {
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
export const emitCreateAiGame = (data) => {
    const currentSocket = getSocket();
    if (!currentSocket) return;
    if (currentSocket.connected) {
        log('EMIT create_ai_game', data);
        currentSocket.emit('create_ai_game', data);
    } else {
        console.error('Socket not connected. Cannot emit create_ai_game.');
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