// node --test works/2026-11-04-dalgona/site/js/camera.test.mjs
// The jab detector is a small state machine; v1 counted one real jab 2-3 times and broke the candy.
import test from "node:test";
import assert from "node:assert/strict";
import { createPokeDetector } from "./camera.js";

// hand size over time at a camera frame rate, with noise; jabs = short growth bumps
function run(fps, seconds, sizeAt, seed = 5) {
  let s = seed;
  const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
  const d = createPokeDetector();
  let n = 0;
  for (let t = 0; t < seconds * 1000; t += 1000 / fps) if (d.feed(t, sizeAt(t) * (1 + (rnd() - 0.5) * 0.02))) n++;
  return n;
}
// a jab: the hand grows ~25% over 120 ms toward the camera, then comes back over 250 ms
const jab = (t, at) => { const u = t - at; return u < 0 ? 0 : u < 120 ? 0.25 * u / 120 : u < 370 ? 0.25 * (1 - (u - 120) / 250) : 0; };

test("a still hand for 30 s makes no pokes", () => {
  for (const fps of [14, 21, 30]) assert.equal(run(fps, 30, () => 100), 0, `${fps} fps`);
});

test("ten jabs make ten pokes at 14-30 fps", () => {
  const times = Array.from({ length: 10 }, (_, i) => 500 + i * 900);
  for (const fps of [14, 21, 30]) {
    assert.equal(run(fps, 10, t => 100 * (1 + times.reduce((a, at) => a + jab(t, at), 0))), 10, `${fps} fps`);
  }
});

test("slowly bringing the hand closer while scraping is not a poke", () => {
  // 40% bigger over 2 s (rate 0.2/s) plus a slow wobble
  for (const fps of [14, 21, 30]) assert.equal(run(fps, 6, t => 100 * (1 + 0.2 * Math.min(t, 2000) / 1000 + 0.03 * Math.sin(t / 300))), 0);
});
