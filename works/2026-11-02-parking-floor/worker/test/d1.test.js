// Concurrent writes on a real local D1: the caps and CAS rules must hold when requests interleave (spec 2.6, 8).
import { test, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { setup } from "./harness.js";
import { normCode, sha256 } from "../src/index.js";

let h, db, call;
before(async () => { h = await setup(); ({ db, call } = h); });
beforeEach(async () => h.reset());
after(async () => h?.close());

const C = "c_00000001";
const count = async (sql, ...args) => (await db.prepare(`SELECT count(*) AS n FROM ${sql}`).bind(...args).first()).n;
const create = async (name = "Owner", token, groupName = "Home") => (await call("POST", "/v3/groups", { name, groupName }, token)).body;
const invite = async (owner, gid = owner.groupId) => (await call("POST", `/v3/groups/${gid}/invites`, null, owner.token)).body.code;
const statuses = rs => rs.map(r => r.status).sort();

test("three concurrent joins into a 7-member group: exactly one gets in, the losers keep their invites", async () => {
  const o = await create();
  for (let i = 0; i < 6; i++) assert.equal((await call("POST", "/v3/join", { code: await invite(o), name: `M${i}` })).status, 200);
  // each loser holds its own code; invites made one by one would close each other, so add them directly
  const codes = [await invite(o)];
  for (const code of ["AAAA-BBBB-CCC2", "AAAA-BBBB-CCC3"]) {
    await h.store.addInvite({ codeHash: await sha256(normCode(code)), groupId: o.groupId, expires: Date.now() + 86400_000 });
    codes.push(code);
  }
  const rs = await Promise.all(codes.map((code, i) => call("POST", "/v3/join", { code, name: `New${i}` })));
  assert.deepEqual(statuses(rs), [200, 409, 409]);
  assert.ok(rs.filter(r => r.status === 409).every(r => r.body.error === "full"));
  assert.equal(await count("memberships WHERE group_id=?", o.groupId), 8);
  assert.equal(await count("devices"), 8, "failed new-device joins leave no orphan device");
  const losers = codes.filter((_, i) => rs[i].status === 409);
  for (const code of losers) assert.equal((await call("POST", "/v3/peek", { code })).status, 200);
});

test("a device in two groups creating two more at once gets one", async () => {
  const o = await create();
  await create(null, o.token, "Second");
  const rs = await Promise.all(["Third", "Fourth"].map(g => call("POST", "/v3/groups", { groupName: g }, o.token)));
  assert.deepEqual(statuses(rs), [200, 409]);
  assert.equal(rs.find(r => r.status === 409).body.error, "groups");
  assert.equal(await count("memberships WHERE device_id=?", o.deviceId), 3);
  assert.equal(await count("groups"), 3);
});

test("concurrent older and newer parks: the newer one is kept in either order", async () => {
  const o = await create();
  const m = (await call("POST", "/v3/join", { code: await invite(o), name: "M" })).body;
  await call("PUT", `/v3/cars/${C}`, { car: { id: C, name: "Car", color: 1, plate: null, order: 0, updated: 1 } }, o.token);
  await call("PUT", `/v3/cars/${C}/groups/${o.groupId}`, null, o.token);
  const put = (token, id) => call("PUT", `/v3/cars/${C}/park`, { park: { id, carId: C, atMs: id, state: "PARKED" } }, token);
  for (const [first, second] of [[100, 200], [400, 300]]) {
    const rs = await Promise.all([put(o.token, first), put(m.token, second)]);
    const newer = Math.max(first, second);
    assert.equal(rs[[first, second].indexOf(newer)].status, 200);
    const older = rs[[first, second].indexOf(Math.min(first, second))];
    assert.ok(older.status === 200 || (older.status === 409 && older.body.error === "stale"));
    assert.equal((await h.store.park3(C)).park.id, newer);
  }
});

test("include racing the owner's leave leaves no link to a group the owner left", async () => {
  for (let i = 0; i < 3; i++) {
    await h.reset();
    const o = await create(), keep = await create(null, o.token, "Keep");
    await call("PUT", `/v3/cars/${C}`, { car: { id: C, name: "Car", color: 1, plate: null, order: 0, updated: 1 } }, o.token);
    await Promise.all([
      call("PUT", `/v3/cars/${C}/groups/${o.groupId}`, null, o.token),
      call("DELETE", `/v3/groups/${o.groupId}/me`, null, o.token),
    ]);
    assert.equal(await count("memberships WHERE group_id=? AND device_id=?", o.groupId, o.deviceId), 0);
    assert.equal(await count("car_groups WHERE group_id=?", o.groupId), 0);
    assert.ok(keep.groupId);
  }
});
