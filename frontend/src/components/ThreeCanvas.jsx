import React, { Suspense, useMemo, useRef, useImperativeHandle, forwardRef } from 'react';
import { Canvas, useLoader } from '@react-three/fiber';
import { OrbitControls, Line } from '@react-three/drei';
import { useSpring, animated } from '@react-spring/three';
import * as THREE from 'three';
import { getGameAssetUrls } from '../services/assetService';

// Component Imports
import Card3D from './Card3D';
import EffectRenderer from './EffectRenderer';
import { CARD_WIDTH, CARD_HEIGHT, CARD_DEPTH } from '../constants';

const TableTop = ({ onTableClick }) => {
  const tableTexture = useLoader(THREE.TextureLoader, '/assets/textures/table.png');
  useMemo(() => {
    if (tableTexture) {
      tableTexture.anisotropy = 16;
    }
  }, [tableTexture]);

  return (
    <mesh
      rotation={[-Math.PI / 2, 0, 0]}
      position={[0, -0.05, 0]}
      receiveShadow
      onClick={onTableClick}
    >
      <planeGeometry args={[100, 60]} />
      <meshStandardMaterial
        map={tableTexture}
        roughness={0.8}
        metalness={0.05}
      />
    </mesh>
  );
};

const FieldSlotVisual = ({ position, onClick, isTargetableForPlay }) => {
  const w = CARD_WIDTH + 0.1;
  const h = CARD_HEIGHT + 0.1;
  const points = useMemo(() => [
    new THREE.Vector3(-w / 2, h / 2, 0), new THREE.Vector3(w / 2, h / 2, 0),
    new THREE.Vector3(w / 2, -h / 2, 0), new THREE.Vector3(-w / 2, -h / 2, 0),
    new THREE.Vector3(-w / 2, h / 2, 0)
  ], [w, h]);

  return (
    <group position={position} rotation={[-Math.PI / 2, 0, 0]}>
      <Line
        points={points}
        color={isTargetableForPlay ? "#76c893" : "#A0A0B0"}
        lineWidth={isTargetableForPlay ? 3.5 : 2.5}
        dashed={true}
        dashSize={0.06}
        gapSize={0.04}
      />
      <mesh onClick={onClick}>
        <planeGeometry args={[w, h]} />
        <meshStandardMaterial transparent opacity={0} />
      </mesh>
    </group>
  );
};

const getFieldSlotPosition = (index, isSelf) => {
  const xSpacing = CARD_WIDTH + 0.25;
  const totalWidthOfSlots = (4 - 1) * xSpacing;
  const startX = -totalWidthOfSlots / 2;
  const xPos = startX + index * xSpacing;
  const yPosForCards = 0.001 + CARD_DEPTH / 2;
  const yPosForSlots = -0.049;
  const zPos = isSelf ? 1.3 : -1.3;
  return { x: xPos, yCard: yPosForCards, ySlot: yPosForSlots, z: zPos };
};


const AnimatedFieldCard = ({ card, fieldIndex, isSelf, isAttacking, findCardPosition, onMagnify, ...props }) => {
  const { x, yCard, z } = getFieldSlotPosition(fieldIndex, isSelf);
  const startPos = [x, yCard, z];

  const { position } = useSpring({
    to: async (next) => {
      if (isAttacking) { // Attack Lunge Animation
        const defenderPos = findCardPosition(isAttacking.defenderId);
        if (defenderPos) {
          await next({ position: [defenderPos.x, defenderPos.y + 0.5, defenderPos.z], config: { tension: 200, friction: 22 } });
          await next({ position: startPos, config: { tension: 250, friction: 25 } });
        } else {
          await next({ position: startPos });
        }
      } else if (card.isDying) { // Dying Animation
        await next({ position: [x, yCard - CARD_HEIGHT, z], config: { tension: 120, friction: 50, duration: 800 } });
      } else {
        await next({ position: startPos });
      }
    },
    from: { position: startPos },
    reset: true,
  });

  return (
    <animated.group position={position}>
      <Card3D {...props} cardData={card} position={[0, 0, 0]} isDying={card.isDying} onMagnify={onMagnify} />
    </animated.group>
  );
};


const cameraSettings = {
  position: [0, 13, 0.01],
  fov: 50,
  near: 0.1,
  far: 100,
};

const ThreeCanvasInternal = forwardRef(({
  gameState,
  playerId,
  onCardClickOnField,
  onEmptyFieldSlotClick,
  onMagnify,
  onTableClick,
  selectedHandCardInfo,
  selectedAttackerInfo,
  selectedAbilitySourceInfo,
  isTargetingMode,
  selectedAbilityOption,
  currentEffect,
  onEffectComplete,
  attackAnimation,
}, ref) => {

  // --- FIX: Load all textures once at the top level ---
  const assetUrls = getGameAssetUrls();
  const allTextures = useLoader(THREE.TextureLoader, assetUrls);
  const textureMap = useMemo(() => {
    const map = {};
    assetUrls.forEach((url, index) => {
      map[url] = allTextures[index];
    });
    return map;
  }, [assetUrls, allTextures]);

  const cardImagesBasePath = '/assets/cards_images/';
  const backTexture = textureMap[`${cardImagesBasePath}back.png`];
  // --- END FIX ---

  if (!gameState || !gameState.player1State || !gameState.player2State) {
    return null;
  }

  const { player1State, player2State } = gameState;
  const viewingPlayer = playerId === player1State.playerId ? player1State : player2State;
  const opponentPlayer = playerId === player1State.playerId ? player2State : player1State;
  const cardInPlayRotation = [-Math.PI / 2, 0, 0];

  const getOpponentHandCardPosition = (index, totalCards) => {
    const startX = -(totalCards - 1) * (CARD_WIDTH * 0.9) / 2;
    const xPos = startX + index * (CARD_WIDTH * 0.9);
    return [xPos, 0.001 + CARD_DEPTH / 2, -3.8];
  };

  const findCardPosition = (instanceId) => {
    if (!instanceId) return null;
    let position = null;
    const findInField = (playerState, isSelf) => {
      playerState.field.forEach((card, index) => {
        if (card && card.instanceId === instanceId) {
          const { x, yCard, z } = getFieldSlotPosition(index, isSelf);
          position = new THREE.Vector3(x, yCard, z);
        }
      });
    };
    findInField(viewingPlayer, true);
    if (!position) findInField(opponentPlayer, false);
    return position;
  };

  useImperativeHandle(ref, () => ({ findCardPosition }));

  const renderField = (playerState, isSelf) => {
    return playerState.field.map((card, index) => {
      const { ySlot, z } = getFieldSlotPosition(index, isSelf);
      if (card) {
        const isSelectedAsAttacker = selectedAttackerInfo?.instanceId === card.instanceId;
        const isSelectedAsAbilitySource = selectedAbilitySourceInfo?.instanceId === card.instanceId;
        let isTargetable = isTargetingMode && (
          (isSelf && (selectedAbilityOption?.requiresTarget === "OWN_FIELD_CARD" || selectedAbilityOption?.requiresTarget === "ANY_FIELD_CARD")) ||
          (!isSelf && (selectedAttackerInfo || selectedAbilityOption?.requiresTarget === "OPPONENT_FIELD_CARD" || selectedAbilityOption?.requiresTarget === "ANY_FIELD_CARD"))
        );

        // --- FIX: Determine texture from map and pass as prop ---
        const frontTextureUrl = card.imageUrl ? `${cardImagesBasePath}${card.imageUrl}` : `${cardImagesBasePath}back.png`;
        const frontTexture = textureMap[frontTextureUrl];

        return (
          <AnimatedFieldCard
            key={`field-${card.instanceId}`}
            card={card}
            isSelf={isSelf}
            fieldIndex={index}
            isAttacking={attackAnimation?.attackerId === card.instanceId ? attackAnimation : null}
            findCardPosition={findCardPosition}
            rotation={cardInPlayRotation}
            onClick={(data, mesh) => onCardClickOnField(data, mesh, playerState.playerId, index)}
            isHighlighted={isSelectedAsAttacker || isSelectedAsAbilitySource || isTargetable}
            highlightColor={isSelectedAsAttacker ? '#ffc107' : isSelectedAsAbilitySource ? '#29b6f6' : '#e57373'}
            isExhausted={isSelf && card.isExhausted}
            onMagnify={onMagnify}
            statChanges={card.statChanges}
            auraColor={card.auraColor}
            frontTexture={frontTexture}
            backTexture={backTexture}
          />
        );
      }
      const { x } = getFieldSlotPosition(index, isSelf);
      return (
        <FieldSlotVisual
          key={`${isSelf ? 'self' : 'opp'}-empty-slot-${index}`}
          position={new THREE.Vector3(x, ySlot, z)}
          onClick={() => isSelf && onEmptyFieldSlotClick(playerState.playerId, index)}
          isTargetableForPlay={isSelf && !!selectedHandCardInfo}
        />);
    });
  };

  const opponentHandDisplay = useMemo(() => Array.from({ length: opponentPlayer.handSize }).map((_, index) => (
    <Card3D
      key={`opp-hand-${index}`}
      isFaceDown={true}
      position={getOpponentHandCardPosition(index, opponentPlayer.handSize)}
      rotation={cardInPlayRotation}
      scale={0.85}
      frontTexture={backTexture}
      backTexture={backTexture}
    />
  )), [opponentPlayer.handSize, backTexture]);

  const pileThicknessMultiplier = 0.15;
  const minPileHeight = 0.01;
  const tableEpsilon = -0.048;
  const playerDeckHeight = Math.max(minPileHeight, viewingPlayer.deckSize * CARD_DEPTH * pileThicknessMultiplier);
  const playerDiscardHeight = Math.max(minPileHeight, viewingPlayer.discardPileSize * CARD_DEPTH * pileThicknessMultiplier);
  const opponentDeckHeight = Math.max(minPileHeight, opponentPlayer.deckSize * CARD_DEPTH * pileThicknessMultiplier);
  const opponentDiscardHeight = Math.max(minPileHeight, opponentPlayer.discardPileSize * CARD_DEPTH * pileThicknessMultiplier);
  const playerPilesZ = getFieldSlotPosition(0, true).z + CARD_HEIGHT * 0.7;
  const opponentPilesZ = getFieldSlotPosition(0, false).z - CARD_HEIGHT * 0.7;

  // --- FIX: Pass preloaded back texture to pile material ---
  const pileMaterial = useMemo(() => [
    new THREE.MeshStandardMaterial({ color: '#382d26' }), new THREE.MeshStandardMaterial({ color: '#382d26' }),
    new THREE.MeshStandardMaterial({ map: backTexture }), new THREE.MeshStandardMaterial({ map: backTexture }),
    new THREE.MeshStandardMaterial({ color: '#382d26' }), new THREE.MeshStandardMaterial({ color: '#382d26' }),
  ], [backTexture]);

  return (
    <>
      <ambientLight intensity={0.8} />
      <directionalLight
        position={[4, 20, 8]} intensity={1.5} castShadow
        shadow-mapSize-width={2048} shadow-mapSize-height={2048}
        shadow-camera-far={70} shadow-bias={-0.0003}
      />
      <hemisphereLight skyColor={0xbde0ff} groundColor={0x505070} intensity={0.5} />
      <fog attach="fog" args={['#0A0B0F', cameraSettings.position[1] + 12, cameraSettings.far + 50]} />

      <TableTop onTableClick={onTableClick} />
      {renderField(viewingPlayer, true)}
      {renderField(opponentPlayer, false)}
      {opponentHandDisplay}

      <mesh position={[-CARD_WIDTH * 3, playerDeckHeight / 2 + tableEpsilon, playerPilesZ]} visible={viewingPlayer.deckSize > 0} castShadow receiveShadow material={pileMaterial}>
        <boxGeometry args={[CARD_WIDTH, playerDeckHeight, CARD_HEIGHT]} />
      </mesh>
      <mesh position={[-CARD_WIDTH * 3 - CARD_WIDTH - 0.5, playerDiscardHeight / 2 + tableEpsilon, playerPilesZ]} visible={viewingPlayer.discardPileSize > 0} castShadow receiveShadow material={pileMaterial}>
        <boxGeometry args={[CARD_WIDTH, playerDiscardHeight, CARD_HEIGHT]} />
      </mesh>
      <mesh position={[CARD_WIDTH * 3, opponentDeckHeight / 2 + tableEpsilon, opponentPilesZ]} visible={opponentPlayer.deckSize > 0} castShadow receiveShadow material={pileMaterial}>
        <boxGeometry args={[CARD_WIDTH, opponentDeckHeight, CARD_HEIGHT]} />
      </mesh>
      <mesh position={[CARD_WIDTH * 3 + CARD_WIDTH + 0.5, opponentDiscardHeight / 2 + tableEpsilon, opponentPilesZ]} visible={opponentPlayer.discardPileSize > 0} castShadow receiveShadow material={pileMaterial}>
        <boxGeometry args={[CARD_WIDTH, opponentDiscardHeight, CARD_HEIGHT]} />
      </mesh>

      <EffectRenderer
        currentEffect={currentEffect}
        onEffectComplete={onEffectComplete}
        findCardPosition={findCardPosition}
      />

      <OrbitControls
        enablePan={true} enableRotate={true} zoomSpeed={0.6} target={[0, 0, 0]}
        minDistance={cameraSettings.position[1] - 9} maxDistance={cameraSettings.position[1] + 20}
        minPolarAngle={Math.PI / 180 * 45} maxPolarAngle={Math.PI / 180 * 85}
      />
    </>
  );
});

const CanvasWrapper = (props) => {
  const canvasRef = useRef();
  return (
    <Canvas
      shadows
      camera={cameraSettings}
      style={{ background: '#0B0C10' }}
      gl={{ antialias: true, logarithmicDepthBuffer: true }}
    >
      <Suspense fallback={null}>
        <ThreeCanvasInternal ref={canvasRef} {...props} />
      </Suspense>
    </Canvas>
  );
};

export default CanvasWrapper;