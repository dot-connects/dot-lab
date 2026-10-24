/* food.js — kimchi fried rice as ~2000 particles: a granular pile on the wok floor (real tilt, flows to the low side,
   spills over the rim), the toss (whole pile, lip first, rolls over toward the cook), cooking per grain (the side on
   the metal browns, toss turns it up, neglect scorches), plating onto the dish. */
"use strict";
(function () {
  const K = window.KFR, T = K.T, TAU = K.TAU, clamp = K.clamp, smooth = K.smooth, rnd = K.rnd, surf = K.surf, lin = K.lin;
  const G = 38;                       // with the higher toss (hs) airtime ~0.55 s: the arc reads on a phone (Ko & Hu: a real cycle is ~0.3 s)
  const GS = 7;                       // gravity for sliding on the floor
  const MU = 0.55, MUK = 0.35; // rice is sticky: it heaps (~35 deg) instead of levelling like a liquid; once it slides, it drags
  const HK = 0.009;                   // pile height per grain in a cell
  const NG = 38, CELL = 2.2 / NG;     // height grid over [-1.1, 1.1]²
  const COUNTER = -0.61;
  const F = (K.Food = { n: 0 });

  // colour ramps (chef): kimchi-stained raw → glossy red-orange → wok-hei brick → scorched → burnt
  const RAMP = [[0, "#e9b08a"], [0.4, "#e2793c"], [0.8, "#c8501f"], [1.1, "#9a4a1e"], [1.4, "#5b2c16"], [1.75, "#1c120d"]].map(([k, c]) => [k, lin(c)]);
  const KRAMP = [[0, "#b8231a"], [0.9, "#b8261c"], [1.4, "#7e1a12"], [1.9, "#2a1a12"]].map(([k, c]) => [k, lin(c)]);
  const PRAMP = [[0, "#5fb043"], [1.0, "#3f8a2c"], [1.5, "#4a4a22"], [1.9, "#262a1a"]].map(([k, c]) => [k, lin(c)]);
  const _c = new T.Color();
  function ramp(R, k, out) { if (k <= R[0][0]) return out.copy(R[0][1]); for (let i = 1; i < R.length; i++) if (k <= R[i][0]) return out.copy(R[i - 1][1]).lerp(R[i][1], (k - R[i - 1][0]) / (R[i][0] - R[i - 1][0])); return out.copy(R[R.length - 1][1]); }

  function build() {
    const W = K.W, S = W.S, M = W.mat;
    if (F.meshes) for (const m of F.meshes.concat(F.shadow)) m.parent && m.parent.remove(m);
    const nR = S.grainN, nK = 160, nP = 70, n = nR + nK + nP;
    const [gl, gw] = S.grain;
    const gGeo = new T.SphereGeometry(1, 7, 5); gGeo.scale(gl, gw, gw);
    const rMesh = new T.InstancedMesh(gGeo, M(S, "#ffffff", { std: { roughness: 0.5 }, phys: { roughness: 0.4, clearcoat: 0.9, clearcoatRoughness: 0.18 } }), nR);
    const leaf = new T.Shape(); for (let i = 0; i < 7; i++) { const a = (i / 7) * TAU, r = rnd(0.6, 1); i ? leaf.lineTo(Math.cos(a) * r, Math.sin(a) * r * 0.7) : leaf.moveTo(Math.cos(a) * r, Math.sin(a) * r * 0.7); }
    const lGeo = new T.ShapeGeometry(leaf); lGeo.rotateX(-Math.PI / 2); lGeo.scale(0.064, 1, 0.064);
    const kMesh = new T.InstancedMesh(lGeo, M(S, "#ffffff", { std: { side: T.DoubleSide, roughness: 0.35 }, phys: { side: T.DoubleSide, roughness: 0.3, clearcoat: 1 } }), nK);
    const pGeo = new T.TorusGeometry(0.02, 0.0075, 6, 12); pGeo.rotateX(Math.PI / 2);
    const pMesh = new T.InstancedMesh(pGeo, M(S, "#ffffff", { std: { roughness: 0.4 } }), nP);
    F.meshes = [rMesh, kMesh, pMesh]; F.off = [0, nR, nR + nK]; F.cnt = [nR, nK, nP];
    for (const m of F.meshes) { m.instanceMatrix.setUsage(T.DynamicDrawUsage); m.receiveShadow = true; W.wok.add(m); m.frustumCulled = false; }
    // flying grains cast shadows (the height reads from above); a cheap octahedron proxy, only airborne grains are in it
    const shGeo = new T.OctahedronGeometry(1, 0); shGeo.scale(gl, gw, gw); F.GL = gl;
    F.shadow = new T.InstancedMesh(shGeo, new T.MeshBasicMaterial({ colorWrite: false, depthWrite: false }), nR);
    F.shadow.castShadow = true; F.shadow.frustumCulled = false; F.shadow.instanceMatrix.setUsage(T.DynamicDrawUsage); W.wok.add(F.shadow);
    F.n = n;
    const A = (C) => new C(n);
    Object.assign(F, { kind: A(Uint8Array), st: A(Uint8Array), x: A(Float32Array), y: A(Float32Array), z: A(Float32Array), vx: A(Float32Array), vy: A(Float32Array), vz: A(Float32Array),
      depth: A(Float32Array), cook: A(Float32Array), tint: A(Float32Array), rank: A(Float32Array), dl: A(Float32Array), rx: A(Float32Array), ry: A(Float32Array), rz: A(Float32Array),
      sx: A(Float32Array), sy: A(Float32Array), sz: A(Float32Array), hx: A(Float32Array), hz: A(Float32Array), hh: A(Float32Array), cb: A(Int16Array), cellOf: A(Int32Array),
      H: new Float32Array(NG * NG), Hs: new Float32Array(NG * NG) });
    for (let i = 0; i < n; i++) F.kind[i] = i < nR ? 0 : i < nR + nK ? 1 : 2;
    for (let i = 0; i < n; i++) { const k = F.kind[i]; ramp(k === 0 ? RAMP : k === 1 ? KRAMP : PRAMP, 0, _c); F.meshes[k].setColorAt(i - F.off[k], _c); }
    for (const m of F.meshes) m.instanceColor.needsUpdate = true;
    reset();
  }
  const NL = 9, lumps = () => Array.from({ length: NL }, () => { const a = Math.random() * TAU, r = 0.32 * Math.sqrt(Math.random()); return [Math.cos(a) * r, Math.sin(a) * r * 0.8]; });
  let LUMP0 = lumps();
  function reset() {
    LUMP0 = lumps();
    for (let i = 0; i < F.n; i++) {
      const L = LUMP0[i % NL], a = Math.random() * TAU, r = 0.2 * Math.sqrt(Math.random());
      F.x[i] = L[0] + Math.cos(a) * r; F.z[i] = L[1] + Math.sin(a) * r; F.vx[i] = F.vz[i] = F.vy[i] = 0; F.st[i] = 0;
      F.depth[i] = F.kind[i] === 2 ? rnd(0.7, 1) : Math.random(); F.cook[i] = rnd(0, 0.08); F.tint[i] = rnd(-0.08, 0.08); F.cb[i] = -1;
      F.rx[i] = Math.random() * TAU; F.ry[i] = Math.random() * TAU; F.rz[i] = Math.random() * TAU;
    }
    F.pile = new Map(); F.toss = null; F.spilled = 0; F.plateMiss = 0; F.spillPan = 0; F.spillAir = 0; F.onPlate = 0; F.lastLand = -9; F.plateN = 0;
    heights(); for (let i = 0; i < F.n; i++) F.y[i] = groundY(i);
  }
  F.build = build; F.reset = reset;
  // the intro: the wok starts empty, then the rice and the kimchi drop in from a bowl (they land through the air path)
  F.hold = function () { for (let i = 0; i < F.n; i++) F.st[i] = 7; };
  F.drop = function (kind) {
    const wm = K.W.wok.matrixWorld;
    for (let i = 0; i < F.n; i++) {
      if (F.st[i] !== 7 || (kind === 0) !== (F.kind[i] === 0)) continue;
      _v.set(F.x[i] * 0.7, K.RIM + 0.7 + rnd(0, 0.3), F.z[i] * 0.7 + 0.1).applyMatrix4(wm);
      F.x[i] = _v.x; F.y[i] = _v.y; F.z[i] = _v.z; F.vx[i] = rnd(-0.2, 0.2); F.vy[i] = -rnd(0.5, 1.5); F.vz[i] = rnd(-0.2, 0.2);
      F.sx[i] = rnd(-6, 6); F.sy[i] = rnd(-3, 3); F.sz[i] = rnd(-6, 6); F.st[i] = 2;
    }
  };

  /* ---------- the pile's height field ---------- */
  const cellIx = (x, z) => { const cx = clamp(((x + 1.1) / CELL) | 0, 0, NG - 1), cz = clamp(((z + 1.1) / CELL) | 0, 0, NG - 1); return cz * NG + cx; };
  // the grid shifts by a random sub-cell offset every step: grains feel their own deposit, and a fixed grid would
  // pull them onto its lines (the checkerboard); a moving one averages that out
  let GO = 0.5;
  function heights() {
    GO = Math.random();
    const H = F.H, Hs = F.Hs; H.fill(0);
    for (let i = 0; i < F.n; i++) if (F.st[i] === 0) {
      F.cellOf[i] = cellIx(F.x[i], F.z[i]);
      const m = F.kind[i] === 1 ? 1.4 : 1, fx = clamp((F.x[i] + 1.1) / CELL - GO, 0, NG - 1.001), fz = clamp((F.z[i] + 1.1) / CELL - GO, 0, NG - 1.001);
      const cx = fx | 0, cz = fz | 0, u = fx - cx, v = fz - cz, j = cz * NG + cx;
      H[j] += m * (1 - u) * (1 - v); H[j + 1] += m * u * (1 - v); H[j + NG] += m * (1 - u) * v; H[j + NG + 1] += m * u * v;
    }
    for (let cz = 0; cz < NG; cz++) for (let cx = 0; cx < NG; cx++) { // 3x3 blur, separable 1-2-1: its spectrum is never negative, a box blur's is and the grains formed clumps on a lattice
      let s = 0, w = 0;
      for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) { const x = cx + dx, z = cz + dz; if (x < 0 || z < 0 || x >= NG || z >= NG) continue; const k = (2 - Math.abs(dx)) * (2 - Math.abs(dz)); s += H[z * NG + x] * k; w += k; }
      Hs[cz * NG + cx] = (s / w) * HK;
    }
  }
  let _gx = 0, _gz = 0; // slope of the last Hat() sample
  function Hat(x, z) {
    const H = F.Hs, fx = clamp((x + 1.1) / CELL - GO, 0, NG - 1.001), fz = clamp((z + 1.1) / CELL - GO, 0, NG - 1.001);
    const cx = fx | 0, cz = fz | 0, u = fx - cx, v = fz - cz, i = cz * NG + cx;
    const a = H[i], b = H[i + 1], c = H[i + NG], d = H[i + NG + 1];
    _gx = ((b - a) * (1 - v) + (d - c) * v) / CELL; _gz = ((c - a) * (1 - u) + (d - b) * u) / CELL;
    return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
  }
  // drawn taller than the physics pile (VIS): a low HK keeps the heap from spreading into a sheet, VIS makes it read as a mound
  const VIS = 2.4;
  function groundY(i) { return surf(F.x[i] ** 2 + F.z[i] ** 2) + 0.008 + Hat(F.x[i], F.z[i]) * VIS * (0.25 + 0.75 * F.depth[i]); }
  F.pileTop = (x, z) => surf(x * x + z * z) + 0.01 + Hat(x, z) * VIS;

  /* ---------- toss ---------- */
  // f: { strength, dirU, dirV } — dir is the throw direction on screen (up = away from the cook)
  F.launch = function (f, t) {
    let du = f.dirU || 0, dv = f.dirV == null ? 1 : f.dirV; const nn = Math.hypot(du, dv);
    if (nn < 0.3 || dv < -0.2) { du = 0; dv = 1; } else { du /= nn; dv /= nn; }
    const s = clamp(f.strength || 1, 0.3, 1.8), dx = du, dz = -dv; // wok-local throw direction (forward = -z)
    let n = 0; const keep = 1 - clamp(0.55 + 0.35 * (s - 0.6) / 0.8, 0.55, 0.9);
    for (let i = 0; i < F.n; i++) {
      if (F.st[i] !== 0) continue;
      const along = F.x[i] * dx + F.z[i] * dz, rk = clamp((along + 0.75) / 1.5, 0, 1);
      if (rk < keep * rnd(0.6, 1.4)) { F.vx[i] += dx * 0.2 * s; F.vz[i] += dz * 0.2 * s; F.depth[i] = clamp(F.depth[i] + rnd(-0.35, 0.35), 0, 1); continue; } // the slosh stirs it too // the back of the pile sloshes forward a little
      F.st[i] = 1; F.rank[i] = rk; F.dl[i] = (1 - F.rank[i]) * 0.12 * rnd(0.7, 1.3);
      F.hx[i] = F.x[i]; F.hz[i] = F.z[i]; n++;
    }
    F.toss = { t, s, dx, dz, n, landed: 0, done: false, peak: 0, lumps: Array.from({ length: NL }, () => [rnd(-0.24, 0.24), rnd(-0.22, 0.22)]) };
    return n;
  };
  const _v = new T.Vector3(), _m = new T.Matrix4(), _inv = new T.Matrix4(), _q = new T.Quaternion(), _g = new T.Vector3();
  function release(i, wm) {
    const tz = F.toss, r = F.rank[i], kh = [1, 0.85, 1.15][F.kind[i]];
    _v.set(F.x[i], F.y[i], F.z[i]).applyMatrix4(wm); F.x[i] = _v.x; F.y[i] = _v.y; F.z[i] = _v.z;
    // height grows with the flick (the operator: higher and flashier scores more); too hard flies over the rim
    const hs = 0.7 + 0.9 * tz.s; // high enough to clear the rim and read from above
    const rise = Math.max(0.12, K.RIM + hs * kh * (0.3 + 0.9 * r) * rnd(0.75, 1.25) - _v.y);
    const vy = Math.sqrt(2 * G * rise), T0 = vy / G + Math.sqrt((2 * Math.max(0.05, _v.y + rise - 0.2)) / G);
    // roll over: the lip grains land nearest the cook; an over-hard flick carries past the near rim
    const over = Math.max(0, tz.s - 1.25) * 1.1;
    const a0 = _v.x * tz.dx + _v.z * tz.dz, b0 = -_v.x * tz.dz + _v.z * tz.dx;
    const L = tz.lumps[i % NL]; // grains that stick together come down together
    // gaussian scatter around the lump (a uniform box landed the heap as a rectangle)
    const ga = (Math.random() + Math.random() + Math.random() - 1.5) * 0.22, gb = (Math.random() + Math.random() + Math.random() - 1.5) * 0.22;
    const aT = -0.1 - 0.1 * (r - 0.5) - over + L[0] + ga * (1 + over), bT = b0 * 0.35 + L[1] + gb * (1 + over * 2);
    const va = (aT - a0) / T0, vb = (bT - b0) / T0;
    F.vx[i] = tz.dx * va - tz.dz * vb; F.vz[i] = tz.dz * va + tz.dx * vb; F.vy[i] = vy;
    F.sx[i] = rnd(-12, 12); F.sy[i] = rnd(-6, 6); F.sz[i] = rnd(-12, 12);
    F.st[i] = 2;
  }

  /* ---------- per step ---------- */
  // ctx: { t, dt, heat (0..1.4), plate: {x,y,z,on} | null }
  F.step = function (ctx) {
    const W = K.W, t = ctx.t, dt = ctx.dt, wm = W.wok.matrixWorld;
    _inv.copy(wm).invert();
    // gravity in the wok's frame: what makes the pile flow when the wok tilts
    W.wok.getWorldQuaternion(_q); _g.set(0, -GS, 0).applyQuaternion(_q.invert());
    heights();
    const damp = Math.exp(-4.5 * dt), H = F.Hs;
    let air = 0, landed = 0, contact = 0, spilledNow = 0;
    const tz = F.toss;
    for (let i = 0; i < F.n; i++) {
      const st = F.st[i];
      if (st === 0) {
        contact++;
        const x = F.x[i], z = F.z[i]; Hat(x, z);
        const gx = 0.7 * x + _gx, gz = 0.7 * z + _gz;
        let ax = _g.x + _g.y * gx, az = _g.z + _g.y * gz; // tangential gravity minus the slope (g.y < 0 pulls downhill)
        if (ctx.pour) { ax += ctx.pour * 5; }
        const am = Math.hypot(ax, az);
        if (am < MU * GS * (0.8 + 0.4 * F.depth[i]) && Math.hypot(F.vx[i], F.vz[i]) < 0.08) { F.vx[i] *= 0.5; F.vz[i] *= 0.5; } // static: the pile holds
        else {
          F.vx[i] += ax * dt; F.vz[i] += az * dt;
          const sp = Math.hypot(F.vx[i], F.vz[i]); // kinetic friction: a sliding grain slows unless the wok is really tipped
          if (sp > 1e-6) { const k = Math.max(0, sp - MUK * GS * dt) / sp; F.vx[i] *= k; F.vz[i] *= k; }
        }
        F.vx[i] *= damp; F.vz[i] *= damp;
        F.x[i] += F.vx[i] * dt; F.z[i] += F.vz[i] * dt;
        const r = Math.hypot(F.x[i], F.z[i]);
        if (r > 1.0) { // over the rim: it leaves the wok
          _v.set(F.x[i], K.RIM + 0.01, F.z[i]).applyMatrix4(wm); F.x[i] = _v.x; F.y[i] = _v.y; F.z[i] = _v.z;
          _v.set(F.vx[i] * 0.6, 0.4, F.vz[i] * 0.6).applyQuaternion(_q.invert()); _q.invert();
          F.vx[i] = _v.x + (F.x[i] - wm.elements[12]) * 0.4; F.vy[i] = _v.y; F.vz[i] = _v.z + (F.z[i] - wm.elements[14]) * 0.4;
          F.st[i] = 6; spilledNow++; F.spillPan = (F.spillPan || 0) + 1; continue;
        }
        F.y[i] += (groundY(i) - F.y[i]) * Math.min(1, dt * 14);
        // cooking: the layer on the metal sears, the top barely warms; the hot centre cooks fastest
        const heat = ctx.heat * Math.max(0.15, 1.15 - 0.65 * r * r), lay = (1 - F.depth[i]) ** 2 * 0.9 + 0.1;
        F.cook[i] += 0.2 * heat * lay * dt + 0.24 * (F.scorchVis || 0) * F.depth[i] * dt; // scorching: smoke and heat brown the top too, so it reads from above
      } else if (st === 1) { // riding up the far wall before the lip snap
        const k = smooth((t - tz.t - 0.04) / 0.13), a = F.hx[i] * tz.dx + F.hz[i] * tz.dz, b = -F.hx[i] * tz.dz + F.hz[i] * tz.dx;
        const aa = a + (0.55 + 0.35 * F.rank[i] - a) * k, bb = b * (1 - 0.3 * k);
        F.x[i] = tz.dx * aa - tz.dz * bb; F.z[i] = tz.dz * aa + tz.dx * bb; F.y[i] = surf(F.x[i] ** 2 + F.z[i] ** 2) + 0.03 + 0.04 * F.depth[i];
        if (t - tz.t >= 0.17 + F.dl[i]) release(i, wm);
      } else if (st === 2) {
        air++;
        F.vy[i] -= (Math.abs(F.vy[i]) < 1 ? 0.75 : 1) * G * dt;
        F.x[i] += F.vx[i] * dt; F.y[i] += F.vy[i] * dt; F.z[i] += F.vz[i] * dt;
        F.rx[i] += F.sx[i] * dt; F.ry[i] += F.sy[i] * dt; F.rz[i] += F.sz[i] * dt;
        F.cook[i] += 0.01 * dt;
        if (tz) tz.peak = Math.max(tz.peak, F.y[i]);
        if (F.vy[i] < 0) {
          _v.set(F.x[i], F.y[i], F.z[i]).applyMatrix4(_inv);
          const r2 = _v.x * _v.x + _v.z * _v.z;
          if (r2 < 1 && _v.y <= F.pileTop(_v.x, _v.z) && F.vy[i] < -2.5 && Math.random() < 0.15) { F.vy[i] *= -0.3; F.vx[i] *= 0.5; F.vz[i] *= 0.5; }
          else if (r2 < 1 && _v.y <= F.pileTop(_v.x, _v.z)) {
            // lands: wok-local again, keeps a little of its momentum (the pile sloshes), the seared side comes up
            F.st[i] = 0; F.x[i] = _v.x; F.z[i] = _v.z; F.y[i] = _v.y;
            _v.set(F.vx[i], 0, F.vz[i]).transformDirection(_inv); F.vx[i] = _v.x * 0.35; F.vz[i] = _v.z * 0.35;
            F.depth[i] = clamp(1 - F.depth[i] + rnd(-0.12, 0.12), 0, 1); landed++;
          } else if (r2 >= 1 && _v.y < K.RIM) { F.st[i] = 6; spilledNow++; F.spillAir = (F.spillAir || 0) + 1; }
        }
      } else if (st === 6 || st === 5) { // falling outside the wok: onto the plate when it is there, else the counter
        F.vy[i] -= G * 0.8 * dt; F.x[i] += F.vx[i] * dt; F.y[i] += F.vy[i] * dt; F.z[i] += F.vz[i] * dt;
        F.rx[i] += F.sx[i] * dt; F.rz[i] += F.sz[i] * dt;
        const P = ctx.plate;
        if (P && P.on) {
          const d = Math.hypot(F.x[i] - P.x, F.z[i] - P.z);
          if (d < 0.6 && F.y[i] <= P.y + 0.03 + F.plateMound(d) && F.vy[i] < 0) { toPlate(i, P); continue; }
        }
        if (F.y[i] <= COUNTER) { F.st[i] = 4; const ck = ((F.x[i] / 0.06) | 0) * 1000 + ((F.z[i] / 0.06) | 0), nn = (F.pile.get(ck) || 0) + 1; F.pile.set(ck, nn); F.y[i] = COUNTER + 0.011 * Math.min(nn, 12); if (P && P.on) F.plateMiss = (F.plateMiss || 0) + 1; else F.spilled++; } // the auto plating's misses are not the player's
      } else if (st === 3) { // on the plate: slide to its spot on the heap
        const P = ctx.plate; if (!P) continue;
        const tx = P.x + F.hx[i], tz2 = P.z + F.hz[i], ty = P.y + 0.03 + F.plateMound(Math.hypot(F.hx[i], F.hz[i])) * F.hh[i] * (1 - 0.1 * (P.pats || 0));
        const k = Math.min(1, dt * 12); F.x[i] += (tx - F.x[i]) * k; F.y[i] += (ty - F.y[i]) * k; F.z[i] += (tz2 - F.z[i]) * k;
      }
    }
    if (tz && !tz.done) { tz.landed += landed; if (tz.landed >= 0.85 * tz.n) { tz.done = true; F.lastLand = t; } }
    F.air = air; F.contact = contact / Math.max(1, F.n - F.spilled - F.onPlate);
    F.spilledNow = spilledNow;
    return { air, landed, spilledNow };
  };
  function toPlate(i, P) {
    F.st[i] = 3; F.onPlate++; const a = Math.random() * TAU, rr = Math.sqrt(Math.random()) * (0.2 + 0.24 * clamp(F.onPlate / (F.n * 0.85), 0, 1));
    F.hx[i] = Math.cos(a) * rr; F.hz[i] = Math.sin(a) * rr; F.hh[i] = rnd(0.55, 1);
  }
  F.plateMound = function (d) { const f = clamp(F.onPlate / (F.n * 0.85), 0, 1), R = 0.2 + 0.24 * f, Hh = 0.06 + 0.3 * f; return d >= R ? 0 : Hh * (1 - (d / R) ** 2); };
  // what is left in the wok after the pour slides back to the bottom
  F.settle = function () { for (let i = 0; i < F.n; i++) if (F.st[i] === 0) { F.x[i] *= 0.3; F.z[i] *= 0.3; } };

  /* ---------- draw ---------- */
  const dummy = new T.Object3D(), _d = new T.Vector3(), _q2 = new T.Quaternion(), AX = new T.Vector3(1, 0, 0), ZERO = new T.Matrix4().makeScale(0, 0, 0);
  let frame = 0;
  F.draw = function () {
    const wm = K.W.wok.matrixWorld; _inv.copy(wm).invert(); frame++;
    const colorPass = frame % 4 === 0;
    for (let i = 0; i < F.n; i++) {
      const k = F.kind[i], mi = i - F.off[k], m = F.meshes[k], st = F.st[i];
      dummy.position.set(F.x[i], F.y[i], F.z[i]);
      if (st >= 2) dummy.position.applyMatrix4(_inv); // flying / plated / spilled food is stored in world space
      dummy.scale.set(1, 1, 1);
      if (st === 7) dummy.scale.set(0, 0, 0); // not in the wok yet
      else if (st === 3) dummy.scale.setScalar(0.62); // on the plate: real-size grains for the close-up
      else if (st === 2 || st === 5 || st === 6) {
        dummy.rotation.set(F.rx[i], F.ry[i], F.rz[i]);
        if (k === 0 && st === 2) { // a fast grain streaks along its velocity: the arc reads as lines, not as dots that skip
          _d.set(F.vx[i], F.vy[i], F.vz[i]); const sp = _d.length();
          if (sp > 2) { _d.transformDirection(_inv); _q2.setFromUnitVectors(AX, _d); dummy.quaternion.slerp(_q2, clamp((sp - 2) / 2, 0, 1)); dummy.scale.x = 1 + Math.min(1.8, ((sp / 60) * 0.7) / (2 * F.GL)); }
        }
      } else if (k === 0) dummy.rotation.set(0, F.ry[i], 0.15 * Math.sin(F.rx[i]));
      else dummy.rotation.set(0.2 * Math.sin(F.rx[i]), F.ry[i], 0.2 * Math.sin(F.rz[i]));
      dummy.updateMatrix(); m.setMatrixAt(mi, dummy.matrix); if (k === 0) F.shadow.setMatrixAt(mi, st === 2 ? dummy.matrix : ZERO);
      if (colorPass) {
        const ck = F.cook[i] + F.tint[i] + (k === 0 ? F.fireTint || 0 : 0), b = ((ck * 25) | 0) + (st === 2 ? 1000 : 0); // 불맛 shows: the rice darkens toward caramel; flying grains catch the light
        if (b !== F.cb[i]) { F.cb[i] = b; ramp(k === 0 ? RAMP : k === 1 ? KRAMP : PRAMP, ck, _c); if (k === 0) _c.multiplyScalar((1 + F.tint[i]) * (st === 2 ? 1.3 : 1)); if (k === 2 && F.tint[i] > 0.04) _c.lerp(lin("#d8efb0"), 0.6); m.setColorAt(mi, _c); m.instanceColor.needsUpdate = true; }
      }
    }
    for (const m of F.meshes) m.instanceMatrix.needsUpdate = true;
    F.shadow.instanceMatrix.needsUpdate = true;
  };

  /* ---------- scoring queries ---------- */
  F.stats = function () {
    let good = 0, burnt = 0, raw = 0, all = 0;
    for (let i = 0; i < F.n; i++) { if (F.st[i] === 4) continue; all++; const c = F.cook[i]; if (c > 1.45) burnt++; else if (c < 0.4) raw++; else good++; }
    return { good: good / Math.max(1, all), burnt: burnt / Math.max(1, all), raw: raw / Math.max(1, all), spill: F.spilled / F.n };
  };
  // the bottom layer's state: how ready / how close to scorching the pile is (drives the diegetic cue and the smoke)
  F.bottom = function () {
    let s = 0, n = 0, hot = 0;
    for (let i = 0; i < F.n; i += 3) if (F.st[i] === 0 && F.depth[i] < 0.35) { s += F.cook[i]; n++; if (F.cook[i] > 1.35) hot++; }
    return { mean: n ? s / n : 0, scorch: n ? hot / n : 0 };
  };
})();
