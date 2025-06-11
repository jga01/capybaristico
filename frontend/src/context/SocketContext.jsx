import React, { createContext, useContext, useEffect, useState, useMemo } from 'react';
import { connectSocket, getSocket } from '../services/socketService';

const SocketContext = createContext(null);

export const useSocket = () => {
    return useContext(SocketContext);
};

export const SocketProvider = ({ children }) => {
    const [socket, setSocket] = useState(null);
    const [isConnected, setIsConnected] = useState(false);

    useEffect(() => {
        const s = connectSocket();
        setSocket(s);
        setIsConnected(s.connected);

        const onConnect = () => setIsConnected(true);
        const onDisconnect = () => setIsConnected(false);

        s.on('connect', onConnect);
        s.on('disconnect', onDisconnect);

        return () => {
            s.off('connect', onConnect);
            s.off('disconnect', onDisconnect);
        };
    }, []);

    const value = useMemo(() => ({
        socket,
        isConnected
    }), [socket, isConnected]);

    return (
        <SocketContext.Provider value={value}>
            {children}
        </SocketContext.Provider>
    );
};