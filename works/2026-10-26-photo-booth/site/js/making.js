// The 메이킹 영상 (Round 8, mockups/r8/making5.html + making6.html, picks in ROUND8.md B): 1080x1920, 9.2 s.
// Row i of both people's strips plays the last 1 s of its pre-shutter clip at T[i] (both at once, rows top to bottom),
// freezes on the shutter with a flash; the strips touch, rush together, hard cut to the decorated strip ("착!"), which
// hangs and sways above the save date. Only text: the title and the date. Two looks: "cute" (말랑부스) and "film".
// This file only draws a frame from t; compose.js recordMaking feeds it the clip frames and encodes.
import { EN, tr } from "./i18n.js";
export const MAKING = { W: 1080, H: 1920, L: 9.2, FPS: 30 };
export const ROW_T = [0.6, 1.6, 2.6, 3.6], ROW_DUR = 1;
const TOUCH = 4.75, RUSH = 5.15, SNAP = 5.43;
const U = MAKING.W / 100;                       // 1 cqw of the mockups in px
const RW = 38, RX = [10.5, 51.5], RY = 36, CW = 32, CH = 24, PITCH = 26.6, PAD = 3;
const cl = x => Math.min(1, Math.max(0, x)), eo = x => 1 - Math.pow(1 - cl(x), 3);
const flash = (t, at, d = 0.35) => { const k = t - at; return k >= 0 && k < d ? 1 - k / d : 0; };

const THEMES = {
  cute: { bg: "#fff0f5", veil: "#fff0f5", flash: "#ffffff", ink: "#3a1f2d", accent: "#ff7eb6", deep: "#c2276a", roll: "#ffffff",
    cell: "#ffd6e5", ghost: "rgba(255,255,255,.85)", titleFont: `400 ${9.4 * U}px Jua, "Noto Sans KR", sans-serif`,
    dateFont: `400 ${5.6 * U}px Jua, "Noto Sans KR", sans-serif`, stampFont: `400 ${8.6 * U}px Jua, "Noto Sans KR", sans-serif` },
  film: { bg: "#cfd0cc", veil: "#cfd0cc", flash: "#d6ff3d", ink: "#0d0d0d", accent: "#d6ff3d", deep: "#0d0d0d", roll: "#0d0d0d",
    cell: "#222222", ghost: "rgba(255,255,255,.33)", titleFont: `900 ${10 * U}px "Noto Sans KR", sans-serif`,
    dateFont: `700 ${4.6 * U}px "Space Grotesk", "Noto Sans KR", monospace`, stampFont: `900 ${8.4 * U}px "Noto Sans KR", sans-serif` },
};
export const makingFonts = ui => ui === "cute" ? ['400 60px "Jua"'] : ['900 60px "Noto Sans KR"', '700 60px "Space Grotesk"'];

const rr = (x, l, t, w, h, r) => { x.beginPath(); x.roundRect(l, t, w, h, r); };
function background(x, th, ui) {
  const { W, H } = MAKING;
  if (ui === "cute") {
    const g = x.createRadialGradient(W / 2, H * 0.38, 0, W / 2, H * 0.38, H * 0.62);
    g.addColorStop(0, "#ffffff"); g.addColorStop(0.55, "#fff0f5"); g.addColorStop(1, "#ffd6e5");
    x.fillStyle = g; x.fillRect(0, 0, W, H);
    x.fillStyle = "rgba(255,126,182,.11)";                        // the dot pattern (6 cqw grid, 0.5 cqw dots)
    for (let py = 6 * U / 2; py < H; py += 6 * U) for (let px = 6 * U / 2; px < W; px += 6 * U) { x.beginPath(); x.arc(px, py, 0.5 * U, 0, 7); x.fill(); }
  } else {
    x.fillStyle = th.bg; x.fillRect(0, 0, W, H);
    x.fillStyle = "rgba(0,0,0,.04)";                              // rule-of-thirds lines, like the camera grid
    for (const k of [1 / 3, 2 / 3]) { x.fillRect(W * k, 0, 0.3 * U, H); x.fillRect(0, H * k, W, 0.2 * U); }
  }
}
function title(x, th, ui) {
  x.save(); x.textAlign = "center"; x.textBaseline = "top"; x.font = th.titleFont; x.fillStyle = th.ink;
  const y = 21 * U;
  if (ui === "cute") {
    x.fillText(tr("따로 같이 네컷"), MAKING.W / 2, y);
    const w = x.measureText(tr("따로 같이 네컷")).width;
    x.font = `400 ${5.6 * U}px Jua, sans-serif`; x.fillStyle = th.accent;
    x.fillText("✦", MAKING.W / 2 - w / 2 - 4 * U, y + 1.6 * U); x.fillText("✦", MAKING.W / 2 + w / 2 + 4 * U, y + 1.6 * U);
  } else {                                                          // "같이" on a lime block, the app's em style
    const parts = EN ? ["Apart ", "Together", ""] : ["따로 ", "같이", " 네컷"], ws = parts.map(p => x.measureText(p).width), tot = ws.reduce((s, v) => s + v);
    let px = MAKING.W / 2 - tot / 2; x.textAlign = "left";
    parts.forEach((p, k) => {
      if (k === 1) { x.fillStyle = th.accent; x.fillRect(px - 1.2 * U, y - 0.6 * U, ws[k] + 2.4 * U, 11.6 * U); x.fillStyle = th.ink; }
      x.fillText(p, px, y); px += ws[k];
    });
  }
  x.restore();
}
function ghost(x, th, l, t, w, h) {
  x.save(); x.strokeStyle = th.ghost; x.lineWidth = 0.5 * U; x.setLineDash([1.2 * U, 1 * U]);
  const gw = w * 0.4, gx = l + (w - gw) / 2, gy = t + h * 0.14;
  x.beginPath(); x.moveTo(gx, t + h); x.lineTo(gx, gy + gw / 2); x.arc(gx + gw / 2, gy + gw / 2, gw / 2, Math.PI, 0); x.lineTo(gx + gw, t + h); x.stroke();
  x.restore();
}
function gate(x, th, l, t, w, h) {                               // film: lime corner brackets on the cell being shot
  const k = 5 * U, s = 0.9 * U; x.fillStyle = th.accent;
  for (const [cx, cy, dx, dy] of [[l, t, 1, 1], [l + w, t, -1, 1], [l, t + h, 1, -1], [l + w, t + h, -1, -1]]) {
    x.fillRect(dx > 0 ? cx : cx - k, dy > 0 ? cy : cy - s, k, s); x.fillRect(dx > 0 ? cx : cx - s, dy > 0 ? cy : cy - k, s, k);
  }
}
function roll(x, th, ui, k, t, cells) {
  const fadeIn = eo(t / 0.5), gap = RX[1] - RX[0] - RW, toCenter = (RX[1] - RX[0]) / 2;
  const touch = eo((t - TOUCH) / 0.25) * gap / 2, rush = Math.pow(cl((t - RUSH) / (SNAP - RUSH)), 3) * (toCenter - gap / 2);
  const dx = (k ? -1 : 1) * (touch + rush) * U, dy = (1 - fadeIn) * 8 * U, sc = 1 + 0.03 * eo((t - TOUCH) / 0.25);
  const l = RX[k] * U, top = RY * U, w = RW * U, h = (4 * PITCH + 5) * U;
  x.save(); x.globalAlpha = fadeIn;
  x.translate(l + w / 2 + dx, top + h / 2 + dy); x.scale(sc, sc); x.translate(-(l + w / 2), -(top + h / 2));
  if (ui === "cute") {
    x.save(); x.shadowColor = "rgba(194,39,106,.14)"; x.shadowBlur = 4 * U; x.shadowOffsetY = 1.8 * U;
    x.fillStyle = th.roll; rr(x, l, top, w, h, 1.4 * U); x.fill(); x.restore();
  } else {
    x.fillStyle = th.roll; x.fillRect(l, top, w, h);
    x.fillStyle = "#ffffff";                                      // sprocket holes down both edges
    for (let y = top + 1.2 * U; y < top + h - 1.8 * U; y += 4.2 * U) { x.fillRect(l + 0.5 * U, y, 1.6 * U, 1.8 * U); x.fillRect(l + w - 2.1 * U, y, 1.6 * U, 1.8 * U); }
  }
  for (let i = 0; i < 4; i++) {
    const cx = l + PAD * U, cy = top + (PAD + i * PITCH) * U, cw = CW * U, chh = CH * U, c = cells[k][i];
    const t0 = ROW_T[i], playing = t >= t0 && t < t0 + ROW_DUR, bump = 1 + 0.05 * flash(t, t0 + ROW_DUR, 0.25);
    x.save(); x.translate(cx + cw / 2, cy + chh / 2); x.scale(bump, bump); x.translate(-(cx + cw / 2), -(cy + chh / 2));
    x.save(); if (ui === "cute") rr(x, cx, cy, cw, chh, 0.8 * U); else { x.beginPath(); x.rect(cx, cy, cw, chh); }
    x.clip(); x.fillStyle = th.cell; x.fillRect(cx, cy, cw, chh);
    if (c) {                                                      // cover-fit the raw frame into the cell
      const s = Math.max(cw / c.width, chh / c.height), dw = c.width * s, dh = c.height * s;
      x.drawImage(c, cx + (cw - dw) / 2, cy + (chh - dh) / 2, dw, dh);
    } else ghost(x, th, cx, cy, cw, chh);
    const f = flash(t, t0 + ROW_DUR);
    if (f) { x.globalAlpha = f; x.fillStyle = "#ffffff"; x.fillRect(cx, cy, cw, chh); x.globalAlpha = 1; }
    x.restore();
    if (playing) {
      if (ui === "cute") { x.strokeStyle = th.accent; x.lineWidth = 0.9 * U; rr(x, cx + 0.45 * U, cy + 0.45 * U, cw - 0.9 * U, chh - 0.9 * U, 0.8 * U); x.stroke(); }
      else gate(x, th, cx, cy, cw, chh);
    }
    x.restore();
  }
  x.restore();
}
// the finished strip's box: centred, inside 35..148 cqw, a vertical strip at most 41 cqw wide, a horizontal one 82
function finBox(strip) {
  const pad = 2.2, maxW = strip.width > strip.height ? 82 : 41, maxH = 113;
  const s = Math.min((maxW - 2 * pad) / strip.width, (maxH - 2 * pad) / strip.height);
  const w = strip.width * s + 2 * pad, h = strip.height * s + 2 * pad;
  return { l: 50 - w / 2, t: 35 + (maxH - h) / 2, w, h, pad, s };
}
function finished(x, th, ui, q, strip) {
  const b = finBox(strip), pad = ui === "cute" ? b.pad : 1.2;
  const shake = q < 0.07 ? (q < 0.035 ? 1.2 : -1.2) : 0;
  const sway = q > 0.3 ? 4 * Math.sin((q - 0.3) * 2.6) * Math.exp(-(q - 0.3) * 0.55) : 0;
  const sc = 1.06 - 0.06 * eo(q / 0.25);
  const px = (b.l + b.w / 2 + shake) * U, py = b.t * U;            // pivot: the pin / tape at the top centre
  x.save(); x.translate(px, py); x.rotate(sway * Math.PI / 180); x.scale(sc, sc); x.translate(-(b.l + b.w / 2) * U, -b.t * U);
  const l = b.l * U, t = b.t * U, w = b.w * U, h = b.h * U;
  if (ui === "cute") {
    x.save(); x.shadowColor = "rgba(200,60,120,.25)"; x.shadowBlur = 5 * U; x.shadowOffsetY = 2 * U; x.fillStyle = "#fff"; x.fillRect(l, t, w, h); x.restore();
    x.strokeStyle = "#ffd0e0"; x.lineWidth = 0.3 * U; x.strokeRect(l, t, w, h);
  } else {
    x.fillStyle = th.accent; x.fillRect(l + 2 * U, t + 2 * U, w, h);   // the lime offset shadow
    x.fillStyle = th.roll; x.fillRect(l - 0.5 * U, t - 0.5 * U, w + U, h + U);
  }
  x.drawImage(strip, l + pad * U, t + pad * U, w - 2 * pad * U, h - 2 * pad * U);
  if (ui === "cute") {                                              // the pin
    const g = x.createRadialGradient(l + w / 2 - U, t - 1.9 * U, 0, l + w / 2, t - 0.9 * U, 2.7 * U);
    g.addColorStop(0, "#ff9cc6"); g.addColorStop(0.6, th.deep); g.addColorStop(1, th.deep);
    x.fillStyle = "#8a1748"; x.beginPath(); x.arc(l + w / 2, t - 0.2 * U, 2.7 * U, 0, 7); x.fill();
    x.fillStyle = g; x.beginPath(); x.arc(l + w / 2, t - 0.9 * U, 2.7 * U, 0, 7); x.fill();
  } else {                                                          // the lime tape
    x.fillStyle = "#000"; x.fillRect(l + w / 2 - 6.5 * U, t - 3.1 * U, 13 * U, 5.4 * U);
    x.fillStyle = th.accent; x.fillRect(l + w / 2 - 6 * U, t - 2.6 * U, 12 * U, 4.4 * U);
  }
  x.restore();
  return b;
}
function stamp(x, th, ui, q, b) {
  const a = Math.min(cl(q / 0.15), 1 - cl((q - 0.8) / 0.3));
  if (a <= 0) return;
  const s = 1.7 - 0.7 * eo(q / 0.25);
  x.save(); x.globalAlpha = a; x.translate((b.l + b.w - 3) * U, (b.t + b.h * 0.62) * U); x.rotate(8 * Math.PI / 180); x.scale(s, s);
  x.font = th.stampFont; x.textAlign = "center"; x.textBaseline = "middle";
  const tw = x.measureText(tr("착!")).width, bw = tw + 7.6 * U, bh = 11.8 * U;
  if (ui === "cute") {
    x.fillStyle = "#fff"; rr(x, -bw / 2 - 0.7 * U, -bh / 2 - 0.7 * U, bw + 1.4 * U, bh + 1.4 * U, 2.4 * U); x.fill();
    x.fillStyle = th.deep; rr(x, -bw / 2, -bh / 2, bw, bh, 2 * U); x.fill(); x.fillStyle = "#fff";
  } else {
    x.fillStyle = "#000"; x.fillRect(-bw / 2 + 1.2 * U, -bh / 2 + 1.2 * U, bw, bh);
    x.fillRect(-bw / 2 - 0.8 * U, -bh / 2 - 0.8 * U, bw + 1.6 * U, bh + 1.6 * U);
    x.fillStyle = th.accent; x.fillRect(-bw / 2, -bh / 2, bw, bh); x.fillStyle = "#000";
  }
  x.fillText(tr("착!"), 0, 0.4 * U);
  x.restore();
}
const SPARKS = [[12, 44], [84, 58], [13, 128], [84, 140]];
function sparks(x, th, ui, q) {
  SPARKS.forEach(([sx, sy], i) => {
    const k = q - 0.25 - i * 0.1;
    if (k <= 0) return;
    x.save(); x.translate(sx * U, sy * U);
    if (ui === "cute") {
      x.globalAlpha = 0.55 + 0.45 * Math.sin(k * 3 + i); const s = 0.75 + 0.25 * Math.sin(k * 3.4 + i);
      x.rotate(k * 30 * Math.PI / 180); x.scale(s, s);
      x.fillStyle = "#ffc93c"; x.font = `400 ${6.4 * U}px Jua, sans-serif`; x.textAlign = "center"; x.textBaseline = "middle"; x.fillText("✦", 0, 0);
    } else {
      const s = 0.6 + 0.4 * eo(k / 0.2); x.rotate((45 + 90 * Math.floor(k * 2.5 + i)) * Math.PI / 180); x.scale(s, s);
      x.fillStyle = "#000"; x.fillRect(-2 * U, -2 * U, 4 * U, 4 * U); x.fillStyle = th.accent; x.fillRect(-1.5 * U, -1.5 * U, 3 * U, 3 * U);
    }
    x.restore();
  });
}
function dateLine(x, th, ui, q, b, text) {
  const a = eo((q - 0.7) / 0.5);
  if (a <= 0) return;
  const y = (b.t + b.h + 6) * U + (1 - a) * 3 * U;
  x.save(); x.globalAlpha = a; x.font = th.dateFont; x.textAlign = "center"; x.textBaseline = "middle";
  if (x.letterSpacing !== undefined) x.letterSpacing = `${(ui === "cute" ? 0.12 : 0.1) * (ui === "cute" ? 5.6 : 4.6) * U}px`;
  const w = x.measureText(text).width;
  if (ui === "cute") {
    x.fillStyle = th.deep; x.fillText(text, MAKING.W / 2, y);
    x.globalAlpha = a * 0.6; x.fillStyle = th.accent;
    x.fillRect(MAKING.W / 2 - w / 2 - 9.6 * U, y - 0.2 * U, 7 * U, 0.4 * U); x.fillRect(MAKING.W / 2 + w / 2 + 2.6 * U, y - 0.2 * U, 7 * U, 0.4 * U);
  } else {
    x.fillStyle = "#0d0d0d"; x.fillRect(MAKING.W / 2 - w / 2 - 3.6 * U, y - 4.3 * U, w + 7.2 * U, 8.6 * U);
    x.fillStyle = th.accent; x.fillText(text, MAKING.W / 2, y + 0.2 * U);
  }
  x.restore();
}

// t: s into the video; cells[k][i]: person k (0 = A, 1 = B) row i's current picture (a canvas: live frame while it
// plays, its shutter frame after), null before its turn; strip: the decorated still strip; date: "2026. 10. 30"
export function drawMaking(x, t, { ui = "film", cells, strip, date }) {
  const th = THEMES[ui] || THEMES.film, { W, H, L } = MAKING;
  background(x, th, ui);
  title(x, th, ui);
  if (t < SNAP) { roll(x, th, ui, 0, t, cells); roll(x, th, ui, 1, t, cells); }
  else {
    const q = t - SNAP, b = finished(x, th, ui, q, strip);
    stamp(x, th, ui, q, b); sparks(x, th, ui, q); dateLine(x, th, ui, q, b, date);
  }
  const f = flash(t, SNAP, 0.4) * 0.9;
  if (f) { x.globalAlpha = f; x.fillStyle = th.flash; x.fillRect(0, 0, W, H); x.globalAlpha = 1; }
  const v = cl((t - (L - 0.3)) / 0.3);
  if (v) { x.globalAlpha = v; x.fillStyle = th.veil; x.fillRect(0, 0, W, H); x.globalAlpha = 1; }
}
export const makingDate = (d = new Date()) => `${d.getFullYear()}. ${String(d.getMonth() + 1).padStart(2, "0")}. ${String(d.getDate()).padStart(2, "0")}`;
