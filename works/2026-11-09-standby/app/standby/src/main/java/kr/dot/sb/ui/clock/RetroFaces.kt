package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
internal fun FlipFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val plate = if (ink.night >= 0.5f) ink.bg else Color(style.plateColor ?: FaceDefaults.PLATE)
    val digit = ink.accent(Color(style.digitColor ?: FaceDefaults.DIGIT))
    val text = "${d.h}:${d.m}"
    val tokens = LocalTheme.current.tokens
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = minOf(maxWidth, maxHeight) < 120.dp
        val info = style.showInfo && !compact
        FlipDigits(
            listOf(text), style, ink, tokens, digit,
            Modifier.fillMaxWidth().height(if (info) maxHeight * 0.78f else maxHeight - if (inSlot) 4.dp else 20.dp),
            plate = plate, animate = true,
        )
        if (info) {
            val caption = buildString {
                append(d.dateLong)
                d.temp?.let { append(" · "); append(it) }
                d.alarmShort?.let { append(" · "); append(it) }
            }
            Text(caption, Modifier.align(Alignment.BottomCenter).padding(bottom = if (inSlot) 4.dp else 10.dp),
                color = ink.ink2, fontSize = if (inSlot) 13.sp else 17.sp, maxLines = 1)
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

private fun Canvas.sevenDigit(ch: Char, x: Float, y: Float, width: Float, height: Float, on: Int, off: Int) {
    save()
    translate(x, y)
    scale(width / 100f, height / 180f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val lit = SevenSegments[ch].orEmpty()
    for ((name, path) in segmentPaths) {
        paint.color = if (name in lit) on else off
        drawPath(path, paint)
    }
    restore()
}

@Composable
internal fun SevenSegmentFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, lcd: Boolean, modifier: Modifier) {
    val label = rememberUi(600)
    val day = if (lcd) Color(style.inkColor ?: FaceDefaults.INK) else Color(style.ledColor ?: FaceDefaults.LEDS[0])
    val on = ink.accent(day).toArgb()
    val off = ink.accent(day).copy(alpha = 0.12f).toArgb()
    val plate = if (ink.night >= 0.5f) Ink.NIGHT_PLATE.toArgb() else Color(style.panelColor ?: FaceDefaults.PANEL).toArgb()
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val compact = min(w, h) < 120.dp.toPx()
        val margin = if (inSlot) 4.dp.toPx() else 10.dp.toPx()
        val platePad = if (lcd && !compact) min(w, h) * 0.06f else 0f
        val chars = d.h + d.m
        val spacing = 0.15f
        val colonW = 0.25f
        val units = chars.length + chars.length * spacing + colonW
        val availableW = w - 2 * (margin + platePad) - if (lcd && !compact) w * 0.18f else 0f
        val availableH = h * (if (lcd && !compact) 0.60f else if (compact) 0.82f else 0.75f)
        val digitH = min(availableH, availableW / (units * (100f / 180f))) * style.sizePct / 100f
        val digitW = digitH * 100f / 180f
        val gap = spacing * digitW
        val rowW = chars.length * digitW + chars.length * gap + colonW * digitW
        val x0 = if (lcd && !compact) margin + platePad + 0.05f * w else (w - rowW) / 2
        val y0 = (h - digitH) / 2 + if (lcd && !compact) 0.03f * h else 0f
        label.color = on
        label.textSize = min(20.dp.toPx(), 0.07f * h)
        return@FaceCanvas {
            if (lcd) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate }
                drawRoundRect(margin, margin, w - margin, h - margin, 14.dp.toPx(), 14.dp.toPx(), p)
            }
            var x = x0
            chars.forEachIndexed { i, ch ->
                if (i == d.h.length) {
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = on }
                    val r = digitW * 0.08f
                    drawCircle(x + colonW * digitW / 2, y0 + digitH * 0.34f, r, dot)
                    drawCircle(x + colonW * digitW / 2, y0 + digitH * 0.68f, r, dot)
                    x += colonW * digitW + gap
                }
                sevenDigit(ch, x, y0, digitW, digitH, on, off)
                x += digitW + gap
            }
            if (!compact) {
                if (lcd) {
                    drawText(d.weekday + "요일", margin + platePad, margin + platePad + label.textSize, label)
                    val date = "%02d-%02d".format(d.month, d.day)
                    drawText(date, w - margin - platePad - label.measureText(date), margin + platePad + label.textSize, label)
                    val side = (if (d.now.hour < 12) "AM" else "PM") + if (d.alarm != null) "  AL" else ""
                    drawText(side, w - margin - platePad - label.measureText(side), h - margin - platePad, label)
                } else {
                    val x = margin + 2.dp.toPx()
                    drawText(if (d.now.hour < 12) "AM" else "PM", x, h * 0.32f, label)
                    if (d.alarm != null) drawText("AL", x, h * 0.74f, label)
                }
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
