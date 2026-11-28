package kr.dot.catchpet.game

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Grab chance at mask column cx, grab row `row`: 0.80 at the body's centre .. 0.35 at its edge, 0 off the body (operator 11-14: was 0.55..0.10, too low). */
fun chance(m: Mask, cx: Int, row: Int): Float {
    val span = m.spanInBand(row, 18) ?: return 0f
    if (cx < span.first - 2 || cx > span.last + 2) return 0f
    return 0.80f - 0.45f * offCentre(cx, span)
}

/** The same thing in another frame: a critter by its id, a real one by kind family (person / animal). */
fun sameThing(a: Mask, b: Mask): Boolean =
    if (a.kind == Mask.Kind.CRITTER) b.critterId == a.critterId
    else b.kind != Mask.Kind.CRITTER && (a.kind == Mask.Kind.PERSON) == (b.kind == Mask.Kind.PERSON)

/**
 * The claw (mockups/claw-endings.html). Screen px, ms. The claw hub hangs from the rail; its tips are 44 px below.
 * One round: AIM -> DOWN -> GRIP -> UP -> (FALL | CARRY) -> DONE, then next() goes straight back to AIM.
 * The tips touching something picks the target and its chance; the roll comes at the end of GRIP, the chance cut by
 * up to half when the target moved while the claw closed (camera shake counts). A won target that leaves the claw's
 * column on the way up for 200 ms slips out (operator 11-13: shaking the phone must not still win).
 *
 * Motion (felt mockup v5, mockups/next7v4/core.js, operator 11-28 "천천히 묵직하게"): the drop is planned to where the
 * tips will touch now (sine ease, 650 ms + 2.4 ms per px, at most 1.7 s), presses PRESS px in and settles back while
 * the prongs close. A lost roll rises (easing out) to dropLift and the claw waits there ([fallHold]) while the game
 * shows the slip, then rises empty. The game can [hold] the lift while the capsule closes round the catch.
 * Every position is a function of the state's clock, so no frame jumps.
 */
class Claw(
    val screenW: Float,
    val screenH: Float,
    val railY: Float,
    private val rnd: () -> Float,
) {
    enum class St { AIM, DOWN, GRIP, UP, FALL, CARRY, DONE }

    /** win, its chance, the thing grabbed (null = grabbed air), the mask row touched, and whether a win slipped out. */
    data class Result(val win: Boolean, val p: Float, val target: Mask?, val hitRow: Int?, val slipped: Boolean = false)

    val restY get() = railY + 28f
    var st = St.AIM; private set
    var x = HOME_X; private set
    private var aimed = false
    var y = restY; private set
    var open = 1f; private set
    var result: Result? = null; private set
    /** How far a held thing has been lifted (px, <= 0 is up). */
    var carryDy = 0f; private set
    /** ms in the current state. */
    var t = 0f; private set
    /** 0..1 while a won thing is out from under the claw (it slips out at 1), else 0. */
    var slip01 = 0f; private set
    /** The grabbed body's size on screen (px), 0 when grabbing air. */
    var bodyH = 0f; private set
    var bodyW = 0f; private set
    /** How far the lift goes before a lost roll falls out (px). */
    var dropLift = 0f; private set
    /** The game holds the lift while the capsule closes round the catch (UP waits, its clock too). */
    var hold = false
    /** FALL: ms the claw stays where the catch fell out before it rises empty (the game's slip show). */
    var fallHold = 0f
    /** DOWN: share of the planned descent done (0..1), for the sway. */
    val downK get() = if (st == St.DOWN && downMs > 0f) (t / downMs).coerceIn(0f, 1f) else 0f
    private var base = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var halfW = 1f
    private var motion = 0f
    private var outMs = 0f
    private var goal = Float.NaN    // hub y the eased descent heads for
    private var downMs = 0f
    private var stopY = Float.NaN   // hub y where it stops once it has touched (contact + PRESS)
    private var fallY = 0f
    /** ms the current empty rise takes (air, or after a fall), by its distance. */
    var riseMs = RISE_EMPTY_MS; private set

    fun aim(x01: Float) { if (st == St.AIM) { x = x01.coerceIn(0.06f, 0.94f); aimed = true } }

    fun release() { if (st == St.AIM) { st = St.DOWN; t = 0f; goal = Float.NaN; stopY = Float.NaN } }

    /** What the tips (or the hub's underside) touch with the hub at [hubY]: the topmost thing and the row touched. */
    private fun touch(hubY: Float, targets: List<Mask>, toMask: (Float, Float) -> Pair<Int, Int>): Pair<Mask, Int>? {
        val (mx, my) = toMask(x * screenW, hubY + TIP)
        // the hub's underside stops on a body's top too (a side hit must not sink the hub into it)
        // (the body under the hub's own width, not its bounding box: review 11-15)
        val hubRow = toMask(x * screenW, hubY + HUB_BOTTOM).second
        val hubHalf = max(2, mx - toMask(x * screenW - HUB_HALF, hubY).first)
        var best: Pair<Mask, Int>? = null
        for (m in targets) {
            val col = m.firstRowInColumn(mx)
            val under = m.firstRowInColumn(mx, hubHalf)
            val row = when { col != null && my >= col -> col; under != null && hubRow >= under -> under; else -> continue }
            if (best == null || row < best.second) best = m to row
        }
        return best
    }

    /** The lowest the hub goes: tips 30 px above the bottom (grabbing air). */
    private val floorHub get() = screenH - 30f - TIP

    /**
     * targets: everything grabbable this frame; toMask maps a screen point to mask (col, row).
     * Masks share one grid (the segmenter's), critters included.
     */
    fun step(dtMs: Float, targets: List<Mask>, toMask: (Float, Float) -> Pair<Int, Int>) {
        t += dtMs
        when (st) {
            // untouched, the claw rides back to its home corner like a real crane
            St.AIM -> if (!aimed) x = max(HOME_X, x - dtMs * HOME_SPEED)
            St.CARRY, St.DONE -> {}
            St.DOWN -> {
                if (goal.isNaN()) {
                    // plan the drop to where it would touch now; the touch itself is still tested every frame
                    var hy = restY
                    while (hy < floorHub && touch(hy, targets, toMask) == null) hy += 2f
                    goal = if (hy >= floorHub) floorHub else min(floorHub, hy + PRESS)
                    downMs = min(DOWN_MAX_MS, DOWN_BASE_MS + (goal - restY) * DOWN_PER_PX)
                }
                // past the plan (the thing moved away) it keeps going down slowly
                y = if (t <= downMs) restY + (goal - restY) * Ease.sine(t / downMs) else min(floorHub, y + dtMs * DOWN_SPEED)
                if (!stopY.isNaN()) { if (y >= stopY || t >= downMs) { y = min(y, stopY); grip() }; return }
                val best = touch(y, targets, toMask)
                if (best != null) {
                    val m = best.first
                    result = Result(win = false, p = chance(m, toMask(x * screenW, y + TIP).first, best.second), target = m, hitRow = best.second)
                    m.box?.let {
                        anchorX = (it.x0 + it.x1) / 2f; anchorY = (it.y0 + it.y1) / 2f; halfW = max(3f, it.w / 2f)
                        // grid cells per screen px, to size the body on screen
                        val (gx0, gy0) = toMask(0f, 0f); val (gx1, gy1) = toMask(100f, 100f)
                        val cx = max(1e-3f, (gx1 - gx0) / 100f); val cy = max(1e-3f, (gy1 - gy0) / 100f)
                        bodyW = it.w / cx; bodyH = (it.y1 - it.y0 + 1) / cy
                    }
                    motion = 0f
                    stopY = min(y + PRESS, max(y, goal))
                    if (y >= stopY || t >= downMs) grip()
                } else if (y + TIP >= screenH - 30.5f) {
                    result = Result(win = false, p = 0f, target = null, hitRow = null)
                    bodyW = 0f; bodyH = 0f; stopY = y
                    grip()
                }
            }
            St.GRIP -> {
                // settle back a little from the press, then the prongs close
                y = stopY - SETTLE * Ease.outQ((t / SETTLE_MS).coerceIn(0f, 1f))
                open = 1f - Ease.outC(((t - SETTLE_MS) / CLOSE_MS).coerceIn(0f, 1f))
                val r = result!!
                val tg = r.target
                if (tg != null) targets.firstOrNull { sameThing(tg, it) }?.box?.let {
                    val d = hypot((it.x0 + it.x1) / 2f - anchorX, (it.y0 + it.y1) / 2f - anchorY) / halfW
                    motion = max(motion, d)
                }
                if (t > GRIP_MS) {
                    st = St.UP; base = y; carryDy = 0f; outMs = 0f; slip01 = 0f; t = 0f
                    if (tg != null) {
                        val p = r.p * (1f - MOTION_CUT * min(1f, motion))
                        val roll = rnd()
                        result = r.copy(win = roll < p, p = p)
                        dropLift = dropLiftFor(roll, p, base - restY)
                    }
                }
            }
            St.UP -> {
                if (hold) { t = 0f; return }
                var r = result!!
                val tg = r.target
                val lost = tg != null && !r.win
                // a lost roll rises (sine: no jerk off the floor) to where it falls out; a win or air eases all the way up
                val end = if (lost) base - dropLift else restY
                if (tg == null && t <= dtMs) riseMs = riseEmptyMs(base - restY)
                val ms = when { tg == null -> riseMs; lost -> LOST_UP_MS; else -> upMs(base - restY) }
                val k = (t / ms).coerceIn(0f, 1f)
                y = base + (end - base) * (if (lost) Ease.sine(k) else Ease.io(k))
                carryDy = y - base
                if (r.win && tg != null) {
                    val mx = toMask(x * screenW, y).first
                    val under = targets.any { o -> sameThing(tg, o) && o.box.let { b -> b != null && mx in b.x0 - 2..b.x1 + 2 } }
                    outMs = if (under) 0f else outMs + dtMs
                    slip01 = (outMs / SLIP_MS).coerceIn(0f, 1f)
                    if (outMs > SLIP_MS) {
                        r = r.copy(win = false, slipped = true); result = r
                        fall()
                        return
                    }
                }
                if (k >= 1f) { if (lost) fall() else st = if (r.win) St.CARRY else St.DONE }
            }
            St.FALL -> {
                // the claw waits while the game shows the slip, then rises empty
                if (t < fallHold) return
                val k = ((t - fallHold) / riseMs).coerceIn(0f, 1f)
                y = fallY + (restY - fallY) * Ease.io(k)
                if (k >= 1f) st = St.DONE
            }
        }
    }

    private fun fall() { st = St.FALL; fallY = y; slip01 = 0f; t = 0f; riseMs = riseEmptyMs(fallY - restY) }

    /** The capsule took the catch away (CARRY -> DONE). */
    fun handedOff() { if (st == St.CARRY) { st = St.DONE; open = 1f } }

    /** Straight back to aiming: no result screen, no button. */
    fun next() {
        st = St.AIM; aimed = false; open = 1f; y = restY; result = null; carryDy = 0f; t = 0f; slip01 = 0f
        hold = false; fallHold = 0f; goal = Float.NaN; stopY = Float.NaN
    }

    private fun grip() { st = St.GRIP; t = 0f }

    companion object {
        const val DOWN_SPEED = 0.26f   // px/ms, only past the planned descent (the thing moved away)
        const val DOWN_BASE_MS = 650f
        const val DOWN_PER_PX = 2.4f
        const val DOWN_MAX_MS = 1700f
        const val PRESS = 19f          // the tips go 14 px into the body plus 5 they settle back (mockup)
        const val SETTLE = 5f
        const val SETTLE_MS = 200f
        const val CLOSE_MS = 120f
        const val GRIP_MS = 560f
        const val RISE_EMPTY_MS = 600f
        const val LOST_UP_MS = 600f
        const val TIP = 44f
        const val HOME_X = 0.1f      // rest at the left, back there after every round (operator 11-14)
        const val HOME_SPEED = 0.0008f   // screen widths per ms (~1 s across)
        const val HUB_HALF = 15f      // the hub is 30 px wide
        const val HUB_BOTTOM = 10f   // the hub's underside below its centre
        const val MOTION_CUT = 0.5f    // moved half its width (or more) while gripping -> chance halved
        const val SLIP_MS = 200f
        const val SLIP_LEAD = 37.5f    // the least lift before a lost roll falls out (px)

        /** An empty claw's rise: 600 ms (mockup) for short ones, longer for far ones so it never whips up. */
        fun riseEmptyMs(dist: Float) = (350f + 1.2f * dist).coerceIn(RISE_EMPTY_MS, 1000f)

        /** A win's lift: about 820 ms for the mockup's 280 px, brisker for short ones. */
        fun upMs(lift: Float) = (400f + 1.5f * lift).coerceIn(500f, 1100f)

        /**
         * A lost roll falls out after this much lift: the nearer the roll came to winning, the higher (90 % of the
         * lift for a near miss .. 25 % for a clear one), so "almost" looks like almost. At least SLIP_LEAD px.
         */
        fun dropLiftFor(roll: Float, p: Float, lift: Float): Float {
            if (lift <= 0f) return 0f
            val margin = if (p >= 1f) 1f else ((roll - p) / (1f - p)).coerceIn(0f, 1f)
            return max(min(SLIP_LEAD, lift * 0.5f), lift * (0.9f + (0.25f - 0.9f) * margin))
        }
    }
}
