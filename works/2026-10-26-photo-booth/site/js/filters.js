// Booth-style filters for one composed cell (people + background, never the frame art).
// One pipeline for every filter: skin smoothing (bilateral, skin tones only) -> (mono) -> per-channel tone LUT
// -> saturation -> shadow tint -> glow (screen) -> grain -> vignette. WebGL first; the same math runs on ImageData
// when WebGL is missing. Numbers: research/review/photo.md (round 2), tuned by eye on dark skin.

export const FILTERS = [
  { id: "original", label: "원본" },
  { id: "bbosasi", label: "뽀샤시" },
  { id: "mono", label: "흑백" },
  { id: "warm", label: "따뜻" },
  { id: "cool", label: "시원" },
  { id: "film", label: "필름" },
];

// exp: exposure, mul: channel gains, lift: output black point per channel (number or [r,g,b], 0..1), cap: white
// point, gamma, contrast, curve: extra tone points [[in,out]...] (0..255), sat, shadow: rgb added in the shadows
// (0..255, x (1-l)^2), satBg: saturation off skin (brightening clips the backdrop's top channel; this gives the colour back),
// smooth: [skin, rest] bilateral-smoothing mix, glow: [threshold 0..1, alpha, alpha multiplier off skin (default 1)],
// grain: sigma (0..255), vig: corner darkening, gray: mono mix weights.
const P = {
  bbosasi: { exp: 1.09, mul: [1.05, 1.0, 0.975], lift: [0.06, 0.045, 0.04], cap: 0.97, gamma: 0.88, contrast: 0.93,
    sat: 1.14, satBg: 1.5, shadow: [14, 6, 4], smooth: [0.9, 0.2], glow: [0.45, 0.34, 0.45], grain: 0, vig: 0 },
  mono: { mono: true, gray: [0.35, 0.55, 0.10], exp: 1.06, mul: [1, 1, 1], lift: 0.02, cap: 1, gamma: 1, contrast: 1.04,
    curve: [[0, 0], [48, 44], [96, 104], [160, 184], [255, 255]], sat: 0, shadow: [0, 0, 0], smooth: [0.3, 0.1],
    glow: [0.7, 0.10], grain: 11, vig: 0.14 },
  warm: { exp: 1.05, mul: [1.06, 1.0, 0.91], lift: 0.02, cap: 1, gamma: 0.97, contrast: 1.0, sat: 1.06,
    shadow: [4, 1, 0], smooth: [0.35, 0.1], glow: [0.75, 0.12], grain: 0, vig: 0.05 },
  cool: { exp: 1.04, mul: [0.97, 1.0, 1.04], lift: 0.02, cap: 1, gamma: 0.97, contrast: 1.0, sat: 1.05,
    shadow: [0, 2, 4], smooth: [0.35, 0.1], glow: [0.75, 0.12], grain: 0, vig: 0.05 },
  film: { exp: 1.02, mul: [1.05, 1.0, 0.94], lift: 0.07, cap: 0.96, gamma: 1.0, contrast: 1.06,
    curve: [[0, 0], [64, 58], [128, 130], [192, 200], [255, 255]], sat: 0.82, shadow: [-4, 5, 5], smooth: [0.15, 0.05],
    glow: [0.72, 0.15], grain: 9, vig: 0.16 },
};

function interp(pts, v) {             // piecewise linear through (in,out) points, 0..255
  for (let k = 1; k < pts.length; k++) if (v <= pts[k][0]) {
    const [x0, y0] = pts[k - 1], [x1, y1] = pts[k];
    return y0 + (y1 - y0) * (v - x0) / (x1 - x0);
  }
  return v;
}
function lut(p) {                     // 256 x RGBA bytes, one tone curve per channel
  const out = new Uint8Array(256 * 4), lift = [].concat(p.lift);
  for (let c = 0; c < 3; c++) for (let i = 0; i < 256; i++) {
    let v = Math.min(1, (i / 255) * p.exp * p.mul[c]);
    if (p.curve) v = interp(p.curve, v * 255) / 255;
    v = Math.pow(v, p.gamma);
    v = Math.min(1, Math.max(0, (v - 0.5) * p.contrast + 0.5));
    const lo = lift[c] ?? lift[0];
    v = lo + v * (p.cap - lo);
    out[i * 4 + c] = Math.round(v * 255);
  }
  for (let i = 0; i < 256; i++) out[i * 4 + 3] = 255;
  return out;
}
const LUTS = Object.fromEntries(Object.entries(P).map(([k, p]) => [k, lut(p)]));

// skin likelihood from YCbCr (Cr 0.52..0.68, Cb 0.29..0.51), soft edges, not in near-black, not where blue > green
// (pink / lilac backdrops); works for dark skin too.
// Bilateral taps: 2 rings x 8 directions at 3 / 6.5 px, range sigma 0.09 in rgb -> edges (eyes, lips, brows) hold.
const RING = [...Array(16)].map((_, k) => {
  const a = (k % 8) * Math.PI / 4 + (k < 8 ? 0 : Math.PI / 8), r = k < 8 ? 3 : 6.5;
  return [Math.cos(a) * r, Math.sin(a) * r, k < 8 ? 1 : 0.6];
});
const RS = 1 / (2 * 0.09 * 0.09);

const VS = `attribute vec2 p; varying vec2 uv; void main(){ uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }`;
const FS = `precision mediump float;
varying vec2 uv;
uniform sampler2D src, blurL, lut;
uniform vec2 size, texL;
uniform float smoothSkin, smoothRest, mono, sat, satBg, glowT, glowA, glowBg, grain, vig, seed, strength;
uniform vec3 shadow, gray;
float luma(vec3 c){ return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float h(vec2 q){ return fract(sin(dot(q, vec2(12.9898, 78.233)) + seed) * 43758.5453); }
float skin(vec3 c){
  float cb = dot(c, vec3(-0.1687, -0.3313, 0.5)) + 0.5, cr = dot(c, vec3(0.5, -0.4187, -0.0813)) + 0.5;
  return smoothstep(0.505, 0.535, cr) * (1.0 - smoothstep(0.66, 0.70, cr)) * smoothstep(0.27, 0.31, cb)
    * (1.0 - smoothstep(0.49, 0.52, cb)) * smoothstep(0.04, 0.10, luma(c)) * smoothstep(-0.04, -0.01, c.g - c.b);
}
vec3 tone(vec3 c){ return vec3(texture2D(lut, vec2(c.r * 0.99609 + 0.00195, 0.5)).r,
  texture2D(lut, vec2(c.g * 0.99609 + 0.00195, 0.5)).g, texture2D(lut, vec2(c.b * 0.99609 + 0.00195, 0.5)).b); }
void main(){
  vec4 o = texture2D(src, uv);
  vec3 c = o.rgb, acc = c; float ws = 1.0;
  ${RING.map(([x, y, w]) => `{ vec3 s = texture2D(src, uv + vec2(${x.toFixed(3)}, ${y.toFixed(3)}) / size).rgb; vec3 d = s - c;
  float k = ${w.toFixed(2)} * exp(-dot(d, d) * ${RS.toFixed(2)}); acc += s * k; ws += k; }`).join("\n  ")}
  float sk = skin(c);
  c = mix(c, acc / ws, mix(smoothRest, smoothSkin, sk));
  if (mono > 0.5) c = vec3(dot(c, gray));
  c = tone(c);
  float l = luma(c);
  c = clamp(mix(vec3(l), c, mix(satBg, sat, sk)) + shadow * (1.0 - l) * (1.0 - l), 0.0, 1.0);
  vec3 g = 0.25 * (texture2D(blurL, uv + texL * vec2(0.5, 0.5)).rgb + texture2D(blurL, uv - texL * vec2(0.5, 0.5)).rgb
    + texture2D(blurL, uv + texL * vec2(0.5, -0.5)).rgb + texture2D(blurL, uv - texL * vec2(0.5, -0.5)).rgb);
  if (mono > 0.5) g = vec3(dot(g, gray));
  g *= smoothstep(glowT - 0.2, glowT + 0.15, luma(g));
  c = 1.0 - (1.0 - c) * (1.0 - g * glowA * mix(glowBg, 1.0, sk));   // backdrop keeps its colour
  vec2 px = floor(uv * size);
  float n = (h(px) + h(px + 17.3) - 1.0) * 1.22;
  float m = 1.0 - 0.6 * pow(abs(2.0 * luma(c) - 1.0), 2.0);
  c += n * grain / 255.0 * m;
  vec2 q = uv - 0.5;
  c *= 1.0 - vig * smoothstep(0.12, 0.5, dot(q, q));
  gl_FragColor = vec4(mix(o.rgb, clamp(c, 0.0, 1.0), strength), 1.0);
}`;

function blurL(src, w, h, scratch) {   // ~16 px blur for the glow, by downscaling
  const mk = (k, cw, ch) => { const c = scratch[k] ||= document.createElement("canvas");
    if (c.width !== cw || c.height !== ch) { c.width = cw; c.height = ch; } return c; };
  const s = mk("s", Math.max(1, Math.round(w / 3)), Math.max(1, Math.round(h / 3)));
  const m = mk("m", Math.max(1, Math.round(w / 6)), Math.max(1, Math.round(h / 6)));
  const l = mk("l", Math.max(1, Math.round(w / 16)), Math.max(1, Math.round(h / 16)));
  for (const [dst, from] of [[s, src], [m, s], [l, m]]) {
    const x = dst.getContext("2d"); x.imageSmoothingQuality = "high";
    x.clearRect(0, 0, dst.width, dst.height); x.drawImage(from, 0, 0, dst.width, dst.height);
  }
  return l;
}

function glFilter() {
  const cv = document.createElement("canvas");
  const gl = cv.getContext("webgl", { preserveDrawingBuffer: false });  // output is opaque: premultiplied = straight, fast 2D copy
  if (!gl) return null;
  const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o);
    if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
  const pr = gl.createProgram();
  gl.attachShader(pr, sh(gl.VERTEX_SHADER, VS)); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, FS));
  gl.linkProgram(pr); gl.useProgram(pr);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  const loc = gl.getAttribLocation(pr, "p"); gl.enableVertexAttribArray(loc);
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  const u = n => gl.getUniformLocation(pr, n);
  const tex = ["src", "blurL", "lut"].map((n, i) => {
    const t = gl.createTexture(); gl.activeTexture(gl.TEXTURE0 + i); gl.bindTexture(gl.TEXTURE_2D, t);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
      [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    gl.uniform1i(u(n), i); return t;
  });
  const up = (i, img) => { gl.activeTexture(gl.TEXTURE0 + i); gl.bindTexture(gl.TEXTURE_2D, tex[i]);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, i < 2);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img); };
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);   // canvases are stored premultiplied: no conversion
  const scratch = {};
  return (src, p, id, strength, seed) => {
    const w = src.width, h = src.height;
    if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
    gl.viewport(0, 0, w, h);
    const bl = blurL(src, w, h, scratch);
    up(0, src); up(1, bl);
    gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, tex[2]); gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 256, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, LUTS[id]);
    gl.uniform2f(u("size"), w, h); gl.uniform2f(u("texL"), 1 / bl.width, 1 / bl.height);
    gl.uniform1f(u("smoothSkin"), p.smooth[0]); gl.uniform1f(u("smoothRest"), p.smooth[1]);
    gl.uniform1f(u("mono"), p.mono ? 1 : 0); gl.uniform1f(u("sat"), p.sat); gl.uniform1f(u("satBg"), p.satBg ?? p.sat);
    gl.uniform1f(u("glowT"), p.glow[0]); gl.uniform1f(u("glowA"), p.glow[1]); gl.uniform1f(u("glowBg"), p.glow[2] ?? 1); gl.uniform1f(u("grain"), p.grain);
    gl.uniform1f(u("vig"), p.vig); gl.uniform1f(u("seed"), seed); gl.uniform1f(u("strength"), strength);
    gl.uniform3f(u("shadow"), p.shadow[0] / 255, p.shadow[1] / 255, p.shadow[2] / 255);
    const gr = p.gray || [0.3, 0.6, 0.1]; gl.uniform3f(u("gray"), gr[0], gr[1], gr[2]);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    return cv;
  };
}

// ponytail: CPU fallback mirrors the shader line by line; slow (~300 ms per cell) but only for no-WebGL browsers.
function cpuFilter() {
  const cv = document.createElement("canvas"), scratch = {}, full = document.createElement("canvas");
  const sm = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const lu = x => 0.2126 * x[0] + 0.7152 * x[1] + 0.0722 * x[2];
  const skin = ([r, g, b]) => {
    const cb = -0.1687 * r - 0.3313 * g + 0.5 * b + 0.5, cr = 0.5 * r - 0.4187 * g - 0.0813 * b + 0.5;
    return sm(0.505, 0.535, cr) * (1 - sm(0.66, 0.70, cr)) * sm(0.27, 0.31, cb) * (1 - sm(0.49, 0.52, cb)) * sm(0.04, 0.10, lu([r, g, b])) * sm(-0.04, -0.01, g - b);
  };
  const taps = RING.map(([x, y, w]) => [Math.round(x), Math.round(y), w]);
  return (src, p, id, strength, seed) => {
    const w = src.width, h = src.height;
    cv.width = full.width = w; cv.height = full.height = h;
    const fx = full.getContext("2d", { willReadFrequently: true });
    const read = c => { fx.clearRect(0, 0, w, h); fx.drawImage(c, 0, 0, w, h); return fx.getImageData(0, 0, w, h).data; };
    const bl = read(blurL(src, w, h, scratch)), o = read(src), L = LUTS[id], gr = p.gray || [0.3, 0.6, 0.1];
    const out = new ImageData(w, h), d = out.data;
    let r = seed * 9301 + 49297;
    const rnd = () => (r = (r * 9301 + 49297) % 233280) / 233280;
    for (let i = 0; i < d.length; i += 4) {
      const X = (i / 4) % w, Y = Math.floor(i / 4 / w);
      let c = [o[i] / 255, o[i + 1] / 255, o[i + 2] / 255];
      const acc = [...c]; let ws = 1;
      for (const [dx, dy, tw] of taps) {
        const j = (Math.min(h - 1, Math.max(0, Y + dy)) * w + Math.min(w - 1, Math.max(0, X + dx))) * 4;
        const s = [o[j] / 255, o[j + 1] / 255, o[j + 2] / 255];
        const dd = (s[0] - c[0]) ** 2 + (s[1] - c[1]) ** 2 + (s[2] - c[2]) ** 2, k = tw * Math.exp(-dd * RS);
        acc[0] += s[0] * k; acc[1] += s[1] * k; acc[2] += s[2] * k; ws += k;
      }
      const sk = skin(c), km = p.smooth[1] + (p.smooth[0] - p.smooth[1]) * sk;
      c = c.map((v, j) => v + (acc[j] / ws - v) * km);
      if (p.mono) { const m = gr[0] * c[0] + gr[1] * c[1] + gr[2] * c[2]; c = [m, m, m]; }
      c = c.map((v, j) => L[Math.round(v * 255) * 4 + j] / 255);
      const l = lu(c);
      c = c.map((v, j) => Math.min(1, Math.max(0, l + (v - l) * ((p.satBg ?? p.sat) + (p.sat - (p.satBg ?? p.sat)) * sk) + p.shadow[j] / 255 * (1 - l) * (1 - l))));
      let g = [bl[i] / 255, bl[i + 1] / 255, bl[i + 2] / 255];
      if (p.mono) { const m = gr[0] * g[0] + gr[1] * g[1] + gr[2] * g[2]; g = [m, m, m]; }
      const gk = sm(p.glow[0] - 0.2, p.glow[0] + 0.15, lu(g));
      c = c.map((v, j) => 1 - (1 - v) * (1 - g[j] * gk * p.glow[1] * ((p.glow[2] ?? 1) + (1 - (p.glow[2] ?? 1)) * sk)));
      const n = (rnd() + rnd() - 1) * 1.22 * p.grain / 255 * (1 - 0.6 * Math.pow(Math.abs(2 * lu(c) - 1), 2));
      const px = X / w - 0.5, py = Y / h - 0.5;
      const v = 1 - p.vig * sm(0.12, 0.5, px * px + py * py);
      for (let j = 0; j < 3; j++) d[i + j] = 255 * (o[i + j] / 255 + (Math.min(1, Math.max(0, (c[j] + n) * v)) - o[i + j] / 255) * strength);
      d[i + 3] = 255;
    }
    cv.getContext("2d").putImageData(out, 0, 0);
    return cv;
  };
}

// apply() returns a canvas that is reused by the next call: draw it before applying again.
// createFilter({ cpu: true }) forces the ImageData path (dev check that both paths match).
export function createFilter({ cpu = false } = {}) {
  let run = null;
  if (!cpu) try { run = glFilter(); } catch (e) { run = null; }
  run ||= cpuFilter();
  return {
    apply(srcCanvas, id, strength = 1, seed = 0) {
      const p = P[id];
      if (!p || strength <= 0) return srcCanvas;
      return run(srcCanvas, p, id, Math.min(1, strength), seed);
    },
  };
}
