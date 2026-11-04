import { createGame, layout, step, poke, percent, label, reason, stars, SHAPES, RULES, wetAt, crackStage } from "./engine.js";
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
const bestOf = k => { try { return JSON.parse(store("dalgona.best2." + k)) || null; } catch { return null; } };
// a better record: more stars, then a faster time; a loss only beats a loss with less carved
const better = (a, b) => !b || a.stars > b.stars || (a.stars === b.stars && (a.stars ? a.time < b.time : a.pct > b.pct));

// ---------- layout ----------
const hc = document.createElement("canvas"), hg = hc.getContext("2d");   // the real hand cut out of the video (CSS px)
function resize() {
  const b = app.getBoundingClientRect();
  DPR = Math.min(2, devicePixelRatio || 1); W = b.width; H = b.height;
  cv.width = W * DPR; cv.height = H * DPR; ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  hc.width = Math.ceil(W); hc.height = Math.ceil(H);
  geom = { cx: W / 2, cy: H * 0.58, R: Math.min(W * 0.36, H * 0.2) };
  if (game) { layout(game, geom); view = createCandyView(game, DPR); }
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
const TG = (() => { let s = 11; return () => (s = (s * 16807) % 2147483647) / 2147483647; })();
const PAPILLAE = Array.from({ length: 40 }, () => [TG() - 0.5, TG(), 0.5 + TG()]);
/** t = {base, dir, len, width, mouthW?, gap?, top?}; draws the open mouth (camera) and a wet tongue reaching to base + dir*len. */
function drawTongue(t, active, now) {
  const [bx, by] = t.base, [dx, dy] = t.dir, L = t.len, w = t.width;
  ctx.save();
  if (t.top) {   // the open mouth: dark inside, a strip of teeth under the upper lip
    const mx = (t.top[0] + bx) / 2, my = (t.top[1] + by) / 2, rx = t.mouthW * 0.42, ry = Math.max(2, t.gap * 0.55);
    const mg = ctx.createRadialGradient(mx, my, 0, mx, my, rx); mg.addColorStop(0, "#1a0306"); mg.addColorStop(1, "#5a1420");
    ctx.fillStyle = mg; ctx.beginPath(); ctx.ellipse(mx, my, rx, ry, Math.atan2(dy, dx) - Math.PI / 2, 0, 7); ctx.fill();
    ctx.fillStyle = "rgba(240,236,228,.85)"; ctx.beginPath(); ctx.ellipse(mx, my - ry * 0.75, rx * 0.7, Math.max(1, ry * 0.18), 0, 0, 7); ctx.fill();
  }
  const wig = active ? Math.sin(now / 130) * 0.13 : 0;
  ctx.translate(bx, by); ctx.rotate(Math.atan2(dy, dx) - Math.PI / 2 + wig);
  // body: wide at the root, a rounded tip with a slight notch
  const body = new Path2D();
  body.moveTo(-w * 0.42, 0);
  body.bezierCurveTo(-w * 0.58, L * 0.45, -w * 0.5, L * 0.92, -w * 0.12, L * 0.99);
  body.quadraticCurveTo(0, L * 0.95, w * 0.12, L * 0.99);
  body.bezierCurveTo(w * 0.5, L * 0.92, w * 0.58, L * 0.45, w * 0.42, 0);
  body.closePath();
  const tg = ctx.createRadialGradient(-w * 0.1, L * 0.55, w * 0.05, 0, L * 0.5, w * 0.75);
  tg.addColorStop(0, "#ff9db0"); tg.addColorStop(0.55, "#ec6380"); tg.addColorStop(1, "#b83652");
  ctx.fillStyle = tg; ctx.fill(body);
  // root goes into the mouth: darker toward the base
  const rg = ctx.createLinearGradient(0, 0, 0, L * 0.35); rg.addColorStop(0, "rgba(90,10,25,.6)"); rg.addColorStop(1, "rgba(90,10,25,0)");
  ctx.fillStyle = rg; ctx.fill(body);
  ctx.save(); ctx.clip(body);
  ctx.fillStyle = "rgba(255,210,220,.22)";
  for (const [px, py, ps] of PAPILLAE) { ctx.beginPath(); ctx.arc(px * w * 0.8, py * L, ps * w * 0.025, 0, 7); ctx.fill(); }
  ctx.restore();
  ctx.strokeStyle = "rgba(140,25,50,.45)"; ctx.lineWidth = Math.max(1, w * 0.045); ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(0, L * 0.12); ctx.quadraticCurveTo(w * 0.02, L * 0.5, 0, L * 0.84); ctx.stroke();
  ctx.fillStyle = "rgba(255,255,255,.55)"; ctx.beginPath(); ctx.ellipse(-w * 0.2, L * 0.5, w * 0.06, L * 0.16, 0.15, 0, 7); ctx.fill();
  ctx.fillStyle = "rgba(255,255,255,.7)"; ctx.beginPath(); ctx.ellipse(w * 0.14, L * 0.82, w * 0.05, w * 0.03, 0.4, 0, 7); ctx.fill();
  ctx.strokeStyle = "rgba(120,20,40,.35)"; ctx.lineWidth = 1; ctx.stroke(body);
  ctx.restore();
}
function drawNeedle(tip, dir, down) {
  const len = geom.R * 1.05, [dx, dy] = dir, ex = tip.x - dx * len, ey = tip.y - dy * len;
  ctx.save(); ctx.lineCap = "round"; ctx.globalAlpha = down ? 1 : 0.55;
  // shadow on the candy closes in when the needle touches it
  const so = down ? geom.R * 0.03 : geom.R * 0.12;
  ctx.strokeStyle = "rgba(0,0,0,.3)"; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(tip.x + so * 0.6, tip.y + so); ctx.lineTo(ex + so * 1.6, ey + so * 2.2); ctx.stroke();
  const ng = ctx.createLinearGradient(tip.x, tip.y, ex, ey); ng.addColorStop(0, "#f4f6f8"); ng.addColorStop(0.5, "#a9b0b6"); ng.addColorStop(1, "#5d646a");
  ctx.strokeStyle = ng; ctx.lineWidth = 2.6; ctx.beginPath(); ctx.moveTo(tip.x, tip.y); ctx.lineTo(ex, ey); ctx.stroke();
  ctx.strokeStyle = "rgba(255,255,255,.8)"; ctx.lineWidth = 0.9; ctx.beginPath(); ctx.moveTo(tip.x - dy, tip.y + dx); ctx.lineTo(ex - dy, ey + dx); ctx.stroke();
  ctx.restore();
}
function drawStressRing(tip) {
  const st = game ? game.stress : 0, col = st < 0.3 ? "#7CE38B" : st < RULES.HAIR ? "#FFC23D" : "#FF3B5C";
  const r = 15 + (st >= RULES.HAIR ? Math.sin(performance.now() / 60) * 1.5 : 0);
  ctx.save(); ctx.lineCap = "round";
  ctx.strokeStyle = "rgba(0,0,0,.45)"; ctx.lineWidth = 5; ctx.beginPath(); ctx.arc(tip.x, tip.y, r, 0, 7); ctx.stroke();
  ctx.strokeStyle = col; ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(tip.x, tip.y, r, -Math.PI / 2, -Math.PI / 2 + Math.max(0.06, Math.min(1, st)) * Math.PI * 2); ctx.stroke();
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
function drawStamp(t) {
  if (t <= 0) return;
  const k = Math.min(1, t / 0.18), sc = 1.6 - 0.6 * k;
  ctx.save(); ctx.globalAlpha = k; ctx.translate(geom.cx, geom.cy - geom.R * 1.45); ctx.rotate(-0.08); ctx.scale(sc, sc);
  ctx.font = `64px "Do Hyeon", sans-serif`; ctx.textAlign = "center"; ctx.textBaseline = "middle";
  ctx.lineWidth = 6; ctx.strokeStyle = "rgba(0,0,0,.55)"; ctx.strokeText("탈락", 0, 0);
  ctx.fillStyle = "#FF3B5C"; ctx.fillText("탈락", 0, 0);
  ctx.restore();
}

// ---------- sound and touch feedback ----------
function initAudio() {
  if (audio) return;
  try {
    const ac = new (window.AudioContext || window.webkitAudioContext)();
    const buf = ac.createBuffer(1, ac.sampleRate, ac.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const src = ac.createBufferSource(); src.buffer = buf; src.loop = true;
    const bp = ac.createBiquadFilter(); bp.type = "bandpass"; bp.frequency.value = 3200; bp.Q.value = 1.2;
    const gn = ac.createGain(); gn.gain.value = 0; src.connect(bp).connect(gn).connect(ac.destination); src.start();
    audio = { ac, gn, bp, buf };
  } catch { audio = null; }
}
const buzz = p => { try { navigator.vibrate && navigator.vibrate(p); } catch { /* not supported */ } };
function noise(f, d, v, type = "bandpass") {
  if (!audio) return; const { ac, buf } = audio, s = ac.createBufferSource(); s.buffer = buf;
  const fl = ac.createBiquadFilter(); fl.type = type; fl.frequency.value = f; fl.Q.value = 1.4;
  const g = ac.createGain(), t = ac.currentTime; g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.001, t + d);
  s.connect(fl).connect(g).connect(ac.destination); s.start(t, Math.random() * 0.5); s.stop(t + d + 0.05);
}
function tone(f, d, v, type = "triangle") {
  if (!audio) return; const { ac } = audio, o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime;
  o.type = type; o.frequency.value = f; g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.001, t + d);
  o.connect(g).connect(ac.destination); o.start(t); o.stop(t + d + 0.02);
}
const SFX = {
  poke: hit => { tone(hit ? 1900 : 1300, 0.05, 0.22); buzz(8); },
  segment: () => { tone(2600, 0.04, 0.2, "square"); noise(5200, 0.03, 0.25); buzz(12); },
  hair: () => { noise(4200, 0.06, 0.5); buzz(15); },
  spread: () => { noise(2600, 0.1, 0.6); buzz([20, 30, 20]); },
  chip: () => { noise(1800, 0.14, 0.7); setTimeout(() => noise(3000, 0.05, 0.3), 260); buzz([30, 40, 30]); },
  snap: () => { noise(1500, 0.12, 0.8); buzz(30); },
  win: () => { [880, 1320, 1760].forEach((f, i) => setTimeout(() => tone(f, 0.35, 0.18, "sine"), i * 110)); buzz([40, 60, 40]); },
  lose: () => { noise(700, 0.4, 0.9, "lowpass"); tone(90, 0.5, 0.5, "sine"); buzz([80, 40, 160]); },
};

// ---------- flow ----------
const show = id => ["start", "pick", "result"].forEach(k => { $(k).hidden = k !== id; });

let camStream = null;
$("camGo").onclick = async () => {
  if ($("camGo").disabled) return;
  $("camGo").disabled = true;
  initAudio();
  const msg = $("camMsg"); msg.hidden = false; msg.textContent = "카메라를 켜는 중이에요…";
  try {
    camStream = await startCamera(video);
    video.hidden = false; $("nocam").hidden = true;
    msg.textContent = "손 인식을 불러오는 중이에요(처음 한 번 몇 초)…";
    const models = await loadModels();
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
$("touchGo").onclick = () => { initAudio(); mode = "touch"; showPick(); };

const starText = n => "★".repeat(n) + "☆".repeat(3 - n);
function showPick() {
  game = null; view = null; endAt = 0; $("hud").hidden = true; $("freeze").hidden = true; show("pick");
  const picks = $("picks"); picks.innerHTML = "";
  for (const [k, s] of Object.entries(SHAPES)) {
    const b = document.createElement("button"), best = bestOf(k);
    b.innerHTML = `<span class="ic"></span><small></small>`;
    b.querySelector(".ic").textContent = s.icon;
    b.querySelector("small").textContent = best ? (best.stars ? starText(best.stars) : best.pct + "%") : s.name;
    b.setAttribute("aria-label", s.name + (best && best.stars ? ` 별 ${best.stars}개` : ""));
    b.onclick = () => begin(k); picks.appendChild(b);
  }
}

let resultShown = false, endAt = 0, winAt = 0, loseT = -1, moment = null, lickShot = null, lickFor = 0, capture = null;
function begin(shape) {
  game = createGame(shape, geom); view = createCandyView(game, DPR); resultShown = false; lickMode = false;
  endAt = 0; winAt = 0; loseT = -1; moment = null; lickShot = null; lickFor = 0; capture = null; crumbs.length = 0;
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
function grab() {
  const c = document.createElement("canvas"); c.width = Math.round(W * DPR); c.height = Math.round(H * DPR);
  const g = c.getContext("2d"); g.setTransform(DPR, 0, 0, DPR, 0, 0);
  if (mode === "camera" && video.videoWidth) videoCover(g);
  else { const bg = g.createRadialGradient(W / 2, H * 0.38, 0, W / 2, H * 0.38, H * 0.7); bg.addColorStop(0, "#4a4038"); bg.addColorStop(0.55, "#2a241f"); bg.addColorStop(1, "#141110"); g.fillStyle = bg; g.fillRect(0, 0, W, H); }
  g.setTransform(1, 0, 0, 1, 0, 0); g.drawImage(cv, 0, 0);
  return c;
}

// 1080 x 1350 card: the moment, the verdict, the link
async function makeCard() {
  try { await document.fonts.load('80px "Do Hyeon"'); await document.fonts.load('40px "Orbit"'); } catch { /* fallback fonts */ }
  const g0 = game, CW = 1080, CH = 1350, c = document.createElement("canvas"); c.width = CW; c.height = CH;
  const g = c.getContext("2d"), m = moment || grab();
  // crop the moment to 4:5 around the candy and whatever is above it (the face)
  const sw = m.width, sh = Math.min(m.height, sw * CH / CW), k = sw / W;
  const sy = Math.max(0, Math.min(m.height - sh, (geom.cy + geom.R * 1.35) * k - sh));
  g.drawImage(m, 0, sy, sw, sh, 0, 0, CW, CH);
  if (lickShot && lickShot !== m) {   // the licking face, as an inset
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
  g.font = '52px "Orbit", monospace';
  g.fillStyle = g0.ok ? "#FFC23D" : "#FF6B81";
  g.fillText(g0.ok ? `${starText(stars(g0))}  ${g0.t.toFixed(1)}s` : `${g0.final}%`, 56, CH - 130);
  g.fillStyle = "rgba(255,255,255,.75)"; g.font = '34px "IBM Plex Sans KR", sans-serif';
  g.fillText("dot-connects.github.io/dot-lab", 56, CH - 64);
  return c;
}

let cardBlob = null;
async function share() {
  const url = location.origin + location.pathname, g0 = game;
  const text = `허공 달고나 ${SHAPES[g0.shape].name} — ${label(g0)}${g0.ok ? ` ${starText(stars(g0))} ${g0.t.toFixed(1)}초` : ` ${g0.final}%`}`;
  try {
    const file = cardBlob && new File([cardBlob], "dalgona.png", { type: "image/png" });
    if (file && navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], text: text + " " + url }); return; }
    if (navigator.share) { await navigator.share({ text, url }); return; }
  } catch (e) { if (e && e.name === "AbortError") return; }
  if (cardBlob) { const a = document.createElement("a"); a.href = URL.createObjectURL(cardBlob); a.download = "dalgona.png"; a.click(); }
}

async function showResult() {
  const g = game, rec = { stars: stars(g), time: +g.t.toFixed(1), pct: g.final }, prev = bestOf(g.shape);
  const isNew = better(rec, prev);
  if (isNew) store("dalgona.best2." + g.shape, JSON.stringify(rec));
  const best = isNew ? rec : prev;
  const r = $("result"); r.innerHTML = "";
  r.classList.toggle("win", g.ok);
  const el = (tag, cls, txt) => { const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };
  const h = el("h2", null, label(g));
  const img = el("img", "res-card"); img.alt = "결과 카드";
  const line = el("div", "res-line num", g.ok ? `${starText(rec.stars)}  ${rec.time.toFixed(1)}초` : `${g.final}% 파냄`);
  const sub = el("p", "res-sub", reason(g));
  const bestLine = el("p", "res-best",
    isNew && prev ? "새 기록!" : best && best.stars ? `${SHAPES[g.shape].name} 최고 ${starText(best.stars)} ${best.time.toFixed(1)}초` : best ? `${SHAPES[g.shape].name} 최고 ${best.pct}%` : "");
  const row = el("div", "row");
  const nextShape = ORDER[(ORDER.indexOf(g.shape) + 1) % ORDER.length];
  const primary = g.ok && nextShape !== ORDER[0]
    ? Object.assign(el("button", "go", `다음: ${SHAPES[nextShape].name} →`), { onclick: () => begin(nextShape) })
    : Object.assign(el("button", "go", "다시 하기"), { onclick: () => begin(g.shape) });
  const again = g.ok && nextShape !== ORDER[0] ? Object.assign(el("button", "sub", "다시 하기"), { onclick: () => begin(g.shape) }) : null;
  const shareB = Object.assign(el("button", "sub", "결과 공유"), { onclick: share });
  row.append(...[primary, again, shareB].filter(Boolean));
  const foot = el("div", "row foot");
  foot.append(Object.assign(el("button", "link", "다른 모양"), { onclick: showPick }), Object.assign(el("a", "link", "시켜본 것 더 보기 →"), { href: "../../../" }));
  r.append(h, img, line, sub, bestLine, row, foot);
  $("hud").hidden = true;
  show("result");
  const card = await makeCard();
  img.src = card.toDataURL("image/jpeg", 0.86);
  card.toBlob(b => { cardBlob = b; }, "image/png");
}

// ---------- touch input ----------
const pos = e => { const b = app.getBoundingClientRect(); touch.x = e.clientX - b.left; touch.y = e.clientY - b.top; };
const touchTip = () => ({ x: touch.x, y: touch.y - (lickMode ? 0 : TOUCH_LIFT) });
app.addEventListener("pointerdown", e => {
  if (mode !== "touch" || !game || game.done || e.target.closest("button,a")) return;
  pos(e); touch.down = true; touch.t0 = performance.now(); touch.mt = 0; touch.fresh = true; touch.moved = 0;
  try { app.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
});
app.addEventListener("pointermove", e => {
  const px = touch.x, py = touch.y; pos(e);
  if (!touch.down) return;
  touch.moved += Math.hypot(touch.x - px, touch.y - py);
  // like camera frames: speed is measured between move events, frames without one hold the needle still
  const now = performance.now(); touch.mdt = Math.min(0.1, (now - (touch.mt || now)) / 1000); touch.mt = now; touch.fresh = true;
});
for (const t of ["pointerup", "pointercancel"]) app.addEventListener(t, () => {
  if (!touch.down) return;
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
const STAGE_TEXT = ["실금 · 아직 괜찮아요", "갈라지는 중 · 살살", "크게 갈라졌어요"];
let last = performance.now(), lastStatus = "";
function frame(now) {
  requestAnimationFrame(frame);
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
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
  const lickOn = lick && Math.hypot(lick.x - geom.cx, lick.y - geom.cy) < geom.R;
  if (game) {
    const { carve } = step(game, dt, { needle, jitter, lick, moveDt });
    for (const ev of game.events.splice(0)) {
      if (ev.type === "win") { winAt = now + 350; buzz(30); continue; }   // a beat of stillness before the break
      view.onEvent(ev);
      if (ev.type === "poke") { SFX.poke(ev.hit); const p = game.pits[game.pits.length - 1]; spawnCrumbs(p[0], p[1], ev.hit ? 7 : 3, true); }
      else if (ev.type === "segment") SFX.segment();
      else if (ev.type === "crack") SFX.hair();
      else if (ev.type === "spread") SFX.spread();
      else if (ev.type === "chip") SFX.chip();
      else if (ev.type === "lose") { SFX.lose(); loseT = 0; }
    }
    if (winAt && now >= winAt) { winAt = 0; view.onEvent({ type: "win" }); SFX.snap(); setTimeout(SFX.win, 250); capture = now + 700; }
    if (loseT >= 0) { loseT += dt; if (!moment && loseT > 0.55) capture = now; }
    if (carve > 0.05 && needle && !needle.held) spawnCrumbs(needle.x, needle.y, Math.random() < carve ? 1 : 0, false);
    if (lickOn) { lickFor += dt; if (!lickShot && lickFor > 0.8 && mode === "camera") lickShot = "next"; } else lickFor = 0;
    if (audio) { audio.gn.gain.value = game.done ? 0 : carve * 0.22; audio.bp.frequency.value = 2400 + carve * 1800; }
    if (game.started) $("hint").textContent = "";
    const left = Math.max(0, Math.ceil(RULES.T_LIMIT - game.t));
    const tm = $("timer"); tm.textContent = `0${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
    tm.classList.toggle("hurry", left <= 10 && game.started);
    const pct = percent(game);
    $("pc").textContent = pct + "%";
    const open = game.cracks.filter(c => !c.done);
    const stg = open.reduce((a, c) => Math.max(a, crackStage(game, c) + 1), 0);
    const lk = lickOn && wetAt(game, lick.x, lick.y);
    const st = game.done ? (game.ok ? "떼어냈어요!" : game.cause === "melt" ? "녹아서 뚫렸어요" : game.cause === "time" ? "시간 끝" : "깨졌어요")
      : game.stress >= RULES.HAIR ? "금이 자라요 · 손 멈춰요"
      : lickOn ? (lk > 0.95 ? "녹고 있어요 · 한곳만 핥으면 구멍" : "핥는 중 · 핥은 곳은 덜 깨져요")
      : lick ? "입을 달고나에 더 가까이"
      : stg ? STAGE_TEXT[stg - 1]
      : game.stress > 0.3 ? "조심 · 압력이 올라요"
      : pct >= 90 ? "거의 다 됐어요 · 숨 참고"
      : game.started ? "천천히 긁는 중" : `${SHAPES[game.shape].name} · 첫 긁기부터 시간이 가요`;
    if (st !== lastStatus) { $("sw").textContent = st; lastStatus = st; }
    $("sw").className = game.stress >= RULES.HAIR || (game.done && !game.ok) ? "bad" : game.done ? "good" : "";
    if (game.done && !resultShown && !endAt) endAt = now + (game.ok ? 2600 : 2000);
    if (endAt && now >= endAt) { endAt = 0; if (!moment) moment = grab(); resultShown = true; freezeFrame(); showResult(); }
  }

  ctx.clearRect(0, 0, W, H);
  if (!game || resultShown) { if (DEBUG && s) debug(s); return; }
  view.draw(ctx, dt, game.done ? null : percent(game) / 100);
  if (game.done && !game.ok) drawMissing(now);
  drawCrumbs(dt);
  if (!game.done) {
    if (s) {
      if (s.tongue) drawTongue(s.tongue, !!lickOn, now);
      if (s.hand) handFront(s.hand, s.hs);
      if (s.needle) { drawNeedle(s.needle, s.dir || [0.55, 0.83], !s.needle.held || !!s.hand); drawStressRing(s.needle); }
    } else if (mode === "touch") {
      if (lickMode) { if (touch.down) drawTongue({ base: [touch.x, touch.y - geom.R * 0.55], dir: [0, 1], len: geom.R * 0.55, width: geom.R * 0.34 }, true, now); }
      else if (touch.down) { const p = touchTip(); drawNeedle(p, [0.45, 0.89], true); drawStressRing(p); }
    }
  }
  if (loseT > 0.25) drawStamp(loseT - 0.25);
  if (lickShot === "next") lickShot = grab();
  if (capture && now >= capture) { capture = null; moment = grab(); }
  if (DEBUG && s) debug(s);
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
