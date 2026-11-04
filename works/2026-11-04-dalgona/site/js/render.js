// Candy look: pre-rendered dry and wet textures, carved groove, pits, scratches, staged cracks, chips that fall off,
// licked gloss, melted holes you can see through, drips, and the win / lose break. Reads engine state, never writes it.
// Drawing is in candy-local px (centre 0,0); layers are K x oversampled.
import { RULES, cellCentre, crackStage } from "./engine.js";

const TAU = Math.PI * 2;
// every offscreen canvas of a view is tracked so dispose() can hand the memory back at once (iOS caps total canvas
// memory; a big iPad view plus win pieces reached ~300 MB before the GC caught up: iOS review r6)
let made = null;
const off = (w, h) => { const c = document.createElement("canvas"); c.width = Math.max(1, w); c.height = Math.max(1, h); if (made) made.push(c); return c; };
function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
const clamp = v => Math.min(1, Math.max(0, v));

export function createCandyView(game, dprIn) {
  const mine = []; made = mine;
  const { R } = game.geom, S = Math.ceil(2.5 * R), K = Math.min(2, dprIn || 1, 1100 / S), HALF = S / 2, PX = Math.ceil(S * K);
  const toC = g => { g.setTransform(K, 0, 0, K, K * HALF, K * HALF); return g; };
  const loc = p => [p[0] - game.geom.cx, p[1] - game.geom.cy];
  const r0 = rng(456);
  const ph = [r0() * 6, r0() * 6, r0() * 6, r0() * 6];
  const edgeR = t => R * (1 + 0.024 * Math.sin(3 * t + ph[0]) + 0.016 * Math.sin(7 * t + ph[1]) + 0.009 * Math.sin(13 * t + ph[2]) + 0.005 * Math.sin(31 * t + ph[3]));
  const edge = new Path2D();
  for (let i = 0; i <= 240; i++) { const t = i / 240 * TAU, r = edgeR(t); i ? edge.lineTo(r * Math.cos(t), r * Math.sin(t)) : edge.moveTo(r, 0); }
  edge.closePath();
  const inCandy = (x, y) => Math.hypot(x, y) < edgeR(Math.atan2(y, x));

  // shape outline (the groove) and the region that comes out on a win
  const lines = game.paths.map(p => { const q = new Path2D(); p.map(loc).forEach(([x, y], i) => (i ? q.lineTo(x, y) : q.moveTo(x, y))); return q; });
  const body = new Path2D();
  game.paths[0].map(loc).forEach(([x, y], i) => (i ? body.lineTo(x, y) : body.moveTo(x, y)));
  body.closePath();
  if (game.paths[1]) for (const [x, y] of game.paths[1].map(loc)) { body.moveTo(x + R * 0.035, y); body.arc(x, y, R * 0.035, 0, TAU); }

  // ---------- textures (rendered once) ----------
  // Glassy toffee, not a cookie (review r1): light translucent bubbles over a warm gradient, a broad gloss band,
  // a shallow bevelled mould dent, a darker caramelised rim with a thin rim light.
  function texture(wet) {
    const c = off(PX, PX), g = toC(c.getContext("2d")), r = rng(77), ps = R / 142;
    g.save(); g.clip(edge);
    const base = g.createRadialGradient(-0.2 * R, -0.22 * R, 0, 0, 0, R * 1.05);
    if (!wet) { base.addColorStop(0, "#f4c878"); base.addColorStop(0.6, "#e0a04a"); base.addColorStop(0.9, "#b8661f"); base.addColorStop(1, "#7a3a10"); }
    else { base.addColorStop(0, "#b56a24"); base.addColorStop(0.6, "#8a4412"); base.addColorStop(1, "#3e1a05"); }
    g.fillStyle = base; g.fillRect(-HALF, -HALF, S, S);
    // caramel blotches, darker toward the rim
    for (let i = 0; i < 220; i++) {
      const t = r() * TAU, d = Math.sqrt(r()) * R, x = d * Math.cos(t), y = d * Math.sin(t), rr = R * (0.04 + r() * 0.15);
      const gr = g.createRadialGradient(x, y, 0, x, y, rr), dark = r() < 0.35 + 0.55 * (d / R);
      gr.addColorStop(0, dark ? "rgba(120,55,12,.14)" : "rgba(255,220,150,.12)"); gr.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = gr; g.fillRect(x - rr, y - rr, rr * 2, rr * 2);
    }
    // micro bubbles
    for (let i = 0; i < 4000; i++) {
      const t = r() * TAU, d = Math.sqrt(r()) * R, x = d * Math.cos(t), y = d * Math.sin(t);
      g.fillStyle = r() < 0.5 ? `rgba(255,236,190,${0.08 + 0.1 * r()})` : `rgba(110,48,10,${0.06 + 0.08 * r()})`;
      g.fillRect(x, y, ps, ps);
    }
    // bubbles: light and see-through, dark lip on the upper left, bright lip on the lower right
    const n = wet ? 90 : 600;   // a wet surface is smooth
    for (let i = 0; i < n; i++) {
      const t = r() * TAU, d = Math.sqrt(r()) * R * 0.97, x = d * Math.cos(t), y = d * Math.sin(t);
      const s = ps * (0.8 + 4 * Math.pow(r(), 2.6)), e = 0.75 + 0.3 * r(), rot = r() * 3;
      g.fillStyle = wet ? "rgba(255,220,160,.12)" : "rgba(255,222,160,.22)";
      g.beginPath(); g.ellipse(x, y, s, s * e, rot, 0, TAU); g.fill();
      if (s > 1.2 * ps) {
        g.lineWidth = 0.6;
        g.strokeStyle = "rgba(110,50,12,.35)"; g.beginPath(); g.arc(x, y, s, Math.PI * 0.9, Math.PI * 1.6); g.stroke();
        g.strokeStyle = "rgba(255,245,215,.5)"; g.beginPath(); g.arc(x, y, s, -0.2, 1.4); g.stroke();
      }
    }
    // caramelised rim: translucent darker ring
    g.strokeStyle = wet ? "rgba(70,28,6,.5)" : "rgba(160,80,20,.5)"; g.lineWidth = 0.1 * R; g.stroke(edge);
    g.strokeStyle = "rgba(84,36,8,.4)"; g.lineWidth = 0.05 * R; g.stroke(edge);
    // mould dent: a shallow bevel, dark on the upper left wall, light on the lower right
    g.lineJoin = "round"; g.lineCap = "round";
    for (const [w, ad, al] of [[0.06, 0.08, 0.06], [0.035, 0.12, 0.1], [0.015, 0.3, 0.35]]) {
      g.save(); g.translate(-ps, -ps); g.strokeStyle = `rgba(90,36,6,${ad})`; g.lineWidth = w * R; lines.forEach(l => g.stroke(l)); g.restore();
      g.save(); g.translate(1.2 * ps, 1.2 * ps); g.strokeStyle = `rgba(255,232,180,${al})`; g.lineWidth = w * R * 0.8; lines.forEach(l => g.stroke(l)); g.restore();
    }
    g.globalCompositeOperation = "screen";
    // broad gloss band, like light on a smooth glassy top
    g.save(); g.rotate(-0.61);
    const band = g.createRadialGradient(0, -0.38 * R, 0, 0, -0.38 * R, 0.9 * R);
    band.addColorStop(0, wet ? "rgba(255,248,225,.4)" : "rgba(255,248,225,.24)"); band.addColorStop(1, "rgba(255,248,225,0)");
    g.scale(1, 0.25); g.fillStyle = band; g.fillRect(-R * 1.2, -R * 5, R * 2.4, R * 8); g.restore();
    const sp = g.createRadialGradient(-0.38 * R, -0.42 * R, 0, -0.38 * R, -0.42 * R, 0.45 * R);
    sp.addColorStop(0, wet ? "rgba(255,236,190,.5)" : "rgba(255,236,190,.18)"); sp.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = sp; g.fillRect(-HALF, -HALF, S, S);
    if (wet) for (let i = 0; i < 40; i++) {   // wet streaks, one light direction
      const t = r() * TAU, d = Math.sqrt(r()) * R * 0.9, x = d * Math.cos(t), y = d * Math.sin(t);
      g.fillStyle = `rgba(255,250,235,${0.5 + 0.4 * r()})`;
      g.beginPath(); g.ellipse(x, y, ps * (6 + 8 * r()), ps * (0.8 + 0.8 * r()), -0.6, 0, TAU); g.fill();
    }
    g.globalCompositeOperation = "source-over";
    // rim light on the upper left edge
    g.strokeStyle = "rgba(255,240,210,.6)"; g.lineWidth = ps;
    g.beginPath(); for (let i = 0; i <= 40; i++) { const t = -Math.PI * 0.83 + i / 40 * Math.PI * 0.5, rr = edgeR(t) - 1.5 * ps; i ? g.lineTo(rr * Math.cos(t), rr * Math.sin(t)) : g.moveTo(rr * Math.cos(t), rr * Math.sin(t)); } g.stroke();
    g.restore();
    return c;
  }
  const DRY = texture(false), WET = texture(true);
  // two shadows: a tight contact one and a wide soft one; holes and chips are cut out of them (SHc)
  const SH = off(PX, PX), SHc = off(PX, PX);
  {
    const g = toC(SH.getContext("2d"));
    g.save(); g.shadowColor = "rgba(0,0,0,.3)"; g.shadowBlur = R * 0.2 * K; g.shadowOffsetX = 4000 * K; g.shadowOffsetY = R * 0.06 * K; g.translate(-4000, 0); g.fill(edge); g.restore();
    g.save(); g.shadowColor = "rgba(0,0,0,.6)"; g.shadowBlur = R * 0.02 * K; g.shadowOffsetX = 4000 * K; g.shadowOffsetY = R * 0.012 * K; g.translate(-4000, 0); g.fill(edge); g.restore();
  }
  SHc.getContext("2d").drawImage(SH, 0, 0);
  let shadowDirty = false;
  const damage = off(PX, PX), dg = toC(damage.getContext("2d"));
  const L = off(PX, PX), lg = L.getContext("2d");
  const B = off(PX, PX), bg = B.getContext("2d");
  const mWet = off(PX, PX), mRim = off(PX, PX), mHole = off(PX, PX), mWetEdge = off(PX, PX), mCore = off(PX, PX);

  // ---------- wet / melt masks from the engine grid ----------
  const N = RULES.GRID, cell = 2 * R / N;
  let maskFrame = 0, anyWet = false, anyMelt = false;
  // soft round sprites drawn with globalAlpha: one gradient each, made once (perf: hundreds of gradients per
  // mask update stalled low-end phones for ~200 ms)
  const sprite = (stops) => {
    const c = off(64, 64), g = c.getContext("2d"), gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    stops.forEach(([o, a]) => gr.addColorStop(o, `rgba(255,255,255,${a})`)); g.fillStyle = gr; g.fillRect(0, 0, 64, 64); return c;
  };
  const BLOB = sprite([[0, 1], [0.55, 0.7], [1, 0]]), RIM = sprite([[0, 1], [0.5, 1], [1, 0]]);
  function blobs(c, val) {
    const g = c.getContext("2d"); g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, c.width, c.height); toC(g);
    let any = false;
    for (let k = 0; k < N * N; k++) {
      const a = val(k); if (a < 0.02) continue;
      any = true;
      const [x, y] = loc(cellCentre(game, k)), r = cell * 1.3;
      g.globalAlpha = a; g.drawImage(BLOB, x - r, y - r, 2 * r, 2 * r);
    }
    g.globalAlpha = 1;
    return any;
  }
  // melted holes: a union of solid circles sized by how far each cell melted, jittered per cell, so the hole is
  // round and ragged instead of following the square grid
  const jit = Array.from({ length: N * N }, (_, k) => { const r = rng(k * 7 + 3); return [r() - 0.5, r() - 0.5, 0.8 + 0.4 * r()]; });
  function holes(c, grow, soft) {
    const g = c.getContext("2d"); g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, c.width, c.height); toC(g);
    let any = false;
    for (let k = 0; k < N * N; k++) {
      const m = game.meltG[k] - 0.45; if (m <= 0) continue;
      any = true;
      const [jx, jy, js] = jit[k], [x0, y0] = loc(cellCentre(game, k)), x = x0 + jx * cell * 0.3, y = y0 + jy * cell * 0.3;
      const r = cell * (0.35 + 0.85 * Math.min(1, m / 0.8)) * js * 1.25 + grow;
      if (soft) g.drawImage(RIM, x - r, y - r, 2 * r, 2 * r);
      else { g.fillStyle = "#fff"; g.beginPath(); g.arc(x, y, r, 0, TAU); g.fill(); }
    }
    return any;
  }
  let meltSum = 0, wetSeen = true;
  function masks() {
    const wetNow = game.wetG.some(v => v > 0.02);
    if (!wetNow && !wetSeen) return;   // nothing licked: no mask work at all
    wetSeen = wetNow;
    anyWet = blobs(mWet, k => clamp(game.wetG[k] * 0.9));
    if (anyWet) {   // the raised edge of the wet film catches light on its upper left
      const e = mWetEdge.getContext("2d"); e.setTransform(1, 0, 0, 1, 0, 0); e.globalCompositeOperation = "source-over";
      e.clearRect(0, 0, PX, PX); e.drawImage(mWet, 0, 0);
      e.globalCompositeOperation = "destination-out"; e.drawImage(mWet, 1.5 * K, 1.5 * K);
      e.globalCompositeOperation = "source-in"; e.fillStyle = "rgba(255,245,220,.55)"; e.fillRect(0, 0, PX, PX);
      e.globalCompositeOperation = "source-over";
    }
    anyMelt = game.meltG.some(m => m > 0.45);
    if (anyMelt) {
      holes(mRim, cell * 1.4, true); holes(mHole, 0, false); holes(mCore, -cell * 0.35, false);
      const sum = game.meltG.reduce((a, m) => a + Math.max(0, m - 0.45), 0);
      if (Math.abs(sum - meltSum) > 0.05) { meltSum = sum; shadowDirty = true; }
    }
  }
  // no shadow under a hole or where a chip fell off
  function rebuildShadow() {
    const g = SHc.getContext("2d"); g.setTransform(1, 0, 0, 1, 0, 0); g.globalCompositeOperation = "source-over";
    g.clearRect(0, 0, PX, PX); g.drawImage(SH, 0, 0);
    g.globalCompositeOperation = "destination-out";
    const ox = R * 0.03 * K, oy = R * 0.08 * K;   // the shadow is drawn offset; cut where the candy is missing above it
    if (anyMelt) g.drawImage(mHole, -ox, -oy);
    if (chips.length) { g.setTransform(K, 0, 0, K, K * HALF - ox, K * HALF - oy); chips.forEach(c => g.fill(c)); }
    g.globalCompositeOperation = "source-over"; shadowDirty = false;
  }

  // ---------- marks: pits and scratches are drawn once, as they happen ----------
  let pitsDrawn = 0, scratchesDrawn = 0;
  const mr = rng(9);
  function marks() {
    dg.save(); dg.clip(edge); dg.lineCap = "round";
    for (; scratchesDrawn < game.scratches.length; scratchesDrawn++) {
      const s = game.scratches[scratchesDrawn], [x0, y0] = loc([s[0], s[1]]), [x1, y1] = loc([s[2], s[3]]);
      dg.strokeStyle = "rgba(255,238,205,.32)"; dg.lineWidth = 1.1; dg.beginPath(); dg.moveTo(x0, y0); dg.lineTo(x1, y1); dg.stroke();
      for (let i = 0; i < 2; i++) { dg.fillStyle = "rgba(255,240,215,.5)"; dg.fillRect(x1 + (mr() - 0.5) * 6, y1 + (mr() - 0.5) * 6, 1, 1); }
    }
    for (; pitsDrawn < game.pits.length; pitsDrawn++) {
      const [x, y] = loc(game.pits[pitsDrawn]), pr = Math.max(1.6, R * 0.013);
      dg.fillStyle = "rgba(50,18,2,.75)"; dg.beginPath(); dg.arc(x, y, pr, 0, TAU); dg.fill();
      dg.strokeStyle = "rgba(255,225,170,.55)"; dg.lineWidth = 0.8; dg.beginPath(); dg.arc(x + 0.6, y + 0.6, pr * 1.45, 0.2, 2.2); dg.stroke();
      for (let i = 0; i < 6; i++) { const a = mr() * TAU, d = pr * (1.7 + mr() * 2.8); dg.fillStyle = "rgba(255,236,200,.55)"; dg.fillRect(x + d * Math.cos(a), y + d * Math.sin(a), 1.2, 1.2); }
    }
    dg.restore();
  }

  // ---------- carved groove from the engine's per-sample progress ----------
  // cached: redrawn only when the progress changed (code review r2: ~550 strokes a frame on the umbrella)
  const CV = off(PX, PX), cvg = toC(CV.getContext("2d"));
  let carveSum = -1;
  function carvedLayer() {
    let sum = 0; for (const pr of game.prog) for (const v of pr) sum += v;
    if (sum !== carveSum) { carveSum = sum; cvg.setTransform(1, 0, 0, 1, 0, 0); cvg.clearRect(0, 0, PX, PX); toC(cvg); carved(cvg); }
    return CV;
  }
  function carved(g) {
    g.save(); g.clip(edge); g.lineCap = "round";
    game.paths.forEach((path, pi) => {
      const pr = game.prog[pi];
      for (let i = 1; i < path.length; i++) {
        const c = Math.min(1, Math.min(pr[i], pr[i - 1]) / RULES.DONE);
        if (c < 0.05) continue;
        const [ax, ay] = loc(path[i - 1]), [bx, by] = loc(path[i]);
        g.strokeStyle = `rgba(52,18,2,${0.3 + 0.6 * c})`; g.lineWidth = R * (0.008 + 0.02 * c); g.beginPath(); g.moveTo(ax, ay); g.lineTo(bx, by); g.stroke();
        if (c > 0.5) { g.strokeStyle = `rgba(255,240,210,${0.35 * c})`; g.lineWidth = 0.8; g.beginPath(); g.moveTo(ax + 1.3, ay + 1.3); g.lineTo(bx + 1.3, by + 1.3); g.stroke(); }
      }
    });
    g.restore();
  }

  // ---------- cracks ----------
  const branches = new Map();
  function branchesOf(c) {
    let b = branches.get(c);
    if (b) return b;
    const r = rng(c.seed), pts = c.pts.map(loc);
    b = [];
    for (let k = 0; k < 2 && pts.length > 10; k++) {
      const at = 6 + ((r() * (pts.length - 8)) | 0), [ax, ay] = pts[at], [px, py] = pts[at - 1];
      let ba = Math.atan2(ay - py, ax - px) + (r() < 0.5 ? -1 : 1) * (0.5 + r() * 0.5), bx = ax, by = ay;
      const bp = [[bx, by]];
      for (let i = 0; i < 5 + r() * 8; i++) { ba += (r() - 0.5) * 0.6; bx += R * 0.02 * Math.cos(ba); by += R * 0.02 * Math.sin(ba); bp.push([bx, by]); }
      b.push({ at: c.cum[at], pts: bp });
    }
    branches.set(c, b);
    return b;
  }
  function crackPath(g, pts, cum, upto) {
    g.beginPath(); g.moveTo(pts[0][0], pts[0][1]);
    for (let i = 1; i < pts.length && cum[i] <= upto; i++) g.lineTo(pts[i][0], pts[i][1]);
  }
  // toffee cracks catch light: a bright refracting band with a hair-thin dark core and a soft drop on one side
  const tipLen = new Map(), locPts = new WeakMap();
  function drawCrack(g, c, now) {
    const st = c.fatal ? 2 : crackStage(game, c), sc = R / 142, w = [1.6, 2.1, 3][st] * sc;
    let pts = locPts.get(c); if (!pts) { pts = c.pts.map(loc); locPts.set(c, pts); }
    const stroke = (pp, cum, upto, ww) => {
      // a step in the surface: lit on one side, shaded on the other, a dark gap between
      g.save(); g.translate(-sc, -sc); g.globalCompositeOperation = "screen"; g.strokeStyle = "rgba(255,236,190,.45)"; g.lineWidth = ww * 1.2; crackPath(g, pp, cum, upto); g.stroke(); g.restore();
      g.save(); g.translate(sc, sc); g.strokeStyle = "rgba(40,12,0,.35)"; g.lineWidth = ww * 0.8; crackPath(g, pp, cum, upto); g.stroke(); g.restore();
      g.strokeStyle = "rgba(35,10,0,.9)"; g.lineWidth = Math.max(0.6, ww * (st ? 0.45 : 0.35)); crackPath(g, pp, cum, upto); g.stroke();
    };
    g.lineJoin = "round"; g.lineCap = "round";
    stroke(pts, c.cum, c.len, w);
    if (st >= 1) for (const b of branchesOf(c)) if (c.len > b.at) {
      const bc = b.pts.map((_, i) => i * R * 0.02), upto = Math.min((c.len - b.at) * 0.6, bc[bc.length - 1]);
      stroke(b.pts, bc, upto, w * 0.6);
    }
    // the tip glints while it is still running
    const prev = tipLen.get(c) || [0, -1];
    if (c.len > prev[0] + 0.01) tipLen.set(c, [c.len, now]);
    const age = (now - (tipLen.get(c) || prev)[1]) / 1000;
    if (!c.done && age < 0.3) {
      let i = c.cum.findIndex(v => v >= c.len); if (i < 0) i = pts.length - 1;
      const [x, y] = pts[i], a = 1 - age / 0.3;
      g.save(); g.globalCompositeOperation = "lighter";
      const sg = g.createRadialGradient(x, y, 0, x, y, 4 * sc); sg.addColorStop(0, `rgba(255,245,220,${a})`); sg.addColorStop(1, "rgba(255,245,220,0)");
      g.fillStyle = sg; g.fillRect(x - 4 * sc, y - 4 * sc, 8 * sc, 8 * sc); g.restore();
    }
  }

  // ---------- chips and pieces ----------
  const chips = [], pieces = [], pending = [];
  function chipFrom(c) {
    const pts = c.pts.map(loc), [ex, ey] = pts[pts.length - 1], te = Math.atan2(ey, ex), r = rng(c.seed + 1);
    const dir = r() < 0.5 ? 1 : -1, span = 0.22 + r() * 0.1;
    const k0 = Math.max(0, c.cum.findIndex(v => v >= c.total - 0.2 * R)), seg = pts.slice(k0);
    const p = new Path2D(); p.moveTo(seg[0][0], seg[0][1]); seg.forEach(([x, y]) => p.lineTo(x, y));
    for (let i = 0; i <= 12; i++) { const t = te + dir * span * i / 12, rr = edgeR(t) + 3; p.lineTo(rr * Math.cos(t), rr * Math.sin(t)); }
    p.closePath();
    const tm = te + dir * span / 2;
    pending.push({ path: p, x: 0.92 * R * Math.cos(tm), y: 0.92 * R * Math.sin(tm), dir: tm, win: false });
  }
  function piecesFromPending() {
    for (const pc of pending.splice(0)) {
      const c = off(PX, PX), g = toC(c.getContext("2d"));
      g.save(); g.clip(pc.path); g.setTransform(1, 0, 0, 1, 0, 0); g.drawImage(L, 0, 0); g.restore();
      if (pc.win) { toC(g); g.globalCompositeOperation = "destination-out"; g.fill(body); g.globalCompositeOperation = "source-over"; }
      const sp = pc.win ? 90 + Math.random() * 60 : 40;
      pieces.push({ c, x: 0, y: 0, vx: Math.cos(pc.dir) * sp, vy: Math.sin(pc.dir) * sp * 0.6 - (pc.win ? 140 : 60), rot: 0, vr: (Math.random() - 0.5) * (pc.win ? 3 : 4), t: 0, life: pc.win ? 1.4 : 1.6, ox: pc.x, oy: pc.y });
      if (!pc.win) { chips.push(pc.path); shadowDirty = true; }
    }
  }
  let won = false, wonT = 0, lostT = -1, halves = null;
  function split() {
    const c = game.cracks.find(k => k.fatal); if (!c) return;
    const pts = c.pts.map(loc), [ax, ay] = pts[0], [bx, by] = pts[pts.length - 1];
    let dx = bx - ax, dy = by - ay; const dl = Math.hypot(dx, dy) || 1; dx /= dl; dy /= dl;
    const nx = -dy, ny = dx, F = R * 3;
    halves = [1, -1].map(side => {
      // the crack itself is the cut on the near side, a straight run on through the far side
      const p = new Path2D(); p.moveTo(ax - dx * F, ay - dy * F); pts.forEach(([x, y]) => p.lineTo(x, y));
      p.lineTo(bx + dx * F, by + dy * F); p.lineTo(bx + dx * F + nx * F * side, by + dy * F + ny * F * side);
      p.lineTo(ax - dx * F + nx * F * side, ay - dy * F + ny * F * side); p.closePath();
      const cnv = off(PX, PX), g = toC(cnv.getContext("2d")); g.clip(p); g.setTransform(1, 0, 0, 1, 0, 0); g.drawImage(L, 0, 0);
      return { c: cnv, nx: nx * side, ny: ny * side, rot: 0.04 * side };
    });
  }
  function breakOut() {
    // the body around the shape snaps into wedges that fall away; the shape stays
    const n = 7, r = rng(game.cracks.length + 31), a0 = r() * TAU;
    const cuts = Array.from({ length: n + 1 }, (_, i) => a0 + (i + (i % n ? (r() - 0.5) * 0.4 : 0)) / n * TAU);
    const kink = cuts.map(() => [0.3, 0.6, 0.8].map(() => (r() - 0.5) * 0.15));
    const ray = (i, p, rev) => {   // a crooked break line from the centre out
      const pts = [[0, 0], ...[0.3, 0.6, 0.8].map((f, j) => { const t = cuts[i] + kink[i % n][j]; return [f * R * Math.cos(t), f * R * Math.sin(t)]; }), [1.25 * R * Math.cos(cuts[i]), 1.25 * R * Math.sin(cuts[i])]];
      (rev ? pts.reverse() : pts).forEach(([x, y]) => p.lineTo(x, y));
    };
    for (let i = 0; i < n; i++) {
      const a = cuts[i], b = cuts[i + 1] + 0.002, p = new Path2D();
      p.moveTo(0, 0); ray(i, p, false);
      for (let k = 1; k < 10; k++) { const t = a + (b - a) * k / 10; p.lineTo(1.25 * R * Math.cos(t), 1.25 * R * Math.sin(t)); }
      ray(i + 1, p, true);
      p.closePath();
      const m = (a + b) / 2;
      pending.push({ path: p, x: 0.75 * R * Math.cos(m), y: 0.75 * R * Math.sin(m), dir: m, win: true });
    }
    piecesFromPending();
    won = true;
  }

  // ---------- drips ----------
  const drips = [];
  function updateDrips(dt) {
    if (drips.length < 6) for (let k = 0; k < N * N; k++) if (game.wetG[k] > 1.1 && Math.random() < dt * 0.35) {
      const [x, y] = loc(cellCentre(game, k));
      drips.push({ x: x + (Math.random() - 0.5) * 6, y, vy: 6, len: 3, a: 1, off: false });
      if (drips.length >= 6) break;
    }
    for (const d of drips) {
      if (inCandy(d.x, d.y) && !d.off) { d.vy = Math.min(26, d.vy + 20 * dt); d.len = Math.min(10, d.len + 6 * dt); }
      else { d.off = true; d.vy += 700 * dt; d.a -= dt * 1.4; }
      d.y += d.vy * dt;
    }
    for (let i = drips.length - 1; i >= 0; i--) if (drips[i].a <= 0) drips.splice(i, 1);
  }

  // ---------- compose the candy layer ----------
  function compose() {
    if (maskFrame++ % 3 === 0) masks();
    marks();
    lg.globalCompositeOperation = "source-over"; lg.setTransform(1, 0, 0, 1, 0, 0); lg.clearRect(0, 0, PX, PX);
    toC(lg); lg.save(); lg.translate(0, R * 0.03);   // candy thickness
    const th = lg.createLinearGradient(0, -R, 0, R); th.addColorStop(0, "#7a3a10"); th.addColorStop(1, "#3a1805"); lg.fillStyle = th; lg.fill(edge); lg.restore();
    lg.setTransform(1, 0, 0, 1, 0, 0); lg.drawImage(DRY, 0, 0); lg.drawImage(damage, 0, 0);
    lg.drawImage(carvedLayer(), 0, 0);
    if (anyWet) {
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(WET, 0, 0);
      bg.globalCompositeOperation = "destination-in"; bg.drawImage(mWet, 0, 0);
      lg.drawImage(B, 0, 0);
      lg.drawImage(mWetEdge, 0, 0);
      // a narrow reflection slides over the wet parts only, as if the candy tilts in the light
      const gx = (Math.sin(performance.now() / 2200) * R * 0.9 + HALF) * K;
      const sg = bg.createLinearGradient(gx - R * 0.1 * K, 0, gx + R * 0.1 * K, R * 0.06 * K);
      sg.addColorStop(0, "rgba(255,250,235,0)"); sg.addColorStop(0.5, "rgba(255,250,235,.5)"); sg.addColorStop(1, "rgba(255,250,235,0)");
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.fillStyle = sg; bg.fillRect(0, 0, PX, PX);
      bg.globalCompositeOperation = "destination-in"; bg.drawImage(mWet, 0, 0);
      lg.globalCompositeOperation = "lighter"; lg.drawImage(B, 0, 0); lg.globalCompositeOperation = "source-over";
    }
    if (anyMelt) {   // a ring of dark translucent melted caramel around each hole
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(mRim, 0, 0);
      bg.globalCompositeOperation = "destination-out"; bg.drawImage(mHole, 0, 0);
      bg.globalCompositeOperation = "source-in"; bg.fillStyle = "rgba(150,70,14,.38)"; bg.fillRect(0, 0, PX, PX);
      lg.globalCompositeOperation = "multiply"; lg.drawImage(B, 0, 0); lg.globalCompositeOperation = "source-over";
    }
    const now = performance.now();
    toC(lg); lg.save(); lg.clip(edge); game.cracks.forEach(c => drawCrack(lg, c, now)); lg.restore();
    if (light) {   // match the room: multiply by a light tinted toward the camera's average colour
      lg.save(); lg.clip(edge); lg.globalCompositeOperation = "multiply"; lg.fillStyle = light.tint; lg.fillRect(-HALF, -HALF, S, S); lg.restore();
      lg.globalCompositeOperation = "source-over";
    }
    lg.setTransform(1, 0, 0, 1, 0, 0);
    if (pending.length) piecesFromPending();
    if (anyMelt) {
      lg.globalCompositeOperation = "destination-out"; lg.drawImage(mHole, 0, 0);
      // the hole's lower right inner wall catches the light
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(mHole, 0, 0);
      bg.globalCompositeOperation = "destination-out"; bg.drawImage(mHole, -0.8 * K, -0.8 * K);
      bg.globalCompositeOperation = "source-in"; bg.fillStyle = "rgba(255,244,215,.75)"; bg.fillRect(0, 0, PX, PX);
      lg.globalCompositeOperation = "source-over"; lg.drawImage(B, 0, 0);
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(mHole, 0, 0);
      bg.globalCompositeOperation = "destination-out"; bg.drawImage(mHole, -4 * K, -4 * K);
      bg.globalCompositeOperation = "source-in"; bg.fillStyle = "rgba(240,150,50,.35)"; bg.fillRect(0, 0, PX, PX);
      lg.globalCompositeOperation = "lighter"; lg.drawImage(B, 0, 0); lg.globalCompositeOperation = "source-over";
      // a film of glassy amber left at the hole's edge: light (and the camera feed) shows through it
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(mHole, 0, 0);
      bg.globalCompositeOperation = "destination-out"; bg.drawImage(mCore, 0, 0);
      bg.globalCompositeOperation = "source-in"; bg.fillStyle = "rgba(205,110,28,.45)"; bg.fillRect(0, 0, PX, PX);
      lg.drawImage(B, 0, 0);
      // and the upper wall shows the candy's thickness
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(mHole, 0, 0);
      bg.globalCompositeOperation = "destination-out"; bg.drawImage(mHole, 0, R * 0.045 * K);
      bg.globalCompositeOperation = "source-in";
      const wg = bg.createLinearGradient(0, 0, 0, PX); wg.addColorStop(0, "#b8661f"); wg.addColorStop(1, "#6e3410"); bg.fillStyle = wg; bg.fillRect(0, 0, PX, PX);
      lg.globalAlpha = 0.7; lg.drawImage(B, 0, 0); lg.globalAlpha = 1;
    }
    if (chips.length) { toC(lg); lg.globalCompositeOperation = "destination-out"; chips.forEach(p => lg.fill(p)); }
    lg.globalCompositeOperation = "source-over"; lg.setTransform(1, 0, 0, 1, 0, 0);
  }

  const flashes = [], bursts = [];
  // a crack is announced where it starts: a white ring snaps out, red if it heads into the shape (users r2: three
  // pokes "changed nothing" because an 11 px hairline was invisible at arm's length)
  function drawBursts(ctx, dt) {
    for (const b of bursts) b.t += dt;
    for (let i = bursts.length - 1; i >= 0; i--) if (bursts[i].t > 0.6) bursts.splice(i, 1);
    for (const b of bursts) {
      const k = b.t / 0.6, r = R * (0.06 + (b.big ? 0.28 : 0.2) * Math.sqrt(k));
      ctx.save(); ctx.globalAlpha = 1 - k; ctx.lineWidth = (b.big ? 4 : 3) * (1 - k) + 1;
      ctx.strokeStyle = b.inward ? "#FF3B5C" : "#fff6e0"; ctx.beginPath(); ctx.arc(b.x, b.y, r, 0, TAU); ctx.stroke(); ctx.restore();
    }
    // open cracks running inward keep a pulsing red marker at their tip until they stop or the round ends
    for (const c of game.cracks) if (c.inward && !c.done) {
      let i = c.cum.findIndex(v => v >= c.len); if (i < 0) i = c.pts.length - 1;
      const [x, y] = loc(c.pts[i]), a = 0.55 + 0.45 * Math.sin(performance.now() / 110);
      ctx.save(); ctx.strokeStyle = `rgba(255,59,92,${a})`; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(x, y, R * 0.07, 0, TAU); ctx.stroke(); ctx.restore();
    }
  }
  function onEvent(ev) {
    if (ev.type === "chip") chipFrom(ev.crack);
    else if (ev.type === "win") { compose(); breakOut(); }
    else if (ev.type === "lose") lostT = 0;
    else if (ev.type === "segment") flashes.push({ k: ev.k, t: 0 });
    else if (ev.type === "crack" || ev.type === "spread") { const [x, y] = loc(ev.crack.pts[0]); bursts.push({ x, y, t: 0, big: ev.type === "spread", inward: ev.crack.inward }); }
  }
  // a finished stretch of the outline lights up for a moment
  function drawFlashes(ctx, dt) {
    for (const f of flashes) f.t += dt;
    for (let i = flashes.length - 1; i >= 0; i--) if (flashes[i].t > 0.5) flashes.splice(i, 1);
    if (!flashes.length) return;
    ctx.save(); ctx.globalCompositeOperation = "lighter"; ctx.lineCap = "round"; ctx.lineJoin = "round";
    for (const f of flashes) {
      ctx.strokeStyle = `rgba(255,214,140,${0.7 * (1 - f.t / 0.5)})`; ctx.lineWidth = R * 0.035;
      game.paths.forEach((path, pi) => {
        ctx.beginPath(); let on = false;
        path.forEach((q, i) => { if (game.segOf[pi][i] !== f.k) { on = false; return; } const [x, y] = loc(q); on ? ctx.lineTo(x, y) : ctx.moveTo(x, y); on = true; });
        ctx.stroke();
      });
    }
    ctx.restore();
  }
  // the room's light on the candy: brightness and a little of the room's colour (from the camera, set by the app)
  let light = null, pose = null;
  function setLight(l) { light = l; }
  /** After a win: draw the freed shape at screen (x, y), scaled by k (null = where it came out). */
  function setPose(p) { pose = p; }

  /** Draw at the candy centre (cx, cy) on a CSS-px context. ring = progress 0..1 (null = no ring). */
  function draw(ctx, dt, ring) {
    const { cx, cy } = game.geom;
    if (!won) compose();
    updateDrips(dt);
    for (const p of pieces) { p.t += dt; p.vy += 900 * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt; }
    for (let i = pieces.length - 1; i >= 0; i--) if (pieces[i].t > 2.5) { pieces[i].c.width = 0; pieces.splice(i, 1); }
    let sx = 0;
    if (lostT >= 0) {
      lostT += dt; sx = lostT < 0.35 ? Math.sin(lostT * 90) * R * 0.03 * (1 - lostT / 0.35) : 0;
      const fc = game.cracks.find(k => k.fatal);
      if (!halves && fc && fc.len >= fc.total) split();
    }
    ctx.save(); ctx.translate(cx + sx, cy);
    const drawPieces = () => { for (const p of pieces) {
      ctx.save(); ctx.translate(p.ox + p.x, p.oy + p.y); ctx.rotate(p.rot); ctx.scale(1, Math.max(0.15, Math.abs(Math.cos(p.t * p.vr * 2)))); ctx.translate(-p.ox, -p.oy);
      ctx.drawImage(p.c, -HALF, -HALF, S, S); ctx.restore();
    } };
    if (won) drawPieces();
    if (won) {
      ctx.save();
      wonT += dt;
      if (pose) { ctx.translate(pose.x - cx - sx, pose.y - cy); ctx.scale(pose.k, pose.k); ctx.rotate(pose.rot || 0); }
      const lift = Math.min(1, wonT / 0.6), e = 1 - Math.pow(1 - lift, 3), sc = 1 + 0.08 * e;
      // soft shadow without ctx.filter (iOS Safari has none): shadow of a copy drawn far off screen
      const dk = ctx.getTransform().a * sc;   // shadow offsets ignore the transform
      ctx.save(); ctx.scale(sc, sc); ctx.shadowColor = `rgba(0,0,0,${0.55 * e})`; ctx.shadowBlur = R * 0.12 * dk; ctx.shadowOffsetX = (9000 + R * 0.04 * e) * dk; ctx.shadowOffsetY = R * 0.14 * e * dk;
      ctx.translate(-9000, 0); ctx.fillStyle = "#000"; ctx.fill(body); ctx.restore();
      // the freed shape: its side wall shows under it (3-4 mm of toffee), then the top, then a rim light
      ctx.save(); ctx.scale(sc, sc); ctx.translate(0, -R * 0.05 * e);
      ctx.save(); ctx.translate(0, R * 0.03); ctx.fillStyle = "#5a2a08"; ctx.fill(body); ctx.restore();
      ctx.save(); ctx.clip(body); ctx.drawImage(L, -HALF, -HALF, S, S);
      ctx.translate(-1, -1); ctx.strokeStyle = "rgba(255,240,210,.6)"; ctx.lineWidth = 1; ctx.stroke(body); ctx.restore();
      ctx.restore();
      // a glint runs across the freed shape once
      const gx = (wonT - 0.5) * 2.4 * R - R;
      if (wonT > 0.4 && wonT < 1.6) {
        ctx.save(); ctx.scale(sc, sc); ctx.translate(0, -R * 0.05 * e); ctx.clip(body);
        // one narrow diagonal streak (the gradient axis runs across the streak)
        const gl = ctx.createLinearGradient(gx - R * 0.12, -R * 0.04, gx + R * 0.12, R * 0.04);
        gl.addColorStop(0, "rgba(255,250,230,0)"); gl.addColorStop(0.5, "rgba(255,250,230,.32)"); gl.addColorStop(1, "rgba(255,250,230,0)");
        ctx.globalCompositeOperation = "lighter"; ctx.fillStyle = gl; ctx.fillRect(-R, -R, 2 * R, 2 * R); ctx.restore();
      }
      ctx.restore();
    } else if (halves) {
      const fc = game.cracks.find(k => k.fatal);
      const k = Math.min(1, (lostT - 0) / 0.5), e = 1 - Math.pow(1 - k, 3), d = R * 0.06 * e;
      // each half casts its own shadow, so the gap between them shows the room, not a black band
      const dk = ctx.getTransform().a;
      for (const h of halves) {
        ctx.save(); ctx.translate(h.nx * d, h.ny * d + R * 0.02 * e); ctx.rotate(h.rot * e);
        ctx.shadowColor = "rgba(0,0,0,.45)"; ctx.shadowBlur = R * 0.1 * dk; ctx.shadowOffsetY = R * 0.07 * dk;
        ctx.drawImage(h.c, -HALF, -HALF, S, S); ctx.restore();
      }
      if (fc) {   // where it started: so the loss is not a mystery
        const [x, y] = loc(fc.pts[0]), a = 0.6 + 0.4 * Math.sin(lostT * 9);
        ctx.save(); ctx.strokeStyle = `rgba(255,59,92,${a})`; ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(x, y, R * 0.1, 0, TAU); ctx.stroke(); ctx.restore();
      }
    } else {
      if (shadowDirty) rebuildShadow();
      ctx.drawImage(SHc, -HALF + R * 0.03, -HALF + R * 0.08, S, S);
      ctx.drawImage(L, -HALF, -HALF, S, S);
      drawFlashes(ctx, dt);
      drawBursts(ctx, dt);
      if (ring != null) {
        ctx.lineCap = "round"; ctx.lineWidth = 4;
        ctx.strokeStyle = "rgba(255,255,255,.22)"; ctx.beginPath(); ctx.arc(0, 0, 1.13 * R, 0, TAU); ctx.stroke();
        if (ring > 0.005) { ctx.strokeStyle = "#fff"; ctx.beginPath(); ctx.arc(0, 0, 1.13 * R, -Math.PI / 2, -Math.PI / 2 + ring * TAU); ctx.stroke(); }
      }
    }
    if (!won) drawPieces();
    for (const d of drips) {
      ctx.save(); ctx.globalAlpha = d.a; ctx.translate(d.x, d.y);
      const g2 = ctx.createLinearGradient(0, -d.len, 0, 4); g2.addColorStop(0, "rgba(150,70,15,0)"); g2.addColorStop(1, "rgba(176,86,20,.95)");
      ctx.fillStyle = g2; ctx.beginPath(); ctx.moveTo(-1.2, -d.len); ctx.quadraticCurveTo(-4, 0, 0, 4.5); ctx.quadraticCurveTo(4, 0, 1.2, -d.len); ctx.fill();
      ctx.fillStyle = "rgba(255,240,200,.8)"; ctx.beginPath(); ctx.arc(-1, 1.5, 1, 0, TAU); ctx.fill(); ctx.restore();
    }
    ctx.restore();
  }

  /** The candy as it ended (the freed shape on a win), for the result screen. */
  function snapshot() {
    const c = off(PX, PX), g = c.getContext("2d");
    if (won) { toC(g); g.clip(body); g.setTransform(1, 0, 0, 1, 0, 0); }
    g.drawImage(L, 0, 0);
    return c;
  }

  made = null;
  // pieces, halves and the snapshot are made later: they go on the same list
  const track = fn => (...a) => { made = mine; try { return fn(...a); } finally { made = null; } };
  function dispose() { for (const c of mine) { c.width = 0; c.height = 0; } mine.length = 0; }
  return { draw: track(draw), onEvent: track(onEvent), snapshot: track(snapshot), setLight, setPose, edgeR, dispose, get won() { return won; } };
}
