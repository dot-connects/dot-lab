package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
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
internal fun rememberClockTypeface(font: FontId, weight: Int): Typeface = rememberTypeface(Fonts.clock(font, weight), weight)

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

internal fun TextPaint.fit(text: String, maxW: Float): String =
    if (measureText(text) <= maxW) text else TextUtils.ellipsize(text, this, maxW, TextUtils.TruncateAt.END).toString()

/** Colours of one time run after night mapping (N4: effects off at night except glow; backgrounds black). */
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
                !effects -> Effect.SOLID
                night && style.effect != Effect.GLOW -> Effect.SOLID
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
 */
internal class TimePainter(tf: Typeface, weight: Int, private val font: FontId, widthPct: Int, val numerals: Numerals) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        fontVariationSettings = "'wght' $weight"
        textScaleX = widthPct / 100f
        textSize = REF
        fontFeatureSettings = "'tnum'"
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

    /** Widest hour string (§2.3.1: 12 h "10"-"12", 24 h "00"-"23"), in em. */
    fun hourEm(use24h: Boolean) = if (use24h) maxAdv(0..2) + maxAdv(0..9) else max(digitAdv[1] + maxAdv(0..2), maxAdv(1..9))
    val minuteEm get() = maxAdv(0..5) + maxAdv(0..9)
    /** Widest "h:mm" in em, with 3 % for kerning slack. */
    fun widestEm(use24h: Boolean) = (hourEm(use24h) + colonAdv + minuteEm) * 1.03f
    /** Widest string of arbitrary digits (each digit position takes the widest digit). */
    fun widestEm(pattern: String) = pattern.sumOf { if (it in '0'..'9') maxAdv(0..9).toDouble() else measure(it.toString(), 1f).toDouble() }.toFloat() * 1.03f

    fun measure(text: String, size: Float): Float { paint.textSize = size; return paint.measureText(numerals.convert(text)) }

    /**
     * Draw [text] (ASCII digits, converted here) with its left edge at [x] and baseline at [baseline].
     * Stencil/Rails cuts are drawn here, per font.
     */
    fun draw(c: Canvas, text: String, x: Float, baseline: Float, size: Float, look: Look) {
        val s = numerals.convert(text)
        paint.textSize = size
        val w = paint.measureText(s)
        val top = baseline - capEm * size
        val bottom = baseline + descEm * size
        val stencil = font == FontId.STENCIL
        val rails = font == FontId.RAILS
        val layered = stencil || rails
        if (layered) c.saveLayer(x - size, top - size, x + w + size, bottom + size, null)

        paint.reset(look, x, top, x + w, bottom)
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
            Effect.GLASS -> color = withAlpha(look.c1, 0.30f)
            else -> {}
        }
    }

    companion object {
        const val REF = 100f
        fun withAlpha(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((a * 255).toInt() shl 24)

        /** Glass stroke = mix(acc, white 65 %), never darker than luminance 0.5 (so it never reads dark on black). */
        fun glassStroke(c1: Int): Int {
            var c = lerp(Color(c1), Color.White, 0.65f)
            var k = 0.65f
            while (c.luminance() < 0.5f && k < 1f) { k += 0.05f; c = lerp(Color(c1), Color.White, k) }
            return c.toArgb()
        }
    }
}

/** Cached painter for a style (rebuilt only when font/weight/width/numerals change). */
@Composable
internal fun rememberTimePainter(style: ClockStyle, weight: Int = style.weight): TimePainter {
    val tf = rememberClockTypeface(style.font, weight)
    return remember(tf, weight, style.font, style.widthPct, style.numerals) { TimePainter(tf, weight, style.font, style.widthPct, style.numerals) }
}

/** Night-aware background colours (N4: black at night). Solar keeps a red-black plate (§5.2). */
internal fun bgColors(style: ClockStyle, ink: Ink, nightPlate: Color = Color.Black): Pair<Int, Int>? {
    val (a, b) = when (style.background) {
        kr.dot.sb.model.Background.BLACK -> return if (nightPlate != Color.Black && ink.night > 0f) lerp(Color.Black, nightPlate, ink.night).toArgb().let { it to it } else null
        kr.dot.sb.model.Background.COLOR -> Color(style.bgColor) to Color(style.bgColor)
        kr.dot.sb.model.Background.GRADIENT -> Color(style.bgColor) to Color(style.bgColor2)
    }
    return lerp(a, nightPlate, ink.night).toArgb() to lerp(b, nightPlate, ink.night).toArgb()
}

/** Paint the face background ([r] > 0 = rounded plate, used in slots). 105° gradient like the mockup. */
internal fun Canvas.drawFaceBg(bg: Pair<Int, Int>?, w: Float, h: Float, r: Float) {
    bg ?: return
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bg.first == bg.second) p.color = bg.first
    else p.shader = LinearGradient(0f, h * 0.35f, w, h * 0.65f, intArrayOf(bg.first, bg.first, bg.second), floatArrayOf(0f, 0.2f, 1f), Shader.TileMode.CLAMP)
    if (r > 0f) drawRoundRect(0f, 0f, w, h, r, r, p) else drawRect(0f, 0f, w, h, p)
}
