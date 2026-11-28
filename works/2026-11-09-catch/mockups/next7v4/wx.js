// 7 weather: no screen effects, only the pet acts (3D-baked frames, tools/bake-weather.*), at 12 fps on a canvas.
// When the weather changes (or on the first home visit of the day) it plays the act once — act, a moment in that look,
// the act backwards to put the prop away — then idles as usual. Afterwards a tap sometimes plays it as one of its reactions.
// What exists comes from img/wx3d/manifest.json, else from probing the files; missing states show "굽는 중".
{
  const STATES = [
    ["sun", "맑음", "햇볕 좋다~ 광합성 중", "맑음 18°"], ["hot", "더움", "더워… 녹는다", "더움 33°"], ["cold", "추움", "추워! 목도리 꽁꽁", "추움 −4°"],
    ["snow", "눈", "눈 온다! 뽀득뽀득", "눈 −2°"], ["rain", "비", "비 와! 우산 챙겨", "비 12°"], ["dust", "미세먼지", "미세먼지 나빠… 마스크!", "미세먼지 나쁨"]];
  const FPS = 12, cv = $("wxCv"), g = cv.getContext("2d"), base = "img/wx3d/", zone = cv.parentElement;
  const nn = i => String(i).padStart(2, "0");
  const cache = {};                      // "critter/state" -> Promise<{act:[Image], loop:[Image]}>
  let manifest = null, pet = "monggeul", cur = "rain", f = null, anim = null, hop = null, token = 0, taps = 0;
  const manifestP = fetch(base + "manifest.json", { cache: "no-store" }).then(r => r.ok ? r.json() : null).catch(() => null).then(m => manifest = m);
  const img = src => new Promise(res => { const i = new Image(); i.onload = () => (i.decode ? i.decode().catch(() => {}) : Promise.resolve()).then(() => res(i)); i.onerror = () => res(null); i.src = src; });
  async function run(name, n){                 // known count: load all in parallel
    if (!name || !n) return [];
    const all = await Promise.all(Array.from({ length: n }, (_, i) => img(`${base}${name}-${nn(i)}.webp`)));
    return all.includes(null) ? [] : all;
  }
  async function probe(name){                 // unknown count: walk until the first missing frame
    if (!name) return [];
    const out = [];
    for (let i = 0; i < 80; i++) { const im = await img(`${base}${name}-${nn(i)}.webp`); if (!im) break; out.push(im); }
    return out;
  }
  function frames(p, s){
    const key = p + "/" + s;
    return cache[key] ??= (async () => {
      await manifestP;
      const m = manifest?.[p]?.[s];
      let act, lp;
      if (m) [act, lp] = await Promise.all([run(`${p}-${s}-act`, m.act), run(`${p}-${s}-loop`, m.loop)]);
      else [act, lp] = await Promise.all([probe(`${p}-${s}-act`), probe(`${p}-${s}-loop`)]);
      return { act, loop: lp.length ? lp : act.slice(-1) };
    })();
  }
  /** bottom-anchored, optional hop (dy) and squash */
  const drawFrame = (im, dy = 0, sx = 1, sy = 1) => { g.clearRect(0, 0, 300, 300); if (!im) return;
    const k = Math.min(300 / im.naturalWidth, 300 / im.naturalHeight), w = im.naturalWidth * k * sx, h = im.naturalHeight * k * sy;
    g.drawImage(im, (300 - w) / 2, 300 - h - dy, w, h); };
  (function tick(now){
    if (f) {
      if (anim) {                                       // the weather act: act -> its loop twice -> act backwards
        const i = Math.floor((now - anim.t0) / 1000 * FPS);
        if (i < anim.seq.length) drawFrame(anim.seq[i]);
        else { anim = null; talk(false); }
      }
      if (!anim) {                                      // everyday idle: the plain pose, breathing; a tap = a hop
        const idle = f.act[0], b = Math.sin(now / 520) * .012;
        let dy = 0, sx = 1 + b, sy = 1 - b;
        if (hop) { const r = (now - hop) / 520;
          if (r >= 1) hop = null;
          else if (r < .18) { const q = r / .18; sx = 1 + .1 * q; sy = 1 - .12 * q; }
          else { const q = (r - .18) / .82; dy = Math.sin(Math.PI * q) * 46; const land = Math.max(0, 1 - Math.abs(q - 1) * 8); sx = 1 - .05 * Math.sin(Math.PI * q) + .08 * land; sy = 2 - sx; } }
        drawFrame(idle, dy, sx, sy);
      }
    }
    requestAnimationFrame(tick);
  })(performance.now());

  const st = () => STATES.find(x => x[0] === cur);
  /** the one-line monologue: shows while the act plays */
  function talk(on){
    const el = $("wxTalk");
    if (!$("psTalk").checked) on = false;            // pet settings "말풍선" off = no weather line either
    if (!on) { el.classList.remove("on", "pop"); return; }
    el.textContent = st()[2]; el.classList.remove("pop"); void el.offsetWidth; el.classList.add("on", "pop");
  }
  function act(){
    if (!f || !f.act.length) return;
    const loop2 = [...f.loop, ...f.loop].slice(0, Math.max(10, f.loop.length * 2));
    anim = { t0: performance.now(), seq: [...f.act, ...loop2, ...[...f.act].reverse()] }; hop = null; talk(true);
  }
  function hearts(){
    for (let i = 0; i < 3; i++) { const h = document.createElement("i"); h.className = "wx-heart"; h.textContent = "♥"; zone.appendChild(h);
      h.style.left = 129 + (i - 1) * 22 + "px";
      h.animate([{ transform: "translateY(0) scale(.4)", opacity: 0 }, { transform: `translate(${(i - 1) * 8}px,-26px) scale(1.1)`, opacity: 1, offset: .35 }, { transform: `translate(${(i - 1) * 14}px,-60px) scale(.9)`, opacity: 0 }],
        { duration: 900, delay: i * 90, easing: "ease-out", fill: "backwards" }).finished.then(() => h.remove()); }
  }
  /** a tap: usually a hop with hearts; about one in three it plays today's weather act */
  function tap(){
    if (!f || anim) return;
    taps++;
    if (taps % 3 === 0) act(); else { hop = performance.now(); hearts(); }
  }
  async function show(s){
    cur = s; const my = ++token, x = st();
    document.querySelectorAll("#wxCtl [data-wx]").forEach(b => b.setAttribute("aria-pressed", b.dataset.wx === s));
    $("wxDate").textContent = `11월 28일 금요일 · ${x[3]}`;
    anim = null; talk(false);
    const fr = await frames(pet, s);
    if (my !== token) return;
    const wait = $("wxWait");
    if (!fr.act.length) { f = null; drawFrame(null);
      wait.innerHTML = `<i class="yarn"></i>${x[1]} 굽는 중<small>3D 털 엔진에서 굽고 있어요</small>`; wait.hidden = false; return; }
    wait.hidden = true; f = fr; taps = 0; act();
    STATES.forEach(([k]) => frames(pet, k));          // warm the rest so switching is instant
  }
  $("wxCtl").innerHTML = '<span class="lab">날씨</span>' + STATES.map(([k, n]) => `<button class="chip felt stitch" data-wx="${k}" aria-pressed="${k === cur}">${n}</button>`).join("");
  document.querySelectorAll("#wxCtl [data-wx]").forEach(b => b.onclick = () => show(b.dataset.wx));
  document.querySelectorAll("#c7 [data-wp]").forEach(b => b.onclick = () => { pet = b.dataset.wp;
    document.querySelectorAll("#c7 [data-wp]").forEach(o => o.setAttribute("aria-pressed", o === b)); show(cur); });
  $("wxTap").onclick = tap; cv.onclick = tap;
  $("wxFirst").onclick = () => { if (f) { taps = 0; act(); } };
  // "말풍선 켬/끔" here is the same switch as in pet settings (6)
  const bub = $("wxBub"), ps = $("psTalk");
  const syncBub = () => { bub.setAttribute("aria-pressed", ps.checked); bub.textContent = ps.checked ? "말풍선 켬" : "말풍선 끔"; if (!ps.checked) talk(false); };
  bub.onclick = () => { ps.checked = !ps.checked; syncBub(); };
  ps.addEventListener("change", syncBub);
  show(cur);   // starts now, not on window load
}
