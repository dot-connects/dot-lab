# Stand Screen r10 — settings redesign, 21 clocks, night/heat features: Implementation Plan (v6, review closed)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship every operator decision in `settings-r10/consensus.md` as 0.4.0 "Stand Screen": reorganised settings,
a clock gallery of 21 clocks with per-clock 꾸미기, heat protection / mood-light auto-off / quiet night, the cleanup
items, the new name and icon.

**Architecture:** Clocks become one registry (`ClockSpec`, built by an exhaustive `when` over `FaceId`) that the
clocks page, board slots and settings read. Several clocks are presets of one renderer (thin = digital, colour block =
split, wall = analog, LCD = LED). The clocks page cycles `AppConfig.myClocks`. Behaviour features are pure functions
in `data/Logic.kt` (unit-tested) wired through `StandbyStateHolder`. Settings are rebuilt on `settings/Kit.kt` with one
shared collapsing preview.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric + Roborazzi, JDK 17, Gradle `:standby`.

**Spec:** `settings-r10/consensus.md` (decision log; later lines win) + mockups (1 CSS px = 1 dp; look and copy):
`settings-r10/mockup.html` (settings; readable source `settings-r10/src/mockup.src.html`: `SCREENS.*`, `STYLE`,
`PART`, `COMBO2/3`, `LEDS`, `KFONTS`, `PERMS`), `features.html` (src `features.src.html`), `cleanup.html` (src
`cleanup.src.html`), `clocks.html` (15 new clocks, renderers + CSS inline), icon `icon/final/`.
Reviews: `app-review-fable.md`, `competitors.md`. Plan review log (5 rounds, High 13·3·2·1·0): `C:\DC\DD\docs\archive\2026-11-12-standby-r10-plan-review\`.

## Global Constraints

- Paths: app `C:\DC\DD\dot-lab\works\2026-11-09-standby\app`; src `standby/src/main/java/kr/dot/sb/`; tests
  `standby/src/test/java/kr/dot/sb/`.
- Commands: `$env:JAVA_HOME='C:\DC\tools\jdk-17'; .\gradlew.bat :standby:testPersonalDebugUnitTest -q`; public suite
  `:standby:testPublicDebugUnitTest` (plain, never `verify`: baselines are personal-flavour). Shots `-Proborazzi.test.record=true`
  / `-Proborazzi.test.verify=true`, filters `-Pshot.themes=… -Pshot.group=clock|board|settings|screens`.
- **Build green after every task** (both flavours compile, unit tests pass). A task fixes every compile error its
  enum/model change causes (`rg` the old names before commit).
- Gates every task: `tools/check_literals.py`, `check_light.py`, `check_fixture.py`; Task 13 adds `check_secrets.py`.
- No user-visible "아이폰"/"iPhone" (Task 1 gate). Enum names stay (stored values).
- Stored config: every removed value has an alias in `ConfigCodec`/`Migrations` with a codec test. `VERSION` 9 → 10;
  the r8 style migration stays keyed on **`v < 9`** (not `< VERSION`).
- Night = candle ink #B9A48C on black for every surface incl. every new clock; effects off.
- Touch targets ≥ 48 dp; text roles 15 / 18 / 13 dp × text size.
- **Clock rendering contract:** one renderer per face sized only by its box; one compact fallback (digits only, same
  colours) when `min(w, h) < 120 dp` or the face's own minimum (stated per face below) is not met. No per-device
  branches. Burn-in: full-screen faces keep `≥ 10 dp` free on every edge so the ±8 dp shift never clips.
- **Shot budget for new faces:** all ten themes on FOLD_COVER_L day; IPHONE theme on all six devices day + night;
  slot classes L/M/St/Sw/XSt on IPHONE only. `sheets.py` regenerated; check per face.
- Fonts: bundled only, OFL, downloaded from Google Fonts into `res/font`, each added to the license list (AboutScreen)
  and to `FontId` (stored name = enum name). Default font per new face (as the mockup): 가는 선 Outfit (100), 시·분 나눔
  Big Shoulders Display (900), 색 블록 Bagel Fat One, 스티커 Titan One, 공 숫자 Fredoka (700), 플립 Oswald (600),
  벽시계 numerals Jost (500), 달력과 시계 Playfair Display (800), 하루 막대 IBM Plex Mono (bundled), 테두리 초 Barlow
  Condensed (600), LED/액정/전광판 none (drawn). Hangul fonts (`kfont`): 명조 Gowun Batang, 고딕 Pretendard (bundled),
  둥근 고딕 Jua, 손글씨 Nanum Pen Script. `FontId` gains `hangul: Boolean`; the font picker offers digit fonts to digit
  faces and only Hangul fonts to 한글 시계 (stored in the same `ClockStyle.font`). The per-face default font + weight
  live in `FaceDefaults` next to the colours and override `Themes.defaults`' THEME font for the new faces.
- Tests only where wrong is expensive: codec/migrations, pure decisions (heat hysteresis, night/notification wake,
  light-theme night, burn-in path, `myClocks` edit ops, number → Hangul words, "N분" parse, sunrise parse). No
  constant/registry re-check tests; registry exhaustiveness is enforced by the compiler (`when`).
- Delegation: Claude implements; Codex (`codex-run.ps1`, background) takes tasks marked **[Codex ok]** when Codex
  `seven_day` < 80 % (and by default if Claude `seven_day` ≥ 85 %). Review input = `git diff` of that task; the
  reviewer is the side that did not implement; a delegating side does not re-run the other side's tests (checks
  output, exit code, scope). Commit with `git commit --only <paths>` (shared index).
- Out of scope (operator, 2026-11-12: "store launch later"): Play Billing, 7-day trial + server record, store
  listing assets (Play 512/1024 PNG exist in `icon/final/` and are used then), release signing key, R8, closed test.

## Decisions fixed by this plan (from review)

- **Upgrade keeps the user's clocks:** a save without `myClocks` (≤ v9) gets the old five `[DIGITAL, ANALOG, WORLD,
  SOLAR, FLOAT]`; a fresh install gets `[DIGITAL, FLIP, HANGUL, ANALOG]` (mockup default). Ids whose renderer is not
  built yet are skipped by the clocks page until their task lands (no duplicate digital pages).
- The clocks page starts on `myClocks[0]`; `RootStart.face` becomes `FaceId? = null` (null = `myClocks[0]`; shot fixtures set it and `myClocks`). An
  empty built list falls back to `[DIGITAL]`. Board slots keep `Board.clockFace`; the board editor's clock chips list
  every built face from the registry (not the hard-coded six in `BoardScreens.kt`).
- **New-face colours:** faces with their own look (플립, LED, 액정, 전광판, 벽시계, 색 블록, 스티커, 공 숫자, 달력 page,
  하루 막대 bar, 테두리 dots) take fixed per-face defaults from the mockup `PART`/`COMBO2/3`/`LEDS` tables
  (`ui/clock/FaceDefaults.kt`, read at render time; not theme-derived, `withTheme` leaves them). Their digits/text
  colour (`color`) and every other face keep coming from `Themes.defaults` as today. Overrides only when edited.
- **Clocks-page long press:** the colour row of the sheet edits the face's first colour row (`spec.rows` first colour
  key); a preset row (combo2/combo3/ledC/solar) shows its preset swatches instead.
- **FLIP:** `FaceId.FLIP` is the flip clock. The FLIP *theme* draws its full-screen DIGITAL face through the same flip
  renderer by day (cleanup.html B1) — one renderer, built in Task 7; Task 4 does not touch it.
- **Quiet night field:** `nightNotifQuiet: Boolean = true` (ON = a notification never wakes a screen that is off at
  night; the alarm always does). UI row "밤에 알림이 와도 꺼진 화면 켜지 않기" binds directly (no inversion). Enforced in
  `StandbyStateHolder.showNotification`: when night && quiet && the display is off it **returns before the notifJob**
  — no `state.notification`, no `machine.touch()`, no `wasOffAtNotif` (the notification card still updates through
  `ListenerBus.shade`; no banner).
- **Heat:** dim = window brightness `min(current, 0.35)` while hot (an explicit value also when the user brightness is
  AUTO); hot from ≥ 42.0 °C, cool at ≤ 40.0 °C. Only the applied window value is capped — `dayBrightness` /
  `nightMaxBrightness` are stored as the user set them. GlowActivity applies the same rule with its own receiver.
- **Burn-in:** ±8 dp (16 dp travel), one 1 dp step per minute along a ping-pong snake over a 17 × 17 grid, step =
  minutes since epoch (no jump at midnight). The shift moves the content layer only: `ThemeBackground` and full-bleed
  face backgrounds (솔라, 색 블록, 사진 페이지), the Compose `MoodLight` and the clocks page background are **overscanned by
  8 dp** on every side (one modifier, `Modifier.burnInBleed()`), so no moving band. Pagers clip their bounds, so the bleed is applied to the
  pagers themselves (outer size + 16 dp, offset back by −8 dp) rather than inside a page. `ClocksPage` keeps its own `ink.bg`
  (edit mode needs it); the candle theme's bottom light is drawn there too (same layer as `ThemeBackground`'s).
- **Light themes force night:** when the *drawn* theme (`drawnTheme()`) is light, night applies even when the stored
  mode is OFF or display-off is NEVER; with mode OFF the AUTO (lux) rule decides. Stored values untouched.
- **Mood light auto-off** for the unfold mood light in **both** places: the Compose `MoodLight` in `StandbyRoot`
  (activity host) and `GlowActivity` launched from the dream (`source = UNFOLD`). `GlowReceiver.post(ctx, source)` with
  `source ∈ UNFOLD|TEST|PRE_ALARM|ALARM`, one PendingIntent request code per source + `FLAG_UPDATE_CURRENT`, debounce
  per source; `openGlow` and the settings test pass their source. A ringing alarm cancels the timer (GlowActivity also handles
  `onNewIntent`: a PRE_ALARM/ALARM source cancels the UNFOLD timer); fade = last 60 s; a tap during the fade restarts
  the timer, otherwise a tap closes (as now). One shared `MoodTimer` rule. In the activity host `StandbyRoot` reports the
  mood light through new `StandbyActions.moodShowing(Boolean)` and `moodDone()` (DONE also sets `moodOff = true`;
  **sleep wins over mood**); the holder passes `mood` into
  `NightRules.displayOff(…, mood)` (mood → keep on) and at DONE calls a new `DisplayOffMachine.sleep()`: a **sticky
  off** that every mode respects (NEVER and the AUTO lux wake included) and only `touch()` clears (tested). A touch after
  DONE wakes to the board (the mood light counts as dismissed, like `moodOff`). `GlowActivity` at DONE fades to black
  and finishes, and neither path shows another UNFOLD mood light until the phone is folded again: one process-level
  `MoodLatch` (set at DONE, reset when any host sees `unfolded = false` or on the next `ACTION_POWER_CONNECTED`).
  `endNotification` restores `sleep()` (not `forceOff()`) when the display was sleeping before the notification.
- **Default D-day:** fresh installs none, and the "수능" preset board leaves the fresh defaults too; a stored entry
  exactly ("수능", 2026-11-19) is removed by the v10 migration (a stored 수능 board stays).
- **Calendars:** the picker lists every calendar in the provider (visible or not), grouped by account; excluded set is
  stored as `"<accountType>|<accountName>|<calendarName>"` keys (ids get reused); empty set = no SQL clause.
- **Permission count** for the home banner = the eight `PERMS` of the mockup, always; inline lines show only where the
  feature that needs them is on.
- Notification banner may cover the clock digits — not changed.

## Tasks

### Task 0: Inputs + start marker
- [x] Mockup sources in `settings-r10/src/`; repo tagged `r10-start` (local). The 0.3.1 shot baseline is
  `app/standby/build/shots` (745 MB, not in git): never run `clean` during r10. Commit plan + sources.

### Task 1: Name, icon, version, wording gate
**Files:** `standby/build.gradle.kts`, `src/main/res/values/strings.xml`, `src/main/AndroidManifest.xml`,
new `src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (`<adaptive-icon>` background `@color/ic_launcher_background`,
foreground `@drawable/ic_launcher_foreground`, monochrome `@drawable/ic_launcher_monochrome`), drawables copied from
`icon/final/*.xml`, `values/colors.xml` (#F4EEE3), `model/Config.kt`, `settings/BoardScreens.kt`, `tools/check_literals.py`.
- [ ] versionName 0.4.0 / versionCode 13; label "Stand Screen"; `android:icon`/`roundIcon` = `@mipmap/ic_launcher`.
- [ ] Labels: `Theme.IPHONE`, `ClockLayout.IPHONE`, `AnalogTicks.IPHONE` → "기본"; drop "아이폰처럼" in BoardScreens;
  `rg '"[^"]*(아이폰|iPhone)' src/` must return nothing.
- [ ] `check_literals.py`: tokenise Kotlin string literals (respect escapes and `//` inside strings) and fail on
  "아이폰"/"iPhone"; self-test case for `"a // 아이폰"` (literal → fail) and `// 아이폰` (comment → pass).
- [ ] Done: `assembleDebug` both flavours, merged manifest shows the label/icon, gates pass.

### Task 2: Config r10 + migrations (TDD)
**Files:** `model/Config.kt`, `settings/ConfigCodec.kt`, `settings/Migrations.kt`, `data/Sleep.kt`,
`ui/icons/SoundIcons.kt`, `ui/clock/TimeText.kt`, `ui/clock/FloatFace.kt`, every other file the compiler flags,
tests `settings/ConfigCodecTest.kt` (+ shot tests that name removed values).
- [ ] Failing tests first: (a) v9 save with `effect` RAINBOW/GLASS → SOLID (explicit alias); (b) `numerals` ≠ LATN →
  LATN; (c) `sleepSound` PINK/BROWN → WHITE; (d) stored `widthPct` → face default; (e) default boards have no "video",
  a stored "video" board survives; (f) new fields round-trip; (g) v9 save without `myClocks` → old five, fresh →
  `[DIGITAL, FLIP, HANGUL, ANALOG]`; (h) unknown ids in `myClocks` dropped, empty → default; (i) v9 save with an r8-look
  digital edit and an analog Duo edit survives a v10 save/load (r8 path keyed on `v < 9`); (j) v9 save with the
  ("수능", 2026-11-19) D-day loses exactly that entry.
- [ ] Model: `FaceId` + `THIN, SPLIT, COLOR_BLOCK, STICKER, BALLS, FLIP, LED, LCD, DOT_MATRIX, WALL, CALENDAR,
  HANGUL, DAY_BAR, RIM` (`ClockStyle.default`/`Migrations.r8Default` get entries; new ids map to the DIGITAL default
  for now). `FaceId.CLOCKS` stays until Task 5. `AppConfig`: `myClocks`, `excludedCalendars: Set<String>`,
  `heatProtect = true`, `moodAutoOffMin = 0` (0/3/10/15), `nightNotifQuiet = true`, `photoIntervalSec = 15`
  (10/15/30/60/300), `ddays` default empty, no 수능 preset board. `Effect` = SOLID, TWO_COLOR, GLOW. `Numerals` = LATN
  only (UI gone). `SleepSound` = RAIN, WAVES, FIRE, WHITE ("백색소음", generator = the old BROWN). Video preset gone.
  `VERSION = 10`.
- [ ] Fix all compile errors (exhaustive `when`s, removed enum values in UI/tests). Both suites + gates. Commit.

### Task 3: Behaviour features (TDD on the pure parts)
**Files:** `data/Logic.kt`, `platform/Brightness.kt`, `data/StandbyStateHolder.kt`, `platform/Glow.kt`,
`ui/screens/StandbyRoot.kt`, `ui/screens/ClocksPage.kt`, `platform/StandbyDream.kt`, `platform/Host.kt`,
`platform/Listener.kt`, `data/Sources.kt`, `ui/screens/PhotosPage.kt`, `ui/state/State.kt`, `ui/screens/Overlays.kt`
(heat pill), tests `platform/LogicTest.kt`, `platform/ChargeBrightnessTest.kt`.
- [ ] `HeatRule.next(tenths, hot)` (≥ 420 hot, ≤ 400 cool; tests 419/420/401/400); the ChargeStart log line also records
  the battery temperature so the operator's device can confirm 42 °C is not reached every night. Holder registers its own
  `BATTERY_CHANGED` receiver (start/stop with the holder) reading `EXTRA_TEMPERATURE`; `Brightness.target(off, night,
  c, hot)` returns `min(base, 0.35)` (AUTO → 0.35) when `c.heatProtect && hot`; `setUserBrightness` passes through the
  same cap; re-apply on every heat change. `StandbyState.hot: Boolean`; the pill (features.html §2 A: "뜨거워서 화면을
  어둡게 했어요" 5 s, then the thermometer glyph) is a composable with its own `LaunchedEffect` timer.
- [ ] Quiet night per Decisions: `NightRules.wakeOnNotification(night, quiet, displayOff): Boolean` (pure, tested) used
  by `showNotification` before `machine.touch()`; alarm path unchanged.
- [ ] `NightRules.on(...)` gets `forceNight: Boolean` = drawn theme is light; per Decisions (mode OFF → the AUTO lux
  rule decides; display NEVER does not disable night). `schedulePoll` polls the hold for forced AUTO too. Tests
  (light + OFF dark room → night, light + OFF bright room → day, light + NEVER, dark + OFF → never night).
- [ ] `BurnIn.at(epochMinute)` per Decisions; tests: max |x|,|y| = 8, neighbouring minutes differ ≤ 1 dp, no jump
  across 23:59 → 0:00.
- [ ] Mood auto-off per Decisions: `MoodTimer` (pure: phase at elapsed ms → ON / FADE(alpha) / DONE, tested) used by the
  Compose `MoodLight` and `GlowActivity`; `GlowReceiver.post(ctx, source)` changes per Decisions.
- [ ] Photos: the crossfade and Ken Burns tween use `config.photoIntervalSec`; the holder reloads a new set every
  `max(45 s, interval × set size)` instead of a fixed 45 s, re-reading the interval every loop.
- [ ] `DeviceSources.calendars(ctx)` (all calendars) and agenda/upcoming filtered by the key set (clause only when
  non-empty).
- [ ] Live: `LiveActivity.stepTimes: List<Long?> = emptyList()`, `etaAt: Long? = null`; `Listener` keeps the previous
  activity per key and copies its times, stamping the first time each progress step is seen; `etaAt = now + N min`
  when the value matches `^(\d{1,3})\s*분`. Test the parse.
- [ ] Both suites + gates. Commit.

### Task 4: Screen fixes (cleanup.html B2–B5, features.html §5 A, candle, bar-land)
**Files:** `ui/clock/DigitalFaces.kt`, `ui/cards/Layouts.kt`, `ui/cards/Cards.kt`, `ui/screens/Overlays.kt`,
`ui/screens/TopPanel.kt`, `ui/clock/WorldFace.kt`, `ui/clock/AnalogFace.kt`, `ui/theme/Theme.kt|ThemeTokens.kt`
(candle background), shots.
- [ ] B4: digital 기본 layout fits `x0 + digits + gap + info ≤ w − margin − 10 dp`, ×1.03 only for CONDENSED.
- [ ] B2: `weatherShape` < 300 dp wide → big-temperature shape (new `WeatherShape.BIG_TEMP`); AgendaCard < 220 dp
  tall → one event large + "다음 HH:MM 제목 · 외 n개".
- [ ] B3 live screen layout (cleanup.html), using Task 3 times. B5 panel previews `night = 0f`, alpha 0.85 at night.
- [ ] Alarm 중단 (features.html §5 A): 2 dp outline ink 75 % + fill ink 16 %, same size/place.
- [ ] Candle theme: one bottom warm-light layer in its `ThemeBackground` on every page, matching today's day widgets
  page strength.
- [ ] Bar-land: world time ≥ 35 % of height; analog ROUND date block on the right when width/height ≥ 1.8; desk
  digits ≥ 60 % of height.
- [ ] Shots for touched surfaces (budget rule), sheets checked. Commit.

### Task 5: Clock registry, `myClocks` page, edit ops (TDD on ops)
**Files:** new `ui/clock/ClockRegistry.kt`, `ui/clock/ClockFaces.kt`, `ui/screens/ClocksPage.kt`, `ui/screens/StandbyRoot.kt`
(`RootStart`/`clockPage`), `settings/ClockScreens.kt` (compile only), new `settings/MyClocks.kt`, tests `settings/MyClocksTest.kt`.
- [ ] `ClockSpec(id, family, ko, ours, rows: List<StyleRow>, more: List<StyleRow>, built: Boolean)` from an exhaustive
  `fun spec(id: FaceId): ClockSpec = when (id) {…}`. Families (consensus): SIMPLE 디지털·가는 선·시·분 나눔·책상;
  POP 색 블록·스티커·공 숫자·솔라·둥근 굵은 글씨(FLOAT); RETRO 플립·빨간 LED·액정·전광판; CLASSIC 벽시계·달력과 시계·
  한글 시계·아날로그·사진 위 시간; GAUGE 하루 막대·테두리 초·세계. `ours` = 한글, 달력과 시계, 스티커, 공 숫자,
  테두리 초. Rows/more = the mockup `STYLE` table verbatim (keys → `StyleRow` 1:1: color→TEXT_COLOR, hourC, minC,
  cityC, litC, plateC, digitC, panelC, inkC, dotC, handC, secC, faceC, tickC, numC, dayC, calC, accC, barC, rimC,
  paperC, ledC, combo2, combo3, solar, size, font, kfont, info, weight, fx, bg, lay, shape, anum, atick, dialfont;
  `DT` = weight, fx, bg).
- [ ] `MyClocks.add/remove/move` pure ops: no duplicates, cannot remove the last, move clamps. Tests.
- [ ] ClocksPage pages `myClocks.filter { spec(it).built }` (empty → `[DIGITAL]`); page state stored as FaceId, index
  clamped when the list shrinks while showing; `RootStart.face: FaceId`; long-press sheet per Decisions; board editor
  clock chips from the registry. `FaceId.CLOCKS` deleted.
- [ ] New `ClockStyle` colour fields needed by the PART table that have no existing field (`hourColor`, `minuteColor`, `accentColor` (한글 강조),
  `plateColor`, `digitColor`, `panelColor`, `inkColor`, `dotColor`, `faceColor`, `calendarColor`, `barColor`,
  `rimColor`, `paperColor`, `ledColor`, `pairPreset`, `trioPreset`), nullable in `ClockStyle` (null = `FaceDefaults`);
  codec round-trip test (null fields omitted and restored). PART `cityC` = the world face's existing `color`, `litC` =
  `litDotColor`. Commit.

### Tasks 6–9: New clocks
Each face: port geometry/ratios from `clocks.html` (renderer functions + CSS), Compose Canvas/Text in
`ui/clock/<Family>Faces.kt`, box-sized, compact fallback per the contract, night ink, `showInfo` where its rows have
`info`, set `built = true`, add to ClockShots per the shot budget, run `check_light`, check its sheet.
- [ ] **Task 6 심플/팝:** 디지털 = clocks.html "꽉 찬 숫자" (info strip on the left), 가는 선 = digital preset (weight
  100, centre layout); 시·분 나눔 (`split`; tall boxes stack hour over minute; min 160 × 120 dp); 색 블록 = split +
  `COMBO2` pair background; 스티커 (`COMBO3` per digit, 12 dp outline + 10 dp offset shadow at 268 dp digits, scale
  with size, rotations −5°/0/+4°/−3°); 공 숫자 (circle per digit, `COMBO3`, min digit circle 48 dp else compact).
- [ ] **Task 7 레트로 [Codex ok]:** 플립 (make `FlipDigits` internal and reuse; tiles + 0.5 s flip on minute change;
  FLIP theme's full-screen DIGITAL routes here by day), 빨간 LED + 액정 = one seven-segment renderer (`seg7` geometry,
  off segments `ledColor` at 12 %, AM/알람 indicators; LCD = plate + `inkColor`), 전광판 (5 × 7 `DOT` font, off dots
  10 %).
- [ ] **Task 8 클래식:** 벽시계 = analog preset (light `faceColor` dial, numerals 1–12, `secondColor` red, big date
  beside; min side 200 dp for the date block), 달력과 시계 (tear-off page + serif time; compact = time + one date
  line), 한글 시계: `hangulTime(h, m, use24): Triple<String?, String, String?>` — 12 h: ("오전"/"오후", "<native hour> 시",
  "<sino minute> 분" or "정각" at :00), 0 h and 12 h = "열두 시"; 24 h: (null, "<sino hour> 시", same minutes), 0 h =
  "영 시". TDD: 0:00, 0:05, 9:41, 12:00, 13:30, 23:59 in both modes. Fonts per Global Constraints (`kfont`).
- [ ] **Task 9 계기 [Codex ok]:** 하루 막대 (24 h bar, now marker, sun window, alarm mark; tall boxes vertical; add
  sunrise,sunset to the existing `&daily=` list in `data/Parsers.kt`, `WeatherInfo.sunrise/sunset: LocalTime?`, parse test), 테두리 초
  (60 dots on the rounded screen edge, redraw 1 Hz only while charging, otherwise the minute dot only).
- [ ] Each: tests + gates + sheets, commit.

### Task 10: Settings shell (components only)
**Files:** `settings/Kit.kt`, `settings/Preview.kt`, `settings/Host.kt`, `settings/Perms.kt`, `settings/HomeScreen.kt`.
- [ ] `Page(preview = …)`: full preview = first scroll item; a 96 dp mini preview overlays the top when the full one
  is out of view (visibility only; no height change); tap = scroll to 0; optional `miniActions` slot (used by 시계).
  Preview bar: 낮|밤; 커버|안쪽 on fold/flip; 가로|세로 on all; pages swipe inside the preview; Fold inner two-pane
  keeps the left preview. An instrumented-free check: a Compose test scrolls the 시계 screen down/up in 10 dp steps and
  asserts the scroll position is monotonic.
- [ ] `PermLine(perm)` ("설정 한 가지가 더 필요해요 · 설정하기" → system page via existing `Perms.open`).
- [ ] Home per mockup `SCREENS.home` (4 groups, values, "설정 필요" pills, banner "N개 중 M개 완료" over the eight PERMS).
  Rows link to the existing screens until Task 11 replaces them. Commit.

### Task 11: Settings screens
Port each from mockup `SCREENS.*` (labels, order, 기본/자세히, disabled reasons); delete the old body when its
replacement lands: 테마와 배경; 페이지 구성 (+ 처음 보이는 페이지, editor, stacks, 사진 넘김 간격); 알림 표시; 밤 화면
(radio rows; 안 씀 disabled for light themes with the reason; disabled + link when 계속 켜 둠); 켜짐과 꺼짐 (충전하면 켜기,
무선 충전일 때만, 화면 꺼짐, 움직이면 켜기, 밤 알림 조용히; 자세히: 발열 보호, 잔상 막기, 최근 기록); 수면 소리 (4 sounds,
tap = 3 s preview via SleepService); 알람 (segmented → `alarmScreen`, (F) rows); 폴드를 펼쳤을 때 ((F), auto-off 0/3/10/15);
날씨와 도시 (one city sheet); 카드에 쓰는 정보 (D-day, 집중·휴식, 캘린더 고르기 by account, usage in personal); 권한
checklist; 앱 정보 (+ new font licenses). (F) reason "갤럭시 폴드처럼 안쪽 화면이 큰 폰에서만 쓸 수 있어요" (flip too).
- [ ] **Pages on/off + fixed four** (operator, 2026-11-13). Config (still v10, unshipped): `Board.on: Boolean = true`
  (codec omits true), `pagesOff: Set<String>` for the fixed pages `widgets`/`photos` (clock is always on). Fixed and
  never deletable: 위젯, 사진, 시계 + the `desk` board (작업 책상; no 지우기 button, no BoardOps.remove for id "desk");
  every other page can be deleted. 페이지 구성 rows get a switch (사용) except 시계; off pages stay in the list (dimmed)
  but leave the pager. One pure `Pages.visible(config): List<PageRef>` feeds StandbyRoot's pager count/order, 처음 보이는
  페이지 (lists visible pages only) and the preview pager; `startPage` is resolved through it (a start page that is
  off or deleted → 시계). TDD: visible order, off board skipped, start fallback, desk undeletable, codec round-trip.
  [페이지 추가] offers 빈 페이지 + every recommended preset not in the list (re-add after delete). Fresh install keeps
  today's recommended set (3 fixed + 모듈러·작업 책상·음악·잠자리·책 = 8; 수능·영상 were removed in Task 2).
- [ ] **사진 고르기** (operator, 2026-11-13). Config `photoSource: PhotoSource { PICKS("추천: 즐겨찾기 + 최근"),
  RECENT("최근 사진"), FOLDERS("폴더 고르기") } = PICKS`, `photoFolders: List<String>` (bucket names). Sources gets one
  query per mode (RECENT = newest 200 by DATE_TAKEN; FOLDERS = `BUCKET_DISPLAY_NAME IN (…)`, empty → PICKS) and a
  `folders()` list with counts. The 사진 row in 페이지 구성 opens a new 사진 screen: source radio, folder checklist
  (name + count, only under FOLDERS), 사진 넘김 간격 (moved here). The photos page's on-screen theme chips write the
  same config (one source of truth; the holder's in-memory `photoBucket` goes away). Memories card unchanged. Folder
  that disappeared → skipped; nothing left → PICKS. Test: query selection per mode (pure builder), fallbacks.
- [ ] SettingsShots per screen (phone + fold inner, alarm also non-fold). Old screens/rows removed. Commit.

### Task 12: Clock settings
**Files:** `settings/ClockScreens.kt` (rewrite), `SettingsShots.kt`.
- [ ] 시계 screen = mockup: preview + name/status + [내 시계에 넣기] or [꾸미기][빼기] (also in `miniActions`); 내 시계
  strip (tap selects; long-press drag reorders via `MyClocks.move`; ✕ removes; + scrolls to 둘러보기); 둘러보기 (전체 +
  five family chips, 2-column live `ClockFace(inSlot = true)` previews of all 21, ✓, "우리만"); 자세히: 시간 표시.
- [ ] 꾸미기 = rows from `spec(face).rows` / `more`, each `StyleRow` → one Kit control (colour rows use the existing
  `ColorPalette`; `combo2/3`, `solar`, `ledC` presets = a new `PresetSwatches` row; `kfont` = font tiles drawn in
  each Korean font). Shots: 시계, 꾸미기 for LED, 한글, 아날로그, 색 블록. Commit.
- [ ] **색 조합 바꾸기** (operator, 2026-11-13, via dot-dev): at the top of every face's 꾸미기 colour section a
  [색 조합 바꾸기] button + [이전 조합] button. Digital-family faces change their colour rows together (color · color2 ·
  bgColor · bgColor2, whichever the face has); analog changes hand · second · weekday · numeral · tick together. Pure
  `ColorShuffle.next(face, current, seed): Map<field, Color>` picks from `SWATCHES` and **rejects any combo whose
  digit/hand colours have WCAG contrast < 3.0 against the background** (BLACK = black, GRADIENT = both ends, photo/
  solar = skip the check). TDD: every result passes the contrast rule; 1 000 seeds never return a failing combo;
  the result differs from the current combo. [이전 조합] restores the combo from before the last shuffle (one level,
  `rememberSaveable` on the screen; a hand-picked colour is never lost). Not on the standby screen itself (night
  mis-taps), not for board accent/background (one colour). Shot: 꾸미기 with the two buttons.

### Task 13: Full matrix, review, release candidate
- [ ] Personal suite record → inspect sheets for every new clock/device → verify 0 diff; public suite plain; gates
  incl. the existing `check_secrets` on all four variants, extended with a positive control (personal must contain
  each key, public none; fail otherwise).
- [ ] Independent review of `git diff r10-start..HEAD` (side with more quota), fix Highs, re-run.
- [ ] Personal release APK to the scratchpad and the operator. Wrap-up (ROADMAP, task, decisions, dashboard).

## Self-review
Coverage: consensus → T1 (name, icon, 아이폰), T2 (removals, sounds, defaults, myClocks, options, migrations), T3
(heat, auto-off, quiet night, light-theme night, burn-in, photos, calendars, live times), T4 (B2–B5, alarm A, candle,
bar-land), T5–9 (21 clocks, B1 flip), T10–12 (settings, preview pattern, gallery, 꾸미기). Dropped by the operator:
희미한 시계, 밤 화면 색, 무지개·유리, 숫자 모양, 너비. Out of scope: store launch items.
