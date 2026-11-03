// Firebase Cloud Messaging HTTP v1 from a Worker: a service-account JWT (RS256, WebCrypto) is traded for an
// OAuth token, cached in memory until shortly before it expires. Only data messages are sent; the phone
// draws its own notification.

let cached = null; // { token, until }

/** The secret as pasted; trim() also drops the UTF-8 BOM a Windows pipe adds. */
export const parseSa = text => JSON.parse(String(text).trim());

const b64url = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlText = s => b64url(new TextEncoder().encode(s));

function pemToDer(pem) {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const raw = atob(body);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out.buffer;
}

export async function signedJwt(sa, nowS) {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowS,
    exp: nowS + 3600,
  };
  const input = `${b64urlText(JSON.stringify(header))}.${b64urlText(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey("pkcs8", pemToDer(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(input));
  return `${input}.${b64url(sig)}`;
}

async function accessToken(env, fetcher) {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.until > now + 60) return cached.token;
  const sa = parseSa(env.FCM_SA);
  const jwt = await signedJwt(sa, now);
  const r = await fetcher("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  if (!r.ok) throw new Error(`token ${r.status}`);
  const t = await r.json();
  cached = { token: t.access_token, until: now + (t.expires_in || 3600) };
  return cached.token;
}

/** Returns { ok } or { ok: false, unregistered } so dead tokens can be dropped. */
export async function sendData(env, token, data, fetcher = fetch) {
  const sa = parseSa(env.FCM_SA);
  const access = await accessToken(env, fetcher);
  const r = await fetcher(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${access}` },
    body: JSON.stringify({ message: { token, data, android: { priority: "high", ttl: "3600s" } } }),
  });
  if (r.ok) return { ok: true };
  const text = await r.text();
  const status = (text.match(/"status":\s*"([A-Z_]+)"/) || [])[1] || "";
  return { ok: false, unregistered: r.status === 404 || text.includes("UNREGISTERED"), reason: `${r.status} ${status}`.trim() };
}

export function resetTokenCache() { cached = null; }
