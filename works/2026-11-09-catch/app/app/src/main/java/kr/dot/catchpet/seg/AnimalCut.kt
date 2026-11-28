package kr.dot.catchpet.seg

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The grip-time animal mask (research/seg-quality-2026-11-27.md, method "w": mIoU 0.66 -> 0.79 on 17 hand GTs):
 * magic touch on the whole frame at a seed point, united with crop DeepLab's sure pixels (> 0.8), kept inside the
 * padded box, only the connected piece holding the seed. Pure (JVM-testable); Segmenter.animalAlpha runs the models.
 */
object AnimalCut {
    const val SURE = 0.8f        // crop DeepLab this sure fills what one touch point missed (src05: head left out)
    const val SEED_MIN = 0.3f    // the claw point seeds the touch only where crop DeepLab sees the animal
    const val MIN_SHARE = 0.05f  // a piece under 5 % or over 95 % of the box is no shape: the grid cut stands in
    const val MAX_SHARE = 0.95f

    /**
     * Where to touch, in crop-normalized coords: the claw point when crop DeepLab [conf] (cw x ch, over the padded
     * crop) is >= SEED_MIN there, else the strongest 3x3-averaged conf inside the raw box [inner] (x0, y0, x1, y1,
     * crop-normalized) with a small pull to its centre (as the bench's run.html), else the box centre.
     */
    fun seed(conf: FloatArray, cw: Int, ch: Int, inner: FloatArray, claw: FloatArray?): FloatArray {
        if (claw != null) {
            val x = (claw[0] * cw).toInt().coerceIn(0, cw - 1); val y = (claw[1] * ch).toInt().coerceIn(0, ch - 1)
            if (conf[y * cw + x] >= SEED_MIN) return claw
        }
        val cx = (inner[0] + inner[2]) / 2; val cy = (inner[1] + inner[3]) / 2
        val bw = max(1e-6f, inner[2] - inner[0]); val bh = max(1e-6f, inner[3] - inner[1])
        var best = SEED_MIN; var sx = cx; var sy = cy
        for (y in 1 until ch - 1) for (x in 1 until cw - 1) {
            val u = (x + 0.5f) / cw; val v = (y + 0.5f) / ch
            if (u < inner[0] || u > inner[2] || v < inner[1] || v > inner[3]) continue
            var s = 0f
            for (dy in -1..1) for (dx in -1..1) s += conf[(y + dy) * cw + x + dx]
            val score = s / 9 - 0.15f * hypot((u - cx) / bw, (v - cy) / bh)
            if (score > best) { best = score; sx = u; sy = v }
        }
        return floatArrayOf(sx, sy)
    }

    /**
     * The alpha (0..1, ow x oh) over the padded crop, or null when nothing usable came out.
     * [touch] is the touched-thing mask of the whole frame (tw x th, 1 = touched); the crop is its pixels
     * [x0, x0 + ow) x [y0, y0 + oh). [conf] is crop DeepLab's animal confidence (cw x ch over the same crop).
     * [seed] is crop-normalized. Edges get a 3x3 box blur (bilinear upscaling does the rest).
     */
    fun combine(
        touch: FloatArray, tw: Int, th: Int, x0: Int, y0: Int, ow: Int, oh: Int,
        conf: FloatArray, cw: Int, ch: Int, seed: FloatArray,
    ): FloatArray? {
        if (ow <= 2 || oh <= 2) return null
        val on = BooleanArray(ow * oh)
        for (y in 0 until oh) for (x in 0 until ow) {
            val tx = x0 + x; val ty = y0 + y
            val t = if (tx in 0 until tw && ty in 0 until th) touch[ty * tw + tx] else 0f
            val c = conf[min(ch - 1, ((y + 0.5f) * ch / oh).toInt()) * cw + min(cw - 1, ((x + 0.5f) * cw / ow).toInt())]
            on[y * ow + x] = t > 0.5f || c > SURE
        }
        val keep = piece(on, ow, oh, (seed[0] * ow).toInt().coerceIn(0, ow - 1), (seed[1] * oh).toInt().coerceIn(0, oh - 1))
            ?: return null
        val share = keep.count { it }.toFloat() / keep.size
        if (share < MIN_SHARE || share > MAX_SHARE) return null
        return FloatArray(ow * oh) { i ->
            val x = i % ow; val y = i / ow; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until ow && yy in 0 until oh && keep[yy * ow + xx]) n++
            }
            n / 9f
        }
    }

    /** The 4-connected piece holding (sx, sy), or the one nearest it; null when [on] is empty. */
    fun piece(on: BooleanArray, w: Int, h: Int, sx: Int, sy: Int): BooleanArray? {
        var start = sy * w + sx
        if (!on[start]) {
            var bd = Long.MAX_VALUE; start = -1
            for (i in on.indices) if (on[i]) {
                val dx = (i % w - sx).toLong(); val dy = (i / w - sy).toLong(); val d = dx * dx + dy * dy
                if (d < bd) { bd = d; start = i }
            }
            if (start < 0) return null
        }
        val out = BooleanArray(on.size)
        val stack = IntArray(on.size); var top = 0
        stack[top++] = start; out[start] = true
        while (top > 0) {
            val i = stack[--top]; val x = i % w; val y = i / w
            fun push(j: Int) { if (on[j] && !out[j]) { out[j] = true; stack[top++] = j } }
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        return out
    }
}
