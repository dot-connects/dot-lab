/* scene.js — three.js world: renderer, camera fit, two looks (실사 / 장난감), counter, burner, wok, fire, plate.
   Units: wok radius = 1 (≈18 cm). Axes: x right, y up, z toward the cook (handle side); the far rim is -z. */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  const T = THREE, TAU = Math.PI * 2, DEG = Math.PI / 180;
  const lin = (h) => new T.Color(h).convertSRGBToLinear();
  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v), smooth = (k) => { k = clamp(k, 0, 1); return k * k * (3 - 2 * k); };
  const rnd = (a, b) => a + Math.random() * (b - a);
  const surf = (r2) => 0.35 * r2;   // wok floor height at radius² (rim at r = 1, h = .35)
  Object.assign(K, { T, TAU, DEG, lin, clamp, smooth, rnd, surf, RIM: 0.355, SIDE: -1 /* the plate is on the left */ });

  const STYLES = {
    real: { bg: "#1b1f24", counter: "steel", exposure: 1.05, grain: [0.038, 0.017], grainN: 2400, phys: true },
    toy: { bg: "#2f3b66", counter: "pastel", exposure: 1.0, grain: [0.06, 0.03], grainN: 1400, chunky: true },
  };

  /* ---------- renderer / camera ---------- */
  const canvas = document.getElementById("cv");
  const renderer = new T.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
  renderer.outputEncoding = T.sRGBEncoding;
  renderer.shadowMap.enabled = true; renderer.shadowMap.type = T.PCFSoftShadowMap;
  const camera = new T.PerspectiveCamera(30, 1, 0.1, 60);
  const camBase = new T.Vector3(), camLook = new T.Vector3(0, -0.05, 0.35);
  function fit() {
    const w = canvas.clientWidth || innerWidth, h = canvas.clientHeight || innerHeight, asp = w / h;
    renderer.setSize(w, h, false); camera.aspect = asp;
    // any aspect: the wok + handle fill ~88% of the width on a tall phone, and never more than ~57% of the height
    const half = Math.tan((camera.fov / 2) * DEG), needW = 1.32 / (half * asp), needH = 1.75 / half;
    const d = Math.max(needW, needH), a = 35 * DEG; // 35° away from straight down, from the cook's side
    camBase.set(0, d * Math.cos(a), d * Math.sin(a) + 0.35);
    camera.position.copy(camBase); camera.lookAt(camLook); camera.updateProjectionMatrix();
  }

  /* ---------- canvas textures ---------- */
  function canvasTex(w, h, draw, repeat) {
    const cv = document.createElement("canvas"); cv.width = w; cv.height = h; draw(cv.getContext("2d"), w, h);
    const t = new T.CanvasTexture(cv); t.encoding = T.sRGBEncoding; t.anisotropy = 4;
    if (repeat) { t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(repeat, repeat); }
    return t;
  }
  const flameTex = canvasTex(64, 128, (c, w, h) => { // teardrop tongue: blue root, orange body, the tip fades out
    const g = c.createLinearGradient(0, h, 0, 0);
    g.addColorStop(0, "rgba(90,150,255,0)"); g.addColorStop(0.1, "rgba(90,150,255,.9)"); g.addColorStop(0.25, "rgba(255,190,90,.95)");
    g.addColorStop(0.55, "rgba(255,120,30,.7)"); g.addColorStop(1, "rgba(255,70,0,0)");
    c.fillStyle = g; c.filter = "blur(3px)"; c.beginPath(); c.moveTo(w / 2, 4);
    c.bezierCurveTo(w * 0.62, h * 0.35, w * 0.9, h * 0.7, w / 2, h - 4); c.bezierCurveTo(w * 0.1, h * 0.7, w * 0.38, h * 0.35, w / 2, 4); c.fill();
  });
  const heiTex = canvasTex(64, 128, (c, w, h) => { // wok hei: a yellow-orange tongue with no blue root
    const g = c.createLinearGradient(0, h, 0, 0);
    g.addColorStop(0, "rgba(255,230,140,0)"); g.addColorStop(0.12, "rgba(255,226,120,1)"); g.addColorStop(0.45, "rgba(255,150,40,.85)"); g.addColorStop(1, "rgba(255,80,10,0)");
    c.fillStyle = g; c.filter = "blur(4px)"; c.beginPath(); c.moveTo(w / 2, 2);
    c.bezierCurveTo(w * 0.7, h * 0.3, w * 0.95, h * 0.72, w / 2, h - 4); c.bezierCurveTo(w * 0.05, h * 0.72, w * 0.3, h * 0.3, w / 2, 2); c.fill();
  });
  const puffTex = canvasTex(64, 64, (c, w) => {
    const g = c.createRadialGradient(w / 2, w / 2, 0, w / 2, w / 2, w / 2);
    g.addColorStop(0, "rgba(255,255,255,.9)"); g.addColorStop(0.5, "rgba(255,255,255,.35)"); g.addColorStop(1, "rgba(255,255,255,0)");
    c.fillStyle = g; c.fillRect(0, 0, w, w);
  });
  const sparkTex = canvasTex(32, 32, (c, w) => {
    const g = c.createRadialGradient(w / 2, w / 2, 0, w / 2, w / 2, w / 2);
    g.addColorStop(0, "rgba(255,250,220,1)"); g.addColorStop(0.35, "rgba(255,190,80,.8)"); g.addColorStop(1, "rgba(255,120,20,0)");
    c.fillStyle = g; c.fillRect(0, 0, w, w);
  });
  function counterTex(kind) {
    if (kind === "steel") return canvasTex(512, 512, (c, w, h) => {
      c.fillStyle = "#6f777e"; c.fillRect(0, 0, w, h);
      for (let i = 0; i < 2600; i++) { const y = Math.random() * h, v = 90 + Math.random() * 70; c.strokeStyle = `rgba(${v},${v + 6},${v + 10},.25)`; c.lineWidth = Math.random() * 1.4; c.beginPath(); c.moveTo(0, y); c.lineTo(w, y + rnd(-2, 2)); c.stroke(); }
    }, 3);
    return canvasTex(256, 256, (c, w, h) => { c.fillStyle = "#46568f"; c.fillRect(0, 0, w, h); c.fillStyle = "#3f4e86"; for (let i = 0; i < 8; i++) c.fillRect(0, i * 32, w, 16); }, 3);
  }

  /* ---------- helpers shared by food / egg ---------- */
  function mat(S, hex, o = {}) {
    const color = lin(hex);
    if (S.phys && o.phys) return new T.MeshPhysicalMaterial(Object.assign({ color }, o.phys));
    return new T.MeshStandardMaterial(Object.assign({ color, roughness: 0.55, metalness: 0 }, o.std || {}));
  }
  function polarDisc(R, rings, segs, height, colorAt, flip) { // a disc whose edge follows R(θ)
    const pos = [], col = [], idx = [];
    for (let i = 0; i <= rings; i++) for (let j = 0; j < segs; j++) {
      const t = i / rings, th = (j / segs) * TAU, r = t * R(th), x = Math.cos(th) * r, z = Math.sin(th) * r;
      pos.push(x, height(t, th, r), z); const c = colorAt(t, th); col.push(c.r, c.g, c.b);
    }
    for (let i = 0; i < rings; i++) for (let j = 0; j < segs; j++) {
      const a = i * segs + j, b = i * segs + ((j + 1) % segs), c2 = a + segs, d = b + segs;
      if (flip) idx.push(a, c2, b, b, c2, d); else idx.push(a, b, c2, b, d, c2);
    }
    const g = new T.BufferGeometry(); g.setAttribute("position", new T.Float32BufferAttribute(pos, 3));
    g.setAttribute("color", new T.Float32BufferAttribute(col, 3)); g.setIndex(idx); g.computeVertexNormals(); return g;
  }

  /* ---------- the wok pose (owned by game.js): translation + pitch/roll about the rim opposite the motion ---------- */
  const pose = { x: 0, y: 0, z: 0, pitch: 0, roll: 0, pz: 0.95 };

  const W = (K.W = { renderer, camera, camBase, camLook, fit, canvasTex, mat, polarDisc, pose, STYLES, S: null, key: "real" });

  function build(key) {
    const S = (W.S = STYLES[key]); W.key = key;
    if (W.scene) W.scene.traverse((o) => { if (o.geometry) o.geometry.dispose(); });
    const scene = (W.scene = new T.Scene()); scene.background = lin(S.bg);
    renderer.toneMapping = S.phys ? T.ACESFilmicToneMapping : T.NoToneMapping; renderer.toneMappingExposure = S.exposure;
    // reflections: a dim room with two soft windows and the burner's glow
    const env = new T.Scene(); env.background = lin(S.phys ? "#262a2e" : S.bg);
    const win = (x, y, z, w, h, c) => { const m = new T.Mesh(new T.PlaneGeometry(w, h), new T.MeshBasicMaterial({ color: lin(c), side: T.DoubleSide })); m.position.set(x, y, z); m.lookAt(0, 0, 0); env.add(m); };
    win(-3, 4, 2, 4, 2, "#fff6e6"); win(4, 3, -2, 3, 3, "#ffd7a8"); win(0, -3, 0, 6, 6, "#ff7a2a");
    const pm = new T.PMREMGenerator(renderer); scene.environment = pm.fromScene(env, 0.04).texture; pm.dispose();

    scene.add(new T.HemisphereLight(lin("#e8eef5"), lin("#6a4a30"), S.phys ? 0.35 : 0.5));
    const key0 = new T.DirectionalLight(lin("#fff1dc"), S.phys ? 1.6 : 0.85); key0.position.set(-2.2, 5, 1.5);
    key0.castShadow = true; key0.shadow.mapSize.set(1024, 1024); key0.shadow.bias = -0.0008; key0.shadow.radius = 4;
    Object.assign(key0.shadow.camera, { left: -3, right: 3, top: 3, bottom: -3, near: 1, far: 12 }); scene.add(key0);
    W.fireLight = new T.PointLight(lin("#ff8a2a"), 2.2, 4.5, 2); W.fireLight.position.set(0, -0.35, 0); scene.add(W.fireLight);

    const counter = new T.Mesh(new T.PlaneGeometry(16, 16), new T.MeshStandardMaterial({ map: counterTex(S.counter), roughness: S.counter === "steel" ? 0.35 : 0.8, metalness: S.counter === "steel" ? 0.6 : 0 }));
    counter.rotation.x = -Math.PI / 2; counter.position.y = -0.62; counter.receiveShadow = true; scene.add(counter);
    const ring = new T.Mesh(new T.TorusGeometry(0.62, 0.07, 10, 48), mat(S, "#2a2c2e", { std: { metalness: 0.6, roughness: 0.4 } }));
    ring.rotation.x = Math.PI / 2; ring.position.y = -0.56; scene.add(ring);
    const hole = new T.Mesh(new T.CircleGeometry(0.58, 40), new T.MeshBasicMaterial({ color: lin("#120b08") })); hole.rotation.x = -Math.PI / 2; hole.position.y = -0.6; scene.add(hole);

    // wok: lathe profile (inner bowl, rolled lip, outer skin), long wooden handle toward the cook, small ear at the far side
    const world = (W.world = new T.Group()); scene.add(world);
    const pivot = (W.pivot = new T.Group()); world.add(pivot);
    const wok = (W.wok = new T.Group()); pivot.add(wok);
    const prof = [];
    for (let i = 0; i <= 40; i++) { const r = i / 40; prof.push(new T.Vector2(Math.max(0.001, r), surf(r * r))); }
    prof.push(new T.Vector2(1.03, 0.365), new T.Vector2(1.055, 0.35), new T.Vector2(1.04, 0.33));
    for (let i = 40; i >= 0; i--) { const r = (i / 40) * 1.02; prof.push(new T.Vector2(Math.max(0.001, r), surf(r * r) - 0.03)); }
    const wokMat = S.phys
      ? new T.MeshPhysicalMaterial({ color: lin("#3a4048"), metalness: 0.85, roughness: 0.3, clearcoat: 0.5, clearcoatRoughness: 0.2, envMapIntensity: 1.3 })
      : new T.MeshStandardMaterial({ color: lin("#4a5d78"), roughness: 0.42, metalness: 0.25 });
    wokMat.side = T.DoubleSide;
    const bowl = new T.Mesh(new T.LatheGeometry(prof, 96), wokMat); bowl.castShadow = true; bowl.receiveShadow = true; wok.add(bowl);
    const steel = mat(S, "#9aa3ab", { std: { metalness: 0.9, roughness: 0.3 }, phys: { metalness: 1, roughness: 0.28 } });
    const sock = new T.Mesh(new T.CylinderGeometry(0.04, 0.05, 0.42, 16), steel); sock.rotation.x = Math.PI / 2 - 12 * DEG; sock.position.set(0, 0.36, 1.2); wok.add(sock);
    const wood = mat(S, S.chunky ? "#c9773a" : "#7b4a2a", { std: { roughness: 0.6 }, phys: { roughness: 0.55, clearcoat: 0.3 } });
    const handle = new T.Mesh(new T.CylinderGeometry(0.06, 0.07, 1.0, 20), wood); handle.rotation.x = Math.PI / 2 - 12 * DEG; handle.position.set(0, 0.5, 1.88); handle.castShadow = true; wok.add(handle);
    const ear = new T.Mesh(new T.TorusGeometry(0.09, 0.022, 8, 20, Math.PI), steel); ear.position.set(0, 0.36, -1.06); ear.rotation.set(-Math.PI / 2, 0, 0); wok.add(ear);

    // burner flames: short tongues on a ring just outside the wok wall
    W.flames = [];
    for (let i = 0; i < 40; i++) {
      const sp = new T.Sprite(new T.SpriteMaterial({ map: flameTex, blending: T.AdditiveBlending, depthWrite: false, transparent: true, color: lin(S.phys ? "#ff9a50" : "#ffc890") }));
      sp.userData = { a: (i / 40) * TAU + rnd(-0.05, 0.05), r: rnd(0.9, 1.06), ph: Math.random() * TAU, s: rnd(0.7, 1.2) };
      scene.add(sp); W.flames.push(sp);
    }
    // wok hei: big yellow tongues over the far lip + sparks (a great toss)
    W.hei = [];
    for (let i = 0; i < 9; i++) { const sp = new T.Sprite(new T.SpriteMaterial({ map: heiTex, blending: T.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0 })); sp.userData = { a: (i - 4) * 0.16, s: rnd(0.8, 1.2), ph: rnd(0, TAU) }; scene.add(sp); W.hei.push(sp); }
    W.sparks = [];
    for (let i = 0; i < 40; i++) { const sp = new T.Sprite(new T.SpriteMaterial({ map: sparkTex, blending: T.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0 })); sp.userData = { life: 0, p: new T.Vector3(), v: new T.Vector3() }; scene.add(sp); W.sparks.push(sp); }
    // oil smoke: thin wisps from the edge of the pile when the metal under it is hot again (the cook's "now", operator 10-24:
    // a glowing rim is not something a wok does)
    W.wisps = [];
    for (let i = 0; i < 8; i++) { const sp = new T.Sprite(new T.SpriteMaterial({ map: puffTex, transparent: true, depthWrite: false, opacity: 0 })); sp.userData = { a: (i / 8) * Math.PI * 2 + rnd(-0.3, 0.3), t: i * 0.1 }; scene.add(sp); W.wisps.push(sp); }
    W.puffs = [];
    for (let i = 0; i < 14; i++) { const sp = new T.Sprite(new T.SpriteMaterial({ map: puffTex, transparent: true, depthWrite: false, opacity: 0 })); sp.userData = { t: Math.random() * 2 }; scene.add(sp); W.puffs.push(sp); }

    // plate (left of the wok) + ladle
    const pg = (W.plate = new T.Group()); scene.add(pg);
    const pprof = [[0, 0], [0.42, 0], [0.47, 0.012], [0.58, 0.05], [0.68, 0.085], [0.7, 0.09], [0.69, 0.075], [0.56, 0.04], [0.42, 0.008], [0, 0.008]].map(([x, y]) => new T.Vector2(Math.max(0.001, x), y));
    const cer = mat(S, S.chunky ? "#fffaf0" : "#f7f5f0", { std: { roughness: 0.25 }, phys: { roughness: 0.2, clearcoat: 1, clearcoatRoughness: 0.08 } });
    const dish = new T.Mesh(new T.LatheGeometry(pprof, 64), cer); dish.castShadow = dish.receiveShadow = true; pg.add(dish);
    const prim = new T.Mesh(new T.TorusGeometry(0.665, 0.008, 6, 64), mat(S, "#2f5fa8", { std: { roughness: 0.3 } })); prim.rotation.x = Math.PI / 2; prim.position.y = 0.084; pg.add(prim);
    pg.visible = false;
    const lad = (W.ladle = new T.Group()); const lb = new T.Mesh(new T.SphereGeometry(0.14, 20, 10, 0, TAU, Math.PI / 2, Math.PI / 2), steel.clone()); lb.material.side = T.DoubleSide; lad.add(lb);
    const lh = new T.Mesh(new T.CylinderGeometry(0.014, 0.014, 0.8, 8), steel); lh.rotation.z = 1.1 * K.SIDE; lh.position.set(-0.38 * K.SIDE, 0.22, 0); lad.add(lh);
    lad.visible = false; scene.add(lad);
    W.plateC = { x: K.SIDE * 1.32, y: -0.6, z: 0.12 };

    fit();
    if (K.Food) K.Food.build(); if (K.Egg) K.Egg.build();
  }
  W.build = build;

  function applyPose() {
    // pitch about the near (handle-side) rim: the far lip rises, the hand end barely moves
    W.pivot.position.set(pose.x, pose.y, pose.z + pose.pz); W.wok.position.set(0, 0, -pose.pz);
    W.pivot.rotation.set(pose.pitch, 0, pose.roll); W.world.updateMatrixWorld(true);
  }
  W.applyPose = applyPose;

  /* burner, wok hei, sparks, steam: one call per frame */
  const _v = new T.Vector3();
  W.fx = function (t, dt, o) {
    const S = W.S;
    W.fireLight.intensity = (S.phys ? 3 : 2) * (0.85 + 0.15 * Math.sin(t * 31) * Math.sin(t * 17) + 1.4 * o.hei);
    for (const sp of W.flames) {
      const u = sp.userData, a = u.a + Math.sin(t * 2 + u.ph) * 0.05, h = (0.2 + 0.08 * Math.sin(t * 13 + u.ph) + 0.12 * o.lick) * u.s * o.heat;
      sp.position.set(Math.cos(a) * u.r, -0.3 + h * 0.5 + surf(u.r * u.r) * 0.5, Math.sin(a) * u.r + pose.z * 0.5);
      sp.scale.set(0.2 * u.s, h, 1); sp.material.opacity = (S.phys ? 0.45 : 0.6) + 0.2 * Math.sin(t * 21 + u.ph);
    }
    // wok hei: tongues stand on the far lip (they ride the wok), big for o.hei, then shrink
    for (const sp of W.hei) {
      const u = sp.userData, h = o.hei * (0.55 + 0.25 * Math.sin(t * 19 + u.ph)) * u.s;
      _v.set(Math.sin(u.a) * 1.0, K.RIM + h * 0.42, -Math.cos(u.a) * 1.0).applyMatrix4(W.wok.matrixWorld);
      sp.position.copy(_v); sp.scale.set(0.34 * u.s * (0.6 + o.hei * 0.6), Math.max(0.001, h), 1); sp.material.opacity = Math.min(1, o.hei * 1.4);
    }
    for (const sp of W.sparks) {
      const u = sp.userData; if (u.life <= 0) { sp.material.opacity = 0; continue; }
      u.life -= dt; u.v.y -= 2.5 * dt; u.p.addScaledVector(u.v, dt); sp.position.copy(u.p); const s = 0.05 * Math.min(1, u.life * 3); sp.scale.set(s, s, 1); sp.material.opacity = Math.min(1, u.life * 2.5);
    }
    for (const p of W.puffs) {
      const u = p.userData; u.t += dt; if (u.t > 2) { u.t = 0; u.x = rnd(-0.4, 0.4); u.z = rnd(-0.3, 0.3); }
      const k = u.t / 2, px = (u.x || 0) + Math.sin(t + u.t * 3) * 0.05, pz = u.z || 0, top = K.Food && K.Food.pileTop ? K.Food.pileTop(px, pz) : 0.25;
      _v.set(px, top + 0.05 + k * 1.1, pz).applyMatrix4(W.wok.matrixWorld); p.position.copy(_v); p.scale.setScalar(0.25 + k * 0.6);
      // burning: more of the puffs turn into dark smoke as it scorches (it only changed colour before, so it never read)
      const sm = o.smoke || 0, on = W.puffs.indexOf(p) < 4 + 10 * sm;
      p.material.opacity = Math.max(o.steam, on ? Math.min(0.9, sm * 1.1) : 0) * Math.sin(Math.PI * Math.min(1, k * 1.3));
      if (sm > 0) p.scale.setScalar((0.3 + k * 0.8) * (1 + 0.7 * sm));
      p.material.color.setRGB(1 - sm * 0.5, 1 - sm * 0.53, 1 - sm * 0.55); // grey, not black: black smoke vanishes on the dark wok
    }
    for (const p of W.wisps) {
      const u = p.userData; u.t += dt; if (u.t > 0.6) u.t -= 0.6;
      const k = u.t / 0.6, r = o.wispR || 0.5, wv = o.wisp || 0, big = o.wispBig || 1;
      const wx = Math.cos(u.a) * r, wz = Math.sin(u.a) * r * 0.9, top = K.Food && K.Food.pileTop ? K.Food.pileTop(wx * 0.8, wz * 0.8) : surf(r * r) + 0.06; // from the top of the heap, not inside it
      _v.set(wx, top + 0.12 + k * 0.6 * (0.5 + 0.5 * wv) * big, wz).applyMatrix4(W.wok.matrixWorld);
      p.position.copy(_v); p.scale.set((0.1 + 0.16 * k) * big * (1 + 0.8 * (o.wispThick || 0)), (0.45 + 0.35 * k) * big, 1);
      p.material.opacity = Math.min(1, wv * 1.25) * Math.sin(Math.PI * k); if (o.wispCol) p.material.color.copy(o.wispCol);
    }
  };
  // the wisps restart from the metal together, so the moment they rise reads as one event
  W.wispBurst = function () { for (const p of W.wisps) p.userData.t = rnd(0, 0.08); };
  W.sparkBurst = function (n) {
    let c = 0; for (const sp of W.sparks) { const u = sp.userData; if (u.life > 0) continue;
      const a = rnd(-0.7, 0.7); u.p.set(Math.sin(a), K.RIM + 0.05, -Math.cos(a)).applyMatrix4(W.wok.matrixWorld);
      u.v.set(rnd(-0.6, 0.6), rnd(1.2, 2.6), rnd(-0.6, 0.2)); u.life = rnd(0.4, 0.8); if (++c >= n) break; }
  };
})();
