# Galaxy StandBy + Fold mood light (mechanics.md known-thing #13)

Picked by the operator on 2026-11-09. Native Android app, personal use plus a channel intro reel. Not a web work:
no manifest.json and no index tile until the operator says so.

Operator decisions, fixed:
- StandBy runs on every Galaxy phone and every aspect ratio (bar, Fold cover and inner, Flip cover). Only the mood
  light (slightly opened inner screen) is Fold-only (2026-11-09).
- StandBy lives on the folded cover screen; alarm = Samsung Clock alarm (2026-10-29).
- Step 1 is a measuring APK, then design mockups after its results (2026-11-09).
- Research every StandBy detail first: `docs/archive/2026-10-29-standby-galaxy/` in C:\DC\DD. Canonical merge:
  `00-standby-canon.md` (2026-11-09, 20 open unknowns in section 9; sources 01-07 + frames/).

- The operator has no iPhone (2026-11-09). Every mockup screen shows the real iPhone version next to ours: a frame
  from `frames/` (or a source link), what it does, and the measured numbers, with unknowns marked as our choice.
- The 20 open unknowns in the canon stay open; where one matters, the mockup states the value we chose.

## Step 1: measuring APK (`app/`, package `kr.dot.standby.probe`)

Answers four questions from I-0053 plus the screen shape for the all-ratio layout:

| Question | Where it is logged |
|---|---|
| Does the screensaver (DreamService) show on the folded cover screen? | `dream_*` rows with the display it landed on |
| From what hinge angle does the inner screen take over, and does our window follow? | `hinge`, `display_*`, `dream_config`, `app_fold`, `app_config` |
| Can we catch the moment a Samsung alarm rings? | `alarm_audio_start` (USAGE_ALARM playback), `notif_posted` from the clock package, vendor `broadcast` rows, `nextAlarm` |
| Overnight cost | `battery` every 5 min: level, temperature, plug, current, thermal status |
| Can an alarm open a full-screen mood light over the lock screen? | `glow_*` rows from the 15 s test |

Media sessions are logged too (`media_sessions`), for the music page later.

One CSV (`events.csv`: time, kind, detail), shared from the app. No wake lock, so gaps while the CPU sleeps are data.
Build: `JAVA_HOME=C:/DC/tools/jdk-17`, `gradlew assembleRelease`; output in `%LOCALAPPDATA%\standby-build`.

Operator test steps:
1. Install, press permission buttons 1-5 (pick "StandBy 측정" as the screen saver, start "while charging").
2. Press 기록 시작.
3. Fold, charge: does the clock show on the cover? Open slowly to about 30, 60, 90 degrees, leave a note at each.
4. Set a Samsung Clock alarm 2 minutes ahead and let it ring.
5. 무드등 15초 뒤 켜기, then fold or lock the phone.
6. Leave it charging overnight, then CSV 보내기.

## Run 1 (2026-11-09 19:12-19:14, Fold8, not charging, 2 minutes)

| Question | Result |
|---|---|
| Dream on the folded cover screen | **Yes.** Started from the screen saver settings (preview), shown on the cover: display id 0, 1248x1972 px, 475x751 dp, ratio 1.58, top cutout 104 px. Auto-start while charging not tested yet. |
| Screen shapes | Cover 475x751 dp (1.58), inner 932x704 dp (1.32, no cutout). Samsung keeps display id 0 and swaps the panel; the other panel shows as id 1, OFF. |
| Full-screen mood light over the lock screen | **Yes.** `canUseFullScreenIntent=true`; the glow opened on the inner screen before unlock (glow_shown 19:13:21, USER_PRESENT 19:13:26). |
| Our window across fold/unfold | **Kept.** Folding moved the open glow activity to the cover screen (config 475x751 dp) without closing it. |
| Hinge angle | Only 0.0 and 90.0 logged; the inner screen turned on before the 90 event. Either the sensor is coarse or 0.1.0 throttled it. 0.1.1 logs every value and puts the angle in each note. |
| Music page | Feasible: YouTube Music session with title, artist, album art, transport actions. |
| Samsung alarm moment, overnight battery | Not run yet. |

0.1.1: every hinge value, angle in notes, display changes that only differ in refresh rate (48/60/120 Hz) dropped.
