# r13 plan — 사용자 지정 테마, 쉬운 설정, 모든 방향

Operator 11-15: (1) theme screen keeps only 테마 / 글자 크기 / 배경; every colour/shape/font/texture edit moves to one
"사용자 지정" screen, layout B (tabs 배경 · 카드 · 글자), buttons [색 조합 바꾸기][이전 조합][저장] in one row, 사진에서
색 뽑기 removed; theme-specific controls all go into that one screen; textures = 종이결 · 필름 그레인 · 린넨 · 헤어라인
금속 · 서리 유리; card fonts 고딕 · 명조 · 둥근 고딕 · 손글씨 (clock digits never change; APK may grow ~2–3 MB).
(2) settings easy for anyone; all 8 permissions asked from the start; 투명도 keeps its name. (3) every phone, portrait
and landscape. Claude implements; /done at the end; record only needed shots.
Mockup: `settings-r10/custom-theme.html` (https://claude.ai/artifact/7QW5qb8RNWBTV5b17mCLyW).
Review: /plan-review round 1 (4 lenses) → this revision; findings and answers in "Review log" at the end.

## Part A — 사용자 지정 테마

**A1 Looks and 내 테마 (decided here).**
- The look = `theme` + `themeEdits[theme]` (unchanged mechanism). Built-in tiles show the theme unedited.
- **Migration, once:** `ConfigCodec.VERSION` 10 → 11; decode runs the r13 migrations only when the stored `"v"` < 11
  (no new flag). Order: first the edit-level migration of A2 on every edit (themeEdits, myThemes, undo slots), then
  every non-empty `themeEdits` entry that equals no `MyTheme` becomes "내 테마 N" (`nextMyThemeName`); the current
  theme's edit stays applied and `customFrom` = that new name (or the existing 내 테마 it equals); other themes' entries are cleared. A fresh `AppConfig()`
  never migrates. TDD (v10 config in, v11 out; v11 decode is a no-op).
- Strip (ThemePicker in `settings/HomeScreen.kt`): 내 테마 … · 10 built-ins · [＋ 사용자 지정]. Built-in tap = `withTheme(th).withThemeEdit(th, null)`; 내 테마 tap =
  `applyMyTheme`; ＋ opens route `THEME_CUSTOM`. Long-press rename/delete of 내 테마 stays as today.
- **Unsaved guard:** when the current edit is non-empty and equals no 내 테마, a built-in tap, a 내 테마 tap or a
  "어디서 시작할까요" chip first asks "저장 안 한 꾸미기가 사라져요" [저장] [버리기] [취소].
- **One switch function** `AppConfig.switchLook(to)` (Theme.kt) for every path — built-in tile, 내 테마 tile, start chip,
  [버리기], panel: sets the target look and clears every `themeEdits` entry except the target's, so a discarded edit
  never comes back (TDD).
- StandBy pull-down panel theme switch (`Host.setTheme` / TopPanel): an unsaved edit is saved silently as "내 테마 N"
  first (no dialog on the stand screen), so the map only ever holds the current look's edit.
- Selected tile (pure `StripPick.of(config)`, TDD): the 내 테마 equal to (theme, edit) → it; else edit empty → the
  built-in; else ＋ 사용자 지정 (shows "저장 안 함").
- 저장 = name dialog prefilled with the 내 테마 the look came from (tracked as `customFrom: String?` in config, set by
  `applyMyTheme`, cleared by built-in tap) else the next "내 테마 N". Same name as an existing 내 테마 → button reads
  "덮어쓰기" (TDD: overwrite replaces exactly one entry, order kept). Renaming a 내 테마 updates `customFrom`; deleting it
  clears it.

**A2 ThemeEdit fields (Config.kt; enums in Config.kt next to GlowShape).** All nullable = the theme's.
- `bgKind: BgKind {SOLID, GRADIENT, GLOW}`
- `plateStyle` offers FILL / GLASS / OUTLINE / RULE on every theme (UI: 판 · 유리 · 테두리선 · 구분선; `PLATE_OFFER`
  removed). `rule` (구분선 색) kept for every theme, shown only when the card shape is 구분선.
- `border: Border {NONE, THIN, THICK}`, `borderColor: Long`
- `bgTexture`, `cardTexture: Texture {NONE, PAPER, GRAIN, LINEN, METAL, FROST}`; one `textureAmt: Float 0.1..1`
  (null = 0.5) for both.
- `textFont: CardFont {GOTHIC, SERIF, ROUND, PEN}`, `textWeight: TextWeight {LIGHT, NORMAL, BOLD}`
- `textGlow: Float 0..1` (0 = off, null = the theme's).
- `sanitized(theme)`: no per-theme field dropping any more; keeps the light-theme page floor; clamps; drops lists of
  the wrong length. Codec: each field encoded; unknown enum name / non-finite / wrong type → null. **Decode migration (v < 11 only):**
  NEON/TERMINAL `glowStrength` (was the halo) → `textGlow` with the same number, `glowStrength` null. TDD: round trip of a full edit,
  garbage cases, both migrations. The SKY "exactly 3 stops" rule goes (any kind on any theme; GRADIENT needs 3, else
  the drawing fills them).

**A3 Drawing.** Every new effect is gated by night inside the drawing code (tokens stay day tokens; `Ink.night`).
- **bgKind** (`ThemeTokens.with`): SOLID → `stops = [bg ?: base]`, glow null. GRADIENT → 3 stops: the edit's `bg` if 3
  long, else base and HSL lightness +0.07 / +0.14 (light themes −0.05 / −0.10). GLOW → `glow = (edit.glow ?: theme glow ?:
  accent)`, alpha = (theme glow alpha ?: 0.35) × 2 × (glowStrength ?: 0.5) — the existing strength rule. Flow keeps today's
  rule (glow colour when there is a glow, else the first stop) on every kind.
- **Card shapes:** FILL/GLASS picked on a theme whose own plate style is RULE or OUTLINE (PAPER, NEON, TERMINAL; never
  CANDLE, whose faint FILL plate is its look) default to alpha 0.85 (GLASS
  0.45) and colour = page base ± 0.06 lightness, unless the edit sets them. RULE draws as PAPER does, colour `rule ?: ink
  25 %`. OUTLINE line = `borderColor ?: ink 35 %`.
- **Border** in `themedSurface` for FILL/GLASS/RULE: THIN 1 dp, THICK 2.5 dp, colour `borderColor ?: ink 35 %`, alpha ×
  (1 − night) and after the existing night ≥ 0.5 early return (fades in the dusk ramp, gone at night).
- **textGlow:** null = the theme's (NEON halo 1 = today; TERMINAL no halo = today; no text shadow anywhere). Set: when
  the *drawn* plate style is OUTLINE the halo is drawn whatever the digit style, at 2 × g (a migrated 0.5 = today's NEON);
  when the drawn style is anything else, a card text shadow (`TextStyle.shadow`,
  blur 4 + 14 × g dp, ink α 0.4 + 0.5 × g); both × (1 − night). Clock faces get nothing.
- **Textures** (`ui/theme/Textures.kt`): one 192 px tile per kind, made once per process with a fixed seed, colours as
  ARGB longs (white / black specks; check_literals safe). Kinds ported from the mockup: PAPER = 28-alpha specks + 160
  fibre strokes; GRAIN = per-pixel specks alpha ≤ 70/255; LINEN = alternating 1 px rows (black 5–17 %) and columns (white
  3–11 %); METAL = 1 px rows white/black ≤ 14 %; FROST = 90 blurred white blobs ≤ 16 % + 20-alpha specks. Drawn with a
  repeat `ImageShader` brush scaled so a tile spans 96 dp, alpha = `textureAmt`: page layer in `ThemeBackground` × (1 −
  night), plate layer inside `themedSurface` (clipped to the plate shape, × (1 − night), after the night early return). Static draw,
  cached bitmap, no per-frame work.
- **Card fonts:** a new token `cardWords(weight)` (from `textFont`; GOTHIC = today's `words()`) is the default family of
  `Txt` in `ui/cards/Kit.kt` and of text in `ui/screens/Overlays.kt` (banner, notification preview, live activity, pills)
  and `PhotosPage` captions. Clock faces (`ui/clock/*`, incl. AnalogFace date/weekday and DigitalFaces info lines) and
  edit chrome keep `words()` (operator: clocks unchanged). `fitsOneLine` takes the family and keys its `remember` on it.
  `numSpans` keeps M PLUS digits only for GOTHIC, other fonts draw their own digits. Every `Txt` call outside the
  listed surfaces that relies on the default family gets `family = words()` explicitly first (ClocksPage edit UI,
  Overlays alarm info line, AnalogFace, DigitalFaces). The direct `CAP_UI` uses in Cards.kt layout maths (calendar rows,
  hourColH, evH and the others) read the token font's cap instead. `CardFont.cap` (cap height / em, read from each font file by the
  subset script and written as constants) replaces `CAP_UI` in `capBox` for that font. Weight: Pretendard is the only
  variable family → LIGHT −200 / BOLD +200 offset on each call's weight (clamped 100..900, hierarchy kept); SERIF (Gowun
  700 only), ROUND, PEN are single-weight → 글자 두께 disabled with "이 글꼴은 두께가 하나예요".
- **Font files:** new resources `card_serif.ttf`, `card_round.ttf`, `card_pen.ttf` subset to the Pretendard set (KS X 1001
  + source chars + titles ranges) from full originals pinned in `tools/fonts-original/card_*.ttf` (GowunBatang-Bold,
  Jua-Regular, NanumPenScript-Regular from github.com/google/fonts ofl; sha256 dbfcaa64…, 769677ae…, 6f0d1ab2…). The clock
  subsets (gowun_batang_700, jua, nanum_pen) stay untouched. `subset_fonts.py`: unknown name → exit, and every original
  for a `card_*` name must be ≥ 1 MB. Licences already in assets/licenses. Test: each card font maps all 2,350 KS X 1001 syllables.

**A4 Screens** (custom screen in `settings/ThemeEditor.kt`; no new screen file).
- `ThemeScreen` (`settings/HomeScreen.kt`): strip, 글자 크기, 배경. Editor section removed.
- `CustomThemeScreen` = the existing editor re-skinned: one universal `Control` list, each control tagged with its tab;
  the visible tab filters it. Top: preview; "어디서 시작할까요" chips (tap = that built-in unedited, through the unsaved
  guard; also sets `customFrom = null`); one row [색 조합 바꾸기][이전 조합][저장] (weights 1.3 / 1 / 1); tabs; contrast
  warnings under the tabs. Writes go live to `themeEdits[theme]` as today; 이전 조합 = one level holding (theme, edit,
  customFrom), not keyed by theme, set by shuffle and start chips; a saved slot from an older version is dropped.
  - 배경: 배경 종류 · 배경 색 (GLOW: "바탕 색") or 그라데이션 위/가운데/아래 · 불빛 색 · 불빛 세기 · 불빛 모양 · 불빛 위치
    (pad off for 노을 with its note) · 시간 따라 흐름 (switch + 아침/낮/저녁 colours + preview chips; always 3 stops) ·
    배경 질감 (+ 질감 정도 when ≠ 없음).
  - 카드: 카드 모양 · 카드 색 · 카드 진하기 (유리: up to 55 %, shown as % of the cap) · 모서리 (%) · 테두리 (+ 색) ·
    구분선 색 (only for 구분선) · 카드 질감.
  - 글자: 글자 색 · 강조 색 · 글꼴 · 글자 두께 · 글자 빛 번짐 (0 = 끔).
  - Every control row keeps its 기본 chip (clears the field).
  - boardBg ≠ THEME: the existing note ("배경을 '테마 기본'으로 두면 꾸민 색·글꼴이 보여요") + [테마 기본으로 바꾸기].
- Landscape/short screens: two-pane (preview left) when width ≥ 600 dp and width > height; otherwise the preview scrolls
  away (not sticky) when height < 480 dp, and only the tab row is sticky.
- Delete `PhotoPalette.kt`, `ThemeEditor.fromPhoto`, `PhotoPaletteTest`, and the fromPhoto cases in `ThemeEditorTest`.
- ThemeShuffle: colour fields only (incl. borderColor, rule), and only the background fields of the current bgKind
  (SOLID: BG; GRADIENT: the 3 stops; GLOW: BG + GLOW); never bgKind, shapes, fonts, textures.
- Known gap (Low): textures are not in `Contrast.warnings`; 질감 정도 capped at 0.8 to keep small text readable.

## Part D — 기본 테마 다듬기 (operator picks 11-15, mockup `settings-r10/theme-polish.html`)

Token changes in `ThemeTokens.kt` (`make(...)`), values exactly as the mockup's chosen column. Unchanged: 종이 (지금).
Fonts, digit styles and shapes stay. Glow tuples are (colour, alpha, centre x, y, size) as in the mockup.

| Theme | Pick | bg | glow(s) | ink | accent | plate (alpha) | radius px in mockup |
|---|---|---|---|---|---|---|---|
| 기본 IPHONE | 다듬기 | #000000 | — | #F5F5F7 | #FF9F0A | #1C1C1E (1), plate2 #2C2C2E | 16 |
| 신문 1면 PAPER | 다듬기 | #11100D | — | #F2EADA | #E8BE62 | RULE | 0 |
| 무드등 MOODLIGHT | 다듬기 | #120D0A | #FF9A4D .30 at .3,.38 size 1.0 | #FFF3E4 | #FFC27A | #2A1E16 (.72), plate2 one step lighter | 20 |
| 촛불 CANDLE | 과감하게 | #080504 | #FF7A2E .34 at .5,1.12 size 1.05 + #FFD27A .22 at .5,1.0 size .45 | #F7E6CF | #FFC070 | #FFCD96 (.08) | 16 |
| 플립 FLIP | 과감하게 | #1A1A1A | — | #F5F1E8 | #F5C400 | #2E2E2E (1), plate2 #383838 | 10 |
| 하늘 SKY | 과감하게 | #0E1E46 → #4B3F86 → #B3605E | — | #FFFFFF | #FFE1B0 | dark glass #0E1030 (.30), plate2 (.38) | 20 |
| 브라운 라디오 RADIO | 과감하게 | #CDC9C1 | — | #22201C | #C2470A (shape and text) | #F3F1ED (1), plate2 #E7E3DC | 14 |
| 네온 NEON | 다듬기 | #05040C | #3A1E6E .55 at .25,.4 size .9 | #7DF9FF | #FF5EDB | OUTLINE | 14 |
| 터미널 TERMINAL | 다듬기 | #04100A | #0F3A1E .6 at .5,.5 size 1.1 | #5CF28A | #FFB000 | OUTLINE | 3 |

- Radius: convert the mockup px (board ~ 300 px tall) to the token ratio by the same factor as today's values
  (keep each theme's ratio change proportional: new = old × mockup_new / mockup_old).
- 촛불's second, smaller yellow glow needs `BgSpec.glow2: Color?` (+ `glow2At`, `glow2Size`): a second colour, drawn after
  the first. TWIN keeps using glow2At with the first colour.
- 라디오 과감하게 "초록 표시등": the derived `green` role is pinned to #2F7D4F (still checked ≥ 3:1).
- Night, onDark twins and the editor's `defaultFlow` follow automatically from the tokens.
- Checks: TokenContrastTest (ink 4.5, ink2 3, accent text 4.5, roles 3) must pass with the new values; where one fails,
  move only that colour's lightness until it passes and note it. Re-record the board/clock shots of the 9 changed themes
  (IPHONE first, then one run per changed theme), committed separately as "theme polish".
- Order: after step 2's baseline diff (so the diff checks only the A3 drawing), before A4.

## Part B — 쉬운 설정

1. **Permissions from the start:** all 8 counted as today. A fresh install (checked in `SettingsActivity`, the launcher:
   `PackageInfo.firstInstallTime == lastUpdateTime` and `permWalkDone` false; `permWalkDone` lives in its own
   no-backup prefs file, so a phone restored from backup with no permissions still gets the walk) opens 권한 first with one [차례로 켜기]; an update never does (decode of a
   v <= 10 config, or no saved config on an updated app, sets `permWalkDone`). Pure `PermWalk.next(missing, asked)` gives
   the next permission not yet asked in this walk; each is opened once (runtime dialog or its system screen); the walk
   advances in `onResume`, and also directly when opening fails or a runtime request returns without showing a screen
   (`PermWalk` ignores a second advance for the same step); runtime permissions denied for good
   open app details. Photos: manifest declares `READ_MEDIA_VISUAL_USER_SELECTED`, both are requested together
   (RequestMultiplePermissions) and `Perms.granted` accepts either. Walk end or [나중에] sets `permWalkDone`. TDD on
   `PermWalk`.
2. 카드 list: only cards with settings, ordered 알림 · 알람 · 시계 · 날씨 · 수면 소리 · 오늘 일정·달력 · D-day ·
   타이머·뽀모도로; footer "폰 위젯은 페이지에서 그 카드의 [설정]을 눌러요". 날씨 row opens the city sheet directly.
3. Fold-only rows/sections hidden on non-Fold phones (Home 폴드; 알람's fold rows) instead of disabled.
4. Words (one pass): 다크·라이트→어둡게/밝게 · 모서리 Ndp→"각지게 ↔ 둥글게" · lx→"어두움 정도" (shown as the lux
   threshold mapped log-scale to 1–20, stored value unchanged) · 쿼츠 틱/기계식 스윕→똑딱/부드럽게 · 인덱스만→눈금만 ·
   솔라 팔레트→색 조합 · 우리만→이 앱에만 · "칸이 Ndp밖에"→"이 폰에선 칸이 너무 작아요" · 칸 크기(W×Hdp) line removed
   (display only) · 알림 표시→알림 · clock 배경→시계 배경 · page 위젯→위젯 페이지 · card-row 색 "설정"→"색" · per-face 시간
   표시→"이 시계만 시간 표시" with the global order. 투명도 unchanged. Check: grep of the old strings = 0.
5. "밤에 알림이 와도 꺼진 화면 켜지 않기" → "밤에 알림 오면 화면 켜기": shows `!nightNotifQuiet`, writes
   `nightNotifQuiet = !v`, disabled (and shown off) when 화면 꺼짐 = 계속 켜 둠. Stored meaning unchanged.
6. 최근 켜짐 기록 → 앱 정보 › 문제 해결. FULL_SCREEN line once in 알람. World-city chip removes only via ✕.
7. Home NavRow values (페이지 구성 "페이지 N개 · 처음: 시계"; 테마와 배경 = 내 테마 name or theme; 앱 정보 = version);
   value one line with ellipsis, never squeezing the title. Preview page-name row → that page's settings route.
8. 처음 보이는 페이지 → sheet. Touch targets ≥ 48 dp (Segmented, ChipFlow, ▲▼✕, colour dots). Dead `Routes.CARD_INFO`
   removed with its two shot tests (SettingsShots.kt).

## Part C — 모든 폰, 가로·세로

1. Insets: settings root `WindowInsets.safeDrawing.exclude(ime)` (cutouts + bars); `imePadding()` only on the Page scroll
   column and the Sheet; Sheet height cap = min(0.86 × h, h − ime), at least 200 dp.
2. D-day date: a Sheet with year (this year … +10) / month / day wheels (reuse the alarm wheel); day clamped to
   `YearMonth.lengthOfMonth()` when month/year changes (TDD: 1/31→2 = 2/28, leap 2/29). Default = today.
3. Short screens: preview height = min(250 dp, 0.5 × height); below 420 dp tall the mini preview shrinks to 56 dp
   without caption; a portrait preview in the left pane is capped to the pane. Two-pane only when width ≥ 600 dp and
   width > height (Fold inner portrait = one column).
4. StandBy overlays: Banner, TopPanel, HeatPill and AlarmScreen take `screenPadding` (cutout-aware).
5. TopPanel scrolls when taller than the screen; QuickSheet wheel item = min(44 dp, width/6, (height − label − pads)/3);
   PreviewBar wraps.
6. Shots (new qualifiers only where needed): `land568 = w568dp-h320dp-land-xxhdpi`, `land780 = w780dp-h360dp-land-xxhdpi`,
   Fold inner portrait `w673dp-h841dp-port-xhdpi`, cover narrow `w360dp-h800dp-port-xxhdpi`, Flip `w300dp-h300dp-land-xhdpi`.
   Settings: home, theme, custom (3 tabs), cards, weather sheet with keyboard, D-day sheet. StandBy: Device entries
   FOLD_INNER_P, COVER_NARROW, FLIP_SMALL for ScreenShots alarm/notif/live/pills + TopPanel + QuickSheet on a 4-up board.

## Order and checks

1. A2 + migrations (tests) → A3 → **baseline diff**: verify existing board/clock shots for IPHONE, NEON, PAPER, DAYPAPER
   against the committed r12 shots before re-recording; only intended changes (none for unedited built-ins) → A4 →
   gates (check_literals, check_light, check_fixture).
2. B, then re-record only the settings shots B changes, committed separately (wording/size churn isolated) → gates.
3. C + new device shots → gates.
4. Fonts subset + APK size before/after (report the number).
5. Full `testPersonalDebugUnitTest` (IPHONE) + all gates + check_secrets → /done → APK.

**Done when:** every mockup row works in the device preview (custom shots, 3 tabs); unedited built-in themes render as
in r12 (baseline verify, 0 unintended diffs); a notification with KS X 1001 text renders in each card font (font cmap
test + one shot); textures, border and text glow fade with the dusk ramp and are absent at night (shots at night 0.3 = reduced, 1.0 =
absent); old edits survive the
update as 내 테마 (migration test); the permission walk runs once on a fresh install and never on an update; settings at
568×320, 780×360, Fold inner portrait, cover narrow and Flip 300×300 show no clipped button or hidden field (shots).

## Review log (round 1, 4 lenses)

High fixed: unsaved edits lost on tap (→ migration + guard); subset-of-subset fonts (→ new card_* files from pinned
originals, exit on unknown, size floor); night leaks (→ gates in drawing code, night shots); custom screen unusable in
landscape (→ two-pane rule, non-sticky preview); texture algorithms, weight rule, glow numbers, edit lifecycle and
구분선 on other themes unspecified (→ A2–A4 values).
Medium fixed: clear plates on PAPER/NEON/TERMINAL; flow kept on SOLID; NEON halo migration; font metrics/numSpans;
Gowun single weight; digit glow perf (→ no digit glow); permission walk states; IME double shrink; D-day clamp;
shot churn (→ baseline diff + separate commit); ThemeEditorTest fromPhoto; gates per part.
Not taken: dropping textWeight / textGlow / border / bgKind (they are rows of the mockup the operator approved);
bundled texture PNGs (seeded tiles are deterministic and need no assets).

## Review log (round 2, opus)

High fixed: migrations gated by codec VERSION 11 (no flag, no re-run on every start; edit-level migration before the
내 테마 comparison); night 0.3 rule made consistent (all new effects × (1 − night), gone ≥ 0.5).
Medium fixed: CANDLE excluded from the clear-plate default; SKY 3-stop rule removed and shuffle follows bgKind;
textGlow numbers (halo 2 × g, no text shadow on OUTLINE themes); card-font surfaces listed, clock faces excluded;
fitsOneLine key; permission walk for updates without saved config, photo partial access, single advance source; panel
theme switch saves unsaved edits.
Low fixed: size floor only for card_*; ThemeScreen location; CARD_INFO tests; customFrom on migration/rename/delete;
texture contrast noted (amount capped 0.8).

## Review log (round 3, opus) — no High; loop stopped

Medium fixed: textGlow follows the drawn plate style (TERMINAL/NEON-on-판 cases); one `switchLook` clears every other
theme's edit so [버리기] really discards; 이전 조합 slot holds the theme too; explicit `words()` on clock/edit `Txt` calls and
Cards.kt cap maths follow the card font; permission walk flag in no-backup prefs (restored phones).
Low fixed: old undo slots dropped; customFrom for edits equal to an existing 내 테마; walk advances when opening fails.
