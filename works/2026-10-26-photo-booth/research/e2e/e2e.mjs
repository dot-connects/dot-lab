// Round 8 e2e (moved from a scratchpad 10-31). Needs: `npx wrangler dev --port 8787` in worker/, a static server on 8805
// at the dot-lab root, fake-camera y4m files in SP/media. Usage: node e2e.mjs wide|tall film|cute [extra-query]
// Walk A then B through the photo booth app in two fresh headless Chrome profiles (CDP, 430x932 dpr 2, touch).
import { spawn } from "node:child_process";
import fs from "node:fs";
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const SP = "C:/Users/ehdck/AppData/Local/Temp/claude/C--DC-DD/dd5f501f-171a-4f5a-9d24-ee18560f566d/scratchpad";
const SHAPE = process.argv[2] || "wide";      // wide (가로) | tall (세로)
const UI = process.argv[3] === "cute" ? "cute" : "film";   // A's theme pick on screen 0 (Round 6)
const OUT = (process.env.OUTDIR || "C:/Users/ehdck/AppData/Local/Temp/claude/C--DC-DD/356ab672-22c9-4a08-bc57-eff3c83c8b2a/scratchpad/e2e") + "/e2e-" + SHAPE + "-" + UI + (process.env.TAG ? "-" + process.env.TAG : "");
const ORIGIN = process.env.ORIGIN || "http://127.0.0.1:8805", SITE = ORIGIN + "/works/2026-10-26-photo-booth/site/";
const MODE = SHAPE;
const EXTRA = process.argv[4] || "";
const N = +(process.env.N || 6);   // shots per side (Round 7: 6)
fs.mkdirSync(OUT, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

async function browser(port, who, y4m) {
  const prof = `${SP}/prof-booth-${who}-${Date.now()}`;
  const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${prof}`,
    "--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream", `--use-file-for-fake-video-capture=${SP}/media/${y4m}`,
    "--autoplay-policy=no-user-gesture-required", "--window-size=430,932", ...(process.env.FLAGS || "").split(" ").filter(Boolean), "about:blank"], { stdio: "ignore" });
  let ws;
  for (let i = 0; i < 120 && !ws; i++) {
    try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      const pg = t.find(x => x.type === "page"); if (pg) { ws = new WebSocket(pg.webSocketDebuggerUrl); var opened = new Promise(r => ws.onopen = r); break; } } catch {}
    await sleep(250);
  }
  await opened;
  let id = 0; const pend = {}, logs = [], reqs = {};
  ws.onmessage = e => { const m = JSON.parse(e.data);
    if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
    if (m.method === "Runtime.consoleAPICalled") logs.push(m.params.type + ": " + m.params.args.map(a => a.value ?? a.description).join(" "));
    // this worktree's own wrangler dev (8788): config.js (127.0.0.1 -> 8787) is served with the port swapped
    if (m.method === "Fetch.requestPaused") {
      const q = m.params;
      if (/\/js\/config\.js/.test(q.request.url)) fetch(q.request.url).then(r => r.text()).then(t => ws.send(JSON.stringify({ id: 900000 + Math.floor(Math.random() * 99999),
        method: "Fetch.fulfillRequest", params: { requestId: q.requestId, responseCode: 200, responseHeaders: [{ name: "Content-Type", value: "text/javascript" }],
          body: Buffer.from(t.replace("127.0.0.1:8787", "127.0.0.1:8787")).toString("base64") } })));
      else ws.send(JSON.stringify({ id: 900000 + Math.floor(Math.random() * 99999), method: "Fetch.continueRequest", params: { requestId: q.requestId } }));
    }
    if (m.method === "Network.loadingFailed") { const q = reqs[m.params.requestId]; console.log("NETFAIL", who, q, JSON.stringify(m.params).slice(0, 300)); }
    if (m.method === "Network.requestWillBeSent") reqs[m.params.requestId] = m.params.request.url.slice(-60);
    if (m.method === "Runtime.exceptionThrown") logs.push("EXC: " + JSON.stringify(m.params.exceptionDetails).slice(0, 400)); };
  const send = (method, params = {}) => new Promise(r => { const i = ++id;
    const t = setTimeout(() => { log("CDP timeout", method); r({}); }, 20000);
    pend[i] = m => { clearTimeout(t); r(m); }; ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async expr => { const r = await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result?.result?.value; };
  log(who, "connected");
  await send("Page.enable"); await send("Runtime.enable"); await send("Network.enable");
  await send("Page.addScriptToEvaluateOnNewDocument", { source: GLHOOK });
  await send("Network.setCacheDisabled", { cacheDisabled: true });
  await send("Fetch.enable", { patterns: [{ urlPattern: "*/js/config.js*", requestStage: "Request" }] });
  await send("Emulation.setDeviceMetricsOverride", { width: 430, height: 932, deviceScaleFactor: 2, mobile: true, ...(who === "b" ? { screenWidth: 412, screenHeight: 915 } : {}) });   // R8 D: B is another phone
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  await send("Browser.grantPermissions", { permissions: ["clipboardReadWrite", "clipboardSanitizedWrite", "videoCapture"], origin: ORIGIN });
  const shot = async (name, full = false) => {
    const r = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: full });
    fs.writeFileSync(`${OUT}/${MODE}-${name}.png`, Buffer.from(r.result.data, "base64")); log("shot", name);
  };
  const tap = async sel => {
    const r = await ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); e.scrollIntoView({block:"center"});
      const b = e.getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 }; })()`);
    await sleep(150);
    const pt = [{ x: r.x, y: r.y }];
    await send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pt });
    await send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await sleep(100);
  };
  const until = async (expr, ms = 60000, what = expr) => {
    const t0 = Date.now();
    while (Date.now() - t0 < ms) { if (await ev(expr).catch(() => false)) return; await sleep(200); }
    throw new Error("timeout: " + what + "\n" + logs.slice(-15).join("\n"));
  };
  const screen = () => ev(`document.body.dataset.screen`);
  const go = async url => { await send("Page.navigate", { url }); await until(`document.readyState === "complete" && !!document.body.dataset.screen`, 15000, "load " + url); };
  return { p, send, ev, shot, tap, until, screen, go, logs, close: () => p.kill() };
}

// every WebGL context the page makes, and every one Chrome takes away (too many live contexts -> oldest lost)
const GLHOOK = `(() => { window.__gl = { made: 0, lost: 0, where: [] };
  const hook = P => { const g = P.getContext; P.getContext = function (t, o) { const c = g.call(this, t, o);
    if (c && /webgl/.test(t) && !this.__glc) { this.__glc = 1; __gl.made++; this.addEventListener("webglcontextlost", () => { __gl.lost++; __gl.where.push(document.body.dataset.screen + ":" + __gl.made); }); }
    return c; }; };
  hook(HTMLCanvasElement.prototype); if (self.OffscreenCanvas) hook(OffscreenCanvas.prototype); })()`;
const GL = b => b.ev(`JSON.stringify(window.__gl)`);
// text contrast: every visible text node vs its painted background (solid colours only; text over photos skipped)
const CONTRAST = `(() => {
  const lum = c => { const [r,g,b] = c.map(v => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }); return .2126*r + .7152*g + .0722*b; };
  const parse = s => (s.match(/[\\d.]+/g) || []).map(Number);
  const out = [], seen = new Set();
  const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (w.nextNode()) {
    const n = w.currentNode, el = n.parentElement; if (!n.textContent.trim() || seen.has(el)) continue; seen.add(el);
    const r = el.getBoundingClientRect(); if (!r.width || el.closest("[hidden]") || getComputedStyle(el).visibility === "hidden") continue;
    if (el.closest("svg")) continue;
    const cs = getComputedStyle(el), fg = parse(cs.color);
    let bg = null, e = el, alphaFg = fg[3] ?? 1, op = 1, img = false;
    for (let x = el; x; x = x.parentElement) op *= +getComputedStyle(x).opacity;
    while (e) { const s = getComputedStyle(e); if (s.backgroundImage !== "none" && !/gradient/.test(s.backgroundImage)) { img = true; break; }
      const c = parse(s.backgroundColor); if (c.length && (c[3] ?? 1) > 0.5) { bg = c; break; }
      if (/gradient/.test(s.backgroundImage)) { const g = s.backgroundImage.match(/rgba?\\([^)]*\\)/); if (g) { bg = parse(g[0]); break; } }
      e = e.parentElement; }
    if (img || el.closest(".stage,.peek,.th")) { out.push({ t: n.textContent.trim().slice(0, 24), skip: "over-photo" }); continue; }
    bg ||= [255, 255, 255];
    const f = fg.slice(0, 3).map((v, i) => v * alphaFg * op + bg[i] * (1 - alphaFg * op));
    const L1 = lum(f), L2 = lum(bg.slice(0, 3)), ratio = (Math.max(L1, L2) + .05) / (Math.min(L1, L2) + .05);
    out.push({ t: n.textContent.trim().slice(0, 24), ratio: +ratio.toFixed(2), size: cs.fontSize });
  }
  return out;
})()`;
async function contrast(b, name) { await sleep(400);
  const r = await b.ev(CONTRAST);
  const bad = r.filter(x => x.ratio && x.ratio < 4.5), skipped = r.filter(x => x.skip);
  log(`contrast ${name}: ${r.length} texts, min ${Math.min(...r.filter(x => x.ratio).map(x => x.ratio))}`,
    bad.length ? "FAIL " + JSON.stringify(bad) : "ok", skipped.length ? "skipped(over photo): " + skipped.map(x => x.t).join(" | ") : "");
}
const RAILS = { a: ["찍기", "고르기", "보정", "보내기"], b: ["찍기", "고르기", "보정", "완성"], c: ["찍기", "고르기", "보정", "꾸미기"] };
async function hooks(b, name, flow, step) {
  const h = JSON.parse(await b.ev(`JSON.stringify({ ui: document.body.dataset.ui, flow: document.body.dataset.flow, step: document.body.dataset.step ?? null,
    screen: document.body.dataset.screen, ev: window.__bs ?? -1, head: !!document.querySelector(".screen:not([hidden]) > .uihead"),
    rail: document.querySelector(".screen:not([hidden]) > .uihead .rail u.on")?.textContent ?? null, railN: document.querySelectorAll(".screen:not([hidden]) > .uihead .rail u").length })`));
  const want = step == null ? null : String(step), label = step == null ? null : RAILS[flow][step];
  const railOk = UI === "film" ? (step == null ? h.railN === 0 : h.rail === label && h.railN === 4) : h.railN === 0;
  ok(`hooks ${name}`, h.ui === UI && h.flow === flow && h.step === want && h.head && railOk, JSON.stringify(h));
}
const BS = `(() => { window.__bs = 0; document.addEventListener("booth:screen", () => __bs++); return true; })()`;
const noOverflow = async (b, name) => { const w = await b.ev(`[document.documentElement.scrollWidth, innerWidth]`); if (w[0] > w[1]) log("H-SCROLL", name, w); };



const segState = `import(new URL("js/segment.js", location.href).href).then(m => JSON.stringify({ state: m.stats.still.state, loadMs: m.stats.still.loadMs, warmMs: m.stats.still.warmMs, err: m.stats.still.error, cached: m.stats.still.cached, fetchMs: m.stats.still.fetchMs, createMs: m.stats.still.createMs }))`;

async function camReady(b) {
  for (let k = 0; ; k++) {
    await b.until(`document.getElementById("camwait").hidden || document.body.dataset.screen === "msg"`, 90000, "camera+segmenter ready")
      .catch(async e => { log("STUCK:", await b.ev(`document.body.dataset.screen + " | " + document.getElementById("camwait").textContent + " | v " + document.getElementById("video").readyState + " " + document.getElementById("video").videoWidth + " paused " + document.getElementById("video").paused`)); throw e; });
    if (await b.screen() !== "msg" || k > 2) break;
    log("camera retry", k, await b.ev(`document.getElementById("msgTitle").textContent`)); await b.tap((await b.ev(`!document.getElementById("msgBtn2").hidden`)) ? "#msgBtn2" : "#msgBtn");
  }
  await sleep(800);
}


// ---- Round 6 flow walk ---------------------------------------------------------------------------------------------
const Q = "?fast" + EXTRA;
const R = {};             // results per item
const ok = (k, v, note = "") => { R[k] = (R[k] ? R[k] + " ; " : "") + (v ? "PASS" : "FAIL") + (note ? " " + note : ""); log(v ? "PASS" : "FAIL", k, note); };
const txt = (b, id) => b.ev(`document.getElementById(${JSON.stringify(id)}).textContent.trim()`);
const vis = (b, sel) => b.ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); if (!e) return false; for (let x = e; x; x = x.parentElement) if (x.hidden) return false; const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== "hidden"; })()`);
// flash + shutter-sound counters (AudioBufferSource = the synthesized shutter noise)
const HOOKS = `(() => { window.__fl = 0; window.__snd = 0;
  const f = document.getElementById("flash"); new MutationObserver(() => f.classList.contains("on") && __fl++).observe(f, { attributes: true });
  const o = AudioContext.prototype.createBufferSource; AudioContext.prototype.createBufferSource = function () { __snd++; return o.apply(this, arguments); };
  return true; })()`;
const CLIPS = `(async () => { const C = await import(new URL("js/compose.js", location.href).href);
  return JSON.stringify(await Promise.all(__booth.S.shots.map(async c => { const m = /mp4/.test(c.clip.type) ? C.readFmp4(await c.clip.arrayBuffer()) : null; return m ? m.samples.length : c.clip.size; }))); })()`;

// N shots; during shot 1's countdown: flip camera + mirror; between shot 2 and 3: flip back
async function shootAll(b, prefix) {
  await b.ev(HOOKS);
  log(prefix, "stop button present:", await b.ev(`!!document.querySelector(".stopbtn,[data-act=stop]")`));
  ok("15 no stop button " + prefix, !(await b.ev(`!!document.querySelector(".stopbtn,[data-act=stop]")`)));
  const front0 = await b.ev(`__booth.S.cam.camState.front`), mir0 = await b.ev(`__booth.S.cam.camState.mirror`);
  await b.tap(".shutter");
  await sleep(500);
  await b.until(`/^[2-5]$/.test(document.getElementById("count").textContent)`, 20000, "countdown");
  const cnt = await b.ev(`document.getElementById("count").textContent`);
  await b.tap('[data-act="flip"]');
  await b.until(`!__booth.S.flipP`, 15000, "flip done");
  const front1 = await b.ev(`__booth.S.cam.camState.front`);
  await b.tap('[data-act="mirror"]');
  const mir1 = await b.ev(`__booth.S.cam.camState.mirror`);
  await b.shot(`${prefix}-countdown-after-flip`);
  ok("15 flip during countdown " + prefix, front1 !== front0 && await b.ev(`__booth.S.busy`), `count was ${cnt}, front ${front0}->${front1}, mirror ->${mir1}, still busy`);
  ok("15 mirror during countdown " + prefix, mir1 !== (await b.ev(`!__booth.S.cam.camState.mirror`)) && await b.ev(`document.getElementById("video").classList.contains("mirror") === __booth.S.cam.camState.mirror`));
  await b.until(`__booth.S.shots.length === 2`, 60000, "2 shots");
  await sleep(100);
  await b.tap('[data-act="flip"]');      // between shots / right at the next countdown start
  await b.until(`!__booth.S.flipP`, 15000, "flip 2");
  await b.until(`__booth.S.shots.length === 3 && !document.getElementById("lastShot").hidden`, 60000, "3 shots");
  await b.shot(`${prefix}-shot4`);
  await b.until(`document.body.dataset.screen === "pick"`, 300000, "pick screen");
  const clips = JSON.parse(await b.ev(CLIPS));
  const fl = await b.ev(`__fl`), snd = await b.ev(`__snd`);
  ok(`15 ${N} shots continue after 2 switches ` + prefix, (await b.ev(`__booth.S.shots.length`)) === N && clips.every(n => n >= 5), "clip frames " + JSON.stringify(clips));
  ok("shutter flash+sound per shot " + prefix, fl >= N && snd >= 2 * N, `flash ${fl}, noise sources ${snd} (2 per shutter)`);
  await b.until(`!__booth.S.hqBusy`, 120000, "HQ of all").catch(e => log("HQ8", e.message.slice(0, 80)));
  const LIVE = `(document.getElementById("video").srcObject?.getTracks() || []).filter(t => t.readyState === "live").length`;
  ok("10 camera off on the pick " + prefix, (await b.ev(LIVE)) === 0, "live tracks " + await b.ev(LIVE));
  if (prefix.includes("b") && process.env.RESHOOT !== "0") {      // reshoot: the camera comes back, the live cut runs again
    await b.tap('#s-pick [data-act="reshoot"]');
    await b.until(`document.body.dataset.screen === "shoot" && document.getElementById("camwait").hidden && ${LIVE} === 1`, 30000, "camera back on reshoot");
    await sleep(1500); await b.shot(`${prefix}-reshoot-ready`);
    const lv = await b.ev(`(() => { const c = document.getElementById("liveCut"); return c.hidden ? "hidden" : c.width + "x" + c.height; })()`);
    await b.tap(".shutter");
    await b.until(`document.body.dataset.screen === "pick"`, 300000, "pick after reshoot");
    ok("10 reshoot: camera back, 6 new shots " + prefix, (await b.ev(`__booth.S.shots.length`)) === N && (await b.ev(LIVE)) === 0, "liveCut " + lv);
    await b.until(`!__booth.S.hqBusy`, 120000, "HQ after reshoot").catch(e => log("HQ8", e.message.slice(0, 80)));
  }
}

// cell pixels of the pick grid (cell j) as a small luminance signature
const CELLSIG = j => `(() => { const c = document.querySelectorAll("#pickGrid .pk")[${j}].querySelector("canvas"); if (!c) return null;
  const s = document.createElement("canvas"); s.width = 24; s.height = 16; const x = s.getContext("2d"); x.drawImage(c, 0, 0, 24, 16);
  return Array.from(x.getImageData(0, 0, 24, 16).data.filter((_, i) => i % 4 === 1)); })()`;
const sigDiff = (p, q) => p && q ? +(p.reduce((s, v, i) => s + Math.abs(v - q[i]), 0) / p.length).toFixed(1) : -1;

async function pickBgWalk(b, prefix, wantLabels) {
  await b.until(`document.querySelectorAll("#pickGrid .pk canvas").length === ${N}`, 60000, "grid painted");
  await hooks(b, prefix + " pick", prefix.includes("b") ? "b" : "a", 1); await contrast(b, prefix + " pick");
  await sleep(600);
  const labels = await b.ev(`[...document.querySelectorAll("#pickBg .pill")].map(p => p.textContent + (p.getAttribute("aria-pressed") === "true" ? "*" : "")).join(",")`);
  ok((prefix.includes("b") ? "6" : "1") + " pick bg toggle " + prefix, wantLabels.every(l => labels.includes(l)), labels);
  const sigs = {};
  for (const v of await b.ev(`[...document.querySelectorAll("#pickBg .pill")].map(p => p.dataset.bg)`)) {
    await b.tap(`#pickBg .pill[data-bg="${v}"]`);
    await b.until(`__booth.S.pickBg === "${v}"`, 5000, "bg " + v); await sleep(2500);
    sigs[v] = await b.ev(CELLSIG(1));
    await b.shot(`${prefix}-pick-bg-${v}`);
  }
  const ks = Object.keys(sigs), d = ks.slice(1).map(k => `${ks[0]}/${k}:${sigDiff(sigs[ks[0]], sigs[k])}`);
  ok("pick bg changes the cells " + prefix, ks.slice(1).every(k => sigDiff(sigs[ks[0]], sigs[k]) > 3), d.join(" "));
}

// the review canvas: snapshots by key, then the share of pixels that changed between two of them
const revSnap = async (b, k) => { await b.ev(`(() => { const c = document.getElementById("review"); (window.__rv ||= {})[${JSON.stringify(k)}] = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; return true; })()`); };
const REVDIFF = (k1, k2) => `(() => { const p = __rv[${JSON.stringify(k1)}], q = __rv[${JSON.stringify(k2)}]; if (!p || !q || p.length !== q.length) return JSON.stringify({ pct: -1, n: [p?.length, q?.length] });
  let n = 0, s = 0; for (let i = 0; i < p.length; i += 4) { const d = Math.abs(p[i] - q[i]) + Math.abs(p[i + 1] - q[i + 1]) + Math.abs(p[i + 2] - q[i + 2]); s += d; if (d > 30) n++; }
  return JSON.stringify({ mean: +(s / (p.length / 4) / 3).toFixed(2), pct: +(n / (p.length / 4) * 100).toFixed(2) }); })()`;
// tap 피부/갸름/눈 levels in a look box, then let the cells re-render
async function setLook(b, box, lv) {
  const rows = ["피부", "갸름", "눈"], names = ["안 함", "살짝", "많이"], keys = ["skin", "slim", "eye"];
  for (let k = 0; k < 3; k++) {
    // the review's tabs (retouch mockup 1): open the row's tab first; the done screen has plain rows
    const tab = `${box} .ltab[data-k="${keys[k]}"]`;
    if (await b.ev(`document.querySelector(${JSON.stringify(tab)})?.getAttribute("aria-pressed") === "false"`)) { await b.tap(tab); await sleep(200); }
    const sel = `${box} [aria-label="${rows[k]} ${names[lv[k]]}"]`;
    if (await b.ev(`document.querySelector(${JSON.stringify(sel)})?.getAttribute("aria-pressed") === "true"`)) continue;
    await b.tap(sel); await sleep(300);
  }
  await sleep(5000);
}
// the fake camera clip walks the person out of frame now and then: pick the 4 shots with the most person in them
// (cut-out alpha coverage), best first, so the strip order is not the shot order
const BEST4 = `(async () => { const cov = await Promise.all(__booth.S.shots.map(async c => { const b = await createImageBitmap(c.cut), x = Object.assign(document.createElement("canvas"), { width: 64, height: 48 }).getContext("2d");
  x.drawImage(b, 0, 0, 64, 48); const d = x.getImageData(0, 0, 64, 48).data; let n = 0; for (let k = 3; k < d.length; k += 4) n += d[k] > 128; return n; }));
  return JSON.stringify(cov.map((n, j) => [n, j]).sort((p, q) => q[0] - p[0]).slice(0, 4).map(p => p[1]).sort((p, q) => q - p)); })()`;
async function pickAndUpload(b, prefix, order) {
  if (!order) { order = JSON.parse(await b.ev(BEST4)); log(prefix, "picked by coverage", JSON.stringify(order)); }
  for (const j of order) await b.tap(`#pickGrid .pk:nth-child(${j + 1})`);
  await b.tap("#pickDone");
  await b.until(`document.body.dataset.screen === "shoot" && !document.getElementById("review").hidden`, 30000, "review");
  await sleep(1500); await b.shot(`${prefix}-review`);
  await hooks(b, prefix + " review", prefix.includes("b") ? "b" : "a", 2); await contrast(b, prefix + " review");
  const side = prefix.includes("b") ? "b" : "a";
  // #3: the pick's background buttons on the retouch screen too, the pick's choice carried over, the cells redraw
  const pickBg0 = await b.ev(`__booth.S.pickBg`);
  const rl = await b.ev(`[...document.querySelectorAll("#revBg .pill")].map(p => p.dataset.bg + (p.getAttribute("aria-pressed") === "true" ? "*" : "")).join(",")`).catch(() => "");
  ok("3 retouch bg buttons " + side, rl.split(",").length === (side === "b" ? 3 : 2) && rl.includes(pickBg0 + "*"), `${rl} (pick: ${pickBg0})`);
  if (rl) {
    await revSnap(b, "bg0");
    const other = rl.split(",").map(x => x.replace("*", "")).find(v => v !== pickBg0);
    await b.tap(`#revBg .pill[data-bg="${other}"]`); await sleep(2500); await revSnap(b, "bg1");
    await b.shot(`${prefix}-review-bg-${other}`);
    const d = JSON.parse(await b.ev(REVDIFF("bg0", "bg1")));
    ok("3 retouch bg redraws the cells " + side, d.pct > 3 && await b.ev(`__booth.S.pickBg === ${JSON.stringify(other)}`), `${pickBg0} -> ${other}: ${JSON.stringify(d)}`);
    await b.tap(`#revBg .pill[data-bg="${pickBg0}"]`); await sleep(2500);
  }
  // #8: the face retouch changes the review cells (both sides): look 0/0/0 vs 2/2/2, pixel diff
  await b.until(`__booth.S.beautyReady === true`, 60000, "beauty ready").catch(e => log("beauty", e.message.slice(0, 80)));
  await setLook(b, "#lookMine", [0, 0, 0]); await revSnap(b, "l0");
  await setLook(b, "#lookMine", [2, 2, 2]); await revSnap(b, "l2");
  await b.shot(`${prefix}-review-look222`);
  const dl = JSON.parse(await b.ev(REVDIFF("l0", "l2")));
  ok("8 retouch changes the review " + side, dl.pct > 0.5, JSON.stringify(dl) + " gl " + await GL(b));
  // retouch mockup 1: holding 원본 보기 shows the four without the look, letting go brings it back
  const ob = await b.ev(`(() => { const e = document.getElementById("revOrig"); if (e.hidden) return null; const r = e.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
  if (!ob) ok("R7 원본 보기 shown with a look " + side, false, "hidden");
  else {
    await b.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [ob] }); await sleep(2500); await revSnap(b, "orig");
    const held = await b.ev(`document.getElementById("revOrig").getAttribute("aria-pressed")`);
    await b.shot(`${prefix}-review-orig-held`);
    await b.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] }); await sleep(2500); await revSnap(b, "back");
    const d0 = JSON.parse(await b.ev(REVDIFF("l0", "orig"))), d2 = JSON.parse(await b.ev(REVDIFF("l2", "back")));
    ok("R7 원본 보기 hold = no look, release = look " + side, held === "true" && d0.pct < dl.pct / 3 && d2.pct < dl.pct / 3,
      `held ${held}; orig vs l0 ${JSON.stringify(d0)}; back vs l2 ${JSON.stringify(d2)}; l0 vs l2 ${dl.pct}`);
  }
  await setLook(b, "#lookMine", [1, 0, 0]);
  await b.tap('#shootDone [data-act="upload"]'); await sleep(400);
  if (await b.ev(`!document.getElementById("hqAsk").hidden`)) await b.tap('[data-act="hqNow"]');
  await b.until(`document.body.dataset.screen === "share"`, 120000, "share after upload");
}

// #7: on the done screen draw cell 0 with bg "a" with B and with B removed (transparent person): B must change pixels,
// and A's cut must still be on top (A-only region unchanged vs B absent)
const LAYER7 = `(async () => {
  const C = await import(new URL("js/compose.js", location.href).href), D = __booth.D, R = { x: 0, y: 0, w: 552, h: 382 };
  const mk = () => Object.assign(document.createElement("canvas"), { width: 552, height: 382 });
  const px = c => c.getContext("2d").getImageData(0, 0, 552, 382).data;
  const empty = Object.assign(document.createElement("canvas"), { width: 64, height: 48 });
  const noB = { cuts: D.media.b.cuts.map(c => ({ raw: c.raw, cut: empty, clip: null })) };
  const out = {};
  for (const bg of ["a", "b", "booth-pink"]) {
    const c1 = mk(), c2 = mk();
    C.drawCell(c1.getContext("2d"), R, 0, { a: D.media.a, b: D.media.b, bg, pairs: D.pairs });
    C.drawCell(c2.getContext("2d"), R, 0, { a: D.media.a, b: noB, bg: bg === "b" ? "booth-pink" : bg, pairs: D.pairs });
    const p = px(c1), q = px(c2); let n = 0;
    for (let k = 0; k < p.length; k += 4) if (Math.abs(p[k] - q[k]) + Math.abs(p[k + 1] - q[k + 1]) + Math.abs(p[k + 2] - q[k + 2]) > 60) n++;
    out[bg] = +(n / (p.length / 4) * 100).toFixed(1);
    if (bg === "a") out.png = c1.toDataURL("image/png");
  }
  return JSON.stringify(out); })()`;


// #9: every control of the decorate 보정 tab must keep both people in every cell. Presence of a side = % of cell pixels
// that change when that side's cut-out is swapped for an empty one (same bg / look / own / frame), on a preset bg.
const PRES = `(async () => {
  const C = await import(new URL("js/compose.js", location.href).href), D = __booth.D, S = __booth.S;
  const V = s => D.view?.[s] || D.media[s];
  window.__noMe ||= new WeakMap();
  const empty = window.__empty ||= Object.assign(document.createElement("canvas"), { width: 64, height: 48 });
  const strip = document.getElementById("stripImg"), W = strip.width, H = strip.height;
  const sp = strip.getContext("2d").getImageData(0, 0, W, H).data;
  const rs = C.cellRects(D.frame, S.shape), out = {};
  for (const side of ["a", "b"]) {
    const src = V(side); let nm = __noMe.get(src);
    if (!nm) __noMe.set(src, nm = { cuts: src.cuts.map(c => ({ raw: c.raw, cut: empty, clip: null })) });
    const o = { shape: S.shape, a: side === "a" ? nm : V("a"), b: side === "b" ? nm : V("b"), bg: D.bg.id, frame: D.frame, filter: D.filter, date: "2026.10.28",
      caption: D.caption.trim(), font: D.font, layout: D.layout, pairs: D.pairs, own: D.own, outline: D.outline, stickers: null };
    const ref = await C.stripCanvas(o), rp = ref.getContext("2d").getImageData(0, 0, W, H).data;
    out[side] = rs.map(r => { let n = 0, t = 0;
      for (let y = Math.ceil(r.y); y < r.y + r.h; y += 2) for (let x = Math.ceil(r.x); x < r.x + r.w; x += 2) { const k = (y * W + x) * 4; t++;
        if (Math.abs(sp[k] - rp[k]) + Math.abs(sp[k + 1] - rp[k + 1]) + Math.abs(sp[k + 2] - rp[k + 2]) > 60) n++; }
      return +(n / t * 100).toFixed(1); });
  }
  return JSON.stringify(out); })()`;
async function settle(b) {
  await sleep(700);
  for (let k = 0; k < 60; k++) {
    const s1 = await b.ev(`__booth.D.seq + "|" + document.getElementById("making").hidden`); await sleep(500);
    const s2 = await b.ev(`__booth.D.seq + "|" + document.getElementById("making").hidden`);
    if (s1 === s2 && s2.endsWith("true")) return;
  }
}
const click = (b, sel) => b.ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); e.scrollIntoView({ block: "center" }); e.click(); return true; })()`);
async function lookWalk(b, who, prefix) {
  const me = await b.ev(`__booth.S.side || new URLSearchParams(location.search).get("me") || (JSON.parse(localStorage.getItem("booth:" + __booth.S.id) || "{}").role)`);
  await click(b, '[data-tab="bg"]'); await sleep(400);
  const pink = await b.ev(`(async () => { const C = await import(new URL("js/compose.js", location.href).href); return C.BACKGROUNDS.find(x => x.id === "booth-pink").label; })()`);
  await b.ev(`[...document.querySelectorAll("#bgChips .tile")].find(t => t.textContent.trim() === ${JSON.stringify(pink)}).click()`);
  await settle(b);
  await click(b, '[data-tab="look"]'); await settle(b);
  const own = await vis(b, '#lookWho [data-who="2"]');   // deco mockup 3: 내 크기·위치 is the third 보정 segment
  ok("7 own size/place only for B " + who, who === "b" ? own : !own, `ownBox ${own ? "shown" : "hidden"}`);
  const bad = [], seen = [];
  const check = async name => { await settle(b); const p = JSON.parse(await b.ev(PRES)); const fr = me === "a" ? "b" : "a", lo = Math.min(...p[me]), lf = Math.min(...p[fr]);
    seen.push(`${name}:${lo}/${lf}`); if (lo < 2 || lf < 2) { bad.push(`${name} me ${p[me]} friend ${p[fr]}`); await b.shot(`${prefix}-look-gone-${bad.length}`); } };
  await check("start");
  // deco mockup 3: one button per row steps 안 함 → 살짝 → 많이; walk each row to 많이, 살짝, 안 함 in turn
  for (const round of [0, 1]) for (const [n, box] of [[0, "#lookA"], [1, "#lookB"]]) {
    await click(b, `#lookWho [data-who="${n}"]`); await sleep(200);
    for (const row of ["skin", "slim", "eye"]) for (const lv of [2, 1, 0]) {
      const sel = `${box} .lcyc[data-k="${row}"]`;
      if (!(await b.ev(`!!document.querySelector(${JSON.stringify(sel)})`))) continue;
      for (let g = 0; g < 3 && +(await b.ev(`document.querySelector(${JSON.stringify(sel)}).dataset.v`)) !== lv; g++) { await click(b, sel); await check(`${round}${box}-${row}-${lv}`); }
    }
  }
  if (own) {
    await click(b, '#lookWho [data-who="2"]'); await sleep(200);
    const slide = async v => { await b.ev(`(() => { const s = document.getElementById("ownSize"); s.value = ${v}; s.dispatchEvent(new Event("input", { bubbles: true })); s.dispatchEvent(new Event("change", { bubbles: true })); return true; })()`); };
    await slide(60); await check("size60"); await slide(140); await check("size140");
    for (let j = 0; j < 5; j++) { await click(b, `#ownCells .pill:nth-child(${j + 1})`); await slide(70 + j * 15); await check(`cell${j}-size`); }
    await click(b, "#ownReset"); await check("reset");
    const c0 = JSON.parse(await b.ev(`(async () => { const C = await import(new URL("js/compose.js", location.href).href), r = C.cellRects(__booth.D.frame, __booth.S.shape)[1];
      document.getElementById("preview").scrollIntoView({ block: "center" }); await new Promise(f => setTimeout(f, 300));
      const R = document.getElementById("previewIn").getBoundingClientRect(), k = R.width / document.getElementById("stripImg").width;
      return JSON.stringify({ x: R.left + (r.x + r.w / 2) * k, y: R.top + (r.y + r.h / 2) * k }); })()`));
    await b.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [c0] });
    for (let s = 1; s <= 6; s++) { await b.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: c0.x + s * 8, y: c0.y + s * 3 }] }); await sleep(60); }
    await b.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await check("drag"); await click(b, "#ownReset"); await check("reset2");
  }
  await b.shot(`${prefix}-look-tab`);
  ok("9 보정 tab keeps both people " + who, !bad.length, (bad.length ? "GONE " + bad.join(" | ") + " ; " : "") + `${seen.length} steps: ` + seen.join(" ") + " gl " + await GL(b));
}


// #6: the hint chip over the camera: never shown empty, its text readable on the chip
const HINT = `(() => { const c = document.getElementById("hintChip"), t = document.getElementById("hintText");
  const shown = !c.hidden && getComputedStyle(c).display !== "none" && c.getBoundingClientRect().width > 0;
  const lum = s => { const [r, g, b] = (s.match(/[\\d.]+/g) || []).slice(0, 3).map(Number).map(v => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }); return .2126 * r + .7152 * g + .0722 * b; };
  const L1 = lum(getComputedStyle(t).color), L2 = lum(getComputedStyle(c).backgroundColor);
  return JSON.stringify({ shown, text: t.textContent, ratio: +((Math.max(L1, L2) + .05) / (Math.min(L1, L2) + .05)).toFixed(2), color: getComputedStyle(t).color }); })()`;
async function hintCheck(b, who) {
  const h = JSON.parse(await b.ev(HINT));
  ok("6 hint chip never empty / unreadable " + who, !h.shown || (h.text.trim() && h.ratio >= 4.5), JSON.stringify(h));
}

const DUR = `(async () => { const C = await import(new URL("js/compose.js", location.href).href), v = __booth.D.video;
  const m = /mp4/.test(v.type) ? C.readFmp4(await v.blob.arrayBuffer()) : null;
  const el = document.createElement("video"); el.src = URL.createObjectURL(v.blob); el.muted = true;
  await new Promise(r => { el.onloadedmetadata = r; el.onerror = r; setTimeout(r, 5000); });
  return JSON.stringify({ type: v.type, size: v.blob.size, dur: el.duration, wh: [el.videoWidth, el.videoHeight], samples: m?.samples?.length, ts: m && m.timescale }); })()`;

const KAKAO_AND = "Mozilla/5.0 (Linux; Android 14; SM-S918N Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.102 Mobile Safari/537.36;KAKAOTALK 2610420";
const KAKAO_IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.8.5";
const THREADS_AND = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36 Barcelona 354.0.0.48.95 Android";
const NAVER_IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 NAVER(inapp; search; 2000; 12.10.3; 15PRO)";
const WV_AND = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36";

// ---- #8 crop check: pause the camera, screenshot the crop box, compare with what the composer puts in the cell ----
// ref(kind, shift): "video" = the raw still capture.js stores (frameRect + fit + mirror) cropped by compose's cellCrop;
// "ghost" = A's cut-out of this slot cropped the same way. shift = a deliberately wrong crop (proves the metric bites).
const REF = (kind, shift) => `(async () => {
  const C = await import(new URL("js/compose.js", location.href).href), S = __booth.S, cam = S.cam, v = document.getElementById("video");
  let src, w, h;
  if (${JSON.stringify(kind)} === "video") {
    const fr = cam.frameRect(v), tall = fr.sw < fr.sh;
    w = tall ? Math.floor(Math.min(fr.sw, 720) / 24) * 24 : Math.floor(Math.min(fr.sw, 960) / 8) * 8; h = tall ? w * 4 / 3 : w * 3 / 4;
    src = Object.assign(document.createElement("canvas"), { width: w, height: h });
    const x = src.getContext("2d"), k = w / fr.sw;
    x.translate(-fr.sx * k, -fr.sy * k);
    if (cam.camState.mirror) { x.translate(v.videoWidth * k, 0); x.scale(-1, 1); }
    x.drawImage(v, 0, 0, v.videoWidth * k, v.videoHeight * k);
  } else { src = document.getElementById("ghost"); w = src.naturalWidth; h = src.naturalHeight; }
  const r = C.cropRect(w, h, C.cellAspect(S.shape)); r.sy *= 0.25;                     // compose cellCrop
  if (${!!shift}) { if (r.sx === 0 && r.sy === 0) { r.sx = w * 0.06; r.sw = w - r.sx; r.sh = r.sw / C.cellAspect(S.shape); } else r.sy = (h - r.sh) / 2; }
  const st = document.getElementById("stage").getBoundingClientRect(), b = S.cropBox, d = devicePixelRatio;
  const out = Object.assign(document.createElement("canvas"), { width: Math.round(b.w * d), height: Math.round(b.h * d) });
  out.getContext("2d").drawImage(src, r.sx, r.sy, r.sw, r.sh, 0, 0, out.width, out.height);
  return JSON.stringify({ png: out.toDataURL("image/png"), clip: { x: st.x + b.x, y: st.y + b.y, width: b.w, height: b.h }, cell: [r.sw, r.sh], src: [w, h], box: [b.w, b.h] });
})()`;
// mean abs RGB difference, inset 6 css px; alphaOnly = only where the reference is opaque (A's cut-out)
const DIFF = (shot, ref, alphaOnly) => `(async () => {
  const ld = s => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = s; });
  const [a, b] = await Promise.all([ld(${JSON.stringify(shot)}), ld(${JSON.stringify(ref)})]);
  const w = Math.min(a.width, b.width), h = Math.min(a.height, b.height), px = i => { const c = Object.assign(document.createElement("canvas"), { width: w, height: h }), x = c.getContext("2d"); x.drawImage(i, 0, 0, w, h); return x.getImageData(0, 0, w, h).data; };
  const p = px(a), q = px(b), m = Math.round(6 * devicePixelRatio);
  let s = 0, n = 0;
  for (let y = m; y < h - m; y++) for (let x = m; x < w - m; x++) { const k = (y * w + x) * 4; if (${!!alphaOnly} && q[k + 3] < 250) continue;
    s += Math.abs(p[k] - q[k]) + Math.abs(p[k + 1] - q[k + 1]) + Math.abs(p[k + 2] - q[k + 2]); n++; }
  return JSON.stringify({ diff: +(s / Math.max(1, n) / 3).toFixed(2), n, w, h, shotW: a.width, refW: b.width });
})()`;
async function cropCheck(b, name, kind) {
  // pause the camera and hide everything over the photo but the layer under test
  await b.ev(`(() => { const v = document.getElementById("video"); v.pause();
    for (const e of document.querySelectorAll("#stage > *:not(#video):not(#frame), #frame > *:not(#crop)" + (${JSON.stringify(kind)} === "ghost" ? "" : ", #ghost"))) { if (${JSON.stringify(kind)} === "ghost" && e.id === "ghost") continue; e.dataset.vis = e.style.visibility; e.style.visibility = "hidden"; }
    if (${JSON.stringify(kind)} === "ghost") v.style.visibility = "hidden";
    return true; })()`);
  await sleep(400);
  const R = JSON.parse(await b.ev(REF(kind, false))), Rx = JSON.parse(await b.ev(REF(kind, true)));
  const s = await b.send("Page.captureScreenshot", { format: "png", clip: { ...R.clip, scale: 1 } });
  const shot = "data:image/png;base64," + s.result.data;
  fs.writeFileSync(`${OUT}/${MODE}-crop-${name}-screen.png`, Buffer.from(s.result.data, "base64"));
  fs.writeFileSync(`${OUT}/${MODE}-crop-${name}-cell.png`, Buffer.from(R.png.split(",")[1], "base64"));
  const d0 = JSON.parse(await b.ev(DIFF(shot, R.png, kind === "ghost"))), d1 = JSON.parse(await b.ev(DIFF(shot, Rx.png, kind === "ghost")));
  ok(`8 crop = cell (${name})`, d0.diff < 10 && d1.diff > d0.diff * 2, `diff ${d0.diff} (n ${d0.n}) vs wrong crop ${d1.diff}; cell src ${R.cell.map(Math.round)} of ${R.src}, box ${R.box.map(Math.round)}`);
  await b.ev(`(() => { for (const e of document.querySelectorAll("[data-vis]")) { e.style.visibility = e.dataset.vis; delete e.dataset.vis; }
    const v = document.getElementById("video"); v.style.visibility = ""; v.play(); return true; })()`);
}

const WANT = SHAPE === "tall" ? { W: 1800, H: 800 } : { W: 600, H: 1800 };
const PHOTO_WH = `(async () => { const b = await createImageBitmap(__booth.D.photo); return JSON.stringify({ w: b.width, h: b.height }); })()`;
async function saveBlob(b, expr, name) {
  const u = await b.ev(`(async () => { const bl = ${expr}; return await new Promise(r => { const f = new FileReader(); f.onload = () => r(f.result); f.readAsDataURL(bl); }); })()`);
  fs.writeFileSync(`${OUT}/${name}`, Buffer.from(u.split(",")[1], "base64")); log("saved", name);
}

const A = await browser(9420 + Math.floor(Math.random() * 500), "a", "person-a-noflash.y4m");
let link;
try {
  await A.go(SITE + Q); await sleep(1200);
  await A.ev(BS);
  await A.until(`document.body.dataset.screen === "theme"`, 10000, "theme screen");
  ok("ui screen 0 first", (await A.screen()) === "theme" && await vis(A, '.tcard[data-ui="film"]') && await vis(A, '.tcard[data-ui="cute"]'), await txt(A, "s-theme"));
  await A.shot("000-a-theme"); await contrast(A, "theme");
  await A.tap(`.tcard[data-ui="${UI}"]`);
  await A.until(`document.body.dataset.screen === "landA"`, 10000, "landA after theme");
  ok("ui pick sets body.dataset.ui", await A.ev(`document.body.dataset.ui === ${JSON.stringify(UI)} && __booth.S.ui === ${JSON.stringify(UI)}`), await A.ev(`document.body.dataset.ui`));
  await hooks(A, "a landA", "a", null);
  await sleep(1200); await A.shot("001-a-landA");
  ok("fb3 no camera-allow note (A)", !(await A.ev(`!!document.querySelector(".camnote")`)));
  ok("fb7 trust text", await A.ev(`[...document.querySelectorAll("#s-landA .trust")].map(t => t.textContent.trim()).join("|")`) === "D:OT lab · 사진은 7일 동안만 다운 가능해요", await A.ev(`document.querySelector("#s-landA .trust")?.textContent`));
  ok("fb1 no delete button anywhere", await A.ev(`!document.querySelector('[data-act="del"], .delask, #del')`));
  // #2: "‹ 테마" on A's cover and on the mode pick goes back to the theme pick (only before shooting)
  const OTHER = UI === "film" ? "cute" : "film";
  const back = async from => {
    const sel = `#s-${from} [data-act="toTheme"]`, there = await vis(A, sel);
    if (there) { await A.tap(sel); await A.until(`document.body.dataset.screen === "theme"`, 5000, "theme from " + from).catch(() => {}); }
    ok("2 ‹ 테마 on " + from, there && (await A.screen()) === "theme", there ? `"${await A.ev(`document.querySelector(${JSON.stringify(sel)}).textContent`)}" -> ${await A.screen()}` : "no button");
    return there;
  };
  if (await back("landA")) {
    await A.tap(`.tcard[data-ui="${OTHER}"]`); await A.until(`document.body.dataset.screen === "landA"`, 5000, "landA other ui");
    ok("2 theme can change after going back", await A.ev(`document.body.dataset.ui === ${JSON.stringify(OTHER)}`), await A.ev(`document.body.dataset.ui`));
    await A.tap(`#s-landA [data-act="toTheme"]`); await A.until(`document.body.dataset.screen === "theme"`, 5000, "theme again");
    await A.tap(`.tcard[data-ui="${UI}"]`); await A.until(`document.body.dataset.screen === "landA"`, 5000, "landA again");
  }
  await A.tap('[data-act="startA"]');
  await A.until(`document.body.dataset.screen === "mode"`, 10000, "mode screen");
  if (await back("mode")) {
    await A.tap(`.tcard[data-ui="${UI}"]`); await A.until(`document.body.dataset.screen === "landA"`, 5000, "landA after mode back");
    await A.tap('[data-act="startA"]'); await A.until(`document.body.dataset.screen === "mode"`, 10000, "mode screen 2");
  }
  ok("2 ui after the back trips", await A.ev(`document.body.dataset.ui === ${JSON.stringify(UI)}`), await A.ev(`document.body.dataset.ui`));
  await A.shot("00-a-mode"); await hooks(A, "a mode", "a", 0); await contrast(A, "mode");
  await A.tap(`#modes .mode[data-shape="${SHAPE}"]`); await sleep(300);
  ok("9 mode pick", await A.ev(`__booth.S.shape === ${JSON.stringify(SHAPE)} && document.querySelector('#modes .mode[data-shape="${SHAPE}"]').getAttribute("aria-checked") === "true"`), await txt(A, "modeGoText"));
  await A.shot("00-a-mode-picked");
  await A.tap('[data-act="modeGo"]'); await camReady(A);
  await A.shot("01-a-shoot-ready"); await hooks(A, "a shoot", "a", 0); await contrast(A, "a shoot");
  await hintCheck(A, "a");
  ok("2 no theme back once shooting", !(await vis(A, '#s-shoot [data-act="toTheme"]')));
  const camA = JSON.parse(await A.ev(`JSON.stringify({ video: [document.getElementById("video").videoWidth, document.getElementById("video").videoHeight], fr: __booth.S.cam.frameRect(document.getElementById("video")), crop: __booth.S.cropBox })`));
  log("A camera", JSON.stringify(camA));
  await cropCheck(A, "a-video", "video");
  await shootAll(A, "02-a");
  await pickBgWalk(A, "03-a", ["배경 켜기", "배경 끄기"]);
  await pickAndUpload(A, "04-a", [5, 0, 3, 2]);
  await sleep(600); await A.shot("05-a-share-sent"); await hooks(A, "a share", "a", 3); await contrast(A, "a share");
  link = await A.ev(`location.href`);
  const opt = (await (await fetch("http://127.0.0.1:8787/s/" + new URL(link).searchParams.get("s"))).json()).opts;
  ok("7 new links are never movable (Worker then refuses layout.json, 403)", opt?.movable === false, JSON.stringify(opt));
  ok("ui rides in A's link", UI === "cute" ? /[?&]ui=cute(&|$)/.test(link) : !/ui=/.test(link), link);
  await A.go(link); await sleep(1500); await A.shot("06-a-own-link-waiting"); await hooks(A, "a wait", "a", 3);
  ok("2 A own link: wait screen", (await A.screen()) === "share" && /아직/.test(await txt(A, "shareTitle")) && await vis(A, "#recheck") && await vis(A, "#testB"),
    `title "${await txt(A, "shareTitle")}", recheck + "${await txt(A, "testB")}" visible`);
  await contrast(A, "a waiting");
  log("link", link);

  const B = await browser(10020 + Math.floor(Math.random() * 500), "b", "person-b-pos.y4m");
  try {
    await B.go(link + "&fast" + EXTRA);
    ok("B ui before paint (link query)", await B.ev(`document.body.dataset.ui === ${JSON.stringify(UI)}`), await B.ev(`document.body.dataset.ui`));
    await sleep(1500);
    // B's page with the bare ?s= link (no query): the Worker's opts.ui decides
    await B.go(link.replace(/&ui=cute/, "") + "&fast" + EXTRA); await B.ev(BS); await sleep(1500);
    ok("B follows A's ui (Worker opts.ui)", await B.ev(`document.body.dataset.ui === ${JSON.stringify(UI)} && __booth.S.ui === ${JSON.stringify(UI)}`), await B.ev(`document.body.dataset.ui + " " + document.body.dataset.screen`));
    await hooks(B, "b landB", "b", null);
    ok("fb3 no camera-allow note (B)", await B.ev(`!document.querySelector("#s-landB .camnote")`));
    await B.shot("06b-b-landB"); await contrast(B, "b landB");
    await B.tap('[data-act="startB"]'); await camReady(B);
    await B.until(`!document.getElementById("ghost").hidden`, 20000, "A still overlay");
    await sleep(800); await B.shot("07-b-view-a"); await hooks(B, "b shoot", "b", 0); await contrast(B, "b shoot");
    await hintCheck(B, "b");
    ok("7 no mover / reset / movable switch", !(await B.ev(`!!document.getElementById("mover") || !!document.getElementById("layReset") || !!document.getElementById("movableBox")`)));
    ok("9 B follows A's shape", await B.ev(`__booth.S.shape === ${JSON.stringify(SHAPE)} && document.body.dataset.shape === ${JSON.stringify(SHAPE)}`), await B.ev(`__booth.S.shape`));
    await cropCheck(B, "b-video", "video");
    await cropCheck(B, "b-ghost", "ghost");
    await sleep(500);
    ok("5 B sees A as still cut-out", await B.ev(`!document.getElementById("ghostClip") && /^blob:/.test(document.getElementById("ghost").src) && !document.querySelector("#frame video, #frame canvas:not(#liveCut)")`),
      "ghost = A's N-cut.png, no clip element");
    ok("10 view picker shown", await vis(B, "#viewPick"), await B.ev(`[...document.querySelectorAll("#viewPick .pill")].map(p => p.textContent).join(" / ")`));
    await B.tap('#viewPick .pill[data-view="b"]'); await sleep(1500);
    await B.shot("08-b-view-b");
    const lv = await B.ev(`(() => { const c = document.getElementById("liveCut"), d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let n = 0; for (let k = 3; k < d.length; k += 4) if (d[k] > 128) n++;
      const order = [...document.querySelectorAll("#frame > *")].map(e => e.id).join(">");
      return JSON.stringify({ cutPct: +(n / (d.length / 4) * 100).toFixed(1), bgShown: !document.getElementById("ghostBg").hidden, videoOpacity: getComputedStyle(document.getElementById("video")).opacity, order }); })()`);
    const L = JSON.parse(lv);
    ok("10 view b = A's room + B live cut-out + A on top", L.bgShown && L.cutPct > 3 && L.videoOpacity === "0" && /ghostBg>liveCut>ghost/.test(L.order), lv);
    await shootAll(B, "09-b");
    await pickBgWalk(B, "10-b", ["친구 배경", "내 배경", "배경 없음"]);
    log("B pick default bg (view b -> friend's):", await B.ev(`__booth.S.pickBg`));
    await B.tap('#pickBg .pill[data-bg="a"]'); await sleep(2000);
    await pickAndUpload(B, "11-b", null);
    await sleep(800); await B.shot("12-b-finished-share"); await hooks(B, "b finished", "b", 3); await contrast(B, "b finished");
    ok("ui rides in B's 완성 link", UI === "cute" ? (await txt(B, "linkText")).includes("ui=cute") : !(await txt(B, "linkText")).includes("ui="), await txt(B, "linkText"));
    ok("booth:screen fired per change", (await B.ev(`__bs`)) >= 5, "events " + await B.ev(`__bs`));
    ok("3 B finished screen", /완성/.test(await txt(B, "shareTitle")) && await vis(B, "#goDeco") && await vis(B, "#shareBtn"), `"${await txt(B, "shareTitle")}" / buttons "${await txt(B, "goDeco")}", "${await txt(B, "shareBtn")}"`);
    await B.tap("#copyBtn"); await sleep(400);
    const clip = await B.ev(`navigator.clipboard.readText()`).catch(e => "ERR " + e.message);
    ok("3 완성됐어 link text carries the URL", clip.includes(link.split("?")[1]) && /완성/.test(clip), JSON.stringify(clip));
    {   // Round 8 A: B's 완성 link goes through done.html (own og image), which forwards to the app with the same query
      const lt = await txt(B, "linkText");
      ok("R8A 완성 link is done.html", /\/done\.html\?s=/.test(lt), lt);
      const html = await (await fetch(lt)).text();
      ok("R8A done.html og image", /og-done\.png/.test(html) && /우리 네컷 나왔어/.test(html), html.match(/og:image" content="[^"]+/)?.[0]);
      ok("R8A og-done.png served", (await fetch(SITE + "og-done.png")).status === 200);
    }
    await B.tap("#goDeco");
    await B.until(`document.body.dataset.screen === "done" && document.getElementById("making").hidden`, 90000, "done B");
    await sleep(1500); await B.shot("13-b-done"); await hooks(B, "b done", "c", 3);
    log("B D.bg default:", await B.ev(`__booth.D.bg.id`));
    const lay = JSON.parse(await B.ev(LAYER7));
    fs.writeFileSync(`${OUT}/layer7-bg-a.png`, Buffer.from(lay.png.split(",")[1], "base64"));
    ok("7 B visible over A's background (pixels B changes)", lay.a > 3, `bg a ${lay.a}% | bg b(raw B) ${lay.b}% | pink ${lay["booth-pink"]}%`);
    await lookWalk(B, "b", "13c-b");
    await click(B, '[data-tab="frame"]'); await sleep(500);
    // decorate on B: a frame + a sticker
    ok("fb9 no 테마 tab on decorate", await B.ev(`!document.querySelector('#s-done [data-tab="theme"]') && !document.getElementById("themeRow")`));
    await B.tap('[data-tab="sticker"]'); await B.until(`document.querySelectorAll("#stkGrid button").length > 2`, 10000, "stickers");
    await B.tap("#stkGrid button:nth-child(2)"); await sleep(800);
    if (!(await B.ev(`__booth.D.stickers.length`))) { log("sticker tap missed, clicking"); await B.ev(`document.querySelector("#stkGrid button:nth-child(2)").click()`); await sleep(800); }
    await B.tap('[data-tab="frame"]'); await sleep(1500);
    log("B deco:", await B.ev(`__booth.D.stickers.length + " stickers, frame " + __booth.D.frame.id + ", theme " + __booth.D.theme`));
    // no auto video
    await sleep(4000);
    ok("12 no moving strip made on its own", await B.ev(`!__booth.D.video && !__booth.D.vjob && document.getElementById("stripVid").hidden && !document.getElementById("stripImg").hidden`));
    await click(B, "#dVid"); await sleep(400);   // deco mockup 3: the video buttons live in the 영상 sheet
    await B.ev(`document.getElementById("vidBtn").scrollIntoView({ block: "center" })`); await sleep(300);
    await B.shot("14-b-video-button");
    ok("13 two video cards", /움직이는 네컷/.test(await txt(B, "vidBtn")) && /메이킹 영상/.test(await txt(B, "storyBtn")) && /보너스/.test(await txt(B, "storyBtn")), `"${await txt(B, "vidBtn")}" / "${await txt(B, "storyBtn")}"`);
    ok("R8C bubble + previews", await vis(B, ".savebubble") && /자랑해/.test(await B.ev(`document.querySelector(".savebubble").textContent`))
      && await B.ev(`!!document.getElementById("vidPv").getAttribute("src") && !!document.getElementById("mkPv").getAttribute("src")`), await B.ev(`document.querySelector(".savebubble").textContent`));
    const copied = await B.ev(`navigator.clipboard.readText().catch(e => "ERR " + e.message)`);
    ok("R8C copied link = the work's start page, no pair id", copied === SITE, copied);
    await contrast(B, "done b"); await noOverflow(B, "done b");
    ok("fb6 no 사진+영상 button", await B.ev(`!document.getElementById("saveBoth")`));
    await B.ev(`window.__dl = []; HTMLAnchorElement.prototype.click = function () { if (this.download) return void __dl.push(this.download); return HTMLElement.prototype.click.call(this); }; 1`);
    await B.tap("#vidBtn"); await sleep(1500);
    log("vid button while making:", await txt(B, "vidBtn"));
    await B.until(`!!__booth.D.video`, 240000, "video");
    const du = JSON.parse(await B.ev(DUR));
    await sleep(2500);
    ok("fb6+10 one tap = one auto download, name to the second", await B.ev(`__dl.length === 1 && /^ttaro-gachi-\\d{8}-\\d{6}\\.(mp4|webm)$/.test(__dl[0])`), await B.ev(`JSON.stringify(__dl)`));
    // fb5: two-finger pinch on the decorate preview grows the whole box (border included) over the page
    await B.ev(`document.getElementById("dScrim").click()`); await sleep(400);   // close the 영상 sheet first
    await click(B, '[data-tab="frame"]'); await sleep(600);
    const R0 = JSON.parse(await B.ev(`JSON.stringify(document.getElementById("preview").getBoundingClientRect())`));
    const cx = R0.x + R0.width / 2, cy = R0.y + R0.height / 2;
    await B.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: cx - 20, y: cy, id: 1 }, { x: cx + 20, y: cy, id: 2 }] });
    for (let k = 1; k <= 8; k++) { await B.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: cx - 20 - k * 4, y: cy, id: 1 }, { x: cx + 20 + k * 4, y: cy, id: 2 }] }); await sleep(40); }
    await B.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] }); await sleep(400);
    const R1 = JSON.parse(await B.ev(`JSON.stringify(document.getElementById("preview").getBoundingClientRect())`));
    const topEl = await B.ev(`(() => { const t = document.querySelector('#s-done [data-tab="frame"]').getBoundingClientRect(); const e = document.elementFromPoint(t.x + t.width / 2, t.y + t.height / 2); return e && e.closest("#preview") ? "preview" : e?.tagName; })()`);
    await B.shot("15z-b-zoomed");
    ok("fb5 pinch grows the whole box", R1.width > R0.width * 2.2 && await B.ev(`__booth ? document.getElementById("preview").classList.contains("zoomed") : false`), `${R0.width.toFixed(0)} -> ${R1.width.toFixed(0)} px, over the dock: ${topEl}`);
    // double tap resets
    for (let k = 0; k < 2; k++) { await B.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: cx, y: cy, id: 3 }] }); await B.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] }); await sleep(120); }
    await sleep(400);
    const R2 = JSON.parse(await B.ev(`JSON.stringify(document.getElementById("preview").getBoundingClientRect())`));
    ok("fb5 double tap back to 1x", Math.abs(R2.width - R0.width) < 1 && Math.abs(R2.y - R0.y) < 1, `${R2.width.toFixed(1)} vs ${R0.width.toFixed(1)}`);
    ok("12 video on tap, 6.000 s", Math.abs(du.dur - 6) < 0.0005, JSON.stringify(du));
    ok("12 preview stays still", await B.ev(`document.getElementById("stripVid").hidden && !document.getElementById("stripImg").hidden`), "after: " + await txt(B, "vidBtn"));
    await B.shot("15-b-video-ready");
    await saveBlob(B, "__booth.D.video.blob", `b-strip-video.${du.type.includes("mp4") ? "mp4" : "webm"}`);
    await B.until(`!!__booth.D.photo`, 20000, "B photo");
    const bp = JSON.parse(await B.ev(PHOTO_WH)); log("B photo", JSON.stringify(bp));
    ok("9 strip size per shape (B photo)", bp.w === WANT.W && bp.h === WANT.H && Math.round(du.wh[0]) === WANT.W && du.wh[1] === WANT.H, `photo ${bp.w}x${bp.h}, video ${du.wh}`);
    await saveBlob(B, "__booth.D.photo", "b-strip.jpg");
    R.bStickers =await B.ev(`__booth.D.stickers.length + " stickers, frame " + __booth.D.frame.id`);
  } finally { log("B logs:\n" + B.logs.slice(-25).join("\n")); B.close(); }

  // A: in-app hand-off, then decorate in "Chrome"
  for (const [name, ua, want] of [["kakao-android", KAKAO_AND, /^kakaotalk:\/\/web\/openExternal\?url=/], ["threads-android", THREADS_AND, /^intent:\/\/.*package=com\.android\.chrome;end$/],
    ["webview-android", WV_AND, /^intent:\/\//], ["kakao-ios", KAKAO_IOS, null], ["naver-ios", NAVER_IOS, null]]) {
    await A.send("Emulation.setUserAgentOverride", { userAgent: ua });
    await A.go(link); await sleep(1500);
    const sc = await A.screen(), url = await A.ev(`__booth.S.handoffUrl || ""`);
    await A.shot(`16-a-handoff-${name}`); await hooks(A, "handoff " + name, "c", 3);
    if (want) ok("3 hand-off " + name, sc === "handoff" && want.test(url) && await vis(A, "#hoGo") && await vis(A, "#hoCopy"), url.slice(0, 120));
    // iOS: Safari steps; KakaoTalk also gets a "Safari에서 열기" button (openExternal from the tap), other apps none
    else ok("3 hand-off " + name, sc === "handoff" && !url && await vis(A, "#hoIOS") && (name === "kakao-ios") === await vis(A, "#hoGo")
      && (name !== "kakao-ios" || (await txt(A, "hoGo")) === "Safari에서 열기"), (await txt(A, "hoIOS")).slice(0, 60));
    if (name === "kakao-ios") { await A.ev(`document.getElementById("hoCopy").click()`); await sleep(300); await A.shot("16-a-handoff-copy"); await contrast(A, "handoff"); ok("3 hand-off copy link", await vis(A, "#hoBox") && (await txt(A, "hoLink")).includes("&me=a") && (UI === "cute") === (await txt(A, "hoLink")).includes("ui=cute"), await txt(A, "hoLink")); }
  }
  await A.send("Emulation.setUserAgentOverride", { userAgent: "" });
  const ho = await txt(A, "hoLink");
  await A.go(ho);
  ok("Chrome hand-off sets ui before paint", await A.ev(`document.body.dataset.ui === ${JSON.stringify(UI)}`), ho);
  await A.until(`document.body.dataset.screen === "done" && document.getElementById("making").hidden`, 90000, "done A");
  await sleep(1500); await A.shot("17-a-done"); await hooks(A, "a done", "c", 3); await contrast(A, "a done");
  await lookWalk(A, "a", "17a-a");
  // filter 약/중/강 (filters.js levels): 중 when first picked, chips compare the base id, hidden on 원본, previews render
  await A.ev(`document.querySelector('[data-tab="filter"]').click()`); await sleep(2500);
  const prev = () => A.ev(`[...document.querySelectorAll("#filterChips .tile .mini")].map(m => { const c = m.querySelector("canvas"); if (!c) return 0;
    const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let n = 0; for (let k = 0; k < d.length; k += 16) if (d[k] + d[k + 1] + d[k + 2] > 30) n++; return n; })`);
  const p0 = await prev();
  ok("filter chips 7 incl. 그레이, previews render", p0.length === 7 && p0.every(n => n > 50) && /그레이/.test(await txt(A, "filterChips")), JSON.stringify(p0));
  ok("filter levels hidden on 원본", !(await vis(A, "#filterLv")) && await A.ev(`__booth.D.filter === "original"`));
  await A.ev(`document.querySelector('#filterChips .tile[data-id="bbosasi"]').click()`); await sleep(2500);
  ok("filter first pick = 중", await A.ev(`__booth.D.filter === "bbosasi:1"`) && await vis(A, "#filterLv") && await A.ev(`[...document.querySelectorAll("#filterLv .pill")].map(p => p.textContent + (p.getAttribute("aria-pressed") === "true" ? "*" : "")).join(",") === "약,중*,강"`)
    && await A.ev(`document.querySelector('#filterChips .tile[data-id="bbosasi"]').getAttribute("aria-pressed") === "true"`), await A.ev(`__booth.D.filter`));
  await A.shot("17b-a-filter-mid"); await contrast(A, "filter tab");
  await A.ev(`document.querySelector('#filterLv .pill[data-lv="0"]').click()`); await sleep(1500);
  await A.ev(`document.querySelector('#filterChips .tile[data-id="gray"]').click()`); await sleep(2500);
  ok("filter switch keeps the level, chip by base id", await A.ev(`__booth.D.filter === "gray:0" && document.querySelector('#filterChips .tile[data-id="gray"]').getAttribute("aria-pressed") === "true" && document.querySelector('#filterLv .pill[data-lv="0"]').getAttribute("aria-pressed") === "true"`), await A.ev(`__booth.D.filter`));
  const p1 = await prev();
  ok("filter previews still render after a level change", p1.length === 7 && p1.every(n => n > 50), JSON.stringify(p1));
  await A.ev(`document.querySelector('#filterLv .pill[data-lv="2"]').click()`); await sleep(1500);
  ok("filter 강 = bare id", await A.ev(`__booth.D.filter === "gray"`));
  await A.shot("17c-a-filter-gray");
  await A.ev(`document.querySelector('#filterChips .tile[data-id="original"]').click()`); await sleep(1500);
  ok("원본 hides the levels", !(await vis(A, "#filterLv")) && await A.ev(`__booth.D.filter === "original"`));
  await A.ev(`document.querySelector('[data-tab="frame"]').click()`); await sleep(500);
  const aDeco = await A.ev(`__booth.D.stickers.length + " stickers, frame " + __booth.D.frame.id`);
  ok("4 decorations are per device", aDeco !== R.bStickers && aDeco.startsWith("0 "), `A: ${aDeco} | B: ${R.bStickers}`);
  await A.ev(`document.querySelector('[data-tab="caption"]').click()`); await sleep(300);
  await A.ev(`(() => { const i = document.getElementById("caption"); i.value = "A만"; i.dispatchEvent(new Event("input")); })()`);
  await sleep(1500); await A.shot("18-a-done-own-caption");
  ok("12 A: no auto video either", await A.ev(`!__booth.D.video && document.getElementById("stripVid").hidden`));
  await sleep(1000);
  await saveBlob(A, "__booth.D.photo", "a-strip.jpg");
  // story cut (1080x1920) on A: the strip on the story backdrop
  await A.ev(`window.__dl = []; HTMLAnchorElement.prototype.click = function () { if (this.download) return void __dl.push(this.download); return HTMLElement.prototype.click.call(this); }; 1`);
  await A.ev(`document.getElementById("storyBtn").click()`);
  await A.until(`!!__booth.D.story`, 240000, "story");
  await sleep(2500);
  ok("fb6+10 story: one tap = one auto download", await A.ev(`__dl.length === 1 && /^ttaro-gachi-\\d{8}-\\d{6}-making\\.(mp4|webm)$/.test(__dl[0])`), await A.ev(`JSON.stringify(__dl)`));
  const sd = JSON.parse(await A.ev(DUR.replace("__booth.D.video", "__booth.D.story")));
  ok("R8B making video 1080x1920 9.2 s", sd.wh[0] === 1080 && sd.wh[1] === 1920 && Math.abs(sd.dur - 9.2) < 0.05, JSON.stringify(sd));
  await saveBlob(A, "__booth.D.story.blob", "a-making.mp4");
  await A.shot("19-a-end", true);
  ok("fb1 no delete on A's decorate", await A.ev(`!document.querySelector('[data-act="del"]')`));
} catch (e) { log("ERROR", e.message); R.error = e.message.slice(0, 300); }
finally {
  log("A logs:\n" + A.logs.slice(-25).join("\n")); A.close();
  log("RESULTS\n" + Object.entries(R).map(([k, v]) => `${k}: ${v}`).join("\n"));
  fs.writeFileSync(`${OUT}/results.json`, JSON.stringify(R, null, 1));
  process.exit(0);
}
