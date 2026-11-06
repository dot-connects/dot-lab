// AR test build (2026-11-05): the rear camera shows the room, a dalgona floats in it and stays put when the phone
// turns (gyro, 3DoF like Pokémon GO's plain AR mode). The real hand holds a needle, a wet swab or a hammer, picked
// from a tray by pointing at it. One WebGL pass per frame: camera frame -> candy + tool -> the hand cut back over
// them (so the fingers cover the candy and the tool's grip) -> film grain over everything.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";
import { SHAPES } from "../js/engine.js";
import { oneEuro } from "../js/camera.js";
import { makeField, score, setGroove, markCrack, walk, loosePieces } from "./fracture.js";

const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const HAND = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";
const $ = id => document.getElementById(id);
const Q = new URLSearchParams(location.search);

// Sizes in metres. A street dalgona is about 9-10 cm across and 5 mm thick. It floats at the distance the free hand
// reaches behind the phone. Phone test 11-05: at 32 cm it looked too small, at 24 cm the hand came so close it
// filled the frame and the tracker lost it; 30 cm with a big (11 cm) candy keeps both. ?dist= tunes it on the phone.
const R = 0.055, T = 0.005, DIST = +(Q.get("dist") || 0.30), TILT = -18 * Math.PI / 180;
const TEX = 512, PXMM = TEX / (2 * R * 1000);   // texture px per mm of candy
const RIMN = 72;   // rim bands: a broken piece takes the bands along its stretch of the edge
// Tuning knobs. Pinch numbers are from the 11-04 front-camera phone spike (camera.js); the rest are first guesses
// for the phone test, not measurements of real candy.
const AR = {
  LONG_FOV: +(Q.get("fov") || 66),   // degrees across the long side of a 26 mm-equivalent rear camera (~67-69)
  // pinch = thumb and index tips touching: tip-to-tip distance in hand sizes. Phone test 11-05: at 0.38 the tool
  // came out with the fingers still apart. The tracker's 3D hand was tried and read 8 cm on touching tips, so 2D.
  PINCH: +(Q.get("pinch") || 0.26), RELEASE: 1.3,   // touching tips read 0.21 on a test photo
  CONFIRM: 2, LOST_GRACE: 400, LAND_MS: 150,
  // Height of the pinched tool = how far the hand moved from the phone since the pinch, read as the hand shrinking
  // on screen ("push", fraction). Phone ?probe 11-06: jitter sd 0.9-1.2 %, a deliberate push 13-24 %. The point lands
  // at LAND and lifts below LIFT (hysteresis, ~3-5 sd clear of the jitter); past HARD it presses too hard.
  LAND: 0.06, LIFT: 0.035, HARD: 0.2, HOVER: 0.012,   // HOVER: metres above the candy right after the pinch
  POKE: 0.3,              // groove depth one needle poke adds near its point
  SCORE: 0.2,             // weakness a needle pass leaves anywhere it goes (~0.4 a pass; cracks follow a line from ~0.7)
  DWELL: 600, DWELL_GRACE: 150,
  SAFE: 22, FAST: 38,     // needle speed over the candy, mm/s: calm under SAFE, cracks over FAST
  CARVE: 0.06,            // groove depth per mm of needle travel near it (about 3 careful passes)
  NEAR: 0.06,             // groove points within this many candy radii of the tip get carved
  MELT_HOLE: 3.2,         // seconds of rubbing one spot before it melts through
  BREAK: 0.55,            // mean groove depth in a sector that lets the hammer snap it off cleanly
  // hammer = the index finger. A tap is a fast move of the fingertip down the screen (or the finger tipping toward
  // the candy, which shortens it on screen) that then stops: the stop is the hit.
  // Hammer = aim, then push, like the needle. With the rear camera looking down a tap moves the fingertip away from
  // the phone (the hand shrinks on screen), and so does reaching for the candy: a detector that fired on any quick
  // shrink fired on the reach, off the candy (phone test 11-06/07: "계속 허공"). So the hammer first arms: the tip
  // holds still over the candy for ARM_MS (lateral speed < ARM_MOVE hand sizes/s, size steady within ARM_SPREAD).
  // Then the hand pushing TAP_DROP smaller than where it armed strikes there (the needle lands at the same 6 %).
  // Over the shape itself only a quick push (TAP_RATE /s) counts, so sinking slowly while aiming does not fail you.
  ARM_MS: 200, ARM_MOVE: 1.2, ARM_SPREAD: 0.04, TAP_DROP: 0.06, TAP_RATE: 0.5, TAP_LEAVE: 0.9,
  TIP: { needle: 0.45, swab: 0.5, hammer: 0.12 },   // hammer: ahead of the index fingertip   // tool contact point ahead of the pinch, in hand sizes
};

// ---------- renderer ----------
const canvas = $("gl"), video = $("video");
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.autoClear = false;
const camera = new THREE.PerspectiveCamera(60, 1, 0.01, 10);
const scene = new THREE.Scene(), bgScene = new THREE.Scene(), fgScene = new THREE.Scene();
const ortho = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
const pmrem = new THREE.PMREMGenerator(renderer);

// the camera frame lives in a canvas so the picture, the hand reading and the light reading all use the same frame
const frameCv = document.createElement("canvas"); frameCv.width = 4; frameCv.height = 4;
const fctx = frameCv.getContext("2d");
const vtex = new THREE.CanvasTexture(frameCv);
vtex.minFilter = THREE.LinearFilter; vtex.generateMipmaps = false;
const maskCv = document.createElement("canvas"); maskCv.width = maskCv.height = 4;
const mctx = maskCv.getContext("2d");
const mtex = new THREE.CanvasTexture(maskCv);

const QUAD_V = "varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }";
const crop = { value: new THREE.Vector2(1, 1) };
const quad = (frag, uniforms, extra = {}) => {
  const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({
    vertexShader: QUAD_V, fragmentShader: frag, uniforms, depthTest: false, depthWrite: false, ...extra }));
  m.frustumCulled = false;
  return m;
};
bgScene.add(quad(`uniform sampler2D map; uniform vec2 crop; varying vec2 vUv;
  void main(){ gl_FragColor = vec4(texture2D(map, .5 + (vUv - .5) * crop).rgb, 1.); }`, { map: { value: vtex }, crop }));
const skinU = { value: new THREE.Vector3(0.8, 0.6, 0.5) };
fgScene.add(quad(`uniform sampler2D map; uniform sampler2D mask; uniform vec2 crop; uniform vec3 skin; varying vec2 vUv;
  vec3 ycc(vec3 c){ return vec3(dot(c, vec3(.299, .587, .114)), dot(c, vec3(-.169, -.331, .5)), dot(c, vec3(.5, -.419, -.081))); }
  void main(){
    vec4 m = texture2D(mask, vUv); if (m.g < .01) discard;
    vec3 c = texture2D(map, .5 + (vUv - .5) * crop).rgb, a = ycc(c), b = ycc(skin);
    // same chroma as the palm, and not far darker or brighter (shadowed knuckles still pass)
    float sk = (1. - smoothstep(.035, .075, distance(a.yz, b.yz))) * smoothstep(.3, .5, a.x / max(b.x, .05)) * (1. - smoothstep(1.6, 2., a.x / max(b.x, .05)));
    gl_FragColor = vec4(c, max(smoothstep(.3, .7, m.r), m.g * sk)); }`,
  { map: { value: vtex }, mask: { value: mtex }, crop, skin: skinU }, { transparent: true }));
const grainU = { time: { value: 0 }, amt: { value: 0.07 } };
const grain = quad(`uniform float time; uniform float amt;
  float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233)) + time) * 43758.5453); }
  void main(){ float n = h(floor(gl_FragCoord.xy / 1.5)) - .5; gl_FragColor = vec4(vec3(step(0., n)), abs(n) * amt * 2.); }`,
  grainU, { transparent: true });
grain.renderOrder = 9;
fgScene.add(grain);

// ---------- lights: driven by the camera frame (lightFromFrame) ----------
const hemi = new THREE.HemisphereLight(0xffffff, 0x554433, 1.1);
const key = new THREE.DirectionalLight(0xffffff, 2.0);
scene.add(hemi, key, key.target);

// ---------- candy ----------
const anchor = new THREE.Group(); scene.add(anchor);
const candy = new THREE.Group(); anchor.add(candy);
const cv = {}, ctx = {}, tex = {};
for (const k of ["color", "height", "rough", "alpha"]) {
  cv[k] = document.createElement("canvas"); cv[k].width = cv[k].height = TEX;
  ctx[k] = cv[k].getContext("2d", { willReadFrequently: k === "alpha" });
  tex[k] = new THREE.CanvasTexture(cv[k]);
  tex[k].anisotropy = 4;
}
tex.color.colorSpace = THREE.SRGBColorSpace;

const discGeo = (() => {
  const g = new THREE.CircleGeometry(R * 0.982, 128), p = g.attributes.position, uv = g.attributes.uv;
  for (let i = 0; i < p.count; i++) uv.setXY(i, p.getX(i) / (2 * R) + 0.5, p.getY(i) / (2 * R) + 0.5);
  return g;
})();
const topMat = new THREE.MeshPhysicalMaterial({
  map: tex.color, bumpMap: tex.height, bumpScale: 2.2, roughnessMap: tex.rough, roughness: 1, alphaMap: tex.alpha,
  alphaTest: 0.5, clearcoat: 0.6, clearcoatRoughness: 0.22, emissive: 0xff8a2a, emissiveMap: tex.color, emissiveIntensity: 0.07,
});
const bottomMat = new THREE.MeshStandardMaterial({ color: 0x8a4512, roughness: 0.55, alphaMap: tex.alpha, alphaTest: 0.5, side: THREE.BackSide });
const rimMat = new THREE.MeshPhysicalMaterial({ color: 0xb8691f, roughness: 0.35, clearcoat: 0.5, clearcoatRoughness: 0.25, emissive: 0xff8a2a, emissiveIntensity: 0.06 });
const top = new THREE.Mesh(discGeo, topMat), bottom = new THREE.Mesh(discGeo, bottomMat);
bottom.position.z = -T;
candy.add(top, bottom);

/** One rim band from local angle a0 to a1 (radians, counter-clockwise), with a rounded top edge. */
function band(a0, a1, n = 12) {
  const prof = [[R * 0.982, 0], [R - 0.0002, -0.0006], [R, -0.0016], [R, -T + 0.001], [R - 0.0006, -T]], m = prof.length;
  const pos = [], idx = [];
  for (let i = 0; i <= n; i++) {
    const a = a0 + (a1 - a0) * i / n;
    for (const [r, z] of prof) pos.push(r * Math.cos(a), r * Math.sin(a), z);
  }
  for (let i = 0; i < n; i++) for (let j = 0; j < m - 1; j++) { const a = i * m + j, b = a + m; idx.push(a, a + 1, b, b, a + 1, b + 1); }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3)); g.setIndex(idx); g.computeVertexNormals();
  return g;
}
// rim bands are counted in texture angles (y down, so clockwise on screen); local angles are their negatives
const rimA = k => [k * 2 * Math.PI / RIMN, (k + 1) * 2 * Math.PI / RIMN];
const rimGeo = [], rims = [];
for (let k = 0; k < RIMN; k++) { const [a0, a1] = rimA(k); rimGeo.push(band(-a1, -a0, 2)); const m = new THREE.Mesh(rimGeo[k], rimMat); rims.push(m); candy.add(m); }

// contact shadow of the tool on the candy: darker and tighter as the tool comes down
const shadowTex = (() => {
  const c = document.createElement("canvas"); c.width = c.height = 64; const x = c.getContext("2d");
  const g = x.createRadialGradient(32, 32, 0, 32, 32, 32); g.addColorStop(0, "rgba(0,0,0,.7)"); g.addColorStop(1, "rgba(0,0,0,0)");
  x.fillStyle = g; x.fillRect(0, 0, 64, 64); return new THREE.CanvasTexture(c);
})();
const shadow = new THREE.Mesh(new THREE.PlaneGeometry(0.014, 0.014), new THREE.MeshBasicMaterial({ map: shadowTex, transparent: true, depthWrite: false }));
shadow.position.z = 0.0004; shadow.visible = false; candy.add(shadow);

// ---------- tools (built with the contact point at the origin, the body along +Y) ----------
const metal = new THREE.MeshStandardMaterial({ color: 0xe6eaed, metalness: 0.9, roughness: 0.25 });
function makeTools() {
  // A sharps sewing needle (size 6-7: 37 mm x 0.69 mm, JJ Needles size chart): a long round shaft, a long gradual
  // taper to the point, a slightly swollen head with a round-ended slot eye. The shaft is drawn 1.3 mm so it is not
  // lost on the phone (a real one is ~2 px at 30 cm); a polished highlight and a thin dark edge carry the rest.
  // The shaft is built 1 unit long so placeTool can stretch it from the fingers to the point; the eye is in metres
  // and placeTool undoes the stretch on it.
  const needle = new THREE.Group(), rim = new THREE.MeshBasicMaterial({ color: 0x16120e, side: THREE.BackSide });
  const steel = new THREE.MeshStandardMaterial({ color: 0xdfe4e8, metalness: 1, roughness: 0.14 });
  const NR = 0.00065;
  const prof = [[0, 0], [0.04, 0.004], [0.16, 0.02], [0.4, 0.06], [0.7, 0.12], [0.92, 0.18], [1, 0.24], [1, 0.95], [1.12, 0.975], [1.12, 1]].map(([r, y]) => new THREE.Vector2(r * NR, y));
  for (const [mat, k] of [[steel, 1], [rim, 1.55]]) {
    const g = new THREE.LatheGeometry(prof.map(v => new THREE.Vector2(v.x * k, v.y)), 14);
    needle.add(new THREE.Mesh(g, mat));
  }
  const eye = new THREE.Group();
  const knob = new THREE.Mesh(new THREE.SphereGeometry(NR * 1.12, 12, 8), steel); knob.scale.set(1, 1.3, 0.8);
  const slot = new THREE.Mesh(new THREE.CapsuleGeometry(NR * 0.32, NR * 3.2, 4, 8), new THREE.MeshBasicMaterial({ color: 0x0c0a08 }));
  slot.scale.z = 3; slot.position.y = -NR * 2.6;
  eye.add(knob, slot); eye.position.y = 1; needle.add(eye); needle.userData.eye = eye;
  const swab = new THREE.Group();
  const stick = new THREE.Mesh(new THREE.CylinderGeometry(0.0013, 0.0013, 0.075, 10), new THREE.MeshStandardMaterial({ color: 0xf6f3ee, roughness: 0.7 }));
  stick.position.y = 0.0455;
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.0034, 18, 12), new THREE.MeshPhysicalMaterial({ color: 0xe4edf1, roughness: 0.5, clearcoat: 0.6, clearcoatRoughness: 0.3 }));
  bulb.scale.set(1, 1.7, 1); bulb.position.y = 0.0052;
  swab.add(stick, bulb);
  const hammer = new THREE.Group();
  const head = new THREE.Mesh(new THREE.CylinderGeometry(0.0075, 0.0075, 0.03, 20), new THREE.MeshStandardMaterial({ color: 0xc4cad0, metalness: 0.85, roughness: 0.28 }));
  head.rotation.z = Math.PI / 2; head.position.y = 0.0075;
  const handle = new THREE.Mesh(new THREE.CylinderGeometry(0.0026, 0.0032, 0.085, 12), new THREE.MeshStandardMaterial({ color: 0xb07a45, roughness: 0.55 }));
  handle.position.y = 0.057;
  hammer.add(head, handle);
  for (const g of [needle, swab, hammer]) { g.visible = false; scene.add(g); }
  return { needle, swab, hammer };
}
const toolMesh = makeTools();

// ---------- state ----------
const S = {
  mode: null, tool: null, shape: "circle", ended: false, gyro: false, spawn: 1, wob: 0, spin: 0,
  pts: [], depth: [], soft: [], melt: new Float32Array(40 * 40), gone: new Uint8Array(0), stress: 0,
  pieces: [], dirty: new Set(), lastUpload: 0, contact: null, prev: null, v: 0, lastHit: 0, offscreenSince: 0,
};
const H = { present: false, P: null, hs: 120, pinched: false, pinchRun: 0, openRun: 0, pinchAt: 0, lostAt: 0, tip: null, dir: [-0.5, -0.86], grip: null, idx: null, t: 0, ys: [] };
const fx = oneEuro(), fy = oneEuro();

// ---------- candy textures ----------
function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
const toPx = (sx, sy) => [(0.5 + sx * 0.5) * TEX, (0.5 + sy * 0.5) * TEX];
function shapePath() {
  const p = new Path2D();
  for (const path of SHAPES[S.shape].paths) path.forEach(([x, y], i) => { const [px, py] = toPx(x, y); i ? p.lineTo(px, py) : p.moveTo(px, py); });
  return p;
}
function resample(path, step) {
  const out = [];
  for (let i = 0; i < path.length - 1; i++) {
    const [x0, y0] = path[i], [x1, y1] = path[i + 1], n = Math.max(1, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / step));
    for (let j = 0; j < n; j++) out.push([x0 + (x1 - x0) * j / n, y0 + (y1 - y0) * j / n]);
  }
  return out;
}
function inShape(sx, sy) {
  const p = SHAPES[S.shape].paths[0];
  let ins = false;
  for (let i = 0, j = p.length - 1; i < p.length; j = i++) {
    const [xi, yi] = p[i], [xj, yj] = p[j];
    if ((yi > sy) !== (yj > sy) && sx < (xj - xi) * (sy - yi) / (yj - yi) + xi) ins = !ins;
  }
  return ins;
}

function paintCandy() {
  const r = rng(7), C = TEX / 2, c = ctx.color, h = ctx.height, ro = ctx.rough, a = ctx.alpha;
  const disc = (x, fill) => { x.fillStyle = fill; x.beginPath(); x.arc(C, C, C - 1, 0, Math.PI * 2); x.fill(); };
  for (const x of [c, h, ro, a]) { x.globalCompositeOperation = "source-over"; x.clearRect(0, 0, TEX, TEX); x.fillStyle = "#000"; x.fillRect(0, 0, TEX, TEX); }
  const g = c.createRadialGradient(TEX * 0.44, TEX * 0.40, 0, C, C, C);
  [[0, "#f7cd7c"], [0.45, "#eab25a"], [0.78, "#d9953e"], [0.92, "#bf7a2c"], [1, "#94511a"]].forEach(([o, col]) => g.addColorStop(o, col));
  disc(c, g);
  disc(h, "#8c8c8c"); disc(ro, "#707070"); disc(a, "#fff");
  // caramel mottling and height noise
  for (let i = 0; i < 520; i++) {
    const ang = r() * Math.PI * 2, d = Math.sqrt(r()) * (C - 10), x = C + Math.cos(ang) * d, y = C + Math.sin(ang) * d, s = 3 + r() * 14;
    c.fillStyle = r() < 0.6 ? `rgba(140,70,18,${0.03 + r() * 0.05})` : `rgba(255,222,150,${0.03 + r() * 0.04})`;
    c.beginPath(); c.arc(x, y, s, 0, Math.PI * 2); c.fill();
    h.fillStyle = r() < 0.5 ? `rgba(255,255,255,${0.04 + r() * 0.05})` : `rgba(0,0,0,${0.04 + r() * 0.05})`;
    h.beginPath(); h.arc(x, y, s * 1.4, 0, Math.PI * 2); h.fill();
  }
  // bubbles: light glassy dots with a dark rim, dimpled in the height map
  for (let i = 0; i < 170; i++) {
    const ang = r() * Math.PI * 2, d = Math.sqrt(r()) * (C - 14), x = C + Math.cos(ang) * d, y = C + Math.sin(ang) * d, s = 1.2 + r() * r() * 5;
    c.fillStyle = "rgba(255,236,190,.30)"; c.strokeStyle = "rgba(105,48,10,.38)"; c.lineWidth = 0.8;
    c.beginPath(); c.arc(x, y, s, 0, Math.PI * 2); c.fill(); c.stroke();
    h.fillStyle = "rgba(0,0,0,.35)"; h.beginPath(); h.arc(x, y, s, 0, Math.PI * 2); h.fill();
    ro.fillStyle = "rgba(0,0,0,.25)"; ro.beginPath(); ro.arc(x, y, s, 0, Math.PI * 2); ro.fill();
  }
  // darker, rougher, rounded-off rim
  const edge = (x, from, to) => { const e = x.createRadialGradient(C, C, C * 0.84, C, C, C); e.addColorStop(0, from); e.addColorStop(1, to); disc(x, e); };
  edge(c, "rgba(90,40,8,0)", "rgba(90,40,8,.42)");
  edge(h, "rgba(0,0,0,0)", "rgba(0,0,0,.55)");
  edge(ro, "rgba(255,255,255,0)", "rgba(255,255,255,.22)");
  // the pressed stamp
  const sp = shapePath();
  for (const x of [c, h]) { x.lineJoin = "round"; x.lineCap = "round"; }
  c.strokeStyle = "rgba(120,58,14,.62)"; c.lineWidth = 6.5; c.stroke(sp);
  c.save(); c.translate(0, 1.6); c.strokeStyle = "rgba(255,215,150,.28)"; c.lineWidth = 1.6; c.stroke(sp); c.restore();
  h.save(); h.shadowColor = "#4a4a4a"; h.shadowBlur = 4; h.strokeStyle = "#4a4a4a"; h.lineWidth = 7; h.stroke(sp); h.restore();
  for (const k of ["color", "height", "rough", "alpha"]) S.dirty.add(k);
}

function reset(shape = S.shape) {
  S.shape = shape; S.ended = false; S.stress = 0; S.melt.fill(0); S.wob = 0; S.spin = 0;
  S.pts = resample(SHAPES[shape].paths[0], 0.01); S.depth = new Float32Array(S.pts.length); S.soft = new Float32Array(S.pts.length); S.gone = new Uint8Array(S.pts.length);
  for (const p of S.pieces) scene.remove(p.g);
  S.pieces = []; S.shapeCracks = [];
  rims.forEach(m => { m.visible = true; });
  S.F = makeField(TEX, inShape, outlineDist);
  S.outer0 = 0; for (let k = 0; k < S.F.alive.length; k++) if (S.F.alive[k] && !S.F.inside[k]) S.outer0++;
  candy.rotation.set(0, 0, 0);
  paintCandy();
  $("again").hidden = true; toast("");
  resetWatcher();
  for (const [k, b] of [["circle", "shCircle"], ["tri", "shTri"], ["star", "shStar"]]) $(b).setAttribute("aria-pressed", String(k === shape));
}

// ---------- placing the candy in the room ----------
const v3 = new THREE.Vector3(), v3b = new THREE.Vector3();
function place() {
  camera.updateMatrixWorld();
  const fwd = v3.set(0, 0, -1).applyQuaternion(camera.quaternion);
  anchor.position.copy(camera.position).addScaledVector(fwd, DIST);
  // upright on the screen as it is held now (world up is degenerate when the phone points at the floor)
  anchor.up.set(0, 1, 0).applyQuaternion(camera.quaternion);
  anchor.lookAt(camera.position);
  anchor.rotateX(TILT);
  S.spawn = 0;
}

// ---------- gyro (three.js r133 DeviceOrientationControls math) ----------
let orient = null;
const gq = new THREE.Quaternion(), euler = new THREE.Euler(), qBack = new THREE.Quaternion(-Math.sqrt(0.5), 0, 0, Math.sqrt(0.5)), qs = new THREE.Quaternion(), Z = new THREE.Vector3(0, 0, 1);
addEventListener("deviceorientation", e => { if (e.beta != null && e.gamma != null) orient = e; });
function gyroTarget() {
  if (!orient) return null;
  const D = Math.PI / 180, so = (screen.orientation && screen.orientation.angle) || window.orientation || 0;
  euler.set(orient.beta * D, (orient.alpha || 0) * D, -orient.gamma * D, "YXZ");
  gq.setFromEuler(euler); gq.multiply(qBack); gq.multiply(qs.setFromAxisAngle(Z, -so * D));
  return gq;
}

// ---------- layout ----------
let W = 1, Hh = 1, crop0 = { k: 1 };
function fit() {
  W = innerWidth; Hh = innerHeight;
  const vw = frameCv.width, vh = frameCv.height, k = Math.max(W / vw, Hh / vh);
  crop.value.set(W / (vw * k), Hh / (vh * k));
  // render at about the camera's own resolution: a sharper candy than the video looks pasted on
  renderer.setPixelRatio(Math.min(2, Math.max(1, 1 / k)));
  renderer.setSize(W, Hh, false);
  const f = S.mode === "camera" ? (Math.max(vw, vh) / 2) / Math.tan(AR.LONG_FOV / 2 * Math.PI / 180) : (vh / 2) / Math.tan(30 * Math.PI / 180);
  camera.fov = 2 * Math.atan((Hh / k / 2) / f) * 180 / Math.PI;
  camera.aspect = W / Hh; camera.updateProjectionMatrix();
  maskCv.width = Math.max(4, Math.round(W / 4)); maskCv.height = Math.max(4, Math.round(Hh / 4));
  crop0 = { k, vw, vh };
}
addEventListener("resize", () => { if (S.mode) fit(); });
const toScreen = p => { const { k, vw, vh } = crop0; return [p.x * vw * k - (vw * k - W) / 2, p.y * vh * k - (vh * k - Hh) / 2]; };

// ---------- light from the camera frame ----------
const lightCv = document.createElement("canvas"); lightCv.width = lightCv.height = 16;
const lctx = lightCv.getContext("2d", { willReadFrequently: true });
const envCv = document.createElement("canvas"); envCv.width = 128; envCv.height = 64;
const ectx = envCv.getContext("2d");
let envRT = null, lastLight = 0, lastEnv = 0;
const keyDir = new THREE.Vector3(-0.4, 0.8, 0.5).normalize(), keyDirT = keyDir.clone();
function lightFromFrame(now) {
  if (now - lastLight > 300) {
    lastLight = now;
    lctx.drawImage(frameCv, 0, 0, 16, 16);
    const d = lctx.getImageData(0, 0, 16, 16).data;
    let r = 0, g = 0, b = 0, best = -1, bx = 8, by = 4, sumL = 0;
    for (let i = 0; i < 256; i++) {
      const R_ = d[i * 4], G = d[i * 4 + 1], B = d[i * 4 + 2], L = 0.2126 * R_ + 0.7152 * G + 0.0722 * B;
      r += R_; g += G; b += B; sumL += L;
      if (L > best) { best = L; bx = i % 16; by = (i / 16) | 0; }
    }
    r /= 256; g /= 256; b /= 256;
    const lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255, e = Math.min(1.5, Math.max(0.35, lum / 0.42)), mx = Math.max(r, g, b, 1);
    // a room's colour cast tints the light, but only partly: a green wall must not make green candy
    const tint = new THREE.Color().setRGB(0.8 + 0.2 * r / mx, 0.8 + 0.2 * g / mx, 0.8 + 0.2 * b / mx, THREE.SRGBColorSpace);
    hemi.color.lerp(tint, 0.3); key.color.lerp(tint, 0.3);
    hemi.intensity += (1.0 * e - hemi.intensity) * 0.3;
    key.intensity += (2.0 * e - key.intensity) * 0.3;
    grainU.amt.value = 0.05 + 0.08 * (1 - Math.min(1, lum / 0.5));   // dim rooms are noisier
    S.dark = lum < 0.28;
    // the brightest patch of the frame is where the light comes from
    keyDirT.set((bx / 15) * 2 - 1, 1 - (by / 15) * 2 + 0.4, -0.6).normalize().applyQuaternion(camera.quaternion);
    S.lightStrong = best > 2 * (sumL / 256 + 1);   // a lamp or window, not an evenly lit room
  }
  if (now - lastEnv > 1500) {
    lastEnv = now;
    // a fake panorama from the frame and its mirror, blurred by PMREM: reflections take the room's colours
    ectx.drawImage(frameCv, 0, 0, 64, 64);
    ectx.save(); ectx.scale(-1, 1); ectx.drawImage(frameCv, -128, 0, 64, 64); ectx.restore();
    const t = new THREE.CanvasTexture(envCv); t.mapping = THREE.EquirectangularReflectionMapping; t.colorSpace = THREE.SRGBColorSpace;
    envRT = pmrem.fromEquirectangular(t, envRT); t.dispose();   // reuse the target: no allocation every 1.5 s
    scene.environment = envRT.texture;
    for (const m of [topMat, rimMat]) m.envMapIntensity = 0.55;
  }
  keyDir.lerp(keyDirT, 0.05).normalize();
}

// ---------- hand ----------
let landmarker = null, vframes = 0, lastVF = -1, handMs = 0, handFps = 0;
async function loadHand() {
  const { FilesetResolver, HandLandmarker } = await import(MP + "/vision_bundle.mjs");
  const files = await FilesetResolver.forVisionTasks(MP + "/wasm");
  const opts = d => ({ baseOptions: { modelAssetPath: HAND, delegate: d }, runningMode: "VIDEO", numHands: 1,
    minHandDetectionConfidence: 0.3, minHandPresenceConfidence: 0.3, minTrackingConfidence: 0.3 });
  try { return await HandLandmarker.createFromOptions(files, opts("GPU")); }
  catch { return await HandLandmarker.createFromOptions(files, opts("CPU")); }
}
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
const BONES = [[0, 1], [1, 2], [2, 3], [3, 4], [5, 6], [6, 7], [7, 8], [9, 10], [10, 11], [11, 12], [13, 14], [14, 15], [15, 16], [17, 18], [18, 19], [19, 20]];
const PALM = [0, 1, 2, 5, 9, 13, 17];

function readHand(lm, now) {
  if (!lm) {
    if (H.present && !H.lostAt) H.lostAt = now;
    if (H.present && now - H.lostAt > AR.LOST_GRACE) { H.present = false; H.pinched = false; H.P = null; H.ys = []; H.arm = null; fx.reset(); fy.reset(); }
    drawMask(null);
    return false;
  }
  H.lostAt = 0;
  const P = lm.map(toScreen), was = H.present;
  H.P = P; H.present = true;
  const hs = (dist(P[0], P[5]) + dist(P[0], P[17]) + dist(P[5], P[17])) / 3;
  H.hs = hs;
  if (PROBE) probe(hs, now);
  // pinch needs CONFIRM frames in a row both ways, so one bad frame neither grabs nor drops the tool
  H.pd = dist(P[4], P[8]) / hs;
  const closed = H.pinched ? H.pd < AR.PINCH * AR.RELEASE : H.pd < AR.PINCH;
  if (closed) { H.pinchRun++; H.openRun = 0; } else { H.openRun++; H.pinchRun = 0; }
  if (!H.pinched && H.pinchRun >= AR.CONFIRM) { H.pinched = true; H.pinchAt = now; H.h0 = hs; H.push = 0; vibrate(8); }
  if (H.pinched && H.openRun >= AR.CONFIRM) { H.pinched = false; H.openAt = now; }
  if (H.pinched) {
    // lifting the hand above where it pinched raises the zero with it, so "up, then push down" always works
    if (hs > H.h0) H.h0 = H.h0 * 0.8 + hs * 0.2;
    H.push = H.push * 0.6 + (1 - hs / H.h0) * 0.4;
    H.down = H.down ? H.push > AR.LIFT : H.push > AR.LAND;
  } else { H.push = 0; H.down = false; }
  H.grip = [(P[4][0] + P[8][0]) / 2, (P[4][1] + P[8][1]) / 2];
  let dx = P[8][0] - P[5][0], dy = P[8][1] - P[5][1]; const dl = Math.hypot(dx, dy) || 1;
  H.dir = [H.dir[0] * 0.5 + dx / dl * 0.5, H.dir[1] * 0.5 + dy / dl * 0.5];
  const nl = Math.hypot(...H.dir) || 1; H.dir = [H.dir[0] / nl, H.dir[1] / nl];
  // the needle and swab sit in the pinch; the hammer is the index finger itself, its head just past the fingertip
  const base = S.tool === "hammer" ? P[8] : H.grip;
  const k = AR.TIP[S.tool || "needle"] * hs, raw = [base[0] + H.dir[0] * k, base[1] + H.dir[1] * k];
  if (!was) { fx.reset(); fy.reset(); }
  H.tip = [fx.f(raw[0], now), fy.f(raw[1], now)];
  H.idx = P[8];
  // the tap is read off the raw fingertip, not the smoothed tip: the filter shaves the very speed we look for
  // kept by time, not count, so a tap that blurs the hand away for a frame still has both ends
  H.ys.push({ t: now, x: raw[0], y: raw[1], hs, l: dist(P[5], P[8]) / hs, asp: dist(P[5], P[17]) / (dist(P[0], P[9]) || 1) });
  while (now - H.ys[0].t > 500) H.ys.shift();
  if (S.mode === "camera" && S.tool === "hammer") H.tapE = tap();
  drawMask(P);
  return true;
}
function contactAt(x, y) { const keep = H.tip; H.tip = [x, y]; const c = contact(); H.tip = keep; return c; }
// The hammer: arm while still over the candy, strike on the push. Returns the strike strength, 0 if none.
function tap() {
  const h = H.ys, b = h[h.length - 1];
  H.hamPush = 0;
  if (b.t < (H.tapBlock || 0)) return 0;
  const A = H.arm;
  if (!A) {
    const w = h.filter(a => b.t - a.t <= AR.ARM_MS), w0 = w[0], span = (b.t - w0.t) / 1000;
    if (span < AR.ARM_MS / 1000 * 0.7) return 0;
    let lo = Infinity, hi = 0; for (const a of w) { lo = Math.min(lo, a.hs); hi = Math.max(hi, a.hs); }
    if (Math.hypot(b.x - w0.x, b.y - w0.y) / b.hs / span > AR.ARM_MOVE || hi / lo - 1 > AR.ARM_SPREAD) return 0;
    let c = contactAt(b.x, b.y); if (c && !c.onCandy) c = snapToCandy(c);
    if (!c || !c.onCandy) return 0;
    H.arm = { top: b.hs, topT: b.t, t: b.t, x: b.x, y: b.y }; return 0;
  }
  // moved off to aim somewhere else: disarm (it re-arms when the tip settles again)
  if (Math.hypot(b.x - A.x, b.y - A.y) > b.hs * AR.TAP_LEAVE) { H.arm = null; return 0; }
  if (b.hs > A.top) { A.top = b.hs; A.topT = b.t; }
  const d = 1 - b.hs / A.top, rate = d / Math.max(0.05, (b.t - A.topT) / 1000);
  H.hamPush = d;
  if (PROBE) PR.dmax = Math.max(PR.dmax || 0, d);
  if (d < AR.TAP_DROP) return 0;
  const path = h.filter(a => a.t >= A.t).map(a => [a.x, a.y]), c = strikePoint(path);
  const onShape = c && c.onCandy && inShape(c.sx, c.sy) && outlineDist(c.sx, c.sy) > 0.03;
  if (onShape && rate < AR.TAP_RATE) { A.top = b.hs; A.topT = b.t; toast("모양 위예요 · 바깥에서 쳐요", "var(--gold)", 900); return 0; }
  H.arm = null; H.tapBlock = b.t + 250; H.tapPath = path;
  if (PROBE) PR.tap = `망치 ${(d * 100).toFixed(0)}% ${rate.toFixed(2)}/s`;
  return Math.min(1.8, Math.max(0.8, rate / AR.TAP_RATE));
}
// Where the tap struck: walk the swing path back from where it ended and take the first point over the candy. A
// tip that passed over the candy on its way counts (operator 11-06: "통과해도 닿은 부분이 쳐지는 걸로"). If that
// point is in the shape but a point outside it lies within 0.15 R on the same path, the outside one wins.
function strikePoint(path) {
  const keep = H.tip; let first = null, out = null;
  if (path.length === 1) path = [path[0], path[0]];
  for (let i = path.length - 1; i > 0 && !out; i--) {
    const [x0, y0] = path[i], [x1, y1] = path[i - 1], n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 4) || 1;
    for (let j = 0; j <= n; j++) {
      H.tip = [x0 + (x1 - x0) * j / n, y0 + (y1 - y0) * j / n]; const c = contact();
      if (!c || !c.onCandy) continue; first ||= c;
      if (!inShape(c.sx, c.sy) || outlineDist(c.sx, c.sy) < 0.03) { out = c; break; }
    }
  }
  if (!first) { H.tip = path[path.length - 1]; first = contact(); }
  H.tip = keep;
  return out && Math.hypot(out.sx - first.sx, out.sy - first.sy) < 0.15 ? out : first;
}
// Two channels: red = surely hand (the bones drawn finger-thick), green = maybe hand (the same, much wider). The
// overlay shader keeps a "maybe" pixel only if its colour is close to the skin colour read off the palm, so the cut
// follows the real edge of the fingers instead of a cartoon outline.
function drawMask(P) {
  const w = maskCv.width, h = maskCv.height, s = w / W;
  mctx.fillStyle = "#000"; mctx.fillRect(0, 0, w, h);
  if (P) {
    // hand size from the whole palm triangle: the knuckle line alone shrinks to nothing when the hand turns sideways
    const q = P.map(([x, y]) => [x * s, y * s]), hs = H.hs * s;
    // the palm is the hull of its points: in a grip the thumb folds over the palm, the plain polygon crosses itself
    // and its overlap is left unfilled (phone test 11-05: a candy-coloured hole in the back of the hand)
    const palm = hull(PALM.map(i => q[i]));
    mctx.lineCap = "round"; mctx.lineJoin = "round";
    const pass = (col, k) => {
      mctx.fillStyle = mctx.strokeStyle = col;
      mctx.lineWidth = hs * 0.22 * k;
      mctx.beginPath(); palm.forEach((p, j) => j ? mctx.lineTo(...p) : mctx.moveTo(...p)); mctx.closePath(); mctx.fill(); mctx.stroke();
      for (const [a, b] of BONES) { mctx.lineWidth = hs * (a < 5 ? 0.26 : 0.21) * k; mctx.beginPath(); mctx.moveTo(...q[a]); mctx.lineTo(...q[b]); mctx.stroke(); }
      // the wrist and forearm run on off the bottom of the hand
      mctx.lineWidth = hs * 0.75 * k; mctx.beginPath(); mctx.moveTo(...q[0]); mctx.lineTo(q[0][0] + (q[0][0] - q[9][0]) * 3, q[0][1] + (q[0][1] - q[9][1]) * 3); mctx.stroke();
    };
    // a soft "maybe" edge: a hard one shows as a straight cut where skin-coloured background (a face, a wooden desk) passes
    mctx.filter = `blur(${Math.max(1, hs * 0.06).toFixed(1)}px)`;
    pass("#0f0", 1.8);
    mctx.globalCompositeOperation = "lighter"; mctx.filter = "blur(1px)"; pass("#f00", 0.9);
    mctx.globalCompositeOperation = "source-over"; mctx.filter = "none";
  }
  mtex.needsUpdate = true;
}
// convex hull, monotone chain
function hull(pts) {
  const p = pts.slice().sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  const cr = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], up = [];
  for (const x of p) { while (lo.length > 1 && cr(lo[lo.length - 2], lo[lo.length - 1], x) <= 0) lo.pop(); lo.push(x); }
  for (const x of p.reverse()) { while (up.length > 1 && cr(up[up.length - 2], up[up.length - 1], x) <= 0) up.pop(); up.push(x); }
  return lo.slice(0, -1).concat(up.slice(0, -1));
}
// skin colour: the palm centre, read every half second (a light change moves it)
let lastSkin = 0;
function readSkin(lm, now) {
  if (now - lastSkin < 500) return;
  lastSkin = now;
  const cx = (lm[0].x + lm[5].x + lm[9].x + lm[13].x + lm[17].x) / 5 * frameCv.width, cy = (lm[0].y + lm[5].y + lm[9].y + lm[13].y + lm[17].y) / 5 * frameCv.height;
  const d = fctx.getImageData(Math.max(0, cx - 4) | 0, Math.max(0, cy - 4) | 0, 8, 8).data;
  let r = 0, g = 0, b = 0;
  for (let i = 0; i < d.length; i += 4) { r += d[i]; g += d[i + 1]; b += d[i + 2]; }
  const n = d.length / 4;
  skinU.value.set(r / n / 255, g / n / 255, b / n / 255);
}

// ---------- ?probe: hand-size meter for the height test ----------
// Height will be read off the hand's size on screen (smaller = further from the phone = lower). Before choosing the
// touch threshold we need to know how much that size jitters on a real phone: this draws the last 4 s of size,
// relative to the size at the moment of the pinch, and the jitter (sd over the last 0.5 s).
const PROBE = Q.has("probe");
const PR = { hist: [], ref: 0, cv: null };
function probe(hs, now) {
  if (!PR.cv) {
    PR.cv = document.createElement("canvas"); PR.cv.width = 300; PR.cv.height = 220;
    Object.assign(PR.cv.style, { position: "fixed", left: "8px", bottom: "120px", width: "150px", height: "110px", zIndex: 50, background: "rgba(0,0,0,.55)", borderRadius: "8px", pointerEvents: "none" });
    document.body.appendChild(PR.cv);
  }
  if (H.pinched && !PR.ref) PR.ref = hs;
  if (!H.pinched) PR.ref = 0;
  const ref = PR.ref || hs;
  PR.hist.push([now, (hs / ref - 1) * 100]);
  while (PR.hist.length && now - PR.hist[0][0] > 4000) PR.hist.shift();
  const last = PR.hist.filter(([t]) => now - t < 500).map(h => h[1]), m = last.reduce((a, b) => a + b, 0) / last.length;
  const sd = Math.sqrt(last.reduce((a, b) => a + (b - m) ** 2, 0) / last.length);
  const g = PR.cv.getContext("2d"), W2 = 300, H2 = 220, y = v => H2 / 2 - v * 10;   // 1 % = 10 px, +-11 %
  g.clearRect(0, 0, W2, H2);
  g.strokeStyle = "rgba(255,255,255,.25)"; g.lineWidth = 1;
  for (const v of [-10, -5, 0, 5, 10]) { g.beginPath(); g.moveTo(0, y(v)); g.lineTo(W2, y(v)); g.stroke(); }
  g.strokeStyle = PR.ref ? "#ffc23d" : "#8fd"; g.lineWidth = 3; g.beginPath();
  PR.hist.forEach(([t, v], i) => { const x = W2 - (now - t) / 4000 * W2; i ? g.lineTo(x, y(v)) : g.moveTo(x, y(v)); }); g.stroke();
  g.fillStyle = "#fff"; g.font = "bold 24px sans-serif";
  g.fillText(`${PR.ref ? "집음" : "안 집음"}  ${m >= 0 ? "+" : ""}${m.toFixed(1)}%`, 10, 30);
  g.fillText(`떨림 ${sd.toFixed(1)}%  손 ${hs.toFixed(0)}px`, 10, H2 - 12);
  if (S.tool === "hammer") { g.font = "bold 18px sans-serif"; g.fillText(`${H.arm ? "준비" : "대기"} 밀기 ${((H.hamPush || 0) * 100).toFixed(0)}% 최대 ${((PR.dmax || 0) * 100).toFixed(0)}%`, 10, 60); g.fillText(`${PR.tap || ""} ${PR.hit || ""}`, 10, 84); }
}

// ---------- touch fallback ----------
const touch = { x: 0, y: 0, down: false, on: false, downAt: 0 };
canvas.addEventListener("pointerdown", e => {
  if (S.mode !== "touch") return;
  Object.assign(touch, { x: e.clientX, y: e.clientY, down: true, on: true, downAt: performance.now() });
  canvas.setPointerCapture(e.pointerId);
  if (S.tool === "hammer") { updateTouchHand(performance.now()); const c = contact(); if (c) hit(c); }
});
canvas.addEventListener("pointermove", e => { if (S.mode === "touch") { touch.x = e.clientX; touch.y = e.clientY; touch.on = true; } });
const up = () => { touch.down = false; };
canvas.addEventListener("pointerup", up); canvas.addEventListener("pointercancel", up);
function updateTouchHand(now) {
  // the tool sits a little above the finger so the finger does not hide its point
  H.present = touch.on; H.hs = 110; H.dir = [-0.42, -0.91];
  const wasDown = H.pinched;
  H.pinched = touch.down && S.tool !== "hammer";
  if (H.pinched && !wasDown) H.pinchAt = now;
  H.tip = [touch.x - 20, touch.y - 44]; H.grip = [touch.x, touch.y]; H.P = null; H.idx = null;
}

// ---------- contact: where the tool's point meets the candy ----------
const ray = new THREE.Raycaster(), plane = new THREE.Plane(), ndc = new THREE.Vector2(), hitP = new THREE.Vector3(), nrm = new THREE.Vector3();
function rayAt(x, y) { ndc.set(x / W * 2 - 1, -(y / Hh) * 2 + 1); ray.setFromCamera(ndc, camera); return ray.ray; }
function contact() {
  if (!S.tool || !H.present || !H.tip) return null;
  top.updateMatrixWorld();
  nrm.set(0, 0, 1).transformDirection(top.matrixWorld);
  plane.setFromNormalAndCoplanarPoint(nrm, v3.setFromMatrixPosition(top.matrixWorld));
  const r = rayAt(H.tip[0], H.tip[1]);
  if (!r.intersectPlane(plane, hitP) || hitP.distanceTo(camera.position) > DIST * 2.5) {
    hitP.copy(r.origin).addScaledVector(r.direction, DIST);
    return { world: hitP.clone(), onCandy: false, pressed: false };
  }
  const l = top.worldToLocal(hitP.clone()), sx = l.x / R, sy = -l.y / R, [px, py] = toPx(sx, sy);
  let onCandy = Math.hypot(sx, sy) < 0.97;
  if (onCandy) onCandy = ctx.alpha.getImageData(px | 0, py | 0, 1, 1).data[1] > 128;
  const cam = S.mode === "camera";
  const pressed = cam ? H.pinched && H.down : H.pinched && performance.now() - H.pinchAt > AR.LAND_MS;
  // 0 = just touching .. 1 = too hard
  const press = cam ? Math.max(0, Math.min(1, (H.push - AR.LIFT) / (AR.HARD - AR.LIFT))) : 0.35;
  return { world: hitP.clone(), sx, sy, px, py, onCandy, pressed, press };
}

// ---------- what the tools do ----------
function stroke(x0, y0, x1, y1, style, lw, k) { const c = ctx[k]; c.strokeStyle = style; c.lineWidth = lw; c.lineCap = "round"; c.beginPath(); c.moveTo(x0, y0); c.lineTo(x1, y1); c.stroke(); S.dirty.add(k); }
function blob(x, y, r, inner, k, op = "source-over") {
  const c = ctx[k], g = c.createRadialGradient(x, y, 0, x, y, r); g.addColorStop(0, inner); g.addColorStop(1, inner.replace(/[\d.]+\)$/, "0)"));
  c.globalCompositeOperation = op; c.fillStyle = g; c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); c.fill(); c.globalCompositeOperation = "source-over";
  S.dirty.add(k);
}
function work(c, dt, now) {
  if (S.ended || !c || !c.pressed || !c.onCandy || S.tool === "hammer") { S.prev = null; S.v *= 0.8; sound(0); return; }
  const p = S.prev; S.prev = c;
  if (!p) { if (S.tool === "needle") poke(c); return; }
  const mm = Math.hypot(c.sx - p.sx, c.sy - p.sy) * R * 1000;
  if (mm > 25) return;   // a jump (lost frame): not a stroke
  S.v = S.v * 0.6 + (mm / Math.max(dt, 0.008)) * 0.4;
  if (S.tool === "needle") {
    // pressing harder carves deeper but cracks at a lower speed; pressing too hard cracks even when slow
    const kp = 0.5 + 1.5 * c.press, fast = AR.FAST * (1.25 - 0.5 * c.press);
    stroke(p.px, p.py, c.px, c.py, "rgba(250,238,214,.34)", 3.6, "color");
    stroke(p.px, p.py, c.px, c.py, "rgba(25,25,25,.30)", 3.2, "height");
    stroke(p.px, p.py, c.px, c.py, "rgba(255,255,255,.18)", 3.2, "rough");
    score(S.F, p.px, p.py, c.px, c.py, AR.SCORE * kp);   // every scratch weakens the candy where it runs, on the line or not
    let near = 0;
    for (let i = 0; i < S.pts.length; i++) {
      const [x, y] = S.pts[i];
      if (Math.abs(x - c.sx) < AR.NEAR && Math.abs(y - c.sy) < AR.NEAR && Math.hypot(x - c.sx, y - c.sy) < AR.NEAR) {
        S.depth[i] = Math.min(1, S.depth[i] + AR.CARVE * kp * mm * (1 + S.soft[i])); near++;
      }
    }
    if (S.v > fast || c.press >= 1) {
      S.stress += (Math.max(0, S.v - fast) * 0.06 + (c.press >= 1 ? 0.8 : 0)) * dt;
      if (S.stress > 1) { S.stress = 0; chip(c); }
    } else S.stress = Math.max(0, S.stress - dt * 0.5);
    sound(Math.min(0.5, S.v / 60), near ? 2600 : 1900);
  } else if (S.tool === "swab") {
    blob(c.px, c.py, 4.5 * PXMM, "rgba(105,40,4,.05)", "color");
    blob(c.px, c.py, 5 * PXMM, "rgba(0,0,0,.10)", "rough");
    blob(c.px, c.py, 5 * PXMM, "rgba(140,140,140,.05)", "height");
    const gx = Math.min(39, Math.max(0, ((c.sx + 1) / 2 * 40) | 0)), gy = Math.min(39, Math.max(0, ((c.sy + 1) / 2 * 40) | 0)), k = gy * 40 + gx;
    S.melt[k] += dt;
    for (let i = 0; i < S.pts.length; i++) { const [x, y] = S.pts[i]; if (Math.hypot(x - c.sx, y - c.sy) < 0.1) S.soft[i] = Math.min(1, S.soft[i] + dt / 1.2); }
    S.meltNow = S.melt[k] / AR.MELT_HOLE;
    heal(c, dt);
    if (S.melt[k] > AR.MELT_HOLE) { S.melt[k] = -1e9; hole(c); }
    sound(Math.min(0.18, 0.05 + S.v / 200), 700);
  }
}
// a poke: the needle lands on the candy (the fingers close over it). Pokes along the line are how most people start.
function poke(c) {
  blob(c.px, c.py, 1.4 * PXMM, "rgba(25,25,25,.55)", "height");
  blob(c.px, c.py, 1.6 * PXMM, "rgba(250,236,210,.45)", "color");
  blob(c.px, c.py, 1.4 * PXMM, "rgba(255,255,255,.3)", "rough");
  score(S.F, c.px, c.py, c.px, c.py, AR.POKE);
  let near = 0;
  for (let i = 0; i < S.pts.length; i++) {
    const [x, y] = S.pts[i], d = Math.hypot(x - c.sx, y - c.sy);
    if (d < AR.NEAR) { S.depth[i] = Math.min(1, S.depth[i] + AR.POKE * (1 - d / AR.NEAR) * (1 + S.soft[i])); near++; }
  }
  sound(0.4, near ? 2800 : 2000); setTimeout(() => sound(0), 60);
  vibrate(near ? 12 : 6);
}
// ---------- cracks ----------
// A crack is a jagged polyline in texture px, drawn as a dark hairline with a lit lip and sunk into the height map
// so the light catches it.
function jag(x, y, ang, stop, step = 5, wob = 0.8) {
  const pts = [[x, y]];
  // the heading jitters but keeps pulling back to where it set off: sugar cracks run straight-ish, not in curls
  const a0 = ang;
  for (let k = 0; k < 400 && !stop(x, y, k); k++) { ang = a0 + (ang - a0) * 0.7 + (Math.random() - 0.5) * wob; x += Math.cos(ang) * step; y += Math.sin(ang) * step; pts.push([x, y]); }
  return pts;
}
function drawCrack(pts, w = 1.7) {
  const path = c => { c.beginPath(); pts.forEach(([a, b], i) => i ? c.lineTo(a, b) : c.moveTo(a, b)); };
  const c = ctx.color; c.lineJoin = "bevel"; c.lineCap = "round";
  path(c); c.strokeStyle = "rgba(55,22,4,.95)"; c.lineWidth = w; c.stroke();
  c.save(); c.translate(1, 1); path(c); c.strokeStyle = "rgba(255,230,180,.5)"; c.lineWidth = w * 0.4; c.stroke(); c.restore();
  path(ctx.height); ctx.height.strokeStyle = "#1a1a1a"; ctx.height.lineWidth = w + 0.5; ctx.height.stroke();
  S.dirty.add("color"); S.dirty.add("height");
}
const C2 = TEX / 2;
const fromCentre = (x, y) => Math.hypot(x - C2, y - C2);
const gpx = i => toPx(...S.pts[i]);
const toS = ([x, y]) => [x / TEX * 2 - 1, y / TEX * 2 - 1];
function outlineDist(sx, sy) { let b = 1e9; for (const [x, y] of S.pts) b = Math.min(b, Math.hypot(x - sx, y - sy)); return b; }
/** From a point on the outline, a crack that runs into the shape. */
function inwardCrack(x, y) {
  const len = TEX * (0.12 + Math.random() * 0.18);
  return jag(x, y, Math.atan2(C2 - y, C2 - x) + (Math.random() - 0.5) * 1.2, (_, __, k) => k * 5 > len, 5, 0.9);
}

// Needle too fast: a crack starts at the point. If it runs past the groove into the shape, the shape is broken.
function chip(c) {
  const over = Math.min(2, S.v / AR.FAST);
  const len = TEX * (0.04 + 0.06 * over * Math.random());
  const pts = jag(c.px, c.py, Math.random() * Math.PI * 2, (_, __, k) => k * 4 > len, 4, 1.0);
  drawCrack(pts, 1.4); markCrack(S.F, pts);
  snap(0.5); vibrate(25); S.wob = 0.4;
  strike();   // the guard brings the rifle up
  const inside = pts.some(p => { const [sx, sy] = toS(p); return inShape(sx, sy) && outlineDist(sx, sy) > 0.035; });
  if (inside) { shapeCrack(pts, 0.5); toast("모양에 금이 갔어요 · 면봉으로 녹이면 붙어요", "var(--bad)", 2200); }
  else toast("금! 너무 빨라요", "var(--bad)", 900);
}
function hole(c) {
  blob(c.px, c.py, 3.4 * PXMM, "rgba(0,0,0,1)", "alpha");
  blob(c.px, c.py, 5 * PXMM, "rgba(70,25,2,.5)", "color");
  vibrate(20);
  if (inShape(c.sx, c.sy) && outlineDist(c.sx, c.sy) > 0.03) fail("모양이 녹아서 구멍이 났어요");
  else toast("녹아서 구멍이 났어요", "var(--bad)", 1200);
}

// A crack in the shape is not the end: rub it with the wet swab and the sugar melts it shut. Left open, the hammer
// widens it (hit) and lifting the shape out snaps it (win check). sev: 0 mended .. 1 broken.
function shapeCrack(pts, sev) { S.shapeCracks.push({ pts, sev }); }
function heal(c, dt) {
  const r = 5 * PXMM;
  for (const k of S.shapeCracks) {
    if (k.sev <= 0) continue;
    let near = false;
    for (let i = 1; i < k.pts.length; i++) {
      const [x0, y0] = k.pts[i - 1], [x1, y1] = k.pts[i];
      if (Math.hypot(x1 - c.px, y1 - c.py) > r) continue;
      near = true;   // melted sugar flows into the line: it fades into the candy and its groove fills
      stroke(x0, y0, x1, y1, "rgba(214,150,72,.16)", 3.4, "color");
      stroke(x0, y0, x1, y1, "rgba(140,140,140,.16)", 3.0, "height");
    }
    if (!near) continue;
    k.sev = Math.max(0, k.sev - dt / 1.6);
    if (k.sev === 0) {
      for (const [x, y] of k.pts) { const m = Math.floor(y / S.F.cell) * S.F.N + Math.floor(x / S.F.cell); if (m >= 0 && m < S.F.crack.length && S.F.inside[m]) S.F.crack[m] = 0; }
      toast("금이 붙었어요", "var(--gold)", 1200); vibrate(10);
    }
  }
}

// ---------- hammer ----------
// A hit starts two cracks at the hammer: one out toward the rim, one in toward the outline. Each walks the weakest
// way (fracture.js): along a needle score or the carved groove if it is cut deep enough, straight on through it if
// not. A crack that gets into the shape breaks it; a region the cracks cut off from the shape falls.
const MELT = 0.35;   // a melted point of the groove counts this much extra depth
const strength = i => Math.min(1, S.depth[i] + MELT * S.soft[i]);
const aliveCv = document.createElement("canvas"), alctx = aliveCv.getContext("2d", { willReadFrequently: true });
function updateAlive() {
  const F = S.F, N = F.N;
  aliveCv.width = aliveCv.height = N;
  alctx.drawImage(cv.alpha, 0, 0, N, N);
  const d = alctx.getImageData(0, 0, N, N).data;
  for (let k = 0; k < N * N; k++) if (d[k * 4 + 1] < 128) F.alive[k] = 0;
}
// A tap is read where the fingertip stops, and the finger has travelled by then (and the tip filter lags), so a blow
// aimed at the edge or next to a fallen piece lands just off the candy (phone test 11-06: "허공을 쳤어요" again and
// again). A miss within SNAP of what is left outside the shape lands on the nearest such bit instead.
const SNAP = 0.35;   // in candy radii, ~2 cm
function snapToCandy(c) {
  if (c.sx === undefined) return c;
  const F = S.F, N = F.N; updateAlive();
  let best = SNAP, bk = -1;
  for (let k = 0; k < N * N; k++) {
    if (!F.alive[k] || F.inside[k]) continue;
    const sx = ((k % N) + 0.5) / N * 2 - 1, sy = (((k / N) | 0) + 0.5) / N * 2 - 1, d = Math.hypot(sx - c.sx, sy - c.sy);
    if (d < best) { best = d; bk = k; }
  }
  if (bk < 0) return c;
  const sx = ((bk % N) + 0.5) / N * 2 - 1, sy = (((bk / N) | 0) + 0.5) / N * 2 - 1, [px, py] = toPx(sx, sy);
  return { ...c, sx, sy, px, py, onCandy: true, snapped: best };
}
function hit(c, E = 1) {
  const now = performance.now();
  if (S.ended || now - S.lastHit < 450) return;
  if (c && !c.onCandy) c = snapToCandy(c);
  if (PROBE) PR.hit = c && c.sx !== undefined ? `친 곳 반지름의 ${Math.hypot(c.sx, c.sy).toFixed(2)}배 ${c.onCandy ? "달고나" : "허공"}` : "친 곳 화면 밖";
  if (!c || !c.onCandy) { toast("허공을 쳤어요", "#fff", 700); return; }
  S.lastHit = now; S.wob = Math.min(1.4, 0.6 + 0.5 * E); thud(); vibrate(18);
  if (inShape(c.sx, c.sy) && outlineDist(c.sx, c.sy) > 0.03) {
    for (let k = 0; k < 3; k++) { const len = 10 + Math.random() * 25; drawCrack(jag(c.px, c.py, Math.random() * Math.PI * 2, (_, __, m) => m > len, 5, 0.7)); }
    fail("모양을 쳤어요"); return;
  }
  // an unmended crack in the shape opens a little more with every blow; this is where you find out
  for (const k of S.shapeCracks) if (k.sev > 0) k.sev += 0.25 * E;
  const open = S.shapeCracks.find(k => k.sev >= 1);
  if (open) {
    const [x, y] = open.pts.at(-1); drawCrack(inwardCrack(x, y), 2.2);
    fail("붙이지 않은 금이 망치질에 벌어졌어요"); return;
  }
  const F = S.F;
  updateAlive();
  setGroove(F, S.pts.map(p => toPx(...p)), strength);
  let i0 = 0, best = 1e9;
  S.pts.forEach(([x, y], i) => { const d = Math.hypot(x - c.sx, y - c.sy); if (d < best) { best = d; i0 = i; } });
  const [gx, gy] = gpx(i0);
  const a = walk(F, c.px, c.py, Math.atan2(c.py - C2, c.px - C2) + (Math.random() - 0.5) * 0.8, { E });
  const b = walk(F, c.px, c.py, Math.atan2(gy - c.py, gx - c.px) + (Math.random() - 0.5) * 0.5, { E });
  drawCrack(a.pts); drawCrack(b.pts);
  markCrack(F, a.pts); markCrack(F, b.pts);
  const into = [a, b].find(w => w.end === "in");
  if (into) {
    const [x, y] = into.pts.at(-1), inw = inwardCrack(x, y);
    drawCrack(inw, 2); markCrack(F, inw); shapeCrack(inw, 0.6);
    toast(into.scratched ? "긁은 자국을 따라 금이 모양으로 들어갔어요 · 면봉으로 녹여 붙여요" : "금이 모양 안으로 들어갔어요 · 면봉으로 녹여 붙여요", "var(--bad)", 2400);
  }
  const pieces = loosePieces(F);
  S.lastBreak = { a: a.end, b: b.end, pieces: pieces.map(p => p.length) };
  pieces.forEach(dropCells);
  // what is left of the ring: when only crumbs remain they come away too, and the shape is out
  let left = 0;
  for (let k = 0; k < F.alive.length; k++) if (F.alive[k] && !F.inside[k] && !F.crack[k]) left++;
  if (left < S.outer0 * 0.04) {
    const rest = [];
    for (let k = 0; k < F.alive.length; k++) if (F.alive[k] && !F.inside[k]) rest.push(k);
    dropCells(rest); S.gone.fill(1);
    // lifting it out bends it: a crack left unmended snaps here
    if (S.shapeCracks.some(k => k.sev > 0.15)) { fail("떼어 내는 순간 금을 따라 부러졌어요"); return; }
    win(); return;
  }
  if (into) return;
  if (pieces.length) { snap(1); vibrate([20, 30, 20]); toast("탁! 떨어졌어요", "var(--gold)", 900); }
  else { snap(0.6); toast("쩍, 금만 갔어요", "#fff", 900); }
}
/** Drop the grid cells as one falling piece, with the rim bands along its edge. */
function dropCells(cells) {
  if (!cells.length) return;
  const F = S.F, N = F.N, set = new Set(cells);
  const m = document.createElement("canvas"); m.width = m.height = N;
  const mx = m.getContext("2d"), img = mx.createImageData(N, N);
  let vx = 0, vy = 0;
  for (const k of cells) {
    img.data[k * 4] = img.data[k * 4 + 1] = img.data[k * 4 + 2] = img.data[k * 4 + 3] = 255;
    F.alive[k] = 0;
    vx += (k % N + 0.5) / N - 0.5; vy += ((k / N | 0) + 0.5) / N - 0.5;
  }
  mx.putImageData(img, 0, 0);
  const bands = [];
  for (let k = 0; k < RIMN; k++) {
    if (!rims[k].visible) continue;
    const am = (rimA(k)[0] + rimA(k)[1]) / 2, r = N / 2 * 0.975;
    const cell = Math.floor(N / 2 + Math.sin(am) * r) * N + Math.floor(N / 2 + Math.cos(am) * r);
    if (set.has(cell)) { rims[k].visible = false; bands.push(k); }
  }
  S.pts.forEach(([x, y], i) => {
    const [px, py] = toPx(x * 1.08, y * 1.08), k = Math.floor(py / F.cell) * N + Math.floor(px / F.cell);
    if (set.has(k)) S.gone[i] = 1;
  });
  dropPiece(m, bands, Math.atan2(vy, vx));
}
function dropPiece(region, bands, mid) {
  // masks are built by compositing, not clip(): clipping one canvas while drawing into another wiped the colour
  // canvas in Chrome (headless check 11-05)
  const layer = () => { const c = document.createElement("canvas"); c.width = c.height = TEX; return [c, c.getContext("2d")]; };
  const [mc, mx] = layer();   // white where the piece is: the region (grid cells, smoothed up), outside the shape
  mx.imageSmoothingEnabled = true; mx.drawImage(region, 0, 0, TEX, TEX);
  mx.globalCompositeOperation = "destination-out"; mx.fill(shapePath());
  const [pc, px] = layer();   // the piece: that mask, minus anything already gone
  px.drawImage(mc, 0, 0); px.globalCompositeOperation = "source-in"; px.drawImage(cv.alpha, 0, 0);
  const [bc, bx] = layer();   // the candy loses it
  bx.drawImage(mc, 0, 0); bx.globalCompositeOperation = "source-in"; bx.fillStyle = "#000"; bx.fillRect(0, 0, TEX, TEX);
  ctx.alpha.drawImage(bc, 0, 0);
  S.dirty.add("alpha"); S.dirty.add("color");
  const ptex = new THREE.CanvasTexture(pc);
  const g = new THREE.Group();
  g.add(new THREE.Mesh(discGeo, topMat.clone()), new THREE.Mesh(discGeo, bottomMat.clone()));
  g.children[0].material.alphaMap = ptex; g.children[1].material.alphaMap = ptex; g.children[1].position.z = -T;
  for (const k of bands) g.add(new THREE.Mesh(rimGeo[k], rimMat));
  candy.updateMatrixWorld(); candy.matrixWorld.decompose(g.position, g.quaternion, g.scale);
  scene.add(g);
  const out = v3b.set(Math.cos(mid), -Math.sin(mid), 0).applyQuaternion(g.quaternion);
  S.pieces.push({ g, t: 0, vel: out.multiplyScalar(0.07 + Math.random() * 0.05).add(new THREE.Vector3(0, 0.04, 0)),
    spin: new THREE.Vector3((Math.random() - 0.5) * 6, (Math.random() - 0.5) * 6, (Math.random() - 0.5) * 3) });
}
function fail(why) {
  S.ended = true; snap(1.2); vibrate([40, 40, 60]);
  doom(why);   // no toast: the stinger and the silence say it; the reason is on the result screen   // the retry button comes after the guard has fired
}
function win() {
  S.ended = true; S.spin = 1; WT.running = false; sfx("win");
  toast("떼어 냈어요!", "var(--gold)", 0); $("again").hidden = false;
}

// ---------- tool placement in 3D ----------
const yAxis = new THREE.Vector3(0, 1, 0);
function placeTool(c) {
  for (const k in toolMesh) toolMesh[k].visible = false;
  shadow.visible = false;
  if (!S.tool || !H.present || !H.tip || !c || (typeof WT !== "undefined" && WT.doom)) return;
  // the tool is only there while the fingers hold it: open them and it is gone, so you can see whether you hold it
  if (S.mode === "camera" && !H.pinched && S.tool !== "hammer") return;
  toolMesh.hammer.children[1].visible = S.mode !== "camera";
  // the hammer hovers and only touches down for a moment when it hits
  // a pinched tool hovers HOVER above the candy at the pinch and comes down as the hand pushes away from the phone
  const hov = S.mode === "camera" ? AR.HOVER * Math.max(0, Math.min(1.5, 1 - H.push / AR.LAND)) : 0.012;
  const hamLift = S.mode === "camera" ? 0.02 * Math.max(0, 1 - (H.hamPush || 0) / AR.TAP_DROP) : 0.025;
  const lift = S.tool === "hammer" ? (performance.now() - S.lastHit < 120 ? 0 : hamLift) : c.pressed && c.onCandy ? 0 : hov;
  S.liftMM = c.onCandy ? lift * 1000 : null;
  const m = toolMesh[S.tool], P = c.world.clone();
  const toCam = v3.copy(camera.position).sub(P).normalize();
  P.addScaledVector(toCam, lift);
  // Needle and swab come out of the pinch: the top end sits just behind where thumb and index meet, so on screen
  // the tool runs from your fingers to its point (phone test 11-06: leaning toward the camera it foreshortened to a
  // stub under the fingers and you could not tell where the point was). It lies fairly flat, rising RISE toward the
  // camera at the fingers. The hammer keeps its pen-like lean.
  const pinchTool = S.tool !== "hammer";
  const back = pinchTool && H.grip ? [H.grip[0] - H.dir[0] * H.hs * 0.12, H.grip[1] - H.dir[1] * H.hs * 0.12]
    : [H.tip[0] - H.dir[0] * H.hs * 0.9, H.tip[1] - H.dir[1] * H.hs * 0.9];
  const rb = rayAt(back[0], back[1]), dB = pinchTool ? P.distanceTo(camera.position) - 0.022 : P.distanceTo(camera.position) * 0.8;
  const B = v3b.copy(rb.origin).addScaledVector(rb.direction, dB);
  m.position.copy(P);
  const len = B.distanceTo(P);
  m.quaternion.setFromUnitVectors(yAxis, B.sub(P).normalize());
  if (S.tool === "needle") { m.scale.set(1, len, 1); m.userData.eye.scale.set(1, 1 / len, 1); }
  m.visible = true;
  // the point's shadow is where it will land: off to one side and soft while it hovers, under the point when it touches
  if (c.onCandy && (pinchTool || H.arm)) {
    const l = candy.worldToLocal(c.world.clone()), off = 0.0008 + lift * 0.5;
    shadow.position.set(l.x + off * 0.5, l.y - off, 0.0004);
    const k = 0.45 + lift * 90; shadow.scale.set(k, k, 1); shadow.material.opacity = Math.max(0.3, 0.95 - lift * 50); shadow.visible = true;
  }
}

// ---------- tray ----------
const TOOLS = [
  ["needle", "바늘", '<svg width="34" height="34" viewBox="0 0 34 34"><line x1="7" y1="28" x2="27" y2="6" stroke="#e8ecef" stroke-width="2.4" stroke-linecap="round"/><circle cx="26" cy="7" r="1.6" fill="none" stroke="#e8ecef" stroke-width="1"/></svg>'],
  ["swab", "젖은 면봉", '<svg width="34" height="34" viewBox="0 0 34 34"><line x1="7" y1="28" x2="23" y2="11" stroke="#f6f3ee" stroke-width="2.6" stroke-linecap="round"/><ellipse cx="25" cy="9" rx="4" ry="6" transform="rotate(43 25 9)" fill="#e4edf1"/><circle cx="21" cy="17" r="1.5" fill="#bfe0ff"/></svg>'],
  ["hammer", "망치", '<svg width="34" height="34" viewBox="0 0 34 34"><line x1="9" y1="28" x2="21" y2="12" stroke="#c08a52" stroke-width="3.6" stroke-linecap="round"/><rect x="13" y="5" width="18" height="9" rx="2" transform="rotate(37 22 9.5)" fill="#a7aeb3"/></svg>'],
];
const RING = '<svg class="ring" viewBox="0 0 74 74"><circle cx="37" cy="37" r="34" fill="none" stroke="#ffc23d" stroke-width="3.5" stroke-linecap="round" pathLength="100" stroke-dasharray="0 100"/></svg>';
$("tray").innerHTML = TOOLS.map(([k, name, svg]) => `<button data-tool="${k}"><span class="ic">${svg}</span>${RING}<span>${name}</span></button>`).join("");
const trayBtns = [...$("tray").querySelectorAll("button")];
trayBtns.forEach(b => { b.onclick = () => pickTool(b.dataset.tool); });
function pickTool(k) {
  S.tool = k; S.prev = null; tick(); startClock();
  trayBtns.forEach(b => b.classList.toggle("on", b.dataset.tool === k));
}
const dwell = { tool: null, t: 0, leftAt: 0, done: null };
function updateDwell(now, dt) {
  const p = S.mode === "camera" && H.present && !H.pinched ? H.idx : null;
  let over = null;
  if (p) for (const b of trayBtns) {
    const r = b.querySelector(".ic").getBoundingClientRect(), grow = dwell.tool === b.dataset.tool ? 1.3 : 1.05;
    const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (Math.hypot(p[0] - cx, p[1] - cy) < r.width / 2 * grow) over = b.dataset.tool;
  }
  if (over && over === dwell.tool) dwell.t += dt * 1000, dwell.leftAt = 0;
  else if (over) { dwell.tool = over; dwell.t = 0; dwell.leftAt = 0; }
  else if (dwell.tool) { dwell.leftAt ||= now; if (now - dwell.leftAt > AR.DWELL_GRACE) { dwell.tool = null; dwell.t = 0; dwell.done = null; } }
  if (dwell.tool && dwell.t >= AR.DWELL && dwell.done !== dwell.tool) { dwell.done = dwell.tool; pickTool(dwell.tool); vibrate(15); }
  for (const b of trayBtns) {
    const on = b.dataset.tool === dwell.tool && dwell.done !== dwell.tool;
    b.classList.toggle("hover", b.dataset.tool === dwell.tool);
    b.querySelector(".ring circle").setAttribute("stroke-dasharray", `${on ? Math.min(100, dwell.t / AR.DWELL * 100) : 0} 100`);
  }
}

// ---------- sound and touch feedback ----------
let ac = null, sg = null, sf = null, noiseBuf = null;
function initAudio() {
  try {
    ac = new (window.AudioContext || window.webkitAudioContext)();
    noiseBuf = ac.createBuffer(1, ac.sampleRate, ac.sampleRate);
    const d = noiseBuf.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const src = ac.createBufferSource(); src.buffer = noiseBuf; src.loop = true;
    sf = ac.createBiquadFilter(); sf.type = "bandpass"; sf.frequency.value = 2400; sf.Q.value = 1.4;
    sg = ac.createGain(); sg.gain.value = 0;
    src.connect(sf).connect(sg).connect(ac.destination); src.start();
  } catch { ac = null; }
}
function sound(level, freq = 2400) { if (!ac) return; sg.gain.setTargetAtTime(level, ac.currentTime, 0.03); sf.frequency.setTargetAtTime(freq, ac.currentTime, 0.05); }
function burst(dur, f, gain, type = "highpass") {
  if (!ac) return;
  const s = ac.createBufferSource(); s.buffer = noiseBuf; const fl = ac.createBiquadFilter(); fl.type = type; fl.frequency.value = f;
  const g = ac.createGain(), t = ac.currentTime; g.gain.setValueAtTime(gain, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
  s.connect(fl).connect(g).connect(ac.destination); s.start(t, Math.random() * 0.5); s.stop(t + dur);
}
const snap = (k = 1) => { burst(0.09 * k, 3200, 0.7); burst(0.05, 1200, 0.4); };
const thud = () => burst(0.12, 300, 0.8, "lowpass");
const tick = () => burst(0.03, 4000, 0.25);
const vibrate = p => { try { navigator.vibrate && navigator.vibrate(p); } catch { /* not allowed */ } };

// ---------- HUD ----------
let toastTimer = 0;
function toast(text, color = "#fff", ms = 1200) {
  const t = $("toast"); clearTimeout(toastTimer);
  if (!text) { t.classList.remove("show"); return; }
  t.textContent = text; t.style.color = color; t.classList.add("show");
  if (ms) toastTimer = setTimeout(() => t.classList.remove("show"), ms);
}
function hud(c) {
  const cam = S.mode === "camera";
  let hint = "", sub = "", col = "#fff";
  if (cam && !landmarker) { hint = "손 인식 준비 중…"; }
  else if (!H.present) { hint = cam ? "손을 카메라 앞에 보여 주세요" : "화면을 손가락으로 만져 보세요"; sub = cam ? "폰은 한 손에, 다른 손을 화면 안으로" : ""; }
  else if (!S.tool) { hint = cam ? "위 도구를 손가락으로 가리켜요" : "위 도구를 눌러 골라요"; }
  else if (cam && !H.pinched && S.tool !== "hammer") { hint = "엄지·검지 끝을 꼭 붙여 잡아요"; sub = "떼면 도구를 놓아요"; }
  else if (S.tool === "needle") {
    const mean = S.depth.reduce((a, b) => a + b, 0) / (S.depth.length || 1);
    sub = `선 판 깊이 ${Math.round(mean * 100)}%`;
    if (!c || !c.pressed) {
      hint = !cam ? "누르면 바늘이 닿아요" : c && c.onCandy ? "손을 폰에서 살짝 밀면 바늘이 내려가요" : "달고나 위로 가져가요";
      if (cam && S.liftMM != null) sub = `바늘 끝이 ${Math.max(1, Math.round(S.liftMM))}mm 떠 있어요 · ` + sub;
    }
    else if (c.press >= 1) { hint = "너무 세게 눌러요!"; col = "var(--bad)"; }
    else if (S.v > AR.FAST) { hint = "너무 빨라요!"; col = "var(--bad)"; }
    else if (S.v > AR.SAFE) { hint = "조금 천천히"; col = "var(--gold)"; }
    else { hint = "좋아요, 이 속도"; col = "var(--good)"; }
    if (cam && c && c.pressed) sub = `누르기 ${c.press < 0.25 ? "살살" : c.press < 0.75 ? "알맞게" : "세게"} · ` + sub;
  } else if (S.tool === "swab") { hint = "녹일 곳에 대고 문질러요"; sub = "녹은 곳은 바늘이 잘 들고 금이 덜 가요 · 모양에 간 금은 문지르면 붙어요"; }
  else {
    hint = !cam ? "바깥을 톡 눌러 쳐요" : H.arm ? "좋아요 · 손을 톡 밀어 쳐요" : "검지 끝을 달고나 바깥 위에 잠깐 멈춰요";
    if (cam && H.arm) col = "var(--good)";
    sub = "금은 깊게 판 홈과 긁은 자국을 따라가요 · 얕은 데선 뚫고 들어가요";
  }
  if (S.ended) { hint = ""; sub = ""; }
  $("hint").textContent = hint; $("hint").style.color = col; $("sub").textContent = sub;
  // speed ring (needle) or melt ring (swab) around the tool's point
  const ring = $("ring"), arc = $("ringArc");
  if (c && c.pressed && c.onCandy && H.tip && (S.tool === "needle" || S.tool === "swab") && !S.ended) {
    ring.hidden = false; ring.style.left = H.tip[0] + "px"; ring.style.top = H.tip[1] + "px";
    const f = S.tool === "needle" ? Math.min(1, S.v / AR.FAST) : Math.max(0, Math.min(1, S.meltNow || 0));
    arc.setAttribute("stroke-dasharray", `${f * 100} 100`);
    arc.setAttribute("stroke", f < 0.58 ? "#7ee2a8" : f < 0.9 ? "#ffc23d" : "#ff5c74");
  } else ring.hidden = true;
  const cur = $("cursor");
  if (cam && H.present && H.idx && !(c && c.pressed)) { cur.hidden = false; cur.style.left = H.idx[0] + "px"; cur.style.top = H.idx[1] + "px"; cur.style.opacity = S.tool ? 0.5 : 1; }
  else cur.hidden = true;
  if (PROBE || Q.has("debug")) $("stat").textContent = `${Math.round(fps)} fps · 손 ${Math.round(handFps)} fps ${handMs.toFixed(0)} ms · 자이로 ${S.gyro ? "켜짐" : "없음"}${H.pd != null ? ` · 집기 ${H.pd.toFixed(2)}/${AR.PINCH}` : ""}`;
}

// The tracker sees a copy at most 640 px wide (its models work at ~200 px anyway, so this only saves time), lifted
// in a dark room where a dim hand is the usual miss. The picture on screen stays the untouched frame.
const detCv = document.createElement("canvas"), dctx = detCv.getContext("2d");
function detFrame() {
  const w = Math.min(640, frameCv.width), h = Math.round(frameCv.height * w / frameCv.width);
  if (detCv.width !== w || detCv.height !== h) { detCv.width = w; detCv.height = h; }
  dctx.filter = S.dark ? "brightness(1.7) contrast(1.1)" : "none";
  dctx.drawImage(frameCv, 0, 0, w, h);
  return detCv;
}
// ---------- the watcher: a masked guard on the floor, a 2-minute clock, and what happens when you fail ----------
// The floor is not detected (WebXR has no plane finding on iPhone). With the gyro we know which way is down and the
// phone is assumed EYE metres above the floor (?h=). With rotation-only tracking that height only sets how big he
// looks; his feet land on whatever real floor is at that angle below the horizon. He starts GUARD_D metres ahead and
// creeps closer only while you are not looking at him. Fail or run out of time: silence, a prompt to look up, a beat
// of stillness when you do, then the rack, the aim and the shot. (Horror and AR reviews 11-06.)
const EYE = +(Q.get("h") || 1.2), GUARD_D = 2.4, TIME_LIMIT = 120;
const GS = +(Q.get("gs") || 1.5);   // guard scale: twice life size reads as menacing on a phone (operator 11-06)
// a bigger guard creeps less, so at the end his face still fits on screen when you look up
const near = d => GUARD_D - (GUARD_D - d) / GS;
const STEPS = [[90, near(2.1)], [60, near(1.8)], [30, near(1.5)]], DOOM_D = near(1.4);   // [seconds left, distance he creeps to]
const STRIKE = [[0.35, 0], [0.55, 0.15], [0.75, 0.3]];          // [rifle lift on a careless stroke, rest after it]
renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFShadowMap;
key.castShadow = true; key.shadow.mapSize.set(512, 512); key.shadow.radius = 4; key.shadow.bias = -0.0003; key.shadow.normalBias = 0.015;
Object.assign(key.shadow.camera, { left: -GS, right: GS, top: 2 * GS, bottom: -0.3 * GS, near: 1, far: 8 * GS });
key.shadow.camera.updateProjectionMatrix();   // without this the default 10 m box stays live (blurry, detached shadow)
// the real floor only takes the shadow: a transparent plane that draws nothing but the dark
const floorY = () => camera.position.y - EYE;
const catcher = new THREE.Mesh(new THREE.PlaneGeometry(20, 20), new THREE.ShadowMaterial({ opacity: 0.4 }));
catcher.rotation.x = -Math.PI / 2; catcher.receiveShadow = true; scene.add(catcher);
// soft dark patches right under the soles, and a faint wide one: what makes him stand on the floor, not hover
const blobTex = a => { const c = document.createElement("canvas"); c.width = c.height = 64; const g = c.getContext("2d"), gr = g.createRadialGradient(32, 32, 2, 32, 32, 31); gr.addColorStop(0, `rgba(0,0,0,${a})`); gr.addColorStop(1, "rgba(0,0,0,0)"); g.fillStyle = gr; g.fillRect(0, 0, 64, 64); return new THREE.CanvasTexture(c); };
const soles = new THREE.Group();
for (const [w, h, x, a] of [[0.7, 0.55, 0, 0.25], [0.18, 0.32, -0.12, 0.55], [0.18, 0.32, 0.12, 0.55]]) {
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: blobTex(a), transparent: true, depthWrite: false }));
  m.rotation.x = -Math.PI / 2; m.position.set(x, 0.004, 0.03); soles.add(m);
}
// muzzle flash: an additive sprite for a few frames
const flashTex = (() => { const c = document.createElement("canvas"); c.width = c.height = 64; const g = c.getContext("2d"), gr = g.createRadialGradient(32, 32, 0, 32, 32, 32); gr.addColorStop(0, "rgba(255,250,230,1)"); gr.addColorStop(0.3, "rgba(255,190,90,.9)"); gr.addColorStop(1, "rgba(255,120,30,0)"); g.fillStyle = gr; g.fillRect(0, 0, 64, 64); return new THREE.CanvasTexture(c); })();
const muzzle = new THREE.Sprite(new THREE.SpriteMaterial({ map: flashTex, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
muzzle.scale.setScalar(0.25 * GS); muzzle.visible = false; scene.add(muzzle);

const WT = { guard: null, sfx: null, placed: false, timeLeft: TIME_LIMIT, running: false, lastTick: -1, doom: null,
  alarm: 0, strikes: 0, dir: new THREE.Vector3(0, 0, -1), dist: GUARD_D, move: null, step: 0, frozen: false };
import("./guard.js").then(m => { WT.guard = m.makeGuard(); WT.guard.root.scale.setScalar(GS); scene.add(WT.guard.root); WT.guard.root.add(soles); if (S.mode) placeGuard(); }, e => console.warn("guard", e));
function loadSfx() { if (ac && !WT.sfx) import("./sfx.js").then(m => { WT.sfx = m.makeSfx(ac); sfx("bed", true); }, e => console.warn("sfx", e)); }
const sfx = (k, ...a) => { try { WT.sfx && WT.sfx[k] && WT.sfx[k](...a); } catch (e) { console.warn(e); } };
const gcall = (k, ...a) => { const G = WT.guard; if (G && G[k]) G[k](...a); };

function setGuardDist(d) {
  const g = WT.guard.root;
  g.position.copy(camera.position).addScaledVector(WT.dir, d); g.position.y = floorY();
  g.lookAt(camera.position.x, g.position.y, camera.position.z);
  WT.dist = d;
}
function placeGuard() {
  camera.updateMatrixWorld();
  // ahead on the floor, along where the phone faces (flattened); when it points straight down, along its top edge
  const f = WT.dir.set(0, 0, -1).applyQuaternion(camera.quaternion); f.y = 0;
  if (f.lengthSq() < 0.04) { f.set(0, 1, 0).applyQuaternion(camera.quaternion); f.y = 0; }
  f.normalize();
  catcher.position.set(camera.position.x, floorY() + 0.001, camera.position.z);
  if (!WT.guard) return;
  WT.move = null; setGuardDist(WT.doom ? WT.dist : GUARD_D);
  WT.placed = true;
}
function startClock() { if (!WT.running && !S.ended && !WT.doom) { WT.running = true; } }
const hudEls = () => ["tray", "stat", "timer", "hint", "sub"].map($).concat([document.querySelector(".tools")]);
function resetWatcher() {
  Object.assign(WT, { timeLeft: TIME_LIMIT, running: false, lastTick: -1, doom: null, alarm: 0, strikes: 0, move: null, step: 0, frozen: false });
  $("timer").innerHTML = "2:00<small>도구를 고르면 시작</small>"; $("timer").classList.remove("low"); $("timer").hidden = false;
  $("doom").hidden = true; $("dead").hidden = true; $("again").hidden = true;
  const f = $("flash"); f.style.transition = "none"; f.style.opacity = 0; f.style.background = "";
  hudEls().forEach(e => { if (e) e.style.visibility = ""; });
  canvas.style.filter = ""; canvas.style.transform = "";
  sfx("breath", false); sfx("bed", true); sfx("setTension", 0);
  if (WT.guard) { gcall("setAim", 0); gcall("hold", false); if (WT.placed) setGuardDist(GUARD_D); }
}
// a careless stroke: he brings the rifle up, a little higher each time, and it stays higher
function strike() {
  const k = Math.min(STRIKE.length - 1, WT.strikes++);
  WT.alarm = 2.5; WT.alarmAim = STRIKE[k][0]; WT.restAim = STRIKE[k][1];
  sfx(k === 0 ? "creak" : "halfRack");
}
// fail(): the candy broke or the time ran out. Everything goes quiet; the prompt comes a moment later.
function doom(why) {
  WT.running = false;
  WT.doom = { why, t: 0, seen: 0, fired: 0, prompted: 0 };
  hudEls().forEach(e => { if (e && e.id !== "stat") e.style.visibility = "hidden"; });
  sfx("stinger"); setTimeout(() => sfx("silence"), 60); sfx("breath", true);
  // he closes in while you are still looking down
  if (WT.guard && !guardInView()) setGuardDist(DOOM_D);
}
const headV = new THREE.Vector3();
function guardInView() {
  if (!WT.guard) return true;
  headV.copy(WT.guard.root.position); headV.y += 1.6 * GS;
  const p = headV.project(camera);
  return p.z < 1 && Math.abs(p.x) < 0.75 && p.y < 0.9 && p.y > -0.6;
}
const away = new THREE.Vector3(), lightD = new THREE.Vector3();
function updateWatcher(dt, now) {
  const G = WT.guard;
  if (G) G.root.visible = S.gyro || S.mode !== "camera";   // no gyro: he would ride along with the phone like a sticker
  if (G && !WT.placed && S.mode) placeGuard();
  if (G) {
    catcher.position.y = G.root.position.y + 0.001;
    // Light: a strong bright patch in the room sets the direction (kept high so the shadow stays short and on the
    // floor). In an evenly lit room it is near-overhead and a little beyond him, so his shadow reaches toward you,
    // under the candy, where you are looking.
    away.copy(WT.dir); away.y = 0;
    if (S.lightStrong) { lightD.copy(keyDir); if (lightD.y < 0.65) { lightD.y = 0.65; lightD.normalize(); } }
    else lightD.set(0, 1, 0).addScaledVector(away, 0.5).normalize();
    key.target.position.copy(G.root.position); key.position.copy(G.root.position).addScaledVector(lightD, 4 * GS);
    catcher.material.opacity += ((S.lightStrong ? 0.5 : 0.32) - catcher.material.opacity) * 0.05;
    // a creep toward you, only ever while you are not looking at him
    if (WT.move) {
      WT.move.t += dt; const k = Math.min(1, WT.move.t / 0.4);
      setGuardDist(WT.move.from + (WT.move.to - WT.move.from) * (1 - (1 - k) * (1 - k)));
      if (k >= 1) WT.move = null;
    }
    if (!WT.doom) {
      G.lookAt(camera.position);
      G.setAim(WT.alarm > 0 ? WT.alarmAim : (WT.restAim || 0));
    }
    WT.alarm = Math.max(0, WT.alarm - dt);
    G.update(dt, now / 1000);
    // his breath comes from his head
    if (WT.sfx && WT.sfx.setVoicePos) {
      headV.copy(G.root.position); headV.y += 1.6 * GS; sfx("setVoicePos", headV.x, headV.y, headV.z);
      const fw = v3b.set(0, 0, -1).applyQuaternion(camera.quaternion), upv = new THREE.Vector3(0, 1, 0).applyQuaternion(camera.quaternion);
      sfx("setListener", camera.position, fw, upv);
    }
  }
  // the clock
  if (WT.running && !S.ended) {
    WT.timeLeft = Math.max(0, WT.timeLeft - dt);
    const s = Math.ceil(WT.timeLeft);
    $("timer").textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
    $("timer").classList.toggle("low", s <= 30);
    if (s !== WT.lastTick) { WT.lastTick = s; if (s <= 10 && s > 0) sfx("tick", true); }
    sfx("setTension", Math.max(0, Math.min(1, (60 - WT.timeLeft) / 60)));
    while (WT.step < STEPS.length && WT.timeLeft <= STEPS[WT.step][0]) {
      const [, to] = STEPS[WT.step++];
      if (G && !guardInView() && !WT.move) { WT.move = { from: WT.dist, to, t: 0 }; sfx("scuff"); }
    }
    if (WT.timeLeft <= 0) { S.ended = true; doom("시간 초과"); }
  }
  // the end: look up, a dead-still beat, the rack, the aim, the shot
  const D = WT.doom;
  if (D && !D.fired) {
    D.t += dt;
    if (D.t > 1.4) {
      if (!D.prompted) { D.prompted = 1; toast(""); }
      const seen = guardInView();
      $("doom").hidden = !!D.seen || seen;
      if (seen && !D.seen) { D.seen = D.t; gcall("hold", true); gcall("setAim", 0); }
      if (D.seen) {
        const s = D.t - D.seen;
        if (s > 0.7 && !D.racked) { D.racked = 1; gcall("hold", false); sfx("cock"); }
        if (s > 0.7) gcall("setAim", 1);
        if (s > 1.6) shoot(D.why, true);
      } else if (D.t > 10.4) shoot(D.why, false);   // he does not wait forever
    }
  }
  if (muzzle.visible && now - WT.flashAt > 60) muzzle.visible = false;
}
function shoot(why, seen) {
  const D = WT.doom; D.fired = 1;
  $("doom").hidden = true;
  hudEls().forEach(e => { if (e) e.style.visibility = "hidden"; });
  gcall("fire");
  if (WT.guard && seen) { WT.guard.muzzleWorld(muzzle.position); muzzle.visible = true; WT.flashAt = performance.now(); }
  sfx("breath", false); sfx("gunshot"); setTimeout(() => sfx("ring"), 120);
  vibrate([60, 30, 120]);
  // one white frame, then black: a hit, not a colour change. Then the room comes back frozen and drained.
  const f = $("flash"); f.style.transition = "none"; f.style.background = "#fff"; f.style.opacity = 1;
  setTimeout(() => { f.style.background = "#000"; }, 34);
  const shake = performance.now();
  const sh = () => { const k = 1 - (performance.now() - shake) / 200; if (k <= 0) { canvas.style.transform = ""; return; } canvas.style.transform = `translate(${(Math.random() - 0.5) * 12 * k}px,${(Math.random() - 0.5) * 12 * k}px)`; requestAnimationFrame(sh); };
  if (seen) sh();
  WT.frozen = true;
  const blackFor = seen ? 450 : 1500;
  setTimeout(() => {
    canvas.style.filter = "saturate(0.2) brightness(0.6)";
    f.style.transition = "opacity .9s ease-out"; f.style.opacity = 0;
    // he lowers the rifle and looks away: done with you
    gcall("setAim", 0); away.copy(WT.guard ? WT.guard.root.position : camera.position).add(v3b.set(1.5, 1.6, 0)); gcall("lookAt", away.clone());
  }, blackFor);
  setTimeout(() => { $("deadWhy").textContent = why; $("dead").hidden = false; }, blackFor + 1400);
  setTimeout(() => { $("again").hidden = false; }, blackFor + 2600);
}

// ---------- frame loop ----------
let last = performance.now(), fps = 60, lastWork = 0;
if (video.requestVideoFrameCallback) { const cb = () => { vframes++; video.requestVideoFrameCallback(cb); }; video.requestVideoFrameCallback(cb); }
function grab(now) {
  const stamp = video.requestVideoFrameCallback && vframes > 0 ? vframes : video.currentTime;
  if (!video.videoWidth || stamp === lastVF || WT.frozen) return false;
  lastVF = stamp;
  if (frameCv.width !== video.videoWidth || frameCv.height !== video.videoHeight) { frameCv.width = video.videoWidth; frameCv.height = video.videoHeight; fit(); }
  fctx.drawImage(video, 0, 0);
  vtex.needsUpdate = true;
  if (!landmarker) return true;
  try {
    const t0 = performance.now();
    const r = landmarker.detectForVideo(detFrame(), now);
    handMs = handMs * 0.9 + (performance.now() - t0) * 0.1;
    const lm = r.landmarks && r.landmarks[0];
    if (lm) readSkin(lm, now);
    readHand(lm, now);
    if (H.handT) handFps = handFps * 0.9 + 100 / Math.max(1, now - H.handT);
    H.handT = now;
  } catch (e) { console.warn(e); }
  return true;
}
const camQ = new THREE.Quaternion();
// the camera frame on screen is ~70 ms old; the gyro is not. Render the pose of that moment (?lag= tunes it).
const GYRO_LAG = +(Q.get("lag") || 70), qHist = [];
function loop(now) {
  requestAnimationFrame(loop);
  const dt = Math.min(0.05, (now - last) / 1000); last = now; fps = fps * 0.95 + (1 / Math.max(dt, 0.001)) * 0.05;
  const g = gyroTarget();
  if (g) {
    qHist.push([now, g.clone()]); while (qHist.length > 2 && now - qHist[1][0] > GYRO_LAG) qHist.shift();
    const qt = S.mode === "camera" ? qHist[0][1] : g;
    if (!S.gyro) { S.gyro = true; camera.quaternion.copy(qt); place(); placeGuard(); }
    camera.quaternion.slerp(qt, 1 - Math.exp(-dt / 0.03));
  }
  let fresh = false;
  if (S.mode === "camera") fresh = grab(now);
  else { updateTouchHand(now); fresh = true; }
  let c = contact();
  if (fresh) {
    const wdt = Math.min(0.1, (now - (lastWork || now - 16)) / 1000); lastWork = now;
    work(c, wdt, now);
    // a harder tap carries the cracks further, and through a groove more easily
    if (H.tapE) { const E = H.tapE; H.tapE = 0; hit(strikePoint(H.tapPath), E); }
    updateDwell(now, wdt);
  }
  // the candy idles with a slow bob, wobbles when hit, pops in when (re)placed
  S.spawn = Math.min(1, S.spawn + dt / 0.6);
  const e = S.spawn, back = 1 - Math.pow(1 - e, 3) * (1 - 1.6 * (1 - e));
  const busy = c && c.pressed && c.onCandy;
  candy.position.y = busy || S.ended ? candy.position.y * 0.9 : Math.sin(now / 1000 * Math.PI) * 0.0012;
  S.wob *= Math.pow(0.02, dt);
  candy.rotation.x = Math.sin(now / 40) * 0.05 * S.wob;
  candy.rotation.z = Math.cos(now / 47) * 0.03 * S.wob;
  // the freed shape turns a little left and right to show itself off, never edge-on
  if (S.spin) { S.spin += dt; candy.rotation.y = Math.sin(S.spin * 1.6) * 0.45; candy.position.z = Math.min(0.06, S.spin * 0.05); }
  else candy.position.z = 0;
  candy.scale.setScalar(Math.max(0.01, back));
  // pieces fall away
  for (const p of S.pieces) {
    p.t += dt; p.vel.y -= 2.6 * dt;
    p.g.position.addScaledVector(p.vel, dt);
    p.g.rotation.x += p.spin.x * dt; p.g.rotation.y += p.spin.y * dt; p.g.rotation.z += p.spin.z * dt;
  }
  S.pieces = S.pieces.filter(p => { if (p.t < 2.2) return true; scene.remove(p.g); return false; });
  // bring the candy back in front if it has been off screen for a while (gyro drift, or the phone turned away)
  v3.setFromMatrixPosition(candy.matrixWorld).project(camera);
  const off = Math.abs(v3.x) > 1.4 || Math.abs(v3.y) > 1.4 || v3.z > 1;
  if (off) { S.offscreenSince ||= now; if (now - S.offscreenSince > 2500) { place(); S.offscreenSince = 0; } } else S.offscreenSince = 0;
  lightFromFrame(now);
  if (!WT.guard) { key.position.copy(anchor.position).addScaledVector(keyDir, 1); key.target.position.copy(anchor.position); }
  updateWatcher(dt, now);
  placeTool(c);
  if (S.dirty.size && now - S.lastUpload > 40) { for (const k of S.dirty) tex[k].needsUpdate = true; S.dirty.clear(); S.lastUpload = now; }
  grainU.time.value = (now / 1000) % 100;
  hud(c);
  renderer.clear();
  renderer.render(bgScene, ortho);
  renderer.clearDepth();
  renderer.render(scene, camera);
  renderer.render(fgScene, ortho);
}

// ---------- start ----------
async function startCamera() {
  const ask = navigator.mediaDevices.getUserMedia({ audio: false,
    video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 960 } } });
  const stream = await Promise.race([ask, new Promise((_, no) => setTimeout(() => no(new Error("timeout")), 15000))]);
  video.srcObject = stream; await video.play();
  return stream;
}
function roomBackdrop() {
  frameCv.width = 720; frameCv.height = 1280;
  const g = fctx.createLinearGradient(0, 0, 0, 1280);
  g.addColorStop(0, "#5a6470"); g.addColorStop(0.7, "#383d44"); g.addColorStop(0.71, "#6d4b33"); g.addColorStop(1, "#3a271a");
  fctx.fillStyle = g; fctx.fillRect(0, 0, 720, 1280);
  fctx.fillStyle = "rgba(240,246,250,.75)"; fctx.filter = "blur(18px)"; fctx.fillRect(60, 120, 240, 360); fctx.filter = "none";
  vtex.needsUpdate = true;
}
function begin(mode) {
  S.mode = mode; $("start").hidden = true;
  initAudio(); loadSfx(); fit(); reset("circle"); place(); placeGuard();
  requestAnimationFrame(loop);
}
$("go").onclick = async () => {
  // iOS asks for motion access only from a tap, and before anything is awaited
  try { if (typeof DeviceOrientationEvent !== "undefined" && typeof DeviceOrientationEvent.requestPermission === "function") DeviceOrientationEvent.requestPermission().catch(() => {}); } catch { /* old browser */ }
  $("go").disabled = true; $("msg").hidden = true;
  try { await startCamera(); }
  catch (e) {
    $("msg").hidden = false; $("go").disabled = false;
    $("msg").textContent = e && e.name === "NotAllowedError" ? "카메라가 막혀 있어요. 주소창 옆 설정에서 카메라를 허용해 주세요." : "카메라를 켜지 못했어요. 화면 터치로 해 보세요.";
    return;
  }
  frameCv.width = video.videoWidth || 720; frameCv.height = video.videoHeight || 1280;
  begin("camera");
  loadHand().then(m => { landmarker = m; }, () => toast("손 인식을 불러오지 못했어요", "var(--bad)", 3000));
};
$("touch").onclick = () => {
  try { if (typeof DeviceOrientationEvent !== "undefined" && typeof DeviceOrientationEvent.requestPermission === "function") DeviceOrientationEvent.requestPermission().catch(() => {}); } catch { /* old browser */ }
  roomBackdrop(); begin("touch");
};
$("again").onclick = () => { reset(); place(); placeGuard(); };
$("center").onclick = () => { place(); placeGuard(); };
$("shCircle").onclick = () => { reset("circle"); place(); };
$("shTri").onclick = () => { reset("tri"); place(); };
$("shStar").onclick = () => { reset("star"); place(); };

// test hook (headless checks drive the touch mode through this)
window.__ar = { S, H, AR, WT, toolMesh, fg: fgScene, pickTool, reset, hit, readHand, contact, strikePoint, heal, ctx, cv, shapePath, alphaURL: (k = "alpha") => cv[k].toDataURL(),
  debug: () => { const c = contact(), m = S.tool && toolMesh[S.tool]; const pr = v => { const p = v.clone().project(camera); return [(p.x + 1) / 2 * W, (1 - p.y) / 2 * Hh]; };
    return { tip: H.tip, c: c && { sx: c.sx, sy: c.sy, on: c.onCandy }, tool: m && pr(m.position), toolEnd: m && pr(m.localToWorld(new THREE.Vector3(0, 0.05, 0))), shadow: pr(shadow.getWorldPosition(new THREE.Vector3())) }; }, carveAll: d => { S.depth.fill(d); },
  screenOf: (sx, sy) => { top.updateMatrixWorld(); const p = top.localToWorld(new THREE.Vector3(sx * R, -sy * R, 0)).project(camera); return [(p.x + 1) / 2 * W, (1 - p.y) / 2 * Hh]; } };
