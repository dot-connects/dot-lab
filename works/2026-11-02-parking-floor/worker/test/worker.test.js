import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { handle, newCode, normCode } from "../src/index.js";
import { MemoryStore } from "../src/store.js";
import { resetTokenCache } from "../src/fcm.js";

const BASE = "https://p.test";
let store, env, sent;

async function call(method, path, body, token) {
  const headers = { "content-type": "application/json" };
  if (token) headers.authorization = `Bearer ${token}`;
  const req = new Request(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const waits = [];
  const res = await handle(req, env, store, { waitUntil: p => waits.push(p) });
  await Promise.all(waits);
  return { status: res.status, body: await res.json() };
}

async function serviceAccount() {
  const kp = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", kp.privateKey));
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...der))}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({ client_email: "sa@p.iam.gserviceaccount.com", private_key: pem, project_id: "parking-test" });
}

beforeEach(async () => {
  store = new MemoryStore();
  sent = [];
  resetTokenCache();
  env = { FCM_SA: await serviceAccount() };
  globalThis.fetch = async (url, init) => {
    if (url.startsWith("https://oauth2.googleapis.com/token")) {
      assert.match(init.body, /assertion=[\w-]+\.[\w-]+\.[\w-]+$/);
      return new Response(JSON.stringify({ access_token: "at", expires_in: 3600 }));
    }
    const msg = JSON.parse(init.body).message;
    if (msg.token === "dead") return new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 });
    sent.push(msg);
    return new Response("{}");
  };
});

async function family() {
  const mom = (await call("POST", "/v1/groups", { name: "엄마" })).body;
  const code = (await call("POST", "/v1/invites", null, mom.token)).body.code;
  const dad = (await call("POST", "/v1/join", { code, name: "아빠" })).body;
  return { mom, dad, code };
}

const park = (over = {}) => ({ id: 1000, atMs: 1000, floor: -2, est: -2, alt: -3, confirmed: false, pillar: "", color: 0, placeName: "스타필드 수원", ...over });

test("codes look like XXXX-XXXX-XXXX and normalise", () => {
  const c = newCode();
  assert.match(c, /^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.equal(normCode(" abcd-efgh-jkmn "), "ABCDEFGHJKMN");
});

test("an invite joins once, then is gone", async () => {
  const { dad, code } = await family();
  assert.equal(dad.members.length, 2);
  const again = await call("POST", "/v1/join", { code, name: "동생" });
  assert.equal(again.status, 404);
});

test("expired invites do not join", async () => {
  const mom = (await call("POST", "/v1/groups", { name: "엄마" })).body;
  const code = (await call("POST", "/v1/invites", null, mom.token)).body.code;
  const real = Date.now;
  Date.now = () => real() + 25 * 60 * 60 * 1000;
  try {
    assert.equal((await call("POST", "/v1/join", { code, name: "아빠" })).status, 404);
  } finally {
    Date.now = real;
  }
});

test("a park reaches the other phone by push and by pull", async () => {
  const { mom, dad } = await family();
  await call("PUT", "/v1/token", { fcmToken: "dad-phone" }, dad.token);
  await call("PUT", "/v1/token", { fcmToken: "mom-phone" }, mom.token);
  const r = await call("PUT", "/v1/park", { park: park() }, mom.token);
  assert.equal(r.body.ok, true);
  assert.deepEqual(sent.map(m => m.token), ["dad-phone"]);
  assert.equal(sent[0].data.type, "park");
  assert.equal(sent[0].android.priority, "high");
  const pulled = (await call("GET", "/v1/park", null, dad.token)).body;
  assert.equal(pulled.park.floor, -2);
  assert.equal(pulled.park.by, "엄마");
  assert.equal(pulled.park.mine, false);
});

test("an older park does not replace a newer one; an update of the same park does", async () => {
  const { mom, dad } = await family();
  await call("PUT", "/v1/park", { park: park({ id: 2000, atMs: 2000 }) }, dad.token);
  const old = await call("PUT", "/v1/park", { park: park({ id: 1000, atMs: 1000 }) }, mom.token);
  assert.equal(old.body.stale, true);
  await call("PUT", "/v1/park", { park: park({ id: 2000, atMs: 2000, confirmed: true, floor: -3, pillar: "C-12" }) }, mom.token);
  const cur = (await call("GET", "/v1/park", null, mom.token)).body.park;
  assert.equal(cur.floor, -3);
  assert.equal(cur.pillar, "C-12");
  assert.equal(cur.by, "아빠"); // who parked stays the driver
});

test("coordinates and unknown fields are dropped", async () => {
  const { mom } = await family();
  await call("PUT", "/v1/park", { park: { ...park(), lat: 37.1, lng: 127.1, secret: "x" } }, mom.token);
  const cur = (await call("GET", "/v1/park", null, mom.token)).body.park;
  assert.equal(cur.lat, undefined);
  assert.equal(cur.secret, undefined);
});

test("bad or missing credentials are refused", async () => {
  const { mom } = await family();
  assert.equal((await call("GET", "/v1/park")).status, 401);
  const forged = mom.token.slice(0, 17) + "0".repeat(64);
  assert.equal((await call("GET", "/v1/park", null, forged)).status, 401);
});

test("a dead push token is cleared", async () => {
  const { mom, dad } = await family();
  await call("PUT", "/v1/token", { fcmToken: "dead" }, dad.token);
  await call("PUT", "/v1/park", { park: park() }, mom.token);
  assert.equal((await store.member(dad.memberId)).fcmToken, null);
});

test("only the owner removes others; anyone can leave", async () => {
  const { mom, dad } = await family();
  assert.equal((await call("DELETE", `/v1/members/${mom.memberId}`, null, dad.token)).status, 403);
  assert.equal((await call("DELETE", `/v1/members/${dad.memberId}`, null, dad.token)).status, 200);
  assert.equal((await call("GET", "/v1/park", null, dad.token)).status, 401);
});

test("without a service account nothing is pushed but the park is stored", async () => {
  const { mom, dad } = await family();
  delete env.FCM_SA;
  await call("PUT", "/v1/token", { fcmToken: "dad-phone" }, dad.token);
  await call("PUT", "/v1/park", { park: park() }, mom.token);
  assert.equal(sent.length, 0);
  assert.equal((await call("GET", "/v1/park", null, dad.token)).body.park.floor, -2);
});

test("a park dated in the future is refused", async () => {
  const { mom } = await family();
  const r = await call("PUT", "/v1/park", { park: park({ id: 9, atMs: Date.now() + 3600_000 }) }, mom.token);
  assert.equal(r.status, 400);
});

test("a full family keeps the invite open", async () => {
  const mom = (await call("POST", "/v1/groups", { name: "m" })).body;
  for (let i = 0; i < 7; i++) {
    const c = (await call("POST", "/v1/invites", null, mom.token)).body.code;
    assert.equal((await call("POST", "/v1/join", { code: c, name: `k${i}` })).status, 200);
  }
  const code = (await call("POST", "/v1/invites", null, mom.token)).body.code;
  assert.equal((await call("POST", "/v1/join", { code, name: "x" })).status, 409);
  const kid = (await store.members((await store.member(mom.memberId)).groupId))[1];
  await store.removeMember(kid.groupId, kid.id);
  assert.equal((await call("POST", "/v1/join", { code, name: "x" })).status, 200);
});

test("only the newest invite works", async () => {
  const mom = (await call("POST", "/v1/groups", { name: "m" })).body;
  const first = (await call("POST", "/v1/invites", null, mom.token)).body.code;
  await call("POST", "/v1/invites", null, mom.token);
  assert.equal((await call("POST", "/v1/join", { code: first, name: "x" })).status, 404);
});

test("the owner leaving hands over; the last one out clears the family", async () => {
  const { mom, dad } = await family();
  await call("PUT", "/v1/park", { park: park() }, mom.token);
  await call("DELETE", `/v1/members/${mom.memberId}`, null, mom.token);
  assert.equal((await store.member(dad.memberId)).owner, true);
  const gid = (await store.member(dad.memberId)).groupId;
  await call("DELETE", `/v1/members/${dad.memberId}`, null, dad.token);
  assert.equal(await store.park(gid), null);
});

test("an oversized body is refused", async () => {
  const { mom } = await family();
  const r = await call("PUT", "/v1/park", { park: park({ placeName: "x".repeat(5000) }) }, mom.token);
  assert.ok(r.status === 413 || r.status === 400);
});
