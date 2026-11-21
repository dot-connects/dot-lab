import { cleanName, fail, json, newCode, normCode, randomId, requestInvites, sha256 } from "./index.js";
import { auth, leaveGroup, notifyGroup, stateFor } from "./core.js";

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

export async function handle(req, env, store, ctx) {
  try { return await route(req, env, store, ctx); } catch (e) { if (e instanceof Response) return e; throw e; }
}

async function route(req, env, store, ctx) {
  const path = new URL(req.url).pathname.replace(/\/+$/, ""), now = Date.now();
  if (Number(req.headers.get("content-length") || 0) > 4096) return fail(413, "size");

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
  return fail(404, "route");
}
