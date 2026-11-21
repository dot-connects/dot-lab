import { cleanName, fail, json, newCode, normCode, randomId, requestInvites, sha256 } from "./index.js";
import { MAX_PHOTO, STATES, auth, cleanCar, cleanPark, dropCarPhotos, leaveGroup, notifyCars, notifyGroup, photo, putPark,
  sendTokens, setState, stateFor, undoPark, validParkId, viewer } from "./core.js";

const DAY = 86400_000;
const REQ_SECRET = /^[0-9a-f]{32}$/, REQ_HASH = /^[0-9a-f]{64}$/;

async function body(req) {
  const bytes = await req.arrayBuffer();
  if (bytes.byteLength > 4096) throw fail(413, "size");
  try { return JSON.parse(new TextDecoder().decode(bytes)); } catch { return null; }
}

async function limited(env, req, key) {
  return env.LIMIT && !(await env.LIMIT.limit({ key: `${key}:${req.headers.get("cf-connecting-ip") || "?"}` })).success;
}

const groupName = value => typeof value === "string" && value.trim().length <= 40 ? value.trim() : "";

async function identity(name, now) {
  const id = randomId(8), secret = randomId(32);
  return { device: { id, name, secretHash: await sha256(secret), fcmToken: null, created: now, lastSeen: now },
    credentials: { deviceId: id, secret, token: `${id}.${secret}` } };
}

/** Photo uploads need a declared length up to 400 KB; every other body is capped at 4 KB. */
export function sizeRefused(req, path) {
  if (req.method === "PUT" && path.endsWith("/photo")) {
    const length = Number(req.headers.get("content-length"));
    return !req.headers.has("content-length") || !Number.isSafeInteger(length) || length < 0 || length > MAX_PHOTO;
  }
  return Number(req.headers.get("content-length") || 0) > 4096;
}

export async function handle(req, env, store, ctx) {
  try { return await route(req, env, store, ctx); } catch (e) { if (e instanceof Response) return e; throw e; }
}

async function route(req, env, store, ctx) {
  const url = new URL(req.url), path = url.pathname.replace(/\/+$/, ""), now = Date.now();
  if (sizeRefused(req, path)) return fail(413, "size");

  if (req.method === "POST" && ["/v3/groups", "/v3/join"].includes(path)) {
    const me = req.headers.has("authorization") ? await auth(req, store) : null;
    if (req.headers.has("authorization") && !me) return fail(401, "auth");
    if (!me && await limited(env, req, path === "/v3/groups" ? "/v2/groups" : "/v2/join")) return fail(429, "slow");
    const b = await body(req), name = me?.name || cleanName(b?.name);
    let gid, fresh;
    if (path === "/v3/groups") {
      const nameOfGroup = groupName(b?.groupName);
      if (!name || !nameOfGroup) return fail(400, "name");
      fresh = me ? null : await identity(name, now);
      gid = randomId(8);
      const result = await store.createGroup({ me: me?.id || fresh.device.id, newDevice: fresh?.device, groupId: gid, groupName: nameOfGroup, now });
      if (result !== "ok") {
        if (me && !await store.device(me.id)) return fail(401, "auth");
        return fail(409, result);
      }
    } else {
      const code = normCode(b?.code);
      if (code.length !== 12) return fail(400, "code");
      if (!name) return fail(400, "name");
      fresh = me ? null : await identity(name, now);
      const result = await store.join({ me: me?.id || fresh.device.id, newDevice: fresh?.device, codeHash: await sha256(code), code, now });
      if (result.result !== "ok") {
        if (me && !await store.device(me.id)) return fail(401, "auth");
        return fail(result.result === "code" ? 404 : 409, result.result);
      }
      gid = result.groupId;
      ctx?.waitUntil?.(notifyGroup(env, store, gid, me?.id || fresh.device.id, { type: "members", groupId: gid }));
    }
    return json({ ...fresh?.credentials, groupId: gid, state: await stateFor(store, me || fresh.device) });
  }

  if (req.method === "POST" && path === "/v3/peek") {
    if (await limited(env, req, "/v2/join")) return fail(429, "slow");
    const code = normCode((await body(req))?.code);
    if (code.length !== 12) return fail(400, "code");
    const inv = await store.invite(await sha256(code), now);
    if (!inv) return fail(404, "code");
    return json({ groupName: (await store.group(inv.groupId))?.name ?? null, count: (await store.groupMembers(inv.groupId)).length });
  }

  if (req.method === "POST" && ["/v3/requests/poll", "/v3/requests/cancel"].includes(path)) {
    if (await limited(env, req, "/v2/requests")) return fail(429, "slow");
    const secret = (await body(req))?.secret;
    if (typeof secret !== "string" || !REQ_SECRET.test(secret)) return fail(400, "req");
    const hash = await sha256(secret), r = await store.request(hash, now);
    if (path.endsWith("/cancel")) {
      if (r) await store.dropInvite(await sha256(r.code));
      await store.dropRequest(hash);
      return json({ ok: true });
    }
    return r ? json({ code: r.code.replace(/(.{4})(?=.)/g, "$1-"), byName: r.byName ?? null,
      groupName: (await store.group(r.groupId))?.name ?? null }) : fail(404, "req");
  }

  const me = await auth(req, store);
  if (!me) return fail(401, "auth");
  if (req.method === "GET" && path === "/v3/state") {
    if (env.LIMIT && !(await env.LIMIT.limit({ key: `state:${me.id}` })).success) return fail(429, "slow");
    return json(await stateFor(store, me));
  }
  if (req.method === "PUT" && path === "/v3/token") {
    const b = await body(req);
    await store.setToken(me.id, b?.fcmToken ? String(b.fcmToken).slice(0, 4096) : null);
    return json({ ok: true });
  }
  if (req.method === "DELETE" && path === "/v3/me") {
    for (const g of await store.memberships(me.id)) await leaveGroup(env, store, g.groupId, me.id);
    return json({ ok: true });
  }
  if (req.method === "PUT" && path === "/v3/requests") {
    const b = await body(req);
    if (typeof b?.req !== "string" || !REQ_HASH.test(b.req)) return fail(400, "req");
    if (!(await store.memberships(me.id)).some(g => g.groupId === b.groupId)) return fail(404, "group");
    const code = normCode(newCode());
    const result = await store.acceptRequest({ reqHash: b.req, groupId: b.groupId, me: me.id, myName: me.name, code, codeHash: await sha256(code), now });
    if (!(await store.memberships(me.id)).some(g => g.groupId === b.groupId)) return fail(404, "group");
    return result === "ok" ? json({ ok: true }) : fail(result === "many" ? 429 : 409, result);
  }

  const groupRoute = path.match(/^\/v3\/groups\/([0-9a-f]{16})(?:\/(invites|me|members\/([0-9a-f]{16})))?$/);
  if (groupRoute) {
    const [, gid, action, target] = groupRoute;
    const membership = (await store.memberships(me.id)).find(g => g.groupId === gid);
    if (!membership) return fail(404, "group");
    if (req.method === "POST" && action === "invites") {
      const code = newCode(), expiresAt = now + DAY;
      await store.dropInvites(gid, await requestInvites(store, gid, now));
      await store.addInvite({ codeHash: await sha256(normCode(code)), groupId: gid, expires: expiresAt });
      return json({ code, expiresAt, link: `${env.LINK_BASE}#${code}` });
    }
    if (req.method === "PUT" && !action) {
      if (!membership.owner) return fail(403, "owner");
      const name = groupName((await body(req))?.name);
      if (!name) return fail(400, "name");
      await store.renameGroup(gid, name);
      ctx?.waitUntil?.(notifyGroup(env, store, gid, me.id, { type: "members", groupId: gid }));
      return json({ ok: true });
    }
    if (req.method === "DELETE" && (action === "me" || target)) {
      const id = target || me.id;
      const victim = (await store.groupMembers(gid)).find(m => m.id === id);
      if (!victim) return fail(404, "member");
      if (id !== me.id && !membership.owner && (victim.owner || now - (victim.lastSeen ?? victim.created) <= 30 * DAY)) return fail(403, "owner");
      const result = await leaveGroup(env, store, gid, id, me.id);
      return json(action === "me" ? { ok: true, device: result.deviceGone ? "gone" : "kept" } : { ok: true });
    }
  }
  const carRoute = path.match(/^\/v3\/cars\/(c_[0-9a-f]{8})(?:\/(park|state|undo|photo)|\/groups\/([0-9a-f]{16}))?$/);
  if (carRoute) return await carRoutes(req, env, store, ctx, me, url, carRoute, now);
  return fail(404, "route");
}

async function carRoutes(req, env, store, ctx, me, url, [, id, kind, gid], now) {
  const car = await store.car(id);
  const visible = !!car && (car.ownerId === me.id || await store.visible(id, me.id));
  const pushCars = (ids, tokens) => ctx?.waitUntil?.(tokens ? sendTokens(env, store, tokens, { type: "cars" }) : notifyCars(env, store, ids, me.id, { type: "cars" }));
  if (!kind && !gid && req.method === "PUT") {
    const next = cleanCar((await body(req))?.car, id);
    if (!next) return fail(400, "car");
    if (!car) {
      const result = await store.createCar(me.id, next, now);
      if (result !== "ok") return fail(409, result);
    } else if (!visible) return fail(409, "id");
    else if (car.ownerId !== me.id) return fail(403, "owner");
    else if (!await store.updateCar(me.id, next)) return fail(403, "owner");
    pushCars([id]);
    return json({ car: next });
  }
  if (!visible) return fail(404, "car");
  if (!kind && !gid && req.method === "DELETE") {
    if (car.ownerId !== me.id) return fail(403, "owner");
    const tokens = await store.audience([id], me.id);
    if (!await store.deleteCar(id, me.id)) return fail(403, "owner");
    await dropCarPhotos(env, car);
    pushCars(null, tokens);
    return json({ ok: true });
  }
  if (gid && (req.method === "PUT" || req.method === "DELETE")) {
    if (car.ownerId !== me.id) return fail(403, "owner");
    const result = req.method === "PUT" ? await store.include(id, gid, me.id, now) : await store.exclude(id, gid, me.id);
    if (result !== "ok") return fail(404, result);
    ctx?.waitUntil?.(notifyGroup(env, store, gid, me.id, { type: "cars" }));
    return json({ ok: true });
  }
  if (!kind) return fail(404, "route");
  // The photo body is read before the park row, so a park written during the upload is not overwritten.
  let bytes, res;
  if (kind === "photo" && req.method === "PUT") {
    if (req.headers.get("content-type") !== "image/jpeg") return fail(400, "photo");
    bytes = await req.arrayBuffer();
    if (bytes.byteLength > MAX_PHOTO) return fail(413, "size");
  }
  const view = await viewer(store, me, id);
  if (kind === "park" && req.method === "PUT") {
    const park = cleanPark((await body(req))?.park, id, now);
    if (!park) return fail(400, "park");
    res = await putPark(env, store, me, car, park, view, now);
  } else if (kind === "state" && req.method === "POST") {
    const b = await body(req);
    if (!STATES.includes(b?.state) || (b.state === "LEFT" && !(validParkId(b.atMs) && b.atMs <= now + 5 * 60_000))) return fail(400, "state");
    res = await setState(env, store, me, car, b.state, b.parkId, b.atMs, view, now);
  } else if (kind === "undo" && req.method === "POST") {
    res = await undoPark(env, store, me, car, (await body(req))?.parkId, view, now);
  } else if (kind === "photo" && (req.method === "PUT" || req.method === "GET")) {
    res = await photo(env, store, me, car, req.method, url.searchParams.get("park"), bytes, view);
  } else return fail(404, "route");
  if (res.status === 200 && req.method !== "GET") ctx?.waitUntil?.(notifyCars(env, store, [id], me.id, { type: "park", carId: id }));
  return res;
}
