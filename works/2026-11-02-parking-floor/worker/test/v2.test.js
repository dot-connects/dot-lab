import { test } from "node:test";
import assert from "node:assert/strict";
import { handle, scheduled, sha256 } from "../src/index.js";
import { MemoryStore } from "../src/store.js";

const base = "https://p.test";
const car = (id = "c_1234abcd") => ({ id, name: "White", color: -723724, plate: "12A3456", order: 0, updated: 1000 });
const park = (id, carId = "c_1234abcd", rest = {}) => ({ id, carId, atMs: id, floor: "B2", zone: "C", number: 37, pillar: null, memo: null, state: "PARKED", photo: true, ...rest });

function setup() {
  const store = new MemoryStore();
  const objects = new Map();
  const env = {
    LINK_BASE: `${base}/j`, ASSETLINKS_SHA256: "AA:BB, CC:DD",
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
    const req = new Request(base + path, { method, headers: h, body });
    const waits = [];
    const res = await handle(req, env, store, { waitUntil: p => waits.push(p) });
    await Promise.all(waits);
    return { status: res.status, body: res.headers.get("content-type")?.includes("json") ? await res.json() : await res.text(), res, waits: waits.length };
  }
  async function family(name = "Home") {
    const owner = (await call("POST", "/v2/groups", { name: "Owner", groupName: name })).body;
    const invite = (await call("POST", "/v2/invites", null, owner.token)).body;
    const peek = await call("POST", "/v2/peek", { code: invite.code });
    const guest = (await call("POST", "/v2/join", { code: invite.code, name: "Guest" })).body;
    return { owner, guest, invite, peek };
  }
  return { store, env, objects, call, family };
}

test("group, non-consuming peek, join, group name and public pages", async () => {
  const { call, family } = setup();
  const { owner, guest, peek } = await family();
  assert.equal(peek.status, 200);
  assert.equal(peek.body.groupName, "Home");
  assert.equal(peek.body.count, 1);
  assert.equal(guest.groupName, "Home");
  assert.equal((await call("GET", "/v2/state", null, owner.token)).body.members.length, 2);
  assert.equal((await call("GET", "/j")).status, 200);
  assert.equal((await call("GET", "/.well-known/assetlinks.json")).body.length, 2);
  const v1 = (await call("POST", "/v1/groups", { name: "Old" })).body;
  assert.equal((await call("GET", "/v2/state", null, v1.token)).body.groupName, null);
});

test("cars are scoped by group, capped at four and rate limited", async () => {
  const { call, env, family } = setup();
  const a = (await family()).owner, b = (await family()).owner;
  for (let i = 0; i < 4; i++) assert.equal((await call("PUT", `/v2/cars/c_0000000${i}`, { car: car(`c_0000000${i}`) }, a.token)).status, 200);
  assert.deepEqual((await call("PUT", "/v2/cars/c_00000004", { car: car("c_00000004") }, a.token)).body, { error: "full" });
  await call("PUT", "/v2/cars/c_00000000", { car: car("c_00000000") }, b.token);
  assert.equal((await call("GET", "/v2/state", null, a.token)).body.cars[0].name, "White");
  env.LIMIT = { limit: async () => ({ success: false }) };
  assert.equal((await call("POST", "/v2/groups", { name: "X", groupName: "Y" })).status, 429);
});

test("park update, stale, state, undo and server-owned photo flag", async () => {
  const { call, family } = setup();
  const { owner, guest } = await family();
  const id = "c_1234abcd";
  for (const suffix of ["park", "state", "undo", "photo?park=1"]) {
    const method = suffix.startsWith("photo") ? "PUT" : "POST";
    assert.equal((await call(method, `/v2/cars/${id}/${suffix}`, suffix.startsWith("photo") ? new Uint8Array(1) : {}, owner.token, suffix.startsWith("photo") ? { "content-length": "1" } : {})).status, 404);
  }
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  assert.equal((await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token)).status, 200);
  let current = (await call("GET", "/v2/state", null, guest.token)).body.parks[id];
  assert.equal(current.photo, false);
  assert.equal(current.by, "Owner");
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100, id, { memo: "Edited" }) }, guest.token);
  current = (await call("GET", "/v2/state", null, guest.token)).body.parks[id];
  assert.equal(current.by, "Owner");
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, guest.token);
  const stale = await call("PUT", `/v2/cars/${id}/park`, { park: park(150) }, owner.token);
  assert.equal(stale.status, 409);
  assert.equal(stale.body.park.id, 200);
  await call("POST", `/v2/cars/${id}/state`, { state: "DRIVING" }, owner.token);
  current = (await call("GET", "/v2/state", null, guest.token)).body.parks[id];
  assert.equal(current.id, 200);
  assert.equal(current.drivingBy, "Owner");
  assert.equal((await call("POST", `/v2/cars/${id}/undo`, { parkId: 200 }, owner.token)).waits, 1);
  current = (await call("GET", "/v2/state", null, owner.token)).body.parks[id];
  assert.equal(current.id, 100);
  assert.equal(current.by, "Owner");
  assert.equal((await call("POST", `/v2/cars/${id}/undo`, { parkId: 200 }, owner.token)).status, 409);
  assert.equal((await call("POST", `/v2/cars/${id}/undo`, { parkId: 100 }, owner.token)).waits, 1);
  assert.equal((await call("GET", "/v2/state", null, owner.token)).body.parks[id], undefined);
});

test("photo size, current id and replacement cleanup", async () => {
  const { call, family, objects } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const path = `/v2/cars/${id}/photo?park=100`;
  assert.equal((await call("PUT", path, new Uint8Array(300 * 1024), owner.token, { "content-length": String(300 * 1024), "content-type": "image/jpeg" })).status, 200);
  assert.equal((await call("PUT", path, new Uint8Array(401 * 1024), owner.token, { "content-length": String(401 * 1024), "content-type": "image/jpeg" })).status, 413);
  assert.equal((await call("PUT", path, new Uint8Array(1), owner.token, { "content-type": "image/jpeg" })).status, 413);
  assert.equal((await call("PUT", path, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" })).status, 200);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, owner.token);
  assert.equal((await call("PUT", path, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" })).status, 409);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(300) }, owner.token);
  assert.equal(objects.size, 0);
});

test("inactive member threshold, null lastSeen fallback, owner never removable by non-owner", async () => {
  const { call, family, store } = setup();
  const { owner, guest } = await family();
  const ownerRow = await store.member(owner.memberId);
  ownerRow.lastSeen = Date.now() - 31 * 86400_000;
  assert.equal((await call("DELETE", `/v2/members/${owner.memberId}`, null, guest.token)).status, 403);
  ownerRow.lastSeen = Date.now();
  const code = (await call("POST", "/v2/invites", null, owner.token)).body.code;
  const other = (await call("POST", "/v2/join", { code, name: "Other" })).body;
  const otherRow = await store.member(other.memberId);
  otherRow.lastSeen = Date.now() - 29 * 86400_000;
  assert.equal((await call("DELETE", `/v2/members/${other.memberId}`, null, guest.token)).status, 403);
  otherRow.lastSeen = null;
  otherRow.created = Date.now() - 29 * 86400_000;
  assert.equal((await call("DELETE", `/v2/members/${other.memberId}`, null, guest.token)).status, 403);
  otherRow.created = Date.now() - 31 * 86400_000;
  assert.equal((await call("DELETE", `/v2/members/${other.memberId}`, null, guest.token)).status, 200);
});

test("a v1 authenticated request refreshes last_seen", async () => {
  const { call, family, store } = setup();
  const { guest } = await family();
  const row = await store.member(guest.memberId);
  row.lastSeen = Date.now() - 120_000;
  const before = row.lastSeen;
  assert.equal((await call("GET", "/v1/park", null, guest.token)).status, 200);
  assert.ok((await store.member(guest.memberId)).lastSeen > before);
});

test("v1 final-member deletion removes v2 storage and photos", async () => {
  const { call, store, objects } = setup();
  const owner = (await call("POST", "/v1/groups", { name: "Old" })).body;
  await call("PUT", "/v2/cars/c_1234abcd", { car: car() }, owner.token);
  await call("PUT", "/v2/cars/c_1234abcd/park", { park: park(100) }, owner.token);
  await call("PUT", "/v2/cars/c_1234abcd/photo?park=100", new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  assert.equal(objects.size, 1);
  await call("DELETE", `/v1/members/${owner.memberId}`, null, owner.token);
  assert.equal((await store.cars(owner.groupId)).length, 0);
  assert.equal(await store.park2(owner.groupId, "c_1234abcd"), null);
  assert.equal(objects.size, 0);
});

test("owner leaving through v2/me hands ownership to the oldest member", async () => {
  const { call, family, store } = setup();
  const { owner, guest } = await family();
  assert.equal((await call("DELETE", "/v2/me", null, owner.token)).status, 200);
  assert.equal((await store.member(guest.memberId)).owner, true);
  assert.equal((await call("GET", "/v2/state", null, guest.token)).body.members.length, 1);
});

test("member removal, owner handoff, group cleanup and scheduled expiry", async () => {
  const { call, family, store, objects, env } = setup();
  const a = await family(), b = await family();
  assert.equal((await call("DELETE", `/v2/members/${b.guest.memberId}`, null, a.owner.token)).status, 404);
  assert.equal((await call("DELETE", `/v2/members/${a.owner.memberId}`, null, a.guest.token)).status, 403);
  assert.equal((await call("DELETE", "/v2/me", null, a.owner.token)).status, 200);
  assert.equal((await store.member(a.guest.memberId)).owner, true);
  const gid = a.guest.groupId;
  await call("PUT", "/v2/cars/c_1234abcd", { car: car() }, a.guest.token);
  await call("PUT", "/v2/cars/c_1234abcd/park", { park: park(100) }, a.guest.token);
  await call("PUT", "/v2/cars/c_1234abcd/photo?park=100", new Uint8Array(1), a.guest.token, { "content-length": "1", "content-type": "image/jpeg" });
  assert.equal(objects.size, 1);
  await scheduled({ scheduledTime: Date.now() + 31 * 86400_000 }, env, { waitUntil() {} }, store);
  assert.equal((await store.park2(gid, "c_1234abcd")).park.photo, false);
  await call("DELETE", "/v2/me", null, a.guest.token);
  assert.equal((await store.cars(gid)).length, 0);
  assert.equal(await store.park2(gid, "c_1234abcd"), null);
  assert.equal(objects.size, 0);
});

test("photo upload racing a newer park keeps the newer park", async () => {
  const { call, family, store, env, objects } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const put = env.PHOTOS.put;
  env.PHOTOS.put = async (k, v, o) => {
    await put(k, v, o);
    const cur = await store.park2(owner.groupId, id);
    await store.setPark2(owner.groupId, id, { ...cur, park: { ...cur.park, id: 300, atMs: 300, photo: false }, updated: cur.updated + 1 });
  };
  const res = await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  assert.equal(res.status, 409);
  assert.equal(res.body.error, "moved");
  assert.equal(res.body.park.id, 300);
  assert.equal((await store.park2(owner.groupId, id)).park.id, 300);
  assert.equal(objects.size, 0);
});

test("state and undo on a park that moved meanwhile answer 409", async () => {
  const { call, family, store } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  for (const [path, payload] of [["state", { state: "DRIVING" }], ["undo", { parkId: 100 }]]) {
    const orig = store.park2.bind(store);
    let armed = true;
    store.park2 = async (...a) => {
      const r = await orig(...a);
      if (armed && r) { armed = false; await store.setPark2(owner.groupId, id, { ...r, park: { ...r.park, id: 300, atMs: 300 }, updated: r.updated + 1 }); }
      return r;
    };
    const res = await call("POST", `/v2/cars/${id}/${path}`, payload, owner.token);
    assert.equal(res.status, 409, path);
    assert.equal(res.body.error, "moved");
    assert.equal(res.body.park.id, 300);
    store.park2 = orig;
    await store.setPark2(owner.groupId, id, { ...(await store.park2(owner.groupId, id)), park: { ...park(100) } });
  }
});

test("null or missing optional fields are accepted", async () => {
  const { call, family } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  const { plate, ...noPlate } = car();
  assert.equal((await call("PUT", `/v2/cars/${id}`, { car: noPlate }, owner.token)).body.car.plate, null);
  assert.equal((await call("PUT", `/v2/cars/${id}`, { car: { ...car(), plate: null } }, owner.token)).status, 200);
  const { zone, pillar, memo, floor, number, ...bare } = park(100);
  const res = await call("PUT", `/v2/cars/${id}/park`, { park: bare }, owner.token);
  assert.equal(res.status, 200);
  assert.deepEqual([res.body.park.zone, res.body.park.pillar, res.body.park.memo, res.body.park.floor, res.body.park.number], [null, null, null, null, null]);
});

test("json body over 4 KB is refused by bytes without content-length", async () => {
  const { call, family } = setup();
  const { owner } = await family();
  const res = await call("POST", "/v2/peek", new Uint8Array(5000).fill(32), owner.token, { "content-type": "application/json" });
  assert.equal(res.status, 413);
});

test("scheduled expiry also clears the photo flag inside prev", async () => {
  const { call, family, store, env } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, owner.token);
  assert.equal((await store.park2(owner.groupId, id)).prev.park.photo, true);
  await scheduled({ scheduledTime: Date.now() + 31 * 86400_000 }, env, { waitUntil() {} }, store);
  assert.equal((await store.park2(owner.groupId, id)).prev.park.photo, false);
});

test("state change racing a photo upload still saves the photo", async () => {
  const { call, family, store, env, objects } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const put = env.PHOTOS.put;
  env.PHOTOS.put = async (k, v, o) => {
    await put(k, v, o);
    const cur = await store.park2(owner.groupId, id);
    await store.setPark2(owner.groupId, id, { ...cur, park: { ...cur.park, state: "DRIVING" }, updated: cur.updated + 1 });
  };
  const res = await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  assert.equal(res.status, 200);
  const row = await store.park2(owner.groupId, id);
  assert.equal(row.park.photo, true);
  assert.equal(row.park.state, "DRIVING");
  assert.equal(objects.size, 1);
});

test("two concurrent uploads for the same park leave the object", async () => {
  const { call, family, store, objects } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const up = () => call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  const [a, b] = await Promise.all([up(), up()]);
  assert.deepEqual([a.status, b.status], [200, 200]);
  assert.equal((await store.park2(owner.groupId, id)).park.photo, true);
  assert.equal(objects.size, 1);
});

test("photo GET needs photo=true on the current park; scheduled deletes expired objects", async () => {
  const { call, family, store, env, objects } = setup();
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  env.PHOTOS.put("g/x", new Uint8Array(1));
  objects.set(`g/${owner.groupId}/${id}/100.jpg`, new Uint8Array(1));
  assert.equal((await call("GET", `/v2/cars/${id}/photo?park=100`, null, owner.token)).status, 404);
  objects.delete("g/x");
  await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  assert.equal((await call("GET", `/v2/cars/${id}/photo?park=100`, null, owner.token)).status, 200);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, owner.token);
  await call("PUT", `/v2/cars/${id}/photo?park=200`, new Uint8Array(1), owner.token, { "content-length": "1", "content-type": "image/jpeg" });
  const cur = await store.park2(owner.groupId, id);
  cur.prev.park.photo = true;
  objects.set(`g/${owner.groupId}/${id}/100.jpg`, new Uint8Array(1));
  await scheduled({ scheduledTime: Date.now() + 31 * 86400_000 }, env, { waitUntil() {} }, store);
  assert.equal(objects.size, 0);
  assert.equal((await call("GET", `/v2/cars/${id}/photo?park=200`, null, owner.token)).status, 404);
});

const poll = (call, secret) => call("POST", "/v2/requests/poll", { secret });

test("join request: accept makes its own invite under the link hash, only the secret polls it, join clears it", async () => {
  const { call, store } = setup();
  const owner = (await call("POST", "/v2/groups", { name: "Owner", groupName: "Home" })).body;
  const other = (await call("POST", "/v2/groups", { name: "Other", groupName: "Away" })).body;
  const S = "0123456789abcdef0123456789abcdef", H = await sha256(S);
  assert.equal((await poll(call, S)).status, 404);
  for (const bad of ["0123456789ABCDEF0123456789ABCDEF", "abc", H, 42, null]) assert.equal((await poll(call, bad)).status, 400);
  assert.equal((await call("GET", `/v2/requests/${S}`)).status, 401);  // no GET-by-id route
  const shared = (await call("POST", "/v2/invites", null, owner.token)).body.code;  // an invite already sent to someone else
  assert.equal((await call("PUT", "/v2/requests", { req: H })).status, 401);
  for (const req of [S, "short", H.toUpperCase(), undefined]) assert.equal((await call("PUT", "/v2/requests", { req }, owner.token)).status, 400);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, other.token)).status, 409);  // first accept wins
  assert.equal(store.invites.size, 2);  // the 409 left no invite behind; the shared one is still open
  assert.deepEqual([...store.requests.keys()], [H]);
  // someone who only saw the link (H) or guesses a secret gets nothing
  assert.equal((await poll(call, "ffffffffffffffffffffffffffffffff")).status, 404);
  assert.equal((await poll(call, H.slice(0, 32))).status, 404);
  const polled = await poll(call, S);
  assert.match(polled.body.code, /^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.deepEqual((await poll(call, S)).body, polled.body);  // polling does not consume
  assert.equal((await call("POST", "/v2/peek", { code: polled.body.code })).body.groupName, "Home");
  // a new invite from the family closes the old shared one but not the request's
  const next = (await call("POST", "/v2/invites", null, owner.token)).body.code;
  assert.equal((await call("POST", "/v2/peek", { code: shared })).status, 404);
  assert.equal((await call("POST", "/v2/join", { code: polled.body.code, name: "Kid" })).status, 200);
  assert.equal((await poll(call, S)).status, 404);
  assert.equal((await call("POST", "/v2/join", { code: next, name: "Kid2" })).status, 200);
  assert.equal((await call("GET", "/v2/state", null, owner.token)).body.members.length, 3);
});

test("join request: the requester cancels; expired rows are missing and purged by the cron; rate limited", async () => {
  const { call, store, env } = setup();
  const owner = (await call("POST", "/v2/groups", { name: "Owner", groupName: "Home" })).body;
  const S = "ffffffffffffffffffffffffffffffff", H = await sha256(S);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  const code = (await poll(call, S)).body.code;
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: H })).status, 400);
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: S })).status, 200);
  assert.equal((await poll(call, S)).status, 404);
  assert.equal((await call("POST", "/v2/peek", { code })).status, 404);  // its invite is gone too
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: S })).status, 200);  // idempotent

  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  for (const r of store.requests.values()) r.expires = Date.now() - 1;
  assert.equal((await poll(call, S)).status, 404);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);  // an expired row no longer blocks
  for (const r of store.requests.values()) r.expires = Date.now() - 1;
  await scheduled({ scheduledTime: Date.now() }, env, { waitUntil() {} }, store);
  assert.equal(store.requests.size, 0);

  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  env.LIMIT = { limit: async ({ key }) => ({ success: !key.startsWith("/v2/requests:") }) };
  assert.equal((await poll(call, S)).status, 429);
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: S })).status, 429);
  env.LIMIT = undefined;
  // the family going away takes its requests along
  assert.equal((await call("DELETE", "/v2/me", null, owner.token)).status, 200);
  assert.equal((await poll(call, S)).status, 404);
  assert.equal((await call("GET", "/r")).status, 200);
});
