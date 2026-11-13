package kr.dot.catchpet.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kr.dot.catchpet.game.Life
import kr.dot.catchpet.game.Pose

/** Drawing a critter picture with a Pose (squash, hop, swing), its dust puff and its shadow. */
object Posed {
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** pic in its resting rect r, moved / rotated / scaled by p about its pivot. */
    fun draw(cv: Canvas, pic: Bitmap, r: RectF, p: Pose, paint: Paint) {
        val h = r.height()
        val px = r.centerX(); val py = r.top + h * p.pivot
        cv.save()
        cv.translate(p.dx * h, p.dy * h)
        if (p.rot != 0f) cv.rotate(p.rot, px, py)
        if (p.sx != 1f || p.sy != 1f) cv.scale(p.sx, p.sy, px, py)
        cv.drawBitmap(pic, null, r, paint)
        cv.restore()
    }

    /** Five little puffs spreading from the feet for the first 350 ms of a landing. */
    fun dust(cv: Canvas, r: RectF, landMs: Float, dark: Boolean = false) {
        val k = Life.dust(landMs) ?: return
        val w = r.width(); val h = r.height()
        dustPaint.color = if (dark) 0xFFB8C0E0.toInt() else 0xFFFFFFFF.toInt()
        dustPaint.alpha = (140 * (1f - k)).toInt()
        for (i in 0 until 5) {
            val o = (i - 2) / 2f
            cv.drawCircle(r.centerX() + o * w * (0.35f + 0.35f * k), r.bottom - h * (0.03f + 0.05f * k * (1f - kotlin.math.abs(o) * 0.5f)), h * 0.08f * (0.6f + 0.8f * k), dustPaint)
        }
    }

    /** Shadow scale for something lifted `lift` px above its feet: smaller and fainter the higher it goes. */
    fun shadowScale(lift: Float, h: Float): Float = 1f - 0.4f * (lift / (2f * h)).coerceIn(0f, 1f)
}
