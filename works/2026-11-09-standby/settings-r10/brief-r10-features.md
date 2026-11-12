# r10 feature & cleanup mockups — brief (2026-11-12)

App: StandBy (Android, Kotlin + Compose), bedside/desk display on a charging phone; main target Galaxy Z Fold cover
screen landscape **751 × 475 dp** (also Fold inner 932 × 704, Flip cover 400 × 362, bar phone). Korean UI, plain words,
polite "~요" tone, never "~냐". Only user: the operator.

Read: `settings-r10/app-review-fable.md` (the whole-app review these items come from), `settings-r10/consensus.md`
(the agreed r10 settings tree; new options must slot into it), `spec-r9.md` / `design-system.md` (skim for tokens).
Code root: `C:\DC\DD\dot-lab\works\2026-11-09-standby\app\standby\src\main\java\kr\dot\sb\`.
Real renders: `C:\DC\DD\dot-lab\works\2026-11-09-standby\app\standby\build\shots\<theme>\{screens,board,clock}\*.png`
(themes iphone, paper, moodlight, candle, flip, sky, daypaper, radio, neon, terminal; `_sheets/` = contact sheets).
Theme tokens (colors, night ink "candle" #B9A48C etc.): `ui\theme\ThemeTokens.kt`, `ui\theme\Tokens.kt`.

## Operator decisions (2026-11-12) — do not reopen
- New features, each with an on/off option in settings:
  1. **희미한 시계** — when the screen would go fully black (display-off) at night, keep a minimal dim clock instead.
     Same design is used for the new display-off choice "꺼진 화면: 완전히 끄기 / 희미한 시계" (one design, two entry
     points). Show where each option lives in the r10 tree (밤 화면 / 켜짐과 꺼짐).
  3. **발열 보호** — battery over ~42 °C → screen dims (about half); how the user is told on screen matters.
  6. **무드등 자동 끄기** — the fold mood light turns itself off after 15 / 30 / 60 min (or 끄지 않음).
- New options: "밤에 알림이 와도 꺼진 화면 켜지 않기" (today a notification wakes a display-off screen, `data/Logic.kt`
  `displayOff(... notification)`; brightness itself stays night-level); "사진 넘김 간격" (today fixed 15 s,
  `ui/screens/PhotosPage.kt` SHUFFLE_MS).
- Bright themes (종이 = DAYPAPER, 브라운 라디오 = RADIO; `light = true`) force night mode on: 밤 화면 "안 씀" is
  disabled for them with a reason.
- Removed now (no mockup): numeral shapes (숫자 모양), the default D-day, the "영상" preset page (media is already one
  merged card/page).
- Theme "아이폰 그대로" is being renamed; use "기본" in mockups.

## Must be decided by looking (mockups)
- Night alarm screen: why the 중단 button is hard to see at night (`iphone/screens/alarm-night-fold-cover-land.png`,
  code `ui/screens/Overlays.kt` AlarmScreen) and 2–3 fixed variants side by side; show snooze minutes if known.
- Sleep sounds: let the operator **hear** all 6 sounds in the page (port the generator in `data/Sleep.kt` NoiseGen to
  Web Audio, start on a tap) and show the sleep card today vs. a fixed card (selected sound clearly marked, play state
  unambiguous). Also show the alternative of **no sleep card** (sound started from where instead?) so the operator can
  decide whether to keep the card.
- Removal candidates shown side by side so the operator can decide by eye: clock effects 무지개 / 유리 vs 단색 / 두 색 /
  번짐; clock 너비 %; theme 촛불 vs 무드등 (day and night); background presets 그래파이트 / 미드나잇 / 앰버 / 오로라
  vs 테마 기본 / 순검정 / 사진 흐림 / 단색.
- Other fixes from the review that change what the screen looks like (before → after): flip-clock theme full-screen
  clock without flip tiles; modular page weather cell half empty; live screen right 40% empty and steps without times;
  digital clock right info touching the screen edge; pull-down panel theme previews identical at night.
