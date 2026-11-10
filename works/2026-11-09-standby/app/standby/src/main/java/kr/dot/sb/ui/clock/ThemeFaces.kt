package kr.dot.sb.ui.clock

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.weatherIc
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Theme B and C versions of the two faces whose layout (not just colour) differs per theme (mockup r5 B, C):
 * Digital = B masthead between rules / C glowing digits over three pills; Analog = B hairline dial beside a date
 * article / C lamp plate dial beside pills. Same lightness rules as the iPhone faces: one cached canvas per minute,
 * the second hand on its own layer at 1 Hz, every glow a static gradient (none at night).
 */

/** Seconds 0-59, ticking at 1 Hz off the frame clock while composed (analog second hands). */
@Composable
internal fun rememberSecond(): IntState {
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

private fun pt(cx: Float, cy: Float, deg: Float, r: Float): Pair<Float, Float> {
    val a = Math.toRadians(deg.toDouble())
    return (cx + sin(a).toFloat() * r) to (cy - cos(a).toFloat() * r)
}

/* ------------------------------------------------------------------ B: masthead Digital */

/**
 * B Digital (mockup r5 B): masthead "11월 9일 월요일" with "서울 7° · 알람 6:30" on the right, a rule, the serif time
 * with a vermilion colon, a rule, then "오늘 남은 시간 2:19" and "제 313 일". Portrait: two masthead rows, hours over
 * minutes. Every word is at least 6 % of the short side.
 */
@Composable
internal fun PaperDigital(style: ClockStyle, d: FaceData, ink: Ink, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val head = rememberUi(700)
    val kick = rememberUi(500)
    val look = Look.of(style, ink)
    val colon = Look(Effect.SOLID, ink.weekday.toArgb(), ink.weekday.toArgb())
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val short = min(w, h)
        val two = h > w
        val m = Grid.margin(short.toDp()).toPx()
        val mx = max(m, 0.06f * w)
        val cw = w - 2 * mx
        head.textSize = 0.068f * short
        kick.textSize = 0.06f * short
        kick.letterSpacing = 0.08f
        val hc = head.capRatio(); val kc = kick.capRatio()
        val right = listOfNotNull(
            d.temp?.let { t -> d.city?.let { "$it $t" } ?: t },
            d.alarmShort?.let { "알람 $it" },
        ).joinToString(" · ")
        val oneRow = head.measureText(d.dateLong) + kick.measureText(right) + 0.05f * w <= cw
        val dateBase = m + head.textSize * hc
        val kickBase = if (oneRow || right.isEmpty()) dateBase else dateBase + 0.035f * short + kick.textSize * kc
        val rule1 = kickBase + 0.045f * short
        val footBase = h - m
        val rule2 = footBase - kick.textSize * kc - 0.045f * short
        val leftMin = 24 * 60 - (d.now.hour * 60 + d.now.minute)
        val remain = "%d:%02d".format(leftMin / 60, leftMin % 60)
        val dayN = "제 ${d.now.dayOfYear} 일"
        val leftFoot = listOf("오늘 남은 시간 $remain", "남은 시간 $remain").firstOrNull { kick.measureText(it) + kick.measureText(dayN) + 0.04f * w <= cw } ?: remain
        val zoneTop = rule1 + 0.05f * short; val zoneBot = rule2 - 0.05f * short
        val gap = 0.03f * h
        val sizePct = style.sizePct / 100f
        val maxCap = (if (two) (zoneBot - zoneTop - gap) / 2 else zoneBot - zoneTop) * sizePct
        val fs = fitSize(p, d, two, maxCap, cw * 1.03f)
        val cap = p.capEm * fs
        val top = (zoneTop + zoneBot - (if (two) 2 * cap + gap else cap)) / 2
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.ink3.toArgb(); strokeWidth = max(1f, 1.dp.toPx()) }
        val bg = bgColors(style, ink)
        val fg = ink.ink.toArgb(); val fg2 = ink.ink2.toArgb()
        val date = head.fit(d.dateLong, cw)
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            head.color = fg; drawText(date, mx, dateBase, head)
            kick.color = fg2
            if (right.isNotEmpty()) {
                if (oneRow) drawText(right, w - mx - kick.measureText(right), kickBase, kick) else drawText(kick.fit(right, cw), mx, kickBase, kick)
            }
            drawLine(mx, rule1, w - mx, rule1, rp)
            if (two) {
                p.draw(this, d.h, mx, top + cap, fs, look)
                p.draw(this, d.m, mx, top + 2 * cap + gap, fs, look)
            } else p.drawHm(this, d.h, d.m, mx, top + cap, fs, look, colon)
            drawLine(mx, rule2, w - mx, rule2, rp)
            drawText(leftFoot, mx, footBase, kick)
            drawText(dayN, w - mx - kick.measureText(dayN), footBase, kick)
        }
    }
}

/* ------------------------------------------------------------------ C: lamp Digital */

/** The C chips: "월 9일", weather glyph + "7°", alarm glyph + "6:30" on round plate2 pills. */
@Composable
internal fun LampPills(d: FaceData, ink: Ink, text: Dp, modifier: Modifier, column: Boolean = false) {
    val items = buildList {
        add(Triple<Ic?, String, String>(null, if (column) "${d.weekday}요일" else "${d.weekday} ${d.day}일", if (column) "${d.day}" else ""))
        d.temp?.let { add(Triple(d.cond?.let(::weatherIc) ?: Ic.CLOUD, "", it)) }
        d.alarmShort?.let { add(Triple(Ic.ALARM, "", it)) }
    }
    val pill = @Composable { ic: Ic?, word: String, num: String, mod: Modifier ->
        Row(
            mod.height(text * (if (column) 2.6f else 1.95f)).clip(RoundedCornerShape(50)).background(ink.plate2)
                .padding(horizontal = text * (if (column) 0.75f else 0.6f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ic != null) {
                IconView(ic, ink.ink, Modifier.size(text * 1.05f), ink.plate2, color2 = if (ic == Ic.ALARM) ink.ink else ink.sun)
                Spacer(Modifier.width(text * 0.35f))
            }
            if (word.isNotEmpty()) Txt(word, text, ink.ink, weight = 500, num = true)
            if (word.isNotEmpty() && num.isNotEmpty()) Spacer(Modifier.width(text * 0.3f))
            // the value in the lamp's heavy rounded numerals (column = the analog face's big chips)
            if (num.isNotEmpty()) Txt(num, if (column) text * 1.2f else text, ink.ink, weight = if (column) 900 else 500, family = if (column) Fonts.nunitoHeavy else null, cap = if (column) 0.705f else 0.707f, num = !column)
        }
    }
    if (column) Column(modifier, verticalArrangement = Arrangement.spacedBy(text * 0.55f)) {
        items.forEach { (ic, w, n) -> pill(ic, w, n, Modifier.fillMaxWidth()) }
    } else Row(modifier, horizontalArrangement = Arrangement.spacedBy(text * 0.45f)) {
        items.forEach { (ic, w, n) -> pill(ic, w, n, Modifier) }
    }
}

/**
 * C Digital (mockup r5 C): butter → peach digits (top to bottom) centred over a soft peach pool of light, three pills
 * along the bottom. Portrait: hours over minutes. The pool is a static radial gradient and is gone at night.
 */
@Composable
internal fun LampDigital(style: ClockStyle, d: FaceData, ink: Ink, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val look = Look.of(style, ink, vertical = true)
    val pool = lerp(Color.Transparent, ink.orange.copy(alpha = 0.22f), 1f - ink.night)
    BoxWithConstraints(modifier) {
        val short = minOf(maxWidth, maxHeight)
        val text = short * 0.066f
        val pillH = text * 1.95f
        val bottom = maxHeight * 0.09f
        val reserve = bottom + pillH + short * (if (style.showInfo) 0.07f else 0f)
        FaceCanvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val two = h > w
            val gap = 0.03f * h
            val zoneTop = 0.06f * h
            val zoneBot = h - (if (style.showInfo) reserve.toPx() else 0.06f * h)
            val maxCap = (if (two) (zoneBot - zoneTop - gap) / 2 else zoneBot - zoneTop) * style.sizePct / 100f
            val fs = fitSize(p, d, two, maxCap, 0.9f * w)
            val cap = p.capEm * fs
            val blockH = if (two) 2 * cap + gap else cap
            val top = (zoneTop + zoneBot - blockH) / 2
            val bg = bgColors(style, ink)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    w / 2, top + blockH * 0.45f, max(blockH, w * 0.4f), pool.toArgb(), Color.Transparent.toArgb(), android.graphics.Shader.TileMode.CLAMP,
                )
            }
            return@FaceCanvas {
                drawFaceBg(bg, w, h, 0f)
                if (pool.alpha > 0f) drawRect(0f, 0f, w, h, glow)
                drawTime(p, d, fs, two, gap, w / 2, top, look)
            }
        }
        if (style.showInfo) LampPills(d, ink, text, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
    }
}

/* ------------------------------------------------------------------ B: hairline Analog */

/**
 * B Analog (mockup r5 B): 60 hairline ticks, italic serif 12 · 3 · 6 · 9, thin ink hands, vermilion second hand with
 * a ring on its tail. Full-screen landscape adds the date "article" left of a vertical rule.
 */
@Composable
internal fun PaperAnalog(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val italic = rememberTypeface(Fonts.instrumentItalic, 400)
    val num = remember(italic, style.font, style.numerals) {
        if (style.font == FontId.SERIF) TimePainter(italic, 400, FontId.SERIF, 100, style.numerals) else null
    } ?: p
    val head = rememberUi(800)
    val sub = rememberUi(500)
    val numLook = Look.of(style, ink, base = Color(style.numeralColor), effects = false)
    val dayLook = Look.of(style, ink, base = ink.pal.fg, effects = false)
    val major = ink.accent(Color(style.tickColor)).toArgb()
    val minor = ink.ink2.toArgb()
    val hand = ink.accent(Color(style.handColor)).toArgb()
    val sec = ink.accent(Color(style.secondColor)).toArgb()
    val second = rememberSecond()
    // dial centre / radius from the box: wide full screen = right of the article; else centred
    fun geo(w: Float, h: Float): FloatArray {
        val wide = !inSlot && w > h * 1.2f
        val port = !inSlot && h > w * 1.2f
        return when {
            wide -> floatArrayOf(0.705f * w, 0.5f * h, 0.413f * h)
            port -> floatArrayOf(w / 2, 0.42f * h, 0.42f * w)
            else -> floatArrayOf(w / 2, h / 2, 0.46f * min(w, h))
        }
    }
    Box(modifier) {
        FaceCanvas(Modifier.graphicsLayer()) {
            val w = size.width; val h = size.height
            val (cx, cy, r) = geo(w, h)
            val k = r / 196f
            val wide = !inSlot && w > h * 1.2f
            val port = !inSlot && h > w * 1.2f
            val mj = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = major; strokeWidth = max(1.5f, 2.4f * k) }
            val mn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = minor; strokeWidth = max(1f, 1.2f * k) }
            val ticks = style.analogTicks != kr.dot.sb.model.AnalogTicks.NONE
            val numSize = 64f * k * 0.72f / num.capEm * style.sizePct / 100f
            val numbers = if (style.analogNumbers == AnalogNumbers.NONE) emptyList() else listOf(0, 3, 6, 9)
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hand; strokeCap = Paint.Cap.ROUND }
            val mi = d.now.minute; val hr = d.now.hour % 12 + mi / 60f
            val bg = if (inSlot) null else bgColors(style, ink)
            // the article (wide) / the date line (portrait)
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.ink3.toArgb(); strokeWidth = max(1f, 1.dp.toPx()) }
            head.textSize = 0.08f * min(w, h); head.color = ink.ink.toArgb()
            sub.textSize = 0.06f * min(w, h); sub.color = ink.ink2.toArgb()
            val dayFs = 0.38f * h / p.capEm
            val ax = 0.06f * w
            val month = listOfNotNull("${d.month}월", d.city?.let { c -> d.temp?.let { "$c $it" } }).joinToString(" · ")
            return@FaceCanvas {
                drawFaceBg(bg, w, h, 0f)
                if (wide) {
                    p.draw(this, d.day.toString(), ax, 0.15f * h + p.capEm * dayFs, dayFs, dayLook)
                    drawText(head.fit("${d.weekday}요일", 0.29f * w), ax, 0.72f * h, head)
                    drawText(sub.fit(month, 0.29f * w), ax, 0.83f * h, sub)
                    drawLine(0.37f * w, 0.09f * h, 0.37f * w, 0.91f * h, rp)
                } else if (port) {
                    val t = head.fit(d.dateLong, 0.88f * w)
                    drawText(t, (w - head.measureText(t)) / 2, cy + r + 0.11f * h, head)
                }
                if (ticks) for (i in 0 until 60) {
                    val big = i % 5 == 0
                    if (!big && style.analogTicks == kr.dot.sb.model.AnalogTicks.HOURS) continue
                    val (x1, y1) = pt(cx, cy, i * 6f, r)
                    val (x2, y2) = pt(cx, cy, i * 6f, r - (if (big) 24f else 11f) * k)
                    drawLine(x1, y1, x2, y2, if (big) mj else mn)
                }
                for (n in numbers) {
                    val label = if (n == 0) "12" else n.toString()
                    val (x, y) = pt(cx, cy, n * 30f, r - 62f * k)
                    num.draw(this, label, x - num.measure(label, numSize) / 2, y + num.capEm * numSize / 2, numSize, numLook)
                }
                hp.strokeWidth = 6f * k
                val (hx, hy) = pt(cx, cy, hr * 30f, 104f * k); drawLine(cx, cy, hx, hy, hp)
                hp.strokeWidth = max(2f, 3.2f * k)
                val (mx2, my2) = pt(cx, cy, mi * 6f, 160f * k); drawLine(cx, cy, mx2, my2, hp)
            }
        }
        FaceCanvas(Modifier.fillMaxSize().graphicsLayer()) {
            val (cx, cy, r) = geo(size.width, size.height)
            val k = r / 196f
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec; strokeWidth = max(1.5f, 1.6f * k); strokeCap = Paint.Cap.ROUND }
            val ring = Paint(sp).apply { this.style = Paint.Style.STROKE }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec }
            return@FaceCanvas {
                val a = second.intValue * 6f
                val (tx, ty) = pt(cx, cy, a + 180f, 44f * k)
                val (ex, ey) = pt(cx, cy, a, 172f * k)
                drawLine(tx, ty, ex, ey, sp)
                drawCircle(tx, ty, 7f * k, ring)
                drawCircle(cx, cy, 5f * k, dot)
            }
        }
    }
}

/* ------------------------------------------------------------------ C: lamp plate Analog */

/**
 * C Analog (mockup r5 C): the dial is a warm plate lit from the top-left, butter bars at 12 · 3 · 6 · 9, soft dots for
 * the other hours, fat round ink hands, peach second hand and cap. Full screen adds the pills (a column on the right
 * in landscape, a row under the dial in portrait).
 */
@Composable
internal fun LampAnalog(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val second = rememberSecond()
    val bars = ink.accent(Color(style.numeralColor)).toArgb()
    val dots = ink.mapped(Color(style.tickColor)).toArgb()
    val hand = ink.accent(Color(style.handColor)).toArgb()
    val sec = ink.accent(Color(style.secondColor)).toArgb()
    val lit = lerp(ink.plate2, ink.mapped(Color(style.color)), 0.22f).toArgb()
    val deep = ink.plate.toArgb()
    BoxWithConstraints(modifier) {
        val wide = !inSlot && maxWidth > maxHeight * 1.2f
        val port = !inSlot && maxHeight > maxWidth * 1.2f
        val short = minOf(maxWidth, maxHeight)
        val dial = when {
            wide -> minOf(maxHeight * 0.94f, maxWidth * 0.58f)
            port -> minOf(maxWidth * 0.86f, maxHeight * 0.6f)
            else -> short
        }
        val dialMod = when {
            wide -> Modifier.align(Alignment.CenterStart).padding(start = maxWidth * 0.03f).size(dial)
            port -> Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.1f).size(dial)
            else -> Modifier.align(Alignment.Center).size(dial)
        }
        Box(dialMod) {
            FaceCanvas(Modifier.graphicsLayer()) {
                val s = min(size.width, size.height)
                val cx = size.width / 2; val cy = size.height / 2
                val r = s * 0.48f; val k = r / 192f
                val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(
                        cx - 0.4f * r, cy - 0.6f * r, 1.8f * r, intArrayOf(lit, deep, deep), floatArrayOf(0f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP,
                    )
                }
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bars; strokeWidth = 18f * k; strokeCap = Paint.Cap.ROUND }
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dots }
                val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hand; strokeCap = Paint.Cap.ROUND }
                val mi = d.now.minute; val hr = d.now.hour % 12 + mi / 60f
                val withBars = style.analogNumbers != AnalogNumbers.NONE
                return@FaceCanvas {
                    drawCircle(cx, cy, r, plate)
                    for (h in 1..12) {
                        if (h % 3 == 0 && withBars) {
                            val (x1, y1) = pt(cx, cy, h * 30f, r - 30f * k); val (x2, y2) = pt(cx, cy, h * 30f, r - 58f * k)
                            drawLine(x1, y1, x2, y2, bp)
                        } else {
                            val (x, y) = pt(cx, cy, h * 30f, r - 40f * k)
                            drawCircle(x, y, 7f * k, dp)
                        }
                    }
                    hp.strokeWidth = 24f * k
                    val (hx, hy) = pt(cx, cy, hr * 30f, 92f * k); drawLine(cx, cy, hx, hy, hp)
                    hp.strokeWidth = 16f * k
                    val (mx2, my2) = pt(cx, cy, mi * 6f, 140f * k); drawLine(cx, cy, mx2, my2, hp)
                }
            }
            FaceCanvas(Modifier.fillMaxSize().graphicsLayer()) {
                val s = min(size.width, size.height)
                val cx = size.width / 2; val cy = size.height / 2
                val k = s * 0.48f / 192f
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec; strokeWidth = 6f * k; strokeCap = Paint.Cap.ROUND }
                val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec }
                return@FaceCanvas {
                    val a = second.intValue * 6f
                    val (tx, ty) = pt(cx, cy, a + 180f, 26f * k)
                    val (ex, ey) = pt(cx, cy, a, 150f * k)
                    drawLine(tx, ty, ex, ey, sp)
                    drawCircle(cx, cy, 13f * k, cap)
                }
            }
        }
        if (wide) LampPills(
            d, ink, short * 0.074f,
            Modifier.align(Alignment.CenterEnd).padding(end = maxWidth * 0.05f).width(maxWidth * 0.32f), column = true,
        )
        if (port) LampPills(d, ink, short * 0.066f, Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.08f))
    }
}
