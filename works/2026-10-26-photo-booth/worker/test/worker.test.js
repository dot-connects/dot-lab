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
