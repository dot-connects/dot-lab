// Group sync for the parking floor app: phones in up to 3 groups share their cars' latest parks.
// No accounts: a device id + secret (only the secret's SHA-256 is stored), memberships via one-time invite codes.
// /v3 is the current API, /v2 a compat layer for the installed app, /v1 is closed (410).

import * as v2 from "./v2.js";
import * as v3 from "./v3.js";
import { renderJoinPage, renderRequestPage } from "./jpage.js";

const NAME_MAX = 12;

export const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8" } });
export const fail = (status, error) => json({ error }, status);

export function randomId(bytes) {
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

/** Invite hashes handed to live join requests: a new invite must not close them. */
export async function requestInvites(store, groupId, now) {
  return Promise.all((await store.groupRequests(groupId, now)).map(r => sha256(r.code)));
}

export const cleanName = n => String(n || "").trim().slice(0, NAME_MAX);

export async function handle(req, env, store, ctx) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "");

  if (env.MAINTENANCE === "1" && /^\/v[123]\//.test(url.pathname) &&
      !(env.SMOKE_KEY && req.headers.get("x-smoke") === env.SMOKE_KEY)) {
    const response = fail(503, "maintenance");
    response.headers.set("Retry-After", "600");
    return response;
  }
  if (path.startsWith("/v2/")) return v2.handle(req, env, store, ctx);
  if (path.startsWith("/v3/")) return v3.handle(req, env, store, ctx);
  if (req.method === "GET" && path === "/j") return new Response(renderJoinPage(env), { headers: { "content-type": "text/html; charset=utf-8" } });
  if (req.method === "GET" && path === "/r") return new Response(renderRequestPage(env), { headers: { "content-type": "text/html; charset=utf-8" } });
  if (req.method === "GET" && path === "/.well-known/assetlinks.json") {
    const fingerprints = String(env.ASSETLINKS_SHA256 || "").split(",").map(x => x.trim()).filter(Boolean);
    return json(fingerprints.map(fingerprint => ({ relation: ["delegate_permission/common.handle_all_urls"], target: { namespace: "android_app", package_name: "kr.dot.wherepark", sha256_cert_fingerprints: [fingerprint] } })));
  }

  if (path.startsWith("/v1/")) return fail(410, "update");
  return fail(404, "route");
}

export async function scheduled(event, env, ctx, store) {
  if (env.MAINTENANCE === "1") return;
  if (!store) { const { D1Store } = await import("./store.js"); store = new D1Store(env.DB); }
  const cutoff = (event.scheduledTime || Date.now()) - 30 * 86400_000;
  // objects first: a crash before the flag flip is retried next run (delete is idempotent)
  for (const k of await store.expiredPhotos3(cutoff)) {
    await env.PHOTOS?.delete(`c/${k.carId}/${k.parkId}.jpg`);
    if (k.legacyGroup && k.legacyCarId) await env.PHOTOS?.delete(`g/${k.legacyGroup}/${k.legacyCarId}/${k.parkId}.jpg`);
  }
  await store.expirePhotos3(cutoff);
  await store.expireRequests(event.scheduledTime || Date.now());  // their invite rows expire on their own
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
  scheduled,
};
