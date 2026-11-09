# Galaxy StandBy — behaviour spec (2026-11-09)

Acceptance list for the native Kotlin/Compose build. Sources: `docs/archive/2026-10-29-standby-galaxy/00-standby-canon.md`
(+02, 05, 07) and `brief.md` (wins on conflict). Mockups: round 3 (clock styling), round 4 (boards, alarm, mood light).

Tags: **U#** = canon section 9 unknown, value here is ours. **ours** = our choice, iPhone has no equivalent.
**P0** = first build, **P1** = later. Probe = `kr.dot.standby.probe` runs 1-2 (Fold8, One UI 9).
Screens: Fold cover 751x475 dp, Fold inner 932x704 dp (measured), bar ~2.17:1, Flip cover ~1.1:1; landscape and portrait
on all of them. Night red = `#FC0000`. Background `#000000`.

## 1. Feature checklist

### 1a. Entry, exit, display

| # | Feature | iPhone | Ours (Android) | Our value | P |
|---|---|---|---|---|---|
| E1 | Entry | Locked + charging (MagSafe/Qi/cable) + on its side + still (no stillness/delay numbers, U2) | `DreamService` started by the system screen saver "while charging" after screen timeout (that is our stillness gate) | - | P0 |
| E2 | Angle gate | "Upright at an angle", flat = no StandBy, no degrees | Dream runs; while flat it renders the **off** state, upright again = on | flat = gravity \|z\| > 9.0 m/s² (~25°) for 1 s; upright = \|z\| < 8.0 for 1 s (U2) | P0 |
| E4 | Orientation | Landscape only (non-Duo) | Landscape **and** portrait, all four rotations. Own rotation from accelerometer (dreams cannot request orientation) | 600 ms debounce, 30° hysteresis (ours) | P0 |
| E5 | Exit: unplug | Ends | AOSP ends dream when power is removed | - | P0 |
| E6 | Exit: unlock | Ends | Side-key fingerprint / face unlock ends dream (system) | - | P0 |
| E7 | Exit: pick up | Ends (implicit) | `finish()` when linear accel > 2.5 m/s² for 300 ms, or flat-to-hand rotation | ours | P0 |
| E8 | Exit: swipe | - | Swipe up from bottom 48 dp edge > 30% height → `finish()` → lock screen | ours | P0 |
| E9 | First entry | Welcome splash once | Splash card once (copy §4) | ours text | P1 |
| E10 | Display 자동 | Off "when not in use and the room is dark"; ~10-20 s reported | Off state after 20 s with lux ≤ 3 and no touch/motion | 20 s, 3 lux (U16) | P0 |
| E11 | Display 20초 후 | Off after 20 s | Off state 20 s after entry / last wake, any light | 20 s | P0 |
| E12 | Display 안 함 | Stays on until unplugged; hides Night Mode + Motion to Wake (reported). 17.0's Always On toggle became these 3 options in 17.1 | Never off; hide 야간 모드 and 움직임으로 깨우기 rows (copied as reported) | - | P0 |
| E13 | Motion to Wake | Movement at night wakes; only under 자동 (reported) | Accelerometer delta wakes from off state | > 0.25 m/s² over low-passed gravity (U15) | P0 |
| E14 | Wake by tap / table nudge | Tap, light knock, Siri | Tap anywhere (first tap only wakes, not delivered); knock = E13 | - | P0 |
| E16 | Siri on/off | Voice turns StandBy on/off | App shortcut "스탠바이 켜기/끄기" (toggles setting); Assistant/Bixby via shortcut only | ours | P1 |
| E17 | Per-charger memory | Remembers clock/photos/widgets per MagSafe charger; other chargers = last view | Key = plug type (`EXTRA_PLUGGED` AC/USB/WIRELESS/DOCK) + screen (cover/inner/bar); remembers last board. Key shown in settings | ours (U3) | P1 |
| E18 | Low Power / Sleep Focus / Bedtime | AOD off in LPM, Sleep Focus (U4) | Power saving on → force off state; DND/Bedtime mode → off state unless woken | ours (U4) | P1 |
| E19 | Brightness | Auto, no slider (complaint) | Day: system auto. Night: own cap slider | night cap 30% (ours) | P0 |
| E20 | Burn-in | No StandBy statement (U18) | Pixel shift of whole root | every 60 s, next point of 3x3 grid {-3,0,+3} dp in a snake order (U18) | P0 |
| E21 | Heat | - | Battery temp > 42 °C → dim to 50% until < 40 °C | ours | P1 |
| E22 | Non-charging StandBy (Duo) | Duo needs no charger; 충전 중에만 switch | Dream cannot start unplugged; needs FGS + screen-off receiver | - | P1 (risk) |

### 1b. Night mode

| # | Feature | iPhone | Ours | Our value | P |
|---|---|---|---|---|---|
| N1 | Trigger | Low ambient light, "a few seconds after lights off", no lux (U16) | `Sensor.TYPE_LIGHT`, 1 s median filter | enter lux ≤ 3 for 5 s; exit lux ≥ 8 for 1.5 s | P0 |
| N2 | Look | Monochrome red, original luminance = intensity, layout identical | Root `graphicsLayer` colour matrix: R = 0.988·(0.299R+0.587G+0.114B), G = B = 0 | `#FC0000` peak | P0 |
| N3 | Transition | Red→normal ≈1 s (pinkish mid-frame), normal→red "a few s" | Crossfade | to red 1.0 s, to normal 0.6 s | P0 |
| N4 | Effects at night | - | Clock effects off except 빛번짐 (glow, red); backgrounds black | as mockup r3 | P0 |
| N5 | Photos at night | U17 | Same red matrix + 50% dim | U17 | P0 |
| N6 | Then off | Night then full off later, motion wakes | = E10 | - | P0 |
| N7 | Colour option | None (complaint) | 야간 색상 빨강/주황 | 주황 `#FF6A00` (ours) | P1 |

### 1c. Pages (iPhone default boards) and clocks

Default boards, left to right: **위젯 ↔ 사진 ↔ 시계**. User boards append. First ever entry lands on 시계, then the last
viewed board (per E17 key when P1 lands).

| # | Feature | iPhone | Ours | Our value | P |
|---|---|---|---|---|---|
| W1 | Widgets page | Two systemSmall widgets, exact left/right halves, scaled up, no background, black; margin ~12% at edges (measured) | Board layout 두 칸, each slot a vertical stack of our cards; portrait = top/bottom halves | slot inset 6% of short side (U5) | P0 |
| W2 | Default stacks | Analog clock + monthly calendar | Left 아날로그 시계, right 달력 | - | P0 |
| W3 | Stack scroll | Vertical swipe per side | `VerticalPager` per slot, dots on the slot's outer edge for 1.5 s after change | ours | P0 |
| W4 | Smart Rotate, Widget Suggestions | Time-of-day auto switch; suggested widgets | 스마트 전환: music when playing, timer when running, 06-10 날씨/일정, 21-06 시계. 위젯 제안: unused cards appended to the stack | ours | P1 |
| W6 | Interactive vs not | Interactive work in place; others show top-right ↗, tap it = auth + open app | Music/timer/sleep buttons work locked; other tap shows ↗ for 3 s, tap ↗ = unlock + open app | 3 s (ours) | P0 |
| W7 | Third-party widgets | Any WidgetKit small | Our 12 cards only; `AppWidgetHost` in dream not planned | - | P1 (maybe never) |
| P1 | Photos page | Themes 추천/자연/반려동물/도시/인물, vertical swipe; time top-right (~18% h, x 72-92%), place bold + date bottom-right (y 72-86%); theme pill right-centre on change | Themes = 추천 (favourites `IS_FAVORITE` + recent), user albums (MediaStore buckets); 자연/반려동물/도시/인물 via on-device labeling | pill 1.5 s (ours) | P0 albums, P1 themes |
| P2 | Shuffle | U6 | Next photo every 15 s, 1 s crossfade | U6 | P0 |
| P3 | Ken Burns | Slow zoom, ≤2% in 3 s observed | scale 1.00→1.04 over each photo | U6 | P0 |
| P4 | Edit | Long press: eye hides theme, + adds album (no single photos), People pick | Same, after unlock | - | P1 |
| P5 | Caption tap | Auth → Photos | Unlock → Samsung Gallery `ACTION_VIEW` uri | - | P0 |
| P6 | Duo Shared Albums + frequency | Pick photos + how often to shuffle | 셔플 간격 10초/15초/1분/5분 | 15 s default | P1 |
| C1 | Clock styles | Digital, Analog, World, Solar, Float; vertical swipe; order U7 | Vertical pager in this order | Digital→Analog→World→Solar→Float (U7) | P0 |
| C2 | Time format | 12 h, no leading 0, no AM/PM; World "7:46 PM" | Follow `DateFormat.is24HourFormat`; 12 h = iPhone format; World "오후 7:46" | - | P0 |
| C3 | Digital | Bold condensed digits h≈69% / w≈53% (x 12.8-66.2%); right column "MON 19 / 73°" x 73-86%, next alarm; colour = digits + weekday | Same geometry; right: "월 19", temp, "알람 7:00" | default `#76A6F3`, font 좁게 | P0 |
| C4 | Analog | Dial x 8.3-92%, pivot (50%, 52.7%), numerals 12/3/6/9 h 8.6%; hour/min `#C5C0C4`, second `#CC894E`, weekday `#D04450`; date right of centre; next alarm | Canvas draw; second hand steps 1 Hz (hidden in off state) | sec `#E9893A` | P0 |
| C5 | World | Dot map x 12.8-93.6%, sunlit area, cities `#FFAF3B`, bottom-left block (arrow + city + big time) x 7-28%, y 59-85%; "Today, +6 HRS"; no colour edit | Natural Earth 110m as 96x44 dots, terminator from date; cities picked in settings | colour editable (ours) | P0 |
| C6 | Solar | Thin digits on flare gradient (lavender→mint/teal), 7+ palettes; day drift U8 | Static gradient per palette (7 from r3) | no drift (U8) | P0 |
| C7 | Float | Rounded heavy overlapping digits h≈66%, colon = 2 grey dots Ø 5% width `#D5D9DF`; roll-in 0.35-0.45 s, only changed digits, old tilts up, new rises, no overshoot | Same; two-colour option | roll 400 ms | P0 |
| C8 | Colour edit | Long press → bottom-right white circle → "Color" row of swatches → X → Done; presets only (Float 10+2, Digital 12+ incl. rainbow, Analog 9+, Solar 7+, World none) | Same flow + our styling panel (C9) | swatches from r3 `SW` | P0 |
| C9 | Per-clock styling | Presets only (Duo adds font/colour) | Each clock instance keeps: font (11), weight 100-900, size 60-130%, width 70-130% (`scaleX`), colour (swatch + free HSV), effect (5), background (3), numerals (6 systems), layout (3); analog: numeral style (4), ticks, 5 colours. Labels §4 | defaults = iPhone look (r3 `DEF`) | P0 font/colour/size/weight/layout; P1 glass/glow/rainbow, numerals |
| C10 | Fonts | SF family, not shippable | 굵게=Inter 800, 가늘게=Inter 250, 둥글게=Nunito 800, 스텐실=Inter 800 cut (ours), 명조=Source Serif 4 500, 굵은 명조=Source Serif 4 900, 슬랩=Roboto Slab 700, 레일=Inter 800 rails (ours), 좁게=Oswald 600 (Digital), 둥글고 아주 굵게=Nunito 950 (Float), 얇게=Inter 350 (Solar); all OFL, bundled | - | P0 |
| C11 | 27.3 multi-version faces | Same face saved in several versions | Boards unlimited; any clock card duplicated with own style | - | P0 |

### 1d. Duo face system (iOS 27.1 / 27.3) mapped to boards

| # | Feature | iPhone | Ours | Our value | P |
|---|---|---|---|---|---|
| D1 | Face switch | Tap current face → carousel → horizontal swipe | Setting 페이스 전환: 바로 넘기기 (default) / 탭한 뒤 넘기기 (Duo) | carousel scale 0.82, neighbours peek 9% | P1 |
| D2 | Add / customise / delete | Long press → `+` 새로 추가, 사용자화 pill; delete = long press → swipe to face → swipe up → trash | Same on boards, after unlock | swipe up > 25% h shows trash | P0 |
| D3 | Edit tabs | STYLE / COLOR / FONT / WIDGET; "Automatic" light/dark pill; numerals pill "Arabic, Western" | 스타일 / 색상 / 서체 / 위젯; 자동 (light/dark) only on faces that support it | - | P0 |
| D4 | Faces | Analog, Digital, Photos, Memories, Modular, Weather, Calendar (gallery: Featured 6 in 3x2, then rows per kind); Digital/Analog take up to 3 widgets; clock position or none | Boards + cards cover all 7; clock card 배치 covers position; tent posture has no Fold signal (= cover); Reachability skipped | - | P0 (Memories P1) |
| D5 | Duo Analog face | 8 numerals (10,12,2,3,4,6,8,9), digital time with seconds left of centre, "THU 22" right, thin blue second hand | Analog card variant "Duo" | blue `#4D9BEF` | P1 |
| D6 | Modular | Left 2 small, right 1 big; big swipes horizontally, 6 page dots; Smart Rotate / Widget Suggestions buttons | Layout 작은 칸 2 + 큰 칸; big slot = horizontal pager | max 6 pages | P0 |
| D7 | Weather face | Station / daily / weekly / temperature only | 날씨 card styles 현재/오늘/주간/기온만 | Open-Meteo hourly | P0 |
| D8 | Calendar face | Month or date only | 달력 card styles 월/날짜만; today = red circle | - | P0 |
| D10 | Book posture | 2 columns: left 55% (2 small + month), right 45% portrait photo; "layout cannot be changed" (U11) | Fold inner screen board 책 (펼침): 큰 칸 + 작은 칸 2, editable | - | P0 |
| D12 | 위치 (start posture) | Pick postures, check each | 위치: 접었을 때(커버) / 펼쳤을 때(안쪽), checkboxes; Fold only | both on | P0 |
| D13 | 충전 중에만 | Switch | Shown, locked ON until E22 exists | - | P1 |
| D16 | Wake glow | Opposite panel: mint `#85D4BA` top → dark bottom, ~7 s seen; colour/duration disputed (U20) | Fold inner screen only (see M2) | warm `#FFB36B`→`#7A3A1C`, ramp 6 s | P0 |

### 1e. Full-screen interrupts and cards

| # | Feature | iPhone | Ours | Our value | P |
|---|---|---|---|---|---|
| I1 | Alarm screen | Small "Alarm" top-left (`#71ABF5` Duo), big time top-right, Snooze pill bottom-left (orange / `#F9C699`), Stop bottom-right (`#35508F` pill or text), buttons ~25-30% height; back to face ~1 s after | Same layout; buttons fire the **Samsung Clock notification's own actions** | Snooze left fixed, Stop right fixed, height 28% | P0 |
| I2 | Snooze length | 9 min (U14) | Whatever Samsung Clock is set to | - | P0 |
| I3 | Alarm robustness | iOS 17 bug: motion silences sound | We never touch alarm audio; buttons always shown with sound | - | P0 |
| I4 | Big notifications | App icon/sender photo big centre → text unfolds (Watch-like), timing U13; off if 알림 보기 off; Focus-allowed only; critical always | NLS → icon 40% of short side, unfold at 0.9 s, close at 6 s, 0.3 s fade; `Ranking.matchesInterruptionFilter()`; alarm/call bypass 알림 보기; we never hold and bulk-release | 0.9 s / 6 s | P0 |
| I6 | 탭할 때만 미리보기 | Hidden until tap; Face ID nearby reveals | Icon + app name + "탭하여 보기"; tap → unlock → content | - | P0 |
| I10 | Live Activity | Minimal icon → tap → Lock Screen layout at 200% full screen; edge margin 14pt; red at night; multi-priority U13 | Ongoing notifications with progress/chronometer, Android 16 Live Updates (`ProgressStyle`, promoted ongoing), Samsung timer → top pill → tap = our template at 2x (title, text, progress, chronometer, icon) → tap again closes | newest-updated first, pill "+N" (U13) | P1 |
| I11 | Now Playing | Waveform icon top → tap → art left (x 9-42%, y 14-83%), title bold + artist right, volume icon top-right (tap = slider), prev/pause(largest)/next x 56-82% y≈58%, progress y≈71%, times y≈76% | `MediaSessionManager.getActiveSessions(NLS)`; waveform icon top-right while PLAYING; same layout | back to board 5 s after pause; ✕ or swipe down | P0 |
| I12 | Call, Siri, opened apps | Contact Poster full screen; landscape Siri; apps open rotated | System dialer takes over (we cannot draw it); opening anything = unlock then normal app | - | P0 (passive) |
| K1-2 | 시계, 아날로그 시계 cards | Clock widgets | Digits only / round small dial, fit slot | - | P0 |
| K3 | 음악 card | (widget) | Art, title, artist, prev/play/next; any media session (YouTube Music confirmed; TVING/Melon/Spotify if they publish one) | - | P0 |
| K4 | 유튜브 card | No video in StandBy | Official IFrame embed in `WebView`, user-entered video/playlist URL, logo + ads kept | - | P1 |
| K5 | 타이머·뽀모도로 | Timer = Live Activity | Own 25 min focus / 5 min break, shrinking ring, tap start/stop | 25/5 | P0 |
| K6 | 수면 소리 | None (Background Sounds separate) | Bundled CC0 loops (빗소리 etc.), 꺼짐 타이머 15/30/60/90분, screen very dark | - | P1 |
| K7 | D-day | None | Name + date user-set, days left | - | P1 |
| K8 | 오늘 일정 | Calendar small: next 1-2 | `CalendarContract.Instances` today, 3 rows, colour bar | - | P1 |
| K9 | Claude·Codex 사용량 | None | Copy of 눈금 `Fetcher`/`Status` (LAN `:8765/status.json` Bearer, else status bot pinned message): 5시간/7일 %, reset time | refresh 5 min | P1 |
| K10 | 날씨 | Weather widget/face | Open-Meteo, coarse location or fixed city | hourly | P0 |
| K11 | 달력 | Month widget | Month grid, today red circle | - | P0 |
| K12 | 추억 | Duo Memories face | "N년 전 오늘" from MediaStore `DATE_TAKEN` | - | P1 |
| B1 | Boards | (ours) | 1-4 slots, 6 layouts (labels §4); unlimited boards; portrait rotates the split axis | - | P0 |
| B2 | Presets | (ours) | 7 presets from r4 `PRESETS` (labels §4) | - | P0 |

### 1f. Fold mood light

| # | Feature | iPhone | Ours | Our value | P |
|---|---|---|---|---|---|
| M1 | Mood light | No such feature (Duo glow only) | Fold inner screen turning on while StandBy runs → full-screen light | - | P0 |
| M2 | Alarm glow | D16 | Ring detected and inner screen on → glow fills inner screen, alarm buttons overlaid bottom. Folded → cover alarm screen only (inner cannot be lit while folded) | ramp 6 s, night cap 35%, until Stop/Snooze or 10 min | P0 |
| M3 | Pre-alarm glow | U20 | `getNextAlarmClock()` + `setAlarmClock(T−N)` → full-screen intent | 알람 전에 미리 밝히기 끔/5/10/15분, default 끔 | P1 |
| M4 | Controls | - | Vertical drag = brightness 5-100%; horizontal swipe = colour 따뜻한 주황/호박/빨강/민트/흰색; tap = back to board | 따뜻한 주황 default | P0 |
| M5 | Auto off | - | 끄기 타이머 끔/15/30/60분 | 30분 | P1 |

## 2. State machine

```
            charger in + screen timeout (system)
 [LOCKED/AWAKE] ───────────────────────────────► [DREAM_START] ─ 스탠바이 off ─► finish()
        ▲                                             │
        │ unplug / unlock / pick-up (E7) /            ▼
        │ swipe-up (E8) / call or Samsung     ┌──► [ON] ◄───────── wake: tap, motion (E13, 자동 only),
        │ alarm activity on top                │     │  ▲             interrupt, lux ≥ 8 (자동),
        └──────────────────────────────────────┘     │  │             stood upright
                                                     │  └── 20 s timer (E10/E11) / flat (E2) ──► [OFF]
                                           night? ───┤                                (black, dream alive,
                                    lux≤3 5s ▼  ▲ lux≥8 1.5s                           touch still delivered)
                                          [ON+NIGHT]
 Inner screen turns on (Fold, D12 펼쳤을 때 checked) ─► [MOOD]  (fold again → back to cover board)
```

- **ON** draws the current board; minute tick only (second hand / Float roll are the only animations).
- **OFF** = all-black frame, window brightness minimum, pixel shift paused. Not `finish()`: finishing hands over to
  Samsung AOD and nothing would wake us on motion.
- **Night** is a layer on ON, not a separate screen; OFF keeps the night flag so a wake shows red immediately.
- **Lock state:** dream runs over the keyguard. Anything that needs auth (edit, open app, preview content) =
  `finish()` the dream → our `StandbyActivity` (`setShowWhenLocked(true)`) → `KeyguardManager.requestDismissKeyguard`
  → on success the action runs; the activity then shows the same board unlocked (`FLAG_KEEP_SCREEN_ON` while plugged)
  until the next screen timeout restarts the dream.
- **Fold:** display 0 swaps panels. In the dream, a configuration change to smallestWidth ≥ 600 dp = inner screen.
  If the dream survives the unfold → switch to MOOD in place. If it ends → the launcher-side `MoodActivity` is opened
  by a full-screen-intent notification posted from the dream's `onDetachedFromWindow` when the last config was inner
  (path confirmed by probe glow test).

### Interrupt priority

`alarm > call > notification > live activity > now playing > board`

| Interrupt | Detect | Shows | Ends / returns |
|---|---|---|---|
| Alarm | Any of: NLS post from `com.sec.android.app.clockpackage` with `category == alarm` or full-screen intent; `AudioPlaybackCallback` with `USAGE_ALARM` active. Pre-armed from `getNextAlarmClock()` (creator package check), re-read on `ACTION_NEXT_ALARM_CLOCK_CHANGED` | I1 (+ M2 when inner) | Notification removed, or alarm audio stopped > 3 s with no notification → board in 1 s. Preempts everything; wakes OFF |
| Call | Ringing state via NLS `category == call` | System dialer UI | Dream usually ended by dialer → restart on next screen timeout |
| Notification | NLS, importance ≥ DEFAULT, not ongoing, not group summary, not media/alarm/own; `VISIBILITY_SECRET` skipped, PRIVATE uses `publicVersion` | I4 | 6 s then fade; newer one replaces after ≥ 2 s; ignored while alarm/call shown; wakes OFF for its 6 s then back to OFF |
| Live activity | NLS ongoing + progress/chronometer/promoted | Pill on board; tap → 2x | Removed notification → pill gone; 2x closes on tap or after 30 s idle (ours) |
| Now playing | Active session PLAYING | Waveform icon; tap → I11 | ✕, swipe down, or 5 s after pause |

Lower interrupts arriving during a higher one: notifications are dropped (they remain in the shade), live activity and
now playing stay as state and reappear when the higher one ends.

## 3. Gestures

Thresholds use Compose pager defaults unless listed: positional threshold 50% or fling ≥ 400 dp/s; snap spring
~350 ms (iPhone: within 1-2 s, low confidence). Long press = `ViewConfiguration.getLongPressTimeout()` (400 ms; One
UI user setting respected). Touch slop = system.

| Screen | Gesture | Action |
|---|---|---|
| Any, OFF state | Tap | Wake only (consumed) |
| Any board | Horizontal swipe | Previous / next board (no wrap; edge rubber-band) |
| Any board | Swipe up from bottom 48 dp, > 30% height | Leave StandBy (lock screen) |
| Any board | Long press on empty area / slot | Edit (after unlock): jiggle 2° at 6 Hz, tap slot = card picker, `+` top-left adds card to stack, 완료 top-right |
| Widgets slot | Vertical swipe | Next/previous card in that slot's stack |
| Widgets slot | Tap card | Interactive control, else ↗ badge 3 s; tap ↗ = unlock + app |
| Clock page | Vertical swipe | Next style (C1 order), dots right edge 1.5 s |
| Clock page | Long press | Colour/style edit: neighbours' captions above/below, 5 dots right, white circle bottom-right → 색상 panel (swatches, ✕), 완료 |
| Photos page | Vertical swipe | Next theme, pill right-centre 1.5 s |
| Photos page | Tap caption | Unlock + open photo |
| Photos page | Long press | Theme edit (eye hide, `+` album) |
| Duo carousel (D1 on) | Tap board → swipe → tap | Pick board; long press = `+`/사용자화; swipe up > 25% = trash |
| Modular big slot | Horizontal swipe | Page inside slot (claims the gesture; board swipe only from other slots) |
| Alarm | Tap 다시 알림 / 중단 | Fire Samsung action. No swipe, no hidden gesture |
| Notification | Tap | Unlock + `contentIntent`; swipe up/down = close card |
| Now Playing | Tap transport / volume icon / ✕ / swipe down | Control / slider / back |
| Live pill | Tap | 2x ↔ pill |
| Mood light | Vertical drag / horizontal swipe / tap | Brightness / colour / board |

Edge gestures: request `systemGestureExclusion` on left/right 200 dp max so back-swipe does not end the dream (risk R7).

## 4. Korean copy

Apple Korean (verbatim from Apple Support, [07]) is marked **A**; everything else is ours.

| Where | String |
|---|---|
| Settings title / switch | 스탠바이 **A** |
| Night | 야간 모드 **A** — "저조도에서 화면 밝기를 낮추고 항목이 빨간색 틴트로 표시됩니다." (A, sentence-ended) |
| Night extras | 야간 밝기, 야간 색상, 빨강, 주황 |
| Notifications | 알림 보기 **A**, 탭할 때만 미리보기 **A** |
| Display | 디스플레이 **A**: 자동 **A** "사용하지 않으며 방이 어두울 때 디스플레이가 꺼집니다." · 20초 후 **A** "20초 후에 디스플레이가 꺼집니다." · 안 함 **A** "스탠바이가 켜져 있으면 디스플레이가 계속 켜져 있습니다." (A, "iPhone을" dropped) |
| Motion to Wake | 움직임으로 깨우기 (U1) |
| Duo rows | 위치 **A**: 접었을 때 / 펼쳤을 때 · 충전 중에만 **A** |
| Face edit | 사용자화 **A**, 스타일 **A**, 서체 **A**, 색상 **A**, 숫자 **A**, 위젯 **A**, 스마트 전환 **A**, 위젯 제안 **A**, 선택 **A**, 자동, 새로 추가, 완료, 취소, 삭제 |
| Clock names | 디지털, 아날로그, 세계, 솔라, 플로트 |
| Styling | 서체, 굵기, 크기, 너비, 색, 효과: 단색 / 두 색 / 무지개 / 유리 / 빛번짐 · 배경: 검정 / 단색 / 그라데이션 · 숫자: 서양 / 아랍 / 데바나가리 / 벵골 / 크메르 / 미얀마 · 배치: 아이폰 / 가운데 / 두 줄 · 아날로그 숫자: 4개 / 12개 / 로마 / 없음 · 눈금 |
| Fonts | the 11 names in C10 |
| Photos themes | 추천, 자연, 반려동물, 도시, 인물; 앨범 추가, 숨기기, 셔플 간격 |
| Boards | 화면 추가, 이 화면 지우기, 칸 나누기; 한 칸, 두 칸, 큰 칸 + 작은 칸 2, 작은 칸 2 + 큰 칸, 네 칸, 세 칸 |
| Board presets | 위젯, 사진, 시계, 모듈러, 작업 책상, 음악, 잠자리, 수능, 책 (펼침), 영상 |
| Cards | 시계, 아날로그 시계, 음악, 유튜브, 타이머·뽀모도로, 수면 소리, D-day, 오늘 일정, Claude·Codex 사용량, 날씨, 달력, 추억 |
| Card text | 집중, 휴식, 눌러서 시작, 눌러서 멈춤, 눌러서 계속, 남은 날, 5시간, 7일, 초기화, "N년 전 오늘", "최고 11° 최저 2°" |
| Digital side | "월 19", "73°", "알람 7:00" |
| World | "오늘, +6시간", "오후 7:46" |
| Alarm | 알람, 다시 알림, 중단 |
| Notification | "탭하여 보기", "N개의 알림" (pill) |
| Now Playing | 재생, 일시정지, 이전, 다음, 볼륨 |
| Mood light | 무드등, 밝기, 따뜻한 주황, 호박, 빨강, 민트, 흰색, 끄기 타이머, 알람 전에 미리 밝히기, 끔, 5분, 10분, 15분, 30분, 60분 |
| Page switch | 페이스 전환: 바로 넘기기 / 탭한 뒤 넘기기 |
| Splash | "스탠바이" / "충전 중에 세워 두면 시계, 사진, 위젯이 크게 보입니다." / 계속 |
| Setup rows | 화면 보호기로 선택, 알림 접근 허용, 정확한 알람 허용, 전체 화면 알림 허용, 사진 접근 허용, 캘린더 접근 허용, 위치 허용 (날씨), 미리보기 |
| Arrow badge a11y | "앱에서 열기" |

## 5. Feasibility and risks

| Item | Status | Risk / fallback |
|---|---|---|
| Dream on folded cover | **Confirmed** (runs 1-2, preview start, display 0 475x751 dp) | - |
| Dream auto-start while charging | **Untested** (charger on only 12 s in run 2). Samsung AOD is itself a dream (every SCREEN_OFF pairs with DREAMING_STARTED) | R1: if One UI prefers AOD, StandBy never starts by itself. Fallback: runtime `ACTION_POWER_CONNECTED` + FGS + full-screen intent → `StandbyActivity` |
| Interactive dream | Untested (probe used `isInteractive = false`) | R2: touches, long press, `requestDismissKeyguard` handoff all unverified |
| Samsung alarm detection | Untested (NLS, `USAGE_ALARM`, broadcast logged but alarm not run) | R3: action titles of Samsung's alarm notification unknown; match by title list (다시 알림/Snooze, 해제/중지/Dismiss), else `contentIntent` |
| Samsung alarm activity vs dream | Untested | R4 (highest): Samsung Clock's own full-screen alarm will likely start on top and end our dream, so I1 never shows. Then I1 is only for screen-on/heads-up cases; Samsung UI is the fallback |
| Glow over lock screen | **Confirmed** (`canUseFullScreenIntent=true`, shown before unlock on inner) | - |
| Window across fold | **Confirmed** for an activity; dream across unfold **untested** | R5: dream may end on unfold → FSI fallback adds ~0.5-1 s and a notification |
| Hinge angle | **Confirmed coarse** (0/90 only; FoldingFeature none→FLAT) | Mood light keys on inner display, not angle (fixed) |
| Media sessions | **Confirmed** YouTube Music (title, artist, art, transport) | TVING/Melon untested; no session = no card data |
| Light sensor | Untested on cover | R6: Fold cover sensor position/self-light may misread; thresholds need a calibration slider (설정 > 야간 모드 민감도, P1) |
| Rotation in dream | Untested with rotation lock on | R8: own rotation via `graphicsLayer` + swapped constraints; touch mapping must follow |
| Back gesture in dream | Untested | R7: edge swipe may end dream; exclusion rects capped 200 dp/edge |
| Overnight battery / heat | Not run | R9: screen + charge heat; E21 dimming; Battery protection 80-95% stop: plug stays, dream should continue (AOSP uses "powered") |
| YouTube in dream | Untested | R10: WebView embed policy, ads, night colour matrix does not apply to video surface → 50% black scrim instead |
| Usage feed | Code exists in status-widget (`Fetcher.kt`, `Status.kt`) | Copy, not import (repos stay independent); LAN token in BuildConfig, never in source |
| Photo themes | No system categories on Android | ML Kit on-device labeling = new dependency; P1 |
| Pre-alarm glow | `SCHEDULE_EXACT_ALARM` user grant (probe checks it) | Without it M3 is off |
| U1-U20 | Open (canon §9) | Our values live in one `StandbyConfig` object |
