# Relief clocks (r11 Part C) — three new faces

Operator 2026-11-15: approved mockup v6 https://claude.ai/artifact/7sWZSDJTY7RB3JGBd3ERWK (local copy:
`settings-r10/relief-clocks.html` — the reference implementation; read its SVG code for every measurement).
Operator 11-15: never call it Bauhaus anywhere (UI, code names). References: two Instagram reels of a Bauhaus -style watch-dial desk clock (full-screen dial, applied lume capsules, rimmed
hands, soft shadows). "고급스럽고 입체감".

## Faces (new `FaceId`s, appended at the end of the enum; stored by name)

| FaceId | ko | Look |
|---|---|---|
| `RELIEF` | 입체 시계 | A: round minute track + rotated 5-min numerals, recessed inner step (r 36.3 of 50), 12 applied lume capsules, numerals 1–12, rimmed lume hands, silver dome pivot |
| `RELIEF_SECTOR` | 입체 섹터 | B: square minute track (two frame lines, numerals 60/15/30/45, dots at 7.5s), round hour ring a tone lighter with a step, capsules except 12/3/6/9 numerals, inner step r 24 |
| `RELIEF_DIGITAL` | 입체 디지털 | C: HH:MM as 7-segment digits made of lume capsules, ghost (off) segments engraved, colon studs, seconds scale 0–60 with an accent capsule, date window top right ("SAT 15") |

All three: the dial fills the whole face (no border), matte grain + soft top-left light + edge falloff; applied
parts cast a two-layer shadow (tight contact + wide soft), hands progressively higher (H < M < S).

## Options (내 시계 꾸미기, through existing `ClockStyle` fields where they fit)

- 다이얼 색: the mockup's 8 presets (아이스, 베이지, 크림, 로즈, 스카이, 테니스, 옐로, 코얼 — exact hex in the mockup) +
  색 조합 바꾸기 (existing `ColorShuffle`, add a plan for these faces) + 직접 고르기. Ink/accent/lume follow the preset;
  a custom dial derives ink by luminance (dark dial → light ink).
- 강조 색 (second hand / seconds capsule).
- 보기: 낮 / 야광 / 밤에만 야광. At night the app's night rule still applies: the lume view drawn in the night ink
  (`ThemeTokens.NIGHT_INK`), no grain/light/shadows (night >= 0.5).
- 초침: 쿼츠 틱 / 기계식 스윕 (A, B). C: tick only.
- A/B: 숫자 / 인덱스만. C: 24시간 / 12시간, 꺼진 획 보이기, 날짜 창 켜기·끄기.

## Performance (StandBy runs for hours on a charger)

- Everything static (dial, grain, track, numerals, capsules, ghost segments, shadows) is drawn once into a cached layer
  (`drawWithCache` / an `ImageBitmap`) per size+style; grain is one small noise bitmap tiled, generated once.
- Per tick only the hands (A, B) or the lit segments/seconds capsule (C) are drawn, via `graphicsLayer` rotation where
  possible. Sweep redraws at most 30 fps and only while visible; quartz tick redraws once per second.
- Shadows: precomputed (blurred once into the cached layer for static parts; hands use a cached blurred hand bitmap
  rotated with the hand), never a per-frame blur.

## Done when

- Three faces selectable everywhere a clock face is (clock page, clock card arrows, 내 시계 list), with previews.
- Options above work and round-trip in the config codec (TDD for any new field + old-config decode).
- Shots: each face in 2 dial colours (아이스, 코얼), lume view, night, a card size (M) — IPHONE runner only.
- Inspected at full size against the mockup; no per-frame allocation in the draw path.
