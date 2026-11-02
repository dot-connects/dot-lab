package kr.dot.parkingfloor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Draws the floor label the way a garage pillar is painted: stencil numerals with a base band under them.
 * An estimate is an outline over hazard stripes; a confirmed floor is solid over the pillar colour
 * (learned from the pillar photo) or over the ink colour when no colour is known.
 * A bitmap because RemoteViews can neither stroke text nor draw stripes.
 */
object PillarMark {
    const val HAZARD_YELLOW = 0xFFF2C230.toInt()
    const val HAZARD_BLACK = 0xFF16191C.toInt()
    private var face: Typeface? = null

    private fun face(context: Context): Typeface =
        face ?: Typeface.createFromAsset(context.assets, "stencil800.ttf").also { face = it }

    fun render(context: Context, label: String, estimate: Boolean, band: Int?, ink: Int, heightPx: Int): Bitmap {
        val height = heightPx.coerceIn(24, 300).toFloat()
        val bandH = (height * 0.12f).coerceAtLeast(4f)
        val gap = height * 0.06f
        val capH = height - bandH - gap
        val darkInk = isDark(ink)
        val shadowR = (height * 0.035f).coerceAtLeast(2f)
        val shadow = if (darkInk) 0xB3FFFFFF.toInt() else 0x8C000000.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face(context)
            color = ink
            textSize = capH
        }
        val bounds = Rect()
        paint.getTextBounds(label, 0, label.length, bounds)
        paint.textSize = capH * capH / bounds.height().coerceAtLeast(1)
        paint.getTextBounds(label, 0, label.length, bounds)
        val stroke = capH * 0.05f
        val pad = (shadowR * 2 + stroke).toInt() + 2
        val width = bounds.width() + pad * 2
        val bitmap = Bitmap.createBitmap(width, (height + pad * 2).toInt(), Bitmap.Config.ARGB_8888)
        bitmap.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(bitmap)

        paint.setShadowLayer(shadowR, 0f, shadowR * 0.4f, shadow)
        if (estimate) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeJoin = Paint.Join.ROUND
        }
        canvas.drawText(label, (pad - bounds.left).toFloat(), (pad - bounds.top).toFloat(), paint)

        val top = pad + capH + gap
        val rect = RectF(pad.toFloat(), top, (pad + bounds.width()).toFloat(), top + bandH)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (estimate) HAZARD_YELLOW else band ?: ink
            setShadowLayer(shadowR, 0f, shadowR * 0.4f, shadow)
        }
        canvas.drawRect(rect, fill)
        if (estimate) {
            val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HAZARD_BLACK; strokeWidth = bandH * 0.7f }
            canvas.save()
            canvas.clipRect(rect)
            val step = bandH * 1.4f
            var x = rect.left - bandH
            while (x < rect.right + bandH) {
                canvas.drawLine(x, rect.bottom, x + bandH, rect.top, stripe)
                x += step
            }
            canvas.restore()
        }
        return bitmap
    }

    fun isDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return r * 299 + g * 587 + b * 114 < 128_000
    }
}
