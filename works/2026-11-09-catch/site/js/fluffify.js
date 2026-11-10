// A caught real one (cut-out PNG) -> a fluff creature spec: same fur world as the virtual ones, coloured from the photo.
// dog -> 멍솜 shape, cat -> 냥솜 shape, person -> a round head with a hair cap on a shirt body.
// Pure functions on RGBA pixels so node tests can run them; the browser wrapper reads a Blob into pixels.

const hex = ([r, g, b]) => "#" + [r, g, b].map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0")).join("");
const lum = ([r, g, b]) => 0.2126 * r + 0.7152 * g + 0.0722 * b;
const CREAM = [251, 246, 236];

/** k-means (k small) over opaque pixels in rows [y0, y1) of a w x h RGBA buffer. Returns clusters by size, largest first. */
export function clusters(px, w, h, { k = 3, y0 = 0, y1 = h, iters = 8 } = {}) {
  const pts = [];
  const step = Math.max(1, Math.floor(Math.sqrt((w * (y1 - y0)) / 1500)));   // ~1500 samples, enough for 3 colours
  for (let y = y0; y < y1; y += step) for (let x = 0; x < w; x += step) {
    const i = (y * w + x) * 4;
    if (px[i + 3] > 200) pts.push([px[i], px[i + 1], px[i + 2]]);
  }
  if (!pts.length) return [];
  // seeds: darkest, brightest, middle by luminance (deterministic, no random)
  const byL = [...pts].sort((a, b) => lum(a) - lum(b));
  let cs = Array.from({ length: k }, (_, j) => [...byL[Math.floor((j + 0.5) / k * (byL.length - 1))]]);
  let n = [];
  for (let it = 0; it < iters; it++) {
    const sum = cs.map(() => [0, 0, 0]); n = cs.map(() => 0);
    for (const p of pts) {
      let best = 0, bd = Infinity;
      cs.forEach((c, j) => { const d = (p[0] - c[0]) ** 2 + (p[1] - c[1]) ** 2 + (p[2] - c[2]) ** 2; if (d < bd) { bd = d; best = j; } });
      sum[best][0] += p[0]; sum[best][1] += p[1]; sum[best][2] += p[2]; n[best]++;
    }
    cs = cs.map((c, j) => n[j] ? sum[j].map(v => v / n[j]) : c);
  }
  return cs.map((c, j) => ({ c, share: n[j] / pts.length })).filter(x => x.share > 0).sort((a, b) => b.share - a.share);
}

/** Pale it down to the game's look but keep the hue: lightness lifted into 0.66..0.9, saturation capped, so a navy shirt
 *  becomes powder blue (not grey) and dot eyes always read. lo/hi move the lightness band. */
export function soften(c, lo = 0.66, hi = 0.9) {
  const [r, g, b] = c.map(v => v / 255), mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2, d = mx - mn;
  let h = 0, s = d === 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
  if (d) h = mx === r ? ((g - b) / d + 6) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  const L = lo + (hi - lo) * l, S = Math.min(0.42, s * 0.8) * Math.min(1, d * 3);   // near-black/near-white hues are noise: keep them grey
  const C = (1 - Math.abs(2 * L - 1)) * S, X = C * (1 - Math.abs((h % 2) - 1)), m = L - C / 2;
  const [r1, g1, b1] = h < 1 ? [C, X, 0] : h < 2 ? [X, C, 0] : h < 3 ? [0, C, X] : h < 4 ? [0, X, C] : h < 5 ? [X, 0, C] : [C, 0, X];
  return [r1 + m, g1 + m, b1 + m].map(v => v * 255);
}
const isSkin = ([r, g, b]) => r > g && g > b && r - b > 30 && r - b < 140 && lum([r, g, b]) > 90;   // rough: warm, mid-light

/** Opaque bounding rows, so "top" means the top of the subject, not of the PNG. */
function rows(px, w, h) {
  let a = h, b = 0;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (px[(y * w + x) * 4 + 3] > 200) { if (y < a) a = y; if (y > b) b = y; break; }
  return a <= b ? [a, b + 1] : [0, h];
}

/** {main, second, top, bottom, bald} colours (hex, already softened) from a cut-out. */
export function palette(px, w, h) {
  const [y0, y1] = rows(px, w, h), cut = y0 + Math.round((y1 - y0) * 0.38);
  const all = clusters(px, w, h, { y0, y1 }), top = clusters(px, w, h, { y0, y1: cut }), bot = clusters(px, w, h, { y0: cut, y1 });
  const pick = (l, i = 0) => (l[i] || l[0] || { c: CREAM }).c;
  // a second colour only counts when it is a real marking (>= 12 % and clearly different), else it repeats the main one
  const m = pick(all), s2 = all[1] && all[1].share >= 0.12 && Math.abs(lum(all[1].c) - lum(m)) > 40 ? all[1].c : m;
  return { main: hex(soften(m)), second: hex(soften(s2, 0.74, 0.95)), top: hex(soften(pick(top), 0.45, 0.8)), bottom: hex(soften(pick(bot))),
    bald: isSkin(pick(top)) };
}

const darker = (h, k = 0.82) => hex([1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) * k));

/** The fur spec for a caught real one. id/name come from the dex entry. */
export function specFor(kind, pal, { id, name }) {
  const base = { id, name, style: "fuzz", group: "real" };
  if (kind === "dog") return { ...base, color: pal.main, height: 0.3,
    body: { r: [0.44, 0.42, 0.42], flat: 0.25, len: 0.07 },
    face: { x: 0.3, y: 0.1, r: 0.045, blush: "#f0aa98", mouth: false },
    parts: [
      { kind: "blob", r: [0.1, 0.2, 0.06], off: [0, -0.15, 0], on: "head", on_at: [1.15, 0.7], mirror: true, rot: [0, 0, 0.35], fur: true, len: 0.05, color: pal.second, wob: 1.1 },
      { kind: "blob", r: [0.12, 0.08, 0.06], on: "head", on_at: [0, -0.12], out: 0.03, color: "#fbf5ea" },
      { kind: "blob", r: [0.035, 0.025, 0.02], on: "head", on_at: [0, -0.05], out: 0.1, color: "#3d3532" },
      { kind: "blob", r: [0.08, 0.045, 0.1], c: [0.15, -0.38, 0.16], mirror: true, color: darker(pal.main) },
    ] };
  if (kind === "cat") return { ...base, color: pal.main, height: 0.3,
    body: { r: [0.44, 0.4, 0.42], flat: 0.25, len: 0.07 },
    face: { x: 0.3, y: 0.04, r: 0.045, blush: "#f1aeb0" },
    parts: [
      { kind: "cone", r: 0.12, h: 0.25, fur: true, on: "head", on_at: [0.55, 0.9], mirror: true, rot: [0, 0, -0.35], out: -0.02, color: pal.second, wob: 0.4 },
      { kind: "blob", r: [0.05, 0.24, 0.05], on: "head", on_at: [Math.PI - 0.5, -0.35], rot: [-0.7, 0, 0.5], out: 0.1, fur: true, len: 0.05, color: pal.second, wob: 1 },
      { kind: "blob", r: [0.2, 0.15, 0.08], on: "head", on_at: [0, -0.45], out: -0.03, fur: true, len: 0.05, color: pal.second },   // chest marking
      { kind: "blob", r: [0.07, 0.045, 0.09], c: [0.15, -0.38, 0.16], mirror: true, color: pal.second },
    ] };
  // person: head = skin-ish (lightest of the top), a fur hair cap in the top colour, a shirt body in the bottom colour
  return { ...base, color: "#f8e6d6", height: 0.36,
    bodies: [
      { r: [0.34, 0.32, 0.32], c: [0, 0.3, 0.06], len: 0.025 },
      { r: [0.34, 0.3, 0.32], c: [0, -0.16, 0], color: pal.bottom, len: 0.06, flat: 0.3 },
    ],
    face: { x: 0.32, y: -0.05, r: 0.04, blush: "#efa9a0" },
    parts: [
      ...(pal.bald ? [] : [{ kind: "blob", r: [0.37, 0.2, 0.36], on: "head", on_at: [0, 1.25], out: -0.12, fur: true, len: 0.06, color: pal.top }]),
      { kind: "blob", r: [0.06, 0.12, 0.06], c: [0.36, -0.12, 0.02], mirror: true, rot: [0, 0, 0.5], fur: true, len: 0.04, color: pal.bottom, wob: 0.8 },
      { kind: "blob", r: [0.08, 0.05, 0.1], c: [0.14, -0.44, 0.1], mirror: true, color: darker(pal.bottom, 0.7) },
    ] };
}

/** Browser: a cut-out Blob -> palette. */
export async function paletteOf(blob) {   // plain <img> + <canvas>: older in-app browsers lack OffscreenCanvas
  const url = URL.createObjectURL(blob), img = new Image();
  try { img.src = url; await img.decode(); } finally { URL.revokeObjectURL(url); }
  const s = Math.min(1, 160 / Math.max(img.naturalWidth, img.naturalHeight));
  const w = Math.max(1, Math.round(img.naturalWidth * s)), h = Math.max(1, Math.round(img.naturalHeight * s));
  const c = document.createElement("canvas"); c.width = w; c.height = h;
  const g = c.getContext("2d", { willReadFrequently: true });
  g.drawImage(img, 0, 0, w, h);
  return palette(g.getImageData(0, 0, w, h).data, w, h);
}
