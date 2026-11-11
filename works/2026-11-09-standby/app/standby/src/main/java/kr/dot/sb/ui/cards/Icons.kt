package kr.dot.sb.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kr.dot.sb.ui.state.WeatherCondition
import kr.dot.sb.ui.theme.LocalInk

/** design-system §4.2 icon set, drawn on a 24 grid with 2-unit round strokes. Weather glyphs are filled like iPhone's. */
enum class Ic { NOTE, VIDEO, ARROW, SUN, MOON, CLOUD, PARTLY, RAIN, SNOW, FOG, THUNDER, PREV, PLAY, PAUSE, NEXT, TIMER, SOUND, CALENDAR, LOCK, ALARM, USAGE, PHOTO, CLOSE, BELL }

internal fun weatherIc(c: WeatherCondition): Ic = when (c) {
    WeatherCondition.CLEAR -> Ic.SUN
    WeatherCondition.PARTLY -> Ic.PARTLY
    WeatherCondition.CLOUDY -> Ic.CLOUD
    WeatherCondition.RAIN -> Ic.RAIN
    WeatherCondition.SNOW -> Ic.SNOW
    WeatherCondition.FOG -> Ic.FOG
    WeatherCondition.THUNDER -> Ic.THUNDER
    WeatherCondition.NIGHT_CLEAR -> Ic.MOON
}

/**
 * One icon. [bg] cuts overlaps (partly cloudy). [stroke] draws the outlined variant used for empty states.
 * [color2] paints the second part of two-colour glyphs (sun, rain drops, bolt) when the theme colours weather icons.
 */
@Composable
fun IconView(ic: Ic, color: Color, modifier: Modifier, bg: Color = LocalInk.current.bg, stroke: Boolean = false, color2: Color = color) {
    Canvas(modifier) { scale(size.minDimension / 24f, pivot = Offset.Zero) { drawIc(ic, color, bg, stroke, color2) } }
}

private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
private fun cloudPath(dx: Float = 0f, dy: Float = 0f, k: Float = 1f): Path {
    fun c(x: Float, y: Float, r: Float) = circle(dx + x * k, dy + y * k, r * k)
    val p = Path().apply { addRoundRect(RoundRect(Rect(dx + 4f * k, dy + 13f * k, dx + 20.5f * k, dy + 19.5f * k), CornerRadius(3.25f * k))) }
    val a = Path().apply { op(p, c(8.5f, 14f, 4.5f), PathOperation.Union) }
    val b = Path().apply { op(a, c(13f, 11f, 6f), PathOperation.Union) }
    return Path().apply { op(b, c(17.5f, 15f, 4.5f), PathOperation.Union) }
}

private fun DrawScope.drawIc(ic: Ic, col: Color, bg: Color, outline: Boolean, c2: Color = col) {
    val st = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color = col) = drawLine(c, Offset(x1, y1), Offset(x2, y2), 2f, StrokeCap.Round)
    fun poly(vararg p: Float, fill: Boolean = true, c: Color = col) {
        val path = Path().apply { moveTo(p[0], p[1]); for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1]); close() }
        if (fill) drawPath(path, c) else drawPath(path, c, style = st)
    }
    fun cloud(dx: Float = 0f, dy: Float = 0f, k: Float = 1f) {
        val p = cloudPath(dx, dy, k)
        if (outline) drawPath(p, col, style = st) else drawPath(p, col)
    }
    when (ic) {
        Ic.NOTE -> {
            line(9f, 18f, 9f, 5.5f); line(9f, 5.5f, 19f, 3.5f); line(19f, 3.5f, 19f, 16f)
            if (outline) { drawCircle(col, 2.6f, Offset(6.5f, 18f), style = st); drawCircle(col, 2.6f, Offset(16.5f, 16f), style = st) }
            else { drawCircle(col, 3f, Offset(6.4f, 18f)); drawCircle(col, 3f, Offset(16.4f, 16f)) }
        }
        Ic.VIDEO -> {
            drawRoundRect(col, Offset(2.5f, 5f), androidx.compose.ui.geometry.Size(19f, 14f), CornerRadius(4f), style = st)
            poly(10f, 9f, 15.5f, 12f, 10f, 15f)
        }
        Ic.PHOTO -> {
            drawRoundRect(col, Offset(3f, 4f), androidx.compose.ui.geometry.Size(18f, 16f), CornerRadius(3f), style = st)
            drawCircle(col, 1.8f, Offset(9f, 9.5f))
            line(4f, 17f, 9.5f, 12.5f); line(9.5f, 12.5f, 13f, 15.5f); line(13f, 15.5f, 16f, 13f); line(16f, 13f, 20f, 16.5f)
        }
        Ic.ARROW -> poly(20.5f, 3.5f, 3.5f, 10.8f, 11f, 13f, 13.2f, 20.5f)
        Ic.SUN -> {
            if (outline) drawCircle(c2, 4.5f, Offset(12f, 12f), style = st) else drawCircle(c2, 5f, Offset(12f, 12f))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0); val s = kotlin.math.sin(a).toFloat(); val c = kotlin.math.cos(a).toFloat()
                line(12f + s * 8f, 12f - c * 8f, 12f + s * 10.2f, 12f - c * 10.2f, c2)
            }
        }
        Ic.MOON -> {
            val p = Path().apply { op(circle(11f, 13f, 8.5f), circle(17f, 7.5f, 7f), PathOperation.Difference) }
            if (outline) drawPath(p, c2, style = st) else drawPath(p, c2)
        }
        Ic.CLOUD -> cloud(0f, 0f)
        Ic.PARTLY -> {
            drawCircle(c2, 4.2f, Offset(15.5f, 8f))
            for (i in listOf(-1, 0, 1, 2)) {
                val a = Math.toRadians(i * 45.0); val s = kotlin.math.sin(a).toFloat(); val c = kotlin.math.cos(a).toFloat()
                line(15.5f + s * 6.6f, 8f - c * 6.6f, 15.5f + s * 8.3f, 8f - c * 8.3f, c2)
            }
            val p = cloudPath(-1f, 3f, 0.85f)
            drawPath(p, bg, style = Stroke(3f))
            drawPath(p, col)
        }
        Ic.RAIN -> { cloud(0f, -4f); line(8f, 18.5f, 7f, 21.5f, c2); line(12.5f, 18.5f, 11.5f, 21.5f, c2); line(17f, 18.5f, 16f, 21.5f, c2) }
        Ic.SNOW -> { cloud(0f, -4f); drawCircle(col, 1.4f, Offset(8f, 19.5f)); drawCircle(col, 1.4f, Offset(12.5f, 21.5f)); drawCircle(col, 1.4f, Offset(17f, 19.5f)) }
        Ic.FOG -> { line(4f, 7f, 20f, 7f); line(2.5f, 11f, 17f, 11f); line(6f, 15f, 21.5f, 15f); line(4f, 19f, 18f, 19f) }
        Ic.THUNDER -> { cloud(0f, -4f); poly(13f, 15f, 9.5f, 19.5f, 12f, 19.5f, 10.5f, 23f, 15f, 18f, 12.5f, 18f, c = c2) }
        Ic.PREV -> { poly(11f, 6f, 11f, 18f, 2.5f, 12f); poly(21f, 6f, 21f, 18f, 12.5f, 12f) }
        Ic.NEXT -> { poly(3f, 6f, 3f, 18f, 11.5f, 12f); poly(13f, 6f, 13f, 18f, 21.5f, 12f) }
        Ic.PLAY -> poly(6f, 3.5f, 20.5f, 12f, 6f, 20.5f)
        Ic.PAUSE -> {
            drawRoundRect(col, Offset(5.5f, 4f), androidx.compose.ui.geometry.Size(4.5f, 16f), CornerRadius(1f))
            drawRoundRect(col, Offset(14f, 4f), androidx.compose.ui.geometry.Size(4.5f, 16f), CornerRadius(1f))
        }
        Ic.TIMER -> { drawCircle(col, 8f, Offset(12f, 13.5f), style = st); line(12f, 13.5f, 12f, 9.5f); line(10f, 3f, 14f, 3f) }
        Ic.SOUND -> { line(4f, 10f, 4f, 14f); line(8f, 7f, 8f, 17f); line(12f, 4f, 12f, 20f); line(16f, 8f, 16f, 16f); line(20f, 10.5f, 20f, 13.5f) }
        Ic.CALENDAR -> {
            drawRoundRect(col, Offset(3.5f, 5f), androidx.compose.ui.geometry.Size(17f, 15.5f), CornerRadius(3f), style = st)
            line(3.5f, 10f, 20.5f, 10f); line(8f, 3f, 8f, 6.5f); line(16f, 3f, 16f, 6.5f)
        }
        Ic.LOCK -> {
            drawRoundRect(col, Offset(5f, 11f), androidx.compose.ui.geometry.Size(14f, 10f), CornerRadius(2.5f), style = st)
            val p = Path().apply { moveTo(8f, 11f); lineTo(8f, 8f); arcTo(Rect(8f, 4f, 16f, 12f), 180f, 180f, false); lineTo(16f, 11f) }
            drawPath(p, col, style = st)
        }
        Ic.ALARM -> {
            drawCircle(col, 7.5f, Offset(12f, 13f), style = st)
            line(12f, 9.5f, 12f, 13f); line(12f, 13f, 14.5f, 15f); line(3.5f, 6f, 6.5f, 3f); line(20.5f, 6f, 17.5f, 3f)
        }
        Ic.CLOSE -> { line(6.5f, 6.5f, 17.5f, 17.5f); line(17.5f, 6.5f, 6.5f, 17.5f) }
        Ic.BELL -> {
            // the mockup's bell: dome, flared rim, clapper
            val p = Path().apply {
                moveTo(4f, 15f); cubicTo(4f, 15f, 6f, 14f, 6f, 9f)
                arcTo(Rect(6f, 3f, 18f, 15f), 180f, 180f, false)
                cubicTo(18f, 14f, 20f, 15f, 20f, 15f); close()
            }
            if (outline) drawPath(p, col, style = st) else drawPath(p, col)
            drawPath(Path().apply { arcTo(Rect(10f, 17f, 14f, 21f), 180f, -180f, true) }, col, style = st)
        }
        Ic.USAGE -> {
            val p = Path().apply { arcTo(Rect(Offset(12f, 15f), 9f), 180f, 180f, true) }
            drawPath(p, col, style = st)
            line(12f, 15f, 16.5f, 9.5f)
            drawCircle(col, 1.8f, Offset(12f, 15f))
        }
    }
}
