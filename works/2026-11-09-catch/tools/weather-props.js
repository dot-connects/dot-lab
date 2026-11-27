// Weather actions for the home-screen pets (2026-11-27 draft): the pet itself opens an umbrella, puts on a mask,
// wears a scarf. Props are 3D meshes hung on a creature from creatures.js and shaded with the creature's own fur
// shader (cloned with a short pile = felt, a long one = paws), so they sit in the same light as the fur.
// ACTIONS[name] = { frames, setup(rig) -> state, at(f, state, rig) -> { sx, sy, roll, happy } }; bake-weather.html drives them.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";

const V3 = THREE.Vector3, V2 = THREE.Vector2;
const dirOf = (yaw, pitch) => new V3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
const lerp = (a, b, t) => a + (b - a) * t;
const sm = x => x * x * (3 - 2 * x);
/** Keyframes [[frame, value], ...] with smoothstep between; value = number or array. */
function kf(f, keys) {
  if (f <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [f1, v1] = keys[i], [f0, v0] = keys[i - 1];
    if (f <= f1) { const a = sm((f - f0) / (f1 - f0)); return Array.isArray(v0) ? v0.map((x, j) => x + (v1[j] - x) * a) : v0 + (v1 - v0) * a; }
  }
  return keys.at(-1)[1];
}

// ---------- rig: what a prop needs to know about the pet ----------
/** creatures.js builds root > hopper > squash > model, the first body's fur shells are model.children[0]. */
export function rig(creature, spec) {
  const model = creature.object3d.children[0].children[0].children[0];
  const body = spec.body || spec.bodies[0], [a, b, c] = body.r, taper = body.taper || 0, flat = body.flat || 0;
  const c0 = new V3(...(body.c || [0, 0, 0]));
  const fn = d => { const k = 1 - taper * d.y; return new V3(a * d.x * k, b * d.y * (d.y < 0 ? 1 - flat : 1), c * d.z * k); };
  const shells = model.children[0].children.map(m => m.material);
  const foot = (spec.parts || []).find(e => e.kind === "blob" && e.mirror && e.c && e.c[1] < -0.15);
  const mats = new Map(), junk = [];
  const r = {
    model, fur: body.len ?? 0.065, face: spec.face, pawColor: foot?.color || spec.color, junk,
    /** A point on the body's skin at yaw/pitch (radians from the front), pushed `out` along the normal. */
    at(yaw, pitch, out = 0) {
      const e = 1e-3, d = dirOf(yaw, pitch);
      const du = fn(dirOf(yaw + e, pitch)).sub(fn(dirOf(yaw - e, pitch))), dv = fn(dirOf(yaw, pitch + e)).sub(fn(dirOf(yaw, pitch - e)));
      const n = new V3().crossVectors(du, dv).normalize(); if (n.dot(d) < 0) n.negate();
      return fn(d).add(c0).addScaledVector(n, out);
    },
    /** A mesh group in the fur shader: len = pile length (0.005 felt .. 0.09 fleece), den = strand density. */
    felt(geo, col, len = 0.012, den = 160) {
      const key = `${col}:${len}:${den}`;
      if (!mats.has(key)) mats.set(key, shells.map(m => { const x = m.clone(); x.uniforms.uC.value = new THREE.Color(col); x.uniforms.uLen.value = len; x.uniforms.uDen.value = den; return x; }));
      const g = new THREE.Group(); for (const m of mats.get(key)) g.add(new THREE.Mesh(geo, m));
      junk.push(geo); return g;
    },
    dispose() { junk.forEach(g => g.dispose()); mats.forEach(l => l.forEach(m => m.dispose())); },
  };
  return r;
}
/** Swap the contents of a holder group (props whose shape changes per frame). */
function refill(holder, ...objs) {
  for (const o of holder.children.slice()) { holder.remove(o); o.traverse(x => x.geometry?.dispose()); }
  holder.add(...objs);
}
const tube = (pts, rad, closed = false, segs = 40) => new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts, closed), segs, rad, 10, closed);

// ---------- umbrella ----------
const YELLOW = "#ffd54a", RIB = "#f0b62c", WOOD = "#dcb486";
const SHAFT = 0.9;   // hand -> canopy apex
/** Origin = the hand on the shaft, shaft along +y. open 0 = folded (a slim bud), 1 = open dome. */
function umbrella(r, open) {
  const g = new THREE.Group(), R = lerp(0.075, 0.58, open), h = lerp(0.48, 0.23, open), th = 0.03, N = 16;
  const pts = [];
  for (let i = 0; i <= N; i++) { const a = i / N * Math.PI / 2; pts.push(new V2(Math.max(1e-3, R * Math.sin(a)), -h * (1 - Math.cos(a)))); }
  for (let i = N; i >= 0; i--) { const a = i / N * Math.PI / 2; pts.push(new V2(Math.max(1e-3, (R - th) * Math.sin(a)), -h * (1 - Math.cos(a)) + th)); }
  const geo = new THREE.LatheGeometry(pts, 64), p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) {   // scalloped hem: the cloth lifts between the 8 ribs
    const x = p.getX(i), z = p.getZ(i), rho = Math.hypot(x, z) / R, th8 = Math.sin(4 * Math.atan2(z, x));
    p.setY(i, p.getY(i) + 0.07 * open * rho ** 6 * th8 * th8);
  }
  geo.computeVertexNormals();
  const canopy = new THREE.Group(); canopy.position.y = SHAFT; g.add(canopy);
  canopy.add(r.felt(geo, YELLOW, 0.014, 150));
  for (let k = 0; k < 8; k++) {
    const t = k * Math.PI / 4, rib = [];
    for (let i = 0; i <= 10; i++) { const a = i / 10 * Math.PI / 2; rib.push(new V3((R + 0.008) * Math.sin(a) * Math.cos(t), -h * (1 - Math.cos(a)) + 0.006, (R + 0.008) * Math.sin(a) * Math.sin(t))); }
    canopy.add(r.felt(tube(rib, 0.011, false, 16), RIB, 0.008));
    const nub = r.felt(new THREE.SphereGeometry(0.02, 10, 8), RIB, 0.006); nub.position.copy(rib.at(-1)); canopy.add(nub);
  }
  const tip = r.felt(new THREE.SphereGeometry(0.032, 14, 10), YELLOW, 0.012); tip.position.y = 0.02; canopy.add(tip);
  const shaft = r.felt(new THREE.CylinderGeometry(0.017, 0.017, SHAFT + 0.08, 10).translate(0, (SHAFT - 0.08) / 2, 0), WOOD, 0.004, 220);
  const hook = r.felt(new THREE.TorusGeometry(0.05, 0.019, 8, 20, Math.PI).rotateZ(Math.PI).translate(0.05, -0.08, 0), WOOD, 0.004, 220);
  g.add(shaft, hook);
  return g;
}
const paw = r => { const m = r.felt(new THREE.SphereGeometry(0.075, 20, 14).scale(1, 0.85, 0.9), r.pawColor, 0.03, 90); return m; };

function setupRain(r) {
  const holder = new THREE.Group(), hand = paw(r);
  r.model.add(holder, hand);
  return { holder, hand, open: -1 };
}
function placeUmbrella(r, s, { yaw, pitch, rot, open }) {
  const p = r.at(yaw, pitch, r.fur * 0.45);
  if (Math.abs(open - s.open) > 1e-4) { refill(s.holder, umbrella(r, open)); s.open = open; }
  s.holder.position.copy(p); s.holder.rotation.set(0, 0, rot);
  s.hand.position.copy(p).add(new V3(0.0, 0.0, 0.035));
}

// ---------- mask ----------
const CLOTH = "#ffffff", HEM = "#e2cfb0";
const MASK_W = 0.54, MASK_TOP = -0.04, MASK_BOT = -0.46;
function maskShape(r) {
  // u across (-1..1), v down (0..1); the top edge dips under the eyes at the sides, the sides round off
  const out = r.fur * 0.95;
  const pt = (u, v, lift = 0) => {
    const top = MASK_TOP - 0.07 * u * u, bot = MASK_BOT + 0.08 * u * u;
    const yaw = u * MASK_W * (0.82 + 0.18 * Math.sin(Math.PI * Math.min(1, Math.max(0, v))));
    const pleat = 0.012 * Math.abs(Math.sin(v * Math.PI * 3)) * (1 - u * u * 0.6);   // three soft folds
    return r.at(yaw, lerp(top, bot, v), out + pleat + lift);
  };
  const NU = 28, NV = 18, pos = [], idx = [];
  for (let j = 0; j <= NV; j++) for (let i = 0; i <= NU; i++) pos.push(...pt(i / NU * 2 - 1, j / NV).toArray());
  for (let j = 0; j < NV; j++) for (let i = 0; i < NU; i++) { const a = j * (NU + 1) + i, b = a + 1, c = a + NU + 1, d = c + 1; idx.push(a, c, b, b, c, d); }   // wound to face out of the body
  const geo = new THREE.BufferGeometry(); geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3)); geo.setIndex(idx); geo.computeVertexNormals();
  const edge = [];
  for (let i = 0; i <= 20; i++) edge.push(pt(i / 10 - 1, 0, 0.004));
  for (let j = 1; j <= 10; j++) edge.push(pt(1, j / 10, 0.004));
  for (let i = 19; i >= 0; i--) edge.push(pt(i / 10 - 1, 1, 0.004));
  for (let j = 9; j >= 1; j--) edge.push(pt(-1, j / 10, 0.004));
  const g = new THREE.Group();
  g.add(r.felt(geo, CLOTH, 0.005, 260), r.felt(tube(edge, 0.013, true, 120), HEM, 0.006, 200));
  // ear loops start at the side corners and run back over the cheek
  const loops = [-1, 1].map(s => [pt(s, 0.12), r.at(s * (MASK_W + 0.32), MASK_TOP - 0.02, out * 0.9), r.at(s * (MASK_W + 0.62), -0.22, out * 0.7),
    r.at(s * (MASK_W + 0.36), MASK_BOT + 0.04, out * 0.9), pt(s, 0.88)]);
  const centre = pt(0, 0.5);
  return { g, loops, centre, corner: pt(1, 0.6) };
}
function setupMask(r) {
  const m = maskShape(r), pivot = new THREE.Group(), loops = new THREE.Group(), hand = paw(r);
  m.g.position.copy(m.centre).negate(); pivot.add(m.g);
  r.model.add(pivot, loops, hand);
  return { ...m, pivot, loopHolder: loops, hand, loopK: -1 };
}
function placeMask(r, s, { pos, rot, scale, loop, hand }) {
  s.pivot.position.copy(s.centre).add(new V3(...pos)); s.pivot.rotation.set(...rot); s.pivot.scale.setScalar(scale);
  s.pivot.visible = scale > 0;
  if (loop !== s.loopK) {   // draw the first `loop` share of each loop's path
    s.loopK = loop;
    refill(s.loopHolder, ...(loop > 0.02 ? s.loops.map(pts => {
      const curve = new THREE.CatmullRomCurve3(pts), part = curve.getPoints(40).slice(0, Math.max(2, Math.round(40 * loop) + 1));
      return r.felt(tube(part, 0.011, false, 30), HEM, 0.005, 200);
    }) : []));
  }
  s.pivot.updateMatrix();
  s.hand.visible = hand;
  s.hand.position.copy(s.corner.clone().sub(s.centre).applyMatrix4(s.pivot.matrix)).add(new V3(0.03, -0.02, 0.03));
}

// ---------- scarf ----------
const RED = "#d2493f", CREAM = "#f7e0bd";
function setupScarf(r) {
  const ring = new THREE.Group(), tail = new THREE.Group();
  const out = r.fur * 0.5 + 0.045;
  const pts = [];
  for (let i = 0; i < 48; i++) { const t = i / 48 * Math.PI * 2; pts.push(r.at(t, -0.5 + 0.16 * (1 - Math.cos(t)) / 2, out)); }
  const curve = new THREE.CatmullRomCurve3(pts, true), all = curve.getSpacedPoints(240), STR = 16;
  for (let k = 0; k < STR; k++) {   // knit stripes: one tube per band, bands meet end to end
    const seg = all.slice(k * 15, k * 15 + 16);
    ring.add(r.felt(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(seg), 12, 0.06, 12, false).scale(1, 0.8, 1), k % 2 ? CREAM : RED, 0.022, 120));
  }
  r.model.add(ring, tail);
  return { ring, tail, knot: r.at(0.42, -0.5, out + 0.03), out };
}
function placeScarf(r, s, phase) {
  // the loose end hangs from the knot over the chest; its tip swings a little
  const k = s.knot, w = Math.sin(phase) * 0.05;
  const path = [k, k.clone().add(new V3(0.03, -0.07, 0.05)), k.clone().add(new V3(0.05 + w * 0.4, -0.14, 0.07)), k.clone().add(new V3(0.07 + w, -0.21, 0.07))];
  const curve = new THREE.CatmullRomCurve3(path), all = curve.getSpacedPoints(60), parts = [];
  for (let b = 0; b < 4; b++) {
    const seg = all.slice(b * 15, b * 15 + 16);
    parts.push(r.felt(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(seg), 8, 0.058, 12, false).scale(1, 1, 0.55), b % 2 ? RED : CREAM, 0.022, 120));
  }
  const end = all.at(-1), dir = all.at(-1).clone().sub(all.at(-4)).normalize();
  for (let i = 0; i < 4; i++) {   // fringe
    const f = r.felt(new THREE.CylinderGeometry(0.012, 0.01, 0.07, 6).translate(0, -0.035, 0), CREAM, 0.008, 200);
    f.position.copy(end).add(new V3((i - 1.5) * 0.03, 0.01, 0)); f.rotation.z = -dir.x * 0.8 + (i - 1.5) * 0.08; parts.push(f);
  }
  const knot = r.felt(new THREE.SphereGeometry(0.075, 16, 12).scale(1, 0.9, 0.7), RED, 0.022, 120); knot.position.copy(k); parts.push(knot);
  refill(s.tail, ...parts);
}

// ---------- the actions ----------
const settle = keys => f => { const sy = kf(f, keys); return { sy, sx: 1 + (1 - sy) * 0.6 }; };
export const ACTIONS = {
  // stop, the folded umbrella comes round from behind the right side, pops open over the head, happy wiggle
  "rain-open": { frames: 16, setup: setupRain, at(f, s, r) {
    const shown = f >= 2;
    s.holder.visible = s.hand.visible = shown;
    if (shown) placeUmbrella(r, s, {
      yaw: kf(f, [[2, 2.6], [6, 0.98]]), pitch: kf(f, [[2, -0.05], [6, -0.3]]),
      rot: kf(f, [[2, -0.55], [6, 0.24], [9, 0.18], [11, 0.26], [12, 0.24]]),
      open: kf(f, [[7, 0], [9, 1.1], [10, 0.95], [11, 1]]) });
    const b = settle([[0, 1], [1, 0.94], [2, 1.02], [3, 1], [8, 1], [9, 0.93], [11, 1.05], [13, 0.98], [15, 1]])(f);
    return { ...b, roll: kf(f, [[11, 0], [12, -4], [13, 3], [14, -1.5], [15, 0]]), happy: f >= 10 && f <= 14 };
  } },
  "rain-hold": { frames: 4, setup: setupRain, at(f, s, r) {
    placeUmbrella(r, s, { yaw: 0.98, pitch: -0.3, rot: 0.24 + 0.045 * Math.sin(f * Math.PI / 2), open: 1 });
    return { sx: 1, sy: 1 + 0.015 * Math.cos(f * Math.PI / 2), roll: 1.2 * Math.sin(f * Math.PI / 2), happy: false };
  } },
  // the mask comes up from the lower right in a paw, presses onto the face, the loops go round, a pleased squint
  "mask-on": { frames: 14, setup: setupMask, at(f, s, r) {
    const shown = f >= 2;
    placeMask(r, s, {
      pos: kf(f, [[2, [0.4, -0.16, 0.32]], [5, [0.14, -0.1, 0.22]], [7, [0, 0, 0.07]], [8, [0, 0, 0]]]),
      rot: kf(f, [[2, [0.15, -0.35, -0.5]], [5, [0.05, -0.15, -0.15]], [7, [0, 0, 0]]]),
      scale: shown ? kf(f, [[2, 0.7], [6, 1]]) : 0, loop: kf(f, [[7, 0], [9, 1]]), hand: shown && f <= 8 });
    const b = settle([[0, 1], [1, 0.95], [2, 1], [8, 1], [9, 0.94], [10, 1.04], [12, 1]])(f);
    return { ...b, roll: kf(f, [[9, 0], [10, 4], [11, -3], [12, 1], [13, 0]]), happy: f >= 9 && f <= 12 };
  } },
  "mask-idle": { frames: 4, setup: setupMask, at(f, s, r) {
    placeMask(r, s, { pos: [0, 0, 0], rot: [0, 0, 0], scale: 1, loop: 1, hand: false });
    return { sx: 1, sy: 1 + 0.016 * Math.sin(f * Math.PI / 2), roll: 0, happy: false };
  } },
  "scarf": { frames: 4, setup: setupScarf, at(f, s, r) {
    placeScarf(r, s, f * Math.PI / 2);
    return { sx: 1, sy: 1 + 0.015 * Math.sin(f * Math.PI / 2), roll: 1.5 * Math.sin(f * Math.PI / 2), happy: false };
  } },
};
