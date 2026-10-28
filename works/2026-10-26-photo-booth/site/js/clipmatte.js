// Moving cut-outs on the HQ model (Round 5, operator item 3: "the moving cut is poor"). In the background, MODNet (the
// stills' worker, segment.js matteFrame) runs on one frame per ~100 ms of each clip. The moving strip then rebuilds each
// clip frame's matte half: the two nearest HQ keyframes blended, except where the recorded fast matte moved since those
// keyframes, which keeps the fast matte there (a blend of two keyframes leaves a see-through double edge on motion).
// = "fuse" in research/video-matting/analyze.py. compose.js recordMovingStrip({ mattes }) applies it; no mattes = the
// recorded fast matte as before.
import { readFmp4 } from "./compose.js";
import { matteFrame, addPet, petMap } from "./segment.js";

const KEY_US = 100_000;              // keyframe spacing
const M0 = 0.05 * 255, M1 = 0.25 * 255;   // fast-matte motion (blurred |diff|) where the fast matte takes over, research values
const LO = 4;                        // the motion map is 1/4 of the model size: only the blend weight comes from it

// limited-range (16..235) green of a [raw | matte] frame's matte half -> 0..255 alpha at w x h
function fastAlpha(src, cx, w, h) {
  const sw = (src.displayWidth || src.width) >> 1, sh = src.displayHeight || src.height;
  cx.drawImage(src, sw, 0, sw, sh, 0, 0, w, h);
  const d = cx.getImageData(0, 0, w, h).data, a = new Uint8Array(w * h);
  for (let i = 0; i < a.length; i++) a[i] = Math.max(0, Math.min(255, (d[i * 4 + 1] - 16) * 255 / 219 + 0.5));
  return a;
}
// box blur radius r, horizontal then vertical, in place (two calls ~ a gaussian of sigma ~r)
function box(f, t, w, h, r) {
  const n = 2 * r + 1;
  for (let y = 0; y < h; y++) { let s = 0; const o = y * w;
    for (let x = -r; x <= r; x++) s += f[o + Math.min(w - 1, Math.max(0, x))];
    for (let x = 0; x < w; x++) { t[o + x] = s / n; s += f[o + Math.min(w - 1, x + r + 1)] - f[o + Math.max(0, x - r)]; } }
  for (let x = 0; x < w; x++) { let s = 0;
    for (let y = -r; y <= r; y++) s += t[Math.min(h - 1, Math.max(0, y)) * w + x];
    for (let y = 0; y < h; y++) { f[y * w + x] = s / n; s += t[Math.min(h - 1, y + r + 1) * w + x] - t[Math.max(0, y - r) * w + x]; } }
}

// One clip <video> (MediaRecorder / WebCodecs fMP4) -> { apply(frame, tUs) } | throws (no model, not decodable).
// onKey(k, n) after each keyframe. ponytail: holds ~2 x 200 KB per keyframe (~20 per 2 s clip); fine for 8 clips.
export async function clipMatte(v, onKey) {
  const mp4 = readFmp4(await (await fetch(v.currentSrc || v.src)).arrayBuffer());
  if (!mp4) throw new Error("clip not decodable");
  const K = { t: [], hq: [], fast: [] }, pend = [];
  let err = null, petOn = false, next = -Infinity, w = 0, h = 0, lw = 0, lh = 0;
  const cx = Object.assign(document.createElement("canvas"), { width: 1, height: 1 }).getContext("2d", { willReadFrequently: true });
  const rc = document.createElement("canvas");
  const dec = new VideoDecoder({ output: f => pend.push(f), error: e => { err = e; } });
  dec.configure(mp4.config);
  const N = Math.ceil((mp4.samples.at(-1).us - mp4.samples[0].us) / KEY_US) + 1;
  const take = async () => {
    while (pend.length) {
      const f = pend.shift();
      try {
        if (f.timestamp < next) continue;
        next = f.timestamp + KEY_US;
        // the raw half on a 2D canvas: MediaPipe's GPU graph aborted (wasm "Failed to create buffer", page hung) on an
        // ImageBitmap made from a VideoFrame; the stills hand it a canvas too
        const rw = f.displayWidth >> 1, rh = f.displayHeight;
        if (rc.width !== rw || rc.height !== rh) { rc.width = rw; rc.height = rh; }
        rc.getContext("2d").drawImage(f, 0, 0, rw, rh, 0, 0, rw, rh);
        const m = await matteFrame(await createImageBitmap(rc));
        // cats / dogs as in the stills (max into MODNet's alpha): every keyframe while a pet is in the clip, every 3rd as a
        // probe while none is (DeepLab ~80 ms a keyframe on a PC, ~10 % of the HQ moving cut: research/round5-engine/).
        // DeepLab runs in pet-worker.js: on the page it aborted MediaPipe's shared GPU pool (face landmarker) mid-decorate.
        if (petOn || K.t.length % 3 === 0) petOn = !!addPet(rc, m, await petMap(await createImageBitmap(rc)));
        if (!w) { w = m.width; h = m.height; lw = Math.ceil(w / LO); lh = Math.ceil(h / LO); cx.canvas.width = lw; cx.canvas.height = lh; }
        K.t.push(f.timestamp); K.hq.push(m.data); K.fast.push(fastAlpha(f, cx, lw, lh));
        onKey?.(K.t.length, N);
      } finally { f.close(); }
    }
  };
  try {
    // the model is the slow part: one sample in, whatever came out goes to the model, so only a frame or two wait
    for (const s of mp4.samples) {
      dec.decode(new EncodedVideoChunk({ type: s.key ? "key" : "delta", timestamp: s.us, data: s.data }));
      await new Promise(r => setTimeout(r, 0));
      if (err) throw err;
      await take();
    }
    await dec.flush(); await take();
    if (err) throw err;
  } finally { pend.forEach(f => f.close()); if (dec.state !== "closed") dec.close(); }
  if (!K.t.length) throw new Error("no keyframes");
  const n = lw * lh, mot = new Float32Array(n), tmp = new Float32Array(n), wt = new Float32Array(n), img = new ImageData(w, h);
  const oc = Object.assign(document.createElement("canvas"), { width: w, height: h }), ox = oc.getContext("2d");
  return {
    keys: K.t.length,
    // frame: canvas [raw | matte] of the clip frame at tUs; its matte half is rebuilt in place. The HQ blend is drawn
    // over the fast matte with opacity 1 - motion weight (source-over = HQ (1 - m) + fast m): the fast matte is
    // never read back at full size, only the 1/4 motion map is (~30 -> a few ms per clip frame, headless)
    apply(frame, tUs) {
      let k = 0; while (k + 1 < K.t.length && K.t[k + 1] <= tUs) k++;
      const k1 = Math.min(k + 1, K.t.length - 1), u = k1 > k ? Math.min(1, Math.max(0, (tUs - K.t[k]) / (K.t[k1] - K.t[k]))) : 0;
      const F = fastAlpha(frame, cx, lw, lh), H0 = K.hq[k], H1 = K.hq[k1], F0 = K.fast[k], F1 = K.fast[k1], d = img.data;
      for (let i = 0; i < n; i++) mot[i] = Math.abs(F[i] - (F0[i] + u * (F1[i] - F0[i])));
      box(mot, tmp, lw, lh, 1); box(mot, tmp, lw, lh, 1);   // ~ sigma 3 at model size
      for (let i = 0; i < n; i++) { const x = Math.min(1, Math.max(0, (mot[i] - M0) / (M1 - M0))); wt[i] = 255 * (1 - x * x * (3 - 2 * x)); }
      for (let y = 0, i = 0; y < h; y++) {
        const ro = ((y / LO) | 0) * lw;
        for (let x = 0; x < w; x++, i++) {
          const g = 16 + (H0[i] + u * (H1[i] - H0[i])) * 219 / 255;   // back to limited range
          d[i * 4] = d[i * 4 + 1] = d[i * 4 + 2] = g; d[i * 4 + 3] = wt[ro + ((x / LO) | 0)];
        }
      }
      ox.putImageData(img, 0, 0);
      const fx = frame.getContext("2d"), hw = frame.width >> 1;
      fx.drawImage(oc, hw, 0, hw, frame.height);
    },
  };
}

// All clips of a strip, one after another (one model session): Map<video, matte>, filled as each clip finishes.
// onProgress(0..1). Clips that fail stay out of the map (their fast matte is used).
export async function clipMattes(videos, onProgress) {
  const out = new Map(), list = [...new Set(videos)].filter(Boolean);
  for (const [i, v] of list.entries()) {
    try { out.set(v, await clipMatte(v, (k, n) => onProgress?.((i + Math.min(1, k / n)) / list.length))); }
    catch (e) { console.warn("[clipmatte]", e); if (/still-/.test(e?.message)) break; }   // model off / failed: none will work
  }
  onProgress?.(1);
  return out;
}
