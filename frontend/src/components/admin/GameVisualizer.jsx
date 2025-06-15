import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { getAllGameIds, getGameHistory, getAllCards } from '../../services/adminApiService';
import { applyEventToState } from '../../services/gameReconstruction';
import ReplayArea from './ReplayBoard';

const GameVisualizer = () => {
    const [gameIds, setGameIds] = useState([]);
    const [allCardDefs, setAllCardDefs] = useState([]);
    const [selectedGameId, setSelectedGameId] = useState(null);
    const [history, setHistory] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const [currentEventIndex, setCurrentEventIndex] = useState(-1);

    useEffect(() => {
        const fetchInitialData = async () => {
            try {
                const [ids, defs] = await Promise.all([getAllGameIds(), getAllCards()]);
                setGameIds(ids);
                setAllCardDefs(defs);
            } catch (err) {
                setError(err.message);
            }
        };
        fetchInitialData();
    }, []);

    const handleSelectGame = async (gameId) => {
        if (!gameId) {
            setSelectedGameId(null);
            setHistory(null);
            setCurrentEventIndex(-1);
            return;
        }
        setIsLoading(true);
        setError('');
        try {
            const historyData = await getGameHistory(gameId);
            setHistory(historyData);
            setSelectedGameId(gameId);
            setCurrentEventIndex(-1); // Start before the first event
        } catch (err) {
            setError(err.message);
            setHistory(null);
        } finally {
            setIsLoading(false);
        }
    };

    const reconstructedState = useMemo(() => {
        if (!history) return null;
        if (currentEventIndex < 0) return history.reconstructedGameState; // Initial state

        let state = history.reconstructedGameState;
        for (let i = 0; i <= currentEventIndex; i++) {
            if (history.eventHistory[i]) {
                state = applyEventToState(state, history.eventHistory[i], allCardDefs);
            }
        }
        return state;
    }, [currentEventIndex, history, allCardDefs]);

    const stepForward = () => {
        if (history && currentEventIndex < history.eventHistory.length - 1) {
            setCurrentEventIndex(prev => prev + 1);
        }
    };

    const stepBackward = () => {
        if (currentEventIndex > -1) {
            setCurrentEventIndex(prev => prev - 1);
        }
    };

    return (
        <div className="game-visualizer">
            <aside className="game-list-panel">
                <h3>Games</h3>
                <ul>
                    {gameIds.map(id => (
                        <li key={id} onClick={() => handleSelectGame(id)} className={selectedGameId === id ? 'selected' : ''}>
                            {id.substring(0, 8)}...
                        </li>
                    ))}
                </ul>
            </aside>
            <main className="game-replay-panel">
                <div className="replay-controls">
                    <button className="admin-button secondary" onClick={stepBackward} disabled={currentEventIndex < 0}>Prev</button>
                    <button className="admin-button secondary" onClick={stepForward} disabled={!history || currentEventIndex >= history.eventHistory.length - 1}>Next</button>
                    <span>Event {currentEventIndex + 1} / {history ? history.eventHistory.length : 0}</span>
                </div>
                {isLoading && <p>Loading game history...</p>}
                {error && <p style={{ color: 'red' }}>Error: {error}</p>}
                <div className="replay-board-area">
                    {reconstructedState && <ReplayArea gameState={reconstructedState} />}
                </div>
                {history && (
                    <div className="event-list">
                        {history.eventHistory.map((evt, index) => (
                            <p key={index} style={{ background: index === currentEventIndex ? '#e6f7ff' : 'transparent', margin: '2px 0' }}>
                                <strong>{evt.eventType}</strong>
                            </p>
                        ))}
                    </div>
                )}
            </main>
        </div>
    );
};

export default GameVisualizer;