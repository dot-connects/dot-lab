// The creature seam. ar.js stands any creature at its anchor and only uses these members:
//   root     THREE.Object3D, feet at the origin, facing +z (ar.js turns the anchor to the camera)
//   hit      {w, h, cy} hit box in metres, centred cy above the feet (the screen box and the ring come from it)
//   shadowR  contact shadow radius in metres
//   update(now, dt, pose, tint) -> lift (metres off the floor, the shadow shrinks with it)
//            pose = {scale 0..1, flash 0..1 (turns to light), squash -1..1 (+ = tall), hold (no idle moves)}
//   dispose()
// Today: the artwork on an upright card (spriteCreature). An original 3D creature later implements the same members.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";

const HEIGHT = 0.35;   // metres
const ART = 0.84;      // official artwork has a clear margin: the hit box is the inner 84 %
const HOP = { ms: 460, up: 0.055 }, SWAY = { ms: 760 }, STRETCH = { ms: 620 };

/** Idle life: one move (hop / sway / stretch) after a random 0.2-1.6 s pause, like the GO encounter's action frequency. */
function idle(now, hold, st) {
  st.next ??= now + 500;
  if (hold) { st.act = null; st.next = now + 600; return { lift: 0, sq: 0, rot: 0 }; }
  if (!st.act && now >= st.next) {
    const r = Math.random();
    st.act = r < 0.55 ? "hop" : r < 0.8 ? "sway" : "stretch"; st.t0 = now; st.dir = Math.random() < 0.5 ? -1 : 1;
  }
  if (!st.act) return { lift: 0, sq: 0, rot: 0 };
  const T = now - st.t0;
  let out = { lift: 0, sq: 0, rot: 0 }, dur = 0;
  if (st.act === "hop") {
    dur = HOP.ms; const u = T / dur;
    if (u < 0.2) out.sq = -0.16 * Math.sin(Math.PI * u / 0.2);                  // crouch
    else if (u < 0.85) { const a = (u - 0.2) / 0.65; out.lift = HOP.up * 4 * a * (1 - a); out.sq = 0.1 * (1 - a) - 0.04; }   // up, stretched
    else out.sq = -0.12 * Math.sin(Math.PI * (u - 0.85) / 0.15);               // land
  } else if (st.act === "sway") {
    dur = SWAY.ms; out.rot = st.dir * 0.14 * Math.sin(2 * Math.PI * T / dur) * (1 - T / dur);
  } else {
    dur = STRETCH.ms; out.sq = 0.07 * Math.sin(Math.PI * T / dur);
  }
  if (T >= dur) { st.act = null; st.next = now + 200 + Math.random() * 1400; }
  return out;
}

/** The creature as its picture on an upright card (img from ar.loadImg). */
export function spriteCreature(img) {
  const t = new THREE.Texture(img); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4; t.needsUpdate = true;
  const aspect = img.naturalWidth / img.naturalHeight || 1;
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(HEIGHT * aspect, HEIGHT), new THREE.MeshBasicMaterial({ map: t, transparent: true, alphaTest: 0.02, depthWrite: false }));
  mesh.position.y = HEIGHT / 2;
  mesh.renderOrder = 2;
  const root = new THREE.Group(), tilt = new THREE.Group();
  tilt.add(mesh); root.add(tilt);
  const st = { act: null, next: null };
  return {
    root,
    hit: { w: HEIGHT * aspect * ART, h: HEIGHT * ART, cy: HEIGHT / 2 },
    shadowR: HEIGHT * aspect * 0.42,
    update(now, dt, pose, tint) {
      const m = idle(now, pose.hold, st), k = Math.max(0.001, pose.scale ?? 1);
      const sq = (pose.squash || 0) + m.sq, breathe = 0.03 * Math.sin(now / 1000 * 2.4);
      root.scale.set(k * (1 - 0.5 * sq), k * (1 + sq + breathe), k);   // squash about the feet
      tilt.position.y = m.lift; tilt.rotation.z = m.rot;
      mesh.material.color.copy(tint).multiplyScalar(1 + 7 * (pose.flash || 0));   // > 1 burns the picture to white
      return m.lift;
    },
    dispose() { mesh.material.map.dispose(); mesh.material.dispose(); mesh.geometry.dispose(); },
  };
}
