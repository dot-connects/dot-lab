# Build contract — #002 김치볶음밥 한 그릇 (team build, 2026-10-23)

One page, plain scripts (no modules, no libraries), everything on `window.KFR`. Canvas 2D only. Target: 60 fps on a
mid Android phone, opened from a Threads/Instagram link. Each file is owned by one person; nobody edits another's file.

```
site/index.html      shell: canvas, result overlay, fonts, loads js in this order        (lead)
site/js/core.js      constants, math, projection, colour ramps, shared art helpers      (lead)
site/js/input.js     devicemotion/orientation + pointer fallback -> flick / tilt events (input)
site/js/rice.js      rice + kimchi + scallion grains: heat, slide, wok-toss wave, draw  (rice)
site/js/egg.js       soft-body fried egg: cook, slide, toss/flip, fold/tear/burst, draw (egg)
site/js/fx.js        flames, steam, smoke, oil spatter, sparks + KFR.Audio (procedural) (fx)
site/js/game.js      state machine, wok pose, stage (counter, wok, handle), HUD,        (lead)
                     scoring, result slip, share image, demo autopilot
```

## World and projection (core.js)

- World units: wok interior radius = 1. `u` sideways (+ = right on screen), `v` depth (+ = far side, top of the
  screen; − = near side, toward the handle), `h` height above the wok bottom centre.
- Wok surface height: `KFR.surf(u,v) = 0.35*(u*u+v*v)`; the rim is at r = 1 (h = 0.35). Outside r > 1 is off the wok.
- Gravity `KFR.G = 12` world units / s².
- `KFR.view(W,H)` → `{R, ct, st, cx, cy, D}`; `R` = wok radius in px. View tilted `THETA = 35°`.
- `KFR.proj(view, pose, u, v, h)` → `[x, y]` screen px. `pose` = wok pose this frame (below); food that sits in the
  wok follows the pose; airborne food is projected with the same pose (good enough, keeps it simple).
- `pose = {du, dv, lift, lean, t}`: the wok's offset this frame in world units (`du` sideways shift, `dv` forward push),
  `lift` (h offset), `lean` (far-lip raise, 0 = flat, 1 ≈ 12°). Owned by game.js, read by everyone's draw().
- Depth sort: draw far (larger v) first.

## Events (input.js → game.js)

- `KFR.Input.start(onFlick, onTilt)`; `KFR.Input.requestPermission()` must be called from a tap (iOS).
- `onFlick({strength, snap, dirU, dirV})`: strength 0.3–1.6 (1 = a normal chef toss), snap 0–1 (sharpness of the
  wrist pop), dirU −1..1 sideways lean, dirV −1..1 forward(+)/back(−) component.
- Tilt is polled: `KFR.Input.tilt` = `{u, v}` −1..1, smoothed, dead zone applied, relative to the level captured at
  start. + u = right side lower, + v = far side lower.
- Lead's calls into input (game.js): `KFR.Input.start(onFlick, appElement)` once at boot (attach pointer listeners to
  appElement; sensors only after permission), `requestPermission()` → Promise (resolves even if denied: then fall back
  to touch), `useTouch()` (force touch mode), `demoTilt(u, v)` (attract mode drives tilt; ignored once a real game
  started), `armAfterRelease()` (after a restart, ignore flicks until the finger lifts / the phone settles 300 ms),
  `KFR.Input.onResultFlick` (optional hook the lead sets; call it on a flick while `KFR.Game.state.phase==='result'`).
- `KFR.Input.mode` = `'motion' | 'touch'`. Touch fallback: fast upward stroke = flick (strength from peak speed,
  snap from how fast the peak is reached, dirU from sideways lean); press-and-drag slowly = tilt.

## Simulation modules

Every module: `create(opts)` → state object; `update(state, dt, ctx)`; `draw(c, state, view, pose)`. `ctx` =
`{t, tilt:{u,v}, heat:(u,v)=>0..1.2, wokPose:pose}`. `heat(u,v)` is the flame heat at a point of the wok floor
(hottest at the centre), owned by core.js.

- `KFR.Rice`: `create({n})`, `toss(state, flick, t)` (starts a toss: wave along the far lip), `update`, `draw`,
  `drawBehind`/`drawFront` if it needs layering around the egg (not required), `stats(state)` →
  `{good, burnt, raw, spill}` fractions of all grains, `airborne(state)` → count, `contactShare(state)` → 0..1 (share
  on the metal, drives sizzle + glow), `hotspots(state, k)` → up to k `{u,v,h,cook}` for smoke/steam emitters,
  `drawBowl(c, state, x, y, r, ct)` (mound of the grains kept, for the result slip), `lastToss` → per-toss quality
  `{label, lift, spill}` for feedback text.
- `KFR.Egg`: `create()`, `toss(state, flick, t)`, `update`, `draw`, `inPan(state)`, `outcome(state)` →
  `{done, caught, flipped, rot, folded, torn, burst, cookTop, cookBottom, impact}`; `drawOnRice(c, state, x, y, r, ct)`
  for the result slip. `hotspots(state,k)` as rice.
- `KFR.Fx`: `create()`, `update(state, dt, {flare, burning, steam:[hotspots]})`, `flare(state, strength)` (a toss),
  `oilCatch(state, strength)` (big toss: flame into the wok), `spatter(state, u, v, n)`, `drawBehind(c,…)` (burner
  flames, drawn before the wok body), `drawFront(c,…)` (rim flare, flame in the wok, steam, smoke, spatter),
  `shake` (px offset to apply to the whole frame), `haze` (0..1 smoke over the screen).
- `KFR.Audio`: `unlock()` (from a tap), `sizzle(level)` (continuous, 0..1), `toss(strength, snap)`, `land(kind)`
  (`'rice' | 'egg-soft' | 'egg-splat' | 'spill'`), `flare()`, `ding()`, `stamp()`, `burnWarn(level)`, `mute(bool)`.

## Colour ramps (core.js, shared so the result slip matches the play)

`KFR.col.rice(cook)`, `KFR.col.kimchi(cook)`, `KFR.col.scallion(cook)`, `KFR.col.eggWhite(cook)`,
`KFR.col.eggEdge(cook)`, `KFR.col.eggUnder(cook)` → `[r,g,b]`; `KFR.gloss(cook)` → 0..0.6. Cook scale: 0 raw,
0.55–1.3 tasty (scored good), > 1.3 burnt, ~1.75 charcoal. Egg: 0.6–1.4 good underside, > 1.9 burnt.

## Fields and extras the lead's game.js reads (must exist)

- Rice: `state.lastToss = {t, label, color, shown:false}` set by `toss()`/landing (label = short Korean feedback for that
  toss, e.g. "좋아!", "불맛!", "너무 세!", "살살…"; color hex), `drawSpilled(c, state, view, pose)` (spilled grains on
  the counter, drawn before the wok), `drawBowl(c, state, cx, cy, r, ct)` returns the screen y of the mound top.
- Egg: `state.u, state.v` (position in the wok), `state.su, state.sv` (slide velocity), `state.cookBottom`;
  `outcome()` also returns `stuck` (never tossed and burnt), `landedNow` (true for exactly one update after landing or
  hitting the floor, plus `u, v` of the impact); `forceStuck(state)` ends the round as stuck.
