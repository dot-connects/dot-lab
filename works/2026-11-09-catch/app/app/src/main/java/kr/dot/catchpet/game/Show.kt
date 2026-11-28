package kr.dot.catchpet.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The felt mockup's motion kit (mockups/next7v4/core.js): easings, the damped pendulum and the capsule's closing
 * timeline, as pure functions of time so every frame is computed from the clock, never stepped by hand (operator 11-28
 * "뚝뚝 끊긴다"). Units: ms, dp, degrees.
 */
object Ease {
    fun inQ(t: Float) = t * t
    fun outQ(t: Float) = 1f - (1f - t) * (1f - t)
    fun inC(t: Float) = t * t * t
    fun outC(t: Float) = 1f - (1f - t).pow(3)
    fun sine(t: Float) = 0.5f - 0.5f * cos(PI.toFloat() * t)
    fun io(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    fun clamp01(t: Float) = t.coerceIn(0f, 1f)
}

/** Damped pendulum offset (degrees) [t] ms after a jolt of size [a]. */
fun pend(a: Float, t: Float, w: Float = 0.011f, tau: Float = 520f): Float = a * exp(-t / tau) * sin(t * w)

/**
 * The open capsule pops in under the claw, the catch is put inside over [fillMs], then the lid swings shut over it
 * with a snap and a squash (core.js closeCapsule). t = ms since it appeared.
 */
class CapClose(val fillMs: Float) {
    val fillAt = 150f
    val lidAt = fillAt + fillMs + 60f
    val lidEnd = lidAt + 190f
    val end = lidEnd + 260f

    /** Pop-in: (scale, dy, alpha) of both halves, 0..240 ms (.55 -> 1.05 at 70 % -> 1). */
    fun pop(t: Float): Triple<Float, Float, Float> {
        if (t >= 240f) return Triple(1f, 0f, 1f)
        val e = Ease.outQ(Ease.clamp01(t / 240f))
        return if (e < 0.7f) { val k = e / 0.7f; Triple(0.55f + 0.5f * k, 18f - 20f * k, k) }
        else { val k = (e - 0.7f) / 0.3f; Triple(1.05f - 0.05f * k, -2f + 2f * k, 1f) }
    }

    /** 0..1 progress of putting the catch in (raw, the caller eases it). */
    fun fill(t: Float) = Ease.clamp01((t - fillAt) / fillMs)

    /** The lid's angle on its hinge: open, "탁" shut (ease-in), then a small wobble. */
    fun lid(t: Float): Float = when {
        t < lidAt -> LID_OPEN
        t < lidEnd -> LID_OPEN * (1f - Ease.inQ((t - lidAt) / 190f))
        // it swings on past shut by 5 degrees and back (the mockup jumped to -5; here it stays continuous)
        t < lidEnd + 240f -> {
            val e = Ease.outQ((t - lidEnd) / 240f)
            when {
                e < 0.25f -> -5f * e / 0.25f
                e < 0.6f -> -5f + 7f * (e - 0.25f) / 0.35f
                else -> 2f * (1f - (e - 0.6f) / 0.4f)
            }
        }
        else -> 0f
    }

    /** Squash after the snap: sx (sy = 2 - sx). */
    fun squash(t: Float): Float {
        if (t < lidEnd || t >= end) return 1f
        val r = (t - lidEnd) / 260f
        return 1f + 0.17f * exp(-r * 5f) * cos(r * 13f)
    }

    companion object { const val LID_OPEN = 46f }
}

/**
 * A slipped real catch (camera mode): the capsule shudders, slides, pops open, and the little one hops back onto its
 * body and melts in (screens.js slip). t = ms since the claw stopped; times are from the mockup.
 */
object SlipShow {
    const val SHUDDER = 520f
    const val SLIDE = 160f
    const val POP = SHUDDER + SLIDE
    const val HOP = 640f
    const val END = POP + HOP

    /** Shudder angle (deg) of the capsule, growing; 0 outside the shudder. */
    fun shudder(t: Float): Float {
        if (t <= 0f || t >= SHUDDER) return 0f
        val r = t / SHUDDER
        return sin(r * PI.toFloat() * 6f) * (3f + 13f * r)
    }

    /** Extra hang (dp) as the prongs give. */
    fun slide(t: Float) = 12f * Ease.inQ(Ease.clamp01((t - SHUDDER) / SLIDE))

    /** Hop from (sx, sy) at scale s0 to (lx, ly) at scale s1: (x, y, scale, alpha) for raw progress r 0..1. */
    fun hop(r: Float, sx: Float, sy: Float, s0: Float, lx: Float, ly: Float, s1: Float): FloatArray {
        val p = Ease.io(r)
        val a = if (r < 0.4f) 1f else 1f - Ease.inQ((r - 0.4f) / 0.6f)
        return floatArrayOf(sx + (lx - sx) * p, sy + (ly - sy) * p - sin(PI.toFloat() * r) * 56f, s0 + (s1 - s0) * p, a)
    }
}
