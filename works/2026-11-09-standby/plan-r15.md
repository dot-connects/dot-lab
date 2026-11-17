# Stand Screen r15 — plan (release readiness + settings rework)

Task: `tasks/T-0162`. Mockups: https://claude.ai/artifact/3xUq8fnJrsFWHLHVXYhK1A (copy: `settings-r10/r15-mock.html`).
Reviews this plan folds in (2026-11-17, Fable): app review (High 8 / Medium 10 / Low 7), settings UX review (IA, copy,
top 10), reel review (separate, reel only). Code root: `app/standby/src/main/java/kr/dot/sb/` (`K\` = `settings/`).
Codec VERSION 12 → **13**.

## Operator decisions (2026-11-17), all closed

- Store name and in-app name = **Stand Screen**. Every user-visible "스탠바이"/"StandBy" becomes "Stand Screen"
  (sentences included); settings home large title = "Stand Screen". Font labels "StandBy 디지털/플로트/솔라" lose the word.
- Price: no ads; 14 days everything free, then free tier + one-time unlock (₩4,900 proposed). **Billing is not in r15**
  (store launch). Closed test = all free.
- Public package id: **`kr.dot.standscreen`** (personal stays `kr.dot.standby`).
- Photos: **user picks folders** with the system folder picker (Storage Access Framework, no media permission).
- Testers: Google Group + profile link (operator creates the group; Claude writes the steps).
- Portrait redesigns: 전광판 **A** (hours over minutes, dots fill the screen), 액정 **A** (desk clock, two big rows,
  weekday/date row above, alarm/temp row below), 하루 막대 **C** (landscape layout reflowed: big time, next two
  events, horizontal bar at the bottom).
- 사진 위 시간: draws the photos of the memories card's folders behind the time (today it draws only the time).
- 내 시계 gallery: picking = whole tile dimmed + big centred check (like the card picker); tap again removes.
- 앱 바로가기: icon colour **원래 색 / 테마 색** chosen in the card's settings, with a live preview there. 테마 색 = the
  app's monochrome layer on a card-coloured plate in the theme accent; apps without one keep their colours.
- New option: with 무선 충전일 때만 on, **가로로 놓였을 때만 켜기**.
- Settings rework (UX review §5) is in r15. Default picked: 테마 색 is not the default for icons — 원래 색 is (operator
  asked for a choice; the review favoured recognisability).
- All app-review Highs and Mediums are in r15. Lows only where noted.
- Stock card → `ideas/I-0055` (out of r15).

## Part A — release blockers (H2, H3, H4, Low cleartext)

A1. `compileSdk`/`targetSdk` 36: AGP 8.7.3 → 8.9+ and Gradle 8.10.2 → the version that AGP needs (check release
notes, pin both). Shots stay `@Config(sdk = [34])`, so target-36 behaviour (edge-to-edge opt-out gone, ≥600 dp ignores
orientation/resizability, `screensaver_components` read in `Perms.kt:36`) is checked once on a real Android 16 phone.
A2. Public flavor `applicationId = "kr.dot.standscreen"` (no suffix); personal keeps `kr.dot.standby`.
`res/xml/dream.xml` moves to `src/personal/res/xml` and `src/public/res/xml` with each flavor's settings activity.
A3. Release signing: upload keystore outside the repo (`%USERPROFILE%\.standscreen\upload.jks`) + `upload.properties`
(path, alias, passwords generated randomly by a script; never printed, never committed). `build.gradle.kts` reads it
when present, else release stays unsigned (CI/shots unaffected). Play App Signing holds the app key.
`./gradlew :standby:bundlePublicRelease` produces the AAB.
A4. `usesCleartextTraffic` only in the personal manifest (public: none).
A5. Version 0.5.0 (versionCode 14).
minSdk stays 34 (Android 14+); the tester invite says so (M10 is an operator check).

## Part B — Play policy (H5)

B1. Photos via SAF (operator decision): drop `READ_MEDIA_IMAGES` and `READ_MEDIA_VISUAL_USER_SELECTED`.
`PhotoSource` becomes folders only: `OpenDocumentTree` (initial URI = DCIM), `takePersistableUriPermission`, store
tree URIs (codec v13).
- Index: built once per StandBy start and on folder change, kept in memory (uri, name, date); `reloadPhotos` (every
  45 s, `StandbyStateHolder.kt:468-472, 573-584, 674`) picks from the index, never re-lists. Recurse one level;
  skip dot-files (`.trashed-*`, `.pending-*`) and folders named Screenshots/스크린샷.
- Date = filename pattern (`YYYYMMDD_HHMMSS`, `IMG_YYYYMMDD…`, `Screenshot_…` skipped) → else `LAST_MODIFIED`; EXIF
  `DateTimeOriginal` read only for the few 추억 candidates.
- Stale grants (folder deleted/renamed, config restored from backup): drop the URI, show "다시 골라 주세요".
- Photos-page album chips (`PhotosPage.kt:78, 96`, `StandbyStateHolder.photoTheme` `:677-678`) list the picked
  folders by name; `openSettings("photos")` maps to `Routes.PHOTOS` (`SettingsActivity.kt:76`).
- 추억 ("N년 전 오늘") reads the same index. Migration: PICKS/RECENT/old bucket names → empty list → the photos row
  and the memories card show "폴더를 골라 주세요" + 고르기. The PHOTOS perm row goes away. Tester note: Android blocks
  picking the storage root and Download; DCIM/Camera and Pictures work.
B2. Privacy policy page on dot-lab (`/standscreen/privacy`, Korean + English): notification text, calendar and photos are
read on the phone and never leave it; weather = city name to the weather API; no ads, no analytics. Link in 정보 and in
the notification-access permission step, with the line "알림 내용은 폰 밖으로 나가지 않아요".
B3. `docs/play-declarations.md` in the work folder: ready-to-paste Console answers for notification listener, full-screen
intent, foreground service `mediaPlayback`, exact alarm, Data safety. Operator pastes them.
B4. Charge-start needs more than DREAM (Fable plan check H1): the dream starts only when a lit screen times out while
charging (`ChargeStart.kt:56-59`); plug-in with the screen off needs notification access (receivers live in the
listener, `Listener.kt:72-86, 113-115`) + POST + full-screen intent (`ChargeStart.kt:49-50, 160-161`). First step
of work: one real-phone matrix (each grant revoked in turn) recording what starts StandBy. First-run step 3 becomes a
"자동으로 켜기" bundle: screen saver (and its "충전 중" setting), notification access, POST, full-screen intent — one
screen, checked in order. If Play denies FSI (non-alarm app), copy and tester note say "화면이 켜진 채 충전하면 켜져요".
`Perm.DREAM` also checks the screen saver is enabled for "while charging" where readable.

## Part C — behaviour fixes (H1, M1–M6)

C1 (H1). `Listener.kt:61-68` alarm detection ignores our own player: `TimerSound` exposes `playing` (true from
start until release); detection skips while it is true. Alarm takeover hides 다시 알림/끄기 when the source has no
snooze/stop intent and shows only 닫기; `ListenerBus.kt:74-80` no longer launches the Samsung clock as a fallback.
Test: a fake active `USAGE_ALARM` config while `TimerSound.playing` → no takeover (pure decision function).
C2 (M1 + new option). Charge-start gate = **not face-down and not moving** (an upright stand has z ≈ 0, so no "facing
up" rule), with a retry window: sample up to every 2 s for 15 s after the trigger, start at the first still sample.
No proximity rule (many Samsungs have none outside calls). The job path returns `true` + `jobFinished`
(`ChargeStart.kt:188`). Axes measured first on the real Fold/Flip in each posture via the trail log, thresholds set
from that. New option 가로로 놓였을 때만 켜기 (under 무선 충전일 때만, enabled only when that is on): separate check,
gravity mostly along the device x axis. Pure function `ChargeGate.ok(sample, opts)` with tests.
C3 (M2). `Listener.handle` and `publishShade` wrapped in `runCatching` + log; `:185` nullable action title fixed.
C4 (M3). Glow validates at fire time in `GlowReceiver`: after ours fires, `nextAlarmClock` is the user's real one;
skip the glow when it is missing or differs from the stored time (no cancel/re-read loop, `Glow.kt:43-44, 57-67`).
C5 (M4). SleepService requests audio focus (`AUDIOFOCUS_GAIN`, pause on loss, duck on transient) and stops on
`ACTION_AUDIO_BECOMING_NOISY`.
C6 (M5). A charge-started StandbyActivity does not `finish()` when covered by our own mood-light activity; it finishes
only when another app or the lock screen takes over.
C7 (M6). ChargeStart's screen on/off receiver returns early when not plugged; no reschedule, no log line.
C8 (Low). `ConfigCodec` gets a failing decode variant; ConfigStore (SharedPreferences) writes a backup key when a load
succeeds; a failed decode restores the backup before falling back to defaults.
C9. POST is no longer requested in `StandbyActivity.onCreate` (`:74-75`); "지금 보기" starts through the tile path's
low-battery guard (`StandbyActivity.kt:34-44`).

## Part D — visual fixes (H8, M7, M8)

D1 alarm on flip-small: subtitle above the buttons, one line ellipsized; snooze label "다시 알림" never "다시".
D2 notification banner and live pill avoid the clock digits on short screens (place in the free band; shrink first).
D3 card-notif XS: count with label ("알림 5"). D4 calendar bars keep their colours in 하늘/라디오 (Ink night map only).
D5 settings: 칸 badges vs card header overlap, keyboard-covered city results (list above keyboard), unsaved dialog dim,
cut "모듈러" label (moot after F), "카드 진하기" 100% vs 55% note.
D6 sample data: 독서 모임 21:00 vs 22:30 contradiction fixed in fixtures.

## Part E — clocks and cards

E1 전광판 portrait A: dot pitch = W/13, hours row over minutes row, unlit dots across the whole screen, digits
centred vertically as one block. Landscape unchanged.
E2 액정 portrait A: one LCD panel full screen; top row weekday (left) + date MM-DD (right); PM indicator; HH row and
MM row 7-seg with ghost segments; bottom row alarm + temp. Landscape unchanged.
E3 하루 막대 portrait C: big time + date/weather line, two "next" rows (next agenda item, next alarm), horizontal day
bar with 0/6/12/18/24 ticks, sunrise/sunset line. Landscape unchanged.
E4 사진 위 시간: face draws a photo from the memories folders (rotating like the photos page interval) behind the
time, with a bottom-to-top scrim for contrast; `Needs.photos` includes "PHOTOS_TIME in myClocks". Empty folders →
"폴더를 골라 주세요" in the face.
E5 내 시계 model (Fable plan check H3): `myClocks` stays the list (clock page swipe list, allowed faces of clock
cards, 이 페이지의 시계 choices). New stored flag `rotateClocks` (codec v13, default = `myClocks.size > 1` on
migration): off = the clock page shows only `myClocks[0]`; clock cards keep using the whole list.
- 시계 screen main grid: tap = make that face first (`MyClocks.add` then move to 0), others kept; a running StandBy
  follows it. 꾸미기 targets `myClocks[0]`.
- "여러 시계 돌려 보기" sub-screen (rotation switch + 모든 시계 grid): picked = tile dimmed + 46 dp centred check
  (CardPickerSheet look, `BoardScreens.kt:555`); tap a picked tile = remove (blocked on the last one with a hint);
  tap another = add at the end; order by drag in the "쓰는 시계" strip. 넣기/빼기 buttons go.
E6 앱 바로가기 icon colour: `SlotOptions.iconStyle` (ORIGINAL default, THEMED), set on the card's own screen
(`apps:board:slot:index`, the only per-slot screen). THEMED: `AdaptiveIconDrawable.monochrome`
(API 33+) drawn in theme accent on `themedSurface`; null monochrome → original bitmap. Card settings show a live
preview of the card with both choices (segmented 원래 색 / 테마 색).

## Part F — settings rework (UX review §5, §6)

F1 Home = large title "Stand Screen", one preview card with page dots + **지금 보기** button (H7), banner only when the
one required permission is missing, then rows with icons + current value: 시계 · 테마 · 페이지와 카드 · 사진 · 밤 화면
· 자동으로 켜기·끄기 · 알림과 알람, then 정보. The 카드 group is dissolved: card settings open from the card's gear in
페이지와 카드; 알람·알림 move to 알림과 알람; 시계 to 시계; 사진 to 사진; 폴드 options into 자동으로 켜기·끄기.
F1b Homes for settings the 카드 group held (Fable plan check H4):
- 시계: 시간 표시 12/24, 세계 시계 도시, **날씨 지역** (global: feeds the info line, 하루 막대 sun times, weather card).
- 페이지와 카드: 처음 보이는 페이지; card-type settings that are global (캘린더 고르기, D-day 목록, 집중·휴식 and 끝나면
  알리기, 수면 소리) open from any card of that type and say "모든 ○○ 카드에 적용".
- 알림과 알람: alarm screen + the four fold alarm rows (안쪽 화면 무드등, 미리 밝히기, 밝아지는 시간, 무드등 색);
  자동으로 켜기·끄기 keeps 펼쳤을 때 only.
- 정보: 권한, 글꼴 라이선스, privacy, version, 처음 설정으로; personal build adds 최근 켜짐 기록.
- Before code: a checklist of every SwitchRow/ControlRow/Segmented/Section today mapped to its new screen (in the plan
  log), so nothing is orphaned.
F2 Preview toolbar (`Preview.kt:280-301`, `LocalPageBar` `Host.kt:90-91`, night caption `:353-357`) removed: the
preview follows the settings window (rotation, fold cover/inner) and falls back to landscape; day/night toggle only
in 밤 화면 and in 꾸미기 (light themes, LUME clocks). Big previews on 시계, 꾸미기, 테마, 페이지, 밤 화면 and card
settings (E6). `onSettle` swipe-to-other-screen removed from 시계 and 사진 (`BoardScreens.kt:267`, `ClockScreens.kt:98`).
F3 Permissions: first run = 4 steps (시계 → 분위기 → 자동으로 켜기 bundle (B4) → 끝/지금 보기). Default-on features
missing a grant show "설정 필요" on their home row (알림과 알람, 자동으로 켜기·끄기, 사진) and a `PermLine` on their
screen: alarm screen gets the listener line, the music card a lock hint, 하루 막대 / agenda clock info the calendar
line. Battery optimisation lives in 자동으로 켜기·끄기. 정보 → 권한 lists all with plain reasons (H6 copy:
full-screen = "화면이 꺼져 있을 때도 바로 켜기", exact alarm = "타이머가 제때 끝나게"). First-run state is saveable
(survives rotation/fold, not activity fields `SettingsActivity.kt:136-142`); shots at flip cover and 780×360.
F4 시계: tap a tile = use it (one tap); "쓰는 시계" strip for rotation is one level down ("여러 시계 돌려 보기");
show 6–8 tiles + 모든 시계 보기 (23).
F5 페이지와 카드: page rows = thumbnail + name + chevron; on/off and order in 편집 mode (drag handles); card rows get a
gear for their settings; delete = one confirm dialog everywhere (pages, themes, D-days); light items get 되돌리기.
F6 Numbers only where they mean something to the user. Words at both ends instead of numbers: 굵기, 크기, 밤 밝기,
밤 감지 (one slider; the exit threshold stays enter + 2, `MoreScreens.kt:143, 148`; the live 밤/낮 pill stays without
the number), 카드 진하기, 모서리, 질감 정도; no hex, no lux, no %. Numbers kept: night schedule hours, 집중/휴식
minutes, 꺼지는 시간, 사진 넘김 간격, 미리 밝히기/밝아지는 시간, 무드등 자동 끄기, D-day dates, version. Choosers:
2–4 = segmented, 5+ = list with check, colours/shapes = swatches.
F7 Copy: one term per concept (추가 / 빼기 / 지우기 / 꾸미기 / 바꾸기 / 글꼴) and the review §4 rewrites, done in place
(no Strings.kt move this round: 77 text-matching test lines). The brand grep covers settings/, platform/, model/, ui/.
F8 정보 gets 처음 설정으로 (confirm dialog; releases persisted folder grants; first run shows again) at the bottom,
privacy policy link, 글꼴 라이선스, version.
F9 Theme editor: basic view = 색 조합 바꾸기 + 배경색 · 카드 모양 · 글자색; the rest under 더 꾸미기; long-press menus →
visible ⋯ button.

Fable plan check (2026-11-17, operator asked for it): High 7 (charge-start needs more than DREAM, default-on features
never ask, 내 시계 tap model, orphaned settings, SAF listing cost, ChargeGate posture, Glow loop) — all folded into
B1, B4, C2, C4, E5, F1b, F2, F3, F6, F7, F8, A1 above.

## Part G — reel v5 assets

After the code: record shots (`-Proborazzi.test.record=true`), rebuild reel assets (`reel-sb/prep5.py`), publish v5.

## Delegation and order

1. A (blockers) + C (behaviour) — Claude, small and risky ones first; C1/C2 pure decisions get tests.
2. B (SAF photos, privacy page, declarations doc) — Claude.
3. E (faces, gallery, icons) — Codex ok for E1–E3 (closed specs with the mockup file), Claude for E4–E6.
4. F (settings rework) — Claude, after the Fable check; largest part, split per screen.
5. D (visual) — with F where screens overlap.
6. Shots, `/done` review, APK + AAB, reel v5.
Commits with `git commit --only <paths>` (shared index with the catch session). One Gradle job at a time.

## Tests (only where wrong is expensive)

Codec v12→v13 migration (photo source, iconStyle, startLandscapeOnly), `ChargeGate.ok`, alarm-detection skip while
`TimerSound.playing`, Glow re-validation decision, ConfigStore backup restore, SAF date parse (EXIF vs modified).
No layout/constant tests. Visual checks = recorded shots per theme × device for the touched screens.

## Done when

- `bundlePublicRelease` builds a signed AAB with id `kr.dot.standscreen`, target 36; personal APK still installs.
- No user-visible "스탠바이"/"StandBy" (grep of strings + shots); no `READ_MEDIA_*` in the public manifest.
- A focus timer ending never opens the alarm takeover (test + one real-phone check).
- The three portrait faces, 사진 위 시간 with folder photos, gallery check, icon colour option visible in shots.
- Settings home = 7 rows + 정보; first run = 4 steps; the F6 word-slider rows show no number; the F1b checklist
  maps every current setting.
- Real phone: charge-start grant matrix recorded (B4); target-36 check on Android 16; ChargeGate axes measured on
  the Fold/Flip.
- Privacy page live; declarations doc written; tester steps written.
- Full test suite green; `/done` review with no High.
