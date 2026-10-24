/* game.js — the whole round: start screen → rice 12 s → plating (auto) → egg → take-out → garnish → receipt.
   Owns the wok pose (phone tilt + the chef's toss), the clock (fixed 1/60 steps), scoring, words, HUD, sound cues. */
"use strict";
(function () {
  const K = window.KFR, T = K.T, W = K.W, clamp = K.clamp, smooth = K.smooth, rnd = K.rnd, DEG = K.DEG, SIDE = K.SIDE;
  const C = K.COPY;
  const $ = (id) => document.getElementById(id);
  const app = $("app"), introEl = $("intro"), hudEl = $("hud"), timerEl = $("timer"), wordEl = $("word"), subEl = $("sub"), gaugeEl = $("gauge"), gaugeFill = $("gaugeFill"), comboEl = $("combo"), resEl = $("result"), tipEl = $("tip");
  const store = { get: (k, d) => { try { const v = localStorage.getItem(k); return v == null ? d : v; } catch (e) { return d; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch (e) {} } };

  const RICE_T = 12, MAXT = 50 * DEG;     // the wok follows the phone up to 50°: the sticky rice flows past ~30° and goes over the rim near the end
  const FIRE_MAX = 25;
  let style = store.get("kfr-style", "real"); if (!W.STYLES[style]) style = "real";

  /* ---------- state ---------- */
  let g = null;
  function newGame(auto) {
    return { ph: "intro", t: 0, phT: 0, auto, clock: RICE_T, started: false, lastFlick: -9, toss: null, tossN: 0,
      fire: 0, combo: 0, maxCombo: 0, grades: [], counts: { perfect: 0, good: 0, early: 0, late: 0 }, perfects: 0, maxGap: 0, scorchSaid: -9,
      order: Math.random() < 0.5 ? "half" : "full", eggShakeT: -9, plateK: 0, pats: 0, doneT: 0, poured: 0, res: null,
      pose: { tilt: { u: 0, v: 0 } }, hei: 0, shim: -9, cam: 0, pan: 0, hit: 0 };
  }
  g = newGame(false);

  /* ---------- words ---------- */
  function word(text, kind, sub) {
    wordEl.textContent = text; wordEl.className = "word " + (kind || ""); void wordEl.offsetWidth; wordEl.classList.add("pop");
    subEl.textContent = sub || ""; subEl.className = "sub"; void subEl.offsetWidth; if (sub) subEl.classList.add("pop");
  }
  const cueEl = $("startCue");
  function tip(text) { tipEl.textContent = text || ""; tipEl.hidden = !text; }

  /* ---------- wok pose: phone tilt + the chef's toss ---------- */
  const DAMP = (k, dt) => 1 - Math.exp(-dt / k);
  function updatePose(dt) {
    const tl = K.Input.tilt, P = g.pose, pose = W.pose;
    // the wok follows the phone fast (sensitive); the food then flows with real gravity in food.js
    P.tilt.u += (tl.u - P.tilt.u) * DAMP(0.03, dt); P.tilt.v += (tl.v - P.tilt.v) * DAMP(0.03, dt);
    let pitch = -4 * DEG - P.tilt.v * MAXT, roll = -P.tilt.u * MAXT, x = P.tilt.u * 0.05, z = 0, y = 0;
    pitch += Math.sin(g.t * 1.3) * 0.8 * DEG; roll += Math.sin(g.t * 0.9) * 1.0 * DEG; // the hand never quite stops
    if (g.ph === "egg" && K.Egg.ph === "air") { x += clamp(P.tilt.u * 0.9, -0.5, 0.5); z += clamp(-P.tilt.v * 0.65, -0.35, 0.35); pitch *= 0.4; roll *= 0.4; } // slide the wok under the egg
    const ks = g.t - g.shim; if (ks >= 0 && ks < 0.6) roll += 5 * DEG * Math.sin(ks * 34) * Math.exp(-ks * 6);
    // the toss (motion review): push 0–90 ms, lip snap 90–140 ms, back with a small spring overshoot
    const a = g.toss;
    if (a) {
      const k = g.t - a.t0, s = a.s;
      if (k > 1.0) g.toss = null;
      else {
        const fwd = 0.4 * s * (smooth(k / 0.09) - smooth((k - 0.14) / 0.18)) - 0.04 * s * Math.exp(-(((k - 0.42) / 0.08) ** 2));
        const bump = smooth((k - 0.08) / 0.06) - smooth((k - 0.16) / 0.14);
        const cushion = k > 0.5 && k < 0.78 ? -0.035 * Math.sin(((k - 0.5) / 0.28) * Math.PI) : 0;
        x += a.du * fwd; z += -a.dv * fwd; y += 0.06 * bump + cushion;
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
    if (g.t - g.lastFlick < 0.3 || (g.started && g.clock <= 0)) return;
    K.Audio.unlock();
    const s = clamp(f.strength || 1, 0.3, 1.8), F = K.Food;
    if (!g.started) { g.started = true; tip(g.auto ? C.watching : C.rimTip); cueEl.classList.add("out"); setTimeout(() => { cueEl.hidden = true; cueEl.classList.remove("out"); }, 160); }
    else {
      if (g.tossN >= 3 && !g.auto) tip("");
      // readiness: how long the food has been back on the metal since the last toss came down
      const back = F.toss && F.toss.done && F.contact > 0.8 ? g.t - F.lastLand : -1; // rice still in the air = too early (mashing does not pay)
      g.maxGap = Math.max(g.maxGap, g.t - g.lastFlick);
      const w = readyWin(), grade = back < w.open ? "early" : back < w.gold0 ? "good" : back <= w.gold1 ? "perfect" : back <= w.late ? "good" : "late";
      g.counts[grade]++;
      score(grade, s, 1 - Math.abs(back - w.peak) / (w.gold1 - w.peak));
    }
    g.lastFlick = g.t; g.lastS = s; g.puffed = false; g.jit = (Math.random() - 0.5) * 0.24;
    const du = f.dirU || 0, dv = f.dirV == null ? 1 : f.dirV;
    g.toss = { t0: g.t, s: clamp(s, 0.5, 1.4), du: clamp(du, -0.5, 0.5), dv: 1 };
    F.launch({ strength: s, dirU: du, dirV: dv }, g.t); W.puffClear();
    g.tossN++;
    K.Audio.toss(s, f.snap == null ? 0.5 : f.snap);
    if (s > 1.55) word(C.tooHard, "bad");
  }
  /* one toss's worth: timing × height; perfect tosses build a combo, the 불맛 meter fills */
  /* the ready window moves with the pile: a higher toss cools the rice, so it needs a little longer back on the metal */
  function readyWin() { const sh = clamp(0.12 * ((g.lastS || 1) - 1), -0.08, 0.12) + (g.jit || 0); return { open: 0.25 + sh, gold0: 0.45 + sh, peak: 0.62 + sh, gold1: 0.79 + sh, late: 1.3 + sh }; }
  function score(grade, s, q) {
    const hm = 0.65 + 0.35 * clamp((s - 0.55) / 0.75, 0, 1); // higher and flashier pays more (operator)
    g.grades.push(grade);
    if (grade === "perfect") {
      g.combo++; g.perfects++; g.maxCombo = Math.max(g.maxCombo, g.combo);
      const add = 2 * hm * (0.65 + 0.35 * clamp(q, 0, 1)) * (1 + Math.min(0.5, 0.1 * (g.combo - 1))); // dead centre pays most
      g.fire = Math.min(FIRE_MAX, g.fire + add);
      g.hei = 1 + Math.min(0.6, 0.15 * (g.combo - 1)); W.sparkBurst(10 + Math.min(20, g.combo * 3)); K.Audio.flare(); g.punch = 1; ember();
      try { if (navigator.vibrate) navigator.vibrate(C.combo.some((c) => c[0] === g.combo) ? [20, 40, 20] : 30); } catch (e) {}
      const title = C.combo.slice().reverse().find((c) => g.combo >= c[0]);
      word(C.perfect, "great", title && g.combo >= 2 ? title[1] : C.perfectSub);
      if (title && g.combo >= (g.bestTitleN || 0)) { g.bestTitleN = g.combo; g.bestTitle = title[1]; }
      comboEl.textContent = g.combo >= 2 ? `${g.combo} 연속` : ""; comboEl.className = "combo"; void comboEl.offsetWidth; if (g.combo >= 2) comboEl.classList.add("pop");
    } else {
      if (grade === "good") { // so-so keeps the combo, fills a little
        g.fire = Math.min(FIRE_MAX, g.fire + 0.3 * hm); g.hei = Math.max(g.hei, 0.3); word(C.good, "ok", g.tossN < 4 ? C.goodChip : "");
        gaugeFill.style.transform = `scaleY(${(g.fire / FIRE_MAX).toFixed(3)})`; K.Food.fireTint = (g.fire / FIRE_MAX) * 0.22; return;
      }
      else if (grade === "early") word(C.early, "meh", C.earlySub);
      else word(C.late, "bad", C.lateSub);
      if (g.combo >= 3) subEl.textContent = C.comboLost;
      g.combo = 0; comboEl.textContent = ""; comboEl.className = "combo";
    }
    gaugeFill.style.transform = `scaleY(${(g.fire / FIRE_MAX).toFixed(3)})`;
    gaugeEl.classList.toggle("hot", g.combo >= 3);
    K.Food.fireTint = (g.fire / FIRE_MAX) * 0.22;
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
      g.flip2Done = true; g.res1 = E.res; tip("");
      E.flipStart({ strength: Math.min(f.strength || 1, 1.3), dirU: 0, second: true }, g.t); K.Audio.toss(f.strength || 1, f.snap || 0.5);
      return;
    }
    if (E.ph !== "pan" || g.t - E.t0 < 0.7) return;
    K.Audio.unlock();
    g.toss = { t0: g.t, s: clamp(f.strength || 1, 0.5, 1.2) * 0.7, du: 0, dv: 1 };
    E.flipStart(f, g.t); K.Audio.toss(f.strength || 1, f.snap || 0.5); if (!g.auto) tip(C.eggCatch);
  }

  /* ---------- phases ---------- */
  function setPh(ph) { g.ph = ph; g.phT = 0; }
  function start(auto) {
    g = newGame(auto);
    if (K.Egg.ph !== "off") K.Egg.hide();
    K.Food.reset(); K.Food.fireTint = 0; W.plate.visible = false; W.ladle.visible = false; if (garn) garn.visible = false;
    introEl.hidden = true; resEl.hidden = true; resEl.classList.remove("up"); hudEl.hidden = false; hudEl.classList.remove("dim");
    timerEl.hidden = false; gaugeEl.hidden = false;
    gaugeFill.style.transform = "scaleY(0)"; comboEl.textContent = ""; wordEl.textContent = ""; subEl.textContent = "";
    timerEl.textContent = RICE_T.toFixed(1); timerEl.classList.remove("warn");
    setPh("rice");
    tip(auto ? C.watching : "");
    if (!auto) { // nothing moves until the first toss, so say so big, over the wok
      const touch = K.Input.mode !== "motion", gg = g;
      cueEl.className = touch ? "touch" : ""; $("scBig").textContent = touch ? C.cueTouch : C.cueMotion; $("scSmall").textContent = C.cueSmall; cueEl.hidden = false;
      setTimeout(() => { if (g === gg && !g.started) { $("scBig").textContent = C.cueProd; cueEl.classList.add("fast"); } }, 4000);
    }
  }
  function startPlate() {
    setPh("plate"); hudEl.classList.add("dim"); tip("");
    W.plate.visible = true; W.plate.position.set(SIDE * 3.2, W.plateC.y, W.plateC.z); W.plate.scale.set(1, 1, 1);
  }
  function startEgg() {
    setPh("egg"); hudEl.classList.remove("dim"); timerEl.hidden = true; gaugeEl.hidden = true; comboEl.textContent = "";
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
        if (k > 0.8 && (left < F.n * 0.03 || k > 3.4)) g.poured = g.t;
      } else {
        const q = g.t - g.poured;
        if (q < 0.2 && g.shim < g.poured) g.shim = g.t; // two quick shakes for the last grains
        g.plateK += (0 - g.plateK) * DAMP(0.08, dt);
        // two ladle pats round the heap off
        const top = pc.y + 0.04 + F.plateMound(0);
        const tap = q < 0.25 ? 0 : q < 0.37 ? Math.sin(((q - 0.25) / 0.12) * Math.PI) : q < 0.49 ? Math.sin(((q - 0.37) / 0.12) * Math.PI) : 0;
        W.ladle.visible = q > 0.2 && q < 0.6; W.ladle.position.set(px - 0.02 * SIDE, top + 0.2 - 0.17 * tap, pc.z); W.ladle.rotation.set(0, 0, -0.15 * SIDE);
        if ((q > 0.31 && g.pats === 0) || (q > 0.43 && g.pats === 1)) { g.pats++; g.plateSq = 1; K.Audio.land("egg-soft"); }
        if (q > 0.6 && !g.doneT) { g.doneT = g.t; K.Audio.ding(); word(C.plated, "ok"); }
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
      const land = pc.y + 0.04 + K.Food.plateMound(d) * 0.95 + (egg && d < 0.46 ? 0.11 * Math.max(0, 1 - (d / 0.5) ** 2) + 0.02 : 0);
      dummy.position.set(W.plate.position.x + q.x, land + 0.9 * (1 - f) ** 2, pc.z + q.z);
      if (i < 110) { dummy.scale.set(1, 1, 1); dummy.rotation.set(-Math.PI / 2 + 0.3 * Math.sin(q.rx) + (1 - f) * 6, q.ry, 0); dummy.updateMatrix(); kim.setMatrixAt(i, dummy.matrix); }
      else { dummy.scale.set(0.008, 0.005, 0.013); dummy.rotation.set(q.rx, q.ry, 0); dummy.updateMatrix(); ses.setMatrixAt(i - 110, dummy.matrix); }
    });
    kim.instanceMatrix.needsUpdate = ses.instanceMatrix.needsUpdate = true;
  }

  const rimCol = new T.Color(), RC = { raw: K.lin("#ffffff"), orange: K.lin("#ff7a1a"), gold: K.lin("#ffd23a"), red: K.lin("#e0261a"), dark: K.lin("#2a120a") };
  /* ---------- the step (fixed 1/60) ---------- */
  function step(dt) {
    g.t += dt; g.phT += dt;
    if (g.auto) autoPlay();
    updatePose(dt);
    const F = K.Food, E = K.Egg;
    if (g.ph === "rice" && g.started) { g.clock = Math.max(0, g.clock - dt); if (g.clock <= 0 && g.t - g.lastFlick > 0.9 && F.air === 0) startPlate(); }
    const plateOn = ["plate", "egg", "take", "garnish", "result"].includes(g.ph);
    W.plate.visible = plateOn;
    const ctx = { t: g.t, dt, pour: g.ph === "plate" && !g.poured && g.phT > 0.6 ? SIDE * clamp((g.phT - 0.6) / 0.5, 0, 1) : 0, heat: g.ph === "rice" ? (g.started ? 1 : 0) : g.ph === "plate" ? 0.2 : 0,
      plate: plateOn ? { x: W.plate.position.x, y: W.plateC.y + 0.01, z: W.plateC.z, on: true, pats: g.pats } : null };
    const r = F.step(ctx);
    if (r.landed > 30 && g.ph === "rice") K.Audio.land("rice");
    if (r.spilledNow > 4 && g.ph === "rice") { K.Audio.land("spill"); if (g.t - (g.spillSaid || -9) > 1.5) { g.spillSaid = g.t; word(C.spill, "bad"); } }
    if (plateOn && g.ph !== "result" && g.ph !== "garnish") plateStep(dt);
    // the diegetic cue: the rim warms while the food is ready to go again; smoke when the bottom starts to scorch
    let rim = 0, steam = 0.12, smoke = 0;
    if (g.ph === "rice" && g.started && F.toss && F.toss.done) {
      const back = g.t - F.lastLand;
      const w = readyWin(), big = g.tossN <= 2 ? 1 : 0; // the first two tosses show the cue bigger
      if (F.contact < 0.8 || back < w.open) { rim = 0.15; rimCol.copy(RC.raw); }
      else if (back < w.gold0) { rimCol.copy(RC.raw).lerp(RC.orange, (back - w.open) / (w.gold0 - w.open)); rim = 0.5; }
      else if (back <= w.gold1) { rimCol.copy(RC.gold); rim = 1.0; if (!g.puffed) { g.puffed = true; W.puffBurst(1 + big); } }
      else if (back <= w.late) { rimCol.copy(RC.orange).lerp(RC.red, (back - w.gold1) / (w.late - w.gold1)); rim = 0.75; }
      else { rimCol.copy(RC.red).lerp(RC.dark, clamp((back - w.late) / 0.5, 0, 1)); rim = 0.55; }
      const b = F.bottom(); smoke = clamp((b.scorch - 0.04) * 6, 0, 1); steam = 0.18 + 0.2 * clamp(back, 0, 1);
      if (smoke > 0.4 && g.t - g.scorchSaid > 2.5) { g.scorchSaid = g.t; word(C.scorch, "bad"); }
    }
    if (g.ph === "egg") steam = 0.1;
    g.hei = Math.max(0, g.hei - dt * 2.2);
    W.fx(g.t, dt, { hei: g.hei, heat: g.ph === "result" || g.ph === "garnish" ? 0.6 : 1, lick: g.toss ? 1 : 0, steam: g.ph === "result" ? 0 : steam, smoke, rim, rimCol });
    K.Audio.sizzle(g.ph === "rice" ? (g.started ? F.contact : 0.2) : g.ph === "egg" ? (E.ph === "air" ? 0.1 : 0.55) : 0);
    K.Audio.burnWarn(smoke);
    if (g.ph === "egg") {
      // the cook's shake test: a quick wiggle of the phone loosens a set egg; a raw one stays stuck
      const tl = K.Input.tilt, sp = Math.hypot(tl.u - (g.ltu || 0), tl.v - (g.ltv || 0)) / dt; g.ltu = tl.u; g.ltv = tl.v;
      if (sp > 6 && g.t - g.eggShakeT > 0.7 && E.ph === "pan" && !g.auto) {
        g.eggShakeT = g.t; g.shim = g.t; const res = E.shake(g.t, tl.u, tl.v);
        if (res === "stuck") word(C.eggStuck, "meh"); else if (res === "slides") word(C.eggSlides, "ok");
      }
      if (E.ph === "pan" && E.cook > 1.4 && !g.eggBurnSaid) { g.eggBurnSaid = true; word(C.eggBurning, "bad"); }
      if (E.ph === "pan" && E.cook > 1.58) { g.eggAuto = !g.auto; eggFlick({ strength: 0.65, dirU: 0 }); } // left too long: it gets flipped for you, badly
      const win = g.order === "half" ? [0.5, 0.82] : [0.82, 1.15];
      E.cue(g.t, E.cook < win[0] - 0.08 ? 0 : E.cook <= win[1] ? clamp((E.cook - win[0] + 0.08) / 0.12, 0, 1) : clamp(1 - (E.cook - win[1]) / 0.15, 0, 1));
      const ev = E.step({ t: g.t, dt, heat: 1 });
      if (ev === "plop") K.Audio.land("egg-soft");
      else if (ev) eggLanded(ev);
      if (E.ph === "landed" && g.t - E.landT > (g.flip2Open && !g.flip2Done ? 2.4 : 1.4)) { setPh("take"); g.takeT = g.t; }
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
    if (ev === "counter") { K.Audio.land("egg-splat"); word(C.eggOut, "bad"); return; }
    if (g.res1) { // the second landing
      g.flip2Up = ev === "clean" && !E.flipped; K.Audio.land(ev === "burst" ? "egg-splat" : "egg-soft");
      if (g.flip2Up) { word(C.eggYolkUp, "great"); K.Audio.ding(); } else word(ev === "burst" ? C.eggBurst : C.eggFold, ev === "burst" ? "bad" : "meh");
      return;
    }
    if (ev === "clean" && E.flipped && !g.eggAuto) { g.flip2Open = true; if (!g.auto) setTimeout(() => g.ph === "egg" && !g.flip2Done && tip(C.flip2Tip), 500); }
    g.hit = ev === "clean" && r.errDeg <= 12 && r.where === "centre" ? 0.09 : 0.05;
    K.Audio.land(ev === "burst" ? "egg-splat" : "egg-soft");
    if (ev === "burst") word(C.eggBurst, "bad");
    else if (ev === "fold") word(C.eggFold, "meh");
    else if (r.where === "wall") word(`${r.angle}°`, "ok", C.eggWall);
    else word(`${r.angle}°!`, r.errDeg <= 12 ? "great" : "ok", r.errDeg <= 12 ? C.eggPerfect : C.eggGood);
    if (ev === "clean" && r.errDeg <= 12) K.Audio.ding();
  }

  /* ---------- scoring: rice 60 = doneness 25 + 불맛 25 + 안 흘리기 10, egg 40 = timing 12 + flip 14 + catch 10 + yolk 4 ---------- */
  function finalScore() {
    const st = K.Food.stats(), r = g.res1 || K.Egg.res, r2 = g.res1 ? K.Egg.res : null;
    const done = Math.max(0, Math.round(25 * clamp((st.good - 0.35) / 0.55, 0, 1) - 12 * clamp(st.burnt * 3, 0, 1)));
    const fire = Math.round(g.fire);
    const clean = Math.round(clamp(g.tossN / 6, 0, 1) * Math.max(0, 10 - Math.round((st.spill * 100) / 1.5)));
    const win = g.order === "half" ? [0.5, 0.82] : [0.82, 1.15];
    let timing = 0, flip = 0, cat = 0, yolk = 0;
    if (r) {
      const c = r.cookAtFlip, off = c < win[0] ? win[0] - c : c > win[1] ? c - win[1] : 0;
      timing = Math.round(12 * clamp(1 - off / 0.3, 0, 1));
      flip = (r.fold ? 3 : Math.round(10 * clamp(1 - Math.max(0, r.errDeg - 8) / 50, 0, 1))) + (g.flip2Up ? 4 : 0); // 10 for the flip, 4 for turning it back yolk-up
      cat = g.eggAuto ? 0 : Math.round(10 * r.catchQ);
      yolk = r.burst || (r2 && r2.burst) ? 0 : 4;
    }
    return { done, fire, clean, timing, flip, cat, yolk, total: done + fire + clean + timing + flip + cat + yolk, st, r };
  }

  /* ---------- result ---------- */
  function showResult() {
    setPh("result"); hudEl.hidden = true; tip("");
    const s = (g.res = finalScore());
    const best = +store.get("kfr-best3d", 0), rec = s.total > best && best > 0;
    if (s.total > best) store.set("kfr-best3d", s.total);
    const tier = C.tiers.find((x) => s.total >= x[0]), next = C.tiers.slice().reverse().find((x) => x[0] > s.total);
    const line = (a, b, c) => `<li><span>${a}</span><span>${b} / ${c}</span></li>`;
    resEl.innerHTML = `<div class="slip"><div class="shop">${C.shop}</div><div class="meta">${C.shopSub} · 김치볶음밥 1 · 계란 ${g.order === "half" ? "반숙" : "완숙"}</div>
      ${rec ? `<div class="rec">신기록</div>` : ""}
      <ul>${line("밥 익힘", s.done, 25)}${line("불맛 (박자·높이)", s.fire, 25)}${line("안 흘리기", s.clean, 10)}${line("계란 타이밍", s.timing, 12)}${line("뒤집기", s.flip, 14)}${line("받기", s.cat, 10)}${line("노른자", s.yolk, 4)}</ul>
      <div class="tot"><span class="stamp">${tier[1]}</span><span class="score"><b id="scoreN">0</b><small>점</small></span></div>
      <div class="why">${whyLine(s)}</div>
      ${next ? `<div class="near">「${next[1]}」까지 ${next[0] - s.total}점</div>` : ""}
      <div class="row"><button class="btn" id="again" type="button">${C.again}</button><button class="btn ghost" id="share" type="button">${C.share}</button></div></div>`;
    resEl.hidden = false; setTimeout(() => resEl.classList.add("up"), 900); // the finished dish gets a moment first
    const n = $("scoreN"), t0 = performance.now() + 1200;
    (function up() { if (performance.now() < t0) return requestAnimationFrame(up); const k = Math.min(1, (performance.now() - t0) / 900); n.textContent = Math.round(s.total * smooth(k)); if (k < 1) requestAnimationFrame(up); else { K.Audio.stamp(); const st = resEl.querySelector(".stamp"); if (st) st.classList.add("pop"); } })();
    $("again").onclick = () => { K.Audio.unlock(); start(false); };
    $("share").onclick = share;
  }
  function whyLine(s) {
    const st = s.st, parts = [];
    if (st.burnt > 0.08) parts.push(`탄 밥 ${Math.round(st.burnt * 100)}%`);
    if (st.raw > 0.15) parts.push(`덜 익은 밥 ${Math.round(st.raw * 100)}%`);
    if (st.spill > 0.01) parts.push(`흘린 밥 ${Math.round(st.spill * 100)}%`);
    if (g.maxGap > 2.2) parts.push(`최대 ${g.maxGap.toFixed(1)}초 안 볶음`);
    parts.push(`불맛 ${Math.round((g.fire / FIRE_MAX) * 100)}%`); if (g.maxCombo >= 2) parts.push(`최고 콤보 x${g.maxCombo}${g.bestTitle ? " " + g.bestTitle : ""}`);
    const c = g.counts, n = c.perfect + c.good + c.early + c.late;
    if (n) parts.push(`금빛에 휙 ${c.perfect}/${n}번` + (c.early > c.late && c.early > 1 ? " (너무 빨리 친 게 많음)" : c.late > 1 ? " (늦게 친 게 많음)" : ""));
    const r = s.r, win = g.order === "half" ? [0.5, 0.82] : [0.82, 1.15];
    if (r) {
      if (r.cookAtFlip < win[0] - 0.05) parts.push("계란을 너무 일찍 뒤집음"); else if (r.cookAtFlip > win[1] + 0.05) parts.push("계란을 너무 늦게 뒤집음");
      if (r.burst) parts.push(r.raw ? "덜 익어서 찢어짐" : "너무 세게 쳐서 노른자 터짐"); else if (r.fold) parts.push("반으로 접힘"); else parts.push(`계란 ${r.angle}°`);
      if (r.where === "wall") parts.push("벽에서 받음"); else if (r.where === "out") parts.push("계란을 못 받음");
      if (g.flip2Open && !g.flip2Up) parts.push(g.flip2Done ? "두 번째 뒤집기 실패" : "한 번 더 안 뒤집음");
    }
    return parts.join(" · ");
  }
  function share() {
    try {
      W.renderer.render(W.scene, W.camera);
      const src = W.renderer.domElement, cv = document.createElement("canvas"); cv.width = 1080; cv.height = 1350;
      const c = cv.getContext("2d"), s = g.res, tier = C.tiers.find((x) => s.total >= x[0]);
      c.fillStyle = "#fbf6ea"; c.fillRect(0, 0, 1080, 1350);
      // the dish fills the top (the result camera centres the plate in the upper half), the score and the stamp below
      const sw = src.width, sh = src.height, cw = Math.min(sw, sh * 0.5);
      c.drawImage(src, (sw - cw) / 2, sh * 0.04, cw, cw, 90, 50, 900, 900);
      c.textAlign = "center";
      c.fillStyle = "#1f2a33"; c.font = "60px 'Black Han Sans', sans-serif"; c.fillText(`${C.shop} · 김치볶음밥 한 그릇`, 540, 1040);
      c.fillStyle = "#E8481C"; c.font = "170px 'Black Han Sans', sans-serif"; c.fillText(`${s.total}점`, 430, 1230);
      c.save(); c.translate(820, 1180); c.rotate(-0.14); c.strokeStyle = c.fillStyle = "#c81e1e"; c.lineWidth = 8; c.font = "54px 'Black Han Sans', sans-serif";
      const tw = c.measureText(tier[1]).width + 40; c.strokeRect(-tw / 2, -46, tw, 84); c.fillText(tier[1], 0, 14); c.restore();
      c.fillStyle = "#8a7a66"; c.font = "34px 'Gowun Dodum', sans-serif"; c.fillText("폰을 웍처럼 휙! · D:OT lab", 540, 1310);
      cv.toBlob((blob) => {
        const file = blob && new File([blob], "kimchi-fried-rice.png", { type: "image/png" });
        if (file && navigator.canShare && navigator.canShare({ files: [file] })) navigator.share({ files: [file], title: "김치볶음밥 한 그릇", text: `${s.total}점 · ${tier[1]}` }).catch(() => {});
        else { const ov = $("shareOv"); ov.querySelector("img").src = cv.toDataURL("image/png"); ov.hidden = false; }
      });
    } catch (e) {}
  }

  /* ---------- watch mode (구경하기): a decent cook plays itself ---------- */
  function autoPlay() {
    const F = K.Food, E = K.Egg;
    if (g.ph === "rice") {
      if (!g.started && g.phT > 1.0) flick({ strength: 1.1, dirU: 0, dirV: 1 });
      else if (g.started && g.clock > 0 && F.toss && F.toss.done) {
        if (!g.autoWait) g.autoWait = rnd(0.35, 1.1);
        if (g.t - F.lastLand > g.autoWait) { g.autoWait = 0; flick({ strength: rnd(0.95, 1.35), dirU: 0, dirV: 1 }); }
      }
    } else if (g.ph === "egg") {
      const win = g.order === "half" ? 0.62 : 0.95;
      if (E.ph === "pan" && E.cook > 0.5 && !g.autoShook) { g.autoShook = true; g.shim = g.t; E.shake(g.t, 0.3, 0); word(C.eggSlides, "ok"); }
      if (E.ph === "pan" && E.cook > win) eggFlick({ strength: 1.05, dirU: 0 });
      if (E.ph === "landed" && g.flip2Open && !g.flip2Done && g.t - E.landT > 0.7) eggFlick({ strength: 1.05, dirU: 0 });
      if (E.ph === "air") { const tg = E.target(); if (tg) K.Input.demoTilt(clamp(tg.x / 0.9, -1, 1), clamp(-tg.z / 0.65, -1, 1)); }
      else K.Input.demoTilt(0, 0);
    }
  }

  /* ---------- camera ---------- */
  const _cp = new T.Vector3(), _cl = new T.Vector3(), _tg = new T.Vector3(), _a = new T.Vector3(), _b = new T.Vector3();
  function camera(dt) {
    const pan = g.ph === "plate" || g.ph === "take" ? SIDE * 0.32 : g.ph === "egg" ? SIDE * 0.14 : 0; // the egg phase keeps the whole wok in view
    g.cam += ((["garnish", "result"].includes(g.ph) ? 1 : 0) - g.cam) * DAMP(0.35, dt);
    g.pan += (pan - g.pan) * DAMP(0.25, dt);
    const close = smooth(g.cam);
    _tg.set(W.plate.position.x, W.plateC.y + 0.15, W.plateC.z);
    _cp.set(W.camBase.x + g.pan, W.camBase.y, W.camBase.z).lerp(_a.copy(_tg).add(_b.set(0, 3.1, 2.3)), close);
    _cl.set(g.pan, -0.05, 0.35).lerp(_a.copy(_tg).add(_b.set(0, g.ph === "result" ? -0.6 : -0.1, 0)), close);
    W.camera.position.copy(_cp); W.camera.lookAt(_cl);
    g.punch = Math.max(0, (g.punch || 0) - dt / 0.12); const fov = 30 - 2 * Math.sin(Math.PI * Math.min(1, g.punch)); if (W.camera.fov !== fov) { W.camera.fov = fov; W.camera.updateProjectionMatrix(); }
  }
  function hud() {
    if (g.ph === "rice") { timerEl.textContent = g.clock.toFixed(1); timerEl.classList.toggle("warn", g.started && g.clock < 3); }
  }

  /* ---------- loop: fixed 1/60 steps so game time never stretches on a slow phone ---------- */
  let last = performance.now(), acc = 0;
  function frame(now) {
    let dt = Math.min(0.1, (now - last) / 1000); last = now;
    if (g.hit > 0) { g.hit -= dt; dt = 0; } // hit-stop on a clean egg landing
    acc += dt; let n = 0;
    if (g.ph === "intro") { updatePose(dt); W.fx(now / 1000, dt, { hei: 0, heat: 1, lick: 0, steam: 0.12, smoke: 0, rim: 0 }); acc = 0; }
    while (acc >= 1 / 60 && n++ < 6) { step(1 / 60); acc -= 1 / 60; }
    if (n >= 6) acc = 0;
    K.Food.draw(); camera(dt); hud();
    W.renderer.render(W.scene, W.camera);
    requestAnimationFrame(frame);
  }

  /* ---------- start screen ---------- */
  function setStyle(k) { style = k; store.set("kfr-style", k); W.build(k); for (const b of document.querySelectorAll("[data-style]")) b.setAttribute("aria-pressed", b.dataset.style === k); }
  for (const b of document.querySelectorAll("[data-style]")) b.onclick = () => setStyle(b.dataset.style);
  setStyle(style);
  $("hook").textContent = C.hook; $("how").innerHTML = C.how; $("go").textContent = C.go; $("watch").textContent = C.watchBtn;
  $("go").onclick = () => { K.Audio.unlock(); const go = () => start(false); K.Input.requestPermission().then(go, go); };
  $("watch").onclick = () => { K.Audio.unlock(); K.Input.useTouch(); start(true); };
  $("shareOv").onclick = () => { $("shareOv").hidden = true; };
  K.Input.start(onFlick, app);
  addEventListener("resize", () => W.fit());
  document.addEventListener("visibilitychange", () => { if (document.hidden) K.Audio.suspend(); else K.Audio.resume(); last = performance.now(); });
  K.Game = { get state() { return g; }, start, flick, finalScore };
  requestAnimationFrame(frame);
})();
