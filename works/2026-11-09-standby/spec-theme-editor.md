# Theme editor (r11) — "테마 위에 내 색 입히기"

Operator 2026-11-14: wants free control of background and card colours, especially on 무드등. Approved the
mockup https://claude.ai/artifact/GsYtS8bHoa84kkoSsqij4m (part 2: editor + all six ideas). "각 테마에 따라 편집기를
다 최적화", and the three buttons [색 조합 바꾸기] [이전 조합] [테마 기본으로] sit at the TOP of the editor.

## Model

- `ThemeEdit` (per theme, stored in `AppConfig.themeEdits: Map<Theme, ThemeEdit>`), nullable fields: an unset field
  = the theme's token. Applied in one place: `Themes.of(theme)` → `tokens.with(edit)` inside `drawnTheme()`, so every
  surface (StandBy, cards, previews, widgets) follows it and nothing else reads raw tokens.
- Fields map onto existing `ThemeTokens` / `BgSpec`: `bgStops` (1–3 colours), `glow`, `glowStrength` (0–1),
  `glowAt` (Offset), `glowShape` (ROUND / SUNSET from the bottom / TWIN), `plate`, `plateAlpha`, `plateStyle`
  (FILL / GLASS / OUTLINE / RULE, only those the theme offers), `radius`, `accent`, `ink`, `flow` (time-of-day
  colour stops, see below).
- Text colours (`ink2`, `ink3`, accentText, roles) are derived from the edited ink/accent with the existing derive
  functions. Contrast guard: accent vs card ≥ 3:1, ink vs card ≥ 4.5:1. A manual pick below that is allowed but
  shows a warning line ("글자가 흐려요").
- Night mode still wins at night (red night palette); the edit is the day look.
- `BoardBg` ("배경" chips, 단색 swatches) folds into the editor's 배경 control; migrate the stored value into
  `ThemeEdit.bgStops` for the current theme. Keep 사진 흐림 and 시간 따라 as background modes.

## Editor screen (테마와 배경 → under the theme strip)

Top row: [색 조합 바꾸기] [이전 조합] [테마 기본으로]. 색 조합 바꾸기 reuses the clock `ColorShuffle` rules
(contrast-filtered, one-level undo) but over the theme's editable set only. 테마 기본으로 clears that theme's edit
(undoable through 이전 조합).

Then only the controls that the theme has (per-theme table below), each a swatch row + 직접 고르기 (HSV picker).

| Theme | Controls (beyond the shared 강조 색 · 글자 색) |
|---|---|
| 기본 | 배경 색 · 카드 색 · 카드 진하기 · 모서리 |
| 신문 1면 | 종이(배경) 색 · 잉크 색 · 구분선 색 (plate RULE fixed) |
| 무드등 | 배경 · 불빛 색 · 세기 · 위치(끌기) · 모양(둥근/노을/둘) · 시간 따라 흐름 · 카드 모양(판/유리/선) · 진하기 · 모서리 |
| 촛불 | 배경 · 불꽃 빛 색 · 세기 · 카드 진하기 |
| 플립 | 배경 · 플레이트 색 · 숫자 색 · 모서리 |
| 하늘 | 하늘 색 3단(위·가운데·아래) · 시간 따라 흐름 · 카드 유리 진하기 |
| 종이 | 종이 색 · 잉크 색 · 카드 색 |
| 브라운 라디오 | 본체 색 · 판 색 · 다이얼(강조) 색 |
| 네온 | 네온관 색(글자) · 두 번째 네온(강조) · 빛 번짐 세기 |
| 터미널 | 형광 색(초록/호박/흰색/직접) · 강조 색 |

## Extras (all approved)

1. **불빛 위치·모양**: drag the glow on the preview; shapes ROUND / SUNSET / TWIN.
2. **시간 따라 흐름**: 2–3 colour stops (저녁 → 자정 → 새벽); the glow (무드등) or sky gradient (하늘) interpolates
   over the night by local time. Updates once a minute with the existing minute tick; no animation loop.
3. **카드 모양**: FILL / GLASS / OUTLINE where the theme supports it.
4. **내 테마로 저장**: save the current theme + edit as a named entry `MyTheme(name, base: Theme, edit)`. They appear
   first in the theme strip with their names; long-press → 이름 바꾸기 / 지우기.
5. **사진에서 색 뽑기**: pick a photo (system photo picker, no new permission) → androidx.palette (or a small
   median-cut, no new dependency if a few lines do) → bg = darkest muted, glow/plate = vibrant, accent =
   light vibrant, contrast-guarded.
6. **카드마다 다른 색**: advanced, off by default ("테마 색 따라감"). In the board editor, a card row's 설정 offers a
   card colour; stored in `SlotOptions.plate`.

## Done when

- Every theme's editor shows only its controls; edits show live in the preview and on StandBy.
- Shuffle/undo/reset at the top; shuffle never produces text below the contrast floor.
- Stored config round-trips (codec test), BoardBg migration test, contrast-guard test, flow interpolation test (TDD).
- Default (no edit) renders identically: existing shots 0 diff.
- New shots: the editor for 무드등, 하늘 and 터미널; StandBy with an edited 무드등 (glass cards, moved glow).
