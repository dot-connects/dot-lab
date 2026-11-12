<teammate-message teammate_id="ia-fable" color="blue" summary="StandBy settings IA proposal (Fable)">
StandBy settings r10 IA proposal (Fable): home becomes preview + 4 purpose groups of nav rows only; 13 leaf screens; every leaf shows at most 4 rows before "자세히".

Basis: brief, Config.kt, HomeScreen/MoreScreens/BoardScreens/ClockScreens/Preview/Perms.kt, and PNGs home, night, alarm, clock-digital, board-editor, boards, data. I did not open the other PNGs and did no web reference check; iOS/One UI comparisons below are from memory.

## 1. Pain points today
1. home.png: home mixes three jobs (carousel, theme picker, bg/text controls, then 3 nav sections). The first nav row is below the fold at 914dp.
2. HomeScreen.kt sections "화면 구성 / 동작 / 연결" are named by implementation, not purpose; "연결" holds D-day and timers.
3. night.png: one 3400px page "야간 모드 · 디스플레이" holds 5 unrelated topics (charge start, screen off, night, notifications, 12/24h). No preview.
4. night.png: `NightMode.OFF` is the third segment "끔", under the fold, inside a section titled the same as the page. Fails decision 4.
5. night.png: lux thresholds ("어두워졌다고 볼 밝기 3 lx") are first-level rows; sensor jargon shown to everyone.
6. MoreScreens.kt:137: `displayOff = NEVER` silently disables night mode and motion wake, explained only in a footer on a different section.
7. alarm.png: the page opens with a colour swatch; alarm switches and "폴드를 펼쳤을 때" come after it. `glowColor` serves two features but reads as an alarm setting.
8. data.png: "데이터" is a junk drawer (weather city, world cities, D-day, sleep sound, pomodoro, usage). The city search + 8 chips block is rendered twice.
9. clock-digital.png: 9 sections, ~25 controls, flat. Device chips (폴드 커버/펼침/바형/플립) + 가로/세로 sit between preview and options and push "서체" down; no day/night toggle.
10. boards.png + home.png: start page is settable in 3 places (home carousel pill, 시작 화면 chips, editor switch); "위젯 카드" is on home and again as the first row of 화면 순서. board-editor.png: per-board accent and clock face buried below the slots.

## 2. Settings tree
Legend: [B] 기본, [D] 자세히, (F) disabled on non-foldables with reason "접는 폰에서만 쓸 수 있어요".

**Home "스탠바이"**: permission banner (only when missing) → live preview pager (pages = widgets/photos/clocks/boards) with 낮/밤 toggle and "전체 화면으로 보기" → groups. Every nav row shows its current value on the right.

**A. 화면**
- 테마와 배경 (nav, value = theme name)
  - 테마: preview strip → `theme` [B]
  - 배경: chips → `boardBg` [B]; colour → `boardBgColor` (only when 단색 고르기) [B]
  - 글자 크기: segmented 작게/보통/크게 → `textSize` [B]
- 화면 구성 (nav, "내 화면 7개")
  - list in page order: 위젯 (nav → card stacks `widgetLeft`/`widgetRight`), 사진, 시계 (nav → 시계), my boards with drag handle + "화면 추가" → `boards` [B]
  - 처음 보이는 화면: nav → single-choice list → `startPage` [B] (one place only)
  - Board editor: tap-a-slot preview [B]; 칸 나누기 thumbs → `Board.layout` [B]; 칸별 카드 → `Board.slots` [B]; 이름 text → `Board.name` [D]; 강조 색 segmented+colour → `Board.accent` [D]; 이 화면의 시계 chips → `Board.clockFace` [D]; 복제 / 삭제 [D]
  - 폰 위젯 options (from a slot): 위젯 고르기 nav → `SlotOptions.widgetId` [B]; 다크·라이트 segmented → `widgetMode` [B]; 밤에 숨기기 switch → `hideAtNight` [B]; 투명도 slider → `opacity` [D]; 모서리 slider → `radiusDp` [D]
- 시계 (nav)
  - 12/24시간: segmented 폰 설정 따라/12시간/24시간 → `use24h` [B]
  - face list (디지털, 아날로그, 세계, 솔라, 플로트, 책상, 사진 위 시간) → face editor:
    - 모양 (analog only): thumbs → `analogStyle` [B]
    - 글꼴: tile grid → `font` [B]
    - 색: colour → `color` (solar: 팔레트 → `bgColor/bgColor2/color/color2`) [B]
    - 크기: slider → `sizePct` [B]
    - 날짜와 날씨 표시: switch → `showInfo` [B]
    - [D] 굵기 `weight`, 너비 `widthPct`, 효과 `effect` (+ `color2`), 배경 `background` (+ `bgColor`, `bgColor2`), 배치 `layout`, 숫자 모양 `numerals`, analog: 숫자 `analogNumbers`, 눈금 `analogTicks`, 바늘/초침/요일/눈금/숫자 색 (`handColor`, `secondColor`, `weekdayColor`, `tickColor`, `numeralColor`), world: `litDotColor`
    - "테마 기본으로 되돌리기" [D] (removes the face from `styles`)
- 알림 표시 (nav, "켜짐")
  - 알림 보기: switch → `showNotifications` [B]
  - 누를 때만 내용 보기: switch → `previewOnTapOnly` [B]

**B. 밤과 꺼짐**
- 밤 화면 (nav, value "자동 / 22시~7시 / 쓰지 않음")
  - 밤 화면 쓰기: master switch at the very top. Off → `nightMode = OFF`, everything below dims, note "낮과 밤 구분 없이 같은 화면으로 보여요". On → restores last AUTO/SCHEDULE [B]
  - 바뀌는 때: segmented 어두워지면/정한 시간에 → `nightMode` AUTO/SCHEDULE [B]
  - 시작 / 끝: two sliders → `nightStartHour`, `nightEndHour` (SCHEDULE only) [B]
  - 밤 밝기: slider → `nightMaxBrightness` [B]
  - [D] 지금 주변 밝기 (read-only), 어두워졌다고 볼 밝기 → `nightEnterLux`, 밝아졌다고 볼 밝기 → `nightExitLux`
- 켜짐과 꺼짐 (nav, "충전하면 켜짐")
  - 충전하면 켜기: switch → `startOnCharge` [B]
  - 화면 끄기: segmented 자동/20초 뒤/끄지 않음 → `displayOff` [B]
  - 움직이면 켜기: switch → `motionToWake` (disabled when 끄지 않음, reason inline) [B]
  - [D] 무선 충전일 때만 → `chargeWirelessOnly`; 잔상 막기 → `burnInShift`; 최근 기록 (charge trail)
- 수면 소리 (nav, "빗소리 · 45분")
  - 소리: chips → `sleepSound` [B]; 꺼지는 시간: chips → `sleepMinutes` [B]

**C. 알람과 폴드**
- 알람 화면 (nav, "켜짐")
  - 스탠바이 알람 화면: switch → `alarmScreen` [B]
  - 안쪽 화면 무드등: switch → `alarmGlow` (F) [B]
  - 알람 전에 미리 밝히기: segmented 끔/5/10/15분 → `glowStartMinBefore` (F) [B]
  - [D] 밝아지는 시간: slider → `glowRampSec` (F)
- 펼쳤을 때 (nav, "책 보드") (F: row disabled on home with the reason line)
  - 펼치면 보이는 것: segmented 책 화면/무드등 → `onUnfold` [B]
  - 무드등 색: 5 named presets + custom → `glowColor` [B] (the alarm screen links here: "색 바꾸기")

**D. 정보와 권한**
- 날씨와 도시 (nav, "서울"): 날씨 지역 → `city` [B]; 세계 시계 도시 → `worldCities` [B]. One shared search sheet, not two inline blocks.
- 카드에 쓰는 것 (nav): D-day list → `ddays` [B]; 집중 / 휴식 sliders → `focusMinutes`, `breakMinutes` [B]; Claude·Codex 사용량 switch → `usageEnabled` (personal build only) [B]
- 권한 (nav, badge "4개 필요" / "모두 켜짐")
- 앱 정보 (nav)

## 3. Live preview per screen
Shared: a pinned landscape 751×475 frame (~379×240dp on the 411 portrait page; left pane on the Fold inner screen), with a 낮/밤 pill in its corner. Device chips and 가로/세로 move into a "다른 기기로 보기" menu on the preview, defaulting to this phone. Changes cross-fade at 200ms; no other motion.
- Home: pager over all pages; 낮/밤 applies to all.
- 테마와 배경: widgets page; theme tap cross-fades; background change animates; text size switches the preview to a notification/agenda board so the change is visible.
- 화면 구성: no preview (thumbnails in the list are the preview). Board editor: that board, slots tappable.
- 시계 face editor: that face; sliders update continuously; switching to 밤 shows that effects turn off.
- 알림 표시: the notification card with a sample message; "누를 때만" collapses it to an icon.
- 밤 화면: opens with the pill forced to 밤; the brightness slider dims the frame live; master off → frame returns to day ink and the pill is disabled.
- 켜짐과 꺼짐: clock face; 화면 끄기 choice plays a 2s fade-to-black loop once; 잔상 막기 shows an exaggerated shift once.
- 수면 소리: no preview; tapping a chip plays 3s of the sound.
- 알람 화면: the ringing alarm screen; with 무드등 on, a strip behind the frame ramps in `glowColor` over `glowRampSec`.
- 펼쳤을 때: 932×704 inner-screen frame showing the book board or the glow in the chosen colour.
- 정보와 권한 screens: no preview (nothing visible changes except city label).

## 4. Remove / merge / rename
- Merge: home "위젯 카드" row into 화면 구성 (it is already the first list row).
- Merge: three start-page controls into one "처음 보이는 화면" row; delete the editor switch and the chips block.
- Split: "야간 모드 · 디스플레이" into 밤 화면, 켜짐과 꺼짐, 알림 표시; 12/24h moves to 시계.
- Split: "데이터" into 날씨와 도시, 카드에 쓰는 것, 수면 소리.
- Rename: 야간 모드 → 밤 화면; 디스플레이 "안 함" → 끄지 않음; 화면 위치 조금씩 옮기기 → 잔상 막기; 탭할 때만 미리보기 → 누를 때만 내용 보기; 책 보드 → 책 화면; "StandBy 알람 화면" → 스탠바이 알람 화면 (the app title is already 스탠바이).
- Remove from UI: `Numerals` other than 서양 stay only under 자세히; I would drop the row entirely if the other designer agrees (no Korean user needs Khmer digits), but it exists so I kept it folded.
- Not placed: `dayBrightness`. It is set from the on-screen bar only (platform/Brightness.kt) and has no settings row today; I keep it that way.
- Remove: lux numbers from first level; "Open-Meteo에서 30분마다" footer moves to 앱 정보.

## 5. Least sure
1. Theme picker leaves home. Home becomes a pure index, which fixes pain point 1, but theme is the most-touched option and now costs one tap. Alternative: keep the strip on home under the preview.
2. Night master switch + 2-way segmented instead of a 3-way segmented with "쓰지 않음" first. The switch makes "off" unmissable but adds a control and needs "last mode" memory (derivable: default AUTO, no new field).
3. 수면 소리 under 밤과 꺼짐 (operator's grouping) while timers and D-day sit under 카드에 쓰는 것. Consistent-by-purpose, but card content is then split across two groups; the alternative is one "카드에 쓰는 것" screen holding all three.
