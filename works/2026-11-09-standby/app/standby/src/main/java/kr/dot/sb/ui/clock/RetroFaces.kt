package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.max
import kr.dot.sb.ui.theme.ThemeTokens
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.ui.theme.FlipDigits
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
import kotlin.math.min

internal val SevenSegments = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
    '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
)

internal val DotGlyphs = mapOf(
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
)

/**
 * 플립 (c-flip): [FlipDigits] tiles in a row 270 dp tall at a 360 dp height (a tall box: hours over minutes), 오전/오후
 * small in the hour tile's corner,
 * "월요일 · 11월 9일 · 7° · ⏰ 6:30" centred at the bottom. Night: black tiles outlined in night ink.
 */
@Composable
internal fun FlipFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val night = ink.night >= 0.5f
    // the tile is the digits' background, so the colours are not checked against the page
    val plate = if (night) ink.bg else Color(style.plateColor ?: FaceDefaults.PLATE)
    val digit = if (night) ink.ink else Color(style.digitColor ?: FaceDefaults.DIGIT)
    val text = "${d.h}:${d.m}"
    val th = LocalTheme.current
    val tokens = th.tokens
    // digits are checked against the tile, not the page (a light theme would turn them dark on a dark tile)
    val tileInk = androidx.compose.runtime.remember(ink.night, digit, plate, th) { Ink.of(ink.night, digit, th, listOf(plate)) }
    val p = rememberTimePainter(style)
    val ui = rememberUi(500); val lab = rememberUi(600)
    val look = Look.of(style, ink)
    BoxWithConstraints(modifier) {
        val s = min(maxWidth.value, maxHeight.value)
        if (s < COMPACT_MIN.value) { FaceCanvas(Modifier) { compact(p, d, look) }; return@BoxWithConstraints }
        val k = s / 360f
        val margin = if (inSlot) 0.04f * s else 10f + 0.04f * s
        val capSize = max(13f, 17f * k)
        val band = if (style.showInfo) capSize * 2.6f else 0f
        val boxW = maxWidth.value - 2 * margin
        val lines = if (maxHeight > maxWidth) listOf(d.h, d.m) else listOf(text)
        val boxH = min(270f * k * style.sizePct / 100f * (lines.size * 1.06f - 0.06f), maxHeight.value - 2 * margin - band)
        Box(Modifier.fillMaxSize().padding(start = margin.dp, end = margin.dp, top = margin.dp, bottom = (margin + band).dp), contentAlignment = Alignment.Center) {
            FlipDigits(lines, style, tileInk, tokens, digit, Modifier.size(boxW.dp, boxH.dp), plate = plate, animate = true)
        }
        FaceCanvas(Modifier) {
            val w = size.width; val h = size.height; val u = density
            // FlipDigits' rows: digits 0.62 em, colon 0.3 em, 0.06 em apart, 0.95 em tall, centred in its box
            fun emW(l: String) = l.sumOf { if (it == ':') 0.35 else 0.62 }.toFloat() + (l.length - 1) * 0.06f
            val fs = min(boxW / lines.maxOf { emW(it) }, boxH / (lines.size * 0.95f + (lines.size - 1) * 0.06f)) * u
            val blockH = (lines.size * 0.95f + (lines.size - 1) * 0.06f) * fs
            val top = margin * u + ((h - 2 * margin * u - band * u) - blockH) / 2
            val tiles = ArrayList<Pair<Float, Float>>()
            lines.forEachIndexed { row, l ->
                val rowW = (l.sumOf { if (it == ':') 0.3 else 0.62 }.toFloat() + (l.length - 1) * 0.06f) * fs
                var x = (w - rowW) / 2
                val y = top + row * 1.01f * fs
                for (ch in l) { if (ch != ':') tiles += x to y; x += (if (ch == ':') 0.3f else 0.62f) * fs + 0.06f * fs }
            }
            val dim = ink.ink2.toArgb()
            lab.textSize = 0.053f * fs; lab.color = TimePainter.withAlpha(digit.toArgb(), 0.55f)
            ui.textSize = capSize * u
            val segs = buildList {
                add(Seg(d.infoLine(), dim))
                d.alarmShort?.let { add(Seg(" · ", dim)); add(alarmSeg(dim)); add(Seg(it, dim)) }
            }
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE; strokeWidth = 1.5f * u; color = ThemeTokens.NIGHT_INK.copy(alpha = 0.35f).toArgb()
            }
            return@FaceCanvas {
                if (night) for ((tx, ty) in tiles) {
                    drawRoundRect(tx, ty, tx + 0.62f * fs, ty + 0.95f * fs, 0.1f * fs, 0.1f * fs, rule)
                    drawLine(tx, ty + 0.475f * fs, tx + 0.62f * fs, ty + 0.475f * fs, rule)
                }
                if (!d.use24h) drawText(d.ampm, tiles[0].first + 0.044f * fs, tiles[0].second + 0.042f * fs + lab.capRatio() * lab.textSize, lab)
                if (style.showInfo) drawSegs(segs, (w - ui.width(segs)) / 2, h - margin * u - (band * u - ui.capRatio() * ui.textSize) / 2, ui)
            }
        }
    }
}

private fun polygon(vararg xy: Float): Path = Path().apply {
    moveTo(xy[0], xy[1])
    for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
    close()
}

// clocks.html seg7 viewBox 100 x 180, with T = 20 and gap = 3.
private val segmentPaths: Map<Char, Path> by lazy { buildMap {
    fun h(y: Float) = polygon(13f, y, 23f, y - 10f, 77f, y - 10f, 87f, y, 77f, y + 10f, 23f, y + 10f)
    fun v(x: Float, y1: Float, y2: Float) = polygon(
        x, y1 + 3f, x + 10f, y1 + 13f, x + 10f, y2 - 13f,
        x, y2 - 3f, x - 10f, y2 - 13f, x - 10f, y1 + 13f,
    )
    put('a', h(10f)); put('g', h(90f)); put('d', h(170f))
    put('f', v(10f, 10f, 90f)); put('b', v(90f, 10f, 90f))
    put('e', v(10f, 90f, 170f)); put('c', v(90f, 90f, 170f))
} }

private fun Canvas.sevenDigit(ch: Char, x: Float, y: Float, width: Float, height: Float, on: Int, off: Int, glow: Int = 0) {
    save()
    translate(x, y)
    scale(width / 100f, height / 180f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val lit = SevenSegments[ch].orEmpty()
    for ((name, path) in segmentPaths) {
        val isOn = name in lit
        paint.color = if (isOn) on else off
        // c-led drop-shadow 0 0 14px on a 270 dp digit = 9.3 units of the 180 viewBox
        if (isOn && glow != 0) paint.setShadowLayer(9.3f, 0f, 0f, glow) else paint.clearShadowLayer()
        drawPath(path, paint)
    }
    restore()
}

/** Seven-segment digits with a square colon, skewed by [skew] (tan) about the row's middle; [digitH] tall. */
private fun Canvas.segRow(
    chars: String, hourLen: Int, x0: Float, top: Float, digitH: Float, gap: Float, colon: Float, colonGap: Float,
    skew: Float, on: Int, off: Int, glow: Int,
) {
    val digitW = digitH * 100f / 180f
    save()
    translate(0f, top + digitH / 2); skew(-skew, 0f); translate(0f, -digitH / 2)
    val sq = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = on; if (glow != 0) setShadowLayer(colon * 0.5f, 0f, 0f, glow) }
    var x = x0
    chars.forEachIndexed { i, ch ->
        if (i == hourLen) {
            for (dy in listOf(-1f, 1f)) {
                val cy = digitH / 2 + dy * (colonGap + colon) / 2
                drawRect(x, cy - colon / 2, x + colon, cy + colon / 2, sq)
            }
            x += colon + gap
        }
        sevenDigit(ch, x, 0f, digitW, digitH, on, off, glow)
        x += digitW + gap
    }
    restore()
}

/** Width of [segRow] for [n] digits. */
private fun segRowW(n: Int, digitH: Float, gap: Float, colon: Float) = n * digitH * 100f / 180f + colon + n * gap

/**
 * 빨간 LED (c-led) and 액정 (c-lcd): one seven-segment renderer (clocks.html `seg7`, off segments at 12 % / 9 %).
 * LED: digits 270 dp tall at 360, skewed −5°, a glow by day, 오전/오후/알람 lamps on the left (above the digits in a
 * tall box). LCD: a [ClockStyle.panelColor] plate inset 22 × 26 dp with [ClockStyle.inkColor] digits 212 dp tall
 * skewed −4°, weekday and MM-DD on top, 오전 / 알람 / temperature on the right. Night: night ink, off segments hidden,
 * the plate black and outlined. Compact: the digits only.
 */
@Composable
internal fun SevenSegmentFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, lcd: Boolean, modifier: Modifier) {
    val mono = rememberTypeface(kr.dot.sb.ui.theme.Fonts.plexMono, 500).let { androidx.compose.runtime.remember(it) { uiPaint(it, 500) } }
    val n = ink.night
    val day = if (lcd) Color(style.inkColor ?: FaceDefaults.INK) else Color(style.ledColor ?: FaceDefaults.LEDS[0])
    val on = lerp(day, ThemeTokens.NIGHT_INK, n).toArgb()
    val off = day.copy(alpha = (if (lcd) 0.09f else 0.12f) * (1f - n)).toArgb()
    val glow = if (lcd || n >= 0.5f) 0 else day.copy(alpha = 0.55f).toArgb()
    val plate = lerp(Color(style.panelColor ?: FaceDefaults.PANEL), ink.bg, n).toArgb()
    val dim = lerp(day.copy(alpha = 0.6f), Ink.NIGHT_DIM, n).toArgb()
    val chars = d.h + d.m
    val nd = chars.length
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height; val s = min(w, h)
        val compact = s < COMPACT_MIN.toPx()
        val margin = if (inSlot) 0.04f * s else 10.dp.toPx() + 0.04f * s
        val tall = h > w
        val k0 = s / 360f
        if (compact || !lcd) {
            // LED row: 150 × 270 digits, 26 dp gaps, 26 dp colon squares 70 apart; the lamps take ~100 dp on the left
            val lamps = !compact && !tall
            val k = min(k0 * style.sizePct / 100f, (w - 2 * margin) / (segRowW(nd, 270f, 26f, 26f) + if (lamps) 100f else 0f))
            val dh = 270 * k
            val rowW = segRowW(nd, dh, 26 * k, 26 * k)
            val top = (h - dh) / 2 + if (tall && !compact) 30 * k0 else 0f
            // the lamps scale with the row (k), so a narrow box shrinks both together
            val lk = if (lamps) k else k0
            val indX = max(margin, 34 * k)
            val x0 = if (lamps) max((w - rowW) / 2 + 30 * k, indX + 60 * k) else (w - rowW) / 2
            mono.textSize = max(11.dp.toPx(), 15 * lk)
            val items = listOfNotNull(
                if (d.use24h) null else "오전" to (d.now.hour < 12),
                if (d.use24h) null else "오후" to (d.now.hour >= 12),
                "알람" to (d.alarm != null),
            )
            val lampOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = on; if (glow != 0) setShadowLayer(5 * lk, 0f, 0f, glow) }
            val lampOff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (n >= 0.5f) { this.style = Paint.Style.STROKE; strokeWidth = 1.dp.toPx(); color = Ink.NIGHT_DIM.toArgb() } else color = off
            }
            val r = 6 * lk
            // a compact LCD keeps its plate: its dark ink would vanish on a dark page
            val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate }
            return@FaceCanvas {
                if (lcd) drawRoundRect(0f, 0f, w, h, 0.08f * s, 0.08f * s, back)
                segRow(chars, d.h.length, x0, top, dh, 26 * k, 26 * k, 70 * k, 0.0875f, on, off, glow)
                if (compact) return@FaceCanvas
                mono.color = dim
                items.forEachIndexed { i, (label, lit) ->
                    if (lamps) {
                        val y = top + 15 * k + i * 61 * k
                        drawCircle(indX + r, y + r, r, if (lit) lampOn else lampOff)
                        drawText(label, indX, y + 20 * k + mono.capRatio() * mono.textSize, mono)
                    } else {
                        val cx = w / 2 + (i - (items.size - 1) / 2f) * 80 * k0
                        val y = top - 40 * k0
                        val lw = mono.measureText(label)
                        drawCircle(cx - lw / 2 - r * 1.5f, y, r, if (lit) lampOn else lampOff)
                        drawText(label, cx - lw / 2 + r, y + mono.capRatio() * mono.textSize / 2, mono)
                    }
                }
            }
        }
        // LCD: the plate, then everything in plate units (316 × 728 dp in the mockup)
        val pl = max(margin, 26 * k0); val pt = max(margin, 22 * k0)
        val pw = w - 2 * pl; val ph = h - 2 * pt
        val pk = min(ph / 316f, pw / (if (tall) 316f else 728f))
        mono.textSize = 22 * pk; mono.letterSpacing = 0.06f
        val topY = pt + 20 * pk + mono.capRatio() * mono.textSize
        val date = "%02d-%02d".format(d.month, d.day)
        val side = listOfNotNull(if (d.use24h) null else d.ampm, d.alarmShort?.let { "알람 $it" }, d.temp)
        val sideSize = 20 * pk
        val sidePaint = android.text.TextPaint(mono).apply { textSize = sideSize; letterSpacing = 0f }
        val sideW = side.maxOfOrNull { sidePaint.measureText(it) } ?: 0f
        val avail = if (tall) pw - 92 * pk else pw - 46 * pk - 30 * pk - 38 * pk - sideW
        val dk = min(pk * style.sizePct / 100f, avail / segRowW(nd, 212f, 20f, 20f))
        val dh = 212 * dk
        val rowTop = pt + (ph - dh) / 2 - (if (tall) 40 * pk else 8 * pk)
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = 2 * pk
            color = if (n >= 0.5f) ThemeTokens.NIGHT_INK.copy(alpha = 0.3f).toArgb() else 0x40000000
        }
        val r = 14 * pk
        return@FaceCanvas {
            drawRoundRect(pl, pt, pl + pw, pt + ph, r, r, panel)
            drawRoundRect(pl + pk, pt + pk, pl + pw - pk, pt + ph - pk, r, r, edge)
            mono.color = on
            drawText("${d.weekday}요일", pl + 30 * pk, topY, mono)
            drawText(date, pl + pw - 30 * pk - mono.measureText(date), topY, mono)
            segRow(chars, d.h.length, pl + 46 * pk, rowTop, dh, 20 * dk, 20 * dk, 56 * dk, 0.07f, on, off, 0)
            sidePaint.color = on
            side.forEachIndexed { i, t ->
                if (tall) drawText(t, pl + 46 * pk, rowTop + dh + 40 * pk + i * sideSize * 1.5f, sidePaint)
                else drawText(t, pl + pw - 38 * pk - sidePaint.measureText(t), rowTop + 8 * pk + sidePaint.capRatio() * sideSize + i * sideSize * 1.5f, sidePaint)
            }
        }
    }
}

@Composable
internal fun DotMatrixFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val on = ink.accent(Color(style.dotColor ?: FaceDefaults.DOT)).toArgb()
    val off = ink.accent(Color(style.dotColor ?: FaceDefaults.DOT)).copy(alpha = 0.10f).toArgb()
    val text = "${d.h}:${d.m}"
    val glyphs = text.map { DotGlyphs.getValue(it) }
    val columns = glyphs.sumOf { it[0].length } + glyphs.size - 1 + 4
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val margin = if (inSlot) 4.dp.toPx() else 10.dp.toPx()
        val pitch = min((w - 2 * margin) / columns, (h - 2 * margin) / 9f) * style.sizePct / 100f
        val x0 = (w - columns * pitch) / 2
        val y0 = (h - 9 * pitch) / 2
        val lit = HashSet<Pair<Int, Int>>()
        var column = 2
        glyphs.forEach { glyph ->
            glyph.forEachIndexed { row, bits -> bits.forEachIndexed { col, bit -> if (bit == '1') lit.add(column + col to row + 1) } }
            column += glyph[0].length + 1
        }
        return@FaceCanvas {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            for (row in 0..8) for (col in 0 until columns) {
                p.color = if ((col to row) in lit) on else off
                drawCircle(x0 + (col + 0.5f) * pitch, y0 + (row + 0.5f) * pitch, pitch * 0.36f, p)
            }
        }
    }
}
