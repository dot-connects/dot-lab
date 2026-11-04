// node --test works/2026-11-04-dalgona/site/js/engine.test.mjs
// Plays whole rounds against the rules: the tuning is what decides whether the work is fun, so it is pinned here.
import test from "node:test";
import assert from "node:assert/strict";
import { createGame, step, poke, percent, RULES } from "./engine.js";

const GEOM = { cx: 215, cy: 470, R: 159 };
const seeded = (s = 7) => () => (s = (s * 16807) % 2147483647) / 2147483647;
const DT = 1 / 30;

// trace the circle outline at a given speed (radii per second) for up to `laps` laps
function traceCircle(g, speed, laps, jitter = 0) {
  const r = 0.84 * GEOM.R, w = speed * GEOM.R / r;   // angular speed
  for (let t = 0; t < laps * 2 * Math.PI / w && !g.done; t += DT) {
    const a = t * w;
    step(g, DT, { needle: { x: GEOM.cx + r * Math.cos(a), y: GEOM.cy + r * Math.sin(a), down: true }, jitter });
  }
}

test("slow laps cut the circle out inside the time limit", () => {
  const g = createGame("circle", GEOM, seeded());
  traceCircle(g, 1.0, 6);
  assert.equal(g.ok, true);
  assert.ok(g.t < RULES.T_LIMIT * 0.8, `took ${g.t.toFixed(1)} s`);
  assert.equal(g.cracks.length, 0);
});

test("rushing cracks it", () => {
  const g = createGame("circle", GEOM, seeded());
  traceCircle(g, 4, 6);
  assert.equal(g.ok, false);
  assert.ok(g.cracks.length >= 1);
});

test("a shaking hand cracks it even at a safe speed", () => {
  const g = createGame("circle", GEOM, seeded());
  traceCircle(g, 0.8, 6, 40);
  assert.ok(g.cracks.length >= 1);
});

test("normal hand shake (spike: 3-13 px) is free", () => {
  const g = createGame("circle", GEOM, seeded());
  traceCircle(g, 1.0, 6, 10);
  assert.equal(g.cracks.length, 0);
  assert.equal(g.ok, true);
});

test("careful pokes carve without cracking; frantic pokes crack", () => {
  const g = createGame("circle", GEOM, seeded());
  const r = 0.84 * GEOM.R;
  for (let i = 0; i < 36 && !g.done; i++) {
    const a = i / 36 * 2 * Math.PI;
    poke(g, GEOM.cx + r * Math.cos(a), GEOM.cy + r * Math.sin(a));
    for (let k = 0; k < 14; k++) step(g, DT, { needle: null });   // 0.47 s between pokes
  }
  assert.equal(g.cracks.length, 0);
  assert.ok(percent(g) > 40, `${percent(g)}%`);

  const h = createGame("circle", GEOM, seeded());
  for (let i = 0; i < 12 && !h.done; i++) { poke(h, GEOM.cx + r, GEOM.cy); for (let k = 0; k < 3; k++) step(h, DT, { needle: null }); }
  assert.ok(h.cracks.length >= 1);
});

test("licking one spot melts only that spot, and too long there breaks the shape", () => {
  const g = createGame("circle", GEOM, seeded());
  poke(g, GEOM.cx + 0.84 * GEOM.R, GEOM.cy);   // start the clock
  for (let i = 0; i < 30 * 1.0; i++) step(g, DT, { needle: null, lick: { x: GEOM.cx, y: GEOM.cy } });
  assert.equal(g.done, false);
  const far = g.wetG[0] + g.wetG[g.wetG.length - 1];
  assert.equal(far, 0);   // the corners stay dry
  for (let i = 0; i < 30 * 8 && !g.done; i++) step(g, DT, { needle: null, lick: { x: GEOM.cx, y: GEOM.cy } });
  assert.equal(g.cause, "melt");
});

test("the clock waits for the first touch of the needle", () => {
  const g = createGame("circle", GEOM, seeded());
  for (let i = 0; i < 300; i++) step(g, DT, { needle: { x: 10, y: 10, down: true } });
  assert.equal(g.t, 0);
});

test("every shape can be finished by tracing its outline slowly", () => {
  for (const shape of ["tri", "star", "umb"]) {
    const g = createGame(shape, GEOM, seeded());
    // walk each path back and forth along its samples at 0.8 R/s
    const v = 0.8 * GEOM.R * DT;
    for (let lap = 0; lap < 8 && !g.done; lap++) {
      for (const path of g.paths) {
        const seq = lap % 2 ? [...path].reverse() : path;
        let acc = 0, prev = seq[0];
        for (const p of seq) {
          acc += Math.hypot(p[0] - prev[0], p[1] - prev[1]); prev = p;
          if (acc < v) continue;
          acc = 0;
          step(g, DT, { needle: { x: p[0], y: p[1], down: true } });
          if (g.done) break;
        }
        step(g, DT, { needle: null });   // lift between paths
      }
    }
    assert.equal(g.ok, true, `${shape}: ${percent(g)}% at ${g.t.toFixed(1)} s, cause ${g.cause}`);
    assert.ok(g.t < RULES.T_LIMIT, `${shape} took ${g.t.toFixed(1)} s`);
    console.log(`${shape}: ${g.t.toFixed(1)} s`);
  }
});
