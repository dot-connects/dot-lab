package kr.dot.catchpet.camera

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kr.dot.catchpet.seg.ScreenMap

/**
 * No camera (switched off, or no permission): the drawn yard (Yard) instead of the preview. The gyro still runs
 * (operator 11-13): the yard is a ring around the player pinned to azimuths like the critters, so turning the phone
 * pans it and tilting moves the horizon. There is no frame, so nothing real is segmented; the "image" is the view
 * itself (ScreenMap 1:1). The yard is painted by Critters in scene mode, on the game canvas.
 */
class SceneSource : FrameSource {
    override fun latest(): Bitmap? = null

    override fun map(viewW: Float, viewH: Float): ScreenMap? =
        if (viewW <= 0f) null else ScreenMap(viewW.toInt(), viewH.toInt(), 0, viewW, viewH)

    /** The yard itself is painted by Critters (scene mode) on the game canvas; this is only the sky behind it. */
    @Composable
    override fun Preview(modifier: Modifier) {
        Box(modifier.background(Color(Yard.palNow().sky1)))
    }

    override fun close() {}

    companion object {
        /** Sky meets the floor at this share of the height; critters stand below it (Critters scene mode). */
        const val HORIZON = 0.52f
    }
}
