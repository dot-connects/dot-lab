// Round 5 video bench: shoot 4 clips per side with the marker fake cameras (dev-capture.html), then render the moving
// strip (bg a / b) and measure both with fps.py. Headless Chrome over CDP; optional CPU throttle (fake slow phone).
// node bench.mjs <tag> capture <a|b> [throttle]        -> site/dev-media/r5/<tag>/<side><k>-{clip,raw,cut}
// node bench.mjs <tag> strip <bg> [throttle] [mode]     -> research/round5-video/out/<tag>-strip-<bg>-t<throttle>.mp4
import { spawn, execFileSync } from "node:child_process";
import fs from "node:fs";
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const SP = "C:/Users/ehdck/AppData/Local/Temp/claude/C--DC-DD/dd5f501f-171a-4f5a-9d24-ee18560f566d/scratchpad";
const HERE = new URL(".", import.meta.url).pathname.replace(/^\/([A-Z]:)/, "$1");
const SITE_DIR = HERE + "../../site/";
const SITE = "http://127.0.0.1:8801/works/2026-10-26-photo-booth/site/";
const [tag, what, arg, thr = "1", mode = ""] = process.argv.slice(2);
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function browser(cam) {
  const port = 9600 + Math.floor(Math.random() * 300), prof = `${SP}/prof-r5-${Date.now()}`;
  const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${prof}`,
    "--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream", ...(cam ? [`--use-file-for-fake-video-capture=${SP}/media/${cam}`] : []),
    "--autoplay-policy=no-user-gesture-required", "--enable-unsafe-webgpu", "--window-size=430,932", "about:blank"], { stdio: "ignore" });
  let ws;
  for (let i = 0; i < 120 && !ws; i++) {
    try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); const pg = t.find(x => x.type === "page");
      if (pg) { ws = new WebSocket(pg.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r); } } catch {}
    if (!ws) await sleep(250);
  }
  let id = 0; const pend = {}, logs = [];
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
    if (m.method === "Runtime.consoleAPICalled") logs.push(m.params.type + ": " + m.params.args.map(a => a.value ?? a.description).join(" "));
    if (m.method === "Runtime.exceptionThrown") logs.push("EXC: " + JSON.stringify(m.params.exceptionDetails).slice(0, 400)); };
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async expr => { const r = await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 600)); return r.result?.result?.value; };
  await send("Page.enable"); await send("Runtime.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 430, height: 932, deviceScaleFactor: 2, mobile: true });
  return { send, ev, logs, close: () => { p.kill(); } };
}
const until = async (b, expr, ms) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await b.ev(expr).catch(() => false)) return; await sleep(300); } throw new Error("timeout " + expr + "\n" + b.logs.slice(-10).join("\n")); };
const fps = (f, kind) => execFileSync("uv", ["run", "--no-project", "--with", "numpy", "python", HERE + "fps.py", f, kind]).toString().trim();
const b64 = `async blob => { const u8 = new Uint8Array(await blob.arrayBuffer()); let s = ""; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode(...u8.subarray(i, i + 0x8000)); return btoa(s); }`;

if (what === "capture") {
  const b = await browser(arg === "a" ? "r5-a-noflash.y4m" : "r5-b-pos.y4m");
  try {
    await b.send("Page.navigate", { url: SITE + `dev-capture.html?fast&still=0${mode ? "&" + mode : ""}` });
    await until(b, `document.readyState === "complete"`, 15000);
    await b.ev(`document.getElementById("go").click()`);
    // throttle only the shooting (camera + model load unthrottled took > 4 min at x4)
    if (+thr > 1) { await until(b, `!!(window.result && result.probe)`, 120000); await b.send("Emulation.setCPUThrottlingRate", { rate: +thr }); }
    await until(b, `window.done === true`, 480000);
    const r = await b.ev(`JSON.stringify({ segMs: result.segMs, err: result.error, loops: result.still?.cuts.map(c => c.loop), long: (result.shootLong || []).length, gaps: result.shootGaps })`);
    console.log("capture", arg, "throttle", thr, r);
    const dir = `${SITE_DIR}dev-media/r5/${tag}/`; fs.mkdirSync(dir, { recursive: true });
    const n = await b.ev(`cuts.length`);
    for (let k = 0; k < n; k++) for (const [key, ext] of [["clip", null], ["raw", "jpg"], ["cut", "png"]]) {
      const e = ext || ((await b.ev(`cuts[${k}].clipType`)).includes("mp4") ? "mp4" : "webm");
      fs.writeFileSync(`${dir}${arg}${k + 1}-${key}.${e}`, Buffer.from(await b.ev(`(${b64})(cuts[${k}].${key})`), "base64"));
    }
    for (let k = 1; k <= n; k++) { const f = fs.readdirSync(dir).find(x => x.startsWith(`${arg}${k}-clip`)); console.log(fps(dir + f, "clip")); }
  } finally { b.close(); }
} else if (what === "clipok") {      // capture.js clipOk on every clip of dev-media/r5/<tag>, plus an empty blob
  const b = await browser(null);
  try {
    await b.send("Page.navigate", { url: SITE + "dev-compose.html" }); await until(b, `window.devReady === true`, 20000);
    const dir = `dev-media/r5/${tag}/`, files = fs.readdirSync(SITE_DIR + dir).filter(f => f.includes("-clip"));
    console.log(await b.ev(`(async () => { const { clipOk } = await import("./js/capture.js"); const out = {};
      for (const f of ${JSON.stringify(files)}) { const bl = await (await fetch("${dir}" + f)).blob(); out[f] = [bl.size, await clipOk(new Blob([bl], { type: "video/mp4" }))]; }
      out.empty = await clipOk(new Blob([], { type: "video/mp4" })); return JSON.stringify(out); })()`));
  } finally { b.close(); }
} else if (what === "strip") {
  const b = await browser(null);
  try {
    await b.send("Page.navigate", { url: SITE + "dev-compose.html" });
    await until(b, `window.devReady === true`, 20000);
    const dir = `dev-media/r5/${tag}/`, files = fs.readdirSync(SITE_DIR + dir);
    const clip = (s, k) => files.find(x => x.startsWith(`${s}${k}-clip`));
    if (+thr > 1) await b.send("Emulation.setCPUThrottlingRate", { rate: +thr });
    const r = JSON.parse(await b.ev(`(async () => {
      const C = await import("./js/compose.js");
      const img = src => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
      const side = async s => ({ cuts: await Promise.all([1, 2, 3, 4].map(async k => ({ raw: await img("${dir}" + s + k + "-raw.jpg"),
        cut: await img("${dir}" + s + k + "-cut.png"), clip: await Promise.race([new Promise(r => setTimeout(r, 8000, null)), C.loadClip(URL.createObjectURL(await (await fetch("${dir}" + ({ a: ${JSON.stringify([1, 2, 3, 4].map(k => clip("a", k)))}, b: ${JSON.stringify([1, 2, 3, 4].map(k => clip("b", k)))} })[s][k - 1])).blob())).catch(() => null)])}))) });   // like app.js loadClips: a clip that will not load = null
      const a = await side("a"), b = await side("b");
      ${mode === "mr" ? "window.__noWebCodecs = true;" : ""}
      let mattes = null, hqMs = 0;
      if (${JSON.stringify(mode)} === "hq") { const M = await import("./js/clipmatte.js"), t = performance.now();
        mattes = await M.clipMattes([...a.cuts, ...b.cuts].map(c => c.clip)); hqMs = Math.round(performance.now() - t); }
      const t0 = performance.now();
      const { blob, type } = await C.recordMovingStrip({ a, b, bg: "${arg}", frame: "white", filter: "original", date: "2026.10.27", mattes });
      return JSON.stringify({ ms: Math.round(performance.now() - t0), hqMs, type, size: blob.size, stats: C.renderStats, b64: await (${b64})(blob) });
    })()`));
    const out = `${HERE}out/${tag}-strip-${arg}-t${thr}${mode ? "-" + mode : ""}.${r.type.includes("mp4") ? "mp4" : "webm"}`;
    fs.mkdirSync(HERE + "out", { recursive: true }); fs.writeFileSync(out, Buffer.from(r.b64, "base64"));
    console.log("strip", arg, "throttle", thr, mode, "renderMs", r.ms, "hqMs", r.hqMs, "type", r.type, "KB", Math.round(r.size / 1024), JSON.stringify(r.stats), b.logs.filter(l => /warn|error|EXC/.test(l)).slice(-3).join(" | "));
    console.log(fps(out, "strip"));
  } finally { b.close(); }
}
process.exit(0);
