import test from "node:test";
import assert from "node:assert/strict";
import { grade, odds, roll, ringAt, rng } from "./catch.js";

test("grade: miss, body only, ring tiers by ring size", () => {
  assert.equal(grade({ onBody: false, inRing: false, ring: 0.4 }), null);
  assert.equal(grade({ onBody: true, inRing: false, ring: 0.4 }), "hit");
  assert.equal(grade({ onBody: true, inRing: true, ring: 0.3 }), "excellent");
  assert.equal(grade({ onBody: true, inRing: true, ring: 0.49 }), "excellent");
  assert.equal(grade({ onBody: true, inRing: true, ring: 0.5 }), "great");
  assert.equal(grade({ onBody: true, inRing: true, ring: 0.74 }), "great");
  assert.equal(grade({ onBody: true, inRing: true, ring: 0.75 }), "nice");
  assert.equal(grade({ onBody: true, inRing: true, ring: 1 }), "nice");
  // the ring circle can reach past the body: a ring hit counts as a hit
  assert.equal(grade({ onBody: false, inRing: true, ring: 0.6 }), "great");
});

const close = (a, b, e = 1e-9) => assert.ok(Math.abs(a - b) < e, `${a} != ${b}`);

test("odds: p = 1 - 0.3^M with M = ring factor x curve", () => {
  assert.deepEqual(odds({ onBody: false, inRing: false, ring: 0.5, curve: 1 }), { p: 0, critical: false });
  close(odds({ onBody: true, inRing: false, ring: 0.4, curve: 0 }).p, 0.7);                    // body only, ring size ignored
  close(odds({ onBody: true, inRing: true, ring: 1, curve: 0 }).p, 0.7);                       // nice at the biggest ring
  close(odds({ onBody: true, inRing: true, ring: 0.8, curve: 0 }).p, 1 - Math.pow(0.3, 1.2));  // nice .765
  close(odds({ onBody: true, inRing: true, ring: 0.6, curve: 0 }).p, 1 - Math.pow(0.3, 1.4));  // great .815
  close(odds({ onBody: true, inRing: true, ring: 0.4, curve: 0 }).p, 1 - Math.pow(0.3, 1.6));  // excellent .854
  close(odds({ onBody: true, inRing: false, ring: 1, curve: -1 }).p, 1 - Math.pow(0.3, 1.7));  // curve hit .871
  close(odds({ onBody: true, inRing: true, ring: 0.6, curve: 1 }).p, 1 - Math.pow(0.3, 1.4 * 1.7));   // curve great .943
});

test("odds: a ring hit under 35 % is critical and sure; at 35 % it is not", () => {
  assert.deepEqual(odds({ onBody: true, inRing: true, ring: 0.3, curve: 0 }), { p: 1, critical: true });
  assert.deepEqual(odds({ onBody: false, inRing: true, ring: 0.25, curve: 1 }), { p: 1, critical: true });
  assert.equal(odds({ onBody: true, inRing: true, ring: 0.35, curve: 0 }).critical, false);
  assert.equal(odds({ onBody: true, inRing: false, ring: 0.3, curve: 0 }).critical, false);   // a small ring missed is no critical
  assert.ok(odds({ onBody: true, inRing: true, ring: 0.35, curve: 1 }).p < 0.97);              // the best non-critical throw stays under 97 %
});

test("roll: critical = 1 wobble caught; caught takes 3 wobbles; escape 1..3", () => {
  const seq = vals => { let i = 0; return () => vals[i++]; };
  assert.deepEqual(roll(1, seq([]), true), { caught: true, shakes: 1, critical: true });
  assert.deepEqual(roll(0.8, seq([0.1])), { caught: true, shakes: 3, critical: false });
  assert.deepEqual(roll(0.8, seq([0.9, 0])), { caught: false, shakes: 1, critical: false });
  assert.deepEqual(roll(0.8, seq([0.9, 0.99])), { caught: false, shakes: 3, critical: false });
  assert.deepEqual(roll(0.5, seq([0.5, 0.5])), { caught: false, shakes: 1, critical: false });   // rnd < p is strict
  for (let k = 0; k < 200; k++) {
    const r = roll(0.75, Math.random);
    assert.ok(r.shakes >= 1 && r.shakes <= 3);
  }
});

test("ringAt: shrinks 1 -> .25 over 1.8 s ease-in, snaps back in .2 s", () => {
  assert.equal(ringAt(0), 1);
  close(ringAt(900), 1 - 0.75 * 0.25);          // half the time, a quarter of the way
  close(ringAt(1799.999), 0.25, 1e-5);
  close(ringAt(1900), 0.25 + 0.75 * 0.75);     // snap is ease-out
  close(ringAt(2000), 1);
  close(ringAt(2900), ringAt(900));
  assert.ok(ringAt(1700) < ringAt(1600) && ringAt(1600) < ringAt(1000));
});

test("rng: same seed, same sequence, in [0,1)", () => {
  const a = rng(1), b = rng(1), c = rng(2);
  const xa = [a(), a(), a()], xb = [b(), b(), b()];
  assert.deepEqual(xa, xb);
  assert.notDeepEqual(xa, [c(), c(), c()]);
  for (const x of xa) assert.ok(x >= 0 && x < 1);
});
