// Web <-> Toss seam (PLAN-toss.md). The Toss build overwrites this file with toss/src/platform-toss.js, which has the same
// exports plus save/share/ad functions. Workers never import this file (the Toss one bundles the SDK, which needs window).
const preview = new URLSearchParams(location.search).has("toss");
export const id = "web";
// The mini-app is public (2026-11-01), so the web build carries the Toss watermark.
// The locks (Toss-only making video, locked themes) stay off (operator 11-01: reach comes first, flip tossLocks to bring them back).
// ?toss=1 previews the locks too (the flag rides along on share links).
export const tossLive = true, tossLocks = false;
export const tossQ = preview ? "&toss=1" : "";
export const watermark = tossLive || preview, makingLocked = tossLocks || preview, decoLocked = makingLocked;
export const tossUrl = "";   // the mini-app link once it is live; "" = the hint says to search in Toss
export const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
export const MP_BUNDLE = MP + "/vision_bundle.mjs";
const ORT = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/";
// per onnxruntime bundle: its module URL and the wasmPaths it loads its loader + wasm from
export const ORT_LOAD = { webgpu: { url: ORT + "ort.webgpu.min.mjs", paths: ORT }, wasm: { url: ORT + "ort.wasm.min.mjs", paths: ORT } };
export const MODELS = {
  selfie: "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite",
  deeplab: "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite",
};
// Worker URL: local wrangler dev while developing, deployed Worker on Pages
export const WORKER = location.hostname === "127.0.0.1" ? "http://127.0.0.1:8787" : "https://dot-lab-booth.dotconnects.workers.dev";
