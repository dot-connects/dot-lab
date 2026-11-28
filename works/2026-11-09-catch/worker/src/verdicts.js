// Pure judgements over Google's answers: is this purchase usable, is this integrity verdict our real app on a real phone.

// sheet_both = fur-ball + figurine sheets from one purchase (operator 11-28, 700 won)
export const PRODUCT_STYLE = { sheet_fluff: "fluff", sheet_figure: "figure", sheet_both: "both" };

/** Play Developer API v3 ProductPurchase -> "ok" (paid, not consumed) | "pending" | "bad". */
export function purchaseVerdict(p) {
  if (!p) return "bad";
  if (p.purchaseState === 2) return "pending";
  return p.purchaseState === 0 && p.consumptionState === 0 ? "ok" : "bad";
}

/** A certificate SHA-256 in base64, base64url or hex (colons allowed) -> lowercase hex ("" for nothing). */
export function certHex(s) {
  const t = String(s ?? "").trim();
  if (!t) return "";
  // Play Console shows "AB:CD:..." (64 hex digits); the integrity verdict sends base64url
  const hex = t.replace(/:/g, "");
  if (t.includes(":") || /^[0-9a-fA-F]{64}$/.test(hex)) return hex.toLowerCase();
  const b64 = t.replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64 + "=".repeat((4 - (b64.length % 4)) % 4));
  return Array.from(bin, c => c.charCodeAt(0).toString(16).padStart(2, "0")).join("");
}

const MAX_AGE = 300_000, SKEW = 30_000;

/** A decoded standard integrity verdict -> "ok" or the first failing reason. */
export function integrityVerdict(v, { pkg, certHex: cert, requestHash, now }) {
  if (!v) return "empty";
  const req = v.requestDetails || {}, app = v.appIntegrity || {}, dev = v.deviceIntegrity || {};
  if (req.requestPackageName !== pkg || app.packageName !== pkg) return "pkg";
  if (app.appRecognitionVerdict !== "PLAY_RECOGNIZED") return "app";
  if (!cert || !(app.certificateSha256Digest || []).some(d => certHex(d) === cert)) return "cert";
  if (!(dev.deviceRecognitionVerdict || []).includes("MEETS_DEVICE_INTEGRITY")) return "device";
  if (req.requestHash !== requestHash) return "hash";
  const age = now - Number(req.timestampMillis);
  if (!(age <= MAX_AGE && age >= -SKEW)) return "old";
  return "ok";
}

const hex = buf => Array.from(new Uint8Array(buf), b => b.toString(16).padStart(2, "0")).join("");
const sha = async s => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));

/** The hash the app puts in its integrity request: sha256 hex of "path\ndevice\nsha256hex(body)". */
export async function requestHash(path, device, bodyText) {
  return sha(`${path}\n${device}\n${await sha(bodyText)}`);
}
