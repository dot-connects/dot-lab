package kr.dot.sb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
import kr.dot.sb.model.Theme

/*
 * Theme system (r9): ten themes, one body per card/surface. Everything that differs between themes is a token
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
    /** Widget numerals ("9", "7°", "20%", "D-10"): digit runs inside mixed text use it too. */
    val numerals: FontFamily get() = Fonts.rounded
    val numeralsCap: Float get() = 0.73f
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

    val IPHONE = r9(Theme.IPHONE, ThemeTokens.IPHONE)
    val PAPER = r9(Theme.PAPER, ThemeTokens.PAPER)
    val MOODLIGHT = r9(Theme.MOODLIGHT, ThemeTokens.MOODLIGHT)
    val CANDLE = r9(Theme.CANDLE, ThemeTokens.CANDLE)
    val FLIP = r9(Theme.FLIP, ThemeTokens.FLIP)
    val SKY = r9(Theme.SKY, ThemeTokens.SKY)
    val DAYPAPER = r9(Theme.DAYPAPER, ThemeTokens.DAYPAPER)
    val RADIO = r9(Theme.RADIO, ThemeTokens.RADIO)
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
        Theme.PAPER -> PAPER
        Theme.MOODLIGHT -> MOODLIGHT
        Theme.CANDLE -> CANDLE
        Theme.FLIP -> FLIP
        Theme.SKY -> SKY
        Theme.DAYPAPER -> DAYPAPER
        Theme.RADIO -> RADIO
        Theme.NEON -> NEON
        Theme.TERMINAL -> TERMINAL
    }
}

/** The style [face] is drawn with: the user's own edit ([AppConfig.styles] holds edits only, r9 D4), else the theme's default. */
fun AppConfig.clockStyle(face: FaceId): ClockStyle = styles[face] ?: drawnTheme().clockDefaults(face)

/** The theme as this config draws it: on a dark board background a light theme is its dark twin ([Themes.on]). */
fun AppConfig.drawnTheme(): StandbyTheme = Themes.on(theme, boardBg, Color(boardBgColor))

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
        val r = s.copy(
            color = d.color, color2 = d.color2, background = d.background, bgColor = d.bgColor, bgColor2 = d.bgColor2,
            numeralColor = d.numeralColor, handColor = d.handColor, secondColor = d.secondColor, weekdayColor = d.weekdayColor,
            tickColor = d.tickColor, litDotColor = d.litDotColor,
        )
        if (r == d) null else f to r
    }.toMap()
    return copy(theme = th, styles = kept)
}

val LocalTheme = staticCompositionLocalOf { Themes.IPHONE }

/** The board background below [LocalTheme] (settings previews draw it behind their content). */
val LocalBoardBg = staticCompositionLocalOf { BoardBg.THEME to Color(0xFF1A1430) }

/** Provide [theme] to everything below (test harness; the app goes through the [AppConfig] overload). */
@Composable
fun ProvideTheme(theme: Theme, content: @Composable () -> Unit) = ProvideTheme(Themes.of(theme), BoardBg.THEME to Color(0xFF1A1430), content)

/** Provide the config's drawn theme and board background (StandbyRoot, BoardView, CardView and settings call it). */
@Composable
fun ProvideTheme(c: AppConfig, content: @Composable () -> Unit) = ProvideTheme(c.drawnTheme(), c.boardBg to Color(c.boardBgColor), content)

@Composable
private fun ProvideTheme(t: StandbyTheme, bg: Pair<BoardBg, Color>, content: @Composable () -> Unit) {
    if (LocalTheme.current === t && LocalBoardBg.current == bg) content()
    else CompositionLocalProvider(LocalTheme provides t, LocalBoardBg provides bg, content = content)
}
