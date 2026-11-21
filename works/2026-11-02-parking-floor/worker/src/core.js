import { sha256 } from "./index.js";
import { sendData } from "./fcm.js";
import { deletePhotos } from "./v2.js";

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

async function sendTokens(env, store, tokens, data) {
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
  if (result.deviceGone) {
    for (const c of owned) {
      await deletePhotos(env, `c/${c.car.id}/`);
      if (c.legacyGroup && c.legacyCarId) await deletePhotos(env, `g/${c.legacyGroup}/${c.legacyCarId}/`);
    }
  }
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
