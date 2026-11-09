# StandBy app v0.1 — build plan (2026-11-09 night)

Operator ask (2026-11-09 22:30): build the test app and a first full app overnight — every iPhone StandBy feature plus
the operator's (styling, boards with slots, media incl. YouTube Music/TVING, timer/pomodoro, sleep sound, D-day,
agenda, Claude/Codex usage via the 눈금 feed); design quality above all (every clock and every option combination
composed and consistent); **the app must be light** (operator felt the probe heat up while charging).

Read before coding: `design-system.md` (look), `spec-behaviour.md` (behaviour, acceptance list), `brief.md` (decisions).

## Module

Gradle project `app/`: `:app` = measuring probe (`kr.dot.standby.probe`), `:standby` = the app (`kr.dot.standby`,
package `kr.dot.sb`). Compose (BOM 2024.10.01), minSdk 34. Build: `JAVA_HOME=C:/DC/tools/jdk-17`, `gradlew.bat
:standby:assembleRelease`. In a worktree set `SB_BUILD_DIR` to a unique folder (e.g. `%LOCALAPPDATA%/sb-<name>`).

## Contracts (owned by the lead; do not edit — ask in your report instead)

- `model/Config.kt` — AppConfig, ClockStyle (+ defaults per face), Board/Layout/CardType, presets.
- `ui/state/State.kt` — StandbyState (plain values, minute resolution) + StandbyActions.
- `ui/theme/Tokens.kt` — Ink (day/night twins, one night fraction), Sizes (slot-relative roles + floors), SizeClass,
  Grid (margin/gutter), Motion. `ui/theme/Fonts.kt` — Fonts.ui (Pretendard) / Fonts.clock(FontId, weight).
- Test kit `src/test/.../ShotKit.kt` — Device list (6 shapes), `rule.shot(group, name, device) { … }`, Fixtures.

## Ownership (one agent per area; touch only your files)

| Area | Files | Entry points others call |
|---|---|---|
| Clocks | `ui/clock/**` | `ClockFace(face, style, state, modifier, inSlot)` |
| Board + cards | `ui/board/**`, `ui/cards/**` | `BoardView(board, state, config, actions, modifier)` |
| Screens | `ui/screens/**` | `StandbyRoot(state, config, actions, modifier)` |
| Platform | `platform/**`, `data/**`, manifest | services, state producer |
| Settings | `settings/**` | `ConfigStore`, `SettingsActivity` |

Each area adds render tests under `src/test/java/kr/dot/sb/<area>/` using ShotKit, all 6 devices where it makes sense,
day and night, and looks at its own PNGs (Read tool) before reporting.

## Lightness rules (hard)

1. No work while nothing changes: recompose once per minute (clock text), never per frame. Only the analog second
   hand ticks (1 Hz, only while visible and display on). No infinite animations on the board (Float roll and swipes
   only; sleep wave animates only while sound plays).
2. Display-off state = black screen + stop all tickers; screen brightness minimum.
3. Sensors: light sensor with `maxReportLatency` batching 5 s, accelerometer only at SENSOR_DELAY_UI while the dream
   runs and only for pose/wake decisions; unregister when stopped. No hinge polling.
4. Network: usage every 5 min, weather every 30 min, only while dreaming, on Wi-Fi; stop on dream stop.
5. No wake locks, no foreground service except while sleep sound plays.
6. Bitmaps decoded at slot size (`inSampleSize`), max 3 photos in memory.
7. Request low refresh rate in the dream window (`preferredRefreshRate` = lowest mode).

## Phases

1. Clocks + board/cards + platform in parallel worktrees → lead merges.
2. Screens + settings in parallel → merge.
3. Render matrix (all screens × 6 devices × day/night) → designer review (Opus) → fix round → iPhone-parity review
   against spec-behaviour.md → fixes.
4. Release APK, brief/report, commit.
