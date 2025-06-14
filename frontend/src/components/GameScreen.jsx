import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useGame } from '../context/GameContext'; // Import useGame hook
import CanvasWrapper from './ThreeCanvas';
import GameLog from './GameLog';
import { emitGameCommand } from '../services/socketService';
import { log, downloadLogs } from '../services/loggingService';
import { MAX_ATTACKS_PER_TURN } from '../constants';
import './HandCard.css';
import MagnifiedCardView from './MagnifiedCardView';

const HTMLHandCard = ({ cardData, onClick, onMagnify, isSelected }) => {
    const isPlayable = cardData.isDirectlyPlayable !== false;

    return (
        <div
            className={`html-hand-card ${isSelected ? 'selected' : ''} ${!isPlayable ? 'unplayable' : ''}`}
            onClick={() => isPlayable && onClick(cardData)}
            onContextMenu={(e) => { e.preventDefault(); onMagnify(cardData); }}
            title={`${cardData.name}${!isPlayable ? ' (Cannot be played from hand)' : ''}\nL:${cardData.currentLife} A:${cardData.currentAttack} D:${cardData.currentDefense}`}
        >
            <img src={cardData.imageUrl ? `/assets/cards_images/${cardData.imageUrl}` : '/assets/cards_images/back.png'} alt={cardData.name} />
        </div>
    );
};


const GameScreen = () => {
    // Get all game data from the context
    const {
        gameId,
        playerId,
        initialGameState, // This is now the LATEST game state
        eventBatch, // This is now just the list of events from the last update
        commandError,
        clearCommandError
    } = useGame();

    const [localGameState, setLocalGameState] = useState(initialGameState);
    const [selectedHandCardInfo, setSelectedHandCardInfo] = useState(null);
    const [selectedAttackerInfo, setSelectedAttackerInfo] = useState(null);
    const [selectedAbilitySourceInfo, setSelectedAbilitySourceInfo] = useState(null);
    const [selectedAbilityOption, setSelectedAbilityOption] = useState(null);
    const [isTargetingMode, setIsTargetingMode] = useState(false);
    const [feedbackMessage, setFeedbackMessage] = useState('Game started!');
    const [magnifiedCard, setMagnifiedCard] = useState(null);
    const [showTurnBanner, setShowTurnBanner] = useState(false);
    const [effectQueue, setEffectQueue] = useState([]);
    const [currentEffect, setCurrentEffect] = useState(null);
    const [attackAnimation, setAttackAnimation] = useState(null);

    const isMyTurn = localGameState.currentPlayerId === playerId;
    const canvasRef = useRef();
    const isProcessingQueue = useRef(false);

    const findCardInfo = useCallback((instanceId, state) => {
        const p1 = state.player1State;
        const p2 = state.player2State;
        let cardInfo = null;
        if (!instanceId) return null;

        [p1, p2].forEach(p => {
            if (p && p.field) {
                p.field.forEach((card, index) => {
                    if (card && card.instanceId === instanceId) {
                        cardInfo = { card, owner: p, index, ownerId: p.playerId };
                    }
                });
            }
        });
        return cardInfo;
    }, []);

    useEffect(() => {
        if (commandError) {
            setFeedbackMessage(`❗ ${commandError}`);
            const timer = setTimeout(() => {
                clearCommandError();
                setFeedbackMessage(isMyTurn ? "Your turn." : "Opponent's turn.");
            }, 4000);
            return () => clearTimeout(timer);
        }
    }, [commandError, clearCommandError, isMyTurn]);

    useEffect(() => {
        log('GameScreen mounted.', { gameId, playerId }, 'INFO');
        return () => log('GameScreen unmounted.', null, 'INFO');
    }, [gameId, playerId]);

    // This is the key change. This effect now just syncs state and queues animations.
    useEffect(() => {
        if (!initialGameState) return;

        // 1. Directly update the state. No more `produce` or `applyEventToState`.
        setLocalGameState(initialGameState);
        log('Local state synchronized with authoritative server state.');

        // 2. Queue visual effects based on the events that came with the new state.
        if (eventBatch && eventBatch.length > 0) {
            const turnStartEvent = eventBatch.find(e => e.eventType === 'TURN_STARTED' && e.newTurnPlayerId === playerId);
            if (turnStartEvent) {
                setShowTurnBanner(true);
                log('My turn started.', { turnNumber: turnStartEvent.newTurnNumber });
            }

            const visualEffects = eventBatch
                .map(event => translateEventToEffect(initialGameState, event, playerId)) // Pass current state for context
                .flat() // Handle cases where an event creates multiple effects
                .filter(Boolean); // Filter out nulls

            setEffectQueue(prev => [...prev, ...visualEffects]);
        }
    }, [initialGameState, eventBatch, playerId]); // Depend on the state object from context

    const handleEffectComplete = useCallback(() => {
        if (currentEffect && currentEffect.isCleanupRequired) {
            // Find the card in the *latest* state and remove it if the server has already done so
            setLocalGameState(prevState => {
                const latestCardInfo = findCardInfo(currentEffect.targetId, prevState);
                if (latestCardInfo?.card?.isDying) {
                    // Create a new state object to trigger re-render
                    const nextState = JSON.parse(JSON.stringify(prevState));
                    const ownerKey = latestCardInfo.ownerId === nextState.player1State.playerId ? 'player1State' : 'player2State';
                    nextState[ownerKey].field[latestCardInfo.index] = null;
                    return nextState;
                }
                return prevState;
            });
        }
        setCurrentEffect(null);
        isProcessingQueue.current = false;
    }, [currentEffect, findCardInfo]);

    useEffect(() => {
        if (!isProcessingQueue.current && effectQueue.length > 0) {
            isProcessingQueue.current = true;
            const nextEffect = effectQueue[0];

            setCurrentEffect(nextEffect);
            setEffectQueue(prev => prev.slice(1));

            if (nextEffect.type === 'ATTACK_LUNGE') {
                setAttackAnimation({ attackerId: nextEffect.sourceId, defenderId: nextEffect.targetId });
                setTimeout(() => {
                    setAttackAnimation(null);
                    handleEffectComplete();
                }, 1000);
            }
        }
    }, [effectQueue, handleEffectComplete]);


    const isInputLocked = () => isProcessingQueue.current || !!attackAnimation;

    const clearSelections = useCallback(() => {
        setFeedbackMessage(isMyTurn ? "Your turn." : "Opponent's turn.");
        setSelectedHandCardInfo(null);
        setSelectedAttackerInfo(null);
        setSelectedAbilitySourceInfo(null);
        setSelectedAbilityOption(null);
        setIsTargetingMode(false);
    }, [isMyTurn]);

    const handleTableClick = () => { if (!isInputLocked()) clearSelections(); };

    const handleHTMLCardClickInHand = (cardData) => {
        if (isInputLocked() || !isMyTurn) return;
        if (selectedAttackerInfo || selectedAbilitySourceInfo) { setFeedbackMessage("Another action is pending. Cancel it first."); return; }
        if (selectedHandCardInfo?.instanceId === cardData.instanceId) { clearSelections(); }
        else { setSelectedHandCardInfo({ instanceId: cardData.instanceId, cardData }); setFeedbackMessage(`Selected ${cardData.name} from hand.`); }
    };

    const handleEmptyFieldSlotClick = (ownerPlayerId, fieldSlotIndex) => {
        if (isInputLocked() || !isMyTurn || !selectedHandCardInfo || ownerPlayerId !== playerId) return;
        const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;
        const handCardIndex = viewingPlayer.hand.findIndex(c => c.instanceId === selectedHandCardInfo.instanceId);
        if (handCardIndex === -1) { setFeedbackMessage("Error: Card not found in hand."); clearSelections(); return; }
        emitGameCommand({ gameId, playerId, commandType: 'PLAY_CARD', handCardIndex, targetFieldSlot: fieldSlotIndex });
        clearSelections();
    };

    const handleCardClickOnField = (cardData, mesh, ownerId, fieldIndex) => {
        if (isInputLocked() || !isMyTurn || !cardData) return;
        const isOwnCard = ownerId === playerId;
        const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;

        if (isTargetingMode) {
            if (selectedAttackerInfo) {
                if (isOwnCard) { setFeedbackMessage("Cannot attack your own card."); return; }
                emitGameCommand({ gameId, playerId, commandType: 'ATTACK', attackerFieldIndex: selectedAttackerInfo.fieldIndex, defenderFieldIndex: fieldIndex });
            } else if (selectedAbilitySourceInfo && selectedAbilityOption) {
                emitGameCommand({ gameId, playerId, commandType: 'ACTIVATE_ABILITY', sourceCardInstanceId: selectedAbilitySourceInfo.instanceId, targetCardInstanceId: cardData.instanceId, abilityOptionIndex: selectedAbilityOption.index });
            }
            clearSelections();
            return;
        }

        if (isOwnCard) {
            clearSelections();
            if (cardData.isExhausted) { setFeedbackMessage(`${cardData.name} is exhausted.`); return; }
            const canAttack = viewingPlayer.attacksDeclaredThisTurn < MAX_ATTACKS_PER_TURN;
            const hasAbilities = cardData.abilities && cardData.abilities.length > 0;
            if (canAttack) { setSelectedAttackerInfo({ instanceId: cardData.instanceId, cardData, fieldIndex }); setFeedbackMessage(`Selected ${cardData.name} to attack. Choose a target.`); setIsTargetingMode(true); }
            if (hasAbilities) { setSelectedAbilitySourceInfo({ instanceId: cardData.instanceId, cardData, fieldIndex }); if (!canAttack) setFeedbackMessage(`Selected ${cardData.name}. Choose an ability.`); }
            if (!canAttack && !hasAbilities) setFeedbackMessage(`Selected ${cardData.name}, but no actions are available.`);
        }
    };

    const handleAbilityOptionSelect = (abilityOpt) => {
        if (isInputLocked() || !selectedAbilitySourceInfo) return;
        setSelectedAttackerInfo(null); setSelectedAbilityOption(abilityOpt);
        const requiresTarget = abilityOpt.requiresTarget && abilityOpt.requiresTarget !== "NONE";
        if (requiresTarget) { setIsTargetingMode(true); setFeedbackMessage(`Ability '${abilityOpt.name}' requires a target.`); }
        else { emitGameCommand({ gameId, playerId, commandType: 'ACTIVATE_ABILITY', sourceCardInstanceId: selectedAbilitySourceInfo.instanceId, abilityOptionIndex: abilityOpt.index }); clearSelections(); }
    };

    const handleEndTurn = () => { if (!isInputLocked() && isMyTurn) { clearSelections(); emitGameCommand({ gameId, playerId, commandType: 'END_TURN' }); } };

    useEffect(() => { if (showTurnBanner) { const timer = setTimeout(() => setShowTurnBanner(false), 2500); return () => clearTimeout(timer); } }, [showTurnBanner]);

    const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;

    return (
        <div style={{ width: '100%', height: '100vh', position: 'relative', overflow: 'hidden' }}>
            {showTurnBanner && <div className="turn-banner">Your Turn</div>}
            <CanvasWrapper ref={canvasRef} gameState={localGameState} playerId={playerId} onMagnify={setMagnifiedCard} onCardClickOnField={handleCardClickOnField} onEmptyFieldSlotClick={handleEmptyFieldSlotClick} onTableClick={handleTableClick} selectedHandCardInfo={selectedHandCardInfo} selectedAttackerInfo={selectedAttackerInfo} selectedAbilitySourceInfo={selectedAbilitySourceInfo} isTargetingMode={isTargetingMode} selectedAbilityOption={selectedAbilityOption} currentEffect={currentEffect} onEffectComplete={handleEffectComplete} attackAnimation={attackAnimation} />
            <GameLog eventBatch={eventBatch} player1={localGameState.player1State} player2={localGameState.player2State} />
            <div style={{ position: 'absolute', top: 10, right: 15, zIndex: 200 }}><button onClick={() => downloadLogs(gameId)}>Download Logs</button></div>
            <div style={{ position: 'absolute', top: 10, left: '50%', transform: 'translateX(-50%)', color: 'white', pointerEvents: 'none', textShadow: '1px 1px 2px black', textAlign: 'center' }}>
                <p style={{ margin: 0 }}>Turn: {localGameState.turnNumber} | {isMyTurn ? "Your Turn" : "Opponent's Turn"}</p>
                {isMyTurn && <p style={{ margin: 0 }}>Attacks Left: {MAX_ATTACKS_PER_TURN - viewingPlayer.attacksDeclaredThisTurn}</p>}
                <p style={{ color: '#FFD700', margin: '5px 0 0 0', fontStyle: 'italic' }}>{feedbackMessage}</p>
            </div>
            {isMyTurn && selectedAbilitySourceInfo && (
                <div style={{ position: 'absolute', bottom: '150px', left: '50%', transform: 'translateX(-50%)', backgroundColor: 'rgba(40,45,60,0.95)', border: '1px solid #7fceff', padding: '15px', borderRadius: '8px', zIndex: 110, color: 'white', display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '300px' }}>
                    <h4 style={{ marginTop: 0, marginBottom: '10px', textAlign: 'center' }}>{selectedAbilitySourceInfo.cardData.name} - Actions</h4>
                    {selectedAbilitySourceInfo.cardData.abilities.map(opt => (<button key={opt.index} onClick={() => handleAbilityOptionSelect(opt)} title={opt.description || ''}>{opt.name}</button>))}
                    <button onClick={clearSelections} style={{ backgroundColor: '#dc3545' }}>Cancel</button>
                </div>
            )}
            <div className="html-hand-card-container">{viewingPlayer.hand.map(card => (<HTMLHandCard key={card.instanceId} cardData={card} onClick={handleHTMLCardClickInHand} onMagnify={setMagnifiedCard} isSelected={selectedHandCardInfo?.instanceId === card.instanceId} />))}</div>
            <div style={{ position: 'absolute', bottom: 10, right: 15 }}>{isMyTurn && <button onClick={handleEndTurn} disabled={isInputLocked()}>End Turn</button>}</div>
            <div style={{ position: 'absolute', bottom: 10, left: 15, color: 'white', pointerEvents: 'none' }}><p style={{ margin: 0 }}>Hand: {viewingPlayer.handSize} | Deck: {viewingPlayer.deckSize} | Discard: {viewingPlayer.discardPileSize}</p></div>
            {magnifiedCard && <MagnifiedCardView cardData={magnifiedCard} onClose={() => setMagnifiedCard(null)} />}
        </div>
    );
};

function translateEventToEffect(currentState, event, viewingPlayerId) {
    if (!event) return null;
    switch (event.eventType) {
        case 'TURN_STARTED': {
            if (event.newTurnPlayerId === viewingPlayerId) {
                const player = currentState.player1State.playerId === event.newTurnPlayerId ? currentState.player1State : currentState.player2State;
                return player.field.filter(Boolean).map(card => ({ type: 'UNEXHAUST', targetId: card.instanceId, duration: 500 }));
            }
            return null;
        }
        case 'COMBAT_DAMAGE_DEALT': return event.damageAfterDefense > 0 ? { type: 'DAMAGE', targetId: event.defenderInstanceId, amount: event.damageAfterDefense } : { type: 'ZERO_DAMAGE', targetId: event.defenderInstanceId };
        case 'ATTACK_DECLARED': return { type: 'ATTACK_LUNGE', sourceId: event.attackerInstanceId, targetId: event.defenderInstanceId, duration: 1000 };
        case 'CARD_DESTROYED': return { type: 'CARD_DESTROYED', targetId: event.card.instanceId, duration: 1200, isCleanupRequired: true };
        case 'CARD_HEALED': return { type: 'HEAL', targetId: event.targetInstanceId, amount: event.amount };
        case 'CARD_PLAYED': return null;
        case 'CARD_BUFFED': return { type: 'STAT_CHANGE', isBuff: true, targetId: event.targetInstanceId, text: `+${event.amount} ${event.stat}` };
        case 'CARD_DEBUFFED': return { type: 'STAT_CHANGE', isBuff: false, targetId: event.targetInstanceId, text: `-${event.amount} ${event.stat}` };
        case 'CARD_TRANSFORMED': return { type: 'TRANSFORM', targetId: event.originalInstanceId, duration: 1500 };
        case 'CARD_VANISHED': return { type: 'VANISH', targetId: event.instanceId, duration: 1200, isCleanupRequired: true };
        case 'CARD_REAPPEARED': return { type: 'REAPPEAR', targetId: event.card.instanceId, duration: 1400 };
        default: return null;
    }
}

export default GameScreen;