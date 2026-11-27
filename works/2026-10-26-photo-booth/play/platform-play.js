// The Google Play app's side of site/js/platform.js (T-0188): same exports as the Toss one (platform-toss.js), no SDK.
// assemble.mjs copies this over www/js/platform.js. The shell (app/.../MainActivity.kt) serves www/ from
// https://appassets.androidplatform.net and adds window.BoothApp { save, share, close }; in a desktop browser each
// call falls back to a logged stub, so the assembled www/ can be checked headless.
const A = window.BoothApp;
const base = new URL(".", document.baseURI).href;

export const id = "play", native = true, ads = false;   // v1 has no ads: saving is open from the start (app.js D.ad)
export const tossLive = true, tossQ = "", watermark = false, makingLocked = false, decoLocked = false, tossUrl = "";
export const MP = base + "vendor/mediapipe";
export const MP_BUNDLE = MP + "/vision_bundle.js";
const ORT = base + "vendor/ort/";
export const ORT_LOAD = {
  webgpu: { url: ORT + "ort.webgpu.min.js", paths: { mjs: ORT + "ort-wasm-simd-threaded.jsep.js", wasm: ORT + "ort-wasm-simd-threaded.jsep.wasm" } },
  wasm: { url: ORT + "ort.wasm.min.js", paths: { mjs: ORT + "ort-wasm-simd-threaded.js", wasm: ORT + "ort-wasm-simd-threaded.wasm" } },
};
// MODNet (26 MB, the HQ still cut) is data, not code: fetched once from the web build on first use and kept in Cache
// Storage (segment.js), so the install stays under 30 MB (operator 11-27). Offline before that = the live matte.
export const MODELS = { selfie: base + "models/selfie_segmenter.tflite", deeplab: base + "models/deeplab_v3.tflite",
  modnet: "https://dot-connects.github.io/dot-lab/works/2026-10-26-photo-booth/site/models/modnet.onnx" };
export const WORKER = "https://dot-lab-booth.dotconnects.workers.dev";
export const debugWebLink = false, stub = !A;
// the friend (B) opens the web build: it needs no install, and the link works for anyone
const WEB = "https://dot-connects.github.io/dot-lab/works/2026-10-26-photo-booth/site/";

const b64 = blob => new Promise((res, rej) => {
  const r = new FileReader(); r.onload = () => res(String(r.result).split(",")[1]); r.onerror = () => rej(r.error); r.readAsDataURL(blob);
});
// straight into the gallery (Pictures/ or Movies/따로같이네컷)
export async function save(blob, fileName) {
  const mimeType = (blob.type || "application/octet-stream").split(";")[0];
  if (!A) return console.log("[play stub] save", fileName, mimeType, blob.size);
  if (!A.save(await b64(blob), fileName, mimeType)) throw new Error("save failed");
}

export async function shareLink(sid) {
  const message = `따로 같이 네컷 같이 찍자! 내 옆자리 비워 뒀어 ${WEB}?s=${sid}&ui=cute`;
  if (A) A.share(message); else console.log("[play stub] share", message);
  return true;
}

export const loadAd = () => Promise.resolve("failed");
export const showAd = onEvent => onEvent("failed");

// Android back key: MainActivity asks window.__boothBack first
export function onBack(fn) { window.__boothBack = fn; }
export const close = () => A ? A.close() : console.log("[play stub] close");
