import React, { useRef, useMemo, useEffect } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

const ParticleEffect = ({ config, onComplete }) => {
  const pointsRef = useRef();
  const materialRef = useRef();
  
  const particles = useMemo(() => {
    const temp = [];
    for (let i = 0; i < config.count; i++) {
      // Base properties for each particle
      const lifetime = THREE.MathUtils.randFloat(config.lifetime.min, config.lifetime.max);
      const startSize = THREE.MathUtils.randFloat(config.size.min, config.size.max);
      
      // Properties for initial position and motion
      const theta = Math.random() * 2 * Math.PI;
      const phi = Math.acos((Math.random() * 2) - 1);
      const radius = THREE.MathUtils.randFloat(config.radius.min, config.radius.max);

      temp.push({
        elapsed: 0,
        lifetime,
        startSize,
        // Motion parameters
        theta, phi, radius,
        // Store initial velocity vector for some shapes
        velocity: new THREE.Vector3(
          Math.sin(phi) * Math.cos(theta),
          Math.cos(phi),
          Math.sin(phi) * Math.sin(theta)
        ).multiplyScalar(THREE.MathUtils.randFloat(1.5, 2.5))
      });
    }
    return temp;
  }, [config]);

  const dummy = useMemo(() => new THREE.Object3D(), []);
  
  useEffect(() => {
    if (onComplete) {
      // The total duration is now the longest possible lifetime of a particle
      const maxLifetime = config.lifetime.max;
      const timer = setTimeout(onComplete, maxLifetime * 1000);
      return () => clearTimeout(timer);
    }
  }, [config, onComplete]);

  useFrame((state, delta) => {
    if (!pointsRef.current || !materialRef.current) return;

    particles.forEach((p, i) => {
      p.elapsed += delta;
      if (p.elapsed > p.lifetime) {
        // Hide the particle instead of resetting it for one-shot effects
        dummy.scale.set(0, 0, 0);
        dummy.updateMatrix();
        pointsRef.current.setMatrixAt(i, dummy.matrix);
        return;
      }
      
      const lifePct = p.elapsed / p.lifetime; // 0 (start) to 1 (end)

      // --- Position Animation ---
      let currentPosition = new THREE.Vector3();
      switch (config.shape) {
        case 'fountain': // Upward motion with gravity
          currentPosition.copy(p.velocity).multiplyScalar(lifePct);
          currentPosition.y -= 0.5 * 9.8 * lifePct * lifePct; // Simple gravity
          break;
        case 'vortex': // Spiraling inward or outward
            const angle = p.theta + lifePct * 10;
            const spiralRadius = p.radius * (1 - lifePct);
            currentPosition.set(
                Math.cos(angle) * spiralRadius,
                p.velocity.y * lifePct,
                Math.sin(angle) * spiralRadius
            );
            break;
        case 'sphere': // Exploding outward
        default:
          currentPosition.copy(p.velocity).multiplyScalar(lifePct);
          break;
      }
      dummy.position.copy(currentPosition);

      // --- Size & Opacity Animation ---
      // Creates a "pop" then fade effect
      const scale = p.startSize * Math.sin(lifePct * Math.PI); 
      dummy.scale.set(scale, scale, scale);
      
      // Opacity can be tied to the same curve
      materialRef.current.opacity = Math.sin(lifePct * Math.PI) * config.opacity;

      dummy.updateMatrix();
      pointsRef.current.setMatrixAt(i, dummy.matrix);
    });
    pointsRef.current.instanceMatrix.needsUpdate = true;
  });

  return (
    <instancedMesh ref={pointsRef} args={[null, null, config.count]}>
      <sphereGeometry args={[1, 16, 16]} />
      <meshStandardMaterial
        ref={materialRef}
        color={config.color}
        emissive={config.color}
        emissiveIntensity={3}
        roughness={0.2}
        metalness={0.2}
        transparent={true}
        opacity={config.opacity}
        // --- KEY CHANGE for depth ---
        // false: always render on top. true: respect scene depth.
        depthWrite={config.depthWrite !== undefined ? config.depthWrite : false}
      />
    </instancedMesh>
  );
};

export default ParticleEffect;