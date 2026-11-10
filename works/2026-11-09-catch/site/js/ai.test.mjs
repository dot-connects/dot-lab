import { test } from "node:test";
import assert from "node:assert/strict";
import { chromaKey } from "./ai.js";

test("chromaKey: green goes, fur stays, a green-ish edge fades and loses its green", () => {
  const px = new Uint8ClampedArray([0, 255, 0, 255,   250, 246, 236, 255,   40, 40, 40, 255,   150, 200, 150, 255,   120, 140, 120, 255]);
  chromaKey(px);
  assert.equal(px[3], 0);                          // pure green
  assert.deepEqual([...px.slice(4, 8)], [250, 246, 236, 255]);   // cream fur untouched
  assert.deepEqual([...px.slice(8, 12)], [40, 40, 40, 255]);     // dot eye untouched
  assert.ok(px[15] > 0 && px[15] < 255); assert.equal(px[13], 150);   // edge: half alpha, green pulled to max(r,b)
  assert.equal(px[19], 255); assert.equal(px[17], 120);                // slight spill: opaque, despilled
});
