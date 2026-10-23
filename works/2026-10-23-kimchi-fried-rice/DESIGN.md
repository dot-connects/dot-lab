# Design decisions — #002 김치볶음밥 한 그릇 (team, 2026-10-23)

Merged from research/art.md, research/anim-physics.md, research/game-design.md, research/input.md. Where the leads
disagreed, the decision and the reason are here. API shapes are in CONTRACT.md.

## Settled by the operator (do not change)
35° view, handle toward the hand, steel counter, D:OT 반점 order slip, colour ramp raw → glossy → golden → burnt with
smoke as the burn signal, tilt slides rice and egg, score /100 = rice 60 + egg 40, flick direction / strength / snap
must matter, lip rice goes highest and the mass turns over like a wave, the egg is soft (folds, tears, bursts, flops on
landing).

## Rice round (12 s, clock starts at the first flick)
- 220 grains (art: chunky, fewer) + ~12% kimchi + ~6% scallion, drawn in clumps of 3–6 with a clump shadow.
- Heat: `KFR.heat(u,v)` × layer factor (bottom layer sears, top barely warms). Tilt slides the pile; the wall is cooler.
- Toss (anim §1): lip point from flick direction (forward-biased: `lip = 0.92·normalize(0.6·dirU, 1)`), reach / slide /
  launch-time / lift / curl / noise from strength and snap; sheet peels from the lip; G = 14 with apex hang; inelastic
  landing with a short slide; **depth flips on landing (bottom layer comes out on top)**. Flick cooldown 300 ms.
- Per-toss grade (game-design): readiness = how seared the contact layer is at launch (mean over grains with depth < .35
  of the heat they took since they last landed). golden window = the 0.6–1.0 s-of-centre-heat equivalent; sheet =
  airborne share. Grades and words: 완벽 "불맛!" (golden + sheet ≥ 80%), 좋음 "좋아", 이름 "덜 익었어", 늦음 "탔다!",
  약함 "약해", 쏟음 "쏟았다!". Combo: consecutive 완벽/좋음; reset on 늦음/쏟음 or a gap > 1.5 s.
- Rice score 60 = doneness 40 (good fraction × 40, minus nothing else — burnt/raw/spill already reduce good) + 불맛 20
  (Σ 완벽 2 × combo factor 1.0/1.2/1.4/1.6 cap, 좋음 1, capped 20). Slip lines name the causes.
- Feedback: one floating word at the food position + "x3" combo, sizzle state (hiss → crackle at golden → harsh pop),
  floor glow, steam → smoke. No bars or meters.

## Egg round (≤ 9 s)
- Soft body (anim §2): PBD disk patch ~37 particles + yolk; bend stiffness rises with cook; the white sets from clear
  to opaque; slides with tilt once the bottom sets (cook > .45), bounces off the wall.
- Toss: height from strength, spin from strength × snap (half turn at a normal chef flick), drift from the slide it had
  + tilt; tilt moves the wok ±.35 under it. Catch radius .62.
- Outcome at landing: angle error d from 180° → 완벽 |d| ≤ 12° (22 pts, 60 ms hit-stop, sparkles), 좋음 ≤ 45°
  (22 − .25(|d|−12)), fold 45–135° (10, physical fold), raw side up (6); tear if impact high vs cook (white −6);
  burst if strength > 1.2 or yolk impact high (yolk 0, runny droplets); splat on the screen if strength > 1.4
  ("계란 화면 강타", it slides down the glass); miss → counter; never tossed by 2.4 cook → stuck.
- Cushion catch (game-design) is cut: it needs a reliable downward-velocity read we can't promise in touch mode.
- Egg score 40 = flip 22 + yolk 12 + sear 6 (bottom cook at toss within the order's window).

## Per-run order (one modifier, printed on the slip and shown at the start of round 2)
「계란 반숙」 sear window .5–.9 · 「계란 완숙」 1.0–1.4 · 「불맛 많이」 불맛 ×1.25, doneness ×.75. Random each run.

## Stamps (priority egg disaster > rice cause > total)
As research/game-design.md; plus "라면이 더 맛있겠다" is dropped (use 엄마 부르기). The slip shows the flip angle,
"최고 n · 다음 등급까지 k점".

## Juice (anim §3)
Wok follow-through overshoot 6%; egg landing squash spring (.75, 8 Hz, ζ .35); hit-stop only on egg catch (60 ms) and
burst (90 ms), never on rice; trauma shake (max 6 px, 1.5°); oil spatter on landings; pop text easeOutBack 250 ms;
vibrate(15/30) on Android.

## Input (research/input.md)
Motion: aIG low-pass gravity, linear accel pulse detector, strength / snap (rise time) / dir (impulse + wrist roll),
REFRACT 300 ms, calibration 1 s level hold; tilt from gravity delta with dead zone 2°, full scale 20°, freeze 250 ms
after a flick. Touch fallback always available and auto-chosen when no motion events arrive within 1 s.

## Audio
Respecting the iOS silent switch vs forcing playback: we try `audioSession.type='playback'` after the player taps
"불 켜기" (an explicit start); flag for the operator.

## Debate round (game-design critique, adopted)
- First toss of the rice round is not graded (readiness ~0 then). Combo gap 1.8 s. Sheet bar for 완벽 verified by a
  headless cue-reading bot (≥ 55/60) vs a blind 0.85 s metronome (≤ 48); lowered to 65% if unreachable.
- Splat only in motion mode (flick.splatOK) and strength > 1.4; a toss over 1.1 shudders at the apex + "어어…".
  The slip prints the strength vs the 1.2 burst line.
- 「불맛 많이」 = doneness 30 + 불맛 30 so 100 stays reachable.
- Live landing angle ("178°!"), a separate best-flip-angle record, the near-miss line ("완벽 뒤집기까지 7°" /
  "「등급」까지 k점") as the biggest line under the total, and a one-time "지금!" at the first golden moment.
