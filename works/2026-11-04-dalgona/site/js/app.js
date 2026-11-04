import { createGame, layout, step, poke, percent, label, reason, stars, clean, SHAPES, RULES, wetAt, crackStage } from "./engine.js";
import { startCamera, loadModels, createTracker, BONES, PALM } from "./camera.js";
import { createCandyView } from "./render.js";

const $ = id => document.getElementById(id);
const app = $("app"), cv = $("cv"), ctx = cv.getContext("2d"), video = $("video");
const DEBUG = new URLSearchParams(location.search).has("debug");
const ORDER = Object.keys(SHAPES);
let W = 0, H = 0, DPR = 1, geom = { cx: 0, cy: 0, R: 1 };
let mode = null;          // "camera" | "touch"
let tracker = null, game = null, view = null, lickMode = false, audio = null;
const touch = { x: 0, y: 0, down: false };
const TOUCH_LIFT = 46;    // touch mode: the needle tip sits above the finger so the finger does not hide it

const store = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch { return null; } };
const bestOf = k => {
  try {
    const v = JSON.parse(store("dalgona.best2." + k));
    const ok = v && typeof v === "object" && Number.isInteger(v.stars) && v.stars >= 0 && v.stars <= 3
      && Number.isFinite(v.time) && v.time >= 0 && Number.isFinite(v.pct) && v.pct >= 0 && v.pct <= 100;
    return ok ? v : null;
  } catch { return null; }
};
// a better record: more stars, then a faster time; a loss only beats a loss with less carved
const better = (a, b) => !b || a.stars > b.stars || (a.stars === b.stars && (a.stars ? a.time < b.time : a.pct > b.pct));

// ---------- layout ----------
const hc = document.createElement("canvas"), hg = hc.getContext("2d");   // the real hand cut out of the video (CSS px)
const freshGeom = () => ({ cx: W / 2, cy: H * 0.58, R: Math.min(W * 0.36, H * 0.2) });
function resize() {
  const b = app.getBoundingClientRect();
  DPR = Math.min(2, devicePixelRatio || 1); W = b.width; H = b.height;
  cv.width = W * DPR; cv.height = H * DPR; ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  hc.width = Math.ceil(W); hc.height = Math.ceil(H);
  if (mode === "touch") { tcv.width = cv.width; tcv.height = cv.height; }   // touch tongue layer, sized before the first lick
  // mid-round the candy stays put (toolbars folding resize the page; rebuilding the view lost chips and the win)
  if (!game || resultShown) geom = freshGeom();
  else if (!game.done) {
    // a toolbar folding is ignored; a rotation or a big shrink that leaves the candy off screen moves it (QA r3)
    const { cx, cy, R } = geom;
    if (cx + R > W || cy + R > H - 40 || cx - R < 0) {
      geom = freshGeom(); layout(game, geom); view.dispose(); view = createCandyView(game, DPR);
      // the new view knows nothing of pieces that already fell: replay them (review r5)
      for (const c of game.cracks) if (c.done && !c.inward && !c.healed && c.len >= c.total) view.onEvent({ type: "chip", crack: c });
    }
  }
  if (tracker) tracker.size(W, H);
}
addEventListener("resize", resize);

// ---------- camera layer in front of the candy ----------
function videoCover(g) {
  const vw = video.videoWidth, vh = video.videoHeight, k = Math.max(W / vw, H / vh), dw = vw * k, dh = vh * k;
  g.save(); g.translate(W, 0); g.scale(-1, 1); g.drawImage(video, (W - dw) / 2, (H - dh) / 2, dw, dh); g.restore();
}
// cut the hand out of the video and lay it over the candy, so the candy sits between face and hand. Only inside the
// candy: elsewhere the live video under the canvas already shows the hand.
function handFront(P, hs) {
  const { cx, cy, R } = geom, r = R * 1.15;
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
  for (const [x, y] of P) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  const pad = hs * 0.3;
  x0 = Math.max(cx - r, x0 - pad); y0 = Math.max(cy - r, y0 - pad); x1 = Math.min(cx + r, x1 + pad); y1 = Math.min(cy + r, y1 + pad);
  if (x1 <= x0 || y1 <= y0) return;
  hg.setTransform(1, 0, 0, 1, 0, 0); hg.clearRect(x0 - 2, y0 - 2, x1 - x0 + 4, y1 - y0 + 4);
  hg.save(); hg.beginPath(); hg.arc(cx, cy, r, 0, 7); hg.clip();
  hg.globalCompositeOperation = "source-over"; hg.fillStyle = "#fff"; hg.strokeStyle = "#fff"; hg.lineCap = "round"; hg.lineJoin = "round";
  hg.shadowColor = "#fff"; hg.shadowBlur = 4;   // soft edge
  hg.beginPath(); PALM.forEach((i, k) => (k ? hg.lineTo(P[i][0], P[i][1]) : hg.moveTo(P[i][0], P[i][1]))); hg.closePath(); hg.fill();
  hg.lineWidth = hs * 0.28; hg.stroke();
  // fingers a little thinner than real: a sliver of hand missing shows less than a band of wall pasted on the candy
  for (const [a, b] of BONES) {
    const tipward = [4, 8, 12, 16, 20].includes(b);
    hg.lineWidth = hs * (a === 0 || b === 0 ? 0.3 : a < 5 && b < 5 ? 0.25 : tipward ? 0.17 : 0.21);
    hg.beginPath(); hg.moveTo(P[a][0], P[a][1]); hg.lineTo(P[b][0], P[b][1]); hg.stroke();
  }
  hg.shadowBlur = 0;
  hg.globalCompositeOperation = "source-in"; videoCover(hg); hg.globalCompositeOperation = "source-over";
  hg.restore();
  ctx.drawImage(hc, x0, y0, x1 - x0, y1 - y0, x0, y0, x1 - x0, y1 - y0);
}

// ---------- drawing ----------
/** t = {base, dir, len, width, mouthW?, gap?, top?}; draws the open mouth (camera) and a wet tongue reaching to base + dir*len. */
let papPat = null;
function papillae(g) {   // a 48 px tile of matte bumps, baked once
  if (papPat) return papPat;
  const c = document.createElement("canvas"); c.width = c.height = 48; const t = c.getContext("2d");
  let sd = 5; const rr = () => (sd = (sd * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 70; i++) {
    const x = rr() * 48, y = rr() * 48;
    t.fillStyle = "rgba(140,35,55,.22)"; t.beginPath(); t.arc(x, y, 1, 0, 7); t.fill();
    t.fillStyle = "rgba(255,220,228,.28)"; t.beginPath(); t.arc(x - 0.6, y - 0.6, 0.7, 0, 7); t.fill();
  }
  return (papPat = g.createPattern(c, "repeat"));
}
function drawTongue(t, active, now, g = ctx) {
  const [bx, by] = t.base, [dx, dy] = t.dir, L = t.len, w = t.width;
  g.save();
  if (t.top) {   // the open mouth: dark inside, a strip of teeth under the upper lip
    const mx = (t.top[0] + bx) / 2, my = (t.top[1] + by) / 2, rx = t.mouthW * 0.42, ry = Math.max(2, t.gap * 0.55);
    const mg = g.createRadialGradient(mx, my, 0, mx, my, rx); mg.addColorStop(0, "#1a0306"); mg.addColorStop(1, "#5a1420");
    g.fillStyle = mg; g.beginPath(); g.ellipse(mx, my, rx, ry, Math.atan2(dy, dx) - Math.PI / 2, 0, 7); g.fill();
    g.fillStyle = "rgba(240,236,228,.85)"; g.beginPath(); g.ellipse(mx, my - ry * 0.75, rx * 0.7, Math.max(1, ry * 0.18), 0, 0, 7); g.fill();
    g.fillStyle = "rgba(0,0,0,.4)"; g.beginPath(); g.ellipse(mx, my - ry * 0.95, rx * 0.85, Math.max(1, ry * 0.2), 0, 0, 7); g.fill();
  }
  const wig = active ? Math.sin(now / 130) * 0.13 : 0;
  g.translate(bx, by); g.rotate(Math.atan2(dy, dx) - Math.PI / 2 + wig);
  if (active) {   // pressed against the candy: the tip flattens a little in time with the licking
    const pr = 0.5 + 0.5 * Math.sin(now / 130);
    g.translate(0, L * 0.9); g.scale(1 + 0.07 * pr, 1 - 0.05 * pr); g.translate(0, -L * 0.9);
  }
  // body: wide at the root, a rounded tip with a slight notch
  const body = new Path2D();
  body.moveTo(-w * 0.42, 0);
  body.bezierCurveTo(-w * 0.58, L * 0.45, -w * 0.5, L * 0.92, -w * 0.12, L * 0.99);
  body.quadraticCurveTo(0, L * 0.95, w * 0.12, L * 0.99);
  body.bezierCurveTo(w * 0.5, L * 0.92, w * 0.58, L * 0.45, w * 0.42, 0);
  body.closePath();
  // contact shadow on the candy below the tongue
  // (a gradient, not shadowBlur: shadow offsets ignore the rotation)
  g.save(); g.translate(w * 0.06, L * 0.62); g.scale(1, L / w);
  const shg = g.createRadialGradient(0, 0, 0, 0, 0, w * 0.62); shg.addColorStop(0, "rgba(0,0,0,.32)"); shg.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = shg; g.beginPath(); g.arc(0, 0, w * 0.62, 0, 7); g.fill(); g.restore();
  if (active) {   // where the tip touches: a tight dark contact and a ring of saliva on the candy
    const cg = g.createRadialGradient(0, L * 0.97, 0, 0, L * 0.97, w * 0.28); cg.addColorStop(0, "rgba(30,8,0,.45)"); cg.addColorStop(1, "rgba(30,8,0,0)");
    g.fillStyle = cg; g.fillRect(-w * 0.3, L * 0.97 - w * 0.3, w * 0.6, w * 0.6);
    g.strokeStyle = "rgba(255,236,200,.22)"; g.lineWidth = 1.2; g.beginPath(); g.ellipse(0, L * 0.97, w * 0.48, w * 0.2, 0, 0, 7); g.stroke();
  }
  const tg = g.createRadialGradient(-w * 0.1, L * 0.55, w * 0.05, 0, L * 0.5, w * 0.75);
  tg.addColorStop(0, "#f2a0ab"); tg.addColorStop(0.55, "#d86a7e"); tg.addColorStop(1, "#9e3a50");
  g.fillStyle = tg; g.fill(body);
  // root goes into the mouth: darker toward the base
  const rg = g.createLinearGradient(0, 0, 0, L * 0.35); rg.addColorStop(0, "rgba(90,10,25,.6)"); rg.addColorStop(1, "rgba(90,10,25,0)");
  g.fillStyle = rg; g.fill(body);
  // rounded flesh: darker toward the edges
  g.save(); g.translate(0, L * 0.5); g.scale(1, L / w);
  const eg = g.createRadialGradient(0, 0, 0, 0, 0, w * 0.6); eg.addColorStop(0.55, "rgba(110,20,40,0)"); eg.addColorStop(1, "rgba(110,20,40,.45)");
  g.restore();
  g.save(); g.clip(body);
  g.save(); g.translate(0, L * 0.5); g.scale(1, L / w); g.fillStyle = eg; g.fillRect(-w, -w, 2 * w, 2 * w); g.restore();
  g.fillStyle = papillae(g); g.fill(body);   // matte bumps, not bubbles
  g.strokeStyle = "#a02a46"; g.lineWidth = w * 0.2; g.globalAlpha = 0.22; g.stroke(body); g.globalAlpha = 1;
  g.restore();
  g.strokeStyle = "rgba(140,25,50,.45)"; g.lineWidth = Math.max(1, w * 0.045); g.lineCap = "round";
  g.beginPath(); g.moveTo(0, L * 0.12); g.quadraticCurveTo(w * 0.02, L * 0.5, 0, L * 0.84); g.stroke();
  // saliva along the left ridge: a soft band, a bright core, three beads
  const ridge = new Path2D(); ridge.moveTo(-w * 0.3, L * 0.2); ridge.quadraticCurveTo(-w * 0.4, L * 0.55, -w * 0.18, L * 0.85);
  g.strokeStyle = "rgba(255,255,255,.22)"; g.lineWidth = w * 0.07; g.stroke(ridge);
  g.strokeStyle = "rgba(255,255,255,.75)"; g.lineWidth = w * 0.02; g.stroke(ridge);
  g.fillStyle = "rgba(255,255,255,.9)";
  for (const [qx, qy] of [[-0.33, 0.4], [-0.3, 0.62], [0.12, 0.8]]) { g.beginPath(); g.arc(qx * w, qy * L, w * 0.018, 0, 7); g.fill(); }
  if (t.fadeRoot) {   // touch mode: the root fades out instead of a cut edge (on its own layer, see drawTongueFaded)
    g.globalCompositeOperation = "destination-out";
    const fg = g.createLinearGradient(0, 0, 0, L * 0.35); fg.addColorStop(0, "rgba(0,0,0,1)"); fg.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = fg; g.fillRect(-w, -2, 2 * w, L * 0.36);
  }
  g.restore();
}
// touch mode tongue: drawn on its own layer so the faded root does not erase the candy under it
const tcv = document.createElement("canvas"), tctx = tcv.getContext("2d");
function drawTongueFaded(t, now) {
  if (tcv.width !== cv.width || tcv.height !== cv.height) { tcv.width = cv.width; tcv.height = cv.height; }
  const pad = t.width, x0 = Math.max(0, (t.base[0] - pad) * DPR), y0 = Math.max(0, (t.base[1] - pad * 0.3) * DPR);
  const w = Math.min(tcv.width - x0, 2 * pad * DPR), h = Math.min(tcv.height - y0, (t.len + pad * 0.6) * DPR);
  if (w <= 0 || h <= 0) return;
  tctx.setTransform(1, 0, 0, 1, 0, 0); tctx.clearRect(x0, y0, w, h); tctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  drawTongue(t, true, now, tctx);
  ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.drawImage(tcv, x0, y0, w, h, x0, y0, w, h); ctx.restore();
}
function pin(x, y, dx, dy, len, w0, w1) {   // a tapered quad from the tip (w0) to the tail (w1)
  const ex = x - dx * len, ey = y - dy * len, nx = -dy, ny = dx, p = new Path2D();
  p.moveTo(x + nx * w0, y + ny * w0); p.lineTo(ex + nx * w1, ey + ny * w1); p.arc(ex, ey, w1, Math.atan2(ny, nx), Math.atan2(ny, nx) + Math.PI);
  p.lineTo(x - nx * w0, y - ny * w0); p.closePath();
  return p;
}
function drawNeedle(tip, dir, down) {
  const len = geom.R * 1.05, [dx, dy] = dir;
  ctx.save(); ctx.globalAlpha = down ? 1 : 0.5;
  // shadow on the candy: offset and soft while hovering, closes in to the tip on contact
  const so = down ? geom.R * 0.025 : geom.R * 0.12;
  ctx.fillStyle = `rgba(0,0,0,${down ? 0.32 : 0.18})`; ctx.fill(pin(tip.x + so * 0.4, tip.y + so, dx - so * 0.002, dy, len, 0.2, 2.6));
  const ng = ctx.createLinearGradient(tip.x - dy * 2, tip.y + dx * 2, tip.x + dy * 2, tip.y - dx * 2);
  ng.addColorStop(0, "#5d646a"); ng.addColorStop(0.45, "#f4f6f8"); ng.addColorStop(1, "#7d858a");
  ctx.fillStyle = ng; ctx.fill(pin(tip.x, tip.y, dx, dy, len, 0.25, 1.7));
  if (down) {
    ctx.fillStyle = "rgba(40,14,2,.7)"; ctx.beginPath(); ctx.arc(tip.x, tip.y, 1.6, 0, 7); ctx.fill();
    ctx.strokeStyle = "rgba(255,236,200,.6)"; ctx.lineWidth = 0.8; ctx.beginPath(); ctx.arc(tip.x + 0.5, tip.y + 0.5, 2.4, 0.2, 2.4); ctx.stroke();
  }
  ctx.restore();
}
function drawStressRing(tip) {
  const st = game ? game.stress : 0, col = st < 0.3 ? "#7CE38B" : st < RULES.HAIR ? "#FFC23D" : "#FF3B5C";
  const r = 22 + (st >= RULES.HAIR ? Math.sin(performance.now() / 60) * 1.5 : 0);
  ctx.save(); ctx.lineCap = "round";
  const heat = game ? Math.min(1, game.heat / 0.9) : 0;
  if (heat > 0.15) {   // poke heat: the needle tip glows hotter with every poke in a run (players r4: cracks felt unfair)
    const hg2 = ctx.createRadialGradient(tip.x, tip.y, 0, tip.x, tip.y, r * 0.8);
    hg2.addColorStop(0, `rgba(255,${Math.round(150 - 110 * heat)},40,${0.55 * heat})`); hg2.addColorStop(1, "rgba(255,60,40,0)");
    ctx.fillStyle = hg2; ctx.beginPath(); ctx.arc(tip.x, tip.y, r * 0.8, 0, 7); ctx.fill();
  }
  if (st > 0.3) {   // stress whitening: toffee goes pale right before it gives
    const wg = ctx.createRadialGradient(tip.x, tip.y, 0, tip.x, tip.y, geom.R * 0.09);
    wg.addColorStop(0, `rgba(255,240,210,${Math.min(0.4, 0.3 * st)})`); wg.addColorStop(1, "rgba(255,240,210,0)");
    ctx.globalCompositeOperation = "screen"; ctx.fillStyle = wg; ctx.fillRect(tip.x - geom.R * 0.1, tip.y - geom.R * 0.1, geom.R * 0.2, geom.R * 0.2); ctx.globalCompositeOperation = "source-over";
  }
  ctx.strokeStyle = "rgba(0,0,0,.4)"; ctx.lineWidth = 4.5; ctx.beginPath(); ctx.arc(tip.x, tip.y, r, 0, 7); ctx.stroke();
  ctx.strokeStyle = col; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.arc(tip.x, tip.y, r, -Math.PI / 2, -Math.PI / 2 + Math.max(0.06, Math.min(1, st)) * Math.PI * 2); ctx.stroke();
  ctx.restore();
}

// crumbs scraped off the line
const crumbs = [];
function spawnCrumbs(x, y, n, burst) {
  for (let i = 0; i < n && crumbs.length < 160; i++) {
    const a = Math.random() * Math.PI * 2, sp = (burst ? 60 : 25) + Math.random() * (burst ? 90 : 45);
    crumbs.push({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp - 30, s: 0.7 + Math.random() * 1.6, t: 0, life: 0.35 + Math.random() * 0.35,
      c: Math.random() < 0.6 ? "#f6d8a0" : "#c98a3c" });
  }
}
function drawCrumbs(dt) {
  for (const p of crumbs) { p.t += dt; p.vy += 420 * dt; p.x += p.vx * dt; p.y += p.vy * dt; }
  for (let i = crumbs.length - 1; i >= 0; i--) if (crumbs[i].t > crumbs[i].life) crumbs.splice(i, 1);
  for (const p of crumbs) { ctx.globalAlpha = 1 - p.t / p.life; ctx.fillStyle = p.c; ctx.fillRect(p.x - p.s / 2, p.y - p.s / 2, p.s, p.s); }
  ctx.globalAlpha = 1;
}

// the outline not yet carved, shown after a loss: how close it was
function drawMissing(now) {
  ctx.save(); ctx.strokeStyle = "#FF3B5C"; ctx.lineWidth = 3; ctx.lineCap = "round"; ctx.setLineDash([2, 7]);
  ctx.globalAlpha = 0.65 + 0.35 * Math.sin(now / 160);
  game.paths.forEach((path, pi) => {
    const pr = game.prog[pi];
    ctx.beginPath();
    for (let i = 1; i < path.length; i++) {
      if (pr[i] >= RULES.DONE && pr[i - 1] >= RULES.DONE) continue;
      ctx.moveTo(path[i - 1][0], path[i - 1][1]); ctx.lineTo(path[i][0], path[i][1]);
    }
    ctx.stroke();
  });
  ctx.restore();
}
let stampCv = null;
function stampImage() {
  if (stampCv) return stampCv;
  const c = document.createElement("canvas"), sw = 220, sh = 110, k = 2; c.width = sw * k; c.height = sh * k;
  const g = c.getContext("2d"); g.scale(k, k);
  g.strokeStyle = g.fillStyle = "#E0213F"; g.lineWidth = 7;
  g.beginPath(); g.roundRect ? g.roundRect(8, 8, sw - 16, sh - 16, 14) : g.rect(8, 8, sw - 16, sh - 16); g.stroke();
  g.font = '68px "Do Hyeon", sans-serif'; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText("탈락", sw / 2, sh / 2 + 4);
  g.globalCompositeOperation = "destination-out";   // worn ink
  let sd = 3; const rr = () => (sd = (sd * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 400; i++) { g.beginPath(); g.arc(rr() * sw, rr() * sh, 0.5 + rr() * 1.5, 0, 7); g.fill(); }
  g.lineWidth = 1.2; for (let i = 0; i < 6; i++) { const x = rr() * sw, y = rr() * sh; g.beginPath(); g.moveTo(x, y); g.lineTo(x + 20 + rr() * 30, y + (rr() - 0.5) * 6); g.stroke(); }
  // cache only once the real font drew it (a fallback-font stamp would stick for the session: iOS review r6)
  try { if (document.fonts.check('68px "Do Hyeon"')) stampCv = c; } catch { stampCv = c; }
  return c;
}
function drawStamp(t) {
  if (t <= 0) return;
  // slammed down: 2x -> 0.92 -> 1 in 0.2 s, inked into the candy
  const sc = t < 0.12 ? 2 - 1.08 * (t / 0.12) : t < 0.2 ? 0.92 + 0.08 * ((t - 0.12) / 0.08) : 1;
  const img = stampImage(), w = geom.R * 1.15, h = w / 2;
  ctx.save(); ctx.globalAlpha = Math.min(0.92, t / 0.06); ctx.translate(geom.cx, geom.cy - geom.R * 0.1); ctx.rotate(-0.14); ctx.scale(sc, sc);
  ctx.globalCompositeOperation = "multiply"; ctx.drawImage(img, -w / 2, -h / 2, w, h);
  ctx.restore();
}

// ---------- sound and touch feedback ----------
function initAudio() {
  if (audio) { if (audio.ac.state !== "running") audio.ac.resume().catch(() => {}); return; }
  try {
    const ac = new (window.AudioContext || window.webkitAudioContext)();
    // every sound goes through one compressor so stacked cracks, chips and beats do not clip (juice r6)
    const bus = ac.createDynamicsCompressor();
    bus.threshold.value = -18; bus.knee.value = 6; bus.ratio.value = 4; bus.attack.value = 0.003; bus.release.value = 0.15;
    bus.connect(ac.destination);
    const buf = ac.createBuffer(1, ac.sampleRate, ac.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const loop = (type, f, q) => {   // a looped noise voice: scraping, licking
      const src = ac.createBufferSource(); src.buffer = buf; src.loop = true;
      const fl = ac.createBiquadFilter(); fl.type = type; fl.frequency.value = f; fl.Q.value = q;
      const gn = ac.createGain(); gn.gain.value = 0; src.connect(fl).connect(gn).connect(bus); src.start();
      return { fl, gn };
    };
    const sc = loop("bandpass", 3200, 1.2), lk = loop("lowpass", 900, 0.7);
    audio = { ac, bus, buf, gn: sc.gn, bp: sc.fl, lk };
  } catch { audio = null; }
}
// iOS suspends the context after a tab switch or a call: wake it on the next touch
for (const t of ["pointerup", "touchend", "click"]) addEventListener(t, () => { if (audio && audio.ac.state !== "running") audio.ac.resume().catch(() => {}); }, { capture: true });
const buzz = p => { try { navigator.vibrate && navigator.vibrate(p); } catch { /* not supported */ } };
function noise(f, d, v, type = "bandpass", at = 0) {
  if (!audio) return; const { ac, buf } = audio, s = ac.createBufferSource(); s.buffer = buf;
  const fl = ac.createBiquadFilter(); fl.type = type; fl.frequency.value = f; fl.Q.value = 1.4;
  const g = ac.createGain(), t = ac.currentTime + at; g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.001, t + d);
  s.connect(fl).connect(g).connect(audio.bus); s.start(t, Math.random() * 0.5); s.stop(t + d + 0.05);
}
function tone(f, d, v, type = "triangle", at = 0, detune = 0) {
  if (!audio) return; const { ac } = audio, o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime + at;
  o.type = type; o.frequency.value = f; o.detune.value = detune; g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.001, t + d);
  o.connect(g).connect(audio.bus); o.start(t); o.stop(t + d + 0.02);
}
// a low thump a phone speaker can play: a pitch drop from 2.5x plus a lowpassed noise body (sub-200 Hz alone is silent there)
function thump(f0, v = 0.7, at = 0) {
  if (!audio) return; const { ac } = audio, o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime + at;
  o.type = "sine"; o.frequency.setValueAtTime(f0 * 2.5, t); o.frequency.exponentialRampToValueAtTime(f0, t + 0.08);
  g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.18);
  o.connect(g).connect(audio.bus); o.start(t); o.stop(t + 0.2);
  noise(180, 0.05, 0.6 * v / 0.7, "lowpass", at);
}
const SFX = {
  poke: hit => { tone(hit ? 1900 : 1300, 0.05, 0.22); buzz(8); },
  segment: n => { tone(2000 + Math.min(12, n) * 120, 0.04, 0.2, "square"); noise(5200, 0.03, 0.25); buzz(12); },   // climbs as stretches finish
  corner: () => { noise(2200, 0.05, 0.6); tone(1500, 0.06, 0.25); buzz(18); },
  heal: () => { tone(660, 0.18, 0.2, "sine"); tone(990, 0.2, 0.18, "sine", 0.12); },
  beat: () => { thump(60); thump(52, 0.5, 0.16); buzz(15); },
  tick: () => { tone(1800, 0.03, 0.18, "square"); },
  shutter: () => { noise(3000, 0.04, 0.6, "highpass"); noise(2000, 0.05, 0.4, "highpass", 0.07); },
  hush: () => { tone(3200, 0.3, 0.04, "sine"); },
  hair: () => { noise(4200, 0.06, 0.5); buzz(15); },
  spread: () => { noise(2600, 0.1, 0.6); buzz([20, 30, 20]); },
  chip: () => { noise(1800, 0.14, 0.7); noise(3000, 0.05, 0.3, "bandpass", 0.26); buzz([30, 40, 30]); },
  snap: () => { noise(4000, 0.015, 0.9, "highpass"); noise(1200, 0.1, 0.7); thump(90); buzz([0, 12, 20, 40]); },
  win: () => {
    // scheduled on the audio clock (setTimeout wobbles); each note doubled 7 cents sharp for a shimmer
    [880, 1320, 1760, 2640].forEach((f, i) => { const d = i === 3 ? 0.5 : 0.35; tone(f, d, 0.18, "sine", i * 0.11); tone(f, d, 0.1, "sine", i * 0.11, 7); });
    buzz([40, 60, 40]);
  },
  lose: () => { noise(900, 0.06, 0.9); thump(70, 0.8, 0.02); noise(700, 0.4, 0.9, "lowpass", 0.02); buzz([80, 40, 160]); },
};

// screen shake: the haptic you can see (iPhone Safari has no vibrate)
let shakeUntil = 0, shakeAmp = 0;
const shake = (amp, ms = 150) => { shakeAmp = amp; shakeUntil = performance.now() + ms; };

// ---------- flow ----------
const show = id => ["start", "pick", "result"].forEach(k => { $(k).hidden = k !== id; });

let camStream = null;
$("camGo").onclick = async () => {
  if ($("camGo").disabled) return;
  $("camGo").disabled = true;
  initAudio();
  const msg = $("camMsg"); msg.hidden = false; msg.textContent = "카메라를 켜는 중이에요…";
  try {
    video.hidden = false;   // WebKit may not play a hidden video
    camStream = await startCamera(video);
    if (mode === "touch") { camStream.getTracks().forEach(t => t.stop()); camStream = null; video.hidden = true; return; }
    $("nocam").hidden = true;
    msg.textContent = "손 인식을 불러오는 중이에요(처음 한 번 몇 초)…";
    const models = await loadModels();
    if (mode === "touch") { camStream.getTracks().forEach(t => t.stop()); camStream = null; video.hidden = true; return; }
    tracker = createTracker(video, models, { onPoke: (x, y) => { if (game) poke(game, x, y); } });
    mode = "camera";
    resize(); showPick();
  } catch (e) {
    if (camStream) camStream.getTracks().forEach(t => t.stop());   // no camera light left on in touch mode
    camStream = null; video.hidden = true; $("nocam").hidden = false; $("camGo").disabled = false;
    msg.textContent = e && e.message === "timeout"
      ? "카메라가 응답하지 않아요. 크롬에서 열거나 화면 터치로 해 보세요."
      : "카메라를 쓸 수 없어요. 화면 터치로 해 보세요.";
  }
};
$("touchGo").onclick = () => { initAudio(); mode = "touch"; resize(); showPick(); };

// the start screen plays a round by itself: a needle scraping a star out (shows the game in one look)
const demoCv = $("demo"), dctx = demoCv.getContext("2d");
let demo = null;
function demoFrame(dt) {
  if ($("start").hidden) { if (demo) demo.v.dispose(); demo = null; return; }
  const S = demoCv.clientWidth || 210, k = Math.min(2, devicePixelRatio || 1);
  if (demoCv.width !== Math.round(S * k)) { demoCv.width = demoCv.height = Math.round(S * k); if (demo) demo.v.dispose(); demo = null; }
  if (!demo || (demo.g.done && demo.t > 3.2)) {
    const g = createGame("star", { cx: S / 2, cy: S / 2, R: S * 0.38 }, Math.random);
    if (demo) demo.v.dispose();
    demo = { g, v: createCandyView(g, k), i: 0, t: 0 };
  }
  const d = demo, g = d.g, path = g.paths[0];
  d.t += dt;
  if (!g.done) {
    d.i = (d.i + dt * RULES.V_SAFE * g.vK * 0.8 * g.geom.R * 2.2 / (g.geom.R * 0.02)) % (path.length - 1);
    const p = path[Math.floor(d.i)];
    step(g, dt * 2.2, { needle: { x: p[0], y: p[1], down: true }, moveDt: dt });
    for (const ev of g.events.splice(0)) d.v.onEvent(ev);
    d.p = p; d.t = 0;
  }
  dctx.setTransform(k, 0, 0, k, 0, 0); dctx.clearRect(0, 0, S, S);
  d.v.draw(dctx, dt, null);
  if (!g.done && d.p) {
    const [x, y] = d.p, len = S * 0.4;
    dctx.strokeStyle = "#e9edf0"; dctx.lineWidth = 2; dctx.lineCap = "round";
    dctx.beginPath(); dctx.moveTo(x, y); dctx.lineTo(x - 0.45 * len, y - 0.89 * len); dctx.stroke();
  }
}

const starText = n => "★".repeat(n) + "☆".repeat(3 - n);
function showPick() {
  if (view) view.dispose();
  if (audio) { audio.gn.gain.setTargetAtTime(0, audio.ac.currentTime, 0.02); audio.lk.gn.gain.setTargetAtTime(0, audio.ac.currentTime, 0.02); }
  game = null; view = null; endAt = 0; $("hud").hidden = true; $("freeze").hidden = true; show("pick");
  $("modeHint").textContent = mode === "camera" ? "엄지·검지로 바늘 집기 · 손을 화면 쪽으로 콕 = 찌르기 · 입 벌리면 혀" : "손가락으로 긁기 · 톡 치면 찌르기 · 혀 버튼으로 핥기";
  const picks = $("picks"); picks.innerHTML = "";
  for (const [k, s] of Object.entries(SHAPES)) {
    const b = document.createElement("button"), best = bestOf(k);
    b.innerHTML = `<span class="ic"></span><small></small>`;
    b.querySelector(".ic").textContent = s.icon;
    const sm = b.querySelector("small"); sm.textContent = s.name;
    if (best) { sm.append(document.createElement("br")); const st = document.createElement("b"); st.textContent = best.stars ? `${starText(best.stars)} ${best.time.toFixed(1)}초` : `최고 ${best.pct}%`; sm.append(st); }
    b.setAttribute("aria-label", s.name + (best && best.stars ? ` 별 ${best.stars}개` : ""));
    b.onclick = () => begin(k); picks.appendChild(b);
  }
}

let resultShown = false, endAt = 0, winAt = 0, loseT = -1, moment = null, lickShot = null, lickFor = 0, capture = null;
let pose = null, ghost = null, beatAt = 0, note = null, candyAt = 0;
const taught = {};   // one-time in-game tips per page load
// the best win per shape leaves its needle's route (10 samples a second, candy-relative); the next rounds replay it
// as a faint rival needle (design r4: something to chase once three stars are in reach)
let trail = [], rival = null;
const loadRival = shape => {
  try { const v = JSON.parse(store(`dalgona.ghost.${mode}.${shape}`)); return v && Array.isArray(v.p) && v.p.length && Number.isFinite(v.t) ? v : null; }
  catch { return null; }
};
function recordTrail(needle) {
  const i = Math.floor(game.t * 10);
  if (trail.length > i || trail.length > 1500) return;
  while (trail.length < i) trail.push(trail.length ? trail[trail.length - 1] : null);   // a slow frame: hold the last point
  trail.push(needle && needle.down ? [+((needle.x - geom.cx) / geom.R).toFixed(3), +((needle.y - geom.cy) / geom.R).toFixed(3)] : null);
}
function drawRival() {
  if (!rival || !game.started || game.done) return;
  const i = Math.floor(game.t * 10), p = rival.p[i];
  if (i >= rival.p.length) {   // the ghost is already out (said for 3 s)
    if (game.t > rival.p.length / 10 + 3) return;
    ctx.save(); ctx.font = '15px "Do Hyeon", sans-serif'; ctx.textAlign = "center"; ctx.fillStyle = "rgba(255,255,255,.75)";
    ctx.shadowColor = "#000"; ctx.shadowBlur = 6; ctx.fillText(`내 최고 기록 ${rival.t.toFixed(1)}초 지남`, geom.cx, geom.cy - geom.R * 1.22); ctx.restore();
    return;
  }
  if (!p) return;
  const x = geom.cx + p[0] * geom.R, y = geom.cy + p[1] * geom.R;
  ctx.save(); ctx.globalAlpha = 0.32;
  drawNeedle({ x, y }, [0.45, 0.89], true);
  ctx.globalAlpha = 0.8; ctx.font = '12px "Do Hyeon", sans-serif'; ctx.fillStyle = "#FFC23D"; ctx.textAlign = "center";
  ctx.shadowColor = "#000"; ctx.shadowBlur = 4; ctx.fillText("내 최고", x, y + 18);
  ctx.restore();
}

function begin(shape) {
  touch.down = false;
  initAudio();   // resumes a context iOS suspended while we were away
  if (view) view.dispose();
  geom = freshGeom();   // a rotation during the last round must not leave the next one off screen
  game = createGame(shape, geom, Math.random, { camera: mode === "camera" }); view = createCandyView(game, DPR); resultShown = false; lickMode = false;
  endAt = 0; winAt = 0; loseT = -1; moment = null; lickShot = null; lickFor = 0; capture = null; crumbs.length = 0;
  pose = null; beatAt = 0; note = null; candyAt = 0; candyShot = null;
  trail = []; rival = loadRival(shape);
  // first round on this phone: a ghost needle shows the safe speed along the first stretch
  ghost = store("dalgona.seen") ? null : { t: 0 };
  show(null); $("hud").hidden = false; $("freeze").hidden = true;
  if (DEBUG) window.__g = game;
  $("lick").hidden = mode !== "touch"; $("lick").setAttribute("aria-pressed", "false"); $("lick").textContent = "혀로 핥기";
  $("hint").textContent = mode === "camera" ? "엄지와 검지를 모아 바늘을 집고 달고나에 대 보세요" : "선을 따라 천천히 긁거나 톡톡 찔러 보세요";
}

$("lick").addEventListener("pointerdown", e => e.stopPropagation());
$("lick").onclick = () => {
  lickMode = !lickMode; $("lick").setAttribute("aria-pressed", String(lickMode)); $("lick").textContent = lickMode ? "바늘 다시 잡기" : "혀로 핥기";
};
$("quit").addEventListener("pointerdown", e => e.stopPropagation());
$("quit").onclick = () => showPick();

function freezeFrame() {
  const f = $("freeze");
  if (mode !== "camera" || !video.videoWidth) return;
  f.width = cv.width; f.height = cv.height; const g = f.getContext("2d"); g.setTransform(DPR, 0, 0, DPR, 0, 0);
  videoCover(g); f.hidden = false;
}

// the screen as the player sees it (camera feed + candy + hand), for the result card
// the canvas layer alone (candy, no camera), for a card without a face
let candyShot = null;
function grabCandy() { const c = document.createElement("canvas"); c.width = cv.width; c.height = cv.height; c.getContext("2d").drawImage(cv, 0, 0); candyShot = c; }
function grab() {
  if (!(pose && pose.t0 && performance.now() >= pose.t0)) grabCandy();
  const c = document.createElement("canvas"); c.width = Math.round(W * DPR); c.height = Math.round(H * DPR);
  const g = c.getContext("2d"); g.setTransform(DPR, 0, 0, DPR, 0, 0);
  if (mode === "camera" && video.videoWidth) videoCover(g);
  else { const bg = g.createRadialGradient(W / 2, H * 0.38, 0, W / 2, H * 0.38, H * 0.7); bg.addColorStop(0, "#4a4038"); bg.addColorStop(0.55, "#2a241f"); bg.addColorStop(1, "#141110"); g.fillStyle = bg; g.fillRect(0, 0, W, H); }
  g.setTransform(1, 0, 0, 1, 0, 0); g.drawImage(cv, 0, 0);
  return c;
}

// 1080 x 1350 card: the moment, the verdict, the link
async function makeCard(faceOff, g0, m, lick, candy, geom, W) {
  try { await document.fonts.load('80px "Do Hyeon"'); } catch { /* fallback fonts */ }
  const CW = 1080, CH = 1350, c = document.createElement("canvas"); c.width = CW; c.height = CH;
  const g = c.getContext("2d"), lickShot = lick, candyShot = candy;
  const k = m.width / W, face = mode === "camera" && !faceOff;
  if (face) {   // 4:5 around the candy and whatever is above it (the face)
    const sw = m.width, sh = Math.min(m.height, sw * CH / CW);
    const sy = Math.max(0, Math.min(m.height - sh, (geom.cy + geom.R * 1.35) * k - sh));
    g.drawImage(m, 0, sy, sw, sh, 0, 0, CW, CH);
  } else {      // the candy alone: centred at 40 % height, 78 % of the card wide
    g.fillStyle = "#16120f"; g.fillRect(0, 0, CW, CH);
    const sw = geom.R * 2.75 * k, sh = sw * CH / CW, sx = geom.cx * k - sw / 2, sy = geom.cy * k - sh * 0.4;
    if (faceOff) {   // no face: the candy on a plain table, not the room
      const bg = g.createRadialGradient(CW / 2, CH * 0.4, 0, CW / 2, CH * 0.4, CH * 0.75); bg.addColorStop(0, "#4a4038"); bg.addColorStop(1, "#141110");
      g.fillStyle = bg; g.fillRect(0, 0, CW, CH);
      if (candyShot) g.drawImage(candyShot, sx, sy, sw, sh, 0, 0, CW, CH);
    } else g.drawImage(m, sx, sy, sw, sh, 0, 0, CW, CH);
  }
  if (face && lickShot && lickShot !== m) {   // the licking face, as an inset
    const r = 170, x = CW - r - 40, y = 230;
    g.save(); g.beginPath(); g.arc(x, y, r, 0, 7); g.clip();
    const ls = lickShot, lw = ls.width * 0.55, lx = ls.width * 0.225, ly = Math.max(0, ls.height * 0.3);
    g.drawImage(ls, lx, ly, lw, lw, x - r, y - r, 2 * r, 2 * r); g.restore();
    g.lineWidth = 8; g.strokeStyle = "#fff"; g.beginPath(); g.arc(x, y, r, 0, 7); g.stroke();
  }
  let sh2 = g.createLinearGradient(0, CH * 0.55, 0, CH); sh2.addColorStop(0, "rgba(0,0,0,0)"); sh2.addColorStop(1, "rgba(0,0,0,.85)");
  g.fillStyle = sh2; g.fillRect(0, CH * 0.55, CW, CH * 0.45);
  sh2 = g.createLinearGradient(0, 0, 0, 200); sh2.addColorStop(0, "rgba(0,0,0,.55)"); sh2.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = sh2; g.fillRect(0, 0, CW, 200);
  g.fillStyle = "#fff"; g.textBaseline = "alphabetic";
  g.font = '44px "Do Hyeon", sans-serif'; g.fillText("허공 달고나 · " + SHAPES[g0.shape].name, 56, 96);
  g.font = '96px "Do Hyeon", sans-serif'; g.fillText(label(g0), 56, CH - 210);
  g.font = '58px "Do Hyeon", sans-serif';
  g.fillStyle = g0.ok ? "#FFC23D" : "#FF6B81";
  g.fillText(g0.ok ? `${starText(stars(g0))}  ${g0.t.toFixed(1)}초` : loseLine(g0), 56, CH - 128);
  g.fillStyle = "rgba(255,255,255,.6)"; g.font = '28px "IBM Plex Sans KR", sans-serif';
  g.fillText("dot-connects.github.io/dot-lab", 56, CH - 64);
  return c;
}

// a loss line people send to tease, not a bare percentage
function loseLine(g0) {
  const p = g0.final;
  if (g0.cause === "melt") return `혀로 ${p}%까지 녹여 먹음`;
  if (g0.cause === "time") return `${p}%에서 시간 끝`;
  return p >= 85 ? `${p}%! 한 조각만 더` : p >= 50 ? `${p}%에서 쩍` : `${p}%에서 바로 쩍`;
}

let cardBlob = null, cardReady = Promise.resolve(), cardDone = () => {};
const IOS = /iP(hone|ad|od)/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
async function share() {
  const url = location.origin + location.pathname, g0 = game;
  if (!cardBlob) return;   // the button stays disabled until the card is ready
  const text = `허공 달고나 ${SHAPES[g0.shape].name} — ${label(g0)}${g0.ok ? ` ${starText(stars(g0))} ${g0.t.toFixed(1)}초` : ` ${g0.final}%`}`;
  try {
    const file = cardBlob && new File([cardBlob], "dalgona.png", { type: "image/png" });
    if (file && navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], text: text + " " + url }); return; }
    if (navigator.share) { await navigator.share({ text, url }); return; }
  } catch (e) { if (e && e.name === "AbortError") return; }
  if (cardBlob && !IOS) {   // iOS WebViews open a blob page or ignore it: only the save tip there
    const a = document.createElement("a"), u = URL.createObjectURL(cardBlob); a.href = u; a.download = "dalgona.png"; a.click();
    setTimeout(() => URL.revokeObjectURL(u), 10000);
  }
  // in-app browsers often ignore both: say how to keep it
  const tip = document.getElementById("saveTip"); if (tip) tip.hidden = false;
}

async function showResult() {
  const g = game, rec = { stars: stars(g), time: +g.t.toFixed(1), pct: g.final }, prev = bestOf(g.shape);
  const isNew = better(rec, prev);
  const lastTry = (() => { try { const v = JSON.parse(store("dalgona.last." + g.shape)); return v && Number.isFinite(v.pct) ? v : null; } catch { return null; } })();
  store("dalgona.last." + g.shape, JSON.stringify({ pct: g.final, ok: g.ok }));
  if (isNew && (rec.stars || rec.pct > 0)) store("dalgona.best2." + g.shape, JSON.stringify(rec));   // a 0 % loss is no record
  const oldRival = loadRival(g.shape);
  if (g.ok && (!oldRival || g.t < oldRival.t)) store(`dalgona.ghost.${mode}.${g.shape}`, JSON.stringify({ t: +g.t.toFixed(1), p: trail }));   // camera and touch race separately
  const best = isNew ? rec : prev;
  const r = $("result"); r.innerHTML = "";
  r.classList.toggle("win", g.ok);
  const el = (tag, cls, txt) => { const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };
  const img = el("img", "res-card"); img.alt = `결과 카드: ${label(g)}`;
  // the numbers live on the card; below it only what the card does not say (review r3)
  const openCracks = g.cracks.filter(c => !c.healed).length;
  const to3 = g.ok && rec.stars === 2 && clean(g) ? `${(g.t - g.limit * 0.5).toFixed(1)}초 더 빨랐으면 ★3` : "";
  const why = g.ok ? [to3, openCracks && `금 ${openCracks}개`, g.chips && `떨어진 조각 ${g.chips}개`, g.pokes && `찌른 횟수 ${g.pokes}`].filter(Boolean).join(" · ")
    : g.cause === "melt" ? "한 곳을 오래 핥아 모양이 녹았어요" : g.cause === "time" ? `${g.limit}초가 다 됐어요 · 원작은 10분` : "금이 모양 안으로 번졌어요";
  const sub = el("p", "res-sub", why);
  const bestLine = el("p", "res-best",
    !g.ok && lastTry && !lastTry.ok && g.final > lastTry.pct ? `지난번 ${lastTry.pct}% → 이번 ${g.final}%` :
    !prev ? "" : isNew ? (tone(1760, 0.2, 0.12, "sine", 0.6), "새 기록!") : prev.stars ? `지난 최고 ${starText(prev.stars)} ${prev.time.toFixed(1)}초` : `지난 최고 ${prev.pct}%`);
  const row = el("div", "row");
  const nextShape = ORDER[(ORDER.indexOf(g.shape) + 1) % ORDER.length];
  const next = g.ok && nextShape !== ORDER[0];
  const primary = next
    ? Object.assign(el("button", "go", `다음: ${SHAPES[nextShape].name} →`), { onclick: () => begin(nextShape) })
    : Object.assign(el("button", "go", "다시 하기"), { onclick: () => begin(g.shape) });
  const shareB = Object.assign(el("button", "sub", g.ok ? "자랑하기" : "친구한테 보내기"), { onclick: share, disabled: true });
  row.append(primary, shareB);
  const foot = el("div", "row foot");
  if (next) foot.append(Object.assign(el("button", "link", "다시 하기"), { onclick: () => begin(g.shape) }));
  foot.append(Object.assign(el("button", "link", "다른 모양"), { onclick: showPick }), Object.assign(el("a", "link", "시켜본 것 더 보기 →"), { href: "../../../" }));
  cardBlob = null;
  const m = moment || grab(), ls = lickShot instanceof HTMLCanvasElement ? lickShot : null, cs = candyShot;
  const shotGeom = { ...geom }, shotW = W;
  let renderSeq = 0;
  const render = async faceOff => {
    const tok = ++renderSeq;   // only the latest render may set the card (review r7: a late face PNG after "face off")
    cardBlob = null; shareB.disabled = true;
    cardReady = new Promise(res => { cardDone = res; });
    const card = await makeCard(faceOff, g, m, ls, cs, shotGeom, shotW);
    if (game !== g || tok !== renderSeq) return;   // a new round, or a newer render, while the fonts loaded
    img.onload = () => { img.style.animation = "none"; void img.offsetWidth; img.style.animation = ""; };   // drop in once it has a picture
    img.src = card.toDataURL("image/jpeg", 0.86);
    card.toBlob(b => {
      if (game === g && tok === renderSeq) {
        if (b) { cardBlob = b; cardDone(); shareB.disabled = false; }
        else saveTip.hidden = false;   // no PNG (memory): the card image above can still be long-pressed
      }
      card.width = 0;
    }, "image/png");
  };
  const saveTip = el("p", "res-sub", "공유가 안 되면 카드를 길게 눌러 저장하세요"); saveTip.id = "saveTip"; saveTip.hidden = true;
  const parts = [img, saveTip, sub, bestLine, row, foot];
  if (mode === "camera") {   // some people will not post their face
    const lab = el("label", "res-face"), cb = el("input"); cb.type = "checkbox";
    cb.onchange = () => render(cb.checked);
    lab.append(cb, document.createTextNode(" 카드에서 얼굴 빼기")); parts.splice(1, 0, lab);
  }
  r.append(...parts);
  $("hud").hidden = true;
  show("result");
  await render(false);
}

// back from another app: iOS may have paused the video or ended the track
let camRestarting = false;
document.addEventListener("visibilitychange", async () => {
  if (document.hidden) { touch.down = false; return; }
  if (mode !== "camera" || camRestarting) return;
  video.play().catch(() => {});
  const tr = camStream && camStream.getVideoTracks()[0];
  if (tr && (tr.readyState === "ended" || tr.muted)) {
    camRestarting = true;
    try { if (camStream) camStream.getTracks().forEach(t => t.stop()); camStream = await startCamera(video); } catch { /* stays on the last frame */ }
    camRestarting = false;
  }
});

// ---------- touch input ----------
const pos = e => { const b = app.getBoundingClientRect(); touch.x = e.clientX - b.left; touch.y = e.clientY - b.top; };
const touchTip = () => ({ x: touch.x, y: touch.y - (lickMode ? 0 : TOUCH_LIFT) });
app.addEventListener("pointerdown", e => {
  if (mode !== "touch" || !game || game.done || e.target.closest("button,a")) return;
  if (!e.isPrimary) return;   // a second finger or a palm does not move the needle (iOS review r6); a new first finger always can
  touch.id = e.pointerId;
  pos(e); touch.down = true; touch.t0 = performance.now(); touch.mt = 0; touch.fresh = true; touch.moved = 0;
  try { app.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
});
app.addEventListener("pointermove", e => {
  if (touch.down && e.pointerId !== touch.id) return;
  const px = touch.x, py = touch.y; pos(e);
  if (!touch.down) return;
  touch.moved += Math.hypot(touch.x - px, touch.y - py);
  // like camera frames: speed is measured between move events, frames without one hold the needle still
  const now = performance.now(); touch.mdt = Math.min(0.1, (now - (touch.mt || now)) / 1000); touch.mt = now; touch.fresh = true;
});
for (const t of ["pointerup", "pointercancel"]) app.addEventListener(t, e => {
  if (!touch.down || e.pointerId !== touch.id) return;
  touch.down = false;
  // a quick tap without sliding is a poke; holding and sliding is scraping
  if (game && !lickMode && performance.now() - touch.t0 < 220 && touch.moved < 8) { const p = touchTip(); poke(game, p.x, p.y); }
});
// in-app browsers: keep the gesture on the page (README "Touch input inside Threads / Instagram")
for (const t of ["touchstart", "touchmove"]) app.addEventListener(t, e => {
  if (game && !game.done && !e.target.closest("button,a")) e.preventDefault();
}, { passive: false });

// the room's light: average colour of the camera every half second, so the candy is not lit brighter than the room
const lc = document.createElement("canvas"); lc.width = lc.height = 16;
let lightAt = 0;
function sampleLight(now) {
  if (now - lightAt < 500 || !video.videoWidth || !view) return;
  lightAt = now;
  try {
    const g = lc.getContext("2d", { willReadFrequently: true }); g.drawImage(video, 0, 0, 16, 16);
    const d = g.getImageData(0, 0, 16, 16).data; let r = 0, gg = 0, b = 0;
    for (let i = 0; i < d.length; i += 4) { r += d[i]; gg += d[i + 1]; b += d[i + 2]; }
    const n = d.length / 4; r /= n; gg /= n; b /= n;
    const luma = (0.299 * r + 0.587 * gg + 0.114 * b) / 255, k = Math.min(1, 0.75 + 0.35 * luma), mx = Math.max(r, gg, b, 1);
    const ch = v => Math.round(255 * k * (0.75 + 0.25 * v / mx));
    view.setLight({ tint: `rgb(${ch(r)},${ch(gg)},${ch(b)})`, luma });
  } catch { /* a tainted or not-ready video: keep the last light */ }
}

// ---------- loop ----------
let last = performance.now(), lastStatus = "";
function frame(now) {
  requestAnimationFrame(frame);
  const dt = Math.max(0, Math.min(0.05, (now - last) / 1000)); last = now;   // the first rAF stamp can precede load
  if (!$("start").hidden) demoFrame(dt);
  let needle = null, lick = null, jitter = 0, moveDt = 0, s = null;
  const playing = game && !resultShown;
  if (mode === "camera" && tracker && playing) {   // no hand/face models outside a round: heat and battery
    s = tracker.update(now);
    // between camera frames the needle has not moved: the engine must not read that as stop-and-jump
    needle = s.needle && (s.fresh ? s.needle : { ...s.needle, held: true }); jitter = s.jitter; lick = s.lick;
    moveDt = s.vdt;
    sampleLight(now);
  } else if (mode === "touch") {
    const p = touchTip();
    // a short tap is a poke, not a scrape: the needle only scrapes once the finger has stayed down
    if (touch.down && !lickMode && (performance.now() - touch.t0 > 120 || touch.moved > 8)) {
      needle = { ...p, down: true, held: !touch.fresh && !!game && !!game.last }; moveDt = touch.mdt || 0;
    }
    touch.fresh = false;
    if (touch.down && lickMode) lick = p;
  }
  // camera: licking only counts once the round has started (an open mouth does not start it; review r5)
  const lickOn = lick && Math.hypot(lick.x - geom.cx, lick.y - geom.cy) < geom.R && (mode !== "camera" || (game && game.started));
  const lickEarly = lick && mode === "camera" && game && !game.started;
  if (game) {
    const { carve } = step(game, dt, { needle, jitter, lick, moveDt });
    if (game.started && !game.done) recordTrail(needle);
    for (const ev of game.events.splice(0)) {
      if (ev.type === "win") {   // a beat of stillness before the break: scraping falls silent, a faint high note
        winAt = now + 350; buzz(30); SFX.hush();
        if (audio) { audio.gn.gain.setTargetAtTime(0, audio.ac.currentTime, 0.02); audio.lk.gn.gain.setTargetAtTime(0, audio.ac.currentTime, 0.02); }
        continue;
      }
      view.onEvent(ev);
      if (ev.type === "poke") { if (ev.corner) { SFX.corner(); note = { text: "모서리 콕! 한 번에 파였어요", until: now + 1200, good: true }; } else SFX.poke(ev.hit); const p = game.pits[game.pits.length - 1]; spawnCrumbs(p[0], p[1], ev.hit ? 7 : 3, true); }
      else if (ev.type === "segment") SFX.segment(game.segs);
      else if (ev.type === "heal") { SFX.heal(); note = { text: "실금이 아물었어요", until: now + 1500, good: true }; }
      else if (ev.type === "crack") {
        SFX.hair();
        if (mode === "camera" && !game.licks && !taught.lick) { taught.lick = true; note = { text: "입을 크게 벌려 핥으면 실금이 아물어요", until: now + 2600, good: true }; }
      }
      else if (ev.type === "spread") SFX.spread();
      else if (ev.type === "chip") {
        SFX.chip(); shake(3);
        if (!taught.chip) { taught.chip = true; note = { text: "조각이 떨어졌어요 · 다음 금은 안쪽으로 가기 쉬워요", until: now + 2400 }; }
      }
      else if (ev.type === "lose") { SFX.lose(); shake(10); loseT = 0; }
    }
    if (winAt && now >= winAt) {
      winAt = 0; view.onEvent({ type: "win" }); SFX.snap(); shake(6); setTimeout(SFX.win, 250);
      // camera: the freed shape flies to your fingers for a photo; touch: the shot is the moment it parts
      if (mode === "camera") { candyAt = now + 500; pose = { t0: now + 1100, cap: now + 1100 + 3300, x: geom.cx, y: geom.cy, k: 1 }; }
      else capture = now + 500;
    }
    if (pct90() && !game.done && now >= beatAt) { beatAt = now + 750 - (percent(game) - 90) * 30; SFX.beat(); }   // speeds up to the end
    if (loseT >= 0) { loseT += dt; if (!moment && loseT > 0.55) capture = now; }
    if (carve > 0.05 && needle && !needle.held) spawnCrumbs(needle.x, needle.y, Math.random() < carve ? 1 : 0, false);
    if (lickOn) { lickFor += dt; if (!lickShot && lickFor > 0.8 && mode === "camera") lickShot = "next"; } else lickFor = 0;
    const still = mode === "touch" && performance.now() - (touch.mt || 0) > 120;
    if (audio) {
      // glide, never jump: a cut to 0 clicks (juice r6); grains of sugar ticking on top while carving hard
      const t = audio.ac.currentTime, target = game.done || still ? 0 : carve * 0.22;
      audio.gn.gain.setTargetAtTime(target, t, target > audio.gn.gain.value ? 0.015 : 0.04);
      audio.bp.frequency.setTargetAtTime(2400 + carve * 1800, t, 0.03);
      if (target > 0.066 && Math.random() < carve * 0.5) noise(5000 + Math.random() * 2500, 0.012, 0.18 * carve, "highpass");
      // licking: a wet low rustle with a lap rhythm; melting goes duller and louder
      const melting = lickOn && wetAt(game, lick.x, lick.y) > 0.95;
      audio.lk.gn.gain.setTargetAtTime(lickOn && !game.done ? (melting ? 0.2 : 0.12 + 0.06 * Math.sin(now / 90)) : 0, t, 0.05);
      audio.lk.fl.frequency.setTargetAtTime(melting ? 500 : 900, t, 0.1);
    }
    if (game.started) $("hint").textContent = "";
    const left = Math.max(0, Math.ceil(game.limit - game.t));
    const tm = $("timer"); tm.textContent = `0${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
    tm.classList.toggle("hurry", left <= 10 && game.started);
    const pct = percent(game);
    $("pc").textContent = pct + "%";
    const open = game.cracks.filter(c => !c.done);
    const stg = open.reduce((a, c) => Math.max(a, crackStage(game, c) + 1), 0);
    const lk = lickOn && wetAt(game, lick.x, lick.y);
    const hot = !game.done && game.heat > 0.6;
    const showNote = note && now < note.until && !game.done && game.stress < RULES.HAIR;
    const st = showNote ? note.text
      : game.done ? (game.ok ? (pose && pose.text ? pose.text : "떼어냈어요!") : game.cause === "melt" ? "녹아서 뚫렸어요" : game.cause === "time" ? "시간 끝" : "깨졌어요")
      : game.stress >= RULES.HAIR ? (open.some(c => c.inward) ? "금이 모양 쪽으로! 손 멈춰요" : "금이 자라요 · 손 멈춰요")
      : lickOn ? (lk > 0.95 ? "녹고 있어요 · 한곳만 핥으면 구멍" : "핥는 중 · 핥은 곳은 덜 깨져요")
      : lickEarly ? "먼저 바늘로 긁어야 혀가 닿아요"
      : lick ? "입을 달고나에 더 가까이"
      : stg ? (open.some(c => c.inward) ? "안쪽 금 · 더 자라면 탈락" : "바깥 금 · 조각만 떨어져요")
      : mode === "camera" && s && !s.hand ? "손이 안 보여요 · 화면 안으로"
      : mode === "camera" && s && !s.pinched ? "엄지·검지를 붙여 바늘 집기"
      : hot ? "찌르기 과열 · 잠깐 긁기만"
      : game.stress > 0.3 ? "조심 · 압력이 올라요"
      : pct >= 90 ? "거의 다 됐어요 · 숨 참고"
      : game.started ? "천천히 긁는 중" : `${SHAPES[game.shape].name} · 첫 긁기부터 시간이 가요`;
    if (st !== lastStatus) { $("sw").textContent = st; lastStatus = st; }
    $("sw").className = showNote ? (note.good ? "good" : "warn") : game.stress >= RULES.HAIR || (game.done && !game.ok) ? "bad" : game.done ? "good" : hot ? "warn" : "";
    if (game.done && !resultShown && !endAt && !(game.ok && mode === "camera")) endAt = now + (game.ok ? 2400 : 2000);
    if (pose && now >= pose.t0 && !pose.done) {
      const grip = s && s.hand && s.grip, target = grip ? { x: grip[0], y: grip[1] - geom.R * 0.35, k: 0.62 } : { x: geom.cx, y: geom.cy - geom.R * 0.2, k: 0.9 };
      const f = 1 - Math.exp(-dt * 7);
      pose.x += (target.x - pose.x) * f; pose.y += (target.y - pose.y) * f; pose.k += (target.k - pose.k) * f;
      view.setPose({ x: pose.x, y: pose.y, k: pose.k, rot: Math.sin(now / 400) * 0.04 });
      if (!grip && (pose.waited || 0) < 3000) { pose.waited = (pose.waited || 0) + dt * 1000; pose.cap += dt * 1000; }
      const n = Math.ceil((pose.cap - now) / 1000);
      if (n !== pose.lastN && n > 0 && !(!grip && pose.waited < 3000)) { pose.lastN = n; SFX.tick(); }
      pose.text = !grip && pose.waited < 3000 ? "손을 보여 주면 모양이 따라가요" : n > 0 ? `얼굴 옆에 들어 보세요 · ${n}` : "";
      if (now >= pose.cap) { pose.done = true; moment = grab(); SFX.shutter(); flashAt = now; endAt = now + 600; }
    }
    if (endAt && now >= endAt) { endAt = 0; if (!moment) moment = grab(); resultShown = true; freezeFrame(); showResult(); }
  }

  ctx.setTransform(DPR, 0, 0, DPR, 0, 0); ctx.clearRect(0, 0, W, H);
  if (now < shakeUntil) { const a = shakeAmp * (shakeUntil - now) / 150; ctx.translate((Math.random() - 0.5) * a, (Math.random() - 0.5) * a); }
  if (!game || resultShown) { if (DEBUG && s) debug(s); return; }
  view.draw(ctx, dt, game.done ? null : percent(game) / 100);
  if (game.done && !game.ok && loseT > 0.9 && game.final >= 50) drawMissing(now);
  drawCrumbs(dt);
  if (!game.done) {
    if (s) {
      if (s.tongue) drawTongue(s.tongue, !!lickOn, now);
      if (s.hand) handFront(s.hand, s.hs);
      if (s.needle) { drawNeedle(s.needle, s.dir || [0.55, 0.83], !!s.needle.down); drawStressRing(s.needle); }
    } else if (mode === "touch") {
      if (lickMode) { if (touch.down) drawTongueFaded({ base: [touch.x, touch.y - geom.R * 0.7], dir: [0, 1], len: geom.R * 0.7, width: geom.R * 0.34, fadeRoot: true }, now); }
      else if (touch.down) { const p = touchTip(); drawNeedle(p, [0.45, 0.89], true); drawStressRing(p); }
    }
  }
  if (loseT > 0.65) drawStamp(loseT - 0.65);
  if (ghost && !game.started) drawGhost(dt);
  else if (ghost && game.started) { ghost = null; store("dalgona.seen", "1"); }
  drawRival();
  if (flashAt && now - flashAt < 250) { ctx.fillStyle = `rgba(255,255,255,${0.8 * (1 - (now - flashAt) / 250)})`; ctx.fillRect(0, 0, W, H); }
  if (lickShot === "next") lickShot = grab();
  if (capture && now >= capture) { capture = null; moment = grab(); }
  if (candyAt && now >= candyAt) { candyAt = 0; grabCandy(); }
  if (DEBUG && s) debug(s);
}

let flashAt = 0;
const pct90 = () => game && percent(game) >= 90;
// first round: a ghost needle traces the first stretch at a safe pace, then waits for the player
function drawGhost(dt) {
  ghost.t += dt;
  const path = game.paths[0], idx = path.map((_, i) => i).filter(i => game.segOf[0][i] === 0);
  const speed = RULES.V_SAFE * game.vK * 0.7 * geom.R, len = idx.length * geom.R * 0.02, cyc = len / speed + 0.8;
  const u = Math.min(1, (ghost.t % cyc) / (len / speed)), p = path[idx[Math.floor(u * (idx.length - 1))]];
  ctx.save(); ctx.globalAlpha = 0.55;
  drawNeedle({ x: p[0], y: p[1] }, [0.45, 0.89], true);
  ctx.globalAlpha = 0.9; ctx.font = '17px "Do Hyeon", sans-serif'; ctx.textAlign = "center"; ctx.fillStyle = "#fff";
  ctx.shadowColor = "rgba(0,0,0,.8)"; ctx.shadowBlur = 6; ctx.fillText("이 속도로", p[0], p[1] - 30);
  ctx.restore();
}

function debug(s) {
  const d = s.det;
  const lines = [`fps ${s.fps.toFixed(0)}  hand ${s.hand ? s.hs.toFixed(0) : "-"}  pinch ${s.pinched ? "Y" : "n"}`,
    `rate ${d.rate.toFixed(2)}  ${d.armed ? "ARMED" : "wait"}  pokes ${s.pokes}`,
    `jaw ${s.jaw.toFixed(2)}  jitter ${s.jitter.toFixed(1)}`,
    game ? `stress ${game.stress.toFixed(2)}  cracks ${game.cracks.length}  ${percent(game)}%` : ""];
  ctx.save(); ctx.fillStyle = "rgba(0,0,0,.6)"; ctx.fillRect(8, 120, 240, 84);
  ctx.fillStyle = "#9f9"; ctx.font = "12px monospace"; lines.forEach((l, i) => ctx.fillText(l, 14, 138 + i * 18));
  if (s.hand) { ctx.fillStyle = "#0f0"; for (const p of s.hand) { ctx.beginPath(); ctx.arc(p[0], p[1], 2.5, 0, 7); ctx.fill(); } }
  ctx.restore();
}

resize();
requestAnimationFrame(frame);
