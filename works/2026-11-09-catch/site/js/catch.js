// Catch rules: our game's numbers, shown on the result line. Pure, tested in catch.test.mjs.

/** Throw grade: null = missed, "hit" = body only, ring tiers by the ring size when thrown (ring = radius / max, 0.3..1). */
export function grade({ onBody, inRing, ring }) {
  if (inRing) return ring < 0.5 ? "excellent" : ring < 0.75 ? "great" : "nice";
  return onBody ? "hit" : null;
}

const P = { hit: 0.5, nice: 0.65, great: 0.8, excellent: 0.95 };
/** Catch chance: the throw only, +10 %p for a curve ball, never above 95 % (5 % always stays). */
export const chance = (g, curve) => (g ? Math.min(0.95, P[g] + (curve ? 0.1 : 0)) : 0);

/** One roll: caught = three shakes; an escape shakes 1..3 times, more often the better the throw. */
export function roll(p, rnd) {
  if (rnd() < p) return { caught: true, shakes: 3 };
  return { caught: false, shakes: Math.min(3, 1 + Math.floor(rnd() * 3 * p)) };
}

export const RING_MS = 1600, RING_MIN = 0.3;
/** Ring radius as a fraction of its max: max -> 30 % -> max, a triangle wave over 1.6 s. */
export function ringAt(ms) {
  const p = (((ms % RING_MS) + RING_MS) % RING_MS) / RING_MS;
  return RING_MIN + (1 - RING_MIN) * Math.abs(1 - 2 * p);
}

/** Seeded 0..1 generator (mulberry32) for ?seed=N. */
export function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
