# plan-r16 — screen stays lit / flickers (2026-11-20)

Plan only, no code. Scope: the "phone screen lit all night" loop and audit candidates 1-19. Operator decisions A (real
panel off), B (start only on plug-in once / used-then-off), C (never lit or flickering by our doing) are fixed inputs.

Paths: `S` = `app/standby/src/main/java/kr/dot/sb`, `T` = `app/standby/src/test/java/kr/dot/sb`.

## 0. Operator decisions 2026-11-20 (override the sections below where they differ)

- **Q1 = lock screen.** Any screen-off ends the session: our own off (flag cleared → system timeout) and the side
  key alike. There is no SLEEPING session and no armed/unarmed state: drop `armed`, `start(armed)`, the unarmed
  wake and "session survives its own off" (architecture 4-5). The next StandBy comes only from the two start
  triggers (plug-in once; unlock → screen off). `keepScreenOn(off, showNow) = !off && !showNow`.
- **Q2 / Q5 / Q6 accepted** as written (no single-tap wake from a really-off panel — say so in settings copy; black
  → panel off takes the phone's own timeout; the dream stub stays for good).
- **Q3 = night max brightness stays 30 %** (no default change). The heat-cap fix in T7 still applies.
- **Q4 = ceiling.** Display AUTO ("방이 어두우면") in a bright room: off after 10 min without a touch
  (`BRIGHT_IDLE_MS = 600_000`). NEVER ("끄지 않기") stays on by the user's choice.
- User-facing name is "스탠드 스크린" (never 스탠바이) in any new copy.

## 1. Android semantics (what is verified, what is not)

Sources read 2026-11-20 from `android.googlesource.com/platform/frameworks/base` (`refs/heads/main`; the
`handleSandman` logic was also checked on `android14-qpr3-release`, same code at lines 3443-3454). Line numbers = main.

| # | Fact | Source | Status |
|---|---|---|---|
| F1 | Screen timeout while AWAKE: `updateWakefulnessLocked` → if `shouldNapAtBedTimeLocked()` (`screensaver_activate_on_sleep`, or `activate_on_dock` while docked) → `dreamPowerGroupLocked`, else doze. | `PowerManagerService.java` 3306-3351 | verified |
| F2 | When a dream ends (`handleSandman`, wakefulness DREAMING, `!isDreaming`): `isItBedTimeYetLocked()` true → **doze** (screen off); false → `wakePowerGroupLocked(... WAKE_REASON_DREAM_FINISHED, "android.server.power:DREAM_FINISHED")`. | PMS 3445-3545 | verified |
| F3 | "Bed time" = not `isBeingKeptAwakeLocked`: stay-on, proximity, `WAKE_LOCK_STAY_AWAKE`, brightness boost, or user-activity summary BRIGHT/DIM. `WAKE_LOCK_STAY_AWAKE` is only inferred while AWAKE, so a dream's own `FLAG_KEEP_SCREEN_ON` does not count. | PMS 3380-3387, 2835-2857 | verified |
| F4 | User-activity summary is BRIGHT/DIM while `now < lastUserActivity(+NoChangeLights) + screenOffTimeout`; `screenOffTimeout = min(setting, window-manager override, ...)`. | PMS 3000-3040, 3256-3271 | verified |
| F5 | A DREAM_FINISHED wake pokes user activity with `NO_CHANGE_LIGHTS` at the wake time. | PMS 733-746 | verified |
| F6 | The lock screen shortens the timeout through the hidden window attribute `userActivityTimeout` (AOSP 10 s; ~5 s seen on One UI); the override disappears when the keyguard is occluded (a dream or a show-when-locked activity on top). | `WindowManager.LayoutParams.userActivityTimeout` (hidden field, WindowManager.java 4538) verified; SystemUI side **from memory, not re-read** | partly |
| F7 | `DreamService.finish()` → `finishSelf` → `stopDreamLocked`; the comment in `finishSelfInternal` says a self-finish is not user activity and "the device may simply go to sleep". No wake call on this path. | `DreamManagerService.java` 548-565; `DreamService.java` 1301-1345 | verified |
| F8 | `setScreenBright(b)` only toggles `FLAG_KEEP_SCREEN_ON` on the dream window (default true). `startDozing()` works only when `mCanDoze` (the system doze component); a third-party dream cannot doze or set the doze screen state. | DreamService 837-843, 919-926 | verified |
| F9 | A non-interactive dream calls `wakeUp()` on any key/touch → `finish()`; the touch is user activity → F2 wakes. | DreamService 455-530 | verified |
| F10 | If the selected dream component is missing/invalid, DreamManagerService falls back to the **default dream** (`config_dreamsDefaultComponent`); it does not turn the screen saver off. | DMS 683-713 | verified |
| F11 | `screensaver_enabled`, `screensaver_components`, `screensaver_activate_on_sleep`, `screensaver_activate_on_dock` are `@hide` but `@Readable` (apps may read them by key). The operator's log proves the first and third are readable on the phone; `Perms.kt:60` already reads the second. | `Settings.java` 10414-10443 | verified |
| F12 | `BRIGHTNESS_OVERRIDE_OFF = 0.0f` = "backlight set to the lowest value when this window is in front". It does not turn the panel off. | WindowManager.java 4012-4017 | verified |
| F13 | Releasing `FLAG_KEEP_SCREEN_ON` pokes user activity (the window manager's screen lock is `ON_AFTER_RELEASE`; PMS 1864-1870 handles the flag), so the screen goes off one full screen-timeout **after** the flag is cleared. | PMS side verified; that WM's lock carries `ON_AFTER_RELEASE` is **from memory** | partly |
| F14 | An app without DEVICE_POWER / device admin / accessibility has no API to turn the panel off. The only route is: hold nothing, let the system timeout run. | no public API (`goToSleep` is hidden + signature) | verified by absence |
| F15 | One UI: no public source; one web search found no Samsung-specific "dream finish wakes" report. | - | unknown |

### Why the phone looped (hypothesis H1, fits F1-F6 and the 5 s period; not confirmed on device)

```
lock screen lit (system wake)        timeout = keyguard override (~5 s)
   │ 5 s, no activity → nap (F1)
   ▼
our dream starts, covers keyguard    override gone → timeout = phone setting (30 s+)
   │ summary is BRIGHT again (F4): "being kept awake"
   │ dream refuses at once → finish()
   ▼
not bed time (F3) → wake DREAM_FINISHED (F2) → lock screen, activity poked (F5) → 5 s → nap → ...
```

So the 11-18 note ("a refused dream looks like the screen going off") holds only when the dream starts from an
unlocked app timeout (no override). Stock logic, not necessarily a Samsung deviation. H1 predicts that a dream which
waits `Settings.System.SCREEN_OFF_TIMEOUT` + margin before `finish()` dozes instead (T12 measures; the fix does not depend on it).

### Answers to the four questions

- **(a) Decline without waking:** no reliable way. `finish()` wakes whenever user activity is still "fresh" (F2-F6),
  which is always the case for an instant refusal started from the lock screen. A dream must never refuse by
  `finish()`.
- **(b) Real panel off while our dream is the screen saver:** impossible in general. Timeout → dream again (F1);
  a third-party dream cannot doze (F8); no sleep API (F14). Real off needs the screen saver to be off (or not ours)
  so that a timeout leads to doze.
- **(c) Black dream at minimum brightness vs sleep:** not equivalent. Wakefulness stays DREAMING: display power state
  ON, touch on, no suspend, and an LCD backlight stays lit (F12). On OLED the pixels are dark but nothing sleeps.
  Watts were not measured.
- **(d) Detect "we are the screen saver":** yes (F11). Guide with `Settings.ACTION_DREAM_SETTINGS` (already in
  `Perms.kt:76`).

## 2. Architecture

1. **The dream is no longer an entry path.** B forbids StandBy after system wakes, and a dream cannot tell why the
   screen was on nor refuse safely (a). `StandbyDream` becomes a **stub**: black, `isScreenBright = false`,
   `isInteractive = false`, window brightness `Brightness.OFF`, never calls `finish()`. It stays in the manifest only
   because removing it would hand existing users the default screen saver (F10).
2. **Screen saver must be off for real off.** Settings shows a warning when `screensaver_enabled = 1` and
   `screensaver_components` contains our package: "화면 보호기를 꺼야 화면이 완전히 꺼져요" → dream settings. Until the
   user does it, the stub keeps the old black-window behaviour (no loop, no flicker, but no real off).
3. **One host: `StandbyActivity`.** Keep-screen-on follows the state: `FLAG_KEEP_SCREEN_ON` only while
   `!displayOff`. Off = black frame + flag cleared → the system timeout turns the panel off (F13, up to the phone's
   screen-timeout setting later) → phone's own doze/AOD.
4. **Session survives its own off.** Screen went off while `displayOff` = our off: the activity stays (stopped,
   show-when-locked). Screen went off while lit = side key: the activity finishes, the next wake is the lock screen.
5. **Wake.** A really-off panel delivers no touch; the wake is the system's (double tap to turn on, side key, lift to
   wake, AOD tap). Any wake during a sleeping session uncovers StandBy **unarmed**: the clock shows but the flag
   stays cleared, so the system timeout turns it off again. A touch on StandBy arms it (flag back, idle rule
   restarts). A system wake therefore can never leave the screen lit. First start of a session (plug-in, tile) is armed.
6. **Start triggers** (everything else deleted): genuine plug-in, once per plug session; screen-off after
   `ACTION_USER_PRESENT`. No start on screen-on, listener connect, or a periodic job. `manualOff` is deleted: with
   these triggers nothing is left for it to veto, so nothing needs persisting for it. The one persisted bit is
   `plugHandled` (this plug session already had its start decision).
7. **Charger flaps:** an unplug ends the session only after `FLAP_MS` (3 s, tunable) with `plugged == 0`; a connect
   while `plugHandled` is still true is a flap and does nothing.

```
            plug-in (once)                 unlock → screen off (while plugged)
                  └───────────┬───────────────────┘
                              ▼ verdict + posture gate (unchanged)
                          [ON armed]  KEEP_SCREEN_ON
        touch ▲               │ idle 20 s / dark / flat
              │               ▼
   [ON unarmed] ◄── wake ── [OFF pending] black, flag cleared ── system timeout ──► [SLEEPING] panel off
     no flag;                                                                          │ activity stopped
     system timeout → SLEEPING                                                         │
  side key while ON armed → finish (lock screen next)        unlock / unplug(3 s) / exit swipe → finish
```

## 3. Tasks

Order: T0-T4 (+T11), then T5-T10 in any order, T12 after the phone round, T13 last. Tests are JVM unless marked.

### T0 — dream stub (evidence loop; 7, 16-dream)
- `S/platform/StandbyDream.kt`: replace the body. `onAttachedToWindow`: `isInteractive = false`, `isFullscreen = true`,
  `isScreenBright = false`, black view, `window.attributes.screenBrightness = Brightness.OFF`. Measure line
  "dream 대기 · 화면 보호기가 켜져 있음" once per start. No holder, no `HostCallbacks`, no `finish()`, no `unlockThen`.
- Delete: `ChargeStart.checkDream`, `StandbyPresence.dream` and its readers, the dream's `finishForChargeRule`,
  `PendingUnlock` + the `StandbyActivity.onResume` hand-over line (grep for other writers first).
- Test `T/platform/R16TripwireTest.kt`: reads the `StandbyDream.kt` source and asserts it contains neither `finish(` nor
  `isScreenBright = true` (guard for the one line that caused the loop).

### T1 — start policy (1, 2, 4, 5, 6-relaunch)
- New `S/data/StartPolicy.kt` (pure):
  `data class StartState(used: Boolean, plugHandled: Boolean)`;
  events `USER_PRESENT, SCREEN_OFF, POWER_CONNECTED, UNPLUG_CONFIRMED, JOB`;
  `fun next(s, e, sessionAlive: Boolean): Pair<StartState, Boolean /* try launch */>`:

  | event | launch | new state |
  |---|---|---|
  | USER_PRESENT | no | used = true |
  | SCREEN_OFF | `used && !sessionAlive` | used = false |
  | POWER_CONNECTED | `!plugHandled && !sessionAlive` | plugHandled = true (already true = flap, nothing) |
  | UNPLUG_CONFIRMED | no | plugHandled = false |
  | JOB | `!plugHandled && !sessionAlive` | plugHandled = true |

- `S/platform/ChargeStart.kt`: `used` in memory (lost on process death = no launch, the safe side); `plugHandled` in
  the existing `charge_start` prefs. Delete `onScreenOn`, `screenOffNow`, `manualOff`, `lastLaunchMs` and the 60 s
  guard, `StandbyPresence.lastHiddenMs`, the `force` parameter, `REARM_MS` and every `arm(ctx, REARM_MS, ...)`.
  `fire()` keeps `verdict()` and the posture gate. The job is armed only by `sync()` and UNPLUG_CONFIRMED, never by itself.
- `S/platform/Listener.kt`: receiver adds `ACTION_USER_PRESENT`, drops `ACTION_SCREEN_ON`; delete the
  `onListenerConnected` launch (lines 316-318 of the current file); on connect, `if (plugged == 0)` → UNPLUG_CONFIRMED
  (repairs a missed unplug). SCREEN_OFF keeps its 1.5 s settle delay.
- `ChargeRules.decide`: drop `dreamRunning`; `activityFront` becomes `sessionAlive` (= `StandbyPresence.alive`).
- Test `T/platform/StartPolicyTest.kt`: the table above row by row, plus sequences — (a) system wake then timeout:
  `SCREEN_OFF` with used = false → no launch; (b) unlock, off → launch once, second off → none; (c) connect, connect
  (flap) → one launch; (d) connect, job → one launch; (e) unplug confirmed, connect → launch; (f) exit swipe then
  lock-screen timeout → none. Update `ChargeRuleTest` for the changed `decide` signature.

### T2 — unplug debounce (3)
- `S/data/StandbyStateHolder.kt`: `unplugRx` and the `batteryRx` plugged-change branch both call one
  `scheduleUnplugCheck()`: after `FLAP_MS` (3 000, const in `StartPolicy`), `if (ChargeStart.plugged(ctx) == 0)` end
  the session (for `automatic`, re-check the wireless rule with the then-current plug type).
- `Listener`: `ACTION_POWER_DISCONNECTED` → same delay → UNPLUG_CONFIRMED + arm the job + finish a sleeping session
  (`StandbyPresence.session?.get()?.finish()`, see T4). `ACTION_POWER_CONNECTED` keeps its 1.5 s settle delay.
- Test (Robolectric, `T/platform/R16HolderTest.kt`): holder with a fake `HostCallbacks`; send DISCONNECTED, restore the
  sticky battery intent to plugged, advance the main looper 3 s → `finish` not called; same without restoring → called once.

### T3 — real off from the holder (16, A)
- `HostCallbacks`: add `fun setKeepScreenOn(on: Boolean)`.
- `StandbyStateHolder`: `start(armed: Boolean)`; field `armed`; `touch()` sets `armed = true`.
  `setOff()` calls `host.setKeepScreenOn(...)` whenever the value changes; unarmed, the board still shows.
- Pure helper in `Logic.kt`: `fun keepScreenOn(off: Boolean, armed: Boolean, showNow: Boolean) = !off && armed && !showNow`.
- `StandbyActivity`: remove the unconditional `addFlags(FLAG_KEEP_SCREEN_ON)`; implement `setKeepScreenOn` with
  add/clearFlags.
- Tests: `LogicTest` truth table for `keepScreenOn`; in `R16HolderTest` (Robolectric): mode AFTER_20S, advance 20 s →
  fake host saw `setKeepScreenOn(false)` and brightness `OFF`; `touch()` → `true`; `start(armed = false)` → never `true`
  until `touch()`.

### T4 — activity lifecycle (6, 7-activity, 9, 11)
- New pure `StopRule.onStop(interactive, keyguardLocked, displayOff, glowCovering, changingConfig): KEEP | FINISH` in
  `Logic.kt`:

  | case | result |
  |---|---|
  | changingConfig or glowCovering | KEEP |
  | screen off, `displayOff` (our off) | KEEP (sleeping session) |
  | screen off, lit (side key) | FINISH |
  | screen on, keyguard locked (alarm/call/stub dream on top) | KEEP |
  | screen on, unlocked (home, app opened) | FINISH |

  Applies to every start mode, replacing the `chargeStart`-only line in `onStop`.
- `StandbyActivity`: `onStart` → `holder.start(armed = firstStart)`; `firstStart` false after the first `onStop`.
  `onStart` also finishes when the session began plugged and `plugged == 0` now (unplug missed while stopped).
  `ACTION_USER_PRESENT` receiver for every mode (not while `unlocking`). `StandbyPresence.chargeActivity` → `session`
  (any mode).
- Wake safety: remove `android:turnScreenOn="true"` from the manifest entry; `setTurnScreenOn(true)` in `onCreate`,
  `setTurnScreenOn(false)` in the first `onResume`, so a later resume/finish of a sleeping session can never light
  the panel.
- Manifest: `android:launchMode="singleTask"` on `StandbyActivity`; `onNewIntent` → `setIntent` + `recreate()` only when
  the mode extras differ.
- `showNow`: never keeps the screen on (T3 helper): the preview follows the phone's timeout and stops blocking charge starts.
- Test `LogicTest`: the `StopRule` table. Robolectric `R16ActivityTest`: build the activity with the tile extra,
  `pause/stop` with `ShadowPowerManager.setInteractive(false)` while lit → `isFinishing`; same with the holder's state
  `displayOff = true` → not finishing; second `start` → window flags lack `FLAG_KEEP_SCREEN_ON` until a touch.

### T5 — motion-to-wake removed (14, 15)
- `StandbyStateHolder.onAccel`: delete the E13 block (`motionToWake ... wakeMotion`), `lastMotionTouch`;
  `MotionDetector.wakeMotion` and `WAKE` deleted. Pick-up exit (2.5 m/s² for 300 ms) and flat stay.
- Settings: hide the 움직임으로 깨우기 row; `AppConfig.motionToWake` stays in the codec (no migration), unused.
- Test `LogicTest`: `DisplayOffMachine` AFTER_20S goes off at 20 s with no `touch` in between (already-true behaviour,
  now reachable); existing tests that reference `wakeMotion` are deleted.

### T6 — lux (12, 18)
- `LuxMedian`: add `fun value(nowMs: Long): Float?` = median of readings ≤ 1 s old, else the newest reading.
  `StandbyStateHolder.evaluate()` reads `lux.value(now)` instead of the cached `lastLux`; after each light event
  `schedulePoll` adds a 1 s wait so the value settles without a new event.
- `DisplayOffMachine.evaluate` AUTO: `lux == null || lux <= enterLux` for going off (no sensor / no reading = the
  20 s rule).
- Tests `LogicTest`: `add(0,200); add(300,50); add(600,2); value(2000) == 2f`; AUTO with `lux = null` is off at 20 s.

### T7 — brightness (17)
- `Brightness.applied`: `if (c.heatProtect && hot && v > HOT_MAX) HOT_MAX else v` (AUTO is -1, so it now stays AUTO
  instead of being raised to 35 %).
- Night default: `AppConfig.nightMaxBrightness` 0.3 → 0.05 (open question Q3; stored user values untouched).
- Tests: `ChargeBrightnessTest` — hot + AUTO stays AUTO; hot + 0.8 → 0.35.
- Not changed: night value vs system auto in general (no public read of the auto level).

### T8 — glow limits (8, 10)
- `S/platform/Glow.kt`: one `scheduleCap()` used by `onCreate` and `onNewIntent`: PRE_ALARM → finish after
  `glowStartMinBefore + 10` minutes; ALARM → 10 minutes (today only when the alarm was already ringing at create).
- `AppConfig.moodAutoOffMin` default 0 → 30 (spec M5 already says 30).
- Test `R15BehaviourTest` neighbour (pure): `GlowCap.minutes(source, minBefore)` = 10 / minBefore + 10 / null for
  UNFOLD and TEST.

### T9 — settings guide (evidence loop, d)
- `S/settings/Perms.kt`: remove `Perm.DREAM` from `CHARGE_START`; replace it with a warning item `SCREEN_SAVER` that is
  "missing" when `screensaver_enabled == 1` **and** `screensaver_components` contains our package (unreadable = not
  missing). Copy: title "화면 보호기 끄기", body "화면 보호기가 켜져 있으면 화면이 완전히 꺼지지 않아요. '없음'으로 바꿔 주세요.",
  button "끄러 가기" → existing `dreamIntents()`.
- `HomeScreen.kt:72`: the banner now shows for `SCREEN_SAVER`. Remove "화면 보호기로 선택" from first-run copy.
- Test `T/settings` (pure): `screenSaverWarning(enabled: Int?, components: String?, pkg)` — (1, ours) true;
  (0, ours) false; (1, other) false; (null, null) false.

### T10 — old tests
- Before starting: `grep -rn "manualOff\|onScreenOn\|checkDream\|wakeMotion\|REARM" T`; delete or adjust what encodes the old behaviour.

### T11 — Measure lines (personal build; needed for the phone round)
- `start 판단 · {event} · used={0/1} handled={0/1} → {verdict}` (replaces the per-source lines).
- `꺼짐 요청 · keep=0` / `켜짐 · keep=1 armed={0/1}` (state changes only).
- `화면 꺼짐 · 세션 {잠듦|종료(측면 버튼)}`; `깨어남 · armed=0`; `터치 · armed=1`.
- `dream 대기` (T0). `lux={v} night={0/1}` once a minute while lit at night (settles candidate 13).

### T12 — optional, after the phone round: delayed finish in the stub (tests H1)
- Personal build only: the stub waits `Settings.System.SCREEN_OFF_TIMEOUT` + 2 s, logs, calls `finish()` once; a dream
  start within 20 s after that = persist `dreamFinishWakes` and never try again. Public build only if the log shows doze.

### T13 — docs
- `spec-behaviour.md`: E1 (entry = activity, plug-in / used-then-off), E10-E14 (off = panel off; E13 removed; E14 wake
  = system wake then touch arms), state diagram from section 2, section 4 setup row. One Recent line in `ROADMAP.md`.

## 4. Candidate verdicts

| # | Verdict | Code evidence | Task |
|---|---|---|---|
| loop | **Confirmed** (log) + mechanism H1 | `StandbyDream.kt:49-52,73` refuse → `finish()` | T0, T9 |
| 1 | Confirmed | `ChargeStart.onScreenOn` + `Listener` SCREEN_ON | T1 |
| 2 | Confirmed | `onScreenOff`: not own → `fire(force = true)` with no "used" test | T1 |
| 3 | Confirmed | `onPowerConnected` clears `manualOff` + fires; `unplugRx`/`batteryRx` finish at once | T1, T2 |
| 4 | Confirmed | `Listener.onListenerConnected` fire; `manualOff` is `@Volatile` memory | T1 |
| 5 | Confirmed | `fire()` re-arms `REARM_MS` on every path; job → `LAUNCH` when locked + off | T1 |
| 6 | Confirmed | exit/pick-up → lock-screen timeout → path 2; `onStop` finishes on any stop, even covered with the screen on | T1, T4 |
| 7 | Confirmed | `StandbyDream.unlockThen` starts the activity without extras → no end rule, flag always on | T0 (path deleted), T4 |
| 8 | Confirmed | 10-min cap only `if (ListenerBus.alarm.value != null)` at create; `cancelMood()` sets `moodMin = 0` | T8 |
| 9 | Confirmed | `evaluate`: `!showNow && ...`; flag unconditional; `alive` blocks `onScreenOn`, front blocks verdict | T4 |
| 10 | Confirmed | `Config.kt:444` default 0; spec M5 says 30 | T8 |
| 11 | **Not confirmed as duplicates** (same-affinity NEW_TASK start of the root activity reuses the task). Real defect: a second start's extras are dropped and backgrounded instances live on. | manifest: no launchMode | T4 |
| 12 | Confirmed (logic) | `lastLux` set only in `onSensorChanged`; median keeps a mid value when the sensor goes quiet | T6 |
| 13 | **Unconfirmed** (physical; depends on the phone's sensor compensation) | - | T11 measures; T7 lowers the night level |
| 14 | Confirmed | `linear = abs(|a| - 9.81)` per sample, `> 0.25` → `machine.touch` every second; a 0.3 m/s² sensor offset keeps the screen on forever under AUTO (default) | T5 |
| 15 | Confirmed (follows from 14) | accel path ignores `nightNotifQuiet`; `endNotification` compares `lastTouch` | T5 |
| 16 | Confirmed | `StandbyActivity.kt:65`, `Brightness.OFF = 0.01`, dream `isScreenBright = true` | T0, T3 |
| 17 | Heat cap: confirmed (`v == AUTO → HOT_MAX`). Night value fixed 0.3: confirmed as code; "pumps day/night" unconfirmed | `Brightness.applied`, `target` | T7, T11 |
| 18 | Confirmed | AUTO needs `lux != null` | T6 |
| 19 | **Rejected for this round**: unimplemented P1 feature (spec E18), not a cause of lit/flicker; Android normally leaves power saving when plugged in | no `isPowerSaveMode` in `S` | - |

## 5. Real-phone checklist (no adb; read the Measure log in settings)

Setup: install, follow the new banner (screen saver → 없음), note the phone's screen timeout.

1. **Loop gone (wired, 무선 충전일 때만 on):** plug the cable, lock, leave 2 min. Screen must be off. Log: no `dream`
   lines, no `start 판단 → 띄움`.
2. **Same with the screen saver still set to Stand Screen:** screen is black, no flicker for 2 min; log shows one
   `dream 대기`, not one per 5 s.
3. **Plug-in once:** allowed charger, screen off → StandBy within ~3 s; log one `POWER_CONNECTED → 띄움`.
4. **Real off:** display 20초 후. After 20 s black; after the phone's timeout the panel is off (AOD or dark, no glow in
   a dark room). Log `꺼짐 요청 · keep=0` then `화면 꺼짐 · 세션 잠듦`.
5. **Wake:** double tap (or side key) → StandBy clock, `깨어남 · armed=0`; do not touch → off again after the timeout.
   Wake again, tap once → `터치 · armed=1`, stays 20 s.
6. **System wake:** send yourself a message with the panel off. Whatever lights up goes dark again within the phone's
   timeout; no `armed=1`.
7. **Side key on lit StandBy:** screen off, wake → phone lock screen (`세션 종료(측면 버튼)`); 5 s later off; StandBy
   does not return.
8. **Used then off:** unlock, open an app, press the side key → StandBy returns (`SCREEN_OFF used=1 → 띄움`).
9. **Not used:** wake to the lock screen, let it time out → no StandBy (`used=0`).
10. **Flap:** lift the phone off the pad for 1 s and put it back → StandBy stays, no relaunch line. Lift for 5 s →
    ends; put back → one launch.
11. **Overnight:** morning log has no line repeating every few seconds/minutes; phone not warm; `lux=` lines show
    whether the lit panel reads above 3 lux (candidate 13).
12. **Fold:** a pre-alarm glow whose alarm was deleted ends by itself; the mood light ends after 30 min.

## 6. Open questions for the operator

- **Q1.** After a wake during a sleeping session StandBy shows **unarmed** (clock visible, off at the phone's timeout
  unless touched). Alternative: lock screen + end the session (back only after unlock or re-plug). Plan assumes unarmed.
- **Q2.** A single tap cannot wake a really-off panel; it needs the phone's 두 번 탭해서 켜기 / side key / 들어서 깨우기.
  Accept, and say so in settings copy?
- **Q3.** Night brightness default 30 % → 5 %: confirm the number.
- **Q4.** 디스플레이 "안 함" and 자동 in a bright room keep the screen on by design. Does C allow that, or add a ceiling?
- **Q5.** "Black" to "panel off" takes the phone's screen-timeout setting (15 s to 10 min); an app cannot shorten it. OK?
- **Q6.** The dream stub stays in the app for good (F10). OK?
