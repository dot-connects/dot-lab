// Five finished decorate looks of take 9's strip, set through the app's own controls in B's browser profile
// (the token there lets B reopen the decorate screen), each captured at 2x for the reel's "꾸미기" beat.
import { spawn } from "node:child_process";
import fs from "node:fs";
const CH = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const SP = decodeURIComponent(new URL(".", import.meta.url).pathname).replace(/^\/(?=[A-Za-z]:)/, "").replace(/\/$/, "");   // this folder
const PROF = process.argv[2], OUT = `${SP}/${process.argv[3] || "take9"}/${process.argv[4] || "looks"}`;
const SITE = "http://127.0.0.1:8799/works/2026-10-26-photo-booth/site/";
fs.mkdirSync(OUT, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const port = 9900 + Math.floor(Math.random() * 90);
const p = spawn(CH, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${PROF}`, "--window-size=430,932", "about:blank"], { stdio: "ignore" });
let ws, opened;
for (let i = 0; i < 120 && !ws; i++) { try { const t = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  const pg = t.find(x => x.type === "page"); if (pg) { ws = new WebSocket(pg.webSocketDebuggerUrl); opened = new Promise(r => ws.onopen = r); } } catch {} await sleep(250); }
await opened;
let id = 0; const pend = {};
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async x => { const r = await send("Runtime.evaluate", { expression: x, awaitPromise: true, returnByValue: true });
  if (r.result?.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result?.result?.value; };
const until = async (x, ms = 60000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await ev(x).catch(() => false)) return; await sleep(250); } throw new Error("timeout " + x); };
await send("Page.enable"); await send("Runtime.enable");
await send("Emulation.setDeviceMetricsOverride", { width: 430, height: 932, deviceScaleFactor: 2, mobile: true, screenWidth: 412, screenHeight: 915 });

await send("Page.navigate", { url: SITE + "?ui=cute" }); await sleep(2500);
const keys = await ev(`JSON.stringify(Object.entries(localStorage).map(([k, v]) => [k, String(v).slice(0, 80)]))`);
const linkId = (keys.match(/[A-Za-z0-9_-]{22}/g) || [])[0];
console.log("link id", linkId, keys.slice(0, 300));
await send("Page.navigate", { url: `${SITE}?s=${linkId}&ui=cute` });
await until(`["share", "done"].includes(document.body.dataset.screen)`, 30000);
if (await ev(`document.body.dataset.screen`) === "share") { await ev(`document.getElementById("goDeco").click(); 1`); }
await until(`document.body.dataset.screen === "done" && document.getElementById("making").hidden`, 90000); await sleep(2500);
await ev(`document.head.insertAdjacentHTML("beforeend", "<style>.zoomtag{display:none!important}</style>"); 1`);

const tab = t => ev(`document.querySelector('#s-done [data-tab="${t}"]').click(); 1`);
const tile = (kind, label) => ev(`(() => { const t = [...document.querySelectorAll(".tile.${kind}")].find(b => b.textContent.trim() === ${JSON.stringify(label)}); if (!t) return "missing ${label}"; t.click(); return "ok"; })()`);
const olColor = label => ev(`(() => { const b = [...document.querySelectorAll("#olColors .sw")].find(x => x.getAttribute("aria-label") === ${JSON.stringify(label)}); if (!b) return "missing"; b.click(); return "ok"; })()`);
const ol = (box, sel) => ev(`(() => { const b = document.querySelector(${JSON.stringify("#" + box + " " + sel)}); if (!b) return "missing"; b.click(); return "ok"; })()`);
// stickers go straight into D.stickers; selecting one and leaving the sticker tab makes the app repaint them all
const stickers = async list => { await tab("sticker"); await sleep(300);
  await ev(`__booth.D.stickers.splice(0, __booth.D.stickers.length, ...${JSON.stringify(list)}); __booth.D.sel = 0; 1`);
  await tab("frame"); await sleep(400); };
const snap = async name => {
  await sleep(1800);
  const r = await ev(`(() => { const b = document.getElementById("preview").getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height }; })()`);
  const s = await send("Page.captureScreenshot", { format: "png", clip: { x: r.x, y: r.y, width: r.w, height: r.h, scale: 2 } });
  fs.writeFileSync(`${OUT}/${name}.png`, Buffer.from(s.result.data, "base64")); console.log("look", name);
};
const top = i => 30 + i * 400;           // cell i's top in the 600x1800 strip (cells 552x382)
const S = (id, x, y, s = 120, r = 0) => ({ kind: "svg", id, x, y, s, r });
// research (ref-booth 10-30): 3-6 stickers, corners / gaps / above heads only, never on faces; colours alternate
const LOOKS = [
  { name: "1-vintage", bg: "내 배경", frame: "필름", filter: "필름", outline: null, caption: "",
    stk: [S("chalkak", 95, top(0) + 40, 110, -0.2), S("clover", 545, top(3) + 345, 95, 0.2)] },
  { name: "2-y2k", bg: "보라 부스", frame: "Y2K", filter: "원본", outline: ["노랑", "dotted", "thick"], caption: "",
    stk: [S("smile", 545, top(0) + 40, 150, 0.15), S("star", 300, top(2) - 8, 85), S("cat-ears", 150, top(2) + 38, 140), S("bff", 300, top(3) + 345, 170, -0.05)] },
  { name: "3-minimal", bg: "흰 벽", frame: "블랙", filter: "흑백", outline: ["흰색", "solid", "thin"], caption: "우리 둘, 따로 같이",
    stk: [S("sparkle", 545, top(1) + 45, 95), S("love-bubble", 545, top(2) + 300, 120, 0.1)] },
  { name: "4-scrap", bg: "친구 배경", frame: "스크랩", filter: "따뜻", outline: ["민트", "glitter", "normal"], caption: "멀리 있어도 오늘 최고",
    stk: [S("strawberry", 545, top(0) + 335, 105, 0.25), S("flower", 60, top(2) - 10, 95, -0.2), S("crown", 160, top(1) + 28, 115, -0.12),
          S("love", 470, top(3) + 345, 150, 0.12)] },
  { name: "5-lovely", bg: "분홍 부스", frame: "체크", filter: "뽀샤시", outline: ["흰색", "solid", "normal"], caption: "D+100 ♡",
    stk: [S("ribbon", 150, top(0) + 30, 125, -0.2), S("hearts", 545, top(1) + 335, 105, 0.2), S("blush", 430, top(2) + 160, 95), S("sparkle", 60, top(3) + 50, 90)] },
];
for (const L of LOOKS) {
  await tab("bg"); console.log(L.name, "bg", await tile("bg", L.bg));
  await tab("caption"); await ev(`(() => { const c = document.getElementById("caption"); c.value = ${JSON.stringify(L.caption)}; c.dispatchEvent(new Event("input", { bubbles: true })); return 1; })()`); await sleep(700);
  await tab("frame"); console.log(L.name, "frame", await tile("frame", L.frame));
  await tab("filter"); console.log("filter", await tile("filter", L.filter));
  await tab("outline");
  if (L.outline) { console.log("ol", await olColor(L.outline[0]), await ol("olStyles", `[data-id="${L.outline[1]}"]`), await ol("olWidths", `[data-id="${L.outline[2]}"]`)); }
  else await ol("olColors", ".none");
  await stickers(L.stk);
  await snap(L.name);
}
p.kill(); process.exit(0);
