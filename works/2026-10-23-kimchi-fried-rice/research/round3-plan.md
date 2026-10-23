# Round 3 plan — rebuild in real 3D (three.js r128)

Inputs: operator answers 10-24 (3D, styles 실사/장난감 as an in-game option, short airtime + bullet time on a perfect
toss, watch-and-judge + rhythm, spill costs points, idle burns fast, higher and flashier tosses score more, 100 rare,
smooth, sensitive tilt, tilting far spills) + `round3-reviews.md`.

## Shape
`site/` is rebuilt in place. Kept from the 2D build: input.js (motion/touch), KFR.Audio (fx.js audio part), result
slip DOM + share image, scoring frame, orders. New files:

| file | job |
|---|---|
| three.min.js (r128, vendored) | renderer |
| world.js | renderer, camera fit for any aspect, lights, the two style kits (실사 / 장난감), fixed-step loop (1/60 + accumulator), bullet time |
| wok.js | carbon-steel wok mesh (round bottom, flared wall, temper rings), burner, wok pose (motion-review curve: push → snap → spring return, waits and catches) |
| food.js | ~1500 rice grains + kimchi + scallion as particles in wok space: gravity along the real tilt, bowl contact, neighbour push (spatial hash), friction; toss launch (whole pile, lip first, curl toward the cook); spill over any rim; cook per grain with the bottom side browning |
| egg3d.js | the existing PBD egg, now in 3D, skinned onto a mesh: whites set from the edge, lace, bubbles; flip with a real catch (tilt under the shadow) |
| fx3d.js | blue burner columns, wok-hei flare over the far lip, steam / oil smoke / burn smoke |
| game.js | flow intro(style pick) → rice 12 s (cue ring, mid event: 밥 추가 or 센불) → plate 1.4 s → egg → result; HUD in DOM |

## Rules (rice 60 + egg 40)
- Doneness 30: per grain, rhythm-driven; idle burns the bottom fast (smoke from 1.5 s, burnt from ~2.5 s).
- Toss quality 20: timing on the visible cue (rim fills 0.4 s, window 0.3 s) × height/spread ("화려하게 높게").
- Spill: every grain on the counter costs points; a big tilt pours food out.
- Perfect toss: bullet time 0.25× for ~0.25 s game time (timer slows too) + 불맛 flare.
- Egg 40: flip angle from visible strength, catch by tilting under the shadow, burst as a slope 1.2–1.4.
- Tuned by bots so a good player sits at 80–90, 100 is rare.

## Order of work (each step ends with screenshots + numbers; step 1 is shown to the operator before going on)
1. World + wok + food sim + tilt + toss + spill (rice playable, both styles). Perf check: 4× CPU throttle ≥ 50 fps.
2. Egg in 3D.
3. Cue, words, bullet time, plate transition, result, audio hookup, style option.
4. Bots + tuning, aspect ratios, review by one fresh agent, operator phone test.

Not in scope: new modes, characters, accounts/publishing (manifest stays draft).

## Egg (operator 10-24: wobble, gradual cooking, yolk dome after the flip, hard flip bursts) — from egg-chef / egg-game / egg-render
- Look (in the mockup now): raw white glossy dark (wok shows through) → matte white from the edge in, the thick ring
  by the yolk last; blisters puff up and sag; lace ring eaten away by noise (gold → brown → black past ~35 s real);
  oil sheen + fizz + spatter streaks; yolk with a deep-orange rim, film creeps over it; wobble slow and long when raw,
  springy when set; flip: edge-on near the apex, white flaps, lands with squash; underside golden with a yolk dome;
  hard landing bursts the yolk, which spreads like honey (√t) and drips.
- Play (for the build): decisions = when (the egg tells you: shake test — stuck while raw, slides when set; no HUD cue),
  how hard (ONE knob: strength → height → airtime → rotation; snap out of rotation), where to catch (landing point off by
  0.3–0.7 R each time, tilt moves the wok ±0.45 R under the shadow; 0.15 s 0.6× slow-mo at the apex), when to take it out
  (tilt forward to slide it onto the rice: 반숙 within 1 s, 완숙 after 2.5 s). Score 40 = timing 12 + rotation 12 +
  catch 10 + take-out 6. Failures with a cause line: torn (early), stuck (no shake), folded (90°/270°), on the rim,
  on the counter, burst (too high / hit the wall), on the screen (motion only), black lace (late).
  Twist: 1 in 5 orders "써니사이드업 · 뒤집지 마".

## Plating (operator 10-24: pouring toward the handle is weird) — chef / motion / game debate, 2 rounds, converged
- Plate on the RIGHT (the ladle-hand side), never toward the handle; it slides in from the right edge and stays half
  visible there as the egg's target. Camera pan right 0.15 + zoom 1.06 while pouring.
- The player pours: tilt the phone right past 20° → the wok rolls with it (max ~40°, 60 ms lag), food leaves over the
  right rim onto the plate. Oiled fried rice falls in small clumps, not a thin stream (chef). Grains that miss land on
  the counter, stay visible and cost points (cap -5). No input for 1.5 s → it pours itself (no bonus, no penalty).
  Flicks are ignored here.
- Then two quick shakes (2 × 90 ms), two ladle pats (2 × 120 ms, automatic: shapes the dome, matches the receipt),
  plate squash + round high mound → '봉긋!' +2 (taken from the toss share so the total stays 100), '완성!' stamp,
  wok back on the fire. Total 1.0–1.8 s.
- Egg take-out later uses the same move: tilt right, slide the egg onto this plate.
- Split vote: game wanted no ladle (adds time); chef + motion kept two short automatic pats.
