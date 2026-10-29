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
    async list({ prefix = "", cursor } = {}) {             // 2 keys per page, so paging is exercised
      const keys = [...m.keys()].filter(k => k.startsWith(prefix)).sort();
      const start = cursor ? +cursor : 0, page = keys.slice(start, start + 2);
      const more = start + 2 < keys.length;
      return { objects: page.map(key => ({ key })), truncated: more, cursor: more ? String(start + 2) : undefined };
    },
    async delete(keys) { for (const k of [].concat(keys)) m.delete(k); },
    m,
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
const call = (e, method, path, { token, body, type, origin = PAGES, ip, dev } = {}) => {
  const headers = { Origin: origin };
  if (ip) headers["CF-Connecting-IP"] = ip;
  if (dev) headers["X-Device"] = dev;
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

test("delete now: only A's or the finished B's token wipes every file and the link", async () => {
  const e = env(); const id = await aDone(e);
  await upAll(e, id, "b", tok("q"), "Q");                                              // abandoned staging
  await upAll(e, id, "b", tok("b"), "B");
  assert.equal((await done(e, id, "b", tok("b"))).status, 204);
  const del = token => call(e, "DELETE", `/s/${id}`, { token });
  assert.equal((await del(undefined)).status, 401);
  assert.equal((await del(tok("q"))).status, 403);                                     // a loser cannot delete
  assert.equal((await del(tok("b"))).status, 204);
  assert.equal([...e.BUCKET.m.keys()].filter(k => k.startsWith(`s/${id}/`)).length, 0);
  assert.equal((await call(e, "GET", `/s/${id}`)).status, 404);
  const id2 = await newLink(e);
  assert.equal((await call(e, "DELETE", `/s/${id2}`, { token: tok("a") })).status, 204);  // A before anyone finished
  const ok = await call(e, "OPTIONS", "/s", { origin: PAGES });
  assert.match(ok.headers.get("Access-Control-Allow-Methods"), /DELETE/);
});

test("A's option travels with the link; B may add a small optional layout.json", async () => {
  const e = env();
  const r = await call(e, "POST", "/s", { body: JSON.stringify({ token: tok("a"), opts: { movable: true, junk: "x" } }), type: "application/json" });
  const { id } = await r.json();
  assert.deepEqual((await (await call(e, "GET", `/s/${id}`)).json()).opts, { movable: true, shape: "wide", ui: "film" });
  await upAll(e, id, "a", tok("a"));
  await done(e, id, "a", tok("a"));
  const lay = JSON.stringify({ a: { x: 0.1, y: -0.05, scale: 1.2 } });
  const put = (name, body, type) => call(e, "PUT", `/s/${id}/b/${name}`, { token: tok("b"), body, type });
  assert.equal((await put("layout.json", lay, "text/plain")).status, 415);
  assert.equal((await put("layout.json", "x".repeat(4097), "application/json")).status, 413);
  assert.equal((await put("layout.json", lay, "application/json")).status, 204);
  assert.equal((await call(e, "PUT", `/s/${id}/a/layout.json`, { token: tok("a"), body: lay, type: "application/json" })).status, 409);  // A is done
  await upAll(e, id, "b", tok("b"));
  assert.equal((await done(e, id, "b", tok("b"))).status, 204);                     // layout is optional for done
  assert.equal(await (await call(e, "GET", `/s/${id}/b/layout.json`)).text(), lay);
  const id2 = await newLink(e);
  assert.deepEqual((await (await call(e, "GET", `/s/${id2}`)).json()).opts, { movable: false, shape: "wide", ui: "film" });  // default: fixed like a booth, wide cells
});

test("layout.json only when A allowed moving, only {a:{x,y,scale}} in range, stored re-serialised", async () => {
  const e = env();
  const fixed = await newLink(e);
  await upAll(e, fixed, "a", tok("a")); await done(e, fixed, "a", tok("a"));
  const lay = JSON.stringify({ a: { x: 0.1, y: -0.05, scale: 1.2 } });
  assert.equal((await call(e, "PUT", `/s/${fixed}/b/layout.json`, { token: tok("b"), body: lay, type: "application/json" })).status, 403);
  const r = await call(e, "POST", "/s", { body: JSON.stringify({ token: tok("a"), opts: { movable: true } }), type: "application/json" });
  const { id } = await r.json();
  await upAll(e, id, "a", tok("a")); await done(e, id, "a", tok("a"));
  const put = body => call(e, "PUT", `/s/${id}/b/layout.json`, { token: tok("b"), body, type: "application/json" });
  for (const bad of ["{", "[]", '{"a":{"x":"1","y":0,"scale":1}}', '{"a":{"x":3,"y":0,"scale":1}}', '{"a":{"x":0,"y":0,"scale":9}}'])
    assert.equal((await put(bad)).status, 400, bad);
  assert.equal((await put('{"a":{"x":0.25,"y":0,"scale":1.5},"evil":"<script>"}')).status, 204);
  await upAll(e, id, "b", tok("b")); await done(e, id, "b", tok("b"));
  assert.equal(await (await call(e, "GET", `/s/${id}/b/layout.json`)).text(), '{"a":{"x":0.25,"y":0,"scale":1.5}}');
});

test("pairs.json: B's 4 cells say which of A's cuts each pairs with; 4 ints 0..3, any link", async () => {
  const e = env(); const id = await aDone(e);                                    // a fixed link: pairs still allowed
  const put = body => call(e, "PUT", `/s/${id}/b/pairs.json`, { token: tok("b"), body, type: "application/json" });
  for (const bad of ["[0,1,2]", "[0,1,2,4]", "[0,1,2,1.5]", '{"a":1}', "x"]) assert.equal((await put(bad)).status, 400, bad);
  assert.equal((await put("[2, 0, 0, 3]")).status, 204);
  assert.equal((await call(e, "PUT", `/s/${id}/a/pairs.json`, { token: tok("a"), body: "[0,1,2,3]", type: "application/json" })).status, 409);
  await upAll(e, id, "b", tok("b")); await done(e, id, "b", tok("b"));
  assert.equal(await (await call(e, "GET", `/s/${id}/b/pairs.json`)).text(), "[2,0,0,3]");
});

test("look.json: each side's face retouch, ints 0..2 for skin/slim/eye, stored re-serialised", async () => {
  const e = env(); const id = await newLink(e);
  const put = (side, token, body) => call(e, "PUT", `/s/${id}/${side}/look.json`, { token, body, type: "application/json" });
  for (const bad of ['{"skin":3,"slim":0,"eye":0}', '{"skin":1,"slim":-1,"eye":0}', '{"skin":1,"slim":1}', '{"skin":1.5,"slim":0,"eye":0}', "[1,1,1]", "x"])
    assert.equal((await put("a", tok("a"), bad)).status, 400, bad);
  assert.equal((await put("a", tok("a"), '{"skin":2,"slim":1,"eye":0,"x":"<script>"}')).status, 204);
  await upAll(e, id, "a", tok("a")); await done(e, id, "a", tok("a"));
  assert.equal(await (await call(e, "GET", `/s/${id}/a/look.json`)).text(), '{"skin":2,"slim":1,"eye":0}');
  assert.equal((await put("b", tok("b"), '{"skin":1,"slim":1,"eye":1}')).status, 204);
  await upAll(e, id, "b", tok("b")); await done(e, id, "b", tok("b"));
  assert.equal(await (await call(e, "GET", `/s/${id}/b/look.json`)).text(), '{"skin":1,"slim":1,"eye":1}');
});

test("shape: A's 가로/세로 pick travels with the link, enum only, legacy links read as wide", async () => {
  const e = env();
  const mk = opts => call(e, "POST", "/s", { body: JSON.stringify({ token: tok("a"), opts }), type: "application/json" });
  for (const bad of ["square", "TALL", "", 1, null, ["tall"], { v: "tall" }]) assert.equal((await mk({ shape: bad })).status, 400, JSON.stringify(bad));
  const r = await mk({ shape: "tall", movable: true, junk: "<script>" });
  assert.equal(r.status, 201);
  const { id } = await r.json();
  assert.deepEqual((await (await call(e, "GET", `/s/${id}`)).json()).opts, { movable: true, shape: "tall", ui: "film" });
  assert.equal(new TextDecoder().decode(e.BUCKET.m.get(`s/${id}/meta.json`).body).includes("junk"), false);   // stored re-serialised
  const w = await (await mk({ shape: "wide" })).json();
  assert.deepEqual((await (await call(e, "GET", `/s/${w.id}`)).json()).opts, { movable: false, shape: "wide", ui: "film" });
  // a link made before shapes existed (meta.opts without shape, or no opts at all) opens as wide
  for (const opts of [{ movable: true }, undefined]) {
    const old = await newLink(e), k = `s/${old}/meta.json`, m = JSON.parse(new TextDecoder().decode(e.BUCKET.m.get(k).body));
    if (opts) m.opts = opts; else delete m.opts;
    await e.BUCKET.put(k, JSON.stringify(m));
    assert.deepEqual((await (await call(e, "GET", `/s/${old}`)).json()).opts, { movable: !!opts, shape: "wide", ui: "film" });
  }
});

test("ui: A's 필름/귀여운 theme travels with the link like shape, enum only, legacy links read as film", async () => {
  const e = env();
  const mk = opts => call(e, "POST", "/s", { body: JSON.stringify({ token: tok("a"), opts }), type: "application/json" });
  for (const bad of ["Cute", "pink", "", 1, null, ["cute"], { v: "cute" }]) assert.equal((await mk({ ui: bad })).status, 400, JSON.stringify(bad));
  const r = await mk({ ui: "cute", shape: "tall", junk: "<script>" });
  assert.equal(r.status, 201);
  const { id } = await r.json();
  assert.deepEqual((await (await call(e, "GET", `/s/${id}`)).json()).opts, { movable: false, shape: "tall", ui: "cute" });
  assert.equal(new TextDecoder().decode(e.BUCKET.m.get(`s/${id}/meta.json`).body).includes("junk"), false);
  const f = await (await mk({ ui: "film" })).json();
  assert.deepEqual((await (await call(e, "GET", `/s/${f.id}`)).json()).opts, { movable: false, shape: "wide", ui: "film" });
  // a link made before themes existed opens as film
  const old = await newLink(e), k = `s/${old}/meta.json`, m = JSON.parse(new TextDecoder().decode(e.BUCKET.m.get(k).body));
  m.opts = { movable: true, shape: "tall" };
  await e.BUCKET.put(k, JSON.stringify(m));
  assert.deepEqual((await (await call(e, "GET", `/s/${old}`)).json()).opts, { movable: true, shape: "tall", ui: "film" });
});

// Round 8 D: the same phone opening its own link in another browser (Chrome -> KakaoTalk in-app) has no localStorage
// role; the Worker recognises it by (IP, screen) and says whose link it is.
const PHONE = "412x915@2.625|android", PHONE2 = "390x844@3|ios";
async function linkFrom(e, ip, dev) {
  const r = await call(e, "POST", "/s", { body: JSON.stringify({ token: tok("a") }), type: "application/json", ip, dev });
  assert.equal(r.status, 201); return (await r.json()).id;
}
const mine = async (e, id, ip, dev) => (await (await call(e, "GET", `/s/${id}`, { ip, dev })).json()).mine;

test("status says mine=a only for the creator's IP + screen", async () => {
  const e = env(); const id = await linkFrom(e, "1.2.3.4", PHONE);
  assert.equal(await mine(e, id, "1.2.3.4", PHONE), "a");
  assert.equal(await mine(e, id, "1.2.3.5", PHONE), null);              // another network
  assert.equal(await mine(e, id, "1.2.3.4", PHONE2), null);             // another phone on the same Wi-Fi
  assert.equal(await mine(e, id, "1.2.3.4", undefined), null);          // no device header
});

test("IPv6 compares the /64 network, so a rotated privacy address still matches", async () => {
  const e = env(); const id = await linkFrom(e, "2001:db8:aa:1::5", PHONE);
  assert.equal(await mine(e, id, "2001:0db8:00aa:0001:9:8:7:6", PHONE), "a");
  assert.equal(await mine(e, id, "2001:db8:aa:2::5", PHONE), null);
});

test("a link made without a device header never matches", async () => {
  const e = env(); const id = await linkFrom(e, "1.2.3.4", undefined);
  assert.equal(await mine(e, id, "1.2.3.4", undefined), null);
  assert.equal(await mine(e, id, "1.2.3.4", PHONE), null);
});

test("B's done records B's device, so B's own link reads mine=b after B is done", async () => {
  const e = env(); const id = await linkFrom(e, "1.2.3.4", PHONE);
  await call(e, "PUT", `/s/${id}/a/1-raw.jpg`, { token: tok("a"), body: "x", type: "image/jpeg" });
  await upAll(e, id, "a", tok("a"));
  assert.equal((await done(e, id, "a", tok("a"))).status, 204);
  await upAll(e, id, "b", tok("b"));
  assert.equal(await mine(e, id, "9.9.9.9", PHONE2), null);             // not before B is done
  const r = await call(e, "POST", `/s/${id}/b/done`, { token: tok("b"), ip: "9.9.9.9", dev: PHONE2 });
  assert.equal(r.status, 204);
  assert.equal(await mine(e, id, "9.9.9.9", PHONE2), "b");
  assert.equal(await mine(e, id, "1.2.3.4", PHONE), "a");
});

test("CORS lets the page send X-Device", async () => {
  const r = await call(env(), "OPTIONS", "/s");
  assert.match(r.headers.get("Access-Control-Allow-Headers"), /X-Device/);
});
