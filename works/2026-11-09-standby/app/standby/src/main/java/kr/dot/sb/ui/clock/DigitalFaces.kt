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
import kr.dot.sb.ui.theme.Ink
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

/** Text run made of coloured segments (e.g. weekday in accent + date in ink). */
internal class Seg(val text: String, val color: Int)

internal fun Canvas.drawSegs(segs: List<Seg>, x: Float, baseline: Float, p: TextPaint): Float {
    var cx = x
    for (s in segs) { p.color = s.color; drawText(s.text, cx, baseline, p); cx += p.measureText(s.text) }
    return cx - x
}

internal fun TextPaint.width(segs: List<Seg>) = segs.sumOf { measureText(it.text).toDouble() }.toFloat()

/** Font size for digits: per-line cap ≤ [maxCap], widest string ≤ [maxW] (design-system §2.3.1). */
internal fun fitSize(p: TimePainter, use24h: Boolean, twoLines: Boolean, maxCap: Float, maxW: Float): Float {
    val wEm = if (twoLines) max(p.hourEm(use24h), p.minuteEm) * 1.03f else p.widestEm(use24h)
    return min(maxCap / p.capEm, maxW / wEm)
}

/** Draw "h:mm" (one line, horizontally centred on [cx]) or hours over minutes; [capTop] = top of the first line. */
internal fun Canvas.drawTime(p: TimePainter, d: FaceData, size: Float, twoLines: Boolean, gap: Float, cx: Float, capTop: Float, look: Look) {
    val cap = p.capEm * size
    if (twoLines) {
        p.draw(this, d.h, cx - p.measure(d.h, size) / 2, capTop + cap, size, look)
        p.draw(this, d.m, cx - p.measure(d.m, size) / 2, capTop + 2 * cap + gap, size, look)
    } else {
        val t = "${d.h}:${d.m}"
        p.draw(this, t, cx - p.measure(t, size) / 2, capTop + cap, size, look)
    }
}

@Composable
internal fun rememberUi(weight: Int): TextPaint {
    val tf = rememberTypeface(Fonts.ui(weight), weight)
    return androidx.compose.runtime.remember(tf) { uiPaint(tf, weight) }
}

/* ------------------------------------------------------------------ Digital */

@Composable
internal fun DigitalFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val ui = rememberUi(600)
    val look = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val portrait = h > w * 1.15f
        val bg = if (inSlot) null else bgColors(style, ink)
        val sizePct = style.sizePct / 100f
        val accent = ink.acc.toArgb(); val white = ink.ink.toArgb(); val grey = ink.ink2.toArgb()
        val capR = ui.capRatio()

        if (inSlot) {
            val sz = Sizes.of(w.toDp(), h.toDp())
            val xs = SizeClass.of(sz.s) == SizeClass.XS
            val two = style.layout == ClockLayout.TWO_LINES || portrait
            ui.textSize = sz.label.toPx()
            val info = if (xs || !style.showInfo) null else listOf(Seg(d.dateLong, grey))
            val infoH = if (info == null) 0f else ui.textSize * capR + sz.label.toPx() * 0.9f
            val gap = h * 0.04f
            val maxCap = ((if (xs) h * 0.8f else h * 0.86f - sz.pad.toPx() - infoH) - if (two) gap else 0f) / (if (two) 2 else 1) * sizePct
            val fs = fitSize(p, d.use24h, two, maxCap, w * 0.9f)
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
        val rows = buildList {
            add(listOf(wk, Seg(d.day.toString(), white)))
            d.temp?.let { add(listOf(Seg(it, white))) }
            d.alarm?.let { add(listOf(Seg("알람 ", grey), Seg(it, white))) }
        }
        val layout = when {
            // portrait, and near-square screens (Fold inner, Flip) where a side column would shrink the digits
            portrait || w < h * 1.45f -> ClockLayout.TWO_LINES
            !style.showInfo && style.layout == ClockLayout.IPHONE -> ClockLayout.CENTER
            else -> style.layout
        }
        val labelCap = ui.textSize * capR
        when (layout) {
            ClockLayout.IPHONE -> {
                val sideW = rows.maxOf { ui.width(it) }
                val margin = 0.06f * w; val g = 0.045f * w
                val fs = fitSize(p, d.use24h, false, 0.69f * h * sizePct, w - 2 * margin - sideW - g)
                val cap = p.capEm * fs
                val t = "${d.h}:${d.m}"
                val tw = p.measure(t, fs)
                val x0 = (w - (tw + g + sideW)) / 2
                val capTop = (h - cap) / 2
                val step = ui.textSize * 1.25f
                return@FaceCanvas {
                    drawFaceBg(bg, w, h, 0f)
                    p.draw(this, t, x0, capTop + cap, fs, look)
                    rows.forEachIndexed { i, r -> drawSegs(r, x0 + tw + g, capTop + labelCap + i * step, ui) }
                }
            }
            else -> {
                val two = layout == ClockLayout.TWO_LINES
                val info = if (!style.showInfo) null else buildList<Seg> {
                    add(wk); add(Seg(d.day.toString(), white))
                    d.temp?.let { add(Seg("  ·  $it", white)) }
                    d.alarm?.let { add(Seg("  ·  알람 ", grey)); add(Seg(it, white)) }
                }
                // one info row: shrink (never wrap) when it is wider than the screen (portrait)
                if (info != null) ui.textSize *= min(1f, 0.9f * w / ui.width(info))
                val infoH = if (info == null) 0f else ui.textSize * capR + 0.06f * h
                val gap = 0.04f * h
                val maxCap = if (two) min(0.36f * h, (0.86f * h - infoH - gap) / 2) else min(0.62f * h, 0.86f * h - infoH)
                val fs = fitSize(p, d.use24h, two, maxCap * sizePct, 0.86f * w)
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
        val rows = listOf(
            if (d.alarm != null) Triple("다음 알람 · ${d.alarmDay}", "${d.alarmAmPm}${d.alarm}", white) else Triple("알람", "없음", grey),
            if (d.weatherLine != null) Triple(d.city ?: "날씨", d.weatherLine, white) else Triple("날씨", null, grey),
            if (d.mediaLine != null) Triple(d.mediaApp ?: "음악", d.mediaLine, white) else Triple("음악", "재생 중 아님", grey),
        )
        val margin = if (inSlot) Sizes.of(w.toDp(), h.toDp()).pad.toPx() else 0.06f * w
        val labCap = lab.capRatio(); val ttlCap = ttl.capRatio()

        // digits box and rows box
        val fs: Float; val dx: Float; val capTop: Float
        val rx: Float; val rTop: Float; val rBottom: Float; val rW: Float
        if (portrait) {
            fs = fitSize(p, d.use24h, false, 0.24f * h * sizePct, w - 2 * margin)
            val cap = p.capEm * fs
            capTop = 0.14f * h
            dx = w / 2
            rx = margin; rW = w - 2 * margin
            rTop = capTop + cap + 0.1f * h; rBottom = h - 0.12f * h
        } else {
            val colX = 0.6f * w
            fs = fitSize(p, d.use24h, false, 0.46f * h * sizePct, colX - margin - 0.04f * w)
            val cap = p.capEm * fs
            capTop = (h - cap) / 2
            dx = margin + (colX - 0.04f * w - margin) / 2
            rx = colX; rW = w - margin - colX
            rTop = capTop; rBottom = capTop + cap
        }
        val rowH = (rBottom - rTop) / 3
        lab.textSize = min(rowH * 0.2f / labCap, 0.06f * min(w, h) / labCap)
        ttl.textSize = min(rowH * 0.3f / ttlCap, 0.09f * min(w, h) / ttlCap)
        // each row = label cap-top … title baseline; rows spread space-between from the digit cap-top to baseline
        val gapLT = lab.textSize * 0.55f
        val blockH = lab.textSize * labCap + gapLT + ttl.textSize * ttlCap
        val pitch = (rBottom - rTop - blockH) / 2
        val sep = Paint().apply { color = ink.ink3.toArgb(); strokeWidth = max(1f, 1.dp.toPx()) }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.ink4.toArgb() }
        val texts = rows.map { (l, t, _) -> lab.fit(l, rW) to t?.let { ttl.fit(it, rW) } }
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            drawTime(p, d, fs, false, 0f, dx, capTop, look)
            rows.forEachIndexed { i, r ->
                val top = rTop + i * pitch
                val tBase = top + blockH
                lab.color = grey
                drawText(texts[i].first, rx, top + lab.textSize * labCap, lab)
                val t = texts[i].second
                if (t == null) {
                    val bh = ttl.textSize * ttlCap
                    drawRoundRect(rx, tBase - bh, rx + rW * 0.45f, tBase, bh / 2, bh / 2, bar)
                } else {
                    ttl.color = r.third
                    drawText(t, rx, tBase, ttl)
                }
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
    val look = if (low) Look(base.effect, base.c1, base.c2, shadow = 0x0F000000) else base
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val two = (if (inSlot) h > w * 1.15f else w < h * 1.45f) || style.layout == ClockLayout.TWO_LINES
        val sizePct = style.sizePct / 100f
        val r = if (inSlot) max(0.05f * min(w, h), 12.dp.toPx()) else 0f
        val gap = 0.03f * h
        val fs = if (inSlot) fitSize(p, d.use24h, two, (if (two) 0.32f else 0.5f) * h * sizePct, 0.8f * w)
        else fitSize(p, d.use24h, two, (if (two) 0.30f else 0.40f) * h * sizePct, (if (two) 0.76f else 0.68f) * w)
        val cap = p.capEm * fs
        val top = (h - (if (two) 2 * cap + gap else cap)) / 2
        return@FaceCanvas {
            drawFaceBg(bg ?: (Color.Black.toArgb() to Color.Black.toArgb()), w, h, r)
            drawTime(p, d, fs, two, gap, w / 2, top, look)
        }
    }
}

/* ------------------------------------------------------------------ StyledTime */

@Composable
internal fun StyledTimeImpl(
    text: String, style: ClockStyle, ink: Ink, modifier: Modifier, color: Color, effects: Boolean, widest: String?,
    align: Alignment.Horizontal,
) {
    val p = rememberTimePainter(style)
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

