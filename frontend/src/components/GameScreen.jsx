import React, { useState, useEffect, useCallback, useRef } from 'react';
import { produce } from 'immer';
import CanvasWrapper from './ThreeCanvas';
import { emitGameCommand } from '../services/socketService';
import { log, downloadLogs } from '../services/loggingService';
import { CARD_ABILITIES, MAX_ATTACKS_PER_TURN } from '../constants';
import './HandCard.css';
import MagnifiedCardView from './MagnifiedCardView';

// Sub-component for rendering a card in the 2D HTML hand UI
const HTMLHandCard = ({ cardData, onClick, onMagnify, isSelected }) => (
    <div
        className={`html-hand-card ${isSelected ? 'selected' : ''}`}
        onClick={() => onClick(cardData)}
        onContextMenu={(e) => { e.preventDefault(); onMagnify(cardData); }}
        title={`${cardData.name}\nType: ${cardData.type || 'N/A'}\nATK: ${cardData.currentAttack} / DEF: ${cardData.currentDefense} / LIFE: ${cardData.currentLife}\nEffect: ${cardData.effectText?.substring(0, 100)}...`}
    >
        <img src={cardData.imageUrl ? (cardData.imageUrl.startsWith('/') ? cardData.imageUrl : `/assets/cards_images/${cardData.imageUrl}`) : '/assets/cards_images/back.png'} alt={cardData.name} />
        <div style={{ position: 'absolute', bottom: '2px', left: '2px', right: '2px', backgroundColor: 'rgba(0,0,0,0.6)', color: 'white', fontSize: '10px', textAlign: 'center', padding: '1px 0', borderRadius: '0 0 3px 3px' }}>
            L:{cardData.currentLife} A:{cardData.currentAttack} D:{cardData.currentDefense}
        </div>
    </div>
);


const GameScreen = ({ initialGameState, playerId, gameId, eventBatch }) => {
    // === Local Game State & UI State ===
    const [localGameState, setLocalGameState] = useState(initialGameState);
    const [selectedHandCardInfo, setSelectedHandCardInfo] = useState(null);
    const [selectedAttackerInfo, setSelectedAttackerInfo] = useState(null);
    const [selectedAbilitySourceInfo, setSelectedAbilitySourceInfo] = useState(null);
    const [selectedAbilityOption, setSelectedAbilityOption] = useState(null);
    const [isTargetingMode, setIsTargetingMode] = useState(false);
    const [feedbackMessage, setFeedbackMessage] = useState('Game started!');
    const [magnifiedCard, setMagnifiedCard] = useState(null);

    // === Visual Effect System State ===
    const [effectQueue, setEffectQueue] = useState([]);
    const [currentEffect, setCurrentEffect] = useState(null);

    const isMyTurn = localGameState.currentPlayerId === playerId;
    const canvasRef = useRef();


    // --- Core System: Event Processing and State Application ---

    useEffect(() => {
        if (!eventBatch || eventBatch.length === 0) return;

        log('PROCESSING EVENT BATCH', { count: eventBatch.length, events: eventBatch });
        log('STATE BEFORE BATCH', localGameState);

        const visualEffects = [];

        const nextState = produce(localGameState, draftState => {
            eventBatch.forEach((event, index) => {
                // --- ENHANCED LOGGING START ---
                console.groupCollapsed(`  Applying event ${index + 1}: ${event.eventType}`);
                console.log(event);
                // --- ENHANCED LOGGING END ---
                applyEventToState(draftState, event, playerId);
                const effect = translateEventToEffect(event, playerId);
                if (effect) visualEffects.push(effect);
                console.groupEnd();
            });
        });

        log('STATE AFTER BATCH', nextState);
        log('GENERATED VISUAL EFFECTS', visualEffects);

        setLocalGameState(nextState);
        setEffectQueue(prev => [...prev, ...visualEffects]);

    }, [eventBatch, playerId]); // Added localGameState to dependencies


    /**
     * The Effect Queue Manager.
     * Watches the queue and feeds effects to the renderer one by one.
     */
    useEffect(() => {
        if (!currentEffect && effectQueue.length > 0) {
            const nextEffect = effectQueue[0];
            setCurrentEffect(nextEffect);
        }
    }, [effectQueue, currentEffect]);

    const handleEffectComplete = useCallback(() => {
        // --- LOGGING ---
        log('VISUAL EFFECT COMPLETE', currentEffect);
        setCurrentEffect(null);
        setEffectQueue(prev => prev.slice(1));
    }, [currentEffect]);


    // --- User Interaction Handlers (locked during effects) ---
    const isInputLocked = () => !!currentEffect;

    const clearSelections = useCallback(() => {
        setFeedbackMessage(isMyTurn ? "Your turn." : "Opponent's turn.");
        setSelectedHandCardInfo(null);
        setSelectedAttackerInfo(null);
        setSelectedAbilitySourceInfo(null);
        setSelectedAbilityOption(null);
        setIsTargetingMode(false);
    }, [isMyTurn]);

    const handleTableClick = () => {
        if (isInputLocked()) return;
        log('ACTION: Clicked table (clear selections)'); // <-- LOG
        clearSelections();
    };

    const handleHTMLCardClickInHand = (cardData) => {
        if (isInputLocked() || !isMyTurn) return;
        // --- LOGGING ---
        log('ACTION: Clicked hand card', { cardName: cardData.name, instanceId: cardData.instanceId });

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

        const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;
        const handCardIndex = viewingPlayer.hand.findIndex(c => c.instanceId === selectedHandCardInfo.instanceId);

        if (handCardIndex === -1) {
            setFeedbackMessage("Error: Card not found in hand.");
            // --- LOGGING ---
            log('ERROR: Attempted to play a card not found in hand.', { selected: selectedHandCardInfo, currentHand: viewingPlayer.hand });
            clearSelections();
            return;
        }

        // --- LOGGING ---
        log('ACTION: Play card to empty slot', { cardName: selectedHandCardInfo.cardData.name, handIndex: handCardIndex, fieldSlot: fieldSlotIndex });

        emitGameCommand({
            gameId,
            playerId,
            commandType: 'PLAY_CARD',
            handCardIndex,
            targetFieldSlot: fieldSlotIndex
        });
        clearSelections();
    };

    const handleCardClickOnField = (cardData, mesh, ownerId, fieldIndex) => {
        log('ACTION: Clicked field card', { cardName: cardData?.name, ownerId, fieldIndex, isTargetingMode, inputLocked: isInputLocked() });

        if (isInputLocked()) {
            setFeedbackMessage("Resolving effects...");
            return;
        }
        if (!isMyTurn || !cardData) return;

        const isOwnCard = ownerId === playerId;
        const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;
        const attacksMadeThisTurn = viewingPlayer.attacksDeclaredThisTurn;

        if (isTargetingMode) {
            if (selectedAttackerInfo) {
                if (isOwnCard) { setFeedbackMessage("Cannot attack your own card."); return; }
                emitGameCommand({
                    gameId, playerId, commandType: 'ATTACK',
                    attackerFieldIndex: selectedAttackerInfo.fieldIndex,
                    defenderFieldIndex: fieldIndex
                });
            } else if (selectedAbilitySourceInfo && selectedAbilityOption) {
                emitGameCommand({
                    gameId, playerId, commandType: 'ACTIVATE_ABILITY',
                    sourceCardInstanceId: selectedAbilitySourceInfo.instanceId,
                    targetCardInstanceId: cardData.instanceId,
                    abilityOptionIndex: selectedAbilityOption.index
                });
            }
            clearSelections();
            return;
        }

        if (isOwnCard) {
            clearSelections();

            if (cardData.isExhausted) {
                setFeedbackMessage(`${cardData.name} is exhausted and cannot attack or use abilities this turn.`);
                return;
            }

            if (CARD_ABILITIES[cardData.cardId]) {
                setSelectedAbilitySourceInfo({ instanceId: cardData.instanceId, cardData, fieldIndex });
                setFeedbackMessage(`Selected ${cardData.name} for ability.`);
            } else if (attacksMadeThisTurn < MAX_ATTACKS_PER_TURN) {
                setSelectedAttackerInfo({ instanceId: cardData.instanceId, cardData, fieldIndex });
                setIsTargetingMode(true);
                setFeedbackMessage(`Selected ${cardData.name} to attack.`);
            } else {
                setFeedbackMessage("No more attacks left this turn.");
            }
        }
    };

    const handleAbilityOptionSelect = (abilityOpt) => {
        if (isInputLocked() || !selectedAbilitySourceInfo) return;
        log('ACTION: Selected ability option', { ability: abilityOpt.name }); // <-- LOG
        setSelectedAbilityOption(abilityOpt);

        const requiresTarget = abilityOpt.requiresTarget && abilityOpt.requiresTarget !== "NONE";

        if (requiresTarget) {
            setIsTargetingMode(true);
            setFeedbackMessage(`Ability '${abilityOpt.name}' requires a target.`);
        } else {
            emitGameCommand({
                gameId, playerId, commandType: 'ACTIVATE_ABILITY',
                sourceCardInstanceId: selectedAbilitySourceInfo.instanceId,
                abilityOptionIndex: abilityOpt.index
            });
            clearSelections();
        }
    };

    const handleEndTurn = () => {
        if (isInputLocked() || !isMyTurn) return;
        // --- LOGGING ---
        log('ACTION: End turn');
        clearSelections();
        emitGameCommand({ gameId, playerId, commandType: 'END_TURN' });
    };


    // --- UI Rendering ---
    const viewingPlayer = localGameState.player1State.playerId === playerId ? localGameState.player1State : localGameState.player2State;

    return (
        <div style={{ width: '100%', height: '100vh', position: 'relative', overflow: 'hidden' }}>
            <CanvasWrapper
                ref={canvasRef}
                gameState={localGameState}
                playerId={playerId}
                onCardClickOnField={handleCardClickOnField}
                onEmptyFieldSlotClick={handleEmptyFieldSlotClick}
                onTableClick={handleTableClick}
                selectedHandCardInfo={selectedHandCardInfo}
                selectedAttackerInfo={selectedAttackerInfo}
                selectedAbilitySourceInfo={selectedAbilitySourceInfo}
                isTargetingMode={isTargetingMode}
                currentEffect={currentEffect}
                onEffectComplete={handleEffectComplete}
            />
            <div style={{ position: 'absolute', top: 10, right: 15, zIndex: 200 }}>
                <button
                    onClick={downloadLogs}
                    style={{
                        backgroundColor: 'rgba(211, 84, 0, 0.8)',
                        color: 'white',
                        border: '1px solid #f39c12'
                    }}
                    title="Download all frontend game logs for this session"
                >
                    Download Logs
                </button>
            </div>
            {/* ... UI Overlays ... */}
            <div style={{ position: 'absolute', top: 10, left: 15, color: 'white', pointerEvents: 'none', textShadow: '1px 1px 2px black' }}>
                <p style={{ margin: 0 }}>Turn: {localGameState.turnNumber} | {isMyTurn ? "Your Turn" : "Opponent's Turn"}</p>
                {isMyTurn && <p style={{ margin: 0 }}>Attacks Left: {MAX_ATTACKS_PER_TURN - viewingPlayer.attacksDeclaredThisTurn}</p>}
                <p style={{ color: '#FFD700', margin: '5px 0 0 0', fontStyle: 'italic' }}>{feedbackMessage}</p>
            </div>

            {/* Ability Panel */}
            {isMyTurn && !isTargetingMode && selectedAbilitySourceInfo && (
                <div style={{ position: 'absolute', bottom: '150px', left: '50%', transform: 'translateX(-50%)', backgroundColor: 'rgba(40,45,60,0.95)', border: '1px solid #7fceff', padding: '15px', borderRadius: '8px', zIndex: 110, color: 'white', display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '300px' }}>
                    <h4 style={{ marginTop: 0, marginBottom: '10px', textAlign: 'center' }}>{selectedAbilitySourceInfo.cardData.name} - Abilities</h4>
                    {CARD_ABILITIES[selectedAbilitySourceInfo.cardData.cardId].map(opt => <button key={opt.index} onClick={() => handleAbilityOptionSelect(opt)}>{opt.name}</button>)}
                    <button onClick={clearSelections} style={{ backgroundColor: '#dc3545' }}>Cancel</button>
                </div>
            )}

            <div className="html-hand-card-container">
                {viewingPlayer.hand.map(card => (
                    <HTMLHandCard
                        key={card.instanceId}
                        cardData={card}
                        onClick={handleHTMLCardClickInHand}
                        onMagnify={setMagnifiedCard}
                        isSelected={selectedHandCardInfo?.instanceId === card.instanceId}
                    />
                ))}
            </div>

            <div style={{ position: 'absolute', bottom: 10, right: 15 }}>
                {isMyTurn && <button onClick={handleEndTurn} disabled={isInputLocked()}>End Turn</button>}
            </div>
            <div style={{ position: 'absolute', bottom: 10, left: 15, color: 'white', pointerEvents: 'none' }}>
                <p style={{ margin: 0 }}>Hand: {viewingPlayer.handSize} | Deck: {viewingPlayer.deckSize} | Discard: {viewingPlayer.discardPileSize}</p>
            </div>

            {magnifiedCard && <MagnifiedCardView cardData={magnifiedCard} onClose={() => setMagnifiedCard(null)} />}
        </div>
    );
};


// --- Client-side State Logic ---

/**
 * Mutates a draft of the game state based on a single event.
 * This is the client-side equivalent of Game.apply().
 * @param {object} draftState - An Immer draft state object.
 * @param {object} event - The GameEvent object from the server.
 * @param {string} viewingPlayerId - The ID of the player viewing the game.
 */
function applyEventToState(draftState, event, viewingPlayerId) {
    const p1 = draftState.player1State;
    const p2 = draftState.player2State;

    const getPlayer = (id) => (p1.playerId === id ? p1 : p2);
    const findCard = (instanceId) => {
        let cardInfo = null;
        [p1, p2].forEach(p => {
            p.field.forEach((card, index) => {
                if (card && card.instanceId === instanceId) {
                    cardInfo = { card, owner: p, index };
                }
            });
        });
        return cardInfo;
    };

    switch (event.eventType) {
        case 'TURN_STARTED': { // Use block scope for constants
            draftState.currentPlayerId = event.newTurnPlayerId;
            draftState.turnNumber = event.newTurnNumber;
            const player = getPlayer(event.newTurnPlayerId);
            if (player) {
                player.attacksDeclaredThisTurn = 0;
                player.field.forEach(card => {
                    if (card) card.isExhausted = false;
                });
            }

            const isP1Turn = draftState.player1State.playerId === event.newTurnPlayerId;
            draftState.currentGameState = isP1Turn ? 'PLAYER_1_TURN' : 'PLAYER_2_TURN';
            break;
        }

        case 'PLAYER_DREW_CARD': {
            const player = getPlayer(event.playerId);
            player.deckSize = event.newDeckSize;
            player.handSize = event.newHandSize;
            if (event.playerId === viewingPlayerId && event.card) {
                player.hand.push(event.card);
            }
            break;
        }

        case 'CARD_PLAYED': {
            const player = getPlayer(event.playerId);

            // Set the authoritative hand size from the event
            player.handSize = event.newHandSize;

            // Only mutate the viewing player's actual hand array
            if (player.playerId === viewingPlayerId) {
                // Use the event's `fromHandIndex` to reliably remove the correct card.
                if (event.fromHandIndex < player.hand.length) {
                    player.hand.splice(event.fromHandIndex, 1);
                }
            }

            // Place the card on the field for both players
            const playedCard = { ...event.card, isExhausted: true };
            player.field[event.toFieldSlot] = event.card;
            break;
        }

        case 'ATTACK_DECLARED': {
            const attackerInfo = findCard(event.attackerInstanceId);
            if (attackerInfo) {
                attackerInfo.owner.attacksDeclaredThisTurn += 1;
                attackerInfo.card.isExhausted = true;
            }
            break;
        }

        case 'COMBAT_DAMAGE_DEALT': {
            const defenderInfo = findCard(event.defenderInstanceId);
            if (defenderInfo) {
                defenderInfo.card.currentLife = event.defenderLifeAfter;
            }
            break;
        }

        case 'CARD_DESTROYED': {
            const cardInfo = findCard(event.card.instanceId);
            if (cardInfo) {
                cardInfo.owner.field[cardInfo.index] = null;
                cardInfo.owner.discardPileSize += 1;
            }
            break;
        }

        case 'CARD_BUFFED':
        case 'CARD_DEBUFFED':
        case 'CARD_HEALED':
        case 'CARD_FLAG_CHANGED': {
            const cardInfo = findCard(event.targetInstanceId);
            if (cardInfo) {
                if (event.statAfter !== undefined) {
                    if (event.stat === "ATK") cardInfo.card.currentAttack = event.statAfter;
                    if (event.stat === "DEF") cardInfo.card.currentDefense = event.statAfter;
                }
                if (event.lifeAfter !== undefined) {
                    cardInfo.card.currentLife = event.lifeAfter;
                }
            }
            break;
        }

        case 'TURN_ENDED': {
            const endedPlayer = getPlayer(event.endedTurnPlayerId);
            if (endedPlayer && endedPlayer.playerId === viewingPlayerId) {
                // Simple hand size adjustment if needed, but PLAYER_DREW_CARD handles the truth.
                // The main logic is just changing the turn.
            }
            break;
        }

        case 'GAME_OVER':
            draftState.currentGameState = 'GAME_OVER';
            draftState.message = event.reason;
            break;
    }
}

/**
 * Translates a GameEvent into a visual effect object for the EffectQueue.
 * @param {object} event - The GameEvent from the server.
 * @returns {object|null} A visual effect object or null if no effect is needed.
 */
function translateEventToEffect(event) {
    switch (event.eventType) {
        case 'COMBAT_DAMAGE_DEALT':
            if (event.damageAfterDefense > 0) {
                return { type: 'DAMAGE', targetId: event.defenderInstanceId, amount: event.damageAfterDefense };
            }
            return { type: 'ZERO_DAMAGE', targetId: event.defenderInstanceId };

        case 'CARD_DESTROYED':
            return { type: 'CARD_DESTROYED', cardData: event.card };

        case 'CARD_HEALED':
            return { type: 'HEAL', targetId: event.targetInstanceId, amount: event.amount };

        case 'CARD_PLAYED':
            return { type: 'CARD_PLAYED', targetId: event.card.instanceId };

        case 'CARD_BUFFED':
            return { type: 'STAT_CHANGE', isBuff: true, targetId: event.targetInstanceId };

        case 'CARD_DEBUFFED':
            return { type: 'STAT_CHANGE', isBuff: false, targetId: event.targetInstanceId };

        case 'GAME_LOG_MESSAGE':
            if (event.level === 'EFFECT' || event.level === 'WARN') {
                // Could be extended to show floating text on screen
            }
            return null;

        default:
            return null;
    }
}


export default GameScreen;