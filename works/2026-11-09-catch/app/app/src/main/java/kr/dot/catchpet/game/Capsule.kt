package kr.dot.catchpet.game

import kotlin.math.min
import kotlin.math.pow

/**
 * The prize capsule (felt mockup v5, core.js dropAndRoll): hangs from the claw (the game places it every frame), then
 * falls (g 0.0042 px/ms²), bounces three times (x0.45) with a squash and rolls off to the right. [rot] in degrees;
 * [sx]/[sy] squash about its bottom.
 */
class Capsule(startX: Float, startY: Float, val floorY: Float, val screenW: Float) {
    enum class Phase { HANG, DROP, ROLL, GONE }

    var x = startX; private set
    var y = startY; private set
    var rot = 0f; private set
    var sx = 1f; private set
    var sy = 1f; private set
    var phase = Phase.HANG; private set
    /** How many times it has hit the floor. */
    var bounces = 0; private set
    private var vy = 0f
    private var vx = 0f
    private var squash = 0f

    /** While hanging: where the claw holds it. */
    fun hang(x: Float, y: Float, rotDeg: Float) { if (phase == Phase.HANG) { this.x = x; this.y = y; rot = rotDeg } }

    /** The prongs let go. */
    fun drop() { if (phase == Phase.HANG) { phase = Phase.DROP; vy = 0f } }

    fun step(dtMs: Float) {
        when (phase) {
            Phase.HANG, Phase.GONE -> return
            Phase.DROP -> {
                vy += dtMs * G
                y += vy * dtMs
                val floor = floorY - R
                if (y > floor) {
                    y = floor; squash = min(0.26f, vy * 0.32f); vy *= -0.45f; bounces++
                    if (bounces >= 3) { phase = Phase.ROLL; vx = 0.22f }
                }
            }
            Phase.ROLL -> {
                vx += dtMs * 0.0006f
                x += vx * dtMs
                rot += vx * dtMs / R * 57.3f
                if (x - R > screenW + 10f) phase = Phase.GONE
            }
        }
        // the hanging tilt settles out as it falls
        if (phase == Phase.DROP) rot *= 0.0001f.pow(dtMs / 600f)
        squash *= 0.0001f.pow(dtMs / 260f)
        sx = 1f + squash * 0.8f; sy = 1f - squash
    }

    companion object {
        const val R = 45f        // half its height (mockup 84 x 90)
        const val W = 84f
        const val H = 90f
        const val G = 0.0042f
    }
}
