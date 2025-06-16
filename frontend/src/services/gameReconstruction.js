import { produce } from 'immer';

// This map will be populated with all card definitions when a game replay is loaded
const cardDefinitionsMap = new Map();

const findCardOnField = (state, instanceId) => {
    let cardInfo = null;
    [state.player1State, state.player2State].forEach(player => {
        if (player && player.field) {
            player.field.forEach((card, index) => {
                if (card && card.instanceId === instanceId) {
                    cardInfo = { card, ownerPlayerId: player.playerId, index };
                }
            });
        }
    });
    return cardInfo;
};

const getPlayerState = (draft, playerId) => {
    if (draft.player1State.playerId === playerId) {
        return draft.player1State;
    }
    if (draft.player2State.playerId === playerId) {
        return draft.player2State;
    }
    return null;
};

// Main function to apply a single event to a game state DTO
export const applyEventToState = (currentState, event, allCardDefinitions) => {
    if (cardDefinitionsMap.size === 0 && allCardDefinitions) {
        allCardDefinitions.forEach(def => cardDefinitionsMap.set(def.cardId, def));
    }

    return produce(currentState, draft => {
        switch (event.eventType) {
            case 'GAME_STARTED':
                draft.turnNumber = 1;
                draft.currentPlayerId = event.startingPlayerId;
                break;

            case 'TURN_STARTED': {
                draft.turnNumber = event.newTurnNumber;
                draft.currentPlayerId = event.newTurnPlayerId;
                const activePlayerState = getPlayerState(draft, event.newTurnPlayerId);
                if (activePlayerState) {
                    activePlayerState.field.forEach(card => {
                        if (card) card.isExhausted = false;
                    });
                    activePlayerState.attacksDeclaredThisTurn = 0;
                }
                break;
            }
            case 'CARD_PLAYED': {
                const player = getPlayerState(draft, event.playerId);
                if (player && event.card) {
                    // Create a new object for the card on the field, setting its exhausted state.
                    const newCardOnField = { ...event.card, isExhausted: true };
                    player.field[event.toFieldSlot] = newCardOnField;
                    player.handSize = event.newHandSize;
                }
                break;
            }
            case 'COMBAT_DAMAGE_DEALT': {
                const defenderInfo = findCardOnField(draft, event.defenderInstanceId);
                if (defenderInfo) {
                    const owner = getPlayerState(draft, defenderInfo.ownerPlayerId);
                    const cardToUpdate = owner.field[defenderInfo.index];
                    if (cardToUpdate) cardToUpdate.currentLife = event.defenderLifeAfter;
                }
                break;
            }

            case 'CARD_DESTROYED': {
                const destroyedInfo = findCardOnField(draft, event.card.instanceId);
                if (destroyedInfo) {
                    const owner = getPlayerState(draft, destroyedInfo.ownerPlayerId);
                    owner.field[destroyedInfo.index] = null;
                    owner.discardPileSize += 1;
                }
                break;
            }

            case 'ATTACK_DECLARED': {
                const attackerInfo = findCardOnField(draft, event.attackerInstanceId);
                if (attackerInfo) {
                    const owner = getPlayerState(draft, attackerInfo.ownerPlayerId);
                    const cardToUpdate = owner.field[attackerInfo.index];
                    if (cardToUpdate) {
                        cardToUpdate.isExhausted = true;
                        owner.attacksDeclaredThisTurn += 1;
                    }
                }
                break;
            }

            case 'CARD_BUFFED':
            case 'CARD_DEBUFFED': {
                const buffedInfo = findCardOnField(draft, event.targetInstanceId);
                if (buffedInfo) {
                    const owner = getPlayerState(draft, buffedInfo.ownerPlayerId);
                    const cardToUpdate = owner.field[buffedInfo.index];
                    if (cardToUpdate) {
                        const amount = event.eventType === 'CARD_BUFFED' ? event.amount : -event.amount;
                        if (event.stat.toUpperCase() === 'ATK') cardToUpdate.currentAttack = event.statAfter;
                        if (event.stat.toUpperCase() === 'DEF') cardToUpdate.currentDefense = event.statAfter;
                        if (event.stat.toUpperCase() === 'MAX_LIFE') {
                            cardToUpdate.currentLife = event.statAfter; // Assuming statAfter reflects new life
                            cardToUpdate.baseLife += amount;
                        }
                    }
                }
                break;
            }

            case 'CARD_HEALED': {
                const healedInfo = findCardOnField(draft, event.targetInstanceId);
                if (healedInfo) {
                    const owner = getPlayerState(draft, healedInfo.ownerPlayerId);
                    const cardToUpdate = owner.field[healedInfo.index];
                    if (cardToUpdate) cardToUpdate.currentLife = event.lifeAfter;
                }
                break;
            }

            case 'CARD_TRANSFORMED': {
                const originalInfo = findCardOnField(draft, event.originalInstanceId);
                if (originalInfo) {
                    const owner = getPlayerState(draft, originalInfo.ownerPlayerId);
                    const newCardDef = cardDefinitionsMap.get(event.newCardDto.cardId);
                    if (newCardDef) {
                        const newCardInstanceDTO = {
                            instanceId: `transformed-${Date.now()}`,
                            cardId: newCardDef.cardId, name: newCardDef.name, type: newCardDef.type,
                            baseLife: newCardDef.initialLife, baseAttack: newCardDef.attack, baseDefense: newCardDef.defense,
                            currentLife: event.newCardDto.currentLife || newCardDef.initialLife,
                            currentAttack: newCardDef.attack, currentDefense: newCardDef.defense,
                            effectText: newCardDef.effectText, rarity: newCardDef.rarity, imageUrl: newCardDef.imageUrl,
                            isExhausted: true, abilities: [], effectFlags: {},
                        };
                        owner.field[originalInfo.index] = newCardInstanceDTO;
                    }
                }
                break;
            }

            case 'PLAYER_DREW_CARD': {
                const drawingPlayer = getPlayerState(draft, event.playerId);
                drawingPlayer.handSize = event.newHandSize;
                drawingPlayer.deckSize = event.newDeckSize;
                if (event.card && draft.viewingPlayerPerspectiveId === event.playerId) {
                    drawingPlayer.hand.push(event.card);
                }
                break;
            }
            case 'PLAYER_OVERDREW_CARD': {
                const overdrawingPlayer = getPlayerState(draft, event.playerId);
                overdrawingPlayer.deckSize = event.newDeckSize;
                overdrawingPlayer.discardPileSize = event.newDiscardPileSize;
                break;
            }

            case 'GAME_OVER':
                draft.currentGameState = 'GAME_OVER_PLAYER_1_WINS'; // Simplified for display
                break;

            default:
                break;
        }
    });
};