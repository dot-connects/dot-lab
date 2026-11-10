// A caught real one (cut-out PNG) -> a soft illustrated sticker that still looks like THAT dog / cat / person:
// shape, markings and face stay; texture becomes flat painted patches, colours go pale, the edge turns into fur tufts.
// On-device, ~100 ms at 220 px. Pure pixel functions + a small canvas wrapper.
import { clusters, soften } from "./fluffify.js";

const INK = [59, 51, 49];
const lum = ([r, g, b]) => 0.2126 * r + 0.7152 * g + 0.0722 * b;

/** Kuwahara filter (radius r) on opaque pixels: flattens fur/skin texture into painted patches, keeps edges (eyes, nose). */
export function kuwahara(px, w, h, r = 3) {
  const out = new Uint8ClampedArray(px);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const o = (y * w + x) * 4;
    if (px[o + 3] < 30) continue;
    let best = Infinity, bc = null;
    for (const [dx, dy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      let n = 0, s0 = 0, s1 = 0, s2 = 0, q = 0;
      for (let j = 0; j <= r; j++) for (let i = 0; i <= r; i++) {
        const xx = x + dx * i, yy = y + dy * j;
        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        const k = (yy * w + xx) * 4;
        if (px[k + 3] < 30) continue;
        const a = px[k], b = px[k + 1], c = px[k + 2];
        s0 += a; s1 += b; s2 += c; q += a * a + b * b + c * c; n++;
      }
      if (!n) continue;
      const v = q / n - (s0 * s0 + s1 * s1 + s2 * s2) / (n * n);
      if (v < best) { best = v; bc = [s0 / n, s1 / n, s2 / n]; }
    }
    if (bc) { out[o] = bc[0]; out[o + 1] = bc[1]; out[o + 2] = bc[2]; }
  }
  return out;
}

/** Map every opaque pixel to its nearest of k paletted colours. Big patches go pale; small dark ones (eyes, nose) become ink. */
export function posterize(px, w, h, k = 6) {
  const cs = clusters(px, w, h, { k, iters: 10 });
  const pal = cs.map(({ c, share }) => lum(c) < 70 && share < 0.12 ? INK : soften(c, 0.55, 0.93));
  const out = new Uint8ClampedArray(px);
  for (let o = 0; o < px.length; o += 4) {
    if (px[o + 3] < 30) { out[o + 3] = 0; continue; }
    let best = 0, bd = Infinity;
    cs.forEach(({ c }, j) => { const d = (px[o] - c[0]) ** 2 + (px[o + 1] - c[1]) ** 2 + (px[o + 2] - c[2]) ** 2; if (d < bd) { bd = d; best = j; } });
    out[o] = pal[best][0]; out[o + 1] = pal[best][1]; out[o + 2] = pal[best][2]; out[o + 3] = 255;
  }
  return out;
}

/** Browser: cut-out Blob -> PNG data URL of the sticker (size px on the long side, plus a margin for the tufts). */
export async function toonOf(blob, size = 220) {
  const url = URL.createObjectURL(blob), img = new Image();
  try { img.src = url; await img.decode(); } finally { URL.revokeObjectURL(url); }
  const s = size / Math.max(img.naturalWidth, img.naturalHeight);
  const w = Math.round(img.naturalWidth * s), h = Math.round(img.naturalHeight * s), M = 14;
  const a = document.createElement("canvas"); a.width = w; a.height = h;
  const g = a.getContext("2d", { willReadFrequently: true });
  g.drawImage(img, 0, 0, w, h);
  const src = g.getImageData(0, 0, w, h);
  const flat = posterize(kuwahara(kuwahara(src.data, w, h, 3), w, h, 2), w, h, 6);
  g.putImageData(new ImageData(flat, w, h), 0, 0);

  const c = document.createElement("canvas"); c.width = w + 2 * M; c.height = h + 2 * M;
  const G = c.getContext("2d");
  // fur tufts: short strokes out of every edge pixel, in that pixel's colour, angled along the outward normal
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const A = (x, y) => (x < 0 || y < 0 || x >= w || y >= h) ? 0 : flat[(y * w + x) * 4 + 3];
  G.lineCap = "round";
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (!A(x, y) || (A(x - 1, y) && A(x + 1, y) && A(x, y - 1) && A(x, y + 1))) continue;
    if (rnd() < 0.35) continue;
    const nx = A(x - 1, y) - A(x + 1, y), ny = A(x, y - 1) - A(x, y + 1), nl = Math.hypot(nx, ny) || 1;
    const ang = Math.atan2(ny / nl, nx / nl) + (rnd() - 0.5) * 0.9, len = 2.5 + rnd() * 4.5, o = (y * w + x) * 4;
    G.strokeStyle = `rgb(${flat[o]},${flat[o + 1]},${flat[o + 2]})`; G.lineWidth = 1.2 + rnd();
    G.beginPath(); G.moveTo(x + M, y + M); G.lineTo(x + M + Math.cos(ang) * len, y + M + Math.sin(ang) * len); G.stroke();
  }
  // a thin warm line just outside the body, then the body on top
  G.save(); G.filter = "drop-shadow(0 0 0.8px rgba(59,51,49,.85))"; G.drawImage(a, M, M); G.restore();
  G.drawImage(a, M, M);
  return c.toDataURL("image/png");
}
