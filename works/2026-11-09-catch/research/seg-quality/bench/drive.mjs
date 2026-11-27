// node drive.mjs <port> <names,comma> : one headless Chrome, writes out/<name>_<key>.png + out/<name>.json
import { spawn } from "node:child_process";
import fs from "node:fs";
const [port, names] = process.argv.slice(2);
const OUT = new URL("./out/", import.meta.url); fs.mkdirSync(OUT, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const dbg = 9440 + Math.floor(Math.random() * 40);
const prof = process.env.TEMP + "\\segbench-prof-" + dbg;
const p = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe", ["--headless=new", `--remote-debugging-port=${dbg}`, `--user-data-dir=${prof}`,
  `http://127.0.0.1:${port}/run.html?n=${names}${process.env.NOESAM ? "&noesam" : ""}`], { stdio: "ignore" });
try {
  let tg;
  for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${dbg}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => (ws.onopen = r));
  let id = 0; const pend = {}; const errs = [];
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
    if (m.method === "Runtime.exceptionThrown") errs.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text); };
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  await send("Runtime.enable");
  const ev = async expr => (await send("Runtime.evaluate", { expression: expr, returnByValue: true })).result?.result?.value;
  const t0 = Date.now(); let seen = 0;
  while (Date.now() - t0 < 12 * 60_000) {
    const lg = (await ev("window.__log||[]")) || []; for (; seen < lg.length; seen++) console.log(lg[seen]);
    if (errs.length) { console.log("ERR", errs.join("\n")); break; }
    if (await ev("!!window.__done")) break;
    await sleep(1500);
  }
  for (const n of names.split(",")) {
    const R = await ev(`window.__res && window.__res[${JSON.stringify(n)}]`); if (!R) { console.log("missing", n); continue; }
    for (const k of ["A", "P", "B", "C1", "C2", "C3", "U", "E"]) if (R[k]) { fs.writeFileSync(new URL(`${n}_${k}.png`, OUT), Buffer.from(R[k].split(",")[1], "base64")); delete R[k]; }
    fs.writeFileSync(new URL(`${n}.json`, OUT), JSON.stringify(R));
  }
} finally { p.kill(); }
