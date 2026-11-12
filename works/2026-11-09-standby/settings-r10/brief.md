# StandBy settings redesign (r10) — shared brief

## The app
StandBy is an Android app (Kotlin + Jetpack Compose) that turns a charging phone into a bedside / desk display, like
iOS StandBy. Clock faces, swipeable "boards" of cards (calendar, weather, music, notifications, phone widgets, photos,
timers, sleep sounds…), 10 themes, night mode (red/dim ink), screen-off rules, alarm screen, and on a Galaxy Fold a
"mood light" on the inner screen. Targets: Galaxy Z Fold cover screen (main), Fold inner screen, Flip, bar phones.
Personal build only: an AI-usage card.

The operator says: "there are so many features and options that the settings screens need to be reorganized to be
user friendly." The app has no users yet besides the operator, so nothing has to stay where it is.

## Where to look (read these, do not guess)
- Settings code: `C:\DC\DD\dot-lab\works\2026-11-09-standby\app\standby\src\main\java\kr\dot\sb\settings\`
  - `HomeScreen.kt` (settings home), `BoardScreens.kt`, `ClockScreens.kt`, `MoreScreens.kt` (widgets, night, alarm,
    data, perms, about), `Kit.kt` (row components), `Preview.kt`, `Host.kt` (routes, SettingsEnv), `Perms.kt`.
  - The config model with every option and its default: `...\kr\dot\sb\model\Config.kt` (`AppConfig`).
- Current screens rendered as PNG (Fold cover size unless named): `C:\DC\DD\dot-lab\works\2026-11-09-standby\app\tools\baseline-r9\settings\*.png`
  (home, home-fold, boards, board-editor, clocks, clock-digital, clock-analog, widgets, night, alarm, data, perms, about).
  Open them with Read; they are the ground truth of what a user sees today.
- r9 design decisions (themes, cards): `C:\DC\DD\dot-lab\works\2026-11-09-standby\spec-r9.md` (skim only if needed).

## Operator decisions already made (do not reopen)
1. Every settings screen that changes something visible pins a **live preview at the top** that updates as you change
   options; the preview has a **day / night** toggle so night settings can be judged in night ink.
2. Home is grouped by **purpose**, about four groups. Starting idea (you may improve names and membership):
   화면 (theme, boards, clocks, text size) · 밤과 꺼짐 (night mode, screen off, sleep sound) ·
   알람과 폴드 (alarm screen, mood light, on unfold) · 데이터와 권한 (weather, calendar, usage, permissions).
3. Each screen shows the 3–4 options people actually use first; the rest folds under "자세히" (progressive disclosure).
4. Night mode must be easy to turn **off** ("no day/night distinction, just use it"). `NightMode.OFF` already exists in
   code but is buried; make it obvious.
5. Mood light and fold options are **disabled (not hidden) with a one-line reason** when the phone is not foldable
   (detected via hinge sensor). New in 0.3.1: two switches "StandBy 알람 화면" and "안쪽 화면 무드등".
6. Mockup first (one decisive HTML page, phone-sized, 1 CSS px = 1 dp, real labels and real data), then code in r10.

## Constraints
- All UI text in Korean, plain words, no jargon. The operator dislikes "~냐" tone.
- Do not invent features. Every option you place must exist in `AppConfig` (or be one of the two new switches); if you
  think an option should be removed or merged, say so explicitly with the reason.
- Settings are used in portrait on the phone screen: design for **411 × 914 dp** (Fold cover / bar phone, the size of
  the `*.png` shots; `tall` shots are the same width, scrolled). It must also work on the Fold inner screen
  (932 × 704 dp landscape, `*-fold.png`; two-pane already exists in `Kit.kt Page`). The StandBy display itself is
  landscape (Fold cover 751 × 475 dp), so a live preview is a landscape frame inside a portrait settings page.
