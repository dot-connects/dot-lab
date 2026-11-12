// Bakes critter PNGs into app/app/src/main/assets/critters/<id>/ (plan Task 6, R4, R16, R19).
// Usage (from works/2026-11-09-catch): node tools/bake-critters.mjs <port> <empty chrome profile dir>
// Needs a static server for C:/DC/DD/dot-lab on <port>. Runs critters in batches of 4 with a 60 s budget per critter
// and 20 min overall; a critter that fails keeps whatever the app falls back to (front, then _placeholder).
// Heavy (headless Chrome + swiftshader): start it only outside the trading windows (plan R12/R17).
import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";

const [port, prof] = process.argv.slice(2);
const root = new URL("../app/app/src/main/assets/critters/", import.meta.url);
const list = JSON.parse(readFileSync(new URL("index.json", root), "utf8")).map(c => c.id);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(), TOTAL = 20 * 60_000;
let done = 0, failed = [];

async function batch(ids) {
  const dbg = 9350 + Math.floor(Math.random() * 40);
  const url = `http://127.0.0.1:${port}/works/2026-11-09-catch/tools/bake.html?ids=${ids.join(",")}`;
  const p = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe",
    ["--headless=new", `--remote-debugging-port=${dbg}`, `--user-data-dir=${prof}-${dbg}`, "--use-angle=swiftshader", "--enable-unsafe-swiftshader", url],
    { stdio: "ignore" });
  try {
    let tg;
    for (let i = 0; i < 80 && !tg; i++) { try { tg = (await (await fetch(`http://127.0.0.1:${dbg}/json`)).json()).find(x => x.type === "page"); } catch {} if (!tg) await sleep(250); }
    const ws = new WebSocket(tg.webSocketDebuggerUrl); await new Promise(r => (ws.onopen = r));
    let id = 0; const pend = {};
    ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
    const ev = expr => new Promise(r => { const i = ++id; pend[i] = m => r(m.result?.result?.value); ws.send(JSON.stringify({ id: i, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true } })); });
    const limit = Date.now() + ids.length * 60_000;
    while (Date.now() < limit && Date.now() - t0 < TOTAL) {
      const out = await ev("window.__out");
      if (out) {
        for (const [cid, pics] of Object.entries(out)) {
          const dir = new URL(`${cid}/`, root); mkdirSync(dir, { recursive: true });
          for (const [name, url] of Object.entries(pics)) writeFileSync(new URL(`${name}.png`, dir), Buffer.from(url.split(",")[1], "base64"));
          done++;
        }
        return;
      }
      await sleep(2000);
    }
    failed.push(...ids);
  } catch (e) { failed.push(...ids); console.log("batch error", String(e)); }
  finally { p.kill(); }
}

for (let i = 0; i < list.length && Date.now() - t0 < TOTAL; i += 4) {
  await batch(list.slice(i, i + 4));
  console.log(`baked ${done}/${list.length} in ${Math.round((Date.now() - t0) / 1000)} s`);
}
console.log(failed.length ? `failed: ${failed.join(" ")}` : "all baked");
