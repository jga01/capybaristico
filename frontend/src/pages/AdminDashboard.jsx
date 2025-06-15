import React, { useState } from 'react';
import CardManager from '../components/admin/CardManager';
import GameVisualizer from '../components/admin/GameVisualizer';
import '../components/admin/AdminDashboard.css';

const AdminDashboard = () => {
    const [activeView, setActiveView] = useState('cards');

    return (
        <div className="admin-dashboard">
            <nav className="admin-sidebar">
                <h2>Capybaristico Admin</h2>
                <ul>
                    <li className={activeView === 'cards' ? 'active' : ''} onClick={() => setActiveView('cards')}>
                        Card Management
                    </li>
                    <li className={activeView === 'games' ? 'active' : ''} onClick={() => setActiveView('games')}>
                        Game Visualizer
                    </li>
                </ul>
            </nav>
            <main className="admin-content">
                {activeView === 'cards' && <CardManager />}
                {activeView === 'games' && <GameVisualizer />}
            </main>
        </div>
    );
};

export default AdminDashboard;