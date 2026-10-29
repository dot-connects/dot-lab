// App shell: screens + role routing (PLAN Task 6 role table). Capture/compose modules load on demand,
// so the first screen needs only this file + api.js, and MediaPipe loads after the first tap.
import * as api from "./api.js";
import { mockCapture, mockCompose, mockFrames, mockFilters } from "./ui.js";

const $ = id => document.getElementById(id);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const Q = new URLSearchParams(location.search);
const MOCK = Q.has("mock");                         // dev: no MediaPipe, placeholder cut-outs
// Round 4 (booth style): one tap shoots SHOTS in a row, SECS each; then pick 4 (tap order = strip order).
// Round 7: 6 shots (was 8, operator 10-28); B's shot j floats A's cut j % 4
const SHOTS = 6, SECS = Q.has("fast") ? 4 : 5;      // 5 s each (operator 10-27); ?fast = tests only
const POSES = ["브이!", "반쪽 하트", "꽃받침", "깜짝 놀란 척", "윙크 한 번", "마음대로!"];
const NAMES = [1, 2, 3, 4].flatMap(c => [`${c}-raw.jpg`, `${c}-cut.png`, `${c}-clip`]);
// strip cell aspect per shape (compose LAYOUTS CW:CH): 가로 = wide cells, 세로 = tall 3:4 cells (Round 6 #9)
const CELLS = { wide: 552 / 382, tall: 426 / 568 };
const cellAsp = () => CELLS[S.shape] || CELLS.wide;
// a cell-sized canvas for the pick grid / review, per shape
const cellPx = () => S.shape === "tall" ? { w: 426, h: 568 } : { w: 552, h: 382 };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const calm = () => matchMedia("(prefers-reduced-motion: reduce)").matches;
const today = new Date(), pad = n => String(n).padStart(2, "0");
const DATE = `${today.getFullYear()}.${pad(today.getMonth() + 1)}.${pad(today.getDate())}`;

// ---- modules ------------------------------------------------------------------------------------------------------
async function load(name, mock) {
  if (MOCK && mock) return mock;
  // a module fetch fails now and then under load (B's camera.js, headless 10-28) and a failed URL can stay failed in the
  // module map: retry once as is, then once under a fresh URL
  let err;
  for (const q of ["", "", `?r=${Date.now()}`]) {
    try { return await import(`./${name}.js${q}`); }
    catch (e) { err = e; await sleep(400); }
  }
  console.warn(`[booth] ${name}.js unavailable, using placeholder:`, err.message); if (mock) return mock; throw err;
}
let env = { app: "브라우저", inApp: false, isIOS: false, framed: top !== self, chromeUrl: u => u };

// ---- state --------------------------------------------------------------------------------------------------------
const S = {
  id: Q.get("s"), side: null, token: null,      // side = what this page shoots/uploads ("a" | "b")
  cuts: [], sent: new Set(), ghost: [],          // the 4 picked Cuts (strip order), uploaded names, A's cut-out URLs (B's overlay)
  shots: [], shotUrls: [], pick: [], pairs: null, // all SHOTS shots + their raw URLs until the pick; B: A's cut per cell
  redo: false, hq: [],                           // 전체 다시 찍기 used (once per page); HQ cut path per cell
  view: "a", pickBg: null, flipP: null,           // B's shoot view (Round 6 #10); pick-screen background; camera switch in flight
  cam: null, cap: null, bound: false, busy: false,
  shape: "wide",                                 // A's 가로 ("wide") / 세로 ("tall") pick, B follows it (opts.shape)
  ui: document.body.dataset.ui === "cute" ? "cute" : "film",   // A's UI theme (screen 0), B follows it (opts.ui); index.html sets it from ?ui= before paint
};
function setShape(sh) {
  S.shape = sh === "tall" ? "tall" : "wide";
  document.body.dataset.shape = S.shape;          // CSS: pick grid, review, previews, mini strips per shape
}
// UI theme: "film" = 필름레일 (app.css), "cute" = 말랑 부스 (css/cute.css + js/cute.js style everything under body[data-ui="cute"])
function setUi(u) {
  S.ui = u === "cute" ? "cute" : "film";
  document.body.dataset.ui = S.ui;
}
const HINT_A = "친구 자리를 생각해서 한쪽으로 비켜서 찍어 줘";
const HINT_SHOOT = `셔터 한 번이면 ${SHOTS}장을 이어서 찍어 · 한 장에 5초`;
// the link carries a cute theme in the query too, so B's page (and the Chrome hand-off) paints in it before the Worker answers
const uiQ = () => S.ui === "cute" ? "&ui=cute" : "";
const shareUrl = () => `${location.origin}${location.pathname}?s=${S.id}${uiQ()}`;
const token = () => S.token || api.loadRole(S.id)?.token;

// ---- screens ------------------------------------------------------------------------------------------------------
// UI hooks (index.html top comment, CONTRACT "UI themes"): body[data-screen], body[data-flow] = a | b | c (c = decorate in
// Chrome), body[data-step] = 0..3 on the 4-step tracker (absent on landings / messages), a "booth:screen" event after
// each change. Film draws its rail into the screen's .uihead; with cute the .uihead is left empty for cute.js.
const STEPS = { mode: 0, shoot: 0, pick: 1, upload: 3, share: 3, handoff: 3, done: 3 };
const RAIL = { a: ["찍기", "고르기", "보정", "보내기"], b: ["찍기", "고르기", "보정", "완성"], c: ["찍기", "고르기", "보정", "꾸미기"] };
function show(name, step = STEPS[name]) {
  const from = document.body.dataset.screen, sec = $("s-" + name), B = document.body.dataset;
  for (const s of $$(".screen")) s.hidden = s.id !== "s-" + name;
  B.screen = name;
  B.flow = name === "done" || name === "handoff" ? "c" : /^(theme|landA|mode)$/.test(name) ? "a" : name === "landB" ? "b"
    : name === "share" ? (S.shareMode === "done" ? "b" : "a") : S.side || "a";
  if (step == null) delete B.step; else B.step = step;
  paintHead(sec, name);
  sec.classList.toggle("slide", name === "shoot" && from !== "shoot");   // land -> shoot slides, the rest fades
  if (scrollY > 1) scrollTo(0, 1);
  queueMicrotask(() => document.dispatchEvent(new CustomEvent("booth:screen")));   // after the caller has filled the screen
}
// film (screens3 concept 1): "D:OT LAB · tag" + the film rail (done steps white, this one lime)
function paintHead(sec, name) {
  const h = sec.querySelector(".uihead"), B = document.body.dataset;
  if (!h) return;
  if (S.ui !== "film" || B.step == null) return h.replaceChildren();
  const k = +B.step, tall = S.shape === "tall";
  const tag = { mode: "모양", shoot: k === 2 ? "보정" : S.side === "b" ? "친구 옆" : `${tall ? "세로" : "가로"} · ${SHOTS}장`, pick: `${SHOTS}장 → 4장`, upload: "올리기",
    share: { wait: "내 링크", done: "완성" }[S.shareMode] || "링크", handoff: "크롬으로", done: `${tall ? "세로" : "가로"} 네컷` }[name] || "";
  h.innerHTML = (sec.querySelector(".topbar") ? "" : `<div class="utop"><span>D:OT LAB</span><span>${tag}</span></div>`)
    + `<div class="rail">${RAIL[B.flow].map((l, i) => `<u class="${i < k ? "dn" : i === k ? "on" : ""}">${l}</u>`).join("")}</div>`;
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
// "chak" (25 ms highpass) + "kk" 45 ms later (70 ms bandpass). Always on, also with the beeps muted (Round 6): a web
// capture is silent, and a camera that makes no sound is bad manners
function shutterSound() {
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
  if (!S.id) { show("theme"); return prefetchHQ(); }
  let st;
  try { st = await api.status(S.id); }
  catch { return msg({ title: "앗, 연결이 안 돼", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: route }); }
  if (st.error) return expired(st.error);
  setShape(st.opts?.shape);
  setUi(st.opts?.ui ?? S.ui);        // a Worker without opts.ui (before Round 6) keeps the link's ?ui=
  const role = api.loadRole(S.id)?.role;
  if (!st.a.done) {
    if (role === "a") return msg({ title: "올리다 멈췄어", text: "찍은 사진이 이 화면에 남아 있지 않아. 새로 찍어 줘.", btn: "다시 찍기", onBtn: fresh });
    return msg({ title: "아직 친구가 올리는 중이에요", text: "조금 뒤에 이 링크를 다시 열어 줘.", btn: "다시 확인", onBtn: route });
  }
  if (!st.b.done) return role === "a" ? shareScreen("wait") : (landB(), prefetchHQ());
  return decorate();
}
// Round 6 #3/#4: the finished strip is decorated in Chrome (Safari on iPhone), each side on its own device; inside a
// messenger's in-app browser the page hands off first
const decorate = (note = "") => env.inApp ? handoffScreen() : doneScreen(note);
const me = () => api.loadRole(S.id)?.role || Q.get("me");
// the hand-off link carries who I am and my pick-screen background (labels + default bg in Chrome; no token). Query,
// not hash: the Android intent:// link needs the fragment for #Intent;...;end
const decoUrl = () => shareUrl() + (me() ? `&me=${me()}${S.pickBg ? "&bg=" + S.pickBg : ""}` : "");
function openOut(url) { S.handoffUrl = env.chromeUrl(url); location.href = S.handoffUrl; }
function handoffScreen() {
  show("handoff");
  $("hoLink").textContent = decoUrl(); $("hoBox").hidden = true;
  $("hoText").hidden = env.isIOS; $("hoIOS").hidden = !env.isIOS;
  // iOS: no way to force Safari, except KakaoTalk's openExternal, reported to work only from a tap (ios-audit #7)
  const kakaoIOS = env.isIOS && env.app === "KakaoTalk";
  $("hoGo").hidden = env.isIOS && !kakaoIOS;
  if (kakaoIOS) $("hoGo").textContent = "Safari에서 열기";
  if (!env.isIOS && !S.hoTried) { S.hoTried = true; openOut(decoUrl()); }    // try once at once; the button stays
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
  // A's cut-outs for the cover and for B's overlay, fetched while B reads the screen
  S.ghostP = Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-cut.png`).catch(() => null)))
    .then(g => S.ghost = g);
  // A's raw photos: A's room for B's view (b) and the pick screen's 친구 배경
  S.ghostRawP = Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-raw.jpg`).catch(() => null)))
    .then(g => S.ghostRaw = g);
  window.__coverReal?.(await S.ghostP);   // the B cover swaps its drawn friend for A's real cut-outs (index.html)
}

// ---- A picks 가로 / 세로 first (Round 6 #9, mockups/screens3.html A-2); the link carries it and B follows ----------
function modeScreen() {
  show("mode");
  for (const b of $$("#modes .mode")) {
    b.setAttribute("aria-checked", b.dataset.shape === S.shape);
    b.onclick = () => { setShape(b.dataset.shape); modeScreen(); };
  }
  $("modeGoText").textContent = S.shape === "tall" ? "세로로 찍기" : "가로로 찍기";
}

// ---- shoot --------------------------------------------------------------------------------------------------------
async function startShoot(side) {
  S.side = side; S.sent.clear();
  show("shoot");
  resetShoot();
  // Round 7: nobody moves A any more, so B has no hint (no mover); a chip with no text never shows
  const hint = side === "a" ? HINT_A : "";
  $("hintText").textContent = hint; $("hintChip").hidden = !hint || S.hintOff === side;
  setSound(soundOn);
  $("camwait").hidden = false; $("camwait").textContent = "카메라 켜는 중…";
  try { ctxA(); } catch {}          // in the tap: the shutter sound plays even with the beeps off
  const video = $("video");
  try {
    S.cam ||= await load("camera");
    S.cam.camState.shape = S.shape;    // the camera frame (and every still / clip) is 4:3 for 가로, 3:4 for 세로
    await S.cam.startCam(video);
    // after the camera, not alongside it: with the HQ model already on the GPU (landing prefetch) MediaPipe's GPU
    // init froze getUserMedia past the 5 s stall limit (headless 10-27, 3 of 3 runs)
    const segP = MOCK ? null : load("segment").then(m => { S.seg = m; return m.loadSegmenter(); });
    segP?.catch(() => {});
    fitStage();
    $("camwait").textContent = "배경 지우개 준비 중…";
    S.cap ||= MOCK ? mockCapture(S.cam) : await load("capture", mockCapture(S.cam));
    await segP;
  } catch (e) { return camError(e); }
  $("camwait").hidden = true;
  syncMirror();
  if (!S.bound) {
    S.cam.bindZoomFocus($("stage")); S.bound = true;
    new ResizeObserver(fitStage).observe($("stage"));
    video.addEventListener("resize", fitStage);
  }
  $("viewPick").hidden = side !== "b";
  if (side === "b") {
    await S.ghostP; await S.ghostRawP; setView(S.view);
    // Round 5: A's 보정 on the overlay, in the background. Not during B's cover: the face landmarker's load + GPU compile
    // took the cover to ~1.5 fps (perf study #5); now it runs once the camera is up and the overlay swaps when done
    S.lookP ||= ghostLook().then(() => S.cutIdx != null && document.body.dataset.screen === "shoot" && setCut(S.cutIdx, false))
      .catch(e => console.warn("[booth] A's look", e));
  }
}
// Lay the camera out so the strip-cell crop (the whole photo) spans the stage width and sits in its middle.
// The crop is compose's cellCrop on the stored frame (camera.js frameRect): full width, a quarter of the spare height
// taken from the top (or the centre width, when the frame is wider than the cell). Round 6 #8: only the crop shows,
// everything outside it is covered (.crop), so what is on screen is exactly what lands in the cell.
// fresh shoot screen: no shots, shutter ready (start, and 전체 다시 찍기)
function resetShoot() {
  S.shotUrls.forEach(u => URL.revokeObjectURL(u));
  S.cuts = []; S.shots = []; S.shotUrls = []; S.pick = []; S.pairs = null; S.hq = [];
  S.gen = (S.gen || 0) + 1; S.hqBusy = false; $("hqChip").hidden = true; $("prepOv").hidden = true;   // an older HQ run stops
  $("s-shoot").classList.remove("shooting-done");
  $("controls").hidden = false; $("shootDone").hidden = true; $("review").hidden = true; $("revOrig").hidden = true; $("pips").hidden = false;
  $("shootHint").hidden = false; $("shootHint").textContent = HINT_SHOOT; $("lastShot").hidden = true;
  $("hqNote").hidden = MOCK || !!S.hqNoteOff;
  setCut(0);
}
function fitStage() {
  const v = $("video"), st = $("stage");
  if (!v.videoWidth || !S.cam) return;
  const W = st.clientWidth, H = st.clientHeight, fr = S.cam.frameRect(v), CELL = cellAsp();
  const cw = fr.sw / fr.sh > CELL ? fr.sh * CELL : fr.sw, ch = cw / CELL, cx = (fr.sw - cw) / 2, cy = (fr.sh - ch) / 2 * 0.25;
  // while shooting the stage is the cell's own shape (camera3 layout 1), so the crop fills it; the 보정 review keeps a margin
  const k = Math.min(W / cw, (H * ($("s-shoot").classList.contains("shooting-done") ? 0.92 : 1)) / ch);
  const left = W / 2 - (fr.sx + cx + cw / 2) * k, top = H / 2 - (fr.sy + cy + ch / 2) * k;
  const px = (el, b) => Object.assign(el.style, { left: b.x + "px", top: b.y + "px", width: b.w + "px", height: b.h + "px" });
  px(v, { x: left, y: top, w: v.videoWidth * k, h: v.videoHeight * k });
  px($("frame"), { x: left + fr.sx * k, y: top + fr.sy * k, w: fr.sw * k, h: fr.sh * k });
  S.cropIn = { x: cx * k, y: cy * k, w: cw * k, h: ch * k };                  // crop inside #frame
  px($("crop"), S.cropIn);
  S.cropBox = { x: left + (fr.sx + cx) * k, y: top + (fr.sy + cy) * k, w: cw * k, h: ch * k };   // crop inside the stage
  px($("snap"), S.cropBox);
}
const isMoved = l => Math.abs(l.x) > 0.002 || Math.abs(l.y) > 0.002 || Math.abs(l.scale - 1) > 0.005;
function syncMirror() {
  $("video").classList.toggle("mirror", S.cam.camState.mirror);
  document.querySelector('[data-act="mirror"]').setAttribute("aria-pressed", S.cam.camState.mirror);
}
// B's view (Round 6 #10): (a) B's own camera picture with A's still cut-out in front, or (b) A's room (A's raw photo of
// that slot) + B's live cut-out + A's cut-out on top: layer order A, B, background everywhere (#7)
function setView(v) {
  S.view = v;
  $$("#viewPick .pill").forEach(p => p.setAttribute("aria-pressed", p.dataset.view === v));
  $("stage").classList.toggle("viewb", v === "b" && S.side === "b");
  $("liveCut").hidden = !(v === "b" && S.side === "b");
  setCut(S.cutIdx ?? S.shots.length, false);
  if (v === "b") liveLoop();
}
for (const p of $$("#viewPick .pill")) p.onclick = () => setView(p.dataset.view);
function liveLoop() {
  if (S.liveOn) return;
  S.liveOn = true;
  const v = $("video"), c = $("liveCut");
  const step = () => {
    if (S.view !== "b" || S.side !== "b" || document.body.dataset.screen !== "shoot" || $("s-shoot").classList.contains("shooting-done")) { S.liveOn = false; return; }
    if (v.videoWidth && S.seg && S.cam) {
      const fr = S.cam.frameRect(v), w = 480, h = Math.round(w * fr.sh / fr.sw), k = w / fr.sw, x = c.getContext("2d");
      if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
      // while shooting, capture's loop runs the model (same matte); otherwise every 2nd camera frame (15/s): view b fps 10 -> 20 (perf study #3)
      if (!S.busy && (S.liveN = (S.liveN || 0) + 1) % 2) S.seg.segmentFrame(v);
      x.clearRect(0, 0, w, h); x.save(); x.translate(-fr.sx * k, -fr.sy * k);
      S.seg.drawCutout(x, v, v.videoWidth * k, v.videoHeight * k, S.cam.camState.mirror); x.restore();
    }
    // one redraw per new camera frame, not per display frame
    if (v.requestVideoFrameCallback) v.requestVideoFrameCallback(step); else requestAnimationFrame(step);
  };
  step();
}
function camError(e) {
  console.warn("[booth] camera", e);
  if (e?.message === "stall" || e?.name === "NotReadableError" && env.inApp)
    return handoff("카메라가 대답을 안 해", "이 앱에서 카메라가 멈췄어. 크롬에서 열면 바로 돼.");
  if (e?.name === "NotAllowedError")
    return msg({ title: "앗, 카메라가 안 켜져", text: env.isIOS ? "카메라 권한을 허용해 줘. 주소창 왼쪽 aA(또는 ⋯) → 웹사이트 설정 → 카메라 → 허용." : "카메라 권한을 허용해 줘. 주소창 옆 자물쇠 → 카메라 → 허용.",
      btn: "다시 해 보기", onBtn: () => startShoot(S.side) });
  msg({ title: "앗, 카메라가 안 켜져", text: "다른 앱이 카메라를 쓰고 있으면 끄고 다시 해 줘.", btn: "다시 해 보기", onBtn: () => startShoot(S.side) });
}
function handoff(title, text) {
  if (env.isIOS && env.inApp)
    return msg({ title, text: "오른쪽 위 공유 메뉴 → Safari로 열기 → 같은 링크에서 다시 해 줘.", btn: "다시 해 보기", onBtn: () => startShoot(S.side) });
  msg({ title, text, btn: "크롬에서 열기", onBtn: () => location.href = env.chromeUrl(location.href),
    btn2: "여기서 다시 해 보기", onBtn2: () => startShoot(S.side) });
}

// shot i of SHOTS; B sees A's cut (i % 4): A's four picked cuts, then the first ones again
function setCut(i, pop = true) {
  S.cutIdx = i;
  $("step").textContent = `${Math.min(i + 1, SHOTS)} / ${SHOTS}`;
  const p = $("pose"); p.textContent = POSES[i] || POSES[0];
  if (pop) restart(p, "pop");
  // Round 6 #5: A is always the still cut-out of this slot (A's clip only feeds the moving strip at the end)
  const k = i % 4, g = $("ghost"), src = S.side === "b" && S.ghost[k], room = S.side === "b" && S.view === "b" && S.ghostRaw?.[k];
  g.hidden = !src; if (src) g.src = src;
  $("ghostBg").hidden = !room; if (room) $("ghostBg").src = room;
  $$("#pips i").forEach((t, n) => { t.className = n < i ? "has" : n === i ? "now" : ""; });
}
function onTick(n) {
  const c = $("count");
  c.textContent = n; restart(c, "go");
  if (n <= 3 && n > 0) pip(n === 1 ? 1320 : 880, n === 1 ? 0.12 : 0.04);   // beeps on 3-2-1 only
  if (n === 2) $("rec").hidden = false;
  if (n === 0) flash();          // capture.js: the shutter itself (after a camera switch in flight, if any)
}
async function shootIdx(i) {
  setCut(i);
  const cut = await S.cap.shootOne(i, { video: $("video"), onTick, seconds: SECS, hold: () => S.flipP });
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
// One tap shoots all SHOTS straight through (Round 6: no stop button), then the pick. After an error it goes on from the
// next shot.
async function runShoot() {
  if (S.busy || !S.cap) return;
  S.busy = true; document.body.classList.add("busy");
  $("shootHint").hidden = true; $("shootDone").hidden = true; $("review").hidden = true; $("revOrig").hidden = true;
  try {
    for (let i = S.shots.length; i < SHOTS; i++) {
      await shootIdx(i);
      if (i < SHOTS - 1) await sleep(250);
    }
  } catch (e) {
    console.warn("[booth] shoot", e); toast("앗, 찍다가 멈췄어. 셔터를 다시 눌러 줘");
  } finally {
    S.busy = false; document.body.classList.remove("busy");
    $("count").textContent = ""; $("rec").hidden = true;
  }
  const n = S.shots.length;
  if (n < SHOTS) {
    setCut(n, false);
    $("shootHint").textContent = n ? `${n}장 찍었어. 셔터를 누르면 ${n + 1}번째부터 이어서 찍어` : HINT_SHOOT;
    $("shootHint").hidden = false;
    if (n) $("hintChip").hidden = $("hqNote").hidden = true;   // the panel shows one note: "n장 찍었어" wins over the start notes
    return;
  }
  prepAndPick();
}

// ---- HQ cut-out of all shots (operator 10-27): starts right after the last shot, before the pick ---------------------
// warmStill (the one GPU freeze) then cutStill shot by shot, 1..SHOTS; after the pick only the 4 picked still pending.
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
// progress everywhere it shows: the "6장 준비 중" overlay, the grid cell (+ its 다듬는 중 badge), the review chip
function onHQ(c, shots) {
  $("prepText").textContent = `${SHOTS}장 준비 중… ${shots.filter(x => x.hq).length}/${SHOTS}`;
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
// After the last shot: a short "6장 준비 중" (at most 5 s, less when all are done), then the pick.
async function prepAndPick() {
  S.hqFrozen = false;
  if (S.cap.hqCut) {
    $("prepText").textContent = `${SHOTS}장 준비 중… 0/${SHOTS}`; $("prepOv").hidden = false;
    S.hqP = upgradeAll();
    await Promise.race([S.hqP, sleep(5000)]);
    $("prepOv").hidden = true;
  }
  pickScreen();
}

// ---- pick 4 of SHOTS (no time limit; tap order = strip order; one 전체 다시 찍기) -----------------------------------------
// One cell composed like the result, on the pick screen's background (Round 6 #1/#6): A = own room on / off, B = the
// friend's room / mine / none; "none" = the plain booth backdrop. On B's phone B + the paired A cut in front (B's move
// of A applied), layer order A, B, background (#7). The strip crop is compose's cellCrop.
const PICK_BG = { a: [["a", "배경 켜기"], ["none", "배경 끄기"]], b: [["a", "친구 배경"], ["b", "내 배경"], ["none", "배경 없음"]] };
const bgId = v => !v || v === "none" ? "booth-pink" : v;
async function cellCanvas(cut, ai, look = null) {   // look: Round 5 보정 (review only; the grid stays plain)
  const { w, h } = cellPx(), CELL = cellAsp();
  const c = Object.assign(document.createElement("canvas"), { width: w, height: h }), x = c.getContext("2d");
  const C = await load("compose", mockCompose);
  const [raw0, cut0] = await cutImgs(cut), { raw, cut: cutImg } = await withLook(raw0, cut0, look), bg = bgId(S.pickBg);
  if (S.side === "b" && ai != null && S.ghost[ai] && C.drawCell) {
    const aCut = await loadImg(S.ghost[ai]), aRaw = S.ghostRaw?.[ai] ? await loadImg(S.ghostRaw[ai]) : aCut;
    C.drawCell(x, { x: 0, y: 0, w, h }, 0, { a: { cuts: [{ raw: aRaw, cut: aCut, clip: null }] },
      b: { cuts: [{ raw, cut: cutImg, clip: null }] }, bg });
  } else {
    const B = C.BACKGROUNDS?.find(b => b.id === bg), img = bg === "a" ? raw : cutImg;
    if (bg !== "a") { if (B?.draw) B.draw(x, w, h); else { x.fillStyle = "#ffc2d6"; x.fillRect(0, 0, w, h); } }
    const iw = img.naturalWidth || img.width, ih = img.naturalHeight || img.height;   // compose cellCrop
    if (iw / ih > CELL) { const sw = ih * CELL; x.drawImage(img, (iw - sw) / 2, 0, sw, ih, 0, 0, w, h); }
    else { const sh = iw / CELL; x.drawImage(img, 0, (ih - sh) / 2 * 0.25, iw, sh, 0, 0, w, h); }
  }
  return c;
}
// the background buttons: the pick screen (#pickBg, redraws the grid) and the retouch review (#revBg, Round 7 #3,
// redraws the four cells); one S.pickBg, so the pick's choice carries over (and on into decorate)
function pickBgRow(box = "pickBg", redraw = () => (async () => { for (let j = 0; j < S.shots.length; j++) await paintCell(j, false); })()) {
  S.pickBg ??= S.side === "b" ? (S.view === "b" ? "a" : "b") : "a";     // B: what the shoot view showed
  $(box).replaceChildren(...PICK_BG[S.side].map(([v, label]) => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill", textContent: label });
    b.dataset.bg = v; b.setAttribute("aria-pressed", S.pickBg === v);
    b.onclick = () => {
      if (S.pickBg === v) return;
      S.pickBg = v; pickBgRow(box, redraw);
      redraw().catch(e => console.warn("[booth] bg redraw", e));
    };
    return b;
  }));
}
function pickScreen() {
  // the camera (the phone's biggest steady heat source) is off for the pick + 보정; reshoot turns it back on (perf study #4)
  $("video").srcObject?.getTracks().forEach(t => t.stop());
  S.pick = []; S.cellCv = [];
  show("pick");
  $("reshoot").hidden = S.redo;
  document.querySelector(".pickfine").textContent = `누른 순서대로 ${S.shape === "tall" ? "왼쪽" : "위에서"}부터 들어가 · 다시 누르면 빠져`;
  pickBgRow();
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
  const bg = S.pickBg, cv = await cellCanvas(shot, S.side === "b" ? j % 4 : null);
  if (S.shots[j] !== shot || S.pickBg !== bg) return;
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
    const { w, h } = S.shape === "tall" ? { w: 120, h: 160 } : { w: 184, h: 127 };
    if (src) { const c = Object.assign(document.createElement("canvas"), { width: w, height: h }); c.getContext("2d").drawImage(src, 0, 0, w, h); d.append(c); }
    else d.innerHTML = `<img alt="" src="${S.shotUrls[j]}">`;
    return d;
  }), Object.assign(document.createElement("div"), { className: "mfoot", textContent: "따로 같이 네컷" }));
}
function pickDone() {
  if (S.pick.length !== 4) return;
  const keep = S.pick.slice();
  S.cuts = keep.map(j => S.shots[j]);
  S.pairs = S.side === "b" ? keep.map(j => j % 4) : null;
  // the unpicked shots go now (stills + clips are a lot for a phone); the picked URLs stay for the review
  S.shotUrls.forEach((u, j) => keep.includes(j) || URL.revokeObjectURL(u));
  S.shots.forEach((c, j) => { if (!keep.includes(j)) { c.raw = c.cut = c.clip = null; c.hqFail = true; } });
  S.shots = []; S.shotUrls = keep.map(j => S.shotUrls[j]); S.cellCv = [];
  show("shoot", 2);      // 보정
  $("s-shoot").classList.add("shooting-done");
  $("controls").hidden = true; $("shootDone").hidden = false; $("pips").hidden = true; $("lastShot").hidden = true;
  $("shootHint").hidden = true; $("hqNote").hidden = true; $("hqAsk").hidden = true;
  $("step").textContent = "4 / 4"; $("pose").textContent = "다 골랐어! 맘에 들어?"; restart($("pose"), "pop");
  $("ghost").hidden = true; $("ghostBg").hidden = true; $("liveCut").hidden = true; $("viewPick").hidden = true;
  $("stage").classList.remove("viewb"); S.cutIdx = null;
  hqChip();
  S.revQ = 1; RZ.reset();
  pickBgRow("revBg", review);
  reviewLook();      // Round 5 보정
  review().catch(e => console.warn("[booth] review", e));
}
function reshoot() {
  if (S.redo) return;
  S.redo = true;
  show("shoot");
  resetShoot();
  $("camwait").hidden = false; $("camwait").textContent = "카메라 켜는 중…";
  S.cam.startCam($("video"), S.cam.camState.front ? "user" : "environment").then(() => {
    $("camwait").hidden = true; syncMirror(); fitStage();
    if (S.side === "b") { $("viewPick").hidden = false; setView(S.view); }
  }, camError);
}

// "맘에 들어?": the four cells composed like the strip (B: B's pick + its paired A in front).
// S.revQ = 2 once the view was zoomed past 1.5x (RZ): cells and canvas at twice the pixels, so faces stay sharp
const reviewCell = i => { const q = S.revQ || 1;
  return cellCanvas(S.cuts[i], S.side === "b" ? S.pairs?.[i] ?? i : null, S.beautyReady && !S.revOrig ? S.look : null, 552 * q, 382 * q); };
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
  const R = $("review"), st = $("stage"), d = (devicePixelRatio || 1) * (S.revQ || 1);
  R.width = st.clientWidth * d; R.height = st.clientHeight * d;
  const CELL = cellAsp(), x = R.getContext("2d"), g = 8 * d, cw = Math.min((R.width - 3 * g) / 2, ((R.height - 3 * g) / 2) * CELL), ch = cw / CELL;
  S.revGeo = { ox: (R.width - 2 * cw - g) / 2, oy: (R.height - 2 * ch - g) / 2, cw, ch, g, d };
  x.fillStyle = getComputedStyle(st).getPropertyValue("--rev-bg").trim() || "#000"; x.fillRect(0, 0, R.width, R.height);   // cute: white
  cells.forEach((c, i) => paintRev(c, i));
  R.hidden = false;
  $("revOrig").hidden = !S.beautyReady || !LOOK_ROWS.some(([k]) => S.look[k]);   // nothing to compare without a look
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
// decorate (deco mockup 3): one button per row (피부·갸름·눈); a tap steps 안 함 → 살짝 → 많이 → 안 함
function lookCycle(box, look, onPick) {
  box.replaceChildren(...LOOK_ROWS.map(([k, label]) => {
    const v = look[k], b = Object.assign(document.createElement("button"), { type: "button", className: "lcyc" });
    b.dataset.k = k; b.dataset.v = v; b.setAttribute("aria-label", `${label} ${LOOK_LV[v]}`);
    b.innerHTML = `<span><b></b> <span></span></span><span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>`;
    b.querySelector("b").textContent = label; b.querySelector("span span").textContent = LOOK_LV[v];
    b.querySelectorAll(".dots i").forEach((d, n) => d.classList.toggle("f", n <= v));
    b.onclick = () => onPick({ ...look, [k]: (v + 1) % LOOK_LV.length });
    return b;
  }));
}
// review (retouch mockup 1): a row of 피부·갸름·눈 tabs (each shows its level) + 안 함·살짝·많이 for the open tab
function lookTabs(box, look, onPick) {
  const cur = S.lookTab ||= LOOK_ROWS[0][0], label = LOOK_ROWS.find(r => r[0] === cur)[1];
  const row = cls => Object.assign(document.createElement("div"), { className: cls });
  const tabs = row("ltabs"), lv = row("llv");
  tabs.append(...LOOK_ROWS.map(([k, l]) => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "ltab" });
    b.dataset.k = k; b.setAttribute("aria-pressed", k === cur);
    b.append(l, Object.assign(document.createElement("small"), { textContent: LOOK_LV[look[k]] }));
    b.onclick = () => { if (S.lookTab !== k) { S.lookTab = k; lookTabs(box, look, onPick); } };
    return b;
  }));
  lv.append(...LOOK_LV.map((t, v) => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill", textContent: t });
    b.setAttribute("aria-pressed", look[cur] === v); b.setAttribute("aria-label", `${label} ${t}`);
    b.onclick = () => { if (look[cur] !== v) onPick({ ...look, [cur]: v }); };
    return b;
  }));
  box.replaceChildren(tabs, lv);
}
// review: my picks re-render with the new look (cached, so flipping back is instant)
function reviewLook() {
  // the review shows plain cells until the face model is up (first visit: download + GPU compile), then re-renders
  if (!S.beautyReady && !S.beautyWait) S.beautyWait = beautyMod().then(B => B.prefetchBeauty()).then(ok => {
    S.beautyReady = ok;
    if (ok) review().catch(e => console.warn("[booth] review", e));
  }).catch(e => console.warn("[booth] beauty", e));
  lookTabs($("lookMine"), S.look, l => { S.look = l; reviewLook(); review().catch(e => console.warn("[booth] review", e)); });
}
// B's overlay of A: A's cut-outs and raw photos with A's look (the same warp on both, so they still line up)
async function ghostLook() {
  S.aLook = await fetchLook("a");
  const B = await beautyMod().catch(() => null);
  if (!B?.lookOn(S.aLook)) return;
  const url = async (c, type) => URL.createObjectURL(await new Promise(res => c.toBlob(res, type)));
  const out = await Promise.all(S.ghost.map(async (u, k) => {
    const ru = S.ghostRaw?.[k];
    if (!u || !ru) return [u, ru];
    try { const [ri, ci] = await Promise.all([loadImg(ru), loadImg(u)]), r = await withLook(ri, ci, S.aLook);
      return [await url(r.cut, "image/png"), await url(r.raw, "image/jpeg")]; } catch { return [u, ru]; }
  }));
  S.ghost = out.map(x => x[0]); S.ghostRaw = out.map(x => x[1]);
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
  const role = me();
  const name = s => role ? (s === role ? "나" : "친구") : s === "a" ? "보낸 사람" : "받은 사람";
  const order = role === "b" ? ["b", "a"] : ["a", "b"];
  // who: 나 · 친구 (· 내 크기·위치 for B), one of them open in the drawer at a time
  const who = [...order.map(name), ...(mySide() ? ["내 크기·위치"] : [])];
  D.lookWho = Math.min(D.lookWho ?? 0, who.length - 1);
  $("lookWho").replaceChildren(...who.map((t, n) => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill", textContent: t });
    b.dataset.who = n; b.setAttribute("aria-pressed", D.lookWho === n);
    b.onclick = () => { if (D.lookWho !== n) { D.lookWho = n; buildLook(); } };
    return b;
  }));
  order.forEach((s, n) => {
    const box = $(n ? "lookB" : "lookA");
    box.hidden = D.lookWho !== n;
    lookCycle(box, D.look[s], async l => { D.look = { ...D.look, [s]: l }; buildLook(); await applyLooks(); changed(["frame", "filter"]); });
  });
  buildOwn();
}
// ---- my own size / place per cell (Round 6, promise 1196: "my face came out too big") ------------------------------
// D.own[me] = 4 x { x, y, scale } (fractions of the cell; compose `own`, same maths as B's move of A). Only this phone's
// own side, kept on this device per link (localStorage), never uploaded. 보정 tab: a lone finger on a cell moves me, the
// slider sizes me; the target is all 4 cells or one (touching another cell then switches to it).
const OWN_LIM = { xy: 0.6, lo: 0.5, hi: 1.5 }, OWN_IN = 0.15, OWN_Y = 0.3;   // anchor x kept in [0.15, 0.85] of the cell
const ownKey = () => `booth:own:${S.id}`;
// Round 7: only B sizes / places itself (A moved over A's own room showed A twice, operator 10-28)
const mySide = () => me() === "b" ? "b" : null;
const ownTargets = () => (D.ownCell >= 0 ? [D.ownCell] : [0, 1, 2, 3]);
function ownList() { const s = mySide(); return D.own[s] ||= [0, 1, 2, 3].map(() => ({ x: 0, y: 0, scale: 1 })); }
function ownLoad() {
  const s = mySide(), num = v => Number.isFinite(v);
  try {
    const l = JSON.parse(localStorage.getItem(ownKey()));
    if (s && Array.isArray(l) && l.length === 4 && l.every(p => num(p?.x) && num(p?.y) && num(p?.scale))) return { [s]: l };
  } catch {}
  return {};
}
let ownG = null, ownBusy = false, ownAgain = false;
function ownWhen(e) {
  if (D.tab !== "look" || !mySide() || !D.media) return false;
  const R = $("previewIn").getBoundingClientRect(), k = R.width / stripWH().W, x = (e.clientX - R.left) / k, y = (e.clientY - R.top) / k;
  const rs = rectsOf(D.frame), j = rs.findIndex(r => x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h);
  if (j < 0) return false;
  if (D.ownCell >= 0 && D.ownCell !== j) { D.ownCell = j; buildOwn(); }
  // Round 7 #9: a drag pushed me right out of the cell (B stands near an edge: +0.44 of the cell and I was gone). My
  // anchor (centre-bottom, compose layoutAnchor) stays inside the middle of each cell
  const an = rs.map((r, i) => C.layoutAnchor?.(D.media.b.cuts[i].cut, r.w / r.h) || { x: 0.5, y: 1 });
  ownG = { w: rs[j].w * k, h: rs[j].h * k, an, start: JSON.stringify(ownList()) };
  return true;
}
function ownMove(dx, dy) {
  const l = ownList(), c = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  for (const j of ownTargets()) { const ax = ownG.an[j].x;
    l[j] = { ...l[j], x: c(l[j].x + dx / ownG.w, Math.max(-OWN_LIM.xy, OWN_IN - ax), Math.min(OWN_LIM.xy, 1 - OWN_IN - ax)), y: c(l[j].y + dy / ownG.h, -OWN_Y, OWN_Y) }; }
  ownPaint();
}
function ownCancel() { D.own[mySide()] = JSON.parse(ownG.start); ownG = null; ownPaint(); }
function ownEnd() { if (ownG && JSON.stringify(ownList()) !== ownG.start) ownCommit(); ownG = null; }
async function ownPaint() {          // live redraw while dragging / sliding, one at a time
  if (ownBusy) { ownAgain = true; return; }
  ownBusy = true;
  try { do { ownAgain = false; await render(); } while (ownAgain); } finally { ownBusy = false; }
}
function ownCommit() {
  const s = mySide(), moved = ownList().some(isMoved);
  try { localStorage.setItem(ownKey(), JSON.stringify(moved ? D.own[s] : null)); } catch {}
  if (!moved) D.own[s] = null;
  // my own room behind a moved me would show me twice: that background goes away (compose falls back per cell too)
  if (moved !== !!D.ownMoved) {
    D.ownMoved = moved;
    const bg = D.bg; buildTiles();
    if (D.bg !== bg) toast("내 배경에선 내가 두 번 보여서 배경을 바꿨어");
  }
  buildOwn(); changed(["frame", "filter"]);
}
function buildOwn() {
  const s = mySide(), l = s && D.own?.[s];
  $("ownBox").hidden = !s || D.lookWho !== 2;
  if (!s) return;
  $("ownCells").replaceChildren(...[-1, 0, 1, 2, 3].map(j => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill", textContent: j < 0 ? "4칸 모두" : `${j + 1}칸` });
    b.setAttribute("aria-pressed", D.ownCell === j);
    b.onclick = () => { D.ownCell = j; buildOwn(); };
    return b;
  }));
  $("ownSize").value = Math.round((l?.[ownTargets()[0]]?.scale ?? 1) * 100);
  $("ownReset").disabled = !l?.some(isMoved);
}
$("ownSize").addEventListener("input", e => {
  const l = ownList(), v = Math.min(OWN_LIM.hi, Math.max(OWN_LIM.lo, e.target.value / 100));
  for (const j of ownTargets()) l[j] = { ...l[j], scale: v };
  ownPaint();
});
$("ownSize").addEventListener("change", () => ownCommit());

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
      // Round 7: no opts.movable (Worker default false), so the Worker refuses layout.json for new links
      const { id } = await api.create(tk, { shape: S.shape, ui: S.ui });
      S.id = id; S.token = tk;
      api.saveRole(id, "a", tk);
      history.replaceState(null, "", `?s=${id}${uiQ()}${MOCK ? "&mock=1" : ""}`);
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
      if (S.side === "b" && S.pairs) await api.put(S.id, "b", "pairs.json", new Blob([JSON.stringify(S.pairs)], { type: "application/json" }), "application/json", S.token);
      await api.put(S.id, S.side, "look.json", new Blob([JSON.stringify(S.look)], { type: "application/json" }), "application/json", S.token);   // Round 5 보정
      const r = await api.done(S.id, S.side, S.token);
      if (r.ok) break;
      if (r.missing) { r.missing.forEach(n => S.sent.delete(n)); continue; }
      if (r.error === "taken") { clearInterval(creep); return decorate("다른 친구가 먼저 찍었어요. 둘이 찍은 네컷이야"); }
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
  shareScreen(S.side === "a" ? "sent" : "done");
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
// ponytail: the test button on A's waiting screen (operator pick, ROUND6 #2); set false at launch
const TEST_B = true;
// mode: "wait" = A reopened A's own link before the friend shot, "sent" = A just uploaded, "done" = B finished and
// sends the 완성됐어 link back to A (Round 6 #3)
function shareScreen(mode) {
  S.shareMode = mode;
  show("share");
  const waiting = mode === "wait", fin = mode === "done";
  $("shareTitle").innerHTML = waiting ? "친구가 아직<br><em>안 찍었어요</em>" : fin ? "완성됐어!<br><em>친구한테 알려 주자</em>" : "다 올렸어!<br><em>친구한테 보내자</em>";
  $("shareLead").innerHTML = waiting ? "이 링크를 친구한테 보내 줘.<br>친구가 찍으면 이 링크에서 바로 열려."
    : fin ? "이 링크를 보내면 친구도 우리 네컷을 열어 봐.<br>꾸미기는 각자 폰에서 따로, 저장도 각자 해."
    : `친구가 이 링크를 열면 네 옆에 서서 찍어.<br>너는 찍은 자리 그대로 들어가.<br>둘 다 찍으면 사진이랑 영상이 열려.`;
  $("recheck").hidden = !waiting;
  $("testB").hidden = !(TEST_B && waiting);
  $("goDeco").hidden = !fin; $("decNote").hidden = fin;
  $("shareBtn").textContent = fin ? "완성됐어 링크 보내기" : "친구한테 링크 보내기";
  $("shareBtn").className = fin ? "cta alt" : "cta";
  $("linkText").textContent = shareUrl(); $("linkBox").hidden = true;
  $("s-share").querySelector(".del").hidden = !token();
  myStrip().catch(() => {});
}
const SHARE_TEXT = "내 옆자리 비워 뒀어 · 2분이면 돼 · 가입 없음";
const DONE_TEXT = "완성됐어! 우리 네컷 열어 봐 · 크롬에서 열면 꾸미고 저장할 수 있어";
const shareText = () => S.shareMode === "done" ? DONE_TEXT : SHARE_TEXT;
async function share() {
  const url = shareUrl();
  if (navigator.share) {
    try { await navigator.share({ title: "따로 같이 네컷", text: shareText(), url });
      return toast(S.shareMode === "done" ? "보냈어! 이제 꾸미러 가자" : "보냈어! 친구가 찍으면 이 링크에서 열려"); }
    catch (e) { if (e.name === "AbortError") return; }
  }
  copy();
}
async function copy() {
  const b = $("copyBtn");
  $("linkBox").hidden = false;
  try { await navigator.clipboard.writeText(`${shareText()}\n${shareUrl()}`); }
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
  pairs: null, mattes: null, ol: { color: "#ffffff", style: "solid", width: "normal" },   // ol = last outline picks
  outline: { color: "#ffffff", style: "solid", width: "normal" },   // review r3 #7: white solid normal by default (clean theme)
  stickers: [], sel: -1, pkey: null,     // Round 5 deco: stickers (strip px, ROUND5.md), selected one, key of D.photo
  own: null, ownCell: -1, ownMoved: false,   // Round 6: my own size / place per cell (app.js "my own size")
  theme: "clean", font: "clean" };       // result theme + caption font (frames.js THEMES / CAPTION_FONTS), per device
// fallbacks until compose.js exports its own lists (CONTRACT Round 3 `outline`)
const OL_COLORS = ["#ffffff", "#ff7eb6", "#ffd84d", "#8fd3ff", "#b79cff", "#2e2230"];
const OL_STYLES = [{ id: "solid", label: "실선" }, { id: "dotted", label: "점선" }, { id: "glitter", label: "반짝이" }];
const OL_WIDTHS = [{ id: "thin", label: "얇게" }, { id: "normal", label: "보통" }, { id: "thick", label: "두껍게" }];
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
async function loadClips(onStep = () => {}) {
  let n = 0;
  for (const side of ["a", "b"]) await Promise.all(D.media[side].cuts.map(async (p, k) => {
    const local = S.side === side && S.cuts.length === 4;
    const v = Object.assign(document.createElement("video"), { muted: true, playsInline: true, loop: true, preload: "auto" });
    // one clip that will not load (merged e2e 10-27: B's empty shot-5 clip) leaves that person still in its cell instead
    // of dropping the whole moving strip
    try {
      v.src = local ? URL.createObjectURL(S.cuts[k].clip) : await api.blobUrl(S.id, side, `${k + 1}-clip`);
      await new Promise((res, rej) => { v.onloadeddata = res; v.onerror = () => rej(new Error("clip")); setTimeout(() => rej(new Error("clip-timeout")), 10000); });
      p.clip = v;
    } catch (e) { console.warn("[booth] clip", side, k + 1, e); p.clip = null; }
    onStep(++n);
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
  // in an app browser the top bar's 저장하기 can't save: the sheet's "open in Chrome" takes its place
  document.querySelector("#s-done .dsave").hidden = inApp; $("dVid").hidden = false; $("dVid").textContent = inApp ? "저장하기" : "영상";
  sheet(null);
  if (inApp) { $("iosText").hidden = !env.isIOS; document.querySelector('[data-act="toChrome"]').hidden = env.isIOS; }
  $("s-done").querySelector(".del").hidden = !token();
  try {
    [C, FR, FI] = await Promise.all([load("compose", mockCompose), load("frames", mockFrames), load("filters", mockFilters)]);
    // Round 7: an old link's layout.json (B's move of A) is never read
    [D.media, D.pairs] = await Promise.all([
      Promise.all(["a", "b"].map(async s => [s, await sideMedia(s)])).then(Object.fromEntries), loadPairs()]);
    // Round 5 보정: each side's look.json (my own picks when I just shot)
    D.look ||= Object.fromEntries(await Promise.all(["a", "b"].map(async s =>
      [s, S.side === s && S.cuts.length === 4 ? { ...S.look } : await fetchLook(s)])));
    await applyLooks();
    D.own ||= { a: null, b: null, ...ownLoad() };
    D.ownMoved = !!D.own[mySide()]?.some(isMoved);
  } catch (e) {
    console.warn("[booth] done", e);
    return msg({ title: "앗, 사진을 못 불러왔어", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: () => doneScreen(note) });
  }
  buildTiles();
  await render(["frame", "filter"]);
  if (!D.revealed) reveal();
  setVid();
}
const rectsOf = f => C.cellRects?.(f, S.shape) || [0, 1, 2, 3].map(i => ({ x: 24, y: 24 + i * 398, w: 552, h: 382 }));
// the strip's size in strip px for this link's shape (stickers, preview canvas, veils)
const stripWH = () => S.shape === "tall" ? { W: 1800, H: 800 } : { W: 600, H: 1800 };
function reveal() {          // first time only: the print slides out, then the four cells appear 90 ms apart
  D.revealed = true;
  if (calm()) return;
  const p = $("preview"), { W, H } = stripWH();
  $("veils").innerHTML = rectsOf(D.frame).map((r, i) =>
    `<i style="left:${r.x / W * 100}%;top:${r.y / H * 100}%;width:${r.w / W * 100}%;height:${r.h / H * 100}%;--i:${i}"></i>`).join("");
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
  const role = me();
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
// my own room behind a moved me would show me twice
const bgOkOwn = id => !D.own?.[id]?.some(isMoved);
function buildTiles() {
  const bgs = C.BACKGROUNDS.filter(b => bgOkOwn(b.id)), frames = FR.FRAMES, filters = FI.FILTERS;
  if (D.bg && !bgs.includes(D.bg)) D.bg = null;
  // Round 6: my pick-screen background (this page, or carried in the hand-off link) is the starting one
  const pb = S.pickBg || Q.get("bg");
  D.bg ??= bgs.find(b => b.id === bgId(pb)) || bgs.find(b => b.id === "booth-pink") || bgs[0];
  D.frame ??= frames[0];
  $("bgChips").replaceChildren(...bgs.map(bg => {
    const t = tile("bg", bgLabel(bg), bg === D.bg, () => { D.bg = bg; changed(["frame", "filter"]); });
    t.querySelector(".mini").style.background = bgSwatch(bg);
    return t;
  }));
  buildFrames();
  $("filterChips").replaceChildren(...filters.map(f => {
    // a filter first picked starts at 중; switching between filters keeps the strength
    const t = tile("filter", f.label, f.id === fBase().id, () => { D.filter = fId(f.id, fBase().id === "original" ? 1 : fBase().level); filterLv(); changed(["frame", "filter"]); });
    t.dataset.id = f.id; return t;
  }));
  filterLv();
  $("caption").value = D.caption; capCount();
  buildOutline();
  buildLook();
  buildThemes();
}
// filter strength 약/중/강 (filters.js FILTER_LEVELS: the level rides in the id, "bbosasi:1"); hidden on 원본
const fBase = () => FI.parseFilter ? FI.parseFilter(D.filter) : { id: D.filter, level: 2 };
const fId = (id, lv) => FI.filterId ? FI.filterId(id, lv) : id;
function filterLv() {
  const box = $("filterLv"), { id, level } = fBase();
  box.hidden = !FI.FILTER_LEVELS || id === "original";
  box.replaceChildren(...(FI.FILTER_LEVELS || []).map((L, k) => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill", textContent: L.label });
    b.dataset.lv = k; b.setAttribute("aria-pressed", k === level);
    b.onclick = () => { if (k !== fBase().level) { D.filter = fId(fBase().id, k); filterLv(); changed(["frame", "filter"]); } };
    return b;
  }));
}
// the current theme's frames first, then the rest (each theme's own order)
function buildFrames() {
  const frames = [...FR.FRAMES].sort((x, y) => (y.theme === D.theme) - (x.theme === D.theme));
  $("frameChips").replaceChildren(...frames.map(f => {
    const t = tile("frame", f.label, f === D.frame, () => { D.frame = f; changed(["filter"]); });
    t.dataset.id = f.id; return t;
  }));
  $("frameChips").scrollLeft = 0;
}
// Result themes: one tap = the theme's first frame + outline + caption font + sticker set; each tab can change them after
function buildThemes() {
  const themes = FR.THEMES || [];
  $("themeRow").hidden = !themes.length; document.querySelector('[data-tab="theme"]').hidden = !themes.length;
  $("themeRow").replaceChildren(...themes.map(t => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "theme theme-" + t.id });
    b.setAttribute("aria-pressed", t.id === D.theme);
    b.innerHTML = `<i aria-hidden="true"></i><span></span>`; b.querySelector("span").textContent = t.label;
    b.onclick = () => t.id !== D.theme && applyTheme(t);
    return b;
  }));
  const fonts = FR.CAPTION_FONTS || [];
  $("capFonts").hidden = !fonts.length;
  $("capFonts").replaceChildren(...fonts.map(f => {
    const b = Object.assign(document.createElement("button"), { type: "button", className: "pill capfont", textContent: f.label });
    b.style.fontFamily = `"${f.family}", var(--title)`; b.style.fontWeight = f.weight;
    b.setAttribute("aria-pressed", f.id === D.font);
    b.onclick = () => { if (f.id === D.font) return; D.font = f.id; buildThemes(); changed(["frame"]); };
    return b;
  }));
}
function applyTheme(t) {
  D.theme = t.id; D.font = t.font;
  D.frame = FR.FRAMES.find(f => f.id === t.frames[0]) || D.frame;
  if (t.outline) { D.ol = { ...D.ol, ...t.outline }; D.outline = { ...D.ol }; } else D.outline = null;
  buildFrames(); buildOutline(); buildThemes();
  if ($("stkGrid").dataset.set && $("stkGrid").dataset.set !== "emoji") buildStickers(t.stickers).catch(() => {});
  changed(["frame", "filter"]);
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
  // review r3 #7: no black glitter (read as noise on faces); picking 반짝이 on black turns the colour white
  const dark = hex => parseInt(hex.slice(1), 16) < 0x444444;
  if (D.ol.style === "glitter" && dark(D.ol.color)) { D.ol.color = "#ffffff"; if (D.outline) D.outline = { ...D.ol }; }
  $("olColors").replaceChildren(none, ...colors.filter(c => !(D.ol.style === "glitter" && dark(c.color))).map(c => {
    const b = pill(D.outline?.color === c.color, () => { D.ol.color = c.color; D.outline = { ...D.ol }; pick(); }, "sw");
    b.style.setProperty("--c", c.color); b.setAttribute("aria-label", c.label || c.color); return b;
  }));
  for (const [box, list, key] of [["olStyles", styles, "style"], ["olWidths", widths, "width"]])
    $(box).replaceChildren(...list.map(s => {
      const b = pill(D.ol[key] === s.id, () => { D.ol[key] = s.id; if (D.outline) D.outline = { ...D.ol }; pick(); }, key);
      b.textContent = s.label; b.dataset.id = s.id; b.disabled = !D.outline; return b;
    }));
}
// a choice changed: redraw the photo now and the thumbnails that depend on it. Round 6 #12: the moving strip is made
// only when asked (its button); a change while it is asked for remakes it once the choosing stops (VIDEO_IDLE)
let vT;
const VIDEO_IDLE = 2000;
function changed(minis) {
  render(minis);
  later();
}
function later() {          // the saved photo soon, the moving strip after the choosing stops
  photoJob();
  clearTimeout(vT); D.vac?.abort();
  setVid();
  if (D.wantVideo || D.wantStory) vT = setTimeout(videoJob, VIDEO_IDLE);
}
const opts = (o = {}) => ({ shape: S.shape, a: D.view?.a || D.media.a, b: D.view?.b || D.media.b, bg: D.bg.id, frame: D.frame, filter: D.filter, date: DATE,
  caption: D.caption.trim(), font: D.font, pairs: D.pairs, own: D.own, outline: D.outline, fx: D.fx || { a: null, b: null }, stickers: D.stickers,
  mattes: D.mattes, ...o });
const vkey = () => [D.bg.id, D.frame.id, D.filter, D.caption.trim(), JSON.stringify(D.outline), JSON.stringify(D.look), JSON.stringify(D.stickers), JSON.stringify(D.own),
  D.mattes ? "hq" : "", D.theme, D.font].join("|");
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
  const el = $("stripImg");
  if (el.width !== cv.width || el.height !== cv.height) { el.width = cv.width; el.height = cv.height; }
  el.getContext("2d").drawImage(cv, 0, 0, cv.width, cv.height);
  showStill();
  $("making").hidden = true;
  photoJob();
  if (minis.length) setTimeout(() => minis.forEach(renderMinis), 60);   // after the photo has painted
}
// Round 6 #12: the preview is always the still (photos are the product); the moving strip is a download
function showStill() {
  $("stripImg").hidden = false; $("stripVid").hidden = true; $("stk").hidden = false;
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
    const o = opts({ stickers: null, ...(kind === "frame" ? { frame: FR.FRAMES.find(x => x.id === b.dataset.id) } : { filter: fId(b.dataset.id, fBase().level) }) });
    try {
      const m = b.querySelector(".mini");
      let cv;
      // mini width in CSS px (64; a 세로 strip's frame tile is wider, css)
      if (C.stripCanvas) cv = await C.stripCanvas(o, kind === "frame" ? { scale: (m.clientWidth || 64) * d / stripWH().W } : { scale: 64 * d / rectsOf(D.frame)[0].w, cell: 0 });
      else {          // mock compose: the old full-size path
        cv = Object.assign(document.createElement("canvas"), { width: stripWH().W, height: stripWH().H });
        cv.getContext("2d").drawImage(await createImageBitmap(await C.drawStrip(o)), 0, 0);
      }
      if (seq !== D.mseq[kind]) return;
      m.replaceChildren(cv); m.style.backgroundImage = "none";
    } catch {}
    await sleep(0);
  }
}
function setSave(on) { document.querySelector('[data-act="savePhoto"]').disabled = !on; }
// iPhone: a download lands in Files, not Photos; the share sheet offers "이미지 저장 / 비디오 저장" (ios-audit #1).
// It needs the tap's user activation: when that is gone (a long render before it) or sharing fails, it downloads.
// files: [blob, name] pairs, so the photo and the video go out in one sheet.
async function download(...files) {
  const fs = env.isIOS && navigator.canShare && files.map(([b, n]) => new File([b], n, { type: b.type }));
  if (fs && navigator.canShare({ files: fs })) {
    try { return await navigator.share({ files: fs }); }
    catch (e) { if (e?.name === "AbortError") return; }
  }
  for (const [blob, name] of files) {
    const a = Object.assign(document.createElement("a"), { href: URL.createObjectURL(blob), download: name });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 60_000);
  }
}
const fileName = (ext, tag = "") => `ttaro-gachi-${DATE.replace(/\./g, "")}${tag}.${ext}`;
const extOf = type => type.includes("mp4") ? "mp4" : "webm";

// Moving strip + story cut: made only when their button is tapped (Round 6 #12), then the button saves it.
// p: 0..1 = the render's real progress (compose counts opening the clips too), a string = the step before it
// (review r3 #5: the button sat at "0%" through the clip loading and the 보정 warm-up)
const LINES = ["영상 만드는 중", "프레임 끼우는 중", "리본 묶는 중", "반짝이 뿌리는 중"];
function setVid(which = "video", p = null) {
  for (const [id, kind, ready, idle, wait] of [["vidBtn", "video", "움직이는 네컷 저장", "움직이는 네컷 다운로드", "움직이는 네컷 기다리는 중"],
    ["storyBtn", "story", "스토리 영상 저장", "스토리용 영상", "스토리 영상 기다리는 중"]]) {
    const b = $(id), span = b.querySelector("span"), cur = D[kind]?.key === vkey();
    const making = which === kind && p != null, pct = typeof p === "number" ? Math.max(1, Math.round(p * 100)) : 0;
    b.classList.toggle("rendering", making); b.style.setProperty("--p", making ? pct : 0);
    if (making) { span.textContent = typeof p === "string" ? p : `${LINES[Math.floor(performance.now() / 2000) % LINES.length]} ${pct}%`; b.disabled = true; continue; }
    if (cur) { span.textContent = ready; b.disabled = false; continue; }
    const want = kind === "video" ? D.wantVideo : D.wantStory;
    span.textContent = want ? wait : idle; b.disabled = !!want;
  }
  hqLine();
}
// Round 5: the HQ moving cut (clipmatte.js, MODNet on ~10 fps of every clip) runs once in the background after the
// clips load; when it lands the video key changes and the strip video is made again with it (fast matte until then).
// Round 6: its progress shows under the video buttons ("n/4 다듬는 중", 1-3 min on a phone) instead of a silent wait.
let hqcP = null;
function hqClips() {
  const media = D.media;
  if (hqcP) return;
  // only with WebGPU: on WASM (iOS 18, any WebGPU failure) it is ~160 MODNet runs = minutes of full CPU and an iPhone
  // tab-kill risk (research/ios-audit.md #2); the fast matte stays
  hqcP = load("segment").then(G => G.prefetchStill().then(ok => ok && G.stats.still.delegate === "webgpu"))
    .then(gpu => gpu && (D.hqProg = 0, load("clipmatte")).then(M => M.clipMattes(["a", "b"].flatMap(s => media[s].cuts.map(c => c.clip)), p => { D.hqProg = p; hqLine(); })))
    .then(m => { if (m?.size && D.media === media) { D.mattes = m; if (D.video) D.wantVideo = true; videoJob(); } })
    .catch(e => console.warn("[booth] hq clips", e))
    .finally(() => { D.hqProg = null; hqLine(); });
}
function hqLine() {
  const on = D.hqProg != null && !D.noVideo;
  $("vidHq").hidden = !on;
  if (on) $("vidHq").textContent = `움직이는 누끼 ${Math.min(4, Math.floor(D.hqProg * 4))}/4 다듬는 중 · 끝나면 영상이 더 깔끔해져`;
}
async function videoJob() {
  if (D.vjob || D.noVideo) return;
  D.vjob = true;
  try {
    if (!D.clips) await loadClips(n => setVid(D.wantVideo ? "video" : "story", `영상 준비 중 ${n}/8`));
    hqClips();
    for (;;) {
      const key = vkey();
      const kind = D.wantVideo && D.video?.key !== key ? "video" : D.wantStory && D.story?.key !== key ? "story" : null;
      if (!kind) break;
      setVid(kind, "보정 준비 중");
      await D.fxP;                                  // Round 5: the 보정 fx for this look
      if (key !== vkey()) continue;
      setVid(kind, 0);
      D.vac = new AbortController();      // deco R5: a change stops the recording (later()); it restarts after VIDEO_IDLE
      const res = await C.recordMovingStrip(opts({ format: kind === "story" ? "story" : "strip", signal: D.vac.signal,
        onProgress: p => key === vkey() && setVid(kind, p) }));
      if (key !== vkey()) continue;
      D[kind] = { key, ...res };
      if (kind === "story") { D.wantStory = false; toast("스토리용 영상 완성! 버튼을 누르면 저장돼"); }
      // iPhone saves through the share sheet, which needs a tap: there the button saves it
      else if (D.autoVid && !env.isIOS) { D.wantVideo = false; D.autoVid = false; download([res.blob, fileName(extOf(res.type))]); toast("움직이는 네컷도 저장했어!"); }
      else { D.wantVideo = false; toast("움직이는 네컷 완성! 버튼을 누르면 저장돼"); }
    }
  } catch (e) {
    if (e?.name === "AbortError") return;
    console.warn("[booth] video", e);
    D.noVideo = true;
    $("saveBtns").querySelector(".vidrow").hidden = true; $("vidNote").hidden = true; $("saveBoth").hidden = true; $("dVid").hidden = true; sheet(null);
    toast("이 폰에서는 영상 저장이 안 돼요. 사진으로 저장해 줘", 3500);
  } finally { D.vjob = false; setVid(); }
}

// ---- actions ------------------------------------------------------------------------------------------------------
// decorate's two sheets over the dock: "save" (영상) and "more" (⋯); null closes both
function sheet(id) {
  for (const x of $$("#s-done .dsheet")) x.hidden = x.dataset.sheetid !== id;
  $("dScrim").hidden = !id;
}
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
// set = a theme id (drawn stickers of that theme; frames.js THEMES[].stickers) or "emoji"
async function buildStickers(set = $("stkGrid").dataset.set || FR.THEMES?.find(t => t.id === D.theme)?.stickers || "y2k") {
  ST ||= await import("./stickers.js");
  if ($("stkGrid").dataset.set === set) return;
  $("stkGrid").dataset.set = set;
  $$("#stkSets .pill").forEach(p => p.setAttribute("aria-pressed", p.dataset.set === set));
  const list = set === "emoji" ? ST.EMOJI.map(e => ({ id: e, label: e }))
    : ST.DRAWN.filter(d => (d.set || "y2k") === set).map(d => ({ id: d.id, label: d.label }));
  $("stkGrid").replaceChildren(...list.map(s => {
    const b = Object.assign(document.createElement("button"), { type: "button" });
    b.setAttribute("aria-label", `${s.label} 붙이기`);
    if (set === "emoji") b.innerHTML = `<span class="emo" aria-hidden="true">${s.id}</span>`;
    else { const i = new Image(); i.alt = ""; b.append(i); ST.stickerUrl("svg", s.id).then(u => i.src = u).catch(() => {}); }
    b.onclick = () => addSticker(set === "emoji" ? "emoji" : "svg", s.id);
    return b;
  }));
}
for (const p of $$("#stkSets .pill")) p.onclick = () => buildStickers(p.dataset.set);
function addSticker(kind, id) {
  if (D.stickers.length >= STK_MAX) return toast(`스티커는 ${STK_MAX}개까지 붙일 수 있어`);
  const n = D.stickers.length, j = ((n % 5) - 2) * 26;          // a little apart, so taps in a row do not stack exactly
  const { W, H } = stripWH();
  D.stickers.push({ kind, id, x: W / 2 + j, y: H / 2 + j, s: kind === "emoji" ? 140 : 170, r: 0 });
  D.sel = D.stickers.length - 1;
  paintStk(); later();
}
function placeStk(el, s) {
  const { W, H } = stripWH();
  Object.assign(el.style, { left: `${s.x / W * 100}%`, top: `${s.y / H * 100}%`, width: `${s.s / W * 100}%` });
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
// Round 6 #11 rule: a touch that lands on a sticker (or its handle) is the sticker's, and a second finger then sizes and
// turns it; a touch anywhere else is the view's (DZ): two fingers zoom / pan, a lone tap lets go of the selection.
let stkOwns = () => false;
function bindStk() {
  const L = $("stk"), pts = new Map();
  let g = null, moved = false;
  stkOwns = id => pts.has(id);
  const cl = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const k = () => stripWH().W / L.getBoundingClientRect().width;   // strip px per screen px (the view may be zoomed)
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
    if (D.tab !== "sticker" || e.pointerType === "mouse" && e.button || DZ.busy()) return;
    const h = pts.size ? null : hit(e.clientX, e.clientY);
    if (!pts.size && h == null) return;          // off every sticker: the view's touch
    if (h === "x") {           // delete on press: touchmove is cancelled here, so no click is relied on
      e.preventDefault(); D.stickers.splice(D.sel, 1); D.sel = -1; paintStk(); later(); return;
    }
    if (!pts.size && h !== "h" && h !== D.sel) { D.sel = h; paintStk(); }
    L.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    begin(pts.size === 1 ? h === "h" : false);
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
      s.x = cl(s0.x + (m1.x - m0.x) * K, 0, stripWH().W); s.y = cl(s0.y + (m1.y - m0.y) * K, 0, stripWH().H);
      s.s = cl(s0.s * (d0 ? d1 / d0 : 1), STK_S.lo, STK_S.hi); s.r = s0.r + a1 - a0;
    } else {
      s.x = cl(s0.x + (now[0].x - p0[0].x) * K, 0, stripWH().W); s.y = cl(s0.y + (now[0].y - p0[0].y) * K, 0, stripWH().H);
    }
    moved = true;
    const el = L.querySelector(`.stk-i[data-i="${D.sel}"]`); if (el) placeStk(el, s);
  });
  const end = e => {
    if (!pts.delete(e.pointerId)) return;
    if (pts.size) return begin(false);
    g = null;
    if (moved) { moved = false; later(); }
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

// ---- inline pinch zoom (Round 6 #11): on the photo itself, the controls under it never move ----------------------
// Two fingers zoom 1x-5x about their midpoint and pan; one finger pans once zoomed (unless `one` takes it); a double tap
// resets (or 2x at the tap). The view is clamped to its box; the shift is kept as a fraction of the box, so a resize
// (the sticker tab's taller preview) keeps the same view. `skip(e)`: a touch something else owns (a sticker).
// `one`: { when(e), move(dx, dy), cancel(), end() } takes a lone finger instead (own person drag); a second finger
// cancels it and becomes a view pinch. Pointer events + cancelled touches (README in-app fixes): CDP, real touch, mouse.
function zoomer({ box, host = box, inner, chip, skip = () => false, one = null, onTap = () => {}, onEnd = () => {}, stop = false }) {
  const z = { s: 1, fx: 0, fy: 0 }, pts = new Map();
  let g = null, down = null, lastTap = { t: 0, x: 0, y: 0 }, mode = null;
  const cl = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const apply = () => {
    const W = box.clientWidth, H = box.clientHeight;
    if (z.s < 1.02) Object.assign(z, { s: 1, fx: 0, fy: 0 });
    z.fx = cl(z.fx, 1 - z.s, 0); z.fy = cl(z.fy, 1 - z.s, 0);
    inner.style.transform = z.s > 1 ? `translate(${z.fx * W}px, ${z.fy * H}px) scale(${z.s})` : "";
    inner.style.setProperty("--zs", z.s);
    chip.hidden = z.s <= 1; chip.textContent = `${z.s.toFixed(1)}×`;
  };
  const rel = p => { const r = box.getBoundingClientRect(); return { x: p.x - r.left, y: p.y - r.top }; };
  const mid = () => { const p = [...pts.values()].map(rel); return p.length > 1
    ? { x: (p[0].x + p[1].x) / 2, y: (p[0].y + p[1].y) / 2, d: Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y) } : { ...p[0], d: 0 }; };
  const now = () => ({ s: z.s, tx: z.fx * box.clientWidth, ty: z.fy * box.clientHeight });
  const begin = () => { g = pts.size ? { ...now(), m: mid() } : null; };
  // zoom to s keeping the content point that was under box point m0 (view g0) under box point m1
  const to = (s, m0, m1, g0 = now()) => {
    const cx = (m0.x - g0.tx) / g0.s, cy = (m0.y - g0.ty) / g0.s;
    z.s = cl(s, 1, 5); z.fx = (m1.x - cx * z.s) / box.clientWidth; z.fy = (m1.y - cy * z.s) / box.clientHeight;
  };
  host.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse" && e.button || pts.size >= 2 || skip(e)) return;
    host.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pts.size === 1) { down = { x: e.clientX, y: e.clientY, t: performance.now() }; mode = one?.when(e) ? "one" : "view"; }
    else { down = null; if (mode === "one") one.cancel(); mode = "view"; }
    begin();
  });
  host.addEventListener("pointermove", e => {
    if (!g || !pts.has(e.pointerId)) return;
    const p0 = pts.get(e.pointerId);
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (mode === "one") return one.move(e.clientX - p0.x, e.clientY - p0.y);
    if (pts.size === 1 && g.s <= 1) return;
    const m = mid();
    to(pts.size > 1 && g.m.d ? g.s * m.d / g.m.d : g.s, g.m, m, g);
    apply();
  });
  const end = e => {
    const at = pts.get(e.pointerId);
    if (!pts.delete(e.pointerId)) return;
    if (pts.size) return begin();
    g = null;
    const t = performance.now(), quick = down && t - down.t < 300 && Math.hypot(at.x - down.x, at.y - down.y) < 12;
    if (mode === "one") one.end();
    if (quick && t - lastTap.t < 350 && Math.hypot(at.x - lastTap.x, at.y - lastTap.y) < 30) {     // double tap
      if (z.s > 1) Object.assign(z, { s: 1, fx: 0, fy: 0 }); else { const p = rel(at); to(2, p, p); }
      lastTap.t = 0;
    } else if (quick) { lastTap = { t, x: at.x, y: at.y }; onTap(at); }
    mode = null; apply(); onEnd(z);
  };
  host.addEventListener("pointerup", end); host.addEventListener("pointercancel", end);
  for (const t of ["touchstart", "touchmove", "touchend"])
    host.addEventListener(t, e => {
      if (stop) e.stopPropagation();          // the shoot stage's camera pinch / tap-to-focus stay out of it
      if (t !== "touchend" && !e.target.closest("button")) e.preventDefault();
      if (scrollY < 1) scrollTo(0, 1);
    }, { passive: false });
  host.addEventListener("wheel", e => {         // a computer: the wheel zooms about the pointer
    if (e.defaultPrevented) return;            // the sticker layer's wheel resized a sticker
    e.preventDefault(); const p = rel({ x: e.clientX, y: e.clientY });
    to(z.s * Math.exp(-e.deltaY / 400), p, p); apply(); clearTimeout(host.zT); host.zT = setTimeout(() => onEnd(z), 300);
  }, { passive: false });
  new ResizeObserver(apply).observe(box);
  return { z, apply, busy: () => pts.size > 0, reset: () => { Object.assign(z, { s: 1, fx: 0, fy: 0 }); apply(); } };
}
// the decorate preview: stickers own their touches on the sticker tab; the 보정 tab's lone finger moves my own person
const DZ = zoomer({ box: $("preview"), inner: $("previewIn"), chip: $("zoomChip"),
  skip: e => D.tab === "sticker" && stkOwns(e.pointerId),
  one: { when: e => ownWhen(e), move: (dx, dy) => ownMove(dx, dy), cancel: () => ownCancel(), end: () => ownEnd() },
  onTap: () => { if (D.tab === "sticker" && D.sel >= 0) { D.sel = -1; paintStk(); } },
  onEnd: z => { $("preview").querySelector(".zoomtag").style.display = z.s > 1 ? "none" : ""; } });
// the retouch review (A and B before upload): sharper cells once zoomed in
const RZ = zoomer({ box: $("stage"), host: $("review"), inner: $("review"), chip: $("revZoom"), stop: true,
  onEnd: z => { if (z.s > 1.5 && S.revQ !== 2) { S.revQ = 2; review().catch(e => console.warn("[booth] review", e)); } } });
// 원본 보기: held = the four without 보정; the pinch zoom under it never sees the press
{ const b = $("revOrig"), hold = on => {
    if (S.revOrig === on) return;
    S.revOrig = on; b.setAttribute("aria-pressed", on); review().catch(e => console.warn("[booth] review", e));
  };
  b.addEventListener("pointerdown", e => { e.stopPropagation(); e.preventDefault(); b.setPointerCapture?.(e.pointerId); hold(true); });
  for (const t of ["pointerup", "pointercancel", "lostpointercapture"]) b.addEventListener(t, e => { e.stopPropagation(); hold(false); });
  b.addEventListener("contextmenu", e => e.preventDefault()); }

const ACT = {
  pickUi: b => { setUi(b.dataset.ui); show("landA"); },   // screen 0: A's theme, then A's cover
  startA: () => modeScreen(),
  toTheme: () => show("theme"),            // Round 7 #2: "‹ 테마" on A's cover and the mode pick
  modeGo: () => startShoot("a"),
  startB: () => startShoot("b"),
  mine: () => shareScreen("wait"),
  testB: () => { landB(); prefetchHQ(); },          // TEST_B: A shoots the friend's side on this phone
  goDeco: () => decorate(),
  hoCopy: async () => {
    $("hoBox").hidden = false;
    try { await navigator.clipboard.writeText(decoUrl()); toast("복사했어! 크롬이나 Safari 주소창에 붙여 넣어 줘"); }
    catch { toast("아래 링크를 길게 눌러 복사해 줘"); }
  },
  recheck: () => route(),
  shoot: () => runShoot(),
  pickDone, reshoot,
  sound: () => setSound(!soundOn),
  // Round 6 #15: mirror and front/back also during the countdown and between shots
  mirror: () => {
    if (!S.cam) return;
    S.cam.camState.mirror = !S.cam.camState.mirror;
    syncMirror();
  },
  flip: () => {
    if (!S.cam || S.flipP) return;
    const busy = S.busy, v = $("video"), front = S.cam.camState.front;
    if (!busy) { $("camwait").hidden = false; $("camwait").textContent = "카메라 바꾸는 중…"; }
    // mid-run the countdown goes on; the shutter waits for this (capture hold) and a failed switch goes back to the
    // camera it came from, so the shots keep running
    S.flipP = S.cam.flipCam(v)
      .catch(e => busy ? S.cam.startCam(v, front ? "user" : "environment") : Promise.reject(e))
      .then(() => { fitStage(); syncMirror(); }, e => camError(e))
      .finally(() => { S.flipP = null; $("camwait").hidden = true; });
  },
  upload: askUpload, retryUpload: upload, hqWait, hqNow: () => upload(), share, copy,
  hideHqNote: () => { $("hqNote").hidden = true; S.hqNoteOff = true; },
  savePhoto: async () => {
    if (D.pkey !== vkey()) { const key = vkey(); D.photo = await C.drawStrip(opts()); D.pkey = key; }
    download([D.photo, fileName("jpg")]);
  },
  // Round 6 (review r3 #6): the photo now, the moving strip as soon as it is made (one tap for both)
  ownReset: () => { if (mySide()) { D.own[mySide()] = null; ownCommit(); } },
  saveBoth: async () => {
    if (D.pkey !== vkey()) { const key = vkey(); D.photo = await C.drawStrip(opts()); D.pkey = key; }
    const photo = [D.photo, fileName("jpg")];
    if (D.video?.key === vkey()) return download(photo, [D.video.blob, fileName(extOf(D.video.type))]);
    download(photo);
    D.wantVideo = true; D.autoVid = true; setVid(); videoJob();
    toast(env.isIOS ? "사진 저장했어! 영상은 다 만들어지면 영상 버튼으로 저장해 줘" : "사진 저장했어! 영상은 다 만들어지면 바로 저장돼", 3200);
  },
  saveVideo: () => {
    if (D.video?.key === vkey()) return download([D.video.blob, fileName(extOf(D.video.type))]);
    D.wantVideo = true; setVid(); videoJob();
  },
  saveStory: () => {
    if (D.story?.key === vkey()) return download([D.story.blob, fileName(extOf(D.story.type), "-story")]);
    D.wantStory = true; setVid(); videoJob();
  },
  toChrome: () => openOut(decoUrl()),
  hideHint: () => { $("hintChip").hidden = true; S.hintOff = S.side; },
  again: fresh,
  dsheet: b => sheet(b.dataset.sheet),
  dsheetClose: () => sheet(null),
};
document.addEventListener("click", e => {
  const b = e.target.closest("[data-act]");
  if (b?.dataset.act === "del" || b?.dataset.act === "delYes" || b?.dataset.act === "delNo")
    return del(b.closest(".del"), b.dataset.act === "del" ? null : b.dataset.act === "delYes");
  if (b && ACT[b.dataset.act]) ACT[b.dataset.act](b);
});

// ---- boot ---------------------------------------------------------------------------------------------------------
try { env = await import("./env.js"); } catch {}
window.__booth = { S, D, MOCK };   // dev handle for the headless checks
setShape(S.shape);
route();
