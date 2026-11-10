// Our own creatures, built from primitives in three.js (no images, no downloads), to stand on a room floor in AR.
// makeCreature(spec) -> { object3d, update(dt), setPose("idle" | "hop" | "caught") }
// object3d's origin is at the feet, the face looks along +z (anchor.lookAt(camera) turns it to you), and it is
// spec.height metres tall (default 0.35). It carries its own soft lights and a contact shadow disc.
// Three looks (spec.style): "mochi" = matte toon + thin line, "fuzz" = shell-texture felt, "paper" = low-poly paper.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";

const V3 = THREE.Vector3;
const INK = 0x3b3331;                                  // eyes, mouth, line: warm charcoal, never black
const LIGHT_DIR = new V3(0.45, 1, 0.75).normalize();   // soft key from above-front, same for every look

// ---------- shapes: a unit sphere direction d -> a point on the part's surface ----------
function blob({ r: [a, b, c], taper = 0, flat = 0, star = 0 }) {
  return d => {
    let { x, y, z } = d;
    if (star) {   // stubby points around the front outline (paper star)
      const m = 1 + star * Math.pow(Math.max(0, Math.cos(5 * Math.atan2(x, y))), 3) * (1 - z * z);
      x *= m; y *= m;
    }
    const k = 1 - taper * y;
    return new V3(a * x * k, b * y * (y < 0 ? 1 - flat : 1), c * z * k);
  };
}
function tangents(d) {
  const t1 = new V3().crossVectors(d, Math.abs(d.y) > 0.95 ? new V3(1, 0, 0) : new V3(0, 1, 0)).normalize();
  return [t1, new V3().crossVectors(d, t1)];
}
function normalAt(fn, d) {   // numeric: the shapes are only continuous, not analytic
  const [t1, t2] = tangents(d), e = 1e-3;
  const du = fn(d.clone().addScaledVector(t1, e).normalize()).sub(fn(d.clone().addScaledVector(t1, -e).normalize()));
  const dv = fn(d.clone().addScaledVector(t2, e).normalize()).sub(fn(d.clone().addScaledVector(t2, -e).normalize()));
  const n = new V3().crossVectors(du, dv).normalize();
  return n.dot(d) < 0 ? n.negate() : n;
}
function shapeGeo(fn, ws, hs) {
  const g = new THREE.SphereGeometry(1, ws, hs), p = g.attributes.position, n = g.attributes.normal, d = new V3();
  for (let i = 0; i < p.count; i++) {
    d.fromBufferAttribute(p, i).normalize();
    const q = fn(d), m = normalAt(fn, d);
    p.setXYZ(i, q.x, q.y, q.z); n.setXYZ(i, m.x, m.y, m.z);
  }
  g.computeBoundingSphere();
  return g;
}
const dirOf = (yaw, pitch) => new V3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
/** A point and normal on a part (fn + its centre), at yaw/pitch radians from the front. */
function surf(part, yaw, pitch) {
  const d = dirOf(yaw, pitch);
  return { p: part.fn(d).add(part.c), n: normalAt(part.fn, d) };
}

// ---------- the three looks ----------
function toonRamp() {
  const t = new THREE.DataTexture(new Uint8Array([168, 168, 168, 255, 222, 222, 222, 255, 255, 255, 255, 255]), 3, 1);
  t.minFilter = t.magFilter = THREE.NearestFilter; t.needsUpdate = true;
  return t;
}
let RAMP = null, PAPER = null;
function paperTex() {   // faint fibres so a flat facet reads as paper, not plastic
  const c = document.createElement("canvas"); c.width = c.height = 128;
  const g = c.getContext("2d"); g.fillStyle = "#fff"; g.fillRect(0, 0, 128, 128);
  let s = 7; const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 900; i++) { const v = 228 + rnd() * 27 | 0; g.fillStyle = `rgb(${v},${v},${v})`; g.fillRect(rnd() * 128, rnd() * 128, 1 + rnd() * 3, 1); }
  const t = new THREE.CanvasTexture(c); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
const OUTLINE_V = `uniform float uW; void main(){ vec3 p = position + normal * uW; gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.); }`;
const OUTLINE_F = `uniform vec3 uC; void main(){ gl_FragColor = vec4(uC, 1.);\n#include <colorspace_fragment>\n}`;

// shell fur: layer 0 is the skin, layers 1..N are the same surface pushed out; strands are hashed 3D cells
const FUR_V = `uniform float uL, uLen; varying vec3 vP; varying vec3 vN;
void main(){
  vec3 p = position + normal * uLen * uL;
  p.y -= uLen * .3 * uL * uL;
  vP = position; vN = normalize(mat3(modelMatrix) * normal);
  gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.);
}`;
const FUR_F = `uniform float uL, uDen; uniform vec3 uC, uLight; varying vec3 vP; varying vec3 vN;
float h(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
// two lattices at odd rotations, each strand jittered inside its cell: an axis-aligned grid sliced by a sphere shows rings
const mat3 R1 = mat3(.80, .36, -.48, -.48, .86, -.17, .36, .36, .86), R2 = mat3(.54, -.70, .47, .84, .46, -.29, -.01, .55, .83);
float strand(vec3 q){ vec3 c = floor(q), f = fract(q) - .5; float r = h(c), len = .55 + .45 * r; f -= (vec3(h(c + 1.7), h(c + 4.3), h(c + 9.1)) - .5) * .5; return uL > len ? 0. : step(length(f), .62 * (1. - .8 * uL / len)); }
void main(){
  if (uL > 0. && strand(R1 * vP * uDen) + strand(R2 * vP * uDen * 1.31 + 3.1) < .5) discard;
  float w = dot(normalize(vN), uLight) * .5 + .5;
  float v = h(floor(R1 * vP * uDen)) * .07;
  vec3 col = uC * (.6 + .46 * w) * (mix(.7, 1.05, uL) - v);
  gl_FragColor = vec4(col, 1.);
#include <colorspace_fragment>
}`;
const SHELLS = 18;

function makeLook(style) {
  const mats = new Map();
  const memo = (key, make) => mats.get(key) || (mats.set(key, make()), mats.get(key));
  if (style === "mochi") {
    RAMP ||= toonRamp();
    return {
      segs: [48, 32], lift: 0.002,
      solid: col => memo(col, () => new THREE.MeshToonMaterial({ color: col, gradientMap: RAMP })),
      part(geo, col, o = {}) {
        const g = new THREE.Group(), m = new THREE.Mesh(geo, this.solid(col));
        g.add(m);
        if (o.line !== false) g.add(new THREE.Mesh(geo, memo("line", () => new THREE.ShaderMaterial({ vertexShader: OUTLINE_V, fragmentShader: OUTLINE_F, side: THREE.BackSide, uniforms: { uW: { value: 0.011 }, uC: { value: new THREE.Color(INK) } } }))));
        return g;
      },
    };
  }
  if (style === "jelly") {   // glossy see-through candy, no line
    return {
      segs: [48, 32], lift: 0.003,
      solid: col => memo(col, () => new THREE.MeshPhysicalMaterial({ color: col, roughness: 0.15, clearcoat: 1, clearcoatRoughness: 0.08, sheen: 0.6, transparent: true, opacity: 0.84 })),
      part(geo, col) { return new THREE.Mesh(geo, this.solid(col)); },
    };
  }
  if (style === "fuzz") {
    return {
      segs: [48, 32], lift: 0.068,
      solid: col => memo(col, () => new THREE.MeshLambertMaterial({ color: col })),
      part(geo, col, o = {}) {
        if (!o.fur) return new THREE.Mesh(geo, this.solid(col));
        const g = new THREE.Group(), len = o.len ?? 0.065;
        for (let i = 0; i <= SHELLS; i++) {
          g.add(new THREE.Mesh(geo, memo(col + ":" + i + ":" + len, () => new THREE.ShaderMaterial({ vertexShader: FUR_V, fragmentShader: FUR_F,
            uniforms: { uL: { value: i / SHELLS }, uLen: { value: len }, uDen: { value: 90 }, uC: { value: new THREE.Color(col) }, uLight: { value: LIGHT_DIR } } }))));
        }
        return g;
      },
    };
  }
  PAPER ||= paperTex();
  return {   // paper
    segs: [10, 7], lift: 0.004,
    solid: col => memo(col, () => new THREE.MeshLambertMaterial({ color: col, map: PAPER, flatShading: true })),
    part(geo, col, o = {}) {
      const g = new THREE.Group();
      g.add(new THREE.Mesh(geo, this.solid(col)));
      const crease = new THREE.Color(col).multiplyScalar(0.72);
      if (o.line !== false) g.add(new THREE.LineSegments(new THREE.EdgesGeometry(geo, 8), memo("e" + col, () => new THREE.LineBasicMaterial({ color: crease }))));
      return g;
    },
  };
}

// ---------- shadow ----------
let SHADOW = null;
function shadowTex() {
  const sc = document.createElement("canvas"); sc.width = sc.height = 128;
  const g = sc.getContext("2d"), grd = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  grd.addColorStop(0, "rgba(0,0,0,.62)"); grd.addColorStop(0.35, "rgba(0,0,0,.42)"); grd.addColorStop(0.7, "rgba(0,0,0,.12)"); grd.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = grd; g.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(sc);
}

// ---------- build ----------
function build(spec) {
  const look = makeLook(spec.style), [WS, HS] = look.segs;
  const model = new THREE.Group(), eyes = new THREE.Group(), happy = new THREE.Group(), wob = [];
  const fur = spec.style === "fuzz";
  const P = (def, col, o = {}) => {   // one blob part; returns {fn, c, obj}
    const fn = blob(def), c = new V3(...(def.c || [0, 0, 0]));
    const obj = look.part(shapeGeo(fn, o.ws || WS, o.hs || HS), col, o);
    obj.position.copy(c); if (def.rot) obj.rotation.set(...def.rot);
    (o.parent || model).add(obj);
    return { fn, c, obj };
  };
  const bodies = (spec.bodies || [spec.body]).map(b => P(b, b.color || spec.color, { fur: true, len: b.len }));
  const head = bodies[0];
  const flat = (geo, col, at, opts = {}) => {   // a face mark lying on the head surface
    const { p, n } = surf(head, at[0], at[1]);
    const m = new THREE.Mesh(geo, opts.mat || look.solid(col));
    m.position.copy(p).addScaledVector(n, look.lift + (opts.out || 0));
    m.quaternion.setFromUnitVectors(new V3(0, 0, 1), n);
    if (opts.spin) m.rotateZ(opts.spin);
    (opts.parent || model).add(m);
    return m;
  };
  const inkMat = new THREE.MeshBasicMaterial({ color: INK });

  // face: dot eyes, closed "^ ^" eyes for the caught pose, tiny mouth, blush
  const f = spec.face, er = f.r || 0.045;
  for (const s of [-1, 1]) {
    const eye = spec.style === "paper" ? new THREE.CircleGeometry(er, 6) : new THREE.SphereGeometry(er, 16, 12).scale(1, 1.18, 0.45);
    flat(eye, INK, [s * f.x, f.y], { mat: inkMat, parent: eyes });
    if (f.shine) flat(new THREE.CircleGeometry(er * 0.38, 12), 0, [s * f.x - er * 0.5, f.y + er * 0.7],
      { mat: new THREE.MeshBasicMaterial({ color: 0xffffff }), out: er * 0.5, parent: eyes });
    flat(new THREE.TorusGeometry(er * 1.05, er * 0.3, 6, 14, Math.PI), INK, [s * f.x, f.y - 0.02], { mat: inkMat, parent: happy });
    if (f.blush) {
      const b = flat(new THREE.CircleGeometry(er * 1.5, spec.style === "paper" ? 6 : 20), 0, [s * (f.x + 0.2), f.y - 0.17],
        { mat: new THREE.MeshBasicMaterial({ color: f.blush, transparent: true, opacity: 0.55, depthWrite: false }), out: 0.002 });
      b.scale.y = 0.62;
    }
  }
  if (f.mouth !== false) flat(new THREE.TorusGeometry(er * 0.55, er * 0.2, 6, 12, Math.PI), INK, [0, f.y - 0.13], { mat: inkMat, spin: Math.PI });
  model.add(eyes, happy); happy.visible = false;

  // ears, feet, extras
  const at = (part, yaw, pitch, out = 0) => { const { p, n } = surf(part, yaw, pitch); return p.addScaledVector(n, out); };
  for (const e of spec.parts || []) {
    for (const s of e.mirror ? [-1, 1] : [1]) {
      const pos = e.on ? at(bodies[e.on === "head" ? 0 : e.on], s * e.on_at[0], e.on_at[1], e.out || 0) : new V3(s * e.c[0], e.c[1], e.c[2]);
      const rot = (e.rot || [0, 0, 0]).map((v, i) => i === 2 || i === 1 ? v * s : v);
      const holder = new THREE.Group(); holder.position.copy(pos); holder.rotation.set(...rot); model.add(holder);
      if (e.kind === "cone") {
        const geo = new THREE.ConeGeometry(e.r, e.h, spec.style === "paper" ? 4 : 24, 1).translate(0, e.h / 2, 0);
        const m = look.part(geo, e.color || spec.color, { fur: e.fur, len: 0.05, line: e.line }); holder.add(m);
        if (e.inner) { const g2 = new THREE.ConeGeometry(e.r * 0.55, e.h * 0.7, spec.style === "paper" ? 4 : 18, 1).translate(0, e.h * 0.35, 0); const m2 = new THREE.Mesh(g2, look.solid(e.inner)); m2.position.z = e.r * 0.42; m2.scale.z = 0.3; holder.add(m2); }
      } else if (e.kind === "stick") {
        const geo = new THREE.CylinderGeometry(0.012, 0.016, e.h, 8).translate(0, e.h / 2, 0);
        holder.add(new THREE.Mesh(geo, look.solid(e.color || INK)));
        P({ r: [e.tip, e.tip, e.tip], c: [0, e.h, 0] }, e.tipColor || spec.color, { parent: holder, line: spec.style !== "paper" ? true : undefined });
      } else if (e.kind === "leaf") {
        P({ r: [0.012, e.h / 2, 0.012], c: [0, e.h / 2, 0] }, e.color, { parent: holder, line: false, ws: 8, hs: 6 });
        for (const k of [-1, 1]) P({ r: [e.w, 0.025, e.w * 0.55], c: [k * e.w * 0.9, e.h, 0], rot: [0, 0, k * 0.5] }, e.color, { parent: holder, ws: 16, hs: 10 });
      } else {   // "blob"
        P({ ...e, c: e.off || [0, 0, 0], rot: undefined }, e.color || spec.color, { parent: holder, fur: e.fur, len: e.len });
      }
      if (e.wob) wob.push({ o: holder, base: holder.rotation.clone(), k: e.wob * s });
    }
  }

  // stand it on y = 0 and scale to spec.height
  const box = new THREE.Box3().setFromObject(model), size = box.getSize(new V3());
  const k = (spec.height || 0.35) / size.y;
  model.position.y = -box.min.y; model.position.x = -(box.min.x + box.max.x) / 2;
  const squash = new THREE.Group(); squash.add(model); squash.scale.setScalar(k);
  const hopper = new THREE.Group(); hopper.add(squash);
  const root = new THREE.Group(); root.add(hopper);

  SHADOW ||= shadowTex();
  const shadow = new THREE.Mesh(new THREE.CircleGeometry(1, 40), new THREE.MeshBasicMaterial({ map: SHADOW, transparent: true, depthWrite: false }));
  shadow.rotation.x = -Math.PI / 2; shadow.position.y = 0.002; shadow.renderOrder = -1;
  const foot = Math.max(size.x, size.z) * k * 0.82;
  shadow.scale.set(foot, foot * 0.9, 1);
  root.add(shadow);

  // own light: a warm room-ish fill and a soft key (the game can recolour these from the camera frame)
  const hemi = new THREE.HemisphereLight(0xfffaf2, 0x8a7d70, 1.9), key = new THREE.DirectionalLight(0xffffff, 1.5);
  key.position.copy(LIGHT_DIR); root.add(hemi, key, key.target);
  root.userData.lights = { hemi, key };
  return { root, hopper, squash, k, model, eyes, happy, shadow, foot, wob, h: spec.height || 0.35 };
}

export function makeCreature(spec) {
  const B = build(spec);
  let t = 0, pose = "idle", poseT = 0, blinkAt = 1.5 + Math.random() * 2, vy = 0;
  const ease = x => x * x * (3 - 2 * x);
  function setPose(name) { pose = name; poseT = 0; B.happy.visible = name === "caught"; B.eyes.visible = name !== "caught"; }
  function update(dt) {
    t += dt; poseT += dt;
    const br = Math.sin(t * 2.4);
    let sy = 1 + 0.035 * br, sxz = 1 - 0.018 * br, y = 0, ry = 0.18 * Math.sin(t * 0.55), rz = 0.035 * Math.sin(t * 1.3);
    if (pose === "hop") {   // anticipate (squash) -> stretch up -> fall -> land squash; 0.95 s a hop
      const p = (poseT % 0.95) / 0.95, H = 0.45;
      if (p < 0.18) { const a = Math.sin(p / 0.18 * Math.PI); sy = 1 - 0.16 * a; sxz = 1 + 0.1 * a; }
      else if (p < 0.78) { const a = (p - 0.18) / 0.6; y = 4 * a * (1 - a) * H; sy = 1 + 0.12 * (1 - a * 2) * (a < 0.5 ? 1 : -0.4); sxz = 1 - (sy - 1) * 0.5; }
      else { const a = Math.sin((p - 0.78) / 0.22 * Math.PI); sy = 1 - 0.12 * a; sxz = 1 + 0.08 * a; }
      ry *= 0.4;
    } else if (pose === "caught") {   // a happy squeeze and wiggle, settling into a sway
      const d = Math.exp(-poseT * 1.6);
      sy = 1 - 0.1 * d + 0.03 * br; sxz = 1 + 0.07 * d;
      rz = 0.22 * Math.sin(poseT * 13) * d + 0.07 * Math.sin(t * 2.2);
      y = Math.max(0, Math.sin(Math.min(1, poseT / 0.4) * Math.PI)) * 0.18;
      ry = 0;
    }
    const prevY = B.hopper.position.y;
    B.hopper.position.y = y * B.h;
    vy = vy * 0.7 + 0.3 * (B.hopper.position.y - prevY) / Math.max(dt, 1e-3) / B.h;
    B.squash.scale.set(B.k * sxz, B.k * sy, B.k * sxz);
    B.model.rotation.y = ry; B.squash.rotation.z = rz;
    for (const w of B.wob) w.o.rotation.z = w.base.z + w.k * Math.max(-0.5, Math.min(0.5, -vy * 0.35));
    // blink: eyes squash to a line for 0.13 s every 2-5 s
    if (t > blinkAt) { const b = t - blinkAt; B.eyes.scale.y = b < 0.13 ? 0.12 : 1; if (b >= 0.13) blinkAt = t + 2 + Math.random() * 3; }
    const lift = Math.min(1, y * 2.2);
    B.shadow.scale.set(B.foot * (1 - 0.35 * lift) * sxz, B.foot * 0.9 * (1 - 0.35 * lift) * sxz, 1);
    B.shadow.material.opacity = 1 - 0.55 * lift;
  }
  return { object3d: B.root, update, setPose };
}

// ---------- the dex: 3 looks x 3 creatures ----------
// angles: face.x = eye yaw from the front, face.y = eye pitch (radians); parts on a body use on_at [yaw, pitch]
export const SPECS = [
  // A. 말랑 — matte toon clay with a thin warm line
  { id: "mongsil", name: "몽실", style: "mochi", color: "#f8f4ec", height: 0.3,
    body: { r: [0.5, 0.42, 0.46], taper: 0.12, flat: 0.35 },
    face: { x: 0.3, y: 0.08, r: 0.042, blush: "#f3b3aa" },
    parts: [
      { kind: "leaf", color: "#a9cf95", h: 0.12, w: 0.07, on: "head", on_at: [0, 1.5], out: -0.01, wob: 0.5 },
      { kind: "blob", r: [0.1, 0.06, 0.12], c: [0.2, -0.25, 0.18], mirror: true },
    ] },
  { id: "bori", name: "보리", style: "mochi", color: "#f6e6c3", height: 0.36,
    body: { r: [0.36, 0.52, 0.34], taper: 0.06, flat: 0.3 },
    face: { x: 0.34, y: 0.25, r: 0.04, blush: "#f0ab95" },
    parts: [
      // long ears hang from the crown down the sides and flap on a hop
      { kind: "blob", r: [0.07, 0.25, 0.055], off: [0, -0.2, 0], on: "head", on_at: [1.25, 0.95], mirror: true, rot: [0, 0, 0.42], wob: 1.0, out: 0.0 },
      { kind: "blob", r: [0.09, 0.055, 0.11], c: [0.15, -0.34, 0.15], mirror: true },
      { kind: "blob", r: [0.08, 0.08, 0.08], on: "head", on_at: [Math.PI, -0.35], out: -0.02 },
    ] },
  { id: "chadol", name: "차돌", style: "mochi", color: "#e6e2f0", height: 0.2,
    body: { r: [0.56, 0.31, 0.46], taper: 0.0, flat: 0.45 },
    face: { x: 0.36, y: 0.16, r: 0.04, blush: "#ecb2c4" },
    parts: [
      { kind: "blob", r: [0.17, 0.045, 0.1], on: "head", on_at: [1.15, -0.45], mirror: true, rot: [0, 0.4, -0.5], wob: 0.6, out: -0.03 },
      { kind: "blob", r: [0.1, 0.06, 0.13], on: "head", on_at: [Math.PI, -0.1], rot: [0.5, 0, 0], out: -0.03 },
    ] },

  // B. 보송 — felt / pompom fur (shell texturing)
  { id: "somsom", name: "솜솜", style: "fuzz", color: "#fbf8f2", height: 0.3,
    body: { r: [0.45, 0.43, 0.43], flat: 0.2 },
    face: { x: 0.3, y: 0.06, r: 0.05, blush: "#f6b9b0" },
    parts: [
      { kind: "blob", r: [0.09, 0.055, 0.11], c: [0.18, -0.36, 0.22], mirror: true, color: "#f2cfb8" },
    ] },
  { id: "kkomul", name: "꼬물", style: "fuzz", color: "#f8dccd", height: 0.26,
    bodies: [   // head in front, the rest trails back to its left so the chain shows from the front
      { r: [0.3, 0.3, 0.3], c: [0, 0.03, 0.26] },
      { r: [0.26, 0.25, 0.26], c: [-0.26, -0.02, -0.02], color: "#f6d3c2" },
      { r: [0.21, 0.2, 0.21], c: [-0.48, -0.06, -0.26], color: "#f8dccd" },
    ],
    face: { x: 0.36, y: 0.08, r: 0.042, blush: "#f1a596" },
    parts: [
      { kind: "stick", h: 0.2, tip: 0.04, tipColor: "#f1a596", color: "#9a7f74", on: "head", on_at: [0.35, 1.05], mirror: true, rot: [0, 0, -0.35], wob: 1.2, out: 0.0 },
      { kind: "blob", r: [0.06, 0.04, 0.07], c: [0.14, -0.25, 0.34], mirror: true, color: "#eab9a6" },
      { kind: "blob", r: [0.06, 0.04, 0.07], c: [-0.12, -0.27, 0.02], color: "#eab9a6" },
      { kind: "blob", r: [0.06, 0.04, 0.07], c: [-0.4, -0.27, -0.06], color: "#eab9a6" },
      { kind: "blob", r: [0.05, 0.035, 0.06], c: [-0.36, -0.27, -0.28], color: "#eab9a6" },
    ] },
  { id: "meonji", name: "먼지톨", style: "fuzz", color: "#dfe5ee", height: 0.34,
    body: { r: [0.38, 0.5, 0.38], taper: 0.16, flat: 0.25, len: 0.08 },
    face: { x: 0.32, y: 0.2, r: 0.05, blush: "#efb8c0" },
    parts: [
      { kind: "cone", r: 0.07, h: 0.2, fur: true, on: "head", on_at: [0.55, 1.05], mirror: true, rot: [0, 0, -0.45], wob: 0.8, out: -0.03 },
      { kind: "blob", r: [0.08, 0.05, 0.1], c: [0.15, -0.38, 0.15], mirror: true, color: "#f0c9a0" },
    ] },

  // C. 종이 — low-poly paper craft, creases as thin lines
  { id: "gaknyang", name: "각냥", style: "paper", color: "#f5efe4", height: 0.32,
    body: { r: [0.42, 0.42, 0.38], taper: 0.08, flat: 0.3 },
    face: { x: 0.3, y: 0.1, r: 0.045, blush: "#f1bcb6" },
    parts: [
      { kind: "cone", r: 0.13, h: 0.22, inner: "#f2c6c0", on: "head", on_at: [0.5, 0.9], mirror: true, rot: [0, 0, -0.35], wob: 0.4, out: -0.05 },
      { kind: "cone", r: 0.06, h: 0.38, on: "head", on_at: [Math.PI - 0.4, -0.3], rot: [-1.0, 0, 0.5], out: -0.04 },
      { kind: "blob", r: [0.1, 0.06, 0.12], c: [0.17, -0.28, 0.16], mirror: true },
    ] },
  { id: "songi", name: "송이", style: "paper", color: "#f3e3c8", height: 0.32,
    body: { r: [0.36, 0.42, 0.36], taper: 0.18, flat: 0.25 },
    face: { x: 0.3, y: -0.08, r: 0.042, blush: "#efb59e" },
    parts: [
      { kind: "blob", r: [0.43, 0.24, 0.43], flat: 0.7, on: "head", on_at: [0, 1.5], out: -0.12, color: "#c8ad8c" },
      { kind: "cone", r: 0.03, h: 0.12, on: "head", on_at: [0, 1.5], out: 0.1, color: "#a88c6c", rot: [0, 0, 0.25] },
      { kind: "blob", r: [0.09, 0.055, 0.11], c: [0.15, -0.31, 0.15], mirror: true },
    ] },
  { id: "kokkok", name: "콕콕", style: "paper", color: "#dce8f5", height: 0.3,
    body: { r: [0.4, 0.44, 0.38], taper: 0.1, flat: 0.25 },
    face: { x: 0.3, y: 0.12, r: 0.045, blush: "#f0b9c8", mouth: false },
    parts: [
      { kind: "cone", r: 0.07, h: 0.13, on: "head", on_at: [0, 0.0], out: -0.02, rot: [Math.PI / 2, 0, 0], color: "#f4dc9c" },
      { kind: "cone", r: 0.11, h: 0.3, on: "head", on_at: [1.45, -0.05], mirror: true, rot: [0.2, 0, -2.3], wob: 1.0, out: -0.06 },
      { kind: "cone", r: 0.04, h: 0.14, on: "head", on_at: [0, 1.45], mirror: true, rot: [0, 0, -0.35], out: -0.02, color: "#c7d9ee" },
      { kind: "blob", r: [0.08, 0.05, 0.1], c: [0.14, -0.31, 0.1], mirror: true, color: "#f4dc9c" },
    ] },

  // D. 새 친구들 — small, round, pale mochi with a thin line and dot eyes (2026-11-10 draft, 8 originals)
  { id: "bangul", name: "방울", style: "mochi", group: "new", color: "#e3f3ef", height: 0.3,
    body: { r: [0.42, 0.46, 0.4], taper: 0.34, flat: 0.3 },   // a water drop: narrow top
    face: { x: 0.3, y: -0.02, r: 0.042, blush: "#f2b2ad" },
    parts: [
      { kind: "cone", r: 0.07, h: 0.16, on: "head", on_at: [0, 1.5], out: -0.05, rot: [0, 0, 0.3], wob: 0.9 },
      { kind: "blob", r: [0.09, 0.055, 0.11], c: [0.16, -0.32, 0.16], mirror: true },
    ] },
  { id: "kongtteok", name: "콩떡", style: "mochi", group: "new", color: "#e4efd2", height: 0.2,
    body: { r: [0.58, 0.32, 0.42], taper: 0.0, flat: 0.5 },   // a wide flat bean cake
    face: { x: 0.3, y: 0.12, r: 0.04, blush: "#efb1a1" },
    parts: [
      { kind: "blob", r: [0.07, 0.05, 0.07], on: "head", on_at: [0.55, 1.1], out: -0.01, mirror: true, color: "#b9cf96" },
      { kind: "blob", r: [0.07, 0.05, 0.07], on: "head", on_at: [0, 1.35], out: -0.01, color: "#b9cf96" },
    ] },
  { id: "noeul", name: "노을", style: "mochi", group: "new", color: "#fbe1d2", height: 0.3,
    body: { r: [0.44, 0.44, 0.42], taper: 0.1, flat: 0.3 },
    face: { x: 0.3, y: 0.06, r: 0.042, blush: "#ee9f93" },
    parts: [
      { kind: "stick", h: 0.24, tip: 0.055, tipColor: "#f6c56e", color: "#8a6f66", on: "head", on_at: [0.2, 1.25], rot: [0, 0, -0.3], wob: 1.3 },
      { kind: "blob", r: [0.09, 0.055, 0.11], c: [0.17, -0.3, 0.17], mirror: true },
    ] },
  { id: "gureum", name: "구름", style: "mochi", group: "new", color: "#eceaf4", height: 0.26,
    bodies: [   // a cloud: one round middle, two smaller puffs at the sides
      { r: [0.4, 0.38, 0.38], c: [0, 0.04, 0] },
      { r: [0.26, 0.24, 0.26], c: [-0.4, -0.06, -0.04] },
      { r: [0.26, 0.24, 0.26], c: [0.4, -0.06, -0.04] },
    ],
    face: { x: 0.3, y: 0.1, r: 0.04, blush: "#efb5c6" },
    parts: [{ kind: "blob", r: [0.08, 0.05, 0.1], c: [0.14, -0.3, 0.16], mirror: true }] },
  { id: "byeolkong", name: "별콩", style: "mochi", group: "new", color: "#fbf1c9", height: 0.28,
    body: { r: [0.46, 0.44, 0.42], taper: 0.05, flat: 0.3, star: 0.22 },   // a soft five-point star
    face: { x: 0.3, y: 0.06, r: 0.042, blush: "#f0ab95" },
    parts: [{ kind: "blob", r: [0.09, 0.055, 0.11], c: [0.16, -0.3, 0.16], mirror: true }] },
  { id: "pengdol", name: "펭돌", style: "mochi", group: "new", color: "#dfe7f1", height: 0.32,
    body: { r: [0.4, 0.5, 0.38], taper: 0.12, flat: 0.3 },
    face: { x: 0.28, y: 0.22, r: 0.04, blush: "#f0b0b8" },
    parts: [
      { kind: "blob", r: [0.24, 0.22, 0.07], on: "head", on_at: [0, -0.55], out: -0.05, color: "#fbfaf6" },   // pale belly, below the face
      { kind: "cone", r: 0.045, h: 0.09, on: "head", on_at: [0, 0.08], out: -0.01, rot: [Math.PI / 2, 0, 0], color: "#f3c77a" },
      { kind: "blob", r: [0.05, 0.2, 0.08], on: "head", on_at: [1.5, -0.1], mirror: true, rot: [0, 0, 0.35], wob: 1.1, out: -0.02 },
      { kind: "blob", r: [0.1, 0.05, 0.12], c: [0.14, -0.4, 0.14], mirror: true, color: "#f3c77a" },
    ] },
  { id: "musun", name: "무순", style: "mochi", group: "new", color: "#fdf3f3", height: 0.34,
    body: { r: [0.42, 0.48, 0.4], taper: -0.22, flat: 0.15 },   // a radish: round top, narrow bottom
    face: { x: 0.3, y: 0.1, r: 0.04, blush: "#f2a9b5" },
    parts: [
      { kind: "leaf", color: "#a7d39a", h: 0.1, w: 0.06, on: "head", on_at: [0.25, 1.4], out: -0.01, mirror: true, rot: [0, 0, -0.35], wob: 0.6 },
      { kind: "blob", r: [0.07, 0.05, 0.09], c: [0.1, -0.36, 0.1], mirror: true },
    ] },
  { id: "malgeun", name: "말금", style: "mochi", group: "new", color: "#ece2f5", height: 0.3,
    body: { r: [0.44, 0.46, 0.42], taper: 0.08, flat: 0.3 },
    face: { x: 0.3, y: 0.05, r: 0.044, blush: "#efb1c9", shine: true },
    parts: [
      { kind: "blob", r: [0.09, 0.09, 0.09], on: "head", on_at: [0.55, 1.05], out: -0.03, mirror: true },   // three soft bumps
      { kind: "blob", r: [0.1, 0.1, 0.1], on: "head", on_at: [0, 1.4], out: -0.03 },
      { kind: "blob", r: [0.09, 0.055, 0.11], c: [0.16, -0.31, 0.16], mirror: true },
    ] },
];
