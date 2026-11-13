package kr.dot.catchpet.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The claw's look, in dp (the caller scales and rotates the canvas). Split in two so a catch can sit between:
 * [back] = cable, hub and the back prong; [front] = the two side prongs drawn over the catch.
 *
 * The side prongs are arms whose tips reach (±s, l) from the hub (operator 11-13 "집게가 진짜 잡은 것처럼"): idle they
 * are the small claw of the mockup; on a body they spread past its sides and close in under its widest point, so
 * the claw visibly hugs what it holds instead of pinching the air over its head.
 */
object ClawArt {
    /** Tip of a side prong (s: sideways from the hub, l: down from the hub) and the back prong's length, dp. */
    class Arms(val s: Float, val l: Float, val mid: Float)

    /** The idle claw for openness 0..1 (the mockup's prongs). */
    fun idle(open: Float) = Arms(3f + open * 20f, 50f, 40f)

    /** A body under the hub: half width [a], its top [top] and height [h], all dp relative to the hub. */
    class Body(val a: Float, val top: Float, val h: Float)

    /**
     * The arms on a body: [open] 1 = spread just outside its sides, 0 = hugging under its widest point.
     * Big bodies (people) get arms at most MAX_REACH long: the claw takes them by the head like a big plush.
     */
    fun on(body: Body, open: Float): Arms {
        val a = min(body.a, MAX_SPREAD)
        val hugL = min(body.top + body.h * 0.6f, MAX_REACH)
        val openL = min(body.top + body.h * 0.42f, MAX_REACH)
        val hugS = a * 0.86f
        val openS = a + 12f
        val o = open.coerceIn(0f, 1f)
        return Arms(hugS + (openS - hugS) * o, hugL + (openL - hugL) * o, max(40f, min(hugL - 14f, body.top + body.h * 0.35f)))
    }

    /** Blend two arm shapes (k 0 = a, 1 = b). */
    fun mix(a: Arms, b: Arms, k: Float) = Arms(a.s + (b.s - a.s) * k, a.l + (b.l - a.l) * k, a.mid + (b.mid - a.mid) * k)

    const val MAX_SPREAD = 64f
    const val MAX_REACH = 120f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    fun back(cv: Canvas, hubX: Float, hubY: Float, railY: Float, arms: Arms) {
        stroke.shader = null
        stroke.strokeWidth = 1.6f; stroke.color = argb(.9f, 230, 233, 240); stroke.strokeCap = Paint.Cap.BUTT
        cv.drawLine(hubX, railY, hubX, hubY, stroke)
        // back prong first (behind the hub)
        stroke.strokeCap = Paint.Cap.ROUND; stroke.color = argb(.75f, 200, 205, 214); stroke.strokeWidth = 4f
        path.reset(); path.moveTo(hubX, hubY + 9f); path.quadTo(hubX + 4f, hubY + arms.mid * 0.6f, hubX, hubY + arms.mid)
        cv.drawPath(path, stroke)
        fill.shader = LinearGradient(hubX - 16f, 0f, hubX + 16f, 0f, intArrayOf(0xFF80868F.toInt(), 0xFFF6F7FA.toInt(), 0xFF6D727C.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(hubX - 15f, hubY - 7f, hubX + 15f, hubY + 10f, 6f, 6f, fill)
        fill.shader = null; fill.color = 0xFF2B2F36.toInt()
        cv.drawRect(hubX - 15f, hubY + 4f, hubX + 15f, hubY + 6f, fill)
    }

    fun front(cv: Canvas, hubX: Float, hubY: Float, arms: Arms) {
        stroke.strokeCap = Paint.Cap.ROUND
        val w = if (arms.l > 70f) 6f else 5f
        for (k in intArrayOf(-1, 1)) {
            val tipX = hubX + k * arms.s; val tipY = hubY + arms.l
            // shoulder out past the tip, then down and in: the arm wraps round the body's side
            val ctrlX = hubX + k * (arms.s + max(10f, arms.s * 0.32f)); val ctrlY = hubY + 9f + (arms.l - 9f) * 0.35f
            path.reset(); path.moveTo(hubX + k * 8f, hubY + 9f); path.quadTo(ctrlX, ctrlY, tipX, tipY)
            path.lineTo(tipX - k * 7f, tipY + 4f)   // the hooked tip, curled in under the body
            // a dark edge under the metal so the arm reads on white fur too
            stroke.shader = null; stroke.color = argb(.55f, 30, 33, 40); stroke.strokeWidth = w + 2.2f
            cv.drawPath(path, stroke)
            stroke.shader = LinearGradient(tipX - 4f, 0f, tipX + 4f, 0f, intArrayOf(0xFF8F95A0.toInt(), Color.WHITE, 0xFF7F8590.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.MIRROR)
            stroke.strokeWidth = w
            cv.drawPath(path, stroke)
        }
        stroke.shader = null; stroke.strokeCap = Paint.Cap.BUTT
        // the joints on the hub
        fill.color = 0xFF5C626C.toInt()
        for (k in intArrayOf(-1, 1)) cv.drawCircle(hubX + k * 8f, hubY + 9f, 2.6f, fill)
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)
}
