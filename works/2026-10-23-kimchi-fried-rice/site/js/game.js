/* game.js — the whole round: start screen → rice 12 s → plating (auto) → egg → take-out → garnish → receipt.
   Owns the wok pose (phone tilt + the chef's toss), the clock (fixed 1/60 steps), scoring, words, HUD, sound cues. */
"use strict";
(function () {
  const K = window.KFR, T = K.T, W = K.W, clamp = K.clamp, smooth = K.smooth, rnd = K.rnd, DEG = K.DEG, SIDE = K.SIDE;
  const C = K.COPY;
  const $ = (id) => document.getElementById(id);
  const statsEl = $("stats"), riceEl = $("riceN"), cookEl = $("cookN"), beatEl = $("beat"), needleEl = $("needle"), zoneEl = $("zone"), bandEl = $("band");
  const app = $("app"), introEl = $("intro"), hudEl = $("hud"), timerEl = $("timer"), wordEl = $("word"), subEl = $("sub"), gaugeEl = $("gauge"), gaugeFill = $("gaugeFill"), comboEl = $("combo"), resEl = $("result"), tipEl = $("tip");
  const store = { get: (k, d) => { try { const v = localStorage.getItem(k); return v == null ? d : v; } catch (e) { return d; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch (e) {} } };

  const RICE_T = 12, MAXT = 50 * DEG;     // the wok follows the phone up to 50°: the sticky rice flows past ~30° and goes over the rim near the end
  const FIRE_MAX = 25;

  /* ---------- state ---------- */
  const vs = (() => { const m = /[?&]vs=(\d{1,3})/.exec(location.search); return m ? Math.min(100, +m[1]) : null; })(); // a friend's score from a shared link
  const inApp = /KAKAOTALK|Instagram|Barcelona|FBAN|FBAV|Line\//i.test(navigator.userAgent); // in-app browsers rarely pass motion: teach touch first
  // cue prototypes (operator 10-24: eyes stay on the bar, not the wok): ?cue=a approach ring in the wok, ?cue=c the fire is the cue
  const cueMode = (/[?&]cue=([ac])/.exec(location.search) || [])[1] || "";
  let ringM = null;
  function approachRing() {
    if (!ringM) { ringM = new T.Mesh(new T.RingGeometry(0.93, 1, 96), new T.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0, depthTest: false, side: T.DoubleSide, toneMapped: false })); ringM.rotation.x = -Math.PI / 2; ringM.renderOrder = 5; }
    if (ringM.parent !== W.wok) W.wok.add(ringM);
    const on = g.ph === "rice" && g.started && g.clock > 0 && g.darkPh !== "dark";
    if (!on) { ringM.material.opacity = 0; return; }
    const w = readyWin(), b = g.t - g.lastFlick, R0 = 1.0, R1 = 0.64; // it closes from the rim onto the pile edge, exactly as the band opens
    const k = clamp(b / w.gold0, 0, 1), r = b < w.gold0 ? R0 + (R1 - R0) * k * k : R1;
    ringM.scale.set(r, r, r); ringM.position.y = K.surf(r * r) + 0.1;
    const inB = b >= w.gold0 && b <= w.gold1, late = b > w.gold1;
    ringM.material.color.set(inB ? 0xffc23a : late ? 0xff4d2a : 0xffffff);
    ringM.material.opacity = late ? Math.max(0, 0.7 - (b - w.gold1) * 2) : inB ? 0.95 : 0.25 + 0.45 * k;
  }
  const todayOrder = () => { const d = new Date(); return (d.getFullYear() * 372 + d.getMonth() * 31 + d.getDate()) % 2 ? "half" : "full"; };
  let g = null;
  function newGame(auto) {
    return { ph: "intro", t: 0, phT: 0, auto, clock: RICE_T, started: false, lastFlick: -9, toss: null, tossN: 0,
      fire: 0, combo: 0, maxCombo: 0, grades: [], counts: { perfect: 0, good: 0, early: 0, late: 0 }, perfects: 0, maxGap: 0, scorchSaid: -9,
      order: todayOrder(), plateK: 0, pats: 0, doneT: 0, poured: 0, res: null,
      pose: { tilt: { u: 0, v: 0 } }, hei: 0, shim: -9, cam: 0, pan: 0, hit: 0 };
  }
  g = newGame(false);

  /* ---------- words ---------- */
  function word(text, kind, sub) {
    if (g && g.darkPh === "dark" && kind !== "dark") return; // in the dark only the 'it landed' word speaks
    if (g && kind !== "great" && kind !== "dark" && g.t < (g.wordLock || 0)) return; // a big moment is not overwritten
    if (g && kind === "great") g.wordLock = g.t + 0.8;
    wordEl.textContent = text; wordEl.className = "word " + (kind || ""); void wordEl.offsetWidth; wordEl.classList.add("pop");
    subEl.textContent = sub || ""; subEl.className = "sub"; void subEl.offsetWidth; if (sub) subEl.classList.add("pop");
  }
  const cueEl = $("startCue");
  function tip(text) { tipEl.textContent = text || ""; tipEl.hidden = !text; }
  function cue(big, small, cls) { cueEl.className = cls || ""; $("scBig").textContent = big; $("scSmall").textContent = small || ""; cueEl.hidden = false; }
  function hideCue() { if (cueEl.hidden) return; cueEl.classList.add("out"); setTimeout(() => { cueEl.hidden = true; cueEl.classList.remove("out"); }, 160); }

  /* ---------- wok pose: phone tilt + the chef's toss ---------- */
  const DAMP = (k, dt) => 1 - Math.exp(-dt / k);
  const LEVEL = { u: 0, v: 0 };
  function updatePose(dt) {
    const tl = g.ph === "intro" || g.introOn ? LEVEL : K.Input.tilt, P = g.pose, pose = W.pose; // the start screen and the ingredient drop keep the wok level

    // the wok follows the phone fast (sensitive); the food then flows with real gravity in food.js
    const tk = g.goT && g.t - g.goT < 0.6 ? 0.2 : 0.03; P.tilt.u += (tl.u - P.tilt.u) * DAMP(tk, dt); P.tilt.v += (tl.v - P.tilt.v) * DAMP(tk, dt); // eases from level into the hand after 볶아!
    let pitch = -4 * DEG - P.tilt.v * MAXT, roll = -P.tilt.u * MAXT, x = P.tilt.u * 0.05, z = 0, y = 0;
    pitch += Math.sin(g.t * 1.3) * 0.8 * DEG; roll += Math.sin(g.t * 0.9) * 1.0 * DEG; // the hand never quite stops
    if (g.eggDip) { const k = (g.t - g.eggDip) / 0.16; if (k < 1) y -= 0.05 * Math.sin(Math.PI * k); }
    if (g.ph === "egg" && K.Egg.ph === "air" && !K.Egg.flip.hop) { x += clamp(P.tilt.u * 0.9, -0.5, 0.5); z += clamp(-P.tilt.v * 0.65, -0.35, 0.35); pitch *= 0.4; roll *= 0.4; } // slide the wok under the egg
    const ks = g.t - g.shim; if (ks >= 0 && ks < 0.6) roll += 5 * DEG * Math.sin(ks * 34) * Math.exp(-ks * 6);
    // the toss (motion review): push 0–90 ms, lip snap 90–140 ms, back with a small spring overshoot
    const a = g.toss;
    if (a) {
      const k = g.t - a.t0, s = a.s;
      if (k > 1.0) g.toss = null;
      else {
        const fwd = 0.4 * s * (smooth(k / 0.09) - smooth((k - 0.14) / 0.18)) - 0.04 * s * Math.exp(-(((k - 0.42) / 0.08) ** 2));
        const bump = smooth((k - 0.08) / 0.06) - smooth((k - 0.16) / 0.14);
        x += a.du * fwd; z += -a.dv * fwd; y += 0.06 * bump;
        pitch += (4 + 12 * s) * DEG * bump * a.dv; roll -= (4 + 12 * s) * DEG * bump * a.du;
      }
    }
    // plating / take-out: roll toward the plate on the left, lifted and shifted over it
    if (g.ph === "plate" || g.ph === "take") { roll += g.plateK * MAXT; y += 0.12 * g.plateK; x += SIDE * 0.32 * g.plateK; pitch = -4 * DEG; }
    pose.x = x; pose.y = y; pose.z = z; pose.pitch = pitch; pose.roll = roll;
    W.applyPose();
  }

  /* ---------- input ---------- */
  function onFlick(f) { if (g.auto || g.ph === "result") return; flick(f); }
  function flick(f) { if (g.ph === "rice") riceFlick(f); else if (g.ph === "egg") eggFlick(f); }
  function riceFlick(f) {
    if (!g.started || g.t - g.lastFlick < 0.18 || g.clock <= 0 || g.finaleDone) return; // the ingredients are still going in
    K.Audio.unlock();
    const s = clamp(f.strength || 1, 0.3, 1.8), F = K.Food;
    if (g.tossN === 0) { tip(g.auto ? C.watching : C.rimTip); hideCue(); }
    { // the first toss is graded too: the needle has been running since "볶아!"
      if (!g.auto && (g.tossN >= 3 || g.perfects > 0)) tip("");
      // operator 10-24: toss back to back. The grade is the beat: the gap since the previous toss (shown on the beat bar)
      const back = g.t - g.lastFlick;
      g.maxGap = Math.max(g.maxGap, back);
      const w = readyWin(), grade = (g.lastGrade = back < w.open ? "early" : back < w.gold0 ? "good" : back <= w.gold1 ? "perfect" : back <= w.late ? "good" : "late");
      g.counts[grade]++;
      score(grade, s, 1 - Math.abs(back - w.peak) / (w.gold1 - w.peak));
    }
    if (g.darkPh === "warn" && g.clock <= 5.2 && !g.cutAt) g.cutAt = g.t + 0.12;
    if (g.darkPh === "dark") { g.darkN = (g.darkN || 0) + 1; if (g.clock <= 1.2) g.onAt = Math.min(g.onAt || 1e9, g.t + 0.2); }
    g.lastFlick = g.t; g.lastS = s; g.puffed = false; g.cFlare = false; g.win = bandFor(s); g.ticked = false; g.landSnd = false; g.landShk = false;
    // every toss hits: the camera kicks, the burner flares; a hard snappy one throws a fire column (old 2D kick/oilCatch)
    g.shake = Math.max(g.shake || 0, 0.5 + 0.5 * s); g.hei = Math.max(g.hei, (cueMode === "c" ? 0.12 : 0.3) + 0.25 * s * (cueMode === "c" ? 0.4 : 1)); /* C: the toss itself flares less, so the band's flare stands out */ g.punch = Math.max(g.punch || 0, 0.5);
    if (s >= 1.15 && (f.snap == null || f.snap > 0.4)) { g.hei = Math.max(g.hei, 0.9); W.sparkBurst(8); }
    const du = f.dirU || 0, dv = f.dirV == null ? 1 : f.dirV;
    const du2 = clamp(du, -0.85, 0.85); g.lastDu = du2; g.tossSpill = 0;
    g.toss = { t0: g.t, s: clamp(s, 0.5, 1.4), du: du2, dv: Math.sqrt(1 - du2 * du2) };
    // the finale: the first toss in the last 1.5 s goes 1.5x high, the camera looks up after it
    const fin = !g.auto && !g.finaleDone && (g.darkPh === "back" || g.darkPh === "over") && g.clock <= 1.3; if (fin) finale(s);
    F.launch({ strength: s, dirU: du, dirV: dv, lift: fin ? 1.5 : 1 }, g.t);
    g.tossN++;
    K.Audio.toss(s, f.snap == null ? 0.5 : f.snap);
  }
  function finale(s) {
    const gold = g.lastGrade === "perfect";
    g.finaleDone = true; g.finaleT = g.t; hideCue();
    g.hei = 1.6; W.sparkBurst(40); g.punch = 1; g.shake = gold ? 0.5 : 1.6; K.Audio.fever();
    const bonus = 3 * clamp((s - 0.8) / 0.8, 0, 1) * (gold ? 1 : 0.4); g.fire = Math.min(FIRE_MAX, g.fire + bonus); fireNum();
    if (gold) { g.slowT = g.t; g.slowUsed = true; hudEl.classList.add("slow"); K.Audio.slow(true); }
    g.finaleWord = { at: g.t + (gold ? 0.72 : 0.35), ok: gold }; // one success rule: the gold band
  }
  /* one toss's worth: timing × height; perfect tosses build a combo, the 불맛 meter fills */
  /* the ready window moves with the pile: a higher toss cools the rice, so it needs a little longer back on the metal */
  // fixed beat so the hand can learn it (no jitter); 불쇼 (fever) widens the sweet spot
  // the gold band opens just before the rice lands (L, from the toss strength and today's gravity) and narrows as
  // the combo grows; it is decided at each toss and stays put while the needle runs (panel p9: physics + ux + game)
  function bandFor(s) {
    const L = (0.62 + 0.13 * clamp(s, 0.3, 1.8)) * Math.sqrt(38 / K.Food.G()), fv = g.t < (g.fever || 0);
    // width (p9-fair Monte Carlo: casual ~54 % perfect, skilled ~84 %, all-perfect ~7 %): narrows over the lit 7 s and with the combo
    const tt = clamp((g.t - (g.goT || g.t)) / 7, 0, 1), base = (0.45 - 0.1 * tt) * (1 - 0.089 * Math.min(g.combo, 5));
    let w = base; void fv; // 불쇼 pays in 불맛, it no longer widens the band
    if (g.darkPh) w = g.winW || base; else g.winW = base; // dark and finale: the last lit band, frozen
    const gold0 = L - 0.08, gold1 = gold0 + w;
    return { open: L - 0.3, gold0, peak: L + 0.05, gold1, late: gold1 + 0.5 };
  }
  function readyWin() { const w = g.win || bandFor(1), first = g.darkPh === "dark" && !g.darkN; return first ? Object.assign({}, w, { gold1: w.gold1 + 0.2 }) : w; } // the first toss in the dark may hesitate
  function score(grade, s, q) {
    const hm = 0.65 + 0.35 * clamp((s - 0.55) / 0.75, 0, 1); // higher and flashier pays more (operator)
    g.grades.push(grade);
    if (grade === "perfect") {
      g.combo++; g.perfects++; g.maxCombo = Math.max(g.maxCombo, g.combo);
      const add = (g.darkPh === "dark" ? 1.25 : 1) * 1.2 * hm * (0.65 + 0.35 * clamp(q, 0, 1)) * (1 + Math.min(0.3, 0.06 * (g.combo - 1))) * (g.t < (g.fever || 0) ? 1.3 : 1); // dead centre pays most
      g.fire = Math.min(FIRE_MAX, g.fire + add); popNum("+" + add.toFixed(1), "fire");
      if (g.combo === 5) { g.fever = g.t + 4; word(C.fever, "great", C.feverSub); K.Audio.fever(); } else K.Audio.perfect(g.combo);
      g.flareAt = g.t + 0.2; g.flareN = g.combo; ember();
      try { if (navigator.vibrate) navigator.vibrate(C.combo.some((c) => c[0] === g.combo) ? [20, 40, 20] : 30); } catch (e) {}
      const title = C.combo.slice().reverse().find((c) => g.combo >= c[0]);
      if (g.darkPh === "dark") { g.darkPerf = (g.darkPerf || 0) + 1; word(C.blackPerfect, "dark"); W.sparkBurst(8); try { navigator.vibrate && navigator.vibrate(15); } catch (e) {} }
      else if (g.combo !== 5 && (g.perfects === 1 || C.combo.some((c) => c[0] === g.combo))) word(C.perfect, "great", title && g.combo >= 2 ? title[1] : "");
      if (title && g.combo >= (g.bestTitleN || 0)) { g.bestTitleN = g.combo; g.bestTitle = title[1]; }
      comboEl.textContent = g.combo >= 2 ? `${g.combo} 연속` : ""; comboEl.className = "combo"; void comboEl.offsetWidth; if (g.combo >= 2) comboEl.classList.add("pop");
    } else {
      if (grade === "good") { // so-so keeps the combo, fills a little
        // good keeps the combo (only perfect builds it) and stays quiet: a word on every toss buried the real moments
        g.fire = Math.min(FIRE_MAX, g.fire + 0.2 * hm); g.hei = Math.max(g.hei, 0.3); if (g.tossN < 4) word(C.good, "ok", C.goodChip);
        comboEl.textContent = g.combo >= 2 ? `${g.combo} 연속` : ""; fireNum(); return;
      }
      else if (grade === "early") { if (g.combo >= 2) { K.Audio.comboBreak(); popNum(C.mashed, "fire"); } g.combo = 0; comboEl.textContent = ""; fireNum(); return; } // mashing breaks the run
      else word(C.late, "bad", pick(C.lateSub));
      if (g.combo >= 2) K.Audio.comboBreak();
      if (g.combo >= 3 && g.darkPh !== "dark") { subEl.textContent = C.comboLost; subEl.className = "sub"; void subEl.offsetWidth; subEl.classList.add("pop"); }
      g.combo = 0; comboEl.textContent = ""; comboEl.className = "combo";
    }
    fireNum();
  }
  function fireNum() {
    gaugeFill.style.transform = `scaleY(${(g.fire / FIRE_MAX).toFixed(3)})`; gaugeEl.classList.toggle("hot", g.combo >= 3);
    gaugeEl.dataset.label = `불맛 ${Math.round(g.fire)}`; K.Food.fireTint = (g.fire / FIRE_MAX) * 0.4; // the rice visibly darkens toward caramel as 불맛 builds
  }
  const pick = (x) => (Array.isArray(x) ? x[(Math.random() * x.length) | 0] : x);
  // a number that jumps out of the HUD: +불맛 on a perfect, -밥 on a spill
  function popNum(text, kind) {
    const e = document.createElement("i"); e.className = "num " + kind; e.textContent = text; app.appendChild(e); setTimeout(() => e.remove(), 900);
  }
  // the lens gets hit: rice grains or yolk stick to the screen for a moment (old 2D: 계란 화면 강타)
  function splat(kind) {
    const n = kind === "egg" ? 3 : 9;
    for (let i = 0; i < n; i++) {
      const e = document.createElement("i"); e.className = "splat " + kind; app.appendChild(e);
      e.style.left = (kind === "egg" ? (i % 2 ? rnd(66, 90) : rnd(8, 32)) : rnd(12, 88)) + "%"; e.style.top = (kind === "egg" ? rnd(6, 22) : rnd(20, 70)) + "%";
      if (kind === "egg") { const sz = rnd(10, 16); e.style.width = sz + "vw"; e.style.height = sz * rnd(0.85, 1.1) + "vw"; e.style.animationDelay = i * 0.04 + "s"; }
      e.style.setProperty("--r", rnd(-80, 80) + "deg"); setTimeout(() => e.remove(), kind === "egg" ? 1600 : 900);
    }
  }
  /* an ember flies from the flame into the 불맛 gauge */
  function ember() {
    const e = document.createElement("i"); e.className = "ember"; app.appendChild(e);
    const r = gaugeEl.getBoundingClientRect(), a = app.getBoundingClientRect();
    e.style.left = a.width * 0.5 + "px"; e.style.top = a.height * 0.3 + "px";
    requestAnimationFrame(() => requestAnimationFrame(() => { e.style.left = r.left - a.left + r.width / 2 + "px"; e.style.top = r.top - a.top + r.height * (1 - g.fire / FIRE_MAX) + "px"; e.style.opacity = "0.2"; }));
    setTimeout(() => { e.remove(); gaugeEl.classList.remove("pulse"); void gaugeEl.offsetWidth; gaugeEl.classList.add("pulse"); }, 400);
  }
  function eggFlick(f) {
    const E = K.Egg;
    if (E.ph === "landed") { // the second flip: back to sunny side up
      if (!g.flip2Open || g.flip2Done) return;
      g.flip2Done = true; g.res1 = E.res; g.egg2Auto = !!f.auto2; tip("");
      E.flipStart({ strength: Math.min(f.strength || 1, 1.6), dirU: 0, second: true }, g.t); K.Audio.toss(f.strength || 1, f.snap || 0.5);
      return;
    }
    if (E.ph !== "pan" || g.t - E.t0 < 0.7) return;
    K.Audio.unlock();
    g.toss = { t0: g.t, s: clamp(f.strength || 1, 0.5, 1.2) * 0.7, du: 0, dv: 1 };
    E.flipStart(f, g.t); K.Audio.toss(f.strength || 1, f.snap || 0.5); if (!g.auto) tip(K.Input.mode === "motion" ? C.eggCatch : C.eggCatchTouch);
  }

  /* ---------- phases ---------- */
  function setPh(ph) { g.ph = ph; g.phT = 0; }
  function start(auto) {
    K.Food.setG(38);
    W.setDark(0); hudEl.classList.remove("dark", "slow", "final"); K.Audio.darkFlag(false); K.Audio.slow(false);
    homeEl.hidden = false;
    K.Input.steerAfterFlick = false;
    W.renderer.toneMappingExposure = W.S.exposure;
    const carry = g.hei; g = newGame(auto); g.hei = carry; // the tap's fire column carries over and hides the swap
    if (K.Egg.ph !== "off") K.Egg.hide();
    K.Food.reset(); K.Food.hold(); K.Food.fireTint = 0; K.Food.scorchVis = 0; W.plate.visible = false; W.ladle.visible = false; if (garn) garn.visible = false;
    introEl.hidden = true; resEl.hidden = true; resEl.classList.remove("up"); hudEl.hidden = false; hudEl.classList.remove("dim");
    timerEl.hidden = false; gaugeEl.hidden = false; statsEl.hidden = false; gaugeEl.classList.remove("egg"); gaugeEl.dataset.label = "불맛 0"; g.riceLeft = null; riceEl.hidden = true; cookEl.hidden = true;
    gaugeFill.style.transform = "scaleY(0)"; comboEl.textContent = ""; wordEl.textContent = ""; subEl.textContent = "";
    timerEl.textContent = RICE_T.toFixed(1); timerEl.classList.remove("warn");
    setPh("rice");
    tip(auto ? C.watching : "");
    g.introOn = true; g.introI = 0; g.skipOK = played && !auto; played = true;
  }
  let played = false;
  /* the intro (operator 10-24: not a wait for the first toss): oil, rice, kimchi go in, "볶아!", then the clock */
  function introStep() {
    const k = g.phT, F = K.Food, I = C.intro;
    if (g.introSkip) { g.introSkip = false; F.reset(); g.introI = I.length; g.phT = 2.4; g.flatGo = true; }
    // hold the phone flat first (operator 10-24): a live bubble level; the drop starts once it is flat for 0.3 s (3 s at most)
    const lvlOn = K.Input.mode === "motion" && !g.auto;
    if (lvlOn) {
      const t = K.Input.tilt, d = Math.hypot(t.u, t.v), ok = d < 0.12, sc = d > 1 ? 1 / d : 1;
      lvlDot.style.transform = `translate(${-t.u * sc * 31}px,${t.v * sc * 31}px)`; lvlDot.parentNode.classList.toggle("ok", ok); // the bubble floats to the high side
      if (!g.flatGo) {
        g.flatT = ok ? (g.flatT || 0) + 1 / 60 : 0; g.waitT = (g.waitT || 0) + 1 / 60;
        if (g.flatT >= 0.3 || g.waitT >= 3) g.flatGo = true;
        else { g.phT = 0; if (!g.flatCued) { g.flatCued = true; cue(C.flatBig, C.flatSmall, "intro level"); } return; }
      }
    }
    while (g.introI < I.length && k >= I[g.introI][0]) {
      const i = g.introI++;
      cue(I[i][1], i < 3 ? (lvlOn ? C.flatOk : C.introSmall) : "", lvlOn && i < 3 ? "intro level" : "intro");
      if (i === 0) K.Audio.pops(); else if (i === 1) F.drop(1); else if (i === 2) F.drop(0); // chef: kimchi first, then the cold rice
    }
    if (k >= 1.75 && !g.introThud) { g.introThud = true; K.Audio.land("rice"); }
    if (k >= 2.4) { g.introOn = false; g.started = true; g.goT = g.t; g.lastFlick = g.t; g.win = bandFor(1); g.clockAt = g.t + 0.5; hideCue(); } // the needle starts at 볶아! // 0.5 s grace before the 12 s run
  }
  // no toss yet: the hint comes only now (1.5 s), and gets pushier at 4 s
  function nudge() {
    const w = g.t - g.goT, touch = K.Input.mode !== "motion";
    if (w > 1.5 && !g.nudged) { g.nudged = 1; cue(touch ? C.cueTouch : C.cueMotion, C.cueSmall, touch ? "touch" : ""); }
    if (w > 4 && g.nudged === 1) { g.nudged = 2; $("scBig").textContent = C.cueProd; cueEl.classList.add("fast"); }
  }
  function startPlate() {
    comboEl.textContent = ""; wordEl.textContent = ""; subEl.textContent = "";
    hudEl.classList.remove("final", "slow", "dark"); // the egg needs its meter back (it stayed hidden after the finale)
    setPh("plate"); hudEl.classList.add("dim"); tip(""); K.Food.scorchVis = 0;
    W.plate.visible = true; W.plate.position.set(SIDE * 3.2, W.plateC.y, W.plateC.z); W.plate.scale.set(1, 1, 1);
  }
  const eggWin = () => (g.order === "half" ? [0.5, 0.82] : [0.82, 1.15]);
  const eggWin2 = () => (g.order === "half" ? [0.2, 0.4] : [0.5, 0.8]); // 반숙: the yolk side sets fast, a short side 2
  function setBand(w, max) { bandEl.style.bottom = (w[0] / max) * 100 + "%"; bandEl.style.height = ((w[1] - w[0]) / max) * 100 + "%"; }
  function startEgg() {
    setPh("egg"); K.Input.steerAfterFlick = true; hudEl.classList.remove("dim"); statsEl.hidden = true; beatEl.hidden = true; comboEl.textContent = ""; g.flips = 0;
    timerEl.classList.remove("warn"); timerEl.textContent = C.flipCount(0);
    gaugeEl.classList.add("egg"); gaugeEl.dataset.label = C.eggSide1; setBand(eggWin(), 1.6); g.tickSide = 0;
    K.Egg.start(g.t); K.Food.settle();
    tip(g.auto ? C.watching : C.eggTip(g.order, K.Input.mode !== "motion"));
  }

  /* ---------- the plate ---------- */
  let garn = null;
  function plateStep(dt) {
    const k = g.phT, pc = W.plateC, F = K.Food;
    const inK = 1 - (1 - clamp(k / 0.3, 0, 1)) ** 5;
    let px = SIDE * 3.2 + (pc.x - SIDE * 3.2) * inK;
    if (g.doneT) px = pc.x + SIDE * 0.6 * smooth((g.t - g.doneT - 0.7) / 0.35);
    W.plate.position.x = px;
    if (g.ph === "plate") {
      if (!g.poured) {
        let left = 0; for (let i = 0; i < F.n; i++) if (F.st[i] <= 2) left++;
        const want = k < 0.4 ? 0 : clamp((k - 0.4) / 0.35, 0, 1);
        g.plateK += (want - g.plateK) * DAMP(0.06, dt);
        if (k > 0.8 && (left < F.n * 0.03 || k > 3.4)) { g.poured = g.t; F.sweep({ x: W.plate.position.x, y: W.plateC.y + 0.01, z: W.plateC.z }); } // the ladle scrapes the rest out
      } else {
        const q = g.t - g.poured;
        if (q < 0.2 && g.shim < g.poured) g.shim = g.t; // two quick shakes for the last grains
        g.plateK += (0 - g.plateK) * DAMP(0.08, dt);
        // two ladle pats round the heap off
        const top = pc.y + 0.04 + F.plateMound(0);
        const tap = q < 0.25 ? 0 : q < 0.37 ? Math.sin(((q - 0.25) / 0.12) * Math.PI) : q < 0.49 ? Math.sin(((q - 0.37) / 0.12) * Math.PI) : 0;
        W.ladle.visible = q > 0.2 && q < 0.6; W.ladle.position.set(px - 0.02 * SIDE, top + 0.2 - 0.17 * tap, pc.z); W.ladle.rotation.set(0, 0, -0.15 * SIDE);
        if ((q > 0.31 && g.pats === 0) || (q > 0.43 && g.pats === 1)) { g.pats++; g.plateSq = 1; K.Audio.land("egg-soft"); }
        if (q > 0.6 && !g.doneT) { g.doneT = g.t; K.Audio.stamp(); word(C.plated, "ok"); }
        if (g.doneT && g.t - g.doneT > 1.3) startEgg();
      }
    }
    g.plateSq = Math.max(0, (g.plateSq || 0) - dt * 7);
    W.plate.scale.set(1 + 0.05 * g.plateSq, 1 - 0.08 * g.plateSq, 1 + 0.05 * g.plateSq);
  }

  /* ---------- garnish: 김가루 + 깨 ---------- */
  function startGarnish() {
    setPh("garnish"); tip("");
    if (!garn || garn.parent !== W.scene) {
      garn = new T.Group(); W.scene.add(garn);
      const kim = new T.InstancedMesh(new T.PlaneGeometry(0.022, 0.014), new T.MeshStandardMaterial({ color: K.lin("#1c2a1c"), roughness: 0.35, side: T.DoubleSide }), 110);
      const ses = new T.InstancedMesh(new T.SphereGeometry(1, 8, 6), new T.MeshStandardMaterial({ color: K.lin("#f0e2bd"), roughness: 0.5 }), 80);
      garn.add(kim, ses); garn.userData = { kim, ses, pts: [] };
    }
    garn.visible = true; garn.userData.pts = [];
    for (let i = 0; i < 190; i++) { const a = Math.random() * K.TAU, r = Math.sqrt(Math.random()) * 0.42; garn.userData.pts.push({ x: Math.cos(a) * r, z: Math.sin(a) * r, d: rnd(0, 0.5), rx: rnd(0, K.TAU), ry: rnd(0, K.TAU) }); }
  }
  const dummy = new T.Object3D();
  function garnishStep() {
    const { kim, ses, pts } = garn.userData, k = g.phT, pc = W.plateC, egg = K.Egg.onPlate;
    pts.forEach((q, i) => {
      const f = clamp((k - q.d) / 0.45, 0, 1), d = Math.hypot(q.x, q.z);
      const ed = Math.hypot(q.x - (K.Egg.plateOff || 0), q.z), onEgg = egg && ed < 0.24;
      const land = pc.y + 0.035 + K.Food.plateMound(d) * 0.95 + (onEgg ? 0.035 + 0.05 * Math.max(0, 1 - (ed / 0.24) ** 2) : 0);
      dummy.position.set(W.plate.position.x + q.x, land + 0.9 * (1 - f) ** 2, pc.z + q.z);
      if (i < 110) { dummy.scale.set(1, 1, 1); dummy.rotation.set(-Math.PI / 2 + 0.3 * Math.sin(q.rx) + (1 - f) * 6, q.ry, 0); dummy.updateMatrix(); kim.setMatrixAt(i, dummy.matrix); }
      else { dummy.scale.set(0.005, 0.003, 0.008); dummy.rotation.set(q.rx, q.ry, 0); dummy.updateMatrix(); ses.setMatrixAt(i - 110, dummy.matrix); }
    });
    kim.instanceMatrix.needsUpdate = ses.instanceMatrix.needsUpdate = true;
  }

  const wispCol = new T.Color(), WC = { white: K.lin("#f2f4f7"), grey: K.lin("#9a948c"), dark: K.lin("#6a625a") };
  /* ---------- the step (fixed 1/60) ---------- */
  function step(dt) {
    g.t += dt; g.phT += dt;
    if (g.auto) autoPlay();
    if (g.ph === "intro") attract();
    updatePose(dt);
    const F = K.Food, E = K.Egg;
    if (g.ph === "rice" && g.introOn) introStep();
    if (g.ph === "rice" && g.started && !g.darkPh) K.Food.setG(38 + 12 * clamp((g.t - g.goT) / 7, 0, 1));
    if (g.ph === "rice" && g.started) blackout();
    else if (g.darkPh && g.darkPh !== "over") lightsOn();
    darkOv.style.opacity = (W.darkK * (1 - 0.4 * clamp(g.hei - 0.35, 0, 1))).toFixed(3); // a perfect's fire lifts the dark for a moment
    if (g.finaleWord && g.t >= g.finaleWord.at) { const w = g.finaleWord; g.finaleWord = null; word(w.ok ? C.finale : C.finaleWeak, w.ok ? "great" : "meh", w.ok ? C.finaleSub : ""); }
    if (g.ph === "rice" && g.started && !g.auto && g.tossN === 0) nudge();
    if (g.ph === "rice" && !g.auto && !g.finaleDone && !g.finaleCue && g.clock > 0 && g.clock <= 1.0 && g.darkPh !== "dark") { g.finaleCue = true; cue(C.finaleCue, C.finaleCueSub, "intro"); }
    if (g.finaleCue && !g.finaleDone && g.clock <= 0) { g.finaleDone = true; hideCue(); }
    if (g.ph === "rice" && g.started && g.t >= g.clockAt) { g.clock = Math.max(0, g.clock - dt); if (g.clock <= 0 && g.t - g.lastFlick > 0.9 && F.air === 0 && !g.slowT && (!g.finaleT || g.t - g.finaleT > 0.95)) startPlate(); }
    const plateOn = ["plate", "egg", "take", "garnish", "result"].includes(g.ph);
    W.plate.visible = plateOn;
    const ctx = { t: g.t, dt, pour: g.ph === "plate" && !g.poured && g.phT > 0.6 ? SIDE * clamp((g.phT - 0.6) / 0.5, 0, 1) : 0, heat: g.ph === "rice" ? (g.t >= g.clockAt ? 1 : 0) : g.ph === "plate" ? 0.2 : 0,
      plate: plateOn ? { x: W.plate.position.x, y: W.plateC.y + 0.01, z: W.plateC.z, on: true, pats: g.pats } : null };
    const r = g.ph === "egg" && g.phT > 0.6 || g.ph === "take" || g.ph === "garnish" || g.ph === "result" ? { landed: 0, spilledNow: 0 } : F.step(ctx); // plated: nothing moves
    if (r.landed > 30 && g.ph === "rice" && !g.landSnd) { g.landSnd = true; K.Audio.land("rice"); }
    if (g.ph === "rice" && r.spilledNow) { g.tossSpill = (g.tossSpill || 0) + r.spilledNow; if (g.tossSpill > 120 && Math.abs(g.lastDu || 0) > 0.5 && !g.sideSaid) { g.sideSaid = g.t; g.spillSaid = g.t; word(C.sideTable, "bad", C.sideTableSub); } }
    if (r.spilledNow > 4 && g.ph === "rice") { K.Audio.land("spill"); g.shake = Math.max(g.shake || 0, 1.4); if (g.t - (g.spillSaid || -9) > 1.5) { g.spillSaid = g.t; word(C.spill, "bad", pick(C.spillSub)); splat("rice"); try { navigator.vibrate && navigator.vibrate(60); } catch (e) {} } }
    if (r.landed > 30 && g.ph === "rice" && !g.landShk) { g.landShk = true; g.shake = Math.max(g.shake || 0, 0.6); g.eggDip = g.t; } // the wok gives under the weight
    if (g.ph === "rice" && g.started && (g.statT = (g.statT || 0) + dt) > 0.2) { g.statT = 0; liveStats(); }
    if (plateOn && g.ph !== "result" && g.ph !== "garnish") plateStep(dt);
    // the diegetic cue: oil smoke rises from the edge of the pile and the sizzle turns to dry pops when the metal under it
    // is hot again; dark smoke when the bottom starts to scorch
    let wisp = 0, wispBig = 1, wispThick = 0, siz = 0.4, steam = 0.12, smoke = 0, flare = 0;
    if (g.ph === "rice" && g.started) {
      const back = g.t - g.lastFlick;
      const w = readyWin(), big = g.tossN <= 2 ? 1 : 0; // the first two tosses show the cue bigger
      wispCol.copy(WC.white);
      if (back < w.open) { wisp = 0; siz = 0.4; }
      else if (back < w.gold0) { const k = (back - w.open) / (w.gold0 - w.open); wisp = 0.3 * k; siz = 0.4 + 0.3 * k; }
      else if (back <= w.gold1) { wisp = 0.65 + 0.2 * (1 - Math.abs(back - w.peak) / (w.gold1 - w.peak)); siz = 0.55; flare = (1 - clamp((back - w.gold0) / 0.25, 0, 1) * 0.6) * (cueMode === "c" ? 2.6 : 1); if (cueMode === "c" && !g.cFlare) { g.cFlare = true; g.hei = Math.max(g.hei, 0.95); W.sparkBurst(10); K.Audio.pops(); } /* C: the band opens with a fire column off the lip */ wispBig = 1 + 0.5 * big; if (!g.puffed) { g.puffed = true; W.wispBurst(); } }
      if (!g.ticked && back >= w.gold0 - 0.04) { g.ticked = true; if (g.darkPh !== "dark") K.Audio.tick(); } // the beat is heard: 탁 at the band (output latency allowed for)
      else if (back <= w.late) { const k = (back - w.gold1) / (w.late - w.gold1); wisp = 0.6; wispCol.lerp(WC.grey, k); wispThick = k; siz = 0.6; }
      else { wisp = 0.6 * (1 - clamp((back - w.late) / 1.2, 0, 0.6)); wispCol.copy(WC.grey).lerp(WC.dark, clamp((back - w.late) / 0.8, 0, 1)); wispThick = 1; siz = 0.55; }
      const b = F.bottom(); smoke = Math.max(clamp((b.scorch - 0.04) * 6, 0, 1), clamp((back - 2.0) / 1.4, 0, 1)); F.scorchVis = smoke; // left alone: smoke from 2 s, thick by 3.4 s
      steam = 0.18 + 0.2 * clamp(back, 0, 1);
      if (smoke > 0.4 && g.t - g.scorchSaid > 2.5) { g.scorchSaid = g.t; word(C.scorch, "bad"); }
    }
    if (g.ph === "egg") { steam = 0.1; smoke = clamp((E.cook - 1.15) / 0.3, 0, 1); } // a forgotten egg smokes
    if (g.flareAt && g.t >= g.flareAt) { g.flareAt = 0; g.hei = 1 + Math.min(0.6, 0.15 * (g.flareN - 1)); W.sparkBurst(10 + Math.min(20, g.flareN * 3)); K.Audio.flare(); g.punch = 1; }
    g.hei = Math.max(0, g.hei - dt * 1.2);
    W.fx(g.t, dt, { hei: g.hei, heat: g.ph === "result" || g.ph === "garnish" ? 0.6 : 1, lick: (g.toss ? 1 : 0) + (g.t < (g.fever || 0) ? 0.8 : 0), steam: g.ph === "result" ? 0 : steam, smoke, wisp, wispCol, wispBig, wispThick, wispR: 0.5, flare });
    if (g.darkPh === "dark") siz = 0.55; // flat in the dark: the sizzle must not tick the beat
    K.Audio.sizzle(g.ph === "rice" ? (g.started ? F.contact * siz : g.introI ? 0.6 : 0.2) : g.ph === "egg" ? (E.ph === "air" ? 0.1 : 0.55) : 0);
    K.Audio.burnWarn(smoke);
    if (g.ph === "egg") {
      if (E.ph === "pan" && E.cook > 1.4 && !g.eggBurnSaid) { g.eggBurnSaid = true; word(C.eggBurning, "bad"); }
      if (E.ph === "pan" && E.cook > 1.58) { g.eggAuto = !g.auto; eggFlick({ strength: 0.65, dirU: 0 }); } // left too long: it gets flipped for you, badly
      if (E.ph === "landed" && g.flip2Open && !g.flip2Done && E.cook2 > 1.18) eggFlick({ strength: 0.65, dirU: 0, auto2: !g.auto });
      if (E.ph === "landed" && g.flip2Open && !g.flip2Done && E.cook2 > 1.0 && !g.yolkSaid) { g.yolkSaid = true; word(C.eggSide2Late, "bad"); }
      // one meter per side: side 1 = E.cook (0..1.6), side 2 = E.cook2 (0..1.2); its band is when to flip
      const side2 = g.flip2Open && !g.flip2Done, win = side2 ? eggWin2() : eggWin(), c = side2 ? E.cook2 : E.cook, cmax = side2 ? 1.2 : 1.6;
      if (!g.flip2Done) gaugeFill.style.transform = `scaleY(${clamp(c / cmax, 0, 1).toFixed(3)})`;
      if (c >= win[0] && g.tickSide !== (side2 ? 2 : 1) && (side2 || E.ph === "pan")) { g.tickSide = side2 ? 2 : 1; K.Audio.tick(); setTimeout(() => K.Audio.tick(), 120); } // 타닥: flip now
      E.cue(g.t, (side2 || E.ph === "pan") ? (c < win[0] - 0.08 ? 0 : c <= win[1] ? clamp((c - win[0] + 0.08) / 0.12, 0, 1) : clamp(1 - (c - win[1]) / 0.15, 0, 1)) : 0);
      const ev = E.step({ t: g.t, dt, heat: 1 });
      if (ev === "plop") K.Audio.land("egg-soft");
      else if (ev) eggLanded(ev);
      if (E.ph === "landed" && (g.flip2Done || !g.flip2Open) && g.t - E.landT > 1.4) { setPh("take"); g.takeT = g.t; }
      else if (E.ph === "counter" && g.t - g.eggLandT > 1.6) startGarnish();
    } else if (g.ph === "take") {
      const k = (g.t - g.takeT) / 1.1;
      g.plateK = 0.55 * Math.sin(Math.PI * Math.min(1, k / 0.85));
      E.takeStep(g.t, k, new T.Vector3(W.plate.position.x, W.plateC.y + 0.04, W.plateC.z));
      if (k >= 1) startGarnish();
    } else if (g.ph === "garnish") {
      E.idle(g.t, dt); garnishStep();
      if (g.phT > 1.4) showResult();
    } else if (g.ph === "result") E.idle(g.t, dt);
  }
  function eggLanded(ev) {
    const E = K.Egg, r = E.res; g.eggLandT = g.t;
    if (ev === "hop") { word(C.eggHop, "meh"); K.Audio.land("egg-soft"); if (E.ph === "landed") { g.flip2Done = false; g.res1 = null; } return; } // too soft: try again
    if (ev === "counter") { K.Audio.land("egg-splat"); word(C.eggOut, "bad"); return; }
    if (g.res1) { // the second landing
      g.flip2Up = ev === "clean" && !E.flipped; K.Audio.land(ev === "burst" ? "egg-splat" : "egg-soft");
      if (ev === "burst" && E.res.s > 1.52) splat("egg");
      if (ev !== "burst") { g.flips = 2; timerEl.textContent = C.flipCount(2); }
      const w2 = eggWin2(), c2 = E.res.cookAtFlip, inBand = c2 >= w2[0] - 0.05 && c2 <= w2[1] + 0.05;
      if (g.flip2Up && inBand) { word(C.eggYolkUp, "great"); K.Audio.ding(); } else if (g.flip2Up) word(c2 < w2[0] ? C.egg2Early : C.egg2Late, "meh"); else word(ev === "burst" ? C.eggBurst : C.eggFold, ev === "burst" ? "bad" : "meh");
      return;
    }
    g.flips = 1; timerEl.textContent = C.flipCount(1);
    if (ev === "burst") { if (r.s > 1.52) splat("egg"); g.shake = Math.max(g.shake || 0, 0.8); try { navigator.vibrate && navigator.vibrate([25, 30, 50]); } catch (e) {} } // the lens gag only for a really hard flip
    // side 2 always follows (operator: two flips): the meter empties, its band moves to side 2's window
    g.flip2Open = true; setBand(eggWin2(), 1.2); gaugeEl.dataset.label = C.eggSide2; gaugeEl.classList.remove("pulse"); void gaugeEl.offsetWidth; gaugeEl.classList.add("pulse");
    if (!g.auto) setTimeout(() => g.ph === "egg" && !g.flip2Done && tip(C.flip2Tip), 500);
    g.hit = ev === "burst" ? 0.11 : ev === "clean" && r.errDeg <= 12 && r.where === "centre" ? 0.09 : 0.05; g.eggDip = g.t; g.punch = 0.6;
    K.Audio.land(ev === "burst" ? "egg-splat" : "egg-soft");
    if (ev === "burst") word(C.eggBurst, "bad", pick(r.s > 1.52 ? C.eggLensSub : C.eggBurstSub));
    else if (ev === "fold") word(C.eggFold, "meh");
    else if (r.where === "wall") word(`${r.angle}°`, "ok", C.eggWall);
    else word(`${r.angle}°!`, r.errDeg <= 12 ? "great" : "ok", r.errDeg <= 12 ? C.eggPerfect : C.eggGood);
    if (ev === "clean" && r.errDeg <= 12) K.Audio.perfect(4);
  }

  /* ---------- scoring: rice 60 = doneness 25 + 불맛 25 + 안 흘리기 10, egg 40 = timing 12 + flip 14 + catch 10 + yolk 4 ---------- */
  function finalScore() {
    const st = K.Food.stats(), r = g.res1 || K.Egg.res, r2 = g.res1 ? K.Egg.res : null;
    // mashing (< 0.35 s gaps) stirs badly: it counts half for doneness and cleanliness (60 mashes scored 80 before)
    const nT = g.counts.perfect + g.counts.good + g.counts.early + g.counts.late, mash = nT ? 1 - 0.5 * (g.counts.early / nT) : 1;
    const done = Math.max(0, Math.round(mash * 25 * clamp((st.good - 0.45) / 0.5, 0, 1) - 12 * clamp(st.burnt * 3, 0, 1)));
    const fire = Math.round(g.fire);
    const clean = Math.round(mash * clamp(g.tossN / 6, 0, 1) * Math.max(0, 10 - Math.round((st.spill * 100) / 0.8)));
    // egg 40 = timing 7 + 5, flip 7 + 7, catch 6 + 4 (side 1 + side 2), yolk 4
    const off = (c, w) => (c < w[0] ? w[0] - c : c > w[1] ? c - w[1] : 0), ang = (x) => (x.fold ? 2 : 7 * clamp(1 - Math.max(0, x.errDeg - 8) / 50, 0, 1));
    let timing = 0, flip = 0, cat = 0, yolk = 0;
    if (r) {
      timing = 7 * clamp(1 - off(r.cookAtFlip, eggWin()) / 0.2, 0, 1) + (r2 && !g.egg2Auto ? 5 * clamp(1 - off(r2.cookAtFlip, eggWin2()) / 0.15, 0, 1) : 0);
      flip = (g.eggAuto ? 0 : ang(r)) + (r2 && !g.egg2Auto ? ang(r2) : 0); // the flips the game made for you earn nothing
      cat = (g.eggAuto ? 0 : 6 * r.catchQ) + (r2 && !g.egg2Auto ? 4 * r2.catchQ : 0);
      yolk = r.burst || (r2 && r2.burst) || (g.eggAuto && g.egg2Auto) ? 0 : 4;
      timing = Math.round(timing); flip = Math.round(flip); cat = Math.round(cat);
    }
    return { done, fire, clean, timing, flip, cat, yolk, total: done + fire + clean + timing + flip + cat + yolk, st, r };
  }

  /* ---------- result ---------- */
  function showResult() {
    W.renderer.toneMappingExposure = 0.82; // the plate close-up blew out at 1.05
    setPh("result"); hudEl.hidden = true; tip("");
    const s = (g.res = finalScore());
    const best = +store.get("kfr-best3d", 0), rec = !g.auto && s.total > best && best > 0;
    if (!g.auto && s.total > best) store.set("kfr-best3d", s.total);
    const tier = C.tiers.find((x) => s.total >= x[0]), next = C.tiers.slice().reverse().find((x) => x[0] > s.total);
    const line = (a, b, c) => `<li><span>${a}</span><span>${b} / ${c}</span></li>`;
    resEl.innerHTML = `<div class="slip"><div class="shop">${C.shop}</div><div class="meta">${C.shopSub} · 김치볶음밥 1 · 계란 ${g.order === "half" ? "반숙" : "완숙"}${g.auto ? `<br>${C.watchedBy}` : ""}</div>
      ${rec ? `<div class="rec">신기록</div>` : ""}
      <ul>${line("밥 익힘", s.done, 25)}${line("불맛 (박자·높이)", s.fire, 25)}${line("안 흘리기", s.clean, 10)}${line("계란 타이밍", s.timing, 12)}${line("뒤집기", s.flip, 14)}${line("받기", s.cat, 10)}${line("노른자", s.yolk, 4)}</ul>
      ${g.darkN ? `<div class="floor">${C.blackLine(g.darkPerf || 0, g.darkN)} · ${blackMemo()}</div>` : ""}
      <div class="floor">바닥 밥 변상 <b>${K.Food.spilled}</b>알 × 10원 = <b>${(K.Food.spilled * 10).toLocaleString()}</b>원</div>
      <div class="tot"><span class="stamp">${tier[1]}</span><span class="score"><b id="scoreN">0</b><small>점</small></span></div>
      ${vs != null ? `<div class="vs">${s.total > vs ? C.vsWin(vs, s.total) : s.total === vs ? C.vsTie(vs) : C.vsLose(vs, s.total)}</div>` : ""}
      <div class="why">${whyLine(s)}</div>
      <div class="memo">사장님: ${memo(tier)}</div>
      ${next ? `<div class="near">「${next[1]}」까지 ${next[0] - s.total}점</div>` : ""}
      ${best > 0 && !rec && best > s.total ? `<div class="near">내 최고 ${best}점</div>` : ""}
      <div class="row"><button class="btn" id="again" type="button">${g.auto ? C.tryIt : C.again}</button>${g.auto ? "" : `<button class="btn ghost" id="share" type="button">${C.share}</button>`}</div>
      ${K.Input.hint ? `<div class="open">${C.openInBrowser}</div>` : ""}<div class="credit">${C.credit}</div></div>`;
    const gg = g; // a restart within 2 s must not let this round's slide-in / stamp fire into the next
    resEl.hidden = false; setTimeout(() => { if (g === gg) resEl.classList.add("up"); }, 900); // the finished dish gets a moment first
    const n = $("scoreN"), t0 = performance.now() + 1200;
    (function up() { if (g !== gg) return; if (performance.now() < t0) return requestAnimationFrame(up); const k = Math.min(1, (performance.now() - t0) / 900); n.textContent = Math.round(s.total * smooth(k)); if (k < 1) requestAnimationFrame(up); else { K.Audio.stamp(); const st = resEl.querySelector(".stamp"); if (st) st.classList.add("pop"); } })();
    $("again").onclick = () => { K.Audio.unlock(); const go = () => start(false); if (g.auto) K.Input.requestPermission().then(go, go); else go(); }; bestLine();
    if (!g.auto) $("share").onclick = share; // the bot's dish is not yours to brag about
  }
  const blackMemo = () => { const n = g.darkN || 0, k = Math.round((3 * (g.darkPerf || 0)) / Math.max(1, n)); return C.blackMemo.find((x) => k >= x[0])[1]; };
  const memo = (tier) => (K.Food.spilled > 300 ? C.memoSpill : tier[2]);
  function whyLine(s) {
    const st = s.st, parts = [];
    if (st.burnt > 0.08) parts.push(`탄 밥 ${Math.round(st.burnt * 100)}%`);
    if (st.raw > 0.15) parts.push(`덜 익은 밥 ${Math.round(st.raw * 100)}%`);
    if (st.spill > 0.01) parts.push(`흘린 밥 ${Math.round(st.spill * 100)}%`);
    if (g.maxGap > 2.2) parts.push(`최대 ${g.maxGap.toFixed(1)}초 안 볶음`);
    parts.push(`불맛 ${Math.round((g.fire / FIRE_MAX) * 100)}%`); if (g.maxCombo >= 2) parts.push(`최고 콤보 x${g.maxCombo}${g.bestTitle ? " " + g.bestTitle : ""}`);
    const c = g.counts, n = c.perfect + c.good + c.early + c.late;
    if (n) parts.push(`금색 칸에 휙 ${c.perfect}/${n}번` + (c.late > 1 ? " (쉬다가 눌린 게 많음)" : ""));
    const r = s.r, win = eggWin(), r2 = g.res1 ? K.Egg.res : null;
    if (r) {
      if (r.cookAtFlip < win[0] - 0.05) parts.push("앞면을 너무 일찍 뒤집음"); else if (r.cookAtFlip > win[1] + 0.05) parts.push("앞면을 너무 늦게 뒤집음");
      if (r2) { const w2 = eggWin2(); if (r2.cookAtFlip < w2[0] - 0.05) parts.push("뒷면을 너무 일찍 뒤집음"); else if (r2.cookAtFlip > w2[1] + 0.05) parts.push("뒷면을 너무 늦게 뒤집음"); }
      if (r.burst) parts.push(r.raw ? "덜 익어서 찢어짐" : C.eggBurstReceipt); else if (r.fold) parts.push("반으로 접힘"); else parts.push(`계란 ${r.angle}°`);
      if (r.where === "wall") parts.push("벽에서 받음"); else if (r.where === "out") parts.push("계란을 못 받음");
      if (g.flip2Open && !g.flip2Up) parts.push(g.egg2Auto ? "뒷면 방치" : "두 번째 뒤집기 실패");
    }
    return parts.join(" · ");
  }
  // the link always goes with the brag, so whoever sees it can tap straight in (operator 10-24, every work)
  const gameUrl = () => (/^https?:/.test(location.protocol) ? location.origin + location.pathname : C.url);
  const shareUrl = (n) => gameUrl() + "s/" + clamp(Math.round(n), 0, 100) + "/";
  function showShare(n, url) { // no share sheet: the preview card and the link to copy
    const ov = $("shareOv"); ov.querySelector("img").src = `s/img/s${clamp(Math.round(n), 0, 100)}-${C.cardVer}.jpg`; $("shareUrl").textContent = url; $("shareCopy").textContent = C.copyLink; ov.hidden = false;
  }
  $("shareCopy").onclick = (e) => { e.stopPropagation(); const u = $("shareUrl").textContent, b = $("shareCopy");
    const done = () => { b.textContent = C.copied; }; try { navigator.clipboard.writeText(u).then(done, () => selectUrl()); } catch (er) { selectUrl(); } };
  function selectUrl() { const r = document.createRange(); r.selectNodeContents($("shareUrl")); const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r); }
  $("shareUrl").onclick = (e) => { e.stopPropagation(); selectUrl(); };
  // operator 10-24: share a LINK, not a picture. Each score has its own static page (s/<n>/) whose preview is that
  // score's receipt; it sends the visitor into the game with ?vs=<n>. Built by make_score_pages.py.
  function share() {
    const s = g.res, tier = C.tiers.find((x) => s.total >= x[0]), url = shareUrl(s.total);
    const text = (vs != null ? (s.total > vs ? C.shareWin(vs, s.total) : C.shareLose(vs, s.total)) : C.shareText(s.total, tier[1])) + (g.darkN ? C.blackShare(g.darkPerf || 0, g.darkN) : "");
    if (navigator.share) navigator.share({ title: "김치볶음밥 한 그릇", text, url }).catch((e) => { if (!e || e.name !== "AbortError") showShare(s.total, url); });
    else showShare(s.total, url);
  }

  /* ---------- watch mode (구경하기): a decent cook plays itself ---------- */
  function autoPlay() {
    const F = K.Food, E = K.Egg;
    if (g.ph === "rice") {
      if (g.started && g.tossN === 0 && g.t - g.goT > 0.8) flick({ strength: 1.1, dirU: 0, dirV: 1 });
      else if (g.started && g.clock > 0 && g.tossN > 0) { // a decent cook: mostly on the beat, sometimes off
        if (!g.autoWait) g.autoWait = rnd(0.5, 1.25);
        if (g.t - g.lastFlick > g.autoWait) { g.autoWait = 0; const sd = g.tossN % 4 === 3 ? (Math.random() < 0.5 ? -0.6 : 0.6) : 0; flick({ strength: rnd(0.95, 1.35), dirU: sd, dirV: Math.sqrt(1 - sd * sd) }); }
      }
    } else if (g.ph === "egg") {
      const win = g.order === "half" ? 0.62 : 0.95;
      if (E.ph === "pan" && E.cook > win) eggFlick({ strength: 1.05, dirU: 0 });
      const w2 = eggWin2(); if (E.ph === "landed" && g.flip2Open && !g.flip2Done && E.cook2 > (w2[0] + w2[1]) / 2) eggFlick({ strength: 1.05, dirU: 0 });
      if (E.ph === "air") { const tg = E.target(); if (tg) K.Input.demoTilt(clamp(tg.x / 0.9, -1, 1), clamp(-tg.z / 0.65, -1, 1)); }
      else K.Input.demoTilt(0, 0);
    }
  }

  /* ---------- 정전 (operator 10-24): the lights go out for the last ~3 tosses, the gauge with them; only the burner,
     the 탁 and the fire on a perfect remain. They come back at 1.8 s left, into the finale. ---------- */
  const darkOv = $("darkOv"), lvlDot = $("lvlDot");
  const CUT = [[0, 0], [0.08, 1], [0.13, 0], [0.25, 0.4], [0.29, 0]]; // light level over the cut: it flickers, then dies
  function blackout() {
    const c = g.clock;
    if (!g.darkPh && c <= 5.6) { g.darkPh = "warn"; g.darkT = g.t; K.Audio.buzz(); cue(C.blackWarn, "", "intro"); }
    if (g.darkPh === "warn") {
      if (!g.cutAt && c <= 4.9) g.cutAt = g.t; // no toss came: cut anyway
      W.setDark(g.t - g.darkT < 0.06 ? 0.3 : 0); // one dip of the tube light
      if (g.cutAt && g.t >= g.cutAt) { wordEl.textContent = ""; subEl.textContent = ""; g.darkPh = "dark"; g.darkT = g.t; g.darkN = 0; g.darkPerf = 0; K.Audio.blackout(true); g.shake = Math.max(g.shake || 0, 0.3); cue(C.blackout, C.blackoutSub, "intro"); g.cueOffAt = g.t + 0.8; try { navigator.vibrate && navigator.vibrate([80, 40, 20]); } catch (e) {} }
    }
    if (g.darkPh === "dark") {
      const k = g.t - g.darkT; let L = 0; for (const [tt, v] of CUT) if (k >= tt) L = v;
      W.setDark(1 - L); hudEl.classList.toggle("dark", L < 0.5);
      if (g.cueOffAt && g.t >= g.cueOffAt) { g.cueOffAt = 0; hideCue(); }
      if (c <= 0.9) g.onAt = Math.min(g.onAt || 1e9, g.t); // never later than 0.9 s left
      if (g.onAt && g.t >= g.onAt) lightsOn(true);
    }
    if (g.darkPh === "back") { const k = clamp((g.t - g.backT) / 0.4, 0, 1); W.renderer.toneMappingExposure = W.S.exposure + 0.25 * (1 - smooth(k)); if (k >= 1) g.darkPh = "over"; }
  }
  // the return is a win, not a fault: one clean switch-on, a little overexposed, the gauge pops back
  function lightsOn(say) {
    if (say || g.darkPh === "dark") K.Audio.blackout(false); else K.Audio.darkFlag(false);
    W.setDark(0); hudEl.classList.remove("dark"); g.backT = g.t; g.darkPh = say ? "back" : "over"; if (!say) W.renderer.toneMappingExposure = W.S.exposure;
    if (!say) return;
    g.punch = 0.6; try { navigator.vibrate && navigator.vibrate([15, 30, 15]); } catch (e) {}
    if (!g.auto) { g.finaleCue = true; cue(C.finaleCue, "", "intro"); } hudEl.classList.add("final");
  }
  /* ---------- attract: a 4-beat loop at 0.85 s (inside the perfect band): front, left, right, 불쇼 ---------- */
  const titleEl = document.querySelector("#intro h1"), goEl = $("go");
  function attract() {
    const k = g.phT - 0.4, beat = Math.floor(k / 0.85), F = K.Food;
    if (k < 0 || beat === g.aBeat) return;
    g.aBeat = beat; const b = beat % 4, du = [0, -0.6, 0.6, 0][b];
    if (b === 3 && F.spilled > 120) F.reset(); // keep the wok full (behind the fire)
    g.toss = { t0: g.t, s: b === 3 ? 1 : 0.75, du, dv: Math.sqrt(1 - du * du) };
    F.launch({ strength: b === 3 ? 1.45 : 1.05, dirU: du, dirV: Math.sqrt(1 - du * du) }, g.t);
    g.shake = Math.max(g.shake || 0, 0.6); g.hei = Math.max(g.hei, 0.45);
    if (b === 3) { g.flareAt = g.t + 0.12; g.flareN = 5; g.fever = g.t + 0.6; }
    titleEl.classList.remove("beat", "fever"); void titleEl.offsetWidth; titleEl.classList.add(b === 3 ? "fever" : "beat");
    goEl.classList.toggle("glow", b === 3);
  }
  /* ---------- camera ---------- */
  const _cp = new T.Vector3(), _cl = new T.Vector3(), _tg = new T.Vector3(), _a = new T.Vector3(), _b = new T.Vector3(), _ax = new T.Vector3(1, 0, 0);
  function camera(dt) {
    const pan = g.ph === "plate" || g.ph === "take" ? SIDE * 0.6 : g.ph === "egg" ? SIDE * 0.14 : 0; // the egg phase keeps the whole wok in view
    g.cam += ((["garnish", "result"].includes(g.ph) ? 1 : 0) - g.cam) * DAMP(0.35, dt);
    g.pan += (pan - g.pan) * DAMP(0.25, dt);
    const close = smooth(g.cam);
    g.camLow = (g.camLow || 0) + (((g.ph === "rice" && g.started) || g.ph === "egg" || g.ph === "intro" ? 1 : 0) - (g.camLow || 0)) * DAMP(0.6, dt);
    _tg.set(W.plate.position.x, W.plateC.y + 0.15, W.plateC.z);
    _cp.set(W.camBase.x + g.pan, W.camBase.y, W.camBase.z).lerp(_a.copy(_tg).add(_b.set(0, 3.1, 2.3)), close);
    _cl.set(g.pan, -0.05, 0.35).lerp(_a.copy(_tg).add(_b.set(0, g.ph === "result" ? -0.6 : -0.1, 0)), close);
    if (g.camLow > 0.001) _cp.sub(_cl).applyAxisAngle(_ax, 10 * DEG * g.camLow * (1 - close)).add(_cl);
    if (g.finaleT) { const k = (g.t - g.finaleT) / (g.slowUsed ? 0.75 : 1.1); if (k < 1) _cl.y += 0.55 * Math.sin(Math.PI * k); }
    W.camera.position.copy(_cp); W.camera.lookAt(_cl);
    g.shake = Math.max(0, (g.shake || 0) - dt * 4); const sk = 0.022 * Math.min(1.6, g.shake) * g.shake; // ≤ ~4 px
    if (sk > 0.0005) W.camera.position.add(_a.set(rnd(-1, 1) * sk, rnd(-1, 1) * sk, 0));
    g.punch = Math.max(0, (g.punch || 0) - dt / 0.12); const fov = 30 - 2 * Math.sin(Math.PI * Math.min(1, g.punch)) - 4 * (g.slowAmt || 0); /* the slow-mo pushes in */ if (W.camera.fov !== fov) { W.camera.fov = fov; W.camera.updateProjectionMatrix(); }
  }
  function hud() {
    if (g.ph === "rice") { timerEl.textContent = g.clock.toFixed(1); timerEl.classList.toggle("warn", g.started && g.clock < 3); }
    // the beat bar: the needle runs from the last toss, the gold band is when to toss again
    beatEl.hidden = !!cueMode || !(g.ph === "rice" && g.started && g.clock > 0);
    if (cueMode === "a") approachRing(); // from the start: the needle waits at 0 for the first toss
    if (!beatEl.hidden) { const w = readyWin(), b = g.t - g.lastFlick; needleEl.style.left = clamp(b / 1.8, 0, 1) * 100 + "%"; beatEl.classList.toggle("in", b >= w.gold0 && b <= w.gold1); beatEl.classList.toggle("late", b > w.late);
      zoneEl.style.left = (w.gold0 / 1.8) * 100 + "%"; zoneEl.style.width = ((w.gold1 - w.gold0) / 1.8) * 100 + "%"; beatEl.classList.toggle("c3", g.combo >= 3 && g.combo < 5); beatEl.classList.toggle("c5", g.combo >= 5); }
  }
  // 밥 % (how much is still yours) and 익음 % (cooked grains), live
  function liveStats() {
    const F = K.Food, st = F.stats(), left = Math.round(100 * (1 - st.spill));
    let sum = 0, n = 0; for (let i = 0; i < F.n; i++) if (F.st[i] !== 4 && F.st[i] !== 7) { sum += Math.min(1, F.cook[i] / 0.9); n++; } // how far along it is, so it climbs every toss
    const ck = Math.round((100 * sum) / Math.max(1, n));
    if (g.riceLeft != null && left < g.riceLeft) popNum(`-${g.riceLeft - left}%`, "rice"); // the chip shows grains, the pop shows the share lost
    g.riceLeft = left; riceEl.textContent = `흘린 밥 ${F.spilled}알`; riceEl.hidden = !F.spilled; cookEl.hidden = true; void ck;
  }

  /* ---------- loop: fixed 1/60 steps so game time never stretches on a slow phone ---------- */
  let last = performance.now(), acc = 0;
  const TS = +((/[?&]ts=([\d.]+)/.exec(location.search) || [])[1] || 1);
  function frame(now) {
    let dt = Math.min(0.1, (now - last) / 1000) * TS; last = now; // TS: ?ts=0.25 slows the whole game (for capturing frames only)
    if (g.hit > 0) { g.hit -= dt; dt = 0; } // hit-stop on a clean egg landing
    let n = 0;
    if (g.slowT) {
      const k = g.t - g.slowT, sc = k < 0.08 ? 1 : k < 0.2 ? 1 - 0.7 * smooth((k - 0.08) / 0.12) : k < 0.5 ? 0.3 : k < 0.66 ? 0.3 + 0.7 * smooth((k - 0.5) / 0.16) : 1;
      g.slowAmt = (1 - sc) / 0.7;
      if (k >= 0.66) { g.slowT = 0; g.slowAmt = 0; hudEl.classList.remove("slow"); K.Audio.slow(false); } else { step(Math.min(1 / 30, dt) * sc); acc = 0; }
    } else {
      acc += dt;
      while (acc >= 1 / 60 && n++ < 2) { step(1 / 60); acc -= 1 / 60; } // at most 2 steps: a slow frame must not make the next one slower
      if (n >= 2) acc = 0;
    }
    K.Food.draw(); camera(dt); hud();
    W.renderer.render(W.scene, W.camera);
    if (goEl.disabled) { goEl.disabled = false; goEl.textContent = C.go; } // ready: the first frame is on screen
    requestAnimationFrame(frame);
  }

  /* ---------- start screen ---------- */
  W.build("real"); try { const hid = []; W.scene.traverse((o) => { if (!o.visible) { hid.push(o); o.visible = true; } }); W.renderer.compile(W.scene, W.camera); hid.forEach((o) => (o.visible = false)); } catch (e) {}
  for (let i = 0; i < K.Food.n; i++) K.Food.cook[i] = 0.75; // the start screen cooks by itself (attract): show it golden
  function bestLine() { const b = +store.get("kfr-best3d", 0), t = b ? C.tiers.find((x) => b >= x[0]) : null; $("best").textContent = (b ? `내 최고 ${b}점 · 「${t[1]}」 · ` : "") + `오늘의 주문: 계란 ${todayOrder() === "half" ? "반숙" : "완숙"}`; }
  bestLine();
  $("hook").textContent = vs != null ? C.vsHook(vs) : C.hook; $("how").innerHTML = inApp ? C.howTouch : C.how; $("go").textContent = C.loading; $("watch").textContent = C.watchBtn;
  $("go").onclick = () => { K.Audio.unlock(); g.hei = 1.6; W.sparkBurst(24); g.punch = 1; K.Audio.flare(); const go = () => start(false); /* 불 켜: the fire answers the tap at once */ K.Input.requestPermission().then(go, go); };
  $("watch").onclick = () => { K.Audio.unlock(); K.Input.useTouch(); start(true); };
  $("shareOv").onclick = () => { $("shareOv").hidden = true; };
  // ⌂: back to the start screen from anywhere (the wok goes back to cooking by itself)
  const homeEl = $("home");
  function goHome() {
    K.Food.setG(38);
    W.setDark(0); hudEl.classList.remove("dark", "slow", "final"); K.Audio.darkFlag(false); K.Audio.slow(false); cueEl.className = "";
    K.Input.steerAfterFlick = false; K.Input.demoTilt(0, 0);
    g = newGame(false);
    if (K.Egg.ph !== "off") K.Egg.hide();
    K.Food.reset(); for (let i = 0; i < K.Food.n; i++) K.Food.cook[i] = 0.75;
    K.Food.fireTint = 0; K.Food.scorchVis = 0; W.plate.visible = false; W.ladle.visible = false; if (garn) garn.visible = false;
    resEl.hidden = true; resEl.classList.remove("up"); hudEl.hidden = true; cueEl.hidden = true; $("shareOv").hidden = true; tip("");
    introEl.hidden = false; homeEl.hidden = true; W.renderer.toneMappingExposure = W.S.exposure; bestLine();
  }
  homeEl.addEventListener("pointerdown", (e) => e.stopPropagation()); // not a flick
  homeEl.onclick = (e) => { e.stopPropagation(); goHome(); };
  const muteEl = $("mute"), setMute = (m) => { K.Audio.mute(m); muteEl.textContent = m ? "🔇" : "🔊"; muteEl.setAttribute("aria-label", m ? "소리 켜기" : "소리 끄기"); store.set("kfr-mute", m ? 1 : 0); };
  muteEl.addEventListener("pointerdown", (e) => e.stopPropagation()); // not a flick
  muteEl.onclick = (e) => { e.stopPropagation(); K.Audio.unlock(); setMute(!K.Audio.isMuted()); };
  if (store.get("kfr-mute", 0) == 1) setMute(true);
  K.Input.start(onFlick, app);
  app.addEventListener("pointerdown", () => { if (g.introOn && g.skipOK) g.introSkip = true; }); // from the 2nd round a tap skips the intro
  addEventListener("resize", () => W.fit());
  document.addEventListener("visibilitychange", () => { if (document.hidden) K.Audio.suspend(); else K.Audio.resume(); last = performance.now(); });
  // capture only (?ts=0.0001 freezes the loop): advance exactly n fixed steps and draw one frame, for a smooth recorded reel
  function tick(n) { for (let i = 0; i < n; i++) step(1 / 60); K.Food.draw(); camera(n / 60); hud(); W.renderer.render(W.scene, W.camera); }
  K.Game = { get state() { return g; }, start, flick, finalScore, tick };
  requestAnimationFrame(frame);
})();
