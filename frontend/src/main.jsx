import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.jsx';
import { SocketProvider } from './context/SocketContext.jsx';
import { LobbyProvider } from './context/LobbyContext.jsx';
import { GameProvider } from './context/GameContext.jsx';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <SocketProvider>
      <GameProvider>
        <LobbyProvider>
          <App />
        </LobbyProvider>
      </GameProvider>
    </SocketProvider>
  </StrictMode>,
)