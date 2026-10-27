// node drive.mjs <profileDir> <port> <outJson> <url> [<url2> ...]  (urls run sequentially in the SAME browser, fresh tab each)
import { spawn } from "node:child_process";
import fs from "node:fs";
const [prof, port, outJson, ...urls] = process.argv.slice(2);
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
const p = spawn(CH, ["--headless=new", "--remote-debugging-port=" + port, "--user-data-dir=" + process.cwd() + "/" + prof, "--user-agent=" + UA,
  "--enable-unsafe-webgpu", "--window-size=1000,800", "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
try {
  let ws0;
  for (let i = 0; i < 80; i++) { try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); if (t.find(x => x.type === "page")) break; } catch {} await sleep(250); }
  for (const url of urls) {
    const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json();
    const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
    let id = 0; const pend = {};
    ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
    const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
    const ev = async expr => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result?.result?.value;
    await send("Page.enable"); await send("Runtime.enable");
    await send("Page.navigate", { url });
    let done = false;
    for (let i = 0; i < 400; i++) { await sleep(500); if (await ev("window.done===true")) { done = true; break; } }
    const o = done ? JSON.parse(await ev("JSON.stringify(window.out)")) : { error: "timeout" };
    const files = o.files || {}; delete o.files; o.url_q = url.split("?")[1];
    for (const [n, b] of Object.entries(files)) fs.writeFileSync(n, Buffer.from(b, "base64"));
    results.push(o); console.log(JSON.stringify(o));
    await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`).catch(() => {});
    ws.close();
  }
} finally { fs.writeFileSync(outJson, JSON.stringify(results, null, 1)); p.kill(); }
process.exit(0);
