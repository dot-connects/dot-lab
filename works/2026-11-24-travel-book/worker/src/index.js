// 「여행 책자」 AI relay. The app sends 512 px previews, local times, place names and the user's notes; Claude picks
// photos and writes captions/essay. Keys stay here, a month money ceiling guards the bill, nothing is stored.
// Money unit = mills (1/10 cent, integers). Reserve the worst case before the call, settle the real cost after.
import Anthropic from "@anthropic-ai/sdk";
import { MemoryCounts, D1Counts } from "./counts.js";
import { SYSTEM_BOOK, SYSTEM_REWRITE, BOOK_SCHEMA, REWRITE_SCHEMA } from "./prompt.js";

export { MemoryCounts, D1Counts };

const MAX_IMAGES = 60, MAX_REWRITE_IMAGES = 4;
const MAX_B64 = 400_000;           // one 512 px JPEG is ~40-80 KB as base64
const BOOK_MAX_TOKENS = 12000, REWRITE_MAX_TOKENS = 1000;
// claude-sonnet-5-5: $2 / M input = 0.002 mills a token, $10 / M output = 0.01 mills a token
const IN_MILLS = 0.002, OUT_MILLS = 0.01;
const IMG_TOKENS = 400;            // a 512 px preview is ~250-330 tokens (28 px patches); rounded up for the reservation
const KEEPALIVE_MS = 10_000;

const num = (v, d) => (v === undefined || v === "" ? d : +v);   // "0" is a real limit (the off switch)
const kst = (ms = Date.now()) => new Date(ms + 9 * 3600_000).toISOString();
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });

export function reserveMills(images, chars, maxTokens) {
  return Math.ceil((images * IMG_TOKENS + Math.ceil(chars / 2) + 2500) * IN_MILLS + maxTokens * OUT_MILLS);
}
export function realMills(usage) {
  const inp = (usage?.input_tokens || 0) + (usage?.cache_creation_input_tokens || 0) * 1.25 + (usage?.cache_read_input_tokens || 0) * 0.1;
  return Math.ceil(inp * IN_MILLS + (usage?.output_tokens || 0) * OUT_MILLS);
}

const goodB64 = s => typeof s === "string" && s.length > 0 && s.length <= MAX_B64 && /^[A-Za-z0-9+/=]+$/.test(s);
const clean = (s, n) => (typeof s === "string" ? s.slice(0, n) : "");
const jpeg = b64 => ({ type: "image", source: { type: "base64", media_type: "image/jpeg", data: b64 } });

/** The user turn: each preview labelled with its id, local time and place, then the notes and the options. */
export function bookContent(body) {
  const shots = new Map((body.shots || []).map(s => [s.id, s]));
  const content = [];
  for (const im of body.images) {
    const s = shots.get(im.id) || {};
    const video = s.video ? `영상 ${Math.round((s.durMs || 0) / 100) / 10}초` + (im.atMs != null ? `, 장면 ${im.atMs}ms` : "") : "";
    const label = [`[${clean(im.id, 64)}]`, clean(s.local, 20), clean(s.place, 40), video].filter(Boolean).join(" ");
    content.push({ type: "text", text: label }, jpeg(im.b64));
  }
  const notes = (body.notes || []).map(n => `${clean(n.day, 10) || "날짜 없음"}: ${clean(n.text, 12000)}`).join("\n\n");
  const o = body.opts || {};
  content.push({ type: "text", text:
    `메모:\n${notes || "(없음)"}\n\n옵션: 말투=${clean(o.tone, 20) || "auto"}, 글의 양=${clean(o.textAmount, 20) || "caption_essay"}, ` +
    `사람 사진=${clean(o.people, 20) || "mix"}, 길이=${clean(o.length, 20) || "normal"}` });
  return content;
}

function rewriteContent(body, images) {
  const p = body.page || {};
  const notes = clean((body.notes || []).map(n => n.text).join(" "), 2000);
  return [...images.map(im => jpeg(im.b64)),
    { type: "text", text: `시각·장소: ${clean(p.local, 20)} ${clean(p.place, 40)}\n지금 캡션: ${clean(p.caption, 60)}\n메모: ${notes}` }];
}

/** Text chars in a request (for the input reservation). */
const chars = body => JSON.stringify({ s: body.shots, n: body.notes, o: body.opts, p: body.page }).length;

export async function handle(req, env, ctx, counts) {
  const url = new URL(req.url);
  if (req.method !== "POST" || !["/book", "/rewrite"].includes(url.pathname)) return json({ error: "not found" }, 404);
  if (!env.APP_TOKEN || req.headers.get("x-app-token") !== env.APP_TOKEN) return json({ error: "token" }, 401);
  if (env.LIMIT && !(await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" })).success) return json({ error: "slow" }, 429);
  let body;
  try { body = await req.json(); } catch { return json({ error: "body" }, 400); }
  const book = url.pathname === "/book";
  const images = Array.isArray(body?.images) ? body.images : [];
  if (images.length > (book ? MAX_IMAGES : MAX_REWRITE_IMAGES)) return json({ error: "too many images" }, 413);
  if (!images.length || !images.every(i => goodB64(i?.b64) && typeof i.id === "string")) return json({ error: "body" }, 400);
  if (!env.ANTHROPIC_API_KEY) return json({ error: "off" }, 503);

  const now = kst(), kMonth = `m|${now.slice(0, 7)}`, kDay = `d|${now.slice(0, 10)}`;
  const cap = num(env.MONTH_CENTS, 1000) * 10;
  if (book && !(await counts.take(kDay, num(env.DAY_BOOKS, 20)))) return json({ error: "off" }, 503);
  const maxTokens = book ? BOOK_MAX_TOKENS : REWRITE_MAX_TOKENS;
  const reserved = reserveMills(images.length, chars(body), maxTokens);
  if (cap <= 0 || !(await counts.add(kMonth, reserved, cap))) {
    if (book) await counts.give(kDay);
    return json({ error: "off" }, 503);
  }

  const client = env.makeClient ? env.makeClient() : new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 0 });
  const system = book ? SYSTEM_BOOK : SYSTEM_REWRITE;
  const schema = book ? BOOK_SCHEMA : REWRITE_SCHEMA;
  const content = book ? bookContent(body) : rewriteContent(body, images);

  // Stream to the app: a space every 10 s keeps the connection open while the model thinks, then one JSON object.
  // Status is already 200 by then, so errors travel in the body: {error: "model" | "upstream"}.
  const enc = new TextEncoder();
  const { readable, writable } = new TransformStream();
  const w = writable.getWriter();
  const beat = setInterval(() => w.write(enc.encode(" ")).catch(() => {}), KEEPALIVE_MS);
  const work = (async () => {
    let started = false, out = { error: "model" }, spent = null;
    try {
      const stream = client.messages.stream({
        model: env.MODEL || "claude-sonnet-5-5", max_tokens: maxTokens,
        output_config: { effort: "medium", format: { type: "json_schema", schema } },
        system, messages: [{ role: "user", content }],
      });
      stream.on("streamEvent", e => { if (e.type === "message_start") started = true; });
      const msg = await stream.finalMessage();
      spent = realMills(msg.usage);
      console.log("model", msg.stop_reason, msg.usage?.input_tokens, msg.usage?.output_tokens);   // counts only, never text
      const text = msg.content.find(b => b.type === "text")?.text;
      if (msg.stop_reason === "end_turn" && text) {
        try { out = JSON.parse(text); } catch { out = { error: "model" }; }
      }
    } catch (e) {
      console.log("upstream", e?.status || "network");
      out = { error: "upstream" };
      if (!started) spent = 0;   // rejected before the model ran: not billed
      // started and then cut off: the real cost is unknown, the reservation stays (over-counts, never under)
    } finally {
      clearInterval(beat);
      if (spent !== null) await counts.settle(kMonth, spent - reserved);
      try { await w.write(enc.encode(JSON.stringify(out))); await w.close(); } catch { /* the app has gone */ }
    }
  })();
  ctx?.waitUntil?.(work);
  return new Response(readable, { status: 200, headers: { "content-type": "application/json" } });
}

export default {
  async fetch(req, env, ctx) { return handle(req, env, ctx, new D1Counts(env.DB)); },
};
