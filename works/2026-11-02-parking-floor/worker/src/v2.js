import { cleanName, fail, json, member, newCode, newMember, normCode, notify, randomId, sha256 } from "./index.js";

const DAY = 86400_000;
const MAX_PHOTO = 400 * 1024;
const validCarId = id => /^c_[0-9a-f]{8}$/.test(id);
const validParkId = id => Number.isSafeInteger(id) && id >= 0;

// JSON body capped at 4 KB by actual bytes (content-length can be absent or lie).
async function body(req) {
  const buf = await req.arrayBuffer();
  if (buf.byteLength > 4096) throw fail(413, "size");
  try { return JSON.parse(new TextDecoder().decode(buf)); } catch { return null; }
}

function cleanCar(c, id) {
  if (c && typeof c === "object") c = { ...c, plate: c.plate ?? null };
  if (!c || c.id !== id || !validCarId(id) || typeof c.name !== "string" || !c.name.trim() || c.name.length > 40 ||
      !Number.isInteger(c.color) || !(c.plate === null || (typeof c.plate === "string" && c.plate.length <= 20)) ||
      !Number.isInteger(c.order) || !Number.isSafeInteger(c.updated)) return null;
  return { id, name: c.name.trim(), color: c.color, plate: c.plate, order: c.order, updated: c.updated };
}

function cleanPark(p, carId, now) {
  if (p && typeof p === "object") for (const k of ["floor", "zone", "number", "pillar", "memo"]) p = { ...p, [k]: p[k] ?? null };
  if (!p || p.carId !== carId || !validParkId(p.id) || !validParkId(p.atMs) || p.atMs > now + 5 * 60_000 ||
      !(p.floor === null || (typeof p.floor === "string" && /^(B[1-9]|[1-9]F|ROOF)$/.test(p.floor))) ||
      !(p.zone === null || (typeof p.zone === "string" && p.zone.length <= 8)) ||
      !(p.number === null || (Number.isInteger(p.number) && p.number >= 1 && p.number <= 999)) ||
      !(p.pillar === null || (typeof p.pillar === "string" && p.pillar.length <= 12)) ||
      !(p.memo === null || (typeof p.memo === "string" && p.memo.length <= 40)) ||
      !["PARKED", "DRIVING"].includes(p.state)) return null;
  return { id: p.id, carId, atMs: p.atMs, floor: p.floor, zone: p.zone, number: p.number, pillar: p.pillar, memo: p.memo, state: p.state, photo: false };
}

function present(v, me) {
  return { ...v.park, by: v.by, byId: v.byId, mine: v.byId === me.id, drivingBy: v.drivingBy };
}

async function limited(env, req, key) {
  if (!env.LIMIT) return false;
  const ip = req.headers.get("cf-connecting-ip") || "?";
  return !(await env.LIMIT.limit({ key: `${key}:${ip}` })).success;
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

export async function handle(req, env, store, ctx) {
  try { return await route(req, env, store, ctx); } catch (e) { if (e instanceof Response) return e; throw e; }
}

async function route(req, env, store, ctx) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "");
  const now = Date.now();
  const photoRoute = path.match(/^\/v2\/cars\/(c_[0-9a-f]{8})\/photo$/);
  if (photoRoute && req.method === "PUT") {
    const length = Number(req.headers.get("content-length"));
    if (!req.headers.has("content-length") || !Number.isSafeInteger(length) || length < 0 || length > MAX_PHOTO) return fail(413, "size");
  } else if (Number(req.headers.get("content-length") || 0) > 4096) return fail(413, "size");

  if (req.method === "POST" && path === "/v2/groups") {
    if (await limited(env, req, path)) return fail(429, "slow");
    const b = await body(req), name = cleanName(b?.name), groupName = String(b?.groupName || "").trim().slice(0, 40);
    if (!name || !groupName) return fail(400, "name");
    const groupId = randomId(8);
    await store.addGroup({ id: groupId, name: groupName, created: now });
    const m = await newMember(store, groupId, name, true);
    return json({ groupId, ...m, groupName, members: [{ id: m.memberId, name, me: true, owner: true, lastSeen: now }], cars: [] });
  }
  if (req.method === "POST" && (path === "/v2/peek" || path === "/v2/join")) {
    if (await limited(env, req, "/v2/join")) return fail(429, "slow");
    const b = await body(req), code = normCode(b?.code);
    if (code.length !== 12) return fail(400, "code");
    const hash = await sha256(code), inv = await store.invite(hash, now);
    if (!inv) return fail(404, "code");
    const members = await store.members(inv.groupId);
    const groupName = (await store.group(inv.groupId))?.name ?? null;
    if (path === "/v2/peek") return json({ groupName, count: members.length });
    const name = cleanName(b?.name);
    if (!name) return fail(400, "name");
    if (members.length >= 8) return fail(409, "full");
    if (!await store.takeInvite(hash, now)) return fail(404, "code");
    const m = await newMember(store, inv.groupId, name, false);
    const all = await store.members(inv.groupId);
    ctx?.waitUntil?.(notify(env, store, inv.groupId, m.memberId, { type: "members" }));
    return json({ groupId: inv.groupId, ...m, groupName, members: all.map(x => ({ id: x.id, name: x.name, me: x.id === m.memberId, owner: x.owner, lastSeen: x.lastSeen })), cars: await store.cars(inv.groupId) });
  }

  const me = await member(req, store);
  if (!me) return fail(401, "auth");

  if (req.method === "POST" && path === "/v2/invites") {
    const code = newCode(), expiresAt = now + DAY;
    await store.dropInvites(me.groupId);
    await store.addInvite({ codeHash: await sha256(normCode(code)), groupId: me.groupId, expires: expiresAt });
    return json({ code, expiresAt, link: `${env.LINK_BASE}#${code}` });
  }
  if (req.method === "PUT" && path === "/v2/token") {
    const b = await body(req), token = b?.fcmToken ? String(b.fcmToken).slice(0, 4096) : null;
    await store.setToken(me.id, token);
    return json({ ok: true });
  }
  if (req.method === "GET" && path === "/v2/state") {
    const groupName = (await store.group(me.groupId))?.name ?? null;
    const members = (await store.members(me.groupId)).map(x => ({ id: x.id, name: x.name, me: x.id === me.id, owner: x.owner, lastSeen: x.lastSeen }));
    const parks = Object.fromEntries(Object.entries(await store.parks2(me.groupId)).map(([id, v]) => [id, present(v, me)]));
    return json({ groupName, members, cars: await store.cars(me.groupId), parks });
  }

  const carRoute = path.match(/^\/v2\/cars\/(c_[0-9a-f]{8})$/);
  if (carRoute) {
    const id = carRoute[1], current = await store.car(me.groupId, id);
    if (req.method === "PUT") {
      const car = cleanCar((await body(req))?.car, id);
      if (!car) return fail(400, "car");
      if (!current && (await store.cars(me.groupId)).length >= 4) return fail(409, "full");
      await store.setCar(me.groupId, car);
      ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "cars" }));
      return json({ car });
    }
    if (req.method === "DELETE") {
      if (!current) return fail(404, "car");
      await deletePhotos(env, `g/${me.groupId}/${id}/`);
      await store.removeCar(me.groupId, id);
      ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "cars" }));
      return json({ ok: true });
    }
  }

  const action = path.match(/^\/v2\/cars\/(c_[0-9a-f]{8})\/(park|state|undo|photo)$/);
  if (action) {
    const [, carId, kind] = action;
    if (!await store.car(me.groupId, carId)) return fail(404, "car");
    // photo body is read before the park row so a park written during the upload is not overwritten
    let bytes;
    if (kind === "photo" && req.method === "PUT") {
      if (req.headers.get("content-type") !== "image/jpeg") return fail(400, "photo");
      bytes = await req.arrayBuffer();
      if (bytes.byteLength > MAX_PHOTO) return fail(413, "size");
    }
    const cur = await store.park2(me.groupId, carId);
    const moved = async () => { const now2 = await store.park2(me.groupId, carId); return json({ error: "moved", park: now2 ? present(now2, me) : null }, 409); };
    if (kind === "park" && req.method === "PUT") {
      const park = cleanPark((await body(req))?.park, carId, now);
      if (!park) return fail(400, "park");
      if (cur && cur.park.id !== park.id && cur.park.atMs >= park.atMs) return json({ error: "stale", park: present(cur, me) }, 409);
      const same = cur?.park.id === park.id;
      if (same) park.photo = cur.park.photo;
      const next = { park, prev: same ? cur.prev : cur ? { park: cur.park, by: cur.by, byId: cur.byId, drivingBy: cur.drivingBy } : null, by: same ? cur.by : me.name, byId: same ? cur.byId : me.id, drivingBy: park.state === "DRIVING" ? me.name : null, updated: now };
      if (!await store.casPark2(me.groupId, carId, cur, next)) return await moved();
      if (cur && !same && cur.prev?.park.photo) await env.PHOTOS?.delete(`g/${me.groupId}/${carId}/${cur.prev.park.id}.jpg`);
      ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "park", carId }));
      return json({ park: present(next, me) });
    }
    if (kind === "state" && req.method === "POST") {
      if (!cur) return fail(404, "park");
      const state = (await body(req))?.state;
      if (!["PARKED", "DRIVING"].includes(state)) return fail(400, "state");
      const next = { ...cur, park: { ...cur.park, state }, drivingBy: state === "DRIVING" ? me.name : null, updated: now };
      if (!await store.casPark2(me.groupId, carId, cur, next)) return await moved();
      ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "park", carId }));
      return json({ park: present(next, me) });
    }
    if (kind === "undo" && req.method === "POST") {
      if (!cur) return fail(404, "park");
      if ((await body(req))?.parkId !== cur.park.id) return json({ error: "moved", park: present(cur, me) }, 409);
      const ok = cur.prev ? await store.casPark2(me.groupId, carId, cur, { ...cur, ...cur.prev, prev: null, updated: now }) : await store.casRemovePark2(me.groupId, carId, cur);
      if (!ok) return await moved();
      if (cur.park.photo) await env.PHOTOS?.delete(`g/${me.groupId}/${carId}/${cur.park.id}.jpg`);
      ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "park", carId }));
      return json({ park: cur.prev ? present(cur.prev, me) : null });
    }
    if (kind === "photo") {
      const requestedPark = url.searchParams.get("park"), parkId = Number(requestedPark);
      if (!cur || requestedPark === null || !validParkId(parkId) || cur.park.id !== parkId) return json({ error: "moved" }, 409);
      const key = `g/${me.groupId}/${carId}/${parkId}.jpg`;
      if (req.method === "PUT") {
        await env.PHOTOS.put(key, bytes, { httpMetadata: { contentType: "image/jpeg" } });
        let row = cur, saved = false;
        for (let i = 0; i < 3 && !saved; i++) {
          if (row.park.photo) { saved = true; break; } // a concurrent upload already flagged it; keep the object
          saved = await store.casPark2(me.groupId, carId, row, { ...row, park: { ...row.park, photo: true }, updated: Date.now() });
          if (!saved) {
            row = await store.park2(me.groupId, carId);
            if (!row || row.park.id !== parkId) { await env.PHOTOS.delete(key); return await moved(); }
          }
        }
        if (!saved) return await moved();
        ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "park", carId }));
        return json({ ok: true });
      }
      if (req.method === "GET") {
        if (!cur.park.photo) return fail(404, "photo");
        const object = await env.PHOTOS.get(key);
        return object ? new Response(object.body, { headers: { "content-type": "image/jpeg" } }) : fail(404, "photo");
      }
    }
  }

  const target = path.match(/^\/v2\/members\/([0-9a-f]{16})$/);
  if (req.method === "DELETE" && (target || path === "/v2/me")) {
    const id = target ? target[1] : me.id;
    const victim = await store.member(id);
    if (!victim || victim.groupId !== me.groupId) return fail(404, "member");
    if (id !== me.id && !me.owner && (victim.owner || now - (victim.lastSeen ?? victim.created) <= 30 * DAY)) return fail(403, "owner");
    const rest = (await store.members(me.groupId)).filter(x => x.id !== id);
    await store.removeMember(me.groupId, id);
    if (!rest.length) { await deletePhotos(env, `g/${me.groupId}/`); await store.dropGroup(me.groupId); }
    else if (!rest.some(x => x.owner)) await store.makeOwner(rest[0].id);
    ctx?.waitUntil?.(notify(env, store, me.groupId, me.id, { type: "members" }));
    return json({ ok: true });
  }
  return fail(404, "route");
}
