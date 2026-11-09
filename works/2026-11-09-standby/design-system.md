# StandBy for Galaxy — design system (v1, 2026-11-09)

Source of truth for the Compose build. Base: mockups r2/r3/r4; numbers from `docs/archive/2026-10-29-standby-galaxy/00-standby-canon.md` §2/3/5 and `frames/`. "Ours" marks a
value we chose where Apple has none. All sizes are dp unless noted; `s` = short side of the box being laid out.

## 1. Principles

1. **Black is the canvas.** Pure `#000000`, no card backgrounds, no borders. Slots are invisible; alignment is what
   makes them read as one screen.
2. **Few, huge type steps.** StandBy's smallest text is ~8 % of its slot height (frame: "Mostly Cloudy" cap = 5.7 % of
   screen). Four text sizes, big jumps, no in-between sizes.
3. **One accent per board.** The hero (clock) owns the colour; everything else is white / grey. Colour = meaning, never
   decoration.
4. **Shared lines.** Every slot in a row puts its first cap-top on the same top line and its last baseline on the same
   bottom line.
5. **Scale, then drop.** Cards scale from their slot; when text would fall under the floor, lines disappear in a fixed
   order — never shrink below the floor, never ellipsize the main value.
6. **Night is the same picture in red.** Layout identical (±0), every token has a night twin; nothing is computed ad hoc.
7. **Calm motion.** Only the Float roll and swipes move. No shimmer, no bounce, no spinners on the board.

## 2. Tokens

### 2.1 Colour — day

| Token | Hex | Use |
|---|---|---|
| `bg` | `#000000` | Every StandBy surface (iPhone measured `#010002`) |
| `ink` | `#FFFFFF` | Values, titles, hands |
| `ink2` | `#98989F` | Labels, details, inactive glyphs (7.3:1 on black) |
| `ink3` | `#3A3A3C` | Tracks, rings, minor ticks, separators — never text |
| `ink4` | `#1C1C1E` | Pills, placeholders, pressed plates — never text |
| `red` | `#FF453A` | Calendar "today", weekday name, critical (≥90 %), recording |
| `scrim` | `#000000` 0→60 % | Bottom gradient under text on photos / art (from 55 % height) |

**Accent swatch set** (shared by every clock and board; 13 + custom picker). First = default.

| Clock | Default accent | Notes |
|---|---|---|
| Digital, Desk | `#76A6F3` | measured |
| Float | `#4D9BEF` | centre digit `accDeep`, overlaps `accLight`, colon `#D5D9DF` |
| Analog | second hand `#E9893A`, weekday `#D04450` | hands `#C5C0C4`, numerals `#D8D8DA` |
| World | `#FFAF3B` (city, arrow) | time stays `ink` |
| Solar | gradient set 1 `[bg #A396AE→#B4E6E2, digits #DCCAF7→#9DF0F7]` | 7 sets from r3 `SW.solar` |

Swatches: `#76A6F3 #4D9BEF #AA88FE #FD6890 #E162B6 #FF453A #FE7F7A #FF9C56 #FFD74B #6FDC86 #30CEDA #D5D9DF #FFFFFF`.
Derived per accent (HSL, as r3 `darker()`): `accDeep` = S+0.35, L−0.25 (floor 0.15); `accLight` = L+0.12;
`accDim` = accent at 45 % alpha over black (inactive segments, progress track tint).

### 2.2 Colour — night (fixed ramp, G = B = 0)

| Day token | Night | Contrast on black |
|---|---|---|
| `ink`, `acc`, hero digits | `#FC0000` (measured) | 5.0:1 |
| `ink2`, `accDeep`, `red` | `#A80000` | 2.5:1 (dim by intent) |
| `ink3`, `accDim` | `#3A0000` | non-text |
| `ink4` | `#1A0000` | non-text |
| Photos, album art, video stills | luminance → red: `ColorMatrix` R = 0.3R+0.59G+0.11B, ×0.5, G=B=0 | |
| Brand glyphs, calendar colours, gradients | collapse to `ink2` night | |

Transition: day→night 2.0 s crossfade starting when ambient lux stays under threshold for 5 s (ours); night→day 1.0 s
(measured ~1 s). Implement as one `nightFraction: Float` animated 0→1 and `lerp(day, night, f)` per token.

### 2.3 Typography

Families (bundled OFL): **Inter** (UI + default numerals), Nunito, Oswald, Source Serif 4, Roboto Slab, Noto digit
subsets (non-Latin numerals). Stencil/Rails = Inter 800 with our path cuts. **Hangul: none of these has it.** Until the
operator decides, fall back to the system sans; recommended: bundle **Pretendard** (OFL, drawn to sit with Inter) as
the Korean fallback in every `FontFamily` — otherwise Samsung's user-selectable system font leaks into StandBy.

Roles (sizes = font size, from the **slot** short side `s`; floors in dp, ~50 cm viewing):

| Role | Size | Floor | Weight / features | Line height |
|---|---|---|---|---|
| `hero` | autosize (§2.3.1) | — | clock style | 0.85 |
| `display` | 0.56 s | 64 | Inter 300 (temp) / hero font (D-day, timer) · `tnum` | 0.9 |
| `value` | 0.30 s | 40 | Inter 600 · `tnum` | 1.0 |
| `title` | 0.13 s | 22 | Inter 700 | 1.1 |
| `label` / `detail` | 0.085 s | 18 | Inter 600 / 500 · `tnum` for times | 1.15 |

Rules: max **one** of hero/display/value per card. Labels are sentence case (no all-caps Korean, Latin weekday in caps
only on Digital "MON 19" like iPhone). StandBy surfaces **ignore system font scale** (size = slot-derived; convert with
`(px / density / fontScale).sp`); the settings app respects it. Use `includeFontPadding = false` and
`LineHeightStyle(Center, Trim.Both)` so cap-tops can be placed exactly. Cap-height constants per font are read once
from OS/2 `sCapHeight` at build time (Inter = 0.727 em) and stored in `FontMetrics.kt`.

#### 2.3.1 Hero autosize (fixes clipping)

`size = min(boxH × hTarget / capRatio, boxW × wTarget / advance(widest))` where `widest` is the widest string the
clock can show in the current mode (12 h: `"10:58"` measured per font; 24 h: `"20:08"`; measure every digit, take the
max per position). Size is recomputed only on style/slot change, **never per minute**, so the clock never breathes.
Width ratio slider multiplies `advance`; size slider multiplies `hTarget` (clamped by the width term).

### 2.4 Spacing, radii, strokes

| Token | Value |
|---|---|
| Board margin `M` | `max(16, 0.06 × screen s)` rounded to 4 |
| Gutter `G` | `max(8, 0.04 × screen s)` rounded to 4 |
| Card inner pad `P` | `max(12, 0.07 × slot s)` — text cards only; graphics (dial, ring, photo) bleed to the slot edge |
| Label → value gap | ≥ 0.04 s; value → detail ≥ 0.03 s (space-between fills the rest) |
| Radius: photo/art/video plate | `0.06 × plate s`, min 12 |
| Radius: Solar/photo card in a slot | `0.05 × slot s`, min 12 |
| Radius: pills, buttons | full (50 %) |
| Stroke: line icons | size/12 (2 dp at 24), round caps/joins |
| Stroke: progress ring | 0.05 × ring diameter, round caps |
| Bar (progress, usage) | height `max(4, 0.025 s)`, full radius, track `ink3` |
| Analog | hour/minute 0.07 R, second 0.02 R, major tick 0.035 R, minor 0.015 R (`ink3` night `#3A0000`) |
| Icon box | = label font size; title icon = title size; aligned on the text's cap centre |

### 2.5 Motion

| What | Duration / curve |
|---|---|
| Float roll (changed digits only) | 400 ms, old digit up + −8° tilt + fade, new from below; `FastOutSlowIn` (0.4,0,0.2,1), no overshoot |
| Board swipe (horizontal) / stack swipe (vertical) | drag-follow; release `spring(dampingRatio = 1f, stiffness = 400f)` (~450 ms, no bounce) |
| Page dots | fade in on drag, out 1.5 s after settle, 300 ms |
| Notification | icon 300 ms scale 0.6→1, hold 900 ms, icon→mini 350 ms, text 300 ms fade (starts after icon done); auto-close 6 s |
| Live Activity pill → full | shared-bounds 350 ms `FastOutSlowIn` |
| Full-screen layers in/out (alarm, now playing) | 350 ms fade |
| Night | §2.2 |
| Alarm glow (Fold inner) | 6 s linear ramp to target alpha (day 0.9, night 0.35) |
| Burn-in shift (ours, canon U18 open) | whole board ±6 dp, 2 dp step every 3 min, no animation |
| Remove-animations on (animator scale 0) | Float roll → 150 ms crossfade, swipes snap |

## 3. Layout grid

### 3.1 Board

Board = screen − margins (`M` all four sides, symmetric). Edge-to-edge window (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`,
immersive). **Camera hole:** on the side holding a `DisplayCutout.boundingRect`, margin = `max(M, rectExtent + 8)` and
the **opposite side gets the same value** so compositions stay centred. Fold cover portrait: measured top inset 104 px
(≈ 40 dp) → top/bottom margin 48. Fold inner: no cutout; if a vertical `FoldingFeature` is reported, column splits
snap to the hinge (two/four/big2 become 50/50) and no text crosses it.

Page dots live inside the bottom margin, centred, `0.013 s` dots, 1.5 % gap — they reserve no space (fixes r4's
asymmetric 6 %/8 % padding).

### 3.2 Six layouts

Landscape (portrait = transpose: columns become rows). Split for big/small = **60/40** (big slot ≈ square on the Fold
cover, good for dials).

```
one        two          big2           two2           four         three
┌──────┐  ┌───┬───┐   ┌────┬──┐     ┌──┬────┐      ┌───┬───┐   ┌──┬──┬──┐
│      │  │   │   │   │    │  │     │  │    │      │   │   │   │  │  │  │
│      │  │   │   │   │    ├──┤     ├──┤    │      ├───┼───┤   │  │  │  │
│      │  │   │   │   │    │  │     │  │    │      │   │   │   │  │  │  │
└──────┘  └───┴───┘   └────┴──┘     └──┴────┘      └───┴───┘   └──┴──┴──┘
```

### 3.3 Slot sizes (dp, w×h) and size class

`M/G` per device; portrait cover subtracts the 48 top/bottom margins. Class from slot `s` (§4.1). `w`=wide (w/h ≥ 1.5),
`t`=tall (h/w ≥ 1.5).

| Device | M / G | one | two | big2: big / small | four | three |
|---|---|---|---|---|---|---|
| Fold cover L 751×475 | 28/20 | 695×419 L·w | 337×419 M | 404×419 L / 271×199 S | 337×199 S·w | 218×419 S·t |
| Fold cover P 475×751 | 28(48)/20 | 419×655 L·t | 419×317 M | 419×380 M / 199×255 S | 199×317 S·t | 419×205 S·w |
| Fold inner 932×704 | 44/28 | 844×616 L | 408×616 L·t | 488×616 L / 328×294 M | 408×294 M | 262×616 S·t |
| Bar L ~780×360 | 20/16 | 740×320 M·w | 362×320 M | 436×320 M / 288×152 XS·w | 362×152 XS·w | 236×320 S |
| Bar P ~360×780 | 20/16 | 320×740 M·t | 320×362 M | 320×436 M / 152×288 XS·t | 152×362 XS·t | 320×236 S |
| Flip cover ~400×362 | 20/16 | 360×322 M | 172×322 XS·t | 208×322 S / 136×153 XS | 172×153 XS | **off** (109 wide) |

A layout whose smallest slot has `s < 120` is greyed out in the editor for that device (Flip three). Bar values assume
the hole fits in `M`; recompute with §3.1 on device.

### 3.4 Alignment shared by all cards

```
 slot ┌───────────────────────────────┐
      │  P                            │
 TOP ─┼─ ◯ Label                 badge│ ← first cap-top on the row's top line
      │                               │
      │  VALUE / DISPLAY              │ ← centred in the leftover space
      │                               │
 BOT ─┼─ detail · detail              │ ← last baseline on the row's bottom line
      └───────────────────────────────┘
```

- **Rows:** slots side by side share `TOP` (= slot top + `P`) and `BOT` (= slot bottom − `P`). Since slots in a row have
  equal height, each card fills its own content box top-to-bottom ("space-between") and the lines line up for free.
- **Leading edge:** text cards are leading-aligned (left in LTR). Exceptions, centred: clock card digits, analog dial,
  timer ring, sleep wave. Even centred cards put their first cap-top on `TOP` and last baseline on `BOT`.
- **Graphics bleed:** dials/rings fit `min(w,h) − 2·0.04 s`; photos and art fill the slot (with plate radius).
- Never centre a text block vertically with empty bands above and below — that is what made r4 rows look glued.

## 4. Card anatomy

### 4.1 Template and size classes

Every card = `Label` (icon + source/subject, `ink2`) → `Value` (one big thing, `ink` or `acc`) → `Detail` (1–2 lines,
`ink2`) + optional `Control` row (transport, start/stop) placed above `Detail`.

| Class | slot `s` | Shows | Drop order when short |
|---|---|---|---|
| XS | < 176 | Value + Label (label at floor 18) | detail, control (tap opens full screen instead) |
| S | 176–279 | Label, Value, 1 detail line | 2nd detail, secondary controls (keep play/pause) |
| M | 280–399 | full template, controls | — |
| L | ≥ 400 | full template; wide shape adds a side column | — |

Shape: **wide** → art/graphic left (height = content box), text right; **tall** → graphic top, text below;
**square** → text template, graphic optional. Text never ellipsizes the Value; labels/details ellipsize at end.

### 4.2 The 12 cards

| Card | Label | Value | Detail / control | Class notes |
|---|---|---|---|---|
| Clock | — (none) | hero digits in board clock font, `acc` | date "11월 9일 월요일" (`detail`) | XS: digits only (fill 80 % h). Uses §2.3.1 within the content box |
| Analog | — | round dial, numerals per style, hands `ink`, second `acc` | weekday+date right of centre (`label` size, weekday `red`) | XS: no numerals, 12 major ticks only |
| Music (any media session) | source glyph + app name | title (`title`), artist (`detail`) | transport row: prev/next = label×1.6, play = label×2.4; progress bar | wide: art left square; tall: art top 52 % w; XS: art + play only |
| Video app (YouTube, TVING…) | source glyph + app | still/thumbnail plate 16:9 + title | channel, controls (no video) | same as Music, art 16:9 |
| Timer / pomodoro | mode "집중 / 휴식" (`label`, `acc`) | `display` mm:ss inside ring, hero font | hint "눌러서 시작" (`detail`) | ring stroke = `acc` on `ink3`; XS: digits only, ring stays |
| Sleep sound | sound name | wave (9 bars, `ink2`, `acc` when playing) | "45분 뒤 꺼짐" | tap = play; dims board to night ramp while on |
| D-day | event name | "D-10" in hero font, `display`, `acc` only if no clock on board else `ink` | date "11월 19일 목요일" | XS: number only |
| Agenda | "오늘" + count | list of up to 3 rows: time (`label`, `ink2`, `tnum`) + event (`title`, capped 0.10 s) | calendar colour bar per row (day), `ink2` at night | S: 2 rows; XS: next event only, as Value |
| Claude/Codex usage | "사용량" + age "21:00 기준" | two rows: name + 5 h % as `value`, bar | 7 d % and reset time as `detail` | S: 5 h only; XS: highest % only. Bars `ink`; ≥ 90 % → `red` |
| Weather | city + location arrow | temperature `display` Inter 300 | condition icon + text, H/L; L·wide adds 5 hours row | iPhone frame: label cap 6 %, value cap 22 %, detail cap 5.7 % |
| Calendar (month) | month "11월" (`title`, `red`) | 7-column grid, day numbers `label` size, today = `red` disc, white text | — | XS/S: "Mon Aug / 19" date widget instead of grid (frame: label cap 12 %, number cap 48 %) |
| Memories | — | photo fills slot (plate radius) | "3년 전 오늘" `title` + place/date `detail` on scrim, bottom-leading | night: red-mapped photo |

Icons: one hand-drawn set (24 grid, 2 dp stroke, round): alarm, location arrow, weather ×8 (sun, moon, cloud,
partly, rain, snow, fog, thunder), prev/play/pause/next (filled), timer, sound, calendar, sources (generic music note,
play-rect). No brand colours on the board; brand appears only as a monochrome glyph in `ink2`.

### 4.3 Full-screen clocks (board `one` + clock = these specs; % of screen)

| Clock | Spec |
|---|---|
| Digital | digits box x 12.8–66 %, cap height 69 % H, top 15.4 %; side column x 73–86 %, top 15.9 % (aligned to digit cap-top): "MON 19" (weekday `acc`, date `ink`) and "7°" — both `label` size computed from **screen** s (cap ≈ 6 % H), not 2 %; next alarm row below when set. Portrait: hours over minutes, each line cap ≤ 36 % H, line gap 4 % H (never overlap), date row `label` below |
| Analog | landscape: rounded-rect dial x 8.3–92 %, y 12.5–92.9 %, axis (50 %, 52.7 %), ticks radiate to the rect. Numerals sit on the rect inset by (major tick + 0.03 s), size `min(0.086 H, fit)` where fit = numeral advance ≤ 0.8 × spacing to neighbours; Roman uses 0.07 H. Second hand length ≤ inset rect (never past the dial). Date x 62.8–72 %. Portrait/slot: circular dial |
| World | dot map 96×44 x 12.8–93.6 %, y 12.9–86.1 %; terminator `ink3`, lit dots `ink2`, dark dots `ink3`, city dots `acc`; bottom-left block x 7.4–28.3 %, y 58.8–85.4 %: arrow+city `label` `acc`, time `value` `ink`; alarm top-left `label` |
| Solar | gradient bg fills screen (or slot with radius); digits `hero` Inter 350, gradient fill; card ~x 16–84 %. **Contrast floor:** digits vs local bg ≥ 3:1 — if a swatch fails, raise digit weight to 500 and add a 6 % black inner shadow |
| Float | Nunito 950, cap 66 % H, x 18.6–80.7 %; each digit rotated (−3°, 0°, +3°, 0°), negative advance −12 % so digits overlap; overlaps drawn with `BlendMode.Screen` (gives `accLight`); centre digit `accDeep`; colon = 2 discs Ø 5 % W `#D5D9DF` 85 % alpha, Screen blend, on top |
| Desk (ours) | Digital digits in left 55 % (cap 46 % H), right column = 3 rows (alarm, weather, music), each `label` (ink2) over `title` (ink), 1 dp `ink3` separators, rows share the digit cap-top and baseline |

Clock style options (r3, all kept): 11 fonts (Bold Inter 800, Light Inter 250, Rounded Nunito 800, Stencil, Serif SS4
500, Serif Heavy SS4 900, Slab Roboto Slab 700, Rails, Condensed Oswald 600 = Digital default, Rounded Heavy Nunito 950
= Float default, Thin Inter 350 = Solar default); weight/size/width sliders; fill solid / two-colour / rainbow / glass /
glow; bg black / colour / gradient; numerals Latin + 5 (Noto subsets); layout iPhone / centre / two lines.
Effect floors: **glass** = fill `acc` 30 % + 1.2 % em stroke `mix(acc, white 65 %)`, with min stroke luminance 0.5 so
it is never dark-on-black; **glow** = 2 blurs (0.05 em, 0.22 em at 55 %), off at night (red glow smears).

### 4.4 Other full-screen screens

| Screen | Spec |
|---|---|
| Widgets | board `two` whose slots are **stacks** (vertical swipe cycles cards in that slot); default left Analog, right Calendar |
| Photos | photo fills, Ken Burns ≤ 2 % over 20 s (ours); time top-right x 72–92 %, y 10–28 % in board clock font `ink`; place `title` bold y 72–80 %, date `label` caps y 80–86 %, right-aligned, on bottom scrim; theme pill (right middle) `ink4` bg on switch only, 1.5 s |
| Alarm | top: "알람" `label` `acc` + icon (top-left), time `hero` 25 % H `ink` (top-right, cap-top on same line). Bottom: two full pills, height 27 % H, gap 4 % W: Snooze `#F2A65E` (night `#7A0000`) left, Stop `ink4`+`ink` text (night `#3A0000`) right; text `title` 700. Portrait: stacked, pills 13 % H. Fold inner glow §2.5 |
| Now Playing | art square x 9–42 %, y 14–83 % (radius 0.06); title `title`, artist `detail`; transport centre x 56–82 %, y ≈ 58 % (play = title × 2); progress y 71 %, times y 76 % `detail` `tnum`; volume glyph top-right. Empty: §5.4 |
| Notification | step 1: app icon/sender photo plate centred, Ø 30 % s, radius 28 %; step 2: plate **shrinks to the mini position first** (Ø 14 % s, top-leading of a 78 % W text block), then sender `title` + app·time `detail` fade in to its right, message `value`×0.8 (max 3 lines) below. Nothing overlaps the plate. Preview hidden → only icon + "메시지 1개" |
| Live Activity | pill top-centre 3 % H: `ink4` bg, icon + short value in `la` = `acc`, `label` size. Tap → 2× full: header `label` + icon, value `display`, step bar (4 segments, `acc` / `ink3`), `detail`. Margins = board `M` |

## 5. Combination rules

### 5.1 Hero and accent

- **Board accent** = the accent of the board's hero. Hero = the clock-like card in the largest slot (ties: leading /
  top slot). Clock-like = clock, analog, timer, D-day, world. No clock-like card → accent = user's board accent
  (default `#76A6F3`).
- Accent may appear on: hero digits / second hand, **one** small mark per other card (timer ring, sleep wave when
  playing, music progress fill, today marker stays `red`). Everything else `ink`/`ink2`.
- **Two clock-like cards** on one board: hero keeps accent digits; the other renders its main numerals in `ink` and puts
  accent only on its progress/second hand. Example: clock (accent digits) + analog (white hands, accent second hand).
- **Clock font carries:** numerals in other cards (timer digits, D-day number; temperature stays Inter 300 like iPhone) use the hero's font family/weight/width; words always Inter. Effects (rainbow, glass, glow)
  stay on the hero only.
- Semantic colours never take the accent: `red` today/critical; calendar event colours (day only).

### 5.2 Night on combos

Night replaces every token by its twin (§2.2) at once — accent, `red`, calendar colours, brand glyphs, gradients all end
in the 4 reds. Solar in night: bg → `#1A0000`, digits `#FC0000`. Photos/art red-mapped. Same layout to the dp.

### 5.3 Size consistency within a board

All non-hero cards on one board use the **smallest** slot's `label` size for their labels (so labels match across
slots of different size); values scale per slot. This is the single biggest unity fix.

### 5.4 Empty / loading / error (same template)

| State | Label | Value position | Detail | Tap |
|---|---|---|---|---|
| No media playing | source glyph + "음악" | outlined note icon, `ink3`, Ø = value size | "폰에서 재생하면 여기 나와요" | opens last player |
| Loading (first fetch) | real label | `ink4` rounded bar(s) the size of the value, static | — (text "불러오는 중" only after 2 s) | — |
| Stale (feed offline) | real label | last value, `ink` | "21:00 기준 · 연결 안 됨" + `ink2` dot | retry |
| Stale > 3 h | real label | last value in `ink2` | same | retry |
| Permission missing | label | lock icon `ink3` | "권한 필요 · 눌러서 설정" | unlock → settings screen of that permission |
| No data (no events / no alarm) | label | "일정 없음" in `title` `ink2` | — | — |

No spinners, no red error banners on the board; errors never change layout height.

## 6. Settings app (launcher activity)

Dark Material 3 with our tokens: `bg #0E0F12`, surface `#17181C`, line `#2A2C32`, text `#EDEDEF` / `#9A9AA0`,
primary = current default accent. Inter + Hangul fallback, respects font scale, touch targets ≥ 48 dp, rows 56 dp,
section headers `label` 13 sp `ink2`.

```
Home ─┬─ Boards (carousel of live previews) ─▶ Board editor
      ├─ Clock styles ─▶ per clock (Digital, Analog, World, Solar, Float, Desk, Photos time)
      ├─ Night & display
      ├─ Alarm & mood light (Fold only)
      ├─ Data sources
      └─ Permissions (badge = missing count)
```

- **Previews are the real composables** (`StandByBoard(state, size)` drawn at the device dp size and scaled) — never
  separate thumbnails, so what you edit is what shows.
- **Board editor:** preview on top with device switch (cover / inner / bar / flip) + orientation; layout row of 6
  thumbnails (disabled ones say why); tap a slot → bottom sheet "카드 고르기" grid showing each card rendered at that
  slot's size class; per-card options below; board accent row ("시계 따라" + swatches); name, duplicate, delete, reorder
  by drag in the carousel.
- **Clock style:** preview, then sections Font (11 tiles showing "12"), Size/Weight/Width sliders, Colour (swatches +
  custom + effect segmented), Background, Numerals, Layout, "아이폰처럼 되돌리기".
- **Night & display:** night mode auto (light sensor) / schedule / off; lux threshold slider with live reading; display
  off auto / 20 s / never; motion to wake; burn-in shift; night max brightness.
- **Alarm & mood light:** glow colour (mint `#85D4BA` iPhone / warm `#FFB36B` ours), start offset, duration, test.
- **Data sources:** weather location, calendars (multi-select), usage feed (LAN URL + token in
  `EncryptedSharedPreferences`, "테스트" button with last fetch time — reuse 눈금's fetch), photo albums, D-day list,
  sleep sounds.
- **Permissions:** one row per permission with state chip (켜짐 / 필요) and one button: screen saver selection,
  notification access (also media), calendar, location, full-screen intent, battery optimisation exemption.

## 7. Top 10 defects in the mockups and the fix

| # | Seen in | Defect | Fix |
|---|---|---|---|
| 1 | r4 usage, timer, clock date, weather | Board text 8–10 px: labels/details 3–5 % of slot vs iPhone ~8 % | §2.3 ramp with 18 dp floor; drop lines by size class instead of shrinking |
| 2 | r4 "작업 책상", 수능, 모듈러 | 5–6 hues on one board (blue clock, orange ring, terracotta bars, red YT, yellow D-day, green/orange bars) | One accent per board (§5.1), others `ink`/`ink2` |
| 3 | r4 all multi-slot boards | Mixed anchors: weather/agenda top-left, music mid-left, clock/sleep centred → rows don't line up | Shared TOP/BOT lines, space-between fill (§3.4); label size unified per board (§5.3) |
| 4 | r3 stencil/outline, r2 Digital landscape | Hero digits clip at the right edge; Digital loses its side column | Width-fit on widest string (§2.3.1); side column reserved x 73–86 % |
| 5 | r2 Digital, portrait digital | Side info "월 9 / 7°" ~2 % H and glued to the digits | Cap 6 % H, top on digit cap-top, x 73 % (§4.3) |
| 6 | r2/r3 Float | Digits ~40 % H instead of 66 %; opaque grey colon pasted on; no tilt/overlap blend; glass navy-on-black nearly invisible | Float spec §4.3 (Screen blend, tilt, 66 %), glass stroke luminance floor |
| 7 | r3 Roman/Stencil analog, r2 portrait analog | Numerals collide (XI XII I, 7 6 5), touch ticks, hands overrun "9"; second hand past the dial | Numeral ring inset + fit-to-spacing rule; hand length ≤ inset (§4.3) |
| 8 | r4 notification | Sender name drawn over the icon bubble (both visible at once) | Two-step sequence: plate shrinks to mini first, text fades after (§2.5, §4.4) |
| 9 | r2 portrait Digital | "8" over "52" overlap / uneven, date tiny | Two-line rule: per-line cap ≤ 36 % H, gap 4 % H, centred on a shared axis |
| 10 | r4 board frame | Asymmetric padding (6 % top / 8 % bottom for always-on dots); dashed selection outline shows on the device | Symmetric `M`, dots overlay and auto-hide; selection only in the settings preview |

