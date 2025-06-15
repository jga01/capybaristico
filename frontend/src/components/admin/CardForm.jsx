import React, { useState, useEffect } from 'react';

const CardForm = ({ initialData, onSubmit, onCancel, isCreating }) => {
    const [formData, setFormData] = useState(initialData || {
        cardId: '', name: '', type: '', initialLife: 0, attack: 0, defense: 0,
        effectText: '', effectConfiguration: '[]', rarity: 'COMMON',
        imageUrl: '', flavorText: '', isDirectlyPlayable: true
    });
    const [jsonError, setJsonError] = useState('');

    useEffect(() => {
        setFormData(initialData || {
            cardId: '', name: '', type: '', initialLife: 0, attack: 0, defense: 0,
            effectText: '', effectConfiguration: '[]', rarity: 'COMMON',
            imageUrl: '', flavorText: '', isDirectlyPlayable: true
        });
    }, [initialData]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
    };

    const handleJsonChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        try {
            JSON.parse(value);
            setJsonError('');
        } catch (error) {
            setJsonError('Invalid JSON format.');
        }
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (jsonError) {
            alert('Cannot submit with invalid JSON in Effect Configuration.');
            return;
        }
        onSubmit(formData);
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h2>{isCreating ? 'Create New Card' : `Edit Card: ${initialData.name}`}</h2>
                <form onSubmit={handleSubmit} className="card-form">
                    <div className="form-group"><label>Card ID</label><input name="cardId" value={formData.cardId} onChange={handleChange} required /></div>
                    <div className="form-group"><label>Name</label><input name="name" value={formData.name} onChange={handleChange} required /></div>
                    <div className="form-group"><label>Type</label><input name="type" value={formData.type} onChange={handleChange} /></div>
                    <div className="form-group"><label>Image URL</label><input name="imageUrl" value={formData.imageUrl} onChange={handleChange} placeholder="e.g., my_card.png" /></div>
                    <div className="form-group"><label>Initial Life</label><input type="number" name="initialLife" value={formData.initialLife} onChange={handleChange} /></div>
                    <div className="form-group"><label>Attack</label><input type="number" name="attack" value={formData.attack} onChange={handleChange} /></div>
                    <div className="form-group"><label>Defense</label><input type="number" name="defense" value={formData.defense} onChange={handleChange} /></div>
                    <div className="form-group">
                        <label>Rarity</label>
                        <select name="rarity" value={formData.rarity} onChange={handleChange}>
                            {['COMMON', 'UNCOMMON', 'RARE', 'SUPER_RARE', 'LEGENDARY'].map(r => <option key={r} value={r}>{r}</option>)}
                        </select>
                    </div>
                    <div className="form-group full-width"><label>Effect Text</label><textarea name="effectText" value={formData.effectText} onChange={handleChange}></textarea></div>
                    <div className="form-group full-width"><label>Flavor Text</label><textarea name="flavorText" value={formData.flavorText} onChange={handleChange}></textarea></div>
                    <div className="form-group full-width">
                        <label>Effect Configuration (JSON)</label>
                        <textarea name="effectConfiguration" value={formData.effectConfiguration} onChange={handleJsonChange}></textarea>
                        {jsonError && <p className="json-error">{jsonError}</p>}
                    </div>
                    <div className="form-group full-width"><label><input type="checkbox" name="isDirectlyPlayable" checked={formData.isDirectlyPlayable} onChange={handleChange} /> Is Directly Playable</label></div>

                    <div className="form-actions">
                        <button type="button" className="admin-button secondary" onClick={onCancel}>Cancel</button>
                        <button type="submit" className="admin-button" disabled={!!jsonError}>Save Card</button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default CardForm;