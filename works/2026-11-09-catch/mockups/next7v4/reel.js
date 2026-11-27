// 9 reel: the existing reel's beats, captions and timing (out/framed-v2.mp4, 35.3 s), redrawn with the felt assets.
// Everything is a pure function of t (render(t)), so the bar can scrub to any frame.
// 11-28: one scene added after the wall, "날씨도 알려줘요" (WA..WB, the baked rain act). The original timeline
// is paused under it (first half at WA, second half already at WR, where the montage is in), so nothing else moved.
{
  const WA = 24.4, WB = 27.1, WR = 24.72;
  const R = $("rl"), DUR = 35.3 + (WB - WA) - (WR - WA);
  const sg = (t, a, b) => clamp((t - a) / (b - a), 0, 1);
  const win = (t, a, b, f = .17) => Math.min(sg(t, a, a + f), 1 - sg(t, b - f, b));     // fade in at a, out at b
  const ICON = '<span class="rl-ic"><img src="img/icon-fg.webp" alt=""></span>';
  R.innerHTML = `
    <span class="rl-pill">그냥 시켜봄</span><div class="rl-title"><b>6번째</b> 그냥 시켜본 것</div>
    <div class="rl-phone"><div class="rl-scr" id="rlS">
      <div class="rl-layer rl-home" id="rlHome">
        <div class="rl-sb"><span>9:41</span><span>▾ ◢ ▮</span></div>
        <div class="rl-row">${[["#b4566e", "✿", "갤러리"], ["#2f7d6b", "17", "캘린더"], ["#5d6b78", "⚙", "설정"]].map(([c, g, l]) => `<span class="rl-app"><i style="background:${c}">${g}</i>${l}</span>`).join("")}<span class="rl-app" id="rlMyApp">${ICON}리얼뽑기</span></div>
        <div class="rl-cloud felt" id="rlCloud"><div>${[["#e9786a", "▲"], ["#4caf74", "●"], ["#506a8a", "▣"], ["#f0b93a", "≡"]].map(([c, g]) => `<i style="background:${c}">${g}</i>`).join("")}</div><p><span>펫 설정</span><span class="off">펫 끄기</span></p></div>
        <div class="rl-bub felt" id="rlBub">삼색이 여기 있어</div>
        <div class="rl-pet" id="rlPet"><img src="img/calico-fluff.png" alt=""></div>
        <div class="rl-hearts" id="rlHearts"><i>♥</i><i>♥</i><i>♥</i></div>
        <div class="rl-daze" id="rlDaze"><i>★</i><i>★</i><i>★</i></div>
        <div class="rl-dock">${[["#3c9a5f", "✆"], ["#3f7fd0", "✉"], ["#6a5bd0", "◍"], ["#d0504a", "◉"]].map(([c, g]) => `<i style="background:${c}">${g}</i>`).join("")}</div>
        <i class="rl-tap" id="rlTap"></i>
      </div>
      <div class="rl-layer" id="rlCat"><img class="rl-cover" src="img/cam2.jpg" alt=""></div>
      <div class="rl-layer" id="rlCatch">
        <img class="rl-cover" id="rlCatchBg" src="img/cam2.jpg" alt="">
        <div class="rl-sb"><span>9:41</span><span>▾ ◢ ▮</span></div>
        <div class="rl-rail"></div><div class="rl-chips"><span>카메라 끄기</span><span>도감 7</span></div>
        <div class="rl-found" id="rlFound"><i></i>고양이 찾았다!</div>
        <img class="rl-cut" id="rlCut" src="img/cam2-cat.png" alt="">
        <div class="rl-claw" id="rlClaw"><i class="rl-str" id="rlStr"></i><img id="rlClawImg" src="img/claw.png" alt=""></div>
        <div class="k-cap rl-capsule" id="rlCap"><div class="k-body"><img class="k-con" src="img/cam2-cat.png" alt=""><img class="k-bot" src="img/cap-closed.png" alt=""><img class="k-top" src="img/cap-closed.png" alt=""></div></div>
        <img class="rl-btn" src="img/button.png" alt="">
        <div class="rl-flash" id="rlFlash"></div>
      </div>
      <div class="rl-layer" id="rlHatch">
        <div class="rl-rays"></div>
        <img class="rl-fluff" id="rlFluff" src="img/calico-fluff.png" alt="">
        <span class="rl-name" id="rlName">삼색이</span>
      </div>
      <div class="rl-layer rl-dex" id="rlDex">
        <div class="rl-dexin" id="rlDexIn">
          <div class="rl-dhead"><span>‹ 뽑기</span><span>펫 설정</span></div>
          <div class="rl-dtitle"><b>도감</b><span id="rlCount">털뭉치 7 / 16</span></div>
          <div class="rl-grid">${[["cam2-sticker.png", "삼색이", 1], ["whitedog-cut.webp", "흰둥이"], ["monggeul-front.webp", "몽글"], ["ppiyak-front.webp", "삐약"], ["dalsom-front.webp", "달솜"], ["bamtol-front.webp", "밤톨"], ["nyangsom-front.webp", "냥솜"], ["meongsom-front.webp", "멍솜"], [0, "?"]]
            .map(([f, n, nw], i) => `<span class="rl-cell felt${nw ? " nw" : ""}${f ? "" : " q"}" ${nw ? 'id="rlNew"' : ""}>${nw ? "<em>NEW</em>" : ""}${f ? `<img src="img/${f}" alt="">` : "<b>?</b>"}${n}</span>`).join("")}</div>
        </div>
      </div>
      <div class="rl-layer rl-mont" id="rlMont">
        <div class="rl-mgrid">${["e01", "f01", "e02", "f03", "src00", "g06"].map(n => `<span class="rl-m"><img class="ph" src="img/reel/${n}.jpg" alt=""><img class="fl" src="img/reel/${n}-fluff.webp" alt=""></span>`).join("")}</div>
        <div class="rl-bear">${["g06", "g03"].map(n => `<span class="rl-b"><img class="fl" src="img/reel/${n}-fluff.webp" alt=""><img class="ph" src="img/reel/${n}.jpg" alt=""></span>`).join("")}</div>
      </div>
      <div class="rl-layer rl-wx" id="rlWx">
        <div class="rl-sb"><span>8:12</span><span>▾ ◢ ▮</span></div>
        <div class="rl-wclock">8:12</div><div class="rl-wdate">11월 28일 금요일 · 비 12°</div>
        <div class="rl-wbub felt" id="rlWxBub">비 와! 우산 챙겨</div>
        <img class="rl-wpet" id="rlWxPet" alt="">
        <div class="rl-dock">${[["#3c9a5f", "✆"], ["#3f7fd0", "✉"], ["#6a5bd0", "◍"], ["#d0504a", "◉"]].map(([c, g]) => `<i style="background:${c}">${g}</i>`).join("")}</div>
      </div>
      <div class="rl-layer rl-end" id="rlEnd">
        ${ICON}<b class="rl-en">리얼뽑기</b><p>우리 아이도, 움직이는 털뭉치로</p>
        <img class="rl-endpet" id="rlEndPet" src="img/calico-fluff.png" alt="">
        <span class="rl-cta">안드로이드 먼저 해 보기</span><small>프로필 링크에서</small>
      </div>
      ${[[.25, 1.5, 54, "제 폰에 누가 <b>살거든요</b>"], [1.7, 3.35, 290, "우리 집 <b>고양이</b>에요"], [3.6, 7.5, 318, "폰으로 비추고<br><b>집게</b>로 집으니까"],
         [9.25, 10.75, 96, "<b>저렇게</b> 돼서 나왔어요"], [10.85, 12.5, 66, "도감에 <b>이름</b>도 올라가요"], [13.5, 15.1, 54, "톡 치면 <b>좋아하고요</b>"],
         [15.25, 18.05, 48, "<b>두 번</b> 치면<br>자주 쓰는 앱이 나와요"], [18.4, 21.5, 54, "휘 튕기면 <b>통통</b>"], [21.75, 24.6, 54, "벽에도 잘 <b>붙어</b> 있네요"],
         [24.75, 27.6, 34, "강아지든 사람이든<br>다 되더라고요"], [27.75, 29.2, 34, "머리도 옷도<br>찍힌 <b>그때 모습</b>이라"], [29.3, 31.1, 34, "안고 있던 <b>곰인형</b>도<br>같이 와요"]]
        .map(([a, b, y, h]) => `<div class="rl-cap" data-a="${a}" data-b="${b}" style="top:${y}px">${h}</div>`).join("")}
      <div class="rl-cap" data-a="${WA + .3}" data-b="${WB - .05}" data-real="1" style="top:54px">날씨도 <b>알려줘요</b></div>
    </div></div>
    <div class="rl-foot" id="rlFoot"><b>리얼뽑기</b>· 우리 아이를 뽑아 폰에 키우는 앱</div>
    <div class="rl-foot2" id="rlFoot2"><b>지금 테스터 모집 중</b>많이 써 주시면 진짜 출시해 볼게요</div>`;
  const q = id => $(id), caps = [...R.querySelectorAll(".rl-cap[data-a]")];
  const L = { home: q("rlHome"), cat: q("rlCat"), catch: q("rlCatch"), hatch: q("rlHatch"), dex: q("rlDex"), mont: q("rlMont"), end: q("rlEnd"), wx: q("rlWx") };
  // the weather scene's frames (nyangsom, rain: act then loop), loaded up front so scrubbing never waits
  const WXF = [...Array.from({ length: 26 }, (_, i) => `img/wx3d/nyangsom-rain-act-${String(i).padStart(2, "0")}.webp`),
    ...Array.from({ length: 8 }, (_, i) => `img/wx3d/nyangsom-rain-loop-${String(i).padStart(2, "0")}.webp`)];
  WXF.forEach(src => { const i = new Image(); i.src = src; });
  const set = (el, o, tf) => { el.style.opacity = o; if (tf != null) el.style.transform = tf; };

  // the flick: a deterministic little physics run (walls 0..260, floor at the pet line)
  const FLICK = (() => { const out = []; let x = 120, y = 330, vx = 640, vy = -760, rot = 0;
    for (let i = 0; i <= 200; i++) { out.push([x, y, rot]); const dt = 1 / 100;
      vy += 1700 * dt; x += vx * dt; y += vy * dt; rot += vx * dt * 1.4;
      if (x > 222) { x = 222; vx *= -.72; } if (x < 0) { x = 0; vx *= -.72; }
      if (y > 330) { y = 330; vy *= -.42; vx *= .7; if (Math.abs(vy) < 60) vy = 0; } }
    return out; })();

  function render(T){
    const t = T < WA ? T : T < WB ? (T < (WA + WB) / 2 ? WA : WR) : T - WB + WR;
    // ---- the weather scene (real time T): same home, the pet opens its umbrella, one line
    const wxA = win(T, WA, WB, .25); set(L.wx, wxA);
    { const i = Math.floor((T - WA - .3) * 12), n = i < 0 ? 0 : i < 26 ? i : 26 + (i - 26) % 8, src = WXF[n];
      if (!q("rlWxPet").src.endsWith(src)) q("rlWxPet").src = src;
      const b = q("rlWxBub"), k = sg(T, WA + .75, WA + 1.05); b.style.opacity = k; b.style.transform = `scale(${.5 + .5 * E.back(k, 2.4)})`; }
    // ---- layer windows
    const homeA = Math.max(win(t, 0, 1.75, .12), win(t, 12.7, 24.7, .25));
    set(L.home, homeA); set(L.cat, win(t, 1.6, 3.5, .1), `scale(${1.04 + sg(t, 1.6, 3.5) * .06})`);
    set(L.catch, win(t, 3.4, 9.35, .1)); set(L.hatch, win(t, 8.9, 10.85, .3)); set(L.dex, win(t, 10.75, 13.2, .1));
    set(L.mont, win(t, 24.55, 31.25, .15)); set(L.end, sg(t, 31.15, 31.45));
    q("rlFoot").style.opacity = 1 - sg(t, 31.1, 31.4); q("rlFoot2").style.opacity = sg(t, 31.2, 31.5);
    for (const c of caps) { const tt = c.dataset.real ? T : t, a = +c.dataset.a, b = +c.dataset.b, o = win(tt, a, b) * (c.dataset.real ? 1 : 1 - wxA);
      c.style.opacity = o; c.style.transform = `scale(${.9 + .1 * sg(tt, a, a + .17)})`; }

    // ---- home: pet walks / tapped / clouds / flick / wall
    const pet = q("rlPet"), bub = q("rlBub"), tap = q("rlTap"), cloud = q("rlCloud"), hearts = q("rlHearts"), daze = q("rlDaze");
    let px = 70 + sg(t, 0, 1.6) * 30, py = 330, pr = 0, sx = 1, sy = 1, face = 1;
    if (t > 12) px = 100;
    const hop = (a, d = .45, h = 26) => { const k = sg(t, a, a + d); return Math.sin(Math.PI * k) * h; };
    if (t < 2) py -= Math.abs(Math.sin(t * 9)) * 4;
    py -= hop(13.85) + hop(14.35, .4, 16);
    bub.style.opacity = win(t, .15, 1.75, .2);
    hearts.style.opacity = win(t, 13.9, 15.0, .2); hearts.style.transform = `translate(${px + 8}px,${300 - sg(t, 13.9, 15) * 30}px)`;
    // double tap -> the app cloud, a tap elsewhere closes it
    const cl = win(t, 15.85, 17.75, .2);
    cloud.style.opacity = cl; cloud.style.transform = `translate(${px - 64}px,214px) scale(${.6 + .4 * E.back(sg(t, 15.85, 16.15))})`;
    // flick
    if (t >= 18.65 && t < 21.6) { const i = Math.min(FLICK.length - 1, Math.floor((t - 18.65) * 100)); [px, py, pr] = FLICK[i]; }
    else if (t >= 18.3 && t < 18.65) { py = 330 - sg(t, 18.3, 18.65) * 10; sx = 1.08; sy = .92; }
    daze.style.opacity = win(t, 20.3, 21.5, .2); daze.style.transform = `translate(${px + 4}px,${py - 12}px) rotate(${t * 300}deg)`;
    // wall: dragged to the right edge, sticks, climbs
    if (t >= 21.6 && t < 25) { const k = E.io(sg(t, 21.9, 22.6)), c = sg(t, 22.9, 24.5);
      px = lerp(110, 226, k); py = lerp(330, 262, k) - c * 70 + Math.sin(c * 40) * 1.5; pr = -90 * sg(t, 22.5, 22.8); face = 1; }
    pet.style.transform = `translate(${px}px,${py}px) rotate(${pr}deg) scale(${sx * face},${sy})`;
    bub.style.transform = `translate(${px - 36}px,${py - 36}px)`;
    // the finger
    const taps = [[13.75, px + 18, 345], [15.45, px + 18, 345], [15.65, px + 18, 345], [17.6, 60, 120], [18.3, px + 18, 345], [21.75, 128, 345]];
    let to = 0, tx = 0, ty = 0;
    for (const [a, x, y] of taps) { const o = win(t, a, a + .35, .08); if (o > to) { to = o; tx = x; ty = y; } }
    if (t > 21.75 && t < 22.75) { to = win(t, 21.75, 22.75, .08); tx = px + 18; ty = py + 18; }
    tap.style.opacity = to; tap.style.transform = `translate(${tx - 18}px,${ty - 18}px) scale(${1 - .15 * to})`;
    // dex shrinks into the app icon, home appears
    if (t > 12.5 && t < 13.3) { const k = E.io(sg(t, 12.55, 13.2)); L.dex.style.opacity = 1 - sg(t, 12.95, 13.2);
      q("rlDexIn").style.transform = `translate(${k * 75}px,${k * -170}px) scale(${1 - k * .82})`; L.dex.style.borderRadius = k * 40 + "px"; }
    else { q("rlDexIn").style.transform = ""; }

    // ---- catch: claw moves over, drops, the cut-out shrinks into a capsule, lifts
    const claw = q("rlClaw"), str = q("rlStr"), cimg = q("rlClawImg"), cut = q("rlCut"), cap = q("rlCap"), cbody = cap.firstElementChild;
    const cx = lerp(30, 95, E.io(sg(t, 3.7, 4.6))), drop = lerp(18, 112, E.io(sg(t, 4.85, 5.4))) - lerp(0, 94, E.io(sg(t, 6.6, 7.6)));
    const close = sg(t, 5.45, 5.6);
    claw.style.transform = `translateX(${cx}px) rotate(${pend(2, Math.max(0, t - 4.6) * 1000, .012, 300)}deg)`;
    str.style.height = drop + "px"; cimg.style.top = drop + "px"; cimg.style.transform = `scaleX(${1 - .16 * close})`;
    const tipX = cx + 34.5, tipY = 30 + drop + 78;
    const suck = E.inC(sg(t, 5.75, 6.05));
    cut.style.opacity = t > 5.62 && t < 6.05 ? 1 : 0;
    cut.style.transform = `translate(${(tipX - 129) * suck}px,${(tipY + 22 - 270) * suck - Math.sin(Math.PI * suck) * 14}px) scale(${lerp(1.04, .26, suck)})`;
    q("rlFlash").style.opacity = win(t, 5.6, 5.95, .1) * .7;
    q("rlFound").style.opacity = 1;
    const capOn = sg(t, 5.9, 6.1), sw = pend(8, Math.max(0, t - 6.6) * 1000, .012, 480);
    // after the lift, the capsule floats to the middle and grows (the hatch screen takes over)
    const fl = E.io(sg(t, 7.7, 8.9));
    const kx = lerp(tipX, 130, fl), ky = lerp(tipY + 22, 220, fl), ks = lerp(.62, 1.9, fl);
    cap.style.opacity = capOn; cap.style.transform = `translate(${kx - 42}px,${ky - 45}px) scale(${ks * (1 + .15 * Math.sin(Math.PI * sg(t, 6.0, 6.3)))},${ks * (1 - .15 * Math.sin(Math.PI * sg(t, 6.0, 6.3)))})`;
    cbody.style.transform = `rotate(${fl > 0 ? Math.sin(t * 22) * 6 * sg(t, 8.2, 8.9) : sw}deg)`;
    q("rlCatchBg").style.filter = `blur(${fl * 6}px) brightness(${1 - fl * .25})`;
    for (const el of R.querySelectorAll("#rlCatch .rl-rail,#rlCatch .rl-chips,#rlCatch .rl-btn,#rlCatch .rl-found,#rlClaw")) el.style.opacity = 1 - fl;
    // ---- hatch: the big capsule pops, the fluff comes out
    const pop = sg(t, 9.15, 9.3);
    const lid = LID_OPEN * (1 - E.inQ(sg(t, 6.02, 6.2)));
    cbody.querySelector(".k-top").style.transform = `translate(${pop * 30}px,${-pop * 120}px) rotate(${pop * 160 + lid}deg)`; cbody.querySelector(".k-top").style.opacity = .72 * (1 - pop);
    cbody.querySelector(".k-bot").style.transform = `translate(${-pop * 10}px,${pop * 140}px) rotate(${-pop * 30}deg)`; cbody.querySelector(".k-bot").style.opacity = 1 - pop;
    cbody.querySelector(".k-con").style.opacity = 1 - pop;
    const f = sg(t, 9.2, 9.75), fs = t < 9.2 ? 0 : E.back(f, 2.2);
    q("rlFluff").style.transform = `translate(-50%,0) scale(${fs * (1 + .06 * Math.sin(t * 6))},${fs * (1 - .06 * Math.sin(t * 6))})`;
    q("rlName").style.opacity = sg(t, 9.7, 9.9); q("rlName").style.transform = `translateX(-50%) scale(${.7 + .3 * E.back(sg(t, 9.7, 10))})`;
    // ---- dex: starts close on the new card, pulls back to the grid; 7 -> 8
    if (t < 12.5) { const z = E.io(sg(t, 10.85, 11.7)); q("rlDexIn").style.transform = `scale(${lerp(1.7, 1, z)})`; q("rlDexIn").style.transformOrigin = "50px 130px"; }
    q("rlCount").textContent = t > 11.5 ? "털뭉치 8 / 16" : "털뭉치 7 / 16";
    q("rlNew").style.transform = `scale(${1 + .08 * Math.sin(Math.PI * sg(t, 11.5, 11.8))})`;
    // ---- montage: photo -> fluff, 0.55 s apart; then they bounce; then the bears
    R.querySelectorAll(".rl-m").forEach((m, i) => { const a = 25.0 + i * .55, k = sg(t, a, a + .3);
      m.querySelector(".ph").style.opacity = 1 - k; const fb = E.back(sg(t, a + .1, a + .45), 2);
      m.querySelector(".fl").style.transform = `scale(${fb}) translateY(${-hop(27.9 + i * .12, .4, 10)}px)`;
      m.style.opacity = 1 - sg(t, 29.15, 29.35); });
    R.querySelectorAll(".rl-b").forEach((b, i) => { const a = 29.4 + i * .35;
      b.style.opacity = sg(t, a, a + .2); b.style.transform = `translateY(${(1 - E.back(sg(t, a, a + .45))) * 30}px)`;
      b.querySelector(".fl").style.transform = `translateY(${-hop(30.3 + i * .2, .45, 10)}px)`; });
    // ---- end card
    const e = R.querySelector("#rlEnd");
    e.querySelector(".rl-ic").style.transform = `scale(${E.back(sg(t, 31.3, 31.7))})`;
    e.querySelector(".rl-en").style.opacity = e.querySelector("p").style.opacity = sg(t, 31.6, 31.9);
    const wx = lerp(-60, 0, E.outC(sg(t, 31.9, 33))), wb = t < 33 ? Math.abs(Math.sin(t * 10)) * 5 : hop(33.6, .4, 14);
    q("rlEndPet").style.transform = `translate(${wx}px,${-wb}px)`;
    e.querySelector(".rl-cta").style.transform = `scale(${E.back(sg(t, 32.4, 32.75))})`;
    e.querySelector("small").style.opacity = sg(t, 32.7, 33);
    q("rlTime").textContent = T.toFixed(1) + "s";
  }

  let t = 0, playing = false, last = 0;
  const seek = q("rlSeek"); seek.max = DUR.toFixed(2);
  function frame(now){ if (!playing) return; t += (now - last) / 1000; last = now; if (t > DUR) t = 0; seek.value = t; render(t); requestAnimationFrame(frame); }
  const play = on => { playing = on; q("rlPlay").textContent = on ? "일시정지" : "재생"; if (on) { last = performance.now(); requestAnimationFrame(frame); } };
  q("rlPlay").onclick = () => play(!playing);
  seek.oninput = () => { t = +seek.value; render(t); };
  render(0);
  new IntersectionObserver(es => es.forEach(e => play(e.isIntersecting)), { threshold: .3 }).observe(q("c9"));
}
