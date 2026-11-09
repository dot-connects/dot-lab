// Stage S3 headless check: node check/s3.mjs <serverPort> <profileDir>
// Needs `python -m http.server <port> --bind 127.0.0.1 --directory C:/DC/DD/dot-lab` running.
// 1) start screen: the counter is there before any catch, zero console errors (the browser modules load).
// 2) a pokemon caught by a swiped throw (?seed=1&touch) -> its card -> [카드 저장·공유] downloads the card PNG.
// 3) two real creatures put in the dex through store.catchReal with S2's cut-outs (out/s2/*-cut.png): the dex grid,
//    the detail (filled slots, silhouettes, moves), a card from the detail -> PNG, [닫기] back to the detail.
// 4) [모두 내보내기] and [이 녀석 내보내기] download .dotpet files; petfile.parse reads them back here and the
//    creature count and labels must match the dex. Everything lands in out/s3/ (+ result.json).
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { parse } from "../site/js/petfile.js";

const [port = "8765", prof] = process.argv.slice(2);
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, "$1")), "..", "out", "s3");
const DL = path.join(OUT, "dl");
fs.rmSync(OUT, { recursive: true, force: true }); fs.mkdirSync(DL, { recursive: true });
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe", DP = 9339;
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${DP}`, `--user-data-dir=${prof}`, "--window-size=500,900",
  "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const R = { errors: [], steps: {} };
const fail = [];
const expect = (ok, what) => { if (!ok) fail.push(what); };
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
  const ev = async expr => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true, userGesture: true })).result?.result?.value;
  const shot = async name => { const r = await send("Page.captureScreenshot", { format: "png" }); fs.writeFileSync(path.join(OUT, name), Buffer.from(r.result.data, "base64")); };
  const until = async (expr, ms) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await ev(expr)) return true; await sleep(80); } return false; };
  const click = sel => ev(`(document.querySelector(${JSON.stringify(sel)}).click(), 1)`);
  const visible = sel => ev(`!!document.querySelector(${JSON.stringify(sel)}) && document.querySelector(${JSON.stringify(sel)}).offsetParent !== null`);
  const touch = (type, x, y, timestamp) => send("Input.dispatchTouchEvent", { type, timestamp, touchPoints: type === "touchEnd" ? [] : [{ x, y, id: 1 }] });
  async function swipe(lx, ly) {   // from s1.mjs
    const b = JSON.parse(await ev("JSON.stringify(__catch.ball())"));
    const rx = b.x + (lx - b.x) / 3.03, ry = b.y + (ly - b.y) / 3.03, n = 8, t0 = Date.now() / 1000;
    await touch("touchStart", b.x, b.y, t0);
    for (let i = 1; i <= n; i++) await touch("touchMove", b.x + (rx - b.x) * i / n, b.y + (ry - b.y) * i / n, t0 + i * 0.016);
    await touch("touchEnd", 0, 0, t0 + n * 0.016 + 0.004);
  }
  // wait for one new finished file in dl/, move it to out/s3/<as>
  async function download(as, ms = 60000) {   // headless sat on one .crdownload for 15 s+ once
    const t0 = Date.now();
    while (Date.now() - t0 < ms) {
      // Chrome on Windows makes an empty file under the final name next to the .crdownload: wait for both to settle
      const all = fs.readdirSync(DL), f = all.filter(x => !/\.(crdownload|tmp)$/.test(x));
      if (f.length && all.length === f.length && fs.statSync(path.join(DL, f[0])).size > 0) {
        await sleep(300); fs.renameSync(path.join(DL, f[0]), path.join(OUT, as)); return { name: f[0], ms: Date.now() - t0 };
      }
      await sleep(150);
    }
    return null;
  }

  await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable");
  // downloads go to out/s3/dl (a Browser.setDownloadBehavior from a closed connection fell back to ~/Downloads)
  R.steps.dlBehavior = (await send("Page.setDownloadBehavior", { behavior: "allow", downloadPath: DL })).error || "ok";
  await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  await send("DeviceOrientation.setDeviceOrientationOverride", { alpha: 0, beta: 90, gamma: 0 });

  // 1) start screen: counter before any catch, the dex opens empty
  await send("Page.navigate", { url: `http://127.0.0.1:${port}/works/2026-11-09-catch/site/?seed=1` });
  await until("window.__dex && document.getElementById('count').textContent.includes('마리')", 15000);
  await sleep(1200);
  R.steps.start = { counter: await ev("count.textContent"), counterVisible: await visible("#count"), errors: R.errors.length };
  await shot("0-start.png");
  await click("#count"); await until("!document.getElementById('dex').hidden", 3000); await sleep(400);
  R.steps.emptyDex = await ev("dGrid.textContent");
  await shot("0-dex-empty.png");
  await click("#dClose");

  // 2) a pokemon caught by a throw, its card, the PNG
  await send("Page.navigate", { url: `http://127.0.0.1:${port}/works/2026-11-09-catch/site/?seed=1&touch` });
  await until("window.__catch && __catch.phase === 'hand'", 25000); await sleep(900);
  const throws = [];
  for (let k = 0; k < 10; k++) {
    await until("__catch.phase === 'hand' && __catch.target() && __catch.target().onScreen", 20000);
    const t = JSON.parse(await ev("JSON.stringify(__catch.target())"));
    await swipe(t.center.x, t.center.y);
    await until("['hand','caught','card'].includes(__catch.phase) && __catch.phase !== 'catch'", 9000);
    await sleep(200);
    throws.push(await ev("__catch.phase"));
    if (throws.at(-1) !== "hand") break;
  }
  await until("__catch.phase === 'card' && !document.getElementById('card').hidden", 6000); await sleep(800);
  R.steps.pokemon = { throws, card: { no: await ev("cNo.textContent"), name: await ev("cName.textContent"), how: await ev("cHow.textContent"), share: await ev("cShare.textContent") } };
  await shot("1-catch-card.png");
  await ev("__dex.card.png.then(() => 1)");
  // the Web Share path: a phone that can share files gets the PNG as a File (stubbed here, Windows would open its dialog)
  await ev(`(navigator.canShare = d => !!(d && d.files)), (navigator.share = async d => { window.__shared = d.files[0]; }), 1`);
  await click("#cShare");
  await until("window.__shared", 8000);
  const sh = await ev(`(async () => { const f = window.__shared; const u = new Uint8Array(await f.arrayBuffer()); let s = ""; for (const b of u) s += String.fromCharCode(b); return { name: f.name, type: f.type, b64: btoa(s) }; })()`);
  fs.writeFileSync(path.join(OUT, "card-pokemon.png"), Buffer.from(sh.b64, "base64"));
  R.steps.pokemon.shared = { name: sh.name, type: sh.type, bytes: Buffer.from(sh.b64, "base64").length, how: await ev("__dex.card && __dex.card.last") };
  await ev("(navigator.canShare = () => false), 1");   // from here on: a browser without file share = download
  await click("#cGo");
  await until("__catch.phase === 'hand' || __catch.phase === 'wait'", 5000);

  // 3) three real creatures from S2's cut-outs (the real catch path is S2's check; this fills the dex quickly).
  // S2 has one dog picture, so 콩이's "side" is the same picture under a second label; selfie-dog's cut is the person.
  R.steps.seed = await ev(`(async () => {
    const blob = async f => { const b = await (await fetch("../out/s2/" + f)).blob(), im = await createImageBitmap(b); return { png: b, w: im.width, h: im.height }; };
    const dog = await blob("white-dog-cut.png"), cat = await blob("black-cat-cut.png"), man = await blob("selfie-dog-cut.png");
    const a = await __catch.store.catchReal({ id: null, name: "콩이", kind: "dog", label: "front", grade: "great", ...dog });
    await __catch.store.catchReal({ id: a.creature.id, name: "콩이", kind: "dog", label: "side", grade: "nice", ...dog });
    const b = await __catch.store.catchReal({ id: null, name: "나비", kind: "cat", label: "lying", grade: "excellent", ...cat });
    const c = await __catch.store.catchReal({ id: null, name: "사람 1", kind: "person", label: "front", grade: "hit", ...man });
    return { dog: a.creature.id, cat: b.creature.id, person: c.creature.id };
  })()`);
  await click("#count"); await until("document.querySelectorAll('#dGrid .cell').length >= 4", 5000); await sleep(800);
  R.steps.dex = { count: await ev("dCount.textContent"), cells: await ev("[...document.querySelectorAll('#dGrid .cell')].map(c => c.innerText.replace(/\\n/g, ' | '))") };
  await shot("2-dex.png");
  await ev(`([...document.querySelectorAll('#dGrid .cell')].find(c => c.textContent.includes("콩이")).click(), 1)`);
  await until("!document.getElementById('dDetail').hidden", 3000); await sleep(800);
  R.steps.detail = {
    head: await ev("xNo.textContent + ' ' + xName.textContent + ' ' + xType.textContent + ' ' + xCount.textContent"),
    slots: await ev("[...document.querySelectorAll('#xSlots .slot')].map(s => s.textContent + (s.classList.contains('off') ? ' (빈칸)' : ''))"),
    moves: await ev("[...document.querySelectorAll('#xMoves .mv')].map(s => s.textContent + (s.classList.contains('on') ? ' *' : ''))"),
  };
  await shot("3-detail.png");
  await click("#xCard");
  await until("!document.getElementById('card').hidden", 3000); await sleep(800);
  R.steps.detailCard = { go: await ev("cGo.textContent"), how: await ev("cHow.textContent") };
  await shot("4-detail-card.png");
  await ev("__dex.card.png.then(() => 1)");
  await click("#cShare");
  R.steps.detailCard.downloaded = await download("card-real.png");
  await click("#cGo"); await sleep(300);
  R.steps.detailCard.backToDetail = await ev("document.getElementById('card').hidden && !document.getElementById('dDetail').hidden && !document.getElementById('dex').hidden");

  // 4) export one, export all, read them back with petfile.parse
  await click("#xExport");
  R.steps.exportOne = { file: await download("one.dotpet") };
  R.steps.exportOne.msg = await ev("dMsg.textContent");
  await click("#xBack"); await sleep(300);
  await click("#dAll");
  R.steps.exportAll = { file: await download("all.dotpet") };
  R.steps.exportAll.msg = await ev("dMsg.textContent");
  await shot("5-exported.png");
  const dexNow = await ev("__dex.list().then(cs => cs.map(c => ({ id: c.id, name: c.name, kind: c.kind, no: c.no, labels: c.poses.map(p => p.label).sort() })))");
  const read = f => { const r = parse(fs.readFileSync(path.join(OUT, f), "utf8")); return { dropped: r.dropped, creatures: r.creatures.map(c => ({ id: c.id, name: c.name, kind: c.kind, no: c.no, labels: c.poses.map(p => p.label).sort(), sizes: c.poses.map(p => `${p.w}x${p.h}`) })) }; };
  R.steps.exportAll.parsed = read("all.dotpet");
  R.steps.exportOne.parsed = read("one.dotpet");
  R.steps.dexNow = dexNow;
  const strip = cs => cs.map(({ sizes, ...c }) => c);
  expect(JSON.stringify(strip(R.steps.exportAll.parsed.creatures)) === JSON.stringify(dexNow), "export all == dex (count, names, labels)");
  expect(R.steps.exportAll.parsed.dropped.poses === 0 && R.steps.exportAll.parsed.dropped.creatures === 0, "nothing dropped");
  expect(JSON.stringify(strip(R.steps.exportOne.parsed.creatures)) === JSON.stringify(dexNow.filter(c => c.name === "콩이")), "export one == 콩이");
  // the exported pictures, so they can be looked at
  for (const c of parse(fs.readFileSync(path.join(OUT, "all.dotpet"), "utf8")).creatures)
    for (const q of c.poses) fs.writeFileSync(path.join(OUT, `export-${c.no}-${q.label}.png`), Buffer.from(q.png, "base64"));

  expect(R.steps.pokemon.shared?.bytes > 10000 && R.steps.detailCard.downloaded, "card PNGs: one shared as a file, one downloaded");
  expect(R.steps.detailCard.backToDetail, "card [닫기] returns to the detail");
  expect(dexNow.length === 4, "4 creatures in the dex");
  expect(R.errors.length === 0, "zero console errors");
  ws.close();
} catch (e) { R.fatal = String(e && e.stack || e); }
finally {
  R.fail = fail; R.ok = !R.fatal && !fail.length;
  fs.writeFileSync(path.join(OUT, "result.json"), JSON.stringify(R, null, 1));
  try {
    const bw = new WebSocket((await (await fetch(`http://127.0.0.1:${DP}/json/version`)).json()).webSocketDebuggerUrl);
    await new Promise(r => bw.onopen = r); bw.send(JSON.stringify({ id: 1, method: "Browser.close" })); await sleep(1500);
  } catch (e) { console.error("Browser.close failed", e); }
  p.kill();
  spawnSync("taskkill", ["/F", "/T", "/PID", String(p.pid)]);
  fs.rmSync(DL, { recursive: true, force: true });
}
console.log(JSON.stringify(R, null, 1));
process.exit(R.ok ? 0 : 1);
