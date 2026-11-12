package kr.dot.catchpet.ar

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geometry for the AR critters (world = ARCore metres, y up; matrices are OpenGL column-major). */
object ArMath {
    /** Real height in metres: 8 cm at critter_scale 1/4 (the web figure / 4), proportional otherwise. */
    fun heightM(critterScale: Float): Float = 0.08f * critterScale / 0.25f

    /** An angle wrapped into -180..180 degrees. */
    fun wrap(deg: Float): Float = ((deg % 360f) + 540f) % 360f - 180f

    /** Compass-style bearing (degrees, atan2(dx, dz)) of the point (toX, toZ) seen from (fromX, fromZ). */
    fun bearing(fromX: Float, fromZ: Float, toX: Float, toZ: Float): Float =
        Math.toDegrees(atan2((toX - fromX).toDouble(), (toZ - fromZ).toDouble())).toFloat()

    /**
     * Which baked view (dir0..dir7, dir0 = facing the viewer, +45 degrees each) shows a critter that faces
     * [facingDeg] when the camera sits at bearing [viewerDeg] from it. [sign] flips the turn direction
     * (calibration knob: the bake's rotation sense is unverified on a phone).
     */
    fun dirIndex(facingDeg: Float, viewerDeg: Float, sign: Int = 1): Int =
        Math.floorMod((sign * wrap(viewerDeg - facingDeg) / 45f).roundToInt(), 8)

    /**
     * A floor point [dist] metres from the camera (camX, camZ) along its horizontal heading (hx, hz),
     * turned by [turnDeg] (positive = towards +x when heading is +z). Returns (x, z).
     */
    fun ground(camX: Float, camZ: Float, hx: Float, hz: Float, dist: Float, turnDeg: Float): Pair<Float, Float> {
        val n = sqrt(hx * hx + hz * hz).takeIf { it > 1e-6f } ?: return camX to camZ + dist
        val a = Math.toRadians(turnDeg.toDouble())
        val ux = hx / n; val uz = hz / n
        // rotate (ux, uz) about +y so that +turn moves +z toward +x (same sense as [bearing])
        val rx = (ux * cos(a) + uz * sin(a)).toFloat()
        val rz = (-ux * sin(a) + uz * cos(a)).toFloat()
        return camX + rx * dist to camZ + rz * dist
    }

    /** Horizontal distance between two world points. */
    fun flatDist(ax: Float, az: Float, bx: Float, bz: Float): Float = sqrt((ax - bx) * (ax - bx) + (az - bz) * (az - bz))

    /**
     * World point -> view px through a view-projection matrix (column-major, OpenGL clip space, y up).
     * Null when the point is behind the camera.
     */
    fun project(vp: FloatArray, x: Float, y: Float, z: Float, viewW: Float, viewH: Float): FloatArray? {
        val cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12]
        val cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13]
        val cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15]
        if (cw <= 1e-4f) return null
        return floatArrayOf((cx / cw + 1f) / 2f * viewW, (1f - cy / cw) / 2f * viewH)
    }
}
