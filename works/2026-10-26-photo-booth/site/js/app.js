// App shell: screens + role routing (PLAN Task 6 role table). Capture/compose modules load on demand,
// so the first screen needs only this file + api.js, and MediaPipe loads after the first tap.
import * as api from "./api.js";
import { mockCapture, mockCompose, mockFrames, mockFilters } from "./ui.js";

const $ = id => document.getElementById(id);
const Q = new URLSearchParams(location.search);
const MOCK = Q.has("mock");                         // dev: no MediaPipe, placeholder cut-outs
const POSES = ["둘 다 브이!", "반쪽 하트", "서로 쳐다보기", "마음대로!"];
const NAMES = [1, 2, 3, 4].flatMap(c => [`${c}-raw.jpg`, `${c}-cut.png`, `${c}-clip`]);
const sleep = ms => new Promise(r => setTimeout(r, ms));
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

// ---- screens ------------------------------------------------------------------------------------------------------
function show(name) {
  for (const s of document.querySelectorAll(".screen")) s.hidden = s.id !== "s-" + name;
  document.body.dataset.screen = name;
  if (scrollY > 1) scrollTo(0, 1);
}
let toastT;
function toast(text, ms = 2600) {
  const t = $("toast"); t.textContent = text; t.hidden = false;
  clearTimeout(toastT); toastT = setTimeout(() => t.hidden = true, ms);
}
function msg({ title, text = "", btn, onBtn, btn2, onBtn2, link = false }) {
  $("msgTitle").textContent = title; $("msgText").textContent = text;
  $("msgBtn").textContent = btn; $("msgBtn").onclick = onBtn;
  $("msgBtn2").hidden = !btn2; $("msgBtn2").textContent = btn2 || ""; $("msgBtn2").onclick = onBtn2 || null;
  $("msgLink").hidden = !link; if (link) $("msgLink").querySelector(".link").textContent = shareUrl();
  show("msg");
}
const fresh = () => location.href = location.pathname + (MOCK ? "?mock=1" : "");

for (const el of document.querySelectorAll("[data-ministrip]")) el.append($("ministrip").content.cloneNode(true));

// ---- sound + flash ------------------------------------------------------------------------------------------------
let audio;
function shutterSound() {
  try {
    audio ||= new AudioContext();
    const n = Math.floor(audio.sampleRate * 0.06), buf = audio.createBuffer(1, n, audio.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / n) ** 2;
    const src = audio.createBufferSource(), g = audio.createGain(), f = audio.createBiquadFilter();
    f.type = "highpass"; f.frequency.value = 1800; g.gain.value = 0.5;
    src.buffer = buf; src.connect(f).connect(g).connect(audio.destination); src.start();
  } catch {}
}
function flash() {
  const f = $("flash"); f.classList.remove("on"); void f.offsetWidth; f.classList.add("on");
  shutterSound();
}

// ---- routing (PLAN Task 6) ----------------------------------------------------------------------------------------
async function route() {
  if (env.framed) { $("openTab").href = location.href; return show("framed"); }
  if (!S.id) return show("landA");
  let st;
  try { st = await api.status(S.id); }
  catch { return msg({ title: "앗, 연결이 안 돼", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: route }); }
  if (st.error) return expired();
  const role = api.loadRole(S.id)?.role;
  if (!st.a.done) {
    if (role === "a") return msg({ title: "올리다 멈췄어", text: "찍은 사진이 이 화면에 남아 있지 않아. 새로 찍어 줘.", btn: "다시 찍기", onBtn: fresh });
    return msg({ title: "아직 친구가 올리는 중이에요", text: "조금 뒤에 이 링크를 다시 열어 줘.", btn: "다시 확인", onBtn: route });
  }
  if (!st.b.done) return role === "a" ? waiting() : landB();
  return doneScreen();
}
function expired() {
  msg({ title: "없거나 7일이 지나 사라진 링크예요", text: "링크는 7일 뒤 사진·영상과 함께 지워져요.", btn: "새로 찍기", onBtn: fresh });
}
function waiting() {
  msg({ title: "친구가 아직 안 찍었어요", text: "이 링크를 친구한테 보내 줘. 친구가 찍으면 여기서 바로 열려.", link: true,
    btn: "링크 보내기", onBtn: share, btn2: "다시 확인", onBtn2: route });
}
addEventListener("visibilitychange", () => {
  if (!document.hidden && document.body.dataset.screen === "msg" && $("msgTitle").textContent.startsWith("친구가 아직")) route();
});

async function landB() {
  show("landB");
  try { $("peekImg").src = await api.blobUrl(S.id, "a", "1-raw.jpg"); } catch {}
  // A's cut-outs for B's overlay, fetched while B reads the screen
  S.ghostP = Promise.all([1, 2, 3, 4].map(c => api.blobUrl(S.id, "a", `${c}-cut.png`).catch(() => null)))
    .then(g => S.ghost = g);
}

// ---- shoot --------------------------------------------------------------------------------------------------------
async function startShoot(side) {
  S.side = side; S.cuts = []; S.sent.clear();
  show("shoot");
  $("s-shoot").classList.remove("shooting-done");
  $("controls").hidden = false; $("shootDone").hidden = true; $("shootHint").hidden = false;
  $("spot").hidden = side !== "a";
  for (const t of document.querySelectorAll(".th")) { t.className = "th"; t.style.backgroundImage = ""; t.disabled = true; }
  setCut(0);
  $("camwait").hidden = false; $("camwait").textContent = "카메라 켜는 중…";
  try { audio ||= new AudioContext(); } catch {}
  const video = $("video");
  try {
    S.cam ||= await load("camera");
    const segP = MOCK ? null : load("segment").then(m => m.loadSegmenter());
    segP?.catch(() => {});
    await S.cam.startCam(video);
    $("camwait").textContent = "누끼 준비 중…";
    S.cap ||= MOCK ? mockCapture(S.cam) : await load("capture", mockCapture(S.cam));
    await segP;
  } catch (e) { return camError(e); }
  $("camwait").hidden = true;
  video.classList.toggle("mirror", S.cam.camState.mirror);
  document.querySelector('[data-act="mirror"]').setAttribute("aria-pressed", S.cam.camState.mirror);
  if (!S.bound) { S.cam.bindZoomFocus($("stage")); S.bound = true; }
  if (side === "b") { await S.ghostP; setCut(0); loadGhostClips(); }
}
// A's 2 s clips loop over B's preview (cut-out via compose's WebGL alpha); the PNG stays until they are ready
async function loadGhostClips() {
  if (S.ca) return ghostLoop();
  try {
    const C = await load("compose"), ca = C.createClipAlpha();
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
  if (pop) { p.classList.remove("pop"); void p.offsetWidth; p.classList.add("pop"); }
  const g = $("ghost"), src = S.side === "b" && S.ghost[i], clip = S.side === "b" && S.ca && S.ghostClips?.[i];
  S.ghostClips?.forEach((v, k) => k !== i && v.pause());
  $("ghostClip").hidden = !clip;
  g.hidden = !src || !!clip; if (src) g.src = src;
  document.querySelectorAll(".th").forEach((t, k) => t.classList.toggle("now", k === i));
}
function onTick(n) {
  const c = $("count");
  c.textContent = n; c.classList.remove("go"); void c.offsetWidth; c.classList.add("go");
  if (n === 2) $("rec").hidden = false;
  if (n === 1) setTimeout(flash, 1000);
}
async function shootIdx(i) {
  setCut(i);
  const cut = await S.cap.shootOne(i, { video: $("video"), onTick });
  $("count").textContent = ""; $("rec").hidden = true;
  S.cuts[i] = cut;
  const t = document.querySelectorAll(".th")[i];
  t.classList.add("has"); t.style.backgroundImage = `url(${URL.createObjectURL(cut.raw)})`;
}
async function runShoot(only) {
  if (S.busy) return;
  S.busy = true; document.body.classList.add("busy");
  $("shootHint").hidden = true; $("shootDone").hidden = true;
  document.querySelectorAll(".th").forEach(t => t.disabled = true);
  try {
    for (const i of only != null ? [only] : [0, 1, 2, 3].filter(k => !S.cuts[k])) {
      await shootIdx(i);
      if (only == null && i < 3) await sleep(500);
    }
  } catch (e) {
    console.warn("[booth] shoot", e);
    toast("앗, 찍다가 멈췄어. 다시 눌러 줘");
  } finally {
    S.busy = false; document.body.classList.remove("busy");
    $("count").textContent = ""; $("rec").hidden = true;
  }
  const all = [0, 1, 2, 3].every(k => S.cuts[k]);
  document.querySelectorAll(".th").forEach((t, k) => { t.disabled = !S.cuts[k] || !all; t.classList.remove("now"); });
  if (!all) return setCut([0, 1, 2, 3].find(k => !S.cuts[k]));
  $("s-shoot").classList.add("shooting-done");
  $("controls").hidden = true; $("shootDone").hidden = false;
  $("step").textContent = "4 / 4"; $("pose").textContent = "다 찍었어! 맘에 들어?";
  $("ghost").hidden = true; $("ghostClip").hidden = true; S.cutIdx = null;
}

// ---- upload -------------------------------------------------------------------------------------------------------
function fileOf(name) {
  const c = S.cuts[+name[0] - 1];
  return name.endsWith(".jpg") ? [c.raw, "image/jpeg"] : name.endsWith(".png") ? [c.cut, "image/png"] : [c.clip, c.clipType];
}
async function upload() {
  show("upload");
  $("upRetry").hidden = true; $("upTitle").textContent = "올리는 중…";
  const bar = () => { $("upBar").style.width = `${S.sent.size / 12 * 100}%`; $("upCount").textContent = `${S.sent.size} / 12`; };
  bar();
  try {
    if (S.side === "a" && !S.token) {
      const token = api.newToken();
      const { id } = await api.create(token);
      S.id = id; S.token = token;
      api.saveRole(id, "a", token);
      history.replaceState(null, "", `?s=${id}${MOCK ? "&mock=1" : ""}`);
    }
    if (S.side === "b" && !S.token) { S.token = api.newToken(); api.saveRole(S.id, "b", S.token); }
    for (let round = 0; round < 2; round++) {
      for (const n of NAMES) {
        if (S.sent.has(n)) continue;
        const [blob, type] = fileOf(n);
        await api.put(S.id, S.side, n, blob, type, S.token);
        S.sent.add(n); bar();
      }
      const r = await api.done(S.id, S.side, S.token);
      if (r.ok) break;
      if (r.missing) { r.missing.forEach(n => S.sent.delete(n)); bar(); continue; }
      if (r.error === "taken") return doneScreen("다른 친구가 먼저 찍었어요. 둘이 찍은 네컷이야");
      if (r.error === "done") break;
      throw new Error(r.error);
    }
  } catch (e) {
    console.warn("[booth] upload", e);
    $("upTitle").textContent = "앗, 올리다 멈췄어";
    $("upErr").textContent = e.status === 413 ? "파일이 너무 커. 다시 찍어 줘." : "인터넷이 끊겼나 봐. 다시 눌러 줘.";
    $("upRetry").hidden = false;
    return;
  }
  $("video").srcObject?.getTracks().forEach(t => t.stop());
  if (S.side === "a") return shareScreen();
  doneScreen();
}

// ---- share (A) ----------------------------------------------------------------------------------------------------
function shareScreen() {
  show("share");
  $("linkText").textContent = shareUrl(); $("linkBox").hidden = false; $("shareOk").hidden = true;
}
async function share() {
  const url = shareUrl(), text = "내 옆자리 비워 뒀어! 같이 네컷 찍자";
  const ok = t => { $("shareOk").textContent = t; $("shareOk").hidden = false; };
  if (navigator.share) {
    try { await navigator.share({ url, text }); return ok("보냈어! 친구가 찍으면 이 링크에서 열려"); }
    catch (e) { if (e.name === "AbortError") return; }
  }
  try { await navigator.clipboard.writeText(url); return ok("링크 복사했어! 친구한테 붙여 넣어 줘"); }
  catch { toast("아래 링크를 길게 눌러 복사해 줘"); }
}

// ---- done ---------------------------------------------------------------------------------------------------------
const D = { bg: null, frame: null, filter: "original", media: null, photo: null, video: null, seq: 0, mseq: 0, clips: false };
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
  $("doneNote").textContent = note || "배경이랑 프레임 골라 봐. 사진도 영상도 저장할 수 있어";
  $("making").hidden = false; $("makingText").textContent = "네컷 만드는 중";
  const inApp = env.inApp;
  $("saveBtns").hidden = inApp; $("inappSave").hidden = !inApp;
  if (inApp) { $("iosText").hidden = !env.isIOS; document.querySelector('[data-act="toChrome"]').hidden = env.isIOS; }
  try {
    [C, FR, FI] = await Promise.all([load("compose", mockCompose), load("frames", mockFrames), load("filters", mockFilters)]);
    D.media = Object.fromEntries(await Promise.all(["a", "b"].map(async s => [s, await sideMedia(s)])));
  } catch (e) {
    console.warn("[booth] done", e);
    return msg({ title: "앗, 사진을 못 불러왔어", text: "인터넷 연결을 확인하고 다시 해 줘.", btn: "다시 해 보기", onBtn: () => doneScreen(note) });
  }
  buildChips();
  render();
}
function chip(label, pressed, onClick, swatch) {
  const b = Object.assign(document.createElement("button"), { className: "chip", type: "button" });
  b.setAttribute("aria-pressed", pressed);
  if (swatch) { const i = document.createElement("i"); i.style.background = swatch; b.append(i); }
  b.append(label);
  b.onclick = () => { b.parentElement.querySelectorAll("[aria-pressed]").forEach(x => x.setAttribute("aria-pressed", x === b)); onClick(); };
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
  const c = Object.assign(document.createElement("canvas"), { width: 36, height: 36 });
  bg.draw(c.getContext("2d"), 36, 36);
  return `center / cover url(${c.toDataURL()})`;
}
function buildChips() {
  const bgs = C.BACKGROUNDS, frames = FR.FRAMES, filters = FI.FILTERS;
  D.bg ??= bgs.find(b => b.id === "booth-pink") || bgs[0];
  D.frame ??= frames[0];
  $("bgChips").replaceChildren(...bgs.map(bg => chip(bgLabel(bg), bg === D.bg, () => { D.bg = bg; render(); }, bgSwatch(bg))));
  $("filterChips").replaceChildren(...filters.map(f => chip(f.label, f.id === D.filter, () => { D.filter = f.id; render(); })));
  $("frameChips").replaceChildren(...frames.map(f => {
    const b = Object.assign(document.createElement("button"), { className: "fr", type: "button" });
    b.dataset.id = f.id; b.setAttribute("aria-pressed", f === D.frame);
    b.innerHTML = `<span class="mini"></span>`; b.append(f.label);
    b.onclick = () => { $("frameChips").querySelectorAll(".fr").forEach(x => x.setAttribute("aria-pressed", x === b)); D.frame = f; render(false); };
    return b;
  }));
}
const opts = (frame = D.frame) => ({ a: D.media.a, b: D.media.b, bg: D.bg.id, frame, filter: D.filter, date: DATE });
async function render(minis = true) {
  const seq = ++D.seq;
  D.video = null; $("stripVid").hidden = true; $("stripVid").removeAttribute("src");
  document.querySelector('[data-act="makeVideo"]').textContent = "영상 만들기";
  $("making").hidden = false; $("makingText").textContent = "네컷 만드는 중";
  setSave(false);
  let blob;
  try { blob = await C.drawStrip(opts()); }
  catch (e) { console.warn("[booth] drawStrip", e); if (seq === D.seq) $("makingText").textContent = "앗, 네컷을 못 만들었어"; return; }
  if (seq !== D.seq) return;
  D.photo = blob;
  const img = $("stripImg");
  if (img.src) URL.revokeObjectURL(img.src);
  img.src = URL.createObjectURL(blob); img.hidden = false;
  $("making").hidden = true;
  setSave(true);
  if (minis) renderMinis(++D.mseq);
}
async function renderMinis(seq) {          // frame carousel previews, one at a time after the main strip
  for (const b of $("frameChips").querySelectorAll(".fr")) {
    if (seq !== D.mseq) return;
    const f = FR.FRAMES.find(x => x.id === b.dataset.id);
    try {
      const blob = await C.drawStrip(opts(f));
      const m = b.querySelector(".mini"), old = m.dataset.url;
      m.dataset.url = URL.createObjectURL(blob); m.style.backgroundImage = `url(${m.dataset.url})`;
      if (old) URL.revokeObjectURL(old);
    } catch {}
    await sleep(30);
  }
}
function setSave(on) { document.querySelectorAll("#saveBtns .cta").forEach(b => b.disabled = !on); }
function download(blob, name) {
  const a = Object.assign(document.createElement("a"), { href: URL.createObjectURL(blob), download: name });
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 60_000);
}
const fileName = ext => `ttaro-gachi-${DATE.replace(/\./g, "")}.${ext}`;
async function makeVideo() {
  if (D.video) return download(D.video.blob, fileName(D.video.type.includes("mp4") ? "mp4" : "webm"));
  const seq = D.seq;
  setSave(false);
  $("making").hidden = false; $("makingText").textContent = "영상 만드는 중… 6초만 기다려";
  try {
    if (!D.clips) await loadClips();
    const res = await C.recordMovingStrip({ ...opts(), onProgress: p => $("makingText").textContent = `영상 만드는 중… ${Math.round(p * 100)}%` });
    if (seq !== D.seq) return;
    D.video = res;
    const v = $("stripVid");
    v.src = URL.createObjectURL(res.blob); v.hidden = false; $("stripImg").hidden = true; v.play().catch(() => {});
    document.querySelector('[data-act="makeVideo"]').textContent = "영상 저장";
    toast("영상 완성! 한 번 더 누르면 저장돼");
  } catch (e) {
    console.warn("[booth] video", e);
    toast("이 폰에서는 영상 저장이 안 돼요. 사진으로 저장해 줘", 3500);
    document.querySelector('[data-act="makeVideo"]').hidden = true;
  } finally {
    if (seq === D.seq) { $("making").hidden = true; setSave(true); }
  }
}

// ---- actions ------------------------------------------------------------------------------------------------------
const ACT = {
  startA: () => startShoot("a"),
  startB: () => startShoot("b"),
  mine: () => waiting(),
  shoot: () => runShoot(),
  mirror: () => {
    if (!S.cam || S.busy) return;
    S.cam.camState.mirror = !S.cam.camState.mirror;
    $("video").classList.toggle("mirror", S.cam.camState.mirror);
    document.querySelector('[data-act="mirror"]').setAttribute("aria-pressed", S.cam.camState.mirror);
  },
  flip: async () => {
    if (!S.cam || S.busy) return;
    $("camwait").hidden = false; $("camwait").textContent = "카메라 바꾸는 중…";
    try { await S.cam.flipCam($("video")); } catch (e) { return camError(e); }
    $("camwait").hidden = true;
    $("video").classList.toggle("mirror", S.cam.camState.mirror);
    document.querySelector('[data-act="mirror"]').setAttribute("aria-pressed", S.cam.camState.mirror);
  },
  upload, retryUpload: upload, share,
  savePhoto: () => D.photo && download(D.photo, fileName("jpg")),
  makeVideo,
  toChrome: () => location.href = env.chromeUrl(shareUrl()),
};
document.addEventListener("click", e => {
  const b = e.target.closest("[data-act]");
  if (b && ACT[b.dataset.act]) ACT[b.dataset.act]();
  const th = e.target.closest(".th");
  if (th && !th.disabled && !S.busy) { $("controls").hidden = false; runShoot(+th.dataset.i); }
});

// ---- boot ---------------------------------------------------------------------------------------------------------
try { env = await import("./env.js"); } catch {}
window.__booth = { S, D, MOCK };   // dev handle for the headless checks
route();
