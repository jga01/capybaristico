const API_BASE = '/api/admin';

// --- Card API ---
export const getAllCards = async () => {
    const response = await fetch(`${API_BASE}/cards`);
    if (!response.ok) throw new Error('Failed to fetch cards');
    return response.json();
};

export const createCard = async (cardData) => {
    const response = await fetch(`${API_BASE}/cards`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cardData),
    });
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to create card: ${errorText}`);
    }
    return response.json();
};

export const updateCard = async (id, cardData) => {
    const response = await fetch(`${API_BASE}/cards/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cardData),
    });
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to update card: ${errorText}`);
    }
    return response.json();
};

export const deleteCard = async (id) => {
    const response = await fetch(`${API_BASE}/cards/${id}`, {
        method: 'DELETE',
    });
    if (!response.ok) throw new Error('Failed to delete card');
};

export const reloadCardDefinitions = async () => {
    const response = await fetch(`${API_BASE}/cards/reload-definitions`, {
        method: 'POST',
    });
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to reload definitions: ${errorText}`);
    }
    return response.text();
};

export const getEffectOptions = async () => {
    const response = await fetch(`${API_BASE}/effect-options`);
    if (!response.ok) throw new Error('Failed to fetch effect options');
    return response.json();
};

// --- Game API ---
export const getAllGameIds = async () => {
    const response = await fetch(`${API_BASE}/games/ids`);
    if (!response.ok) throw new Error('Failed to fetch game IDs');
    return response.json();
};

export const getGameHistory = async (gameId) => {
    const response = await fetch(`/api/debug/game/${gameId}/history`);
    if (!response.ok) throw new Error(`Failed to fetch history for game ${gameId}`);
    return response.json();
};