import React, { useState, useEffect, useCallback, useRef } from 'react';
import { produce } from 'immer';
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


const GameScreen = ({ initialGameState, playerId, gameId, eventBatch, commandError, clearCommandError }) => {
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

    // Helper function to find card info within the current or a draft state
    const findCardInfo = useCallback((instanceId, state) => {
        const p1 = state.player1State;
        const p2 = state.player2State;
        let cardInfo = null;
        if (!instanceId) return null;

        [p1, p2].forEach(p => {
            if (p && p.field) {
                p.field.forEach((card, index) => {
                    if (card && card.instanceId === instanceId) {
                        cardInfo = { card, owner: p, index };
                    }
                });
            }
        });
        return cardInfo;
    }, []);

    useEffect(() => {
        if (commandError) {
            setFeedbackMessage(`❗ ${commandError}`); // Show the error
            const timer = setTimeout(() => {
                clearCommandError(); // Clear state in parent
                setFeedbackMessage(isMyTurn ? "Your turn." : "Opponent's turn."); // Reset
            }, 4000); // Display for 4 seconds

            return () => clearTimeout(timer);
        }
    }, [commandError, clearCommandError, isMyTurn, localGameState.currentPlayerId]);

    useEffect(() => {
        log('GameScreen mounted.', { gameId, playerId }, 'INFO');
        return () => log('GameScreen unmounted.', null, 'INFO');
    }, [gameId, playerId]);

    useEffect(() => {
        if (!eventBatch || eventBatch.length === 0) return;

        log('Received event batch from server.', { count: eventBatch.length, events: eventBatch }, 'INFO');

        const turnStartEvent = eventBatch.find(e => e.eventType === 'TURN_STARTED' && e.newTurnPlayerId === playerId);
        if (turnStartEvent) {
            setShowTurnBanner(true);
            log('My turn started.', { turnNumber: turnStartEvent.newTurnNumber });
        }

        const visualEffects = [];
        log('Local game state BEFORE applying events.', localGameState);
        const nextState = produce(localGameState, draftState => {
            eventBatch.forEach(event => {
                applyEventToState(draftState, event, playerId, findCardInfo);
                const effect = translateEventToEffect(draftState, event, playerId);
                if (effect) {
                    if (Array.isArray(effect)) visualEffects.push(...effect);
                    else visualEffects.push(effect);
                }
            });
        });

        log('Local game state AFTER applying events.', nextState);
        setLocalGameState(nextState);
        setEffectQueue(prev => {
            const newQueue = [...prev, ...visualEffects];
            log('Effect queue updated.', { added: visualEffects.length, newTotal: newQueue.length });
            return newQueue;
        });
    }, [eventBatch, playerId, findCardInfo]);

    const handleEffectComplete = useCallback(() => {
        if (currentEffect) {
            log('Visual effect completed.', currentEffect, 'RENDER');
            // If the completed effect was a destruction, now is the time to remove the card from state.
            if (currentEffect.isCleanupRequired) {
                setLocalGameState(produce(draft => {
                    const info = findCardInfo(currentEffect.targetId, draft);
                    if (info) {
                        draft[info.owner === draft.player1State ? 'player1State' : 'player2State'].field[info.index] = null;
                    }
                }));
            }
        }
        setCurrentEffect(null);
        setEffectQueue(prev => prev.slice(1));
    }, [currentEffect, findCardInfo]);

    useEffect(() => {
        if (!currentEffect && effectQueue.length > 0) {
            const nextEffect = effectQueue[0];
            log('Processing next effect from queue.', nextEffect, 'RENDER');

            setCurrentEffect(nextEffect);
            if (nextEffect.type === 'ATTACK_LUNGE') {
                setAttackAnimation({ attackerId: nextEffect.sourceId, defenderId: nextEffect.targetId });
                log('Starting ATTACK_LUNGE animation.', { attackerId: nextEffect.sourceId, defenderId: nextEffect.targetId }, 'RENDER');
                setTimeout(() => {
                    setAttackAnimation(null);
                    handleEffectComplete();
                }, 1000);
            }
        }
    }, [effectQueue, currentEffect, handleEffectComplete]);

    const isInputLocked = () => effectQueue.length > 0 || !!currentEffect || !!attackAnimation;

    const clearSelections = useCallback(() => {
        log('Clearing all user selections.');
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
        log(`USER_ACTION: Clicked card in hand.`, { cardName: cardData.name, instanceId: cardData.instanceId });
        if (selectedAttackerInfo || selectedAbilitySourceInfo) {
            setFeedbackMessage("Another action is pending. Cancel it first.");
            return;
        }
        if (selectedHandCardInfo?.instanceId === cardData.instanceId) {
            clearSelections();
        } else {
            setSelectedHandCardInfo({ instanceId: cardData.instanceId, cardData });
            setFeedbackMessage(`Selected ${cardData.name} from hand.`);
        }
    };

    const handleEmptyFieldSlotClick = (ownerPlayerId, fieldSlotIndex) => {
        if (isInputLocked() || !isMyTurn || !selectedHandCardInfo || ownerPlayerId !== playerId) return;
        log('USER_ACTION: Clicked empty field slot to play card.', { cardName: selectedHandCardInfo.cardData.name, slot: fieldSlotIndex });
        const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;
        const handCardIndex = viewingPlayer.hand.findIndex(c => c.instanceId === selectedHandCardInfo.instanceId);
        if (handCardIndex === -1) {
            log("Error: Card to be played not found in local hand state.", { selected: selectedHandCardInfo }, 'ERROR');
            setFeedbackMessage("Error: Card not found in hand.");
            clearSelections();
            return;
        }
        emitGameCommand({
            gameId, playerId, commandType: 'PLAY_CARD',
            handCardIndex, targetFieldSlot: fieldSlotIndex
        });
        clearSelections();
    };

    const handleCardClickOnField = (cardData, mesh, ownerId, fieldIndex) => {
        if (isInputLocked() || !isMyTurn || !cardData) return;
        log(`USER_ACTION: Clicked card on field.`, { cardName: cardData.name, ownerId, fieldIndex, isTargetingMode });
        const isOwnCard = ownerId === playerId;
        const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;

        if (isTargetingMode) {
            log('Targeting mode active. Resolving target.', { targetCard: cardData.name });
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
        log(`USER_ACTION: Selected ability.`, { cardName: selectedAbilitySourceInfo.cardData.name, ability: abilityOpt.name });
        setSelectedAttackerInfo(null); setSelectedAbilityOption(abilityOpt);
        const requiresTarget = abilityOpt.requiresTarget && abilityOpt.requiresTarget !== "NONE";
        if (requiresTarget) { setIsTargetingMode(true); setFeedbackMessage(`Ability '${abilityOpt.name}' requires a target.`); }
        else { emitGameCommand({ gameId, playerId, commandType: 'ACTIVATE_ABILITY', sourceCardInstanceId: selectedAbilitySourceInfo.instanceId, abilityOptionIndex: abilityOpt.index }); clearSelections(); }
    };

    const handleEndTurn = () => { if (!isInputLocked() && isMyTurn) { log('USER_ACTION: Clicked End Turn button.'); clearSelections(); emitGameCommand({ gameId, playerId, commandType: 'END_TURN' }); } };

    useEffect(() => { if (showTurnBanner) { const timer = setTimeout(() => setShowTurnBanner(false), 2500); return () => clearTimeout(timer); } }, [showTurnBanner]);

    const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;

    return (
        <div style={{ width: '100%', height: '100vh', position: 'relative', overflow: 'hidden' }}>
            {showTurnBanner && <div className="turn-banner">Your Turn</div>}
            <CanvasWrapper ref={canvasRef} gameState={localGameState} playerId={playerId} onMagnify={setMagnifiedCard} onCardClickOnField={handleCardClickOnField} onEmptyFieldSlotClick={handleEmptyFieldSlotClick} onTableClick={handleTableClick} selectedHandCardInfo={selectedHandCardInfo} selectedAttackerInfo={selectedAttackerInfo} selectedAbilitySourceInfo={selectedAbilitySourceInfo} isTargetingMode={isTargetingMode} selectedAbilityOption={selectedAbilityOption} currentEffect={currentEffect} onEffectComplete={handleEffectComplete} attackAnimation={attackAnimation} />
            <GameLog eventBatch={eventBatch} player1Name={localGameState.player1State.displayName} player2Name={localGameState.player2State.displayName} />
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

// --- FIX: `applyEventToState` is modified to not remove the card, only mark it for death ---
function applyEventToState(draftState, event, viewingPlayerId, findCardInfo) {
    const getPlayer = (id) => (draftState.player1State.playerId === id ? draftState.player1State : draftState.player2State);
    const resetStatChanges = (card) => { if (card) card.statChanges = {}; };

    switch (event.eventType) {
        case 'TURN_STARTED': {
            draftState.currentPlayerId = event.newTurnPlayerId; draftState.turnNumber = event.newTurnNumber;
            const player = getPlayer(event.newTurnPlayerId);
            if (player) { player.attacksDeclaredThisTurn = 0; player.field.forEach(card => { if (card) { card.isExhausted = false; resetStatChanges(card); } }); }
            break;
        }
        case 'PLAYER_DREW_CARD': {
            const player = getPlayer(event.playerId); player.deckSize = event.newDeckSize; player.handSize = event.newHandSize;
            if (event.playerId === viewingPlayerId && event.card) { player.hand.push(event.card); }
            break;
        }
        case 'CARD_PLAYED': {
            const player = getPlayer(event.playerId); player.handSize = event.newHandSize;
            if (player.playerId === viewingPlayerId && event.fromHandIndex < player.hand.length) player.hand.splice(event.fromHandIndex, 1);
            player.field[event.toFieldSlot] = { ...event.card, isExhausted: true, statChanges: {}, baseAttack: event.card.currentAttack, baseDefense: event.card.currentDefense, baseLife: event.card.currentLife };
            break;
        }
        case 'ATTACK_DECLARED': { const info = findCardInfo(event.attackerInstanceId, draftState); if (info) { info.owner.attacksDeclaredThisTurn += 1; info.card.isExhausted = true; } break; }
        case 'COMBAT_DAMAGE_DEALT': { const info = findCardInfo(event.defenderInstanceId, draftState); if (info) { resetStatChanges(info.card); info.card.statChanges.life = true; info.card.currentLife = event.defenderLifeAfter; } break; }
        case 'CARD_DESTROYED': {
            const info = findCardInfo(event.card.instanceId, draftState);
            if (info) {
                // *** FIX: Only mark as dying, do not remove from field array here ***
                info.card.isDying = true;
                info.owner.discardPileSize += 1;
            }
            break;
        }
        case 'CARD_STATS_CHANGED': {
            const info = findCardInfo(event.targetInstanceId, draftState);
            if (info) {
                resetStatChanges(info.card);
                if (info.card.currentAttack !== event.newAttack) info.card.statChanges.attack = true;
                if (info.card.currentDefense !== event.newDefense) info.card.statChanges.defense = true;
                info.card.currentAttack = event.newAttack; info.card.currentDefense = event.newDefense;
            }
            break;
        }
        case 'CARD_BUFFED': case 'CARD_DEBUFFED': {
            const info = findCardInfo(event.targetInstanceId, draftState);
            if (info) {
                resetStatChanges(info.card);
                if (event.stat === "ATK") { info.card.statChanges.attack = true; info.card.currentAttack = event.statAfter; }
                if (event.stat === "DEF") { info.card.statChanges.defense = true; info.card.currentDefense = event.statAfter; }
            }
            break;
        }
        case 'CARD_HEALED': { const info = findCardInfo(event.targetInstanceId, draftState); if (info) { resetStatChanges(info.card); info.card.statChanges.life = true; info.card.currentLife = event.lifeAfter; } break; }
        case 'CARD_FLAG_CHANGED': {
            const info = findCardInfo(event.targetInstanceId, draftState);
            if (info) { if (!info.card.effectFlags) info.card.effectFlags = {}; if (event.value === null) delete info.card.effectFlags[event.flagName]; else info.card.effectFlags[event.flagName] = event.value; }
            break;
        }
        case 'CARD_TRANSFORMED': {
            const info = findCardInfo(event.originalInstanceId, draftState);
            if (info) { const newCard = { ...event.newCardDto, isExhausted: true, statChanges: {}, baseAttack: event.newCardDto.currentAttack, baseDefense: event.newCardDto.currentDefense, baseLife: event.newCardDto.currentLife }; info.owner.field[info.index] = newCard; }
            break;
        }
        case 'CARD_VANISHED': { const info = findCardInfo(event.instanceId, draftState); if (info) info.owner.field[info.index] = null; break; }
        case 'CARD_REAPPEARED': { const owner = getPlayer(event.ownerPlayerId); if (owner) owner.field[event.toFieldSlot] = { ...event.card, isExhausted: true, statChanges: {} }; break; }
    }
}

// --- FIX: `translateEventToEffect` is modified to signal a cleanup action is required ---
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
        case 'CARD_DESTROYED': return { type: 'CARD_DESTROYED', targetId: event.card.instanceId, duration: 1200, isCleanupRequired: true }; // *** FIX: Add flag ***
        case 'CARD_HEALED': return { type: 'HEAL', targetId: event.targetInstanceId, amount: event.amount };
        case 'CARD_PLAYED': return null;
        case 'CARD_BUFFED': return { type: 'STAT_CHANGE', isBuff: true, targetId: event.targetInstanceId, text: `+${event.amount} ${event.stat}` };
        case 'CARD_DEBUFFED': return { type: 'STAT_CHANGE', isBuff: false, targetId: event.targetInstanceId, text: `-${event.amount} ${event.stat}` };
        case 'CARD_TRANSFORMED': return { type: 'TRANSFORM', targetId: event.originalInstanceId, duration: 1500 };
        case 'CARD_VANISHED': return { type: 'VANISH', targetId: event.instanceId, duration: 1200 };
        case 'CARD_REAPPEARED': return { type: 'REAPPEAR', targetId: event.card.instanceId, duration: 1400 };
        default: return null;
    }
}

export default GameScreen;