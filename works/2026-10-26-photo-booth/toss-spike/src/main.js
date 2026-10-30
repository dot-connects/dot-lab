// Toss mini-app spike for 「따로 같이 네컷」: does each engine piece work inside the Toss app's WebView?
// 1 live camera (getUserMedia from a tap), 2 recording (MediaRecorder / WebCodecs + mp4-muxer), 3 saving (saveBase64Data),
// 4 cut-outs (MediaPipe selfie_segmenter, MODNet on onnxruntime-web), 5 rewarded test ad, 6 share link.
// Every result goes to the log so the operator can copy it back in one tap.
import {
  getOperationalEnvironment, getPlatformOS, getTossAppVersion, getTossShareLink, loadFullScreenAd, saveBase64Data,
  setClipboardText, share, showFullScreenAd,
} from "@apps-in-toss/web-framework";
import { FilesetResolver, ImageSegmenter } from "@mediapipe/tasks-vision";
import { ArrayBufferTarget, Muxer } from "../../site/js/vendor/mp4-muxer.mjs";

const $ = id => document.getElementById(id);
const BASE = new URL("./", location.href).href;
const AD_ID = "ait-ad-test-rewarded-id";            // test id only: a real id in development counts as a policy violation
const APP = import.meta.env.VITE_AIT_APP_NAME || "photo-booth-spike";
const lines = [];
function log(msg) {
  const t = (performance.now() / 1000).toFixed(1).padStart(6);
  lines.push(`${t}s ${msg}`); $("log").textContent = lines.join("\n");
  console.log("[spike]", msg);
}
function badge(id, ok, text) {
  const b = $("b-" + id); b.textContent = text ?? (ok ? "성공" : "실패"); b.className = "badge " + (ok ? "ok" : "bad");
}
const safe = f => async (...a) => { try { await f(...a); } catch (e) { log("ERROR " + (e?.name || "") + " " + (e?.message || e)); } };
const tryCall = (f, d = "?") => { try { return f(); } catch (e) { return d + " (" + (e?.message || e) + ")"; } };

// 0. environment
const MIMES = ["video/mp4;codecs=avc1", "video/mp4", "video/webm;codecs=vp9", "video/webm"];
async function env() {
  const gpu = await navigator.gpu?.requestAdapter().catch(() => null);
  const info = {
    env: tryCall(getOperationalEnvironment), os: tryCall(getPlatformOS), toss: tryCall(getTossAppVersion),
    ua: navigator.userAgent, screen: `${screen.width}x${screen.height}@${devicePixelRatio}`,
    webgpu: !!gpu, webgl2: !!document.createElement("canvas").getContext("webgl2"),
    VideoEncoder: !!window.VideoEncoder, MediaRecorder: !!window.MediaRecorder,
    recorderTypes: MIMES.filter(t => window.MediaRecorder?.isTypeSupported(t)).join(", ") || "-",
    getUserMedia: !!navigator.mediaDevices?.getUserMedia, crossOriginIsolated: self.crossOriginIsolated,
    deviceMemory: navigator.deviceMemory ?? "?", heapLimitMB: performance.memory ? Math.round(performance.memory.jsHeapSizeLimit / 2 ** 20) : "?",
    origin: location.origin,
  };
  $("env").textContent = Object.entries(info).map(([k, v]) => `${k}: ${v}`).join("\n");
  badge("env", true, info.env === "toss" ? "토스 앱" : String(info.env));
  log("env " + JSON.stringify(info));
}

// 1. camera
let stream = null;
async function cam() {
  const t0 = performance.now();
  stream = await navigator.mediaDevices.getUserMedia({ audio: false,
    video: { facingMode: { ideal: "user" }, aspectRatio: { ideal: 4 / 3 }, width: { ideal: 1280 } } });
  const v = $("preview");
  v.muted = true; v.playsInline = true; v.srcObject = stream;
  await v.play();
  if (!v.videoWidth) await new Promise(r => v.addEventListener("loadeddata", r, { once: true }));
  const s = stream.getVideoTracks()[0].getSettings();
  log(`camera ok ${v.videoWidth}x${v.videoHeight} ${s.frameRate ?? "?"}fps in ${Math.round(performance.now() - t0)}ms`);
  badge("cam", true);
  for (const id of ["rec-mr", "rec-wc", "rec-long", "save-png", "cut-fast", "cut-hq"]) $(id).disabled = false;
}

// 2. recording: both paths draw the mirrored preview into a 640x480 canvas, like the real app's clip canvas
const W = 640, H = 480, cv = Object.assign(document.createElement("canvas"), { width: W, height: H }), cg = cv.getContext("2d");
const draw = () => { cg.setTransform(-1, 0, 0, 1, W, 0); cg.drawImage($("preview"), 0, 0, W, H); };
let lastClip = null;
function showClip(blob, how, ms) {
  lastClip = blob;
  const v = $("clip"); v.src = URL.createObjectURL(blob);
  v.onloadeddata = () => { log(`${how} plays: ${v.videoWidth}x${v.videoHeight} ${v.duration.toFixed(2)}s`); badge("rec", true); };
  v.onerror = () => { log(`${how} does NOT play (${v.error?.code})`); badge("rec", false); };
  v.play().catch(() => {});
  log(`${how} ${blob.type || "?"} ${(blob.size / 1024).toFixed(0)}KB in ${ms}ms`);
  $("save-vid").disabled = blob.size === 0;
}
async function recMR(sec) {
  const type = MIMES.find(t => MediaRecorder.isTypeSupported(t)) || "";
  const st = cv.captureStream(30), rec = new MediaRecorder(st, { mimeType: type, videoBitsPerSecond: 1_000_000 }), parts = [];
  rec.ondataavailable = e => e.data.size && parts.push(e.data);
  const t0 = performance.now(), stop = new Promise(r => { rec.onstop = r; }), started = new Promise(r => { rec.onstart = r; });
  let on = true; const loop = () => { if (!on) return; draw(); requestAnimationFrame(loop); }; loop();
  rec.start(250);
  await started;                                     // the encoder can take ~1 s to start (headless, 10-26)
  log(`MediaRecorder started after ${Math.round(performance.now() - t0)}ms`);
  await new Promise(r => setTimeout(r, sec * 1000));
  rec.stop(); await stop; on = false; st.getTracks().forEach(t => t.stop());
  showClip(new Blob(parts, { type: type.split(";")[0] || parts[0]?.type }), `MediaRecorder(${type || "default"})`, Math.round(performance.now() - t0));
}
async function recWC(sec) {
  let cfg = null;
  for (const codec of ["avc1.42e01f", "avc1.4d401f", "avc1.640028"]) {
    const c = { codec, width: W, height: H, bitrate: 1_000_000, framerate: 30 };
    if ((await VideoEncoder.isConfigSupported(c).catch(() => null))?.supported) { cfg = c; break; }
  }
  if (!cfg) { log("WebCodecs: no supported avc config"); badge("rec", false); return; }
  const muxer = new Muxer({ target: new ArrayBufferTarget(), video: { codec: "avc", width: W, height: H, frameRate: 30 }, fastStart: "in-memory" });
  let failed = null;
  const enc = new VideoEncoder({ output: (c, m) => muxer.addVideoChunk(c, m), error: e => { failed = e; } });
  enc.configure(cfg);
  const t0 = performance.now();
  for (let i = 0; performance.now() - t0 < sec * 1000; i++) {
    draw();
    const f = new VideoFrame(cv, { timestamp: Math.round(i * 1e6 / 30) });
    enc.encode(f, { keyFrame: i % 30 === 0 }); f.close();
    await new Promise(r => setTimeout(r, 1000 / 30));
  }
  await enc.flush(); enc.close(); muxer.finalize();
  if (failed) throw failed;
  showClip(new Blob([muxer.target.buffer], { type: "video/mp4" }), `WebCodecs(${cfg.codec})`, Math.round(performance.now() - t0));
}

// 3. saving
const b64 = blob => new Promise((res, rej) => {
  const r = new FileReader(); r.onload = () => res(String(r.result).split(",")[1]); r.onerror = () => rej(r.error); r.readAsDataURL(blob);
});
async function save(blob, name) {
  const mimeType = (blob.type || "application/octet-stream").split(";")[0], t0 = performance.now(), data = await b64(blob);
  log(`save ${name} ${mimeType} ${(blob.size / 1024).toFixed(0)}KB (base64 ${(data.length / 1024).toFixed(0)}KB)…`);
  await saveBase64Data({ data, fileName: name, mimeType });
  log(`save ${name} resolved in ${Math.round(performance.now() - t0)}ms — check the gallery`);
  badge("save", true, "저장 호출 성공");
}
async function saveVid() {
  const ext = lastClip.type.includes("webm") ? "webm" : "mp4";
  await save(lastClip, `ttaro-gachi-${Date.now()}.${ext}`).catch(e => { badge("save", false); throw e; });
}
async function savePng() {
  draw();
  const blob = await new Promise(r => cv.toBlob(r, "image/png"));
  await save(blob, `ttaro-gachi-${Date.now()}.png`).catch(e => { badge("save", false); throw e; });
}

// 4. cut-outs
let seg = null;
async function cutFast() {
  const t0 = performance.now();
  if (!seg) {
    const files = await FilesetResolver.forVisionTasks(BASE + "vendor/mediapipe");
    for (const delegate of ["GPU", "CPU"]) {
      try {
        seg = await ImageSegmenter.createFromOptions(files, { baseOptions: { modelAssetPath: BASE + "models/selfie_segmenter.tflite", delegate },
          runningMode: "IMAGE", outputConfidenceMasks: true, outputCategoryMask: false });
        log(`fast cut-out loaded (${delegate}) in ${Math.round(performance.now() - t0)}ms`); break;
      } catch (e) { log(`fast cut-out ${delegate} failed: ${e?.message || e}`); }
    }
    if (!seg) { badge("cut", false); return; }
  }
  draw();
  const t1 = performance.now(), r = seg.segment(cv), m = r.confidenceMasks[0].getAsFloat32Array();
  let s = 0; for (const v of m) s += v;
  r.close();
  log(`fast cut-out ran in ${Math.round(performance.now() - t1)}ms, person share ${(s / m.length).toFixed(3)}`);
  badge("cut", true, "빠른 누끼 성공");
}
let mw = null;
async function cutHQ() {
  mw ||= new Worker(new URL("./matting.worker.js", import.meta.url), { type: "module" });
  draw();
  const img = await createImageBitmap(cv);
  log("HQ cut-out started (first run downloads 26MB + builds the session)…");
  const r = await new Promise(res => { mw.onmessage = e => res(e.data); mw.onerror = e => res({ error: "worker " + (e.message || "load") }); mw.postMessage({ base: BASE, img }, [img]); });
  if (r.error) { log("HQ cut-out failed: " + r.error); badge("cut", false); return; }
  log(`HQ cut-out ${r.ep} ${r.w}x${r.h}: load ${r.loadMs}ms, run ${r.ms}ms, alpha mean ${r.mean}`);
  badge("cut", true, `정밀 누끼 성공 (${r.ep})`);
}

// 5. rewarded test ad: load -> loaded event -> show -> userEarnedReward -> load the next one
let adReady = false;
function adLoad() {
  if (tryCall(() => loadFullScreenAd.isSupported(), false) !== true) { $("ad").textContent = "광고 미지원 환경"; log("ad: isSupported() = false"); return; }
  adReady = false; $("ad").disabled = true; $("ad").textContent = "광고 불러오는 중…";
  loadFullScreenAd({ options: { adGroupId: AD_ID },
    onEvent: e => { if (e.type === "loaded") { adReady = true; $("ad").disabled = false; $("ad").textContent = "광고 보고 영상 받기 (테스트)"; log("ad loaded"); } },
    onError: e => { log("ad load error: " + (e?.message || e)); badge("ad", false); $("ad").textContent = "광고 불러오기 실패"; } });
}
function adShow() {
  if (!adReady) return;
  showFullScreenAd({ options: { adGroupId: AD_ID },
    onEvent: e => {
      log("ad event " + e.type + (e.data ? " " + JSON.stringify(e.data) : ""));
      if (e.type === "userEarnedReward") badge("ad", true, "보상 받음");
      if (e.type === "dismissed" || e.type === "failedToShow") adLoad();
    },
    onError: e => { log("ad show error: " + (e?.message || e)); badge("ad", false); adLoad(); } });
}

// 6. share link
async function shareLink() {
  const link = await getTossShareLink(`intoss://${APP}/?s=spike`);
  log("share link: " + link);
  badge("share", true, "링크 생성");
  await share({ message: "따로 같이 네컷 시험 " + link }).catch(e => log("share sheet: " + (e?.message || e)));
}

async function copy() {
  const text = lines.join("\n");
  try { await setClipboardText(text); log("copied (Toss clipboard)"); }
  catch { await navigator.clipboard?.writeText(text).then(() => log("copied (web clipboard)"), e => log("copy failed: " + e?.message)); }
}

$("cam").onclick = safe(async () => { try { await cam(); } catch (e) { badge("cam", false); throw e; } });
$("rec-mr").onclick = safe(() => recMR(2));
$("rec-wc").onclick = safe(async () => { if (!window.VideoEncoder) { log("WebCodecs: no VideoEncoder"); badge("rec", false); return; } await recWC(2); });
$("rec-long").onclick = safe(() => recMR(8));
$("save-vid").onclick = safe(saveVid);
$("save-png").onclick = safe(savePng);
$("cut-fast").onclick = safe(cutFast);
$("cut-hq").onclick = safe(cutHQ);
$("ad").onclick = safe(adShow);
$("share").onclick = safe(async () => { try { await shareLink(); } catch (e) { badge("share", false); throw e; } });
$("copy").onclick = safe(copy);
addEventListener("error", e => log("window error " + e.message));
addEventListener("unhandledrejection", e => log("unhandled " + (e.reason?.message || e.reason)));
safe(env)();
safe(async () => adLoad())();
