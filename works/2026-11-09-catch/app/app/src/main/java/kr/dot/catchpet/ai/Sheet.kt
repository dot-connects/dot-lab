package kr.dot.catchpet.ai

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The bought 3x2 pose sheet (green background) -> six transparent pet frames. */
object Sheet {
    val KEYS = listOf("front", "smile", "walkA", "walkB", "sleep", "curl")   // row by row, left to right

    /** k = g - max(r, b): k > 60 is background (alpha 0); 18 < k <= 60 is the edge: partly clear, green pulled down. */
    fun keyGreen(argb: Int): Int {
        val a = argb ushr 24; val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
        val m = max(r, b)
        val k = g - m
        if (k > 60) return 0
        if (k <= 18) return argb
        val na = a * (60 - k) / 42
        return (na shl 24) or (r shl 16) or (m shl 8) or b
    }

    /**
     * Each third of the sheet is cut to the box of its non-green pixels plus a 6% margin (limbs that reach the
     * cell edge stay), then padded to a square with the feet on the bottom edge. A cell with nothing in it is skipped.
     */
    fun cells(sheet: Bitmap): Map<String, Bitmap> {
        val w = sheet.width; val h = sheet.height
        val px = IntArray(w * h)
        sheet.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = keyGreen(px[i])
        val out = LinkedHashMap<String, Bitmap>()
        for ((i, key) in KEYS.withIndex()) {
            val cx0 = (i % 3) * w / 3; val cx1 = (i % 3 + 1) * w / 3
            val cy0 = (i / 3) * h / 2; val cy1 = (i / 3 + 1) * h / 2
            var x0 = cx1; var y0 = cy1; var x1 = -1; var y1 = -1
            for (y in cy0 until cy1) for (x in cx0 until cx1) if ((px[y * w + x] ushr 24) > 24) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            if (x1 < 0) continue
            val mx = ((x1 - x0 + 1) * 0.06f).roundToInt(); val my = ((y1 - y0 + 1) * 0.06f).roundToInt()
            x0 = max(0, x0 - mx); x1 = min(w - 1, x1 + mx); y0 = max(0, y0 - my); y1 = min(h - 1, y1 + my)
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            val side = max(bw, bh)
            val sq = IntArray(side * side)
            val ox = (side - bw) / 2; val oy = side - bh
            for (y in 0 until bh) System.arraycopy(px, (y0 + y) * w + x0, sq, (oy + y) * side + ox, bw)
            out[key] = Bitmap.createBitmap(sq, side, side, Bitmap.Config.ARGB_8888)
        }
        return out
    }
}
