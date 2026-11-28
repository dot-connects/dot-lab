package kr.dot.catchpet.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The pet's one-line cloud (operator 11-16, mockup features 1A / picker 가): the app cloud's paper and shadow,
 * bold text up to two lines, two puffs toward the pet. Floor: above the pet. Wall: beside it toward the middle.
 * A tap closes it; [Talk.SHOW_MS] closes it too.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class TalkBubble(ctx: Context, text: String, private val side: PetBrain.Zone, private val showMs: Long = Talk.SHOW_MS, private val onTap: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val wall = side == PetBrain.Zone.LEFT || side == PetBrain.Zone.RIGHT
    private val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#24331F"); textSize = 13 * d; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val lay = StaticLayout.Builder.obtain(text, 0, text.length, ink, (if (wall) 120 else 160).times(d).roundToInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    private val textW = (0 until lay.lineCount).maxOf { lay.getLineWidth(it) }
    private val pad = 8 * d          // room for the shadow
    private val puff = 22 * d
    private val bodyW = ceil(textW + 24 * d)
    private val bodyH = ceil(lay.height + 14 * d)
    val wPx = (bodyW + pad * 2 + if (wall) puff else 0f).roundToInt()
    val hPx = (bodyH + pad * 2 + if (wall) 0f else puff).roundToInt()
    /** The body's left edge: on the left wall the puffs come first. */
    private val ox = pad + if (side == PetBrain.Zone.LEFT) puff else 0f
    private val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDF6"); setShadowLayer(6 * d, 0f, 2 * d, 0x40000000) }
    private val r = RectF()
    private val born = SystemClock.uptimeMillis()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // shadow layers need software drawing

    fun expired() = SystemClock.uptimeMillis() - born > showMs

    override fun onDraw(c: Canvas) {
        r.set(ox, pad, ox + bodyW, pad + bodyH)
        c.drawRoundRect(r, 18 * d, 18 * d, cloud)
        val cy = pad + bodyH / 2
        when (side) {   // two puffs, the small one nearest the pet
            PetBrain.Zone.LEFT -> { c.drawCircle(ox - 8 * d, cy + 2 * d, 6 * d, cloud); c.drawCircle(ox - 18 * d, cy + 5 * d, 3.5f * d, cloud) }
            PetBrain.Zone.RIGHT -> { c.drawCircle(ox + bodyW + 8 * d, cy + 2 * d, 6 * d, cloud); c.drawCircle(ox + bodyW + 18 * d, cy + 5 * d, 3.5f * d, cloud) }
            else -> { val mx = ox + bodyW / 2; c.drawCircle(mx - 5 * d, pad + bodyH + 7 * d, 6 * d, cloud); c.drawCircle(mx + 3 * d, pad + bodyH + 17 * d, 3.5f * d, cloud) }
        }
        c.save()
        c.translate(ox + (bodyW - lay.width) / 2, pad + 7 * d)
        lay.draw(c)
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) onTap()
        return true
    }

    /** Where it goes beside the pet's window box (px), kept on screen. */
    fun place(p: WindowManager.LayoutParams, pet: Rect, screenW: Int, screenH: Int) {
        when (side) {
            PetBrain.Zone.LEFT -> { p.x = pet.right - (pad / 2).roundToInt(); p.y = pet.centerY() - hPx / 2 }
            PetBrain.Zone.RIGHT -> { p.x = pet.left - wPx + (pad / 2).roundToInt(); p.y = pet.centerY() - hPx / 2 }
            else -> { p.x = pet.centerX() - wPx / 2; p.y = pet.top - hPx + (pad).roundToInt() }
        }
        p.x = p.x.coerceIn(0, (screenW - wPx).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH - hPx).coerceAtLeast(0))
    }

    fun params(pet: Rect, screenW: Int, screenH: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            wPx, hPx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0   // same origin as the pet window
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            place(this, pet, screenW, screenH)
        }
}
