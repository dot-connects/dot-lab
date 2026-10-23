/* core.js — shared constants, math, projection, colour ramps (see CONTRACT.md) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  const TAU = Math.PI * 2;
  K.TAU = TAU;
  K.G = 12;
  K.THETA = (35 * Math.PI) / 180; // view angle away from straight down (operator 10-23)
  K.RIM_H = 0.35;

  K.rnd = (a, b) => a + Math.random() * (b - a);
  K.clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
  K.lerp = (a, b, k) => a + (b - a) * k;
  K.smooth = (k) => k * k * (3 - 2 * k);
  K.easeOutBack = (k) => 1 + 2.7 * Math.pow(k - 1, 3) + 1.7 * Math.pow(k - 1, 2);

  /* the wok floor: a shallow bowl, rim at r = 1 */
  K.surf = (u, v) => 0.35 * (u * u + v * v);
  /* slope of the floor at (u, v): what gravity pulls food along */
  K.slope = (u, v) => [0.7 * u, 0.7 * v];
  /* flame heat on the floor: hottest at the centre, cool at the rim */
  K.heat = (u, v) => Math.max(0.05, 1.18 - 0.65 * (u * u + v * v));

  K.view = function (W, H) {
    const R = W * 0.43, ct = Math.cos(K.THETA), st = Math.sin(K.THETA);
    return { W, H, R, ct, st, cx: W / 2, cy: H * 0.47, D: 0.35 * R * st };
  };
  K.restPose = () => ({ du: 0, dv: 0, lift: 0, lean: 0, t: 0 });

  /* world (u sideways, v depth, h height) -> screen px. The wok rim sits on the ellipse (cx, cy, R, R*ct).
     A point at height h is drawn (RIM_H - h) * R * st below the rim plane; above the rim, height reads
     a bit taller so tosses look generous. pose moves the whole wok. */
  K.proj = function (vw, pose, u, v, h) {
    const lean = 1 + (pose.lean || 0) * 0.12;
    const x = vw.cx + (u + (pose.du || 0)) * vw.R;
    let y = vw.cy - (v + (pose.dv || 0)) * vw.R * vw.ct * lean + (K.RIM_H - h - (pose.lift || 0)) * vw.R * vw.st;
    if (h > K.RIM_H) y -= (h - K.RIM_H) * vw.R * 0.15;
    return [x, y];
  };
  /* size multiplier for something at height h (closer to the camera = a touch bigger) */
  K.scaleAt = (h) => 1 + Math.max(0, h) * 0.55;

  /* ---------- colour ramps: raw -> glossy -> golden -> burnt ---------- */
  function ramp(stops) {
    return function (v) {
      if (v <= stops[0][0]) return stops[0][1];
      for (let i = 1; i < stops.length; i++)
        if (v <= stops[i][0]) {
          const [a, ca] = stops[i - 1], [b, cb] = stops[i], k = (v - a) / (b - a);
          return [ca[0] + (cb[0] - ca[0]) * k, ca[1] + (cb[1] - ca[1]) * k, ca[2] + (cb[2] - ca[2]) * k];
        }
      return stops[stops.length - 1][1];
    };
  }
  K.ramp = ramp;
  K.col = {
    /* art bible: raw #F4EEE2 -> sauced #E8743A -> glossy #F0552A -> golden #D98A2B -> burnt #5A2A14 -> char #231510 */
    rice: ramp([[0, [244, 238, 226]], [0.3, [232, 116, 58]], [0.75, [240, 85, 42]], [1.1, [217, 138, 43]], [1.3, [176, 98, 36]], [1.5, [90, 42, 20]], [1.75, [35, 21, 16]]]),
    kimchi: ramp([[0, [215, 38, 30]], [0.7, [200, 36, 28]], [1.1, [168, 35, 26]], [1.35, [120, 34, 22]], [1.55, [64, 28, 20]], [1.75, [35, 21, 16]]]),
    scallion: ramp([[0, [111, 191, 58]], [0.8, [92, 168, 52]], [1.3, [70, 112, 40]], [1.6, [48, 50, 30]], [1.8, [34, 30, 24]]]),
    eggWhite: ramp([[0, [236, 238, 232]], [0.7, [255, 255, 252]], [1.6, [252, 248, 238]], [2.4, [236, 226, 206]]]),
    eggEdge: ramp([[0, [255, 255, 255]], [0.8, [250, 244, 230]], [1.1, [238, 200, 132]], [1.5, [198, 130, 66]], [1.9, [112, 68, 42]], [2.4, [46, 37, 33]]]),
    eggUnder: ramp([[0, [252, 248, 240]], [0.6, [244, 216, 160]], [1.0, [228, 172, 98]], [1.4, [186, 118, 60]], [1.9, [98, 62, 42]], [2.4, [42, 34, 30]]]),
    yolk: ramp([[0, [255, 178, 28]], [1.2, [255, 170, 34]], [1.8, [240, 180, 80]], [2.4, [226, 186, 120]]]),
  };
  K.rgb = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;
  K.gloss = (cook) => (cook < 0.4 || cook > 1.3 ? 0 : 0.6 * Math.sin((Math.PI * (cook - 0.4)) / 0.9));
  K.DPR = () => Math.min(1.5, window.devicePixelRatio || 1);   // 1.5 keeps a full-screen canvas at 60 fps on mid phones
  /* make an offscreen canvas of css size w x h at device pixel ratio, returns [canvas, ctx] */
  K.offscreen = function (w, h) {
    const cv = document.createElement("canvas"), d = K.DPR();
    cv.width = Math.max(1, Math.ceil(w * d)); cv.height = Math.max(1, Math.ceil(h * d));
    const c = cv.getContext("2d"); c.scale(d, d);
    return [cv, c];
  };

  /* scoring bands, shared by rice.js stats and the result slip */
  K.RICE_GOOD = [0.55, 1.3];
  K.EGG_GOOD = [0.6, 1.4];
})();
