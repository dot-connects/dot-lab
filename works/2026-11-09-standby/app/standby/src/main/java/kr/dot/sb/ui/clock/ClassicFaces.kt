package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.TextPaint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogTicks
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.ThemeTokens
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* r10 클래식 faces (clocks.html c-wall, c-cal, c-han; plan-r10 Task 8). 1 mockup px = 1 dp on a 780 × 360 box. */

private val NIGHT_INK = ThemeTokens.NIGHT_INK
private const val WHITE = 0xFFFFFFFF

private fun solid(c: Int) = Look(Effect.SOLID, c, 0)

/** "7° 대체로 흐림 · ⏰ 6:30" as segments; [weather] / [alarm] false drop that part. */
private fun FaceData.infoSegs(color: Int, weather: Boolean = true, alarm: Boolean = true): List<Seg> {
    val out = ArrayList<Seg>()
    if (weather) weatherLine?.let { out += Seg(it, color) }
    if (alarm && alarmShort != null) {
        if (out.isNotEmpty()) out += Seg(" · ", color)
        out += alarmSeg(color); out += Seg(alarmShort, color)
    }
    return out
}

/** Draw [text] centred on [cx] with its cap centred on [cy]. */
private fun Canvas.capCentred(text: String, cx: Float, cy: Float, p: TextPaint) =
    drawText(text, cx - p.measureText(text) / 2, cy + p.capRatio() * p.textSize / 2, p)

/* ------------------------------------------------------------------ 한글 시계 words */

private val NATIVE = arrayOf("열두", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한")
private val SINO = arrayOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")

/** Sino-Korean 0-59: 영, 오, 십, 십일, 이십, 사십일. */
private fun sino(n: Int): String = when {
    n == 0 -> "영"
    n < 10 -> SINO[n]
    else -> (if (n / 10 == 1) "" else SINO[n / 10]) + "십" + SINO[n % 10]
}

/**
 * The time in Hangul words: (오전/오후 or null in 24 h, hour, minute). 12 h hours are native ("아홉 시", 0 h and 12 h =
 * "열두 시"), 24 h hours sino ("십삼 시", 0 h = "영 시"); minutes sino ("사십일 분"), "정각" on the hour.
 */
fun hangulTime(h: Int, m: Int, use24: Boolean): Triple<String?, String, String> {
    val minute = if (m == 0) "정각" else "${sino(m)} 분"
    return if (use24) Triple(null, "${sino(h)} 시", minute)
    else Triple(if (h < 12) "오전" else "오후", "${NATIVE[h % 12]} 시", minute)
}

/* ------------------------------------------------------------------ 벽시계 */

private val ROMAN = arrayOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

/** Where the dial sits and whether the date block fits beside (landscape) or under it. */
private class WallGeo(val cx: Float, val cy: Float, val r: Float, val sideX: Float, val sideTop: Float, val side: Boolean, val k: Float)

/**
 * 벽시계 = an analog preset (c-wall): a light [ClockStyle.faceColor] dial (r 156 of a 360 dp height), ticks, numerals
 * 1-12 in the dial font at r 118, dark hands, a red second hand on its own 1 Hz layer; beside it (under it in a tall
 * box) the weekday, a big "11월 9일" and weather · alarm. The date block needs a 200 dp short side, else the dial alone.
 * Night: black dial with a night ring, night ink, a dim second hand.
 */
@Composable
internal fun WallFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val wk = rememberUi(600); val big = rememberUi(700); val ui = rememberUi(500)
    val n = ink.night
    val second = rememberSecond()
    fun night(c: Long, to: Color = NIGHT_INK) = lerp(Color(c), to, n).toArgb()
    val dialC = lerp(Color(style.faceColor ?: FaceDefaults.FACE), ink.bg, n).toArgb()
    val ring = NIGHT_INK.copy(alpha = 0.4f * n).toArgb()
    val handC = night(style.handColor); val tickC = night(style.tickColor); val numC = night(style.numeralColor)
    val secC = night(style.secondColor, Ink.NIGHT_DIM); val hubC = night(style.secondColor)
    val weekC = ink.accent(Color(style.weekdayColor)).toArgb()
    val fallback = Look.of(style, ink)
    val lines = listOfNotNull(d.weatherLine?.let { listOf(Seg(it, 0)) }, d.alarmShort?.let { listOf(alarmSeg(0), Seg(" $it", 0)) })

    /** Layout + text sizes; the dial and the second-hand layer both call it so they agree. */
    fun CacheDrawScope.geo(): WallGeo {
        val w = size.width; val h = size.height; val s = min(w, h)
        val tall = h > w * 1.1f
        val margin = if (inSlot) 0.04f * s else 10.dp.toPx() + 0.04f * s
        // the side block in mockup units (1 = 1 dp at a 360 dp height): its width is measured, its height fixed
        val unit = 1.dp.toPx()
        fun sizes(k: Float) { wk.textSize = 22 * k; big.textSize = 68 * k; ui.textSize = 22 * k }
        sizes(unit)
        val sideW = maxOf(wk.measureText("${d.weekday}요일"), big.measureText("${d.month}월 ${d.day}일"), lines.maxOfOrNull { ui.width(it) } ?: 0f) / unit
        val sideH = 22 + 6 + 68 * 1.15f + 8 + lines.size * 22 * 1.4f
        val base = s / 360f
        // the whole composition (dial 312 + gap + side block) scaled to fit the box, never past the height scale
        val k = if (tall) minOf(base, (h - 2 * margin) / (312 + 40 + sideH), (w - 2 * margin) / max(312f, sideW))
            else minOf(base, (w - 2 * margin) / (312 + 50 + sideW), (h - 2 * margin) / 312)
        val side = style.showInfo && s >= 200.dp.toPx() && k >= 0.6f * base
        if (!side) {
            val r = min(156 * base * style.sizePct / 100f, s / 2 - margin)
            return WallGeo(w / 2, h / 2, r, 0f, 0f, false, base)
        }
        sizes(k)
        val r = 156 * k * min(1f, style.sizePct / 100f)
        if (tall) {
            val top = (h - 2 * r - 40 * k - sideH * k) / 2
            return WallGeo(w / 2, top + r, r, w / 2, top + 2 * r + 40 * k, true, k)
        }
        val left = (w - 2 * r - 50 * k - sideW * k) / 2
        return WallGeo(left + r, h / 2, r, left + 2 * r + 50 * k, (h - sideH * k) / 2, true, k)
    }

    Box(modifier) {
        FaceCanvas(Modifier) {
            val w = size.width; val h = size.height
            if (min(w, h) < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, fallback)
            val g = geo()
            val u = g.r / 156f
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tickC; strokeCap = Paint.Cap.ROUND }
            val minor = Paint(tick).apply { alpha = alpha / 2 }
            val ticks = ArrayList<FloatArray>()
            val (majorIn, minorIn) = when (style.analogTicks) {
                AnalogTicks.SHORT -> 145f to 148f
                else -> 140f to 146f
            }
            if (style.analogTicks != AnalogTicks.NONE) for (i in 0 until 60) {
                if (i % 5 != 0 && style.analogTicks == AnalogTicks.HOURS) continue
                val a = Math.toRadians(i * 6.0); val sx = sin(a).toFloat(); val cy = -cos(a).toFloat()
                val r1 = if (i % 5 == 0) majorIn else minorIn
                ticks += floatArrayOf(g.cx + sx * r1 * u, g.cy + cy * r1 * u, g.cx + sx * 151 * u, g.cy + cy * 151 * u, if (i % 5 == 0) 1f else 0f)
            }
            val which = when (style.analogNumbers) {
                AnalogNumbers.NONE -> emptyList()
                AnalogNumbers.FOUR -> listOf(0, 3, 6, 9)
                else -> (0 until 12).toList()
            }
            val fs = 30 * u * (if (style.analogNumbers == AnalogNumbers.ROMAN) 0.8f else 1f)
            val nums = which.map { i ->
                val t = if (style.analogNumbers == AnalogNumbers.ROMAN) ROMAN[i] else (if (i == 0) 12 else i).toString()
                val a = Math.toRadians(i * 30.0)
                Triple(t, g.cx + sin(a).toFloat() * 118 * u - p.measure(t, fs) / 2, g.cy - cos(a).toFloat() * 118 * u + p.capEm * fs / 2)
            }
            val numLook = solid(numC)
            val dialP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dialC }
            val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = 1.5f * u; color = ring }
            val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = handC; strokeCap = Paint.Cap.ROUND }
            val m = d.now.minute; val hr = d.now.hour % 12 + m / 60f
            val k = g.k
            return@FaceCanvas {
                drawCircle(g.cx, g.cy, 156 * u, dialP)
                if (n > 0f) drawCircle(g.cx, g.cy, 156 * u - 0.75f * u, ringP)
                tick.strokeWidth = 2 * u; minor.strokeWidth = u
                for (t in ticks) drawLine(t[0], t[1], t[2], t[3], if (t[4] > 0f) tick else minor)
                for ((t, x, y) in nums) p.draw(this, t, x, y, fs, numLook)
                fun hand(deg: Float, tail: Float, len: Float, width: Float) {
                    val a = Math.toRadians(deg.toDouble()); val sx = sin(a).toFloat(); val cy = -cos(a).toFloat()
                    hand.strokeWidth = width * u
                    drawLine(g.cx - sx * tail * u, g.cy - cy * tail * u, g.cx + sx * len * u, g.cy + cy * len * u, hand)
                }
                hand(hr * 30f, 14f, 78f, 9f)
                hand(m * 6f, 18f, 126f, 6f)
                if (g.side) {
                    val tall = g.sideX == w / 2
                    fun x(width: Float) = if (tall) g.sideX - width / 2 else g.sideX
                    var y = g.sideTop
                    val wkS = "${d.weekday}요일"; wk.color = weekC
                    drawText(wkS, x(wk.measureText(wkS)), y + 11 * k + wk.capRatio() * wk.textSize / 2, wk)
                    y += 22 * k + 6 * k
                    val dt = "${d.month}월 ${d.day}일"; big.color = ink.ink.toArgb()
                    drawText(dt, x(big.measureText(dt)), y + 68 * 1.15f * k / 2 + big.capRatio() * big.textSize / 2, big)
                    y += 68 * 1.15f * k + 8 * k
                    val dim = ink.ink2.toArgb()
                    for (segs in lines) {
                        val cs = segs.map { Seg(it.text, dim, it.glyph) }
                        drawSegs(cs, x(ui.width(cs)), y + 22 * 1.4f * k / 2 + ui.capRatio() * ui.textSize / 2, ui)
                        y += 22 * 1.4f * k
                    }
                }
            }
        }
        // second hand: its own layer, the only thing that redraws each second
        FaceCanvas(Modifier.fillMaxSize().graphicsLayer()) {
            if (min(size.width, size.height) < COMPACT_MIN.toPx()) return@FaceCanvas {}
            val g = geo()
            val u = g.r / 156f
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secC; strokeWidth = 2.5f * u; strokeCap = Paint.Cap.ROUND }
            val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hubC }
            return@FaceCanvas {
                val a = second.intValue * Math.PI.toFloat() / 30f
                val sx = sin(a); val cy = -cos(a)
                drawLine(g.cx - sx * 30 * u, g.cy - cy * 30 * u, g.cx + sx * 134 * u, g.cy + cy * 134 * u, sp)
                drawCircle(g.cx, g.cy, 6 * u, hub)
            }
        }
    }
}

/* ------------------------------------------------------------------ 달력과 시계 */

/**
 * 달력과 시계 (c-cal): a tear-off page (244 × 300 dp of 360: [ClockStyle.calendarColor] month band, the day in the serif
 * at 168, the weekday) left of a serif time with weather · alarm under it. A tall box puts the page above the time;
 * when neither fits, the compact form = the time and one date line. Night: an outlined black page.
 */
@Composable
internal fun CalendarFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val pd = rememberTimePainter(style, weight = min(900, style.weight + 100))
    val head = rememberUi(700); val wd = rememberUi(600); val ui = rememberUi(500)
    val n = ink.night
    val look = Look.of(style, ink)
    val pageC = lerp(Color(FaceDefaults.PAGE), ink.bg, n).toArgb()
    val pageInk = lerp(Color(FaceDefaults.WALL_INK), NIGHT_INK, n).toArgb()
    val band = Color(style.calendarColor ?: FaceDefaults.CALENDAR).copy(alpha = 1f - n).toArgb()
    val bandInk = lerp(Color(WHITE), NIGHT_INK, n).toArgb()
    val rule = NIGHT_INK.copy(alpha = 0.35f * n).toArgb()
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height; val s = min(w, h)
        if (s < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, look)
        val k = s / 360f
        val margin = if (inSlot) 0.04f * s else 10.dp.toPx() + 0.04f * s
        val dim = ink.ink2.toArgb()
        ui.textSize = max(13.dp.toPx(), 20 * k)
        var info = d.infoSegs(dim)
        if (ui.width(info) > 0.9f * w) info = d.infoSegs(dim, alarm = false)
        val infoCap = ui.capRatio() * ui.textSize
        val sizePct = style.sizePct / 100f

        // page geometry: landscape = left column at the mockup's 34 dp; tall = centred above the time
        val land = w >= h
        // landscape: the mockup's 780 × 360 frame fitted to the box (a wide-but-short box keeps the page beside the time)
        val pk = if (land) min(h / 360f, w / 700f) else k
        var ph = 300 * pk; var pw = 244 * pk
        if (!land) { ph = min(ph, 0.5f * (h - 2 * margin)); pw = ph * 244 / 300 }
        val rtW = w - margin - pw - 34 * pk
        val tallFits = !land && h - 2 * margin >= ph + 120 * k
        if (land && rtW < 0.9f * pw || !land && !tallFits) {
            // compact: the time and one date line
            val date = "${d.month}월 ${d.day}일 ${d.weekday}요일"
            ui.textSize = max(13.dp.toPx(), min(24 * k, 0.9f * w / ui.measureText(date) * ui.textSize))
            val gap = 16 * k
            val fs = fitSize(p, d, false, (0.8f * h - gap - infoCap) * sizePct, 0.9f * w)
            val cap = p.capEm * fs
            val top = (h - cap - gap - ui.capRatio() * ui.textSize) / 2
            return@FaceCanvas {
                drawTime(p, d, fs, false, 0f, w / 2, top, look)
                ui.color = dim; capCentred(date, w / 2, top + cap + gap + ui.capRatio() * ui.textSize / 2, ui)
            }
        }
        val px: Float; val py: Float
        val timeCx: Float; val timeTop: Float; val fs: Float
        val gapI = 18 * k
        if (land) {
            px = max(margin, 34 * pk); py = (h - ph) / 2
            val rx = px + pw
            fs = fitSize(p, d, false, 0.39f * h * sizePct, 0.86f * (w - rx - margin))
            timeCx = (rx + w) / 2
            timeTop = (h - p.capEm * fs - gapI - infoCap) / 2
            val colW = 0.95f * (w - rx - margin)
            if (ui.width(info) > colW) info = d.infoSegs(dim, alarm = false)
            if (ui.width(info) > colW) ui.textSize *= colW / ui.width(info)
        } else {
            fs = fitSize(p, d, false, 0.2f * h * sizePct, 0.86f * w)
            val group = ph + 40 * k + p.capEm * fs + gapI + infoCap
            px = (w - pw) / 2; py = (h - group) / 2
            timeCx = w / 2; timeTop = py + ph + 40 * k
        }
        val kk = ph / 300f
        head.textSize = 26 * kk; wd.textSize = 24 * kk
        val dayFs = 168 * kk
        val clip = Path().apply { addRoundRect(px, py, px + pw, py + ph, 14 * kk, 14 * kk, Path.Direction.CW) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = 1.5f * kk; color = rule }
        val day = d.day.toString()
        return@FaceCanvas {
            save(); clipPath(clip)
            fill.color = pageC; drawRect(px, py, px + pw, py + ph, fill)
            fill.color = band; drawRect(px, py, px + pw, py + 58 * kk, fill)
            restore()
            if (n > 0f) {
                drawRoundRect(px + 0.75f * kk, py + 0.75f * kk, px + pw - 0.75f * kk, py + ph - 0.75f * kk, 14 * kk, 14 * kk, stroke)
                drawLine(px, py + 58 * kk, px + pw, py + 58 * kk, stroke)
            }
            head.color = bandInk; capCentred("${d.month}월", px + pw / 2, py + 29 * kk, head)
            pd.draw(this, day, px + (pw - pd.measure(day, dayFs)) / 2, py + 148 * kk + pd.capEm * dayFs / 2, dayFs, solid(pageInk))
            wd.color = pageInk; capCentred("${d.weekday}요일", px + pw / 2, py + 236 * kk, wd)
            drawTime(p, d, fs, false, 0f, timeCx, timeTop, look)
            if (info.isNotEmpty()) drawSegs(info, timeCx - ui.width(info) / 2, timeTop + p.capEm * fs + gapI + infoCap, ui)
        }
    }
}

/* ------------------------------------------------------------------ 한글 시계 */

/**
 * 한글 시계 (c-han): the time in words, left-aligned at 56 dp: 오전/오후 small and tracked, the hour line in
 * [ClockStyle.accentColor], the minute line in the text colour (116 dp of 360, sized against the widest words so it
 * never changes per minute). Right: a hairline column with the numeric time, weekday + date, weather + alarm. A tall
 * box drops the column and puts "9:41 · 월요일 11월 9일" under the words. Font = the Hangul font (`kfont`).
 */
@Composable
internal fun HangulFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val font = if (style.font.hangul) style.font else FontId.HANGUL_SERIF
    val tf = rememberClockTypeface(font, style.weight)
    val words = remember(tf) { uiPaint(tf, style.weight) }
    val ap = remember(tf) { uiPaint(tf, style.weight) }
    val mono = rememberTypeface(kr.dot.sb.ui.theme.Fonts.plexMono, 500).let { remember(it) { uiPaint(it, 500) } }
    val ui = rememberUi(500)
    val p = rememberTimePainter(style.copy(font = FontId.THEME))
    val hourC = ink.accent(Color(style.accentColor ?: FaceDefaults.ACCENT)).toArgb()
    val minC = ink.accent(Color(style.color)).toArgb()
    val look = Look.of(style, ink)
    val (amPm, hourW, minW) = hangulTime(d.now.hour, d.now.minute, d.use24h)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height; val s = min(w, h)
        if (s < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, look)
        val k = s / 360f
        val margin = if (inSlot) 0.05f * s else 10.dp.toPx() + 0.04f * s
        val dim = ink.ink2.toArgb(); val fg = ink.ink.toArgb()
        val tall = w < h * 1.3f
        // right column (landscape): "9:41" mono 44, then two dim 20 dp lines on top, two at the bottom
        mono.textSize = 44 * k; ui.textSize = max(13.dp.toPx(), 20 * k)
        val num = "${d.h}:${d.m}"
        val topLines = listOf(listOf(Seg("${d.weekday}요일", dim)), listOf(Seg("${d.month}월 ${d.day}일", dim)))
        val botLines = listOfNotNull(d.weatherLine?.let { listOf(Seg(it, dim)) }, d.alarmShort?.let { listOf(alarmSeg(dim), Seg(" $it", dim)) })
        val colW = maxOf(mono.measureText(num), (topLines + botLines).maxOf { ui.width(it) })
        val colX = if (tall) w else w - max(margin, 44 * k) - colW - 22 * k
        // words: sized against the widest hour / minute words of this clock mode
        val all = (0 until 24).map { hangulTime(it, 0, d.use24h).second } + (0 until 60).map { hangulTime(0, it, d.use24h).third }
        words.textSize = 100f
        val widest = all.maxOf { words.measureText(it) } / 100f
        val left = if (tall) margin else max(margin, 56 * k)
        val under = if (tall) "$num · ${d.weekday}요일 ${d.month}월 ${d.day}일" else null
        val underH = if (under == null) 0f else 24 * k + ui.capRatio() * ui.textSize
        val apH = if (amPm == null) 0f else 24 * k + 10 * k
        val maxW = (if (tall) w - 2 * left else colX - left - 24 * k)
        val fs = minOf(116 * k * style.sizePct / 100f, maxW / widest, (h - 2 * margin - apH - underH) / (2 * 1.14f))
        words.textSize = fs
        ap.textSize = 24 * k * min(1f, fs / (116 * k)); ap.letterSpacing = 0.3f
        val lh = 1.14f * fs
        val cap = words.capRatio() * fs
        val block = apH + 2 * lh + underH
        val top = (h - block) / 2
        val line = Paint().apply { color = dim; strokeWidth = max(1f, 1.dp.toPx()) }
        return@FaceCanvas {
            var y = top
            if (amPm != null) { ap.color = dim; drawText(amPm, left, y + ap.capRatio() * ap.textSize + (24 * k - ap.textSize) / 2, ap); y += apH }
            words.color = hourC; drawText(hourW, left, y + (lh + cap) / 2, words); y += lh
            words.color = minC; drawText(minW, left, y + (lh + cap) / 2, words); y += lh
            if (under != null) { ui.color = dim; drawText(ui.fit(under, w - 2 * left), left, y + underH, ui) }
            if (!tall) {
                val t = max(margin, 44 * k); val b = h - t
                drawLine(colX, t, colX, b, line)
                val x = colX + 22 * k
                mono.color = fg
                var ty = t + mono.capRatio() * mono.textSize
                drawText(num, x, ty, mono)
                ty += 10 * k
                for (l in topLines) { ty += ui.textSize * 1.5f; drawSegs(l, x, ty, ui) }
                var by = b
                for (l in botLines.reversed()) { drawSegs(l, x, by, ui); by -= ui.textSize * 1.5f }
            }
        }
    }
}
