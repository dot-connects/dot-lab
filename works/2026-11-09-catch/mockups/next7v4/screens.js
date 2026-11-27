// v4 screens 1-6. core.js (loaded first) has tween/loop/easings, the claw rig and the capsule.

/* ---------- share card (4:5, built at 300x375) ---------- */
const CARDS = {
  calico: { name: "삼색이", kind: "진짜 고양이", no: 8, cc: "#e2627a", img: "img/cam2-sticker.png", w: 150, top: 42, cut: false,
    line: "소파 구석에서 동그랗게 자다 뽑힘. 꿈에서 츄르 먹던 중", tags: ["잠꾸러기", "말랑함"], date: "11.27 오후 3:12" },
  dog: { name: "흰둥이", kind: "진짜 강아지", no: 7, cc: "#4d9be0", img: "img/whitedog-cut.webp", w: 100, top: 34, cut: true,
    line: "이불 위에서 혀 내밀고 웃다 뽑힘. 아직도 웃는 중", tags: ["해맑음", "혀빼꼼"], date: "11.26 오전 9:40" },
};
function cardHTML(k){
  const c = CARDS[k];
  return `<div class="sc" style="--cc:${c.cc}">
    <div class="sc-bg"></div><div class="sc-rays"></div>
    <div class="sc-brand felt"><img src="img/icon-fg.webp" alt="">리얼뽑기</div>
    <div class="sc-stamp"><span><small>도감</small><b>No.${c.no}</b></span></div>
    <img class="sc-who${c.cut ? " cut" : ""}" src="${c.img}" alt="" style="width:${c.w}px;top:${c.top}px">
    <div class="sc-plate felt stitch">
      <div class="sc-r1"><h4>${c.name}</h4><span class="sc-kind">${c.kind}</span></div>
      <p class="sc-line">${c.line}</p>
      <div class="sc-r3"><div class="k-tags">${c.tags.map(t => `<span>${t}</span>`).join("")}</div><span class="sc-date">${c.date}</span></div>
    </div></div>`;
}
function fillSlot(slot, k){ slot.innerHTML = cardHTML(k); slot.firstElementChild.style.transform = `scale(${slot.clientWidth / 300})`; }
document.querySelectorAll(".sc-slot[data-card]").forEach(s => fillSlot(s, s.dataset.card));

/** screen 2: the first-catch card; plays its entrance. */
function firstCard(host, onClose){
  host.innerHTML = `<div class="fc-rib">새로 뽑았다!</div><div class="sc-slot"></div>
    <p class="fc-after">도감에 넣었어요 · <u>펫으로 만들 수 있어요 ›</u></p>
    <div class="fc-btns"><button class="fbtn felt stitch">자랑하기</button><button class="flink light">계속 뽑기</button></div>`;
  host.hidden = false; fillSlot(host.querySelector(".sc-slot"), "calico");
  host.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 260, easing: "ease-out" });
  host.querySelector(".fc-rib").animate([{ transform: "translateY(-50px) rotate(-14deg)", opacity: 0 }, { transform: "translateY(4px) rotate(1deg)", opacity: 1, offset: .7 }, { transform: "rotate(-2deg)" }],
    { duration: 560, delay: 160, easing: "cubic-bezier(.3,1.2,.5,1)", fill: "backwards" });
  host.querySelector(".sc-slot").animate([{ transform: "translateY(40px) scale(.72) rotate(-6deg)", opacity: 0 }, { transform: "translateY(-6px) scale(1.03) rotate(1deg)", opacity: 1, offset: .65 }, { transform: "none", opacity: 1 }],
    { duration: 620, delay: 60, easing: "cubic-bezier(.25,1,.5,1)", fill: "backwards" });
  host.querySelector(".sc-who").animate([{ transform: "translateX(-50%) rotate(-4deg) scale(.4)", opacity: 0 }, { transform: "translateX(-50%) rotate(-8deg) scale(1.1)", opacity: 1, offset: .6 }, { transform: "translateX(-50%) rotate(-4deg) scale(1)", opacity: 1 }],
    { duration: 560, delay: 380, easing: "cubic-bezier(.3,1.3,.5,1)", fill: "backwards" });
  host.querySelector(".sc-stamp").animate([{ transform: "rotate(30deg) scale(2)", opacity: 0 }, { transform: "rotate(6deg) scale(.92)", opacity: 1, offset: .7 }, { transform: "rotate(6deg) scale(1)", opacity: 1 }],
    { duration: 380, delay: 700, easing: "ease-out", fill: "backwards" });
  host.querySelectorAll(".fc-after,.fc-btns").forEach((e, i) => e.animate([{ transform: "translateY(14px)", opacity: 0 }, { transform: "none", opacity: 1 }],
    { duration: 320, delay: 760 + i * 90, easing: "ease-out", fill: "backwards" }));
  host.querySelector(".flink").onclick = () => { host.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200 }).finished.then(() => { host.hidden = true; onClose && onClose(); }); };
}
{
  const host = $("fcStatic"); let shown = false;
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting && !shown) { shown = true; firstCard(host); } }), { threshold: .35 });
  io.observe($("c2")); firstCard(host);   // drawn at once; replays when scrolled to
  shown = false;
  $("fcRe").onclick = () => firstCard(host);
  host.addEventListener("click", e => { if (e.target.closest(".flink")) setTimeout(() => firstCard(host), 900); });
}

/* ---------- 1 camera claw ("잡자마자 작게") ---------- */
{
  const scr = $("cm"), rig = makeRig($("cmClaw")), held = $("cmHeld"), ring = $("cmRing"), go = $("cmGo");
  const BOX = { L: 84.5, T: 303.5, W: 188, H: 208 }, BCX = BOX.L + BOX.W / 2, BCY = BOX.T + BOX.H / 2;
  const AIR = ["헛집었다", "허공이었다", "아무것도 없었다"], SLIP = ["흔들려서 빠졌다", "미끄러졌다!"];
  const FLOOR = 752, HANG = 30, MINI = 48 / 188;     // capsule centre hangs 30 px below the tips; mini cut-out scale
  let outcome = "win", busy = false, dex = 8, drag = null;
  rig.x = 130; rig.draw();
  const toast = t => { const el = $("cmToast"); el.textContent = t; el.classList.add("on"); clearTimeout(el._t); el._t = setTimeout(() => el.classList.remove("on"), 1500); };
  document.querySelectorAll("#c1 [data-o]").forEach(b => b.onclick = () => {
    if (busy) return;
    outcome = b.dataset.o; document.querySelectorAll("#c1 [data-o]").forEach(o => o.setAttribute("aria-pressed", o === b));
    const want = outcome === "air" ? 14 : (CAM_TOP[Math.round(rig.cx())] ? null : 130);
    if (want != null) tween(420, E.io, (t, r) => { rig.x = lerp(rig.x, want, t); rig.swing = Math.sin(r * Math.PI) * (want < rig.x ? 3 : -3); rig.draw(); });
  });
  scr.addEventListener("pointerdown", e => { if (busy || e.target.closest("button") || e.target.closest(".fc")) return; drag = e.clientX; scr.setPointerCapture(e.pointerId); });
  scr.addEventListener("pointermove", e => { if (drag == null) return; const k = scr.getBoundingClientRect().width / 360;
    rig.x = clamp(rig.x + (e.clientX - drag) / k, 0, 264); drag = e.clientX; rig.draw(); });
  scr.addEventListener("pointerup", () => drag = null);

  /** capsule hanging from the (possibly swinging) claw tips at angle a (deg) */
  const hang = (cap, a, extra = 0) => {
    const sw = rig.swing * Math.PI / 180, L = rig.drop + TIP;
    const px = rig.cx() + Math.sin(-sw) * L, py = 48 + Math.cos(sw) * L, ar = a * Math.PI / 180;
    cap.x = px + Math.sin(-ar) * (HANG + extra); cap.y = py + Math.cos(ar) * (HANG + extra); cap.rot = a; cap.draw();
  };
  const heldAt = (cx, cy, s, rot = 0, sx = 1, sy = 1, op = 1) => {
    held.style.transform = `translate(${cx - BCX}px,${cy - BCY}px) rotate(${rot}deg) scale(${s * sx},${s * sy})`; held.style.opacity = op; };
  async function riseEmpty(from, ms){
    await tween(ms, E.io, (t, r) => { rig.drop = lerp(from, 40, t); rig.swing = pend(2.5, r * ms, .013, 300); rig.draw(); });
    await rig.to(160, E.outQ, { close: 0, swing: 0 });
  }

  go.onclick = async () => {
    if (busy) return; busy = true;
    go.classList.add("press"); setTimeout(() => go.classList.remove("press"), 140);
    const cx = Math.round(rig.cx()), top = CAM_TOP[cx], air = top == null;
    const target = air ? 446 : Math.round(top + 14 - 48 - TIP);
    // drop: slow and heavy (the rise stays brisk), a little dig past the contact, settle
    const dur = Math.min(1700, 650 + (target - 40) * 2.4);
    await tween(dur, E.sine, (t, r) => { rig.drop = lerp(40, target + 5, t); rig.swing = Math.sin(r * Math.PI) * -.8; rig.draw(); });
    await rig.to(200, E.outQ, { drop: target, swing: 0 });
    if (air) {
      await rig.to(100, E.outC, { close: 1.32 });
      puff(scr, rig.cx(), rig.tipY() - 4, 5, 16, 72);
      await tween(200, E.lin, (_, r) => { rig.swing = Math.sin(r * 30) * 2.2 * (1 - r); rig.squash = .05 * Math.sin(r * Math.PI); rig.draw(); });
      await sleep(140); toast(AIR[Math.floor(Math.random() * AIR.length)]);
      await riseEmpty(target, 700); busy = false; return;
    }
    // grip
    await rig.to(120, E.outC, { close: 1, squash: .04 });
    rig.to(140, E.outQ, { squash: 0 });
    ring.style.opacity = 0;
    const tipX = rig.cx(), tipY = rig.tipY(), capX = tipX, capY = tipY + HANG;
    const fl = $("cmFlash"); fl.style.setProperty("--fx", BCX + "px"); fl.style.setProperty("--fy", BCY + "px");
    fl.animate([{ opacity: 0 }, { opacity: .75, offset: .25 }, { opacity: 0 }], { duration: 360, easing: "ease-out" });
    held.style.display = "block"; heldAt(BCX, BCY, 1);
    // anticipation: it lifts and swells a touch toward the claw
    await tween(110, E.outQ, t => heldAt(BCX, BCY - 6 * t, 1 + .05 * t));
    const cap = makeCapsule(scr, "img/cam2-cat.png", 0); cap.x = capX; cap.y = capY;
    cap.top.style.opacity = cap.bot.style.opacity = 0; cap.con.style.visibility = "hidden"; cap.draw();
    // the open capsule appears, the catch is sucked in (accelerating, stretching), the lid shuts over it
    await closeCapsule(scr, cap, async () => {
      await tween(300, E.inC, (t, r) => { const st = Math.sin(Math.PI * r);
        heldAt(lerp(BCX, capX, t), lerp(BCY - 6, capY + 3, t) - st * 18, lerp(1.05, MINI, t), -10 * t, 1 - .14 * st, 1 + .2 * st); });
      held.style.display = "none"; cap.con.style.visibility = "";
    });
    await sleep(70);
    // lift: eased, the capsule swings like a pendulum from the tips
    const upTo = 60, T0 = performance.now(), swingAt = () => performance.now() - T0;
    if (outcome === "slip") {
      const mid = Math.round(lerp(target, upTo, .52));
      await tween(560, E.outQ, t => { rig.drop = lerp(target, mid, t); hang(cap, pend(7, swingAt(), .012, 500)); });
      // trouble: shudders, wider and wider
      await tween(520, E.lin, (_, r) => { const a = Math.sin(r * Math.PI * 6) * (3 + 13 * r); rig.swing = a * .12; rig.draw(); hang(cap, a); cap.sx = 1 + .03 * Math.sin(r * 40); cap.sy = 2 - cap.sx; cap.draw(); });
      cap.sx = cap.sy = 1;
      // prongs give, the capsule slides down a little ...
      await Promise.all([rig.to(140, E.outQ, { close: .5, swing: 0 }), tween(160, E.inQ, t => hang(cap, 0, 12 * t))]);
      // ... and pops open
      toast(SLIP[0]); puff(scr, cap.x, cap.y, 10, 40, 82); sparkle(scr, cap.x, cap.y - 20, 3, 83);
      cap.top.animate([{ transform: "none", opacity: .72 }, { transform: "translate(34px,-70px) rotate(150deg)", opacity: 0 }], { duration: 520, easing: "cubic-bezier(.2,.6,.4,1)", fill: "forwards" });
      cap.bot.animate([{ transform: "none", opacity: 1 }, { transform: "translate(-14px,140px) rotate(-40deg)", opacity: 0 }], { duration: 560, easing: "cubic-bezier(.5,0,.9,.6)", fill: "forwards" });
      cap.con.style.visibility = "hidden";
      // the little one springs back to where it was, overshoots, settles into the photo
      const sx = cap.x, sy = cap.y; held.style.display = "block";
      tween(520, E.io, t => { rig.drop = lerp(mid, mid - 8, t); rig.draw(); });
      await tween(560, E.lin, (_, r) => { const p = E.outC(r), s = lerp(MINI, 1, E.back(r, 1.9));
        const x = lerp(sx, BCX, p), y = lerp(sy, BCY, p) - Math.sin(Math.PI * r) * 70; heldAt(x, y, Math.max(.1, s), lerp(-10, 0, p)); });
      await tween(240, E.lin, (_, r) => { const k = Math.exp(-r * 4) * Math.cos(r * 12); heldAt(BCX, BCY, 1, 0, 1 + .06 * k, 1 - .08 * k); });
      await tween(220, E.inQ, t => heldAt(BCX, BCY, 1, 0, 1, 1, 1 - t));
      held.style.display = "none"; cap.remove(); ring.style.opacity = "";
      await riseEmpty(mid - 8, 560); busy = false; return;
    }
    const D = 820;
    await tween(D, E.io, t => { rig.drop = lerp(target, upTo, t); rig.swing = pend(1.2, swingAt(), .012, 500); rig.draw(); hang(cap, pend(7, swingAt(), .012, 500)); });
    // stop at the top: the capsule swings on, then the prongs let go
    const T1 = performance.now();
    await tween(260, E.lin, () => { const t = performance.now() - T1; rig.swing = pend(1.2, swingAt(), .012, 500) * .6; rig.draw(); hang(cap, pend(7, swingAt(), .012, 500) + pend(-6, t, .016, 260)); });
    rig.to(180, E.outQ, { close: 0, swing: 0 });
    toast("삼색이를 뽑았다!"); dex++; $("cmDex").textContent = `도감 ${dex}`;
    $("cmDex").animate([{ transform: "scale(1)" }, { transform: "scale(1.18)" }, { transform: "scale(1)" }], { duration: 380, easing: "cubic-bezier(.3,1.6,.5,1)" });
    await dropAndRoll(cap, FLOOR, 360);
    cap.remove(); ring.style.opacity = "";
    firstCard($("cmCard"));
    busy = false;
  };
}

/* ---------- 4 dex: newest first, favourites (set in the detail) pinned on top; long-press = pick several to delete (DexScreen) ---------- */
const STAR = '<svg viewBox="0 0 24 24"><path d="M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.2l-5.7 3.1 1.2-6.4-4.7-4.4 6.4-.8z"/></svg>';
/** 을/를 by the last syllable (Dex.withObject) */
const withObject = n => { const c = n.charCodeAt(n.length - 1) - 0xac00; return n + (c >= 0 && c < 11172 && c % 28 ? "을" : "를"); };
{
  const DX = [
    { id: "calico", n: "삼색이", s: "진짜 고양이", img: "img/cam2-sticker.png", real: 1, t: 1127.15, nw: 1 },
    { id: "dog", n: "흰둥이", s: "진짜 강아지", img: "img/whitedog-cut.webp", real: 1, cut: 1, t: 1126.09 },
    { id: "kkam", n: "까망이", s: "털뭉치 만드는 중", hatch: 1, real: 1, t: 1125.2 },
    { id: "monggeul", n: "몽글", s: "털뭉치", img: "img/monggeul-front.webp", t: 1124 },
    { id: "ppiyak", n: "삐약", s: "털뭉치", img: "img/ppiyak-front.webp", t: 1122 },
    { id: "dalsom", n: "달솜", s: "털뭉치", img: "img/dalsom-front.webp", t: 1120 },
    { id: "bamtol", n: "밤톨", s: "털뭉치", img: "img/bamtol-front.webp", t: 1118 },
    { id: "nyangsom", n: "냥솜", s: "털뭉치", img: "img/nyangsom-front.webp", t: 1115 },
    { id: "meongsom", n: "멍솜", s: "털뭉치", img: "img/meongsom-front.webp", t: 1112 },
  ];
  const scr = $("dx"), grid = $("dxGrid"), head = scr.querySelector(".dx-head"), cells = {};
  const fav = new Set(["monggeul"]), picked = new Set(), gone = new Set();
  let picking = false, asking = false;
  const pic = d => d.hatch ? "img/cap-closed.png" : d.img;
  for (const d of DX) {
    const el = document.createElement("div"); el.className = `dx-cell felt stitch${d.real ? " real" : ""}${d.hatch ? " hatch" : ""}`;
    el.innerHTML = `${d.real ? '<span class="dx-real">진짜</span>' : ""}${d.nw ? '<span class="dx-new">NEW</span>' : ""}
      <i class="dx-fav" aria-hidden="true">${STAR}</i><i class="dx-check" aria-hidden="true"></i>
      <span class="pic"><img ${d.cut ? 'class="cut"' : ""} src="${pic(d)}" alt="" ${d.hatch ? 'style="filter:hue-rotate(185deg)"' : ""}></span>${d.n}<small>${d.s}</small>`;
    cells[d.id] = el; grid.appendChild(el);
    let timer = null, longed = false;
    el.addEventListener("pointerdown", () => { longed = false;
      el.classList.remove("dx-press"); void el.offsetWidth; el.classList.add("dx-press");
      timer = setTimeout(() => { longed = true; if (!picking) startPicking(d.id); else togglePick(d.id); }, 450); });
    ["pointerup", "pointerleave", "pointercancel"].forEach(ev => el.addEventListener(ev, () => clearTimeout(timer)));
    el.addEventListener("click", () => { if (longed) return; picking ? togglePick(d.id) : openDetail(d); });
    el.addEventListener("contextmenu", e => e.preventDefault());
  }
  const qs = [0, 1, 2].map(() => { const q = document.createElement("div"); q.className = "dx-cell felt q";
    q.innerHTML = '<span class="pic"><img src="img/cap-closed.png" alt=""><span class="qq">?</span></span>&nbsp;<small>&nbsp;</small>'; grid.appendChild(q); return q; });
  const hint = document.createElement("p"); hint.className = "dx-hint"; hint.textContent = "길게 누르면 여러 마리를 골라 지울 수 있어요."; grid.appendChild(hint);

  /* FLIP: every cell glides from where it was */
  function order(){
    const list = DX.filter(d => !gone.has(d.id)).sort((a, b) => (fav.has(b.id) - fav.has(a.id)) || b.t - a.t);
    const all = [...list.map(d => cells[d.id]), ...qs];
    const before = new Map(all.map(e => [e, e.getBoundingClientRect()]));
    all.forEach(e => grid.insertBefore(e, hint));
    for (const d of DX) { cells[d.id].classList.toggle("fav", fav.has(d.id)); cells[d.id].classList.toggle("picked", picked.has(d.id)); }
    const k = grid.getBoundingClientRect().width / grid.offsetWidth || 1;
    all.forEach(e => { const a = before.get(e), b = e.getBoundingClientRect(); if (!a.width || !b.width) return;
      const dx = (a.left - b.left) / k, dy = (a.top - b.top) / k;
      if (dx || dy) e.animate([{ transform: `translate(${dx}px,${dy}px)`, zIndex: 5 }, { transform: "none", zIndex: 5 }], { duration: 520, easing: "cubic-bezier(.3,1.15,.4,1)" }); });
  }

  /* select mode, as DexScreen: top bar = 취소 · "N마리 지우기", check circles; the ? cells and the hint step aside */
  function drawHead(){
    if (!picking) { head.innerHTML = '<button class="chip felt stitch">‹ 뽑기</button><button class="chip felt stitch">펫 설정</button>'; return; }
    head.innerHTML = `<button class="chip felt stitch" id="dxCancel">취소</button>
      <button class="chip felt stitch dx-del" id="dxDel" ${picked.size ? "" : "disabled"}>${picked.size ? `${picked.size}마리 지우기` : "골라 주세요"}</button>`;
    $("dxCancel").onclick = stopPicking; $("dxDel").onclick = ask;
  }
  function startPicking(id){
    picking = true; picked.clear(); picked.add(id); scr.classList.add("picking");
    if (navigator.vibrate) navigator.vibrate(12);
    drawHead(); order();
    cells[id].querySelector(".dx-check").animate([{ transform: "scale(0)" }, { transform: "scale(1.25)" }, { transform: "none" }], { duration: 300, easing: "ease-out" });
  }
  function stopPicking(){ picking = false; picked.clear(); scr.classList.remove("picking"); drawHead(); order(); }
  function togglePick(id){
    picked.has(id) ? picked.delete(id) : picked.add(id);
    cells[id].classList.toggle("picked", picked.has(id)); drawHead();
    if (picked.has(id)) cells[id].querySelector(".dx-check").animate([{ transform: "scale(.4)" }, { transform: "scale(1.2)" }, { transform: "none" }], { duration: 260, easing: "ease-out" });
  }
  function ask(){
    if (!picked.size || asking) return; asking = true;
    const es = DX.filter(d => picked.has(d.id)), busy = es.filter(d => d.hatch).length;
    const who = es.length === 1 ? withObject(es[0].n) : `${es.length}마리를`;
    const dlg = document.createElement("div"); dlg.className = "dx-dlg";
    dlg.innerHTML = `<div class="dx-dbox felt stitch"><h4>${who} 지울까요?</h4>
      <p>사진과 그려 본 그림이 이 폰에서 지워져요. 되돌릴 수 없어요.${busy ? ` 지금 그리는 중인 ${busy}마리는 다 그린 뒤에 지울 수 있어서 이번엔 빠져요.` : ""}</p>
      <div class="dx-dbtn"><button class="chip felt stitch" data-a="no">그대로 두기</button><button class="chip felt stitch dx-del" data-a="yes">지우기</button></div></div>`;
    scr.appendChild(dlg);
    dlg.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 180 });
    dlg.firstElementChild.animate([{ transform: "translateY(24px) scale(.92)" }, { transform: "none" }], { duration: 300, easing: "cubic-bezier(.3,1.4,.5,1)" });
    dlg.onclick = async e => { const a = e.target.closest("[data-a]")?.dataset.a; if (!a) return;
      await dlg.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 160 }).finished; dlg.remove(); asking = false;
      if (a === "yes") remove(es.filter(d => !d.hatch).map(d => d.id)); };
  }
  async function remove(ids){
    const sr = scr.getBoundingClientRect(), k = sr.width / 360;
    await Promise.all(ids.map((id, i) => { const el = cells[id], r = el.getBoundingClientRect();
      setTimeout(() => puff(scr, (r.left + r.width / 2 - sr.left) / k, (r.top + r.height / 2 - sr.top) / k, 8, 34, 8), i * 70);
      return el.animate([{ transform: "none", opacity: 1 }, { transform: "scale(1.06)", opacity: 1, offset: .3 }, { transform: "scale(.2) rotate(-8deg)", opacity: 0 }],
        { duration: 340, delay: i * 70, easing: "ease-in", fill: "forwards" }).finished; }));
    ids.forEach(id => { gone.add(id); fav.delete(id); cells[id].style.display = "none"; cells[id].getAnimations().forEach(a => a.cancel()); });
    stopPicking();
  }
  $("dxRefill").onclick = () => { if (asking) return; for (const id of gone) cells[id].style.display = ""; gone.clear(); if (picking) stopPicking(); else order(); };

  /* tap a cell -> its detail; the star there pins it to the front */
  const det = document.createElement("div"); det.className = "dx-det"; scr.appendChild(det);
  function openDetail(d){
    det.innerHTML = `<div class="dt-top"><button class="chip felt stitch" id="dxBack">‹ 도감</button>
        <button class="chip felt stitch dx-star" id="dxStar" aria-pressed="${fav.has(d.id)}">${STAR}<span>즐겨찾기</span></button></div>
      <div class="dx-dhero${d.real ? " real" : ""}"><img ${d.cut ? 'class="cut"' : ""} src="${pic(d)}" alt="" ${d.hatch ? 'style="filter:hue-rotate(185deg)"' : ""}><i></i></div>
      <div class="dx-dname"><h3>${d.n}</h3><p>${d.s}</p></div>
      <p class="dx-dnote felt stitch" id="dxNote">${fav.has(d.id) ? "즐겨찾기한 녀석은 도감 맨 앞에 있어요." : "별을 누르면 도감 맨 앞에 고정돼요."}</p>
      <button class="fbtn felt stitch dx-dmake" tabindex="-1">${d.real ? "펫 미리보기" : "펫으로 쓰기"}</button>`;
    det.classList.add("on");
    det.animate([{ transform: "translateX(100%)" }, { transform: "none" }], { duration: 320, easing: "cubic-bezier(.3,1,.4,1)" });
    $("dxStar").onclick = () => { fav.has(d.id) ? fav.delete(d.id) : fav.add(d.id); const on = fav.has(d.id);
      $("dxStar").setAttribute("aria-pressed", on);
      $("dxStar").querySelector("svg").animate([{ transform: "scale(.6) rotate(-30deg)" }, { transform: "scale(1.35) rotate(8deg)" }, { transform: "none" }], { duration: 380, easing: "ease-out" });
      if (on) sparkle(det, 292, 62, 4, 9);
      $("dxNote").textContent = on ? "도감 맨 앞에 고정했어요." : "즐겨찾기를 뺐어요. 뽑은 날 순서로 돌아가요."; };
    $("dxBack").onclick = closeDetail;
  }
  async function closeDetail(){
    await det.animate([{ transform: "none" }, { transform: "translateX(100%)" }], { duration: 260, easing: "ease-in" }).finished;
    det.classList.remove("on"); order();
  }
  drawHead(); order();
}

/* ---------- 5 detail: preview both looks, then make BOTH pets at once (one free pull), two capsules hatch ---------- */
{
  const btn = $("dtBtn"), note = $("dtNote"), pick = document.querySelector("#dt .dt-pick"), looks = [...pick.querySelectorAll(".dt-look")];
  let state = "idle", free = true, timers = [], run = 0;
  const EMPTY = '<span class="dt-empty"><span><svg viewBox="0 0 48 48" fill="none" stroke="#6e5640" stroke-width="2" stroke-linecap="round"><circle cx="24" cy="27" r="13"/><path d="M14 18l-3-8 9 4M34 18l3-8-9 4"/><circle cx="19" cy="26" r="1.6" fill="#6e5640"/><circle cx="29" cy="26" r="1.6" fill="#6e5640"/></svg>미리보기 전</span></span>';
  const DRAW = '<span class="dt-draw"><i class="wisp"></i><i class="wisp w2"></i><i class="ball"></i><i class="ndl"></i><span class="lbl">그리는 중<i>.</i><i>.</i><i>.</i></span></span>';
  const tile = b => b.querySelector(".dt-tile");
  const label = () => { btn.textContent = `펫 만들기 · ${free ? "무료 5장" : "500원"}`; };
  const later = (ms, f) => { const r = run; timers.push(setTimeout(() => r === run && f(), ms)); };
  function reset(){
    run++; timers.forEach(clearTimeout); timers = []; state = "idle";
    looks.forEach(b => { tile(b).innerHTML = EMPTY; b.classList.remove("made"); });
    pick.classList.add("pre"); btn.textContent = "펫 미리보기"; btn.style.setProperty("--c", "var(--sun)"); btn.disabled = false;
    note.textContent = "사진으로 털뭉치·피규어 두 가지를 그려 볼게요 · 미리보기는 무료"; $("dtNoti").classList.remove("on");
  }
  function reveal(b){
    const t = tile(b), d = t.querySelector(".dt-draw");
    d.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 220, fill: "forwards" }).finished.then(() => d.remove());
    const im = document.createElement("img"); im.className = "pet"; im.src = t.dataset.img; im.alt = ""; t.appendChild(im);
    im.animate([{ transform: "translateY(10px) scale(.55)", opacity: 0 }, { transform: "translateY(-4px) scale(1.06,.96)", opacity: 1, offset: .6 }, { transform: "scale(.98,1.03)", offset: .8 }, { transform: "none", opacity: 1 }],
      { duration: 560, easing: "ease-out" });
    puff(t, t.clientWidth / 2, t.clientHeight * .78, 6, 30, 3); sparkle(t, t.clientWidth / 2, t.clientHeight * .4, 4, 4);
  }
  btn.onclick = () => {
    if (state === "idle") {
      state = "drawing"; btn.disabled = true; btn.textContent = "그리는 중…"; btn.style.setProperty("--c", "var(--paper)");
      note.textContent = "털뭉치와 피규어를 같이 그리고 있어요";
      looks.forEach(b => tile(b).innerHTML = DRAW);
      later(2000, () => reveal(looks[0])); later(2600, () => reveal(looks[1]));
      later(3000, () => { state = "ready"; pick.classList.remove("pre"); btn.disabled = false;
        btn.style.setProperty("--c", "var(--sun)"); label(); note.textContent = "털뭉치·피규어 둘 다 한 번에 만들어요 · 1장만 써요";
        btn.animate([{ transform: "scale(1)" }, { transform: "scale(1.05)" }, { transform: "scale(1)" }], { duration: 420, easing: "cubic-bezier(.3,1.6,.5,1)" }); });
    } else if (state === "ready") make();
  };
  /** one tile: the capsule wobbles (cracks ride on it), then pops; delay staggers the second one */
  function hatch(b, delay){
    const t = tile(b);
    t.innerHTML = `<div class="dt-hatch"><div class="wob"><img class="con" src="${t.dataset.img}" alt=""><img class="bot" src="img/cap-closed.png" alt=""><img class="tp" src="img/cap-closed.png" alt="">
      <svg viewBox="0 0 100 100"><path d="M52 8 L46 20 L55 28 L48 41"/><path class="hl" d="M53 9 L47 20 L56 28 L49 40"/><path d="M48 41 L37 49 L41 60"/><path d="M48 41 L61 51 L58 66 L66 72"/></svg></div></div>`;
    const wob = t.querySelector(".wob"), paths = [...t.querySelectorAll("path")];
    wob.animate([{ transform: "scale(.4)", opacity: 0 }, { transform: "scale(1.08,.94)", opacity: 1, offset: .6 }, { transform: "none", opacity: 1 }], { duration: 420, delay, easing: "ease-out", fill: "backwards" });
    const r0 = run, T0 = performance.now() + delay;
    (function wobble(now){ if (run !== r0 || !wob.isConnected || state !== "making") return;
      const s = Math.max(0, now - T0) / 1000, k = (s % 1.15) * 1000, amp = 4 + Math.min(s, 6) * 2.2;     // a kick every 1.15 s, stronger as it nears
      wob.style.transform = `rotate(${pend(amp, k, .02, 260)}deg) scaleY(${1 - .025 * Math.exp(-k / 120)})`;
      requestAnimationFrame(wobble); })(performance.now());
    [[0, 1], [2], [3]].forEach((g, i) => later(delay + 1500 + i * 1450, () => g.forEach(j => {
      paths[j].style.transition = "stroke-dashoffset .45s ease-out"; paths[j].style.strokeDashoffset = 0; })));
    return async () => {
      wob.style.transform = "";
      await once(wob, [{ transform: "none" }, { transform: "scale(1.1,.86) translateY(6px)" }], { duration: 160, easing: "ease-in" });
      const tp = wob.querySelector(".tp"), bot = wob.querySelector(".bot"), svg = wob.querySelector("svg"), con = wob.querySelector(".con");
      con.style.opacity = 0; svg.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 120, fill: "forwards" });
      tp.animate([{ transform: "none", opacity: .78 }, { transform: "translate(26px,-90px) rotate(160deg)", opacity: 0 }], { duration: 560, easing: "cubic-bezier(.2,.7,.4,1)", fill: "forwards" });
      bot.animate([{ transform: "none", opacity: 1 }, { transform: "translate(-10px,70px) rotate(-25deg) scale(.9)", opacity: 0 }], { duration: 520, easing: "cubic-bezier(.5,0,.9,.6)", fill: "forwards" });
      wob.animate([{ transform: "scale(1.1,.86) translateY(6px)" }, { transform: "none" }], { duration: 200, fill: "forwards" });
      const pop = document.createElement("img"); pop.className = "dt-pop"; pop.src = t.dataset.img; pop.alt = ""; t.querySelector(".dt-hatch").appendChild(pop);
      puff(t, t.clientWidth / 2, t.clientHeight / 2, 10, 46, 5); sparkle(t, t.clientWidth / 2, t.clientHeight * .35, 5, 6);
      pop.animate([{ transform: "translateY(30px) scale(.3,.5)", opacity: 0 }, { transform: "translateY(-16px) scale(.9,1.15)", opacity: 1, offset: .45 },
        { transform: "translateY(4px) scale(1.1,.9)", offset: .7 }, { transform: "translateY(-2px) scale(.98,1.02)", offset: .85 }, { transform: "none", opacity: 1 }], { duration: 760, easing: "ease-out" });
      later(620, () => wob.remove());   // the capsule is gone; only the pet stays
      b.classList.add("made");
    };
  }
  function make(){
    state = "making";
    btn.disabled = true; btn.textContent = "만드는 중… 나가도 돼요"; btn.style.setProperty("--c", "var(--paper)");
    note.textContent = "보통 1~3분 · 다 되면 알림으로 알려 줄게요";
    const pops = looks.map((b, i) => hatch(b, i * 260));
    later(5400, () => $("dtNoti").classList.add("on"));
    later(6100, () => { state = "hatching"; pops[0](); });
    later(6400, () => { pops[1]();
      state = "done"; btn.disabled = false; btn.textContent = "홈 화면에 데려가기"; btn.style.setProperty("--c", "var(--sun)");
      note.textContent = "털뭉치·피규어 삼색이가 왔어요! 홈에 띄울 쪽은 펫 설정에서 골라요"; });
    later(9000, () => $("dtNoti").classList.remove("on"));
  }
  $("dtReset").onclick = reset;
  $("dtFree").onclick = () => { free = !free; $("dtFree").setAttribute("aria-pressed", !free); if (state === "ready") label(); };
  $("dtStar").onclick = () => { const b = $("dtStar"), on = b.getAttribute("aria-pressed") !== "true"; b.setAttribute("aria-pressed", on);
    b.querySelector("svg").animate([{ transform: "scale(.6) rotate(-30deg)" }, { transform: "scale(1.35) rotate(8deg)" }, { transform: "none" }], { duration: 380, easing: "ease-out" }); };
  reset();
}

/* ---------- 6 pet settings (PetScreen sections, felt) ---------- */
function seg(el, onPick){
  const th = document.createElement("i"); th.className = "thumb"; el.prepend(th);
  const place = b => { th.style.left = b.offsetLeft + "px"; th.style.width = b.offsetWidth + "px"; };
  const btns = [...el.querySelectorAll("button")];
  btns.forEach(b => b.onclick = () => { btns.forEach(o => o.setAttribute("aria-pressed", o === b)); place(b); onPick(b.dataset.v); });
  const cur = btns.find(b => b.getAttribute("aria-pressed") === "true"); place(cur);
  return cur.dataset.v;
}
{
  const NOTE = { home: "홈 화면에서만 보이고, 앱을 열면 숨어요.",
    always: "앱을 쓰는 동안에도 녀석이 늘 있어요. 다만 홈이 아닌 앱에서는 키보드를 가리지 않게 양옆 벽 위쪽에만 붙어 있어요." };
  const wn = $("psWhereNote"); wn.textContent = NOTE[seg($("psWhere"), v => wn.textContent = NOTE[v])];
  // each made look is its own choice (PetScreen): 삼색이 has both, so it shows twice
  const PETS = [["calico", "삼색이", "img/calico-fluff.png", "털뭉치"], ["calico", "삼색이", "img/calico-figure.webp", "피규어"], ["monggeul", "몽글", "img/monggeul-front.webp", ""], ["ppiyak", "삐약", "img/ppiyak-front.webp", ""]];
  let pet = PETS[0], size = 56;
  const drawSizes = () => { $("psSizes").innerHTML = [["작게", 40], ["보통", 56], ["크게", 80]].map(([l, d]) =>
      `<button data-d="${d}" aria-pressed="${d === size}"><span class="bx" style="width:${d}px;height:${d}px"><img src="${pet[2]}" alt=""></span>${l}</button>`).join("");
    $("psSizes").querySelectorAll("button").forEach(b => b.onclick = () => { size = +b.dataset.d; drawSizes();
      $("psSizes").querySelector(`[data-d="${size}"] img`).animate([{ transform: "scale(.8)" }, { transform: "scale(1.1)" }, { transform: "none" }], { duration: 320, easing: "ease-out" }); }); };
  $("psWho").innerHTML = PETS.map(([id, n, src, look], i) => `<button class="felt" data-p="${i}" aria-pressed="${i === 0}"><img src="${src}" alt="">${n}${look ? `<small>${look}</small>` : ""}</button>`).join("");
  $("psWho").querySelectorAll("button").forEach(b => b.onclick = () => { pet = PETS[+b.dataset.p];
    $("psWho").querySelectorAll("button").forEach(o => o.setAttribute("aria-pressed", o === b)); drawSizes(); });
  drawSizes();
  const APPS = [["#e9786a", "▲", "유튜브"], ["#4caf74", "●", "메시지"], ["#506a8a", "▣", "카메라"], ["#f0b93a", "≡", "메모"], ["#9b6fd6", "♪", "음악"], ["#3e5577", "◷", "시계"], ["#ee8a3a", "▦", "달력"], ["#6d7785", "◎", "설정"]];
  let mode = "chosen", chosen = 0, count = 6;
  const icon = ([c, g, l]) => `<span class="ps-app"><i style="background:${c}">${g}</i>${l}</span>`;
  function apps(){
    const box = $("psApps");
    if (mode !== "chosen") {
      box.innerHTML = `<div class="ps-apps">${APPS.slice(0, count).map(icon).join("")}</div>
        <div class="k-seg felt ps-cnt" id="psCnt">${[4, 6, 8].map(n => `<button data-v="${n}" aria-pressed="${n === count}">${n}개</button>`).join("")}</div>
        <p class="ps-note">${mode === "recent" ? `두 번 톡톡 누르면 방금까지 쓴 앱 ${count}개가 최근 순서로 떠요.` : `두 번 톡톡 누르면 최근 일주일 동안 오래 쓴 앱 ${count}개가 떠요.`}</p>`;
      seg($("psCnt"), v => { count = +v; apps(); });
      return;
    }
    box.innerHTML = `<div class="ps-apps">${APPS.slice(0, chosen).map(icon).join("")}<span class="ps-app add" id="psAdd" role="button"><i>${chosen ? "✎" : "+"}</i>${chosen ? "바꾸기" : "추가"}</span></div>
      <p class="ps-note">${chosen ? `두 번 톡톡 누르면 말풍선에 ${chosen}개 앱과 [펫 끄기]가 떠요(최대 8개).` : "앱을 고르면 두 번 톡톡 눌렀을 때 말풍선에 떠요. 말풍선 끝의 [펫 끄기]로 펫을 끌 수 있어요."}</p>`;
    $("psAdd").onclick = () => { chosen = chosen >= 4 ? 0 : chosen + 2; apps(); };
  }
  seg($("psMode"), v => { mode = v; apps(); });
  apps();
}
