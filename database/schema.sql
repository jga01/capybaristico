CREATE TABLE cards (
    id SERIAL PRIMARY KEY,                      -- Auto-incrementing integer primary key
    card_id VARCHAR(100) NOT NULL UNIQUE,       -- A unique string identifier for the card (e.g., "CAP001_PYROBLAST")
    name VARCHAR(100) NOT NULL,
    type VARCHAR(100),                          -- e.g., "Capybara, Fire", "Undead, Spell" (comma-separated or array)
    initial_life INTEGER NOT NULL,
    attack INTEGER NOT NULL,
    defense INTEGER NOT NULL,
    effect_text TEXT,
    rarity VARCHAR(20) CHECK (rarity IN ('COMMON', 'UNCOMMON', 'RARE', 'SUPER_RARE', 'LEGENDARY')), -- Example rarities
    image_url VARCHAR(255),                     -- Path/URL to card art
    flavor_text TEXT,
    -- (Optional) version INTEGER DEFAULT 1,    -- For card versioning if designs change
    -- (Optional) created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- (Optional) updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    CONSTRAINT initial_life_non_negative CHECK (initial_life >= 0),
    CONSTRAINT attack_non_negative CHECK (attack >= 0),
    CONSTRAINT defense_non_negative CHECK (defense >= 0)
);