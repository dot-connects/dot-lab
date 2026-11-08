// Fracture on a grid over the candy texture (2026-11-06). Sugar glass breaks along its weakest path: a needle score,
// the carved groove, an older crack. A crack is a walker that picks, step by step, between going straight (its
// momentum, larger for a harder hit), turning into a weaker line nearby, and heading for a free edge (where the
// stress lets go). A piece falls when cracks cut a region off from the shape. Pure functions, no DOM: run
// `node fracture.js` for the self-check.

export const FR = {
  N: 128,          // grid cells across the candy
  W: 1.35,         // pull of a weak line (score per unit weakness): a groove at depth ~0.65 holds a tap
  INERTIA: 1.0,    // pull of going straight, times hit energy
  RIM: 0.05,       // pull towards the free edge while running (small: momentum carries it)
  NOISE: 0.35,
  BUDGET: 45,      // how far a tap's crack runs, in cells of uncut candy (a cell is ~0.9 mm)
  COST_WEAK: 0.6,  // a fully weak cell costs 1 - 0.6 of an uncut one: cracks run far along scores
  EXIT: 70,        // when the budget is spent the crack turns out to the rim with this much left
  PULL: [0.55, 0.88], // weakness range over which a line goes from no pull to full pull
  STAMP: 0.25,     // weakness of the pressed outline before any carving
  HOLD: 0.65,      // carve strength from which the groove stops every crack (11-08: a finished groove must be safe)
  HELD_R: 3,       // cells around a finished groove point that a crack may not step into the shape through
};

export function makeField(TEX, inShape, outlineDist) {
  const N = FR.N, cell = TEX / N, n = N * N;
  const F = { N, cell, TEX, scratch: new Float32Array(n), groove: new Float32Array(n), crack: new Uint8Array(n),
    alive: new Uint8Array(n).fill(1), inside: new Uint8Array(n), deep: new Uint8Array(n), held: new Uint8Array(n) };
  for (let j = 0; j < N; j++) for (let i = 0; i < N; i++) {
    const sx = ((i + 0.5) / N) * 2 - 1, sy = ((j + 0.5) / N) * 2 - 1, k = j * N + i;
    if (Math.hypot(sx, sy) > 1) { F.alive[k] = 0; continue; }
    if (inShape(sx, sy)) { F.inside[k] = 1; if (outlineDist(sx, sy) > 0.035) F.deep[k] = 1; }
  }
  return F;
}
const idx = (F, x, y) => { const i = Math.floor(x / F.cell), j = Math.floor(y / F.cell); return i < 0 || j < 0 || i >= F.N || j >= F.N ? -1 : j * F.N + i; };
export const weak = (F, k) => k < 0 ? 0 : F.crack[k] ? 1 : Math.max(F.scratch[k], F.groove[k]);

/** A needle score from (x0,y0) to (x1,y1) in texture px: each pass over a cell adds about 2 * amt. */
export function score(F, x0, y0, x1, y1, amt) {
  const len = Math.hypot(x1 - x0, y1 - y0), n = Math.max(1, Math.ceil(len / (F.cell / 2)));
  for (let s = 0; s < n; s++) {
    const x = x0 + (x1 - x0) * s / n, y = y0 + (y1 - y0) * s / n, k = idx(F, x, y);
    if (k < 0) continue;
    F.scratch[k] = Math.min(1, F.scratch[k] + amt);
    // the score's shoulders: a crack running close beside it still finds it
    for (const d of [-1, 1, -F.N, F.N]) if (F.scratch[k + d] !== undefined) F.scratch[k + d] = Math.min(1, F.scratch[k + d] + amt * 0.45);
  }
}
/** The outline's weakness from the carve depth of each point (strength 0..1). */
export function setGroove(F, pts, strength) {
  F.groove.fill(0); F.held.fill(0);
  const R = FR.HELD_R;
  pts.forEach(([x, y], p) => {
    const s = strength(p), w = FR.STAMP + (1 - FR.STAMP) * s, k = idx(F, x, y);
    if (k < 0) return;
    F.groove[k] = Math.max(F.groove[k], w);
    for (const d of [-1, 1, -F.N, F.N]) if (F.groove[k + d] !== undefined) F.groove[k + d] = Math.max(F.groove[k + d], w * 0.6);
    // a finished groove: the shape cells just inside it are closed to cracks
    if (s >= FR.HOLD) {
      const i0 = k % F.N, j0 = (k / F.N) | 0;
      for (let j = j0 - R; j <= j0 + R; j++) for (let i = i0 - R; i <= i0 + R; i++)
        if (i >= 0 && j >= 0 && i < F.N && j < F.N && F.inside[j * F.N + i]) F.held[j * F.N + i] = 1;
    }
  });
}
export function markCrack(F, pts) { for (const [x, y] of pts) { const k = idx(F, x, y); if (k >= 0) F.crack[k] = 1; } }

/**
 * Walk a crack from (x, y) heading ang (radians, texture px). Returns { pts, end } with end:
 * "edge" (ran off the candy or into a hole), "join" (met an older crack), "in" (got into the shape), "died",
 * "held" (stopped against a finished groove).
 */
export function walk(F, x, y, ang, { E = 1, rnd = Math.random } = {}) {
  const C = F.TEX / 2, step = F.cell, pts = [[x, y]]; let a0 = ang;
  let budget = FR.BUDGET * (0.75 + 0.25 * E) * (0.8 + 0.4 * rnd()), exit = false;
  // a line pulls only once it is cut deep enough: a light scratch or a half-carved groove does not steer a crack
  const pull = w => { const t = Math.min(1, Math.max(0, (w - FR.PULL[0]) / (FR.PULL[1] - FR.PULL[0]))); return t * t * (3 - 2 * t); };
  const sample = (a, r) => pull(weak(F, idx(F, x + Math.cos(a) * step * r, y + Math.sin(a) * step * r)));
  for (let s = 0; s < 500; s++) {
    const here = weak(F, idx(F, x, y)), span = here > 0.7 ? 1.85 : 1.05;   // on a weak line it may turn hard into it
    const Wt = exit ? 0.8 : FR.W, In = exit ? 0.3 : FR.INERTIA * E, Rim = exit ? 1.2 : FR.RIM;
    let best = -1e9, ba = ang;
    for (let d = -span; d <= span + 1e-6; d += span / 3) {
      const a = ang + d, kn = idx(F, x + Math.cos(a) * step, y + Math.sin(a) * step);
      if (kn >= 0 && F.held[kn]) continue;   // a finished groove: it runs along it or stops, never through
      const w = sample(a, 1.5) * 0.6 + sample(a, 3) * 0.4;
      const ox = x - C, oy = y - C, ol = Math.hypot(ox, oy) || 1, out = (Math.cos(a) * ox + Math.sin(a) * oy) / ol;
      // momentum: keep the current heading and drift back to the one it set off on (sugar cracks run straight-ish)
      const mom = exit ? Math.cos(d) : 0.5 * Math.cos(d) + 0.5 * Math.cos(a - a0);
      const sc = Wt * w + In * mom + Rim * out + (rnd() - 0.5) * FR.NOISE;
      if (sc > best) { best = sc; ba = a; }
    }
    if (best === -1e9) return { pts, end: "held" };
    if (here > 0.7 && !exit) a0 = ba;   // once in a line, the line is its new heading
    ang = ba; x += Math.cos(ang) * step; y += Math.sin(ang) * step; pts.push([x, y]);
    const k = idx(F, x, y);
    if (k < 0 || !F.alive[k] || Math.hypot(x - C, y - C) > C * 0.985) return { pts, end: "edge" };
    if (F.deep[k]) return { pts, end: "in", scratched: F.scratch[k] > 0.4 };
    if (F.crack[k] && s > 1) return { pts, end: "join" };
    budget -= 1 - FR.COST_WEAK * weak(F, k);
    if (budget <= 0) { if (exit) return { pts, end: "died" }; exit = true; budget = FR.EXIT; }
  }
  return { pts, end: "died" };
}

/** Regions cut off from the shape: 4-connected runs of live, uncracked cells that hold no shape cell. */
export function loosePieces(F) {
  const N = F.N, seen = new Uint8Array(N * N), out = [];
  for (let k0 = 0; k0 < N * N; k0++) {
    if (seen[k0] || !F.alive[k0] || F.crack[k0]) continue;
    const cells = [], st = [k0]; seen[k0] = 1; let shape = false;
    while (st.length) {
      const k = st.pop(); cells.push(k); if (F.inside[k]) shape = true;
      const i = k % N;
      for (const m of [i > 0 ? k - 1 : -1, i < N - 1 ? k + 1 : -1, k - N, k + N]) {
        if (m < 0 || m >= N * N || seen[m] || !F.alive[m] || F.crack[m]) continue;
        seen[m] = 1; st.push(m);
      }
    }
    if (!shape) out.push(cells);
  }
  // the crack cells along a piece's edge go with it unless they belong to the shape, so no hairline is left standing
  for (const cells of out) {
    const set = new Set(cells);
    for (const k of [...set]) for (const m of [k - 1, k + 1, k - N, k + N]) if (F.crack[m] && F.alive[m] && !F.inside[m] && !set.has(m)) { set.add(m); cells.push(m); }
  }
  return out;
}

// ---------- self-check: node fracture.js ----------
if (typeof process !== "undefined" && process.argv[1] && process.argv[1].endsWith("fracture.js")) {
  const TEX = 512, C = TEX / 2, RS = 0.6;   // a circle shape of radius 0.6
  const inShape = (sx, sy) => Math.hypot(sx, sy) < RS, outlineDist = (sx, sy) => Math.abs(Math.hypot(sx, sy) - RS);
  const pts = []; for (let a = 0; a < Math.PI * 2; a += 0.01 / RS) pts.push([C + Math.cos(a) * RS * C, C + Math.sin(a) * RS * C]);
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const trial = (depth, setup) => {
    const F = makeField(TEX, inShape, outlineDist); setGroove(F, pts, () => depth); if (setup) setup(F);
    const hx = C, hy = C - 0.8 * C;   // hit the ring at the top
    const a = walk(F, hx, hy, -Math.PI / 2 + (rnd() - 0.5) * 0.8, { rnd });
    const b = walk(F, hx, hy, Math.PI / 2 + (rnd() - 0.5) * 0.6, { rnd });
    markCrack(F, a.pts); markCrack(F, b.pts);
    return { a, b, F, pieces: loosePieces(F) };
  };
  const rate = (depth, setup, f) => { let k = 0; for (let t = 0; t < 200; t++) if (f(trial(depth, setup))) k++; return k / 200; };
  const into = r => r.a.end === "in" || r.b.end === "in";
  const deepIn = rate(0.9, null, into), shallowIn = rate(0.2, null, into), deepPiece = rate(0.9, null, r => !into(r) && r.pieces.length > 0);
  // a deep radial score from the hit point going left-down to the rim: the crack should run along it
  const radial = F => score(F, C, C - 0.8 * C, C - 0.62 * C, C - 0.62 * C - 0.5 * C, 0.5);
  const alongScore = rate(0.9, radial, r => [r.a, r.b].some(w => w.pts.filter(([x, y]) => x < C - 30 && y < C - 0.75 * C).length > 8));
  const noScore = rate(0.9, null, r => [r.a, r.b].some(w => w.pts.filter(([x, y]) => x < C - 30 && y < C - 0.75 * C).length > 8));
  console.log({ deepIn, shallowIn, deepPiece, alongScore, noScore });
  const holdIn = rate(FR.HOLD, null, into);   // a groove carved just to HOLD lets nothing through
  console.log({ holdIn });
  const ok = deepIn === 0 && holdIn === 0 && shallowIn > 0.8 && deepPiece > 0.6 && alongScore > noScore + 0.3;
  console.log(ok ? "fracture self-check ok" : "fracture self-check FAILED");
  if (!ok) process.exit(1);
}
