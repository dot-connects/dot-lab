# r15 settings map (plan-r15 F1b step 0)

Every row in `settings/` at base 30b95d37 → where it lives after Part F. `file:line` = before. Nothing is dropped
unless the "new" column says so.

## Home (HomeScreen.kt)

| before | row | after |
|---|---|---|
| HomeScreen.kt:69 | title 스탠바이 + page pager | Home: title "Stand Screen", one preview with page dots + 지금 보기 |
| HomeScreen.kt:70 | PermBanner (any of 7 missing) | Home banner only while DREAM (charge-start) is missing |
| HomeScreen.kt:75 | 테마와 배경 | Home › 테마 |
| HomeScreen.kt:80 | 페이지 구성 | Home › 페이지와 카드 |
| HomeScreen.kt:82 | 카드 (card list) | dissolved: each card's gear in 페이지와 카드 (see 카드 below) |
| HomeScreen.kt:91 | 밤 화면 | Home › 밤 화면 |
| HomeScreen.kt:96 | 켜짐과 꺼짐 | Home › 자동으로 켜기·끄기 |
| HomeScreen.kt:101 | 폴드를 펼쳤을 때 | 자동으로 켜기·끄기 › 펼쳤을 때 (Fold only) |
| HomeScreen.kt:104 | 권한 | 정보 › 권한 |
| HomeScreen.kt:105 | 앱 정보 | Home › 정보 |
| HomeScreen.kt:106 | 개인정보처리방침 | 정보 › 개인정보 처리방침 |
| — | (new) | Home › 시계, 사진, 알림과 알람, 팁 |
| HomeScreen.kt:119-124 | 배경 chips + 사진 흐림 folder hint + 단색 palette | 테마 › 배경 (unchanged place) |

## 카드 (CardScreens.kt) → card gears

| before | row | after |
|---|---|---|
| CardScreens.kt:58 (NOTIF) | 알림 | 알림과 알람 › 알림 (card gear → 알림과 알람) |
| CardScreens.kt:58 (ALARM) | 알람 | 알림과 알람 › 알람 (card gear → 알림과 알람) |
| CardScreens.kt:58 (CLOCK) | 시계 | Home › 시계 (card gear → 시계) |
| CardScreens.kt:63 (WEATHER) | 날씨 지역 sheet | 시계 › 날씨 지역 (global); weather card gear → card screen with the same row |
| CardScreens.kt:58 (SLEEP) | 수면 소리 | card gear → 수면 소리 ("모든 수면 소리 카드에 적용돼요.") |
| CardScreens.kt:58 (AGENDA/CALENDAR) | 캘린더 | card gear → 오늘 일정 · 달력 (F12 picker, "모든 … 카드에 적용돼요.") |
| CardScreens.kt:58 (DDAY) | D-day 목록 | card gear → D-day ("모든 D-day 카드에 적용돼요.") |
| CardScreens.kt:58 (TIMER) | 집중 · 휴식, 끝나면 알리기 | card gear → 집중 타이머 ("모든 집중 타이머 카드에 적용돼요.") |
| CardScreens.kt:58 (USAGE) | 사용량 | card gear → card screen (personal build) |
| CardScreens.kt:96 | 설정할 게 없는 카드예요 | card screen copy "이 카드는 바로 쓰면 돼요." (cards without a gear never open it) |

## 시계 (ClockScreens.kt)

| before | row | after |
|---|---|---|
| ClockScreens.kt:100-104 | six tiles | 시계 (unchanged, E5 rules) |
| ClockScreens.kt:107 | 모든 시계 보기 | 시계 |
| ClockScreens.kt:108 | 꾸미기 | 시계 |
| ClockScreens.kt:109 | 여러 시계 돌려 보기 | 시계 |
| ClockScreens.kt:114 | 시간 표시 | 시계 (자세히 → visible rows) |
| ClockScreens.kt:122 / MoreScreens.kt:387 | 세계 시계 도시 | 시계 |
| — | (new) | 시계 › 날씨 지역 (from 카드 › 날씨) |
| ClockScreens.kt:168 | 여러 시계 돌려 보기 switch | 시계 › 여러 시계 돌려 보기 |
| ClockScreens.kt:349-446 | every StyleRow control | 시계 › 꾸미기 (words instead of numbers on 크기/굵기) |

## 페이지와 카드 (BoardScreens.kt)

| before | row | after |
|---|---|---|
| BoardScreens.kt:90 | 처음 보이는 페이지 | 페이지와 카드 |
| BoardScreens.kt:91-117 | page rows (switch, ▲▼) | 페이지와 카드: thumbnail + name + chevron; switch and drag order in 편집 |
| BoardScreens.kt:121-135 | 페이지 추가 sheet | 페이지와 카드 |
| BoardScreens.kt:224 | 위젯 stacks | 페이지와 카드 › 기본 페이지 (card rows get a gear) |
| BoardScreens.kt:267-286 | 사진 폴더, 사진 넘김 간격 | Home › 사진 |
| BoardScreens.kt:341-358 | 칸 나누기 | page editor |
| BoardScreens.kt:365 | slot stacks | page editor (card rows get a gear) |
| BoardScreens.kt:380-394 | 이름, 강조 색, 이 페이지의 시계 | page editor › 자세히 |
| BoardScreens.kt:399-404 | 복제, 지우기 | page editor: 복사, 지우기 (confirm dialog) |
| BoardScreens.kt:586 | 아이콘 색 | 앱 고르기 (card gear of 앱 바로가기) |

## 밤 화면 / 자동으로 켜기·끄기 / 알림과 알람 (MoreScreens.kt)

| before | row | after |
|---|---|---|
| MoreScreens.kt:90-98 | 알림 보기, 누를 때만 내용 보기 | 알림과 알람 › 알림 |
| MoreScreens.kt:119-125 | 밤 모드 radios | 밤 화면 |
| MoreScreens.kt:128-129 | 시작 / 끝 시 | 밤 화면 |
| MoreScreens.kt:133 | 밤 밝기 | 밤 화면 (words) |
| MoreScreens.kt:140 | 지금 주변 밝기 | 밤 화면 › 지금 방 밝기 (밤/낮 pill, no number) |
| MoreScreens.kt:143-148 | 어두워졌다고/밝아졌다고 볼 밝기 | 밤 화면: one slider "얼마나 어두우면 밤으로 볼까요" (exit = enter + 2) |
| MoreScreens.kt:206-222 | 충전하면 켜기, 무선 충전일 때만, 가로로 놓였을 때만 | 자동으로 켜기·끄기 |
| MoreScreens.kt:225 | 화면 꺼짐 | 자동으로 켜기·끄기 › 화면 끄기 |
| MoreScreens.kt:230-236 | 움직이면 켜기, 밤에 알림 오면 화면 켜기 | 자동으로 켜기·끄기 |
| MoreScreens.kt:244-245 | 발열 보호, 잔상 막기 | 자동으로 켜기·끄기 › 자세히 |
| — | (new) | 자동으로 켜기·끄기: battery PermLine |
| MoreScreens.kt:262-273 | 수면 소리, 꺼지는 시간 | 수면 소리 card gear |
| MoreScreens.kt:291-292 | 알람이 울릴 때 | 알림과 알람 › 알람 |
| MoreScreens.kt:300-319 | 안쪽 화면 무드등, 미리 밝히기, 밝아지는 시간, 무드등 색 (Fold) | 알림과 알람 › 알람 (Fold) |
| MoreScreens.kt:335-359 | 펼치면, 무드등 밝기·색·효과·빠르기, 바깥 화면에 시계, 자동 끄기 | 자동으로 켜기·끄기 › 펼쳤을 때 (Fold) |
| MoreScreens.kt:364 | 무드등 테스트 | 자동으로 켜기·끄기 › 무드등 켜 보기 |
| MoreScreens.kt:439 | D-day | D-day card gear |
| MoreScreens.kt:458-473 | 집중 · 휴식, 끝나면 알리기 | 집중 타이머 card gear |
| MoreScreens.kt:482-489 | 캘린더 switches | 오늘 일정 · 달력 card gear (F12) |
| MoreScreens.kt:502 | 사용량 | 사용량 card gear |
| MoreScreens.kt:580-604 | 권한 checklist | 정보 › 권한 |
| MoreScreens.kt:622 | 버전 | 정보 |
| MoreScreens.kt:626-645 | 최근 켜짐 기록, 측정 기록 (personal) | 정보 |
| MoreScreens.kt:648 | 글꼴 라이선스 | 정보 › 글꼴 라이선스 |
| — | (new) | 정보 › 처음 설정으로 |

## 테마 (HomeScreen.kt ThemePicker, ThemeEditor.kt)

| before | row | after |
|---|---|---|
| HomeScreen.kt:176-195 | strip + 내 테마 long-press menu | 테마: strip, 내 테마 tiles get a visible ⋯ |
| ThemeEditor.kt:148-169 | every Control row | 직접 만들기: 배경 색 · 카드 모양 · 글자 색 first, the rest under 더 꾸미기 |
| ThemeEditor.kt:459 | 테마 기본으로 바꾸기 | 직접 만들기 (unchanged) |
