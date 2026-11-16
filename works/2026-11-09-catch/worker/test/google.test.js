import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { googleToken, getPurchase, consumePurchase, decodeIntegrity, _resetTokenCache } from "../src/google.js";

const b64url = b => Buffer.from(b).toString("base64url");
const fromB64url = s => new Uint8Array(Buffer.from(s, "base64url"));

let env, pub, fake;

const keys = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const pkcs8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", keys.privateKey)).toString("base64");
const PEM = `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`;

function makeFake(routes) {
  const f = async (url, init = {}) => {
    f.calls.push({ url, method: init.method || "GET", headers: init.headers || {}, body: init.body });
    for (const [match, reply] of routes) if (url.includes(match)) return reply(url, init);
    return new Response("{}", { status: 404 });
  };
  f.calls = [];
  return f;
}
const ok = body => () => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });

beforeEach(() => {
  _resetTokenCache();
  pub = keys.publicKey;
  env = { PKG: "kr.dot.catchpet",
    GOOGLE_SA: "﻿" + JSON.stringify({ client_email: "sa@x.iam.gserviceaccount.com", private_key: PEM, token_uri: "https://oauth2.googleapis.com/token" }) + "\n" };
  fake = makeFake([
    ["oauth2.googleapis.com/token", ok({ access_token: "at", expires_in: 3600 })],
    [":consume", () => new Response(null, { status: 204 })],
    ["/purchases/products/", ok({ purchaseState: 0, consumptionState: 0, orderId: "GPA.1" })],
    [":decodeIntegrityToken", ok({ tokenPayloadExternal: { requestDetails: { requestHash: "H" } } })],
  ]);
});

test("googleToken signs a JWT the public key verifies, and caches it until near expiry", async () => {
  const t1 = await googleToken(env, fake, 1_000_000);
  const t2 = await googleToken(env, fake, 1_000_000 + 60_000);
  assert.equal(t1, "at");
  assert.equal(t2, "at");
  const tokenCalls = fake.calls.filter(c => c.url.includes("oauth2"));
  assert.equal(tokenCalls.length, 1);
  const form = new URLSearchParams(tokenCalls[0].body);
  assert.equal(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");
  const [h, c, s] = form.get("assertion").split(".");
  assert.deepEqual(JSON.parse(Buffer.from(h, "base64url")), { alg: "RS256", typ: "JWT" });
  const claims = JSON.parse(Buffer.from(c, "base64url"));
  assert.equal(claims.iss, "sa@x.iam.gserviceaccount.com");
  assert.equal(claims.aud, "https://oauth2.googleapis.com/token");
  assert.match(claims.scope, /auth\/androidpublisher/);
  assert.match(claims.scope, /auth\/playintegrity/);
  assert.equal(claims.iat, 1000);
  assert.equal(claims.exp, 1000 + 3600);
  assert.ok(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pub, fromB64url(s), new TextEncoder().encode(h + "." + c)));
  await googleToken(env, fake, 1_000_000 + 3_550_000);   // within 60 s of expiry: a new token
  assert.equal(fake.calls.filter(c => c.url.includes("oauth2")).length, 2);
});

test("getPurchase hits the v3 products URL with the bearer", async () => {
  const p = await getPurchase(env, "sheet_fluff", "T/1", fake);
  assert.equal(p.orderId, "GPA.1");
  const c = fake.calls.find(c => c.url.includes("/purchases/"));
  assert.equal(c.url, "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/kr.dot.catchpet/purchases/products/sheet_fluff/tokens/T%2F1");
  assert.equal(c.headers.authorization, "Bearer at");
});

test("consumePurchase POSTs :consume and returns true on 204", async () => {
  assert.equal(await consumePurchase(env, "sheet_figure", "T1", fake), true);
  const c = fake.calls.find(c => c.url.endsWith(":consume"));
  assert.equal(c.method, "POST");
  assert.match(c.url, /products\/sheet_figure\/tokens\/T1:consume$/);
});

test("decodeIntegrity POSTs {integrity_token} and returns tokenPayloadExternal", async () => {
  const v = await decodeIntegrity(env, "INT", fake);
  assert.deepEqual(v, { requestDetails: { requestHash: "H" } });
  const c = fake.calls.find(c => c.url.includes("decodeIntegrityToken"));
  assert.equal(c.url, "https://playintegrity.googleapis.com/v1/kr.dot.catchpet:decodeIntegrityToken");
  assert.equal(c.method, "POST");
  assert.deepEqual(JSON.parse(c.body), { integrity_token: "INT" });
});

test("a Google error throws {status} and never carries the token", async () => {
  fake = makeFake([
    ["oauth2.googleapis.com/token", ok({ access_token: "at", expires_in: 3600 })],
    ["/purchases/products/", () => new Response('{"error":"x"}', { status: 410 })],
  ]);
  await assert.rejects(getPurchase(env, "sheet_fluff", "SECRET-TOKEN", fake), e => e.status === 410 && !String(e.message).includes("SECRET-TOKEN"));
});

test("a missing or broken GOOGLE_SA throws status 0 (the route answers off)", async () => {
  await assert.rejects(googleToken({ PKG: "x" }, fake), e => e.status === 0);
  await assert.rejects(googleToken({ PKG: "x", GOOGLE_SA: "{nope" }, fake), e => e.status === 0);
});
