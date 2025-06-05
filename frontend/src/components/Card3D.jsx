import React, { useRef, useMemo } from 'react';
import { useLoader } from '@react-three/fiber';
import * as THREE from 'three';
import { CARD_WIDTH, CARD_HEIGHT, CARD_DEPTH } from '../constants';

const Card3D = ({
    cardData,
    position,
    rotation,
    onClick,
    isHighlighted,
    highlightColor,
    isFaceDown,
    isDamagedRecently, // New prop for flicker
    scale = 1,
}) => {
    const meshRef = useRef();

    const cardImagesBasePath = '/assets/cards_images/';
    const actualCardBackUrl = `${cardImagesBasePath}back.png`;
    let cardFrontTexturePath;

    if (isFaceDown) {
        cardFrontTexturePath = actualCardBackUrl;
    } else if (cardData && cardData.imageUrl) {
        if (cardData.imageUrl.startsWith('http') || cardData.imageUrl.startsWith('/')) {
            cardFrontTexturePath = cardData.imageUrl;
        } else {
            cardFrontTexturePath = cardImagesBasePath + cardData.imageUrl;
        }
    } else {
        cardFrontTexturePath = actualCardBackUrl;
    }

    const normalizePath = (path) => {
        if (!path) return null;
        let normalized = path;
        if (!normalized.startsWith('/') && !normalized.startsWith('http')) {
            normalized = '/' + normalized;
        }
        normalized = normalized.replace(/(?<!:)\/\/+/g, '/');
        return normalized;
    };

    const finalFrontTexUrl = normalizePath(cardFrontTexturePath);
    const finalBackTexUrl = normalizePath(actualCardBackUrl);

    const frontTexture = useLoader(THREE.TextureLoader, finalFrontTexUrl || finalBackTexUrl);
    const backTexture = useLoader(THREE.TextureLoader, finalBackTexUrl);


    useMemo(() => {
        [frontTexture, backTexture].forEach(tex => {
            if (tex) {
                tex.anisotropy = 16;
            }
        });
    }, [frontTexture, backTexture]);

    const materials = useMemo(() => {
        let currentEmissiveColor = isHighlighted ? new THREE.Color(highlightColor) : new THREE.Color(0x000000);
        let currentEmissiveIntensity = isHighlighted ? 0.6 : 0.03;

        if (isDamagedRecently) {
            currentEmissiveColor = new THREE.Color("red");
            currentEmissiveIntensity = 0.8; // Make flicker prominent
        }

        const createMaterial = (textureMap) => new THREE.MeshStandardMaterial({
            map: textureMap,
            roughness: 0.5,
            metalness: 0.1,
            emissive: currentEmissiveColor,
            emissiveIntensity: currentEmissiveIntensity,
            side: THREE.FrontSide,
        });
        const edgeMaterial = new THREE.MeshStandardMaterial({
            color: '#202023',
            roughness: 0.7,
            metalness: 0.05,
            emissive: currentEmissiveColor,
            emissiveIntensity: currentEmissiveIntensity * 0.5,
        });
        return [
            edgeMaterial, edgeMaterial, edgeMaterial, edgeMaterial,
            createMaterial(frontTexture),
            createMaterial(backTexture)
        ];
    }, [frontTexture, backTexture, isHighlighted, highlightColor, isDamagedRecently]); // Added isDamagedRecently

    return (
        <mesh
            ref={meshRef}
            scale={scale}
            position={position || [0, CARD_DEPTH / 2, 0]}
            rotation={rotation || [0, 0, 0]}
            onClick={(e) => {
                e.stopPropagation();
                if (onClick) onClick(cardData, meshRef.current);
            }}
            castShadow
            receiveShadow
            material={materials}
        >
            <boxGeometry args={[CARD_WIDTH, CARD_HEIGHT, CARD_DEPTH]} />
        </mesh>
    );
};

const cardPropsAreEqual = (prevProps, nextProps) => {
    // Compare critical props that affect rendering
    if (prevProps.isFaceDown !== nextProps.isFaceDown) return false;
    if (prevProps.isHighlighted !== nextProps.isHighlighted) return false;
    if (prevProps.highlightColor !== nextProps.highlightColor) return false; // if string
    // if (prevProps.highlightColor.equals(nextProps.highlightColor)) // if THREE.Color
    if (prevProps.isDamagedRecently !== nextProps.isDamagedRecently) return false;
    if (prevProps.scale !== nextProps.scale) return false;

    // Compare cardData (assuming it can be null or an object)
    if (!prevProps.cardData && !nextProps.cardData) { /* both null, same */ }
    else if (!prevProps.cardData || !nextProps.cardData) return false; // one is null, different
    else { // both are objects, compare relevant fields
        if (prevProps.cardData.instanceId !== nextProps.cardData.instanceId) return false;
        if (prevProps.cardData.imageUrl !== nextProps.cardData.imageUrl) return false;
        if (prevProps.cardData.currentLife !== nextProps.cardData.currentLife) return false;
        if (prevProps.cardData.currentAttack !== nextProps.cardData.currentAttack) return false;
        if (prevProps.cardData.currentDefense !== nextProps.cardData.currentDefense) return false;
        // Add other cardData fields if they affect visuals
    }

    // Compare position array (content, not reference)
    if (prevProps.position && nextProps.position) {
        if (prevProps.position.length !== nextProps.position.length) return false;
        for (let i = 0; i < prevProps.position.length; i++) {
            if (prevProps.position[i] !== nextProps.position[i]) return false;
        }
    } else if (prevProps.position || nextProps.position) return false; // one is null/undefined

    // Compare rotation (if it changes dynamically and affects visuals)
    // Similar logic as position for rotation array if used

    return true; // Props are considered equal
};


export default React.memo(Card3D, cardPropsAreEqual);