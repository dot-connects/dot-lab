package kr.dot.catchpet.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The two-tone gashapon (mockup capsule A, operator 11-15): clear top, opaque coloured bottom with a seam band, a lid
 * tab and three air holes. Shared by the catch screen and the share card (spec-extras 3) so both are one shape.
 */
object CapsuleArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    /**
     * A capsule of radius [r] at ([x], [y]) turned [deg]; [inside] draws what it holds in the capsule's own frame
     * (origin = centre, already turned and clipped to the ball), before the coloured half covers its lower part.
     */
    fun draw(cv: Canvas, x: Float, y: Float, r: Float, deg: Float, color: Int, inside: (Canvas) -> Unit) {
        cv.save()
        cv.translate(x, y); cv.rotate(deg)
        if (r > 4f) { path.reset(); path.addCircle(0f, 0f, r, Path.Direction.CW); cv.clipPath(path) }
        inside(cv)
        if (r > 4f) {
            // the coloured bottom half, shaded light up-left to dark down-right
            fill.shader = RadialGradient(-r * .25f, -r * .6f, r * 1.8f,
                intArrayOf(shade(color, .35f), color, shade(color, -.32f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            rect.set(-r, -r, r, r)
            cv.drawArc(rect, 0f, 180f, true, fill)
            fill.shader = null
            fill.color = shade(color, -.45f); fill.alpha = 170
            for (hx in floatArrayOf(-.32f, 0f, .32f)) cv.drawOval(hx * r - r * .06f, r * (if (hx == 0f) .64f else .54f) - r * .043f,
                hx * r + r * .06f, r * (if (hx == 0f) .64f else .54f) + r * .043f, fill)
            fill.alpha = 255
        }
        cv.restore()
        if (r <= 4f) return
        cv.save()
        cv.translate(x, y)
        // glass over the top half: a bright rim, a highlight up-left
        fill.shader = RadialGradient(0f, 0f, r, intArrayOf(argb(.05f, 255, 255, 255), argb(.07f, 255, 255, 255), argb(.5f, 255, 255, 255)), floatArrayOf(0f, .82f, 1f), Shader.TileMode.CLAMP)
        cv.save(); cv.rotate(deg); cv.clipRect(-r, -r, r, 0f); cv.drawCircle(0f, 0f, r, fill); cv.restore()
        fill.shader = RadialGradient(-r * .35f, -r * .45f, r * .45f, intArrayOf(argb(.8f, 255, 255, 255), argb(.15f, 255, 255, 255), 0), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(0f, 0f, r, fill)
        fill.shader = null
        val line = max(1.5f, r * .033f)
        stroke.color = argb(.7f, 255, 255, 255); stroke.strokeWidth = line
        cv.drawCircle(0f, 0f, r, stroke)
        // seam band and lid tab turn with the capsule
        cv.rotate(deg)
        val band = max(3f, r * .16f)
        fill.color = shade(color, .2f)
        cv.drawRoundRect(-r - 1f, -band / 2, r + 1f, band / 2, band / 2, band / 2, fill)
        stroke.color = shade(color, -.25f); stroke.strokeWidth = line * .8f
        cv.drawRoundRect(-r - 1f, -band / 2, r + 1f, band / 2, band / 2, band / 2, stroke)
        fill.color = shade(color, .1f)
        cv.drawRoundRect(r * .88f, -band * .8f, r * 1.1f, band * .8f, band * .45f, band * .45f, fill)
        stroke.color = shade(color, -.3f)
        cv.drawRoundRect(r * .88f, -band * .8f, r * 1.1f, band * .8f, band * .45f, band * .45f, stroke)
        cv.restore()
    }

    /** Lighter (k > 0) or darker (k < 0) of an opaque colour. */
    fun shade(c: Int, k: Float): Int {
        fun f(v: Int) = (if (k > 0f) v + (255 - v) * k else v * (1f + k)).toInt().coerceIn(0, 255)
        return Color.rgb(f(Color.red(c)), f(Color.green(c)), f(Color.blue(c)))
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)
}
