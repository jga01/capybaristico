# CapyCards (Working Title)

CapyCards is a two-player competitive digital trading card game (TCG/CCG) inspired by classics like Pokémon, Magic: The Gathering, and Yu-Gi-Oh!. Players battle with randomly generated, rarity-balanced decks, aiming to outwit their opponent using cards with unique fantasy themes and powerful effects, including the illustrious Capybara!

This project is currently in active development, focusing on a Minimum Viable Demo Product (MVDP) with an expanding set of card effects.

## Features (MVDP - In Progress)

*   **Two-Player Online Gameplay:** Battle against another player in real-time.
*   **Lobby System:** Create your own game lobby or join an existing one.
*   **Card-Based Combat & Effects:**
    *   **Dynamic Turns:** Draw until your hand has 5 cards (P1T1 skips draw).
    *   **Flexible Plays:** Play multiple cards from your hand to your field (4 slots) per turn. Played cards enter exhausted.
    *   **Strategic Attacks:** Declare up to two attacks per turn with un-exhausted field cards.
    *   **Combat Resolution:** `Attacker's ATK - Defender's DEF = Damage to Defender's Life`. Min 0 damage. No automatic counter-attack.
    *   **Emerging Card Effects:** A growing number of cards feature unique abilities that trigger on play, death, during combat, or passively modify game rules (e.g., AoE damage, immunities, conditional stat changes, revival).
*   **Win Conditions:**
    1.  **Deck Out:** Opponent attempts to draw from an empty deck.
    2.  **Field Control Loss (Opponent):** Opponent's field is empty for two of their consecutive turns.
*   **Deck Generation:** 20-card decks are randomly generated respecting rarity distribution limits (Max 1 Legendary/Super-Rare, Max 2 Rares, rest Common/Uncommon).
*   **Dynamic Game Board UI:** Visual representation of hands, fields, decks, discard piles, and game information.
*   **Unique Theme:** Fantasy cards featuring categories like "Capybara," "American," "Femboy," "Indigenous," "European," and "Undead," driving card synergies and effect interactions.

## Technology Stack

*   **Frontend:** React.js (with Vite)
*   **Backend:** Java (Spring Boot)
*   **Real-time Communication:** Socket.IO (`netty-socketio` for Java, `socket.io-client` for React)
*   **Database:** PostgreSQL
*   **Version Control:** Git

## Project Structure

*   `backend/`: Contains the Java Spring Boot application.
*   `frontend/`: Contains the React.js application.
*   `database/schema.sql`: Provides the definitive database structure.

## Setup and Installation

### Prerequisites

*   Java JDK 21 or later
*   Apache Maven 3.6+
*   Node.js 18.x or later (with npm)
*   PostgreSQL server (e.g., version 12+)

### 1. Database Setup

1.  Ensure PostgreSQL is running.
2.  Create a database (e.g., `capycards_db`) and a user (e.g., `capycards_user`) with privileges.
    ```sql
    -- Example PSQL:
    CREATE USER capycards_user WITH PASSWORD 'your_password';
    CREATE DATABASE capycards_db OWNER capycards_user;
    GRANT ALL PRIVILEGES ON DATABASE capycards_db TO capycards_user;
    ```
    *(Match password in `application.properties`)*
3.  The backend will initialize the database using `database/schema.sql`. For development purposes, `spring.jpa.hibernate.ddl-auto=update` can be utilized, but `database/schema.sql` defines the definitive database structure.

### 2. Backend Setup

1.  Navigate to `backend/`.
2.  Configure `src/main/resources/application.properties` with your DB details.
3.  Run: `mvn spring-boot:run`
    (Socket.IO on port 9092, card definitions will be seeded if DB is empty).

### 3. Frontend Setup

1.  Navigate to `frontend/`.
2.  Install: `npm install`
3.  Run: `npm run dev` (Usually on `http://localhost:5173`)
4.  Open two browser tabs to play.

## Gameplay Overview (MVDP Rules - Evolving)

*   **Objective:** Win by Deck Out or forcing Opponent's Field Control Loss.
*   **Deck:** 20 cards, random generation with rarity limits.
*   **Starting Hand:** 5 cards.
*   **Turn Start:** Draw until hand is 5 (P1T1 skips). Un-exhaust your field cards. Start-of-turn effects resolve.
*   **Main Phase:**
    *   Play any number of cards from hand to empty field slots (max 4 on field). Cards enter exhausted.
    *   Declare up to 2 attacks with un-exhausted field cards.
    *   (Future) Activate card abilities that require player choice.
*   **Combat:** `Attacker ATK - Defender DEF = Damage`. Destroyed at 0 Life. Card effects can alter combat.
*   **Card Effects:** Many cards have unique abilities triggered by game events (play, death, attack, etc.) or are passive. These are central to strategy.
*   **End Turn:** Discard excess cards (if hand > 5). Check if you lost via Field Control. Pass turn.

## Contributing

Contributions are welcome! If you have suggestions for improvements, new features, or bug fixes, please feel free to:
1. Open an issue to discuss the change.
2. Fork the repository and create a pull request with your changes.

## License

[TODO: Add a LICENSE file to the project (e.g., MIT, GPL, Apache 2.0) and update this section to reflect the chosen license. For example: 'This project is licensed under the MIT License - see the LICENSE.md file for details.']

## Current Status & Next Steps

**[TODO: Update this section with the latest project progress, key achievements since the last update, and immediate next steps. Focus on the Card Effect System implementation status if that's still the priority.]**

The project is an MVDP with a functional core game loop, lobby system, and UI. Key game rule changes (deck generation, draw up to 5, multiple card plays, field control loss) have been implemented.
The current major focus is on implementing the **Card Effect System** for the 31 defined cards. This involves:
*   Backend logic for diverse effect types (damage, healing, stat changes, transformations, RNG, etc.).
*   A system for triggering and resolving effects, including future priority rules.
*   Frontend UI for targeting and visualizing effects.

See the "Project State of the Union" document or commit history for detailed progress.

## Future Enhancements (Post-Core Effects)

*   Full implementation of all 31+ card effects.
*   Advanced effect interactions and priority queue for simultaneous effects.
*   Deck Builder (if desired beyond random generation).
*   Visual Polish: Custom art, animations, refined UI/UX, game log.
*   User Accounts & Persistence.
*   Sound Effects.