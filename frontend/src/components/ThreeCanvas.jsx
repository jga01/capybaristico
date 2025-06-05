import React, { Suspense, useMemo, useRef, useImperativeHandle, forwardRef } from 'react';
import { Canvas, useLoader } from '@react-three/fiber';
import { OrbitControls, Line } from '@react-three/drei';
import * as THREE from 'three';

// Component Imports
import Card3D from './Card3D';
import DamageNumber from './DamageNumber';
import EffectRenderer from './EffectRenderer'; // The new effects manager
import { CARD_WIDTH, CARD_HEIGHT, CARD_DEPTH } from '../constants';

// --- Sub-Components for the Scene ---

const TableTop = ({ onTableClick }) => {
  const tableTexture = useLoader(THREE.TextureLoader, '/assets/textures/table.png');
  useMemo(() => {
    if (tableTexture) {
      tableTexture.wrapS = tableTexture.wrapT = THREE.RepeatWrapping;
      tableTexture.repeat.set(8, 5);
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

const cameraSettings = {
  position: [0, 13, 0.01],
  fov: 50,
  near: 0.1,
  far: 100,
};

// --- Main Canvas Component Logic ---

const ThreeCanvasInternal = forwardRef(({
  gameState,
  playerId,
  onCardClickOnField,
  onEmptyFieldSlotClick,
  onTableClick,
  selectedHandCardInfo,
  selectedAttackerInfo,
  selectedAbilitySourceInfo,
  selectedAbilityOption,
  isTargetingMode,
  currentEffect, // Prop for the effect currently being animated
  onEffectComplete, // Callback to signal animation is done
}, ref) => {

  const cardMeshes = useRef({}); // Store refs to all card meshes by instanceId
  const cardBackTexture = useLoader(THREE.TextureLoader, '/assets/cards_images/back.png');

  useMemo(() => {
    if (cardBackTexture) {
      cardBackTexture.anisotropy = 16;
    }
  }, [cardBackTexture]);

  // Loading guard
  if (!gameState || !gameState.player1State || !gameState.player2State) {
    return null; // The parent component shows the loading text
  }

  const { player1State, player2State } = gameState;
  const viewingPlayer = playerId === player1State.playerId ? player1State : player2State;
  const opponentPlayer = playerId === player1State.playerId ? player2State : player1State;

  const cardInPlayRotation = [-Math.PI / 2, 0, 0];

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

  const getOpponentHandCardPosition = (index, totalCards) => {
    const startX = -(totalCards - 1) * (CARD_WIDTH * 0.9) / 2;
    const xPos = startX + index * (CARD_WIDTH * 0.9);
    return [xPos, 0.001 + CARD_DEPTH / 2, -3.8];
  };

  // Expose a method to the parent (GameScreen) to get card positions for effects
  useImperativeHandle(ref, () => ({
    findCardPosition(instanceId) {
      const mesh = cardMeshes.current[instanceId];
      if (mesh) {
        // Return a clone to prevent accidental mutation of the mesh's position
        return mesh.position.clone();
      }

      // Fallback logic for cards that might have been removed from the scene
      // This is crucial for effects like CARD_DESTROYED
      let position = null;
      const findInField = (field, isSelf) => {
        field.forEach((card, index) => {
          if (card && card.instanceId === instanceId) {
            const { x, yCard, z } = getFieldSlotPosition(index, isSelf);
            position = new THREE.Vector3(x, yCard, z);
          }
        });
      };

      findInField(viewingPlayer.field, true);
      if (!position) findInField(opponentPlayer.field, false);

      return position;
    }
  }));

  // Render logic for cards on the player's field
  const playerInPlayCards = useMemo(() => viewingPlayer.field.map((card, index) => {
    const { x, yCard, ySlot, z } = getFieldSlotPosition(index, true);
    if (card) {
      const isSelectedAsAttacker = selectedAttackerInfo?.instanceId === card.instanceId;
      const isSelectedAsAbilitySource = selectedAbilitySourceInfo?.instanceId === card.instanceId;
      let isTargetable = isTargetingMode && (
        (selectedAttackerInfo) ||
        (selectedAbilityOption?.requiresTarget === "OWN_FIELD_CARD" || selectedAbilityOption?.requiresTarget === "ANY_FIELD_CARD")
      );

      return (
        <Card3D
          ref={el => cardMeshes.current[card.instanceId] = el}
          key={`self-field-${card.instanceId}`}
          cardData={card}
          isFaceDown={false}
          position={[x, yCard, z]}
          rotation={cardInPlayRotation}
          onClick={(data, mesh) => onCardClickOnField(data, mesh, viewingPlayer.playerId, index)}
          isHighlighted={isSelectedAsAttacker || isSelectedAsAbilitySource || isTargetable}
          highlightColor={isSelectedAsAttacker ? '#ffc107' : isSelectedAsAbilitySource ? '#29b6f6' : '#e57373'}
        />);
    }
    return (
      <FieldSlotVisual
        key={`self-empty-slot-${index}`}
        position={new THREE.Vector3(x, ySlot, z)}
        onClick={() => onEmptyFieldSlotClick(viewingPlayer.playerId, index)}
        isTargetableForPlay={!!selectedHandCardInfo}
      />);
  }), [viewingPlayer.field, selectedHandCardInfo, selectedAttackerInfo, selectedAbilitySourceInfo, isTargetingMode, selectedAbilityOption]);

  // Render logic for cards on the opponent's field
  const opponentInPlayCards = useMemo(() => opponentPlayer.field.map((card, index) => {
    const { x, yCard, ySlot, z } = getFieldSlotPosition(index, false);
    if (card) {
      let isTargetable = isTargetingMode && (
        (selectedAttackerInfo) ||
        (selectedAbilityOption?.requiresTarget === "OPPONENT_FIELD_CARD" || selectedAbilityOption?.requiresTarget === "ANY_FIELD_CARD")
      );
      return (
        <Card3D
          ref={el => cardMeshes.current[card.instanceId] = el}
          key={`opp-field-${card.instanceId}`}
          cardData={card}
          isFaceDown={false}
          position={[x, yCard, z]}
          rotation={cardInPlayRotation}
          onClick={(data, mesh) => onCardClickOnField(data, mesh, opponentPlayer.playerId, index)}
          isHighlighted={isTargetable}
          highlightColor={'#e57373'}
        />);
    }
    return <FieldSlotVisual key={`opp-empty-slot-${index}`} position={new THREE.Vector3(x, ySlot, z)} />;
  }), [opponentPlayer.field, selectedAttackerInfo, selectedAbilitySourceInfo, isTargetingMode, selectedAbilityOption]);

  // Render logic for opponent's hand
  const opponentHandDisplay = useMemo(() => Array.from({ length: opponentPlayer.handSize }).map((_, index) => (
    <Card3D
      key={`opp-hand-${index}`}
      isFaceDown={true}
      position={getOpponentHandCardPosition(index, opponentPlayer.handSize)}
      rotation={cardInPlayRotation}
      scale={0.85}
    />
  )), [opponentPlayer.handSize]);

  // Deck and Discard Pile visualization logic
  const pileThicknessMultiplier = 0.15;
  const minPileHeight = 0.01;
  const tableEpsilon = -0.048;
  const playerDeckHeight = Math.max(minPileHeight, viewingPlayer.deckSize * CARD_DEPTH * pileThicknessMultiplier);
  const playerDiscardHeight = Math.max(minPileHeight, viewingPlayer.discardPileSize * CARD_DEPTH * pileThicknessMultiplier);
  const opponentDeckHeight = Math.max(minPileHeight, opponentPlayer.deckSize * CARD_DEPTH * pileThicknessMultiplier);
  const opponentDiscardHeight = Math.max(minPileHeight, opponentPlayer.discardPileSize * CARD_DEPTH * pileThicknessMultiplier);
  const playerPilesZ = getFieldSlotPosition(0, true).z + CARD_HEIGHT * 0.7;
  const opponentPilesZ = getFieldSlotPosition(0, false).z - CARD_HEIGHT * 0.7;

  const pileMaterial = useMemo(() => [
    new THREE.MeshStandardMaterial({ color: '#382d26' }), new THREE.MeshStandardMaterial({ color: '#382d26' }),
    new THREE.MeshStandardMaterial({ map: cardBackTexture }), new THREE.MeshStandardMaterial({ map: cardBackTexture }),
    new THREE.MeshStandardMaterial({ color: '#382d26' }), new THREE.MeshStandardMaterial({ color: '#382d26' }),
  ], [cardBackTexture]);

  return (
    <>
      {/* --- SCENE SETUP (Lights, Fog) --- */}
      <ambientLight intensity={0.6} />
      <directionalLight
        position={[4, 20, 8]} intensity={1.5} castShadow
        shadow-mapSize-width={2048} shadow-mapSize-height={2048}
        shadow-camera-far={70} shadow-bias={-0.0003}
      />
      <hemisphereLight skyColor={0xbde0ff} groundColor={0x505070} intensity={0.35} />
      <fog attach="fog" args={['#0A0B0F', cameraSettings.position[1] + 12, cameraSettings.far + 50]} />

      {/* --- RENDERED OBJECTS --- */}
      <Suspense fallback={null}>
        <TableTop onTableClick={onTableClick} />
        {playerInPlayCards}
        {opponentInPlayCards}
        {opponentHandDisplay}

        {/* Deck & Discard Piles */}
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

        {/* The Effect Renderer - this is where all the magic happens! */}
        <EffectRenderer
          currentEffect={currentEffect}
          onEffectComplete={onEffectComplete}
          findCardPosition={(id) => ref.current?.findCardPosition(id)} // Use the exposed method
          cardMeshes={cardMeshes} // Pass the refs for direct animation if needed
        />

      </Suspense>

      {/* --- CONTROLS --- */}
      <OrbitControls
        enablePan={true} enableRotate={true} zoomSpeed={0.6} target={[0, 0, 0]}
        minDistance={cameraSettings.position[1] - 9} maxDistance={cameraSettings.position[1] + 20}
        minPolarAngle={Math.PI / 180 * 45} maxPolarAngle={Math.PI / 180 * 85}
      />
    </>
  );
});


// --- The Wrapper Component that gets exported ---

// This wrapper component creates the <Canvas> and the ref, then passes it
// down to the internal component. This is the standard pattern for using
// `forwardRef` with R3F components that need to be accessed from parents.
const CanvasWrapper = (props) => {
  const canvasRef = useRef();

  return (
    <Canvas
      shadows
      camera={cameraSettings}
      style={{ background: '#0B0C10' }}
      gl={{ antialias: true, logarithmicDepthBuffer: true }}
    >
      <ThreeCanvasInternal ref={canvasRef} {...props} />
    </Canvas>
  );
};

export default CanvasWrapper;