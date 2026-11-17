# Stand Screen — Play Console answers (plan-r15 B3)

Ready to paste. Package `kr.dot.standscreen`. Privacy policy URL:
`https://dot-connects.github.io/dot-lab/works/2026-11-09-standby/page/privacy.html` (live after the dot-lab push).

If Play denies the full-screen intent (plan-r15 B4), drop section 2 and remove `USE_FULL_SCREEN_INTENT` from the public
manifest before the next upload.

## 1. Notification listener (Sensitive permissions → NotificationListenerService)

**KO**
> Stand Screen은 충전 중 화면을 시계·알림 화면으로 바꾸는 앱입니다. 알림 읽기 권한으로 (1) 새 알림을 크게 보여 주는 알림
> 카드, (2) 재생 중인 음악의 제목과 재생 버튼, (3) 시계 앱 알람이 울릴 때 큰 끄기/다시 알림 버튼을 보여 줍니다. 충전기를
> 꽂았을 때 화면을 띄우는 신호도 이 서비스가 받습니다. 알림 내용은 폰 안에서만 처리하며 저장하거나 외부로 보내지 않습니다.
> 사용자가 설정에서 직접 허용해야 하며, 허용하지 않아도 시계·달력·사진 화면은 동작합니다.

**EN**
> Stand Screen turns a charging phone into a clock and notification screen. Notification access is used to (1) show new
> notifications as large cards, (2) show the playing track's title and transport buttons, and (3) show large dismiss /
> snooze buttons when the clock app's alarm rings. The same service receives the charger-plugged signal that brings up
> the screen. Notification content is processed on the device only; it is never stored or sent anywhere. The user grants
> it in system settings; without it the clock, calendar and photo screens still work.

## 2. Full-screen intent (USE_FULL_SCREEN_INTENT declaration)

Category to pick: **Alarm-related / calling is not it → "Other" with the explanation below** (the form asks whether the
app's core function is alarms or calls; answer honestly: alarm-adjacent).

**KO**
> 풀스크린 인텐트는 알람과 맞물린 두 기능에만 씁니다. (1) 알람 전 미리 밝히기: 사용자가 정한 알람 시각 몇 분 전에 잠금 화면
> 위로 무드등을 서서히 밝혀 깨워 줍니다. (2) 충전 시작 시계: 화면이 꺼진 채 충전기를 꽂으면 잠금 화면 위에 탁상시계를 띄웁니다.
> 둘 다 사용자가 켠 설정일 때만 동작하며, 광고나 홍보에는 쓰지 않습니다.

**EN**
> The full-screen intent is used only for two alarm-adjacent features: (1) wake-up glow — a few minutes before the alarm
> time the user set, a light fades in over the lock screen; (2) charge-start clock — when the charger is plugged in with
> the screen off, a desk clock is shown over the lock screen. Both run only when the user turned them on, and never for
> ads or promotion.

## 3. Foreground service: mediaPlayback (FOREGROUND_SERVICE_MEDIA_PLAYBACK)

Task type: **Media playback**. User-visible: yes (ongoing notification with a 끄기 button). Video link: record 20 s —
수면 소리 card → play → notification → 끄기.

**KO**
> 수면 소리(백색 소음, 파도 등)를 사용자가 고른 시간 동안 재생합니다. 화면이 꺼지거나 다른 화면으로 가도 소리가 끊기지 않도록
> 재생하는 동안만 포그라운드 서비스를 쓰고, 알림에서 언제든 멈출 수 있습니다. 타이머가 끝나면 서비스도 끝납니다.

**EN**
> Plays sleep sounds (white noise, waves, …) for the length the user picked. The foreground service runs only while
> playing, so the sound continues with the screen off; the ongoing notification can stop it at any time, and the service
> ends with the sleep timer.

## 4. Exact alarms (SCHEDULE_EXACT_ALARM)

The app declares `SCHEDULE_EXACT_ALARM` (user-granted, off by default on Android 14+), not `USE_EXACT_ALARM`, so there is
no exact-alarm declaration form. If the Console still asks, paste:

**KO**
> 사용자가 시작한 집중 타이머가 정확한 시각에 끝나 알려 주고, 알람 전 미리 밝히기가 알람 시각에 맞춰 시작하도록 정확한 알람을
> 씁니다. 사용자가 권한을 주지 않으면 몇 분 늦을 수 있다고 안내합니다.

**EN**
> Exact alarms make a user-started focus timer end and alert on time, and start the wake-up glow in step with the alarm
> time. Without the grant the app says these may be a few minutes late.

## 5. Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS) — shown only if asked |
| Do you provide a way for users to request that their data is deleted? | Not applicable (nothing collected) |

Why "No": the only data leaving the device is the chosen city's latitude/longitude for the weather (Open-Meteo,
`api.open-meteo.com`). That is not the user's location (no location permission; the city is picked from a list), it is not
linked to a user, and it is not stored by us. Notifications, calendar and photos are processed on the device only
("processed ephemerally on device" is not "collected" in Play's definition).

**KO (설명 칸이 있으면)**
> 알림·캘린더·사진은 폰 안에서만 읽고 외부로 보내지 않습니다. 날씨를 위해 사용자가 고른 도시의 위도·경도만 Open-Meteo에
> 보내며, 이는 기기 위치가 아니고 사용자와 연결되지 않습니다. 광고, 분석 도구, 계정이 없습니다.

**EN**
> Notifications, calendar and photos are read on the device and never sent. For weather, only the latitude/longitude of
> the city the user picked is sent to Open-Meteo; it is not the device location and not linked to the user. No ads, no
> analytics, no account.

## 6. Content rating (IARC questionnaire)

- Category: **Utility, Productivity, Communication, or Other** (not a game).
- Violence, sexuality, language, controlled substances, gambling: **No** to all.
- User-generated content / users interact: **No** (no sharing, no chat).
- Shares the user's location: **No**. Digital purchases: **No** (closed test; billing comes later — update then).
- Expected: Everyone / 전체 이용가.

## 7. Target audience and content

- Target age groups: **18 and over** (also acceptable: 13+). Not designed for children → no Families policy.
- Appeals to children: **No**. Ads: **No ads**.
- App access: all features available without login (no credentials needed for review). Note for reviewers:

**EN**
> No login. Turn the screen on and plug in the charger, or add the Stand Screen quick-settings tile and tap it, to see
> the screen. Notification access, calendar and photo folders are optional (Settings → 권한, Settings → 사진).

## 8. Tester invite (closed test)

**KO**
> Stand Screen 비공개 테스트에 참여해 주셔서 고마워요.
> 1. 이 Google 그룹에 가입해 주세요: [그룹 링크]
> 2. 같은 Google 계정으로 이 링크를 열어 "테스터 되기"를 눌러 주세요: [옵트인 링크]
> 3. Play 스토어에서 Stand Screen을 설치해 주세요. **Android 14 이상** 폰이 필요해요.
> 4. 예전에 받은 테스트 앱(패키지 `kr.dot.standby.pub`)이 있으면 지워 주세요. 새 앱과 따로 깔려요.
> 5. **14일 동안** 지우지 말고 가끔 열어 주세요. Google이 정식 출시 전에 이 기간을 요구해요.
>
> 사진은 앱이 폴더를 직접 고르게 해요(DCIM › Camera, Pictures 가능, 저장소 맨 위와 Download는 Android가 막아요). 이름에
> 날짜가 없는 복사본 사진은 "N년 전 오늘"에 안 나올 수 있어요.

**EN**
> Thanks for testing Stand Screen.
> 1. Join this Google Group: [group link]
> 2. With the same Google account, open this link and tap "Become a tester": [opt-in link]
> 3. Install Stand Screen from the Play Store. Needs **Android 14 or newer**.
> 4. If you have the old test app (package `kr.dot.standby.pub`), uninstall it — the new one installs separately.
> 5. Keep it installed for **14 days** and open it now and then; Google requires this before a public release.
>
> Photos come from folders you pick (DCIM › Camera and Pictures work; Android blocks the storage root and Download).
> Copied photos without a date in the file name may not show up in "N years ago today".
