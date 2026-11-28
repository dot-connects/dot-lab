import { test } from "node:test";
import assert from "node:assert/strict";
import { purchaseVerdict, integrityVerdict, certHex, requestHash, PRODUCT_STYLE } from "../src/verdicts.js";

// the same vector is asserted in the app (RequestHashTest.kt): both sides must hash the same bytes the same way
export const SHARED_VECTOR = "f21c85072b81e7a3ba8879de2a6583872088957123dd8aa5ac9252cb9407bfec";

const V = {
  requestDetails: { requestPackageName: "kr.dot.catchpet", requestHash: "H", timestampMillis: "1000000" },
  appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "kr.dot.catchpet", certificateSha256Digest: ["q83vEjRWeJA="] },
  deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
};
const O = { pkg: "kr.dot.catchpet", certHex: "abcdef1234567890", requestHash: "H", now: 1_100_000 };

test("a good verdict passes, also with a stronger device label", () => {
  assert.equal(integrityVerdict(V, O), "ok");
  assert.equal(integrityVerdict({ ...V, deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY", "MEETS_STRONG_INTEGRITY"] } }, O), "ok");
});

test("each failure has its own reason", () => {
  assert.equal(integrityVerdict({ ...V, appIntegrity: { ...V.appIntegrity, appRecognitionVerdict: "UNRECOGNIZED_VERSION" } }, O), "app");
  assert.equal(integrityVerdict({ ...V, deviceIntegrity: { deviceRecognitionVerdict: [] } }, O), "device");
  assert.equal(integrityVerdict({ ...V, deviceIntegrity: {} }, O), "device");
  assert.equal(integrityVerdict(V, { ...O, requestHash: "X" }), "hash");
  assert.equal(integrityVerdict(V, { ...O, now: 1_000_000 + 301_000 }), "old");
  assert.equal(integrityVerdict(V, { ...O, now: 1_000_000 - 60_000 }), "old", "from the future");
  assert.equal(integrityVerdict(V, { ...O, certHex: "00" }), "cert");
  assert.equal(integrityVerdict(V, { ...O, certHex: "" }), "cert", "an unset CERT_SHA256 accepts nothing");
  assert.equal(integrityVerdict(V, { ...O, pkg: "x.y" }), "pkg");
  assert.equal(integrityVerdict(null, O), "empty");
});

test("certHex reads base64, base64url, colon hex and 64-digit hex", () => {
  assert.equal(certHex("q83vEjRWeJA="), "abcdef1234567890");
  assert.equal(certHex("q83vEjRWeJA"), "abcdef1234567890");
  assert.equal(certHex("AB:CD:EF:12:34:56:78:90"), "abcdef1234567890");
  assert.equal(certHex("AB".repeat(32)), "ab".repeat(32));
  assert.equal(certHex(""), "");
});

test("purchase states", () => {
  assert.equal(purchaseVerdict({ purchaseState: 0, consumptionState: 0 }), "ok");
  assert.equal(purchaseVerdict({ purchaseState: 2, consumptionState: 0 }), "pending");
  assert.equal(purchaseVerdict({ purchaseState: 1, consumptionState: 0 }), "bad");
  assert.equal(purchaseVerdict({ purchaseState: 0, consumptionState: 1 }), "bad");
  assert.equal(purchaseVerdict(null), "bad");
});

test("products map to styles", () => {
  assert.deepEqual(PRODUCT_STYLE, { sheet_fluff: "fluff", sheet_figure: "figure", sheet_both: "both" });
});

test("requestHash matches the shared vector", async () => {
  assert.equal(await requestHash("/sheet", "dev-aaaaaaaa", '{"a":1}'), SHARED_VECTOR);
});
