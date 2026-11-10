// Throw-feel check: node check/feel.mjs <serverPort> <profileDir> [seed]
// Needs `python -m http.server <port> --bind 127.0.0.1 --directory C:/DC/DD/dot-lab` running.
// Phone viewport 390x844, ?seed=N&touch. 20 deliberate flicks at the creature through CDP touch events with their own
// timestamps: speed 1.2-3.5 px/ms, aim +-6 degrees of noise, 140-260 px long, a sample every 12 ms. Reports the hit %
// (target >= 80 %). Then the page again with ?slow=6 (the game clock 6x slower: a headless frame takes ~0.4 s) for
// screenshots of the armed curve, the hit text pop, a wobble, a catch, a break-out and a miss.
// Out: out/feel/*.png + out/feel/result.json. Zero console errors expected.
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const [port = "8765", prof, seedArg = "3"] = process.argv.slice(2);
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, "$1")), "..", "out", "feel");
fs.mkdirSync(OUT, { recursive: true });
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe", DP = 9341;
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${DP}`, `--user-data-dir=${prof}`, "--window-size=500,900",
  "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let s = +seedArg >>> 0;   // the flicks' own seeded randomness (mulberry32), apart from the page's ?seed
const rand = () => { s = (s + 0x6d2b79f5) >>> 0; let t = s; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const R = { errors: [], flicks: [], shots: {} };
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
  let closed = null;
  ws.onclose = () => { closed = new Error("browser connection closed"); for (const k in pend) pend[k]({ error: closed }); };
  const send = (method, params = {}) => new Promise((r, no) => { if (closed) return no(closed); const i = ++id; pend[i] = m => m.error === closed ? no(closed) : r(m); ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async expr => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result?.result?.value;
  const js = async expr => JSON.parse(await ev(`JSON.stringify(${expr})`) ?? "null");
  const shot = async name => { const r = await send("Page.captureScreenshot", { format: "png" }); fs.writeFileSync(path.join(OUT, name), Buffer.from(r.result.data, "base64")); R.shots[name] = await ev("__catch.phase"); };
  const until = async (expr, ms) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await ev(expr)) return true; await sleep(40); } return false; };
  const touch = (type, x, y, timestamp) => send("Input.dispatchTouchEvent", { type, timestamp, touchPoints: type === "touchEnd" ? [] : [{ x, y, id: 1 }] });
  // ready = ball in hand and risen, the creature popped in
  const ready = () => until("__catch.phase === 'hand' && __catch.clock() - __catch.G.handT0 > 400 && __catch.target()", 30000);

  /** A flick from the ball towards (ax, ay): speed v px/ms, angle noise deg, length len px; a sample every 12 ms. */
  async function flick(v, noise, len, pre = []) {
    const b = await js("__catch.ball()"), t = await js("__catch.target()");
    if (!t) throw new Error("no target: " + JSON.stringify(await js("{ ph: __catch.phase, h: performance.now() - __catch.G.handT0, t: String(__catch.target()), d: __catch.ar.debug() }")));
    const a = Math.atan2(t.center.x - b.x, b.y - t.center.y) + noise * Math.PI / 180;
    const ms = len / v, n = Math.max(3, Math.round(ms / 12)), t0 = Date.now() / 1000;
    let x = b.x, y = b.y, tt = t0;
    await touch("touchStart", x, y, tt);
    for (const q of pre) { tt += 0.012; x = q.x; y = q.y; await touch("touchMove", x, y, tt); }   // a circling before the flick
    const sx = x, sy = y;
    for (let i = 1; i <= n; i++) await touch("touchMove", sx + Math.sin(a) * len * i / n, sy - Math.cos(a) * len * i / n, tt + i * ms / n / 1000);
    await touch("touchEnd", 0, 0, tt + ms / 1000 + 0.004);
    return { aim: t.center, box: t.box };
  }
  // settle a throw: back in hand (miss / break-out) or caught -> card -> next creature
  async function settle() {
    await until("['hand','card'].includes(__catch.phase)", 40000);
    if (await ev("__catch.phase") === "card") { await ev("document.getElementById('cGo').click(), 1"); }
    await ready();
  }

  await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  const open = async q => {
    await send("Page.navigate", { url: `http://127.0.0.1:${port}/works/2026-11-09-catch/site/?seed=${seedArg}&touch${q}` });
    await until("window.__catch && __catch.phase === 'hand'", 25000); await ready(); await sleep(600);
  };
  await open("");
  await shot("0-start.png");

  // 20 deliberate flicks at normal speed
  for (let k = 0; k < 20; k++) {
    await ready();
    const v = 1.2 + rand() * 2.3, noise = (rand() - 0.5) * 12, len = 140 + rand() * 120;
    await flick(v, noise, len);
    await until("__catch.phase !== 'drag' && __catch.phase !== 'fly'", 3000);
    const ph = await ev("__catch.phase");
    const res = await js("__catch.G.res && { grade: __catch.G.res.grade, p: +__catch.G.res.p.toFixed(3), caught: __catch.G.res.caught, shakes: __catch.G.res.shakes, critical: __catch.G.res.critical }");
    const th = await js("__catch.G.lastThrow && __catch.G.lastThrow.th");
    R.flicks.push({ k, v: +v.toFixed(2), noise: +noise.toFixed(1), len: Math.round(len), measured: th && +th.v.toFixed(2), hit: ph === "catch", res: ph === "catch" ? res : null });
    await settle();
  }
  R.hits = R.flicks.filter(f => f.hit).length;
  R.hitPct = Math.round(R.hits / R.flicks.length * 100);

  // screenshots on a 6x slower game clock
  await open("&slow=6");
  // the curve: circle the ball 1.1 turns and hold still (armed glow + sparkles); a slow lift is no throw
  const b = await js("__catch.ball()"), circ = [];
  for (let i = 1; i <= 40; i++) { const a = i / 36 * 2 * Math.PI; circ.push({ x: b.x + 34 * Math.sin(a), y: b.y - 34 + 34 * Math.cos(a) }); }
  const t0 = Date.now() / 1000;
  await touch("touchStart", b.x, b.y, t0);
  for (let i = 0; i < circ.length; i++) await touch("touchMove", circ[i].x, circ[i].y, t0 + (i + 1) * 0.016);
  await sleep(1500);
  R.curve = { armed: await ev("__catch.G.armed"), turn: await ev("Math.round(__catch.G.turn)") };
  await shot("1-armed.png");
  await touch("touchEnd", 0, 0, t0 + circ.length * 0.016 + 0.3);
  await until("__catch.phase === 'hand'", 3000);
  // a deliberate miss, 35 degrees wide
  await flick(2.2, 35, 200);
  await until("__catch.phase === 'hand' && __catch.G.ghosts.length", 8000); await shot("6-miss.png");
  await settle();
  // a curve ball, then straight ones until a catch and a break-out were both seen
  for (let k = 0; k < 14 && !(R.shots["4-caught.png"] && R.shots["5-break-out.png"]); k++) {
    await ready();
    await flick(2.2, 0, 200, k === 0 ? circ : []);
    await until("__catch.phase !== 'drag' && __catch.phase !== 'fly'", 8000);
    const res = await js("__catch.G.res && { grade: __catch.G.res.grade, p: __catch.G.res.p, caught: __catch.G.res.caught, curve: __catch.G.res.curve }");
    if (k === 0) R.curve.flick = res;
    if (!res) { await settle(); continue; }
    if (!R.shots["2-hit-pop.png"]) { await until("__catch.clock() - __catch.G.res.t0 > 120", 4000); await shot("2-hit-pop.png"); }
    if (!R.shots["3-wobble.png"]) { await until("__catch.clock() - __catch.G.res.t0 > 1090", 15000); await shot("3-wobble.png"); }
    if (res.caught && !R.shots["4-caught.png"]) { await until("__catch.phase === 'caught' && __catch.clock() - __catch.G.res.doneT > 120", 30000); await shot("4-caught.png"); }
    if (!res.caught && !R.shots["5-break-out.png"]) { await until("!!__catch.G.burst && __catch.clock() - __catch.G.burst.t0 > 60", 30000); await shot("5-break-out.png"); }
    await settle();
  }
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
console.log(JSON.stringify({ hitPct: R.hitPct, hits: R.hits, errors: R.errors, curve: R.curve, shots: R.shots, fatal: R.fatal }, null, 1));
process.exit(0);
