package kr.dot.sb.ui.clock

import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.IntState
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogStyle
import kr.dot.sb.model.AnalogTicks
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.rectPoint
import kr.dot.sb.ui.screens.Glow
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ThemedDigits
import kotlin.math.cos
import kotlin.math.sin

private val ROMAN = arrayOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

/** Dial colours. An untouched theme default reads the tokens; a user colour goes through [Ink.accent] (night + 3:1 → ink). */
private class Hues(val hand: Color, val num: Color, val tick: Color, val sec: Color, val wk: Color, val under: Color)

@Composable
private fun hues(style: ClockStyle, ink: Ink, under: Color): Hues {
    val t = LocalTheme.current
    val def = t.clockDefaults(FaceId.ANALOG)
    val acc = remember(ink.night, t, ink.page) { Ink.of(ink.night, null, t, ink.page).acc }
    fun pick(user: Long, d: Long, token: Color) = if (user == d) token else ink.accent(Color(user))
    return Hues(
        pick(style.handColor, def.handColor, ink.ink), pick(style.numeralColor, def.numeralColor, ink.ink),
        pick(style.tickColor, def.tickColor, ink.ink), pick(style.secondColor, def.secondColor, acc),
        pick(style.weekdayColor, def.weekdayColor, ink.accText), under,
    )
}

/**
 * Analog (spec-r9 §3 "Analog clock (full)", mockup 화면 전체 → 아날로그 스타일): five styles from `AppConfig.analogStyle`.
 * ROUND = a round dial with date/weather beside (landscape) or under it; RECT = ticks and numerals along the screen edge;
 * BIG4 = 12 · 3 · 6 · 9 huge on the edges; MINIMAL = 12 bars + an accent glow; DUO = round dial + digital time.
 * In a board slot it is always the round dial (no numerals under 176 dp). The static dial and hour/minute hands redraw
 * once a minute; the second hand ticks on its own layer.
 */
@Composable
internal fun AnalogFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier, kind: AnalogStyle = AnalogStyle.ROUND) {
    val bg = if (inSlot) null else bgColors(style, ink)
    val c = hues(style, ink, bg?.let { Color(it.first) } ?: ink.bg)
    val p = rememberTimePainter(style)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (bg != null) FaceCanvas(Modifier) { val w = size.width; val h = size.height; { drawFaceBg(bg, w, h, 0f) } }
        val w = maxWidth; val h = maxHeight; val s = min(w, h); val land = w > h
        val dial = @Composable { k: AnalogStyle, m: Modifier -> Dial(k, style, d, c, p, inSlot && s < 176.dp, m) }
        when {
            inSlot -> dial(AnalogStyle.ROUND, Modifier.size(s))
            kind == AnalogStyle.ROUND -> {
                val pad = s * 0.07f
                val dd = min(s - pad * 2, if (land) w * 0.52f else h * 0.62f)
                val room = if (land) w - dd - pad * 3 else w - pad * 2
                val info = style.showInfo && (if (land) room > 160.dp else h - dd - pad * 3 > 90.dp)
                val fs = (s * 0.05f).coerceIn(14.dp, 24.dp)
                val body = @Composable {
                    dial(AnalogStyle.ROUND, Modifier.size(dd))
                    if (info) Info(d, c, ink, fs, land, room)
                }
                if (land) Row(horizontalArrangement = Arrangement.spacedBy(pad * 1.2f), verticalAlignment = Alignment.CenterVertically) { body() }
                else Column(verticalArrangement = Arrangement.spacedBy(pad * 1.2f), horizontalAlignment = Alignment.CenterHorizontally) { body() }
            }
            kind == AnalogStyle.DUO -> {
                val pad = s * 0.05f
                val dd = if (land) min(h - pad * 2, w * 0.48f) else min(w - pad * 2, h * 0.48f)
                val fs = if (land) min(h * 0.34f, (w - dd - pad * 3) / 2.3f) else min(w * 0.3f, (h - dd - pad * 3) * 0.55f)
                val digits = @Composable {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ThemedDigits("${d.h}:${d.m}", LocalTheme.current, ink, Modifier.size(fs * 2.3f, fs), color = c.num)
                        if (style.showInfo) {
                            val line = "${d.weekday}요일 ${d.month}월 ${d.day}일" + (d.temp?.let { " · $it" } ?: "")
                            Txt(line, max(14.dp, fs * 0.16f) * d.ts, c.wk, Modifier.padding(top = 6.dp), weight = 500, num = true, maxW = fs * 2.3f)
                        }
                    }
                }
                if (land) Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    dial(AnalogStyle.ROUND, Modifier.size(dd)); digits()
                } else Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceAround, horizontalAlignment = Alignment.CenterHorizontally) {
                    dial(AnalogStyle.ROUND, Modifier.size(dd)); digits()
                }
            }
            else -> {
                if (kind == AnalogStyle.MINIMAL) Glow(c.sec, Offset(0.5f, 0.5f), 0.5f, 0.6f, 0.16f)
                dial(kind, Modifier.fillMaxSize())
            }
        }
    }
}

/** Weekday (accent text), "11월 9일" big, weather · next alarm; left-aligned beside the dial, centred under it. */
@Composable
private fun Info(d: FaceData, c: Hues, ink: Ink, fs: Dp, land: Boolean, room: Dp) {
    val line = listOfNotNull(d.cond?.let { "${it.ko} ${d.temp}" }, d.alarmShort?.let { "알람 $it" }).joinToString(" · ")
    Column(
        horizontalAlignment = if (land) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(fs * 0.5f), // cap boxes: Hangul overhangs the cap and the baseline
    ) {
        // weekday and the info line follow 글자 크기; the big date does not
        Txt("${d.weekday}요일", fs * d.ts, c.wk, maxW = room)
        Txt("${d.month}월 ${d.day}일", fs * 1.8f, ink.ink, num = true, maxW = room)
        if (line.isNotEmpty()) Txt(line, fs * d.ts, ink.ink2, weight = 500, num = true, maxW = room)
    }
}

/** Hand geometry: centre, reach and base width (mockup `hands(W, H, len, …, wid)`). */
private class Geo(val cx: Float, val cy: Float, val len: Float, val wid: Float)

private fun geo(kind: AnalogStyle, w: Float, h: Float): Geo {
    val s = minOf(w, h)
    return when (kind) {
        AnalogStyle.RECT -> Geo(w / 2, h / 2, 0.38f * s, 0.018f * s)
        AnalogStyle.BIG4 -> Geo(w / 2, h / 2, 0.42f * s, 0.022f * s)
        AnalogStyle.MINIMAL -> Geo(w / 2, h / 2, 0.46f * s, 0.02f * s)
        else -> Geo(w / 2, h / 2, 0.44f * s, 0.022f * s)
    }
}

/** One dial filling [modifier]: ROUND draws in the box's square, the others along the box edge ([rectPoint]). */
@Composable
private fun Dial(kind: AnalogStyle, style: ClockStyle, d: FaceData, c: Hues, p: TimePainter, bare: Boolean, modifier: Modifier) {
    val second = rememberSecond()
    Box(modifier) {
        FaceCanvas(Modifier.graphicsLayer()) {
            val w = size.width; val h = size.height; val s = minOf(w, h); val r = s / 2
            val round = kind == AnalogStyle.ROUND
            // a point [inset] in from the dial edge on the ray at [deg] (0 = 12 o'clock)
            fun at(deg: Float, inset: Float): Pair<Float, Float> = if (round) {
                val a = Math.toRadians(deg.toDouble()); val rr = r - inset
                (w / 2 + sin(a).toFloat() * rr) to (h / 2 - cos(a).toFloat() * rr)
            } else rectPoint(w, h, deg, inset)

            class Mark(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val p: Paint)
            val major = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.tick.toArgb(); strokeCap = Paint.Cap.ROUND }
            val minor = Paint(major).apply { color = c.tick.copy(alpha = c.tick.alpha * 0.45f).toArgb() }
            val marks = ArrayList<Mark>()
            fun mark(deg: Float, from: Float, to: Float, paint: Paint) { val (a, b) = at(deg, from); val (e, f) = at(deg, to); marks += Mark(a, b, e, f, paint) }

            // numerals: (label, hour index), font px (mockup font-size, Inter cap .73 → this font's cap)
            var labels: List<Pair<String, Int>> = emptyList()
            var fontPx = 0f; var numInset = 0f
            val pad = 0.05f * s
            when (kind) {
                AnalogStyle.ROUND, AnalogStyle.RECT -> {
                    val ticks = if (bare) AnalogTicks.HOURS else style.analogTicks
                    if (ticks != AnalogTicks.NONE) for (i in 0 until 60) {
                        val big = i % 5 == 0
                        if (!big && ticks == AnalogTicks.HOURS) continue
                        if (round) {
                            major.strokeWidth = 0.022f * r; minor.strokeWidth = 0.01f * r
                            mark(i * 6f, 0.04f * r, if (big) 0.14f * r else 0.09f * r, if (big) major else minor)
                        } else {
                            major.strokeWidth = 0.008f * s; minor.strokeWidth = 0.004f * s
                            mark(i * 6f, pad, pad + if (big) 0.05f * s else 0.025f * s, if (big) major else minor)
                        }
                    }
                    val nums = if (bare) AnalogNumbers.NONE else style.analogNumbers
                    val which = if (nums == AnalogNumbers.FOUR) listOf(0, 3, 6, 9) else (0 until 12).toList()
                    labels = if (nums == AnalogNumbers.NONE) emptyList()
                    else which.map { (if (nums == AnalogNumbers.ROMAN) ROMAN[it] else (if (it == 0) 12 else it).toString()) to it }
                    fontPx = (if (round) 0.17f * r else 0.075f * s) * (if (nums == AnalogNumbers.ROMAN) 0.8f else 1f)
                    numInset = if (round) 0.28f * r else pad + 0.12f * s
                }
                AnalogStyle.BIG4 -> {
                    major.strokeWidth = 0.012f * s; minor.strokeWidth = 0.012f * s
                    for (i in 0 until 12) if (i % 3 != 0) mark(i * 30f, pad, pad + 0.06f * s, minor)
                    labels = listOf("12" to 0, "3" to 3, "6" to 6, "9" to 9)
                    fontPx = 0.3f * s; numInset = 0.3f * s * 0.45f
                }
                else -> {   // MINIMAL: twelve bars, the quarters long and in full tick colour
                    major.strokeWidth = 0.014f * s; minor.strokeWidth = 0.014f * s
                    for (i in 0 until 12) mark(i * 30f, pad, pad + s * (if (i % 3 == 0) 0.09f else 0.04f), if (i % 3 == 0) major else minor)
                }
            }
            val numSize = fontPx * 0.73f / p.capEm * style.sizePct / 100f
            val look = Look(Effect.SOLID, c.num.toArgb(), c.num.toArgb())
            val placed = labels.map { (t, i) -> val (x, y) = at(i * 30f, numInset); Triple(t, x - p.measure(t, numSize) / 2, y + p.capEm * numSize / 2) }

            val g = geo(kind, w, h)
            val m = d.now.minute; val hr = d.now.hour % 12 + m / 60f
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.hand.toArgb(); strokeCap = Paint.Cap.ROUND }
            val outline = Paint(hp).apply { color = c.under.toArgb() }
            fun android.graphics.Canvas.hand(deg: Float, len: Float, width: Float) {
                val a = Math.toRadians(deg.toDouble())
                val ex = g.cx + sin(a).toFloat() * len; val ey = g.cy - cos(a).toFloat() * len
                outline.strokeWidth = width + g.wid * 0.6f; drawLine(g.cx, g.cy, ex, ey, outline)
                hp.strokeWidth = width; drawLine(g.cx, g.cy, ex, ey, hp)
            }
            return@FaceCanvas {
                for (k in marks) drawLine(k.x0, k.y0, k.x1, k.y1, k.p)
                for ((t, x, y) in placed) p.draw(this, t, x, y, numSize, look)
                hand(hr * 30f, g.len * 0.55f, g.wid * 1.4f)
                hand(m * 6f, g.len * 0.85f, g.wid)
            }
        }
        // second hand: its own layer, the only thing that redraws each second
        FaceCanvas(Modifier.fillMaxSize().graphicsLayer()) {
            val g = geo(kind, size.width, size.height)
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.sec.toArgb(); strokeWidth = g.wid * 0.35f; strokeCap = Paint.Cap.ROUND }
            val cap = Paint(sp).apply { this.style = Paint.Style.FILL }
            val dot = Paint(cap).apply { color = c.under.toArgb() }
            return@FaceCanvas {
                val a = second.intValue * Math.PI.toFloat() / 30f
                val dx = sin(a); val dy = -cos(a)
                drawLine(g.cx - dx * g.len * 0.12f, g.cy - dy * g.len * 0.12f, g.cx + dx * g.len * 0.92f, g.cy + dy * g.len * 0.92f, sp)
                drawCircle(g.cx, g.cy, g.wid * 0.9f, cap)
                drawCircle(g.cx, g.cy, g.wid * 0.35f, dot)
            }
        }
    }
}

/** Unit tests pin the second hand (Gradle sets `shot.second`) so shots compare byte-for-byte. */
internal val fixedSecond: Int? = System.getProperty("shot.second")?.toIntOrNull()

/** Seconds 0-59, ticking at 1 Hz off the frame clock while composed (analog second hands). */
@Composable
internal fun rememberSecond(): IntState {
    fixedSecond?.let { return remember { mutableIntStateOf(it) } }
    val second = remember { mutableIntStateOf(((System.currentTimeMillis() / 1000) % 60).toInt()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            second.intValue = ((ms / 1000) % 60).toInt()
            // Wait off the frame clock so idle tests and the UI thread never spin on this ticker.
            withContext(Dispatchers.Default) { delay(1000 - ms % 1000) }
        }
    }
    return second
}
