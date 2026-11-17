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

A1. `compileSdk`/`targetSdk` 36: AGP 8.7.3 → **8.10.1**, Gradle 8.10.2 → **8.11.1** (confirm against the AGP
release notes via context7 at implementation; Kotlin, Compose BOM, Robolectric 4.13, Roborazzi 1.29 stay unless the
build fails, then bump the smallest set). Own commit first; if shots break, fix before anything else. Shots stay `@Config(sdk = [34])`, so target-36 behaviour (edge-to-edge opt-out gone, ≥600 dp ignores
orientation/resizability, `screensaver_components` read in `Perms.kt:36`) is checked once on a real Android 16 phone.
A2. Public flavor `applicationId = "kr.dot.standscreen"` (drop `applicationIdSuffix`); personal keeps
`kr.dot.standby`. Delete `src/main/res/xml/dream.xml`; add `src/personal/res/xml/dream.xml` and
`src/public/res/xml/dream.xml`, each with `android:settingsActivity="<its applicationId>/kr.dot.sb.settings.SettingsActivity"`.
`taskAffinity` (`kr.dot.standby.board`) becomes `${applicationId}.board` (manifest placeholder). Both flavors on one
phone: the listener ignores notifications from the sibling package (`kr.dot.standby*`/`kr.dot.standscreen`) for alarm
takeover and charge-start; `Glow.ours()` (`Glow.kt:54`) treats the sibling's glow alarm as ours too (else the two apps
keep pulling each other's alarm earlier); `Perms.kt:36` matches the exact component, not `contains(packageName)`.
A3. Release signing for the **public flavor only** (remove `signingConfig = debug` from `buildTypes.release`,
`build.gradle.kts:46`, because a buildType signingConfig overrides the flavor's; set personal = debug and
public = upload on the flavors); personal release keeps the debug key so the operator's installed
personal app updates in place (a key change would force uninstall and lose `standby_config`). Upload keystore outside
the repo: `%USERPROFILE%\.standscreen\upload.jks` + `upload.properties`, created by `tools/make_upload_key.py`
(python `keytool` call with passwords via a temp file / stdin, never on the command line; random passwords; file
written UTF-8 without BOM; nothing printed). `build.gradle.kts` reads it
when present, else the public release is unsigned. `.gitignore` adds `*.jks`, `*.keystore`, `upload.properties`.
`tools/check_secrets.py` also scans the AAB. Play App Signing holds the app key.
`./gradlew :standby:bundlePublicRelease` produces the AAB.
A4. `usesCleartextTraffic` moves from `src/main/AndroidManifest.xml:32` to a new `src/personal/AndroidManifest.xml`
(`<application android:usesCleartextTraffic="true">`, no `tools:replace` needed once main drops it); public has none.
Tester/operator note: with both flavors installed both react to the charger; keep only one installed for real use.
A5. Version 0.5.0 (versionCode 14).
minSdk stays 34 (Android 14+); the tester invite says so (M10 is an operator check).

## Part B — Play policy (H5)

B1. Photos via SAF (operator decision): drop `READ_MEDIA_IMAGES` and `READ_MEDIA_VISUAL_USER_SELECTED`.
`PhotoSource` becomes folders only: `OpenDocumentTree` (initial URI = DCIM), `takePersistableUriPermission`.
Storage shape: the `PhotoSource` enum and field are deleted (decoder ignores the old key); `photoFolders: List<String>`
now holds tree URI strings (old bucket names dropped in v13 migration); display name via a `DocumentsContract` `COLUMN_DISPLAY_NAME` query (no new dependency). **One folder list** feeds the photos page, 추억 and 사진 위 시간. Photos-page chips set a view filter
`photoFilter: String?` (a tree URI or null = all) and never rewrite `photoFolders` (`StandbyStateHolder.photoTheme`
`:677-678`, `Host.photoTheme`, `BoardScreens.kt:269-274` source picker change accordingly).
- Index: app-wide `object PhotoIndex` (shared by dream, activity and settings preview), built on `Dispatchers.IO`
  once per process start and on folder change: (uri, name, date). Raw `DocumentsContract.buildChildDocumentsUriUsingTree`
  queries; the picked folder's own files + one sub-folder level. Two products from one pass over all rows: the
  slideshow list (newest 5,000 per folder) and a month-day → uris map over **every** row for 추억 (1–15 years ago).
  Rebuilt on folder change and when the StandBy screen opens if the last build is older than 6 h (the listener keeps
  the process alive for days, so "once per process" would hide today's photos). `reloadPhotos`
  (every 45 s, `StandbyStateHolder.kt:468-472, 573-584, 674`) picks from the index, never re-lists; its loop also runs
  when the clock page shows 사진 위 시간 (today `page == 1` only). Skip dot-files (`.trashed-*`, `.pending-*`) and
  sub-folders named Screenshots/스크린샷.
- Date = filename pattern (`YYYYMMDD_HHMMSS`, `IMG_YYYYMMDD…`, `Screenshot_…` skipped) → else `LAST_MODIFIED`; EXIF
  `DateTimeOriginal` read only for the few 추억 candidates.
- Stale grants: a URI is dropped only when it is absent from `contentResolver.persistedUriPermissions` (config
  restored from backup, grant revoked); a query failure (provider error, SD card out) keeps it and shows
  "다시 골라 주세요" until the next successful index. Known limit (tester note): photos copied in without a dated
  filename get their copy date, so 추억 may miss them.
- Photos-page album chips (`PhotosPage.kt:78, 96`, `StandbyStateHolder.photoTheme` `:677-678`) list the picked
  folders by name; `openSettings("photos")` maps to `Routes.PHOTOS` (`SettingsActivity.kt:76`).
- 추억 ("N년 전 오늘") and the 사진 흐림 page background (`BoardBg.PHOTO_BLUR`, `Logic.kt:487`, `Primitives.kt:288`)
  read the same index; the background chooser shows "폴더를 골라 주세요" when the list is empty. A `photoFilter` that is
  no longer in `photoFolders` resets to null. Migration: PICKS/RECENT/old bucket names → empty list → the photos row
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
screen, checked in order. If Play denies FSI (non-alarm app), the public manifest drops `USE_FULL_SCREEN_INTENT` (`tools:node="remove"`;
the code already handles `NO_FULL_SCREEN`) and copy and tester note say "화면이 켜진 채 충전하면 켜져요". Without FSI the
glow features (미리 밝히기, 알람 무드등, 펼치면 무드등 from a dark screen) also only post a heads-up: their rows are hidden in
the public build in that case. B3's declaration states FSI serves alarm-adjacent features (glow before the alarm).
`Perm.DREAM` also checks the screen saver is enabled for "while charging" where readable.

## Part C — behaviour fixes (H1, M1–M6)

C1 (H1). `Listener.kt:61-68`: an alarm seen only on audio (`USAGE_ALARM` playback, `ListenerBus.recompute`
`:66-68`) with no matching clock-app alarm notification (`Logic.kt:376-389`) no longer opens the takeover. That
covers our own timer tone, previews and a sibling flavor's tone without a counter. "No snooze/stop intent" = the matched notification has no action whose title matches the
existing snooze/stop matchers in `ListenerBus`. Alarm takeover hides 다시 알림/끄기 when the source has no
snooze/stop intent and shows only 닫기; `ListenerBus.kt:74-80` no longer launches the Samsung clock as a fallback.
Test: a fake active `USAGE_ALARM` config while `TimerSound.playing` → no takeover (pure decision function).
C2 (M1 + new option). Charge-start gate = **not face-down and not moving** (an upright stand has z ≈ 0, so no "facing
up" rule), with a retry window: sample up to every 2 s for 15 s after the trigger, start at the first still sample.
No proximity rule (many Samsungs have none outside calls). The job path returns `true` + `jobFinished`
(`ChargeStart.kt:188`). Axes measured first on the real Fold/Flip in each posture via the trail log, thresholds set
from that. New option 가로로 놓였을 때만 켜기 (under 무선 충전일 때만, enabled only when that is on): separate check,
gravity mostly along the device x axis. Pure function `ChargeGate.ok(window: List<FloatArray>, opts)` over
`TYPE_ACCELEROMETER` samples, default thresholds (tuned after measurement): moving = magnitude std-dev > 0.6 m/s² over
1 s; face-down = z < −7; landscape = |x| > 7 and |x| > |y|.
- `fire()` must not re-`arm(replace = true)` the same JOB_ID while the job's window runs (`ChargeStart.kt:145`):
  re-arm after the window, right before `jobFinished`.
- Listener path holds a partial wakelock for the window (≤ 15 s, `WAKE_LOCK` added to the manifest, acquire inside
  `runCatching`); job path returns `true` + `jobFinished`.
- One `sampling` flag blocks a second window (listener and job for the same plug-in); `ChargeRules.decide` and the
  `lastLaunchMs` check run again at the still sample, so a user who unlocked meanwhile is not overlaid.
- No sample at all in the window (sensor asleep) → start as today (no gate).
C3 (M2). `Listener.handle` and `publishShade` wrapped in `runCatching` + log; `:185` nullable action title fixed.
C4 (M3). Glow validates at fire time in `GlowReceiver`: after ours fires, `nextAlarmClock` is the user's real one;
skip the glow when it is missing or differs from the stored time (no cancel/re-read loop, `Glow.kt:43-44, 57-67`).
C5 (M4). SleepService requests audio focus (`AUDIOFOCUS_GAIN`, pause on loss, duck on transient) and stops on
`ACTION_AUDIO_BECOMING_NOISY`.
C6 (M5). A charge-started StandbyActivity does not `finish()` when covered by our own mood-light activity: the glow
activity sets a process flag `GlowPresence.covering` in onStart/onStop; StandbyActivity's onStop skips finish while it
is set. Side key while the glow is on top: `GlowActivity.onStop` with the screen off (`PowerManager.isInteractive`
false) calls its own `finish()` and, when the covered StandBy was charge-started, finishes StandbyActivity too via a
`WeakReference` held in `StandbyPresence` (a flow could arrive after StandbyActivity stopped) and updates
`lastHiddenMs`; `ChargeStart.onScreenOff`'s "we turned it off" check (`ChargeStart.kt:127-131`) also counts
`GlowPresence.covering`, so the side key never relaunches StandBy 1.5 s later and the next wake shows the lock screen
(operator rule 11-13); real-phone check. GlowActivity's 다시 알림/끄기 buttons (`Glow.kt:273-274`) follow the C1
rule too.
C7 (M6). ChargeStart's screen on/off receiver returns early when not plugged; no reschedule, no log line.
C8 (Low). `ConfigCodec.decodeOrNull` (throws inside → null); ConfigStore (SharedPreferences) copies the encoded
config to a backup key after every successful save that decodes back, except in a session that started from
defaults after a failed decode; the failed raw JSON is kept in a `broken` key. A load whose decode fails restores the
backup before falling back to defaults.
C9. POST is no longer requested in `StandbyActivity.onCreate` (`:74-75`); "지금 보기" starts through the tile path's
low-battery guard (`StandbyActivity.kt:34-44`) and says why when it refuses ("충전기에 꽂으면 켤 수 있어요").

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
E4 사진 위 시간: face draws a photo from the photo folders (the one list, B1) (rotating like the photos page interval) behind the
time, with a bottom-to-top scrim for contrast; `Needs.photos` includes "PHOTOS_TIME in myClocks". Empty folders →
"폴더를 골라 주세요" in the face.
E5 내 시계 model (Fable plan check H3): `myClocks` stays the list (clock page swipe list, allowed faces of clock
cards, 이 페이지의 시계 choices). New stored flag `rotateClocks` (codec v13, default = `myClocks.size > 1` on
migration): off = the clock page shows only `myClocks[0]`; clock cards keep using the whole list.
- Fresh install: `rotateClocks = true` (default list of 4 rotates, as today). Migration: `myClocks.size > 1`.
- 시계 screen main grid (6 tiles + 모든 시계 보기): with rotation **on**, tap = preview only (as today's `sel`) plus a
  "이 시계 쓰기" button that moves it to 0 (adding it if absent). With rotation **off**, tap = replace `myClocks[0]` (or
  move it to 0 if present); the previous `[0]` stays in the list when a clock card on a page uses it, so a card never
  changes face silently. Nothing else is added, so trying faces never grows the list. A running StandBy follows
  (clock page shows `myClocks[0]` when rotation is off; `MyClocks.page` re-targets). 꾸미기 targets `myClocks[0]`.
  Adding is only in the sub-screen below.
- "여러 시계 돌려 보기" sub-screen (rotation switch + 모든 시계 grid): picked = tile dimmed + 46 dp centred check
  (CardPickerSheet look, `BoardScreens.kt:555`); tap a picked tile = remove (blocked on the last one with a hint);
  tap another = add at the end; order by drag in the "쓰는 시계" strip. 넣기/빼기 buttons go.
E6 앱 바로가기 icon colour: `SlotOptions.iconStyle` (ORIGINAL default, THEMED), set on the card's own screen
(`apps:board:slot:index`, the only per-slot screen), segmented 원래 색 / 테마 색 above the existing card preview of
that slot. THEMED: `AdaptiveIconDrawable.monochrome` (API 33+) drawn in `LocalAccent` on the card's own plate colour
(the theme surface the card already uses); null monochrome → original bitmap.

E7 텐트 무드등 (operator 2026-11-17, reference = Fold stood as a slightly open tent: clock on the outer screen, the
inner screen glowing down onto the table). Galaxy turns the cover screen off once opened, so both screens at once need
Jetpack WindowManager dual-screen mode (`WindowAreaController`, `OPERATION_PRESENT_ON_AREA`,
`presentContentOnWindowArea`, window 1.3.0 already a dependency). Official docs confirm it only on Pixel Fold; Samsung
documents rear display mode (One UI 6.0+) but not dual-screen. So:
- Spike in the measurement APK (step 2): log the rear-area capabilities (`presentOnArea` status) on the operator's Fold
  at closed, tent (~30–60°) and open, plus which display the app runs on at each angle (the angle where the inner
  screen takes over).
- Measured 2026-11-17 on the operator's Fold: PRESENT_ON_AREA = AVAILABLE whenever the app is on the inner display
  (933×704 dp), UNAVAILABLE on the cover. The cover turns off as the inner turns on; the switch happens around the
  flex angle (hinge sensor reports only 0°/90°), so the tent must be opened to ~80–100° (flex), not 30–60°.
  The reel's tent opening follows that (~90°).
- If PRESENT_ON_AREA is AVAILABLE (it is, on the operator's Fold): when the mood light runs on the inner screen, present the clock face
  (`myClocks[0]`, night colours) on the outer display; ends with the mood light (`onSessionEnded` handled; the system
  dialog appears when it asks). Setting under 자동으로 켜기·끄기 → 폴드: "무드등 켤 때 바깥 화면에 시계".
- If not available: report to the operator with the measured angles; the reel shows only what the app can do.

E8 Mood light options (operator 2026-11-17, mockup https://claude.ai/artifact/WhzeZkLiykCEK5uL4fmVDN): the whole inner
screen one flat colour (no top→bottom gradient, `StandbyRoot.MoodLight` + `Glow.kt` glow screen), brightness slider up
to the panel maximum (window brightness override), colours = existing GLOWS + 분홍/보라/하늘, effect 그대로 / 색 바뀜
(eases through the palette) / 숨쉬기 (slow dim–bright) / 촛불 (gentle flicker), 빠르기 slider for effects. Stored in
config (codec field `mood`: bright, color, effect, speed). The same controls appear in settings (폴드) and in a
pull-down panel on the cover-screen clock (E7 presentation), so tent users adjust from the outside. That panel keeps
everything the normal pull-down panel has (brightness, volume, theme / 내 테마) and adds the mood section; operator
2026-11-17: "무드등이 켜져 있어도 원래 있던 밝기·음량·테마 선택은 같이 있어야 해".
Copy under 펼치면: "90° 넘게 펼치면 안쪽 화면이 무드등으로 켜져요. 켜진 뒤에는 다시 좁혀서 텐트처럼 세워도 계속 켜져
있어요." (operator: once on, narrowing keeps it on).

E10 Remember everything across close/reopen (operator 2026-11-17: "모든 페이지, 카드가 기억되는 거 맞아?"). Root cause
of the report: `BoardView.kt:161` `LastCard.keyOf` returns null for the notification card on purpose, so reopening shows
the card before it. Fix: store NOTIF like any card (drop the exclusion and its comment `:154`, update `LastCardTest.kt:24`
to expect it remembered). Also remember the clock page's current face when rotation is on (`StandbyRoot.kt:145`, same
`last_card` prefs, key "clock-face"). Pages are already stored (`startPage`).
E9 decisions (operator 2026-11-17, mockup https://claude.ai/artifact/6ztnkSgYkHJ2MqdMHQ4tL5): all 12 items as drawn;
전광판 near-square = **A** (one row, as large as the width allows); 입체 시계·입체 네모 get a "날짜·요일" switch (style
showInfo, like 입체 디지털's 날짜 창; default off) and show weekday/date in the empty top/bottom only when it is on.
E9 Clock layouts in every frame (operator: 스티커·공 숫자·LED·테두리 초 portrait not optimised; check all faces in
landscape/portrait full screen, near-square, split slots and cards) — fix list comes from the Fable audit
(scratchpad clock-audit.jpg), applied after E5–E8.

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
- Step 0 of Part F (before code): `settings-r10/r15-settings-map.md` = every SwitchRow/ControlRow/Segmented/Section/
  NavRow today (file:line) → its new screen; reviewed in the F diff review, so nothing is orphaned.
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
F4 시계: E5 rules; 6 tiles (디지털, 플립, 아날로그, 한글 시계, 전광판, 가는 선) + 모든 시계 보기 (23); "여러 시계
돌려 보기" one level down. Row icons (F1) come from the existing `Ic` set (`ui/cards/Icons.kt`).
F5 페이지와 카드: page rows = thumbnail + name + chevron; on/off and order in 편집 mode (drag handles); card rows get a
gear for their settings; delete = one confirm dialog everywhere (pages, themes, D-days); light items get 되돌리기.
F6 Numbers only where they mean something to the user. Words at both ends instead of numbers: 굵기, 크기, 밤 밝기,
밤 감지 (one slider; the exit threshold stays enter + 2, `MoreScreens.kt:143, 148`; the live 밤/낮 pill stays without
the number), 카드 진하기, 모서리, 질감 정도; no hex, no lux, no %. Numbers kept: night schedule hours, 집중/휴식
minutes, 꺼지는 시간, 사진 넘김 간격, 미리 밝히기/밝아지는 시간, 무드등 자동 끄기, D-day dates, version. Choosers:
2–4 = segmented, 5+ = list with check, colours/shapes = swatches.
F7 Copy: `settings-r10/r15-copy.md` (terms, rewrites, slider word pairs), done in place (no Strings.kt move this
round). Text-matching tests are updated in the same commit as the copy they match. Brand grep covers all of `src/**`
(kt + res + manifests); allowed: package ids, code identifiers and comments.
F8 정보 gets 처음 설정으로 (confirm dialog; releases persisted folder grants; deletes `perm_walk_done` and bypasses the
`fresh` install check in `PermWalk.starts`, `Perms.kt:113`, so first run shows again) at the bottom,
privacy policy link, 글꼴 라이선스, version.
F10 Tips (operator 2026-11-17, mockup https://claude.ai/artifact/ESndgkT2kxVhzg1DECTmBL): ① one-time in-place tips
(one per session, edge pill 4 s, never repeats; stored as a seen-set in config): first StandBy → pull-down bar nudge
"↓ 위에서 끌어내리면 테마·밝기"; first timer card → press ripple "길게 누르면 집중 ↔ 휴식"; first tent → cover screen
"↓ 무드등 밝기·색"; plus the other hidden gestures (clock page up/down + long-press, board long-press, stacked cards,
photos up/down, banner swipe up, notif swipe-left, clock card face choice, side key → lock screen next). ② 설정 › 팁
(above 정보): grouped by Stand Screen 화면 / 카드 / 폴드, a small looping gesture drawing per row, '새' until used,
tap = demo in the preview. ③ one footer line under the related setting (timer, theme, fold, …), gesture in bold.
F11 Full copy pass (operator 2026-11-17: "앱에 있는 모든 문구 정돈"): every user-visible string in `src/**` (kt + res)
listed in `settings-r10/r15-copy-all.md` (file:line → now → new), style rules at its top (해요체, plain words, one term
per concept, brand "Stand Screen", no raw numbers per F6); the operator skims the table before it is applied; applied
together with F7 (same commit per screen, tests updated with the copy).
F0 Look rule (operator 2026-11-17: "아이폰을 베낀 건 아닌지"): keep the app's own Android identity — Material3 switches
in the theme accent (never iOS green #34C759), row icons = accent line glyphs without coloured tiles (no iOS Settings
squircles), back = Android arrow + title (no "‹ 설정" text), segmented = the existing accent pill. The r15 mockup's
iOS-looking bits (green switches, tile icons, ‹ back) are not to be copied.
F12 Calendar picker (operator 2026-11-17 picked **B**, mockup https://claude.ai/artifact/R1vjijdHCxdnRc7uJPJAur): in
오늘 일정 · 달력 settings, one summary line "켠 캘린더의 이번 주 일정 N개", calendars grouped by account (phone /
each Google account / subscribed: holidays, birthdays) with colour dot, name, "이번 주 N개" and a switch; tapping a
row expands that calendar's events for the next 7 days (date · title · time); per-account 모두 끄기/켜기. Reads the
phone's CalendarContract (existing calendar permission path).
F9 Theme editor: basic view = 색 조합 바꾸기 + 배경색 · 카드 모양 · 글자색; the rest under 더 꾸미기; long-press menus →
visible ⋯ button.

Reviews: Fable plan check (2026-11-17, operator asked for it): High 7 (charge-start needs more than DREAM, default-on features
never ask, 내 시계 tap model, orphaned settings, SAF listing cost, ChargeGate posture, Glow loop) — all folded into
B1, B4, C2, C4, E5, F1b, F2, F3, F6, F7, F8, A1 above. /plan-review round 4: **No High**; Medium 3 folded (clock tap vs saved list, glow closes itself, FSI also gates glow).
/plan-review round 3: High 1 (side key under the glow relaunched StandBy) + Medium 4 (job re-arm, sibling audio,
backup overwrite, FSI fallback) folded.
/plan-review round 2: High 2 (memories cap, WAKE_LOCK) + Medium 8 folded (signing precedence, index refresh,
sibling glow, photo-blur bg, playing counter, glow side key, first-run reset, measurement APK early).
/plan-review round 1 (4 lenses): High 13 merged → A2–A4
manifests and signing per flavor, B1 storage shape and chip filter, C2 wakelock/re-entry, C6 flag, E5 tap rule,
delegation all-Claude, done-when gaps — folded in. Scope stays whole (operator: "모두 진행"), but A–D land and are
committed before E–F start, so a slow F never holds the release fixes.

## Part G — reel

After the code: record shots (`-Proborazzi.test.record=true`), rebuild reel assets (`reel-sb/prep5.py`), render the
final 1080×1920 30 fps video from `v6.html` (adds the fold mood-light shot), music + SFX, mp4 to the operator.

## Order (all Claude — operator 2026-11-17: no Codex)

1. A1 build bump alone → shots green.
2. A2–A5, C1–C9 (C1/C2/C8 tests first) → commit → **measurement APK** to the operator: B4 grant
   matrix + ChargeGate axes on Fold/Flip, written to a separate `measure` log key (the charge log keeps 8 lines,
   `ChargeStart.kt:113`), shown in 정보 (personal build). Results set C2 thresholds and the F3 step-3 copy before F.
3. B1–B3 (+ migration tests) → commit. B2 page pushed to dot-lab (Pages = live on push).
4. D → commit.
5. E1–E6 → commit.
6. F step 0 map → F1–F9 per screen → commit per screen.
7. Final real-phone round (one checklist, one APK): timer end never opens the alarm screen, glow + side key,
   personal APK updates in place, target 36 on Android 16 if a phone has it.
8. Full suite, `/done` (fresh Claude reviewers, diff input), APK + AAB (`check_secrets` on both), reel video.
Parallel subagents only in a git worktree with `SB_BUILD_DIR` set; their diffs are read before commit. Commits with
`git commit --only <paths>` (shared index with the catch session), `git diff --staged --stat` read first. One Gradle job
at a time. Lows in scope: C8, A4 only.

## Tests (only where wrong is expensive)

Codec v12→v13 migration (old bucket names/PICKS/RECENT → empty `photoFolders`; `rotateClocks` from list size),
`MyClocks` tap rule and last-clock block, `ChargeGate.ok`, audio-only alarm ignored without a clock notification,
Glow fire-time decision, ConfigStore backup restore, photo date parse (filename patterns vs modified), stale-grant
rule (absent from persisted permissions → drop; query error → keep).
No layout/constant tests. Visual checks = recorded shots per theme × device for the touched screens.

## Done when

- `bundlePublicRelease` builds a signed AAB with id `kr.dot.standscreen`, target 36; `check_secrets` passes on APKs
  and the AAB; the upload key lives outside the repo; the personal APK updates over the installed one (operator).
- No user-visible "스탠바이"/"StandBy" (grep of strings + shots); no `READ_MEDIA_*` in the public manifest.
- A focus timer ending never opens the alarm takeover (test + one real-phone check).
- The three portrait faces, 사진 위 시간 with folder photos, rotation-screen check, icon colour option visible in shots.
- Settings home = 7 rows + 정보; first run = 4 steps; the F6 word-slider rows show no number; the F1b checklist
  maps every current setting.
- Real phone (step 7): grant matrix recorded and reflected in first-run copy and the tester note; ChargeGate axes
  measured; target-36 check where an Android 16 phone exists.
- Privacy page live; declarations doc written; tester steps written (Google Group, Android 14+, delete the old `kr.dot.standby.pub` test app, 12 testers ×
  14 days before production for a new personal account).
- Full test suite green; `/done` review with no High.
