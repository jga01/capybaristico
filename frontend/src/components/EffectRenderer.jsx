import React, { useEffect, useState } from 'react';
import * as THREE from 'three';
import FloatingText from './FloatingText';
import ParticleEffect from './ParticleEffect';
import { PARTICLE_CONFIGS } from '../constants';

const EffectRenderer = ({ currentEffect, onEffectComplete, findCardPosition }) => {
  const [localEffect, setLocalEffect] = useState(null);

  useEffect(() => {
    if (currentEffect) {
      setLocalEffect(currentEffect);

      // The effect's duration is now primarily controlled by the particle lifetime or text animation.
      // We set a generous timeout to ensure the queue always advances.
      const duration = currentEffect.duration || 2500; // Use effect-specific duration or a default
      const timer = setTimeout(() => {
        onEffectComplete();
      }, duration);

      return () => clearTimeout(timer);
    } else {
      setLocalEffect(null);
    }
  }, [currentEffect, onEffectComplete]);

  if (!localEffect) return null;

  const { type, targetId, sourceId, amount, isBuff, text, color, cardData } = localEffect;

  const targetPosition = findCardPosition(targetId) || findCardPosition(cardData?.instanceId);
  const sourcePosition = findCardPosition(sourceId);

  const renderEffectContent = () => {
    if (!targetPosition) return null;

    switch (type) {
      case 'DAMAGE':
        return <>
          <FloatingText text={`-${amount}`} color={'#FF4136'} position={new THREE.Vector3(0, 0.4, 0)} />
          <ParticleEffect config={PARTICLE_CONFIGS.DAMAGE} />
        </>;
      case 'ZERO_DAMAGE':
        return <>
          <FloatingText text={'Blocked!'} color={'#add8e6'} fontSize={0.35} position={new THREE.Vector3(0, 0.5, 0)} />
          <ParticleEffect config={PARTICLE_CONFIGS.BLOCK} />
        </>;
      case 'HEAL':
        return <>
          <FloatingText text={`+${amount}`} color={'#2ecc40'} position={new THREE.Vector3(0, 0.4, 0)} />
          <ParticleEffect config={PARTICLE_CONFIGS.HEAL} />
        </>;
      case 'STAT_CHANGE':
        return <>
          <FloatingText text={text} color={isBuff ? '#7fdbff' : '#b10dc9'} fontSize={0.3} position={new THREE.Vector3(0, 0.4, 0)} />
          <ParticleEffect config={isBuff ? PARTICLE_CONFIGS.BUFF : PARTICLE_CONFIGS.DEBUFF} />
        </>;
      case 'CARD_PLAYED':
        return <ParticleEffect config={PARTICLE_CONFIGS.CARD_PLAYED} />;
      case 'CARD_DESTROYED':
        return <>
          <ParticleEffect config={PARTICLE_CONFIGS.DESTROY} />
          <ParticleEffect config={{ ...PARTICLE_CONFIGS.DAMAGE, count: 40, lifetime: { min: 0.4, max: 0.8 } }} />
        </>;
      case 'TRANSFORM':
        return <ParticleEffect config={PARTICLE_CONFIGS.TRANSFORM} />;
      case 'VANISH':
        return <ParticleEffect config={PARTICLE_CONFIGS.VANISH} />;
      case 'REAPPEAR':
        return <ParticleEffect config={PARTICLE_CONFIGS.REAPPEAR} />;
      case 'UNEXHAUST':
        return <ParticleEffect config={PARTICLE_CONFIGS.UNEXHAUST} />;
      default:
        return null;
    }
  };

  return (
    <group>
      {targetPosition && (
        <group position={targetPosition}>
          {renderEffectContent()}
        </group>
      )}

      {sourcePosition && type === 'SHOCKWAVE' && (
        <group position={sourcePosition}>
          <ParticleEffect config={{ ...PARTICLE_CONFIGS.SHOCKWAVE, color: color || '#FFFFFF' }} />
        </group>
      )}
    </group>
  );
};

export default EffectRenderer;