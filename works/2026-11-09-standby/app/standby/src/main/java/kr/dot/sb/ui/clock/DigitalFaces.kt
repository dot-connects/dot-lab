package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.ClockLayout
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.LocalSlotLabel
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
import kotlin.math.max
import kotlin.math.min

/** One cached native-canvas drawing that fills the box; [build] runs on size/input change only. */
@Composable
internal fun FaceCanvas(modifier: Modifier, build: CacheDrawScope.() -> (Canvas.() -> Unit)) {
    Spacer(modifier.fillMaxSize().drawWithCache {
        val draw = build()
        onDrawBehind { drawIntoCanvas { it.nativeCanvas.draw() } }
    })
}

/** Text run made of coloured segments (e.g. weekday in accent + date in ink). [glyph] = the alarm glyph, not text. */
internal class Seg(val text: String, val color: Int, val glyph: Boolean = false)

/** The alarm glyph as a segment: icon box = the label size (§2.4), then a 0.3 em gap. */
internal fun alarmSeg(color: Int) = Seg("", color, glyph = true)

private const val GLYPH_GAP = 0.3f

internal fun Canvas.drawSegs(segs: List<Seg>, x: Float, baseline: Float, p: TextPaint): Float {
    var cx = x
    for (s in segs) {
        p.color = s.color
        if (s.glyph) {
            val box = p.textSize
            drawAlarmGlyph(cx, baseline - p.textSize * p.capRatio() / 2, box, s.color)
            cx += box * (1 + GLYPH_GAP)
        } else { drawText(s.text, cx, baseline, p); cx += p.measureText(s.text) }
    }
    return cx - x
}

internal fun TextPaint.width(segs: List<Seg>) = segs.sumOf { (if (it.glyph) textSize * (1 + GLYPH_GAP) else measureText(it.text)).toDouble() }.toFloat()

/** Alarm clock glyph (cards' Ic.ALARM, 24 grid, round strokes): box left edge [x], vertically centred on [cy]. */
internal fun Canvas.drawAlarmGlyph(x: Float, cy: Float, box: Float, color: Int) {
    val k = box / 24f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2f * k; strokeCap = Paint.Cap.ROUND }
    val y0 = cy - 12f * k
    fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(x + x1 * k, y0 + y1 * k, x + x2 * k, y0 + y2 * k, p)
    drawCircle(x + 12f * k, y0 + 13f * k, 7.5f * k, p)
    l(12f, 9.5f, 12f, 13f); l(12f, 13f, 14.5f, 15f); l(3.5f, 6f, 6.5f, 3f); l(20.5f, 6f, 17.5f, 3f)
}

/** Font size for digits: per-line cap ≤ [maxCap], widest string of the current hour-digit class ≤ [maxW] (§2.3.1). */
internal fun fitSize(p: TimePainter, d: FaceData, twoLines: Boolean, maxCap: Float, maxW: Float): Float {
    val hl = d.h.length
    val wEm = if (twoLines) max(p.hourEm(hl, d.use24h), p.minuteEm) * 1.03f else p.widestEm(hl, d.use24h)
    return min(maxCap / p.capEm, maxW / wEm)
}

/** Draw "h:mm" (one line, horizontally centred on [cx]) or hours over minutes; [capTop] = top of the first line. */
internal fun Canvas.drawTime(p: TimePainter, d: FaceData, size: Float, twoLines: Boolean, gap: Float, cx: Float, capTop: Float, look: Look) {
    val cap = p.capEm * size
    if (twoLines) {
        p.draw(this, d.h, cx - p.measure(d.h, size) / 2, capTop + cap, size, look)
        p.draw(this, d.m, cx - p.measure(d.m, size) / 2, capTop + 2 * cap + gap, size, look)
    } else {
        p.drawHm(this, d.h, d.m, cx - p.measureHm(d.h, d.m, size) / 2, capTop + cap, size, look)
    }
}

@Composable
internal fun rememberUi(weight: Int): TextPaint {
    val tf = rememberTypeface(LocalTheme.current.words(weight), weight)
    return androidx.compose.runtime.remember(tf) { uiPaint(tf, weight) }
}

/* ------------------------------------------------------------------ Digital */

/** Side column width reserved on Digital (review X1): fixed so the hero never shrinks for a long label. */
private val SIDE_MIN = 100.dp

@Composable
internal fun DigitalFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val ui = rememberUi(500)
    val look = Look.of(style, ink)
    val slotLabel = LocalSlotLabel.current
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val portrait = h > w * 1.15f
        val bg = if (inSlot) null else bgColors(style, ink)
        val sizePct = style.sizePct / 100f
        val accent = ink.acc.toArgb(); val white = ink.ink.toArgb(); val grey = ink.ink2.toArgb()
        val capR = ui.capRatio()

        if (inSlot) {
            val sz = Sizes.of(w.toDp(), h.toDp())
            // the card already took its pad off: only a really small box drops the date line
            val xs = sz.s < 140.dp
            val two = style.layout == ClockLayout.TWO_LINES || portrait
            // r5 desk: "월요일 11월 9일 · 7°" right under the digits, weekday in the clock colour
            ui.textSize = slotLabel?.toPx() ?: max(18.dp.toPx(), 0.075f * min(w, h))
            val info = if (xs || !style.showInfo) null else buildList {
                add(Seg("${d.weekday}요일 ", accent)); add(Seg("${d.month}월 ${d.day}일", white))
                d.temp?.let { add(Seg(" · $it", white)) }
            }.let { segs -> if (ui.width(segs) > w) listOf(Seg("${d.weekday}요일 ", accent), Seg("${d.month}월 ${d.day}일", white)) else segs }
            val infoH = if (info == null) 0f else ui.textSize * capR + sz.label.toPx() * 0.9f
            val gap = h * 0.04f
            val maxCap = ((if (xs) h * 0.8f else h * 0.86f - sz.pad.toPx() - infoH) - if (two) gap else 0f) / (if (two) 2 else 1) * sizePct
            val fs = fitSize(p, d, two, maxCap, w * 0.9f)
            val cap = p.capEm * fs
            val blockH = (if (two) 2 * cap + gap else cap) + infoH
            val top = (h - blockH) / 2
            return@FaceCanvas {
                drawTime(p, d, fs, two, gap, w / 2, top, look)
                if (info != null) drawSegs(info, (w - ui.width(info)) / 2, top + blockH, ui)
            }
        }

        ui.textSize = 0.06f * min(w, h) / capR
        val wk = Seg(d.weekday + " ", accent)
        val alarm = d.alarmShort?.let { listOf(alarmSeg(grey), Seg(it, white)) }
        val rows = buildList {
            add(listOf(wk, Seg(d.day.toString(), white)))
            d.temp?.let { add(listOf(Seg(it, white))) }
            alarm?.let { add(it) }
        }
        val layout = when {
            // portrait, and near-square screens (w/h < 1.45: Fold inner, Flip) where a side column would shrink the digits
            portrait || w < h * 1.45f -> ClockLayout.TWO_LINES
            !style.showInfo && style.layout == ClockLayout.IPHONE -> ClockLayout.CENTER
            else -> style.layout
        }
        val labelCap = ui.textSize * capR
        when (layout) {
            ClockLayout.IPHONE -> {
                val sideW = max(SIDE_MIN.toPx(), rows.maxOf { ui.width(it) })
                val margin = Grid.margin(min(w, h).toDp()).toPx(); val g = 0.025f * w
                // r5: digits as big as the iPhone's (≥ 60 % H): the side column may use half the right margin, and
                // Sofia has no kerning to reserve slack for (×1.03 cancels widestEm's slack)
                val fs = fitSize(p, d, false, 0.69f * h * sizePct, (w - 1.5f * margin - sideW - g) * 1.03f)
                val cap = p.capEm * fs
                // digits centred in the class's reserved width, so the side column never moves within the hour class
                val res = p.widestEm(d.h.length, d.use24h) * fs
                val tw = p.measureHm(d.h, d.m, fs)
                val x0 = margin + (w - 1.5f * margin - (res + g + sideW)) / 2
                val capTop = (h - cap) / 2
                val step = ui.textSize * 1.25f
                return@FaceCanvas {
                    drawFaceBg(bg, w, h, 0f)
                    p.drawHm(this, d.h, d.m, x0 + (res - tw) / 2, capTop + cap, fs, look)
                    rows.forEachIndexed { i, r -> drawSegs(r, x0 + res + g, capTop + labelCap + i * step, ui) }
                }
            }
            else -> {
                val two = layout == ClockLayout.TWO_LINES
                val info = if (!style.showInfo) null else buildList<Seg> {
                    add(wk); add(Seg(d.day.toString(), white))
                    d.temp?.let { add(Seg("  ·  $it", white)) }
                    alarm?.let { add(Seg("  ·  ", white)); addAll(it) }
                }
                // one info row: shrink (never wrap) when it is wider than the screen (portrait)
                if (info != null) ui.textSize *= min(1f, 0.9f * w / ui.width(info))
                val infoH = if (info == null) 0f else ui.textSize * capR + 0.06f * h
                val gap = 0.04f * h
                val maxCap = if (two) min(0.36f * h, (0.86f * h - infoH - gap) / 2) else min(0.62f * h, 0.86f * h - infoH)
                val fs = fitSize(p, d, two, maxCap * sizePct, 0.86f * w)
                val cap = p.capEm * fs
                val blockH = (if (two) 2 * cap + gap else cap) + infoH
                val top = (h - blockH) / 2
                return@FaceCanvas {
                    drawFaceBg(bg, w, h, 0f)
                    drawTime(p, d, fs, two, gap, w / 2, top, look)
                    if (info != null) drawSegs(info, (w - ui.width(info)) / 2, top + blockH, ui)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Desk */

@Composable
internal fun DeskFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val lab = rememberUi(600)
    val ttl = rememberUi(700)
    val look = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val portrait = w < h * 1.45f // near-square stacks too
        val bg = if (inSlot) null else bgColors(style, ink)
        val sizePct = style.sizePct / 100f
        val white = ink.ink.toArgb(); val grey = ink.ink2.toArgb()
        // Full form (review X2): "다음 알람 · 내일" over "오전 6:30". Empty rows keep the template with "없음" (§5.4).
        val rows = listOf(
            if (d.alarm != null) Triple("다음 알람 · ${d.alarmDay}", "${d.alarmAmPm}${d.alarm}", white) else Triple("알람", "없음", grey),
            if (d.weatherLine != null) Triple(d.city ?: "날씨", d.weatherLine, white) else Triple("날씨", "없음", grey),
            if (d.mediaLine != null) Triple(d.mediaApp ?: "음악", d.mediaLine, white) else Triple("음악", "재생 중 아님", grey),
        )
        val margin = if (inSlot) Sizes.of(w.toDp(), h.toDp()).pad.toPx() else 0.06f * w
        val labCap = lab.capRatio(); val ttlCap = ttl.capRatio()

        // digits box and rows box
        val fs: Float; val dx: Float; val capTop: Float
        val rx: Float; val rTop: Float; val rBottom: Float; val rW: Float
        if (portrait) {
            fs = fitSize(p, d, false, 0.24f * h * sizePct, w - 2 * margin)
            val cap = p.capEm * fs
            capTop = 0.14f * h
            dx = w / 2
            rx = margin; rW = w - 2 * margin
            rTop = capTop + cap + 0.1f * h; rBottom = h - 0.12f * h
        } else {
            // digits in the left 55 %, rows from 60 % (design-system §4.3 Desk)
            val digitR = 0.55f * w
            fs = fitSize(p, d, false, 0.46f * h * sizePct, digitR - margin)
            val cap = p.capEm * fs
            capTop = (h - cap) / 2
            dx = (margin + digitR) / 2
            rx = 0.6f * w; rW = w - margin - rx
            rTop = capTop; rBottom = capTop + cap
        }
        // review C2: row pitch = digit cap / 3; title 0.38 × pitch, label 0.24 × pitch, floors 22 / 18 dp
        val pitch0 = p.capEm * fs / 3
        lab.textSize = max(18.dp.toPx(), 0.24f * pitch0)
        ttl.textSize = max(22.dp.toPx(), 0.38f * pitch0)
        // each row = label cap-top … title baseline; rows spread space-between from rTop to rBottom
        val gapLT = lab.textSize * 0.55f
        val blockH = lab.textSize * labCap + gapLT + ttl.textSize * ttlCap
        val pitch = (rBottom - rTop - blockH) / 2
        val sep = Paint().apply { color = ink.ink3.toArgb(); strokeWidth = max(1f, 1.dp.toPx()) }
        val texts = rows.map { (l, t, _) -> lab.fit(l, rW) to ttl.fit(t, rW) }
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            drawTime(p, d, fs, false, 0f, dx, capTop, look)
            rows.forEachIndexed { i, r ->
                val top = rTop + i * pitch
                val tBase = top + blockH
                lab.color = grey
                drawText(texts[i].first, rx, top + lab.textSize * labCap, lab)
                ttl.color = r.third
                drawText(texts[i].second, rx, tBase, ttl)
                val y1 = (tBase + top + pitch) / 2
                if (i < 2) drawLine(rx, y1, rx + rW, y1, sep)
            }
        }
    }
}

/* ------------------------------------------------------------------ Solar */

private fun contrast(a: Int, b: Int): Float {
    val la = Color(a).luminance(); val lb = Color(b).luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

@Composable
internal fun SolarFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val bg = bgColors(style, ink, nightPlate = Ink.NIGHT_INK4)
    val base = Look.of(style, ink)
    // Contrast floor (§4.3): digits vs local bg ≥ 3:1, else weight ≥ 500 + soft dark shadow.
    val low = bg != null && ink.night < 0.5f && (
        if (base.effect == Effect.TWO_COLOR) min(contrast(base.c1, bg.first), contrast(base.c2, bg.second)) < 3f
        else min(contrast(base.c1, bg.first), contrast(base.c1, bg.second)) < 3f)
    val p = rememberTimePainter(style, if (low) max(style.weight, 500) else style.weight)
    val look = if (low) Look(base.effect, base.c1, base.c2, shadow = SOLAR_SHADOW) else base
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val two = (if (inSlot) h > w * 1.15f else w < h * 1.45f) || style.layout == ClockLayout.TWO_LINES
        val sizePct = style.sizePct / 100f
        val r = if (inSlot) max(0.05f * min(w, h), 12.dp.toPx()) else 0f
        val gap = 0.03f * h
        val fs = if (inSlot) fitSize(p, d, two, (if (two) 0.32f else 0.5f) * h * sizePct, 0.8f * w)
        else fitSize(p, d, two, (if (two) 0.30f else 0.40f) * h * sizePct, (if (two) 0.76f else 0.68f) * w)
        val cap = p.capEm * fs
        val top = (h - (if (two) 2 * cap + gap else cap)) / 2
        return@FaceCanvas {
            drawFaceBg(bg ?: (Color.Black.toArgb() to Color.Black.toArgb()), w, h, r)
            drawTime(p, d, fs, two, gap, w / 2, top, look)
        }
    }
}

/** Solar low-contrast shadow: 6 % black was invisible on the pastel plate (review C4); 22 % gives the digits an edge. */
private const val SOLAR_SHADOW = 0x38000000

/* ------------------------------------------------------------------ Photos time */

/**
 * Time on the Photos page (review C1, iPhone `photos-dan`): placed from the screen, not a corner pad — right edge at
 * 92 % W, cap-top at 10 % H, cap 15 % H. Width allowance x 66-92 % (not 72-92): the frame's 72 % fits SF's narrow
 * "5:19", Inter's widest class-1 string needs ~2.9 caps. Same rule on every device.
 */
@Composable
internal fun PhotosTime(style: ClockStyle, d: FaceData, ink: Ink, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val look = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val fs = min(0.15f * h / p.capEm, 0.26f * w / p.widestEm(d.h.length, d.use24h)) * style.sizePct.coerceAtMost(100) / 100f
        val tw = p.measureHm(d.h, d.m, fs)
        return@FaceCanvas { p.drawHm(this, d.h, d.m, 0.92f * w - tw, 0.10f * h + p.capEm * fs, fs, look) }
    }
}

/* ------------------------------------------------------------------ StyledTime */

@Composable
internal fun StyledTimeImpl(
    text: String, style: ClockStyle, ink: Ink, modifier: Modifier, color: Color, effects: Boolean, widest: String?,
    align: Alignment.Horizontal,
) {
    val p = rememberTimePainter(style, tnum = true)
    val look = Look.of(style, ink, base = color, effects = effects)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val wEm = p.widestEm(widest ?: text)
        val fs = min(h * 0.9f / (p.capEm + p.descEm), w / wEm) * style.sizePct.coerceAtMost(100) / 100f
        val tw = p.measure(text, fs)
        val x = when (align) { Alignment.Start -> 0f; Alignment.End -> w - tw; else -> (w - tw) / 2 }
        val baseline = (h + (p.capEm - p.descEm) * fs) / 2
        return@FaceCanvas { p.draw(this, text, x, baseline, fs, look) }
    }
}
