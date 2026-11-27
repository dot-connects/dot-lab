// Bakes the weather action frames into mockups/next7v4/img/wx3d/ (frames as WebP q85, review sheets as JPEG in sheets/).
// Usage (from works/2026-11-09-catch): node tools/bake-weather.mjs <port> <empty chrome profile dir> [ids] [actions]
// Needs a static server for C:/DC/DD/dot-lab on <port>. One headless Chrome (swiftshader) = one render slot.
import { spawn, spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync, unlinkSync } from "node:fs";
import { fileURLToPath } from "node:url";

const [port, prof, ids = "monggeul,nyangsom", actions = ""] = process.argv.slice(2);
const dir = new URL("../mockups/next7v4/img/wx3d/", import.meta.url), sheets = new URL("sheets/", dir);
mkdirSync(sheets, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const dbg = 9390 + Math.floor(Math.random() * 40);
const url = `http://127.0.0.1:${port}/works/2026-11-09-catch/tools/bake-weather.html?ids=${ids}${actions ? "&actions=" + actions : ""}`;
const p = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe",
  ["--headless=new", `--remote-debugging-port=${dbg}`, `--user-data-dir=${prof}-${dbg}`, "--use-angle=swiftshader", "--enable-unsafe-swiftshader", url],
  { stdio: "ignore" });
console.log("chrome pid", p.pid);
try {
  let tg;
  for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${dbg}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => (ws.onopen = r));
  let id = 0; const pend = {};
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
    if (m.method === "Runtime.exceptionThrown") { console.log("page error", m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text); process.exit(2); } };
  ws.send(JSON.stringify({ id: ++id, method: "Runtime.enable" }));
  const ev = expr => new Promise(r => { const i = ++id; pend[i] = m => r(m.result?.result?.value); ws.send(JSON.stringify({ id: i, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true } })); });
  const t0 = Date.now();
  let out;
  let seen = 0;
  while (!(out = await ev("window.__out")) && Date.now() - t0 < 30 * 60_000) {
    await sleep(3000); const n = await ev("window.__p || 0"); if (n !== seen) { seen = n; console.log(`${Math.round((Date.now() - t0) / 1000)} s: ${n} frames+sheets`); }
  }
  if (!out) throw new Error("timed out");
  for (const [name, u] of Object.entries(out)) {
    const buf = Buffer.from(u.split(",")[1], "base64");
    if (name.endsWith("-sheet")) { writeFileSync(new URL(`${name}.jpg`, sheets), buf); continue; }
    const png = fileURLToPath(new URL(`${name}.png`, dir));
    writeFileSync(png, buf);
    spawnSync("ffmpeg", ["-loglevel", "error", "-y", "-threads", "2", "-i", png, "-c:v", "libwebp", "-quality", "85", "-lossless", "0", png.replace(/\.png$/, ".webp")]);
    unlinkSync(png);
  }
  console.log(`wrote ${Object.keys(out).length} files in ${Math.round((Date.now() - t0) / 1000)} s`);
} catch (e) { console.log("error", String(e)); process.exitCode = 1; }
finally { p.kill(); }
