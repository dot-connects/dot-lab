package kr.dot.wherepark.widget

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Style
import kr.dot.wherepark.ui.AssetFonts

/** One car line as the widget shows it (spec 8.3). [f] floor, [s] zone/number; [who]/[time] feed the "who when" line. */
internal enum class St { OK, NONE, PEND, GONE }
internal data class Line(val car: Car, val f: String, val s: String, val st: St, val who: String, val time: String)

/** Text box drawn, in card dp, for tools/contrast.py. */
internal data class TextBox(val rect: RectF, val text: String, val base: Float = rect.bottom)

/**
 * Readable colours at any strength, ported from the mockup's fitText (round2/src.html, v10): the surface at [wop] over the
 * wallpaper samples; if the theme's text does not reach 4.5:1, nudge the surface toward a pole and add a scrim one step at a
 * time (pass 0 keeps the theme polarity with a light scrim, pass 1 may flip the text). [big] = accent numerals moved toward
 * the text pole until 4.5:1 (poster; the mockup used 3:1 for large text, spec 8.2 asks 4.5 for all text). Below 60 % the text gets a halo.
 */
internal data class Look(
    val bg: Int, val a: Float, val fg: Int, val mu: Int, val dk: Boolean, val scrim: Int, val big: Int, val halo: Boolean, val score: Double,
) {
    val whi: Float get() = if (dk) .07f else .55f

    companion object {
        private val BLACK = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        private val WHITE = doubleArrayOf(255.0, 255.0, 255.0, 1.0)
        private val DARK = doubleArrayOf(17.0, 17.0, 17.0, .78)
        private val LIGHT = doubleArrayOf(255.0, 255.0, 255.0, .88)

        private fun rgba(c: Int) = doubleArrayOf((c shr 16 and 255).toDouble(), (c shr 8 and 255).toDouble(), (c and 255).toDouble(), (c ushr 24) / 255.0)
        private fun lin(v: Double): Double { val s = v / 255; return if (s <= .03928) s / 12.92 else ((s + .055) / 1.055).pow(2.4) }
        private fun lum(c: DoubleArray) = .2126 * lin(c[0]) + .7152 * lin(c[1]) + .0722 * lin(c[2])
        private fun cr(a: DoubleArray, b: DoubleArray): Double { val x = lum(a); val y = lum(b); return (max(x, y) + .05) / (min(x, y) + .05) }
        private fun over(top: DoubleArray, a: Double, bot: DoubleArray) = DoubleArray(3) { top[it] * a + bot[it] * (1 - a) }
        private fun argb(c: DoubleArray, a: Double = 1.0): Int =
            ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (c[0].roundToInt().coerceIn(0, 255) shl 16) or
                (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)

        fun of(style: Style, wop: Double, samples: List<Int>): Look {
            val b = rgba(style.bg); val f0 = rgba(style.fg); val m0 = rgba(style.mu); val acc = rgba(style.acc)
            val a = wop * b[3]
            val ws = samples.ifEmpty { listOf(0xFF808080.toInt()) }.map(::rgba)
            fun pol(f: DoubleArray) = lum(f) > .4
            val cands = listOf(f0 to m0, f0 to (if (pol(f0)) LIGHT else DARK), DARK to DARK, LIGHT to LIGHT)
            class E(val f: DoubleArray, val m: DoubleArray, val lt: Boolean, val bp: DoubleArray, val sc: Double, val pole: DoubleArray, val bgs: List<DoubleArray>, val score: Double)
            fun worst(col: DoubleArray, bgs: List<DoubleArray>) = bgs.minOf { bg -> cr(over(col, col.getOrElse(3) { 1.0 }, bg), bg) }
            var best: E? = null
            var pick: E? = null
            search@ for ((set, maxL) in listOf(cands.take(2) to 4, cands to 9)) for (l in 0..maxL) for ((f, m) in set) {
                val lt = pol(f); val pole = if (lt) BLACK else WHITE
                val bp = over(pole, l * .1, b); val sc = l * .07
                val bgs = ws.map { w -> over(bp, a, over(pole, sc, w)) }
                val score = min(worst(doubleArrayOf(f[0], f[1], f[2], 1.0), bgs), worst(m, bgs))
                val e = E(f, m, lt, bp, sc, pole, bgs, score)
                if (best == null || score > best.score) best = e
                if (score >= 4.5) { pick = e; break@search }
            }
            val r = pick ?: best!!
            val fg = argb(r.f)
            var big = fg
            var t = 0.0
            while (t <= 1.001) {
                val c = over(if (r.lt) WHITE else BLACK, t, acc)
                // also on the poster strips, which tint the surface with 5 % text colour
                if ((r.bgs + r.bgs.map { over(r.f, .05, it) }).minOf { cr(c, it) } >= 4.5) { big = argb(c); break }
                t += .15
            }
            return Look(argb(r.bp), wop.toFloat(), fg, argb(r.m, r.m.getOrElse(3) { 1.0 }), r.lt,
                if (r.sc > 0 && wop < .995) argb(r.pole, r.sc) else 0, big, wop < .6, r.score)
        }
    }
}

internal fun Int.alpha(a: Float): Int = (((this ushr 24) * a).roundToInt().coerceIn(0, 255) shl 24) or (this and 0xFFFFFF)
private fun white(a: Float) = 0xFFFFFFFF.toInt().alpha(a)
private fun black(a: Float) = 0xFF000000.toInt().alpha(a)

/**
 * Canvas drawing of the five widget shapes from the mockup (round2/src.html W()/W1(), v10 CSS), in card dp: mockup px = dp.
 * Positions and baselines are the mockup's measured values (Chrome layout of final/preview.html); right-side parts are anchored
 * to the card's right edge so wider cards stretch the middle like the CSS grids do.
 */
internal class Shapes(
    private val ctx: Context, private val c: Canvas, private val style: Style, private val look: Look, private val shape: Shape,
    private val micOn: Boolean,
) {
    val hits = ArrayList<Hit>()
    val texts = ArrayList<TextBox>()
    private val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val isDefault = style.family == "default"
    private val mat = if (isDefault) "base" else style.material
    private val fg = look.fg
    private val mu = look.mu
    private val line = look.fg.alpha(.13f)
    private val micIcon = if (shape == Shape.DOT) 0xFFF2F2F2.toInt() else if (isDefault) 0xFFFFFFFF.toInt() else style.onAcc

    companion object {
        const val NOTE = "마이크·음성팩이 필요해요 · 눌러서 켜기"
        private val tfCache = HashMap<String, Typeface>()
        private val KO400: Typeface = Typeface.DEFAULT
        private val KO600: Typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        private val KO700: Typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        private val MONO: Typeface = Typeface.MONOSPACE
        private val DEFAULT_BANDS = intArrayOf(0xFF2F7D4F.toInt(), 0xFF2D5DA8.toInt(), 0xFFB5562B.toInt(), 0xFF6A4A9C.toInt())

        /** Natural card size in dp (mockup measurements): width is the minimum, the card may be drawn wider. */
        fun natural(shape: Shape, one: Boolean, n: Int, micOn: Boolean): Pair<Float, Float> {
            val note = if (micOn || one) 0f else when (shape) { Shape.ROW -> 28.6f; Shape.TICKET -> 35f; else -> 30.6f }
            if (one) return when (shape) {
                Shape.ROW -> 150f to 111.4f; Shape.POSTER -> 164f to 104f; Shape.PILLAR -> 150f to 123.2f
                Shape.DOT -> 150f to 111.3f; Shape.TICKET -> 150f to 113.2f
            }
            val h = when (shape) {
                Shape.ROW -> n * 57f + 5f
                Shape.POSTER -> 80f + (n - 1) * 46f
                Shape.PILLAR -> { val rows = ceil(n / 2.0).toInt(); 16f + rows * 97.6f + (rows - 1) * 7f }
                Shape.DOT -> 16f + n * 50f + (n - 1) * 5f
                Shape.TICKET -> n * 58f + (n - 1) * 5f
            }
            return 280f to h + note
        }
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
        c.drawPaint(fill(look.bg.alpha(look.a)))
        val w = look.whi; val a = look.a
        when (mat) {
            "base" -> c.drawRect(r, Paint().apply { shader = linear(r, 180f, intArrayOf(white(w * a), white(0f)), floatArrayOf(0f, .44f)) })
            "gloss" -> c.drawRect(r, Paint().apply { shader = linear(r, 180f, intArrayOf(white(w * .95f * a), white(w * .16f * a), white(0f)), floatArrayOf(0f, .48f, .52f)) })
            "metal" -> c.drawRect(r, Paint().apply { shader = linear(r, 160f, intArrayOf(white(w * .7f * a), white(0f), black(.09f)), floatArrayOf(0f, .42f, 1f)) })
        }
        c.restore()
    }

    /** Card = outer shadows + surface + inset edges + 1-px border, per material (CSS .wx and .wx[data-mat]). */
    private fun card(r: RectF, rad: Float) {
        val box = rrPath(r, rad)
        val w = look.whi
        when (mat) {
            "matte" -> { shadow(box, r, rad, 0f, 1f, 2f, 0f, black(.14f)); shadow(box, r, rad, 0f, 10f, 20f, -10f, black(.35f)) }
            "gloss" -> shadow(box, r, rad, 0f, 12f, 26f, -10f, black(.45f))
            "paper" -> { shadow(box, r, rad, 0f, 1f, 1f, 0f, black(.12f)); shadow(box, r, rad, 0f, 6f, 10f, -6f, black(.28f)) }
            "metal" -> shadow(box, r, rad, 0f, 10f, 22f, -10f, black(.45f))
            "soft" -> { shadow(box, r, rad, -4f, -4f, 10f, 0f, white(.4f)); shadow(box, r, rad, 6f, 7f, 14f, 0f, black(.24f)) }
            else -> { shadow(box, r, rad, 0f, 1f, 2f, 0f, black(.10f)); shadow(box, r, rad, 0f, 12f, 24f, -12f, black(.38f)) }
        }
        surface(box, r)
        c.save(); c.clipPath(box)
        when (mat) {
            "gloss" -> { edge(box, 1f, white(min(1f, w * 1.7f))); innerShadow(box, r, 0f, -2f, 6f, black(.12f)) }
            "paper" -> ring(r, rad, 3f, 1f, fg.alpha(.12f))
            "metal" -> { edge(box, 1f, white(min(1f, w * 1.3f))); edge(box, -1f, black(.2f)) }
            "soft" -> ring(r, rad, 0f, 1f, white(.14f))
            "matte" -> {}
            else -> edge(box, 1f, white(min(1f, w * 1.5f)))
        }
        c.restore()
        ring(r, rad, 0f, 1f, fg.alpha(if (mat == "paper") .16f else .06f))
    }

    /** Text at a baseline; halo below 60 % (CSS --wts). Returns the advance width. */
    private fun text(s: String, x: Float, base: Float, size: Float, tf: Typeface, color: Int, ls: Float = 0f, right: Boolean = false,
                     stroke: Float = 0f, halo: Boolean = look.halo, record: Boolean = true): Float {
        if (s.isEmpty()) return 0f
        setText(size, tf, ls)
        val w = p.measureText(s)
        val x0 = if (right) x - w else x
        if (halo) {
            val passes = if (look.dk) listOf(Triple(3f, 1f, black(.45f)), Triple(2f, 0f, black(.6f)))
            else listOf(Triple(5f, 0f, white(.55f)), Triple(2f, 0f, white(.8f)))
            for ((blur, dy, col) in passes) {
                p.color = col; p.maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL); p.style = Paint.Style.FILL
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

    private fun setText(size: Float, tf: Typeface, ls: Float) { p.reset(); p.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG; p.typeface = tf; p.textSize = size; p.letterSpacing = ls }
    private fun measure(s: String, size: Float, tf: Typeface, ls: Float = 0f): Float { setText(size, tf, ls); return p.measureText(s) }
    private fun clip(s: String, size: Float, tf: Typeface, max: Float): String { setText(size, tf, 0f); return TextUtils.ellipsize(s, p, max, TextUtils.TruncateAt.END).toString() }

    /** Floor or zone text. DOT: digits in Doto with a .55 stroke, letters in heavy Inter Tight (a dotted B reads as 8). */
    private fun spotText(s: String, x: Float, base: Float, size: Float, weight: Int, color: Int, ls: Float, sub: Boolean): Float {
        if (shape != Shape.DOT) return text(s, x, base, size, num(weight), color, ls)
        var cx = x
        Regex("\\d+|\\D+").findAll(s).forEach { m ->
            val run = m.value
            cx += if (run[0].isDigit() || run == "–") text(run, cx, base, size, face("Doto", 900), color, 0f, stroke = .55f)
            else text(run, cx, base, size * (if (sub) .92f else .84f), face("Inter Tight", 900), color, -.02f) + (if (sub) 1f else 2f)
        }
        return cx - x
    }

    private fun spotWidth(s: String, size: Float, weight: Int, ls: Float, sub: Boolean): Float {
        if (shape != Shape.DOT) return measure(s, size, num(weight), ls)
        return Regex("\\d+|\\D+").findAll(s).sumOf { m ->
            val run = m.value
            (if (run[0].isDigit() || run == "–") measure(run, size, face("Doto", 900)) else measure(run, size * (if (sub) .92f else .84f), face("Inter Tight", 900), -.02f) + (if (sub) 1f else 2f)).toDouble()
        }.toFloat()
    }

    /** Car-colour dot (always outlined, a white car shows on a light widget) + bold name. */
    private fun name(l: Line, x: Float, base: Float, right: Boolean = false, onBand: Boolean = false, max: Float = 120f) {
        val nm = clip(l.car.name, 12f, KO700, max - 13f)
        val tw = measure(nm, 12f, KO700)
        val x0 = if (right) x - (13f + tw) else x
        val cy = base - 4.2f
        c.drawCircle(x0 + 4f, cy, 5f, fill(if (onBand) white(.7f) else fg.alpha(.48f)))
        c.drawCircle(x0 + 4f, cy, 4f, fill(l.car.color or (0xFF shl 24)))
        text(nm, x0 + 13f, base, 12f, KO700, if (onBand) 0xFFFFFFFF.toInt() else fg, halo = !onBand && look.halo)
    }

    /** "아빠 14:32" (who bold in fg, when in muted), "↑ 전송 대기", "기록 없음", "엄마 출발". */
    private fun who(l: Line, x: Float, base: Float, right: Boolean = false, mono: Boolean = false, onBand: Boolean = false, max: Float = 999f) {
        val f = if (onBand) 0xFFFFFFFF.toInt() else fg
        val m = if (onBand) 0xFFFFFFFF.toInt() else mu
        val halo = !onBand && look.halo
        val timeFace = if (mono) MONO else KO400
        when (l.st) {
            St.NONE -> text("기록 없음", x, base, 12f, KO400, m, right = right, halo = halo)
            St.PEND -> {
                val tw = measure("전송 대기", 12f, KO400)
                val x0 = if (right) x - tw - 15f else x
                upIcon(x0, base - 10f, m)
                text("전송 대기", x0 + 15f, base, 12f, KO400, m, halo = halo)
            }
            else -> {
                var rest = if (l.st == St.GONE) " 출발" else " ${l.time}"
                var restFace = if (l.st == St.GONE) KO400 else timeFace
                val ls = if (mono) -.02f else 0f
                var whoName = l.who
                // a date ("11월 16일") that does not fit drops the name: when matters more than who on an old record
                if (measure(whoName, 12f, KO600) + measure(rest, 12f, restFace, ls) > max && rest.contains('월')) { whoName = ""; rest = rest.trim(); restFace = KO400 }
                val w1 = measure(whoName, 12f, KO600)
                // narrow slot: "어제 19:05" -> "어제" (the mockup's short form), then an ellipsis
                if (w1 + measure(rest, 12f, restFace, ls) > max && rest.startsWith(" 어제 ")) rest = " 어제"
                if (w1 + measure(rest, 12f, restFace, ls) > max) { setText(12f, restFace, ls); rest = TextUtils.ellipsize(rest, p, max(0f, max - w1), TextUtils.TruncateAt.END).toString() }
                val w2 = measure(rest, 12f, restFace, ls)
                val x0 = if (right) x - w1 - w2 else x
                text(whoName, x0, base, 12f, KO600, f, halo = halo)
                text(rest, x0 + w1, base, 12f, restFace, m, ls, halo = halo)
            }
        }
    }

    /** One-car head line: name on the left, who/when on the right; the name gets at most half when both are long. */
    private fun header(l: Line, x0: Float, x1: Float, nameBase: Float, whoBase: Float, onBand: Boolean = false, mono: Boolean = false) {
        val room = x1 - x0 - 6f
        val nw = min(13f + measure(l.car.name, 12f, KO700), max(room / 2, room - measure("${l.who} ${l.time}", 12f, if (mono) MONO else KO400)))
        name(l, x0, nameBase, onBand = onBand, max = nw)
        who(l, x1, whoBase, right = true, mono = mono, onBand = onBand, max = room - nw)
    }

    private fun upIcon(x: Float, y: Float, color: Int) {
        val s = 12f / 24f
        val q = fill(color).apply { style = Paint.Style.STROKE; strokeWidth = 2.4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val path = Path().apply { moveTo(12f, 19f); lineTo(12f, 6f); moveTo(6.5f, 11.5f); lineTo(12f, 6f); lineTo(17.5f, 11.5f) }
        c.save(); c.translate(x, y); c.scale(s, s); c.drawPath(path, q); c.restore()
    }

    private fun micGlyph(cx: Float, cy: Float, color: Int) {
        c.save(); c.translate(cx - 10f, cy - 10f); c.scale(20f / 24f, 20f / 24f)
        c.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, fill(color))
        val q = fill(color).apply { style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }
        c.drawArc(RectF(6f, 5f, 18f, 17f), 0f, 180f, false, q)
        c.drawLine(12f, 17f, 12f, 21f, q)
        c.restore()
    }

    /** One mic style per widget; off = outlined circle, muted glyph, diagonal bar (spec 8.3 mic/pack needed). */
    private fun mic(l: Line, x: Float, y: Float, d: Float = 44f) {
        val r = RectF(x, y, x + d, y + d)
        val box = Path().apply { addOval(r, Path.Direction.CW) }
        val cx = r.centerX(); val cy = r.centerY()
        hits += Hit(l.car.id, RectF(r), "MIC")
        if (!micOn) {
            c.drawCircle(cx, cy, d / 2 - .75f, fill(fg.alpha(.38f)).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f })
            micGlyph(cx, cy, mu)
            c.save(); c.rotate(-45f, cx, cy); c.drawRect(cx - 13f, cy - .75f, cx + 13f, cy + .75f, fill(mu)); c.restore()
            return
        }
        when (shape) {
            Shape.DOT -> {
                shadow(box, r, d / 2, 0f, 2f, 4f, 0f, black(.5f))
                c.drawOval(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF3B3B40.toInt(), 0xFF1A1A1C.toInt(), Shader.TileMode.CLAMP) })
                c.save(); c.clipPath(box); edge(box, 1f, white(.2f)); c.restore()
                c.drawCircle(cx, cy, d / 2 - .5f, fill(white(.1f)).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
                c.drawCircle(r.right - 11f, r.top + 11f, 3f, fill(0xFFFF5A36.toInt()))
            }
            else -> {
                if (shape == Shape.TICKET) shadow(box, r, d / 2, 0f, 1f, 2f, 0f, black(.25f))
                else { shadow(box, r, d / 2, 0f, 1f, 2f, 0f, black(.2f)); shadow(box, r, d / 2, 0f, 5f, 9f, -5f, black(.4f)) }
                c.drawOval(r, fill(style.acc or (0xFF shl 24)))
                c.drawOval(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, r.top, 0f, r.top + d * .55f, white(.22f), white(0f), Shader.TileMode.CLAMP) })
                if (shape != Shape.TICKET) { c.save(); c.clipPath(box); edge(box, -1f, black(.22f)); c.restore() }
            }
        }
        micGlyph(cx, cy, micIcon)
    }

    private fun row(l: Line, r: RectF) { hits += Hit(l.car.id, r, "ROW") }

    private fun note(x: Float, y: Float, base: Float, w: Float, border: Boolean) {
        if (border) c.drawRect(x, y, x + w, y + 1f, fill(line))
        text(clip(NOTE, 12f, KO400, w), x, base, 12f, KO400, mu)
    }

    // ---------- shapes ----------

    fun draw(lines: List<Line>, one: Boolean, w: Float, h: Float) {
        when (shape) {
            Shape.ROW -> if (one) rowOne(lines[0], w, h) else rowAll(lines, w, h)
            Shape.POSTER -> if (one) posterOne(lines[0], w, h) else posterAll(lines, w, h)
            Shape.PILLAR -> if (one) pillarOne(lines[0], w, h) else pillarAll(lines, w, h)
            Shape.DOT -> if (one) dotOne(lines[0], w, h) else dotAll(lines, w, h)
            Shape.TICKET -> if (one) ticketOne(lines[0], w, h) else ticketAll(lines, w)
        }
    }

    private val wr get() = style.radiusDp.toFloat()

    /** f + s on one baseline (CSS .sp, align-items baseline); empty states per spec 8.3. */
    private fun spotRow(l: Line, x: Float, base: Float, fs: Float, ss: Float, gap: Float, fls: Float = -.03f, sls: Float = -.01f,
                        fw: Int = 900, sw: Int = 800, goneBase: Float = base - 8.4f) {
        when (l.st) {
            St.NONE -> spotText("–", x, base, fs, fw, mu, fls, false)
            St.GONE -> text("운행 중", x, goneBase, 17f, KO700, fg, -.01f)
            else -> {
                val fw0 = spotText(l.f, x, base, fs, fw, fg, fls, false)
                if (l.s.isNotEmpty()) spotText(l.s, x + fw0 + gap, base, ss, sw, fg, sls, true)
            }
        }
    }

    private fun rowAll(lines: List<Line>, w: Float, h: Float) {
        card(RectF(0f, 0f, w, h), wr)
        lines.forEachIndexed { i, l ->
            val top = 9f + i * 57f
            if (i > 0) c.drawRect(14f, top - 7f, w - 64f, top - 6f, fill(line))
            spotRow(l, 14f, top + 35.9f, 38f, 19f, 6f)
            val metaMax = w - 64f - 14f - (if (l.st == St.OK || l.st == St.PEND) spotWidth(l.f, 38f, 900, -.03f, false) + 6f + spotWidth(l.s, 19f, 800, -.01f, true) + 10f else 40f)
            name(l, w - 64f, top + 16.9f, right = true, max = max(40f, metaMax))
            who(l, w - 64f, top + 33.5f, right = true, max = max(40f, metaMax))
            mic(l, w - 54f, top)
            row(l, RectF(0f, max(0f, top - 6.5f), w - 56f, min(h, top + 50.5f)))
        }
        if (!micOn) { val y = 9f + lines.size * 57f - 7f; note(14f, y, y + 18f, w - 24f, true) }
    }

    private fun rowOne(l: Line, w: Float, h: Float) {
        card(RectF(0f, 0f, w, h), wr)
        header(l, 13f, w - 10f, 20f, 20f)
        when (l.st) {
            St.NONE -> spotText("–", 13f, 71.6f, 52f, 900, mu, -.03f, false)
            St.GONE -> text("운행 중", 13f, 60f, 17f, KO700, fg, -.01f)
            else -> { spotText(l.f, 13f, 71.6f, 52f, 900, fg, -.03f, false); spotText(l.s, 13f, 98.4f, 22f, 800, fg, -.01f, true) }
        }
        mic(l, w - 54f, h - 54f)
        row(l, RectF(0f, 0f, w - 56f, h))
    }

    private fun posterAll(lines: List<Line>, w: Float, h: Float) {
        val r = RectF(0f, 0f, w, h)
        card(r, wr)
        c.save(); c.clipPath(rrPath(RectF(1f, 1f, w - 1f, h - 1f), max(0f, wr - 1f)))
        val l0 = lines[0]
        c.save(); c.clipRect(1f, 1f, w - 1f, 79f)
        val big = when (l0.st) { St.NONE -> "–"; St.GONE -> ""; else -> l0.f }
        val bw = if (big.isEmpty()) 0f else text(big, 10f, 80f, 100f, num(900), look.big, -.06f)
        c.restore()
        val hx = 10f + bw + 11f
        val s0 = if (l0.st == St.GONE) "운행 중" else if (l0.st == St.NONE) "" else l0.s
        text(s0, hx, 31.4f, 27f, num(800), fg, -.03f)
        name(l0, hx, 50.4f, max = w - 52f - hx - 4f)
        who(l0, hx, 67f, max = w - 52f - hx - 4f)
        mic(l0, w - 52f, 20f, 40f)
        row(l0, RectF(0f, 0f, w - 54f, 79f))
        lines.drop(1).forEachIndexed { i, l ->
            val y = 79f + i * 46f
            c.drawRect(1f, y, w - 1f, y + 46f, fill(fg.alpha(.05f)))
            c.drawRect(1f, y, w - 1f, y + 1f, fill(line))
            var x = 13f
            when (l.st) {
                St.NONE -> text("–", x, y + 32.3f, 25f, num(900), look.big, -.03f)
                St.GONE -> text("운행 중", x, y + 29.5f, 16f, KO700, fg)
                else -> { x += text(l.f, x, y + 32.3f, 25f, num(900), look.big, -.03f) + 7f; x += text(l.s, x, y + 29.5f, 16f, num(800), fg, -.01f) }
            }
            name(l, w - 61f, y + 18.4f, right = true, max = 110f)
            who(l, w - 61f, y + 35f, right = true, max = w - 61f - x - 8f)
            mic(l, w - 52f, y + 3.5f, 40f)
            row(l, RectF(0f, y, w - 54f, y + 46f))
        }
        if (!micOn) { val y = 80f + (lines.size - 1) * 46f - 1f; note(12f, y, y + 18f, w - 24f, true) }
        c.restore()
    }

    private fun posterOne(l: Line, w: Float, h: Float) {
        card(RectF(0f, 0f, w, h), wr)
        header(l, 13f, w - 11f, 20f, 20f)
        val big = when (l.st) { St.NONE -> "–"; St.GONE -> ""; else -> l.f }
        text(big, 9f, 95.7f, 74f, num(900), look.big, -.06f)
        text(if (l.st == St.GONE) "운행 중" else if (l.st == St.NONE) "" else l.s, w - 12f, 46f, 21f, if (l.st == St.GONE) KO700 else num(800), fg, -.01f, right = true)
        mic(l, w - 53f, 51f)
        row(l, RectF(0f, 0f, w - 56f, h))
    }

    private fun band(l: Line, i: Int): Int = if (isDefault) DEFAULT_BANDS[i % 4] else if (i % 2 == 0) style.fl1 else style.fl2

    private fun pillarAll(lines: List<Line>, w: Float, h: Float) {
        card(RectF(0f, 0f, w, h), wr)
        val pw = (w - 16f - 7f) / 2
        val rad = max(0f, wr - 8f)
        lines.forEachIndexed { i, l ->
            val full = i == lines.size - 1 && i % 2 == 0
            val px = 8f + (i % 2) * (pw + 7f); val py = 8f + (i / 2) * 104.6f
            val pr = RectF(px, py, px + (if (full) w - 16f else pw), py + 97.6f)
            val box = rrPath(pr, rad)
            shadow(box, pr, rad, 0f, 1f, 2f, 0f, black(.14f))
            c.drawPath(box, fill(white((.62f - .53f * (if (look.dk) 1 else 0)) * look.a)))
            c.save(); c.clipPath(box)
            val br = RectF(pr.left, pr.top, pr.right, pr.top + 21f)
            c.drawRect(br, fill(band(l, i)))
            c.drawRect(br, Paint().apply { shader = LinearGradient(0f, br.top, 0f, br.bottom, white(.06f), black(.12f), Shader.TileMode.CLAMP) })
            ring(pr, rad, 0f, 1f, white(look.whi * .6f))
            c.restore()
            name(l, px + 10f, py + 13.7f, onBand = true, max = pr.width() - 20f)
            when (l.st) {
                St.NONE -> text("–", px + 10f, py + 64.8f, 46f, num(900), fg)
                St.GONE -> {}
                else -> text(l.f, px + 10f, py + 64.8f, 46f, num(900), fg)
            }
            mic(l, pr.right - 52f, py + 25f)
            val sx = if (l.st == St.GONE) "운행 중" else if (l.st == St.NONE) "" else l.s
            val sw = text(sx, px + 10f, py + 86f, 18f, if (l.st == St.GONE) KO700 else num(800), fg, -.01f)
            who(l, pr.right - 9f, py + 86f, right = true, max = pr.width() - 19f - sw - 10f)
            row(l, RectF(pr.left, pr.top, pr.right - 54f, pr.bottom))
        }
        if (!micOn) { val y = h - 30.6f + 7f - 8f; note(12f, y, y + 18f, w - 24f, true) }
    }

    private fun pillarOne(l: Line, w: Float, h: Float) {
        val r = RectF(0f, 0f, w, h)
        card(r, wr)
        c.save(); c.clipPath(rrPath(RectF(1f, 1f, w - 1f, h - 1f), max(0f, wr - 1f)))
        val br = RectF(1f, 1f, w - 1f, 24f)
        c.drawRect(br, fill(band(l, 0)))
        c.drawRect(br, Paint().apply { shader = LinearGradient(0f, br.top, 0f, br.bottom, white(.06f), black(.12f), Shader.TileMode.CLAMP) })
        c.restore()
        header(l, 13f, w - 11f, 15.7f, 15.7f, onBand = true)
        when (l.st) {
            St.NONE -> text("–", 14f, 85f, 58f, num(900), mu)
            St.GONE -> text("운행 중", 14f, 70f, 17f, KO700, fg)
            else -> { text(l.f, 14f, 85f, 58f, num(900), fg); text(l.s, 14f, 110.2f, 22f, num(800), fg, -.01f) }
        }
        mic(l, w - 54f, 69.2f)
        row(l, RectF(0f, 0f, w - 56f, h))
    }

    /** LED panel: dot grid every 6 dp + darkening at strength (CSS .wx.kA .led). */
    private fun led(r: RectF) {
        val rad = max(4f, wr - 8f)
        val box = rrPath(r, rad)
        shadow(box, r, rad, 0f, 1f, 0f, 0f, white(.07f))
        c.save(); c.clipPath(box)
        val dot = fill(fg.alpha(.13f))
        var y = r.top + 3f
        while (y < r.bottom) { var x = r.left + 3f; while (x < r.right) { c.drawCircle(x, y, 1.15f, dot); x += 6f }; y += 6f }
        val dk = if (look.dk) 1f else 0f
        c.drawPaint(fill(black(.5f * dk * look.a)))
        c.drawPaint(fill(white(.5f * (1 - dk) * look.a)))
        c.restore()
        innerShadow(box, r, 0f, 1f, 3f, black(.45f))
    }

    private fun dotAll(lines: List<Line>, w: Float, h: Float) {
        card(RectF(0f, 0f, w, h), wr)
        val n = lines.size
        led(RectF(8f, 8f, w - 59f, 8f + n * 50f + (n - 1) * 5f))
        lines.forEachIndexed { i, l ->
            val ty = 8f + i * 55f
            spotRow(l, 18f, ty + 36.8f, 36f, 21f, 6f, 0f, 0f)
            val meta = w - 68f - 18f - 10f - (if (l.st == St.OK || l.st == St.PEND) spotWidth(l.f, 36f, 900, 0f, false) + 6f + spotWidth(l.s, 21f, 900, 0f, true) else 50f)
            name(l, w - 68f, ty + 19.9f, right = true, max = max(40f, meta))
            who(l, w - 68f, ty + 37.5f, right = true, mono = true, max = max(40f, meta))
            if (i < n - 1) {
                val dot = fill(0xFFFF5A36.toInt())
                var x = 17f
                while (x < w - 64f) { c.drawCircle(x, ty + 52.5f, 1.3f, dot); x += 6f }
            }
            mic(l, w - 52f, ty + 3f)
            row(l, RectF(0f, max(0f, ty - 2.5f), w - 54f, ty + 52.5f))
        }
        if (!micOn) { val y = 8f + n * 50f + (n - 1) * 5f; note(12f, y, y + 17f, w - 24f, false) }
    }

    private fun dotOne(l: Line, w: Float, h: Float) {
        card(RectF(0f, 0f, w, h), wr)
        led(RectF(7f, 7f, w - 57f, h - 7f))
        name(l, 15f, 25f, max = w - 72f)
        when (l.st) {
            St.NONE -> spotText("–", 15f, 68.6f, 46f, 900, mu, 0f, false)
            St.GONE -> text("운행 중", 15f, 60f, 17f, KO700, fg)
            else -> { spotText(l.f, 15f, 68.6f, 46f, 900, fg, 0f, false); spotText(l.s, 15f, 93f, 21f, 900, fg, 0f, true) }
        }
        val cx = w - 29f
        val (a, b) = when (l.st) { St.NONE -> "기록" to "없음"; St.PEND -> "전송" to "대기"; St.GONE -> l.who to "출발"; St.OK -> l.who to l.time }
        text(a, cx - measure(a, 12f, KO600) / 2, 22f, 12f, KO600, fg)
        val bf = if (l.st == St.OK) MONO else KO400
        text(b, cx - measure(b, 12f, bf, -.02f) / 2, 37.6f, 12f, bf, mu, -.02f)
        mic(l, w - 51f, h - 51f)
        row(l, RectF(0f, 0f, w - 54f, h))
    }

    private fun ticketPath(r: RectF, holes: List<Pair<Float, Float>>): Path {
        val rad = min(wr, 14f)
        val path = rrPath(r, rad)
        val cut = Path().apply { holes.forEach { (x, y) -> addCircle(x, y, 4.8f, Path.Direction.CW) } }
        return Path().apply { op(path, cut, Path.Op.DIFFERENCE) }
    }

    /** One printed ticket: drop shadow following the punched notches, then the surface (CSS .wx.kP .tk). */
    private fun ticket(path: Path, r: RectF) {
        c.save(); c.clipOutPath(path)
        c.drawPath(Path(path).apply { offset(0f, 2f) }, fill(black(.24f)).apply { maskFilter = BlurMaskFilter(sigmaRadius(3f), BlurMaskFilter.Blur.NORMAL) })
        c.restore()
        surface(path, r)
    }

    private fun dash(x0: Float, y0: Float, x1: Float, y1: Float) {
        c.drawLine(x0, y0, x1, y1, fill(fg.alpha(.36f)).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(4.5f, 3f), 0f) })
    }

    private fun ticketAll(lines: List<Line>, w: Float) {
        val tear = w - 134f
        lines.forEachIndexed { i, l ->
            val ty = i * 63f
            val r = RectF(0f, ty, w, ty + 58f)
            ticket(ticketPath(r, listOf(tear to ty, tear to ty + 58f)), r)
            val bar = fill(fg.alpha(.6f))
            var by = ty + 10f
            while (by < ty + 48f) {
                for ((a, b) in listOf(0f to 1.5f, 3f to 4f, 6.5f to 9f)) if (by + a < ty + 48f) c.drawRect(10f, by + a, 21f, min(ty + 48f, by + b), bar)
                by += 10.5f
            }
            when (l.st) {
                St.NONE -> text("–", 30f, ty + 44f, 40f, num(700), mu, -.06f)
                St.GONE -> text("운행 중", 30f, ty + 36f, 17f, KO700, fg)
                else -> { val fw = text(l.f, 30f, ty + 44f, 40f, num(700), fg, -.06f); text(l.s, 30f + fw + 6f, ty + 44f, 19f, num(600), fg, -.03f) }
            }
            dash(tear + .75f, ty, tear + .75f, ty + 58f)
            name(l, tear + 11f, ty + 23.9f, max = w - 57f - tear - 11f)
            who(l, tear + 11f, ty + 41.5f, mono = true, max = w - 57f - tear - 11f)
            mic(l, w - 51f, ty + 7f)
            row(l, RectF(0f, ty, w - 54f, ty + 58f))
        }
        if (!micOn) {
            val ty = lines.size * 63f
            val r = RectF(0f, ty, w, ty + 30f)
            ticket(rrPath(r, min(wr, 14f)), r)
            note(12f, ty, ty + 19.5f, w - 24f, false)
        }
    }

    private fun ticketOne(l: Line, w: Float, h: Float) {
        val r = RectF(0f, 0f, w, h)
        ticket(ticketPath(r, listOf(0f to 31f, w to 31f)), r)
        dash(12f, 30.25f, w - 9f, 30.25f)
        header(l, 12f, w - 9f, 18.2f, 19.2f, mono = true)
        when (l.st) {
            St.NONE -> text("–", 12f, 77f, 48f, num(700), mu, -.06f)
            St.GONE -> text("운행 중", 12f, 70f, 17f, KO700, fg)
            else -> { text(l.f, 12f, 77f, 48f, num(700), fg, -.06f); text(l.s, 12f, 101.2f, 21f, num(600), fg, -.03f) }
        }
        mic(l, w - 53f, h - 53f)
        row(l, RectF(0f, 0f, w - 56f, h))
    }
}
