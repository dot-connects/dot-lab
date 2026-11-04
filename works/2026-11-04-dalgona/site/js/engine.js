// Dalgona rules, no DOM: carving, pressure, cracks, local licking. Coordinates are screen px; the candy is (cx, cy, R).
// Rule constants are ours (tuned in the 11-04 mockups and the phone spike), not measurements of real candy.

export const RULES = {
  T_LIMIT: 90,        // seconds (the drama gave 10 minutes)
  V_SAFE: 1.35,       // safe needle speed in candy radii per second
  TOL: 0.11,          // how close to the line the needle must be, in candy radii
  SCRAPE: 1.75,       // carve per radius of needle travel near a point (one slow lap ~0.38 -> about three laps)
  POKE: 0.42,         // carve of one poke at its centre
  POKE_STRESS: 0.16,  // pressure of one poke on the line (0.22 off the line)
  POKE_FAST: 0.3,     // extra pressure when pokes come less than 0.3 s apart
  JITTER_SAFE: 0.08,  // hand shake below this many radii (RMS) is free
  SQUEEZE: 5,         // pinch closing speed (hand sizes/s) that counts as gripping the needle too hard
  DONE: 0.9,          // every outline point carved this deep -> the shape comes out
  GRID: 28,           // wet / melt grid across the candy
  MELT_HOLES: 3,      // melted cells inside the shape that break it
};

export const SHAPES = {
  circle: { icon: "○", name: "동그라미", paths: [circle(0, 0, 0.42)] },
  // shapes keep inside ~0.8 R of the candy edge (the first mockups ran the star and umbrella off the rim)
  tri: { icon: "△", name: "세모", paths: scale([[[0, -0.5], [0.47, 0.32], [-0.47, 0.32], [0, -0.5]]], 0.8) },
  star: { icon: "☆", name: "별", paths: scale([star()], 0.78) },
  umb: { icon: "☂", name: "우산", paths: scale(umbrella(), 0.8, 0.04) },
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

/** A new round. geom = {cx, cy, R} in px; rng is injectable so tests are repeatable. */
export function createGame(shape, geom, rng = Math.random) {
  const g = {
    shape, geom, rng, t: 0, started: false, stress: 0, chips: 0, cracks: [], pits: [], scratches: [],
    done: false, ok: false, cause: null, fastHits: 0, pokes: 0, lastPoke: -9, licks: 0, final: 0,
    wetG: new Float32Array(RULES.GRID * RULES.GRID), meltG: new Float32Array(RULES.GRID * RULES.GRID),
    events: [], last: null,
  };
  layout(g, geom);
  return g;
}

/** Re-place the outline for a new screen size, keeping carve progress (same sample counts per unit length). */
export function layout(g, geom) {
  const { cx, cy, R } = geom;
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
  const old = g.prog;
  g.prog = g.paths.map((p, i) => (old && old[i] && old[i].length === p.length ? old[i] : new Float32Array(p.length)));
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
function meltedInShape(g) {
  let n = 0;
  for (let k = 0; k < g.meltG.length; k++) if (g.meltG[k] >= 1) { const [x, y] = cellCentre(g, k); if (inShape(g, x, y)) n++; }
  return n;
}

function crack(g, x, y, w) {
  g.fastHits++;
  const pIn = Math.min(0.85, 0.22 + 0.14 * g.chips) * (1 - w * 0.6);
  const inward = g.rng() < pIn;
  const { cx, cy, R } = g.geom, toC = Math.atan2(cy - y, cx - x);
  let ang = inward ? toC + (g.rng() - 0.5) * 0.9 : toC + Math.PI + (g.rng() - 0.5) * 1.4;
  const len = inward ? R * 1.5 : R * (0.25 + g.rng() * 0.25), step = R * 0.045;
  const pts = [[x, y]];
  let px = x, py = y;
  for (let i = 0; i < Math.round(len / step); i++) {
    ang += (g.rng() - 0.5) * 0.7; px += Math.cos(ang) * step; py += Math.sin(ang) * step;
    if (Math.hypot(px - cx, py - cy) > R * 1.02) break;
    pts.push([px, py]);
  }
  g.cracks.push({ pts, grow: 0, inward });
  g.events.push({ type: "crack", inward });
  g.stress = 0.25;
  if (inward) finish(g, false, "crack"); else g.chips++;
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
  g.paths.forEach((path, pi) => {
    const pr = g.prog[pi];
    for (let i = 0; i < path.length; i++) {
      const d = Math.hypot(path[i][0] - x, path[i][1] - y);
      if (d < tol) { hit = true; pr[i] = Math.min(1, pr[i] + RULES.POKE * (1 + 0.5 * w) * (1 - d / tol * 0.5)); }
    }
  });
  g.pits.push([x, y]);
  g.stress += (hit ? RULES.POKE_STRESS : 0.22) * (1 - 0.5 * w) + (gap < 0.3 ? RULES.POKE_FAST : 0);
  g.events.push({ type: "poke", hit });
  if (g.stress >= 1) crack(g, x, y, w);
}

/** Gripping the needle too hard (fast pinch close) over the candy: a small pressure spike. */
export function squeeze(g, x, y, closeSpeed) {
  if (g.done || closeSpeed < RULES.SQUEEZE || !inCandy(g, x, y)) return;
  g.stress += 0.2;
  g.events.push({ type: "squeeze" });
}

/**
 * Advance dt seconds. input = {
 *   needle: {x, y, down, held?, resume?} | null — down = held to the candy (pinched / finger on screen);
 *           held = no new camera frame (do not measure motion); resume = first frame after the hand was lost
 *   moveDt: seconds the needle's move took (camera frame interval); defaults to dt
 *   jitter: px RMS of the hand (camera only, 0 for touch)
 *   lick:   {x, y} | null         — where the tongue/open mouth is on screen
 * }
 */
export function step(g, dt, input) {
  g.cracks.forEach(c => { c.grow = Math.min(1, c.grow + dt * (c.inward ? 2.2 : 5)); });
  if (g.done) return { carve: 0 };
  const { needle, jitter = 0, lick } = input;
  if (needle && needle.down && inCandy(g, needle.x, needle.y)) g.started = true;
  if (!g.started) return { carve: 0 };
  g.t += dt;
  if (g.t >= RULES.T_LIMIT) { finish(g, false, "time"); return { carve: 0 }; }
  const { R } = g.geom;

  // licking paints wetness only where the mouth is; a spot licked too long melts through
  for (let i = 0; i < g.wetG.length; i++) g.wetG[i] = Math.max(0, g.wetG[i] - dt * 0.07);
  if (lick && inCandy(g, lick.x, lick.y)) {
    const moved = g.lastLick ? Math.hypot(lick.x - g.lastLick.x, lick.y - g.lastLick.y) / R : 0;
    if (!g.lastLick) g.licks++;
    const N = RULES.GRID, cs = 2 * R / N, r = R * 0.2;
    for (let k = 0; k < g.wetG.length; k++) {
      const [x, y] = cellCentre(g, k), d = Math.hypot(x - lick.x, y - lick.y);
      if (d >= r) continue;
      const f = 1 - d / r;
      g.wetG[k] = Math.min(1.4, g.wetG[k] + dt * 1.6 * f * (1 + moved * 12));
      if (g.wetG[k] > 1) g.meltG[k] += dt * 0.45 * f;
    }
    void cs;
    g.lastLick = { x: lick.x, y: lick.y };
  } else g.lastLick = null;
  if (meltedInShape(g) >= RULES.MELT_HOLES) { finish(g, false, "melt"); return { carve: 0 }; }

  let carve = 0;
  if (needle && needle.held) return { carve: g.lastCarve || 0 };   // same camera frame: nothing moved yet
  if (needle && needle.resume) g.last = null;                      // a hand found again does not "jump"
  if (needle && needle.down && g.last) {
    const d = Math.hypot(needle.x - g.last.x, needle.y - g.last.y), v = d / Math.max(dt, input.moveDt || 0) / R;
    const w = wetAt(g, needle.x, needle.y), vSafe = RULES.V_SAFE * (1 + w * 0.9), tol = RULES.TOL * R;
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
    if (v > vSafe) g.stress += (v - vSafe) * dt * 1.6;
    const shake = jitter / R;
    if (shake > RULES.JITTER_SAFE) g.stress += (shake - RULES.JITTER_SAFE) * dt * 8;
    const on = inCandy(g, needle.x, needle.y);
    if (on && !near && d > 0) {
      g.stress += dt * 0.35 * Math.min(2, v);
      if (g.rng() < 0.5) g.scratches.push([g.last.x, g.last.y, needle.x, needle.y]);
    }
    if (g.stress >= 1 && on) crack(g, needle.x, needle.y, w);
  }
  g.last = needle && needle.down ? { x: needle.x, y: needle.y } : null;
  g.stress = Math.max(0, g.stress - dt * 0.5);
  g.lastCarve = carve;
  if (!g.done && g.prog.every(pr => pr.every(v => v >= RULES.DONE))) finish(g, true, null);
  return { carve };
}

/** Funny name for the result. */
export function label(g) {
  const p = g.final;
  if (g.ok) return "통과. 456번 생존";
  if (g.cause === "melt") return "혀로 다 녹임";
  if (g.cause === "time") return p > 80 ? "시간이 모자랐음" : "바늘만 들고 있었음";
  if (g.fastHits > 2) return "성격 급함";
  if (p >= 85) return "한 끗 차이";
  if (p < 25) return "시작하자마자";
  return "탈락";
}

export function reason(g) {
  const s = Math.round(g.t);
  if (g.ok) return `${s}초 · 떨어진 조각 ${g.chips}개 · 찌른 횟수 ${g.pokes}`;
  if (g.cause === "melt") return `한 곳을 오래 핥아 모양이 녹았어요 · ${g.final}% 파냄`;
  if (g.cause === "time") return `90초 안에 ${g.final}%까지 · 원작은 10분`;
  return `금이 모양 안으로 번졌어요 · ${s}초 · ${g.final}% 파냄`;
}
