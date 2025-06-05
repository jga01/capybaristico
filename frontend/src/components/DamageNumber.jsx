import React, { useRef, useEffect, useState } from 'react';
import { useFrame, useThree } from '@react-three/fiber'; // Added useThree
import { Text } from '@react-three/drei';
import * as THREE from 'three';

const DamageNumber = ({ amount, position, onComplete }) => {
    const textRef = useRef();
    const [opacity, setOpacity] = useState(1);
    const [currentYOffset, setCurrentYOffset] = useState(0); // Renamed for clarity
    const { camera } = useThree(); // Get camera for billboarding

    const animationDuration = 2.0;
    const riseAmount = 0.7;      // How much it rises
    const fadeStartTime = animationDuration * 0.6; // Start fading later
    const fadeDuration = animationDuration * 0.4;  // Fade over the remaining time

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
        const elapsedTime = state.clock.elapsedTime % animationDuration; // Use modulo if effect can re-trigger fast
        // Or track own elapsed time
        if (elapsedTime > fadeStartTime) {
            const timeIntoFade = elapsedTime - fadeStartTime;
            setOpacity(Math.max(0, 1 - (timeIntoFade / fadeDuration)));
        }

        // Billboard: Make the text face the camera
        // The Text component might handle this, but explicit control is safer.
        // We want to match the camera's rotation but only on Y axis if text is upright,
        // or full lookAt if it's pure billboarding.
        // For simple billboarding where text is always upright relative to world:
        textRef.current.quaternion.copy(camera.quaternion);
        // If you want it to *always* face camera plane (could tilt if camera tilts):
        textRef.current.lookAt(camera.position);

    });

    if (opacity <= 0.01) return null;

    return (
        <Text
            ref={textRef}
            position={[position.x, position.y + currentYOffset, position.z]} // Y offset already includes initial above-card height
            fontSize={0.4} // Slightly larger
            color="#FF4136" // Brighter red
            anchorX="center"
            anchorY="middle"
            material-transparent={true}
            material-opacity={opacity}
            outlineWidth={0.025}
            outlineColor="black"
            depthTest={false} // Render on top of other objects
            renderOrder={999} // Ensure it renders late (on top)
        >
            -{amount}
        </Text>
    );
};

export default DamageNumber;