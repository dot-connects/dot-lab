// Dalgona rules, no DOM: carving, pressure, staged cracks, local licking. Coordinates are screen px; the candy is (cx, cy, R).
// Rule constants are ours (tuned in the 11-04 mockups and the phone spike), not measurements of real candy.

export const RULES = {
  CAM_TIME: 2,        // camera play gets twice each shape's time (hand tracking is slower than a finger)
  CAM_SPEED: 1.3,     // and a safer speed: 6 px of landmark noise at ~11 Hz already reads as 0.4 R/s (review r2)
  V_SAFE: 1.35,       // safe needle speed in candy radii per second
  TOL: 0.11,          // how close to the line the needle must be, in candy radii
  SCRAPE: 2.6,        // carve per radius of needle travel near a point (one slow lap ~0.57 -> two laps; three were dull, review r1)
  POKE: 0.42,         // carve of one poke at its centre
  POKE_STRESS: 0.16,  // pressure of one poke on the line (0.22 off the line)
  POKE_WEAK: 0.9,     // a poke on a sharp corner carves it almost through at once: corners are for poking (review r2)
  POKE_WEAK_STRESS: 0.12,
  POKE_HEAT: 0.15,    // every poke adds heat that fades slowly; pressure from a poke is multiplied by 1 + heat, so a
  HEAT_DECAY: 0.12,   // steady rhythm of pokes all the way round builds up (code review r2: 0.46 s pokes won clean)
  POKE_FAST: 0.2,     // extra pressure when pokes come less than FAST_GAP apart
  FAST_GAP: 0.45,     // s (at 0.3 a steady 0.31 s rhythm never cracked)
  POKE_CAP: 0.45,     // one poke never adds more than this (v1: one camera jab broke the candy at once)
  SPEED_CAP: 1.4,     // pressure per second from rushing, shaking or scraping the body, at most
  GRACE: 0.25,        // seconds after the needle touches down with no speed or body-scrape pressure
  DECAY: 0.5,         // pressure fades this much per second
  HAIR: 0.5,          // pressure where a hairline crack appears on the line
  GROW: 0.35,         // crack growth in radii per second per unit of pressure above HAIR
  FATAL: 0.14,        // an inward crack this many radii inside the shape breaks it
  IN_P: 0.25,         // chance a new crack runs into the shape (+0.1 per chip, +WEAK_IN at a sharp inner corner, less where licked)
  WEAK_IN: 0.4,       // scraping across a sharp corner (star points and valleys, umbrella rib tips) breaks inward more often
  WEAK_V: 0.75,       // and tolerate less speed
  SEG: 12,            // the main outline is cut in this many stretches; each finished stretch clicks
  JITTER_SAFE: 0.08,  // hand shake below this many radii (RMS) is free
  DONE: 0.9,          // every outline point carved this deep -> the shape comes out
  GRID: 28,           // wet / melt grid across the candy
  MELT_HOLES: 2,      // melted cells inside the shape that break it (3 let two holes pass as harmless)
  FINAL_DECAY: 0.35,  // over 90 % the pressure fades slower: hold your breath (the app plays a heartbeat)
  HEAL: 0.035,        // a licked hairline closes this many radii per second; licking also slows a crack's growth
};

// v: safe-speed factor per shape on touch (the umbrella is the hard one); t: seconds on touch. A slow two laps take
// about half of t; the drama gave 10 minutes.
export const SHAPES = {
  circle: { icon: "○", name: "동그라미", v: 1.1, t: 30, paths: [circle(0, 0, 0.42)] },
  // shapes keep inside ~0.8 R of the candy edge (the first mockups ran the star and umbrella off the rim)
  tri: { icon: "△", name: "세모", v: 1, t: 35, paths: scale([[[0, -0.5], [0.47, 0.32], [-0.47, 0.32], [0, -0.5]]], 0.8) },
  star: { icon: "☆", name: "별", v: 0.9, t: 40, paths: scale([star()], 0.78) },
  umb: { icon: "☂", name: "우산", v: 0.8, t: 50, paths: scale(umbrella(), 0.8, 0.04) },
};

function scale(paths, k, dy = 0) { return paths.map(p => p.map(([x, y]) => [x * k, y * k + dy])); }

function circle(cx, cy, r) {
  const a = [];
  for (let i = 0; i <= 96; i++) { const t = i / 96 * Math.PI * 2; a.push([cx + r * Math.cos(t), cy + r * Math.sin(t)]); }
  return a;
}
function star() {
  const a = [];
  for (let i = 0; i <= 10; i++) { const r = i % 2 ? 0.23 : 0.52, t = -Math.PI / 2 + i * Math.PI / 5; a.push([r * Math.cos(t), r * Math.sin(t) + 0.03]); }
  return a;
}
function umbrella() {
  const top = [];
  for (let i = 0; i <= 40; i++) { const t = Math.PI + i / 40 * Math.PI; top.push([0.5 * Math.cos(t), 0.5 * Math.sin(t) * 0.82 - 0.02]); }
  const sc = [];
  for (let k = 0; k < 4; k++) {
    const x0 = 0.5 - k * 0.25, cx = x0 - 0.125;
    for (let i = 1; i <= 10; i++) { const t = i / 10 * Math.PI; sc.push([cx + 0.125 * Math.cos(t), -0.02 - 0.08 * Math.sin(t)]); }
  }
  const handle = [[0, -0.02]];
  for (let i = 1; i <= 14; i++) handle.push([0, -0.02 + i * 0.031]);
  for (let i = 1; i <= 10; i++) { const t = i / 10 * Math.PI; handle.push([-0.07 + 0.07 * Math.cos(t), 0.414 + 0.07 * Math.sin(t)]); }
  return [[...top, ...sc], handle];
}

/** A new round. geom = {cx, cy, R} in px; rng is injectable so tests are repeatable; opts.camera = hand tracking. */
export function createGame(shape, geom, rng = Math.random, opts = {}) {
  const cam = !!opts.camera;
  const g = {
    shape, geom, rng, cam, limit: SHAPES[shape].t * (cam ? RULES.CAM_TIME : 1), vK: cam ? RULES.CAM_SPEED : SHAPES[shape].v, t: 0, started: false, stress: 0, chips: 0, cracks: [], pits: [], scratches: [],
    done: false, ok: false, cause: null, pokes: 0, heat: 0, lastPoke: -9, licks: 0, final: 0, at: null, downAt: null, segs: 0,
    wetG: new Float32Array(RULES.GRID * RULES.GRID), meltG: new Float32Array(RULES.GRID * RULES.GRID),
    events: [], last: null,
  };
  layout(g, geom);
  return g;
}

/** Re-place the outline for a new screen size, keeping carve progress (same sample counts per unit length). */
export function layout(g, geom) {
  const { cx, cy, R } = geom, old = g.geom;
  g.geom = geom;
  g.paths = SHAPES[g.shape].paths.map(path => {
    const px = path.map(p => [cx + p[0] * R * 2, cy + p[1] * R * 2]), out = [];
    for (let i = 0; i < px.length - 1; i++) {
      const a = px[i], b = px[i + 1], n = Math.max(1, Math.round(Math.hypot(b[0] - a[0], b[1] - a[1]) / (R * 0.02)));
      for (let j = 0; j < n; j++) out.push([a[0] + (b[0] - a[0]) * j / n, a[1] + (b[1] - a[1]) * j / n]);
    }
    out.push(px[px.length - 1]);
    return out;
  });
  const prev = g.prog;
  g.prog = g.paths.map((p, i) => (prev && prev[i] && prev[i].length === p.length ? prev[i] : new Float32Array(p.length)));
  g.weak = g.paths.map(weakPoints);
  // stretches: SEG along the main outline, the umbrella handle as one more
  if (!g.segOf) {
    g.segOf = g.paths.map((p, pi) => Array.from(p, (_, i) => (pi === 0 ? Math.min(RULES.SEG - 1, Math.floor(i / p.length * RULES.SEG)) : RULES.SEG + pi - 1)));
    g.segDone = new Uint8Array(RULES.SEG + g.paths.length - 1);
  }
  // marks made before a resize move with the candy
  if (old && old !== geom) {
    const k = R / old.R, mv = ([x, y]) => [cx + (x - old.cx) * k, cy + (y - old.cy) * k];
    g.pits = g.pits.map(mv);
    g.scratches = g.scratches.map(s => [...mv([s[0], s[1]]), ...mv([s[2], s[3]])]);
    for (const c of g.cracks) { c.pts = c.pts.map(mv); c.cum = c.cum.map(v => v * k); c.total *= k; c.len *= k; }
    g.at = null; g.last = null;
  }
}

// sharp corners of a closed outline (a turn over ~50 deg: star points and valleys, umbrella rib tips), with a margin
function weakPoints(path) {
  const n = path.length, k = 3, out = new Uint8Array(n);
  const closed = Math.hypot(path[0][0] - path[n - 1][0], path[0][1] - path[n - 1][1]) < 1;
  if (!closed) return out.fill(1);   // an open stroke (the umbrella handle) is thin all along: all of it is fragile
  const m = n - 1;
  for (let i = 0; i < m; i++) {
    const a = path[(i - k + m) % m], b = path[i], c = path[(i + k) % m];
    const ux = b[0] - a[0], uy = b[1] - a[1], vx = c[0] - b[0], vy = c[1] - b[1];
    const turn = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
    if (Math.abs(turn) > 0.9) for (let j = -2; j <= 2; j++) out[(i + j + m) % m] = 1;
  }
  return out;
}

function nearWeak(g, x, y) {
  const r = g.geom.R * 0.1;
  for (let pi = 0; pi < g.paths.length; pi++) { const p = g.paths[pi], w = g.weak[pi]; for (let i = 0; i < p.length; i++) if (w[i] && Math.abs(p[i][0] - x) < r && Math.abs(p[i][1] - y) < r) return true; }
  return false;
}

export function percent(g) {
  let a = 0, n = 0;
  for (const p of g.prog) for (const v of p) { a += Math.min(1, v / RULES.DONE); n++; }
  return Math.round(a / n * 100);
}

/** Inside the shape body (main outline polygon, plus the umbrella handle as a thin stroke). */
export function inShape(g, x, y) {
  const poly = g.paths[0];
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i], [xj, yj] = poly[j];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
  }
  if (inside) return true;
  if (g.paths[1]) for (const p of g.paths[1]) if (Math.hypot(p[0] - x, p[1] - y) < g.geom.R * 0.035) return true;
  return false;
}

const inCandy = (g, x, y) => Math.hypot(x - g.geom.cx, y - g.geom.cy) < g.geom.R;

function cellOf(g, x, y) {
  const { cx, cy, R } = g.geom, N = RULES.GRID, cs = 2 * R / N;
  const i = Math.floor((x - cx + R) / cs), j = Math.floor((y - cy + R) / cs);
  return i < 0 || j < 0 || i >= N || j >= N ? -1 : j * N + i;
}
export function wetAt(g, x, y) { const k = cellOf(g, x, y); return k < 0 ? 0 : Math.min(1, g.wetG[k]); }
export function cellCentre(g, k) {
  const { cx, cy, R } = g.geom, N = RULES.GRID, cs = 2 * R / N;
  return [cx - R + (k % N + 0.5) * cs, cy - R + (Math.floor(k / N) + 0.5) * cs];
}
// holes as the eye sees them: melted cells within a tongue's width of each other are one hole (design r4: two
// cells under one tongue tip melted together and ended the round on "one" hole)
function meltedInShape(g) {
  const cells = [];
  for (let k = 0; k < g.meltG.length; k++) if (g.meltG[k] >= 1) { const [x, y] = cellCentre(g, k); if (inShape(g, x, y)) cells.push([x, y]); }
  const r = g.geom.R * 0.2, seen = new Uint8Array(cells.length);
  let holes = 0;
  for (let i = 0; i < cells.length; i++) {
    if (seen[i]) continue;
    holes++; seen[i] = 1;
    const stack = [i];
    while (stack.length) { const a = cells[stack.pop()]; for (let j = 0; j < cells.length; j++) if (!seen[j] && Math.hypot(cells[j][0] - a[0], cells[j][1] - a[1]) <= r) { seen[j] = 1; stack.push(j); } }
  }
  // one hole that keeps growing (6 cells, wider than a tongue) breaks it too
  return cells.length >= 6 ? Math.max(holes, RULES.MELT_HOLES) : holes;
}

function nearestOutline(g, x, y) {
  let best = null, bd = 1e9, pi = 0;
  g.paths.forEach((path, k) => { for (const q of path) { const d = Math.hypot(q[0] - x, q[1] - y); if (d < bd) { bd = d; best = q; pi = k; } } });
  return { q: best, d: bd, pi };
}

// A new hairline on the outline nearest (x, y). It only grows while the pressure stays above HAIR (growCracks).
function spawnCrack(g, x, y, w) {
  const { cx, cy, R } = g.geom;
  const no = nearestOutline(g, x, y), [sx, sy] = no.q;
  const corner = nearWeak(g, sx, sy) && !(g.at && g.at.poke && g.t - g.at.t < 1);   // a poke's corner pass lasts a second
  const inward = g.rng() < Math.min(0.85, RULES.IN_P + 0.1 * g.chips + (corner ? RULES.WEAK_IN : 0)) * (1 - w * 0.6);
  const st = R * 0.02, pts = [[sx, sy]], cum = [0], flip = inward ? Math.PI : 0;
  let ang = Math.atan2(sy - cy, sx - cx) + flip + (g.rng() - 0.5) * 0.8, px = sx, py = sy, L = 0;
  for (let i = 0; i < 200; i++) {
    const tg = Math.atan2(py - cy, px - cx) + flip;
    ang += (g.rng() - 0.5) * 0.7 + Math.atan2(Math.sin(tg - ang), Math.cos(tg - ang)) * 0.12;
    px += Math.cos(ang) * st; py += Math.sin(ang) * st; L += st;
    pts.push([px, py]); cum.push(L);
    if (!inward && Math.hypot(px - cx, py - cy) > R) break;
    if (inward && L > R * 0.9) break;
  }
  // handle: a crack starting on the umbrella's thin handle snaps it once it spreads, whichever way it runs
  const c = { pts, cum, total: L, len: R * 0.07, inward, handle: no.pi > 0, done: false, fatal: false, healed: false, maxStage: 0, seed: Math.floor(g.rng() * 1e9) };
  g.cracks.push(c);
  g.events.push({ type: "crack", inward, crack: c });
  return c;
}

export function crackTip(c) {
  let i = c.cum.findIndex(v => v >= c.len);
  if (i < 0) i = c.pts.length - 1;
  return c.pts[i];
}

/** 0 hairline, 1 spreading, 2 wide open. */
export function crackStage(g, c) { const k = c.len / g.geom.R; return k < 0.12 ? 0 : k < 0.3 ? 1 : 2; }

// pressure above HAIR grows the crack nearest to where it is applied (a new hairline if none is close)
function growCracks(g, dt) {
  const { R } = g.geom;
  // licking a hairline closes it (review r2: licking had no upside worth its risk)
  for (const c of g.cracks) {
    if (c.done || c.healed || c.fatal || crackStage(g, c) > 0) continue;
    const w = wetAt(g, c.pts[0][0], c.pts[0][1]);
    if (w >= 0.6) { c.len -= RULES.HEAL * R * dt * w; if (c.len <= R * 0.015) { c.healed = true; c.done = true; c.len = 0; g.events.push({ type: "heal", crack: c }); } }
  }
  if (g.stress < RULES.HAIR || !g.at) return;
  const { x, y, w } = g.at;
  let near = null, nd = R * 0.35;
  for (const c of g.cracks) if (!c.done && !c.healed) { const d = Math.hypot(c.pts[0][0] - x, c.pts[0][1] - y); if (d < nd) { nd = d; near = c; } }
  if (!near) { spawnCrack(g, x, y, w); return; }
  const s0 = crackStage(g, near);
  const wr = wetAt(g, near.pts[0][0], near.pts[0][1]);
  near.len = Math.min(near.total, near.len + (g.stress - RULES.HAIR) * RULES.GROW * R * dt * (1 - 0.85 * wr));
  if (crackStage(g, near) > s0) { near.maxStage = crackStage(g, near); g.events.push({ type: "spread", crack: near }); }
  if (near.handle && crackStage(g, near) >= 1) { near.fatal = true; finish(g, false, "crack"); return; }
  if (!near.inward && near.len >= near.total) { near.done = true; g.chips++; g.events.push({ type: "chip", crack: near }); return; }
  if (near.inward) {
    const [tx, ty] = crackTip(near);
    if (inShape(g, tx, ty) && nearestOutline(g, tx, ty).d > RULES.FATAL * R) { near.fatal = true; finish(g, false, "crack"); }
  }
}

function checkSegments(g) {
  const full = new Uint8Array(g.segDone.length).fill(1);
  g.prog.forEach((pr, pi) => pr.forEach((v, i) => { if (v < RULES.DONE) full[g.segOf[pi][i]] = 0; }));
  for (let k = 0; k < full.length; k++) if (full[k] && !g.segDone[k]) { g.segDone[k] = 1; g.segs++; g.events.push({ type: "segment", k }); }
  g.final90 = percent(g) >= 90;
}

function finish(g, ok, cause) {
  if (g.done) return;
  g.done = true; g.ok = ok; g.cause = cause; g.final = ok ? 100 : percent(g);
  g.events.push({ type: ok ? "win" : "lose", cause });
}

/** One jab of the needle: deep where it lands on the line, but a pressure spike; fast jabbing stacks up. */
export function poke(g, x, y) {
  if (g.done || !inCandy(g, x, y)) return;
  g.started = true;
  const w = wetAt(g, x, y), tol = RULES.TOL * g.geom.R * 1.05, gap = g.t - g.lastPoke;
  g.lastPoke = g.t; g.pokes++;
  let hit = false;
  const corner = nearWeak(g, x, y), deep = corner ? RULES.POKE_WEAK : RULES.POKE;
  g.paths.forEach((path, pi) => {
    const pr = g.prog[pi];
    for (let i = 0; i < path.length; i++) {
      const d = Math.hypot(path[i][0] - x, path[i][1] - y);
      if (d < tol) { hit = true; pr[i] = Math.min(1, pr[i] + deep * (1 + 0.5 * w) * (1 - d / tol * 0.5)); }
    }
  });
  g.pits.push([x, y]);
  const add = (hit ? (corner ? RULES.POKE_WEAK_STRESS : RULES.POKE_STRESS) : 0.22) * (1 - 0.5 * w) + (gap < RULES.FAST_GAP ? RULES.POKE_FAST : 0);
  g.stress = Math.min(1.3, g.stress + Math.min(RULES.POKE_CAP, add * (1 + g.heat)));
  g.heat += RULES.POKE_HEAT;
  g.at = { x, y, w, poke: true, t: g.t };
  g.events.push({ type: "poke", hit, corner: hit && corner });
}

/**
 * Advance dt seconds. input = {
 *   needle: {x, y, down, held?, resume?} | null — down = held to the candy (pinched / finger on screen);
 *           held = no new camera frame (do not measure motion); resume = first frame after the hand was lost
 *   moveDt: seconds the needle's move took (camera frame interval); defaults to dt
 *   jitter: px RMS of the hand (camera only, 0 for touch)
 *   lick:   {x, y} | null         — where the tongue tip is on screen
 * }
 */
export function step(g, dt, input) {
  // the breaking crack runs through the shape once the round is lost
  for (const c of g.cracks) if (c.fatal) c.len = Math.min(c.total, c.len + g.geom.R * 2.5 * dt);
  if (g.done) return { carve: 0 };
  const { needle, jitter = 0, lick } = input;
  if (needle && needle.down && inCandy(g, needle.x, needle.y)) g.started = true;
  // licking first is a fair opening move on touch; on camera an open mouth (a laugh, a yawn) must not start the clock
  if (!g.cam && lick && inCandy(g, lick.x, lick.y)) g.started = true;
  if (!g.started) return { carve: 0 };
  g.t += dt;
  g.heat = Math.max(0, g.heat - dt * RULES.HEAT_DECAY);
  if (g.t >= g.limit) { finish(g, false, "time"); return { carve: 0 }; }
  const { R } = g.geom;

  // licking paints wetness only where the tongue is; a spot licked too long melts through
  for (let i = 0; i < g.wetG.length; i++) g.wetG[i] = Math.max(0, g.wetG[i] - dt * 0.07);
  if (lick && inCandy(g, lick.x, lick.y)) {
    const moved = g.lastLick ? Math.hypot(lick.x - g.lastLick.x, lick.y - g.lastLick.y) / R : 0;
    if (!g.lastLick) g.licks++;
    const r = R * 0.2;
    for (let k = 0; k < g.wetG.length; k++) {
      const [x, y] = cellCentre(g, k), d = Math.hypot(x - lick.x, y - lick.y);
      if (d >= r) continue;
      const f = 1 - d / r;
      // holding the tongue still is what melts: moving it spreads the wet thinner (review r1 flipped this)
      g.wetG[k] = Math.min(1.4, g.wetG[k] + dt * 1.6 * f * Math.max(0.4, 1 - moved * 8));
      if (g.wetG[k] > 1) {
        const was = g.meltG[k];
        g.meltG[k] += dt * (g.cam ? 0.3 : 0.45) * f;   // camera: time to see "녹고 있어요" and close the mouth
        // a hole that opens on the line cracks it there
        if (was < 1 && g.meltG[k] >= 1) {
          const [x, y] = cellCentre(g, k);
          if (nearestOutline(g, x, y).d < R * 0.08) spawnCrack(g, x, y, 0);
        }
      }
    }
    g.lastLick = { x: lick.x, y: lick.y };
  } else g.lastLick = null;
  if (meltedInShape(g) >= RULES.MELT_HOLES) { finish(g, false, "melt"); return { carve: 0 }; }

  let carve = 0;
  const pressure = () => {
    growCracks(g, dt);
    g.stress = Math.max(0, g.stress - dt * (g.final90 ? RULES.FINAL_DECAY : RULES.DECAY));
  };
  if (needle && needle.held) { pressure(); return { carve: g.lastCarve || 0 }; }   // same camera frame: nothing moved yet
  if (needle && needle.resume) g.last = null;                      // a hand found again does not "jump"
  if (needle && needle.down && !g.last) g.downAt = g.t;
  if (needle && needle.down && g.last) {
    const d = Math.hypot(needle.x - g.last.x, needle.y - g.last.y), v = d / Math.max(dt, input.moveDt || 0) / R;
    const w = wetAt(g, needle.x, needle.y), tol = RULES.TOL * R;
    const vSafe = RULES.V_SAFE * g.vK * (nearWeak(g, needle.x, needle.y) ? RULES.WEAK_V : 1) * (1 + w * 0.9);
    let near = false;
    g.paths.forEach((path, pi) => {
      const pr = g.prog[pi];
      for (let i = 0; i < path.length; i++) {
        const q = path[i];
        if (Math.abs(q[0] - needle.x) < tol && Math.abs(q[1] - needle.y) < tol && Math.hypot(q[0] - needle.x, q[1] - needle.y) < tol) {
          near = true;
          if (v < vSafe * 1.6) pr[i] = Math.min(1, pr[i] + (1 + w * 0.5) * RULES.SCRAPE * d / R);
        }
      }
    });
    carve = near ? Math.min(1, v / vSafe) : 0;
    const on = inCandy(g, needle.x, needle.y);
    let add = 0;
    const settled = g.t - g.downAt > RULES.GRACE;   // landing: no speed, shake or body-scrape pressure yet
    if (settled) {
      if (v > vSafe) add += (v - vSafe) * 1.6;
      const shake = jitter / R;
      if (shake > RULES.JITTER_SAFE) add += (shake - RULES.JITTER_SAFE) * 8;
    }
    if (on && !near && d > 0) {
      if (settled) add += 0.35 * Math.min(2, v);
      if (g.rng() < 0.5) g.scratches.push([g.last.x, g.last.y, needle.x, needle.y]);
    }
    // pressure over the time the move took: a 30 fps camera on a 60-120 Hz screen moves on every 2nd-4th frame
    if (on) { g.stress = Math.min(1.3, g.stress + Math.min(RULES.SPEED_CAP, add) * Math.max(dt, input.moveDt || 0)); if (add > 0 || !g.at || g.at.poke) g.at = { x: needle.x, y: needle.y, w }; }
  }
  g.last = needle && needle.down ? { x: needle.x, y: needle.y } : null;
  pressure();
  g.lastCarve = carve;
  checkSegments(g);
  if (!g.done && g.prog.every(pr => pr.every(v => v >= RULES.DONE))) finish(g, true, null);
  return { carve };
}

/** Win grade: 3 = no crack grew past a hairline and nothing fell off, 2 = no piece lost, 1 = came out anyway. 0 = lost. */
export function clean(g) { return !g.chips && !g.cracks.some(c => c.maxStage > 0); }
/** ★3 also needs the shape out within half its time (design r4: calm tracing always got ★3, no goal left). */
export function stars(g) { return !g.ok ? 0 : g.chips ? 1 : !clean(g) || g.t > g.limit * 0.5 ? 2 : 3; }

/** Funny name for the result. */
export function label(g) {
  const p = g.final;
  const open = g.cracks.filter(c => !c.healed).length;
  if (g.ok && clean(g)) return open ? "실금 하나로 버팀" : "흠집 하나 없이 통과";
  if (g.ok && g.chips) return "조각은 잃었지만 통과";
  if (g.ok) return "실금 위에서 통과";
  if (g.cause === "melt") return "혀로 다 녹임";
  if (g.cause === "time") return p >= 50 ? "시간이 모자랐음" : "바늘만 들고 있었음";
  if (p >= 85) return "한 끗 차이";
  if (open > 2) return "성격 급함";
  if (p < 25) return "시작하자마자";
  return "탈락";
}

export function reason(g) {
  const s = Math.round(g.t);
  if (g.ok) return `${s}초 · 금 ${g.cracks.filter(c => !c.healed).length}개 · 떨어진 조각 ${g.chips}개 · 찌른 횟수 ${g.pokes}`;
  if (g.cause === "melt") return `한 곳을 오래 핥아 모양이 녹았어요 · ${g.final}% 파냄`;
  if (g.cause === "time") return `${g.limit}초 안에 ${g.final}%까지`;
  if (g.cracks.some(c => c.fatal && c.handle)) return `손잡이가 끊겼어요 · ${s}초 · ${g.final}% 파냄`;
  return `금이 모양 안으로 번졌어요 · ${s}초 · ${g.final}% 파냄`;
}
