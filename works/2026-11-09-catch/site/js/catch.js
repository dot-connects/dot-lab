// Catch rules: our game's numbers, shown on the result line. Pure, tested in catch.test.mjs.

/** Throw grade: null = missed, "hit" = body only, ring tiers by the ring size when thrown (ring = radius / max, 0.25..1). */
export function grade({ onBody, inRing, ring }) {
  if (inRing) return ring < 0.5 ? "excellent" : ring < 0.75 ? "great" : "nice";
  return onBody ? "hit" : null;
}

export const BASE = 0.7, CURVE = 1.7, CRIT_RING = 0.35;
/**
 * Catch odds, Pokemon GO's formula shape with our own base: p = 1 - (1 - 0.7)^M, M = (in ring ? 2 - ring : 1) x (curve ? 1.7 : 1)
 * (the best non-critical throw, curve + ring at 35 %, is 96.6 %). A ring hit while the ring is under 35 % is critical: caught for sure, after one wobble.
 */
export function odds({ onBody, inRing, ring, curve }) {
  if (!onBody && !inRing) return { p: 0, critical: false };
  if (inRing && ring < CRIT_RING) return { p: 1, critical: true };
  const M = (inRing ? 2 - ring : 1) * (curve ? CURVE : 1);
  return { p: 1 - Math.pow(1 - BASE, M), critical: false };
}

/** One roll: critical = one wobble and caught; caught = three wobbles; an escape wobbles 1..3 times, more often the better the throw. */
export function roll(p, rnd, critical = false) {
  if (critical) return { caught: true, shakes: 1, critical: true };
  if (rnd() < p) return { caught: true, shakes: 3, critical: false };
  return { caught: false, shakes: Math.min(3, 1 + Math.floor(rnd() * 3 * p)), critical: false };
}

export const RING_SHRINK = 1800, RING_SNAP = 200, RING_MS = RING_SHRINK + RING_SNAP, RING_MIN = 0.25;
/** Ring radius as a fraction of its max: shrinks 1 -> 25 % over 1.8 s (ease-in: slow while big, quick when small), snaps back in 0.2 s. */
export function ringAt(ms) {
  const t = ((ms % RING_MS) + RING_MS) % RING_MS;
  if (t < RING_SHRINK) { const u = t / RING_SHRINK; return 1 - (1 - RING_MIN) * u * u; }
  const u = (t - RING_SHRINK) / RING_SNAP;
  return RING_MIN + (1 - RING_MIN) * (1 - (1 - u) * (1 - u));
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
