/* fx.js — burner flames, rim flare, oil catch, steam, smoke, oil spatter, screen shake + KFR.Audio (see CONTRACT.md) */
"use strict";
(function () {
  const K = window.KFR, TAU = K.TAU, rnd = K.rnd, clamp = K.clamp;

  /* ---------- sprites: rendered once, only drawImage per frame ---------- */
  const FW = 48, FH = 128; // flame sprite css px; the base sits at the bottom centre
  let SPR = null;

  /* teardrop tongue with a hooked tip; curl < 0 hooks left, > 0 right */
  function flameSprite(curl, blue) {
    const [cv, c] = K.offscreen(FW, FH);
    const bx = FW / 2, by = FH - 3;
    const tongue = (w, h, lift, fill, alpha) => {
      const b = by - lift, tx = bx + curl * w * 0.95, ty = by - h;
      c.globalAlpha = alpha;
      c.beginPath(); c.moveTo(bx, b);
      c.bezierCurveTo(bx - w * 0.68, b + 1, bx - w * (0.5 - curl * 0.25), b - h * 0.5, tx, ty);
      c.bezierCurveTo(bx + w * (0.3 + curl * 0.35), b - h * 0.48, bx + w * 0.68, b + 1, bx, b);
      c.fillStyle = fill; c.fill();
    };
    const h = FH - 8, w = FW * 0.72;
    const g = c.createLinearGradient(0, by, 0, by - h);
    if (blue) { g.addColorStop(0, "rgba(47,107,255,.95)"); g.addColorStop(0.1, "rgba(127,180,255,.95)"); g.addColorStop(0.2, "#FF7A1A"); }
    else { g.addColorStop(0, "rgba(255,122,26,0)"); g.addColorStop(0.18, "#FF7A1A"); }
    g.addColorStop(0.75, "#FF8A24"); g.addColorStop(1, "rgba(255,110,20,.7)");
    c.shadowColor = "rgba(255,110,30,.6)"; c.shadowBlur = 9;
    tongue(w, h, 0, g, 1);
    c.shadowBlur = 0;
    tongue(w * 0.66, h * 0.7, h * 0.11, "#FFC24A", 1);
    c.globalCompositeOperation = "lighter";
    tongue(w * 0.36, h * 0.42, h * 0.15, "#FFF2C2", 0.75);
    return cv;
  }
  function softSprite(r, g, b, a0) {
    const [cv, c] = K.offscreen(64, 64);
    const gr = c.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, `rgba(${r},${g},${b},${a0})`); gr.addColorStop(0.3, `rgba(${r},${g},${b},${a0 * 0.7})`); gr.addColorStop(0.65, `rgba(${r},${g},${b},${a0 * 0.2})`); gr.addColorStop(1, `rgba(${r},${g},${b},0)`);
    c.fillStyle = gr; c.fillRect(0, 0, 64, 64);
    return cv;
  }
  /* smoke: a lumpy billow of overlapping blobs, so it reads as curling smoke, not a steam disc */
  function billowSprite(r, g, b) {
    const [cv, c] = K.offscreen(96, 96);
    const blobs = [[48, 52, 26], [34, 58, 18], [62, 60, 20], [42, 38, 17], [58, 36, 15], [50, 66, 16]];
    for (const [x, y, rr] of blobs) {
      const gr = c.createRadialGradient(x - rr * 0.25, y - rr * 0.3, 0, x, y, rr);
      gr.addColorStop(0, `rgba(${r + 22},${g + 20},${b + 18},.55)`); gr.addColorStop(0.7, `rgba(${r},${g},${b},.35)`); gr.addColorStop(1, `rgba(${r},${g},${b},0)`);
      c.fillStyle = gr; c.beginPath(); c.arc(x, y, rr, 0, TAU); c.fill();
    }
    return cv;
  }
  function sprites() {
    if (SPR) return SPR;
    // smoke shades: light warm grey (just burning) -> art bible #4A4440 -> #2A2624 (charcoal)
    const smoke = [[168, 160, 152], [138, 128, 120], [104, 96, 90], [74, 68, 64]].map((s) => billowSprite(s[0], s[1], s[2]));
    SPR = {
      burner: [-0.32, 0.12, 0.38].map((k) => flameSprite(k, true)),
      lick: [-0.3, 0.3].map((k) => flameSprite(k, false)),
      steam: softSprite(255, 255, 255, 1),
      glow: softSprite(255, 160, 60, 1),
      dot: softSprite(255, 236, 190, 1),
      smoke,
    };
    return SPR;
  }

  /* ---------- state ---------- */
  const N_TONGUE = 16, MAX_STEAM = 36, MAX_SMOKE = 22, MAX_SPAT = 48, HAZE_MAX = 0.15;
  // burner ring in units of R relative to the rim centre (cx, cy); tuned against stage.js wok silhouette
  const RING = { rx: 0.74, ry: 0.15, y: 0.84, ht: 0.7, wd: 0.27, lean: 0.68 };

  function create() {
    const tongues = [];
    for (let i = 0; i < N_TONGUE; i++) {
      const a = (TAU * (i + rnd(-0.3, 0.3))) / N_TONGUE;
      const side = Math.abs(Math.cos(a)); // side tongues are the visible ones: make them the big ones
      tongues.push({ a, size: rnd(0.75, 1.05) + 0.25 * side, spr: i % 3, flip: Math.random() < 0.5,
        f1: rnd(7, 10), f2: rnd(13, 19), p1: rnd(0, TAU), p2: rnd(0, TAU) });
    }
    tongues.sort((p, q) => Math.sin(p.a) - Math.sin(q.a)); // far first
    // near-wall tongues: spread along the lower arc of the wok's outer wall (bezier t), gap where the handle neck is
    const near = [];
    for (let i = 0; i < 16; i++) {
      let t = 0.1 + (0.8 * (i + rnd(0.15, 0.85))) / 16;
      if (Math.abs(t - 0.5) < 0.05) t += t < 0.5 ? -0.05 : 0.05;
      near.push({ t, size: rnd(0.65, 1.15), spr: i % 3, flip: Math.random() < 0.5,
        f1: rnd(8, 11), f2: rnd(14, 20), p1: rnd(0, TAU), p2: rnd(0, TAU) });
    }
    const licks = [];
    for (let i = 0; i < 6; i++) licks.push({ phi: Math.PI * (0.18 + (0.64 * i) / 5) + rnd(-0.1, 0.1), size: rnd(0.6, 1.25),
      spr: i % 2, f1: rnd(9, 13), p1: rnd(0, TAU), delay: rnd(0, 0.3), lean: rnd(-0.25, 0.25) });
    return { t: 0, flare: 0, flareLevel: 0, oil: null, tongues, near, licks, parts: [], spat: [],
      shake: { x: 0, y: 0 }, amp: 0, haze: 0, rimDrawn: false, gap: 0, lick: 0, pose: K.restPose() };
  }

  function kick(s, px) { s.amp = Math.min(4, Math.max(s.amp, px)); }

  function flare(s, strength) {
    const k = clamp((strength || 1) * 0.75, 0.2, 1);
    s.flare = Math.max(s.flare, k);
    kick(s, 1.6 + 0.9 * k);
  }
  function oilCatch(s, strength) {
    s.oil = { t: 0, dur: 0.35, k: clamp(strength || 1, 0.6, 1.4) };
    kick(s, 3);
  }
  /* hot oil pops where food lands; a landing also nudges the screen */
  function spatter(s, u, v, n) {
    n = Math.min(n || 6, 16);
    for (let i = 0; i < n && s.spat.length < MAX_SPAT; i++) {
      const a = rnd(0, TAU), sp = rnd(0.4, 1.4);
      s.spat.push({ u: u + rnd(-0.08, 0.08), v: v + rnd(-0.08, 0.08), h: K.surf(u, v) + 0.03,
        du: Math.cos(a) * sp, dv: Math.sin(a) * sp, dh: rnd(0.8, 2.2), age: rnd(-0.04, 0), life: 0.12, r: rnd(1, 1.5) });
    }
    if (n >= 6) kick(s, Math.min(2, n * 0.15));
  }

  /* a dark curl where rice starts to burn (the lead calls it on the 'burning' event) */
  function burnCue(s, u, v) {
    for (let i = 0; i < 3; i++) {
      if (s.parts.length >= MAX_STEAM + MAX_SMOKE + 6) break;
      s.parts.push({ smoke: true, dark: 0.3, u: u + rnd(-0.05, 0.05), v: v + rnd(-0.05, 0.05), h: K.surf(u, v), pose: s.pose,
        x: 0, y: 0, born: false, vx: rnd(-0.04, 0.04) + (i - 1) * 0.06, vy: rnd(0.95, 1.15), r0: 0.07, r1: rnd(0.26, 0.34),
        age: -i * 0.12, life: 1.3, ph: rnd(0, TAU), rot: rnd(0, TAU), spin: (i % 2 ? 1 : -1) * rnd(1.6, 2.4) });
    }
  }

  /* how far the wok has moved off the burner (world units): lift, push and the tilt of the far side */
  function gapOf(pose, u, v) {
    const w = K.toWorld(pose, u, v, 0, _g);
    return Math.max(0, w[2]) + 0.6 * Math.hypot(w[0] - u, w[1] - v);
  }
  const _g = [0, 0, 0];

  function update(s, dt, ctx) {
    ctx = ctx || {};
    s.t += dt;
    if (ctx.flare > 0) s.flare = Math.max(s.flare, ctx.flare);
    s.flare = Math.max(0, s.flare - dt * 2.2); // ~0.45 s jump
    // wok leaving the burner: a 0.2 s lick into the opening gap
    const pose0 = ctx.wokPose || K.restPose(), gap = Math.max(gapOf(pose0, 0, 0.6), gapOf(pose0, 0, -0.6), gapOf(pose0, 0.6, 0), gapOf(pose0, -0.6, 0));
    if (gap > 0.03 && s.gap <= 0.03) s.lick = 1;
    s.gap = gap; s.lick = Math.max(0, s.lick - dt * 5); s.pose = pose0;
    s.flareLevel = s.flare;
    if (s.oil && (s.oil.t += dt) > s.oil.dur) s.oil = null;
    s.rimDrawn = false;
    s.burner = false;
    // shake: decaying wobble, two incommensurate sines so it never looks like a loop
    s.amp *= Math.exp(-dt * 11);
    if (s.amp < 0.05) s.amp = 0;
    s.shake.x = s.amp * Math.sin(s.t * 71); s.shake.y = s.amp * Math.cos(s.t * 57);
    s.haze += (HAZE_MAX * clamp(((ctx.burning || 0) - 0.08) * 1.6, 0, 1) - s.haze) * Math.min(1, dt * 1.5);

    // steam vs smoke from the hotspots. Emitters live in wok space; particles are handed off to the air at spawn.
    const hs = ctx.steam || [], pose = ctx.wokPose || K.restPose();
    let nSteam = 0, nSmoke = 0;
    for (const p of s.parts) p.smoke ? nSmoke++ : nSteam++;
    for (const h of hs) {
      const cook = h.cook || 0;
      if (cook < 0.95) {
        if (nSteam < MAX_STEAM && Math.random() < dt * (0.5 + 1.1 * Math.min(1, cook + 0.3))) {
          nSteam++; s.parts.push({ smoke: false, u: h.u, v: h.v, h: h.h || 0, pose, x: 0, y: 0, born: false,
            vx: rnd(-0.08, 0.08), vy: rnd(0.9, 1.2), r0: rnd(0.05, 0.07), r1: rnd(0.15, 0.21), age: 0, life: rnd(0.7, 0.85), ph: rnd(0, TAU), rot: 0 });
        }
      } else {
        const dark = clamp((cook - 0.95) / 0.8, 0, 1);
        if (nSmoke < MAX_SMOKE && Math.random() < dt * (0.4 + 2 * dark)) {
          nSmoke++; s.parts.push({ smoke: true, dark, u: h.u, v: h.v, h: h.h || 0, pose, x: 0, y: 0, born: false,
            vx: rnd(-0.04, 0.04), vy: rnd(0.4, 0.55), r0: rnd(0.07, 0.1), r1: rnd(0.28, 0.4), age: 0, life: rnd(1.5, 1.9),
            ph: rnd(0, TAU), rot: rnd(0, TAU), spin: rnd(-0.6, 0.6) });
        }
      }
    }
    for (let i = s.parts.length - 1; i >= 0; i--) {
      const p = s.parts[i];
      if ((p.age += dt) > p.life) { s.parts[i] = s.parts[s.parts.length - 1]; s.parts.pop(); }
    }
    for (let i = s.spat.length - 1; i >= 0; i--) {
      const p = s.spat[i];
      p.age += dt;
      if (p.age > p.life) { s.spat[i] = s.spat[s.spat.length - 1]; s.spat.pop(); continue; }
      if (p.age > 0) { p.u += p.du * dt; p.v += p.dv * dt; p.h += p.dh * dt; p.dh -= K.G * dt; }
    }
  }

  /* one tongue: base at (x, y), leaning by `lean` rad */
  function tongue(c, img, x, y, w, h, lean, flip, alpha) {
    c.save(); c.translate(x, y); c.rotate(lean); if (flip) c.scale(-1, 1);
    c.globalAlpha = alpha; c.drawImage(img, -w / 2, -h, w, h); c.restore();
  }

  /* burner ring under the wok: drawn before the wok body, so only what rises past its wall shows */
  /* the burner stays put; where the wok lifts/pushes off it, the tongues under that side flare into the gap */
  function drawBehind(c, s, vw, pose) {
    s.burner = true;
    pose = pose || s.pose;
    const S = sprites(), R = vw.R, t = s.t, f = s.flare, L = s.lick;
    const by = vw.cy + RING.y * R;
    if (f > 0.05 || L > 0.05) { // hot bloom under the wok: the toss reads even where the tongues are hidden
      const e = Math.max(f, L), gr = R * (0.9 + 0.5 * e);
      c.globalCompositeOperation = "lighter"; c.globalAlpha = 0.55 * e;
      c.drawImage(S.glow, vw.cx - gr * 1.3, by - gr * 0.9, gr * 2.6, gr * 1.4);
      c.globalCompositeOperation = "source-over";
    }
    for (const q of s.tongues) {
      const ca = Math.cos(q.a), sa = Math.sin(q.a);
      const gp = clamp(gapOf(pose, ca * 0.6, sa * 0.6) * 6, 0, 1.2); // 0 at rest, ~1 on a full toss
      const boost = 1 + 0.85 * f + 0.9 * gp + 0.6 * L * gp;
      const flick = 0.8 + 0.13 * Math.sin(q.f1 * t + q.p1) + 0.08 * Math.sin(q.f2 * t + q.p2);
      const h = R * RING.ht * q.size * flick * boost, w = R * RING.wd * q.size * (1 + 0.2 * f + 0.25 * gp) * (1.1 - 0.15 * flick);
      const sway = 0.06 * Math.sin(q.f2 * 0.5 * t + q.p1);
      // a lifted wok no longer bends the tongues outward: they stand up into the opening
      tongue(c, S.burner[q.spr], vw.cx + ca * RING.rx * R, by + sa * RING.ry * R, w, h, ca * RING.lean * (1 - 0.15 * f - 0.4 * Math.min(1, gp)) + sway, q.flip, 1);
    }
    c.globalAlpha = 1;
  }

  /* tongues licking up the near wall from below: in front of the wall, under the rim line (the wall silhouette
     is stage.js's bezier: rim corners at x = +-1.04R, control depth B) */
  function drawNear(c, s, vw) {
    const S = sprites(), R = vw.R, t = s.t, f = s.flare, B = R * vw.ct * 1.25 + vw.D * 1.6;
    for (const q of s.near) {
      const k = q.t, m = 1 - k;
      const x = vw.cx + 1.04 * R * (-m * m * m - 3 * m * m * k + 3 * m * k * k + k * k * k);
      const y = vw.cy + 3 * B * k * m + R * 0.05;
      const flick = 0.8 + 0.13 * Math.sin(q.f1 * t + q.p1) + 0.08 * Math.sin(q.f2 * t + q.p2);
      const g = Math.min(1, s.gap * 6), h = R * 0.3 * q.size * flick * (1 + 0.9 * f + 0.8 * g + 0.5 * s.lick), w = R * 0.15 * q.size * (1 + 0.25 * f + 0.2 * g);
      tongue(c, S.burner[q.spr], x, y, w, h, ((x - vw.cx) / R) * 0.55, q.flip, 1);
    }
    c.globalAlpha = 1;
  }

  /* flare tongues licking up over the far rim; between the wok and the food if the game calls it, else in drawFront */
  function drawRim(c, s, vw, pose) {
    s.rimDrawn = true;
    if (s.flare < 0.05) return;
    const S = sprites(), R = vw.R;
    for (const q of s.licks) {
      const e = K.smooth(clamp(s.flare * 1.5 - q.delay, 0, 1));
      if (e <= 0) continue;
      const [x, y] = K.proj(vw, pose, Math.cos(q.phi) * 1.03, Math.sin(q.phi) * 1.03, K.RIM_H);
      const flick = 0.85 + 0.15 * Math.sin(q.f1 * s.t + q.p1);
      const h = R * 0.72 * q.size * flick * e, w = R * 0.32 * q.size * (0.6 + 0.4 * e);
      tongue(c, S.lick[q.spr], x, y + h * 0.15, w, h, -Math.cos(q.phi) * 0.5 + q.lean, false, Math.min(1, e * 1.3));
    }
    c.globalAlpha = 1;
  }

  /* rim flare (if not drawn yet), oil-catch fireball, steam, smoke, spatter: after the food */
  function drawFront(c, s, vw, pose) {
    const S = sprites(), R = vw.R;
    if (!s.rimDrawn) drawRim(c, s, vw, pose);
    if (s.burner !== false) drawNear(c, s, vw);
    if (s.oil) {
      const k = s.oil.t / s.oil.dur, e = (k < 0.2 ? K.smooth(k / 0.2) : 1 - K.smooth((k - 0.2) / 0.8)) * s.oil.k;
      // the oil mist catches on the burner flame beyond the far lip: the flare rises behind the rim, never in the food
      const [x, y] = K.proj(vw, pose, 0, 0.98, K.RIM_H + 0.05);
      c.globalCompositeOperation = "lighter";
      const gr = R * 1.05 * e;
      c.globalAlpha = 0.7; c.drawImage(S.glow, x - gr, y - gr * 0.8, gr * 2, gr * 1.6);
      c.globalCompositeOperation = "source-over";
      for (let i = 0; i < 6; i++) { // outer tongues first, the tall centre one last
        const a = (i - 2.5) * 0.17 + Math.PI / 2; // tongues along the far rim arc
        const [tx, ty] = K.proj(vw, pose, Math.cos(a) * 1.02, Math.sin(a) * 1.02, K.RIM_H);
        const rr = i === 5 ? 0 : 0.1;
        const sz = (i === 5 ? 1.25 : 0.8) * (0.9 + 0.12 * Math.sin(i * 1.7 + s.oil.t * 30));
        tongue(c, S.lick[i % 2], tx, ty, R * 0.5 * e * sz, R * 0.8 * e * sz, Math.cos(a) * rr * 2.2, i % 2 === 0, Math.min(1, e * 1.4));
      }
      c.globalAlpha = 1;
    }
    // steam and smoke: spawn point projected once, then they drift in screen space
    for (const p of s.parts) {
      if (!p.born) { const [x, y] = K.proj(vw, p.pose, p.u, p.v, p.h + 0.05); p.x = x; p.y = y; p.born = true; p.pose = null; }
      const k = p.age / p.life, a = p.age;
      const x = p.x + (p.vx * a + (p.smoke ? Math.sin(a * 1.7 + p.ph) * 0.06 * (0.3 + k) : Math.sin(a * 5 + p.ph) * 0.015)) * R;
      const y = p.y - p.vy * R * a * (p.smoke ? 1 : 1 - 0.35 * k);
      const r = R * (p.r0 + (p.r1 - p.r0) * Math.sqrt(k));
      if (p.age < 0) continue;
      const fade = Math.min(1, p.age * (p.smoke ? 3 : 8)) * (1 - k) * clamp((y - vw.H * 0.1) / (vw.H * 0.2), 0, 1);
      if (fade <= 0.005) continue;
      if (p.smoke) {
        c.globalAlpha = Math.min(0.35, (0.24 + 0.11 * p.dark) * fade * 1.6);
        c.save(); c.translate(x, y); c.rotate(p.rot + p.spin * a);
        c.drawImage(S.smoke[Math.min(3, (p.dark * 4) | 0)], -r, -r, r * 2, r * 2); c.restore();
      } else {
        c.globalAlpha = 0.34 * fade;
        c.drawImage(S.steam, x - r * 0.7, y - r * 1.1, r * 1.4, r * 2.2); // tall wisp
      }
    }
    // burning haze: only around the wok, never a full-screen film
    if (s.haze > 0.01) {
      const [hx, hy] = K.proj(vw, pose, 0, 0, K.RIM_H);
      c.globalAlpha = s.haze / HAZE_MAX * 0.35;
      c.drawImage(S.smoke[3], hx - R * 1.4, hy - R * 1.6, R * 2.8, R * 2.2);
    }
    // oil spatter: hot-white pops, 120 ms
    for (const p of s.spat) {
      if (p.age < 0) continue;
      const k = p.age / p.life, [x, y] = K.proj(vw, pose, p.u, p.v, p.h), r = p.r * (1 + 0.7 * k);
      c.globalAlpha = (1 - k * k) * 0.6; c.drawImage(S.dot, x - r * 3, y - r * 3, r * 6, r * 6);
      c.globalAlpha = 1 - k * k; c.fillStyle = "#FFFBF0"; c.fillRect(x - r, y - r, r * 2, r * 2);
    }
    c.globalAlpha = 1;
  }

  K.Fx = { create, update, flare, oilCatch, spatter, kick, burnCue, drawBehind, drawRim, drawFront };

  /* ================= KFR.Audio: Web Audio, all procedural ================= */
  const A = { ctx: null, out: null, muted: false, noise: null, crackle: null, siz: null, warn: null };

  function makeBuffers(ac) {
    const sr = ac.sampleRate, mk = (sec) => ac.createBuffer(1, Math.floor(sr * sec), sr);
    A.noise = mk(2); const n = A.noise.getChannelData(0);
    for (let i = 0; i < n.length; i++) n[i] = Math.random() * 2 - 1;
    // sizzle bed: hiss with a slow random swell plus dense tiny pops
    A.sizz = mk(3); const z = A.sizz.getChannelData(0);
    let env = 0.5, tgt = 0.5, pop = 0, popA = 0;
    for (let i = 0; i < z.length; i++) {
      if (i % 600 === 0) tgt = rnd(0.25, 0.8);
      env += (tgt - env) * 0.002;
      if (Math.random() < 0.0016) { pop = 1; popA = rnd(0.4, 1); }
      pop *= 0.93;
      z[i] = (Math.random() * 2 - 1) * (env * 0.55 + pop * popA * 1.4);
    }
    // crackle: sparse sharp clicks only (burn warning, rice landing)
    A.crackle = mk(2); const k = A.crackle.getChannelData(0);
    let cl = 0, clA = 0;
    for (let i = 0; i < k.length; i++) {
      if (Math.random() < 0.0009) { cl = 1; clA = rnd(0.3, 1); }
      cl *= 0.9;
      k[i] = (Math.random() * 2 - 1) * cl * clA;
    }
  }

  /* a looping bed: src -> highpass -> lowpass -> gain(0) -> out */
  function bed(buf, hp, lp, rate) {
    const ac = A.ctx, src = ac.createBufferSource(), h = ac.createBiquadFilter(), l = ac.createBiquadFilter(), g = ac.createGain();
    src.buffer = buf; src.loop = true; src.playbackRate.value = rate;
    h.type = "highpass"; h.frequency.value = hp; l.type = "lowpass"; l.frequency.value = lp; g.gain.value = 0;
    src.connect(h); h.connect(l); l.connect(g); g.connect(A.out); src.start();
    return { g, l, level: 0 };
  }

  function unlock() {
    try {
      try { if (navigator.audioSession) navigator.audioSession.type = "playback"; } catch (e) {}
      if (!A.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        const ac = (A.ctx = new AC());
        const comp = ac.createDynamicsCompressor();
        comp.threshold.value = -14; comp.ratio.value = 4;
        A.out = ac.createGain(); A.out.gain.value = A.muted ? 0 : 0.9;
        A.out.connect(comp); comp.connect(ac.destination);
        makeBuffers(ac);
        A.siz = bed(A.sizz, 1500, 6000, 1);
        A.warn = bed(A.crackle, 900, 4500, 0.8);
        // iOS: play something inside the gesture
        const s = ac.createBufferSource(); s.buffer = ac.createBuffer(1, 1, ac.sampleRate); s.connect(ac.destination); s.start();
      }
      if (A.ctx.state !== "running") A.ctx.resume();
    } catch (e) {}
  }

  /* everything below no-ops before unlock and swallows errors: sound must never break the game */
  const safe = (fn) => function () { if (!A.ctx) return; try { fn.apply(null, arguments); } catch (e) {} };
  const now = () => A.ctx.currentTime;

  /* filtered noise burst with a frequency sweep and an attack/decay envelope */
  function burst(o) {
    const ac = A.ctx, t = now() + (o.at || 0), d = o.dur;
    const src = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain();
    src.buffer = o.buf || A.noise; src.playbackRate.value = o.rate || 1;
    f.type = o.type || "bandpass"; f.Q.value = o.q || 1;
    f.frequency.setValueAtTime(o.f0, t); f.frequency.exponentialRampToValueAtTime(o.f1 || o.f0, t + d);
    const at = o.attack || 0.004;
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(o.gain, t + at); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    src.connect(f); f.connect(g); g.connect(A.out);
    src.start(t, Math.random() * (src.buffer.duration - d - 0.1)); src.stop(t + d + 0.05);
  }
  function tone(o) {
    const ac = A.ctx, t = now() + (o.at || 0), d = o.dur, osc = ac.createOscillator(), g = ac.createGain();
    osc.type = o.type || "sine";
    osc.frequency.setValueAtTime(o.f0, t); osc.frequency.exponentialRampToValueAtTime(o.f1 || o.f0, t + d);
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(o.gain, t + (o.attack || 0.003)); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    osc.connect(g); g.connect(A.out); osc.start(t); osc.stop(t + d + 0.05);
  }

  K.Audio = {
    unlock,
    /* main heat cue: louder and brighter with food on the metal; drops fast when it all goes airborne */
    sizzle: safe((level) => {
      const l = clamp(+level || 0, 0, 1), b = A.siz;
      if (Math.abs(l - b.level) < 0.01) return;
      const tau = l < b.level ? 0.035 : 0.08;
      b.level = l;
      b.g.gain.setTargetAtTime(0.42 * Math.pow(l, 1.3), now(), tau);
      b.l.frequency.setTargetAtTime(2200 + 7500 * l, now(), tau);
    }),
    toss: safe((strength, snap) => {
      const s = clamp((strength || 1) / 1.6, 0.15, 1), p = clamp(snap || 0, 0, 1);
      burst({ f0: 320, f1: 1500 + 900 * s, q: 1.1, dur: 0.2 + 0.12 * s, gain: 0.4 * s, attack: 0.07 }); // whoosh
      burst({ f0: 2400, f1: 3900, q: 9, dur: 0.08 + 0.07 * p, gain: 0.16 + 0.22 * p, at: 0.01 }); // scrape on the burner ring
      tone({ f0: 1870, dur: 0.3, gain: 0.03 + 0.05 * p, type: "triangle", at: 0.01 }); // steel ring
      tone({ f0: 2950, dur: 0.18, gain: 0.02 + 0.03 * p, type: "sine", at: 0.01 });
    }),
    land: safe((kind) => {
      if (kind === "egg-soft") {
        tone({ f0: 190, f1: 85, dur: 0.13, gain: 0.35 });
        burst({ type: "lowpass", f0: 1000, f1: 280, dur: 0.12, gain: 0.3 });
        burst({ buf: A.sizz, type: "highpass", f0: 3000, dur: 0.35, gain: 0.25, at: 0.03, attack: 0.03 });
      } else if (kind === "egg-splat") {
        burst({ type: "lowpass", f0: 2000, f1: 240, q: 2, dur: 0.24, gain: 0.55, attack: 0.002 });
        tone({ f0: 120, f1: 55, dur: 0.16, gain: 0.4 });
        for (let i = 0; i < 3; i++) tone({ f0: rnd(650, 950), f1: rnd(260, 340), dur: 0.05, gain: 0.1, at: 0.03 + i * rnd(0.03, 0.06) });
      } else if (kind === "spill") {
        for (let i = 0; i < 9; i++) burst({ f0: rnd(2800, 5200), q: 5, dur: 0.025, gain: rnd(0.12, 0.28), at: rnd(0, 0.45), attack: 0.001 });
      } else { // rice: a crackling burst of contact
        burst({ buf: A.crackle, type: "highpass", f0: 2200, f1: 3800, dur: 0.4, gain: 0.9, rate: rnd(1.1, 1.4), attack: 0.003 });
        burst({ buf: A.sizz, type: "highpass", f0: 3500, dur: 0.3, gain: 0.3, attack: 0.01 });
        tone({ f0: 150, f1: 75, dur: 0.07, gain: 0.15 });
      }
    }),
    flare: safe(() => { // gas whoomph
      burst({ type: "lowpass", f0: 160, f1: 1100, q: 1.5, dur: 0.5, gain: 0.6, attack: 0.05 });
      tone({ f0: 75, f1: 42, dur: 0.45, gain: 0.3, attack: 0.03 });
    }),
    ding: safe(() => { // kitchen-timer bell: inharmonic partials
      [[1, 0.28], [2, 0.1], [2.76, 0.08], [5.4, 0.03]].forEach(([m, g]) => tone({ f0: 1320 * m, dur: 1.4 / Math.sqrt(m), gain: g }));
    }),
    stamp: safe(() => {
      tone({ f0: 115, f1: 45, dur: 0.22, gain: 0.6 });
      burst({ type: "lowpass", f0: 1400, f1: 200, dur: 0.08, gain: 0.5, attack: 0.001 });
    }),
    burnWarn: safe((level) => {
      const l = clamp(+level || 0, 0, 1), b = A.warn;
      if (Math.abs(l - b.level) < 0.01) return;
      b.level = l;
      b.g.gain.setTargetAtTime(0.5 * l, now(), 0.25);
    }),
    mute: (m) => {
      A.muted = !!m;
      try { if (A.ctx) A.out.gain.setTargetAtTime(A.muted ? 0 : 0.9, now(), 0.03); } catch (e) {}
    },
  };
})();
