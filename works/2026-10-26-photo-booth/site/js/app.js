// App shell: screens + role routing (PLAN Task 6 role table). Capture/compose modules load on demand,
// so the first screen needs only this file + api.js, and MediaPipe loads after the first tap.
import * as api from "./api.js";
import { mockCapture, mockCompose, mockFrames, mockFilters } from "./ui.js";

const $ = id => document.getElementById(id);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const Q = new URLSearchParams(location.search);
const MOCK = Q.has("mock");                         // dev: no MediaPipe, placeholder cut-outs
// Round 4 (booth style): one tap shoots 8 in a row, SECS each; then pick 4 (tap order = strip order)
const SHOTS = 8, SECS = Q.has("fast") ? 4 : 5;      // 5 s each (operator 10-27); ?fast = tests only
const POSES = ["브이!", "반쪽 하트", "꽃받침", "깜짝 놀란 척", "윙크 한 번", "볼 빵빵", "친구 쪽 쳐다보기", "마음대로!"];
const NAMES = [1, 2, 3, 4].flatMap(c => [`${c}-raw.jpg`, `${c}-cut.png`, `${c}-clip`]);
const CELL = 552 / 382;                             // strip cell aspect (compose LAYOUT CW:CH)
const sleep = ms => new Promise(r => setTimeout(r, ms));
const calm = () => matchMedia("(prefers-reduced-motion: reduce)").matches;
const today = new Date(), pad = n => String(n).padStart(2, "0");
const DATE = `${today.getFullYear()}.${pad(today.getMonth() + 1)}.${pad(today.getDate())}`;

// ---- modules ------------------------------------------------------------------------------------------------------
async function load(name, mock) {
  if (MOCK && mock) return mock;
  try { return await import(`./${name}.js`); }
  catch (e) { console.warn(`[booth] ${name}.js unavailable, using placeholder:`, e.message); if (mock) return mock; throw e; }
}
let env = { app: "브라우저", inApp: false, isIOS: false, framed: top !== self, chromeUrl: u => u };

// ---- state --------------------------------------------------------------------------------------------------------
const S = {
  id: Q.get("s"), side: null, token: null,      // side = what this page shoots/uploads ("a" | "b")
  cuts: [], sent: new Set(), ghost: [],          // the 4 picked Cuts (strip order), uploaded names, A's cut-out URLs (B's overlay)
  shots: [], shotUrls: [], pick: [], pairs: null, // all 8 shots + their raw URLs until the pick; B: A's cut per cell
  stop: false, redo: false, hq: [],              // 멈추기 pressed; 전체 다시 찍기 used (once per page); HQ cut path per cell
  cam: null, cap: null, bound: false, busy: false,
  movable: false, lay: { x: 0, y: 0, scale: 1 },  // A's choice (opts.movable); B's placement of A, fractions of the cell
};
const HINT_A = "친구 자리를 생각해서 한쪽으로 비켜서 찍어 줘";
const HINT_B = "친구를 끌어서 옮기고, 두 손가락으로 크기를 바꿔 봐";
const HINT_SHOOT = "셔터 한 번이면 8장을 이어서 찍어 · 한 장에 5초";
const shareUrl = () => `${location.origin}${location.pathname}?s=${S.id}`;
const token = () => S.token || api.loadRole(S.id)?.token;

// ---- screens ------------------------------------------------------------------------------------------------------
function show(name) {
  const from = document.body.dataset.screen;
  for (const s of $$(".screen")) s.hidden = s.id !== "s-" + name;
  document.body.dataset.screen = name;
  $("s-" + name).classList.toggle("slide", name === "shoot" && from !== "shoot");   // land -> shoot slides, the rest fades
  if (scrollY > 1) scrollTo(0, 1);
}
let toastT;
function toast(text, ms = 2600) {
  const t = $("toast"); t.textContent = text; t.hidden = false;
  clearTimeout(toastT); toastT = setTimeout(() => t.hidden = true, ms);
}
function msg({ title, text = "", btn, onBtn, btn2, onBtn2 }) {
  $("msgTitle").textContent = title; $("msgText").textContent = text;
  $("msgBtn").textContent = btn; $("msgBtn").onclick = onBtn;
  $("msgBtn2").hidden = !btn2; $("msgBtn2").textContent = btn2 || ""; $("msgBtn2").onclick = onBtn2 || null;
  show("msg");
}
const fresh = () => location.href = location.pathname + (MOCK ? "?mock=1" : "");

for (const el of $$("[data-ministrip]")) el.append($("ministrip").content.cloneNode(true));
for (const el of $$("[data-trust]")) el.replaceWith($("trust").content.cloneNode(true));
for (const el of $$("[data-del]")) el.replaceWith($("del").content.cloneNode(true));

// ---- sound (WebAudio, behind the toggle) + flash + sparkle --------------------------------------------------------
let audio, soundOn = (() => { try { return localStorage.getItem("booth:sound") !== "0"; } catch { return true; } })();
const ctxA = () => { audio ||= new AudioContext(); if (audio.state === "suspended") audio.resume(); return audio; };
function pip(freq, dur) {
  if (!soundOn) return;
  try {
    const a = ctxA(), t = a.currentTime, o = a.createOscillator(), g = a.createGain();
    o.frequency.value = freq; g.gain.setValueAtTime(0.12, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g).connect(a.destination); o.start(t); o.stop(t + dur + 0.02);
  } catch {}
}
function noise(at, dur, type, freq, gain, q = 0.7) {
  const a = ctxA(), n = Math.ceil(a.sampleRate * dur), buf = a.createBuffer(1, n, a.sampleRate), d = buf.getChannelData(0);
  for (let i = 0; i < n; i++) d[i] = Math.random() * 2 - 1;
  const src = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain();
  f.type = type; f.frequency.value = freq; f.Q.value = q;
  g.gain.setValueAtTime(gain, at); g.gain.exponentialRampToValueAtTime(0.001, at + dur);
  src.buffer = buf; src.connect(f).connect(g).connect(a.destination); src.start(at);
}
function shutterSound() {      // "chak" (25 ms highpass) + "kk" 45 ms later (70 ms bandpass)
  if (!soundOn) return;
  try { const t = ctxA().currentTime; noise(t, 0.025, "highpass", 2500, 0.5); noise(t + 0.045, 0.07, "bandpass", 1200, 0.35, 1); } catch {}
}
function setSound(on) {
  soundOn = on;
  try { localStorage.setItem("booth:sound", on ? "1" : "0"); } catch {}
  const b = document.querySelector('[data-act="sound"]');
  b.setAttribute("aria-pressed", on); b.querySelector("span").textContent = on ? "소리 켬" : "소리 끔";
}
const restart = (el, cls) => { el.classList.remove(cls); void el.offsetWidth; el.classList.add(cls); };
function flash() {
  restart($("flash"), "on"); restart($("stage"), "thump");
  shutterSound();
}
function sparkle(host, { n = 3, dist = 24, size = 18, x = 0.5, y = 0.5 } = {}) {
  if (calm()) return;
  for (let k = 0; k < n; k++) {
    const s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    s.innerHTML = '<use href="#star"/>'; s.classList.add("spark");
    Object.assign(s.style, { left: `${x * 100}%`, top: `${y * 100}%`, width: size + "px", height: size + "px" });
    host.append(s);
    const ang = (k / n) * Math.PI * 2 - Math.PI / 2 + Math.random() * 0.6;
    s.animate([{ transform: "translate(-50%,-50%) scale(.4)", opacity: 1 },
      { transform: `translate(calc(-50% + ${Math.cos(ang) * dist}px), calc(-50% + ${Math.sin(ang) * dist}px)) scale(1) rotate(40deg)`, opacity: 0 }],
      { duration: 400 + k * 40, easing: "cubic-bezier(.2,.7,.3,1)" }).onfinish = () => s.remove();
  }
}

// ---- routing (PLAN Task 6) ----------------------------------------------------------------------------------------
async function route() {
  if (env.framed) { $("openTab").href = location.href; return show("framed"); }
  if (!S.id) { show("landA"); return prefetchHQ(); }
  let st;
  try { st = await api.status(S.id); }
  catch { return msg({ title: "앗, 연결이 안 돼", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: route }); }
  if (st.error) return expired(st.error);
  S.movable = !!st.opts?.movable;
  const role = api.loadRole(S.id)?.role;
  if (!st.a.done) {
    if (role === "a") return msg({ title: "올리다 멈췄어", text: "찍은 사진이 이 화면에 남아 있지 않아. 새로 찍어 줘.", btn: "다시 찍기", onBtn: fresh });
    return msg({ title: "아직 친구가 올리는 중이에요", text: "조금 뒤에 이 링크를 다시 열어 줘.", btn: "다시 확인", onBtn: route });
  }
  if (!st.b.done) return role === "a" ? shareScreen(true) : (landB(), prefetchHQ());
  return doneScreen();
}
function expired(why) {
  msg(why === "expired"
    ? { title: "7일이 지나 사라진 링크예요", text: "링크는 7일 뒤 사진·영상과 함께 지워져요.", btn: "새로 찍기", onBtn: fresh }
    : { title: "없는 링크예요", text: "지워졌거나 주소가 조금 틀렸을 수 있어요.", btn: "새로 찍기", onBtn: fresh });
}
addEventListener("visibilitychange", () => {
  if (!document.hidden && document.body.dataset.screen === "share" && !$("recheck").hidden) route();
});

// The HQ still model (MODNet) loads + warms on the landing page once it has painted, so it is ready by the pick
// and its first GPU run never lands inside a countdown. No camera needed.
function prefetchHQ() {
  if (MOCK || S.prefetched) return;
  S.prefetched = true;
  const go = () => load("segment").then(m => m.prefetchStill?.()).catch(e => console.warn("[booth] prefetch", e));
  "requestIdleCallback" in window ? requestIdleCallback(go, { timeout: 3000 }) : setTimeout(go, 1200);
}
async function landB() {
  show("landB");
  // A's cut-outs for the peek and for B's overlay, fetched while B reads the screen
  S.ghostP = Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-cut.png`).catch(() => null)))
    .then(g => S.ghost = g);
  S.ghostP.then(ghostLook).then(() => S.cutIdx != null && document.body.dataset.screen === "shoot" && setCut(S.cutIdx, false))
    .catch(e => console.warn("[booth] A's look", e));   // Round 5: A's 보정 on the overlay, in the background
  const g = await S.ghostP;
  if (g[0]) $("peekImg").src = g[0];
}

// ---- shoot --------------------------------------------------------------------------------------------------------
async function startShoot(side) {
  S.side = side; S.sent.clear();
  show("shoot");
  resetShoot();
  S.lay = { x: 0, y: 0, scale: 1 }; applyLay();
  const hint = side === "a" ? HINT_A : S.movable ? HINT_B : "";
  $("hintText").textContent = hint; $("hintChip").hidden = !hint || S.hintOff === side;
  $("mover").hidden = side !== "b" || !S.movable;
  $("movableBox").hidden = side !== "a"; movableSub();
  setSound(soundOn);
  $("camwait").hidden = false; $("camwait").textContent = "카메라 켜는 중…";
  try { if (soundOn) ctxA(); } catch {}
  const video = $("video");
  try {
    S.cam ||= await load("camera");
    await S.cam.startCam(video);
    // after the camera, not alongside it: with the HQ model already on the GPU (landing prefetch) MediaPipe's GPU
    // init froze getUserMedia past the 5 s stall limit (headless 10-27, 3 of 3 runs)
    const segP = MOCK ? null : load("segment").then(m => m.loadSegmenter());
    segP?.catch(() => {});
    fitStage();
    $("camwait").textContent = "배경 지우개 준비 중…";
    S.cap ||= MOCK ? mockCapture(S.cam) : await load("capture", mockCapture(S.cam));
    await segP;
  } catch (e) { return camError(e); }
  $("camwait").hidden = true;
  syncMirror();
  if (!S.bound) {
    S.cam.bindZoomFocus($("stage")); bindMover(); S.bound = true;
    new ResizeObserver(fitStage).observe($("stage"));
    video.addEventListener("resize", fitStage);
  }
  if (side === "b") {
    await S.ghostP; setCut(S.shots.length, false); loadGhostClips();
    if (S.movable && !S.anchors) load("compose").then(async C => {
      S.anchors = C.layoutAnchor && await Promise.all(S.ghost.map(u => u && loadImg(u).then(i => C.layoutAnchor(i))));
      applyLay();
    }).catch(e => console.warn("[booth] layout anchors", e));
  }
}
// Lay the camera out so the strip-cell crop (the whole photo) spans the stage width and sits in its middle.
// The crop is compose's cellCrop: full width, a quarter of the spare height taken from the top.
// fresh shoot screen: no shots, shutter ready (start, and 전체 다시 찍기)
function resetShoot() {
  S.shotUrls.forEach(u => URL.revokeObjectURL(u));
  S.cuts = []; S.shots = []; S.shotUrls = []; S.pick = []; S.pairs = null; S.hq = [];
  S.gen = (S.gen || 0) + 1; S.hqBusy = false; $("hqChip").hidden = true; $("prepOv").hidden = true;   // an older HQ run stops
  $("s-shoot").classList.remove("shooting-done");
  $("controls").hidden = false; $("shootDone").hidden = true; $("review").hidden = true; $("pips").hidden = false;
  $("shootHint").hidden = false; $("shootHint").textContent = HINT_SHOOT; $("lastShot").hidden = true;
  $("hqNote").hidden = MOCK || !!S.hqNoteOff;
  setCut(0);
}
function fitStage() {
  const v = $("video"), st = $("stage");
  if (!v.videoWidth || !S.cam) return;
  const W = st.clientWidth, H = st.clientHeight, fr = S.cam.frameRect(v);
  const cw = fr.sw / fr.sh > CELL ? fr.sh * CELL : fr.sw, ch = cw / CELL, cx = (fr.sw - cw) / 2, cy = (fr.sh - ch) / 2 * 0.25;
  const k = Math.min(W / cw, (H * 0.92) / ch);
  const left = W / 2 - (fr.sx + cx + cw / 2) * k, top = H / 2 - (fr.sy + cy + ch / 2) * k;
  const px = (el, b) => Object.assign(el.style, { left: b.x + "px", top: b.y + "px", width: b.w + "px", height: b.h + "px" });
  px(v, { x: left, y: top, w: v.videoWidth * k, h: v.videoHeight * k });
  px($("frame"), { x: left + fr.sx * k, y: top + fr.sy * k, w: fr.sw * k, h: fr.sh * k });
  S.cropIn = { x: cx * k, y: cy * k, w: cw * k, h: ch * k };                  // crop inside #frame
  px($("crop"), S.cropIn);
  S.cropBox = { x: left + (fr.sx + cx) * k, y: top + (fr.sy + cy) * k, w: cw * k, h: ch * k };   // crop inside the stage
  px($("snap"), S.cropBox);
  applyLay();
}
// ---- B moves / resizes A (only when A allowed it). Same maths as compose: scale about A's anchor (compose
// layoutAnchor = A's centre-bottom in the cell, per cut), then shift by (x * cell width, y * cell height).
const LIM = { xy: 0.8, lo: 0.4, hi: 2.5 };
const isMoved = l => Math.abs(l.x) > 0.002 || Math.abs(l.y) > 0.002 || Math.abs(l.scale - 1) > 0.005;
const layOf = () => S.side === "b" && S.movable && isMoved(S.lay)
  ? { a: Object.fromEntries(Object.entries(S.lay).map(([k, v]) => [k, Math.round(v * 1e4) / 1e4])) } : null;
function applyLay() {
  const c = S.cropIn, { x, y, scale } = S.lay, an = S.anchors?.[S.cutIdx % 4] || { x: 0.5, y: 1 };
  for (const el of [$("ghost"), $("ghostClip")]) {
    el.style.transformOrigin = c ? `${c.x + an.x * c.w}px ${c.y + an.y * c.h}px` : "";
    el.style.transform = c && isMoved(S.lay) ? `translate(${x * c.w}px, ${y * c.h}px) scale(${scale})` : "";
  }
  $("layReset").hidden = !(S.side === "b" && S.movable && isMoved(S.lay));
}
function setLay(l) {
  const cl = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  S.lay = { x: cl(l.x, -LIM.xy, LIM.xy), y: cl(l.y, -LIM.xy, LIM.xy), scale: cl(l.scale, LIM.lo, LIM.hi) };
  applyLay();
}
// Pointer events (touch + mouse), one finger drags, two fingers pinch; the wheel resizes on a computer.
// The layer swallows its touches so the stage's camera pinch-zoom doesn't fire too (README in-app fixes kept here).
function bindMover() {
  const m = $("mover"), pts = new Map();
  let g = null;
  const mid = () => { const p = [...pts.values()]; return p.length > 1
    ? { x: (p[0].x + p[1].x) / 2, y: (p[0].y + p[1].y) / 2, d: Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y) } : { ...p[0], d: 0 }; };
  const begin = () => { g = pts.size ? { lay: { ...S.lay }, at: mid() } : null; };
  m.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse" && e.button) return;
    m.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); begin();
    $("hintChip").hidden = true;
  });
  m.addEventListener("pointermove", e => {
    if (!g || !pts.has(e.pointerId) || !S.cropBox) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const now = mid(), k = g.at.d && now.d ? now.d / g.at.d : 1;
    setLay({ x: g.lay.x + (now.x - g.at.x) / S.cropBox.w, y: g.lay.y + (now.y - g.at.y) / S.cropBox.h, scale: g.lay.scale * k });
  });
  const end = e => { pts.delete(e.pointerId); begin(); };
  m.addEventListener("pointerup", end); m.addEventListener("pointercancel", end);
  m.addEventListener("wheel", e => { e.preventDefault(); setLay({ ...S.lay, scale: S.lay.scale * Math.exp(-e.deltaY / 600) }); }, { passive: false });
  for (const t of ["touchstart", "touchmove", "touchend"])
    m.addEventListener(t, e => { e.stopPropagation(); if (t !== "touchend") e.preventDefault(); if (scrollY < 1) scrollTo(0, 1); }, { passive: false });
}
function movableSub() {
  $("movableSub").textContent = $("movable").checked ? "친구가 끌어서 옮기고 크기를 바꿀 수 있어"
    : "꺼 두면 찍은 자리에 고정돼 (셀럽 프레임처럼)";
}
$("movable").addEventListener("change", movableSub);
function syncMirror() {
  $("video").classList.toggle("mirror", S.cam.camState.mirror);
  document.querySelector('[data-act="mirror"]').setAttribute("aria-pressed", S.cam.camState.mirror);
}
// A's 2 s clips loop over B's preview (cut-out via compose's WebGL alpha); the PNG stays until they are ready
async function loadGhostClips() {
  if (S.ca) return ghostLoop();
  try {
    const C = await load("compose"), ca = C.createClipAlpha({ choke: 2 });
    S.ghostClips = await Promise.all([1, 2, 3, 4].map(async c => C.loadClip(await api.blobUrl(S.id, "a", `${c}-clip`))));
    ca.canvas.id = "ghostClip"; $("ghostClip").replaceWith(ca.canvas);
    S.ca = ca; applyLay();
    if (S.cutIdx != null) setCut(S.cutIdx, false);
    ghostLoop();
  } catch (e) { console.warn("[booth] A clip overlay unavailable, using the still", e); }
}
function ghostLoop(tick) {
  if (S.looping && !tick) return;
  S.looping = true;
  if (document.body.dataset.screen !== "shoot") { S.looping = false; return S.ghostClips?.forEach(v => v.pause()); }
  const v = S.ghostClips?.[S.cutIdx % 4];
  if (v && !$("ghostClip").hidden) { if (v.paused) v.play().catch(() => {}); if (v.readyState >= 2) S.ca.draw(ghostFrame(v)); }
  requestAnimationFrame(() => ghostLoop(true));
}
// Round 5: with A's look the clip frame goes through beautyFx on a canvas first
function ghostFrame(v) {
  if (!S.ghostFx) return v;
  const c = S.gfc ||= document.createElement("canvas");
  if (c.width !== v.videoWidth || c.height !== v.videoHeight) { c.width = v.videoWidth; c.height = v.videoHeight; }
  c.getContext("2d").drawImage(v, 0, 0);
  S.ghostFx(c, v.currentTime * 1000, v);
  return c;
}
function camError(e) {
  console.warn("[booth] camera", e);
  if (e?.message === "stall" || e?.name === "NotReadableError" && env.inApp)
    return handoff("카메라가 대답을 안 해", "이 앱에서 카메라가 멈췄어. 크롬에서 열면 바로 돼.");
  if (e?.name === "NotAllowedError")
    return msg({ title: "앗, 카메라가 안 켜져", text: "카메라 권한을 허용해 줘. 주소창 옆 자물쇠 → 카메라 → 허용.",
      btn: "다시 해 보기", onBtn: () => startShoot(S.side) });
  msg({ title: "앗, 카메라가 안 켜져", text: "다른 앱이 카메라를 쓰고 있으면 끄고 다시 해 줘.", btn: "다시 해 보기", onBtn: () => startShoot(S.side) });
}
function handoff(title, text) {
  if (env.isIOS && env.inApp)
    return msg({ title, text: "오른쪽 위 공유 메뉴 → Safari로 열기 → 같은 링크에서 다시 해 줘.", btn: "다시 해 보기", onBtn: () => startShoot(S.side) });
  msg({ title, text, btn: "크롬에서 열기", onBtn: () => location.href = env.chromeUrl(location.href),
    btn2: "여기서 다시 해 보기", onBtn2: () => startShoot(S.side) });
}

// shot i of 8; B sees A's cut (i % 4): A's four picked cuts float twice around
function setCut(i, pop = true) {
  S.cutIdx = i;
  $("step").textContent = `${Math.min(i + 1, SHOTS)} / ${SHOTS}`;
  const p = $("pose"); p.textContent = POSES[i] || POSES[0];
  if (pop) restart(p, "pop");
  const k = i % 4, g = $("ghost"), src = S.side === "b" && S.ghost[k], clip = S.side === "b" && S.ca && S.ghostClips?.[k];
  S.ghostClips?.forEach((v, n) => n !== k && v.pause());
  $("ghostClip").hidden = !clip;
  g.hidden = !src || !!clip; if (src) g.src = src;
  $$("#pips i").forEach((t, n) => { t.className = n < i ? "has" : n === i ? "now" : ""; });
  applyLay();
}
function onTick(n) {
  const c = $("count");
  c.textContent = n; restart(c, "go");
  if (n <= 3) pip(n === 1 ? 1320 : 880, n === 1 ? 0.12 : 0.04);   // beeps on 3-2-1 only
  if (n === 2) $("rec").hidden = false;
  if (n === 1) setTimeout(flash, 1000);
}
async function shootIdx(i) {
  setCut(i);
  const cut = await S.cap.shootOne(i, { video: $("video"), onTick, seconds: SECS, stop: () => S.stop });
  $("count").textContent = ""; $("rec").hidden = true;
  S.shots[i] = cut;
  // "짠": the captured photo pops over the crop for 350 ms with a little star burst, then lands as the last-shot thumbnail
  const snap = $("snap"), url = S.shotUrls[i] = URL.createObjectURL(cut.raw);
  snap.src = url; snap.hidden = false; restart(snap, "pop");
  if (S.cropBox) sparkle($("stage"), { n: 3, x: (S.cropBox.x + S.cropBox.w * 0.82) / $("stage").clientWidth, y: (S.cropBox.y + S.cropBox.h * 0.2) / $("stage").clientHeight });
  await sleep(350);
  snap.hidden = true;
  const last = $("lastShot"); last.src = url; last.hidden = false; restart(last, "land");
}
// One tap shoots the rest of the 8 (after 멈추기 it goes on from the next shot), then the pick.
async function runShoot() {
  if (S.busy || !S.cap) return;
  S.busy = true; S.stop = false; document.body.classList.add("busy");
  $("shootHint").hidden = true; $("shootDone").hidden = true; $("review").hidden = true;
  try {
    for (let i = S.shots.length; i < SHOTS && !S.stop; i++) {
      await shootIdx(i);
      if (i < SHOTS - 1 && !S.stop) await sleep(250);
    }
  } catch (e) {
    if (e?.message !== "stopped") { console.warn("[booth] shoot", e); toast("앗, 찍다가 멈췄어. 셔터를 다시 눌러 줘"); }
  } finally {
    S.busy = false; document.body.classList.remove("busy");
    $("count").textContent = ""; $("rec").hidden = true;
  }
  const n = S.shots.length;
  if (n < SHOTS) {
    setCut(n, false);
    $("shootHint").textContent = n ? `${n}장 찍었어. 셔터를 누르면 ${n + 1}번째부터 이어서 찍어` : HINT_SHOOT;
    $("shootHint").hidden = false;
    return;
  }
  prepAndPick();
}

// ---- HQ cut-out of all 8 shots (operator 10-27): starts right after shot 8, before the pick ------------------------
// warmStill (the one GPU freeze) then cutStill shot by shot, 1..8; after the pick only the 4 picked still pending.
// Each finished cut replaces the live one in place (grid cell / review cell crossfade). Stops once the upload starts:
// files cannot change after `done` (Worker 409).
async function upgradeAll() {
  const gen = S.gen, shots = S.shots.slice();
  if (!S.cap.hqCut) return;
  S.hqBusy = true; S.hq = [];
  const t0 = performance.now();
  try {
    if (!await S.cap.hqReady()) { S.hq.push("live:" + S.cap.hqState()); return; }
    S.hq.push(`ready ${Math.round(performance.now() - t0)}ms`);
    for (;;) {
      if (gen !== S.gen || S.hqFrozen) break;
      // WASM (no WebGPU, Round 5): seconds per still, so only the 4 picked ones are cut, once they are picked
      if (S.cap.hqPath?.() === "wasm" && !S.cuts.length) { await sleep(300); continue; }
      const c = (S.cuts.length ? S.cuts : shots).find(x => !x.hq && !x.hqFail);
      if (!c) break;
      const t1 = performance.now(), j = shots.indexOf(c) + 1;
      try {
        const png = await S.cap.hqCut(c);
        if (gen !== S.gen || S.hqFrozen) break;
        c.cut = png; c.hq = true; S.hq.push(`${j}:${Math.round(performance.now() - t1)}ms`);
      } catch (e) { c.hqFail = true; S.hq.push(`${j}:live ${e?.message || e}`); console.warn("[booth] hq cut", j, e); }
      if (gen === S.gen) onHQ(c, shots);
    }
  } finally {
    if (gen === S.gen) { S.hqBusy = false; S.hq.push(`total ${Math.round(performance.now() - t0)}ms`); onHQ(null, shots); }
  }
}
// progress everywhere it shows: the "8장 준비 중" overlay, the grid cell (+ its 다듬는 중 badge), the review chip
function onHQ(c, shots) {
  $("prepText").textContent = `8장 준비 중… ${shots.filter(x => x.hq).length}/8`;
  const j = S.shots.indexOf(c);
  if (j >= 0 && document.body.dataset.screen === "pick") paintCell(j, true);
  if (!c) $$("#pickGrid .pk").forEach(b => b.classList.remove("pending"));
  const k = S.cuts.indexOf(c);
  if (k >= 0) swapCell(k).catch(() => {});
  hqChip();
}
function hqChip() {
  const left = S.cuts.filter(x => !x.hq && !x.hqFail).length;
  $("hqChip").hidden = !(S.hqBusy && left && S.cuts.length);
  $("hqChip").textContent = `누끼 다듬는 중 ${4 - left}/4`;
}
// After shot 8: a short "8장 준비 중" (at most 5 s, less when all 8 are done), then the pick.
async function prepAndPick() {
  S.hqFrozen = false;
  if (S.cap.hqCut) {
    $("prepText").textContent = "8장 준비 중… 0/8"; $("prepOv").hidden = false;
    S.hqP = upgradeAll();
    await Promise.race([S.hqP, sleep(5000)]);
    $("prepOv").hidden = true;
  }
  pickScreen();
}

// ---- pick 4 of 8 (no time limit; tap order = strip order; one 전체 다시 찍기) -----------------------------------------
// One cell composed like the result: the cut-out on the default booth background; on B's phone B + the paired A cut
// in front (B's move of A applied). The strip crop is compose's cellCrop.
const BG = "booth-pink";
async function cellCanvas(cut, ai, look = null, w = 552, h = 382) {   // look: Round 5 보정 (review only; the grid stays plain)
  const c = Object.assign(document.createElement("canvas"), { width: w, height: h }), x = c.getContext("2d");
  const C = await load("compose", mockCompose);
  const [raw0, cut0] = await cutImgs(cut), { raw, cut: cutImg } = await withLook(raw0, cut0, look);
  if (S.side === "b" && ai != null && S.ghost[ai] && C.drawCell) {
    const aImg = await loadImg(S.ghost[ai]);
    C.drawCell(x, { x: 0, y: 0, w, h }, 0, { a: { cuts: [{ raw: aImg, cut: aImg, clip: null }] },
      b: { cuts: [{ raw, cut: cutImg, clip: null }] }, bg: BG, layout: layOf() });
  } else {
    const B = C.BACKGROUNDS?.find(b => b.id === BG);
    if (B?.draw) B.draw(x, w, h); else { x.fillStyle = "#ffc2d6"; x.fillRect(0, 0, w, h); }
    const sw = cutImg.naturalWidth || cutImg.width, sh = sw / CELL;
    x.drawImage(cutImg, 0, ((cutImg.naturalHeight || cutImg.height) - sh) / 2 * 0.25, sw, sh, 0, 0, w, h);
  }
  return c;
}
function pickScreen() {
  S.pick = []; S.cellCv = [];
  show("pick");
  $("reshoot").hidden = S.redo;
  $("pickGrid").replaceChildren(...S.shots.map((_, j) => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pk" });
    b.setAttribute("aria-label", `${j + 1}번째 사진`);
    b.innerHTML = `<img alt="" src="${S.shotUrls[j]}"><b class="badge" aria-hidden="true"></b>`;   // raw until composed
    b.onclick = () => togglePick(j);
    return b;
  }));
  paintPick();
  (async () => { for (let j = 0; j < S.shots.length; j++) await paintCell(j, false); })().catch(e => console.warn("[booth] grid", e));
}
async function paintCell(j, fade) {
  const shot = S.shots[j], b = $$("#pickGrid .pk")[j];
  if (!shot || !b) return;
  const cv = await cellCanvas(shot, S.side === "b" ? j % 4 : null);
  if (S.shots[j] !== shot) return;
  const old = [...b.querySelectorAll("img, canvas")];
  cv.className = fade && !calm() ? "fadein" : "";
  b.insertBefore(cv, b.querySelector(".badge"));
  setTimeout(() => old.forEach(o => o.remove()), fade ? 500 : 0);
  b.classList.toggle("pending", S.hqBusy && !shot.hq && !shot.hqFail && S.cap.hqPath?.() !== "wasm");
  S.cellCv[j] = cv; paintStrip();
}
function togglePick(j) {
  const k = S.pick.indexOf(j);
  if (k >= 0) S.pick.splice(k, 1);
  else if (S.pick.length < 4) S.pick.push(j);
  else return toast("4장까지만 골라. 뺄 사진을 먼저 눌러 줘");
  paintPick();
}
function paintPick() {
  $$("#pickGrid .pk").forEach((b, j) => {
    const k = S.pick.indexOf(j);
    b.setAttribute("aria-pressed", k >= 0); b.querySelector(".badge").textContent = k >= 0 ? k + 1 : "";
  });
  $("pickCount").textContent = `${S.pick.length} / 4`;
  $("pickDone").disabled = S.pick.length !== 4;
  paintStrip();
}
function paintStrip() {
  const box = $("pickStrip");
  box.replaceChildren(...[0, 1, 2, 3].map(k => {
    const j = S.pick[k], d = document.createElement("div");
    if (j == null) { d.className = "mc empty"; d.innerHTML = `<span>${k + 1}</span>`; return d; }
    d.className = "mc";
    const src = S.cellCv[j];
    if (src) { const c = Object.assign(document.createElement("canvas"), { width: 184, height: 127 }); c.getContext("2d").drawImage(src, 0, 0, 184, 127); d.append(c); }
    else d.innerHTML = `<img alt="" src="${S.shotUrls[j]}">`;
    return d;
  }), Object.assign(document.createElement("div"), { className: "mfoot", textContent: "따로 같이 네컷" }));
}
function pickDone() {
  if (S.pick.length !== 4) return;
  const keep = S.pick.slice();
  S.cuts = keep.map(j => S.shots[j]);
  S.pairs = S.side === "b" ? keep.map(j => j % 4) : null;
  // the 4 unpicked shots go now (8 stills + 8 clips are a lot for a phone); the picked URLs stay for the review
  S.shotUrls.forEach((u, j) => keep.includes(j) || URL.revokeObjectURL(u));
  S.shots.forEach((c, j) => { if (!keep.includes(j)) { c.raw = c.cut = c.clip = null; c.hqFail = true; } });
  S.shots = []; S.shotUrls = keep.map(j => S.shotUrls[j]); S.cellCv = [];
  show("shoot");
  $("s-shoot").classList.add("shooting-done");
  $("controls").hidden = true; $("shootDone").hidden = false; $("pips").hidden = true; $("lastShot").hidden = true;
  $("shootHint").hidden = true; $("hqNote").hidden = true; $("hqAsk").hidden = true;
  $("step").textContent = "4 / 4"; $("pose").textContent = "다 골랐어! 맘에 들어?"; restart($("pose"), "pop");
  $("ghost").hidden = true; $("ghostClip").hidden = true; S.cutIdx = null;
  hqChip();
  reviewLook();      // Round 5 보정
  review().catch(e => console.warn("[booth] review", e));
}
function reshoot() {
  if (S.redo) return;
  S.redo = true;
  show("shoot");
  resetShoot();
  if (S.side === "b") ghostLoop();
}

// "맘에 들어?": the four cells composed like the strip (B: B's pick + its paired A in front).
const reviewCell = i => cellCanvas(S.cuts[i], S.side === "b" ? S.pairs?.[i] ?? i : null, S.beautyReady ? S.look : null);
function paintRev(c, i, alpha = 1) {
  const R = $("review"), x = R.getContext("2d"), { ox, oy, cw, ch, g, d } = S.revGeo;
  const px = ox + (i % 2) * (cw + g), py = oy + (i >> 1) * (ch + g);
  x.save(); x.beginPath(); x.roundRect(px, py, cw, ch, 10 * d); x.clip();
  x.globalAlpha = alpha; x.drawImage(c, px, py, cw, ch); x.restore();
}
async function review() {
  const seq = S.revSeq = (S.revSeq || 0) + 1;      // a newer 보정 pick wins
  const cells = await Promise.all([0, 1, 2, 3].map(reviewCell));
  if (!$("s-shoot").classList.contains("shooting-done") || seq !== S.revSeq) return;
  const R = $("review"), st = $("stage"), d = devicePixelRatio || 1;
  R.width = st.clientWidth * d; R.height = st.clientHeight * d;
  const x = R.getContext("2d"), g = 8 * d, cw = Math.min((R.width - 3 * g) / 2, ((R.height - 3 * g) / 2) * CELL), ch = cw / CELL;
  S.revGeo = { ox: (R.width - 2 * cw - g) / 2, oy: (R.height - 2 * ch - g) / 2, cw, ch, g, d };
  x.fillStyle = "#2e2230"; x.fillRect(0, 0, R.width, R.height);
  cells.forEach((c, i) => paintRev(c, i));
  R.hidden = false;
}
// an HQ cut arrived for picked cell i: crossfade that cell
async function swapCell(i) {
  if ($("review").hidden || !S.revGeo) return;
  const c = await reviewCell(i);
  if (calm()) return paintRev(c, i);
  const t0 = performance.now();
  const step = () => {
    const k = (performance.now() - t0) / 450;
    paintRev(c, i, k >= 1 ? 1 : 0.18);       // repeated low-alpha draws converge on the new cell
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}
// 올리기 while a picked cut is still being refined: ask (wait up to 20 s, or upload the live cuts now)
function askUpload() {
  if (!S.hqBusy || !S.cuts.some(c => !c.hq && !c.hqFail)) return upload();
  $("hqAsk").hidden = false;
}
async function hqWait() {
  const b = $("hqWaitBtn"); b.disabled = true; b.textContent = "기다리는 중…";
  await Promise.race([S.hqP, sleep(S.cap.hqPath?.() === "wasm" ? 45000 : 20000)]);   // WASM: ~9 s per still on a phone CPU
  b.disabled = false; b.textContent = "조금만 기다리기";
  upload();
}

// ---- face retouch (Round 5, beauty.js) ----------------------------------------------------------------------------
// S.look = this phone's picks (review screen, uploaded as look.json); the done screen keeps D.look = { a, b }, each
// side defaulting to its look.json. Beautified stills are cached per (cut-out image, look), landmarks per image.
const LOOK0 = { skin: 1, slim: 0, eye: 0 };
const LOOK_ROWS = [["skin", "피부"], ["slim", "갸름"], ["eye", "눈"]], LOOK_LV = ["안 함", "살짝", "많이"];
S.look = { ...LOOK0 };
const beautyMod = () => S.beautyP ||= load("beauty");
const blobImg = b => { const u = URL.createObjectURL(b); return loadImg(u).finally(() => URL.revokeObjectURL(u)); };
const imgMemo = new WeakMap();          // Cut -> its decoded [raw, cut] (renewed when the HQ cut replaces cut.cut)
function cutImgs(c) {
  let m = imgMemo.get(c);
  if (!m || m.cut !== c.cut) imgMemo.set(c, m = { cut: c.cut, p: Promise.all([blobImg(c.raw), blobImg(c.cut)]) });
  return m.p;
}
const looked = new WeakMap();           // cut-out image -> Map(look key -> Promise<{ raw, cut }>)
async function withLook(raw, cut, look) {
  const B = await beautyMod().catch(() => null);
  if (!B?.lookOn(look)) return { raw, cut };
  let m = looked.get(cut);
  if (!m) looked.set(cut, m = new Map());
  const k = B.lookKey(look);
  if (!m.has(k)) m.set(k, B.beautyStill(raw, cut, look));
  return m.get(k);
}
async function fetchLook(side) {
  try {
    const l = await (await fetch(await api.blobUrl(S.id, side, "look.json"))).json();
    return LOOK_ROWS.every(([k]) => [0, 1, 2].includes(l?.[k])) ? l : { ...LOOK0 };
  } catch { return { ...LOOK0 }; }
}
// three rows of 안 함 / 살짝 / 많이 into `box`; onPick(newLook)
function lookRows(box, look, onPick, title = "") {
  box.replaceChildren(...(title ? [Object.assign(document.createElement("h3"), { textContent: title })] : []),
    ...LOOK_ROWS.map(([k, label]) => {
      const row = Object.assign(document.createElement("div"), { className: "lrow" });
      row.append(Object.assign(document.createElement("span"), { textContent: label }), ...LOOK_LV.map((t, v) => {
        const b = Object.assign(document.createElement("button"), { type: "button", className: "pill", textContent: t });
        b.setAttribute("aria-pressed", look[k] === v); b.setAttribute("aria-label", `${label} ${t}`);
        b.onclick = () => { if (look[k] !== v) onPick({ ...look, [k]: v }); };
        return b;
      }));
      return row;
    }));
}
// review: my picks re-render with the new look (cached, so flipping back is instant)
function reviewLook() {
  // the review shows plain cells until the face model is up (first visit: download + GPU compile), then re-renders
  if (!S.beautyReady && !S.beautyWait) S.beautyWait = beautyMod().then(B => B.prefetchBeauty()).then(ok => {
    S.beautyReady = ok;
    if (ok) review().catch(e => console.warn("[booth] review", e));
  }).catch(e => console.warn("[booth] beauty", e));
  lookRows($("lookMine"), S.look, l => { S.look = l; reviewLook(); review().catch(e => console.warn("[booth] review", e)); }, "보정");
}
// B's overlay of A: A's cut-outs (and clips, see ghostLoop) with A's look
async function ghostLook() {
  S.aLook = await fetchLook("a");
  const B = await beautyMod().catch(() => null);
  if (!B?.lookOn(S.aLook)) return;
  S.ghostFx = await B.beautyFx(S.aLook);
  S.ghost = await Promise.all(S.ghost.map(async u => {
    if (!u) return u;
    try { const i = await loadImg(u), r = await withLook(i, i, S.aLook);
      return URL.createObjectURL(await new Promise(res => r.cut.toBlob(res, "image/png"))); } catch { return u; }
  }));
}
// done: D.look per side -> D.view (beautified stills, the clips stay the originals) + D.fx for the moving strip
async function applyLooks() {
  const B = await beautyMod().catch(() => null), look = D.look;
  const view = Object.fromEntries(await Promise.all(["a", "b"].map(async s => [s, { cuts: await Promise.all(D.media[s].cuts.map(async p => {
    const r = await withLook(p.raw, p.cut, look[s]);
    return { raw: r.raw, cut: r.cut, get clip() { return p.clip; } };
  })) }])));
  if (D.look !== look) return;          // a newer pick is already on its way
  D.view = view;
  // the clip fx (VIDEO landmarker + its GPU warm-up) only gates the moving strip, not the photo
  D.fxP = (async () => { const fx = B ? { a: await B.beautyFx(look.a), b: await B.beautyFx(look.b) } : null; if (D.look === look) D.fx = fx; })()
    .catch(e => console.warn("[booth] beauty fx", e));
}
function buildLook() {
  const role = api.loadRole(S.id)?.role;
  const name = s => role ? (s === role ? "나" : "친구") : s === "a" ? "보낸 사람" : "받은 사람";
  const order = role === "b" ? ["b", "a"] : ["a", "b"];
  order.forEach((s, n) => lookRows($(n ? "lookB" : "lookA"), D.look[s], async l => {
    D.look = { ...D.look, [s]: l }; buildLook();
    await applyLooks(); changed(["frame", "filter"]);
  }, name(s)));
}

// ---- upload -------------------------------------------------------------------------------------------------------
function fileOf(name) {
  const c = S.cuts[+name[0] - 1];
  return name.endsWith(".jpg") ? [c.raw, "image/jpeg"] : name.endsWith(".png") ? [c.cut, "image/png"] : [c.clip, c.clipType];
}
async function upload() {
  S.hqFrozen = true; $("hqAsk").hidden = true;    // the picks are final from here (no HQ swap mid-upload)
  show("upload");
  $("upRetry").hidden = true;
  const size = n => fileOf(n)[0].size, total = NAMES.reduce((s, n) => s + size(n), 0);
  const done = () => [...S.sent].reduce((s, n) => s + size(n), 0);
  // shown % creeps toward the end of the file in flight (fetch has no upload progress), never past it
  let shown = done() / total, cap = shown;
  const paint = () => { $("upBar").style.width = `${shown * 100}%`; $("upTitle").textContent = `올리는 중… ${Math.floor(shown * 100)}%`; };
  paint();
  const creep = setInterval(() => { const base = done() / total; shown = Math.max(base, shown + (cap * 0.97 - shown) * 0.12); paint(); }, 200);
  try {
    if (S.side === "a" && !S.token) {
      const tk = api.newToken();
      S.movable = $("movable").checked;
      const { id } = await api.create(tk, { movable: S.movable });
      S.id = id; S.token = tk;
      api.saveRole(id, "a", tk);
      history.replaceState(null, "", `?s=${id}${MOCK ? "&mock=1" : ""}`);
    }
    if (S.side === "b" && !S.token) { S.token = api.newToken(); api.saveRole(S.id, "b", S.token); }
    for (let round = 0; round < 2; round++) {
      for (const n of NAMES) {
        if (S.sent.has(n)) continue;
        const [blob, type] = fileOf(n);
        cap = (done() + blob.size) / total;
        await api.put(S.id, S.side, n, blob, type, S.token);
        S.sent.add(n);
      }
      const lay = layOf();      // B's placement of A, before `done` so A's device finds it
      if (lay) await api.put(S.id, "b", "layout.json", new Blob([JSON.stringify(lay)], { type: "application/json" }), "application/json", S.token);
      if (S.side === "b" && S.pairs) await api.put(S.id, "b", "pairs.json", new Blob([JSON.stringify(S.pairs)], { type: "application/json" }), "application/json", S.token);
      await api.put(S.id, S.side, "look.json", new Blob([JSON.stringify(S.look)], { type: "application/json" }), "application/json", S.token);   // Round 5 보정
      const r = await api.done(S.id, S.side, S.token);
      if (r.ok) break;
      if (r.missing) { r.missing.forEach(n => S.sent.delete(n)); continue; }
      if (r.error === "taken") { clearInterval(creep); return doneScreen("다른 친구가 먼저 찍었어요. 둘이 찍은 네컷이야"); }
      if (r.error === "done") break;
      throw new Error(r.error);
    }
  } catch (e) {
    clearInterval(creep);
    console.warn("[booth] upload", e);
    $("upTitle").textContent = "앗, 올리다 멈췄어";
    $("upErr").textContent = e.status === 413 ? "파일이 너무 커. 다시 찍어 줘." : "인터넷이 끊겼나 봐. 다시 눌러 줘.";
    $("upRetry").hidden = false;
    return;
  }
  clearInterval(creep);
  shown = 1; paint(); $("upTitle").textContent = "다 올렸어! 100%";
  restart($("upBarBox"), "full");
  await sleep(calm() ? 150 : 600);
  $("video").srcObject?.getTracks().forEach(t => t.stop());
  if (S.side === "a") return shareScreen(false);
  doneScreen();
}

// ---- share (A) ----------------------------------------------------------------------------------------------------
// A's own four crops as a mini strip
async function myStrip() {
  const urls = S.cuts.length === 4 ? S.cuts.map(c => URL.createObjectURL(c.raw))
    : await Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-raw.jpg`).catch(() => null)));
  const strip = Object.assign(document.createElement("div"), { className: "ministrip real" });
  strip.setAttribute("aria-hidden", "true");
  strip.innerHTML = urls.map(u => `<div class="mc"><img alt="" src="${u || ""}"></div>`).join("")
    + `<div class="mfoot">따로 같이 네컷</div>`;
  const star = '<svg class="deco s1"><use href="#star"/></svg><svg class="deco s2"><use href="#heart"/></svg>';
  $("myStrip").innerHTML = star; $("myStrip").prepend(strip);
}
function shareScreen(waiting) {
  show("share");
  $("shareTitle").innerHTML = waiting ? "친구가 아직<br><em>안 찍었어요</em>" : "다 올렸어!<br><em>친구한테 보내자</em>";
  $("shareLead").innerHTML = waiting ? "이 링크를 친구한테 보내 줘.<br>친구가 찍으면 이 링크에서 바로 열려."
    : `친구가 이 링크를 열면 네 옆에 서서 찍어.<br>${S.movable ? "친구가 너를 옮기고 크기를 바꿀 수 있어." : "너는 찍은 자리에 고정돼."}<br>둘 다 찍으면 사진이랑 영상이 열려.`;
  $("recheck").hidden = !waiting;
  $("linkText").textContent = shareUrl(); $("linkBox").hidden = true;
  $("s-share").querySelector(".del").hidden = !token();
  myStrip().catch(() => {});
}
const SHARE_TEXT = "내 옆자리 비워 뒀어 · 2분이면 돼 · 가입 없음";
async function share() {
  const url = shareUrl();
  if (navigator.share) {
    try { await navigator.share({ title: "따로 같이 네컷", text: SHARE_TEXT, url }); return toast("보냈어! 친구가 찍으면 이 링크에서 열려"); }
    catch (e) { if (e.name === "AbortError") return; }
  }
  copy();
}
async function copy() {
  const b = $("copyBtn");
  $("linkBox").hidden = false;
  try { await navigator.clipboard.writeText(`${SHARE_TEXT}\n${shareUrl()}`); }
  catch { return toast("아래 링크를 길게 눌러 복사해 줘"); }
  b.textContent = "복사했어! ♥"; b.classList.add("copied");
  setTimeout(() => { b.textContent = "링크 복사"; b.classList.remove("copied"); }, 1500);
}

// ---- delete -------------------------------------------------------------------------------------------------------
async function del(box, yes) {
  const ask = box.querySelector(".delask");
  if (yes == null) { ask.hidden = false; box.querySelector('[data-act="del"]').hidden = true; return ask.querySelector(".danger").focus(); }
  if (!yes) { ask.hidden = true; box.querySelector('[data-act="del"]').hidden = false; return; }
  const btn = ask.querySelector(".danger"); btn.disabled = true; btn.textContent = "지우는 중…";
  let r;
  try { r = await api.deleteLink(S.id, token()); } catch { r = { error: "network" }; }
  btn.disabled = false; btn.textContent = "지우기";
  if (!r.ok) return toast(r.error === "network" ? "인터넷이 끊겼나 봐. 다시 눌러 줘" : "이 폰에서는 지울 수 없는 링크야");
  D.video = D.story = null; D.vkeyWanted = null;
  msg({ title: "삭제됐어", text: "사진·영상을 서버에서 지웠어. 이 링크는 이제 안 열려. 저장해 둔 파일은 폰에 그대로 있어.",
    btn: "새로 찍기", onBtn: fresh });
}

// ---- done ---------------------------------------------------------------------------------------------------------
const D = { bg: null, frame: null, filter: "original", caption: "", media: null, photo: null, video: null, story: null,
  wantStory: false, seq: 0, mseq: {}, clips: false, vjob: false, revealed: false, tab: "frame",
  layout: null, pairs: null, outline: null, mattes: null, ol: { color: "#ffffff", style: "solid", width: "normal" },   // ol = last outline picks
  stickers: [], sel: -1, pkey: null };   // Round 5 deco: stickers (strip px, ROUND5.md), selected one, key of D.photo
// fallbacks until compose.js exports its own lists (CONTRACT Round 3 `outline`)
const OL_COLORS = ["#ffffff", "#ff7eb6", "#ffd84d", "#8fd3ff", "#b79cff", "#2e2230"];
const OL_STYLES = [{ id: "solid", label: "실선" }, { id: "dotted", label: "점선" }, { id: "glitter", label: "반짝이" }];
const OL_WIDTHS = [{ id: "thin", label: "얇게" }, { id: "normal", label: "보통" }, { id: "thick", label: "두껍게" }];
async function loadLayout() {
  if (!S.movable) return null;
  if (S.side === "b" && S.cuts.length === 4) return layOf();
  try { return await (await fetch(await api.blobUrl(S.id, "b", "layout.json"))).json(); } catch { return null; }
}
// B's cell i = B's pick i + A's cut pairs[i] (pairs.json; none = identity, e.g. Round 3 links)
async function loadPairs() {
  if (S.side === "b" && S.cuts.length === 4) return S.pairs;
  try {
    const p = await (await fetch(await api.blobUrl(S.id, "b", "pairs.json"))).json();
    return Array.isArray(p) && p.length === 4 && p.every(v => Number.isInteger(v) && v >= 0 && v < 4) ? p : null;
  } catch { return null; }
}
const loadImg = src => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });

async function sideMedia(side) {
  const local = S.side === side && S.cuts.length === 4;
  return { cuts: await Promise.all([1, 2, 3, 4].map(async c => {
    const u = n => local ? URL.createObjectURL(fileOf(`${c}-${n}`)[0]) : api.blobUrl(S.id, side, `${c}-${n}`);
    const [raw, cut] = await Promise.all([loadImg(await u("raw.jpg")), loadImg(await u("cut.png"))]);
    return { raw, cut, clip: null };
  })) };
}
async function loadClips() {
  for (const side of ["a", "b"]) await Promise.all(D.media[side].cuts.map(async (p, k) => {
    const local = S.side === side && S.cuts.length === 4;
    const v = Object.assign(document.createElement("video"), { muted: true, playsInline: true, loop: true, preload: "auto" });
    v.src = local ? URL.createObjectURL(S.cuts[k].clip) : await api.blobUrl(S.id, side, `${k + 1}-clip`);
    await new Promise((res, rej) => { v.onloadeddata = res; v.onerror = () => rej(new Error("clip")); });
    p.clip = v;
  }));
  D.clips = true;
}

let C, FR, FI;
async function doneScreen(note = "") {
  show("done");
  $("doneNote").textContent = note; $("doneNote").hidden = !note;
  $("making").hidden = false; $("makingText").textContent = "네컷 만드는 중";
  const inApp = env.inApp;
  $("saveBtns").hidden = inApp; $("inappSave").hidden = !inApp;
  if (inApp) { $("iosText").hidden = !env.isIOS; document.querySelector('[data-act="toChrome"]').hidden = env.isIOS; }
  $("s-done").querySelector(".del").hidden = !token();
  try {
    [C, FR, FI] = await Promise.all([load("compose", mockCompose), load("frames", mockFrames), load("filters", mockFilters)]);
    [D.media, D.layout, D.pairs] = await Promise.all([
      Promise.all(["a", "b"].map(async s => [s, await sideMedia(s)])).then(Object.fromEntries), loadLayout(), loadPairs()]);
    // Round 5 보정: each side's look.json (my own picks when I just shot)
    D.look ||= Object.fromEntries(await Promise.all(["a", "b"].map(async s =>
      [s, S.side === s && S.cuts.length === 4 ? { ...S.look } : await fetchLook(s)])));
    await applyLooks();
  } catch (e) {
    console.warn("[booth] done", e);
    return msg({ title: "앗, 사진을 못 불러왔어", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: () => doneScreen(note) });
  }
  buildTiles();
  await render(["frame", "filter"]);
  if (!D.revealed) reveal();
  if (!inApp) { clearTimeout(vT); vT = setTimeout(videoJob, VIDEO_IDLE); }
}
const rectsOf = f => C.cellRects?.(f) || [0, 1, 2, 3].map(i => ({ x: 24, y: 24 + i * 398, w: 552, h: 382 }));
function reveal() {          // first time only: the print slides out, then the four cells appear 90 ms apart
  D.revealed = true;
  if (calm()) return;
  const p = $("preview");
  $("veils").innerHTML = rectsOf(D.frame).map((r, i) =>
    `<i style="left:${r.x / 6}%;top:${r.y / 18}%;width:${r.w / 6}%;height:${r.h / 18}%;--i:${i}"></i>`).join("");
  restart(p, "print");
  setTimeout(() => sparkle($("s-done").querySelector(".previewwrap"), { n: 6, dist: 70, size: 22, y: 0.45 }), 1100);
}
function tile(kind, label, pressed, onPick) {
  const b = Object.assign(document.createElement("button"), { className: "tile " + kind, type: "button" });
  b.setAttribute("aria-pressed", pressed);
  b.innerHTML = `<span class="mini"></span>`; b.append(label);
  b.onclick = () => {
    if (b.getAttribute("aria-pressed") === "true") return;
    b.parentElement.querySelectorAll(".tile").forEach(x => x.setAttribute("aria-pressed", x === b));
    onPick();
  };
  return b;
}
function bgLabel(bg) {
  const role = api.loadRole(S.id)?.role;
  if (bg.id === "a") return role === "a" ? "내 배경" : role === "b" ? "친구 배경" : "보낸 사람 배경";
  if (bg.id === "b") return role === "b" ? "내 배경" : role === "a" ? "친구 배경" : "받은 사람 배경";
  return bg.label;
}
function bgSwatch(bg) {
  if (bg.id === "a" || bg.id === "b") return `center / cover url(${D.media[bg.id].cuts[0].raw.src})`;
  if (!bg.draw) return "#fff";
  const c = Object.assign(document.createElement("canvas"), { width: 64, height: 64 });
  bg.draw(c.getContext("2d"), 64, 64);
  return `center / cover url(${c.toDataURL()})`;
}
// A moved by B no longer lines up with A's own room: compose says which backgrounds still work
function bgOk(id) {
  if (C.bgAllowed) { const r = C.bgAllowed(D.layout); return Array.isArray(r) ? r.includes(id) : typeof r === "boolean" ? id !== "a" || r : r?.[id] ?? true; }
  return !(id === "a" && D.layout && isMoved(D.layout.a));
}
function buildTiles() {
  const bgs = C.BACKGROUNDS.filter(b => bgOk(b.id)), frames = FR.FRAMES, filters = FI.FILTERS;
  if (D.bg && !bgs.includes(D.bg)) D.bg = null;
  D.bg ??= bgs.find(b => b.id === "booth-pink") || bgs[0];
  D.frame ??= frames[0];
  $("bgChips").replaceChildren(...bgs.map(bg => {
    const t = tile("bg", bgLabel(bg), bg === D.bg, () => { D.bg = bg; changed(["frame", "filter"]); });
    t.querySelector(".mini").style.background = bgSwatch(bg);
    return t;
  }));
  $("frameChips").replaceChildren(...frames.map(f => {
    const t = tile("frame", f.label, f === D.frame, () => { D.frame = f; changed(["filter"]); });
    t.dataset.id = f.id; return t;
  }));
  $("filterChips").replaceChildren(...filters.map(f => {
    const t = tile("filter", f.label, f.id === D.filter, () => { D.filter = f.id; changed(["frame"]); });
    t.dataset.id = f.id; return t;
  }));
  $("caption").value = D.caption; capCount();
  buildOutline();
  buildLook();
}
// 테두리: 없음 + colours, then style and width (dimmed while 없음)
function buildOutline() {
  const norm = x => typeof x === "string" ? { id: x, label: "", color: x } : { id: x.id, label: x.label || "", color: x.color || x.id };
  const colors = (C.OUTLINE_COLORS || OL_COLORS).map(norm), styles = (C.OUTLINE_STYLES || OL_STYLES).map(norm);
  const widths = (C.OUTLINE_WIDTHS || OL_WIDTHS).map(norm);
  const pick = () => { changed(["frame", "filter"]); buildOutline(); };
  const pill = (on, onPick, cls = "") => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill " + cls });
    b.setAttribute("aria-pressed", on);
    b.onclick = () => { if (!on) onPick(); };
    return b;
  };
  const none = pill(!D.outline, () => { D.outline = null; pick(); }, "none");
  none.textContent = "없음";
  $("olColors").replaceChildren(none, ...colors.map(c => {
    const b = pill(D.outline?.color === c.color, () => { D.ol.color = c.color; D.outline = { ...D.ol }; pick(); }, "sw");
    b.style.setProperty("--c", c.color); b.setAttribute("aria-label", c.label || c.color); return b;
  }));
  for (const [box, list, key] of [["olStyles", styles, "style"], ["olWidths", widths, "width"]])
    $(box).replaceChildren(...list.map(s => {
      const b = pill(D.ol[key] === s.id, () => { D.ol[key] = s.id; if (D.outline) D.outline = { ...D.ol }; pick(); }, key);
      b.textContent = s.label; b.dataset.id = s.id; b.disabled = !D.outline; return b;
    }));
}
// a choice changed: redraw the photo now, the thumbnails that depend on it, and the moving strip once the choosing
// stops (Round 5: the recorder competed with every tap; it now starts after VIDEO_IDLE ms without a change)
let vT;
const VIDEO_IDLE = 2000;
function changed(minis) {
  render(minis);
  later();
}
function later() {          // the saved photo soon, the moving strip after the choosing stops
  photoJob();
  clearTimeout(vT); D.vac?.abort();
  if (!env.inApp) { setVid(); vT = setTimeout(videoJob, VIDEO_IDLE); }
}
const opts = (o = {}) => ({ a: D.view?.a || D.media.a, b: D.view?.b || D.media.b, bg: D.bg.id, frame: D.frame, filter: D.filter, date: DATE,
  caption: D.caption.trim(), layout: D.layout, pairs: D.pairs, outline: D.outline, fx: D.fx || { a: null, b: null }, stickers: D.stickers,
  mattes: D.mattes, ...o });
const vkey = () => [D.bg.id, D.frame.id, D.filter, D.caption.trim(), JSON.stringify(D.outline), JSON.stringify(D.look), JSON.stringify(D.stickers),
  D.mattes ? "hq" : ""].join("|");
// the strip as an image: compose paints a canvas (cells cached), no JPEG round trip; stickers are not in it (they are
// the DOM layer on top while choosing) but are in D.photo and the video
const stripOf = async o => C.stripCanvas ? C.stripCanvas(o) : createImageBitmap(await C.drawStrip(o));
async function render(minis = []) {
  const seq = ++D.seq;
  $("making").hidden = false; $("makingText").textContent = "네컷 만드는 중";
  let cv;
  try { cv = await stripOf(opts({ stickers: null })); }
  catch (e) { console.warn("[booth] drawStrip", e); if (seq === D.seq) $("makingText").textContent = "앗, 네컷을 못 만들었어"; return; }
  if (seq !== D.seq) return;
  $("stripImg").getContext("2d").drawImage(cv, 0, 0, 600, 1800);
  showStill();
  $("making").hidden = true;
  photoJob();
  if (minis.length) setTimeout(() => minis.forEach(renderMinis), 60);   // after the photo has painted
}
function showStill() {
  if (D.video?.key === vkey() && D.tab !== "sticker") return showVideo();
  $("stripImg").hidden = false; $("stripVid").hidden = true; $("stripVid").pause(); $("stk").hidden = false;
}
function showVideo() {
  const v = $("stripVid");
  if (v.dataset.key !== D.video.key) { if (v.src) URL.revokeObjectURL(v.src); v.src = URL.createObjectURL(D.video.blob); v.dataset.key = D.video.key; }
  v.hidden = false; $("stripImg").hidden = true; $("stk").hidden = true; v.play().catch(() => {});   // stickers are in the video
}
// the JPEG to save, with stickers, made shortly after the last change (toBlob encodes off the main thread)
let pT;
function photoJob() {
  clearTimeout(pT);          // the button stays on: a tap before the JPEG is ready makes it on the spot (savePhoto)
  pT = setTimeout(async () => {
    const key = vkey();
    try { const bl = await C.drawStrip(opts()); if (key === vkey()) { D.photo = bl; D.pkey = key; setSave(true); } }
    catch (e) { console.warn("[booth] photo", e); }
  }, 250);
}
// tile previews at their own size (Round 5: were whole 600x1800 JPEGs), one per task so taps stay responsive
async function renderMinis(kind) {
  const seq = D.mseq[kind] = (D.mseq[kind] || 0) + 1;
  const box = kind === "frame" ? $("frameChips") : $("filterChips"), d = Math.min(devicePixelRatio || 1, 2);
  for (const b of box.querySelectorAll(".tile")) {
    if (seq !== D.mseq[kind]) return;
    const o = opts({ stickers: null, ...(kind === "frame" ? { frame: FR.FRAMES.find(x => x.id === b.dataset.id) } : { filter: b.dataset.id }) });
    try {
      const m = b.querySelector(".mini");
      let cv;
      if (C.stripCanvas) cv = await C.stripCanvas(o, kind === "frame" ? { scale: 64 * d / 600 } : { scale: 64 * d / rectsOf(D.frame)[0].w, cell: 0 });
      else {          // mock compose: the old full-size path
        cv = Object.assign(document.createElement("canvas"), { width: 600, height: 1800 });
        cv.getContext("2d").drawImage(await createImageBitmap(await C.drawStrip(o)), 0, 0);
      }
      if (seq !== D.mseq[kind]) return;
      m.replaceChildren(cv); m.style.backgroundImage = "none";
    } catch {}
    await sleep(0);
  }
}
function setSave(on) { document.querySelector('[data-act="savePhoto"]').disabled = !on; }
function download(blob, name) {
  const a = Object.assign(document.createElement("a"), { href: URL.createObjectURL(blob), download: name });
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 60_000);
}
const fileName = (ext, tag = "") => `ttaro-gachi-${DATE.replace(/\./g, "")}${tag}.${ext}`;
const extOf = type => type.includes("mp4") ? "mp4" : "webm";

// Moving strip renders in the background from the moment the done screen opens; the story cut only when asked.
const LINES = ["영상 만드는 중", "프레임 끼우는 중", "리본 묶는 중", "반짝이 뿌리는 중"];
function setVid(which = "video", p = null) {
  for (const [id, kind, ready, idle] of [["vidBtn", "video", "영상도 저장", "영상 만드는 중"], ["storyBtn", "story", "스토리 영상 저장", "스토리용 영상"]]) {
    const b = $(id), span = b.querySelector("span"), cur = D[kind]?.key === vkey();
    const making = which === kind && p != null;
    b.classList.toggle("rendering", making); b.style.setProperty("--p", making ? Math.round(p * 100) : 0);
    if (making) { span.textContent = `${LINES[Math.floor(performance.now() / 2000) % LINES.length]} ${Math.round(p * 100)}%`; b.disabled = true; continue; }
    if (cur) { span.textContent = ready; b.disabled = false; continue; }
    if (kind === "video") { span.textContent = idle; b.disabled = true; }
    else { span.textContent = D.wantStory ? "스토리 영상 기다리는 중" : idle; b.disabled = D.wantStory; }
  }
}
// Round 5: the HQ moving cut (clipmatte.js, MODNet on ~10 fps of every clip) runs once in the background after the
// clips load; when it lands the video key changes and the strip video is made again with it (fast matte until then).
let hqcP = null;
function hqClips() {
  const media = D.media;
  hqcP ||= load("clipmatte").then(M => M.clipMattes(["a", "b"].flatMap(s => media[s].cuts.map(c => c.clip))))
    .then(m => { if (m.size && D.media === media) { D.mattes = m; videoJob(); } })
    .catch(e => console.warn("[booth] hq clips", e));
}
async function videoJob() {
  if (D.vjob || D.noVideo) return;
  D.vjob = true;
  try {
    if (!D.clips) await loadClips();
    hqClips();
    for (;;) {
      const key = vkey();
      const kind = D.video?.key !== key ? "video" : D.wantStory && D.story?.key !== key ? "story" : null;
      if (!kind) break;
      setVid(kind, 0);
      await D.fxP;                                  // Round 5: the 보정 fx for this look
      if (key !== vkey()) continue;
      D.vac = new AbortController();      // deco R5: a change stops the recording (later()); it restarts after VIDEO_IDLE
      const res = await C.recordMovingStrip(opts({ format: kind === "story" ? "story" : "strip", signal: D.vac.signal,
        onProgress: p => key === vkey() && setVid(kind, p) }));
      if (key !== vkey()) continue;
      D[kind] = { key, ...res };
      if (kind === "story") D.wantStory = false;
      if (kind === "video" && !$("stripImg").hidden && D.tab !== "sticker") showVideo();
      if (kind === "story") toast("스토리용 영상 완성! 버튼을 누르면 저장돼");
    }
  } catch (e) {
    if (e?.name === "AbortError") return;
    console.warn("[booth] video", e);
    D.noVideo = true;
    $("saveBtns").querySelector(".vidrow").hidden = true;
    toast("이 폰에서는 영상 저장이 안 돼요. 사진으로 저장해 줘", 3500);
  } finally { D.vjob = false; setVid(); }
}

// ---- actions ------------------------------------------------------------------------------------------------------
function capCount() { $("capCount").textContent = `${[...$("caption").value].length} / 16`; }
let capT;
$("caption").addEventListener("input", () => {
  capCount();
  clearTimeout(capT);
  capT = setTimeout(() => { D.caption = $("caption").value; changed(["frame"]); }, 450);
});
$("caption").addEventListener("keydown", e => { if (e.key === "Enter") e.target.blur(); });
for (const t of $$("[data-tab]")) t.onclick = () => {
  D.tab = t.dataset.tab;
  $$("[data-tab]").forEach(x => x.setAttribute("aria-selected", x === t));
  $$("[data-panel]").forEach(p => p.hidden = p.dataset.panel !== D.tab);
  stickerTab();
};

// ---- stickers (Round 5 deco) ---------------------------------------------------------------------------------------
// While choosing, stickers are DOM over the preview (moving one never re-renders the strip); compose bakes them into
// D.photo and the moving strip (opts.stickers, strip px). Drag = move, two fingers = size + angle, corner handle =
// the same with one finger, × = delete. Pointer events, so CDP touch, real touch and a mouse all work.
let ST = null;
const STK_MAX = 30, STK_S = { lo: 40, hi: 900 };
function stickerTab() {
  document.body.dataset.tab = D.tab;
  if (D.tab !== "sticker") { if (D.sel >= 0) { D.sel = -1; paintStk(); } if (D.media) showStill(); return; }
  if (D.media) showStill();          // the still + the editable layer, not the video
  buildStickers().catch(e => console.warn("[booth] stickers", e));
}
async function buildStickers(set = $("stkGrid").dataset.set || "svg") {
  ST ||= await import("./stickers.js");
  if ($("stkGrid").dataset.set === set) return;
  $("stkGrid").dataset.set = set;
  $$("#stkSets .pill").forEach(p => p.setAttribute("aria-pressed", p.dataset.set === set));
  const list = set === "svg" ? ST.DRAWN.map(d => ({ id: d.id, label: d.label })) : ST.EMOJI.map(e => ({ id: e, label: e }));
  $("stkGrid").replaceChildren(...list.map(s => {
    const b = Object.assign(document.createElement("button"), { type: "button" });
    b.setAttribute("aria-label", `${s.label} 붙이기`);
    if (set === "emoji") b.innerHTML = `<span class="emo" aria-hidden="true">${s.id}</span>`;
    else { const i = new Image(); i.alt = ""; b.append(i); ST.stickerUrl("svg", s.id).then(u => i.src = u).catch(() => {}); }
    b.onclick = () => addSticker(set, s.id);
    return b;
  }));
}
for (const p of $$("#stkSets .pill")) p.onclick = () => buildStickers(p.dataset.set);
function addSticker(kind, id) {
  if (D.stickers.length >= STK_MAX) return toast(`스티커는 ${STK_MAX}개까지 붙일 수 있어`);
  const n = D.stickers.length, j = ((n % 5) - 2) * 26;          // a little apart, so taps in a row do not stack exactly
  D.stickers.push({ kind, id, x: 300 + j, y: 900 + j, s: kind === "emoji" ? 140 : 170, r: 0 });
  D.sel = D.stickers.length - 1;
  paintStk(); later();
}
function placeStk(el, s) {
  Object.assign(el.style, { left: `${s.x / 6}%`, top: `${s.y / 18}%`, width: `${s.s / 6}%` });
  el.style.setProperty("--r", `${s.r}rad`);
}
function paintStk() {
  $("stk").replaceChildren(...D.stickers.map((s, i) => {
    const el = Object.assign(document.createElement("div"), { className: "stk-i" + (i === D.sel ? " sel" : "") });
    el.dataset.i = i;
    // focusable, so Chrome's touch adjustment (taps snap to the nearest tappable node) keeps a touch on the sticker on
    // the sticker instead of snapping it to the small ✕ / handle next to it (CDP touch, 10-27)
    el.tabIndex = -1;
    const img = new Image(); img.alt = ""; el.append(img);
    ST?.stickerUrl(s.kind, s.id).then(u => img.src = u).catch(() => {});
    if (i === D.sel) el.insertAdjacentHTML("beforeend", '<span class="stk-x" aria-hidden="true">✕</span>'
      + '<span class="stk-h" aria-hidden="true"><svg viewBox="0 0 16 16"><path d="M3 9a5 5 0 0 0 8.5 3.5M13 7A5 5 0 0 0 4.5 3.5" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"/><path d="M12 9.5v3.5H8.5M4 6.5V3h3.5" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></span>');
    placeStk(el, s);
    return el;
  }));
}
function bindStk() {
  const L = $("stk"), pts = new Map();
  let g = null, moved = false, miss = false;
  const cl = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const k = () => 600 / L.clientWidth;          // strip px per CSS px
  const begin = handle => {
    const s = D.stickers[D.sel];
    g = s && pts.size ? { s0: { ...s }, p0: [...pts.values()].map(p => ({ ...p })), handle: handle ?? g?.handle ?? false } : null;
    if (g && pts.size > 1) g.handle = false;
  };
  // hit test by coordinates, not e.target: Chrome's touch adjustment snapped a touch on the sticker to the ✕ next to
  // it (CDP touch, 10-27). ✕ / handle first (selected sticker), then the stickers top-down in their rotated boxes.
  const near = (sel, x, y) => { const el = L.querySelector(sel); if (!el) return false;
    const r = el.getBoundingClientRect(); return Math.hypot(x - r.x - r.width / 2, y - r.y - r.height / 2) < 18; };
  const hit = (x, y) => {
    if (D.sel >= 0 && near(".stk-x", x, y)) return "x";
    if (D.sel >= 0 && near(".stk-h", x, y)) return "h";
    const R = L.getBoundingClientRect(), K = k();
    for (let i = D.stickers.length - 1; i >= 0; i--) {
      const s = D.stickers[i], dx = x - R.x - s.x / K, dy = y - R.y - s.y / K, c = Math.cos(-s.r), sn = Math.sin(-s.r);
      if (Math.max(Math.abs(dx * c - dy * sn), Math.abs(dx * sn + dy * c)) <= s.s / K / 2) return i;
    }
    return null;
  };
  L.addEventListener("pointerdown", e => {
    if (D.tab !== "sticker" || e.pointerType === "mouse" && e.button) return;
    const h = pts.size ? null : hit(e.clientX, e.clientY);
    if (h === "x") {           // delete on press: touchmove is cancelled here, so no click is relied on
      e.preventDefault(); D.stickers.splice(D.sel, 1); D.sel = -1; paintStk(); later(); return;
    }
    if (!pts.size) {
      miss = h == null;          // off every sticker: a second finger still pinches the selected one; a lone tap deselects
      if (miss && D.sel < 0) return;
      if (!miss && h !== "h" && h !== D.sel) { D.sel = h; paintStk(); }
    }
    L.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    begin(pts.size === 1 ? h === "h" : false);
    if (miss && pts.size === 1) g = null;
  });
  // the ✕ is not a button (a button next to the sticker is what touch adjustment snapped to); keyboard: Delete
  addEventListener("keydown", e => {
    if (D.tab === "sticker" && D.sel >= 0 && (e.key === "Delete" || e.key === "Backspace") && !e.target.closest("input")) {
      D.stickers.splice(D.sel, 1); D.sel = -1; paintStk(); later();
    }
  });
  L.addEventListener("pointermove", e => {
    if (!g || !pts.has(e.pointerId)) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const s = D.stickers[D.sel], { s0, p0 } = g, now = [...pts.values()], K = k();
    if (!s) return;
    if (g.handle) {                              // one finger on the corner: size + angle about the centre
      const r = L.getBoundingClientRect(), cx = r.left + s0.x / K, cy = r.top + s0.y / K;
      const a = Math.atan2(p0[0].y - cy, p0[0].x - cx), b = Math.atan2(now[0].y - cy, now[0].x - cx);
      const d0 = Math.hypot(p0[0].x - cx, p0[0].y - cy), d1 = Math.hypot(now[0].x - cx, now[0].y - cy);
      s.s = cl(s0.s * (d0 ? d1 / d0 : 1), STK_S.lo, STK_S.hi); s.r = s0.r + b - a;
    } else if (now.length > 1 && p0.length > 1) {   // two fingers: move by the midpoint, size by the spread, turn by the angle
      const m0 = { x: (p0[0].x + p0[1].x) / 2, y: (p0[0].y + p0[1].y) / 2 }, m1 = { x: (now[0].x + now[1].x) / 2, y: (now[0].y + now[1].y) / 2 };
      const d0 = Math.hypot(p0[1].x - p0[0].x, p0[1].y - p0[0].y), d1 = Math.hypot(now[1].x - now[0].x, now[1].y - now[0].y);
      const a0 = Math.atan2(p0[1].y - p0[0].y, p0[1].x - p0[0].x), a1 = Math.atan2(now[1].y - now[0].y, now[1].x - now[0].x);
      s.x = cl(s0.x + (m1.x - m0.x) * K, 0, 600); s.y = cl(s0.y + (m1.y - m0.y) * K, 0, 1800);
      s.s = cl(s0.s * (d0 ? d1 / d0 : 1), STK_S.lo, STK_S.hi); s.r = s0.r + a1 - a0;
    } else {
      s.x = cl(s0.x + (now[0].x - p0[0].x) * K, 0, 600); s.y = cl(s0.y + (now[0].y - p0[0].y) * K, 0, 1800);
    }
    moved = true;
    const el = L.querySelector(`.stk-i[data-i="${D.sel}"]`); if (el) placeStk(el, s);
  });
  const end = e => {
    if (!pts.delete(e.pointerId)) return;
    if (pts.size) { begin(false); if (miss && pts.size === 1) g = null; return; }
    g = null;
    if (moved) { moved = false; later(); }
    else if (miss) { D.sel = -1; paintStk(); }   // a tap on the photo: let go of the selection
    miss = false;
  };
  L.addEventListener("pointerup", end); L.addEventListener("pointercancel", end);
  // in-app browsers scroll / zoom the page under a finger anyway unless touchmove is cancelled (README touch fixes)
  L.addEventListener("touchmove", e => { if (D.tab === "sticker") e.preventDefault(); }, { passive: false });
  L.addEventListener("wheel", e => {             // a computer: the wheel resizes the selected one
    const s = D.stickers[D.sel]; if (D.tab !== "sticker" || !s) return;
    e.preventDefault(); s.s = cl(s.s * Math.exp(-e.deltaY / 600), STK_S.lo, STK_S.hi);
    const el = L.querySelector(`.stk-i[data-i="${D.sel}"]`); if (el) placeStk(el, s);
    clearTimeout(L.wT); L.wT = setTimeout(later, 300);
  }, { passive: false });
}
bindStk();

// ---- zoom viewer (Round 5 deco): tap the strip -> full screen, pinch / drag / double-tap 2x, ✕ or swipe down ------
const Z = { s: 1, x: 0, y: 0 };
async function openZoom() {
  if (D.tab === "sticker" || !$("making").hidden || !D.media) return;
  const vid = !$("stripVid").hidden && D.video?.key === vkey();
  let el;
  if (vid) el = Object.assign(document.createElement("video"), { src: $("stripVid").src, muted: true, loop: true, playsInline: true, autoplay: true });
  else {
    const bl = D.pkey === vkey() && D.photo ? D.photo : await C.drawStrip(opts());
    el = new Image(); el.alt = "완성된 네컷 크게 보기"; el.src = URL.createObjectURL(bl);
    el.onload = () => setTimeout(() => URL.revokeObjectURL(el.src), 1000);
  }
  if (vid) { el.setAttribute("playsinline", ""); el.play?.().catch(() => {}); }
  Object.assign(Z, { s: 1, x: 0, y: 0 }); zoomApply();
  $("zoomStage").replaceChildren(el);
  $("zoom").hidden = false; $("zoom").style.opacity = 1;
  $("zoom").querySelector(".zoomx").focus({ preventScroll: true });
}
function closeZoom() {
  $("zoom").hidden = true; $("zoomStage").replaceChildren();
}
function zoomApply(fade = 1) {
  $("zoomStage").style.transform = `translate(${Z.x}px, ${Z.y}px) scale(${Z.s})`;
  $("zoom").style.opacity = fade;
}
function bindZoom() {
  const box = $("zoom"), pts = new Map();
  let g = null, lastTap = { t: 0, x: 0, y: 0 }, down = null;
  const rel = p => { const r = box.getBoundingClientRect(); return { x: p.x - r.left - r.width / 2, y: p.y - r.top - r.height / 2 }; };
  const mid = () => { const p = [...pts.values()]; return p.length > 1
    ? { x: (p[0].x + p[1].x) / 2, y: (p[0].y + p[1].y) / 2, d: Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y) } : { ...p[0], d: 0 }; };
  const begin = () => { g = pts.size ? { z: { ...Z }, at: mid() } : null; };
  box.addEventListener("pointerdown", e => {
    if (e.target.closest(".zoomx")) return;
    box.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); begin();
    if (pts.size === 1) down = { x: e.clientX, y: e.clientY, t: performance.now() };
  });
  box.addEventListener("pointermove", e => {
    if (!g || !pts.has(e.pointerId)) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const now = mid(), k = g.at.d && now.d ? now.d / g.at.d : 1, s = Math.min(5, Math.max(1, g.z.s * k)), kk = s / g.z.s;
    const p0 = rel(g.at), p1 = rel(now);
    // keep the point under the fingers under the fingers: t = p1 - (p0 - t0) * k
    Z.s = s; Z.x = p1.x - (p0.x - g.z.x) * kk; Z.y = p1.y - (p0.y - g.z.y) * kk;
    if (g.z.s <= 1.01 && pts.size === 1) { Z.x = 0; Z.y = Math.max(0, p1.y - p0.y); }   // at 1x a drag only pulls down
    zoomApply(g.z.s <= 1.01 && pts.size === 1 ? 1 - Math.min(0.6, Z.y / 400) : 1);
  });
  const end = e => {
    const at = pts.get(e.pointerId);              // last move position (a touchend may carry no coordinates)
    if (!pts.delete(e.pointerId)) return;
    if (pts.size) return begin();
    const z0 = g?.z; g = null;
    if (z0 && z0.s <= 1.01 && Z.y > 110) return closeZoom();                       // swiped down
    const t = performance.now(), quick = down && t - down.t < 300 && Math.hypot(at.x - down.x, at.y - down.y) < 12;
    if (quick && t - lastTap.t < 350 && Math.hypot(at.x - lastTap.x, at.y - lastTap.y) < 30) {   // double tap
      const p = rel(at);
      if (Z.s > 1.01) Object.assign(Z, { s: 1, x: 0, y: 0 }); else Object.assign(Z, { s: 2, x: -p.x, y: -p.y });
      lastTap.t = 0;
    } else if (quick) lastTap = { t, x: at.x, y: at.y };
    if (Z.s <= 1.01) Object.assign(Z, { s: 1, x: 0, y: 0 });
    zoomApply();
  };
  box.addEventListener("pointerup", end); box.addEventListener("pointercancel", end);
  box.addEventListener("touchmove", e => e.preventDefault(), { passive: false });
  box.addEventListener("wheel", e => { e.preventDefault(); Z.s = Math.min(5, Math.max(1, Z.s * Math.exp(-e.deltaY / 400))); if (Z.s <= 1.01) Object.assign(Z, { s: 1, x: 0, y: 0 }); zoomApply(); }, { passive: false });
  addEventListener("keydown", e => { if (e.key === "Escape" && !box.hidden) closeZoom(); });
  $("preview").addEventListener("click", () => openZoom().catch(e => console.warn("[booth] zoom", e)));
}
bindZoom();

const ACT = {
  startA: () => startShoot("a"),
  startB: () => startShoot("b"),
  mine: () => shareScreen(true),
  recheck: () => route(),
  shoot: () => runShoot(),
  stop: () => { if (S.busy) { S.stop = true; $("shootHint").textContent = "이번 장까지만 찍고 멈출게"; $("shootHint").hidden = false; } },
  pickDone, reshoot,
  sound: () => setSound(!soundOn),
  mirror: () => {
    if (!S.cam || S.busy) return;
    S.cam.camState.mirror = !S.cam.camState.mirror;
    syncMirror();
  },
  flip: async () => {
    if (!S.cam || S.busy) return;
    $("camwait").hidden = false; $("camwait").textContent = "카메라 바꾸는 중…";
    try { await S.cam.flipCam($("video")); } catch (e) { return camError(e); }
    $("camwait").hidden = true;
    fitStage(); syncMirror();
  },
  upload: askUpload, retryUpload: upload, hqWait, hqNow: () => upload(), share, copy,
  hideHqNote: () => { $("hqNote").hidden = true; S.hqNoteOff = true; },
  savePhoto: async () => {
    if (D.pkey !== vkey()) { const key = vkey(); D.photo = await C.drawStrip(opts()); D.pkey = key; }
    download(D.photo, fileName("jpg"));
  },
  zoomClose: () => closeZoom(),
  saveVideo: () => D.video?.key === vkey() && download(D.video.blob, fileName(extOf(D.video.type))),
  saveStory: () => {
    if (D.story?.key === vkey()) return download(D.story.blob, fileName(extOf(D.story.type), "-story"));
    D.wantStory = true; setVid(); videoJob();
  },
  toChrome: () => location.href = env.chromeUrl(shareUrl()),
  hideHint: () => { $("hintChip").hidden = true; S.hintOff = S.side; },
  layReset: () => setLay({ x: 0, y: 0, scale: 1 }),
  again: fresh,
};
document.addEventListener("click", e => {
  const b = e.target.closest("[data-act]");
  if (b?.dataset.act === "del" || b?.dataset.act === "delYes" || b?.dataset.act === "delNo")
    return del(b.closest(".del"), b.dataset.act === "del" ? null : b.dataset.act === "delYes");
  if (b && ACT[b.dataset.act]) ACT[b.dataset.act]();
});

// ---- boot ---------------------------------------------------------------------------------------------------------
try { env = await import("./env.js"); } catch {}
window.__booth = { S, D, MOCK };   // dev handle for the headless checks
route();
