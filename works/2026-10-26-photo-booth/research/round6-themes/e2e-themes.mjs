// Walk A then B through the photo booth app in two fresh headless Chrome profiles (CDP, 430x932 dpr 2, touch).
import { spawn } from "node:child_process";
import fs from "node:fs";
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const SP = "C:/Users/ehdck/AppData/Local/Temp/claude/C--DC-DD/dd5f501f-171a-4f5a-9d24-ee18560f566d/scratchpad";
const OUT = "C:/DC/DD/dot-lab/.claude/worktrees/agent-aa67daf86b186a8a5/works/2026-10-26-photo-booth/research/round6-themes";
const SITE = "http://127.0.0.1:8802/works/2026-10-26-photo-booth/site/";
const MODE = process.argv[2] || "fast";      // fixed | movable
const EXTRA = process.argv[3] || "";
fs.mkdirSync(OUT, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

async function browser(port, who, y4m) {
  const prof = `${SP}/prof-booth-${who}-${Date.now()}`;
  const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${prof}`,
    "--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream", `--use-file-for-fake-video-capture=${SP}/media/${y4m}`,
    "--autoplay-policy=no-user-gesture-required", "--window-size=430,932", "about:blank"], { stdio: "ignore" });
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
  await send("Emulation.setDeviceMetricsOverride", { width: 430, height: 932, deviceScaleFactor: 2, mobile: true });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  await send("Browser.grantPermissions", { permissions: ["clipboardReadWrite", "clipboardSanitizedWrite", "videoCapture"], origin: "http://127.0.0.1:8802" });
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
async function contrast(b, name) {
  const r = await b.ev(CONTRAST);
  const bad = r.filter(x => x.ratio && x.ratio < 4.5), skipped = r.filter(x => x.skip);
  log(`contrast ${name}: ${r.length} texts, min ${Math.min(...r.filter(x => x.ratio).map(x => x.ratio))}`,
    bad.length ? "FAIL " + JSON.stringify(bad) : "ok", skipped.length ? "skipped(over photo): " + skipped.map(x => x.t).join(" | ") : "");
}
const noOverflow = async (b, name) => { const w = await b.ev(`[document.documentElement.scrollWidth, innerWidth]`); if (w[0] > w[1]) log("H-SCROLL", name, w); };



// ---- Round 4: 8 shots -> pick 4 -> HQ -> review -> upload ----------------------------------------------------------
const FAST = MODE !== "real";
const Q = (FAST ? "?fast" : "?") + EXTRA;
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

// shoot all 8 (optionally pressing 멈추기 after `stopAfter` shots and going on), returns ms from tap to the pick screen
async function shoot8(b, prefix, { stopAfter = null, shots = true } = {}) {
  const t0 = Date.now();
  await b.tap(".shutter");
  if (shots) { await sleep(FAST ? 700 : 1300); await b.shot(`${prefix}-countdown`);
    log("countdown text:", await b.ev(`document.getElementById("count").textContent + " step " + document.getElementById("step").textContent + " pose " + document.getElementById("pose").textContent`)); }
  if (stopAfter != null) {
    await b.until(`__booth.S.shots.length === ${stopAfter - 1}`, 90000, "shots before stop");
    await sleep(FAST ? 400 : 1500);
    await b.tap(".stopbtn");
    await b.until(`!__booth.S.busy`, 60000, "stopped");
    log("stopped at", await b.ev(`__booth.S.shots.length`), "hint:", await b.ev(`document.getElementById("shootHint").textContent`));
    await b.shot(`${prefix}-stopped`);
    await b.tap(".shutter");
  }
  if (shots) {
    await b.until(`__booth.S.shots.length === 3 && !document.getElementById("lastShot").hidden`, 120000, "3 shots");
    await sleep(FAST ? 300 : 3000); await b.shot(`${prefix}-shot4-lastthumb`);
    log("ghost on shot 4:", await b.ev(`JSON.stringify({ cutIdx: __booth.S.cutIdx, step: document.getElementById("step").textContent, ghostSrcIdx: __booth.S.ghost.indexOf(document.getElementById("ghost").src), clipShown: !document.getElementById("ghostClip").hidden })`));
  }
  const ghostLog = [];
  if (prefix.includes("-b")) {          // which of A's cuts floats on each shot
    for (let j = 4; j < 8; j++) {
      await b.until(`__booth.S.cutIdx === ${j}`, 120000, "shot " + j);
      ghostLog.push(await b.ev(`(() => { const S = __booth.S, g = document.getElementById("ghost"); return S.cutIdx + "->" + (S.ghost.indexOf(g.src) >= 0 ? S.ghost.indexOf(g.src) : "clip" + (S.cutIdx % 4)); })()`));
    }
    log("B ghost per shot (shot->A cut):", ghostLog.join(" "));
  }
  await b.until(`document.body.dataset.screen === "pick"`, 300000, "pick screen");
  const ms = Date.now() - t0;
  log(prefix, "HQ at pick open:", await b.ev(`JSON.stringify({ hq: __booth.S.hq, done: __booth.S.shots.filter(c => c.hq).length, busy: __booth.S.hqBusy })`));
  await sleep(200); await b.shot(`${prefix}-pick-open`);
  if (MODE !== "ask") await b.until(`!__booth.S.hqBusy`, 120000, "HQ of 8").catch(e => log("HQ8", e.message.slice(0, 80)));
  log(prefix, "HQ of 8 (shot:ms):", await b.ev(`JSON.stringify(__booth.S.hq)`), "segment stats:", await b.ev(segState));
  log(prefix, "8 shots, tap -> pick:", ms, "ms; sizes KB:", await b.ev(`JSON.stringify(__booth.S.shots.map(c => [c.raw.size, c.cut.size, c.clip.size].map(x => Math.round(x / 1024))))`),
    "stills px:", await b.ev(`Promise.all(__booth.S.shots.map(c => createImageBitmap(c.raw).then(i => i.width + "x" + i.height))).then(a => [...new Set(a)].join(","))`),
    "per-shot stats:", await b.ev(`import(new URL("js/segment.js", location.href).href).then(m => JSON.stringify(m.stats.still.cuts.map(c => c && [c.path, c.pauseMs, c.retried || 0])))`));
  return ms;
}

async function pick(b, prefix, order, askUpload = false) {
  await sleep(600); await b.shot(`${prefix}-pick-empty`); await contrast(b, prefix + " pick"); await noOverflow(b, prefix + " pick");
  log("pick: done disabled?", await b.ev(`document.getElementById("pickDone").disabled`), "reshoot shown?", await b.ev(`!document.getElementById("reshoot").hidden`));
  for (const [k, j] of order.entries()) {
    await b.tap(`#pickGrid .pk:nth-child(${j + 1})`);
    if (k === 1) { await sleep(300); await b.shot(`${prefix}-pick-2`); }
  }
  // remove + re-add the 2nd pick: order badges renumber, then the same 4 in the same order again
  await b.tap(`#pickGrid .pk:nth-child(${order[1] + 1})`);
  log("after removing 2nd:", await b.ev(`JSON.stringify(__booth.S.pick)`), "disabled:", await b.ev(`document.getElementById("pickDone").disabled`));
  await b.tap(`#pickGrid .pk:nth-child(${order[1] + 1})`);  // order is now 0,2,3,1 of `order`
  const want = [order[0], order[2], order[3], order[1]];
  log("pick order:", await b.ev(`JSON.stringify(__booth.S.pick)`), "want", JSON.stringify(want),
    "badges:", await b.ev(`[...document.querySelectorAll("#pickGrid .badge")].map(x => x.textContent || "-").join("")`),
    "done enabled:", await b.ev(`!document.getElementById("pickDone").disabled`));
  await sleep(300); await b.shot(`${prefix}-pick-4`);
  const t0 = Date.now();
  await b.tap("#pickDone");
  await b.until(`document.body.dataset.screen === "shoot" && !document.getElementById("review").hidden`, 30000, "review");
  log(prefix, "pick -> review (live cuts):", Date.now() - t0, "ms; chip:", await b.ev(`document.getElementById("hqChip").hidden ? "-" : document.getElementById("hqChip").textContent`),
    "pairs:", await b.ev(`JSON.stringify(__booth.S.pairs)`), "shots dropped:", await b.ev(`__booth.S.shots.length`), "cuts:", await b.ev(`__booth.S.cuts.length`));
  await sleep(300); await b.shot(`${prefix}-review-live`);
  // Round 5 보정 on the review: default look, then this side's picks (row 1 피부, 2 갸름, 3 눈; level 1 안 함, 2 살짝, 3 많이)
  await b.until(`document.querySelectorAll("#lookMine .pill").length === 9`, 10000, "look rows");
  await sleep(2500); await b.ev(`document.getElementById("lookMine").scrollIntoView({ block: "end" })`); await b.shot(`${prefix}-review-look-default`);
  for (const [r, v] of prefix.includes("-b") ? [[1, 3], [3, 2]] : [[2, 3], [3, 3]]) await b.tap(`#lookMine .lrow:nth-of-type(${r}) .pill:nth-of-type(${v})`);
  const tl = Date.now(); await sleep(400);
  await b.until(`(() => { const S = __booth.S; return S.revSeq && !document.getElementById("review").hidden; })()`, 10000, "review relook");
  await sleep(1500); await b.ev(`document.getElementById("lookMine").scrollIntoView({ block: "end" })`); await b.shot(`${prefix}-review-look-picked`);
  log(prefix, "look:", await b.ev(`JSON.stringify(__booth.S.look)`), "beauty stats:", await b.ev(`import(new URL("js/beauty.js", location.href).href).then(m => JSON.stringify({ still: m.stats.stillMs, det: m.stats.detectMs.slice(0, 12) }))`));
  await contrast(b, prefix + " look"); await noOverflow(b, prefix + " look");
  if (askUpload) {        // B: try 올리기 at once; the choice shows only while a picked cut is still pending
    await b.tap('[data-act="upload"]'); await sleep(300);
    const asked = await b.ev(`!document.getElementById("hqAsk").hidden`);
    log("ask shown:", asked);
    if (asked) { log("pending:", await b.ev(`JSON.stringify(__booth.S.cuts.map(c => !!c.hq))`), "chip:", await b.ev(`document.getElementById("hqChip").hidden ? "-" : document.getElementById("hqChip").textContent`));
      await b.shot(`${prefix}-upload-ask`); await contrast(b, prefix + " ask"); await noOverflow(b, prefix + " ask");
      const t1 = Date.now(); await b.tap("#hqWaitBtn"); await b.until(`document.body.dataset.screen !== "shoot"`, 40000, "upload after wait"); log("waited", Date.now() - t1, "ms"); }
    await b.until(`document.body.dataset.screen !== "shoot"`, 40000, "upload");
    return want;
  }
  await sleep(700); await b.shot(`${prefix}-review`); await contrast(b, prefix + " review"); await noOverflow(b, prefix + " review");
  return want;
}

const photoB64 = `(async () => { for (let i = 0; i < 300 && !__booth.D.photo; i++) await new Promise(r => setTimeout(r, 100)); const u8 = new Uint8Array(await __booth.D.photo.arrayBuffer()); let s = ""; for (const x of u8) s += String.fromCharCode(x); return btoa(s); })()`;
const A = await browser(9420 + Math.floor(Math.random() * 500), "a", "person-a-noflash.y4m");
let link;
try {
  await A.go(SITE + Q);
  await sleep(1500); await A.shot("01-a-landing");
  await A.until(`import(new URL("js/segment.js", location.href).href).then(m => ["loaded", "ready", "failed"].includes(m.stats.still.state))`, 90000, "HQ prefetch on landing").catch(e => log("PREFETCH", e.message.slice(0, 100)));
  log("HQ on landing (no tap):", await A.ev(segState));
  await A.tap('[data-act="startA"]');
  await camReady(A);
  await A.shot("02-a-shoot-ready"); await contrast(A, "a shoot");
  log("hq note:", await A.ev(`document.getElementById("hqNote").hidden ? "hidden" : document.getElementById("hqNote").textContent`));
  log("A HQ before shooting:", await A.ev(segState));
  const msA = await shoot8(A, "02-a", { stopAfter: MODE === "fast" ? 3 : null });
  if (MODE === "fast") {     // 전체 다시 찍기 once, then it is gone
    await A.tap("#reshoot");
    await A.until(`document.body.dataset.screen === "shoot" && __booth.S.shots.length === 0`, 10000, "reshoot");
    await sleep(400); await A.shot("03-a-reshoot");
    await shoot8(A, "03-a", { shots: false });
    log("reshoot hidden on 2nd pick:", await A.ev(`document.getElementById("reshoot").hidden`));
  }
  if (MODE === "real") log("REAL timer: A tap->pick", msA, "ms");
  const pickA = await pick(A, "04-a", [5, 0, 7, 2], MODE === "real" || MODE === "ask");
  if (MODE === "ask") { await A.until(`document.body.dataset.screen === "share"`, 60000, "share after ask"); log("ask run: uploaded cut sizes", await A.ev(`JSON.stringify(__booth.S.cuts.map(c => [c.hq || false, c.cut.size]))`), "HQ:", await A.ev(`JSON.stringify(__booth.S.hq)`)); throw new Error("ask run: done"); }
  if (MODE !== "real") await A.tap('[data-act="upload"]');
  await A.until(`document.body.dataset.screen === "share"`, 90000, "share screen");
  await sleep(800); await A.shot("05-a-share");
  link = await A.ev(`location.href`);
  log("A picked shots", JSON.stringify(pickA), "link", link);

  const B = await browser(10020 + Math.floor(Math.random() * 500), "b", "person-b-pos.y4m");
  try {
    await B.go(link + "&fast" + EXTRA); await B.until(`document.getElementById("peekImg").complete && document.getElementById("peekImg").naturalWidth > 0`, 15000, "peek");
    await sleep(600); await B.shot("06-b-landing");
    await B.tap('[data-act="startB"]');
    await camReady(B);
    await B.until(`!document.getElementById("ghostClip").hidden`, 20000, "A clip overlay").catch(e => log("NO CLIP OVERLAY", e.message.slice(0, 80)));
    await B.shot("07-b-shoot-ready");
    await B.until(`!!__booth.S.aLook`, 20000, "A look").catch(e => log("A LOOK", e.message.slice(0, 80)));
    await sleep(3000); await B.shot("07-b-shoot-ghost-look");
    log("B sees A's look:", await B.ev(`JSON.stringify(__booth.S.aLook)`), "ghost fx:", await B.ev(`!!__booth.S.ghostFx`), "ghost urls:", await B.ev(`__booth.S.ghost.map(u => u && u.slice(0, 30)).join(" ")`));
    await shoot8(B, "07-b");
    const pickB = await pick(B, "08-b", [6, 1, 3, 4], true);
    log("B expected pairs:", JSON.stringify(pickB.map(j => j % 4)));
    await B.until(`document.body.dataset.screen === "done"`, 90000, "done screen B");
    await B.until(`document.getElementById("making").hidden && !document.getElementById("stripImg").hidden`, 60000, "strip B ready");
    log("pairs.json on worker:", await B.ev(`fetch("http://127.0.0.1:8787/s/" + __booth.S.id + "/b/pairs.json").then(async r => r.status + " " + await r.text())`), "D.pairs:", await B.ev(`JSON.stringify(__booth.D.pairs)`));
    await sleep(1500); await B.shot("09-b-done", true); await contrast(B, "done"); await noOverflow(B, "done");
    // ---- result themes: tap each, add one sticker of its set, save the still + the moving strip ----
    const VK = `(() => { const D = __booth.D; return [D.bg.id, D.frame.id, D.filter, D.caption.trim(), JSON.stringify(D.outline), JSON.stringify(D.look), JSON.stringify(D.stickers), D.mattes ? "hq" : "", D.theme, D.font].join("|"); })()`;
    const blobB64 = expr => `(async () => { const u8 = new Uint8Array(await (${expr}).arrayBuffer()); let s = ""; for (let i = 0; i < u8.length; i += 32768) s += String.fromCharCode(...u8.subarray(i, i + 32768)); return btoa(s); })()`;
    const settle = `new Promise(r => { const c = () => document.getElementById("making").hidden ? r() : requestAnimationFrame(c); requestAnimationFrame(c); })`;
    for (const th of ["clean", "y2k", "diary"]) {
      await B.ev(`scrollTo(0, 0)`);
      if (th !== "clean") { await B.ev(`document.querySelector(${JSON.stringify(`.theme-${th}`)}).click()`); }
      await B.ev(settle); await sleep(400);
      log("theme", th, await B.ev(`JSON.stringify({ theme: __booth.D.theme, frame: __booth.D.frame.id, font: __booth.D.font, outline: __booth.D.outline, pressed: [...document.querySelectorAll(".theme")].map(b => b.getAttribute("aria-pressed")).join() })`));
      await B.ev(`document.querySelector(${JSON.stringify('[data-tab="frame"]')}).click()`); await sleep(900); await B.ev(`scrollTo(0, 0)`); await B.shot(`12-b-theme-${th}-frame`);
      await noOverflow(B, "theme " + th); await contrast(B, "theme " + th);
      await B.ev(`document.querySelector(${JSON.stringify('[data-tab="sticker"]')}).click()`); await sleep(1500);
      log("sticker set", await B.ev(`document.getElementById("stkGrid").dataset.set + " " + document.querySelectorAll("#stkGrid button").length`));
      await B.ev(`document.querySelector("#stkGrid button:nth-child(${th === "diary" ? 1 : 2})").click()`); await sleep(600);
      log("stickers", await B.ev(`JSON.stringify(__booth.D.stickers.map(s => s.kind + ":" + s.id))`));
      await B.shot(`12-b-theme-${th}-sticker`); await noOverflow(B, "sticker " + th);
      await B.ev(`document.querySelector(${JSON.stringify('[data-tab="caption"]')}).click()`); await sleep(500); await B.shot(`12-b-theme-${th}-caption`);
      await B.ev(`document.querySelector(${JSON.stringify('[data-tab="frame"]')}).click()`); await B.ev(settle);
      await B.until(`__booth.D.pkey === ${VK} && !!__booth.D.photo`, 30000, "photo " + th);
      fs.writeFileSync(`${OUT}/strip-${th}.jpg`, Buffer.from(await B.ev(blobB64("__booth.D.photo")), "base64"));
      const t0 = Date.now();
      await B.until(`__booth.D.video?.key === ${VK}`, 300000, "video " + th).catch(e => log("VIDEO", th, e.message.slice(0, 200)));
      const vi = await B.ev(`(async () => { const D = __booth.D; if (!D.video) return null; const v = document.createElement("video"); v.muted = true; v.src = URL.createObjectURL(D.video.blob);
        await new Promise(r => v.onloadedmetadata = r); const dur = v.duration; v.currentTime = 2.5; await new Promise(r => v.onseeked = r);
        const c = Object.assign(document.createElement("canvas"), { width: v.videoWidth, height: v.videoHeight }); c.getContext("2d").drawImage(v, 0, 0);
        return { dur, type: D.video.type, size: D.video.blob.size, w: v.videoWidth, h: v.videoHeight, frame: c.toDataURL("image/jpeg", 0.9).split(",")[1] }; })()`);
      if (vi) { fs.writeFileSync(`${OUT}/video-${th}-t2.5.jpg`, Buffer.from(vi.frame, "base64")); fs.writeFileSync(`${OUT}/video-${th}.mp4`, Buffer.from(await B.ev(blobB64("__booth.D.video.blob")), "base64")); }
      log("theme video", th, vi && JSON.stringify({ dur: vi.dur, type: vi.type, size: vi.size, w: vi.w, h: vi.h }), "wait ms", Date.now() - t0);
    }
    // decorate speed: click -> photo painted (rAF poll on #making), theme vs plain frame chip
    const speed = [];
    for (const sel of [".theme-y2k", ".theme-diary", ".theme-clean", '#frameChips .tile:nth-child(2)', '#frameChips .tile:nth-child(3)', ".theme-diary", ".theme-y2k", ".theme-clean", '#olColors .pill:nth-child(3)', '#olColors .pill:nth-child(5)', '#olColors .pill:nth-child(1)'])
      { speed.push(sel + " " + await B.ev(`(async () => { await new Promise(r => setTimeout(r, 2500)); const t0 = performance.now(); document.querySelector(${JSON.stringify(sel)}).click(); await ${settle}; return Math.round(performance.now() - t0); })()`)); }
    log("decorate speed ms:", speed.join(" | "));
    log("look.json a/b on worker:", await B.ev(`Promise.all(["a", "b"].map(s => fetch("http://127.0.0.1:8787/s/" + __booth.S.id + "/" + s + "/look.json").then(async r => r.status + " " + await r.text()))).then(a => a.join(" | "))`), "D.look:", await B.ev(`JSON.stringify(__booth.D.look)`));
    await B.tap('[data-tab="look"]'); await sleep(500); await B.ev(`document.getElementById("lookB").scrollIntoView({ block: "end" })`);
    await B.shot("09-b-done-looktab");
    const tr = Date.now();
    await B.tap('#lookB .lrow:nth-of-type(1) .pill:nth-of-type(1)');   // friend (A) skin -> 안 함
    await sleep(300); await B.until(`document.getElementById("making").hidden`, 30000, "re-render");
    log("done 보정 change -> strip ms:", Date.now() - tr, "D.look:", await B.ev(`JSON.stringify(__booth.D.look)`));
    await B.tap('#lookB .lrow:nth-of-type(1) .pill:nth-of-type(3)');   // friend skin -> 많이
    const tr2 = Date.now(); await sleep(300); await B.until(`document.getElementById("making").hidden`, 30000, "re-render 2");
    log("done 보정 change 2 -> strip ms:", Date.now() - tr2);
    await sleep(800); await B.ev(`scrollTo(0, 0)`); await B.shot("09-b-done-look-changed", true);
    fs.writeFileSync(`${OUT}/strip-b.jpg`, Buffer.from(await B.ev(photoB64), "base64"));
    await B.until(`!!__booth.D.video`, 120000, "video").catch(e => log("VIDEO", e.message.slice(0, 160)));
    log("video:", await B.ev(`JSON.stringify(__booth.D.video && { key: __booth.D.video.key, size: __booth.D.video.blob.size, type: __booth.D.video.type })`));
    await B.tap("#storyBtn");
    await B.until(`!!__booth.D.story`, 120000, "story").catch(e => log("STORY", e.message.slice(0, 160)));
    log("story:", await B.ev(`JSON.stringify(__booth.D.story && { size: __booth.D.story.blob.size })`));
    log("pair check (cell: diff vs a.cuts[pairs[i]], diff vs identity):", await B.ev(`(async () => {
      const C = await import(new URL("js/compose.js", location.href).href), D = __booth.D, out = [];
      const mk = () => Object.assign(document.createElement("canvas"), { width: 552, height: 382 });
      const px = c => c.getContext("2d").getImageData(0, 0, 552, 382).data;
      const diff = (p, q) => { let s = 0; for (let k = 0; k < p.length; k += 4) s += Math.abs(p[k] - q[k]) + Math.abs(p[k + 1] - q[k + 1]) + Math.abs(p[k + 2] - q[k + 2]); return +(s / (p.length / 4 * 3)).toFixed(2); };
      const R = { x: 0, y: 0, w: 552, h: 382 };
      for (let i = 0; i < 4; i++) {
        const c1 = mk(), c2 = mk(), c3 = mk();
        C.drawCell(c1.getContext("2d"), R, i, { a: D.media.a, b: D.media.b, bg: "booth-pink", pairs: D.pairs });
        C.drawCell(c2.getContext("2d"), R, i, { a: { cuts: D.media.a.cuts.map((_, k) => D.media.a.cuts[D.pairs[k]]) }, b: D.media.b, bg: "booth-pink" });
        C.drawCell(c3.getContext("2d"), R, i, { a: D.media.a, b: D.media.b, bg: "booth-pink" });
        out.push(i + ":" + diff(px(c1), px(c2)) + "/" + diff(px(c1), px(c3)));
      }
      return out.join(" "); })()`));
  } finally { log("B logs:\n" + B.logs.slice(-20).join("\n")); B.close(); }

  await A.go(link); await A.until(`document.body.dataset.screen === "done" && document.getElementById("making").hidden`, 60000, "done A");
  log("A D.pairs:", await A.ev(`JSON.stringify(__booth.D.pairs)`));
  await sleep(1500); await A.shot("10-a-done");
  fs.writeFileSync(`${OUT}/strip-a.jpg`, Buffer.from(await A.ev(photoB64), "base64"));
  await A.tap('#s-done [data-act="del"]'); await sleep(300);
  await A.tap('#s-done [data-act="delYes"]'); await A.until(`document.getElementById("msgTitle").textContent === "삭제됐어"`, 15000, "deleted");
  log("deleted ok");
  throw new Error("themes run: done (no cached revisit)");
  // cached visit: same profile, model from Cache Storage
  await A.go(SITE + Q); await sleep(1500);
  await A.until(`import(new URL("js/segment.js", location.href).href).then(m => ["loaded", "ready", "failed"].includes(m.stats.still.state))`, 90000, "prefetch 2").catch(() => {});
  log("cached landing:", await A.ev(segState));
  await A.tap('[data-act="startA"]'); await camReady(A);
  await shoot8(A, "11-a-cached", { shots: false });
} catch (e) { log("ERROR", e.message); }
finally { log("A logs:\n" + A.logs.slice(-20).join("\n")); A.close(); process.exit(0); }
