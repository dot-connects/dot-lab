// Camera input: MediaPipe hand (pinch = needle, push toward the camera = poke) and face (open mouth = lick).
// Thresholds from the phone spike 2026-11-04 (works/2026-11-04-dalgona/brief.md).
const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const HAND = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";
const FACE = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";

export const CAM = {
  PINCH: 0.38,        // thumb-index tip distance in hand sizes (pinched 0.10-0.20, open 0.5-1.2)
  RELEASE: 1.25,      // hysteresis factor
  LOST_GRACE: 300,    // ms the needle survives a dropped hand
  POKE_RATE: 1.3,     // hand growth rate (/s) that is a jab (still -0.3..0.7, jabs 1.5-3.6)
  POKE_AFTER_PINCH: 300, POKE_GAP: 220,
  JAW: 0.4,           // jawOpen that counts as licking (open mouth 0.5-0.74)
};

export async function startCamera(video, timeoutMs = 5000) {
  const ask = navigator.mediaDevices.getUserMedia({ audio: false,
    video: { facingMode: "user", width: { ideal: 960 }, height: { ideal: 720 } } });
  // in-app browsers sometimes never answer the permission request (#003 spike); give up after a while
  const stream = await Promise.race([ask, new Promise((_, no) => setTimeout(() => no(new Error("timeout")), timeoutMs))]);
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
  const hand = await mk(HandLandmarker, HAND, { numHands: 1 });
  const face = await mk(FaceLandmarker, FACE, { numFaces: 1, outputFaceBlendshapes: true });
  return { hand, face };
}

/** Tracks one hand and the mouth on a mirrored, object-fit:cover video filling a W x H box. */
export function createTracker(video, models, { onPoke, onSqueeze } = {}) {
  const s = {
    needle: null, pinched: false, lostAt: 0, pinchAt: 0, lastPinch: null, closeSpeed: 0,
    ema: null, jit: [], jitter: 0, scale: null, lastPoke: 0, lick: null, jaw: 0, hand: null, frame: 0,
    lastVT: -1, lastT: 0,
  };
  let W = 0, H = 0;
  const size = (w, h) => { W = w; H = h; };
  const toScreen = p => {
    const vw = video.videoWidth, vh = video.videoHeight, k = Math.max(W / vw, H / vh), dw = vw * k, dh = vh * k;
    return [W - (p.x * dw - (dw - W) / 2), p.y * dh - (dh - H) / 2];
  };

  function update(now) {
    if (!video.videoWidth || video.currentTime === s.lastVT) return s;
    s.lastVT = video.currentTime; s.frame++;
    const dt = Math.max(0.001, (now - (s.lastT || now - 33)) / 1000); s.lastT = now;
    readHand(models.hand.detectForVideo(video, now), dt, now);
    if (s.frame % 2 === 0) readFace(models.face.detectForVideo(video, now));
    return s;
  }

  function readHand(r, dt, now) {
    const lm = r.landmarks && r.landmarks[0];
    if (!lm) {
      s.hand = null; s.scale = null; s.lastPinch = null;
      if (s.pinched && now - s.lostAt > CAM.LOST_GRACE && s.lostAt) { s.pinched = false; s.needle = null; }
      if (!s.lostAt) s.lostAt = now;
      if (s.needle) s.needle = { ...s.needle, held: true };   // frozen while lost: no scraping from a jump
      return;
    }
    s.lostAt = 0;
    const P = lm.map(toScreen);
    s.hand = P;
    const hs = Math.hypot(P[0][0] - P[9][0], P[0][1] - P[9][1]);
    const pd = Math.hypot(P[4][0] - P[8][0], P[4][1] - P[8][1]) / hs;
    s.closeSpeed = s.lastPinch != null ? (s.lastPinch - pd) / dt : 0;
    s.lastPinch = pd;
    const was = s.pinched;
    s.pinched = s.pinched ? pd < CAM.PINCH * CAM.RELEASE : pd < CAM.PINCH;
    const tip = [(P[4][0] + P[8][0]) / 2, (P[4][1] + P[8][1]) / 2];
    if (s.pinched && !was) { s.pinchAt = now; s.ema = tip; s.jit = []; }
    // shake: RMS distance from a smoothed path over ~0.5 s, skipping the jolt of closing the fingers
    s.ema = s.ema ? [s.ema[0] * 0.7 + tip[0] * 0.3, s.ema[1] * 0.7 + tip[1] * 0.3] : tip;
    if (now - s.pinchAt > CAM.POKE_AFTER_PINCH) { s.jit.push(Math.hypot(tip[0] - s.ema[0], tip[1] - s.ema[1])); if (s.jit.length > 15) s.jit.shift(); }
    s.jitter = s.jit.length ? Math.sqrt(s.jit.reduce((a, x) => a + x * x, 0) / s.jit.length) : 0;
    s.needle = s.pinched ? { x: tip[0], y: tip[1], down: true } : null;
    if (s.pinched && s.closeSpeed > 0 && onSqueeze) onSqueeze(tip[0], tip[1], s.closeSpeed);
    const rate = s.scale ? (hs / s.scale - 1) / dt : 0;
    s.scale = hs;
    if (s.pinched && rate > CAM.POKE_RATE && now - s.pinchAt > CAM.POKE_AFTER_PINCH && now - s.lastPoke > CAM.POKE_GAP) {
      s.lastPoke = now;
      if (onPoke) onPoke(tip[0], tip[1]);
    }
  }

  function readFace(r) {
    const lm = r.faceLandmarks && r.faceLandmarks[0];
    if (!lm) { s.lick = null; s.jaw = 0; return; }
    const bs = r.faceBlendshapes && r.faceBlendshapes[0] && r.faceBlendshapes[0].categories;
    const j = bs && bs.find(b => b.categoryName === "jawOpen");
    s.jaw = j ? j.score : 0;
    const lo = toScreen(lm[14]);   // inner lower lip: the tongue comes out here
    s.lick = s.jaw > CAM.JAW ? { x: lo[0], y: lo[1] + Math.abs(toScreen(lm[152])[1] - lo[1]) * 0.25 } : null;
    s.mouth = lo;
  }

  return { update, size, state: s };
}
