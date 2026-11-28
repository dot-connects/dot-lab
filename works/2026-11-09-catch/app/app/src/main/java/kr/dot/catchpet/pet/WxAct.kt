package kr.dot.catchpet.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The weather act in its own untouchable window around the pet (phase C, mockup wx.js): a critter plays its baked
 * frames at 12 fps, act -> loop twice -> act backwards; a photo pet (no frames) gets a light 2D prop that pops on and
 * off. The frames are this view's only copy: [recycle] frees them when the act ends.
 *
 * Frames: [bodyPx] is the height the pet's front picture has on screen; the first act frame's opaque box is scaled to
 * it and its bottom centre sits on the pet's feet, so the act starts where the idle pet stands.
 */
@SuppressLint("ViewConstructor")
class WxActView(
    ctx: Context, val state: Weather.State,
    private val act: List<Bitmap>, private val loop: List<Bitmap>,
    private val pet: Rect, bodyPx: Float,
) : View(ctx) {
    private val seq: List<Bitmap> = if (act.isEmpty()) emptyList() else act + loop + loop + act.reversed()
    val framed = seq.isNotEmpty()
    val lengthMs: Long = if (framed) seq.size * 1000L / WxPack.FPS else PROP_MS
    private val born = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    // frames: scale and where the frame's corner goes relative to the pet's feet
    private val box = act.firstOrNull()?.let { b ->
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        Frames.opaqueBox(px, b.width, b.height)
    }
    private val k = box?.let { bodyPx / (it[3] - it[1] + 1) } ?: 1f
    val wPx: Int = if (framed) ceil(act[0].width * k).toInt() else (pet.width() * 1.6f).roundToInt()
    val hPx: Int = if (framed) ceil(act[0].height * k).toInt() else (pet.height() * 1.5f).roundToInt()

    fun params(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        wPx, hPx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        if (framed && box != null) {
            x = (pet.centerX() - (box[0] + box[2] + 1) / 2f * k).roundToInt()
            y = (pet.bottom - (box[3] + 1) * k).roundToInt()
        } else { x = pet.left - (pet.width() * 0.3f).roundToInt(); y = pet.top - (pet.height() * 0.5f).roundToInt() }
    }

    fun elapsed() = SystemClock.uptimeMillis() - born
    fun done() = elapsed() >= lengthMs

    /** The frame index now; the service redraws only when it changes. */
    fun frameAt(ms: Long) = (ms * WxPack.FPS / 1000).toInt()

    override fun onDraw(c: Canvas) = drawAt(c, elapsed())

    fun drawAt(c: Canvas, ms: Long) {
        if (framed) {
            val b = seq[frameAt(ms).coerceIn(0, seq.size - 1)]
            dst.set(0f, 0f, b.width * k, b.height * k)
            c.drawBitmap(b, null, dst, paint)
        } else Props.draw(c, state.key, pet.width() * 0.3f, pet.height() * 0.5f, pet.width().toFloat(), ms, lengthMs)
    }

    fun recycle() { (act + loop).forEach(Bitmap::recycle) }

    companion object { const val PROP_MS = 4000L }
}

/** Light 2D props over a photo pet in a box of s px at (ox, oy): umbrella, scarf, mask, sunglasses, snow cap, sweat. */
object Props {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val r = RectF()

    fun draw(c: Canvas, key: String, ox: Float, oy: Float, s: Float, ms: Long, len: Long) {
        // pops on over 250 ms, off over the last 250 ms, a small bob in between
        val pop = min(1f, min(ms, len - ms).coerceAtLeast(0L) / 250f)
        val bob = sin(ms / 260f) * s * 0.015f
        c.save()
        c.translate(ox, oy + bob)
        c.scale(pop, pop, s / 2, s / 2)
        line.strokeWidth = s * 0.035f
        when (key) {
            "rain" -> {   // umbrella held over the head
                fill.color = 0xFFE8574A.toInt()
                r.set(s * 0.12f, -s * 0.38f, s * 0.98f, s * 0.42f)
                path.reset(); path.arcTo(r, 180f, 180f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFFFFFDF6.toInt()
                for (i in 0..3) c.drawCircle(s * (0.22f + i * 0.22f), s * 0.03f, s * 0.11f, fill)   // scalloped rim
                fill.color = 0xFFE8574A.toInt(); c.drawCircle(s * 0.55f, -s * 0.4f, s * 0.035f, fill)
                line.color = 0xFF6E5640.toInt()
                c.drawLine(s * 0.55f, -s * 0.05f, s * 0.55f, s * 0.5f, line)
                r.set(s * 0.55f, s * 0.44f, s * 0.71f, s * 0.6f); c.drawArc(r, 0f, 180f, false, line)
            }
            "cold" -> {   // scarf round the neck, one end hanging
                fill.color = 0xFFD9483B.toInt()
                r.set(s * 0.18f, s * 0.58f, s * 0.82f, s * 0.72f); c.drawRoundRect(r, s * 0.07f, s * 0.07f, fill)
                r.set(s * 0.6f, s * 0.64f, s * 0.74f, s * 0.92f); c.drawRoundRect(r, s * 0.04f, s * 0.04f, fill)
                line.color = 0xFFFFFDF6.toInt(); line.strokeWidth = s * 0.02f
                for (i in 0..2) c.drawLine(s * (0.3f + i * 0.15f), s * 0.6f, s * (0.27f + i * 0.15f), s * 0.7f, line)
            }
            "dust" -> {   // white mask with ear strings
                line.color = 0xFFB8B0A2.toInt(); line.strokeWidth = s * 0.018f
                c.drawLine(s * 0.32f, s * 0.52f, s * 0.14f, s * 0.42f, line); c.drawLine(s * 0.68f, s * 0.52f, s * 0.86f, s * 0.42f, line)
                fill.color = 0xFFF7F5F0.toInt()
                r.set(s * 0.3f, s * 0.44f, s * 0.7f, s * 0.66f); c.drawRoundRect(r, s * 0.08f, s * 0.08f, fill)
                line.color = 0xFFD8D2C6.toInt()
                c.drawLine(s * 0.36f, s * 0.52f, s * 0.64f, s * 0.52f, line); c.drawLine(s * 0.36f, s * 0.58f, s * 0.64f, s * 0.58f, line)
            }
            "sun" -> {    // sunglasses
                fill.color = 0xFF2A2622.toInt()
                r.set(s * 0.26f, s * 0.32f, s * 0.47f, s * 0.46f); c.drawRoundRect(r, s * 0.06f, s * 0.06f, fill)
                r.set(s * 0.53f, s * 0.32f, s * 0.74f, s * 0.46f); c.drawRoundRect(r, s * 0.06f, s * 0.06f, fill)
                line.color = 0xFF2A2622.toInt(); line.strokeWidth = s * 0.025f
                c.drawLine(s * 0.46f, s * 0.36f, s * 0.54f, s * 0.36f, line)
                fill.color = 0x66FFFFFF; c.drawCircle(s * 0.32f, s * 0.36f, s * 0.025f, fill); c.drawCircle(s * 0.59f, s * 0.36f, s * 0.025f, fill)
            }
            "snow" -> {   // knitted cap with a pompom
                fill.color = 0xFF5A8FD6.toInt()
                r.set(s * 0.22f, -s * 0.12f, s * 0.78f, s * 0.4f)
                path.reset(); path.arcTo(r, 180f, 180f); path.close(); c.drawPath(path, fill)
                fill.color = 0xFFFFFDF6.toInt()
                r.set(s * 0.18f, s * 0.08f, s * 0.82f, s * 0.2f); c.drawRoundRect(r, s * 0.06f, s * 0.06f, fill)
                c.drawCircle(s * 0.5f, -s * 0.14f, s * 0.08f, fill)
            }
            "hot" -> {    // two sweat drops beside the head
                fill.color = 0xFF7EC3F0.toInt()
                for ((x, y, z) in listOf(Triple(0.84f, 0.18f, 1f), Triple(0.12f, 0.3f, 0.7f))) {
                    val d = s * 0.07f * z; val cx = s * x; val cy = s * y
                    path.reset(); path.moveTo(cx, cy - d * 1.9f)
                    path.quadTo(cx + d * 1.1f, cy - d * 0.2f, cx, cy + d); path.quadTo(cx - d * 1.1f, cy - d * 0.2f, cx, cy - d * 1.9f)
                    c.drawPath(path, fill)
                }
            }
        }
        c.restore()
    }
}
