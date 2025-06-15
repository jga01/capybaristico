import React, { useState, useEffect, useCallback } from 'react';
import { getAllCards, createCard, updateCard, deleteCard, reloadCardDefinitions } from '../../services/adminApiService';
import CardForm from './CardForm';

const CardManager = () => {
    const [cards, setCards] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const [editingCard, setEditingCard] = useState(null);
    const [isCreating, setIsCreating] = useState(false);

    const fetchCards = useCallback(async () => {
        try {
            setIsLoading(true);
            setError('');
            const data = await getAllCards();
            setCards(data);
        } catch (err) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchCards();
    }, [fetchCards]);

    const handleCreate = () => {
        setEditingCard(null);
        setIsCreating(true);
    };

    const handleEdit = (card) => {
        setIsCreating(false);
        // Deep copy to avoid issues with JSON string editing
        setEditingCard(JSON.parse(JSON.stringify(card)));
    };

    const handleDelete = async (id) => {
        if (window.confirm('Are you sure you want to delete this card?')) {
            try {
                await deleteCard(id);
                fetchCards();
            } catch (err) {
                setError(err.message);
            }
        }
    };

    const handleReload = async () => {
        if (window.confirm('This will update the database from the JSON definition files. Are you sure?')) {
            try {
                const message = await reloadCardDefinitions();
                alert(message);
                fetchCards();
            } catch (err) {
                setError(err.message);
            }
        }
    };

    const handleFormSubmit = async (formData) => {
        try {
            if (isCreating) {
                await createCard(formData);
            } else {
                await updateCard(editingCard.id, formData);
            }
            fetchCards();
            setEditingCard(null);
            setIsCreating(false);
        } catch (err) {
            setError(err.message);
            alert(`Error saving card: ${err.message}`);
        }
    };

    const handleCancelForm = () => {
        setEditingCard(null);
        setIsCreating(false);
    };

    return (
        <div className="card-manager">
            <h1>Card Management</h1>
            <div className="toolbar">
                <button className="admin-button" onClick={handleCreate}>Create New Card</button>
                <button className="admin-button secondary" onClick={handleReload}>Reload from JSON</button>
            </div>
            {isLoading && <p>Loading cards...</p>}
            {error && <p style={{ color: 'red' }}>Error: {error}</p>}

            {(editingCard || isCreating) &&
                <CardForm
                    initialData={editingCard}
                    isCreating={isCreating}
                    onSubmit={handleFormSubmit}
                    onCancel={handleCancelForm}
                />
            }

            <div className="card-list">
                {cards.map(card => (
                    <div key={card.id} className="card-summary">
                        <h3>{card.name} ({card.cardId})</h3>
                        <p><strong>Type:</strong> {card.type}</p>
                        <p><strong>Stats:</strong> L:{card.initialLife} / A:{card.attack} / D:{card.defense}</p>
                        <p><strong>Rarity:</strong> {card.rarity}</p>
                        <div className="actions">
                            <button className="admin-button secondary" onClick={() => handleEdit(card)}>Edit</button>
                            <button className="admin-button danger" onClick={() => handleDelete(card.id)} style={{ marginLeft: '10px' }}>Delete</button>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default CardManager;