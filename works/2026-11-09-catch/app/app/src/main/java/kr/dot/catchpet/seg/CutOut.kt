package kr.dot.catchpet.seg

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kr.dot.catchpet.game.Mask
import kotlin.math.max
import kotlin.math.roundToInt

/** Cuts a grabbed thing out of the full-resolution upright frame with its (grid) mask. */
object CutOut {
    /** The cut-out (long side 512, transparent around the thing) and where it came from in the frame (px). */
    class Cut(val bmp: Bitmap, val src: Rect)

    /**
     * The mask scaled to frame size with a soft edge: a 3x3 box blur on the grid, then bilinear upscaling
     * (one 90x160 cell is ~8 frame px; the ramp hides the steps). No white rim.
     */
    fun alpha(m: Mask, w: Int, h: Int): Bitmap {
        val px = IntArray(m.w * m.h)
        for (y in 0 until m.h) for (x in 0 until m.w) {
            var n = 0
            for (dy in -1..1) for (dx in -1..1) if (m.at(x + dx, y + dy)) n++
            px[y * m.w + x] = (n * 255 / 9) shl 24
        }
        val small = Bitmap.createBitmap(px, m.w, m.h, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(small, w, h, true).also { small.recycle() }
    }

    /** null when the mask is empty. */
    fun make(frame: Bitmap, m: Mask): Cut? {
        val b = m.box ?: return null
        val sx = frame.width.toFloat() / m.w; val sy = frame.height.toFloat() / m.h
        val mx = b.w * sx * 0.08f; val my = b.h * sy * 0.08f
        val src = Rect(
            max(0, (b.x0 * sx - mx).toInt()), max(0, (b.y0 * sy - my).toInt()),
            minOf(frame.width, ((b.x1 + 1) * sx + mx).roundToInt()), minOf(frame.height, ((b.y1 + 1) * sy + my).roundToInt()),
        )
        if (src.width() <= 0 || src.height() <= 0) return null
        val a = alpha(m, frame.width, frame.height)
        val k = 512f / max(src.width(), src.height())
        val out = Bitmap.createBitmap(max(1, (src.width() * k).roundToInt()), max(1, (src.height() * k).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(k, k)
        c.translate(-src.left.toFloat(), -src.top.toFloat())
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(a, 0f, 0f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(frame, 0f, 0f, p)
        a.recycle()
        return Cut(out, src)
    }

    /** Frame-sized layer: inside the mask a heavily blurred copy of the frame (the hole the catch leaves). */
    fun hole(frame: Bitmap, m: Mask): Bitmap {
        val tiny = Bitmap.createScaledBitmap(frame, max(1, frame.width / 24), max(1, frame.height / 24), true)
        val out = Bitmap.createScaledBitmap(tiny, frame.width, frame.height, true).copy(Bitmap.Config.ARGB_8888, true)
        tiny.recycle()
        val a = alpha(m, frame.width, frame.height)
        Canvas(out).drawBitmap(a, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        a.recycle()
        return out
    }

    /** Share of pixels at least half opaque. */
    fun opaque(bmp: Bitmap): Float {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px.count { (it ushr 24) >= 128 }.toFloat() / px.size
    }
}
