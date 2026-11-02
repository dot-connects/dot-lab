# 주차 층 측정 드래프트 (T-0184)

Android measurement app. Question: does a phone barometer read the floor correctly, first on foot today, then in a car tomorrow? Research: `docs/archive/2026-11-01-lab-references/06-parking-app.md`, `07-parking-improvements.md` in C:\DC\DD.

Operator decisions, fixed:
- Claude implements and reviews, no Codex.
- Today: test floors on foot, no car. Tomorrow: car.

Plan review round 1 narrowed the build. One app with two modes, one APK, built today. The Bluetooth parked state machine, the transparent widget, learned places, pillar OCR and sharing wait until real data shows the barometer is good enough. Reason: every timing rule is unmeasured, and a failed background service would lose tomorrow's data. The product design stays mockup round 3, direction 3 (https://claude.ai/artifact/HhbY5kbwG4zEdohqfAYohb).

Not published. No `manifest.json`, no index tile, no post. This folder is not a lab work until the operator says so.

The app is installed by APK through SendUserFile. There is no adb and no device here, so every device behaviour is checked by the operator.

## Screen

One Activity, plain Views.

Start card:
- Sensor check. If `TYPE_PRESSURE` is missing, show `기압계 없음` and disable start.
- Text field `장소` (building name, required, saved in the CSV).
- Mode: `도보` or `차`.
- Floor list `지금 층`: B6..B1, 1층..20층, default 1층.
- Button `여기서 시작`.

Running card:
- Estimated floor in large text, height from baseline in metres, pressure in hPa, elapsed time, sample count.
- Floor grid. Tapping a floor records the true floor.
- `기준 다시`: resets the baseline at the last tapped floor, or at the start floor if none was tapped.
- Car mode only: `주차함` button.
- `끝내기`.

`주차함` opens the floor grid. Choosing a floor writes the `park` row with `true_index`, so every park can be scored. In car mode `끝내기` stays disabled for 2 minutes after the last `park`, so the Bluetooth disconnect is captured.

Finished state: `기록 보내기` shares the session CSV through the share sheet. A list of earlier sessions allows re-sending.

The Activity holds no session state. `MeasureService.current` (a companion property, null when idle) exposes the running session. On resume the Activity shows the running card if a session exists, and refreshes it once a second by `Handler`. Tapping the notification reopens the Activity with `singleTop`. Start is refused while a session exists, so there is only one CSV writer. `configChanges` covers orientation, screenSize, screenLayout and smallestScreenSize for the Fold. Root view uses `setOnApplyWindowInsetsListener` to pad for system bars, because targetSdk 35 is edge-to-edge.

## Measurement service

`MeasureService`, foreground, started only from a button tap in the Activity, after the permission callback returns. `START_NOT_STICKY`: if the process dies the session ends and the CSV written so far survives. No auto restart, no boot receiver.

Manifest `foregroundServiceType`: `specialUse|location`. `startForeground` passes `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`, plus `FOREGROUND_SERVICE_TYPE_LOCATION` in car mode when a location permission is granted and `LocationManager.isLocationEnabled()` is true. A `SecurityException` from that call falls back to `SPECIAL_USE` alone and the running card shows `GPS 없음`. Includes `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = `barometric floor measurement`.

Holds a `PARTIAL_WAKE_LOCK` for the session, timeout 3 hours 1 minute, released in `onDestroy`. A `Handler` timer at 3 hours writes the `end` row and calls `stopSelf`.

Sensor: `TYPE_PRESSURE` default sensor, `registerListener` with 200000 microseconds. Times come from `event.timestamp`. Each second, the median of that second's readings becomes one `sample` row. If a second has no readings, no row is written, so gaps are visible in the CSV.

Baseline: median of samples for 5 seconds after start or `기준 다시`. Until then the estimate shows `측정 중`.

Car mode also requests GPS through `LocationManager.GPS_PROVIDER` at 1 s, wrapped for `SecurityException`, and writes the last fix on each sample row. The running card shows fix age and accuracy, so a missing GPS is seen during the test. It registers a receiver with `RECEIVER_EXPORTED` for `ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED` and `BluetoothAdapter.ACTION_STATE_CHANGED` when `BLUETOOTH_CONNECT` is granted, and logs them as `bt` rows with device name and no MAC. The rows only record events and change nothing. The running card shows a `bt` count, so toggling Bluetooth once before driving proves the rows are written.

Permissions asked at `여기서 시작`: `POST_NOTIFICATIONS` always. In car mode also fine and coarse location, and `BLUETOOTH_CONNECT`. If denied, the session runs and the CSV shows empty columns.

Manifest permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.

## Floor maths

`FloorMath.kt`, pure Kotlin, in `app/src/main`, tests in `app/src/test`.

- `heightM(p, p0) = 44330 * (1 - (p / p0).pow(1 / 5.255))`. Lower pressure gives positive height.
- Index: 1층 = 0, 2층 = 1, B1 = -1, B2 = -2.
- `labelOf(0) = "1층"`, `labelOf(1) = "2층"`, `labelOf(-1) = "B1"`, `labelOf(-3) = "B3"`.
- `indexOf("B2") = -2`, `indexOf("3층") = 2`. Round trip is tested over B6..20층.
- `estimate(baseIndex, deltaM, floorM) = baseIndex + floor(deltaM / floorM + 0.5)`. A tie rounds up, and a test pins it.
- `median(list)`: pure, even length takes the mean of the two middle values.
- `const val FLOOR_HEIGHT_M = 3.3`. One value. Every row stores it, so analysis can refit.

Tests: known pressure pairs at a 3.3 m step, B1 to 1층 adjacency with no zero floor gap, the round trip, the median.

## CSV

File `session-<yyyyMMdd-HHmmss>-<mode>.csv` in app files dir. Written through one `BufferedWriter`, flushed after every row. UTF-8 with BOM. Every text field uses RFC 4180 escaping through `FloorMath.csv()`, tested with a comma and a quote.

Columns, in order:
`type,t_s,wall,place,mode,pressure_hpa,base_hpa,base_index,est_index,est_label,delta_m,floor_m,true_index,true_label,lat,lng,acc_m,fix_age_s,note`

`type`: `start`, `base`, `sample`, `label`, `park`, `bt`, `end`.
`t_s` comes from `event.timestamp`, or `SystemClock.elapsedRealtimeNanos()` for rows not from a sensor event, as seconds since session start.
`label` rows use the median of the last 3 samples. `bt` note holds action and device name.

Pass reading, from real data. Scored rows exclude labels on the baseline floor taken before the first floor change, because they are correct by construction.
- At least 90% of scored labels correct across at least 2 buildings and 12 scored labels: the barometer alone is good enough, go on to the widget.
- 70% to 90%: needs per-place correction.
- Under 70%: floor comes from the pillar scan, with the barometer as a hint.
- Drift: estimate on returning to the start floor.

## Test protocol

On foot, one APK check first: start on a known floor, tap each floor when stopped, return to the start floor, end. One run keeps the screen off with the phone in a pocket for at least 10 minutes. Gaps in `sample` rows are reported.

In the car: start at the place, in car mode, before entering. Stop at the entrance, tap `1층` or the floor at street level, then `기준 다시` while stopped. Drive in, park, tap `주차함` and the true floor, switch off, wait 2 minutes, end. A human-set baseline is more favourable than the automatic one the product needs, so car results are an upper bound. A second run from a session started at home measures the automatic case from the GPS loss point in the CSV.

## Round 3 additions

- Session list sits on the start card. Sessions left by a dead process can be sent from there.
- Notification: channel low importance, built-in small icon, `PendingIntent.FLAG_IMMUTABLE`. `startForeground` runs before file and sensor work.
- The start button disables on tap, and `onStartCommand` also ignores a start while `current` is set.
- Before testing, set app battery to `제한 없음`.
- Car order: switch off, get out, wait 2 minutes, `끝내기`. After `기준 다시`, wait until `측정 중` clears before moving.
- `끝내기` asks for confirmation in the page. `마지막 취소` writes an `undo` row for the last label or park.
- A label with no sample in the last 5 seconds leaves `est_index` empty.
- Scoring includes `park` rows, excludes `undo`ne rows and labels taken just before `기준 다시`.
- Crash handler is installed in `Application.onCreate` and calls the previous handler.

## Crash visibility

`Thread.setDefaultUncaughtExceptionHandler` writes the stack to `crash.log`. Next start shows it with a copy button. R8 off, so stacks stay readable and no mapping file is needed.

## Project

- Folder: `dot-lab/works/2026-11-02-parking-floor/app/`. Single module `:app`. Copy Gradle wrapper, `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties` shape from tap-trigger, minus `:core` and `:pc`.
- Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, targetSdk 35, minSdk 33.
- `namespace` and `applicationId` `kr.dot.parkingfloor`, `versionCode` 1, `versionName` `0.1.0`, label `주차층`.
- Dependency: `androidx.core:core-ktx:1.13.1` for `FileProvider`, `android.useAndroidX=true`. JUnit for tests.
- Manifest `<provider>` for `androidx.core.content.FileProvider`, authority `kr.dot.parkingfloor.files`, `exported=false`, `grantUriPermissions=true`, `res/xml/file_paths.xml` with `<files-path name="sessions" path="."/>`. Share intent sets `FLAG_GRANT_READ_URI_PERMISSION`.
- Signed with the Android debug key from `~/.android/debug.keystore` through `signingConfigs.getByName("debug")`. No key or password goes in the repo.
- Build output in `%LOCALAPPDATA%/parking-floor-build`, same reason as tap-trigger.
- `.gitignore`: `build/`, `.gradle/`, `local.properties`, `*.apk`, `*.csv`.

## Privacy

Session CSVs from car tests hold GPS positions. Received CSVs go to `C:\DC\DD\docs\archive\2026-11-02-parking-floor\`, outside every git repo, never in `dot-lab/`, which is a public site. Analysis results are reported as totals. This brief and the app source go live with the dot-lab push, and neither holds personal data.

## Steps

1. Gradle project, crash log, `FloorMath` tests.
2. Service, CSV, screen. Build `gradlew test assembleRelease`. Commit.
3. Independent Claude review of the diff, until no High, max 5 rounds. Fix. Rebuild.
4. Deliver APK with short test steps. Commit and push. Update `mechanics.md` line for the idea and the task.
5. Operator tests on foot. Claude reads the CSV, reports accuracy and metres per floor.
6. Operator tests in the car. Claude reads the CSV and reports the same, plus Bluetooth event timing against the `주차함` tap.
7. Plan the next round from the data.

## Done when

- `gradlew test assembleRelease` passes and the review has no High.
- Foot test CSVs cover at least 2 buildings and 12 scored labels, with accuracy and metres per floor reported against the pass reading, plus sample gaps with the screen off.
- Car test CSVs cover 5 parks, at least 3 underground, with accuracy reported and the Bluetooth disconnect time compared with `주차함`.
- A written decision: widget next, per-place correction, or pillar scan first.

## Later (decided 2026-11-03, not in the measurement app)

- Absolute floor: phone pressure + KMA AWS station pressure + ground height (DEM) + building height and floor
  count (V-World LT_C_BLDGINFO, building register). Keys via `dot-lab/scripts/wizard-parking-keys.bat`, stored in
  `%USERPROFILE%\.dot-lab\parking.env` (outside git).
- Entrance anchor: the pressure when GPS drops on the way in marks the entrance level.
- Design round 4: widget sizes 1x1 to 4x3, app screens, family sharing.
- Family sharing: register a car, invite family by link or QR (no account), whichever phone sees the car
  Bluetooth records the park, every family widget shows it. Sync via the dot-lab Cloudflare Worker; instant
  widget refresh needs FCM. Location-law status for sharing is unverified.
