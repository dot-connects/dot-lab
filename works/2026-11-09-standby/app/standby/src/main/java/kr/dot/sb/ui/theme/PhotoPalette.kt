package kr.dot.sb.ui.theme

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * r11 사진에서 색 뽑기 (plan-r11 A5): a photo's main colours by median cut, no palette library. [pick] is pure (tests
 * feed synthetic pixels); [decode] reads a picked photo small (≤ 96 px) on the caller's IO dispatcher.
 */
object PhotoPalette {
    /** The colours a theme edit is made from. [vivid] / [accent] are null only for an empty photo / a grey one. */
    data class Picked(val bg: Long, val vivid: Long?, val accent: Long?, val lightest: Long)

    private const val BOXES = 8
    private const val NONE = 0xFF000000

    private fun ch(p: Int, c: Int) = (p shr (16 - 8 * c)) and 0xFF

    /** Median cut into at most [BOXES] boxes; each box's average colour (opaque ARGB). Transparent pixels are skipped. */
    fun boxes(px: IntArray): List<Long> {
        val opaque = px.filter { (it ushr 24) >= 128 }
        if (opaque.isEmpty()) return emptyList()
        fun range(b: List<Int>, c: Int) = b.maxOf { ch(it, c) } - b.minOf { ch(it, c) }
        fun widest(b: List<Int>) = (0..2).maxBy { range(b, it) }
        val boxes = mutableListOf(opaque)
        while (boxes.size < BOXES) {
            val b = boxes.filter { it.size > 1 }.maxByOrNull { range(it, widest(it)) } ?: break
            val c = widest(b)
            if (range(b, c) == 0) break
            val sorted = b.sortedBy { ch(it, c) }
            // cut at the value step nearest the median, so one flat colour never lands in two boxes (a muddy average)
            val m = sorted.size / 2
            val v = ch(sorted[m], c)
            val lo = sorted.indexOfFirst { ch(it, c) == v }
            val hi = sorted.indexOfFirst { ch(it, c) > v }.let { if (it < 0) sorted.size else it }
            val cut = listOf(lo, hi).filter { it in 1 until sorted.size }.minByOrNull { kotlin.math.abs(it - m) } ?: m
            boxes.remove(b)
            boxes += sorted.subList(0, cut)
            boxes += sorted.subList(cut, sorted.size)
        }
        return boxes.map { b ->
            val (r, g, bl) = (0..2).map { c -> b.sumOf { ch(it, c).toLong() } / b.size }
            0xFF000000 or (r shl 16) or (g shl 8) or bl
        }
    }

    /**
     * bg = the darkest box with HSL saturation < 0.35 (else the darkest), vivid = the most saturated box with lightness
     * 0.3..0.7 (else the most saturated), accent = the lightest box with saturation ≥ 0.45 (else none).
     */
    fun pick(px: IntArray): Picked {
        val cs = boxes(px)
        if (cs.isEmpty()) return Picked(NONE, null, null, NONE)
        fun lum(c: Long) = Color(c).luminance()
        fun hsl(c: Long) = Hsl.toHsl(Color(c))
        val bg = cs.filter { hsl(it)[1] < 0.35f }.minByOrNull(::lum) ?: cs.minBy(::lum)
        val vivid = cs.filter { hsl(it)[2] in 0.3f..0.7f }.maxByOrNull { hsl(it)[1] } ?: cs.maxBy { hsl(it)[1] }
        val accent = cs.filter { hsl(it)[1] >= 0.45f }.maxByOrNull(::lum)
        return Picked(bg, vivid, accent, cs.maxBy(::lum))
    }

    /** The photo at [uri] decoded at most [size] px on its long side, as ARGB pixels (call off the main thread). */
    fun decode(ctx: Context, uri: Uri, size: Int = 96): IntArray {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val s = info.size
            val k = size.toFloat() / maxOf(s.width, s.height, 1)
            if (k < 1f) d.setTargetSize(maxOf(1, (s.width * k).toInt()), maxOf(1, (s.height * k).toInt()))
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height); bmp.recycle() }
    }
}
