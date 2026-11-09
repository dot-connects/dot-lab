// Stage S1 headless check: node check/s1.mjs <serverPort> <profileDir>
// Needs `python -m http.server <port> --bind 127.0.0.1 --directory C:/DC/DD/dot-lab` running.
// Phone viewport 390x844, ?seed=1&touch: console errors, gyro A -> B moves the creature sideways, a swiped catch
// makes the counter 1, a miss puts the ball back in hand with no tap. Screenshots + result.json in out/s1/.
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const [port = "8765", prof] = process.argv.slice(2);
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, "$1")), "..", "out", "s1");
fs.mkdirSync(OUT, { recursive: true });
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe", DP = 9339;
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${DP}`, `--user-data-dir=${prof}`, "--window-size=500,900",
  "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--autoplay-policy=no-user-gesture-required", "--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream", "about:blank"], { stdio: "ignore" });
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
    if (m.method === "Log.entryAdded" && m.params.entry.level === "error") R.errors.push("log: " + m.params.entry.text + " " + (m.params.entry.url || ""));
  };
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async expr => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result?.result?.value;
  const shot = async name => { const r = await send("Page.captureScreenshot", { format: "png" }); fs.writeFileSync(path.join(OUT, name), Buffer.from(r.result.data, "base64")); };
  const until = async (expr, ms) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await ev(expr)) return true; await sleep(50); } return false; };
  // the browser's own sensor override (a synthetic event alone is overwritten by the emulated sensor's next reading)
  const orient = (alpha, beta, gamma) => send("DeviceOrientation.setDeviceOrientationOverride", { alpha, beta, gamma });
  const target = async () => JSON.parse(await ev("JSON.stringify(__catch.target())"));
  // events carry their own timestamps 16 ms apart: headless software GL draws a frame in ~0.4 s, and a flick timed
  // by arrival would read as a slow push
  const touch = (type, x, y, timestamp) => send("Input.dispatchTouchEvent", { type, timestamp, touchPoints: type === "touchEnd" ? [] : [{ x, y, id: 1 }] });
  // a straight flick from the ball whose 80 ms speed carries it to (lx, ly): release a third of the way, 128 ms
  async function swipe(lx, ly) {
    const b = JSON.parse(await ev("JSON.stringify(__catch.ball())"));
    const rx = b.x + (lx - b.x) / 3.03, ry = b.y + (ly - b.y) / 3.03, n = 8;
    const t0 = Date.now() / 1000;
    await touch("touchStart", b.x, b.y, t0);
    for (let i = 1; i <= n; i++) await touch("touchMove", b.x + (rx - b.x) * i / n, b.y + (ry - b.y) * i / n, t0 + i * 0.016);
    await touch("touchEnd", 0, 0, t0 + n * 0.016 + 0.004);
  }

  await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  // pose A from the first sensor reading: the emulated sensor's own default must not become the first pose
  await orient(0, 90, 0);
  await send("Page.navigate", { url: `http://127.0.0.1:${port}/works/2026-11-09-catch/site/?seed=1&touch` });
  R.steps.ready = await until("window.__catch && __catch.phase === 'hand'", 25000);
  await sleep(900);
  await shot("0-start.png");

  // gyro A then B (alpha +10: the phone turns left, the creature should slide right on the screen)
  await orient(0, 90, 0); await sleep(1000);
  const ta = await target(); await shot("1-gyroA.png");
  const tB = Date.now(); await orient(10, 90, 0); await sleep(700);
  const tb = await target(), msShot = Date.now() - tB; await shot("2-gyroB.png");
  R.steps.gyro = { A: ta.center, B: tb.center, dx: tb.center.x - ta.center.x, dy: tb.center.y - ta.center.y, msAtShot: msShot };

  // throw until one catch; record every outcome and that the ball is back after an escape
  const throws = [];
  for (let k = 0; k < 8; k++) {
    const t = await target();
    await until("__catch.phase === 'hand'", 4000);
    await sleep(Math.round(Math.random() * 300));
    await swipe(t.center.x, t.center.y);
    await until("__catch.phase !== 'drag' && __catch.phase !== 'fly'", 2000);
    const ph = await ev("__catch.phase"), g = JSON.parse(await ev("JSON.stringify(__catch.G.res && { grade: __catch.G.res.grade, p: __catch.G.res.p, caught: __catch.G.res.caught, shakes: __catch.G.res.shakes })"));
    if (ph === "catch") { await sleep(950); await shot(`3-shake-${k}.png`); }
    await until("['hand','caught','card'].includes(__catch.phase)", 6000);
    const after = await ev("__catch.phase");
    throws.push({ k, aim: t.center, landed: ph, res: g, after, th: JSON.parse(await ev("JSON.stringify(__catch.G.lastThrow)")) });
    if (after !== "hand") break;
  }
  await until("__catch.phase === 'card'", 4000); await sleep(300);
  await shot("4-card.png");
  R.steps.throws = throws;
  R.steps.counterAfterCatch = await ev("__catch.count()");

  // next creature, then a miss to the far side: the ball must be in hand again with no tap
  await ev("document.getElementById('cGo').click(), 1");
  await until("__catch.phase === 'hand'", 15000); await sleep(800);
  await shot("5-next.png");
  await swipe(25, 120);
  await until("__catch.phase !== 'drag' && __catch.phase !== 'fly'", 2000);
  const missPhase = await ev("__catch.phase"); await sleep(120);
  await shot("6-miss.png");
  R.steps.miss = { phaseRightAfter: missPhase, counter: await ev("__catch.count()") };
  // switch to the camera mid-game (Chrome's fake camera stands in for the rear one), then back
  R.steps.gum = await ev("navigator.mediaDevices.getUserMedia({ video: true }).then(s => { const l = s.getVideoTracks()[0].label; s.getTracks().forEach(t => t.stop()); return l; }, e => 'ERR ' + e.name)");
  await send("Runtime.evaluate", { expression: "document.getElementById('mode').click()", userGesture: true });
  await until("__catch.ar.debug().mode === 'camera'", 10000); await sleep(1500);
  R.steps.cameraMode = JSON.parse(await ev("JSON.stringify(__catch.ar.debug())"));
  R.steps.note = await ev("document.getElementById('note').textContent");
  await shot("7-camera.png");
  await ev("document.getElementById('mode').click(), 1"); await sleep(800);
  R.steps.backToScreen = JSON.parse(await ev("JSON.stringify(__catch.ar.debug())")).mode;
  ws.close();
} catch (e) { R.fatal = String(e && e.stack || e); }
finally {
  fs.writeFileSync(path.join(OUT, "result.json"), JSON.stringify(R, null, 1));
  // Browser.close ends every child process; killing the launcher alone leaves renderers behind on Windows
  try {
    const bw = new WebSocket((await (await fetch(`http://127.0.0.1:${DP}/json/version`)).json()).webSocketDebuggerUrl);
    await new Promise(r => bw.onopen = r); bw.send(JSON.stringify({ id: 1, method: "Browser.close" })); await sleep(1500);
  } catch (e) { console.error("Browser.close failed", e); }
  p.kill();
}
console.log(JSON.stringify(R, null, 1));
process.exit(0);
