package kr.dot.sb.ui.clock

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.LocalTime
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun barPosition(time: LocalTime): Float = (time.hour * 60 + time.minute) / 1440f

/** The mockup's clockwise rounded rectangle, starting in the middle of the top edge. */
internal fun rimDot(index: Int, width: Float, height: Float, inset: Float, radius: Float): Pair<Float, Float> {
    val left = inset; val top = inset; val right = width - inset; val bottom = height - inset
    val r = radius.coerceAtMost(min(right - left, bottom - top) / 2)
    val half = (right - left) / 2 - r
    val side = bottom - top - 2 * r
    val long = right - left - 2 * r
    val arc = (PI * r / 2).toFloat()
    val perimeter = 2 * (right - left + bottom - top - 4 * r) + 4 * arc
    var distance = perimeter * (index.mod(60)) / 60f
    fun line(length: Float, x: Float, y: Float, dx: Float, dy: Float): Pair<Float, Float>? {
        if (distance <= length) return x + dx * distance / length to y + dy * distance / length
        distance -= length
        return null
    }
    fun corner(cx: Float, cy: Float, degrees: Float): Pair<Float, Float>? {
        if (distance <= arc) {
            val angle = Math.toRadians((degrees + 90 * distance / arc).toDouble())
            return cx + r * cos(angle).toFloat() to cy + r * sin(angle).toFloat()
        }
        distance -= arc
        return null
    }
    return line(half, width / 2, top, half, 0f)
        ?: corner(right - r, top + r, -90f)
        ?: line(side, right, top + r, 0f, side)
        ?: corner(right - r, bottom - r, 0f)
        ?: line(long, right - r, bottom, -long, 0f)
        ?: corner(left + r, bottom - r, 90f)
        ?: line(side, left, bottom - r, 0f, -side)
        ?: corner(left + r, top + r, 180f)
        ?: line(half, left + r, top, half, 0f)
        ?: (width / 2 to top)
}

@Composable
internal fun DayBarFace(
    style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, weather: WeatherInfo?, alarm: LocalDateTime?, next: NextEvent?, modifier: Modifier,
) {
    val painter = rememberTimePainter(style)
    val label = rememberUi(500)
    val small = rememberUi(600)
    val mono = rememberTypeface(kr.dot.sb.ui.theme.Fonts.plexMono, 500).let { remember(it) { uiPaint(it, 500) } }
    val look = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        if (min(w, h).toDp() < 120.dp) {
            val fs = fitSize(painter, d, false, h * 0.72f * style.sizePct / 100f, w * 0.88f)
            val cap = painter.capEm * fs
            return@FaceCanvas { drawTime(painter, d, fs, false, 0f, w / 2, (h - cap) / 2, look) }
        }
        if (h > w * 1.15f) return@FaceCanvas dayBarPortrait(painter, label, small, mono, look, d, ink, style, weather, next, w, h)
        val bar = ink.accent(Color(style.barColor ?: FaceDefaults.BAR)).toArgb()
        val text = ink.ink.toArgb(); val dim = ink.ink2.toArgb(); val track = ink.ink3.toArgb()
        val sun = ink.ink4.toArgb()
        val now = barPosition(d.now.toLocalTime())
        val rise = weather?.sunrise?.let(::barPosition)
        val set = weather?.sunset?.let(::barPosition)
        val alarmAt = alarm?.let { barPosition(it.toLocalTime()) }
        label.textSize = min(w, h) * (if (inSlot) 0.055f else 0.06f)
        // landscape: the time and its small 오전/오후 share 62 % of the width (c-day), the info column takes the rest
        small.textSize = 26 * min(w, h) / 360f
        val ampmW = if (d.use24h) 0f else small.measureText(d.ampm) + 14 * min(w, h) / 360f
        val fs = fitSize(painter, d, false, 0.38f * h * style.sizePct / 100f, w * 0.62f - ampmW)
        val cap = painter.capEm * fs
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar }
        val pale = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sun }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
        val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text }
        return@FaceCanvas {
            // c-day at 780 × 360: time cap top 41, info right 38 / top 44, "하루의 N%" at 238, track 266 (16 tall),
            // hour scale 10 under the track (mono 15), 해 뜸 / 해 짐 22 under that
            val k = min(w, h) / 360f
            val capTop = 41 * k
            val leftTime = w * 0.046f
            painter.drawHm(this, d.h, d.m, leftTime, capTop + cap, fs, look)
            if (!d.use24h) {
                small.textSize = 26 * k; small.color = dim
                drawText(d.ampm, leftTime + painter.measureHm(d.h, d.m, fs) + 14 * k, capTop + cap, small)
            }
            // the info column ends where the time (and its 오전/오후) ends
            val infoW = w * 0.951f - (leftTime + painter.measureHm(d.h, d.m, fs) + ampmW) - 16 * k
            val dateFull = "${d.weekday}요일 · ${d.month}월 ${d.day}일"
            val tooNarrow = label.measureText(dateFull) > infoW || d.weatherLine?.let { label.measureText(it) > infoW } == true
            // near-square boxes (Flip cover): the right column would cut both lines, so they go under the time
            val lineH = label.capRatio() * label.textSize
            val below1 = capTop + cap + 34 * k + lineH
            val below2 = below1 + 34 * k
            if (!inSlot && tooNarrow && below2 < 238 * k - small.capRatio() * 16 * k - 14 * k) {
                label.color = text
                drawText(label.fit(dateFull, w * 0.902f), leftTime, below1, label)
                d.weatherLine?.let { label.color = dim; drawText(label.fit(it, w * 0.902f), leftTime, below2, label) }
            } else if (!inSlot && infoW > 80 * k) {
                val date = label.fit(dateFull, infoW)
                label.color = text
                drawText(date, w * 0.951f - label.measureText(date), 44 * k + 17.6f * k + label.capRatio() * label.textSize / 2, label)
                d.weatherLine?.let {
                    val line = label.fit(it, infoW)
                    label.color = dim
                    drawText(line, w * 0.951f - label.measureText(line), 44 * k + 52.8f * k + label.capRatio() * label.textSize / 2, label)
                }
            }
            val x0 = w * 0.049f; val length = w * 0.902f; val y = 266 * k; val thick = 16 * k
            small.textSize = 16 * k; small.color = bar // on the bar, beside the alarm glyph: stays (LARGE ran into it)
            drawText("하루의 ${(now * 100).toInt()}%", x0, 238 * k + small.capRatio() * small.textSize, small)
            drawRoundRect(x0, y, x0 + length, y + thick, thick / 2, thick / 2, base)
            if (rise != null && set != null && set > rise) drawRect(x0 + length * rise, y, x0 + length * set, y + thick, pale)
            drawRoundRect(x0, y, x0 + length * now, y + thick, thick / 2, thick / 2, fill)
            val xm = x0 + length * now
            drawRoundRect(xm - 2 * k, y - 9 * k, xm + 2 * k, y + 25 * k, 2 * k, 2 * k, marker)
            alarmAt?.let { drawAlarmGlyph(x0 + length * it - 8 * k, y - 14 * k, 16 * k, text) }
            mono.textSize = 15 * k; mono.color = dim
            val scaleY = y + thick + 10 * k + mono.capRatio() * mono.textSize
            for (i in 0..4) {
                val tick = (i * 6).toString()
                val tx = x0 + length * i / 4f - if (i == 0) 0f else if (i == 4) mono.measureText(tick) else mono.measureText(tick) / 2
                drawText(tick, tx, scaleY, mono)
            }
            small.textSize = 15 * k; small.color = dim
            fun sun(word: String, t: LocalTime?, at: Float?) {
                if (t == null || at == null) return
                val s = "$word ${t.hour}:%02d".format(t.minute)
                drawText(s, x0 + length * at - small.measureText(s) / 2, scaleY + 22 * k, small)
            }
            sun("해 뜸", weather?.sunrise, rise)
            sun("해 짐", weather?.sunset, set)
        }
    }
}

/** One sticky read, no receiver: StandBy closes when the charger goes out, so the value cannot go stale on screen. */
@Composable
private fun chargingNow(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        (context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }
}

@Composable
internal fun RimFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    // read only in the dot layer's draw lambda: the time and caption never re-lay-out per second
    val second = if (chargingNow()) rememberSecond() else null
    val painter = rememberTimePainter(style)
    val label = rememberUi(500)
    val look = Look.of(style, ink)
    Box(modifier) {
        // 60 rim dots on their own layer, the only thing that redraws each second
        FaceCanvas(Modifier.graphicsLayer()) {
            val w = size.width; val h = size.height
            if (min(w, h).toDp() < 120.dp) return@FaceCanvas {} // compact: digits only
            val active = ink.accent(Color(style.rimColor ?: FaceDefaults.RIM)).toArgb()
            val inactive = ink.ink3.toArgb()
            val inset = min(w, h) * 0.05f
            val radius = min(w, h) * (44f / 360f)
            val dots = (0 until 60).map { rimDot(it, w, h, inset, radius) }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            return@FaceCanvas {
                val sec = second?.intValue
                dots.forEachIndexed { i, (x, y) ->
                    dotPaint.color = if (sec?.let { i <= it } ?: (i == d.now.minute)) active else inactive
                    drawCircle(x, y, min(w, h) * (if (i % 5 == 0) 5f else 3f) / 360f, dotPaint)
                }
            }
        }
        FaceCanvas(Modifier) {
            val w = size.width; val h = size.height
            val compact = min(w, h).toDp() < 120.dp
            val fs = fitSize(painter, d, false, h * (if (compact) 0.72f else 0.46f) * style.sizePct / 100f, w * 0.78f)
            val cap = painter.capEm * fs
            if (compact) return@FaceCanvas { drawTime(painter, d, fs, false, 0f, w / 2, (h - cap) / 2, look) }
            label.textSize = min(w, h) * 0.053f
            // c-rim: "월요일 · 11월 9일 · 7° · ⏰ 6:30" 29 dp (of 360) under the digits' baseline, the pair centred
            val dim = ink.ink2.toArgb()
            val caption = if (!style.showInfo || inSlot) null else buildList {
                add(Seg(d.infoLine(), dim))
                d.alarmShort?.let { add(Seg(" · ", dim)); add(alarmSeg(dim)); add(Seg(it, dim)) }
            }.let { if (label.width(it) <= w * 0.78f) it else listOf(Seg(d.infoLine(), dim)) }
            if (caption != null) label.textSize *= min(1f, 0.9f * w / label.width(caption)) // one line, never past the edges
            val gap = 29 * min(w, h) / 360f
            val group = cap + if (caption == null) 0f else gap + label.capRatio() * label.textSize
            val capTop = (h - group) / 2
            return@FaceCanvas {
                drawTime(painter, d, fs, false, 0f, w / 2, capTop, look)
                if (caption != null) drawSegs(caption, (w - label.width(caption)) / 2, capTop + group, label)
            }
        }
    }
}

/** The next event for 하루 막대 portrait: its time label ("22:30", "내일 13:00"), title and calendar colour. */
@androidx.compose.runtime.Immutable
internal data class NextEvent(val time: String, val title: String, val color: Long)

/** The next timed item still to come today, else the first upcoming one on a later day; null = none. */
internal fun nextEvent(s: kr.dot.sb.ui.state.StandbyState): NextEvent? {
    val now = s.now.toLocalTime()
    val today = s.now.toLocalDate()
    fun at(a: kr.dot.sb.ui.state.AgendaItem) = runCatching { LocalTime.parse(a.start) }.getOrNull()
    s.agenda.orEmpty().filter { (it.date == null || it.date == today) && at(it)?.isAfter(now) == true }.minByOrNull { at(it)!! }
        ?.let { return NextEvent(it.start, it.title, it.color) }
    val later = s.upcoming.firstOrNull { it.date?.isAfter(today) == true } ?: return null
    val date = later.date!!
    val day = if (date == today.plusDays(1)) "내일" else "${date.monthValue}/${date.dayOfMonth}"
    return NextEvent(if (later.start == "종일") day else "$day ${later.start}", later.title, later.color)
}

/**
 * 하루 막대 portrait C (plan-r15 E3, r15-mock F.barC at 270 × 585, laid out as one block centred vertically): the big
 * time, "월요일 · 11월 9일 · 7° 흐림", up to two "next" rows on plates (next event, next alarm), "하루의 N%" over a
 * horizontal day bar with the now marker and 0/6/12/18/24 under it, then the sunrise/sunset line.
 */
private fun CacheDrawScope.dayBarPortrait(
    painter: TimePainter, label: android.text.TextPaint, small: android.text.TextPaint, mono: android.text.TextPaint, look: Look,
    d: FaceData, ink: Ink, style: ClockStyle, weather: WeatherInfo?, next: NextEvent?, w: Float, h: Float,
): Canvas.() -> Unit {
    val u = min(w / 270f, h / 585f)
    val top = (h - 416 * u) / 2 - 110 * u // mockup y 110 (time cap top) .. 526 (sun line)
    fun y(v: Float) = top + v * u
    val x0 = max(24 * u, w * 0.06f); val x1 = w - x0
    val fs = fitSize(painter, d, false, 60 * u * style.sizePct / 100f, w * 0.86f)
    val cap = painter.capEm * fs
    val bar = ink.accent(Color(style.barColor ?: FaceDefaults.BAR)).toArgb()
    val text = ink.ink.toArgb(); val dim = ink.ink2.toArgb()
    val now = barPosition(d.now.toLocalTime())
    val info = listOfNotNull("${d.weekday}요일 ${d.month}월 ${d.day}일", d.weatherLine).joinToString(" · ")
    val rows = listOfNotNull(
        next?.let { Triple(it.time, it.title, ink.mapped(Color(it.color)).toArgb()) },
        d.alarm?.let { Triple("${d.alarmDay} ${d.alarmAmPm}$it", "알람", ink.orange.toArgb()) },
    )
    val sunLine = weather?.let { wi ->
        val r = wi.sunrise; val st = wi.sunset
        if (r == null || st == null) null else "해 뜸 ${r.hour}:%02d · 해 짐 ${st.hour}:%02d".format(r.minute, st.minute)
    }
    val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.plate.compositeOver(ink.bg).toArgb() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar }
    val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.ink3.toArgb() }
    val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text }
    val swatch = Paint(Paint.ANTI_ALIAS_FLAG)
    return {
        drawTime(painter, d, fs, false, 0f, w / 2, y(170f) - cap, look)
        label.textSize = 13 * u; label.color = dim
        val line = label.fit(info, w * 0.9f)
        drawText(line, (w - label.measureText(line)) / 2, y(202f), label)
        rows.forEachIndexed { i, (t, title, c) ->
            val ry = y(290f + i * 44f)
            drawRoundRect(x0, ry - 26 * u, x1, ry + 10 * u, 10 * u, 10 * u, plate)
            swatch.color = c
            drawRect(x0 + 12 * u, ry - 17 * u, x0 + 15 * u, ry + u, swatch)
            label.textSize = 13 * u; label.color = dim
            drawText(t, x0 + 24 * u, ry - 2 * u, label)
            small.textSize = 14 * u; small.color = text
            val tt = small.fit(title, (x1 - 12 * u) - (x0 + 24 * u + label.measureText(t) + 12 * u))
            drawText(tt, x1 - 12 * u - small.measureText(tt), ry - 2 * u, small)
        }
        val by = y(470f); val len = x1 - x0; val thick = 10 * u
        small.textSize = 11 * u; small.color = bar
        drawText("하루의 ${(now * 100).toInt()}%", x0, by - 12 * u, small)
        drawRoundRect(x0, by, x1, by + thick, thick / 2, thick / 2, base)
        drawRoundRect(x0, by, x0 + len * now, by + thick, thick / 2, thick / 2, fill)
        val xm = x0 + len * now
        drawRect(xm - 1.5f * u, by - 4 * u, xm + 1.5f * u, by + 14 * u, marker)
        mono.textSize = 10 * u; mono.color = dim
        for (i in 0..4) {
            val tick = (i * 6).toString()
            drawText(tick, x0 + len * i / 4f - mono.measureText(tick) / 2, by + 30 * u, mono)
        }
        sunLine?.let { label.textSize = 11 * u; label.color = dim; drawText(it, (w - label.measureText(it)) / 2, by + 56 * u, label) }
    }
}
