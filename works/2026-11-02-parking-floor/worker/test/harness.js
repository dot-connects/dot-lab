// Local D1 (Miniflare) for Worker tests: the store runs real SQL, so conditional writes are tested as deployed.
import { Miniflare } from "miniflare";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { D1Store } from "../src/store.js";
import { handle } from "../src/index.js";

const root = fileURLToPath(new URL("..", import.meta.url));
const base = "https://parking.test";

/** Splits a SQL file into statements: comment lines dropped, split on ';'. */
export function statements(text) {
  return text.split("\n").filter(l => !l.trim().startsWith("--")).join("\n")
    .split(";").map(s => s.trim()).filter(Boolean);
}

export async function setup({ sql = ["schema.sql"] } = {}) {
  const mf = new Miniflare({ modules: true, script: "export default {}", d1Databases: ["DB"] });
  const db = await mf.getD1Database("DB");
  for (const f of sql) for (const s of statements(readFileSync(root + f, "utf8"))) await db.prepare(s).run();
  const store = new D1Store(db);
  const objects = new Map();
  const env = {
    DB: db, LINK_BASE: `${base}/j`, ASSETLINKS_SHA256: "AA:BB, CC:DD",
    PHOTOS: {
      async put(k, v) { objects.set(k, v); },
      async get(k) { const v = objects.get(k); return v ? { body: v } : null; },
      async delete(k) { objects.delete(k); },
      async list({ prefix }) { return { objects: [...objects.keys()].filter(k => k.startsWith(prefix)).map(key => ({ key })), truncated: false }; },
    },
  };
  async function call(method, path, payload, token, headers = {}) {
    const h = { ...headers };
    if (token) h.authorization = `Bearer ${token}`;
    if (payload && !(payload instanceof Uint8Array)) h["content-type"] = "application/json";
    const body = payload instanceof Uint8Array ? payload : payload == null ? undefined : JSON.stringify(payload);
    const waits = [];
    const res = await handle(new Request(base + path, { method, headers: h, body }), env, store, { waitUntil: p => waits.push(p) });
    await Promise.all(waits);
    return { status: res.status, body: res.headers.get("content-type")?.includes("json") ? await res.json() : await res.text(), res, waits: waits.length };
  }
  async function reset() {
    const t = await db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '_cf_%'").all();
    for (const r of t.results) await db.prepare(`DELETE FROM "${r.name}"`).run();
    objects.clear();
  }
  return { mf, db, store, env, objects, call, reset, close: () => mf.dispose() };
}
