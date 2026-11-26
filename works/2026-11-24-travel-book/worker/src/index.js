// 「여행 책자」 AI relay. The app sends 512 px previews, local times, place names and the user's notes; the model picks
// photos and writes captions/essay. Keys stay here, a month money ceiling guards the bill, nothing is stored.
// Model: OpenAI Responses API (operator 11-25: use the GPT key already on hand); the MODEL var picks the model.
// Money unit = mills (1/10 cent, integers). Reserve the worst case before the call, settle the real cost after.
import { MemoryCounts, D1Counts } from "./counts.js";
import { SYSTEM_PICK, systemWrite, systemRewrite, PICK_SCHEMA, WRITE_SCHEMA, REWRITE_SCHEMA,
  THEMES, systemLayout, LAYOUT_SCHEMA } from "./prompt.js";

export { MemoryCounts, D1Counts };

// per route: image cap, output budget (reasoning included), day counter key + var + default
const ROUTES = {
  "/pick": { images: 60, tokens: () => 12000, day: ["dp", "DAY_BOOKS", 20] },
  "/write": { images: 48, tokens: shape => (shape === "short" ? 8000 : 16000), day: ["dw", "WRITE_DAY", 60] },
  "/rewrite": { images: 4, tokens: () => 1000, day: ["dr", "REWRITE_DAY", 200] },
  // the whole-book page plan; shares the /write day counter (it replaces /write for book making)
  "/layout": { images: 72, tokens: () => 24000, day: ["dw", "WRITE_DAY", 60] },
};
const MAX_CATALOG = 80, MAX_PREV = 100_000;
const SHAPES = ["short", "diary", "mag"];
const MAX_B64 = 400_000;           // one 512 px JPEG is ~40-80 KB as base64
// gpt-5.4: $2.50 / M input = 0.0025 mills a token, $15 / M output (reasoning included) = 0.015 mills a token
const IN_MILLS = 0.0025, OUT_MILLS = 0.015;
const IMG_TOKENS = 400;            // a 512 px preview at detail "low" is well under this; rounded up for the reservation
const KEEPALIVE_MS = 10_000;
const OPENAI_URL = "https://api.openai.com/v1/responses";

const num = (v, d) => (v === undefined || v === "" ? d : +v);   // "0" is a real limit (the off switch)
const kst = (ms = Date.now()) => new Date(ms + 9 * 3600_000).toISOString();
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });

export function reserveMills(images, chars, maxTokens) {
  return Math.ceil((images * IMG_TOKENS + Math.ceil(chars / 2) + 2500) * IN_MILLS + maxTokens * OUT_MILLS);
}
export function realMills(usage) {
  const cached = usage?.input_tokens_details?.cached_tokens || 0;
  const inp = (usage?.input_tokens || 0) - cached + cached * 0.1;
  return Math.ceil(inp * IN_MILLS + (usage?.output_tokens || 0) * OUT_MILLS);
}

const goodB64 = s => typeof s === "string" && s.length > 0 && s.length <= MAX_B64 && /^[A-Za-z0-9+/=]+$/.test(s);
const clean = (s, n) => (typeof s === "string" ? s.slice(0, n) : "");
const jpeg = b64 => ({ type: "input_image", image_url: `data:image/jpeg;base64,${b64}`, detail: "low" });
const txt = text => ({ type: "input_text", text });

/** The user turn: each preview labelled with its id, local time and place, then the notes and the options. */
export function bookContent(body, pick = true) {
  const shots = new Map((body.shots || []).map(s => [s.id, s]));
  const content = [];
  for (const im of body.images) {
    const s = shots.get(im.id) || {};
    const video = s.video ? `영상 ${Math.round((s.durMs || 0) / 100) / 10}초` + (im.atMs != null ? `, 장면 ${im.atMs}ms` : "") : "";
    const label = [`[${clean(im.id, 64)}]`, clean(s.local, 20), clean(s.place, 40), video].filter(Boolean).join(" ");
    content.push(txt(label), jpeg(im.b64));
  }
  const notes = (body.notes || []).map(n => `${clean(n.day, 10) || "날짜 없음"}: ${clean(n.text, 12000)}`).join("\n\n");
  const o = body.opts || {};
  const must = (body.mustInclude || []).filter(x => typeof x === "string").slice(0, 10);
  if (pick && must.length) content.push(txt(`꼭 넣을 사진: ${must.map(x => clean(x, 64)).join(", ")}`));
  content.push(txt(
    `메모:\n${notes || "(없음)"}\n\n옵션: 말투=${clean(o.tone, 20) || "auto"}, 글의 양=${clean(o.textAmount, 20) || "caption_essay"}, ` +
    `사람 사진=${clean(o.people, 20) || "mix"}, 길이=${clean(o.length, 20) || "normal"}
` +
    `여행 성격: 누구랑=${clean(o.who, 40) || "없음"}, ${clean(o.about, 200) || "없음"} (같이 간 사람이 나온 사진을 아낀다)`));
  return content;
}

function rewriteContent(body, images) {
  const p = body.page || {};
  const notes = clean((body.notes || []).map(n => n.text).join(" "), 2000);
  return [...images.map(im => jpeg(im.b64)),
    txt(`시각·장소: ${clean(p.local, 20)} ${clean(p.place, 40)}\n지금 캡션: ${clean(p.caption, 60)}\n메모: ${notes}`)];
}

/** /layout user turn: labelled previews (with orientation), the trip concept, the notes, then the retry block if any. */
export function layoutContent(body) {
  const shots = new Map((body.shots || []).map(s => [s.id, s]));
  const content = [txt("## 이 여행\n사진(각 이미지 앞 줄에 id · 현지 시각 · 장소 · 방향 P=세로 L=가로 S=정사각에 가까움):")];
  for (const im of body.images) {
    const s = shots.get(im.id) || {};
    const ar = s.w > 0 && s.h > 0 ? s.w / s.h : null;
    const orient = ar ? `${ar < 0.95 ? "P" : ar > 1.05 ? "L" : "S"} ${ar.toFixed(2)}` : "";
    const video = s.video ? `영상 ${Math.round((s.durMs || 0) / 100) / 10}초` + (im.atMs != null ? `, 장면 ${im.atMs}ms` : "") : "";
    content.push(txt([`[${clean(im.id, 64)}]`, clean(s.local, 20), clean(s.place, 40), orient, video].filter(Boolean).join(" ")), jpeg(im.b64));
  }
  const o = body.opts || {};
  const notes = (body.notes || []).map(n => `${clean(n.day, 10) || "날짜 없음"}: ${clean(n.text, 12000)}`).join("\n\n");
  let tail = `여행한 사람이 적은 여행 성격: 누구랑=${clean(o.who, 40) || "없음"}, ${clean(o.about, 200) || "없음"}\n\n` +
    `여행한 사람의 메모:\n${notes || "(없음)"}\n\n답은 스키마 JSON 만.`;
  const fix = (Array.isArray(body.fix) ? body.fix : []).filter(x => typeof x === "string").slice(0, 60);
  if (fix.length || body.prev) {
    tail += "\n\n## 지난 답의 문제 (모두 고쳐서 책 전체를 다시 답해라)\n" + fix.map(e => `- ${clean(e, 400)}`).join("\n") +
      "\n\n지난 답:\n" + JSON.stringify(body.prev ?? null);
  }
  content.push(txt(tail));
  return content;
}

/** Text chars in a request (for the input reservation). */
const chars = body => JSON.stringify({ s: body.shots, n: body.notes, o: body.opts, p: body.page, m: body.mustInclude,
  c: body.catalog, f: body.fix, v: body.prev }).length;

export async function handle(req, env, ctx, counts) {
  const url = new URL(req.url);
  const route = ROUTES[url.pathname];
  if (req.method !== "POST" || !route) return json({ error: "not found" }, 404);
  if (!env.APP_TOKEN || req.headers.get("x-app-token") !== env.APP_TOKEN) return json({ error: "token" }, 401);
  if (env.LIMIT && !(await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" })).success) return json({ error: "slow" }, 429);
  let body;
  try { body = await req.json(); } catch { return json({ error: "body" }, 400); }
  const images = Array.isArray(body?.images) ? body.images : [];
  if (images.length > route.images) return json({ error: "too many images" }, 413);
  if (!images.length || !images.every(i => goodB64(i?.b64) && typeof i.id === "string")) return json({ error: "body" }, 400);
  const shape = SHAPES.includes(body.shape) ? body.shape : "short";
  const kind = url.pathname.slice(1);
  if (kind === "layout" && (!Object.hasOwn(THEMES, body.theme) || !Array.isArray(body.catalog) || !body.catalog.length ||
    body.catalog.length > MAX_CATALOG || JSON.stringify(body.prev ?? null).length > MAX_PREV)) return json({ error: "body" }, 400);
  if (!env.OPENAI_KEY) return json({ error: "off" }, 503);

  const now = kst(), kMonth = `m|${now.slice(0, 7)}`;
  const [dk, dvar, ddef] = route.day, kDay = `${dk}|${now.slice(0, 10)}`;
  const cap = num(env.MONTH_CENTS, 1000) * 10;
  if (!(await counts.take(kDay, num(env[dvar], ddef)))) return json({ error: "off" }, 503);
  const maxTokens = route.tokens(shape);
  const system = kind === "pick" ? SYSTEM_PICK : kind === "write" ? systemWrite(shape) : kind === "rewrite" ? systemRewrite(shape)
    : systemLayout(body.theme, body.catalog, body.rules, new Set(images.map(i => i.id)).size);
  const reserved = reserveMills(images.length, chars(body) + (kind === "layout" ? system.length : 0), maxTokens);
  if (cap <= 0 || !(await counts.add(kMonth, reserved, cap))) {
    await counts.give(kDay);
    return json({ error: "off" }, 503);
  }

  const schema = { pick: PICK_SCHEMA, write: WRITE_SCHEMA, rewrite: REWRITE_SCHEMA, layout: LAYOUT_SCHEMA }[kind];
  const content = kind === "rewrite" ? rewriteContent(body, images) : kind === "layout" ? layoutContent(body) : bookContent(body, kind === "pick");

  // Stream to the app: a space every 10 s keeps the connection open while the model thinks, then one JSON object.
  // Status is already 200 by then, so errors travel in the body: {error: "model" | "upstream"}.
  const enc = new TextEncoder();
  const { readable, writable } = new TransformStream();
  const w = writable.getWriter();
  const beat = setInterval(() => w.write(enc.encode(" ")).catch(() => {}), KEEPALIVE_MS);
  const work = (async () => {
    let out = { error: "model" }, spent = null;
    try {
      const r = await (env.fetchModel || fetch)(OPENAI_URL, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${env.OPENAI_KEY}` },
        body: JSON.stringify({
          model: env.MODEL || "gpt-5.4", max_output_tokens: maxTokens, reasoning: { effort: "medium" }, store: false,
          input: [{ role: "system", content: system }, { role: "user", content }],
          text: { format: { type: "json_schema", name: kind, schema, strict: true } },
        }),
      });
      if (!r.ok) {
        console.log("upstream", r.status);
        out = { error: "upstream" };
        spent = 0;   // rejected: not billed
      } else {
        const data = await r.json();
        spent = realMills(data.usage);
        console.log("model", data.status, data.usage?.input_tokens, data.usage?.output_tokens);   // counts only, never text
        const msg = (data.output || []).find(o => o.type === "message");
        const text = msg?.content?.find(c => c.type === "output_text")?.text;
        if (data.status === "completed" && text) {
          try { out = JSON.parse(text); } catch { out = { error: "model" }; }
        }
      }
    } catch {
      console.log("upstream network");
      out = { error: "upstream" };
      // cut off: whether it was billed is unknown, the reservation stays (over-counts, never under)
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
