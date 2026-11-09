// The dex (grid), one creature's detail (5 pose slots, empty = silhouette, unlocked moves), the catch card as a
// 1080x1350 PNG (share or download) and the .dotpet export the Android app reads.
import { loadImg } from "./ar.js";
import { cardText } from "./lines.js";
import { LABELS, LABEL_KO, moves, count } from "./moves.js";
import { serialize, fileName } from "./petfile.js";
import * as store from "./store.js";

export const ART = id => `https://cdn.jsdelivr.net/gh/PokeAPI/sprites@master/sprites/pokemon/other/official-artwork/${id}.png`;
const LINK = "dot-connects.github.io/dot-lab/works/2026-11-09-catch/site/";
const MOVE_KO = { bounce: "통통", flinch: "움찔", walk: "걷기", run: "뛰기", sleep: "잠자기", hide: "숨기" };
const MOVE_NEEDS = { walk: "옆모습", run: "옆모습 + 걷는 중", sleep: "누운 모습", hide: "뒷모습" };
const $ = id => document.getElementById(id);
const el = (tag, cls, text) => Object.assign(document.createElement(tag), cls ? { className: cls } : {}, text !== undefined ? { textContent: text } : {});
const no3 = n => `No.${String(n).padStart(3, "0")}`;

// ---------- pictures ----------
const urls = new Map();   // pose id -> object URL of its PNG (kept while the page lives; a home dex is small)
function poseUrl(p) {
  const k = `${p.id}@${p.at}`;
  if (!urls.has(k)) urls.set(k, URL.createObjectURL(p.png));
  return urls.get(k);
}
/** The picture that stands for a creature: the pokemon art, or its front (else first) pose. */
function face(c) {
  if (c.kind === "pokemon") return ART(c.pokeId);
  const p = LABELS.map(l => c.poses.find(q => q.label === l && q.png)).find(Boolean);
  return p ? poseUrl(p) : "";
}

// ---------- the card PNG ----------
const W = 1080, H = 1350;
function wrap(x, text, max) {   // Korean wraps at any character; break at a space when one is near
  const lines = [];
  let cur = "";
  for (const ch of text) {
    if (x.measureText(cur + ch).width > max && cur) {
      const sp = cur.lastIndexOf(" ");
      if (sp > cur.length * 0.6) { lines.push(cur.slice(0, sp)); cur = cur.slice(sp + 1) + ch; } else { lines.push(cur); cur = ch; }
    } else cur += ch;
  }
  return cur ? [...lines, cur] : lines;
}
/** {no, name, type, line, how, src} -> PNG Blob, 1080x1350 */
export async function cardPng(d) {
  const c = Object.assign(document.createElement("canvas"), { width: W, height: H }), x = c.getContext("2d");
  const all = [d.no, d.name, d.type, d.line, d.how, LINK, "우리 집 몬스터 잡기"].join("");
  await Promise.all([document.fonts.load('96px "Do Hyeon"', all), document.fonts.load('40px "IBM Plex Sans KR"', all)]).catch(() => {});
  const bg = x.createLinearGradient(0, 0, W * 0.3, H);
  bg.addColorStop(0, "#fffaf0"); bg.addColorStop(1, "#f1e6cf");
  x.fillStyle = bg; x.fillRect(0, 0, W, H);
  x.lineWidth = 6; x.strokeStyle = "#2a2418"; x.beginPath(); x.roundRect(30, 30, W - 60, H - 60, 44); x.stroke();
  x.textAlign = "center"; x.textBaseline = "alphabetic";
  x.fillStyle = "#9a7b33"; x.font = '52px "Do Hyeon"'; x.fillText(d.no, W / 2, 128);
  try {   // the sticker, contained in a 720x600 box, with a soft shadow like the on-screen card
    const im = await loadImg(d.src), s = Math.min(720 / im.naturalWidth, 600 / im.naturalHeight);
    const w = im.naturalWidth * s, h = im.naturalHeight * s;
    x.save(); x.shadowColor = "rgba(60,40,0,.28)"; x.shadowBlur = 30; x.shadowOffsetY = 16;
    x.drawImage(im, (W - w) / 2, 160 + (600 - h) / 2, w, h); x.restore();
  } catch { /* no picture: the card still carries the name and the line */ }
  x.fillStyle = "#2a2418"; x.font = '112px "Do Hyeon"'; x.fillText(d.name, W / 2, 878);
  x.font = '42px "IBM Plex Sans KR"';
  const tw = x.measureText(d.type).width + 56;
  x.beginPath(); x.roundRect((W - tw) / 2, 914, tw, 64, 22); x.fill();
  x.fillStyle = "#fff3d6"; x.fillText(d.type, W / 2, 960);
  x.fillStyle = "#2a2418"; x.font = '46px "IBM Plex Sans KR"';
  wrap(x, d.line, 900).slice(0, 2).forEach((l, i) => x.fillText(l, W / 2, 1060 + i * 64));
  x.fillStyle = "#7a6c50"; x.font = '34px "IBM Plex Sans KR"';
  d.how.split("\n").slice(0, 2).forEach((l, i) => x.fillText(l, W / 2, 1168 + i * 44));
  x.strokeStyle = "#d9cba8"; x.lineWidth = 2; x.beginPath(); x.moveTo(150, 1228); x.lineTo(W - 150, 1228); x.stroke();
  x.fillStyle = "#2a2418"; x.font = '36px "Do Hyeon"'; x.fillText("우리 집 몬스터 잡기", W / 2, 1268);
  x.fillStyle = "#9a8a66"; x.font = '24px "IBM Plex Sans KR"'; x.fillText(LINK, W / 2, 1300);
  return new Promise((ok, no) => c.toBlob(b => (b ? ok(b) : no(new Error("toBlob"))), "image/png"));
}

function download(blob, name) {
  const a = el("a"); a.href = URL.createObjectURL(blob); a.download = name;
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 10000);
}
/** Web Share with the file where the phone can, else a download. */
async function shareFile(blob, name) {
  const f = new File([blob], name, { type: blob.type });
  try {
    if (navigator.canShare?.({ files: [f] })) { await navigator.share({ files: [f] }); return "share"; }
  } catch (e) { if (e && e.name === "AbortError") return "cancel"; }
  download(blob, name); return "download";
}

// ---------- the card overlay (S1's HTML card, now with a PNG behind it) ----------
let card = null;   // {png: Promise<Blob>, file, from: "game" | "dex"}
/** Fill the on-screen card for creature c and start drawing its PNG now, so [저장·공유] can share at once. */
export function fillCard(c, src, how, from = "game") {
  const t = cardText(c.id, c.kind), d = { no: no3(c.no), name: c.name, type: t.type, line: t.line, how, src };
  $("cNo").textContent = d.no;
  $("cImg").crossOrigin = "anonymous"; $("cImg").src = src;
  $("cName").textContent = c.name; $("cType").textContent = t.type; $("cLine").textContent = t.line;
  $("cHow").textContent = how; $("cHow").style.whiteSpace = "pre-line";
  $("cGo").textContent = from === "dex" ? "닫기" : "계속 잡기";
  $("card").classList.toggle("over", from === "dex");
  card = { png: cardPng(d), file: `우리집몬스터-${d.no}-${c.name}.png`, from };
  card.png.catch(() => {});
  $("card").hidden = false;
}
$("cShare").onclick = async () => {
  if (!card) return;
  try { card.last = await shareFile(await card.png, card.file); } catch { card.last = "fail"; }
};
/** The card's close button: true when the card came from the dex (back to the detail), false = the game's card. */
export function closeCard() {
  if (!card || card.from !== "dex") return false;
  $("card").hidden = true; $("card").classList.remove("over"); card = null;
  return true;
}

// ---------- export ----------
async function b64(blob) {
  const u = new Uint8Array(await blob.arrayBuffer());
  let s = "";
  for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode(...u.subarray(i, i + 0x8000));
  return btoa(s);
}
async function artPng(pokeId) {   // the pokemon's one pose: its art, long side 256
  const im = await loadImg(ART(pokeId)), s = 256 / Math.max(im.naturalWidth, im.naturalHeight);
  const w = Math.round(im.naturalWidth * s), h = Math.round(im.naturalHeight * s);
  const c = Object.assign(document.createElement("canvas"), { width: w, height: h });
  c.getContext("2d").drawImage(im, 0, 0, w, h);
  return { png: await new Promise(ok => c.toBlob(ok, "image/png")), w, h };
}
/** creatures (from store.list) -> .dotpet text. A picture that cannot be read is left out (the parser drops empties). */
export async function exportText(cs) {
  const out = [];
  for (const c of cs) {
    const poses = [];
    if (c.kind === "pokemon") {
      try { const a = await artPng(c.pokeId); poses.push({ label: "front", png: await b64(a.png), w: a.w, h: a.h }); } catch { /* offline */ }
    } else {
      for (const l of LABELS) {
        const p = c.poses.find(q => q.label === l && q.png);
        if (p) poses.push({ label: l, png: await b64(p.png), w: p.w, h: p.h });
      }
    }
    if (poses.length) out.push({ id: c.id, name: c.name, kind: c.kind, no: c.no, pokeId: c.pokeId ?? null, poses });
  }
  return { text: serialize(out), creatures: out.length, poses: out.reduce((n, c) => n + c.poses.length, 0) };
}
async function exportNow(cs, msg) {
  msg.textContent = "내보내는 중…";
  const r = await exportText(cs);
  if (!r.creatures) { msg.textContent = "내보낼 모습이 없어요"; return; }
  download(new Blob([r.text], { type: "application/octet-stream" }), fileName());
  msg.textContent = `${r.creatures}마리 · 모습 ${r.poses}개를 내보냈어요 · Android 펫 앱에서 열어요`;
}

// ---------- screens ----------
const IOS = /iP(hone|ad|od)/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
let onClose = () => {};
/** Open the dex over whatever is on screen; onClosed runs when it is closed. */
export async function open(onClosed = () => {}) {
  onClose = onClosed;
  $("dex").hidden = false;
  await grid();
}
$("dClose").onclick = () => { $("dex").hidden = true; onClose(); };

async function grid() {
  $("dDetail").hidden = true; $("dGrid").hidden = false; $("dHead").hidden = false; $("dMsg").textContent = "";
  const cs = await store.list(), n = cs.reduce((a, c) => a + c.poses.length, 0);
  $("dCount").textContent = `${cs.length}마리 · 모습 ${n}개`;
  $("dNote").textContent = !store.saved ? "이 브라우저에선 도감이 저장되지 않아요 — 내보내기로 보관하세요"
    : IOS ? "Safari 는 7일 안 열면 지울 수 있어요 — 내보내기로 보관" : "";
  $("dNote").hidden = !$("dNote").textContent;
  $("dGrid").replaceChildren(...(cs.length ? cs.map(c => {
    const b = el("button", "cell"), im = el("img");
    im.crossOrigin = "anonymous"; im.src = face(c); im.alt = "";
    b.append(im, el("span", "cno", no3(c.no)), el("b", "", c.name), el("span", "cn", count(c.poses.map(p => p.label), c.kind)));
    b.onclick = () => detail(c.id);
    return b;
  }) : [el("p", "empty", "아직 잡은 녀석이 없어요. 공을 던져 첫 녀석을 잡아 봐요!")]));
  $("dAll").hidden = !cs.length;
  $("dAll").onclick = () => exportNow(cs, $("dMsg"));
}

async function detail(id) {
  const c = (await store.list()).find(x => x.id === id);
  if (!c) return grid();
  const labels = c.poses.map(p => p.label), t = cardText(c.id, c.kind), src = face(c);
  $("dGrid").hidden = true; $("dHead").hidden = true; $("dAll").hidden = true; $("dNote").hidden = true;
  $("dDetail").hidden = false; $("dMsg").textContent = "";
  $("xImg").crossOrigin = "anonymous"; $("xImg").src = src;
  $("xNo").textContent = no3(c.no);
  $("xName").textContent = c.name;
  $("xType").textContent = t.type;
  $("xCount").textContent = count(labels, c.kind) + (c.times > 1 ? ` · 잡은 횟수 ${c.times}` : "");
  // pose slots: a pokemon has its one picture; a real one has five, an empty one is its own silhouette
  const slots = c.kind === "pokemon" ? ["front"] : LABELS;
  $("xSlots").replaceChildren(...slots.map(l => {
    const p = c.poses.find(q => q.label === l), s = el("div", "slot" + (p ? "" : " off")), im = el("img");
    im.crossOrigin = "anonymous"; im.alt = ""; im.src = p ? (c.kind === "pokemon" ? src : poseUrl(p)) : src;
    if (l === "back" && !p) im.style.transform = "scaleX(-1)";
    s.append(im, el("span", "", LABEL_KO[l]));
    if (!p) s.append(el("i", "", "?"));
    return s;
  }));
  const on = moves(labels), all = c.kind === "pokemon" ? on : Object.keys(MOVE_KO);
  $("xMoves").replaceChildren(...all.map(m => el("span", "mv" + (on.includes(m) ? " on" : ""),
    on.includes(m) ? MOVE_KO[m] : `${MOVE_KO[m]} · ${MOVE_NEEDS[m]} 잡으면`)));
  $("xBack").onclick = () => grid();
  $("xCard").onclick = () => fillCard(c, src, `${count(labels, c.kind)} · 할 줄 아는 것 ${on.map(m => MOVE_KO[m]).join("·")}`, "dex");
  $("xExport").onclick = () => exportNow([c], $("dMsg"));
}

window.__dex = { open, detail, exportText, cardPng, list: () => store.list(), get card() { return card; } };   // headless checks
