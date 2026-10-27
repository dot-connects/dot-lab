// node drive.mjs <outJson> <url> [<url2> ...]   (env FLAGS = extra chrome flags, space separated; THROTTLE = CDP CPU rate)
// Each url runs in a fresh tab of one headless Chrome; the page sets window.done and window.out ({ ..., files: {path: b64} }).
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
const [outJson, ...urls] = process.argv.slice(2);
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe", port = 9300 + Math.floor(Math.random() * 500);
const prof = fs.mkdtempSync(path.join(os.tmpdir(), "r5m-"));
const p = spawn(CH, ["--headless=new", "--remote-debugging-port=" + port, "--user-data-dir=" + prof, "--window-size=1000,800",
  ...(process.env.FLAGS || "").split(" ").filter(Boolean), "about:blank"], { stdio: "ignore" });
for (const s of ["SIGINT", "SIGTERM", "SIGHUP"]) process.on(s, () => { p.kill(); process.exit(1); });
console.error("chrome pid", p.pid);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
try {
  for (let i = 0; i < 80; i++) { try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); if (t.find(x => x.type === "page")) break; } catch {} await sleep(250); }
  for (const url of urls) {
    const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json();
    const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
    let id = 0; const pend = {}, logs = [];
    ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
      if (m.method === "Runtime.consoleAPICalled") logs.push(m.params.type + ": " + m.params.args.map(a => a.value ?? a.description).join(" ").slice(0, 300));
      if (m.method === "Runtime.exceptionThrown") logs.push("EXC " + JSON.stringify(m.params.exceptionDetails).slice(0, 300)); };
    const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
    const ev = async expr => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result?.result?.value;
    await send("Page.enable"); await send("Runtime.enable");
    if (process.env.THROTTLE) await send("Emulation.setCPUThrottlingRate", { rate: +process.env.THROTTLE });
    await send("Page.navigate", { url });
    let done = false;
    for (let i = 0; i < 2400; i++) { await sleep(500); if (await ev("window.done===true")) { done = true; break; } if (i % 60 === 59) console.error("progress", await ev("JSON.stringify(window.out && window.out.rows)")); }
    const o = done ? JSON.parse(await ev("JSON.stringify(window.out)")) : { error: "timeout" };
    const files = o.files || {}; delete o.files; o.url_q = url.split("?")[1]; o.logs = logs.slice(-20);
    for (const [n, b] of Object.entries(files)) { fs.mkdirSync(path.dirname(n), { recursive: true }); fs.writeFileSync(n, Buffer.from(b, "base64")); }
    results.push(o); console.log(JSON.stringify(o).slice(0, 3000));
    await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`).catch(() => {});
    ws.close();
  }
} finally { fs.writeFileSync(outJson, JSON.stringify(results, null, 1)); p.kill(); }
process.exit(0);
