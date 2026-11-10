// AI transform of a caught real one (worker/ = the server): consent once, style "fluff" | "figure", device id for the day limit.
// The model draws on a flat green background; chromaKey() turns that into transparency so the picture stands in AR.

// ?ai= a stub server for headless checks, loopback only: a shared link must never send photos elsewhere
export const WORKER = (() => {
  try { const u = new URL(new URLSearchParams(location.search).get("ai")); if (u.protocol === "http:" && ["127.0.0.1", "localhost"].includes(u.hostname)) return u.origin; } catch { /* none or bad */ }
  return "https://dot-lab-catch.dotconnects.workers.dev";
})();
const K = { consent: "catch.ai", style: "catch.style", device: "catch.device" };
const get = k => { try { return localStorage.getItem(k); } catch { return null; } };
const set = (k, v) => { try { localStorage.setItem(k, v); } catch { /* private mode: asks again next time */ } };

export const consent = () => get(K.consent);               // "yes" | "no" | null (not asked)
export const setConsent = v => set(K.consent, v);
export const style = () => (get(K.style) === "figure" ? "figure" : "fluff");
export const setStyle = v => set(K.style, v === "figure" ? "figure" : "fluff");
export function device() {
  let d = get(K.device);
  if (!/^dev-[a-z0-9]{8,32}$/.test(d || "")) {
    d = "dev-" + [...crypto.getRandomValues(new Uint8Array(8))].map(x => x.toString(16).padStart(2, "0")).join("");
    set(K.device, d);
  }
  return d;
}

/** Green-screen RGBA -> transparent background, soft edge, green spill pulled out of fur tips. In place; returns px. */
export function chromaKey(px) {
  for (let o = 0; o < px.length; o += 4) {
    const r = px[o], g = px[o + 1], b = px[o + 2], m = Math.max(r, b);
    const k = g - m;                                   // how much greener than it is red/blue
    if (k > 70) { px[o + 3] = 0; continue; }          // background
    if (k > 20) { px[o + 3] = Math.round(255 * (70 - k) / 50); px[o + 1] = m; continue; }   // edge: fade + despill
    if (k > 0) px[o + 1] = m;                          // slight spill
  }
  return px;
}

async function blobToPngB64(blob, max = 512) {
  const url = URL.createObjectURL(blob), img = new Image();
  try { img.src = url; await img.decode(); } finally { URL.revokeObjectURL(url); }
  const s = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.round(img.naturalWidth * s)); c.height = Math.max(1, Math.round(img.naturalHeight * s));
  // white under the cut-out: a transparent PNG confuses image models less on a plain background
  const g = c.getContext("2d"); g.fillStyle = "#fff"; g.fillRect(0, 0, c.width, c.height); g.drawImage(img, 0, 0, c.width, c.height);
  return c.toDataURL("image/png").split(",")[1];
}

/** The model's picture keyed and trimmed to its content: {blob (PNG), w, h}. */
async function keyed(b64, mime) {
  const img = new Image(); img.src = `data:${mime};base64,${b64}`; await img.decode();
  const W = Math.min(512, img.naturalWidth), H = Math.round(img.naturalHeight * W / img.naturalWidth);
  const c = document.createElement("canvas"); c.width = W; c.height = H;
  const g = c.getContext("2d", { willReadFrequently: true }); g.drawImage(img, 0, 0, W, H);
  const d = g.getImageData(0, 0, W, H); chromaKey(d.data); g.putImageData(d, 0, 0);
  let x0 = W, y0 = H, x1 = -1, y1 = -1;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (d.data[(y * W + x) * 4 + 3] > 24) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  if (x1 < 0) throw Object.assign(new Error("empty picture"), { code: "model" });
  const t = document.createElement("canvas"); t.width = x1 - x0 + 1; t.height = y1 - y0 + 1;
  t.getContext("2d").drawImage(c, -x0, -y0);
  return { blob: await new Promise(ok => t.toBlob(ok, "image/png")), w: t.width, h: t.height };
}

/** cut-out Blob -> {blob, w, h, left}. Throws an Error with .code: "off" | "device" | "all" | "model" | "net". */
export async function transform(cut, kind, st = style()) {
  let r;
  try {
    r = await fetch(WORKER + "/transform", { method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ style: st, kind, device: device(), image: await blobToPngB64(cut) }) });
  } catch { throw Object.assign(new Error("network"), { code: "net" }); }
  const body = await r.json().catch(() => ({}));
  if (!r.ok) throw Object.assign(new Error(body.error || String(r.status)), { code: body.error === "device" || body.error === "all" || body.error === "off" ? body.error : "model" });
  return { ...(await keyed(body.image, body.mime || "image/png")), left: body.left };
}
