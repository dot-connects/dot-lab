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

  /* any aspect ratio: the wok is sized by the tighter of width and height, the HUD band keeps ~17% at the top */
  K.view = function (W, H) {
    const R = Math.min(W * 0.43, H * 0.25), ct = Math.cos(K.THETA), st = Math.sin(K.THETA);
    const top = Math.max(H * 0.17, 96), cy = Math.min(H * 0.47, top + R * 1.05 + (H - top - R * 2.4) * 0.35);
    const vcy = Math.max(cy, top + R * ct + R * 0.6);
    // height exaggeration fitted to the room above the wok, so a toss never flies into the HUD on short screens
    const hx = K.clamp((vcy - R * ct - top - R * 0.1) / (R * 0.7) - st, 0.3, K.H_EXAG);
    return { W, H, R, ct, st, cx: W / 2, cy: vcy, D: 0.35 * R * st, top, hx };
  };
  /* pose of the wok this frame (owned by game.js): translation (du, dv, lift) and a real tilt — pitch lifts the
     +v side, roll lifts the +u side, both about the pivot (pu, pv) on the wok floor plane (the rim opposite the
     toss, so the handle-side rim stays put on a forward toss). Velocities (vdu, vdv, vlift) are world units/s. */
  K.restPose = () => ({ du: 0, dv: 0, lift: 0, pitch: 0, roll: 0, pu: 0, pv: 0, vdu: 0, vdv: 0, vlift: 0, t: 0 });
  K.WORLD = K.restPose();   // airborne things are projected with this: they do not ride the wok

  /* wok-local point -> world point (applies the tilt about the pivot, then the translation) */
  K.toWorld = function (pose, u, v, h, out) {
    const p = pose.pitch || 0, r = pose.roll || 0, pu = pose.pu || 0, pv = pose.pv || 0;
    const dvv = v - pv, duu = u - pu;
    out = out || [0, 0, 0];
    out[0] = pu + duu * Math.cos(r) + (pose.du || 0);
    out[1] = pv + dvv * Math.cos(p) + (pose.dv || 0);
    out[2] = h + dvv * Math.sin(p) + duu * Math.sin(r) + (pose.lift || 0);
    return out;
  };
  /* world point -> wok-local (inverse of toWorld; small-angle safe) */
  K.toWok = function (pose, x, y, z, out) {
    const p = pose.pitch || 0, r = pose.roll || 0, pu = pose.pu || 0, pv = pose.pv || 0;
    const du = (x - (pose.du || 0) - pu) / Math.cos(r), dv = (y - (pose.dv || 0) - pv) / Math.cos(p);
    out = out || [0, 0, 0];
    out[0] = pu + du; out[1] = pv + dv; out[2] = z - (pose.lift || 0) - dv * Math.sin(p) - du * Math.sin(r);
    return out;
  };
  /* height above the rim is exaggerated on screen so a toss visibly rises before it comes back (round 2: .15 -> .5) */
  K.H_EXAG = 1.0;
  /* world (u sideways, v depth, h height) -> screen px. The rim sits on the ellipse (cx, cy, R, R*ct); a point at
     height h is drawn (RIM_H - h)·R·st below the rim plane. Pass pose for things in the wok, K.WORLD for airborne. */
  const _w = [0, 0, 0];
  K.proj = function (vw, pose, u, v, h) {
    K.toWorld(pose, u, v, h, _w);
    const x = vw.cx + _w[0] * vw.R;
    let y = vw.cy - _w[1] * vw.R * vw.ct + (K.RIM_H - _w[2]) * vw.R * vw.st;
    if (_w[2] > K.RIM_H) y -= (_w[2] - K.RIM_H) * vw.R * (vw.hx == null ? K.H_EXAG : vw.hx);
    return [x, y];
  };
  /* size multiplier for something at height h (closer to the camera = bigger, up to 1.5x) */
  K.scaleAt = (h) => 1 + Math.min(0.32, Math.max(0, h - 0.1) * 0.5);

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
