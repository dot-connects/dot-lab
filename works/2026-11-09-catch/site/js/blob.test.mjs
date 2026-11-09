import test from "node:test";
import assert from "node:assert/strict";
import "./blob.js";
import { track, widen, square } from "./geom.js";

const { blobBox, kindOf, keepLargest } = globalThis;
// w x h mask with filled rectangles [x, y, w, h, value]
function mask(w, h, ...rects) {
  const m = new Float32Array(w * h);
  for (const [x, y, rw, rh, v = 1] of rects) for (let j = y; j < y + rh; j++) for (let i = x; i < x + rw; i++) m[j * w + i] = v;
  return m;
}

test("blobBox: the largest blob as a 0..1 box, not the union of blobs", () => {
  const b = blobBox(mask(100, 50, [10, 10, 20, 10], [60, 5, 30, 40]), 100, 50);
  assert.deepEqual([b.x, b.y, b.w, b.h], [0.6, 0.1, 0.3, 0.8]);
  assert.equal(b.frac, 1200 / 5000);
});

test("blobBox: under 0.5 is background; a blob under 3 % of the frame is nothing", () => {
  assert.equal(blobBox(mask(100, 100, [0, 0, 50, 50, 0.49]), 100, 100), null);
  assert.equal(blobBox(mask(100, 100, [0, 0, 17, 17]), 100, 100), null);   // 289 px < 300
  assert.ok(blobBox(mask(100, 100, [0, 0, 18, 18]), 100, 100));             // 324 px
});

test("blobBox: diagonal touch is two blobs (4-connected)", () => {
  const b = blobBox(mask(10, 10, [0, 0, 3, 3], [3, 3, 2, 2]), 10, 10, 0.5, 0);
  assert.deepEqual([b.w, b.h], [0.3, 0.3]);
});

test("kindOf: more cat than dog inside the blob = cat, outside pixels do not count", () => {
  const w = 10, h = 10, cat = mask(w, h, [0, 0, 5, 10, 0.6]), dog = mask(w, h, [0, 0, 5, 10, 0.3], [5, 0, 5, 10, 0.9]);
  const any = cat.map((v, i) => Math.min(1, v + dog[i]));
  const b = blobBox(any.map((v, i) => (i % w < 5 ? v : 0)), w, h);
  assert.equal(kindOf(cat, dog, b.lab, b.id), "cat");
  const all = blobBox(any, w, h);
  assert.equal(kindOf(cat, dog, all.lab, all.id), "dog");
});

test("keepLargest: other blobs and far soft edges go, the blob and its near soft edge stay", () => {
  const m = mask(20, 10, [2, 2, 8, 6], [14, 2, 3, 3], [11, 2, 1, 1, 0.3], [19, 9, 1, 1, 0.3]);
  keepLargest(m, 20, 10, 0.5, 2);
  assert.equal(m[2 * 20 + 2], 1);     // the blob
  assert.ok(Math.abs(m[2 * 20 + 11] - 0.3) < 1e-6);  // soft edge 2 px from it
  assert.equal(m[2 * 20 + 14], 0);    // second blob
  assert.equal(m[9 * 20 + 19], 0);    // far soft pixel
});

test("track: EMA 0.5, two misses hold the last box, the third drops it", () => {
  let t = track(null, { x: 0, y: 0, w: 0.4, h: 0.4, kind: "dog" });
  t = track(t, { x: 0.2, y: 0.2, w: 0.4, h: 0.4, kind: "dog" });
  assert.deepEqual([t.x, t.y, t.miss], [0.1, 0.1, 0]);
  t = track(t, null); assert.equal(t.miss, 1); assert.equal(t.x, 0.1);
  t = track(t, null); assert.equal(t.miss, 2);
  assert.equal(track(t, null), null);
  assert.equal(track(null, null), null);
});

test("widen: 25 % wider and taller around the box, clamped to the frame", () => {
  assert.deepEqual(widen({ x: 100, y: 100, w: 80, h: 40 }, 1000, 1000), { x: 90, y: 95, w: 100, h: 50 });
  assert.deepEqual(widen({ x: 0, y: 0, w: 80, h: 40 }, 85, 1000), { x: 0, y: 0, w: 85, h: 45 });
});

test("square: the short side grows to the long side around the centre, clamped to the frame", () => {
  assert.deepEqual(square({ x: 100, y: 100, w: 200, h: 80 }, 1000, 1000), { x: 100, y: 40, w: 200, h: 200 });
  assert.deepEqual(square({ x: 100, y: 10, w: 200, h: 80 }, 1000, 1000), { x: 100, y: 0, w: 200, h: 200 });
  assert.deepEqual(square({ x: 0, y: 0, w: 1000, h: 300 }, 1000, 720), { x: 0, y: 0, w: 1000, h: 720 });
});
