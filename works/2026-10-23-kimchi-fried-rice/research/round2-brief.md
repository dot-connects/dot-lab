# Round 2 brief (shared by every reviewer, 2026-10-23)

## Operator's feedback after playing the team build (verbatim, Korean)
> 왜 항상 아래로만 떨어져? 그리고 이게 후라이팬에서 떨어지는 느낌이 아니라 그냥 위에서 아래로 떨어지는 느낌이야.
> 그리고 앞 뒤 옆으로도 떨어지는게 있어야할거같고, 그 파도처럼 웍 휘두를때 그 밥알들이 돌아가는? 그런 모션이 잘
> 안나오는데? 그리고 계란이 접혔다고 뜨는데 그냥 찢어지는데? 그리고 왜 항상 뒤집을때마다 탔다고 떠? 안뒤집고
> 오래놔두면 타는거 아니야? … 많이 나아지긴했어.

In English: (1) rice always falls DOWN the screen — it reads as dropping from above, not as being tossed out of a
pan and falling back in; (2) spills/falls should go forward, back and sideways too, not one way; (3) the wave-like
rolling of the rice when a chef swings the wok barely shows; (4) the slip says the egg "folded" but on screen it just
tears; (5) "탔다!" (burnt) appears on almost every toss — burning should come from leaving rice unflipped too long.
Overall: better than before.

## The build
- Play it: `C:\DC\DD\dot-lab\works\2026-10-23-kimchi-fried-rice\site\index.html` (touch fallback: button "센서 없이 손가락으로 하기";
  the game can be driven from page.evaluate with `KFR.Game.flick({strength, snap, dirU, dirV})`, tilt via
  `KFR.Input.demoTilt(u,v)` only before a real game, state in `KFR.Game.state`).
- Code: site/js/core.js (projection `KFR.proj`), stage.js, rice.js, egg.js, fx.js, input.js, game.js.
- Decisions so far: DESIGN.md, CONTRACT.md, research/*.md (round 1), brief.md (history).
- Screenshot helper to copy: `C:\Users\ehdck\AppData\Local\Temp\claude\C--DC-DD\64e6709a-87e5-42bf-8d7c-731c399f7e06\scratchpad\integ.py`
  (Playwright, `uv run --with playwright python x.py`, `p.chromium.launch(channel="chrome")`, viewport 430x932;
  Bash calls that launch a browser need dangerouslyDisableSandbox=true). Put your own files under
  `C:\Users\ehdck\AppData\Local\Temp\claude\C--DC-DD\64e6709a-87e5-42bf-8d7c-731c399f7e06\scratchpad\round2\<your-role>\`.

## Fixed constraints (operator, do not re-open)
Phone = wok, 35° view from above with the handle toward the hand, steel counter, D:OT 반점 order slip, score /100 =
rice 60 + egg 40, flick direction / strength / snap matter, tilt slides food, soft egg, colour ramp + smoke = burn.

## What every reviewer returns (in KOREAN, ≤ 450 words, no file edits)
1. What you saw (look at real frames you captured, not only the code) from your role's point of view.
2. The root cause of each operator complaint that touches your field (file:line where possible).
3. Your top 5 concrete changes, ranked, each with: what to change, the numbers/equations, which file, effort S/M/L.
4. One thing the others will probably get wrong from your angle.

## Added by the operator after the reviews started
> 폰 비율에 상관없이 모든 비율을 다 고려해줘야해
Every aspect ratio must work: tall phones (9:21), short phones (9:16), foldables/tablets (3:4, 1:1), landscape and
desktop. Today `#app` is width min(100%, 100dvh·0.5) and `KFR.view` sizes the wok from width only (R = W·.43,
cy = H·.47), so short/wide screens can clip the wok and HUD.
