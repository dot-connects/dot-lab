// Fake-clock frame grabber for the reel's app scenes.
// node reel/render/render.mjs <page.html?query> <frames> <outDir> [port]
// Needs `python -m http.server <port> --bind 127.0.0.1 --directory C:/DC/DD/dot-lab` running (default 8766).
// Heavy-job rule (memory reference_heavy_job_rule.md): run it BelowNormal on two logical threads, Chrome inherits both:
//   cmd //c "start /b /wait /belownormal /affinity 30 node render.mjs <page> <frames> <outDir>"   (or /affinity C0)
// and claim the render slot with the other sessions first.
// Calls window.frame() once per frame (the page steps its own clock 1/30 s) and writes outDir/%04d.png,
// then outDir/events.json (sound cues). Headless Chrome, CPU only; ~1 frame/s on this laptop.
import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const [page, framesArg, outDir, port = "8766"] = process.argv.slice(2);
const N = +framesArg;
fs.mkdirSync(outDir, { recursive: true });
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe", DP = 9342;
const prof = fs.mkdtempSync(path.join(os.tmpdir(), "reel-chrome-"));
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${DP}`, `--user-data-dir=${prof}`, "--window-size=600,900",
  "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const errors = [];
try {
  let tg;
  for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${DP}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
  let id = 0; const pend = {};
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; return; }
    if (m.method === "Runtime.exceptionThrown") errors.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
    if (m.method === "Runtime.consoleAPICalled" && m.params.type === "error") errors.push(m.params.args.map(a => a.value ?? a.description).join(" "));
  };
  const cmd = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async expr => {
    const r = await cmd("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text);
    return r.result?.result?.value;
  };
  await cmd("Runtime.enable");
  await cmd("Page.navigate", { url: `http://127.0.0.1:${port}/works/2026-11-09-catch/reel/render/${page}` });
  for (let i = 0; i < 240; i++) { if (await ev("typeof window.ready === 'object'").catch(() => false)) break; await sleep(250); }
  await ev("window.ready");
  if (N < 0) {   // a tool page: window.cuts() -> [{name, <key>: dataURL, ...}], each data URL saved as <name>-<key>.png
    for (const o of await ev("window.cuts()")) {
      for (const [k, v] of Object.entries(o)) if (typeof v === "string" && v.startsWith("data:")) fs.writeFileSync(path.join(outDir, `${o.name}-${k}.png`), Buffer.from(v.split(",")[1], "base64"));
      fs.writeFileSync(path.join(outDir, `${o.name}.json`), JSON.stringify(Object.fromEntries(Object.entries(o).filter(([, v]) => !(typeof v === "string" && v.startsWith("data:"))))));
    }
  }
  if (N === 0) {   // a DOM screen (1 CSS px = 1 dp): one shot at the phone's 2.625 density = 1080 x 1920
    await cmd("Emulation.setDeviceMetricsOverride", { width: 411, height: 731, deviceScaleFactor: 2.625, mobile: true });
    await sleep(400);
    const s = await cmd("Page.captureScreenshot", { format: "png", clip: { x: 0, y: 0, width: 1080 / 2.625, height: 1920 / 2.625, scale: 1 } });
    fs.writeFileSync(path.join(outDir, "shot.png"), Buffer.from(s.result.data, "base64"));
  }
  const t0 = Date.now(), states = [];
  for (let f = 0; f < N; f++) {
    const r = await ev("window.frame()");
    fs.writeFileSync(path.join(outDir, String(f + 1).padStart(4, "0") + ".png"), Buffer.from(r.url.split(",")[1], "base64"));
    states.push([f + 1, r.t, r.st, r.cap, r.toast]);
    if (f % 30 === 0) console.log(`frame ${f + 1}/${N} t=${r.t} ${r.st} ${r.cap ?? ""} ${((Date.now() - t0) / 1000).toFixed(0)}s`);
  }
  if (await ev("typeof window.cut === 'function'")) {
    const u = await ev("window.cut()");
    if (u) fs.writeFileSync(path.join(outDir, "cut.png"), Buffer.from(u.split(",")[1], "base64"));
  }
  fs.writeFileSync(path.join(outDir, "events.json"), JSON.stringify({ ...(await ev("typeof window.events === 'function' ? window.events() : {}")), states, errors }, null, 1));
  console.log(errors.length ? `errors: ${errors.join(" | ")}` : "no page errors");
} finally {
  p.kill();
  await sleep(500);
  try { fs.rmSync(prof, { recursive: true, force: true }); } catch {}
}
