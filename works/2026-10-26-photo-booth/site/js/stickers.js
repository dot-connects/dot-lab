// Stickers (Round 5): a drawn set (inline SVG, booth style: flat colours, white die-cut edge, soft shadow) and a
// curated emoji set (system emoji font). Each one is rasterised once to a square canvas of R px; compose.paint draws
// opts.stickers = [{ kind: "svg"|"emoji", id, x, y, s, r }] (strip px, x/y = centre, s = size, r = radians) on top.
// Words on the drawn ones are canvas text in Jua (an SVG used as an image cannot load web fonts).

const R = 320;
const PINK = "#ff7eb6", DEEP = "#c22d6c", BUTTER = "#ffd166", SKY = "#7fd1ff", LAV = "#c7a6ff", MINT = "#7fe3c4", INK = "#2e2230";
const W = 'stroke="#fff" stroke-width="7" stroke-linejoin="round" paint-order="stroke"';   // die-cut edge
const face = (x, y, k = 1) => `<g fill="${INK}"><circle cx="${x - 9 * k}" cy="${y}" r="${2.6 * k}"/><circle cx="${x + 9 * k}" cy="${y}" r="${2.6 * k}"/></g>`
  + `<path d="M${x - 4 * k} ${y + 5 * k}q${4 * k} ${4 * k} ${8 * k} 0" fill="none" stroke="${INK}" stroke-width="${2.2 * k}" stroke-linecap="round"/>`
  + `<g fill="#ff8fab" opacity=".7"><ellipse cx="${x - 15 * k}" cy="${y + 5 * k}" rx="${4 * k}" ry="${2.6 * k}"/><ellipse cx="${x + 15 * k}" cy="${y + 5 * k}" rx="${4 * k}" ry="${2.6 * k}"/></g>`;
const HEART = "M50 86S12 62 12 36a19 19 0 0 1 38-6 19 19 0 0 1 38 6c0 26-38 50-38 50z";
const bubble = fill => `<path d="M14 22h72a10 10 0 0 1 10 10v30a10 10 0 0 1-10 10H46l-14 14 2-14H14A10 10 0 0 1 4 62V32a10 10 0 0 1 10-10z" fill="${fill}" ${W}/>`;
const star5 = (cx, cy, ro, ri) => Array.from({ length: 10 }, (_, k) => {
  const a = -Math.PI / 2 + k * Math.PI / 5, r = k % 2 ? ri : ro;
  return `${(cx + r * Math.cos(a)).toFixed(1)},${(cy + r * Math.sin(a)).toFixed(1)}`;
}).join(" ");
const spark = (x, y, r, fill) => `<path d="M${x} ${y - r}c${r * .12} ${r * .6} ${r * .4} ${r * .88} ${r} ${r}c-${r * .6} ${r * .12}-${r * .88} ${r * .4}-${r} ${r}c-${r * .12}-${r * .6}-${r * .4}-${r * .88}-${r}-${r}c${r * .6}-${r * .12} ${r * .88}-${r * .4} ${r}-${r}z" fill="${fill}" ${W}/>`;

// text: [word, y (0..100), size, fill]
export const DRAWN = [
  { id: "heart", label: "하트", svg: `<path d="${HEART}" fill="${PINK}" ${W}/><ellipse cx="31" cy="34" rx="6" ry="4" fill="#fff" opacity=".7" transform="rotate(-35 31 34)"/>` },
  { id: "star", label: "별", svg: `<polygon points="${star5(50, 53, 42, 20)}" fill="${BUTTER}" ${W}/>${face(50, 56, .9)}` },
  { id: "sparkle", label: "반짝", svg: `${spark(42, 54, 34, LAV)}${spark(78, 22, 16, SKY)}${spark(80, 78, 10, BUTTER)}` },
  { id: "ribbon", label: "리본", svg: `<g ${W}><path d="M50 50 14 26c-8 6-8 42 0 48z" fill="${PINK}"/><path d="M50 50 86 26c8 6 8 42 0 48z" fill="${PINK}"/>
    <path d="M44 56 30 90l12-4 6 10 6-38z" fill="#ff9fc6"/><path d="M56 56l14 34-12-4-6 10-6-38z" fill="#ff9fc6"/></g><rect x="40" y="38" width="20" height="24" rx="8" fill="${DEEP}" ${W}/>` },
  { id: "crown", label: "왕관", svg: `<path d="M12 74 8 30l22 18 20-30 20 30 22-18-4 44z" fill="${BUTTER}" ${W}/><rect x="12" y="70" width="76" height="14" rx="5" fill="#ffb84d" ${W}/>
    <circle cx="50" cy="56" r="6" fill="${PINK}"/><circle cx="28" cy="60" r="4" fill="${SKY}"/><circle cx="72" cy="60" r="4" fill="${SKY}"/>` },
  { id: "flower", label: "꽃", svg: `<g fill="#ffc2d6" ${W}>${[0, 72, 144, 216, 288].map(a => `<ellipse cx="50" cy="27" rx="17" ry="22" transform="rotate(${a} 50 50)"/>`).join("")}</g>
    <g fill="#ffc2d6">${[0, 72, 144, 216, 288].map(a => `<ellipse cx="50" cy="27" rx="17" ry="22" transform="rotate(${a} 50 50)"/>`).join("")}</g><circle cx="50" cy="50" r="13" fill="${BUTTER}"/>` },
  { id: "chalkak", label: "찰칵", svg: bubble(BUTTER), text: ["찰칵!", 50, 30, INK] },
  { id: "best", label: "최고", svg: bubble(SKY), text: ["최고", 50, 32, INK] },
  { id: "friend", label: "우정", svg: bubble(LAV), text: ["우정", 50, 32, INK] },
  { id: "love-bubble", label: "♥ 말풍선", svg: `${bubble("#fff")}<path d="M50 63s-13-8-13-17a6.5 6.5 0 0 1 13-2 6.5 6.5 0 0 1 13 2c0 9-13 17-13 17z" fill="${PINK}"/>`, edge: true },
  { id: "arrow", label: "화살표", svg: `<path d="M14 80C24 40 50 24 80 26" fill="none" stroke="#fff" stroke-width="15" stroke-linecap="round"/><path d="M66 12 84 26 68 42" fill="none" stroke="#fff" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M14 80C24 40 50 24 80 26" fill="none" stroke="${DEEP}" stroke-width="6" stroke-linecap="round" stroke-dasharray="1 0"/><path d="M66 12 84 26 68 42" fill="none" stroke="${DEEP}" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>` },
  { id: "cloud", label: "구름", svg: `<path d="M24 74a16 16 0 0 1-2-32 20 20 0 0 1 36-12 16 16 0 0 1 26 10 17 17 0 0 1-4 34z" fill="#d9eeff" ${W} transform="translate(50 52) scale(.86) translate(-54 -52)"/>${face(50, 54, .9)}` },
  { id: "strawberry", label: "딸기", svg: `<path d="M50 92C22 80 12 54 18 40c8-12 56-12 64 0 6 14-4 40-32 52z" fill="#ff5d73" ${W}/>
    <path d="M30 34l8-14 12 8 12-8 8 14-20 4z" fill="#5cc98a" ${W}/><g fill="#ffe08a">${[[34, 50], [50, 48], [66, 50], [42, 64], [58, 64], [50, 78]].map(([x, y]) => `<ellipse cx="${x}" cy="${y}" rx="2.2" ry="3.2"/>`).join("")}</g>` },
  { id: "smile", label: "스마일", svg: `<circle cx="50" cy="50" r="40" fill="${BUTTER}" ${W}/>${face(50, 46, 1.4)}` },
  { id: "clover", label: "클로버", svg: `<g fill="${MINT}" ${W}>${[0, 90, 180, 270].map(a => `<path d="M50 50C38 40 30 26 38 18a9 9 0 0 1 12 2 9 9 0 0 1 12-2c8 8 0 22-12 32z" transform="rotate(${a} 50 50)"/>`).join("")}</g>
    <g fill="${MINT}">${[0, 90, 180, 270].map(a => `<path d="M50 50C38 40 30 26 38 18a9 9 0 0 1 12 2 9 9 0 0 1 12-2c8 8 0 22-12 32z" transform="rotate(${a} 50 50)"/>`).join("")}</g>` },
  { id: "love", label: "LOVE", svg: `<rect x="8" y="30" width="84" height="40" rx="20" fill="${PINK}" ${W} transform="rotate(-8 50 50)"/>`, text: ["LOVE", 52, 28, "#fff", -8] },
  { id: "bff", label: "BFF", svg: `<circle cx="50" cy="50" r="38" fill="${LAV}" ${W}/><circle cx="50" cy="50" r="30" fill="none" stroke="#fff" stroke-width="2.5" stroke-dasharray="4 5"/>`, text: ["BFF", 52, 30, INK] },
  { id: "hearts", label: "하트 둘", svg: `<path d="${HEART}" fill="${PINK}" ${W} transform="translate(4 18) scale(.62) rotate(-14 50 50)"/><path d="${HEART}" fill="#ffb3cf" ${W} transform="translate(40 2) scale(.52) rotate(16 50 50)"/>` },
  { id: "cat-ears", label: "고양이 귀", svg: `<g ${W}><path d="M6 78 14 22l30 36z" fill="${INK}"/><path d="M94 78 86 22 56 58z" fill="${INK}"/></g><path d="M15 64l4-26 14 16z" fill="#ff9fc6"/><path d="M85 64l-4-26-14 16z" fill="#ff9fc6"/>` },
  { id: "blush", label: "볼터치", svg: `<g fill="#ff8fab" opacity=".75"><ellipse cx="22" cy="50" rx="16" ry="10"/><ellipse cx="78" cy="50" rx="16" ry="10"/></g>
    <g stroke="#fff" stroke-width="2.4" stroke-linecap="round" opacity=".9"><path d="M16 46l-3 7M23 46l-3 7M30 46l-3 7M72 46l-3 7M79 46l-3 7M86 46l-3 7"/></g>` },
];
export const EMOJI = ["😀", "😆", "🥰", "😍", "😘", "😎", "🤪", "😜", "🥹", "😭", "🤭", "🙈", "❤️", "💖", "💕", "💘", "✨", "⭐", "🌈", "☀️",
  "🌸", "🌷", "🍀", "🍓", "🍒", "🍰", "🎂", "🎀", "🎁", "🎉", "🎈", "👑", "💎", "💋", "✌️", "🫶", "👍", "🐶", "🐱", "🐰"];

const SHADOW = '<filter id="d" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="2.5" stdDeviation="1.6" flood-color="#6b2d45" flood-opacity=".3"/></filter>';
const svgUrl = d => "data:image/svg+xml;charset=utf-8," + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="${R}" height="${R}"><defs>${SHADOW}</defs><g filter="url(#d)">${d.svg}</g></svg>`);
const loadImg = src => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });

const cache = new Map();   // "kind:id" -> Promise<canvas>; canvas.ready set once drawn (paint is sync)
async function raster(kind, id) {
  const c = Object.assign(document.createElement("canvas"), { width: R, height: R }), x = c.getContext("2d");
  if (kind === "emoji") {
    x.font = `${R * 0.78}px "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif`;
    x.textAlign = "center"; x.textBaseline = "alphabetic";
    const m = x.measureText(id), h = (m.actualBoundingBoxAscent || R * 0.6) + (m.actualBoundingBoxDescent || 0);
    x.fillText(id, R / 2, R / 2 + h / 2 - (m.actualBoundingBoxDescent || 0));
  } else {
    const d = DRAWN.find(s => s.id === id);
    if (!d) throw new Error("sticker " + id);
    x.drawImage(await loadImg(svgUrl(d)), 0, 0, R, R);
    if (d.text) {
      const [t, y, size, fill, rot = 0] = d.text, px = size * R / 100;
      await document.fonts.load(`${px}px "Jua"`, t).catch(() => null);
      x.save(); x.translate(R / 2, y * R / 100); x.rotate(rot * Math.PI / 180);
      x.font = `${px}px "Jua", system-ui, sans-serif`; x.textAlign = "center"; x.textBaseline = "middle";
      x.fillStyle = fill; x.fillText(t, 0, 0); x.restore();
    }
  }
  c.ready = true;
  return c;
}
export function stickerCanvas(kind, id) {
  const k = kind + ":" + id;
  if (!cache.has(k)) cache.set(k, raster(kind, id).catch(e => { cache.delete(k); throw e; }));
  return cache.get(k);
}
const done = new Map();    // "kind:id" -> canvas, filled as rasters land
export async function stickersReady(list) {
  await Promise.all((list || []).map(s => stickerCanvas(s.kind, s.id).then(c => done.set(s.kind + ":" + s.id, c)).catch(() => null)));
}
// a picker / overlay image of the same raster (blob URL, made once)
const urls = new Map();
export function stickerUrl(kind, id) {
  const k = kind + ":" + id;
  if (!urls.has(k)) urls.set(k, stickerCanvas(kind, id).then(c => new Promise(r => c.toBlob(b => r(URL.createObjectURL(b)), "image/png"))));
  return urls.get(k);
}
// draw on a strip-sized context (600x1800 coordinates); stickers not yet rasterised are skipped (call stickersReady first)
export function drawStickers(ctx, list) {
  for (const s of list || []) {
    const c = done.get(s.kind + ":" + s.id);
    if (!c) continue;
    ctx.save(); ctx.translate(s.x, s.y); ctx.rotate(s.r || 0);
    ctx.drawImage(c, -s.s / 2, -s.s / 2, s.s, s.s);
    ctx.restore();
  }
}
