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
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.ThemeEdit
import java.time.LocalTime

/**
 * 시간 따라 흐름 (plan-r11 A4): the colour at [t] over the day, linear in sRGB. 3 stops: 06:00 → 13:00 → 21:00;
 * 2 stops: 06:00 → 21:00. Null 21:00–05:59 (night has no flow: the edit's own colour applies).
 */
fun flowColor(stops: List<Long>, t: LocalTime): Color? {
    if (stops.size !in 2..3) return null
    val m = t.toSecondOfDay() / 60f
    if (m < 6 * 60f || m >= 21 * 60f) return null
    val marks = if (stops.size == 3) listOf(6 * 60f, 13 * 60f, 21 * 60f) else listOf(6 * 60f, 21 * 60f)
    val i = (1 until marks.size).first { m < marks[it] || it == marks.size - 1 }
    val f = ((m - marks[i - 1]) / (marks[i] - marks[i - 1])).coerceIn(0f, 1f)
    val a = Color(stops[i - 1]); val b = Color(stops[i])
    fun ch(x: Float, y: Float) = x + (y - x) * f
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue), ch(a.alpha, b.alpha))
}

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
    /** r11 둘 (TWIN): a second glow of the same colour and size here (the mirror of [glowAt]). */
    val glow2At: Offset? = null,
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
            fun fit(c: Color) = Contrast.fit(c, surfaces, light)
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

    /** [c] with its lightness moved away from the page (down when [light]) until it reaches [min] on every surface. */
    fun fit(c: Color, surfaces: List<Color>, light: Boolean, min: Float = 3f): Color {
        val (h, s, l0) = Hsl.toHsl(c)
        var l = l0
        var out = c
        while (surfaces.minOf { ratio(out, it) } < min && l in 0f..1f) {
            l += if (light) -0.01f else 0.01f
            out = Color.hsl(h, s, l.coerceIn(0f, 1f))
        }
        return out
    }

    /** r11 editor warnings (the settings screen words them: 글자가 흐려요 · 작은 글자가 흐려요 · 강조 색이 흐려요). */
    enum class Warning { INK, INK2, ACCENT }

    /**
     * r11 A3: what [edited] made harder to read than the theme's own default [base]. Floors are relative (min of the
     * usual floor and the base's own ratio), so a theme's intentional look (RADIO's accent) never warns. Manual picks
     * below them are allowed; the editor only says so.
     */
    fun warnings(edited: ThemeTokens, base: ThemeTokens): List<Warning> {
        fun ink(t: ThemeTokens) = t.surfaces.minOf { ratio(t.ink, it) }
        fun ink2(t: ThemeTokens) = t.surfaces.minOf { ratio(t.ink.copy(alpha = t.ink2).compositeOver(it), it) }
        fun accent(t: ThemeTokens) = t.pages.minOf { ratio(t.accent, t.plate.compositeOver(it)) }
        // float noise on an unchanged token must not warn
        fun low(v: Float, floor: Float, b: Float) = v < minOf(floor, b) - 0.01f
        return listOfNotNull(
            Warning.INK.takeIf { low(ink(edited), 4.5f, ink(base)) },
            Warning.INK2.takeIf { low(ink2(edited), 3f, ink2(base)) },
            Warning.ACCENT.takeIf { low(accent(edited), 3f, accent(base)) },
        )
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
    /** r11: the theme draws every role in its accent/ink pair ([Roles.mono]); set by `make`, read by [with]. */
    val monoRoles: Boolean = false,
    /** r11 NEON 빛 번짐 세기: scales the OUTLINE halo alphas (1 = the theme's). */
    val halo: Float = 1f,
    /** r11 PAPER 구분선 색: the RULE line colour (null = ink at 25 %). Not a surface. */
    val rule: Color? = null,
) {
    /** The page colours: each bg stop and the glow over it. */
    val pages: List<Color> get() = bg.stops + (bg.glow?.let { g -> bg.stops.map { g.compositeOver(it) } } ?: emptyList())

    /** Every opaque colour text can sit on: each bg stop, the glow over it, and both plates composited over those. */
    val surfaces: List<Color>
        get() {
            val pages = this.pages
            return pages + pages.map { plate.compositeOver(it) } + pages.map { plate2.compositeOver(it) }
        }

    /** The candle night of this theme (spec §3): candle colours, theme shapes and font, effects off. */
    fun night(): ThemeTokens = copy(
        bg = NIGHT_BG, ink = NIGHT_INK, ink2 = 0.58f, ink3 = 0.30f, accent = NIGHT_INK, accentText = NIGHT_INK,
        plate = NIGHT_INK.copy(alpha = 0x0F / 255f), plate2 = NIGHT_INK.copy(alpha = 0x1A / 255f),
        plateStyle = PlateStyle.FILL, digitWeight = minOf(digitWeight, 400), digitStyle = DigitStyle.PLAIN,
        roles = Roles.mono(NIGHT_INK, NIGHT_INK),
    )

    /**
     * A light theme on a dark board background (Task 20): the page colour becomes the ink, plates turn to white glass,
     * accents and roles are lifted until they read on the dark page. Fonts and shapes stay the theme's.
     */
    fun onDark(): ThemeTokens {
        val t = copy(bg = BgSpec(listOf(DARK_PAGE)), ink = bg.base, plate = rgba(0xFFFFFF12), plate2 = rgba(0xFFFFFF1F), light = false)
        val s = t.surfaces
        return t.copy(
            accent = Contrast.fit(accent, s, false), accentText = Contrast.fit(accentText, s, false, 4.5f),
            roles = Roles.derive(Roles.IOS, s, false),
        )
    }

    /**
     * r11: these tokens with the user's [e] (plan-r11 A2, the one place an edit is applied), at [at] for 시간 따라 흐름
     * (null = no flow). Text colours are re-derived on the new surfaces: accent text by [Contrast.fit] (a hand-set
     * accent text that still reads is kept), roles derived or mono as the theme does. [light] never changes; [fitLight]
     * is only the direction text moves (a card's own plate, A6).
     */
    fun with(e: ThemeEdit, at: LocalTime? = null, fitLight: Boolean = light): ThemeTokens {
        val strength = ((e.glowStrength ?: 0.5f) * 2f)
        val flowC = e.flow?.let { f -> at?.let { flowColor(f, it) } }
        var b = bg
        e.bg?.let { s -> b = b.copy(stops = s.map { Color(it) }) }
        val glow = b.glow
        if (glow != null) {
            // 무드등 / 촛불: the flow and the picked colour replace the glow's colour, its alpha stays the theme's × 세기
            val c = flowC ?: e.glow?.let { Color(it) } ?: glow
            b = b.copy(glow = c.copy(alpha = (glow.alpha * strength).coerceIn(0f, 1f)))
        } else if (flowC != null) {
            b = b.copy(stops = listOf(flowC) + b.stops.drop(1))   // 하늘: the top of the sky
        }
        e.glowAt?.let { (x, y) -> b = b.copy(glowAt = Offset(x, y)) }
        when (e.glowShape) {
            GlowShape.SUNSET -> b = b.copy(glowAt = Offset(0.5f, 1.15f), glowSize = Offset(1.1f, 0.8f), glow2At = null)
            GlowShape.TWIN -> b = b.copy(glow2At = Offset(1f - b.glowAt.x, 1f - b.glowAt.y))
            GlowShape.ROUND, null -> {}
        }
        val inkC = e.ink?.let { Color(it) } ?: ink
        val acc = e.accent?.let { Color(it) } ?: accent
        var p = plate
        var p2 = plate2
        e.plate?.let { v ->
            val c = Color(v)
            p = c.copy(alpha = plate.alpha)
            p2 = Hsl.shift(c, if (light) -0.06f else 0.06f).copy(alpha = plate2.alpha)
        }
        // NEON / TERMINAL: the progress track is ink-tinted, so it follows an edited ink
        if (plateStyle == PlateStyle.OUTLINE && e.ink != null) p2 = inkC.copy(alpha = plate2.alpha)
        e.plateAlpha?.let { a -> p = p.copy(alpha = a); p2 = p2.copy(alpha = a) }
        if (e.plateStyle == PlateStyle.GLASS) { p = p.copy(alpha = minOf(p.alpha, GLASS_MAX)); p2 = p2.copy(alpha = minOf(p2.alpha, GLASS_MAX)) }
        val t = copy(
            bg = b, ink = inkC, accent = acc, plate = p, plate2 = p2, plateStyle = e.plateStyle ?: plateStyle,
            radiusRatio = e.radius ?: radiusRatio, halo = if (e.glowStrength != null) strength else halo,
            rule = e.rule?.let { Color(it) } ?: rule,
        )
        val s = t.surfaces
        // an untouched accent keeps its text colour as the theme set it (RADIO's hand-set one included)
        val accText = if (e.accent == null) accentText else Contrast.fit(acc, s, fitLight, 4.5f)
        val roles = if (monoRoles) Roles.mono(acc, inkC) else Roles.derive(Roles.IOS, s, fitLight)
        return t.copy(accentText = accText, roles = roles)
    }

    /** r11 카드 색: one card's tokens on its own plate, and that plate composited over the page (what its text sits on). */
    data class CardPlate(val tokens: ThemeTokens, val over: Color)

    /**
     * r11 A6 (plan round 4/5): these (drawn) tokens on a card whose plate is [c], at the theme's plate alpha (GLASS:
     * [GLASS_MAX]). Ink, accent text and roles are fitted on that one surface, in the direction that reads better (dark
     * text on a plate lighter than the black/white crossover); ink2/ink3 are raised where the secondary text would
     * fall under 3:1 on it.
     */
    fun cardPlate(c: Long): CardPlate {
        val a = if (plateStyle == PlateStyle.GLASS) GLASS_MAX else plate.alpha
        val p = Color(c).copy(alpha = a)
        val over = p.compositeOver(bg.base)
        val s = listOf(over)
        // dark text when black beats white on this plate (luminance > ≈ 0.18)
        val lightPlate = Contrast.ratio(Color(0f, 0f, 0f), over) > Contrast.ratio(Color(1f, 1f, 1f), over)
        val inkC = Contrast.fit(ink, s, lightPlate, 4.5f)
        fun second(al: Float) = Contrast.ratio(inkC.copy(alpha = al).compositeOver(over), over)
        var i2 = ink2
        while (second(i2) < 3f && i2 < 1f) i2 = minOf(1f, i2 + 0.02f)
        val p2 = Hsl.shift(Color(c), if (lightPlate) -0.06f else 0.06f).copy(alpha = if (plateStyle == PlateStyle.GLASS) GLASS_MAX else plate2.alpha)
        val acc = Contrast.fit(accentText, s, lightPlate, 4.5f)
        val roles = if (monoRoles) Roles.mono(accent, inkC) else Roles.derive(Roles.IOS, s, lightPlate)
        return CardPlate(copy(ink = inkC, ink2 = i2, ink3 = (ink3 + (i2 - ink2)).coerceAtMost(1f), plate = p, plate2 = p2, accentText = acc, roles = roles), over)
    }

    companion object {
        /** 카드 모양 유리 caps both plates' alpha here (plan-r11 round 3), so the page shows through. */
        const val GLASS_MAX = 0.55f

        /** The page [onDark] assumes: between the dark board presets (graphite, midnight, amber …). */
        val DARK_PAGE = Color(0xFF111113)
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
            return t.copy(roles = roles, monoRoles = monoRoles)
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
