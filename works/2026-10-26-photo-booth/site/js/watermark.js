// The web build's "made with" sticker (I-0054 watermark F: a speech bubble with the app sticker's white die-cut border).
// compose.paint draws it last, so the still, every moving-strip frame and the story's strip carry it; the Toss build
// never draws it (platform.watermark = false). Drawn once per side into a cached canvas, then drawImage'd per frame.
import { fontCss } from "./frames.js";

const W = 150, H = 56, TAIL = 12, PAD = 22;      // bubble size in strip px (600 wide); PAD = room for border, shadow, tilt
const LINE1 = "따로 같이 네컷", LINE2 = "토스에서 검색!";
// frames whose own footer art sits in the bottom-right corner (lime: right-aligned date at L.W - L.M - 24)
export const WM_SIDE = { lime: "left", scrap: "left" };   // scrap: its LOVE stamp sits bottom right

let fontP = null;
export const watermarkReady = () => fontP ||= fontCss()
  .then(() => Promise.all(['24px "Jua"', '16px "Jua"'].map(f => document.fonts.load(f, LINE1 + LINE2))))
  .catch(() => null);

const cache = {};
function art(side) {
  if (cache[side]) return cache[side];
  const left = side === "left", c = document.createElement("canvas");
  c.width = W + PAD * 2; c.height = H + TAIL + PAD * 2;
  const x = c.getContext("2d");
  x.translate(PAD + W / 2, PAD + H / 2); x.rotate((left ? -5 : 5) * Math.PI / 180); x.translate(-W / 2, -H / 2);
  const tx = left ? W - 44 : 24;                  // the tail points in from the strip's edge
  const bubble = () => { x.beginPath(); x.roundRect(0, 0, W, H, 18); x.moveTo(tx, H - 1); x.lineTo(tx + 6, H + TAIL); x.lineTo(tx + 20, H - 1); };
  x.lineJoin = "round";
  x.shadowColor = "rgba(0,0,0,.22)"; x.shadowBlur = 10; x.shadowOffsetY = 4;
  bubble(); x.lineWidth = 12; x.strokeStyle = "#fff"; x.stroke();      // die-cut border
  x.shadowColor = "transparent";
  bubble(); x.fillStyle = "#fff6c9"; x.fill(); x.lineWidth = 3; x.strokeStyle = "#0d0d0d"; x.stroke();
  x.textAlign = "center"; x.textBaseline = "middle";
  x.fillStyle = "#0d0d0d"; x.font = '24px "Jua"'; x.fillText(LINE1, W / 2, 21);
  x.fillStyle = "#3182f6"; x.font = '16px "Jua"'; x.fillText(LINE2, W / 2, 42);
  return (cache[side] = c);
}

// the bubble's box in strip coordinates: tucked into the very bottom corner, below the caption line and beside the
// centred date (zz-wm probe 10-31: 190 px wide in the footer covered captions on most frames)
const box = (L, side) => ({ x: side === "left" ? 10 : L.W - 10 - W, y: L.H - 8 - H - TAIL, w: W, h: H + TAIL });
const hits = (b, s) => Math.abs(s.x - (b.x + b.w / 2)) < s.s / 2 + b.w / 2 && Math.abs(s.y - (b.y + b.h / 2)) < s.s / 2 + b.h / 2;
// right by default; left for WM_SIDE frames or when a sticker covers the right corner (and not the left one)
export function watermarkSide(L, frameId, stickers = []) {
  if (WM_SIDE[frameId]) return WM_SIDE[frameId];
  const st = stickers || [];
  return st.some(s => hits(box(L, "right"), s)) && !st.some(s => hits(box(L, "left"), s)) ? "left" : "right";
}
export function drawWatermark(ctx, L, side) {
  const b = box(L, side);
  ctx.drawImage(art(side), b.x - PAD, b.y - PAD);
}
