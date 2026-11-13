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
    /** How far a held thing has been lifted (px, <= 0 is up) and its fall speed. */
    var carryDy = 0f; private set
    private var t = 0f
    private var base = 0f
    private var vy = 0f
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
                    m.box?.let { anchorX = (it.x0 + it.x1) / 2f; anchorY = (it.y0 + it.y1) / 2f; halfW = max(3f, it.w / 2f) }
                    motion = 0f
                    grip()
                } else if (tipY > screenH - 30f) {
                    result = Result(win = false, p = 0f, target = null, hitRow = null)
                    grip()
                }
            }
            St.GRIP -> {
                open = max(0f, 1f - t / 380f)
                val r = result!!
                val tg = r.target
                if (tg != null) targets.firstOrNull { sameThing(tg, it) }?.box?.let {
                    val d = hypot((it.x0 + it.x1) / 2f - anchorX, (it.y0 + it.y1) / 2f - anchorY) / halfW
                    motion = max(motion, d)
                }
                if (t > 560f) {
                    if (tg != null) {
                        val p = r.p * (1f - MOTION_CUT * min(1f, motion))
                        result = r.copy(win = rnd() < p, p = p)
                    }
                    st = St.UP; base = y; carryDy = 0f; outMs = 0f
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
                    if (outMs > SLIP_MS) {
                        r = r.copy(win = false, slipped = true); result = r
                        st = St.FALL; vy = 0f; open = 0.8f
                        return
                    }
                }
                // a miss slips after 70 px, or halfway up when the lift is short
                if (r.target != null && !r.win && base - y > min(70f, (base - restY) * 0.5f)) { st = St.FALL; vy = 0f; open = 0.8f }
                else if (y <= restY) st = if (r.win) St.CARRY else St.DONE
            }
            St.FALL -> {
                vy += dtMs * 0.0045f
                carryDy = min(0f, carryDy + vy * dtMs)
                y = max(restY, y - dtMs * UP_SPEED)
                if (carryDy >= 0f) st = St.DONE
            }
        }
    }

    /** The capsule took the catch away (CARRY -> DONE). */
    fun handedOff() { if (st == St.CARRY) { st = St.DONE; open = 1f } }

    /** Straight back to aiming: no result screen, no button. */
    fun next() {
        st = St.AIM; open = 1f; y = restY; result = null; carryDy = 0f; t = 0f
    }

    private fun grip() { st = St.GRIP; t = 0f }

    companion object {
        const val DOWN_SPEED = 0.26f   // px/ms
        const val UP_SPEED = 0.15f
        const val TIP = 44f
        const val MOTION_CUT = 0.5f    // moved half its width (or more) while gripping -> chance halved
        const val SLIP_MS = 200f
    }
}
