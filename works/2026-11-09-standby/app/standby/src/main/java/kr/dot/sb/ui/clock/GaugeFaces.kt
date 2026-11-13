package kr.dot.sb.ui.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.LocalTime
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.cos
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
    style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, weather: WeatherInfo?, alarm: LocalDateTime?, modifier: Modifier,
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
        val vertical = h > w * 1.15f
        val bar = ink.accent(Color(style.barColor ?: FaceDefaults.BAR)).toArgb()
        val text = ink.ink.toArgb(); val dim = ink.ink2.toArgb(); val track = ink.ink3.toArgb()
        val sun = ink.ink4.toArgb()
        val now = barPosition(d.now.toLocalTime())
        val rise = weather?.sunrise?.let(::barPosition)
        val set = weather?.sunset?.let(::barPosition)
        val alarmAt = alarm?.let { barPosition(it.toLocalTime()) }
        label.textSize = min(w, h) * (if (inSlot) 0.055f else 0.06f)
        val capRatio = label.capRatio()
        // landscape: the time and its small 오전/오후 share 62 % of the width (c-day), the info column takes the rest
        small.textSize = 26 * min(w, h) / 360f
        val ampmW = if (d.use24h || vertical) 0f else small.measureText(d.ampm) + 14 * min(w, h) / 360f
        val fs = fitSize(painter, d, false, (if (vertical) 0.18f * h else 0.38f * h) * style.sizePct / 100f,
            if (vertical) w * 0.84f else w * 0.62f - ampmW)
        val cap = painter.capEm * fs
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar }
        val pale = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sun }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
        val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text }
        return@FaceCanvas {
            if (vertical) {
                drawTime(painter, d, fs, false, 0f, w / 2, h * 0.07f, look)
                val y0 = h * 0.34f; val length = h * 0.54f; val x = w * 0.38f; val thick = min(w * 0.055f, 16.dp.toPx())
                drawRoundRect(x, y0, x + thick, y0 + length, thick / 2, thick / 2, base)
                if (rise != null && set != null && set > rise) drawRect(x, y0 + length * rise, x + thick, y0 + length * set, pale)
                drawRoundRect(x, y0, x + thick, y0 + length * now, thick / 2, thick / 2, fill)
                val ym = y0 + length * now
                drawRoundRect(x - thick * 0.6f, ym - 2.dp.toPx(), x + thick * 1.6f, ym + 2.dp.toPx(), 2.dp.toPx(), 2.dp.toPx(), marker)
                alarmAt?.let { drawAlarmGlyph(x - thick * 2f, y0 + length * it, label.textSize, text) }
                label.color = dim
                for (i in 0..4) {
                    val y = y0 + length * i / 4f
                    drawText((i * 6).toString(), x + thick + 0.05f * w, y + label.textSize * capRatio / 2, label)
                }
            } else {
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
                if (!inSlot && infoW > 80 * k) {
                    val date = label.fit("${d.weekday}요일 · ${d.month}월 ${d.day}일", infoW)
                    label.color = text
                    drawText(date, w * 0.951f - label.measureText(date), 44 * k + 17.6f * k + label.capRatio() * label.textSize / 2, label)
                    d.weatherLine?.let {
                        val line = label.fit(it, infoW)
                        label.color = dim
                        drawText(line, w * 0.951f - label.measureText(line), 44 * k + 52.8f * k + label.capRatio() * label.textSize / 2, label)
                    }
                }
                val x0 = w * 0.049f; val length = w * 0.902f; val y = 266 * k; val thick = 16 * k
                small.textSize = 16 * k; small.color = bar
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
}

@Composable
private fun chargingNow(): Boolean {
    val context = LocalContext.current
    val charging = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                charging.value = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return charging.value
}

@Composable
internal fun RimFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val charging = chargingNow()
    val second = if (charging) rememberSecond().intValue else null
    val painter = rememberTimePainter(style)
    val label = rememberUi(500)
    val look = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val compact = min(w, h).toDp() < 120.dp
        val fs = fitSize(painter, d, false, h * (if (compact) 0.72f else 0.46f) * style.sizePct / 100f, w * 0.78f)
        val cap = painter.capEm * fs
        if (compact) return@FaceCanvas { drawTime(painter, d, fs, false, 0f, w / 2, (h - cap) / 2, look) }
        val active = ink.accent(Color(style.rimColor ?: FaceDefaults.RIM)).toArgb()
        val inactive = ink.ink3.toArgb()
        val inset = min(w, h) * 0.05f
        val radius = min(w, h) * (44f / 360f)
        val dots = (0 until 60).map { rimDot(it, w, h, inset, radius) }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        label.textSize = min(w, h) * 0.053f
        // c-rim: "월요일 · 11월 9일 · 7° · ⏰ 6:30" 29 dp (of 360) under the digits' baseline, the pair centred
        val dim = ink.ink2.toArgb()
        val caption = if (!style.showInfo || inSlot) null else buildList {
            add(Seg(d.infoLine(), dim))
            d.alarmShort?.let { add(Seg(" · ", dim)); add(alarmSeg(dim)); add(Seg(it, dim)) }
        }.let { if (label.width(it) <= w * 0.78f) it else listOf(Seg(d.infoLine(), dim)) }
        val gap = 29 * min(w, h) / 360f
        val group = cap + if (caption == null) 0f else gap + label.capRatio() * label.textSize
        val capTop = (h - group) / 2
        return@FaceCanvas {
            dots.forEachIndexed { i, (x, y) ->
                dotPaint.color = if (second?.let { i <= it } ?: (i == d.now.minute)) active else inactive
                drawCircle(x, y, min(w, h) * (if (i % 5 == 0) 5f else 3f) / 360f, dotPaint)
            }
            drawTime(painter, d, fs, false, 0f, w / 2, capTop, look)
            if (caption != null) drawSegs(caption, (w - label.width(caption)) / 2, capTop + group, label)
        }
    }
}
