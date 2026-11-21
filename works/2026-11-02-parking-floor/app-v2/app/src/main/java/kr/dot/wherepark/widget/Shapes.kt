package kr.dot.wherepark.widget

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Contrast
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Style
import kr.dot.wherepark.ui.AssetFonts

/** One car as the widget shows it. [f] floor, [s] zone/number/pillar, [memo] the park's memo; [stale] = "3일 전" chip. */
internal enum class St { OK, NONE, PEND, FAIL, GONE }
internal data class Line(
    val car: Car, val f: String, val s: String, val memo: String, val st: St, val who: String, val time: String, val stale: String? = null,
)

/** Text box drawn, in widget dp, for tools/contrast.py. */
internal data class TextBox(val rect: RectF, val text: String, val base: Float = rect.bottom)

/**
 * Text colours (mockup v3 look()). Under 50 % ([low]) the text is white (or the forced colour) and the halo grows one level at a
 * time (3 levels) until small text reaches 7:1 over the wallpaper; from 50 % auto nudges the surface toward a pole until 7:1
 * ([of], ported from round2 fitText), a forced colour leaves the card as it is ([fixed]).
 * [big] = poster accent numerals moved toward the text colour until they read. [haloLv] 0 none .. 3 strongest.
 */
internal data class Look(
    val bg: Int, val a: Float, val fg: Int, val mu: Int, val dk: Boolean, val scrim: Int, val big: Int, val haloLv: Int, val score: Double,
) {
    val whi: Float get() = if (dk) .07f else .55f

    companion object {
        private val BLACK = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        private val WHITE = doubleArrayOf(255.0, 255.0, 255.0, 1.0)
        private val INK = doubleArrayOf(17.0, 17.0, 17.0, 1.0)
        private val DARK = doubleArrayOf(17.0, 17.0, 17.0, .78)
        private val LIGHT = doubleArrayOf(255.0, 255.0, 255.0, .88)

        /** How much each halo level covers what is behind the text (mockup HA; an estimate, to check on a phone). */
        private val HALO = doubleArrayOf(0.0, .4, .6, .8)

        /** 7:1 for small text, 4.5:1 for the big numerals. */
        const val MIN_CR = 7.0
        private const val MIN_BIG = 4.5
        private fun rgba(c: Int) = doubleArrayOf((c shr 16 and 255).toDouble(), (c shr 8 and 255).toDouble(), (c and 255).toDouble(), (c ushr 24) / 255.0)
        private fun lin(v: Double): Double { val s = v / 255; return if (s <= .03928) s / 12.92 else ((s + .055) / 1.055).pow(2.4) }
        private fun lum(c: DoubleArray) = .2126 * lin(c[0]) + .7152 * lin(c[1]) + .0722 * lin(c[2])
        private fun cr(a: DoubleArray, b: DoubleArray): Double { val x = lum(a); val y = lum(b); return (max(x, y) + .05) / (min(x, y) + .05) }
        private fun over(top: DoubleArray, a: Double, bot: DoubleArray) = DoubleArray(3) { top[it] * a + bot[it] * (1 - a) }
        private fun argb(c: DoubleArray, a: Double = 1.0): Int =
            ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (c[0].roundToInt().coerceIn(0, 255) shl 16) or
                (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)
        private fun pol(f: DoubleArray) = lum(f) > .4
        private fun worst(col: DoubleArray, bgs: List<DoubleArray>) = bgs.minOf { bg -> cr(over(col, col.getOrElse(3) { 1.0 }, bg), bg) }
        private fun walls(samples: List<Int>) = samples.ifEmpty { listOf(0xFF808080.toInt()) }.map(::rgba)

        /** Accent moved toward the text colour until [min] against every background. */
        private fun bigOf(f: DoubleArray, acc: DoubleArray, bgs: List<DoubleArray>, min: Double): Int {
            var t = 0.0
            while (t <= 1.001) {
                val c = over(f, t, acc)
                if (bgs.minOf { cr(c, it) } >= min) return argb(c)
                t += .15
            }
            return argb(f)
        }

        /** The widget's colours: [mode] = auto | white | black (per-widget 글자 색). */
        fun pick(style: Style, strength: Int, samples: List<Int>, mode: String): Look {
            val wop = strength / 100.0
            val force = when (mode) { "white" -> WHITE; "black" -> INK; else -> null }
            return when {
                wop < .5 -> low(style, wop, samples, force ?: WHITE)
                force != null -> fixed(style, wop, samples, force)
                else -> of(style, wop, samples)
            }
        }

        /** Under 50 %: the text colour stays, the halo (dark under light text) steps up until 7:1. */
        private fun low(style: Style, wop: Double, samples: List<Int>, f: DoubleArray): Look {
            val b = rgba(style.bg); val a = wop * b[3]
            val pole = if (pol(f)) BLACK else WHITE
            val under = walls(samples).map { over(b, a, it) }
            var lv = 1; var bgs = under; var score = 0.0
            for (l in 1..3) {
                lv = l; bgs = under.map { over(pole, HALO[l], it) }; score = worst(f, bgs)
                if (score >= MIN_CR) break
            }
            return Look(argb(b), wop.toFloat(), argb(f), argb(f), pol(f), 0, bigOf(f, rgba(style.acc), bgs, MIN_BIG), lv, score)
        }

        /** 흰 / 검정 from 50 %: only the text colour changes, the card keeps its colour. */
        private fun fixed(style: Style, wop: Double, samples: List<Int>, f: DoubleArray): Look {
            val b = rgba(style.bg); val a = wop * b[3]
            val bgs = walls(samples).map { over(b, a, it) }
            return Look(argb(b), wop.toFloat(), argb(f), argb(f, .86), pol(f), 0, bigOf(f, rgba(style.acc), bgs, MIN_BIG), 0, worst(f, bgs))
        }

        fun of(style: Style, wop: Double, samples: List<Int>): Look {
            val b = rgba(style.bg); val f0 = rgba(style.fg); val m0 = rgba(style.mu); val acc = rgba(style.acc)
            val a = wop * b[3]
            val ws = walls(samples)
            val cands = listOf(f0 to m0, f0 to (if (pol(f0)) LIGHT else DARK), DARK to DARK, LIGHT to LIGHT)
            class E(val f: DoubleArray, val m: DoubleArray, val lt: Boolean, val bp: DoubleArray, val sc: Double, val pole: DoubleArray, val bgs: List<DoubleArray>, val score: Double)
            var best: E? = null
            var pick: E? = null
            search@ for ((set, maxL) in listOf(cands.take(2) to 4, cands to 9)) for (l in 0..maxL) for ((f, m) in set) {
                val lt = pol(f); val pole = if (lt) BLACK else WHITE
                val bp = over(pole, l * .1, b); val sc = l * .07
                val bgs = ws.map { w -> over(bp, a, over(pole, sc, w)) }
                val score = min(worst(doubleArrayOf(f[0], f[1], f[2], 1.0), bgs), worst(m, bgs))
                val e = E(f, m, lt, bp, sc, pole, bgs, score)
                if (best == null || score > best.score) best = e
                if (score >= MIN_CR) { pick = e; break@search }
            }
            val r = pick ?: best!!
            // also on the poster strip, which tints the surface with 5 % text colour
            val big = bigOf(if (r.lt) WHITE else BLACK, acc, r.bgs + r.bgs.map { over(r.f, .05, it) }, MIN_CR)
            return Look(argb(r.bp), wop.toFloat(), argb(r.f), argb(r.m, r.m.getOrElse(3) { 1.0 }), r.lt,
                if (r.sc > 0 && wop < .995) argb(r.pole, r.sc) else 0, big, 0, r.score)
        }
    }
}

internal fun Int.alpha(a: Float): Int = (((this ushr 24) * a).roundToInt().coerceIn(0, 255) shl 24) or (this and 0xFFFFFF)
private fun white(a: Float) = 0xFFFFFFFF.toInt().alpha(a)
private fun black(a: Float) = 0xFF000000.toInt().alpha(a)

/**
 * Canvas drawing of the widget (mockup v3-widget-sizes, A안): the content fills the real cell. Every shape shares one tile
 * layout per car (mockup tile()); a shape only adds its structure behind it (card, bands, LED panel, tickets), faded with the
 * strength, and its numeral faces. At strength 0 nothing but text and the mic button is drawn.
 * [textScale] = system font scale x 글자 크게, already clamped to 1..1.3; it grows small text only, never the floor.
 */
internal class Shapes(
    private val ctx: Context, private val c: Canvas, private val style: Style, private val look: Look, private val shape: Shape,
    private val micOn: Boolean, private val textScale: Float = 1f,
) {
    val hits = ArrayList<Hit>()
    val texts = ArrayList<TextBox>()
    /** Mic circles as drawn (widget dp), for the corner test. */
    val mics = ArrayList<RectF>()
    private val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val isDefault = style.family == "default"
    private val mat = if (isDefault) "base" else style.material
    private val a = look.a
    private val fg = look.fg
    private val mu = look.mu
    private val bw = if (a < .5f) KO700 else KO500   // memo and time: bolder over the bare wallpaper
    private val micIcon = if (shape == Shape.DOT) 0xFFF2F2F2.toInt() else if (isDefault) 0xFFFFFFFF.toInt() else style.onAcc
    private val geo = GEO.getValue(shape)
    private val xh = geo[6]
    /** 기둥 띠 name line sits on the painted band (white text) only while the band is clearly there. */
    private val onBand = shape == Shape.PILLAR && a >= .5f

    companion object {
        private val KO500: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        private val KO600: Typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        private val KO700: Typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        private val MONO: Typeface = Typeface.MONOSPACE
        private val tfCache = HashMap<String, Typeface>()
        private const val PAD = 8f
        /** Largest corner radius as a share of the widget's short side. */
        const val R_MAX = .22f
        /** Distance from a corner where a box corner just touches the rounded curve: r * (1 - 1/sqrt 2). */
        private const val CURVE = .2929f

        /** Floor size = its row height / this (the mockup's line-height .84 on the numerals). */
        private const val F_LH = .84f
        /** Floor cap so a tablet-size cell keeps the hierarchy instead of one giant glyph. */
        private const val F_MAX = 150f
        private const val MIC_MIN = 48f
        /** Content-box widths: below NARROW the mic shares the floor row; from WIDE_ONE a one-row tile stacks pillar + memo beside a big floor. */
        private const val NARROW = 190f
        private const val WIDE_ONE = 280f
        /** Content-box heights: from TALL the tall layouts; below SHORT two compact lines beside the mic. */
        private const val TALL = 120f
        private const val SHORT = 68f
        /** Widget width from which two cars sit side by side. */
        const val WIDE = 290f

        /** Per shape (mockup GEO): gap between cars, top pad (first, later), bottom pad (last, earlier), inner side pad, extra height under the name line. */
        private val GEO = mapOf(
            Shape.ROW to floatArrayOf(1f, 8f, 4f, 8f, 4f, 4f, 0f),
            Shape.PILLAR to floatArrayOf(4f, 5f, 5f, 7f, 7f, 7f, 4f), Shape.DOT to floatArrayOf(6f, 8f, 7f, 8f, 7f, 7f, 0f),
            Shape.TICKET to floatArrayOf(5f, 6f, 6f, 6f, 6f, 8f, 3f),
        )
    }

    private fun num(w: Int) = face(style.font, w)
    private fun face(font: String, w: Int): Typeface = synchronized(tfCache) { tfCache.getOrPut("$font/$w") { AssetFonts.typeface(ctx, font, w) } }

    // ---------- primitives ----------

    private fun sigmaRadius(blurPx: Float) = max(.1f, (blurPx / 2 - .5f) / .57735f)

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun rrPath(r: RectF, rad: Float) = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }

    /** CSS outer box-shadow: drawn only outside [box]. */
    private fun shadow(box: Path, r: RectF, rad: Float, dx: Float, dy: Float, blur: Float, spread: Float, color: Int) {
        c.save(); c.clipOutPath(box)
        val q = fill(color).apply { if (blur > 0) maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL) }
        val s = RectF(r).apply { inset(-spread, -spread); offset(dx, dy) }
        val sr = max(0f, rad + spread)
        c.drawRoundRect(s, sr, sr, q)
        c.restore()
    }

    /** CSS inset 0 dy 0 color (a 1-px edge inside the top or bottom). */
    private fun edge(box: Path, dy: Float, color: Int) {
        val moved = Path(box).apply { offset(0f, dy) }
        val d = Path().apply { op(box, moved, Path.Op.DIFFERENCE) }
        c.drawPath(d, fill(color))
    }

    /** CSS inset blurred shadow (inner shadow). */
    private fun innerShadow(box: Path, r: RectF, dx: Float, dy: Float, blur: Float, color: Int) {
        c.save(); c.clipPath(box)
        val outer = Path().apply { addRect(RectF(r).apply { inset(-40f, -40f) }, Path.Direction.CW) }
        val hole = Path(box).apply { offset(dx, dy) }
        val d = Path().apply { op(outer, hole, Path.Op.DIFFERENCE) }
        c.drawPath(d, fill(color).apply { if (blur > 0) maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL) })
        c.restore()
    }

    private fun ring(r: RectF, rad: Float, inset: Float, width: Float, color: Int) {
        val q = fill(color).apply { style = Paint.Style.STROKE; strokeWidth = width }
        val rr = RectF(r).apply { inset(inset + width / 2, inset + width / 2) }
        val rd = max(0f, rad - inset - width / 2)
        c.drawRoundRect(rr, rd, rd, q)
    }

    private fun linear(r: RectF, deg: Float, stops: IntArray, pos: FloatArray): Shader {
        val th = Math.toRadians(deg.toDouble())
        val dx = sin(th).toFloat(); val dy = (-cos(th)).toFloat()
        val len = kotlin.math.abs(r.width() * dx) + kotlin.math.abs(r.height() * dy)
        val cx = r.centerX(); val cy = r.centerY()
        return LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2, stops, pos, Shader.TileMode.CLAMP)
    }

    /** The widget surface: scrim, colour at strength, material highlight (CSS background stack --wsc, --wsf, --wgl). */
    private fun surface(box: Path, r: RectF) {
        c.save(); c.clipPath(box)
        if (look.scrim != 0) c.drawPaint(fill(look.scrim))
        c.drawPaint(fill(look.bg.alpha(a)))
        val w = look.whi
        when (mat) {
            "base" -> c.drawRect(r, Paint().apply { shader = linear(r, 180f, intArrayOf(white(w * a), white(0f)), floatArrayOf(0f, .44f)) })
            "gloss" -> c.drawRect(r, Paint().apply { shader = linear(r, 180f, intArrayOf(white(w * .95f * a), white(w * .16f * a), white(0f)), floatArrayOf(0f, .48f, .52f)) })
            "metal" -> c.drawRect(r, Paint().apply { shader = linear(r, 160f, intArrayOf(white(w * .7f * a), white(0f), black(.09f * a)), floatArrayOf(0f, .42f, 1f)) })
        }
        c.restore()
    }

    /** Card = outer shadows + surface + inset edges + 1-px border, per material (CSS .wx and .wx[data-mat]); all faded with the strength. */
    private fun card(r: RectF, rad: Float) {
        val box = rrPath(r, rad)
        val w = look.whi
        when (mat) {
            "matte" -> { shadow(box, r, rad, 0f, 1f, 2f, 0f, black(.14f * a)); shadow(box, r, rad, 0f, 10f, 20f, -10f, black(.35f * a)) }
            "gloss" -> shadow(box, r, rad, 0f, 12f, 26f, -10f, black(.45f * a))
            "paper" -> { shadow(box, r, rad, 0f, 1f, 1f, 0f, black(.12f * a)); shadow(box, r, rad, 0f, 6f, 10f, -6f, black(.28f * a)) }
            "metal" -> shadow(box, r, rad, 0f, 10f, 22f, -10f, black(.45f * a))
            "soft" -> { shadow(box, r, rad, -4f, -4f, 10f, 0f, white(.4f * a)); shadow(box, r, rad, 6f, 7f, 14f, 0f, black(.24f * a)) }
            else -> { shadow(box, r, rad, 0f, 1f, 2f, 0f, black(.10f * a)); shadow(box, r, rad, 0f, 12f, 24f, -12f, black(.38f * a)) }
        }
        surface(box, r)
        c.save(); c.clipPath(box)
        when (mat) {
            "gloss" -> { edge(box, 1f, white(min(1f, w * 1.7f) * a)); innerShadow(box, r, 0f, -2f, 6f, black(.12f * a)) }
            "paper" -> ring(r, rad, 3f, 1f, fg.alpha(.12f * a))
            "metal" -> { edge(box, 1f, white(min(1f, w * 1.3f) * a)); edge(box, -1f, black(.2f * a)) }
            "soft" -> ring(r, rad, 0f, 1f, white(.14f * a))
            "matte" -> {}
            else -> edge(box, 1f, white(min(1f, w * 1.5f) * a))
        }
        c.restore()
        ring(r, rad, 0f, 1f, fg.alpha((if (mat == "paper") .16f else .06f) * a))
    }

    // ---------- text ----------

    private fun setText(size: Float, tf: Typeface, ls: Float) { p.reset(); p.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG; p.typeface = tf; p.textSize = size; p.letterSpacing = ls }
    private fun measure(s: String, size: Float, tf: Typeface, ls: Float = 0f): Float { setText(size, tf, ls); return p.measureText(s) }
    /** [s] cut to [max] with "…" (by measuring: TextUtils.ellipsize is a no-op under Robolectric, so the shots would lie). */
    private fun clip(s: String, size: Float, tf: Typeface, max: Float, ls: Float = 0f): String {
        setText(size, tf, ls)
        if (p.measureText(s) <= max) return s
        var n = p.breakText(s, true, max(0f, max - p.measureText("…")), null)
        while (n > 0 && s[n - 1] == ' ') n--
        return if (n <= 0) "" else s.substring(0, n) + "…"
    }

    /** Baseline that centres one line of [tf] at [size] in a [h]-tall box at [top]. */
    private fun midBase(top: Float, h: Float, size: Float, tf: Typeface): Float { setText(size, tf, 0f); val fm = p.fontMetrics; return top + h / 2 - (fm.ascent + fm.descent) / 2 }

    private fun isLight(color: Int) = Contrast.readableOn(color or (0xFF shl 24)) == Contrast.INK

    /** Halo passes per level (mockup halo(): CSS blur, dy, alpha). */
    private val haloPasses = when (look.haloLv) {
        0 -> emptyList()
        1 -> listOf(Triple(2f, 0f, .7f), Triple(6f, 0f, .45f))
        2 -> listOf(Triple(1f, 0f, .9f), Triple(3f, 0f, .8f), Triple(7f, 0f, .6f), Triple(12f, 1f, .4f))
        else -> listOf(Triple(1f, 0f, 1f), Triple(2f, 0f, 1f), Triple(4f, 0f, .9f), Triple(8f, 0f, .75f), Triple(14f, 2f, .55f))
    }

    /** Text at a baseline with the look's halo (dark under light text, light under dark). Returns the advance width. */
    private fun text(s: String, x: Float, base: Float, size: Float, tf: Typeface, color: Int, ls: Float = 0f, right: Boolean = false,
                     stroke: Float = 0f, halo: Boolean = look.haloLv > 0, record: Boolean = true): Float {
        if (s.isEmpty()) return 0f
        setText(size, tf, ls)
        val w = p.measureText(s)
        val x0 = if (right) x - w else x
        if (halo) {
            val sh = if (isLight(color)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            for ((blur, dy, al) in haloPasses) {
                p.color = sh.alpha(al); p.maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL)
                if (stroke > 0) { p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = stroke } else p.style = Paint.Style.FILL
                c.drawText(s, x0, base + dy, p)
            }
            p.maskFilter = null
        }
        p.color = color
        if (stroke > 0) { p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = stroke } else p.style = Paint.Style.FILL
        c.drawText(s, x0, base, p)
        p.style = Paint.Style.FILL
        if (record) { val fm = p.fontMetrics; texts += TextBox(RectF(x0, base + fm.ascent * .72f, x0 + w, base + fm.descent * .35f), s, base) }
        return w
    }

    // floor (big) and zone/pillar (small) faces per shape; DOT draws every Latin letter and digit in Doto, Hangul in gothic
    private val fFace get() = num(if (shape == Shape.TICKET) 700 else 900)
    private val pFace get() = num(if (shape == Shape.TICKET) 600 else 800)
    private val fLs = when (shape) { Shape.TICKET -> -.06f; Shape.PILLAR, Shape.DOT -> 0f; else -> -.03f }
    private val pLs = when (shape) { Shape.TICKET -> -.03f; Shape.DOT -> 0f; else -> -.01f }
    private val doto get() = face("Doto", 900)
    private val gothic get() = face("Inter Tight", 900)
    private fun runs(s: String) = Regex("[ -~]+|[^ -~]+").findAll(s).map { it.value to (it.value[0] in ' '..'~') }

    private fun spotW(s: String, size: Float, big: Boolean): Float =
        if (shape != Shape.DOT) measure(s, size, if (big) fFace else pFace, if (big) fLs else pLs)
        else runs(s).sumOf { (t, latin) -> (if (latin) measure(t, size, doto) else measure(t, size * .84f, gothic, -.02f) + 2f).toDouble() }.toFloat()

    private fun spotDraw(s: String, x: Float, base: Float, size: Float, big: Boolean, color: Int): Float {
        if (shape != Shape.DOT) return text(s, x, base, size, if (big) fFace else pFace, color, if (big) fLs else pLs)
        var cx = x
        runs(s).forEach { (t, latin) -> cx += if (latin) text(t, cx, base, size, doto, color, stroke = .55f) else text(t, cx, base, size * .84f, gothic, color, -.02f) + 2f }
        return cx - x
    }

    /** Ink-centred baseline for a spot string in a [h]-tall row. */
    private fun spotBase(s: String, size: Float, big: Boolean, top: Float, h: Float): Float {
        setText(size, if (shape == Shape.DOT) doto else if (big) fFace else pFace, 0f)
        val r = Rect(); p.getTextBounds(s, 0, s.length, r)
        return if (r.height() == 0) top + h * .8f else top + h / 2 - (r.top + r.bottom) / 2f
    }

    /** Drop characters until [s] fits [maxW] (last resort after shrinking). */
    private fun squeeze(s: String, size: Float, big: Boolean, maxW: Float): String {
        if (spotW(s, size, big) <= maxW + .5f) return s
        var t = s
        while (t.length > 1 && spotW("$t…", size, big) > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Shrink the pillar first (down to 14 dp), then the floor (down to 20 dp), until floor + pillar fit [avail]. */
    private fun fit(f: String, s: String, fz0: Float, pz0: Float, gap: Float, avail: Float): Pair<Float, Float> {
        var fz = fz0; var pz = pz0
        fun over() = spotW(f, fz, true) + (if (s.isEmpty()) 0f else gap + spotW(s, pz, false)) - avail
        var i = 0
        while (s.isNotEmpty() && pz > 14f && over() > .5f && i++ < 8) { val wp = spotW(s, pz, false); pz = max(14f, pz * max(.2f, (wp - over()) / wp * .98f)) }
        i = 0
        while (fz > 20f && over() > .5f && i++ < 8) { val wf = spotW(f, fz, true); fz = max(20f, fz * max(.2f, (wf - over()) / wf * .98f)) }
        return fz to pz
    }

    private fun spotShown(l: Line) = l.st == St.OK || l.st == St.PEND || l.st == St.FAIL
    private val fColor = fg

    /**
     * Floor (+ pillar) on one baseline, centred in the row; non-spot states show "운행 중" / "기록 없음" at [k] x 15..26.
     * Returns the width used.
     */
    private fun spotRow(l: Line, x: Float, top: Float, h: Float, avail: Float, fz0: Float, pz0: Float, withP: Boolean, k: Float, gap: Float = 8f): Float {
        if (!spotShown(l)) {
            val t = if (l.st == St.GONE) "운행 중" else "기록 없음"
            var z = (fz0 * .4f).coerceIn(15f * k, 26f * k)
            while (z > 13f && measure(t, z, KO700) > avail) z = max(13f, z * .92f)
            return text(clip(t, z, KO700, avail), x, midBase(top, h, z, KO700), z, KO700, if (l.st == St.NONE) mu else fg)
        }
        val s = if (withP) l.s else ""
        val (fz, pz) = fit(l.f, s, min(F_MAX, fz0), pz0, gap, avail)
        val base = spotBase(l.f, fz, true, top, h)
        val fw = spotDraw(squeeze(l.f, fz, true, avail), x, base, fz, true, fColor)
        if (s.isEmpty()) return fw
        val room = avail - fw - gap
        return if (room > 14f) fw + gap + spotDraw(squeeze(s, pz, false, room), x + fw + gap, base, pz, false, fg) else fw
    }

    /** Size of the pillar alone in [maxW], from [z0] down to 14. */
    private fun pillarSize(l: Line, z0: Float, maxW: Float): Float {
        var z = z0
        while (z > 14f && spotW(l.s, z, false) > maxW) z = max(14f, z * .92f)
        return z
    }

    /** Pillar alone; [base] null = ink-centred in the row at [top]/[h]. Returns the width drawn. */
    private fun pillar(l: Line, x: Float, z0: Float, maxW: Float, top: Float, h: Float, base: Float? = null): Float {
        if (!spotShown(l) || l.s.isEmpty() || maxW < 14f) return 0f
        val z = pillarSize(l, z0, maxW)
        return spotDraw(squeeze(l.s, z, false, maxW), x, base ?: spotBase(l.s, z, false, top, h), z, false, fg)
    }

    /** Memo: one step smaller when long, then an ellipsis. */
    private fun memo(m: String, x: Float, base: Float, z: Float, maxW: Float): Float {
        if (m.isEmpty() || maxW < 16f) return 0f
        val zz = if (measure(m, z, bw) > maxW) z * .87f else z
        return text(clip(m, zz, bw, maxW), x, base, zz, bw, fg)
    }

    /** Memo on up to two lines (tall wide tiles): break at the last space that fits, the second line ellipsized. */
    private fun memo2(m: String, x: Float, top: Float, h: Float, z: Float, maxW: Float) {
        if (m.isEmpty() || maxW < 16f) return
        val lh = z * 1.25f
        if (measure(m, z, bw) <= maxW) { text(m, x, midBase(top, h, z, bw), z, bw, fg); return }
        setText(z, bw, 0f)
        var n = p.breakText(m, true, maxW, null)
        m.lastIndexOf(' ', n).takeIf { it > 0 && n < m.length }?.let { n = it }
        val first = m.substring(0, n).trimEnd(); val rest = m.substring(n).trimStart()
        val t0 = top + (h - 2 * lh) / 2
        text(first, x, midBase(t0, lh, z, bw), z, bw, fg)
        text(clip(rest, z, bw, maxW), x, midBase(t0 + lh, lh, z, bw), z, bw, fg)
    }

    /**
     * Name line (mockup l1): colour bar + name first, "외 N대", then the time or a state chip at the right end.
     * A long name first shortens the time ("동찬 17:15" -> "17:15"), then drops it; when even a short name has no room,
     * "외 N대" goes first, the state chip last.
     */
    private fun nameLine(l: Line, more: Int, x: Float, top: Float, w: Float, z: Float, band: Boolean) {
        val lh = z * 1.15f
        val zs = z * 12f / 13f
        val base = midBase(top, lh, z, KO700)
        val f = if (band) 0xFFFFFFFF.toInt() else fg
        val m = if (band) 0xFFFFFFFF.toInt() else mu
        val halo = !band && look.haloLv > 0
        val cy = top + lh / 2
        val bar = RectF(x, cy - z / 2, x + 4f, cy + z / 2)
        c.drawRoundRect(RectF(bar).apply { inset(-1f, -1f) }, 3f, 3f, fill(if (band) white(.75f) else fg.alpha(.6f)))
        c.drawRoundRect(bar, 2f, 2f, fill(l.car.color or (0xFF shl 24)))
        val moreS = if (more > 0) "외 ${more}대" else ""
        val chip = when { l.st == St.PEND -> "저장 중"; l.st == St.FAIL -> "실패"; l.stale != null && l.st == St.OK -> l.stale; else -> null }
        var moreOn = more > 0; var chipOn = chip != null
        fun moreW() = if (moreOn) measure(moreS, zs, KO600) + 4f else 0f
        fun chipW() = if (chipOn) measure(chip!!, zs, KO700) + zs + 6f else 0f
        val nameW = measure(l.car.name, z, KO700)
        val minName = min(nameW, z * 1.5f)
        if (8f + minName + moreW() + chipW() > w) moreOn = false
        if (8f + minName + chipW() > w) chipOn = false
        val timeFace = if (shape == Shape.DOT || shape == Shape.TICKET) MONO else bw
        val tls = if (timeFace == MONO) -.03f else 0f
        val full = when (l.st) { St.OK -> "${l.who} ${l.time}".trim(); St.GONE -> "${l.who} 출발".trim(); else -> "" }
        val short = when (l.st) { St.OK -> l.time; St.GONE -> "출발"; else -> "" }
        val fixed = 8f + moreW() + chipW()
        var tail = if (chip != null) "" else full
        fun tailW() = if (tail.isEmpty()) 0f else measure(tail, z, timeFace, tls) + 6f
        if (tail.isNotEmpty() && nameW > w - fixed - tailW()) tail = short
        if (tail.isNotEmpty() && nameW > w - fixed - tailW()) tail = ""
        val nm = clip(l.car.name, z, KO700, w - fixed - tailW())
        val nw = text(nm, x + 8f, base, z, KO700, f, halo = halo)
        if (moreOn) text(moreS, x + 8f + nw + 4f, base, zs, KO600, m, halo = halo)
        if (tail.isNotEmpty()) text(tail, x + w, base, z, timeFace, m, tls, right = true, halo = halo)
        if (chipOn) {
            val ch = zs * 16f / 12f
            val r = RectF(x + w - (chipW() - 6f), cy - ch / 2, x + w, cy + ch / 2)
            val (bg, tc) = when {
                l.st == St.PEND -> fg to (if (isLight(fg)) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                l.st == St.FAIL -> 0xFFA3120A.toInt() to 0xFFFFFFFF.toInt()
                else -> 0xFFFFD24A.toInt() to 0xFF161616.toInt()
            }
            c.drawRoundRect(r, ch / 2, ch / 2, fill(bg))
            text(chip!!, r.left + zs / 2, midBase(r.top, ch, zs, KO700), zs, KO700, tc, halo = false)
        }
    }

    // ---------- mic ----------

    private fun micGlyph(cx: Float, cy: Float, size: Float, color: Int, dots: Boolean) {
        c.save(); c.translate(cx - size / 2, cy - size / 2); c.scale(size / 24f, size / 24f)
        if (dots) listOf(5f, 12f, 19f).forEach { c.drawCircle(it, 12f, 2.4f, fill(color)) }
        else {
            c.drawRoundRect(RectF(9f, 2.5f, 15f, 14.5f), 3f, 3f, fill(color))
            val q = fill(color).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; strokeCap = Paint.Cap.ROUND }
            c.drawArc(RectF(5.5f, 5f, 18.5f, 18f), 0f, 180f, false, q)
            c.drawLine(12f, 18f, 12f, 21.5f, q)
        }
        c.restore()
    }

    /** The 말하기 button; off = outlined circle, muted glyph, diagonal bar and "켜기" (tap opens the permission / pack step). */
    private fun mic(l: Line, r: RectF) {
        val d = r.width()
        val box = Path().apply { addOval(r, Path.Direction.CW) }
        val cx = r.centerX(); val cy = r.centerY()
        if (!micOn) {
            c.drawCircle(cx, cy, d / 2 - .75f, fill(fg.alpha(.38f)).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f })
            val gy = cy - d * .1f
            micGlyph(cx, gy, d * .38f, mu, false)
            c.save(); c.rotate(-45f, cx, gy); c.drawRect(cx - d * .22f, gy - .75f, cx + d * .22f, gy + .75f, fill(mu)); c.restore()
            val z = d * .2f
            text("켜기", cx - measure("켜기", z, KO700) / 2, cy + d * .3f, z, KO700, fg)
            return
        }
        val acc = style.acc or (0xFF shl 24)
        if (shape == Shape.DOT) {
            if (a > 0) {
                shadow(box, r, d / 2, 0f, 2f, 4f, 0f, black(.5f * a))
                c.drawOval(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF3B3B40.toInt(), 0xFF1A1A1C.toInt(), Shader.TileMode.CLAMP) })
                c.save(); c.clipPath(box); edge(box, 1f, white(.2f)); c.restore()
            } else c.drawOval(r, fill(0xFF2A2A2E.toInt()))
            c.drawCircle(cx, cy, d / 2 - .5f, fill(white(.1f)).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
            c.drawCircle(r.right - d * .15f - 3f, r.top + d * .15f + 3f, 3f, fill(0xFFFF5A36.toInt()))
        } else {
            if (a > 0) { shadow(box, r, d / 2, 0f, 1f, 2f, 0f, black(.2f * a)); shadow(box, r, d / 2, 0f, 5f, 9f, -5f, black(.4f * a)) }
            c.drawOval(r, fill(acc))
            c.drawOval(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, r.top, 0f, r.top + d * .55f, white(.22f), white(0f), Shader.TileMode.CLAMP) })
            // a ring when the button would melt into what is behind it
            if (a < .5f || Contrast.ratio(acc, look.bg or (0xFF shl 24)) < 3.0)
                c.drawCircle(cx, cy, d / 2 - .75f, fill(fg.alpha(.9f)).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f })
        }
        micGlyph(cx, cy, d * .5f, micIcon, l.st == St.PEND)
    }

    // ---------- layout ----------

    /** One car's place: [r] the cell (with its padding), [t] the content box. Decor reads [mic], [nameBottom], [side]. */
    private class Cell(val l: Line, val more: Int, val r: RectF, val t: RectF, val bc: Boolean) {
        var mic = RectF(); var nameBottom = 0f; var side = false
        val ops = ArrayList<() -> Unit>()
    }

    /** Card corner radius: the style's, capped to [R_MAX] of the widget's short side so a one-row card keeps room for its text; set in [draw]. */
    private var wr = style.radiusDp.toFloat()
    val cardRadius get() = wr

    /** [lines] = the cars this size shows (main first, at most two); [more] = cars left out ("외 N대"). */
    fun draw(lines: List<Line>, more: Int, w: Float, h: Float) {
        val g = geo[0]
        wr = min(style.radiusDp.toFloat(), R_MAX * min(w, h))
        // with a card, content at the widget's edge keeps clear of the corner curve (no card at 0 %: layout unchanged; tickets have their own small corners)
        val cin = if (a > 0 && shape != Shape.TICKET) ceil(CURVE * wr) + 1f else 0f
        val cols = when { lines.size == 1 -> listOf(lines); w >= WIDE -> lines.map { listOf(it) }; else -> listOf(lines) }
        val nc = cols.size; val cw = (w - g * (nc - 1)) / nc
        val cells = ArrayList<Cell>()
        cols.forEachIndexed { ci, col ->
            val nr = col.size; val ch = (h - g * (nr - 1)) / nr
            col.forEachIndexed { ri, l ->
                val x = ci * (cw + g); val y = ri * (ch + g)
                val pl = if (ci > 0) geo[5] else max(PAD, cin); val pr = if (ci < nc - 1) geo[5] else max(PAD, cin)
                val pt = if (ri > 0) geo[2] else max(geo[1], cin); val pb = if (ri < nr - 1) geo[4] else max(geo[3], cin)
                val bc = shape == Shape.TICKET && a > 0 && ch < TALL && cw - pl - pr >= 220
                val bx = if (bc) 19f else 0f
                cells += Cell(l, more, RectF(x, y, x + cw, y + ch), RectF(x + pl + bx, y + pt, x + cw - pr, y + ch - pb), bc)
            }
        }
        cells.forEach { plan(it) }
        if (a > 0) {
            if (shape != Shape.TICKET) card(RectF(0f, 0f, w, h), wr)
            cells.forEachIndexed { i, cl -> decor(cl, i, cells.size, w, h) }
            dividers(cols.size, cells, w, h)
        }
        cells.forEach { cl ->
            cl.ops.forEach { it() }
            mics += RectF(cl.mic)
            mic(cl.l, cl.mic)
            // a side mic owns its column of the cell (easier to hit); on 기둥 띠 the name row above it stays the car's
            val m = if (cl.side && !onBand) RectF(cl.mic.left - 4f, cl.r.top, cl.r.right, cl.r.bottom) else RectF(cl.mic)
            hits += Hit(cl.l.car.id, RectF(cl.r), "ROW")
            hits += Hit(cl.l.car.id, m, "MIC")
        }
    }

    /** Mockup tile(): the layout is picked from the content box, so one car fills a 2x1, a strip or a 4x2. */
    private fun plan(cl: Cell) {
        val t = cl.t; val x0 = t.left; val y0 = t.top; val w = t.width(); val hb = t.height(); val h = hb - xh; val l = cl.l
        fun spread(vararg hs: Float): FloatArray {
            val gap = max(0f, (hb - hs.sum()) / (hs.size - 1)); var y = y0
            return FloatArray(hs.size) { i -> val v = y; y += hs[i] + gap; v }
        }
        val tall = h >= TALL
        // small text: font scale, and a little more in cells far bigger than a phone's (tablets) so the floor does not dwarf it
        val k = min(1.6f, textScale * (if (tall) h / 184f else h / 84f).coerceIn(1f, 1.5f))
        val z1 = 13f * k; val l1 = 15f * k   // one-row name line: font, line height
        val z2 = 15f * k; val l2 = 17f * k   // tall name line
        val l3 = 17f * k
        val band = onBand
        if (hb < SHORT) {   // compact: name / floor + pillar + memo, mic as tall as the cell allows
            val d = max(min(MIC_MIN, cl.r.height()), min(56f, hb))
            val my = max(cl.r.top, cl.r.centerY() - d / 2)
            cl.mic = RectF(x0 + w - d, my, x0 + w, my + d)
            cl.side = true
            val lcw = w - d - 6f
            val rest = max(14f, hb - l1 - xh)
            val ys = spread(l1 + xh, rest)
            cl.nameBottom = ys[0] + l1
            cl.ops += {
                nameLine(l, cl.more, x0, ys[0], lcw, z1, band)   // the mic is centred here, so the band text stops before it
                val fz = rest / F_LH
                val used = spotRow(l, x0, ys[1], rest, lcw, fz, fz * .46f, true, k)
                val mx = x0 + used + 8f
                memo(l.memo, mx, midBase(ys[1], rest, z1, bw), z1, x0 + lcw - mx)
            }
        } else if (!tall && w < NARROW) {   // t1: name / floor + mic / pillar + memo
            val mh = h - l1 - l3 - 2
            val d = max(MIC_MIN, floor(min(mh, 56f)))
            val ys = spread(l1 + xh, mh, l3)
            var mt = ys[1] + mh / 2 - d / 2
            mt = min(max(mt, ys[0] + l1), y0 + hb - d)
            cl.mic = RectF(x0 + w - d, mt, x0 + w, mt + d)
            cl.nameBottom = ys[0] + l1
            val l3w = if (cl.mic.bottom > ys[2] + 1f) w - d - 6f else w
            cl.ops += {
                nameLine(l, cl.more, x0, ys[0], w, z1, band)
                spotRow(l, x0, ys[1], mh, w - d - 6f, mh / F_LH, 0f, false, k)
                // the third line is pillar + memo; with a memo the pillar takes at most 55 % so the memo always shows
                val base = midBase(ys[2], l3, z1, bw)
                val pmax = if (l.memo.isNotEmpty()) l3w * .55f else l3w
                val pw = pillar(l, x0, 17f, pmax, ys[2], l3, base)
                memo(l.memo, x0 + pw + (if (pw > 0) 6f else 0f), base, z1, l3w - pw - (if (pw > 0) 6f else 0f))
            }
        } else if (!tall) {   // t2 / t3: content left, mic on the right across the tile height
            val mh = h - l1 - l3 - 2
            var d = Math.round(min(h, min(88f, max(MIC_MIN, w * .3f)))).toFloat()
            if (band) d = min(d, h - 16f)
            d = max(MIC_MIN, d)
            val my = if (band) y0 + hb - d else y0 + (hb - d) / 2
            cl.mic = RectF(x0 + w - d, my, x0 + w, my + d)
            cl.side = true
            val lcw = w - d - 8f
            val nw = if (band) w else lcw
            if (w < WIDE_ONE) {   // t2: name / floor + pillar / memo
                val ys = spread(l1 + xh, mh, l3)
                cl.nameBottom = ys[0] + l1
                cl.ops += {
                    nameLine(l, cl.more, x0, ys[0], nw, z1, band)
                    val fz = mh / F_LH
                    spotRow(l, x0, ys[1], mh, lcw, fz, fz * .46f, true, k)
                    memo(l.memo, x0, midBase(ys[2], l3, z1, bw), z1, lcw)
                }
            } else {   // t3: big floor, pillar + memo stacked beside it
                val bh = h - l1 - 2
                val ys = spread(l1 + xh, bh)
                cl.nameBottom = ys[0] + l1
                cl.ops += {
                    nameLine(l, cl.more, x0, ys[0], nw, z1, band)
                    val bottom = ys[1] + bh
                    if (!spotShown(l)) spotRow(l, x0, ys[1], bh, lcw, bh / F_LH, 0f, false, k)
                    else {
                        val (fz, _) = fit(l.f, "", min(F_MAX, bh / F_LH), 0f, 0f, lcw - 10f - 96f)
                        setText(fz, if (shape == Shape.DOT) doto else fFace, 0f)
                        val r = Rect(); p.getTextBounds(l.f, 0, l.f.length, r)
                        val fl = if (a > 0) 4f else 0f   // the text box reaches below the baseline: keep it out of the card's bottom corners
                        val fw = spotDraw(squeeze(l.f, fz, true, lcw - 106f), x0, bottom - r.bottom - 1f - fl, fz, true, fColor)
                        val px = x0 + fw + 10f; val pw = lcw - fw - 10f
                        val mz = 14f * k
                        setText(mz, bw, 0f); val fm = p.fontMetrics
                        val mBase = bottom - fm.descent
                        val hasMemo = l.memo.isNotEmpty()
                        if (hasMemo) memo(l.memo, px, mBase, mz, pw)
                        pillar(l, px, 34f, pw, 0f, 0f, if (hasMemo) mBase + fm.ascent - 4f else bottom - 2f)
                    }
                }
            }
        } else if (w < NARROW) {   // tn: name / floor across / pillar + mic / memo
            val fh = max(20f, h - l2 - MIC_MIN - l3 - 3)
            val ys = spread(l2 + xh, fh, MIC_MIN, l3)
            cl.mic = RectF(x0 + w - MIC_MIN, ys[2], x0 + w, ys[2] + MIC_MIN)
            cl.nameBottom = ys[0] + l2
            cl.ops += {
                nameLine(l, cl.more, x0, ys[0], w, z2, band)
                spotRow(l, x0, ys[1], fh, w, fh / F_LH, 0f, false, k)
                pillar(l, x0, 38f, w - MIC_MIN - 6f, ys[2], MIC_MIN)
                memo(l.memo, x0, midBase(ys[3], l3, z2, bw), z2, w)
            }
        } else {   // tx: name / floor + pillar / memo (two lines) + mic
            val bot = 64f
            val mh = max(20f, h - l2 - bot - 6)
            val ys = spread(l2 + xh, mh, bot)
            cl.mic = RectF(x0 + w - bot, ys[2], x0 + w, ys[2] + bot)
            cl.nameBottom = ys[0] + l2
            cl.ops += {
                nameLine(l, cl.more, x0, ys[0], w, z2, band)
                val fz = min(F_MAX, mh / F_LH)
                spotRow(l, x0, ys[1], mh, w, fz, ((w - 1.2f * fz - 12f) / 2.3f).coerceIn(28f, 64f), true, k)
                memo2(l.memo, x0, ys[2], bot, (if (w < WIDE_ONE) 17f else 20f) * min(k, 1.2f), w - bot - 6f)
            }
        }
    }

    private fun band(i: Int): Int {
        var c = (if (i % 2 == 0) style.fl1 else style.fl2) or (0xFF shl 24)
        while (Contrast.ratio(Contrast.WHITE, c) < 7) c = Contrast.blend(0xFF000000.toInt(), .1, c)
        return c
    }

    /** What makes the shape, drawn behind the content (mockup deco()), faded with the strength. */
    private fun decor(cl: Cell, i: Int, n: Int, w: Float, h: Float) {
        val r = cl.r
        val whole = RectF(0f, 0f, w, h)
        when (shape) {
            Shape.PILLAR -> {
                val holder = if (n > 1) {
                    val box = rrPath(r, 8f)
                    c.save(); c.clipPath(rrPath(whole, wr))   // cells at the widget corners stay inside the card's curve
                    shadow(box, r, 8f, 0f, 1f, 2f, 0f, black(.14f * a))
                    c.drawPath(box, fill(white((if (look.dk) .09f else .62f) * a)))
                    c.restore()
                    box
                } else rrPath(whole, wr)
                c.save(); c.clipPath(holder); c.clipPath(rrPath(whole, wr))
                val br = RectF(r.left, r.top, r.right, cl.nameBottom + 3f)
                c.drawRect(br, fill(band(i).alpha(a)))
                c.drawRect(br, Paint().apply { shader = LinearGradient(0f, br.top, 0f, br.bottom, white(.06f * a), black(.12f * a), Shader.TileMode.CLAMP) })
                c.restore()
            }
            Shape.DOT -> {
                val pr = RectF(r).apply { inset(3f, 3f) }
                val rad = max(4f, wr - 5f)
                val hole = Path().apply { addCircle(cl.mic.centerX(), cl.mic.centerY(), cl.mic.width() / 2 + 5f, Path.Direction.CW) }
                val box = Path().apply { op(rrPath(pr, rad), hole, Path.Op.DIFFERENCE) }
                c.save(); c.clipPath(box)
                val dot = fill(fg.alpha(.13f * a))
                var y = pr.top + 3f
                while (y < pr.bottom) { var x = pr.left + 3f; while (x < pr.right) { c.drawCircle(x, y, 1.15f, dot); x += 6f }; y += 6f }
                c.drawPaint(fill(if (look.dk) black(.5f * a) else white(.5f * a)))
                c.restore()
                innerShadow(box, pr, 0f, 1f, 3f, black(.45f * a))
            }
            Shape.TICKET -> {
                val rad = min(wr, 14f)
                val tear = cl.mic.left - 4f
                val notches = if (cl.side) listOf(tear to r.top, tear to r.bottom) else listOf(r.left to cl.nameBottom + 1.5f, r.right to cl.nameBottom + 1.5f)
                val cut = Path().apply { notches.forEach { (x, y) -> addCircle(x, y, 4.8f, Path.Direction.CW) } }
                val path = Path().apply { op(rrPath(r, rad), cut, Path.Op.DIFFERENCE) }
                c.save(); c.clipOutPath(path)
                c.drawPath(Path(path).apply { offset(0f, 2f) }, fill(black(.24f * a)).apply { maskFilter = BlurMaskFilter(sigmaRadius(3f), BlurMaskFilter.Blur.NORMAL) })
                c.restore()
                surface(path, r)
                val dash = fill(fg.alpha(.36f * a)).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(4.5f, 3f), 0f) }
                if (cl.side) c.drawLine(tear, r.top + 6f, tear, r.bottom - 6f, dash)
                else c.drawLine(r.left + 7f, cl.nameBottom + 1.5f, r.right - 7f, cl.nameBottom + 1.5f, dash)
                if (cl.bc) {
                    val bar = fill(fg.alpha(.6f * a))
                    val top = r.top + 10f; val bot = r.bottom - 10f
                    var by = top
                    while (by < bot) {
                        for ((s, e) in listOf(0f to 1.5f, 3f to 4f, 6.5f to 9f)) if (by + s < bot) c.drawRect(r.left + 9f, by + s, r.left + 20f, min(bot, by + e), bar)
                        by += 10.5f
                    }
                }
            }
            Shape.ROW -> {}
        }
    }

    /** Lines between cars: ROW hairline, DOT red dots; PILLAR and TICKET are apart by their own panels. Faded with the strength. */
    private fun dividers(nc: Int, cells: List<Cell>, w: Float, h: Float) {
        if (cells.size < 2) return
        val g = geo[0]
        val vertical = nc > 1
        val pos = if (vertical) cells[0].r.right else cells[0].r.bottom
        val hr = fg.alpha(.16f + .18f * (1 - a))
        when (shape) {
            Shape.ROW -> if (vertical) c.drawRect(pos, 8f, pos + g, h - 8f, fill(hr)) else c.drawRect(8f, pos, w - 8f, pos + g, fill(hr))
            Shape.DOT -> {
                val dot = fill(0xFFFF5A36.toInt().alpha(a)); val mid = pos + g / 2
                if (vertical) { var y = 12f; while (y < h - 9f) { c.drawCircle(mid, y, 1.2f, dot); y += 6f } }
                else { var x = 12f; while (x < w - 9f) { c.drawCircle(x, mid, 1.2f, dot); x += 6f } }
            }
            else -> {}
        }
    }
}
