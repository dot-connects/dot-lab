import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { handle, MemoryCounts, D1Counts, callOpenAI, SHEET_PROMPT , secret, validKind } from "../src/index.js";
import { Ledger } from "../src/ledger.js";
import { requestHash } from "../src/verdicts.js";

const PNG = "iVBORw0KGgo" + "A".repeat(200);   // only checked for being base64-ish and small
const SITE = "https://dot-connects.github.io";
let counts, env, calls, ledger, sqlite, google, purchases;
const APP = { "x-app": "catchpet", "x-integrity": "INT" };
const CERT = "q83vEjRWeJA=";   // = hex abcdef1234567890
let nextHash = "";             // what the app hashed for the request in flight: the fake verdict echoes it
const verdict = (extra = {}) => ({
  requestDetails: { requestPackageName: "kr.dot.catchpet", requestHash: nextHash, timestampMillis: String(Date.now()) },
  appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "kr.dot.catchpet", certificateSha256Digest: [CERT] },
  deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] }, ...extra });

async function call(body, { origin = null, method = "POST", path = "/transform", headers = APP } = {}) {
  const h = { "content-type": "application/json", ...headers };
  if (origin) h.origin = origin;
  const text = body ? JSON.stringify(body) : undefined;
  nextHash = body ? await requestHash(path, body.device, text) : "";
  const req = new Request("https://w.test" + path, { method, headers: h, body: text });
  const res = await handle(req, env, counts, { day: "2026-11-11", ledger });
  return { status: res.status, body: res.headers.get("content-type")?.includes("json") ? await res.json() : null, res };
}
const ok = (extra = {}) => ({ style: "fluff", kind: "dog", device: "dev-aaaaaaaa", image: PNG, ...extra });
const sheetBody = (extra = {}) => ({ kind: "cat", device: "dev-aaaaaaaa", image: PNG, entry: "e1", ...extra });
const sheet = (body = sheetBody(), token = "tok-123") => call(body, { path: "/sheet", headers: { "x-app": "catchpet", "x-purchase": token } });
const paid = (extra = {}, headers = APP) => call(sheetBody({ layout: "12", ...extra }), { path: "/sheet", headers });

beforeEach(() => {
  counts = new MemoryCounts();
  calls = [];
  sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  ledger = new Ledger({ prepare: sql => ({ bind: (...a) => ({ first: async () => sqlite.prepare(sql).get(...a) ?? null, run: async () => sqlite.prepare(sql).run(...a) }) }) });
  purchases = new Map();   // token -> ProductPurchase
  google = { consumed: [], verdict: () => verdict(),
    decodeIntegrity: async () => google.verdict(),
    getPurchase: async (e, product, token) => { if (!purchases.has(token)) throw Object.assign(new Error("x"), { status: 410 }); return purchases.get(token); },
    consumePurchase: async (e, product, token) => { google.consumed.push(token); return true; } };
  const r2 = new Map();
  env = { OPENAI_KEY: "k", DEVICE_DAY: "5", ALL_DAY: "100", DEBUG_PURCHASE: "tok-123", CERT_SHA256: "AB:CD:EF:12:34:56:78:90", FREE_SHEETS: "3", google,
    SHEETS: { m: r2, put: async (k, v, o) => { r2.set(k, { v, o }); }, get: async k => r2.has(k) ? { arrayBuffer: async () => r2.get(k).v.buffer, httpMetadata: r2.get(k).o.httpMetadata } : null },
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
  assert.equal(r.res.headers.get("access-control-allow-origin"), null);
});

test("bad style, kind, device or image never reaches the model", async () => {
  for (const b of [ok({ style: "toon" }), ok({ kind: "dragon" }), ok({ device: "x" }), ok({ device: "dev-ABCDEFGH" }), ok({ image: "" }), ok({ image: "A".repeat(2_000_001) }), ok({ image: "<script>" })]) {
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
  const pre = await call(null, { method: "OPTIONS", origin: SITE });
  assert.equal(pre.status, 204);
  assert.match(pre.res.headers.get("access-control-allow-methods"), /POST/);
});

test("the frozen web game gets 503 off (with CORS so it falls back cleanly)", async () => {
  const r = await call(ok(), { origin: SITE, headers: {} });
  assert.equal(r.status, 503);
  assert.equal(r.body.error, "off");
  assert.equal(r.res.headers.get("access-control-allow-origin"), SITE);
  assert.equal(calls.length, 0);
});

test("no Origin: accepted only with x-app catchpet and a good integrity verdict, then no CORS headers", async () => {
  assert.equal((await call(ok(), { headers: {} })).status, 403);
  assert.equal((await call(ok(), { headers: { "x-app": "other" } })).status, 403);
  assert.deepEqual((await call(ok(), { headers: { "x-app": "catchpet" } })).body, { error: "integrity" });
  const r = await call(ok());
  assert.equal(r.status, 200);
  assert.equal(r.body.left, 4);
  assert.equal(r.res.headers.get("access-control-allow-origin"), null);
});

// ---------- /sheet ----------

test("/sheet: an unset, empty or wrong DEBUG_PURCHASE is no dev pass (no integrity header = 403)", async () => {
  env.DEBUG_PURCHASE = "";
  assert.deepEqual((await sheet(sheetBody(), "")).body, { error: "integrity" });
  delete env.DEBUG_PURCHASE;
  assert.equal((await sheet(sheetBody(), "undefined")).status, 403);
  env.DEBUG_PURCHASE = "tok-123";
  assert.equal((await sheet(sheetBody(), "tok-12")).status, 403);
  assert.equal(calls.length, 0);
});

test("/sheet: the right token gets a medium 1536x1024 sheet with the style picture, outside ALL_DAY", async () => {
  env.ALL_DAY = "0";
  const r = await sheet();
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { mime: "image/webp", image: "Zm9v", layout: "6" });
  assert.deepEqual({ q: calls[0].quality, s: calls[0].size, ref: calls[0].ref }, { q: "medium", s: "1536x1024", ref: true });
  assert.equal(calls[0].prompt, SHEET_PROMPT("cat"));
  assert.match(calls[0].prompt, /3 x 2 grid/);
  assert.match(calls[0].prompt, /6\) curled up asleep in a round ball/);
  assert.match(calls[0].prompt, /#00FF00/);
});

test("/sheet: style figure draws the figurine sheet without the fur-ball picture and counts apart from fluff", async () => {
  env.ALL_DAY = "0";
  assert.equal((await sheet(sheetBody({ style: "figure" }))).status, 200);
  assert.equal(calls[0].ref, false);
  assert.match(calls[0].prompt, /figurine/);
  assert.match(calls[0].prompt, /#00FF00/);
  assert.equal((await sheet(sheetBody({ style: "fluff" }))).status, 200);   // the fur-ball sheet still has its own slots
  assert.equal(calls[1].ref, true);
  assert.equal((await sheet(sheetBody({ style: "ball" }))).status, 400);
});

test("group kinds: up to 3 known kinds joined by +, nothing else", () => {
  assert.equal(validKind("person+cat"), true);
  assert.equal(validKind("person+person+dog"), true);
  for (const k of ["person+person+person+cat", "person+", "+cat", "person+dragon", "person cat", "", null, 3]) assert.equal(validKind(k), false, String(k));
});

test("/sheet: needs x-app and a valid entry", async () => {
  assert.equal((await call(sheetBody(), { path: "/sheet", headers: { "x-purchase": "tok-123" } })).status, 403);
  assert.equal((await sheet(sheetBody({ entry: "" }))).status, 400);
  assert.equal((await sheet(sheetBody({ entry: "a/b" }))).status, 400);
  assert.equal((await sheet(sheetBody({ kind: "dragon" }))).status, 400);
});

test("dev token: sheets have no entry or day limit", async () => {
  for (let i = 0; i < 12; i++) assert.equal((await sheet()).status, 200, `call ${i}`);
  assert.equal(calls.length, 12);
});

test("dev token: app previews skip the day counters and say no 'left'", async () => {
  const dev = b => call(b, { headers: { "x-app": "catchpet", "x-purchase": "tok-123" } });
  for (let i = 0; i < 7; i++) { const r = await dev(ok()); assert.equal(r.status, 200, `call ${i}`); assert.equal(r.body.left, undefined); }
  assert.equal(counts.m.get("2026-11-11|dev-aaaaaaaa"), undefined);
  assert.equal(counts.m.get("devusd|2026-11"), 35);
  const wrong = await call(ok(), { headers: { ...APP, "x-purchase": "nope" } });
  assert.equal(wrong.body.left, 4, "a wrong token is an ordinary preview");
});

// ---------- money ----------

test("the monthly caps: public MONTH_CENTS and the dev DEV_MONTH_CENTS are apart, MONTH_CENTS 0 stops both", async () => {
  env.MONTH_CENTS = "15"; env.DEV_MONTH_CENTS = "16";
  for (let i = 0; i < 3; i++) assert.equal((await call(ok({ device: `dev-aaaaaaa${i}` }))).status, 200);   // 15
  const r = await call(ok({ device: "dev-cccccccc" }));   // 20 > 15
  assert.equal(r.status, 503);
  assert.equal(r.body.error, "off");
  assert.equal(counts.m.get("2026-11-11|dev-cccccccc"), 0, "an over-cap call gives the day slot back");
  assert.equal((await sheet()).status, 200);              // dev 8
  assert.equal((await sheet()).status, 200);              // dev 16
  assert.equal((await sheet()).status, 503);              // dev 24 > 16
  assert.equal(counts.m.get("usd|2026-11"), 15);
  assert.equal(counts.m.get("devusd|2026-11"), 16);
  env.MONTH_CENTS = "0"; env.DEV_MONTH_CENTS = "1000";
  assert.equal((await sheet()).status, 503, "the master switch stops dev calls too");
  delete env.MONTH_CENTS; delete env.DEV_MONTH_CENTS;   // defaults 500 / 2000
  assert.equal((await call(ok({ device: "dev-cccccccc" }))).status, 200);
  assert.equal((await sheet()).status, 200);
});

test("cents come back only when the call never left for OpenAI", async () => {
  env.callModel = async () => { throw new Error("style ref 404"); };   // before send
  assert.equal((await call(ok())).status, 502);
  assert.equal((await sheet()).status, 502);
  assert.equal(counts.m.get("usd|2026-11"), 0);
  assert.equal(counts.m.get("devusd|2026-11"), 0);
  env.callModel = async () => { throw Object.assign(new Error("model HTTP 400"), { sent: true }); };
  assert.equal((await call(ok())).status, 502);
  assert.equal((await sheet()).status, 502);
  assert.equal(counts.m.get("usd|2026-11"), 5);
  assert.equal(counts.m.get("devusd|2026-11"), 8);
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

test("a 4xx refusal keeps the day slot but gives the month's cents back (review M1)", async () => {
  delete env.callModel;
  fakeFetch(reply(400, { error: { code: "invalid_image" } }));
  const r = await call(ok({ style: "figure" }));
  assert.equal(r.status, 502);
  assert.equal(counts.m.get("usd|2026-11"), 0, "a refusal is not billed: cents back");
  assert.equal(counts.m.get("2026-11-11|dev-aaaaaaaa"), 1, "the day slot stays used");
});

test("/sheet: says which layout it drew (the app names the file by it)", async () => {
  env.DEBUG_PURCHASE = "tok-123";
  assert.equal((await sheet(sheetBody({ layout: "12" }))).body.layout, "12");
  assert.equal((await sheet(sheetBody({ entry: "e2" }))).body.layout, "6");
});

// ---------- Play: integrity, free test sheets, purchases (spec-release.md §2-§4) ----------

test("integrity: each bad verdict is a 403 and never reaches the model; no service account = 503 off", async () => {
  for (const v of [{ appIntegrity: { ...verdict().appIntegrity, appRecognitionVerdict: "UNRECOGNIZED_VERSION" } },
                   { deviceIntegrity: { deviceRecognitionVerdict: [] } }]) {
    google.verdict = () => verdict(v);
    assert.equal((await call(ok())).status, 403);
    assert.equal((await paid()).status, 403);
  }
  google.verdict = () => ({ ...verdict(), requestDetails: { ...verdict().requestDetails, requestHash: "other body" } });
  assert.equal((await call(ok())).status, 403, "a token made for another request");
  google.decodeIntegrity = async () => { throw Object.assign(new Error("x"), { status: 500 }); };
  assert.equal((await call(ok())).status, 503, "Google trouble is retryable, not a refusal");
  delete env.google;
  assert.deepEqual((await call(ok())).body, { error: "off" });
  assert.equal(calls.length, 0);
});

test("free test sheets: 3 per device, a failed draw does not use one, then 429 free", async () => {
  env.callModel = async () => { throw Object.assign(new Error("x"), { sent: true }); };
  assert.equal((await paid()).status, 502);
  env.callModel = async job => { calls.push(job); return { mime: "image/webp", data: "Zm9v" }; };
  for (const left of [2, 1, 0]) { const r = await paid(); assert.equal(r.status, 200); assert.equal(r.body.free_left, left); assert.equal(r.body.layout, "12"); }
  const r = await paid();
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "free");
  assert.equal((await paid({ device: "dev-bbbbbbbb" })).status, 200, "another device has its own 3");
  env.FREE_SHEETS = "0";
  assert.equal((await paid({ device: "dev-cccccccc" })).body.error, "free", "production: none");
});

test("/quota tells the app what is left", async () => {
  await paid();
  const q = await call(null, { method: "GET", path: "/quota?device=dev-aaaaaaaa" });
  assert.deepEqual(q.body, { free_left: 2, free_total: 3 });
  assert.equal((await call(null, { method: "GET", path: "/quota?device=x" })).status, 400);
});

test("a paid sheet: verified, drawn once, stored, consumed; the same token again gets the stored sheet", async () => {
  purchases.set("PT1", { purchaseState: 0, consumptionState: 0 });
  const r = await paid({ product: "sheet_fluff", purchaseToken: "PT1" });
  assert.equal(r.status, 200);
  assert.deepEqual({ img: r.body.image, layout: r.body.layout, free: r.body.free_left }, { img: "Zm9v", layout: "12", free: undefined });
  assert.deepEqual(google.consumed, ["PT1"]);
  assert.equal(env.SHEETS.m.size, 1);
  assert.ok(![...env.SHEETS.m.keys()][0].includes("PT1"), "the R2 key is a hash, not the token");
  purchases.set("PT1", { purchaseState: 0, consumptionState: 1 });   // Google now says consumed
  const again = await paid({ product: "sheet_fluff", purchaseToken: "PT1" });
  assert.equal(again.status, 200);
  assert.equal(again.body.image, "Zm9v");
  assert.equal(calls.length, 1, "drawn once");
  assert.deepEqual(google.consumed, ["PT1"], "not consumed twice");
  assert.equal((await call(null, { method: "GET", path: "/quota?device=dev-aaaaaaaa" })).body.free_left, 3, "a paid sheet uses no free one");
});

test("a consume that failed is retried when the stored sheet is fetched again", async () => {
  purchases.set("PT2", { purchaseState: 0, consumptionState: 0 });
  google.consumePurchase = async () => { throw Object.assign(new Error("x"), { status: 503 }); };
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT2" })).status, 200);
  google.consumePurchase = async (e, p, t) => { google.consumed.push(t); return true; };
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT2" })).status, 200);
  assert.deepEqual(google.consumed, ["PT2"]);
});

test("pending, cancelled, consumed or unknown tokens draw nothing; a pending one can come back later", async () => {
  purchases.set("PEND", { purchaseState: 2, consumptionState: 0 });
  assert.deepEqual((await paid({ product: "sheet_fluff", purchaseToken: "PEND" })).body, { error: "pending" });
  purchases.set("PEND", { purchaseState: 0, consumptionState: 0 });
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PEND" })).status, 200);
  purchases.set("CANC", { purchaseState: 1, consumptionState: 0 });
  purchases.set("USED", { purchaseState: 0, consumptionState: 1 });
  for (const t of ["CANC", "USED", "NOPE"]) assert.deepEqual((await paid({ product: "sheet_fluff", purchaseToken: t })).body, { error: "purchase" }, t);
  assert.equal(calls.length, 1);
});

test("product and style must match; a token is bound to its product", async () => {
  purchases.set("PT3", { purchaseState: 0, consumptionState: 0 });
  assert.equal((await paid({ product: "sheet_figure", purchaseToken: "PT3" })).status, 400, "figure product, fluff style");
  assert.equal((await paid({ product: "sheet_other", purchaseToken: "PT3" })).status, 400);
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT3" })).status, 200);
  assert.equal((await paid({ product: "sheet_figure", style: "figure", purchaseToken: "PT3" })).status, 400);
});

test("a model failure on a paid sheet: 502, not consumed, the same token draws on retry", async () => {
  purchases.set("PT4", { purchaseState: 0, consumptionState: 0 });
  env.callModel = async () => { throw Object.assign(new Error("x"), { sent: true }); };
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT4" })).status, 502);
  assert.deepEqual(google.consumed, []);
  env.callModel = async job => { calls.push(job); return { mime: "image/webp", data: "Zm9v" }; };
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT4" })).status, 200);
  assert.deepEqual(google.consumed, ["PT4"]);
});

test("a second request while the first still draws gets 409 busy", async () => {
  purchases.set("PT5", { purchaseState: 0, consumptionState: 0 });
  let release;
  env.callModel = () => new Promise(res => { release = () => res({ mime: "image/webp", data: "Zm9v" }); });
  const first = paid({ product: "sheet_fluff", purchaseToken: "PT5" });
  await new Promise(r => setTimeout(r, 20));
  assert.deepEqual((await paid({ product: "sheet_fluff", purchaseToken: "PT5" })).body, { error: "busy" });
  release();
  assert.equal((await first).status, 200);
});

test("/report stores a row; bad fields are refused", async () => {
  const rep = b => call(b, { path: "/report" });
  assert.deepEqual((await rep({ device: "dev-aaaaaaaa", entry: "e1", kind: "cat", style: "fluff", reason: "odd" })).body, { ok: true });
  assert.equal((await rep({ device: "dev-aaaaaaaa", entry: "e1", kind: "cat", style: "fluff", reason: "spam" })).status, 400);
  assert.equal((await rep({ device: "x", entry: "e1", kind: "cat", style: "fluff", reason: "odd" })).status, 400);
  assert.equal(sqlite.prepare("SELECT COUNT(*) n FROM reports").get().n, 1);
});

test("the dev token still draws sheets and previews with no integrity at all", async () => {
  delete env.google;
  assert.equal((await sheet()).status, 200);
  assert.equal((await call(ok(), { headers: { "x-app": "catchpet", "x-purchase": "tok-123" } })).status, 200);
});

// ---------- review round 1 (2026-11-16): H1, M1-M4, L2-L4 + security review ----------

test("H1: parallel free requests cannot draw more than FREE_SHEETS", async () => {
  let release; const gate = new Promise(r => { release = r; });
  env.callModel = async job => { calls.push(job); await gate; return { mime: "image/webp", data: "Zm9v" }; };
  const all = Promise.all(Array.from({ length: 6 }, () => paid()));
  await new Promise(r => setTimeout(r, 20));
  release();
  const rs = await all;
  assert.equal(rs.filter(r => r.status === 200).length, 3);
  assert.equal(rs.filter(r => r.body.error === "free").length, 3);
  assert.equal(calls.length, 3);
});

test("security: free sheets also stop per IP and for everyone a day", async () => {
  env.FREE_IP_DAY = "4"; env.FREE_ALL_DAY = "100";
  for (const d of ["dev-aaaaaaa1", "dev-aaaaaaa2"]) for (let i = 0; i < 2; i++) assert.equal((await paid({ device: d })).status, 200);
  assert.equal((await paid({ device: "dev-aaaaaaa3" })).body.error, "free", "5th free sheet from one network");
  env.FREE_IP_DAY = "100"; env.FREE_ALL_DAY = "5";
  assert.equal((await paid({ device: "dev-aaaaaaa4" })).status, 200);
  assert.equal((await paid({ device: "dev-aaaaaaa5" })).body.error, "free", "6th free sheet today for everyone");
});

test("M1: a taken-over or failed row is checked with Google again before drawing", async () => {
  purchases.set("PT6", { purchaseState: 0, consumptionState: 0 });
  env.callModel = async () => { throw Object.assign(new Error("x"), { sent: true }); };
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT6" })).status, 502);
  purchases.set("PT6", { purchaseState: 1, consumptionState: 0 });   // refunded meanwhile
  env.callModel = async job => { calls.push(job); return { mime: "image/webp", data: "Zm9v" }; };
  assert.deepEqual((await paid({ product: "sheet_fluff", purchaseToken: "PT6" })).body, { error: "purchase" });
  assert.equal(calls.length, 0);
});

test("M2: a Google outage is a retryable 503, only 400/404/410 mean a bad purchase", async () => {
  purchases.set("PT7", { purchaseState: 0, consumptionState: 0 });
  for (const st of [500, 503, 429, 401, 403]) {
    google.getPurchase = async () => { throw Object.assign(new Error("x"), { status: st }); };
    assert.deepEqual((await paid({ product: "sheet_fluff", purchaseToken: "PT7" })).body, { error: "busy" }, String(st));
  }
  google.getPurchase = async () => { throw Object.assign(new Error("x"), { status: 410 }); };
  assert.deepEqual((await paid({ product: "sheet_fluff", purchaseToken: "PT7" })).body, { error: "purchase" });
  google.getPurchase = async (e, p, t) => purchases.get(t);
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT7" })).status, 200, "the token was not burned");
  google.decodeIntegrity = async () => { throw Object.assign(new Error("x"), { status: 503 }); };
  assert.deepEqual((await call(ok())).body, { error: "busy" });
  google.decodeIntegrity = async () => { throw Object.assign(new Error("x"), { status: 400 }); };
  assert.deepEqual((await call(ok())).body, { error: "integrity" });
});

test("M3: paid sheets have their own ceiling, so free testers cannot block buyers", async () => {
  env.MONTH_CENTS = "8";
  assert.equal((await paid()).status, 200);                       // free sheet: public 8
  assert.equal((await paid({ device: "dev-bbbbbbbb" })).body.error, "off");
  purchases.set("PT8", { purchaseState: 0, consumptionState: 0 });
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT8" })).status, 200);
  assert.equal(counts.m.get("paidusd|2026-11"), 8);
  env.MONTH_CENTS = "0";
  purchases.set("PT9", { purchaseState: 0, consumptionState: 0 });
  assert.equal((await paid({ product: "sheet_fluff", purchaseToken: "PT9" })).body.error, "off", "the master switch still stops it");
});

test("M4: a failed R2 write still hands over the drawn sheet and consumes", async () => {
  purchases.set("PT10", { purchaseState: 0, consumptionState: 0 });
  env.SHEETS.put = async () => { throw new Error("r2 down"); };
  const r = await paid({ product: "sheet_fluff", purchaseToken: "PT10" });
  assert.equal(r.status, 200);
  assert.equal(r.body.image, "Zm9v");
  assert.deepEqual(google.consumed, ["PT10"]);
});

test("L2: reports stop at 20 a device a day", async () => {
  const rep = () => call({ device: "dev-aaaaaaaa", entry: "e1", kind: "cat", style: "fluff", reason: "odd" }, { path: "/report" });
  for (let i = 0; i < 20; i++) assert.equal((await rep()).status, 200);
  assert.equal((await rep()).status, 429);
});

test("security: a billed or refused free draw keeps its slot; only an unsent or 5xx one comes back", async () => {
  env.callModel = async () => { throw Object.assign(new Error("x"), { sent: true, rejected: true }); };   // 4xx: junk image
  assert.equal((await paid()).status, 502);
  env.callModel = async () => { throw Object.assign(new Error("x"), { sent: true, billed: true }); };
  assert.equal((await paid()).status, 502);
  env.callModel = async () => { throw Object.assign(new Error("x"), { sent: true }); };                  // 5xx
  assert.equal((await paid()).status, 502);
  env.callModel = async job => { calls.push(job); return { mime: "image/webp", data: "Zm9v" }; };
  assert.equal((await paid()).body.free_left, 0, "two kept, one handed back");
});

test("security: IPv6 addresses count per /64 network", async () => {
  env.FREE_IP_DAY = "2";
  const from = (ip, device) => call(sheetBody({ layout: "12", device }), { path: "/sheet", headers: { ...APP, "cf-connecting-ip": ip } });
  assert.equal((await from("2001:db8:1:2:aaaa::1", "dev-v6aaaaa1")).status, 200);
  assert.equal((await from("2001:db8:1:2:bbbb::9", "dev-v6aaaaa2")).status, 200);
  assert.equal((await from("2001:db8:1:2:cccc::5", "dev-v6aaaaa3")).body.error, "free");
  assert.equal((await from("2001:db8:1:3::5", "dev-v6aaaaa4")).status, 200, "another /64");
});

test("ipKey expands IPv6 before cutting the /64; junk shares one bucket", async () => {
  const { ipKey } = await import("../src/index.js");
  const k = ipKey("2001:db8:0:0:1::5");
  assert.equal(k, "2001:db8:0:0::/64");
  for (const ip of ["2001:db8::5", "2001:db8::2:5", "2001:DB8:0:0:ffff::", "2001:0db8:0000:0000::1", "2001:db8::1.2.3.4"]) assert.equal(ipKey(ip), k, ip);
  assert.equal(ipKey("2001:db8:0:1::5"), "2001:db8:0:1::/64");
  assert.equal(ipKey("203.0.113.7"), "203.0.113.7");
  for (const bad of ["1:2:3:4:5:6:7:8:9", "2001::db8::1", "zz::1", "", null]) assert.equal(ipKey(bad), "?", String(bad));
});
