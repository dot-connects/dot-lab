// App shell: screens + role routing (PLAN Task 6 role table). Capture/compose modules load on demand,
// so the first screen needs only this file + api.js, and MediaPipe loads after the first tap.
import * as api from "./api.js";
import { mockCapture, mockCompose, mockFrames, mockFilters } from "./ui.js";

const $ = id => document.getElementById(id);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const Q = new URLSearchParams(location.search);
const MOCK = Q.has("mock");                         // dev: no MediaPipe, placeholder cut-outs
const POSES = ["둘 다 브이!", "반쪽 하트", "서로 쳐다보기", "마음대로!"];
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
  cuts: [], sent: new Set(), ghost: [],          // local Cuts, uploaded names, A's cut-out URLs (B's overlay)
  cam: null, cap: null, bound: false, busy: false,
};
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
  if (!S.id) return show("landA");
  let st;
  try { st = await api.status(S.id); }
  catch { return msg({ title: "앗, 연결이 안 돼", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: route }); }
  if (st.error) return expired(st.error);
  const role = api.loadRole(S.id)?.role;
  if (!st.a.done) {
    if (role === "a") return msg({ title: "올리다 멈췄어", text: "찍은 사진이 이 화면에 남아 있지 않아. 새로 찍어 줘.", btn: "다시 찍기", onBtn: fresh });
    return msg({ title: "아직 친구가 올리는 중이에요", text: "조금 뒤에 이 링크를 다시 열어 줘.", btn: "다시 확인", onBtn: route });
  }
  if (!st.b.done) return role === "a" ? shareScreen(true) : landB();
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

async function landB() {
  show("landB");
  // A's cut-outs for the peek and for B's overlay, fetched while B reads the screen
  S.ghostP = Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-cut.png`).catch(() => null)))
    .then(g => S.ghost = g);
  const g = await S.ghostP;
  if (g[0]) $("peekImg").src = g[0];
}

// ---- shoot --------------------------------------------------------------------------------------------------------
async function startShoot(side) {
  S.side = side; S.cuts = []; S.sent.clear();
  show("shoot");
  $("s-shoot").classList.remove("shooting-done");
  $("controls").hidden = false; $("shootDone").hidden = true; $("shootHint").hidden = false; $("review").hidden = true;
  $("spot").hidden = side !== "a";
  for (const t of $$(".th")) { t.className = "th"; t.style.backgroundImage = ""; t.disabled = true; }
  setSound(soundOn);
  setCut(0);
  $("camwait").hidden = false; $("camwait").textContent = "카메라 켜는 중…";
  try { if (soundOn) ctxA(); } catch {}
  const video = $("video");
  try {
    S.cam ||= await load("camera");
    const segP = MOCK ? null : load("segment").then(m => m.loadSegmenter());
    segP?.catch(() => {});
    await S.cam.startCam(video);
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
  if (side === "b") { await S.ghostP; setCut(0, false); loadGhostClips(); }
}
// Lay the camera out so the strip-cell crop spans the stage width (portrait phones show the whole frame around it).
function fitStage() {
  const v = $("video"), st = $("stage");
  if (!v.videoWidth || !S.cam) return;
  const W = st.clientWidth, H = st.clientHeight, fr = S.cam.frameRect(v);
  const cw = fr.sw / fr.sh > CELL ? fr.sh * CELL : fr.sw, ch = cw / CELL;
  const k = Math.min(W / cw, (H * 0.92) / ch);
  const left = W / 2 - (fr.sx + fr.sw / 2) * k, top = H / 2 - (fr.sy + fr.sh / 2) * k;
  const px = (el, x, y, w, h) => Object.assign(el.style, { left: x + "px", top: y + "px", width: w + "px", height: h + "px" });
  px(v, left, top, v.videoWidth * k, v.videoHeight * k);
  px($("frame"), left + fr.sx * k, top + fr.sy * k, fr.sw * k, fr.sh * k);
  px($("crop"), (fr.sw - cw) / 2 * k, (fr.sh - ch) / 2 * k, cw * k, ch * k);
  const c = $("crop").getBoundingClientRect(), s = st.getBoundingClientRect();
  S.cropBox = { x: c.left - s.left, y: c.top - s.top, w: c.width, h: c.height };
  px($("snap"), S.cropBox.x, S.cropBox.y, S.cropBox.w, S.cropBox.h);
}
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
    S.ca = ca;
    if (S.cutIdx != null) setCut(S.cutIdx, false);
    ghostLoop();
  } catch (e) { console.warn("[booth] A clip overlay unavailable, using the still", e); }
}
function ghostLoop(tick) {
  if (S.looping && !tick) return;
  S.looping = true;
  if (document.body.dataset.screen !== "shoot") { S.looping = false; return S.ghostClips?.forEach(v => v.pause()); }
  const v = S.ghostClips?.[S.cutIdx];
  if (v && !$("ghostClip").hidden) { if (v.paused) v.play().catch(() => {}); if (v.readyState >= 2) S.ca.draw(v); }
  requestAnimationFrame(() => ghostLoop(true));
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

function setCut(i, pop = true) {
  S.cutIdx = i;
  $("step").textContent = `${i + 1} / 4`;
  const p = $("pose"); p.textContent = POSES[i];
  if (pop) restart(p, "pop");
  const g = $("ghost"), src = S.side === "b" && S.ghost[i], clip = S.side === "b" && S.ca && S.ghostClips?.[i];
  S.ghostClips?.forEach((v, k) => k !== i && v.pause());
  $("ghostClip").hidden = !clip;
  g.hidden = !src || !!clip; if (src) g.src = src;
  $$(".th").forEach((t, k) => t.classList.toggle("now", k === i));
}
function onTick(n) {
  const c = $("count");
  c.textContent = n; restart(c, "go");
  pip(n === 1 ? 1320 : 880, n === 1 ? 0.12 : 0.04);
  if (n === 2) $("rec").hidden = false;
  if (n === 1) setTimeout(flash, 1000);
}
async function shootIdx(i) {
  setCut(i);
  const cut = await S.cap.shootOne(i, { video: $("video"), onTick });
  $("count").textContent = ""; $("rec").hidden = true;
  S.cuts[i] = cut;
  // "짠": the captured photo pops over the crop for 350 ms with a little star burst, then lands in its thumbnail
  const snap = $("snap"), url = URL.createObjectURL(cut.raw);
  snap.src = url; snap.hidden = false; restart(snap, "pop");
  if (S.cropBox) sparkle($("stage"), { n: 3, x: (S.cropBox.x + S.cropBox.w * 0.82) / $("stage").clientWidth, y: (S.cropBox.y + S.cropBox.h * 0.2) / $("stage").clientHeight });
  await sleep(350);
  snap.hidden = true;
  const t = $$(".th")[i];
  t.classList.add("has"); t.style.backgroundImage = `url(${url})`; restart(t, "land");
}
async function runShoot(only) {
  if (S.busy || !S.cap) return;
  S.busy = true; document.body.classList.add("busy");
  $("shootHint").hidden = true; $("shootDone").hidden = true; $("review").hidden = true;
  $("s-shoot").classList.remove("shooting-done");
  $$(".th").forEach(t => t.disabled = true);
  try {
    for (const i of only != null ? [only] : [0, 1, 2, 3].filter(k => !S.cuts[k])) {
      await shootIdx(i);
      if (only == null && i < 3) await sleep(250);
    }
  } catch (e) {
    console.warn("[booth] shoot", e);
    toast("앗, 찍다가 멈췄어. 셔터를 다시 눌러 줘");
  } finally {
    S.busy = false; document.body.classList.remove("busy");
    $("count").textContent = ""; $("rec").hidden = true;
  }
  const all = [0, 1, 2, 3].every(k => S.cuts[k]);
  $$(".th").forEach((t, k) => { t.disabled = !S.cuts[k] || !all; t.classList.remove("now"); });
  if (!all) return setCut([0, 1, 2, 3].find(k => !S.cuts[k]));
  $("s-shoot").classList.add("shooting-done");
  $("controls").hidden = true; $("shootDone").hidden = false;
  $("step").textContent = "4 / 4"; $("pose").textContent = "다 찍었어! 맘에 들어?"; restart($("pose"), "pop");
  $("ghost").hidden = true; $("ghostClip").hidden = true; S.cutIdx = null;
  review().catch(e => console.warn("[booth] review", e));
}
// "맘에 들어?": B sees the four cells as the strip will (A + B on the default booth), A sees its own crops.
async function review() {
  const cells = await Promise.all([0, 1, 2, 3].map(async i => {
    const c = document.createElement("canvas"); c.width = 552; c.height = 382;
    const x = c.getContext("2d"), raw = await loadImg(URL.createObjectURL(S.cuts[i].raw));
    if (S.side === "b" && S.ghost[i]) {
      const C = await load("compose", mockCompose);
      const aImg = await loadImg(S.ghost[i]), bCut = await loadImg(URL.createObjectURL(S.cuts[i].cut));
      const a = { cuts: [] }, b = { cuts: [] };
      a.cuts[i] = { raw: aImg, cut: aImg, clip: null }; b.cuts[i] = { raw, cut: bCut, clip: null };
      if (C.drawCell) C.drawCell(x, { x: 0, y: 0, w: 552, h: 382 }, i, { a, b, bg: "booth-pink" });
      else { x.drawImage(raw, 0, 0, 552, 382); x.drawImage(aImg, 0, 0, 552, 382); }
    } else {
      const sw = raw.naturalWidth, sh = sw / CELL;
      x.drawImage(raw, 0, (raw.naturalHeight - sh) / 2, sw, sh, 0, 0, 552, 382);
    }
    return c;
  }));
  if (!$("s-shoot").classList.contains("shooting-done")) return;   // a retake started meanwhile
  cells.forEach((c, i) => { $$(".th")[i].style.backgroundImage = `url(${c.toDataURL("image/jpeg", 0.8)})`; });
  const R = $("review"), st = $("stage"), d = devicePixelRatio || 1;
  R.width = st.clientWidth * d; R.height = st.clientHeight * d;
  const x = R.getContext("2d"), g = 8 * d, cw = Math.min((R.width - 3 * g) / 2, ((R.height - 3 * g) / 2) * CELL), ch = cw / CELL;
  const ox = (R.width - 2 * cw - g) / 2, oy = (R.height - 2 * ch - g) / 2;
  x.fillStyle = "#2e2230"; x.fillRect(0, 0, R.width, R.height);
  cells.forEach((c, i) => {
    x.save(); x.beginPath(); x.roundRect(ox + (i % 2) * (cw + g), oy + (i >> 1) * (ch + g), cw, ch, 10 * d); x.clip();
    x.drawImage(c, ox + (i % 2) * (cw + g), oy + (i >> 1) * (ch + g), cw, ch); x.restore();
  });
  R.hidden = false;
}

// ---- upload -------------------------------------------------------------------------------------------------------
function fileOf(name) {
  const c = S.cuts[+name[0] - 1];
  return name.endsWith(".jpg") ? [c.raw, "image/jpeg"] : name.endsWith(".png") ? [c.cut, "image/png"] : [c.clip, c.clipType];
}
async function upload() {
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
      const { id } = await api.create(tk);
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
// A's own four crops as a mini strip with the dashed friend spot on the right of each cell
async function myStrip() {
  const urls = S.cuts.length === 4 ? S.cuts.map(c => URL.createObjectURL(c.raw))
    : await Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-raw.jpg`).catch(() => null)));
  const strip = Object.assign(document.createElement("div"), { className: "ministrip real" });
  strip.setAttribute("aria-hidden", "true");
  strip.innerHTML = urls.map(u => `<div class="mc"><img alt="" src="${u || ""}"><span class="mspot"></span></div>`).join("")
    + `<div class="mfoot">따로 같이 네컷</div>`;
  const star = '<svg class="deco s1"><use href="#star"/></svg><svg class="deco s2"><use href="#heart"/></svg>';
  $("myStrip").innerHTML = star; $("myStrip").prepend(strip);
}
function shareScreen(waiting) {
  show("share");
  $("shareTitle").innerHTML = waiting ? "친구가 아직<br><em>안 찍었어요</em>" : "다 올렸어!<br><em>친구한테 보내자</em>";
  $("shareLead").innerHTML = waiting ? "이 링크를 친구한테 보내 줘.<br>친구가 찍으면 이 링크에서 바로 열려."
    : "친구가 이 링크를 열면 네 옆 빈자리에 서서 찍어.<br>둘 다 찍으면 사진이랑 영상이 열려.";
  $("recheck").hidden = !waiting;
  $("linkText").textContent = shareUrl(); $("linkBox").hidden = true;
  $("s-share").querySelector(".del").hidden = !token();
  myStrip().catch(() => {});
}
const SHARE_TEXT = "내 옆자리 비워 뒀어 · 30초면 돼 · 가입 없음";
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
  wantStory: false, seq: 0, mseq: {}, clips: false, vjob: false, revealed: false, tab: "frame" };
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
    D.media = Object.fromEntries(await Promise.all(["a", "b"].map(async s => [s, await sideMedia(s)])));
  } catch (e) {
    console.warn("[booth] done", e);
    return msg({ title: "앗, 사진을 못 불러왔어", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: () => doneScreen(note) });
  }
  buildTiles();
  await render(["frame", "filter"]);
  if (!D.revealed) reveal();
  if (!inApp) videoJob();
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
function buildTiles() {
  const bgs = C.BACKGROUNDS, frames = FR.FRAMES, filters = FI.FILTERS;
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
}
// a choice changed: redraw the photo now, the thumbnails that depend on it, and the moving strip a moment later
let vT;
function changed(minis) {
  render(minis);
  clearTimeout(vT);
  if (!env.inApp) { setVid(); vT = setTimeout(videoJob, 700); }
}
const opts = (o = {}) => ({ a: D.media.a, b: D.media.b, bg: D.bg.id, frame: D.frame, filter: D.filter, date: DATE,
  caption: D.caption.trim(), ...o });
const vkey = () => [D.bg.id, D.frame.id, D.filter, D.caption.trim()].join("|");
async function render(minis = []) {
  const seq = ++D.seq;
  $("making").hidden = false; $("makingText").textContent = "네컷 만드는 중";
  setSave(false);
  let blob;
  try { blob = await C.drawStrip(opts()); }
  catch (e) { console.warn("[booth] drawStrip", e); if (seq === D.seq) $("makingText").textContent = "앗, 네컷을 못 만들었어"; return; }
  if (seq !== D.seq) return;
  D.photo = blob;
  const img = $("stripImg");
  if (img.src) URL.revokeObjectURL(img.src);
  img.src = URL.createObjectURL(blob);
  await img.decode().catch(() => {});
  if (seq !== D.seq) return;
  showStill();
  $("making").hidden = true;
  setSave(true);
  for (const k of minis) renderMinis(k);
}
function showStill() {
  if (D.video?.key === vkey()) return showVideo();
  $("stripImg").hidden = false; $("stripVid").hidden = true; $("stripVid").pause();
}
function showVideo() {
  const v = $("stripVid");
  if (v.dataset.key !== D.video.key) { if (v.src) URL.revokeObjectURL(v.src); v.src = URL.createObjectURL(D.video.blob); v.dataset.key = D.video.key; }
  v.hidden = false; $("stripImg").hidden = true; v.play().catch(() => {});
}
async function renderMinis(kind) {          // tile previews, one at a time after the main strip
  const seq = D.mseq[kind] = (D.mseq[kind] || 0) + 1;
  const box = kind === "frame" ? $("frameChips") : $("filterChips");
  for (const b of box.querySelectorAll(".tile")) {
    if (seq !== D.mseq[kind]) return;
    const o = kind === "frame" ? { frame: FR.FRAMES.find(x => x.id === b.dataset.id) } : { filter: b.dataset.id };
    try {
      const blob = await C.drawStrip(opts(o));
      const m = b.querySelector(".mini"), old = m.dataset.url;
      m.dataset.url = URL.createObjectURL(blob); m.style.backgroundImage = `url(${m.dataset.url})`;
      if (kind === "filter") {          // show cell 1 only
        const r = rectsOf(D.frame)[0], s = 64 / r.w;
        m.style.backgroundSize = `${600 * s}px auto`; m.style.backgroundPosition = `${-r.x * s}px ${-r.y * s}px`;
      }
      if (old) URL.revokeObjectURL(old);
    } catch {}
    await sleep(30);
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
async function videoJob() {
  if (D.vjob || D.noVideo) return;
  D.vjob = true;
  try {
    if (!D.clips) await loadClips();
    for (;;) {
      const key = vkey();
      const kind = D.video?.key !== key ? "video" : D.wantStory && D.story?.key !== key ? "story" : null;
      if (!kind) break;
      setVid(kind, 0);
      const res = await C.recordMovingStrip(opts({ format: kind === "story" ? "story" : "strip",
        onProgress: p => key === vkey() && setVid(kind, p) }));
      if (key !== vkey()) continue;
      D[kind] = { key, ...res };
      if (kind === "story") D.wantStory = false;
      if (kind === "video" && !$("stripImg").hidden) showVideo();
      if (kind === "story") toast("스토리용 영상 완성! 버튼을 누르면 저장돼");
    }
  } catch (e) {
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
};

const ACT = {
  startA: () => startShoot("a"),
  startB: () => startShoot("b"),
  mine: () => shareScreen(true),
  recheck: () => route(),
  shoot: () => runShoot(),
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
  upload, retryUpload: upload, share, copy,
  savePhoto: () => D.photo && download(D.photo, fileName("jpg")),
  saveVideo: () => D.video?.key === vkey() && download(D.video.blob, fileName(extOf(D.video.type))),
  saveStory: () => {
    if (D.story?.key === vkey()) return download(D.story.blob, fileName(extOf(D.story.type), "-story"));
    D.wantStory = true; setVid(); videoJob();
  },
  toChrome: () => location.href = env.chromeUrl(shareUrl()),
  again: fresh,
};
document.addEventListener("click", e => {
  const b = e.target.closest("[data-act]");
  if (b?.dataset.act === "del" || b?.dataset.act === "delYes" || b?.dataset.act === "delNo")
    return del(b.closest(".del"), b.dataset.act === "del" ? null : b.dataset.act === "delYes");
  if (b && ACT[b.dataset.act]) ACT[b.dataset.act]();
  const th = e.target.closest(".th");
  if (th && !th.disabled && !S.busy) { $("controls").hidden = false; runShoot(+th.dataset.i); }
});

// ---- boot ---------------------------------------------------------------------------------------------------------
try { env = await import("./env.js"); } catch {}
window.__booth = { S, D, MOCK };   // dev handle for the headless checks
route();
