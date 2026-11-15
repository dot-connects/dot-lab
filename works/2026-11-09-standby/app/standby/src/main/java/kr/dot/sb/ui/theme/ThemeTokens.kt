package kr.dot.sb.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.BgKind
import kr.dot.sb.model.Border
import kr.dot.sb.model.CardFont
import kr.dot.sb.model.FontId
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.TextWeight
import kr.dot.sb.model.Texture
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
enum class DigitStyle { PLAIN, GLOW }

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
    /** r11 둘 (TWIN): a second glow here (the mirror of [glowAt]); same colour and size as the first when [glow2] is null. */
    val glow2At: Offset? = null,
    /** r13: a second glow colour, drawn after the first at [glow2At] (else [glowAt]) and [glow2Size] (else [glowSize]). */
    val glow2: Color? = null,
    val glow2Size: Offset? = null,
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
    /** 구분선 색: the RULE line colour (null = ink at 25 %). Not a surface. */
    val rule: Color? = null,
    // r13 (plan-r13 A3): defaults reproduce r12 exactly; every effect below is faded by night in the drawing code
    /** Page texture (drawn in ThemeBackground) and plate texture (themedSurface), both at [textureAmt]. */
    val bgTexture: Texture = Texture.NONE,
    val cardTexture: Texture = Texture.NONE,
    val textureAmt: Float = 0.5f,
    /** Card words font (Txt default, overlays, photo captions); clock faces keep Pretendard. */
    val cardFont: CardFont = CardFont.GOTHIC,
    /** Text weight: an offset on Pretendard only (single-weight card fonts ignore it). */
    val textWeight: TextWeight = TextWeight.NORMAL,
    /** Border around FILL/GLASS/RULE plates; [borderColor] null = ink at 35 % (also the OUTLINE line colour when set). */
    val border: Border = Border.NONE,
    val borderColor: Color? = null,
    /** Text glow 0..1; null = the theme's (NEON's own halo, no text shadow). */
    val textGlow: Float? = null,
    // r13 Part D (theme lineup): defaults = r12
    /** The role colours [Roles.derive] starts from (pinned sun/red/green per theme, else iOS). */
    val roleBase: Roles = Roles.IOS,
    /** Plate texture amount when it differs from the page's (RADIO); null = [textureAmt]. An edit's 질감 정도 sets both. */
    val cardTextureAmt: Float? = null,
    /** Hero digit letter spacing in em (RADIO -0.045), theme font only. */
    val digitTracking: Float = 0f,
    /** The theme's own card text shadow 0..1 when [textGlow] is unset (NEON, TERMINAL: weak). */
    val cardGlow: Float? = null,
    /** Latin/digit runs of card text in this family (TERMINAL: IBM Plex Mono); null = the rounded widget numerals. */
    val latinFont: FontFamily? = null,
    /** Label rows draw a `>` prompt instead of their icon (TERMINAL). */
    val prompt: Boolean = false,
) {
    /** The page colours: each bg stop and the glow(s) over it. */
    val pages: List<Color> get() = bg.stops + listOfNotNull(bg.glow, bg.glow2).flatMap { g -> bg.stops.map { g.compositeOver(it) } }

    /**
     * OUTLINE halo strength (multiplies the halo alphas; 0 = none): a set [textGlow] draws it at 2 x g whatever the
     * digit style; else the theme's own halo on glowing digits (NEON). Other plate styles never draw it.
     */
    val haloAmt: Float
        get() = if (plateStyle != PlateStyle.OUTLINE) 0f else textGlow?.let { it * 2f } ?: if (digitStyle == DigitStyle.GLOW) halo else 0f

    /**
     * Card text shadow strength (plan-r13 A3): a set, non-zero [textGlow] on any plate style but OUTLINE; unset = the
     * theme's own [cardGlow] (Part D); else null.
     */
    val textShadow: Float? get() = if (textGlow == null) cardGlow else textGlow.takeIf { it > 0f && plateStyle != PlateStyle.OUTLINE }

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
        val inkC = e.ink?.let { Color(it) } ?: ink
        val acc = e.accent?.let { Color(it) } ?: accent
        var b = bg
        // r13 background kind (plan-r13 A3); null = the theme's own kind with the picked stops
        val base0 = e.bg?.firstOrNull()?.let { Color(it) } ?: bg.base
        when (e.bgKind) {
            BgKind.SOLID -> b = b.copy(stops = listOf(base0), glow = null, glow2 = null, glow2At = null)
            BgKind.GRADIENT -> {
                val d = if (light) -0.05f else 0.07f
                val stops = e.bg?.takeIf { it.size == 3 }?.map { Color(it) } ?: listOf(base0, Hsl.shift(base0, d), Hsl.shift(base0, 2 * d))
                b = b.copy(stops = stops, glow = null, glow2 = null, glow2At = null)
            }
            // the strength rule below scales the alpha: (theme glow alpha ?: 0.35) x 2 x (glowStrength ?: 0.5)
            BgKind.GLOW -> b = b.copy(stops = listOf(base0), glow = b.glow ?: acc.copy(alpha = 0.35f))
            null -> e.bg?.let { s -> b = b.copy(stops = s.map { Color(it) }) }
        }
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
            GlowShape.SUNSET -> b = b.copy(glowAt = Offset(0.5f, 1.15f), glowSize = Offset(1.1f, 0.8f), glow2At = null, glow2 = null, glow2Size = null)
            // TWIN: the mirror in the first colour
            GlowShape.TWIN -> b = b.copy(glow2At = Offset(1f - b.glowAt.x, 1f - b.glowAt.y), glow2 = null, glow2Size = null)
            GlowShape.ROUND, null -> {}
        }
        var p = plate
        var p2 = plate2
        val shift = if (light) -0.06f else 0.06f
        // r13: FILL/GLASS on a theme whose own plate is clear (RULE/OUTLINE: DAYPAPER, NEON, TERMINAL) gets a visible plate
        val clearToFill = (plateStyle == PlateStyle.RULE || plateStyle == PlateStyle.OUTLINE) &&
            (e.plateStyle == PlateStyle.FILL || e.plateStyle == PlateStyle.GLASS)
        if (clearToFill) {
            val c = e.plate?.let { Color(it) } ?: Hsl.shift(b.base, shift)
            val a = if (e.plateStyle == PlateStyle.GLASS) 0.45f else 0.85f
            p = c.copy(alpha = a)
            p2 = Hsl.shift(c, shift).copy(alpha = a)
        } else e.plate?.let { v ->
            val c = Color(v)
            p = c.copy(alpha = plate.alpha)
            p2 = Hsl.shift(c, shift).copy(alpha = plate2.alpha)
        }
        // NEON / TERMINAL: the progress track is ink-tinted, so it follows an edited ink
        if (plateStyle == PlateStyle.OUTLINE && e.ink != null && !clearToFill) p2 = inkC.copy(alpha = plate2.alpha)
        e.plateAlpha?.let { a -> p = p.copy(alpha = a); p2 = p2.copy(alpha = a) }
        if (e.plateStyle == PlateStyle.GLASS) { p = p.copy(alpha = minOf(p.alpha, GLASS_MAX)); p2 = p2.copy(alpha = minOf(p2.alpha, GLASS_MAX)) }
        val t = copy(
            bg = b, ink = inkC, accent = acc, plate = p, plate2 = p2, plateStyle = e.plateStyle ?: plateStyle,
            // glowStrength is the page glow only (v11 moved NEON/TERMINAL's halo to textGlow, drawn by [haloAmt])
            radiusRatio = e.radius ?: radiusRatio,
            rule = e.rule?.let { Color(it) } ?: rule,
            bgTexture = e.bgTexture ?: bgTexture, cardTexture = e.cardTexture ?: cardTexture, textureAmt = e.textureAmt ?: textureAmt,
            cardTextureAmt = if (e.textureAmt != null) null else cardTextureAmt,
            cardFont = e.textFont ?: cardFont, textWeight = e.textWeight ?: textWeight,
            border = e.border ?: border, borderColor = e.borderColor?.let { Color(it) } ?: borderColor,
            textGlow = e.textGlow ?: textGlow,
        )
        val s = t.surfaces
        // an untouched accent keeps its text colour as the theme set it (RADIO's hand-set one included)
        val accText = if (e.accent == null) accentText else Contrast.fit(acc, s, fitLight, 4.5f)
        val roles = if (monoRoles) Roles.mono(acc, inkC) else Roles.derive(roleBase, s, fitLight)
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
        val roles = if (monoRoles) Roles.mono(accent, inkC) else Roles.derive(roleBase, s, lightPlate)
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
            accentText: Long = accent, monoRoles: Boolean = mono, roleBase: Roles = Roles.IOS,
        ): ThemeTokens {
            val t = ThemeTokens(
                bg, Color(ink), ink2, ink3, Color(accent), Color(accentText), plate, plate2, style, radius, radiusMin.dp,
                font, weight, digit, cap, light, mono, labelIcons, tracking, Roles.IOS, roleBase = roleBase,
            )
            val roles = if (monoRoles) Roles.mono(t.accent, t.ink) else Roles.derive(roleBase, t.surfaces, light)
            return t.copy(roles = roles, monoRoles = monoRoles)
        }

        /** iOS roles with the theme's pinned sun (yellow), red and green (plan-r13 Part D table). */
        private fun pinned(sun: Long, red: Long, green: Long) = Roles.IOS.copy(yellow = Color(sun), red = Color(red), green = Color(green))

        /** A mockup glow at ([x], [y]) of [size]: CSS radii size x 70 % of the width and x 90 % of the height (clear at 70 %). */
        private fun glow(c: Long, alpha: Float, x: Float, y: Float, size: Float, stops: List<Color>) =
            BgSpec(stops, glow = Color(c).copy(alpha = alpha), glowAt = Offset(x, y), glowSize = Offset(size * 0.7f, size * 0.9f))

        private val sofia: (Int) -> FontFamily = { Fonts.clock(FontId.CONDENSED, it) }
        private val CLEAR = Color.Transparent

        val IPHONE = make(
            BgSpec(listOf(Color.Black)), 0xFFF6EFE6, .62f, .35f, 0xFFFFAD5C, Color(0xFF1C1A18), Color(0xFF2A2724),
            PlateStyle.FILL, .075f, 12, sofia, 600, DigitStyle.PLAIN, 0.655f, labelIcons = true,
        )

        /** 종이 (다듬기): paper grain, one hairline rule per card, serif card words. */
        val DAYPAPER = make(
            BgSpec(listOf(Color(0xFFF2EBDD))), 0xFF1F1B16, .78f, .35f, 0xFFA6360A, CLEAR, Color(0xFFDDD4C3),
            PlateStyle.RULE, 0f, 0, { Fonts.clock(FontId.SERIF, it) }, 600, DigitStyle.PLAIN, 0.67f, light = true,
            roleBase = pinned(0xFF8F6200, 0xFFB3261E, 0xFF2F6B45),
        ).copy(bgTexture = Texture.PAPER, textureAmt = .6f, rule = Color(0xFF1F1B16).copy(alpha = .55f), cardFont = CardFont.SERIF)

        /**
         * 라디오 (과감하게): lit aluminium, near-square panels with an engraved line. Shape accent below 3:1 by choice (D12).
         * Accent text #A83C08 darkened to #7C2C06 (HSL L -0.09) so it reads 4.5:1 on the darkest page stop; pinned roles
         * are lifted by [Roles.derive] where they fall under 3:1.
         */
        val RADIO = make(
            BgSpec(listOf(Color(0xFFD8D6D1), Color(0xFFC9C7C1), Color(0xFFB7B4AD))), 0xFF1A1917, .68f, .38f, 0xFFE8590C,
            Color(0xFFEEECE7), Hsl.shift(Color(0xFFEEECE7), -0.06f), PlateStyle.FILL, .035f, 4, { Fonts.clock(FontId.BOLD, it) }, 700,
            DigitStyle.PLAIN, 0.7275f, light = true, accentText = 0xFF7C2C06, roleBase = pinned(0xFFA86F00, 0xFFD2500A, 0xFF2E7D4F),
        ).copy(
            bgTexture = Texture.METAL, textureAmt = .45f, cardTexture = Texture.METAL, cardTextureAmt = .3f,
            border = Border.THIN, borderColor = Color(0xFF1A1917).copy(alpha = .3f), digitTracking = -.045f,
        )

        /** 무드등 (다듬기, 둥근): a big amber light behind the clock, frosted glass cards. */
        val MOODLIGHT = make(
            glow(0xFFFF9A4D, .30f, .3f, .38f, 1f, listOf(Color(0xFF120D0A))), 0xFFFFF3E4, .66f, .32f, 0xFFFFC27A,
            Color(0xFF2A1E16).copy(alpha = .55f), Hsl.shift(Color(0xFF2A1E16), 0.06f).copy(alpha = .55f), PlateStyle.GLASS,
            .17f, 16, { Fonts.clock(FontId.OUTFIT, it) }, 300, DigitStyle.PLAIN, 0.676f, labelIcons = true,
            roleBase = pinned(0xFFFFD27A, 0xFFFF8566, 0xFFC7DB8E),
        ).copy(cardTexture = Texture.FROST, textureAmt = .5f, border = Border.THIN, borderColor = Color(0xFFFFD9B0).copy(alpha = .12f))

        /** 하늘 (다듬기): navy, violet, peach horizon; dark glass cards. Horizon #B3605E darkened to #9E4C4A (L -0.08): white ink and the peach accent read 4.5:1 on it. */
        val SKY = make(
            BgSpec(listOf(Color(0xFF0E1E46), Color(0xFF4B3F86), Color(0xFF9E4C4A))), 0xFFFFFFFF, .76f, .40f, 0xFFFFE1B0,
            Color(0xFF0E1030).copy(alpha = .30f), Hsl.shift(Color(0xFF0E1030), 0.06f).copy(alpha = .30f), PlateStyle.GLASS, .15f, 12,
            { Fonts.clock(FontId.JOST, it) }, 300, DigitStyle.PLAIN, 0.70f, labelIcons = true,
            roleBase = pinned(0xFFFFE1B0, 0xFFFFA08E, 0xFFA8E6C1),
        ).copy(border = Border.THIN, borderColor = Color.White.copy(alpha = .14f))

        /** 네온 (다듬기): violet dark behind cyan tubes; card words glow faintly. */
        val NEON = make(
            glow(0xFF3A1E6E, .55f, .25f, .4f, .9f, listOf(Color(0xFF05040C))), 0xFF7DF9FF, .64f, .30f, 0xFFFF5EDB, CLEAR,
            rgba(0x7DF9FF14), PlateStyle.OUTLINE, .12f, 12, sofia, 400, DigitStyle.GLOW, 0.655f, mono = true,
        ).copy(cardGlow = .3f)

        /** 터미널 (과감하게): phosphor green on scanlines, output rules instead of boxes, `>` prompts, mono Latin. */
        val TERMINAL = make(
            glow(0xFF12452A, .55f, .5f, .5f, 1.25f, listOf(Color(0xFF020A06))), 0xFF7CF7A0, .68f, .30f, 0xFFFFB000, CLEAR,
            rgba(0x7CF7A012), PlateStyle.RULE, 0f, 0, { Fonts.plexMono }, 500, DigitStyle.GLOW, 0.698f, mono = true,
            labelIcons = true, tracking = .02f,
        ).copy(
            bgTexture = Texture.SCANLINE, textureAmt = .7f, rule = Color(0xFF7CF7A0).copy(alpha = .5f), cardGlow = .3f,
            latinFont = Fonts.plexMono, prompt = true,
        )
    }
}
