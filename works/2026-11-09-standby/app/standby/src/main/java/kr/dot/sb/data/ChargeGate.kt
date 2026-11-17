package kr.dot.sb.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * plan-r15 C2: the charge start waits for the phone to lie still and not face down (an upright stand has z ≈ 0, so
 * there is no "facing up" rule). [window] = about one second of TYPE_ACCELEROMETER samples (x, y, z in m/s²).
 * Thresholds are first guesses, tuned from the measure log on the real Fold/Flip.
 */
object ChargeGate {
    class Opts(
        /** 가로로 놓였을 때만 켜기: gravity mostly along the device x axis. */
        val landscapeOnly: Boolean = false,
        val moveStd: Float = 0.6f,
        val faceDownZ: Float = -7f,
        val landscapeX: Float = 7f,
    )

    enum class Posture(val ko: String) { STILL("가만히"), MOVING("움직임"), FACE_DOWN("엎어짐"), NOT_LANDSCAPE("가로 아님"), NONE("샘플 없음") }

    class Stats(val x: Float, val y: Float, val z: Float, val std: Float, val n: Int)

    fun stats(window: List<FloatArray>): Stats? {
        if (window.isEmpty()) return null
        val n = window.size
        val mags = window.map { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
        val mean = mags.average().toFloat()
        val std = sqrt(mags.sumOf { ((it - mean) * (it - mean)).toDouble() } / n).toFloat()
        return Stats(window.map { it[0] }.average().toFloat(), window.map { it[1] }.average().toFloat(), window.map { it[2] }.average().toFloat(), std, n)
    }

    fun posture(window: List<FloatArray>, opts: Opts): Posture {
        val s = stats(window) ?: return Posture.NONE
        return when {
            s.std > opts.moveStd -> Posture.MOVING
            s.z < opts.faceDownZ -> Posture.FACE_DOWN
            opts.landscapeOnly && !(abs(s.x) > opts.landscapeX && abs(s.x) > abs(s.y)) -> Posture.NOT_LANDSCAPE
            else -> Posture.STILL
        }
    }

    fun ok(window: List<FloatArray>, opts: Opts): Boolean = posture(window, opts) == Posture.STILL
}
