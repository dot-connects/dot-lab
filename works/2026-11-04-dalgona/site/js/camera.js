// Camera input: MediaPipe hand (pinch = needle, push toward the camera = poke) and face (open mouth = lick).
// Thresholds from the phone spike 2026-11-04 (works/2026-11-04-dalgona/brief.md).
const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const HAND = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";
const FACE = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";

export const CAM = {
  PINCH: 0.38,        // thumb-index tip distance in hand sizes (pinched 0.10-0.20, open 0.5-1.2)
  RELEASE: 1.25,      // hysteresis factor
  LOST_GRACE: 300,    // ms the needle survives a dropped hand
  POKE_RATE: 1.1,     // hand growth rate (/s) over RATE_WINDOW that is a jab (spike per frame: still -0.3..0.7, jabs 1.5-3.6)
  REARM_RATE: 0.3,    // the hand must settle below this growth rate ...
  REARM_MS: 120,      // ... for this long before the next jab can count (v1 counted one jab 2-3 times)
  RATE_WINDOW: 100,   // ms over which the growth rate is measured (at least two frames, so 14 fps still sees a jab)
  POKE_AFTER_PINCH: 300, POKE_GAP: 350,
  HOLD_MS: 200,       // the needle stays put this long after a jab (pushing the hand moved the tip)
  TIP: 0.45,          // needle tip this many hand sizes ahead of the pinch, along the index finger
  JAW: 0.4,           // jawOpen that counts as licking (open mouth 0.5-0.74)
  LAND_MS: 250,       // a fresh pinch hovers this long before the needle touches the candy (review r2: landings scratched)
  EURO_MIN: 1.0, EURO_BETA: 0.02,   // needle smoothing (px, px/s)
};

export const BONES = [[0, 1], [1, 2], [2, 3], [3, 4], [0, 5], [5, 6], [6, 7], [7, 8], [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16], [13, 17], [0, 17], [17, 18], [18, 19], [19, 20]];
export const PALM = [0, 1, 2, 5, 9, 13, 17];
export const LIPS = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185];

/**
 * One jab = one poke. Fires when the hand grows faster than POKE_RATE, then stays quiet until the growth rate has
 * been under REARM_RATE for REARM_MS. feed(t ms, hand size) -> true on the frame a jab counts.
 */
export function createPokeDetector() {
  const hist = [];
  let armed = true, calmSince = null, lastPoke = -1e9;
  const d = {
    rate: 0, armed: true,
    reset() { hist.length = 0; armed = true; calmSince = null; d.rate = 0; },
    feed(t, hs, allowed = true) {
      hist.push([t, hs]);
      while (hist.length > 2 && t - hist[1][0] >= CAM.RATE_WINDOW) hist.shift();
      const [t0, h0] = hist[0], span = (t - t0) / 1000;
      d.rate = span > 0.02 ? (hs / h0 - 1) / span : 0;
      let fire = false;
      if (armed && allowed && d.rate > CAM.POKE_RATE && t - lastPoke > CAM.POKE_GAP) { fire = true; armed = false; lastPoke = t; calmSince = null; }
      else if (!armed) {
        if (d.rate < CAM.REARM_RATE) { calmSince ??= t; if (t - calmSince >= CAM.REARM_MS) armed = true; }
        else calmSince = null;
      }
      d.armed = armed;
      return fire;
    },
  };
  return d;
}

export async function startCamera(video, timeoutMs = 5000) {
  const ask = navigator.mediaDevices.getUserMedia({ audio: false,
    video: { facingMode: "user", width: { ideal: 960 }, height: { ideal: 720 } } });
  // in-app browsers sometimes never answer the permission request (#003 spike); give up after a while
  let late = false;
  // a permission granted after we gave up must not leave the camera light on
  ask.then(st => { if (late) st.getTracks().forEach(t => t.stop()); }, () => {});
  const stream = await Promise.race([ask, new Promise((_, no) => setTimeout(() => { late = true; no(new Error("timeout")); }, timeoutMs))]);
  video.srcObject = stream;
  await video.play();
  return stream;
}

export async function loadModels() {
  const { FilesetResolver, HandLandmarker, FaceLandmarker } = await import(MP + "/vision_bundle.mjs");
  const files = await FilesetResolver.forVisionTasks(MP + "/wasm");
  const mk = async (Cls, path, opts) => {
    try { return await Cls.createFromOptions(files, { baseOptions: { modelAssetPath: path, delegate: "GPU" }, runningMode: "VIDEO", ...opts }); }
    catch { return await Cls.createFromOptions(files, { baseOptions: { modelAssetPath: path, delegate: "CPU" }, runningMode: "VIDEO", ...opts }); }
  };
  const HO = { numHands: 1 }, FO = { numFaces: 1, outputFaceBlendshapes: true };
  const models = { hand: await mk(HandLandmarker, HAND, HO), face: await mk(FaceLandmarker, FACE, FO) };
  // some GPUs create the model fine and then throw on the first frame: rebuild both on the CPU once
  models.toCpu = async () => {
    const cpu = (Cls, path, opts) => Cls.createFromOptions(files, { baseOptions: { modelAssetPath: path, delegate: "CPU" }, runningMode: "VIDEO", ...opts });
    models.hand = await cpu(HandLandmarker, HAND, HO); models.face = await cpu(FaceLandmarker, FACE, FO);
  };
  return models;
}

const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);

/**
 * One-Euro filter (Casiez et al. 2012): heavy smoothing when the point is slow, light when it moves fast, so a
 * still hand does not tremble and a moving one does not lag. Values in px, t in ms.
 */
export function oneEuro(minCutoff = CAM.EURO_MIN, beta = CAM.EURO_BETA, dCutoff = 1) {
  let x = null, dx = 0, t0 = 0;
  const alpha = (cut, dt) => 1 / (1 + 1 / (2 * Math.PI * cut * dt));
  return {
    reset() { x = null; dx = 0; },
    f(v, t) {
      if (x == null) { x = v; t0 = t; return v; }
      const dt = Math.max(0.001, (t - t0) / 1000); t0 = t;
      const d = (v - x) / dt, ad = alpha(dCutoff, dt);
      dx = dx + ad * (d - dx);
      const a = alpha(minCutoff + beta * Math.abs(dx), dt);
      x = x + a * (v - x);
      return x;
    },
  };
}

/** Tracks one hand and the mouth on a mirrored, object-fit:cover video filling a W x H box. */
export function createTracker(video, models, { onPoke } = {}) {
  const s = {
    needle: null, pinched: false, lostAt: 0, pinchAt: 0, lastPinch: null, closeSpeed: 0, hs: 0,
    ema: null, jit: [], jitter: 0, lick: null, jaw: 0, hand: null, lips: null, tongue: null, frame: 0,
    lastVT: -1, lastT: 0, fresh: false, vdt: 0, wasLost: false, holdUntil: 0, pokes: 0, fps: 0, foundAt: 0, licking: false,
  };
  let rebuilding = false;
  const det = createPokeDetector();
  s.det = det;
  const fx = oneEuro(), fy = oneEuro();
  let W = 0, H = 0;
  const size = (w, h) => { W = w; H = h; };
  const toScreen = p => {
    const vw = video.videoWidth, vh = video.videoHeight, k = Math.max(W / vw, H / vh), dw = vw * k, dh = vh * k;
    return [W - (p.x * dw - (dw - W) / 2), p.y * dh - (dh - H) / 2];
  };

  function update(now) {
    s.fresh = false;
    if (!video.videoWidth || video.currentTime === s.lastVT) return s;
    s.lastVT = video.currentTime; s.frame++; s.fresh = true;
    const dt = Math.max(0.001, (now - (s.lastT || now - 33)) / 1000); s.lastT = now; s.vdt = dt;
    s.fps = s.fps ? s.fps * 0.9 + 0.1 / dt : 1 / dt;
    if (rebuilding) return s;
    try {
      // the face is slow to read: every 4th frame, every 2nd while the mouth is open; never on the same frame as the
      // hand, so one frame never pays for both models
      if (s.frame % (s.jaw > CAM.JAW * 0.6 ? 2 : 4) === 0) { readFace(models.face.detectForVideo(video, now)); keepHand(); }
      else readHand(models.hand.detectForVideo(video, now), dt, now);
    } catch (e) {
      rebuilding = true;
      models.toCpu().finally(() => { rebuilding = false; });
    }
    return s;
  }

  // a frame without a hand reading: the needle stays where it was, without counting as a move
  function keepHand() { if (s.needle) s.needle = { ...s.needle, held: true }; }

  function readHand(r, dt, now) {
    const lm = r.landmarks && r.landmarks[0];
    if (!lm) {
      s.hand = null; s.lastPinch = null; det.reset();
      if (s.pinched && now - s.lostAt > CAM.LOST_GRACE && s.lostAt) { s.pinched = false; s.needle = null; }
      if (!s.lostAt) s.lostAt = now;
      s.wasLost = true;
      keepHand();
      return;
    }
    if (s.wasLost) s.foundAt = now;   // the first frames after finding a hand again jump in size: no jabs yet
    s.lostAt = 0;
    const P = lm.map(toScreen);
    s.hand = P;
    // palm triangle: steadier than wrist-to-middle when the fingers curl to pinch
    const hs = (dist(P[0], P[5]) + dist(P[0], P[17]) + dist(P[5], P[17])) / 3;
    s.hs = hs;
    const pd = dist(P[4], P[8]) / hs;
    s.closeSpeed = s.lastPinch != null ? (s.lastPinch - pd) / dt : 0;
    s.lastPinch = pd;
    const was = s.pinched;
    s.pinched = s.pinched ? pd < CAM.PINCH * CAM.RELEASE : pd < CAM.PINCH;
    const grip = [(P[4][0] + P[8][0]) / 2, (P[4][1] + P[8][1]) / 2];
    // the needle sticks out ahead of the fingers along the index finger, so the fingers do not hide the line
    let dx = P[8][0] - P[5][0], dy = P[8][1] - P[5][1], dl = Math.hypot(dx, dy) || 1;
    dx /= dl; dy /= dl;
    const raw = [grip[0] + dx * CAM.TIP * hs, grip[1] + dy * CAM.TIP * hs];
    if (!s.pinched || s.wasLost) { fx.reset(); fy.reset(); }
    const tip = [fx.f(raw[0], now), fy.f(raw[1], now)];
    s.grip = grip; s.dir = [dx, dy];
    if (s.pinched && !was) { s.pinchAt = now; s.ema = tip; s.jit = []; }
    // shake: RMS distance from a smoothed path over ~0.5 s, skipping the jolt of closing the fingers
    s.ema = s.ema ? [s.ema[0] * 0.7 + tip[0] * 0.3, s.ema[1] * 0.7 + tip[1] * 0.3] : tip;
    // in a 120 px hand's pixels, so a hand close to the camera is not punished for looking bigger
    if (now - s.pinchAt > CAM.POKE_AFTER_PINCH) { s.jit.push(dist(tip, s.ema) * 120 / hs); if (s.jit.length > 15) s.jit.shift(); }
    s.jitter = s.jit.length ? Math.sqrt(s.jit.reduce((a, x) => a + x * x, 0) / s.jit.length) : 0;
    const poked = det.feed(now, hs, s.pinched && now - s.pinchAt > CAM.POKE_AFTER_PINCH && now - s.foundAt > CAM.POKE_AFTER_PINCH);
    if (poked) { s.holdUntil = now + CAM.HOLD_MS; s.pokes++; }
    const holding = now < s.holdUntil && s.needle;
    if (!s.pinched) s.needle = null;
    else if (holding) s.needle = { ...s.needle, held: true, resume: false };
    // until it has hovered LAND_MS the needle is shown but does not touch
    else s.needle = { x: tip[0], y: tip[1], down: now - s.pinchAt > CAM.LAND_MS, resume: s.wasLost || !was || s.needle?.held === true || !s.needle?.down };
    s.wasLost = false;
    if (poked && onPoke) onPoke(s.needle.x, s.needle.y);
  }

  function readFace(r) {
    const lm = r.faceLandmarks && r.faceLandmarks[0];
    if (!lm) { s.lick = null; s.jaw = 0; s.lips = null; s.tongue = null; s.licking = false; return; }
    const bs = r.faceBlendshapes && r.faceBlendshapes[0] && r.faceBlendshapes[0].categories;
    const j = bs && bs.find(b => b.categoryName === "jawOpen");
    s.jaw = j ? j.score : 0;
    s.lips = LIPS.map(i => toScreen(lm[i]));
    const lo = toScreen(lm[14]), chin = toScreen(lm[152]), mw = dist(toScreen(lm[78]), toScreen(lm[308]));
    // the tongue hangs from the inner lower lip toward the chin and a bit past it
    const ux = chin[0] - lo[0], uy = chin[1] - lo[1], ul = Math.hypot(ux, uy) || 1;
    const out = Math.max(0, Math.min(1, (s.jaw - CAM.JAW * 0.6) / 0.35));
    const len = ul * (0.4 + 0.9 * out);
    const up = toScreen(lm[13]), gap = dist(up, lo);
    s.tongue = out > 0 ? { base: lo, dir: [ux / ul, uy / ul], len, width: mw * 0.6, out, mouthW: mw, gap, top: up } : null;
    // on at 0.4, off below 0.3: talking does not flicker it on and off
    s.licking = s.licking ? s.jaw > CAM.JAW - 0.1 : s.jaw > CAM.JAW;
    s.lick = s.licking ? { x: lo[0] + ux / ul * len, y: lo[1] + uy / ul * len } : null;
    s.mouth = lo;
  }

  return { update, size, state: s };
}
