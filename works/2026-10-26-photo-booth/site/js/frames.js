// Strip frames, drawn procedurally on the 600 x 1800 canvas. Each frame:
//   draw(ctx, L, { date, cellRects })  background, cell borders, footer (cells are drawn after this)
//   over(ctx, L, { date, cellRects })  optional stickers on top of the cells (corners only, never faces)
//   rects                              optional cell rects (same 552:382 aspect as the default, so preview crop holds)
//   radius                             cell corner radius; color = chip swatch.
// Footer text is always 따로 같이 네컷 + the date (YYYY.MM.DD), styled per frame.

const FONT_CSS = "https://fonts.googleapis.com/css2?family=Black+Han+Sans&family=Jua&family=Gaegu:wght@700" +
  "&family=Hi+Melody&family=Do+Hyeon&family=IBM+Plex+Sans+KR:wght@300;500;700&family=IBM+Plex+Mono:wght@500&display=block";
const SAMPLE = "따로같이네컷우리둘 dearus·0123456789.";
const TITLE = "따로 같이 네컷";

let cssReady = null;
export async function loadFrameFonts(frame) {
  cssReady ||= new Promise(res => {
    const l = document.createElement("link");
    l.rel = "stylesheet"; l.href = FONT_CSS; l.onload = l.onerror = res;
    document.head.appendChild(l);
  });
  await cssReady;
  await Promise.all((frame?.fonts || []).map(f => document.fonts.load(f, SAMPLE).catch(() => null)));
}

// ---------- drawing helpers ----------
const rng = seed => () => ((seed = (seed * 1103515245 + 12345) >>> 0) / 4294967296);
function text(ctx, s, x, y, font, { fill, stroke, lw = 0, align = "center", ls = 0, shadow } = {}) {
  ctx.save();
  ctx.font = font; ctx.textAlign = align; ctx.textBaseline = "middle"; ctx.lineJoin = "round";
  if ("letterSpacing" in ctx) ctx.letterSpacing = ls + "px";
  if (shadow) { ctx.shadowColor = shadow[0]; ctx.shadowBlur = shadow[1]; ctx.shadowOffsetY = shadow[2] || 0; }
  for (const [c, w] of [].concat(stroke || [])) { ctx.strokeStyle = c; ctx.lineWidth = w; ctx.strokeText(s, x, y); }
  if (fill) { ctx.fillStyle = fill; ctx.fillText(s, x, y); }
  ctx.restore();
}
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
    const r = i % 2 ? R * 0.48 : R, a = -Math.PI / 2 + i * Math.PI / 5;
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

// film strip: narrower cells between sprocket rails, same aspect as the default cell
const FILM_W = 500, FILM_H = Math.round(500 * 382 / 552), FILM_GAP = 24;
const FILM_RECTS = [0, 1, 2, 3].map(i => ({ x: 50, y: 36 + i * (FILM_H + FILM_GAP), w: FILM_W, h: FILM_H }));

export const FRAMES = [
  {
    id: "black", label: "블랙", color: "#141216", radius: 2,
    fonts: ['46px "Black Han Sans"', '500 20px "IBM Plex Mono"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#141216"; ctx.fillRect(0, 0, L.W, L.H);
      const y = footY(L, cellRects);
      text(ctx, TITLE, L.W / 2, y - 16, '46px "Black Han Sans"', { fill: "#f3f0f5", ls: 1 });
      text(ctx, date, L.W / 2, y + 34, '500 20px "IBM Plex Mono"', { fill: "#f3f0f5b0", ls: 4 });
    },
  },
  {
    id: "white", label: "화이트", color: "#fdfdfd", radius: 2,
    fonts: ['46px "Black Han Sans"', '500 20px "IBM Plex Mono"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#fdfdfd"; ctx.fillRect(0, 0, L.W, L.H);
      borders(ctx, cellRects, 1.5, 2, "#e4e0e3");
      const y = footY(L, cellRects);
      text(ctx, TITLE, L.W / 2, y - 16, '46px "Black Han Sans"', { fill: "#2a2530", ls: 1 });
      ctx.font = '500 20px "IBM Plex Mono"';
      text(ctx, date, L.W / 2 - 12, y + 34, '500 20px "IBM Plex Mono"', { fill: "#2a2530a8", ls: 4 });
      heart(ctx, L.W / 2 + ctx.measureText(date).width / 2 + 22, y + 34, 16, "#f0789a");
    },
  },
  {
    id: "pink", label: "핑크", color: "#ffc6d6", radius: 10,
    fonts: ['54px "Jua"', '24px "Jua"'],
    draw(ctx, L, { date, cellRects }) {
      vgrad(ctx, L.W, L.H, ["#ffe0ea", "#ffc9da", "#ffb9cf"]);
      const r = rng(7);
      for (let i = 0; i < 70; i++) sparkle(ctx, r() * L.W, r() * L.H, 3 + r() * 5, "#ffffffb0", r());
      borders(ctx, cellRects, 7, 10, "#fff", "#e0789a40");
      const y = footY(L, cellRects);
      text(ctx, TITLE, L.W / 2, y - 14, '54px "Jua"', { fill: "#fff", stroke: [["#ee6f97", 7]] });
      text(ctx, date, L.W / 2, y + 40, '24px "Jua"', { fill: "#e0567f", ls: 2 });
      heart(ctx, 88, y - 18, 30, "#ff8fb1", "#fff", 5, -0.25);
      heart(ctx, L.W - 88, y - 18, 30, "#ff8fb1", "#fff", 5, 0.25);
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
    id: "sky", label: "하늘", color: "#cfe6ff", radius: 12,
    fonts: ['48px "Jua"', '24px "Jua"'],
    draw(ctx, L, { date, cellRects }) {
      vgrad(ctx, L.W, L.H, ["#b9dcff", "#d6ebff", "#eef7ff"]);
      const r = rng(3);
      for (let i = 0; i < 50; i++) { ctx.fillStyle = "#ffffffa0"; ctx.beginPath(); ctx.arc(r() * L.W, r() * L.H, 1.5 + r() * 2.5, 0, 7); ctx.fill(); }
      borders(ctx, cellRects, 6, 12, "#fff", "#4a8fd630");
      const y = footY(L, cellRects);
      cloud(ctx, 70, L.H - 4, 110); cloud(ctx, L.W - 60, L.H + 6, 130); cloud(ctx, L.W / 2 + 40, L.H + 20, 90, "#ffffffd0");
      text(ctx, TITLE, L.W / 2, y - 22, '48px "Jua"', { fill: "#2f78d0", stroke: [["#fff", 8]] });
      text(ctx, date, L.W / 2, y + 28, '24px "Jua"', { fill: "#5b9be0", ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      cloud(ctx, c[0].x + c[0].w - 30, c[0].y + 26, 92);
      cloud(ctx, c[2].x + 34, c[2].y + c[2].h + 14, 80);
    },
  },
  {
    id: "check", label: "체크", color: "#ffb3c8", radius: 4,
    fonts: ['700 46px "Gaegu"', '700 26px "Gaegu"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#fffafb"; ctx.fillRect(0, 0, L.W, L.H);
      ctx.fillStyle = "#ff8fb04a";
      for (let x = -10; x < L.W; x += 56) ctx.fillRect(x, 0, 28, L.H);
      for (let y = -10; y < L.H; y += 56) ctx.fillRect(0, y, L.W, 28);
      borders(ctx, cellRects, 8, 4, "#fff", "#c0406a30");
      const y = footY(L, cellRects);
      ctx.save(); ctx.fillStyle = "#fff"; ctx.shadowColor = "#c0406a30"; ctx.shadowBlur = 10; ctx.shadowOffsetY = 3;
      ctx.beginPath(); ctx.roundRect(110, y - 60, L.W - 220, 120, 18); ctx.fill(); ctx.restore();
      ctx.save(); ctx.setLineDash([9, 7]); ctx.strokeStyle = "#f28fae"; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.roundRect(119, y - 51, L.W - 238, 102, 12); ctx.stroke(); ctx.restore();
      text(ctx, TITLE, L.W / 2, y - 12, '700 46px "Gaegu"', { fill: "#d6406e" });
      text(ctx, date, L.W / 2, y + 28, '700 26px "Gaegu"', { fill: "#e57a9a", ls: 1 });
    },
    over(ctx, L, { cellRects: c }) {
      bow(ctx, L.W / 2, c[0].y - 2, 86, "#ff8fb0", "#d6406e");
    },
  },
  {
    id: "film", label: "필름", color: "#1b1b1b", radius: 2, rects: FILM_RECTS,
    fonts: ['40px "Do Hyeon"', '500 26px "IBM Plex Mono"', '500 13px "IBM Plex Mono"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#1b1a19"; ctx.fillRect(0, 0, L.W, L.H);
      ctx.fillStyle = "#f4efe6";
      for (let y = 12; y < L.H - 10; y += 40) for (const x of [16, L.W - 32]) { ctx.beginPath(); ctx.roundRect(x, y, 16, 22, 4); ctx.fill(); }
      cellRects.forEach((c, i) => {
        text(ctx, `▸ ${i + 1}A`, c.x + 2, c.y + c.h + FILM_GAP / 2, '500 13px "IBM Plex Mono"', { fill: "#ffb347", align: "left", ls: 1 });
        text(ctx, "TG 400", c.x + c.w - 2, c.y + c.h + FILM_GAP / 2, '500 13px "IBM Plex Mono"', { fill: "#ffb347b0", align: "right", ls: 2 });
      });
      const y = footY(L, cellRects);
      text(ctx, TITLE, L.W / 2, y - 22, '40px "Do Hyeon"', { fill: "#f4efe6", ls: 2 });
      text(ctx, date, L.W / 2, y + 30, '500 26px "IBM Plex Mono"', { fill: "#ff8a3d", ls: 5, shadow: ["#ff6a1a", 10] });
    },
  },
  {
    id: "duo", label: "우리 둘", color: "#ffd4c7", radius: 8,
    fonts: ['58px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#ffd6c9"; ctx.fillRect(0, 0, L.W / 2, L.H);
      ctx.fillStyle = "#cfe5ff"; ctx.fillRect(L.W / 2, 0, L.W / 2, L.H);
      ctx.save(); ctx.strokeStyle = "#fff"; ctx.lineWidth = 3; ctx.setLineDash([10, 9]);
      ctx.beginPath(); ctx.moveTo(L.W / 2, 0); ctx.lineTo(L.W / 2, L.H); ctx.stroke(); ctx.restore();
      borders(ctx, cellRects, 5, 8, "#fff");
      const y = footY(L, cellRects);
      text(ctx, "따로", L.W / 4, y - 18, '58px "Jua"', { fill: "#e8664f", stroke: [["#fff", 9]] });
      text(ctx, "같이", L.W * 3 / 4, y - 18, '58px "Jua"', { fill: "#3f7fd6", stroke: [["#fff", 9]] });
      heart(ctx, L.W / 2, y - 18, 36, "#ff7a8e", "#fff", 6);
      text(ctx, `네컷 · ${date}`, L.W / 2, y + 42, '26px "Jua"', { fill: "#5b5560", ls: 1 });
    },
    over(ctx, L, { cellRects: c }) {
      for (let i = 0; i < 3; i++) heart(ctx, L.W / 2, c[i].y + c[i].h + 8, 26, "#ff7a8e", "#fff", 5);
    },
  },
  {
    id: "coquette", label: "리본", color: "#ffe3ea", radius: 14,
    fonts: ['64px "Hi Melody"', '34px "Hi Melody"', '30px "Hi Melody"'],
    draw(ctx, L, { date, cellRects }) {
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
      text(ctx, "dear us", L.W / 2, y - 52, '34px "Hi Melody"', { fill: "#d9708f" });
      bow(ctx, L.W / 2 - 92, y - 54, 40, "#f7a6ba", "#c95c7c"); bow(ctx, L.W / 2 + 92, y - 54, 40, "#f7a6ba", "#c95c7c");
      text(ctx, TITLE, L.W / 2, y + 4, '64px "Hi Melody"', { fill: "#b23a60" });
      text(ctx, date, L.W / 2, y + 56, '30px "Hi Melody"', { fill: "#c25d7d", ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      bow(ctx, L.W / 2, c[0].y - 4, 96, "#f7a6ba", "#c95c7c");
      bow(ctx, c[2].x + 6, c[2].y - 6, 54, "#f7a6ba", "#c95c7c");
      bow(ctx, c[3].x + c[3].w - 6, c[3].y - 6, 54, "#f7a6ba", "#c95c7c");
    },
  },
  {
    id: "purikura", label: "프리쿠라", color: "#ff9ad5", radius: 18,
    fonts: ['60px "Jua"', '26px "Jua"'],
    draw(ctx, L, { date, cellRects }) {
      const g = ctx.createLinearGradient(0, 0, L.W, L.H);
      g.addColorStop(0, "#ff8fd0"); g.addColorStop(0.5, "#c39bff"); g.addColorStop(1, "#8ff0dc");
      ctx.fillStyle = g; ctx.fillRect(0, 0, L.W, L.H);
      const r = rng(11);
      for (let i = 0; i < 90; i++) sparkle(ctx, r() * L.W, r() * L.H, 2 + r() * 6, "#ffffffc0", r());
      borders(ctx, cellRects, 8, 18, "#fff", "#7a3cff40");
      const y = footY(L, cellRects);
      text(ctx, TITLE, L.W / 2, y - 14, '60px "Jua"', { fill: "#ff4fa3", stroke: [["#7a3cff", 16], ["#fff", 8]] });
      text(ctx, date, L.W / 2, y + 46, '26px "Jua"', { fill: "#fff", stroke: [["#7a3cff", 6]], ls: 3 });
    },
    over(ctx, L, { cellRects: c }) {
      star5(ctx, c[0].x + c[0].w - 14, c[0].y + 14, 34, "#ffe14d", "#fff", 7, 0.2);
      heart(ctx, c[1].x + 14, c[1].y + c[1].h - 14, 56, "#ff4f9a", "#fff", 7, -0.2);
      sparkle(ctx, c[1].x + 56, c[1].y + c[1].h - 50, 14, "#fff");
      star5(ctx, c[3].x + c[3].w - 16, c[3].y + c[3].h - 14, 26, "#7ef2d6", "#fff", 6, -0.2);
      heart(ctx, c[2].x + c[2].w - 10, c[2].y + 14, 30, "#c39bff", "#fff", 6, 0.3);
    },
  },
  {
    id: "strawberry", label: "딸기", color: "#ffd0d6", radius: 12,
    fonts: ['52px "Jua"', '24px "Jua"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#ffeef0"; ctx.fillRect(0, 0, L.W, L.H);
      for (let y = 10, row = 0; y < L.H + 40; y += 62, row++) for (let x = row % 2 ? 12 : 43; x < L.W + 20; x += 62) {
        (row + Math.round(x / 62)) % 2 ? cherry(ctx, x, y, 22, 0.15) : strawberry(ctx, x, y, 21, -0.2);
      }
      borders(ctx, cellRects, 6, 12, "#fff", "#e2384f30");
      const y = footY(L, cellRects);
      ctx.save(); ctx.fillStyle = "#fffffff0"; ctx.beginPath(); ctx.roundRect(70, y - 62, L.W - 140, 124, 62); ctx.fill(); ctx.restore();
      text(ctx, TITLE, L.W / 2, y - 12, '52px "Jua"', { fill: "#e2384f" });
      text(ctx, date, L.W / 2, y + 34, '24px "Jua"', { fill: "#6fa046", ls: 2 });
    },
    over(ctx, L, { cellRects: c }) {
      strawberry(ctx, c[0].x + 10, c[0].y + 12, 58, -0.3);
      cherry(ctx, c[1].x + c[1].w - 12, c[1].y + c[1].h - 14, 58, 0.2);
      strawberry(ctx, c[3].x + c[3].w - 10, c[3].y + c[3].h - 16, 52, 0.3);
    },
  },
  {
    id: "chrome", label: "크롬", color: "#c9cfd8", radius: 6,
    fonts: ['58px "Black Han Sans"', '500 20px "IBM Plex Mono"'],
    draw(ctx, L, { date, cellRects }) {
      const g = ctx.createLinearGradient(0, 0, L.W, L.H * 0.35);
      ["#f4f6f9", "#b8c0cc", "#eef1f5", "#9aa4b2", "#e6e9ee", "#c3cad4"].forEach((c, i, a) => g.addColorStop(i / (a.length - 1), c));
      ctx.fillStyle = g; ctx.fillRect(0, 0, L.W, L.H);
      borders(ctx, cellRects, 3, 6, "#1d2230");
      const y = footY(L, cellRects);
      const t = ctx.createLinearGradient(0, y - 42, 0, y + 14);
      t.addColorStop(0, "#ffffff"); t.addColorStop(0.48, "#dfe5ee"); t.addColorStop(0.52, "#8d97a8"); t.addColorStop(1, "#f4f6fa");
      text(ctx, TITLE, L.W / 2, y - 14, '58px "Black Han Sans"', { fill: t, stroke: [["#1d2230", 4]], ls: 3, shadow: ["#1d223066", 6, 3] });
      text(ctx, date, L.W / 2, y + 42, '500 20px "IBM Plex Mono"', { fill: "#1d2230", ls: 5 });
      for (const [x, yy, s] of [[60, y - 40, 22], [L.W - 64, y + 30, 16], [L.W - 110, y - 58, 10]]) {
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
    id: "minimal", label: "미니멀", color: "#f5f0e6", radius: 0,
    fonts: ['300 76px "IBM Plex Sans KR"', '500 18px "IBM Plex Sans KR"'],
    draw(ctx, L, { date, cellRects }) {
      ctx.fillStyle = "#f5f0e6"; ctx.fillRect(0, 0, L.W, L.H);
      const y = footY(L, cellRects);
      text(ctx, date.slice(5), L.M, y - 14, '300 76px "IBM Plex Sans KR"', { fill: "#2b2620", align: "left", ls: -1 });
      text(ctx, `${TITLE} · ${date}`, L.M + 2, y + 50, '500 18px "IBM Plex Sans KR"', { fill: "#2b2620a0", align: "left", ls: 2 });
      ctx.fillStyle = "#2b2620"; ctx.fillRect(L.W - L.M - 46, y - 14, 46, 3);
    },
  },
];
