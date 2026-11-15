package kr.dot.catchpet.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.roundToInt

/**
 * The 집기 button (operator 11-15, mockups/button.html: B's steel plate with A's button): a chrome-ringed red
 * arcade dome on a brushed steel plate with four screws and the label engraved under the dome. dp units.
 * Pressed sinks the dome; grey while the claw is busy.
 */
object ClawButton {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

    fun draw(cv: Canvas, x: Float, y: Float, radius: Float, side: Float, foot: Float, ready: Boolean, pressed: Boolean) {
        // drawn at the mockup size (dome 29) and scaled, so screws, label and bevels shrink with it
        val k = radius / 29f
        cv.save(); cv.scale(k, k, x, y)
        drawBase(cv, x, y, side / k, foot / k, ready, pressed)
        cv.restore()
    }

    private fun drawBase(cv: Canvas, x: Float, y: Float, side: Float, foot: Float, ready: Boolean, pressed: Boolean) {
        val r = 29f
        val l = x - r - side; val t = y - r - side; val rt = x + r + side; val b = y + r + foot
        // plate: drop shadow, brushed steel, a light top edge and a dark rim
        fill.shader = null; fill.color = argb(.4f, 0, 0, 0)
        cv.drawRoundRect(l + 1f, t + 5f, rt + 1f, b + 5f, 10f, 10f, fill)
        fill.color = Color.BLACK   // a shader still takes the paint's alpha
        fill.shader = LinearGradient(l, t, rt, b, intArrayOf(0xFFEEF0F3.toInt(), 0xFFA9AFB8.toInt(), 0xFFD6DAE0.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(l, t, rt, b, 10f, 10f, fill); fill.shader = null
        stroke.pathEffect = null; stroke.strokeWidth = 1f; stroke.color = 0xFF7D838F.toInt()
        cv.drawRoundRect(l + .5f, t + .5f, rt - .5f, b - .5f, 10f, 10f, stroke)
        stroke.color = argb(.9f, 255, 255, 255); cv.drawLine(l + 8f, t + 1.5f, rt - 8f, t + 1.5f, stroke)
        // four screws with slots
        for ((sx, sy) in listOf(l + 8f to t + 8f, rt - 8f to t + 8f, l + 8f to b - 8f, rt - 8f to b - 8f)) {
            fill.color = Color.BLACK; fill.shader = RadialGradient(sx - 1f, sy - 1f, 4f, 0xFFFAFAFA.toInt(), 0xFF6B717A.toInt(), Shader.TileMode.CLAMP)
            cv.drawCircle(sx, sy, 3.2f, fill); fill.shader = null
            stroke.color = 0xFF555A62.toInt(); stroke.strokeWidth = 0.9f
            cv.drawLine(sx - 2f, sy + 1f, sx + 2f, sy - 1f, stroke)
        }
        // engraved label: dark letters with a light lip below
        text.textSize = 13f; text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE; cv.drawText("집기", x, b - 8f, text)
        text.color = 0xFF4A505A.toInt(); cv.drawText("집기", x, b - 9f, text)
        // chrome bezel, the dark well, the dome's red side, then the dome
        fill.color = Color.BLACK; fill.shader = SweepGradient(x, y, intArrayOf(0xFF8A9099.toInt(), 0xFFF7F8FA.toInt(), 0xFFAAB0B9.toInt(), 0xFF5F656E.toInt(), 0xFFD9DDE3.toInt(), 0xFF8A9099.toInt()), null)
        cv.drawCircle(x, y, r + 6f, fill); fill.shader = null
        fill.color = 0xFF2A070C.toInt(); cv.drawCircle(x, y, r + 1f, fill)
        val sink = if (pressed && ready) 2.5f else 0f
        val side = if (ready) 0xFF6A0510.toInt() else 0xFF3C4046.toInt()
        if (sink == 0f) { fill.color = side; cv.drawCircle(x, y + 2.5f, r - 1.5f, fill) }
        val dome = if (ready) intArrayOf(0xFFFF7A80.toInt(), 0xFFE01B2C.toInt(), 0xFFA50D1B.toInt())
            else intArrayOf(0xFFC4C8CE.toInt(), 0xFF8C9199.toInt(), 0xFF5E636A.toInt())
        val dy = sink - 1f
        fill.color = Color.BLACK; fill.shader = RadialGradient(x - .16f * r, y + dy - .4f * r, 1.3f * r, dome, floatArrayOf(0f, .45f, .8f), Shader.TileMode.CLAMP)
        cv.drawCircle(x, y + dy, r - 1.5f, fill); fill.shader = null
        // the glossy highlight
        cv.save(); cv.rotate(-18f, x - .12f * r, y + dy - .55f * r)
        fill.color = Color.BLACK; fill.shader = LinearGradient(0f, y + dy - .8f * r, 0f, y + dy - .3f * r, argb(.85f, 255, 255, 255), argb(0f, 255, 255, 255), Shader.TileMode.CLAMP)
        cv.drawOval(x - .58f * r, y + dy - .8f * r, x + .34f * r, y + dy - .3f * r, fill); fill.shader = null
        cv.restore()
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)
}
