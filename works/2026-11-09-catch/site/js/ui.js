// The game: start screen, mode switch, the ball (drawn in 2D over the 3D scene), throw -> hit -> shakes -> caught /
// escaped, the counter and the catch card (dex, detail, card PNG and export live in dex.js).
import * as ar from "./ar.js";
import * as dex from "./dex.js";
import { grade, odds, roll, ringAt, rng, CRIT_RING } from "./catch.js";
import { release, flight, sweep, turnOf, curveOf, coverMap, FLIGHT_MS, END_SCALE } from "./geom.js";
import { modelCreature, spriteCreature, VIRTUAL } from "./creature.js";
import { LABELS, LABEL_KO, suggest, count } from "./moves.js";
import * as seg from "./seg.js";
import * as store from "./store.js";

const $ = id => document.getElementById(id);
const Q = new URLSearchParams(location.search);
const rnd = Q.has("seed") ? rng(+Q.get("seed") || 0) : Math.random;
const ART = dex.ART;
const GRADE_KO = { hit: "맞았다", nice: "좋아", great: "멋져", excellent: "완벽해" };
const STAY_MS = 30000;   // a creature nobody catches leaves after 30 s
// ?slow=N (tests): the game clock runs N times slower, so a slow headless frame can screenshot a 0.3 s moment
const SLOW = Math.max(1, +Q.get("slow") || 1), clock = () => performance.now() / SLOW;

const fx = $("fx"), g = fx.getContext("2d");
let W = 1, H = 1;
function size() {
  W = innerWidth; H = innerHeight;
  const d = Math.min(2, devicePixelRatio || 1);
  fx.width = Math.round(W * d); fx.height = Math.round(H * d); g.setTransform(d, 0, 0, d, 0, 0);
}
addEventListener("resize", size);

// ---------- state ----------
const G = {
  mode: null, phase: "wait",   // wait | hand | drag | fly | catch | card
  mon: null,                   // {id, name, img, t0}
  ball: { x: 0, y: 0, r: 28 },
  path: [], turn: 0, armed: 0, // finger path while dragging, its signed turn (degrees), armed curve sign
  th: null, t0: 0, prevU: 0, ring: 1, ringT0: 0, handT0: 0,
  res: null,                   // {grade, curve, p, critical, caught, shakes, land, ground, center, t0}
  ghosts: [],                  // missed balls rolling away while the next one rises into the hand
  parts: [], pops: [],         // particles, text pops
  burst: null,                 // {t0}: the creature springing back out of the ball
  target: null, look: null,
};
const rest = () => ({ x: W / 2, y: H * 0.78 });
const ballR = () => Math.max(24, Math.min(34, W * 0.075));

// ---------- creatures ----------
let spawning = false;
async function spawn() {
  if (spawning) return;
  spawning = true;   // a creature that leaves stays in view (and the ball in hand) until the next one has loaded
  const own = dex.mine(), pool = own.length ? own : VIRTUAL;   // the operator's own pictures replace the 3D ones
  const spec = pool[Math.floor(rnd() * pool.length)], id = spec.id;
  try {
    const img = await ar.loadImg(ART(id));
    G.mon = { id, name: spec.name, img, t0: clock() };
    ar.setCreature(own.length ? spriteCreature(img) : modelCreature(spec)); ar.place();
    G.ringT0 = clock();
    if (G.phase === "wait") { G.phase = "hand"; G.handT0 = clock(); }
  } catch {
    note("녀석을 불러오지 못했어요 · 인터넷 연결을 확인해 주세요");
    setTimeout(() => { spawning = false; spawn(); }, 3000);
    return;
  }
  spawning = false;
}
// A real dog / cat / person in the camera beats the virtual one (which hides); several = the one nearest the centre.
const REAL_KO = { dog: "강아지", cat: "고양이", person: "사람" };
function currentTarget(t) {
  const v = $("video");
  G.reals = G.mode === "camera" && v.videoWidth ? seg.targets().map(r => {
    const m = coverMap(v.videoWidth, v.videoHeight, W, H), [x0, y0] = m([r.x * v.videoWidth, r.y * v.videoHeight]),
      [x1, y1] = m([(r.x + r.w) * v.videoWidth, (r.y + r.h) * v.videoHeight]);
    return { box: { x: x0, y: y0, w: x1 - x0, h: y1 - y0 }, center: { x: (x0 + x1) / 2, y: (y0 + y1) / 2 }, onScreen: true, real: r.kind, n: r };
  }) : [];
  if (!G.reals.length) return t;
  const d = r => Math.hypot(r.center.x - W / 2, r.center.y - H / 2);
  return G.reals.reduce((a, b) => (d(b) < d(a) ? b : a));
}

// ---------- input ----------
const app = $("app");
for (const t of ["touchstart", "touchmove"])   // keep the gesture in the page inside Threads / Instagram (README)
  app.addEventListener(t, e => { if (!e.target.closest("button,a,input,#dex")) e.preventDefault(); }, { passive: false });
document.body.style.minHeight = "calc(100% + 2px)";
const nudge = () => { if (scrollY < 1) scrollTo(0, 1); };
addEventListener("load", nudge); addEventListener("scroll", nudge, { passive: true });
// haptics only after a first tap: Chrome logs an error for vibrate() before any user activation (iOS has none at all)
const vib = p => { try { if (navigator.userActivation?.hasBeenActive !== false) navigator.vibrate?.(p); } catch { /* no vibrate */ } };

app.addEventListener("pointerdown", e => {
  if (e.target.closest("button,a,#card,#start,#sheet,#dex")) return;
  const b = G.ball;
  if (G.phase === "hand" && Math.hypot(e.clientX - b.x, e.clientY - b.y) < b.r * 1.9) {
    G.phase = "drag"; G.path = [{ x: e.clientX, y: e.clientY, t: e.timeStamp }]; G.turn = 0; G.armed = 0;
    app.setPointerCapture(e.pointerId);
  } else if (G.mode === "screen" && !ar.hasGyro()) {
    G.look = { x: e.clientX, y: e.clientY, id: e.pointerId };
    app.setPointerCapture(e.pointerId);
  }
});
app.addEventListener("pointermove", e => {
  if (G.phase === "drag") {
    // every sample with its own time: moves are batched per frame, and a slow frame must not look like a slow flick
    const cs = e.getCoalescedEvents?.();
    for (const c of cs && cs.length ? cs : [e]) G.path.push({ x: c.clientX, y: c.clientY, t: c.timeStamp });
    G.ball.x = e.clientX; G.ball.y = e.clientY;
    G.turn = turnOf(G.path);
    const arm = curveOf(G.path);
    if (arm && !G.armed) { vib(10); burst(G.ball.x, G.ball.y, 14, { col: GOLD, speed: 160, kind: "star" }); }   // the curve is armed
    G.armed = arm;
  } else if (G.look && G.look.id === e.pointerId) {
    ar.look((e.clientX - G.look.x) * 0.0045, (e.clientY - G.look.y) * 0.0045);
    G.look.x = e.clientX; G.look.y = e.clientY;
  }
});
const aimable = t => t && t.onScreen && !t.behind;
function up(e) {
  if (G.look && G.look.id === e.pointerId) G.look = null;
  if (G.phase !== "drag") return;
  const now = clock();
  G.path.push({ x: e.clientX, y: e.clientY, t: e.timeStamp });
  const th = release(G.path, W, H, rest(), aimable(G.target) ? G.target.box : null);
  G.lastThrow = { th, n: G.path.length, span: e.timeStamp - G.path[0].t };
  G.armed = 0;
  if (!th) { G.phase = "hand"; return; }   // not a throw: the ball goes back to the hand
  G.th = th; G.t0 = now; G.prevU = 0; G.ring = ringAt(now - G.ringT0); G.phase = "fly";
  G.aim = G.target?.real ? G.target : null;   // thrown at a real one: it stays the target even if a find misses mid-flight
}
app.addEventListener("pointerup", up);
app.addEventListener("pointercancel", e => { if (G.phase === "drag") { G.phase = "hand"; G.armed = 0; } if (G.look) G.look = null; });

// ---------- the throw flies, hits or misses ----------
const ringMax = t => Math.min(t.box.w, t.box.h) * 0.4;   // ring diameter = 80 % of the short side
function flyStep(now) {
  const u = Math.min(1, (now - G.t0) / FLIGHT_MS);
  const t = G.aim ? (G.target?.real === G.aim.real ? G.target : G.aim) : G.target, ok = aimable(t);
  const ringC = ok ? { x: t.center.x, y: t.center.y, r: ringMax(t) * G.ring } : { x: 0, y: 0, r: 0 };
  const h = ok ? sweep(G.th, G.prevU, u, t.box, ringC, W, H) : null;
  G.prevU = u;
  if (h) return hitAt(now, h, t);
  const b = flight(G.th, u, W, H);
  if (G.th.curve) sparkle(b.x, b.y, ballR() * b.s);
  drawBall(b.x, b.y, ballR() * b.s, u * 12 * (G.th.curve || 1), G.th.curve ? 0.7 : 0);
  if (u < 1) return;
  // miss: the ball rolls off and fades; the next one rises into the hand
  G.aim = null;
  const prev = flight(G.th, 0.95, W, H);
  G.ghosts.push({ x: b.x, y: b.y, s: END_SCALE, t0: now, vx: (b.x - prev.x) / (0.05 * FLIGHT_MS), vy: (b.y - prev.y) / (0.05 * FLIGHT_MS) });
  pop("빗나감", "", b.x, b.y - 20, "#e8eef5", 0.8);
  toHand(now);
}
function hitAt(now, h, t) {
  G.aim = null;
  const gr = grade({ ...h, ring: G.ring }), { p, critical } = odds({ ...h, ring: G.ring, curve: G.th.curve }), r = roll(p, rnd, critical);
  G.res = { grade: gr, curve: G.th.curve, p, ...r, land: h.at, ground: t.box.y + t.box.h * 1.04, center: { ...t.center }, t0: now };
  // a real one: freeze this frame and start the cut-out now; the roll above already decided, the cut cannot change it
  if (t.real) G.res.real = { kind: t.real, n: t.n, cut: seg.cut({ ...t.n, kind: t.real }) };
  G.phase = "catch";
  vib(15);
  burst(h.at.x, h.at.y, 10, { col: "#ffffff", speed: 220, kind: "dot" });
  ringFx(h.at.x, h.at.y, GRADE_COL[gr]);
  pop(`${GRADE_KO[gr]}!${G.th.curve ? " 커브볼" : ""}`, critical ? "크리티컬! 확실히 잡힌다" : `잡힐 확률 ${Math.round(p * 100)}%`, h.at.x, h.at.y - 30, GRADE_COL[gr], critical ? 1.25 : 1);
}
const toHand = (now, delay = 150) => { G.phase = "hand"; G.handT0 = now + delay; };

// catch timeline (ms from the hit): ball bounces up, the creature turns to light and goes in, the ball drops with one
// bounce, then 0.9 s per wobble (0.5 s of rocking, 0.4 s still). 3 wobbles ~ 3.75 s, a critical 1 wobble ~ 1.95 s.
const SH = { bounce: 250, absorb: 450, drop: 350, each: 900, rock: 500 };
const SHAKE0 = SH.bounce + SH.absorb + SH.drop;
function catchStep(now) {
  const R = G.res, T = now - R.t0, endT = SHAKE0 + R.shakes * SH.each - 250;
  const shakeI = Math.floor((T - SHAKE0) / SH.each);
  if (shakeI >= 0 && shakeI < R.shakes && R.vib !== shakeI) { R.vib = shakeI; vib(30); }
  if (T < endT) return;
  const b = catchBall(now);
  if (R.caught) {
    G.phase = "caught"; R.doneT = now;
    vib([20, 50, 40]);
    burst(b.x, b.y - b.r, R.critical ? 30 : 18, { col: GOLD, speed: R.critical ? 340 : 260, kind: "star", up: true });
    burst(b.x, b.y - b.r, 12, { col: "#ffffff", speed: 200, kind: "dot", up: true });
    ringFx(b.x, b.y - b.r, GOLD);
    toast(R.critical ? "크리티컬 캐치!" : "잡았다!", R.critical ? "한 번에 쏙" : "");
    if (R.real) setTimeout(() => who(R), 1200 * SLOW);
    else store.catchVirtual({ pokeId: G.mon.id, name: G.mon.name, grade: R.grade }).then(async out => {
      await counter();
      setTimeout(() => showCard(out), Math.max(0, 1200 - (clock() - R.doneT)) * SLOW);
    });
  } else {
    // break-out: the ball pops open in a flash, the creature springs back out with a squash
    vib(60);
    burst(b.x, b.y - b.r, 22, { col: "#fff6dc", speed: 300, kind: "dot" });
    ringFx(b.x, b.y - b.r, "#ffffff");
    G.burst = { t0: now }; G.res = null; G.ringT0 = now;
    toast("앗, 튀어나왔다!", "다시 던져요");
    toHand(now, 350);
  }
}
/** Where the caught ball is now: {x, y (bottom of the ball), r, rot, rocking}. */
function catchBall(now) {
  const R = G.res, T = now - R.t0, rr = ballR() * END_SCALE, hover = R.land.y - 46, ground = R.ground;
  let y, rot = 0;
  if (T < SH.bounce) y = R.land.y - 46 * easeOut(T / SH.bounce);
  else if (T < SH.bounce + SH.absorb) y = hover + Math.sin((T - SH.bounce) / 70) * 1.5;
  else if (T < SHAKE0) {   // drop, then one small bounce
    const u = (T - SH.bounce - SH.absorb) / SH.drop;
    y = u < 0.7 ? hover + (ground - rr - hover) * (u / 0.7) ** 2 : ground - rr - 16 * 4 * ((u - 0.7) / 0.3) * (1 - (u - 0.7) / 0.3);
  } else y = ground - rr;
  const ts = T - SHAKE0, i = Math.floor(ts / SH.each), f = (ts - i * SH.each) / SH.rock;
  const rocking = ts > 0 && G.phase === "catch" && i < R.shakes && f < 1;
  if (rocking) rot = 0.314 * Math.sin(f * Math.PI * 2) * (1 - f * 0.6);   // +-18 degrees
  return { x: R.land.x, y: y + rr, r: rr, rot, rocking };
}

function monPose(now) {
  if (G.res ? G.res.real : G.aim || G.target?.real) return { scale: 0, hidden: true, hold: true };   // a real one is the target
  if (G.phase === "catch") {
    const T = now - G.res.t0, flash = Math.min(1, Math.max(0, (T - 150) / 200)), u = (T - SH.bounce) / SH.absorb;
    if (u < 0.3) return { flash, hold: true, squash: u > 0 ? -0.08 : 0 };
    if (u < 1) return { flash: 1, scale: 1 - easeIn((u - 0.3) / 0.7), squash: 0.25 * (u - 0.3), hold: true };
    return { scale: 0, hidden: true, hold: true };
  }
  if (G.phase === "caught" || G.phase === "card") return { scale: 0, hidden: true, hold: true };
  if (G.burst) {   // springs back out: small and flat -> overshoot tall -> settles
    const u = (now - G.burst.t0) / 300;
    if (u >= 1.6) G.burst = null;
    else return { scale: u < 1 ? easeOutBack(u) : 1, squash: u < 1 ? -0.35 * Math.cos(u * Math.PI * 1.5) * (1 - u) : 0, flash: Math.max(0, 1 - u * 1.4), hold: u < 1 };
  }
  return {};
}

// ---------- drawing ----------
const easeOut = u => 1 - (1 - u) * (1 - u), easeIn = u => u * u * u;
const easeOutBack = u => { const c = 1.7; return 1 + (c + 1) * (u - 1) ** 3 + c * (u - 1) ** 2; };
const GOLD = "#ffc83d";
const GRADE_COL = { hit: "#ffffff", nice: "#9ff0c0", great: "#8fd3ff", excellent: GOLD };

/**
 * Our own ball: a D:OT-blue sphere with a cream S-seam and two gold dots (the colon of "D:OT").
 * glow 0..1 = gold halo (curve armed / flying curve / caught), lit = the dots light up (wobble, caught).
 */
function drawBall(x, y, r, rot = 0, glow = 0, lit = 0) {
  g.save(); g.translate(x, y);
  if (glow > 0) {
    const h = g.createRadialGradient(0, 0, r * 0.6, 0, 0, r * 2.1);
    h.addColorStop(0, `rgba(255,214,90,${0.55 * glow})`); h.addColorStop(1, "rgba(255,214,90,0)");
    g.fillStyle = h; g.beginPath(); g.arc(0, 0, r * 2.1, 0, Math.PI * 2); g.fill();
  }
  g.rotate(rot);
  const body = g.createRadialGradient(-r * 0.35, -r * 0.4, r * 0.1, 0, 0, r);
  body.addColorStop(0, "#86adff"); body.addColorStop(0.5, "#2f6fe4"); body.addColorStop(1, "#16357f");
  g.beginPath(); g.arc(0, 0, r, 0, Math.PI * 2); g.fillStyle = body; g.fill();
  g.save(); g.clip();
  g.beginPath();
  for (let i = 0; i <= 24; i++) { const sx = -r * 1.1 + 2.2 * r * i / 24; g[i ? "lineTo" : "moveTo"](sx, 0.34 * r * Math.sin(Math.PI * sx / r)); }
  g.lineWidth = r * 0.15; g.strokeStyle = "#fff1d2"; g.stroke();
  g.restore();
  g.fillStyle = lit ? "#fff7cf" : GOLD;
  if (lit) { g.shadowColor = GOLD; g.shadowBlur = r * 0.8 * lit; }
  for (const dy of [-0.5, -0.12]) { g.beginPath(); g.arc(r * 0.4, r * dy, r * 0.12, 0, Math.PI * 2); g.fill(); }
  g.shadowBlur = 0;
  const gloss = g.createRadialGradient(-r * 0.38, -r * 0.48, 0, -r * 0.38, -r * 0.48, r * 0.55);
  gloss.addColorStop(0, "rgba(255,255,255,.75)"); gloss.addColorStop(1, "rgba(255,255,255,0)");
  g.beginPath(); g.arc(0, 0, r, 0, Math.PI * 2); g.fillStyle = gloss; g.fill();
  g.lineWidth = Math.max(1, r * 0.05); g.strokeStyle = "rgba(10,24,60,.55)"; g.stroke();
  g.restore();
}
function drawShadow(x, y, r, a = 0.3) {
  g.fillStyle = `rgba(0,0,0,${a})`; g.beginPath(); g.ellipse(x, y, r * 0.85, r * 0.24, 0, 0, Math.PI * 2); g.fill();
}
// the ring: a circle of dots at its biggest, and the shrinking ring coloured by the catch chance of a ring hit right now
// (red -> yellow -> green); under 35 % it turns gold: the critical window
function drawRing(t, now) {
  const m = ringMax(t), k = G.phase === "fly" ? G.ring : ringAt(now - G.ringT0), r = m * k;
  g.fillStyle = "rgba(255,255,255,.75)";
  for (let i = 0; i < 28; i++) { const a = i / 28 * Math.PI * 2 + now / 4000; g.beginPath(); g.arc(t.center.x + Math.cos(a) * m, t.center.y + Math.sin(a) * m, 1.8, 0, Math.PI * 2); g.fill(); }
  const crit = k < CRIT_RING, p = odds({ onBody: true, inRing: true, ring: k, curve: G.armed }).p;
  g.lineWidth = crit ? 5.5 : 4.5;
  g.strokeStyle = crit ? "#ffd84d" : `hsl(${Math.round(120 * Math.min(1, Math.max(0, (p - 0.4) / 0.55)))},90%,58%)`;
  g.shadowColor = crit ? "rgba(255,200,60,.9)" : "rgba(0,0,0,.45)"; g.shadowBlur = crit ? 14 : 6;
  g.beginPath(); g.arc(t.center.x, t.center.y, r, 0, Math.PI * 2); g.stroke();
  g.shadowBlur = 0;
}

// ---------- particles and text pops ----------
function burst(x, y, n, { col, speed, kind, up = false }) {
  for (let i = 0; i < n; i++) {
    const a = up ? -Math.PI / 2 + (Math.random() - 0.5) * 2.4 : Math.random() * Math.PI * 2, v = speed * (0.45 + Math.random() * 0.75);
    G.parts.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, t0: clock(), life: 500 + Math.random() * 450, col, kind, size: kind === "star" ? 5 + Math.random() * 5 : 2 + Math.random() * 2.5, spin: Math.random() * 6 });
  }
}
const ringFx = (x, y, col) => G.parts.push({ x, y, vx: 0, vy: 0, t0: clock(), life: 420, col, kind: "ring", size: 10 });
let lastSpark = 0;
function sparkle(x, y, r) {   // the armed / flying curve leaves gold sparkles
  const now = clock();
  if (now - lastSpark < 30) return;
  lastSpark = now;
  const a = Math.random() * Math.PI * 2;
  G.parts.push({ x: x + Math.cos(a) * r * 1.2, y: y + Math.sin(a) * r * 1.2, vx: Math.cos(a) * 30, vy: Math.sin(a) * 30 - 20, t0: now, life: 450, col: GOLD, kind: "star", size: 3 + Math.random() * 4, spin: 0 });
}
function star(x, y, s, rot) {   // four-point sparkle
  g.save(); g.translate(x, y); g.rotate(rot); g.beginPath();
  for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4, d = i % 2 ? s * 0.32 : s; g[i ? "lineTo" : "moveTo"](Math.cos(a) * d, Math.sin(a) * d); }
  g.closePath(); g.fill(); g.restore();
}
function drawParts(now, dt) {
  G.parts = G.parts.filter(q => now - q.t0 < q.life);
  for (const q of G.parts) {
    const a = (now - q.t0) / q.life;
    g.globalAlpha = 1 - a * a;
    if (q.kind === "ring") {
      g.strokeStyle = q.col; g.lineWidth = 3 * (1 - a); g.beginPath(); g.arc(q.x, q.y, q.size + 60 * easeOut(a), 0, Math.PI * 2); g.stroke();
      continue;
    }
    q.x += q.vx * dt; q.y += q.vy * dt; q.vx *= 0.96; q.vy = q.vy * 0.96 + 320 * dt;
    g.fillStyle = q.col;
    if (q.kind === "star") star(q.x, q.y, q.size * (1 - a * 0.5), q.spin + a * 4);
    else { g.beginPath(); g.arc(q.x, q.y, q.size * (1 - a * 0.6), 0, Math.PI * 2); g.fill(); }
  }
  g.globalAlpha = 1;
}
/** A Korean text pop at a point: springs in, rises 40 px over 0.9 s, fades (0.5 s longer with a second line). */
function pop(text, sub, x, y, col, size = 1) {
  G.pops.push({ text, sub, x: Math.min(W - 90, Math.max(90, x)), y: Math.max(110, y), col, size, t0: clock() });
}
function drawPops(now) {
  G.pops = G.pops.filter(q => now - q.t0 < 900 + (q.sub ? 500 : 0));
  g.textAlign = "center"; g.lineJoin = "round";
  for (const q of G.pops) {
    const T = now - q.t0, life = 900 + (q.sub ? 500 : 0), a = T / life;
    const s = q.size * (T < 160 ? easeOutBack(T / 160) : 1), y = q.y - 40 * easeOut(Math.min(1, T / 900));
    g.globalAlpha = a > 0.75 ? (1 - a) / 0.25 : 1;
    g.save(); g.translate(q.x, y); g.scale(s, s);
    g.font = '34px "Do Hyeon", sans-serif';
    g.lineWidth = 7; g.strokeStyle = "rgba(18,22,34,.9)"; g.strokeText(q.text, 0, 0);
    g.fillStyle = q.col; g.fillText(q.text, 0, 0);
    if (q.sub) {
      g.font = '600 14px "IBM Plex Sans KR", sans-serif';
      g.lineWidth = 4.5; g.strokeText(q.sub, 0, 24); g.fillStyle = "#ffffff"; g.fillText(q.sub, 0, 24);
    }
    g.restore();
  }
  g.globalAlpha = 1;
}

function draw(now, dt) {
  g.clearRect(0, 0, W, H);
  const t = G.target, aiming = G.phase === "hand" || G.phase === "drag" || G.phase === "fly";
  if (t && aiming && t.onScreen && !G.burst) drawRing(t, now);
  const nm = $("name");
  if (Q.has("boxes")) for (const r of G.reals || []) {   // test view: every real box
    g.setLineDash([6, 5]); g.lineWidth = 2; g.strokeStyle = r.real === "person" ? "#7ecbff" : "#ffc83d";
    g.strokeRect(r.box.x, r.box.y, r.box.w, r.box.h); g.setLineDash([]);
    g.fillStyle = g.strokeStyle; g.font = "12px sans-serif"; g.textAlign = "start"; g.fillText(r.real, r.box.x + 4, r.box.y + r.box.h - 6);
  }
  if (t && aiming && t.onScreen && (t.real || G.mon)) {
    nm.hidden = false; nm.textContent = `야생의 ${t.real ? REAL_KO[t.real] : G.mon.name}`;
    nm.style.left = `${t.center.x}px`; nm.style.top = `${Math.max(70, t.box.y - 10)}px`;
  } else nm.hidden = true;

  for (const q of G.ghosts) {   // a missed ball keeps going, drops and rolls, fading over 0.5 s
    const T = (now - q.t0) / 1000;
    g.globalAlpha = Math.max(0, 1 - T / 0.5);
    drawBall(q.x + q.vx * 1000 * T * 0.6, q.y + q.vy * 1000 * T * 0.3 + 900 * T * T, ballR() * q.s, T * 14 * Math.sign(q.vx || 1));
    g.globalAlpha = 1;
  }
  G.ghosts = G.ghosts.filter(q => now - q.t0 < 500);

  const r0 = ballR();
  if (G.phase === "hand" || G.phase === "wait") {
    const p = rest(), rise = G.handT0 ? Math.min(1, Math.max(0, (now - G.handT0) / 300)) : 1;   // the next ball rises from below
    const y = p.y + (1 - easeOutBack(rise)) * (H - p.y + r0 * 2);
    G.ball = { x: p.x, y, r: r0 };
    drawShadow(p.x, p.y + r0 * 1.05, r0, 0.28 * rise);
    if (G.phase === "hand" && rise > 0) drawBall(p.x, y + Math.sin(now / 300) * 2 * rise, r0);
  } else if (G.phase === "drag") {
    const k = Math.min(1, Math.abs(G.turn) / 300);
    if (k > 0.15 && !G.armed) {   // how far the circling has got
      g.lineWidth = 3; g.strokeStyle = "rgba(255,214,90,.85)"; g.lineCap = "round";
      g.beginPath(); g.arc(G.ball.x, G.ball.y, r0 * 1.45, -Math.PI / 2, -Math.PI / 2 + Math.sign(G.turn) * k * Math.PI * 2, G.turn < 0); g.stroke();
    }
    if (G.armed) sparkle(G.ball.x, G.ball.y, r0);
    drawBall(G.ball.x, G.ball.y, r0, G.armed ? now / 90 * G.armed : 0, G.armed ? 0.8 + 0.2 * Math.sin(now / 80) : 0);
  } else if (G.phase === "fly") {
    flyStep(now);
  } else if (G.phase === "catch" || G.phase === "caught") {
    const R = G.res, T = now - R.t0, b = catchBall(now);
    if (T > SH.bounce && T < SH.bounce + SH.absorb + 120) {   // the creature turns to light and streams into the ball
      const u = (T - SH.bounce) / SH.absorb, a = Math.max(0, Math.sin(Math.min(1, u) * Math.PI)) * 0.75;
      const grd = g.createLinearGradient(R.center.x, R.center.y, b.x, b.y - b.r);
      grd.addColorStop(0, `rgba(255,248,220,${a * 0.2})`); grd.addColorStop(1, `rgba(255,248,220,${a})`);
      g.fillStyle = grd; g.beginPath();
      g.moveTo(b.x - b.r * 0.6, b.y - b.r); g.lineTo(R.center.x - 60 * (1 - u), R.center.y); g.lineTo(R.center.x + 60 * (1 - u), R.center.y); g.lineTo(b.x + b.r * 0.6, b.y - b.r);
      g.fill();
      if (Math.random() < 0.6) G.parts.push({ x: R.center.x + (Math.random() - 0.5) * 80, y: R.center.y + (Math.random() - 0.5) * 80, vx: (b.x - R.center.x) * 2, vy: (b.y - b.r - R.center.y) * 2, t0: now, life: 380, col: "#fff6dc", kind: "dot", size: 2.5, spin: 0 });
    }
    const air = Math.max(0, R.ground - b.y);
    drawShadow(b.x, R.ground, b.r * Math.max(0.5, 1 - air / 160), 0.32);
    const open = T > SH.bounce && T < SH.bounce + SH.absorb, done = G.phase === "caught" ? Math.min(1, (now - R.doneT) / 150) : 0;
    const click = done ? 1 + 0.18 * Math.sin(done * Math.PI) : 1;   // the "click" when it locks
    g.save(); g.translate(b.x, b.y); g.rotate(b.rot);
    drawBall(0, -b.r, b.r * click, 0, open ? 0.9 : done ? 0.6 + 0.25 * Math.sin(now / 160) : 0, b.rocking || done ? 1 : 0);
    g.restore();
    if (G.phase === "catch") catchStep(now);
  }
  drawParts(now, dt);
  drawPops(now);
}

// ---------- HUD ----------
let toastT = 0;
function toast(text, small = "") {
  const t = $("toast"); t.innerHTML = ""; t.append(text);
  if (small) { const s = document.createElement("small"); s.textContent = small; t.append(s); }
  t.classList.remove("show"); void t.offsetWidth; t.classList.add("show");   // restart the spring
  clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove("show"), 1400 * SLOW);
}
function note(text) { const n = $("note"); n.textContent = text; n.hidden = !text; }
async function counter() {
  const c = await store.counts();
  $("count").textContent = `${c.creatures}마리 · 모습 ${c.poses}개`;
}
function showCard({ creature: c, outcome }) {
  const R = G.res;
  dex.fillCard(c, G.mon.img.src, `${GRADE_KO[R.grade]}${R.curve ? " · 커브볼" : ""}${R.critical ? " · 크리티컬" : ""} · 확률 ${Math.round(R.p * 100)}%` +
    (outcome === "again" ? ` · 또 잡았다 ×${c.times}` : " · 새 친구!") +
    "\n맞히면 70% · 링이 작을수록·커브볼이면 더 잘 잡혀요");
  G.phase = "card";
}
$("cGo").onclick = () => {
  if (dex.closeCard()) return;   // a card made from the dex goes back to the detail
  $("card").hidden = true;
  if (G.res?.real) { G.res = null; G.phase = "hand"; G.ringT0 = clock(); return; }   // the virtual one is still there
  G.res = null; G.phase = "wait"; spawn();
};

// ---------- a real one caught: "누구야?" then "어떤 모습?" ----------
const BASE = { dog: "멍멍이", cat: "야옹이", person: "사람" };
const POSE_KO = { new: "새 모습 발견!", better: "더 잘 찍힌 사진으로 바꿨다", have: "이미 있는 모습" };
function chip(text, onTap, cls = "") {
  const b = document.createElement("button"); b.className = "chip " + cls; b.textContent = text; b.onclick = onTap;
  return b;
}
async function who(R) {
  const cut = await R.real.cut, v = $("video");
  const sug = suggest(R.real.n.w * v.videoWidth, R.real.n.h * v.videoHeight);
  let kind = R.real.kind;
  G.phase = "card";
  $("sImg").src = URL.createObjectURL(cut.blob);
  async function ask() {
    $("sTitle").textContent = "누구야?";
    const list = await store.ofKind(kind);
    $("sKind").replaceChildren(...(kind === "person" ? [] : ["dog", "cat"].map(k =>
      chip(k === "dog" ? "개" : "고양이", () => { kind = k; ask(); }, k === kind ? "on" : ""))));
    $("sChips").replaceChildren(...list.map(c => chip(`${c.name} · ${count(c.labels, c.kind)}`, () => pose({ id: c.id, name: c.name, kind: c.kind }))),
      chip("+ 새 친구", () => { $("sNew").hidden = false; $("sName").value = `${BASE[kind]} ${list.length + 1}`; $("sName").focus(); }, "new"));
    $("sNew").hidden = true;
    $("sOk").onclick = () => pose({ id: null, name: $("sName").value.trim().slice(0, 12) || `${BASE[kind]} ${list.length + 1}`, kind });
    $("sheet").hidden = false;
  }
  function pose(c) {
    $("sTitle").textContent = "어떤 모습?";
    $("sKind").replaceChildren(); $("sNew").hidden = true;
    $("sChips").replaceChildren(...LABELS.map(l => chip(LABEL_KO[l], () => save(c, l), l === sug ? "on" : "")));
  }
  async function save(c, label) {
    const out = await store.catchReal({ ...c, label, png: cut.blob, w: cut.w, h: cut.h, grade: R.grade, blurry: cut.blurry });
    $("sheet").hidden = true;
    await counter();
    dex.fillCard(out.creature, $("sImg").src, `${count(out.labels, out.creature.kind)} · ${LABEL_KO[label]} ${POSE_KO[out.pose]}` + (cut.blurry ? " · 흐릿하게 찍힘" : "") +
      `\n${GRADE_KO[R.grade]}${R.curve ? " · 커브볼" : ""}${R.critical ? " · 크리티컬" : ""} · 확률 ${Math.round(R.p * 100)}%`);
  }
  ask();
}

// ---------- modes ----------
const askMotion = () => { try { if (typeof DeviceOrientationEvent?.requestPermission === "function") DeviceOrientationEvent.requestPermission().catch(() => {}); } catch { /* old browser */ } };
// ?still=<url>: a photo stands in for the camera (tests), as the whole camera frame (the screen crops it like a camera).
// Letterboxing it into a portrait frame shrank a landscape photo to a strip in black, and DeepLab never found the black cat.
async function stillStream(url) {
  const img = await ar.loadImg(url), c = document.createElement("canvas");
  c.width = img.naturalWidth; c.height = img.naturalHeight;
  const x = c.getContext("2d"), paint = () => x.drawImage(img, 0, 0);
  paint(); setInterval(paint, 200);   // a canvas stream only sends a frame when the canvas is drawn
  return c.captureStream(10);
}
async function toCamera() {
  try { await ar.startCamera(Q.has("still") ? await stillStream(Q.get("still")) : undefined); } catch (e) {
    note(e && e.name === "NotAllowedError" ? "카메라가 막혀 있어 화면으로 잡아요 · 주소창 옆 설정에서 허용할 수 있어요" : "카메라를 켜지 못해 화면으로 잡아요");
    return false;
  }
  setMode("camera"); seg.start($("video")); return true;
}
function setMode(m) {
  G.mode = m; ar.setMode(m);
  if (m === "screen") { ar.stopCamera(); seg.clear(); }
  $("mode").textContent = m === "camera" ? "화면으로" : "카메라로";
  if (m === "camera") note("");
}
$("mode").onclick = async () => { askMotion(); if (G.mode === "camera") setMode("screen"); else await toCamera(); };

let started = false;
async function begin(mode) {
  if (started) return; started = true;
  $("start").hidden = true; $("mode").hidden = false;
  size();
  setMode("screen");
  if (mode === "camera" && !(await toCamera())) setMode("screen");
  if (!(await store.open())) note("이 브라우저에선 도감이 저장되지 않아요");
  counter();
  spawn();
}
$("goCam").onclick = () => { askMotion(); begin("camera"); };
$("goScreen").onclick = () => { askMotion(); begin("screen"); };

// the counter opens the dex, from the start screen too (not over a card or the "누구야?" sheet)
store.open().then(counter);
// "그림 넣기": the picked pictures (long side 256, PNG) become the creatures, replacing the last set; name = file name
$("mineBtn").onclick = () => $("mineFile").click();
$("mineFile").onchange = async e => {
  const files = [...e.target.files]; e.target.value = "";
  if (!files.length) return;
  const out = [];
  for (const [i, f] of files.entries()) {
    try {
      const im = await ar.loadImg(URL.createObjectURL(f)), s = 256 / Math.max(im.naturalWidth, im.naturalHeight);
      const c = Object.assign(document.createElement("canvas"), { width: Math.round(im.naturalWidth * s), height: Math.round(im.naturalHeight * s) });
      c.getContext("2d").drawImage(im, 0, 0, c.width, c.height);
      out.push({ id: `u_${Date.now().toString(36)}${i}`, name: f.name.replace(/\.[^.]+$/, "").slice(0, 12), src: c.toDataURL("image/png") });
    } catch { /* not a picture */ }
  }
  if (!out.length) return toast("그림을 읽지 못했어요");
  try { localStorage.setItem("catch.mine", JSON.stringify(out)); } catch { return toast("저장 공간이 부족해요"); }
  toast(`${out.length}마리 들어왔다!`);
  if (started && G.phase === "hand") spawn();
};
$("count").onclick = () => { if ($("card").hidden && $("sheet").hidden) dex.open(counter); };

// ---------- loop ----------
ar.init($("gl"), $("video"));
ar.loadRoom("img/room.jpg").catch(() => {});
size();
let last = clock();
function loop(t) {
  const now = t / SLOW;
  requestAnimationFrame(loop);
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  if (!started) return;
  seg.tick(now);
  if (G.phase === "hand" && G.mon && !G.target?.real && now - G.mon.t0 > STAY_MS) { G.mon.t0 = now; toast("녀석이 가 버렸다…"); spawn(); }
  G.target = currentTarget(ar.frame(now, dt, monPose(now)));
  draw(now, dt);
}
requestAnimationFrame(loop);
if (Q.has("touch")) begin("screen");

// test hook for the headless checks
window.__catch = { G, clock, ar, seg, store, reals: () => (G.reals || []).map(r => ({ kind: r.real, box: r.box, n: r.n })), get phase() { return G.phase; }, target: () => G.target, ball: () => G.ball, count: () => $("count").textContent };
