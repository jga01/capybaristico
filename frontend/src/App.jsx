import React, { useEffect, useState, useCallback, Suspense } from 'react';
import { Canvas } from '@react-three/fiber';
import { useLoader } from '@react-three/fiber';
import * as THREE from 'three';
import './App.css';
import {
  connectSocket,
  getSocket,
  emitCreateLobby,
  onLobbyCreated,
  onLobbyError,
  offLobbyCreated,
  offLobbyError,
  emitGetOpenLobbies,
  onOpenLobbiesList,
  onOpenLobbiesUpdate,
  offOpenLobbiesList,
  offOpenLobbiesUpdate,
  emitJoinLobby,
  onJoinLobbyFailed,
  offJoinLobbyFailed,
  onGameReady,
  offGameReady,
  onGameEvents,
  offGameEvents,
  onCommandRejected,
  offCommandRejected,
} from './services/socketService';
import { initLogger, log } from './services/loggingService';
import GameScreen from './components/GameScreen';
import { getGameAssetUrls } from './services/assetService';

const PreloadTrigger = ({ onLoaded }) => {
  useLoader(THREE.TextureLoader, getGameAssetUrls());
  useEffect(() => {
    onLoaded();
  }, [onLoaded]);
  return null;
};


const LobbyItem = ({ lobby, onJoin, isJoining, currentDisplayName }) => (
  <div style={{ border: '1px solid #444', margin: '8px auto', padding: '10px', background: '#2e2e2e', color: '#eee', borderRadius: '5px', width: '80%', maxWidth: '400px' }}>
    <p>Lobby ID: <span style={{ color: '#7fceff' }}>{lobby.lobbyId.substring(0, 8)}...</span></p>
    <p>Created by: <span style={{ color: '#a5d6a7' }}>{lobby.creatorDisplayName}</span></p>
    <p>Players: {lobby.currentPlayerCount}/2</p>
    <button
      onClick={() => onJoin(lobby.lobbyId)}
      disabled={isJoining || lobby.currentPlayerCount >= 2 || !currentDisplayName.trim()}
      style={{ backgroundColor: (isJoining || lobby.currentPlayerCount >= 2 || !currentDisplayName.trim()) ? '#555' : '#007bff', color: 'white', padding: '8px 15px', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
    >
      {isJoining ? 'Joining...' : 'Join Lobby'}
    </button>
  </div>
);

function App() {
  const [isConnected, setIsConnected] = useState(false);
  const [socketId, setSocketId] = useState(null);
  const [serverMessage, setServerMessage] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [appError, setAppError] = useState('');

  const [lobbyId, setLobbyId] = useState(null);
  const [isInLobbyOrGame, setIsInLobbyOrGame] = useState(false);
  const [openLobbies, setOpenLobbies] = useState([]);
  const [isJoiningLobby, setIsJoiningLobby] = useState(false);

  const [gameId, setGameId] = useState(null);
  const [playerId, setPlayerId] = useState(null);
  const [initialGameState, setInitialGameState] = useState(null);
  const [eventBatch, setEventBatch] = useState(null);
  const [commandErrorMsg, setCommandErrorMsg] = useState('');
  const [isGameOver, setIsGameOver] = useState(false);
  const [gameOverMessage, setGameOverMessage] = useState('');

  const [isPreloading, setIsPreloading] = useState(false);
  const [assetsLoaded, setAssetsLoaded] = useState(false);

  const resetGameRelatedState = useCallback(() => {
    setGameId(null);
    setPlayerId(null);
    setInitialGameState(null);
    setEventBatch(null);
    setIsInLobbyOrGame(false);
    setIsGameOver(false);
    setCommandErrorMsg('');
    setGameOverMessage('');
    setIsPreloading(false);
    setAssetsLoaded(false);
    if (getSocket() && getSocket().connected) {
      emitGetOpenLobbies();
    }
  }, []);

  const handleConnect = useCallback(() => {
    setIsConnected(true);
    const s = getSocket();
    if (s) setSocketId(s.id || null);
    setServerMessage(''); setAppError(''); setCommandErrorMsg('');
    emitGetOpenLobbies();
  }, []);

  const handleDisconnect = useCallback((reason) => {
    setIsConnected(false); setSocketId(null);
    setServerMessage(`Disconnected: ${reason}. Please refresh the page or check server status.`);
    resetGameRelatedState();
    setIsInLobbyOrGame(false);
  }, [resetGameRelatedState]);

  const handleConnectError = useCallback((error) => {
    setIsConnected(false); setSocketId(null);
    setServerMessage(`Connection Error: ${error.message || 'Server unavailable'}. Please refresh or check server.`);
  }, []);

  const handleConnectionAck = useCallback((message) => setServerMessage(message), []);

  const handleLobbyCreated = useCallback((data) => {
    setLobbyId(data.lobbyId); setIsInLobbyOrGame(true);
    setServerMessage(data.message || `Lobby ${data.lobbyId} created. Waiting...`);
    setAppError('');
  }, []);

  const handleLobbyError = useCallback((errorMessage) => {
    setAppError(errorMessage); setIsJoiningLobby(false);
  }, []);

  const handleOpenLobbiesList = useCallback((lobbies) => setOpenLobbies(lobbies || []), []);
  const handleOpenLobbiesUpdate = useCallback((lobbies) => setOpenLobbies(lobbies || []), []);

  const handleJoinLobbyFailed = useCallback((errorMessage) => {
    setAppError(errorMessage); setIsJoiningLobby(false);
  }, []);

  const handleGameReady = useCallback((initialState) => {
    initLogger();
    log('EVENT: Game is ready. Received initial state.', initialState);

    if (!assetsLoaded) {
      setIsPreloading(true);
    }

    setInitialGameState(initialState);
    setGameId(initialState.gameId);
    setPlayerId(initialState.viewingPlayerPerspectiveId);
    setIsInLobbyOrGame(true);
    setLobbyId(null);
    setAppError('');
    setServerMessage(initialState.message || `Game ${initialState.gameId} started!`);
    setIsJoiningLobby(false);
    setCommandErrorMsg('');
    setIsGameOver(false);
  }, [assetsLoaded]);

  const handleGameEvents = useCallback((events) => {
    if (Array.isArray(events)) {
      setEventBatch(events);
      const gameOverEvent = events.find(e => e.eventType === 'GAME_OVER');
      if (gameOverEvent) {
        setIsGameOver(true);
        setGameOverMessage(gameOverEvent.reason || "The game has ended.");
      }
    }
  }, []);

  const handleCommandRejected = useCallback((message) => {
    setCommandErrorMsg(message || "Your action was rejected by the server.");
  }, []);

  useEffect(() => {
    const currentSocket = connectSocket();
    if (currentSocket) {
      if (currentSocket.connected && !socketId) {
        setIsConnected(true);
        setSocketId(currentSocket.id || null);
        emitGetOpenLobbies();
      }

      currentSocket.on('connect', handleConnect);
      currentSocket.on('disconnect', handleDisconnect);
      currentSocket.on('connect_error', handleConnectError);
      currentSocket.on('connection_ack', handleConnectionAck);
      onLobbyCreated(handleLobbyCreated); onLobbyError(handleLobbyError);
      onOpenLobbiesList(handleOpenLobbiesList); onOpenLobbiesUpdate(handleOpenLobbiesUpdate);
      onJoinLobbyFailed(handleJoinLobbyFailed);
      onGameReady(handleGameReady);
      onGameEvents(handleGameEvents);
      onCommandRejected(handleCommandRejected);
    }
    return () => {
      if (currentSocket) {
        currentSocket.off('connect', handleConnect);
        currentSocket.off('disconnect', handleDisconnect);
        currentSocket.off('connect_error', handleConnectError);
        currentSocket.off('connection_ack', handleConnectionAck);
        offLobbyCreated(); offLobbyError();
        offOpenLobbiesList(); offOpenLobbiesUpdate();
        offJoinLobbyFailed();
        offGameReady();
        offGameEvents();
        offCommandRejected();
      }
    };
  }, [
    socketId, handleConnect, handleDisconnect, handleConnectError, handleConnectionAck,
    handleLobbyCreated, handleLobbyError, handleOpenLobbiesList, handleOpenLobbiesUpdate,
    handleJoinLobbyFailed, handleGameReady, handleGameEvents, handleCommandRejected
  ]);

  const handleCreateLobby = () => {
    if (!displayName.trim()) { setAppError('Please enter a display name.'); return; }
    if (!isConnected) { setAppError('Not connected to the server.'); return; }
    log('ACTION: Create Lobby', { displayName });
    setAppError(''); emitCreateLobby({ displayName });
  };

  const handleJoinLobby = (lobbyIdToJoin) => {
    if (!displayName.trim()) { setAppError('Please enter a display name to join a lobby.'); return; }
    if (!isConnected) { setAppError('Not connected to the server.'); return; }
    log('ACTION: Join Lobby', { lobbyId: lobbyIdToJoin, displayName });
    setAppError(''); setIsJoiningLobby(true); emitJoinLobby({ lobbyId: lobbyIdToJoin, displayName });
  };

  const handleReturnToLobby = () => {
    resetGameRelatedState();
    setServerMessage("Returned to lobby selection.");
  };


  if (!isConnected) {
    return (
      <div className="App" style={{ color: '#ddd', textAlign: 'center', paddingTop: '50px' }}>
        <p>{serverMessage.includes("Connection Error") ? serverMessage : "Connecting to CapyCards server..."}</p>
        {serverMessage.includes("Connection Error") && <p>Please ensure the server is running and refresh the page.</p>}
      </div>
    );
  }

  if (isPreloading && !assetsLoaded) {
    return (
      <div className="App game-active" style={{ backgroundColor: '#1a1d21' }}>
        <div style={{ color: 'white', textAlign: 'center', position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', zIndex: 1 }}>
          <h2>Loading Game Assets...</h2>
        </div>
        <Canvas style={{ position: 'absolute', width: '1px', height: '1px', top: -9999, left: -9999 }}>
          <Suspense fallback={null}>
            <PreloadTrigger onLoaded={() => {
              log('Assets loaded successfully.');
              setAssetsLoaded(true);
              setIsPreloading(false);
            }} />
          </Suspense>
        </Canvas>
      </div>
    );
  }

  if (isInLobbyOrGame && gameId && playerId && initialGameState && !isGameOver && assetsLoaded) {
    return (
      <div className="App game-active" style={{ width: '100vw', height: '100vh', padding: 0, margin: 0, overflow: 'hidden', position: 'relative', background: '#1a1d21' }}>
        <GameScreen
          initialGameState={initialGameState}
          playerId={playerId}
          gameId={gameId}
          eventBatch={eventBatch}
          commandError={commandErrorMsg}
          clearCommandError={() => setCommandErrorMsg('')}
        />
      </div>
    );
  }

  if (isGameOver && gameId) {
    return (
      <div className="App">
        <div className="game-over-screen">
          <h2>Game Over!</h2>
          <p>{gameOverMessage}</p>
          <button onClick={handleReturnToLobby} className="action-button">Return to Lobbies</button>
        </div>
      </div>
    );
  }

  return (
    <div className="App">
      <header className="App-header">
        <h1>CapyCards</h1>
        <p style={{ fontSize: '0.8em' }}>Socket: <span style={{ color: 'lightgreen' }}>Connected (ID: {socketId || 'N/A'})</span></p>
        {serverMessage && <p style={{ fontSize: '0.9em', fontStyle: 'italic' }}>Server: {serverMessage}</p>}
        {appError && <p style={{ color: 'red' }}>Error: {appError}</p>}
      </header>
      <main>
        {isInLobbyOrGame && lobbyId ? (
          <div>
            <h2>In Your Lobby: <span style={{ color: '#7fceff' }}>{lobbyId.substring(0, 8)}...</span></h2>
            <p>Your Display Name: <span style={{ color: '#a5d6a7' }}>{displayName}</span></p>
            <p style={{ color: '#ffeb3b', fontStyle: 'italic' }}>Waiting for an opponent...</p>
          </div>
        ) : (
          <>
            <div style={{ marginBottom: '20px', padding: '15px', background: '#2a2a2e', borderRadius: '8px' }}>
              <input
                type="text"
                placeholder="Enter your display name"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
              <button
                onClick={handleCreateLobby}
                disabled={!displayName.trim() || isJoiningLobby}
              >
                Create Lobby
              </button>
            </div>
            <hr style={{ borderColor: '#444' }} />
            <h2>Open Lobbies</h2>
            <button
              onClick={() => { setAppError(''); if (isConnected) emitGetOpenLobbies(); else setAppError("Not connected to server."); }}
              disabled={isJoiningLobby || !isConnected}
            >
              Refresh Lobbies
            </button>
            {openLobbies.length === 0 ? <p>No open lobbies. Create one or refresh!</p> : openLobbies.map((lobby) => (
              <LobbyItem key={lobby.lobbyId} lobby={lobby} onJoin={handleJoinLobby} isJoining={isJoiningLobby} currentDisplayName={displayName} />
            ))}
          </>
        )}
      </main>
    </div>
  );
}

export default App;