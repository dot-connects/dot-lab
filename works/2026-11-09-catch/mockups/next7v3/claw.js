const $ = id => document.getElementById(id);
const wait = ms => new Promise(r => setTimeout(r, ms));

/* ---------- 1 claw ---------- */
const scr = $("claw"), cb = $("cb"), str = $("str"), capw = $("capw");
const W = 360, FLOOR = [560, 640, 715];
const crits = [["monggeul", 40, 0], ["dalsom", 190, 1], ["bamtol", 120, 2]].map(([id, x, row]) => {
  const el = document.createElement("div"); el.className = "crit";
  const s = 1.2 + row * .22; el.innerHTML = `<img src="img/${id}-walk1.webp" alt="">`;
  scr.appendChild(el);
  return { id, el, img: el.firstChild, x, y: FLOOR[row], s, dir: row % 2 ? -1 : 1, f: 1, held: false, gone: false, speed: .5 + row * .15 };
});
let clawX = 132, busy = false, dex = 7;
function placeClaw(){ cb.style.transform = `translateX(${clawX}px)`; }
placeClaw();
function tick(){
  for (const c of crits) {
    if (c.held || c.gone) continue;
    c.x += c.dir * c.speed; if (c.x < -10 || c.x > W - 84 * c.s) c.dir *= -1;
    c.el.style.transform = `translate(${c.x}px,${c.y - 84 * c.s}px) scale(${c.s})`; c.img.style.transform = `scaleX(${c.dir})`;
    c.el.style.zIndex = 10 + Math.round(c.y / 10);
  }
}
setInterval(() => { for (const c of crits) if (!c.held && !c.gone) { c.f = c.f % 4 + 1; c.img.src = `img/${c.id}-walk${c.f}.webp`; } }, 150);
(function loop(){ tick(); requestAnimationFrame(loop); })();
let drag = null;
scr.addEventListener("pointerdown", e => { if (busy || e.target.closest("button")) return; drag = e.clientX; scr.setPointerCapture(e.pointerId); });
scr.addEventListener("pointermove", e => { if (drag == null) return; const k = scr.getBoundingClientRect().width / W;
  clawX = Math.max(0, Math.min(W - 96, clawX + (e.clientX - drag) / k)); drag = e.clientX; placeClaw(); });
scr.addEventListener("pointerup", () => drag = null);
function toast(t){ const el = $("toast"); el.textContent = t; el.classList.add("on"); setTimeout(() => el.classList.remove("on"), 1500); }
function anim(el, kf, o){ return el.animate(kf, { fill: "forwards", easing: "ease-in-out", ...o }).finished; }
$("go").onclick = async () => {
  if (busy) return; busy = true;
  const cx = clawX + 48;
  const hit = crits.filter(c => !c.gone && Math.abs(c.x + 42 * c.s - cx) < 40 * c.s).sort((a, b) => b.y - a.y)[0];
  const CLAW_H = 123, depth = hit ? Math.round(hit.y - 46 * hit.s - CLAW_H - 48) : 470;
  if (hit) hit.held = true;
  const imgD = cb.querySelector("img.c");
  await Promise.all([anim(str, [{ height: "40px" }, { height: depth + "px" }], { duration: 900 }),
    anim(imgD, [{ top: "40px" }, { top: depth + "px" }], { duration: 900 })]);
  cb.classList.add("shut"); await wait(300);
  if (!hit) { toast("아깝다! 다시 해 봐"); cb.classList.remove("shut");
    await Promise.all([anim(str, [{ height: depth + "px" }, { height: "40px" }], { duration: 700 }), anim(imgD, [{ top: depth + "px" }, { top: "40px" }], { duration: 700 })]);
    str.getAnimations().forEach(x => x.cancel()); imgD.getAnimations().forEach(x => x.cancel()); busy = false; return; }
  // capsule wraps it on the way up
  $("capIn").src = `img/${hit.id}-holdA.webp`; hit.el.style.visibility = "hidden";
  capw.style.display = "block"; capw.style.left = (clawX) + "px";
  const lift = [{ height: depth + "px" }, { height: "60px" }];
  const capTopFrom = 48 + depth + 70, capTopTo = 48 + 60 + 70;
  const a1 = anim(str, lift, { duration: 900 });
  const imgC = cb.querySelector("img.c");
  const a2 = anim(imgC, [{ top: depth + "px" }, { top: "60px" }], { duration: 900 });
  const a3 = anim(capw, [{ top: capTopFrom + "px", transform: "scale(.6)" }, { top: capTopTo + "px", transform: "scale(1)" }], { duration: 900 });
  await Promise.all([a1, a2, a3]);
  cb.classList.remove("shut"); await wait(150);
  // drop, bounce, roll out
  await anim(capw, [{ top: capTopTo + "px" }, { top: "600px", offset: .55 }, { top: "540px", offset: .72 }, { top: "600px", offset: .86 }, { top: "585px", offset: .93 }, { top: "600px" }],
    { duration: 1100, easing: "linear" });
  dex++; $("dexn").textContent = `도감 ${dex}`; toast(`${hit.id === "monggeul" ? "몽글" : hit.id === "dalsom" ? "달솜" : "밤톨"} 뽑았다! 도감 +1`);
  await anim(capw, [{ top: "600px", left: clawX + "px", transform: "rotate(0)" }, { top: "600px", left: W + 20 + "px", transform: "rotate(400deg)" }], { duration: 1000, easing: "ease-in" });
  capw.getAnimations().forEach(a => a.cancel()); capw.style.display = "none";
  str.getAnimations().forEach(a => a.cancel()); imgC.getAnimations().forEach(a => a.cancel()); imgC.style.top = "40px"; str.style.height = "40px";
  hit.gone = true; setTimeout(() => { hit.x = -80; hit.dir = 1; hit.gone = false; hit.held = false; hit.el.style.visibility = ""; }, 1800);
  busy = false;
};


