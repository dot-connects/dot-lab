/* input.js — devicemotion flick + tilt, pointer fallback (input; spec: research/input.md, API: CONTRACT.md) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  const clamp01 = (x) => (x < 0 ? 0 : x > 1 ? 1 : x);
  const clamp1 = (x) => (x < -1 ? -1 : x > 1 ? 1 : x);

  /* motion constants (research/input.md §3, est — tune on a phone with ?debug + KFR.Input.debug) */
  const TAU_G = 0.2, A_ON_MIN = 6, A_OFF = 2, A_MAX = 25, W_ON = 200, PULSE_MAX = 250;
  const RISE_FAST = 40, RISE_SLOW = 180, K_ROT = 0.02, REFRACT = 300, CAL_MS = 1000, PROBE_MS = 1000;
  /* tilt (§4) */
  const DEAD = 0.03, FULL = 0.64, TILT_TAU = 30, FREEZE = 250; // gravity fallback: ~33 deg of phone = full tilt
  const DEAD_DEG = 3, FULL_DEG = 40; // deviceorientation: the wok turns ~1.2x the phone (operator 10-24: 12 deg was full, too touchy)
  /* touch: speeds in screen heights per second */
  const T_ON = 0.8, T_MAX = 2.2, T_RISE_FAST = 30, T_RISE_SLOW = 220, T_MIN_TRAVEL = 0.03;
  const SLOW_TRAVEL = 0.15, SLOW_MIN = 0.3; // too-slow flick: > 15% of the height, peak 0.3..T_ON (a crawl is tilt)
  const HINT = "Safari/Chrome에서 열면 폰을 흔들어서 할 수 있어요";

  const I = (K.Input = {
    mode: "touch", hint: "", debug: {}, slowHint: 0,
    start, requestPermission, useTouch, demoTilt, armAfterRelease,
    _feed: (e) => { started = true; onMotion(e); },
  });

  let onFlickCb = null, appEl = null, started = false;

  /* ---------- tilt: targets set by motion / touch / demo, EMA applied when the game polls ---------- */
  const tilt = { u: 0, v: 0 };
  let tu = 0, tv = 0, tiltLast = 0;
  Object.defineProperty(I, "tilt", {
    get() {
      const now = performance.now(), dt = tiltLast ? now - tiltLast : 1000;
      tiltLast = now;
      const k = 1 - Math.exp(-dt / TILT_TAU);
      tilt.u += (tu - tilt.u) * k; tilt.v += (tv - tilt.v) * k;
      return tilt;
    },
  });
  function demoTilt(u, v) { tu = u; tv = v; } // watch mode and test scripts steer the wok

  /* ---------- flick out ---------- */
  function emit(f) {
    I.debug.last = f;
    try { navigator.vibrate && navigator.vibrate(15); } catch (e) {}
    onFlickCb && onFlickCb(f);
  }

  /* ---------- motion ---------- */
  let g = null, g0 = null, sgn = 1, lastT = 0, aOn = A_ON_MIN;
  let cal = null;            // {t0, n, gx, gy, gz, m, m2} while calibrating
  let pulse = null, quietT = 0, lastFlick = -1e9, frozenUntil = 0, quietUntil = 0;
  let gotMotion = false, probeDone = null, ori0 = null;
  // fused attitude: smooth and immediate, unlike gravity from the accelerometer (which needs a low-pass and lags)
  function onOrient(e) {
    if (e.beta == null || e.gamma == null || I.mode !== "motion") return;
    const ang = (screen.orientation && screen.orientation.angle) || window.orientation || 0;
    if (ang) { ori0 = null; return; } // landscape: leave it to the gravity path
    ori0 = true; // the orientation path owns the tilt (the gravity path stands down)
    if (performance.now() < frozenUntil) return;
    const zone = (x) => Math.sign(x) * clamp01((Math.abs(x) - DEAD_DEG) / (FULL_DEG - DEAD_DEG));
    const gm = Math.abs(e.beta) > 90 ? -e.gamma : e.gamma; // past vertical gamma mirrors
    tu = zone(gm); tv = zone(-Math.max(-90, Math.min(90, e.beta))); // absolute: flat on the ground = level wok
  }

  function onMotion(e) {
    const a = e.accelerationIncludingGravity;
    if (!a || a.x == null) return;
    if (!gotMotion) { gotMotion = true; I.mode = "motion"; probeDone && probeDone(); }
    const t = e.timeStamp;
    const dt = g ? Math.min(0.1, Math.max(0.001, (t - lastT) / 1000)) : 0;
    lastT = t;
    // device -> screen frame (layout may have auto-rotated). iOS reports aIG with the opposite sign to the spec on some
    // versions: face-up z must be +g, so take the sign from the first sample.
    const ang = ((screen.orientation && screen.orientation.angle) || window.orientation || 0) * Math.PI / 180;
    const ca = Math.cos(ang), sa = Math.sin(ang);
    const rot = (x, y) => [x * ca - y * sa, x * sa + y * ca];
    if (!g) sgn = a.z + a.y < -4 ? -1 : 1; // face-up z = +g, upright y = +g
    const [ax, ay] = rot(a.x * sgn, a.y * sgn), az = a.z * sgn;
    if (!g) { g = [ax, ay, az]; g0 = g.slice(); cal = { t0: t, n: 0, gx: 0, gy: 0, gz: 0, m: 0, m2: 0 }; }
    const k = dt / (TAU_G + dt);
    g[0] += (ax - g[0]) * k; g[1] += (ay - g[1]) * k; g[2] += (az - g[2]) * k;
    const ac = e.acceleration;
    let lx, ly, lz;
    if (ac && ac.x != null) { [lx, ly] = rot(ac.x * sgn, ac.y * sgn); lz = ac.z * sgn; }
    else { lx = ax - g[0]; ly = ay - g[1]; lz = az - g[2]; }
    const m = Math.hypot(lx, ly, lz);
    const rr = e.rotationRate;
    const [pitch, roll] = rr && rr.beta != null ? rot(rr.beta, rr.gamma) : [0, 0]; // about screen x (far lip up +), y

    if (cal) {
      cal.n++; cal.gx += g[0]; cal.gy += g[1]; cal.gz += g[2]; cal.m += m; cal.m2 += m * m;
      if (t - cal.t0 >= CAL_MS) {
        const n = cal.n, mu = cal.m / n, sd = Math.sqrt(Math.max(0, cal.m2 / n - mu * mu));
        g0 = [cal.gx / n, cal.gy / n, cal.gz / n]; aOn = Math.max(A_ON_MIN, mu + 6 * sd);
        I.debug.cal = { mu: +mu.toFixed(3), sd: +sd.toFixed(3), aOn: +aOn.toFixed(2), g0: g0.map((x) => +x.toFixed(2)) };
        cal = null;
      }
    }

    // flick pulse (§3)
    if (!pulse && m < A_OFF) quietT = t;
    const gate = m > aOn || Math.abs(pitch) > W_ON;
    if (gate && t < quietUntil) quietUntil = t + 400; // armAfterRelease: wait for 400 ms of calm
    if (!pulse && gate && t - lastFlick > REFRACT) {
      pulse = { t0: t, tq: quietT, peak: m, tPeak: t, ix: 0, iy: 0, iz: 0, rx: 0, ry: 0 };
      frozenUntil = t + FREEZE;
    }
    if (pulse) {
      const p = pulse;
      if (m > p.peak) { p.peak = m; p.tPeak = t; }
      p.ix += lx * dt; p.iy += ly * dt; p.iz += lz * dt; p.rx += pitch * dt; p.ry += roll * dt;
      const fell = m < A_OFF || (p.peak > aOn && m < 0.5 * p.peak);
      if ((t > p.t0 && fell && Math.abs(pitch) < W_ON) || t - p.t0 > PULSE_MAX) {
        pulse = null; lastFlick = t;
        const s01 = clamp01((p.peak - aOn) / (A_MAX - aOn));
        const rise = p.tPeak - p.tq; // from leaving the noise floor, not from the gate: a slow push crosses A_ON late
        const snap = clamp01(1 - (rise - RISE_FAST) / (RISE_SLOW - RISE_FAST));
        // a flat phone flicked up moves along z (screen normal): lifts and pulls count as forward, only x is sideways
        const sx = p.ix + K_ROT * p.ry, N = Math.hypot(sx, p.iy + K_ROT * p.rx, p.iz);
        let side = N > 0.15 ? sx / N : 0; if (Math.abs(side) < 0.3) side = 0;
        const dx = side, dy = Math.sqrt(1 - side * side);
        I.debug.peak = +p.peak.toFixed(2); I.debug.rise = Math.round(rise); I.debug.dir = [+dx.toFixed(2), +dy.toFixed(2)];
        I.debug.rejected = s01 < 0.2 && snap < 0.1;
        if (!I.debug.rejected && started && t >= quietUntil)
          emit({ strength: 0.3 + 1.3 * s01, snap, dirU: dx, dirV: dy });
      }
    }

    // tilt from gravity delta (§4): + u = right side lower (g leans to -x), + v = far side lower (g leans to -y)
    if (t >= frozenUntil && !ori0) {
      const zone = (x) => Math.sign(x) * clamp01((Math.abs(x) - DEAD) / (FULL - DEAD));
      tu = zone(-g[0] / 9.81); tv = zone(-g[1] / 9.81); // absolute, like the orientation path
    }
  }

  /* ---------- touch / mouse ---------- */
  let ptr = null, blockUntilUp = false;
  function onDown(e) {
    if (I.mode !== "touch" || ptr) return;
    const r = appEl.getBoundingClientRect();
    ptr = { id: e.pointerId, x0: e.clientX, y0: e.clientY, W: r.width, H: r.height, s: [[e.timeStamp, e.clientX, e.clientY]],
      fast: null, fired: false, vMax: 0, slowT: e.timeStamp, slowX: e.clientX, slowY: e.clientY };
  }
  function fire(p) {
    const f = p.fast;
    const dx = f.x - f.x0, dy = f.y0 - f.y, n = Math.hypot(dx, dy); // screen up = +dy (forward)
    if (n / p.H < T_MIN_TRAVEL) { p.fast = null; return; } // a twitch, not a stroke
    p.fired = true;
    const s01 = clamp01((f.peak - T_ON) / (T_MAX - T_ON));
    const snap = clamp01(1 - (f.tPeak - f.t0 - T_RISE_FAST) / (T_RISE_SLOW - T_RISE_FAST));
    let su = dx / n; if (Math.abs(su) < 0.3) su = 0; // a sideways swipe throws sideways, up/down swipes forward
    const dirU = su, dirV = Math.sqrt(1 - su * su);
    I.debug.peak = +f.peak.toFixed(2); I.debug.rise = Math.round(f.tPeak - f.t0); I.debug.dir = [+dirU.toFixed(2), +dirV.toFixed(2)];
    if (started) emit({ strength: 0.3 + 1.3 * s01, snap, dirU, dirV });
  }
  function onMove(e) {
    const p = ptr;
    if (!p || e.pointerId !== p.id) return;
    const t = e.timeStamp, x = e.clientX, y = e.clientY;
    p.s.push([t, x, y]);
    while (p.s.length > 2 && t - p.s[0][0] > 70) p.s.shift();
    const a = p.s[0], dt = Math.max(16, t - a[0]); // a 70 ms window: one jittery sample no longer sets the strength
    const v = Math.hypot(x - a[1], y - a[2]) / dt * 1000 / p.H; // stroke speed in any direction, heights / s
    if (v > p.vMax) p.vMax = v;
    if (p.fired || blockUntilUp) return; // after a flick: tilt holds until the finger lifts
    if (p.fast) {
      if (v > p.fast.peak) { p.fast.peak = v; p.fast.tPeak = t; }
      p.fast.x = x; p.fast.y = y;
      if (v < 0.5 * p.fast.peak) fire(p);
    } else if (v > T_ON) {
      p.fast = { t0: p.slowT, x0: p.slowX, y0: p.slowY, peak: v, tPeak: t, x, y };
    } else {
      p.slowT = t; p.slowX = x; p.slowY = y;
      tu = clamp1((x - p.x0) / (0.3 * p.W)); tv = clamp1((p.y0 - y) / (0.15 * p.H)); // drag down = toward the handle
    }
  }
  function onUp(e) {
    const p = ptr;
    if (!p || e.pointerId !== p.id) return;
    if (e.type === "pointerup" && p.fast && !p.fired && !blockUntilUp) fire(p);
    // a long purposeful stroke that never got fast enough: an attempted flick, tell the lead ("더 빠르게!")
    if (e.type === "pointerup" && !p.fired && !blockUntilUp && started &&
        Math.hypot(e.clientX - p.x0, e.clientY - p.y0) / p.H > SLOW_TRAVEL && p.vMax > SLOW_MIN && p.vMax < T_ON)
      I.slowHint = performance.now();
    ptr = null; blockUntilUp = false;
    if (I.mode === "touch" && started) { tu = 0; tv = 0; }
  }

  /* ---------- API ---------- */
  function start(onFlick, el) {
    onFlickCb = onFlick; appEl = el;
    el.addEventListener("pointerdown", onDown);
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
  }
  function useTouch() {
    started = true; I.mode = "touch"; tu = 0; tv = 0;
    window.removeEventListener("devicemotion", onMotion); window.removeEventListener("deviceorientation", onOrient); ori0 = null;
  }
  // Must be called synchronously from a tap (iOS). Resolves once motion events flow, or after the 1 s probe in touch mode.
  function requestPermission() {
    started = true; tu = 0; tv = 0;
    const DM = window.DeviceMotionEvent;
    let ask;
    try {
      ask = DM && typeof DM.requestPermission === "function" ? DM.requestPermission() : Promise.resolve(DM ? "granted" : "denied");
    } catch (e) { ask = Promise.resolve("denied"); }
    return Promise.resolve(ask).catch(() => "denied").then((r) => new Promise((res) => {
      if (r !== "granted") { useTouch(); return res(); }
      const timer = setTimeout(() => {
        probeDone = null;
        if (!gotMotion) { useTouch(); I.hint = HINT; }
        res();
      }, PROBE_MS);
      probeDone = () => { probeDone = null; clearTimeout(timer); res(); };
      window.addEventListener("devicemotion", onMotion); window.addEventListener("deviceorientation", onOrient);
    }));
  }
  function armAfterRelease() {
    blockUntilUp = !!ptr;
    quietUntil = lastT + 400;
  }
})();
