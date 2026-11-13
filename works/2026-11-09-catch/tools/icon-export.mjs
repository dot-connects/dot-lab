// Writes the launcher icon from mockups/icon.html (operator's palette pick, 2026-11-13):
//   app/.../res/mipmap-xxxhdpi/ic_launcher_foreground.png (432 px), out/store-icon-512.png.
// Usage (from works/2026-11-09-catch): node tools/icon-export.mjs <empty chrome profile dir>
import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";

const [prof] = process.argv.slice(2);
const here = new URL("../", import.meta.url);
const page = new URL("mockups/icon.html", here).href;
const dbg = 9400 + Math.floor(Math.random() * 40);
const p = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe",
  ["--headless=new", "--disable-gpu", `--remote-debugging-port=${dbg}`, `--user-data-dir=${prof}`, page], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
try {
  let tg;
  for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${dbg}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => (ws.onopen = r));
  const ev = expr => new Promise(r => { ws.onmessage = e => r(JSON.parse(e.data).result?.result?.value); ws.send(JSON.stringify({ id: 1, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true } })); });
  let out;
  for (let i = 0; i < 40 && !out; i++) { out = await ev("window.__exportIcons && window.__exportIcons()"); if (!out) await sleep(500); }
  const png = u => Buffer.from(u.split(",")[1], "base64");
  const mip = new URL("app/app/src/main/res/mipmap-xxxhdpi/", here); mkdirSync(mip, { recursive: true });
  writeFileSync(new URL("ic_launcher_foreground.png", mip), png(out.fg));
  writeFileSync(new URL("out/store-icon-512.png", here), png(out.store));
  console.log("bg", out.bg.join(" "));
  ws.close();
} finally { p.kill(); }
