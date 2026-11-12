import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { handle, MemoryCounts, D1Counts, callOpenAI, SHEET_PROMPT , secret } from "../src/index.js";

const PNG = "iVBORw0KGgo" + "A".repeat(200);   // only checked for being base64-ish and small
const SITE = "https://dot-connects.github.io";
let counts, env, calls;

async function call(body, { origin = SITE, method = "POST", path = "/transform", headers = {} } = {}) {
  const h = { "content-type": "application/json", ...headers };
  if (origin) h.origin = origin;
  const req = new Request("https://w.test" + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined });
  const res = await handle(req, env, counts, { day: "2026-11-11" });
  return { status: res.status, body: res.headers.get("content-type")?.includes("json") ? await res.json() : null, res };
}
const ok = (extra = {}) => ({ style: "fluff", kind: "dog", device: "dev-aaaaaaaa", image: PNG, ...extra });
const sheetBody = (extra = {}) => ({ kind: "cat", device: "dev-aaaaaaaa", image: PNG, entry: "e1", ...extra });
const sheet = (body = sheetBody(), token = "tok-123") => call(body, { origin: null, path: "/sheet", headers: { "x-app": "catchpet", "x-purchase": token } });

beforeEach(() => {
  counts = new MemoryCounts();
  calls = [];
  env = { OPENAI_KEY: "k", DEVICE_DAY: "5", ALL_DAY: "100", DEBUG_PURCHASE: "tok-123",
    callModel: async (job) => { calls.push(job); return { mime: "image/webp", data: "Zm9v" }; } };
});

test("a valid request returns the model image and counts once", async () => {
  const r = await call(ok());
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { mime: "image/webp", image: "Zm9v", left: 4 });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].quality, "low");
  assert.equal(calls[0].size, "1024x1024");
  assert.equal(calls[0].ref, true);
  assert.equal(r.res.headers.get("access-control-allow-origin"), SITE);
});

test("bad style, kind, device or image never reaches the model", async () => {
  for (const b of [ok({ style: "toon" }), ok({ kind: "horse" }), ok({ device: "x" }), ok({ device: "dev-ABCDEFGH" }), ok({ image: "" }), ok({ image: "A".repeat(2_000_001) }), ok({ image: "<script>" })]) {
    assert.equal((await call(b)).status, 400, JSON.stringify(b).slice(0, 60));
  }
  assert.equal(calls.length, 0);
});

test("a device gets DEVICE_DAY images a day, then 429 without calling the model", async () => {
  for (let i = 0; i < 5; i++) assert.equal((await call(ok())).status, 200);
  const r = await call(ok());
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "device");
  assert.equal(calls.length, 5);
  assert.equal((await call(ok({ device: "dev-bbbbbbbb" }))).status, 200);   // another device still can
});

test("the whole site stops at ALL_DAY a day", async () => {
  env.ALL_DAY = "3";
  for (let i = 0; i < 3; i++) assert.equal((await call(ok({ device: `dev-${i}aaaaaaa` }))).status, 200);
  const r = await call(ok({ device: "dev-zzzzzzzz" }));
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "all");
  assert.equal(calls.length, 3);
});

test("ALL_DAY comes from wrangler.toml and is 30", async () => {
  const toml = readFileSync(new URL("../wrangler.toml", import.meta.url), "utf8");
  env.ALL_DAY = toml.match(/^ALL_DAY\s*=\s*"(\d+)"/m)[1];
  assert.equal(env.ALL_DAY, "30");
  assert.equal(toml.match(/^DEVICE_DAY\s*=\s*"(\d+)"/m)[1], "5");
  env.IP_DAY = "100";
  for (let i = 0; i < 30; i++) assert.equal((await call(ok({ device: `dev-${String(Math.floor(i / 5)).padStart(8, "0")}` }))).status, 200, `call ${i}`);
  assert.equal((await call(ok({ device: "dev-zzzzzzzz" }))).body.error, "all");
});

test("a refused global slot does not burn the device's own count", async () => {
  env.ALL_DAY = "1";
  await call(ok({ device: "dev-other111" }));
  assert.equal((await call(ok())).status, 429);
  env.ALL_DAY = "100";
  for (let i = 0; i < 5; i++) assert.equal((await call(ok())).status, 200, `call ${i}`);
});

test("a refused model call gives the day slot back", async () => {
  env.callModel = async () => { throw Object.assign(new Error("model HTTP 400"), { sent: true }); };
  for (let i = 0; i < 7; i++) assert.equal((await call(ok())).status, 502);
  env.callModel = async () => ({ mime: "image/webp", data: "YmFy" });
  assert.equal((await call(ok())).status, 200);
});

test("an answer without a picture was billed: the slot stays used", async () => {
  env.callModel = async () => { throw Object.assign(new Error("model gave no image"), { sent: true, billed: true }); };
  for (let i = 0; i < 5; i++) assert.equal((await call(ok())).status, 502);
  env.callModel = async () => ({ mime: "image/webp", data: "YmFy" });
  assert.equal((await call(ok())).status, 429);
});

test("ALL_DAY \"0\" switches the AI off; one network stops at IP_DAY", async () => {
  env.ALL_DAY = "0";
  assert.equal((await call(ok())).status, 429);
  env.ALL_DAY = "100"; env.IP_DAY = "2";
  assert.equal((await call(ok({ device: "dev-ip111111" }))).status, 200);
  assert.equal((await call(ok({ device: "dev-ip222222" }))).status, 200);
  assert.equal((await call(ok({ device: "dev-ip333333" }))).status, 429);
  assert.equal(calls.length, 2);
});

test("no key = 503 off, other origins and methods are refused, OPTIONS answers CORS", async () => {
  delete env.OPENAI_KEY;
  assert.deepEqual((await call(ok())).body, { error: "off" });
  assert.equal((await sheet()).status, 503);
  env.OPENAI_KEY = "k";
  assert.equal((await call(ok(), { origin: "https://evil.example" })).status, 403);
  assert.equal((await call(ok(), { origin: "https://evil.example", headers: { "x-app": "catchpet" } })).status, 403);
  const pre = await call(null, { method: "OPTIONS" });
  assert.equal(pre.status, 204);
  assert.match(pre.res.headers.get("access-control-allow-methods"), /POST/);
});

test("no Origin: accepted only with x-app catchpet, and then no CORS headers", async () => {
  assert.equal((await call(ok(), { origin: null })).status, 403);
  assert.equal((await call(ok(), { origin: null, headers: { "x-app": "other" } })).status, 403);
  const r = await call(ok(), { origin: null, headers: { "x-app": "catchpet" } });
  assert.equal(r.status, 200);
  assert.equal(r.body.left, 4);
  assert.equal(r.res.headers.get("access-control-allow-origin"), null);
});

// ---------- /sheet ----------

test("/sheet: an unset or empty DEBUG_PURCHASE refuses every token, a wrong token is refused", async () => {
  env.DEBUG_PURCHASE = "";
  assert.deepEqual((await sheet(sheetBody(), "")).body, { error: "purchase" });
  assert.equal((await sheet(sheetBody(), "")).status, 402);
  delete env.DEBUG_PURCHASE;
  assert.equal((await sheet(sheetBody(), "")).status, 402);
  assert.equal((await sheet(sheetBody(), "undefined")).status, 402);
  env.DEBUG_PURCHASE = "tok-123";
  assert.equal((await sheet(sheetBody(), "tok-12")).status, 402);
  assert.equal(calls.length, 0);
});

test("/sheet: the right token gets a medium 1536x1024 sheet with the style picture, outside ALL_DAY", async () => {
  env.ALL_DAY = "0";
  const r = await sheet();
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { mime: "image/webp", image: "Zm9v", left: 1 });
  assert.deepEqual({ q: calls[0].quality, s: calls[0].size, ref: calls[0].ref }, { q: "medium", s: "1536x1024", ref: true });
  assert.equal(calls[0].prompt, SHEET_PROMPT("cat"));
  assert.match(calls[0].prompt, /3 x 2 grid/);
  assert.match(calls[0].prompt, /6\) curled up asleep in a round ball/);
  assert.match(calls[0].prompt, /#00FF00/);
});

test("/sheet: needs x-app and a valid entry", async () => {
  assert.equal((await call(sheetBody(), { path: "/sheet", headers: { "x-purchase": "tok-123" } })).status, 404);
  assert.equal((await sheet(sheetBody({ entry: "" }))).status, 400);
  assert.equal((await sheet(sheetBody({ entry: "a/b" }))).status, 400);
  assert.equal((await sheet(sheetBody({ kind: "fish" }))).status, 400);
});

test("/sheet: an entry gets 2 calls, a token 10 a day", async () => {
  assert.equal((await sheet()).body.left, 1);
  assert.equal((await sheet()).body.left, 0);
  const r = await sheet();
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "limit");
  assert.equal(calls.length, 2);
  for (let i = 0; i < 8; i++) assert.equal((await sheet(sheetBody({ entry: `x${i}` }))).status, 200, `entry ${i}`);
  assert.equal((await sheet(sheetBody({ entry: "fresh" }))).status, 429);
  assert.equal(calls.length, 10);
});

// ---------- money ----------

test("the monthly cap: previews 5 and sheets 8 cents, over MONTH_CENTS = 503 off", async () => {
  env.MONTH_CENTS = "20";
  assert.equal((await call(ok())).status, 200);           // 5
  assert.equal((await sheet()).status, 200);              // 13
  assert.equal((await call(ok())).status, 200);           // 18
  const r = await call(ok({ device: "dev-cccccccc" }));   // 23 > 20
  assert.equal(r.status, 503);
  assert.equal(r.body.error, "off");
  assert.equal((await sheet(sheetBody({ entry: "e2" }))).status, 503);   // 26 > 20
  assert.equal(calls.length, 3);
  assert.equal(counts.m.get("usd|2026-11"), 18);
  assert.equal(counts.m.get("2026-11-11|dev-cccccccc"), 0, "an over-cap call gives the day slot back");
  assert.equal(counts.m.get("sheet|tok-123|e2"), 0, "and the sheet slot");
  delete env.MONTH_CENTS;   // default 500
  assert.equal((await call(ok({ device: "dev-cccccccc" }))).status, 200);
});

test("cents come back only when the call never left for OpenAI", async () => {
  env.callModel = async () => { throw new Error("style ref 404"); };   // before send
  assert.equal((await call(ok())).status, 502);
  assert.equal((await sheet()).status, 502);
  assert.equal(counts.m.get("usd|2026-11"), 0);
  assert.equal(counts.m.get("sheet|tok-123|e1"), 0, "an unsent sheet keeps its retry");
  env.callModel = async () => { throw Object.assign(new Error("model HTTP 400"), { sent: true }); };
  assert.equal((await call(ok())).status, 502);
  assert.equal((await sheet()).status, 502);
  assert.equal(counts.m.get("usd|2026-11"), 13);
  assert.equal(counts.m.get("sheet|tok-123|e1"), 1);
});

test("counts.add: atomic up to the limit, 0 when it would go over, negative refunds (memory and D1 SQL)", async () => {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  const d1 = { prepare: sql => ({ bind: (...a) => ({ first: async () => sqlite.prepare(sql).get(...a) ?? null, run: async () => sqlite.prepare(sql).run(...a) }) }) };
  for (const c of [new MemoryCounts(), new D1Counts(d1)]) {
    assert.equal(await c.add("usd|2026-11", 600, 500), 0);
    assert.equal(await c.add("usd|2026-11", 5, 500), 5);
    assert.equal(await c.add("usd|2026-11", 490, 500), 495);
    assert.equal(await c.add("usd|2026-11", 8, 500), 0);
    assert.equal(await c.add("usd|2026-11", 5, 500), 500);
    assert.equal(await c.add("usd|2026-11", -8, 500), 492);
    assert.equal(await c.add("usd|2026-11", 8, 500), 500);
    assert.equal(await c.take("2026-11-11|all", 1), 1);   // the old day counters still work beside it
    assert.equal(await c.take("2026-11-11|all", 1), 0);
  }
});

// ---------- the OpenAI call itself (fake fetch) ----------

let realFetch, sent;
const reply = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
function fakeFetch(...openaiReplies) {
  sent = [];
  globalThis.fetch = async (url, init) => {
    if (String(url).includes("style-ref")) return new Response(new Uint8Array([1, 2, 3]));
    sent.push({ url: String(url), init });
    const next = openaiReplies.shift();
    if (next instanceof Error) throw next;
    return next;
  };
}
beforeEach(() => { realFetch = globalThis.fetch; });
afterEach(() => { globalThis.fetch = realFetch; });
const job = (extra = {}) => ({ prompt: "p", image: PNG, ref: true, quality: "low", size: "1024x1024", ...extra });

test("callOpenAI posts multipart to images/edits with the right fields and image[] count", async () => {
  fakeFetch(reply(200, { data: [{ b64_json: "d2VicA==" }], output_format: "webp" }));
  assert.deepEqual(await callOpenAI(job(), { OPENAI_KEY: "sk-x" }), { mime: "image/webp", data: "d2VicA==" });
  assert.equal(sent.length, 1);
  assert.equal(sent[0].url, "https://api.openai.com/v1/images/edits");
  assert.equal(sent[0].init.headers.authorization, "Bearer sk-x");
  const f = sent[0].init.body;
  assert.ok(f instanceof FormData);
  assert.deepEqual({ model: f.get("model"), quality: f.get("quality"), size: f.get("size"), fmt: f.get("output_format"), comp: f.get("output_compression"), prompt: f.get("prompt") },
    { model: "gpt-image-2.6-flare", quality: "low", size: "1024x1024", fmt: "webp", comp: "80", prompt: "p" });
  assert.equal(f.getAll("image[]").length, 2);
  assert.equal(f.getAll("image[]")[0].type, "image/png");

  fakeFetch(reply(200, { data: [{ b64_json: "eA==" }] }));
  await callOpenAI(job({ ref: false, quality: "medium", size: "1536x1024" }), { OPENAI_KEY: "k" });
  assert.equal(sent[0].init.body.getAll("image[]").length, 1);
  assert.equal(sent[0].init.body.get("quality"), "medium");
  assert.equal(sent[0].init.body.get("size"), "1536x1024");
});

test("callOpenAI retries a 5xx once, never a 4xx, and marks what was sent or billed", async () => {
  fakeFetch(reply(500, {}), reply(200, { data: [{ b64_json: "b2s=" }] }));
  assert.equal((await callOpenAI(job(), { OPENAI_KEY: "k" })).data, "b2s=");
  assert.equal(sent.length, 2);

  fakeFetch(reply(502, {}), reply(503, {}), reply(200, {}));
  await assert.rejects(callOpenAI(job(), { OPENAI_KEY: "k" }), e => e.sent === true && !e.billed && /503/.test(e.message));
  assert.equal(sent.length, 2);

  for (const s of [400, 429]) {
    fakeFetch(reply(s, { error: { code: "x" } }), reply(200, { data: [{ b64_json: "b2s=" }] }));
    await assert.rejects(callOpenAI(job(), { OPENAI_KEY: "k" }), e => e.sent === true && !e.billed);
    assert.equal(sent.length, 1, `no retry on ${s}`);
  }

  fakeFetch(reply(200, { data: [] }));
  await assert.rejects(callOpenAI(job(), { OPENAI_KEY: "k" }), e => e.sent && e.billed);

  fakeFetch(new TypeError("network down"));
  await assert.rejects(callOpenAI(job(), { OPENAI_KEY: "k" }), e => e.sent === true);
});

test("a 5xx twice through the real handler: one retry, a 502, and the cents stay reserved", async () => {
  delete env.callModel;
  fakeFetch(reply(500, {}), reply(500, {}));
  const r = await call(ok({ style: "figure" }));
  assert.equal(r.status, 502);
  assert.equal(sent.length, 2);
  assert.equal(counts.m.get("usd|2026-11"), 5);
  assert.equal(counts.m.get("2026-11-11|dev-aaaaaaaa"), 0, "an unbilled failure gives the day slot back");
});

test("a BOM or newline around a secret is ignored (PowerShell pipe)", () => {
  assert.equal(secret(String.fromCharCode(0xfeff) + "sk-abc" + String.fromCharCode(13, 10)), "sk-abc");
  assert.equal(secret(undefined), "");
});
