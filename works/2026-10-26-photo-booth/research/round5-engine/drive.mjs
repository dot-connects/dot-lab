// node eng-bench.mjs <url> <outdir>: headless Chrome, wait for window.result, write result.json + sheets (data URLs) as png
import { spawn } from "node:child_process";
import fs from "node:fs";
const [url, out] = process.argv.slice(2);
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const SP = "C:/Users/ehdck/AppData/Local/Temp/claude/C--DC-DD/dd5f501f-171a-4f5a-9d24-ee18560f566d/scratchpad";
const port = 9900 + Math.floor(Math.random() * 90);
fs.mkdirSync(out, { recursive: true });
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${SP}/prof-bench-${Date.now()}`, "--window-size=900,900", ...(process.env.Y4M ? ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream", `--use-file-for-fake-video-capture=${SP}/media/${process.env.Y4M}`, "--autoplay-policy=no-user-gesture-required"] : []), "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let ws;
for (let i = 0; i < 120 && !ws; i++) { try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); const pg = t.find(x => x.type === "page"); if (pg) { ws = new WebSocket(pg.webSocketDebuggerUrl); var opened = new Promise(r => ws.onopen = r); } } catch {} await sleep(250); }
console.log("ws", !!ws, port);
await opened;
console.log("open");
let id = 0; const pend = {};
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
  if (m.method === "Runtime.consoleAPICalled") console.log("console:", m.params.args.map(a => a.value ?? a.description).join(" ").slice(0, 300));
  if (m.method === "Runtime.exceptionThrown") console.log("EXC", JSON.stringify(m.params.exceptionDetails).slice(0, 400)); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
await send("Runtime.enable");
await send("Page.navigate", { url });
console.log("navigated");
const t0 = Date.now();
let res;
while (Date.now() - t0 < 900000) {
  const r = await send("Runtime.evaluate", { expression: "window.result && JSON.stringify(window.result)", returnByValue: true });
  if (r.result?.result?.value) { res = JSON.parse(r.result.result.value); break; }
  await sleep(1000);
}
if (!res) console.log("TIMEOUT");
else {
  for (const [k, v] of Object.entries(res.sheets || {})) fs.writeFileSync(`${out}/${k.replace(/\//g, "_")}.png`, Buffer.from(v.split(",")[1], "base64"));
  delete res.sheets;
  fs.writeFileSync(`${out}/result.json`, JSON.stringify(res, null, 1));
  console.log(JSON.stringify(res, null, 1));
}
p.kill(); process.exit(0);
