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
  assert.ok(g.t < g.limit * 0.8, `took ${g.t.toFixed(1)} s`);
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

test("a few careful pokes are free; frantic pokes crack", () => {
  const g = createGame("circle", GEOM, seeded());
  const r = 0.84 * GEOM.R;
  for (let i = 0; i < 8 && !g.done; i++) {
    const a = i / 36 * 2 * Math.PI;
    poke(g, GEOM.cx + r * Math.cos(a), GEOM.cy + r * Math.sin(a));
    for (let k = 0; k < 14; k++) step(g, DT, { needle: null });   // 0.47 s between pokes
  }
  assert.equal(g.cracks.length, 0);

  const h = createGame("circle", GEOM, seeded());
  for (let i = 0; i < 12 && !h.done; i++) { poke(h, GEOM.cx + r, GEOM.cy); for (let k = 0; k < 3; k++) step(h, DT, { needle: null }); }
  assert.ok(h.cracks.length >= 1);
});

// code review r2 H2: poking all the way round at just over FAST_GAP took every shape with three stars
test("poking the whole outline on a steady rhythm does not come out clean", () => {
  for (const shape of ["circle", "tri", "star", "umb"]) {
    const g = createGame(shape, GEOM, seeded(9), { camera: true });   // the longest clock
    const pts = g.paths.flatMap(p => p.filter((_, i) => i % 4 === 0));
    for (let lap = 0; lap < 4 && !g.done; lap++) for (const q of pts) {
      if (g.done) break;
      poke(g, q[0], q[1]);
      for (let k = 0; k < 14; k++) step(g, DT, { needle: null });   // 0.47 s
    }
    assert.ok(!g.ok || g.cracks.some(c => c.maxStage > 0) || g.chips, `${shape}: clean win by rhythm poking`);
  }
});

test("licking one spot melts only that spot, and too long there breaks the shape", () => {
  const g = createGame("circle", GEOM, seeded());
  for (let i = 0; i < 30 * 1.0; i++) step(g, DT, { needle: null, lick: { x: GEOM.cx, y: GEOM.cy } });
  assert.equal(g.done, false);
  const far = g.wetG[0] + g.wetG[g.wetG.length - 1];
  assert.equal(far, 0);   // the corners stay dry
  for (let i = 0; i < 30 * 8 && !g.done; i++) step(g, DT, { needle: null, lick: { x: GEOM.cx, y: GEOM.cy } });
  assert.equal(g.cause, "melt");
});

test("a 30 fps camera on a 120 Hz screen reads the same speed (review 11-04)", () => {
  const g = createGame("circle", GEOM, seeded());
  const r = 0.84 * GEOM.R, w = 1.0 * GEOM.R / r, sdt = 1 / 120;
  let pos = null;
  for (let i = 0; i < 120 * 80 && !g.done; i++) {
    const fresh = i % 4 === 0;
    if (fresh) { const a = i * sdt * w; pos = { x: GEOM.cx + r * Math.cos(a), y: GEOM.cy + r * Math.sin(a), down: true }; }
    step(g, sdt, { needle: fresh ? pos : { ...pos, held: true }, moveDt: 1 / 30 });
  }
  assert.equal(g.ok, true, `${percent(g)}%, cracks ${g.cracks.length}`);
  assert.equal(g.cracks.length, 0);
});

test("a hand found again somewhere else does not count as a fast move", () => {
  const g = createGame("circle", GEOM, seeded());
  const r = 0.84 * GEOM.R;
  step(g, DT, { needle: { x: GEOM.cx + r, y: GEOM.cy, down: true } });
  step(g, DT, { needle: { x: GEOM.cx + r, y: GEOM.cy + 1, down: true } });
  step(g, DT, { needle: { x: GEOM.cx - r, y: GEOM.cy, down: true, resume: true } });
  assert.equal(g.cracks.length, 0);
  assert.ok(g.stress < 0.1, `stress ${g.stress}`);
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
    assert.ok(g.t < g.limit, `${shape} took ${g.t.toFixed(1)} s`);
    console.log(`${shape}: ${g.t.toFixed(1)} s`);
  }
});

// v2 (operator 11-04 "조금만 찌르면 바로 깨지네"): cracks come in stages and only depth inside the shape fails
test("one poke never cracks; three quick pokes leave at most a hairline", () => {
  const r = 0.84 * GEOM.R;
  for (let seed = 1; seed <= 200; seed++) {
    const g = createGame("circle", GEOM, seeded(seed * 104729 + 7));
    poke(g, GEOM.cx + r, GEOM.cy);
    for (let k = 0; k < 30; k++) step(g, DT, { needle: null });
    assert.equal(g.cracks.length, 0, `seed ${seed}: one poke cracked`);
    for (let i = 0; i < 3; i++) { poke(g, GEOM.cx + r, GEOM.cy); for (let k = 0; k < 4; k++) step(g, DT, { needle: null }); }
    for (let k = 0; k < 90; k++) step(g, DT, { needle: null });
    assert.equal(g.done, false, `seed ${seed}: three pokes ended the round`);
  }
});

test("a crack stops growing when the pressure stops", () => {
  const r = 0.84 * GEOM.R;
  const g = createGame("circle", GEOM, seeded(3));
  for (let i = 0; i < 4 && g.cracks.length === 0; i++) { poke(g, GEOM.cx + r, GEOM.cy); for (let k = 0; k < 3; k++) step(g, DT, { needle: null }); }
  assert.ok(g.cracks.length >= 1);
  for (let k = 0; k < 60; k++) step(g, DT, { needle: null });
  const len = g.cracks[0].len;
  for (let k = 0; k < 300; k++) step(g, DT, { needle: null });
  assert.equal(g.cracks[0].len, len);
});

test("jabbing on and on ends the round, through a chip or a crack into the shape", () => {
  const r = 0.84 * GEOM.R;
  let fails = 0, chips = 0;
  for (let seed = 1; seed <= 100; seed++) {
    const g = createGame("circle", GEOM, seeded(seed * 104729 + 7));
    for (let i = 0; i < 60 && !g.done; i++) { poke(g, GEOM.cx + r, GEOM.cy); for (let k = 0; k < 4; k++) step(g, DT, { needle: null }); }
    if (g.cause === "crack") fails++;
    chips += g.chips;
  }
  assert.ok(fails > 80, `only ${fails}/100 failed`);
  assert.ok(chips > 20, `${chips} chips`);
});

// review r2: pokes are the corner tool, licking is crack first aid, camera play gets more time
test("a poke on a star corner carves it nearly through; on a flat edge it does not", () => {
  const g = createGame("star", GEOM, seeded(11));
  const p = g.paths[0], tipI = p.findIndex((_, i) => g.weak[0][i]), flatI = p.findIndex((_, i) => !g.weak[0][i] && i > tipI + 8);
  poke(g, ...p[tipI]); poke(g, ...p[flatI]);
  assert.ok(g.prog[0][tipI] >= 0.85, `corner ${g.prog[0][tipI]}`);
  assert.ok(g.prog[0][flatI] < 0.5, `edge ${g.prog[0][flatI]}`);
});

test("licking a hairline closes it", () => {
  const r = 0.84 * GEOM.R;
  const g = createGame("circle", GEOM, seeded(5));
  for (let i = 0; i < 5 && g.cracks.length === 0; i++) { poke(g, GEOM.cx + r, GEOM.cy); for (let k = 0; k < 3; k++) step(g, DT, { needle: null }); }
  assert.ok(g.cracks.length >= 1);
  for (let k = 0; k < 60; k++) step(g, DT, { needle: null });   // pressure gone, crack stopped
  const c = g.cracks[0];
  for (let k = 0; k < 30 * 4 && !c.healed; k++) step(g, DT, { needle: null, lick: { x: c.pts[0][0], y: c.pts[0][1] } });
  assert.equal(c.healed, true);
});

test("camera play gets twice the time", () => {
  assert.equal(createGame("umb", GEOM, seeded(), { camera: true }).limit, 2 * createGame("umb", GEOM, seeded()).limit);
});
