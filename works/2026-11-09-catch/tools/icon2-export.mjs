// Writes the launcher icon from mockups/icon2.html, option C (operator pick 2026-11-17, Fable's second round):
//   app/.../res/mipmap-xxxhdpi/ic_launcher_foreground.png (432 px), out/store-icon-512.png, and prints the
//   background gradient for res/drawable/ic_launcher_background.xml.
// Usage (from works/2026-11-09-catch, local server on 8766 serving C:/DC/DD/dot-lab): node tools/icon2-export.mjs <empty chrome profile dir> [A|B|C]
import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";

const [prof, opt = "C"] = process.argv.slice(2);
const here = new URL("../", import.meta.url);
const page = "http://127.0.0.1:8766/works/2026-11-09-catch/mockups/icon2.html";
const dbg = 9440 + Math.floor(Math.random() * 40);
const p = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe",
  ["--headless=new", "--disable-gpu", `--remote-debugging-port=${dbg}`, `--user-data-dir=${prof}`, page], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
try {
  let tg;
  for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${dbg}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => (ws.onopen = r));
  let id = 0;
  const ev = expr => new Promise(r => { const i = ++id; ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id === i) r(m.result?.result?.value); }; ws.send(JSON.stringify({ id: i, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true, awaitPromise: true } })); });
  let out;
  for (let i = 0; i < 40 && !out; i++) { out = await ev(`window.__exportIcons && window.__exportIcons(${JSON.stringify(opt)})`); if (!out) await sleep(500); }
  const png = u => Buffer.from(u.split(",")[1], "base64");
  const mip = new URL("app/app/src/main/res/mipmap-xxxhdpi/", here); mkdirSync(mip, { recursive: true });
  writeFileSync(new URL("ic_launcher_foreground.png", mip), png(out.fg));
  writeFileSync(new URL("out/store-icon-512.png", here), png(out.store));
  console.log("bg", out.bg.join(" "));
  ws.close();
} finally { p.kill(); }
