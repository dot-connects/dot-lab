package kr.dot.catchpet.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kr.dot.catchpet.seg.ScreenMap

/**
 * No camera (switched off, or no permission): the drawn yard (Yard) instead of the preview. The gyro still runs
 * (operator 11-13): the yard is a ring around the player pinned to azimuths like the critters, so turning the phone
 * pans it and tilting moves the horizon. There is no frame, so nothing real is segmented; the "image" is the view
 * itself (ScreenMap 1:1). look() = Critters.view(): yaw degrees and the floor shift (share of the height).
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
            frame   // redraw every frame: the yard follows the phone
            val (yaw, shift) = look()
            drawIntoCanvas { Yard.back(it.nativeCanvas, Yard.View(size.width, size.height, yaw, shift, SystemClock.uptimeMillis() / 1000f, Yard.palNow())) }
        }
    }

    override fun close() {}

    companion object {
        /** Sky meets the floor at this share of the height; critters stand below it (Critters scene mode). */
        const val HORIZON = 0.52f
    }
}
