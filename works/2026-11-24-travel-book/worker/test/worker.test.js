import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { handle, MemoryCounts, D1Counts, reserveMills, realMills } from "../src/index.js";

const B64 = "/9j/" + "A".repeat(300);
let counts, env, calls, logs, waits;

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
  ({ status: st, usage, output: [{ type: "reasoning" }, { type: "message", content: [{ type: "output_text", text: JSON.stringify(obj) }] }] });

beforeEach(() => {
  counts = new MemoryCounts(); calls = []; logs = []; waits = [];
  env = { APP_TOKEN: "tok", OPENAI_KEY: "k", MONTH_CENTS: "1000", DAY_BOOKS: "20",
    fetchModel: fakeFetch({ reply: ok({ picks: [] }) }) };
  console.log = (...a) => logs.push(a.join(" "));
});

async function call(body, { path = "/book", token = "tok" } = {}) {
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

test("61 images is 413", async () => {
  assert.equal((await call({ images: imgs(61) })).status, 413);
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
  const reserve = reserveMills(2, 50, 12000);
  env.MONTH_CENTS = String(Math.ceil(reserve / 10));   // room for one reservation only
  env.fetchModel = fakeFetch({ reply: ok({}, { input_tokens: 10, output_tokens: 30000 }) });   // 300 mills > reserve
  await call({ images: imgs(2) });
  assert.equal(month(), realMills({ input_tokens: 10, output_tokens: 30000 }));
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
  assert.equal(month(), reserveMills(2, JSON.stringify({}).length, 12000));
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
