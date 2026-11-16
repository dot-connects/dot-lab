# StandBy r14 — plan

Task: `tasks/T-0162`. Mockups (approved 2026-11-16): `settings-r10/r14-picks.html` (https://claude.ai/artifact/NUivfTzDiQjdeqt37Sv6Dn).
Code root: `app/standby/src/main/java/kr/dot/sb/` (personal flavor `src/personal`, public `src/public`). Codec VERSION 11 → **12**.
Plan review round 1 (4 lenses) folded in 2026-11-16; log in the task's attempts.

Operator decisions (2026-11-16), all closed:
- 글자 크기, 사용자 지정의 글꼴·글자 두께: remove the settings, fixed defaults (theme defaults such as 종이 = 명조 stay).
- Card picker: picked card = whole card dimmed + big centred check (mockup 1-B). Tap again removes (already works).
- Clock card label in the picker and the stack rows: just "시계".
- 디지털: info (date · weather · alarm) **under the digits** in one line, like the 내 시계 꾸미기 preview; readable.
  New options on 디지털: 가운데 숫자 색 다르게, 꽉 채우기, 살짝 겹치기. **둥근 굵은 글씨 (FLOAT) is removed.**
- 입체 시계 colour picker: no colour names, swatches only.
- 입체 디지털 portrait: mockup 1 (B layout, landscape materials). Landscape unchanged.
- Page editor: no per-card colour; every card follows the theme.
- 폰 위젯 card removed → new **앱 바로가기** card (several apps, tap opens).
- Weather + usage: fetch on mobile data too; empty state with reason + 다시 시도; stale = dimmed + "HH:MM 기준" (mockups 2, 3).
- 내 테마: tapping the selected 내 테마 shows a dark veil + pencil (mockup 4-A, no text); pencil opens 사용자 지정 in "고치기" mode.
- Pull-down panel: 내 테마 listed first; brightness bar must visibly work.
- 무선 충전 start closed itself ("닫힘 · 이미 뽑힘" at 87 % with battery protection): judge by "plugged", not "charging".
- 최근 켜짐 기록 (문제 해결): personal build only; the public build hides it.

Defaults taken in review (no operator call needed, reported):
- FLOAT + DIGITAL both saved → **DIGITAL wins** (the user's digital look is kept; FLOAT is dropped from 내 시계).
- Old widget cards become an **empty** 앱 바로가기 card ("앱을 골라 주세요"); no carry-over of the widget's app.

## Part A — clock

**A1+A2+A5 one digital renderer.** `DigitalFace` (`ui/clock/DigitalFaces.kt`) serves full screen and slot; only
`DigitalCard` is deleted (ClockCard routes DIGITAL to `DigitalFace(inSlot = true)`). `ThemedDigits` stays (AnalogFace
`:100`, Overlays `:240`); its theme treatment (`digitStyle == GLOW` glow, `digitGlow` strength, `Primitives.kt:140-149`)
is applied in DigitalFace's canvas draw **in slot only** with today's condition (theme font, SOLID effect, night < 0.5,
`Primitives.kt:139-140`), so full-screen shots stay as they are. Card colour rule carried over from `DigitalCard`
(`Cards.kt:313-331`): hero card or user-styled clock = theme accent, a board's second (small) clock = `ink.ink`. The
DIGITAL slot path shows the info row (it does not take ClockCard's `showInfo = false`). Fixes once: the double pad
(`:133` subtracts `sz.pad` again — ClockCard already padded).
- Info row under the digits, centred with the digits as one block, in every layout. The IPHONE left strip (`:160-187`) is
  dropped; a stored `ClockLayout.IPHONE` renders as CENTER and the 디지털 layout row offers only 두 줄 / 가운데.
- Row content in order: [오전/오후 when 12 h] · weekday+day ("월 9", weekday in accent) · weather ("7°", only if `temp`
  non-null) · alarm ("⏰ 6:30", only if `alarmShort` non-null). Separators " · " in ink2, values in ink.
- Size: fixed `max(16.dp, digitCap × 0.16)`; `d.ts` reads in DigitalFaces are removed (text scale is gone, B1). If the row
  is wider than the block width: drop alarm, then weather; date always stays.
- `ClockStyle` (`model/Config.kt`) gets `accentMiddle`, `fill`, `overlap: Boolean = false`; codec keys `am`, `fl`, `ov`
  (absent = false).
  - These are drawn in DigitalFace's canvas path (`drawTime` / `TimePainter.drawHm`, `DigitalFaces.kt:83, :139, :206`),
    not `StyledTime`.
  - `accentMiddle`: index 1 of the colon-free digit string `h + m` (FloatFace `:91`) in `ink.accDeep` — "9:41" → the 4,
    "10:41" → the 0. One-line layouts only; two-line layout draws no accent digit (as FloatFace).
  - `fill`: one line cap = `min(0.92h, h − infoH − margin)`, two lines per line `min(0.46h, (h − infoH − margin)/2)`, times
    `sizePct` as now; normal (off) keeps today's CENTER formula `min(0.62h, 0.86h − infoH)` unchanged (no size jump for
    existing users). In slot the digits take all height left after the info row.
  - `overlap`: FloatFace's per-glyph placement (step `a − 0.12 em`, `a + 0.02` across the colon, ±3° tilt,
    Multiply/Screen blend) as a `drawTime` mode; the colon stays FloatFace's (drawn between, not overlapped); two-line
    layout overlaps within each row only; no roll animation.
- Registry (`ClockRegistry.kt:125-159`): `spec()` is an exhaustive `when`, so the FLOAT branch stays with `built = false`
  and `ClockRegistry.all` excludes FLOAT explicitly (gallery count and list never show it); DIGITAL adds toggles "가운데 숫자 색", "꽉 채우기",
  "살짝 겹치기". `FloatFace.kt` deleted. Also `ColorShuffle.kt:76`, `ClockFaces.kt:101` FLOAT refs: route to DigitalFace.
- `FaceId.FLOAT` **stays in the enum as legacy** (not pickable) so old JSON and backups still decode; ClockFaces draws it
  as DIGITAL.
- Migration `Migrations.floatToDigital` (v < 12), runs **after** `Migrations.flip`:
  - `myClocks`: FLOAT always replaced by DIGITAL at the same position, then `distinct()` (a FLIP user whose DIGITAL became
    FLIP keeps a digital clock).
  - `styles`: a FLOAT style moves to `styles[DIGITAL]` (with `accentMiddle = true`, `overlap = true`, font ROUNDED_HEAVY /
    950 / colour kept) **only if** a FLOAT style is actually stored **and** DIGITAL was not in the pre-migration
    `myClocks` and no DIGITAL style is stored. Every other case: FLOAT style dropped, DIGITAL untouched (an r9 user who
    never edited DIGITAL keeps the theme default digital look). `widgetOptions` faces handled like `flip()` (`:86`).
  - Board `clockFace` and slot faces FLOAT → DIGITAL in both cases. Idempotent (second run = no change).
  - **TDD** `FloatMigrationTest`: both style cases, FLIP-theme v10 user with FLOAT, idempotency.
  - Existing expectations to update: `ConfigCodecTest` `:106`, `:141-143`, `:268` (v9 myClocks had FLOAT); `ClockShots`
    float/fx shots and `BoardShots` dual-float → deleted or DIGITAL with the new options.
  - Stored `ClockLayout.IPHONE` shows as 가운데 selected in the layout row.
  - `Look` (ui.theme) clashes with internal `class Look` in `ui/clock/TimeText.kt:76`; import-alias where both meet.
- Slot (ClockCard): non-DIGITAL faces get `style` unchanged (keep `showInfo`), no extra flat `Detail` line; faces draw their
  own date (relief date window etc.).
- Check by shots, not a size test: `clock/digital-*` re-recorded (FLIP_SMALL, COVER_NARROW, bar-port: row not clipped),
  plus a 작업 책상 board shot (fold cover land) with DIGITAL normal and `fill`; eyeball digits fill the slot.

**A3 입체 colour names.** `Kit.kt:457` `NamedSwatches` gets `showNames: Boolean = true`; `ClockScreens.kt:332-334` passes
`false`. LED and glow pickers unchanged. `Relief.DIALS[].ko` stays as `contentDescription`.

**A4 입체 디지털 portrait.** `ReliefFaces.kt`: `RlGeo.of` CAPSULE gets a portrait branch when `h > w * 1.15`. Units are
the capsule units already used (digit W 24, H 46, T 4.6, gap 5.2); mockup code = `relief2.js` logic in r14-picks.html.
- `u = (w × 0.74) / (2·24 + 5.2)`; rows = hours (2 digits), minutes (2 digits), row gap 17 units, block centred at h × 0.53.
- Colon = two studs (radius 2 u silver, 1.45 u lume) at x = centre ± 8 units, y = middle of the row gap.
- Date window 22 × 7 units, centred, bottom edge 9 units above the block (same silver frame + top shade as landscape).
- Seconds scale: from x = centre − 30.6 to centre + 30.6 units (pair width 53.2/2 + 4 each side), 61 ticks, labels every
  10, 11 units under the block; knob as landscape.
- Ghost segments for unlit segments in all four digits (as landscape); leading blank hour ("9:41") = top row ghost + 9.
- Implementation: extend `RlGeo` with `portrait: Boolean` and the digit origins list (`digitX: FloatArray`,
  `digitY: FloatArray`), colon/date/scale anchors; `capsuleStatic`, the per-tick layer and `dateBox` read positions from
  `RlGeo` instead of the fixed row. Landscape values computed exactly as now → existing relief shots must be 0 diff.
  Compact fallback (< 120 dp) unchanged.
Shots: relief-digital bar-port, flip-cover, fold-cover-port; ice/cream/sky/coal; night.

## Part B — settings

**B1 remove 글자 크기 / 글꼴 / 글자 두께.** Codec stops **reading and writing** `textSize`, `ThemeEdit.textFont`,
`textWeight` (keys ignored on decode → defaults; idempotent, no migration code). Fields removed from the model; all
`textScale` reads become 1.0 (`LocalTextScale` default). UI: HomeScreen `:118-119`; ThemeEditor `:169-170` rows and
read/write/reset (`:276-277, :288-289, :308-309, :327-328, :560-564`), plus `encodeEdit` (`:107`) drops them so 되돌리기
can't bring them back. Theme tokens' own `cardFont/textWeight` defaults untouched.

**B2 card picker veil.** `settings/BoardScreens.kt:575-590`: `added` → over the preview a `Box(matchParentSize)` with
`Color.Black.copy(alpha = 0.55f)` + centred 46 dp `SK.accent` circle with `Icon(Ic.CHECK, SK.bg, 24.dp)`; 2 dp accent border
on the tile; the label row loses "추가됨". Replace-mode border unchanged.

**B3 "시계" only.** `BoardScreens.kt:208` `CardPick.ko` → `type.ko` for CLOCK (picker and stack rows).

**B4 no per-card colour.** Remove the 색 button (`:510-512`), panel (`:522-527`), `onPlate` wiring (`:399-400`), render
override (`Cards.kt:164-169` → theme plate). `SlotOptions.plate` removed; codec ignores the key.

**B5 내 테마 고치기 (mockup 4-A).** HomeScreen.kt `ThemeThumb`:
- tap on an unselected 내 테마 = switch (as now); the selected one (`StripPick.of(config) == StripPick.Mine(name)`) shows the
  veil (as B2) + 38 dp accent circle with `Ic.EDIT`; tapping the selected thumb or pencil opens `CustomThemeScreen` with a
  new `editing: String?` parameter (route arg) = name.
- `Ic.EDIT` (new, `Kit.kt` `Ic`, 24-unit grid like the others): pencil body `M4 20 L4 16 L15 5 L19 9 L8 20 Z`, ferrule
  line `M13 7 L17 11`, stroked like `Ic.CLOSE`.
- ThemeEditor "고치기" mode: title "{name} 고치기", hint "고친 내용은 [저장]을 눌러야 '{name}'에 덮어써져요", buttons
  되돌리기 · 다른 이름으로 · 저장 (저장 = `saveMyTheme(name)`, which already replaces in place), second row 이름 바꾸기 ·
  지우기 (existing dialogs; 지우기 returns to 테마와 배경 with 기본). Long-press menu stays. `UnsavedGuard` applies.

**B6 pull-down panel.** `ui/screens/TopPanel.kt:132-152`: list = `config.myThemes` then `Theme.entries`; selection via
`StripPick.of(config)`. `StandbyActions.setTheme(Theme)` → `setLook(Look)` (`State.kt:247`, `Host.kt:85`,
`StandbyRoot.kt:260`), and `switchFromPanel` takes a `Look`: an unsaved edit is still saved as the next "내 테마 N" first
(no data loss). Initial scroll targets the selected item.

**B7 brightness bar.** Review round 2 ruled out a snap-back race (`ConfigStore.update` sets the same `MutableStateFlow`
synchronously; `setOff` already skips while pending). Remaining causes: linear `screenBrightness` (mid bar looks near
full on OLED) and the heat cap (no change above 35 %). So: curve + heat hint + logging, no waiting on a log.
1. Regression guard (cheap, state logic): Robolectric `BrightnessBarTest` on `StandbyStateHolder` with a fake host — bar
   edit → host gets the value → flush → next `evaluate` keeps it (day and forced-night, light themes force night).
2. Trail (shown in the personal build, B8): every `host.setBrightness` change logs "밝기 {source} {old}→{new}" (source =
   막대 / 밤 / 열; screen off/on is not logged — it would push the charge lines out of the 8-line trail). Trail text is Korean like the existing ChargeStart lines (it is UI).
3. Perceptual curve: bar `p` ↔ window `v = MIN + (1 − MIN) · p^2.2`; `Brightness.toBar(v)` / `fromBar(p)`; `shown()` goes
   through `toBar`. `toBar` of AUTO (−1) = `toBar(0.5)`; values < MIN clamp to MIN first (no NaN). Stored values stay
   window values. Test (in `BrightnessBarTest`): round trip, monotonic, AUTO/OFF/below-MIN give a finite bar.
   `fromBar` is applied at `setUserBrightness` entry (the panel passes `p`); `ChargeBrightnessTest:63-65` expectations
   follow the new `shown()`.
4. Heat: the holder exposes `brightCapped: Boolean` in state (`heatProtect && hot && (v == AUTO || v > HOT_MAX)` with
   `v = pending ?: stored`, stored = `nightMaxBrightness` at night else `dayBrightness ?: AUTO` — same test as
   `Brightness.applied`); the panel shows
   "폰이 뜨거워서 35%까지만 밝아져요" under the bar while true.

**B8 trail only in the personal build.** MoreScreens.kt `:571` 문제 해결 section shown only when
`BuildConfig.FLAVOR == "personal"` (flavor dimension "dist"; check the public flavor name in `build.gradle.kts`). Trail
writes stay in both builds. Trail lines never contain URLs or exception messages (C2 logs the error kind only).

## Part C — cards

**C1 앱 바로가기 replaces 폰 위젯.**
- Model: `CardType.WIDGET` → `CardType.APPS("앱 바로가기")`; `SlotOptions.apps: List<String>` (package names, tap order,
  max 8 — a 9th tap shows "8개까지 고를 수 있어요" and is ignored). Widget fields removed; codec decodes "WIDGET" as APPS with
  `apps = []` and ignores widget keys (idempotent).
- Old host ids: once at app start `AppWidgetHost(ctx, 0x5B0A).deleteHost()` behind a `widgetsCleared` pref (keep the
  constant when WidgetHost.kt goes).
- Picker: settings route "앱 고르기" (replaces `Routes.widget` and the `widget:` section extra in SettingsActivity
  `fromBoard`): `pm.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), 0)`, sorted by label,
  search field, multi-select with the B2 veil+check. Manifest `<queries>`: add MAIN/LAUNCHER intent, remove APPWIDGET_UPDATE.
- Card: grid of app icons (+ label when the slot is ≥ M), cells sized to `apps.size`; empty = "앱을 골라 주세요", tap opens
  the picker. Tap = existing `Host.kt:77` launch-by-package helper (`unlockThen` + `getLaunchIntentForPackage`); not
  launchable / uninstalled → cell hidden. Night: `nightFilter(n)` (`Kit.kt:373`) on the icons.
- Charge-started StandBy `finish()`es on `ACTION_USER_PRESENT` (`StandbyActivity.kt:48-56`), racing
  `onDismissSucceeded`. Fix: `unlockThen` sets an `unlocking` flag; the USER_PRESENT receiver ignores it while set;
  `onDismissSucceeded` launches from the activity context, then `finish()` only if `chargeStart` (manual/tile StandBy
  stays as today); `onDismissCancelled`/`onDismissError` clear the flag. Same path serves `openMediaApp`/`openAlarmApp`.
- Icons: picker loads labels+icons on `Dispatchers.IO` once per open; the card caches `ImageBitmap` per package
  (`remember(pkg)`), no Drawable→Bitmap per recomposition.
- Delete `platform/WidgetHost.kt`, `ui/cards/PhoneWidget.kt`, `settings/WidgetPicker.kt`, SettingsActivity bind/configure
  (`:231-367`), `WidgetOptionsScreen`, `BoardOps.widgetIds/removedWidgetIds`, host start/stop in StandbyActivity/
  StandbyDream, `LocalLiveWidgets`, widget tests. The widgets page preset uses APPS.

**C2 weather and usage.**
- `fetchWeather` (`:493`): drop the `onWifi` gate. Usage (`personal/.../UsageFeeds.kt`): `fetch()` no Wi-Fi requirement;
  LAN route keeps Wi-Fi + private IP, Telegram route any network.
- Error kind without changing the feed interface: the holder checks for an active validated network (same
  `ConnectivityManager` as `Feeds.onWifi`) before fetching → none = `NO_NETWORK`; fetch returned null / threw = `FAILED`.
  Set at the same points as `markWeatherOffline`/`markUsageOffline` (`:491`, `:498`, `:511`), which are no-ops while
  data is null — without this the first failure leaves the card loading forever. State gets `weatherError/usageError: FeedError?`, cleared
  on success. Existing `offline` flag keeps meaning "have data, last refresh failed".
- Cards: null + no error = loading (first fetch only); null + error = empty state (mockup 2b/3b: "날씨를 아직 못
  불러왔어요" / "PC 소식을 아직 못 받았어요", hint by kind, 다시 시도 → `retryFeeds`); data + (offline or older than
  weather 2 h / usage 3 h as now) = 55 % alpha + "HH:MM 기준" + 다시 시도 (mockup 2c).
- **TDD** `FeedStateTest`: null→NO_NETWORK, null→FAILED, FAILED→success clears, data+fail = offline not error.

## Part D — charge start

Log evidence: "띄움 → 화면 열림 → 닫힘 · 이미 뽑힘" — the first launch closed itself at `StandbyActivity.kt:45`
(`isCharging` false under 85 % protection / pad start); the ~5 s StandBy the operator saw is the later relaunch. Fix:
`:45` and the tile rule `:33` use the sticky `ACTION_BATTERY_CHANGED` `EXTRA_PLUGGED == 0` (same read as `:38`,
`ChargeStart.kt:139-144`); `Glow.charging` (`Glow.kt:103`) the same. One private `plugged(ctx)` in ChargeStart reused;
no new test (one-line rule). Trail adds elapsed ms and the plugged value to "화면 열림" (a pad renegotiating can read
0 for a moment; if the log shows it, the next round adds a short re-check).

## Order of work (one commit each, tests green between)

New fields are added in the step that uses them; old fields/keys are removed in the same step as their UI.
1. D (charge) + B7 (guard test, logging, curve, heat hint) + B8.
2. A1+A2+A5 (digital renderer, options, FLOAT migration, clock card) + codec v12 bump + shots.
3. A4 + A3 + shots.
4. B1–B6.
5. C1 + C2 + shots.

Gates per step: `--tests` for touched classes; at the end the full suite, IPHONE shots (touched classes re-recorded,
others 0 diff), `check_secrets` (public APK 0 secrets), APK sent first.

## Device checks after install (operator)
- Wireless pad at ≥ 85 % with protection: StandBy without the close/relaunch; log "화면 열림" with no "이미 뽑힘".
- Brightness bar: half bar visibly dim, stays after letting go; log shows "밝기 막대".
- Mobile data: weather and usage fill within 5 min (PC on).
- 앱 바로가기: pick Samsung Notes + 2 others; tap opens after unlock — once from a charge-started StandBy.
- 입체 디지털 portrait on the cover screen; 디지털 with 꽉 채우기 in the 작업 책상 page.
