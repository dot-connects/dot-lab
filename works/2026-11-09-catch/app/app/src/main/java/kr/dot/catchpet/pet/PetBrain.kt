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
    private val baseFloor: Float,
    val size: Float = 48f,
    private val rnd: () -> Float,
) {
    enum class Zone { FLOOR, LEFT, RIGHT, BEHIND }
    enum class Act { IDLE, WALK, THINK, SMILE, PONDER, SLEEP, CLIMB, HANG, FALL, DIZZY, HOP, WIGGLE, WX }

    var zone = zone; private set
    var act = Act.IDLE; private set
    var x = w * 0.75f; private set
    var y = floorY; private set
    /** +1 = right (floor) / down (wall). */
    var dir = 1; private set
    /** ms in the current act (the drawing times its squash, wobble and stars by it). */
    val actMs get() = t
    private var t = 0f
    private var len = 3000f
    private var vy = 0f
    private var vx = 0f
    /** Let go near a wall: how far from it (x) the pet was, and ms since; the drawing slides it in over SLIDE_MS. */
    private var slideFrom = 0f
    private var slideMs = SLIDE_MS

    init { place(zone) }

    /** The stand line, just above the dock. */
    val floorY get() = baseFloor

    /**
     * Away from the home screen in "always" mode: keep off the floor and on the upper part of a wall, where a
     * keyboard never reaches (operator 11-15 plan A; the keyboard watch is gone, 11-16). [movedUp]: we took it
     * there, so we bring it back down.
     */
    var upHigh = false; private set
    private var movedUp = false

    fun upHigh(on: Boolean) {
        if (on == upHigh) return
        upHigh = on
        val asleep = act == Act.SLEEP
        if (on) { if (act != Act.HANG && act != Act.FALL) goUp() }
        else if (movedUp) {
            movedUp = false
            if (onWall && act != Act.HANG && act != Act.FALL) { x = if (zone == Zone.LEFT) size else w - size; place(Zone.FLOOR) }
        }
        if (asleep && act != Act.SLEEP) start(Act.SLEEP, Float.MAX_VALUE)   // switching apps doesn't wake it
        if (on && onWall) y = y.coerceIn(topLimit, max(topLimit, bottomLimit))
    }

    /** Floor (or behind the icons) -> the nearer wall, in the upper half. */
    private fun goUp() {
        if (onWall) return
        y = min(y, UP_SHARE * h)
        place(if (x < w / 2) Zone.LEFT else Zone.RIGHT)
        movedUp = true
    }

    val topLimit get() = h * 0.2f
    val bottomLimit get() = if (upHigh) min(floorY - size, UP_SHARE * h - size / 2) else floorY - size

    /** Where the drawing puts the pet across the wall while it slides in (0 once it is there). */
    fun slideOffset(): Float {
        if (!onWall || slideMs >= SLIDE_MS) return 0f
        val k = 1f - slideMs / SLIDE_MS
        return slideFrom * k * k
    }

    /** Puts the pet on a place and calms it down. */
    fun place(z: Zone) {
        zone = z
        when (z) {
            Zone.FLOOR -> { y = floorY; x = x.coerceIn(size / 2, w - size / 2) }
            Zone.BEHIND -> { y = floorY - size * 3; x = x.coerceIn(size / 2, w - size / 2) }
            Zone.LEFT -> { x = 0f; y = y.coerceIn(topLimit, max(topLimit, bottomLimit)) }
            Zone.RIGHT -> { x = w; y = y.coerceIn(topLimit, max(topLimit, bottomLimit)) }
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
            onWall -> if (bottomLimit - topLimit < 4f) listOf(Act.IDLE) else listOf(Act.IDLE, Act.CLIMB, Act.CLIMB)
            zone == Zone.BEHIND -> listOf(Act.IDLE, Act.WALK, Act.WALK)
            else -> listOf(Act.IDLE, Act.WALK, Act.THINK, Act.WALK)
        }
        start(pool[min(pool.size - 1, (rnd() * pool.size).toInt())])
    }

    fun step(dtMs: Float, sinceTouchMs: Float, night: Boolean, charging: Boolean) {
        t += dtMs
        if (slideMs < SLIDE_MS) slideMs += dtMs
        when (act) {
            Act.HANG -> return
            Act.FALL -> {
                vy += dtMs * GRAVITY
                y = min(floorY, y + vy * dtMs)
                if (y < size) { y = size; vy = kotlin.math.abs(vy) * 0.5f }
                // thrown: across the screen, bouncing off the side edges (operator 11-15, Shimeji)
                x += vx * dtMs
                if (x < size / 2) { x = size / 2; vx = -vx * 0.55f }
                if (x > w - size / 2) { x = w - size / 2; vx = -vx * 0.55f }
                if (y >= floorY) { vy = 0f; vx = 0f; place(Zone.FLOOR); start(Act.DIZZY, DIZZY_MS) }   // dropped: lands dizzy (operator 11-15)
                return
            }
            Act.SLEEP -> return   // until touched
            Act.SMILE, Act.HOP, Act.WIGGLE, Act.WX -> if (t < len) return
            Act.PONDER -> return   // until the cloud closes
            Act.DIZZY -> if (t < len) return else if (upHigh && !onWall) { goUp(); return }
            Act.WALK -> {
                x += dir * WALK_SPEED * dtMs
                if (x < size / 2 || x > w - size / 2) { dir = -dir; x = x.coerceIn(size / 2, w - size / 2) }
            }
            Act.CLIMB -> {
                y += dir * CLIMB_SPEED * dtMs
                if (y < topLimit || y > bottomLimit) { dir = -dir; y = y.coerceIn(topLimit, max(topLimit, bottomLimit)) }
            }
            Act.IDLE, Act.THINK -> {}
        }
        if (t >= len) {
            if (shouldSleep(sinceTouchMs, night, charging)) start(Act.SLEEP, Float.MAX_VALUE) else pickNext()
        }
    }

    /** A tap: a smile, a big hop or a wiggle, so it is not the same every time (operator 11-15). */
    fun tap() {
        if (act == Act.FALL || act == Act.HANG) return   // a tap on a flying pet left it walking in mid-air (review 11-16)
        val r = rnd()
        when {
            r < 0.45f -> start(Act.SMILE, 1400f)
            r < 0.75f -> start(Act.HOP, 650f)
            else -> start(Act.WIGGLE, 900f)
        }
    }

    /** The weather act plays (phase C): it stands still on its line for [ms]. */
    fun hold(ms: Float) { if (act != Act.FALL && act != Act.HANG) start(Act.WX, ms) }

    /** Its talk cloud was tapped away: a smile (not on a flying pet). */
    fun smile() { if (act != Act.FALL && act != Act.HANG) start(Act.SMILE, 1400f) }

    /** Woken to talk (an unlock or a plug-in, review 11-16). */
    fun wake() { if (act == Act.SLEEP) start(Act.IDLE) }

    /** The app cloud is open: it stops where it is and thinks until the cloud closes (operator 11-16). */
    fun ponder(on: Boolean) {
        if (!on) { if (act == Act.PONDER) start(Act.IDLE); return }
        if (act !in setOf(Act.HANG, Act.FALL, Act.DIZZY, Act.SLEEP, Act.PONDER)) start(Act.PONDER, Float.MAX_VALUE)
    }

    fun grab(px: Float, py: Float) { act = Act.HANG; t = 0f; x = px; y = py }

    fun drag(px: Float, py: Float) { if (act == Act.HANG) { x = px; y = py } }

    /** Let go at (px, py): stick to the nearest edge, or fall to the floor (behind the icons stays behind). */
    fun drop(px: Float, py: Float) {
        x = px; y = py
        when {
            px < w * 0.18f -> { place(Zone.LEFT); slideIn(px - size / 2); movedUp = false }
            px > w * 0.82f -> { place(Zone.RIGHT); slideIn(px - w + size / 2); movedUp = false }
            upHigh -> { place(if (px < w / 2) Zone.LEFT else Zone.RIGHT); slideIn(if (px < w / 2) px - size / 2 else px - w + size / 2); movedUp = true }
            zone == Zone.BEHIND -> place(Zone.BEHIND)
            else -> { zone = Zone.FLOOR; act = Act.FALL; vy = 0f; vx = 0f; t = 0f }
        }
    }

    private fun slideIn(dx: Float) { slideFrom = dx; slideMs = 0f }

    /** Let go while moving fast (px/ms): it flies, bounces off the side edges and lands dizzy. */
    fun fling(px: Float, py: Float, vx: Float, vy: Float) {
        x = px; y = py
        zone = Zone.FLOOR; act = Act.FALL; t = 0f; movedUp = false
        this.vx = vx.coerceIn(-MAX_THROW, MAX_THROW); this.vy = vy.coerceIn(-MAX_THROW, MAX_THROW)
    }

    /** Which of the six pet pictures to draw now. */
    fun frame(tMs: Float): String = when (act) {
        Act.IDLE, Act.THINK -> "front"
        Act.HANG -> if ((tMs / 160f).toInt() % 2 == 0) "holdA" else "holdB"     // flailing while held
        Act.FALL -> "holdA"
        Act.DIZZY -> if (t < 700f) "fall" else "dazed"                         // flat on the belly, then sits up dazed
        Act.SMILE, Act.HOP -> "smile"
        Act.WIGGLE, Act.WX -> "front"
        Act.PONDER -> "front"
        Act.WALK, Act.CLIMB -> "walk${(tMs / 130f).toInt() % 4 + 1}"   // contact, passing, contact, passing
        Act.SLEEP -> if (onWall) "curl" else "sleep"
    }

    /**
     * A new brain for the same pet window (rotation, fold, another pet, another size): the same place at the same
     * share of the screen (operator 11-15; 11-16 "a new pet on the left wall came back on the right").
     */
    fun takeOver(o: PetBrain) {
        zone = o.zone; upHigh = o.upHigh; movedUp = o.movedUp
        x = o.x / o.w * w; y = o.y / o.h * h
        place(zone)
    }

    fun rotationDeg(): Float = when (zone) { Zone.LEFT -> 90f; Zone.RIGHT -> -90f; else -> 0f }

    /** Mirror the picture: walking left on the floor, or climbing the "wrong" way on a wall. */
    fun flip(): Boolean = when (zone) {
        Zone.LEFT -> dir < 0    // turned 90 deg: the picture's front points down the wall
        Zone.RIGHT -> dir > 0   // turned -90 deg: it points up
        else -> dir < 0
    }

    companion object {
        const val DIZZY_MS = 1800f
        const val UP_SHARE = 0.6f       // away from home it stays on the top 60 % of a wall (operator 11-16)
        const val SLIDE_MS = 120f       // let go near a wall: slides in (operator 11-15)
        const val MAX_THROW = 3f        // px/ms
        const val THROW_SPEED = 1.1f    // px/ms at let-go: faster than this is a throw
        const val WALK_SPEED = 0.022f    // px/ms
        const val CLIMB_SPEED = 0.018f
        const val GRAVITY = 0.003f       // px/ms²
        const val QUIET_MS = 180_000f    // untouched this long -> sleep
        const val SETTLE_MS = 30_000f    // at night / charging, sleep once untouched this long
    }
}
