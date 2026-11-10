package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogTicks
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.ui.theme.Family
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val ROMAN = arrayOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

/** Dial geometry shared by the static layer and the second-hand layer. */
private class Dial(w: Float, h: Float, val rect: Boolean) {
    val cx: Float; val cy: Float
    /** Half extents of the dial (equal for the round dial) and corner radius. */
    val hw: Float; val hh: Float; val rc: Float
    /** Unit for strokes and hands: the dial's half short side. */
    val u: Float

    init {
        if (rect) {
            val l = 0.083f * w; val r = 0.92f * w; val t = 0.125f * h; val b = 0.929f * h
            cx = (l + r) / 2; cy = (t + b) / 2; hw = (r - l) / 2; hh = (b - t) / 2; rc = 0.16f * hh
        } else {
            val s = min(w, h)
            val rr = s / 2 - 0.04f * s
            cx = w / 2; cy = h / 2; hw = rr; hh = rr; rc = rr
        }
        u = min(hw, hh)
    }

    /** Distance from the centre to the dial edge along the unit ray (dx, dy), insetting the edge by [inset]. */
    fun dist(dx: Float, dy: Float, inset: Float = 0f): Float {
        val ax = hw - inset; val ay = hh - inset; val r = max(0f, rc - inset)
        val adx = abs(dx); val ady = abs(dy)
        val t = min(if (adx > 1e-6f) ax / adx else Float.MAX_VALUE, if (ady > 1e-6f) ay / ady else Float.MAX_VALUE)
        if (adx * t <= ax - r || ady * t <= ay - r) return t
        // ray meets the rounded corner: |t·d − c| = r with c = corner circle centre
        val ccx = ax - r; val ccy = ay - r
        val bq = adx * ccx + ady * ccy
        val disc = bq * bq - (ccx * ccx + ccy * ccy - r * r)
        return bq + sqrt(max(0f, disc))
    }
}

/**
 * Analog (spec C4, design-system §4.3): rounded-rect dial in a full-screen landscape box, round dial otherwise.
 * The static dial + hour/minute hands redraw once a minute; the second hand lives on its own layer and ticks at 1 Hz
 * from its own LaunchedEffect only while this face is composed.
 */
@Composable
internal fun AnalogFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    // B / C draw their own dial for the 12·3·6·9 (and bare) numbering; Duo, twelve and Roman stay shared
    val family = kr.dot.sb.ui.theme.LocalTheme.current.family
    if (family != Family.IPHONE && (style.analogNumbers == AnalogNumbers.FOUR || style.analogNumbers == AnalogNumbers.NONE)) {
        if (family == Family.PAPER) PaperAnalog(style, d, ink, inSlot, modifier) else LampAnalog(style, d, ink, inSlot, modifier)
        return
    }
    val p = rememberTimePainter(style)
    val ui = rememberUi(600)
    val ui2 = rememberUi(600)
    val numLook = Look.of(style, ink, base = Color(style.numeralColor), effects = false)
    val tick = ink.mapped(Color(style.tickColor)).toArgb()
    val hand = ink.accent(Color(style.handColor)).toArgb()
    val sec = ink.accent(Color(style.secondColor)).toArgb()
    val wkday = ink.accent(Color(style.weekdayColor)).toArgb()
    val white = ink.ink.toArgb()
    val bg = if (inSlot) null else bgColors(style, ink)

    val second = rememberSecond()

    Box(modifier) {
        FaceCanvas(Modifier.graphicsLayer()) {
            val w = size.width; val h = size.height
            val dial = Dial(w, h, rect = !inSlot && w > h * 1.2f)
            if (dial.rect && style.analogNumbers == AnalogNumbers.DUO) return@FaceCanvas duo(w, h, d, style, p, numLook, ui, tick, hand, wkday, white, bg)
            val xs = inSlot && SizeClass.of(Sizes.of(w.toDp(), h.toDp()).s) == SizeClass.XS
            val s = min(w, h); val u = dial.u
            // Duo's eight-numeral layout needs the wide rect dial; a round dial shows all twelve
            val numbers = if (xs) AnalogNumbers.NONE else if (style.analogNumbers == AnalogNumbers.DUO) AnalogNumbers.TWELVE else style.analogNumbers
            val ticks = if (xs) AnalogTicks.HOURS else style.analogTicks
            val ringNums = numbers == AnalogNumbers.TWELVE || numbers == AnalogNumbers.ROMAN
            val major = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tick; strokeWidth = 0.028f * u; strokeCap = Paint.Cap.ROUND }
            val minor = Paint(major).apply { color = TimePainter.withAlpha(tick, 0.55f); strokeWidth = 0.016f * u }
            val minorLen = if (dial.rect) 0.028f * w else 0.05f * u

            // ticks: (outer x, y, inner x, y, major?)
            class T(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val major: Boolean)
            val tickList = ArrayList<T>()
            var hourLen = 0f
            if (ticks != AnalogTicks.NONE) for (i in 0 until 60) {
                val isHour = i % 5 == 0
                if (!isHour && ticks == AnalogTicks.HOURS) continue
                val an = i * Math.PI.toFloat() / 30f
                val dx = sin(an); val dy = -cos(an)
                val dist = dial.dist(dx, dy)
                var len = when {
                    !isHour -> minorLen
                    !dial.rect -> 0.11f * u
                    ticks == AnalogTicks.IPHONE -> when {
                        numbers == AnalogNumbers.FOUR && (i == 0 || i == 30) -> minorLen * 1.4f
                        i == 15 || i == 45 -> dist * 0.19f
                        else -> dist * 0.43f
                    }
                    else -> dist * 0.12f
                }
                if (isHour && ringNums) len = min(len, if (dial.rect) dist * 0.12f else 0.08f * u)
                if (isHour && (i % 15 != 0 || !dial.rect)) hourLen = max(hourLen, if (dial.rect) min(len, dist * 0.12f) else len)
                tickList += T(dial.cx + dx * dist, dial.cy + dy * dist, dial.cx + dx * (dist - len), dial.cy + dy * (dist - len), isHour)
            }

            // numerals
            class N(val text: String, val x: Float, val y: Float)
            val nums = ArrayList<N>()
            var numSize = 0f
            if (numbers != AnalogNumbers.NONE) {
                val sizePct = style.sizePct / 100f
                val label = { i: Int -> if (numbers == AnalogNumbers.ROMAN) ROMAN[i] else (if (i == 0) 12 else i).toString() }
                var cap = if (dial.rect) (if (numbers == AnalogNumbers.ROMAN) 0.07f else 0.086f) * h * sizePct
                else (if (numbers == AnalogNumbers.FOUR) 0.17f else if (numbers == AnalogNumbers.ROMAN) 0.11f else 0.13f) * u * sizePct
                val which = if (numbers == AnalogNumbers.FOUR) listOf(0, 3, 6, 9) else (0 until 12).toList()
                for (iter in 0 until 20) {
                    numSize = cap / p.capEm
                    val adv = which.associateWith { p.measure(label(it), numSize) }
                    nums.clear()
                    for (i in which) {
                        val an = i * Math.PI.toFloat() / 6f
                        val dx = sin(an); val dy = -cos(an)
                        val inset = hourLen + 0.03f * s
                        if (numbers == AnalogNumbers.FOUR && dial.rect) {
                            // iPhone frame: 12/6 at 35 % of the way in from the edge, 3/9 at 30 %
                            val f = if (i % 6 == 0) 0.35f else 0.30f
                            nums += N(label(i), dial.cx + dx * dial.hw * (1 - f), dial.cy + dy * dial.hh * (1 - f))
                        } else if (dial.rect) {
                            // review C6: equal spacing along the inset rounded rect, starting at 12, so 3/6/9/12 sit on
                            // the axes and "11 12 1" no longer bunch while the 3/9 sides stay empty
                            val a = dial.hw - inset - adv.values.max() / 2; val b = dial.hh - inset - cap / 2
                            val (px, py) = perimeterPoint(a, b, max(0f, dial.rc - inset), i / 12f)
                            nums += N(label(i), dial.cx + px, dial.cy + py)
                        } else {
                            // sit on the dial inset by (major tick + 0.03 s) and the numeral's own half extent
                            val r = dial.dist(dx, dy, inset) - (abs(dx) * adv.getValue(i) / 2 + abs(dy) * cap / 2)
                            nums += N(label(i), dial.cx + dx * r, dial.cy + dy * r)
                        }
                    }
                    if (!ringNums) break
                    // fit: each numeral's advance ≤ 0.8 × spacing to its neighbour (no collisions: "XI XII I")
                    val ok = nums.indices.all { k ->
                        val a = nums[k]; val b = nums[(k + 1) % nums.size]
                        val apart = (p.measure(a.text, numSize) + p.measure(b.text, numSize)) / 2 <= 0.8f * abs(a.x - b.x)
                        apart || cap <= 0.8f * abs(a.y - b.y)
                    }
                    if (ok) break
                    cap *= 0.93f
                }
            }

            // date: weekday + day, right of centre
            val date = if (xs) null else listOf(Seg(d.weekday + " ", wkday), Seg(d.day.toString(), white))
            ui.textSize = if (dial.rect) 0.055f * h else if (inSlot) Sizes.of(w.toDp(), h.toDp()).label.toPx() else 0.1f * u
            var dateX = if (dial.rect) 0.628f * w else dial.cx + 0.3f * u
            var dateBase = dial.cy + ui.textSize * ui.capRatio() / 2
            if (date != null && !dial.rect) {
                // review C3: on the round dial the date must clear numeral 3 by 0.5 em ("월 9" + "3" read "월 93");
                // when it cannot (or the slot is small), it moves below the centre (y = +0.38 R, centred)
                val n3 = nums.firstOrNull { it.text == "3" || it.text == "III" }
                val dw = ui.width(date)
                val limit = n3?.let { it.x - p.measure(it.text, numSize) / 2 - 0.5f * ui.textSize } ?: (dial.cx + 0.9f * u)
                if (dateX + dw > limit || (inSlot && s.toDp() < 300.dp)) {
                    dateX = dial.cx - dw / 2
                    dateBase = dial.cy + 0.38f * u + ui.textSize * ui.capRatio() / 2
                }
            }

            // hour / minute hands (minute resolution)
            val m = d.now.minute; val hr = d.now.hour % 12 + m / 60f
            val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hand; strokeCap = Paint.Cap.ROUND }
            val reach = if (dial.rect) dial.hh else u
            return@FaceCanvas {
                drawFaceBg(bg, w, h, 0f)
                for (t in tickList) drawLine(t.x0, t.y0, t.x1, t.y1, if (t.major) major else minor)
                for (n in nums) p.draw(this, n.text, n.x - p.measure(n.text, numSize) / 2, n.y + p.capEm * numSize / 2, numSize, numLook)
                if (date != null) drawSegs(date, dateX, dateBase, ui)
                drawHand(dial.cx, dial.cy, hr * 30f, 0.52f * reach, u, handPaint)
                drawHand(dial.cx, dial.cy, m * 6f, 0.9f * reach, u, handPaint)
            }
        }
        // second hand: own layer, the only thing that redraws each second
        FaceCanvas(Modifier.fillMaxSize().graphicsLayer()) {
            val w = size.width; val h = size.height
            val dial = Dial(w, h, rect = !inSlot && w > h * 1.2f)
            if (dial.rect && style.analogNumbers == AnalogNumbers.DUO) {
                // iPhone Duo: thin second hand with a tail, centre cap, and the "9:41:10" readout left of centre
                val k = h / 475f; val cx = w / 2; val cy = h / 2
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec; strokeWidth = 3.5f * k; strokeCap = Paint.Cap.ROUND }
                val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hand }
                ui2.textSize = 0.06f * h
                ui2.color = white
                ui2.textAlign = Paint.Align.CENTER
                val base = cy + ui2.textSize * ui2.capRatio() / 2
                return@FaceCanvas {
                    val sNow = second.intValue
                    drawText("${d.h}:${d.m}:${"%02d".format(sNow)}", cx - 0.149f * w, base, ui2)
                    val an = sNow * Math.PI.toFloat() / 30f
                    val dx = sin(an); val dy = -cos(an)
                    drawLine(cx - dx * 30f * k, cy - dy * 30f * k, cx + dx * 200f * k, cy + dy * 200f * k, sp)
                    drawCircle(cx, cy, 9f * k, cap)
                    drawCircle(cx, cy, 4f * k, sp)
                }
            }
            val u = dial.u
            val reach = if (dial.rect) dial.hh * 0.97f else u * 0.88f
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec; strokeWidth = 0.02f * u; strokeCap = Paint.Cap.ROUND }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.Black.toArgb() }
            return@FaceCanvas {
                val an = second.intValue * Math.PI.toFloat() / 30f
                val dx = sin(an); val dy = -cos(an)
                drawLine(dial.cx - dx * reach * 0.18f, dial.cy - dy * reach * 0.18f, dial.cx + dx * reach, dial.cy + dy * reach, sp)
                drawCircle(dial.cx, dial.cy, 0.045f * u, sp)
                drawCircle(dial.cx, dial.cy, 0.02f * u, dot)
            }
        }
    }
}

/**
 * iPhone Duo analog (mockup r5 A, frame ip-analog-duo): on the wide screen, eight numerals (12 2 3 4 6 8 9 10) sit on a
 * rounded-rect ring, ticks at 1 5 7 11, short dashes inside 3 and 9, weekday + day right of centre, white hands with a
 * black outline. Geometry in the mockup's 751 × 475 units, scaled by the height.
 */
private fun duo(
    w: Float, h: Float, d: FaceData, style: ClockStyle, p: TimePainter, numLook: Look, ui: android.text.TextPaint,
    tick: Int, hand: Int, wkday: Int, white: Int, bg: Pair<Int, Int>?,
): Canvas.() -> Unit {
    val k = h / 475f; val cx = w / 2; val cy = h / 2
    val rx = 0.389f * w; val ry = 172f * k
    fun onRect(deg: Float, inset: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble())
        val sx = sin(a).toFloat(); val sy = -cos(a).toFloat()
        val t = min((rx - inset) / max(abs(sx), 1e-6f), (ry - inset) / max(abs(sy), 1e-6f))
        return (cx + sx * t) to (cy + sy * t)
    }
    val numSize = 80f * k * 0.73f / p.capEm * style.sizePct / 100f
    val labels = mapOf(0 to "12", 2 to "2", 3 to "3", 4 to "4", 6 to "6", 8 to "8", 9 to "9", 10 to "10")
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tick; strokeWidth = 7f * k; strokeCap = Paint.Cap.ROUND }
    val dash = Paint(tp).apply { strokeWidth = 5f * k }
    ui.textSize = 0.06f * h
    val date = listOf(Seg(d.weekday + " ", wkday), Seg(d.day.toString(), white))
    val dateX = cx + 0.149f * w - ui.width(date) / 2
    val dateBase = cy + ui.textSize * ui.capRatio() / 2
    val m = d.now.minute; val hr = d.now.hour % 12 + m / 60f
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.Black.toArgb(); strokeCap = Paint.Cap.ROUND }
    val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hand; strokeCap = Paint.Cap.ROUND }
    fun Canvas.handLine(deg: Float, len: Float, width: Float) {
        val a = Math.toRadians(deg.toDouble())
        val ex = cx + sin(a).toFloat() * len; val ey = cy - cos(a).toFloat() * len
        outline.strokeWidth = width + 6f * k; drawLine(cx, cy, ex, ey, outline)
        hp.strokeWidth = width; drawLine(cx, cy, ex, ey, hp)
    }
    return {
        drawFaceBg(bg, w, h, 0f)
        for (i in 1..12) {
            val hNum = i % 12
            val label = labels[hNum]
            if (label != null) {
                val (x, y) = onRect(i * 30f, 0f)
                p.draw(this, label, x - p.measure(label, numSize) / 2, y + p.capEm * numSize / 2, numSize, numLook)
            } else {
                val (x1, y1) = onRect(i * 30f, 18f * k); val (x2, y2) = onRect(i * 30f, 58f * k)
                drawLine(x1, y1, x2, y2, tp)
            }
        }
        drawLine(cx - 222f * w / 751f, cy, cx - 196f * w / 751f, cy, dash)
        drawLine(cx + 196f * w / 751f, cy, cx + 222f * w / 751f, cy, dash)
        drawSegs(date, dateX, dateBase, ui)
        handLine(hr * 30f, 112f * k, 14f * k)
        handLine(m * 6f, 178f * k, 10f * k)
    }
}

/**
 * Point at fraction [t] (0 = 12 o'clock, clockwise) of the perimeter of a rounded rect with half extents [a]×[b] and
 * corner radius [r], relative to its centre. Quarter-symmetric, so 3/6/9 land exactly on the axes.
 */
internal fun perimeterPoint(a: Float, b: Float, r0: Float, t: Float): Pair<Float, Float> {
    val r = r0.coerceIn(0f, min(a, b))
    val arc = r * Math.PI.toFloat() / 2
    val lq = (a - r) + arc + (b - r)
    // quadrant 0: from (0, -b) along the top, round the corner, down to (a, 0)
    fun q0(l: Float): Pair<Float, Float> = when {
        l <= a - r -> l to -b
        l <= a - r + arc -> { val th = (l - (a - r)) / r; (a - r + r * sin(th)) to (-b + r - r * cos(th)) }
        else -> a to (-b + r + (l - (a - r) - arc))
    }
    val pos = (((t % 1f) + 1f) % 1f) * 4 * lq
    val q = (pos / lq).toInt().coerceAtMost(3)
    val l = pos - q * lq
    return when (q) {
        0 -> q0(l)
        1 -> q0(lq - l).let { (x, y) -> x to -y }
        2 -> q0(l).let { (x, y) -> -x to -y }
        else -> q0(lq - l).let { (x, y) -> -x to y }
    }
}

/** iPhone hand: thin neck from the pivot, then the wide bar with round ends. [deg] clockwise from 12. */
private fun Canvas.drawHand(cx: Float, cy: Float, deg: Float, len: Float, u: Float, p: Paint) {
    val an = Math.toRadians(deg.toDouble())
    val dx = sin(an).toFloat(); val dy = -cos(an).toFloat()
    val neck = 0.14f * u
    p.strokeWidth = 0.028f * u
    drawLine(cx, cy, cx + dx * neck, cy + dy * neck, p)
    p.strokeWidth = 0.07f * u
    drawLine(cx + dx * neck, cy + dy * neck, cx + dx * len, cy + dy * len, p)
}
