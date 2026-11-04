import { createGame, layout, step, poke, squeeze, percent, label, reason, SHAPES, RULES, wetAt, cellCentre } from "./engine.js";
import { startCamera, loadModels, createTracker } from "./camera.js";

const $ = id => document.getElementById(id);
const app = $("app"), cv = $("cv"), ctx = cv.getContext("2d"), video = $("video");
let W = 0, H = 0, DPR = 1, geom = { cx: 0, cy: 0, R: 1 }, candy = null;
let mode = null;          // "camera" | "touch"
let tracker = null, game = null, lickMode = false, audio = null;
const touch = { x: 0, y: 0, down: false };

const store = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch { return null; } };

// ---------- layout ----------
function resize() {
  const b = app.getBoundingClientRect();
  DPR = Math.min(2, devicePixelRatio || 1); W = b.width; H = b.height;
  cv.width = W * DPR; cv.height = H * DPR; ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  geom = { cx: W / 2, cy: H * 0.55, R: Math.min(W * 0.37, H * 0.27) };
  candy = makeCandy(geom.R);
  if (game) { layout(game, geom); mainPath = shapePath(); game.last = null; }
  if (tracker) tracker.size(W, H);
}
addEventListener("resize", resize);

function makeCandy(R) {
  const s = Math.ceil(R * 2.3), o = document.createElement("canvas");
  o.width = s * DPR; o.height = s * DPR;
  const g = o.getContext("2d"); g.scale(DPR, DPR); const c = s / 2;
  let gr = g.createRadialGradient(c - R * 0.2, c - R * 0.25, R * 0.1, c, c, R);
  gr.addColorStop(0, "#e8b25c"); gr.addColorStop(0.6, "#cf8b34"); gr.addColorStop(0.92, "#a8621d"); gr.addColorStop(1, "#7c4512");
  g.fillStyle = gr; g.beginPath();
  for (let i = 0; i <= 72; i++) { const t = i / 72 * Math.PI * 2, rr = R * (1 + 0.018 * Math.sin(t * 7) + 0.01 * Math.sin(t * 13)); g.lineTo(c + rr * Math.cos(t), c + rr * Math.sin(t)); }
  g.fill();
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 420; i++) {
    const a = rnd() * Math.PI * 2, d = Math.sqrt(rnd()) * R * 0.95, r = 0.6 + rnd() * rnd() * R * 0.035;
    g.fillStyle = rnd() < 0.5 ? "rgba(255,228,160,.28)" : "rgba(110,55,10,.22)";
    g.beginPath(); g.arc(c + d * Math.cos(a), c + d * Math.sin(a), r, 0, 7); g.fill();
  }
  gr = g.createRadialGradient(c - R * 0.35, c - R * 0.45, 0, c - R * 0.35, c - R * 0.45, R * 0.6);
  gr.addColorStop(0, "rgba(255,245,215,.35)"); gr.addColorStop(1, "rgba(255,245,215,0)");
  g.fillStyle = gr; g.beginPath(); g.arc(c, c, R, 0, 7); g.fill();
  return { cv: o, s };
}

// ---------- drawing ----------
let mainPath = null, popT = 0;
function shapePath() {
  const p = new Path2D(); game.paths[0].forEach((q, i) => (i ? p.lineTo(q[0], q[1]) : p.moveTo(q[0], q[1]))); p.closePath(); return p;
}
function drawTin() {
  const { cx, cy, R } = geom;
  ctx.fillStyle = "#6c7579"; ctx.beginPath(); ctx.arc(cx, cy + 4, R * 1.16, 0, 7); ctx.fill();
  const g = ctx.createRadialGradient(cx - R * 0.4, cy - R * 0.5, 0, cx, cy, R * 1.16);
  g.addColorStop(0, "#e8edef"); g.addColorStop(1, "#6c7579"); ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, R * 1.13, 0, 7); ctx.fill();
}
function drawCandy(g2, forSnap) {
  const { cx, cy, R } = geom, s = candy.s, img = () => g2.drawImage(candy.cv, cx - s / 2, cy - s / 2, s, s);
  const popped = game && game.ok && !forSnap && popT > 0.01;
  if (!popped) img();
  if (!game) return;
  if (popped) {
    g2.save(); const hole = new Path2D(); hole.rect(0, 0, W, H); hole.addPath(mainPath); g2.clip(hole, "evenodd"); img();
    g2.fillStyle = "rgba(30,15,5,.55)"; g2.fill(mainPath); g2.restore();
  }
  g2.lineCap = "round"; g2.lineJoin = "round";
  game.paths.forEach((path, pi) => {
    g2.strokeStyle = "rgba(110,58,14,.55)"; g2.lineWidth = Math.max(2, R * 0.022);
    g2.beginPath(); path.forEach((p, i) => (i ? g2.lineTo(p[0], p[1]) : g2.moveTo(p[0], p[1]))); g2.stroke();
    const pr = game.prog[pi];
    for (let i = 1; i < path.length; i++) {
      const v = (pr[i] + pr[i - 1]) / 2; if (v < 0.02) continue;
      g2.strokeStyle = `rgba(${Math.round(70 - 40 * v)},${Math.round(35 - 20 * v)},8,${0.25 + 0.7 * v})`;
      g2.lineWidth = Math.max(1.5, R * (0.012 + 0.022 * v));
      g2.beginPath(); g2.moveTo(path[i - 1][0], path[i - 1][1]); g2.lineTo(path[i][0], path[i][1]); g2.stroke();
    }
  });
  const cs = 2 * R / RULES.GRID;
  for (let k = 0; k < game.wetG.length; k++) {
    const w = game.wetG[k], m = game.meltG[k]; if (w < 0.03 && m < 0.03) continue;
    const [x, y] = cellCentre(game, k); if (Math.hypot(x - cx, y - cy) > R) continue;
    if (w > 0.03) { const gr = g2.createRadialGradient(x, y, 0, x, y, cs * 1.1); gr.addColorStop(0, `rgba(255,244,222,${Math.min(0.45, 0.32 * w)})`); gr.addColorStop(1, "rgba(255,244,222,0)"); g2.fillStyle = gr; g2.fillRect(x - cs * 1.1, y - cs * 1.1, cs * 2.2, cs * 2.2); }
    if (m > 0.03) { g2.fillStyle = `rgba(20,32,28,${Math.min(0.92, m)})`; g2.beginPath(); g2.arc(x, y, cs * 0.62 * Math.min(1, m + 0.3), 0, 7); g2.fill(); }
  }
  for (const p of game.pits) {
    g2.fillStyle = "rgba(60,28,6,.75)"; g2.beginPath(); g2.arc(p[0], p[1], Math.max(1.6, R * 0.016), 0, 7); g2.fill();
    g2.fillStyle = "rgba(255,236,190,.35)"; g2.beginPath(); g2.arc(p[0] + 0.8, p[1] + 0.8, Math.max(1, R * 0.009), 0, 7); g2.fill();
  }
  g2.strokeStyle = "rgba(255,236,190,.35)"; g2.lineWidth = 1; g2.beginPath();
  for (const s2 of game.scratches) { g2.moveTo(s2[0], s2[1]); g2.lineTo(s2[2], s2[3]); } g2.stroke();
  for (const c of game.cracks) {
    const n = Math.max(2, Math.round(c.pts.length * c.grow));
    g2.strokeStyle = "rgba(40,18,4,.95)"; g2.lineWidth = c.inward ? 3 : 2.2; g2.beginPath();
    for (let i = 0; i < n; i++) { const p = c.pts[i]; i ? g2.lineTo(p[0], p[1]) : g2.moveTo(p[0], p[1]); } g2.stroke();
    g2.strokeStyle = "rgba(255,230,180,.45)"; g2.lineWidth = 1; g2.beginPath();
    for (let i = 0; i < n; i++) { const p = c.pts[i]; i ? g2.lineTo(p[0] + 1.2, p[1] + 1.2) : g2.moveTo(p[0] + 1.2, p[1] + 1.2); } g2.stroke();
    if (!c.inward && c.grow >= 1) { const e = c.pts[c.pts.length - 1]; g2.fillStyle = "#14201c"; g2.beginPath(); g2.arc(e[0], e[1], R * 0.06, 0, 7); g2.fill(); }
  }
  if (popped) {
    const lift = popT * R * 0.12; g2.save(); g2.translate(0, -lift);
    g2.shadowColor = "rgba(0,0,0,.5)"; g2.shadowBlur = 18 * popT; g2.shadowOffsetY = lift;
    g2.save(); g2.clip(mainPath); img(); g2.restore(); g2.restore();
  }
}
function drawNeedle(n) {
  if (!n) return;
  const { R } = geom, hot = performance.now() - lastPokeAt < 180;
  ctx.save(); ctx.globalAlpha = n.down ? 1 : 0.45; ctx.lineCap = "round";
  ctx.strokeStyle = "#dfe3e6"; ctx.lineWidth = 2.4; ctx.beginPath(); ctx.moveTo(n.x, n.y); ctx.lineTo(n.x + R * 0.62, n.y + R * 0.62); ctx.stroke();
  ctx.strokeStyle = "#7d858a"; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(n.x + 1, n.y + 1.5); ctx.lineTo(n.x + R * 0.62 + 1, n.y + R * 0.62 + 1.5); ctx.stroke();
  ctx.strokeStyle = hot ? "#ff2e7e" : "#ffffffcc"; ctx.lineWidth = hot ? 4 : 2;
  ctx.beginPath(); ctx.arc(n.x, n.y, hot ? R * 0.12 : R * 0.08, 0, 7); ctx.stroke();
  ctx.restore();
}
function drawTongue(p, active) {
  if (!p) return;
  const { R } = geom;
  ctx.save(); ctx.globalAlpha = active ? 0.85 : 0.35; ctx.fillStyle = "#ff7a9a";
  ctx.beginPath(); ctx.ellipse(p.x, p.y, R * 0.16, R * 0.21, 0, 0, 7); ctx.fill();
  ctx.strokeStyle = "#d94f72"; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(p.x, p.y - R * 0.16); ctx.lineTo(p.x, p.y + R * 0.05); ctx.stroke();
  ctx.restore();
}
function snapshot() {
  const s = Math.ceil(geom.R * 2.5), o = document.createElement("canvas"); o.width = o.height = s * DPR;
  const g = o.getContext("2d"); g.setTransform(DPR, 0, 0, DPR, 0, 0); g.translate(s / 2 - geom.cx, s / 2 - geom.cy);
  if (game.ok) g.clip(mainPath);
  drawCandy(g, true);
  return o.toDataURL("image/png");
}

// ---------- sound ----------
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
function burst(big) {
  if (!audio) return; const { ac, buf } = audio, s = ac.createBufferSource(); s.buffer = buf;
  const hp = ac.createBiquadFilter(); hp.type = "highpass"; hp.frequency.value = big ? 900 : 1800;
  const g = ac.createGain(), t = ac.currentTime; g.gain.setValueAtTime(big ? 0.9 : 0.5, t); g.gain.exponentialRampToValueAtTime(0.001, t + (big ? 0.35 : 0.12));
  s.connect(hp).connect(g).connect(ac.destination); s.start(t); s.stop(t + 0.4);
  try { navigator.vibrate && navigator.vibrate(big ? [60, 40, 120] : 25); } catch { /* not supported */ }
}
function tick(hit) {
  if (!audio) return; const { ac } = audio, o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime;
  o.type = "triangle"; o.frequency.value = hit ? 1900 : 1300; g.gain.setValueAtTime(0.25, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.05);
  o.connect(g).connect(ac.destination); o.start(t); o.stop(t + 0.06);
}

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
    msg.textContent = "손 인식을 불러오는 중이에요(처음 한 번 몇 초)…";
    const models = await loadModels();
    tracker = createTracker(video, models, {
      onPoke: (x, y) => { if (game) { poke(game, x, y); lastPokeAt = performance.now(); } },
      onSqueeze: (x, y, sp) => { if (game) squeeze(game, x, y, sp); },
    });
    mode = "camera"; video.hidden = false; $("shade").hidden = false; $("nocam").hidden = true;
    resize(); showPick();
  } catch (e) {
    if (camStream) camStream.getTracks().forEach(t => t.stop());   // no camera light left on in touch mode
    camStream = null; $("camGo").disabled = false;
    msg.textContent = e && e.message === "timeout"
      ? "카메라가 응답하지 않아요. 크롬에서 열거나 화면 터치로 해 보세요."
      : "카메라를 쓸 수 없어요. 화면 터치로 해 보세요.";
  }
};
$("touchGo").onclick = () => { initAudio(); mode = "touch"; showPick(); };

function showPick() {
  game = null; $("hud").hidden = true; show("pick");
  const picks = $("picks"); picks.innerHTML = "";
  for (const [k, s] of Object.entries(SHAPES)) {
    const b = document.createElement("button"); b.textContent = s.icon; b.setAttribute("aria-label", s.name);
    b.onclick = () => begin(k); picks.appendChild(b);
  }
  const best = Object.entries(SHAPES).map(([k, s]) => { const v = store("dalgona.best." + k); return v ? `${s.icon} ${v}%` : null; }).filter(Boolean);
  $("best").textContent = best.length ? "최고 " + best.join(" · ") : "";
}

let lastPokeAt = 0, resultShown = false;
function begin(shape) {
  game = createGame(shape, geom); mainPath = shapePath(); popT = 0; resultShown = false; lickMode = false;
  show(null); $("hud").hidden = false;
  $("lick").hidden = mode !== "touch"; $("lick").setAttribute("aria-pressed", "false"); $("lick").textContent = "혀로 핥기";
  $("hint").textContent = mode === "camera" ? "엄지와 검지를 모아 바늘을 집고 달고나에 대 보세요" : "선을 따라 천천히 긁거나 톡톡 찔러 보세요";
}

$("lick").addEventListener("pointerdown", e => e.stopPropagation());
$("lick").onclick = () => {
  lickMode = !lickMode; $("lick").setAttribute("aria-pressed", String(lickMode)); $("lick").textContent = lickMode ? "바늘 다시 잡기" : "혀로 핥기";
};

function showResult() {
  const g = game, k = "dalgona.best." + g.shape, b = +(store(k) || 0);
  if (g.final > b) store(k, String(g.final));
  const r = $("result"); r.innerHTML = "";
  const h = document.createElement("h2"); h.textContent = label(g);
  const img = new Image(); img.className = "res-img"; img.alt = "내 달고나"; img.src = snapshot();
  const pct = document.createElement("div"); pct.className = "res-pct"; pct.textContent = g.final + "%";
  const why = document.createElement("p"); why.textContent = reason(g);
  const best = document.createElement("p"); best.style.cssText = "font-size:13px;color:var(--mute)";
  best.textContent = `${SHAPES[g.shape].name} 최고 ${Math.max(b, g.final)}%`;
  const row = document.createElement("div"); row.className = "row";
  const again = document.createElement("button"); again.className = "go"; again.textContent = "다시 하기"; again.onclick = () => begin(g.shape);
  const other = document.createElement("button"); other.className = "sub"; other.textContent = "다른 모양"; other.onclick = showPick;
  row.append(again, other);
  const more = document.createElement("a"); more.className = "link"; more.href = "../../../"; more.textContent = "시켜본 것 더 보기 →";
  r.append(h, img, pct, why, best, row, more);
  $("hud").hidden = true;
  show("result");
}

// ---------- touch input ----------
const pos = e => { const b = app.getBoundingClientRect(); touch.x = e.clientX - b.left; touch.y = e.clientY - b.top; };
app.addEventListener("pointerdown", e => {
  if (mode !== "touch" || !game || game.done || e.target.closest("button,a")) return;
  pos(e); touch.down = true; app.setPointerCapture(e.pointerId);
  if (!lickMode) { poke(game, touch.x, touch.y); lastPokeAt = performance.now(); }
});
app.addEventListener("pointermove", pos);
for (const t of ["pointerup", "pointercancel"]) app.addEventListener(t, () => { touch.down = false; });
// in-app browsers: keep the gesture on the page (README "Touch input inside Threads / Instagram")
// only while playing: the start and result screens may need to scroll on short phones
for (const t of ["touchstart", "touchmove"]) app.addEventListener(t, e => {
  if (game && !game.done && !e.target.closest("button,a")) e.preventDefault();
}, { passive: false });

// ---------- loop ----------
let last = performance.now();
function frame(now) {
  requestAnimationFrame(frame);
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  let needle = null, lick = null, jitter = 0, moveDt = 0;
  if (mode === "camera" && tracker) {
    const s = tracker.update(now);
    // between camera frames the needle has not moved: the engine must not read that as stop-and-jump
    needle = s.needle && (s.fresh ? s.needle : { ...s.needle, held: true }); jitter = s.jitter; lick = s.lick;
    moveDt = s.vdt;
  } else if (mode === "touch") {
    if (touch.down && !lickMode) needle = { x: touch.x, y: touch.y, down: true };
    if (touch.down && lickMode) lick = { x: touch.x, y: touch.y };
  }
  if (game) {
    const { carve } = step(game, dt, { needle, jitter, lick, moveDt });
    for (const ev of game.events.splice(0)) {
      if (ev.type === "crack") burst(ev.inward);
      else if (ev.type === "poke") tick(ev.hit);
      else if (ev.type === "squeeze") tick(false);
    }
    if (audio) { audio.gn.gain.value = carve * 0.22; audio.bp.frequency.value = 2400 + carve * 1800; }
    if (game.started) $("hint").textContent = "";
    const left = Math.max(0, Math.ceil(RULES.T_LIMIT - game.t));
    $("timer").textContent = `0${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
    const p = percent(game); $("pc").textContent = p + "%"; $("pb").style.width = p + "%";
    const st = game.stress, sw = $("sw");
    sw.textContent = st > 0.7 ? "손이 떨립니다" : st > 0.35 ? "조심" : lick ? (wetAt(game, lick.x, lick.y) > 0.9 ? "녹고 있어요" : "말랑해지는 중") : "손이 안정적";
    sw.style.color = st > 0.7 ? "#ff6a6a" : "";
    if (game.done) {
      if (game.ok) popT = Math.min(1, popT + dt * 1.2);
      if (!resultShown) { resultShown = true; setTimeout(showResult, game.ok ? 1400 : 1100); }
    }
  }
  ctx.clearRect(0, 0, W, H);
  drawTin(); drawCandy(ctx, false);
  if (game && !game.done) {
    if (mode === "camera" && tracker && tracker.state.mouth) drawTongue({ x: tracker.state.mouth[0], y: tracker.state.mouth[1] }, !!lick);
    else if (lick) drawTongue(lick, true);
    drawNeedle(needle);
  }
}

resize();
requestAnimationFrame(frame);
