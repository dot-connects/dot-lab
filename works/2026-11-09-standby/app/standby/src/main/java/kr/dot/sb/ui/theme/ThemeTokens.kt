package kr.dot.sb.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.FontId

/*
 * r9 theme tokens (plan-r9 "Token values"): everything a card needs to draw itself in a theme. One layout per card
 * reads these; nothing branches on the theme name. Contrast floors are enforced by TokenContrastTest.
 */

/** How a plate sits on the page: filled, hairline rule, frosted glass, or outline. */
enum class PlateStyle { FILL, RULE, GLASS, OUTLINE }

/** How hero digits are treated beyond font and colour. */
enum class DigitStyle { PLAIN, ITALIC, FLIP, GLOW }

/**
 * Page background: one stop = solid, more = vertical gradient (SKY: time of day); optional radial glow, centre [glowAt]
 * and radii [glowSize] as fractions of the page, fading to clear at 70 % (CSS `radial-gradient(… at …, c, transparent 70%)`).
 */
@Immutable
data class BgSpec(
    val stops: List<Color>,
    val glow: Color? = null,
    val glowAt: Offset = Offset(0.5f, 0.5f),
    val glowSize: Offset = Offset(0.6f, 0.55f),
) {
    val base: Color get() = stops.first()
}

/** Function colours (weekday, sun, usage rings …). Derived from one base set per theme, never hand-set (D11). */
@Immutable
data class Roles(
    val red: Color, val orange: Color, val yellow: Color, val green: Color, val blue: Color,
    val amber: Color, val snooze: Color, val music: Color, val lilac: Color,
) {
    val all: List<Color> get() = listOf(red, orange, yellow, green, blue, amber, snooze, music, lilac)

    companion object {
        /** iOS system colours (dark appearance) + the app's amber/snooze/music. */
        val IOS = Roles(
            red = Color(0xFFFF453A), orange = Color(0xFFFF9F0A), yellow = Color(0xFFFFD60A), green = Color(0xFF30D158),
            blue = Color(0xFF0A84FF), amber = Color(0xFFF5A524), snooze = Color(0xFFF26B2A), music = Color(0xFFFF5B57),
            lilac = Color(0xFFBF5AF2),
        )

        /** Every role in one colour pair: warm/alert roles take [accent], the rest [ink] (NEON, TERMINAL, night). */
        fun mono(accent: Color, ink: Color) = Roles(accent, accent, ink, ink, ink, accent, accent, accent, ink)

        /** Move each base colour's lightness away from the page (up on dark, down on light) until it reaches 3:1 on every surface. */
        fun derive(base: Roles, surfaces: List<Color>, light: Boolean): Roles {
            fun fit(c: Color): Color {
                val (h, s, l0) = Hsl.toHsl(c)
                var l = l0
                var out = c
                while (surfaces.minOf { Contrast.ratio(out, it) } < 3f && l in 0f..1f) {
                    l += if (light) -0.01f else 0.01f
                    out = Color.hsl(h, s, l.coerceIn(0f, 1f))
                }
                return out
            }
            return Roles(
                fit(base.red), fit(base.orange), fit(base.yellow), fit(base.green), fit(base.blue),
                fit(base.amber), fit(base.snooze), fit(base.music), fit(base.lilac),
            )
        }
    }
}

object Contrast {
    /** WCAG contrast ratio of two opaque colours. */
    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** [c] if it reaches 3:1 on every colour in [against] (translucent [c] composited first), else [fallback]. */
    fun safe(c: Color, against: List<Color>, fallback: Color): Color =
        if (against.all { ratio(c.compositeOver(it), it) >= 3f }) c else fallback
}

@Immutable
data class ThemeTokens(
    val bg: BgSpec,
    val ink: Color,
    /** Secondary / tertiary text as alpha of [ink]. */
    val ink2: Float,
    val ink3: Float,
    /** Accent as a shape (rings, dots, knobs). */
    val accent: Color,
    /** Accent used for text (D12): darker/lighter than [accent] where the shape colour is too weak for 4.5:1. */
    val accentText: Color = accent,
    val plate: Color,
    val plate2: Color,
    val plateStyle: PlateStyle,
    /** Corner radius as a fraction of the element's short side, floored at [radiusMin]. */
    val radiusRatio: Float,
    val radiusMin: Dp,
    /** Hero digit font at a weight, its weight, treatment and cap height / em. */
    val digitFont: (Int) -> FontFamily,
    val digitWeight: Int,
    val digitStyle: DigitStyle,
    val digitCap: Float,
    /** Light page (DAYPAPER, RADIO): night steps bg and plates to black instead of lerping. */
    val light: Boolean,
    /** Notification/app icons drawn in one ink (D9). */
    val monoIcons: Boolean,
    val labelIcons: Boolean,
    /** Label letter spacing in em. */
    val tracking: Float,
    val roles: Roles,
) {
    /** Every opaque colour text can sit on: each bg stop, the glow over it, and both plates composited over those. */
    val surfaces: List<Color>
        get() {
            val pages = bg.stops + (bg.glow?.let { g -> bg.stops.map { g.compositeOver(it) } } ?: emptyList())
            return pages + pages.map { plate.compositeOver(it) } + pages.map { plate2.compositeOver(it) }
        }

    /** The candle night of this theme (spec §3): candle colours, theme shapes and font, effects off. */
    fun night(): ThemeTokens = copy(
        bg = NIGHT_BG, ink = NIGHT_INK, ink2 = 0.58f, ink3 = 0.30f, accent = NIGHT_INK, accentText = NIGHT_INK,
        plate = NIGHT_INK.copy(alpha = 0x0F / 255f), plate2 = NIGHT_INK.copy(alpha = 0x1A / 255f),
        plateStyle = PlateStyle.FILL, digitWeight = minOf(digitWeight, 400), digitStyle = DigitStyle.PLAIN,
        roles = Roles.mono(NIGHT_INK, NIGHT_INK),
    )

    companion object {
        val NIGHT_INK = Color(0xFFB9A48C)
        val NIGHT_BG = BgSpec(listOf(Color.Black), glow = rgba(0xFFAA5A14), glowAt = Offset(0.5f, 1.15f))

        /** Colour from CSS-order #RRGGBBAA (the token table's notation). */
        fun rgba(v: Long): Color = Color(((v and 0xFF) shl 24) or (v ushr 8))

        private fun make(
            bg: BgSpec, ink: Long, ink2: Float, ink3: Float, accent: Long, plate: Color, plate2: Color, style: PlateStyle,
            radius: Float, radiusMin: Int, font: (Int) -> FontFamily, weight: Int, digit: DigitStyle, cap: Float,
            light: Boolean = false, mono: Boolean = false, labelIcons: Boolean = false, tracking: Float = 0f,
            accentText: Long = accent, monoRoles: Boolean = mono,
        ): ThemeTokens {
            val t = ThemeTokens(
                bg, Color(ink), ink2, ink3, Color(accent), Color(accentText), plate, plate2, style, radius, radiusMin.dp,
                font, weight, digit, cap, light, mono, labelIcons, tracking, Roles.IOS,
            )
            val roles = if (monoRoles) Roles.mono(t.accent, t.ink) else Roles.derive(Roles.IOS, t.surfaces, light)
            return t.copy(roles = roles)
        }

        private val sofia: (Int) -> FontFamily = { Fonts.clock(FontId.CONDENSED, it) }
        private val pretendard: (Int) -> FontFamily = { Fonts.ui(it) }
        private val CLEAR = Color.Transparent

        val IPHONE = make(
            BgSpec(listOf(Color.Black)), 0xFFF6EFE6, .62f, .35f, 0xFFFFAD5C, Color(0xFF1C1A18), Color(0xFF2A2724),
            PlateStyle.FILL, .075f, 12, sofia, 600, DigitStyle.PLAIN, 0.655f, labelIcons = true,
        )
        val PAPER = make(
            BgSpec(listOf(Color(0xFF0E0D0B))), 0xFFECE4D4, .55f, .30f, 0xFFD8B46A, CLEAR, Color(0xFF1A1814),
            PlateStyle.RULE, 0f, 0, { Fonts.instrumentItalic }, 400, DigitStyle.ITALIC, 0.72f, mono = true, monoRoles = false,
            tracking = .04f,
        )
        val MOODLIGHT = make(
            BgSpec(listOf(Color(0xFF14100C)), glow = rgba(0xFFAA5A2E), glowAt = Offset(.3f, .4f), glowSize = Offset(.7f, .6f)), 0xFFFFF2E2, .60f, .32f,
            0xFFFFB36B, Color(0xFF2B2119), Color(0xFF3A2C21), PlateStyle.FILL, .09f, 16, pretendard, 300, DigitStyle.PLAIN,
            0.707f, labelIcons = true,
        )
        val CANDLE = make(
            BgSpec(listOf(Color(0xFF0C0805)), glow = rgba(0xFFA05047), glowAt = Offset(.5f, 1.15f)), 0xFFEAD9C2, .55f, .30f,
            0xFFE3A35B, rgba(0xFFCD960F), rgba(0xFFCD961A), PlateStyle.FILL, .085f, 16, pretendard, 200, DigitStyle.PLAIN,
            0.707f, labelIcons = true, accentText = 0xFFE6AD6C,
        )
        val FLIP = make(
            BgSpec(listOf(Color(0xFF0C0C0C))), 0xFFF2F2F2, .55f, .30f, 0xFFFF9F0A, Color(0xFF1F1F1F), Color(0xFF2A2A2A),
            PlateStyle.FILL, .05f, 10, sofia, 700, DigitStyle.FLIP, 0.655f,
        )
        /** Dusk stops darkened from #3A3F6B/#7A4148 so accent text passes 4.5:1 on glass at every stop. */
        val SKY = make(
            BgSpec(listOf(Color(0xFF1B2A4A), Color(0xFF31355A), Color(0xFF552D32))), 0xFFFFFFFF, .70f, .40f, 0xFFFFD29A,
            rgba(0xFFFFFF24), rgba(0xFFFFFF33), PlateStyle.GLASS, .075f, 12, pretendard, 200, DigitStyle.PLAIN, 0.707f,
            labelIcons = true,
        )
        val DAYPAPER = make(
            BgSpec(listOf(Color(0xFFF3EEE4))), 0xFF1D1A16, .60f, .35f, 0xFFB23A0B, Color(0xFFE8E1D3), Color(0xFFDDD4C3),
            PlateStyle.FILL, .055f, 10, { Fonts.clock(FontId.SERIF, it) }, 600, DigitStyle.PLAIN, 0.67f, light = true,
            accentText = 0xFFA6360A,
        )
        /** Accent #E8641C is the operator's shape colour (2026-11-11, below 3:1 on the page by choice); text uses #9C3D0D. */
        val RADIO = make(
            BgSpec(listOf(Color(0xFFD9D3C7))), 0xFF2A2723, .62f, .38f, 0xFFE8641C, Color(0xFFEBE6DC), Color(0xFFE0DACE),
            PlateStyle.FILL, .065f, 12, { Fonts.clock(FontId.BOLD, it) }, 500, DigitStyle.PLAIN, 0.7275f, light = true,
            accentText = 0xFF9C3D0D,
        )
        val NEON = make(
            BgSpec(listOf(Color.Black)), 0xFF7DF9FF, .55f, .30f, 0xFFFF4FD8, CLEAR, rgba(0x7DF9FF14),
            PlateStyle.OUTLINE, .07f, 12, sofia, 400, DigitStyle.GLOW, 0.655f, mono = true,
        )
        val TERMINAL = make(
            BgSpec(listOf(Color(0xFF050805))), 0xFF59FF7A, .55f, .30f, 0xFFFFB000, CLEAR, rgba(0x59FF7A12),
            PlateStyle.OUTLINE, .012f, 4, { Fonts.plexMono }, 500, DigitStyle.PLAIN, 0.698f, mono = true, tracking = .02f,
        )
    }
}
