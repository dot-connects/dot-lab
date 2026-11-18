package kr.dot.sb.ui.clock

import android.graphics.BlendMode
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.ClockLayout
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.theme.BURN_IN_BLEED
import kr.dot.sb.ui.theme.DigitStyle
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
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

/**
 * Draw "h:mm" (one line, horizontally centred on [cx]) or hours over minutes; [capTop] = top of the first line.
 * [mid] = the look of digit index 1 (r14 가운데 숫자 색), one line only.
 */
internal fun Canvas.drawTime(p: TimePainter, d: FaceData, size: Float, twoLines: Boolean, gap: Float, cx: Float, capTop: Float, look: Look, mid: Look? = null) {
    val cap = p.capEm * size
    if (twoLines) {
        p.draw(this, d.h, cx - p.measure(d.h, size) / 2, capTop + cap, size, look)
        p.draw(this, d.m, cx - p.measure(d.m, size) / 2, capTop + 2 * cap + gap, size, look)
    } else {
        p.drawHm(this, d.h, d.m, cx - p.measureHm(d.h, d.m, size) / 2, capTop + cap, size, look, mid = mid)
    }
}

/**
 * r14 살짝 겹치기 (the old 둥근 굵은 글씨 placement, design-system §4.3 Float): in em, digits overlap by 0.12 inside a
 * group and just touch across the colon. Widths depend only on the hour-digit class (§2.3.1).
 */
private class Overlap(p: TimePainter) {
    val a = p.digitAdv.max()
    val inStep = a - 0.12f
    val crossStep = a + 0.02f
    fun lineEm(d: FaceData, two: Boolean) =
        if (two) (d.h.length.coerceAtLeast(2) - 1) * inStep + a else (d.h.length + d.m.length - 2) * inStep + crossStep + a
}

/** Overlapping digits, alternately tilted ±3°, each blended with [blend] inside one layer; colon = two discs on top. */
private fun Canvas.drawOverlap(
    p: TimePainter, o: Overlap, d: FaceData, fs: Float, two: Boolean, gap: Float, cx: Float, top: Float,
    look: Look, mid: Look?, blend: Paint, colon: Paint, w: Float, h: Float,
) {
    val digits = d.h + d.m
    val hl = d.h.length
    val cap = p.capEm * fs
    val xs = FloatArray(digits.length); val line = IntArray(digits.length)
    if (two) {
        for ((li, s) in listOf(d.h, d.m).withIndex()) {
            val lw = ((s.length - 1) * o.inStep + o.a) * fs
            for (k in s.indices) { val i = if (li == 0) k else hl + k; xs[i] = cx - lw / 2 + k * o.inStep * fs; line[i] = li }
        }
    } else {
        var x = cx - ((digits.length - 2) * o.inStep + o.crossStep + o.a) * fs / 2
        for (i in digits.indices) { xs[i] = x; x += (if (i == hl - 1) o.crossStep else o.inStep) * fs }
    }
    saveLayer(0f, 0f, w, h, null)
    for (i in digits.indices) {
        val capTop = top + line[i] * (cap + gap)
        val base = capTop + cap
        val gx = xs[i] + o.a * fs / 2
        save()
        rotate(if (i % 2 == 0) 3f else -3f, gx, capTop + cap / 2)
        saveLayer(xs[i] - fs, capTop - fs, xs[i] + 2 * fs, base + fs, blend)
        val s = digits[i].toString()
        p.draw(this, s, gx - p.measure(s, fs) / 2, base, fs, if (i == 1 && !two && mid != null) mid else look)
        restore(); restore()
    }
    if (!two) {
        val colonX = (xs[hl - 1] + o.a * fs + xs[hl]) / 2
        val r = min(0.025f * w, 0.07f * fs)
        drawCircle(colonX, top + cap * 0.27f, r, colon)
        drawCircle(colonX, top + cap * 0.71f, r, colon)
    }
    restore()
}

@Composable
internal fun rememberUi(weight: Int): TextPaint {
    val tf = rememberTypeface(LocalTheme.current.words(weight), weight)
    return androidx.compose.runtime.remember(tf) { uiPaint(tf, weight) }
}

/* ------------------------------------------------------------------ Digital */

/**
 * 디지털, full screen and in a board card (plan-r14 A1): digits with one info row under them, centred as one block:
 * [오전/오후 ·] 월 9 · 7° · alarm 6:30 (weather and alarm only when known; alarm dropped first, then weather, when the
 * row is wider than the block). Options 가운데 숫자 색 / 꽉 채우기 / 살짝 겹치기 = [ClockStyle.accentMiddle] / fill / overlap.
 */
@Composable
internal fun DigitalFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val tk = LocalTheme.current.tokens
    // a card keeps the theme's digit treatment (ThemedDigits): theme font with a plain effect glows by day
    val glow = inSlot && tk.digitStyle == DigitStyle.GLOW && style.font == FontId.THEME && style.effect == Effect.SOLID && ink.night < 0.5f
    val st = if (glow) style.copy(effect = Effect.GLOW) else style
    val p = rememberTimePainter(st)
    val ui = rememberUi(500)
    val look = Look.of(st, ink, glow = if (inSlot) tk.digitGlow else 1f)
    // a two-colour run keeps its gradient
    val mid = if (style.accentMiddle && look.effect != Effect.TWO_COLOR) Look(look.effect, ink.accDeep.toArgb(), look.c2, look.shadow, look.glow) else null
    val mix = if (ink.tokens.light && ink.night < 0.5f) BlendMode.MULTIPLY else BlendMode.SCREEN
    val colon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TimePainter.withAlpha(ink.ink2.toArgb(), 0.85f); blendMode = mix }
    val blend = Paint().apply { blendMode = if (style.font == FontId.RAILS) BlendMode.SRC_OVER else mix }
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val bg = if (inSlot) null else bgColors(style, ink)
        val sizePct = style.sizePct / 100f
        val accent = ink.acc.toArgb(); val white = ink.ink.toArgb(); val grey = ink.ink2.toArgb()
        val capR = ui.capRatio()
        // full screen: portrait and near-square screens (w/h < 1.45: Fold inner, Flip) stack the digits
        val two = style.layout == ClockLayout.TWO_LINES || (if (inSlot) h > w * 1.15f else w < h * 1.45f)
        // the card already took its pad off: only a really small box drops the info row
        val showInfo = style.showInfo && !(inSlot && Sizes.of(w.toDp(), h.toDp()).s < 140.dp)
        val gap = 0.04f * h
        val maxW = w * if (style.fill) (if (inSlot) 1f else 0.94f) else (if (inSlot) 0.9f else 0.86f)
        val margin = if (inSlot) 0f else 2 * Grid.margin(min(w, h).toDp()).toPx()
        fun maxCap(infoH: Float) = sizePct * when {
            style.fill && two -> min(0.46f * h, (h - infoH - margin - gap) / 2)
            style.fill -> min(0.92f * h, h - infoH - margin)
            two -> if (inSlot) (0.86f * h - infoH - gap) / 2 else min(0.36f * h, (0.86f * h - infoH - gap) / 2)
            else -> if (inSlot) 0.86f * h - infoH else min(0.62f * h, 0.86f * h - infoH)
        }
        val ov = if (style.overlap) Overlap(p) else null
        fun fit(cap: Float) = if (ov != null) min(cap / p.capEm, maxW / ov.lineEm(d, two)) else fitSize(p, d, two, cap, maxW)
        fun infoSize(fs: Float) = max(16.dp.toPx(), p.capEm * fs * 0.16f)
        // row height: its cap + 0.7 × the text size of air above it
        fun rowH() = ui.textSize * (capR + 0.7f)
        val sep = Seg(" · ", grey)
        fun row(weather: Boolean, alarm: Boolean): List<Seg> = buildList {
            if (!d.use24h) { add(Seg(d.ampm, white)); add(sep) }
            add(Seg(d.weekday + " ", accent)); add(Seg(d.day.toString(), white))
            if (weather) d.temp?.let { add(sep); add(Seg(it, white)) }
            if (alarm) d.alarmShort?.let { add(sep); add(alarmSeg(white)); add(Seg(it, white)) }
        }
        var fs = fit(maxCap(0f))
        var info: List<Seg>? = null
        if (showInfo) {
            ui.textSize = infoSize(fs)
            fs = fit(maxCap(rowH()))
            ui.textSize = infoSize(fs) // smaller digits: a row no taller than the space kept for it
            val r = listOf(row(true, true), row(true, false)).firstOrNull { ui.width(it) <= maxW } ?: row(false, false)
            if (ui.width(r) > maxW) ui.textSize *= maxW / ui.width(r)
            info = r
        }
        val infoH = if (info == null) 0f else rowH()
        val cap = p.capEm * fs
        val blockH = (if (two) 2 * cap + gap else cap) + infoH
        val top = (h - blockH) / 2
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            if (ov != null) drawOverlap(p, ov, d, fs, two, gap, w / 2, top, look, mid, blend, colon, w, h)
            else drawTime(p, d, fs, two, gap, w / 2, top, look, if (two) null else mid)
            info?.let { drawSegs(it, (w - ui.width(it)) / 2, top + blockH, ui) }
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
            if (d.mediaLine != null) Triple(d.mediaApp ?: "음악", d.mediaLine, white) else Triple("음악", "듣는 곡 없음", grey),
        )
        val margin = if (inSlot) Sizes.of(w.toDp(), h.toDp()).pad.toPx() else 0.06f * w
        val labCap = lab.capRatio(); val ttlCap = ttl.capRatio()

        // digits box and rows box
        val fs: Float; val dx: Float; val capTop: Float
        val rx: Float; var rTop: Float; var rBottom: Float; val rW: Float
        if (portrait) {
            fs = fitSize(p, d, false, 0.24f * h * sizePct, w - 2 * margin)
            val cap = p.capEm * fs
            capTop = 0.14f * h
            dx = w / 2
            rx = margin; rW = w - 2 * margin
            rTop = capTop + cap + 0.1f * h; rBottom = h - 0.12f * h
        } else {
            // digits in the left 55 %, rows from 60 % (design-system §4.3 Desk); a bar phone (w/h ≥ 1.8) is so short
            // that 46 % H left the digits small: there they take ≥ 60 % H and the left 62 % (r10 bar-land)
            val bar = w >= 1.8f * h
            val digitR = (if (bar) 0.62f else 0.55f) * w
            fs = fitSize(p, d, false, (if (bar) 0.62f else 0.46f) * h * sizePct, digitR - margin)
            val cap = p.capEm * fs
            capTop = (h - cap) / 2
            dx = (margin + digitR) / 2
            rx = digitR + 0.05f * w; rW = w - margin - rx
            rTop = capTop; rBottom = capTop + cap
        }
        // review C2: row pitch = digit cap / 3; title 0.38 × pitch, label 0.24 × pitch, floors 22 / 18 dp
        val pitch0 = p.capEm * fs / 3
        // plan-r15 E9 #9: a near-square screen (stacked, not tall: Fold inner) keeps the rows at the Flip cover's share
        // of the short side, so they grow with the screen instead of sitting on the dp floors
        val sq = portrait && h <= w * 1.15f && !inSlot
        val s = min(w, h)
        lab.textSize = maxOf(18.dp.toPx(), 0.24f * pitch0, if (sq) 0.042f * s else 0f)
        ttl.textSize = maxOf(22.dp.toPx(), 0.38f * pitch0, if (sq) 0.052f * s else 0f)
        // each row = label cap-top … title baseline; rows spread space-between from rTop to rBottom
        val gapLT = lab.textSize * 0.55f
        val blockH = lab.textSize * labCap + gapLT + ttl.textSize * ttlCap
        // a wide font (theme C Nunito) leaves a short digit cap: the rows then keep at least 0.8 label of air between
        // them, growing the band around its centre instead of overlapping the separators
        val need = 3 * blockH + 2 * 1.6f * lab.textSize
        val mid = (rTop + rBottom) / 2
        if (rBottom - rTop < need) { rTop = mid - need / 2; rBottom = mid + need / 2 }
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
internal fun SolarFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier, slotBleed: Dp = 0.dp) {
    val bg = bgColors(style, ink, nightPlate = Ink.NIGHT_PLATE)
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
        val r = if (inSlot && slotBleed == 0.dp) max(0.05f * min(w, h), 12.dp.toPx()) else 0f
        val gap = 0.03f * h
        val fs = if (inSlot) fitSize(p, d, two, (if (two) 0.32f else 0.5f) * h * sizePct, 0.8f * w)
        else fitSize(p, d, two, (if (two) 0.30f else 0.40f) * h * sizePct, (if (two) 0.76f else 0.68f) * w)
        val cap = p.capEm * fs
        val top = (h - (if (two) 2 * cap + gap else cap)) / 2
        // full screen: the plate overscans for the burn-in shift; a clock card: to the card edge
        val bleed = if (inSlot) slotBleed.toPx() else BURN_IN_BLEED.toPx()
        return@FaceCanvas {
            if (bleed > 0f) { save(); translate(-bleed, -bleed); drawFaceBg(bg, w + 2 * bleed, h + 2 * bleed, r); restore() }
            else drawFaceBg(bg, w, h, r)
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
    align: Alignment.Horizontal, glow: Float = 1f,
) {
    val p = rememberTimePainter(style, tnum = true)
    val look = Look.of(style, ink, base = color, effects = effects, glow = glow)
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
