import React, { useRef, useEffect, useState } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { Text as DreiText } from '@react-three/drei';
import * as THREE from 'three';

const DamageNumber = ({ amount, position, onComplete }) => {
    const textRef = useRef();
    const [opacity, setOpacity] = useState(1);
    const [currentYOffset, setCurrentYOffset] = useState(0);
    const { camera } = useThree();

    // Determine visual style based on amount
    const isZeroDamage = amount === 0;
    const textToShow = isZeroDamage ? 'Blocked!' : `-${amount}`;
    const color = isZeroDamage ? '#add8e6' : '#FF4136'; // Light blue for blocked, red for damage
    const fontSize = isZeroDamage ? 0.35 : 0.4;

    const animationDuration = 2.0;
    const riseAmount = 0.7;
    const fadeStartTime = animationDuration * 0.6;
    const fadeDuration = animationDuration * 0.4;

    useEffect(() => {
        const timer = setTimeout(() => {
            if (onComplete) onComplete();
        }, animationDuration * 1000);
        return () => clearTimeout(timer);
    }, [onComplete, animationDuration]);

    useFrame((state, delta) => {
        if (!textRef.current) return;

        // Rise
        setCurrentYOffset((prev) => Math.min(prev + delta * (riseAmount / animationDuration), riseAmount));

        // Fade out
        // Note: This timing method can be imprecise if multiple effects are triggered.
        // A more robust method would track start time on the ref.
        const elapsedTime = state.clock.elapsedTime % animationDuration;
        if (elapsedTime > fadeStartTime) {
            const timeIntoFade = elapsedTime - fadeStartTime;
            setOpacity(Math.max(0, 1 - (timeIntoFade / fadeDuration)));
        }

        // Billboard: Make the text face the camera
        textRef.current.quaternion.copy(camera.quaternion);
    });

    if (opacity <= 0.01) return null;

    return (
        <DreiText
            ref={textRef}
            position={[position.x, position.y + currentYOffset, position.z]}
            fontSize={fontSize}
            color={color}
            anchorX="center"
            anchorY="middle"
            material-transparent={true}
            material-opacity={opacity}
            outlineWidth={0.025}
            outlineColor="black"
            depthTest={false}
            renderOrder={999}
        >
            {textToShow}
        </DreiText>
    );
};

export default DamageNumber;