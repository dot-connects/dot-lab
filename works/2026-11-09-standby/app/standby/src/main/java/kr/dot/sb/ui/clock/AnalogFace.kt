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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogTicks
import kr.dot.sb.model.ClockStyle
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
    val p = rememberTimePainter(style)
    val ui = rememberUi(600)
    val numLook = Look.of(style, ink, base = Color(style.numeralColor), effects = false)
    val tick = ink.mapped(Color(style.tickColor)).toArgb()
    val hand = ink.accent(Color(style.handColor)).toArgb()
    val sec = ink.accent(Color(style.secondColor)).toArgb()
    val wkday = ink.accent(Color(style.weekdayColor)).toArgb()
    val white = ink.ink.toArgb()
    val bg = if (inSlot) null else bgColors(style, ink)

    val second = remember { mutableIntStateOf(((System.currentTimeMillis() / 1000) % 60).toInt()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            second.intValue = ((ms / 1000) % 60).toInt()
            // Wait off the frame clock so idle tests and the UI thread never spin on this ticker.
            withContext(Dispatchers.Default) { delay(1000 - ms % 1000) }
        }
    }

    Box(modifier) {
        FaceCanvas(Modifier.graphicsLayer()) {
            val w = size.width; val h = size.height
            val dial = Dial(w, h, rect = !inSlot && w > h * 1.2f)
            val xs = inSlot && SizeClass.of(Sizes.of(w.toDp(), h.toDp()).s) == SizeClass.XS
            val s = min(w, h); val u = dial.u
            val numbers = if (xs) AnalogNumbers.NONE else style.analogNumbers
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
                        if (numbers == AnalogNumbers.FOUR && dial.rect) {
                            // iPhone frame: 12/6 at 35 % of the way in from the edge, 3/9 at 30 %
                            val f = if (i % 6 == 0) 0.35f else 0.30f
                            nums += N(label(i), dial.cx + dx * dial.hw * (1 - f), dial.cy + dy * dial.hh * (1 - f))
                        } else {
                            // sit on the dial inset by (major tick + 0.03 s) and the numeral's own half extent
                            // on a squircle inside the inset rect: rect-like, but 10/2/4/8 come in from the corners
                            val inset = hourLen + 0.03f * s
                            val a = dial.hw - inset - adv.getValue(i) / 2; val b = dial.hh - inset - cap / 2
                            val r = if (dial.rect) 1f / sqrt(sqrt((abs(dx) / a).pow(4) + (abs(dy) / b).pow(4)))
                            else dial.dist(dx, dy, inset) - (abs(dx) * adv.getValue(i) / 2 + abs(dy) * cap / 2)
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
            val dateX = if (dial.rect) 0.628f * w else dial.cx + 0.3f * u
            val dateBase = dial.cy + ui.textSize * ui.capRatio() / 2

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
