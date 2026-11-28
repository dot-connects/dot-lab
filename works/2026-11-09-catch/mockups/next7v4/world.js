// 10 felt world (camera off): a 360° ring pinned to azimuths (SceneSource/Yard). Drag or turn the phone to pan,
// tilt (vertical drag / beta) moves the horizon. Critters live at world x; the button is always above them.
{
  const scr = $("fw"), pano = $("fwPano"), rig = makeRig($("fwClaw")), go = $("fwGo");
  const PH = 860, P = 3980 * PH / 1000;            // panorama drawn 860 tall (40 px spare above and below for tilt)
  const HZ = -40 + .52 * PH;                        // horizon at tilt 0 (SceneSource.HORIZON)
  let pan = 2050, tilt = 0, vpan = 0, lastTouch = 0, busy = false, dex = 7, drag = null;
  const wrap = x => ((x % P) + P) % P;
  const NAMES = { monggeul: "몽글", dalsom: "달솜", bamtol: "밤톨", nyangsom: "냥솜", meongsom: "멍솜", ppiyak: "삐약" };
  const WALK = new Set(["monggeul", "dalsom", "bamtol"]);
  const crits = [["monggeul", 2120, .55], ["dalsom", 2260, .2], ["bamtol", 2010, .92], ["nyangsom", 2700, .45], ["meongsom", 3300, .75], ["ppiyak", 1500, .35], ["monggeul", 600, .6], ["dalsom", 1050, .85]]
    .map(([id, x, d], i) => {
      const el = document.createElement("div"); el.className = "fw-crit"; el.innerHTML = `<i class="fw-csh"></i><img alt="">`; scr.appendChild(el);
      return { id, el, img: el.querySelector("img"), sh: el.querySelector(".fw-csh"), x, d, dir: i % 2 ? -1 : 1, v: .022 + d * .02, f: 1, state: "walk", ph: Math.random() * 6, turnAt: 2000 + Math.random() * 5000 };
    });
  const size = c => 64 + c.d * 62;
  const feet = c => HZ + tilt + 118 + c.d * 190;
  function sx(c){ let s = wrap(c.x - pan); if (s > P - 300) s -= P; return s; }      // screen left edge of its box
  function drawCrit(c, now){
    const w = size(c), x = sx(c) - w / 2 + 180, y = feet(c) - w;
    const on = x > -w && x < 360 + w && c.state !== "held";
    c.el.style.display = on ? "" : "none"; if (!on) return;
    let bob = 0, sq = 0;
    if (c.state === "walk" && !WALK.has(c.id)) { const p = Math.abs(Math.sin(now / 170 + c.ph)); bob = -p * 6; sq = (1 - p) * .06; }
    if (c.state === "dazed") { bob = 0; sq = .04 * Math.sin(now / 60); }
    c.el.style.transform = `translate(${x}px,${y + (c.dy || 0)}px)`;
    c.el.style.zIndex = 10 + Math.round(c.d * 30);   // always under the claw (70), capsule (80) and the button (100)
    c.el.style.width = c.el.style.height = w + "px";
    c.img.style.transform = `translateY(${bob}px) scaleX(${c.dir}) scale(${1 + sq},${1 - sq})`;
    c.sh.style.opacity = c.dy ? .25 : .55;
  }
  setInterval(() => { for (const c of crits) if (c.state === "walk") {
    c.f = c.f % 4 + 1; c.img.src = WALK.has(c.id) ? `img/${c.id}-walk${c.f}.webp` : `img/${c.id}-front.webp`; } }, 140);
  let tPrev = performance.now();
  (function frame(now){
    const dt = Math.min(34, now - tPrev); tPrev = now;
    if (!drag && Math.abs(vpan) > .01) { pan += vpan * dt; vpan *= Math.pow(.004, dt / 1000); }
    for (const c of crits) {
      if (c.state === "walk") { c.x = wrap(c.x + c.dir * c.v * dt); c.turnAt -= dt; if (c.turnAt < 0) { c.dir *= -1; c.turnAt = 2500 + Math.random() * 6000; } }
      drawCrit(c, now);
    }
    pano.style.backgroundPosition = `${-wrap(pan)}px 0`; pano.style.transform = `translateY(${tilt}px)`;
    $("fwNeedle").style.transform = `rotate(${wrap(pan) / P * 360}deg)`;
    requestAnimationFrame(frame);
  })(tPrev);

  /* input: lower area pans/tilts the ring, the rail band moves the claw */
  scr.addEventListener("pointerdown", e => { if (e.target.closest("button")) return;
    const r = scr.getBoundingClientRect(), k = r.width / 360, y = (e.clientY - r.top) / k;
    drag = { x: e.clientX, y: e.clientY, mode: y < 190 && !busy ? "claw" : "pan", k, t: performance.now() }; vpan = 0; scr.setPointerCapture(e.pointerId); });
  scr.addEventListener("pointermove", e => { if (!drag) return; const dx = (e.clientX - drag.x) / drag.k, dy = (e.clientY - drag.y) / drag.k, now = performance.now();
    if (drag.mode === "claw") { rig.x = clamp(rig.x + dx, 0, 264); rig.draw(); }
    else { pan -= dx; tilt = clamp(tilt + dy * .45, -40, 40); vpan = -dx / Math.max(8, now - drag.t); lastTouch = now; }
    drag.x = e.clientX; drag.y = e.clientY; drag.t = now; });
  scr.addEventListener("pointerup", () => { if (drag && performance.now() - drag.t > 80) vpan = 0; drag = null; });
  addEventListener("deviceorientation", e => { if (e.alpha == null || performance.now() - lastTouch < 2500) return;
    pan = (360 - e.alpha) / 360 * P; if (e.beta != null) tilt = clamp((e.beta - 70) * 1.2, -40, 40); });

  const toast = t => { const el = $("fwToast"); el.textContent = t; el.classList.add("on"); clearTimeout(el._t); el._t = setTimeout(() => el.classList.remove("on"), 1500); };
  const heldEl = document.createElement("img"); heldEl.className = "fw-held"; heldEl.alt = ""; scr.appendChild(heldEl);
  const pivot = () => { const sw = rig.swing * Math.PI / 180, L = rig.drop + TIP; return [rig.cx() + Math.sin(-sw) * L, 48 + Math.cos(sw) * L]; };
  function hangHeld(w, a, slide = 0){       // the critter hangs by its top from the tips
    const [px, py] = pivot(); heldEl.style.width = w + "px";
    heldEl.style.transform = `translate(${px - w / 2}px,${py - 22 + slide}px) rotate(${a}deg)`; }
  async function riseEmpty(from, ms){
    await tween(ms, E.io, (t, r) => { rig.drop = lerp(from, 40, t); rig.swing = pend(2.5, r * ms, .013, 300); rig.draw(); });
    await rig.to(160, E.outQ, { close: 0, swing: 0 });
  }
  go.onclick = async () => {
    if (busy) return; busy = true; go.classList.add("press"); setTimeout(() => go.classList.remove("press"), 140);
    const cx = rig.cx();
    const hit = crits.filter(c => c.state === "walk" && Math.abs(sx(c) + 180 - cx) < size(c) * .42).sort((a, b) => b.d - a.d)[0];
    const target = hit ? Math.round(feet(hit) - size(hit) * .62 - 48 - TIP) : Math.round(feet({ d: .5 }) - 60 - 48 - TIP);
    if (hit) hit.state = "aimed";
    const dur = Math.min(1700, 650 + (target - 40) * 2.4);       // slow, heavy descent; the rise stays brisk
    await tween(dur, E.sine, (t, r) => { rig.drop = lerp(40, target + 5, t); rig.swing = Math.sin(r * Math.PI) * -.8; rig.draw(); });
    await rig.to(200, E.outQ, { drop: target, swing: 0 });
    if (!hit) {
      await rig.to(100, E.outC, { close: 1.32 }); puff(scr, rig.cx(), rig.tipY() - 4, 5, 16, 72);
      await tween(200, E.lin, (_, r) => { rig.swing = Math.sin(r * 30) * 2.2 * (1 - r); rig.draw(); });
      await sleep(140); toast("헛집었다"); await riseEmpty(target, 700); busy = false; return;
    }
    // grip: the chance is the app's (0.80 at the centre .. 0.35 at the edge)
    const off = clamp(Math.abs(sx(hit) + 180 - cx) / (size(hit) * .42), 0, 1), p = .8 - .45 * off, win = Math.random() < p;
    hit.state = "held"; const w = size(hit);
    heldEl.src = WALK.has(hit.id) ? `img/${hit.id}-holdA.webp` : `img/${hit.id}-front.webp`; hangHeld(w, 0); heldEl.style.display = "block";
    await rig.to(130, E.outC, { close: .72, squash: .04 }); rig.to(140, E.outQ, { squash: 0 });
    hangHeld(w, 0);
    await tween(120, E.outQ, t => hangHeld(w, 0, -6 * t));      // a little tug
    const T0 = performance.now(), sw = () => performance.now() - T0, upTo = 60;
    if (!win) {
      const mid = Math.round(lerp(target, upTo, .45 + Math.random() * .25));
      await tween(620, E.outQ, t => { rig.drop = lerp(target, mid, t); rig.draw(); hangHeld(w, pend(8, sw(), .012, 480), -6); });
      // it slides along the prongs (the player sees it coming), then drops
      await Promise.all([rig.to(260, E.inQ, { close: .35 }), tween(260, E.inQ, t => hangHeld(w, pend(8, sw(), .012, 480) * (1 - t), -6 + 22 * t))]);
      toast("흔들려서 빠졌다");
      // it falls in the same pose it hung in (no sprite swap mid-air), tipping a little; the world sprite takes over on landing
      const [px, py] = pivot(); let y = py - 22 + 16, vy = 0, a = 0; const floor = feet(hit) - w;
      await loop(dt => { vy += dt * .0042; y = Math.min(floor, y + vy * dt); a = Math.min(14, a + dt * .03);
        heldEl.style.transform = `translate(${px - w / 2}px,${y}px) rotate(${a}deg)`; return y >= floor; });
      heldEl.style.display = "none"; hit.x = wrap(pan + px - 180); hit.dy = 0; hit.state = "dazed";
      puff(scr, px, floor + w - 4, 6, 28, 40);
      riseEmpty(mid, 600);
      await sleep(900); hit.state = "walk"; hit.dy = 0; busy = false; return;
    }
    await tween(860, E.io, t => { rig.drop = lerp(target, upTo, t); rig.swing = pend(1, sw(), .012, 500); rig.draw(); hangHeld(w, pend(8, sw(), .012, 480), -6); });
    // WRAP at the top: the capsule closes round it
    const [px, py] = pivot();
    const cap = makeCapsule(scr, heldEl.src, [0, 120, 200, 300][dex % 4]); cap.x = px; cap.y = py + 30;
    cap.top.style.opacity = cap.bot.style.opacity = 0; cap.con.style.visibility = "hidden"; cap.draw();
    const s0 = w;
    await closeCapsule(scr, cap, async () => {
      await tween(260, E.inC, t => { const ww = lerp(s0, 48, t); heldEl.style.width = ww + "px";
        heldEl.style.transform = `translate(${px - ww / 2}px,${lerp(py - 28, py + 30 - ww * .55, t)}px)`; });
      heldEl.style.display = "none"; cap.con.style.visibility = "";
    });
    await sleep(80);
    rig.to(180, E.outQ, { close: 0, swing: 0 });
    dex++; $("fwDex").textContent = `도감 ${dex}`; toast(`${NAMES[hit.id]} 뽑았다! 도감 +1`);
    await dropAndRoll(cap, 752, 360);
    cap.remove();
    hit.x = wrap(pan - 260); hit.dir = 1; hit.state = "walk";       // another one walks in from the left, off screen
    busy = false;
  };
}
