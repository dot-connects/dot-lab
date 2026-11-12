# Settings r10 — consensus tree (after 2 debate rounds: Fable 5.2 × Opus 5.5 + first-time-user walkthrough)

Inputs: `brief.md`, `ia-fable.md`, `ia-opus.md`, `ia-user.md` and the two round-2 replies (summarised below).
Legend: [기본] shown · [자세히] folded under "자세히" · (F) disabled on non-foldables, reason "폴더블폰에서만 쓸 수 있어요".

## Lead's calls on the points still split after round 2
| Point | Fable | Opus | Call | Why |
|---|---|---|---|---|
| Night off control | 3 full-width radio rows, 안 씀 first, one-line description each | 3-way segmented, 안 씀 first | **Radio rows** | the user's task-1 confusion was two look-alike 3-segment bars stacked; radio rows with descriptions remove it; neither needs a new field |
| 12/24h | 테마와 글자 | first row of 시계 | **시계, first row** | it is about the clock; global row sits above the per-face editor |
| 수면 소리 | bottom sheet | own screen | **own screen** (tap a sound = 3 s sample) | every home row opens a screen; the user asked for it on its own |
| Alarm control | segmented "스탠바이 화면 / 시계 앱 화면" | switch "StandBy 알람 화면" | **segmented "알람이 울릴 때: 스탠바이 화면 / 시계 앱 화면"** | user task 4: a switch named after the app was not read as "which alarm screen" |
| Fold light name | "안쪽 화면 조명" | keep "무드등" + subtitle | **keep 무드등 + subtitle "펼친 안쪽 화면이 은은한 불빛이 돼요"** | operator's own word; theme 무드등 → 램프 removes the clash (operator to confirm the theme rename) |
| Home preview | scrolls with page, ~180 dp | pinned | **scrolls, ~180 dp** on home; pinned on leaf screens | home changes nothing itself and has 12 rows |

## Agreed by both (round 2)
- Home = preview (scrolls) + permission banner + 4 groups of nav rows only, each row shows its current value.
- Boards are called **페이지** everywhere ("화면" now only means the physical screen).
- Permissions live at the feature (inline red sub-line "설정 한 가지가 더 필요해요 · 설정하기"); the 권한 screen becomes a checklist (missing first with big buttons, granted as one line), plain names.
- Start page set in exactly one place: "처음 보이는 페이지" nav row. Home "위젯 카드" row removed (it is the first row of 페이지 구성).
- One clock screen: horizontal strip of live mini faces (name under each) → editor of the picked face below the pinned preview. No list level, no text chips.
- `displayOff = 계속 켜 둠` disables (not hides) the 밤 화면 rows with a reason and a link to 켜짐과 꺼짐.
- Preview chrome: tap = full screen; 낮|밤 pill top-right; device chip "폴드 커버 ▾" top-left (replaces the device/가로·세로 rows).
- 밤 pill when night is 안 씀: day ink + caption "밤에도 이렇게 보여요".
- No 켜짐과 꺼짐 preview; no dayBrightness row (the on-screen bar owns it).
- 글자 크기 switches the preview to a page with notification/agenda text.
- Renames: 야간 모드 → 밤 화면 (subtitle "어두워지면 화면이 붉고 어둡게 바뀌어요", no "틴트"); 디스플레이 자동/20초 후/안 함 → 화면 꺼짐 어두워지면/20초 뒤/계속 켜 둠; 화면 위치 조금씩 옮기기 → 잔상 막기; 탭할 때만 미리보기 → 누를 때만 내용 보기; 책 보드 → 책 화면; 데이터 → split; 달력 card → 이번 달 달력.

## Tree
**Home "스탠바이"**: preview pager (scrolls, 낮|밤) → permission banner "충전 중 스탠바이가 뜨려면 설정이 더 필요해요 · 8개 중 4개 완료" (only if missing) → groups:

**A. 화면**
- 테마와 배경 (value: theme name) — preview: 위젯 page
  - [기본] 테마 strip → `theme` · [기본] 글자 크기 작게/보통/크게 → `textSize` (preview flips to 오늘 일정+알림 page) · [기본] 배경 chips → `boardBg` (+ color when 단색) 
- 페이지 구성 (value: "페이지 7개") — no pinned preview, thumbnails are the preview
  - [기본] 처음 보이는 페이지 (nav → single-choice list) → `startPage`
  - [기본] page list in order: 위젯 (→ left/right stacks), 사진, 시계, my pages with ▲▼ → `boards` · 페이지 추가
  - Page editor (preview = this page, slots tappable): [기본] 칸 나누기 → `layout`; 칸별 카드 → `slots` (phone widget → 위젯 고르기/다크·라이트/밤에 숨기기 [기본], 투명도/모서리 [자세히]); [자세히] 이름, 강조 색, 이 페이지의 시계, 복제/지우기
- 시계 (value: start clock name) — preview: the picked face
  - [기본] 시간 표시 폰 설정 따라/12시간/24시간 → `use24h`
  - face strip (live minis: 디지털·아날로그·세계·솔라·둥근 굵은 글씨(플로트)·책상·사진 위 시간)
  - Digital-type: [기본] 서체, 글자 색, 크기, 날짜와 날씨 표시 · [자세히] 굵기, 너비, 효과(+두 번째 색), 배경, 배치, 숫자 모양, 테마 기본값으로 되돌리기
  - Analog: [기본] 모양 (5 thumbnails, `analogStyle`), 시침·분침 색, 초침 색, 숫자 · [자세히] 눈금, 숫자·눈금·요일 색, 서체·크기, 배경, 되돌리기
- 알림 표시 (value: 켜짐) — preview: notification card sample
  - [기본] 알림 보기 → `showNotifications` (+ inline permission 알림 읽기) · [기본] 누를 때만 내용 보기 → `previewOnTapOnly`

**B. 밤과 꺼짐**
- 밤 화면 (value: 안 씀 / 어두워지면 / 정한 시간에 22–07) — preview opens on 밤
  - [기본] radio rows: 안 씀 "밤에도 낮과 같은 화면" / 어두워지면 "방이 어두워지면 붉고 어둡게" / 정한 시간에 "22시~7시" → `nightMode`
  - [기본] 시작·끝 (정한 시간에 only) → `nightStartHour/EndHour` · [기본] 밤 밝기 → `nightMaxBrightness` (dims preview live)
  - [자세히] 지금 주변 밝기 (live), 어두워졌다고 볼 밝기, 밝아졌다고 볼 밝기 → lux (어두워지면 only)
- 켜짐과 꺼짐 (value: 충전하면 켜짐) — no preview
  - [기본] 충전하면 켜기 → `startOnCharge` (+ inline permission 충전 중 스탠바이 띄우기) · [기본] 화면 꺼짐 어두워지면/20초 뒤/계속 켜 둠 → `displayOff` · [기본] 움직이면 켜기 → `motionToWake`
  - [자세히] 무선 충전일 때만 → `chargeWirelessOnly`, 잔상 막기 → `burnInShift`, 최근 켜짐 기록
- 수면 소리 (value: 빗소리 · 45분) — no preview, tap = 3 s sample
  - [기본] 소리 → `sleepSound` · [기본] 꺼지는 시간 → `sleepMinutes`

**C. 알람과 폴드**
- 알람 (value: 스탠바이 화면) — preview: ringing alarm screen (+ glow strip on foldables)
  - [기본] 알람이 울릴 때 스탠바이 화면/시계 앱 화면 → `alarmScreen`
  - [기본] 안쪽 화면 무드등 (F) → `alarmGlow` · [기본] 알람 전에 미리 밝히기 끔/5/10/15분 (F) → `glowStartMinBefore` (+ inline permission 알람 시각 미리 알기)
  - [자세히] 밝아지는 시간 (F) → `glowRampSec`
  - footer "알람 소리와 끄기·다시 알림은 언제나 시계 앱이 맡아요."
- 폴드를 펼쳤을 때 (F, value: 책 화면) — preview: inner-screen frame
  - [기본] 펼치면 책 화면/무드등 → `onUnfold` · [기본] 무드등 색 (5 named presets + 직접 고르기 in one row) → `glowColor` · [기본] 무드등 테스트 button (exists today: `env.onGlowTest`)

**D. 정보와 권한**
- 날씨와 도시 (value: 서울): [기본] 날씨 지역 → `city` · [자세히] 세계 시계 도시 → `worldCities` — one shared city search sheet
- 카드에 쓰는 정보: [기본] D-day → `ddays` · [기본] 집중·휴식 → `focusMinutes/breakMinutes` · [기본, personal only] Claude·Codex 사용량 → `usageEnabled`
- 권한 (badge "4개 필요"/"모두 켜짐"): checklist, plain names — 충전 중 스탠바이 띄우기 · 알림 읽기 · 알림 띄우기 · 잠금 화면 위에 띄우기 · 알람 시각 미리 알기 · 캘린더 · 사진 …
- 앱 정보

## Fold inner screen (932 dp)
Existing two-pane `Page`: preview + pills on the left 55 %, rows on the right with 자세히 open by default. Home: preview left, groups right.

## Operator decisions on the mockup (2026-11-12)
- Theme "아이폰 그대로" is renamed (it reads as copying the iPhone); mockup uses "기본" until the operator picks a name.
- Theme "무드등" keeps its name (the 램프 rename is dropped).
- 켜짐과 꺼짐: "무선 충전일 때만" moves out of 자세히, under 충전하면 켜기.
- No device dropdown in the real app: the preview follows this phone. Fold and Flip get a 커버 | 안쪽 toggle, a bar phone none.
- The pinned preview shrinks to ~112 dp while the list scrolls, so the menu keeps most of the screen.
- Leaf previews (테마와 배경, 위젯, 밤 화면, 알림 표시, 시계) swipe sideways through the other pages; the first slide is the screen's own view.
- A 가로 | 세로 toggle sits beside 커버 | 안쪽 where the screen can turn (fold cover, bar phone).
- The preview shrinks only when the list is long enough to stay scrolled after it shrinks (short screens keep the full preview; no flicker).
- 가로 | 세로 on every screen (fold inner and flip cover rotate too; the app has no orientation lock). r10 needs portrait shots for fold inner and flip.
- Changing an option never jumps the preview to another page; every slide reflects the current settings and the user swipes to where the change shows (글자 크기 → 모듈러).

## Operator decisions (2026-11-12, round 3)
- Trial: 7 days full, then the free tier (no lock). Trial start is recorded on our server (hash of the per-app device id
  + first-run date on the existing Cloudflare Worker), so a reinstall does not restart it; Auto Backup as a fallback.
  Price proposal ₩4,900 one-time.
- New features 1 (희미한 시계), 3 (발열 보호), 6 (무드등 자동 끄기), each switchable; options: 밤 알림으로 꺼진 화면 안 켜기
  (the app already drops notifications the phone's 방해 금지 filters; this covers people without 방해 금지),
  꺼진 화면 = 완전히 끄기 / 희미한 시계 (same design as feature 1), 사진 넘김 간격.
- Removed now: numeral shapes, default D-day, the 영상 preset page. Bright themes (종이, 브라운 라디오) force night on.
- Burn-in shift: keep on, widen to a slow 10–20 dp drift (r10). Notification banner may cover the digits (it is brief).
- Alarm action buttons verified on the operator's device (2026-11-12).

## Operator decisions on features.html / cleanup.html (2026-11-12)
- 희미한 시계: dropped (no dim clock, and no "꺼진 화면: 완전히 끄기 / 희미한 시계" option; display-off stays full black).
- 발열 보호: variant A (pill message 5 s, then a thermometer icon until it cools). On by default.
- 무드등 자동 끄기: 끄지 않음 / 3분 / 10분 / 15분.
- New option rows (사진 넘김 간격, bright themes lock 밤 화면 "안 씀", 밤 알림으로 꺼진 화면 안 켜기): as in the mockup.
- 밤 알람: variant A (outlined 중단, same size and place; snooze minutes only when readable).
- 수면 소리: keep 빗소리 · 파도 · 장작불; merge 백색소음 / 핑크 / 브라운 into one sound named "백색소음" that uses the
  brown-noise generator. Keep the sleep card, fixed per variant A (selected tile marked, play state clear).
- Removal: 촛불 theme stays — give it a warm light rising from the bottom on every page, as visible as on the day
  widgets page and day desk board ("a candle lit below"). Background presets stay (all 4).
  A1 (rainbow/glass effects) and A2 (width slider) were not discussed: lead's assumption = follow the recommendation
  (drop rainbow/glass; drop the width slider, keep per-face default widths) unless the operator objects.
- Fixes B1–B5: all in (B3 step times = time StandBy saw each step change; arrival = now + remaining minutes).
- App name: **Stand Screen**. Play title idea "Stand Screen: StandBy Clock"; Korean listing carries "스탠바이" in title/short description.
- New in r10: calendar picker (choose which phone calendars feed the cards — every calendar in the system calendar provider, per account).
- Clocks: all 15 candidates of clocks.html go in (operator 2026-11-12), each with a full-screen form and a form for every
  card size class (L / M / St / Sw), plus night ink. Clock picker = gallery by family (see lead's proposal in chat).
- Icon: D3 (corner brackets + colon) chosen as the base; colour variants in icon/r4/.
- No "아이폰" / "iPhone" in any user-visible text (operator 2026-11-12). Renames: theme 아이폰 그대로 → 기본;
  ClockLayout.IPHONE "아이폰" → "기본"; AnalogTicks.IPHONE "아이폰" → "기본" (operator, 2026-11-12);
  BoardScreens footer "아이폰처럼" → drop the phrase. Enum names in code stay (stored values), only labels change.
  Add a check (like tools/check_literals) that fails on "아이폰"/"iPhone" in user-visible strings.
- 시간 표시 (12/24h) stays, folded under "자세히" at the bottom of the 시계 screen; default 폰 설정 따라 (operator 2026-11-12).
- 책상 시계 and 사진 위 시간 become regular clocks in the gallery (selectable into 내 시계). No "기존" tab: every clock
  is sorted into 심플 / 팝 / 레트로 / 클래식 / 계기. 디지털 and the "꽉 찬 숫자" candidate are one clock.
- App icon CONFIRMED (operator 2026-11-12) = F4 (sideways phone outline + stand + clock hands; cream #F4EEE3 / navy #1B2238 / coral #FF6F59),
  redrawn as exact geometry in `icon/final/make_icon.py` (SVG, adaptive foreground + monochrome vector drawables,
  Play 512/1024 PNG). Goes into the app in r10.
- Preview pattern (replaces the shrinking pin): the full preview is the first item of the scrolling list; once it is out
  of view a 96 dp sticky mini preview (낮|밤; on the 시계 screen also the face name + 넣기/꾸미기/빼기) overlays the top
  without changing layout height; tapping it scrolls to the top. Fold inner two-pane keeps the left preview.
- 꾸미기 per clock shows only what that clock supports (operator agreed 2026-11-12): every clock has its own colour
  slots; the font choice exists only where digits are typeset (digital-type faces, split, colour block, sticker,
  balls, calendar time, day bar, wall/analog numerals); 7-segment LED/LCD and the dot-matrix board have no font (their
  shape is the look) but get colour/style presets; 한글 시계 offers only Korean-capable fonts.
