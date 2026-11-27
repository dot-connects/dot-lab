// Builds app/src/main/assets/www for the Play app (T-0188): the Toss assemble (site/ + bundled libraries and models, no
// remote code) as it is, then platform-play.js as js/platform.js and data-platform = "play".
// Install under 30 MB (operator 11-27): data that is not code stays on the web and is fetched on first use instead:
// MODNet (26 MB, platform-play.js MODELS.modnet) and the Google Fonts (21 MB of slices; the site's own <link> and
// frames.js FONT_CSS, as on the web). Code (MediaPipe, onnxruntime, their wasm) stays in the app.
// node play/assemble.mjs   (from anywhere; needs toss/node_modules: npm ci in toss/ once)
import { execFileSync } from "node:child_process";
import { cpSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url)), TOSS = join(HERE, "..", "toss"), SITE = join(HERE, "..", "site");
const WWW = join(HERE, "app/src/main/assets/www");
execFileSync(process.execPath, ["scripts/assemble.mjs"], { cwd: TOSS, stdio: "inherit" });
rmSync(WWW, { recursive: true, force: true });
cpSync(join(TOSS, "dist"), WWW, { recursive: true });
cpSync(join(HERE, "platform-play.js"), join(WWW, "js/platform.js"));
rmSync(join(WWW, "models/modnet.onnx"));
rmSync(join(WWW, "vendor/fonts"), { recursive: true });
cpSync(join(SITE, "js/frames.js"), join(WWW, "js/frames.js"));   // FONT_CSS back to Google Fonts

// index.html from the site (its font link and preconnects), with the Toss build's two app-shell changes
const src = readFileSync(join(SITE, "index.html"), "utf8");
const UI = 'document.body.dataset.ui = /[?&]ui=cute(&|$)/.test(location.search) ? "cute" : "film";';
if (!src.includes(UI)) throw new Error("index.html ui script changed: update play/assemble.mjs");
writeFileSync(join(WWW, "index.html"), src.replace(UI, 'document.body.dataset.ui = "cute"; document.body.dataset.platform = "play";'));
console.log("play assemble ok", WWW);
