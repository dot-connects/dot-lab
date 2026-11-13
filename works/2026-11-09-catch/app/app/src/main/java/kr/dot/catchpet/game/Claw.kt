package kr.dot.catchpet.game

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Grab chance at mask column cx, grab row `row`: 0.55 at the body's centre .. 0.10 at its edge, 0 off the body (operator 11-13). */
fun chance(m: Mask, cx: Int, row: Int): Float {
    val span = m.spanInBand(row, 18) ?: return 0f
    if (cx < span.first - 2 || cx > span.last + 2) return 0f
    return 0.55f - 0.45f * offCentre(cx, span)
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
 * Feel (operator 11-13, Fable design note): on contact the claw digs into the body (DIG_MS) before the prongs close,
 * and the prongs stop at the body's width (minOpen) instead of closing on air. A lost roll slips out on the way up,
 * later the closer the roll was (dropLift), sliding for SLIP_LEAD px first (slip01) so the player sees it coming;
 * it then falls slowly (FALL_G).
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
    var x = 0.5f; private set
    var y = restY; private set
    var open = 1f; private set
    var result: Result? = null; private set
    /** How far a held thing has been lifted (px, <= 0 is up). */
    var carryDy = 0f; private set
    /** ms in the current state. */
    var t = 0f; private set
    /** 0..1 while a held thing is sliding out of the prongs (just before FALL), else 0. */
    var slip01 = 0f; private set
    /** Fall speed (px/ms) while FALLing. */
    var vy = 0f; private set
    /** The grabbed body's size on screen (px), 0 when grabbing air. */
    var bodyH = 0f; private set
    var bodyW = 0f; private set
    /** How far the lift goes before a lost roll falls out (px). */
    var dropLift = 0f; private set
    /** How far the prongs close on the grabbed body (0 = shut, for air). */
    var minOpen = 0f; private set
    private var base = 0f
    private var contactY = 0f
    private var dig = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var halfW = 1f
    private var motion = 0f
    private var outMs = 0f

    fun aim(x01: Float) { if (st == St.AIM) x = x01.coerceIn(0.06f, 0.94f) }

    fun release() { if (st == St.AIM) { st = St.DOWN; t = 0f } }

    /**
     * targets: everything grabbable this frame; toMask maps a screen point to mask (col, row).
     * Masks share one grid (the segmenter's), critters included.
     */
    fun step(dtMs: Float, targets: List<Mask>, toMask: (Float, Float) -> Pair<Int, Int>) {
        t += dtMs
        when (st) {
            St.AIM, St.CARRY, St.DONE -> {}
            St.DOWN -> {
                y += dtMs * DOWN_SPEED
                val tipY = y + TIP
                val (mx, my) = toMask(x * screenW, tipY)
                var best: Pair<Mask, Int>? = null
                for (m in targets) {
                    val row = m.firstRowInColumn(mx) ?: continue
                    if (best == null || row < best.second) best = m to row
                }
                if (best != null && my >= best.second) {
                    val m = best.first
                    result = Result(win = false, p = chance(m, mx, best.second), target = m, hitRow = best.second)
                    m.box?.let {
                        anchorX = (it.x0 + it.x1) / 2f; anchorY = (it.y0 + it.y1) / 2f; halfW = max(3f, it.w / 2f)
                        // grid cells per screen px, to size the body on screen
                        val (gx0, gy0) = toMask(0f, 0f); val (gx1, gy1) = toMask(100f, 100f)
                        val cx = max(1e-3f, (gx1 - gx0) / 100f); val cy = max(1e-3f, (gy1 - gy0) / 100f)
                        bodyW = it.w / cx; bodyH = (it.y1 - it.y0 + 1) / cy
                    }
                    dig = min(DIG_SHARE * bodyH, DIG_MAX)
                    minOpen = ((0.75f * bodyW / 2f - 8f) / 20f).coerceIn(0f, 0.6f)
                    contactY = y
                    motion = 0f
                    grip()
                } else if (tipY > screenH - 30f) {
                    result = Result(win = false, p = 0f, target = null, hitRow = null)
                    bodyW = 0f; bodyH = 0f; dig = 0f; minOpen = 0f; contactY = y
                    grip()
                }
            }
            St.GRIP -> {
                // dig in, then close (ease-in) down to the body's width
                if (t < DIG_MS) { val k = t / DIG_MS; y = contactY + dig * (1f - (1f - k) * (1f - k)) } else y = contactY + dig
                val k = ((t - DIG_MS) / CLOSE_MS).coerceIn(0f, 1f)
                open = 1f - (1f - minOpen) * k * k
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
                y = max(restY, y - dtMs * UP_SPEED)
                carryDy = y - base
                var r = result!!
                val tg = r.target
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
                if (tg != null && !r.win) {
                    val lifted = base - y
                    slip01 = ((lifted - (dropLift - SLIP_LEAD)) / SLIP_LEAD).coerceIn(0f, 1f)
                    open = minOpen + (0.8f - minOpen) * slip01
                    if (lifted >= dropLift) { fall(); return }
                }
                if (y <= restY) st = if (r.win) St.CARRY else St.DONE
            }
            St.FALL -> {
                vy += dtMs * FALL_G
                carryDy = min(0f, carryDy + vy * dtMs)
                y = max(restY, y - dtMs * UP_SPEED)
                if (carryDy >= 0f) st = St.DONE
            }
        }
    }

    private fun fall() { st = St.FALL; vy = 0f; open = 0.8f; slip01 = 0f; t = 0f }

    /** The capsule took the catch away (CARRY -> DONE). */
    fun handedOff() { if (st == St.CARRY) { st = St.DONE; open = 1f } }

    /** Straight back to aiming: no result screen, no button. */
    fun next() {
        st = St.AIM; open = 1f; y = restY; result = null; carryDy = 0f; t = 0f; slip01 = 0f; vy = 0f
    }

    private fun grip() { st = St.GRIP; t = 0f }

    companion object {
        const val DOWN_SPEED = 0.26f   // px/ms
        const val UP_SPEED = 0.15f
        const val TIP = 44f
        const val MOTION_CUT = 0.5f    // moved half its width (or more) while gripping -> chance halved
        const val SLIP_MS = 200f
        const val DIG_MS = 140f
        const val CLOSE_MS = 380f
        const val GRIP_MS = 560f
        const val DIG_SHARE = 0.3f     // dig into the body by 30 % of its height ...
        const val DIG_MAX = 26f        // ... at most 26 px
        const val SLIP_LEAD = 37.5f    // px of visible sliding before a lost roll falls out (250 ms of lift)
        const val FALL_G = 0.0014f     // px/ms², slow and floaty (was 0.0045)

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
