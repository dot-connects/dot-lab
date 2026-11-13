import { spawn, spawnSync } from "node:child_process"; import { writeFileSync } from "node:fs";
const [port, prof, out, page = "run.html"] = process.argv.slice(2), dbg = 9391, sleep = ms => new Promise(r => setTimeout(r, ms));
const p = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe", ["--headless=new", `--remote-debugging-port=${dbg}`, `--user-data-dir=${prof}`, "--use-angle=swiftshader", "--enable-unsafe-swiftshader", `http://127.0.0.1:${port}/${page}`], { stdio: ["ignore","ignore","pipe"] });
p.stderr.on("data", d => process.stderr.write(d)); p.on("exit", c => console.log("chrome exit", c));
try {
  let tg; for (let i = 0; i < 240 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${dbg}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => (ws.onopen = r));
  let id = 0; const pend = {}; ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
  const ev = expr => new Promise(r => { const i = ++id; pend[i] = m => r(m.result?.result?.value); ws.send(JSON.stringify({ id: i, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true } })); });
  for (let t = 0; t < 400; t++) {
    const o = JSON.parse((await ev("JSON.stringify({d:window.__done,e:window.__err,n:(window.__log||[]).length,o:!!window.__out})")) || "{}");
    if (o.o) { writeFileSync(out, Buffer.from((await ev("window.__out")).split(",")[1], "base64")); console.log("sheet", out); break; }
    if (o.e) { console.log("ERR", o.e); break; }
    if (o.d) { writeFileSync(out, await ev("JSON.stringify({log:window.__log,ms:window.__ms,skip:window.__skip})")); console.log("done", o.n); break; }
    if (t % 10 == 0) console.log("progress", o.n); await sleep(2000);
  }
} finally { spawnSync("taskkill", ["/T", "/F", "/PID", String(p.pid)]); }
