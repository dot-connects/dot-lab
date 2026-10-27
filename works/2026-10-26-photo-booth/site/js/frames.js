// Strip frames, drawn procedurally on the 600 x 1800 canvas. Each frame:
//   draw(ctx, L, { date, caption, cellRects })  background, cell borders, footer (cells are drawn after this)
//   over(ctx, L, { date, caption, cellRects })  optional stickers on top of the cells (corners only, never faces)
//   rects                                       optional cell rects (same 552:382 aspect as the default, so preview crop holds)
//   radius                                      cell corner radius; color = chip swatch.
// Footer: the caption (one line, <= 16 chars, fallback 따로 같이 네컷) + the date (YYYY.MM.DD), styled per frame.
// FRAMES order = display order, first = default (CONTRACT.md "Round 2 additions").

const FONT_CSS = "https://fonts.googleapis.com/css2?family=Black+Han+Sans&family=Jua&family=Do+Hyeon" +
  "&family=Gowun+Batang:wght@700&family=IBM+Plex+Sans+KR:wght@500;700&family=IBM+Plex+Mono:wght@500" +
  "&family=Rye&family=Press+Start+2P&display=block";
const TITLE = "따로 같이 네컷";
// Korean Google Fonts are split by unicode-range: load exactly the glyphs we draw (fixed strings + digits + caption).
const SAMPLE = TITLE + "우리둘 따로같이 네컷 지명수배 현상금 생일 축하 dear us WANTED REWARD HAPPY BIRTHDAY TG ▸AVSP·★♥ 0123456789.+-D";
const cap = c => (typeof c === "string" && c.trim()) || TITLE;

let cssReady = null;
// caption: pass the user's caption so its glyph slices are loaded before drawing.
export async function loadFrameFonts(frame, caption = "") {
  cssReady ||= new Promise(res => {
    const l = document.createElement("link");
    l.rel = "stylesheet"; l.href = FONT_CSS; l.onload = l.onerror = res;
    document.head.appendChild(l);
  });
  await cssReady;
  const s = SAMPLE + (caption || "");
  await Promise.all((frame?.fonts || []).map(f => document.fonts.load(f, s).catch(() => null)));
}

// ---------- drawing helpers ----------
const rng = seed => () => ((seed = (seed * 1103515245 + 12345) >>> 0) / 4294967296);
// max: shrink the font until the line fits (long captions); the px size in `font` is the largest size.
function text(ctx, s, x, y, font, { fill, stroke, lw = 0, align = "center", ls = 0, shadow, max } = {}) {
  ctx.save();
  ctx.font = font; ctx.textAlign = align; ctx.textBaseline = "middle"; ctx.lineJoin = "round";
  if ("letterSpacing" in ctx) ctx.letterSpacing = ls + "px";
  if (max) {
    const wd = ctx.measureText(s).width;
    if (wd > max) ctx.font = font.replace(/(\d+(?:\.\d+)?)px/, (_, n) => `${Math.floor(n * max / wd)}px`);
  }
  if (shadow) { ctx.shadowColor = shadow[0]; ctx.shadowBlur = shadow[1]; ctx.shadowOffsetY = shadow[2] || 0; }
  for (const [c, w] of [].concat(stroke || [])) { ctx.strokeStyle = c; ctx.lineWidth = w; ctx.strokeText(s, x, y); }
  ctx.shadowColor = "transparent";
  if (fill) { ctx.fillStyle = fill; ctx.fillText(s, x, y); }
  ctx.restore();
}
const textW = (ctx, s, font, ls = 0) => { ctx.save(); ctx.font = font; if ("letterSpacing" in ctx) ctx.letterSpacing = ls + "px";
  const w = ctx.measureText(s).width; ctx.restore(); return w; };
function heartPath(ctx, cx, cy, s) {          // s = width
  const k = s / 2;
  ctx.beginPath();
  ctx.moveTo(cx, cy + k * 0.9);
  ctx.bezierCurveTo(cx - k * 1.25, cy + k * 0.05, cx - k * 0.95, cy - k * 1.05, cx, cy - k * 0.42);
  ctx.bezierCurveTo(cx + k * 0.95, cy - k * 1.05, cx + k * 1.25, cy + k * 0.05, cx, cy + k * 0.9);
  ctx.closePath();
}
function heart(ctx, cx, cy, s, fill, line, lw = 0, rot = 0) {
  ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot); heartPath(ctx, 0, 0, s);
  if (line) { ctx.lineWidth = lw; ctx.strokeStyle = line; ctx.lineJoin = "round"; ctx.stroke(); }
  ctx.fillStyle = fill; ctx.fill(); ctx.restore();
}
function sparkle(ctx, cx, cy, r, fill, rot = 0) { // 4-point star with concave sides
  ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot); ctx.beginPath();
  for (let i = 0; i < 4; i++) {
    const a = i * Math.PI / 2, b = a + Math.PI / 4;
    ctx[i ? "lineTo" : "moveTo"](Math.cos(a) * r, Math.sin(a) * r);
    ctx.quadraticCurveTo(Math.cos(b) * r * 0.16, Math.sin(b) * r * 0.16, Math.cos(a + Math.PI / 2) * r, Math.sin(a + Math.PI / 2) * r);
  }
  ctx.fillStyle = fill; ctx.fill(); ctx.restore();
}
function star5(ctx, cx, cy, R, fill, line, lw, rot = 0) {
  ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot); ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const r = i % 2 ? R * 0.5 : R, a = -Math.PI / 2 + i * Math.PI / 5;
    ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  ctx.closePath(); ctx.lineJoin = "round";
  if (line) { ctx.lineWidth = lw; ctx.strokeStyle = line; ctx.stroke(); }
  ctx.fillStyle = fill; ctx.fill(); ctx.restore();
}
function cloud(ctx, x, y, s, fill = "#fff") {   // x,y = bottom centre
  ctx.save(); ctx.fillStyle = fill; ctx.beginPath();
  for (const [dx, dy, r] of [[-0.55, -0.28, 0.3], [-0.18, -0.5, 0.4], [0.25, -0.42, 0.34], [0.58, -0.22, 0.26]]) {
    ctx.moveTo(x + dx * s + r * s, y + dy * s); ctx.arc(x + dx * s, y + dy * s, r * s, 0, Math.PI * 2);
  }
  ctx.roundRect(x - 0.85 * s, y - 0.3 * s, 1.7 * s, 0.3 * s, 0.15 * s);
  ctx.fill(); ctx.restore();
}
function bow(ctx, cx, cy, s, fill, line) {      // s = total width
  const k = s / 2;
  ctx.save(); ctx.translate(cx, cy); ctx.lineJoin = "round"; ctx.lineCap = "round";
  ctx.lineWidth = Math.max(2, s * 0.035); ctx.strokeStyle = line; ctx.fillStyle = fill;
  for (const d of [-1, 1]) {                    // tails
    ctx.beginPath(); ctx.moveTo(d * k * 0.06, k * 0.05);
    ctx.bezierCurveTo(d * k * 0.25, k * 0.5, d * k * 0.3, k * 0.75, d * k * 0.5, k * 1.05);
    ctx.lineTo(d * k * 0.28, k * 0.98); ctx.lineTo(d * k * 0.2, k * 1.12);
    ctx.bezierCurveTo(d * k * 0.1, k * 0.8, d * k * 0.02, k * 0.4, -d * k * 0.05, k * 0.08);
    ctx.closePath(); ctx.fill(); ctx.stroke();
  }
  for (const d of [-1, 1]) {                    // loops
    ctx.beginPath(); ctx.moveTo(0, 0);
    ctx.bezierCurveTo(d * k * 0.35, -k * 0.62, d * k * 1.05, -k * 0.5, d * k * 0.95, -k * 0.02);
    ctx.bezierCurveTo(d * k * 0.9, k * 0.38, d * k * 0.35, k * 0.3, 0, 0);
    ctx.fill(); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(d * k * 0.2, -k * 0.08);
    ctx.quadraticCurveTo(d * k * 0.55, -k * 0.2, d * k * 0.72, -k * 0.05); ctx.stroke();
  }
  ctx.beginPath(); ctx.ellipse(0, 0, k * 0.17, k * 0.2, 0, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
  ctx.restore();
}
function strawberry(ctx, cx, cy, s, rot = 0) {
  ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot);
  const k = s / 2;
  ctx.beginPath(); ctx.moveTo(0, k * 1.0);
  ctx.bezierCurveTo(-k * 0.9, k * 0.45, -k * 1.0, -k * 0.55, -k * 0.35, -k * 0.6);
  ctx.quadraticCurveTo(0, -k * 0.7, k * 0.35, -k * 0.6);
  ctx.bezierCurveTo(k * 1.0, -k * 0.55, k * 0.9, k * 0.45, 0, k * 1.0);
  ctx.fillStyle = "#f2475f"; ctx.fill();
  ctx.fillStyle = "#ffe9a8";
  for (const [x, y] of [[-0.4, -0.25], [0, -0.3], [0.4, -0.25], [-0.25, 0.1], [0.2, 0.12], [-0.05, 0.45], [0.42, 0.18], [-0.45, 0.15]]) {
    ctx.beginPath(); ctx.ellipse(x * k, y * k, k * 0.05, k * 0.08, 0, 0, Math.PI * 2); ctx.fill();
  }
  ctx.fillStyle = "#5fae4f";
  for (const a of [-1.2, -0.6, 0, 0.6, 1.2]) {
    ctx.save(); ctx.translate(0, -k * 0.6); ctx.rotate(a);
    ctx.beginPath(); ctx.ellipse(0, k * 0.2, k * 0.12, k * 0.26, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore();
  }
  ctx.fillRect(-k * 0.05, -k * 0.95, k * 0.1, k * 0.32);
  ctx.restore();
}
function cherry(ctx, cx, cy, s, rot = 0) {
  ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot);
  const k = s / 2;
  ctx.strokeStyle = "#5c8f3a"; ctx.lineWidth = Math.max(1.5, k * 0.09); ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(-k * 0.45, k * 0.25); ctx.quadraticCurveTo(-k * 0.2, -k * 0.5, k * 0.15, -k * 0.85);
  ctx.moveTo(k * 0.45, k * 0.35); ctx.quadraticCurveTo(k * 0.3, -k * 0.4, k * 0.15, -k * 0.85); ctx.stroke();
  ctx.fillStyle = "#7cc05a"; ctx.beginPath(); ctx.ellipse(k * 0.42, -k * 0.85, k * 0.3, k * 0.13, -0.4, 0, Math.PI * 2); ctx.fill();
  for (const [x, y] of [[-0.45, 0.5], [0.45, 0.6]]) {
    ctx.fillStyle = "#e0263f"; ctx.beginPath(); ctx.arc(x * k, y * k, k * 0.36, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#ffffffaa"; ctx.beginPath(); ctx.arc(x * k - k * 0.12, y * k - k * 0.12, k * 0.09, 0, Math.PI * 2); ctx.fill();
  }
  ctx.restore();
}
function balloon(ctx, x, y, s, fill, rot = 0) {   // x,y = balloon centre, string hangs down
  ctx.save(); ctx.translate(x, y); ctx.rotate(rot);
  ctx.strokeStyle = "#9c8a7a"; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(0, s * 0.62);
  ctx.bezierCurveTo(s * 0.18, s * 0.95, -s * 0.18, s * 1.2, s * 0.05, s * 1.55); ctx.stroke();
  ctx.fillStyle = fill; ctx.beginPath(); ctx.ellipse(0, 0, s * 0.46, s * 0.58, 0, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.moveTo(-s * 0.08, s * 0.64); ctx.lineTo(s * 0.08, s * 0.64); ctx.lineTo(0, s * 0.55); ctx.fill();
  ctx.fillStyle = "#ffffff80"; ctx.beginPath(); ctx.ellipse(-s * 0.17, -s * 0.22, s * 0.08, s * 0.15, 0.5, 0, Math.PI * 2); ctx.fill();
  ctx.restore();
}
const borders = (ctx, rects, pad, r, fill, shadow) => {
  ctx.save();
  if (shadow) { ctx.shadowColor = shadow; ctx.shadowBlur = 10; ctx.shadowOffsetY = 3; }
  ctx.fillStyle = fill;
  for (const c of rects) { ctx.beginPath(); ctx.roundRect(c.x - pad, c.y - pad, c.w + pad * 2, c.h + pad * 2, r + pad); ctx.fill(); }
  ctx.restore();
};
const vgrad = (ctx, W, H, stops) => {
  const g = ctx.createLinearGradient(0, 0, 0, H); stops.forEach((c, i) => g.addColorStop(i / (stops.length - 1), c));
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
};
const footY = (L, rects) => { const b = rects[3].y + rects[3].h; return b + (L.H - b) / 2; };
// 4 stacked rects of width w (default cell aspect), centred, starting at y0 with gap g
const stack = (w, y0, g) => { const h = Math.round(w * 382 / 552), x = (600 - w) / 2;
  return [0, 1, 2, 3].map(i => ({ x, y: y0 + i * (h + g), w, h })); };

// film strip: narrower cells between sprocket rails
const FILM_GAP = 24, FILM_RECTS = stack(500, 36, FILM_GAP);
const WANTED_RECTS = stack(480, 176, 14);
const VS_RECTS = stack(500, 132, 14);
const MONO = '500 22px "IBM Plex Mono"';

export const FRAMES = [
  {
    id: "white", label: "화이트", color: "#fdfdfd", radius: 2,
    fonts: ['46px "Black Han Sans"', MONO],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#fdfdfd"; ctx.fillRect(0, 0, L.W, L.H);
      borders(ctx, cellRects, 1.5, 2, "#e4e0e3");
      const y = footY(L, cellRects);
      text(ctx, cap(caption), L.W / 2, y - 18, '46px "Black Han Sans"', { fill: "#2a2530", ls: 1, max: 520 });
      const dw = textW(ctx, date, MONO, 4);
      text(ctx, date, L.W / 2 - 13, y + 36, MONO, { fill: "#2a2530c8", ls: 4 });
      heart(ctx, L.W / 2 + dw / 2 + 10, y + 36, 18, "#f0789a");
    },
  },
  {
    id: "pink", label: "핑크", color: "#ffc6d6", radius: 10,
    fonts: ['54px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      vgrad(ctx, L.W, L.H, ["#ffe0ea", "#ffc9da", "#ffb9cf"]);
      const r = rng(7);
      for (let i = 0; i < 70; i++) sparkle(ctx, r() * L.W, r() * L.H, 3 + r() * 5, "#ffffffb0", r());
      borders(ctx, cellRects, 7, 10, "#fff", "#e0789a40");
      const y = footY(L, cellRects);
      text(ctx, cap(caption), L.W / 2, y - 14, '54px "Jua"', { fill: "#fff", stroke: [["#ee6f97", 8]], max: 360 });
      text(ctx, date, L.W / 2, y + 42, '26px "Jua"', { fill: "#d94a76", ls: 2 });
      heart(ctx, 80, y - 18, 32, "#ff8fb1", "#fff", 5, -0.25);
      heart(ctx, L.W - 80, y - 18, 32, "#ff8fb1", "#fff", 5, 0.25);
    },
    over(ctx, L, { cellRects: c }) {
      heart(ctx, c[0].x + c[0].w - 8, c[0].y + 6, 40, "#ff7aa2", "#fff", 6, 0.2);
      sparkle(ctx, c[0].x + c[0].w - 44, c[0].y - 2, 13, "#fff");
      heart(ctx, c[2].x + 8, c[2].y + c[2].h - 6, 34, "#ff9dbb", "#fff", 6, -0.25);
      sparkle(ctx, c[1].x + 4, c[1].y + 10, 16, "#fff");
      sparkle(ctx, c[3].x + c[3].w - 6, c[3].y + c[3].h - 8, 18, "#fff");
    },
  },
  {
    id: "check", label: "체크", color: "#ffb3c8", radius: 4,
    fonts: ['52px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#fffafb"; ctx.fillRect(0, 0, L.W, L.H);
      ctx.fillStyle = "#ff8fb04a";
      for (let x = -10; x < L.W; x += 56) ctx.fillRect(x, 0, 28, L.H);
      for (let y = -10; y < L.H; y += 56) ctx.fillRect(0, y, L.W, 28);
      borders(ctx, cellRects, 8, 4, "#fff", "#c0406a30");
      const y = footY(L, cellRects);
      ctx.save(); ctx.fillStyle = "#fff"; ctx.shadowColor = "#c0406a30"; ctx.shadowBlur = 10; ctx.shadowOffsetY = 3;
      ctx.beginPath(); ctx.roundRect(90, y - 64, L.W - 180, 128, 18); ctx.fill(); ctx.restore();
      ctx.save(); ctx.setLineDash([9, 7]); ctx.strokeStyle = "#f28fae"; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.roundRect(99, y - 55, L.W - 198, 110, 12); ctx.stroke(); ctx.restore();
      text(ctx, cap(caption), L.W / 2, y - 14, '52px "Jua"', { fill: "#d6406e", max: 370 });
      text(ctx, date, L.W / 2, y + 32, '26px "Jua"', { fill: "#e0688f", ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      bow(ctx, L.W / 2, c[0].y - 2, 86, "#ff8fb0", "#d6406e");
    },
  },
  {
    id: "coquette", label: "리본", color: "#ffe3ea", radius: 14,
    fonts: ['700 54px "Gowun Batang"', '700 24px "Gowun Batang"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#fff1f4"; ctx.fillRect(0, 0, L.W, L.H);
      ctx.fillStyle = "#ffd3de";
      for (let y = 10, row = 0; y < L.H; y += 26, row++) for (let x = row % 2 ? 23 : 10; x < L.W; x += 26) {
        ctx.beginPath(); ctx.arc(x, y, 2.6, 0, 7); ctx.fill();
      }
      ctx.fillStyle = "#fff";                       // scalloped edging
      for (const c of cellRects) {
        const e = 5, step = 15, r = 8.5;
        ctx.beginPath(); ctx.roundRect(c.x - e, c.y - e, c.w + 2 * e, c.h + 2 * e, 14); ctx.fill();
        for (let x = c.x + 4; x <= c.x + c.w - 4; x += step) for (const y of [c.y - e, c.y + c.h + e]) { ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.fill(); }
        for (let y = c.y + 4; y <= c.y + c.h - 4; y += step) for (const x of [c.x - e, c.x + c.w + e]) { ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.fill(); }
      }
      const y = footY(L, cellRects);
      text(ctx, "dear us", L.W / 2, y - 56, '700 24px "Gowun Batang"', { fill: "#d0688a", ls: 5 });
      bow(ctx, L.W / 2 - 96, y - 58, 40, "#f7a6ba", "#c95c7c"); bow(ctx, L.W / 2 + 96, y - 58, 40, "#f7a6ba", "#c95c7c");
      text(ctx, cap(caption), L.W / 2, y + 2, '700 54px "Gowun Batang"', { fill: "#b23a60", max: 500 });
      text(ctx, date, L.W / 2, y + 56, '700 24px "Gowun Batang"', { fill: "#c25d7d", ls: 3 });
    },
    over(ctx, L, { cellRects: c }) {
      bow(ctx, L.W / 2, c[0].y - 4, 96, "#f7a6ba", "#c95c7c");
      bow(ctx, c[2].x + 6, c[2].y - 6, 54, "#f7a6ba", "#c95c7c");
      bow(ctx, c[3].x + c[3].w - 6, c[3].y - 6, 54, "#f7a6ba", "#c95c7c");
    },
  },
  {
    id: "strawberry", label: "딸기", color: "#ffd0d6", radius: 12,
    fonts: ['52px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#ffeef0"; ctx.fillRect(0, 0, L.W, L.H);
      for (let y = 10, row = 0; y < L.H + 40; y += 62, row++) for (let x = row % 2 ? 12 : 43; x < L.W + 20; x += 62) {
        (row + Math.round(x / 62)) % 2 ? cherry(ctx, x, y, 22, 0.15) : strawberry(ctx, x, y, 21, -0.2);
      }
      borders(ctx, cellRects, 6, 12, "#fff", "#e2384f30");
      const y = footY(L, cellRects);
      ctx.save(); ctx.fillStyle = "#fffffff0"; ctx.beginPath(); ctx.roundRect(70, y - 64, L.W - 140, 128, 64); ctx.fill(); ctx.restore();
      text(ctx, cap(caption), L.W / 2, y - 14, '52px "Jua"', { fill: "#e2384f", max: 390 });
      text(ctx, date, L.W / 2, y + 34, '26px "Jua"', { fill: "#5f9338", ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      strawberry(ctx, c[0].x + 10, c[0].y + 12, 58, -0.3);
      cherry(ctx, c[1].x + c[1].w - 12, c[1].y + c[1].h - 14, 58, 0.2);
      strawberry(ctx, c[3].x + c[3].w - 10, c[3].y + c[3].h - 16, 52, 0.3);
    },
  },
  {
    id: "sky", label: "하늘", color: "#cfe6ff", radius: 12,
    fonts: ['50px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      vgrad(ctx, L.W, L.H, ["#b9dcff", "#d6ebff", "#eef7ff"]);
      const r = rng(3);
      for (let i = 0; i < 50; i++) { ctx.fillStyle = "#ffffffa0"; ctx.beginPath(); ctx.arc(r() * L.W, r() * L.H, 1.5 + r() * 2.5, 0, 7); ctx.fill(); }
      borders(ctx, cellRects, 6, 12, "#fff", "#4a8fd630");
      const y = footY(L, cellRects);
      cloud(ctx, 70, L.H - 4, 110); cloud(ctx, L.W - 60, L.H + 6, 130); cloud(ctx, L.W / 2 + 40, L.H + 20, 90, "#ffffffd0");
      text(ctx, cap(caption), L.W / 2, y - 22, '50px "Jua"', { fill: "#2f78d0", stroke: [["#fff", 8]], max: 500 });
      text(ctx, date, L.W / 2, y + 30, '26px "Jua"', { fill: "#3f86d6", stroke: [["#fff", 5]], ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      cloud(ctx, c[0].x + c[0].w - 30, c[0].y + 26, 92);
      cloud(ctx, c[2].x + 34, c[2].y + c[2].h + 14, 80);
    },
  },
  {
    id: "film", label: "필름", color: "#1b1b1b", radius: 2, rects: FILM_RECTS,
    fonts: ['42px "Do Hyeon"', '500 26px "IBM Plex Mono"', '500 14px "IBM Plex Mono"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#1b1a19"; ctx.fillRect(0, 0, L.W, L.H);
      ctx.fillStyle = "#f4efe6";
      for (let y = 12; y < L.H - 10; y += 40) for (const x of [16, L.W - 32]) { ctx.beginPath(); ctx.roundRect(x, y, 16, 22, 4); ctx.fill(); }
      cellRects.forEach((c, i) => {
        text(ctx, `▸ ${i + 1}A`, c.x + 2, c.y + c.h + FILM_GAP / 2, '500 14px "IBM Plex Mono"', { fill: "#ffb347", align: "left", ls: 1 });
        text(ctx, "TG 400", c.x + c.w - 2, c.y + c.h + FILM_GAP / 2, '500 14px "IBM Plex Mono"', { fill: "#ffb347b0", align: "right", ls: 2 });
      });
      const y = footY(L, cellRects);
      text(ctx, cap(caption), L.W / 2, y - 22, '42px "Do Hyeon"', { fill: "#f4efe6", ls: 2, max: 480 });
      text(ctx, date, L.W / 2, y + 32, '500 26px "IBM Plex Mono"', { fill: "#ff8a3d", ls: 5, shadow: ["#ff6a1a", 10] });
    },
  },
  {
    id: "duo", label: "우리 둘", color: "#ffd4c7", radius: 8,
    fonts: ['58px "Jua"', '28px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#ffd6c9"; ctx.fillRect(0, 0, L.W / 2, L.H);
      ctx.fillStyle = "#cfe5ff"; ctx.fillRect(L.W / 2, 0, L.W / 2, L.H);
      ctx.save(); ctx.strokeStyle = "#fff"; ctx.lineWidth = 3; ctx.setLineDash([10, 9]);
      ctx.beginPath(); ctx.moveTo(L.W / 2, 0); ctx.lineTo(L.W / 2, L.H); ctx.stroke(); ctx.restore();
      borders(ctx, cellRects, 5, 8, "#fff");
      const y = footY(L, cellRects);
      text(ctx, "따로", L.W / 4, y - 20, '58px "Jua"', { fill: "#e8664f", stroke: [["#fff", 9]] });
      text(ctx, "같이", L.W * 3 / 4, y - 20, '58px "Jua"', { fill: "#3f7fd6", stroke: [["#fff", 9]] });
      heart(ctx, L.W / 2, y - 20, 36, "#ff7a8e", "#fff", 6);
      const t = typeof caption === "string" && caption.trim() ? caption.trim() : "네컷";
      text(ctx, `${t} · ${date}`, L.W / 2, y + 44, '28px "Jua"', { fill: "#4a4450", stroke: [["#fff", 6]], ls: 1, max: 540 });
    },
    over(ctx, L, { cellRects: c }) {
      for (let i = 0; i < 3; i++) heart(ctx, L.W / 2, c[i].y + c[i].h + 8, 26, "#ff7a8e", "#fff", 5);
    },
  },
  {
    id: "black", label: "블랙", color: "#141216", radius: 2,
    fonts: ['46px "Black Han Sans"', MONO],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#141216"; ctx.fillRect(0, 0, L.W, L.H);
      const y = footY(L, cellRects);
      text(ctx, cap(caption), L.W / 2, y - 18, '46px "Black Han Sans"', { fill: "#f3f0f5", ls: 1, max: 520 });
      text(ctx, date, L.W / 2, y + 36, MONO, { fill: "#f3f0f5c8", ls: 4 });
    },
  },
  {
    id: "minimal", label: "미니멀", color: "#f5f0e6", radius: 0,
    fonts: ['500 72px "IBM Plex Sans KR"', '500 20px "IBM Plex Sans KR"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#f5f0e6"; ctx.fillRect(0, 0, L.W, L.H);
      const y = footY(L, cellRects), c = typeof caption === "string" && caption.trim();
      text(ctx, c || date.slice(5), L.M, y - 16, '500 72px "IBM Plex Sans KR"', { fill: "#2b2620", align: "left", ls: -1, max: L.W - 2 * L.M });
      text(ctx, `${TITLE}  ${date}`, L.M + 2, y + 50, '500 20px "IBM Plex Sans KR"', { fill: "#2b2620b0", align: "left", ls: 2 });
    },
  },
  {
    id: "chrome", label: "크롬", color: "#c9cfd8", radius: 6,
    fonts: ['58px "Black Han Sans"', MONO],
    draw(ctx, L, { date, caption, cellRects }) {
      const g = ctx.createLinearGradient(0, 0, L.W, L.H * 0.35);
      ["#f4f6f9", "#b8c0cc", "#eef1f5", "#9aa4b2", "#e6e9ee", "#c3cad4"].forEach((c, i, a) => g.addColorStop(i / (a.length - 1), c));
      ctx.fillStyle = g; ctx.fillRect(0, 0, L.W, L.H);
      borders(ctx, cellRects, 3, 6, "#1d2230");
      const y = footY(L, cellRects);
      text(ctx, cap(caption), L.W / 2, y - 16, '58px "Black Han Sans"', { fill: "#2e3542", stroke: [["#ffffff", 3]], ls: 3, max: 470, shadow: ["#ffffff", 0, 2] });
      text(ctx, date, L.W / 2, y + 42, MONO, { fill: "#1d2230", ls: 5 });
      for (const [x, yy, s] of [[48, y - 40, 22], [L.W - 52, y + 30, 16], [L.W - 70, y - 56, 10]]) {
        ctx.save(); ctx.shadowColor = "#fff"; ctx.shadowBlur = 12; sparkle(ctx, x, yy, s, "#fff"); ctx.restore();
      }
    },
    over(ctx, L, { cellRects: c }) {
      for (const [x, y, s] of [[c[0].x + c[0].w - 10, c[0].y + 10, 24], [c[2].x + 10, c[2].y + c[2].h - 10, 20]]) {
        ctx.save(); ctx.shadowColor = "#fff"; ctx.shadowBlur = 14; sparkle(ctx, x, y, s, "#fff"); ctx.restore();
      }
    },
  },
  {
    // Y2K pastel: soft holographic gradient, glossy pastel stickers, white type with a pink-lilac outline
    id: "purikura", label: "Y2K", color: "#e9d9ff", radius: 18,
    fonts: ['58px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      const g = ctx.createLinearGradient(0, 0, L.W, L.H);
      ["#ffe1ef", "#efe2ff", "#dff3ff", "#e3fbf1", "#ffe8f3"].forEach((c, i, a) => g.addColorStop(i / (a.length - 1), c));
      ctx.fillStyle = g; ctx.fillRect(0, 0, L.W, L.H);
      ctx.save(); ctx.globalAlpha = 0.35; ctx.fillStyle = "#fff";       // holo sheen bands
      for (let k = -L.H; k < L.H; k += 230) { ctx.beginPath(); ctx.moveTo(0, k); ctx.lineTo(L.W, k + 260); ctx.lineTo(L.W, k + 320); ctx.lineTo(0, k + 60); ctx.fill(); }
      ctx.restore();
      const r = rng(11), pal = ["#ffb6d9", "#c9b6ff", "#a8e6ff", "#b9f2d9", "#fff1a8"];
      for (let i = 0; i < 46; i++) sparkle(ctx, r() * L.W, r() * L.H, 3 + r() * 6, i % 3 ? "#ffffffd0" : pal[i % 5], r());
      borders(ctx, cellRects, 8, 18, "#fff", "#b48cff30");
      const y = footY(L, cellRects);
      const t = ctx.createLinearGradient(0, y - 40, 0, y + 10);
      t.addColorStop(0, "#ff8cc6"); t.addColorStop(1, "#a98bff");
      text(ctx, cap(caption), L.W / 2, y - 14, '58px "Jua"', { fill: "#fff", stroke: [[t, 12]], max: 350, shadow: ["#a98bff55", 8, 3] });
      text(ctx, date, L.W / 2, y + 44, '26px "Jua"', { fill: "#8b6fe0", ls: 3 });
      star5(ctx, 74, y - 20, 22, "#fff1a8", "#fff", 5, -0.2);
      heart(ctx, L.W - 74, y - 20, 34, "#ffb6d9", "#fff", 5, 0.2);
    },
    over(ctx, L, { cellRects: c }) {
      star5(ctx, c[0].x + c[0].w - 14, c[0].y + 14, 30, "#fff1a8", "#fff", 6, 0.2);
      heart(ctx, c[1].x + 14, c[1].y + c[1].h - 14, 48, "#ffb6d9", "#fff", 6, -0.2);
      sparkle(ctx, c[1].x + 52, c[1].y + c[1].h - 46, 13, "#fff");
      star5(ctx, c[3].x + c[3].w - 16, c[3].y + c[3].h - 14, 24, "#b9f2d9", "#fff", 6, -0.2);
      heart(ctx, c[2].x + c[2].w - 10, c[2].y + 14, 30, "#c9b6ff", "#fff", 6, 0.3);
    },
  },
  {
    // 지명수배 meme poster: sepia paper, WANTED header, caption as the reward line
    id: "wanted", label: "지명수배", color: "#e3cc9c", radius: 0, rects: WANTED_RECTS,
    fonts: ['92px "Rye"', '30px "Black Han Sans"', '54px "Black Han Sans"', '24px "Black Han Sans"', MONO],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#ead8ae"; ctx.fillRect(0, 0, L.W, L.H);
      const v = ctx.createRadialGradient(L.W / 2, L.H / 2, L.W * 0.4, L.W / 2, L.H / 2, L.H * 0.62);
      v.addColorStop(0, "#00000000"); v.addColorStop(1, "#7a4e1c55"); ctx.fillStyle = v; ctx.fillRect(0, 0, L.W, L.H);
      const r = rng(5);
      for (let i = 0; i < 2600; i++) { ctx.fillStyle = r() < 0.5 ? "#6b4a2218" : "#fff6e020"; ctx.fillRect(r() * L.W, r() * L.H, 1 + r() * 2.5, 1 + r() * 2.5); }
      for (let i = 0; i < 6; i++) { ctx.fillStyle = "#8a5a2214"; ctx.beginPath(); ctx.ellipse(r() * L.W, r() * L.H, 30 + r() * 60, 20 + r() * 40, r() * 3, 0, 7); ctx.fill(); }
      ctx.strokeStyle = "#3b2412"; ctx.lineWidth = 4; ctx.strokeRect(12, 12, L.W - 24, L.H - 24);
      ctx.lineWidth = 1.5; ctx.strokeRect(20, 20, L.W - 40, L.H - 40);
      text(ctx, "WANTED", L.W / 2, 82, '92px "Rye"', { fill: "#3b2412", ls: 2, max: 520 });
      text(ctx, "★ 지명수배 ★", L.W / 2, 146, '30px "Black Han Sans"', { fill: "#7a2a14", ls: 6 });
      borders(ctx, cellRects, 4, 0, "#3b2412");
      const y = footY(L, cellRects);
      text(ctx, "현상금  REWARD", L.W / 2, y - 66, '24px "Black Han Sans"', { fill: "#7a2a14", ls: 4 });
      text(ctx, cap(caption), L.W / 2, y - 8, '54px "Black Han Sans"', { fill: "#3b2412", max: 500 });
      ctx.fillStyle = "#3b2412"; ctx.fillRect(L.W / 2 - 150, y + 30, 300, 2);
      text(ctx, date, L.W / 2, y + 60, MONO, { fill: "#3b2412d0", ls: 4 });
    },
  },
  {
    // game VS screen: red / blue diagonal split, P1 / P2 health bars, big VS in the footer
    id: "vs", label: "VS", color: "#ff4d5e", radius: 0, rects: VS_RECTS,
    fonts: ['20px "Press Start 2P"', '14px "Press Start 2P"', '110px "Black Han Sans"', '36px "Black Han Sans"'],
    draw(ctx, L, { date, caption, cellRects }) {
      ctx.fillStyle = "#3d6bff"; ctx.fillRect(0, 0, L.W, L.H);
      ctx.fillStyle = "#ff4058"; ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(L.W / 2 + 70, 0); ctx.lineTo(L.W / 2 - 70, L.H); ctx.lineTo(0, L.H); ctx.fill();
      ctx.save(); ctx.globalAlpha = 0.12; ctx.strokeStyle = "#fff"; ctx.lineWidth = 10;   // speed lines
      for (let k = -L.H; k < L.W + L.H; k += 46) { ctx.beginPath(); ctx.moveTo(k, 0); ctx.lineTo(k - L.H * 0.3, L.H); ctx.stroke(); }
      ctx.restore();
      const bar = (x, w, fill, dir) => {
        ctx.fillStyle = "#16162a"; ctx.fillRect(x - 4, 66, w + 8, 30);
        ctx.fillStyle = "#ffe14d"; ctx.fillRect(dir > 0 ? x : x + w * 0.15, 70, w * 0.85, 22);
        ctx.fillStyle = fill; ctx.fillRect(dir > 0 ? x + w * 0.85 : x, 70, w * 0.15, 22);
      };
      bar(50, 200, "#ff4058", 1); bar(L.W - 250, 200, "#3d6bff", -1);
      const st = [["#16162a", 6]];
      text(ctx, "P1", 50, 40, '20px "Press Start 2P"', { fill: "#fff", stroke: st, align: "left" });
      text(ctx, "P2", L.W - 50, 40, '20px "Press Start 2P"', { fill: "#fff", stroke: st, align: "right" });
      ctx.fillStyle = "#16162a"; ctx.beginPath(); ctx.roundRect(L.W / 2 - 36, 50, 72, 62, 8); ctx.fill();
      text(ctx, "99", L.W / 2, 84, '20px "Press Start 2P"', { fill: "#ffe14d" });
      borders(ctx, cellRects, 6, 0, "#16162a");
      borders(ctx, cellRects, 3, 0, "#fff");
      const y = footY(L, cellRects);
      ctx.save(); ctx.translate(L.W / 2, y - 40); ctx.transform(1, 0, -0.18, 1, 0, 0);
      text(ctx, "VS", 0, 0, '110px "Black Han Sans"', { fill: "#ffe14d", stroke: [["#16162a", 16], ["#fff", 6]] });
      ctx.restore();
      text(ctx, cap(caption), L.W / 2, y + 46, '36px "Black Han Sans"', { fill: "#fff", stroke: st, max: 520 });
      text(ctx, date, L.W / 2, y + 92, '14px "Press Start 2P"', { fill: "#fff", stroke: [["#16162a", 5]], ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      const tag = (x, y, s, fill, align) => {
        ctx.font = '14px "Press Start 2P"'; const w = ctx.measureText(s).width + 16;
        const x0 = align === "left" ? x : x - w;
        ctx.fillStyle = fill; ctx.fillRect(x0, y - 14, w, 28);
        text(ctx, s, x0 + w / 2, y + 1, '14px "Press Start 2P"', { fill: "#fff" });
      };
      tag(c[0].x + 6, c[0].y + c[0].h - 22, "P1", "#ff4058", "left");
      tag(c[0].x + c[0].w - 6, c[0].y + c[0].h - 22, "P2", "#3d6bff", "right");
    },
  },
  {
    id: "birthday", label: "생일", color: "#ffe9a8", radius: 12,
    fonts: ['52px "Jua"', '26px "Jua"', '24px "Jua"'],
    draw(ctx, L, { date, caption, cellRects }) {
      vgrad(ctx, L.W, L.H, ["#fff7dc", "#fff1d0", "#ffe6e6"]);
      const r = rng(21), pal = ["#ff8fab", "#ffd166", "#7fd1ff", "#a5e38c", "#c7a6ff"];
      for (let i = 0; i < 160; i++) {
        ctx.save(); ctx.translate(r() * L.W, r() * L.H); ctx.rotate(r() * 3); ctx.fillStyle = pal[i % 5];
        i % 4 ? ctx.fillRect(-5, -2.5, 10, 5) : (ctx.beginPath(), ctx.arc(0, 0, 3.5, 0, 7), ctx.fill()); ctx.restore();
      }
      borders(ctx, cellRects, 7, 12, "#fff", "#e0a04040");
      const y = footY(L, cellRects);
      balloon(ctx, 62, y - 40, 54, "#ff8fab", -0.12); balloon(ctx, 104, y - 20, 44, "#7fd1ff", 0.1);
      balloon(ctx, L.W - 62, y - 40, 54, "#ffd166", 0.12); balloon(ctx, L.W - 104, y - 20, 44, "#c7a6ff", -0.1);
      text(ctx, "HAPPY BIRTHDAY", L.W / 2, y - 60, '24px "Jua"', { fill: "#f08a5d", ls: 4 });
      text(ctx, cap(caption), L.W / 2, y - 6, '52px "Jua"', { fill: "#e2557a", stroke: [["#fff", 7]], max: 340 });
      text(ctx, date, L.W / 2, y + 48, '26px "Jua"', { fill: "#c98a2a", ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      balloon(ctx, c[0].x + c[0].w - 26, c[0].y + 30, 46, "#ff8fab", 0.15);
      star5(ctx, c[2].x + 18, c[2].y + 18, 22, "#ffd166", "#fff", 5, -0.2);
    },
  },
];
