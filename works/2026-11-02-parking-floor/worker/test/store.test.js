import { test, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { setup, statements } from "./harness.js";

let h, store, db;
before(async () => { h = await setup(); ({ store, db } = h); });
beforeEach(async () => h.reset());
after(async () => h?.close());
const device = id => ({ id, name: id, secretHash: `hash-${id}`, created: 10, lastSeen: 10 });
const car = (id = "c_00000001") => ({ id, name: id, color: 1, plate: null, order: 0, updated: 10 });
const park = (id = 1) => ({ park: { id, atMs: id, photo: true }, prev: null, by: "a", byId: "a", drivingBy: null, drivingById: null, updated: id });
async function group(me = "a", groupId = "g", now = 10) {
  return store.createGroup({ me, newDevice: await store.device(me) ? undefined : device(me), groupId, groupName: groupId, now });
}
async function join(me, gid = "g", now = 20, hash = `invite-${me}`) {
  await store.addInvite({ codeHash: hash, groupId: gid, expires: 1000 });
  return store.join({ me, newDevice: await store.device(me) ? undefined : device(me), codeHash: hash, code: hash, now });
}

test("v3 migration can be reapplied without losing rows", async () => {
  await group();
  for (const sql of statements(readFileSync(new URL("../migrations/2026-11-22-v3.sql", import.meta.url), "utf8"))) await db.prepare(sql).run();
  assert.equal((await store.device("a")).name, "a");
});

test("devices retain identity, touch v2 activity, transfer tokens and clear dead tokens", async () => {
  assert.equal(await store.device("missing"), null);
  await group(); await join("b");
  assert.deepEqual(await store.device("a"), { ...device("a"), fcmToken: null, v2Seen: null });
  await store.touchDevice("a", 30, true);
  await store.touchDevice("a", 40);
  assert.equal((await store.device("a")).lastSeen, 40);
  assert.equal((await store.device("a")).v2Seen, 30);
  await store.setToken("a", "shared"); await store.setToken("b", "shared");
  assert.equal((await store.device("a")).fcmToken, null);
  assert.equal((await store.device("b")).fcmToken, "shared");
  await store.clearToken("shared");
  assert.equal((await store.device("b")).fcmToken, null);
});

test("create group caps memberships and does not leave a failed group", async () => {
  assert.equal(await group(), "ok");
  await group("a", "g2", 12); await group("a", "g3", 11);
  assert.equal(await group("a", "g4"), "groups");
  assert.equal(await store.group("g4"), null);
  await store.renameGroup("g3", "Third");
  assert.deepEqual(await store.memberships("a"), [
    { groupId: "g", name: "g", owner: true, joined: 10 },
    { groupId: "g3", name: "Third", owner: true, joined: 11 },
    { groupId: "g2", name: "g2", owner: true, joined: 12 },
  ]);
});

test("join consumes successful invitations and requests but keeps a member's invite even at the same timestamp", async () => {
  await group();
  await store.addRequest({ reqHash: "req", groupId: "g", code: "invite-b", expires: 1000, byId: "a", byName: "a" }, 10);
  assert.deepEqual(await join("b"), { result: "ok", groupId: "g" });
  assert.equal(await store.invite("invite-b", 20), null);
  assert.equal(await store.request("req", 20), null);
  assert.deepEqual(await join("b"), { result: "member" });
  assert.ok(await store.invite("invite-b", 20));
  assert.deepEqual(await store.groupMembers("g"), [
    { id: "a", name: "a", owner: true, joined: 10, lastSeen: 10, created: 10 },
    { id: "b", name: "b", owner: false, joined: 20, lastSeen: 10, created: 10 },
  ]);
});

test("missing and expired join codes leave no new device", async () => {
  await group();
  for (const hash of ["missing", "expired"]) {
    if (hash === "expired") await store.addInvite({ codeHash: hash, groupId: "g", expires: 1 });
    assert.deepEqual(await store.join({ me: "b", newDevice: device("b"), codeHash: hash, code: hash, now: 20 }), { result: "code" });
    assert.equal(await store.device("b"), null);
  }
});

test("full group refuses joins without consuming invites or leaving devices", async () => {
  await group();
  for (let i = 0; i < 7; i++) await join(`m${i}`);
  assert.deepEqual(await join("b"), { result: "full" });
  assert.equal(await store.device("b"), null);
  assert.ok(await store.invite("invite-b", 20));
});

test("join enforces the device group cap without consuming an invite", async () => {
  await group(); await group("b", "b1"); await group("b", "b2"); await group("b", "b3");
  assert.deepEqual(await join("b"), { result: "groups" });
  assert.ok(await store.invite("invite-b", 20));
});

test("request acceptance is first-writer-wins, capped, and expired requests can be accepted again", async () => {
  await group();
  const args = { reqHash: "r0", groupId: "g", me: "a", myName: "a", code: "code0", codeHash: "hash0", now: 20 };
  assert.equal(await store.acceptRequest(args), "ok");
  assert.equal(await store.acceptRequest({ ...args, code: "other", codeHash: "other" }), "taken");
  assert.equal(await store.invite("other", 20), null);
  for (let i = 1; i < 5; i++) assert.equal(await store.acceptRequest({ ...args, reqHash: `r${i}`, code: `code${i}`, codeHash: `hash${i}` }), "ok");
  assert.equal(await store.acceptRequest({ ...args, reqHash: "r5", code: "code5", codeHash: "hash5" }), "many");
  assert.equal(await store.invite("hash5", 20), null);
  assert.equal(await store.acceptRequest({ ...args, now: 86400_021, code: "new", codeHash: "new" }), "ok");
  assert.equal((await store.request("r0", 86400_021)).code, "new");
});

test("cars enforce ownership, owner cap, identity collisions and live visibility", async () => {
  await group(); await join("b"); await group("s", "other");
  assert.equal(await store.car("missing"), null);
  assert.equal(await store.createCar("a", car(), 10), "ok");
  assert.deepEqual(await store.car(car().id), { car: car(), ownerId: "a", ownerName: "a", legacyGroup: null, legacyCarId: null });
  assert.equal(await store.visible(car().id, "a"), true);
  assert.equal(await store.visible(car().id, "b"), false);
  assert.equal(await store.createCar("b", car(), 10), "id");
  for (let i = 2; i <= 4; i++) await store.createCar("a", car(`c_0000000${i}`), 10);
  assert.equal(await store.createCar("a", car("c_00000005"), 10), "cars");
  assert.equal(await store.updateCar("b", { ...car(), name: "Bad" }), false);
  assert.equal(await store.updateCar("a", { ...car(), name: "Good" }), true);
  assert.equal((await store.car(car().id)).car.name, "Good");
  assert.equal(await store.include(car().id, "g", "a", 20), "ok");
  assert.equal(await store.include(car().id, "g", "a", 30), "ok");
  assert.equal(await store.visible(car().id, "b"), true);
  assert.equal(await store.visible(car().id, "s"), false);
  assert.equal(await store.include(car().id, "other", "a", 20), "group");
  assert.equal(await store.include(car().id, "g", "b", 20), "car");
  assert.equal(await store.include("missing", "g", "a", 20), "car");
  assert.deepEqual(await store.carGroups([car().id]), { [car().id]: ["g"] });
  assert.equal((await store.visibleCars("b")).length, 1);
  assert.equal(await store.exclude(car().id, "g", "b"), "car");
  assert.equal(await store.exclude(car().id, "other", "a"), "group");
  assert.equal(await store.exclude(car().id, "g", "a"), "ok");
  assert.equal(await store.exclude(car().id, "g", "a"), "ok");
  assert.equal(await store.visible(car().id, "b"), false);
});

test("park CAS handles insert, stale update and delete, and fails after visibility ends", async () => {
  await group(); await join("b"); await store.createCar("a", car(), 10);
  const id = car().id;
  assert.equal(await store.park3(id), null);
  assert.equal(await store.casPark3(id, null, park(), "b"), false);
  await store.include(id, "g", "a", 20);
  assert.equal(await store.casPark3(id, null, park(), "b"), true);
  assert.equal(await store.casPark3(id, null, park(2), "a"), false);
  assert.deepEqual(await store.park3(id), park());
  assert.deepEqual(await store.parks3([id]), { [id]: park() });
  assert.equal(await store.casPark3(id, park(), park(2), "b"), true);
  assert.equal(await store.casPark3(id, park(), park(3), "b"), false);
  assert.equal(await store.casRemovePark3(id, park(), "b"), false);
  await store.exclude(id, "g", "a");
  assert.equal(await store.casPark3(id, park(2), park(3), "b"), false);
  assert.equal(await store.casRemovePark3(id, park(2), "b"), false);
  assert.equal(await store.casRemovePark3(id, park(2), "a"), true);
});

test("audiences dedupe tokens, exclude the actor and parkers are scoped to the viewer's car groups", async () => {
  await group(); await join("b"); await group("a", "g2"); await join("b", "g2"); await join("c", "g2");
  await store.createCar("a", car(), 10);
  await store.include(car().id, "g", "a", 20); await store.include(car().id, "g2", "a", 20);
  await store.setToken("a", "actor");
  await db.prepare("UPDATE devices SET fcm_token='same' WHERE id IN ('b','c')").run();
  assert.deepEqual(await store.audience([car().id], "a"), ["same"]);
  assert.deepEqual(await store.groupTokens("g2", "a"), ["same"]);
  assert.equal((await store.visibleCars("b")).length, 1);
  assert.deepEqual((await store.knownParkers("b", [car().id]))[car().id], new Set(["a", "b", "c"]));
  await store.leave("g2", "b", []);
  assert.deepEqual((await store.knownParkers("b", [car().id]))[car().id], new Set(["a", "b"]));
});

test("leaving hands off ownership, deletes accepted requests, and preserves cars while another membership remains", async () => {
  await group(); await join("b", "g", 22); await join("c", "g", 21); await group("a", "g2");
  await store.createCar("a", car(), 10); await store.include(car().id, "g", "a", 20); await store.include(car().id, "g2", "a", 20);
  await store.createCar("b", car("c_00000002"), 10); await store.include("c_00000002", "g", "b", 20);
  await store.acceptRequest({ reqHash: "req", groupId: "g", me: "a", myName: "a", code: "code", codeHash: "hash", now: 20 });
  assert.deepEqual(await store.leave("g", "a", ["hash"]), { deviceGone: false });
  assert.equal((await store.groupMembers("g"))[0].id, "c");
  assert.equal((await store.groupMembers("g"))[0].owner, true);
  assert.deepEqual(await store.carGroups([car().id, "c_00000002"]), { [car().id]: ["g2"], c_00000002: ["g"] });
  assert.ok(await store.car(car().id)); assert.ok(await store.device("a"));
  assert.equal(await store.request("req", 20), null); assert.equal(await store.invite("hash", 20), null);
});

test("last leave deletes the device's cars, parks and links, and drops empty groups without deleting others' cars", async () => {
  await group(); await store.createCar("a", car(), 10); await store.include(car().id, "g", "a", 20);
  await store.casPark3(car().id, null, park(), "a");
  await group("b", "other"); await store.createCar("b", car("c_00000002"), 10);
  await store.addInvite({ codeHash: "open", groupId: "g", expires: 1000 });
  assert.deepEqual(await store.leave("g", "a", []), { deviceGone: true });
  assert.equal(await store.device("a"), null); assert.equal(await store.group("g"), null);
  assert.equal(await store.car(car().id), null); assert.equal(await store.park3(car().id), null);
  assert.deepEqual(await store.carGroups([car().id]), { [car().id]: [] });
  assert.equal(await store.invite("open", 20), null); assert.ok(await store.car("c_00000002"));
});

test("delete car checks ownership and removes its links and park", async () => {
  await group(); await store.createCar("a", car(), 10); await store.include(car().id, "g", "a", 20);
  await store.casPark3(car().id, null, park(), "a");
  assert.equal(await store.deleteCar(car().id, "b"), false);
  assert.ok(await store.park3(car().id));
  assert.equal(await store.deleteCar(car().id, "a"), true);
  assert.equal(await store.deleteCar(car().id, "a"), false);
  assert.equal(await store.park3(car().id), null);
  assert.deepEqual(await store.carGroups([car().id]), { [car().id]: [] });
});

test("photo expiry includes current and previous legacy keys and only clears expired flags", async () => {
  await group(); await store.createCar("a", car(), 10);
  await db.prepare("UPDATE cars3 SET legacy_group='old', legacy_car_id='old-car'").run();
  await store.casPark3(car().id, null, { ...park(2), prev: park(1) }, "a");
  assert.deepEqual(await store.expiredPhotos3(2), [{ carId: car().id, parkId: 1, legacyGroup: "old", legacyCarId: "old-car" }]);
  await store.expirePhotos3(2);
  const row = await store.park3(car().id);
  assert.equal(row.park.photo, true); assert.equal(row.prev.park.photo, false);
  assert.deepEqual(await store.expiredPhotos3(3), [{ carId: car().id, parkId: 2, legacyGroup: "old", legacyCarId: "old-car" }]);
});

test("leave retries a changed request snapshot before removing membership or sharing", async t => {
  await group(); await join("b"); await store.createCar("a", car(), 10); await store.include(car().id, "g", "a", 20);
  const { sha256 } = await import("../src/index.js");
  const codeHash = await sha256("ABCDEFGHJKLM");
  const original = db.batch.bind(db);
  let injected = false;
  t.after(() => { store.db = db; });
  store.db = { prepare: db.prepare.bind(db), batch: async statements => {
    if (!injected) {
      injected = true;
      await store.acceptRequest({ reqHash: "late", groupId: "g", me: "a", myName: "a", code: "ABCDEFGHJKLM", codeHash, now: 20 });
      const result = await original(statements);
      assert.ok((await store.memberships("a")).some(m => m.groupId === "g"));
      assert.deepEqual(await store.carGroups([car().id]), { [car().id]: ["g"] });
      return result;
    }
    return original(statements);
  } };
  assert.deepEqual(await store.leave("g", "a", []), { deviceGone: true });
  assert.equal(await store.invite(codeHash, 20), null);
  assert.equal(await store.request("late", 20), null);
});
