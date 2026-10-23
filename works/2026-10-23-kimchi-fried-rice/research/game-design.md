# Game design brief (game-design lead, 2026-10-23)

## Principles
1. The cue says "now" (Sizzling Stakes: rumble stops → flip). A steady metronome must NOT max the score.
2. Signal without a meter: sizzle pitch, glow, smoke (1-2-Switch eyes-closed games).
3. Never fail because the sensor missed you (top complaint: Cooking Mama Wii, WarioWare Move It). Every registered toss
   gets an instant visible pulse; generous thresholds; penalize outcomes only.
4. Failure looks funny (Cooking Mama fire-eyed Mama) — every bad outcome its own picture + stamp.
5. One input, deep ceiling (Pancake – The Game, QWOP). Strength + timing + tilt is enough.
6. Near-miss tension: show how close ("뒤집기 168° (완벽 ±12°)", "다음 등급까지 3점").
7. Instant restart, run ≤ 30 s.
8. Grade each step and the dish (Cooking Mama medals per step).
9. One-word verbs: "볶아!", "뒤집어!".
10. Small per-run variety, stable core.

## Rice round (12 s)
- Bottom-layer readiness is the cue: resting contact layer goes raw → golden (~0.6–1.0 s at centre heat) → scorching
  (>1.3 s). Sizzle soft hiss → crackle at golden → harsh pop. Glow red → orange-white. Smoke white → grey.
- Grade every toss = readiness at launch × sheet quality (airborne share in the strength band; too weak < 40% lifts,
  too strong spills): 완벽 (golden + sheet ≥ 80%) "불맛!" + flare; 좋음 "좋아"; 이름 (too early) "덜 익었어";
  늦음 (scorching) "탔다!" + black puff; 약함 "약해"; 쏟음 "쏟았다!".
- Wok-hei combo: consecutive 완벽/좋음 raise it; 늦음/쏟음 or a gap > 1.5 s resets. Whoosh pitch +1 semitone per step,
  flare grows. No meter: the fire is the meter.
- Score: rice 60 = doneness 40 (good − burnt − raw − spill, scaled) + 불맛 20; 불맛 = min(20, Σ): 완벽 = 2 × combo factor
  (1.0, 1.2, 1.4, cap 1.6), 좋음 = 1. Est: blind 0.85 s rhythm ≈ 46/60, cue reader 55–60.
- Tilt matters through heat: pile on the wall reaches golden slower.

## Egg round
- Same cue: sizzle crackle when the bottom sets (cook .6); toss window .6–1.4; white edge goes lacy golden.
- Flip quality continuous on landing angle error d from 180°: |d| ≤ 12° 완벽 22 pts + 0.15 s hit-stop + apex slow-mo;
  ≤ 45° 22 − .25(|d| − 12); fold 45–135° → 10 (half-moon); raw side up → 6. Print the angle on the slip.
- Rotation = strength × snap (snap = peak jerk); strength also sets height.
- Catch radius .62. Cushion: pan moving down with the egg at contact saves the yolk even from high. Burst if fall height
  > X without cushion.
- Comedy: too high + strong → egg splats on the screen and slides down ("계란 화면 강타"); miss → counter
  ("계란은 바닥이 먹음"); never tossed ("계란 들러붙음"); burst ("노른자 대참사").
- Egg 40 = flip 22 + yolk 12 + sear 6, flip continuous.

## Per-run variety (one)
Order request on the slip from the start: 「계란 반숙」 window .5–.9, 「완숙」 1.0–1.4, 「불맛 많이」 불맛 ×1.25, doneness ×.75.
Optional 1/20 「쌍란」.

## Stamps (priority: egg disaster > rice cause > total)
100 사장님 나와 보세요 · ≥90 불맛 장인 · ≥75 동네 중국집 에이스 · ≥55 자취 3년 차 · <55 엄마 부르기 ·
rice ≥55 & egg miss 밥만 국가대표 · egg 완벽 & rice <35 계란만 국가대표 · burnt >25% 화재경보기 울림 ·
spill >20% 주방 바닥 볶음밥 · raw >30% 김치밥 (볶음 아님) · combo ≥6 불쇼 하는 사람 · screen splat 계란 화면 강타.
Slip also: "최고 n · 다음 등급까지 k점".

## Per-toss feedback
One floating word ~0.4 s at the food position (never centre-screen): 불맛!, 좋아, 덜 익었어, 탔다!, 약해, 쏟았다!
+ small combo "x3". Sound carries the signal, visuals must suffice alone (iOS mute).

## Do NOT add
Timing bars/QTE rings/meters; more rounds/ingredients/difficulty; penalty for sensor misses; leaderboards/accounts;
rumble dependency; stacking multipliers; tutorial screens; more than one per-run modifier.

Sources: mariowiki Sizzling_Stakes; nintendo.com SG ranking; GameSpot/Engadget/CNN Cooking Mama Wii; Wikipedia Cooking
Mama; Worthplaying; Vice "flipping pancakes"; App Store Pancake; VGC + TechRadar WarioWare Move It; Cubed3 1-2-Switch;
Tampere PlayLab frustrating casual games; Temple Flappy Bird; itch "the kitchen is on fire" devlog.
