import React, { useEffect, useState, useCallback } from 'react';
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
  onGameStart,
  offJoinLobbyFailed,
  offGameStart,
  onGameStateUpdate,
  offGameStateUpdate,
  onActionError,
  offActionError
} from './services/socketService';
import GameScreen from './components/GameScreen';

const LobbyItem = ({ lobby, onJoin, isJoining, currentDisplayName }) => ( // Added currentDisplayName
  <div style={{ border: '1px solid #444', margin: '8px auto', padding: '10px', background: '#2e2e2e', color: '#eee', borderRadius: '5px', width: '80%', maxWidth: '400px' }}>
    <p>Lobby ID: <span style={{ color: '#7fceff' }}>{lobby.lobbyId.substring(0, 8)}...</span></p>
    <p>Created by: <span style={{ color: '#a5d6a7' }}>{lobby.creatorDisplayName}</span></p>
    <p>Players: {lobby.currentPlayerCount}/2</p>
    <button
      onClick={() => onJoin(lobby.lobbyId)}
      disabled={isJoining || lobby.currentPlayerCount >= 2 || !currentDisplayName.trim()} // Disable if no display name
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
  const [gameState, setGameState] = useState(null);
  const [actionErrorMsg, setActionErrorMsg] = useState('');
  const [isGameOver, setIsGameOver] = useState(false);

  const resetGameRelatedState = useCallback(() => {
    setGameId(null);
    setPlayerId(null);
    setGameState(null);
    setIsInLobbyOrGame(false);
    setIsGameOver(false);
    setActionErrorMsg('');
    if (getSocket() && getSocket().connected) {
      emitGetOpenLobbies();
    }
  }, []);


  const handleConnect = useCallback(() => {
    setIsConnected(true);
    const s = getSocket();
    if (s) setSocketId(s.id || null);
    setServerMessage(''); setAppError(''); setActionErrorMsg('');
    emitGetOpenLobbies(); // Fetch lobbies on fresh connect
    // resetGameRelatedState(); // resetGameRelatedState is usually for after game ends or disconnect
  }, []); // Removed resetGameRelatedState from here to avoid loop if it emits

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

  const handleOpenLobbiesList = useCallback((lobbies) => { setOpenLobbies(lobbies || []); console.log("Lobbies list received:", lobbies) }, []);
  const handleOpenLobbiesUpdate = useCallback((lobbies) => { setOpenLobbies(lobbies || []); console.log("Lobbies list updated:", lobbies) }, []);

  const handleJoinLobbyFailed = useCallback((errorMessage) => {
    setAppError(errorMessage); setIsJoiningLobby(false);
  }, []);

  const handleGameStart = useCallback((initialGameState) => {
    setGameState(initialGameState);
    setGameId(initialGameState.gameId);
    if (initialGameState.viewingPlayerPerspectiveId) {
      setPlayerId(initialGameState.viewingPlayerPerspectiveId);
    } else {
      console.error("Server did not provide viewingPlayerPerspectiveId in game_start!");
      setAppError("Error starting game: Missing player perspective. Please try rejoining.");
      resetGameRelatedState();
      return;
    }
    setIsInLobbyOrGame(true);
    setLobbyId(null);
    setAppError('');
    setServerMessage(initialGameState.message || `Game ${initialGameState.gameId} started!`);
    setIsJoiningLobby(false);
    setActionErrorMsg('');
    setIsGameOver(false);
  }, [resetGameRelatedState]);

  const handleGameStateUpdate = useCallback((newGameState) => {
    setGameState(newGameState);
    if (newGameState.viewingPlayerPerspectiveId && (!playerId || playerId !== newGameState.viewingPlayerPerspectiveId)) {
      setPlayerId(newGameState.viewingPlayerPerspectiveId);
    }
    if (newGameState.currentGameState && newGameState.currentGameState.includes("GAME_OVER")) {
      console.log("GAME OVER from GameStateUpdate:", newGameState.message);
      setIsGameOver(true);
      setServerMessage(newGameState.message || "The game has ended.");
    } else {
      setIsGameOver(false);
    }
    setActionErrorMsg('');
    if (newGameState.message) { // Prioritize new server message
      setServerMessage(newGameState.message);
    }
  }, [playerId]);

  const handleActionError = useCallback((errorData) => {
    console.error('Game Action Error from server:', errorData);
    setActionErrorMsg(errorData.message || "An unknown game action error occurred.");
    if (errorData.currentGameState && errorData.currentGameState.includes("GAME_OVER")) {
      setIsGameOver(true);
      setServerMessage(errorData.message || "The game ended due to an error or specific condition.");
      if (errorData.player1State && errorData.player2State) {
        setGameState(errorData);
      }
    }
  }, []);

  useEffect(() => {
    const currentSocket = connectSocket();
    if (currentSocket) {
      setIsConnected(currentSocket.connected);
      if (currentSocket.connected && !socketId) { // Set socketId if connected and not already set
        setSocketId(currentSocket.id || null);
        emitGetOpenLobbies();
      }

      currentSocket.on('connect', handleConnect);
      currentSocket.on('disconnect', handleDisconnect);
      currentSocket.on('connect_error', handleConnectError);
      currentSocket.on('connection_ack', handleConnectionAck);
      onLobbyCreated(handleLobbyCreated); onLobbyError(handleLobbyError);
      onOpenLobbiesList(handleOpenLobbiesList); onOpenLobbiesUpdate(handleOpenLobbiesUpdate);
      onJoinLobbyFailed(handleJoinLobbyFailed); onGameStart(handleGameStart);
      onGameStateUpdate(handleGameStateUpdate); onActionError(handleActionError);
    }
    return () => {
      if (currentSocket) {
        currentSocket.off('connect', handleConnect); currentSocket.off('disconnect', handleDisconnect);
        currentSocket.off('connect_error', handleConnectError); currentSocket.off('connection_ack', handleConnectionAck);
        offLobbyCreated(); offLobbyError(); offOpenLobbiesList(); offOpenLobbiesUpdate();
        offJoinLobbyFailed(); offGameStart(); offGameStateUpdate(); offActionError();
      }
    };
  }, [handleConnect, handleDisconnect, handleConnectError, handleConnectionAck,
    handleLobbyCreated, handleLobbyError, handleOpenLobbiesList, handleOpenLobbiesUpdate,
    handleJoinLobbyFailed, handleGameStart, handleGameStateUpdate, handleActionError, socketId]); // Added socketId

  const handleCreateLobby = () => {
    if (!displayName.trim()) { setAppError('Please enter a display name.'); return; }
    if (!isConnected) { setAppError('Not connected to the server.'); return; }
    setAppError(''); emitCreateLobby({ displayName });
  };

  const handleJoinLobby = (lobbyIdToJoin) => {
    if (!displayName.trim()) { setAppError('Please enter a display name to join a lobby.'); return; }
    if (!isConnected) { setAppError('Not connected to the server.'); return; }
    setAppError(''); setIsJoiningLobby(true); emitJoinLobby({ lobbyId: lobbyIdToJoin, displayName });
  };

  const handleReturnToLobby = () => {
    resetGameRelatedState();
    setServerMessage("Returned to lobby selection.");
  };

  if (!isConnected && !serverMessage.includes("Connection Error")) { // Show connecting unless there's a persistent error
    return (
      <div className="App" style={{ color: '#ddd', textAlign: 'center', paddingTop: '50px' }}>
        <p>Connecting to CapyCards server...</p>
      </div>
    );
  }
  if (!isConnected && serverMessage.includes("Connection Error")) {
    return (
      <div className="App" style={{ color: '#ddd', textAlign: 'center', paddingTop: '50px' }}>
        <p style={{ color: 'red' }}>{serverMessage}</p>
        <p>Please ensure the server is running and refresh the page.</p>
      </div>
    );
  }


  if (isInLobbyOrGame && gameId && playerId && gameState && !isGameOver) {
    console.log(`[App.jsx rendering GameScreen] playerId: ${playerId}, gameId: ${gameId}`);
    return (
      <div className="App game-active" style={{ width: '100vw', height: '100vh', padding: 0, margin: 0, overflow: 'hidden', position: 'relative', background: '#1a1d21' }}>
        {actionErrorMsg && !isGameOver && <p className="app-level-error-bar">Action Error: {actionErrorMsg}</p>}
        <GameScreen gameState={gameState} playerId={playerId} gameId={gameId} />
      </div>
    );
  }

  if (isGameOver && gameId) {
    return (
      <div className="App">
        <div className="game-over-screen">
          <h2>Game Over!</h2>
          <p>{serverMessage || gameState?.message || "The game has concluded."}</p>
          <button onClick={handleReturnToLobby} className="action-button">Return to Lobbies</button>
        </div>
      </div>
    );
  }

  if (isInLobbyOrGame && lobbyId) {
    return (
      <div className="App">
        <header className="App-header"><h1>CapyCards</h1></header>
        <h2>In Your Lobby: <span style={{ color: '#7fceff' }}>{lobbyId.substring(0, 8)}...</span></h2>
        <p>Your Display Name: <span style={{ color: '#a5d6a7' }}>{displayName}</span></p>
        <p style={{ color: '#ffeb3b', fontStyle: 'italic' }}>Waiting for an opponent...</p>
        {serverMessage && !serverMessage.includes("Lobby created") && <p>Server: {serverMessage}</p>}
        {appError && <p style={{ color: 'red' }}>Error: {appError}</p>}
      </div>
    );
  }

  return (
    <div className="App">
      <header className="App-header">
        <h1>CapyCards (3D R3F Test)</h1>
        <p style={{ fontSize: '0.8em' }}>Socket: {isConnected ? <span style={{ color: 'lightgreen' }}>Connected (ID: {socketId || 'N/A'})</span> : <span style={{ color: 'red' }}>Disconnected</span>}</p>
        {serverMessage && <p style={{ fontSize: '0.9em', fontStyle: 'italic' }}>Server: {serverMessage}</p>}
        {appError && <p style={{ color: 'red' }}>Error: {appError}</p>}
      </header>
      <main>
        <div style={{ marginBottom: '20px', padding: '15px', background: '#2a2a2e', borderRadius: '8px' }}>
          <input
            type="text"
            placeholder="Enter your display name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            style={{ padding: '10px', marginRight: '10px', borderRadius: '4px', border: '1px solid #555', background: '#333', color: 'white' }}
          />
          <button
            onClick={handleCreateLobby}
            disabled={!displayName.trim() || isJoiningLobby}
            style={{ backgroundColor: (!displayName.trim() || isJoiningLobby) ? '#555' : '#007bff', color: 'white', padding: '10px 15px', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
          >
            Create Lobby
          </button>
        </div>
        <hr style={{ borderColor: '#444' }} />
        <h2>Open Lobbies</h2>
        <button
          onClick={() => { setAppError(''); if (isConnected) emitGetOpenLobbies(); else setAppError("Not connected to server."); }}
          disabled={isJoiningLobby || !isConnected}
          style={{ marginBottom: '10px', backgroundColor: (isJoiningLobby || !isConnected) ? '#555' : '#6c757d', color: 'white', padding: '8px 12px', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
        >
          Refresh Lobbies
        </button>
        {openLobbies.length === 0 ? <p>No open lobbies. Create one or refresh!</p> : openLobbies.map((lobby) => (
          <LobbyItem key={lobby.lobbyId} lobby={lobby} onJoin={handleJoinLobby} isJoining={isJoiningLobby} currentDisplayName={displayName} />
        ))}
      </main>
    </div>
  );
}

export default App;