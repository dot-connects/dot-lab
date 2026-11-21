import { test, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { setup } from "./harness.js";
import { sha256, normCode } from "../src/index.js";
import { resetTokenCache } from "../src/fcm.js";

const DAY = 86400_000;
let h, store, db, env, call;
before(async () => { h = await setup(); ({ store, db, env, call } = h); });
beforeEach(async () => { await h.reset(); delete env.LIMIT; delete env.FCM_SA; resetTokenCache(); });
after(async () => h?.close());
async function create(name = "Owner", token, groupName = "Home") {
  const r = await call("POST", "/v3/groups", { name, groupName }, token);
  assert.equal(r.status, 200);
  return r.body;
}
async function invite(owner, gid = owner.groupId) {
  const r = await call("POST", `/v3/groups/${gid}/invites`, null, owner.token);
  assert.equal(r.status, 200);
  return r.body.code;
}
async function join(owner, name = "Member", token) {
  const code = await invite(owner);
  const r = await call("POST", "/v3/join", { code, name }, token);
  assert.equal(r.status, 200);
  return r.body;
}
async function error(method, path, payload, token, status, code, headers) {
  const r = await call(method, path, payload, token, headers);
  assert.equal(r.status, status, `${method} ${path}: ${JSON.stringify(r.body)}`);
  assert.deepEqual(r.body, { error: code });
}
const car = (id = "c_00000001") => ({ id, name: "Car", color: 1, plate: null, order: 0, updated: 10 });
const park = (id, who, prev = null) => ({ park: { id, atMs: id, state: "DRIVING", photo: true }, by: who.name, byId: who.id, drivingBy: who.name, drivingById: who.id, prev, updated: id });

test("create with and without auth returns state and keeps one device name across groups", async () => {
  const a = await create();
  assert.match(a.deviceId, /^[0-9a-f]{16}$/); assert.match(a.secret, /^[0-9a-f]{64}$/);
  assert.equal(a.token, `${a.deviceId}.${a.secret}`);
  assert.deepEqual(a.state.me, { id: a.deviceId, name: "Owner" });
  assert.deepEqual(a.state.cars, []); assert.deepEqual(a.state.parks, {});
  assert.deepEqual(a.state.groups[0], { id: a.groupId, name: "Home", owner: true,
    members: [{ id: a.deviceId, name: "Owner", me: true, owner: true, lastSeen: (await store.device(a.deviceId)).lastSeen }] });
  const b = await create("Ignored", a.token, "Work");
  assert.deepEqual(Object.keys(b).sort(), ["groupId", "state"]);
  assert.equal(b.state.me.name, "Owner"); assert.equal(b.state.groups.length, 2);
  assert.equal((await call("GET", "/v3/state", null, a.token)).status, 200);
});

test("create validates names, optional credentials, body bytes and the group cap", async () => {
  for (const payload of [{}, { name: "A", groupName: " " }, { groupName: "Home" }, { name: "A", groupName: "x".repeat(41) }]) {
    await error("POST", "/v3/groups", payload, null, 400, "name");
  }
  await error("POST", "/v3/groups", { name: "A", groupName: "Home" }, "bad", 401, "auth");
  await error("POST", "/v3/groups", { name: "A", groupName: "x".repeat(4100) }, null, 413, "size");
  await error("POST", "/v3/groups", {}, null, 413, "size", { "content-length": "5000" });
  const a = await create();
  await create(null, a.token); await create(null, a.token);
  await error("POST", "/v3/groups", { groupName: "Fourth" }, a.token, 409, "groups");
});

test("peek does not consume invitations and both join forms return the selected group's state", async () => {
  const a = await create(), code = await invite(a);
  for (let i = 0; i < 2; i++) assert.deepEqual((await call("POST", "/v3/peek", { code })).body, { groupName: "Home", count: 1 });
  const b = (await call("POST", "/v3/join", { code, name: "Member" })).body;
  assert.equal(b.token, `${b.deviceId}.${b.secret}`); assert.equal(b.groupId, a.groupId);
  assert.equal(b.state.groups[0].owner, false); assert.equal(b.state.groups[0].members.length, 2);
  const c = await create("Third");
  const joined = await join(a, "Ignored", c.token);
  assert.deepEqual(Object.keys(joined).sort(), ["groupId", "state"]);
  assert.equal(joined.state.me.name, "Third"); assert.equal(joined.state.groups.length, 2);
  await error("POST", "/v3/peek", { code }, null, 404, "code");
});

test("join validates code and name, rejects bad auth, and preserves an existing member's invite", async () => {
  const a = await create(), code = await invite(a);
  await error("POST", "/v3/join", { code: "short", name: "B" }, null, 400, "code");
  await error("POST", "/v3/peek", { code: "short" }, null, 400, "code");
  await error("POST", "/v3/join", { code: "ABCD-EFGH-JKLM", name: "B" }, null, 404, "code");
  await error("POST", "/v3/join", { code }, null, 400, "name");
  await error("POST", "/v3/join", { code }, "bad", 401, "auth");
  await error("POST", "/v3/join", { code }, a.token, 409, "member");
  assert.equal((await call("POST", "/v3/join", { code, name: "B" })).status, 200);
  const expired = await invite(a);
  await db.prepare("UPDATE invites SET expires=0").run();
  await error("POST", "/v3/join", { code: expired, name: "B" }, null, 404, "code");
});

test("full joins preserve invites and rejected new devices are rolled back", async () => {
  const a = await create();
  for (let i = 0; i < 7; i++) {
    const code = normCode(await invite(a));
    await store.join({ me: `m${i}`, newDevice: { id: `m${i}`, name: "M", secretHash: "hash", created: 1 },
      code, codeHash: await sha256(code), now: Date.now() });
  }
  const code = await invite(a);
  await error("POST", "/v3/join", { code, name: "B" }, null, 409, "full");
  assert.equal((await db.prepare("SELECT count(*) AS n FROM devices").first()).n, 8);
  assert.equal((await call("POST", "/v3/peek", { code })).body.count, 8);
  const b = await create("B");
  await error("POST", "/v3/join", { code }, b.token, 409, "full");
});

test("joining a fourth group is rejected without using its invite", async () => {
  const a = await create(), b = await create("B");
  await create(null, b.token); await create(null, b.token);
  const code = await invite(a);
  await error("POST", "/v3/join", { code }, b.token, 409, "groups");
  assert.equal((await call("POST", "/v3/peek", { code })).status, 200);
});

test("new invites close older ordinary invites but preserve request-held invitations", async () => {
  const a = await create(), old = await invite(a), secret = "a".repeat(32);
  assert.deepEqual((await call("PUT", "/v3/requests", { req: await sha256(secret), groupId: a.groupId }, a.token)).body, { ok: true });
  const poll = await call("POST", "/v3/requests/poll", { secret });
  assert.equal(poll.status, 200); assert.equal(poll.body.byName, "Owner"); assert.equal(poll.body.groupName, "Home");
  assert.match(poll.body.code, /^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  const next = await call("POST", `/v3/groups/${a.groupId}/invites`, null, a.token);
  assert.equal(next.body.link, `${env.LINK_BASE}#${next.body.code}`); assert.ok(next.body.expiresAt > Date.now());
  await error("POST", "/v3/peek", { code: old }, null, 404, "code");
  assert.equal((await call("POST", "/v3/peek", { code: poll.body.code })).status, 200);
  assert.equal((await call("POST", "/v3/join", { code: poll.body.code, name: "B" })).status, 200);
  await error("POST", "/v3/requests/poll", { secret }, null, 404, "req");
});

test("request acceptance validates chosen membership, first accept wins, and five live requests cap the group", async () => {
  const a = await create(), b = await create("B");
  await error("PUT", "/v3/requests", { req: "bad", groupId: a.groupId }, a.token, 400, "req");
  await error("PUT", "/v3/requests", { req: "a".repeat(64), groupId: b.groupId }, a.token, 404, "group");
  for (let i = 0; i < 5; i++) assert.equal((await call("PUT", "/v3/requests", { req: String(i).repeat(64), groupId: a.groupId }, a.token)).status, 200);
  await error("PUT", "/v3/requests", { req: "0".repeat(64), groupId: a.groupId }, a.token, 409, "taken");
  await error("PUT", "/v3/requests", { req: "5".repeat(64), groupId: a.groupId }, a.token, 429, "many");
});

test("request poll and cancel validate secrets; cancel is idempotent and closes the invitation", async () => {
  const a = await create(), secret = "a".repeat(32);
  for (const action of ["poll", "cancel"]) await error("POST", `/v3/requests/${action}`, { secret: "bad" }, null, 400, "req");
  await error("POST", "/v3/requests/poll", { secret }, null, 404, "req");
  await call("PUT", "/v3/requests", { req: await sha256(secret), groupId: a.groupId }, a.token);
  const code = (await call("POST", "/v3/requests/poll", { secret })).body.code;
  for (let i = 0; i < 2; i++) assert.deepEqual((await call("POST", "/v3/requests/cancel", { secret })).body, { ok: true });
  await error("POST", "/v3/peek", { code }, null, 404, "code");
  await call("PUT", "/v3/requests", { req: await sha256(secret), groupId: a.groupId }, a.token);
  await db.prepare("UPDATE requests SET expires=0").run();
  await error("POST", "/v3/requests/poll", { secret }, null, 404, "req");
});

test("authenticated group routes hide other groups and require a valid device", async () => {
  const a = await create(), b = await create("B");
  const routes = [
    ["GET", "/v3/state"], ["PUT", "/v3/token", { fcmToken: "t" }], ["DELETE", "/v3/me"],
    ["PUT", "/v3/requests", { req: "a".repeat(64), groupId: b.groupId }],
    ["POST", `/v3/groups/${b.groupId}/invites`], ["PUT", `/v3/groups/${b.groupId}`, { name: "New" }],
    ["DELETE", `/v3/groups/${b.groupId}/me`], ["DELETE", `/v3/groups/${b.groupId}/members/${b.deviceId}`],
  ];
  for (const [method, path, body] of routes) await error(method, path, body, null, 401, "auth");
  for (const [method, path, body] of routes.slice(3)) await error(method, path, body, a.token, 404, "group");
  await error("GET", "/v3/missing", null, a.token, 404, "route");
  await error("GET", "/v3/state", null, `${a.deviceId}.${"0".repeat(64)}`, 401, "auth");
});

test("rename is owner-only, trims names and rejects blank or 41 characters", async () => {
  const a = await create(), b = await join(a);
  const path = `/v3/groups/${a.groupId}`;
  await error("PUT", path, { name: "New" }, b.token, 403, "owner");
  for (const name of [" ", "x".repeat(41)]) await error("PUT", path, { name }, a.token, 400, "name");
  assert.deepEqual((await call("PUT", path, { name: " New " }, a.token)).body, { ok: true });
  assert.equal((await call("GET", "/v3/state", null, b.token)).body.groups[0].name, "New");
});

test("token registration transfers a token to the current device and permits clearing it", async () => {
  const a = await create(), b = await join(a);
  for (const user of [a, b]) assert.deepEqual((await call("PUT", "/v3/token", { fcmToken: "phone" }, user.token)).body, { ok: true });
  assert.equal((await store.device(a.deviceId)).fcmToken, null);
  assert.equal((await store.device(b.deviceId)).fcmToken, "phone");
  await call("PUT", "/v3/token", { fcmToken: null }, b.token);
  assert.equal((await store.device(b.deviceId)).fcmToken, null);
});

test("member removal enforces idle threshold and owner protection, including null lastSeen", async () => {
  const a = await create(), b = await join(a), c = await join(a, "Third");
  const path = id => `/v3/groups/${a.groupId}/members/${id}`;
  await error("DELETE", path("0".repeat(16)), null, a.token, 404, "member");
  await error("DELETE", path(c.deviceId), null, b.token, 403, "owner");
  await db.prepare("UPDATE devices SET last_seen=?,created=? WHERE id=?").bind(Date.now() - 31 * DAY, 1, a.deviceId).run();
  await error("DELETE", path(a.deviceId), null, b.token, 403, "owner");
  await db.prepare("UPDATE devices SET last_seen=NULL,created=? WHERE id=?").bind(Date.now() - 31 * DAY, c.deviceId).run();
  assert.deepEqual((await call("DELETE", path(c.deviceId), null, b.token)).body, { ok: true });
  await error("GET", "/v3/state", null, c.token, 401, "auth");
  assert.equal((await call("DELETE", path(b.deviceId), null, a.token)).status, 200);
});

test("leaving one group keeps the device; last leave deletes it and invalidates its credential", async () => {
  const a = await create(), b = await join(a);
  const second = await create(null, a.token, "Work");
  assert.deepEqual((await call("DELETE", `/v3/groups/${a.groupId}/me`, null, a.token)).body, { ok: true, device: "kept" });
  assert.equal((await call("GET", "/v3/state", null, b.token)).body.groups[0].owner, true);
  assert.deepEqual((await call("DELETE", `/v3/groups/${second.groupId}/me`, null, a.token)).body, { ok: true, device: "gone" });
  await error("GET", "/v3/state", null, a.token, 401, "auth");
});

test("delete me leaves every group and invalidates a device with two memberships", async () => {
  const a = await create(), b = await join(a);
  await create(null, a.token, "Work");
  assert.deepEqual((await call("DELETE", "/v3/me", null, a.token)).body, { ok: true });
  await error("GET", "/v3/state", null, a.token, 401, "auth");
  assert.equal((await call("GET", "/v3/state", null, b.token)).body.groups[0].owner, true);
});

test("rate limits share v2 anonymous keys, while state is limited by device", async () => {
  const a = await create(), keys = [];
  env.LIMIT = { limit: async ({ key }) => { keys.push(key); return { success: false }; } };
  for (const [path, payload] of [["groups", {}], ["join", {}], ["peek", {}], ["requests/poll", {}], ["requests/cancel", {}]]) {
    await error("POST", `/v3/${path}`, payload, null, 429, "slow", { "cf-connecting-ip": "test-ip" });
  }
  assert.deepEqual(keys, ["/v2/groups:test-ip", "/v2/join:test-ip", "/v2/join:test-ip", "/v2/requests:test-ip", "/v2/requests:test-ip"]);
  await error("GET", "/v3/state", null, a.token, 429, "slow");
  assert.equal(keys.at(-1), `state:${a.deviceId}`);
  assert.equal((await call("POST", "/v3/groups", { groupName: "Work" }, a.token)).status, 200);
  env.LIMIT = { limit: async () => ({ success: true }) };
  assert.equal((await call("GET", "/v3/state", null, a.token)).status, 200);
});

test("auth returns the pre-touch row, throttles touches, and rejects malformed bearer credentials", async () => {
  const { auth } = await import("../src/core.js");
  const a = await create();
  await db.prepare("UPDATE devices SET last_seen=1 WHERE id=?").bind(a.deviceId).run();
  const req = token => new Request("https://parking.test/v3/state", { headers: { authorization: `Bearer ${token}` } });
  assert.equal((await auth(req(a.token), store)).lastSeen, 1);
  const touched = (await store.device(a.deviceId)).lastSeen;
  assert.ok(touched > 1);
  await auth(req(a.token), store);
  assert.equal((await store.device(a.deviceId)).lastSeen, touched);
  for (const token of ["bad", a.token.toUpperCase(), `${a.token}x`, `${"f".repeat(16)}.${a.secret}`]) assert.equal(await auth(req(token), store), null);
});

test("state dedupes visible cars, hides foreign group ids and masks current and previous parkers live", async () => {
  const a = await create(), b = await join(a);
  const second = await create(null, a.token, "Work"), c = await join({ ...a, groupId: second.groupId }, "Third");
  await store.createCar(a.deviceId, car(), 10);
  await store.createCar(b.deviceId, car("c_00000002"), 10);
  await store.include(car().id, a.groupId, a.deviceId, 10); await store.include(car().id, second.groupId, a.deviceId, 11);
  const who = { id: c.deviceId, name: "Third" };
  await store.casPark3(car().id, null, park(2, who, park(1, who)), a.deviceId);
  const state = (await call("GET", "/v3/state", null, b.token)).body;
  assert.deepEqual(state.cars.find(c => !c.mine).groups, [a.groupId]);
  assert.equal(state.cars.find(c => c.mine).ownerId, b.deviceId);
  assert.equal(state.cars.find(c => !c.mine).ownerName, "Owner");
  for (const p of [state.parks[car().id], state.parks[car().id].prev]) {
    assert.equal(p.by, null); assert.equal(p.byId, null); assert.equal(p.byOther, true);
    assert.equal(p.drivingBy, null); assert.equal(p.drivingById, null); assert.equal(p.drivingOther, true); assert.equal(p.mine, false);
  }
  const own = (await call("GET", "/v3/state", null, a.token)).body;
  assert.equal(own.cars.length, 1); assert.deepEqual(own.cars[0].groups, [a.groupId, second.groupId]);
  assert.equal(own.parks[car().id].by, "Third");
  await call("DELETE", `/v3/groups/${second.groupId}/me`, null, c.token);
  assert.equal((await call("GET", "/v3/state", null, a.token)).body.parks[car().id].byOther, true);
  const { present } = await import("../src/core.js");
  assert.equal(present(park(3, { id: b.deviceId, name: "Member" }), { id: b.deviceId }, new Set()).mine, true);
});

async function pushCapture(t) {
  const kp = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", kp.privateKey));
  env.FCM_SA = JSON.stringify({ client_email: "test@example.test", project_id: "test", private_key: `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...der))}\n-----END PRIVATE KEY-----` });
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url, init) => {
    if (url === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "local", expires_in: 3600 });
    assert.match(url, /^https:\/\/fcm.googleapis.com\//);
    const message = JSON.parse(init.body).message;
    sent.push(message);
    return message.token === "dead" ? new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 }) : Response.json({});
  });
  return sent;
}

test("group and car notifications collapse, dedupe recipients, exclude the actor and clear dead tokens", async t => {
  const { notifyCars, notifyGroup } = await import("../src/core.js");
  const a = await create(), b = await join(a), c = await join(a, "Third");
  await store.createCar(a.deviceId, car(), 10); await store.include(car().id, a.groupId, a.deviceId, 10);
  await store.setToken(a.deviceId, "actor"); await store.setToken(b.deviceId, "live"); await store.setToken(c.deviceId, "dead");
  const sent = await pushCapture(t);
  await notifyCars(env, store, [car().id], a.deviceId, { type: "park", carId: car().id });
  assert.deepEqual(sent.map(m => m.token).sort(), ["dead", "live"]);
  assert.ok(sent.every(m => m.android.collapse_key === `park:${car().id}`));
  assert.equal((await store.device(c.deviceId)).fcmToken, null);
  sent.length = 0;
  await notifyGroup(env, store, a.groupId, a.deviceId, { type: "members", groupId: a.groupId });
  assert.equal(sent[0].android.collapse_key, `members:${a.groupId}`);
  sent.length = 0;
  await notifyCars(env, store, [car().id], a.deviceId, { type: "cars" });
  assert.equal(sent[0].android.collapse_key, "cars");
});

test("leave reads audiences before removing links and deletes both photo prefixes only when the device is gone", async t => {
  const a = await create(), b = await join(a), second = await create(null, a.token, "Work");
  const c = await join({ ...a, groupId: second.groupId }, "Third");
  await store.createCar(a.deviceId, car(), 10);
  for (const gid of [a.groupId, second.groupId]) await store.include(car().id, gid, a.deviceId, 10);
  await db.prepare("UPDATE cars3 SET legacy_group='old',legacy_car_id='old-car'").run();
  h.objects.set(`c/${car().id}/1.jpg`, "new"); h.objects.set("g/old/old-car/1.jpg", "old"); h.objects.set("c/other/1.jpg", "keep");
  for (const [user, token] of [[a, "actor"], [b, "home"], [c, "work"]]) await store.setToken(user.deviceId, token);
  const sent = await pushCapture(t);
  await call("DELETE", `/v3/groups/${a.groupId}/me`, null, a.token);
  assert.equal(h.objects.size, 3);
  assert.deepEqual(sent.filter(m => m.data.type === "cars").map(m => m.token).sort(), ["home", "work"]);
  assert.deepEqual(sent.filter(m => m.data.type === "members").map(m => m.token), ["home"]);
  sent.length = 0;
  await call("DELETE", `/v3/groups/${second.groupId}/me`, null, a.token);
  assert.deepEqual([...h.objects.keys()], ["c/other/1.jpg"]);
  assert.deepEqual(sent.filter(m => m.data.type === "cars").map(m => m.token), ["work"]);
});

test("create after concurrent final leave cannot resurrect a deleted device's membership", async t => {
  const a = await create();
  const original = store.createGroup.bind(store);
  t.mock.method(store, "createGroup", async args => {
    await store.leave(a.groupId, a.deviceId, []);
    return original(args);
  });
  await error("POST", "/v3/groups", { groupName: "Too late" }, a.token, 401, "auth");
  assert.equal((await db.prepare("SELECT count(*) AS n FROM groups").first()).n, 0);
  assert.equal((await db.prepare("SELECT count(*) AS n FROM memberships").first()).n, 0);
});

test("join after concurrent final leave preserves the invite and creates no orphan membership", async t => {
  const a = await create(), b = await create("B"), code = await invite(a);
  const original = store.join.bind(store);
  t.mock.method(store, "join", async args => {
    await store.leave(b.groupId, b.deviceId, []);
    return original(args);
  });
  await error("POST", "/v3/join", { code }, b.token, 401, "auth");
  assert.deepEqual(await store.memberships(b.deviceId), []);
  assert.equal((await call("POST", "/v3/peek", { code })).body.count, 1);
});

test("request acceptance after concurrent membership removal cannot create a group invite", async t => {
  const a = await create(), b = await join(a);
  await create(null, b.token, "Other");
  const original = store.acceptRequest.bind(store);
  t.mock.method(store, "acceptRequest", async args => {
    await store.leave(a.groupId, b.deviceId, []);
    return original(args);
  });
  await error("PUT", "/v3/requests", { req: "a".repeat(64), groupId: a.groupId }, b.token, 404, "group");
  assert.equal((await db.prepare("SELECT count(*) AS n FROM requests").first()).n, 0);
  assert.equal((await db.prepare("SELECT count(*) AS n FROM invites").first()).n, 0);
});

test("leave also closes requests accepted after its initial invite snapshot", async t => {
  const a = await create(), b = await join(a);
  const original = store.memberRequests.bind(store);
  let injected = false;
  t.mock.method(store, "memberRequests", async (gid, id) => {
    const rows = await original(gid, id);
    if (!injected) {
      injected = true;
      await store.acceptRequest({ reqHash: "a".repeat(64), groupId: gid, me: id, myName: "Member",
        code: "ABCDEFGHJKLM", codeHash: await sha256("ABCDEFGHJKLM"), now: Date.now() });
    }
    return rows;
  });
  assert.equal((await call("DELETE", `/v3/groups/${a.groupId}/me`, null, b.token)).status, 200);
  assert.equal(await store.request("a".repeat(64), Date.now()), null);
  await error("POST", "/v3/peek", { code: "ABCD-EFGH-JKLM" }, null, 404, "code");
});

// ---- W4: cars, parks, masking, fan-out, cron ----
const C = "c_00000001";
const pk = (id, extra = {}) => ({ park: { id, carId: C, atMs: id, floor: "B2", state: "PARKED", ...extra } });
const jpeg = (token, path) => call("PUT", path, new Uint8Array(1), token, { "content-length": "1", "content-type": "image/jpeg" });
const setPhoto = () => db.prepare("UPDATE parks3 SET json=json_set(json,'$.photo',json('true'))").run();

// Owner O in X and Y, MX in X only, MY in Y only, stranger S; car C shared into X and Y.
async function world() {
  const o = await create("Owner"), y = await create(null, o.token, "Y");
  const mx = await join(o, "MX"), my = await join({ ...o, groupId: y.groupId }, "MY"), s = await create("S");
  assert.equal((await call("PUT", `/v3/cars/${C}`, { car: car() }, o.token)).status, 200);
  for (const gid of [o.groupId, y.groupId]) assert.deepEqual((await call("PUT", `/v3/cars/${C}/groups/${gid}`, null, o.token)).body, { ok: true });
  return { o, mx, my, s, X: o.groupId, Y: y.groupId };
}

test("car routes authorize owner, members of either group and strangers", async () => {
  const { o, mx, my, s, X, Y } = await world();
  const routes = n => [
    ["DELETE", `/v3/cars/${C}`], ["PUT", `/v3/cars/${C}/groups/${X}`], ["DELETE", `/v3/cars/${C}/groups/${Y}`],
    ["PUT", `/v3/cars/${C}/park`, pk(n)], ["POST", `/v3/cars/${C}/state`, { state: "DRIVING", parkId: n }],
    ["POST", `/v3/cars/${C}/undo`, { parkId: n }], ["GET", `/v3/cars/${C}/photo?park=${n}`],
  ];
  for (const [method, path, payload] of routes(1)) await error(method, path, payload, s.token, 404, "car");
  await error("PUT", `/v3/cars/${C}`, { car: car() }, s.token, 409, "id");
  let n = 100;
  for (const m of [mx, my]) {
    await error("PUT", `/v3/cars/${C}`, { car: { ...car(), name: "Hijack" } }, m.token, 403, "owner");
    for (const [method, path, payload] of routes(n).slice(0, 3)) await error(method, path, payload, m.token, 403, "owner");
    assert.equal((await call("PUT", `/v3/cars/${C}/park`, pk(n), m.token)).status, 200);
    assert.equal((await call("POST", `/v3/cars/${C}/state`, { state: "DRIVING", parkId: n }, m.token)).status, 200);
    assert.equal((await jpeg(m.token, `/v3/cars/${C}/photo?park=${n}`)).status, 200);
    assert.equal((await call("GET", `/v3/cars/${C}/photo?park=${n}`, null, m.token)).status, 200);
    assert.equal((await call("POST", `/v3/cars/${C}/undo`, { parkId: n }, m.token)).status, 200);
    n += 100;
  }
  assert.equal((await store.car(C)).car.name, "Car");
  assert.equal((await call("PUT", `/v3/cars/${C}`, { car: { ...car(), name: "New" } }, o.token)).body.car.name, "New");
  await error("PUT", `/v3/cars/${C}/groups/${s.groupId}`, null, o.token, 404, "group");
  await error("PUT", `/v3/cars/${C}`, { car: { ...car(), color: "x" } }, o.token, 400, "car");
  for (let i = 2; i <= 4; i++) assert.equal((await call("PUT", `/v3/cars/c_0000000${i}`, { car: car(`c_0000000${i}`) }, o.token)).status, 200);
  await error("PUT", "/v3/cars/c_00000005", { car: car("c_00000005") }, o.token, 409, "cars");
  assert.deepEqual((await call("DELETE", `/v3/cars/${C}`, null, o.token)).body, { ok: true });
  await error("PUT", `/v3/cars/${C}/park`, pk(900), mx.token, 404, "car");
});

test("excluding a group ends its members' access unless another group still shares the car", async () => {
  const { o, mx, my, X, Y } = await world();
  assert.deepEqual((await call("DELETE", `/v3/cars/${C}/groups/${X}`, null, o.token)).body, { ok: true });
  assert.deepEqual((await call("DELETE", `/v3/cars/${C}/groups/${X}`, null, o.token)).body, { ok: true });
  await error("PUT", `/v3/cars/${C}/park`, pk(100), mx.token, 404, "car");
  assert.equal((await call("PUT", `/v3/cars/${C}/park`, pk(100), my.token)).status, 200);
  await join({ ...o, groupId: Y }, "Both", mx.token);
  assert.equal((await call("PUT", `/v3/cars/${C}/park`, pk(200), mx.token)).status, 200);
  assert.deepEqual(await store.carGroups([C]), { [C]: [Y] });
});

test("D13: parkers outside the viewer's shared groups are masked in every park body", async () => {
  const { o, mx, my, Y } = await world();
  const masked = p => {
    assert.equal(p.by, null); assert.equal(p.byId, null); assert.equal(p.byOther, true);
    assert.equal(p.drivingBy, null); assert.equal(p.drivingById, null); assert.equal(p.drivingOther, true);
  };
  assert.equal((await call("PUT", `/v3/cars/${C}/park`, pk(100), my.token)).body.park.by, "MY");
  assert.equal((await call("POST", `/v3/cars/${C}/state`, { state: "DRIVING", parkId: 100 }, my.token)).body.park.drivingBy, "MY");
  masked((await call("GET", "/v3/state", null, mx.token)).body.parks[C]);
  const stale = await call("PUT", `/v3/cars/${C}/park`, pk(50), mx.token);
  assert.equal(stale.status, 409); assert.equal(stale.body.error, "stale"); masked(stale.body.park);
  await call("PUT", `/v3/cars/${C}/park`, pk(200, { state: "DRIVING" }), my.token);
  const undone = await call("POST", `/v3/cars/${C}/undo`, { parkId: 200 }, mx.token);
  assert.equal(undone.status, 200); masked(undone.body.park);
  const own = (await call("GET", "/v3/state", null, o.token)).body.parks[C];
  assert.equal(own.by, "MY"); assert.equal(own.drivingBy, "MY"); assert.equal(own.byOther, false);
  await call("DELETE", `/v3/groups/${Y}/me`, null, my.token);
  masked((await call("GET", "/v3/state", null, o.token)).body.parks[C]);
  const moved = await call("POST", `/v3/cars/${C}/state`, { state: "PARKED", parkId: 1 }, mx.token);
  assert.equal(moved.status, 409); assert.equal(moved.body.error, "moved"); masked(moved.body.park);
});

test("LEFT is stored with leftAt, masked like DRIVING, validated, and replaced by a new park", async () => {
  const { o, mx, my } = await world();
  await call("PUT", `/v3/cars/${C}/park`, pk(100, { state: "DRIVING" }), my.token);
  await error("POST", `/v3/cars/${C}/state`, { state: "LEFT", parkId: 100 }, my.token, 400, "state");
  await error("POST", `/v3/cars/${C}/state`, { state: "GONE", parkId: 100 }, my.token, 400, "state");
  const left = await call("POST", `/v3/cars/${C}/state`, { state: "LEFT", parkId: 100, atMs: 150 }, my.token);
  assert.equal(left.status, 200);
  assert.equal(left.body.park.state, "LEFT"); assert.equal(left.body.park.leftAt, 150); assert.equal(left.body.park.drivingBy, "MY");
  const seen = (await call("GET", "/v3/state", null, mx.token)).body.parks[C];
  assert.equal(seen.state, "LEFT"); assert.equal(seen.leftAt, 150); assert.equal(seen.drivingBy, null); assert.equal(seen.drivingOther, true);
  assert.equal((await call("GET", "/v3/state", null, o.token)).body.parks[C].drivingBy, "MY");
  const parked = (await call("POST", `/v3/cars/${C}/state`, { state: "PARKED", parkId: 100 }, my.token)).body.park;
  assert.equal(parked.leftAt, undefined); assert.equal(parked.drivingBy, null);
  await call("POST", `/v3/cars/${C}/state`, { state: "LEFT", parkId: 100, atMs: 160 }, my.token);
  const next = (await call("PUT", `/v3/cars/${C}/park`, pk(200), mx.token)).body.park;
  assert.equal(next.state, "PARKED"); assert.equal(next.leftAt, undefined); assert.equal(next.drivingOther, false);
  assert.equal(next.prev.state, "LEFT");
  await error("PUT", `/v3/cars/${C}/park`, pk(300, { state: "LEFT" }), mx.token, 400, "park");
  assert.equal((await call("PUT", `/v3/cars/${C}/park`, pk(300, { state: "LEFT", leftAt: 300 }), mx.token)).body.park.leftAt, 300);
});

test("a lost park CAS re-judges recency: an older row loses to the incoming park, a newer row answers stale", async t => {
  const { o, mx } = await world();
  const row = id => ({ park: { ...pk(id).park, photo: false }, prev: null, by: "Owner", byId: o.deviceId, drivingBy: null, drivingById: null, updated: id });
  const original = store.casPark3.bind(store);
  let rival;
  t.mock.method(store, "casPark3", async (...args) => {
    if (rival) { const r = rival; rival = null; await original(C, await store.park3(C), r, o.deviceId); return false; }
    return original(...args);
  });
  rival = row(100);
  const won = await call("PUT", `/v3/cars/${C}/park`, pk(200), mx.token);
  assert.equal(won.status, 200);
  const stored = await store.park3(C);
  assert.equal(stored.park.id, 200); assert.equal(stored.prev.park.id, 100);
  rival = row(400);
  const lost = await call("PUT", `/v3/cars/${C}/park`, pk(300), mx.token);
  assert.equal(lost.status, 409); assert.equal(lost.body.error, "stale"); assert.equal(lost.body.park.id, 400);
  const moved = await call("POST", `/v3/cars/${C}/state`, { state: "DRIVING", parkId: 300 }, mx.token);
  assert.equal(moved.status, 409); assert.equal(moved.body.error, "moved"); assert.equal(moved.body.park.id, 400);
});

test("park pushes reach each phone once, never the actor; car edits push cars", async t => {
  const { o, mx, my, X, Y } = await world();
  await join({ ...o, groupId: Y }, "Both", mx.token);
  await store.setToken(mx.deviceId, "mx"); await store.setToken(my.deviceId, "shared");
  await db.prepare("UPDATE devices SET fcm_token='shared' WHERE id=?").bind(o.deviceId).run();
  const sent = await pushCapture(t);
  await call("PUT", `/v3/cars/${C}/park`, pk(100), mx.token);
  assert.deepEqual(sent.map(m => [m.token, m.data.type]), [["shared", "park"]]);
  sent.length = 0;
  await call("PUT", `/v3/cars/${C}`, { car: car() }, o.token);
  assert.deepEqual(sent.map(m => [m.token, m.data.type]).sort(), [["mx", "cars"], ["shared", "cars"]]);
  sent.length = 0;
  await call("DELETE", `/v3/cars/${C}/groups/${X}`, null, o.token);
  assert.deepEqual(sent.map(m => m.token), ["mx"]);
  sent.length = 0;
  await call("DELETE", `/v3/cars/${C}`, null, my.token);
  assert.deepEqual(sent, []);
  await call("DELETE", `/v3/cars/${C}`, null, o.token);
  assert.deepEqual(sent.map(m => [m.token, m.data.type]).sort(), [["mx", "cars"], ["shared", "cars"]]);
});

test("photos use the car key, fall back to the legacy key, and every delete path removes both", async () => {
  const { o, mx } = await world();
  await db.prepare("UPDATE cars3 SET legacy_group='oldg',legacy_car_id='oldc'").run();
  await call("PUT", `/v3/cars/${C}/park`, pk(100), mx.token);
  await setPhoto();
  h.objects.set("g/oldg/oldc/100.jpg", "legacy");
  assert.equal((await call("GET", `/v3/cars/${C}/photo?park=100`, null, mx.token)).body, "legacy");
  assert.equal((await jpeg(mx.token, `/v3/cars/${C}/photo?park=100`)).status, 200);
  assert.ok(h.objects.has(`c/${C}/100.jpg`));
  await call("POST", `/v3/cars/${C}/undo`, { parkId: 100 }, mx.token);
  assert.equal(h.objects.size, 0, "undo");
  const both = id => { h.objects.set(`c/${C}/${id}.jpg`, "x"); h.objects.set(`g/oldg/oldc/${id}.jpg`, "x"); };
  for (const id of [100, 200]) { await call("PUT", `/v3/cars/${C}/park`, pk(id), mx.token); both(id); await setPhoto(); }
  await call("PUT", `/v3/cars/${C}/park`, pk(300), mx.token);
  assert.deepEqual([...h.objects.keys()].sort(), [`c/${C}/200.jpg`, "g/oldg/oldc/200.jpg"], "replace");
  const { scheduled } = await import("../src/index.js");
  await scheduled({ scheduledTime: 30 * DAY + 250 }, env, null, store);
  assert.equal(h.objects.size, 0, "cron");
  assert.equal((await store.park3(C)).prev.park.photo, false);
  both(300); h.objects.set("c/c_00000009/1.jpg", "keep");
  await call("DELETE", `/v3/cars/${C}`, null, o.token);
  assert.deepEqual([...h.objects.keys()], ["c/c_00000009/1.jpg"], "delete");
});
