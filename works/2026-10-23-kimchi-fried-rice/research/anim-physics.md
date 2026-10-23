# Animation + physics recipes (anim-physics lead, 2026-10-23)

Conventions in this note (from the old mockup): x = near(-)/far(+), y = up, z = sideways, wok radius 1, ys(x) = .35x².
In the build these map to CONTRACT.md: x → v (depth), z → u (sideways), y → h.

## 1. Wok toss: direction, strength, snap, wave
Real kinematics (Ko & Hu 2020, PMC7061713): two pendulums, link = wok radius (20 cm). f ≈ 3.0 Hz (one cycle ≈ 0.33 s),
amplitudes 0.2 rad (~11.5°), phase ϕ = 1.0 rad; rotation leads translation ~55 ms (reading). Rice launches only from the
far side; the front lip rises then brakes at ~1.5 g and the rice keeps flying. Airtime 0.22 s; landing inelastic (no
bounce); rice slides back before the next cycle. Keep game G = 12–16 so the sheet reads; wok motion itself ~330 ms.

Input features: dir = unit vector of the push; strength 0..1.6; snap 0..1 (sharp = high).

Per-grain launch:
```
lipPt = 0.92*dir                      // the lip being thrown over (forward toss: far lip, v = +0.92)
d     = dist(grain, lipPt)            // at flick time
reach = 0.35 + 0.6*strength           // weak flick: only rice near the lip goes
if d > reach: slosh only (push toward lipPt 0.15, slide back)
tLaunch = t0 + 0.06 + d/vSlide        // vSlide = 3+3*strength; far grains first → sheet peels
// during slide: move along the wall toward lipPt at vSlide (not a lerp)
lift = exp(-d/0.35)                   // 1 at the lip, falls toward the centre
vUp  = V*strength*(0.55+0.45*lift)*(0.7+0.5*snap)            // V≈4.2
vBack= -V*strength*(0.35+0.5*lift)*(1.1-0.3*snap)            // along -dir: curls toward the cook
noise= (0.1+0.3*(1-snap))*V*strength  // soft flick = loose clump, sharp = clean sheet
spin = vBack*8                        // all tumble the same way → rolling wave
```
- Lip grains go highest and farthest back (land nearest the handle); centre grains only hop → overturning wave.
- On landing set depth = 1 − depthBefore (+ noise 0.1): the seared bottom layer comes out on top (wave ↔ burn mechanic).
- Landing: vy = 0, keep 50% tangential velocity, slide home with damping k = 6/s (~200 ms). 15% get a tiny hop (vy .4).
- Wok render over 330 ms: lean = A*sin(ωt + 1.0) (lip up), push = B*sin(ωt) — offset lean ~55 ms.
- Pitfalls: flick cooldown ≥ 300 ms (or queue). Optional apex hang: G×0.7 while |vy| < 1. Sort only airborne grains.

## 2. Soft fried egg (PBD patch, 2.5D)
- 7×7 grid of 3D particles {p, prev, invMass}, spacing 2R/6, masked to a disk → ~37 points. 1 yolk particle (r .33R)
  tied to the centre by a soft spring. Constraints: structural (4-nbr), shear (diag), bend (2-away). ~60 triangles.
- Step: fixed dt 1/120, 2 substeps/frame, 6 iterations:
  `v=(p-prev)*(1-0.02); prev=p; p+=v+g*dt²; project constraints Δ=(|pi-pj|-L0)*k, each moves Δ/2; collide with
  surface (p.h<surf → surf, tangential v*=0.6)`. Clamp to surface after every iteration (tunnelling).
- Stiffness follows cook: structural 1, shear 0.6, bend 0.08 (raw) → 0.5 (cook ≥ 1). Fix dt and iterations.
- Flip: every particle v = v_cm + ω × (p − cm), ω = 5.2·s; lag ω_i = ω(1 − 0.3·soft·u) (u 0 leading → 1 trailing
  edge, soft = 1 − bend); per-particle air drag 1.5/s → edges flutter, flops instead of a coin spin. Landing on the
  curved wall drapes for free.
- Outcome rules decided at landing (deterministic scoring), physics shows them:
  - Fold: rot error in the fold bin → hinge line through centre ⟂ toss; tween far half 180° about it over 180 ms,
    then back to PBD.
  - Tear: v_imp > V_tear = 5 − 3·|cook − 1| → line through impact point, delete constraints within 0.4 cells.
  - Burst: s > 1.2 or yolk impact > 4 u/s or yolk on a fold/tear line (lower threshold when cook < .8) → yolk becomes
    8 droplets flowing downhill, drawn as overlapping circles (cheap metaball).
  - Settle: max|v| < .05 for 200 ms → freeze.
- Render: project particles; triangle winding after projection picks the face (+ = top white, − = browned underside);
  painter-sort triangles; flat fill shaded by normal·light (0.75–1.05); stroke each tri in its own colour 1 px (hide
  seams); outline path + one radial overlay + lacy rim. Yolk ellipse stretched along velocity (1 + .4|v|/4, max 1.5),
  area preserved. No per-triangle gradients.

## 3. Juice
| Effect | Recipe |
|---|---|
| Anticipation | flick detected at the peak → spend it on follow-through: wok overshoots its return by 6%, easeOutBack 180 ms. Egg ready cue: whites jiggle 2 px at 2 Hz. |
| Squash & stretch | preserve volume sx = 1/√sy. Airborne grains 1 + .25·min(1,|v|/5) along velocity. Egg landing: damped spring on scaleY, start .75, 8 Hz, ζ .35 (~250 ms, one overshoot ~1.08). |
| Hit-stop | 50–100 ms on heavy hits only: clean egg catch 60 ms, yolk burst 90 ms. Never on rice tosses. timeScale .05, keep rendering, buffer input. |
| Screen shake | trauma: +.2 hard toss, +.4 egg slap, +.6 burst; decay 1.5/s; shake = trauma²; max 6–8 px, roll ≤ 1.5°; noise = two sines 23/31 Hz. vibrate(20) on landings (Android). |
| Particles | rice landing: 1 oil spark per 10 grains, 250 ms. Egg landing: 12 oil droplets 1.5–3 u/s, 300–400 ms + steam puff. Perfect flip: 6 sparkles 300 ms + 1-frame white flash α .3. |
| Text/UI | pop scale 0 → 1.2 → 1 in 250 ms (easeOutBack), fade 150 ms. Score count-up 700 ms easeOutCubic. |

References: PMC7061713 (Ko & Hu full text); hu2.me.gatech.edu/Ko/?p=19 (toss videos); Stanford CS348c PBD;
tutsplus tearable verlet cloth; flagcloth.js.org; Eiserloh GDC 2016 camera shake; wayline.io game feel (hit-stop).
Caveats: Ko & Hu flight height / 40% tossed numbers unverified; no Cooking Mama/Sizzling Stakes devlog found.
