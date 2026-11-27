// Weather actions for the home-screen pets (v2, 2026-11-27): six states, each an action the pet does then a calm loop.
// sun = stretch + sunglasses, hot = flush + sweat + hand fan, cold = shiver + knit hat, snow = scarf wraps + catches
// a flake, rain = drop on the head + umbrella pops open, dust = cough + mask + nod.
// Props are 3D meshes hung on a creature from creatures.js and shaded with the creature's own fur shader (cloned with
// a short pile = felt), so they sit in the same light as the fur. Props stay close to the body (operator: the pet
// must not look bigger): umbrella canopy <= 1.3x body width, the rest inside the silhouette or barely outside.
// ACTIONS["<state>-act" | "<state>-loop"] = { frames, loop?, setup(rig) -> s, body(f) -> pose, props(f, s, rig) }
// pose = { sq (height scale, width keeps volume), roll, pitch (+ = nod forward), turn (deg), y (x height), eye (1 open,
// 0.1 shut), happy (^ ^ eyes), ear (extra ear swing) }. bake-weather.html applies the pose and the ear follow-through.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";

const V3 = THREE.Vector3, V2 = THREE.Vector2;
const dirOf = (yaw, pitch) => new V3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
const lerp = (a, b, t) => a + (b - a) * t;
const clamp01 = x => Math.max(0, Math.min(1, x));
const TAU = Math.PI * 2;
// eases for one keyframe segment: s = in-out, o = out (fast start), i = in (fast end), b = out with overshoot, l = linear
const EASE = { s: x => x * x * (3 - 2 * x), o: x => 1 - (1 - x) ** 3, i: x => x ** 3, l: x => x,
  b: x => { const c = 2.2; return 1 + (c + 1) * (x - 1) ** 3 + c * (x - 1) ** 2; } };
/** Keyframes [[frame, value, ease?], ...]; the ease belongs to the segment that ends at that key. value = number or array. */
export function kf(f, keys) {
  if (f <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [f1, v1, e = "s"] = keys[i], [f0, v0] = keys[i - 1];
    if (f <= f1) { const a = EASE[e]((f - f0) / (f1 - f0)); return Array.isArray(v0) ? v0.map((x, j) => x + (v1[j] - x) * a) : v0 + (v1 - v0) * a; }
  }
  return keys.at(-1)[1];
}
/** A damped wobble that starts at frame f0 with amplitude amp (settle after an impact). */
const spring = (f, f0, amp, w = 1.7, d = 0.45) => f < f0 ? 0 : amp * Math.exp(-d * (f - f0)) * Math.cos(w * (f - f0));
/** Shiver: alternates sign every frame, slightly uneven. */
const shake = (f, amp) => amp * (f % 2 ? -1 : 1) * (0.8 + 0.2 * Math.sin(f * 2.3));
const cyc = (f, n, ph = 0) => Math.sin(TAU * f / n + ph);

// ---------- rig: what a prop needs to know about the pet ----------
/** creatures.js builds root > hopper > squash > model, the first body's fur shells are model.children[0]. */
export function rig(creature, spec) {
  const model = creature.object3d.children[0].children[0].children[0];
  const body = spec.body || spec.bodies[0], [a, b, c] = body.r, taper = body.taper || 0, flat = body.flat || 0;
  const c0 = new V3(...(body.c || [0, 0, 0]));
  const fn = d => { const k = 1 - taper * d.y; return new V3(a * d.x * k, b * d.y * (d.y < 0 ? 1 - flat : 1), c * d.z * k); };
  const shells = model.children[0].children.map(m => m.material);
  // eye groups: setPose("caught") swaps their visibility (update() is never called, so nothing else moves)
  creature.setPose("caught"); const vis = model.children.map(o => o.visible); creature.setPose("idle");
  const eyes = model.children.find((o, i) => !vis[i] && o.visible), happy = model.children.find((o, i) => vis[i] && !o.visible);
  // part holders are the last children build() added, in spec.parts order (mirrored parts twice, -1 then +1)
  const holders = model.children.slice(-(spec.parts || []).reduce((n, e) => n + (e.mirror ? 2 : 1), 0));
  const wob = []; let hi = 0;
  for (const e of spec.parts || []) for (const s of e.mirror ? [-1, 1] : [1]) {
    const o = holders[hi++];
    if (e.wob) wob.push({ o, base: o.rotation.z, k: e.wob * s });
  }
  // paws: the foot colour, pushed a little darker and warmer when it is close to the fur (냥솜: a white paw vanished on white fur)
  const foot = (spec.parts || []).find(e => e.kind === "blob" && e.mirror && e.c && e.c[1] < -0.15);
  const pc = new THREE.Color(foot?.color || spec.color), hsl = {}; pc.getHSL(hsl);
  if (hsl.l > 0.55) pc.lerp(new THREE.Color("#9c7c66"), 0.4);
  const mats = new Map(), junk = [];
  const r = {
    model, eyes, happy, wob, fur: body.len ?? 0.065, face: spec.face, pawColor: "#" + pc.getHexString(), junk,
    width: a * 2,
    /** A point on the body's skin at yaw/pitch (radians from the front), pushed `out` along the normal. */
    at(yaw, pitch, out = 0) { return fn(dirOf(yaw, pitch)).add(c0).addScaledVector(r.nrm(yaw, pitch), out); },
    nrm(yaw, pitch) {
      const e = 1e-3, d = dirOf(yaw, pitch);
      const du = fn(dirOf(yaw + e, pitch)).sub(fn(dirOf(yaw - e, pitch))), dv = fn(dirOf(yaw, pitch + e)).sub(fn(dirOf(yaw, pitch - e)));
      const n = new V3().crossVectors(du, dv).normalize(); return n.dot(d) < 0 ? n.negate() : n;
    },
    /** A mesh group in the fur shader: len = pile length (0.004 smooth felt .. 0.09 fleece), den = strand density. */
    felt(geo, col, len = 0.012, den = 160) {
      const key = `${col}:${len}:${den}`;
      if (!mats.has(key)) mats.set(key, shells.map(m => { const x = m.clone(); x.uniforms.uC.value = new THREE.Color(col); x.uniforms.uLen.value = len; x.uniforms.uDen.value = den; return x; }));
      const g = new THREE.Group(); for (const m of mats.get(key)) g.add(new THREE.Mesh(geo, m));
      junk.push(geo); return g;
    },
    /** Unlit flat colour (glints, sparkles). */
    flat(geo, col, opacity = 1) {
      const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: col, transparent: opacity < 1, opacity, depthWrite: opacity >= 1 }));
      junk.push(geo, m.material); return m;
    },
    dispose() { junk.forEach(g => g.dispose()); mats.forEach(l => l.forEach(m => m.dispose())); },
  };
  return r;
}
/** Swap the contents of a holder group (props whose shape changes per frame). */
function refill(holder, ...objs) {
  for (const o of holder.children.slice()) { holder.remove(o); o.traverse(x => x.geometry?.dispose()); }
  if (objs.length) holder.add(...objs);
}
const tube = (pts, rad, closed = false, segs = 40) => new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts, closed), segs, rad, 10, closed);
const ball = (r, rad, col, len = 0.01, den = 180) => r.felt(new THREE.SphereGeometry(rad, 16, 12), col, len, den);
function star(r, size, col, points = 4) {   // a flat sparkle, facing the camera
  const s = new THREE.Shape();
  for (let i = 0; i <= points * 2; i++) { const a = i / (points * 2) * TAU + Math.PI / 2, d = i % 2 ? size * 0.28 : size; i ? s.lineTo(Math.cos(a) * d, Math.sin(a) * d) : s.moveTo(Math.cos(a) * d, Math.sin(a) * d); }
  return r.flat(new THREE.ShapeGeometry(s), col);
}
/** A paw (round mitten) in the pet's paw colour; reach(t) brings it from the side of the body to `to`. */
const paw = r => r.felt(new THREE.SphereGeometry(0.075, 20, 14).scale(1, 0.85, 0.9), r.pawColor, 0.02, 120);
function reach(r, hand, side, to, t) {
  hand.visible = t > 0.02;
  if (!hand.visible) return;
  const home = r.at(side * 1.3, -0.35, r.fur * 0.2);
  hand.position.lerpVectors(home, to, t); hand.scale.setScalar(0.55 + 0.45 * t);
}

// ---------- sun: sunglasses ----------
const LENS = "#43302c", FRAME = "#e8694c", SPARK = "#ffc93c";
function shades(r) {
  const f = r.face, out = r.fur * 0.8 + 0.012, g = new THREE.Group(), c = r.at(0, f.y, out);
  for (const s of [-1, 1]) {
    const yaw = s * (f.x + 0.02), L = new THREE.Group();
    L.position.copy(r.at(yaw, f.y, out)).sub(c); L.quaternion.setFromUnitVectors(new V3(0, 0, 1), r.nrm(yaw, f.y));
    L.add(r.felt(new THREE.SphereGeometry(1, 24, 14).scale(0.105, 0.085, 0.03), LENS, 0.004, 320));
    L.add(r.felt(new THREE.TorusGeometry(0.105, 0.019, 8, 32).scale(1, 0.82, 1), FRAME, 0.005, 260));
    const gl = r.flat(new THREE.CircleGeometry(0.026, 14).scale(1, 0.55, 1), "#fff7e6"); gl.position.set(-0.035, 0.032, 0.034); gl.rotation.z = 0.5; L.add(gl);
    g.add(L);
    // a short temple running back over the cheek
    g.add(r.felt(tube([r.at(s * (f.x + 0.24), f.y + 0.02, out), r.at(s * (f.x + 0.45), f.y + 0.04, out * 0.8), r.at(s * (f.x + 0.65), f.y + 0.05, out * 0.5)].map(p => p.sub(c)), 0.016, false, 16), FRAME, 0.005, 260));
  }
  g.add(r.felt(tube([r.at(-0.1, f.y + 0.02, out), r.at(0, f.y + 0.06, out + 0.01), r.at(0.1, f.y + 0.02, out)].map(p => p.sub(c)), 0.016, false, 16), FRAME, 0.005, 260));
  const glint = star(r, 0.07, "#fffbe8"); glint.position.set(-0.17, 0.08, 0.06); g.add(glint);
  return { g, c, glint };
}
function setupSun(r) {
  const sh = shades(r), pivot = new THREE.Group(), hand = paw(r), sparks = [0, 1, 2].map(() => star(r, 0.06, SPARK));
  pivot.add(sh.g);   // shades() already builds around its centre
  r.model.add(pivot, hand, ...sparks);
  return { ...sh, pivot, hand, sparks };
}
function placeShades(r, s, { pos = [0, 0, 0], rot = 0, scale = 1, glint = 0 }) {
  s.pivot.visible = scale > 0.01;
  s.pivot.position.copy(s.c).add(new V3(...pos)); s.pivot.rotation.set(0, 0, rot); s.pivot.scale.setScalar(Math.max(scale, 1e-3));
  s.glint.scale.setScalar(Math.max(glint, 1e-3)); s.glint.visible = glint > 0.01; s.glint.rotation.z = glint * 0.8;
}
const SPARK_AT = [[-0.42, 0.52, 0.2], [0.46, 0.4, 0.2], [0.12, 0.66, 0.1]];
function sparkle(s, f, starts) {   // each spark pops (0 -> 1.15 -> 0) over 5 frames from its start frame
  s.sparks.forEach((m, i) => {
    const t = f - starts[i], k = t < 0 || t > 5 ? 0 : kf(t, [[0, 0], [2, 1.15, "b"], [5, 0, "i"]]);
    m.visible = k > 0.01; m.scale.setScalar(Math.max(k, 1e-3)); m.position.set(...SPARK_AT[i]); m.rotation.z = t * 0.25;
  });
}

// ---------- hot: flush, sweat drop, round hand fan ----------
const FAN = "#5fb8c9", FAN_RIM = "#fdf3df", WOOD = "#c08a5a", SWEAT = "#79c2ea";
function fan(r) {
  const g = new THREE.Group(), R = 0.17, H = 0.2;   // origin = the paw on the handle; the face is a disc up the +y axis
  g.add(r.felt(new THREE.CylinderGeometry(0.016, 0.016, H + 0.02, 8).translate(0, H / 2, 0), WOOD, 0.004, 220));
  const face = new THREE.Group(); face.position.y = H + R * 0.8; g.add(face);
  face.add(r.felt(new THREE.CylinderGeometry(R, R, 0.022, 36).rotateX(Math.PI / 2).scale(1, 0.92, 1), FAN, 0.006, 220));
  face.add(r.felt(new THREE.TorusGeometry(R, 0.018, 8, 40).scale(1, 0.92, 1), FAN_RIM, 0.006, 220));
  for (let k = -2; k <= 2; k++) face.add(r.felt(tube([new V3(0, -R * 0.85, 0.016), new V3(k * 0.06, -R * 0.2, 0.016), new V3(k * 0.1, R * 0.6, 0.016)], 0.007, false, 10), FAN_RIM, 0.004, 220));
  return g;
}
function sweatDrop(r) {
  const g = new THREE.Group();
  g.add(r.felt(new THREE.SphereGeometry(0.05, 16, 12), SWEAT, 0.004, 300));
  g.add(r.felt(new THREE.ConeGeometry(0.043, 0.07, 16).translate(0, 0.055, 0), SWEAT, 0.004, 300));
  const hl = r.flat(new THREE.CircleGeometry(0.014, 10), "#ffffff"); hl.position.set(-0.016, 0.012, 0.05); g.add(hl);
  return g;
}
function setupHot(r) {
  const f = r.face, flush = [-1, 1].map(s => {
    const yaw = s * (f.x + 0.18), m = r.flat(new THREE.CircleGeometry(0.11, 24).scale(1, 0.62, 1), "#f0705e", 0.7);
    m.position.copy(r.at(yaw, f.y - 0.16, r.fur * 0.6)); m.quaternion.setFromUnitVectors(new V3(0, 0, 1), r.nrm(yaw, f.y - 0.16));
    m.renderOrder = 2; return m;
  });
  const hand = paw(r), fanG = fan(r), drop = sweatDrop(r);
  r.model.add(...flush, hand, fanG, drop);
  return { flush, hand, fan: fanG, drop };
}
const FAN_AT = () => [0.4, -0.2, 0.42];   // the paw holding the fan, at the right cheek, in front
function placeHot(r, s, { flush, drop, dropSlide, fanT, fanRot }) {
  s.flush.forEach(m => { m.visible = flush > 0.02; m.material.opacity = 0.6 * flush; });
  s.drop.visible = drop > 0.02;
  const pitch = lerp(0.62, 0.3, dropSlide);
  s.drop.position.copy(r.at(-0.78, pitch, r.fur * 0.9)); s.drop.scale.setScalar(Math.max(drop, 1e-3)); s.drop.rotation.z = 0.35;
  const to = new V3(...FAN_AT());
  reach(r, s.hand, 1, to, fanT);
  s.fan.visible = fanT > 0.02;
  s.fan.position.copy(s.hand.position); s.fan.scale.setScalar(Math.max(1e-3, s.hand.scale.x));
  s.fan.rotation.set(-0.1, fanRot, lerp(-1.2, 0.15, fanT) + 0.3 * fanRot);   // the face swings about the handle toward the face
}

// ---------- cold: knit hat with a pompom, breath puffs ----------
const HAT = "#e07656", CUFF = "#f6e7c9", BREATH = "#cfe1ee";
function hat(r) {
  const out = r.fur * 0.55 + 0.03, P0 = 0.42, NU = 40, NV = 10, pos = [], idx = [];
  const pt = (u, v) => { const yaw = u * TAU, pitch = lerp(P0 + 0.06 * Math.cos(yaw), Math.PI / 2 - 0.02, v);
    return r.at(yaw, pitch, out + 0.05 * v * v); };
  for (let j = 0; j <= NV; j++) for (let i = 0; i <= NU; i++) pos.push(...pt(i / NU, j / NV).toArray());
  for (let j = 0; j < NV; j++) for (let i = 0; i < NU; i++) { const a = j * (NU + 1) + i, b = a + 1, c = a + NU + 1, d = c + 1; idx.push(a, b, c, b, d, c); }
  const geo = new THREE.BufferGeometry(); geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3)); geo.setIndex(idx); geo.computeVertexNormals();
  const top = r.at(0, Math.PI / 2 - 0.02, out + 0.05), base = r.at(0, P0, 0).y, g = new THREE.Group(), body = new THREE.Group();
  body.add(r.felt(geo, HAT, 0.016, 140));
  for (let k = 0; k < 10; k++) {   // knit ribs: faint darker lines from the cuff up
    const u = k / 10, line = []; for (let j = 0; j <= 6; j++) line.push(pt(u, j / 6 * 0.8).addScaledVector(r.nrm(u * TAU, 0.8), 0.006));
    body.add(r.felt(tube(line, 0.007, false, 12), "#c95f43", 0.004, 200));
  }
  const cuff = []; for (let i = 0; i < 48; i++) cuff.push(pt(i / 48, 0.04));
  body.add(r.felt(tube(cuff, 0.05, true, 96), CUFF, 0.016, 140));
  const pom = ball(r, 0.085, CUFF, 0.03, 120); pom.position.copy(top).add(new V3(0, 0.06, 0)); body.add(pom);
  body.position.y = -base; g.add(body); g.position.y = base;   // pivot = the cuff line, so the hat squashes onto the head
  return { g, pom };
}
function setupCold(r) {
  const h = hat(r), hands = [paw(r), paw(r)], puffs = [0, 1, 2].map(() => ball(r, 0.07, BREATH, 0.02, 120));
  r.model.add(h.g, ...hands, ...puffs);
  return { ...h, hands, puffs, base: h.g.position.y };
}
function placeHat(r, s, { drop, tilt = 0, squash = 1, pull = 0, pom = 0 }) {
  s.g.visible = drop < 1.5;
  s.g.position.set(0, s.base + drop, 0); s.g.rotation.set(0, 0, tilt); s.g.scale.set(1 / Math.sqrt(squash), squash, 1 / Math.sqrt(squash));
  s.pom.rotation.z = pom; s.pom.position.x = pom * 0.04;
  s.hands.forEach((h, i) => reach(r, h, i ? 1 : -1, r.at((i ? 1 : -1) * 1.05, 0.5 - drop * 0.3, r.fur * 0.6), pull));
}
/** Breath: puffs leave the mouth, swell and drift up and out, then shrink away. t = frames since the breath. */
function breath(r, s, t, side = 1) {
  const m = r.at(0, r.face.y - 0.16, r.fur * 0.9);
  s.puffs.forEach((p, i) => {
    const u = (t - i * 1.2) / 6, k = u < 0 || u > 1 ? 0 : Math.sin(Math.PI * Math.sqrt(u)) * (1 - i * 0.18);
    p.visible = k > 0.03; p.scale.setScalar(Math.max(k, 1e-3) * (0.9 + u * 0.6));
    p.position.copy(m).add(new V3(side * (0.12 + u * 0.42), 0.02 + u * 0.14 + i * 0.03, 0.14 + u * 0.08));
  });
}

// ---------- snow: knit scarf wrapping round the neck, snowflakes ----------
const RED = "#d2493f", CREAM = "#f7e0bd", FLAKE = "#8fc2ec";
function setupScarf(r) {
  const ring = new THREE.Group(), tail = new THREE.Group(), out = r.fur * 0.5 + 0.045, y0 = 0.42;
  const pts = [];
  for (let i = 0; i < 48; i++) { const t = y0 + i / 48 * TAU; pts.push(r.at(t, -0.5 + 0.16 * (1 - Math.cos(t)) / 2, out)); }
  const all = new THREE.CatmullRomCurve3(pts, true).getSpacedPoints(240);
  const flakes = [snowflake(r), snowflake(r)];
  r.model.add(ring, tail, ...flakes);
  return { ring, tail, all, flakes, knot: r.at(y0, -0.5, out + 0.03), wrap: -1 };
}
function placeScarf(r, s, { wrap, swing, hang }) {
  if (Math.abs(wrap - s.wrap) > 1e-4) {   // knit stripes: the first `wrap` share of the loop, one tube per band
    s.wrap = wrap;
    const n = Math.round(wrap * 240), parts = [];
    for (let k = 0; k * 15 < n; k++) {
      const seg = s.all.slice(k * 15, Math.min(n, k * 15 + 15) + 1);
      if (seg.length < 2) break;
      parts.push(r.felt(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(seg), 12, 0.06, 12, false).scale(1, 0.8, 1), k % 2 ? CREAM : RED, 0.022, 120));
    }
    if (n > 0 && n < 240) { const cap = ball(r, 0.058, (Math.floor((n - 1) / 15)) % 2 ? CREAM : RED, 0.022, 120); cap.position.copy(s.all[n]); cap.scale.set(1, 0.8, 1); parts.push(cap); }
    refill(s.ring, ...parts);
  }
  if (hang <= 0.01) { refill(s.tail); return; }
  // the loose end hangs from the knot over the chest; hang 0..1 lets it fall, swing moves its tip
  const k = s.knot, w = swing * 0.06, L = hang;
  const path = [k, k.clone().add(new V3(0.03 + w * 0.2, -0.06 * L, 0.05)), k.clone().add(new V3(0.05 + w * 0.5, -0.115 * L, 0.07)), k.clone().add(new V3(0.07 + w, -0.17 * L, 0.07 + Math.abs(w) * 0.3))];
  const all = new THREE.CatmullRomCurve3(path).getSpacedPoints(60), parts = [];
  for (let b = 0; b < 4; b++) parts.push(r.felt(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(all.slice(b * 15, b * 15 + 16)), 8, 0.058, 12, false).scale(1, 1, 0.55), b % 2 ? RED : CREAM, 0.022, 120));
  const end = all.at(-1), dir = all.at(-1).clone().sub(all.at(-4)).normalize();
  for (let i = 0; i < 4; i++) {   // fringe
    const fr = r.felt(new THREE.CylinderGeometry(0.012, 0.01, 0.07, 6).translate(0, -0.035, 0), CREAM, 0.008, 200);
    fr.position.copy(end).add(new V3((i - 1.5) * 0.03, 0.01, 0)); fr.rotation.z = -dir.x * 0.8 + (i - 1.5) * 0.08 + w; parts.push(fr);
  }
  const knot = ball(r, 0.075, RED, 0.022, 120); knot.position.copy(k); knot.scale.set(1, 0.9, 0.7); parts.push(knot);
  refill(s.tail, ...parts);
}
function snowflake(r) {
  const g = new THREE.Group();
  for (let k = 0; k < 3; k++) {
    const arm = r.felt(new THREE.BoxGeometry(0.15, 0.024, 0.02), FLAKE, 0.006, 240); arm.rotation.z = k * Math.PI / 3; g.add(arm);
  }
  for (let k = 0; k < 6; k++) {   // little v-twigs near each tip
    const a = k * Math.PI / 3, tw = r.felt(new THREE.BoxGeometry(0.05, 0.016, 0.016), FLAKE, 0.004, 240);
    tw.position.set(Math.cos(a) * 0.05, Math.sin(a) * 0.05, 0); tw.rotation.z = a + Math.PI / 2; g.add(tw);
  }
  g.add(ball(r, 0.022, "#d9ecfb", 0.004, 240));
  return g;
}
function placeFlake(fl, p, k, spin) { fl.visible = k > 0.02; if (fl.visible) { fl.position.copy(p); fl.scale.setScalar(k); fl.rotation.set(0.3, 0, spin); } }

// ---------- rain: umbrella with striped panels, drops ----------
const YELLOW = "#ffc83d", STRIPE = "#fff3d4", RIB = "#d99a22", HANDLE = "#9b6a43", DROP = "#5fa6dc";
const SHAFT = 0.84;   // hand -> canopy apex
/** Origin = the hand on the shaft, shaft along +y. open 0 = folded (a slim bud), 1 = open dome. */
function umbrella(r, open) {
  const g = new THREE.Group(), R = lerp(0.075, 0.58, open), h = lerp(0.48, 0.24, open), th = 0.03, N = 16;
  const pts = [];
  for (let i = 0; i <= N; i++) { const a = i / N * Math.PI / 2; pts.push(new V2(Math.max(1e-3, R * Math.sin(a)), -h * (1 - Math.cos(a)))); }
  for (let i = N; i >= 0; i--) { const a = i / N * Math.PI / 2; pts.push(new V2(Math.max(1e-3, (R - th) * Math.sin(a)), -h * (1 - Math.cos(a)) + th)); }
  const canopy = new THREE.Group(); canopy.position.y = SHAFT; g.add(canopy);
  for (let k = 0; k < 8; k++) {   // 8 panels in two colours: the stripes say "umbrella" even at 112 px
    const geo = new THREE.LatheGeometry(pts, 8, k * Math.PI / 4, Math.PI / 4), p = geo.attributes.position;
    for (let i = 0; i < p.count; i++) {   // scalloped hem: the cloth lifts between the ribs
      const x = p.getX(i), z = p.getZ(i), rho = Math.hypot(x, z) / R, s8 = Math.sin(4 * Math.atan2(z, x));
      p.setY(i, p.getY(i) + 0.09 * open * rho ** 5 * s8 * s8);
    }
    geo.computeVertexNormals();
    canopy.add(r.felt(geo, k % 2 ? STRIPE : YELLOW, 0.012, 150));
  }
  for (let k = 0; k < 8; k++) {
    const t = k * Math.PI / 4, rib = [];
    for (let i = 0; i <= 10; i++) { const a = i / 10 * Math.PI / 2; rib.push(new V3((R + 0.008) * Math.sin(a) * Math.cos(t), -h * (1 - Math.cos(a)) + 0.006, (R + 0.008) * Math.sin(a) * Math.sin(t))); }
    canopy.add(r.felt(tube(rib, 0.011, false, 16), RIB, 0.006));
    const nub = ball(r, 0.022, RIB, 0.006); nub.position.copy(rib.at(-1)); canopy.add(nub);
  }
  const tip = ball(r, 0.034, RIB, 0.008); tip.position.y = 0.025; canopy.add(tip);
  g.add(r.felt(new THREE.CylinderGeometry(0.018, 0.018, SHAFT + 0.1, 10).translate(0, (SHAFT - 0.1) / 2, 0), HANDLE, 0.004, 220));
  // the J handle hangs below the paw, in front of it, so the grip reads even on a pale paw
  g.add(r.felt(new THREE.TorusGeometry(0.05, 0.021, 8, 20, Math.PI).rotateZ(Math.PI).translate(0.05, -0.1, 0.02), HANDLE, 0.004, 220));
  return g;
}
const raindrop = r => { const g = new THREE.Group();
  g.add(r.felt(new THREE.SphereGeometry(0.036, 14, 10), DROP, 0.004, 300), r.felt(new THREE.ConeGeometry(0.031, 0.06, 14).translate(0, 0.045, 0), DROP, 0.004, 300));
  return g; };
function setupRain(r) {
  const holder = new THREE.Group(), hand = paw(r), drops = [0, 1, 2, 3].map(() => raindrop(r));
  r.model.add(holder, hand, ...drops);
  return { holder, hand, drops, open: -1 };
}
const UMB = { yaw: 0.98, pitch: -0.3 };
function placeUmbrella(r, s, { yaw, pitch, rot, open, show = true }) {
  s.holder.visible = s.hand.visible = show;
  if (!show) return;
  const p = r.at(yaw, pitch, r.fur * 0.45);
  if (Math.abs(open - s.open) > 1e-4) { refill(s.holder, umbrella(r, open)); s.open = open; }
  s.holder.position.copy(p); s.holder.rotation.set(0, 0, rot); s.holder.updateMatrix();
  s.hand.position.copy(p).add(new V3(0, 0.01, 0.0)); s.hand.scale.setScalar(1);
}
/** A point on the open canopy (u = -1 left edge .. 1 right edge), in model space, for the current umbrella pose. */
function canopyPoint(s, u) {
  const R = 0.58, h = 0.24, a = Math.asin(Math.min(1, Math.abs(u))), y = SHAFT - h * (1 - Math.cos(a)) + 0.04;
  return new V3(u * R, y, 0.12).applyMatrix4(s.holder.matrix);
}
/** A drop falls onto `hit` (arrives at frame f0), then bounces off sideways and shrinks away. */
function dropAt(d, f, f0, hit, side) {
  const t = f - f0;
  if (t <= -3 || t > 4) { d.visible = false; return; }
  d.visible = true;
  if (t <= 0) { d.position.copy(hit).add(new V3(0, -t * 0.16, 0)); d.scale.set(0.9, 1.25, 0.9); d.rotation.z = 0; return; }   // falling, stretched
  if (t < 1) { d.position.copy(hit); d.scale.set(1.5, 0.55, 1.5); return; }   // splat
  const u = t - 1;   // bounce: a small arc out and down
  d.position.copy(hit).add(new V3(side * u * 0.07, 0.07 * u - 0.025 * u * u, 0.02 * u));
  const k = Math.max(0.001, 0.75 - u * 0.2); d.scale.setScalar(k); d.rotation.z = -side * 0.9;
}

// ---------- dust: cough cloud, mask ----------
const MASK = "#bfe0ee", MASK_EDGE = "#9cc8dc", LOOP = "#86b4c9", DUST = "#b6a690";
const MASK_W = 0.56, MASK_TOP = -0.05, MASK_BOT = -0.5;
function maskShape(r) {
  // u across (-1..1), v down (0..1): a KF94-like cup, a little deeper in the middle; no pleats (pleats read as teeth at 112 px)
  const out = r.fur * 0.95;
  const pt = (u, v, lift = 0) => {
    const top = MASK_TOP - 0.08 * u * u, bot = MASK_BOT + 0.1 * u * u - 0.04 * (1 - u * u);
    const yaw = u * MASK_W * (0.85 + 0.15 * Math.sin(Math.PI * clamp01(v)));
    const cup = 0.035 * (1 - u * u) * Math.sin(Math.PI * clamp01(v));
    return r.at(yaw, lerp(top, bot, v), out + cup + lift);
  };
  const NU = 28, NV = 18, pos = [], idx = [];
  for (let j = 0; j <= NV; j++) for (let i = 0; i <= NU; i++) pos.push(...pt(i / NU * 2 - 1, j / NV).toArray());
  for (let j = 0; j < NV; j++) for (let i = 0; i < NU; i++) { const a = j * (NU + 1) + i, b = a + 1, c = a + NU + 1, d = c + 1; idx.push(a, c, b, b, c, d); }
  const geo = new THREE.BufferGeometry(); geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3)); geo.setIndex(idx); geo.computeVertexNormals();
  const edge = [];
  for (let i = 0; i <= 20; i++) edge.push(pt(i / 10 - 1, 0, 0.004));
  for (let j = 1; j <= 10; j++) edge.push(pt(1, j / 10, 0.004));
  for (let i = 19; i >= 0; i--) edge.push(pt(i / 10 - 1, 1, 0.004));
  for (let j = 9; j >= 1; j--) edge.push(pt(-1, j / 10, 0.004));
  const seam = []; for (let j = 0; j <= 10; j++) seam.push(pt(0, j / 10, 0.006));
  const g = new THREE.Group();
  g.add(r.felt(geo, MASK, 0.005, 260), r.felt(tube(edge, 0.012, true, 120), MASK_EDGE, 0.005, 220), r.felt(tube(seam, 0.007, false, 20), MASK_EDGE, 0.004, 220));
  const loops = [-1, 1].map(s => [pt(s, 0.12), r.at(s * (MASK_W + 0.32), MASK_TOP - 0.02, out * 0.9), r.at(s * (MASK_W + 0.62), -0.22, out * 0.7),
    r.at(s * (MASK_W + 0.36), MASK_BOT + 0.04, out * 0.9), pt(s, 0.88)]);
  return { g, loops, centre: pt(0, 0.5), corner: pt(1, 0.55) };
}
function setupDust(r) {
  const m = maskShape(r), pivot = new THREE.Group(), loops = new THREE.Group(), hand = paw(r), cloud = [0, 1, 2, 3].map(() => ball(r, 0.065, DUST, 0.025, 110));
  m.g.position.copy(m.centre).negate(); pivot.add(m.g);
  r.model.add(pivot, loops, hand, ...cloud);
  return { ...m, pivot, loopHolder: loops, hand, cloud, loopK: -1 };
}
function placeMask(r, s, { pos = [0, 0, 0], rot = 0, tip = 0, scale = 1, puff = 1, loop = 1, hand = 0 }) {
  s.pivot.visible = scale > 0.01;
  s.pivot.position.copy(s.centre).add(new V3(...pos)); s.pivot.rotation.set(tip, 0, rot);
  s.pivot.scale.set(scale, scale, scale * puff);
  if (loop !== s.loopK) {   // draw the first `loop` share of each ear loop's path
    s.loopK = loop;
    refill(s.loopHolder, ...(loop > 0.02 ? s.loops.map(pts => {
      const part = new THREE.CatmullRomCurve3(pts).getPoints(40).slice(0, Math.max(2, Math.round(40 * loop) + 1));
      return r.felt(tube(part, 0.011, false, 30), LOOP, 0.005, 200);
    }) : []));
  }
  s.pivot.updateMatrix();
  const grip = s.corner.clone().sub(s.centre).applyMatrix4(s.pivot.matrix).add(new V3(0.05, -0.03, 0.04));
  reach(r, s.hand, 1, grip, hand);
}
/** The cough: dust balls burst from the mouth, roll forward and down, then shrink. t = frames since the cough. */
function coughCloud(r, s, t) {
  const m = r.at(0, r.face.y - 0.18, r.fur);
  s.cloud.forEach((b, i) => {
    const u = (t - i * 0.5) / 6, k = u < 0 || u > 1 ? 0 : Math.sin(Math.PI * Math.sqrt(u)) * (1 - i * 0.12);
    b.visible = k > 0.03; b.scale.setScalar(Math.max(k, 1e-3) * (0.8 + u));
    const a = (i - 1.5) * 0.35;   // a fan of puffs to the pet's lower left (viewer's right), out past the cheek
    b.position.copy(m).add(new V3(0.08 + u * 0.5 + Math.sin(a) * u * 0.1, -0.02 + Math.cos(a + 1.2) * u * 0.2 - u * u * 0.06, 0.16 + u * 0.1));
  });
}

// ---------- the actions (12 fps) ----------
export const ACTIONS = {
  // squints up at the sun, winds down, a big happy stretch with sparkles, settles, puts on sunglasses (glint)
  "sun-act": { frames: 24, setup: setupSun,
    body: f => ({
      sq: kf(f, [[0, 1], [3, 1.01], [6, 0.88, "o"], [9, 1.15, "b"], [12, 1.12], [14, 0.92, "i"], [16, 1.03], [18, 0.97, "o"], [20, 1], [23, 1]]) + spring(f, 19, 0.03),
      pitch: kf(f, [[0, 0], [3, -6], [6, -2], [9, -10, "b"], [12, -9], [15, 0], [19, 2], [21, -3], [23, -3]]),
      roll: kf(f, [[9, 0], [10, -3], [11, 3], [12, -2], [13, 0]]),
      y: kf(f, [[7, 0], [9, 0.05, "o"], [12, 0.04], [14, 0, "i"]]),
      eye: kf(f, [[1, 1], [3, 0.35], [6, 0.35]]), happy: f >= 8 && f <= 15,
    }),
    props(f, s, r) {
      const t = kf(f, [[13, 0], [17, 1, "b"]]);
      placeShades(r, s, { pos: kf(f, [[13, [0.36, -0.36, 0.28]], [16, [0, 0.04, 0.05], "o"], [17, [0, 0.03, 0]], [19, [0, -0.012, 0]], [20, [0, 0, 0]]]),
        rot: kf(f, [[13, -0.7], [16, 0.08, "o"], [18, -0.03], [19, 0]]), scale: f < 13 ? 0 : kf(f, [[13, 0.6], [16, 1]]),
        glint: kf(f, [[19, 0], [21, 1.2, "b"], [23, 0, "i"]]) });
      reach(r, s.hand, 1, s.pivot.position.clone().add(new V3(0.27, -0.08, 0.1)), f <= 18 ? t : kf(f, [[18, 1], [21, 0, "i"]]));
      sparkle(s, f, [8, 9.5, 11]);
    } },
  "sun-loop": { frames: 8, loop: true, setup: setupSun,
    body: f => ({ sq: 1 + 0.015 * cyc(f, 8, Math.PI / 2), roll: 3 * cyc(f, 8), pitch: -3, turn: 2 * cyc(f, 8) }),
    props(f, s, r) { placeShades(r, s, {}); s.hand.visible = false; sparkle(s, -10, [0, 0, 0]); } },

  // wilts in the heat (flushed, half-lidded, a sweat drop), pulls out a hand fan and fans: relief
  "hot-act": { frames: 26, setup: setupHot,
    body: f => ({
      sq: kf(f, [[0, 1], [6, 0.91], [9, 0.9], [11, 0.95, "o"], [13, 0.94], [20, 0.97], [25, 0.97]]),
      roll: kf(f, [[0, 0], [6, -4], [10, -4], [13, 1, "b"], [16, 0]]) + (f >= 14 ? 1.2 * cyc(f - 14, 6) : 0),
      pitch: kf(f, [[0, 0], [6, 5], [11, 4], [14, 0]]),
      eye: kf(f, [[0, 1], [5, 0.45], [17, 0.45], [18, 1]]), happy: f >= 18 && f <= 23,
      ear: kf(f, [[0, 0], [6, -0.4], [12, -0.4], [16, 0]]) + (f >= 14 ? 0.25 * cyc(f - 14, 6, 1) : 0),
    }),
    props(f, s, r) {
      placeHot(r, s, { flush: kf(f, [[0, 0], [7, 1]]), drop: kf(f, [[4, 0], [7, 1, "b"]]), dropSlide: kf(f, [[7, 0], [15, 1, "i"]]),
        fanT: kf(f, [[8, 0], [10, 0.55, "o"], [11, 0.5], [14, 1, "b"]]),
        fanRot: f < 14 ? kf(f, [[10, 0], [11, 0.3], [14, 0]]) : 0.7 * cyc(f - 14, 6) });
    } },
  "hot-loop": { frames: 6, loop: true, setup: setupHot,
    body: f => ({ sq: 0.97 + 0.01 * cyc(f, 6, Math.PI / 2), roll: 1.2 * cyc(f, 6), eye: 1, ear: 0.25 * cyc(f, 6, 1) }),
    props(f, s, r) { placeHot(r, s, { flush: 1, drop: 1, dropSlide: 1, fanT: 1, fanRot: 0.7 * cyc(f, 6) }); } },

  // a sudden shiver with a breath puff, the knit hat drops on, both paws pull it down, cosy, one last little shiver
  "cold-act": { frames: 24, setup: setupCold,
    body: f => ({
      sq: kf(f, [[0, 1], [2, 0.92, "o"], [9, 0.93], [12, 0.97], [13, 0.86, "o"], [15, 1.02], [17, 0.97], [19, 0.99]]) + spring(f, 19, 0.02),
      sx: f >= 1 && f <= 9 ? 1.04 + shake(f, 0.015) : undefined, x: f >= 1 && f <= 9 ? shake(f, 0.025) : 0,
      roll: (f >= 1 && f <= 9 ? shake(f, 3.6 * (1 - (f - 1) / 12)) : 0) + (f >= 20 && f <= 23 ? shake(f, 1.4) : 0),
      eye: kf(f, [[0, 1], [1, 0.25, "o"], [9, 0.3], [11, 1], [12, 1], [13, 0.12], [15, 0.15]]), happy: f >= 15 && f <= 19,
      ear: f >= 1 && f <= 9 ? -0.35 + shake(f, 0.15) : spring(f, 13, -0.45),
    }),
    props(f, s, r) {
      placeHat(r, s, { drop: kf(f, [[9, 0.55], [13, 0, "i"], [15, -0.035, "o"], [17, 0]]) + (f < 9 ? 3 : 0),
        tilt: kf(f, [[9, 0.35], [13, 0.05, "i"], [15, -0.06], [17, 0.02], [19, 0]]),
        squash: kf(f, [[12, 1], [13, 0.82, "o"], [15, 1.05], [17, 1]]),
        pull: kf(f, [[12, 0], [14, 1, "o"], [17, 1], [19, 0, "i"]]), pom: spring(f, 13, 0.5) + (f >= 20 && f <= 23 ? shake(f, 0.15) : 0) });
      breath(r, s, f - 3, 1);
    } },
  "cold-loop": { frames: 6, loop: true, setup: setupCold,
    body: f => ({ sq: 0.985, roll: shake(f, 1.3), x: shake(f, 0.01), sx: 1.01 + shake(f, 0.006), eye: 1, ear: -0.12 + shake(f, 0.06) }),
    props(f, s, r) { placeHat(r, s, { drop: 0, pom: shake(f, 0.12) }); breath(r, s, f, 1); } },

  // the scarf whips round the neck (body turns with it), the tail drops and swings, cosy squeeze,
  // looks up, a snowflake drifts down onto the nose, a happy hop, the flake melts
  "snow-act": { frames: 26, setup: setupScarf,
    body: f => ({
      sq: kf(f, [[0, 1], [2, 0.93, "o"], [4, 1.02], [11, 1], [13, 0.92, "o"], [15, 1.02], [16, 1], [20, 1], [21, 0.9, "o"], [23, 1.06, "b"], [25, 1]]),
      turn: kf(f, [[2, 0], [6, 12], [9, -8], [11, 3], [13, 0]]),
      roll: kf(f, [[12, 0], [13, 3], [14, -3], [15, 1.5], [16, 0]]),
      pitch: kf(f, [[15, 0], [18, -12, "s"], [21, -11], [23, -4], [25, -2]]),
      y: kf(f, [[21, 0], [23, 0.05, "o"], [25, 0, "i"]]),
      eye: kf(f, [[0, 1], [2, 0.4, "o"], [4, 1]]), happy: (f >= 12 && f <= 15) || (f >= 22 && f <= 24),
      ear: spring(f, 3, 0.3) + spring(f, 13, 0.3) + spring(f, 21, -0.35),
    }),
    props(f, s, r) {
      const wrap = kf(f, [[3, 0], [11, 1, "s"]]);
      placeScarf(r, s, { wrap, hang: kf(f, [[10, 0], [12, 1, "b"]]), swing: f < 11 ? 0 : 1.3 * Math.exp(-0.3 * (f - 11)) * Math.cos(1.4 * (f - 11)) });
      const nose = r.at(0.04, r.face.y - 0.08, r.fur + 0.04);
      const fall = kf(f, [[13, 0], [22, 1, "l"]]);
      const p = f <= 22 ? new V3(lerp(0.35, nose.x, fall) + 0.08 * Math.sin(fall * 7), lerp(0.85, nose.y, fall), lerp(0.4, nose.z, fall)) : nose;
      placeFlake(s.flakes[0], p, f < 13 ? 0 : kf(f, [[22, 1], [23, 1.15, "o"], [25, 0, "i"]]), fall * 3);
      placeFlake(s.flakes[1], p, 0, 0);
    } },
  "snow-loop": { frames: 8, loop: true, setup: setupScarf,
    body: f => ({ sq: 1 + 0.015 * cyc(f, 8, Math.PI / 2), roll: 1.5 * cyc(f, 8), pitch: -2, ear: 0.1 * cyc(f, 8, 1) }),
    props(f, s, r) {
      placeScarf(r, s, { wrap: 1, hang: 1, swing: 0.8 * cyc(f, 8, -0.8) });
      const u = f / 8;   // one flake drifting down beside the pet, fading in and out so the loop has no seam
      placeFlake(s.flakes[0], new V3(-0.62 + 0.05 * Math.sin(u * TAU), lerp(0.62, -0.05, u), 0.25), 1.05 * Math.sin(Math.PI * (u + 0.5 / 8)), u * 2.5);
      placeFlake(s.flakes[1], null, 0, 0);
    } },

  // a drop lands on its head (flinch), the folded umbrella comes round from behind, pops open (overshoot),
  // the pet relaxes underneath; drops bounce off the canopy
  "rain-act": { frames: 26, setup: setupRain,
    body: f => ({
      sq: kf(f, [[0, 1], [3, 1], [4, 0.86, "o"], [6, 1.03], [8, 1], [12, 1], [13, 0.9, "o"], [15, 1.05], [17, 0.97], [20, 0.96], [25, 0.98]]),
      roll: kf(f, [[3, 0], [4, 2], [6, -1], [7, 0], [15, 0], [16, -3], [17, 2.5], [18, -1], [19, 0]]),
      pitch: kf(f, [[4, 0], [6, -5], [9, -6], [12, 0], [25, 0]]),
      eye: kf(f, [[3, 1], [4, 0.12, "o"], [6, 0.12], [7, 1]]), happy: f >= 16 && f <= 21,
      ear: spring(f, 4, -0.5) + spring(f, 13, 0.35),
    }),
    props(f, s, r) {
      placeUmbrella(r, s, { show: f >= 6,
        yaw: kf(f, [[6, 2.6], [10, 0.98, "o"]]), pitch: kf(f, [[6, -0.05], [9, -0.42], [11, UMB.pitch]]),
        rot: kf(f, [[6, -0.6], [10, 0.32, "o"], [12, 0.2], [14, 0.27], [15, 0.24]]) + spring(f, 15, 0.03),
        open: kf(f, [[11, 0], [13, 1.15, "o"], [14, 0.93], [15, 1.03], [16, 1]]) });
      const head = r.at(0.15, 1.2, r.fur);
      dropAt(s.drops[0], f, 3, head, -1);
      dropAt(s.drops[1], f, 3, head, 1);
      if (f >= 6) { dropAt(s.drops[2], f, 18, canopyPoint(s, -0.4), -1); dropAt(s.drops[3], f, 22, canopyPoint(s, 0.45), 1); }
      else s.drops[2].visible = s.drops[3].visible = false;
      if (f <= 3) s.drops[1].visible = false;   // one drop falls, two bounce off
    } },
  "rain-loop": { frames: 8, loop: true, setup: setupRain,
    body: f => ({ sq: 0.98 + 0.012 * cyc(f, 8, Math.PI / 2), roll: 1.2 * cyc(f, 8), ear: 0.08 * cyc(f, 8, 1) }),
    props(f, s, r) {
      placeUmbrella(r, s, { ...UMB, rot: 0.24 + 0.04 * cyc(f, 8), open: 1 });
      dropAt(s.drops[0], f, 3, canopyPoint(s, -0.35), -1);
      s.drops[1].visible = s.drops[2].visible = s.drops[3].visible = false;
    } },

  // squints, breathes in, one cough (dust puffs out), the mask comes up in a paw face-on, presses on,
  // the ear loops go round, two pleased nods
  "dust-act": { frames: 24, setup: setupDust,
    body: f => ({
      sq: kf(f, [[0, 1], [2, 1], [5, 1.07], [6, 0.85, "o"], [8, 1.03], [10, 1], [14, 1], [15, 0.93, "o"], [17, 1.02], [18, 1]]),
      pitch: kf(f, [[2, 0], [5, -8], [6, 10, "o"], [8, -2], [10, 0], [18, 0], [19.5, 8], [21, 0], [22.5, 6], [23.5, 0]]),
      roll: kf(f, [[5, 0], [6, -2], [8, 1], [10, 0]]),
      eye: kf(f, [[1, 1], [3, 0.45], [5, 0.5], [6, 0.1, "o"], [8, 0.2], [10, 1]]), happy: f >= 15 && f <= 22,
      ear: spring(f, 6, 0.5) + spring(f, 15, -0.3),
    }),
    props(f, s, r) {
      const t = kf(f, [[10, 0], [14, 1, "b"]]);
      placeMask(r, s, { pos: kf(f, [[10, [0.12, -0.22, 0.26]], [14, [0, 0, 0.06], "o"], [15, [0, 0, 0]]]), tip: kf(f, [[10, -0.55], [14, -0.05, "o"], [15, 0]]),
        rot: kf(f, [[10, -0.3], [14, 0.04, "o"], [15, 0]]), scale: f < 10 ? 0 : kf(f, [[10, 0.8], [14, 1]]),
        puff: kf(f, [[14, 1], [15, 0.8, "o"], [17, 1]]), loop: kf(f, [[15, 0], [18, 1]]),
        hand: f <= 15 ? t : kf(f, [[15, 1], [18, 0, "i"]]) });
      coughCloud(r, s, f - 6);
    } },
  "dust-loop": { frames: 8, loop: true, setup: setupDust,
    body: f => ({ sq: 1 + 0.02 * cyc(f, 8, Math.PI / 2), roll: 0.8 * cyc(f, 8), happy: false }),
    props(f, s, r) { placeMask(r, s, { puff: 1 + 0.07 * Math.max(0, cyc(f, 8, Math.PI / 2)) }); coughCloud(r, s, -10); } },
};
