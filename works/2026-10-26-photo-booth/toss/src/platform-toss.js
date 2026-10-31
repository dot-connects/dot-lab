// The Toss mini-app's side of site/js/platform.js (same exports + save / share / ad / back). Bundled by vite with the SDK
// into dist/js/platform.js. Outside the Toss app (headless checks, a desktop browser) each SDK call falls back to a
// logged stub, decided per function by its own isSupported() (review r3 #34).
import { Environment, closeView, getTossShareLink, graniteEvent, loadFullScreenAd, saveBase64Data, share, showFullScreenAd } from "@apps-in-toss/web-framework";

const APP = import.meta.env.VITE_AIT_APP_NAME;
const base = new URL(".", document.baseURI).href;     // the bundle root (index.html's folder), built at runtime (review r2 #20)
const sup = f => { try { return !!f.isSupported?.(); } catch { return false; } };

export const id = "toss";
export const tossLive = true, tossQ = "", watermark = false, makingLocked = false, decoLocked = false, tossUrl = "";
export const MP = base + "vendor/mediapipe";
export const MP_BUNDLE = MP + "/vision_bundle.js";
const ORT = base + "vendor/ort/";
export const ORT_LOAD = {
  webgpu: { url: ORT + "ort.webgpu.min.js", paths: { mjs: ORT + "ort-wasm-simd-threaded.jsep.js", wasm: ORT + "ort-wasm-simd-threaded.jsep.wasm" } },
  wasm: { url: ORT + "ort.wasm.min.js", paths: { mjs: ORT + "ort-wasm-simd-threaded.js", wasm: ORT + "ort-wasm-simd-threaded.wasm" } },
};
export const MODELS = { selfie: base + "models/selfie_segmenter.tflite", deeplab: base + "models/deeplab_v3.tflite" };
export const WORKER = "https://dot-lab-booth.dotconnects.workers.dev";
// the bridge moves ~85 KB/s (spike, 10-30): a smaller moving strip (6 s) saves ~15 s of "보내는 중"
export const videoBps = 2_500_000;
// test build only: the share message also carries the web link, so a second phone can be B before release (review r3 #28)
export const debugWebLink = /-test$/.test(APP);
const WEB = "https://dot-connects.github.io/dot-lab/works/2026-10-26-photo-booth/site/";
export const stub = !sup(saveBase64Data);

// the link this launch came in with (?s=...): Environment.initialURL, read once per session so 다시 찍기 starts clean
export function entryQuery() {
  const here = new URLSearchParams(location.search);
  if (here.has("s")) return here;
  try {
    if (sessionStorage.getItem("booth:entry")) return here;
    sessionStorage.setItem("booth:entry", "1");
    const q = new URL(Environment.initialURL).searchParams;
    console.log("[toss] entry", Environment.initialURL);
    return q.has("s") ? q : here;
  } catch { return here; }
}

const b64 = blob => new Promise((res, rej) => {
  const r = new FileReader(); r.onload = () => res(String(r.result).split(",")[1]); r.onerror = () => rej(r.error); r.readAsDataURL(blob);
});
// saves through Toss: Android shows the share sheet (operator 10-30: sent to KakaoTalk "나와의 채팅" and saved from there)
export async function save(blob, fileName) {
  const t0 = performance.now(), mimeType = (blob.type || "application/octet-stream").split(";")[0];
  const data = await b64(blob);
  if (sup(saveBase64Data)) await saveBase64Data({ data, fileName, mimeType });
  else console.log("[toss stub] save", fileName, mimeType, blob.size);
  console.log("[toss] saved", fileName, blob.size, Math.round(performance.now() - t0) + "ms");
}

export async function shareLink(sid) {
  let link = "";
  try { link = await getTossShareLink(`intoss://${APP}/?s=${sid}`); } catch (e) { console.warn("[toss] share link", e); }
  const web = debugWebLink ? `\n(시험용 웹 링크) ${WEB}?s=${sid}&ui=cute&toss=1` : "";
  const message = `따로 같이 네컷 같이 찍자! 내 옆자리 비워 뒀어 ${link}${web}`;
  try { await share({ message }); return true; } catch (e) { console.log("[toss stub] share", message, e?.message); return !link; }
}

// rewarded ad (test id until launch). loadAd resolves "loaded" | "failed"; showAd's onEvent gets
// "earned" | "dismissed" | "failed" (failedToShow / onError: the user is not blocked by our ad supply, operator 10-31)
const AD_ID = debugWebLink ? "ait-ad-test-rewarded-id" : import.meta.env.VITE_AIT_AD_ID;
let adP = null;
export function loadAd() {
  if (!sup(loadFullScreenAd)) return Promise.resolve("failed");
  return adP ||= new Promise(res => {
    try {
      loadFullScreenAd({ options: { adGroupId: AD_ID }, onEvent: e => { if (e.type === "loaded") res("loaded"); },
        onError: e => { console.warn("[toss] ad load", e); res("failed"); } });
    } catch (e) { console.warn("[toss] ad load", e); res("failed"); }
  }).then(r => { if (r !== "loaded") adP = null; return r; });
}
export function showAd(onEvent) {
  adP = null;                              // an ad is used up by showing it: the next one loads fresh
  if (!sup(showFullScreenAd)) {            // headless stub: a 3 s "ad" that pays out
    console.log("[toss stub] ad"); setTimeout(() => { onEvent("earned"); onEvent("dismissed"); }, 3000); return;
  }
  try {
    showFullScreenAd({ options: { adGroupId: AD_ID },
      onEvent: e => { console.log("[toss] ad", e.type, document.hidden);
        if (e.type === "userEarnedReward") onEvent("earned");
        else if (e.type === "dismissed") onEvent("dismissed");
        else if (e.type === "failedToShow") onEvent("failed"); },
      onError: e => { console.warn("[toss] ad show", e); onEvent("failed"); } });
  } catch (e) { console.warn("[toss] ad show", e); onEvent("failed"); }
}

// system back (nav bar + Android key): the app decides (review item 9 / r2 #22)
export function onBack(fn) {
  try { graniteEvent.addEventListener("backEvent", { onEvent: fn, onError: e => console.warn("[toss] back", e) }); }
  catch (e) { console.warn("[toss] back", e); }
}
export const close = () => closeView().catch(e => console.warn("[toss] close", e));
console.log("[toss] platform", APP, location.origin, stub ? "stub" : "app");
