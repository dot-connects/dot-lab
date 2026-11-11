# StandBy r9 — decided design (2026-11-11)

Source of truth for the r9 rebuild. Every item was picked by the operator on the mockup page
https://claude.ai/artifact/AbMds8H83pxMkdie9mXEh4 (frames drawn at 1 CSS px = 1 dp, device sizes and slot
geometry identical to `BoardView.slots`). When this file and the page disagree, the page wins; fix this file.

## 0. Ground rules

- Slot geometry is unchanged: margin = round4(short × 0.06) ≥ 16, gutter = round4(short × 0.04) ≥ 8, layouts
  ONE / TWO / BIG2 / TWO2 / FOUR / THREE.
- Devices checked: Fold cover 751×475 (land/port), Fold inner 932×704, bar 780×360 (land/port), Flip cover 400×362.
- "Fill the slot": no card leaves a large hole. When a slot has room, the hero number / picture grows or the next
  layer of information is added (hours → days, events, sessions…).
- Reading text is fixed and small ("두 단계 작게"): message 15, sender/head 18, meta 13 dp, scaled only by the new
  text-size setting (90 / 100 / 115 %). Hero numbers (clock digits, temperature, ETA) are slot-sized and NOT scaled.
- Every card and every full-screen surface must render correctly in all 10 themes and in night mode.

## 1. Cards (all confirmed)

| Card | Decision |
|---|---|
| Media (music + video in one card) | w/h ≤ 0.82 → **tall**: art (video 16:9) on top, left title/artist, bar + times, prev · white play disc · next, always shown. Otherwise **cover**: art/still fills, at rest picture only; tap shows scrim + title top-left + centred prev/play/next on dark discs + bar/times for 3 s; tap again opens Now Playing. Width < 140 → play/pause only. Buttons ≥ 48 dp. (Implemented in 50e5acb for the iPhone family.) |
| Notifications (stack + list merged) | Closed by default: one plate for the card, quiet rows without boxes, no enlarged newest; same conversation merged with a count; overflow = overlapped icons + "+N"; dot + bold for < 10 min. Tiers by width: A icon grid (w < 150, or w < 230 & h < 170), B 150–230 (time under sender), C 230–300, D 300–600 (app name only if w ≥ 400), E ≥ 600 two columns; S strip (h < 170) count column + 3 rows. "내용 열기" or a row → open: plates (sender 18 + message 15, 4 lines newest, 2 others), scroll; "접기" or 10 s idle closes. |
| New notification arrives | Small banner at the top centre (width min(W − 2M, 520), height 64): icon, sender, app · time, one line. 5 s, swipe up to dismiss, tap = notification card opens expanded (no card on the board → open the app). Replaces the full-screen notification. |
| Pull-down panel | Brightness slider, volume slider (media stream), three-or-more theme previews (shapes only, no names). Pull-and-slide still sets brightness. Hides 4 s after the last touch. |
| Clock (digital) | Digits fill the slot: one line "9:41" when w/h ≥ 1.05 or short < 150; hours over minutes when tall. Date line "월요일 11월 9일 · 7°" 13–20 dp, weekday in accent; dropped when short < 150. Two clocks on a board: the bigger keeps the accent. |
| Analog clock (card) | iPhone widget dial; wide (≥ 1.45) dial left + date block right; tall (≤ 0.7) dial + date line under; else dial only. No "월 9" inside the dial. |
| Weather | Temperature grows into the free height. Tiny: temp + sky. Short-wide (h < 176): temp left, 4–5 hours right. Small tall: temp + hour list. Wide (≥ 1.3): now left at full height, hours right (+5 days when tall enough). Square/tall: hours row + day rows as they fit, leftover goes to the number. |
| Calendar | Tiny: weekday/month + biggest day. Short-wide (h < 240): big day + this week + next 1–2 events. Narrow (w < 260): big day + upcoming events list. Big: month grid (wide: big day + events left, grid right; tall: grid + "다가오는 날"). Events from the phone calendar. |
| Agenda, D-day | As drawn: colour-bar list ("외 N개"), two columns when wide; D-day name · D-n · date + progress bar when short ≥ 176. |
| Phone widget (new card) | Hosts any home-screen AppWidget (Samsung Notes etc.) via AppWidgetHost; bind permission asked once. Long-press menu: "위젯 설정" (provider's configure activity) + StandBy settings: opacity 0–100 %, follow theme / always dark / always light (uiMode), corner radius, hide at night. Night: dark + red-tinted overlay over the whole slot. |
| Timer · pomodoro | Ring progress; square/tall: digits inside the ring + "집중 2/4", pause/stop under; wide: ring left, digits + next break + buttons right; small: digits + one button. |
| Sleep sounds | Six tiles (빗소리 · 파도 · 장작불 · 백색소음 · 핑크 · 브라운) in Control-Center style (round tile, circular SF-Symbol-like glyph, active one coloured); grid picks the column count with the biggest tiles; footer: pause · "45분 뒤 꺼짐" · minutes chip. Tiny: current sound + ◀ ▶. Needs a fire-crackle audio asset. |
| Usage (personal build only) | 눈금 widget rings (270°, open at the bottom; outer 5 h left thick, inner 7 d left thin, gap at the pace point, number = 7 d left) + session rows (dev · dev2 · sync · lab: dot state busy/wait/idle/off, context fill, cache arc). Wide: rings left full height, sessions right; stacked otherwise, sessions sized first; small = one line per session; tall big slots add bots + last update. Removed from release builds with the PC feed code. |
| Memories | Photo fills the slot, "3년 전 오늘" + place/date, "1 / 12" when big. |

## 2. Settings and behaviour

- **Text size**: 작게 90 / 보통 100 / 크게 115 % — reading text only.
- **Background**: 순검정 · 그래파이트 · 미드나잇 · 앰버 · 오로라 · 시간 따라 · 사진 흐림 · 단색 고르기; card plates translucent; night forces pure black.
- **Theme picker** (settings and panel): previews only, no theme names.
- **Charging**: unplug ends StandBy whatever opened it (done, db41677); own task affinity (done, a681260); lock the phone while charging → StandBy after ~1.5 s; StandBy switched off with the side key → StandBy again on the next wake (done, a681260).
- **Wave pill** over the board while media plays: removed (done, 13e2938). The media card is the way into Now Playing.

## 3. Full-screen surfaces

- **Alarm**: sunrise glow from the bottom, centred big time, "⏰ 기상 · 월요일", weather + today's event count, big orange "다시 알림 · 9분" pill + quiet "중단" pill (side by side in landscape, stacked in portrait).
- **Live activity**: ETA fills its column ("도착까지 12분"), app + store header, vertical timeline 주문 → 조리 → 픽업 → 도착 with times, rider row when tall enough; warm corner glow.
- **Analog clock (full)**: five styles, all shipped as a style option: 둥근 판 · 네모 판 (ticks/numerals along the screen edge) · 큰 숫자 4개 · 미니멀 (12 bars + glow) · 듀오 (analog + digital). Date/weather outside the dial.
- **Now Playing**: same YouTube Music look as the media card (tall/cover rules applied to the whole screen).
- **Night mode**: "촛불" — warm grey ink #B9A48C, secondary 45 %, plates 6 %, faint candle glow from the bottom, thin digits. Replaces the saturated red everywhere.
- Digital / world / solar / float / desk / photos: unchanged except the theme tokens.

## 4. Themes (all ten ship)

| Theme | Tokens |
|---|---|
| 아이폰 (따뜻하게) | bg #000, ink #F6EFE6, accent #FFAD5C, plate #1C1A18, r 26, digits Barlow Condensed 600 |
| 신문 1면 | bg #0E0D0B, ink #ECE4D4, accent #D8B46A, rules instead of plates, r 0, serif italic digits |
| 무드등 | bg #14100C + warm glow, ink #FFF2E2, accent #FFB36B, plate #2B2119, r 32, light sans digits |
| 촛불 (new) | bg #0C0805 + candle glow from the bottom, ink #EAD9C2, accent #E3A35B, plate 6 % warm, r 28, thin sans digits |
| 플립 시계 (new) | bg #0C0C0C, ink #F2F2F2, accent #FF9F0A, plate #1F1F1F, r 14, split-flap digit tiles |
| 하늘 (new) | time-of-day gradient bg, ink #FFF, accent #FFD29A, glass plates 14 %, r 26, extra-light digits |
| 종이 · 낮 (new) | bg #F3EEE4, ink #1D1A16, accent #C2410C, plate #E8E1D3, r 18, serif digits (light theme) |
| 브라운 라디오 (new) | bg #D9D3C7, ink #2A2723, accent #E8641C, plate #EBE6DC, r 22, Inter digits, knob row (light theme) |
| 네온 (new) | bg #000, ink #7DF9FF, accent #FF4FD8, outlined plates with glow, r 24, condensed digits with glow |
| 터미널 (new) | bg #050805, ink #59FF7A, accent #FFB000, outlined plates, r 4, IBM Plex Mono |

Every theme: same card layouts (section 1); the theme only changes tokens (colours, plate style, radius, fonts,
digit treatment, glow). Night mode overrides every theme with 촛불 night.
