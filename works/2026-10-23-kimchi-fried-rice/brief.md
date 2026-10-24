# 실험 #002 「김치볶음밥 한 그릇」 — 기획 (2026-10-23, T-0162)

장부 "아는 것 × 기기 입력" 3번(계란후라이 뒤집기) + 운영자가 붙인 웍질 볶음밥. 주사위 스테이크 시안은
"마리오파티와 너무 같다"(운영자 10-23)로 버림.

## 한 줄
폰이 웍이다. 손목으로 튕겨 김치볶음밥을 볶고, 마지막에 계란후라이를 던져 뒤집어 얹는다. 한 그릇 점수 /100.

## 조작 (새 mechanic: 튕겨 던지고 기울여 받기 → 한 그릇 점수)
- 폰: devicemotion 선형 가속 스파이크 = 튕기기(세기 = 튀는 높이), 중력 기울기 = 팬 좌우 위치. iOS 는 시작 탭에서 권한.
- 노트북·센서 없음: 위로 드래그 = 튕기기(속도 = 세기), 옆 드래그 = 팬 이동.
- 진동은 안드로이드만 덤. 신호의 본체는 소리(지글 세기 = 열)와 연기.

## 판
1. 밥 볶기 12초 — 밥알마다 열을 쌓는다. 바닥에 닿아 있는 동안만 익고, 오래 붙어 있으면 탄다. 너무 세게 튕기면 웍 밖으로.
   밥 점수 0~60 = 고르게 익음 − 탄 밥 − 쏟은 밥.
2. 계란 한 번 — 바닥 익는 소리 → 튕겨 던짐(회전 = 세기) → 기울여 팬을 밑으로. 반 바퀴 = 성공, 한 바퀴 = 날 면 위로,
   세게 떨어지면 노른자 터짐, 놓치면 바닥. 계란 점수 0~40.
3. 결과 — 그릇 그림 + 점수 + 웃긴 이름 + 깎인 이유 두 줄 + 최고점 + 0탭 다시.

## 상태 표현 (연기 = 타는 신호)
| 상태 | 밥알 | 연기 |
|---|---|---|
| 생 | 김치 붉은 주황 | 없음 |
| 익는 중 | 윤기, 주황 | 흰 김 가늘게 |
| 딱 좋음 (누룽지 끝) | 가장자리 금갈색 | 흰 김 |
| 타기 시작 | 짙은 갈색 | 회색 연기 굵어짐 |
| 탐 | 검정, 채도 빠짐 | 검은 연기 + 화면 위로 번짐 |
Overcooked 류: 탄 음식은 채도를 빼고, 연기는 흰색(익음) → 짙은 회색(탐).

## 숫자의 근거
점수 상수(12초, 60/40, 반 바퀴 판정 폭)는 우리 게임 규칙 — 결과 화면에 산식 한 줄. 웍질 모델은 Georgia Tech 연구
(Ko & Hu, J. R. Soc. Interface 2020: 한 번 튕기는 주기 약 0.3초, 밀기 + 들어 올리기 두 동작)를 느낌의 기준으로만 쓴다.

## 다시 하게 만드는 장치
0탭 재시도, 점수 /100 + 최고, 깎인 이유 두 줄, 웃긴 이름("불맛 장인", "탄 밥 장인", "계란은 바닥이 먹음"),
그릇 결과 이미지.

## 하지 않는 것
서버·순위, 재료 고르기, 여러 단계 레벨, 3D 엔진.

## 레퍼런스
- Super Mario Party 「주사위 스테이크를 굴려라」 — 진동이 멈추면 뒤집기, 떨어지면 처음부터 (mariowiki Sizzling_Stakes)
- Cooking Mama (Wii) — 리모컨을 팬 손잡이처럼 튕겨 뒤집기, 너무 높이 던지면 마마가 앞치마로 받는 연출
- Pancake – The Game (iOS) — 탭 한 번 던지고 받기 횟수
- Overcooked — 탄 것 = 회색조, 김 흰색 → 연기 짙은 회색, 지글 소리 신호

## 결과
- 시안 1 (2026-10-23): A 불쇼 단면 · B 위에서 본 웍 · C 중국집 레트로, 같은 엔진, 드래그로 직접 튕겨 봄 — https://claude.ai/artifact/UffN3xvN6CE8PLiRECN2g9
  (mockups.html). 엔진 조정(12초, RATE .2, 밑바닥 1.6·위층 .05 열): 0.85초마다 튕김 81% 좋음, 2.5초마다 62%(탄 33%),
  가만히 21%(탄 51%·덜 익음 27%), 세게 튕김 쏟음 45%, 0.3초마다 미친 듯이 = 덜 익음 97% ← 실폰에서 너무 가혹한지 볼 것.
- 2026-10-23 운영자: B 화면 + C 주문서 채택. 밥알·계란 색은 점점 맛있게 → 오래 두면 점점 타게, 손잡이는 화면 아래(폰 쥔 손 쪽),
  완전 위가 아닌 살짝 비스듬한 입체 시점. → 시안 2 (mockups-2.html) https://claude.ai/artifact/VcHFsCgLTJGk2DcSYxGekg :
  밥알 윤기 점(익는 중~노릇에서만), 계란 흰자 투명→불투명·가장자리 노릇→검정, 뒤집으면 밑면 색, 계란도 타면 연기.
  결과 = 스테인리스 조리대 위 "D:OT 반점" 주문서 + 같은 시점의 밥 무더기 그릇. 시점 각도 22°/35°/50° 고르기.
- 2026-10-23 운영자: 35° 확정, 배경 조리대 유지 + 불 세게, 독립 리뷰 요청. 독립 리뷰(Claude 서브에이전트, 구현 안 한 쪽) 반영해 시안 2 갱신:
  웍 옆으로 크게 솟는 불, 바닥 붉은빛 = 팬에 닿은 밥 비율(다 튕겨 올리면 꺼짐), 공중 밥알도 약하게 익음(RATE×0.1),
  첫 튕김부터 12초 시작(폰 튕기는 그림), 결과지 원인 문구("최대 5.1초 안 튕김", "튕김 35번, 너무 잦음 (맛있는 리듬 12~16번)",
  "한 바퀴 돌아 날 면이 위로 · 살살"), 감점 합 = 60 − 밥 점수, 산식 한 줄, 합계 크게, 최고점 localStorage + 신기록 도장,
  계란 받는 폭 .55 → .32 + 옆 방향 = 튕긴 방향(실제는 손목 기울기), 튕긴 직후 0.3초 팬 입력 잠금, 안 던지면 "계란 들러붙음",
  둥근 밥 무더기·찢긴 김치·파, 결과 그릇 바탕 = 맛있는 색 고정(탄 알만 검은 점), 연기 sprite·조리대 캐시.
  밸런스(헤드리스, 정책 고정): 0.85초마다 92점(밥 87% 좋음) · 2.5초마다 76 · 가만히 25 · 0.3초마다 23(덜 익음+쏟음) · 세게 13(쏟음).
- 구현 때 할 것 (리뷰 중 시안 밖): 모션 입력 — 화면 수직축 가속 + 기준선 초과만 튕김, `acceleration` null 이면
  `accelerationIncludingGravity` high-pass, Threads·IG iOS 인앱 권한 실폰 확인, 드래그 대체 항상 켬. 소리 — iPhone 무음
  스위치가 Web Audio 를 끄므로 열 신호는 그림만으로 충분해야 함(`navigator.audioSession.type='playback'` 시도). 공유 이미지 —
  주문서 canvas → `navigator.share({files})`, 인앱은 이미지 길게 눌러 저장 대체.
- 2026-10-23 operator: fire looked awkward, wok toss should feel like a Chinese chef. Fire rebuilt as particles (soft flame sprites rising around the rim from behind the wok, flare on each toss, oil-catch flame inside the wok on strong tosses). Toss = wok pushes forward and lifts (~0.34 s), rice slides up the far wall and peels off as a sheet that curls back toward the handle; too strong spills on the near side. Balance: 0.85 s rhythm 91, 2.5 s 79, idle 23, 0.3 s rhythm now 84% rice good (realistic: Ko & Hu's 0.3 s cycle), too strong 7.
- 2026-10-23 운영자: 서브에이전트 독립 리뷰 + 실제 플레이 반복 요청, 그리고 "폰 기울기에 따라 볶음밥이랑 계란이 움직여야".
  플레이테스트(14판, 실제 드래그 입력): 밥 판 실력 차 작음, 계란은 화면상 웍 안인데 놓침(판정 .32 vs 그려진 웍 ~1.0),
  튕김 세기를 첫 문턱 순간으로 잼, 결과 그릇 계란 앞뒷면이 보상과 반대, 다시 시작 튕김이 첫 튕김으로 먹힘, 밥이 한 줄로 모임,
  불이 동그란 얼룩. 반영(시안 2 v4):
  - 기울기: 밥 무더기가 모양을 유지한 채 낮은 쪽으로 쏠림, 불은 가운데가 가장 셈(1.15 − 0.6r²) → 벽 쪽 밥은 덜 익음.
    계란은 바닥이 익어야(익힘 .45~) 미끄러지고 벽에 튕김, 던질 때 미끄러지던 속도 + 기울기로 날아감, 공중에선 기울기로 웍을
    조금(±.35) 옮김. 받는 반경 .62. 시안 입력: 누른 채 천천히 끌기 = 기울기, 화면 오른쪽 아래 수평계.
  - 튕김 세기 = 쓸기 중 최고 속도(쓸기가 끝날 때 발사), 다시 시작 뒤 손을 한 번 떼야 튕김 인정.
  - 결과 그릇: 항상 노른자 면, 성공 = 흰자 다 익고 가장자리 노릇, 실패 = 덜 익은 흰자, 접힘 = 반달. 쏟은 만큼 밥 무더기가 작아짐.
    도장은 합계 줄 왼쪽(그림 안 가림), "(계란 없음)"은 배경 알약.
  - 불: 끝이 뾰족한 불꽃 혀(겹친 물방울 3겹), 튕길 때 먼 쪽 테두리 위로 넘어오는 불, 웍 그림은 한 번 그려 캐시.
  - 날 밥 색을 김치 양념 쪽으로, 밥 320알 무더기 반경 .66, 튕길 때 먼 벽 위치를 깊이별로 흩음.
  밸런스(헤드리스 정책): 0.85초 90 · 2.5초 76 · 가만히 26 · 0.3초 48(밥 64%) · 너무 세게 50(쏟음 79%).
- 2026-10-23 operator (away 1.5 h): "Nintendo-grade, team of subagents: design / animation / rules / principles, debate,
  references; flick direction / strength / snap; lip rice higher, wave overturn; soft egg that folds, tears, bursts,
  flops on landing". Team build (site/ = the real work, Pages):
  - Research (4 parallel leads) → research/art.md, anim-physics.md, game-design.md, input.md.
  - DESIGN.md (merged decisions) + CONTRACT.md (module API) → debate round with the game-design lead (first toss not
    graded, splat only in motion mode, 「불맛 많이」 30/30, live flip angle + best-angle record, near-miss line).
  - Builders in parallel, one file each: input.js (devicemotion flick strength/snap/dir + gravity tilt + touch
    fallback), rice.js (lip-launch wave, depth flip on landing, per-toss grade + 불맛 combo), egg.js (PBD soft egg,
    flop flight, fold/tear/burst/splat), fx.js (flames, rim flare, oil catch, steam vs smoke, spatter, procedural
    audio); lead: core.js, stage.js, game.js (flow, pose, HUD, scoring, slip, share image), index.html.
  - Checks: art critique (6 fixes) + playtest (6 rounds via in-page pointer events) → fixes; rice bots: cue reader
    ≥ 55/60 vs blind 0.85 s metronome ≤ 48.
  - Preview (touch only, Artifact blocks sensors): https://claude.ai/artifact/BFeZjB4jYU8eoVNnDWdMWd
  - Not done: real-phone check (axis signs, in-app motion events, feel), listening to the audio, reel.

## 2026-10-24 — round 3: playtest fixes, review team, switch to real 3D
- Operator playtest of round 2: too easy and lumpy (rice moved as one block), wok tilted against the food, egg
  flipped on the rice rhythm, rice landed on the handle, egg tore on almost every flip, oil flare burned mid-pan.
  Fixed in the 2D build (2e7a6bd, b7060ba): per-grain launch with rank/scatter, doneness depends on rhythm again
  (bots: cue 58, 1.2 s 58, 1.0 s 38, 1.5 s 38, mash 23, 0.85 s 21, idle 7), tilt sign, 1.8 s plate pause + egg lock,
  near-rim landing clamp, egg tear only when raw (old: strength >= 1.3 always burst), flare at the far lip.
- Operator: "sellable quality"; review by 6 role agents (art, motion, physics/3D, game, programmer, chef).
- Operator chose real 3D (three.js) and asked for art-style mockups: mockups-3d.html (A illustration / B photo-real /
  C toy), https://claude.ai/artifact/Hx3M7xyEcEDvfP1VApF3iR
- New requirements for the 3D build (operator 10-24): as smooth as possible, tilt more sensitive than expected,
  tilting the phone far to one side spills the food.

## Round 4 (2026-10-24)
- Operator: rice looked like a checkerboard; flip the egg twice?; say how the game starts (or start on 불 켜); toy mint bg off.
- Decided with subagents: start stays on the first toss (iOS motion permission, fair 12 s) + a big cue over the wok;
  second flip is optional (back yolk-up = 4 of the 14 flip points), not required (tempo); toy bg indigo #2f3b66.
- Checkerboard root cause: the box blur's negative spectrum made grains clump on a lattice; 1-2-1 blur + bilinear
  deposit/sample on a jittered grid.
- Expert sweep fixes: tosses after the 3rd were never graded; idle egg 28→7 of 40; vanishing egg at flip; per-toss window jitter.

## Round 5 (2026-10-24)
- Operator: rice still round/checkered, phone tilt too sensitive and laggy, yellow rim glow unreal, egg should flip on a
  golden edge (slides from the start), neglect must visibly brown, smoke and burn; more game/3D reviewers.
- Done (e65a698, 7490819, 4c46ff9): friction heap + lumps + gaussian landing; deviceorientation tilt (wok ~1.25x phone,
  flow ~35°, spill ~42°); oil-smoke wisps + burner flare + pops replace the rim glow; egg lace gold→brown→black + smoke;
  grey burn smoke ~2.3 s after landing; ±0.2 s cue jitter; less free points; one landing sound; egg dip; matte counter.
- Review team rv5 (casual, feel, systems, 3d-art, 3d-food); not applied: motion emit latency, matte burnt grains, flame
  ring/shadow tweaks, toy exposure, layout (bigger wok), red lost lines on the receipt.
- Not verified: real-phone tilt feel, cue readability on a real screen, audio, mid-range Android fps.

## Round 6 (2026-10-24)
- Operator: drop the toy look; an intro instead of starting on the first toss; tilt level = phone flat on the ground;
  the egg rarely flips ("did you try it?"); the forward toss doesn't read.
- Measured before the fix (scripted flicks): only strength 0.9-1.1 flipped cleanly; 0.7 folded, 1.3 folded/burst, 1.5+ burst
  — while rice rewards hard tosses. Decided with dec6-game/3d/input agents.
- Done: toy look removed; intro 2.4 s (oil, rice drops in, kimchi, "볶아!"), clock after +0.5 s grace, first toss ungraded,
  hint only after 1.5 s idle, tap skips from round 2; absolute tilt (deviceorientation beta/gamma from 0, gravity path from
  [0,0,g], DEAD 3°), "폰을 바닥과 평평하게"; egg spin = π + clamp(s-1, ±0.6)·1.2 → clean 0.5-1.5 (≤34°), perfect 0.83-1.17,
  s < 0.45 just hops (retry), burst s > 1.6 / wall > 1.4, drift ±0.22 / -(s-1)·0.15 ± 0.11, 0.15 s settle on landing;
  flying rice: shadow proxy (octahedron, airborne only), velocity streaks, 1.3x brighter, G 40 → 30, camera 10° lower while cooking.
- Checked: egg sweep with steering 0.5-1.5 all clean centre, 1.7 burst; watch run 81; touch 3 sizes; frame 16.7-17.8 ms.
- Not verified: real-phone absolute tilt (most people hold the phone tipped toward them), intro timing feel, audio.

## Round 7 (2026-10-24)
- Operator: show the 2 egg flips as numbers; when to toss/flip is unclear; let me toss back to back; the egg sits under
  the plated rice; less fun than the first rough 2D version (rice barely bounces, burning/cooking/losing rice not felt).
- Panel rv7 (bm, fun, game, byungmat, 3d, action; motion pending). Consensus: mashing was punished (air re-toss = early
  + combo reset, ~9 tosses / 12 s), jitter made the beat unlearnable, zero shake/kick on a toss, progress invisible.
- Done: grade = gap since the previous toss (perfect 0.45-0.9 s, no jitter, early keeps the combo, good builds it);
  beat bar with a gold band under the clock; 불쇼 at 5 combo (4 s, wider band, x1.3); camera kick on every toss/landing/
  spill, fire column on hard snappy tosses; front of the pile peels off as a sheet (55-90 %), higher (hs 0.7+0.9 s, G 38),
  15 % bounce on landing; live 밥 % / 익음 % with -n% pops, +불맛 pops; spilled rice stacks on the counter; rice/lens
  splats, yolk splat "계란 화면 강타!"; clear cook colour steps; pale smoke column from 1.6 s idle; egg: "뒤집기 0/2" in
  the clock, doneness meter with the order's green band, 2nd flip 8 of 14; plated egg on top of the heap, plated grains
  0.62x; 사장님 lines, receipt "바닥에 버린 밥 n알" + memo, share text.
- Checked: scripted beat run (combo 5 → 불쇼), watch runs 80-97 (the bot is near perfect), no errors.
- rv7-motion (measured): the toss flew as one slab and landed in 0.12 s; an air re-toss replaced the toss with an
  empty one. Fixed: lip peel-off delay 0.24 s, rise spread 0.55-1.45, empty launch keeps the toss, wok dip + kick on
  landing, FOV punch on every toss, plated egg 0.62x with a squash. Watch run 85.

## Round 8 (2026-10-24)
- Operator: rice doesn't fly sideways on a sideways flick; when to flip the egg each time is unclear; make it sellable;
  the start screen wok is empty; the share must always carry the game link (all works, saved as a memory).
- Panel rv8 (physics, egg, title, product, novel, sound). Done: sideways = x share of the 3D pulse (lift/pull = forward),
  touch horizontal swipe, landing target moves to the thrown side (hard ones spill), flatter; egg side 1 / side 2 meters
  (side 2 0.2/s, band 0.2-0.4 반숙 / 0.5-0.8 완숙), flip 2 required, auto at 1.18, 타닥 tick at each band, score
  7+5 / 7+7 / 6+4 / 4; attract loop on the start screen (front-left-right-불쇼 at 0.72 s), title/button beat, the tap
  lights a fire column that hides the swap; finale (last 1.5 s: 1.5x high, camera looks up, air shot for the share
  image); 옆 테이블 서비스 on a big sideways spill; receipt floor 변상; tooHard word removed; sound: phone-speaker range,
  beat tick, combo pitch steps, fever, spill thud; share: url in navigator.share, fallback overlay with link + copy, URL
  printed on the image.
- Checked: sideways tosses visibly go sideways; scripted egg on the meters = 40/40 and "뒤집기 2/2"; attract frames;
  watch 82; touch 3 sizes 16.7 ms; no errors. Not heard: the audio. Share sheet untested on a real phone.
