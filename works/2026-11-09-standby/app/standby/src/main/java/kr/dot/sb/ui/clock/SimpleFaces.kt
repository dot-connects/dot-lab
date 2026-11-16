package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kotlin.math.max
import kotlin.math.min

/* r10 심플 faces (clocks.html c-thin, c-split; plan-r10 Task 6). 1 mockup px = 1 dp on a 780 × 360 box. */

/** The compact fallback of every r10 face (rendering contract): digits only, one line, centred in the box. */
internal fun CacheDrawScope.compact(p: TimePainter, d: FaceData, look: Look): Canvas.() -> Unit {
    val w = size.width; val h = size.height
    val fs = fitSize(p, d, false, 0.8f * h, 0.9f * w)
    return { drawTime(p, d, fs, false, 0f, w / 2, (h - p.capEm * fs) / 2, look) }
}

/** Below 120 dp on the short side every r10 face draws [compact]. */
internal val COMPACT_MIN = 120.dp

/** "월요일 · 11월 9일 · 7°" (the one-line info of 가는 선 / 공 숫자). */
internal fun FaceData.infoLine(date: Boolean = true): String =
    listOfNotNull("${weekday}요일", if (date) "${month}월 ${day}일" else null, temp).joinToString(" · ")

/* ------------------------------------------------------------------ 가는 선 */

/**
 * 가는 선 = the digital skeleton with Outfit 100, centred, and a tracked date line under a hairline (c-thin). A slot
 * draws it as Digital does. Night: one weight step up (a 100 line is too faint in candle ink).
 */
@Composable
internal fun ThinFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val night = ink.night >= 0.5f
    val st = if (night) style.copy(weight = style.weight + 100) else style
    if (inSlot) { DigitalFace(st, d, ink, true, modifier); return }
    val p = rememberTimePainter(st)
    val ui = rememberUi(400)
    val look = Look.of(st, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        if (min(w, h) < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, look)
        val k = min(w, h) / 360f
        val two = w < h * 1.45f
        val bg = bgColors(st, ink)
        val dim = ink.ink2.toArgb()
        val sub = if (st.showInfo) d.infoLine() else null
        ui.letterSpacing = 0.34f
        ui.textSize = 17 * k
        if (sub != null) ui.textSize *= min(1f, 0.9f * w / ui.measureText(sub))
        val subW = sub?.let { ui.measureText(it) } ?: 0f
        // hairline 6 dp under the digits, text 16 dp under the hairline (c-thin .sub)
        val subH = if (sub == null) 0f else 22 * k + ui.textSize * ui.capRatio()
        val gap = 0.04f * h
        val maxCap = if (two) min(0.34f * h, (0.86f * h - subH - gap) / 2) else min(0.53f * h, 0.86f * h - subH)
        val fs = fitSize(p, d, two, maxCap * st.sizePct / 100f, 0.86f * w)
        val cap = p.capEm * fs
        val digitsH = if (two) 2 * cap + gap else cap
        val top = (h - digitsH - subH) / 2
        val lineY = top + digitsH + 6 * k
        val lineW = min(0.9f * w, max(0.718f * w, subW))
        val rule = Paint().apply { color = dim; strokeWidth = max(1f, 1.dp.toPx()) }
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            drawTime(p, d, fs, two, gap, w / 2, top, look)
            if (sub != null) {
                drawLine((w - lineW) / 2, lineY, (w + lineW) / 2, lineY, rule)
                ui.color = dim
                // letter spacing trails the last glyph: centre on the ink, not the advance
                drawText(sub, (w - subW + ui.letterSpacing * ui.textSize) / 2, top + digitsH + subH, ui)
            }
        }
    }
}

/* ------------------------------------------------------------------ 시·분 나눔 */

/**
 * 시·분 나눔 (c-split): hour left in [ClockStyle.hourColor], minute right in [ClockStyle.minuteColor], no colon; a 2 dp
 * rule between them carries 오전/오후 at its top and "월 9 · 7°" at its bottom, turned 90°. Tall boxes stack hour over
 * minute with a horizontal rule. Needs 160 × 120 dp (either way round), else [CompactTime].
 */
@Composable
internal fun SplitFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val hourLook = Look(kr.dot.sb.model.Effect.SOLID, ink.accent(Color(style.hourColor ?: FaceDefaults.HOUR)).toArgb(), 0)
    val minLook = Look(kr.dot.sb.model.Effect.SOLID, ink.accent(Color(style.minuteColor ?: FaceDefaults.MINUTE)).toArgb(), 0)
    val p = rememberTimePainter(style)
    val ui = rememberUi(600)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        if (min(w, h) < COMPACT_MIN.toPx() || max(w, h) < 160.dp.toPx()) return@FaceCanvas compact(p, d, hourLook)
        val tall = h > w
        val bg = if (inSlot) null else bgColors(style, ink)
        val sizePct = style.sizePct / 100f
        val k = min(w, h) / 360f
        val dim = ink.ink2.toArgb()
        val margin = if (inSlot) 0.05f * min(w, h) else Grid.margin(min(w, h).toDp()).toPx() + 10.dp.toPx()
        val gap = 34 * k
        val hourEm = p.hourEm(d.h.length, d.use24h) * 1.03f
        val minEm = p.minuteEm * 1.03f
        val top = if (d.use24h) null else d.ampm
        val bottom = if (style.showInfo) listOfNotNull("${d.weekday} ${d.day}", d.temp).joinToString(" · ") else null
        ui.textSize = max(13.dp.toPx(), 15 * k)
        ui.letterSpacing = 0.2f
        ui.color = dim
        val labCap = ui.capRatio() * ui.textSize
        val pad = 8 * k
        val rule = Paint().apply { color = dim; strokeWidth = max(2f, 2.dp.toPx()) }
        if (!tall) {
            val fs = min(0.78f * h * sizePct / p.capEm, (w - 2 * margin - 2 * gap) / (hourEm + minEm))
            val cap = p.capEm * fs
            // the rule sits where the reserved hour width ends, so it never moves within the hour class
            val rx = (w - (hourEm + minEm) * fs - 2 * gap) / 2 + hourEm * fs + gap
            val capTop = (h - cap) / 2
            val half = 0.347f * h // rule 250 dp of 360
            return@FaceCanvas {
                drawFaceBg(bg, w, h, 0f)
                p.draw(this, d.h, rx - gap - p.measure(d.h, fs), capTop + cap, fs, hourLook)
                p.draw(this, d.m, rx + gap, capTop + cap, fs, minLook)
                var y0 = h / 2 - half; var y1 = h / 2 + half
                // labels read top-to-bottom (turned 90° clockwise), each cut into the rule with [pad] of air
                fun label(s: String, atTop: Boolean): Float {
                    val len = ui.measureText(s)
                    val start = if (atTop) y0 + pad else y1 - pad - len
                    save(); translate(rx - labCap / 2, start); rotate(90f); drawText(s, 0f, 0f, ui); restore()
                    return len + 2 * pad
                }
                if (top != null) y0 += label(top, true)
                if (bottom != null) y1 -= label(bottom, false)
                if (y1 > y0) drawLine(rx, y0, rx, y1, rule)
            }
        }
        // tall: hour over minute, horizontal rule with the labels at its two ends
        val fs = min((h - 2 * margin - 2 * gap) / 2 / p.capEm * sizePct, (w - 2 * margin) / max(hourEm, minEm))
        val cap = p.capEm * fs
        val ry = h / 2
        val half = 0.347f * w
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            p.draw(this, d.h, (w - p.measure(d.h, fs)) / 2, ry - gap, fs, hourLook)
            p.draw(this, d.m, (w - p.measure(d.m, fs)) / 2, ry + gap + cap, fs, minLook)
            var x0 = w / 2 - half; var x1 = w / 2 + half
            val base = ry + labCap / 2
            if (top != null) { drawText(top, x0, base, ui); x0 += ui.measureText(top) + pad }
            if (bottom != null) { val l = ui.measureText(bottom); drawText(bottom, x1 - l, base, ui); x1 -= l + pad }
            if (x1 > x0) drawLine(x0, ry, x1, ry, rule)
        }
    }
}
