import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { handle, cron, runJob, MemoryCounts, D1Counts, reserveMills, realMills } from "../src/index.js";

const B64 = "/9j/" + "A".repeat(300);
let counts, env, calls, logs, waits, db;

// D1 over node:sqlite, built from schema.sql
function fakeD1() {
  db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  return { prepare(sql) { const st = db.prepare(sql); return { bind(...a) {
    return { first: async () => st.get(...a) ?? null, run: async () => st.run(...a), all: async () => ({ results: st.all(...a) }) }; } }; } };
}

// KV and Queue stand-ins
let kv, queued;
function fakeKV() { kv = new Map(); return { put: async (k, v) => kv.set(k, v), get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null), delete: async k => kv.delete(k) }; }
function fakeQ() { queued = []; return { send: async m => queued.push(m) }; }

// a fake OpenAI fetch: `reply` (Responses API body), an HTTP error `status`, or a network `fail`
function fakeFetch({ reply, status, fail } = {}) {
  return async (url, init) => {
    calls.push(JSON.parse(init.body));
    if (fail) throw fail;
    if (status) return new Response("{}", { status });
    return new Response(JSON.stringify(reply), { status: 200 });
  };
}
const ok = (obj, usage = { input_tokens: 20000, output_tokens: 4000 }, st = "completed") =>
  ({ id: "resp_1", status: st, usage, output: [{ type: "reasoning" }, { type: "message", content: [{ type: "output_text", text: JSON.stringify(obj) }] }] });

beforeEach(() => {
  counts = new MemoryCounts(); calls = []; logs = []; waits = [];
  env = { APP_TOKEN: "tok", OPENAI_KEY: "k", MONTH_CENTS: "1000", DAY_BOOKS: "20",
    fetchModel: fakeFetch({ reply: ok({ picks: [] }) }), DB: fakeD1(), LAYOUT_IN: fakeKV(), JOBQ: fakeQ() };
  console.log = (...a) => logs.push(a.join(" "));
});

async function call(body, { path = "/pick", token = "tok" } = {}) {
  const req = new Request("https://w" + path, { method: "POST", headers: { "x-app-token": token }, body: JSON.stringify(body) });
  const r = await handle(req, env, { waitUntil: p => waits.push(p) }, counts);
  const text = await r.text();
  await Promise.all(waits);
  return { status: r.status, body: JSON.parse(text.trim() || "null") };
}
const imgs = n => Array.from({ length: n }, (_, i) => ({ id: "p" + i, b64: B64 }));
const month = () => [...counts.m.entries()].find(([k]) => k.startsWith("m|"))?.[1] || 0;

test("wrong token is 401 and costs nothing", async () => {
  const r = await call({ images: imgs(3) }, { token: "nope" });
  assert.equal(r.status, 401);
  assert.equal(calls.length, 0);
});

test("131 images is 413", async () => {
  assert.equal((await call({ images: imgs(131) })).status, 413);
  assert.equal(calls.length, 0);
});

test("month ceiling reached: 503 off and the model is never called", async () => {
  env.MONTH_CENTS = "1";   // 10 mills, below one reservation
  const r = await call({ images: imgs(5) });
  assert.equal(r.status, 503);
  assert.deepEqual(r.body, { error: "off" });
  assert.equal(calls.length, 0);
  assert.equal(month(), 0);
});

test("day books cap refuses the 21st book", async () => {
  env.DAY_BOOKS = "1";
  assert.equal((await call({ images: imgs(2) })).status, 200);
  assert.equal((await call({ images: imgs(2) })).status, 503);
});

test("a good answer: JSON from the text block, the month records the real cost", async () => {
  env.fetchModel = fakeFetch({ reply: ok({ titles: ["제주"] }, { input_tokens: 20000, output_tokens: 4000 }) });
  const r = await call({ images: imgs(40), shots: [{ id: "p0", local: "2026-06-12T13:20", place: "협재" }] });
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { titles: ["제주"] });
  assert.equal(month(), realMills({ input_tokens: 20000, output_tokens: 4000 }));   // 40 + 40 = 80 mills
  assert.equal(calls[0].text.format.type, "json_schema");
  assert.equal(calls[0].input[1].content[1].detail, "low");
});

test("real cost above the reservation is still recorded past the ceiling", async () => {
  const reserve = reserveMills(2, 50, 32000);
  env.MONTH_CENTS = String(Math.ceil(reserve / 10));   // room for one reservation only
  env.fetchModel = fakeFetch({ reply: ok({}, { input_tokens: 10, output_tokens: 60000 }) });   // 900 mills > reserve
  await call({ images: imgs(2) });
  assert.equal(month(), realMills({ input_tokens: 10, output_tokens: 60000 }));
  assert.ok(month() > Number(env.MONTH_CENTS) * 10);
});

test("incomplete (output limit): 200 with {error:model} in the body, cost recorded", async () => {
  env.fetchModel = fakeFetch({ reply: ok({}, { input_tokens: 1000, output_tokens: 12000 }, "incomplete") });
  const r = await call({ images: imgs(2) });
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { error: "model" });
  assert.equal(month(), realMills({ input_tokens: 1000, output_tokens: 12000 }));
});

test("rejected by the model API (HTTP error): upstream error, the reservation is refunded", async () => {
  env.fetchModel = fakeFetch({ status: 429 });
  const r = await call({ images: imgs(2) });
  assert.deepEqual(r.body, { error: "upstream" });
  assert.equal(month(), 0);
});

test("network cut: billing unknown, the reservation stays", async () => {
  env.fetchModel = fakeFetch({ fail: new Error("socket") });
  await call({ images: imgs(2) });
  assert.equal(month(), reserveMills(2, JSON.stringify({}).length, 32000));
});

test("logs carry counts only, never captions or notes", async () => {
  env.fetchModel = fakeFetch({ reply: ok({ essay: "비밀 일기 내용" }) });
  await call({ images: imgs(2), notes: [{ day: "2026-06-12", text: "비밀 메모" }] });
  assert.ok(logs.every(l => !l.includes("비밀")));
});

test("D1 settle binds only finite numbers and records past the limit", async () => {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  const binds = [];
  const d1 = { prepare(sql) { const st = db.prepare(sql); return { bind(...a) { binds.push(...a);
    return { first: async () => st.get(...a), run: async () => st.run(...a) }; } }; } };
  const c = new D1Counts(d1);
  assert.equal(await c.add("m|x", 50, 60), 50);
  assert.equal(await c.add("m|x", 20, 60), 0);   // over the ceiling: refused
  await c.settle("m|x", 30);                       // the real cost: recorded anyway
  assert.equal(db.prepare("SELECT n FROM counts WHERE k='m|x'").get().n, 80);
  assert.ok(binds.every(v => typeof v !== "number" || Number.isFinite(v)));
});

test("/write: 49 images is 413, the shape picks the output budget", async () => {
  assert.equal((await call({ images: imgs(49), shape: "short" }, { path: "/write" })).status, 413);
  await call({ images: imgs(10), shape: "diary" }, { path: "/write" });
  await call({ images: imgs(10), shape: "short" }, { path: "/write" });
  assert.equal(calls[0].max_output_tokens, 16000);
  assert.equal(calls[1].max_output_tokens, 8000);
  assert.equal(calls[0].text.format.name, "write");
  assert.ok(calls[0].input[0].content.includes("두세 줄"));   // diary rules in the system prompt
});

test("/write has its own day cap, separate from books", async () => {
  env.WRITE_DAY = "1";
  assert.equal((await call({ images: imgs(2) }, { path: "/write" })).status, 200);
  assert.equal((await call({ images: imgs(2) }, { path: "/write" })).status, 503);
  assert.equal((await call({ images: imgs(2) })).status, 200);   // /pick still open
});

test("/pick passes must-include ids to the model, never more than 10", async () => {
  await call({ images: imgs(3), mustInclude: Array.from({ length: 12 }, (_, i) => "p" + i) });
  const texts = calls[0].input[1].content.filter(c => c.type === "input_text").map(c => c.text).join("\n");
  const line = texts.split("\n").find(l => l.startsWith("꼭 넣을 사진"));
  assert.equal(line.split(",").length, 10);
});

test("unknown route is 404", async () => {
  assert.equal((await call({ images: imgs(1) }, { path: "/book" })).status, 404);
});

// /layout: whole-book page plan over the theme's designer layouts
const CATALOG = [
  { id: "a/cover", kind: "cover", slots: ["P"], texts: { title: 12, period: 20 }, when: "표지" },
  { id: "a/pair", kind: "photo", slots: ["L", "V"], texts: { cap: 24 }, when: "짝 두 장" },
];
const layoutBody = extra => ({ job: "jAAAABBBBCCCCDDDDEEEEFF1", images: imgs(2), theme: "a", catalog: CATALOG, rules: { pages: [16, 26], min_distinct: 5 },
  shots: [{ id: "p0", local: "2026-06-12T13:20", place: "협재", w: 3000, h: 4000 },
    { id: "p1", local: "2026-06-12T14:00", place: "우도", video: true, durMs: 8400, w: 1920, h: 1080 }],
  opts: { who: "연인", about: "느긋하게 걷기" }, notes: [{ day: "2026-06-12", text: "바람이 셌다" }], ...extra });
const allText = c => [c.input[0].content, ...c.input[1].content.filter(x => x.type === "input_text").map(x => x.text)].join("\n");

test("/layout prompt: theme guide, catalog line, concept line, labelled images, layout schema", async () => {
  const r = await call(layoutBody(), { path: "/layout" });
  assert.equal(r.status, 200);
  await runJob(env, counts, "jAAAABBBBCCCCDDDDEEEEFF1");   // the queue consumer makes the call
  const c = calls[0], t = allText(c);
  assert.ok(t.includes("여백의 책"));                                    // a.md
  assert.ok(t.includes("글에서 '메모'"));                                 // _common.md
  assert.ok(t.includes("- a/pair [photo] 칸 L V · 글 cap≤24자 — 짝 두 장"));
  assert.ok(t.includes("V=영상(움직이는 장면)"));
  assert.ok(t.includes("쪽 수 16~26") && t.includes("본문 틀 최소 5가지"));
  assert.ok(t.includes("여행한 사람이 적은 여행 성격: 누구랑=연인, 느긋하게 걷기"));
  assert.ok(t.includes("[p0] 2026-06-12T13:20 협재 P 0.75"));
  assert.ok(t.includes("[p1] 2026-06-12T14:00 우도 L 1.78 영상 8.4초"));
  assert.ok(!t.includes("지난 답의 문제"));
  assert.equal(c.text.format.name, "layout");
  assert.deepEqual(c.text.format.schema.required, ["title", "plan", "pages"]);
  assert.equal(c.max_output_tokens, 40000);
});

test("/layout: concept defaults to 없음, retry block carries fix list and the previous answer", async () => {
  await call(layoutBody({ opts: {}, fix: ["2쪽: 없는 틀 x"], prev: { title: "제주", pages: [] } }), { path: "/layout" });
  await runJob(env, counts, "jAAAABBBBCCCCDDDDEEEEFF1");
  const t = allText(calls[0]);
  assert.ok(t.includes("누구랑=없음, 없음"));
  assert.ok(t.includes("## 지난 답의 문제 (모두 고쳐서 책 전체를 다시 답해라)\n- 2쪽: 없는 틀 x\n\n지난 답:\n{\"title\":\"제주\",\"pages\":[]}"));
});

test("/layout: unknown theme 400, 73 images 413, bad token 401, none call the model", async () => {
  assert.equal((await call(layoutBody({ theme: "zz" }), { path: "/layout" })).status, 400);
  assert.equal((await call(layoutBody({ theme: "toString" }), { path: "/layout" })).status, 400);
  assert.equal((await call(layoutBody({ images: imgs(73) }), { path: "/layout" })).status, 413);
  assert.equal((await call(layoutBody(), { path: "/layout", token: "nope" })).status, 401);
  assert.equal(calls.length, 0);
});

test("/layout shares the /write day counter", async () => {
  env.WRITE_DAY = "1";
  assert.equal((await call(layoutBody(), { path: "/layout" })).status, 200);
  assert.equal((await call({ images: imgs(2) }, { path: "/write" })).status, 503);
});

// /layout queued jobs + /result polling
const PLAN = { title: "제주", plan: "p", pages: [] };
const USAGE = { input_tokens: 30000, output_tokens: 9000 };
const result = (job = "jAAAABBBBCCCCDDDDEEEEFF1", token) => call({ job }, { path: "/result", token });
const work = () => runJob(env, counts, "jAAAABBBBCCCCDDDDEEEEFF1");

test("/layout: queued at once (KV + queue, no model call yet); the same job never queues twice", async () => {
  const r = await call(layoutBody(), { path: "/layout" });
  assert.deepEqual(r.body, { job: "jAAAABBBBCCCCDDDDEEEEFF1", status: "pending" });
  assert.equal(calls.length, 0);
  assert.deepEqual(queued, [{ job: "jAAAABBBBCCCCDDDDEEEEFF1" }]);
  assert.ok(kv.has("jAAAABBBBCCCCDDDDEEEEFF1"));
  assert.ok(month() > 0);   // reserved
  assert.deepEqual((await call(layoutBody(), { path: "/layout" })).body, { job: "jAAAABBBBCCCCDDDDEEEEFF1", status: "pending" });
  assert.equal(queued.length, 1);
  assert.equal(db.prepare("SELECT count(*) c FROM jobs").get().c, 1);
});

test("consumer: one normal model call, plan stored, money settled once, request deleted", async () => {
  env.fetchModel = fakeFetch({ reply: ok(PLAN, USAGE) });
  await call(layoutBody(), { path: "/layout" });
  assert.deepEqual((await result()).body, { status: "pending" });
  await work();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].background, undefined);
  assert.deepEqual((await result()).body, PLAN);
  assert.equal(month(), realMills(USAGE));
  assert.equal(kv.has("jAAAABBBBCCCCDDDDEEEEFF1"), false);
  await work();   // a redelivered message does nothing
  assert.equal(calls.length, 1);
  assert.deepEqual((await call(layoutBody(), { path: "/layout" })).body, PLAN);
});

test("consumer: model error -> {error}; upstream HTTP error refunds; unknown job -> none", async () => {
  env.fetchModel = fakeFetch({ reply: ok({}, { input_tokens: 1000, output_tokens: 0 }, "incomplete") });
  await call(layoutBody(), { path: "/layout" });
  await work();
  assert.deepEqual((await result()).body, { error: "model" });
  assert.equal(month(), realMills({ input_tokens: 1000, output_tokens: 0 }));
  assert.deepEqual((await result("jZZZZYYYYXXXXWWWWVVVVUU1")).body, { error: "none" });
  assert.equal((await result("bad")).status, 400);
});

test("/result skips the IP limiter; a bad token is 401", async () => {
  let limited = 0;
  env.LIMIT = { limit: async () => { limited++; return { success: false }; } };
  assert.deepEqual((await result()).body, { error: "none" });
  assert.equal(limited, 0);
  assert.equal((await result("jAAAABBBBCCCCDDDDEEEEFF1", "nope")).status, 401);
});

test("cron: times out a job stuck 30 min (reservation kept), deletes day-old rows", async () => {
  const now = Date.now();
  const ins = db.prepare("INSERT INTO jobs (job, resp, month, reserved, status, body, created) VALUES (?, ?, ?, ?, ?, ?, ?)");
  ins.run("jOOOOLLLLDDDDQQQQUUUUEE1", null, "m|x", 50, "queued", null, now - 31 * 60_000);
  ins.run("jDDDDAAAAYYYYOOOOLLLLDD1", null, "m|x", 50, "done", "{}", now - 25 * 3600_000);
  await cron(env, counts, now);
  assert.deepEqual((await result("jOOOOLLLLDDDDQQQQUUUUEE1")).body, { error: "timeout" });
  assert.deepEqual((await result("jDDDDAAAAYYYYOOOOLLLLDD1")).body, { error: "none" });
});

test("a request without a job still gets the old streamed answer", async () => {
  env.fetchModel = fakeFetch({ reply: ok(PLAN, USAGE) });
  const b = layoutBody(); delete b.job;
  assert.deepEqual((await call(b, { path: "/layout" })).body, PLAN);
});

test("/made: counts each book once per phone, survives the app forgetting its books, and keeps phones apart", async () => {
  const made = (device, books) => handle(new Request("https://w/made", { method: "POST", headers: { "x-app-token": "tok" },
    body: JSON.stringify({ device, books }) }), env, {}, counts).then(r => r.json());
  assert.deepEqual(await made("a1b2c3d4e5f60718", ["b1"]), { made: 1 });
  assert.deepEqual(await made("a1b2c3d4e5f60718", ["b1", "b2"]), { made: 2 });
  assert.deepEqual(await made("a1b2c3d4e5f60718", []), { made: 2 }, "reinstalled: the phone sends no books, the count stays");
  assert.deepEqual(await made("ffff0000ffff0000", ["b1"]), { made: 1 });
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM made WHERE dev LIKE '%a1b2%'").get().n, 0, "the raw id is never stored");
  for (const bad of [{ device: "../x", books: [] }, { device: "a1b2c3d4", books: ["a b"] }, { device: "a1b2c3d4" }]) {
    const r = await handle(new Request("https://w/made", { method: "POST", headers: { "x-app-token": "tok" }, body: JSON.stringify(bad) }), env, {}, counts);
    assert.equal(r.status, 400);
  }
});

test("/scout: the small model at low effort, numbered previews, its own limiter, mini prices", async () => {
  let main = 0, scout = 0;
  env.LIMIT = { limit: async () => { main++; return { success: false }; } };
  env.SCOUT_LIMIT = { limit: async () => { scout++; return { success: true }; } };
  const usage = { input_tokens: 6000, output_tokens: 3000 };
  env.fetchModel = fakeFetch({ reply: ok({ tags: [{ n: 0, k: "음식", p: 0, s: 4, e: false }] }, usage) });
  const r = await call({ images: imgs(3), shots: [{ id: "i0", local: "2026-06-12T12:00", place: "제주", scene: 2 }] }, { path: "/scout" });
  assert.deepEqual(r.body.tags[0].k, "음식");
  assert.equal(main, 0);
  assert.equal(scout, 1);
  assert.equal(calls[0].model, "gpt-5.4-mini");
  assert.equal(calls[0].reasoning.effort, "low");
  const text = calls[0].input[1].content.filter(c => c.type === "input_text").map(c => c.text);
  assert.ok(text[0].startsWith("[0]"));
  assert.equal(month(), realMills(usage, true));
  assert.ok(realMills(usage, true) < realMills(usage));
  assert.equal((await call({ images: imgs(101) }, { path: "/scout" })).status, 413);
});

test("/pick: main-scene marks and the 'about N, at most M' line reach the model", async () => {
  env.fetchModel = fakeFetch({ reply: ok({ picks: [] }) });
  await call({ images: imgs(2), shots: [{ id: "p0", local: "2026-05-03T10:00", place: "바다", hl: 2 }], target: 30,
    highlights: { lines: [{ top: "물속 거북이", n: 9 }], n: 8, m: 10 } });
  const text = calls[0].input[1].content.filter(c => c.type === "input_text").map(c => c.text).join("\n");
  assert.ok(text.includes("주인공 장면"));
  assert.ok(text.includes('"물속 거북이" 9장'));
  assert.ok(text.includes("약 8장, 많아도 10장"));
});

// ---- /map (다닌 길): box checks and the coast -> land polygons
import { cleanBox, landOf, joinChains, clipLine, shapeMap } from "../src/map.js";

test("map: a box is rounded outward and bounded", () => {
  assert.deepEqual(cleanBox([-8.694, 115.431, -8.661, 115.468]), [-8.7, 115.43, -8.66, 115.47]);
  assert.equal(cleanBox([0, 0, 20, 20]), null);          // wider than 12°
  assert.equal(cleanBox([1, 2, 3]), null);
  assert.equal(cleanBox([0, 0, "1", 1]), null);
  assert.equal(cleanBox([0, 0, 1, 1], true), null);       // a street-level close-up is small only
});

test("map: ways that continue each other join in any order", () => {
  const a = [[0, 0], [0, 1]], b = [[0, 1], [0, 2]], c = [[0, 2], [0, 3]];
  const out = joinChains([b, c, a]);
  assert.equal(out.length, 1);
  assert.deepEqual(out[0], [[0, 0], [0, 1], [0, 2], [0, 3]]);
});

test("map: a coast crossing the box closes along the edge on the land side", () => {
  const box = [0, 0, 10, 10];
  // a coast running south to north along x=5: land on the left (west half)
  const { land, sea } = landOf([[[-1, 5], [11, 5]]], box);
  assert.equal(sea, true);
  assert.equal(land.length, 1);
  const lngs = land[0].map(p => p[1]);
  assert.ok(Math.max(...lngs) <= 5 + 1e-9 && Math.min(...lngs) === 0); // the west half, edge corners included
});

test("map: a closed island crossing the edge is still one ring (clipped from outside)", () => {
  const box = [0, 0, 10, 10];
  const ring = [[5, 8], [5, 12], [7, 12], [7, 8], [5, 8]].map(([la, lo]) => [la, lo]); // starts inside the box
  // counter-clockwise (land on the left) in lat/lng: reorder to (lat,lng) going CCW
  const ccw = [[5, 8], [5, 12], [7, 12], [7, 8], [5, 8]];
  const { land } = landOf([ccw], box);
  assert.equal(land.length, 1);
  assert.ok(land[0].every(p => p[1] <= 10 + 1e-9));
});

test("map: no coast means an inland box (land everywhere)", () => {
  const m = shapeMap({ elements: [] }, [0, 0, 1, 1], false);
  assert.equal(m.sea, false);
  assert.deepEqual(m.land, []);
});

test("map: lines are cut at the box", () => {
  const runs = clipLine([[5, -5], [5, 5], [5, 15]], [0, 0, 10, 10]);
  assert.deepEqual(runs, [[[5, 0], [5, 5], [5, 10]]]);
});
