// Stickers (Round 5): a drawn set (inline SVG, booth style: flat colours, white die-cut edge, soft shadow) and a
// curated emoji set (system emoji font). Each one is rasterised once to a square canvas of R px; compose.paint draws
// opts.stickers = [{ kind: "svg"|"emoji", id, x, y, s, r }] (strip px, x/y = centre, s = size, r = radians) on top.
// Words on the drawn ones are canvas text in Jua (an SVG used as an image cannot load web fonts).
import { tr } from "./i18n.js";
import { fontCss } from "./frames.js";

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
  { id: "chalkak", label: "찰칵", svg: bubble(BUTTER), text: [tr("찰칵!"), 50, 30, INK] },
  { id: "best", label: "최고", svg: bubble(SKY), text: [tr("최고"), 50, 32, INK] },
  { id: "friend", label: "우정", svg: bubble(LAV), text: [tr("우정"), 50, 32, INK] },
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
// Result-theme sets (2026-10-27): `set` = the theme whose sticker row lists it; the ones above (no set) are Y2K.
// 클린: lime + black, bold rules (concepts5 concept 1). 다이어리: washi tape, pen doodles, stamps (concept 4).
const K = "#151515", LM = "#d6ff3d", EDGE = 'stroke="#fff" stroke-width="12" stroke-linejoin="round" stroke-linecap="round"';
const line = (d, w = 4) => `fill="${d}" stroke="${K}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"`;
const both = (shape, fill, w) => `<g ${EDGE} fill="#fff">${shape}</g><g ${line(fill, w)}>${shape}</g>`;   // white edge, then the inked shape
const burst = Array.from({ length: 24 }, (_, k) => { const a = k * Math.PI / 12, r = k % 2 ? 31 : 44; return `${(50 + r * Math.cos(a)).toFixed(1)},${(50 + r * Math.sin(a)).toFixed(1)}`; }).join(" ");
const tapeD = (() => { let d = "M10 36"; for (let i = 0; i <= 6; i++) d += `L${90 + (i % 2) * 4} ${36 + i * 28 / 6}`; for (let i = 6; i >= 0; i--) d += `L${10 - (i % 2) * 4} ${36 + i * 28 / 6}`; return d + "Z"; })();
const tapeSvg = (fill, print, rot) => `<clipPath id="t"><path d="${tapeD}"/></clipPath><g transform="rotate(${rot} 50 50)" opacity=".88">`
  + `<path d="${tapeD}" fill="${fill}"/><g clip-path="url(#t)" fill="#fff" opacity=".6">${print}</g></g>`;
const INKD = "#3a2e2a";
const ink = (fill, w = 3.5) => `fill="${fill}" stroke="${INKD}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"`;
const inkBoth = (shape, fill, w) => `<g ${EDGE} fill="#fff">${shape}</g><g ${ink(fill, w)}>${shape}</g>`;
const THEMED = [
  { set: "clean", id: "c-burst", label: "번쩍", svg: both(`<polygon points="${burst}"/>`, LM, 3.5), text: ["NEW", 52, 22, K, -10, "Space Grotesk", 700] },
  { set: "clean", id: "c-check", label: "체크", svg: both(`<circle cx="50" cy="50" r="38"/>`, K, 3) + `<path d="M31 51l13 13 26-28" fill="none" stroke="${LM}" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>` },
  { set: "clean", id: "c-heart", label: "하트", svg: both(`<path d="${HEART}"/>`, LM, 4) },
  { set: "clean", id: "c-ast", label: "별표", svg: `<g stroke-linecap="round"><path d="M50 14v72M19 32l62 36M19 68l62-36" stroke="#fff" stroke-width="26"/><path d="M50 14v72M19 32l62 36M19 68l62-36" stroke="${K}" stroke-width="13"/></g>` },
  { set: "clean", id: "c-arrow", label: "화살표", svg: both(`<path d="M18 30h44V16l26 28-26 28V58H18z"/>`, K, 3) },
  { set: "clean", id: "c-smile", label: "스마일", svg: both(`<circle cx="50" cy="50" r="38"/>`, LM, 4)
    + `<g fill="${K}"><ellipse cx="38" cy="42" rx="4.5" ry="7"/><ellipse cx="62" cy="42" rx="4.5" ry="7"/></g><path d="M33 58q17 18 34 0" fill="none" stroke="${K}" stroke-width="5" stroke-linecap="round"/>` },
  { set: "clean", id: "c-wow", label: "WOW", svg: both(`<rect x="10" y="30" width="80" height="40" transform="rotate(-8 50 50)"/>`, K, 3), text: ["WOW", 51, 27, LM, -8, "Space Grotesk", 700] },
  { set: "clean", id: "c-best", label: "BEST", svg: both(`<rect x="8" y="32" width="84" height="36" rx="18"/>`, LM, 4), text: ["BEST", 51, 24, K, 0, "Space Grotesk", 700] },
  { set: "clean", id: "c-no1", label: "No.1", svg: both(`<circle cx="50" cy="50" r="38"/>`, "#fff", 4) + `<circle cx="50" cy="50" r="30" fill="none" stroke="${K}" stroke-width="2"/>`, text: ["No.1", 52, 25, K, -10, "Space Grotesk", 700] },
  { set: "clean", id: "c-hi", label: "HI", svg: both(`<path d="M14 18h72a8 8 0 0 1 8 8v36a8 8 0 0 1-8 8H48L30 86l3-16H14a8 8 0 0 1-8-8V26a8 8 0 0 1 8-8z"/>`, "#fff", 4), text: ["HI!", 45, 32, K, 0, "Space Grotesk", 700] },

  { set: "diary", id: "d-tape-pink", label: "마스킹 분홍", svg: tapeSvg("#f6b0c2", [...Array(7)].map((_, k) => `<circle cx="${16 + k * 12}" cy="${k % 2 ? 44 : 56}" r="3"/>`).join(""), -14) },
  { set: "diary", id: "d-tape-mint", label: "마스킹 민트", svg: tapeSvg("#9fd9c0", [...Array(9)].map((_, k) => `<path d="M${2 + k * 12} 66l14-32h5l-14 32z"/>`).join(""), 10) },
  { set: "diary", id: "d-tape-yellow", label: "마스킹 노랑", svg: tapeSvg("#ffd977", [...Array(8)].map((_, k) => `<rect x="${8 + k * 11}" y="30" width="5" height="40"/>`).join("") + `<rect x="0" y="45" width="100" height="5"/>`, -30) },
  { set: "diary", id: "d-heart", label: "낙서 하트", svg: inkBoth(`<path d="${HEART}"/>`, "#ffb6c6", 4) + `<path d="M28 40q4-8 10-6" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round"/>` },
  { set: "diary", id: "d-star", label: "낙서 별", svg: inkBoth(`<polygon points="${star5(50, 54, 40, 18)}"/>`, "#ffe27a", 4) },
  { set: "diary", id: "d-swirl", label: "뱅글 화살표", svg: `<g fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 78c18-4 28-20 20-30s-22 2-12 14 40 8 58-30M70 28l10 4 2-12" stroke="#fff" stroke-width="13"/>`
    + `<path d="M14 78c18-4 28-20 20-30s-22 2-12 14 40 8 58-30M70 28l10 4 2-12" stroke="${INKD}" stroke-width="4.5"/></g>` },
  { set: "diary", id: "d-stamp", label: "도장", svg: `<circle cx="50" cy="50" r="40" fill="#fffaf2" ${W}/><g fill="none" stroke="#d6455d" opacity=".9"><circle cx="50" cy="50" r="36" stroke-width="4"/><circle cx="50" cy="50" r="29" stroke-width="2" stroke-dasharray="3 3"/></g>`,
    text: ["LOVE", 52, 20, "#d6455d", -14, "Black Han Sans", 400] },
  { set: "diary", id: "d-today", label: "오늘", svg: `<rect x="12" y="28" width="76" height="44" rx="3" fill="#fffaf0" ${W} transform="rotate(-6 50 50)"/><rect x="14" y="30" width="72" height="40" fill="none" stroke="#e7b9a5" stroke-width="1.5" stroke-dasharray="4 3" transform="rotate(-6 50 50)"/>`
    + `<path d="M34 22l32 4-1 10-32-4z" fill="#a9dcc6" opacity=".85"/>`, text: [tr("오늘"), 53, 30, INKD, -6, "Gaegu", 700] },
  { set: "diary", id: "d-flower", label: "들꽃", svg: inkBoth([0, 60, 120, 180, 240, 300].map(a => `<ellipse cx="50" cy="28" rx="11" ry="18" transform="rotate(${a} 50 50)"/>`).join(""), "#fff", 3)
    + `<circle cx="50" cy="50" r="11" ${ink("#ffd36b", 3)}/>` },
  { set: "diary", id: "d-ribbon", label: "리본", svg: inkBoth(`<path d="M50 50 16 30c-7 6-7 34 0 40z"/><path d="M50 50 84 30c7 6 7 34 0 40z"/><path d="M46 54 34 88l9-3 5 8 4-34z"/><path d="M54 54l12 34-9-3-5 8-4-34z"/><circle cx="50" cy="50" r="8"/>`, "#ffc2d4", 3) },
  { set: "diary", id: "d-like", label: "좋아", svg: inkBoth(`<path d="M24 74a16 16 0 0 1-2-32 20 20 0 0 1 36-12 16 16 0 0 1 26 10 17 17 0 0 1-4 34z" transform="translate(50 52) scale(.86) translate(-54 -52)"/>`, "#fff", 3.5), text: [tr("좋아"), 55, 26, "#e0607e", -4, "Gaegu", 700] },
];
DRAWN.push(...THEMED);
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
      const [t, y, size, fill, rot = 0, family = "Jua", weight = 400] = d.text, px = size * R / 100, f = `${weight} ${px}px "${family}"`;
      if (family !== "Jua") await fontCss();         // the theme sets' fonts come from frames.js's stylesheet
      await document.fonts.load(f, t).catch(() => null);
      x.save(); x.translate(R / 2, y * R / 100); x.rotate(rot * Math.PI / 180);
      x.font = `${f}, system-ui, sans-serif`; x.textAlign = "center"; x.textBaseline = "middle";
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
