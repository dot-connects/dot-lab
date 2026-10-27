// Face retouch (Round 5, ROUND5.md "Beauty"): skin smoothing + bright tone, face slim, eye enlarge; 3 levels each.
// Landmarks: MediaPipe FaceLandmarker (478 points; model self-hosted in site/models/, LICENSE-FACE-LANDMARKER.txt,
// kept in Cache Storage). The rest is one WebGL pass per image: a backward warp (slim = the lower face squeezed toward
// its midline, more toward the jaw; eye = local magnify), then on the photo only an edge-preserving (bilateral) blur inside a feathered skin
// mask (face oval minus eyes/brows/lips) and a lift + slight pink tone. Cut-outs get the same warp and RGB treatment,
// their alpha only follows the warp. No face found / no WebGL: the input comes back unchanged.
const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const MODEL = { url: new URL("../models/face_landmarker.task", import.meta.url).href, cache: "booth-face-v1" };
export const DEFAULT_LOOK = { skin: 1, slim: 0, eye: 0 };
// per level 0/1/2. blur/feather = fractions of the face width; sr = bilateral colour sigma; mix = how much of the
// smoothed skin replaces the original; lift = screen-lift on skin, glift = on the whole photo; pink = rosy cast on skin.
// slim = how much the jaw line is squeezed toward the midline (fraction of its offset); eye = magnify amount at the eye centre.
export const TUNE = {
  skin: [null,
    { blur: 0.03, sr: 0.075, mix: 0.75, lift: 0.07, glift: 0.025, pink: 0.6 },
    { blur: 0.045, sr: 0.1, mix: 0.88, lift: 0.1, glift: 0.045, pink: 1 }],
  slim: [0, 0.08, 0.15], eye: [0, 0.1, 0.17], feather: 0.035, maxFaces: 3,
};
const lv = v => (Number.isInteger(v) && v >= 0 && v <= 2 ? v : 0);
const norm = l => ({ skin: lv(l?.skin), slim: lv(l?.slim), eye: lv(l?.eye) });
export const lookOn = l => { const n = norm(l); return !!(n.skin || n.slim || n.eye); };
export const lookKey = l => { const n = norm(l); return `${n.skin}${n.slim}${n.eye}`; };

// ---- landmarks ----------------------------------------------------------------------------------------------------
const OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176,
  149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109];
const EYE_L = [33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246];
const EYE_R = [263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466];
const BROW_L = [70, 63, 105, 66, 107, 55, 65, 52, 53, 46], BROW_R = [336, 296, 334, 293, 300, 276, 283, 282, 295, 285];
const LIPS = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185];

async function modelBytes() {
  const box = self.caches ? await caches.open(MODEL.cache).catch(() => null) : null;
  let res = await box?.match(MODEL.url).catch(() => null);
  if (!res) {
    res = await fetch(MODEL.url, { cache: "force-cache" });
    if (!res.ok) throw new Error("face model http " + res.status);
    if (box) await box.put(MODEL.url, res.clone()).catch(() => {});   // quota / private mode: just not cached
  }
  return new Uint8Array(await res.arrayBuffer());
}
let visionP, filesP, bytesP, imageLM, videoLM, fxWarm = false;
async function landmarker(mode) {
  visionP ||= import(MP + "/vision_bundle.mjs");
  const { FilesetResolver, FaceLandmarker } = await visionP;
  filesP ||= FilesetResolver.forVisionTasks(MP + "/wasm");
  const [files, bytes] = await Promise.all([filesP, bytesP ||= modelBytes()]);
  const mk = delegate => FaceLandmarker.createFromOptions(files, { baseOptions: { modelAssetBuffer: bytes.slice(), delegate },
    runningMode: mode, numFaces: TUNE.maxFaces, minFaceDetectionConfidence: 0.4, minFacePresenceConfidence: 0.4 });
  try { return await mk("GPU"); } catch { return mk("CPU"); }
}
// model download + both landmarkers, no image needed: call early (review screen) so the first preview is quick
export function prefetchBeauty() {
  imageLM ||= landmarker("IMAGE");
  return warmP ||= imageLM.then(lm => {   // one blank run compiles the detector's GPU shaders
    lm.detect(Object.assign(document.createElement("canvas"), { width: 64, height: 64 }));
    return true;
  }).catch(() => false);
}
let warmP;
const faceCache = new WeakMap();      // image -> landmarks, so changing levels never runs the model again
export async function facesOf(img) {
  if (!faceCache.has(img)) faceCache.set(img, (async () => (await (imageLM ||= landmarker("IMAGE"))).detect(img).faceLandmarks || [])());
  return faceCache.get(img);
}

// ---- control points (pixels of one image / one half) ----------------------------------------------------------
// eye:  P[i] = (centre x, y, radius, 1), M[i] = (amount)
// slim: P[i] = (between-the-eyes x, y, half face width, 2), M[i] = (vector to the chin x, y, amount)
const N = 48;
function plan(faces, w, h, look) {
  const P = new Float32Array(N * 4), M = new Float32Array(N * 4);
  let n = 0, fw = 0;
  const pt = (f, i) => [f[i].x * w, f[i].y * h];
  const put = (x, y, r, kind, dx, dy, z = 0) => { if (n < N) { P.set([x, y, r, kind], n * 4); M.set([dx, dy, z, 0], n * 4); n++; } };
  for (const f of faces) {
    const [lx, ly] = pt(f, 234), [rx, ry] = pt(f, 454), W = Math.hypot(rx - lx, ry - ly);
    fw = Math.max(fw, W);
    const s = TUNE.slim[look.slim];
    if (s) {   // one smooth squeeze of the lower face toward its midline: origin between the eyes, axis down to the chin
      const [ox, oy] = pt(f, 168), [cx, cy] = pt(f, 152);
      put(ox, oy, W / 2, 2, cx - ox, cy - oy, s);
    }
    const a = TUNE.eye[look.eye];
    if (a) for (const [E, o, i] of [[EYE_L, 33, 133], [EYE_R, 263, 362]]) {
      let x = 0, y = 0;
      for (const k of E) { x += f[k].x * w; y += f[k].y * h; }
      const [ax, ay] = pt(f, o), [bx, by] = pt(f, i);
      put(x / E.length, y / E.length, Math.hypot(bx - ax, by - ay) * 1.05, 1, a, 0);
    }
  }
  return { P, M, n, fw };
}
// skin mask at 1/4 size: face oval minus eyes (wide), brows and lips; the shader feathers it
function mask(faces, w, h) {
  const k = 0.25, c = Object.assign(document.createElement("canvas"), { width: Math.ceil(w * k), height: Math.ceil(h * k) });
  const x = c.getContext("2d"), path = (f, ids) => { x.beginPath(); ids.forEach((i, n) => x[n ? "lineTo" : "moveTo"](f[i].x * c.width, f[i].y * c.height)); x.closePath(); };
  x.fillStyle = "#000"; x.fillRect(0, 0, c.width, c.height);
  x.lineJoin = "round";
  for (const f of faces) {
    const W = Math.hypot((f[454].x - f[234].x) * c.width, (f[454].y - f[234].y) * c.height);
    x.fillStyle = "#fff"; path(f, OVAL); x.fill();
    x.fillStyle = x.strokeStyle = "#000";
    for (const [ids, lw] of [[EYE_L, 0.07], [EYE_R, 0.07], [BROW_L, 0.035], [BROW_R, 0.035], [LIPS, 0.03]]) {
      path(f, ids); x.lineWidth = W * lw; x.fill(); x.stroke();
    }
  }
  return c;
}

// ---- WebGL ----------------------------------------------------------------------------------------------------------
const FS = `precision highp float;
varying vec2 uv;
uniform sampler2D img, msk;
uniform vec2 size;             // texture px (2w x h for a [raw | matte] clip frame)
uniform float halves, rad, sr, mixk, lift, glift, pink, fe;
uniform vec4 P[${N}]; uniform vec4 M[${N}]; uniform int n;
float mk(vec2 u, float f){     // feathered skin mask: 3x3 taps f apart on the bilinear 1/4-size mask
  float s = 0.0;
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) s += texture2D(msk, u + vec2(float(x), float(y)) * f).r;
  return s / 9.0;
}
void main(){
  float hw = halves > 0.5 ? size.x * 0.5 : size.x;
  vec2 p = uv * size;
  float side = p.x >= hw ? 1.0 : 0.0;
  vec2 l = vec2(p.x - side * hw, p.y), d = vec2(0.0);
  for (int i = 0; i < ${N}; i++) {
    if (i >= n) break;
    vec2 v = l - P[i].xy;
    if (P[i].w > 1.5) {          // slim: squeeze across the face axis, growing from the cheekbones to the jaw
      vec2 D = M[i].xy, R = normalize(vec2(-D.y, D.x));
      float t = dot(v, D) / dot(D, D), u = dot(v, R) / P[i].z;
      float c = M[i].z * smoothstep(0.15, 0.85, t) * (1.0 - smoothstep(1.0, 1.35, t)) * (1.0 - smoothstep(0.9, 1.7, abs(u)));
      d += R * u * P[i].z * c;
      continue;
    }
    float q = dot(v, v) / (P[i].z * P[i].z);
    if (q < 1.0) { float f = (1.0 - q) * (1.0 - q); d -= v * M[i].x * f; }   // eye: magnify
  }
  vec2 s = clamp(l + d, vec2(0.5), vec2(hw, size.y) - 0.5);
  vec2 su = vec2((s.x + side * hw) / size.x, s.y / size.y);
  vec4 c = texture2D(img, su);
  if (side < 0.5 && mixk > 0.0) {
    vec2 mu = s / vec2(hw, size.y), fu = vec2(fe / hw, fe / size.y);
    float m = mk(mu, fu.x), mt = mk(mu, fu.x * 2.5);
    vec3 acc = c.rgb; float ws = 1.0;
    for (int r = 1; r <= 3; r++) for (int a = 0; a < 8; a++) {
      float ang = (float(a) + 0.5 * float(r)) * 0.785398, rr = rad * float(r) / 3.0;
      vec4 t = texture2D(img, su + vec2(cos(ang), sin(ang)) * rr / size);
      vec3 e = t.rgb - c.rgb;
      float w = exp(-dot(e, e) / (2.0 * sr * sr) - float(r * r) / 6.0) * t.a;
      acc += t.rgb * w; ws += w;
    }
    c.rgb = mix(c.rgb, acc / ws, m * mixk);
    float k = glift + lift * mt;
    c.rgb = min(c.rgb * (1.0 + k * 0.6), 1.0);           // half exposure (keeps colour on darker skin) ...
    c.rgb = 1.0 - (1.0 - c.rgb) * (1.0 - k * 0.5);        // ... half screen lift (the airy booth look)
    c.rgb = clamp(c.rgb + pink * mt * vec3(0.018, -0.008, 0.006), 0.0, 1.0);
  }
  gl_FragColor = c;
}`;
let glP;
function gpu() {
  return glP ||= Promise.resolve().then(() => {
    const cv = document.createElement("canvas");
    const opt = { premultipliedAlpha: false, preserveDrawingBuffer: true, alpha: true, antialias: false };
    const gl = cv.getContext("webgl", opt);
    if (!gl) throw new Error("webgl");
    const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o);
      if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
    const pr = gl.createProgram();
    gl.attachShader(pr, sh(gl.VERTEX_SHADER, "attribute vec2 a; varying vec2 uv; void main(){ uv = vec2(a.x * 0.5 + 0.5, 0.5 - a.y * 0.5); gl_Position = vec4(a, 0.0, 1.0); }"));
    gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, FS));
    gl.linkProgram(pr);
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    gl.useProgram(pr);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    const U = name => gl.getUniformLocation(pr, name);
    const tex = unit => { const t = gl.createTexture(); gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t);
      for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
      return t; };
    const ti = tex(0), tm = tex(1);
    gl.uniform1i(U("img"), 0); gl.uniform1i(U("msk"), 1);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    // src: image/canvas (w x h, or 2hw x h with halves); faces in the coordinates of one half
    return (src, w, h, halves, faces, look) => {
      const hw = halves ? w >> 1 : w, pl = plan(faces, hw, h, look), sk = TUNE.skin[look.skin];
      if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
      gl.viewport(0, 0, w, h);
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, ti);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
      if (sk) { gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, tm);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, mask(faces, hw, h)); }
      gl.uniform2f(U("size"), w, h); gl.uniform1f(U("halves"), halves ? 1 : 0);
      gl.uniform1f(U("rad"), (sk?.blur || 0) * pl.fw); gl.uniform1f(U("sr"), sk?.sr || 1); gl.uniform1f(U("mixk"), sk?.mix || 0);
      gl.uniform1f(U("lift"), sk?.lift || 0); gl.uniform1f(U("glift"), sk?.glift || 0); gl.uniform1f(U("pink"), sk?.pink || 0);
      gl.uniform1f(U("fe"), TUNE.feather * pl.fw);
      gl.uniform4fv(U("P"), pl.P); gl.uniform4fv(U("M"), pl.M); gl.uniform1i(U("n"), pl.n);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      return cv;
    };
  });
}

const dims = o => [o.naturalWidth || o.videoWidth || o.width, o.naturalHeight || o.videoHeight || o.height];
function copy(src) {
  const [w, h] = dims(src), c = Object.assign(document.createElement("canvas"), { width: w, height: h });
  c.getContext("2d").drawImage(src, 0, 0, w, h);
  return c;
}
export const stats = { stillMs: [], fxMs: [], detectMs: [] };

// one photo + its cut-out (same framing): the same warp on both, skin/tone on both RGBs; canvases out
export async function beautyStill(raw, cut, look) {
  const l = norm(look);
  if (!lookOn(l)) return { raw: copy(raw), cut: copy(cut) };
  const t0 = performance.now();
  try {
    const faces = await facesOf(raw);
    if (!faces.length) return { raw: copy(raw), cut: copy(cut) };
    const run = await gpu(), t1 = performance.now();
    const [w, h] = dims(raw), [cw, ch] = dims(cut);
    const r = copy(run(raw, w, h, false, faces, l)), c = copy(run(cut, cw, ch, false, faces, l));
    stats.stillMs.push(Math.round(performance.now() - t1));
    return { raw: r, cut: c };
  } catch (e) {
    console.warn("[booth] beauty", e);
    return { raw: copy(raw), cut: copy(cut) };
  } finally { stats.detectMs.push(Math.round(performance.now() - t0)); }
}

// moving clips: fx(frame, tMs, key) edits one [raw | matte] frame canvas in place (landmarks on the raw half).
// key = the clip's <video> (compose), tMs = its media time. Landmarks cost ~45 ms a call (headless GPU), so they are
// sampled every GAP frames per clip and cached for the page (they do not depend on the look): between two samples
// they are interpolated, past the last one held. The 2 s clips loop, so after one pass a frame costs only the warp.
// One VIDEO landmarker for the page; timestamps only go up.
const GAP = 3, lmCache = new WeakMap();
let vts = 0;
const lerpFaces = (a, b, k) => a.length !== b.length ? (k < 0.5 ? a : b)
  : a.map((f, i) => f.map((p, j) => ({ x: p.x + (b[i][j].x - p.x) * k, y: p.y + (b[i][j].y - p.y) * k })));
export async function beautyFx(look) {
  const l = norm(look);
  if (!lookOn(l)) return null;
  let lm, run;
  const det = document.createElement("canvas"), dx = det.getContext("2d");
  const detect = src => { vts = Math.max(vts + 1, Math.round(performance.now())); return lm.detectForVideo(src, vts).faceLandmarks || []; };
  try {
    [lm, run] = await Promise.all([videoLM ||= landmarker("VIDEO"), gpu()]);
    if (!fxWarm) { det.width = 320; det.height = 240; dx.fillRect(0, 0, 320, 240); detect(det); fxWarm = true; }   // GPU compile here, not mid-strip
  } catch (e) { console.warn("[booth] beauty fx", e); return null; }
  return (frame, tMs, key = frame) => {
    const t0 = performance.now(), w = frame.width, h = frame.height, hw = w >> 1, fi = Math.round((tMs || 0) / (1000 / 30));
    if (!lmCache.has(key)) lmCache.set(key, new Map());
    const m = lmCache.get(key);
    let lo = -1, hi = -1;
    for (const k of m.keys()) { if (k <= fi && k > lo) lo = k; if (k >= fi && (hi < 0 || k < hi)) hi = k; }
    let faces;
    if (lo >= 0 && hi >= 0) faces = lo === hi ? m.get(lo) : lerpFaces(m.get(lo), m.get(hi), (fi - lo) / (hi - lo));
    else if (lo >= 0 && fi - lo < GAP) faces = m.get(lo);
    else if (hi >= 0 && hi - fi < GAP) faces = m.get(hi);
    else {
      const k = Math.min(1, 320 / hw);       // the model looks at 256 px anyway
      if (det.width !== Math.round(hw * k)) { det.width = Math.round(hw * k); det.height = Math.round(h * k); }
      dx.drawImage(frame, 0, 0, hw, h, 0, 0, det.width, det.height);
      try { faces = detect(det); } catch { return; }
      m.set(fi, faces);
      stats.detectMs.push(+(performance.now() - t0).toFixed(1));
    }
    if (!faces.length) return;
    const out = run(frame, w, h, true, faces, l), x = frame.getContext("2d");
    x.save(); x.globalCompositeOperation = "copy"; x.drawImage(out, 0, 0); x.restore();
    stats.fxMs.push(+(performance.now() - t0).toFixed(1));
  };
}
