export const MAX_ATTACKS_PER_TURN = 2;

// Card dimensions
export const CARD_WIDTH = 1.2;
export const CARD_HEIGHT = CARD_WIDTH * 1.4; // Maintain aspect ratio
export const CARD_DEPTH = 0.02; // Very thin for a card feel

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
  TRANSFORM: {
    shape: 'vortex', count: 150, lifetime: { min: 1.0, max: 1.5 },
    size: { min: 0.05, max: 0.15 }, radius: { min: 0.5, max: 1.0 },
    color: '#8e44ad', opacity: 1, depthWrite: false,
  },
  // New Particles
  EXHAUSTED: {
    shape: 'fountain', count: 5, lifetime: { min: 2.0, max: 3.0 },
    size: { min: 0.03, max: 0.06 }, radius: { min: 0.1, max: 0.3 },
    color: '#a9a9a9', opacity: 0.6, depthWrite: false,
  },
  UNEXHAUST: {
    shape: 'sphere', count: 20, lifetime: { min: 0.2, max: 0.4 },
    size: { min: 0.03, max: 0.06 }, radius: { min: 0.2, max: 0.4 },
    color: '#ffd700', opacity: 1, depthWrite: false,
  },
  VANISH: {
    shape: 'vortex', count: 100, lifetime: { min: 0.8, max: 1.2 },
    size: { min: 0.04, max: 0.08 }, radius: { min: 0.1, max: 0.5 },
    color: '#ffffff', opacity: 0.9, depthWrite: false,
  },
  REAPPEAR: {
    shape: 'vortex', count: 120, lifetime: { min: 1.0, max: 1.4 },
    size: { min: 0.05, max: 0.1 }, radius: { min: 1.5, max: 0.1 }, // Inward vortex
    color: '#ffffff', opacity: 1.0, depthWrite: false,
  },
};