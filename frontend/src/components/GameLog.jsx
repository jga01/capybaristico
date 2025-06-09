import React, { useState, useEffect, useRef } from 'react';
import './GameLog.css';

const GameLog = ({ eventBatch, player1Name, player2Name }) => {
    const [logEntries, setLogEntries] = useState([]);
    const [isExpanded, setIsExpanded] = useState(false);
    const logEndRef = useRef(null);

    const formatEvent = (event) => {
        switch (event.eventType) {
            case 'TURN_STARTED':
                return `Turn ${event.newTurnNumber} begins. It's ${event.newTurnPlayerId === player1Name ? player1Name : player2Name}'s turn.`;
            case 'CARD_PLAYED':
                return `${event.playerId === player1Name ? player1Name : player2Name} played ${event.card.name}.`;
            case 'ATTACK_DECLARED':
                // Note: This relies on finding names, which aren't in the event.
                // A better implementation would have the GameScreen pass card names.
                // For now, we'll just use a generic message.
                return `An attack was declared.`;
            case 'COMBAT_DAMAGE_DEALT':
                if (event.damageAfterDefense > 0) {
                    return `A card took ${event.damageAfterDefense} damage.`;
                }
                return `An attack was blocked!`;
            case 'CARD_DESTROYED':
                return `${event.card.name} was destroyed.`;
            case 'CARD_HEALED':
                return `A card was healed for ${event.amount}.`;
            case 'CARD_BUFFED':
                return `A card's ${event.stat} was buffed by ${event.amount}.`;
            case 'CARD_DEBUFFED':
                return `A card's ${event.stat} was debuffed by ${event.amount}.`;
            case 'CARD_TRANSFORMED':
                return `A card transformed into ${event.newCardDto.name || event.newCardDto.cardId}!`;
            case 'GAME_LOG_MESSAGE':
                return `Effect: ${event.message}`;
            default:
                return null; // Don't log irrelevant events like TURN_ENDED, etc.
        }
    };

    useEffect(() => {
        if (eventBatch && eventBatch.length > 0) {
            const newEntries = eventBatch
                .map(formatEvent)
                .filter(Boolean) // Remove null entries
                .map((message, index) => ({ id: `${Date.now()}-${index}`, message }));

            if (newEntries.length > 0) {
                setLogEntries(prev => [...prev.slice(-50), ...newEntries]); // Keep last 50 entries
            }
        }
    }, [eventBatch, player1Name, player2Name]);

    useEffect(() => {
        if (isExpanded) {
            logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }
    }, [logEntries, isExpanded]);

    return (
        <div className={`game-log-container ${isExpanded ? 'expanded' : ''}`}
            onMouseEnter={() => setIsExpanded(true)}
            onMouseLeave={() => setIsExpanded(false)}
        >
            <div className="game-log-header">Game Log</div>
            <div className="game-log-content">
                {logEntries.map(entry => (
                    <div key={entry.id} className="log-entry">{entry.message}</div>
                ))}
                <div ref={logEndRef} />
            </div>
        </div>
    );
};

export default GameLog;