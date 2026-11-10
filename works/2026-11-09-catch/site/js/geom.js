// Screen geometry of a throw. Pure, tested in geom.test.mjs. Screen points are CSS px, y down.

/** video pixel -> screen point, the same mapping as CSS object-fit: cover (centred, cropped). */
export function coverMap(vw, vh, W, H) {
  const k = Math.max(W / vw, H / vh), ox = (W - vw * k) / 2, oy = (H - vh * k) / 2;
  return ([x, y]) => [ox + x * k, oy + y * k];
}

export const FLIGHT_MS = 600, END_SCALE = 0.6, HIT_FROM = 0.6;
const LAST_MS = 80, MIN_UP = 0.5, MIN_MOVE = 60, ARC = 0.12, STEPS = 50;

/**
 * Swipe speed (px/ms) -> how far the ball goes, as a fraction of the distance to the target: 0.15 + 0.38 v, 0.4..2.2.
 * A natural flick (~2.2 px/ms) reaches the centre, 1.2 px/ms 60 % of the way, over ~4.4 px/ms it flies over even with the assist.
 */
export const reach = v => Math.min(2.2, Math.max(0.4, 0.15 + 0.38 * v));

/**
 * Finger path ({x, y, t} from the press on the ball to the release) -> throw, or null when it is not one
 * (moved < 60 px, or under 0.5 px/ms upward over the last 80 ms; the ball then goes back to the hand).
 * Direction = the last 80 ms; distance = reach(speed) x the distance from the hand (rest.y, the release's x) to the
 * target box centre, so a flick works at any speed in a wide middle band. raw = where the swipe sends it, to = after the
 * aim assist (the ball flies there). No box: 40 % of the screen height up, no assist.
 */
export function release(path, W, H, rest, box = null) {
  if (!path || path.length < 2) return null;
  const a = path[0], b = path[path.length - 1];
  if (Math.hypot(b.x - a.x, b.y - a.y) < MIN_MOVE) return null;
  let i = path.length - 1;
  while (i > 0 && b.t - path[i - 1].t <= LAST_MS) i--;
  if (i === path.length - 1) i--;
  const s = path[i], dt = Math.max(1, b.t - s.t), vx = (b.x - s.x) / dt, vy = (b.y - s.y) / dt;
  if (-vy < MIN_UP) return null;
  const v = Math.hypot(vx, vy), o = { x: b.x, y: rest.y };
  const D = box ? Math.hypot(box.x + box.w / 2 - o.x, box.y + box.h / 2 - o.y) : 0.4 * H, L = D * reach(v);
  const raw = { x: o.x + vx / v * L, y: o.y + vy / v * L };
  return { from: { x: b.x, y: b.y }, raw, to: box ? assist(raw, box) : raw, curve: curveOf(path), v };
}

/**
 * Soft aim assist: a landing off the box but within one box height of its edge is pulled half way to the box centre.
 * A landing on the box is left alone (the ring stays skill), one further off stays a miss.
 */
export function assist(p, box) {
  const dx = Math.max(box.x - p.x, 0, p.x - box.x - box.w), dy = Math.max(box.y - p.y, 0, p.y - box.y - box.h);
  if ((dx === 0 && dy === 0) || Math.hypot(dx, dy) > box.h) return p;
  return { x: p.x + (box.x + box.w / 2 - p.x) / 2, y: p.y + (box.y + box.h / 2 - p.y) / 2 };
}

/** Signed turn of the finger path in degrees (sum of the turns between segments), + = clockwise on screen. */
export function turnOf(path) {
  let sum = 0, prev = null, last = path[0];
  for (let k = 1; k < path.length; k++) {
    const q = path[k], dx = q.x - last.x, dy = q.y - last.y;
    if (Math.hypot(dx, dy) < 2) continue;   // finger jitter has no direction
    const ang = Math.atan2(dy, dx);
    if (prev !== null) { let d = ang - prev; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; sum += d; }
    prev = ang; last = q;
  }
  return sum * 180 / Math.PI;
}
/** |turn| >= 300 degrees = a curve ball (armed), +1 clockwise. */
export function curveOf(path) {
  const deg = turnOf(path);
  return deg >= 300 ? 1 : deg <= -300 ? -1 : 0;
}

/**
 * Ball at flight time u (0..1): straight from release to `to`, lifted on an arc whose top is
 * 12 % of the screen height, shrinking 1 -> 0.6 (it goes away from you; 0.6 of the 58 px ball is a 7 cm ball at the
 * creature's 1.2 m). A curve ball bows out sideways by up to 12 % of the width and comes back: it lands where it was aimed.
 */
export function flight(th, u, W, H) {
  const to = th.to;
  return {
    x: th.from.x + (to.x - th.from.x) * u + (th.curve || 0) * 0.12 * W * Math.sin(Math.PI * u),
    y: th.from.y + (to.y - th.from.y) * u - ARC * H * 4 * u * (1 - u),
    s: 1 - (1 - END_SCALE) * u,
  };
}

const inBox = (p, b) => p.x >= b.x && p.x <= b.x + b.w && p.y >= b.y && p.y <= b.y + b.h;
/**
 * Test the flight between u0 and u1 against the target (only u >= 0.6 counts: the ball reaches the creature's depth late
 * in the flight; 50 samples per flight, so a slow frame cannot jump over the box). First sample on the box = the hit:
 * {u, at, onBody, inRing}, inRing = where the swipe itself sends the ball (no assist) is inside the ring. null = not yet.
 */
export function sweep(th, u0, u1, box, ring, W, H) {
  if (!box) return null;
  for (let k = Math.max(Math.ceil(u0 * STEPS), HIT_FROM * STEPS); k <= Math.floor(u1 * STEPS + 1e-9); k++) {
    const at = flight(th, k / STEPS, W, H);
    if (!inBox(at, box)) continue;
    return { u: k / STEPS, at, onBody: true, inRing: Math.hypot(th.raw.x - ring.x, th.raw.y - ring.y) <= ring.r };
  }
  return null;
}

/**
 * One find result into a tracked real target: the 0..1 box smoothed by EMA (a = 0.5) against the last one, and a miss
 * keeps the last box for 2 misses (DeepLab drops a close dog about 1 run in 3). found/prev = {x, y, w, h, kind} | null.
 */
export function track(prev, found, a = 0.5, hold = 2) {
  if (!found) return prev && prev.miss < hold ? { ...prev, miss: prev.miss + 1 } : null;
  if (!prev) return { x: found.x, y: found.y, w: found.w, h: found.h, kind: found.kind, miss: 0 };
  const m = k => prev[k] + (found[k] - prev[k]) * a;
  return { x: m("x"), y: m("y"), w: m("w"), h: m("h"), kind: found.kind, miss: 0 };
}

/**
 * Grow a crop's short side towards its long side (centred, clamped to the frame): DeepLab stretches its input to a
 * square, and a 2.5:1 crop of a lying cat came back with no cat at all.
 */
export function square(b, fw, fh) {
  const s = Math.max(b.w, b.h), w = Math.min(fw, s), h = Math.min(fh, s);
  const x = Math.round(Math.min(fw - w, Math.max(0, b.x + b.w / 2 - w / 2))), y = Math.round(Math.min(fh - h, Math.max(0, b.y + b.h / 2 - h / 2)));
  return { x, y, w: Math.round(w), h: Math.round(h) };
}

/** The crop for a cut-out: the box (frame px) widened 25 % (12.5 % each side), clamped to the frame, whole pixels. */
export function widen(b, fw, fh, k = 0.25) {
  const x0 = Math.max(0, Math.floor(b.x - b.w * k / 2)), y0 = Math.max(0, Math.floor(b.y - b.h * k / 2));
  const x1 = Math.min(fw, Math.ceil(b.x + b.w * (1 + k / 2))), y1 = Math.min(fh, Math.ceil(b.y + b.h * (1 + k / 2)));
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}
