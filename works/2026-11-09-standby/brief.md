# Galaxy StandBy + Fold mood light (mechanics.md known-thing #13)

Picked by the operator on 2026-11-09. Native Android app, personal use plus a channel intro reel. Not a web work:
no manifest.json and no index tile until the operator says so.

Operator decisions, fixed:
- StandBy runs on every Galaxy phone and every aspect ratio (bar, Fold cover and inner, Flip cover). Only the mood
  light (slightly opened inner screen) is Fold-only (2026-11-09).
- StandBy lives on the folded cover screen; alarm = Samsung Clock alarm (2026-10-29).
- Step 1 is a measuring APK, then design mockups after its results (2026-11-09).
- Research every StandBy detail first: `docs/archive/2026-10-29-standby-galaxy/` in C:\DC\DD (01-06 + frames/).

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
