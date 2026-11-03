// Family sync for the parking floor app: a few phones share one car's latest park.
// No accounts: a family is created on one phone, others join with a one-time invite code.
// Each phone holds a member id + secret; only the secret's SHA-256 is stored.
// Stored per family: members, open invites, the latest park (floor, pillar, building name; no coordinates).

import { sendData } from "./fcm.js";

const INVITE_MS = 24 * 60 * 60 * 1000;
const MAX_MEMBERS = 8;
const NAME_MAX = 12;
const BODY_MAX = 4096;
const FUTURE_MS = 5 * 60 * 1000;

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8" } });
const fail = (status, error) => json({ error }, status);

function randomId(bytes) {
  const b = crypto.getRandomValues(new Uint8Array(bytes));
  return [...b].map(x => x.toString(16).padStart(2, "0")).join("");
}

// 12 chars from an alphabet without 0/O/1/I, shown as XXXX-XXXX-XXXX (about 60 bits, one-time, 24 h)
const CODE_ABC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export function newCode() {
  const b = crypto.getRandomValues(new Uint8Array(12));
  const s = [...b].map(x => CODE_ABC[x % CODE_ABC.length]).join("");
  return `${s.slice(0, 4)}-${s.slice(4, 8)}-${s.slice(8)}`;
}
export const normCode = c => String(c || "").toUpperCase().replace(/[^A-Z0-9]/g, "");

export async function sha256(text) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map(x => x.toString(16).padStart(2, "0")).join("");
}

const cleanName = n => String(n || "").trim().slice(0, NAME_MAX);

/** The park fields phones may send; everything else is dropped. */
export function cleanPark(p, now = Date.now()) {
  if (!p || typeof p !== "object") return null;
  const int = v => (Number.isInteger(v) ? v : null);
  const id = Number(p.id), atMs = Number(p.atMs);
  // a park from the future would block every later park of the family
  if (!Number.isSafeInteger(id) || !Number.isSafeInteger(atMs) || atMs > now + FUTURE_MS) return null;
  return {
    id, atMs,
    floor: int(p.floor), est: int(p.est), alt: int(p.alt),
    confirmed: !!p.confirmed,
    pillar: String(p.pillar || "").slice(0, 24),
    color: int(p.color) ?? 0,
    placeName: p.placeName == null ? null : String(p.placeName).slice(0, 40),
  };
}

async function member(req, store) {
  const auth = req.headers.get("authorization") || "";
  const m = auth.match(/^Bearer ([0-9a-f]{16})\.([0-9a-f]{64})$/);
  if (!m) return null;
  const row = await store.member(m[1]);
  if (!row || row.secretHash !== (await sha256(m[2]))) return null;
  return row;
}

async function body(req) {
  try {
    const text = await req.text();
    if (text.length > BODY_MAX) return null;
    return JSON.parse(text);
  } catch { return null; }
}

async function newMember(store, groupId, name, owner) {
  const id = randomId(8), secret = randomId(32);
  await store.addMember({ id, groupId, name, secretHash: await sha256(secret), fcmToken: null, owner, created: Date.now() });
  return { memberId: id, secret, token: `${id}.${secret}` };
}

/** Wakes the other phones; a failed push is not an error for the sender. */
async function notify(env, store, groupId, exceptId, data) {
  if (!env.FCM_SA) return { sent: 0, failed: 0, reason: "no-push" };
  const tokens = (await store.members(groupId)).filter(m => m.id !== exceptId && m.fcmToken).map(m => m.fcmToken);
  let sent = 0, failed = 0, reason = null;
  for (const t of tokens) {
    const r = await sendData(env, t, data).catch(e => ({ ok: false, reason: e.message }));
    if (r.ok) sent++;
    else {
      failed++;
      reason = reason || r.reason || null;
      if (r.unregistered) await store.clearToken(t);
    }
  }
  return { sent, failed, reason };
}

export async function handle(req, env, store, ctx) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "");
  const now = Date.now();

  // unauthenticated routes are rate limited per IP when the binding exists (wrangler.toml)
  if ((path === "/v1/groups" || path === "/v1/join") && env.LIMIT) {
    const ip = req.headers.get("cf-connecting-ip") || "?";
    const { success } = await env.LIMIT.limit({ key: `${path}:${ip}` });
    if (!success) return fail(429, "slow");
  }
  if (Number(req.headers.get("content-length") || 0) > BODY_MAX) return fail(413, "size");

  if (req.method === "POST" && path === "/v1/groups") {
    const b = await body(req);
    const name = cleanName(b?.name);
    if (!name) return fail(400, "name");
    const groupId = randomId(8);
    await store.addGroup({ id: groupId, created: now });
    const m = await newMember(store, groupId, name, true);
    return json({ groupId, ...m });
  }

  if (req.method === "POST" && path === "/v1/join") {
    const b = await body(req);
    const name = cleanName(b?.name);
    const code = normCode(b?.code);
    if (!name || code.length !== 12) return fail(400, "code");
    const codeHash = await sha256(code);
    const peek = await store.invite(codeHash, now);
    if (!peek) return fail(404, "code");
    // a full family keeps the invite usable for when someone leaves
    if ((await store.members(peek.groupId)).length >= MAX_MEMBERS) return fail(409, "full");
    const inv = await store.takeInvite(codeHash, now);
    if (!inv) return fail(404, "code");
    const m = await newMember(store, inv.groupId, name, false);
    const members = await store.members(inv.groupId);
    ctx?.waitUntil?.(notify(env, store, inv.groupId, m.memberId, { type: "members" }));
    return json({ groupId: inv.groupId, ...m, members: members.map(x => ({ id: x.id, name: x.name })) });
  }

  const me = await member(req, store);
  if (!me) return fail(401, "auth");

  if (req.method === "POST" && path === "/v1/invites") {
    const code = newCode();
    await store.dropInvites(me.groupId); // only the newest invite of a family is open
    await store.addInvite({ codeHash: await sha256(normCode(code)), groupId: me.groupId, expires: now + INVITE_MS });
    return json({ code, expiresAt: now + INVITE_MS });
  }

  if (req.method === "PUT" && path === "/v1/token") {
    const b = await body(req);
    const token = b?.fcmToken ? String(b.fcmToken).slice(0, 4096) : null;
    await store.setToken(me.id, token);
    return json({ ok: true });
  }

  if (req.method === "PUT" && path === "/v1/park") {
    const park = cleanPark((await body(req))?.park, now);
    if (!park) return fail(400, "park");
    const cur = await store.park(me.groupId);
    // the newest park wins; an update of the same park (confirm, pillar) always applies
    if (cur && cur.park.id !== park.id && cur.park.atMs > park.atMs) return json({ ok: false, stale: true });
    const by = cur && cur.park.id === park.id ? cur.by : me.name;
    await store.setPark(me.groupId, { park, by, byId: cur && cur.park.id === park.id ? cur.byId : me.id, updated: now });
    // the phone logs how many family phones were woken; a push problem never fails the upload
    const push = await notify(env, store, me.groupId, me.id, { type: "park", id: String(park.id) })
      .catch(e => ({ sent: 0, failed: 0, reason: String(e.message || e) }));
    return json({ ok: true, push });
  }

  if (req.method === "GET" && path === "/v1/park") {
    const cur = await store.park(me.groupId);
    const members = await store.members(me.groupId);
    return json({
      park: cur ? { ...cur.park, by: cur.by, mine: cur.byId === me.id } : null,
      members: members.map(x => ({ id: x.id, name: x.name, me: x.id === me.id })),
    });
  }

  const del = path.match(/^\/v1\/members\/([0-9a-f]{16})$/);
  if (req.method === "DELETE" && del) {
    if (del[1] !== me.id && !me.owner) return fail(403, "owner");
    const rest = (await store.members(me.groupId)).filter(m => m.id !== del[1]);
    await store.removeMember(me.groupId, del[1]);
    // an owner leaving hands the family to the oldest member; the last one out clears the family
    if (rest.length === 0) await store.dropGroup(me.groupId);
    else if (!rest.some(m => m.owner)) await store.makeOwner(rest[0].id);
    return json({ ok: true });
  }

  return fail(404, "route");
}

export default {
  async fetch(req, env, ctx) {
    const { D1Store } = await import("./store.js");
    try {
      return await handle(req, env, new D1Store(env.DB), ctx);
    } catch (e) {
      console.error(e);
      return fail(500, "server");
    }
  },
};
