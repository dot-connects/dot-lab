package kr.dot.sb.ui.clock

import kr.dot.sb.ui.theme.ThemeTokens
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Numerals
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

/* Shared drawing kit for every clock: time strings, typefaces, the digit painter (effects, stencil, rails). */

/** "9" / "41" (12 h, no leading zero) or "09" / "41" (24 h). ASCII digits; numerals are converted when drawn. */
internal fun timeParts(now: LocalDateTime, use24h: Boolean): Pair<String, String> {
    val h = if (use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    return (if (use24h) h.toString().padStart(2, '0') else h.toString()) to now.minute.toString().padStart(2, '0')
}

internal val WEEKDAY_KO = arrayOf("월", "화", "수", "목", "금", "토", "일")

@Composable
internal fun rememberTypeface(family: FontFamily, weight: Int): Typeface {
    val resolver = LocalFontFamilyResolver.current
    return remember(family, weight, resolver) {
        resolver.resolve(family, FontWeight(weight.coerceIn(1, 1000))).value as Typeface
    }
}

@Composable
internal fun rememberClockTypeface(font: FontId, weight: Int): Typeface = rememberTypeface(LocalTheme.current.digits(font, weight), weight)

/** Pretendard paint for words on clock faces (sizes in px, set per use). */
internal fun uiPaint(tf: Typeface, weight: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = tf
    fontVariationSettings = "'wght' $weight"
}

/** Cap height of a Pretendard paint as a fraction of its text size. */
internal fun TextPaint.capRatio(): Float {
    val keep = textSize; textSize = 100f
    val r = Rect(); getTextBounds("H", 0, 1, r)
    textSize = keep
    return -r.top / 100f
}

/** One line, ellipsized at the end to [maxW] (own loop: never clips mid-glyph, same result on device and in tests). */
internal fun TextPaint.fit(text: String, maxW: Float): String {
    if (measureText(text) <= maxW) return text
    var n = text.length
    while (n > 0 && measureText(text, 0, n) + measureText("…") > maxW) n--
    return text.substring(0, n).trimEnd() + "…"
}

/** Colours of one time run after night mapping (N4 + design-system §4.3: every effect off at night, glow too). */
internal class Look(
    val effect: Effect,
    val c1: Int,
    val c2: Int,
    /** Extra soft shadow (Solar contrast floor); 0 = none. */
    val shadow: Int = 0,
) {
    companion object {
        fun of(style: ClockStyle, ink: Ink, base: Color = Color(style.color), base2: Color = Color(style.color2), effects: Boolean = true): Look {
            val night = ink.night >= 0.5f
            val fx = when {
                !effects || night -> Effect.SOLID
                else -> style.effect
            }
            return Look(fx, ink.accent(base).toArgb(), ink.accent(base2).toArgb())
        }

        val RAINBOW = intArrayOf(0xFFFF5A5F.toInt(), 0xFFFFB347.toInt(), 0xFFFFE066.toInt(), 0xFF6FDC86.toInt(), 0xFF4D9BEF.toInt(), 0xFFAA88FE.toInt())
    }
}

/**
 * Digit painter for one clock font at one weight/width/numeral system. Metrics are measured once (at 100 px) and
 * reused for every minute, so autosize never re-measures on a time change (design-system §2.3.1).
 * Hero digits are proportional ([tnum] false: a "1" is narrow, like iPhone); board numerals keep `tnum`.
 */
internal class TimePainter(tf: Typeface, weight: Int, private val font: FontId, widthPct: Int, val numerals: Numerals, tnum: Boolean = false) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        fontVariationSettings = "'wght' $weight"
        // "StandBy 디지털" is Sofia Sans Extra Condensed (r5): already iPhone-narrow, no artificial squeeze
        textScaleX = widthPct / 100f
        textSize = REF
        fontFeatureSettings = if (tnum) "'tnum'" else null
    }
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /** Advance of each digit 0-9 (converted numerals) in em. */
    val digitAdv = FloatArray(10) { paint.measureText(numerals.convert(it.toString())) / REF }
    val colonAdv = paint.measureText(":") / REF
    /** Ink top of the digits above the baseline (positive, em) and overshoot below it (em). */
    val capEm: Float
    val descEm: Float

    init {
        val r = Rect(); var top = 0; var bottom = 0
        for (d in 0..9) {
            val s = numerals.convert(d.toString())
            paint.getTextBounds(s, 0, s.length, r)
            top = min(top, r.top); bottom = max(bottom, r.bottom)
        }
        capEm = -top / REF; descEm = bottom / REF
    }

    private fun maxAdv(range: IntRange) = range.maxOf { digitAdv[it] }

    /**
     * Widest hour string of one hour-digit class (§2.3.1 rev. 2026-11-10), in em: class 1 = "1"-"9" (12 h 1:00-9:59),
     * class 2 = 12 h "10"-"12" or 24 h "00"-"23". The size changes only when the class does (9:59→10:00, 12:59→1:00).
     */
    fun hourEm(digits: Int, use24h: Boolean) = when {
        digits == 1 -> maxAdv(1..9)
        use24h -> maxAdv(0..2) + maxAdv(0..9)
        else -> digitAdv[1] + maxAdv(0..2)
    }
    val minuteEm get() = maxAdv(0..5) + maxAdv(0..9)
    /** The colon's advance in a time run: 30 % tighter than the font's (iPhone-like). */
    val colonRun get() = colonAdv * COLON
    /** Widest "h:mm" of the class in em, with 3 % for kerning slack. */
    fun widestEm(hourDigits: Int, use24h: Boolean) = (hourEm(hourDigits, use24h) + colonRun + minuteEm) * 1.03f
    /** Widest string of arbitrary digits (each digit position takes the widest digit). */
    // non-digits measured at REF: at 1 px Android rounds the colon advance to 0 and StyledTime overflowed its box
    fun widestEm(pattern: String) = pattern.sumOf { if (it in '0'..'9') maxAdv(0..9).toDouble() else measure(it.toString(), REF).toDouble() / REF }.toFloat() * 1.03f

    fun measure(text: String, size: Float): Float { paint.textSize = size; return paint.measureText(numerals.convert(text)) }

    /** Width of "h:mm" drawn by [drawHm] (tight colon). */
    fun measureHm(h: String, m: String, size: Float): Float = measure(h, size) + colonRun * size + measure(m, size)

    /** "h:mm" with the tight colon; one gradient spans the whole run. [colon] = its own look (theme B: accent colon). */
    fun drawHm(c: Canvas, h: String, m: String, x: Float, baseline: Float, size: Float, look: Look, colon: Look = look) {
        val wh = measure(h, size)
        val r = x + measureHm(h, m, size)
        draw(c, h, x, baseline, size, look, x, r)
        draw(c, ":", x + wh - colonAdv * size * (1 - COLON) / 2, baseline, size, colon, x, r)
        draw(c, m, x + wh + colonRun * size, baseline, size, look, x, r)
    }

    /**
     * Draw [text] (ASCII digits, converted here) with its left edge at [x] and baseline at [baseline].
     * Stencil/Rails cuts are drawn here, per font.
     */
    fun draw(c: Canvas, text: String, x: Float, baseline: Float, size: Float, look: Look, gl: Float = x, gr: Float = Float.NaN) {
        val s = numerals.convert(text)
        paint.textSize = size
        val w = paint.measureText(s)
        val top = baseline - capEm * size
        val bottom = baseline + descEm * size
        val stencil = font == FontId.STENCIL
        val rails = font == FontId.RAILS
        val layered = stencil || rails
        if (layered) c.saveLayer(x - size, top - size, x + w + size, bottom + size, null)

        paint.reset(look, gl, top, if (gr.isNaN()) x + w else gr, bottom)
        val glass = look.effect == Effect.GLASS
        if (look.effect == Effect.GLOW) {
            // Two blurs (0.05 em full, 0.22 em at 55 %), then the crisp fill on top.
            paint.setShadowLayer(size * 0.22f, 0f, 0f, withAlpha(look.c1, 0.55f))
            c.drawText(s, x, baseline, paint)
            paint.setShadowLayer(size * 0.05f, 0f, 0f, look.c1)
            c.drawText(s, x, baseline, paint)
            paint.clearShadowLayer()
        }
        if (look.shadow != 0) paint.setShadowLayer(size * 0.04f, 0f, size * 0.01f, look.shadow)
        if (rails) {
            // Outline = stroke, then the fill is cleared so overlapping contours of the variable font never show.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.09f
            paint.strokeJoin = Paint.Join.ROUND
            c.drawText(s, x, baseline, paint)
            paint.style = Paint.Style.FILL
            // same paint (features, width, weight) so the cleared fill lands exactly inside the stroke
            val clear = Paint(paint).apply { shader = null; clearShadowLayer(); xfermode = fx.xfermode }
            c.drawText(s, x, baseline, clear)
        } else {
            c.drawText(s, x, baseline, paint)
            if (glass) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * 0.012f
                paint.shader = null
                paint.color = glassStroke(look.c1)
                // outline of the merged glyph shape: variable fonts overlap contours, a plain stroke shows them
                val path = android.graphics.Path()
                paint.getTextPath(s, 0, s.length, x, baseline, path)
                path.op(android.graphics.Path(), android.graphics.Path.Op.UNION)
                c.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }
        }
        paint.clearShadowLayer()
        if (stencil) {
            // Vertical gap through the middle of every glyph except '1' and the colon (7 % of the advance).
            // advances in context (tnum, contextual colon), so the cut sits on the glyph that was drawn
            val adv = FloatArray(s.length).also { paint.getTextWidths(s, it) }
            var cx = x
            for (i in s.indices) {
                val a = adv[i]
                if (text[i] in '0'..'9' && text[i] != '1') {
                    val g = a * 0.035f
                    c.drawRect(cx + a / 2 - g, top - size * 0.1f, cx + a / 2 + g, bottom + size * 0.1f, fx)
                }
                cx += a
            }
        }
        if (layered) c.restore()
    }

    private fun Paint.reset(look: Look, l: Float, t: Float, r: Float, b: Float) {
        style = Paint.Style.FILL
        shader = null
        color = look.c1
        when (look.effect) {
            Effect.TWO_COLOR -> shader = LinearGradient(l, t, r, b, look.c1, look.c2, Shader.TileMode.CLAMP)
            Effect.RAINBOW -> shader = LinearGradient(l, t, r, t, Look.RAINBOW, null, Shader.TileMode.CLAMP)
            // 60 % (review C5): 30 % read ≈ 1.5:1 on black, the digits showed only through the stroke
            Effect.GLASS -> color = withAlpha(look.c1, 0.60f)
            else -> {}
        }
    }

    companion object {
        const val REF = 100f
        const val COLON = 0.7f
        fun withAlpha(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((a * 255).toInt() shl 24)

        /** Glass stroke = mix(acc, white 65 %), never darker than luminance 0.5 (so it never reads dark on black). */
        fun glassStroke(c1: Int): Int {
            var c = lerp(Color(c1), Color.White, 0.65f) // literal-ok: glass highlight = fill pushed toward white
            var k = 0.65f
            while (c.luminance() < 0.5f && k < 1f) { k += 0.05f; c = lerp(Color(c1), Color.White, k) } // literal-ok
            return c.toArgb()
        }
    }
}

/** Cached painter for a style (rebuilt only when font/weight/width/numerals change). */
@Composable
internal fun rememberTimePainter(style: ClockStyle, weight: Int = style.weight, tnum: Boolean = false): TimePainter {
    val tf = rememberClockTypeface(style.font, weight)
    return remember(tf, weight, style.font, style.widthPct, style.numerals, tnum) { TimePainter(tf, weight, style.font, style.widthPct, style.numerals, tnum) }
}

/**
 * Face background colours; null = paint nothing ([kr.dot.sb.model.Background.BLACK] means "the page", r9 D4b). Own
 * colours fade to the night page (or [nightPlate], Solar's candle plate) as night rises.
 */
internal fun bgColors(style: ClockStyle, ink: Ink, nightPlate: Color? = null): Pair<Int, Int>? {
    val to = nightPlate ?: ThemeTokens.NIGHT_BG.base
    val (a, b) = when (style.background) {
        kr.dot.sb.model.Background.BLACK -> return nightPlate?.takeIf { ink.night > 0f }?.copy(alpha = ink.night)?.toArgb()?.let { it to it }
        kr.dot.sb.model.Background.COLOR -> Color(style.bgColor) to Color(style.bgColor)
        kr.dot.sb.model.Background.GRADIENT -> Color(style.bgColor) to Color(style.bgColor2)
    }
    return lerp(a, to, ink.night).toArgb() to lerp(b, to, ink.night).toArgb()
}

/** Paint the face background ([r] > 0 = rounded plate, used in slots). 105° gradient like the mockup. */
internal fun Canvas.drawFaceBg(bg: Pair<Int, Int>?, w: Float, h: Float, r: Float) {
    bg ?: return
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bg.first == bg.second) p.color = bg.first
    else p.shader = LinearGradient(0f, h * 0.35f, w, h * 0.65f, intArrayOf(bg.first, bg.first, bg.second), floatArrayOf(0f, 0.2f, 1f), Shader.TileMode.CLAMP)
    if (r > 0f) drawRoundRect(0f, 0f, w, h, r, r, p) else drawRect(0f, 0f, w, h, p)
}
