package kr.dot.catchpet.seg

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenMapTest {
    @Test fun rotatedLandscapeBufferFillsTallViewCroppingTheSides() {
        // 1280x720 sensor buffer, rotated 90 -> 720x1280 upright, FILL_CENTER on a 1080x2400 view:
        // scale 1.875, image 1350 px wide, 135 px cut off each side
        val m = ScreenMap(1280, 720, 90, 1080f, 2400f)
        assertEquals(1.875f, m.scale, 1e-4f)
        assertEquals(-135f, m.left, 1e-3f)
        assertEquals(9 to 80, m.toGrid(0f, 1200f))           // 135 / 1.875 = 72 image px = grid col 9
        assertEquals(45 to 80, m.toGrid(540f, 1200f))        // view centre = grid centre
        // 270 turns the buffer the same way for sizes
        assertEquals(m.toGrid(0f, 1200f), ScreenMap(1280, 720, 270, 1080f, 2400f).toGrid(0f, 1200f))
    }

    @Test fun gridCellCentresRoundTripThroughTheView() {
        // unrotated 4:3 buffer letterboxed by height into a wide view: crop top and bottom
        val m = ScreenMap(640, 480, 0, 2000f, 1000f)
        assertEquals(0f, m.left, 1e-4f)
        for ((gx, gy) in listOf(0 to 0, 44 to 80, 89 to 159, 10 to 150)) {
            val vx = m.gridToViewX(gx + 0.5f); val vy = m.gridToViewY(gy + 0.5f)
            assertEquals(gx to gy, m.toGrid(vx, vy))
        }
        assertEquals(-250f, m.top, 1e-3f)                    // 480 * 3.125 = 1500 tall, 250 cut top and bottom
    }
}
