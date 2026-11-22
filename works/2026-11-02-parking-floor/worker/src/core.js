import { fail, json, sha256 } from "./index.js";
import { sendData } from "./fcm.js";

export async function auth(req, store) {
  const match = (req.headers.get("authorization") || "").match(/^Bearer ([0-9a-f]{16})\.([0-9a-f]{64})$/);
  if (!match) return null;
  const me = await store.device(match[1]);
  if (!me || me.secretHash !== await sha256(match[2])) return null;
  const now = Date.now();
  if (!me.lastSeen || now - me.lastSeen >= 60_000) await store.touchDevice(me.id, now);
  return me;
}

export function present(v, me, known) {
  if (!v) return null;
  const visible = id => id === me.id || (id != null && known.has(id));
  const byOther = !visible(v.byId);
  const drivingOther = !!(v.drivingBy || v.drivingById) && !visible(v.drivingById);
  const out = { ...v.park, by: byOther ? null : v.by, byId: byOther ? null : v.byId,
    byOther, mine: v.byId === me.id, drivingBy: drivingOther ? null : v.drivingBy,
    drivingById: drivingOther ? null : v.drivingById, drivingOther };
  if (v.prev) out.prev = present(v.prev, me, known);
  return out;
}

export async function sendTokens(env, store, tokens, data) {
  if (!env.FCM_SA) return { sent: 0, failed: 0, reason: "no-push" };
  let sent = 0, failed = 0, reason = null;
  for (const token of tokens) {
    const r = await sendData(env, token, data).catch(e => ({ ok: false, reason: e.message }));
    if (r.ok) sent++;
    else {
      failed++;
      reason ||= r.reason || null;
      if (r.unregistered) await store.clearToken(token);
    }
  }
  return { sent, failed, reason };
}

export async function notifyCars(env, store, ids, actor, data) {
  return sendTokens(env, store, await store.audience(ids, actor), data);
}

export async function notifyGroup(env, store, gid, actor, data) {
  return sendTokens(env, store, await store.groupTokens(gid, actor), data);
}

export async function leaveGroup(env, store, gid, x, actor = x) {
  const memberships = await store.memberships(x);
  const owned = (await store.visibleCars(x)).filter(c => c.ownerId === x);
  const groups = await store.carGroups(owned.map(c => c.car.id));
  const affected = owned.filter(c => memberships.length === 1 || groups[c.car.id].includes(gid));
  const tokens = await store.audience(affected.map(c => c.car.id), actor);
  const dropHashes = await Promise.all((await store.memberRequests(gid, x)).map(r => sha256(r.code)));
  const result = await store.leave(gid, x, dropHashes);
  if (result.deviceGone) for (const c of owned) await dropCarPhotos(env, c);
  await notifyGroup(env, store, gid, actor, { type: "members", groupId: gid });
  if (affected.length) await sendTokens(env, store, tokens, { type: "cars" });
  return result;
}

export async function stateFor(store, me) {
  const groups = await store.memberships(me.id);
  const gids = groups.map(g => g.groupId);
  // Fetch every group's roster together so the pull has a fixed query count.
  const { results: members } = await store.db.prepare(`SELECT m.group_id,d.id,d.name,m.owner,d.last_seen
    FROM memberships m JOIN devices d ON d.id=m.device_id WHERE m.group_id IN (SELECT value FROM json_each(?))
    ORDER BY m.joined,d.id`).bind(JSON.stringify(gids)).all();
  const cars = await store.visibleCars(me.id), ids = cars.map(c => c.car.id);
  const [links, parks, known] = await Promise.all([store.carGroups(ids), store.parks3(ids), store.knownParkers(me.id, ids)]);
  const mine = new Set(gids);
  return {
    me: { id: me.id, name: me.name },
    groups: groups.map(g => ({ id: g.groupId, name: g.name, owner: g.owner,
      members: members.filter(m => m.group_id === g.groupId).map(m => ({ id: m.id, name: m.name, me: m.id === me.id, owner: !!m.owner, lastSeen: m.last_seen })) })),
    cars: cars.map(c => ({ ...c.car, ownerId: c.ownerId, ownerName: c.ownerName, mine: c.ownerId === me.id,
      groups: c.ownerId === me.id ? links[c.car.id] : links[c.car.id].filter(gid => mine.has(gid)) })),
    parks: Object.fromEntries(ids.map(id => [id, present(parks[id], me, known[id])])),
  };
}

// ---- cars and parks (v3 routes; v2 wraps them with its own `view`) ----

export const MAX_PHOTO = 400 * 1024;
export const validCarId = id => /^c_[0-9a-f]{8}$/.test(id);
export const validParkId = id => Number.isSafeInteger(id) && id >= 0;
export const STATES = ["PARKED", "DRIVING", "LEFT"];

export function cleanCar(c, id) {
  if (c && typeof c === "object") c = { ...c, plate: c.plate ?? null };
  if (!c || c.id !== id || !validCarId(id) || typeof c.name !== "string" || !c.name.trim() || c.name.length > 40 ||
      !Number.isInteger(c.color) || !(c.plate === null || (typeof c.plate === "string" && c.plate.length <= 20)) ||
      !Number.isInteger(c.order) || !Number.isSafeInteger(c.updated)) return null;
  return { id, name: c.name.trim(), color: c.color, plate: c.plate, order: c.order, updated: c.updated };
}

export function cleanPark(p, carId, now, states = STATES) {
  if (p && typeof p === "object") for (const k of ["floor", "zone", "number", "pillar", "memo"]) p = { ...p, [k]: p[k] ?? null };
  if (!p || p.carId !== carId || !validParkId(p.id) || !validParkId(p.atMs) || p.atMs > now + 5 * 60_000 ||
      !(p.floor === null || (typeof p.floor === "string" && /^(B[1-9](\.5)?|[1-9](\.5)?F|M|MZ2|G|L[1-9]|P[1-9]|ROOF)$/.test(p.floor))) ||
      !(p.zone === null || (typeof p.zone === "string" && p.zone.length <= 8)) ||
      !(p.number === null || (Number.isInteger(p.number) && p.number >= 1 && p.number <= 999)) ||
      !(p.pillar === null || (typeof p.pillar === "string" && p.pillar.length <= 12)) ||
      !(p.memo === null || (typeof p.memo === "string" && p.memo.length <= 80)) ||
      !states.includes(p.state) || (p.state === "LEFT" && !validParkId(p.leftAt))) return null;
  const out = { id: p.id, carId, atMs: p.atMs, floor: p.floor, zone: p.zone, number: p.number, pillar: p.pillar, memo: p.memo, state: p.state, photo: false };
  if (p.state === "LEFT") out.leftAt = p.leftAt;
  return out;
}

export async function deletePhotos(env, prefix) {
  if (!env.PHOTOS) return;
  let cursor;
  do {
    const page = await env.PHOTOS.list({ prefix, cursor });
    for (const object of page.objects) await env.PHOTOS.delete(object.key);
    cursor = page.truncated ? page.cursor : null;
  } while (cursor);
}

/** Photo keys of one park: the v3 key, then the pre-v3 key a migrated car kept. */
export const photoKeys = (car, parkId) => [`c/${car.car.id}/${parkId}.jpg`,
  ...(car.legacyGroup && car.legacyCarId ? [`g/${car.legacyGroup}/${car.legacyCarId}/${parkId}.jpg`] : [])];
export async function dropPhoto(env, car, parkId) { for (const k of photoKeys(car, parkId)) await env.PHOTOS?.delete(k); }
export async function dropCarPhotos(env, car) {
  await deletePhotos(env, `c/${car.car.id}/`);
  if (car.legacyGroup && car.legacyCarId) await deletePhotos(env, `g/${car.legacyGroup}/${car.legacyCarId}/`);
}

/** The viewer's park presenter for one car (D13 masking). */
export async function viewer(store, me, carId) {
  const known = (await store.knownParkers(me.id, [carId]))[carId];
  return v => present(v, me, known);
}

const driver = (state, me) => state === "PARKED" ? { drivingBy: null, drivingById: null } : { drivingBy: me.name, drivingById: me.id };
const history = v => ({ park: v.park, by: v.by, byId: v.byId, drivingBy: v.drivingBy ?? null, drivingById: v.drivingById ?? null });
const moved = (view, v) => json({ error: "moved", park: v ? view(v) : null }, 409);

// Every write is a CAS that re-checks visibility (spec 2.6); a lost CAS re-reads and re-judges, at most 3 tries.
export async function putPark(env, store, me, car, park, view, now) {
  const id = car.car.id;
  for (let i = 0; i < 3; i++) {
    const cur = await store.park3(id);
    if (cur && cur.park.id !== park.id && cur.park.atMs >= park.atMs) return json({ error: "stale", park: view(cur) }, 409);
    const same = cur?.park.id === park.id;
    const next = { park: { ...park, photo: same ? cur.park.photo : false }, prev: same ? cur.prev : cur ? history(cur) : null,
      by: same ? cur.by : me.name, byId: same ? cur.byId : me.id, ...driver(park.state, me), updated: now };
    if (await store.casPark3(id, cur, next, me.id)) {
      if (cur && !same && cur.prev?.park.photo) await dropPhoto(env, car, cur.prev.park.id);
      return json({ park: view(next) });
    }
    if (!await store.visible(id, me.id)) return fail(404, "car");
  }
  const cur = await store.park3(id);
  return json({ error: "stale", park: cur ? view(cur) : null }, 409);
}

export async function setState(env, store, me, car, state, parkId, atMs, view, now) {
  const id = car.car.id;
  for (let i = 0; i < 3; i++) {
    const cur = await store.park3(id);
    if (!cur) return fail(404, "park");
    if (cur.park.id !== parkId) return moved(view, cur);
    const { leftAt, ...park } = cur.park;
    const next = { ...cur, park: state === "LEFT" ? { ...park, state, leftAt: atMs } : { ...park, state }, ...driver(state, me), updated: now };
    if (await store.casPark3(id, cur, next, me.id)) return json({ park: view(next) });
    if (!await store.visible(id, me.id)) return fail(404, "car");
  }
  return moved(view, await store.park3(id));
}

export async function undoPark(env, store, me, car, parkId, view, now) {
  const id = car.car.id, cur = await store.park3(id);
  if (!cur) return fail(404, "park");
  if (cur.park.id !== parkId) return moved(view, cur);
  const ok = cur.prev ? await store.casPark3(id, cur, { ...history(cur.prev), prev: null, updated: now }, me.id) : await store.casRemovePark3(id, cur, me.id);
  if (!ok) return await store.visible(id, me.id) ? moved(view, await store.park3(id)) : fail(404, "car");
  if (cur.park.photo) await dropPhoto(env, car, cur.park.id);
  return json({ park: cur.prev ? view(cur.prev) : null });
}

export async function photo(env, store, me, car, method, requested, bytes, view) {
  const id = car.car.id, parkId = Number(requested), cur = await store.park3(id);
  if (!cur || requested === null || !validParkId(parkId) || cur.park.id !== parkId) return json({ error: "moved" }, 409);
  const [key, legacy] = photoKeys(car, parkId);
  if (method === "GET") {
    if (!cur.park.photo) return fail(404, "photo");
    const object = await env.PHOTOS.get(key) || (legacy && await env.PHOTOS.get(legacy));
    return object ? new Response(object.body, { headers: { "content-type": "image/jpeg" } }) : fail(404, "photo");
  }
  await env.PHOTOS.put(key, bytes, { httpMetadata: { contentType: "image/jpeg" } });
  let row = cur;
  for (let i = 0; i < 3; i++) {
    if (row.park.photo) return json({ ok: true }); // a concurrent upload already flagged it; keep the object
    if (await store.casPark3(id, row, { ...row, park: { ...row.park, photo: true }, updated: Date.now() }, me.id)) return json({ ok: true });
    row = await store.park3(id);
    const visible = await store.visible(id, me.id);
    if (!row || row.park.id !== parkId || !visible) { await env.PHOTOS.delete(key); return visible ? moved(view, row) : fail(404, "car"); }
  }
  return json({ error: "moved" }, 409);
}
