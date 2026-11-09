// Screen geometry of a throw. Pure, tested in geom.test.mjs. Screen points are CSS px, y down.

/** video pixel -> screen point, the same mapping as CSS object-fit: cover (centred, cropped). */
export function coverMap(vw, vh, W, H) {
  const k = Math.max(W / vw, H / vh), ox = (W - vw * k) / 2, oy = (H - vh * k) / 2;
  return ([x, y]) => [ox + x * k, oy + y * k];
}

export const FLIGHT_MS = 500, END_SCALE = 0.6;
const LAST_MS = 80, MIN_UP = 0.5, MIN_MOVE = 60, CARRY_MS = 260;

/**
 * Finger path ({x, y, t} from the press on the ball to the release) -> throw, or null when it is not one
 * (moved < 60 px, or under 0.5 px/ms upward over the last 80 ms; the ball then goes back to the hand).
 * The ball lands where the finger's last 80 ms speed carries it in 260 ms, clamped to the screen.
 */
export function release(path, W, H) {
  if (!path || path.length < 2) return null;
  const a = path[0], b = path[path.length - 1];
  if (Math.hypot(b.x - a.x, b.y - a.y) < MIN_MOVE) return null;
  let i = path.length - 1;
  while (i > 0 && b.t - path[i - 1].t <= LAST_MS) i--;
  if (i === path.length - 1) i--;
  const s = path[i], dt = Math.max(1, b.t - s.t), vx = (b.x - s.x) / dt, vy = (b.y - s.y) / dt;
  if (-vy < MIN_UP) return null;
  const to = { x: Math.min(W, Math.max(0, b.x + vx * CARRY_MS)), y: Math.min(H, Math.max(0, b.y + vy * CARRY_MS)) };
  return { from: { x: b.x, y: b.y }, to, curve: curveOf(path) };
}

/** Sum of the signed turns between path segments; |sum| >= 300 degrees = a curve ball, +1 clockwise on screen. */
export function curveOf(path) {
  let sum = 0, prev = null, last = path[0];
  for (let k = 1; k < path.length; k++) {
    const q = path[k], dx = q.x - last.x, dy = q.y - last.y;
    if (Math.hypot(dx, dy) < 2) continue;   // finger jitter has no direction
    const ang = Math.atan2(dy, dx);
    if (prev !== null) { let d = ang - prev; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; sum += d; }
    prev = ang; last = q;
  }
  const deg = sum * 180 / Math.PI;
  return deg >= 300 ? 1 : deg <= -300 ? -1 : 0;
}

/**
 * Ball at flight time u (0..1): straight from release to landing, lifted on an arc whose top is 12 % of the screen
 * height, shrinking 1 -> 0.6 (it goes away from you; 0.6 of the 58 px ball is a 7 cm ball at the creature's 1.2 m), and a curve ball swings 15 % of the width late in the flight.
 */
export function flight(th, u, W, H) {
  const bend = th.curve ? th.curve * 0.15 * W * Math.pow(Math.max(0, (u - 0.4) / 0.6), 2) : 0;
  return {
    x: th.from.x + (th.to.x - th.from.x) * u + bend,
    y: th.from.y + (th.to.y - th.from.y) * u - 0.12 * H * 4 * u * (1 - u),
    s: 1 - (1 - END_SCALE) * u,
  };
}

/** Where the ball came down: inside the target's box = onBody, inside the ring circle = inRing. */
export function hit(p, box, ring) {
  if (!box) return { onBody: false, inRing: false };
  return {
    onBody: p.x >= box.x && p.x <= box.x + box.w && p.y >= box.y && p.y <= box.y + box.h,
    inRing: Math.hypot(p.x - ring.x, p.y - ring.y) <= ring.r,
  };
}
