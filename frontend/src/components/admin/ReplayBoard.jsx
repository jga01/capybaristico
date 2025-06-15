import React from 'react';

const ReplayCard = ({ card }) => {
    if (!card) return <div className="replay-card empty"></div>;

    const imageUrl = card.imageUrl ? `/assets/cards_images/${card.imageUrl}` : '/assets/cards_images/back.png';

    return (
        <div className="replay-card" title={card.effectText}>
            <img src={imageUrl} alt={card.name} />
            <div className="replay-card-name">{card.name}</div>
            <div className="replay-card-stats">
                <span>A: {card.currentAttack}</span>
                <span>D: {card.currentDefense}</span>
                <span>L: {card.currentLife}</span>
            </div>
            {card.isExhausted && <div className="exhausted-overlay" title="Exhausted">E</div>}
        </div>
    );
};

const ReplayBoard = ({ playerState, isOpponent = false }) => {
    if (!playerState) return <div className="replay-board">Loading player...</div>;

    return (
        <div className={`replay-board ${isOpponent ? 'opponent' : ''}`}>
            <h4>{playerState.displayName} (Deck: {playerState.deckSize} | Hand: {playerState.handSize} | Discard: {playerState.discardPileSize})</h4>
            <div className="replay-field">
                {Array.from({ length: 4 }).map((_, index) => (
                    <ReplayCard key={playerState.field[index]?.instanceId || `empty-${index}`} card={playerState.field[index]} />
                ))}
            </div>
        </div>
    );
};

const ReplayArea = ({ gameState }) => {
    if (!gameState) return <div>Select a game to view the replay.</div>;

    const { player1State, player2State, viewingPlayerPerspectiveId } = gameState;
    const viewingPlayerIsP1 = viewingPlayerPerspectiveId === player1State.playerId;

    const selfPlayer = viewingPlayerIsP1 ? player1State : player2State;
    const opponentPlayer = viewingPlayerIsP1 ? player2State : player1State;

    return (
        <div className="replay-board-wrapper">
            <ReplayBoard playerState={opponentPlayer} isOpponent={true} />
            <ReplayBoard playerState={selfPlayer} />
        </div>
    )
}

export default ReplayArea;