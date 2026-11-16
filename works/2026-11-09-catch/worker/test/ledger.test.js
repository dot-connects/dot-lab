import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { Ledger } from "../src/ledger.js";

let sqlite, led;
const d1 = db => ({ prepare: sql => ({ bind: (...a) => ({
  first: async () => db.prepare(sql).get(...a) ?? null,
  run: async () => db.prepare(sql).run(...a),
}) }) });

beforeEach(() => {
  sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  led = new Ledger(d1(sqlite));
});

test("a fresh token is new, the same token while drawing is busy", async () => {
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 1000), { go: "new" });
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 2000), { go: "busy" });
});

test("finish -> cached with key and layout; markConsumed shows up", async () => {
  await led.claim("T", "sheet_fluff", "dev-a", 1000);
  await led.finish("T", "sheet/abc.webp", "12");
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 5000), { go: "cached", key: "sheet/abc.webp", layout: "12", consumed: 0 });
  await led.markConsumed("T");
  assert.equal((await led.claim("T", "sheet_fluff", "dev-a", 6000)).consumed, 1);
});

test("fail -> the next claim draws again", async () => {
  await led.claim("T", "sheet_fluff", "dev-a", 1000);
  await led.fail("T");
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 2000), { go: "draw" });
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 3000), { go: "busy" }, "and is drawing again");
});

test("a drawing row older than 10 minutes is taken over (a Worker died mid-draw)", async () => {
  await led.claim("T", "sheet_fluff", "dev-a", 1000);
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 1000 + 599_000), { go: "busy" });
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 1000 + 601_000), { go: "draw" });
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 1000 + 602_000), { go: "busy" });
});

test("two claims racing on one fresh token: exactly one is new", async () => {
  const r = await Promise.all([led.claim("T", "sheet_fluff", "dev-a", 1000), led.claim("T", "sheet_fluff", "dev-b", 1000)]);
  assert.deepEqual(r.map(x => x.go).sort(), ["busy", "new"]);
});

test("a token is bound to its product: another product is refused", async () => {
  await led.claim("T", "sheet_fluff", "dev-a", 1000);
  await led.finish("T", "k", "12");
  assert.deepEqual(await led.claim("T", "sheet_figure", "dev-a", 2000), { go: "mismatch" });
});

test("forget frees a token Google refused", async () => {
  await led.claim("T", "sheet_fluff", "dev-a", 1000);
  await led.forget("T");
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 2000), { go: "new" });
});

test("free sheets count per device", async () => {
  assert.equal(await led.freeLeft("dev-a", 3), 3);
  await led.takeFree("dev-a", 3); await led.takeFree("dev-a", 3); await led.takeFree("dev-a", 3);
  assert.equal(await led.freeLeft("dev-a", 3), 0);
  assert.equal(await led.freeLeft("dev-b", 3), 3);
  assert.equal(await led.freeLeft("dev-a", 0), 0, "FREE_SHEETS 0 = none");
});

test("reports are stored", async () => {
  await led.report({ device: "dev-a", entry: "e1", kind: "cat", style: "fluff", reason: "odd" }, 7);
  assert.deepEqual({ ...sqlite.prepare("SELECT * FROM reports").get() },
    { at: 7, device: "dev-a", entry: "e1", kind: "cat", style: "fluff", reason: "odd" });
});

test("takeFree reserves atomically up to the total and giveFree hands one back", async () => {
  const got = await Promise.all(Array.from({ length: 5 }, () => led.takeFree("dev-a", 3)));
  assert.equal(got.filter(Boolean).length, 3);
  await led.giveFree("dev-a");
  assert.equal(await led.freeLeft("dev-a", 3), 1);
  assert.equal(await led.takeFree("dev-b", 0), false);
});

test("L3: a drawing row is taken over only after 10 minutes (a draw can take 3+)", async () => {
  await led.claim("T", "sheet_fluff", "dev-a", 0);
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 590_000), { go: "busy" });
  assert.deepEqual(await led.claim("T", "sheet_fluff", "dev-a", 601_000), { go: "draw" });
});
