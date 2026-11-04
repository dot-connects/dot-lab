import { createGame, layout, step, poke, squeeze, percent, label, reason, SHAPES, RULES, wetAt, crackStage } from "./engine.js";
import { startCamera, loadModels, createTracker, BONES, PALM } from "./camera.js";
import { createCandyView } from "./render.js";

const $ = id => document.getElementById(id);
const app = $("app"), cv = $("cv"), ctx = cv.getContext("2d"), video = $("video");
const DEBUG = new URLSearchParams(location.search).has("debug");
let W = 0, H = 0, DPR = 1, geom = { cx: 0, cy: 0, R: 1 };
let mode = null;          // "camera" | "touch"
let tracker = null, game = null, view = null, lickMode = false, audio = null;
const touch = { x: 0, y: 0, down: false };
const TOUCH_LIFT = 46;    // touch mode: the needle tip sits above the finger so the finger does not hide it

const store = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch { return null; } };

// ---------- layout ----------
const hc = document.createElement("canvas"), hg = hc.getContext("2d");   // the real hand / mouth cut out of the video
function resize() {
  const b = app.getBoundingClientRect();
  DPR = Math.min(2, devicePixelRatio || 1); W = b.width; H = b.height;
  cv.width = W * DPR; cv.height = H * DPR; ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  hc.width = cv.width; hc.height = cv.height;
  geom = { cx: W / 2, cy: H * 0.58, R: Math.min(W * 0.36, H * 0.2) };
  if (game) { layout(game, geom); view = createCandyView(game, DPR); }
  if (tracker) tracker.size(W, H);
}
addEventListener("resize", resize);

// ---------- camera layers in front of the candy ----------
function videoCover(g) {
  const vw = video.videoWidth, vh = video.videoHeight, k = Math.max(W / vw, H / vh), dw = vw * k, dh = vh * k;
  g.save(); g.translate(W, 0); g.scale(-1, 1); g.drawImage(video, (W - dw) / 2, (H - dh) / 2, dw, dh); g.restore();
}
// cut the hand (or the lips) out of the video and lay it over the candy, so the candy sits between face and hand
function frontLayer(shape) {
  hg.setTransform(1, 0, 0, 1, 0, 0); hg.clearRect(0, 0, hc.width, hc.height); hg.setTransform(DPR, 0, 0, DPR, 0, 0);
  hg.globalCompositeOperation = "source-over"; hg.fillStyle = "#fff"; hg.strokeStyle = "#fff"; hg.lineCap = "round"; hg.lineJoin = "round";
  shape(hg);
  hg.globalCompositeOperation = "source-in"; videoCover(hg); hg.globalCompositeOperation = "source-over";
  ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.drawImage(hc, 0, 0); ctx.restore();
}
function handShape(P, hs) {
  return g => {
    g.beginPath(); PALM.forEach((i, k) => (k ? g.lineTo(P[i][0], P[i][1]) : g.moveTo(P[i][0], P[i][1]))); g.closePath(); g.fill();
    g.lineWidth = hs * 0.34; g.stroke();
    for (const [a, b] of BONES) { g.lineWidth = hs * (a === 0 || b === 0 ? 0.36 : a < 5 && b < 5 ? 0.3 : 0.24); g.beginPath(); g.moveTo(P[a][0], P[a][1]); g.lineTo(P[b][0], P[b][1]); g.stroke(); }
  };
}
function lipsShape(lips) {
  return g => {
    let mx = 0, my = 0; for (const p of lips) { mx += p[0]; my += p[1]; } mx /= lips.length; my /= lips.length;
    g.beginPath(); lips.forEach(([x, y], i) => { const X = mx + (x - mx) * 1.35, Y = my + (y - my) * 1.5; i ? g.lineTo(X, Y) : g.moveTo(X, Y); }); g.closePath();
    g.shadowColor = "#fff"; g.shadowBlur = 10 * DPR; g.fill(); g.shadowBlur = 0;
  };
}

// ---------- drawing ----------
function drawTongue(t, active) {
  const [bx, by] = t.base, [dx, dy] = t.dir, L = t.len, w = t.width;
  ctx.save(); ctx.translate(bx, by); ctx.rotate(Math.atan2(dy, dx) - Math.PI / 2); ctx.globalAlpha = active ? 1 : 0.8;
  const tg = ctx.createLinearGradient(0, 0, 0, L); tg.addColorStop(0, "#c94a63"); tg.addColorStop(1, "#f07e95");
  ctx.fillStyle = tg; ctx.beginPath(); ctx.moveTo(-w / 2, 0);
  ctx.bezierCurveTo(-w * 0.62, L * 0.7, -w * 0.38, L, 0, L); ctx.bezierCurveTo(w * 0.38, L, w * 0.62, L * 0.7, w / 2, 0); ctx.closePath(); ctx.fill();
  ctx.strokeStyle = "rgba(150,35,65,.55)"; ctx.lineWidth = Math.max(1, w * 0.05); ctx.beginPath(); ctx.moveTo(0, L * 0.1); ctx.lineTo(0, L * 0.8); ctx.stroke();
  ctx.fillStyle = "rgba(255,255,255,.35)"; ctx.beginPath(); ctx.ellipse(-w * 0.15, L * 0.55, w * 0.08, L * 0.12, 0.3, 0, 7); ctx.fill();
  ctx.restore();
}
function drawLickRing(p) {
  ctx.save(); ctx.strokeStyle = "#ff2e7e"; ctx.lineWidth = 2.5; ctx.setLineDash([5, 4]);
  ctx.beginPath(); ctx.arc(p.x, p.y, geom.R * 0.2, 0, 7); ctx.stroke(); ctx.restore();
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
function snapshotURL() {
  const c = view.snapshot(), o = document.createElement("canvas"), s = 480;
  o.width = o.height = s; const g = o.getContext("2d"); g.drawImage(c, 0, 0, s, s);
  return o.toDataURL("image/png");
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
  hair: () => { noise(4200, 0.06, 0.5); buzz(15); },
  spread: () => { noise(2600, 0.1, 0.6); buzz([20, 30, 20]); },
  chip: () => { noise(1800, 0.14, 0.7); setTimeout(() => noise(3000, 0.05, 0.3), 260); buzz([30, 40, 30]); },
  win: () => { noise(1500, 0.12, 0.8); [880, 1320, 1760].forEach((f, i) => setTimeout(() => tone(f, 0.35, 0.18, "sine"), 180 + i * 110)); buzz([40, 60, 40]); },
  lose: () => { noise(700, 0.4, 0.9, "lowpass"); buzz([80, 40, 160]); },
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
    tracker = createTracker(video, models, {
      onPoke: (x, y) => { if (game) poke(game, x, y); },
      onSqueeze: (x, y, sp) => { if (game) squeeze(game, x, y, sp); },
    });
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

function showPick() {
  game = null; view = null; endAt = 0; $("hud").hidden = true; $("freeze").hidden = true; show("pick");
  const picks = $("picks"); picks.innerHTML = "";
  for (const [k, s] of Object.entries(SHAPES)) {
    const b = document.createElement("button"); b.textContent = s.icon; b.setAttribute("aria-label", s.name);
    b.onclick = () => begin(k); picks.appendChild(b);
  }
  const best = Object.entries(SHAPES).map(([k, s]) => { const v = store("dalgona.best." + k); return v ? `${s.icon} ${v}%` : null; }).filter(Boolean);
  $("best").textContent = best.length ? "최고 " + best.join(" · ") : "";
}

let resultShown = false, endAt = 0;
function begin(shape) {
  game = createGame(shape, geom); view = createCandyView(game, DPR); resultShown = false; lickMode = false; endAt = 0;
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

function showResult() {
  const g = game, k = "dalgona.best." + g.shape, b = +(store(k) || 0);
  if (g.final > b) store(k, String(g.final));
  const r = $("result"); r.innerHTML = "";
  r.classList.toggle("win", g.ok);
  const h = document.createElement("h2"); h.textContent = label(g);
  const img = new Image(); img.className = "res-img"; img.alt = "내 달고나"; img.src = snapshotURL();
  const pct = document.createElement("div"); pct.className = "res-pct"; pct.textContent = g.final + "%";
  const why = document.createElement("p"); why.textContent = reason(g);
  const best = document.createElement("p"); best.className = "res-best";
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
const touchTip = () => ({ x: touch.x, y: touch.y - (lickMode ? 0 : TOUCH_LIFT) });
app.addEventListener("pointerdown", e => {
  if (mode !== "touch" || !game || game.done || e.target.closest("button,a")) return;
  pos(e); touch.down = true; touch.t0 = performance.now(); touch.mt = 0; touch.fresh = true; touch.x0 = touch.x; touch.y0 = touch.y; touch.moved = 0;
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

// ---------- loop ----------
const STAGE_TEXT = ["실금 · 아직 괜찮아요", "갈라지는 중 · 살살", "크게 갈라졌어요"];
let last = performance.now(), lastStatus = "";
function frame(now) {
  requestAnimationFrame(frame);
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  let needle = null, lick = null, jitter = 0, moveDt = 0, s = null;
  if (mode === "camera" && tracker) {
    s = tracker.update(now);
    // between camera frames the needle has not moved: the engine must not read that as stop-and-jump
    needle = s.needle && (s.fresh ? s.needle : { ...s.needle, held: true }); jitter = s.jitter; lick = s.lick;
    moveDt = s.vdt;
  } else if (mode === "touch") {
    const p = touchTip();
    // a short tap is a poke, not a scrape: the needle only scrapes once the finger has stayed down
    if (touch.down && !lickMode && (performance.now() - touch.t0 > 120 || touch.moved > 8)) {
      needle = { ...p, down: true, held: !touch.fresh && !!game && !!game.last }; moveDt = touch.mdt || 0;
    }
    touch.fresh = false;
    if (touch.down && lickMode) lick = p;
  }
  if (game) {
    const { carve } = step(game, dt, { needle, jitter, lick, moveDt });
    for (const ev of game.events.splice(0)) {
      view.onEvent(ev);
      if (ev.type === "poke") SFX.poke(ev.hit);
      else if (ev.type === "squeeze") SFX.poke(false);
      else if (ev.type === "crack") SFX.hair();
      else if (ev.type === "spread") SFX.spread();
      else if (ev.type === "chip") SFX.chip();
      else if (ev.type === "win") SFX.win();
      else if (ev.type === "lose") SFX.lose();
    }
    if (audio) { audio.gn.gain.value = game.done ? 0 : carve * 0.22; audio.bp.frequency.value = 2400 + carve * 1800; }
    if (game.started) $("hint").textContent = "";
    const left = Math.max(0, Math.ceil(RULES.T_LIMIT - game.t));
    const tm = $("timer"); tm.textContent = `0${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
    tm.classList.toggle("hurry", left <= 10 && game.started);
    $("pc").textContent = percent(game) + "%";
    const open = game.cracks.filter(c => !c.done);
    const stg = open.reduce((a, c) => Math.max(a, crackStage(game, c) + 1), 0);
    const lk = lick && wetAt(game, lick.x, lick.y);
    const st = game.done ? (game.ok ? "떼어냈어요!" : game.cause === "melt" ? "녹아서 뚫렸어요" : game.cause === "time" ? "시간 끝" : "깨졌어요")
      : game.stress >= RULES.HAIR ? "금이 자라요 · 손 멈춰요"
      : lick ? (lk > 0.95 ? "녹고 있어요 · 너무 오래 핥으면 구멍" : "핥는 중 · 말랑해져요")
      : stg ? STAGE_TEXT[stg - 1]
      : game.stress > 0.3 ? "조심 · 압력이 올라요"
      : game.started ? "천천히 긁는 중" : mode === "camera" ? "바늘을 집어 보세요" : "선을 따라 긁어 보세요";
    if (st !== lastStatus) { $("sw").textContent = st; lastStatus = st; }
    $("sw").className = game.stress >= RULES.HAIR || (game.done && !game.ok) ? "bad" : game.done ? "good" : "";
    if (game.done && !resultShown && !endAt) endAt = now + (game.ok ? 2200 : 1600);
    if (endAt && now >= endAt) { endAt = 0; resultShown = true; freezeFrame(); showResult(); }
  }

  ctx.clearRect(0, 0, W, H);
  if (!game || resultShown) { if (DEBUG && s) debug(s); return; }
  view.draw(ctx, dt, game.done ? null : percent(game) / 100);
  if (!game.done) {
    if (s) {
      if (s.tongue) { frontLayer(lipsShape(s.lips)); drawTongue(s.tongue, !!lick); }
      if (lick) drawLickRing(lick);
      if (s.needle) {
        const tip = s.needle;
        drawNeedle(tip, s.dir || [0.55, 0.83], true);
        if (s.hand) frontLayer(handShape(s.hand, s.hs));
        drawStressRing(tip);
      } else if (s.hand && s.grip) {
        if (s.hand) frontLayer(handShape(s.hand, s.hs));
      }
    } else if (mode === "touch") {
      if (lickMode) { if (touch.down) { drawTongue({ base: [touch.x, touch.y - geom.R * 0.5], dir: [0, 1], len: geom.R * 0.5, width: geom.R * 0.32 }, true); drawLickRing({ x: touch.x, y: touch.y }); } }
      else if (touch.down) { const p = touchTip(); drawNeedle(p, [0.45, 0.89], true); drawStressRing(p); }
    }
  }
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
