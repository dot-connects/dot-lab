// Reel draft footage: A then B through the photo booth with AI performers as the camera (canvas getUserMedia, pose =
// shots taken so far), both screens screencast to JPEG frames with timestamps, plus the saved moving strip + making video.
import { spawn } from "node:child_process";
import fs from "node:fs";
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const SP = decodeURIComponent(new URL(".", import.meta.url).pathname).replace(/^\/(?=[A-Za-z]:)/, "").replace(/\/$/, "");   // this folder
const OUT = `${SP}/take${process.argv[2] || 1}`;
const ORIGIN = "http://127.0.0.1:8799", SITE = ORIGIN + "/works/2026-10-26-photo-booth/site/";
const UI = process.env.UI || "film";
fs.mkdirSync(`${OUT}/a`, { recursive: true }); fs.mkdirSync(`${OUT}/b`, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const T0 = Date.now(), log = (...a) => console.log(((Date.now() - T0) / 1000).toFixed(1).padStart(6), ...a);

// camera = the AI pose for the shot in progress, mirrored like a front camera, with a little life (breathing, sway)
const CAM = side => `(() => {
  const imgs = [0,1,2,3,4,5].map(i => Object.assign(new Image(), { src: "dev-media/reel/${side}-" + i + ".png" }));
  const W = 1280, H = 960, c = Object.assign(document.createElement("canvas"), { width: W, height: H }), g = c.getContext("2d");
  let cur = 0, prev = 0, at = 0;
  function draw(t) {
    const n = Math.min(5, window.__booth?.S?.shots?.length || 0);
    if (n !== cur) { prev = cur; cur = n; at = t; }
    const mix = Math.min(1, (t - at) / 350);
    const s = 1.012 + 0.008 * Math.sin(t / 900), dx = 7 * Math.sin(t / 1300), dy = 4 * Math.sin(t / 1100 + 1), r = 0.004 * Math.sin(t / 1700);
    g.setTransform(1, 0, 0, 1, 0, 0); g.fillStyle = "#000"; g.fillRect(0, 0, W, H);
    for (const [k, a] of [[prev, 1], [cur, mix]]) {
      const im = imgs[k]; if (!im.complete || !im.naturalWidth) continue;
      g.globalAlpha = a;
      g.setTransform(-s * Math.cos(r), s * Math.sin(r), -s * Math.sin(r), s * Math.cos(r), W / 2 + dx, H / 2 + dy);  // mirrored
      const sw = im.naturalHeight * 4 / 3, sx = (im.naturalWidth - sw) / 2;
      g.drawImage(im, sx, 0, sw, im.naturalHeight, -W / 2, -H / 2, W, H);
    }
    g.globalAlpha = 1;
  }
  (function loop(t) { draw(t || 0); requestAnimationFrame(loop); })();
  setInterval(() => draw(performance.now()), 33);           // keep frames coming when rAF is throttled
  const md = navigator.mediaDevices;
  md.getUserMedia = async () => c.captureStream(30);
  md.enumerateDevices = async () => [{ kind: "videoinput", deviceId: "ai", label: "AI", groupId: "ai" }];
})();`;

async function browser(port, who) {
  const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${SP}/prof-${who}-${Date.now()}`,
    "--autoplay-policy=no-user-gesture-required", "--window-size=430,932", "about:blank"], { stdio: "ignore" });
  let ws, opened;
  for (let i = 0; i < 120 && !ws; i++) {
    try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      const pg = t.find(x => x.type === "page"); if (pg) { ws = new WebSocket(pg.webSocketDebuggerUrl); opened = new Promise(r => ws.onopen = r); } } catch {}
    await sleep(250);
  }
  await opened;
  let id = 0, nf = 0; const pend = {}, logs = [], frames = [];
  ws.onmessage = e => { const m = JSON.parse(e.data);
    if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; }
    if (m.method === "Page.screencastFrame") {
      const f = `${String(nf++).padStart(5, "0")}.jpg`;
      fs.writeFileSync(`${OUT}/${who}/${f}`, Buffer.from(m.params.data, "base64"));
      frames.push({ f, t: m.params.metadata.timestamp, screen: null });
      ws.send(JSON.stringify({ id: 800000 + nf, method: "Page.screencastFrameAck", params: { sessionId: m.params.sessionId } }));
    }
    if (m.method === "Runtime.consoleAPICalled" && /warn|error/.test(m.params.type)) logs.push(m.params.args.map(a => a.value ?? a.description).join(" "));
    if (m.method === "Runtime.exceptionThrown") logs.push("EXC " + JSON.stringify(m.params.exceptionDetails).slice(0, 300)); };
  const send = (method, params = {}) => new Promise(r => { const i = ++id; const t = setTimeout(() => r({}), 30000);
    pend[i] = m => { clearTimeout(t); r(m); }; ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async x => { const r = await send("Runtime.evaluate", { expression: x, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result?.result?.value; };
  await send("Page.enable"); await send("Runtime.enable");
  await send("Page.addScriptToEvaluateOnNewDocument", { source: CAM(who === "a" ? "A" : "B") });
  await send("Emulation.setDeviceMetricsOverride", { width: 430, height: 932, deviceScaleFactor: 2, mobile: true, ...(who === "b" ? { screenWidth: 412, screenHeight: 915 } : {}) });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  const tap = async sel => {
    const r = await ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); e.scrollIntoView({block:"center"}); const b = e.getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 }; })()`);
    await sleep(150);
    await send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [r] }); await send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await sleep(120);
  };
  const until = async (x, ms = 60000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await ev(x).catch(() => false)) return; await sleep(200); } throw new Error("timeout " + x + "\n" + logs.slice(-10).join("\n")); };
  const mark = async label => { frames.push({ mark: label, t: Date.now() / 1000 }); log(who, label); };
  const cast = () => send("Page.startScreencast", { format: "jpeg", quality: 88, maxWidth: 860, maxHeight: 1864, everyNthFrame: 1 });
  const save = (expr, name) => ev(`(async () => { const bl = ${expr}; return await new Promise(r => { const f = new FileReader(); f.onload = () => r(f.result); f.readAsDataURL(bl); }); })()`)
    .then(u => { fs.writeFileSync(`${OUT}/${name}`, Buffer.from(u.split(",")[1], "base64")); log("saved", name); });
  return { p, send, ev, tap, until, mark, cast, save, frames, logs, close: () => p.kill() };
}
const ready = b => b.until(`document.getElementById("camwait").hidden`, 90000);

const A = await browser(9700 + Math.floor(Math.random() * 100), "a");
let link;
try {
  await A.cast();
  await A.send("Page.navigate", { url: SITE + "?fast" });
  await A.until(`document.body.dataset.screen === "theme"`, 20000); await sleep(1200);
  await A.mark("theme");
  for (let k = 0; k < 5 && (await A.ev(`document.body.dataset.screen`)) === "theme"; k++) { await A.tap(`.tcard[data-ui="${UI}"]`); await sleep(1500); }
  await A.until(`document.body.dataset.screen === "landA"`); await sleep(1500);
  await A.mark("landA"); await A.tap('[data-act="startA"]');
  await A.until(`document.body.dataset.screen === "mode"`); await sleep(600);
  await A.tap('#modes .mode[data-shape="wide"]'); await A.tap('[data-act="modeGo"]');
  await ready(A); await sleep(1500);
  await A.mark("shoot-ready"); await A.tap(".shutter");
  await A.until(`document.body.dataset.screen === "pick"`, 300000);
  await A.mark("pick"); await A.until(`!__booth.S.hqBusy`, 180000).catch(e => log("hq", e.message.slice(0, 60))); await sleep(800);
  for (const j of [0, 1, 2, 3]) { await A.tap(`#pickGrid .pk:nth-child(${j + 1})`); await sleep(350); }
  await A.tap("#pickDone");
  await A.until(`document.body.dataset.screen === "shoot" && !document.getElementById("review").hidden`, 30000); await sleep(1500);
  await A.mark("review"); await A.tap('#shootDone [data-act="upload"]'); await sleep(400);
  if (await A.ev(`!document.getElementById("hqAsk").hidden`)) await A.tap('[data-act="hqNow"]');
  await A.until(`document.body.dataset.screen === "share"`, 120000); await sleep(1500);
  await A.mark("share");
  link = await A.ev(`location.href`); log("link", link);
  await sleep(1500);
} catch (e) { log("A ERROR", e.message); }

if (!link) { log("no link from A, stopping"); A.close(); process.exit(1); }
const B = await browser(9800 + Math.floor(Math.random() * 100), "b");
try {
  await B.cast();
  await B.send("Page.navigate", { url: link + "&fast" });
  await B.until(`document.body.dataset.screen === "landB"`, 30000); await sleep(1500);
  await B.mark("landB");
  // full-screen stills of B's camera for the reel: before A appears, with A, every countdown digit of shot 1, the shot
  fs.mkdirSync(`${OUT}/bcam`, { recursive: true });
  const still = async name => { const s = await B.send("Page.captureScreenshot", { format: "png" });
    if (s.result) fs.writeFileSync(`${OUT}/bcam/${name}.png`, Buffer.from(s.result.data, "base64")); log("still", name); };
  await still("landB");
  // geometry for re-drawing B's camera at 30 fps: stage, video and A's cut-out rects (css px) + the cut-out PNG itself,
  // and the screen with the camera picture and the cut-out hidden (the app's own chrome around them)
  const GEO = `(async () => { const r = id => { const b = document.getElementById(id).getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height }; };
    const g = document.getElementById("ghost"), bl = await (await fetch(g.src)).blob();
    const url = await new Promise(res => { const f = new FileReader(); f.onload = () => res(f.result); f.readAsDataURL(bl); });
    return JSON.stringify({ stage: r("stage"), video: r("video"), ghost: r("ghost"), count: r("count"), mirror: document.getElementById("video").classList.contains("mirror"),
      ghostStyle: getComputedStyle(g).transform, png: url, dpr: devicePixelRatio, step: document.getElementById("step")?.textContent }); })()`;
  const geo = async name => { const j = JSON.parse(await B.ev(GEO)); fs.writeFileSync(`${OUT}/bcam/${name}.png`, Buffer.from(j.png.split(",")[1], "base64")); delete j.png;
    fs.writeFileSync(`${OUT}/bcam/${name}.json`, JSON.stringify(j)); log("geo", name, JSON.stringify(j.ghost)); };
  const chrome = async name => { await B.ev(`for (const id of ["video", "ghost"]) document.getElementById(id).style.visibility = "hidden"; 1`); await sleep(300);
    await still(name); await B.ev(`for (const id of ["video", "ghost"]) document.getElementById(id).style.visibility = ""; 1`); };
  await B.tap('[data-act="startB"]');
  await ready(B); await B.mark("cam-ready"); await B.until(`!document.getElementById("ghost").hidden`, 20000); await sleep(2500);
  await B.ev(`document.getElementById("ghost").style.visibility = "hidden"; 1`); await sleep(400); await still("empty");
  await B.ev(`document.getElementById("ghost").style.visibility = ""; 1`); await sleep(400); await still("ghost");
  await geo("geo0"); await chrome("chrome0");
  await B.mark("shoot-ready"); await B.tap(".shutter");
  const seen = new Set();
  while ((await B.ev(`__booth.S.shots.length`)) < 1) {
    const c = await B.ev(`document.getElementById("count").textContent.trim()`);
    if (c && !seen.has(c)) { seen.add(c); await still("count-" + c); }
    await sleep(40);
  }
  await still("shot1");
  await sleep(600); await geo("geo1"); await chrome("chrome1");
  await B.until(`document.body.dataset.screen === "pick"`, 300000);
  await B.mark("pick"); await B.until(`!__booth.S.hqBusy`, 180000).catch(e => log("hq", e.message.slice(0, 60))); await sleep(800);
  for (const j of [0, 1, 2, 3]) { await B.tap(`#pickGrid .pk:nth-child(${j + 1})`); await sleep(350); }
  await B.tap("#pickDone");
  await B.until(`document.body.dataset.screen === "shoot" && !document.getElementById("review").hidden`, 30000); await sleep(1500);
  await B.mark("review"); await B.tap('#shootDone [data-act="upload"]'); await sleep(400);
  if (await B.ev(`!document.getElementById("hqAsk").hidden`)) await B.tap('[data-act="hqNow"]');
  await B.until(`document.body.dataset.screen === "share"`, 120000); await sleep(1500);
  await B.mark("finished"); await B.tap("#goDeco");
  await B.until(`document.body.dataset.screen === "done" && document.getElementById("making").hidden`, 90000); await sleep(2500);
  await B.mark("done");
  // the decorate tab's outline "none": cut-outs without the default white sticker border
  if (process.env.NO_OUTLINE) { await B.ev(`__booth.D.outline = null; 1`); log("outline off"); }
  await B.ev(`HTMLAnchorElement.prototype.click = function () { if (this.download) return; return HTMLElement.prototype.click.call(this); }; 1`);
  await B.ev(`document.getElementById("dVid").click()`); await sleep(600);
  await B.tap("#vidBtn"); await B.until(`!!__booth.D.video`, 300000);
  await B.save("__booth.D.video.blob", "strip.mp4");
  await B.tap("#storyBtn"); await B.until(`!!__booth.D.story`, 300000);
  await B.save("__booth.D.story.blob", "making.mp4");
  await B.mark("saved");
  if (process.env.DECO) {
    // decorate states for the reel's "꾸미기" beat: each state is set through the app's own controls, then the strip
    // preview (canvas + sticker layer) is captured at 2x; dev-only CSS hides the sticker selection box
    fs.mkdirSync(`${OUT}/deco`, { recursive: true });
    await B.ev(`document.head.insertAdjacentHTML("beforeend", "<style>.stk-i{outline:none!important;box-shadow:none!important}.stk-i *:not(img){display:none!important}</style>"); 1`);
    const tab = t => B.ev(`document.querySelector('#s-done [data-tab="${t}"]').click(); 1`);
    const tileClick = (kind, label) => B.ev(`(() => { const t = [...document.querySelectorAll(".tile.${kind}")].find(b => b.textContent.trim() === ${JSON.stringify(label)}); if (!t) return "missing"; t.click(); return "ok"; })()`);
    const olClick = (box, sel) => B.ev(`(() => { const b = document.querySelector("#${box} " + ${JSON.stringify(sel)}); if (!b) return "missing"; b.click(); return "ok"; })()`);
    const snap = async name => {
      await sleep(1600);
      const r = await B.ev(`(() => { const b = document.getElementById("preview").getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height }; })()`);
      const s = await B.send("Page.captureScreenshot", { format: "png", clip: { x: r.x, y: r.y, width: r.w, height: r.h, scale: 2 } });
      if (s.result) fs.writeFileSync(`${OUT}/deco/${name}.png`, Buffer.from(s.result.data, "base64"));
      const full = await B.send("Page.captureScreenshot", { format: "png" });
      if (full.result) fs.writeFileSync(`${OUT}/deco/${name}-screen.png`, Buffer.from(full.result.data, "base64"));
      log("deco", name);
    };
    await B.ev(`document.getElementById("dScrim")?.click(); 1`); await sleep(500);
    await tab("frame"); await snap("00-base");
    for (const [k, f] of [["01", "핑크"], ["02", "블랙"], ["03", "리본"], ["04", "딸기"], ["05", "Y2K"], ["06", "하늘"], ["07", "체크"]]) {
      log("frame", f, await tileClick("frame", f)); await snap(`${k}-frame-${f}`);
    }
    await tileClick("frame", "핑크"); await tab("filter");
    for (const [k, f] of [["10", "흑백"], ["11", "뽀샤시"], ["12", "따뜻"], ["13", "필름"]]) { log("filter", f, await tileClick("filter", f)); await snap(`${k}-filter-${f}`); }
    await tileClick("filter", "원본"); await tab("outline");
    log("ol white", await olClick("olColors", ".sw:nth-of-type(2)")); await snap("20-outline-1");
    log("ol color", await olClick("olColors", ".sw:nth-of-type(4)")); await snap("21-outline-2");
    log("ol dotted", await olClick("olStyles", '[data-id="dotted"]')); await snap("22-outline-dotted");
    log("ol glitter", await olClick("olStyles", '[data-id="glitter"]')); await snap("23-outline-glitter");
    await olClick("olColors", ".none"); await tab("sticker");
    for (const set of ["y2k", "clean"]) {
      await B.ev(`document.querySelector('#stkSets [data-set="${set}"]').click(); 1`); await sleep(600);
      for (const n of [2, 5]) { await B.ev(`document.querySelector("#stkGrid button:nth-child(${n})")?.click(); 1`); await sleep(300); }
      await snap(`3${set === "y2k" ? 0 : 1}-stickers-${set}`);
    }
    // one full look: Y2K frame + glitter outline + the stickers already on + 뽀샤시
    await tab("frame"); await tileClick("frame", "Y2K"); await tab("filter"); await tileClick("filter", "뽀샤시");
    await tab("outline"); await olClick("olColors", ".sw:nth-of-type(3)"); await olClick("olStyles", '[data-id="glitter"]'); await snap("40-combo");
    await B.mark("deco");
  }
} catch (e) { log("B ERROR", e.message); console.log(B.logs.slice(-15).join("\n")); }
// A's own link after B is done: the wait screen turns into the finished strip
try { await A.send("Page.navigate", { url: link }); await sleep(6000); await A.mark("a-after"); await sleep(3000); } catch (e) { log("A after", e.message); }
await A.send("Page.stopScreencast"); await B.send("Page.stopScreencast"); await sleep(500);
fs.writeFileSync(`${OUT}/a.json`, JSON.stringify(A.frames)); fs.writeFileSync(`${OUT}/b.json`, JSON.stringify(B.frames));
log("frames a", A.frames.filter(f => f.f).length, "b", B.frames.filter(f => f.f).length);
A.close(); B.close(); process.exit(0);
