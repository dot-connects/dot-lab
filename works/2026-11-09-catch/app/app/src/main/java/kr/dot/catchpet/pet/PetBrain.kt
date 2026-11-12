package kr.dot.catchpet.pet

import kotlin.math.max
import kotlin.math.min

/**
 * The pet's mind (spec 5, mockups/pet-moves.html). Pure Kotlin, units px and ms. The pet lives in one of four
 * places: the bottom row (standing on floorY), the left or right screen edge, or behind the icons (live wallpaper).
 * It picks small acts on its own and reacts to touch: tap = smile, double tap = dance, drag = dangle, drop = stick
 * to the nearest place. It sleeps where it is after a long quiet spell, or at night / on the charger once untouched
 * for a while (a tap always wakes it, review R5/R7).
 * Position (x, y) is the pet's foot point: on the floor and behind the icons the middle of its bottom edge, on a wall
 * the point where its feet touch the wall.
 */
class PetBrain(
    zone: Zone,
    val w: Float,
    val h: Float,
    val floorY: Float,
    val size: Float = 48f,
    private val rnd: () -> Float,
) {
    enum class Zone { FLOOR, LEFT, RIGHT, BEHIND }
    enum class Act { IDLE, WALK, THINK, SMILE, DANCE, SLEEP, CLIMB, HANG, FALL }

    var zone = zone; private set
    var act = Act.IDLE; private set
    var x = w * 0.75f; private set
    var y = floorY; private set
    /** +1 = right (floor) / down (wall). */
    var dir = 1; private set
    private var t = 0f
    private var len = 3000f
    private var vy = 0f

    init { place(zone) }

    val topLimit get() = h * 0.2f
    val bottomLimit get() = floorY - size

    /** Puts the pet on a place and calms it down. */
    fun place(z: Zone) {
        zone = z
        when (z) {
            Zone.FLOOR -> { y = floorY; x = x.coerceIn(size / 2, w - size / 2) }
            Zone.BEHIND -> { y = floorY - size * 3; x = x.coerceIn(size / 2, w - size / 2) }
            Zone.LEFT -> { x = 0f; y = y.coerceIn(topLimit, bottomLimit) }
            Zone.RIGHT -> { x = w; y = y.coerceIn(topLimit, bottomLimit) }
        }
        start(Act.IDLE)
    }

    private val onWall get() = zone == Zone.LEFT || zone == Zone.RIGHT

    private fun start(a: Act, ms: Float = 2500f + rnd() * 3000f) {
        act = a; t = 0f; len = ms
        if (a == Act.WALK || a == Act.CLIMB) dir = if (rnd() < 0.5f) -1 else 1
    }

    private fun shouldSleep(sinceTouchMs: Float, night: Boolean, charging: Boolean) =
        sinceTouchMs >= QUIET_MS || ((night || charging) && sinceTouchMs >= SETTLE_MS)

    private fun pickNext() {
        val pool = when {
            onWall -> listOf(Act.IDLE, Act.CLIMB, Act.CLIMB)
            zone == Zone.BEHIND -> listOf(Act.IDLE, Act.WALK, Act.WALK)
            else -> listOf(Act.IDLE, Act.WALK, Act.THINK, Act.WALK)
        }
        start(pool[min(pool.size - 1, (rnd() * pool.size).toInt())])
    }

    fun step(dtMs: Float, sinceTouchMs: Float, night: Boolean, charging: Boolean) {
        t += dtMs
        when (act) {
            Act.HANG -> return
            Act.FALL -> {
                vy += dtMs * GRAVITY
                y = min(floorY, y + vy * dtMs)
                if (y >= floorY) { vy = 0f; place(Zone.FLOOR) }
                return
            }
            Act.SLEEP -> return   // until touched
            Act.SMILE, Act.DANCE -> if (t < len) return
            Act.WALK -> {
                x += dir * WALK_SPEED * dtMs
                if (x < size / 2 || x > w - size / 2) { dir = -dir; x = x.coerceIn(size / 2, w - size / 2) }
            }
            Act.CLIMB -> {
                y += dir * CLIMB_SPEED * dtMs
                if (y < topLimit || y > bottomLimit) { dir = -dir; y = y.coerceIn(topLimit, bottomLimit) }
            }
            Act.IDLE, Act.THINK -> {}
        }
        if (t >= len) {
            if (shouldSleep(sinceTouchMs, night, charging)) start(Act.SLEEP, Float.MAX_VALUE) else pickNext()
        }
    }

    fun tap() { start(Act.SMILE, 1400f) }

    fun doubleTap() { start(Act.DANCE, 3000f) }

    fun grab(px: Float, py: Float) { act = Act.HANG; t = 0f; x = px; y = py }

    fun drag(px: Float, py: Float) { if (act == Act.HANG) { x = px; y = py } }

    /** Let go at (px, py): stick to the nearest edge, or fall to the floor (behind the icons stays behind). */
    fun drop(px: Float, py: Float) {
        x = px; y = py
        when {
            px < w * 0.18f -> place(Zone.LEFT)
            px > w * 0.82f -> place(Zone.RIGHT)
            zone == Zone.BEHIND -> place(Zone.BEHIND)
            else -> { zone = Zone.FLOOR; act = Act.FALL; vy = 0f; t = 0f }
        }
    }

    /** Which of the six pet pictures to draw now. */
    fun frame(tMs: Float): String = when (act) {
        Act.IDLE, Act.THINK, Act.HANG, Act.FALL -> "front"
        Act.SMILE -> "smile"
        Act.DANCE -> if ((tMs / 360f).toInt() % 2 == 0) "front" else "smile"
        Act.WALK, Act.CLIMB -> if ((tMs / 170f).toInt() % 2 == 0) "walkA" else "walkB"
        Act.SLEEP -> if (onWall) "curl" else "sleep"
    }

    /** Drawing rotation: feet into the wall. */
    fun rotationDeg(): Float = when (zone) { Zone.LEFT -> 90f; Zone.RIGHT -> -90f; else -> 0f }

    /** Mirror the picture: walking left on the floor, or climbing the "wrong" way on a wall. */
    fun flip(): Boolean = when (zone) {
        Zone.LEFT -> dir > 0
        Zone.RIGHT -> dir < 0
        else -> dir < 0
    }

    companion object {
        const val WALK_SPEED = 0.022f    // px/ms
        const val CLIMB_SPEED = 0.018f
        const val GRAVITY = 0.003f       // px/ms²
        const val QUIET_MS = 180_000f    // untouched this long -> sleep
        const val SETTLE_MS = 30_000f    // at night / charging, sleep once untouched this long
    }
}
