import React, { useRef, useEffect, useState } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { Text as DreiText } from '@react-three/drei';

const FloatingText = ({ text, color, fontSize = 0.4, position, onComplete }) => {
    const textRef = useRef();
    const [opacity, setOpacity] = useState(1);
    const [currentYOffset, setCurrentYOffset] = useState(0);
    const { camera } = useThree();

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
        const riseSpeed = riseAmount / animationDuration;
        setCurrentYOffset((prev) => Math.min(prev + delta * riseSpeed, riseAmount));

        // Fade out
        const elapsedTime = (textRef.current.userData.startTime) ? state.clock.getElapsedTime() - textRef.current.userData.startTime : 0;
        if (!textRef.current.userData.startTime) {
            textRef.current.userData.startTime = state.clock.getElapsedTime();
        }

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
            depthTest={false} // Render on top of other objects
            renderOrder={999} // High render order
        >
            {text}
        </DreiText>
    );
};

export default FloatingText;