# StandBy r11 plan — theme editor + perf Lows

Operator 2026-11-14 22:40: "남은거 전부" — the theme editor (`spec-theme-editor.md`, approved mockup
https://claude.ai/artifact/GsYtS8bHoa84kkoSsqij4m part 2, all six extras approved) and the deferred perf Lows. Deadline:
personal release APK by 2026-11-15 07:00 KST, after /done. App: `app/standby` (Kotlin + Compose, Robolectric + Roborazzi).
Plan-review round 1 (4 lenses) changed: no SOLID migration, edits only on the theme background, `light`/night untouched,
codec sanitising, explicit control→field table, relative contrast warnings, B3 dropped.

## Global constraints

- Default config (no edit) renders identically: existing shots 0 diff, except the 테마와 배경 screen shots.
- Night is unchanged: night logic (`StandbyStateHolder` forceNight / luxNight) reads the **unedited** theme
  (`AppConfig.baseTheme()` = today's `Themes.on(...)`); `ThemeTokens.night()` still replaces the edited day look.
- `ThemeTokens.light` is never recomputed from an edit (it drives night and the dark twins).
- No new dependency (no androidx.palette); photo pick via `ActivityResultContracts.PickVisualMedia` (activity-compose
  1.9.3 present), no new permission.
- `tools/check_literals.py` forbids the substring `copy(theme` outside `ui/theme/Theme.kt`, which also matches
  `copy(themeEdits = …)`: all config writers for the new fields live in `Theme.kt` as `AppConfig.withThemeEdit(th, e)`
  and `AppConfig.withMyThemes(list)`; everything else calls those.
- Enums stored by `.name`, never ordinal (R8 has 0 keep rules; org.json codec).
- English code/comments; Korean UI strings under `settings/` (literal rules).
- Verification speed (operator rule): record only the shot classes a step touches, `-Pshot.themes=IPHONE` unless the
  step names themes. One extra IPHONE Board+Settings compare right after A2 (shared code). Full suite once before the
  APK. One Gradle job at a time; never `gradlew clean`.
- TDD for: codec round-trip + sanitising, edit apply, contrast warnings, shuffle, flow interpolation, photo palette,
  editor table coverage.

## Part A — theme editor

### A1. Model + codec (TDD)

`model/Config.kt`:
```kotlin
enum class GlowShape(val ko: String) { ROUND("둥근"), SUNSET("노을"), TWIN("둘") }
data class ThemeEdit(
    val bg: List<Long>? = null,          // 1..3 stops (SKY: 3 = top/mid/bottom)
    val glow: Long? = null,              // MOODLIGHT, CANDLE
    val glowStrength: Float? = null,     // 0..1, multiplies the glow alpha; NEON: halo strength
    val glowAt: Pair<Float, Float>? = null, // 0..1 of the page
    val glowShape: GlowShape? = null,
    val plate: Long? = null,             // card colour; RADIO: 판; FLIP: plate (never on PAPER)
    val rule: Long? = null,              // PAPER only: rule-line colour (new token, plate stays CLEAR)
    val plateAlpha: Float? = null,       // 0.2..1 "카드 진하기"
    val plateStyle: PlateStyle? = null,  // only styles in the theme's offer list
    val radius: Float? = null,           // radiusRatio 0..0.2
    val accent: Long? = null, val ink: Long? = null,
    val flow: List<Long>? = null,        // 2..3 stops morning → (noon) → evening; null = off
)
data class MyTheme(val name: String, val base: Theme, val edit: ThemeEdit)
// AppConfig += themeEdits: Map<Theme, ThemeEdit> = emptyMap(), myThemes: List<MyTheme> = emptyList()
// SlotOptions += plate: Long? = null   (A6)
```
`ConfigCodec`: encode only non-null fields; omit empty maps/lists (an old config re-encodes byte-identical).
**Sanitise on decode and before encode** (`ThemeEdit.sanitized()` in Config.kt, one function, used by both and by
`withThemeEdit`): drop non-finite floats, clamp ranges above, `bg` 1..3 entries else null, `flow` 2..3 else null,
`glowAt` clamped to 0..1, unknown enum names dropped, a malformed entry drops only itself. An edit that becomes empty
is removed from the map. `BoardBg` is untouched (SOLID stays a preset; no migration).
Tests: full round-trip (every field + myThemes + SlotOptions.plate); NaN/negative/empty/garbage values → sanitized,
never throw; old config JSON without the keys decodes to empty and re-encodes identically.

### A2. Apply (one place, TDD)

- `ThemeTokens.with(e: ThemeEdit, at: LocalTime): ThemeTokens` in `ThemeTokens.kt`. Fields → tokens:
  `bg` → `bg.stops`; `glow`/`glowStrength` → `bg.glow` (alpha × strength); `glowAt` → `bg.glowAt`; `glowShape`: ROUND =
  today's radial, SUNSET = glowAt (0.5, 1.15) glowSize (1.1, 0.8), TWIN = second radial at the mirror of glowAt
  (1−x, 1−y), same size (`BgSpec.glow2At: Offset? = null`, drawn by `ground()` in Primitives.kt; the drag moves the
  first, the second mirrors); `plate` → `plate` and `plate2` (plate2 = plate lightened 6 % on dark, darkened on light);
  `plateAlpha` → both plates' alpha × value (opaque FILL plates become translucent over the page); `plateStyle`;
  `radius` → `radiusRatio`; `accent` → `accent`; `ink` → `ink`.
  Re-derive after: `accentText = Contrast.fit(accent, surfaces, light, 4.5f)`, roles = `mono(accent, ink)` for mono
  themes else `Roles.derive(Roles.IOS, surfaces, light)`. `light` keeps the theme's value.
  NEON `glowStrength` scales the OUTLINE halo alphas in `themedSurface` (via a new `ThemeTokens.halo: Float = 1f`).
  PAPER `rule` -> new `ThemeTokens.rule: Color? = null`; `themedSurface` RULE draws it when set, else today's ink 25 %.
  Not part of `surfaces` (a line, not a plate). NEON/TERMINAL: an edited `ink` also re-tints `plate2` (ink at its
  current alpha) so the progress track follows.
  Roles: store the theme's `monoRoles` flag on `ThemeTokens` (new field, set in `make`) and branch on it; PAPER has
  mono icons but not mono roles. `accentText`: if `accent` is unchanged and the old `accentText` still reaches 4.5:1
  on the new surfaces, keep it (hand-set CANDLE/DAYPAPER/RADIO values survive an ink-only edit); else `Contrast.fit`.
  Light themes (DAYPAPER, RADIO): bg colour picks are floored at HSL lightness 0.6 (the picker clamps), so `light`
  stays true and derivation never runs toward black.
- Clock styles: `withThemeEdit` rebases the kept `styles` exactly like `withTheme` does (extract the shared rebase in
  Theme.kt), so an edited accent/ink reaches big digits and hands for users who changed a clock's font or size.
- `flow` (A4) at time `at`: MOODLIGHT/CANDLE → glow colour; SKY → the top stop. Other themes: the editor never offers it.
- `Themes.edited(base: StandbyTheme, tk: ThemeTokens): StandbyTheme` (public, next to the private `r9`) rebuilds
  `clockDefaults` from the edited tokens, so hero digits/hands follow an edited accent/ink.
- `AppConfig.drawnTheme(at: LocalTime = LocalTime.now())`: `base = Themes.on(...)`; if `boardBg != THEME` or no edit
  for `theme` → `base` (same instance, so 0 diff). Else `Themes.edited(base, base.tokens.with(edit, at))`.
  **Edits apply only on the theme background.** With a preset background the editor shows one note line
  "배경을 '테마 기본'으로 두면 편집한 색이 보여요" and a [테마 기본으로 바꾸기] chip. (Light themes on a preset are
  the twins and are never edited, so no dark-on-dark.)
- `AppConfig.baseTheme()` = `Themes.on(...)`; `StandbyStateHolder` lines 256/272 switch to it (night unchanged).
- Cache: a small LRU (16 entries) keyed by (theme, edit, flow bucket = minute of day / 10 when flow set, else 0) in
  `Theme.kt`, held as one `@Volatile` immutable map reference (holder thread + composition). The theme strip's 10
  previews + main preview fit. StandBy recomposes on flow change at most every 10 min.
- Time: `ProvideTheme(config, at: LocalTime)` (default `LocalTime.now()` only at the app entry points StandbyRoot /
  Settings, which already tick per minute) provides `LocalThemeTime`; `clockStyle()` callers inside composition read
  it. Shots pass a fixed `at`; the flow shot sets 09:30.
- Route `Listener.publishShade` unchanged (monoIcons is not editable).
- Right after A2: IPHONE Board + Settings compare → must be 0 diff.

### A3. Contrast warnings (TDD)

`Contrast.warnings(edited: ThemeTokens, base: ThemeTokens): List<String>` — relative to the theme's own default so
RADIO's intentional accent never warns: ink vs any surface < min(4.5, base ratio) → "글자가 흐려요"; ink at `ink2`
alpha (composited) < min(3, base) → "작은 글자가 흐려요"; accent vs plate-over-page < min(3, base) → "강조 색이 흐려요".
Shown in amber under the editor controls. Manual picks are allowed.

### A4. Flow interpolation (TDD)

Operator 11-14: the flow runs over the **day** (night mode covers the evening), labels 아침 → 낮 → 저녁.
`flowColor(stops: List<Long>, t: LocalTime): Color?` — null 21:00–05:59 (night: no flow, the edit/token colour).
3 stops: 06:00 = s0 → 13:00 = s1 → 21:00 = s2, linear `lerp` in sRGB. 2 stops: 06:00 s0 → 21:00 s1.
Tests at 05:59, 06:00, 09:30, 13:00, 17:00, 20:59, 21:00.

### A5. Editor (`settings/ThemeEditor.kt`, under the theme strip in `ThemeScreen`)

Top row first: [색 조합 바꾸기] [이전 조합] [테마 기본으로].
- Shuffle: `ThemeShuffle.next(theme, base: ThemeTokens, rnd: Random): ThemeEdit` — pure; reuses `ColorShuffle`'s tone /
  light helpers (lift them to `internal` in ColorShuffle.kt rather than copy). Draws only this theme's colour fields
  (table below), re-draws up to 30 times until `Contrast.warnings` is empty, else returns the current edit.
  Test: 300 seeds × 10 themes → no warnings. One-level undo kept in a `rememberSaveable` previous edit (screen
  lifetime); 테마 기본으로 = `withThemeEdit(theme, null)` (undoable the same way).
- Controls per theme (`ThemeEditor.controls(theme): List<Control>`, a `when`; `Control` sealed:
  `Colour(field, label, swatches)`, `Slider(field, label, range)`, `Shape`, `CardStyle(offer)`, `Flow`, `GlowDrag`):

| Theme | Controls (label → field) |
|---|---|
| 기본 | 배경 색→bg · 카드 색→plate · 카드 진하기→plateAlpha · 모서리→radius · 강조 색→accent · 글자 색→ink |
| 신문 1면 | 종이 색→bg · 잉크 색→ink · 구분선 색→rule · 강조 색→accent |
| 무드등 | 배경→bg · 불빛 색→glow · 세기→glowStrength · 위치(끌기)→glowAt · 모양→glowShape · 시간 따라 흐름→flow · 카드 모양(판/유리/선)→plateStyle · 진하기→plateAlpha · 모서리→radius · 강조→accent · 글자→ink |
| 촛불 | 배경→bg · 불꽃 빛 색→glow · 세기→glowStrength · 시간 따라 흐름→flow · 카드 진하기→plateAlpha · 강조→accent · 글자→ink |
| 플립 | 배경→bg · 플레이트 색→plate · 숫자 색→accent · 글자 색→ink · 모서리→radius |
| 하늘 | 하늘 위·가운데·아래→bg[0..2] · 시간 따라 흐름→flow · 카드 유리 진하기→plateAlpha · 강조→accent · 글자→ink |
| 종이 | 종이 색→bg · 잉크 색→ink · 카드 색→plate · 강조→accent |
| 브라운 라디오 | 본체 색→bg · 판 색→plate · 다이얼 색→accent · 글자→ink |
| 네온 | 네온관 색→ink · 두 번째 네온→accent · 빛 번짐 세기→glowStrength |
| 터미널 | 형광 색→ink (swatches 초록 #59FF7A · 호박 #FFB000 · 흰색 #E8E8E8 + 직접) · 강조→accent |

  Swatches: dark bg = existing `BG_SWATCHES` + the theme's own; light bg (종이, 라디오, 신문 paper is dark) =
  `0xFFF3EEE4, 0xFFE9E2D2, 0xFFD9D3C7, 0xFFEDE6F0, 0xFFE3ECE6`; other colours = `ClockStyle.SWATCHES`. A row whose
  field is null shows the token colour selected; each row has a small "기본" reset (sets that field null).
  Sliders: existing `SliderRow`, held in local state while dragging and written once on release (no config write per
  frame); glowStrength 0..1, plateAlpha 0.2..1, radius 0..0.2. The theme strip's per-thumbnail `remember` keys on
  (theme, that theme's edit) only, so editing one theme redraws one thumbnail.
  `GlowDrag`: a drag on the editor's live preview box (the `Preview` above) maps to 0..1 of that box; writes on drag
  end only (not per move).
  Test (cheap, TDD): every `Theme` has controls; every control's field is one `ThemeEdit.with()` actually changes.
- 내 테마로 저장: name dialog (default "내 테마 N") → `withMyThemes(myThemes + MyTheme(...))`. The strip shows them first
  with names; tap = `withTheme(base)` then `withThemeEdit(base, edit)`. If `base` already has an edit equal to no saved
  my-theme, the tap first shows an inline row "지금 꾸민 색이 바뀌어요 [바꾸기] [먼저 저장]" (no silent loss).
  Long-press: 이름 바꾸기 / 지우기. No "selected" mark on my themes; the strip's initial scroll targets the current
  theme's index offset by the my-theme count.
- 사진에서 색 뽑기: PickVisualMedia(ImageOnly) → new `ui/theme/PhotoPalette.kt`: decode on `Dispatchers.IO` with
  `ImageDecoder` (`setTargetSize` ≤ 96 px, `ALLOCATOR_SOFTWARE`), pixels → `PhotoPalette.pick(IntArray): ThemeEdit`
  (pure, TDD on synthetic arrays): median-cut 8 boxes; bg = darkest box with saturation < 0.35 (fallback darkest);
  glow/plate = most saturated box with lightness 0.3..0.7; accent = lightest box with saturation ≥ 0.45 (fallback none);
  then contrast-fit accent/ink against the result; fields not offered by the theme are dropped. Light themes use the
  lightest box for bg (floor 0.6).

### A6. Per-card colour

Board editor card row 설정: "카드 색" — "테마 색 따라감" (null, default) + `ClockStyle.SWATCHES` → `SlotOptions.plate`.
Only for FILL/GLASS themes (row hidden for RULE/OUTLINE). `Cards.kt` plate: when set and night < 0.5, the plate colour
is the slot's; ink for that card = `Contrast.fit(ink, listOf(plate over page), light, 4.5f)`. Night ≥ 0.5 ignores it.

### A7. Shots (new only)

`ThemeEditorShots` (IPHONE runner, themes named in the test): editor for MOODLIGHT, SKY, TERMINAL; StandBy board with an
edited MOODLIGHT (GLASS cards, glow moved right, SUNSET); the same at night 1.0 with no radius edit (must equal
unedited MOODLIGHT night);
MOODLIGHT flow at 09:30 (fixed `at`); one board with a per-card plate.

## Part B — perf Lows

1. `Listener.publishShade`: trailing debounce 300 ms with a 1 s max wait (a burst still publishes ≤ 1 s), on the
   listener's existing scope; removals included.
2. (deferred: `publishTimers` replaces the whole state each second; needs a separate timers flow and a `StandbyRoot`
   signature change. Next round.)
3. (dropped — analog numerals are measured once in face setup, not per frame.)
4. Calendar: the agenda/calendar query runs every 15 min, on a `CalendarContract.Events` content-observer change
   (registered in start inside `runCatching`, unregistered in stop), at date change, on calendar-permission change,
   and when `excludedCalendars` changes; the query keeps more than 5 rows and render drops ended events then takes 5. `nextAlarm` and permissions keep their minute refresh.
5. Sleep sound: write in 250 ms chunks into a ≥ 500 ms `AudioTrack` buffer (fewer wakeups), keep the per-sample gain
   ramp; on stop/switch signal the play thread, which calls `pause()` + `flush()` before its own `release()` (never
   touched from main), so sound stops at once and tracks never overlap.

Each: only its own test/shot classes; no visual diff.

## Round 3 fixes (override the sections above where they differ)

- **Style rebase (A2)**: `withThemeEdit` rebases a kept clock style field-by-field: a colour field equal to the
  *previous* drawn theme's default for that face moves to the new default; any other value (the user's own pick) stays.
  New helper `rebaseStyles(old: StandbyTheme, new: StandbyTheme)` in Theme.kt; `withTheme` keeps today's behaviour.
  Test: a pink digit colour survives a radius edit; an untouched default follows an accent edit.
- **Time (A2)**: `LocalThemeTime` holds a 10-minute bucket (`LocalTime` truncated to 10 min). The app root
  (`StandbyRoot`) provides it from `state.now` truncated, so it changes at most every 10 min; Settings provides the
  editor's preview time (below). Every inner `ProvideTheme(config)` defaults `at` to `LocalThemeTime.current`, never
  `LocalTime.now()`. Shots provide a fixed value (flow shot 09:30). With no flow edit the bucket is not read at all,
  so unedited users never recompose from it.
- **Preview time (A5)**: when 시간 따라 흐름 is on, the editor shows chips 아침 · 낮 · 저녁 (07:00 / 13:00 / 19:00)
  that set the Settings preview's `LocalThemeTime` (screen-local state, not saved).
- **Undo (A5)**: undo is `UndoSlot(has: Boolean, edit: ThemeEdit?)` kept with `rememberSaveable` and a `Saver` that
  stores the codec JSON string (ThemeEdit is not Bundle-able). `has=false` = nothing to undo; `has=true, edit=null` =
  undo back to "no edit". Test the Saver round-trip.
- **Colour writes (A5)**: every colour row (swatches and the HSV picker) writes on release only: `ColorPalette` gets
  `onChangeFinished`; during a drag the row and the live preview read a screen-local draft edit. Same for sliders.
  The theme-strip thumbnails keep today's `remember(config, th)` key (no narrowing).
- **Per-card colour (A6)**: the card's tokens = `base.with(ThemeEdit(plate = c, plateAlpha = 1f), at, fitLight =
  c.luminance() > 0.4f)` and its `Ink.of(...)` is rebuilt from those tokens, so ink, ink2/ink3, accent text and roles
  are all derived on the card's plate in the right direction. `with()` gains `fitLight: Boolean = light` used only for
  `Contrast.fit` direction. Stored `SlotOptions.plate` is kept but ignored when the theme's plate style is RULE or
  OUTLINE, when the board uses a preset background, and at night >= 0.5.
- **My themes (A5)**: thumbnails render with `boardBg = THEME`; tapping one also sets `boardBg = THEME` (one
  `applyMyTheme` helper in Theme.kt), so the saved look always shows.
- **GLASS (A2)**: choosing GLASS in the editor caps both plate alphas at 0.55 (no drawing change, so SKY's existing
  glass look is untouched); OUTLINE/FILL as today.
- **plateAlpha** is an absolute alpha (0.15..1) applied to plate and plate2, not a multiplier, so SKY/CANDLE can go
  denser too.
- **Glow position**: a small square position pad in the editor (not a drag on the pager preview); disabled with
  SUNSET (which fixes the position).
- **Shuffle** never draws `flow` stops; warnings are checked for the day look only.
- StandbyStateHolder night reads are at lines ~268/284 (and `Preview.kt:236`, `MoreScreens:109` read `light`, which
  never changes) - all switch to `baseTheme()` for clarity.
- Already in source (verify, don't redo): B1 `Debounce(300, 1_000)` in Listener.kt, B5 pause+flush in SleepService.kt.

## Round 4 fixes (override everything above where they differ)

- **Per-card colour (A6), final**: start from the card's *drawn* tokens (edits included). `plateOverPage` = the chosen
  colour at the theme's plate alpha (GLASS themes: alpha 0.55) composited over the page base. Card ink =
  `Contrast.fit(ink, listOf(plateOverPage), light = plateOverPage.luminance() > 0.4f, 4.5f)`; accent text and roles are
  re-derived on `listOf(plateOverPage)` the same way; the card's `Ink.of(..., page = listOf(plateOverPage))` so
  ink2/ink3 are composited over the card, not the page. Round 5: `Ink.of` gains `over: Color? = null`; `alpha()` and
  ink4 composite over `over ?: bg` (null = today, so unedited configs are unchanged). The card passes
  `over = plateOverPage` and a card `StandbyTheme` built with `Themes.edited` from the card tokens (fitted ink,
  accentText, roles), since `Ink.of` reads ink from the theme. Extra TDD: IPHONE + dark red plate (luminance ~0.2) ->
  ink2 >= 3:1 on the plate. TDD: IPHONE + yellow plate -> ink >= 4.5:1 and ink2 >= 3:1 on
  the plate; DAYPAPER + navy plate -> same; SKY keeps translucent cards.
- **Board background changes** go through `AppConfig.withBoardBg(bg, color)` in Theme.kt, which rebases kept clock
  styles from the old drawn theme to the new one (same field-by-field rule as `withThemeEdit`). `HomeScreen`'s chip and
  the editor's [테마 기본으로 바꾸기] call it. `applyMyTheme` = `withBoardBg(THEME)` first, then `withThemeEdit`.
- **Clock defaults ignore flow**: `Themes.edited` builds `clockDefaults` from the edited tokens *without* flow applied;
  flow changes only the drawn background/glow. So rebase comparisons never depend on the time bucket.
- **plateAlpha** range is 0.05..1 everywhere (sanitizer, slider); an unset field shows the token's own alpha, so
  CANDLE (≈0.06) is reachable and untouched themes never jump.

## Order + verification

B1, B4, B5 → A1 → A2 (+ IPHONE Board/Settings 0-diff compare) → A3, A4 → A5 → A6 → A7 → full suite once →
assemblePersonalRelease → /done review → check_literals/light/fixture/secrets → APK to the operator.
Commit per part with `git commit --only` (dot-lab holds other sessions' files).

## Done when

- Every theme's editor shows only its controls (table test); edits show live in the preview and on StandBy on the
  theme background; preset backgrounds show the note; night identical (shot).
- Shuffle/undo/reset at the top; shuffle never yields a warning (seed test).
- Codec round-trip + sanitising, apply, warnings, flow, photo palette tests green.
- Existing shots 0 diff outside the theme screen; new shots inspected at full size.
- B1, B4, B5 in; no visual diff.
- check_literals / check_light / check_fixture / check_secrets pass; personal release APK sent.
