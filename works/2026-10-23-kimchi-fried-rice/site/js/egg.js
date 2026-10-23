/* egg.js — soft-body fried egg: cook, slide, toss/flip, fold/tear/burst, draw (see CONTRACT.md) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  const TAU = Math.PI * 2, PI = Math.PI, clamp = K.clamp, lerp = K.lerp;
  const ER = 0.36;                 // egg radius, world units
  const N = 3, SP = ER / N;        // 7x7 grid masked to a disk -> 37 particles
  const DT = 1 / 120, ITER = 6;    // fixed-step PBD (anim §2)
  const RATE = 0.25;               // bottom cook per s at heat 1: good window .6-1.4 ≈ 2-5 s at the centre
  const CATCH = 0.62;
  const WOK = [56, 64, 76];        // what shows through raw white
  const LT = (() => { const l = [-0.35, 0.45, 0.82], m = Math.hypot(...l); return l.map((x) => x / m); })();
  const AIRPOSE = { du: 0, dv: 0, lift: 0, lean: 0 };

  const mix = (a, b, k) => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
  const rgbS = (c, s) => `rgb(${(c[0] * s) | 0},${(c[1] * s) | 0},${(c[2] * s) | 0})`;
  const noise = (n) => Array.from({ length: n }, () => Math.random());
  const fz = (x) => (1 - Math.exp(-0.8 * x)) / (1 - Math.exp(-0.8)); // spin slows a little in the air (drag)

  /* ---------- build ---------- */
  function create() {
    const P = [], idx = {}, at = (i, j) => idx[i + "," + j];
    for (let i = -N; i <= N; i++) for (let j = -N; j <= N; j++) {
      if (i * i + j * j > 10.5) continue;
      const a = i * SP, b = j * SP, h = K.surf(a, b);
      idx[i + "," + j] = P.length;
      P.push({ u: a, v: b, h, pu: a, pv: b, ph: h, a, b, rim: Math.hypot(a, b) / ER, cb: 0, ct: 0, nz: Math.random(),
        layer: 0, e: 0, d: 0, n: 0, th: 0, thT: 0, side: 0 });
    }
    const C = [], T = [];
    const add = (p, q, type) => { if (p !== undefined && q !== undefined) C.push({ p, q, L: Math.hypot(P[p].a - P[q].a, P[p].b - P[q].b), type, cut: false }); };
    for (let i = -N - 1; i <= N; i++) for (let j = -N - 1; j <= N; j++) {
      const p = at(i, j), r = at(i + 1, j), u = at(i, j + 1), q = at(i + 1, j + 1);
      if (p !== undefined) { add(p, r, 0); add(p, u, 0); add(p, q, 1); add(p, at(i + 2, j), 2); add(p, at(i, j + 2), 2); }
      add(r, u, 1);
      // triangles, all counter-clockwise in (u, v): + normal = the top (yolk) face
      const n = [p, r, q, u].filter((x) => x !== undefined).length;
      if (n === 4) { T.push([p, r, q], [p, q, u]); }
      else if (n === 3) T.push([p, r, q, u].filter((x) => x !== undefined));
    }
    const ring = P.map((p, i) => i).filter((i) => {
      const a = Math.round(P[i].a / SP), b = Math.round(P[i].b / SP);
      return [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([x, y]) => at(a + x, b + y) === undefined);
    }).sort((i, j) => Math.atan2(P[i].b, P[i].a) - Math.atan2(P[j].b, P[j].a));
    const interior = P.map((p, i) => i).filter((i) => P[i].rim < 0.75 && P[i].rim > 0.2);
    return {
      P, C, T: T.map((v) => ({ v, cut: false })), ring, centre: at(0, 0),
      phase: "pan", t: 0, acc: 0, age: 0, calm: 0, u: 0, v: 0, su: 0, sv: 0, cookBottom: 0, cookTop: 0, cookAtToss: null,
      du: 0, dv: 0, tilt: { u: 0, v: 0 }, heat: K.heat, rot: 0, res: null, landedNow: false, done: false, stuck: false,
      sqT0: -9, yolk: { x: 0, y: 0, vx: 0, vy: 0, lu: 0, lv: 0 }, drops: [], air: null,
      lace: noise(40), blob: noise(16),
      craters: Array.from({ length: 13 }, () => ({ p: interior[(Math.random() * interior.length) | 0], ox: Math.random() - 0.5, oy: Math.random() - 0.5, s: 0.6 + Math.random() * 0.8 })),
    };
  }

  /* ---------- PBD step (in the pan, wok-relative coordinates) ---------- */
  function floorOf(p) { return K.surf(p.u, p.v) + (p.layer ? 0.03 : 0); }
  function collide(p) {
    const r = Math.hypot(p.u, p.v);
    if (r > 0.97) { p.u *= 0.97 / r; p.v *= 0.97 / r; }
    const f = floorOf(p);
    if (p.h < f) {
      // push out along the bowl normal so gravity slides food downhill
      const [gu, gv] = K.slope(p.u, p.v), l2 = 1 + gu * gu + gv * gv, pen = (f - p.h) / l2;
      p.u -= gu * pen; p.v -= gv * pen; p.h += pen;
      p.h = Math.max(p.h, floorOf(p));
    }
  }
  function pbd(s, h) {
    const P = s.P, tl = s.tilt;
    const gu = K.G * Math.sin(tl.u * 0.35), gv = K.G * Math.sin(tl.v * 0.35), h2 = h * h;
    for (const p of P) {
      const vu = (p.u - p.pu) * 0.996, vv = (p.v - p.pv) * 0.996, vh = (p.h - p.ph) * 0.996;
      p.pu = p.u; p.pv = p.v; p.ph = p.h;
      p.u += vu + gu * h2; p.v += vv + gv * h2; p.h += vh - K.G * h2;
    }
    const kb = 0.08 + 0.42 * clamp(s.cookBottom, 0, 1), ks = [0.95, 0.6, kb];
    for (let it = 0; it < ITER; it++) {
      for (const c of s.C) {
        if (c.cut) continue;
        const a = P[c.p], b = P[c.q], du = b.u - a.u, dv = b.v - a.v, dh = b.h - a.h, l = Math.hypot(du, dv, dh) || 1e-6;
        const k = ((l - c.L) / l) * ks[c.type] * 0.5;
        a.u += du * k; a.v += dv * k; a.h += dh * k; b.u -= du * k; b.v -= dv * k; b.h -= dh * k;
      }
      for (const p of P) collide(p);
    }
    const sticky = s.phase === "pan" && s.cookBottom < 0.45, damp = 1 - (s.res && s.res.torn ? 5 : 1.5) * h; // torn pieces stay apart
    for (const p of P) {
      if (p.h > floorOf(p) + 1e-4) continue;
      if (sticky) { p.u = p.pu; p.v = p.pv; p.h = floorOf(p); continue; }
      let vu = (p.u - p.pu) * damp, vv = (p.v - p.pv) * damp;
      const r = Math.hypot(p.u, p.v);
      if (r > 0.965) { // soft bounce off the wall
        const nu = p.u / r, nv = p.v / r, vr = vu * nu + vv * nv;
        if (vr > 0) { vu -= 1.35 * vr * nu; vv -= 1.35 * vr * nv; }
      }
      p.pu = p.u - vu; p.pv = p.v - vv;
    }
  }
  function centroid(s) {
    let u = 0, v = 0, h = 0;
    for (const p of s.P) { u += p.u; v += p.v; h += p.h; }
    const n = s.P.length; return [u / n, v / n, h / n];
  }

  /* ---------- toss: the flight is kinematic so the angle shown is the angle scored ---------- */
  function toss(s, f, t) {
    if (s.phase !== "pan" || s.done) return;
    const st = clamp(f.strength || 1, 0.3, 1.6), snap = clamp(f.snap == null ? 0.5 : f.snap, 0, 1), dirU = f.dirU || 0;
    s.cookAtToss = s.cookBottom;
    const [cu, cv, ch] = centroid(s), tl = s.tilt;
    let Du = 0.5 * dirU, Dv = 1; const m = Math.hypot(Du, Dv); Du /= m; Dv /= m;
    const A = {
      t: 0, st, snap, Du, Dv, Au: Dv, Av: -Du,
      u0: cu + s.du, v0: cv + s.dv, h0: ch,
      vu: s.su + 0.45 * tl.u + 0.25 * dirU, vv: s.sv + 0.45 * tl.v,
      vh: 4.2 * st, rot: (PI * st * (0.6 + 0.6 * snap)) / 0.864,
      soft: 1 - 0.6 * clamp(s.cookBottom / 1.2, 0, 1), splat: st > 1.4 && f.splatOK === true, decided: false, miss: false, fell: false, T: 1,
    };
    for (const p of s.P) {
      const ru = p.u - cu, rv = p.v - cv;
      p.e = ru * A.Au + rv * A.Av; p.d = ru * A.Du + rv * A.Dv; p.n = p.h - ch; p.layer = 0;
    }
    for (let tt = 0.05; tt < 3; tt += 1 / 240) { // predict touchdown for the spin curve
      if (A.vh - K.G * tt > 0) continue;
      const ru = A.u0 + A.vu * tt - s.du, rv = A.v0 + A.vv * tt - s.dv;
      if (A.h0 + A.vh * tt - 0.5 * K.G * tt * tt <= K.surf(ru, rv) + 0.02) { A.T = tt; break; }
    }
    s.air = A; s.phase = "air"; s.tossT = t;
  }
  function kin(s, t) {
    const A = s.air, x = t / A.T, th = A.rot * fz(x), sw = Math.sin(PI * Math.min(1, x)), soft = A.soft;
    const cu = A.u0 + A.vu * t, cv = A.v0 + A.vv * t, ch = A.h0 + A.vh * t - 0.5 * K.G * t * t, vhn = A.vh - K.G * t;
    // too strong: the egg shudders at the apex (the burst warning), ~±.12 s around it
    const ta = (t - A.vh / K.G) / 0.12, shud = A.st > 1.1 ? clamp((A.st - 1.1) / 0.3, 0.4, 1) * Math.exp(-ta * ta) : 0;
    for (const p of s.P) {
      // trailing edge (d < 0) lags the spin, edges cup against the air and flutter
      const lag = 0.6 * soft * sw * Math.pow(clamp(0.25 - p.d / ER, 0, 1.25), 1.4);
      const ti = th - lag, c = Math.cos(ti), sn = Math.sin(ti), rr = (p.e * p.e + p.d * p.d) / (ER * ER);
      const n = p.n * (1 - Math.min(1, t * 6)) - 0.03 * soft * rr * clamp(vhn * c, -6, 6) / 4 + 0.014 * soft * rr * Math.sin(17 * t + 6 * p.nz) * sw
        + shud * 0.05 * rr * Math.sin(55 * t + 9 * p.nz) + shud * 0.03 * Math.sin(41 * t) * p.d / ER;
      p.th = ti;
      p.u = cu + p.e * A.Au + (p.d * c - n * sn) * A.Du;
      p.v = cv + p.e * A.Av + (p.d * c - n * sn) * A.Dv;
      p.h = ch + p.d * sn + n * c;
    }
    s.rot = th;
    return [cu, cv, ch, vhn];
  }

  /* ---------- landing: decide the outcome, then show it physically ---------- */
  function land(s, ru, rv, ch, vhn) {
    const A = s.air, cook = s.cookAtToss, rot = s.rot;
    const r = ((rot % TAU) + TAU) % TAU, d = Math.abs(r - PI), err = (d * 180) / PI;
    const flipped = err <= 45, folded = !flipped && d <= PI - 0.8;
    const impact = -vhn;
    const vTear = 5 - 3 * Math.max(0, 1 - cook) - 1.5 * Math.max(0, cook - 1.4);
    const torn = impact > vTear;
    let burst = A.st > 1.2 || impact > 5.3 - (cook < 0.8 ? 0.7 : 0) - (folded ? 0.5 : 0);
    s.res = { caught: true, flipped, folded, torn, burst, rot, angleErr: Math.round(err) * (rot > PI ? 1 : -1), angle: Math.round((rot * 180) / PI), impact, strength: A.st,
      grade: flipped ? (err <= 12 ? "완벽" : "좋음") : folded ? "접힘" : "날면", u: ru, v: rv };
    const base = Math.round(rot / PI) * PI;
    for (const p of s.P) {
      p.thT = base;
      if (folded && !torn && Math.sign(p.d) * Math.sin(rot) > 0) { p.thT = base + (rot > base ? PI : -PI); p.layer = 1; }
    }
    A.land = [ru, rv, ch]; A.lt = 0; s.phase = "land"; s.sqT0 = s.t; s.landedNow = true; s.u = ru; s.v = rv;
  }
  function settleTween(s, dt) {
    const A = s.air; A.lt += dt;
    const k = K.smooth(Math.min(1, A.lt / 0.14));
    const cu = A.land[0] + A.vu * 0.3 * A.lt, cv = A.land[1] + A.vv * 0.3 * A.lt, ch = lerp(A.land[2], K.surf(cu, cv) + 0.012, k);
    for (const p of s.P) {
      const ti = lerp(p.th, p.thT, k), c = Math.cos(ti), sn = Math.sin(ti), n = p.n * (1 - k) * 0.3 + (p.layer ? 0.03 : 0) * k;
      p.u = cu + p.e * A.Au + (p.d * c - n * sn) * A.Du;
      p.v = cv + p.e * A.Av + (p.d * c - n * sn) * A.Dv;
      p.h = Math.max(ch + p.d * sn + n * c, floorOf(p));
    }
    s.u = cu; s.v = cv;
    if (k < 1) return;
    // hand over to PBD: the landed shape becomes the rest shape (keeps a fold folded)
    for (const p of s.P) { p.pu = p.u - A.vu * 0.3 * DT; p.pv = p.v - A.vv * 0.3 * DT; p.ph = p.h; }
    for (const c of s.C) { const a = s.P[c.p], b = s.P[c.q]; c.L = Math.hypot(a.u - b.u, a.v - b.v, a.h - b.h); }
    const res = s.res, yc = s.P[s.centre];
    if (res.torn) {
      const ang = Math.random() * PI, nu = Math.cos(ang), nv = Math.sin(ang), off = (Math.random() < 0.5 ? -1 : 1) * K.rnd(0.06, 0.16);
      const sd = (p) => (p.u - cu) * nu + (p.v - cv) * nv - off;
      for (const c of s.C) if (sd(s.P[c.p]) * sd(s.P[c.q]) < 0) c.cut = true;
      for (const t of s.T) { const ds = t.v.map((i) => sd(s.P[i])); if (Math.min(...ds) < 0 && Math.max(...ds) > 0) t.cut = true; }
      for (const p of s.P) { const sg = Math.sign(sd(p)) || 1; p.u += nu * sg * 0.04; p.v += nv * sg * 0.04; p.pu = p.u - nu * sg * 0.35 * DT; p.pv = p.v - nv * sg * 0.35 * DT; }
      if (Math.abs(sd(yc)) < 0.07) res.burst = true; // the tear ran through the yolk
    }
    if (res.burst) {
      const topUp = !res.flipped;
      for (let i = 0; i < 9; i++) {
        const src = topUp ? yc : s.P[s.ring[(Math.random() * s.ring.length) | 0]], a = Math.random() * TAU, sp = K.rnd(0.15, 0.6);
        s.drops.push({ u: src.u + Math.cos(a) * 0.03, v: src.v + Math.sin(a) * 0.03, vu: Math.cos(a) * sp, vv: Math.sin(a) * sp, r: K.rnd(0.035, 0.065), on: topUp });
      }
    }
    s.phase = "landed"; s.age = 0; s.calm = 0;
  }

  /* ---------- update ---------- */
  function update(s, dt, ctx) {
    s.landedNow = false; s.t += dt;
    const pose = ctx.wokPose || {};
    s.du = pose.du || 0; s.dv = pose.dv || 0; s.tilt = ctx.tilt || { u: 0, v: 0 }; s.heat = ctx.heat || K.heat;
    if (s.phase === "pan" || s.phase === "landed") {
      s.acc = Math.min(s.acc + dt, 4 * DT);
      while (s.acc >= DT) { pbd(s, DT); s.acc -= DT; }
      // cooking: the face on the metal sears, the other side barely warms
      const down = s.res && s.res.flipped ? "ct" : "cb", up = down === "cb" ? "ct" : "cb";
      let cb = 0, ct = 0, vmax = 0;
      for (const p of s.P) {
        const hot = s.heat(p.u, p.v) * (p.h - floorOf(p) < 0.01 ? 1 : 0.3);
        if (s.phase === "pan" || down === "ct") p[down] += hot * RATE * dt;
        p[up] += hot * RATE * 0.22 * dt;
        cb += p.cb; ct += p.ct;
        vmax = Math.max(vmax, Math.hypot(p.u - p.pu, p.v - p.pv) / DT);
      }
      if (s.phase === "pan") s.cookBottom = cb / s.P.length;
      s.cookTop = ct / s.P.length;
      const [cu, cv] = centroid(s), y = s.yolk;
      s.su = (cu - s.u) / dt; s.sv = (cv - s.v) / dt; s.u = cu; s.v = cv;
      // yolk wobble: 4 Hz, ζ .2, driven by the egg's acceleration
      const w = TAU * 4, au = (s.su - y.lu) / dt, av = (s.sv - y.lv) / dt; y.lu = s.su; y.lv = s.sv;
      y.vx += (-w * w * y.x - 2 * 0.2 * w * y.vx - clamp(au, -30, 30) * 0.4) * dt; y.x += y.vx * dt;
      y.vy += (-w * w * y.y - 2 * 0.2 * w * y.vy - clamp(av, -30, 30) * 0.4) * dt; y.y += y.vy * dt;
      if (s.phase === "pan" && s.cookBottom >= 2.4) { s.stuck = true; s.done = true; }
      if (s.phase === "landed") {
        s.age += dt; s.calm = vmax < 0.05 ? s.calm + dt : 0;
        if ((s.calm > 0.2 && s.age > 0.6) || s.age > 1.3) s.done = true;
      }
    } else if (s.phase === "air") {
      const A = s.air; A.t += dt;
      const [cu, cv, ch, vhn] = kin(s, A.t), ru = cu - s.du, rv = cv - s.dv;
      s.u = cu; s.v = cv; s.su = A.vu; s.sv = A.vv;
      if (A.splat) {
        if (A.t > 0.26) { s.phase = "splatFly"; s.age = 0; s.res = { caught: false, splat: true, burst: true, rot: s.rot, impact: 9, u: cu, v: cv, angleErr: null, strength: A.st }; }
      } else if (vhn < 0) {
        if (!A.decided && ch < K.RIM_H + 0.02) { A.decided = true; A.miss = Math.hypot(ru, rv) > CATCH; }
        if (A.miss) {
          if (!A.fell && ch < -0.05) { A.fell = true; s.landedNow = true; s.res = { caught: false, rot: s.rot, impact: -vhn, u: ru, v: rv, angleErr: null, strength: A.st }; }
          if (ch < -1.6 || A.t > A.T + 1.2) { s.done = true; if (!s.res) s.res = { caught: false, rot: s.rot }; }
        } else if (A.decided && ch <= K.surf(ru, rv) + 0.02) land(s, ru, rv, ch, vhn);
      }
    } else if (s.phase === "land") settleTween(s, dt);
    else if (s.phase === "splatFly") {
      s.age += dt; kin(s, s.air.t += dt);
      if (s.age > 0.22) { s.phase = "splat"; s.age = 0; s.landedNow = true; s.splatX = K.rnd(-0.08, 0.08); }
    } else if (s.phase === "splat") { s.age += dt; if (s.age > 1.5) s.done = true; }
    // burst yolk runs downhill (toward the wok centre) and with the tilt
    for (const d of s.drops) {
      const [gu, gv] = K.slope(d.u, d.v);
      d.vu += (-gu * 3 + s.tilt.u * 2) * dt; d.vv += (-gv * 3 + s.tilt.v * 2) * dt;
      d.vu *= 1 - 2.5 * dt; d.vv *= 1 - 2.5 * dt; d.u += d.vu * dt; d.v += d.vv * dt;
      if (d.r < 0.09) d.r += 0.03 * dt; // spreads as it runs
    }
  }

  /* ---------- draw ---------- */
  function setOf(p) { return clamp((p.cb + 2 * p.ct) * (0.7 + 0.6 * p.rim) / 0.8, 0, 1); }
  const X = [], Y = [], TL = [];
  function draw(c, s, vw, pose) {
    if (s.phase === "splat" || s.phase === "splatFly") return drawSplat(c, s, vw, pose);
    const abs = s.phase === "air" || s.phase === "splatFly", pp = abs ? AIRPOSE : pose, P = s.P, R = vw.R;
    const [cu, cv, ch] = centroid(s);
    // shadow on the wok floor while airborne: the catch cue
    if (abs && !(s.air && s.air.miss)) {
      const ru = cu - s.du, rv = cv - s.dv;
      if (Math.hypot(ru, rv) < 1) {
        const fh = K.surf(ru, rv), k = clamp((ch - fh) / 1.4, 0, 1), [x, y] = K.proj(vw, pose, ru, rv, fh);
        const sr = ER * 0.8 * R * (1 - 0.35 * k), g = c.createRadialGradient(0, 0, 0, 0, 0, sr);
        g.addColorStop(0, `rgba(8,12,18,${0.18 * (1 - 0.5 * k)})`); g.addColorStop(0.55, `rgba(8,12,18,${0.12 * (1 - 0.5 * k)})`); g.addColorStop(1, "rgba(8,12,18,0)");
        c.save(); c.translate(x, y); c.scale(1, vw.ct); c.fillStyle = g; c.beginPath(); c.arc(0, 0, sr, 0, TAU); c.fill(); c.restore();
      }
    }
    let [ox, oy] = K.proj(vw, pp, cu, cv, ch);
    let sc = abs ? K.scaleAt(Math.max(0, ch - 0.05)) : 1, tx = ox, ty = oy;
    if (s.phase === "splatFly") { const k = s.age / 0.22; sc *= 1 + 3.2 * k * k; tx = lerp(ox, vw.W * 0.5, k); ty = lerp(oy, vw.H * 0.34, k); }
    const ta = s.t - s.sqT0, sq = ta < 0.6 ? 1 - 0.25 * Math.exp(-0.35 * 50 * ta) * Math.cos(47 * ta) : 1;
    const jig = s.phase === "pan" && s.cookBottom >= 0.6 && s.cookBottom <= 1.4, rip = s.phase === "landed" ? Math.exp(-6 * s.age) : 0;
    for (let i = 0; i < P.length; i++) {
      const p = P[i], q = K.proj(vw, pp, p.u, p.v, p.h);
      let x = tx + (q[0] - ox) * sc / Math.sqrt(sq), y = ty + (q[1] - oy) * sc * sq;
      if (jig) y += Math.sin(TAU * 2 * s.t + 5 * p.nz) * p.rim * p.rim * 1.2;
      if (rip) y += Math.sin(30 * s.age + 7 * p.nz) * 3 * rip * p.rim * p.rim * p.rim;
      X[i] = x; Y[i] = y;
    }
    // which face looks at us (signed screen area of the rim ring; counter-clockwise world reads negative on screen)
    let area = 0;
    for (let k = 0; k < s.ring.length; k++) { const i = s.ring[k], j = s.ring[(k + 1) % s.ring.length]; area += X[i] * Y[j] - X[j] * Y[i]; }
    const topUp = area < 0, flat = PI * (ER * R * sc) ** 2 * vw.ct, vis = clamp(Math.abs(area / 2) / flat, 0, 1.3);
    const res = s.res || {}, folded = !!res.folded && !res.torn && s.phase === "landed", torn = !!res.torn && s.phase === "landed";
    c.save();
    if (abs && s.air.miss) c.globalAlpha = clamp(1 + ch / 1.2, 0, 1);
    const cbm = s.phase === "pan" ? s.cookBottom : s.cookAtToss || s.cookBottom;
    // contact shadow + lace skirt (crispy brown edge) while the yolk face is up and flat
    if (!abs && !folded && !torn) { ringPath(c, s, X, Y, tx, ty, 1.02, 0, 2.5, 3); c.fillStyle = "rgba(10,14,20,.28)"; c.fill(); }
    if (topUp && !folded && vis > 0.3) {
      const la = clamp((cbm - 0.5) / 0.3, 0, 1);
      if (la > 0 && !torn) {
        ringPath(c, s, X, Y, tx, ty, 1.06, 0.07, 0, 0);
        c.fillStyle = K.rgb(K.col.eggEdge(cbm), la); c.fill();
        c.strokeStyle = K.rgb(K.col.eggEdge(cbm + 0.35), la * 0.8); c.lineWidth = 1.2; c.stroke();
      }
    }
    const smoothBody = !folded && !torn;
    if (smoothBody) {
      // seen edge-on: a side band gives the white its thickness
      if (abs && vis < 0.6) { ringPath(c, s, X, Y, tx, ty, 1, 0, 0, 0.04 * R * sc * (1 - vis / 0.6)); c.fillStyle = "#EADFC8"; c.fill(); }
      if (topUp) { // the white: one smooth shape, clear (raw) -> #FFFDF6 with an #EADFC8 shadow toward the rim
        let sc0 = 0, n0 = 0, sr = 0, nr = 0, rad = 1;
        for (const p of P) { if (p.rim < 0.45) { sc0 += setOf(p); n0++; } else if (p.rim > 0.8) { sr += setOf(p); nr++; } }
        sc0 /= n0; sr /= nr;
        const xc = X[s.centre], yc = Y[s.centre];
        for (const i of s.ring) rad = Math.max(rad, Math.hypot(X[i] - xc, Y[i] - yc));
        const g = c.createRadialGradient(xc - 0.18 * rad, yc - 0.2 * rad, 0, xc, yc, rad * 1.05);
        const wc = (k, a, sh) => `rgba(${lerp(255, lerp(255, 234, sh), k) | 0},${lerp(255, lerp(253, 223, sh), k) | 0},${lerp(255, lerp(246, 200, sh), k) | 0},${0.35 + 0.65 * a})`;
        g.addColorStop(0, wc(sc0, sc0, 0)); g.addColorStop(0.6, wc((sc0 + sr) / 2, (sc0 + sr) / 2, 0.15)); g.addColorStop(1, wc(sr, sr, 1));
        ringPath(c, s, X, Y, tx, ty, 1, 0, 0, 0); c.fillStyle = g; c.fill();
      } else {
        let cb = 0; for (const p of P) cb += p.cb; cb /= P.length;
        ringPath(c, s, X, Y, tx, ty, 1, 0, 0, 0); c.fillStyle = rgbS(mix(K.col.eggUnder(cb), K.col.eggEdge(cb + 0.1), 0.6), 0.95); c.fill();
      }
    }
    // body: painter-sorted triangles, flat shaded, the winding picks the face
    TL.length = 0;
    const st = vw.st, ctv = vw.ct;
    for (const t of s.T) {
      if (t.cut) continue;
      const [i, j, k] = t.v, a = P[i], b = P[j], d = P[k];
      const cr = (X[j] - X[i]) * (Y[k] - Y[i]) - (Y[j] - Y[i]) * (X[k] - X[i]), top = cr < 0;
      const e1 = [b.u - a.u, b.v - a.v, b.h - a.h], e2 = [d.u - a.u, d.v - a.v, d.h - a.h];
      let nx = e1[1] * e2[2] - e1[2] * e2[1], ny = e1[2] * e2[0] - e1[0] * e2[2], nh = e1[0] * e2[1] - e1[1] * e2[0];
      const nl = (top ? 1 : -1) / (Math.hypot(nx, ny, nh) || 1);
      const shade = 0.9 + 0.13 * Math.max(0, (nx * LT[0] + ny * LT[1] + nh * LT[2]) * nl);
      const rim = (a.rim + b.rim + d.rim) / 3, set = (setOf(a) + setOf(b) + setOf(d)) / 3;
      const cb = (a.cb + b.cb + d.cb) / 3, ct = (a.ct + b.ct + d.ct) / 3;
      let col;
      if (top) {
        col = mix(WOK, K.col.eggWhite(Math.max(set * 0.75, ct)), 0.36 + 0.64 * set);
      } else {
        const nz = (a.nz + b.nz + d.nz) / 3;
        col = mix(WOK, K.col.eggUnder(cb), 0.45 + 0.55 * clamp(cb / 0.6, 0, 1));
        col = mix(col, K.col.eggEdge(cb + 0.1), Math.pow(rim, 3) * 0.75).map((x) => x * (0.92 + 0.16 * nz));
      }
      TL.push({ i, j, k, z: (a.h + b.h + d.h) * ctv - (a.v + b.v + d.v) * st + (a.layer + b.layer + d.layer) * 0.5 + (top ? 0 : -1e-4), f: rgbS(col, shade), top });
    }
    TL.sort((p, q) => p.z - q.z);
    c.lineWidth = 1; c.lineJoin = "round";
    c.save();
    if (smoothBody) { ringPath(c, s, X, Y, tx, ty, 1, 0, 0, 0); c.clip(); } // no straight polygon edges on the silhouette
    for (const t of TL) {
      if (smoothBody && topUp && t.top) continue;
      c.fillStyle = c.strokeStyle = t.f;
      c.beginPath(); c.moveTo(X[t.i], Y[t.i]); c.lineTo(X[t.j], Y[t.j]); c.lineTo(X[t.k], Y[t.k]); c.closePath(); c.fill(); c.stroke();
    }
    c.restore();
    const y0 = s.P[s.centre], yx = X[s.centre] + s.yolk.x * R, yy = Y[s.centre] - s.yolk.y * R * ctv;
    if (!topUp) {
      // browned underside: bubble craters + the yolk bump
      if (vis > 0.25) {
        for (const cr of s.craters) {
          const p = P[cr.p]; if (torn || folded) continue;
          c.fillStyle = K.rgb(K.col.eggUnder(p.cb + 0.45), 0.55);
          c.beginPath(); c.ellipse(X[cr.p] + cr.ox * 0.06 * R * sc, Y[cr.p] + cr.oy * 0.05 * R * sc, 0.013 * R * cr.s * sc, 0.009 * R * cr.s * sc * vis, 0, 0, TAU); c.fill();
        }
        if (!folded && !torn) { c.fillStyle = `rgba(255,236,190,${0.22 * vis})`; c.beginPath(); c.ellipse(yx, yy - 0.02 * R, 0.1 * R * sc, 0.08 * R * sc * vis, 0, 0, TAU); c.fill(); }
      }
    } else {
      const set = setOf(y0);
      if (set < 0.85) { // raw whites are glossy
        c.fillStyle = `rgba(255,255,255,${0.6 * (1 - set)})`;
        c.beginPath(); c.ellipse(yx - 0.16 * R * sc, yy - 0.06 * R * sc, 0.09 * R * sc, 0.035 * R * sc * vis, -0.3, 0, TAU); c.fill();
        c.beginPath(); c.ellipse(yx + 0.15 * R * sc, yy + 0.09 * R * sc, 0.05 * R * sc, 0.018 * R * sc * vis, -0.3, 0, TAU); c.fill();
      }
      if (!res.burst || s.phase === "air" || s.phase === "land") drawYolk(c, yx, yy - (folded ? 0.03 * R : 0), 0.118 * R * sc * (folded ? 0.8 : 1), vis, s.cookTop, s.su, s.sv);
      else { // deflated yolk skin
        c.fillStyle = "rgba(242,138,0,.85)"; c.beginPath(); c.ellipse(yx, yy, 0.08 * R, 0.05 * R, 0.3, 0, TAU); c.fill();
      }
    }
    c.restore();
    // runny yolk droplets: overlapping circles (cheap metaball)
    if (s.drops.length) {
      const pts = s.drops.map((d) => [...K.proj(vw, pose, d.u, d.v, K.surf(d.u, d.v) + 0.04), d.r * R]);
      c.fillStyle = "#E57E00"; for (const [x, y, r] of pts) { c.beginPath(); c.ellipse(x, y, r + 1.5, (r + 1.5) * ctv, 0, 0, TAU); c.fill(); }
      c.fillStyle = "#FFB21A"; for (const [x, y, r] of pts) { c.beginPath(); c.ellipse(x, y, r, r * ctv, 0, 0, TAU); c.fill(); }
      c.fillStyle = "rgba(255,240,190,.8)"; for (const [x, y, r] of pts) { c.beginPath(); c.ellipse(x - r * 0.35, y - r * 0.3, r * 0.25, r * 0.15, 0, 0, TAU); c.fill(); }
    }
  }
  /* closed smooth path through the rim ring, pushed out from (cx, cy); jag adds lace spikes between points */
  function ringPath(c, s, X, Y, cx, cy, grow, jag, dx, dy) {
    const R = s.ring, n = R.length, pts = [];
    for (let k = 0; k < n; k++) {
      const i = R[k], j = R[(k + 1) % n];
      const g1 = grow + jag * (s.lace[(2 * k) % 40] - 0.3), g2 = grow * 1.035 + jag * (s.lace[(2 * k + 1) % 40] * 1.6 - 0.2);
      pts.push([cx + (X[i] - cx) * g1 + dx, cy + (Y[i] - cy) * g1 + dy]);
      const mx = (X[i] + X[j]) / 2, my = (Y[i] + Y[j]) / 2;
      pts.push([cx + (mx - cx) * g2 + dx, cy + (my - cy) * g2 + dy]);
    }
    c.beginPath();
    const m0 = [(pts[0][0] + pts[1][0]) / 2, (pts[0][1] + pts[1][1]) / 2]; c.moveTo(m0[0], m0[1]);
    for (let k = 1; k <= pts.length; k++) {
      const p = pts[k % pts.length], q = pts[(k + 1) % pts.length];
      c.quadraticCurveTo(p[0], p[1], (p[0] + q[0]) / 2, (p[1] + q[1]) / 2);
    }
    c.closePath();
  }
  function drawYolk(c, x, y, r, vis, cook, su, sv) {
    const sp = Math.hypot(su || 0, sv || 0), st = Math.min(1.5, 1 + (0.4 * sp) / 4);
    const rx = r * Math.sqrt(st), ry = (r / Math.sqrt(st)) * clamp(0.3 + 0.62 * vis, 0.3, 0.92), dy = -0.25 * r * vis;
    c.fillStyle = "rgba(150,100,30,.22)"; c.beginPath(); c.ellipse(x + 0.2 * r, y + 0.25 * r, rx, ry, 0, 0, TAU); c.fill();
    const mid = K.col.yolk(cook * 1.2);
    const g = c.createRadialGradient(x - 0.35 * r, y + dy - 0.4 * r, 0.1 * r, x, y + dy, Math.max(rx, ry));
    g.addColorStop(0, "#FFE27A"); g.addColorStop(0.35, "#FFD04A"); g.addColorStop(0.75, K.rgb(mid)); g.addColorStop(1, "#F28A00");
    c.fillStyle = g; c.beginPath(); c.ellipse(x, y + dy, rx, ry, 0, 0, TAU); c.fill();
    c.fillStyle = "rgba(255,255,255,.9)"; c.beginPath(); c.ellipse(x - 0.38 * rx, y + dy - 0.42 * ry, 0.28 * rx, 0.15 * ry + 0.5, -0.5, 0, TAU); c.fill();
    c.beginPath(); c.arc(x + 0.25 * rx, y + dy - 0.5 * ry, Math.max(0.8, 0.06 * r), 0, TAU); c.fill();
  }
  /* the egg hits the screen glass and slides down it */
  function drawSplat(c, s, vw, pose) {
    const fly = s.phase === "splatFly", t = fly ? 0 : s.age, W = vw.W, n = s.blob.length, cb = s.cookAtToss || 0;
    let R = W * 0.36, cx = W * (0.5 + (s.splatX || 0)), cy = Math.min(vw.H * 0.72, vw.H * 0.34 + 22 * t + 34 * t * t), pop = t < 0.08 ? 0.8 + 2.5 * t : 1;
    if (fly) { // rushing at the glass: grows from its place in the air to the splat
      const k = s.age / 0.22, [cu, cv, ch] = centroid(s), [ex, ey] = K.proj(vw, AIRPOSE, cu, cv, ch);
      cx = lerp(ex, cx, k); cy = lerp(ey, cy, k); pop = lerp(ER * vw.R * 1.2 / R, 0.75, k * k);
    }
    const blob = (rr) => {
      c.beginPath();
      const pts = s.blob.map((z, k) => { const a = (k / n) * TAU; return [cx + Math.cos(a) * rr * (0.8 + 0.35 * z), cy + Math.sin(a) * rr * (0.8 + 0.35 * z) * 0.92]; });
      c.moveTo((pts[0][0] + pts[1][0]) / 2, (pts[0][1] + pts[1][1]) / 2);
      for (let k = 1; k <= n; k++) { const p = pts[k % n], q = pts[(k + 1) % n]; c.quadraticCurveTo(p[0], p[1], (p[0] + q[0]) / 2, (p[1] + q[1]) / 2); }
      c.closePath();
    };
    c.save();
    c.fillStyle = "rgba(0,0,0,.16)"; c.translate(4, 7); blob(R * pop); c.fill(); c.translate(-4, -7);
    if (cb > 0.5) { c.fillStyle = K.rgb(K.col.eggEdge(cb), 0.95); blob(R * pop * 1.05); c.fill(); }
    const g = c.createRadialGradient(cx - R * 0.2, cy - R * 0.25, R * 0.1, cx, cy, R * 1.1);
    g.addColorStop(0, "#FFFDF6"); g.addColorStop(1, "#E9DFC9");
    c.fillStyle = g; blob(R * pop); c.fill();
    // drips of white running down the glass
    const dl = Math.min(1, t * 0.9);
    for (const [a, w, l] of [[0.35, 0.09, 0.55], [0.62, 0.07, 0.8], [0.15, 0.06, 0.4]]) {
      const x = cx + (a - 0.4) * R * 1.6, y = cy + R * 0.7, L = R * l * dl;
      c.beginPath(); c.moveTo(x - w * R, y); c.lineTo(x - w * R * 0.6, y + L); c.arc(x, y + L, w * R * 0.6, PI, 0, true); c.lineTo(x + w * R, y); c.fill();
    }
    // burst yolk smeared and running
    const yx = cx + R * 0.06, yy = cy - R * 0.06, yl = R * (0.3 + 0.9 * Math.min(1, t * 0.8));
    c.fillStyle = "#F28A00";
    c.beginPath(); c.ellipse(yx, yy, R * 0.32, R * 0.27, 0.3, 0, TAU); c.fill();
    c.beginPath(); c.moveTo(yx - R * 0.12, yy); c.quadraticCurveTo(yx - R * 0.06, yy + yl * 0.6, yx - R * 0.02, yy + yl); c.arc(yx + R * 0.03, yy + yl, R * 0.05, PI, 0, true); c.quadraticCurveTo(yx + R * 0.12, yy + yl * 0.5, yx + R * 0.14, yy); c.fill();
    c.fillStyle = "#FFB21A";
    c.beginPath(); c.ellipse(yx, yy, R * 0.28, R * 0.23, 0.3, 0, TAU); c.fill();
    c.beginPath(); c.moveTo(yx - R * 0.08, yy); c.quadraticCurveTo(yx - R * 0.04, yy + yl * 0.6, yx, yy + yl); c.arc(yx + R * 0.03, yy + yl, R * 0.03, PI, 0, true); c.quadraticCurveTo(yx + R * 0.09, yy + yl * 0.5, yx + R * 0.1, yy); c.fill();
    // glass glare
    c.strokeStyle = "rgba(255,255,255,.6)"; c.lineCap = "round"; c.lineWidth = R * 0.05;
    c.beginPath(); c.arc(cx, cy, R * 0.72, PI * 1.12, PI * 1.38); c.stroke();
    c.lineWidth = R * 0.025; c.beginPath(); c.arc(yx, yy, R * 0.18, PI * 1.1, PI * 1.45); c.stroke();
    c.restore();
  }

  /* ---------- the egg on the rice, result slip ---------- */
  function drawOnRice(c, s, x, y, r, ct) {
    const o = outcome(s), cb = o.cookBottom || 0;
    const body = (dx, dy) => {
      const lace = s.blob.map((z, k) => { const a = (k / s.blob.length) * TAU; return [x + dx + Math.cos(a) * r * (1.04 + 0.1 * z), y + dy + Math.sin(a) * r * ct * (1.04 + 0.1 * z)]; });
      const wpts = s.blob.map((z, k) => { const a = (k / s.blob.length) * TAU; return [x + dx + Math.cos(a) * r * (0.9 + 0.1 * s.lace[k]), y + dy + Math.sin(a) * r * ct * (0.9 + 0.1 * s.lace[k])]; });
      if (cb > 0.45) { smooth(c, lace); c.fillStyle = K.rgb(K.col.eggEdge(cb + 0.2)); c.fill(); c.strokeStyle = K.rgb(K.col.eggEdge(cb + 0.4), 0.8); c.lineWidth = 1; c.stroke(); }
      smooth(c, wpts);
      const g = c.createRadialGradient(x + dx - r * 0.25, y + dy - r * 0.3 * ct, r * 0.1, x + dx, y + dy, r * 1.05);
      g.addColorStop(0, "#FFFDF6"); g.addColorStop(0.7, "#FBF5E8"); g.addColorStop(1, "#EADFC8");
      c.fillStyle = g; c.fill();
      if (!o.flipped || o.stuck) { // raw top: translucent, glossy whites round the yolk
        c.fillStyle = "rgba(205,212,214,.55)"; c.beginPath(); c.ellipse(x + dx, y + dy - r * 0.05, r * 0.5, r * 0.4 * ct, 0.2, 0, TAU); c.fill();
        c.fillStyle = "rgba(255,255,255,.7)"; c.beginPath(); c.ellipse(x + dx - r * 0.3, y + dy - r * 0.2 * ct, r * 0.18, r * 0.05, -0.4, 0, TAU); c.fill();
      }
    };
    c.save();
    if (o.folded) {
      // half-moon: the fold edge faces us, browned lace on the curve, a yolk seam peeking out
      c.beginPath(); c.ellipse(x, y + r * 0.2 * ct, r * 1.06, r * 0.9 * ct, 0, PI, TAU); c.closePath();
      c.fillStyle = K.rgb(K.col.eggEdge(Math.max(cb, 0.8))); c.fill();
      c.beginPath(); c.ellipse(x, y + r * 0.2 * ct, r * 0.97, r * 0.82 * ct, 0, PI, TAU);
      c.quadraticCurveTo(x, y + r * 0.34 * ct, x - r * 0.97, y + r * 0.2 * ct);
      const g = c.createLinearGradient(x, y - r * 0.7 * ct, x, y + r * 0.3 * ct); g.addColorStop(0, K.rgb(K.col.eggUnder(cb))); g.addColorStop(1, K.rgb(K.col.eggUnder(cb * 0.6)));
      c.fillStyle = g; c.fill();
      for (let k = 0; k < 7; k++) { c.fillStyle = K.rgb(K.col.eggUnder(cb + 0.45), 0.5); c.beginPath(); c.ellipse(x + (s.lace[k] - 0.5) * r * 1.3, y - s.lace[k + 7] * r * 0.5 * ct, r * 0.04, r * 0.03, 0, 0, TAU); c.fill(); }
      c.fillStyle = o.burst ? "#FFB21A" : "#F7A21B"; c.beginPath(); c.ellipse(x + r * 0.05, y + r * 0.26 * ct, r * 0.3, r * 0.06, 0, 0, PI); c.fill();
      if (o.burst) runny(c, x + r * 0.05, y + r * 0.3 * ct, r);
    } else if (o.torn) {
      for (const sg of [-1, 1]) {
        c.save(); c.beginPath();
        const jx = (k) => x + sg * 0.0 + (s.lace[k] - 0.5) * r * 0.25;
        c.moveTo(x, y - r * 2);
        for (let k = 0; k <= 8; k++) c.lineTo(jx(k) + k * r * 0.03, y - r + (k / 8) * r * 2);
        c.lineTo(x + sg * r * 3, y + r * 2); c.lineTo(x + sg * r * 3, y - r * 2); c.closePath(); c.clip();
        body(sg * r * 0.12, sg * r * 0.03);
        c.restore();
      }
      if (o.burst) runny(c, x + r * 0.1, y - r * 0.05, r);
      else drawYolk(c, x + r * 0.22, y - r * 0.08, r * 0.3, 0.85, o.cookTop, 0, 0);
    } else {
      body(0, 0);
      if (o.burst) runny(c, x - r * 0.04, y - r * 0.08, r);
      else {
        drawYolk(c, x - r * 0.04, y - r * 0.08, r * 0.33, 0.85, o.flipped ? o.cookTop + 0.3 : 0, 0, 0);
        if (!o.flipped) { c.fillStyle = "rgba(255,255,255,.55)"; c.beginPath(); c.ellipse(x + r * 0.12, y - r * 0.02, r * 0.07, r * 0.03, 0.5, 0, TAU); c.fill(); }
      }
    }
    c.restore();
  }
  function runny(c, x, y, r) { // burst yolk running down the rice
    c.fillStyle = "#F28A00";
    c.beginPath(); c.ellipse(x, y, r * 0.3, r * 0.17, 0.15, 0, TAU); c.fill();
    c.fillStyle = "#FFB21A";
    c.beginPath(); c.ellipse(x, y, r * 0.26, r * 0.14, 0.15, 0, TAU); c.fill();
    for (const [dx, l, w] of [[-0.15, 0.95, 0.07], [0.12, 0.7, 0.06], [0.28, 1.15, 0.05]]) {
      const x0 = x + dx * r;
      c.beginPath(); c.moveTo(x0 - w * r, y); c.quadraticCurveTo(x0 - w * r * 0.8, y + l * r * 0.6, x0, y + l * r); c.arc(x0 + w * r * 0.35, y + l * r, w * r * 0.6, PI, 0, true); c.quadraticCurveTo(x0 + w * r, y + l * r * 0.4, x0 + w * r, y); c.fill();
    }
    c.fillStyle = "rgba(255,245,200,.85)"; c.beginPath(); c.ellipse(x - r * 0.1, y - r * 0.05, r * 0.08, r * 0.03, -0.3, 0, TAU); c.fill();
  }
  function smooth(c, pts) {
    const n = pts.length; c.beginPath();
    c.moveTo((pts[0][0] + pts[1][0]) / 2, (pts[0][1] + pts[1][1]) / 2);
    for (let k = 1; k <= n; k++) { const p = pts[k % n], q = pts[(k + 1) % n]; c.quadraticCurveTo(p[0], p[1], (p[0] + q[0]) / 2, (p[1] + q[1]) / 2); }
    c.closePath();
  }

  /* ---------- queries ---------- */
  function outcome(s) {
    const r = s.res || {};
    return {
      done: s.done, caught: !!r.caught, flipped: !!r.flipped, rot: r.rot || 0, folded: !!r.folded, torn: !!r.torn, burst: !!r.burst,
      splat: !!r.splat, angleErr: r.angleErr == null ? null : r.angleErr, angle: r.angle == null ? null : r.angle, grade: r.grade || null,
      strength: s.air ? s.air.st : null,
      cookTop: s.cookTop, cookBottom: s.cookAtToss == null ? s.cookBottom : s.cookAtToss, impact: r.impact || 0,
      stuck: s.stuck, landedNow: s.landedNow, u: r.u == null ? s.u : r.u, v: r.v == null ? s.v : r.v,
    };
  }
  function hotspots(s, k) {
    if (s.phase !== "pan" && s.phase !== "landed") return [];
    const out = [], step = Math.max(1, Math.floor(s.P.length / k));
    for (let i = 0; i < s.P.length && out.length < k; i += step) { const p = s.P[i]; out.push({ u: p.u, v: p.v, h: p.h, cook: Math.max(p.cb, p.ct) }); }
    return out;
  }

  K.Egg = {
    create, toss, update, draw, drawOnRice, outcome, hotspots,
    inPan: (s) => s.phase === "pan" && !s.done,
    forceStuck(s) { if (s.phase === "pan") s.stuck = true; s.done = true; },
  };
})();
