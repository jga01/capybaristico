import React, { useEffect, useState } from 'react';
import * as THREE from 'three';
import DamageNumber from './DamageNumber';
import ParticleEffect from './ParticleEffect';
import { PARTICLE_CONFIGS } from '../constants';

const EffectRenderer = ({ currentEffect, onEffectComplete, findCardPosition }) => {
  const [localEffect, setLocalEffect] = useState(null);

  useEffect(() => {
    if (currentEffect) {
      setLocalEffect(currentEffect);
      const { type } = currentEffect;

      // The effect's duration is now primarily controlled by the particle lifetime.
      // We set a generous timeout to ensure the queue always advances.
      let duration = 2500; // Max expected lifetime
      const timer = setTimeout(() => {
        onEffectComplete();
      }, duration);

      return () => clearTimeout(timer);
    } else {
      setLocalEffect(null);
    }
  }, [currentEffect, onEffectComplete]);

  if (!localEffect) return null;

  const { type, targetId, sourceId, amount, isBuff, cardData, color } = localEffect;

  const targetPosition = findCardPosition(targetId) || findCardPosition(cardData?.instanceId);
  const sourcePosition = findCardPosition(sourceId);

  // We can now render multiple effects for a single event to create compositions.
  return (
    <group>
      {/* --- TARGETED EFFECTS --- */}
      {targetPosition && (
        <group position={targetPosition}>
          {type === 'DAMAGE' && (
            <>
              <DamageNumber amount={amount} position={new THREE.Vector3(0, 0.4, 0)} />
              <ParticleEffect config={PARTICLE_CONFIGS.DAMAGE} />
            </>
          )}
          {type === 'HEAL' && <ParticleEffect config={PARTICLE_CONFIGS.HEAL} />}
          {type === 'STAT_CHANGE' && <ParticleEffect config={isBuff ? PARTICLE_CONFIGS.BUFF : PARTICLE_CONFIGS.DEBUFF} />}
          {type === 'CARD_PLAYED' && <ParticleEffect config={PARTICLE_CONFIGS.CARD_PLAYED} />}
          {type === 'AURA_PULSE' && <ParticleEffect config={{ ...PARTICLE_CONFIGS.AURA_PULSE, color: color || '#f0f' }} />}
          
          {/* --- COMPOSITION EXAMPLE --- */}
          {type === 'CARD_DESTROYED' && (
            <>
              {/* Smoky explosion that respects depth */}
              <ParticleEffect config={PARTICLE_CONFIGS.DESTROY} />
              {/* A secondary, smaller impact burst that renders on top */}
              <ParticleEffect config={{...PARTICLE_CONFIGS.DAMAGE, count: 40, lifetime: {min: 0.4, max: 0.8}}} />
            </>
          )}
        </group>
      )}

      {/* --- SOURCE-ORIGINATING EFFECTS --- */}
      {sourcePosition && (
        <group position={sourcePosition}>
            {type === 'SHOCKWAVE' && (
                <ParticleEffect config={{ ...PARTICLE_CONFIGS.SHOCKWAVE, color: color || '#FFFFFF' }} />
            )}
        </group>
      )}
    </group>
  );
};

export default EffectRenderer;