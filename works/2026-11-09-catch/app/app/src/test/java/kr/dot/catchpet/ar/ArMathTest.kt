package kr.dot.catchpet.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArMathTest {
    @Test fun viewAngleChoosesTheNearestOfEightBakedViews() {
        // critter faces bearing 0; the camera walks around it
        assertEquals(0, ArMath.dirIndex(0f, 0f))       // seen from the front
        assertEquals(0, ArMath.dirIndex(0f, 22f))
        assertEquals(1, ArMath.dirIndex(0f, 23f))
        assertEquals(2, ArMath.dirIndex(0f, 90f))
        assertEquals(4, ArMath.dirIndex(0f, 180f))     // from behind
        assertEquals(4, ArMath.dirIndex(0f, -180f))
        assertEquals(7, ArMath.dirIndex(0f, -45f))
        assertEquals(6, ArMath.dirIndex(0f, -90f))
        assertEquals(2, ArMath.dirIndex(0f, -90f, sign = -1))
        // wraps: facing 350, viewer at 10 -> 20 degrees off -> front
        assertEquals(0, ArMath.dirIndex(350f, 10f))
        assertEquals(1, ArMath.dirIndex(350f, 40f))
    }

    @Test fun groundPointsSitAtTheAskedDistanceAndBearing() {
        val (x, z) = ArMath.ground(1f, 2f, 0f, 3f, 2f, 0f)          // straight ahead (+z), heading not unit
        assertEquals(1f, x, 1e-5f); assertEquals(4f, z, 1e-5f)
        val (x2, z2) = ArMath.ground(0f, 0f, 0f, 1f, 1.5f, 90f)     // turned toward +x
        assertEquals(1.5f, x2, 1e-5f); assertEquals(0f, z2, 1e-5f)
        // any turn keeps the distance, and the bearing matches the turn
        for (t in listOf(-25f, -10f, 0f, 17f, 25f)) {
            val (px, pz) = ArMath.ground(0.3f, -0.7f, -1f, -1f, 2.5f, t)
            assertEquals(2.5f, ArMath.flatDist(0.3f, -0.7f, px, pz), 1e-4f)
            assertEquals(ArMath.wrap(-135f + t), ArMath.bearing(0.3f, -0.7f, px, pz), 1e-3f)
        }
    }

    @Test fun projectionMapsCameraSpaceToViewPixels() {
        // a camera at the origin looking down -z, 90 degree fov (column-major, m[11] = -1)
        val vp = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1.01f, -1f, 0f, 0f, -0.1f, 0f)
        val c = ArMath.project(vp, 0f, 0f, -2f, 1000f, 2000f)!!
        assertEquals(500f, c[0], 1e-3f); assertEquals(1000f, c[1], 1e-3f)
        val q = ArMath.project(vp, 1f, 1f, -2f, 1000f, 2000f)!!   // up-right -> right and toward the top of the view
        assertEquals(750f, q[0], 1e-3f); assertEquals(500f, q[1], 1e-3f)
        assertNull(ArMath.project(vp, 0f, 0f, 2f, 1000f, 2000f)) // behind
        // an 8 cm critter 2 m away is twice as tall on screen as at 4 m
        fun hPx(d: Float) = ArMath.project(vp, 0f, -0.5f, -d, 1000f, 2000f)!![1] - ArMath.project(vp, 0f, -0.5f + ArMath.heightM(0.25f), -d, 1000f, 2000f)!![1]
        assertEquals(2f, hPx(2f) / hPx(4f), 1e-3f)
        assertEquals(0.064f, ArMath.heightM(0.2f), 1e-6f)
    }
}
