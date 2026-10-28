// Cute UI theme "말랑 부스" (mockups/cute3.html concept 3, operator pick 2026-10-28). Runs only while
// body[data-ui="cute"]: fills each screen's .uihead with the booth mascot + a speech bubble (step label, 4 dots) and
// puts the pink booth on the landing covers. The booth is driven by the film cover's own timeline (index.html inline
// script): its window shows the frame the film is on, its slot prints the 4 picked frames when the film lifts them,
// so the counter line under the cover keeps telling the same story. Leaving cute removes everything this file added.
const B = document.body;
const LABELS = { a: ["찍기", "고르기", "보정", "보내기"], b: ["찍기", "고르기", "보정", "완성"], c: ["찍기", "고르기", "보정", "꾸미기"] };
const NO_HEAD = new Set(["landA", "landB", "framed"]);   // the landing covers carry the booth itself
const EYES = {
  dot: '<circle cx="22" cy="38" r="3" fill="#2a2140"/><circle cx="38" cy="38" r="3" fill="#2a2140"/><circle cx="23" cy="37" r="1" fill="#fff"/><circle cx="39" cy="37" r="1" fill="#fff"/>',
  wink: '<circle cx="22" cy="38" r="3" fill="#2a2140"/><path d="M34 39q4-5 8 0" stroke="#2a2140" stroke-width="2.4" fill="none" stroke-linecap="round"/>',
  happy: '<path d="M18 39q4-5 8 0M34 39q4-5 8 0" stroke="#2a2140" stroke-width="2.4" fill="none" stroke-linecap="round"/>',
};
const mascot = ex => `<svg class="cu-mas" viewBox="0 0 60 64"><circle cx="30" cy="6" r="4" fill="#ffe27a" stroke="#2a2140" stroke-width="2"/><rect x="4" y="10" width="52" height="50" rx="15" fill="#ff9cc4" stroke="#2a2140" stroke-width="2.4"/><path d="M9 18h42v5q-5.25 5-10.5 0q-5.25 5-10.5 0q-5.25 5-10.5 0q-5.25 5-10.5 0z" fill="#c45fd9" stroke="#2a2140" stroke-width="2" stroke-linejoin="round"/>${EYES[ex]}<ellipse cx="15" cy="45" rx="4" ry="2.6" fill="#ff5f9e" opacity=".6"/><ellipse cx="45" cy="45" rx="4" ry="2.6" fill="#ff5f9e" opacity=".6"/><path d="M25 46q5 5 10 0" stroke="#2a2140" stroke-width="2.4" fill="none" stroke-linecap="round"/><rect x="18" y="53" width="24" height="3" rx="1.5" fill="#5b2a49"/></svg>`;
const FACE = '<svg class="cu-face" viewBox="0 0 60 22"><circle cx="18" cy="8" r="3.4" fill="#2a2140"/><circle cx="42" cy="8" r="3.4" fill="#2a2140"/><circle cx="19" cy="7" r="1.1" fill="#fff"/><circle cx="43" cy="7" r="1.1" fill="#fff"/><ellipse cx="9" cy="14" rx="4.4" ry="2.6" fill="#ff5f9e" opacity=".6"/><ellipse cx="51" cy="14" rx="4.4" ry="2.6" fill="#ff5f9e" opacity=".6"/><path d="M25 13q5 6 10 0" stroke="#2a2140" stroke-width="2.4" fill="none" stroke-linecap="round"/></svg>';
const BOOTH = `<div class="cu-booth" aria-hidden="true"><div class="cu-box"><i class="cu-lamp"></i><div class="cu-win"><div class="cu-shot"></div><i class="cu-flash"></i></div>${FACE}<div class="cu-slot"><div class="cu-out"><div class="cu-cells"></div><div class="cu-foot">따로 같이 네컷</div></div></div></div></div>`;

const cute = () => B.dataset.ui === "cute";

// ---- mascot header -------------------------------------------------------------------------------------------------
function heads() {
  const L = LABELS[B.dataset.flow], st = Number(B.dataset.step), sc = B.dataset.screen;
  const on = cute() && L && Number.isInteger(st) && st >= 0 && st < 4 && !NO_HEAD.has(sc);
  const ex = sc === "shoot" ? "wink" : sc === "share" || sc === "done" ? "happy" : "dot";
  const key = on ? `${B.dataset.flow}${st}${ex}` : "";
  const html = on ? `${mascot(ex)}<div class="cu-bub"><b>${L[st]}</b><span class="cu-dots">${[0, 1, 2, 3]
    .map(i => `<i class="${i < st ? "dn" : i === st ? "on" : ""}"></i>`).join("")}</span></div>` : "";
  for (const h of document.querySelectorAll(".uihead")) {
    if ((h.dataset.cu || "") === key) continue;
    h.innerHTML = html;
    if (key) h.dataset.cu = key; else delete h.dataset.cu;
  }
}

// ---- landing booth (one per film cover .fst) ---------------------------------------------------------------------------
const booths = new Map();   // .fst -> MutationObserver
function addBooth(fst) {
  fst.insertAdjacentHTML("beforeend", BOOTH);
  const shot = fst.querySelector(".cu-shot"), cells = fst.querySelector(".cu-cells"), flash = fst.querySelector(".cu-flash");
  let winKey = "", outKey = "", queued = false;
  // a frame's photo cells (the drawn people + A's real cut-outs once loaded) under a wrapper with the frame's classes,
  // so app.css's per-frame visibility rules (.v1 .fr.ex .me, ...) apply to the copy as they do to the film
  const copy = f => { const w = document.createElement("div"); w.className = f.className;
    w.append(...[...f.querySelectorAll(":scope > .cell")].map(c => c.cloneNode(true))); return w; };
  const pn = f => Number(f.querySelector(":scope > .pn")?.textContent || 0);
  const sync = () => {
    queued = false;
    const frs = [...fst.querySelectorAll(":scope > .fr")], has = c => f => f.classList.contains(c);
    if (!frs.length) return;
    const picked = frs.filter(pn).sort((x, y) => pn(x) - pn(y)), real = fst.classList.contains("real");
    // the frame the film is on: the friend's latest fill-in, else the latest pick, else the latest exposure
    const cur = picked.filter(has("ex2")).pop() || picked.filter(has("pkd")).pop() || frs.filter(has("ex")).pop() || frs[0];
    const k = `${frs.indexOf(cur)} ${cur.className} ${real}`;
    if (k !== winKey) {
      const shotNow = has("ex2")(cur) || (has("ex")(cur) && !fst.classList.contains("wide"));
      winKey = k; shot.replaceChildren(copy(cur));
      if (shotNow) { flash.classList.remove("on"); void flash.offsetWidth; flash.classList.add("on"); }
    }
    const o = picked.map(f => f.className).join() + real;
    if (o !== outKey) { outKey = o; cells.replaceChildren(...picked.map(copy)); }
  };
  const mo = new MutationObserver(() => { if (!queued) { queued = true; requestAnimationFrame(sync); } });
  mo.observe(fst, { subtree: true, attributes: true, attributeFilter: ["class"], childList: true });
  booths.set(fst, mo);
  sync();
}
function covers() {
  if (cute()) { for (const fst of document.querySelectorAll(".fst")) if (!booths.has(fst)) addBooth(fst); return; }
  for (const [fst, mo] of booths) { mo.disconnect(); fst.querySelector(".cu-booth")?.remove(); }
  booths.clear();
}

function update() { heads(); covers(); }
new MutationObserver(update).observe(B, { attributes: true, attributeFilter: ["data-ui", "data-screen", "data-flow", "data-step"] });
document.addEventListener("booth:screen", update);
update();
