// Person cut-out: MediaPipe selfie_segmenter + refinement on the GPU.
// Refinement per frame: temporal EMA on the confidence (motion-adaptive, so moving edges do not ghost),
// then a joint bilateral filter guided by the full-res frame (the 256 px mask snaps to real colour edges),
// then smoothstep levels; the soft remainder of the smoothstep is the ~1-2 px feather.
const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const MODEL = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite";
// selfie_segmenter has a single output: confidenceMasks[0] = person probability (model card; pinned by a fake-camera
// run 10-26: centre of person-a 0.99+, wall corners < 0.01). No polarity guess.
const PERSON = 0;
import { createRefiner } from "./refine.js";
// engine 0 = joint bilateral below (default: kept braids on person-a, A/B 10-26), 1 = research guided filter
// (refine.js, copied from research/cutout-poc; softer, lost braid tips and smeared on motion blur)
// still = 1 (default; ?still=0 turns it off) cuts each shutter still with MODNet (see the stills section below);
// slo/shi = its linear alpha levels, scpu = 1 forces the WASM backend (tests only: 9-15 s per still).
export const TUNE = { engine: 0, ema: 0.6, lo: 0.3, hi: 0.85, sigmaR: 0.08, maxSide: 960, glo: 0.15, ghi: 0.85,
  still: new URLSearchParams(location.search).get("still") === "0" ? 0 : 1, slo: 0.03, shi: 0.97, scpu: 0 };
export const stats = { delegate: "", warmMs: 0, held: 0, ms: [], maskW: 0, maskH: 0, masks: 0, webgl: false,
  still: { state: "", delegate: "", loadMs: 0, warmMs: 0, segMs: 0, refineMs: 0, error: "", cuts: [] } };

let seg = null, lastTime = -1, lastTs = 0, ema = null, emaSum = 0, held = 0, m8 = null, mw = 0, mh = 0, ready = false;
// The model input is 256 px anyway: feed it a 256-wide copy so the mask comes back at 256x192 instead of the video size
// (a 1280x960 phone frame read back 4.9 MB of floats per frame). The bilateral pass upsamples it against the full frame.
const small = document.createElement("canvas"), smallX = small.getContext("2d");
function toSmall(video) {
  const w = 256, h = Math.round(256 * video.videoHeight / video.videoWidth);
  if (small.width !== w || small.height !== h) { small.width = w; small.height = h; }
  smallX.drawImage(video, 0, 0, w, h);
  return small;
}

export async function loadSegmenter() {
  if (seg) return seg;
  prefetchStill();   // HQ still model (no-op when the landing page already started it)
  const { FilesetResolver, ImageSegmenter } = await import(MP + "/vision_bundle.mjs");
  const files = await FilesetResolver.forVisionTasks(MP + "/wasm");
  const mk = delegate => ImageSegmenter.createFromOptions(files, { baseOptions: { modelAssetPath: MODEL, delegate },
    runningMode: "VIDEO", outputConfidenceMasks: true, outputCategoryMask: false });
  try { seg = await mk("GPU"); stats.delegate = "GPU"; } catch { seg = await mk("CPU"); stats.delegate = "CPU"; }
  // warm-up: the first GPU call compiles the shaders (~10 s on a PC iGPU). Pay it here, behind the "누끼 준비 중" wait,
  // not on the first preview frame. Same 256x192 input size as segmentFrame, so nothing is compiled again later.
  const t0 = performance.now(), w = Object.assign(document.createElement("canvas"), { width: 256, height: 192 });
  w.getContext("2d").fillRect(0, 0, 256, 192);
  lastTs = performance.now();
  seg.segmentForVideo(w, lastTs, r => r.confidenceMasks[PERSON].getAsFloat32Array());
  stats.warmMs = Math.round(performance.now() - t0);
  return seg;
}

// Runs the model once per video frame (callers may share one video: the same frame is not segmented twice).
export function segmentFrame(video, tsMs = performance.now()) {
  if (!seg || !video.videoWidth || video.currentTime === lastTime) return false;
  // while the HQ still model compiles its WebGPU shaders (~10 s cold on a PC iGPU) the GPU is busy, and this model's
  // synchronous mask readback would block the main thread for the whole time (frozen preview, 10-27): keep the last
  // matte instead until the warm-up is done
  if (warming) return false;
  lastTime = video.currentTime;
  const ts = Math.max(tsMs, lastTs + 1); lastTs = ts;
  const t0 = performance.now();
  seg.segmentForVideo(toSmall(video), ts, res => {
    stats.masks = res.confidenceMasks.length;
    const m = res.confidenceMasks[PERSON], a = m.getAsFloat32Array();
    if (m.width !== mw || m.height !== mh || !ema) {
      mw = stats.maskW = m.width; mh = stats.maskH = m.height;
      ema = Float32Array.from(a); m8 = new Uint8Array(a.length); emaSum = 0; held = 0;
    }
    // dropout guard: the person losing over 60 % of their area in one frame is a model miss (a flash, a blur), not
    // motion; keep the previous matte for up to 3 frames instead of cutting the person out of the clip (10-27 ghost)
    let sum = 0;
    for (let i = 0; i < a.length; i++) sum += a[i];
    if (emaSum > 0.02 * a.length && sum < 0.4 * emaSum && held < 3) { held++; stats.held++; return; }
    held = 0;
    const k0 = TUNE.ema;
    let es = 0;
    for (let i = 0; i < a.length; i++) {
      const d = Math.abs(a[i] - ema[i]);
      const k = d >= 0.25 ? 0 : k0 * (1 - 4 * d);  // big change = real motion: follow it now (no ghost)
      const v = ema[i] = ema[i] * k + a[i] * (1 - k);
      es += v; m8[i] = v * 255 + 0.5;
    }
    emaSum = es;
  });
  if (TUNE.engine === 1 && guided()) {
    const s = Math.min(1, TUNE.maxSide / Math.max(video.videoWidth, video.videoHeight));
    gref.refine(video, { data: m8, width: mw, height: mh }, { temporal: 0, lo: TUNE.glo, hi: TUNE.ghi,
      width: Math.round(video.videoWidth * s), height: Math.round(video.videoHeight * s) });
  } else refine(video);
  ready = true;
  stats.ms.push(performance.now() - t0);
  if (stats.ms.length > 120) stats.ms.shift();
  return true;
}

export function hasMatte() { return ready; }

// Stills: keep only the largest person (+ any at least 40 % of its size), so a passer-by is not cut out too.
// Works on a 64x48 copy of the alpha (4-connected BFS, < 1 ms), dilated one cell and smoothed back up.
export function keepLargest(c) {
  const W = 64, H = 48, s = Object.assign(document.createElement("canvas"), { width: W, height: H });
  const x = s.getContext("2d", { willReadFrequently: true });
  x.drawImage(c, 0, 0, W, H);
  const d = x.getImageData(0, 0, W, H), px = d.data, lab = new Int32Array(W * H), area = [0];
  for (let i = 0; i < W * H; i++) {
    if (lab[i] || px[i * 4 + 3] < 128) continue;
    const id = area.length, q = [i]; lab[i] = id; let n = 0;
    while (q.length) {
      const j = q.pop(), jx = j % W; n++;
      for (const k of [jx > 0 && j - 1, jx < W - 1 && j + 1, j - W, j + W])
        if (k !== false && k >= 0 && k < W * H && !lab[k] && px[k * 4 + 3] >= 128) { lab[k] = id; q.push(k); }
    }
    area.push(n);
  }
  if (area.length <= 2) return c;                       // zero or one person: nothing to drop
  const max = Math.max(...area), keep = area.map(n => n >= 0.4 * max);
  if (keep.slice(1).every(Boolean)) return c;
  for (let i = 0; i < W * H; i++) {
    const ix = i % W, iy = (i / W) | 0;
    let on = false;
    for (let dy = -1; dy <= 1 && !on; dy++) for (let dx = -1; dx <= 1 && !on; dx++) {
      const X = ix + dx, Y = iy + dy;
      on = X >= 0 && X < W && Y >= 0 && Y < H && keep[lab[Y * W + X]] && lab[Y * W + X] > 0;
    }
    px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = 255; px[i * 4 + 3] = on ? 255 : 0;
  }
  x.putImageData(d, 0, 0);
  const cx = c.getContext("2d");
  cx.save(); cx.globalCompositeOperation = "destination-in"; cx.imageSmoothingQuality = "high";
  cx.drawImage(s, 0, 0, c.width, c.height); cx.restore();
  return c;
}

let gref = null;   // EMA above already smooths, so refine.js runs with temporal 0
function guided() {
  if (gref === null) { try { gref = createRefiner(); stats.webgl = true; } catch { gref = false; } }
  return !!gref;
}
function drawMirrored(ctx, src, w, h, mirror) {
  ctx.save(); if (mirror) { ctx.translate(w, 0); ctx.scale(-1, 1); }
  ctx.drawImage(src, 0, 0, w, h); ctx.restore();
}
let gm = null;     // guided matte: the cut-out's alpha painted white
function guidedMatte() {
  const c = gref.canvas;
  if (!gm) gm = document.createElement("canvas");
  if (gm.width !== c.width || gm.height !== c.height) { gm.width = c.width; gm.height = c.height; }
  const x = gm.getContext("2d");
  x.globalCompositeOperation = "copy"; x.drawImage(c, 0, 0);
  x.globalCompositeOperation = "source-in"; x.fillStyle = "#fff"; x.fillRect(0, 0, gm.width, gm.height);
  x.globalCompositeOperation = "destination-over"; x.fillStyle = "#000"; x.fillRect(0, 0, gm.width, gm.height);
  return gm;
}

// ---- GPU refinement ---------------------------------------------------------------------------------------------
let gl = null, cv = null, P = {}, tex = {}, fbo = null, fw = 0, fh = 0, quad = null, fb2d = null;

const VS = `#version 300 es
in vec2 p; void main(){ gl_Position = vec4(p, 0., 1.); }`;
// Pass 1: alpha at frame resolution, same orientation as the frame (row 0 = top).
const FS_REFINE = `#version 300 es
precision highp float;
uniform sampler2D uImg, uMask; uniform vec2 uSize; uniform float uStep, uLo, uHi, uR;
out vec4 o;
void main(){
  vec2 px = 1.0 / uSize, uv = gl_FragCoord.xy * px;
  vec3 c0 = texture(uImg, uv).rgb;
  float s = 0., ws = 0.;
  for (int y = -3; y <= 3; y++) for (int x = -3; x <= 3; x++) {
    vec2 q = uv + vec2(x, y) * uStep * px;
    vec3 dc = texture(uImg, q).rgb - c0;
    float w = exp(-float(x * x + y * y) / 8.0 - dot(dc, dc) / (2.0 * uR * uR));
    s += w * texture(uMask, q).r; ws += w;
  }
  o = vec4(smoothstep(uLo, uHi, s / ws), 0., 0., 1.);
}`;
// Pass 2: to the canvas, flipped right side up, optional mirror. mode 0 = matte, 1 = cut-out (premultiplied).
const FS_DRAW = `#version 300 es
precision highp float;
uniform sampler2D uImg, uA; uniform vec2 uSize; uniform float uMirror; uniform int uMode;
out vec4 o;
void main(){
  vec2 uv = gl_FragCoord.xy / uSize; uv.y = 1.0 - uv.y;
  if (uMirror > 0.5) uv.x = 1.0 - uv.x;
  float a = texture(uA, uv).r;
  if (uMode == 0) { o = vec4(a, a, a, 1.0); return; }
  vec3 c = texture(uImg, uv).rgb;
  if (a > 0.0 && a < 0.98) {
    // edge pixel: its colour is part wall, so take the colour of the solid person pixels around it (no light rim)
    vec2 px = 1.0 / uSize; vec3 s = vec3(0.); float ws = 0.;
    for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
      vec2 q = uv + vec2(x, y) * 2.0 * px;
      float b = texture(uA, q).r; b *= b; b *= b;
      s += b * texture(uImg, q).rgb; ws += b;
    }
    if (ws > 0.05) c = s / ws;
  }
  o = vec4(c * a, a);
}`;

function initGL() {
  cv = document.createElement("canvas");
  gl = cv.getContext("webgl2", { premultipliedAlpha: true, alpha: true, antialias: false, preserveDrawingBuffer: false });
  if (!gl) return false;
  const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; };
  for (const [k, fs] of [["refine", FS_REFINE], ["draw", FS_DRAW]]) {
    const p = gl.createProgram();
    gl.attachShader(p, sh(gl.VERTEX_SHADER, VS)); gl.attachShader(p, sh(gl.FRAGMENT_SHADER, fs));
    gl.bindAttribLocation(p, 0, "p"); gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    const u = {}; for (const n of ["uImg", "uMask", "uA", "uSize", "uStep", "uLo", "uHi", "uR", "uMirror", "uMode"]) u[n] = gl.getUniformLocation(p, n);
    P[k] = { p, u };
  }
  quad = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, quad);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
  for (const k of ["img", "mask", "a"]) {
    tex[k] = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex[k]);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  }
  fbo = gl.createFramebuffer();
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
  stats.webgl = true;
  return true;
}

function refine(video) {
  if (gl === null && !initGL()) gl = false;
  if (!gl) return refine2d();
  const vw = video.videoWidth, vh = video.videoHeight, s = Math.min(1, TUNE.maxSide / Math.max(vw, vh));
  const w = Math.round(vw * s), h = Math.round(vh * s);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex.img);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, tex.mask);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.R8, mw, mh, 0, gl.RED, gl.UNSIGNED_BYTE, m8);
  gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, tex.a);
  if (w !== fw || h !== fh) {
    fw = w; fh = h;
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex.a, 0);
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
  gl.bindTexture(gl.TEXTURE_2D, null);   // tex.a is the render target in this pass
  const { p, u } = P.refine; gl.useProgram(p);
  gl.uniform1i(u.uImg, 0); gl.uniform1i(u.uMask, 1);
  gl.uniform2f(u.uSize, w, h);
  gl.uniform1f(u.uStep, Math.max(1, w / 256));   // tap spacing ~ one model texel (256 px input)
  gl.uniform1f(u.uLo, TUNE.lo); gl.uniform1f(u.uHi, TUNE.hi); gl.uniform1f(u.uR, TUNE.sigmaR);
  gl.viewport(0, 0, w, h);
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
}

function render(mode, mirror) {
  if (cv.width !== fw || cv.height !== fh) { cv.width = fw; cv.height = fh; }
  gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, tex.a);
  const { p, u } = P.draw; gl.useProgram(p);
  gl.uniform1i(u.uImg, 0); gl.uniform1i(u.uA, 2);
  gl.uniform2f(u.uSize, fw, fh); gl.uniform1f(u.uMirror, mirror ? 1 : 0); gl.uniform1i(u.uMode, mode);
  gl.viewport(0, 0, fw, fh);
  gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  return cv;
}

// ---- 2D fallback (no WebGL2): EMA mask only, no edge-aware step -----------------------------------------------------
function refine2d() {
  if (!fb2d) { fb2d = document.createElement("canvas"); fb2d.ctx = fb2d.getContext("2d"); }
  if (fb2d.width !== mw || fb2d.height !== mh) { fb2d.width = mw; fb2d.height = mh; fb2d.img = fb2d.ctx.createImageData(mw, mh); }
  const d = fb2d.img.data;
  for (let i = 0; i < m8.length; i++) { const v = m8[i]; d[i * 4] = d[i * 4 + 1] = d[i * 4 + 2] = 255; d[i * 4 + 3] = v; }
  fb2d.ctx.putImageData(fb2d.img, 0, 0);
}
function draw2d(ctx, src, w, h, mirror, op) {
  ctx.save(); ctx.globalCompositeOperation = op;
  if (mirror) { ctx.translate(w, 0); ctx.scale(-1, 1); }
  ctx.drawImage(src, 0, 0, w, h); ctx.restore();
}

// ---- public draws (both use the frame the mask was computed on) ---------------------------------------------------
export function drawMatte(ctx, w, h, mirror) {
  if (!ready) { ctx.fillStyle = "#000"; ctx.fillRect(0, 0, w, h); return; }
  if (TUNE.engine === 1 && gref) { drawMirrored(ctx, guidedMatte(), w, h, mirror); return; }
  if (gl) { ctx.drawImage(render(0, mirror), 0, 0, w, h); return; }
  ctx.save(); ctx.fillStyle = "#000"; ctx.fillRect(0, 0, w, h); ctx.restore();
  draw2d(ctx, fb2d, w, h, mirror, "source-over");
}

export function drawCutout(ctx, video, w, h, mirror) {
  if (!ready) return;
  if (TUNE.engine === 1 && gref) { drawMirrored(ctx, gref.canvas, w, h, mirror); return; }
  if (gl) { ctx.drawImage(render(1, mirror), 0, 0, w, h); return; }
  const t = drawCutout.tmp || (drawCutout.tmp = document.createElement("canvas"));
  if (t.width !== w || t.height !== h) { t.width = w; t.height = h; }
  const c = t.getContext("2d");
  draw2d(c, video, w, h, mirror, "copy"); draw2d(c, fb2d, w, h, mirror, "destination-in");
  ctx.drawImage(t, 0, 0);
}

// ---- shutter stills: MODNet portrait matting (Apache-2.0) + refine.js decontamination (research/cutout-hq/) --------
// ~26 MB ONNX on onnxruntime-web WebGPU, in a module worker so the preview never waits on it. Self-hosted under
// site/models/ (LICENSE-MODNET.txt) and kept in Cache Storage by the worker, so a revisit does not download it again.
// Round 4: prefetchStill() = download (or Cache Storage) + session, no camera needed: call it on the landing page and/or
// while shooting. warmStill() = the first real-size run, which compiles the WebGPU shaders and freezes the page for a
// few seconds: call it when the Pick screen opens. cutStill then runs on the 4 picked stills (it warms up itself when
// warmStill was not called). When anything fails / there is no WebGPU, the picked stills keep the live matte. WASM runs 9-15 s per still on the PC (single thread without cross-origin isolation): not used.
// MODNet's alpha already follows hair, so refine.js runs with guide 0 (linear levels + edge colour decontamination).
// 512x384 input (short side 384): 0.81-0.9 s per still instead of 1.33-1.5 s at 672x512, same look (research/cutout-speed/)
const STILL_MODEL = { url: new URL("../models/modnet.onnx", import.meta.url).href, cache: "booth-modnet-v1",
  size: { short: 384, div: 32 }, mean: [0.5, 0.5, 0.5], std: [0.5, 0.5, 0.5], out: "alpha" };
// The first WebGPU run compiles shaders (3-10 s on a PC iGPU) and stalls the GPU process meanwhile (no new frames
// anywhere on the page, any canvas readback waits: 10-27): that is warmStill(), never during shooting.
let stillW = null, stillRef = null, stillP = null, warmP = null, stillOk = false, wid = 0, warming = null;
export function stillBusy() {}   // Round 3 hook, kept for callers: nothing runs on its own any more

export const stillWarm = () => warming || Promise.resolve();
const wcb = new Map();
function wcall(msg, transfer = [], ms = 0) {
  return new Promise((res, rej) => {
    const id = ++wid, t = ms && setTimeout(() => { wcb.delete(id); rej(new Error("still-timeout")); }, ms);
    wcb.set(id, m => { clearTimeout(t); m.error ? rej(new Error(m.error)) : res(m); });
    stillW.postMessage({ ...msg, id }, transfer);
  });
}
const bitmap = c => createImageBitmap(c);

export async function loadStillSegmenter() {
  const S = stats.still, t0 = performance.now();
  S.state = "loading"; S.model = "modnet";
  stillRef = createRefiner();   // throws without WebGL2 + float targets: stills stay on the live matte
  stillW = new Worker(new URL("./matting-worker.js", import.meta.url), { type: "module" });
  stillW.onmessage = ({ data }) => { const f = wcb.get(data.id); wcb.delete(data.id); f?.(data); };
  stillW.onerror = e => { e.preventDefault(); for (const f of wcb.values()) f({ error: "worker: " + (e.message || "load") }); wcb.clear(); };
  const L = await wcall({ load: STILL_MODEL, eps: TUNE.scpu ? ["wasm"] : ["webgpu"] });
  S.delegate = L.ep; S.cached = L.cached; S.fetchMs = L.fetchMs; S.createMs = L.createMs; S.bytes = L.bytes;
  S.loadMs = Math.round(performance.now() - t0); S.state = "loaded"; S.loadedAt = Math.round(performance.now());   // ms since page load
}
const fail = e => { stats.still.state = "failed"; stats.still.error = String(e?.message || e); stillW?.terminate(); return false; };

// Starts the background load (download or cache + session) once; no camera, no freeze. Resolves true when the session
// is up, false when stills cannot use the HQ model.
export function prefetchStill() {
  if (!TUNE.still) return Promise.resolve(false);
  return stillP ||= loadStillSegmenter().then(() => true, fail);
}
// The first real-size run (shader compile, a few seconds of frozen page): once, after prefetchStill. Resolves true when
// cutStill is ready.
export function warmStill() {
  return warmP ||= prefetchStill().then(async ok => {
    if (!ok) return false;
    const S = stats.still, t0 = performance.now();
    S.state = "warming";
    warming = (async () => {   // 4:3 like every still, so the warm-up compiles the same 512x384 shapes
      const w = Object.assign(document.createElement("canvas"), { width: 96, height: 72 }), b = await bitmap(w);
      stillRef.refine(w, await wcall({ img: b }, [b]), { temporal: 0, guide: 0 });
    })();
    try { await warming; } finally { warming = null; }
    S.warmMs = Math.round(performance.now() - t0);
    stillOk = true; S.state = "ready"; S.readyAt = Math.round(performance.now());
    return true;
  }).catch(fail);
}
export const stillReady = () => stillOk && !!TUNE.still;

// canvas4x3: the still as saved (mirrored as previewed) -> new canvas, person with matted, decontaminated alpha.
// Loads / warms up first when needed (call it without checking stillReady); throws when the model is off or failed, on a model error or after 6 s per still (callers then keep the live matte for that cut).
// Calls run one at a time in call order (one session, one refiner, the worker's model runs never overlap).
let cutQ = Promise.resolve();
export function cutStill(canvas4x3) {
  const p = cutQ.then(() => cutOne(canvas4x3));
  cutQ = p.catch(() => {});
  return p;
}
// Several stills in order; onEach(i, canvas | null, error) as each one lands (null = keep the live matte for that one).
export async function cutStills(canvases, onEach) {
  const out = [];
  for (const [i, c] of canvases.entries()) {
    try { out[i] = await cutStill(c); onEach?.(i, out[i], null); } catch (e) { out[i] = null; onEach?.(i, null, e); }
  }
  return out;
}
async function cutOne(canvas4x3) {
  if (!stillReady() && !(await warmStill())) throw new Error("still-" + (stats.still.state || "off"));
  const S = stats.still, t0 = performance.now(), b = await bitmap(canvas4x3);
  const m = await wcall({ img: b }, [b], 6000), t1 = performance.now();
  const g = stillRef.refine(canvas4x3, m, { temporal: 0, guide: 0, lo: TUNE.slo, hi: TUNE.shi });
  const o = Object.assign(document.createElement("canvas"), { width: canvas4x3.width, height: canvas4x3.height });
  o.getContext("2d").drawImage(g, 0, 0);   // own copy: the refiner canvas is reused by the next cut
  S.segMs = Math.round(t1 - t0); S.modelMs = m.ms; S.modelWH = `${m.width}x${m.height}`; S.refineMs = Math.round(performance.now() - t1);
  return o;
}
