<teammate-message teammate_id="ia-opus" color="green" summary="Opus settings IA proposal">
Opus IA proposal for StandBy settings r10 (independent; read brief, Config.kt, HomeScreen/Board/Clock/More/Preview/Kit, PNGs home, home-fold, night, clock-digital).

## 1. Pain points
1. Home mixes inline controls (theme strip, 배경 chips, 글자 크기) with nav rows, under a big carousel preview: two previews stacked, home is ~3 screens tall on the cover (home.png, HomeScreen.kt).
2. "무드등" is both a theme name (theme strip) and the fold feature ("알람 · 무드등"), on the same home screen (home.png, home-fold.png).
3. Night OFF is the 3rd segment "끔" in the 3rd section of a screen titled "야간 모드 · 디스플레이" (night.png). With 화면 꺼짐 = 안 함 the whole night section disappears with no explanation of how to get it back (NightScreen `if (!never)`, night-never.png).
4. The night screen is a junk drawer: charging start, screen off, burn-in, night, notifications, 12/24h (night.png).
5. "데이터" holds sleep sound and pomodoro minutes, which are not data (DataScreen).
6. Two lux sliders ("3 lx 이하", "8 lx 이상") shown by default: jargon most people never touch (night.png).
7. Clock style: two rows of preview controls (device chips + 가로/세로) sit between the preview and the first setting; then 11 font tiles, 3 sliders, color, effect, background, numerals, layout, with nothing folded (clock-digital.png).
8. Clock style has 3 entry points (home → 시계 스타일 → list → face; 화면 → 시계 row; board editor → 시계 스타일 chips → "편집"). The list screen is one extra level.
9. Start page is set in two places (화면 "시작 화면" chips + board editor "시작 화면으로 설정" switch), and the widgets page in two places (home "위젯 카드" + first row of 화면) (BoardScreens.kt, HomeScreen.kt).
10. No settings preview can show night ink, though `PreviewFrame(night=)` already exists. 12/24h, a visible option, has no preview at all (it lives on the night screen).

## 2. Settings tree
Legend: [기본] shown, [자세히] folded, (F) = disabled on non-foldables with reason "폴더블폰에서만 쓸 수 있어요".

HOME "스탠바이"
- Pinned: live carousel (existing) + 낮/밤 toggle. Tap preview = full screen (the "전체 화면으로 미리보기" button is removed).
- Permission banner (existing, only when something is missing).
- Group **화면**
  - 테마와 글자 → nav, value = current theme thumbnail + name
  - 화면 구성 → nav, subtitle "위젯 · 사진 · 시계 + 내 화면 7개"
  - 시계 모양 → nav, subtitle = start clock name
- Group **밤과 꺼짐**
  - 밤 화면 → nav, value "어두워지면" / "정한 시간에 22–07" / **"안 씀"** (OFF visible from home)
  - 켜짐과 꺼짐 → nav, subtitle "충전하면 시작 · 어두우면 꺼짐"
  - 수면 소리 → bottom Sheet, value "빗소리 · 45분"
- Group **알람과 폴드**
  - 알람 → nav
  - 폴드 안쪽 화면 → nav (F: row disabled, one-line reason)
- Group **정보와 권한**
  - 카드에 쓰는 정보 → nav, subtitle "서울 날씨 · D-day 1개 · 타이머 25/5분"
  - 알림 → nav
  - 권한 → nav, badge "2개 필요" / value "모두 켜짐"
  - 정보 → nav

SCREEN 테마와 글자 (preview: widgets page)
- [기본] 테마: horizontal thumbnails (existing ThemePicker) → `theme`
- [기본] 글자 크기: segmented 작게/보통/크게 → `textSize`
- [기본] 시간 표시: segmented 시스템 설정/12시간/24시간 → `use24h`
- [자세히] 배경: chips → `boardBg`; + color, only when 단색 고르기 → `boardBgColor`

SCREEN 화면 구성 (preview: the page chosen as start page; tapping a row swaps the preview to that page)
- [기본] 시작 화면: chips 위젯/사진/시계/boards… → `startPage`
- [기본] page list: 위젯 (nav → widgets stack screen), 사진 (no nav), 시계 (nav → 시계 모양), boards with ▲▼ → `boards` order
- [기본] 화면 추가 button
- Widgets stack screen: unchanged (left/right stacks → `widgetLeft`/`widgetRight`).
- Board editor (preview = this board with slot overlay; tapping a slot opens the card picker):
  - [기본] 칸 나누기: layout tiles → `Board.layout`
  - [기본] 칸 N stacks → `Board.slots` (phone widget row "설정" → widget options, which stay as they are: 투명도, 다크·라이트, 모서리, 밤에 숨기기 → `SlotOptions`)
  - [자세히] 이름: field → `Board.name` (the title is also tappable)
  - [자세히] 강조 색: segmented 시계 따라/직접 + color → `Board.accent`
  - [자세히] 이 화면의 시계: chips → `Board.clockFace` (no "편집" link; editing goes through 시계 모양)
  - [자세히] 복제 / 이 화면 지우기
  - Removed: "시작 화면으로 설정" switch

SCREEN 시계 모양 (merges ClockList and ClockStyle)
- Under the preview: face chips 디지털·아날로그·세계·솔라·플로트·책상·사진 위 시간. They pick which clock you edit and are not saved.
- Digital/World/Solar/Float/Desk/Photos:
  - [기본] 서체: font tiles → `font` (+ default weight)
  - [기본] 글자 색: color → `color` (세계: "도시 색"; 솔라: 솔라 팔레트 row above it → bg/color pair)
  - [기본] 크기: slider → `sizePct`
  - [자세히] 굵기, 너비 sliders → `weight`, `widthPct`
  - [자세히] 효과: segmented → `effect`; 두 번째 색 when 두 색 → `color2`
  - [자세히] 배경: segmented + colors → `background`, `bgColor`, `bgColor2`
  - [자세히] 배치 → `layout`; 날짜와 온도 표시 switch → `showInfo`; 밝은 점 (세계) → `litDotColor`
  - [자세히] 숫자 모양: chips → `numerals`
  - [자세히] 테마 기본값으로 되돌리기
- Analog:
  - [기본] 모양: 5 live thumbnails → `analogStyle` (global)
  - [기본] 시침 · 분침 색, 초침 색 → `handColor`, `secondColor`
  - [기본] 숫자: segmented → `analogNumbers` (round/rect dials only)
  - [자세히] 눈금 → `analogTicks`; 숫자·눈금·요일 색 → `numeralColor`/`tickColor`/`weekdayColor`; 날짜와 요일 표시 → `showInfo`; 서체·크기·너비·굵기; 배경; 숫자 모양; 되돌리기

SCREEN 밤 화면 (preview: widgets page, toggle opens on **밤**)
- [기본] 밤 화면 쓰기: **switch**. Off → `nightMode=OFF`, subtitle "밤에도 낮과 같은 화면이에요". On → AUTO.
- [기본] 바꾸는 때: segmented 어두워지면/정한 시간에 → `nightMode` AUTO/SCHEDULE (disabled when the switch is off)
- [기본] 시작 · 끝: two sliders (SCHEDULE only) → `nightStartHour`, `nightEndHour`
- [기본] 밤 밝기: slider → `nightMaxBrightness`
- [자세히] 지금 주변 밝기 (live lux readout) + 어두워졌다고 볼 밝기 / 밝아졌다고 볼 밝기 → `nightEnterLux`, `nightExitLux` (AUTO only)
- When `displayOff=NEVER`: every row stays visible but disabled, with reason "화면을 늘 켜 두면 밤 화면을 쓸 수 없어요 · 켜짐과 꺼짐에서 바꾸기" (link).

SCREEN 켜짐과 꺼짐 (no preview: nothing visible changes)
- [기본] 충전기 꽂으면 시작: switch → `startOnCharge`
- [기본] 화면 꺼짐: segmented 자동/20초 후/늘 켜 둠 → `displayOff` + note
- [기본] 움직이면 켜기: switch → `motionToWake` (AUTO only, otherwise disabled with reason)
- [자세히] 무선 충전일 때만 → `chargeWirelessOnly`
- [자세히] 낮 밝기: switch "자동 밝기 따라" + slider → `dayBrightness` (null = follow the system). It already exists in AppConfig but today it can only be set from the on-screen bar.
- [자세히] 화면 위치 조금씩 옮기기 → `burnInShift`
- [자세히] footer: charge-start trail log

SHEET 수면 소리: 소리 chips → `sleepSound`; 꺼지는 시간 chips → `sleepMinutes`.

SCREEN 알람 (preview: the alarm screen sample)
- [기본] StandBy 알람 화면: switch → `alarmScreen`
- [기본] 안쪽 화면 무드등: switch → `alarmGlow` (F)
- [자세히] 알람 전에 미리 밝히기: segmented 끔/5/10/15분 → `glowStartMinBefore` (F; the exact-alarm warning stays)
- [자세히] 밝아지는 시간: slider → `glowRampSec` (F)
- Footer: "알람 소리와 끄기는 시계 앱이 맡아요."

SCREEN 폴드 안쪽 화면 (F on the whole screen; preview: a portrait inner-screen frame filled with the glow gradient)
- [기본] 펼쳤을 때: segmented 책 보드/무드등 → `onUnfold`
- [기본] 무드등 색: 5 named presets + "직접 고르기" swatch in **one** row → `glowColor`, note "알람 불빛도 이 색이에요"
- [기본] 무드등 테스트 button

SCREEN 카드에 쓰는 정보 (no preview)
- [기본] 날씨 지역 → `city`; [기본] D-day list + add → `ddays`
- [기본] 타이머: 집중 / 휴식 sliders → `focusMinutes`, `breakMinutes`
- [자세히] 세계 시계 도시 → `worldCities`
- [기본, personal build only] Claude·Codex 사용량 → `usageEnabled`

SCREEN 알림 (preview: notification card sample)
- [기본] 알림 보기 → `showNotifications`; [기본] 탭할 때만 미리보기 → `previewOnTapOnly`; footer pointing to the notification permission if it is missing.

권한, 정보: unchanged.

## 3. Live preview behaviour
- Frame: landscape 751×475 scaled to page width (about 240 dp tall on 411 dp), pinned via `Page(header=)`. Top-right overlay pill **낮 | 밤** (preview-only state, not saved). Top-left overlay chip "폴드 커버 ▾" opens the device + 가로/세로 choice. This replaces the two rows under the preview on clock and board screens.
- Night pill renders `PreviewFrame(night=1f)` in night ink, capped by `nightMaxBrightness` as a dim scrim. If `nightMode=OFF`, 밤 shows day ink with the caption "밤에도 이렇게 보여요", which proves the off state.
- What animates (crossfade ~200 ms): theme, textSize, boardBg, use24h, clock font/color/size/effect/background, analog style, layout/card changes, the night-brightness slider (dims live), glow color, the fold gradient. Lux sliders change nothing in the preview (that is one reason they are folded).
- Default toggle: 밤 on 밤 화면, 낮 elsewhere.
- Fold inner (≥600 dp): the existing two-pane `Page`. Left 55 % holds the sticky preview at full pane width, the pills and the device chip. Right pane holds the rows, with 자세히 expanded by default (there is room). Home: carousel on the left, the 4 groups on the right. Screens without a preview stay single-pane (centred, max 720 dp).

## 4. Remove / merge / rename
- `NightMode` labels: OFF "끔" → switch "밤 화면 쓰기"; AUTO "자동 (조도)" → "어두워지면"; SCHEDULE "시간 예약" → "정한 시간에". Reason: this answers "just use it" with one switch.
- Theme MOODLIGHT "무드등" → "램프". Reason: it collides with the fold mood light.
- `DisplayOff.NEVER` "안 함" → "늘 켜 둠". Reason: "안 함" reads as "do nothing".
- Merge the clock list and the clock style screen into one screen with face chips. Reason: one level fewer.
- Remove the start-page switch from the board editor (keep the chips on 화면 구성). Remove the home "위젯 카드" row. Reason: one place each.
- Remove the board editor's "스타일 편집" link. Reason: a third entry to clock style.
- Merge the glow presets and the "직접 고르기" palette into one row. Remove the "효과" footer ("밤에는 모든 효과가 꺼져요"); the night pill shows it.
- Rename "야간 모드 · 디스플레이" → split into 밤 화면 + 켜짐과 꺼짐; "데이터" → "카드에 쓰는 정보"; sleep sound leaves it.
- 12/24h moves to 테마와 글자; notifications get their own screen in 정보와 권한.

## 5. Least sure
1. Switch + "바꾸는 때" segmented vs a single 3-way segmented with OFF first ("안 씀 / 어두워지면 / 정한 시간에"). The switch is clearer, but turning it back on resets to AUTO: AppConfig keeps no memory of SCHEDULE, unless we accept losing it or add a field.
2. Theme moved off home into 테마와 글자 (one more tap for the most visible option) vs keeping the strip on home. I chose a clean nav-only home; home still previews the theme.
3. One clock screen with 7 face chips. On 411 dp the chips wrap to 2 rows, or need a horizontal scroll, and "사진 위 시간"/"책상" may confuse more than a list. The fallback is to keep the list as the screen's first state.
