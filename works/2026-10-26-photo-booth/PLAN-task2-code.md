# Task 2 code (PLAN.md) — tests and Worker, verbatim

Checked 2026-10-26: 5/5 pass on Node 24; dropping the `onlyIf` option fails "the first finished friend wins".

## worker/test/worker.test.js

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function fakeR2() {
  const m = new Map(); let n = 0;
  const obj = v => ({ body: v.body, etag: v.etag, httpMetadata: v.meta,
    async json() { return JSON.parse(new TextDecoder().decode(v.body)); } });
  return {
    async get(k) { const v = m.get(k); return v ? obj(v) : null; },
    async head(k) { const v = m.get(k); return v ? { etag: v.etag, size: v.body.byteLength } : null; },
    async put(k, body, opt = {}) {
      await null;                                   // yield, so concurrent callers interleave
      const cur = m.get(k);
      if (opt.onlyIf?.etagMatches && (!cur || cur.etag !== opt.onlyIf.etagMatches)) return null;
      const b = typeof body === "string" ? new TextEncoder().encode(body) : new Uint8Array(await new Response(body).arrayBuffer());
      const v = { body: b, meta: opt.httpMetadata, etag: String(++n) }; m.set(k, v); return { etag: v.etag };
    },
  };
}
const PAGES = "https://dot-connects.github.io";
const env = () => ({ BUCKET: fakeR2(), ORIGINS: `${PAGES},http://127.0.0.1:8799` });
const tok = c => c.repeat(43);
const call = (e, method, path, { token, body, type, origin = PAGES } = {}) => {
  const headers = { Origin: origin };
  if (token) headers.Authorization = "Bearer " + token;
  if (type) headers["Content-Type"] = type;
  return worker.fetch(new Request("https://w.test" + path, { method, body, headers }), e);
};
const FILES = [1, 2, 3, 4].flatMap(c => [[`${c}-raw.jpg`, "image/jpeg"], [`${c}-cut.png`, "image/png"], [`${c}-clip`, "video/mp4"]]);
async function upAll(e, id, side, token, mark = "x") {
  for (const [n, t] of FILES) assert.equal((await call(e, "PUT", `/s/${id}/${side}/${n}`, { token, body: mark, type: t })).status, 204);
}
const done = (e, id, side, token) => call(e, "POST", `/s/${id}/${side}/done`, { token });
async function newLink(e) {
  const r = await call(e, "POST", "/s", { body: JSON.stringify({ token: tok("a") }), type: "application/json" });
  assert.equal(r.status, 201); return (await r.json()).id;
}
async function aDone(e) {
  const id = await newLink(e); await upAll(e, id, "a", tok("a"));
  assert.equal((await done(e, id, "a", tok("a"))).status, 204);
  assert.equal((await done(e, id, "a", tok("a"))).status, 204);                       // lost response, retried
  return id;
}

test("A then B: B's files show only after B is done, B's done is idempotent", async () => {
  const e = env(); const id = await aDone(e);
  assert.equal(id.length, 22);
  await upAll(e, id, "b", tok("b"), "B");
  assert.equal((await call(e, "GET", `/s/${id}/b/1-clip`)).status, 404);              // not claimed yet
  assert.equal((await done(e, id, "b", tok("b"))).status, 204);
  assert.equal((await done(e, id, "b", tok("b"))).status, 204);                       // retry
  const st = await (await call(e, "GET", `/s/${id}`)).json();
  assert.deepEqual([st.a.done, st.b.done], [true, true]);
  const f = await call(e, "GET", `/s/${id}/b/1-clip`);
  assert.equal(f.status, 200);
  assert.equal(await f.text(), "B");
  assert.equal(f.headers.get("Content-Type"), "video/mp4");
  assert.equal(f.headers.get("Cache-Control"), "no-store");
});

test("an abandoned B upload never blocks the slot; the first finished friend wins", async () => {
  const e = env(); const id = await aDone(e);
  await call(e, "PUT", `/s/${id}/b/1-raw.jpg`, { token: tok("q"), body: "x", type: "image/jpeg" });   // q gives up
  await upAll(e, id, "b", tok("b"), "B");
  await upAll(e, id, "b", tok("c"), "C");
  const rs = await Promise.all([done(e, id, "b", tok("b")), done(e, id, "b", tok("c"))]);
  assert.deepEqual(rs.map(r => r.status).sort(), [204, 409]);
  const winner = rs[0].status === 204 ? "B" : "C";
  assert.equal(await (await call(e, "GET", `/s/${id}/b/4-cut.png`)).text(), winner);
  assert.equal((await call(e, "PUT", `/s/${id}/b/1-raw.jpg`, { token: tok("q"), body: "x", type: "image/jpeg" })).status, 409);
});

test("B cannot upload before A is done; done names the missing files", async () => {
  const e = env(); const id = await newLink(e);
  assert.equal((await call(e, "PUT", `/s/${id}/b/1-raw.jpg`, { token: tok("b"), body: "x", type: "image/jpeg" })).status, 409);
  await call(e, "PUT", `/s/${id}/a/1-raw.jpg`, { token: tok("a"), body: "x", type: "image/jpeg" });
  const r = await done(e, id, "a", tok("a"));
  assert.equal(r.status, 400);
  assert.equal((await r.json()).names.length, 11);
});

test("write checks: token, side, name, type, size, finished side", async () => {
  const e = env(); const id = await newLink(e);
  const put = (side, name, opt) => call(e, "PUT", `/s/${id}/${side}/${name}`, { body: "x", ...opt });
  assert.equal((await put("a", "1-raw.jpg", { type: "image/jpeg" })).status, 401);
  assert.equal((await put("a", "1-raw.jpg", { token: tok("z"), type: "image/jpeg" })).status, 403);
  assert.equal((await put("a", "evil.html", { token: tok("a"), type: "text/html" })).status, 400);
  assert.equal((await put("a", "5-raw.jpg", { token: tok("a"), type: "image/jpeg" })).status, 400);
  assert.equal((await put("a", "1-raw.jpg", { token: tok("a"), type: "text/html" })).status, 415);
  assert.equal((await put("a", "1-clip", { token: tok("a"), type: "video/webm;codecs=vp9" })).status, 204);
  assert.equal((await put("a", "1-clip", { token: tok("a"), type: "video/mp4", body: new Uint8Array(0) })).status, 400);
  assert.equal((await put("a", "1-clip", { token: tok("a"), type: "video/mp4", body: new Uint8Array(3 * 1024 * 1024 + 1) })).status, 413);
  await upAll(e, id, "a", tok("a"));
  await done(e, id, "a", tok("a"));
  assert.equal((await put("a", "1-raw.jpg", { token: tok("a"), type: "image/jpeg" })).status, 409);
  assert.equal((await call(e, "POST", "/s", { body: JSON.stringify({ token: "short" }), type: "application/json" })).status, 400);
});

test("expiry, unknown ids, CORS", async () => {
  const e = env(); const id = await newLink(e);
  e.NOW = Date.now() + 7 * 864e5 + 1000;
  assert.equal((await call(e, "GET", `/s/${id}`)).status, 410);
  assert.equal((await call(e, "GET", "/s/AAAAAAAAAAAAAAAAAAAAAA")).status, 404);
  const ok = await call(e, "OPTIONS", "/s", { origin: "http://127.0.0.1:8799" });
  assert.equal(ok.headers.get("Access-Control-Allow-Origin"), "http://127.0.0.1:8799");
  assert.match(ok.headers.get("Access-Control-Allow-Headers"), /Content-Type/);
  const bad = await call(e, "OPTIONS", "/s", { origin: "https://evil.test" });
  assert.equal(bad.headers.get("Access-Control-Allow-Origin"), PAGES);
});
```

## worker/src/index.js

```js
const LIFE = 7 * 864e5, MAX_FILE = 3 * 1024 * 1024;
const NAMES = [1, 2, 3, 4].flatMap(c => [`${c}-raw.jpg`, `${c}-cut.png`, `${c}-clip`]);
const typeOk = (name, t) => name.endsWith(".jpg") ? t === "image/jpeg" : name.endsWith(".png") ? t === "image/png"
  : t === "video/mp4" || t === "video/webm";
const b64u = a => btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const sha = async s => b64u(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))));
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
// B uploads into a staging folder named by its token hash; `done` claims the B slot, so an abandoned upload never blocks it
const bDir = (id, hash) => `s/${id}/b-${hash.slice(0, 16)}`;

export default {
  async fetch(req, env) {
    const allowed = env.ORIGINS.split(",");
    const origin = req.headers.get("Origin");
    const cors = { "Access-Control-Allow-Origin": allowed.includes(origin) ? origin : allowed[0], "Vary": "Origin",
      "Access-Control-Allow-Headers": "Authorization, Content-Type", "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
      "Access-Control-Max-Age": "86400" };
    const json = (status, obj) => new Response(obj === undefined ? null : JSON.stringify(obj),
      { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const now = env.NOW ?? Date.now();
    const p = new URL(req.url).pathname.split("/").filter(Boolean);
    if (p[0] !== "s") return json(404, { error: "not-found" });

    if (req.method === "POST" && p.length === 1) {
      let token = null;
      try { token = (await req.json()).token; } catch {}
      if (!TOKEN.test(token || "")) return json(400, { error: "token" });
      const id = b64u(crypto.getRandomValues(new Uint8Array(16)));
      await env.BUCKET.put(`s/${id}/meta.json`, JSON.stringify({ created: now,
        a: { tokenHash: await sha(token), done: false }, b: { tokenHash: null, done: false } }));
      return json(201, { id, expires: now + LIFE });
    }

    const id = p[1];
    if (!/^[A-Za-z0-9_-]{22}$/.test(id || "")) return json(404, { error: "not-found" });
    const key = `s/${id}/meta.json`;
    const load = async () => { const o = await env.BUCKET.get(key); return o && { meta: await o.json(), etag: o.etag }; };
    const store = (meta, etag) => env.BUCKET.put(key, JSON.stringify(meta), { onlyIf: { etagMatches: etag } });
    let cur = await load();
    if (!cur) return json(404, { error: "not-found" });
    if (now - cur.meta.created > LIFE) return json(410, { error: "expired" });
    const meta = cur.meta;

    if (req.method === "GET" && p.length === 2)
      return json(200, { a: { done: meta.a.done }, b: { done: meta.b.done }, expires: meta.created + LIFE });

    const side = p[2];
    if ((side !== "a" && side !== "b") || p.length !== 4) return json(404, { error: "not-found" });

    if (req.method === "GET") {
      if (!NAMES.includes(p[3])) return json(400, { error: "name" });
      if (side === "b" && !meta.b.done) return json(404, { error: "not-found" });
      const dir = side === "a" ? `s/${id}/a` : bDir(id, meta.b.tokenHash);
      const o = await env.BUCKET.get(`${dir}/${p[3]}`);
      if (!o) return json(404, { error: "not-found" });
      return new Response(o.body, { headers: { ...cors, "Cache-Control": "no-store",
        "Content-Type": o.httpMetadata?.contentType || "application/octet-stream" } });
    }

    const auth = (req.headers.get("Authorization") || "").replace(/^Bearer /, "");
    if (!TOKEN.test(auth)) return json(401, { error: "token" });
    const h = await sha(auth);
    let dir;
    if (side === "a") {
      if (h !== meta.a.tokenHash) return json(403, { error: "token" });
      if (meta.a.done) return p[3] === "done" ? json(204) : json(409, { error: "done" });
      dir = `s/${id}/a`;
    } else {
      if (!meta.a.done) return json(409, { error: "not-ready" });
      if (meta.b.done) return meta.b.tokenHash === h && p[3] === "done" ? json(204) : json(409, { error: meta.b.tokenHash === h ? "done" : "taken" });
      dir = bDir(id, h);
    }

    if (req.method === "POST" && p[3] === "done") {
      const missing = [];
      for (const n of NAMES) if (!(await env.BUCKET.head(`${dir}/${n}`))) missing.push(n);
      if (missing.length) return json(400, { error: "missing", names: missing });
      for (let i = 0; i < 3; i++) {                        // compare-and-set on meta.json
        if (side === "b" && cur.meta.b.done) return cur.meta.b.tokenHash === h ? json(204) : json(409, { error: "taken" });
        if (side === "a") cur.meta.a.done = true; else cur.meta.b = { tokenHash: h, done: true };
        if (await store(cur.meta, cur.etag)) return json(204);
        cur = await load();
        if (!cur) return json(404, { error: "not-found" });
      }
      return json(409, { error: "busy" });
    }

    if (req.method === "PUT") {
      if (!NAMES.includes(p[3])) return json(400, { error: "name" });
      const type = (req.headers.get("Content-Type") || "").split(";")[0].trim();
      if (!typeOk(p[3], type)) return json(415, { error: "type" });
      const body = new Uint8Array(await req.arrayBuffer());
      if (body.byteLength === 0) return json(400, { error: "empty" });
      if (body.byteLength > MAX_FILE) return json(413, { error: "file-too-big" });
      await env.BUCKET.put(`${dir}/${p[3]}`, body, { httpMetadata: { contentType: type } });
      return json(204);
    }
    return json(405, { error: "method" });
  },
};
```
