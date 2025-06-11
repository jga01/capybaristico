import React, { useEffect, useState, Suspense } from 'react';
import { useLoader } from '@react-three/fiber';
import { Canvas } from '@react-three/fiber';
import * as THREE from 'three';
import './App.css';

import { useSocket } from './context/SocketContext';
import { useLobby } from './context/LobbyContext';
import { useGame } from './context/GameContext';

import GameScreen from './components/GameScreen';
import { getGameAssetUrls } from './services/assetService';
import { log } from './services/loggingService';


// --- UI Components ---

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

const LobbyScreen = () => {
  const {
    displayName, setDisplayName,
    lobbyId, openLobbies, error,
    isJoining, waitingMessage,
    createLobby, joinLobby, refreshLobbies
  } = useLobby();

  return (
    <div className="App">
      <header className="App-header">
        <h1>CapyCards</h1>
        {error && <p style={{ color: 'red' }}>Error: {error}</p>}
      </header>
      <main>
        {lobbyId ? (
          <div>
            <h2>In Your Lobby: <span style={{ color: '#7fceff' }}>{lobbyId.substring(0, 8)}...</span></h2>
            <p>Your Display Name: <span style={{ color: '#a5d6a7' }}>{displayName}</span></p>
            <p style={{ color: '#ffeb3b', fontStyle: 'italic' }}>{waitingMessage || 'Waiting for an opponent...'}</p>
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
              <button onClick={createLobby} disabled={!displayName.trim() || isJoining}>
                Create Lobby
              </button>
            </div>
            <hr style={{ borderColor: '#444' }} />
            <h2>Open Lobbies</h2>
            <button onClick={refreshLobbies} disabled={isJoining}>
              Refresh Lobbies
            </button>
            {openLobbies.length === 0 ? <p>No open lobbies. Create one or refresh!</p> : openLobbies.map((lobby) => (
              <LobbyItem key={lobby.lobbyId} lobby={lobby} onJoin={joinLobby} isJoining={isJoining} currentDisplayName={displayName} />
            ))}
          </>
        )}
      </main>
    </div>
  );
};

const GameOverScreen = ({ onReturnToLobby }) => {
  const { gameOverMessage } = useGame();
  return (
    <div className="App">
      <div className="game-over-screen">
        <h2>Game Over!</h2>
        <p>{gameOverMessage}</p>
        <button onClick={onReturnToLobby} className="action-button">Return to Lobbies</button>
      </div>
    </div>
  );
}

// --- Main App Component ---

function App() {
  const { isConnected } = useSocket();
  const { resetLobbyState } = useLobby();
  const { gameId, initialGameState, isGameOver, resetGameState } = useGame();

  const [isPreloading, setIsPreloading] = useState(false);
  const [assetsLoaded, setAssetsLoaded] = useState(false);

  // Trigger preloading when a game is ready but assets are not yet loaded
  useEffect(() => {
    if (gameId && !assetsLoaded) {
      setIsPreloading(true);
    }
  }, [gameId, assetsLoaded]);

  const handleReturnToLobby = () => {
    resetGameState();
    resetLobbyState();
    setAssetsLoaded(false); // Reset for the next game
    setIsPreloading(false);
  };

  if (!isConnected) {
    return (
      <div className="App" style={{ color: '#ddd', textAlign: 'center', paddingTop: '50px' }}>
        <p>Connecting to CapyCards server...</p>
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

  if (gameId && initialGameState && assetsLoaded) {
    if (isGameOver) {
      return <GameOverScreen onReturnToLobby={handleReturnToLobby} />;
    }
    return (
      <div className="App game-active" style={{ width: '100vw', height: '100vh', padding: 0, margin: 0, overflow: 'hidden', position: 'relative', background: '#1a1d21' }}>
        <GameScreen />
      </div>
    );
  }

  return <LobbyScreen />;
}

export default App;