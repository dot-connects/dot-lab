# Round 2 plan (lead, from 8 role reviews, 2026-10-23)

Reviews: chef, physics, game, egg, animator, art, code (+ fried-rice expert pending). Conflicts resolved below.

## A. The toss (rice.js + core.js + game.js pose) — "falls from above", no wave, one direction
- Rule the player can feel: **the rice rides up the wall on the side you flicked toward, goes over as a sheet and
  rolls back toward the other side.** dir d = normalize(dirU, dirV) (+ ±15° jitter); forward flick → far wall
  (classic toss, rolls back toward you); pull back → near wall, rolls away up the screen; sideways → that side.
  (Physics wanted pull-back = slide only; chef + animator want every direction to toss. Chose "every direction tosses",
  it is what the operator asked for: 앞·뒤·옆.)
- Wok motion synced to the launch (chef table + animator): wind-up 0–50 ms (back 0.03), push 50–150 ms
  (forward 0.45·s along d), **lip snap at 150 ms: the whole sheet launches within ≤ 40 ms**, catch at landing
  (lift −0.03 cushion 80 ms) then easeOutBack return 120 ms.
- Wok tilt is real (chef): pivot = the rim opposite d (handle-side rim for a forward toss); pitch 5° + 15°·s·(0.6+0.4·snap)
  bump 150–300 ms, roll 10°·dirU; resting pitch −6° (tilted toward the cook so food pools near) + tilt.v·10°,
  roll tilt.u·12°. One transform for wok rim, food and fire: h' = h + (pivot-distance)·sin(pitch) + u·sin(roll).
- Sheet (animator): the launched group's centre follows a ballistic arc (airtime ~0.55 s, apex hang 0.08 s, rise to
  ~0.45 above the rim), each grain = centre + Rot(ω·τ)·offset in the (d, h) plane with ω = −π/T (half turn) —
  the lip grains go over the top and land on the far side of the group = the curl; release to individual ballistics
  in the last 20%; landings within 0.12 s ("툭").
- Frame (physics): airborne grains integrate in world space (wok velocity added at launch); drawn without the wok
  pose; landing tested in wok coordinates.
- Screen height (physics/animator/code): height above the rim exaggerated 0.15 → 0.5 in BOTH core.js K.proj and the
  rice.js inline P (keep them identical). Target: the sheet rises 150–200 px on a 430 px screen, then comes back.
- Landing (chef/code): land on the opposite half from the lip (the pile rolls over each toss), keep 80% of the slide,
  depth flips (bottom up). Spills follow the trajectory: too strong overshoots the opposite rim, angled → side.
- Pile at rest leans to the cook (resting pitch) and follows tilt (existing).

## B. Burn (rice.js + game.js) — "탔다!" every toss
- Per-toss word judges only the toss: 불맛!/좋아 (on time), 너무 빨라 (early), 늦었어 (grey, late) — never "탔다".
- 탔다 = real state: "타고 있어!" when smoke starts (bottom-layer cook > 1.0 on ≥ 5% and no toss for 1.5 s);
  "탔다!" when the burnt share rises ≥ 3 pp since the last toss. First 3 tosses: no negative words.
- Cooking budget to a human rhythm (chef: a toss every 1–2 s; 3–5 s still on the centre scorches): RATE so that a
  1.5 s rhythm ends ~good, 2.5 s leaves some burnt bottom, idle burns. Re-tune the timing window to the new airtime.
  Bots must hold: cue reader ≥ 55/60, blind 0.85 s ≤ 48, mash 0.3 s ≤ 40, 1.5 s rhythm burnt ≤ 15%.

## C. Egg (egg.js + game.js)
- torn wins over folded everywhere (slip, bowl, in-wok); folding no longer lowers the burst threshold.
- Height from strength, rotation from snap: vh = 2.6 + 1.6·st, rot = π·(0.35 + 1.3·snap)·(0.7 + 0.3·st).
- Fold only on a near-vertical landing (|rot mod π − π/2| < 35° and white still soft); other angles drape onto the
  nearest face. Fold drawn as a rounded crease (3 rows smoothstep), keep the smooth outline + lace.
- 0.8 s dwell after landing before the slip. Egg toss direction uses the flick direction too.

## D. Depth cues (rice.js, egg.js, stage.js, game.js order, fx.js)
- Per-unit contact shadow on the floor (sprite), alpha .32 → .08 with height, size grows, offset with key light,
  also near the wall and on the counter for spills; egg too.
- Front lip redrawn over the food (lower half-ring of the wok) so food inside sits behind the near wall.
- scaleAt up to 1.5; airborne +brightness; stretch along velocity 1.6 rising/falling, 1.0 at the apex; one ghost.
- Smoke: per-puff alpha ≤ .35, no full-screen grey film (haze only near the wok, top 30% fades).
- Rice pile gaps: sauce bed alpha .28, radius .08R.

## E. Input (input.js)
- Touch: stroke vector in any direction = flick (strength from peak speed, dir from the stroke), not only upward.
- Motion: dirV keeps its sign (pull back = negative). Demo autopilot varies directions.

## F. Every aspect ratio (index.html, core.js view, game.js HUD)
- #app fills the screen at any ratio (no 0.5 cap); K.view: R = min(W·.43, H·.27), cy from the space between HUD and
  bottom; HUD/level/slip positions from the safe area; check 9:21, 9:16, 3:4, 1:1, 16:9.

## G. Fried-rice expert additions (adopted)
- Readiness measured on the grains of the previous toss that have landed (skip the word if < 60% landed); "late"
  word = "눌었다" (light), never "탔다".
- Burning only from continuous contact: dc = RATE·layer·heat·dt; if cook > 1.05 and rest < 1.5 s, dc ×0.15 (a re-toss
  within 1.5 s parks rice near golden; left alone it scorches from ~3 s). layer = 1.0·(1−d)² + 0.15; non-launched
  grains also re-mix depth. Real-world: golden 2–4 s of contact, 누룽지 5–8 s, burnt 10 s+.
- Mound, not confetti: sauce bed alpha .85, radius .09R, rice shade colour; PILE .45, MOUND .16; rice base a warm
  orange-ochre, kimchi deeper translucent red, burnt bits #5A2A14 specks (never black squares).
- Per ingredient flight: kimchi heavy (vUp ×.85, vBack ×.9, lands first, sticks to the wall), scallion light
  (vUp ×1.15, terminal fall speed 2.5, flutters, ends on top depth .9).
- Ordered landing: launch order → landing band (lip grains land in the nearest band, end on top), 0.3 s slide to
  settle, shared spin sign per toss → a rolling band is visible. Direction follows the flick, not random scatter.
