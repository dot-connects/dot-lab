# StandBy — iPhone parity / acceptance review 1 (2026-11-10)

Spec P0 rows, §2-§4 vs `app/standby/src/main/java/kr/dot/sb/` (paths relative), read mid-edit. Device-only risks R1-R10 not judged.

**DONE (no action):** N2 (token mapping instead of the luminance matrix: accepted design choice) · D3 (settings sections instead of tabs) · E1 E2 E5 E6 E10 E11 E13 E14 E19 E20 · N3 N4 N5 N6 · W1 W2 W3 · P2 P3 · C1 C2 C3 C4 C6 C7 C8 C10 ·
D4 D10 · I1 I2 I3 I6 I12 · K1-2 K3 K5 K10 K11 · B1 B2 · M2 (logic) · §2 OFF/night/priority · E8.

## 1. P0 rows not DONE

| # | Status | Where | What is missing / wrong | Exact fix |
|---|---|---|---|---|
| E7 | WRONG | `data/Logic.kt:116-122` | "Linear" = raw − low-passed gravity (A=0.9 @16 Hz, τ≈0.6 s). Turning the phone 90° (E4) leaves 5-9 m/s² of lag error for >300 ms → `pickedUp` → `finish()`. Rotating the phone exits StandBy (dream and the preview activity). | Use the rotation-invariant magnitude: `linear = abs(sqrt(x²+y²+z²) − 9.81f)` for both pick-up and wake; keep the gravity low-pass only for `gz`/flat. |
| E12 | WRONG | `data/StandbyStateHolder.kt:198-202` | Settings hide 야간 모드 under 안 함 (footer says it cannot be used), but the holder still turns red at night. | `val nightOn = c.displayOff != DisplayOff.NEVER && when (c.nightMode) {…}`. |
| E4/§3 | PARTIAL | — (no `systemGestureExclusion` anywhere) | Left/right edge back-swipe can end the dream mid board-swipe (R7). | `Modifier.systemGestureExclusion()` on two 200 dp edge strips in `StandbyRoot` (or `decorView.systemGestureExclusionRects` in `StandbyDream.onAttachedToWindow`). |
| §1c start | PARTIAL | `ui/screens/StandbyRoot.kt:111`, `StandbyStateHolder.kt:379` | "First entry = 시계, then the last viewed board": `onPage` keeps the page in memory only; every dream starts on `startPage`. | Persist last settled page (`ConfigStore.update { it.copy(lastPage=…) }` or a prefs int) and use it as the pager's initial page when no explicit start page was set. |
| N1 | PARTIAL | `StandbyStateHolder.kt:116, 135` | No 1 s median filter; raw lux drives the holds (a phone-screen flash can reset the 5 s hold). | Keep the last 3 readings ≤1 s old, feed `median` into `nightSw.update`. |
| W6 | MISSING | `ui/cards/Cards.kt` (no badge, no "앱에서 열기") | Non-interactive cards (weather, calendar, agenda, D-day, usage, memories) do nothing on tap; media cards open the app directly. | In `CardView`, non-interactive types: tap → show ↗ badge top-right 3 s (a11y "앱에서 열기"); tap badge → `actions.openSettings`/`unlockThen(open app)`. Add `openApp(card)` to `StandbyActions`. |
| P1 | WRONG | `ui/screens/PhotosPage.kt:300, 321` | Swipe shows 자연/반려동물/도시/인물 pills but the photo set never changes (labels lie). P0 = 추천 + user albums. | Themes = `["추천"] + MediaStore bucket names`; pass the chosen bucket to `DeviceSources.photos(ctx, n, bucket)` (add `BUCKET_DISPLAY_NAME=?` selection). Drop the 4 ML themes until P1. |
| P5 | MISSING | `PhotosPage.kt:344-351` | Caption tap does nothing. | `.tap { actions.openPhoto(uri) }` on the caption column → `unlockThen { startActivity(ACTION_VIEW, uri) }`; keep the `Uri` in `Photo`. |
| C5 | WRONG | `ui/clock/WorldFace.kt:23-24, 88-89, 116` | City dot hard-coded to Seoul; label = weather city; `config.worldCities` (settings `MoreScreens.kt:268-281`) is never read; no "오늘, +6시간" line; time is always local. | Pass `worldCities.first()` (lat/lon/zone) into `FaceData`; dot from its lat/lon; time in that zone; line `"오늘, +N시간"` / `"내일, …"` from zone offset vs system zone. Add a `zoneId` to `City`. |
| C9/C11 | PARTIAL | `model/Config.kt:138, 125`; `ui/cards/Cards.kt:120-128` | Styles are per `FaceId`, boards pick a face: two clock cards cannot keep different styles ("each clock instance keeps…", C11 duplicates). | Optional `styleId` per board slot → `styles: Map<String, ClockStyle>`; editor "이 시계만 따로 꾸미기". |
| D2/§3 | PARTIAL | `StandbyRoot.kt:177` | Long press = unlock + settings board editor (no in-place jiggle/`+`/완료). Widgets page sends `"board:widgets"`, which `settings/Host.kt:50-52` does not know → lands on Home. | `if (board.id == "widgets") openSettings(Routes.WIDGETS) else openSettings("board:${board.id}")`. |
| D6 | MISSING | `ui/board/BoardView.kt:189` | Modular big slot is a vertical stack; spec: horizontal pager, ≤6 pages, claims the gesture. | In `SlotStack`, when layout is `TWO2`/`BIG2` and slot is the big one → `HorizontalPager` (max 6 cards) + horizontal dots; nested scroll keeps the board pager from moving while inside. |
| D7 | MISSING | `Cards.kt:508-583`, `data/Parsers.kt:52` | No 현재/오늘/주간/기온만 styles; feed has 2 days, so 주간 is impossible. | Add `WeatherStyle` per slot card; `forecast_days=7` + daily codes; 기온만 = temp only. |
| D8 | PARTIAL | `Cards.kt:597-600` | 월/날짜만 chosen by slot size only. | Same per-card style flag; size fallback stays. |
| D12 | MISSING | `settings/MoreScreens.kt:245-249` | No 위치: 접었을 때 / 펼쳤을 때 checkboxes; only an on-unfold action. | Two `SwitchRow`s; holder: if inner unchecked and unfolded → render OFF (or `finish()`); cover unchecked → `finish()` at start when folded. |
| D16/M1 | PARTIAL | `StandbyStateHolder.kt:402`, `platform/StandbyDream.kt:50` | Dream that survives unfold still launches GlowActivity via FSI instead of switching to MOOD in place; and posts twice (config change + `onDetachedFromWindow`) → two GlowActivity instances. | Render a MOOD layer in `StandbyRoot` when `unfolded && onUnfold==MOOD_LIGHT`; post FSI only from `onDetachedFromWindow`. At minimum `android:launchMode="singleTop"` on GlowActivity. |
| M4 | PARTIAL | `platform/Glow.kt:374-378` | Only tap; no vertical drag = brightness 5-100 %, no horizontal swipe = colour. | `GestureDetector` on root: `onScroll` vertical → `window.attributes.screenBrightness`, horizontal fling → next of GLOWS list (move list to model), persist `glowColor`. |
| I4 | PARTIAL | `StandbyStateHolder.kt:342-354`, `Overlays.kt:216`, `Tokens.kt:146-147` | Woken from OFF stays on 20 s (spec: back to OFF after 6 s); icon 30 % (spec 40 %); text at 1.55 s (spec 0.9 s); no swipe up/down to close. | Remember `wasOff`; after the 6.5 s clear call `machine.forceOff()`; `big = s*0.40f`; `NOTIF_HOLD_MS = 300`; add `detectVerticalDragGestures` → `notificationDismiss`. |
| I11 | PARTIAL | `Overlays.kt:373-446` | No ✕ button; volume icon not tappable (no slider); transport has no a11y labels (재생/일시정지/이전/다음/볼륨). | ✕ top-left → `onClose`; volume tap → `AudioManager.adjustStreamVolume(…, FLAG_SHOW_UI)` or a slider; `contentDescription` per Btn. |

## 2. Bugs

| Sev | Where | Bug | Fix |
|---|---|---|---|
| High | `Logic.kt:116-122` | E7 false pick-up on any rotation → StandBy closes (above). | Magnitude-based linear accel. |
| High | `AndroidManifest.xml:7`, `settings/Perms.kt:59-67` | targetSdk 35 but `POST_NOTIFICATIONS` is never requested → the glow/mood-light FSI (`Glow.kt:351`) is silently dropped: M1/M2/M3 never show. | Add `Perm.NOTIFY("알림 허용", …)` runtime row (`Manifest.permission.POST_NOTIFICATIONS`). |
| High | `StandbyStateHolder.kt:158, 205` | Alarm only `touch()`es once; in 20초 후 or dark 자동 the display goes OFF 20 s into a ringing alarm — I1 vanishes while ringing. | In `evaluate`: `val off = (… ) && _state.value.alarm == null && _state.value.notification == null`. |
| Medium | `Overlays.kt:340-343` | WavePill loops every 160 ms on every board while music plays (overnight YouTube Music = 6 redraws/s all night). Lightness rule 1. | Static waveform glyph; animate only the 2 s after it appears, or only on Now Playing. |
| Medium | `StandbyStateHolder.kt:172-175`, `Cards.kt:304` | Running pomodoro rewrites `StandbyState.timers` every second → the whole visible board (every `CardView(state)`) recomposes 1 Hz for 25 min; loop also wakes while OFF. | Put `endsAtElapsed` in `TimerState` (changes only on start/stop/finish); `TimerCard` ticks its own digits/ring with a local 1 s `LaunchedEffect`, only while composed. |
| Medium | `platform/Listener.kt:66`, `Glow.kt:335` | Alarm glow fires for every alarm whenever the last config was inner (phone unplugged, StandBy not running). | Gate `showIfInner` on "StandBy running" (a `@Volatile running` flag set in holder start/stop) or on `BatteryManager.isCharging`. |
| Medium | `StandbyStateHolder.kt:386`, `SleepService.kt:328-331` | `startForegroundService` from the dream is not wrapped; a `ForegroundServiceStartNotAllowedException` crashes the dream, and `SleepBus` was already set to playing. | `runCatching { … }.onFailure { SleepBus.state.value = SleepState() }`. |
| Medium | `StandbyDream.kt:50` + `StandbyStateHolder.kt:402` | Double FSI post on unfold → duplicate GlowActivity (above). | `singleTop` + post once. |
| Low | `Glow.kt:393-397, 431-435` | Light listener registered at FASTEST leaks if the activity dies within 400 ms with no reading. | `sm.unregisterListener(l)` in `onDestroy`. |
| Low | `Glow.kt:397` | Plain mood light also auto-finishes at 10 min (M5 default 30, P1). | Apply the 10 min only when `ListenerBus.alarm.value != null`. |
| Low | `PhotosPage.kt:338` | Crossfade keyed by index: the 45 s reload swaps the photo under the same index → hard cut. | `Crossfade(photos[idx % size])` (key by Photo). |
| Low | `StandbyStateHolder.kt:274` | `burnStep++` on every wake, not per minute. | Step from `minute-of-day`: `BurnIn.at(t.hour*60+t.minute)`. |
| Low | `Cards.kt:419` | Agenda XS: `"종일" >= "HH:mm"` is always true → all-day event shown as "next". | Compare only non-all-day starts. |

Gestures (§3): exit swipe (Initial pass), slot vs board pagers, board long press, overlay blocking all OK. Missing: D6 claim, notification swipe-close, mood-light drag/swipe.

## 3. Copy (§4) differences

| Where | Code | Spec |
|---|---|---|
| `model/Config.kt:189` | 빛 번짐 | 빛번짐 |
| `model/Config.kt:202` | 12·3·6·9 | 4개 |
| `model/Config.kt:276` | 영상 앱 | 유튜브 (operator extra: keep) |
| `settings/MoreScreens.kt:262` | "한 시간마다" | code fetches every 30 min (`StandbyStateHolder.kt:265`) — make them agree |
| missing | 위치 / 접었을 때 / 펼쳤을 때, 끄기 타이머, 밝기 (mood), "오늘, +6시간", 남은 날 (D-day), 앱에서 열기, 이전/다음/재생/일시정지/볼륨 (a11y), 사용자화, 새로 추가, 알림 허용 row | §4 |

All other §4 strings (display/night A-notes, alarm, notification, card texts, layouts, presets) match.

## 4. Operator extras

| Extra | Status |
|---|---|
| 11 fonts, weight, size, width | DONE (`Config.kt:175`, `ClockScreens.kt:99-117`) |
| Effects 5, background 3, numerals 6, layout 3, analog numerals/ticks/5 colours | DONE (`ClockScreens.kt:121-169`) |
| Per-instance style (two clocks, two looks) | PARTIAL — per face only (C11) |
| Boards 1-4 slots, 6 layouts, unlimited, presets | DONE (`BoardView.kt:65-98`, `BoardOps.kt`) |
| 12 cards | DONE — all 12 types render (`Cards.kt:99-112`) |
| Music via media session (YouTube Music, TVING) | DONE (`Sources.kt:37-127`); TVING routes to the 영상 card only, so a 음악 card stays empty while TVING plays |
| Video app | DONE (session card, no in-app video) |
| Timer / pomodoro | DONE (no reset gesture: `timerReset` is never called) |
| Sleep sound | DONE (procedural noise, FGS) — crash risk above |
| D-day, agenda, weather, calendar, memories | DONE (weather: fixed city, no weekly) |
| Claude/Codex usage via 눈금 feed | DONE (`Feeds.kt:305-327`, 5 min, Wi-Fi only) |
| Fold mood light at alarm time | PARTIAL — logic present, but blocked by missing POST_NOTIFICATIONS; fires when StandBy isn't running; no M4 controls |
| Lightness | PARTIAL — minute ticker, OFF = tickers cancelled, sensors unregistered, Wi-Fi-only 30/5 min polls, low refresh rate all OK; violations: WavePill 6 Hz, pomodoro 1 Hz full-board recomposition, sleep wave per frame |
