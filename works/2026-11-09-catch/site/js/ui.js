// The game: start screen, mode switch, the ball (drawn in 2D over the 3D scene), throw -> hit -> shakes -> caught /
// escaped, the counter and a minimal catch card (the full card and dex are stage S3).
import * as ar from "./ar.js";
import { grade, chance, roll, ringAt, rng } from "./catch.js";
import { release, flight, hit, FLIGHT_MS, END_SCALE } from "./geom.js";
import { cardText } from "./lines.js";
import * as store from "./store.js";

const $ = id => document.getElementById(id);
const Q = new URLSearchParams(location.search);
const rnd = Q.has("seed") ? rng(+Q.get("seed") || 0) : Math.random;
const ART = id => `https://cdn.jsdelivr.net/gh/PokeAPI/sprites@master/sprites/pokemon/other/official-artwork/${id}.png`;
const GRADE_KO = { hit: "맞았다", nice: "Nice", great: "Great", excellent: "Excellent" };
const STAY_MS = 30000;   // a creature nobody catches leaves after 30 s

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
  path: [], th: null, t0: 0, ring: 1, ringT0: 0,
  res: null,                   // {grade, curve, p, caught, shakes, land, ground, t0}
  ghosts: [],                  // missed balls falling away while the next one is already in hand
  target: null, look: null,
};
const rest = () => ({ x: W / 2, y: H * 0.78 });
const ballR = () => Math.max(24, Math.min(34, W * 0.075));

// ---------- creatures ----------
const names = new Map();
async function koName(id) {
  if (names.has(id)) return names.get(id);
  let n = `포켓몬 ${id}`;
  try {
    const r = await fetch(`https://pokeapi.co/api/v2/pokemon-species/${id}`);
    const ko = (await r.json()).names.find(x => x.language.name === "ko");
    if (ko) n = ko.name;
  } catch { /* the number will do */ }
  names.set(id, n);
  return n;
}
let spawning = false;
async function spawn() {
  if (spawning) return;
  spawning = true;   // a creature that leaves stays in view (and the ball in hand) until the next one has loaded
  const id = 1 + Math.floor(rnd() * 151);
  try {
    const [img, name] = await Promise.all([ar.loadImg(ART(id)), koName(id)]);
    G.mon = { id, name, img, t0: performance.now() };
    ar.setCreature(img); ar.place();
    G.ringT0 = performance.now();
    if (G.phase === "wait") G.phase = "hand";
  } catch {
    note("녀석을 불러오지 못했어요 · 인터넷 연결을 확인해 주세요");
    setTimeout(() => { spawning = false; spawn(); }, 3000);
    return;
  }
  spawning = false;
}
// S2 hook: when seg.js sees a real dog / cat / person, its box becomes the target here and the virtual one hides.
const currentTarget = t => t;

// ---------- input ----------
const app = $("app");
for (const t of ["touchstart", "touchmove"])   // keep the gesture in the page inside Threads / Instagram (README)
  app.addEventListener(t, e => { if (!e.target.closest("button,a")) e.preventDefault(); }, { passive: false });
document.body.style.minHeight = "calc(100% + 2px)";
const nudge = () => { if (scrollY < 1) scrollTo(0, 1); };
addEventListener("load", nudge); addEventListener("scroll", nudge, { passive: true });

app.addEventListener("pointerdown", e => {
  if (e.target.closest("button,a,#card,#start")) return;
  const b = G.ball, now = performance.now();
  if (G.phase === "hand" && Math.hypot(e.clientX - b.x, e.clientY - b.y) < b.r * 1.9) {
    G.phase = "drag"; G.path = [{ x: e.clientX, y: e.clientY, t: e.timeStamp }];
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
  } else if (G.look && G.look.id === e.pointerId) {
    ar.look((e.clientX - G.look.x) * 0.0045, (e.clientY - G.look.y) * 0.0045);
    G.look.x = e.clientX; G.look.y = e.clientY;
  }
});
function up(e) {
  if (G.look && G.look.id === e.pointerId) G.look = null;
  if (G.phase !== "drag") return;
  const now = performance.now();
  G.path.push({ x: e.clientX, y: e.clientY, t: e.timeStamp });
  const th = release(G.path, W, H);
  G.lastThrow = { th, n: G.path.length, span: e.timeStamp - G.path[0].t };
  if (!th) { G.phase = "hand"; return; }   // not a throw: the ball goes back to the hand
  G.th = th; G.t0 = now; G.ring = ringAt(now - G.ringT0); G.phase = "fly";
}
app.addEventListener("pointerup", up);
app.addEventListener("pointercancel", e => { if (G.phase === "drag") G.phase = "hand"; if (G.look) G.look = null; });

// ---------- the throw lands ----------
function land(now) {
  const end = flight(G.th, 1, W, H), t = G.target;
  const ok = t && t.onScreen && !t.behind;
  const ringC = ok ? { x: t.center.x, y: t.center.y, r: ringMax(t) * G.ring } : { x: 0, y: 0, r: 0 };
  const gr = grade({ ...hit(end, ok ? t.box : null, ringC), ring: G.ring });
  if (!gr) {   // miss: no verdict, the next ball is already in the hand
    G.ghosts.push({ x: end.x, y: end.y, s: END_SCALE, t0: now, vx: (G.th.to.x - G.th.from.x) / FLIGHT_MS * 0.6 });
    G.phase = "hand";
    toast("빗나갔다");
    return;
  }
  const p = chance(gr, G.th.curve), r = roll(p, rnd);
  G.res = { grade: gr, curve: G.th.curve, p, ...r, land: end, ground: t.box.y + t.box.h - 6, t0: now };
  G.phase = "catch";
  toast(`${GRADE_KO[gr]}!${G.th.curve ? " 커브볼" : ""}`, `잡힐 확률 ${Math.round(p * 100)}%`);
}
const ringMax = t => Math.min(t.box.w, t.box.h) * 0.4;   // ring diameter = 80 % of the short side
const SH = { absorb: 450, drop: 300, each: 800 };
function catchStep(now) {
  const R = G.res, T = now - R.t0, endT = SH.absorb + SH.drop + R.shakes * SH.each + 150;
  const shakeI = Math.floor((T - SH.absorb - SH.drop) / SH.each);
  if (shakeI >= 0 && shakeI < R.shakes && R.vib !== shakeI) { R.vib = shakeI; try { navigator.vibrate?.(30); } catch { /* iOS */ } }
  if (T < endT) return;
  if (R.caught) {
    G.phase = "caught"; R.doneT = now; toast("잡았다!");
    store.catchVirtual({ pokeId: G.mon.id, name: G.mon.name, grade: R.grade }).then(async out => {
      await counter();
      setTimeout(() => showCard(out), 800);
    });
  } else {
    G.phase = "hand"; G.res = null; G.ringT0 = now;
    toast("앗, 도망쳤다!", "다시 던져요");
  }
}
function monPose(now) {
  if (G.phase === "catch") {
    const T = now - G.res.t0;
    return { scale: Math.max(0, 1 - T / SH.absorb), hold: true };
  }
  if (G.phase === "caught" || G.phase === "card") return { scale: 0, hidden: true, hold: true };
  return {};
}

// ---------- drawing ----------
function drawBall(x, y, r, rot = 0) {
  g.save(); g.translate(x, y); g.rotate(rot);
  g.beginPath(); g.arc(0, 0, r, 0, Math.PI * 2); g.fillStyle = "#f4f4f2"; g.fill();
  g.beginPath(); g.arc(0, 0, r, Math.PI, 0); g.closePath(); g.fillStyle = "#e8352b"; g.fill();
  g.lineWidth = r * 0.16; g.strokeStyle = "#1c1c1c";
  g.beginPath(); g.moveTo(-r, 0); g.lineTo(r, 0); g.stroke();
  g.beginPath(); g.arc(0, 0, r, 0, Math.PI * 2); g.lineWidth = r * 0.08; g.stroke();
  g.beginPath(); g.arc(0, 0, r * 0.3, 0, Math.PI * 2); g.fillStyle = "#f8f8f6"; g.fill(); g.lineWidth = r * 0.12; g.stroke();
  const sh = g.createRadialGradient(-r * 0.35, -r * 0.45, r * 0.1, 0, 0, r * 1.05);   // round, lit from above
  sh.addColorStop(0, "rgba(255,255,255,.55)"); sh.addColorStop(0.45, "rgba(255,255,255,0)"); sh.addColorStop(1, "rgba(0,0,0,.38)");
  g.beginPath(); g.arc(0, 0, r, 0, Math.PI * 2); g.fillStyle = sh; g.fill();
  g.restore();
}
function drawRing(t, now) {
  const m = ringMax(t), k = G.phase === "fly" ? G.ring : ringAt(now - G.ringT0), r = m * k;
  g.lineWidth = 2; g.strokeStyle = "rgba(255,255,255,.8)";
  g.beginPath(); g.arc(t.center.x, t.center.y, m, 0, Math.PI * 2); g.stroke();
  g.lineWidth = 4.5; g.strokeStyle = k < 0.5 ? "#ffc83d" : k < 0.75 ? "#7ee2a8" : "#ffffff";
  g.shadowColor = "rgba(0,0,0,.45)"; g.shadowBlur = 6;
  g.beginPath(); g.arc(t.center.x, t.center.y, r, 0, Math.PI * 2); g.stroke();
  g.shadowBlur = 0;
}
function draw(now) {
  g.clearRect(0, 0, W, H);
  const t = G.target, aiming = G.phase === "hand" || G.phase === "drag" || G.phase === "fly";
  if (t && aiming && t.onScreen) drawRing(t, now);
  const nm = $("name");
  if (t && aiming && t.onScreen && G.mon) {
    nm.hidden = false; nm.textContent = `야생의 ${G.mon.name}`;
    nm.style.left = `${t.center.x}px`; nm.style.top = `${Math.max(70, t.box.y - 10)}px`;
  } else nm.hidden = true;

  for (const q of G.ghosts) {
    const T = (now - q.t0) / 500;
    g.globalAlpha = Math.max(0, 1 - T);
    drawBall(q.x + q.vx * T * 500, q.y + 260 * T * T, ballR() * q.s, T * 6);
    g.globalAlpha = 1;
  }
  G.ghosts = G.ghosts.filter(q => now - q.t0 < 500);

  const r0 = ballR();
  if (G.phase === "hand" || G.phase === "wait") {
    const p = rest(); G.ball = { x: p.x, y: p.y, r: r0 };
    g.fillStyle = "rgba(0,0,0,.28)"; g.beginPath(); g.ellipse(p.x, p.y + r0 * 1.05, r0 * 0.8, r0 * 0.22, 0, 0, Math.PI * 2); g.fill();
    if (G.phase === "hand") drawBall(p.x, p.y + Math.sin(now / 300) * 2, r0);
  } else if (G.phase === "drag") {
    drawBall(G.ball.x, G.ball.y, r0);
  } else if (G.phase === "fly") {
    const u = Math.min(1, (now - G.t0) / FLIGHT_MS), b = flight(G.th, u, W, H);
    drawBall(b.x, b.y, r0 * b.s, u * 10 * (G.th.curve || 1));
    if (u >= 1) land(now);
  } else if (G.phase === "catch" || G.phase === "caught") {
    const R = G.res, T = now - R.t0, rr = r0 * END_SCALE;
    let y = R.land.y, rot = 0;
    if (T > SH.absorb) { const u = Math.min(1, (T - SH.absorb) / SH.drop); y = R.land.y + (R.ground - rr - R.land.y) * u * u; }
    const ts = T - SH.absorb - SH.drop;
    if (ts > 0 && G.phase === "catch") { const i = Math.floor(ts / SH.each), f = (ts - i * SH.each) / 500; if (i < R.shakes && f < 1) rot = Math.sin(f * Math.PI * 3) * 0.45 * (1 - f); }
    if (T < SH.absorb) {   // the creature turns to light and goes in
      g.fillStyle = `rgba(255,250,220,${0.6 * (1 - T / SH.absorb)})`;
      g.beginPath(); g.arc(R.land.x, R.land.y, rr * (1 + 3 * T / SH.absorb), 0, Math.PI * 2); g.fill();
    }
    g.fillStyle = "rgba(0,0,0,.3)"; g.beginPath(); g.ellipse(R.land.x, Math.max(y + rr, R.ground) , rr * 0.9, rr * 0.25, 0, 0, Math.PI * 2); g.fill();
    g.save(); g.translate(R.land.x, y + rr); g.rotate(rot); drawBall(0, -rr, rr); g.restore();
    if (G.phase === "caught") {   // three little stars
      for (let i = 0; i < 3; i++) {
        const a = -Math.PI / 2 + (i - 1) * 0.7, d = rr * 2.2 + Math.min(1, (now - R.doneT) / 300) * 8;
        g.fillStyle = "#ffe066"; g.font = `${Math.round(rr * 1.2)}px sans-serif`; g.textAlign = "center";
        g.fillText("★", R.land.x + Math.cos(a) * d, y + Math.sin(a) * d);
      }
    } else catchStep(now);
  }
}

// ---------- HUD ----------
let toastT = 0;
function toast(text, small = "") {
  const t = $("toast"); t.innerHTML = ""; t.append(text);
  if (small) { const s = document.createElement("small"); s.textContent = small; t.append(s); }
  t.classList.add("show"); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove("show"), 1400);
}
function note(text) { const n = $("note"); n.textContent = text; n.hidden = !text; }
async function counter() {
  const c = await store.counts();
  $("count").textContent = `${c.creatures}마리 · 모습 ${c.poses}개`;
}
function showCard({ creature: c, outcome }) {
  const R = G.res, txt = cardText(c.id, c.kind);
  $("cNo").textContent = `No.${String(c.no).padStart(3, "0")}`;
  $("cImg").crossOrigin = "anonymous"; $("cImg").src = G.mon.img.src;
  $("cName").textContent = c.name;
  $("cType").textContent = txt.type;
  $("cLine").textContent = txt.line;
  $("cHow").textContent = `${GRADE_KO[R.grade]}${R.curve ? " · 커브볼" : ""} · 확률 ${Math.round(R.p * 100)}%` +
    (outcome === "again" ? ` · 또 잡았다 ×${c.times}` : " · 새 친구!") +
    "\n몸 50 · Nice 65 · Great 80 · Excellent 95% · 커브 +10%p";
  $("cHow").style.whiteSpace = "pre-line";
  $("card").hidden = false; G.phase = "card";
}
$("cGo").onclick = () => { $("card").hidden = true; G.res = null; G.phase = "wait"; spawn(); };

// ---------- modes ----------
const askMotion = () => { try { if (typeof DeviceOrientationEvent?.requestPermission === "function") DeviceOrientationEvent.requestPermission().catch(() => {}); } catch { /* old browser */ } };
async function toCamera() {
  try { await ar.startCamera(); } catch (e) {
    note(e && e.name === "NotAllowedError" ? "카메라가 막혀 있어 화면으로 잡아요 · 주소창 옆 설정에서 허용할 수 있어요" : "카메라를 켜지 못해 화면으로 잡아요");
    return false;
  }
  setMode("camera"); return true;
}
function setMode(m) {
  G.mode = m; ar.setMode(m);
  if (m === "screen") ar.stopCamera();
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

// ---------- loop ----------
ar.init($("gl"), $("video"));
ar.loadRoom("img/room.jpg").catch(() => {});
size();
let last = performance.now();
function loop(now) {
  requestAnimationFrame(loop);
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  if (!started) return;
  if (G.phase === "hand" && G.mon && now - G.mon.t0 > STAY_MS) { G.mon.t0 = now; toast("녀석이 가 버렸다…"); spawn(); }
  G.target = currentTarget(ar.frame(now, dt, monPose(now)));
  draw(now);
}
requestAnimationFrame(loop);
if (Q.has("touch")) begin("screen");

// test hook for the headless checks
window.__catch = { G, ar, get phase() { return G.phase; }, target: () => G.target, ball: () => G.ball, count: () => $("count").textContent };
