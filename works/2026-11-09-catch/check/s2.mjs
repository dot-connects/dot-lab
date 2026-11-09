// Stage S2 headless check: node check/s2.mjs <serverPort> <profileDir>
// Needs `python -m http.server <port> --bind 127.0.0.1 --directory C:/DC/DD/dot-lab` running.
// ?still= feeds three photo-booth test photos in place of the camera: real boxes (selfie-dog = a person and a dog in one
// shot), a catch -> cut-out -> "누구야?" (new friend) -> "어떤 모습?" (suggested) -> IndexedDB; reload keeps the counter;
// the same dog caught again under another label reads "모습 2/5". Screenshots, cut-out PNGs and result.json in out/s2/.
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const [port = "8765", prof] = process.argv.slice(2);
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, "$1")), "..", "out", "s2");
fs.mkdirSync(OUT, { recursive: true });
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe", DP = 9339;
const MEDIA = "../../2026-10-26-photo-booth/research/pet-matting/media/";
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${DP}`, `--user-data-dir=${prof}`, "--window-size=500,900",
  "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--autoplay-policy=no-user-gesture-required", "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const R = { errors: [], steps: {} };
try {
  let tg;
  for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${DP}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
  const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
  let id = 0; const pend = {};
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; return; }
    if (m.method === "Runtime.exceptionThrown") R.errors.push("exception: " + (m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text));
    if (m.method === "Runtime.consoleAPICalled" && m.params.type === "error") R.errors.push("console: " + m.params.args.map(a => a.value ?? a.description).join(" "));
    // MediaPipe's wasm prints "INFO: Created TensorFlow Lite XNNPACK delegate" to stderr, which Chrome logs as an error
    if (m.method === "Log.entryAdded" && m.params.entry.level === "error" && !/^INFO: /.test(m.params.entry.text)) R.errors.push("log: " + m.params.entry.text + " " + (m.params.entry.url || ""));
  };
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async expr => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true, userGesture: true })).result?.result?.value;
  const shot = async name => { const r = await send("Page.captureScreenshot", { format: "png" }); fs.writeFileSync(path.join(OUT, name), Buffer.from(r.result.data, "base64")); };
  const until = async (expr, ms) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await ev(expr)) return true; await sleep(100); } return false; };
  const touch = (type, x, y, timestamp) => send("Input.dispatchTouchEvent", { type, timestamp, touchPoints: type === "touchEnd" ? [] : [{ x, y, id: 1 }] });
  async function swipe(lx, ly) {   // from s1.mjs: a straight flick whose 80 ms speed carries the ball to (lx, ly)
    const b = JSON.parse(await ev("JSON.stringify(__catch.ball())"));
    const rx = b.x + (lx - b.x) / 3.03, ry = b.y + (ly - b.y) / 3.03, n = 8, t0 = Date.now() / 1000;
    await touch("touchStart", b.x, b.y, t0);
    for (let i = 1; i <= n; i++) await touch("touchMove", b.x + (rx - b.x) * i / n, b.y + (ry - b.y) * i / n, t0 + i * 0.016);
    await touch("touchEnd", 0, 0, t0 + n * 0.016 + 0.004);
  }
  const click = sel => ev(`(document.querySelector(${JSON.stringify(sel)}).click(), 1)`);
  const clickText = t => ev(`([...document.querySelectorAll("#sheet .chip")].find(b => b.textContent.includes(${JSON.stringify(t)})).click(), 1)`);
  const chips = () => ev(`[...document.querySelectorAll("#sheet .chip")].map(b => b.textContent + (b.classList.contains("on") ? " *" : ""))`);

  await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });

  async function open(photo) {
    await send("Page.navigate", { url: `http://127.0.0.1:${port}/works/2026-11-09-catch/site/?seed=3&boxes&cpu&still=${MEDIA}${photo}.jpg` });
    await until("window.__catch && document.getElementById('goCam')", 15000);
    await click("#goCam");
    const t0 = Date.now(), ok = await until("__catch.seg.stats.ready", 60000);
    return { ready: ok, warmMs: await ev("__catch.seg.stats.warmMs"), waitedMs: Date.now() - t0 };
  }
  // throw at the chosen real target until it is caught; returns the throws
  async function catchReal() {
    const throws = [];
    for (let k = 0; k < 10; k++) {
      await until("__catch.phase === 'hand' && __catch.target() && __catch.target().real", 8000);
      const t = await ev("JSON.stringify(__catch.target())").then(JSON.parse);
      await swipe(t.center.x, t.center.y);
      await until("__catch.phase !== 'drag' && __catch.phase !== 'fly'", 3000);
      const landed = await ev("__catch.phase");
      await until("['hand','caught','card'].includes(__catch.phase)", 8000);
      throws.push({ aim: t.real, landed, after: await ev("__catch.phase"), cut: await ev("__catch.seg.stats.cut") });
      if (throws.at(-1).after !== "hand") break;
    }
    return throws;
  }
  const lastPose = () => ev(`new Promise(ok => { const r = indexedDB.open("dotcatch"); r.onsuccess = () => {
    const q = r.result.transaction("poses").objectStore("poses").getAll(); q.onsuccess = () => {
      const p = q.result.filter(x => x.png).sort((a, b) => b.at - a.at)[0], f = new FileReader();
      if (!p) return ok(null);
      f.onload = () => ok({ id: p.id, label: p.label, w: p.w, h: p.h, blurry: p.blurry, grade: p.grade, png: f.result.split(",")[1] }); f.readAsDataURL(p.png); }; }; })`);

  for (const photo of ["white-dog", "black-cat", "selfie-dog"]) {
    const S = R.steps[photo] = {};
    S.open = await open(photo);
    await until("__catch.reals().length >= (" + (photo === "selfie-dog" ? 2 : 1) + ")", 15000);
    await sleep(1500);
    S.reals = await ev("__catch.reals()");
    S.stats = await ev("JSON.parse(JSON.stringify(__catch.seg.stats))");
    S.label = await ev("document.getElementById('name').hidden ? '' : document.getElementById('name').textContent");
    await shot(`${photo}-1-boxes.png`);
    S.throws = await catchReal();
    await until("!document.getElementById('sheet').hidden && document.querySelector('#sheet .chip')", 30000); await sleep(300);
    S.who = { title: await ev("sTitle.textContent"), chips: await chips() };
    await shot(`${photo}-2-who.png`);
    await clickText("새 친구"); await sleep(150);
    S.defaultName = await ev("sName.value");
    await click("#sOk"); await sleep(150);
    S.pose = { title: await ev("sTitle.textContent"), chips: await chips() };
    await shot(`${photo}-3-pose.png`);
    await ev(`([...document.querySelectorAll("#sheet .chip.on")][0].click(), 1)`);
    await until("!document.getElementById('card').hidden", 5000); await sleep(400);
    S.card = await ev("cHow.textContent");
    await shot(`${photo}-4-card.png`);
    const pz = await lastPose();
    if (pz) { fs.writeFileSync(path.join(OUT, `${photo}-cut.png`), Buffer.from(pz.png, "base64")); delete pz.png; }
    S.saved = pz; S.cutStat = await ev("__catch.seg.stats.cut");
    S.counter = await ev("__catch.count()");
  }

  // reload: the counter comes back from IndexedDB
  const before = R.steps["selfie-dog"].counter;
  await send("Page.reload");
  await until("window.__catch && document.getElementById('goCam')", 15000);
  await click("#goScreen");
  await until("__catch.count() !== '0마리 · 모습 0개'", 8000);
  R.steps.reload = { before, after: await ev("__catch.count()") };

  // the white dog again: pick the existing dog, a label it does not have yet -> "모습 2/5"
  const S = R.steps.again = {};
  S.open = await open("white-dog");
  await until("__catch.reals().length >= 1", 15000); await sleep(1000);
  S.throws = await catchReal();
  await until("!document.getElementById('sheet').hidden && document.querySelector('#sheet .chip')", 30000); await sleep(300);
  S.who = await chips();
  await clickText("멍멍이 1"); await sleep(150);
  S.pose = await chips();
  const had = R.steps["white-dog"].saved.label;
  const KO = { front: "앞모습", side: "옆모습", walk: "걷는 중", lying: "누운 모습", back: "뒷모습" };
  S.picked = await ev(`(() => { const b = [...document.querySelectorAll("#sheet .chip")].find(b => b.textContent !== ${JSON.stringify(KO[had])}); b.click(); return b.textContent; })()`);
  S.hadLabel = had;
  await until("!document.getElementById('card').hidden", 5000); await sleep(400);
  S.card = await ev("cHow.textContent");
  S.counter = await ev("__catch.count()");
  await shot("again-card.png");
  ws.close();
} catch (e) { R.fatal = String(e && e.stack || e); }
finally {
  fs.writeFileSync(path.join(OUT, "result.json"), JSON.stringify(R, null, 1));
  try {
    const bw = new WebSocket((await (await fetch(`http://127.0.0.1:${DP}/json/version`)).json()).webSocketDebuggerUrl);
    await new Promise(r => bw.onopen = r); bw.send(JSON.stringify({ id: 1, method: "Browser.close" })); await sleep(1500);
  } catch (e) { console.error("Browser.close failed", e); }
  p.kill();
  spawnSync("taskkill", ["/F", "/T", "/PID", String(p.pid)]);   // Browser.close left 6 of them behind once
}
console.log(JSON.stringify(R, null, 1));
process.exit(0);
