import React, { useState, useEffect, useCallback, useRef } from 'react';
import CanvasWrapper from './ThreeCanvas';
import { emitGameAction } from '../services/socketService';
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

const GameScreen = ({ gameState, playerId, gameId }) => {
    // === State for User Selections & Game Flow ===
    const [selectedHandCardInfo, setSelectedHandCardInfo] = useState(null);
    const [selectedAttackerInfo, setSelectedAttackerInfo] = useState(null);
    const [selectedAbilitySourceInfo, setSelectedAbilitySourceInfo] = useState(null);
    const [selectedAbilityOption, setSelectedAbilityOption] = useState(null);
    const [isTargetingMode, setIsTargetingMode] = useState(false);
    const [attacksMadeThisTurn, setAttacksMadeThisTurn] = useState(0);
    const [isMyTurn, setIsMyTurn] = useState(false);

    // === State for UI Feedback & Logging ===
    const [feedbackMessage, setFeedbackMessage] = useState('');
    const [gameLog, setGameLog] = useState([]);
    const [magnifiedCard, setMagnifiedCard] = useState(null);

    // === State for the Visual Effect System ===
    const [effectQueue, setEffectQueue] = useState([]);
    const [currentEffect, setCurrentEffect] = useState(null);

    // === Refs to manage state in callbacks and for performance ===
    const prevGameStateRef = useRef(null);
    const isMyTurnRef = useRef(isMyTurn);
    const lastLoggedServerMessageRef = useRef({ message: "", turn: -1 });
    const canvasRef = useRef(); // Ref for the ThreeCanvas component

    // Update refs whenever their state counterparts change
    useEffect(() => { isMyTurnRef.current = isMyTurn; }, [isMyTurn]);

    // --- Core System Logic ---

    /**
     * The Event Detector.
     * Compares the new gameState with the previous one to find discrete events
     * (damage, healing, card plays, deaths) and adds them to the effectQueue.
     */
    useEffect(() => {
        if (!gameState || !prevGameStateRef.current) {
            prevGameStateRef.current = gameState;
            return;
        }

        const newEffects = [];
        const prevGs = prevGameStateRef.current;
        const currentGs = gameState;

        const getPrevCard = (instanceId) => {
            const p1card = prevGs.player1State.field.find(c => c && c.instanceId === instanceId);
            if (p1card) return p1card;
            return prevGs.player2State.field.find(c => c && c.instanceId === instanceId);
        };

        // --- Generic Event Detection ---
        const allCurrentCards = [...currentGs.player1State.field, ...currentGs.player2State.field].filter(c => c);

        allCurrentCards.forEach(card => {
            const prevCard = getPrevCard(card.instanceId);
            if (prevCard) {
                if (prevCard.currentLife > card.currentLife) newEffects.push({ type: 'DAMAGE', targetId: card.instanceId, amount: prevCard.currentLife - card.currentLife });
                if (prevCard.currentLife < card.currentLife) newEffects.push({ type: 'HEAL', targetId: card.instanceId, amount: card.currentLife - prevCard.currentLife });
                if (prevCard.currentAttack !== card.currentAttack) newEffects.push({ type: 'STAT_CHANGE', targetId: card.instanceId, stat: 'ATK', isBuff: card.currentAttack > prevCard.currentAttack });
                if (prevCard.currentDefense !== card.currentDefense) newEffects.push({ type: 'STAT_CHANGE', targetId: card.instanceId, stat: 'DEF', isBuff: card.currentDefense > prevCard.currentDefense });
            } else {
                newEffects.push({ type: 'CARD_PLAYED', targetId: card.instanceId });
            }
        });

        const allPrevCards = [...prevGs.player1State.field, ...prevGs.player2State.field].filter(c => c);
        allPrevCards.forEach(prevCard => {
            if (!allCurrentCards.some(c => c.instanceId === prevCard.instanceId)) {
                newEffects.push({ type: 'CARD_DESTROYED', cardData: prevCard });
            }
        });

        // --- Card-Specific Effect Detection ---
        if (newEffects.filter(e => e.type === 'DAMAGE' && e.amount === 3).length >= 2) {
            const olivio = allCurrentCards.find(c => c.cardId === 'CAP019');
            if (olivio) newEffects.unshift({ type: 'SHOCKWAVE', sourceId: olivio.instanceId, color: '#8B4513' });
        }
        if (newEffects.filter(e => e.type === 'DAMAGE' && e.amount === 2).length >= 2) {
            const makachu = allCurrentCards.find(c => c.cardId === 'CAP020');
            if (makachu) newEffects.unshift({ type: 'SHOCKWAVE', sourceId: makachu.instanceId, color: '#7fdbff' });
        }
        newEffects.forEach(effect => {
            if (effect.type === 'DAMAGE') {
                const attacker = allCurrentCards.find(c => c.cardId === 'CAP012');
                if (attacker && Math.abs(effect.amount - attacker.currentAttack * 2) <= 1) {
                    newEffects.push({ type: 'AURA_PULSE', targetId: attacker.instanceId, color: '#FF4136' });
                }
            }
        });

        // --- Finalizing the Queue ---
        if (newEffects.length > 0) {
            newEffects.sort((a, b) => {
                const aScore = a.type.includes('SHOCKWAVE') ? 0 : a.type === 'DAMAGE' ? 1 : a.type === 'CARD_DESTROYED' ? 3 : 2;
                const bScore = b.type.includes('SHOCKWAVE') ? 0 : b.type === 'DAMAGE' ? 1 : b.type === 'CARD_DESTROYED' ? 3 : 2;
                return aScore - bScore;
            });
            setEffectQueue(prev => [...prev, ...newEffects]);
        }
        prevGameStateRef.current = gameState;
    }, [gameState, playerId]);

    /**
     * The Effect Queue Manager.
     * Watches the queue and feeds effects to the renderer one by one.
     */
    useEffect(() => {
        if (!currentEffect && effectQueue.length > 0) {
            setCurrentEffect(effectQueue[0]);
        }
    }, [effectQueue, currentEffect]);

    const handleEffectComplete = useCallback(() => {
        setCurrentEffect(null);
        setEffectQueue(prev => prev.slice(1));
    }, []);

    // --- Turn Management & Logging ---
    useEffect(() => {
        if (!gameState || !playerId) return;
        const gameIsOver = gameState.currentGameState.includes("GAME_OVER");
        const evaluationIsMyTurn = gameState.currentPlayerId === playerId && !gameIsOver;
        if (evaluationIsMyTurn !== isMyTurn) {
            setIsMyTurn(evaluationIsMyTurn);
            if (evaluationIsMyTurn) {
                setAttacksMadeThisTurn(0);
                setFeedbackMessage("Your turn!");
                handleTableClick();
            }
        }
    }, [gameState, playerId, isMyTurn]);

    const addLogEntry = useCallback((message) => {
        setGameLog(prev => [{ timestamp: Date.now(), message }, ...prev].slice(0, 100));
    }, []);

    useEffect(() => {
        if (gameState?.message) {
            const { message, turnNumber } = gameState;
            if (message !== lastLoggedServerMessageRef.current.message || turnNumber !== lastLoggedServerMessageRef.current.turn) {
                addLogEntry(message);
                lastLoggedServerMessageRef.current = { message, turn: turnNumber };
            }
        }
    }, [gameState, addLogEntry]);

    // --- User Interaction Handlers (locked during effects) ---
    const isInputLocked = () => !!currentEffect;

    const handleTableClick = useCallback(() => {
        if (isInputLocked()) return;
        setFeedbackMessage("Selection cleared.");
        setSelectedHandCardInfo(null);
        setSelectedAttackerInfo(null);
        setSelectedAbilitySourceInfo(null);
        setSelectedAbilityOption(null);
        setIsTargetingMode(false);
    }, []);

    const handleHTMLCardClickInHand = (cardData) => {
        if (isInputLocked() || !isMyTurnRef.current) return;
        if (selectedAttackerInfo || selectedAbilitySourceInfo) {
            setFeedbackMessage("Another action is pending. Cancel it first.");
            return;
        }
        if (selectedHandCardInfo?.instanceId === cardData.instanceId) {
            handleTableClick();
        } else {
            setSelectedHandCardInfo({ instanceId: cardData.instanceId, cardData });
            setFeedbackMessage(`Selected ${cardData.name} from hand.`);
        }
    };

    const handleEmptyFieldSlotClick = (ownerPlayerId, fieldSlotIndex) => {
        if (isInputLocked() || !isMyTurnRef.current || !selectedHandCardInfo || ownerPlayerId !== playerId) return;
        const handCardIndex = viewingPlayer.hand.findIndex(c => c.instanceId === selectedHandCardInfo.instanceId);
        if (handCardIndex === -1) {
            setFeedbackMessage("Error: Card not found in hand.");
            handleTableClick();
            return;
        }
        emitGameAction({ gameId, playerId, actionType: 'PLAY_CARD', handCardIndex, targetFieldSlot: fieldSlotIndex });
        handleTableClick();
    };

    const handleCardClickOnField = (cardData, mesh, ownerId, fieldIndex) => {
        if (isInputLocked() || !isMyTurnRef.current || !cardData) return;
        const isOwnCard = ownerId === playerId;

        if (isTargetingMode) {
            if (selectedAttackerInfo) {
                if (isOwnCard) { setFeedbackMessage("Cannot attack your own card."); return; }
                emitGameAction({ gameId, playerId, actionType: 'ATTACK', attackerFieldIndex: selectedAttackerInfo.fieldIndex, defenderFieldIndex: fieldIndex });
                setAttacksMadeThisTurn(prev => prev + 1);
            } else if (selectedAbilitySourceInfo && selectedAbilityOption) {
                emitGameAction({ gameId, playerId, actionType: 'ACTIVATE_ABILITY', sourceCardInstanceId: selectedAbilitySourceInfo.instanceId, targetCardInstanceId: cardData.instanceId, abilityOptionIndex: selectedAbilityOption.index });
            }
            handleTableClick();
            return;
        }

        if (isOwnCard) {
            if (CARD_ABILITIES[cardData.cardId]) {
                setSelectedAbilitySourceInfo({ instanceId: cardData.instanceId, cardData, fieldIndex });
                setFeedbackMessage(`Selected ${cardData.name} for ability.`);
            } else if (attacksMadeThisTurn < MAX_ATTACKS_PER_TURN) {
                setSelectedAttackerInfo({ instanceId: cardData.instanceId, cardData, fieldIndex });
                setIsTargetingMode(true);
                setFeedbackMessage(`Selected ${cardData.name} to attack.`);
            } else {
                setFeedbackMessage("No attacks left.");
            }
        }
    };

    const handleAbilityOptionSelect = (abilityOpt) => {
        if (isInputLocked() || !selectedAbilitySourceInfo) return;
        setSelectedAbilityOption(abilityOpt);
        if (!abilityOpt.requiresTarget || abilityOpt.requiresTarget === "NONE") {
            emitGameAction({ gameId, playerId, actionType: 'ACTIVATE_ABILITY', sourceCardInstanceId: selectedAbilitySourceInfo.instanceId, abilityOptionIndex: abilityOpt.index });
            handleTableClick();
        } else {
            setIsTargetingMode(true);
            setFeedbackMessage(`Ability '${abilityOpt.name}' requires a target.`);
        }
    };

    const handleEndTurn = () => {
        if (isInputLocked() || !isMyTurnRef.current) return;
        handleTableClick();
        emitGameAction({ gameId, playerId, actionType: 'END_TURN' });
    };

    // --- UI Rendering ---
    const renderAbilityOptionsPanel = () => {
        if (isInputLocked() || !isMyTurnRef.current || !selectedAbilitySourceInfo || isTargetingMode) return null;
        const abilities = CARD_ABILITIES[selectedAbilitySourceInfo.cardData.cardId];
        if (!abilities) return null;
        return (<div style={{ position: 'absolute', bottom: '150px', left: '50%', transform: 'translateX(-50%)', backgroundColor: 'rgba(40,45,60,0.95)', border: '1px solid #7fceff', padding: '15px', borderRadius: '8px', zIndex: 110, color: 'white', display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '300px' }}>
            <h4 style={{ marginTop: 0, marginBottom: '10px', textAlign: 'center' }}>{selectedAbilitySourceInfo.cardData.name} - Abilities</h4>
            {abilities.map(opt => <button key={opt.index} onClick={() => handleAbilityOptionSelect(opt)}>{opt.name}</button>)}
            <button onClick={handleTableClick} style={{ backgroundColor: '#dc3545' }}>Cancel</button>
        </div>);
    };

    const renderLogEntry = (entry, index) => <div key={`${entry.timestamp}-${index}`}>{entry.message}</div>;

    if (!gameState?.player1State || !gameState.player2State) {
        return <div style={{ color: 'white', textAlign: 'center', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>Loading game...</div>;
    }

    const { player1State, player2State, currentPlayerId, currentGameState, turnNumber } = gameState;
    const viewingPlayer = playerId === player1State.playerId ? player1State : player2State;

    return (
        <div style={{ width: '100%', height: '100vh', position: 'relative', overflow: 'hidden' }}>
            <CanvasWrapper
                ref={canvasRef}
                gameState={gameState}
                playerId={playerId}
                onCardClickOnField={handleCardClickOnField}
                onEmptyFieldSlotClick={handleEmptyFieldSlotClick}
                onTableClick={handleTableClick}
                selectedHandCardInfo={selectedHandCardInfo}
                selectedAttackerInfo={selectedAttackerInfo}
                selectedAbilitySourceInfo={selectedAbilitySourceInfo}
                selectedAbilityOption={selectedAbilityOption}
                isTargetingMode={isTargetingMode}
                currentEffect={currentEffect}
                onEffectComplete={handleEffectComplete}
            />

            {/* UI Overlays */}
            <div style={{ position: 'absolute', top: 10, left: 15, color: 'white', pointerEvents: 'none', textShadow: '1px 1px 2px black' }}>
                <p style={{ margin: 0 }}>Turn: {turnNumber} | {isMyTurn ? "Your Turn" : "Opponent's Turn"}</p>
                {isMyTurn && <p style={{ margin: 0 }}>Attacks Left: {MAX_ATTACKS_PER_TURN - attacksMadeThisTurn}</p>}
                <p style={{ color: '#FFD700', margin: '5px 0 0 0', fontStyle: 'italic' }}>{feedbackMessage}</p>
            </div>
            <div style={{ position: 'absolute', top: 10, right: 15, width: 350, maxHeight: 120, overflowY: 'auto', backgroundColor: 'rgba(0,0,0,0.5)', padding: 8, borderRadius: 5, color: 'white' }}>
                {gameLog.map(renderLogEntry)}
            </div>

            {renderAbilityOptionsPanel()}

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
                <p style={{ margin: 0 }}>Hand: {viewingPlayer.hand.length} | Deck: {viewingPlayer.deckSize} | Discard: {viewingPlayer.discardPileSize}</p>
            </div>

            {magnifiedCard && <MagnifiedCardView cardData={magnifiedCard} onClose={() => setMagnifiedCard(null)} />}
        </div>
    );
};

export default GameScreen;