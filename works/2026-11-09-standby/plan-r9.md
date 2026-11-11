# StandBy r9 — themes everywhere + decided cards: Implementation Plan (v6, review closed)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship every decision in `spec-r9.md` so that each card, setting and full-screen surface renders correctly
in all ten themes, day and night, on all six test devices.

**Architecture:** Themes become token sets read by one layout per card. Three primitives (`themedSurface`,
`ThemedDigits`, `ThemeBackground`) are the only readers of style switches; colours flow through `Ink`, which reads
tokens and the candle night. Clock styles are stored as **user overrides only**; defaults are computed from the
theme at render time. Old family code goes card by card as each replacement lands.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric + Roborazzi, JDK 17, Gradle module `:standby`.

**Spec:** `works/2026-11-09-standby/spec-r9.md` + mockup page linked there (dp reference).

**Review log:** v1 → Fable 5.2 + Codex; v2 → /plan-review round 1 (four lenses); v3 → round 2 (Opus); v4 → round 3 (Opus); v5 → round 4 (Opus): no High.
Round logs: `docs/archive/2026-11-11-standby-r9-plan-review/` (workspace root). This file keeps conclusions.

## Global Constraints

- Paths: app `C:\DC\DD\dot-lab\works\2026-11-09-standby\app`; sources `standby/src/main/java/kr/dot/sb/`;
  tests `standby/src/test/java/kr/dot/sb/`.
- Commands (PowerShell): `$env:JAVA_HOME='C:\DC\tools\jdk-17'; .\gradlew.bat :standby:testDebugUnitTest -q`
  → after Task 17: `:standby:testPersonalDebugUnitTest` (+ `:standby:testPublicDebugUnitTest` in Tasks 17 and 23).
  Shots: `-Proborazzi.test.record=true`; dev filters `-Pshot.themes=IPHONE,DAYPAPER -Pshot.group=board`.
- Slot geometry untouched. Touch targets ≥ 48 dp. No theme names in pickers.
- Reading roles fixed: message 15, head 18, meta 13 dp × text size (0.9/1.0/1.15). Hero numbers slot-sized, never
  scaled. Text over pictures (media cover, photos, memories, art) is white + scrim in every theme.
- Settings chrome (`settings/Kit.kt` `SK.*`, swatches) stays un-themed; previews inside settings are themed.
- Accent: `board.accent ?: styleOverride(face)?.color ?: tokens.accent`; the literal `0xFF76A6F3` leaves
  `BoardView.kt:158,184` and `Tokens.kt:85` (settings swatches keep it as one user choice).
- Night = candle colours for every theme (fonts and shapes stay the theme's). At night: glow/flip/glass/outline
  effects off, plates → candle plate, light themes' bg + plates **step** to black at `night ≥ 0.5` while ink lerps.
  No red anywhere (widget night overlay included).
- Usage card + PC feed + secrets exist only in the `personal` flavor; `public` has stubs and a token scan gate.
- Delegation: whoever implements, the other side reviews; a delegated diff is read in full before commit
  (`git diff --staged`). Reviewer for Task 23 chosen by remaining quota at that time.
- Tests only where wrong is expensive (codec/migration, choosers, parsers, contrast floors, secret gate,
  gesture timings); visuals by shots + scripted checks.

## Decisions

- **D1** Family code goes **with its replacement**: each card/surface task deletes that card's Paper/Lamp body and
  dispatch branch; Task 22 removes what is left (`Family`, `Surface`, `Palette`) once `grep` finds no reader.
- **D2** Board background is `BoardBg` (JSON `boardBg`): `THEME` (default) + 8 presets. Clock `Background` untouched.
- **D3** Card merge alias-only on decode (`NOTIF_STACK|NOTIF_LIST → NOTIF_LIST`, `VIDEO → MUSIC`), **deduplicated
  per stack** (`[NOTIF_STACK, NOTIF_LIST]` → one), for boards and `widgetLeft/Right`. Rename to `NOTIF` in Task 22.
- **D4** Clock styles = **overrides**. `AppConfig.styles: Map<FaceId, ClockStyle>` holds only edited faces;
  `clockStyle(face) = styles[face] ?: theme.clockDefaults(face)`. Encode writes overrides only. Decode drops a stored
  style equal to `ClockStyleR8.default(face)` (frozen r8 copy in `settings/Migrations.kt`). The editor's reset
  (`settings/ClockScreens.kt:175`) becomes `styles - face`; edits (`ClockScreens.kt:82`) still copy the effective
  style; `platform/Host.kt:140` (clock page edit mode) writes the same way. **Theme changes go through one function,
  `AppConfig.withTheme(th)`** (settings `HomeScreen`, the pull-down panel, and every theme preview call it): every
  override's colour fields (`color, color2, numeralColor, handColor, secondColor, weekdayColor, tickColor, bgColor,
  bgColor2, litDotColor`) **and `background`** reset to the new theme's defaults (font/size/effect kept).
  Safety net `Contrast.safe(c: Color, against: Color): Color` (< 3:1 → `tokens.ink`) used by `Look.of` **and** by every
  colour that bypasses it — wired once inside `Ink.accent()` / `Ink.mapped()` (`Tokens.kt:54,57`), which covers
  `AnalogFace.kt:87-90`, `WorldFace.kt:40,116`, `DigitalFaces.kt:121`, `ThemeFaces` until deleted; `against` = the
  clock's own background when it has one, else `LocalPageBg` (root, from `BoardBg`/theme). For gradients `against` =
  the stop with the lowest contrast to the colour; for PHOTO_BLUR = the scrim colour. A `withTheme`-reset override
  equal to the new theme default is removed (`styles - face`). Gate: `check_literals.py` fails on `copy(theme`
  outside `model/Config.kt`.
- **D4b** `FontId.THEME` (new first value) = "the theme's digit font + digitStyle"; every `clockDefaults` uses it.
  `ThemedDigits`/`StyledTimeImpl` resolve THEME → `tokens.digitFont/digitWeight`; `capOf(FontId.THEME)`
  (`ui/cards/Kit.kt:96`) returns `tokens.digitCap` (sofia_xcond 0.655 etc., stored per theme); FLIP/GLOW apply only when
  `font == THEME && effect == SOLID`. Clock `Background.BLACK` is shown in the editor as "배경 없음" and means
  "the page background" (theme or board bg); PHOTOS_TIME digits stay white over the photo; SOLAR keeps its own
  gradient sets.
- **D5** Regression gate: after Tasks 2–3, Roborazzi compare 0 diff for the three **old** themes (theme labels in
  settings unchanged until Task 20). From Task 4 on, mockup comparison + scripted checks.
- **D6** Phone widget: bind/configure in `SettingsActivity`; options keyed by `"slotIdx:stackIdx"`, moved/cleared by
  `BoardOps` — including stack edits (`BoardScreens.kt:151 editSlot` goes through a new `BoardOps.editStack`);
  `BoardOps.removedWidgetIds(old, new)` returns ids the caller (settings) passes to `deleteAppWidgetId`;
  the widgets page (`widgetLeft/Right`) never offers the widget card; cancelled bind/configure deletes
  the id; one process-wide `WidgetHost` with ref-counted `startListening/stopListening` shared by Activity and Dream;
  previews draw a placeholder (never a second `createView` for an id). Dark/light best effort.
- **D7** Order inside decode: (1) compare the stored analog style with `ClockStyleR8.default(ANALOG)` and remember
  the result; (2) if `theme == IPHONE` or the style was edited, and `analogNumbers = DUO` → `AppConfig.analogStyle =
  DUO`, `analogNumbers = TWELVE`; (3) drop the style if step 1 said it was the r8 default. `AnalogNumbers.DUO` stays as a
  decode-only alias (→ TWELVE) and is removed from the editor.
- **D8** Mood light keeps its colour setting; pills/text take theme or candle ink.
- **D9** Shade icons mono when `tokens.monoIcons` (PAPER, NEON, TERMINAL): `Listener.kt:101,277` read
  `Themes.of(cfg.theme).tokens.monoIcons` instead of `theme == Theme.PAPER` (Task 11).
- **D10** Fire sound is synthesised in the existing `AudioTrack` path (filtered noise + random crackle bursts in
  `NoiseGen`), so fade/timer/stop work unchanged; a recorded asset only if the operator dislikes it on device.
- **D12** `ThemeTokens.accentText` (default = accent): the accent used for **text**. RADIO keeps #E8641C for shapes (operator, 2026-11-11; below 3:1, waived in the test) and uses #9C3D0D for text; CANDLE text #E6AD6C, DAYPAPER text #A6360A; the contrast test checks `accentText`/plate ≥ 4.5 and `accent` only as a shape colour.
- **D13** Phone widget at night: light widget → invert colour matrix (invert, hue-rotate 180°, warm toward candle, brightness .75); dark widget → candle tone (grayscale + sepia, dim). Light/dark from the mean luminance of a 16×16 downscale of the widget bitmap, recomputed on each `updateAppWidget`.
- **D11** Role colours are **derived**, not hand-set: `Roles.derive(base = iOS system set, bg, light)` adjusts
  lightness until contrast ≥ 3:1 on bg; TERMINAL and NEON map every role to accent/ink variants.

## Token values (Task 3) — everything an implementer needs

| Theme | bg (BgSpec) | ink | ink2 / ink3 α | accent | plate / plate2 | style | radius (ratio, min) | digits (font, weight, style) | light | mono | tracking |
|---|---|---|---|---|---|---|---|---|---|---|---|
| IPHONE | #000000 | #F6EFE6 | .62 / .35 | #FFAD5C | #1C1A18 / #2A2724 | FILL | .075, 12 | Barlow-like `sofia_xcond` 600, PLAIN | no | no | 0 |
| PAPER | #0E0D0B | #ECE4D4 | .55 / .30 | #D8B46A | transparent / #1A1814 | RULE | 0, 0 | `instrument_serif_italic` 400, ITALIC | no | yes | .04 |
| MOODLIGHT | #14100C + glow #FFAA5A2E at (.3,.4) | #FFF2E2 | .60 / .32 | #FFB36B | #2B2119 / #3A2C21 | FILL | .09, 16 | Pretendard 300, PLAIN | no | no | 0 |
| CANDLE | #0C0805 + glow #FFA05047 at (.5,1.15) | #EAD9C2 | .55 / .30 | #E3A35B | #FFCD960F / #FFCD961A | FILL | .085, 16 | Pretendard 200, PLAIN | no | no | 0 |
| FLIP | #0C0C0C | #F2F2F2 | .55 / .30 | #FF9F0A | #1F1F1F / #2A2A2A | FILL | .05, 10 | `sofia_xcond` 700, FLIP | no | no | 0 |
| SKY | gradient #1B2A4A→#31355A→#552D32 (time of day; last two stops darkened in Task 3) | #FFFFFF | .70 / .40 | #FFD29A | #FFFFFF24 / #FFFFFF33 | GLASS | .075, 12 | Pretendard 200, PLAIN | no | no | 0 |
| DAYPAPER | #F3EEE4 | #1D1A16 | .60 / .35 | #B23A0B | #E8E1D3 / #DDD4C3 | FILL | .055, 10 | `source_serif` 600, PLAIN | yes | no | 0 |
| RADIO | #D9D3C7 | #2A2723 | .62 / .38 | #E8641C (shapes) · text #9C3D0D | #EBE6DC / #E0DACE | FILL | .065, 12 | `inter` 500, PLAIN | yes | no | 0 |
| NEON | #000000 | #7DF9FF | .55 / .30 | #FF4FD8 | transparent / #7DF9FF14 | OUTLINE | .07, 12 | `sofia_xcond` 400, GLOW | no | yes | 0 |
| TERMINAL | #050805 | #59FF7A | .55 / .30 | #FFB000 | transparent / #59FF7A12 | OUTLINE | .012, 4 | `plex_mono_500` 500, PLAIN | no | yes | .02 |
| NIGHT_CANDLE | #000000 + glow #FFAA5A14 at (.5,1.15) | #B9A48C | .58 / .30 | = ink | #B9A48C0F / #B9A48C1A | FILL (effects off) | theme | theme font, weight min(theme, 400) | — | — | theme |

`labelIcons` = true for IPHONE, MOODLIGHT, CANDLE, SKY; false otherwise. Fonts: add `res/font/plex_mono_500.ttf`
(IBM Plex Mono, OFL — license line in `standby/LICENSES.md`); caps for new fonts measured once with the existing
`capRatio` helper in `ui/theme/Fonts.kt` and stored as constants. Per-theme `clockDefaults`: hands/numerals = ink,
second hand / digits = accent, background BLACK for dark themes and THEME-bg for light ones.

## Tasks

### Task 1: Shot matrix, filters, sheets
**Files:** `test/.../ShotKit.kt:36-57` (+ `textScale` param), shot classes, `BrightnessBarShots`, `SettingsShots`;
Create `app/tools/sheets.py`; `app/.gitignore` += `tools/baseline-r8/`.
- [ ] `THEMES` from `System.getProperty("shot.themes")` else `Theme.entries`; `shot.group` filter; Gradle passes
  `-Pshot.*` via `tasks.withType<Test> { systemProperty(...) }`.
- [ ] Extra matrix rows: IPHONE night × all cards × 6 devices; IPHONE `TextSize.LARGE` × all cards on FOLD_COVER_L/P.
- [ ] `sheets.py`: one sheet per card group, rows = themes (labelled), columns = size classes, 1:1, missing cell =
  red ✕; output `build/shots/_sheets/<group>.png`.
- [ ] Baseline (3 old themes) → `tools/baseline-r8/`. Commit.

### Task 2: Config — themes, BoardBg, TextSize, AnalogStyle, SlotOptions, overrides, migrations
**Files:** `model/Config.kt`, `settings/ConfigCodec.kt`, Create `settings/Migrations.kt`; Test `ConfigCodecTest.kt`.

```kotlin
enum class Theme(val ko: String) { IPHONE("아이폰 그대로"), PAPER("신문 1면"), MOODLIGHT("무드등"), CANDLE("촛불"), FLIP("플립 시계"),
    SKY("하늘"), DAYPAPER("종이"), RADIO("브라운 라디오"), NEON("네온"), TERMINAL("터미널") }   // old labels unchanged (D5)
enum class BoardBg { THEME, BLACK, GRAPHITE, MIDNIGHT, AMBER, AURORA, TIME_OF_DAY, PHOTO_BLUR, SOLID }
enum class TextSize(val f: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f) }
enum class AnalogStyle { ROUND, RECT, BIG4, MINIMAL, DUO }
enum class WidgetMode { THEME, DARK, LIGHT }
data class SlotOptions(val widgetId: Int? = null, val opacity: Float = 1f, val widgetMode: WidgetMode = WidgetMode.THEME,
    val radiusDp: Int = 22, val hideAtNight: Boolean = false)
// Board: + options: Map<String, SlotOptions> = emptyMap()   // key "slotIdx:stackIdx"
// AppConfig: styles = overrides only (D4); + boardBg = THEME, boardBgColor = 0xFF1A1430, textSize = NORMAL, analogStyle = ROUND
```
- [ ] Failing tests: aliases + per-stack dedupe (boards and widgetLeft/Right); unknown theme → IPHONE; round trip
  of every new field; D7 then D4 order (stored r8 analog default → `analogStyle = DUO`, style dropped); edited style
  kept; **theme switch → encode → decode → `clockStyle(face)` = new theme default** (override map empty);
  `BoardOps.withLayout/fit/duplicate/delete` keep `options` aligned (duplicate clears widgetId).
- [ ] New `Theme` values map to `Themes.IPHONE` in `Themes.of` until Task 3 adds their real entries (keeps `when`
  exhaustive and the build green).
- [ ] `withTheme` test: IPHONE + white hands override → `withTheme(DAYPAPER)` → hands = DAYPAPER default, size kept,
  `background` reset.
- [ ] Rewrite tests that read `styles.getValue(face)` (`SettingsShots.kt:52`, `ConfigCodecTest.kt:27,87,90`) to
  `clockStyle(face)`; reset path test (`styles - face`); theme-change colour reset test; `editStack` re-keying test.
- [ ] Implement; suite green; Roborazzi compare 0 diff (old themes). Commit.

### Task 3: ThemeTokens, ten themes, derived roles, contrast floors — done
**Files:** Create `ui/theme/ThemeTokens.kt`; `ui/theme/Theme.kt`, `ui/theme/Fonts.kt`; Test `TokenContrastTest.kt`.
- [ ] `ThemeTokens(bg: BgSpec, ink, ink2: Float, ink3: Float, accent, plate, plate2, plateStyle, radiusRatio,
  radiusMin: Dp, digitFont: Font, digitWeight, digitStyle, light, monoIcons, labelIcons, tracking)`; `Roles.derive`
  (D11); values from the table above; `StandbyTheme.tokens`, per-theme `clockDefaults`, fonts + caps.
- [ ] Test asserts **floors only** (no hex equality): ink/plate ≥ 4.5, ink2/plate ≥ 3, accent/bg ≥ 3, every role/bg
  ≥ 3, same for NIGHT_CANDLE; **gradient backgrounds are checked at every stop**, translucent plates composited over
  each stop. If a value fails, darken it (RADIO accent, SKY last stop) until it passes and copy the result into
  `spec-r9.md` §4.
- [ ] New `StandbyTheme` entries copy every non-token field (`palette, family = IPHONE, surface, ramp, words,
  numerals, time, clockFamily, minText, radius`) from `Themes.IPHONE`; those fields die in Task 22.
- [ ] Tokens and `clockDefaults` entries for the **new** themes are added; the three old themes keep their current
  `clockDefaults` until Task 5 switches every theme to `FontId.THEME` (keeps the D5 gate meaningful). Nothing reads
  tokens until Task 4. Contrast test also asserts **accent/plate ≥ 4.5** where the accent is used as text. Commit.

### Task 4: Ink reads tokens; candle night; image filter — done (the D4 backdrop is `Ink.page` — page stops or the clock's own bg, passed to `Ink.of` — instead of a separate `LocalPageBg`)
**Files:** `ui/theme/Tokens.kt:24-91`, `ui/cards/Kit.kt:353-369` (`nightFilter`), callers `BoardView:184`,
`Cards:105`, `ClockFaces:64`, `StandbyRoot:166`, `NotifCards:104`; `TimeText.kt:89`.
- [ ] `Ink.of(night, accent, tokens)`: ink/ink2/ink3/accent/roles lerp to NIGHT_CANDLE; bg/plates step for light
  themes; `nightFilter` = candle matrix. Remove `NIGHT_INK*` red constants.
- [ ] `Contrast.safe` + `LocalPageBg` (D4) wired in `Ink.accent()/mapped()`; test: DAYPAPER + user colour #F0F0F0 →
  world city name colour has ≥ 3:1 against the page.
- [ ] Test `NightColourTest`: for 4 themes, `Ink.of(1f, …)` returns the same colour set (ink, ink2, accent, roles,
  plate) — the "night ignores the theme" check, on colours not pixels. Commit.

### Task 5: Primitives + reading roles — done (as built: text size rides on `CardLook.textScale` instead of a `LocalTextScale`; `ThemedDigits` autosizes to its box (no `size` arg); `themedSurface(t, ink, shape, custom)`; FontId.THEME used by the seven r9 themes' defaults — IPHONE/PAPER/MOODLIGHT keep their per-face fonts (iPhone faces differ by font; their family code is replaced card by card); `capOf` had no callers and was deleted; Ink plates now lerp premultiplied (dusk plates were bright brown); TIME_OF_DAY = SKY dusk stops until the operator picks dawn/day/night sets; open: light themes on a custom board bg (dark ink on dark preset) → decide in Task 20)
**Files:** Create `ui/theme/Primitives.kt`; `ui/cards/Kit.kt:132-214`; `ui/theme/Tokens.kt:145-158`.
- [ ] `themedSurface(t, ink, shape, boardBg)`, `ThemedDigits(text, size, t, ink, style: ClockStyle?, twoLines)` —
  adapter over `StyledTimeImpl` (`DigitalFaces.kt:343`); `style == null` means "theme default", only then FLIP/GLOW
  apply. `ThemeBackground(t, bg, custom, night, photo)`.
- [ ] Reading: delete `Reading` and the reading part of `TypeScale.meta`; `Env.read/head/meta` = 15/18/13 dp ×
  `LocalTextScale`; every caller moved (grep `Reading\.` = 0). Shot test `PrimitivesShots`. Commit.

### Task 6: Background ownership on every page — done (as built: r9 clock defaults use Background.BLACK = page for light themes too; `bgColors` paints nothing for BLACK and fades own colours to the night page; the gate also skips `ui/theme/Tokens.kt` (token defs) and settings/ chrome; `copy(theme` lives in `ui/theme/Theme.kt` (`withTheme`), not model/Config.kt; check_light samples corners + edge midpoints (not gutters), fails on ≥ 3 dark samples, skips Solar/photo time; shot harness draws `ThemeBackground` instead of a black box)
**Files:** `StandbyRoot.kt:157-158`, `settings/Host.kt:136`, `settings/Preview.kt:67`, `TimeText.kt:270-277`,
`DigitalFaces.kt:312` (digital + desk), `WorldFace.kt:39`, `FloatFace.kt:42`, `SolarFace`, `PhotosPage.kt`, `Overlays.kt`.
- [ ] Rule: the root draws `ThemeBackground`; a face/overlay paints its own background only when the clock style says
  `background != BLACK` or the overlay is a scrim. Gate script `tools/check_literals.py`: `Color.Black|Color.White`
  outside `Scrim`/`overPicture` helpers and outside files listed in `tools/legacy.txt` (family files, shrinking per D1)
  → exit 1.
- [ ] `FloatFace.kt:42` colon: colour `ink.ink2`, blend `MULTIPLY` when `tokens.light` (SCREEN otherwise).
- [ ] Create `tools/check_light.py`: for DAYPAPER/RADIO PNGs, sample the 4 screen corners and every slot gutter
  midpoint; any sample with luminance < 0.5 → fail (catches black rectangles, ignores dark digits). Commit.

### Task 7: Board + cards plates through tokens — done (as built: every non-notification slot gets `themedSurface` with radius = tokens ratio × short side (PAPER square); the old gutter rules and `BoardGrid.rules` are gone (RULE = top hairline per card, mockup); LAMP per-type tints and album-colour plates dropped (mockup plates are flat); notification rows use the theme plate until Task 11; IPHONE board digits are still the r8 blue default, accent decision in Task 10)
`BoardView.kt:184-186`, `Cards.kt:105-128`, `NotifCards.kt:70-105` → `themedSurface`. Shots vs mockup "테마 전체".
Commit.

### Task 8: Layout choosers (pure) — done (as built: `TileGrid` instead of `Grid` (clashes with `ui.theme.Grid`); `rectPoint` = ray from centre, 0° = 12 o'clock clockwise)
**Files:** Create `ui/cards/Layouts.kt`; Test `LayoutsTest.kt`.
```kotlin
enum class MediaShape { TALL, COVER }
fun mediaShape(w: Float, h: Float): MediaShape = if (w / h <= 0.82f) MediaShape.TALL else MediaShape.COVER
enum class NotifTier { A, B, C, D, E, S }
fun notifTier(w: Float, h: Float): NotifTier = when { w < 150f || (w < 230f && h < 170f) -> NotifTier.A; w < 230f -> NotifTier.B
    h < 170f -> NotifTier.S; w < 300f -> NotifTier.C; w < 600f -> NotifTier.D; else -> NotifTier.E }
enum class WeatherShape { TINY, SHORT_WIDE, SMALL, WIDE, STACK }
fun weatherShape(w: Float, h: Float): WeatherShape = when { w < 150f || h < 130f -> WeatherShape.TINY; h < 176f -> WeatherShape.SHORT_WIDE
    minOf(w, h) < 176f -> WeatherShape.SMALL; w / h >= 1.3f -> WeatherShape.WIDE; else -> WeatherShape.STACK }
enum class CalendarShape { TINY, SHORT_WIDE, NARROW, WIDE_MONTH, MONTH }
fun calendarShape(w: Float, h: Float): CalendarShape = when { w < 150f || h < 130f -> CalendarShape.TINY; h < 240f -> CalendarShape.SHORT_WIDE
    w < 260f -> CalendarShape.NARROW; w / h >= 1.25f -> CalendarShape.WIDE_MONTH; else -> CalendarShape.MONTH }
data class Grid(val cols: Int, val rows: Int, val tileW: Float, val tileH: Float)
fun tileGrid(n: Int, w: Float, h: Float, gap: Float = 8f): Grid   // cols 2..6 maximising min(tileW, tileH)
fun rectPoint(w: Float, h: Float, deg: Float, inset: Float): Pair<Float, Float>
```
- [ ] Tests: `notifTier(338f,200f)==D`, `(172f,153f)==A`, `(362f,152f)==S`, `(695f,419f)==E`, `(200f,338f)==B`;
  `mediaShape(338f,419f)==TALL`, `(419f,338f)==COVER`; `tileGrid(6,338f,380f).cols==2`;
  `rectPoint(400f,200f,90f,10f)==(390f to 100f)`. FAIL → implement → PASS. Commit.

### Task 9: Data paths — done (as built: `WeatherInfo.days` = days after today (forecast_days=6, daily weather_code); `DeviceSources.upcoming` one query today..+14 days ≤ 8 with `AgendaItem.date`, `agenda` = today's first 5 of it; `notifGroups` in `data/Logic.kt` by package + title; inline sample instead of a saved file)
`Parsers.kt:49-74` + `State.kt:91-100` (weather `days` from Open-Meteo `daily`), `Sources.kt:141-160` (calendar today
+ 14 days, ≤ 8 → `state.upcoming`), `Logic.kt` (`NotifGroup` by app + conversation). Tests: saved Open-Meteo sample,
grouping. Commit.

### Tasks 10–16: Cards (each: spec §1 + mockup tab, tokens, delete that card's Paper/Lamp body, shots all themes,
`sheets.py`, `check_light.py`, commit)

| Task | Card | Notes |
|---|---|---|
| 10 | Media — done (as built: `mediaShape` picks the body; tall = ink text, ink play disc with a page-colour glyph, ink bar on ink3; cover overlay = `overPicture` white (candle ink at night) + scrim; new `StandbyActions.openNowPlaying()`, which `StandbyRoot` wraps (delegation) to set `npOpen` for board pages. Tall tap and the cover's second tap open Now Playing; the idle card still opens the app. `Bar` got a `track` arg. BoardShots gained `card-*-L-open[-night]` (tap via the new `shot(act=)`) and no-art cases. IPHONE board digit default left for Task 12) | port 50e5acb; card tap → `npOpen`; remove PaperMusic/LampMusic |
| 11 | Notifications — done (as built: one `NotifCard` in `NotifCards.kt` for NOTIF_LIST and NOTIF_STACK (old boards draw the merged card); CardView hands it the theme plate, worn only while closed, open plates are per-item `themedSurface`; closed row tap / tier A tap / overflow cluster open the card, an open plate tap opens the app (NotifGestureTest's tap case updated); swipe-to-clear kept on rows (whole group) and plates; letter plates = the app colour without the contrast floor (KakaoTalk stayed yellow on DAYPAPER), letter/badge ink = ink or bg by luminance; `monoIcons` themes draw rings with the app glyph on the card too; `IconView` bg defaults to `LocalInk.bg`; NOTIF_LIST label "알림"; no fade mask at the open list's bottom edge) | new `NotifCard` for `NOTIF_LIST` slots (tiers + open plates + 10 s idle); picker hides `NOTIF_STACK` (`BoardScreens.kt:353`); D9 in `Listener` |
| 12 | Clock + analog card — done (as built: `DigitalCard` = `ThemedDigits` (style = the user's DIGITAL override with the board accent, else theme digits; non-hero = ink) + `DateLine` (weekday accentText, ink on a second clock; words font, not theme numerals: PAPER serif read "II월"); `ThemedDigits` now takes only the height its digits need so the date sits right under them; `AnalogCard` + `Dial` drawn on Canvas from Ink (ink2/ink3 ticks, `e.num` numerals, ink hands, accent second hand via `rememberSecond`); wide also needs h ≥ 140 and tall w ≥ 140 (mockup); non-DIGITAL board faces keep `ClockFace`; "two clocks" = the existing `CardLook.hero` (largest clock-like slot); accent = `board.accent ?: styles[face]?.color ?: tokens.accent` in `looks()` and `CardLook.of`; RADIO digits fall to ink via `Contrast.safe` (#E8641C < 3:1); Paper/LampClock removed; BoardShots + Fold-inner L and Fold-cover-port M clock/analog cases) | one/two-line rule; analog card date placement; **two clocks on a board: larger keeps accent, smaller = ink** (`BoardView` hero rule) |
| 13 | Weather + calendar — done (as built: `weatherShape`/`calendarShape` pick the body; inset = 7 % of the short side 12–28 dp; hero numbers via `Txt` in `tokens.digitFont/digitWeight/digitCap` (no FLIP tiles / GLOW on them), the temperature degree is a drawn ring (digit fonts draw "°" from a dot to a cap-high "o"); STACK allows the hour row from 3 hours (iw ≥ 132) instead of the mockup's iw ≥ 200, temp cap ≤ min(180, 0.8 w); day rows = today + `WeatherInfo.days`, range bar blue→sun role gradient (ink2→ink on mono themes); `WxIcon` reads `tokens.monoIcons` instead of `colorIcons`; calendar today = accent disc (text ink or bg, whichever contrasts more), Sundays + weekday header in the red role, event bars/dots = `AgendaItem.color` lightness-stepped to 3:1 by the new `Contrast.fit` (extracted from `Roles.derive`) so pastels darken on light themes instead of falling back to ink; no permission (`agenda == null`) = "캘린더 권한 필요 · 눌러서 설정" in the events area, whole card taps to settings; Paper/Lamp weather + calendar bodies removed (LampCards.kt left tools/legacy.txt); BoardShots: Fold-inner L + portrait M for weather/calendar, `small-weather-calendar` (bar portrait BIG2: SMALL weather), `card-calendar-{M,Sw}-noperm`; fixture weather `days` + `upcoming`) | Task 9 data |
| 14 | Agenda + D-day — done (as built: `AgendaCard` = head "월요일 9 · 남은 일정 N" (weekday accentText, meta) + `EventRow` (shared with the calendar's `EventList`: `Contrast.fit` colour bar, title read 600, time meta) as many as fit, two columns (filled across, CSS-grid order) when w/h ≥ 1.6 or w ≥ 560 and ≥ 1.2, "외 N개" at the bottom; no permission = "캘린더 권한 필요 · 눌러서 설정" under the head, card taps to settings; empty = "남은 일정 없음"; no time ranges / places (`AgendaItem` has neither). `DdayCard` = name (read, accentText) · D-n via the calendar's `Hero` (digit font, no FLIP/GLOW; cap ≤ min(0.3 h, 130 dp)) in ink · date (short ≥ 150, words font) · accent bar 6 dp on ink3 + "시작 · N일 지남 · N일 남음" (short ≥ 176; "시작" dropped when the line does not fit); progress = a fixed 100-day window before the date (DDay has no start date, open question); left-aligned when w/h ≥ 1.6, else centred. Paper/LampAgenda + dispatch removed (Paper/LampCards.kt keep timer + usage); legacy.txt unchanged (Cards.kt still has memories/timer literals). Fixture agenda = the mockup's 5 events; BoardShots: agenda/dday Fold-inner L + portrait M, `card-agenda-M-noperm`, `card-agenda-Sw-empty`) | |
| 15 | Timer + memories — done (as built: `TimerCard` = `TimerRing` (accent arc on ink3, from 12 o'clock, elapsed fraction) + digits via the shared `Hero` (theme digit font, no FLIP/GLOW; ink, ink2 when paused); square/tall = digits + mode inside the ring, accent pause/play disc (glyph `onAcc`) 56 + ink4 stop disc 48 under it, `SpaceEvenly` instead of the mockup's space-between so tall slots do not open a hole; wide (w/h ≥ 1.35) = ring with the mode left, "집중 · 21:59에 휴식" (now + left; "집중 · 멈춤" when paused) + digits as wide as the column + 52 dp buttons right; short side < 150 **or a ring < 120 dp** (Flip FOUR) = mode, digits, one 48 dp toggle. `TimerState` has no session count → mode label only ("집중", not "집중 2/4"). `MemoriesCard` = photo fills the slot, scrim + `overPicture` "3년 전 오늘" (head) + place · date (meta/read) at the bottom; the "1 / 12" pill is left out: the state carries one photo, no count and no rotation (open question). Paper/LampTimer + dispatch removed (Paper/LampCards.kt now hold only usage, for Task 17); `Env.onAcc` shared with the calendar; Cards.kt keeps its legacy.txt line (`UsageRing` track = Task 17). BoardShots: the fixture timer in every card slot, `card-timer-*-paused` per size class, timer/memories Fold-inner L + portrait M, `card-memories-M-{nophoto,noperm}`) | memories white over photo |
| 16 | Sleep sounds — done (as built: `SleepCard` = six tiles from `tileGrid` (plate2 tile, ink3 disc + ink glyph from the new `ui/icons/SoundIcons.kt` (mockup SVG paths via `PathParser`), the playing sound on the accent with an `onAcc` glyph; names when a tile is ≥ 78 dp) + footer (48 dp ink play/pause disc · "빗소리 · 32분 뒤 꺼짐" (name when iw ≥ 320; "멈춤" when stopped) · minutes chip); `SleepOne` (short side < 150 **or tiles < 48 dp**) = "수면 소리 1/6", ◀ disc + name ▶, timer line. Tile tap = new `StandbyActions.sleepPick(sound, toggle=true)` (plays it; the playing one stops), arrows = `sleepPick(…, false)` (select; switches a playing sound); chip = new `sleepMinutesNext()` cycling the new shared `SLEEP_MINUTES` (also the settings chips). `SleepService` now swaps sound/timer in the running loop on a second start (no stop/start race). `SleepSound.FIRE` after WAVES = brown rumble + hiss + white-noise crackles in `NoiseGen`; codec stores names (unknown → default, unchanged); `LogicTest.fireCracklesOverASmoothRumble`. Old wave body removed; there were no Paper/Lamp sleep bodies left. BoardShots: sleep L/M extra slots, `card-sleep-*-playing` per size class + Fold-inner L, `card-sleep-M-playing-night`) | `SleepSound.FIRE` synthesised (D10) + `when` updates; `ui/icons/SoundIcons.kt` |

### Task 17: Usage card + flavors (high risk: secrets) — done (as built: flavours `personal` (id kr.dot.standby, the three secret `buildConfigField`s) / `public` (`.pub`, no secret field); `UsageFeed` interface lives in `ui/state/State.kt` next to `UsageInfo`; each flavour has `data.UsageFeature` (`enabled`, `feed(ctx)`) and `ui.cards.UsageCard` (public = empty stub), so `CardView` keeps one dispatch; `Feeds.get` internal, `usageText` → `src/personal` `UsageFeeds`; `UsageParser` there also reads `pace`, the four session dots and the content/trading bots (`UsageWindow.pace`, `UsageInfo.sessions/bots`, new `UsageSession`); the card shows only what the feed has: no per-session task line or "N분" age (status.json has neither) → rows = state + context %, two-line rows add cache minutes; stale/offline = ink2 rings (the old caption is gone; the tall footer says "연결 안 됨"); wide rings take the width the session column does not need (mockup: half) so big slots fill; one-line rows drop the context share when it would not fit. Public preset 작업 책상 = CLOCK, TIMER, TIMER (plan text; two timers, open question); picker + the settings 사용량 section hidden when disabled. Paper/LampCards.kt deleted (`lampPlate` + `italicSpans` moved into ThemeOverlays.kt as private), Cards.kt left tools/legacy.txt; `check_literals.py` now also scans src/personal + src/public. Fixture `src/testPersonal/resources/status.json` = a live ai-hub snapshot trimmed to v/at/sessions/limits/codex_runs/bots/work/alerts (trading/content/pc dropped, work steps blanked); `tools/check_fixture.py` (+ inline self-test) and `tools/check_secrets.py` (builds both debug APKs; public 0 hits, personal 2 entries per key). Usage shots run from the shared BoardShots (personal suite draws the real body; public draws the stub): + `card-usage-L` Fold-inner, `-M` portrait, `card-usage-tall` bar portrait. Independent review: arranged by the lead session)
**Files:** `standby/build.gradle.kts`, `data/Feeds.kt` (split), `data/Parsers.kt` (split weather/usage),
`data/StandbyStateHolder.kt:348-371`, `ui/state/State.kt`, `ui/cards/Cards.kt` + family files' usage bodies,
`settings/Preview.kt`, `model/Config.kt` presets, `test/.../LogicTest.kt` (usage tests → `src/testPersonal`).
- [ ] `build.gradle.kts`: `flavorDimensions("dist")`, `personal`, `public`; `STATUS_BOT_TOKEN`/`STATUS_CHAT_ID`/
  `LAN_TOKEN` `buildConfigField`s only in `personal`; `public` gets `applicationIdSuffix = ".pub"` so both can sit
  on one phone (personal keeps `kr.dot.standby` and the operator's stored config).
- [ ] Main keeps `Feeds` minus `usageText` (weather + `onWifi` stay), `UsageInfo`, `interface UsageFeed { suspend fun
  fetch(): UsageInfo? }`, `UsageFeature.enabled`; the holder calls `UsageFeed` only.
  `Feeds.get` becomes `internal` for the personal source set. `src/personal/java` holds `UsageFeeds.usageText`, `UsageParser` (+ `dots`, `bots`), the real feed and the card body;
  `src/public/java` returns null / `enabled = false`. Usage tests move to `src/testPersonal`. Presets: "작업 책상"
  uses `TIMER` instead of `USAGE` in `public`. Pickers hide `USAGE` when disabled.
- [ ] Fixture `status.json`: copied from ai-hub output, then `tools/check_fixture.py` fails on any token-shaped or
  chat-id-shaped string.
- [ ] Gate `tools/check_secrets.py`: unzip the public APK, scan every `classes*.dex` + resources for each value read
  from `secrets.properties` → must find 0; same scan on the personal APK must find them (proves the scan works).
- [ ] Independent review of this task's diff (the side that did not implement it). Commit.

### Task 18: Phone widget card — done (as built: `CardType.WIDGET` ("폰 위젯", boards only: the card picker offers it only from the board editor); `platform/WidgetHost.kt` = one AppWidgetHost, ref-counted `start/stop` from StandbyActivity onStart/onStop and StandbyDream onDreamingStarted/Stopped, one cached `WidgetView` per id (re-created only when the dark/light mode changes), `delete` releases the id; `WidgetView` samples a 16×16 software draw after each `updateAppWidget` / size change (a widget with hardware bitmaps keeps the last answer); `ui/cards/PhoneWidget.kt` hosts it only under `LocalLiveWidgets` (provided by `HostContent`), else draws a plate2 placeholder ("길게 눌러 위젯 고르기" / "폰 위젯"); inset 4 % of the short side 6–14 dp (mockup), opacity, radius clip, `hideAtNight`, `updateAppWidgetSize` with the inset slot; WidgetMode = uiMode on a configuration context + `setOnLightBackground` (best effort); night = D13 colour matrices on a hardware layer at `night ≥ 0.5` (candle scale = #B9A48C). The long-press "menu" is a settings page `widget:<board>:<slot>:<idx>` (`WidgetOptionsScreen`: 위젯 고르기/바꾸기, 위젯 설정 = configure activity, 투명도, 다크·라이트, 모서리 0–40, 밤에 숨기기), opened by a long press on the card (consumes the down so the board long press does not also fire) or the "설정" button on the editor's stack row. Bind: provider list in an AlertDialog → `bindAppWidgetIdIfAllowed` else ACTION_APPWIDGET_BIND → `startAppWidgetConfigureActivityForResult`; cancel deletes the id; picking the card in the editor starts the bind. SettingsActivity's `update` releases `BoardOps.removedWidgetIds(old, new)`. Editor `editSlot` now maps real moves/removals (options follow their card). Manifest `<queries>` for APPWIDGET_UPDATE. Shots: `card-widget-*` per size class + night (automatic) and `card-widget-M-bound`; BoardOps unchanged so no new unit test)
`platform/WidgetHost.kt` (singleton, ref-counted), `ui/cards/PhoneWidget.kt` (AndroidView, size options, opacity,
`WidgetMode` best effort, night per D13 or hidden), `SettingsActivity` bind/configure flow (D6), long-press menu
"위젯 설정 / 투명도 / 다크·라이트 / 모서리 / 밤에 숨기기", `BoardOps` cleanup. Unit tests for options re-keying are
in Task 2; shot with a fake RemoteViews. Commit.

### Task 19a–e: Full-screen surfaces (each removes its Paper/Lamp overlay) — a–d done (as built: one body per surface in `Overlays.kt` on `ThemeBackground`; ThemeOverlays.kt deleted, Overlays.kt left tools/legacy.txt; alarm + live take the **theme** accent (`Ink.of(night, null, theme)`) instead of the root ink's board-clock colour (the r8 blue made a blue sunrise), glows are static radial gradients off at night; new `Ink.on(c)` / `Ink.onAcc` (Env.onAcc delegates). **a** alarm: "⏰ 알람 · 월요일" (label when set), `ThemedDigits` in ink, weather + `agenda.size` events, pills accent/onAcc + ink 12 %; no "· 9분" (snooze length not in `AlarmRinging`). **b** live: header = app + detail (glyph disc, no app colour/icon in `LiveActivity`), kicker = title in accentText, value split into digit-font number + small unit, timeline 주문 → 조리 → 픽업 → 도착 lit by `progress × steps` (other step counts "n단계"; hidden when progress is null); no step times, store, ETA clock or rider row (not carried). LivePill = bg + `themedSurface`. **c** Now Playing = `CardView(MUSIC, close = …)`: no plate, tall body gets a ✕ row, cover keeps tap-to-reveal and puts ✕ in its title row (second tap hides instead of reopening); old ✕/volume glyph and album-colour wash gone (volume → Task 20 panel). **d** `NotifBanner`: Layer.NOTIFICATION removed (`topLayer(state, np)`), banner over any page/layer except the alarm, bg + `themedSurface`, 15/13 dp × text size, swipe up > 24 dp or 5 s dismisses; `StandbyActions.openNotifCard(key)` (default = notificationTap) overridden in StandbyRoot: `notifPage(config, current)` → dismiss banner, close NP/live/edit, scroll the pager, set `LocalNotifOpen`; `SlotStack` scrolls to its notif card and `NotifCard` opens and clears it. `OverlayType` + NOTIF_* motion constants deleted. ScreenShots: `np-open` (tap reveals cover controls), `alarm-night` portrait; LayerTest rewritten + `notifPage` case)
- **a Alarm** r9; **b Live** r9; **c Now Playing** = media tall/cover full screen; **d Banner** `NotifBanner` +
  root action `openNotifCard(key)` (switch to the board page with a `NOTIF_LIST` slot and open expanded, else
  `notificationTap`); **e Analog styles** RECT/BIG4/MINIMAL/DUO via `rectPoint`, clock editor picker, `Look.of`
  falls back to `tokens.ink` under 3:1, hand outline from tokens. Shots all themes day + IPHONE night. Commit each.

### Task 20: Pull-down panel + settings
`BrightnessBar.kt` → `TopPanel.kt` (brightness, `STREAM_MUSIC` volume, theme chips = existing `ThemePicker`
`PreviewFrame` thumbnails in a horizontal scroll, widget cells as placeholders); `HomeScreen.kt:86-107` horizontal
scroll, no `th.ko`; rows `BoardBg` (+ colour picker), `TextSize`, analog style. Commit.

### Task 21: Behaviour tests (compose test clock)
`BannerTest` (5 s auto-hide, swipe up dismiss, tap → `openNotifCard`), `NotifCardTest` (open on row tap, 10 s idle
close, `접기`), `MediaCoverTest` (tap reveals 3 s, button tap restarts, second tap → Now Playing), `TopPanelTest`
(4 s hide, pull-and-slide brightness — extends `BrightnessSwipeTest`), `TouchTargetTest` (every clickable ≥ 48 dp on
FOLD_COVER_L and FLIP boards). Commit.

### Task 22: Full matrix + family leftovers
- [ ] Full shots, all themes, night set, LARGE set; `sheets.py`, `check_light.py`, `check_literals.py`; fix until
  all scripts exit 0 and every sheet has no clipping/overlap/holes (operator-visible criteria: no text cut, no
  element outside its slot, no slot more than 25 % empty in a non-tiny size class).
- [ ] Delete `Family`, `Surface`, `Palette`, leftover family files (`tools/legacy.txt` empty); rename `NOTIF_LIST` →
  `NOTIF`, remove `NOTIF_STACK`/`VIDEO` enum values; aliases now also map `"NOTIF_LIST" → NOTIF` (r9 dev builds stored
  it); **re-run `ConfigCodecTest` alias + dedupe tests with JSON string inputs** against the renamed enum; Roborazzi compare vs the shots just recorded → 0 diff. Commit.

### Task 23: Review, release candidate, wrap-up
- [ ] Independent review of the whole diff (reviewer by quota); fix Critical/High.
- [ ] versionName 0.3.0; `testPersonalDebugUnitTest` + `testPublicDebugUnitTest` + `check_secrets.py`; personal APK
  zipped if > 30 MB → SendUserFile; device checklist: widget bind/config/dark-light, unplug/relock/side-key, fire
  sound, banner → card, text sizes, candle night, each theme once on the Fold cover.
- [ ] Wrap-up per `C:\DC\DD\CLAUDE.md`: ROADMAP Recent, `tasks/T-0162` attempts, `docs/decisions` (D1, D4, D10),
  `references/techniques.md` (mockup-first decision page), `standby/LICENSES.md` (Plex Mono OFL).

## Self-review
- Spec §1 → 9–18, 21; §2 → 2, 5, 20; §3 → 4, 6, 19; §4 → 2–7, 22. Two-clock accent → 12. Desk face → 6.
- Round-4: no High. Medium folded: `Contrast.safe` wired in `Ink.accent/mapped` (Task 4), FloatFace colon, gradient
  `against` rule, new themes copy IPHONE's non-token fields, `copy(theme` gate.
- Round-3: High — one theme-change entry point (`withTheme`). Medium — `Contrast.safe` for colours outside `Look.of`,
  background reset, `capOf(THEME)`, clockDefaults switch moved to Task 5, D9 wired. Low — `NOTIF_LIST` alias, picker,
  accent/plate floor, D7 order, temporary `Themes.of` mapping, `Feeds.get` visibility.
- Round-2 Highs: reset path + colour reset on theme change + contrast fallback (D4), `FontId.THEME` (D4b), night
  ink2 .58 + gradient stops (Task 3), `Feeds` split (Task 17). Mediums: `editStack`, tokens-only Task 3, test
  rewrites, D7 scope, clock background meaning, `.pub` suffix.
- Round-1 Highs: style override (D4, Task 2 test), usage stubs/test sourceset/presets (17), widget keys/host (D6,
  Task 2/18), token values (table), candle values + step rule (table, Task 4), r8 default frozen copy (D4),
  lint vs settings (constraints: settings excluded, literal gate is code-level), secret scan + delegated diff
  (17), migration re-test (22).
