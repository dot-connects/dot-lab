package kr.dot.catchpet.seg

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure pixel steps on unpremultiplied ARGB ints (JVM-testable). */
object Pixels {
    /**
     * Every pixel made opaque over white. With a transparent cut-out the image model answered with a transparent
     * background instead of the green key (fine: keyGreen keeps alpha) but lost the fur's colours (a tortie came back
     * grey, 11-17, twice); the same cut on white kept its markings and came back on green. Sent images go through this.
     */
    fun onWhite(px: IntArray): IntArray = IntArray(px.size) { i ->
        val c = px[i]; val a = c ushr 24
        fun ch(v: Int) = (v * a + 255 * (255 - a) + 127) / 255
        (0xFF shl 24) or (ch((c shr 16) and 255) shl 16) or (ch((c shr 8) and 255) shl 8) or ch(c and 255)
    }

    /**
     * Fills what is not [known] (0 = unknown .. 1 = known) from what is around it: normalized box blurs of growing
     * radius, each pass only filling pixels still unfilled, so a hole takes the colours of its surroundings and none
     * of its own. Known pixels stay as they are; partly known ones blend toward the fill.
     */
    fun fill(px: IntArray, known: FloatArray, w: Int, h: Int): IntArray {
        val n = w * h
        if (known.none { it > 0f }) return px.copyOf()
        val cr = FloatArray(n); val cg = FloatArray(n); val cb = FloatArray(n); val wt = FloatArray(n)
        for (i in 0 until n) {
            val k = known[i].coerceIn(0f, 1f); val c = px[i]
            cr[i] = ((c shr 16) and 255) * k; cg[i] = ((c shr 8) and 255) * k; cb[i] = (c and 255) * k; wt[i] = k
        }
        val fr = FloatArray(n); val fg = FloatArray(n); val fb = FloatArray(n); val filled = BooleanArray(n) { known[it] >= 1f }
        var radius = 1
        while (filled.any { !it } && radius <= max(w, h)) {
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (filled[i]) continue
                var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
                for (yy in max(0, y - radius)..min(h - 1, y + radius)) for (xx in max(0, x - radius)..min(w - 1, x + radius)) {
                    val j = yy * w + xx; sr += cr[j]; sg += cg[j]; sb += cb[j]; sw += wt[j]
                }
                if (sw > 0.5f) { fr[i] = sr / sw; fg[i] = sg / sw; fb[i] = sb / sw; filled[i] = true }
            }
            radius *= 2
        }
        return IntArray(n) { i ->
            val k = known[i].coerceIn(0f, 1f)
            if (k >= 1f) return@IntArray px[i]
            val c = px[i]
            fun mix(own: Int, f: Float) = (own * k + f * (1 - k)).roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (mix((c shr 16) and 255, fr[i]) shl 16) or (mix((c shr 8) and 255, fg[i]) shl 8) or mix(c and 255, fb[i])
        }
    }
}
