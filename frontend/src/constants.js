export const MAX_ATTACKS_PER_TURN = 2;

// Card dimensions
export const CARD_WIDTH = 1.2;
export const CARD_HEIGHT = CARD_WIDTH * 1.4; // Maintain aspect ratio
export const CARD_DEPTH = 0.02; // Very thin for a card feel

export const CARD_ABILITIES = {
  "CAP027": [ // GGaego
    { name: "Reduce Target DEF by 2", index: 0, requiresTarget: "ANY_FIELD_CARD", description: "Select any card on the field to permanently reduce its DEF by 2. (Once per turn)" },
    { name: "Global ATK Buff", index: 1, requiresTarget: "NONE", description: "Give +2 ATK to all friendly cards and +1 ATK to all enemy cards for this turn. (Once per turn)" },
    { name: "Heal Self +3", index: 2, requiresTarget: "NONE", description: "GGaego heals itself for 3 LIFE. (Once per turn)" },
  ],
  "CAP006": [ // Aop
    { name: "Roll a Die", index: 0, requiresTarget: "NONE", description: "Roll a six-sided die. On a 1 or 6, Aop gains +1 ATK and +4 DEF for the turn. (Once per turn)" }
  ],
  "CAP020": [ // Makachu
    { name: "Rain", index: 0, requiresTarget: "NONE", description: "Deal 2 damage to all non-Capybara cards on the field. (Once per game)" }
  ],
  "CAP018": [ // Gloire
    { name: "Flip Coin", index: 0, requiresTarget: "NONE", description: "Randomly either heal a friendly card for 5 life, or deal 3 damage to Gloire." }
  ],
  "CAP015": [ // PH
    { name: "Heal Ally +1", index: 0, requiresTarget: "OWN_FIELD_CARD", description: "Heal a friendly Jamestiago or Menino-Veneno for 1 life instead of attacking." }
  ],
  "CAP024": [ // Kizer
    { name: "Heal Ally +1", index: 0, requiresTarget: "OWN_FIELD_CARD", description: "Heal a friendly Kahina, Swettie, Ariel, Makachu, or any Femboy for 1 life instead of attacking." }
  ],
};

export const PARTICLE_CONFIGS = {
  // Explodes in a sphere, good for impacts. Renders on top of cards.
  DAMAGE: {
    shape: 'sphere', count: 80, lifetime: { min: 0.6, max: 1.0 },
    size: { min: 0.03, max: 0.08 }, radius: { min: 0.8, max: 1.5 },
    color: '#ff4136', opacity: 1, depthWrite: false,
  },
  // Drifts gently upwards. Renders on top of cards.
  HEAL: {
    shape: 'fountain', count: 50, lifetime: { min: 1.2, max: 1.8 },
    size: { min: 0.04, max: 0.09 }, radius: { min: 0.3, max: 0.6 },
    color: '#2ecc40', opacity: 1, depthWrite: false,
  },
  // Quick upward burst. Renders on top of cards.
  BUFF: {
    shape: 'fountain', count: 60, lifetime: { min: 0.8, max: 1.2 },
    size: { min: 0.05, max: 0.1 }, radius: { min: 0.2, max: 0.5 },
    color: '#7fdbff', opacity: 1, depthWrite: false,
  },
  // A quick flash of grey/white particles, like a shield deflecting a blow.
  BLOCK: {
    shape: 'sphere', count: 50, lifetime: { min: 0.3, max: 0.6 },
    size: { min: 0.05, max: 0.1 }, radius: { min: 0.4, max: 0.7 },
    color: '#d3d3d3', opacity: 0.9, depthWrite: false,
  },
  // Sinking, heavy feel. Renders on top of cards.
  DEBUFF: {
    shape: 'fountain', count: 60, lifetime: { min: 1.0, max: 1.5 },
    size: { min: 0.06, max: 0.12 }, radius: { min: -0.5, max: -0.2 }, // Negative for downward
    color: '#b10dc9', opacity: 0.9, depthWrite: false,
  },
  // A chaotic, smoky sphere. Renders BEHIND cards for a cool effect.
  DESTROY: {
    shape: 'sphere', count: 120, lifetime: { min: 1.0, max: 1.5 },
    size: { min: 0.08, max: 0.15 }, radius: { min: 1.0, max: 2.0 },
    color: '#555555', opacity: 0.8, depthWrite: true, // KEY: Renders with depth
  },
  // A ground-based shockwave. Renders with depth to stay on the table.
  CARD_PLAYED: {
    shape: 'sphere', count: 150, lifetime: { min: 0.5, max: 0.8 },
    size: { min: 0.04, max: 0.08 }, radius: { min: 1.8, max: 2.5 },
    color: '#ffffff', opacity: 1, depthWrite: true,
  },
  // A large, outward blast. Renders with depth.
  SHOCKWAVE: {
    shape: 'sphere', count: 300, lifetime: { min: 0.8, max: 1.4 },
    size: { min: 0.06, max: 0.12 }, radius: { min: 3.0, max: 5.0 },
    color: '#FFFFFF', opacity: 1, depthWrite: true,
  },
  // A slow, mesmerizing vortex. Renders on top.
  AURA_PULSE: {
    shape: 'vortex', count: 40, lifetime: { min: 1.5, max: 2.2 },
    size: { min: 0.08, max: 0.15 }, radius: { min: 0.8, max: 1.2 },
    color: '#f0f', opacity: 0.8, depthWrite: false,
  },
};