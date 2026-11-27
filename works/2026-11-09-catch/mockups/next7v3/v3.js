// v3 screens. claw.js (loaded first) defines $, wait, anim and the felt claw.
const sleepMs = ms => new Promise(r => setTimeout(r, ms));
const CAPS = ["#ff6b6b", "#ffb938", "#4da8ff", "#4ccb8c", "#a98bff", "#ff86c2"];

/* ---------- share card (4:5, built at 300x375) ---------- */
function card(kind){
  const real = kind === "real";
  const cc = real ? "#e2627a" : "#4da8ff", hue = real ? 0 : 190;
  return `<div class="sc" style="--cc:${cc}">
    <div class="bgi"></div><div class="rays"></div>
    <div class="brand felt"><img src="img/cap-closed.png" alt="">리얼뽑기</div>
    <div class="stamp"><span><small>도감</small><b>No.${real ? "8" : "3"}</b></span></div>
    <img class="capt" src="img/cap-open.png" alt="" style="filter:hue-rotate(${hue}deg)">
    <img class="who ${real ? "real" : ""}" src="img/${real ? "cam2-sticker.png" : "monggeul-front.webp"}" alt="">
    <img class="capb" src="img/cap-open.png" alt="" style="filter:hue-rotate(${hue}deg)">
    <div class="plate felt stitch">
      <div class="row1"><h4>${real ? "삼색이" : "몽글"}</h4><span class="kind">${real ? "진짜 고양이" : "털뭉치"}</span></div>
      <p>${real ? "11월 27일 오후 3시 · 우리 집 소파에서" : "11월 20일 · 풀밭에서"} 뽑음</p>
      <div class="tags">${real ? '<span class="on">잠꾸러기</span><span class="on">애교쟁이</span>' : '<span class="on">느긋함</span><span class="on">호기심</span>'}</div>
    </div></div>`;
}
document.querySelectorAll(".cardslot").forEach(s => {
  s.innerHTML = card(s.dataset.card);
  const k = s.clientWidth / 300; s.firstElementChild.style.transform = `scale(${k})`;
});

/* ---------- 1 camera claw ---------- */
{
  const scr = $("cam"), cb = $("ccb"), st = $("cstr"), img = cb.querySelector("img.c"), held = $("held"), capx = $("capx");
  let x = 130, variant = "sticker", outcome = "win", busy = false, dex = 8, drag = null;
  const BOX = { sticker: [77.5, 296.5, 202, 222, "img/cam2-sticker.png"], cut: [84.5, 303.5, 188, 208, "img/cam2-cat.png"] };
  const TIP = 108;   // claw image top -> prong tips, dp
  const AIR = ["헛집었다", "허공이었다", "아무것도 없었다"], SLIP = ["흔들려서 빠졌다", "미끄러졌다!"];
  const place = () => cb.style.transform = `translateX(${x}px)`;
  document.querySelectorAll("[data-v]").forEach(b => b.onclick = () => { variant = b.dataset.v; document.querySelectorAll("[data-v]").forEach(o => o.setAttribute("aria-pressed", o === b)); });
  document.querySelectorAll("[data-o]").forEach(b => b.onclick = () => { outcome = b.dataset.o; document.querySelectorAll("[data-o]").forEach(o => o.setAttribute("aria-pressed", o === b));
    if (outcome === "air") { x = 14; place(); } else if (!CAM_TOP[x + 48]) { x = 130; place(); } });
  scr.addEventListener("pointerdown", e => { if (busy || e.target.closest("button") || e.target.closest(".newcard")) return; drag = e.clientX; scr.setPointerCapture(e.pointerId); });
  scr.addEventListener("pointermove", e => { if (drag == null) return; const k = scr.getBoundingClientRect().width / 360;
    x = Math.max(0, Math.min(264, x + (e.clientX - drag) / k)); drag = e.clientX; place(); });
  scr.addEventListener("pointerup", () => drag = null);
  const toast = t => { const el = $("ctoast"); el.textContent = t; el.classList.add("on"); setTimeout(() => el.classList.remove("on"), 1500); };
  const stop = (...els) => els.forEach(e => e.getAnimations().forEach(a => a.cancel()));
  const clawTo = (from, to, ms) => Promise.all([anim(st, [{ height: from + "px" }, { height: to + "px" }], { duration: ms }), anim(img, [{ top: from + "px" }, { top: to + "px" }], { duration: ms })]);
  const showFound = on => { $("cring").style.opacity = on ? "" : 0; $("found").style.opacity = on ? "" : 0; };
  function puff(px, py){ for (let i = 0; i < 8; i++) { const p = document.createElement("i"); p.className = "puff"; p.style.left = px + "px"; p.style.top = py + "px"; scr.appendChild(p);
    const a = i / 8 * 6.28; p.animate([{ transform: "scale(.4)", opacity: .9 }, { transform: `translate(${Math.cos(a) * 26}px,${Math.sin(a) * 14}px) scale(1.3)`, opacity: 0 }], { duration: 500 }).finished.then(() => p.remove()); } }

  $("cgo").onclick = async () => {
    if (busy) return; busy = true;
    const cx = Math.round(x + 48), top = CAM_TOP[cx];
    const air = top == null;
    const depth = air ? 470 : Math.round(top + 12 - 48 - TIP);           // claw image top (relative to the box at 48)
    await clawTo(40, depth, 850);
    cb.classList.add("shut"); await sleepMs(260);
    if (air) { toast(AIR[Math.floor(Math.random() * 3)]); cb.classList.remove("shut"); await clawTo(depth, 40, 700); stop(st, img); busy = false; return; }
    const tipX = cx, tipY = 48 + depth + TIP;
    showFound(false);
    let capTop, capLeft = x + 6;
    if (variant === "small") {
      // flash, the cut-out shrinks straight into a capsule at the tips
      $("cflash").animate([{ opacity: .8 }, { opacity: 0 }], { duration: 300 });
      const [L, T, W, H, src] = BOX.cut; held.src = src; held.className = "held cut"; Object.assign(held.style, { display: "block", left: L + "px", top: T + "px", width: W + "px", height: H + "px", transformOrigin: "50% 50%" });
      capTop = tipY - 6;
      await anim(held, [{ transform: "none" }, { transform: `translate(${capLeft + 42 - (L + W / 2)}px,${capTop + 42 - (T + H / 2)}px) scale(.22)` }], { duration: 480, easing: "cubic-bezier(.5,0,.3,1)" });
      stop(held); held.style.display = "none";
      capx.style.display = "block"; capx.style.left = capLeft + "px"; capx.style.top = capTop + "px"; capx.querySelector(".sh").style.filter = "hue-rotate(0deg)";
      capx.animate([{ transform: "scale(.6)" }, { transform: "scale(1)" }], { duration: 220 });
      // up; a lost roll wobbles and pops open on the way
      const upTo = 60, upCap = 48 + upTo + TIP - 6;
      if (outcome === "slip") {
        const mid = Math.round((depth + upTo) / 2);
        await Promise.all([clawTo(depth, mid, 650), anim(capx, [{ top: capTop + "px" }, { top: capTop - (depth - mid) + "px" }], { duration: 650 })]);
        await anim(capx, [{ transform: "rotate(0)" }, { transform: "rotate(-14deg)" }, { transform: "rotate(12deg)" }, { transform: "rotate(-8deg)" }, { transform: "rotate(0)" }], { duration: 520 });
        const cy = capTop - (depth - mid);
        stop(capx); capx.style.display = "none"; puff(capLeft + 42, cy + 42); toast(SLIP[1]);
        held.style.display = "block";
        await anim(held, [{ transform: `translate(${capLeft + 42 - (L + W / 2)}px,${cy + 42 - (T + H / 2)}px) scale(.22)`, opacity: 1 },
          { transform: "translate(0,-30px) scale(.9)", opacity: 1, offset: .7 }, { transform: "none", opacity: 0 }], { duration: 650, easing: "cubic-bezier(.3,1.2,.5,1)" });
        stop(held); held.style.display = "none"; cb.classList.remove("shut"); await clawTo(mid, 40, 500); stop(st, img); showFound(true); busy = false; return;
      }
      await Promise.all([clawTo(depth, upTo, 1000), anim(capx, [{ top: capTop + "px" }, { top: upCap + "px" }], { duration: 1000 })]);
      capTop = upCap;
    } else {
      // sticker / cut-out: peels off and rises at full size, dangling from the tips
      const [L, T, W, H, src] = BOX[variant]; held.src = src; held.className = "held " + variant;
      const ox = tipX - L, oy = tipY - T;
      Object.assign(held.style, { display: "block", left: L + "px", top: T + "px", width: W + "px", height: H + "px", transformOrigin: `${ox}px ${oy}px` });
      if (variant === "sticker") await anim(held, [{ transform: "none" }, { transform: "scale(1.04) rotate(-4deg)" }, { transform: "scale(1.03) rotate(2deg)" }], { duration: 320 });
      const upTo = 60, dy = depth - upTo, rise = outcome === "slip" ? .55 : 1;
      const tUp = 1300 * rise, sway = [0, -7, 6, -4, 3, -1.5, 0];
      const kf = sway.map((r, i) => ({ transform: `translateY(${-dy * rise * i / (sway.length - 1)}px) scale(${variant === "sticker" ? 1.03 : 1}) rotate(${r}deg)` }));
      await Promise.all([clawTo(depth, Math.round(depth - dy * rise), tUp), anim(held, kf, { duration: tUp, easing: "linear" })]);
      if (outcome === "slip") {
        const yNow = -dy * rise;
        // slides along the prongs first, then lets go
        await anim(held, [{ transform: `translateY(${yNow}px) rotate(0)` }, { transform: `translateY(${yNow + 18}px) rotate(5deg)` }], { duration: 420 });
        cb.classList.remove("shut"); toast(SLIP[0]);
        if (variant === "sticker") {
          await anim(held, [{ transform: `translateY(${yNow + 18}px) rotate(5deg)` }, { transform: `translateY(${(yNow + 18) * .5}px) rotate(-14deg)` }, { transform: `translateY(${(yNow + 18) * .2}px) rotate(9deg)` }, { transform: "translateY(0) rotate(-2deg) scale(1.02)" }, { transform: "none" }],
            { duration: 1100, easing: "ease-in-out" });
          puff(L + W / 2, T + H - 6);
          await anim(held, [{ opacity: 1 }, { opacity: 0 }], { duration: 250 });
        } else {
          await anim(held, [{ transform: `translateY(${yNow + 18}px) rotate(5deg)`, opacity: 1 }, { transform: "translateY(0) rotate(0)", opacity: 1, offset: .8 }, { transform: "translateY(-6px)", opacity: 0 }], { duration: 650, easing: "cubic-bezier(.5,0,.9,.6)" });
          puff(L + W / 2, T + H - 10);
        }
        stop(held); held.style.display = "none";
        await clawTo(Math.round(depth - dy * rise), 40, 600); stop(st, img); showFound(true); busy = false; return;
      }
      // at the top the capsule closes round it and it shrinks in
      capTop = 48 + upTo + TIP - 6;
      capx.style.display = "block"; capx.style.left = capLeft + "px"; capx.style.top = capTop + "px";
      capx.animate([{ transform: "scale(.3)", opacity: 0 }, { transform: "scale(1)", opacity: 1 }], { duration: 300 });
      const end = `translate(${capLeft + 42 - (L + W / 2)}px,${capTop + 42 - (T + H / 2) - dy}px)`;
      held.style.transformOrigin = "50% 50%";
      await anim(held, [{ transform: `translateY(${-dy}px)` }, { transform: `${end} scale(.22)` }], { duration: 420, easing: "ease-in" });
      stop(held); held.style.display = "none";
    }
    cb.classList.remove("shut"); await sleepMs(120);
    const fl = 640;
    await anim(capx, [{ top: capTop + "px" }, { top: fl + "px", offset: .55 }, { top: fl - 60 + "px", offset: .72 }, { top: fl + "px", offset: .86 }, { top: fl - 14 + "px", offset: .93 }, { top: fl + "px" }], { duration: 1100, easing: "linear" });
    dex++; $("cdex").textContent = `도감 ${dex}`; toast("삼색이를 뽑았다!");
    await anim(capx, [{ top: fl + "px", left: capLeft + "px", transform: "rotate(0)" }, { top: fl + "px", left: "380px", transform: "rotate(420deg)" }], { duration: 900, easing: "ease-in" });
    stop(capx, st, img); capx.style.display = "none"; showFound(true);
    // the first-catch card (screen 2)
    const nc = $("newcard"); nc.innerHTML = `<div class="ribbon">새로 뽑았다!</div><div class="cardslot"></div><p style="color:#fffaf0;font-size:13px;margin:0">도감에 넣었어요 · <u>펫으로 만들 수 있어요 ›</u></p><div style="width:290px;display:flex;flex-direction:column;gap:8px;align-items:center"><button class="fbtn felt stitch">자랑하기</button><button class="flink light" id="ncClose">계속 뽑기</button></div>`;
    const slot = nc.querySelector(".cardslot"); slot.innerHTML = card("real"); slot.firstElementChild.style.transform = `scale(${270 / 300})`;
    nc.classList.add("on"); $("ncClose").onclick = () => nc.classList.remove("on");
    busy = false;
  };
}

/* ---------- 4 dex ---------- */
const CELLS = [
  ["cam2-sticker.png", "삼색이", "진짜 고양이", "real"], ["hatch", "까망이", "털뭉치 만드는 중"], ["monggeul-front.webp", "몽글", "털뭉치"],
  ["ppiyak-front.webp", "삐약", "털뭉치"], ["dalsom-front.webp", "달솜", "털뭉치"], ["bamtol-front.webp", "밤톨", "털뭉치"],
  ["nyangsom-front.webp", "냥솜", "털뭉치"], ["meongsom-front.webp", "멍솜", "털뭉치"], ["calico-fluff.png", "삼색이 펫", "털뭉치로 만든"],
  [0], [0], [0]];
$("grid").innerHTML = CELLS.map(([f, n, t, k]) =>
  f === "hatch" ? `<div class="cell felt stitch hatch"><div class="cap"><img src="img/cap-closed.png" alt="" style="filter:hue-rotate(180deg)"></div>${n}<small>${t}</small></div>`
  : f ? `<div class="cell felt stitch ${k || ""}">${k === "real" ? '<span class="rtag">진짜</span>' : ""}<img src="img/${f}" alt="">${n}<small>${t}</small></div>`
  : `<div class="cell felt q"><img src="img/cap-closed.png" alt=""><span class="qq">?</span><small style="visibility:hidden">-</small></div>`).join("");

/* ---------- 5 make pet: capsule hatch ---------- */
{
  let timers = [];
  const tim = document.querySelector("#tile1 .tim"), btn = $("mkBtn"), note = $("mkNote");
  const orig = tim.innerHTML;
  document.querySelectorAll("#det .pick button").forEach(b => b.onclick = () => { if (btn.dataset.state) return;
    document.querySelectorAll("#det .pick button").forEach(o => o.setAttribute("aria-pressed", o === b)); btn.textContent = `${b.dataset.k} 펫 만들기 · 무료 5장`; });
  function reset(){ timers.forEach(clearTimeout); timers = []; tim.innerHTML = orig; btn.textContent = "털뭉치 펫 만들기 · 무료 5장"; btn.style.setProperty("--c", "var(--sun)");
    note.textContent = "한 번 만들면 홈 화면에서 같이 살아요"; delete btn.dataset.state; $("noti").classList.remove("on"); }
  btn.onclick = () => {
    if (btn.dataset.state === "done") return;
    if (btn.dataset.state) return;
    btn.dataset.state = "making";
    tim.innerHTML = `<div class="hcap"><img class="c" src="img/cap-closed.png" alt=""><svg viewBox="0 0 100 90"><path d="M50 8 L44 22 L54 30 L47 44"/><path d="M47 44 L36 52 L40 64"/><path d="M47 44 L60 54 L57 70"/></svg><img class="open" src="img/cap-open.png" alt=""></div>`;
    btn.textContent = "만드는 중… 나가도 돼요"; btn.style.setProperty("--c", "var(--paper)");
    note.textContent = "보통 1~3분 · 다 되면 알림으로 알려 줄게요";
    const paths = tim.querySelectorAll("path");
    paths.forEach((p, i) => timers.push(setTimeout(() => p.style.strokeDashoffset = 0, 1200 + i * 1500)));
    timers.push(setTimeout(() => $("noti").classList.add("on"), 5600));
    timers.push(setTimeout(() => {
      const h = tim.querySelector(".hcap"); h.querySelector("img.c").style.display = "none"; h.querySelector("svg").style.display = "none";
      const o = h.querySelector(".open"); o.style.opacity = 1; o.classList.add("pop");
      h.insertAdjacentHTML("beforeend", '<img src="img/calico-fluff.png" alt="" class="pop" style="position:absolute;width:78%;top:2%;animation-delay:.15s">');
      btn.textContent = "홈 화면에 데려가기"; btn.style.setProperty("--c", "var(--sun)"); btn.dataset.state = "done";
      note.textContent = "털뭉치 삼색이가 왔어요! 펫 설정에서 바꿀 수 있어요";
    }, 6400));
    timers.push(setTimeout(() => $("noti").classList.remove("on"), 9000));
  };
  $("mkReset").onclick = reset;
}

/* ---------- 6 pet settings ---------- */
{
  const PETS = [["monggeul", "몽글"], ["ppiyak", "삐약"], ["calico", "삼색이"], ["dalsom", "달솜"], ["bamtol", "밤톨"]];
  const src = id => id === "calico" ? "img/calico-fluff.png" : `img/${id}-front.webp`;
  let pet = "monggeul", size = 1, f = 1;
  $("who").innerHTML = PETS.map(([id, n]) => `<button class="felt" data-p="${id}" aria-pressed="${id === pet}"><img src="${src(id)}" alt="">${n}</button>`).join("");
  const apply = () => { $("pvPet").style.transform = `scale(${size})`; $("pvPet").style.opacity = $("pOn").checked ? 1 : .15; };
  document.querySelectorAll("[data-p]").forEach(b => b.onclick = () => { pet = b.dataset.p; document.querySelectorAll("[data-p]").forEach(o => o.setAttribute("aria-pressed", o === b)); });
  document.querySelectorAll("[data-s]").forEach(b => b.onclick = () => { size = +b.dataset.s; document.querySelectorAll("[data-s]").forEach(o => o.setAttribute("aria-pressed", o === b)); apply(); });
  $("pOn").onchange = apply;
  $("moreBtn").onclick = () => { const m = $("moreBox"); m.hidden = !m.hidden; $("moreBtn").textContent = m.hidden ? "더 보기 ▾" : "접기 ▴"; };
  setInterval(() => { f = f % 4 + 1; $("pvImg").src = ["monggeul", "dalsom", "bamtol"].includes(pet) ? `img/${pet}-walk${f}.webp` : src(pet); }, 160);
  apply();
}

/* ---------- 7 weather (frames from the 3D bake, when present) ---------- */
{
  const STATES = [["rain", "비", "비 와! 우산 챙겨", "비 12° · 오후까지"], ["dust", "미세먼지", "미세먼지 나빠… 마스크!", "미세먼지 나쁨"], ["snow", "눈", "눈 온다! 따뜻하게", "눈 −2°"]];
  window.WX3D = window.WX3D || null;   // set by wx3d.js: { rain: [...urls], rainLoop: [...], dust: [...], dustLoop: [...], snow: [...] }
  const ctl = $("wxCtl"); let timer;
  ctl.innerHTML = STATES.map(([k, n], i) => `<button class="chip felt stitch" data-wx="${k}" aria-pressed="${i === 0}">${n}</button>`).join("");
  let play = function(k){
    document.querySelectorAll("[data-wx]").forEach(b => b.setAttribute("aria-pressed", b.dataset.wx === k));
    const s = STATES.find(x => x[0] === k); const t = $("wtalk"); t.innerHTML = `<span><b>${s[2]}</b><span class="t">${s[3]}</span></span>`;
    t.style.animation = "none"; void t.offsetWidth; t.style.animation = "";
    clearInterval(timer); const W = window.WX3D; if (!W) return;
    const seq = W[k] || [], loop = W[k + "Loop"] || seq.slice(-2); let i = 0;
    timer = setInterval(() => { $("wimg").src = i < seq.length ? seq[i] : loop[(i - seq.length) % loop.length]; i++; }, 1000 / 12);
  };
  document.querySelectorAll("[data-wx]").forEach(b => b.onclick = () => play(b.dataset.wx));
  let cur = "rain";
  const _play = play; play = k => { cur = k; _play(k); };
  document.querySelectorAll("[data-wp]").forEach(b => b.onclick = () => { window.WX3D = WX3D_ALL[b.dataset.wp];
    document.querySelectorAll("[data-wp]").forEach(o => o.setAttribute("aria-pressed", o === b)); play(cur); });
  window.addEventListener("load", () => play("rain"));
}

/* ---------- 9 reel first 3 s ---------- */
{
  const K = 300 / 360 * 1.0, OY = -70.2;   // cam2 (360x780 dp) shown 300 wide, cover, 60 % down
  const at = (el, [L, T, W, H]) => Object.assign(el.style, { left: L * 0.8333 + "px", top: T * 0.8333 + OY + "px", width: W * 0.8333 + "px", height: H * 0.8333 + "px" });
  at($("rRing"), [77.5, 296.5, 202, 222]); at($("rStk"), [77.5, 296.5, 202, 222]);
  Object.assign($("rFl").style, { left: "196px", top: "352px" }); Object.assign($("rFound").style, { left: "80px", top: "150px" });
  let T = [];
  function run(){
    T.forEach(clearTimeout); T = []; const s = $("rSub"), r = $("rRing"), k = $("rStk"), fl = $("rFl"), fd = $("rFound");
    [r, k, fl, fd].forEach(e => { e.getAnimations().forEach(a => a.cancel()); e.style.opacity = 0; }); s.textContent = "";
    const say = (txt, t0) => [...txt].forEach((c, i) => T.push(setTimeout(() => s.innerHTML = txt.slice(0, i + 1).replace("\n", "<br>"), t0 + i * 55)));
    say("우리 집 고양이\n사진 한 장으로", 100);
    T.push(setTimeout(() => { r.animate([{ opacity: 0, clipPath: "inset(0 100% 0 0)" }, { opacity: 1, clipPath: "inset(0 0 0 0)" }], { duration: 500, fill: "forwards" }); fd.animate([{ opacity: 0, transform: "translateY(6px)" }, { opacity: 1, transform: "none" }], { duration: 250, delay: 300, fill: "forwards" }); }, 800));
    T.push(setTimeout(() => { r.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 150, fill: "forwards" }); fd.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 150, fill: "forwards" });
      k.animate([{ opacity: 1, transform: "none" }, { opacity: 1, transform: "translateY(-30px) rotate(-6deg) scale(1.04)", offset: .6 }, { opacity: 0, transform: "translate(70px,10px) rotate(8deg) scale(.3)" }], { duration: 650, fill: "forwards", easing: "ease-in-out" }); }, 1700));
    T.push(setTimeout(() => { fl.animate([{ opacity: 0, transform: "translateY(30px) scale(.3)" }, { opacity: 1, transform: "none" }], { duration: 600, fill: "forwards", easing: "cubic-bezier(.3,1.6,.5,1)" }); s.textContent = ""; say("털뭉치 펫이\n생겼어요!", 0); }, 2250));
  }
  $("rRe").onclick = run;
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { run(); io.disconnect(); } }), { threshold: .4 });
  io.observe($("c9"));
}
