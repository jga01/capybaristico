import React, { useState, useEffect, useRef } from 'react';
import './GameLog.css';

const GameLog = ({ eventBatch, player1, player2 }) => {
    const [logEntries, setLogEntries] = useState([]);
    const [isExpanded, setIsExpanded] = useState(false);
    const contentRef = useRef(null);

    const getPlayerName = (playerId) => {
        if (player1?.playerId === playerId) return player1.displayName;
        if (player2?.playerId === playerId) return player2.displayName;
        return "Unknown Player";
    };

    const formatEvent = (event) => {
        switch (event.eventType) {
            case 'TURN_STARTED':
                return `Turn ${event.newTurnNumber} begins. It's ${getPlayerName(event.newTurnPlayerId)}'s turn.`;
            case 'CARD_PLAYED':
                return `${getPlayerName(event.playerId)} played ${event.card.name}.`;
            case 'PLAYER_OVERDREW_CARD':
                return `Hand was full! ${getPlayerName(event.playerId)} discarded ${event.discardedCard.name}.`;
            case 'ATTACK_DECLARED':
                return `${event.attackerCardName} attacks ${event.defenderCardName}.`;
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
            case 'CARD_VANISHED':
                return `A card vanished from the field.`;
            case 'CARD_REAPPEARED':
                return `A card reappeared on the field.`;
            default:
                return null;
        }
    };

    useEffect(() => {
        if (eventBatch && eventBatch.length > 0) {
            const newEntries = eventBatch
                .map(formatEvent)
                .filter(Boolean)
                .map((message, index) => ({ id: `${Date.now()}-${index}`, message }));

            if (newEntries.length > 0) {
                setLogEntries(prev => [...newEntries, ...prev].slice(0, 50)); // Keep last 50 entries, newest first
            }
        }
    }, [eventBatch]);

    return (
        <div className={`game-log-container ${isExpanded ? 'expanded' : ''}`}
            onMouseEnter={() => setIsExpanded(true)}
            onMouseLeave={() => setIsExpanded(false)}
        >
            <div className="game-log-header">Game Log</div>
            <div className="game-log-content" ref={contentRef}>
                {logEntries.map(entry => (
                    <div key={entry.id} className="log-entry">{entry.message}</div>
                ))}
            </div>
        </div>
    );
};

export default GameLog;