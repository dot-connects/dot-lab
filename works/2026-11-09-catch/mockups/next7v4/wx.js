// 7 weather: plays the 3D-baked frames (tools/bake-weather.*) — the action once, then its loop, at 12 fps on a canvas.
// What exists comes from img/wx3d/manifest.json, else from probing the files; missing states show "굽는 중".
{
  const STATES = [
    ["sun", "맑음", "햇볕 좋다~ 광합성 중", "맑음 18°"], ["hot", "더움", "더워… 녹는다", "더움 33°"], ["cold", "추움", "추워! 목도리 꽁꽁", "추움 −4°"],
    ["snow", "눈", "눈 온다! 뽀득뽀득", "눈 −2°"], ["rain", "비", "비 와! 우산 챙겨", "비 12° · 오후까지"], ["dust", "미세먼지", "미세먼지 나빠… 마스크!", "미세먼지 나쁨"]];
  const FPS = 12, cv = $("wxCv"), g = cv.getContext("2d"), base = "img/wx3d/";
  const nn = i => String(i).padStart(2, "0");
  const cache = {};                      // "critter/state" -> Promise<{act:[Image], loop:[Image]}>
  let manifest = null, pet = "monggeul", cur = "rain", seq = null, t0 = 0, token = 0;
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
  const drawFrame = im => { g.clearRect(0, 0, 300, 300); if (!im) return;
    const k = Math.min(300 / im.naturalWidth, 300 / im.naturalHeight), w = im.naturalWidth * k, h = im.naturalHeight * k;
    g.drawImage(im, (300 - w) / 2, 300 - h, w, h); };
  (function tick(now){
    if (seq) { const i = Math.floor((now - t0) / 1000 * FPS);
      drawFrame(i < seq.act.length ? seq.act[i] : seq.loop[(i - seq.act.length) % seq.loop.length]); }
    requestAnimationFrame(tick);
  })(performance.now());

  /* sky effects, cheap DOM particles */
  const sky = $("wxSky");
  const TINT = { sun: "radial-gradient(circle at 88% 6%,#fff6c8cc,#ffe08a44 30%,#0000 60%)", hot: "linear-gradient(#ff9a3c55,#ffcf7a22 50%,#0000)",
    cold: "linear-gradient(#bfe3ff55,#0000 60%)", snow: "linear-gradient(#dfeefd66,#0000 70%)", rain: "linear-gradient(#2b3d5577,#2b3d5533)", dust: "linear-gradient(#c7a97a88,#b9996c55)" };
  function fx(s){
    sky.innerHTML = ""; sky.style.background = TINT[s];
    const n = { rain: 46, snow: 34, dust: 26 }[s] || 0;
    for (let i = 0; i < n; i++) { const p = document.createElement("i");
      p.className = s === "rain" ? "wx-drop" : s === "snow" ? "wx-flake" : "wx-mote";
      p.style.left = Math.random() * 390 + "px"; p.style.top = (s === "dust" ? Math.random() * 760 : -20) + "px";
      p.style.animationDelay = -Math.random() * (s === "snow" ? 6 : s === "rain" ? .7 : 7) + "s";
      if (s === "snow") { const z = .5 + Math.random(); p.style.width = p.style.height = 4 + z * 6 + "px"; p.style.animationDuration = 5 + z * 3 + "s"; }
      sky.appendChild(p); }
  }
  async function play(s){
    cur = s; const my = ++token, st = STATES.find(x => x[0] === s);
    document.querySelectorAll("#wxCtl [data-wx]").forEach(b => b.setAttribute("aria-pressed", b.dataset.wx === s));
    const talk = $("wxTalk"); talk.innerHTML = `<b>${st[2]}</b><span>${st[3]}</span>`; talk.classList.remove("pop"); void talk.offsetWidth; talk.classList.add("pop");
    $("wxDate").textContent = `11월 28일 금요일 · ${st[3].split(" · ")[0]}`; fx(s);
    const f = await frames(pet, s);
    if (my !== token) return;
    const wait = $("wxWait");
    if (!f.act.length && !f.loop.length) { seq = null; drawFrame(null);
      wait.innerHTML = `<i class="yarn"></i>${st[1]} 굽는 중<small>3D 털 엔진에서 굽고 있어요</small>`; wait.hidden = false; talk.style.visibility = "hidden"; return; }
    wait.hidden = true; talk.style.visibility = ""; seq = f; t0 = performance.now();
    // warm the rest in the background so switching is instant
    STATES.forEach(([k]) => frames(pet, k));
  }
  $("wxCtl").innerHTML = '<span class="lab">날씨</span>' + STATES.map(([k, n]) => `<button class="chip felt stitch" data-wx="${k}" aria-pressed="${k === cur}">${n}</button>`).join("");
  document.querySelectorAll("#wxCtl [data-wx]").forEach(b => b.onclick = () => play(b.dataset.wx));
  document.querySelectorAll("#c7 [data-wp]").forEach(b => b.onclick = () => { pet = b.dataset.wp;
    document.querySelectorAll("#c7 [data-wp]").forEach(o => o.setAttribute("aria-pressed", o === b)); play(cur); });
  play(cur);   // starts now, not on window load
  window.__wx = { play, state: () => ({ cur, pet, playing: !!seq, act: seq?.act.length, loop: seq?.loop.length }) };
}
