package kr.dot.sb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kr.dot.sb.model.ClockStyle
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
 * Adding a theme = one more `val` in [Themes] and one branch in [Themes.of]. A new card surface or effect that the
 * fields below cannot express gets a new field here (default = the iPhone behaviour), not a `when (theme)` in a card.
 */

/** How a card sits on the board: bare on black (A), on a rounded plate (C), between hairline rules (B). */
enum class Surface { NONE, PLATE, RULE }

/**
 * Day colours of one theme, by role. Night is always the red ramp: [Ink] maps every role to #FC0000 (bright roles) or
 * #A80000 (dim roles) by the night fraction, in every theme.
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
    }
}

/**
 * Text roles relative to the slot short side `s` (design-system §2.3). [label] is also floored by [StandbyTheme.minText]
 * of the screen (board-wide), so the smallest line never drops below 6 % of the screen.
 */
@Immutable
data class Ramp(val display: Float, val value: Float, val title: Float, val label: Float, val pad: Float) {
    companion object {
        val IPHONE = Ramp(display = 0.56f, value = 0.30f, title = 0.15f, label = 0.095f, pad = 0.07f)
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
)

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
        minText = 0.06f,
        surface = Surface.NONE,
        radius = 0.12f,
        colorIcons = true,
        artTint = true,
    )

    // ponytail: PAPER and MOODLIGHT render as IPHONE until their vals land (next agent adds them here only).
    fun of(t: Theme): StandbyTheme = when (t) {
        Theme.IPHONE, Theme.PAPER, Theme.MOODLIGHT -> IPHONE
    }
}

val LocalTheme = staticCompositionLocalOf { Themes.IPHONE }

/** Provide [theme] to everything below (StandbyRoot, BoardView, CardView and settings previews call it). */
@Composable
fun ProvideTheme(theme: Theme, content: @Composable () -> Unit) {
    val t = Themes.of(theme)
    if (LocalTheme.current === t) content() else CompositionLocalProvider(LocalTheme provides t, content = content)
}
