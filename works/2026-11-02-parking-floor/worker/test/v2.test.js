import { test, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { scheduled, sha256 } from "../src/index.js";
import { setup } from "./harness.js";

const DAY = 86400_000;
const car = (id = "c_1234abcd") => ({ id, name: "White", color: -723724, plate: "12A3456", order: 0, updated: 1000 });
const park = (id, carId = "c_1234abcd", rest = {}) => ({ id, carId, atMs: id, floor: "B2", zone: "C", number: 37, pillar: null, memo: null, state: "PARKED", photo: true, ...rest });
const jpeg = { "content-length": "1", "content-type": "image/jpeg" };

let h, store, db, env, objects, call;
before(async () => { h = await setup(); ({ store, db, env, objects, call } = h); });
beforeEach(async () => { await h.reset(); delete env.LIMIT; });
after(async () => h?.close());

async function family(name = "Home") {
  const owner = (await call("POST", "/v2/groups", { name: "Owner", groupName: name })).body;
  const invite = (await call("POST", "/v2/invites", null, owner.token)).body;
  const peek = await call("POST", "/v2/peek", { code: invite.code });
  const guest = (await call("POST", "/v2/join", { code: invite.code, name: "Guest" })).body;
  return { owner, guest, invite, peek };
}
const count = async table => (await db.prepare(`SELECT count(*) AS n FROM ${table}`).first()).n;
const setLastSeen = (id, lastSeen, created) => created === undefined
  ? db.prepare("UPDATE devices SET last_seen=? WHERE id=?").bind(lastSeen, id).run()
  : db.prepare("UPDATE devices SET last_seen=?,created=? WHERE id=?").bind(lastSeen, created, id).run();
/** Rewrites the stored park row in place (a concurrent writer). */
const poke = async (carId, change) => {
  const cur = await store.park3(carId), next = change(cur);
  await db.prepare("UPDATE parks3 SET json=?,prev_json=?,updated=? WHERE car_id=?")
    .bind(JSON.stringify(next.park), next.prev ? JSON.stringify(next.prev) : null, next.updated, carId).run();
};

test("group, non-consuming peek, join, group name and public pages", async () => {
  const { owner, guest, peek } = await family();
  assert.equal(peek.status, 200);
  assert.equal(peek.body.groupName, "Home");
  assert.equal(peek.body.count, 1);
  assert.equal(guest.groupName, "Home");
  assert.equal(guest.token, `${guest.memberId}.${guest.secret}`);
  assert.ok(await store.device(guest.memberId));
  assert.equal((await call("GET", "/v2/state", null, owner.token)).body.members.length, 2);
  assert.equal((await call("GET", "/j")).status, 200);
  assert.equal((await call("GET", "/.well-known/assetlinks.json")).body.length, 2);
});

test("cars are scoped by group, capped at four and rate limited", async () => {
  const a = (await family()).owner, b = (await family()).owner;
  for (let i = 0; i < 4; i++) assert.equal((await call("PUT", `/v2/cars/c_0000000${i}`, { car: car(`c_0000000${i}`) }, a.token)).status, 200);
  assert.deepEqual((await call("PUT", "/v2/cars/c_00000004", { car: car("c_00000004") }, a.token)).body, { error: "full" });
  assert.deepEqual(await store.carGroups(["c_00000000"]), { c_00000000: [a.groupId] });
  assert.deepEqual((await call("PUT", "/v2/cars/c_00000000", { car: car("c_00000000") }, b.token)).body, { error: "car" });
  assert.equal((await call("GET", "/v2/state", null, a.token)).body.cars[0].name, "White");
  env.LIMIT = { limit: async () => ({ success: false }) };
  assert.equal((await call("POST", "/v2/groups", { name: "X", groupName: "Y" })).status, 429);
  assert.equal((await call("GET", "/v2/state", null, a.token)).status, 429);
});

test("park update, stale, state, undo and server-owned photo flag", async () => {
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
  assert.equal((await call("POST", `/v2/cars/${id}/state`, { state: "DRIVING" }, owner.token)).status, 200);
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

test("legacy state applies only to a park written before the device's last contact", async () => {
  const { owner, guest } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  await setLastSeen(guest.memberId, 1);
  const moved = await call("POST", `/v2/cars/${id}/state`, { state: "DRIVING" }, guest.token);
  assert.equal(moved.status, 409); assert.equal(moved.body.error, "moved"); assert.equal(moved.body.park.id, 100);
  assert.equal((await store.park3(id)).park.state, "PARKED");
  assert.equal((await call("POST", `/v2/cars/${id}/state`, { state: "DRIVING" }, guest.token)).status, 200);
  assert.ok((await store.device(guest.memberId)).v2Seen > 0);
});

test("photo size, current id and replacement cleanup", async () => {
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const path = `/v2/cars/${id}/photo?park=100`;
  assert.equal((await call("PUT", path, new Uint8Array(300 * 1024), owner.token, { "content-length": String(300 * 1024), "content-type": "image/jpeg" })).status, 200);
  assert.equal((await call("PUT", path, new Uint8Array(401 * 1024), owner.token, { "content-length": String(401 * 1024), "content-type": "image/jpeg" })).status, 413);
  assert.equal((await call("PUT", path, new Uint8Array(1), owner.token, { "content-type": "image/jpeg" })).status, 413);
  assert.equal((await call("PUT", path, new Uint8Array(1), owner.token, jpeg)).status, 200);
  assert.deepEqual([...objects.keys()], [`c/${id}/100.jpg`]);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, owner.token);
  assert.equal((await call("PUT", path, new Uint8Array(1), owner.token, jpeg)).status, 409);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(300) }, owner.token);
  assert.equal(objects.size, 0);
});

test("inactive member threshold, null lastSeen fallback, owner never removable by non-owner", async () => {
  const { owner, guest } = await family();
  await setLastSeen(owner.memberId, Date.now() - 31 * DAY);
  assert.equal((await call("DELETE", `/v2/members/${owner.memberId}`, null, guest.token)).status, 403);
  const code = (await call("POST", "/v2/invites", null, owner.token)).body.code;
  const other = (await call("POST", "/v2/join", { code, name: "Other" })).body;
  await setLastSeen(other.memberId, Date.now() - 29 * DAY);
  assert.equal((await call("DELETE", `/v2/members/${other.memberId}`, null, guest.token)).status, 403);
  await setLastSeen(other.memberId, null, Date.now() - 29 * DAY);
  assert.equal((await call("DELETE", `/v2/members/${other.memberId}`, null, guest.token)).status, 403);
  await setLastSeen(other.memberId, null, Date.now() - 31 * DAY);
  assert.equal((await call("DELETE", `/v2/members/${other.memberId}`, null, guest.token)).status, 200);
});

test("owner leaving through v2/me hands ownership to the oldest member", async () => {
  const { owner, guest } = await family();
  assert.equal((await call("DELETE", "/v2/me", null, owner.token)).status, 200);
  assert.equal((await store.groupMembers(guest.groupId))[0].owner, true);
  assert.equal((await call("GET", "/v2/state", null, guest.token)).body.members.length, 1);
  assert.equal((await call("GET", "/v2/state", null, owner.token)).status, 401);
});

test("member removal, owner handoff, group cleanup and scheduled expiry", async () => {
  const a = await family(), b = await family();
  assert.equal((await call("DELETE", `/v2/members/${b.guest.memberId}`, null, a.owner.token)).status, 404);
  assert.equal((await call("DELETE", `/v2/members/${a.owner.memberId}`, null, a.guest.token)).status, 403);
  assert.equal((await call("DELETE", "/v2/me", null, a.owner.token)).status, 200);
  assert.equal((await store.groupMembers(a.guest.groupId))[0].owner, true);
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, a.guest.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, a.guest.token);
  await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), a.guest.token, jpeg);
  assert.equal(objects.size, 1);
  await scheduled({ scheduledTime: Date.now() + 31 * DAY }, env, { waitUntil() {} }, store);
  assert.equal((await store.park3(id)).park.photo, false);
  objects.set(`c/${id}/100.jpg`, "x");
  await call("DELETE", "/v2/me", null, a.guest.token);
  assert.equal(await store.car(id), null);
  assert.equal(await store.park3(id), null);
  assert.equal(objects.size, 0);
  assert.equal(await store.group(a.guest.groupId), null);
});

test("photo upload racing a newer park keeps the newer park", async () => {
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const put = env.PHOTOS.put;
  env.PHOTOS.put = async (k, v, o) => {
    await put(k, v, o);
    await poke(id, cur => ({ ...cur, park: { ...cur.park, id: 300, atMs: 300, photo: false }, updated: cur.updated + 1 }));
  };
  try {
    const res = await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, jpeg);
    assert.equal(res.status, 409);
    assert.equal(res.body.error, "moved");
    assert.equal(res.body.park.id, 300);
  } finally { env.PHOTOS.put = put; }
  assert.equal((await store.park3(id)).park.id, 300);
  assert.equal(objects.size, 0);
});

test("state and undo on a park that moved meanwhile answer 409", async t => {
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  for (const [path, payload] of [["state", { state: "DRIVING" }], ["undo", { parkId: 100 }]]) {
    const orig = store.park3.bind(store);
    let armed = true;
    const mock = t.mock.method(store, "park3", async (...a) => {
      const r = await orig(...a);
      if (armed && r) { armed = false; await poke(id, cur => ({ ...cur, park: { ...cur.park, id: 300, atMs: 300 }, updated: cur.updated + 1 })); }
      return r;
    });
    const res = await call("POST", `/v2/cars/${id}/${path}`, payload, owner.token);
    mock.mock.restore();
    assert.equal(res.status, 409, path);
    assert.equal(res.body.error, "moved");
    assert.equal(res.body.park.id, 300);
    await poke(id, cur => ({ ...cur, park: { ...park(100), photo: false } }));
  }
});

test("null or missing optional fields are accepted", async () => {
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
  const { owner } = await family();
  const res = await call("POST", "/v2/peek", new Uint8Array(5000).fill(32), owner.token, { "content-type": "application/json" });
  assert.equal(res.status, 413);
});

test("scheduled expiry also clears the photo flag inside prev; photo GET needs photo=true", async () => {
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  objects.set(`c/${id}/100.jpg`, new Uint8Array(1));
  assert.equal((await call("GET", `/v2/cars/${id}/photo?park=100`, null, owner.token)).status, 404);
  await call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, jpeg);
  assert.equal((await call("GET", `/v2/cars/${id}/photo?park=100`, null, owner.token)).status, 200);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, owner.token);
  await call("PUT", `/v2/cars/${id}/photo?park=200`, new Uint8Array(1), owner.token, jpeg);
  assert.equal((await store.park3(id)).prev.park.photo, true);
  await scheduled({ scheduledTime: Date.now() + 31 * DAY }, env, { waitUntil() {} }, store);
  assert.equal((await store.park3(id)).prev.park.photo, false);
  assert.equal(objects.size, 0);
  assert.equal((await call("GET", `/v2/cars/${id}/photo?park=200`, null, owner.token)).status, 404);
});

test("state change racing a photo upload still saves the photo; concurrent uploads keep the object", async () => {
  const { owner } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, owner.token);
  const up = () => call("PUT", `/v2/cars/${id}/photo?park=100`, new Uint8Array(1), owner.token, jpeg);
  const put = env.PHOTOS.put;
  env.PHOTOS.put = async (k, v, o) => {
    await put(k, v, o);
    await poke(id, cur => ({ ...cur, park: { ...cur.park, state: "DRIVING" }, updated: cur.updated + 1 }));
  };
  try { assert.equal((await up()).status, 200); } finally { env.PHOTOS.put = put; }
  const row = await store.park3(id);
  assert.equal(row.park.photo, true);
  assert.equal(row.park.state, "DRIVING");
  assert.equal(objects.size, 1);
  const [a, b] = await Promise.all([up(), up()]);
  assert.deepEqual([a.status, b.status], [200, 200]);
  assert.equal(objects.size, 1);
});

// ---- v3 rules seen through v2 ----

test("another group's car id is 404 with no data; non-owner edit and delete are 200 no-ops", async () => {
  const a = await family(), b = await family("Away"), id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, a.owner.token);
  for (const [method, payload] of [["PUT", { car: { ...car(), name: "Mine" } }], ["DELETE"]]) {
    assert.deepEqual((await call(method, `/v2/cars/${id}`, payload, b.owner.token)).body, { error: "car" });
  }
  assert.equal((await call("PUT", `/v2/cars/${id}/park`, { park: park(100) }, b.owner.token)).status, 404);
  const edit = await call("PUT", `/v2/cars/${id}`, { car: { ...car(), name: "Renamed" } }, a.guest.token);
  assert.equal(edit.status, 200); assert.equal(edit.body.car.name, "White");
  assert.equal((await store.car(id)).car.name, "White");
  assert.deepEqual((await call("DELETE", `/v2/cars/${id}`, null, a.guest.token)).body, { ok: true });
  assert.ok(await store.car(id));
  assert.deepEqual((await call("DELETE", `/v2/cars/${id}`, null, a.owner.token)).body, { ok: true });
  assert.equal(await store.car(id), null);
});

test("v2 state lists the group's cars plus every car I own, masked by D13", async () => {
  const { owner, guest } = await family();
  const id = "c_1234abcd", own = "c_00000009";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  // the guest owns a car outside the v2 group; a parker it no longer shares a group with is masked
  const work = (await call("POST", "/v3/groups", { groupName: "Work" }, guest.token)).body.groupId;
  await store.createCar(guest.memberId, car(own), 1);
  const code = (await call("POST", `/v3/groups/${work}/invites`, null, guest.token)).body.code;
  const stranger = (await call("POST", "/v3/join", { code, name: "Stranger" })).body;
  await store.include(own, work, guest.memberId, 1);
  await call("PUT", `/v3/cars/${own}/park`, { park: park(100, own) }, stranger.token);
  assert.equal((await call("GET", "/v2/state", null, guest.token)).status, 410);
  await call("DELETE", `/v3/groups/${work}/me`, null, guest.token);
  const state = (await call("GET", "/v2/state", null, guest.token)).body;
  assert.deepEqual(state.cars.map(c => c.id).sort(), [own, id]);
  const p = state.parks[own];
  assert.equal(p.by, null); assert.equal(p.byId, null); assert.equal(p.byOther, true);
});

test("v2 never sees LEFT; LEFT input is refused", async () => {
  const { owner, guest } = await family();
  const id = "c_1234abcd";
  await call("PUT", `/v2/cars/${id}`, { car: car() }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(100, id, { state: "DRIVING" }) }, owner.token);
  const v3 = (await call("POST", `/v3/cars/${id}/state`, { state: "LEFT", parkId: 100, atMs: 150 }, owner.token)).body.park;
  assert.equal(v3.state, "LEFT");
  const v2 = p => { assert.equal(p.state, "DRIVING"); assert.equal("leftAt" in p, false); };
  v2((await call("GET", "/v2/state", null, guest.token)).body.parks[id]);
  v2((await call("PUT", `/v2/cars/${id}/park`, { park: park(50) }, guest.token)).body.park);
  await call("POST", `/v3/cars/${id}/state`, { state: "LEFT", parkId: 100, atMs: 150 }, owner.token);
  await call("PUT", `/v2/cars/${id}/park`, { park: park(200) }, guest.token);
  v2((await call("GET", "/v2/state", null, guest.token)).body.parks[id].prev);
  v2((await call("POST", `/v2/cars/${id}/undo`, { parkId: 200 }, guest.token)).body.park);
  await setLastSeen(guest.memberId, 1);
  v2((await call("POST", `/v2/cars/${id}/state`, { state: "PARKED" }, guest.token)).body.park);
  assert.equal((await call("POST", `/v2/cars/${id}/state`, { state: "LEFT" }, guest.token)).status, 400);
  assert.equal((await call("PUT", `/v2/cars/${id}/park`, { park: park(300, id, { state: "LEFT", leftAt: 300 }) }, guest.token)).status, 400);
});

test("a device with two memberships gets 410 on every authed v2 route; v1 is closed", async () => {
  const { owner } = await family();
  await call("POST", "/v3/groups", { groupName: "Work" }, owner.token);
  for (const [method, path] of [["GET", "/v2/state"], ["POST", "/v2/invites"], ["PUT", "/v2/token"], ["DELETE", "/v2/me"], ["PUT", "/v2/cars/c_1234abcd"]]) {
    assert.deepEqual((await call(method, path, method === "PUT" ? {} : null, owner.token)).body, { error: "update" }, path);
  }
  for (const [method, path] of [["PUT", "/v1/park"], ["POST", "/v1/groups"], ["GET", "/v1/park"]]) {
    const r = await call(method, path, null, owner.token);
    assert.equal(r.status, 410); assert.deepEqual(r.body, { error: "update" });
  }
});

const poll = secret => call("POST", "/v2/requests/poll", { secret });

test("join request: accept makes its own invite under the link hash, only the secret polls it, join clears it", async () => {
  const owner = (await call("POST", "/v2/groups", { name: "Owner", groupName: "Home" })).body;
  const other = (await call("POST", "/v2/groups", { name: "Other", groupName: "Away" })).body;
  const S = "0123456789abcdef0123456789abcdef", H = await sha256(S);
  assert.equal((await poll(S)).status, 404);
  for (const bad of ["0123456789ABCDEF0123456789ABCDEF", "abc", H, 42, null]) assert.equal((await poll(bad)).status, 400);
  assert.equal((await call("GET", `/v2/requests/${S}`)).status, 401);  // no GET-by-id route
  const shared = (await call("POST", "/v2/invites", null, owner.token)).body.code;  // an invite already sent to someone else
  assert.equal((await call("PUT", "/v2/requests", { req: H })).status, 401);
  for (const req of [S, "short", H.toUpperCase(), undefined]) assert.equal((await call("PUT", "/v2/requests", { req }, owner.token)).status, 400);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, other.token)).status, 409);  // first accept wins
  assert.equal(await count("invites"), 2);  // the 409 left no invite behind; the shared one is still open
  assert.equal(await count("requests"), 1);
  assert.equal((await poll("ffffffffffffffffffffffffffffffff")).status, 404);
  assert.equal((await poll(H.slice(0, 32))).status, 404);
  const polled = await poll(S);
  assert.match(polled.body.code, /^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.deepEqual(polled.body, { code: polled.body.code, byName: "Owner" });
  assert.deepEqual((await poll(S)).body, polled.body);  // polling does not consume
  assert.equal((await call("POST", "/v2/peek", { code: polled.body.code })).body.groupName, "Home");
  const next = (await call("POST", "/v2/invites", null, owner.token)).body.code;
  assert.equal((await call("POST", "/v2/peek", { code: shared })).status, 404);
  assert.equal((await call("POST", "/v2/join", { code: polled.body.code, name: "Kid" })).status, 200);
  assert.equal((await poll(S)).status, 404);
  assert.equal((await call("POST", "/v2/join", { code: next, name: "Kid2" })).status, 200);
  assert.equal((await call("GET", "/v2/state", null, owner.token)).body.members.length, 3);
});

test("join request: the requester cancels; expired rows are missing and purged by the cron; rate limited", async () => {
  const owner = (await call("POST", "/v2/groups", { name: "Owner", groupName: "Home" })).body;
  const S = "ffffffffffffffffffffffffffffffff", H = await sha256(S);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  const code = (await poll(S)).body.code;
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: H })).status, 400);
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: S })).status, 200);
  assert.equal((await poll(S)).status, 404);
  assert.equal((await call("POST", "/v2/peek", { code })).status, 404);
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: S })).status, 200);  // idempotent
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  await db.prepare("UPDATE requests SET expires=?").bind(Date.now() - 1).run();
  assert.equal((await poll(S)).status, 404);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);  // an expired row no longer blocks
  await db.prepare("UPDATE requests SET expires=?").bind(Date.now() - 1).run();
  await scheduled({ scheduledTime: Date.now() }, env, { waitUntil() {} }, store);
  assert.equal(await count("requests"), 0);
  assert.equal((await call("PUT", "/v2/requests", { req: H }, owner.token)).status, 200);
  env.LIMIT = { limit: async ({ key }) => ({ success: !key.startsWith("/v2/requests:") }) };
  assert.equal((await poll(S)).status, 429);
  assert.equal((await call("POST", "/v2/requests/cancel", { secret: S })).status, 429);
  delete env.LIMIT;
  assert.equal((await call("DELETE", "/v2/me", null, owner.token)).status, 200);  // the group going away takes its requests
  assert.equal((await poll(S)).status, 404);
  assert.equal((await call("GET", "/r")).status, 200);
});

test("join request: at most five live requests per group; a removed member's requests and their invites go", async () => {
  const { owner, guest } = await family();
  const secrets = [...Array(6)].map((_, i) => String(i).repeat(32));
  const hashes = await Promise.all(secrets.map(s => sha256(s)));
  for (let i = 0; i < 3; i++) assert.equal((await call("PUT", "/v2/requests", { req: hashes[i] }, guest.token)).status, 200);
  for (let i = 3; i < 5; i++) assert.equal((await call("PUT", "/v2/requests", { req: hashes[i] }, owner.token)).status, 200);
  const r6 = await call("PUT", "/v2/requests", { req: hashes[5] }, owner.token);
  assert.equal(r6.status, 429); assert.equal(r6.body.error, "many");
  assert.equal((await call("PUT", "/v2/requests", { req: hashes[0] }, owner.token)).status, 409);
  const guestCodes = [];
  for (let i = 0; i < 3; i++) { const b = (await poll(secrets[i])).body; assert.equal(b.byName, "Guest"); guestCodes.push(b.code); }
  assert.equal((await call("DELETE", `/v2/members/${guest.memberId}`, null, owner.token)).status, 200);
  for (let i = 0; i < 3; i++) assert.equal((await poll(secrets[i])).status, 404);
  for (const c of guestCodes) assert.equal((await call("POST", "/v2/peek", { code: c })).status, 404);
  assert.equal((await poll(secrets[3])).body.byName, "Owner");
  assert.equal((await call("PUT", "/v2/requests", { req: hashes[5] }, owner.token)).status, 200);
  const inv2 = (await call("POST", "/v2/invites", null, owner.token)).body.code;
  const kid = (await call("POST", "/v2/join", { code: inv2, name: "Kid" })).body;
  await db.prepare("UPDATE requests SET expires=?").bind(Date.now() - 1).run();  // free the cap
  const S7 = "7".repeat(32);
  assert.equal((await call("PUT", "/v2/requests", { req: await sha256(S7) }, kid.token)).status, 200);
  const kidCode = (await poll(S7)).body.code;
  assert.equal((await call("DELETE", "/v2/me", null, kid.token)).status, 200);
  assert.equal((await poll(S7)).status, 404);
  assert.equal((await call("POST", "/v2/peek", { code: kidCode })).status, 404);
  assert.equal((await call("DELETE", "/v2/me", null, owner.token)).status, 200);
  assert.equal(await count("requests"), 0);
});
