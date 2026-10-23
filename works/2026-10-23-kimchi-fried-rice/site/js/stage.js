/* stage.js — counter, carbon-steel wok, handle, heat glow (art bible §2). Cached art, cheap per frame. */
"use strict";
(function () {
  const K = window.KFR, TAU = K.TAU;
  let counterCache = null, wokCache = null;

  function counter(W, H) {
    if (counterCache && counterCache.W === W && counterCache.H === H) return counterCache.cv;
    const [cv, c] = K.offscreen(W, H);
    const bg = c.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, "#E9EDF0"); bg.addColorStop(1, "#C3CBD2");
    c.fillStyle = bg; c.fillRect(0, 0, W, H);
    // subtle horizontal brushing
    for (let y = 0; y < H; y += 2) {
      c.fillStyle = `rgba(255,255,255,${0.05 + 0.1 * Math.abs(Math.sin(y * 12.9898) * 43758.5453 % 1)})`;
      c.fillRect(0, y, W, 1);
    }
    for (let i = 0; i < 40; i++) {
      c.fillStyle = "rgba(142,153,163,.08)";
      c.fillRect(0, Math.random() * H, W, 1);
    }
    // a vignette pulls the eye to the wok
    const vg = c.createRadialGradient(W / 2, H * 0.47, W * 0.3, W / 2, H * 0.47, H * 0.75);
    vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(40,50,60,.22)");
    c.fillStyle = vg; c.fillRect(0, 0, W, H);
    counterCache = { W, H, cv };
    return cv;
  }

  /* the wok around its own rim centre; returns {cv, w, h, ox, oy} */
  function wokArt(vw) {
    if (wokCache && wokCache.R === vw.R) return wokCache;
    const R = vw.R, ct = vw.ct, D = vw.D, pad = 6;
    const w = R * 2.16 + pad * 2, top = R * 1.05 * ct + pad, h = top + R * ct * 1.25 + D * 1.7 + pad;
    const [cv, c] = K.offscreen(w, h);
    const cx = w / 2, cy = top;
    const wall = () => {
      c.beginPath(); c.moveTo(cx - R * 1.04, cy);
      c.bezierCurveTo(cx - R * 1.04, cy + R * ct * 1.25 + D * 1.6, cx + R * 1.04, cy + R * ct * 1.25 + D * 1.6, cx + R * 1.04, cy);
      c.closePath();
    };
    // outer wall: blue-black with a warm underglow from the fire
    const wg = c.createLinearGradient(cx - R, 0, cx + R, 0);
    wg.addColorStop(0, "#15181B"); wg.addColorStop(0.35, "#2A3440"); wg.addColorStop(0.7, "#1E2226"); wg.addColorStop(1, "#121416");
    c.fillStyle = wg; wall(); c.fill();
    const glow = c.createLinearGradient(0, cy, 0, cy + R * ct + D * 1.6);
    glow.addColorStop(0, "rgba(255,110,40,0)"); glow.addColorStop(0.6, "rgba(255,110,40,0)"); glow.addColorStop(1, "rgba(255,110,40,.15)");
    c.fillStyle = glow; wall(); c.fill();
    // rim ring
    c.fillStyle = "#3a434d"; c.beginPath(); c.ellipse(cx, cy, R * 1.045, R * 1.045 * ct, 0, 0, TAU); c.fill();
    c.strokeStyle = "#11141a"; c.lineWidth = 1; c.beginPath(); c.ellipse(cx, cy, R * 1.045, R * 1.045 * ct, 0, 0, TAU); c.stroke();
    c.strokeStyle = "rgba(201,211,220,.85)"; c.lineWidth = Math.max(1.2, R * 0.012);
    c.beginPath(); c.ellipse(cx, cy, R * 1.03, R * 1.03 * ct, 0, (200 / 180) * Math.PI, (340 / 180) * Math.PI); c.stroke();
    // interior: patina bowl
    const ig = c.createRadialGradient(cx - R * 0.05, cy + D * 0.55, R * 0.04, cx, cy + D * 0.3, R);
    ig.addColorStop(0, "#3A4350"); ig.addColorStop(0.6, "#2A3440"); ig.addColorStop(1, "#15181B");
    c.fillStyle = ig; c.beginPath(); c.ellipse(cx, cy, R * 0.98, R * 0.98 * ct, 0, 0, TAU); c.fill();
    c.save(); c.beginPath(); c.ellipse(cx, cy, R * 0.98, R * 0.98 * ct, 0, 0, TAU); c.clip();
    // straw/blue heat tint ring (seasoned carbon steel)
    const ht = c.createRadialGradient(cx, cy + D * 0.45, R * 0.5, cx, cy + D * 0.45, R * 0.85);
    ht.addColorStop(0, "rgba(59,74,107,0)"); ht.addColorStop(0.5, "rgba(59,74,107,.28)"); ht.addColorStop(0.8, "rgba(106,90,58,.22)"); ht.addColorStop(1, "rgba(106,90,58,0)");
    c.fillStyle = ht; c.fillRect(cx - R, cy - R, R * 2, R * 2);
    // faint seasoning rings
    c.strokeStyle = "rgba(255,255,255,.035)"; c.lineWidth = 1;
    for (const rr of [0.3, 0.55, 0.78]) { c.beginPath(); c.ellipse(cx, cy + D * (1 - rr) * 0.7, R * rr, R * rr * ct, 0, 0, TAU); c.stroke(); }
    // far inner wall catches the key light, plus a soft specular crescent upper-left
    const lw = c.createLinearGradient(0, cy - R * ct, 0, cy);
    lw.addColorStop(0, "rgba(255,255,255,.13)"); lw.addColorStop(1, "rgba(255,255,255,0)");
    c.fillStyle = lw; c.fillRect(cx - R, cy - R * ct, R * 2, R * ct);
    c.strokeStyle = "rgba(255,255,255,.12)"; c.lineWidth = R * 0.12; c.lineCap = "round";
    c.beginPath(); c.ellipse(cx, cy + D * 0.2, R * 0.78, R * 0.78 * ct, 0, Math.PI * 1.08, Math.PI * 1.32); c.stroke();
    c.restore();
    wokCache = { R, cv, w, h, ox: cx, oy: cy };
    return wokCache;
  }

  function handle(c, vw, x0, y0, H) {
    const R = vw.R, nw = R * 0.1;
    // steel neck + rivets
    c.fillStyle = "#4a5058";
    c.beginPath(); c.moveTo(x0 - nw * 0.5, y0); c.lineTo(x0 + nw * 0.5, y0); c.lineTo(x0 + nw * 0.62, y0 + R * 0.32); c.lineTo(x0 - nw * 0.62, y0 + R * 0.32); c.fill();
    c.fillStyle = "rgba(255,255,255,.18)"; c.fillRect(x0 - nw * 0.4, y0, nw * 0.18, R * 0.32);
    // wood grip, widening toward the viewer
    const y1 = y0 + R * 0.3, y2 = H + 12;
    const g = c.createLinearGradient(x0 - R * 0.2, 0, x0 + R * 0.2, 0);
    g.addColorStop(0, "#4a2a16"); g.addColorStop(0.4, "#A0673C"); g.addColorStop(0.62, "#8a5530"); g.addColorStop(1, "#3e2412");
    c.fillStyle = g; c.beginPath();
    c.moveTo(x0 - R * 0.09, y1); c.lineTo(x0 + R * 0.09, y1); c.lineTo(x0 + R * 0.17, y2); c.lineTo(x0 - R * 0.17, y2); c.closePath(); c.fill();
    c.strokeStyle = "rgba(60,30,12,.35)"; c.lineWidth = 1;
    for (let i = -2; i <= 2; i++) { c.beginPath(); c.moveTo(x0 + i * R * 0.025, y1 + 4); c.lineTo(x0 + i * R * 0.05, y2); c.stroke(); }
    c.fillStyle = "#c9ccd1";
    for (const yy of [y0 + R * 0.08, y0 + R * 0.24]) { c.beginPath(); c.arc(x0, yy, R * 0.022, 0, TAU); c.fill(); }
  }

  /* the wok is cached drawn at rest; a real tilt (pitch/roll about the pivot) and the push are an affine map of
     that rest picture: solve it from three rim points (centre, +u, +v) rest -> posed. Cheap and exact on the rim. */
  const REST = K.restPose();
  function poseTransform(c, vw, pose) {
    const P0 = K.proj(vw, REST, 0, 0, K.RIM_H), Q0 = K.proj(vw, pose, 0, 0, K.RIM_H);
    const Q1 = K.proj(vw, pose, 1, 0, K.RIM_H), Q2 = K.proj(vw, pose, 0, 1, K.RIM_H);
    const ax = (Q1[0] - Q0[0]) / vw.R, ay = (Q1[1] - Q0[1]) / vw.R;               // image of a +x screen step
    const bx = (Q2[0] - Q0[0]) / (-vw.R * vw.ct), by = (Q2[1] - Q0[1]) / (-vw.R * vw.ct);   // image of a +y step
    c.transform(ax, ay, bx, by, Q0[0] - ax * P0[0] - bx * P0[1], Q0[1] - ay * P0[0] - by * P0[1]);
    return P0;
  }

  /* draws everything below the food: counter, burner glow, wok shadow, handle, wok body, live heat glow */
  K.Stage = {
    drawCounter(c, vw) { c.drawImage(counter(vw.W, vw.H), 0, 0, vw.W, vw.H); },
    drawUnderGlow(c, vw, flare) {
      const g = c.createRadialGradient(vw.cx, vw.cy + vw.D * 1.6, vw.R * 0.3, vw.cx, vw.cy + vw.D * 1.6, vw.R * 1.55);
      g.addColorStop(0, `rgba(255,120,40,${0.3 + 0.25 * Math.min(1, flare)})`); g.addColorStop(1, "rgba(255,120,40,0)");
      c.fillStyle = g; c.fillRect(0, 0, vw.W, vw.H);
    },
    drawWok(c, vw, pose, contact, fire) {
      // shadow stays on the burner; it shrinks and fades as the wok lifts
      const lift = Math.max(0, pose.lift || 0), sh = 1 - Math.min(0.25, lift * 2.5);
      c.fillStyle = `rgba(20,30,40,${0.2 - Math.min(0.08, lift)})`; c.beginPath();
      c.ellipse(vw.cx + vw.R * 0.05, vw.cy + vw.D * 2.1, vw.R * 0.95 * sh, vw.R * vw.ct * 0.9 * sh, 0, 0, TAU); c.fill();
      // the handle hangs from the posed near rim straight down to the hand (outside the tilt map, so it never gets cut)
      const nr = K.proj(vw, pose, 0, -1, K.RIM_H);
      handle(c, vw, nr[0], nr[1] - 4, vw.H);
      c.save();
      const [cx, cy] = poseTransform(c, vw, pose);
      const wa = wokArt(vw);
      c.drawImage(wa.cv, cx - wa.ox, cy - wa.oy, wa.w, wa.h);
      if (fire) { // the floor glows where food is on the metal
        c.save(); c.beginPath(); c.ellipse(cx, cy, vw.R * 0.97, vw.R * 0.97 * vw.ct, 0, 0, TAU); c.clip();
        const g = c.createRadialGradient(cx, cy + vw.D * 0.5, vw.R * 0.05, cx, cy + vw.D * 0.5, vw.R * 0.8);
        g.addColorStop(0, `rgba(255,110,40,${0.05 + 0.15 * contact})`); g.addColorStop(1, "rgba(255,110,40,0)");
        c.fillStyle = g; c.fillRect(cx - vw.R, cy - vw.R, vw.R * 2, vw.R * 2); c.restore();
      }
      c.restore();
    },
    /* the near wall drawn again over resting food, so food in the wok sits behind it */
    drawFrontLip(c, vw, pose) {
      c.save();
      const [cx, cy] = poseTransform(c, vw, pose), wa = wokArt(vw), R = vw.R, ct = vw.ct;
      c.beginPath();
      c.ellipse(cx, cy, R * 1.07, R * 1.07 * ct, 0, 0, Math.PI);          // outer edge, lower half
      c.ellipse(cx, cy + R * 0.02, R * 0.93, R * 0.93 * ct, 0, Math.PI, 0, true);   // inner edge back
      c.closePath(); c.clip();
      c.drawImage(wa.cv, cx - wa.ox, cy - wa.oy, wa.w, wa.h);
      c.restore();
    },
    /* rim highlight flash when food lands */
    flashRim(c, vw, pose, a) {
      if (a <= 0) return;
      c.save(); const [cx, cy] = poseTransform(c, vw, pose);
      c.strokeStyle = `rgba(255,236,200,${a})`; c.lineWidth = 2;
      c.beginPath(); c.ellipse(cx, cy, vw.R * 1.03, vw.R * 1.03 * vw.ct, 0, Math.PI, TAU); c.stroke(); c.restore();
    },
    reset() { counterCache = null; wokCache = null; },
  };
})();
