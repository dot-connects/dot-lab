// Compose the 4-cut strip (JPG) and the moving strip (MP4/WebM) from both people's media.
// Cell = background (A's raw | B's raw | preset) + the other person's cut-out (both on a preset), positions as shot,
// light wrap on the cut-out edges, then the filter over the whole cell. Frame art goes under and over the cells.
import { createFilter } from "./filters.js";
import { FRAMES, loadFrameFonts } from "./frames.js";

export const LAYOUT = { W: 600, H: 1800, M: 24, GAP: 16, FOOT: 200, CW: 552, CH: 382 };
const L = LAYOUT;

// ---------- preset backgrounds: soft studio paper, lit from the front-top, darker floor and corners ----------
function studio(ctx, w, h, top, bottom, light, lightA, vign) {
  const g = ctx.createLinearGradient(0, 0, 0, h);
  g.addColorStop(0, top); g.addColorStop(1, bottom);
  ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  const r = ctx.createRadialGradient(w * 0.5, h * 0.42, 0, w * 0.5, h * 0.42, w * 0.62);
  r.addColorStop(0, light + Math.round(lightA * 255).toString(16).padStart(2, "0")); r.addColorStop(1, light + "00");
  ctx.fillStyle = r; ctx.fillRect(0, 0, w, h);
  const v = ctx.createRadialGradient(w * 0.5, h * 0.45, w * 0.35, w * 0.5, h * 0.45, w * 0.8);
  v.addColorStop(0, "#00000000"); v.addColorStop(1, `rgba(0,0,0,${vign})`);
  ctx.fillStyle = v; ctx.fillRect(0, 0, w, h);
}
function softPuff(ctx, x, y, r, a) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, r);
  g.addColorStop(0, `rgba(255,255,255,${a})`); g.addColorStop(0.6, `rgba(255,255,255,${a * 0.55})`); g.addColorStop(1, "rgba(255,255,255,0)");
  ctx.fillStyle = g; ctx.fillRect(x - r, y - r, r * 2, r * 2);
}
export const BACKGROUNDS = [
  { id: "a", label: "보낸 사람 배경" },
  { id: "b", label: "받은 사람 배경" },
  { id: "booth-purple", label: "보라 부스", color: "#b9a6e6", draw: (c, w, h) => studio(c, w, h, "#c6b4f0", "#9d86d8", "#f1eaff", 0.55, 0.16) },
  { id: "booth-pink", label: "분홍 부스", color: "#f7b8c9", draw: (c, w, h) => studio(c, w, h, "#ffd2de", "#ef9fb6", "#fff0f4", 0.6, 0.12) },
  { id: "white-wall", label: "흰 벽", color: "#f5f3f0", draw: (c, w, h) => studio(c, w, h, "#f6f4f1", "#e2ddd7", "#ffffff", 0.8, 0.07) },
  { id: "mono", label: "흑백 부스", color: "#a7a7a7", draw: (c, w, h) => studio(c, w, h, "#bdbdbd", "#858585", "#f2f2f2", 0.7, 0.22) },
  { id: "sky", label: "하늘", color: "#9fd3ff", draw: (c, w, h) => {
    const g = c.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, "#6fb6f5"); g.addColorStop(0.65, "#a9d8ff"); g.addColorStop(1, "#dff1ff");
    c.fillStyle = g; c.fillRect(0, 0, w, h);
    for (const [x, y, r, a] of [[0.12, 0.2, 0.16, 0.75], [0.24, 0.16, 0.12, 0.7], [0.82, 0.32, 0.18, 0.7], [0.68, 0.36, 0.12, 0.6],
      [0.95, 0.27, 0.1, 0.6], [0.45, 0.78, 0.26, 0.5], [0.1, 0.85, 0.2, 0.55], [0.85, 0.9, 0.22, 0.55]]) softPuff(c, x * w, y * h, r * w, a);
    const s = c.createRadialGradient(w * 0.82, -h * 0.1, 0, w * 0.82, -h * 0.1, w * 0.6);
    s.addColorStop(0, "rgba(255,250,235,0.55)"); s.addColorStop(1, "rgba(255,250,235,0)");
    c.fillStyle = s; c.fillRect(0, 0, w, h);
  } },
];

const frameOf = f => (typeof f === "string" ? FRAMES.find(x => x.id === f) : f) || FRAMES[0];
const fmtDate = d => {
  if (typeof d === "string") return d;
  d = d || new Date();
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

export function cellRects(frame) {
  const f = frame && frameOf(frame);
  return f?.rects || [0, 1, 2, 3].map(i => ({ x: L.M, y: L.M + i * (L.CH + L.GAP), w: L.CW, h: L.CH }));
}
export function cropRect(w, h, aspect = L.CW / L.CH) {
  if (w / h > aspect) { const sw = h * aspect; return { sx: (w - sw) / 2, sy: 0, sw, sh: h }; }
  const sh = w / aspect; return { sx: 0, sy: (h - sh) / 2, sw: w, sh };
}

export async function loadClip(blobUrl) {
  const v = document.createElement("video");
  v.muted = true; v.playsInline = true; v.loop = true; v.preload = "auto";
  v.setAttribute("playsinline", ""); v.setAttribute("muted", "");
  await new Promise((res, rej) => { v.onloadeddata = res; v.onerror = () => rej(new Error("clip")); v.src = blobUrl; });
  return v;
}


// ---------- cut-out alpha on the GPU: one shader for clips ([raw | matte] side by side) and stills (RGBA) ----------
// choke : alpha = min of 4 taps at +-choke px (erosion as a min, not a product, so soft edges stay soft)
// decon : edge pixels (0 < a < 0.98) take the a^4-weighted colour of the solid person around them, so the old wall
//         colour never shows at the edge (same math as segment.js FS_DRAW)
// gain / tint : brightness match between the two people and a light backdrop colour cast (compose only)
// Output is premultiplied. Texture edges clamp, so a person cut by the frame edge is not eroded there.
export function createClipAlpha({ choke = 0 } = {}) {
  const cv = document.createElement("canvas");
  const gl = cv.getContext("webgl", { premultipliedAlpha: true, preserveDrawingBuffer: true, alpha: true });
  if (!gl) throw new Error("webgl");
  const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o);
    if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
  const pr = gl.createProgram();
  gl.attachShader(pr, sh(gl.VERTEX_SHADER, "attribute vec2 p; varying vec2 uv; void main(){ uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }"));
  gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, `precision highp float; varying vec2 uv; uniform sampler2D v;
    uniform float hx, ck, side, gain; uniform vec2 px; uniform vec3 tint;
    float A(vec2 q){
      if (side < 0.5) return texture2D(v, q).a;
      float g = texture2D(v, vec2(max(0.5 + q.x * 0.5, 0.5 + hx), q.y)).g;
      return clamp((g - 16.0 / 255.0) * 255.0 / 219.0, 0.0, 1.0);   // limited-range video: 16..235
    }
    vec3 C(vec2 q){ return side < 0.5 ? texture2D(v, q).rgb : texture2D(v, vec2(clamp(q.x * 0.5, 0.0, 0.5 - hx), q.y)).rgb; }
    void main(){
      float a = A(uv);
      if (ck > 0.0) a = min(a, min(min(A(uv + vec2(ck * px.x, 0.0)), A(uv - vec2(ck * px.x, 0.0))),
                                   min(A(uv + vec2(0.0, ck * px.y)), A(uv - vec2(0.0, ck * px.y)))));
      vec3 c = C(uv);
      if (a > 0.0 && a < 0.98) {
        vec3 s = vec3(0.0); float ws = 0.0;
        for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
          vec2 q = uv + vec2(float(x), float(y)) * 2.0 * px;
          float b = A(q); b *= b; b *= b;
          s += b * C(q); ws += b;
        }
        if (ws > 0.05) c = s / ws;
      }
      c = min(c * gain, 1.0) * tint;
      gl_FragColor = vec4(c * a, a);
    }`));
  gl.linkProgram(pr); gl.useProgram(pr);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  const loc = gl.getAttribLocation(pr, "p"); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  gl.bindTexture(gl.TEXTURE_2D, gl.createTexture());
  for (const [k, val] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
    [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, val);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);   // stills: straight colour, alpha in .a
  const U = n => gl.getUniformLocation(pr, n), u = { hx: U("hx"), px: U("px"), side: U("side"), gain: U("gain"), tint: U("tint") };
  gl.uniform1f(U("ck"), choke);
  const run = (src, sw, w, h, isSide, { gain = 1, tint = [1, 1, 1] } = {}) => {
    if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
    gl.viewport(0, 0, w, h);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
    gl.uniform1f(u.side, isSide); gl.uniform1f(u.hx, 0.5 / sw); gl.uniform2f(u.px, 1 / w, 1 / h);
    gl.uniform1f(u.gain, gain); gl.uniform3f(u.tint, tint[0], tint[1], tint[2]);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    return cv;
  };
  return {
    canvas: cv,
    // src: <video> or canvas holding a side-by-side [raw | matte] frame
    draw(src, o) { const sw = src.videoWidth || src.width; return run(src, sw, sw >> 1, src.videoHeight || src.height, 1, o); },
    // img: a still cut-out (RGBA, straight alpha)
    still(img, o) { const w = img.naturalWidth || img.width, h = img.naturalHeight || img.height; return run(img, w, w, h, 0, o); },
  };
}

// ---------- matching the two people ----------
// mean luma of the face area of a cut-out: rows 15-55 % and the middle third of the person's bounding box, a > 0.5
const lumaCache = new WeakMap();
function faceLuma(img) {
  if (lumaCache.has(img)) return lumaCache.get(img);
  const W = 64, H = 48, c = Object.assign(document.createElement("canvas"), { width: W, height: H }), x = c.getContext("2d", { willReadFrequently: true });
  x.drawImage(img, 0, 0, W, H);
  const d = x.getImageData(0, 0, W, H).data;
  let x0 = W, x1 = -1, y0 = H, y1 = -1;
  for (let y = 0; y < H; y++) for (let k = 0; k < W; k++) if (d[(y * W + k) * 4 + 3] > 128) { x0 = Math.min(x0, k); x1 = Math.max(x1, k); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  let s = 0, n = 0;
  if (x1 >= 0) {
    const bw = x1 - x0 + 1, bh = y1 - y0 + 1;
    for (let y = Math.floor(y0 + 0.15 * bh); y <= y0 + 0.55 * bh; y++) for (let k = Math.floor(x0 + bw / 3); k <= x0 + 2 * bw / 3; k++) {
      const i = (y * W + k) * 4; if (d[i + 3] > 128) { s += 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2]; n++; }
    }
  }
  const L = n > 8 ? s / n : null;
  lumaCache.set(img, L);
  return L;
}
const clampG = g => Math.min(1.35, Math.max(0.85, g));
// Gains for two cut-outs on a preset: both move toward their mean, pulled 30 % toward a booth-ish face luma (0.42).
// fixed = "a" | "b": that person is in the raw backdrop (cannot change), so only the other one moves toward it.
export function matchLuma(aCut, bCut, fixed = null) {
  const La = faceLuma(aCut), Lb = faceLuma(bCut);
  if (!La || !Lb) return [1, 1];
  if (fixed === "a") return [1, clampG(La / Lb)];
  if (fixed === "b") return [clampG(Lb / La), 1];
  const t = 0.7 * (La + Lb) / 2 + 0.3 * 0.42 * 255;
  return [clampG(t / La), clampG(t / Lb)];
}

// ---------- cells ----------
const scratch = {};
const canvas = (k, w, h) => {
  const c = scratch[k] ||= document.createElement("canvas");
  if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
  return c;
};
const presetCache = new Map();
function preset(bg, w, h) {
  const key = `${bg.id}:${w}x${h}`;
  if (!presetCache.has(key)) {
    const c = document.createElement("canvas"); c.width = w; c.height = h; bg.draw(c.getContext("2d"), w, h);
    const m = Object.assign(document.createElement("canvas"), { width: 1, height: 1 }), mx = m.getContext("2d", { willReadFrequently: true });
    mx.drawImage(c, 0, 0, 1, 1); const d = mx.getImageData(0, 0, 1, 1).data;
    c.tint = [0, 1, 2].map(k => 1 - TINT + TINT * d[k] / 255);   // multiply-mode cast of the mean backdrop colour
    presetCache.set(key, c);
  }
  return presetCache.get(key);
}
let alpha = null;
// clip frames for the moving strip (crossfaded at the loop seam), keyed by video; empty outside recordMovingStrip
const clipFrames = new Map();
// one person's source for cell i: video halves when t is set and a clip exists, else the stills
function sources(p, i, t, o) {
  const c = p.cuts[i];
  alpha ||= createClipAlpha({ choke: CHOKE });
  if (t != null && c.clip) {
    const f = clipFrames.get(c.clip) || c.clip, w = (f.videoWidth || f.width) >> 1, h = f.videoHeight || f.height;
    return { raw: f, rawW: w, rawH: h, cut: () => alpha.draw(f, o), cutW: w, cutH: h };
  }
  const W = o => o.naturalWidth || o.videoWidth || o.width, H = o => o.naturalHeight || o.videoHeight || o.height;
  return { raw: c.raw, rawW: W(c.raw), rawH: H(c.raw), cut: () => alpha.still(c.cut, o), cutW: W(c.cut), cutH: H(c.cut) };
}
// The camera frame is 4:3 and the cell wider: crop mostly from the bottom so the head keeps its headroom.
const cellCrop = (w, h, aspect) => { const r = cropRect(w, h, aspect); r.sy *= 0.25; return r; };
const WRAP = 0.3;      // light wrap strength: how much blurred background spills over the cut-out edge
const CHOKE = 1;       // px the cut-out edge is eroded (min) before compositing: the only erosion in compose
const TINT = 0.07;     // backdrop colour cast on cut-outs over presets
const SHADOW = { a: 0.10, blur: 16, dy: 6 };   // soft contact shadow, lower half of the cell only

export function drawCell(ctx, rect, i, { a, b, bg, t = null }) {
  const { w, h } = rect, aspect = w / h;
  const back = canvas("back", w, h), bx = back.getContext("2d");
  const B = typeof bg === "string" ? BACKGROUNDS.find(x => x.id === bg) : bg;
  const people = B.id === "a" ? [b] : B.id === "b" ? [a] : [a, b];
  const gains = matchLuma(a.cuts[i].cut, b.cuts[i].cut, B.draw ? null : B.id);
  let tint;
  if (B.draw) { const pc = preset(B, w, h); bx.drawImage(pc, 0, 0); tint = pc.tint; }
  else {
    const s = sources(B.id === "a" ? a : b, i, t), r = cellCrop(s.rawW, s.rawH, aspect);
    bx.drawImage(s.raw, r.sx, r.sy, r.sw, r.sh, 0, 0, w, h);
  }
  for (const p of people) {
    const s = sources(p, i, t, { gain: gains[p === a ? 0 : 1], tint }), r = cellCrop(s.cutW, s.cutH, aspect);
    const per = canvas("per", w, h), px = per.getContext("2d");
    px.clearRect(0, 0, w, h); px.drawImage(s.cut(), r.sx, r.sy, r.sw, r.sh, 0, 0, w, h);
    // contact shadow: the silhouette's shadow only (person drawn off-canvas), faded in toward the shoulders
    const sh = canvas("shadow", w, h), sx = sh.getContext("2d");
    sx.clearRect(0, 0, w, h);
    sx.save(); sx.shadowColor = `rgba(0,0,0,${SHADOW.a})`; sx.shadowBlur = SHADOW.blur; sx.shadowOffsetX = w * 2; sx.shadowOffsetY = SHADOW.dy;
    sx.drawImage(per, -w * 2, 0); sx.restore();
    const g = sx.createLinearGradient(0, h * 0.45, 0, h * 0.75); g.addColorStop(0, "#0000"); g.addColorStop(1, "#000f");
    sx.globalCompositeOperation = "destination-in"; sx.fillStyle = g; sx.fillRect(0, 0, w, h); sx.globalCompositeOperation = "source-over";
    bx.drawImage(sh, 0, 0);
    // light wrap: blurred background, kept only inside the person and fading out from the edge (~3 px band)
    const sw = Math.max(1, Math.round(w / 14)), shh = Math.max(1, Math.round(h / 14));
    const ew = Math.max(1, Math.round(w / 10)), eh = Math.max(1, Math.round(h / 10));
    const bgS = canvas("bgS", sw, shh), peS = canvas("peS", ew, eh);
    bgS.getContext("2d").drawImage(back, 0, 0, sw, shh);
    const pe = peS.getContext("2d"); pe.clearRect(0, 0, ew, eh); pe.drawImage(per, 0, 0, ew, eh);
    const wrap = canvas("wrap", w, h), wx = wrap.getContext("2d");
    wx.globalCompositeOperation = "copy"; wx.drawImage(bgS, 0, 0, w, h);
    wx.globalCompositeOperation = "destination-out"; wx.drawImage(peS, 0, 0, w, h);
    wx.globalCompositeOperation = "destination-in"; wx.drawImage(per, 0, 0);
    wx.globalCompositeOperation = "source-over";
    bx.drawImage(per, 0, 0);
    bx.globalAlpha = WRAP; bx.drawImage(wrap, 0, 0); bx.globalAlpha = 1;
  }
  ctx.drawImage(back, rect.x, rect.y);
}

// ---------- strip ----------
let filt = null;
async function prepare({ frame, date, caption = "" }) {
  const F = frameOf(frame);
  await loadFrameFonts(F, caption);
  const rects = cellRects(F), d = fmtDate(date), o = { date: d, caption, cellRects: rects };
  const base = document.createElement("canvas"); base.width = L.W; base.height = L.H;
  F.draw(base.getContext("2d"), L, o);
  let over = null;
  if (F.over) { over = document.createElement("canvas"); over.width = L.W; over.height = L.H; F.over(over.getContext("2d"), L, o); }
  return { F, rects, base, over };
}
function paint(ctx, st, { a, b, bg, filter = "original" }, t) {
  filt ||= createFilter();
  ctx.drawImage(st.base, 0, 0);
  st.rects.forEach((r, i) => {
    const cell = canvas("cell", r.w, r.h), cx = cell.getContext("2d");
    drawCell(cx, { x: 0, y: 0, w: r.w, h: r.h }, i, { a, b, bg, t: t == null ? null : t });
    const out = filt.apply(cell, filter, 1, i + 1);
    ctx.save(); ctx.beginPath(); ctx.roundRect(r.x, r.y, r.w, r.h, st.F.radius || 0); ctx.clip();
    ctx.drawImage(out, r.x, r.y); ctx.restore();
  });
  if (st.over) ctx.drawImage(st.over, 0, 0);
}

export async function drawStrip({ a, b, bg, frame, filter, date, caption }) {
  const st = await prepare({ frame, date, caption });
  const cv = document.createElement("canvas"); cv.width = L.W; cv.height = L.H;
  paint(cv.getContext("2d"), st, { a, b, bg, filter }, null);
  return new Promise((res, rej) => cv.toBlob(bl => (bl ? res(bl) : rej(new Error("toBlob"))), "image/jpeg", 0.92));
}

// ---------- story (1080x1920): the strip centred on a soft backdrop made from the frame's own colours ----------
const STORY = { W: 1080, H: 1920 };
async function storyBack(st) {
  const { W, H } = STORY, c = Object.assign(document.createElement("canvas"), { width: W, height: H }), x = c.getContext("2d");
  // the frame's own colour (FRAMES[].color), washed toward white: lighter at the top, a little deeper at the bottom
  x.fillStyle = st.F.color || "#f3e9dc"; x.fillRect(0, 0, W, H);
  const wash = x.createLinearGradient(0, 0, 0, H);
  wash.addColorStop(0, "rgba(255,255,255,0.62)"); wash.addColorStop(1, "rgba(255,255,255,0.35)");
  x.fillStyle = wash; x.fillRect(0, 0, W, H);
  const s = (H * 0.84) / L.H, sw = L.W * s, sh = L.H * s, ox = (W - sw) / 2, oy = (H - sh) / 2 - 40;
  x.save(); x.shadowColor = "rgba(0,0,0,0.22)"; x.shadowBlur = 40; x.shadowOffsetY = 14;
  x.fillStyle = "#fff"; x.fillRect(ox, oy, sw, sh); x.restore();
  const m = Object.assign(document.createElement("canvas"), { width: 1, height: 1 }), mx = m.getContext("2d", { willReadFrequently: true });
  mx.drawImage(c, 0, H - 60, W, 60, 0, 0, 1, 1);
  const [r, g, bl] = mx.getImageData(0, 0, 1, 1).data, dark = 0.299 * r + 0.587 * g + 0.114 * bl < 140;
  await document.fonts.load('600 34px "Jua"', "따로 같이 네컷").catch(() => null);
  x.font = '34px "Jua", system-ui, sans-serif'; x.textAlign = "center"; x.textBaseline = "middle";
  x.fillStyle = dark ? "rgba(255,255,255,0.8)" : "rgba(40,30,45,0.55)";
  x.fillText("따로 같이 네컷", W / 2, oy + sh + (H - oy - sh) / 2);
  return { c, ox, oy, sw, sh };
}

const SECONDS = 6, FPS = 30, BPS = 4_000_000;
// loop-seam crossfade per clip (s); ~0 = hard loop like a booth live photo: a 0.2 s fade showed the cut-out
// half see-through for a frame or two, the "ghost" four reviewers flagged (lead, 10-27). Kept > 0 to avoid / 0.
const FADE = 0.001;
const HOLD = 0.3;                      // the last 0.3 s holds the finished photo strip
const PHASE = [0, 0.12, 0.24, 0.36];   // per-cell start offsets: the four cells do not loop on the same frame
export async function recordMovingStrip({ a, b, bg, frame, filter, date, caption, format = "strip", onProgress }) {
  if (!window.MediaRecorder) throw new Error("no MediaRecorder");
  const vids = [...a.cuts, ...b.cuts].map(c => c.clip);
  if (vids.some(v => !v)) throw new Error("missing clip");
  const st = await prepare({ frame, date, caption });
  const story = format === "story" ? await storyBack(st) : null;
  const cv = document.createElement("canvas"); cv.width = story ? STORY.W : L.W; cv.height = story ? STORY.H : L.H;
  const ok = t => MediaRecorder.isTypeSupported(t);
  const mp4 = ["video/mp4;codecs=avc1", "video/mp4"].find(ok), webm = ["video/webm;codecs=vp9", "video/webm"].find(ok);
  let lastErr = new Error("no recorder type");
  for (const type of [mp4, webm].filter(Boolean)) {
    try { return { blob: await recordOnce(cv, st, story, vids, type, { a, b, bg, filter }, onProgress), type: type.split(";")[0] }; }
    catch (e) { lastErr = e; }
  }
  throw lastErr;
}

// Per clip: a copy of the frame shown last tick; when the clip wraps, that copy fades out over the new frames.
function clipState(v) {
  const mk = () => Object.assign(document.createElement("canvas"), { width: v.videoWidth, height: v.videoHeight });
  return { v, last: mk(), snap: mk(), out: mk(), prevT: v.currentTime, wrapAt: -1 };
}
function tickClip(c, t) {
  const ct = c.v.currentTime;
  if (ct + 0.05 < c.prevT) { [c.last, c.snap] = [c.snap, c.last]; c.wrapAt = t; }   // wrapped: keep the old last frame
  c.prevT = ct;
  c.last.getContext("2d").drawImage(c.v, 0, 0);
  const k = (t - c.wrapAt) / FADE;
  if (c.wrapAt < 0 || k >= 1) return c.last;
  const x = c.out.getContext("2d");
  x.globalAlpha = 1; x.drawImage(c.last, 0, 0);
  x.globalAlpha = 1 - k; x.drawImage(c.snap, 0, 0); x.globalAlpha = 1;
  return c.out;
}

// A cold encoder starts 1-5 s after start() (headless 10-27: 600x1800 ~1 s, 1080x1920 5.4 s) and the strip then opens
// on a frozen frame: start a throwaway one of the same type and size first, once per page.
const warmed = new Map();
function warmEncoder(type, w, h) {
  const key = `${type} ${w}x${h}`;
  if (!warmed.has(key)) warmed.set(key, (async () => {
    const c = Object.assign(document.createElement("canvas"), { width: w, height: h }), x = c.getContext("2d");
    const s = c.captureStream(30), r = new MediaRecorder(s, { mimeType: type });
    let on = true; const l = () => { if (on) { x.fillStyle = `hsl(${performance.now() % 360},50%,50%)`; x.fillRect(0, 0, 64, 48); requestAnimationFrame(l); } };
    l();
    const got = new Promise(res => (r.ondataavailable = res)), stop = new Promise(res => (r.onstop = res));
    r.start(100);
    await Promise.race([got, new Promise(res => setTimeout(res, 8000))]);
    r.stop(); await Promise.race([stop, new Promise(res => setTimeout(res, 1000))]);
    on = false; s.getTracks().forEach(t => t.stop());
  })().catch(() => {}));
  return warmed.get(key);
}

async function recordOnce(cv, st, story, vids, type, opts, onProgress) {
  await warmEncoder(type, cv.width, cv.height);
  const ctx = cv.getContext("2d");
  const strip = story ? Object.assign(document.createElement("canvas"), { width: L.W, height: L.H }) : cv, sctx = strip.getContext("2d");
  const show = () => { if (story) { ctx.drawImage(story.c, 0, 0); ctx.drawImage(strip, story.ox, story.oy, story.sw, story.sh); } };
  const still = Object.assign(document.createElement("canvas"), { width: L.W, height: L.H });
  paint(still.getContext("2d"), st, opts, null);
  // start every clip at its cell's phase and wait until all of them show that frame
  vids.forEach((v, n) => { v.loop = true; v.muted = true; v.pause(); v.currentTime = Math.min(PHASE[n % 4], (v.duration || 1) * 0.5); });
  await Promise.all(vids.map(v => (v.readyState >= 2 && !v.seeking ? null : new Promise(r => v.addEventListener("seeked", r, { once: true })))));
  const states = vids.map(clipState);
  const frame = t => {
    states.forEach(s => clipFrames.set(s.v, tickClip(s, t)));
    paint(sctx, st, opts, t);
    const k = (t - (SECONDS - HOLD - FADE)) / FADE;       // fade into the finished strip, then hold it
    if (k > 0) { sctx.globalAlpha = Math.min(1, k); sctx.drawImage(still, 0, 0); sctx.globalAlpha = 1; }
    show();
  };
  try {
    // push each painted frame (captureStream(0) + requestFrame): with a timed stream headless Chrome encoded 86 of
    // ~260 painted frames and the 6 s strip came out 3.8 s long (10-27); the timed stream stays as the fallback
    let stream = cv.captureStream(0), track = stream.getVideoTracks()[0];
    if (!track?.requestFrame) { stream.getTracks().forEach(tr => tr.stop()); stream = cv.captureStream(FPS); track = null; }
    const push = () => track?.requestFrame();
    frame(0);
    const rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: BPS });
    const chunks = [];
    let failed = null;
    rec.ondataavailable = e => e.data.size && chunks.push(e.data);
    rec.onerror = e => { failed = e.error || new Error("recorder"); };
    const stopped = new Promise(r => (rec.onstop = r));
    const started = new Promise(r => (rec.onstart = r));
    // play() can take ~1.5 s and the encoder ~1-4 s to start (headless 10-27): both before the clock starts,
    // or the strip opens on a frozen frame / loses its first second
    await Promise.all(vids.map(v => v.play()));
    // the encoder needs a frame before it fires onstart; the clock starts only then.
    // ponytail: a slow encoder start (headless 1080x1920: ~5 s even warmed) opens the file on that first frame;
    // starting paused did not help (onstart came even later); fix only if a phone shows it
    rec.start(1000); push();
    await Promise.race([started, new Promise(r => setTimeout(r, 10000))]);
    const t0 = performance.now();
    await new Promise(done => {
      const tick = () => {
        const t = (performance.now() - t0) / 1000;
        if (failed) return done();
        frame(Math.min(t, SECONDS)); push();
        onProgress?.(Math.min(1, t / SECONDS));
        if (t >= SECONDS) return setTimeout(done, 100);    // one more held frame reaches the encoder
        requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    rec.stop(); await stopped;
    stream.getTracks().forEach(tr => tr.stop());
    if (failed) throw failed;
    const blob = new Blob(chunks, { type: type.split(";")[0] });
    if (!blob.size) throw new Error("empty recording");
    onProgress?.(1);
    return blob;
  } finally {
    vids.forEach(v => v.pause());
    clipFrames.clear();
  }
}
