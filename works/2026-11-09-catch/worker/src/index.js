// 「우리 집 몬스터 잡기」 transform: a caught dog / cat / person cut-out -> an image model -> a cute picture of THAT one.
// /transform (web game + app preview): style "fluff" = our pale fur-ball creatures (a style reference image goes along),
// "figure" = a small 3D figurine of itself. /sheet (app, bought): a 3 x 2 pose sheet of the fur-ball for the pet.
// The photo is passed through and never stored. Cost guards: DEVICE_DAY previews per device a day, ALL_DAY previews for
// everyone (a device id is client-made, so ALL_DAY is the real ceiling; IP_DAY keeps one network from eating it all),
// and MONTH_CENTS = a money ceiling for the month over both routes (cents reserved before each OpenAI call).
// The Origin / x-app check only keeps other web pages out; it is not a cost guard (any client can send the header).
// Secrets: OPENAI_KEY (none = 503 "off"), DEBUG_PURCHASE (the /sheet token until real billing; empty = /sheet refused).
// Set ALL_DAY = "0" to stop previews, MONTH_CENTS = "0" to stop every AI call at once.

// A secret piped in from Windows PowerShell can carry a UTF-8 BOM and a newline (2026-11-12: OpenAI answered 400).
export const secret = v => String(v ?? "").replace(/^\uFEFF/, "").trim();
const ORIGINS = [/^https:\/\/dot-connects\.github\.io$/, /^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/];
const STYLES = ["fluff", "figure"], KINDS = ["dog", "cat", "person"];
const IMAGE_MAX = 2_000_000;   // base64 chars, ~1.5 MB: the game sends a <= 512 px PNG
const MODEL = "gpt-image-2.6-flare";
const OPENAI_URL = "https://api.openai.com/v1/images/edits";
const STYLE_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-09-catch/site/img/style-ref.jpg";
const CENTS = { preview: 5, sheet: 8 };   // reserved per call: a conservative price incl. input image tokens
const SHEET_PER_ENTRY = 2, SHEET_PER_DAY = 10;

export const PROMPTS = {
  fluff: who => `Image 1 is a real photo of a ${who}. Image 2 is the art style of our game creatures: small round pale 3D plush fur balls, soft felt/fur texture, tiny black dot eyes, small pink blush, short stubby feet, no outline, soft studio light.

Draw THIS ${who} as one of our creatures: keep what makes it recognisable (its fur or hair colours and markings, ear shape, nose and face features, clothing colour for a person, never any text) but turn the whole body into a chubby round fur-ball plush in exactly the style of image 2. Front view, full body, centred, square image, no text. Background: one flat solid pure green (#00FF00) colour edge to edge, no floor, no shadow, no gradient.`,
  figure: who => `Image 1 is a real photo of a ${who}. Make a cute small 3D figurine of THIS ${who} itself (not a ball): its real body shape and pose simplified, slightly chibi proportions (bigger head), soft matte clay/plush 3D render, keep its real colours, markings, ear shape, face features and clothing colour (no text on clothes). Tiny black dot eyes, small pink blush, gentle smile. Full body, front three-quarter view, centred, standing, square image, no text. Background: one flat solid pure green (#00FF00) colour edge to edge, no floor, no shadow, no gradient.`,
};

export const SHEET_PROMPT = who => `Image 1 is a real photo of a ${who} (it may be seen from the side or lying down). Image 2 shows the art style of our game creatures: small round pale 3D plush fur balls, soft fur texture, tiny black dot eyes, small pink blush, short stubby feet, no outline, soft studio light.

Make a character pose sheet of THIS ${who} as ONE of our chubby round fur-ball plush creatures, in exactly the style of image 2. Keep what makes it recognisable: its exact fur colours and where its markings are, ear shape, nose and face. The SAME creature appears 6 times in a 3 x 2 grid of equal square cells, every cell the same size and scale, the creature centred in its cell with space around it:
top row: 1) standing, front view, calm; 2) front view, big happy smile with eyes closed in joy; 3) side view walking to the right, left foot forward.
bottom row: 4) side view walking to the right, right foot forward; 5) lying down asleep, eyes closed; 6) curled up asleep in a round ball.
No text, no numbers, no grid lines. Background of the whole image: one flat solid pure green (#00FF00), no floor, no shadows.`;

const allowed = o => !!o && ORIGINS.some(r => r.test(o));
function cors(origin) {
  return { "access-control-allow-origin": origin, "access-control-allow-methods": "POST, OPTIONS", "access-control-allow-headers": "content-type", "access-control-max-age": "86400", vary: "origin" };
}
const json = (body, status, origin) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8", ...(origin ? cors(origin) : {}) } });
const b64bytes = s => Uint8Array.from(atob(s), c => c.charCodeAt(0));

let styleRef = null;   // fetched once per isolate
async function styleImage() {
  if (styleRef) return styleRef;
  const r = await fetch(STYLE_URL);
  if (!r.ok) throw new Error("style ref " + r.status);
  return (styleRef = new Uint8Array(await r.arrayBuffer()));
}

/**
 * One OpenAI image edit: {prompt, image (base64 PNG), ref (send the style picture too), quality, size} -> {mime, data}.
 * Errors carry .sent once the request left for OpenAI (the money reservation then stays) and .billed when OpenAI
 * answered 200 without a picture. One retry on 5xx, none on 4xx.
 */
export async function callOpenAI({ prompt, image, ref, quality, size }, env) {
  const form = new FormData();
  for (const [k, v] of Object.entries({ model: env.MODEL || MODEL, prompt, quality, size, output_format: "webp", output_compression: "80" })) form.append(k, v);
  form.append("image[]", new Blob([b64bytes(image)], { type: "image/png" }), "photo.png");
  if (ref) form.append("image[]", new Blob([await styleImage()], { type: "image/jpeg" }), "style.jpg");
  const sent = e => Object.assign(e, { sent: true });
  for (let attempt = 0; ; attempt++) {
    let r;
    try { r = await fetch(OPENAI_URL, { method: "POST", headers: { authorization: `Bearer ${secret(env.OPENAI_KEY)}` }, body: form }); }
    catch (e) { throw sent(new Error("model network " + (e?.message || e))); }
    if (r.status >= 500 && attempt === 0) continue;
    const body = await r.json().catch(() => ({}));
    if (!r.ok) throw sent(new Error(`model HTTP ${r.status} ${body?.error?.code || ""}`));
    const data = body?.data?.[0]?.b64_json;
    if (!data) throw Object.assign(sent(new Error("model gave no image")), { billed: true });
    return { mime: `image/${body.output_format || "webp"}`, data };
  }
}

// ---------- counters ----------
// take(k, limit) adds one only while below limit and returns the new count (0 = refused); give(k) hands one back.
// add(k, n, limit) adds n only if the total stays <= limit and returns the new total (0 = refused); add(k, -n) refunds.
export class MemoryCounts {
  constructor() { this.m = new Map(); }
  async take(k, limit) { const n = this.m.get(k) || 0; if (n >= limit) return 0; this.m.set(k, n + 1); return n + 1; }
  async give(k) { const n = this.m.get(k) || 0; if (n > 0) this.m.set(k, n - 1); }
  async add(k, n, limit) { const t = (this.m.get(k) || 0) + n; if (t > limit) return 0; this.m.set(k, t); return t; }
}
// ponytail: one row per key per day, never pruned; a few hundred rows a day, prune if it ever matters
export class D1Counts {
  constructor(db) { this.db = db; }
  async take(k, limit) {
    const r = await this.db.prepare("INSERT INTO counts (k, n) VALUES (?1, 1) ON CONFLICT(k) DO UPDATE SET n = n + 1 WHERE n < ?2 RETURNING n").bind(k, limit).first();
    return r ? r.n : 0;
  }
  async give(k) { await this.db.prepare("UPDATE counts SET n = n - 1 WHERE k = ?1 AND n > 0").bind(k).run(); }
  async add(k, n, limit) {
    if (n > limit) return 0;   // the INSERT branch has no WHERE
    const r = await this.db.prepare("INSERT INTO counts (k, n) VALUES (?1, ?2) ON CONFLICT(k) DO UPDATE SET n = n + ?2 WHERE n + ?2 <= ?3 RETURNING n").bind(k, n, limit).first();
    return r ? r.n : 0;
  }
}

const num = (v, d) => (v === undefined || v === "" ? d : +v);   // "0" is a real limit (the off switch)
const goodImage = s => typeof s === "string" && !!s && s.length <= IMAGE_MAX && /^[A-Za-z0-9+/=]+$/.test(s);
const goodDevice = s => /^dev-[a-z0-9]{8,32}$/.test(s || "");

/** Reserve the month's cents, call the model, refund the cents only if the request never left for OpenAI. */
async function paidCall(job, cents, env, counts, day, onFail) {
  const kUsd = `usd|${day.slice(0, 7)}`, cap = num(env.MONTH_CENTS, 500);
  if (!(await counts.add(kUsd, cents, cap))) { await onFail({}); return { off: true }; }
  try {
    return { img: await (env.callModel || callOpenAI)(job, env) };
  } catch (e) {
    console.log("model failed", e?.message || e);   // status only: never the model's words about the photo
    if (!e?.sent) await counts.add(kUsd, -cents, cap);
    await onFail(e);
    return { err: e };
  }
}

async function transform(b, req, env, counts, day, origin) {
  if (!STYLES.includes(b.style) || !KINDS.includes(b.kind) || !goodDevice(b.device) || !goodImage(b.image)) return json({ error: "bad request" }, 400, origin);
  if (!secret(env.OPENAI_KEY)) return json({ error: "off" }, 503, origin);
  if (env.LIMIT) { const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" }); if (!success) return json({ error: "slow down" }, 429, origin); }

  const perDev = num(env.DEVICE_DAY, 5), all = num(env.ALL_DAY, 30), perIp = num(env.IP_DAY, 15);
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
  const job = { prompt: PROMPTS[b.style](b.kind), image: b.image, ref: b.style === "fluff", quality: "low", size: "1024x1024" };
  // the day slots come back on any failure OpenAI did not bill (a refused or failed call costs nothing)
  const r = await paidCall(job, CENTS.preview, env, counts, day, async e => { if (!e.billed) { await counts.give(kDev); await counts.give(kIp); await counts.give(kAll); } });
  if (r.off) return json({ error: "off" }, 503, origin);
  if (r.err) return json({ error: "model" }, 502, origin);
  return json({ mime: r.img.mime, image: r.img.data, left: perDev - used }, 200, origin);
}

async function sheet(b, req, env, counts, day) {
  const token = req.headers.get("x-purchase") || "";
  if (!secret(env.DEBUG_PURCHASE) || token !== secret(env.DEBUG_PURCHASE)) return json({ error: "purchase" }, 402);
  if (!KINDS.includes(b.kind) || !goodDevice(b.device) || !goodImage(b.image) || !/^[A-Za-z0-9_-]{1,64}$/.test(b.entry || "")) return json({ error: "bad request" }, 400);
  if (!secret(env.OPENAI_KEY)) return json({ error: "off" }, 503);
  if (env.LIMIT) { const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" }); if (!success) return json({ error: "slow down" }, 429); }

  const kEntry = `sheet|${token}|${b.entry}`, kDay = `sheetday|${token}|${day}`;
  if (!(await counts.take(kDay, SHEET_PER_DAY))) return json({ error: "limit" }, 429);
  const used = await counts.take(kEntry, SHEET_PER_ENTRY);
  if (!used) { await counts.give(kDay); return json({ error: "limit" }, 429); }
  const job = { prompt: SHEET_PROMPT(b.kind), image: b.image, ref: true, quality: "medium", size: "1536x1024" };
  // an entry's 2 = one call + one retry, so a sent call keeps its slot; only a call that never left comes back
  const r = await paidCall(job, CENTS.sheet, env, counts, day, async e => { if (!e.sent) { await counts.give(kEntry); await counts.give(kDay); } });
  if (r.off) return json({ error: "off" }, 503);
  if (r.err) return json({ error: "model" }, 502);
  return json({ mime: r.img.mime, image: r.img.data, left: SHEET_PER_ENTRY - used });
}

export async function handle(req, env, counts, { day = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10) } = {}) {   // KST day
  const origin = req.headers.get("origin"), app = req.headers.get("x-app") === "catchpet";
  if (origin ? !allowed(origin) : !app) return json({ error: "origin" }, 403);   // no Origin = the app, no CORS headers
  if (req.method === "OPTIONS" && origin) return new Response(null, { status: 204, headers: cors(origin) });
  const path = new URL(req.url).pathname;
  if (req.method !== "POST" || !(path === "/transform" || (path === "/sheet" && app))) return json({ error: "not found" }, 404, origin);
  if (+(req.headers.get("content-length") || 0) > IMAGE_MAX + 2000) return json({ error: "too big" }, 413, origin);
  const b = await req.json().catch(() => null);
  if (!b || typeof b !== "object") return json({ error: "bad request" }, 400, origin);
  return path === "/sheet" ? sheet(b, req, env, counts, day) : transform(b, req, env, counts, day, origin);
}

export default {
  async fetch(req, env) { return handle(req, env, new D1Counts(env.DB)); },
};
