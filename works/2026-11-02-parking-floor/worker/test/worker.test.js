import { test, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { handle, newCode, normCode, scheduled } from "../src/index.js";
import { resetTokenCache } from "../src/fcm.js";
import { setup } from "./harness.js";

const BASE = "https://p.test";
let h, store, db, env, call, sent, realFetch;

async function serviceAccount() {
  const kp = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", kp.privateKey));
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...der))}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({ client_email: "sa@p.iam.gserviceaccount.com", private_key: pem, project_id: "parking-test" });
}
const SA = serviceAccount();

before(async () => { h = await setup(); ({ store, db, env, call } = h); realFetch = globalThis.fetch; });
after(async () => { globalThis.fetch = realFetch; await h?.close(); });
beforeEach(async () => {
  await h.reset();
  for (const k of ["MAINTENANCE", "SMOKE_KEY", "LIMIT"]) delete env[k];
  sent = [];
  resetTokenCache();
  env.FCM_SA = await SA;
  globalThis.fetch = async (url, init) => {
    if (typeof url !== "string" || !url.startsWith("https://")) return realFetch(url, init);
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

test("maintenance gates every API version and only accepts the configured smoke key", async () => {
  env.MAINTENANCE = "1";
  env.SMOKE_KEY = "local-smoke";
  for (const path of ["/v1/park", "/v2/state", "/v3/state"]) {
    for (const key of [null, "wrong"]) {
      const headers = key ? { "x-smoke": key } : {};
      const res = await handle(new Request(BASE + path, { headers }), env, store);
      assert.equal(res.status, 503);
      assert.equal(res.headers.get("retry-after"), "600");
      assert.deepEqual(await res.json(), { error: "maintenance" });
    }
  }
  const request = key => new Request(BASE + "/v2/state", { headers: { "x-smoke": key } });
  assert.equal((await handle(request("local-smoke"), env, store)).status, 401);
  for (const key of [undefined, ""]) {
    env.SMOKE_KEY = key;
    assert.equal((await handle(request("local-smoke"), env, store)).status, 503);
    assert.equal((await handle(request(""), env, store)).status, 503);
  }
  env.MAINTENANCE = "0";
  assert.equal((await handle(request("wrong"), env, store)).status, 401);
});

test("maintenance keeps public join, request and asset links available", async () => {
  env.MAINTENANCE = "1";
  for (const path of ["/j", "/r", "/.well-known/assetlinks.json"]) {
    assert.equal((await handle(new Request(BASE + path), env, store)).status, 200);
  }
});

test("join and request pages say 그룹, not 가족", async () => {
  for (const path of ["/j", "/r"]) {
    const html = await (await handle(new Request(BASE + path), env, store)).text();
    assert.ok(html.includes("그룹")); assert.ok(!html.includes("가족"));
  }
});

test("v1 is closed with 410 update, authenticated or not", async () => {
  const mom = (await call("POST", "/v2/groups", { name: "엄마", groupName: "집" })).body;
  for (const [method, path, token] of [["POST", "/v1/groups"], ["POST", "/v1/join"], ["PUT", "/v1/park", mom.token], ["GET", "/v1/park", mom.token], ["DELETE", `/v1/members/${mom.memberId}`, mom.token]]) {
    const r = await call(method, path, null, token);
    assert.equal(r.status, 410); assert.deepEqual(r.body, { error: "update" });
  }
  assert.ok(await store.device(mom.memberId));
});

test("codes look like XXXX-XXXX-XXXX and normalise", () => {
  const c = newCode();
  assert.match(c, /^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.equal(normCode(" abcd-efgh-jkmn "), "ABCDEFGHJKMN");
});

async function family() {
  const mom = (await call("POST", "/v2/groups", { name: "엄마", groupName: "집" })).body;
  const code = (await call("POST", "/v2/invites", null, mom.token)).body.code;
  const dad = (await call("POST", "/v2/join", { code, name: "아빠" })).body;
  await call("PUT", "/v2/cars/c_00000001", { car: { id: "c_00000001", name: "흰 차", color: 0, plate: null, order: 0, updated: 1 } }, mom.token);
  return { mom, dad };
}
const park = (over = {}) => ({ park: { id: 1000, carId: "c_00000001", atMs: 1000, floor: "B2", state: "PARKED", ...over } });

test("a park reaches the other phone by push and by pull; a dead token is cleared", async () => {
  const { mom, dad } = await family();
  await call("PUT", "/v2/token", { fcmToken: "dad-phone" }, dad.token);
  await call("PUT", "/v2/token", { fcmToken: "mom-phone" }, mom.token);
  assert.equal((await call("PUT", "/v2/cars/c_00000001/park", park(), mom.token)).status, 200);
  assert.deepEqual(sent.map(m => m.token), ["dad-phone"]);
  assert.equal(sent[0].data.type, "park");
  assert.equal(sent[0].android.priority, "high");
  const pulled = (await call("GET", "/v2/state", null, dad.token)).body.parks.c_00000001;
  assert.equal(pulled.by, "엄마");
  assert.equal(pulled.mine, false);
  await call("PUT", "/v2/token", { fcmToken: "dead" }, dad.token);
  await call("PUT", "/v2/cars/c_00000001/park", park({ id: 2000, atMs: 2000 }), mom.token);
  assert.equal((await store.device(dad.memberId)).fcmToken, null);
});

test("bad or missing credentials are refused", async () => {
  const { mom } = await family();
  assert.equal((await call("GET", "/v2/state")).status, 401);
  const forged = mom.token.slice(0, 17) + "0".repeat(64);
  assert.equal((await call("GET", "/v2/state", null, forged)).status, 401);
});

test("without a service account nothing is pushed but the park is stored", async () => {
  const { mom, dad } = await family();
  delete env.FCM_SA;
  await call("PUT", "/v2/token", { fcmToken: "dad-phone" }, dad.token);
  await call("PUT", "/v2/cars/c_00000001/park", park(), mom.token);
  assert.equal(sent.length, 0);
  assert.equal((await call("GET", "/v2/state", null, dad.token)).body.parks.c_00000001.floor, "B2");
});

test("a service account pasted with a BOM still signs", async () => {
  const { mom, dad } = await family();
  env.FCM_SA = "﻿" + env.FCM_SA;
  await call("PUT", "/v2/token", { fcmToken: "dad-phone" }, dad.token);
  await call("PUT", "/v2/cars/c_00000001/park", park(), mom.token);
  assert.deepEqual(sent.map(m => m.token), ["dad-phone"]);
});

test("maintenance cron leaves expired photo flags and objects untouched", async () => {
  const { mom } = await family();
  await call("PUT", "/v2/cars/c_00000001/park", park({ id: 1, atMs: 1 }), mom.token);
  await db.prepare("UPDATE parks3 SET json=json_set(json,'$.photo',json('true'))").run();
  const deleted = [];
  const photos = env.PHOTOS;
  env.PHOTOS = { delete: async key => deleted.push(key) };
  try {
    env.MAINTENANCE = "1";
    await scheduled({ scheduledTime: 40 * 86400_000 }, env, null, store);
    assert.equal((await store.park3("c_00000001")).park.photo, true);
    assert.deepEqual(deleted, []);
    env.MAINTENANCE = "0";
    await scheduled({ scheduledTime: 40 * 86400_000 }, env, null, store);
    assert.equal((await store.park3("c_00000001")).park.photo, false);
    assert.deepEqual(deleted, ["c/c_00000001/1.jpg"]);
  } finally { env.PHOTOS = photos; }
});
