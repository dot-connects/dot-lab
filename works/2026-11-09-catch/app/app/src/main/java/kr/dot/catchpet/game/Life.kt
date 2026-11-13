package kr.dot.catchpet.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a thing is drawn this frame, relative to its resting picture rect of height H.
 * dx, dy: offset as a share of H; rot: degrees; sx, sy: scale; pivot: where rot/scale pivot, 0 = top .. 1 = feet;
 * look: show the neighbouring baked direction (dir±1) instead of its own; daz: degrees it has shuffled sideways.
 */
data class Pose(
    val dx: Float = 0f, val dy: Float = 0f, val rot: Float = 0f, val sx: Float = 1f, val sy: Float = 1f,
    val pivot: Float = 1f, val look: Int = 0, val daz: Float = 0f,
) {
    companion object { val REST = Pose() }
}

/**
 * Fur-critter life (operator 11-13 "가만히 있을 때도 좀 움직였으면", Fable design note): idle breathing, hops, looks and a
 * rare half-step; the landing after a dropped catch. Pure functions of a per-critter clock (ms) and a seed 0..1, so six
 * critters cost nothing to keep alive and nothing has to be stored per frame.
 */
object Life {
    private fun frac(x: Float) = x - floor(x)
    private fun arc(k: Float) = 4f * k * (1f - k)   // 0 -> 1 -> 0 parabola for jumps
    private const val TAU = (2 * PI).toFloat()

    /** Idle: breathing squash, a hop every 4-9 s, a look aside every 7-11 s, a half step every 10-20 s. */
    fun idle(t: Float, seed: Float): Pose {
        val ph = seed * 1000f
        val br = sin(TAU * (t + ph) / (2200f + 1000f * seed))
        var sy = 1f + 0.025f * br
        var sx = 1f - 0.0125f * br
        var dy = 0f
        val every = 4000f + 5000f * frac(seed * 7.3f)
        val u = (t + ph * 3f) % every
        when {
            u < 80f -> { val k = u / 80f; sy *= 1f - 0.08f * k; sx *= 1f + 0.04f * k }          // crouch
            u < 300f -> { val k = (u - 80f) / 220f; dy = -0.08f * arc(k); sy *= 1.04f; sx *= 0.98f }   // up
            u < 370f -> { val k = (u - 300f) / 70f; sy *= 0.90f + 0.10f * k; sx *= 1.05f - 0.05f * k } // land
        }
        val lookEvery = 7000f + 4000f * frac(seed * 3.1f)
        val look = if ((t + ph * 5f) % lookEvery < 900f) (if (frac(seed * 11f) > 0.5f) 1 else -1) else 0
        // half step: out to ±1.5 degrees over 800 ms with two little hops, back again one period later
        val per = 10000f + 10000f * frac(seed * 5.7f)
        val v = (t + ph * 7f) % (2 * per)
        val moving = if (v < per) v else v - per
        val k = (moving / 800f).coerceAtMost(1f)
        val ease = k * k * (3f - 2f * k)
        val side = if (frac(seed * 13f) > 0.5f) 1.5f else -1.5f
        val daz = side * (if (v < per) ease else 1f - ease)
        if (k < 1f) dy += -0.05f * abs(sin(TAU * k))
        return Pose(dy = dy, sx = sx, sy = sy, look = look, daz = daz)
    }

    const val LAND_MS = 1400f

    /** The landing after a drop, t ms since it touched the floor: squash, two bounces, a still beat, a shake, a hop. */
    fun land(t: Float): Pose = when {
        t < 90f -> { val k = t / 90f; Pose(sx = 1.2f - 0.2f * k, sy = 0.72f + 0.28f * k) }
        t < 350f -> Pose(dy = -0.12f * arc((t - 90f) / 260f))
        t < 440f -> Pose(dy = -0.015f * arc((t - 350f) / 90f))
        t < 690f -> Pose.REST
        t < 1140f -> { val k = (t - 690f) / 450f; Pose(rot = 10f * sin(TAU * 12f * (t - 690f) / 1000f) * (1f - k)) }
        t < LAND_MS -> Pose(dy = -0.08f * arc((t - 1140f) / 260f))
        else -> Pose.REST
    }

    /** Dust puff progress 0..1 for the first 350 ms of a landing, null after. */
    fun dust(t: Float): Float? = if (t in 0f..350f) t / 350f else null
}

/**
 * A caught thing hanging from the claw: a damped pendulum about the grip point (driven by how fast the thing moves
 * against the claw, i.e. the player turning the phone) plus, for critters, struggling in bursts.
 * Units: degrees, ms, dp.
 */
class Dangle(private val rnd: () -> Float) {
    var rot = 0f; private set
    private var w = 0f
    private var t = 0f
    private var burstAt = 0f
    private var restEnd = 0f
    private var prevRel = Float.NaN
    private var prevV = 0f

    fun reset() { rot = 0f; w = 0f; t = 0f; burstAt = 0f; restEnd = 0f; prevRel = Float.NaN; prevV = 0f }

    /** A jolt (deg/ms), e.g. the claw stopping at the top. */
    fun kick(dw: Float) { w += dw }

    /** rel: the thing's x minus the claw's x (dp); cap: max swing in degrees. */
    fun step(dt: Float, rel: Float, cap: Float) {
        if (dt <= 0f) return
        t += dt
        val v = if (prevRel.isNaN()) 0f else (rel - prevRel) / dt
        prevRel = rel
        w += -(v - prevV) * GAIN
        prevV = v
        val k = (2 * PI.toFloat() / PERIOD_MS).let { it * it }
        val c = 2f * ZETA * sqrt(k)
        w += (-k * rot - c * w) * dt
        rot += w * dt
        if (abs(rot) > cap) { rot = rot.coerceIn(-cap, cap); w *= -0.3f }
    }

    /**
     * Struggling (cute: bursts of 400 ms, then a 300-600 ms rest). amp scales it (tires on the way up, grows while
     * slipping). Returns (rotation deg, sideways share of H, squash share).
     */
    fun struggle(amp: Float): Triple<Float, Float, Float> {
        if (t >= restEnd) { burstAt = t; restEnd = t + BURST_MS + 300f + 300f * rnd() }
        val inBurst = t - burstAt < BURST_MS
        if (!inBurst || amp <= 0f) return Triple(0f, 0f, 0f)
        val s = (t - burstAt) / 1000f * 5f * 2 * PI.toFloat()   // 5 Hz
        val env = sin(PI.toFloat() * (t - burstAt) / BURST_MS)   // ease in and out of the burst
        return Triple(6f * amp * env * sin(s), 0.03f * amp * env * sin(s + 1f), 0.06f * amp * env * sin(s + 1.6f))
    }

    companion object {
        const val PERIOD_MS = 1100f
        const val ZETA = 0.12f
        const val GAIN = 0.25f        // deg/ms of swing per dp/ms change of the thing's speed against the claw
        const val BURST_MS = 400f
        const val CAP_CRITTER = 12f
        const val CAP_PHOTO = 5f
    }
}
