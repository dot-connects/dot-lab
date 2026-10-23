/* rice.js — rice + kimchi + scallion: heat, tilt slide, wok-toss sheet, landing, draw (see CONTRACT.md, round2-plan A/B/D/G) */
"use strict";
(function () {
  const K = window.KFR, TAU = K.TAU, clamp = K.clamp;

  /* ---------- tuning ---------- */
  const N_RICE = 400, N_KIMCHI = 34, N_PA = 18; // a full portion: ~100 rice clumps + kimchi + scallion
  const PILE = 0.55;          // pile radius at rest (world units)
  const MOUND = 0.18;         // pile height at its centre
  const G = K.G;
  const RATE = 0.44;          // cook per second at layer 1, heat 1 (balance: see harness)
  const AIR = 0.03;           // flames lick the rice in the air (share of RATE)
  const SNAP_T = 0.15;        // game.js lip snap after the flick: the sheet launches here
  const STAGGER = 0.07;       // lip grains leave first, the near side of the band this much later (the peel-off wave)
  const READY = [0.3, 0.6, 0.9]; // readiness (contact s of the last toss's landed grains): early < [0] <= 불맛 <= [1] < 좋아 <= [2] < 눌었다
  const SHEET = 0.8;               // launched share a 불맛 needs
  const LABELS = {
    perfect: ["불맛!", "#E8481C"], good: ["좋아", "#2BB673"], early: ["너무 빨라", "#1F2A33"],
    late: ["눌었다", "#8A5A2B"], weak: ["약해", "#1F2A33"], spill: ["쏟았다!", "#C81E1E"],
  };
  const layer = (d) => (1 - d) * (1 - d) + 0.15; // bottom layer sears, top barely warms (expert G)
  const KH = [0.85, 1, 1.15], KD = [0.9, 1, 1.05]; // per ingredient flight: kimchi heavy/low, scallion light/high

  const hash = (i) => { const s = Math.sin(i * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };

  /* ---------- colours (plan G): warm orange-ochre rice, deeper translucent kimchi, burnt caps at #5A2A14 ---------- */
  const RAMPS = [
    K.ramp([[0, [240, 176, 120]], [0.3, [230, 134, 66]], [0.75, [220, 116, 50]], [1.1, [204, 128, 46]], [1.3, [168, 92, 34]], [1.5, [90, 42, 20]]]),
    K.ramp([[0, [190, 34, 30]], [0.7, [172, 30, 26]], [1.1, [146, 30, 24]], [1.35, [112, 34, 22]], [1.55, [90, 42, 20]]]),
    K.ramp([[0, [111, 191, 58]], [0.8, [92, 168, 52]], [1.3, [70, 112, 40]], [1.6, [64, 56, 30]]]),
  ];
  const Q = 0.02, NQ = 100;
  const LUT = RAMPS.map((f) => {
    const base = [], shade = [], lit = [], gl = [];
    for (let q = 0; q < NQ; q++) {
      const ck = q * Q, c = f(ck);
      base.push(K.rgb(c)); shade.push(K.rgb([c[0] * 0.72, c[1] * 0.72, c[2] * 0.72]));
      lit.push(K.rgb([Math.min(255, c[0] * 1.18 + 12), Math.min(255, c[1] * 1.18 + 12), Math.min(255, c[2] * 1.18 + 12)]));
      gl.push(ck <= 1.2 ? K.gloss(ck) : 0);
    }
    return { base, shade, lit, gl };
  });
  // bed under the pile: fried-rice orange, not a brown sauce
  const BED = []; for (let q = 0; q < NQ; q++) { const c = RAMPS[0](q * Q); BED.push(K.rgb([Math.min(255, c[0] * 1.02), c[1] * 0.8, c[2] * 0.66])); }
  const qi = (ck) => (ck <= 0 ? 0 : ck >= (NQ - 1) * Q ? NQ - 1 : (ck / Q) | 0);

  /* soft contact shadow sprite (drawn scaled, alpha per unit) */
  let SHADOW = null;
  function shadowSprite() {
    if (SHADOW) return SHADOW;
    const cv = document.createElement("canvas"); cv.width = cv.height = 64;
    const c = cv.getContext("2d"), g = c.createRadialGradient(32, 32, 0, 32, 32, 32);
    g.addColorStop(0, "rgba(20,10,6,1)"); g.addColorStop(0.55, "rgba(20,10,6,.6)"); g.addColorStop(1, "rgba(20,10,6,0)");
    c.fillStyle = g; c.fillRect(0, 0, 64, 64);
    return (SHADOW = cv);
  }

  /* ---------- state ----------
     st: 0 pile (wok-local u,v,h), 1 riding up the wall before the snap (wok-local), 2 in the sheet (world),
         4 flying free (world), 3 spilled (world, falls to the counter at h = -0.32) */
  function create() {
    const units = [];
    let left = N_RICE;
    while (left > 0) { const n = Math.min(left, 3 + ((Math.random() * 3) | 0)); units.push({ kind: 0, n }); left -= n; }
    for (let i = 0; i < N_KIMCHI; i++) units.push({ kind: 1, n: 1 });
    for (let i = 0; i < N_PA; i++) units.push({ kind: 2, n: 1 });
    const M = units.length, F = () => new Float32Array(M);
    const s = {
      M, kind: new Uint8Array(M), n: new Uint8Array(M), st: new Uint8Array(M),
      u: F(), v: F(), h: F(), vu: F(), vv: F(), vh: F(), su: F(), sv: F(), hu: F(), hv: F(),
      u0: F(), v0: F(), dl: F(), rank: F(), hop: new Uint8Array(M),
      depth: F(), cook: F(), rest: F(), con: F(), rot: F(), spin: F(), key: F(), ph: F(),
      toss: new Int32Array(M), shape: [], ord: new Uint16Array(M),
      pc: [0, 0], sheets: [], cur: null, tossN: 0, prevId: 0, lastTossT: -9, combo: 0, maxCombo: 0, wokhei: 0, tosses: 0,
      prevFlickT: -9, readiness: 0, golden: false, lowT: 0, fallback: false, burnAtToss: 0, warned: false, burntWarned: false,
      lastToss: null, events: [], total: N_RICE + N_KIMCHI + N_PA, hot: [],
    };
    for (let i = 0; i < M; i++) {
      const un = units[i];
      s.kind[i] = un.kind; s.n[i] = un.n; s.ord[i] = i;
      const a = Math.random() * TAU, r = Math.sqrt(Math.random()) * PILE;
      s.hu[i] = Math.cos(a) * r; s.hv[i] = Math.sin(a) * r;
      s.u[i] = s.hu[i]; s.v[i] = s.hv[i]; s.depth[i] = un.kind === 2 ? 0.7 + 0.3 * Math.random() : Math.random(); s.h[i] = groundH(s, i);
      s.rot[i] = Math.random() * TAU; s.toss[i] = -1; s.ph[i] = Math.random() * TAU;
      const m = [];
      for (let k = 0; k < un.n; k++) {
        const ma = Math.random() * TAU, mr = un.n > 1 ? 0.008 + Math.random() * 0.022 : 0;
        m.push({ ox: Math.cos(ma) * mr, oy: Math.sin(ma) * mr * 0.8, rot: Math.random() * Math.PI, sz: 0.85 + Math.random() * 0.3, j: (Math.random() - 0.5) * 0.08 });
      }
      s.shape.push({ m, q: Math.random(), strip: un.kind === 2 && Math.random() < 0.35 });
    }
    return s;
  }
  function groundH(s, i) {
    const du = s.u[i] - s.pc[0], dv = s.v[i] - s.pc[1], rh2 = (du * du + dv * dv) / (PILE * PILE);
    return K.surf(s.u[i], s.v[i]) + 0.015 + MOUND * s.depth[i] * Math.max(0.12, 1 - rh2);
  }
  /* top of the pile at a wok-local point: where flying food lands */
  function pileTop(s, u, v) {
    const du = u - s.pc[0], dv = v - s.pc[1], rh2 = (du * du + dv * dv) / (PILE * PILE);
    return K.surf(u, v) + 0.03 + MOUND * Math.max(0, 1 - rh2) * 0.8;
  }

  /* ---------- toss: food rides up the wall on the d side, launches at the lip snap as one sheet ---------- */
  function toss(s, f, t) {
    if (s.cur) finish(s, t);
    const str = clamp(f.strength || 1, 0.2, 1.8), snap = clamp(f.snap == null ? 0.5 : f.snap, 0, 1);
    let du = f.dirU || 0, dv = f.dirV == null ? 1 : f.dirV;
    if (Math.hypot(du, dv) < 0.2) { du = 0; dv = 1; }
    const ja = Math.atan2(dv, du) + (Math.random() - 0.5) * (30 * Math.PI / 180); // ±15° jitter
    du = Math.cos(ja); dv = Math.sin(ja);
    const reach = 0.35 + 1.1 * str;
    // readiness from the previous toss's grains that have landed (plan G)
    let pn = 0, ln = 0, rs = 0;
    for (let i = 0; i < s.M; i++) {
      if (s.toss[i] !== s.prevId || s.st[i] === 3) continue;
      pn += s.n[i]; if (s.st[i] === 0) { ln += s.n[i]; rs += s.rest[i] * s.n[i]; }
    }
    let landed = pn ? ln / pn : 1, readiness = ln ? rs / ln : 0;
    if (s.fallback) { landed = 1; readiness = s.readiness; } // last toss mostly lost: time the pile that is down
    const id = ++s.tossN;
    let launched = 0, avail = 0, aMin = 9, aMax = -9;
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] !== 0) continue;
      avail += s.n[i]; s.con[i] = 0; // a toss stirs the whole pan: contact restarts
      const a = s.u[i] * du + s.v[i] * dv;
      if (0.92 - a > reach) { // beyond reach: sloshes toward the lip, the layers re-mix
        s.su[i] += du * 0.5 * str; s.sv[i] += dv * 0.5 * str;
        s.depth[i] = 0.5 * s.depth[i] + 0.5 * Math.random(); continue;
      }
      s.st[i] = 1; s.toss[i] = id; launched += s.n[i];
      s.u0[i] = s.u[i]; s.v0[i] = s.v[i];
      if (a < aMin) aMin = a; if (a > aMax) aMax = a;
    }
    const span = Math.max(0.05, aMax - aMin);
    for (let i = 0; i < s.M; i++) if (s.st[i] === 1 && s.toss[i] === id) {
      s.rank[i] = (s.u[i] * du + s.v[i] * dv - aMin) / span;
      s.dl[i] = (1 - s.rank[i]) * STAGGER * (0.7 + 0.6 * Math.random());
    }
    // centre flight: rise above the rim from strength + snap, lands on the -d half (too strong: past the far rim)
    const rise = 0.45 * Math.pow(str, 0.7) * (0.8 + 0.4 * snap); // apex above the rim
    const aLand = 0.5 - 0.65 * Math.min(str, 1) - 0.4 * Math.max(0, str - 1); // centre landing along d (wok-local)
    // scatter: a lazy snap sprays the grains, an over-hard flick sprays them past the rim
    const nz = 0.2 + 0.4 * (1 - snap) + 0.6 * Math.max(0, str - 1.1);
    s.sheets.push({ id, tl: t + SNAP_T, du, dv, rise, aLand, str, snap, nz });
    s.cur = { id, t, str, gap: t - s.prevFlickT, readiness, landed, sheet: avail ? launched / avail : 0, launched, out: launched, spill: 0 };
    s.prevId = id; s.tosses++; s.prevFlickT = t;
    s.burnAtToss = stats(s).burnt; s.warned = false; s.burntWarned = false;
  }

  /* one grain leaves the lip: world position now, its own ballistic arc. The lip grains rise highest and land
     farthest back (the pile rolls over), every grain with its own scatter so the food never moves as one block. */
  const _w = [0, 0, 0];
  function launchGrain(s, sh, i, pose) {
    K.toWorld(pose, s.u[i], s.v[i], s.h[i], _w);
    s.u[i] = _w[0]; s.v[i] = _w[1]; s.h[i] = _w[2];
    const r = s.rank[i], k = s.kind[i], nz = sh.nz, du = sh.du, dv = sh.dv;
    const rise = Math.max(0.08, K.RIM_H + sh.rise * KH[k] * (0.55 + 0.75 * r) * (1 + (Math.random() - 0.5) * nz) - s.h[i]);
    const vh = Math.sqrt(2 * G * rise), T = vh / G + Math.sqrt((2 * Math.max(0.05, s.h[i] + rise - 0.22)) / G);
    const a0 = s.u[i] * du + s.v[i] * dv, b0 = -s.u[i] * dv + s.v[i] * du;
    // never back over the handle: a cook's toss comes home short of the near rim; an over-hard one sprays sideways
    const aT = Math.max(-0.72, sh.aLand * KD[k] - 0.25 * (r - 0.5) + (Math.random() - 0.5) * nz * 0.6);
    const bT = b0 * (0.5 + 0.4 * Math.random()) + (Math.random() - 0.5) * nz * 1.2;
    const va = (aT - a0) / T, vb = (bT - b0) / T;
    s.vu[i] = du * va - dv * vb; s.vv[i] = dv * va + du * vb; s.vh[i] = vh;
    s.spin[i] = (4 + 6 * Math.random()) * (Math.random() < 0.5 ? -1 : 1);
    s.hop[i] = 0; s.st[i] = 4;
  }

  /* grains of an already graded toss that go over the rim late still make it a spill */
  function lateSpill(s, id, n) {
    const lt = s.lastToss;
    if (!lt || lt.id !== id || lt.grade === "spill" || lt.quiet) return;
    lt.late = (lt.late || 0) + n;
    if (lt.late < 4) return;
    s.wokhei = Math.max(0, s.wokhei - (lt.gain || 0)); s.combo = 0;
    s.lastToss = { t: lt.t, label: LABELS.spill[0], color: LABELS.spill[1], shown: false, grade: "spill", id, lift: lt.lift, spill: lt.late / Math.max(1, lt.n), readiness: lt.readiness, combo: 0 };
  }

  /* grade a toss once its grains are back (or the next toss starts): timing words only, never "탔다" */
  function finish(s, t) {
    const c = s.cur; s.cur = null;
    if (c.id === 1) { s.lastTossT = c.t; return; } // first toss: nothing to time against
    let g;
    if (c.spill >= 4) g = "spill";
    else if (c.sheet < 0.4) g = "weak";
    else if (c.landed < 0.6 || c.readiness < READY[0]) g = "early";
    else if (c.readiness > READY[2]) g = "late";
    else if (c.readiness <= READY[1] && c.sheet >= SHEET) g = "perfect";
    else g = "good";
    if (c.t - s.lastTossT > 2.2 || g === "late" || g === "spill" || g === "early" || g === "weak") s.combo = 0;
    s.lastTossT = c.t;
    let gain = 0;
    if (g === "perfect" || g === "good") {
      s.combo++;
      const fac = Math.min(1.6, 1 + 0.2 * (s.combo - 1)), w0 = s.wokhei;
      if (c.gap >= 0.6) s.wokhei = Math.min(20, s.wokhei + (g === "perfect" ? 2 * fac : 1));
      gain = s.wokhei - w0;
    }
    s.maxCombo = Math.max(s.maxCombo, s.combo);
    // first 3 tosses: no negative words; mash (< 60% of the last toss down): no word at all
    const neg = g !== "perfect" && g !== "good", quiet = (neg && c.id <= 3) || c.landed < 0.6;
    const [label, color] = LABELS[g];
    s.lastToss = {
      t: c.t, label: quiet ? "" : label + (s.combo >= 2 && !neg ? " x" + s.combo : ""), color, shown: false, quiet,
      grade: g, id: c.id, n: c.launched, gain, lift: c.sheet, spill: c.launched ? c.spill / c.launched : 0, readiness: c.readiness, combo: s.combo,
    };
  }

  /* ---------- update ---------- */
  function update(s, dt, ctx) {
    dt = Math.min(dt, 0.05);
    const pose = ctx.wokPose || K.WORLD;
    const steps = dt > 1 / 50 ? 2 : 1;
    for (let k = 0; k < steps; k++) step(s, dt / steps, ctx, pose, ctx.t - dt + (dt * (k + 1)) / steps);
    s.sheets = s.sheets.filter((sh) => ctx.t - sh.tl < 1.6);
    if (s.cur && (s.cur.out <= 0 || ctx.t - s.cur.t > 1.6)) finish(s, ctx.t);
    // live cue: the last toss is down and its contact time is in the window
    let pn = 0, ln = 0, rs = 0, bot = 0, botHot = 0, inWok = 0, hu = 0, hv = 0;
    for (let i = 0; i < s.M; i++) {
      const st = s.st[i]; if (st === 3) continue;
      inWok += s.n[i];
      if (st === 0 && s.depth[i] < 0.35) { bot += s.n[i]; if (s.cook[i] > 1.0) { botHot += s.n[i]; hu += s.u[i] * s.n[i]; hv += s.v[i] * s.n[i]; } }
      if (s.toss[i] !== s.prevId) continue;
      pn += s.n[i]; if (st === 0) { ln += s.n[i]; rs += s.rest[i] * s.n[i]; }
    }
    // never block the cue: if the last toss is still < 60% down 0.6 s past its normal landing (~1 s after the flick),
    // time every grain at rest instead
    s.lowT = pn && ln < 0.6 * pn && ctx.t - s.prevFlickT > 1.0 ? s.lowT + dt : 0;
    s.fallback = s.lowT > 0.6;
    if (s.fallback) { rs = 0; ln = 0; for (let i = 0; i < s.M; i++) if (s.st[i] === 0) { rs += s.rest[i] * s.n[i]; ln += s.n[i]; } }
    s.readiness = ln ? rs / ln : 0;
    // late still means "toss now": the cue stays on past the window instead of going dark
    s.golden = (s.fallback || !pn || ln >= 0.9 * pn) && s.readiness >= READY[0];
    // burn is a state, not a toss word: smoke when the bottom has sat too long, "burnt" when the burnt share jumps
    if (s.tosses > 0 && ctx.t - s.prevFlickT >= 1.5) {
      const share = inWok ? botHot / inWok : 0;
      if (!s.warned && share >= 0.05) { s.warned = true; s.events.push({ type: "burning", share, u: hu / botHot, v: hv / botHot }); }
      if (!s.burntWarned) {
        const delta = stats(s).burnt - s.burnAtToss;
        if (delta >= 0.03) { s.burntWarned = true; s.events.push({ type: "burnt", delta }); }
      }
    }
    if (s.events.length > 60) s.events.splice(0, s.events.length - 60);
  }
  const _l = [0, 0, 0];
  function step(s, dt, ctx, pose, t) {
    const heat = ctx.heat || K.heat, tilt = ctx.tilt || { u: 0, v: 0 };
    const tm = Math.min(1, Math.hypot(tilt.u, tilt.v)), sq = 1 - 0.3 * tm;
    const pk = Math.min(1, dt * 6);
    s.pc[0] += (tilt.u * 0.45 - s.pc[0]) * pk; s.pc[1] += (tilt.v * 0.45 - s.pc[1]) * pk;
    const air = RATE * AIR * dt, damp = Math.exp(-5 * dt);
    let lN = 0, lU = 0, lV = 0, sN = 0, sU = 0, sV = 0;
    for (let i = 0; i < s.M; i++) {
      const st = s.st[i];
      if (st === 0) {
        const hh = heat(s.u[i], s.v[i]);
        // the metal re-heats the food after a toss: the sear builds up over ~0.6 s, so mashing leaves it pale
        let dc = RATE * layer(s.depth[i]) * hh * (0.25 + 0.75 * Math.min(1, s.con[i] / 0.6)) * dt;
        if (s.cook[i] > 1.05 && s.con[i] < 1.2) dc *= 0.4; // a re-toss in time parks it near golden
        s.cook[i] += dc; s.rest[i] += dt; s.con[i] += dt;
        let gu = s.pc[0] + s.hu[i] * sq, gv = s.pc[1] + s.hv[i] * sq;
        const gr = Math.hypot(gu, gv); if (gr > 0.9) { gu *= 0.9 / gr; gv *= 0.9 / gr; }
        // settle: ~0.3 s slide home, landing momentum carries the roll
        s.u[i] += s.su[i] * dt + (gu - s.u[i]) * Math.min(1, dt * 3.3);
        s.v[i] += s.sv[i] * dt + (gv - s.v[i]) * Math.min(1, dt * 3.3);
        s.su[i] *= damp; s.sv[i] *= damp;
        const r = Math.hypot(s.u[i], s.v[i]); if (r > 0.95) { s.u[i] *= 0.95 / r; s.v[i] *= 0.95 / r; }
        s.h[i] = groundH(s, i);
        s.rot[i] += (s.su[i] + s.sv[i]) * dt * 3;
      } else if (st === 1) {
        // the push carries the food up the wall on the d side; the lip grains lead (band: 0.55 .. 0.9 along d)
        const sh = sheetOf(s, s.toss[i]); if (!sh) { s.st[i] = 0; continue; }
        if (t >= sh.tl + s.dl[i]) { launchGrain(s, sh, i, pose); continue; }
        const k = K.smooth(clamp(1 - (sh.tl - t) / SNAP_T, 0, 1));
        const a0 = s.u0[i] * sh.du + s.v0[i] * sh.dv, b0 = -s.u0[i] * sh.dv + s.v0[i] * sh.du;
        const a = K.lerp(a0, 0.5 + 0.38 * s.rank[i], k), b = b0 * (1 - 0.3 * k);
        s.u[i] = sh.du * a - sh.dv * b; s.v[i] = sh.dv * a + sh.du * b;
        s.h[i] = K.surf(s.u[i], s.v[i]) + 0.02 + 0.06 * s.depth[i];
        s.cook[i] += RATE * layer(s.depth[i]) * dt * 0.5;
      } else if (st === 4) {
        s.cook[i] += air;
        s.vh[i] -= (Math.abs(s.vh[i]) < 0.8 ? 0.8 * G : G) * dt; // a short hang at the apex
        if (s.kind[i] === 2) { // scallion: light, flutters, slow fall
          if (s.vh[i] < -2.5) s.vh[i] = -2.5;
          s.ph[i] += dt * 14; s.u[i] += Math.sin(s.ph[i]) * 0.25 * dt;
        }
        s.u[i] += s.vu[i] * dt; s.v[i] += s.vv[i] * dt; s.h[i] += s.vh[i] * dt;
        s.rot[i] += s.spin[i] * (s.kind[i] === 1 ? 0.4 : 1) * dt;
        if (s.vh[i] < 0) {
          K.toWok(pose, s.u[i], s.v[i], s.h[i], _l);
          const r2 = _l[0] * _l[0] + _l[1] * _l[1];
          if (r2 <= 1) {
            if (_l[2] <= pileTop(s, _l[0], _l[1])) {
              // some grains bounce once off the pile before they settle
              if (!s.hop[i] && Math.random() < 0.15) { s.hop[i] = 1; s.vh[i] = 0.5; s.vu[i] *= 0.5; s.vv[i] *= 0.5; continue; }
              land(s, i, _l[0], _l[1]); lN += s.n[i]; lU += s.u[i] * s.n[i]; lV += s.v[i] * s.n[i];
            }
          } else if (_l[2] < K.RIM_H) {
            s.st[i] = 3; // over the rim: falls on to the counter beyond it
            const r = Math.sqrt(r2); s.vu[i] += (_l[0] / r) * 0.6; s.vv[i] += (_l[1] / r) * 0.6;
            if (s.cur && s.toss[i] === s.cur.id) { s.cur.spill += s.n[i]; s.cur.out -= s.n[i]; }
            else lateSpill(s, s.toss[i], s.n[i]);
            sN += s.n[i]; sU += _l[0] * s.n[i]; sV += _l[1] * s.n[i];
          }
        }
      } else if (s.h[i] > -0.32) {
        s.vh[i] -= G * dt; s.u[i] += s.vu[i] * dt; s.v[i] += s.vv[i] * dt; s.h[i] += s.vh[i] * dt; s.rot[i] += 4 * dt;
        if (s.h[i] <= -0.32) { s.h[i] = -0.32; s.vu[i] = s.vv[i] = s.vh[i] = 0; }
      }
    }
    if (lN) s.events.push({ type: "land", u: lU / lN, v: lV / lN, n: lN });
    if (sN) s.events.push({ type: "spill", u: sU / sN, v: sV / sN, n: sN });
  }
  function sheetOf(s, id) { for (const sh of s.sheets) if (sh.id === id) return sh; return null; }
  function land(s, i, lu, lv) {
    const sh = sheetOf(s, s.toss[i]);
    s.st[i] = 0; s.u[i] = lu; s.v[i] = lv;
    // the pile rolled over: bottom comes up, lip grains end on top, scallion floats to the top
    s.depth[i] = clamp(0.55 * (1 - s.depth[i]) + 0.45 * s.rank[i] + (Math.random() - 0.5) * 0.1, 0, 1);
    if (s.kind[i] === 2) s.depth[i] = Math.max(0.9, s.depth[i]);
    s.rest[i] = 0; s.con[i] = 0;
    s.su[i] = s.vu[i] * 0.5; s.sv[i] = s.vv[i] * 0.5; // keeps rolling toward -d for a moment
    if (sh) { // new home = old home mirrored across the line through the centre perpendicular to d (the roll-over)
      const hd = s.hu[i] * sh.du + s.hv[i] * sh.dv;
      s.hu[i] -= 2 * hd * sh.du; s.hv[i] -= 2 * hd * sh.dv;
    }
    if (s.cur && s.toss[i] === s.cur.id) s.cur.out -= s.n[i];
  }

  /* ---------- queries ---------- */
  function stats(s) {
    let good = 0, burnt = 0, raw = 0, spill = 0;
    for (let i = 0; i < s.M; i++) {
      const n = s.n[i], c = s.cook[i];
      if (s.st[i] === 3) spill += n;
      else if (c > K.RICE_GOOD[1]) burnt += n;
      else if (c < K.RICE_GOOD[0]) raw += n;
      else good += n;
    }
    const T = s.total;
    return { good: good / T, burnt: burnt / T, raw: raw / T, spill: spill / T };
  }
  function airborne(s) { let n = 0; for (let i = 0; i < s.M; i++) if (s.st[i] === 2 || s.st[i] === 4) n += s.n[i]; return n; }
  function contactShare(s) {
    let on = 0, all = 0;
    for (let i = 0; i < s.M; i++) { if (s.st[i] === 3) continue; all += s.n[i]; if (s.st[i] < 2) on += s.n[i]; }
    return all ? on / all : 0;
  }
  function hotspots(s, k) {
    const out = s.hot; out.length = 0;
    const stride = Math.max(1, Math.floor(s.M / k));
    for (let i = (s.tosses * 7) % stride; i < s.M && out.length < k; i += stride) {
      if (s.st[i] > 1) continue;
      out.push({ u: s.u[i], v: s.v[i], h: s.h[i], cook: s.cook[i] });
    }
    return out;
  }

  /* ---------- draw ---------- */
  function drawUnit(c, s, i, x, y, R, sc, lit, sAng, sStr) {
    const kind = s.kind[i], sh = s.shape[i], q = qi(s.cook[i]), L = LUT[kind];
    if (kind === 0) {
      const rx0 = 0.03 * R * sc, ry0 = 0.0148 * R * sc;
      c.lineCap = "round";
      const m = sh.m, ca0 = Math.cos(sAng), sa0 = Math.sin(sAng);
      for (let k = 0; k < m.length; k++) {
        const mm = m[k], ck = qi(s.cook[i] + mm.j);
        let ox = mm.ox * R * sc, oy = mm.oy * R * sc, rot = mm.rot + s.rot[i] * 0.3, rx = rx0 * mm.sz, ry = ry0 * mm.sz;
        if (sStr > 1.02) { // stretched along the screen velocity (volume kept)
          const along = (ox * ca0 + oy * sa0) * sStr, perp = (-ox * sa0 + oy * ca0) / sStr;
          ox = along * ca0 - perp * sa0; oy = along * sa0 + perp * ca0;
          rot = sAng + (mm.rot - 1.5) * 0.4; rx *= sStr; ry /= Math.sqrt(sStr);
        }
        const gx = x + ox, gy = y + oy, hl = Math.max(0, rx - ry), ca = Math.cos(rot) * hl, sa = Math.sin(rot) * hl;
        c.strokeStyle = L.shade[ck]; c.lineWidth = ry * 2;
        c.beginPath(); c.moveTo(gx - ca + ry * 0.25, gy - sa + ry * 0.35); c.lineTo(gx + ca + ry * 0.25, gy + sa + ry * 0.35); c.stroke();
        c.strokeStyle = lit ? L.lit[ck] : L.base[ck]; c.lineWidth = ry * 1.7;
        c.beginPath(); c.moveTo(gx - ca, gy - sa); c.lineTo(gx + ca, gy + sa); c.stroke();
        const gl = L.gl[ck];
        if (gl > 0.05) { c.fillStyle = `rgba(255,246,232,${(gl * 1.4).toFixed(2)})`; c.beginPath(); c.arc(gx - rx * 0.35, gy - ry * 0.45, Math.max(0.7, ry * 0.4), 0, TAU); c.fill(); }
      }
    } else if (kind === 1) {
      // torn kimchi leaf, translucent: uneven quad, lit edge, pale rib on some; shrinks as it cooks
      const z = 0.046 * R * sc * (1 - 0.15 * Math.min(1, s.cook[i])) * (0.85 + 0.3 * sh.q), a = s.rot[i], ca = Math.cos(a), sa = Math.sin(a), qq = sh.q;
      const px = [-1, -0.2, 0.3 + qq * 0.6, 1, 0.2, -0.3 - qq * 0.5], py = [-0.4, -0.75 + qq * 0.2, -0.85, 0.2 + (1 - qq) * 0.3, 0.7, 0.8];
      c.globalAlpha = 0.9;
      c.fillStyle = lit ? L.lit[q] : L.base[q]; c.beginPath();
      for (let k = 0; k < 6; k++) { const X = x + (px[k] * ca - py[k] * sa) * z, Y = y + (px[k] * sa + py[k] * ca) * z * 0.85; k ? c.lineTo(X, Y) : c.moveTo(X, Y); }
      c.closePath(); c.fill(); c.globalAlpha = 1;
      c.strokeStyle = s.cook[i] < 1.2 ? "rgba(255,120,80,.8)" : "rgba(120,50,30,.6)"; c.lineWidth = 1;
      c.beginPath(); c.moveTo(x + (px[0] * ca - py[0] * sa) * z, y + (px[0] * sa + py[0] * ca) * z * 0.85);
      c.lineTo(x + (px[1] * ca - py[1] * sa) * z, y + (px[1] * sa + py[1] * ca) * z * 0.85); c.stroke();
      if (qq > 0.6) { c.strokeStyle = "rgba(247,201,168,.45)"; c.lineWidth = 1.1 * sc; c.beginPath(); c.moveTo(x - ca * z * 0.6, y - sa * z * 0.5); c.lineTo(x + ca * z * 0.6, y + sa * z * 0.5); c.stroke(); }
      const gl = L.gl[q];
      if (gl > 0.05) { c.fillStyle = `rgba(255,240,225,${(gl * 0.9).toFixed(2)})`; c.beginPath(); c.arc(x - z * 0.3, y - z * 0.3, z * 0.16, 0, TAU); c.fill(); }
    } else {
      const z = 0.025 * R * sc;
      if (sh.strip) {
        c.strokeStyle = lit ? L.lit[q] : L.base[q]; c.lineWidth = z * 0.9; c.lineCap = "round";
        const a = s.rot[i]; c.beginPath(); c.moveTo(x - Math.cos(a) * z * 1.5, y - Math.sin(a) * z * 1.2); c.lineTo(x + Math.cos(a) * z * 1.5, y + Math.sin(a) * z * 1.2); c.stroke();
      } else {
        c.fillStyle = L.shade[q]; c.beginPath(); c.ellipse(x, y, z, z * 0.8, s.rot[i], 0, TAU); c.fill();
        c.fillStyle = s.cook[i] < 1.3 ? "#CFE9A8" : "#6d6a4a"; c.beginPath(); c.ellipse(x, y, z * 0.5, z * 0.4, s.rot[i], 0, TAU); c.fill();
      }
    }
  }

  function sortUnits(s) {
    const M = s.M, ord = s.ord, key = s.key, sn = Math.sin(K.THETA), cs = Math.cos(K.THETA);
    for (let i = 0; i < M; i++) key[i] = -sn * s.v[i] + cs * s.h[i];
    for (let i = 1; i < M; i++) { // order barely changes frame to frame: insertion sort is ~O(n)
      const o = ord[i], kv = key[o]; let j = i - 1;
      while (j >= 0 && key[ord[j]] > kv) { ord[j + 1] = ord[j]; j--; }
      ord[j + 1] = o;
    }
  }

  /* food on the wok (rides the pose): sauce bed, units far to near, then the airborne food's contact shadows */
  function drawResting(c, s, vw, pose) {
    const R = vw.R, M = s.M;
    let cs = 0, cn = 0;
    for (let i = 0; i < M; i++) if (s.kind[i] === 0 && s.st[i] === 0) { cs += s.cook[i]; cn++; }
    // a thin oily bed under each clump only: the grains read one by one, not as a single block
    c.fillStyle = BED[qi(cn ? cs / cn : 0)]; c.globalAlpha = 0.4; c.beginPath();
    const rr = 0.065 * R;
    for (let i = 0; i < M; i++) {
      if (s.st[i] > 1 || s.kind[i]) continue;
      const p = K.proj(vw, pose, s.u[i], s.v[i], s.h[i] - 0.03);
      c.moveTo(p[0] + rr, p[1]); c.ellipse(p[0], p[1], rr, rr * 0.72, 0, 0, TAU);
    }
    c.fill(); c.globalAlpha = 1;
    sortUnits(s);
    for (let n = 0; n < M; n++) {
      const i = s.ord[n]; if (s.st[i] > 1) continue;
      const p = K.proj(vw, pose, s.u[i], s.v[i], s.h[i]);
      drawUnit(c, s, i, p[0], p[1], R, 1, false, 0, 1);
    }
    // contact shadows of the food in the air: on the pile / floor / wall, under the front lip
    const spr = shadowSprite(), W = K.WORLD;
    for (let i = 0; i < M; i++) {
      const st = s.st[i]; if (st < 2 || (st === 3 && s.h[i] <= -0.32)) continue;
      K.toWok(pose, s.u[i], s.v[i], s.h[i], _l);
      let p, up;
      if (st !== 3 && _l[0] * _l[0] + _l[1] * _l[1] <= 1) { const fl = pileTop(s, _l[0], _l[1]); up = Math.max(0, _l[2] - fl); p = K.proj(vw, pose, _l[0], _l[1], fl); }
      else if (st === 3) { up = Math.max(0, s.h[i] + 0.32); p = K.proj(vw, W, s.u[i], s.v[i], -0.32); }
      else continue; // over the rim but still in the toss: its shadow is behind the wok wall
      const k = Math.min(1, up / 0.7), sz = 0.07 * R * (1 + 0.8 * k) * (s.kind[i] === 0 ? 1 : 0.8), off = 0.1 * R * k;
      c.globalAlpha = 0.32 - 0.24 * k;
      c.drawImage(spr, p[0] + off - sz, p[1] + off * 0.6 - sz * 0.6, sz * 2, sz * 1.2);
    }
    c.globalAlpha = 1;
  }

  /* food in the air (world space, KFR.WORLD): a ghost, stretch, brighter */
  function drawAirborne(c, s, vw) {
    const R = vw.R, M = s.M, W = K.WORLD;
    sortUnits(s);
    for (let n = 0; n < M; n++) {
      const i = s.ord[n], st = s.st[i];
      if (st < 2 || (st === 3 && s.h[i] <= -0.32)) continue;
      const p = K.proj(vw, W, s.u[i], s.v[i], s.h[i]), x = p[0], y = p[1];
      const q = K.proj(vw, W, s.u[i] - s.vu[i] * 0.03, s.v[i] - s.vv[i] * 0.03, s.h[i] - s.vh[i] * 0.03);
      const dx = x - q[0], dy = y - q[1];
      // stretch 1.6 while rising / falling fast, 1.0 at the apex
      const sStr = 1 + 0.6 * Math.min(1, Math.abs(s.vh[i]) / 3), sAng = Math.atan2(dy, dx);
      const sc = K.scaleAt(Math.max(0, s.h[i] - 0.1));
      // one faint ghost behind it
      c.globalAlpha = 0.22; c.fillStyle = LUT[s.kind[i]].lit[qi(s.cook[i])];
      c.beginPath(); c.ellipse(q[0], q[1], 0.035 * R * sc * sStr, 0.02 * R * sc, sAng, 0, TAU); c.fill();
      c.globalAlpha = 1;
      drawUnit(c, s, i, x, y, R, sc, true, sAng, sStr);
    }
  }

  function draw(c, s, vw, pose) { drawResting(c, s, vw, pose); drawAirborne(c, s, vw, pose); }

  function drawSpilled(c, s, vw) {
    const R = vw.R, spr = shadowSprite();
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] !== 3 || s.h[i] > -0.32) continue;
      const p = K.proj(vw, K.WORLD, s.u[i], s.v[i], s.h[i]);
      c.globalAlpha = 0.3; c.drawImage(spr, p[0] - 0.06 * R + 1.5, p[1] - 0.03 * R + 2, 0.12 * R, 0.06 * R); c.globalAlpha = 1;
      drawUnit(c, s, i, p[0], p[1], R, 1, false, 0, 1);
    }
  }

  /* bowl on the result slip: a heaped mound of what was kept, tasty base, burnt grains as #5A2A14 specks */
  function drawBowl(c, s, cx, cy, r, ct) {
    let kept = 0, sum = 0, sn = 0;
    for (let i = 0; i < s.M; i++) if (s.st[i] !== 3) { kept += s.n[i]; if (s.kind[i] === 0) { sum += clamp(s.cook[i], 0.65, 1.15) * s.n[i]; sn += s.n[i]; } }
    const share = kept / s.total, mh = r * (0.2 + 0.32 * share), mr = r * (0.55 + 0.37 * share);
    const base = RAMPS[0](sn ? sum / sn : 0.9);
    const g = c.createRadialGradient(cx - r * 0.2, cy - mh, r * 0.1, cx, cy - mh * 0.3, mr * 1.1);
    g.addColorStop(0, K.rgb([Math.min(255, base[0] + 30), Math.min(255, base[1] + 30), Math.min(255, base[2] + 25)]));
    g.addColorStop(1, K.rgb([base[0] * 0.7, base[1] * 0.7, base[2] * 0.7]));
    c.fillStyle = g; c.beginPath(); c.ellipse(cx, cy, mr, mr * ct, 0, 0, Math.PI);
    c.bezierCurveTo(cx - mr, cy - mh * 1.45, cx + mr, cy - mh * 1.45, cx + mr, cy); c.fill();
    const pts = [];
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] === 3) continue;
      for (let rep = 0; rep < 3; rep++) { const a = hash(i * 3 + rep) * TAU, d = Math.sqrt(hash(i * 7 + rep + 0.5)) * 0.9; pts.push([i, a, d]); }
    }
    pts.sort((p1, p2) => Math.sin(p1[1]) * p1[2] - Math.sin(p2[1]) * p2[2]);
    const fakeR = r * 1.25;
    for (const [i, a, d] of pts) {
      const lift = (1 - d * d) * mh * 1.05, x = cx + Math.cos(a) * d * mr, y = cy + Math.sin(a) * d * mr * ct - lift;
      drawUnit(c, s, i, x, y, fakeR, 1, false, 0, 1);
    }
    return cy - mh;
  }

  K.Rice = { create, toss, update, draw, drawResting, drawAirborne, drawSpilled, drawBowl, stats, airborne, contactShare, hotspots };
})();
