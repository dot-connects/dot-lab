# StandBy renders — design review 1 (2026-11-10)

Inputs: `design-system.md`, `spec-behaviour.md`, iPhone frames (`docs/archive/2026-10-29-standby-galaxy/frames/`), renders:
`clock/` = `agent-a317d…/…/build/shots/clock/`, `board/` = `agent-a3afba…/…/shots/board/`, `settings/` = `agent-aea640…/…/shots/settings/`.
% = pixel bounding box on the PNG.

Severity: **P0** fix before the operator sees it · **P1** next pass · **P2** polish. Kind: **F** iPhone fidelity · **U** unity · **L** legibility.

## A. Cross-cutting (fix once, every screen improves)

| ID | Sev | Kind | Where (PNG) | What is wrong (measured) | Exact fix |
|---|---|---|---|---|---|
| X1 | P0 | F | `clock/digital-day-fold-cover-land`, `float-day-fold-cover-land`, `desk-day-fold-cover-land`, `solar-day-fold-cover-land`, all `style-font-*` | Hero cap height on Fold cover: Digital **35.7 %** H, Float **44.5 %**, Desk **28 %**, Solar **26 %**, every non-condensed font **25–30 %**. iPhone: Digital **69.4 %**, Float **65.6 %** (`digital-dan`, `float-dan`). Cause: §2.3.1 sizes for "10:58" all day; Oswald "10:58" is 2.51× its cap wide vs "9:41" 1.81×. Side column is also ~165 dp wide because of "알람 6:30". | **ui/clock, §2.3.1 rewrite:** `widest` is computed **per hour-digit class**: class 1 = 1:00–9:59 (12 h), class 2 = 10:00–12:59; 24 h = one class ("20:08"). Size changes only at 9:59→10:00 and 12:59→1:00 (crossfade 400 ms with the Float roll; never per minute). Hero digits **proportional** (drop `tnum` on hero; keep it on board numerals) so "1" is narrow. Colon advance −30 % (tighter, iPhone-like). Side column fixed **120 dp** (see X2) + gap **0.05 W**. Fold cover L (content 695×419): Digital class 1 = min(328, 537/1.81) = **297 dp = 62.5 % H**, class 2 ≈ **47–49 %**; Float class 1 = 695/2.28 = **64 %**, class 2 ≈ **51 %**; Bar L Digital class 1 = **69 %** (height-limited). Desk: digit box = left 55 % → **44 %** H class 1 (spec 46 %). Shot test: Digital class-1 cap ≥ 60 % H. |
| X2 | P0 | U/F | `clock/digital-*` ("알람 6:30"), `world-*` ("알람 오전 6:30"), `style-*-24h` ("알람 06:30"), `desk-*` ("다음 알람 · 내일 / 오전 6:30") | Four alarm formats across faces; Digital's text form makes the side column 22 % W and steals hero width. | One formatter `AlarmText`: **compact** = alarm glyph (icon box = label size) + `h:mm` (no 오전/오후; 24 h `H:mm`) on Digital side column and World top-left; **full** = "다음 알람 · 내일" + "오전 6:30" only on Desk and the alarm card. |
| X3 | P0 | L | `board/card-usage-M`, `card-usage-St`, `preset-desk-fold-cover-port` | Main value ellipsized: **"2…"** instead of "20%". Breaks §4.1 "never ellipsize the Value". | **ui/cards usage row:** value `wrapContentWidth` measured first; name + window label take `weight(1f)` and ellipsize; if still short, drop the "5시간" sub-label, then the name (keep glyph). |
| X4 | P1 | L/U | `board/card-video-Sw` ("재즈 3시 / 간"), `card-memories-M/St`, `preset-book-flip-cover` ("11월 9 / 일") | Korean breaks inside a word; detail wraps to a 3rd line with one syllable. | All StandBy text: `TextStyle(lineBreak = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase))` (Compose ≥1.5, API 33+; fall back to `Heading`). Detail lines `maxLines = 1`, ellipsis end; memories detail short form "설악산 · 2023.11.9" when it does not fit. |
| X5 | P1 | U | `board/preset-music-fold-cover-port` ("YouTube…"), `preset-music-bar-port` ("YouT…"), `preset-music-bar-land` | Label truncated to 4 letters because in wide shape the label sits beside the art in a narrow text column, at a size derived from the big slot. | **ui/cards music/video wide shape:** label row spans the full slot width on the TOP line, art + text start below it (matches §3.4 TOP = first label cap-top). Apply §5.3 board label size (smallest slot) — it is not applied in portrait (label ≈ 24 dp glyph there vs 15 dp in landscape). |
| X6 | P1 | U | `board/empty-a-fold-cover-land` ("➤ 서울") vs `card-weather-*` ("서울 ➤") | Weather label changes arrow side between loading and loaded. | Weather label = city then arrow (iPhone "Holland ➤") in every state; World keeps arrow before city (iPhone World). Loading/stale states reuse the real label composable. |
| X7 | P1 | F | `board/card-weather-*`, `preset-modular-*`, `preset-book-*` | "7 °": gap between digit and degree ≈ 0.25 em. iPhone "74°" has the degree touching. | Temperature string drawn with −0.12 em letter-spacing before "°" . |
| X8 | P1 | U | `board/card-music-*` vs `card-video-*` | Music order = title → artist → progress → transport; Video = progress → title → channel → transport (right). | Video uses the music order exactly; transport row same size rule (prev/next = label×1.6, play = label×2.4) and same x anchor. |
| X9 | P1 | U | `board/card-agenda-St`, `preset-modular-bar-port`, `preset-book-bar-port`, `card-calendar-St`, `preset-bed-flip-cover` | In tall slots the rule "label on TOP, last baseline on BOT" leaves a 150–200 dp void between label and a lone value; agenda rows spread to the extremes (row 1 at 40 %, row 2 at 95 % of slot). | Lists (agenda, usage rows) use a **fixed row pitch** = 1.9 × title size under the label; leftover space goes below the list (BOT only binds the last *detail*). Lone values in tall (`t`) slots size from `min(w, 0.6 h)` instead of `s`, so "7°"/"9"/"헬스" fill the slot instead of floating. Sleep XS: wave centred in the space below the label. |
| X10 | P1 | U | `clock/style-fx-glow-night` (glow on at night) vs `settings/clock-digital` text "밤에는 … 빛 번짐만 빨간색으로 남아요" vs design-system §4.3 "glow off at night" | Spec, code and settings copy disagree. | Follow the design system: glow off at night (red blur smears on OLED and reads as burn). Change settings copy to "밤에는 모든 효과가 꺼져요". |

## B. Clocks (ui/clock)

| ID | Sev | Kind | Where | What is wrong | Exact fix |
|---|---|---|---|---|---|
| C1 | P0 | F | `clock/photos_time-day-fold-cover-land`, `-fold-inner`, `-bar-land` | Time cap **9.3 % H** at y 2.6–11.9 %, x 82–97.7 % — jammed into the corner with ~12 dp inset (less than M = 28); Fold inner only ~5 % H. iPhone `photos-dan`: cap ≈ **17 % H**, y 11–28 %, x 73.6–93 %. | Position from screen, not from a corner pad: right edge at **92 % W**, cap-top at **10 % H**, cap **15 % H** (hero autosize inside box x 72–92 %, y 10–28 %). Same rule on all devices. |
| C2 | P0 | L | `clock/desk-day-fold-cover-land`, `style-desk-empty-fold-cover-land`, `desk-day-bar-port` | Right column label "다음 알람 · 내일" ≈ 11 dp font, title ≈ 17 dp — under the 18/22 floors; empty state labels ≈ 6 dp. Bar portrait: "Ditto · NewJeans" clipped mid-glyph at the right edge. | Desk rows share the digit cap-top/baseline (keep — already right); row pitch = digitCap / 3; **title = 0.38 × pitch, label = 0.24 × pitch, floors 22/18**. With X1 (cap 211 dp) → title 27 dp, label 18 dp. Titles `maxLines = 1` + ellipsis; never clip. Empty rows use §5.4 text ("없음") at the same sizes, no bar placeholder. |
| C3 | P0 | L | `clock/slot-analog-m-fold-cover-land`, `slot-analog-wide-…` | Circular dial: date "월 9" butts against numeral "3" → reads **"월 93"** (gap ≈ 0). | Circular dial: date block right edge ≤ numeral-3 left edge − 0.5 em; if it does not fit (slot `s` < 300), move the date below centre (y = +0.38 R, centred) or hide the 3 numeral when the date is shown. Rect dial (full screen) is fine — keep. |
| C4 | P1 | L | `clock/solar-day-*`, `style-solar-arab` | Digits #DCCAF7 on #A49BB1 ≈ **1.7:1** (spec floor 3:1); hero only 26 % H (X1 fixes size). | Apply the §4.3 rule that is written but not implemented: when contrast < 3:1 → weight 500 + 6 % black inner shadow. Test all 7 palettes. |
| C5 | P1 | L | `clock/style-fx-glass-*`, `style-digital-glass` | Glass fill #172E47 / #233149 ≈ **1.5:1** on black; digits read only via the 1.2 % stroke. | Fill = accent at **60 %** (≈ 3.5:1), stroke unchanged. |
| C6 | P1 | F/L | `clock/style-analog-twelve`, `style-analog-roman` | On the rect dial, ray-intersection packs "11 12 1" and "7 6 5" / "XI XII I", "VII VI V" within ~0.3 em while 9/3 sides are empty. | Place 12-numeral styles on the rect at **equal perimeter spacing** starting at 12, snapping 3/6/9/12 to the axes; apply §4.3 fit rule (advance ≤ 0.8 × neighbour spacing) to the tightest pair. |
| C7 | P1 | F | `clock/photos_time-night-fold-cover-land` | Placeholder photo stays blue at night (only the time turns red). | Placeholder/fallback images pass through the same red `ColorMatrix` as photos. |
| C8 | P2 | U | `clock/digital-day-fold-inner`, `float-day-fold-inner`, `solar-day-fold-inner`, `desk-day-fold-inner` | Fold inner (932×704, landscape) uses the stacked portrait layout; spec §4.3 says portrait only. | Write the rule into §4.3: stacked when screen w/h < **1.45** (Fold inner 1.32, Flip 1.1), one-line otherwise; apply the same threshold to Digital, Float, Solar, Desk. |

## C. Boards and cards (ui/board, ui/cards)

| ID | Sev | Kind | Where | What is wrong | Exact fix |
|---|---|---|---|---|---|
| B1 | P0 | L/U | `board/card-weather-L-fold-cover-land` | Hourly row: first hour clipped ("2시" of 22시), hours/icons/temps not in columns ("5° 5°4° 4°"), temps larger than the hours. | 5 equal-width columns (`Row` + `weight(1f)`), each centred: hour = `label` ink2, icon = label × 1.6, temp = `title` Inter 600 ink. Row width = content box; never wider. |
| B2 | P1 | U | `board/card-timer-St/Sw/XS`, `preset-desk-*` | Timer mode "집중" lives inside the ring (accent) while every other card's label is on the TOP line. | S/XS: ring only (digits inside), mode label moves to TOP line as a normal label (`ink2`, icon + "집중"); hint "눌러서 시작" dropped in S. L/M keep in-ring text. |
| B3 | P1 | F | `board/card-weather-*`, `preset-modular-*` | Weather label "서울" is small `ink2`; iPhone `widgets-day-dan`/`night-dan`: "Holland ➤" bold white, cap ≈ 6 % H. | Weather card label = `title` weight 700 `ink` (iPhone), still from §5.3 size; other cards keep `ink2`. Document as the one label exception (§4.2). |
| B5 | P2 | U | `board/board-picker` (settings) | The picker shot shows the editor without the "카드 고르기" sheet. | Re-shoot with the sheet open. |

## D. Settings (settings)

| ID | Sev | Kind | Where | What is wrong | Exact fix |
|---|---|---|---|---|---|
| S1 | P1 | U | `settings/home-fold`, `board-editor-fold`, `clock-solar-fold` | On Fold inner the phone column (~420 dp) sits centred with 35 % empty bands; preview ~25 % of width. | Width ≥ 600 dp: two panes — preview left (sticky, 55 %), controls right (45 %), same components. |
| S2 | P2 | U | `settings/about` | Lists Inter, Pretendard, Nunito only; Oswald (Digital default), Source Serif 4, Roboto Slab, Noto subsets missing. | List every bundled OFL family. |

## E. What already looks right — keep

- **Night ramp**: every night shot is pure #FC0000/#A80000/#3A0000 with layout identical to day
- **Digital side column**: cap-top aligned to the digit cap-top, weekday accent + date ink, "7°" white, label cap ≈ 6 % H — matches `digital-dan` (only width/alarm need X1/X2).
- **Analog landscape**: rounded-rect dial, radiating ticks, 12/3/6/9, red weekday + white date right of centre, orange second hand, gap at the pivot
- **Float**: tilt pattern, overlap Screen blend, deep centre digit, grey disc colon over the overlap; only size (X1) is off.
- **One accent per board**: timer ring, music progress and D-day follow the board accent; D-day turns `ink` when a clock is present; calendar keeps semantic red.
- **Shared TOP/BOT lines** in `preset-exam`, `preset-modular`, `preset-bed` landscape: labels share the cap-top, last baselines share the bottom line.
- **Empty/stale/permission states**: card template, no spinners, no layout jump.
- **Settings**: consistent dark M3 tokens, font tiles in the real font, same 13 swatches as the clocks, clear 필요/켜짐 chips.

**P0 count: 7** (X1, X2, X3, C1, C2, C3, B1).
