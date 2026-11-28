// 「여행 책자」 AI relay. The app sends 512 px previews, local times, place names and the user's notes; the model picks
// photos and writes captions/essay. Keys stay here, a month money ceiling guards the bill.
// /made counts the books each phone has made (free books): the phone's ANDROID_ID, hashed, and book ids (D1 `made`).
// Stored: /layout runs as a queued job (the app may leave and come back): its request waits in KV up to 2 h (deleted when
// the job ends) and the plan text stays in D1 `jobs` up to 24 h. Nothing else is stored.
// Model: OpenAI Responses API (operator 11-25: use the GPT key already on hand); the MODEL var picks the model.
// Money unit = mills (1/10 cent, integers). Reserve the worst case before the call, settle the real cost after.
import { MemoryCounts, D1Counts } from "./counts.js";
import { handleMap } from "./map.js";
import { SYSTEM_PICK, SYSTEM_SCOUT, SCOUT_SCHEMA, systemWrite, systemRewrite, PICK_SCHEMA, WRITE_SCHEMA, REWRITE_SCHEMA,
  THEMES, systemLayout, LAYOUT_SCHEMA } from "./prompt.js";

export { MemoryCounts, D1Counts };

// per route: image cap, output budget (reasoning included), day counter key + var + default
const ROUTES = {
  // 11-28: a long book's pick (44 + reasons) ran out at 16000 -> {error: model}, shown as "offline"; low effort + more room
  "/pick": { images: 130, tokens: () => 32000, day: ["dp", "DAY_BOOKS", 20] },
  // the first, wide look (operator 11-27): up to 480 small previews per book in 5-6 parallel batches, a small model tags them
  "/scout": { images: 100, tokens: () => 9000, day: ["ds", "SCOUT_DAY", 200] },
  "/write": { images: 48, tokens: shape => (shape === "short" ? 8000 : 16000), day: ["dw", "WRITE_DAY", 60] },
  "/rewrite": { images: 4, tokens: () => 1000, day: ["dr", "REWRITE_DAY", 200] },
  // the whole-book page plan; shares the /write day counter (it replaces /write for book making)
  "/layout": { images: 72, tokens: () => 40000, day: ["dw", "WRITE_DAY", 60] },
};
const MAX_CATALOG = 80, MAX_PREV = 100_000;
const NAMES_MILLS = 2;   // /names: ~300 tokens in and out on the small model, rounded up (never settled)
const SYSTEM_NAMES = "여행 책 지도에 쓸 지명을 한국어로 바꾼다. 받은 순서대로 같은 개수의 이름을 낸다. " +
  "한국에서 흔히 쓰는 표기가 있으면 그것(예: 섬 이름은 '~ 섬'을 붙이지 않고 고유 이름만), 없으면 현지 발음대로 한글로 적는다. " +
  "이미 한국어면 그대로. 설명은 붙이지 않는다.";
const NAMES_SCHEMA = { type: "object", additionalProperties: false, required: ["ko"], properties: { ko: { type: "array", items: { type: "string" } } } };
const SHAPES = ["short", "diary", "mag"];
const MAX_B64 = 400_000;           // one 512 px JPEG is ~40-80 KB as base64
// gpt-5.4: $2.50 / M input = 0.0025 mills a token, $15 / M output (reasoning included) = 0.015 mills a token
const IN_MILLS = 0.0025, OUT_MILLS = 0.015;
// gpt-5.4-mini (assumed $0.75 / $4.50 per M, check against the OpenAI price page): /scout settles at these
const MINI_IN = 0.00075, MINI_OUT = 0.0045;
const IMG_TOKENS = 400;            // a 512 px preview at detail "low" is well under this; rounded up for the reservation
const KEEPALIVE_MS = 10_000;
const OPENAI_URL = "https://api.openai.com/v1/responses";
const JOB = /^[A-Za-z0-9_-]{23,64}$/;   // the app sends 128 random bits: the name is the only key to a result
const MADE_CAP = 200;
const DEVICE = /^[0-9a-f]{8,32}$/, BOOK = /^[A-Za-z0-9_-]{1,64}$/;   // ANDROID_ID is 16 hex
const JOB_MAX_MS = 30 * 60_000, JOB_KEEP_MS = 24 * 3600_000;

const num = (v, d) => (v === undefined || v === "" ? d : +v);   // "0" is a real limit (the off switch)
const kst = (ms = Date.now()) => new Date(ms + 9 * 3600_000).toISOString();
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });

export function reserveMills(images, chars, maxTokens) {
  return Math.ceil((images * IMG_TOKENS + Math.ceil(chars / 2) + 2500) * IN_MILLS + maxTokens * OUT_MILLS);
}
export function realMills(usage, mini = false) {
  const cached = usage?.input_tokens_details?.cached_tokens || 0;
  const inp = (usage?.input_tokens || 0) - cached + cached * 0.1;
  return Math.ceil(inp * (mini ? MINI_IN : IN_MILLS) + (usage?.output_tokens || 0) * (mini ? MINI_OUT : OUT_MILLS));
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
    // what the phone and the first look found: kind tag, people, how busy the scene was, which pose series
    const seen = [s.tag ? `꼬리표 ${clean(s.tag, 4)}` : "", s.people ? `사람 ${clean(String(s.people), 2)}` : "",
      s.busy ? `이 장면 ${clean(String(s.busy), 6)}장` : "", s.series ? `연속${clean(String(s.series), 4)}` : "",
      s.hl === 2 ? "주인공 장면" : ""].filter(Boolean).join(" · ");
    const label = [`[${clean(im.id, 64)}]`, clean(s.local, 20), clean(s.place, 40), video, seen].filter(Boolean).join(" ");
    content.push(txt(label), jpeg(im.b64));
  }
  const notes = (body.notes || []).map(n => `${clean(n.day, 10) || "날짜 없음"}: ${clean(n.text, 12000)}`).join("\n\n");
  const o = body.opts || {};
  const must = (body.mustInclude || []).filter(x => typeof x === "string").slice(0, 10);
  if (pick && must.length) content.push(txt(`꼭 넣을 사진: ${must.map(x => clean(x, 64)).join(", ")}`));
  if (pick && Number.isInteger(body.target) && body.target > 0) content.push(txt(`책에 넣을 사진 수: 약 ${Math.min(body.target, 60)}장 (영상 포함)`));
  // the first look's "main scenes": what it saw (a phrase per batch, with how many) and the share the phone allows
  const hl = body.highlights;
  if (pick && hl && Array.isArray(hl.lines) && hl.lines.length && Number.isInteger(hl.n) && Number.isInteger(hl.m) &&
    hl.n > 0 && hl.m > 0 && hl.n <= hl.m) {
    const lines = hl.lines.slice(0, 12).map(l => `"${clean(l?.top, 24)}" ${Math.max(0, Math.min(999, Math.floor(+l?.n) || 0))}장`).join(" · ");
    content.push(txt(`이 여행의 주인공 장면(먼저 훑어본 결과): ${lines}. 책에 주인공 장면 약 ${Math.min(hl.n, 60)}장, 많아도 ${Math.min(hl.m, 60)}장.`));
  }
  content.push(txt(
    `메모:\n${notes || "(없음)"}\n\n옵션: 말투=${clean(o.tone, 20) || "auto"}, 글의 양=${clean(o.textAmount, 20) || "caption_essay"}, ` +
    `사람 사진=${clean(o.people, 20) || "mix"}, 길이=${clean(o.length, 20) || "normal"}
` +
    `여행 성격: 누구랑=${clean(o.who, 40) || "없음"}, ${clean(o.about, 200) || "없음"} (같이 간 사람이 나온 사진을 아낀다)`));
  return content;
}

/** /scout user turn: each small preview after "[n] 시각 · 장소 · 장면 k" (n = its place in this batch), then the trip's shape. */
export function scoutContent(body) {
  const shots = new Map((body.shots || []).map(s => [s.id, s]));
  const content = [];
  for (const [n, im] of body.images.entries()) {
    const s = shots.get(im.id) || {};
    content.push(txt([`[${n}]`, clean(s.local, 20), clean(s.place, 40), s.scene != null ? `장면 ${clean(String(s.scene), 6)}` : "",
      s.video ? `영상 ${Math.round((s.durMs || 0) / 1000)}초` : ""].filter(Boolean).join(" ")), jpeg(im.b64));
  }
  const o = body.opts || {};
  content.push(txt(`여행 성격: 누구랑=${clean(o.who, 40) || "없음"}, ${clean(o.about, 200) || "없음"}. 답은 스키마 JSON 만.`));
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
    const tag = s.kind ? `${clean(s.kind, 4)}: ${clean(s.what, 30)}` : "";
    const group = s.group ? `묶음 ${clean(s.group, 8)}` : "";
    content.push(txt([`[${clean(im.id, 64)}]`, clean(s.local, 20), clean(s.place, 40), orient, video, tag, group].filter(Boolean).join(" ")), jpeg(im.b64));
  }
  const flow = outline(body.scenes, shots);
  if (flow) content.push(txt(flow));
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

const getJob = (db, job) => db.prepare("SELECT * FROM jobs WHERE job = ?1").bind(job).first();

/** Where a /layout job stands: the queue consumer writes the result; until then it is pending. */
export async function jobState(env, counts, row) {
  if (!row) return { error: "none" };
  return row.status === "queued" ? { status: "pending" } : JSON.parse(row.body);
}

/** /layout on a job that already exists: the /result answer, but a pending one keeps the {job, status} shape. */
async function layoutState(env, counts, row) {
  const s = await jobState(env, counts, row);
  return s.status === "pending" ? { job: row.job, status: "pending" } : s;
}

/** Record a job's end once (the status='queued' guard) and settle its money: real usage, or keep the reservation. */
async function finishJob(env, counts, row, out, usage) {
  const status = out && !out.error ? "done" : "failed";
  const won = await env.DB.prepare("UPDATE jobs SET status = ?2, body = ?3 WHERE job = ?1 AND status = 'queued' RETURNING job")
    .bind(row.job, status, JSON.stringify(out || { error: "model" })).first();
  if (won && usage) await counts.settle(row.month, realMills(usage) - row.reserved);
}

/** Queue consumer: one /layout job, a normal (fast, not background) model call; up to 15 min of wall time here. */
export async function runJob(env, counts, job) {
  const row = await getJob(env.DB, job);
  if (!row || row.status !== "queued") return;
  const payload = await env.LAYOUT_IN.get(job, "json");
  if (!payload) return finishJob(env, counts, row, { error: "none" }, null);
  let out = { error: "model" }, usage = null;
  try {
    const r = await (env.fetchModel || fetch)(OPENAI_URL, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${env.OPENAI_KEY}` },
      body: JSON.stringify(payload),
    });
    if (!r.ok) { console.log("upstream", r.status); out = { error: "upstream" }; usage = { input_tokens: 0, output_tokens: 0 }; }
    else {
      const data = await r.json();
      usage = data.usage || null;
      console.log("model", data.status, data.usage?.input_tokens, data.usage?.output_tokens);   // counts only, never text
      const text = (data.output || []).find(o => o.type === "message")?.content?.find(c => c.type === "output_text")?.text;
      if (data.status === "completed" && text) { try { out = JSON.parse(text); } catch { out = { error: "model" }; } }
    }
  } catch { console.log("upstream network"); out = { error: "upstream" }; }   // billing unknown: the reservation stays
  await finishJob(env, counts, row, out, usage);
  await env.LAYOUT_IN.delete(job);
}

/** Cron: drop rows older than a day, time out jobs stuck over 30 min (reservation kept). */
export async function cron(env, counts, now = Date.now()) {
  await env.DB.prepare("DELETE FROM jobs WHERE created < ?1").bind(now - JOB_KEEP_MS).run();
  await env.DB.prepare("UPDATE jobs SET status = 'failed', body = ?2 WHERE status = 'queued' AND created < ?1")
    .bind(now - JOB_MAX_MS, JSON.stringify({ error: "timeout" })).run();
}

/** /layout: claim the job row, park the request (KV, 2 h) and queue it; answer at once. The app polls /result. */
async function startLayout(env, counts, job, payload, kMonth, kDay, reserved) {
  const refund = async () => { await counts.settle(kMonth, -reserved); await counts.give(kDay); };
  const claimed = await env.DB.prepare("INSERT INTO jobs (job, month, reserved, status, created) VALUES (?1, ?2, ?3, 'queued', ?4) " +
    "ON CONFLICT(job) DO NOTHING RETURNING job").bind(job, kMonth, reserved, Date.now()).first();
  if (!claimed) { await refund(); return layoutState(env, counts, await getJob(env.DB, job)); }   // a twin request won the race
  try {
    await env.LAYOUT_IN.put(job, JSON.stringify(payload), { expirationTtl: 7200 });
    await env.JOBQ.send({ job });
  } catch {
    console.log("queue");
    await refund();
    await env.DB.prepare("DELETE FROM jobs WHERE job = ?1").bind(job).run();
    return { error: "upstream" };
  }
  return { job, status: "pending" };
}

/** The trip as the app grouped it (day -> scene by time gap/place): the editor keeps this order. */
export function outline(days, shots) {
  if (!Array.isArray(days) || !days.length) return "";
  const lines = ["## 여행의 흐름 (사진의 시각·장소로 앱이 나눈 것 — 날과 장면의 순서를 그대로 지킨다)"];
  for (const d of days.slice(0, 31)) {
    lines.push("", clean(d.label, 40));
    for (const [i, sc] of (Array.isArray(d.scenes) ? d.scenes : []).slice(0, 40).entries()) {
      const ids = (Array.isArray(sc.ids) ? sc.ids : []).slice(0, 40).map(id => {
        const s = shots.get(id) || {};
        return `${clean(id, 64)}(${clean(s.kind, 4) || "?"}: ${clean(s.what, 30)})`;
      });
      lines.push(`  장면 ${i + 1} · ${clean(sc.from, 5)}–${clean(sc.to, 5)} · ${clean(sc.place, 40) || "장소 모름"}: ${ids.join(", ")}`);
    }
  }
  return lines.join("\n");
}

/** Text chars in a request (for the input reservation). */
const chars = body => JSON.stringify({ s: body.shots, n: body.notes, o: body.opts, p: body.page, m: body.mustInclude,
  c: body.catalog, f: body.fix, v: body.prev, sc: body.scenes }).length;

export async function handle(req, env, ctx, counts) {
  const url = new URL(req.url);
  const route = ROUTES[url.pathname], result = url.pathname === "/result", made = url.pathname === "/made", map = url.pathname === "/map", names = url.pathname === "/names";
  if (req.method !== "POST" || !(route || result || made || map || names)) return json({ error: "not found" }, 404);
  if (!env.APP_TOKEN || req.headers.get("x-app-token") !== env.APP_TOKEN) return json({ error: "token" }, 401);
  if (map) {   // 다닌 길: OpenStreetMap shapes for a box, no model, no money (map.js); 1-4 calls a book
    if (env.SCOUT_LIMIT && !(await env.SCOUT_LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" })).success) return json({ error: "slow" }, 429);
    let b;
    try { b = await req.json(); } catch { return json({ error: "body" }, 400); }
    const r = await handleMap(b, ctx);
    return json(r.body, r.status);
  }
  if (names) {   // 다닌 길: the map's place names in Korean (public names only, ≤ 12 a book, small model)
    if (env.SCOUT_LIMIT && !(await env.SCOUT_LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" })).success) return json({ error: "slow" }, 429);
    let b;
    try { b = await req.json(); } catch { return json({ error: "body" }, 400); }
    const list = b?.names;
    if (!Array.isArray(list) || !list.length || list.length > 12 || !list.every(n => typeof n === "string" && n.length > 0 && n.length <= 60)) return json({ error: "body" }, 400);
    if (!env.OPENAI_KEY) return json({ error: "off" }, 503);
    const cap = num(env.MONTH_CENTS, 1000) * 10;
    if (cap <= 0 || !(await counts.add(`m|${kst().slice(0, 7)}`, NAMES_MILLS, cap))) return json({ error: "off" }, 503);
    try {
      const r = await (env.fetchModel || fetch)(OPENAI_URL, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${env.OPENAI_KEY}` },
        body: JSON.stringify({ model: env.SCOUT_MODEL || "gpt-5.4-mini", max_output_tokens: 1500, reasoning: { effort: "low" }, store: false,
          input: [{ role: "system", content: SYSTEM_NAMES }, { role: "user", content: JSON.stringify(list) }],
          text: { format: { type: "json_schema", name: "names", schema: NAMES_SCHEMA, strict: true } } }),
      });
      const out = JSON.parse((await r.json()).output?.find(o => o.type === "message")?.content?.[0]?.text ?? "{}");
      const ko = Array.isArray(out.ko) && out.ko.length === list.length && out.ko.every(k => typeof k === "string" && k.length <= 30) ? out.ko : null;
      return json(ko ? { ko } : { error: "model" });
    } catch {
      return json({ error: "model" });
    }
  }
  if (made) {   // the app's written books (idempotent) in, how many this phone has ever made out
    let b;
    try { b = await req.json(); } catch { return json({ error: "body" }, 400); }
    const books = Array.isArray(b?.books) ? b.books : null;
    if (!DEVICE.test(b?.device) || !books || books.length > 50 || !books.every(x => typeof x === "string" && BOOK.test(x))) return json({ error: "body" }, 400);
    return json({ made: await madeCount(env.DB, b.device, books) });
  }
  if (result) {   // polled every few seconds: no IP limiter, no counters
    let b;
    try { b = await req.json(); } catch { return json({ error: "body" }, 400); }
    if (!JOB.test(b?.job)) return json({ error: "body" }, 400);
    return json(await jobState(env, counts, await getJob(env.DB, b.job)));
  }
  // /scout comes in parallel batches: a limiter of its own, so a book's look never eats the 4-a-minute of the rest
  const limiter = url.pathname === "/scout" ? env.SCOUT_LIMIT : env.LIMIT;
  if (limiter && !(await limiter.limit({ key: req.headers.get("cf-connecting-ip") || "?" })).success) return json({ error: "slow" }, 429);
  let body;
  try { body = await req.json(); } catch { return json({ error: "body" }, 400); }
  const images = Array.isArray(body?.images) ? body.images : [];
  if (images.length > route.images) return json({ error: "too many images" }, 413);
  if (!images.length || !images.every(i => goodB64(i?.b64) && typeof i.id === "string")) return json({ error: "body" }, 400);
  const shape = SHAPES.includes(body.shape) ? body.shape : "short";
  const kind = url.pathname.slice(1);
  // a request without a job (app builds before 11-27) still gets the old streamed answer
  const asJob = kind === "layout" && body.job !== undefined;
  if (kind === "layout" && ((asJob && !JOB.test(body.job)) || !Object.hasOwn(THEMES, body.theme) || !Array.isArray(body.catalog) || !body.catalog.length ||
    body.catalog.length > MAX_CATALOG || JSON.stringify(body.prev ?? null).length > MAX_PREV)) return json({ error: "body" }, 400);
  if (!env.OPENAI_KEY) return json({ error: "off" }, 503);
  if (asJob) {   // idempotent: the same job never calls the model twice
    const row = await getJob(env.DB, body.job);
    if (row) return json(await layoutState(env, counts, row));
  }

  const now = kst(), kMonth = `m|${now.slice(0, 7)}`;
  const [dk, dvar, ddef] = route.day, kDay = `${dk}|${now.slice(0, 10)}`;
  const cap = num(env.MONTH_CENTS, 1000) * 10;
  if (!(await counts.take(kDay, num(env[dvar], ddef)))) return json({ error: "off" }, 503);
  const maxTokens = route.tokens(shape);
  const system = kind === "pick" ? SYSTEM_PICK : kind === "scout" ? SYSTEM_SCOUT : kind === "write" ? systemWrite(shape) : kind === "rewrite" ? systemRewrite(shape)
    : systemLayout(body.theme, body.catalog, body.rules, new Set(images.map(i => i.id)).size);
  const reserved = reserveMills(images.length, chars(body) + (kind === "layout" ? system.length : 0), maxTokens);
  if (cap <= 0 || !(await counts.add(kMonth, reserved, cap))) {
    await counts.give(kDay);
    return json({ error: "off" }, 503);
  }

  const schema = { pick: PICK_SCHEMA, scout: SCOUT_SCHEMA, write: WRITE_SCHEMA, rewrite: REWRITE_SCHEMA, layout: LAYOUT_SCHEMA }[kind];
  const content = kind === "rewrite" ? rewriteContent(body, images) : kind === "layout" ? layoutContent(body)
    : kind === "scout" ? scoutContent(body) : bookContent(body, kind === "pick");
  // /layout's model and effort can differ (LAYOUT_MODEL / LAYOUT_EFFORT); with ALLOW_TEST=1 a request may ask for others (model comparison)
  // the override needs the operator's TEST_KEY secret (the app token ships inside the APK) and an allowed model
  const test = kind === "layout" && env.ALLOW_TEST === "1" && env.TEST_KEY && req.headers.get("x-test-key") === env.TEST_KEY &&
    body.test && typeof body.test === "object" ? body.test : {};
  const TEST_MODELS = ["gpt-5.4", "gpt-5.4-mini", "gpt-5.5"];
  const model = kind === "layout" ? (TEST_MODELS.includes(test.model) ? test.model : env.LAYOUT_MODEL || env.MODEL || "gpt-5.4")
    : kind === "scout" ? env.SCOUT_MODEL || "gpt-5.4-mini" : env.MODEL || "gpt-5.4";
  const effort = kind === "layout" ? (["low", "medium", "high"].includes(test.effort) ? test.effort : env.LAYOUT_EFFORT || "medium")
    : kind === "scout" || kind === "pick" ? "low" : "medium";
  const mini = model.endsWith("-mini");
  const payload = {
    model, max_output_tokens: maxTokens, reasoning: { effort }, store: false,
    input: [{ role: "system", content: system }, { role: "user", content }],
    text: { format: { type: "json_schema", name: kind, schema, strict: true } },
  };
  if (asJob) return json(await startLayout(env, counts, body.job, payload, kMonth, kDay, reserved));

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
        body: JSON.stringify(payload),
      });
      if (!r.ok) {
        console.log("upstream", r.status);
        out = { error: "upstream" };
        spent = 0;   // rejected: not billed
      } else {
        const data = await r.json();
        spent = realMills(data.usage, mini);
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

/** Records [books] for the phone (already-known ones change nothing) and returns how many it has made in all. */
export async function madeCount(db, device, books) {
  const dev = [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`tripbook|${device}`)))]
    .map(x => x.toString(16).padStart(2, "0")).join("");
  const count = async () => (await db.prepare("SELECT COUNT(*) AS n FROM made WHERE dev = ?1").bind(dev).first()).n;
  // ponytail: rows per phone capped at MADE_CAP (a person makes a handful of books); a flood of made-up device ids
  // still adds rows (the app token ships in the APK) — add an IP limiter of its own if the table ever grows oddly
  if (await count() >= MADE_CAP) return MADE_CAP;
  for (const book of books) {
    await db.prepare("INSERT OR IGNORE INTO made (dev, book, created) VALUES (?1, ?2, ?3)").bind(dev, book, Date.now()).run();
  }
  return count();
}

export default {
  async fetch(req, env, ctx) { return handle(req, env, ctx, new D1Counts(env.DB)); },
  async scheduled(event, env, ctx) { ctx.waitUntil(cron(env, new D1Counts(env.DB))); },
  async queue(batch, env) {
    for (const m of batch.messages) { await runJob(env, new D1Counts(env.DB), m.body.job); m.ack(); }
  },
};
