# StandBy r9 — themes everywhere + decided cards: Implementation Plan (v2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship every decision in `spec-r9.md` so that each card, setting and full-screen surface renders correctly
in all ten themes, day and night, on all six test devices.

**Architecture:** Themes become token sets read by one layout per card. Three primitives (`themedSurface`,
`ThemedDigits`, `ThemeBackground`) are the only readers of style switches; colours flow through `Ink`, which now
reads tokens (with role colours) and the candle night. Old family code is deleted **last**, after every reader has
moved.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric + Roborazzi, JDK 17, Gradle module `:standby`.

**Spec:** `works/2026-11-09-standby/spec-r9.md` + mockup page linked there (dp reference).

**Reviews folded in:** Fable 5.2 (C1–C5, H1–H10, M) and Codex (1–17), 2026-11-11. v1 → v2 changes are marked ⟲.

## Global Constraints

- Paths: app `C:\DC\DD\dot-lab\works\2026-11-09-standby\app`; sources `standby/src/main/java/kr/dot/sb/`;
  tests `standby/src/test/java/kr/dot/sb/`.
- ⟲ Commands (PowerShell): `$env:JAVA_HOME='C:\DC\tools\jdk-17'; .\gradlew.bat :standby:testPersonalDebugUnitTest -q`
  (before Task 17 adds flavors: `:standby:testDebugUnitTest`). Shots: add `-Proborazzi.test.record=true`;
  ⟲ dev filters `-Pshot.themes=IPHONE,DAYPAPER -Pshot.group=board` (Task 1). Full matrix only in Tasks 21–23.
- Slot geometry untouched. Touch targets ≥ 48 dp. No theme names in any picker.
- ⟲ Reading roles fixed: message 15, head 18, meta 13 dp × text size (0.9/1.0/1.15). Hero numbers are slot-sized,
  never scaled. Text drawn **over pictures** (media cover, photos, memories, art) is always white + scrim,
  whatever the theme.
- ⟲ Settings chrome (`settings/Kit.kt` SK colours) stays un-themed; only previews inside settings are themed.
- ⟲ Accent priority: `board.accent ?: edited clock colour ?: tokens.accent`. The literal `0xFF76A6F3`
  (`BoardView.kt:158,184`, `Tokens.kt:85`, `settings/Kit.kt:89`) disappears.
- Night = candle for every theme. At night: glow/flip/glass/outline effects off, plates → candle plate, light
  themes' backgrounds → black (no grey half-state: `night` snaps light themes, ramps dark ones).
- ⟲ Usage card, `Feeds`, `UsageParser`, LAN/bot secrets live only in the `personal` flavor source set; the
  `public` flavor has a no-usage stub and its APK is checked for token strings.
- Tests only where wrong is expensive: codec/migration, layout choosers, parsers, token contrast, pixel lint.

## Decisions (D) — reviewers' objections resolved

- **D1** Family bodies (`PaperCards`, `LampCards`, `ThemeFaces`, `ThemeOverlays`, `Family`, `Surface`, `Palette`)
  are deleted ⟲ **in Task 22, after** every replacement exists and `grep` shows no reader.
- **D2** ⟲ Board background is `BoardBg` (JSON key `boardBg`): `THEME` (default) + 8 presets. The clock's own
  `Background` enum (`Config.kt:26`) is untouched.
- **D3** ⟲ Card type merge is alias-only in Task 2 (`NOTIF_STACK|NOTIF_LIST → NOTIF`, `VIDEO → MUSIC` on decode);
  enum values are removed only in Task 22 with their last callers.
- **D4** ⟲ Clock defaults migrate: on decode a stored style equal to the **r8** default of its face is dropped,
  so the theme's new default applies; real edits are kept.
- **D5** ⟲ Regression gate = Roborazzi compare 0 diff after Tasks 2–3 (no visible change yet); from Task 4 on,
  shots are compared with the mockup page, not the baseline.
- **D6** ⟲ Phone-widget dark/light is best effort (`setOnLightBackground`, `uiMode` context); bind + configure run
  in `StandbyActivity`/`SettingsActivity` (Dream cannot), ids stored in config, hosted by both Activity and Dream.
- **D7** ⟲ `AnalogStyle` (ROUND/RECT/BIG4/MINIMAL/DUO) replaces `AnalogNumbers.DUO` as the dial layout; decode maps
  `analogNumbers = DUO` → `analogStyle = DUO, analogNumbers = TWELVE`.
- **D8** ⟲ Mood light (`Glow.kt`, unfold) keeps its own colour setting; only its pills/text take candle/theme ink.
- **D9** ⟲ Notification-shade icon mode (`Listener.kt:101,277`): mono glyphs when `tokens.monoIcons`
  (PAPER, NEON, TERMINAL), colour otherwise.

## File structure

| File | Responsibility |
|---|---|
| `ui/theme/ThemeTokens.kt` (new) | `ThemeTokens`, `Roles`, `PlateStyle`, `DigitStyle`, `BgSpec`, ten sets, `NIGHT_CANDLE` |
| `ui/theme/Theme.kt` | `StandbyTheme.tokens`; ten `Themes.*` with fonts, caps, ramp, per-theme `clockDefaults` |
| `ui/theme/Tokens.kt` | `Ink.of(night, accent, tokens)`; `nightFilter` → candle |
| `ui/theme/Primitives.kt` (new) | `themedSurface`, `ThemedDigits`, `ThemeBackground`, `LocalTextScale` |
| `model/Config.kt`, `settings/ConfigCodec.kt` | `Theme`×10, `BoardBg`, `TextSize`, `AnalogStyle`, `SlotOptions`, aliases, migrations |
| `ui/cards/Layouts.kt` (new) | pure choosers |
| `data/*` | weather days, calendar upcoming, usage sessions/bots, notification grouping |
| `ui/cards/*`, `ui/screens/*`, `ui/clock/*` | token-driven bodies |
| `platform/WidgetHost.kt`, `ui/cards/PhoneWidget.kt` | AppWidget hosting |
| `standby/build.gradle.kts`, `src/personal/`, `src/public/` | flavors |
| test `ShotKit.kt`, `tools/sheets.py`, `tools/lint_px.py` | matrix filters, per-card sheets, pixel lint |

---

### Task 1: Shot matrix, filters, readable sheets, pixel lint ⟲

**Files:** `test/.../ShotKit.kt:36-57`, `test/.../board/BoardShots.kt`, `screens/ScreenShots.kt`,
`clock/ClockShots.kt`, `screens/BrightnessBarShots.kt`; Create `app/tools/sheets.py`, `app/tools/lint_px.py`;
`app/.gitignore` += `tools/baseline-r8/`.

- [ ] `THEMES` reads `System.getProperty("shot.themes")` (comma list) else `Theme.entries`; groups filtered by
  `shot.group`; Gradle passes `-Pshot.*` as system properties (`tasks.withType<Test> { systemProperty(...) }`).
- [ ] Night shots: every card × 6 devices for **one** theme (IPHONE); plus a test asserting the night render of
  PAPER, SKY, DAYPAPER equals IPHONE night pixel-for-pixel on 3 cards (night ignores the theme).
- [ ] `BrightnessBarShots` and `SettingsShots` iterate `THEMES`.
- [ ] `sheets.py`: one sheet **per card group**, rows = themes (labelled), columns = size classes, **1:1 scale**,
  missing cell drawn as a red ✕ (no silent shift); output `build/shots/_sheets/<group>.png`.
- [ ] `lint_px.py`: fails if (a) any PNG under a light theme (DAYPAPER, RADIO) has > 2 % near-black pixels outside
  art/photo cards, (b) any PNG contains `#76A6F3` or `#FC0000` (legacy blue / red night) after Task 4.
- [ ] Record baseline to `tools/baseline-r8/`. Commit `test(lab): standby shot matrix filters, sheets, pixel lint`.

### Task 2: Config — themes, BoardBg, TextSize, AnalogStyle, SlotOptions, migrations ⟲

**Files:** `model/Config.kt`, `settings/ConfigCodec.kt`; Test `test/.../settings/ConfigCodecTest.kt`.

```kotlin
enum class Theme(val ko: String) { IPHONE("아이폰"), PAPER("신문"), MOODLIGHT("무드등"), CANDLE("촛불"), FLIP("플립 시계"),
    SKY("하늘"), DAYPAPER("종이"), RADIO("브라운 라디오"), NEON("네온"), TERMINAL("터미널") }      // ko = logs only
enum class BoardBg { THEME, BLACK, GRAPHITE, MIDNIGHT, AMBER, AURORA, TIME_OF_DAY, PHOTO_BLUR, SOLID }
enum class TextSize(val f: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f) }
enum class AnalogStyle { ROUND, RECT, BIG4, MINIMAL, DUO }
data class SlotOptions(val widgetId: Int? = null, val opacity: Float = 1f, val widgetMode: WidgetMode = WidgetMode.THEME,
    val radiusDp: Int = 22, val hideAtNight: Boolean = false)
enum class WidgetMode { THEME, DARK, LIGHT }
// Board: + options: Map<String, SlotOptions> = emptyMap()   // key "row:col"
// AppConfig: + boardBg = THEME, boardBgColor = 0xFF1A1430, textSize = NORMAL, analogStyle = ROUND
```

- [ ] Failing tests: alias decode (`NOTIF_STACK`/`NOTIF_LIST`/`VIDEO`), unknown theme → IPHONE, round trip of the
  new fields + `options`, r8 default clock style dropped vs edited style kept, `analogNumbers DUO → analogStyle DUO`,
  old JSON without new keys → defaults. **Enum values stay** (aliases point to existing `NOTIF_LIST`/`MUSIC`
  until Task 22 renames).
- [ ] Implement; run; existing suite green; Roborazzi compare 0 diff. Commit.

### Task 3: ThemeTokens + ten themes + contrast test ⟲

**Files:** Create `ui/theme/ThemeTokens.kt`; Modify `ui/theme/Theme.kt`; Add fonts `res/font/plex_mono_500.ttf`
(OFL) and keep existing; Test `test/.../theme/TokenContrastTest.kt`.

```kotlin
data class Roles(val red: Long, val orange: Long, val yellow: Long, val green: Long, val blue: Long,
                 val amber: Long, val snooze: Long, val music: Long, val lilac: Long)      // derived per theme, light-safe
enum class PlateStyle { FILL, RULE, OUTLINE, GLASS }
enum class DigitStyle { PLAIN, ITALIC, FLIP, GLOW }
data class BgSpec(val base: Long, val gradient: List<Long> = emptyList(), val glow: Long? = null, val glowY: Float = 1.15f)
data class ThemeTokens(val bg: BgSpec, val ink: Long, val ink2: Float, val ink3: Float, val accent: Long,
    val plate: Long, val plate2: Long, val plateStyle: PlateStyle, val radiusRatio: Float, val radiusMin: Dp,
    val digitStyle: DigitStyle, val light: Boolean, val roles: Roles, val monoIcons: Boolean,
    val labelIcons: Boolean, val labelTracking: Float)
```

- `StandbyTheme` keeps `words/numerals/time/clockFamily` fonts + their **caps** (measured once per new font with the
  existing cap helper) + `ramp` + ⟲ per-theme `clockDefaults` (light themes: dark hands/numerals, theme accent).
- Token values: spec §4 table verbatim; ⟲ RADIO accent darkened until ≥ 4.5:1 on its plate (start #B4470F).
- Radius ⟲ = `max(radiusMin, short × radiusRatio)` (current rule, `cards/Kit.kt:143`), RULE themes ratio 0.
- [ ] Test: for each theme, `contrast(ink, plate) ≥ 4.5`, `contrast(ink2, plate) ≥ 3`, `contrast(accent, bg) ≥ 3`,
  every `roles.*` vs bg ≥ 3; night tokens too. FAIL → set values → PASS. Commit.

### Task 4: Ink reads tokens; candle night; image filter ⟲

**Files:** `ui/theme/Tokens.kt:24-91` (`Ink.of`), `ui/cards/Kit.kt:353-369` (`nightFilter`), callers
`BoardView:184`, `Cards:105`, `ClockFaces:64`, `StandbyRoot:166`, `NotifCards:104`; `ui/clock/TimeText.kt:89`.

- [ ] `Ink.of(night, accent, tokens)`; role getters read `tokens.roles`; night lerps to `NIGHT_CANDLE` (light
  themes snap at night ≥ 0.5). `nightFilter` → candle colour matrix (warm grey, no red). Effects collapse at night.
- [ ] Shots: all `*-night-*` + cards with art; `lint_px.py` (b) passes. Commit.

### Task 5: Primitives + reading roles ⟲

**Files:** Create `ui/theme/Primitives.kt`; Modify `ui/cards/Kit.kt:132-214` (`Env.read/head/meta`, both `Txt`).

```kotlin
val LocalTextScale = staticCompositionLocalOf { 1f }
fun Modifier.themedSurface(t: ThemeTokens, ink: Ink, shape: Shape, boardBg: BoardBg): Modifier  // translucent when boardBg != THEME
@Composable fun ThemedDigits(text: String, size: Dp, t: ThemeTokens, ink: Ink, style: ClockStyle?, modifier: Modifier = Modifier, twoLines: Boolean = false)
@Composable fun ThemeBackground(t: ThemeTokens, bg: BoardBg, custom: Long, night: Float, photo: ImageBitmap?, modifier: Modifier)
```

- ⟲ `ThemedDigits` is an adapter over `StyledTimeImpl` (`DigitalFaces.kt:343`): a user-edited `ClockStyle`
  (font/effect) wins; FLIP/GLOW apply only when the style is the theme default. Scope table: clock faces + clock
  cards + timer digits use it; temperature, ETA, alarm time use the theme font plain.
- ⟲ Reading roles: `Env.read = 15.dp × scale`, `head = 18`, `meta = 13` (replaces `Reading.read`);
  `LocalTextScale` provided at `StandbyRoot` and `ShotKit.shot(textScale)`.
- [ ] Shot test `PrimitivesShots` (every theme: 4 plate styles, digits, 9 backgrounds). Commit.

### Task 6: Background ownership on every page ⟲

**Files:** `StandbyRoot.kt:157-158`, `settings/Host.kt:136`, `settings/Preview.kt:67`, `ui/clock/TimeText.kt:270-277`,
`DigitalFaces.kt:312`, `WorldFace.kt:39`, `FloatFace.kt:42`, `PhotosPage.kt`, `Overlays.kt`.

- [ ] One owner per page: root draws `ThemeBackground`; faces draw their own background only when the clock style
  has `background != BLACK`; `Color.Black`/`Color.White` literals gone except scrims (grep gate in the commit).
- [ ] Shots `clock/*`, `screens/photos*` for all themes; `lint_px.py` (a) passes. Commit.

### Task 7: Board + cards plates through tokens

**Files:** `BoardView.kt:184-186`, `Cards.kt:105-128`, `NotifCards.kt:70-105`. Replace `Surface.*`/`lampPlate`/`tint`
with `themedSurface`. Shots vs mockup "테마 전체" tab. Commit.

### Task 8: Layout choosers (pure) ⟲

**Files:** Create `ui/cards/Layouts.kt`; Test `LayoutsTest.kt`. Same functions as v1 (`mediaShape`, `notifTier`,
`weatherShape`, `calendarShape`, `tileGrid`, `rectPoint`) with ⟲ `tileGrid(6, 338f, 380f).cols == 2` (maximises
the smaller tile side). Tests from real slot sizes; FAIL → implement → PASS. Commit.

### Task 9: Data paths for the new cards ⟲

**Files:** `data/Parsers.kt:49-74` + `ui/state/State.kt:91-100` (weather `days: List<DayForecast>` from Open-Meteo
`daily`), `data/Sources.kt:141-160` (calendar: today + next 14 days, up to 8 → `state.upcoming`),
`data/Logic.kt` (notification grouping by app + conversation → `NotifGroup(count, newest)`). Tests: parser with a
saved Open-Meteo sample, grouping test. Commit.

### Tasks 10–16: Cards (one per row; each: layout per spec §1 + mockup tab, token-driven, shots for all themes,
sheets inspected at 1:1 for clipping/holes/contrast on light themes, commit)

| Task | Card | Notes |
|---|---|---|
| 10 | Media | port 50e5acb bodies to tokens; card tap → `npOpen` (Now Playing), not `openMediaApp` |
| 11 | Notifications | new `NotifCard` (closed tiers A–E/S, open plates, 10 s idle), ⟲ board places `NOTIF_LIST` slots through it |
| 12 | Clock + analog card | one-line / two-line rule; analog card wide/tall date |
| 13 | Weather + calendar | uses Task 9 data |
| 14 | Agenda + D-day | ⟲ split from timer/memories |
| 15 | Timer + memories | memories text always white over photo |
| 16 | Sleep sounds | ⟲ `SleepSound.FIRE` played by `SleepService` through a looped `MediaPlayer` on `res/raw/fire.ogg` (CC0, ≤ 400 KB) beside the procedural `AudioTrack` path; exhaustive `when` in `Sleep.kt:39-53` updated; glyphs in `ui/icons/SoundIcons.kt` |

### Task 17: Usage card + flavors ⟲

**Files:** `standby/build.gradle.kts` (`flavorDimensions("dist")`, `personal`, `public`; secrets `buildConfigField`
only in `personal`), move `data/Feeds.kt`, `UsageParser`, usage fetch in `StandbyStateHolder.kt:360-371`, the usage
card body to `src/personal/java/...`; `src/public/java/...` stubs (`UsageFeature.enabled = false`, card hidden from
pickers). Parser: `dots` (sessions) + `bots` added to `UsageInfo` (test with a real `status.json` copied from
`ai-hub` output, secrets stripped). Gate: build `assemblePublicRelease`-equivalent debug APK and
`strings`-scan for the bot token prefix and the LAN token → none. Commands switch to
`testPersonalDebugUnitTest` (+ `testPublicDebugUnitTest` once). Commit.

### Task 18: Phone widget card ⟲

**Files:** `platform/WidgetHost.kt` (host id 7021; allocate → `ACTION_APPWIDGET_BIND` → configure, all results in
`SettingsActivity`; ids saved in `Board.options`), `ui/cards/PhoneWidget.kt` (AndroidView `AppWidgetHostView`,
size options, opacity, `WidgetMode` via `setOnLightBackground` + uiMode context — best effort, night: dark
overlay or hidden by `hideAtNight`), host started by `StandbyActivity` and `StandbyDream`; theme thumbnails never
host widgets (draw a placeholder). Shot with a fake RemoteViews; real check in Task 23. Commit.

### Task 19a–19e: Full-screen surfaces ⟲ (split)

- **19a Alarm** — `Overlays.kt:149` r9 alarm (sunrise glow, pills); themes: glow colour = accent, light themes
  keep the dark sunrise.
- **19b Live** — `Overlays.kt:343` r9 live (ETA column, timeline, rider).
- **19c Now Playing** — `Overlays.kt:403` = media tall/cover at screen size.
- **19d Banner** — new `NotifBanner` replaces the big notification; ⟲ root action `openNotifCard(key)`: if a board
  page has a `NOTIF` slot, switch to it and open expanded; else `notificationTap`.
- **19e Analog styles** — `AnalogFace.kt` RECT/BIG4/MINIMAL/DUO using `rectPoint`; clock editor style picker;
  ⟲ `Look.of` (`TimeText.kt`) falls back to `tokens.ink` when a user colour is < 3:1 against the theme background;
  hand outline colour from tokens (`AnalogFace.kt:238,276`).
- Each: shots all themes day + IPHONE night; commit.

### Task 20: Pull-down panel + settings ⟲

**Files:** `BrightnessBar.kt` → `TopPanel.kt` (brightness, `STREAM_MUSIC` volume, theme chips = static thumbnails
in a horizontal scroll); `settings/HomeScreen.kt:86-107` → horizontal scroll of static thumbnails, no `th.ko`;
rows for `BoardBg` (+ colour picker), `TextSize`; widget option rows. `BrightnessSwipeTest` stays green. Commit.

### Task 21: Full matrix pass

- [ ] All shots, all themes, 6 devices; night set; `sheets.py`; `lint_px.py`; read every card sheet at 1:1;
  fix and re-shoot until clean. Commit fixes per card.

### Task 22: Delete family code + enum cleanup ⟲

- [ ] `grep -rn "Family\.\|Surface\.\|palette\|paperBody\|lampBody\|PaperAnalog\|LampAnalog\|PaperLive\|LampAlarm"` → only
  the files being deleted; delete `PaperCards.kt`, `LampCards.kt`, `ThemeFaces.kt`, `ThemeOverlays.kt`, `Palette`,
  `Family`, `Surface`, `StandbyTheme.tint/colorIcons/artTint/surface/family`; rename `NOTIF_LIST → NOTIF`, remove
  `NOTIF_STACK`, `VIDEO` (aliases stay in the codec). Full suite + Roborazzi compare vs Task 21 → 0 diff. Commit.

### Task 23: Review + release candidate

- [ ] One independent review of the whole diff (Codex `review`, spec + plan attached); fix Critical/High.
- [ ] versionName 0.3.0; `personal` APK zipped if > 30 MB → SendUserFile; device checklist: widget bind/config/
  dark-light, unplug + relock + side-key behaviour, fire sound, banner → card, text sizes, night candle, each theme
  once on the Fold cover.

---

## Self-review

- Spec §1 → Tasks 9–18; §2 → 2, 5, 20 (relock already shipped a681260); §3 → 4, 6, 19a–e; §4 → 2–7, 22.
- Fable C1–C5 → D2, Task 6, D4 + Task 3 clockDefaults, Task 3 Roles, D3. H1 → D1/Task 22; H2 → D5; H3 → accent
  rule; H4 → 19e + Task 3 clockDefaults; H5 → Task 5 scope; H6 → Task 3 fonts/caps/fields; H7 → night constraint;
  H8 → `SlotOptions`; H9 → Task 20; H10 → commands. Codex 1–17 → D3, D1, Task 17, D2, D4, Task 4, Task 6, D6, D6,
  19d, Task 5, Task 9, Task 16, Task 5 adapter, Task 8, Task 1, constraints.
- Biggest remaining risk: shot time (10 themes × 6 devices). Mitigation: filters in dev, full matrix twice.
- Delegation: Codex (closed specs) — Tasks 2, 8, 9, 17; Claude — visual tasks (needs judgement on shots).
