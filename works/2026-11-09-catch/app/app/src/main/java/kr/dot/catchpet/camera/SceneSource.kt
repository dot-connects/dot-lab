package kr.dot.catchpet.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.seg.ScreenMap

/**
 * No camera (switched off, or no permission): a drawn room instead of the preview. The gyro still runs (operator
 * 11-13): the room is a ring around the player, its walls and rugs pinned to azimuths like the critters, so turning
 * the phone pans it and tilting moves the floor. There is no frame, so nothing real is segmented; the "image" is the
 * view itself (ScreenMap 1:1). look() = Critters.view(): yaw degrees and the floor shift (share of the height).
 */
class SceneSource(private val look: () -> Pair<Float, Float>) : FrameSource {
    override fun latest(): Bitmap? = null

    override fun map(viewW: Float, viewH: Float): ScreenMap? =
        if (viewW <= 0f) null else ScreenMap(viewW.toInt(), viewH.toInt(), 0, viewW, viewH)

    @Composable
    override fun Preview(modifier: Modifier) {
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
        Canvas(modifier) {
            frame   // redraw every frame: the room follows the phone
            val (yaw, shift) = look()
            val w = size.width; val h = size.height; val dy = shift * h; val hz = h * HORIZON + dy
            fun x(az: Float) = w / 2 + Critters.diff(az, yaw) / Critters.FOV_DEG * w
            val degW = w / Critters.FOV_DEG   // px per degree
            // wall (reaches above the screen when the phone tilts down)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFBEFE6), Color(0xFFF2DACB)), hz - h, hz), Offset(0f, 0f), Size(w, hz.coerceAtLeast(0f)))
            for ((az, kind) in WALL) {
                val cx = x(az)
                if (cx < -w || cx > 2 * w) continue
                when (kind) {
                    "window" -> {
                        val ww = 30 * degW; val wh = h * 0.24f; val wx = cx - ww / 2; val wy = hz - h * 0.40f
                        drawCircle(Brush.radialGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)), Offset(cx, wy + wh / 2), w * 0.6f), w * 0.6f, Offset(cx, wy + wh / 2))
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFCFE7FF), Color(0xFFF6FBFF)), wy, wy + wh), Offset(wx, wy), Size(ww, wh), CornerRadius(18f))
                        drawRoundRect(Color.White, Offset(wx, wy), Size(ww, wh), CornerRadius(18f), style = Stroke(w * 0.018f))
                        drawLine(Color.White, Offset(cx, wy), Offset(cx, wy + wh), w * 0.012f)
                        drawLine(Color.White, Offset(wx, wy + wh / 2), Offset(wx + ww, wy + wh / 2), w * 0.012f)
                    }
                    "frame" -> {
                        val fw = 16 * degW; val fh = h * 0.13f; val fy = hz - h * 0.36f
                        drawRoundRect(Color(0xFFE9C9B6), Offset(cx - fw / 2, fy), Size(fw, fh), CornerRadius(10f))
                        drawRoundRect(Color(0xFFFFE9DE), Offset(cx - fw / 2 + fw * 0.1f, fy + fh * 0.1f), Size(fw * 0.8f, fh * 0.8f), CornerRadius(6f))
                    }
                    "door" -> {
                        val dw = 24 * degW; val dh = h * 0.42f
                        drawRoundRect(Color(0xFFE2C1A6), Offset(cx - dw / 2, hz - dh), Size(dw, dh), CornerRadius(10f))
                        drawRoundRect(Color(0xFFEBD0B9), Offset(cx - dw / 2 + dw * 0.1f, hz - dh * 0.92f), Size(dw * 0.8f, dh * 0.38f), CornerRadius(8f))
                        drawCircle(Color(0xFFC79A5B), dw * 0.05f, Offset(cx + dw * 0.32f, hz - dh * 0.45f))
                    }
                    "plant" -> {
                        val pw = 12 * degW
                        drawRoundRect(Color(0xFFD98F6A), Offset(cx - pw / 2, hz - h * 0.09f), Size(pw, h * 0.09f), CornerRadius(8f))
                        for (k in -2..2) drawOval(Color(0xFF8DBF8B), Offset(cx + k * pw * 0.22f - pw * 0.18f, hz - h * 0.2f - (2 - kotlin.math.abs(k)) * h * 0.03f), Size(pw * 0.36f, h * 0.12f))
                    }
                }
            }
            // floor, planks pinned to azimuths and running to a vanishing point above the horizon
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE5C7A3), Color(0xFFCFA57A)), hz, hz + h), Offset(0f, hz), Size(w, (h - hz).coerceAtLeast(0f)))
            val vy = hz - h * 0.4f
            val t = (hz - vy) / (h - vy)
            var az = kotlin.math.floor((yaw - 60f) / PLANK_DEG) * PLANK_DEG
            while (az < yaw + 60f) {
                val bx = x(az) ; val vx = w / 2
                drawLine(Color(0x22704A2A), Offset(vx + (bx - vx) * t, hz), Offset(bx, h), 2f)
                az += PLANK_DEG
            }
            drawRect(Color(0xFFE6CDBD), Offset(0f, hz - h * 0.012f), Size(w, h * 0.014f))
            // rugs where the critters gather
            for (raz in RUGS) {
                val cx = x(raz); val rw = 46 * degW
                if (cx < -rw || cx > w + rw) continue
                drawOval(Color(0x99F6BFCB), Offset(cx - rw / 2, h * 0.66f + dy), Size(rw, h * 0.24f))
                drawOval(Color(0x66FFE3EA), Offset(cx - rw * 0.38f, h * 0.70f + dy), Size(rw * 0.76f, h * 0.16f))
            }
        }
    }

    override fun close() {}

    companion object {
        /** Wall meets floor at this share of the height; critters stand below it (Critters scene mode). */
        const val HORIZON = 0.52f
        const val PLANK_DEG = 6f
        /** The room around the player: wall pieces at world azimuths (degrees, any start) and rugs on the floor. */
        val WALL = listOf(0f to "window", 55f to "frame", 120f to "door", 190f to "window", 250f to "plant", 305f to "frame")
        val RUGS = listOf(0f, 120f, 240f)
    }
}
