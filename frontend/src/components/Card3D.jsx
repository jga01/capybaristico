import React, { useRef, useMemo } from 'react';
import * as THREE from 'three';
import { Text as DreiText } from '@react-three/drei';
import { useSpring, animated } from '@react-spring/three';
import { CARD_WIDTH, CARD_HEIGHT, CARD_DEPTH, PARTICLE_CONFIGS } from '../constants';
import ParticleEffect from './ParticleEffect';

// A small sub-component for rendering stat numbers with animation
const AnimatedStat = ({ number, position, color, hasChanged }) => {
    const { scale, textColor } = useSpring({
        scale: hasChanged ? [1.5, 1.5, 1.5] : [1, 1, 1],
        textColor: color,
        config: { tension: 300, friction: 10 },
        reset: hasChanged,
    });

    return (
        <animated.group position={position} scale={scale}>
            <DreiText
                fontSize={0.18}
                anchorX="center"
                anchorY="middle"
                outlineWidth={0.01}
                outlineColor="black"
            >
                <animated.meshStandardMaterial
                    attach="material"
                    color={textColor}
                    roughness={0.4}
                    metalness={0.1}
                />
                {number}
            </DreiText>
        </animated.group>
    );
};

// Sub-component for a single status icon
const StatusIcon = ({ iconType, position, index }) => {
    const colors = {
        SILENCED: '#b10dc9',
        CAN_ATTACK_AGAIN: '#2ecc40',
        CANNOT_ATTACK: '#ff4136',
    };
    const color = colors[iconType] || '#ffffff';

    return (
        <mesh position={[position[0] + index * 0.25, position[1], position[2]]}>
            <planeGeometry args={[0.2, 0.2]} />
            <meshStandardMaterial color={color} emissive={color} emissiveIntensity={1.5} />
        </mesh>
    );
};


const Card3D = ({
    cardData,
    position,
    rotation,
    onClick,
    onMagnify,
    isHighlighted,
    highlightColor,
    isFaceDown,
    isExhausted,
    isDying,
    statChanges,
    auraColor,
    scale = 1,
    // --- NEW PROPS ---
    frontTexture,
    backTexture,
}) => {
    const meshRef = useRef();

    // --- REMOVED useLoader calls ---

    useMemo(() => [frontTexture, backTexture].forEach(t => t && (t.anisotropy = 16)), [frontTexture, backTexture]);

    const { emissiveIntensity, color, opacity } = useSpring({
        emissiveIntensity: isHighlighted ? 0.6 : (auraColor ? 0.4 : 0.03),
        color: isExhausted ? '#888' : '#fff',
        opacity: isDying ? 0 : 1,
        config: { duration: isDying ? 800 : 200 }
    });


    const currentEmissiveColor = useMemo(() => new THREE.Color(auraColor || (isHighlighted ? highlightColor : 0x000000)), [auraColor, isHighlighted, highlightColor]);

    const getStatColor = (current, base, isLife = false) => {
        if (!base) base = current;
        if (isLife) return current < base ? '#ff4136' : '#ffffff';
        if (current > base) return '#2ecc40';
        if (current < base) return '#ff4136';
        return '#ffffff';
    };

    const statusIcons = useMemo(() => {
        if (!cardData?.effectFlags) return [];
        const icons = [];
        if (cardData.effectFlags.status_silenced) icons.push('SILENCED');
        if (cardData.effectFlags.canAttackAgainThisTurn) icons.push('CAN_ATTACK_AGAIN');
        if (cardData.effectFlags.status_cannot_attack_AURA) icons.push('CANNOT_ATTACK');
        return icons;
    }, [cardData?.effectFlags]);


    return (
        <animated.mesh
            ref={meshRef}
            scale={scale}
            position={position || [0, CARD_DEPTH / 2, 0]}
            rotation={rotation || [0, 0, 0]}
            onClick={(e) => { e.stopPropagation(); onClick?.(cardData, meshRef.current); }}
            onContextMenu={(e) => { e.stopPropagation(); onMagnify?.(cardData); }}
            castShadow
            receiveShadow
        >
            <boxGeometry args={[CARD_WIDTH, CARD_HEIGHT, CARD_DEPTH]} />

            <animated.meshStandardMaterial attach="material-0" color="#202023" transparent opacity={opacity} />
            <animated.meshStandardMaterial attach="material-1" color="#202023" transparent opacity={opacity} />
            <animated.meshStandardMaterial attach="material-2" color="#202023" transparent opacity={opacity} />
            <animated.meshStandardMaterial attach="material-3" color="#202023" transparent opacity={opacity} />
            {/* --- MODIFIED: Use texture props --- */}
            <animated.meshStandardMaterial attach="material-4" map={frontTexture} color={color} emissive={currentEmissiveColor} emissiveIntensity={emissiveIntensity} roughness={0.5} metalness={0.1} transparent opacity={opacity} />
            <animated.meshStandardMaterial attach="material-5" map={backTexture} color={color} emissive={currentEmissiveColor} emissiveIntensity={emissiveIntensity} roughness={0.5} metalness={0.1} transparent opacity={opacity} />

            {!isFaceDown && cardData && (
                <>
                    <AnimatedStat
                        number={cardData.currentAttack}
                        position={[-CARD_WIDTH * 0.35, -CARD_HEIGHT * 0.4, CARD_DEPTH / 2 + 0.01]}
                        color={getStatColor(cardData.currentAttack, cardData.baseAttack)}
                        hasChanged={statChanges?.attack}
                    />
                    <AnimatedStat
                        number={cardData.currentDefense}
                        position={[0, -CARD_HEIGHT * 0.4, CARD_DEPTH / 2 + 0.01]}
                        color={getStatColor(cardData.currentDefense, cardData.baseDefense)}
                        hasChanged={statChanges?.defense}
                    />
                    <AnimatedStat
                        number={cardData.currentLife}
                        position={[CARD_WIDTH * 0.35, -CARD_HEIGHT * 0.4, CARD_DEPTH / 2 + 0.01]}
                        color={getStatColor(cardData.currentLife, cardData.baseLife, true)}
                        hasChanged={statChanges?.life}
                    />
                </>
            )}

            {statusIcons.map((iconType, i) => (
                <StatusIcon
                    key={i} iconType={iconType}
                    position={[-CARD_WIDTH * 0.4 + (statusIcons.length - 1) * -0.125, CARD_HEIGHT * 0.4, CARD_DEPTH / 2 + 0.01]}
                    index={i}
                />
            ))}

            {isExhausted && <ParticleEffect config={PARTICLE_CONFIGS.EXHAUSTED} />}
        </animated.mesh>
    );
};

export default React.memo(Card3D);