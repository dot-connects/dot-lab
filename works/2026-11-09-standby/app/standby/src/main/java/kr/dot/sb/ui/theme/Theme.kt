package kr.dot.sb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Background
import kr.dot.sb.model.CardType
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Theme

/*
 * Theme system (mockup r5: A 아이폰 그대로, B 신문 1면, C 무드등).
 *
 * Everything that differs between themes lives in one [StandbyTheme] value, provided by [ProvideTheme] /
 * [LocalTheme]. Cards, clocks and screens read colours from [Ink] (built from the theme's [Palette]), fonts and sizes
 * from [StandbyTheme]; they never branch on [Theme] themselves.
 *
 * Tokens cover most of a theme. Where B and C lay a screen out differently (not just in other colours or fonts: the
 * newspaper masthead, the lamp's pills, the hairline dial, editorial card bodies), the code branches once on
 * [StandbyTheme.family] and hands over to that family's own composables (PaperCards / LampCards, ThemeFaces,
 * ThemeOverlays). Anything a family does not redraw falls back to the shared iPhone layout in the theme's tokens.
 *
 * Adding a theme = one more `val` in [Themes] and one branch in [Themes.of].
 */

/** How a card sits on the board: bare on black (A), on a rounded plate (C), between hairline rules (B). */
enum class Surface { NONE, PLATE, RULE }

/** Which layout family draws the screens that differ beyond tokens (see the file header). */
enum class Family { IPHONE, PAPER, LAMP }

/**
 * Legacy day colours of one theme, by role (family code only; [Ink] now reads [ThemeTokens] and the candle night).
 */
@Immutable
data class Palette(
    val bg: Color,
    /** Text: primary, secondary, track/hairline, plate. */
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val fg4: Color,
    /** Weekday names (calendar, agenda header), "danger" values (usage ≥ 90 %). */
    val red: Color,
    /** D-day, second accents. */
    val orange: Color,
    /** Sun in weather glyphs. */
    val yellow: Color,
    /** Usage rings (battery-widget green). */
    val green: Color,
    /** Sleep sound wave, rain drops. */
    val blue: Color,
    /** Timer / Live Activity plate. */
    val amber: Color,
    /** Alarm snooze pill. */
    val snooze: Color,
    /** Music app glyph. */
    val music: Color,
    /** Card plate (Surface.PLATE) and rule (Surface.RULE) colour. */
    val plate: Color,
    /** Lighter plate (C: pills, transport discs, the plate gradient's lit corner). */
    val plate2: Color = plate,
    /** Usage colour (C: lilac). */
    val lilac: Color = blue,
) {
    companion object {
        /** Mockup r5 A, measured from iPhone frames. */
        val IPHONE = Palette(
            bg = Color.Black,
            fg = Color.White,
            fg2 = Color(0xFF8E8E93),
            fg3 = Color(0xFF3A3A3C),
            fg4 = Color(0xFF1C1C1E),
            red = Color(0xFFFF5B57),
            orange = Color(0xFFFF9F0A),
            yellow = Color(0xFFFFD60A),
            green = Color(0xFF30D158),
            blue = Color(0xFF0A84FF),
            amber = Color(0xFFF5A524),
            snooze = Color(0xFFF26B2A),
            music = Color(0xFFFF5B57),
            plate = Color(0xFF1C1C1E),
        )

        /** Mockup r5 B: warm ink on black, one vermilion accent, hairline rules. */
        val PAPER = Palette(
            bg = Color.Black,
            fg = Color(0xFFEFE8DC),
            fg2 = Color(0xFF8F887C),
            fg3 = Color(0xFF4A463F),
            fg4 = Color(0xFF1F1C18),
            red = Color(0xFFFF5533),
            orange = Color(0xFFFF5533),
            yellow = Color(0xFFEFE8DC),
            green = Color(0xFFFF5533),
            blue = Color(0xFFEFE8DC),
            amber = Color(0xFFFF5533),
            snooze = Color(0xFFFF5533),
            music = Color(0xFFFF5533),
            plate = Color(0xFF4A463F),
            plate2 = Color(0xFF1F1C18),
            lilac = Color(0xFFEFE8DC),
        )

        /** Mockup r5 C: warm plates (≤ 12 % brightness for OLED), one pastel per function. */
        val LAMP = Palette(
            bg = Color.Black,
            fg = Color(0xFFFFF3E6),
            fg2 = Color(0xFFC2B2A2),
            fg3 = Color(0xFF3A2E25),
            fg4 = Color(0xFF2C221B),
            red = Color(0xFFFF9AAA), // rose: calendar
            orange = Color(0xFFFFB27A), // peach: clock
            yellow = Color(0xFFFFDC8F), // butter: sun, play
            green = Color(0xFFB5DC9F), // sage: focus
            blue = Color(0xFF9FD3FF), // sky: weather
            amber = Color(0xFFFFB27A),
            snooze = Color(0xFFFFDC8F),
            music = Color(0xFFC9B8FF),
            plate = Color(0xFF1E1713),
            plate2 = Color(0xFF2C221B),
            lilac = Color(0xFFC9B8FF), // usage
        )
    }
}

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
    val palette: Palette,
    /** Words (Korean + Latin) at a weight. */
    val words: (Int) -> FontFamily,
    /** Cap height / em of [words]. */
    val wordsCap: Float,
    /** Widget numerals ("9", "7°", "20%", "D-10"): digit runs inside mixed text use it too. */
    val numerals: FontFamily,
    val numeralsCap: Float,
    /** Big times on interrupt screens (alarm, timer, live activity) and the timer card. */
    val time: FontFamily,
    val timeCap: Float,
    /** Clock font choice → family. Themes may remap a choice; the user's per-clock choice is always honoured. */
    val clockFamily: (FontId, Int) -> FontFamily,
    /** Clock styles before the user changed anything (settings "기본값"). */
    val clockDefaults: (FaceId) -> ClockStyle,
    val ramp: Ramp,
    /** Smallest text on a StandBy surface as a fraction of the screen short side. */
    val minText: Float,
    val surface: Surface,
    /** Plate / art / button corner radius as a fraction of the element's short side. */
    val radius: Float,
    /** Weather glyphs in colour (yellow sun, white cloud) instead of one ink. */
    val colorIcons: Boolean,
    /** Now Playing background washed with the album art colour. */
    val artTint: Boolean,
    val family: Family = Family.IPHONE,
    /** Label rows draw their glyph (A, C); B labels are bare kickers. */
    val labelIcons: Boolean = true,
    /** Letter spacing of label rows in em (B kickers are tracked out). */
    val labelTracking: Float = 0f,
    /** Plate wash per card type (C): the plate's gradient leans to this colour; null = plain plate. */
    val tint: (CardType) -> Color? = { null },
    /** r9 tokens: colours, plates, digits (ThemeTokens). */
    val tokens: ThemeTokens = ThemeTokens.IPHONE,
) {
    /** Clock family for a font choice; [FontId.THEME] = this theme's digit font (D4b). */
    fun digits(f: FontId, w: Int): FontFamily = if (f == FontId.THEME) tokens.digitFont(w) else clockFamily(f, w)
}

object Themes {
    val IPHONE = StandbyTheme(
        id = Theme.IPHONE,
        palette = Palette.IPHONE,
        words = { Fonts.ui(it) },
        wordsCap = 0.707f,
        numerals = Fonts.rounded,
        numeralsCap = 0.73f,
        time = Fonts.clock(FontId.BOLD, 600),
        timeCap = 0.7275f,
        clockFamily = { f, w -> Fonts.clock(f, w) },
        clockDefaults = { ClockStyle.default(it) },
        ramp = Ramp.IPHONE,
        minText = 0.038f,
        surface = Surface.NONE,
        radius = 0.12f,
        colorIcons = true,
        artTint = true,
        tokens = ThemeTokens.IPHONE,
    )

    private const val P_FG = 0xFFEFE8DC
    private const val P_ACC = 0xFFFF5533

    /** B 신문 1면 (mockup r5 B): Instrument Serif numerals and clock, Pretendard words, hairline rules, one accent. */
    val PAPER = StandbyTheme(
        id = Theme.PAPER,
        palette = Palette.PAPER,
        words = { Fonts.ui(it) },
        wordsCap = 0.707f,
        numerals = Fonts.instrument,
        numeralsCap = 0.70f,
        time = Fonts.instrument,
        timeCap = 0.70f,
        // "명조" is the newspaper serif in this theme; every other choice keeps its own font
        clockFamily = { f, w -> if (f == FontId.SERIF) Fonts.instrument else Fonts.clock(f, w) },
        clockDefaults = { face ->
            when (face) {
                FaceId.ANALOG -> ClockStyle(
                    FontId.SERIF, 400, color = P_FG, analogNumbers = AnalogNumbers.FOUR, numeralColor = P_FG,
                    handColor = P_FG, secondColor = P_ACC, weekdayColor = P_FG, tickColor = P_FG,
                )
                FaceId.SOLAR -> ClockStyle(
                    FontId.SERIF, 400, color = P_FG, background = Background.GRADIENT, bgColor = 0xFF231D17, bgColor2 = 0xFF3D3025,
                )
                FaceId.PHOTOS_TIME -> ClockStyle(FontId.SERIF, 400, color = 0xFFFFFFFF)
                else -> ClockStyle(FontId.SERIF, 400, color = P_FG)
            }
        },
        ramp = Ramp.IPHONE,
        minText = 0.038f,
        surface = Surface.RULE,
        radius = 0f,
        colorIcons = false,
        artTint = false,
        family = Family.PAPER,
        labelIcons = false,
        labelTracking = 0.08f,
        tokens = ThemeTokens.PAPER,
    )

    /** C 무드등 (mockup r5 C): Nunito heavy numerals, warm plates washed per function, soft pastel accents. */
    val MOODLIGHT = StandbyTheme(
        id = Theme.MOODLIGHT,
        palette = Palette.LAMP,
        words = { Fonts.ui(it) },
        wordsCap = 0.707f,
        numerals = Fonts.nunitoHeavy,
        numeralsCap = 0.705f,
        time = Fonts.nunitoHeavy,
        timeCap = 0.705f,
        clockFamily = { f, w -> Fonts.clock(f, w) },
        clockDefaults = { face ->
            when (face) {
                FaceId.DIGITAL, FaceId.DESK -> ClockStyle(
                    FontId.ROUNDED_HEAVY, 900, color = 0xFFFFDC8F, color2 = 0xFFFFB27A, effect = Effect.TWO_COLOR,
                )
                FaceId.ANALOG -> ClockStyle(
                    FontId.ROUNDED, 900, color = 0xFFFFB27A, analogNumbers = AnalogNumbers.FOUR, numeralColor = 0xFFFFDC8F,
                    handColor = 0xFFFFF3E6, secondColor = 0xFFFFB27A, weekdayColor = 0xFFFFF3E6, tickColor = 0xFFC2B2A2,
                )
                FaceId.SOLAR -> ClockStyle.SOLAR_SETS[1].let { c ->
                    ClockStyle(
                        FontId.ROUNDED, 800, color = c[2], color2 = c[3], effect = Effect.TWO_COLOR,
                        background = Background.GRADIENT, bgColor = c[0], bgColor2 = c[1],
                    )
                }
                FaceId.WORLD -> ClockStyle(FontId.ROUNDED, 800, color = 0xFFFFB27A)
                FaceId.FLOAT -> ClockStyle(FontId.ROUNDED_HEAVY, 950, color = 0xFFFFB27A)
                FaceId.PHOTOS_TIME -> ClockStyle(FontId.ROUNDED_HEAVY, 900, color = 0xFFFFF3E6)
            }
        },
        ramp = Ramp.IPHONE,
        minText = 0.038f,
        surface = Surface.PLATE,
        radius = 0.16f,
        colorIcons = true,
        artTint = true,
        family = Family.LAMP,
        tint = { type ->
            val p = Palette.LAMP
            when (type) {
                CardType.CLOCK, CardType.ANALOG, CardType.DDAY -> p.orange
                CardType.CALENDAR, CardType.AGENDA -> p.red
                CardType.WEATHER, CardType.SLEEP -> p.blue
                CardType.TIMER -> p.green
                CardType.USAGE -> p.lilac
                CardType.MUSIC -> Color(0xFF9B6BD8)
                // notification cards put a plate under each notification instead of one under the slot
                CardType.VIDEO, CardType.MEMORIES, CardType.WIDGET, CardType.NOTIF_STACK, CardType.NOTIF_LIST -> null
            }
        },
        tokens = ThemeTokens.MOODLIGHT,
    )

    private fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL

    /**
     * Clock defaults of the seven r9 themes: digits/second hand = accent, hands/numerals = ink, ticks = ink2 on the page;
     * clocks sit on the page (Background.BLACK = page, D4b). Digits are [FontId.THEME] (D4b); SOLAR keeps its gradients.
     */
    private fun r9Defaults(tk: ThemeTokens): (FaceId) -> ClockStyle = { face ->
        val font = FontId.THEME; val weight = tk.digitWeight
        val ink = tk.ink.long(); val acc = tk.accent.long()
        val tick = tk.ink.copy(alpha = tk.ink2).compositeOver(tk.bg.base).long()
        when (face) {
            FaceId.ANALOG -> ClockStyle(
                font, weight, color = acc, numeralColor = ink, handColor = ink,
                secondColor = acc, weekdayColor = tk.accentText.long(), tickColor = tick,
            )
            FaceId.SOLAR -> ClockStyle.default(face).copy(font = font, weight = weight)
            FaceId.PHOTOS_TIME -> ClockStyle(font, weight, color = 0xFFFFFFFF)
            else -> ClockStyle(font, weight, color = acc, litDotColor = tick)
        }
    }

    /** r9 themes: every non-token field copied from [IPHONE] (those fields go in Task 22). */
    private fun r9(id: Theme, tk: ThemeTokens) = StandbyTheme(
        id = id, palette = IPHONE.palette, words = IPHONE.words, wordsCap = IPHONE.wordsCap, numerals = IPHONE.numerals,
        numeralsCap = IPHONE.numeralsCap, time = IPHONE.time, timeCap = IPHONE.timeCap, clockFamily = IPHONE.clockFamily,
        clockDefaults = r9Defaults(tk), ramp = IPHONE.ramp, minText = IPHONE.minText, surface = IPHONE.surface,
        radius = IPHONE.radius, colorIcons = IPHONE.colorIcons, artTint = IPHONE.artTint, tokens = tk,
    )

    val CANDLE = r9(Theme.CANDLE, ThemeTokens.CANDLE)
    val FLIP = r9(Theme.FLIP, ThemeTokens.FLIP)
    val SKY = r9(Theme.SKY, ThemeTokens.SKY)
    val DAYPAPER = r9(Theme.DAYPAPER, ThemeTokens.DAYPAPER)
    val RADIO = r9(Theme.RADIO, ThemeTokens.RADIO)
    val NEON = r9(Theme.NEON, ThemeTokens.NEON)
    val TERMINAL = r9(Theme.TERMINAL, ThemeTokens.TERMINAL)

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
fun AppConfig.clockStyle(face: FaceId): ClockStyle = styles[face] ?: Themes.of(theme).clockDefaults(face)

/**
 * The one way to change theme (r9 D4: settings, the pull-down panel and previews all call it). Edited clock styles keep
 * their font, size and effect but take the new theme's colours and background; an edit left equal to the new default
 * is dropped, so it follows the theme again.
 */
fun AppConfig.withTheme(th: Theme): AppConfig {
    if (th == theme) return this
    val t = Themes.of(th)
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

/** Provide [theme] to everything below (StandbyRoot, BoardView, CardView and settings previews call it). */
@Composable
fun ProvideTheme(theme: Theme, content: @Composable () -> Unit) {
    val t = Themes.of(theme)
    if (LocalTheme.current === t) content() else CompositionLocalProvider(LocalTheme provides t, content = content)
}
