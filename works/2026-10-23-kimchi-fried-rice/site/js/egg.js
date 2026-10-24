/* egg.js — one fried egg as a live height field: the white sets from the edge, blisters puff, the edge goes golden
   then brown; ripples and a yolk on a spring; a flip with real height / spin / drift that the player has to catch
   by moving the wok; outcomes: clean, folded, burst yolk, on the wall, on the counter. Then it slides onto the rice. */
"use strict";
(function () {
  const K = window.KFR, T = K.T, TAU = K.TAU, clamp = K.clamp, smooth = K.smooth, rnd = K.rnd, surf = K.surf, lin = K.lin;
  const YU = 0.04, YV = -0.03;   // yolk centre in egg-local x / z
  const GE = 16, PS = 0.62;     // PS: egg scale on the plate (the rice shows around it)                 // egg gravity: a good flip ~0.5 s in the air
  const E = (K.Egg = { ph: "off" });
  const C_RAW = lin("#666b70"), C_SET = lin("#fbfaf4"), C_GOLD = lin("#e7ac5c"), C_BROWN = lin("#8e4e20"), C_BURNT = lin("#2a1a12"), C_UNDER = lin("#f2e9d3"), C_DOME = lin("#efcf8a");
  const _c = new T.Color(), _v = new T.Vector3(), _q = new T.Quaternion(), _qx = new T.Quaternion(), _ax = new T.Vector3(1, 0, 0), _inv = new T.Matrix4();
  const dummy = new T.Object3D();

  function build() {
    const W = K.W, S = W.S, M = W.mat;
    const g = (E.g = new T.Group()); W.scene.add(g);
    const LN = 180, lump = new Float32Array(LN);
    for (let i = 0; i < LN; i++) lump[i] = Math.random() < 0.06 ? -rnd(0.02, 0.05) : rnd(-0.015, 0.02);
    for (let k = 0; k < 4; k++) for (let i = 0; i < LN; i++) lump[i] = (lump[i] + lump[(i + 1) % LN] + lump[(i + LN - 1) % LN]) / 3;
    const ER = (E.ER = (th) => { const f = ((((th / TAU) % 1) + 1) % 1) * LN, i = f | 0, w = f - i;
      return 0.43 * (1 + 0.16 * Math.sin(th + 0.7) + 0.08 * Math.sin(2 * th + 2) + 0.05 * Math.sin(5 * th) + lump[i] * (1 - w) + lump[(i + 1) % LN] * w); });
    const RN = 18, SN = 96, N = (E.N = (RN + 1) * SN);
    E.vx = new Float32Array(N); E.vz = new Float32Array(N); E.vt = new Float32Array(N); E.vd = new Float32Array(N); E.vn = new Float32Array(N);
    for (let i = 0; i <= RN; i++) for (let j = 0; j < SN; j++) {
      const k = i * SN + j, t = Math.pow(i / RN, 0.85), th = (j / SN) * TAU, r = t * ER(th);
      E.vx[k] = Math.cos(th) * r; E.vz[k] = Math.sin(th) * r; E.vt[k] = t; E.vn[k] = Math.random(); E.vd[k] = Math.hypot(E.vx[k] - YU, E.vz[k] - YV);
    }
    const grid = (down) => {
      const idx = [];
      for (let i = 0; i < RN; i++) for (let j = 0; j < SN; j++) { const a = i * SN + j, b = i * SN + ((j + 1) % SN), c = a + SN, d = b + SN; if (down) idx.push(a, c, b, b, c, d); else idx.push(a, b, c, b, d, c); }
      const geo = new T.BufferGeometry();
      geo.setAttribute("position", new T.BufferAttribute(new Float32Array(N * 3), 3).setUsage(T.DynamicDrawUsage));
      geo.setAttribute("color", new T.BufferAttribute(new Float32Array(N * 3), 3).setUsage(T.DynamicDrawUsage));
      geo.setIndex(idx); return geo;
    };
    const eggMat = () => { const m = M(S, "#ffffff", { std: { vertexColors: true, roughness: 0.3 }, phys: { vertexColors: true, roughness: 0.25, clearcoat: 0.8, clearcoatRoughness: 0.15 } }); m.vertexColors = true; return m; };
    E.top = new T.Mesh(grid(false), eggMat()); E.top.castShadow = true; g.add(E.top);
    E.top.geometry.setAttribute("aSet", new T.BufferAttribute(new Float32Array(N), 1).setUsage(T.DynamicDrawUsage));
    E.top.material.onBeforeCompile = (sh) => { // raw white: glossy and dark (the wok shows through); set: matte white
      sh.vertexShader = sh.vertexShader.replace("#include <common>", "#include <common>\nattribute float aSet; varying float vSet;").replace("#include <begin_vertex>", "#include <begin_vertex>\nvSet = aSet;");
      sh.fragmentShader = sh.fragmentShader.replace("#include <common>", "#include <common>\nvarying float vSet;")
        .replace("#include <roughnessmap_fragment>", "float roughnessFactor = mix(0.04, 0.55, vSet);")
        .replace("#include <tonemapping_fragment>", "float frE = pow(1.0 - saturate(dot(normal, normalize(vViewPosition))), 2.5);\ngl_FragColor.rgb += (1.0 - vSet) * (0.06 + frE * 0.45) * vec3(0.8, 0.82, 0.85);\n#include <tonemapping_fragment>");
    };
    E.bot = new T.Mesh(grid(true), eggMat()); g.add(E.bot);
    E.yolkMat = M(S, "#ffa412", { std: { roughness: 0.18 }, phys: { roughness: 0.12, clearcoat: 1, clearcoatRoughness: 0.04 } });
    E.yolkMat.onBeforeCompile = (sh) => { // light soaks into the yolk: its rim goes deep orange
      sh.fragmentShader = sh.fragmentShader.replace("#include <tonemapping_fragment>", "float ndvY = saturate(dot(normal, normalize(vViewPosition)));\ngl_FragColor.rgb = mix(gl_FragColor.rgb * vec3(0.95, 0.42, 0.12), gl_FragColor.rgb, pow(ndvY, 0.55));\n#include <tonemapping_fragment>");
    };
    E.yolk = new T.Mesh(new T.SphereGeometry(0.13, 40, 20, 0, TAU, 0, Math.PI / 2), E.yolkMat); E.yolk.castShadow = true; g.add(E.yolk);
    // oil sheen (adds reflections only), fizz around the egg, spatter streaks
    E.oil = new T.Mesh(W.polarDisc((th) => ER(th) * 1.2, 6, 64, (t, th, r) => surf(r * r) + 0.003, () => new T.Color(0, 0, 0)),
      new T.MeshStandardMaterial({ color: 0x000000, roughness: 0.06, metalness: 0, transparent: true, blending: T.AdditiveBlending, depthWrite: false, envMapIntensity: S.phys ? 0.45 : 0.15 }));
    W.wok.add(E.oil);
    E.fizz = new T.InstancedMesh(new T.SphereGeometry(1, 8, 4, 0, TAU, 0, Math.PI / 2), new T.MeshStandardMaterial({ color: lin("#3a3226"), roughness: 0.05, metalness: 0.2, transparent: true, opacity: 0.85 }), 60);
    E.fizz.instanceMatrix.setUsage(T.DynamicDrawUsage); W.wok.add(E.fizz);
    E.fz = []; for (let i = 0; i < 60; i++) { const a = Math.random() * TAU; E.fz.push({ a, r: ER(a) * rnd(0.98, 1.4), ph: Math.random() * TAU, sp: rnd(6, 14), s: rnd(0.006, 0.014) }); }
    E.spat = new T.LineSegments(new T.BufferGeometry(), new T.LineBasicMaterial({ color: lin("#fff3d6"), transparent: true, blending: T.AdditiveBlending, depthWrite: false }));
    E.spat.geometry.setAttribute("position", new T.BufferAttribute(new Float32Array(48 * 6), 3).setUsage(T.DynamicDrawUsage)); E.spat.frustumCulled = false; W.scene.add(E.spat);
    E.drops = []; for (let i = 0; i < 48; i++) E.drops.push({ life: 0, p: new T.Vector3(), v: new T.Vector3() });
    E.pud = new T.Mesh(W.polarDisc((th) => 0.24 * (1 + 0.25 * Math.sin(3 * th + 1) + 0.15 * Math.sin(7 * th)), 6, 64, () => 0, () => lin("#ffb21c")),
      M(S, "#ffffff", { std: { vertexColors: true, roughness: 0.12 }, phys: { vertexColors: true, roughness: 0.08, clearcoat: 1, clearcoatRoughness: 0.02 } }));
    E.pud.material.vertexColors = true; W.scene.add(E.pud);
    E.drips = []; for (let i = 0; i < 4; i++) { const d = new T.Mesh(new T.SphereGeometry(0.05, 16, 8), E.yolkMat); d.scale.set(1, 0.3, 1.4); W.scene.add(d); E.drips.push(d); }
    E.bubbles = []; for (let i = 0; i < 12; i++) E.bubbles.push({ gk: 0, born: 1e9, life: 0, x: 0, z: 0, r: 0.04, h: 0 });
    // ready glow: a warm ring that breathes around the egg while it is in the order's window (reads with the sound off)
    E.halo = new T.Mesh(new T.RingGeometry(0.41, 0.58, 64), new T.MeshBasicMaterial({ color: lin("#ffc23a"), transparent: true, opacity: 0, blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide }));
    E.halo.rotation.x = -Math.PI / 2; W.wok.add(E.halo);
    // the landing shadow: where the egg will come down, so the player can slide the wok under it
    E.shadow = new T.Mesh(new T.CircleGeometry(0.41, 40), new T.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0, depthWrite: false }));
    E.shadow.rotation.x = -Math.PI / 2; W.scene.add(E.shadow);
    hide();
  }
  E.build = build;
  function blister(b, t) { const a = Math.random() * TAU, tt = rnd(0.3, 0.9), r = tt * E.ER(a); Object.assign(b, { x: Math.cos(a) * r, z: Math.sin(a) * r, r: rnd(0.03, 0.06), h: rnd(0.012, 0.026), born: t + rnd(0, 1.2), life: rnd(1.0, 2.0) }); }
  function hide() { E.g.visible = E.oil.visible = E.fizz.visible = E.spat.visible = E.pud.visible = false; E.halo.material.opacity = 0; E.shadow.material.opacity = 0; for (const d of E.drips) d.visible = false; E.ph = "off"; }
  E.hide = hide;

  /* ---------- a new egg ---------- */
  E.start = function (t) {
    E.g.scale.setScalar(1);
    Object.assign(E, { ph: "drop", t0: t, cook: 0, waves: [], flip: null, flipped: false, burst: false, fold: 0, bend: 0, sx: 0, sz: 0, svx: 0, svz: 0, ox: 0, oz: 0.05,
      y: { x: 0, z: 0, h: 0, vx: 0, vz: 0, vh: 0 }, sq: { s: 0, v: 0 }, res: null, onPlate: false, onCounter: false, take: null, snap: null, cook2: 0, nFlip: 0 });
    for (const b of E.bubbles) blister(b, t);
    E.g.visible = E.oil.visible = E.fizz.visible = E.spat.visible = true; E.pud.visible = false; for (const d of E.drips) d.visible = false;
  };
  function jolt(t, a, yk) { E.waves.push({ t0: t, a }); E.y.vx += rnd(-1, 1) * yk; E.y.vz += rnd(-1, 1) * yk; E.y.vh += yk * 0.4; }
  E.jolt = jolt;
  function spatter(n) {
    let c = 0; for (const d of E.drops) { if (d.life > 0) continue; const a = Math.random() * TAU, r = E.ER(a) * rnd(0.8, 1.1);
      d.p.set(Math.cos(a) * r, 0.03, Math.sin(a) * r); E.g.localToWorld(d.p); d.v.set(Math.cos(a) * rnd(0.6, 1.6), rnd(1.2, 2.6), Math.sin(a) * rnd(0.6, 1.6)); d.life = rnd(0.25, 0.5); if (++c >= n) break; }
  }
  E.spatter = spatter;


  /* the flip: ONE knob — the flick's strength sets height, airtime and spin; its side component drifts the egg */
  E.flipStart = function (f, t) {
    if (f.second && E.ph === "landed") { E.sx = E.ox; E.sz = E.oz - 0.05; } // flips again from where it lies
    else if (E.ph !== "pan") return false;
    const s = clamp(f.strength || 1, 0.3, 1.9), hop = s < 0.45; // too soft: it only jumps, try again (it keeps frying)
    const rise = hop ? 0.1 : clamp(0.22 + 0.5 * (s - 0.35), 0.16, 1.0), vy = Math.sqrt(2 * GE * rise), dur = 2 * vy / GE;
    // a half turn for any honest flick (0.5..1.5 lands within 34 deg, 0.83..1.17 within 12 = perfect)
    const spin = hop ? 0 : Math.PI + clamp(s - 1, -0.6, 0.6) * 1.2;
    const W = K.W; _v.set(E.sx, 0, 0.05 + E.sz).applyMatrix4(W.wok.matrixWorld);
    // where it comes down: a little random, and a harder flick carries it further (-z): sliding the wok under it is the skill
    const drift = f.second || hop ? { x: rnd(-0.06, 0.06), z: rnd(-0.05, 0.05) } : { x: (f.dirU || 0) * 0.3 + rnd(-0.22, 0.22), z: -(s - 1) * 0.15 + rnd(-0.12, 0.1) };
    E.flip = { t0: t, s, rise, vy, dur, spin, hop, second: !!f.second, p0: _v.clone(), drift, f: 0, cook: E.cook, cook2: E.cook2, base: E.flipped ? Math.PI : 0 };
    E.ph = "air"; jolt(t, 0.4, 0.8);
    return true;
  };
  // where the shadow is: the landing spot, so the player can steer the wok under it
  E.cue = function (t, ready) { // 0..1: how ready for the order; drawn as the breathing ring
    E.halo.position.set(E.sx, surf(E.sx * E.sx + (0.05 + E.sz) ** 2) + 0.012, 0.05 + E.sz);
    E.halo.material.opacity = E.ph === "pan" ? ready * (0.35 + 0.25 * Math.sin(t * 7)) : 0;
    const fl = E.flip;
    if (E.ph === "air" && fl) {
      _v.set(fl.p0.x + fl.drift.x, 0, fl.p0.z + fl.drift.z); _inv.copy(K.W.wok.matrixWorld).invert(); const w = _v.clone().applyMatrix4(_inv);
      const r2 = w.x * w.x + w.z * w.z; w.y = r2 < 1 ? surf(r2) + 0.01 : -0.6; w.applyMatrix4(K.W.wok.matrixWorld);
      E.shadow.position.copy(w); E.shadow.position.y = r2 < 1 ? w.y + 0.005 : -0.6; E.shadow.material.opacity = 0.15 + 0.3 * fl.f; E.shadow.scale.setScalar(0.7 + 0.3 * fl.f);
    } else E.shadow.material.opacity = 0;
  };
  E.target = function () { const fl = E.flip; return fl ? { x: fl.p0.x + fl.drift.x, z: fl.p0.z + fl.drift.z, f: fl.f } : null; };

  /* ---------- per step ---------- */
  E.step = function (ctx) {
    if (E.ph === "off" || E.ph === "plate" || E.ph === "counter") return null;
    const t = ctx.t, dt = ctx.dt, W = K.W, wm = W.wok.matrixWorld;
    let event = null;
    if (E.ph === "drop" || E.ph === "pan") {
      const k = t - E.t0;
      // hot oil: the egg slides toward the low side of the wok from the start, then drifts back (operator 10-24)
      _q.setFromRotationMatrix(wm).invert(); _v.set(0, -1, 0).applyQuaternion(_q);
      const g = 2.6;
      E.svx += (_v.x * g - 5 * E.sx) * dt; E.svz += (_v.z * g - 5 * E.sz) * dt;
      E.svx *= Math.exp(-2.2 * dt); E.svz *= Math.exp(-2.2 * dt);
      E.sx = clamp(E.sx + E.svx * dt, -0.4, 0.4); E.sz = clamp(E.sz + E.svz * dt, -0.35, 0.3);
      E.ox = E.sx; E.oz = 0.05 + E.sz;
      _v.set(E.ox, 0, E.oz).applyMatrix4(wm); E.g.position.copy(_v);
      W.wok.getWorldQuaternion(E.g.quaternion);
      if (k < 2.2 && Math.random() < dt * 14 * (1 - k / 2.2)) spatter(1);
      E.cook = Math.min(1.6, E.cook + dt * 0.13 * ctx.heat);
      if (E.ph === "drop") {
        if (k < 0.32) { const f = k / 0.32; E.g.position.y += 0.7 * (1 - f * f); }
        else { E.ph = "pan"; jolt(t, 0.9, 1.2); E.sq.v = 9; spatter(14); event = "plop"; }
      }
    } else if (E.ph === "air") {
      const fl = E.flip, tau = t - fl.t0 - 0.1; // the wok's lip snap comes first
      if (tau < 0) fl.p0.set(E.sx, 0, 0.05 + E.sz).applyMatrix4(wm);
      fl.f = clamp(tau / fl.dur, 0, 1);
      const f = fl.f;
      E.g.position.set(fl.p0.x + fl.drift.x * f, fl.p0.y + Math.max(0, fl.vy * Math.max(0, tau) - 0.5 * GE * Math.max(0, tau) ** 2), fl.p0.z + fl.drift.z * f - 0.12 * Math.sin(Math.PI * f));
      const rot = fl.base + fl.spin * (0.5 - 0.5 * Math.cos(Math.PI * f)); // S-curve: edge-on near the apex
      W.wok.getWorldQuaternion(E.g.quaternion); E.g.quaternion.multiply(_qx.setFromAxisAngle(_ax, rot));
      E.bend = 0.16 * Math.sin(Math.PI * f) * (fl.s > 1.3 ? 1.5 : 1);
      if (f >= 1) event = land(t);
    } else if (E.ph === "landed") {
      _v.set(E.ox, 0, E.oz).applyMatrix4(wm); E.g.position.copy(_v);
      W.wok.getWorldQuaternion(E.g.quaternion); if (E.flipped) E.g.quaternion.multiply(_qx.setFromAxisAngle(_ax, Math.PI));
      if (E.snap) { const k = (t - E.snap.t) / 0.15; if (k < 1) E.g.quaternion.multiply(_qx.setFromAxisAngle(_ax, E.snap.a * (1 - smooth(k)))); else E.snap = null; }
      E.cook = Math.min(1.6, E.cook + dt * 0.04);
      if (E.nFlip === 1) E.cook2 = Math.min(1.2, E.cook2 + dt * 0.2); // side 2 fries: its own meter
    }
    E.g.updateMatrixWorld(true);
    if (E.burst) burstStep(t, wm);
    shape(dt, t);
    return event;
  };

  /* landing: where it fell relative to the wok (the catch), how far it turned (the flip), how hard (the yolk) */
  function land(t) {
    const fl = E.flip, W = K.W;
    if (fl.hop) { E.ph = fl.second ? "landed" : "pan"; E.landT = t; jolt(t, 0.6, 0); spatter(6); return "hop"; }
    _inv.copy(W.wok.matrixWorld).invert(); _v.copy(E.g.position).applyMatrix4(_inv);
    const d = Math.hypot(_v.x, _v.z - 0.05), turn = fl.spin, err = Math.abs((((turn % TAU) + TAU) % TAU) - Math.PI), errDeg = (err * 180) / Math.PI;
    const raw = fl.cook < 0.42;
    let catchQ, where;
    if (d < 0.22) { catchQ = 1; where = "centre"; } else if (d < 0.45) { catchQ = 0.45; where = "near"; } else if (d < 0.86) { catchQ = 0.35; where = "wall"; } else { catchQ = 0; where = "out"; }
    const flipped = errDeg < 70 && turn > Math.PI * 0.6;
    const fold = !flipped || errDeg > 40 || raw ? 1 : 0;
    const burst = fl.s > 1.6 || (where === "wall" && fl.s > 1.4) || raw;
    E.res = { d, where, catchQ, errDeg: Math.round(errDeg), angle: Math.round(180 - errDeg), flipped, fold, burst, raw, cookAtFlip: fl.second ? fl.cook2 : fl.cook, s: fl.s };
    E.nFlip++;
    E.flipped = fl.base ? !flipped : flipped; E.fold = fold; E.bend = 0;
    const rest = E.flipped ? Math.PI : 0, res = ((((fl.base + fl.spin - rest) % TAU) + TAU + Math.PI) % TAU) - Math.PI;
    E.snap = Math.abs(res) < 1.25 ? { t, a: res } : null; // it settles flat over 0.15 s instead of jumping
    if (where === "out") { // off the wok: onto the counter
      E.onCounter = true; E.ph = "counter"; E.ox = 0; E.oz = 0;
      E.g.position.y = -0.6; E.g.quaternion.setFromAxisAngle(_ax, flipped ? Math.PI : 0); E.g.updateMatrixWorld(true);
      E.oil.visible = E.fizz.visible = false; jolt(t, 1.8, 0); E.sq.v = 14;
      return "counter";
    }
    const k = where === "wall" ? 0.55 : 1; E.ox = _v.x * k; E.oz = _v.z * k + (1 - k) * 0.05; // a wall landing slides down
    E.ph = "landed"; E.landT = t; E.sq.v = burst ? 16 : 8; jolt(t, burst ? 1.8 : where === "wall" ? 1.3 : 0.9, 0); spatter(burst ? 20 : 10);
    if (burst) { E.burst = true; E.burstT = t; }
    return burst ? "burst" : fold ? "fold" : "clean";
  }
  function burstStep(t, wm) {
    const b = clamp((t - E.burstT) / 1.6, 0, 1), bb = Math.sqrt(b); // runny yolk: fast first, then slow like honey
    _v.set(YU, E.flipped ? -0.014 - drape(YU, YV, true) - 0.03 : drape(YU, YV, false) + 0.03, YV); E.g.localToWorld(_v);
    E.pud.visible = true; E.pud.position.set(_v.x, _v.y + 0.022, _v.z + 0.05 * bb);
    if (E.onPlate) E.pud.quaternion.identity(); else K.W.wok.getWorldQuaternion(E.pud.quaternion);
    E.pud.scale.set(0.3 + 1.0 * bb, 1, 0.3 + 1.3 * bb);
    if (E.onPlate || E.onCounter) { for (const d of E.drips) d.visible = false; return; }
    E.drips.forEach((d, i) => { const a = 0.6 + i * 0.5, rr = 0.55 + 0.1 * i; _v.set(E.ox + Math.cos(a) * rr * 0.5, 0, E.oz + Math.sin(a) * rr); _v.applyMatrix4(wm);
      d.visible = b > 0.15 * (i + 1); d.position.set(_v.x, _v.y + 0.01, _v.z); d.scale.set(1 + i * 0.3, 0.3, 1.2 + bb); });
  }

  /* take-out: the wok rolls toward the plate and the egg slides over the lip onto the rice */
  E.takeStep = function (t, k, plateTop) {
    const W = K.W;
    if (!E.take) { E.take = { p0: E.g.position.clone() }; E.cook = Math.max(E.cook, 1.0); } // the top has set by now (it read as jelly)
    const lip = _v.set(K.SIDE * 0.9, K.RIM + 0.05, 0.05).applyMatrix4(W.wok.matrixWorld).clone();
    const flipQ = new T.Quaternion().setFromAxisAngle(_ax, E.flipped ? Math.PI : 0), upQ = new T.Quaternion();
    if (k < 0.45) { E.g.position.lerpVectors(E.take.p0, lip, smooth(k / 0.45)); W.wok.getWorldQuaternion(E.g.quaternion); E.g.quaternion.multiply(flipQ); }
    else { const f = clamp((k - 0.45) / 0.55, 0, 1); E.g.position.lerpVectors(lip, _v.copy(plateTop).setY(plateTop.y + K.Food.plateMound(0) + 0.035), f); /* onto the top of the heap, not into it */ E.g.position.y += 0.3 * Math.sin(Math.PI * f); W.wok.getWorldQuaternion(_q); _q.multiply(flipQ); E.g.quaternion.copy(_q).slerp(upQ, smooth(f)); if (f > 0.5 && E.flipped) E.flipped = false; }
    // on the plate the egg shrinks a little so the rice shows around it
    if (k >= 1 && !E.onPlate) { E.onPlate = true; E.g.scale.setScalar(PS); E.flipped = false; E.ox = E.oz = 0; E.g.position.copy(plateTop); E.g.quaternion.copy(upQ); jolt(t, 1.2, 0); E.sq.v = 14; E.ph = "plate"; E.oil.visible = E.fizz.visible = false; for (const d of E.drips) d.visible = false; }
    E.g.updateMatrixWorld(true);
    if (E.burst) burstStep(t, W.wok.matrixWorld);
    shape(1 / 60, t);
  };
  E.idle = function (t, dt) { if (E.ph === "plate" || E.ph === "counter") { if (E.burst) burstStep(t, K.W.wok.matrixWorld); shape(dt, t); } };

  /* ---------- the live shape ---------- */
  function drape(x, z, under) {
    if (E.onPlate) return (K.Food.plateMound(Math.hypot(x, z) * PS) + 0.035) / PS; // egg-local units: the group is scaled PS on the plate
    if (E.onCounter) return 0;
    return under ? surf((x + E.ox) ** 2 + (E.oz - z) ** 2) : surf((x + E.ox) ** 2 + (z + E.oz) ** 2);
  }
  function shape(dt, tNow) {
    const e = E, P = e.top.geometry.attributes.position.array, Cc = e.top.geometry.attributes.color.array;
    const Pb = e.bot.geometry.attributes.position.array, Cb = e.bot.geometry.attributes.color.array, A = e.top.geometry.attributes.aSet.array;
    e.waves = e.waves.filter((w) => tNow - w.t0 < 2);
    const cookK = clamp(e.cook, 0, 1);
    for (const b of e.bubbles) {
      const a = tNow - b.born; b.gk = a < 0 ? 0 : smooth(a / 0.3) * (1 - smooth((a - b.life + 0.5) / 0.5));
      if (a > b.life) { if (e.cook > 0.15 && e.cook < 1.05 && e.ph === "pan") blister(b, tNow); else b.gk = 0; }
    }
    for (let k = 0; k < e.N; k++) {
      const x = e.vx[k], t = e.vt[k], d = e.vd[k], n = e.vn[k]; let z = e.vz[k];
      const set = clamp((e.cook * 1.2 - (1 - t) * 0.75 - Math.exp(-(((d - 0.15) / 0.09) ** 2)) * 0.25) / 0.3, 0, 1);
      let thick = 0.008 + 0.026 * Math.exp(-(((d - 0.145) / 0.1) ** 2)) + 0.016 * Math.max(0, 1 - d / 0.17);
      thick *= (1 + 0.45 * set) * (t > 0.9 ? 0.4 + 0.6 * (1 - t) / 0.1 : 1);
      let bub = 0;
      for (const b of e.bubbles) { if (b.gk > 0) { const q = ((x - b.x) ** 2 + (z - b.z) ** 2) / (b.r * b.r); if (q < 4) bub += b.h * b.gk * Math.exp(-q); } }
      const lw = 0.1 + 0.25 * clamp((e.cook - 0.45) / 0.9, 0, 1); // the crisp edge widens as it fries: the flip cue
      const lace = t > 1 - lw ? 0.02 * smooth((t - 1 + lw) / lw) * clamp((e.cook - 0.45) * 2.5, 0, 1) * n : 0;
      let wob = 0;
      for (const w of e.waves) { const tau = tNow - w.t0; wob += w.a * 0.014 * (1.25 - 0.55 * cookK) * Math.exp(-(2.6 + 4 * cookK) * tau) * Math.sin(17 * d - (19 + 16 * cookK) * tau) * (1 - 0.4 * t); }
      const bend = -e.bend * z * z * 0.9, sq = 1 - 0.5 * e.sq.s;
      let lift = 0;
      if (e.fold && z > 0) { const rho = 0.05, a = z / rho; if (a < Math.PI) { lift = rho * (1 - Math.cos(a)); z = rho * Math.sin(a); } else { lift = 2 * rho; z = -(z - Math.PI * rho); } } // folded: the near half rolls over a rounded crease
      const j = k * 3;
      P[j] = x * (1 + 0.12 * e.sq.s); P[j + 2] = z * (1 + 0.12 * e.sq.s);
      P[j + 1] = drape(P[j], P[j + 2], false) + (thick + bub) * sq + lace + wob + bend + lift;
      A[k] = set; _c.copy(C_RAW).lerp(C_SET, set);
      // the edge: thin gold from ~0.45 (반숙 window from 0.5), wider and brown by ~0.85 (완숙), black past 1.15
      const br = clamp((e.cook - 0.4) * 2.2, 0, 1) * smooth((t - 1 + lw) / Math.max(0.06, lw * 0.6)) * (0.8 + 0.35 * n);
      if (br > 0) { _c.lerp(C_GOLD, Math.min(1, br * 1.4)); _c.lerp(C_BROWN, clamp((e.cook - 0.75) * 1.8, 0, 0.85) * smooth((t - 0.92 + 0.1 * lw) / 0.1)); }
      const bt = clamp((e.cook - 1.15) / 0.3, 0, 1); if (bt > 0) _c.lerp(C_BURNT, bt * smooth((t - 0.55) / 0.4) * 0.9);
      Cc[j] = _c.r; Cc[j + 1] = _c.g; Cc[j + 2] = _c.b;
      const dome = 0.07 * Math.exp(-((d / 0.13) ** 2)) * (e.burst ? 0.35 : 1);
      Pb[j] = P[j]; Pb[j + 2] = P[j + 2];
      Pb[j + 1] = -0.014 - drape(P[j], P[j + 2], true) - dome * (1 - 0.3 * e.sq.s) - wob * 0.8 - bend - lace * 0.5 - lift;
      const under = clamp(e.cook * 1.25, 0.3, 1.4);
      // the fried side: white with golden blotches and a crisp brown lace at the edge (an even tan read as a pancake)
      _c.copy(C_UNDER).lerp(C_DOME, Math.exp(-((d / 0.125) ** 2)) * 0.7).lerp(C_GOLD, clamp((n - 0.45) * 1.6 * under, 0, 0.8))
        .lerp(C_BROWN, clamp(smooth((t - 0.62) / 0.38) * (0.6 + 0.6 * n) * under, 0, 0.9));
      Cb[j] = _c.r; Cb[j + 1] = _c.g; Cb[j + 2] = _c.b;
    }
    for (const m of [e.top, e.bot]) { const g = m.geometry; g.attributes.position.needsUpdate = true; g.attributes.color.needsUpdate = true; }
    if (!e.flipped || e.ph === "air") e.top.geometry.computeVertexNormals();
    if (e.flipped || e.ph === "air") e.bot.geometry.computeVertexNormals();
    e.top.geometry.attributes.aSet.needsUpdate = true;
    const fzK = e.ph === "air" ? 0 : clamp(1.2 - e.cook * 0.6, 0.3, 1);
    e.fz.forEach((f, i) => { const sc = f.s * fzK * Math.max(0, Math.sin(tNow * f.sp + f.ph)); const fx = e.sx + Math.cos(f.a) * f.r, fzz = 0.05 + e.sz + Math.sin(f.a) * f.r;
      dummy.position.set(fx, surf(fx * fx + fzz * fzz) + 0.002, fzz); dummy.rotation.set(0, 0, 0); dummy.scale.set(sc, sc * 0.7, sc); dummy.updateMatrix(); e.fizz.setMatrixAt(i, dummy.matrix); });
    e.fizz.instanceMatrix.needsUpdate = true; e.oil.position.set(e.sx, 0, 0.05 + e.sz);
    const sp = e.spat.geometry.attributes.position.array;
    e.drops.forEach((d, i) => { d.life -= dt; if (d.life > 0) { d.v.y -= 12 * dt; d.p.addScaledVector(d.v, dt); }
      const o = i * 6; if (d.life > 0) { sp[o] = d.p.x; sp[o + 1] = d.p.y; sp[o + 2] = d.p.z; sp[o + 3] = d.p.x - d.v.x * 0.025; sp[o + 4] = d.p.y - d.v.y * 0.025; sp[o + 5] = d.p.z - d.v.z * 0.025; } else sp.fill(0, o, o + 6); });
    e.spat.geometry.attributes.position.needsUpdate = true;
    const Y = e.y;
    for (const c of ["x", "z", "h"]) { const vk = "v" + c; Y[vk] += (-140 * Y[c] - 7 * Y[vk]) * dt; Y[c] += Y[vk] * dt; }
    e.sq.v += (-260 * e.sq.s - 14 * e.sq.v) * dt; e.sq.s += e.sq.v * dt;
    const ys = 1 - 0.35 * e.sq.s + Y.h * 4;
    e.yolk.position.set(YU + Y.x, drape(YU, YV, false) + 0.03 * (1 + 0.45 * clamp(e.cook, 0, 1)), YV + Y.z);
    e.yolk.scale.set(1 + 0.25 * e.sq.s - Y.h * 2, clamp(0.5 * ys, 0.25, 0.7), 1 + 0.25 * e.sq.s - Y.h * 2);
    e.yolkMat.color.copy(lin("#ffa412")).lerp(lin("#ffd98a"), clamp((e.cook - 0.82) * 1.2, 0, 0.6));
    e.yolk.visible = !(e.ph === "air" ? !!e.flip.base !== e.flip.f > 0.5 : e.flipped) && !e.fold && !e.burst;
  }
})();
