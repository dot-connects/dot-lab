// Candy look: pre-rendered dry and wet textures, carved groove, pits, scratches, staged cracks, chips that fall off,
// licked gloss, melted holes you can see through, drips, and the win / lose break. Reads engine state, never writes it.
// Drawing is in candy-local px (centre 0,0); layers are K x oversampled.
import { RULES, cellCentre, crackStage } from "./engine.js";

const TAU = Math.PI * 2;
const off = (w, h) => { const c = document.createElement("canvas"); c.width = Math.max(1, w); c.height = Math.max(1, h); return c; };
function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
const clamp = v => Math.min(1, Math.max(0, v));

export function createCandyView(game, dprIn) {
  const { R } = game.geom, K = Math.min(2, dprIn || 1), S = Math.ceil(2.5 * R), HALF = S / 2, PX = Math.ceil(S * K);
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

  // ---------- textures ----------
  function texture(wet) {
    const c = off(PX, PX), g = toC(c.getContext("2d")), r = rng(77);
    g.save(); g.clip(edge);
    const base = g.createRadialGradient(-0.25 * R, -0.25 * R, 0, 0, 0, R * 1.05);
    if (!wet) { base.addColorStop(0, "#efb75c"); base.addColorStop(0.55, "#dc953c"); base.addColorStop(0.86, "#b06420"); base.addColorStop(1, "#6b3410"); }
    else { base.addColorStop(0, "#c9772a"); base.addColorStop(0.6, "#a85718"); base.addColorStop(1, "#5e2a0a"); }
    g.fillStyle = base; g.fillRect(-HALF, -HALF, S, S);
    // caramel blotches, darker toward the rim
    for (let i = 0; i < 260; i++) {
      const t = r() * TAU, d = Math.sqrt(r()) * R, x = d * Math.cos(t), y = d * Math.sin(t), rr = R * (0.04 + r() * 0.15);
      const gr = g.createRadialGradient(x, y, 0, x, y, rr), dark = r() < 0.4 + 0.5 * (d / R);
      gr.addColorStop(0, dark ? "rgba(110,50,12,.16)" : "rgba(255,214,140,.12)"); gr.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = gr; g.fillRect(x - rr, y - rr, rr * 2, rr * 2);
    }
    // honeycomb pores with power-law sizes; each big pore catches light on its lower right lip
    const n = wet ? 700 : 2000, ps = R / 142;
    for (let i = 0; i < n; i++) {
      const t = r() * TAU, d = Math.sqrt(r()) * R * 0.98, x = d * Math.cos(t), y = d * Math.sin(t);
      const s = ps * (0.5 + 3.6 * Math.pow(r(), 3.2));
      g.fillStyle = wet ? "rgba(70,28,6,.18)" : `rgba(92,40,10,${0.35 + 0.3 * r()})`;
      g.beginPath(); g.ellipse(x, y, s, s * (0.75 + 0.3 * r()), r() * 3, 0, TAU); g.fill();
      if (!wet && s > 1.1 * ps) { g.strokeStyle = "rgba(255,226,170,.38)"; g.lineWidth = 0.6; g.beginPath(); g.arc(x + 0.25 * s, y + 0.25 * s, s, 0.1, 1.5); g.stroke(); }
    }
    g.strokeStyle = wet ? "rgba(50,20,4,.45)" : "rgba(84,36,8,.42)"; g.lineWidth = 0.14 * R; g.stroke(edge);
    g.strokeStyle = "rgba(60,24,4,.5)"; g.lineWidth = 0.035 * R; g.stroke(edge);
    // pressed shape: engraved, dark on the upper left, light on the lower right
    g.lineJoin = "round"; g.lineCap = "round";
    const gw = Math.max(1.6, R * 0.017);
    g.save(); g.translate(-0.7, -0.7); g.strokeStyle = "rgba(82,34,6,.75)"; g.lineWidth = gw; lines.forEach(l => g.stroke(l)); g.restore();
    g.save(); g.translate(0.9, 0.9); g.strokeStyle = "rgba(255,218,150,.55)"; g.lineWidth = gw * 0.55; lines.forEach(l => g.stroke(l)); g.restore();
    g.globalCompositeOperation = "screen";
    const sp = g.createRadialGradient(-0.38 * R, -0.42 * R, 0, -0.38 * R, -0.42 * R, 0.5 * R);
    sp.addColorStop(0, wet ? "rgba(255,236,190,.55)" : "rgba(255,236,190,.26)"); sp.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = sp; g.fillRect(-HALF, -HALF, S, S);
    if (wet) for (let i = 0; i < 90; i++) {
      const t = r() * TAU, d = Math.sqrt(r()) * R, x = d * Math.cos(t), y = d * Math.sin(t);
      g.fillStyle = `rgba(255,248,225,${0.25 + 0.45 * r()})`; g.beginPath(); g.ellipse(x, y, ps * (1.5 + 4 * r()), ps * (0.8 + 1.2 * r()), -0.6, 0, TAU); g.fill();
    }
    g.restore();
    return c;
  }
  const DRY = texture(false), WET = texture(true);
  const SH = off(PX, PX);
  { const g = toC(SH.getContext("2d")); g.shadowColor = "rgba(0,0,0,.55)"; g.shadowBlur = R * 0.2 * K; g.shadowOffsetX = 4000 * K; g.translate(-4000, 0); g.fill(edge); }
  const damage = off(PX, PX), dg = toC(damage.getContext("2d"));
  const L = off(PX, PX), lg = L.getContext("2d");
  const B = off(PX, PX), bg = B.getContext("2d");
  const mWet = off(PX, PX), mRim = off(PX, PX), mHole = off(PX, PX);

  // ---------- wet / melt masks from the engine grid ----------
  const N = RULES.GRID, cell = 2 * R / N;
  let maskFrame = 0, anyWet = false, anyMelt = false;
  function blobs(c, val) {
    const g = c.getContext("2d"); g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, c.width, c.height); toC(g);
    let any = false;
    for (let k = 0; k < N * N; k++) {
      const a = val(k); if (a < 0.02) continue;
      any = true;
      const [sx, sy] = cellCentre(game, k), [x, y] = loc([sx, sy]), r = cell * 1.3;
      const gr = g.createRadialGradient(x, y, 0, x, y, r);
      gr.addColorStop(0, `rgba(255,255,255,${a})`); gr.addColorStop(0.55, `rgba(255,255,255,${a * 0.7})`); gr.addColorStop(1, "rgba(255,255,255,0)");
      g.fillStyle = gr; g.fillRect(x - r, y - r, 2 * r, 2 * r);
    }
    return any;
  }
  function masks() {
    anyWet = blobs(mWet, k => clamp(game.wetG[k] * 0.9));
    anyMelt = blobs(mRim, k => clamp((game.meltG[k] - 0.1) / 0.45));
    if (anyMelt) blobs(mHole, k => clamp((game.meltG[k] - 0.4) / 0.5));
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
  function drawCrack(g, c) {
    const st = c.fatal ? 2 : crackStage(game, c), sc = R / 142, w = [1.3, 1.8, 2.8][st] * sc, al = [0.8, 0.9, 0.97][st];
    const pts = c.pts.map(loc);
    g.lineJoin = "round"; g.lineCap = "round";
    g.save(); g.translate(0.8, 0.8); g.strokeStyle = "rgba(255,214,160,.45)"; g.lineWidth = w * 0.6; crackPath(g, pts, c.cum, c.len); g.stroke(); g.restore();
    g.strokeStyle = `rgba(40,14,2,${al})`; g.lineWidth = w; crackPath(g, pts, c.cum, c.len); g.stroke();
    if (st >= 1) for (const b of branchesOf(c)) if (c.len > b.at) {
      const bc = b.pts.map((_, i) => i * R * 0.02), upto = Math.min((c.len - b.at) * 0.6, bc[bc.length - 1]);
      g.strokeStyle = `rgba(40,14,2,${al * 0.8})`; g.lineWidth = w * 0.55; crackPath(g, b.pts, bc, upto); g.stroke();
    }
  }

  // ---------- chips and pieces ----------
  const chips = [], pieces = [], pending = [];
  function chipFrom(c) {
    const pts = c.pts.map(loc), [ex, ey] = pts[pts.length - 1], te = Math.atan2(ey, ex), r = rng(c.seed + 1);
    const dir = r() < 0.5 ? 1 : -1, span = 0.16 + r() * 0.1;
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
      if (!pc.win) chips.push(pc.path);
    }
  }
  let won = false, wonT = 0, lostT = -1;
  function breakOut() {
    // the body around the shape snaps into wedges that fall away; the shape stays
    const n = 7, r = rng(game.cracks.length + 31), a0 = r() * TAU;
    for (let i = 0; i < n; i++) {
      const a = a0 + i / n * TAU, b = a0 + (i + 1) / n * TAU + 0.002, p = new Path2D();
      p.moveTo(0, 0);
      for (let k = 0; k <= 10; k++) { const t = a + (b - a) * k / 10 + (k > 0 && k < 10 ? (r() - 0.5) * 0.03 : 0); p.lineTo(1.2 * R * Math.cos(t), 1.2 * R * Math.sin(t)); }
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
    toC(lg); lg.fillStyle = "#4a2207"; lg.save(); lg.translate(0, R * 0.025); lg.fill(edge); lg.restore();   // candy thickness
    lg.setTransform(1, 0, 0, 1, 0, 0); lg.drawImage(DRY, 0, 0); lg.drawImage(damage, 0, 0);
    toC(lg); carved(lg); lg.setTransform(1, 0, 0, 1, 0, 0);
    if (anyWet) {
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.drawImage(WET, 0, 0);
      bg.globalCompositeOperation = "destination-in"; bg.drawImage(mWet, 0, 0);
      lg.drawImage(B, 0, 0);
    }
    if (anyMelt) {
      bg.globalCompositeOperation = "source-over"; bg.clearRect(0, 0, PX, PX); bg.fillStyle = "#ffc56a"; bg.fillRect(0, 0, PX, PX);
      bg.globalCompositeOperation = "destination-in"; bg.drawImage(mRim, 0, 0);
      lg.globalAlpha = 0.6; lg.globalCompositeOperation = "source-atop"; lg.drawImage(B, 0, 0); lg.globalAlpha = 1;
      lg.globalCompositeOperation = "source-over";
    }
    toC(lg); lg.save(); lg.clip(edge); game.cracks.forEach(c => drawCrack(lg, c)); lg.restore();
    lg.setTransform(1, 0, 0, 1, 0, 0);
    if (pending.length) piecesFromPending();
    if (anyMelt) { lg.globalCompositeOperation = "destination-out"; lg.drawImage(mHole, 0, 0); }
    if (chips.length) { toC(lg); lg.globalCompositeOperation = "destination-out"; chips.forEach(p => lg.fill(p)); }
    lg.globalCompositeOperation = "source-over"; lg.setTransform(1, 0, 0, 1, 0, 0);
  }

  function onEvent(ev) {
    if (ev.type === "chip") chipFrom(ev.crack);
    else if (ev.type === "win") { compose(); breakOut(); }
    else if (ev.type === "lose") lostT = 0;
  }

  /** Draw at the candy centre (cx, cy) on a CSS-px context. ring = progress 0..1 (null = no ring). */
  function draw(ctx, dt, ring) {
    const { cx, cy } = game.geom;
    if (!won) compose();
    updateDrips(dt);
    for (const p of pieces) { p.t += dt; p.vy += 900 * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt; }
    for (let i = pieces.length - 1; i >= 0; i--) if (pieces[i].t > pieces[i].life) pieces.splice(i, 1);
    let sx = 0;
    if (lostT >= 0) { lostT += dt; sx = lostT < 0.35 ? Math.sin(lostT * 90) * R * 0.03 * (1 - lostT / 0.35) : 0; }
    ctx.save(); ctx.translate(cx + sx, cy);
    const drawPieces = () => { for (const p of pieces) {
      ctx.save(); ctx.globalAlpha = Math.max(0, 1 - p.t / p.life); ctx.translate(p.ox + p.x, p.oy + p.y); ctx.rotate(p.rot); ctx.translate(-p.ox, -p.oy);
      ctx.drawImage(p.c, -HALF, -HALF, S, S); ctx.restore();
    } };
    if (won) drawPieces();
    if (won) {
      wonT += dt;
      const lift = Math.min(1, wonT / 0.6), e = 1 - Math.pow(1 - lift, 3), sc = 1 + 0.08 * e;
      // soft shadow without ctx.filter (iOS Safari has none): shadow of a copy drawn far off screen
      const dk = ctx.getTransform().a * sc;   // shadow offsets ignore the transform
      ctx.save(); ctx.scale(sc, sc); ctx.shadowColor = `rgba(0,0,0,${0.55 * e})`; ctx.shadowBlur = R * 0.12 * dk; ctx.shadowOffsetX = (9000 + R * 0.04 * e) * dk; ctx.shadowOffsetY = R * 0.14 * e * dk;
      ctx.translate(-9000, 0); ctx.fillStyle = "#000"; ctx.fill(body); ctx.restore();
      ctx.save(); ctx.scale(sc, sc); ctx.translate(0, -R * 0.05 * e); ctx.clip(body); ctx.drawImage(L, -HALF, -HALF, S, S); ctx.restore();
      // a glint runs across the freed shape once
      const gx = (wonT - 0.5) * 2.4 * R - R;
      if (wonT > 0.4 && wonT < 1.6) {
        ctx.save(); ctx.scale(sc, sc); ctx.translate(0, -R * 0.05 * e); ctx.clip(body);
        const gl = ctx.createLinearGradient(gx - R * 0.3, -R, gx + R * 0.3, R);
        gl.addColorStop(0, "rgba(255,250,230,0)"); gl.addColorStop(0.5, "rgba(255,250,230,.55)"); gl.addColorStop(1, "rgba(255,250,230,0)");
        ctx.fillStyle = gl; ctx.fillRect(-R, -R, 2 * R, 2 * R); ctx.restore();
      }
    } else {
      ctx.drawImage(SH, -HALF + R * 0.03, -HALF + R * 0.08, S, S);
      ctx.drawImage(L, -HALF, -HALF, S, S);
      if (ring != null) {
        ctx.lineCap = "round"; ctx.lineWidth = 4;
        ctx.strokeStyle = "rgba(255,255,255,.22)"; ctx.beginPath(); ctx.arc(0, 0, 1.13 * R, 0, TAU); ctx.stroke();
        ctx.strokeStyle = "#fff"; ctx.beginPath(); ctx.arc(0, 0, 1.13 * R, -Math.PI / 2, -Math.PI / 2 + Math.max(0.001, ring) * TAU); ctx.stroke();
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

  return { draw, onEvent, snapshot, edgeR, get won() { return won; } };
}
