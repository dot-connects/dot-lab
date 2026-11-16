// 「우리 집 몬스터 잡기」 transform: a caught dog / cat / person cut-out -> an image model -> a cute picture of THAT one.
// /transform (web game + app preview): style "fluff" = our pale fur-ball creatures (a style reference image goes along),
// "figure" = a small 3D figurine of itself. /sheet (app, bought): a 3 x 2 pose sheet of the fur-ball for the pet.
// The photo is passed through and never stored. Cost guards: DEVICE_DAY previews per device a day, ALL_DAY previews for
// everyone (a device id is client-made, so ALL_DAY is the real ceiling; IP_DAY keeps one network from eating it all),
// and MONTH_CENTS = a money ceiling for the month over both routes (cents reserved before each OpenAI call).
// The Origin / x-app check only keeps other web pages out; it is not a cost guard (any client can send the header).
// Secrets: OPENAI_KEY (none = 503 "off"), DEBUG_PURCHASE (the dev app's token: /sheet + previews without day limits,
// on its own month ceiling DEV_MONTH_CENTS; empty = /sheet refused).
// Set ALL_DAY = "0" to stop previews, MONTH_CENTS = "0" to stop every AI call at once.

import { secret } from "./secret.js";
import { decodeIntegrity, getPurchase, consumePurchase } from "./google.js";
import { purchaseVerdict, integrityVerdict, certHex, requestHash, PRODUCT_STYLE } from "./verdicts.js";
import { Ledger } from "./ledger.js";
export { secret };
const ORIGINS = [/^https:\/\/dot-connects\.github\.io$/, /^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/];
const STYLES = ["fluff", "figure"];
// the app's dex kinds (Dex.KIND_NAMES): each is an English word the prompts use as is
export const KINDS = ["dog", "cat", "person", "bird", "cow", "horse", "sheep", "elephant", "bear", "zebra", "giraffe",
  "hamster", "rabbit", "turtle", "fish", "guinea pig", "lizard"];
// a group caught together (operator 11-15): up to 3 kinds joined by "+", e.g. "person+cat"
export const validKind = k => typeof k === "string" && k.split("+").length <= 3 && k.split("+").every(x => KINDS.includes(x));
const groupWho = k => { const a = k.split("+"); return a.length === 1 ? null : a.map(x => "a " + x).join(" and ") };
export const GROUP_PROMPTS = {
  fluff: k => `Image 1 is a real photo of ${groupWho(k)} together (${k.split("+").length} of them). Image 2 is the art style of our game creatures: small round pale 3D plush fur balls, soft felt/fur texture, tiny black dot eyes, small pink blush, short stubby feet, no outline, soft studio light.

Draw EACH of them as its own chubby round fur-ball plush creature in exactly the style of image 2, keeping what makes each one recognisable (hair or fur colours and markings, glasses, clothing colours, never any text), and keep them TOGETHER the way they are in the photo: touching, side by side or one held by the other, the same relative sizes. All of them fully in view, centred as one group, front view, square image, no text. Background: one flat solid pure green (#00FF00) colour edge to edge, no floor, no shadow, no gradient.`,
  figure: k => `Image 1 is a real photo of ${groupWho(k)} together (${k.split("+").length} of them). Make a cute small 3D figurine set of THEM: each one its own figurine with its real body shape simplified, slightly chibi proportions (bigger heads), soft matte clay/plush 3D render, real colours, markings, glasses, face features and clothing colours (no text on clothes), tiny black dot eyes, small pink blush, gentle smiles. Keep them TOGETHER the way they are in the photo: touching, side by side or one held by the other, the same relative sizes. All of them fully in view, centred as one group, front three-quarter view, square image, no text. Background: one flat solid pure green (#00FF00) colour edge to edge, no floor, no shadow, no gradient.`,
};
const IMAGE_MAX = 2_000_000;   // base64 chars, ~1.5 MB: the game sends a <= 512 px PNG
const MODEL = "gpt-image-2.6-flare";
const OPENAI_URL = "https://api.openai.com/v1/images/edits";
const STYLE_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-09-catch/site/img/style-ref.jpg";
const CENTS = { preview: 5, sheet: 8 };   // reserved per call: a conservative price incl. input image tokens

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

// the figurine look of PROMPTS.figure as a pose sheet (operator 11-15: both looks can be bought as a pet)
export const FIGURE_SHEET_PROMPT = who => `Image 1 is a real photo of a ${who} (it may be seen from the side or lying down).

Make a character pose sheet of THIS ${who} as a cute small 3D figurine of itself (not a ball): its real body shape simplified, slightly chibi proportions (bigger head), soft matte clay/plush 3D render, keep its real colours, markings, ear shape, face features and clothing colour (no text on clothes). Tiny black dot eyes, small pink blush. The SAME figurine appears 6 times in a 3 x 2 grid of equal square cells, every cell the same size and scale, the figurine centred in its cell with space around it:
top row: 1) standing, front view, calm; 2) front view, big happy smile with eyes closed in joy; 3) side view walking to the right, left foot forward.
bottom row: 4) side view walking to the right, right foot forward; 5) lying down asleep, eyes closed; 6) curled up asleep, sitting hugging its knees.
No text, no numbers, no grid lines. Background of the whole image: one flat solid pure green (#00FF00), no floor, no shadows.`;

const SHEET_STYLES = ["fluff", "figure"];

// operator 11-15: walking with crossing arms and legs, flailing while held, a pratfall when dropped -> 12 cells
const CELLS12 = `A 4 x 3 grid of 12 equal cells, every cell the same scale, the subject centred in its cell with space around it, feet at the same height in every cell of a row:
row 1: 1) standing, front view, calm; 2) front view, big happy smile, eyes closed in joy; 3) side view walking to the right: LEFT leg forward and RIGHT arm forward (contact pose); 4) side view walking to the right: legs passing, both feet under the body, arms by the sides.
row 2: 5) side view walking to the right: RIGHT leg forward and LEFT arm forward; 6) side view walking to the right: legs passing again, the other leg in front; 7) lying down asleep, eyes closed; 8) curled up asleep in a round ball.
row 3: 9) lifted into the air (do NOT draw any hand or whoever lifts it), dangling, arms and legs flailing wide, surprised open mouth; 10) dangling with the arms and legs swung the other way, still flailing; 11) fallen flat on the belly after a tumble, arms and legs spread, dizzy swirl eyes; 12) sitting up on the floor, dizzy and dazed, one hand on the head.`;

/** The 12-cell pose sheet: look = how it is drawn, who = what is in the photo. */
export const SHEET12_PROMPT = (style, k) => {
  const group = k.includes("+");
  const who = group ? `${groupWho(k)} together (${k.split("+").length} of them)` : `a ${k}`;
  const look = group ? GROUP_LOOK[style] : style === "fluff"
    ? "ONE of our chubby round fur-ball plush creatures in exactly the style of image 2 (small round pale 3D plush fur ball, soft fur texture, tiny black dot eyes, small pink blush, short stubby arms and feet)"
    : "a cute small 3D figurine of itself (not a ball): real body shape simplified, slightly chibi proportions (bigger head), soft matte clay/plush 3D render, tiny black dot eyes, small pink blush";
  return `Image 1 is a real photo of ${who} (it may be seen from the side or lying down).${style === "fluff" ? " Image 2 shows the art style of our game creatures." : ""}

Make a character pose sheet of THIS ${group ? "GROUP, each of them as" : ""} ${look}. Keep what makes ${group ? "each one" : "it"} recognisable: hair or fur colours and markings, glasses, ear shape, face, clothing colours (no text).${group ? " In EVERY cell the SAME group appears TOGETHER, touching, with the same relative sizes." : " The SAME character in every cell."}
${CELLS12}
No text, no numbers, no grid lines. Background of the whole image: one flat solid pure green (#00FF00), no floor, no shadows.`;
};

// a group's pose sheet (operator 11-15): the same group in every cell, always together
const GROUP_LOOK = {
  fluff: "each of them as its own chubby round fur-ball plush creature in exactly the style of image 2 (small round pale 3D plush fur balls, soft fur texture, tiny black dot eyes, small pink blush, short stubby feet)",
  figure: "each of them as its own cute small 3D figurine: real body shape simplified, slightly chibi proportions (bigger heads), soft matte clay/plush 3D render, tiny black dot eyes, small pink blush",
};
export const GROUP_SHEET_PROMPT = (style, k) => `Image 1 is a real photo of ${groupWho(k)} together (${k.split("+").length} of them).${style === "fluff" ? " Image 2 shows the art style of our game creatures." : ""}

Make a character pose sheet of THIS GROUP, ${GROUP_LOOK[style]}. Keep what makes each one recognisable: hair or fur colours and markings, glasses, face features, clothing colours (no text). In EVERY cell the SAME group appears TOGETHER, touching, with the same relative sizes. A 3 x 2 grid of equal square cells, every cell the same scale, the group centred in its cell with space around it:
top row: 1) standing together, front view, calm; 2) front view, all with big happy smiles, eyes closed in joy; 3) side view walking together to the right, left feet forward.
bottom row: 4) side view walking together to the right, right feet forward; 5) lying down together asleep, eyes closed; 6) curled up together asleep in one cosy round pile.
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
    // a 4xx is a refusal OpenAI does not bill (bad image, moderation): marked so the money reservation comes back
    if (!r.ok) throw Object.assign(sent(new Error(`model HTTP ${r.status} ${body?.error?.code || ""}`)), { rejected: r.status < 500 });
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
    if (limit <= 0) return 0;   // "0" = off: the INSERT branch below has no WHERE
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

// one IPv6 user holds a whole /64: count per network, not per address (security review)
export const ipKey = ip => (ip && ip.includes(":") ? ip.split(":").slice(0, 4).join(":") + "::/64" : ip || "?");
const num = (v, d) => (v === undefined || v === "" ? d : +v);   // "0" is a real limit (the off switch)
const goodImage = s => typeof s === "string" && !!s && s.length <= IMAGE_MAX && /^[A-Za-z0-9+/=]+$/.test(s);
const goodDevice = s => /^dev-[a-z0-9]{8,32}$/.test(s || "");

/** Reserve the month's cents, call the model, refund the cents only if the request never left for OpenAI. */
// dev = the operator's debug token (the dev app): its own month ceiling DEV_MONTH_CENTS, so testing never eats the
// public ceiling and the public never blocks testing; MONTH_CENTS = "0" still stops both (operator 11-16).
// bucket "paid" = bought sheets, on PAID_MONTH_CENTS (a runaway guard only): free testers can never block a buyer (review M3)
const BUCKETS = { pub: ["usd", "MONTH_CENTS", 500], dev: ["devusd", "DEV_MONTH_CENTS", 2000], paid: ["paidusd", "PAID_MONTH_CENTS", 5000] };
async function paidCall(job, cents, env, counts, day, onFail, dev = false) {
  const [key, capVar, capDefault] = BUCKETS[dev === true ? "dev" : dev || "pub"];
  const kUsd = `${key}|${day.slice(0, 7)}`;
  const cap = num(env.MONTH_CENTS, 500) <= 0 ? 0 : num(env[capVar], capDefault);
  if (cap <= 0 || !(await counts.add(kUsd, cents, cap))) { await onFail({}); return { off: true }; }
  try {
    return { img: await (env.callModel || callOpenAI)(job, env) };
  } catch (e) {
    console.log("model failed", e?.message || e);   // status only: never the model's words about the photo
    if (!e?.sent || e?.rejected) await counts.add(kUsd, -cents, cap);
    await onFail(e);
    return { err: e };
  }
}

const previewJob = b => ({ prompt: (b.kind.includes("+") ? GROUP_PROMPTS : PROMPTS)[b.style](b.kind), image: b.image, ref: b.style === "fluff", quality: "low", size: "1024x1024" });

async function transform(b, req, env, counts, day, origin, dev) {
  if (!STYLES.includes(b.style) || !validKind(b.kind) || !goodDevice(b.device) || !goodImage(b.image)) return json({ error: "bad request" }, 400, origin);
  if (!secret(env.OPENAI_KEY)) return json({ error: "off" }, 503, origin);
  if (env.LIMIT) { const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" }); if (!success) return json({ error: "slow down" }, 429, origin); }
  if (dev) {   // the dev app: no day counters, its own month ceiling
    const r = await paidCall(previewJob(b), CENTS.preview, env, counts, day, async () => {}, true);
    if (r.off) return json({ error: "off" }, 503, origin);
    if (r.err) return json({ error: "model" }, 502, origin);
    return json({ mime: r.img.mime, image: r.img.data }, 200, origin);
  }

  const perDev = num(env.DEVICE_DAY, 5), all = num(env.ALL_DAY, 30), perIp = num(env.IP_DAY, 15);
  const kAll = `${day}|all`, kDev = `${day}|${b.device}`, kIp = `${day}|ip:${ipKey(req.headers.get("cf-connecting-ip"))}`;
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
  const job = previewJob(b);
  // day slots come back unless OpenAI refused (4xx) or billed: a refused call keeps its slot, so junk images cannot
  // be retried for free until they drain the month (review M1, 2026-11-13); its money comes back in paidCall
  const r = await paidCall(job, CENTS.preview, env, counts, day, async e => { if (!e.sent || (!e.rejected && !e.billed)) { await counts.give(kDev); await counts.give(kIp); await counts.give(kAll); } });
  if (r.off) return json({ error: "off" }, 503, origin);
  if (r.err) return json({ error: "model" }, 502, origin);
  return json({ mime: r.img.mime, image: r.img.data, left: perDev - used }, 200, origin);
}

function sheetJob(b, style) {
  return String(b.layout) === "12"
    ? { prompt: SHEET12_PROMPT(style, b.kind), image: b.image, ref: style === "fluff", quality: "medium", size: "1536x1024" }
    : b.kind.includes("+")
    ? { prompt: GROUP_SHEET_PROMPT(style, b.kind), image: b.image, ref: style === "fluff", quality: "medium", size: "1536x1024" }
    : style === "figure"
    ? { prompt: FIGURE_SHEET_PROMPT(b.kind), image: b.image, ref: false, quality: "medium", size: "1536x1024" }
    : { prompt: SHEET_PROMPT(b.kind), image: b.image, ref: true, quality: "medium", size: "1536x1024" };
}
const layoutOf = b => (String(b.layout) === "12" ? "12" : "6");

// ---------- Play: integrity, purchases, free test sheets (spec-release.md §2-§4) ----------

const google = env => env.google || { decodeIntegrity, getPurchase, consumePurchase };
const bytesB64 = u8 => { let s = ""; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode(...u8.subarray(i, i + 0x8000)); return btoa(s); };
const sha256hex = async t => Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t))), x => x.toString(16).padStart(2, "0")).join("");

/** "ok" | "off" (no service account: nothing can be checked) | a failure reason. Statuses only in logs. */
async function integrity(req, env, path, b, text) {
  if (!env.google && !secret(env.GOOGLE_SA)) return "off";
  const tok = req.headers.get("x-integrity") || "";
  if (!tok) return "missing";
  try {
    const v = await google(env).decodeIntegrity(env, tok);
    const want = await requestHash(path, b.device, text);
    const r = integrityVerdict(v, { pkg: env.PKG || "kr.dot.catchpet", certHex: certHex(env.CERT_SHA256), requestHash: want, now: Date.now() });
    if (r !== "ok") console.log("integrity", r);
    return r;
  } catch (e) {
    console.log("integrity call", e?.status);
    return e?.status === 0 ? "off" : (e?.status === 400 ? "badtoken" : "busy");   // Google trouble is retryable (review M2)
  }
}

async function sheet(b, req, env, counts, day, dev, ledger, text) {
  if (!validKind(b.kind) || !goodDevice(b.device) || !goodImage(b.image) || !/^[A-Za-z0-9_-]{1,64}$/.test(b.entry || "")) return json({ error: "bad request" }, 400);
  const style = b.style ?? "fluff";   // apps before 0.3.8 send no style: the fur-ball sheet
  if (!SHEET_STYLES.includes(style)) return json({ error: "bad request" }, 400);
  if (!secret(env.OPENAI_KEY)) return json({ error: "off" }, 503);
  if (env.LIMIT) { const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" }); if (!success) return json({ error: "slow down" }, 429); }

  if (dev) {   // the dev app: no integrity, no purchase, no limits, its own month ceiling (operator 11-16)
    const r = await paidCall(sheetJob(b, style), CENTS.sheet, env, counts, day, async () => {}, true);
    if (r.off) return json({ error: "off" }, 503);
    if (r.err) return json({ error: "model" }, 502);
    return json({ mime: r.img.mime, image: r.img.data, layout: layoutOf(b) });
  }

  const ig = await integrity(req, env, "/sheet", b, text);
  if (ig === "off" || ig === "busy") return json({ error: ig }, 503);
  if (ig !== "ok") return json({ error: "integrity" }, 403);

  const token = typeof b.purchaseToken === "string" ? b.purchaseToken : "";
  if (!token) {   // a test-period free sheet: reserved before the draw, handed back if it fails (review H1)
    const total = num(env.FREE_SHEETS, 0);
    // a device id is app-made (a reinstall = 3 more): one network and everyone are capped a day too (security review)
    const kIp = `${day}|freeip:${ipKey(req.headers.get("cf-connecting-ip"))}`, kAll = `${day}|freeall`;
    if (!(await counts.take(kAll, num(env.FREE_ALL_DAY, 60)))) return json({ error: "free" }, 429);
    if (!(await counts.take(kIp, num(env.FREE_IP_DAY, 6)))) { await counts.give(kAll); return json({ error: "free" }, 429); }
    if (!(await ledger.takeFree(b.device, total))) { await counts.give(kIp); await counts.give(kAll); return json({ error: "free" }, 429); }
    // the transform rule: a slot comes back only if the call never left or OpenAI failed on its side (5xx, not billed);
    // a refused junk image or a billed empty answer keeps it, so failures cannot be farmed (security review)
    const r = await paidCall(sheetJob(b, style), CENTS.sheet, env, counts, day, async e => {
      if (!e.sent || (!e.rejected && !e.billed)) { await ledger.giveFree(b.device); await counts.give(kIp); await counts.give(kAll); }
    });
    if (r.off) return json({ error: "off" }, 503);
    if (r.err) return json({ error: "model" }, 502);
    return json({ mime: r.img.mime, image: r.img.data, layout: layoutOf(b), free_left: await ledger.freeLeft(b.device, total) });
  }

  if (token.length > 512 || PRODUCT_STYLE[b.product] !== style) return json({ error: "bad request" }, 400);
  const g = google(env);
  const consume = async () => {
    try { if (await g.consumePurchase(env, b.product, token)) await ledger.markConsumed(token); }
    catch (e) { console.log("consume", e?.status); }   // the app consumes too; Google refunds only unacknowledged buys
  };
  const c = await ledger.claim(token, b.product, b.device, Date.now());
  if (c.go === "mismatch") return json({ error: "bad request" }, 400);
  if (c.go === "busy") return json({ error: "busy" }, 409);
  if (c.go === "cached") {
    const o = await env.SHEETS.get(c.key);
    if (!o) { await ledger.fail(token); return json({ error: "busy" }, 409); }   // expired from R2: the next try draws again
    if (!c.consumed) await consume();
    return json({ mime: o.httpMetadata?.contentType || "image/webp", image: bytesB64(new Uint8Array(await o.arrayBuffer())), layout: c.layout });
  }
  // "new" and "draw" (a failed or dead earlier try) both ask Google first: a refund in between draws nothing (review M1)
  const drop = async () => (c.go === "new" ? ledger.forget(token) : ledger.fail(token));
  let p;
  try { p = await g.getPurchase(env, b.product, token); }
  catch (e) {
    console.log("purchase check", e?.status);
    await drop();
    if (e?.status === 0) return json({ error: "off" }, 503);
    // only "this token is not a purchase" is final; a Google outage or our auth trouble is retryable (review M2)
    return [400, 404, 410].includes(e?.status) ? json({ error: "purchase" }, 402) : json({ error: "busy" }, 503);
  }
  const v = purchaseVerdict(p);
  if (v !== "ok") { await drop(); return json({ error: v === "pending" ? "pending" : "purchase" }, 402); }
  const r = await paidCall(sheetJob(b, style), CENTS.sheet, env, counts, day, async () => {}, "paid");
  if (r.off || r.err) { await ledger.fail(token); return r.off ? json({ error: "off" }, 503) : json({ error: "model" }, 502); }
  try {
    const key = `sheet/${await sha256hex(token)}`;
    await env.SHEETS.put(key, b64bytes(r.img.data), { httpMetadata: { contentType: r.img.mime } });
    await ledger.finish(token, key, layoutOf(b));
  } catch (e) {   // the sheet is drawn and paid for: hand it over anyway (review M4); a later retry would draw again
    console.log("store failed", e?.message);
    await ledger.fail(token).catch(() => {});
  }
  await consume();
  return json({ mime: r.img.mime, image: r.img.data, layout: layoutOf(b) });
}

const REASONS = ["odd", "person", "other"];

export async function handle(req, env, counts, { day = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10), ledger = new Ledger(env.DB) } = {}) {   // KST day
  const origin = req.headers.get("origin"), app = req.headers.get("x-app") === "catchpet";
  const dbg = secret(env.DEBUG_PURCHASE), given = req.headers.get("x-purchase") || "";
  // compared as digests so the time taken says nothing about the token (review L4)
  const dev = app && !origin && !!dbg && !!given && (await sha256hex(given)) === (await sha256hex(dbg));
  if (origin ? !allowed(origin) : !app) return json({ error: "origin" }, 403);   // no Origin = the app, no CORS headers
  if (req.method === "OPTIONS" && origin) return new Response(null, { status: 204, headers: cors(origin) });
  const url = new URL(req.url), path = url.pathname;
  if (origin) return json({ error: "off" }, 503, origin);   // the frozen web game: AI is app-only now, it keeps its own picture
  if (req.method === "GET" && path === "/quota") {
    const device = url.searchParams.get("device") || "";
    if (!goodDevice(device)) return json({ error: "bad request" }, 400);
    const total = num(env.FREE_SHEETS, 0);
    return json({ free_left: await ledger.freeLeft(device, total), free_total: total });
  }
  if (req.method !== "POST" || !["/transform", "/sheet", "/report"].includes(path)) return json({ error: "not found" }, 404);
  if (+(req.headers.get("content-length") || 0) > IMAGE_MAX + 2000) return json({ error: "too big" }, 413);
  const text = await req.text();
  let b = null;
  try { b = JSON.parse(text); } catch {}
  if (!b || typeof b !== "object") return json({ error: "bad request" }, 400);
  if (path === "/report") {
    if (!goodDevice(b.device) || !/^[A-Za-z0-9_-]{1,64}$/.test(b.entry || "") || !validKind(b.kind) || !STYLES.includes(b.style) || !REASONS.includes(b.reason)) return json({ error: "bad request" }, 400);
    if (env.LIMIT) { const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" }); if (!success) return json({ error: "slow down" }, 429); }
    if (!(await counts.take(`${day}|report:${b.device}`, 20))) return json({ error: "slow down" }, 429);   // review L2
    await ledger.report(b, Date.now());
    return json({ ok: true });
  }
  if (path === "/sheet") return sheet(b, req, env, counts, day, dev, ledger, text);
  if (!dev) {
    if (!STYLES.includes(b.style) || !validKind(b.kind) || !goodDevice(b.device) || !goodImage(b.image)) return json({ error: "bad request" }, 400);
    const ig = await integrity(req, env, "/transform", b, text);
    if (ig === "off" || ig === "busy") return json({ error: ig }, 503);
    if (ig !== "ok") return json({ error: "integrity" }, 403);
  }
  return transform(b, req, env, counts, day, null, dev);
}

export default {
  async fetch(req, env) { return handle(req, env, new D1Counts(env.DB), { ledger: new Ledger(env.DB) }); },
};
