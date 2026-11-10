import test from "node:test";
import assert from "node:assert/strict";
import { coverMap, release, reach, assist, curveOf, turnOf, flight, sweep, FLIGHT_MS, END_SCALE } from "./geom.js";

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

const W = 390, H = 844, REST = { x: 195, y: 658 };
const BOX = { x: 115, y: 388, w: 160, h: 160 }, C = { x: 195, y: 468 };   // centre 190 px above the hand

test("reach: linear in swipe speed, clamped .4 .. 2.2", () => {
  near(reach(2.2), 0.986); near(reach(1.2), 0.606); near(reach(0), 0.4); near(reach(10), 2.2);
});

test("release: distance = reach(speed) x distance from the hand to the box centre, direction = last 80 ms", () => {
  const p = line(195, 658, 195, 458, 96);   // 200 px up in 96 ms = 2.083 px/ms
  const r = release(p, W, H, REST, BOX);
  assert.ok(r);
  near(r.from.x, 195); near(r.from.y, 458);
  near(r.v, 200 / 96, 1e-9);
  near(r.raw.x, 195); near(r.raw.y, 658 - 190 * reach(200 / 96), 1e-6);
  assert.deepEqual(r.to, r.raw);             // on the box: no assist
  // 10 degrees right of straight up: the landing moves along that direction from the hand
  const a = 10 * Math.PI / 180, q = line(195, 658, 195 + 200 * Math.sin(a), 658 - 200 * Math.cos(a), 96), t = release(q, W, H, REST, BOX);
  const ox = 195 + 200 * Math.sin(a), D = Math.hypot(C.x - ox, C.y - 658), L = D * reach(200 / 96);
  near(t.raw.x, ox + Math.sin(a) * L, 1e-6); near(t.raw.y, 658 - Math.cos(a) * L, 1e-6);
});

test("release: no box = 40 % of the screen height times reach, no assist", () => {
  const r = release(line(195, 658, 195, 458, 96), W, H, REST, null);
  near(r.raw.y, 658 - 0.4 * H * reach(200 / 96), 1e-6); assert.deepEqual(r.to, r.raw);
});

test("release: too short, too slow or downward is not a throw", () => {
  assert.equal(release(line(195, 660, 195, 620, 48), W, H, REST, BOX), null);     // 40 px
  assert.equal(release(line(195, 660, 195, 560, 400), W, H, REST, BOX), null);    // 0.25 px/ms up
  assert.equal(release(line(195, 560, 195, 660, 100), W, H, REST, BOX), null);    // down
  assert.equal(release(line(100, 660, 300, 650, 100), W, H, REST, BOX), null);    // sideways
});

test("release: only the last 80 ms set the speed (a slow start then a flick)", () => {
  const slow = line(195, 700, 195, 680, 400), fast = line(195, 680, 195, 560, 96).map(q => ({ ...q, t: q.t + 400 }));
  const r = release([...slow, ...fast.slice(1)], W, H, REST, BOX);
  near(r.v, 120 / 96, 1e-9);
});

test("assist: near miss pulled half way to the centre; on the box or far off left alone", () => {
  assert.deepEqual(assist({ x: 195, y: 300 }, BOX), { x: 195, y: 384 });            // 88 px over the top -> inside
  assert.deepEqual(assist({ x: 30, y: 468 }, BOX), { x: 112.5, y: 468 });           // 85 px left -> still just off
  assert.deepEqual(assist({ x: 200, y: 400 }, BOX), { x: 200, y: 400 });            // on the box
  assert.deepEqual(assist({ x: 195, y: 220 }, BOX), { x: 195, y: 220 });            // 168 px over > one box height
  const corner = assist({ x: 115 - 120, y: 388 - 120 }, BOX);                       // 170 px off diagonally
  assert.deepEqual(corner, { x: -5, y: 268 });
});

// a straight flick from the hand: speed v px/ms, angle deg off straight up, 200 px long, samples every 12 ms
const flick = (v, deg, len = 200) => {
  const a = deg * Math.PI / 180, ms = len / v, n = Math.max(3, Math.round(ms / 12)), out = [];
  for (let i = 0; i <= n; i++) out.push({ x: REST.x + Math.sin(a) * len * i / n, y: REST.y - Math.cos(a) * len * i / n, t: 1000 + ms * i / n });
  return out;
};
const shoot = (v, deg, ring = { ...C, r: 38 }, len) => { const th = release(flick(v, deg, len), W, H, REST, BOX); return th && sweep(th, 0, 1, BOX, ring, W, H); };

test("sweep: every natural flick 1.2..3.5 px/ms within +-10 degrees hits the body", () => {
  for (const v of [1.2, 1.6, 2, 2.5, 3, 3.5]) for (const deg of [-10, -5, 0, 5, 10]) for (const len of [140, 200, 260])
    assert.ok(shoot(v, deg, undefined, len)?.onBody, `v ${v} deg ${deg} len ${len}`);
});

test("sweep: a very hard flick flies over, a wide one misses", () => {
  assert.equal(shoot(5, 0), null);
  assert.equal(shoot(2.2, 35), null);
});

test("sweep: the ring takes a well-judged speed; the assist never puts a throw in the ring", () => {
  assert.equal(shoot(2.2, 0).inRing, true);
  assert.equal(shoot(1.2, 0).inRing, false);   // body only
  assert.equal(shoot(3.5, 0).inRing, false);   // assisted onto the body, not into the ring
});

test("sweep: only u >= .6 counts, and a frame that jumps over the box still finds it", () => {
  const th = release(flick(2.2, 0), W, H, REST, BOX);
  assert.equal(sweep(th, 0, 0.59, BOX, { ...C, r: 38 }, W, H), null);
  const h = sweep(th, 0, 1, BOX, { ...C, r: 38 }, W, H);
  assert.ok(h.u >= 0.6);
  assert.deepEqual(sweep(th, 0.3, 1, BOX, { ...C, r: 38 }, W, H), h);   // one slow frame from .3 to 1
  assert.equal(sweep(th, 0, 1, null, C, W, H), null);
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

test("turnOf: degrees with sign", () => {
  near(turnOf(circle(195, 660, 30, 1, 1)), 350, 1e-6);   // 36 segments, 35 turns of 10 degrees
  near(turnOf(circle(195, 660, 30, 1, -1)), -350, 1e-6);
});

test("flight: starts at release, ends at the (assisted) landing, arcs up, shrinks; a curve bows out and comes back", () => {
  const th = { from: { x: 195, y: 600 }, to: { x: 195, y: 300 }, raw: { x: 195, y: 250 }, curve: 0 };
  const a = flight(th, 0, W, H), b = flight(th, 1, W, H), m = flight(th, 0.5, W, H);
  near(a.x, 195); near(a.y, 600); near(a.s, 1);
  near(b.x, 195); near(b.y, 300); near(b.s, END_SCALE);
  near(m.y, 450 - 0.12 * H);
  near(flight({ ...th, curve: 1 }, 0.5, W, H).x, 195 + 0.12 * W);
  near(flight({ ...th, curve: -1 }, 1, W, H).x, 195, 1e-9);
  assert.ok(FLIGHT_MS === 600);
});
