// Builds app/src/main/assets/www for the Play app (T-0188): the Toss assemble (site/ + bundled libraries, models and
// fonts, no remote code) as it is, then platform-play.js as js/platform.js and data-platform = "play".
// node play/assemble.mjs   (from anywhere; needs toss/node_modules: npm ci in toss/ once)
import { execFileSync } from "node:child_process";
import { cpSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url)), TOSS = join(HERE, "..", "toss"), WWW = join(HERE, "app/src/main/assets/www");
execFileSync(process.execPath, ["scripts/assemble.mjs"], { cwd: TOSS, stdio: "inherit" });
rmSync(WWW, { recursive: true, force: true });
cpSync(join(TOSS, "dist"), WWW, { recursive: true });
cpSync(join(HERE, "platform-play.js"), join(WWW, "js/platform.js"));
const html = join(WWW, "index.html"), src = readFileSync(html, "utf8"), mark = 'document.body.dataset.platform = "toss";';
if (!src.includes(mark)) throw new Error("toss assemble changed its platform mark: update play/assemble.mjs");
writeFileSync(html, src.replace(mark, 'document.body.dataset.platform = "play";'));
console.log("play assemble ok", WWW);
