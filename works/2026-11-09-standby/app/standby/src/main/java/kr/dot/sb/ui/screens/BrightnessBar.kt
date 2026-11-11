package kr.dot.sb.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.ui.theme.LocalInk
import kotlin.math.cos
import kotlin.math.sin

/** The bar is 60 % of the width, at most 480 dp, centred. */
internal fun barWidth(w: Dp): Dp = min(w * 0.6f, 480.dp)

/** Finger x (dp from the left of the screen) to 0..1 along the bar. */
internal fun barFraction(xDp: Float, screenWDp: Float): Float {
    val bw = minOf(screenWDp * 0.6f, 480f)
    return ((xDp - (screenWDp - bw) / 2f) / bw).coerceIn(0f, 1f)
}

/**
 * Slim brightness bar at the top edge: sun glyph + track, filled up to [level]. Same ink as the board, so at night it
 * is the candle night. Touching or dragging on it calls [onLevel] (0..1 along the bar); [onPress] reports finger down/up
 * so the owner keeps it open while a finger is on it.
 */
@Composable
internal fun BrightnessBar(level: Float, onLevel: (Float) -> Unit, onPress: (Boolean) -> Unit, w: Dp, h: Dp, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val s = min(w, h)
    val bar = max(36.dp, s * 0.07f)
    val top = screenPadding(w, h).calculateTopPadding()
    val bw = barWidth(w)
    Box(
        modifier.padding(top = top).width(bw).height(bar).clip(CircleShape).background(ink.ink3)
            .pointerInput(bw) {
                val widthPx = size.width.toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPress(true)
                    onLevel((down.position.x / widthPx).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        onLevel((c.position.x / widthPx).coerceIn(0f, 1f))
                        c.consume()
                    }
                    onPress(false)
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(ink.ink))
        val icon = bar * 0.5f
        // the glyph sits at the left end: dark on the filled part, light on the empty track
        Sun(if (level > 0.12f) Color.Black else ink.ink, Modifier.align(Alignment.CenterStart).padding(start = (bar - icon) / 2).size(icon))
    }
}

@Composable
private fun Sun(color: Color, modifier: Modifier) {
    val d = LocalDensity.current
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension
        val stroke = with(d) { 1.6.dp.toPx() }
        drawCircle(color, r * 0.2f, c)
        for (i in 0 until 8) {
            val a = Math.PI / 4 * i
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            drawLine(color, Offset(c.x + dx * r * 0.33f, c.y + dy * r * 0.33f), Offset(c.x + dx * r * 0.48f, c.y + dy * r * 0.48f), stroke, StrokeCap.Round)
        }
    }
}
