// AR scene: the rear camera (or a CC0 room panorama in screen mode) with one creature standing in it, held in place
// by the gyro (3DoF, like Pokémon GO's plain AR mode).
// Copied and trimmed from works/2026-11-04-dalgona/site/ar/ar.js (D:OT lab #004, 2026-11-05): gyroTarget (the
// three.js r133 DeviceOrientationControls math), the first-pose placement + 2.5 s re-place in the frame loop, the
// camera-fit / FOV (?fov=), startCamera and the colour part of lightFromFrame. Hands, tools, fracture, PMREM and grain
// are not taken. Nothing is imported from that folder.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";

const Q = new URLSearchParams(location.search);
const LONG_FOV = +(Q.get("fov") || 66);    // degrees across the long side of a 26 mm-equivalent rear camera
const DIST = 1.2, DROP = 0.09;             // metres: the creature stands 1.2 m along the first gaze, its centre 9 cm below it
                                           // (centre at ~0.56 of the screen height: on the floor in front of you, not floating)

/** Every picture (pokemon art, room) loads through here: crossOrigin "anonymous" keeps canvases readable later. */
export function loadImg(src) {
  return new Promise((ok, no) => {
    const im = new Image();
    im.crossOrigin = "anonymous";
    im.decoding = "async";
    im.onload = () => ok(im); im.onerror = () => no(new Error("image failed: " + src));
    im.src = src;
  });
}

let renderer, camera, scene, bgScene, ortho, video, vtex, roomTex = null, roomPix = null;
const crop = { value: new THREE.Vector2(1, 1) };
const S = { mode: null, gyro: false, yaw: 0, pitch: 0, offSince: 0, spawn: 1, scale: 1, W: 1, H: 1 };
const anchor = new THREE.Group(), body = new THREE.Group();
let cur = null, shadow = null;   // cur = the creature (creature.js seam)
const tint = new THREE.Color(1, 1, 1), tintT = new THREE.Color(1, 1, 1);

export function init(canvas, videoEl) {
  video = videoEl;
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.autoClear = false;
  camera = new THREE.PerspectiveCamera(LONG_FOV, 1, 0.01, 100);
  scene = new THREE.Scene(); bgScene = new THREE.Scene();
  ortho = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  vtex = new THREE.VideoTexture(video); vtex.colorSpace = THREE.SRGBColorSpace;
  const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({
    vertexShader: "varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }",
    fragmentShader: "uniform sampler2D map; uniform vec2 crop; varying vec2 vUv;\nvoid main(){ gl_FragColor = vec4(texture2D(map, .5 + (vUv - .5) * crop).rgb, 1.);\n#include <colorspace_fragment>\n}",
    uniforms: { map: { value: vtex }, crop }, depthTest: false, depthWrite: false }));
  quad.frustumCulled = false;
  bgScene.add(quad);

  // contact shadow: we do not know the floor (3DoF), so only a soft dark disc under the feet
  const sc = document.createElement("canvas"); sc.width = sc.height = 128;
  const g = sc.getContext("2d"), grd = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  grd.addColorStop(0, "rgba(0,0,0,.55)"); grd.addColorStop(0.55, "rgba(0,0,0,.25)"); grd.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = grd; g.fillRect(0, 0, 128, 128);
  shadow = new THREE.Mesh(new THREE.CircleGeometry(1, 48), new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(sc), transparent: true, depthWrite: false }));
  shadow.rotation.x = -Math.PI / 2;
  anchor.add(shadow, body);
  scene.add(anchor);
  addEventListener("resize", fit);
}

/** The creature (see creature.js for the members it must have). */
export function setCreature(c) {
  if (cur) { body.remove(cur.root); cur.dispose(); }
  cur = c; body.add(c.root);
  shadow.position.y = 0.002;
  S.spawn = 0;
}

/** Screen mode backdrop: an equirectangular room photo as scene.background (it turns with the view). */
export async function loadRoom(src) {
  const img = await loadImg(src);
  roomTex = new THREE.Texture(img); roomTex.mapping = THREE.EquirectangularReflectionMapping; roomTex.colorSpace = THREE.SRGBColorSpace; roomTex.needsUpdate = true;
  const c = document.createElement("canvas"); c.width = 128; c.height = 64;
  const x = c.getContext("2d", { willReadFrequently: true }); x.drawImage(img, 0, 0, 128, 64);
  roomPix = x.getImageData(0, 0, 128, 64).data;
  if (S.mode === "screen") scene.background = roomTex;
}

export function setMode(mode) {
  S.mode = mode;
  scene.background = mode === "screen" ? roomTex : null;
  fit();
}

function fit() {
  S.W = innerWidth; S.H = innerHeight;
  const W = S.W, H = S.H;
  if (S.mode === "camera" && video.videoWidth) {
    const vw = video.videoWidth, vh = video.videoHeight, k = Math.max(W / vw, H / vh);
    crop.value.set(W / (vw * k), H / (vh * k));
    // render near the camera's own resolution: a sharper creature than the video looks pasted on
    renderer.setPixelRatio(Math.min(2, Math.max(1, 1 / k)));
    const f = (Math.max(vw, vh) / 2) / Math.tan(LONG_FOV / 2 * Math.PI / 180);
    camera.fov = 2 * Math.atan((H / k / 2) / f) * 180 / Math.PI;
  } else {
    renderer.setPixelRatio(Math.min(2, devicePixelRatio || 1));
    camera.fov = LONG_FOV * Math.min(1, H / W);   // the long side of a phone held upright spans the camera's long FOV
  }
  renderer.setSize(W, H, false);
  camera.aspect = W / H; camera.updateProjectionMatrix();
}

// ---------- gyro (three.js r133 DeviceOrientationControls math, from dalgona) ----------
let orient = null;
const gq = new THREE.Quaternion(), euler = new THREE.Euler(), qBack = new THREE.Quaternion(-Math.sqrt(0.5), 0, 0, Math.sqrt(0.5)), qs = new THREE.Quaternion(), Z = new THREE.Vector3(0, 0, 1);
// desktop / headless Chrome without a sensor fires all-zero events: that is "no gyro", not a phone lying flat
addEventListener("deviceorientation", e => { if (e.beta != null && e.gamma != null && (e.alpha || e.beta || e.gamma)) orient = e; });
function gyroTarget() {
  if (!orient) return null;
  const D = Math.PI / 180, so = (screen.orientation && screen.orientation.angle) || window.orientation || 0;
  euler.set(orient.beta * D, (orient.alpha || 0) * D, -orient.gamma * D, "YXZ");
  gq.setFromEuler(euler); gq.multiply(qBack); gq.multiply(qs.setFromAxisAngle(Z, -so * D));
  return gq;
}
export const hasGyro = () => !!orient;

/** Screen mode without a gyro: drag to look around (radians). */
export function look(dx, dy) {
  S.yaw += dx; S.pitch = Math.max(-1.3, Math.min(1.3, S.pitch + dy));
}

const v3 = new THREE.Vector3();
/** Stand the creature along the current gaze, DIST away, centre on the gaze line (no vertical offset). */
export function place() {
  camera.updateMatrixWorld();
  const fwd = v3.set(0, 0, -1).applyQuaternion(camera.quaternion);
  anchor.position.copy(camera.position).addScaledVector(fwd, DIST);
  anchor.position.y -= (cur ? cur.hit.cy : 0.175) + DROP;
  S.spawn = 0; S.offSince = 0;
  if (S.mode === "screen") roomLight(fwd);
}

// ---------- light: the creature takes the colour and brightness of what is around it ----------
const lightCv = document.createElement("canvas"); lightCv.width = lightCv.height = 16;
const lctx = lightCv.getContext("2d", { willReadFrequently: true });
let lastLight = 0;
function setTint(r, g, b) {   // 0..255 average of the surroundings
  const lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255, e = Math.min(1.12, Math.max(0.45, lum / 0.42)), mx = Math.max(r, g, b, 1);
  // a room's colour cast tints the creature only partly: a green wall must not make a green pikachu
  tintT.setRGB((0.82 + 0.18 * r / mx) * e, (0.82 + 0.18 * g / mx) * e, (0.82 + 0.18 * b / mx) * e, THREE.SRGBColorSpace);
}
function lightFromFrame(now) {   // camera mode, from dalgona lightFromFrame (colour part only)
  if (now - lastLight < 300 || !video.videoWidth) return;
  lastLight = now;
  lctx.drawImage(video, 0, 0, 16, 16);
  const d = lctx.getImageData(0, 0, 16, 16).data;
  let r = 0, g = 0, b = 0;
  for (let i = 0; i < 256; i++) { r += d[i * 4]; g += d[i * 4 + 1]; b += d[i * 4 + 2]; }
  setTint(r / 256, g / 256, b / 256);
}
function roomLight(dir) {   // screen mode: the panorama around where it stands
  if (!roomPix) return;
  const u = Math.atan2(dir.z, dir.x) / (2 * Math.PI) + 0.5, v = Math.acos(Math.max(-1, Math.min(1, dir.y))) / Math.PI;
  let r = 0, g = 0, b = 0, n = 0;
  for (let dy = -6; dy <= 6; dy++) for (let dx = -10; dx <= 10; dx++) {
    const x = ((Math.round(u * 128) + dx) % 128 + 128) % 128, y = Math.min(63, Math.max(0, Math.round(v * 64) + dy)), i = (y * 128 + x) * 4;
    r += roomPix[i]; g += roomPix[i + 1]; b += roomPix[i + 2]; n++;
  }
  setTint(r / n, g / n, b / n);
}

// ---------- frame ----------
const GYRO_LAG = +(Q.get("lag") || 70), qHist = [];   // the camera frame on screen is ~70 ms old; the gyro is not
const lookQ = new THREE.Quaternion(), lookE = new THREE.Euler(0, 0, 0, "YXZ");
const corners = [[-0.5, 0], [0.5, 0], [-0.5, 1], [0.5, 1]].map(([x, y]) => new THREE.Vector3(x, y, 0));

/**
 * One frame. pose = {scale (0..1, shrinks into the ball), flash, squash, hidden, hold} (creature.js). Returns the target on screen:
 * {box: {x, y, w, h}, center: {x, y}, onScreen} in CSS px, or null before a creature is loaded.
 */
export function frame(now, dt, pose = {}) {
  const g = gyroTarget();
  if (g) {
    qHist.push([now, g.clone()]); while (qHist.length > 2 && now - qHist[1][0] > GYRO_LAG) qHist.shift();
    const qt = S.mode === "camera" ? qHist[0][1] : g;
    if (!S.gyro) { S.gyro = true; camera.quaternion.copy(qt); place(); }
    camera.quaternion.slerp(qt, 1 - Math.exp(-dt / 0.03));
  } else {
    lookE.set(S.pitch, S.yaw, 0); lookQ.setFromEuler(lookE);
    camera.quaternion.slerp(lookQ, 1 - Math.exp(-dt / 0.05));
  }
  camera.updateMatrixWorld();
  if (S.mode === "camera") lightFromFrame(now);
  tint.lerp(tintT, 0.1);

  let out = null;
  if (cur) {
    // stands upright in the room and turns to face you; pops in when (re)placed; the creature moves itself
    anchor.lookAt(v3.set(camera.position.x, anchor.position.y, camera.position.z));
    S.spawn = Math.min(1, S.spawn + dt / 0.5);
    const e = S.spawn, pop = 1 - Math.pow(1 - e, 3) * (1 - 1.6 * (1 - e)), k = Math.max(0.001, pop * (pose.scale ?? 1));
    const lift = cur.update(now, dt, { ...pose, scale: k }, tint), air = Math.max(0.4, 1 - lift / 0.09);
    body.visible = !pose.hidden;
    shadow.visible = !pose.hidden;
    shadow.scale.set(cur.shadowR * k * air, cur.shadowR * k * air, 1);   // the contact shadow shrinks and fades as it hops
    shadow.material.opacity = Math.min(1, k) * air;

    // bring it back in front if it has been off screen for 2.5 s (gyro drift, or the phone turned away)
    anchor.updateMatrixWorld(true);
    v3.set(0, cur.hit.cy, 0).applyMatrix4(anchor.matrixWorld).project(camera);
    const off = Math.abs(v3.x) > 1.4 || Math.abs(v3.y) > 1.4 || v3.z > 1;
    if (off && !pose.hold) { S.offSince ||= now; if (now - S.offSince > 2500) place(); } else S.offSince = 0;

    // screen box of the creature's hit box, unmoved by its idle moves and the catch shrink
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    const hb = cur.hit;
    for (const c of corners) {
      v3.set(c.x * hb.w, hb.cy + (c.y - 0.5) * hb.h, 0).applyMatrix4(anchor.matrixWorld).project(camera);
      const sx = (v3.x + 1) / 2 * S.W, sy = (1 - v3.y) / 2 * S.H;
      x0 = Math.min(x0, sx); x1 = Math.max(x1, sx); y0 = Math.min(y0, sy); y1 = Math.max(y1, sy);
    }
    out = { box: { x: x0, y: y0, w: x1 - x0, h: y1 - y0 }, center: { x: (x0 + x1) / 2, y: (y0 + y1) / 2 }, onScreen: !off, behind: v3.z > 1 };
  }

  renderer.clear();
  if (S.mode === "camera") { renderer.render(bgScene, ortho); renderer.clearDepth(); }
  renderer.render(scene, camera);
  return out;
}

// ---------- camera ----------
export async function startCamera(given) {   // from dalgona startCamera; given = a stream in place of the camera (?still=)
  const ask = given ? Promise.resolve(given) : navigator.mediaDevices.getUserMedia({ audio: false,
    video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 960 } } });
  const stream = await Promise.race([ask, new Promise((_, no) => setTimeout(() => no(new Error("timeout")), 15000))]);
  video.srcObject = stream; await video.play();
  return stream;
}
export function stopCamera() {
  const s = video.srcObject;
  if (s) s.getTracks().forEach(t => t.stop());
  video.srcObject = null;
}

export const debug = () => ({ mode: S.mode, gyro: S.gyro, fov: camera.fov, anchor: anchor.position.toArray(), q: camera.quaternion.toArray() });
