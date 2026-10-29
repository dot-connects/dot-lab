// Compose the 4-cut strip (JPG) and the moving strip (MP4/WebM) from both people's media.
// Cell = background (A's raw | B's raw | preset), then B's cut-out, then A's (A always in front), positions as shot
// (B may size / place itself, `own.b`; A never moves); optional sticker outline on both; light wrap on plain cut-out edges, then the filter over
// the whole cell. Frame art goes under and over the cells.
import { createFilter } from "./filters.js";
import { FRAMES, loadFrameFonts } from "./frames.js";
import { drawStickers, stickersReady } from "./stickers.js";
import { MAKING, ROW_T, ROW_DUR, drawMaking, makingFonts, makingDate } from "./making.js";

// Round 6 #9: A picks the shape first and B follows it (link opts.shape).
//   wide = 가로: 4 wide cells stacked in a tall 2x6 in strip (600 x 1800).
//   tall = 세로: 4 portrait 3:4 cells side by side (1800 x 800). The footer band keeps the wide strip's ~200 px, so every
//          frame's caption block fits unchanged; 1800 = the same 6 in at 300 dpi.
export const LAYOUTS = {
  wide: { W: 600, H: 1800, M: 24, GAP: 16, FOOT: 200, CW: 552, CH: 382, shape: "wide" },
  tall: { W: 1800, H: 800, M: 24, GAP: 16, FOOT: 208, CW: 426, CH: 568, shape: "tall" },
};
export const LAYOUT = LAYOUTS.wide;
export const layoutFor = shape => LAYOUTS[shape] || LAYOUTS.wide;
export const cellAspect = shape => layoutFor(shape).CW / layoutFor(shape).CH;
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

// frame.rects = { wide?: [4 rects], tall?: [4 rects] } (frames with their own cell layout), else the shape's default
export function cellRects(frame, shape = "wide") {
  const f = frame && frameOf(frame), Ls = layoutFor(shape), own = f?.rects?.[Ls.shape];
  if (own) return own;
  return [0, 1, 2, 3].map(i => Ls.shape === "tall" ? { x: Ls.M + i * (Ls.CW + Ls.GAP), y: Ls.M, w: Ls.CW, h: Ls.CH }
    : { x: Ls.M, y: Ls.M + i * (Ls.CH + Ls.GAP), w: Ls.CW, h: Ls.CH });
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


// ---------- sticker outline (Round 3): chips for the UI, colour as hex (or a chip id) in `outline.color` ----------
export const OUTLINE_COLORS = [
  { id: "white", label: "흰색", color: "#ffffff" },
  { id: "pink", label: "분홍", color: "#ff8fb6" },
  { id: "yellow", label: "노랑", color: "#ffd84a" },
  { id: "sky", label: "하늘", color: "#78c6ff" },
  { id: "mint", label: "민트", color: "#7fe3c4" },
  { id: "lavender", label: "보라", color: "#b79cff" },
  { id: "black", label: "검정", color: "#222222" },
];
export const OUTLINE_STYLES = [{ id: "solid", label: "실선" }, { id: "dotted", label: "점선" }, { id: "glitter", label: "반짝이" }];
// px = band width in strip px (cells are 552x382 in the 600x1800 strip), whatever B's scale of A is
export const OUTLINE_WIDTHS = [{ id: "thin", label: "얇게", px: 6 }, { id: "normal", label: "보통", px: 10 }, { id: "thick", label: "두껍게", px: 16 }];
function outlineOf(o) {
  if (!o) return null;
  const w = OUTLINE_WIDTHS.find(x => x.id === o.width) || OUTLINE_WIDTHS[1];
  const style = OUTLINE_STYLES.some(s => s.id === o.style) ? o.style : "solid";
  const hex = /^#[0-9a-f]{6}$/i.test(o.color || "") ? o.color : OUTLINE_COLORS.find(c => c.id === o.color)?.color || "#ffffff";
  return { style, px: w.px, color: hex, rgb: [1, 3, 5].map(k => parseInt(hex.slice(k, k + 2), 16) / 255) };
}
// The sticker shape is the person's alpha blurred (Gaussian, sigma = width / ZB) and cut at TH = the level a straight
// edge reaches `width` px out: a round offset that also rounds the concave corners (neck, armpits) like a die cut.
// The blur runs at 1/2-1/4 resolution and is upsampled bilinearly, so a clip frame stays cheap.
const erf = x => { const s = Math.sign(x), t = 1 / (1 + 0.3275911 * Math.abs(x));
  return s * (1 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x)); };
const Phi = x => 0.5 * (1 + erf(x / Math.SQRT2));
const ZB = 1.9, TH = 1 - Phi(ZB);
const STICKER_SHADOW = 3;   // drop shadow offset (strip px) under solid / glitter stickers

// iso-lines of a top-down row-major field at level t (marching squares), chained into polylines in grid coords
function isolines(F, w, h, t) {
  const pt = new Map(), adj = new Map();
  const at = k => { if (!pt.has(k)) { const i = k >> 1, x = i % w, y = (i / w) | 0, v = F[i], u = (t - v) / (F[i + (k & 1 ? w : 1)] - v);
    pt.set(k, k & 1 ? [x, y + u] : [x + u, y]); } return pt.get(k); };
  const nb = k => adj.get(k) || (adj.set(k, []), adj.get(k));
  const link = (p, q) => { at(p); at(q); nb(p).push(q); nb(q).push(p); };
  for (let y = 0; y < h - 1; y++) for (let x = 0; x < w - 1; x++) {
    const i = y * w + x, c = (F[i] > t) | (F[i + 1] > t) << 1 | (F[i + w + 1] > t) << 2 | (F[i + w] > t) << 3;
    if (c === 0 || c === 15) continue;
    const T = i << 1, R = (i + 1) << 1 | 1, Bt = (i + w) << 1, Lf = i << 1 | 1, e = [];
    if ((c & 1) !== (c >> 1 & 1)) e.push(T);
    if ((c >> 1 & 1) !== (c >> 2 & 1)) e.push(R);
    if ((c >> 2 & 1) !== (c >> 3 & 1)) e.push(Bt);
    if ((c >> 3 & 1) !== (c & 1)) e.push(Lf);
    if (e.length === 2) link(e[0], e[1]);
    else if ((c === 5) === ((F[i] + F[i + 1] + F[i + w] + F[i + w + 1]) / 4 > t)) { link(T, R); link(Bt, Lf); }   // saddle
    else { link(T, Lf); link(R, Bt); }
  }
  const seen = new Set(), lines = [];
  const walk = s => {
    const pts = [pt.get(s)]; seen.add(s);
    let prev = -1, cur = s;
    for (;;) {
      const nx = adj.get(cur).find(k => k !== prev && !seen.has(k));
      if (nx == null) break;
      seen.add(nx); pts.push(pt.get(nx)); prev = cur; cur = nx;
    }
    lines.push({ pts, closed: pts.length > 2 && adj.get(cur).includes(s) });
  };
  for (const [k, v] of adj) if (v.length === 1 && !seen.has(k)) walk(k);   // open ends (field cut by the grid edge) first
  for (const k of adj.keys()) if (!seen.has(k)) walk(k);
  return lines;
}
// evenly spaced points along a polyline, about `step` apart (closed lines get an exact whole number)
function along({ pts, closed }, step) {
  const P = closed ? [...pts, pts[0]] : pts, cum = [0];
  for (let j = 1; j < P.length; j++) cum.push(cum[j - 1] + Math.hypot(P[j][0] - P[j - 1][0], P[j][1] - P[j - 1][1]));
  const len = cum[cum.length - 1], n = Math.round(len / step), out = [];
  if (n < 1) return out;
  for (let j = 0, s = 0; j < n; j++) {
    const d = (j + 0.5) * len / n;
    while (cum[s + 1] < d) s++;
    const u = (d - cum[s]) / (cum[s + 1] - cum[s] || 1);
    out.push([P[s][0] + u * (P[s + 1][0] - P[s][0]), P[s][1] + u * (P[s + 1][1] - P[s][1])]);
  }
  return out;
}

// ---------- cut-out alpha on the GPU: one shader for clips ([raw | matte] side by side) and stills (RGBA) ----------
// choke : alpha = min of 4 taps at +-choke px (erosion as a min, not a product, so soft edges stay soft)
// decon : edge pixels (0 < a < 0.98) take the a^4-weighted colour of the solid person around them, so the old wall
//         colour never shows at the edge (same math as segment.js FS_DRAW)
// gain / tint : per-channel light match between the two people (matchLight) and a light backdrop colour cast (compose only)
// outline : { style, px (band width in source px), rgb, color, cs (strip px per source px) } -> sticker under the person.
//   The output canvas then grows by `pad` px on every side (canvas.pad) so the band is not cut at the frame edge;
//   dotted returns a 2D canvas (dots placed along the field's iso-line on the CPU, then the person on top).
// time : seconds, glitter twinkle. Output is premultiplied. Texture edges clamp, so a person cut by the frame edge is
// not eroded there.
export function createClipAlpha({ choke = 0 } = {}) {
  const cv = document.createElement("canvas");
  const opt = { premultipliedAlpha: true, preserveDrawingBuffer: true, alpha: true };
  const gl = cv.getContext("webgl2", opt) || cv.getContext("webgl", opt);   // GLSL 100 shaders run on both
  if (!gl) throw new Error("webgl");
  const gl2 = typeof WebGL2RenderingContext !== "undefined" && gl instanceof WebGL2RenderingContext;
  const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o);
    if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
  const HEAD = `precision highp float; varying vec2 uv; uniform sampler2D v, f;
    uniform float hx, side, sg; uniform vec4 pm; uniform vec2 lo;
    vec2 S(vec2 u){ return u * pm.xy + pm.zw; }          // padded output uv -> source uv
    float A(vec2 q){
      if (q.x < 0.0 || q.y < 0.0 || q.x > 1.0 || q.y > 1.0) return 0.0;
      if (side < 0.5) return texture2D(v, q).a;
      float g = texture2D(v, vec2(max(0.5 + q.x * 0.5, 0.5 + hx), q.y)).g;
      return clamp((g - 16.0 / 255.0) * 255.0 / 219.0, 0.0, 1.0);   // limited-range video: 16..235
    }
    vec3 C(vec2 q){ return side < 0.5 ? texture2D(v, q).rgb : texture2D(v, vec2(clamp(q.x * 0.5, 0.0, 0.5 - hx), q.y)).rgb; }
    vec4 pk(float x){ x = clamp(x, 0.0, 1.0) * 255.0; return vec4(floor(x) / 255.0, fract(x), 0.0, 1.0); }   // 16-bit in RG
    float un(vec2 q){ vec4 c = texture2D(f, q); return c.r + c.g / 255.0; }
    float K(float x){ return abs(x) > 3.0 * sg + 0.5 ? 0.0 : exp(-x * x / (2.0 * sg * sg)); }
    float h1(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
`;
  const FS_H = `void main(){ float s = 0.0, n = 0.0;
      for (int i = -12; i <= 12; i++) { float x = float(i), w = K(x); s += w * A(S(uv + vec2(x * lo.x, 0.0))); n += w; }
      gl_FragColor = pk(s / n); }`;
  const FS_V = `void main(){ float s = 0.0, n = 0.0;
      for (int i = -12; i <= 12; i++) { float x = float(i), w = K(x); s += w * un(uv + vec2(0.0, x * lo.y)); n += w; }
      gl_FragColor = pk(s / n); }`;
  const FS_M = `uniform float ck, sty, th, cs, tm, sd; uniform vec2 px, op, ps; uniform vec3 tint, oc, gain;
    void main(){
      vec2 q = S(uv);
      float a = A(q);
      if (ck > 0.0) a = min(a, min(min(A(q + vec2(ck * px.x, 0.0)), A(q - vec2(ck * px.x, 0.0))),
                                   min(A(q + vec2(0.0, ck * px.y)), A(q - vec2(0.0, ck * px.y)))));
      vec3 c = C(q);
      if (a > 0.0 && a < 0.98) {
        vec3 s = vec3(0.0); float ws = 0.0;
        for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
          vec2 r = q + vec2(float(x), float(y)) * 2.0 * px;
          float b = A(r); b *= b; b *= b;
          s += b * C(r); ws += b;
        }
        if (ws > 0.05) c = s / ws;
      }
      c = min(c * gain, 1.0) * tint;
      vec4 o = vec4(c * a, a);
      // 1 solid, 2 glitter (3 dotted: the dots come from the CPU); nothing to do under the opaque person
      if (sty > 0.5 && sty < 2.5 && a < 0.995) {
        float b = un(uv), ba = b > th ? 1.0 : 0.0;
        if (b > th * 0.3 && b < th * 3.0) {                                  // 1 px anti-aliased band edge
          float g = 0.5 * length(vec2(un(uv + vec2(op.x, 0.0)) - un(uv - vec2(op.x, 0.0)), un(uv + vec2(0.0, op.y)) - un(uv - vec2(0.0, op.y))));
          ba = clamp((b - th) / max(g, 1e-6) + 0.5, 0.0, 1.0);
        }
        vec4 L = vec4(0.0, 0.0, 0.0, 0.3 * smoothstep(th * 0.06, th, un(uv + vec2(0.0, sd))));   // soft drop shadow
        vec3 col = oc; float st = 0.0;
        if (sty > 1.5 && b > th * 0.003) {   // star arms reach a few px past the band
          // glitter: 2 px flakes in close tones of the colour, ~1 in 5 a glint (white with a faint rainbow) that
          // twinkles, plus small 4-point stars centred on the band (all in strip px, so B's scale of A keeps the size)
          vec2 p = uv * ps * cs, cell = floor(p / 2.0);
          float r = h1(cell), r2 = h1(cell + 17.0), tw = 0.5 + 0.5 * sin(tm * 6.0 + 6.283 * r2);
          col = min(oc * (0.8 + 0.3 * r) + 0.04, 1.0);
          vec3 glint = mix(vec3(1.0), 0.6 + 0.4 * cos(6.283 * (r2 + vec3(0.0, 0.33, 0.67))), 0.3);
          col = mix(col, glint, step(0.8, r) * (0.35 + 0.65 * tw));
          // stars: one candidate per 12 px cell (3x3 neighbours, so arms cross cells), kept when its centre is on the band
          for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 id = floor(p / 12.0) + vec2(float(i), float(j));
            if (h1(id + 9.9) > 0.3) continue;
            vec2 sc = (id + vec2(h1(id + 3.1), h1(id + 5.3))) * 12.0, su = sc / cs / ps;
            if (un(su) < th || A(S(su)) > 0.3) continue;
            float sr = 3.5 + 3.5 * h1(id + 7.7); vec2 d = abs(p - sc);
            float s1 = max(0.0, 1.0 - d.x / sr) * exp(-d.y * 1.3) + max(0.0, 1.0 - d.y / sr) * exp(-d.x * 1.3) + 0.5 * exp(-dot(d, d) / 2.5);
            st = max(st, clamp(s1, 0.0, 1.0) * (0.6 + 0.4 * sin(tm * 4.0 + 6.283 * h1(id + 1.3))));
          }
        }
        L = vec4(col * ba, ba) + (1.0 - ba) * L;
        L = vec4(vec3(st), st) + (1.0 - st) * L;
        o = o + (1.0 - a) * L;
      }
      gl_FragColor = o;
    }`;
  const prog = fs => {
    const pr = gl.createProgram();
    gl.attachShader(pr, sh(gl.VERTEX_SHADER, "attribute vec2 p; varying vec2 uv; void main(){ uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }"));
    gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, HEAD + fs));
    gl.bindAttribLocation(pr, 0, "p"); gl.linkProgram(pr);
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    gl.useProgram(pr); gl.uniform1i(gl.getUniformLocation(pr, "v"), 0); gl.uniform1i(gl.getUniformLocation(pr, "f"), 1);
    const loc = {};
    pr.set = u => { for (const [n, val] of Object.entries(u)) {
      const l = n in loc ? loc[n] : (loc[n] = gl.getUniformLocation(pr, n));
      if (l == null) continue;
      if (typeof val === "number") gl.uniform1f(l, val); else gl[`uniform${val.length}fv`](l, val);
    } };
    return pr;
  };
  const PH = prog(FS_H), PV = prog(FS_V), PM = prog(FS_M);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
  const tex = () => { const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
    for (const [k, val] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
      [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, val);
    return t; };
  const srcTex = tex(), ft = [tex(), tex()], fb = [gl.createFramebuffer(), gl.createFramebuffer()];
  let fw = 0, fh = 0, buf = null;
  const fbos = (w, h) => {
    if (fw === w && fh === h) return;
    fw = w; fh = h;
    ft.forEach((t, k) => { gl.bindTexture(gl.TEXTURE_2D, t); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      gl.bindFramebuffer(gl.FRAMEBUFFER, fb[k]); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0); });
  };
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);   // stills: straight colour, alpha in .a
  // one pass: program, target framebuffer (null = canvas), size, uniforms, texture on unit 1 (never the target's own)
  const pass = (pr, target, w, h, u, t1) => {
    gl.useProgram(pr); pr.set(u);
    gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, t1); gl.activeTexture(gl.TEXTURE0);
    gl.bindFramebuffer(gl.FRAMEBUFFER, target); gl.viewport(0, 0, w, h);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  };
  const dc = document.createElement("canvas");
  const run = (src, sw, w, h, isSide, { gain = [1, 1, 1], tint = [1, 1, 1], outline = null, time = 0, slot = null } = {}) => {
    const ol = outline, sg = ol ? ol.px / ZB : 0, P = ol ? Math.ceil(ol.px + 3 * sg + 4) : 0, cw = w + 2 * P, ch = h + 2 * P;
    if (cv.width !== cw || cv.height !== ch) { cv.width = cw; cv.height = ch; }
    gl.bindTexture(gl.TEXTURE_2D, srcTex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
    const base = { side: isSide, hx: 0.5 / sw, pm: [cw / w, ch / h, -P / w, -P / h], px: [1 / w, 1 / h] };
    const sty = ol ? { solid: 1, glitter: 2, dotted: 3 }[ol.style] : 0;
    let dots = null;
    if (ol) {
      // blur at 1/f resolution with sigma ~2-3 texels there: separable, <= 25 taps per pass
      // (dotted only needs the iso-line for the dot centres: a 1/4+ grid keeps the CPU readback and marching small)
      const f = Math.max(sty === 3 ? 4 : 2, Math.round(sg / 2.5)), lw = Math.ceil(cw / f), lh = Math.ceil(ch / f), u = { lo: [1 / lw, 1 / lh], sg: sg * lw / cw };
      fbos(lw, lh); gl.bindTexture(gl.TEXTURE_2D, srcTex);   // fbos() used unit 0
      pass(PH, fb[0], lw, lh, { ...base, ...u }, ft[1]);
      pass(PV, fb[1], lw, lh, u, ft[0]);
      if (sty === 3) dots = gl2 && slot != null ? lateDots(slot, lw, lh, cw, ch, ol, time) : dotsFrom(readSync(lw, lh), lw, lh, cw, ch, ol);
    }
    pass(PM, null, cw, ch, { ...base, ck: choke, gain, tint, sty, th: TH, cs: ol?.cs || 1, tm: time,
      sd: ol ? STICKER_SHADOW / (ol.cs || 1) / ch : 0, op: [1 / cw, 1 / ch], ps: [cw, ch], oc: ol?.rgb || [1, 1, 1] }, ft[1]);
    if (!dots) { cv.pad = P; return cv; }
    if (dc.width !== cw || dc.height !== ch) { dc.width = cw; dc.height = ch; }
    const x = dc.getContext("2d");
    x.clearRect(0, 0, cw, ch); x.fillStyle = ol.color; x.beginPath();
    for (const [X, Y] of dots) { x.moveTo(X + ol.px / 2, Y); x.arc(X, Y, ol.px / 2, 0, Math.PI * 2); }
    x.fill(); x.drawImage(cv, 0, 0);
    dc.pad = P;
    return dc;
  };
  // the field in the bound framebuffer, read now (a readPixels stall: ~13 ms in headless, x 8 people per frame)
  const readSync = (lw, lh) => {
    buf = buf?.length === lw * lh * 4 ? buf : new Uint8Array(lw * lh * 4);
    gl.readPixels(0, 0, lw, lh, gl.RGBA, gl.UNSIGNED_BYTE, buf);
    return buf;
  };
  // moving strip (WebGL2): per cell `slot`, this frame's field goes to a pixel buffer behind a fence and the dots come
  // from the last field that has landed (one frame late, no stall); the first frame of a run reads synchronously
  const late = new Map();
  const lateDots = (slot, lw, lh, cw, ch, ol, time) => {
    let s = late.get(slot);
    if (!s) late.set(slot, s = { pbo: gl.createBuffer(), sync: null, time: -9, n: 0, dots: null });
    const same = time - s.time > 0 && time - s.time < 0.2 && s.n === lw * lh;   // the same run, same size
    let dots = same ? s.dots : null;
    gl.bindBuffer(gl.PIXEL_PACK_BUFFER, s.pbo);
    const landed = s.sync && gl.getSyncParameter(s.sync, gl.SYNC_STATUS) === gl.SIGNALED;
    if (same && landed) {
      buf = buf?.length === lw * lh * 4 ? buf : new Uint8Array(lw * lh * 4);
      gl.getBufferSubData(gl.PIXEL_PACK_BUFFER, 0, buf);
      dots = dotsFrom(buf, lw, lh, cw, ch, ol);
    }
    if (s.sync && (landed || !same)) { gl.deleteSync(s.sync); s.sync = null; }
    if (!s.sync) {
      if (s.n !== lw * lh) gl.bufferData(gl.PIXEL_PACK_BUFFER, lw * lh * 4, gl.STREAM_READ);
      gl.readPixels(0, 0, lw, lh, gl.RGBA, gl.UNSIGNED_BYTE, 0);
      s.sync = gl.fenceSync(gl.SYNC_GPU_COMMANDS_COMPLETE, 0); gl.flush();
    }
    gl.bindBuffer(gl.PIXEL_PACK_BUFFER, null);
    if (!dots) dots = dotsFrom(readSync(lw, lh), lw, lh, cw, ch, ol);
    Object.assign(s, { time, n: lw * lh, dots });
    return dots;
  };
  // dots from a field readback: centres on the iso-line half a band out, diameter = band width, ~1.75 widths apart
  const dotsFrom = (buf, lw, lh, cw, ch, ol) => {
    const F = new Float32Array(lw * lh);
    for (let y = 0; y < lh; y++) for (let x = 0; x < lw; x++) { const k = ((lh - 1 - y) * lw + x) * 4; F[y * lw + x] = buf[k] / 255 + buf[k + 1] / 65025; }
    const gx = cw / lw, gy = ch / lh;
    return isolines(F, lw, lh, 1 - Phi(ZB / 2)).flatMap(l => along({ pts: l.pts.map(([x, y]) => [(x + 0.5) * gx, (y + 0.5) * gy]), closed: l.closed }, ol.px * 1.75));
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
// mean luma of the face area of a cut-out: rows 15-55 % and the middle third of the person's bounding box, a > 0.5;
// also the person's centre-bottom (fractions of the image), the anchor of B's scaling of A
const lumaCache = new WeakMap();
function cutStats(img) {
  if (lumaCache.has(img)) return lumaCache.get(img);
  const W = 64, H = 48, c = Object.assign(document.createElement("canvas"), { width: W, height: H }), x = c.getContext("2d", { willReadFrequently: true });
  x.drawImage(img, 0, 0, W, H);
  const d = x.getImageData(0, 0, W, H).data;
  let x0 = W, x1 = -1, y0 = H, y1 = -1;
  for (let y = 0; y < H; y++) for (let k = 0; k < W; k++) if (d[(y * W + k) * 4 + 3] > 128) { x0 = Math.min(x0, k); x1 = Math.max(x1, k); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  let s = 0, n = 0;
  const rgb = [0, 0, 0];
  if (x1 >= 0) {
    const bw = x1 - x0 + 1, bh = y1 - y0 + 1;
    for (let y = Math.floor(y0 + 0.15 * bh); y <= y0 + 0.55 * bh; y++) for (let k = Math.floor(x0 + bw / 3); k <= x0 + 2 * bw / 3; k++) {
      const i = (y * W + k) * 4; if (d[i + 3] > 128) { s += 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2]; n++; for (let j = 0; j < 3; j++) rgb[j] += d[i + j]; }
    }
  }
  const st = { L: n > 8 ? s / n : null, rgb: n > 8 ? rgb.map(v => v / n) : null, cx: x1 >= 0 ? (x0 + x1 + 1) / 2 / W : 0.5, by: x1 >= 0 ? (y1 + 1) / H : 1 };
  lumaCache.set(img, st);
  return st;
}
const clampG = g => Math.min(1.35, Math.max(0.85, g));
const WB = { pull: 0.6, max: 0.06 };   // white balance: each side moves 60 % of the way to the shared colour, <= 6 % a channel
// One person's light, once per side: the face area's mean luma and rgb over all 4 of their cuts (cutStats, alpha > 0.5)
const sideCache = new WeakMap();
function sideLight(p) {
  if (sideCache.has(p)) return sideCache.get(p);
  const st = p.cuts.map(c => c?.cut && cutStats(c.cut)).filter(x => x?.L), n = st.length;
  const r = n ? { L: st.reduce((s, x) => s + x.L, 0) / n, rgb: [0, 1, 2].map(j => st.reduce((s, x) => s + x.rgb[j], 0) / n) } : null;
  sideCache.set(p, r);
  return r;
}
// Review #1 (round 3): A and B shot on different phones in different rooms read as two stickers. Per-channel gains for
// the two people, computed once per side (sideLight), so every still and every video frame of a side gets the same
// ones. Luma: both move toward their mean, pulled 30 % toward a booth-ish face luma (0.42), 0.85..1.35. White balance:
// the face's r/g and b/g move toward the two sides' geometric mean, bounded (WB), at the same luma.
// fixed = "a" | "b": that person is in the raw backdrop (cannot change), so only the other one moves toward it.
export function matchLight(a, b, fixed = null) {
  const A = sideLight(a), B = sideLight(b), one = [1, 1, 1];
  if (!A || !B) return [one, one];
  const t = fixed === "a" ? A.L : fixed === "b" ? B.L : 0.7 * (A.L + B.L) / 2 + 0.3 * 0.42 * 255;
  const ch = (X, Y, move) => {   // X's rgb gains toward Y's chromaticity
    if (!move) return one;
    const k = fixed ? WB.pull : WB.pull / 2;   // both move: each goes half the way, so they meet at the same point
    const f = [0, 2].map(j => Math.min(1 + WB.max, Math.max(1 - WB.max, ((Y.rgb[j] / Y.rgb[1]) / (X.rgb[j] / X.rgb[1])) ** k)));
    const g = [f[0], 1, f[1]], lum = 0.299 * g[0] + 0.587 + 0.114 * g[2], G = clampG(t / X.L);
    return g.map(v => v / lum * G);
  };
  return [ch(A, B, fixed !== "a"), ch(B, A, fixed !== "b")];
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
// one GL cut-out per side for the page ("a" / "b": they pad differently with an outline, and one shared canvas would be
// reallocated twice per cell). Round 7: not per person object - app.js makes fresh person objects for every pick /
// review cell and every 보정 pick, and one context each went past Chrome's live WebGL limit, which silently drops the
// oldest contexts (the face landmarker / beauty warp: B's 보정 did nothing, a 보정 pick blanked a person)
const alphas = {};
// clip frames for the moving strip (crossfaded at the loop seam), keyed by video; empty outside recordMovingStrip
const clipFrames = new Map();
// clips that would not load or decode: their cells use the stills (one bad clip must not cost the whole video)
const badClips = new WeakSet();
const clipUsable = v => !!v && !badClips.has(v) && !v.error && v.readyState >= 2;
// one person's source for cell i: video halves when t is set and a clip exists, else the stills; cut(o) -> canvas
// (with canvas.pad px of margin when o.outline is set)
function sources(p, i, t, slot, side) {
  const c = p.cuts[i], alpha = alphas[side] ||= createClipAlpha({ choke: CHOKE });
  if (t != null && clipUsable(c.clip)) {
    const f = clipFrames.get(c.clip) || c.clip, w = (f.videoWidth || f.width) >> 1, h = f.videoHeight || f.height;
    return { raw: f, rawW: w, rawH: h, cut: o => alpha.draw(f, { ...o, slot }), cutW: w, cutH: h };
  }
  const W = o => o.naturalWidth || o.videoWidth || o.width, H = o => o.naturalHeight || o.videoHeight || o.height;
  return { raw: c.raw, rawW: W(c.raw), rawH: H(c.raw), cut: o => alpha.still(c.cut, o), cutW: W(c.cut), cutH: H(c.cut) };
}
// The camera frame is 4:3 and the cell wider: crop mostly from the bottom so the head keeps its headroom.
const cellCrop = (w, h, aspect) => { const r = cropRect(w, h, aspect); r.sy *= 0.25; return r; };
const WRAP = 0.3;      // light wrap strength: how much blurred background spills over the cut-out edge
const CHOKE = 1;       // px the cut-out edge is eroded (min) before compositing: the only erosion in compose
const TINT = 0.07;     // backdrop colour cast on cut-outs over presets
const SHADOW = { a: 0.10, blur: 16, dy: 6 };   // soft contact shadow, lower half of the cell only

// ---------- a person's own size / place (Round 6; Round 3's B-moves-A is gone since Round 7) ----------
// p = { x, y, scale }: in the cell (= the strip crop of the camera frame), the person is scaled by `scale` about
// layoutAnchor(their cut) and then shifted by (x * cell width, y * cell height). CSS equivalent on a cell-sized box:
// transform-origin: anchor; transform: translate(x*w, y*h) scale(scale). Values are clamped.
const num = (v, lo, hi, d) => (Number.isFinite(+v) && v !== null && v !== "" ? Math.min(hi, Math.max(lo, +v)) : d);
function layoutOf(l) {
  const a = l && l.a;
  if (!a) return null;
  const r = { x: num(a.x, -1, 1, 0), y: num(a.y, -1, 1, 0), scale: num(a.scale, 0.25, 3, 1) };
  return Math.abs(r.x) > 0.002 || Math.abs(r.y) > 0.002 || Math.abs(r.scale - 1) > 0.005 ? r : null;
}
// A's centre-bottom as fractions of the cell (y can be a little > 1: the crop cuts the bottom of the camera frame)
export function layoutAnchor(cutImg, aspect = L.CW / L.CH) {
  const st = cutStats(cutImg), w = cutImg.naturalWidth || cutImg.width, h = cutImg.naturalHeight || cutImg.height, r = cellCrop(w, h, aspect);
  return { x: (st.cx * w - r.sx) / r.sw, y: (st.by * h - r.sy) / r.sh };
}
// Round 7: nobody moves A any more (A over A's own room showed A twice), so every background is allowed; `layout`
// (old links' layout.json) is ignored everywhere
export const bgAllowed = () => BACKGROUNDS.map(b => b.id);
// Round 4: pairs[i] = which of A's 4 cuts goes in cell i (B picked 4 of 8 shots, shot j floated A's cut j % 4; repeats
// allowed). pairs.json comes from the other phone: anything but 4 ints 0..3 = identity.
const pairsOf = p => (Array.isArray(p) && p.length === 4 && p.every(k => Number.isInteger(k) && k >= 0 && k <= 3) ? p : [0, 1, 2, 3]);

// Round 6: own = { b: [p x4] | null }, B's own size / place per cell (same maths as the old layout). Round 7: A is never
// placed (own.a and layout are ignored). B moved over B's own room would show twice: that cell falls back to the first
// background whose person stayed put (or a preset).
const ownOf = (own, side, i) => (side === "b" ? layoutOf({ a: own?.b?.[i] }) : null);
// solo = "a" | "b": only that person over the background (the 메이킹 영상 rows, Round 8); null = both
export function drawCell(ctx, rect, i, { a, b, bg, t = null, layout = null, outline = null, pairs = null, own = null, solo = null }) {
  const { w, h } = rect, aspect = w / h, ia = pairsOf(pairs)[i];   // A's cut for this cell; B's is cut i
  const at = p => (p === a ? ia : i);
  const back = canvas("back", w, h), bx = back.getContext("2d");
  const ol = outlineOf(outline), place = { a: null, b: ownOf(own, "b", i) };
  let B = typeof bg === "string" ? BACKGROUNDS.find(x => x.id === bg) : bg;
  if (!B.draw && place[B.id]) B = BACKGROUNDS.find(x => x.draw || !place[x.id]);
  // draw order background -> B -> A: A is always in front. The person who is the background gets their cut drawn
  // again on top of it (A so that B never covers A; B only to carry the outline).
  const self = B.draw ? null : B.id === "a" ? a : b;
  const people = solo ? [solo === "a" ? a : b] : B.id === "b" && !ol ? [a] : [b, a];
  const gains = matchLight(a, b, B.draw ? null : B.id);
  let tint;
  if (B.draw) { const pc = preset(B, w, h); bx.drawImage(pc, 0, 0); tint = pc.tint; }
  else {
    const s = sources(self, at(self), t, i, B.id), r = cellCrop(s.rawW, s.rawH, aspect);
    bx.drawImage(s.raw, r.sx, r.sy, r.sw, r.sh, 0, 0, w, h);
  }
  for (const p of people) {
    const s = sources(p, at(p), t, i, p === a ? "a" : "b"), r = cellCrop(s.cutW, s.cutH, aspect), k = w / r.sw;
    const Lp = place[p === a ? "a" : "b"], sc = Lp ? Lp.scale : 1;
    const cut = s.cut({ gain: gains[p === a ? 0 : 1], tint, time: t || 0, outline: ol && { ...ol, px: ol.px / (k * sc), cs: k * sc } });
    // source px -> cell px: the crop, then (A only) scale about the anchor and shift
    let m = k, ex = -r.sx * k, ey = -r.sy * k;
    if (Lp) {
      const an = layoutAnchor(p.cuts[at(p)].cut, aspect), ax = an.x * w, ay = an.y * h;
      m = k * sc; ex = ax * (1 - sc) + sc * ex + Lp.x * w; ey = ay * (1 - sc) + sc * ey + Lp.y * h;
    }
    const P = cut.pad || 0, per = canvas("per", w, h), px = per.getContext("2d");
    px.clearRect(0, 0, w, h);
    px.setTransform(m, 0, 0, m, ex - m * P, ey - m * P); px.drawImage(cut, 0, 0); px.setTransform(1, 0, 0, 1, 0, 0);
    // a sticker carries its own drop shadow and stays a crisp cut; a raw person's own cut sits exactly on itself
    if (ol || p === self) { bx.drawImage(per, 0, 0); continue; }
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
// frame art per (frame, date, caption): drawn once, so a filter / bg change or a mini tile reuses it
const prepCache = new Map();
function prepare(o) {
  const F = frameOf(o.frame), key = `${F.id}|${fmtDate(o.date)}|${o.caption || ""}|${o.font || ""}|${layoutFor(o.shape).shape}`;
  if (!prepCache.has(key)) {
    if (prepCache.size > 24) prepCache.delete(prepCache.keys().next().value);
    prepCache.set(key, prepareNow(o).catch(e => { prepCache.delete(key); throw e; }));
  }
  return prepCache.get(key);
}
async function prepareNow({ frame, date, caption = "", font = null, shape = "wide" }) {
  const F = frameOf(frame), L = layoutFor(shape);
  await loadFrameFonts(F, caption, font);
  const rects = cellRects(F, L.shape), d = fmtDate(date), o = { date: d, caption, cellRects: rects, font };
  const base = document.createElement("canvas"); base.width = L.W; base.height = L.H;
  F.draw(base.getContext("2d"), L, o);
  let over = null;
  if (F.over) { over = document.createElement("canvas"); over.width = L.W; over.height = L.H; F.over(over.getContext("2d"), L, o); }
  return { F, rects, base, over, L };
}
// Still cells are cached (Round 5, decorate speed): the composite by what it depends on (people, bg, layout, outline,
// pairs, size) and the filtered cell by that + filter, so a frame change recomposites nothing and a filter change only
// re-runs the filter. Video frames (t set) never touch the caches. Round 6 #5: tiles (scale < 1) keep their own small
// copies (miniCache), so the frame tiles' 7 cell sizes no longer push the photo's cells out (a theme tap went 0.10-0.34 s).
// Memory: cellCache 16 + filtCache 16 full cells (552x382 RGBA, 0.84 MB each) ~ 27 MB; miniCache 160 tiles (~40 KB each:
// 7 cell sizes x 4 + 6 filter tiles = 34 per look, three themes ~ 100) ~ 6 MB.
const ids = new WeakMap();
let nid = 0;
const oid = o => (o == null ? "-" : typeof o !== "object" ? String(o) : ids.get(o) ?? (ids.set(o, ++nid), nid));
const lru = (m, max) => { while (m.size > max) m.delete(m.keys().next().value); };
const cellCache = new Map(), filtCache = new Map(), miniCache = new Map();
function cellKey(i, r, { a, b, bg, layout, outline, pairs, own }) {
  const ia = pairsOf(pairs)[i], pa = a.cuts[ia], pb = b.cuts[i];
  return [i, r.w, r.h, typeof bg === "string" ? bg : bg?.id, JSON.stringify(outline || null), ia, JSON.stringify([ownOf(own, "a", i), ownOf(own, "b", i)]),
    oid(a), oid(b), oid(pa), oid(pb), oid(pa.raw), oid(pa.cut), oid(pb.raw), oid(pb.cut)].join("|");
}
function stillCell(r, i, o, scale = 1) {
  const key = cellKey(i, r, o), fk = key + "|" + o.filter;
  if (scale < 1) {
    const mk = fk + "|" + scale.toFixed(4);
    let m = miniCache.get(mk);
    if (m) miniCache.delete(mk);
    else {
      let cell = cellCache.get(key);
      if (!cell) { cell = canvas("mini", r.w, r.h); drawCell(cell.getContext("2d"), { x: 0, y: 0, w: r.w, h: r.h }, i, { ...o, t: null }); }
      const f = filt.apply(cell, o.filter, 1, i + 1);
      m = Object.assign(document.createElement("canvas"), { width: Math.max(1, Math.round(r.w * scale)), height: Math.max(1, Math.round(r.h * scale)) });
      m.getContext("2d").drawImage(f, 0, 0, m.width, m.height);
    }
    miniCache.set(mk, m); lru(miniCache, 160);
    return m;
  }
  let out = filtCache.get(fk);
  if (out) { filtCache.delete(fk); filtCache.set(fk, out); return out; }     // most recent last
  let cell = cellCache.get(key);
  if (!cell) {
    cell = Object.assign(document.createElement("canvas"), { width: r.w, height: r.h });
    drawCell(cell.getContext("2d"), { x: 0, y: 0, w: r.w, h: r.h }, i, { ...o, t: null });
    cellCache.set(key, cell); lru(cellCache, 16);
  } else { cellCache.delete(key); cellCache.set(key, cell); }
  const f = filt.apply(cell, o.filter, 1, i + 1);
  if (f === cell) return cell;
  out = Object.assign(document.createElement("canvas"), { width: r.w, height: r.h });
  out.getContext("2d").drawImage(f, 0, 0);
  filtCache.set(fk, out); lru(filtCache, 16);
  return out;
}
function paint(ctx, st, { a, b, bg, filter = "original", layout = null, outline = null, pairs = null, own = null, stickers = null }, t, scale = 1) {
  filt ||= createFilter();
  ctx.drawImage(st.base, 0, 0);
  st.rects.forEach((r, i) => {
    let out;
    if (t == null) out = stillCell(r, i, { a, b, bg, filter, layout, outline, pairs, own }, scale);
    else {
      const cell = canvas("cell", r.w, r.h), cx = cell.getContext("2d");
      drawCell(cx, { x: 0, y: 0, w: r.w, h: r.h }, i, { a, b, bg, t, layout, outline, pairs, own });
      out = filt.apply(cell, filter, 1, i + 1);
    }
    ctx.save(); ctx.beginPath(); ctx.roundRect(r.x, r.y, r.w, r.h, st.F.radius || 0); ctx.clip();
    ctx.drawImage(out, r.x, r.y, r.w, r.h); ctx.restore();
  });
  if (st.over) ctx.drawImage(st.over, 0, 0);
  if (stickers?.length) drawStickers(ctx, stickers);
}

// The still strip on a canvas. scale < 1 = a small preview (mini tiles); cell = k: only cell k (filter tiles).
// `into` paints an existing canvas (the done screen's preview) instead of a new one.
export async function stripCanvas(o, { scale = 1, cell = null, into = null } = {}) {
  const st = await prepare(o);
  if (o.stickers?.length) await stickersReady(o.stickers);
  const r = cell == null ? { x: 0, y: 0, w: st.L.W, h: st.L.H } : st.rects[cell];
  const cv = into || document.createElement("canvas"), w = Math.round(r.w * scale), h = Math.round(r.h * scale);
  if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
  const x = cv.getContext("2d");
  x.setTransform(scale, 0, 0, scale, -r.x * scale, -r.y * scale);
  paint(x, st, o, null, scale);
  x.setTransform(1, 0, 0, 1, 0, 0);
  return cv;
}
export async function drawStrip(o) {
  const cv = await stripCanvas(o);
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
  const L = st.L, s = Math.min((H * 0.84) / L.H, (W * 0.9) / L.W), sw = L.W * s, sh = L.H * s, ox = (W - sw) / 2, oy = (H - sh) / 2 - 40;
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
// fx = { a, b } (Round 5, beauty.js): fx[side](frame, tMs, video) edits each decoded clip frame in place before the
// cut-out and compositing; frame = that clip's own canvas [raw | matte] (the same object all recording), tMs = the
// frame's clip time (repeats on every loop), video = the clip element (a stable cache key across recordings).
// signal (AbortSignal): an abort stops the render within a frame and rejects with DOMException "AbortError".
// mattes (clipmatte.js clipMattes): Map<clip video, HQ moving cut>; frame-exact path only, other clips keep their matte.
export async function recordMovingStrip({ a, b, bg, frame, filter, date, caption, font = null, shape = "wide", layout = null, outline = null, pairs = null, own = null, stickers = null, format = "strip", fx = null, signal = null, mattes = null, onProgress }) {
  // the clips the cells use, each once with its first cell's phase: two cells showing the same A cut share one video and
  // read the same frame per tick (seeking it twice, or two loop-seam states, would break one of them)
  const P = pairsOf(pairs), vids = [], phase = [], fxs = [];
  for (const [n, c, side] of [0, 1, 2, 3].flatMap(i => [[i, a.cuts[P[i]], "a"], [i, b.cuts[i], "b"]])) {
    if (!clipUsable(c.clip)) continue;            // no / broken clip: that person stays still in this cell
    if (!vids.includes(c.clip)) { vids.push(c.clip); phase.push(PHASE[n]); fxs.push(fx?.[side] || null); }
  }
  const st = await prepare({ frame, date, caption, font, shape }), L = st.L;
  if (stickers?.length) await stickersReady(stickers);
  const story = format === "story" ? await storyBack(st) : null;
  const cv = document.createElement("canvas"); cv.width = story ? STORY.W : L.W; cv.height = story ? STORY.H : L.H;
  const o = { a, b, bg, filter, layout, outline, pairs, own, stickers, signal, mattes };
  // frame-exact first: output frame n is painted for t = n/30 s and encoded with that timestamp, so a busy phone only
  // renders slower; the real-time MediaRecorder path drops frames under load (10-27 phone: the saved strip stuttered)
  const cfg = await encoderConfig(cv.width, cv.height);
  if (cfg && window.VideoDecoder) {
    try { return { blob: await renderOffline(cv, st, story, vids, phase, fxs, cfg, o, onProgress), type: "video/mp4" }; }
    catch (e) { if (e?.name === "AbortError") throw e; console.warn("[compose] offline render failed, real-time fallback", e); }
  }
  if (!window.MediaRecorder) throw new Error("no MediaRecorder");
  const ok = t => MediaRecorder.isTypeSupported(t);
  const mp4 = ["video/mp4;codecs=avc1", "video/mp4"].find(ok), webm = ["video/webm;codecs=vp9", "video/webm"].find(ok);
  let lastErr = new Error("no recorder type");
  for (const type of [mp4, webm].filter(Boolean)) {
    try { return { blob: await recordOnce(cv, st, story, vids, phase, fxs, type, o, onProgress), type: type.split(";")[0] }; }
    catch (e) { if (e?.name === "AbortError") throw e; lastErr = e; }
  }
  throw lastErr;
}

// ---------- frame-exact render: WebCodecs H.264 + mp4-muxer (MIT, vendored in js/vendor) ----------
export const renderStats = {};   // last offline render: ms, frames decoded per clip (dev pages, tests)
async function encoderConfig(w, h) {
  if (window.__noWebCodecs || !window.VideoEncoder || !window.VideoFrame) return null;   // __noWebCodecs: tests of the fallback
  for (const codec of ["avc1.640028", "avc1.4d0028", "avc1.42e028"]) {   // level 4.0 fits 1080x1920
    const c = { codec, width: w, height: h, bitrate: BPS, framerate: FPS, avc: { format: "avc" } };
    if ((await VideoEncoder.isConfigSupported(c).catch(() => null))?.supported) return c;
  }
  return null;
}

// Our recorded clips (MediaRecorder / capture.js MP4: one H.264 track, moov + moof/mdat fragments) -> decoder config +
// samples. null for anything else (WebM from older browsers, plain MP4): the real-time path takes those.
// The other person's clip is untrusted input: every box must fit in its parent, sample tables in their box, samples in
// the file, at most MAX_SAMPLES; anything off -> null.
const MAX_SAMPLES = 1200;            // 10 s at 120 fps; our clips are ~60
export function readFmp4(buf) {
  try { return parseFmp4(buf); } catch { return null; }
}
function parseFmp4(buf) {
  const d = new DataView(buf), u8 = new Uint8Array(buf), out = { samples: [], ts: 0, trex: [0, 0, 0] };
  const bad = () => { throw new Error("mp4"); };
  const kids = (s, e, f, depth = 0) => {
    if (depth > 8) bad();
    for (let o = s; o + 8 <= e;) {
      let n = d.getUint32(o), h = 8; const t = String.fromCharCode(...u8.subarray(o + 4, o + 8));
      if (n === 1) { if (o + 16 > e) bad(); n = Number(d.getBigUint64(o + 8)); h = 16; } else if (n === 0) n = e - o;
      if (n < h || o + n > e) bad();
      f(t, o + h, o + n, o, depth + 1); o += n;
    }
  };
  const need = (p, k, e) => { if (p + k > e) bad(); };
  const walk = (t, s, e, o, depth) => {
    if (["moov", "trak", "mdia", "minf", "stbl", "mvex"].includes(t)) return kids(s, e, walk, depth);
    if (t === "mdhd") { const v1 = u8[s] === 1; need(s, v1 ? 24 : 16, e); out.ts = d.getUint32(s + (v1 ? 20 : 12)); }
    else if (t === "trex") { need(s, 24, e); out.trex = [d.getUint32(s + 12), d.getUint32(s + 16), d.getUint32(s + 20)]; }
    else if (t === "stsd") { need(s, 8, e); kids(s + 8, e, (t2, s2, e2) => {
      if (t2 !== "avc1") return;
      need(s2, 78, e2);
      out.w = d.getUint16(s2 + 24); out.h = d.getUint16(s2 + 26);
      kids(s2 + 78, e2, (t3, s3, e3) => { if (t3 === "avcC") out.desc = u8.slice(s3, e3); }, depth + 2);
    }, depth + 1); }
    else if (t === "moof") kids(s, e, (t2, s2, e2) => {
      if (t2 !== "traf") return;
      let base = o, dts = 0, [dd, ds, df] = out.trex;
      kids(s2, e2, (t3, p, e3) => {
        if (!["tfhd", "tfdt", "trun"].includes(t3)) return;
        need(p, 4, e3);
        const fl = d.getUint32(p) & 0xffffff; p += 4;
        if (t3 === "tfhd") {
          need(p, 4 + (fl & 1 ? 8 : 0) + (fl & 2 ? 4 : 0) + (fl & 8 ? 4 : 0) + (fl & 0x10 ? 4 : 0) + (fl & 0x20 ? 4 : 0), e3);
          p += 4;
          if (fl & 1) { base = Number(d.getBigUint64(p)); p += 8; }
          if (fl & 2) p += 4;
          if (fl & 8) { dd = d.getUint32(p); p += 4; }
          if (fl & 0x10) { ds = d.getUint32(p); p += 4; }
          if (fl & 0x20) df = d.getUint32(p);
        } else if (t3 === "tfdt") { const v1 = u8[p - 4] === 1; need(p, v1 ? 8 : 4, e3); dts = v1 ? Number(d.getBigUint64(p)) : d.getUint32(p); }
        else {
          need(p, 4 + (fl & 1 ? 4 : 0) + (fl & 4 ? 4 : 0), e3);
          const n = d.getUint32(p); p += 4;
          const per = 4 * [0x100, 0x200, 0x400, 0x800].filter(b => fl & b).length;
          if (out.samples.length + n > MAX_SAMPLES) bad();
          let at = base + (fl & 1 ? d.getInt32(p) : 0); if (fl & 1) p += 4;
          let first = null; if (fl & 4) { first = d.getUint32(p); p += 4; }
          need(p, n * per, e3);
          for (let i = 0; i < n; i++) {
            const dur = fl & 0x100 ? d.getUint32((p += 4) - 4) : dd, size = fl & 0x200 ? d.getUint32((p += 4) - 4) : ds;
            let f = fl & 0x400 ? d.getUint32((p += 4) - 4) : df; if (i === 0 && first != null) f = first;
            const cto = fl & 0x800 ? d.getInt32((p += 4) - 4) : 0;
            if (!size || at < 0 || at + size > buf.byteLength) bad();
            out.samples.push({ data: u8.subarray(at, at + size), pts: dts + cto, key: !(f & 0x10000) });
            at += size; dts += dur;
          }
        }
      }, depth + 1);
    }, depth);
  };
  kids(0, buf.byteLength, walk);
  const S = out.samples;
  if (!out.desc || out.desc.length < 7 || !out.ts || !S.length || !S[0].key) return null;
  if (!(out.w > 0 && out.w <= 4096 && out.h > 0 && out.h <= 4096)) return null;
  const hx = k => out.desc[k].toString(16).padStart(2, "0");
  return { config: { codec: `avc1.${hx(1)}${hx(2)}${hx(3)}`, codedWidth: out.w, codedHeight: out.h, description: out.desc },
    samples: S.map(s => ({ ...s, us: Math.round(s.pts * 1e6 / out.ts) })) };
}

// One clip decoded frame by frame (WebCodecs), looping, starting at `phase` s; at most a few frames held at once.
// frame = the clip's own canvas (fx edits it in place), advance() = one output frame later. A ~30 fps clip moves exactly
// one frame per output frame: a recorded clip's timestamps jitter by up to half a frame, and sampling them by time
// repeats one frame and skips the next. Slower clips keep time (their frames repeat).
// fromEnd (s): start that long before the clip's last frame, read from its samples (a fragmented MP4's <video>.duration
// can be Infinity, review r8 /done M1); phase is ignored then
async function clipSource(v, phase, fx, hq, fromEnd = null) {
  const buf = await (await fetch(v.currentSrc || v.src)).arrayBuffer(), mp4 = readFmp4(buf);
  // not an MP4 at all (WebM): the real-time path plays it. An MP4 we cannot read or decode: a broken clip (that cell's still)
  const tag = buf.byteLength >= 8 ? String.fromCharCode(...new Uint8Array(buf, 4, 4)) : "";
  if (!mp4 && tag !== "ftyp") throw Object.assign(new Error("clip not MP4"), { notMp4: true });
  if (!mp4 || !(await VideoDecoder.isConfigSupported(mp4.config).catch(() => null))?.supported) throw new Error("clip not decodable");
  const S = mp4.samples, N = S.length, us = S.map(s => s.us).sort((x, y) => x - y);
  const span = (us[N - 1] - us[0]) * N / Math.max(1, N - 1) / 1e6 || N / FPS, r = N / (span * FPS);
  const step = Math.abs(r - 1) < 0.05 ? 1 : Math.min(2, r);
  const q = [];
  const start = fromEnd == null ? us[0] + phase * 1e6 : Math.max(us[0], us[N - 1] + span * 1e6 / N - fromEnd * 1e6);
  let failed = null, wake = null, k = 0, skip = start - 1000;   // frames before the start: decoded, dropped
  const dec = new VideoDecoder({ output: f => {
    if (f.timestamp < skip) f.close(); else q.push(f);
    wake?.();
  }, error: e => { failed = e; wake?.(); } });
  dec.configure(mp4.config);
  // fromEnd (the making rows) holds the last frame at the end instead of looping: a row that wants one frame more
  // than its window holds would otherwise end on the clip's first frame (review r8 /done r2 M1)
  let ended = false;
  const feed = async () => {          // keep ~2 frames ready; at the end, flush and start over at the first (key) sample
    while (q.length < 2 && !failed) {
      if (k === N && fromEnd != null) { if (!ended) { ended = true; await dec.flush(); } break; }
      if (k === N) { await dec.flush(); k = 0; skip = -Infinity; continue; }
      if (dec.decodeQueueSize < 3) { const s = S[k++]; dec.decode(new EncodedVideoChunk({ type: s.key ? "key" : "delta", timestamp: s.us, data: s.data })); continue; }
      await new Promise(res => { wake = res; setTimeout(res, 50); }); wake = null;
    }
    if (failed) throw failed;
  };
  const frame = Object.assign(document.createElement("canvas"), { width: mp4.config.codedWidth, height: mp4.config.codedHeight });
  const fctx = frame.getContext("2d");
  const take = async () => {
    await feed();
    const f = q.shift();
    if (!f) return;                  // past the end of a held clip: keep the last frame
    if (frame.width !== f.displayWidth || frame.height !== f.displayHeight) { frame.width = f.displayWidth; frame.height = f.displayHeight; }
    fctx.drawImage(f, 0, 0); const t = f.timestamp; f.close();
    hq?.apply(frame, t);             // the HQ moving cut (clipmatte.js) replaces the matte half, before any fx warp
    fx?.(frame, t / 1000, v);
    src.n++;
  };
  let acc = 0;
  const src = { frame, n: 0, step, async advance() { acc += step; while (acc >= 1) { acc -= 1; await take(); } },
    close() { q.forEach(f => f.close()); q.length = 0; if (dec.state !== "closed") dec.close(); } };
  try { await take(); } catch (e) { src.close(); throw e; }       // no decoder left open behind a clip that fails here
  return src;
}

const PREP = 0.1;     // share of the progress spent opening the clips (a phone sat at "0%" through it, review r3 #5)
async function renderOffline(cv, st, story, vids, phase, fxs, cfg, opts, onProgress) {
  const { Muxer, ArrayBufferTarget } = await import("./vendor/mp4-muxer.mjs");
  const t0 = performance.now(), ctx = cv.getContext("2d"), L = st.L;
  const strip = story ? Object.assign(document.createElement("canvas"), { width: L.W, height: L.H }) : cv, sctx = strip.getContext("2d");
  const still = Object.assign(document.createElement("canvas"), { width: L.W, height: L.H });
  paint(still.getContext("2d"), st, opts, null);
  const muxer = new Muxer({ target: new ArrayBufferTarget(), video: { codec: "avc", width: cv.width, height: cv.height, frameRate: FPS },
    fastStart: "in-memory", firstTimestampBehavior: "offset" });
  let failed = null;
  const enc = new VideoEncoder({ output: (ch, meta) => muxer.addVideoChunk(ch, meta), error: e => { failed = e; } });
  enc.configure(cfg);
  const srcs = [];
  try {
    for (let n = 0; n < vids.length; n++) {
      // a WebM clip -> the real-time path for all; an MP4 we cannot read or decode (e.g. a header with no frames, which
      // a <video> still "loads") -> that cell's still
      try { srcs.push(await clipSource(vids[n], phase[n], fxs[n], opts.mattes?.get(vids[n]))); onProgress?.(PREP * (n + 1) / vids.length); }
      catch (e) { if (e?.notMp4 || e?.name === "AbortError") throw e; badClips.add(vids[n]); srcs.push(null); console.warn("[compose] clip skipped", e); }
    }
    const N = SECONDS * FPS;
    for (let n = 0; n < N; n++) {
      if (failed) throw failed;
      opts.signal?.throwIfAborted();
      if (n) for (const s of srcs) await s?.advance();
      const t = n / FPS;
      srcs.forEach((s, k) => s && clipFrames.set(vids[k], s.frame));
      if (t < SECONDS - HOLD) paint(sctx, st, opts, t); else sctx.drawImage(still, 0, 0);   // the last 0.3 s hold the photo strip
      if (story) { ctx.drawImage(story.c, 0, 0); ctx.drawImage(strip, story.ox, story.oy, story.sw, story.sh); }
      const f = new VideoFrame(cv, { timestamp: Math.round(n * 1e6 / FPS), duration: Math.round(1e6 / FPS) });
      enc.encode(f, { keyFrame: n % (2 * FPS) === 0 }); f.close();
      while (enc.encodeQueueSize > 3 && !failed) await new Promise(r => setTimeout(r, 4));   // a few frames in flight, never 180
      onProgress?.(PREP + (1 - PREP) * n / N);
    }
    await enc.flush();
    if (failed) throw failed;
    muxer.finalize();
    Object.assign(renderStats, { ms: Math.round(performance.now() - t0), frames: N, decoded: srcs.map(s => s?.n ?? null), step: srcs.map(s => s && +s.step.toFixed(3)),
      hq: vids.filter(v => opts.mattes?.has(v)).length });
    onProgress?.(1);
    return new Blob([muxer.target.buffer], { type: "video/mp4" });
  } finally {
    if (enc.state !== "closed") enc.close();
    srcs.forEach(s => s?.close());
    clipFrames.clear();
  }
}

// ---------- the 메이킹 영상 (Round 8 B, making.js draws it): both people's raw clips row by row, then the strip ----------
// Each row plays the last ROW_DUR s of its clip: that person alone, cut out over the strip's chosen background (operator
// 10-30: not the real room, which a public post would show), with their 보정, size / place and the strip's filter; one
// row = two decoders at a time; a clip that will not decode shows the person's still instead (no motion).
// Frame-exact (WebCodecs) where it can; else real time through MediaRecorder with the photos only.
export async function recordMaking({ a, b, bg, frame, filter = "original", date, caption, font = null, shape = "wide", layout = null, outline = null, pairs = null, own = null, stickers = null, fx = null, mattes = null, ui = "film", signal = null, onProgress }) {
  filt ||= createFilter();
  const st = await prepare({ frame, date, caption, font, shape }), L = st.L;
  if (stickers?.length) await stickersReady(stickers);
  const strip = Object.assign(document.createElement("canvas"), { width: L.W, height: L.H });
  paint(strip.getContext("2d"), st, { a, b, bg, filter, layout, outline, pairs, own, stickers }, null);
  await Promise.all(makingFonts(ui).map(f => document.fonts.load(f, "따로 같이 네컷 착! 2026. 10. 30").catch(() => null)));
  const P = pairsOf(pairs), rows = [0, 1, 2, 3].map(i => [a.cuts[P[i]], b.cuts[i]]), fxs = [fx?.a || null, fx?.b || null];
  const cells = [[null, null, null, null], [null, null, null, null]], CW = 552, CHt = 414;   // the rows' 4:3 cells
  // a room background holds its own person (B alone over A's room would show A too): the rows then use the theme's booth
  const soloBg = BACKGROUNDS.find(x => x.id === (typeof bg === "string" ? bg : bg?.id))?.draw ? bg : ui === "cute" ? "booth-pink" : "white-wall";
  // person k, row i: drawn alone over the background (t = null: their still), then the filter
  const put = (k, i, t) => {
    const c = cells[k][i] ||= Object.assign(document.createElement("canvas"), { width: CW, height: CHt }), x = c.getContext("2d");
    drawCell(x, { x: 0, y: 0, w: CW, h: CHt }, i, { a, b, bg: soloBg, t, outline, pairs, own, solo: k ? "b" : "a" });
    const f = filt.apply(c, filter, 1, i + 1);
    if (f !== c) x.drawImage(f, 0, 0, CW, CHt);
  };
  const cv = Object.assign(document.createElement("canvas"), { width: MAKING.W, height: MAKING.H }), ctx = cv.getContext("2d");
  const look = { ui, cells, strip, date: makingDate() }, N = Math.round(MAKING.L * MAKING.FPS);
  const cfg = await encoderConfig(cv.width, cv.height);
  if (cfg && window.VideoDecoder) {
    try { return { blob: await makingOffline(), type: "video/mp4" }; }
    catch (e) { if (e?.name === "AbortError") throw e; console.warn("[compose] making: offline render failed, real-time fallback", e); }
  }
  return makingRealtime();

  async function makingOffline() {
    const { Muxer, ArrayBufferTarget } = await import("./vendor/mp4-muxer.mjs");
    const muxer = new Muxer({ target: new ArrayBufferTarget(), video: { codec: "avc", width: cv.width, height: cv.height, frameRate: MAKING.FPS },
      fastStart: "in-memory", firstTimestampBehavior: "offset" });
    let failed = null, row = -1, srcs = [null, null];
    const enc = new VideoEncoder({ output: (ch, meta) => muxer.addVideoChunk(ch, meta), error: e => { failed = e; } });
    enc.configure(cfg);
    const closeRow = () => { srcs.forEach(s => s?.close()); srcs = [null, null]; };
    try {
      for (let n = 0; n < N; n++) {
        if (failed) throw failed;
        signal?.throwIfAborted();
        const t = n / MAKING.FPS, i = ROW_T.findIndex(t0 => t >= t0 && t < t0 + ROW_DUR);
        if (i !== row) {
          closeRow(); row = i;
          if (i >= 0) for (const k of [0, 1]) {
            const c = rows[i][k], v = c.clip;
            if (clipUsable(v)) try { srcs[k] = await clipSource(v, 0, fxs[k], mattes?.get(v), ROW_DUR); }
              catch (e) { if (e?.name === "AbortError") throw e; console.warn("[compose] making: clip shown still", e); }
            if (!srcs[k]) put(k, i, null);
          }
        } else if (i >= 0) for (const s of srcs) await s?.advance();
        if (i >= 0) srcs.forEach((s, k) => { if (s) { clipFrames.set(rows[i][k].clip, s.frame); put(k, i, t); } });
        drawMaking(ctx, t, look);
        const f = new VideoFrame(cv, { timestamp: Math.round(n * 1e6 / MAKING.FPS), duration: Math.round(1e6 / MAKING.FPS) });
        enc.encode(f, { keyFrame: n % (2 * MAKING.FPS) === 0 }); f.close();
        while (enc.encodeQueueSize > 3 && !failed) await new Promise(r => setTimeout(r, 4));
        onProgress?.(n / N);
      }
      await enc.flush();
      if (failed) throw failed;
      muxer.finalize();
      onProgress?.(1);
      return new Blob([muxer.target.buffer], { type: "video/mp4" });
    } finally { closeRow(); if (enc.state !== "closed") enc.close(); clipFrames.clear(); }
  }

  // ponytail: no WebCodecs (older iOS) = photos only, painted in real time; add <video> seeking if a phone needs motion here
  async function makingRealtime() {
    if (!window.MediaRecorder) throw new Error("no MediaRecorder");
    const ok = t => MediaRecorder.isTypeSupported(t);
    const types = [["video/mp4;codecs=avc1", "video/mp4"].find(ok), ["video/webm;codecs=vp9", "video/webm"].find(ok)].filter(Boolean);
    rows.forEach((r, i) => r.forEach((c, k) => put(k, i, null)));
    let lastErr = new Error("no recorder type");
    for (const type of types) {
      try { return await makingOnce(type); }
      catch (e) { if (e?.name === "AbortError") throw e; lastErr = e; }
    }
    throw lastErr;
  }
  async function makingOnce(type) {
    await warmEncoder(type, cv.width, cv.height);
    const shown = [[null, null, null, null], [null, null, null, null]];
    const frameAt = t => {
      cells.forEach((row, k) => row.forEach((c, i) => { shown[k][i] = t >= ROW_T[i] ? c : null; }));
      drawMaking(ctx, t, { ...look, cells: shown });
    };
    let stream = cv.captureStream(0), track = stream.getVideoTracks()[0];
    if (!track?.requestFrame) { stream.getTracks().forEach(tr => tr.stop()); stream = cv.captureStream(MAKING.FPS); track = null; }
    frameAt(0);
    const rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: BPS }), chunks = [];
    let failed = null;
    rec.ondataavailable = e => e.data.size && chunks.push(e.data);
    rec.onerror = e => { failed = e.error || new Error("recorder"); };
    const stopped = new Promise(r => (rec.onstop = r)), started = new Promise(r => (rec.onstart = r));
    rec.start(1000); track?.requestFrame();
    await Promise.race([started, new Promise(r => setTimeout(r, 10000))]);
    const t0 = performance.now();
    await new Promise(done => {
      const tick = () => {
        const t = (performance.now() - t0) / 1000;
        if (failed) return done();
        if (signal?.aborted) { failed = new DOMException("aborted", "AbortError"); return done(); }
        try { frameAt(Math.min(t, MAKING.L)); } catch (e) { failed = e; return done(); }
        track?.requestFrame();
        onProgress?.(Math.min(1, t / MAKING.L));
        if (t >= MAKING.L) return setTimeout(done, 100);
        requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    rec.stop(); await stopped;
    stream.getTracks().forEach(tr => tr.stop());
    if (failed) throw failed;
    return { blob: new Blob(chunks, { type: type.split(";")[0] }), type: type.split(";")[0] };
  }
}

// Per clip: a copy of the frame shown last tick; when the clip wraps, that copy fades out over the new frames.
function clipState(v, fx) {
  const mk = () => Object.assign(document.createElement("canvas"), { width: v.videoWidth, height: v.videoHeight });
  return { v, fx, last: mk(), snap: mk(), out: mk(), prevT: v.currentTime, wrapAt: -1, drawn: -1 };
}
function tickClip(c, t) {
  const ct = c.v.currentTime;
  if (ct + 0.05 < c.prevT) { [c.last, c.snap] = [c.snap, c.last]; c.wrapAt = t; }   // wrapped: keep the old last frame
  c.prevT = ct;
  if (ct !== c.drawn) { c.drawn = ct; c.last.getContext("2d").drawImage(c.v, 0, 0); c.fx?.(c.last, ct * 1000, c.v); }
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

async function recordOnce(cv, st, story, vids, phase, fxs, type, opts, onProgress) {
  await warmEncoder(type, cv.width, cv.height);
  const ctx = cv.getContext("2d"), L = st.L;
  const strip = story ? Object.assign(document.createElement("canvas"), { width: L.W, height: L.H }) : cv, sctx = strip.getContext("2d");
  const show = () => { if (story) { ctx.drawImage(story.c, 0, 0); ctx.drawImage(strip, story.ox, story.oy, story.sw, story.sh); } };
  const still = Object.assign(document.createElement("canvas"), { width: L.W, height: L.H });
  paint(still.getContext("2d"), st, opts, null);
  // start every clip at its cell's phase and wait until all of them show that frame
  vids.forEach((v, n) => { v.loop = true; v.muted = true; v.pause(); v.currentTime = Math.min(phase[n], (v.duration || 1) * 0.5); });
  await Promise.all(vids.map(v => (v.readyState >= 2 && !v.seeking ? null
    : Promise.race([new Promise(r => v.addEventListener("seeked", r, { once: true })), new Promise(r => setTimeout(r, 3000))]))));   // a broken clip never seeks
  const states = vids.map((v, n) => clipState(v, fxs[n]));
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
    // a broken clip may never settle play(): it just stays on its frame
    await Promise.all(vids.map(v => Promise.race([v.play().catch(() => {}), new Promise(r => setTimeout(r, 3000))])));
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
        if (opts.signal?.aborted) { failed = new DOMException("aborted", "AbortError"); return done(); }   // deco R5: a change mid-recording
        frame(Math.min(t, SECONDS)); push();
        onProgress?.(Math.min(1, t / SECONDS));
        if (t >= SECONDS) return setTimeout(done, 100);    // one more held frame reaches the encoder
        requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    rec.stop(); await stopped;
    stream.getTracks().forEach(tr => tr.stop());
    opts.signal?.throwIfAborted();
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
