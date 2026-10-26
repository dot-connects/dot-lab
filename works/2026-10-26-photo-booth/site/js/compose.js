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

// ---------- clip alpha: [raw | matte] video frame -> premultiplied RGBA canvas ----------
export function createClipAlpha() {
  const cv = document.createElement("canvas");
  const gl = cv.getContext("webgl", { premultipliedAlpha: true, preserveDrawingBuffer: true, alpha: true });
  if (!gl) throw new Error("webgl");
  const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o); return o; };
  const pr = gl.createProgram();
  gl.attachShader(pr, sh(gl.VERTEX_SHADER, "attribute vec2 p; varying vec2 uv; void main(){ uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }"));
  gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, `precision mediump float; varying vec2 uv; uniform sampler2D v; uniform float hx;
    void main(){
      vec3 c = texture2D(v, vec2(min(uv.x * 0.5, 0.5 - hx), uv.y)).rgb;
      float g = texture2D(v, vec2(max(0.5 + uv.x * 0.5, 0.5 + hx), uv.y)).g;
      float a = clamp((g - 24.0 / 255.0) * 255.0 / 207.0, 0.0, 1.0);
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
  const hx = gl.getUniformLocation(pr, "hx");
  return {
    canvas: cv,
    draw(video) {
      const w = video.videoWidth >> 1, h = video.videoHeight;
      if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
      gl.viewport(0, 0, w, h);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
      gl.uniform1f(hx, 0.5 / video.videoWidth);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      return cv;
    },
  };
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
  if (!presetCache.has(key)) { const c = document.createElement("canvas"); c.width = w; c.height = h; bg.draw(c.getContext("2d"), w, h); presetCache.set(key, c); }
  return presetCache.get(key);
}
let clipAlpha = null;
// one person's source for cell i: video halves when t is set and a clip exists, else the stills
function sources(p, i, t) {
  const c = p.cuts[i];
  if (t != null && c.clip) {
    const v = c.clip, w = v.videoWidth >> 1, h = v.videoHeight;
    return { raw: v, rawW: w, rawH: h, cut: () => (clipAlpha ||= createClipAlpha()).draw(v), cutW: w, cutH: h };
  }
  const W = o => o.naturalWidth || o.videoWidth || o.width, H = o => o.naturalHeight || o.videoHeight || o.height;
  return { raw: c.raw, rawW: W(c.raw), rawH: H(c.raw), cut: () => c.cut, cutW: W(c.cut), cutH: H(c.cut) };
}
const WRAP = 0.6;      // light wrap strength: how much blurred background spills over the cut-out edge
const CHOKE = 3;       // px the cut-out edge is eroded before compositing

export function drawCell(ctx, rect, i, { a, b, bg, t = null }) {
  const { w, h } = rect, aspect = w / h;
  const back = canvas("back", w, h), bx = back.getContext("2d");
  const B = typeof bg === "string" ? BACKGROUNDS.find(x => x.id === bg) : bg;
  const people = B.id === "a" ? [b] : B.id === "b" ? [a] : [a, b];
  if (B.draw) bx.drawImage(preset(B, w, h), 0, 0);
  else {
    const s = sources(B.id === "a" ? a : b, i, t), r = cropRect(s.rawW, s.rawH, aspect);
    bx.drawImage(s.raw, r.sx, r.sy, r.sw, r.sh, 0, 0, w, h);
  }
  for (const p of people) {
    const s = sources(p, i, t), r = cropRect(s.cutW, s.cutH, aspect);
    const per0 = canvas("per0", w, h), p0 = per0.getContext("2d");
    p0.clearRect(0, 0, w, h); p0.drawImage(s.cut(), r.sx, r.sy, r.sw, r.sh, 0, 0, w, h);
    // choke: intersect shifted copies = erode the matte edge by CHOKE px (drops the old-wall fringe)
    const per = canvas("per", w, h), px = per.getContext("2d");
    px.globalCompositeOperation = "copy"; px.drawImage(per0, 0, 0);
    px.globalCompositeOperation = "destination-in";
    for (const [dx, dy] of [[CHOKE, 0], [-CHOKE, 0], [0, CHOKE], [0, -CHOKE]]) px.drawImage(per0, dx, dy);
    px.globalCompositeOperation = "source-over";
    for (const [x, y, ww, hh] of [[0, h - CHOKE, w, CHOKE], [0, 0, w, CHOKE], [0, 0, CHOKE, h], [w - CHOKE, 0, CHOKE, h]])
      px.drawImage(per0, x, y, ww, hh, x, y, ww, hh);  // a person cut by the cell edge stays flush with it
    // light wrap: blurred background, kept only inside the person and fading out from the edge
    const sw = Math.max(1, Math.round(w / 14)), sh = Math.max(1, Math.round(h / 14));
    const ew = Math.max(1, Math.round(w / 6)), eh = Math.max(1, Math.round(h / 6));
    const bgS = canvas("bgS", sw, sh), peS = canvas("peS", ew, eh);
    bgS.getContext("2d").drawImage(back, 0, 0, sw, sh);
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
async function prepare({ frame, date }) {
  const F = frameOf(frame);
  await loadFrameFonts(F);
  const rects = cellRects(F), d = fmtDate(date);
  const base = document.createElement("canvas"); base.width = L.W; base.height = L.H;
  F.draw(base.getContext("2d"), L, { date: d, cellRects: rects });
  let over = null;
  if (F.over) { over = document.createElement("canvas"); over.width = L.W; over.height = L.H; F.over(over.getContext("2d"), L, { date: d, cellRects: rects }); }
  return { F, rects, base, over };
}
function paint(ctx, st, { a, b, bg, filter = "original" }, t) {
  filt ||= createFilter();
  ctx.drawImage(st.base, 0, 0);
  st.rects.forEach((r, i) => {
    const cell = canvas("cell", r.w, r.h), cx = cell.getContext("2d");
    drawCell(cx, { x: 0, y: 0, w: r.w, h: r.h }, i, { a, b, bg, t });
    const out = filt.apply(cell, filter, 1, i + 1);
    ctx.save(); ctx.beginPath(); ctx.roundRect(r.x, r.y, r.w, r.h, st.F.radius || 0); ctx.clip();
    ctx.drawImage(out, r.x, r.y); ctx.restore();
  });
  if (st.over) ctx.drawImage(st.over, 0, 0);
}

export async function drawStrip({ a, b, bg, frame, filter, date }) {
  const st = await prepare({ frame, date });
  const cv = document.createElement("canvas"); cv.width = L.W; cv.height = L.H;
  paint(cv.getContext("2d"), st, { a, b, bg, filter }, null);
  return new Promise((res, rej) => cv.toBlob(bl => (bl ? res(bl) : rej(new Error("toBlob"))), "image/jpeg", 0.92));
}

const SECONDS = 6, FPS = 30, BPS = 4_000_000;
export async function recordMovingStrip({ a, b, bg, frame, filter, date, onProgress }) {
  if (!window.MediaRecorder) throw new Error("no MediaRecorder");
  const vids = [...a.cuts, ...b.cuts].map(c => c.clip);
  if (vids.some(v => !v)) throw new Error("missing clip");
  const st = await prepare({ frame, date });
  const cv = document.createElement("canvas"); cv.width = L.W; cv.height = L.H;
  const ctx = cv.getContext("2d");
  const ok = t => MediaRecorder.isTypeSupported(t);
  const mp4 = ["video/mp4;codecs=avc1", "video/mp4"].find(ok), webm = ["video/webm;codecs=vp9", "video/webm"].find(ok);
  let lastErr = new Error("no recorder type");
  for (const type of [mp4, webm].filter(Boolean)) {
    try { return { blob: await recordOnce(cv, ctx, st, vids, type, { a, b, bg, filter }, onProgress), type: type.split(";")[0] }; }
    catch (e) { lastErr = e; }
  }
  throw lastErr;
}
async function recordOnce(cv, ctx, st, vids, type, opts, onProgress) {
  for (const v of vids) { v.loop = true; v.muted = true; v.pause(); v.currentTime = 0; }
  await Promise.all(vids.map(v => (v.readyState >= 2 && !v.seeking ? null : new Promise(r => v.addEventListener("seeked", r, { once: true })))));
  paint(ctx, st, opts, 0);
  const stream = cv.captureStream(FPS);
  const rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: BPS });
  const chunks = [];
  let failed = null;
  rec.ondataavailable = e => e.data.size && chunks.push(e.data);
  rec.onerror = e => { failed = e.error || new Error("recorder"); };
  const stopped = new Promise(r => (rec.onstop = r));
  await Promise.all(vids.map(v => v.play()));
  rec.start(1000);
  const t0 = performance.now();
  await new Promise(done => {
    const tick = () => {
      const t = (performance.now() - t0) / 1000;
      if (t >= SECONDS || failed) return done();
      paint(ctx, st, opts, t);
      onProgress?.(t / SECONDS);
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
  rec.stop(); await stopped;
  stream.getTracks().forEach(tr => tr.stop());
  vids.forEach(v => v.pause());
  if (failed) throw failed;
  const blob = new Blob(chunks, { type: type.split(";")[0] });
  if (!blob.size) throw new Error("empty recording");
  onProgress?.(1);
  return blob;
}
