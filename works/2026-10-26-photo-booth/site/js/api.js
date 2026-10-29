// Worker client. API: PLAN Task 2, shapes: CONTRACT.md "api.js".
export { WORKER } from "./config.js";
import { WORKER } from "./config.js";

const b64u = a => btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
export const newToken = () => b64u(crypto.getRandomValues(new Uint8Array(32)));

// Round 8 D: the screen string the Worker pairs with the IP to recognise this phone in another browser (Chrome vs an
// in-app browser keep separate localStorage). Sorted so turning the phone does not change it.
const ua = navigator.userAgent;
const os = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) ? "ios" : /Android/.test(ua) ? "android" : "other";
const [sw, sh] = [screen.width, screen.height].sort((x, y) => x - y);
// a query parameter, not a header: a custom header needs a CORS preflight that a Worker deployed before Round 8
// refuses (the live site broke when Pages went out first); an old Worker just ignores ?d=
const DEV = "d=" + encodeURIComponent(`${sw}x${sh}@${Math.round(devicePixelRatio * 100) / 100}|${os}`);

const url = (...p) => `${WORKER}/s/${p.map(encodeURIComponent).join("/")}`;
const body = async r => { try { return await r.json(); } catch { return {}; } };

// opts.movable: B may move / resize A (Round 3); opts.shape: "wide" (가로) | "tall" (세로), B follows it (Round 6 #9);
// opts.ui: "film" | "cute" UI theme, B follows it (Round 6); status() echoes all three back as `opts`
export async function create(token, opts = {}) {
  const r = await fetch(`${WORKER}/s?${DEV}`, { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, opts: { movable: opts.movable === true, shape: opts.shape === "tall" ? "tall" : "wide", ui: opts.ui === "cute" ? "cute" : "film" } }) });
  if (r.status !== 201) throw new Error(`create ${r.status}`);
  return r.json();
}

// one file; retries 2x on network errors only (an HTTP error answer is final)
export async function put(id, side, name, blob, type, token) {
  for (let tries = 0; ; tries++) {
    let r;
    try {
      r = await fetch(url(id, side, name), { method: "PUT", body: blob,
        headers: { Authorization: `Bearer ${token}`, "Content-Type": type } });
    } catch (e) {
      if (tries >= 2) throw e;
      await new Promise(res => setTimeout(res, 800 * (tries + 1)));
      continue;
    }
    if (r.status === 204) return;
    const e = new Error(`put ${name} ${r.status}`); e.status = r.status; e.error = (await body(r)).error;
    throw e;
  }
}

export async function done(id, side, token) {
  const r = await fetch(`${url(id, side, "done")}?${DEV}`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
  if (r.status === 204) return { ok: true };
  const j = await body(r);
  if (r.status === 400 && j.error === "missing") return { missing: j.names };
  return { error: j.error || `http-${r.status}` };
}

// status: { a, b, expires, opts, mine: "a" | "b" | null } (mine = this phone made that side, seen from any browser)
export async function status(id) {
  const r = await fetch(`${url(id)}?${DEV}`, { cache: "no-store" });
  if (r.status === 404) return { error: "not-found" };
  if (r.status === 410) return { error: "expired" };
  if (!r.ok) throw new Error(`status ${r.status}`);
  return r.json();
}

export async function blobUrl(id, side, name) {
  const r = await fetch(url(id, side, name), { cache: "no-store" });
  if (!r.ok) throw new Error(`get ${name} ${r.status}`);
  return URL.createObjectURL(await r.blob());
}

const KEY = id => "booth:" + id;
export function loadRole(id) {
  try { return JSON.parse(localStorage.getItem(KEY(id))) || null; } catch { return null; }
}
export function saveRole(id, role, token) {
  try { localStorage.setItem(KEY(id), JSON.stringify({ role, token })); } catch {}
}
