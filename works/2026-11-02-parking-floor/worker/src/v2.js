// /v2 compat layer for the installed app (spec 2.4): v2 shapes over the v3 tables.
// The v2 group is the device's only membership; 0 or more than 1 → 410 update.
import { cleanName, fail, json, newCode, normCode, randomId, requestInvites, sha256 } from "./index.js";
import { MAX_PHOTO, auth, cleanCar, cleanPark, dropCarPhotos, leaveGroup, notifyCars, notifyGroup, photo, present, putPark,
  sendTokens, setState, undoPark } from "./core.js";
import { sizeRefused } from "./v3.js";

const DAY = 86400_000;
const REQ_SECRET = /^[0-9a-f]{32}$/, REQ_HASH = /^[0-9a-f]{64}$/;
const V2_STATES = ["PARKED", "DRIVING"];

// JSON body capped at 4 KB by actual bytes (content-length can be absent or lie).
async function body(req) {
  const buf = await req.arrayBuffer();
  if (buf.byteLength > 4096) throw fail(413, "size");
  try { return JSON.parse(new TextDecoder().decode(buf)); } catch { return null; }
}

async function limited(env, req, key) {
  if (!env.LIMIT) return false;
  const ip = req.headers.get("cf-connecting-ip") || "?";
  return !(await env.LIMIT.limit({ key: `${key}:${ip}` })).success;
}

/** v2 never sees LEFT (D16): it reads as DRIVING without leftAt. */
function toV2(p) {
  if (!p) return p;
  const { leftAt, ...out } = p;
  if (out.state === "LEFT") out.state = "DRIVING";
  if (out.prev) out.prev = toV2(out.prev);
  return out;
}

const members = (rows, me) => rows.map(x => ({ id: x.id, name: x.name, me: x.id === me, owner: x.owner, lastSeen: x.lastSeen }));

async function newDevice(name, now) {
  const id = randomId(8), secret = randomId(32);
  return { device: { id, name, secretHash: await sha256(secret), fcmToken: null, created: now, lastSeen: now },
    credentials: { memberId: id, secret, token: `${id}.${secret}` } };
}

/** v2 cars of a device: the group's cars plus every car it owns (the old Merge deletes local cars missing here). */
async function carsOf(store, me, gid) {
  const cars = await store.visibleCars(me);
  const links = await store.carGroups(cars.map(c => c.car.id));
  return cars.filter(c => c.ownerId === me || links[c.car.id].includes(gid));
}

export async function handle(req, env, store, ctx) {
  try { return await route(req, env, store, ctx); } catch (e) { if (e instanceof Response) return e; throw e; }
}

async function route(req, env, store, ctx) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "");
  const now = Date.now();
  if (sizeRefused(req, path)) return fail(413, "size");

  if (req.method === "POST" && path === "/v2/groups") {
    if (await limited(env, req, path)) return fail(429, "slow");
    const b = await body(req), name = cleanName(b?.name), groupName = String(b?.groupName || "").trim().slice(0, 40);
    if (!name || !groupName) return fail(400, "name");
    const fresh = await newDevice(name, now), groupId = randomId(8);
    const result = await store.createGroup({ me: fresh.device.id, newDevice: fresh.device, groupId, groupName, now });
    if (result !== "ok") return fail(409, result);
    return json({ groupId, ...fresh.credentials, groupName, members: [{ id: fresh.device.id, name, me: true, owner: true, lastSeen: now }], cars: [] });
  }
  if (req.method === "POST" && (path === "/v2/peek" || path === "/v2/join")) {
    if (await limited(env, req, "/v2/join")) return fail(429, "slow");
    const b = await body(req), code = normCode(b?.code);
    if (code.length !== 12) return fail(400, "code");
    const codeHash = await sha256(code), inv = await store.invite(codeHash, now);
    if (!inv) return fail(404, "code");
    const groupName = (await store.group(inv.groupId))?.name ?? null;
    if (path === "/v2/peek") return json({ groupName, count: (await store.groupMembers(inv.groupId)).length });
    const name = cleanName(b?.name);
    if (!name) return fail(400, "name");
    const fresh = await newDevice(name, now);
    const result = await store.join({ me: fresh.device.id, newDevice: fresh.device, codeHash, code, now });
    if (result.result !== "ok") return fail(result.result === "code" ? 404 : 409, result.result);
    const gid = result.groupId;
    ctx?.waitUntil?.(notifyGroup(env, store, gid, fresh.device.id, { type: "members", groupId: gid }));
    return json({ groupId: gid, ...fresh.credentials, groupName, members: members(await store.groupMembers(gid), fresh.device.id),
      cars: (await carsOf(store, fresh.device.id, gid)).map(c => c.car) });
  }

  // join request poll: no auth; only the requester holds the secret S, the shared /r link carries sha256(S).
  // The row stays until a join uses its code (a lost response must not lose the code).
  if (req.method === "POST" && (path === "/v2/requests/poll" || path === "/v2/requests/cancel")) {
    if (await limited(env, req, "/v2/requests")) return fail(429, "slow");
    const secret = (await body(req))?.secret;
    if (typeof secret !== "string" || !REQ_SECRET.test(secret)) return fail(400, "req");
    const hash = await sha256(secret), r = await store.request(hash, now);
    if (path.endsWith("/cancel")) {
      if (r) await store.dropInvite(await sha256(r.code));
      await store.dropRequest(hash);
      return json({ ok: true });
    }
    return r ? json({ code: r.code.replace(/(.{4})(?=.)/g, "$1-"), byName: r.byName ?? null }) : fail(404, "req");
  }

  const me = await auth(req, store);
  if (!me) return fail(401, "auth");
  // ponytail: one write per v2 call keeps last_seen exact for the legacy state rule and marks v2_seen; v2 is short-lived
  await store.touchDevice(me.id, now, true);
  const groups = await store.memberships(me.id);
  if (groups.length !== 1) return fail(410, "update");
  const gid = groups[0].groupId, owner = groups[0].owner;

  if (req.method === "PUT" && path === "/v2/requests") {
    const b = await body(req), id = typeof b?.req === "string" ? b.req : "";
    if (!REQ_HASH.test(id)) return fail(400, "req");
    const code = normCode(newCode());
    const result = await store.acceptRequest({ reqHash: id, groupId: gid, me: me.id, myName: me.name, code, codeHash: await sha256(code), now });
    return result === "ok" ? json({ ok: true }) : fail(result === "many" ? 429 : 409, result);
  }
  if (req.method === "POST" && path === "/v2/invites") {
    const code = newCode(), expiresAt = now + DAY;
    await store.dropInvites(gid, await requestInvites(store, gid, now));
    await store.addInvite({ codeHash: await sha256(normCode(code)), groupId: gid, expires: expiresAt });
    return json({ code, expiresAt, link: `${env.LINK_BASE}#${code}` });
  }
  if (req.method === "PUT" && path === "/v2/token") {
    const b = await body(req);
    await store.setToken(me.id, b?.fcmToken ? String(b.fcmToken).slice(0, 4096) : null);
    return json({ ok: true });
  }
  if (req.method === "GET" && path === "/v2/state") {
    if (env.LIMIT && !(await env.LIMIT.limit({ key: `state:${me.id}` })).success) return fail(429, "slow");
    const cars = await carsOf(store, me.id, gid), ids = cars.map(c => c.car.id);
    const [parks, known] = await Promise.all([store.parks3(ids), store.knownParkers(me.id, ids)]);
    return json({ groupName: groups[0].name ?? null, members: members(await store.groupMembers(gid), me.id), cars: cars.map(c => c.car),
      parks: Object.fromEntries(ids.filter(id => parks[id]).map(id => [id, toV2(present(parks[id], me, known[id]))])) });
  }

  const carRoute = path.match(/^\/v2\/cars\/(c_[0-9a-f]{8})(?:\/(park|state|undo|photo))?$/);
  if (carRoute) {
    const [, id, kind] = carRoute, car = await store.car(id);
    const visible = !!car && (car.ownerId === me.id || await store.visible(id, me.id));
    if (car && !visible) return fail(404, "car");
    if (!kind && req.method === "PUT") {
      const next = cleanCar((await body(req))?.car, id);
      if (!next) return fail(400, "car");
      if (!car) {
        const result = await store.createCar(me.id, next, now);
        if (result === "cars") return fail(409, "full");
        if (result !== "ok") return fail(404, "car");
        await store.include(id, gid, me.id, now);
      } else if (car.ownerId !== me.id) return json({ car: car.car }); // old app drops on 2xx; the next pull restores it
      else await store.updateCar(me.id, next);
      ctx?.waitUntil?.(notifyCars(env, store, [id], me.id, { type: "cars" }));
      return json({ car: next });
    }
    if (!car) return fail(404, "car");
    if (!kind && req.method === "DELETE") {
      if (car.ownerId !== me.id) return json({ ok: true });
      const tokens = await store.audience([id], me.id);
      if (await store.deleteCar(id, me.id)) {
        await dropCarPhotos(env, car);
        ctx?.waitUntil?.(sendTokens(env, store, tokens, { type: "cars" }));
      }
      return json({ ok: true });
    }
    if (!kind) return fail(404, "route");
    // photo body is read before the park row so a park written during the upload is not overwritten
    let bytes, res;
    if (kind === "photo" && req.method === "PUT") {
      if (req.headers.get("content-type") !== "image/jpeg") return fail(400, "photo");
      bytes = await req.arrayBuffer();
      if (bytes.byteLength > MAX_PHOTO) return fail(413, "size");
    }
    const known = (await store.knownParkers(me.id, [id]))[id], view = v => toV2(present(v, me, known));
    if (kind === "park" && req.method === "PUT") {
      const park = cleanPark((await body(req))?.park, id, now, V2_STATES);
      if (!park) return fail(400, "park");
      res = await putPark(env, store, me, car, park, view, now);
    } else if (kind === "state" && req.method === "POST") {
      const state = (await body(req))?.state;
      if (!V2_STATES.includes(state)) return fail(400, "state");
      const cur = await store.park3(id);
      if (!cur) return fail(404, "park");
      // no parkId in v2: apply only if the phone was in contact after this park was written
      if (cur.updated > (me.lastSeen ?? me.created)) return json({ error: "moved", park: view(cur) }, 409);
      res = await setState(env, store, me, car, state, cur.park.id, null, view, now);
    } else if (kind === "undo" && req.method === "POST") {
      res = await undoPark(env, store, me, car, (await body(req))?.parkId, view, now);
    } else if (kind === "photo" && (req.method === "PUT" || req.method === "GET")) {
      res = await photo(env, store, me, car, req.method, url.searchParams.get("park"), bytes, view);
    } else return fail(404, "route");
    if (res.status === 200 && req.method !== "GET") ctx?.waitUntil?.(notifyCars(env, store, [id], me.id, { type: "park", carId: id }));
    return res;
  }

  const target = path.match(/^\/v2\/members\/([0-9a-f]{16})$/);
  if (req.method === "DELETE" && (target || path === "/v2/me")) {
    const id = target ? target[1] : me.id;
    const victim = (await store.groupMembers(gid)).find(m => m.id === id);
    if (!victim) return fail(404, "member");
    if (id !== me.id && !owner && (victim.owner || now - (victim.lastSeen ?? victim.created) <= 30 * DAY)) return fail(403, "owner");
    await leaveGroup(env, store, gid, id, me.id);
    return json({ ok: true });
  }
  return fail(404, "route");
}
