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
import kr.dot.catchpet.seg.ScreenMap

/**
 * No camera (switched off, or no permission): a drawn room instead of the preview, like Pokémon GO with AR off.
 * There is no frame, so nothing real is segmented; the "image" is the view itself (ScreenMap 1:1).
 */
class SceneSource : FrameSource {
    override fun latest(): Bitmap? = null

    override fun map(viewW: Float, viewH: Float): ScreenMap? =
        if (viewW <= 0f) null else ScreenMap(viewW.toInt(), viewH.toInt(), 0, viewW, viewH)

    @Composable
    override fun Preview(modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width; val h = size.height; val hz = h * HORIZON
            // wall
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFBEFE6), Color(0xFFF2DACB)), 0f, hz), size = Size(w, hz))
            // window with a soft sky, light falling into the room
            val wx = w * 0.12f; val wy = h * 0.12f; val ww = w * 0.36f; val wh = h * 0.24f
            drawCircle(Brush.radialGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)), Offset(wx + ww / 2, wy + wh / 2), w * 0.6f), w * 0.6f, Offset(wx + ww / 2, wy + wh / 2))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFCFE7FF), Color(0xFFF6FBFF)), wy, wy + wh), Offset(wx, wy), Size(ww, wh), CornerRadius(18f))
            drawRoundRect(Color.White, Offset(wx, wy), Size(ww, wh), CornerRadius(18f), style = Stroke(w * 0.018f))
            drawLine(Color.White, Offset(wx + ww / 2, wy), Offset(wx + ww / 2, wy + wh), w * 0.012f)
            drawLine(Color.White, Offset(wx, wy + wh / 2), Offset(wx + ww, wy + wh / 2), w * 0.012f)
            // a small frame on the wall
            drawRoundRect(Color(0xFFE9C9B6), Offset(w * 0.64f, h * 0.17f), Size(w * 0.2f, h * 0.13f), CornerRadius(10f))
            drawRoundRect(Color(0xFFFFE9DE), Offset(w * 0.66f, h * 0.18f), Size(w * 0.16f, h * 0.11f), CornerRadius(6f))
            // floor, planks running to a vanishing point above the horizon
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE5C7A3), Color(0xFFCFA57A)), hz, h), Offset(0f, hz), Size(w, h - hz))
            val vx = w / 2; val vy = hz - h * 0.4f
            for (i in -6..6) {
                val bx = w / 2 + i * w * 0.22f
                val t = (hz - vy) / (h - vy)
                drawLine(Color(0x22704A2A), Offset(vx + (bx - vx) * t, hz), Offset(bx, h), 2f)
            }
            drawRect(Color(0xFFE6CDBD), Offset(0f, hz - h * 0.012f), Size(w, h * 0.014f))
            // rug where the critters gather
            drawOval(Color(0x99F6BFCB), Offset(w * 0.04f, h * 0.66f), Size(w * 0.92f, h * 0.24f))
            drawOval(Color(0x66FFE3EA), Offset(w * 0.14f, h * 0.70f), Size(w * 0.72f, h * 0.16f))
        }
    }

    override fun close() {}

    companion object {
        /** Wall meets floor at this share of the height; critters stand below it (Critters scene mode). */
        const val HORIZON = 0.52f
    }
}
