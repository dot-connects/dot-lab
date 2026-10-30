// Builds dist/ for the Apps in Toss bundle from site/ as it is (PLAN-toss.md Task 6 + review items 3, 5, 18, 19):
// site/ copied raw, then vite bundles src/platform-toss.js over dist/js/platform.js (the one web<->Toss seam).
// Libraries and models come from node_modules / cache/ into dist/vendor + dist/models: no remotely loaded code.
// No raw .mjs in the bundle (the Toss host's MIME for .mjs is untested): every module is copied as .js.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";

const SITE = "../site", OUT = "dist", NM = "node_modules";
rmSync(OUT, { recursive: true, force: true });
cpSync(SITE, OUT, { recursive: true, filter: p => !/[\\/](dev-[^\\/]*|zz-[^\\/]*)$/.test(p) });

// MediaPipe: the module bundle (page), the .cjs (pet-worker loads it as text), the SIMD wasm only
const mp = `${OUT}/vendor/mediapipe`;
mkdirSync(`${mp}/wasm`, { recursive: true });
cpSync(`${NM}/@mediapipe/tasks-vision/vision_bundle.mjs`, `${mp}/vision_bundle.js`);
cpSync(`${NM}/@mediapipe/tasks-vision/vision_bundle.cjs`, `${mp}/vision_bundle.cjs`);
for (const f of ["vision_wasm_internal.js", "vision_wasm_internal.wasm"]) cpSync(`${NM}/@mediapipe/tasks-vision/wasm/${f}`, `${mp}/wasm/${f}`);

// onnxruntime-web: the two bundles matting-worker.js picks from, and the loader + wasm each one asks for (wasmPaths)
const ort = `${OUT}/vendor/ort`, od = `${NM}/onnxruntime-web/dist`;
mkdirSync(ort, { recursive: true });
for (const f of ["ort.webgpu.min", "ort.wasm.min", "ort-wasm-simd-threaded.jsep", "ort-wasm-simd-threaded"]) cpSync(`${od}/${f}.mjs`, `${ort}/${f}.js`);
for (const f of ["ort-wasm-simd-threaded.jsep.wasm", "ort-wasm-simd-threaded.wasm"]) cpSync(`${od}/${f}`, `${ort}/${f}`);

// models the web build fetches from storage.googleapis.com: downloaded once into cache/
mkdirSync("cache", { recursive: true });
for (const [name, url] of [
  ["selfie_segmenter.tflite", "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite"],
  ["deeplab_v3.tflite", "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite"]]) {
  if (!existsSync(`cache/${name}`)) {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`${name} http ${r.status}`);
    writeFileSync(`cache/${name}`, Buffer.from(await r.arrayBuffer()));
  }
  cpSync(`cache/${name}`, `${OUT}/models/${name}`);
}

// the workers' CDN fallbacks (for a cached older web page) never apply here: segment.js always passes the bases. Blank
// them so the bundle holds no remote code URL at all.
for (const [f, urls] of [["matting-worker.js", ["https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/"]],
  ["pet-worker.js", ["https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14",
    "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite"]]]) {
  let s = readFileSync(`${OUT}/js/${f}`, "utf8");
  for (const u of urls) { if (!s.includes(`"${u}"`)) throw new Error(`${f}: ${u} not found, update assemble.mjs`); s = s.replace(`"${u}"`, '""'); }
  writeFileSync(`${OUT}/js/${f}`, s);
}

// the mini-app is the 말랑 부스 theme only (light-mode rule): the pre-paint ui script always says cute
const html = `${OUT}/index.html`, src = readFileSync(html, "utf8");
const UI = 'document.body.dataset.ui = /[?&]ui=cute(&|$)/.test(location.search) ? "cute" : "film";';
if (!src.includes(UI)) throw new Error("index.html ui script changed: update assemble.mjs");
writeFileSync(html, src.replace(UI, 'document.body.dataset.ui = "cute"; document.body.dataset.platform = "toss";'));
rmSync(`${OUT}/done.html`, { force: true });       // the web 완성 link page: Toss shares its own link
console.log("assemble ok");
