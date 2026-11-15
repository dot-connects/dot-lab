package kr.dot.sb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import java.time.LocalTime

/*
 * Theme system (r9; r13 Part D: seven themes), one body per card/surface. Everything that differs between themes is a token
 * ([ThemeTokens]: colours, plates, digit font, radius); cards read colours from [Ink] and never branch on [Theme].
 * Adding a theme = one more [ThemeTokens] value and one `r9(...)` line in [Themes].
 */

/**
 * Text roles relative to the slot short side `s` (design-system §2.3). [label] is also floored by [StandbyTheme.minText]
 * of the screen (board-wide), so the smallest line never drops below 4.5 % of the screen (r7: 6 % read too big in Hangul).
 */
@Immutable
data class Ramp(val display: Float, val value: Float, val title: Float, val label: Float, val pad: Float) {
    companion object {
        val IPHONE = Ramp(display = 0.56f, value = 0.30f, title = 0.12f, label = 0.08f, pad = 0.07f)
    }
}

@Immutable
class StandbyTheme(
    val id: Theme,
    /** r9 tokens: colours, plates, digits (ThemeTokens). */
    val tokens: ThemeTokens,
    /** Clock styles before the user changed anything (settings "기본값"). */
    val clockDefaults: (FaceId) -> ClockStyle,
) {
    /** Words (Korean + Latin) at a weight. */
    fun words(weight: Int): FontFamily = Fonts.ui(weight)
    /** Card words (plan-r13 A3): the token font at [weight] after [cardWeight]; GOTHIC NORMAL = [words]. */
    fun cardWords(weight: Int): FontFamily = Fonts.cardFamily(tokens.cardFont, cardWeight(weight))
    /** [weight] with the token text weight (Pretendard only). */
    fun cardWeight(weight: Int): Int = cardFontWeight(tokens.cardFont, tokens.textWeight, weight)
    val cardCap: Float get() = tokens.cardFont.cap
    /** Widget numerals ("9", "7°", "20%", "D-10"): digit runs inside mixed text use it too (TERMINAL: its mono). */
    val numerals: FontFamily get() = tokens.latinFont ?: Fonts.rounded
    val numeralsCap: Float get() = if (tokens.latinFont != null) tokens.digitCap else 0.73f
    val ramp: Ramp get() = Ramp.IPHONE
    /** Smallest text on a StandBy surface as a fraction of the screen short side. */
    val minText: Float get() = 0.038f
    /** Label rows draw their glyph; otherwise a bare tracked kicker (token table). */
    val labelIcons: Boolean get() = tokens.labelIcons
    val labelTracking: Float get() = tokens.tracking

    /** Clock family for a font choice; [FontId.THEME] = this theme's digit font (D4b). */
    fun digits(f: FontId, w: Int): FontFamily = if (f == FontId.THEME) tokens.digitFont(w) else Fonts.clock(f, w)
}

object Themes {
    private fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL

    /**
     * Clock defaults from the tokens: digits/second hand = accent, hands/numerals = ink, ticks = ink2 on the page;
     * clocks sit on the page (Background.BLACK = page, D4b). Digits are [FontId.THEME] (D4b); SOLAR keeps its gradients.
     */
    private fun defaults(tk: ThemeTokens): (FaceId) -> ClockStyle = { face ->
        // r10 faces with their own font (FaceDefaults) keep it under every theme
        val (font, weight) = kr.dot.sb.ui.clock.FaceDefaults.font(face) ?: (FontId.THEME to tk.digitWeight)
        val ink = tk.ink.long(); val acc = tk.accent.long()
        val tick = tk.ink.copy(alpha = tk.ink2).compositeOver(tk.bg.base).long()
        when (face) {
            FaceId.ANALOG -> ClockStyle(
                font, weight, color = acc, numeralColor = ink, handColor = ink,
                secondColor = acc, weekdayColor = tk.accentText.long(), tickColor = tick,
            )
            // 벽시계: a light dial with dark ink and a red second hand under every theme (clocks.html c-wall)
            FaceId.WALL -> ClockStyle(
                font, weight, color = acc, analogNumbers = kr.dot.sb.model.AnalogNumbers.TWELVE,
                numeralColor = kr.dot.sb.ui.clock.FaceDefaults.WALL_INK, handColor = kr.dot.sb.ui.clock.FaceDefaults.WALL_INK,
                tickColor = kr.dot.sb.ui.clock.FaceDefaults.WALL_INK, secondColor = kr.dot.sb.ui.clock.FaceDefaults.WALL_SECOND,
                weekdayColor = tk.accentText.long(),
            )
            FaceId.SOLAR -> ClockStyle.default(face).copy(font = font, weight = weight)
            FaceId.PHOTOS_TIME -> ClockStyle(font, weight, color = 0xFFFFFFFF)
            else -> ClockStyle(font, weight, color = acc, litDotColor = tick)
        }
    }

    private fun r9(id: Theme, tk: ThemeTokens) = StandbyTheme(id, tk, defaults(tk))

    /**
     * r11: [base] drawn with edited tokens [tk]. Clock defaults come from [still] (the edit without 시간 따라 흐름), so
     * hero digits and hands follow an edited accent/ink but never the time of day.
     */
    fun edited(base: StandbyTheme, tk: ThemeTokens, still: ThemeTokens = tk): StandbyTheme = StandbyTheme(base.id, tk, defaults(still))

    val IPHONE = r9(Theme.IPHONE, ThemeTokens.IPHONE)
    val DAYPAPER = r9(Theme.DAYPAPER, ThemeTokens.DAYPAPER)
    val RADIO = r9(Theme.RADIO, ThemeTokens.RADIO)
    val MOODLIGHT = r9(Theme.MOODLIGHT, ThemeTokens.MOODLIGHT)
    val SKY = r9(Theme.SKY, ThemeTokens.SKY)
    val NEON = r9(Theme.NEON, ThemeTokens.NEON)
    val TERMINAL = r9(Theme.TERMINAL, ThemeTokens.TERMINAL)

    /** Light themes drawn on a dark board background (plan Task 5 open item, decided in Task 20). */
    private val twins: Map<Theme, StandbyTheme> by lazy {
        Theme.entries.filter { of(it).tokens.light }.associateWith { r9(it, of(it).tokens.onDark()) }
    }

    /**
     * [th] as drawn on board background [bg]: a light theme on a dark background (any preset, or a dark [custom]
     * colour) becomes its dark twin, so its ink is not dark on dark.
     */
    fun on(th: Theme, bg: BoardBg, custom: Color): StandbyTheme {
        val dark = bg != BoardBg.THEME && (bg != BoardBg.SOLID || custom.luminance() < 0.4f)
        return if (dark) twins[th] ?: of(th) else of(th)
    }

    fun of(t: Theme): StandbyTheme = when (t) {
        Theme.IPHONE -> IPHONE
        Theme.DAYPAPER -> DAYPAPER
        Theme.RADIO -> RADIO
        Theme.MOODLIGHT -> MOODLIGHT
        Theme.SKY -> SKY
        Theme.NEON -> NEON
        Theme.TERMINAL -> TERMINAL
    }
}

/** The style [face] is drawn with: the user's own edit ([AppConfig.styles] holds edits only, r9 D4), else the theme's default. */
fun AppConfig.clockStyle(face: FaceId): ClockStyle = styles[face] ?: drawnTheme().clockDefaults(face)

/** The theme without the user's edit (r11): night logic and `light` read this, so night never changes with an edit. */
fun AppConfig.baseTheme(): StandbyTheme = Themes.on(theme, boardBg, Color(boardBgColor))

/**
 * The theme as this config draws it: on a dark board background a light theme is its dark twin ([Themes.on]); on the
 * theme background the theme's edit (r11, [AppConfig.themeEdits]) is applied, with 시간 따라 흐름 at [at] (null = no
 * flow). No edit = the [baseTheme] instance itself.
 */
fun AppConfig.drawnTheme(at: LocalTime? = null): StandbyTheme {
    val base = baseTheme()
    val e = themeEdits[theme]
    if (boardBg != BoardBg.THEME || e == null) return base
    val bucket = if (e.flow != null && at != null) at.toSecondOfDay() / 600 + 1 else 0
    return EditCache.get(Triple(theme, e, bucket)) {
        val still = base.tokens.with(e)
        val tk = if (bucket == 0) still else base.tokens.with(e, LocalTime.ofSecondOfDay((bucket - 1) * 600L))
        Themes.edited(base, tk, still)
    }
}

/** Edited themes by (theme, edit, 10-minute flow bucket): one immutable map swapped whole (holder thread + composition). */
private object EditCache {
    private const val MAX = 16
    @Volatile private var map: Map<Triple<Theme, ThemeEdit, Int>, StandbyTheme> = emptyMap()

    fun get(k: Triple<Theme, ThemeEdit, Int>, make: () -> StandbyTheme): StandbyTheme {
        map[k]?.let { return it }
        val v = make()
        val m = LinkedHashMap(map)
        m[k] = v
        while (m.size > MAX) m.remove(m.keys.first())
        map = m
        return v
    }
}

/** Colour fields of a [ClockStyle] the theme sets: what a theme change moves, and what an edit may rebase. */
private val THEMED: List<Pair<(ClockStyle) -> Any, (ClockStyle, ClockStyle) -> ClockStyle>> = listOf(
    { s: ClockStyle -> s.color } to { s, d -> s.copy(color = d.color) },
    { s: ClockStyle -> s.color2 } to { s, d -> s.copy(color2 = d.color2) },
    { s: ClockStyle -> s.background } to { s, d -> s.copy(background = d.background) },
    { s: ClockStyle -> s.bgColor } to { s, d -> s.copy(bgColor = d.bgColor) },
    { s: ClockStyle -> s.bgColor2 } to { s, d -> s.copy(bgColor2 = d.bgColor2) },
    { s: ClockStyle -> s.numeralColor } to { s, d -> s.copy(numeralColor = d.numeralColor) },
    { s: ClockStyle -> s.handColor } to { s, d -> s.copy(handColor = d.handColor) },
    { s: ClockStyle -> s.secondColor } to { s, d -> s.copy(secondColor = d.secondColor) },
    { s: ClockStyle -> s.weekdayColor } to { s, d -> s.copy(weekdayColor = d.weekdayColor) },
    { s: ClockStyle -> s.tickColor } to { s, d -> s.copy(tickColor = d.tickColor) },
    { s: ClockStyle -> s.litDotColor } to { s, d -> s.copy(litDotColor = d.litDotColor) },
)

/**
 * r11 (plan round 3): kept clock styles from drawn theme [old] to [new], field by field: a colour still equal to
 * [old]'s default moves to [new]'s, the user's own pick stays. A style left equal to the new default is dropped.
 */
private fun rebaseStyles(styles: Map<FaceId, ClockStyle>, old: StandbyTheme, new: StandbyTheme): Map<FaceId, ClockStyle> {
    if (old === new) return styles
    return styles.mapNotNull { (f, s) ->
        val o = old.clockDefaults(f)
        val d = new.clockDefaults(f)
        val r = THEMED.fold(s) { acc, (get, set) -> if (get(s) == get(o)) set(acc, d) else acc }
        if (r == d) null else f to r
    }.toMap()
}

/**
 * The one way to change theme (r9 D4: settings, the pull-down panel and previews all call it). Edited clock styles keep
 * their font, size and effect but take the new theme's colours and background; an edit left equal to the new default
 * is dropped, so it follows the theme again.
 */
fun AppConfig.withTheme(th: Theme): AppConfig {
    if (th == theme) return this
    val t = copy(theme = th).drawnTheme()
    val kept = styles.mapNotNull { (f, s) ->
        val d = t.clockDefaults(f)
        val r = THEMED.fold(s) { acc, (_, set) -> set(acc, d) }
        if (r == d) null else f to r
    }.toMap()
    return copy(theme = th, styles = kept)
}

/**
 * r11: set [th]'s edit (null or an empty edit clears it). Sanitised; when [th] is the current theme the kept clock
 * styles are rebased ([rebaseStyles]) so an edited accent/ink reaches clocks whose font or size the user changed.
 */
fun AppConfig.withThemeEdit(th: Theme, e: ThemeEdit?): AppConfig {
    val clean = e?.sanitized(th)?.takeUnless { it.isEmpty }
    val edits = if (clean == null) themeEdits - th else themeEdits + (th to clean)
    if (edits == themeEdits) return this
    val next = copy(themeEdits = edits)
    return if (th != theme) next else next.copy(styles = rebaseStyles(styles, drawnTheme(), next.drawnTheme()))
}

/** r11: the board background (and the 단색 colour), rebasing kept clock styles like [withThemeEdit]. */
fun AppConfig.withBoardBg(bg: BoardBg, color: Long = boardBgColor): AppConfig {
    if (bg == boardBg && color == boardBgColor) return this
    val next = copy(boardBg = bg, boardBgColor = color)
    return next.copy(styles = rebaseStyles(styles, drawnTheme(), next.drawnTheme()))
}

/** r11 내 테마 list (save, rename, delete). */
fun AppConfig.withMyThemes(list: List<MyTheme>): AppConfig = copy(myThemes = list)

/** r11: switch to a saved 내 테마 — its base theme on the theme background (so the look shows), with its edit. */
fun AppConfig.applyMyTheme(m: MyTheme): AppConfig =
    withTheme(m.base).withBoardBg(BoardBg.THEME).withThemeEdit(m.base, m.edit).copy(customFrom = m.name)

/** The next free "내 테마 N". */
fun nextMyThemeName(list: List<MyTheme>): String =
    generateSequence(list.size + 1) { it + 1 }.map { "내 테마 $it" }.first { n -> list.none { it.name == n } }

/** The tile the theme strip marks (plan-r13 A1): a [Look], or [Custom] = an edit saved in no 내 테마 ("저장 안 함"). */
sealed interface StripPick {
    data object Custom : StripPick

    companion object {
        /** The 내 테마 equal to (theme, edit) (the [AppConfig.customFrom] one first); else the built-in when unedited. */
        fun of(c: AppConfig): StripPick {
            val e = c.themeEdits[c.theme] ?: ThemeEdit()
            val same = c.myThemes.filter { it.base == c.theme && it.edit == e }
            (same.firstOrNull { it.name == c.customFrom } ?: same.firstOrNull())?.let { return Look.Mine(it) }
            return if (e.isEmpty) Look.Builtin(c.theme) else Custom
        }
    }
}

/** What a strip tap switches to. */
sealed interface Look : StripPick {
    data class Builtin(val theme: Theme) : Look
    data class Mine(val my: MyTheme) : Look
}

/**
 * The one way to change the look (plan-r13 A1): built-in tile, 내 테마 tile, start chip, [버리기], panel. Every
 * `themeEdits` entry except the target's is cleared, so a discarded edit never comes back.
 */
fun AppConfig.switchLook(to: Look): AppConfig {
    val c = when (to) {
        is Look.Builtin -> withTheme(to.theme).withThemeEdit(to.theme, null).copy(customFrom = null)
        is Look.Mine -> applyMyTheme(to.my)
    }
    return c.copy(themeEdits = c.themeEdits.filterKeys { it == c.theme })
}

/** 이전 조합 (plan-r13 A4): back to [th] with [e] and [from]; like [switchLook], no other theme keeps an edit. */
fun AppConfig.restoreLook(th: Theme, e: ThemeEdit?, from: String?): AppConfig =
    withTheme(th).withThemeEdit(th, e).let { c -> c.copy(customFrom = from, themeEdits = c.themeEdits.filterKeys { it == th }) }

/** StandBy's pull-down panel: an unsaved edit is kept as the next "내 테마 N" first (no dialog on the stand screen). */
fun AppConfig.switchFromPanel(th: Theme): AppConfig =
    (if (StripPick.of(this) == StripPick.Custom) saveMyTheme(nextMyThemeName(myThemes)) else this).switchLook(Look.Builtin(th))

/** 저장: the current look under [name]; an existing 내 테마 of that name is replaced in place (덮어쓰기), else appended. */
fun AppConfig.saveMyTheme(name: String): AppConfig {
    val m = MyTheme(name, theme, themeEdits[theme] ?: ThemeEdit())
    val i = myThemes.indexOfFirst { it.name == name }
    return copy(myThemes = if (i < 0) myThemes + m else myThemes.toMutableList().also { it[i] = m }, customFrom = name)
}

fun AppConfig.renameMyTheme(m: MyTheme, name: String): AppConfig = copy(
    myThemes = myThemes.toMutableList().also { l -> l.indexOf(m).takeIf { it >= 0 }?.let { i -> l[i] = m.copy(name = name) } },
    customFrom = if (customFrom == m.name) name else customFrom,
)

fun AppConfig.deleteMyTheme(m: MyTheme): AppConfig = copy(
    myThemes = myThemes.toMutableList().also { it.remove(m) },
    customFrom = customFrom.takeUnless { it == m.name },
)

/**
 * r11 시간 따라 흐름: the time edited themes are drawn at, in 10-minute steps (null = no flow). Dynamic, not static: only
 * [ProvideTheme] reads it, and only for a config with a flow, so the root's 10-minute change recomposes nothing else.
 */
val LocalThemeTime = compositionLocalOf<LocalTime?> { null }

/** [t] truncated to its 10-minute step (the flow's resolution). */
fun themeTimeOf(t: LocalTime): LocalTime = LocalTime.ofSecondOfDay(t.toSecondOfDay() / 600 * 600L)

val LocalTheme = staticCompositionLocalOf { Themes.IPHONE }

/**
 * False while the page this is drawn on cannot be seen: an opaque overlay (alarm, live activity, Now Playing) covers
 * it, or it is not the pager's current page. Clock tickers stop on false (r11 perf); default true (settings, previews).
 * Dynamic, so a change recomposes only its readers.
 */
val LocalClockActive = compositionLocalOf { true }

/** The board background below [LocalTheme] (settings previews draw it behind their content). */
val LocalBoardBg = staticCompositionLocalOf { BoardBg.THEME to Color(0xFF1A1430) }

/** Provide [theme] to everything below (test harness; the app goes through the [AppConfig] overload). */
@Composable
fun ProvideTheme(theme: Theme, content: @Composable () -> Unit) = ProvideTheme(Themes.of(theme), BoardBg.THEME to Color(0xFF1A1430), content)

/** Provide the config's drawn theme, board background and text size (StandbyRoot, BoardView, CardView and settings call it). */
@Composable
fun ProvideTheme(c: AppConfig, content: @Composable () -> Unit) {
    // the time is read only when this config draws a flow (unedited users never recompose from it)
    val at = if (c.boardBg == BoardBg.THEME && c.themeEdits[c.theme]?.flow != null) LocalThemeTime.current else null
    CompositionLocalProvider(LocalTextScale provides c.textSize.f) { ProvideTheme(c.drawnTheme(at), c.boardBg to Color(c.boardBgColor), content) }
}

/** [ProvideTheme] at [at] (StandbyRoot: the state's minute; settings: now or the editor's 아침/낮/저녁 chip). */
@Composable
fun ProvideTheme(c: AppConfig, at: LocalTime, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalThemeTime provides themeTimeOf(at)) { ProvideTheme(c, content) }

/** Always provides (never "skip when equal"): a branch on the value would reset everything remembered below on a switch. */
@Composable
private fun ProvideTheme(t: StandbyTheme, bg: Pair<BoardBg, Color>, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalTheme provides t, LocalBoardBg provides bg, content = content)
