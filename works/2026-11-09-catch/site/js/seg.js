// Real creatures: seg-worker.js (DeepLab) finds people / dogs / cats in the camera frame every >= 300 ms, and cuts the
// one that was hit out of a frozen full-resolution frame. One worker for both, so the find loop pauses during a cut.
import { createRefiner } from "./refine.js";
import { track, widen, square } from "./geom.js";

// ?cpu (headless tests on a busy PC: ~1-2.6 s a frame) stretches the 3 s / 4 s limits x3; phones keep the plan's numbers
const CPU = new URLSearchParams(location.search).has("cpu"), SLOW = CPU ? 3 : 1;
const EVERY = 300, WARM_MS = 30000, CALL_MS = 3000 * SLOW, CUT_MS = 4000 * SLOW, LONG = 320, OUT = 256, WORK = 512;
let W = null, src = null, ready = false, busy = false, paused = false, late = 0, rebuilds = 0, nid = 0, lastAt = 0;
const cbs = new Map(), T = { person: null, animal: null };
export const stats = { ready: false, warmMs: 0, ms: 0, finds: 0, timeouts: 0, rebuilds: 0, cut: "", off: "", err: "" };

/** Eye-contact check goes here later (brief: a person only counts when looking at the camera). Now everyone counts. */
export const gate = kind => true;

function call(msg, transfer, ms) {
  return new Promise((ok, no) => {
    const id = ++nid;
    const t = setTimeout(() => { if (cbs.delete(id)) no(new Error("timeout")); }, ms);   // a wasm abort raises no error event
    cbs.set(id, d => { clearTimeout(t); ok(d); });
    W.postMessage({ id, ...msg }, transfer);
  });
}

function make() {
  W = new Worker(new URL(CPU ? "./seg-worker.js?cpu" : "./seg-worker.js", import.meta.url));
  W.onmessage = ({ data }) => { const f = cbs.get(data.id); cbs.delete(data.id); f?.(data); };
  W.onerror = e => { e.preventDefault(); rebuild("error: " + (e.message || "load")); };
  ready = stats.ready = false;
  // warm-up: wasm + model download and the first GPU compile (several seconds) on a black frame, before any ring shows
  const c = document.createElement("canvas"); c.width = LONG; c.height = 240;
  c.getContext("2d").fillRect(0, 0, LONG, 240);
  const t0 = performance.now(), w = W;
  createImageBitmap(c).then(img => call({ mode: "find", img }, [img], WARM_MS)).then(d => {
    if (w !== W) return;
    if (d.error) return rebuild("warm: " + d.error);
    ready = stats.ready = true; stats.warmMs = Math.round(performance.now() - t0);
  }, () => { if (w === W) rebuild("warm timeout"); });
}

function rebuild(why) {
  W.terminate();
  for (const f of cbs.values()) f({ error: why });
  cbs.clear(); T.person = T.animal = null; late = 0;
  stats.rebuilds = ++rebuilds;
  // ponytail: gives up for the page after 3 rebuilds (CDN down, no wasm); a reload tries again
  if (rebuilds > 3) { W = false; ready = stats.ready = false; stats.off = why; return; }
  make();
}

/** Start finding in this video (the camera, or the ?still= stream). Safe to call again. */
export function start(video) {
  src = video;
  if (W === null) make();
}

/** Call every frame: sends the next find when the last one is back and 300 ms have passed. */
export function tick(now) {
  if (!W || !ready || busy || paused || !src?.videoWidth || now - lastAt < EVERY) return;
  busy = true; lastAt = now;
  const vw = src.videoWidth, vh = src.videoHeight, k = LONG / Math.max(vw, vh);
  createImageBitmap(src, { resizeWidth: Math.round(vw * k), resizeHeight: Math.round(vh * k) })
    .then(img => call({ mode: "find", img }, [img], CALL_MS))
    .then(d => {
      late = 0;
      if (d.error) { stats.err = d.error; return; }
      stats.finds++; stats.ms = d.ms;
      T.person = track(T.person, d.person); T.animal = track(T.animal, d.animal);
    }, () => {
      if (paused) return;   // a find caught behind a cut-out is not the worker hanging
      stats.timeouts++;
      if (++late >= 3) rebuild("no answer");
      else { T.person = track(T.person, null); T.animal = track(T.animal, null); }
    })
    .finally(() => { busy = false; });
}

/** Real targets now: [{kind: "person" | "dog" | "cat", x, y, w, h}] in 0..1 of the video frame. */
export function targets() {
  const o = [];
  if (T.person && gate("person")) o.push({ ...T.person, kind: "person" });
  if (T.animal && gate(T.animal.kind)) o.push({ ...T.animal });
  return o;
}

export function clear() { T.person = T.animal = null; }

const png = c => new Promise(ok => c.toBlob(ok, "image/png"));
const canvas = (w, h) => Object.assign(document.createElement("canvas"), { width: w, height: h });

/**
 * Freeze the frame now (call it at the hit) and cut target t (0..1 box + kind) out of it:
 * resolves {blob (transparent PNG, long side 256), w, h, blurry}. Never rejects: no mask within 4 s (or any failure)
 * gives the box cropped as a rounded rectangle with blurry: true.
 */
export function cut(t) {
  const vw = src.videoWidth, vh = src.videoHeight, frame = canvas(vw, vh);
  frame.getContext("2d").drawImage(src, 0, 0, vw, vh);
  paused = true;
  const tight = { x: t.x * vw, y: t.y * vh, w: t.w * vw, h: t.h * vh }, b = square(widen(tight, vw, vh), vw, vh);
  const fallback = () => {
    const s = OUT / Math.max(tight.w, tight.h), ow = Math.max(1, Math.round(tight.w * s)), oh = Math.max(1, Math.round(tight.h * s));
    const c = canvas(ow, oh), x = c.getContext("2d");
    x.beginPath(); x.roundRect(0, 0, ow, oh, Math.min(ow, oh) * 0.18); x.clip();
    x.drawImage(frame, tight.x, tight.y, tight.w, tight.h, 0, 0, ow, oh);
    return png(c).then(blob => ({ blob, w: ow, h: oh, blurry: true }));
  };
  const work = (async () => {
    if (!W || !ready) throw new Error("not ready");
    const crop = canvas(b.w, b.h);
    crop.getContext("2d").drawImage(frame, b.x, b.y, b.w, b.h, 0, 0, b.w, b.h);
    const k = LONG / Math.max(b.w, b.h);
    const img = await createImageBitmap(crop, { resizeWidth: Math.max(1, Math.round(b.w * k)), resizeHeight: Math.max(1, Math.round(b.h * k)) });
    const m = await call({ mode: "cut", kind: t.kind === "person" ? "person" : "animal", img }, [img], CUT_MS);
    if (m.error || !m.data) throw new Error(m.error || "no mask");
    const s = WORK / Math.max(b.w, b.h), ow = Math.round(b.w * s), oh = Math.round(b.h * s), out = canvas(ow, oh), x = out.getContext("2d", { willReadFrequently: true });
    try {   // guided filter: the low-res mask snaps to the photo's edges (fur, hair)
      const r = createRefiner();
      x.drawImage(r.refine(crop, m, { temporal: 0, guide: 1, width: ow, height: oh }), 0, 0);
      r.canvas.getContext("webgl2")?.getExtension("WEBGL_lose_context")?.loseContext();
      stats.cut = "refined";
    } catch {   // no WebGL2 / float targets: the model's own mask as alpha
      const mc = canvas(m.width, m.height), id = mc.getContext("2d").createImageData(m.width, m.height);
      for (let i = 0; i < m.data.length; i++) id.data[i * 4 + 3] = m.data[i] * 255;
      mc.getContext("2d").putImageData(id, 0, 0);
      x.drawImage(crop, 0, 0, ow, oh);
      x.globalCompositeOperation = "destination-in"; x.drawImage(mc, 0, 0, ow, oh);
      stats.cut = "mask";
    }
    // trim to what is left (the square crop has room around it), long side 256
    const a = x.getImageData(0, 0, ow, oh).data;
    let x0 = ow, y0 = oh, x1 = -1, y1 = -1, n = 0;
    for (let i = 3, p = 0; i < a.length; i += 4, p++) if (a[i] > 8) { const px = p % ow, py = (p - px) / ow; n++; if (px < x0) x0 = px; if (px > x1) x1 = px; if (py < y0) y0 = py; if (py > y1) y1 = py; }
    if (n < ow * oh * 0.02) throw new Error("empty cut");
    const tw = x1 - x0 + 1, th = y1 - y0 + 1, f = OUT / Math.max(tw, th), fw = Math.max(1, Math.round(tw * f)), fh = Math.max(1, Math.round(th * f)), fin = canvas(fw, fh);
    fin.getContext("2d").drawImage(out, x0, y0, tw, th, 0, 0, fw, fh);
    return { blob: await png(fin), w: fw, h: fh, blurry: false };
  })();
  const limit = new Promise((_, no) => setTimeout(() => no(new Error("timeout")), CUT_MS));
  return Promise.race([work, limit])
    .catch(e => { stats.cut = "fallback: " + (e?.message || e); return fallback(); })
    .finally(() => { paused = false; });
}
