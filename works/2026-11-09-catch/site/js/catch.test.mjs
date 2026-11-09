import test from "node:test";
import assert from "node:assert/strict";
import { grade, chance, roll, ringAt, rng } from "./catch.js";

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

test("chance: table, curve bonus, capped at .95", () => {
  assert.equal(chance("hit", false), 0.5);
  assert.equal(chance("nice", false), 0.65);
  assert.equal(chance("great", false), 0.8);
  assert.equal(chance("excellent", false), 0.95);
  assert.ok(Math.abs(chance("hit", true) - 0.6) < 1e-9);
  assert.ok(Math.abs(chance("great", true) - 0.9) < 1e-9);
  assert.equal(chance("excellent", true), 0.95);
  assert.equal(chance(null, true), 0);
});

test("roll: caught takes 3 shakes, escape 1..3 shakes", () => {
  const seq = vals => { let i = 0; return () => vals[i++]; };
  assert.deepEqual(roll(0.8, seq([0.1])), { caught: true, shakes: 3 });
  assert.deepEqual(roll(0.8, seq([0.9, 0])), { caught: false, shakes: 1 });
  assert.deepEqual(roll(0.8, seq([0.9, 0.99])), { caught: false, shakes: 3 });
  assert.deepEqual(roll(0.5, seq([0.5, 0.5])), { caught: false, shakes: 1 });   // rnd < p is strict
  for (let k = 0; k < 200; k++) {
    const r = roll(0.65, Math.random);
    assert.ok(r.shakes >= 1 && r.shakes <= 3);
  }
});

test("ringAt: max -> 30% -> max over 1.6 s", () => {
  assert.equal(ringAt(0), 1);
  assert.ok(Math.abs(ringAt(800) - 0.3) < 1e-9);
  assert.ok(Math.abs(ringAt(400) - 0.65) < 1e-9);
  assert.ok(Math.abs(ringAt(1600) - 1) < 1e-9);
});

test("rng: same seed, same sequence, in [0,1)", () => {
  const a = rng(1), b = rng(1), c = rng(2);
  const xa = [a(), a(), a()], xb = [b(), b(), b()];
  assert.deepEqual(xa, xb);
  assert.notDeepEqual(xa, [c(), c(), c()]);
  for (const x of xa) assert.ok(x >= 0 && x < 1);
});
