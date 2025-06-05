import React from 'react';
import './MagnifiedCardView.css';

const MagnifiedCardView = ({ cardData, onClose }) => {
    if (!cardData) return null;

    const imageUrl = cardData.imageUrl
        ? (cardData.imageUrl.startsWith('/') ? cardData.imageUrl : `/assets/cards_images/${cardData.imageUrl}`)
        : '/assets/cards_images/back.png';

    return (
        <div className="magnified-card-overlay" onClick={onClose}>
            <div className="magnified-card-content" onClick={(e) => e.stopPropagation()}>
                <button className="magnified-card-close-btn" onClick={onClose}>×</button>
                <div className="magnified-card-image-container">
                    <img src={imageUrl} alt={cardData.name} />
                </div>
                <div className="magnified-card-details">
                    <h3>{cardData.name}</h3>
                    <p><strong>ID:</strong> {cardData.cardId}</p>
                    <p><strong>Type:</strong> {cardData.type || 'N/A'}</p>
                    <div className="magnified-card-stats">
                        <span><strong>L:</strong> {cardData.currentLife}</span>
                        <span><strong>A:</strong> {cardData.currentAttack}</span>
                        <span><strong>D:</strong> {cardData.currentDefense}</span>
                    </div>
                    <p className="magnified-card-rarity"><strong>Rarity:</strong> {cardData.rarity || 'N/A'}</p>
                    <h4>Effect:</h4>
                    <p className="magnified-card-effect">{cardData.effectText || 'No effect text.'}</p>
                </div>
            </div>
        </div>
    );
};

export default MagnifiedCardView;