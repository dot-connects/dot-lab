/* rice.js — rice + kimchi + scallion: heat, tilt slide, wok-toss wave, landing, draw (see CONTRACT.md, DESIGN.md) */
"use strict";
(function () {
  const K = window.KFR, TAU = K.TAU, clamp = K.clamp;

  /* ---------- tuning ---------- */
  const N_RICE = 220, N_KIMCHI = 26, N_PA = 13; // ~12% kimchi, ~6% scallion
  const PILE = 0.58;          // pile radius at rest (world units)
  const MOUND = 0.11;        // pile height at its centre
  const G = 14;              // anim §1
  const RATE = 0.34;         // cook per second for the contact layer at heat 1 (balance: see harness)
  const AIR = 0.05;           // flames lick the rice in the air (share of RATE): keeps doneness from hinging on rhythm alone
  const RAMP0 = 0.8;        // sear rate right after landing (rises to 1 over 0.4 s of contact): constant tossing stays pale
  const V = 3.1;             // toss speed scale (strength 1 lip grain lands near the handle side, 1.5 spills)
  const HEAT_C = K.heat(0, 0);
  const READY = [0.26, 0.45, 0.7]; // readiness: early < [0] <= 완벽 <= [1] < 좋음 <= [2] < late
  const SHEET = 0.8;               // airborne share a 완벽 needs
  const LABELS = {
    perfect: ["불맛!", "#E8481C"], good: ["좋아", "#2BB673"], early: ["덜 익었어", "#C98A3E"],
    late: ["탔다!", "#5A2A14"], weak: ["약해", "#6B7781"], spill: ["쏟았다!", "#C81E1E"],
  };
  const layer = (d) => 1.6 * (1 - d) * (1 - d) + 0.05; // bottom layer sears, top barely warms

  /* deterministic hash so the bowl and shapes don't flicker */
  const hash = (i) => { const s = Math.sin(i * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };

  /* ---------- colour LUT: no string building per frame ---------- */
  const Q = 0.02, NQ = 100;
  const LUT = [K.col.rice, K.col.kimchi, K.col.scallion].map((f) => {
    const base = [], shade = [], lit = [], gl = [];
    for (let q = 0; q < NQ; q++) {
      const ck = q * Q, c = f(ck);
      base.push(K.rgb(c)); shade.push(K.rgb([c[0] * 0.75, c[1] * 0.75, c[2] * 0.75]));
      lit.push(K.rgb([Math.min(255, c[0] * 1.15 + 8), Math.min(255, c[1] * 1.15 + 8), Math.min(255, c[2] * 1.15 + 8)]));
      gl.push(ck <= 1.2 ? K.gloss(ck) : 0); // art: spec only while glossy (.4–1.2)
    }
    return { base, shade, lit, gl };
  });
  const qi = (ck) => (ck <= 0 ? 0 : ck >= (NQ - 1) * Q ? NQ - 1 : (ck / Q) | 0);

  /* ---------- projection (same formula as KFR.proj, inlined so the hot loop allocates nothing) ---------- */
  let PX = 0, PY = 0;
  function P(vw, pose, u, v, h) {
    const lean = 1 + (pose.lean || 0) * 0.12;
    PX = vw.cx + (u + (pose.du || 0)) * vw.R;
    PY = vw.cy - (v + (pose.dv || 0)) * vw.R * vw.ct * lean + (K.RIM_H - h - (pose.lift || 0)) * vw.R * vw.st;
    if (h > K.RIM_H) PY -= (h - K.RIM_H) * vw.R * 0.15;
  }

  /* ---------- state ---------- */
  function create() {
    const units = [];
    // rice in clumps of 3–6 (art §2), kimchi and scallion as single pieces
    let left = N_RICE;
    while (left > 0) { const n = Math.min(left, 3 + ((Math.random() * 4) | 0)); units.push({ kind: 0, n }); left -= n; }
    for (let i = 0; i < N_KIMCHI; i++) units.push({ kind: 1, n: 1 });
    for (let i = 0; i < N_PA; i++) units.push({ kind: 2, n: 1 });
    const M = units.length, F = () => new Float32Array(M);
    const s = {
      M, kind: new Uint8Array(M), n: new Uint8Array(M), st: new Uint8Array(M), // st: 0 pile, 1 sliding to the lip, 2 air, 3 spilled
      u: F(), v: F(), h: F(), vu: F(), vv: F(), vh: F(), su: F(), sv: F(), hu: F(), hv: F(),
      depth: F(), cook: F(), rest: F(), tl: F(), lift: F(), rot: F(), spin: F(), key: F(), hop: new Uint8Array(M),
      toss: new Int32Array(M), shape: [], ord: new Uint16Array(M),
      pc: [0, 0], slU: 0, slV: 1, sl: F(), cur: null, tossN: 0, lastTossT: -9, combo: 0, maxCombo: 0, wokhei: 0, tosses: 0, prevFlickT: -9, readiness: 0, golden: false,
      lastToss: null, events: [], total: N_RICE + N_KIMCHI + N_PA, hot: [],
    };
    for (let i = 0; i < M; i++) {
      const un = units[i];
      s.kind[i] = un.kind; s.n[i] = un.n; s.ord[i] = i;
      const a = Math.random() * TAU, r = Math.sqrt(Math.random()) * PILE;
      s.hu[i] = Math.cos(a) * r; s.hv[i] = Math.sin(a) * r;
      s.u[i] = s.hu[i]; s.v[i] = s.hv[i]; s.depth[i] = Math.random(); s.h[i] = groundH(s, i);
      s.rot[i] = Math.random() * TAU; s.toss[i] = -1;
      // member layout (in R units): grains overlap ~60% inside a small blob
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
    const rh2 = (s.hu[i] * s.hu[i] + s.hv[i] * s.hv[i]) / (PILE * PILE);
    return K.surf(s.u[i], s.v[i]) + 0.012 + MOUND * s.depth[i] * Math.max(0.15, 1 - rh2);
  }

  /* ---------- toss: the sheet peels off the lip, lip grains highest, curls back over the pile ---------- */
  function toss(s, f, t) {
    if (s.cur) finish(s, t);
    const str = clamp(f.strength || 1, 0.2, 1.8), snap = clamp(f.snap == null ? 0.5 : f.snap, 0, 1);
    let du = 0.6 * clamp(f.dirU || 0, -1, 1), dv = 1;
    const dl = Math.hypot(du, dv); du /= dl; dv /= dl;
    const lipU = 0.92 * du, lipV = 0.92 * dv;
    const reach = 0.5 + 1.1 * str, vSlide = 3 + 3 * str;
    // readiness: heat the contact layer took since it last landed (centre-heat seconds)
    let rs = 0, rn = 0, avail = 0, dMin = 9;
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] !== 0) continue;
      avail += s.n[i];
      if (s.depth[i] < 0.35) { rs += Math.min(1.6, s.rest[i]) * s.n[i]; rn += s.n[i]; }
      const d = dist(s.u[i], s.v[i], lipU, lipV, du, dv);
      if (d < dMin) dMin = d;
    }
    s.slU = du; s.slV = dv;
    const id = ++s.tossN;
    let launched = 0, mu = 0, mv = 0;
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] !== 0) continue;
      const d = dist(s.u[i], s.v[i], lipU, lipV, du, dv);
      if (d > reach) { // slosh toward the lip; the layers stir a little
        s.su[i] += du * 0.5 * str; s.sv[i] += dv * 0.5 * str;
        s.depth[i] = 0.5 * s.depth[i] + 0.5 * Math.random(); s.rest[i] *= 0.5; continue;
      }
      s.st[i] = 1; s.toss[i] = id; launched += s.n[i]; mu += s.u[i] * s.n[i]; mv += s.v[i] * s.n[i];
      s.tl[i] = t + 0.07 + (d - dMin) / vSlide; // lip first, the rest peel off after it
      // the push carries every grain up onto the far wall (order kept, band compressed): nothing launches from the near side
      s.sl[i] = Math.max(0, d - (0.1 + 0.35 * (d - dMin))) / (s.tl[i] - t);
      const lift = Math.exp(-(d - dMin) / 0.25);
      s.lift[i] = lift;
      // strength mostly throws farther back (too strong spills over the near rim), height grows slower
      const vUp = V * Math.pow(str, 0.6) * (0.5 + 1.3 * lift) * (0.7 + 0.5 * snap);
      const vBack = V * Math.pow(str, 1.4) * (0.45 + 0.2 * lift) * (1.1 - 0.3 * snap);
      const nz = (0.1 + 0.3 * (1 - snap)) * V * str;
      // velocities are stored now, applied at launch time
      s.vu[i] = -du * vBack + (Math.random() - 0.5) * nz * 0.8 - s.u[i] * 0.25;
      s.vv[i] = -dv * vBack + (Math.random() - 0.5) * nz * 0.6;
      s.vh[i] = vUp + (Math.random() - 0.5) * nz * 0.5;
      s.spin[i] = -vBack * 8 * (0.7 + 0.6 * Math.random()) * (du >= 0 ? 1 : -1);
      s.hop[i] = 0;
    }
    s.cur = {
      id, t, str, gap: t - s.prevFlickT, readiness: rn ? rs / rn : 0, sheet: avail ? launched / avail : 0, launched, out: launched,
      spill: 0, u: launched ? mu / launched : 0, v: launched ? mv / launched : 0,
    };
    s.tosses++; s.prevFlickT = t;
  }
  /* distance from the lip measured along the throw (sideways counts half) */
  function dist(u, v, lu, lv, du, dv) {
    const a = (lu - u) * du + (lv - v) * dv, p = (lu - u) * -dv + (lv - v) * du;
    return Math.sqrt(Math.max(0, a) * Math.max(0, a) + 0.25 * p * p);
  }

  /* grains of an already graded toss that go over the rim late still make it a spill */
  function lateSpill(s, id, n) {
    const lt = s.lastToss;
    if (!lt || lt.id !== id || lt.grade === "spill") return;
    lt.late = (lt.late || 0) + n;
    if (lt.late < 3) return;
    s.wokhei = Math.max(0, s.wokhei - (lt.gain || 0)); s.combo = 0;
    s.lastToss = { t: lt.t, label: LABELS.spill[0], color: LABELS.spill[1], shown: false, grade: "spill", id, lift: lt.lift, spill: lt.late / Math.max(1, lt.n), readiness: lt.readiness, combo: 0, u: lt.u, v: lt.v };
  }

  /* grade a toss once its grains are back (or the next toss starts) */
  function finish(s, t) {
    const c = s.cur; s.cur = null;
    if (c.id === 1) { s.lastTossT = c.t; return; } // first toss of the round: readiness is ~0, no word, no combo change
    const spillF = c.launched ? c.spill / c.launched : 0;
    let g;
    if (c.spill >= 3) g = "spill";
    else if (c.sheet < 0.4) g = "weak";
    // readiness = contact-layer seconds on the metal since landing. DESIGN's 0.6–1.0 s window is for a real 0.22 s
    // flight; ours is ~0.7 s, so the window sits where the rhythm that also gives the best doneness lands (~1.0–1.15 s)
    else if (c.readiness > READY[2]) g = "late";
    else if (c.readiness < READY[0]) g = "early";
    else if (c.readiness <= READY[1] && c.sheet >= SHEET) g = "perfect";
    else g = "good";
    if (c.t - s.lastTossT > 1.8 || g === "late" || g === "spill" || g === "early") s.combo = 0;
    s.lastTossT = c.t;
    let gain = 0;
    if (g === "perfect" || g === "good") {
      s.combo++;
      const fac = Math.min(1.6, 1 + 0.2 * (s.combo - 1)), w0 = s.wokhei;
      // mashing doesn't pay: a toss within 0.6 s of the previous one earns no 불맛
      if (c.gap >= 0.6) s.wokhei = Math.min(20, s.wokhei + (g === "perfect" ? 2 * fac : 1));
      gain = s.wokhei - w0;
    }
    s.maxCombo = Math.max(s.maxCombo, s.combo);
    const [label, color] = LABELS[g];
    s.lastToss = {
      t: c.t, label: label + (s.combo >= 2 && (g === "perfect" || g === "good") ? " x" + s.combo : ""), color, shown: false,
      grade: g, id: c.id, n: c.launched, gain, lift: c.sheet, spill: spillF, readiness: c.readiness, combo: s.combo, u: c.u, v: c.v,
    };
  }

  /* ---------- update ---------- */
  function update(s, dt, ctx) {
    dt = Math.min(dt, 0.05);
    const steps = dt > 1 / 50 ? 2 : 1;
    for (let k = 0; k < steps; k++) step(s, dt / steps, ctx);
    if (s.cur && (s.cur.out <= 0 || ctx.t - s.cur.t > 1.6)) finish(s, ctx.t);
    // live cue for the lead: same scale and window as the grade (완벽/좋음 = 0.25–0.7)
    let rs = 0, rn = 0, up = 0, all = 0;
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] === 3) continue;
      all += s.n[i]; if (s.st[i] !== 0) up += s.n[i];
      if (s.st[i] === 0 && s.depth[i] < 0.35) { rs += Math.min(1.6, s.rest[i]) * s.n[i]; rn += s.n[i]; }
    }
    s.readiness = rn ? rs / rn : 0;
    // "now" only once the pile is back down (a few early landers don't make a cue)
    s.golden = up <= 0.1 * all && s.readiness >= READY[0] && s.readiness <= READY[2];
  }
  function step(s, dt, ctx) {
    const t = ctx.t, heat = ctx.heat || K.heat, tilt = ctx.tilt || { u: 0, v: 0 };
    // the pile keeps its shape and slides toward the low side; squeezes a little against the wall
    const tm = Math.min(1, Math.hypot(tilt.u, tilt.v)), sq = 1 - 0.3 * tm;
    const pk = Math.min(1, dt * 2.5);
    s.pc[0] += (tilt.u * 0.5 - s.pc[0]) * pk; s.pc[1] += (tilt.v * 0.5 - s.pc[1]) * pk;
    const air = RATE * AIR * dt, damp = Math.exp(-6 * dt);
    let lN = 0, lU = 0, lV = 0, sN = 0, sU = 0, sV = 0;
    for (let i = 0; i < s.M; i++) {
      const st = s.st[i];
      if (st === 0) {
        const hh = heat(s.u[i], s.v[i]);
        // a grain that just landed was cooled in the air: the sear builds back up over ~0.5 s of contact
        s.cook[i] += RATE * layer(s.depth[i]) * hh * (RAMP0 + (1 - RAMP0) * Math.min(1, s.rest[i] / 0.4)) * dt;
        s.rest[i] += (hh / HEAT_C) * dt;
        let gu = s.pc[0] + s.hu[i] * sq, gv = s.pc[1] + s.hv[i] * sq;
        const gr = Math.hypot(gu, gv); if (gr > 0.9) { gu *= 0.9 / gr; gv *= 0.9 / gr; }
        s.u[i] += s.su[i] * dt + (gu - s.u[i]) * Math.min(1, dt * 3);
        s.v[i] += s.sv[i] * dt + (gv - s.v[i]) * Math.min(1, dt * 3);
        s.su[i] *= damp; s.sv[i] *= damp;
        const r = Math.hypot(s.u[i], s.v[i]); if (r > 0.95) { s.u[i] *= 0.95 / r; s.v[i] *= 0.95 / r; }
        s.h[i] = groundH(s, i);
        s.rot[i] += (s.su[i] + s.sv[i]) * dt * 2;
      } else if (st === 1) {
        // the push slides the rice up the wall toward the lip (not a lerp: constant speed)
        s.u[i] += s.slU * s.sl[i] * dt; s.v[i] += s.slV * s.sl[i] * dt;
        const r = Math.hypot(s.u[i], s.v[i]); if (r > 0.93) { s.u[i] *= 0.93 / r; s.v[i] *= 0.93 / r; }
        s.h[i] = K.surf(s.u[i], s.v[i]) + 0.02 + 0.04 * s.depth[i];
        if (t >= s.tl[i]) s.st[i] = 2;
      } else if (st === 2) {
        s.cook[i] += air;
        const gg = Math.abs(s.vh[i]) < 0.8 ? G * 0.8 : G; // apex hang
        s.vh[i] -= gg * dt;
        s.u[i] += s.vu[i] * dt; s.v[i] += s.vv[i] * dt; s.h[i] += s.vh[i] * dt;
        s.rot[i] += s.spin[i] * dt;
        if (s.vh[i] < 0) {
          const r2 = s.u[i] * s.u[i] + s.v[i] * s.v[i];
          if (r2 <= 1) {
            const floor = K.surf(s.u[i], s.v[i]) + 0.06; // lands on the pile, not under it
            if (s.h[i] <= floor) {
              if (!s.hop[i] && Math.random() < 0.15) { s.hop[i] = 1; s.h[i] = floor; s.vh[i] = 0.4; s.vu[i] *= 0.5; s.vv[i] *= 0.5; continue; }
              land(s, i, sq);
              lN += s.n[i]; lU += s.u[i] * s.n[i]; lV += s.v[i] * s.n[i];
            }
          } else if (s.h[i] < K.RIM_H) {
            // over the rim: onto the counter
            s.st[i] = 3; s.vh[i] = Math.min(s.vh[i], -1);
            const r = Math.sqrt(r2); s.vu[i] += (s.u[i] / r) * 0.6; s.vv[i] += (s.v[i] / r) * 0.6;
            if (s.toss[i] === (s.cur && s.cur.id)) { s.cur.spill += s.n[i]; s.cur.out -= s.n[i]; }
            else lateSpill(s, s.toss[i], s.n[i]);
            sN += s.n[i]; sU += s.u[i] * s.n[i]; sV += s.v[i] * s.n[i];
          }
        }
      } else if (s.h[i] > -0.32) {
        // spilled, still falling to the counter: scatters out in front of the wok
        s.vh[i] -= G * dt; s.u[i] += s.vu[i] * dt; s.v[i] += s.vv[i] * dt; s.h[i] += s.vh[i] * dt; s.rot[i] += s.spin[i] * dt;
        if (s.h[i] <= -0.32) { s.h[i] = -0.32; s.vu[i] = s.vv[i] = s.vh[i] = 0; }
      }
    }
    if (lN) s.events.push({ type: "land", u: lU / lN, v: lV / lN, n: lN });
    if (sN) s.events.push({ type: "spill", u: sU / sN, v: sV / sN, n: sN });
    if (s.events.length > 60) s.events.splice(0, s.events.length - 60); // nobody drains: don't grow forever
  }
  function land(s, i, sq) {
    s.st[i] = 0;
    // depth flips: the seared bottom layer comes out on top (wave <-> burn mechanic)
    s.depth[i] = clamp(1 - s.depth[i] + (Math.random() - 0.5) * 0.2, 0, 1);
    s.rest[i] = 0;
    s.su[i] = s.vu[i] * 0.5; s.sv[i] = s.vv[i] * 0.5; // inelastic: keep half the tangential speed, slide home
    // new slot anywhere in the full pile disc, on the side it landed on (keeps the overturn) 70% of the time
    const a = Math.random() * TAU, r = Math.sqrt(Math.random()) * PILE;
    let hu = Math.cos(a) * r, hv = Math.sin(a) * r;
    if (Math.random() < 0.7) {
      if ((hu > 0) !== (s.u[i] > s.pc[0])) hu = -hu;
      if ((hv > 0) !== (s.v[i] > s.pc[1])) hv = -hv;
    }
    s.hu[i] = hu; s.hv[i] = hv;
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
  function airborne(s) { let n = 0; for (let i = 0; i < s.M; i++) if (s.st[i] === 2) n += s.n[i]; return n; }
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
      const rx0 = 0.0315 * R * sc, ry0 = 0.0154 * R * sc; // art §2 capsule 0.045R x 0.022R, x1.4 so 220 grains fill the 0.58 pile
      c.lineCap = "round";
      const m = sh.m;
      for (let k = 0; k < m.length; k++) {
        const mm = m[k], ck = qi(s.cook[i] + mm.j);
        // in the air the clump stretches along its velocity (volume kept)
        let ox = mm.ox * R * sc, oy = mm.oy * R * sc, rot = mm.rot + s.rot[i] * 0.3, rx = rx0 * mm.sz, ry = ry0 * mm.sz;
        if (sStr > 1) {
          const ca = Math.cos(sAng), sa = Math.sin(sAng), along = (ox * ca + oy * sa) * (sStr * 1.6 - 0.6);
          const perp = (-ox * sa + oy * ca) / sStr;
          ox = along * ca - perp * sa; oy = along * sa + perp * ca;
          rot = sAng + (mm.rot - 1.5) * 0.5; rx *= sStr; ry /= Math.sqrt(sStr);
        }
        const gx = x + ox, gy = y + oy, hl = Math.max(0, rx - ry), ca = Math.cos(rot) * hl, sa = Math.sin(rot) * hl;
        // plain capsule: shade underneath (offset lower-right), base on top, spec dot upper-left
        c.strokeStyle = L.shade[ck]; c.lineWidth = ry * 2;
        c.beginPath(); c.moveTo(gx - ca + ry * 0.25, gy - sa + ry * 0.35); c.lineTo(gx + ca + ry * 0.25, gy + sa + ry * 0.35); c.stroke();
        c.strokeStyle = lit ? L.lit[ck] : L.base[ck]; c.lineWidth = ry * 1.7;
        c.beginPath(); c.moveTo(gx - ca, gy - sa); c.lineTo(gx + ca, gy + sa); c.stroke();
        const gl = L.gl[ck];
        if (gl > 0.05) { c.fillStyle = `rgba(255,246,232,${(gl * 1.4).toFixed(2)})`; c.beginPath(); c.arc(gx - rx * 0.35, gy - ry * 0.45, Math.max(0.7, ry * 0.4), 0, TAU); c.fill(); }
      }
    } else if (kind === 1) {
      // torn kimchi leaf: uneven quad, lit edge top-left, pale rib on some; shrinks as it cooks
      const z = 0.056 * R * sc * (1 - 0.15 * Math.min(1, s.cook[i])) * (0.85 + 0.3 * sh.q), a = s.rot[i], ca = Math.cos(a), sa = Math.sin(a), qq = sh.q;
      const px = [-1, -0.2, 0.3 + qq * 0.6, 1, 0.2, -0.3 - qq * 0.5], py = [-0.4, -0.75 + qq * 0.2, -0.85, 0.2 + (1 - qq) * 0.3, 0.7, 0.8];
      c.fillStyle = lit ? L.lit[q] : L.base[q]; c.beginPath();
      for (let k = 0; k < 6; k++) { const X = x + (px[k] * ca - py[k] * sa) * z, Y = y + (px[k] * sa + py[k] * ca) * z * 0.85; k ? c.lineTo(X, Y) : c.moveTo(X, Y); }
      c.closePath(); c.fill();
      c.strokeStyle = s.cook[i] < 1.2 ? "rgba(255,106,61,.9)" : "rgba(120,50,30,.6)"; c.lineWidth = 1;
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

  function draw(c, s, vw, pose) {
    const R = vw.R, M = s.M, ord = s.ord, key = s.key, sn = Math.sin(K.THETA), cs = Math.cos(K.THETA);
    // sauce bed under the pile: one path, uniform alpha (clump shadow, kills the confetti read)
    c.fillStyle = "rgba(120,40,18,.12)"; c.beginPath();
    for (let i = 0; i < M; i++) {
      if (s.st[i] > 1 || s.kind[i]) continue; // one soft shadow per rice clump
      P(vw, pose, s.u[i], s.v[i], s.h[i] - 0.02 - 0.06 * s.depth[i]);
      const rr = 0.06 * R;
      c.moveTo(PX + rr + 2, PY + 2); c.ellipse(PX + 2, PY + 2, rr, rr * 0.7, 0, 0, TAU);
    }
    c.fill();
    // airborne clumps cast a shadow on the floor; it shrinks and fades with height
    c.fillStyle = "rgba(20,10,6,.1)"; c.beginPath();
    for (let i = 0; i < M; i++) {
      if (s.st[i] !== 2 || s.u[i] * s.u[i] + s.v[i] * s.v[i] > 0.9) continue;
      const fl = K.surf(s.u[i], s.v[i]), up = Math.max(0, s.h[i] - fl), k = Math.max(0.2, 1 - up * 0.8);
      P(vw, pose, s.u[i], s.v[i], fl);
      const rr = 0.06 * R * k; c.moveTo(PX + rr, PY); c.ellipse(PX, PY, rr, rr * 0.6, 0, 0, TAU);
    }
    c.fill();
    // depth sort toward the camera; the order barely changes frame to frame -> insertion sort is ~O(n)
    for (let i = 0; i < M; i++) key[i] = -sn * s.v[i] + cs * s.h[i];
    for (let i = 1; i < M; i++) {
      const o = ord[i], kv = key[o]; let j = i - 1;
      while (j >= 0 && key[ord[j]] > kv) { ord[j + 1] = ord[j]; j--; }
      ord[j + 1] = o;
    }
    for (let n = 0; n < M; n++) {
      const i = ord[n], st = s.st[i];
      if (st === 3 && s.h[i] <= -0.32) continue; // on the counter: drawSpilled
      P(vw, pose, s.u[i], s.v[i], s.h[i]);
      const sc = K.scaleAt(s.h[i] - K.surf(s.u[i], s.v[i]));
      let sAng = 0, sStr = 1;
      if (st >= 2) {
        const x0 = PX, y0 = PY;
        P(vw, pose, s.u[i] + s.vu[i] * 0.03, s.v[i] + s.vv[i] * 0.03, s.h[i] + s.vh[i] * 0.03);
        const dx = PX - x0, dy = PY - y0, sp = Math.hypot(s.vu[i], s.vv[i], s.vh[i]);
        sAng = Math.atan2(dy, dx); sStr = 1 + 0.25 * Math.min(1, sp / 5);
        PX = x0; PY = y0;
      }
      drawUnit(c, s, i, PX, PY, R, sc, st >= 2, sAng, sStr);
    }
  }

  function drawSpilled(c, s, vw, pose) {
    const R = vw.R, rest = K.restPose();
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] !== 3 || s.h[i] > -0.32) continue;
      P(vw, rest, s.u[i], s.v[i], s.h[i]); // on the counter: it doesn't move with the wok
      c.fillStyle = "rgba(30,40,50,.18)"; c.beginPath(); c.ellipse(PX + 1.5, PY + 2, 0.05 * R, 0.025 * R, 0, 0, TAU); c.fill();
      drawUnit(c, s, i, PX, PY, R, 1, false, 0, 1);
    }
  }

  /* bowl on the result slip: a heaped mound of what was kept, tasty base, burnt grains as dark specks */
  function drawBowl(c, s, cx, cy, r, ct) {
    let kept = 0, sum = 0, sn = 0;
    for (let i = 0; i < s.M; i++) if (s.st[i] !== 3) { kept += s.n[i]; if (s.kind[i] === 0) { sum += clamp(s.cook[i], 0.65, 1.15) * s.n[i]; sn += s.n[i]; } }
    const share = kept / s.total, mh = r * (0.2 + 0.32 * share), mr = r * (0.55 + 0.37 * share);
    const base = K.col.rice(sn ? sum / sn : 0.9);
    const g = c.createRadialGradient(cx - r * 0.2, cy - mh, r * 0.1, cx, cy - mh * 0.3, mr * 1.1);
    g.addColorStop(0, K.rgb([Math.min(255, base[0] + 30), Math.min(255, base[1] + 30), Math.min(255, base[2] + 25)]));
    g.addColorStop(1, K.rgb([base[0] * 0.7, base[1] * 0.7, base[2] * 0.7]));
    c.fillStyle = g; c.beginPath(); c.ellipse(cx, cy, mr, mr * ct, 0, 0, Math.PI);
    c.bezierCurveTo(cx - mr, cy - mh * 1.45, cx + mr, cy - mh * 1.45, cx + mr, cy); c.fill();
    // every kept unit placed on the mound (deterministic), far ones first
    const pts = [];
    for (let i = 0; i < s.M; i++) {
      if (s.st[i] === 3) continue;
      for (let rep = 0; rep < 3; rep++) { const a = hash(i * 3 + rep) * TAU, d = Math.sqrt(hash(i * 7 + rep + 0.5)) * 0.9; pts.push([i, a, d]); }
    }
    pts.sort((p1, p2) => Math.sin(p1[1]) * p1[2] - Math.sin(p2[1]) * p2[2]);
    const fakeR = r * 1.25; // grains a bit finer than in the wok: the bowl reads as a heap, not stickers
    for (const [i, a, d] of pts) {
      const lift = (1 - d * d) * mh * 1.05, x = cx + Math.cos(a) * d * mr, y = cy + Math.sin(a) * d * mr * ct - lift;
      const ck = s.cook[i]; if (ck > 1.5) s.cook[i] = 1.5; // burnt reads as #5A2A14 specks, not black flies
      drawUnit(c, s, i, x, y, fakeR, 1, false, 0, 1); s.cook[i] = ck;
    }
    return cy - mh;
  }

  K.Rice = { create, toss, update, draw, drawSpilled, drawBowl, stats, airborne, contactShare, hotspots };
})();
