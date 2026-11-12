package kr.dot.catchpet.game

import kotlin.math.min
import kotlin.math.pow

/** The prize capsule (operator pick 11-12): wraps the catch, drops and bounces three times, rolls off to the right. */
class Capsule(startX: Float, startY: Float, val floorY: Float, val screenW: Float) {
    enum class Phase { WRAP, DROP, ROLL, GONE }

    var x = startX; private set
    var y = startY; private set
    var r = 0f; private set
    var rot = 0f; private set
    var phase = Phase.WRAP; private set
    private var t = 0f
    private var vy = 0f
    private var vx = 0f
    private var bounces = 0

    fun step(dtMs: Float) {
        t += dtMs
        when (phase) {
            Phase.WRAP -> {
                val k = min(1f, t / 420f)
                r = R * (1f - (1f - k).pow(3))
                if (t >= 520f) { r = R; phase = Phase.DROP }
            }
            Phase.DROP -> {
                vy += dtMs * 0.0042f
                y += vy * dtMs
                val floor = floorY - r
                if (y > floor) {
                    y = floor; vy *= -0.45f; bounces++
                    if (bounces >= 3) { phase = Phase.ROLL; vx = 0.22f }
                }
            }
            Phase.ROLL -> {
                vx += dtMs * 0.0006f
                x += vx * dtMs
                rot += vx * dtMs / r
                if (x - r > screenW) phase = Phase.GONE
            }
            Phase.GONE -> {}
        }
    }

    companion object { const val R = 64f }
}
