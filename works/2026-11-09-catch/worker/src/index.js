// 「우리 집 몬스터 잡기」 transform: a caught dog / cat / person cut-out -> an image model -> a cute picture of THAT one.
// style "fluff" = our pale fur-ball creatures (a style reference image goes along), "figure" = a small 3D figurine of itself.
// The photo is passed through and never stored. Cost guard: DEVICE_DAY images per device a day, ALL_DAY for the whole site
// (a device id is client-made, so ALL_DAY is the real ceiling; IP_DAY keeps one network from eating it all).
// The Origin check only keeps other web pages out; it is not a cost guard (any client can send the header).
// Secret GEMINI_KEY: `npx wrangler@4 secret put GEMINI_KEY`. Set ALL_DAY = "0" to switch the AI off at once.

const ORIGINS = [/^https:\/\/dot-connects\.github\.io$/, /^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/];
const STYLES = ["fluff", "figure"], KINDS = ["dog", "cat", "person"];
const IMAGE_MAX = 2_000_000;   // base64 chars, ~1.5 MB: the game sends a <= 512 px PNG
const MODEL = "gemini-3.1-flash-image";
const STYLE_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-09-catch/site/img/style-ref.jpg";
const WHO = { dog: "dog", cat: "cat", person: "person" };

export const PROMPTS = {
  fluff: who => `Image 1 is a real photo of a ${who}. Image 2 is the art style of our game creatures: small round pale 3D plush fur balls, soft felt/fur texture, tiny black dot eyes, small pink blush, short stubby feet, no outline, soft studio light.

Draw THIS ${who} as one of our creatures: keep what makes it recognisable (its fur or hair colours and markings, ear shape, nose and face features, clothing colour for a person, never any text) but turn the whole body into a chubby round fur-ball plush in exactly the style of image 2. Front view, full body, centred, square image, no text. Background: one flat solid pure green (#00FF00) colour edge to edge, no floor, no shadow, no gradient.`,
  figure: who => `Image 1 is a real photo of a ${who}. Make a cute small 3D figurine of THIS ${who} itself (not a ball): its real body shape and pose simplified, slightly chibi proportions (bigger head), soft matte clay/plush 3D render, keep its real colours, markings, ear shape, face features and clothing colour (no text on clothes). Tiny black dot eyes, small pink blush, gentle smile. Full body, front three-quarter view, centred, standing, square image, no text. Background: one flat solid pure green (#00FF00) colour edge to edge, no floor, no shadow, no gradient.`,
};

const allowed = o => !!o && ORIGINS.some(r => r.test(o));
function cors(origin) {
  return { "access-control-allow-origin": origin, "access-control-allow-methods": "POST, OPTIONS", "access-control-allow-headers": "content-type", "access-control-max-age": "86400", vary: "origin" };
}
const json = (body, status, origin) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8", ...(origin ? cors(origin) : {}) } });

/** The first image in a model response: the interactions API (steps[].content[]) or the older generateContent shape. */
export function pickImage(r) {
  for (const s of r?.steps || []) for (const c of s?.content || []) if (c?.type === "image" && c.data) return { mime: c.mime_type || "image/png", data: c.data };
  if (r?.output_image?.data) return { mime: r.output_image.mime_type || "image/png", data: r.output_image.data };
  for (const p of r?.candidates?.[0]?.content?.parts || []) { const d = p.inlineData || p.inline_data; if (d?.data) return { mime: d.mimeType || d.mime_type || "image/png", data: d.data }; }
  return null;
}

let styleRef = null;   // fetched once per isolate
async function styleImage() {
  if (styleRef) return styleRef;
  const r = await fetch(STYLE_URL);
  if (!r.ok) throw new Error("style ref " + r.status);
  const b = new Uint8Array(await r.arrayBuffer());
  let s = ""; for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode(...b.subarray(i, i + 0x8000));
  return (styleRef = btoa(s));
}

async function callGemini({ style, kind, image }, env) {
  const input = [{ type: "text", text: PROMPTS[style](WHO[kind]) }, { type: "image", mime_type: "image/png", data: image }];
  if (style === "fluff") input.push({ type: "image", mime_type: "image/jpeg", data: await styleImage() });
  const r = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
    method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_KEY },
    // store: false = Google keeps no copy of the interaction (default is stored 55 days on paid tier)
    body: JSON.stringify({ model: env.MODEL || MODEL, store: false, input, response_format: { type: "image", aspect_ratio: "1:1", image_size: "1K", mime_type: "image/jpeg" } }),
  });
  const body = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(`model HTTP ${r.status} ${body?.error?.status || ""}`);   // a refused call is not billed
  const img = pickImage(body);
  // answered without a picture (refusal, safety): Google billed it, so the slot stays used
  if (!img) throw Object.assign(new Error("model gave no image"), { billed: true });
  return img;
}

// ---------- day counters ----------
// take(k, limit) adds one only while below limit and returns the new count (0 = refused); give(k) hands one back.
export class MemoryCounts {
  constructor() { this.m = new Map(); }
  async take(k, limit) { const n = this.m.get(k) || 0; if (n >= limit) return 0; this.m.set(k, n + 1); return n + 1; }
  async give(k) { const n = this.m.get(k) || 0; if (n > 0) this.m.set(k, n - 1); }
}
// ponytail: one row per key per day, never pruned; a few hundred rows a day, prune if it ever matters
export class D1Counts {
  constructor(db) { this.db = db; }
  async take(k, limit) {
    const r = await this.db.prepare("INSERT INTO counts (k, n) VALUES (?1, 1) ON CONFLICT(k) DO UPDATE SET n = n + 1 WHERE n < ?2 RETURNING n").bind(k, limit).first();
    return r ? r.n : 0;
  }
  async give(k) { await this.db.prepare("UPDATE counts SET n = n - 1 WHERE k = ?1 AND n > 0").bind(k).run(); }
}

export async function handle(req, env, counts, { day = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10) } = {}) {   // KST day
  const origin = req.headers.get("origin");
  if (!allowed(origin)) return json({ error: "origin" }, 403);
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors(origin) });
  if (req.method !== "POST" || new URL(req.url).pathname !== "/transform") return json({ error: "not found" }, 404, origin);
  if (+(req.headers.get("content-length") || 0) > IMAGE_MAX + 2000) return json({ error: "too big" }, 413, origin);
  const b = await req.json().catch(() => null);
  if (!b || !STYLES.includes(b.style) || !KINDS.includes(b.kind) || !/^dev-[a-z0-9]{8,32}$/.test(b.device || "")
      || typeof b.image !== "string" || !b.image || b.image.length > IMAGE_MAX || !/^[A-Za-z0-9+/=]+$/.test(b.image)) return json({ error: "bad request" }, 400, origin);
  if (!env.GEMINI_KEY) return json({ error: "off" }, 503, origin);
  if (env.LIMIT) { const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" }); if (!success) return json({ error: "slow down" }, 429, origin); }

  const num = (v, d) => (v === undefined || v === "" ? d : +v);   // "0" is a real limit (the off switch)
  const perDev = num(env.DEVICE_DAY, 5), all = num(env.ALL_DAY, 100), perIp = num(env.IP_DAY, 15);
  const kAll = `${day}|all`, kDev = `${day}|${b.device}`, kIp = `${day}|ip:${req.headers.get("cf-connecting-ip") || "?"}`;
  if (!(await counts.take(kAll, all))) return json({ error: "all" }, 429, origin);
  let used = 0, ipOk = 0;
  try {
    ipOk = await counts.take(kIp, perIp);
    if (ipOk) used = await counts.take(kDev, perDev);
  } catch (e) { console.log("counter failed", e?.message); }
  if (!ipOk || !used) {
    await counts.give(kAll); if (ipOk) await counts.give(kIp);
    return json({ error: "device" }, 429, origin);
  }
  try {
    const img = await (env.callModel || callGemini)(b, env);
    return json({ mime: img.mime, image: img.data, left: perDev - used }, 200, origin);
  } catch (e) {
    console.log("transform failed", e?.message || e);   // status only: never the model's words about the photo
    if (!e?.billed) { await counts.give(kDev); await counts.give(kIp); await counts.give(kAll); }
    return json({ error: "model" }, 502, origin);
  }
}

export default {
  async fetch(req, env) { return handle(req, env, new D1Counts(env.DB)); },
};
