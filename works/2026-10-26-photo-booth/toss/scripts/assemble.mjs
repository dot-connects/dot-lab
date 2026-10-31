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

// Google Fonts, bundled: each stylesheet with its unicode-range slices (woff2), so glyphs still load on demand
const FONT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
mkdirSync("cache/fonts", { recursive: true }); mkdirSync(`${OUT}/vendor/fonts`, { recursive: true });
async function bundleFonts(url, name) {
  const cached = `cache/fonts/${name}.css`;
  let css = existsSync(cached) ? readFileSync(cached, "utf8") : "";
  if (!css) {
    const r = await fetch(url, { headers: { "User-Agent": FONT_UA } });
    if (!r.ok) throw new Error(`font css ${name} http ${r.status}`);
    css = await r.text(); writeFileSync(cached, css);
  }
  const urls = [...new Set([...css.matchAll(/url\((https:[^)]+)\)/g)].map(m => m[1]))];
  for (let i = 0; i < urls.length; i += 12) await Promise.all(urls.slice(i, i + 12).map(async u => {
    const f = `${u.split("/").slice(-3).join("-")}`.replace(/[^\w.-]/g, "_");
    if (!existsSync(`cache/fonts/${f}`)) {
      const r = await fetch(u); if (!r.ok) throw new Error(`font ${u} http ${r.status}`);
      writeFileSync(`cache/fonts/${f}`, Buffer.from(await r.arrayBuffer()));
    }
    cpSync(`cache/fonts/${f}`, `${OUT}/vendor/fonts/${f}`);
    css = css.replaceAll(u, f);
  }));
  writeFileSync(`${OUT}/vendor/fonts/${name}.css`, css);
}
const framesJs = `${OUT}/js/frames.js`, fs0 = readFileSync(framesJs, "utf8");
const fontDecl = /const FONT_CSS = ([\s\S]*?);\r?\n/.exec(fs0);
if (!fontDecl) throw new Error("frames.js FONT_CSS changed: update assemble.mjs");
await bundleFonts(fontDecl[1].replace(/"\s*\+\s*"/g, "").replace(/^"|"$/g, ""), "frames");
writeFileSync(framesJs, fs0.replace(fontDecl[0], 'const FONT_CSS = "vendor/fonts/frames.css";\n'));

// the mini-app is the 말랑 부스 theme only (light-mode rule): the pre-paint ui script always says cute
const html = `${OUT}/index.html`, src = readFileSync(html, "utf8");
const UI = 'document.body.dataset.ui = /[?&]ui=cute(&|$)/.test(location.search) ? "cute" : "film";';
if (!src.includes(UI)) throw new Error("index.html ui script changed: update assemble.mjs");
const uiFont = /<link rel="stylesheet" href="(https:\/\/fonts\.googleapis\.com[^"]+)"[^>]*>/.exec(src);
if (!uiFont) throw new Error("index.html font link changed: update assemble.mjs");
await bundleFonts(uiFont[1].replaceAll("&amp;", "&"), "ui");
writeFileSync(html, src.replace(UI, 'document.body.dataset.ui = "cute"; document.body.dataset.platform = "toss";')
  .replace(uiFont[0], '<link rel="stylesheet" href="vendor/fonts/ui.css">').replace(/<link rel="preconnect"[^>]*>\r?\n/g, ""));
rmSync(`${OUT}/done.html`, { force: true });       // the web 완성 link page: Toss shares its own link
console.log("assemble ok");
