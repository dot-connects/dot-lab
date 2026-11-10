import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { handle, MemoryCounts, pickImage } from "../src/index.js";

const PNG = "iVBORw0KGgo" + "A".repeat(200);   // only checked for being base64-ish and small
let counts, env, gemini;

async function call(body, { origin = "https://dot-connects.github.io", method = "POST" } = {}) {
  const req = new Request("https://w.test/transform", { method, headers: { "content-type": "application/json", origin }, body: body ? JSON.stringify(body) : undefined });
  const res = await handle(req, env, counts, { day: "2026-11-11" });
  return { status: res.status, body: res.headers.get("content-type")?.includes("json") ? await res.json() : null, res };
}
const ok = (extra = {}) => ({ style: "fluff", kind: "dog", device: "dev-aaaaaaaa", image: PNG, ...extra });

beforeEach(() => {
  counts = new MemoryCounts();
  gemini = [];
  env = { GEMINI_KEY: "k", DEVICE_DAY: "5", ALL_DAY: "100",
    callModel: async (input) => { gemini.push(input); return { mime: "image/jpeg", data: "Zm9v" }; } };
});

test("a valid request returns the model image and counts once", async () => {
  const r = await call(ok());
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { mime: "image/jpeg", image: "Zm9v", left: 4 });
  assert.equal(gemini.length, 1);
  assert.equal(r.res.headers.get("access-control-allow-origin"), "https://dot-connects.github.io");
});

test("bad style, kind, device or image never reaches the model", async () => {
  for (const b of [ok({ style: "toon" }), ok({ kind: "horse" }), ok({ device: "x" }), ok({ image: "" }), ok({ image: "A".repeat(2_000_001) }), ok({ image: "<script>" })]) {
    assert.equal((await call(b)).status, 400, JSON.stringify(b).slice(0, 60));
  }
  assert.equal(gemini.length, 0);
});

test("a device gets DEVICE_DAY images a day, then 429 without calling the model", async () => {
  for (let i = 0; i < 5; i++) assert.equal((await call(ok())).status, 200);
  const r = await call(ok());
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "device");
  assert.equal(gemini.length, 5);
  assert.equal((await call(ok({ device: "dev-bbbbbbbb" }))).status, 200);   // another device still can
});

test("the whole site stops at ALL_DAY a day", async () => {
  env.ALL_DAY = "3";
  for (let i = 0; i < 3; i++) assert.equal((await call(ok({ device: `dev-${i}aaaaaaa` }))).status, 200);
  const r = await call(ok({ device: "dev-zzzzzzzz" }));
  assert.equal(r.status, 429);
  assert.equal(r.body.error, "all");
  assert.equal(gemini.length, 3);
});

test("a refused global slot does not burn the device's own count", async () => {
  env.ALL_DAY = "1";
  await call(ok({ device: "dev-other111" }));
  assert.equal((await call(ok())).status, 429);
  env.ALL_DAY = "100";
  for (let i = 0; i < 5; i++) assert.equal((await call(ok())).status, 200, `call ${i}`);
});

test("a refused model call gives the slot back", async () => {
  env.callModel = async () => { throw new Error("model HTTP 500"); };
  for (let i = 0; i < 7; i++) assert.equal((await call(ok())).status, 502);
  env.callModel = async () => ({ mime: "image/png", data: "YmFy" });
  assert.equal((await call(ok())).status, 200);
});

test("an answer without a picture was billed: the slot stays used", async () => {
  env.callModel = async () => { throw Object.assign(new Error("model gave no image"), { billed: true }); };
  for (let i = 0; i < 5; i++) assert.equal((await call(ok())).status, 502);
  env.callModel = async () => ({ mime: "image/png", data: "YmFy" });
  assert.equal((await call(ok())).status, 429);
});

test("ALL_DAY \"0\" switches the AI off; one network stops at IP_DAY", async () => {
  env.ALL_DAY = "0";
  assert.equal((await call(ok())).status, 429);
  env.ALL_DAY = "100"; env.IP_DAY = "2";
  assert.equal((await call(ok({ device: "dev-ip111111" }))).status, 200);
  assert.equal((await call(ok({ device: "dev-ip222222" }))).status, 200);
  assert.equal((await call(ok({ device: "dev-ip333333" }))).status, 429);
  assert.equal(gemini.length, 2);
});

test("no key = 503, other origins and methods are refused, OPTIONS answers CORS", async () => {
  delete env.GEMINI_KEY;
  assert.equal((await call(ok())).status, 503);
  env.GEMINI_KEY = "k";
  assert.equal((await call(ok(), { origin: "https://evil.example" })).status, 403);
  const pre = await call(null, { method: "OPTIONS" });
  assert.equal(pre.status, 204);
  assert.match(pre.res.headers.get("access-control-allow-methods"), /POST/);
});

test("pickImage reads both the interactions and the generateContent shapes", () => {
  assert.deepEqual(pickImage({ steps: [{ type: "thought" }, { type: "model_output", content: [{ type: "text", text: "hi" }, { type: "image", mime_type: "image/png", data: "QQ==" }] }] }), { mime: "image/png", data: "QQ==" });
  assert.deepEqual(pickImage({ output_image: { data: "Qg==" } }), { mime: "image/png", data: "Qg==" });
  assert.deepEqual(pickImage({ candidates: [{ content: { parts: [{ text: "x" }, { inlineData: { mimeType: "image/jpeg", data: "Qw==" } }] } }] }), { mime: "image/jpeg", data: "Qw==" });
  assert.equal(pickImage({ steps: [] }), null);
});
