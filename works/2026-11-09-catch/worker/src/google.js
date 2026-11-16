// Google calls for 리얼뽑기 on Play: one service account (Worker secret GOOGLE_SA = its JSON key) signs an OAuth JWT
// (RS256, WebCrypto) for the Play Developer API (purchase check + consume) and Play Integrity (token decode).
// Errors carry only an HTTP status: never a purchase token, an integrity token or the key (lesson 11-12: a logged
// header leaked a key).
import { secret } from "./secret.js";

const SCOPE = "https://www.googleapis.com/auth/androidpublisher https://www.googleapis.com/auth/playintegrity";
const PUBLISHER = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
const INTEGRITY = "https://playintegrity.googleapis.com/v1";

let cached = null;   // { token, exp (ms) } for this isolate
export const _resetTokenCache = () => { cached = null; };

const fail = status => Object.assign(new Error(`google HTTP ${status}`), { status });
const b64url = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlText = s => b64url(new TextEncoder().encode(s));

function account(env) {
  try {
    const sa = JSON.parse(secret(env.GOOGLE_SA));
    if (!sa.client_email || !sa.private_key) throw 0;
    return sa;
  } catch { throw fail(0); }
}

async function signingKey(pem) {
  const der = Uint8Array.from(atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "")), c => c.charCodeAt(0));
  return crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

/** An OAuth access token for the service account, cached until 60 s before it expires. */
export async function googleToken(env, fetchFn = fetch, now = Date.now()) {
  if (cached && now < cached.exp - 60_000) return cached.token;
  const sa = account(env);
  const aud = sa.token_uri || "https://oauth2.googleapis.com/token";
  const iat = Math.floor(now / 1000);
  const unsigned = b64urlText(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    b64urlText(JSON.stringify({ iss: sa.client_email, scope: SCOPE, aud, iat, exp: iat + 3600 }));
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", await signingKey(sa.private_key), new TextEncoder().encode(unsigned));
  const r = await fetchFn(aud, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: unsigned + "." + b64url(sig) }).toString(),
  });
  if (!r.ok) throw fail(r.status);
  const j = await r.json();
  cached = { token: j.access_token, exp: now + (j.expires_in || 3600) * 1000 };
  return cached.token;
}

async function call(env, fetchFn, url, init = {}) {
  const token = await googleToken(env, fetchFn);
  const r = await fetchFn(url, { ...init, headers: { ...(init.headers || {}), authorization: `Bearer ${token}` } });
  if (!r.ok) throw fail(r.status);
  return r;
}

const productUrl = (env, product, token) => `${PUBLISHER}/${env.PKG}/purchases/products/${product}/tokens/${encodeURIComponent(token)}`;

/** The Play Developer API v3 ProductPurchase for one token. */
export async function getPurchase(env, product, token, fetchFn = fetch) {
  return (await call(env, fetchFn, productUrl(env, product, token))).json();
}

/** Consume (and so acknowledge) a purchase; true when Google accepted it. */
export async function consumePurchase(env, product, token, fetchFn = fetch) {
  await call(env, fetchFn, productUrl(env, product, token) + ":consume", { method: "POST" });
  return true;
}

/** Decode a standard Play Integrity token: the verdict object (tokenPayloadExternal). */
export async function decodeIntegrity(env, integrityToken, fetchFn = fetch) {
  const r = await call(env, fetchFn, `${INTEGRITY}/${env.PKG}:decodeIntegrityToken`, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ integrity_token: integrityToken }),
  });
  return (await r.json()).tokenPayloadExternal;
}
