import test from "node:test";
import assert from "node:assert/strict";
import { coverMap, release, curveOf, flight, hit, FLIGHT_MS } from "./geom.js";

const near = (a, b, e = 1e-6) => assert.ok(Math.abs(a - b) < e, `${a} != ${b}`);

test("coverMap: portrait video on a portrait screen fills and crops like object-fit: cover", () => {
  // 720x1280 video on 390x844: scale = max(390/720, 844/1280) = 0.659..., width 474.75 -> 42.375 cut each side
  const m = coverMap(720, 1280, 390, 844);
  const [x0, y0] = m([0, 0]), [x1, y1] = m([720, 1280]);
  near(y0, 0); near(y1, 844);
  near(x0, -(720 * 844 / 1280 - 390) / 2); near(x1, 390 + (720 * 844 / 1280 - 390) / 2);
  const [cx, cy] = m([360, 640]); near(cx, 195); near(cy, 422);
});

test("coverMap: landscape video on a portrait screen crops the sides", () => {
  const m = coverMap(1280, 720, 390, 844), k = 844 / 720;
  const [x, y] = m([640, 360]); near(x, 195); near(y, 422);
  near(m([0, 0])[0], 195 - 640 * k);
});

// points 16 ms apart along a straight line
const line = (x0, y0, x1, y1, ms) => {
  const n = Math.round(ms / 16), out = [];
  for (let i = 0; i <= n; i++) out.push({ x: x0 + (x1 - x0) * i / n, y: y0 + (y1 - y0) * i / n, t: 1000 + i * 16 });
  return out;
};

test("release: a quick upward flick lands v x 260 ms beyond the release point", () => {
  const p = line(195, 660, 195, 560, 128);   // 100 px up in 128 ms = 0.78 px/ms
  const r = release(p, 390, 844);
  assert.ok(r);
  near(r.from.x, 195); near(r.from.y, 560);
  near(r.to.x, 195, 1e-3); near(r.to.y, 560 - 100 / 128 * 260, 1e-3);
});

test("release: too short, too slow or downward is not a throw", () => {
  assert.equal(release(line(195, 660, 195, 620, 48), 390, 844), null);     // 40 px
  assert.equal(release(line(195, 660, 195, 560, 400), 390, 844), null);    // 0.25 px/ms up
  assert.equal(release(line(195, 560, 195, 660, 100), 390, 844), null);    // down
  assert.equal(release(line(100, 660, 300, 650, 100), 390, 844), null);    // sideways
});

test("release: only the last 80 ms set the speed (a slow start then a flick)", () => {
  const slow = line(195, 700, 195, 680, 400), fast = line(195, 680, 195, 560, 96).map(q => ({ ...q, t: q.t + 400 }));
  const r = release([...slow, ...fast.slice(1)], 390, 844);
  assert.ok(r);
  near(r.to.y, 560 - 120 / 96 * 260, 1);
});

test("release: landing stays on the screen", () => {
  const r = release(line(195, 660, 195, 300, 64), 390, 844);
  assert.ok(r.to.y >= 0);
});

// a circle drawn with n points, direction +1 = clockwise on screen (y down)
const circle = (cx, cy, rad, turns, dir) => {
  const out = [], n = Math.round(36 * turns);
  for (let i = 0; i <= n; i++) { const a = dir * i / 36 * 2 * Math.PI; out.push({ x: cx + rad * Math.cos(a), y: cy + rad * Math.sin(a), t: i * 16 }); }
  return out;
};

test("curveOf: a full circle either way is a curve with its sign, a wiggle is not", () => {
  assert.equal(curveOf(circle(195, 660, 30, 1, 1)), 1);
  assert.equal(curveOf(circle(195, 660, 30, 1, -1)), -1);
  assert.equal(curveOf(circle(195, 660, 30, 0.7, 1)), 0);   // 252 degrees
  assert.equal(curveOf(line(195, 660, 195, 560, 128)), 0);
  const zig = []; for (let i = 0; i < 20; i++) zig.push({ x: 195 + (i % 2) * 20, y: 660 - i * 10, t: i * 16 });
  assert.equal(curveOf(zig), 0);
});

test("flight: starts at release, ends at landing (bent by a curve), arcs up and shrinks", () => {
  const th = { from: { x: 195, y: 600 }, to: { x: 195, y: 300 }, curve: 0 };
  const a = flight(th, 0, 390, 844), b = flight(th, 1, 390, 844), m = flight(th, 0.5, 390, 844);
  near(a.x, 195); near(a.y, 600); near(a.s, 1);
  near(b.x, 195); near(b.y, 300); near(b.s, 0.6);
  near(m.y, 450 - 0.12 * 844);
  const c = flight({ ...th, curve: 1 }, 1, 390, 844);
  near(c.x, 195 + 0.15 * 390);
  near(flight({ ...th, curve: -1 }, 0.3, 390, 844).x, 195);   // the bend is late in the flight
  assert.ok(FLIGHT_MS === 500);
});

test("hit: body box and ring circle", () => {
  const box = { x: 100, y: 200, w: 120, h: 160 }, ring = { x: 160, y: 280, r: 30 };
  assert.deepEqual(hit({ x: 160, y: 280 }, box, ring), { onBody: true, inRing: true });
  assert.deepEqual(hit({ x: 105, y: 210 }, box, ring), { onBody: true, inRing: false });
  assert.deepEqual(hit({ x: 50, y: 210 }, box, ring), { onBody: false, inRing: false });
  assert.deepEqual(hit({ x: 160, y: 280 }, null, ring), { onBody: false, inRing: false });
});
