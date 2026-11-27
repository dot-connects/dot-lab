// v4 shared motion kit: tweens on rAF, easings, the claw rig, the two-tone capsule and its drop/bounce/roll physics.
const $ = id => document.getElementById(id);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const E = {
  lin: t => t,
  inQ: t => t * t, outQ: t => 1 - (1 - t) * (1 - t),
  inC: t => t * t * t, outC: t => 1 - Math.pow(1 - t, 3),
  sine: t => .5 - .5 * Math.cos(Math.PI * t),
  io: t => t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2,
  back: (t, s = 1.7) => 1 + (s + 1) * Math.pow(t - 1, 3) + s * Math.pow(t - 1, 2),
  inBack: (t, s = 1.7) => (s + 1) * t * t * t - s * t * t,
};
/** fn(eased 0..1, raw 0..1) every frame for ms; resolves at the end. */
function tween(ms, ease, fn){
  return new Promise(res => {
    const t0 = performance.now();
    (function f(now){ const r = clamp((now - t0) / ms, 0, 1); fn(ease(r), r); r < 1 ? requestAnimationFrame(f) : res(); })(t0);
  });
}
/** fn(dtMs, tMs) every frame until it returns true. */
function loop(fn){
  return new Promise(res => { let last = performance.now(), t = 0;
    (function f(now){ const dt = Math.min(34, now - last); last = now; t += dt; fn(dt, t) ? res() : requestAnimationFrame(f); })(last); });
}
const once = (el, kf, o) => el.animate(kf, { fill: "forwards", ...o }).finished;

/** wool puffs + tiny stars at (x,y) inside scr. */
function puff(scr, x, y, n = 8, spread = 30, z = 40){
  for (let i = 0; i < n; i++) {
    const p = document.createElement("i"); p.className = "k-puff"; p.style.cssText = `left:${x}px;top:${y}px;z-index:${z}`;
    const a = i / n * 6.283 + Math.random() * .4, d = spread * (.7 + Math.random() * .5), s = .6 + Math.random() * .8;
    scr.appendChild(p);
    p.animate([{ transform: `translate(-50%,-50%) scale(${s * .5})`, opacity: .95 },
      { transform: `translate(calc(-50% + ${Math.cos(a) * d}px),calc(-50% + ${Math.sin(a) * d * .7}px)) scale(${s * 1.3})`, opacity: 0 }],
      { duration: 480 + Math.random() * 220, easing: "cubic-bezier(.2,.7,.3,1)" }).finished.then(() => p.remove());
  }
}
function sparkle(scr, x, y, n = 5, z = 41){
  for (let i = 0; i < n; i++) {
    const p = document.createElement("i"); p.className = "k-star"; p.style.cssText = `left:${x}px;top:${y}px;z-index:${z}`;
    const a = -Math.PI / 2 + (i - (n - 1) / 2) * .55, d = 34 + Math.random() * 18;
    scr.appendChild(p);
    p.animate([{ transform: "translate(-50%,-50%) scale(.2) rotate(0)", opacity: 1 },
      { transform: `translate(calc(-50% + ${Math.cos(a) * d}px),calc(-50% + ${Math.sin(a) * d}px)) scale(1) rotate(90deg)`, opacity: 1, offset: .6 },
      { transform: `translate(calc(-50% + ${Math.cos(a) * d * 1.15}px),calc(-50% + ${Math.sin(a) * d * 1.15}px)) scale(.4) rotate(160deg)`, opacity: 0 }],
      { duration: 700, easing: "ease-out" }).finished.then(() => p.remove());
  }
}

/* ---------- the capsule: bottom half, contents, translucent top half; one transformed element ---------- */
const CAP_W = 84, CAP_H = 90;
function makeCapsule(scr, content, hue = 0){
  const el = document.createElement("div"); el.className = "k-cap";
  el.innerHTML = `<i class="k-shadow"></i><div class="k-body"><img class="k-con" alt=""><img class="k-bot" src="img/cap-closed.png" alt=""><img class="k-top" src="img/cap-closed.png" alt=""></div>`;
  scr.appendChild(el);
  const c = { el, body: el.querySelector(".k-body"), con: el.querySelector(".k-con"), top: el.querySelector(".k-top"), bot: el.querySelector(".k-bot"), sh: el.querySelector(".k-shadow"),
    x: 0, y: 0, rot: 0, sx: 1, sy: 1, floor: null };
  if (content) c.con.src = content; else c.con.style.display = "none";
  c.top.style.filter = c.bot.style.filter = `hue-rotate(${hue}deg)`;
  /** x,y = centre in screen px */
  c.draw = () => {
    el.style.transform = `translate(${c.x - CAP_W / 2}px,${c.y - CAP_H / 2}px)`;
    c.body.style.transform = `rotate(${c.rot}deg) scale(${c.sx},${c.sy})`;
    if (c.floor != null) { const h = clamp((c.floor - (c.y + CAP_H / 2)) / 260, 0, 1);
      c.sh.style.opacity = .55 * (1 - h * .8); c.sh.style.transform = `translate(-50%,0) translateY(${c.floor - c.y - CAP_H / 2 + 38}px) scale(${1 - h * .5})`; }
    else c.sh.style.opacity = 0;
  };
  c.remove = () => el.remove();
  return c;
}
/** the open capsule pops in under the claw (lid up on its hinge), fill() puts the catch inside,
    then the lid swings shut over it with a snap and a squash. */
const LID_OPEN = 46;
async function closeCapsule(scr, c, fill){
  c.draw();
  c.bot.style.opacity = c.top.style.opacity = "";          // CSS: solid cup, see-through lid
  c.top.style.transform = `rotate(${LID_OPEN}deg)`;
  const pop = [{ transform: "translateY(18px) scale(.55)", opacity: 0 }, { transform: "translateY(-2px) scale(1.05)", opacity: 1, offset: .7 }, { transform: "none", opacity: 1 }];
  c.bot.animate(pop, { duration: 240, easing: "ease-out" });
  c.top.animate(pop.map(k => ({ ...k, transform: `${k.transform === "none" ? "" : k.transform} rotate(${LID_OPEN}deg)`, opacity: k.opacity * .72 })), { duration: 240, easing: "ease-out" });
  await sleep(150);
  if (fill) await fill();
  await sleep(60);
  // "탁": the lid drops shut, overshoots a hair and settles
  await tween(190, E.inQ, t => { c.top.style.transform = `rotate(${LID_OPEN * (1 - t)}deg)`; });
  c.top.style.transform = "";
  c.top.animate([{ transform: "rotate(-5deg)" }, { transform: "rotate(2deg)", offset: .5 }, { transform: "none" }], { duration: 240, easing: "ease-out" });
  puff(scr, c.x, c.y - 8, 7, 34); sparkle(scr, c.x, c.y - 30, 4);
  await tween(260, E.lin, (_, r) => { const k = Math.exp(-r * 5) * Math.cos(r * 13); c.sx = 1 + .17 * k; c.sy = 1 - .17 * k; c.draw(); });
  c.sx = c.sy = 1; c.draw();
}
/** the app's Capsule: falls (g .0042 px/ms²), bounces 3 times (×.45) with a squash, then rolls off to the right. */
async function dropAndRoll(c, floorY, screenW, onLand){
  const r = CAP_H / 2; c.floor = floorY; let vy = 0, vx = 0, bounces = 0, squash = 0, phase = "drop";
  await loop(dt => {
    if (phase === "drop") {
      vy += dt * .0042; c.y += vy * dt;
      if (c.y > floorY - r) { c.y = floorY - r; squash = Math.min(.26, vy * .32); vy *= -.45; bounces++; onLand && onLand(bounces);
        if (bounces >= 3) { phase = "roll"; vx = .22; } }
    } else { vx += dt * .0006; c.x += vx * dt; c.rot += vx * dt / r * 57.3; }
    squash *= Math.pow(.0001, dt / 260);
    c.sx = 1 + squash * .8; c.sy = 1 - squash; c.body.style.transformOrigin = "50% 100%";
    c.draw();
    return c.x - r > screenW + 10;
  });
}

/* ---------- claw rig: string + claw image in a box that can swing ---------- */
const TIP = 108;          // claw image top -> prong tips
function makeRig(box){
  const str = box.querySelector(".k-str"), img = box.querySelector("img.k-claw");
  const g = { x: 132, drop: 40, close: 0, swing: 0, squash: 0 };
  g.draw = () => {
    box.style.transform = `translateX(${g.x}px) rotate(${g.swing}deg)`;
    str.style.height = g.drop + "px"; img.style.top = g.drop + "px";
    img.style.transform = `scale(${1 - .16 * g.close},${1 - g.squash})`;
  };
  g.tipY = () => 48 + g.drop + TIP;        // the box hangs from the rail at top 48
  g.cx = () => g.x + 48;
  g.to = (ms, ease, k) => { const s = { ...g }; return tween(ms, ease, t => { for (const key in k) g[key] = lerp(s[key], k[key], t); g.draw(); }); };
  g.draw();
  return g;
}
/** damped pendulum offset in degrees for time t ms after a jolt of size a */
const pend = (a, t, w = .011, tau = 520) => a * Math.exp(-t / tau) * Math.sin(t * w);
