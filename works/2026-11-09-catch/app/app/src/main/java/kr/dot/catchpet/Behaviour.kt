package kr.dot.catchpet

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

enum class Move { BOUNCE, FLINCH, WALK, RUN, SLEEP, HIDE, DRAG }

/** What to draw now: window top-left in dp (hide goes partly off screen), wanted picture label, facing. */
data class Frame(val x: Float, val y: Float, val pose: String, val faceLeft: Boolean, val move: Move, val stateMs: Long)

/**
 * The floating pet's state machine. Pure Kotlin, units dp and ms. The pet's window top-left runs on the
 * rectangle [0, w-size] x [0, h-size] ("perimeter"), parameterised by t: bottom edge left->right, right edge
 * up, top edge right->left, left edge down. Corners count as bottom/top, so "on a side" means strictly inside
 * a side edge. Moving states never end on a side: if time runs out there, the same move continues.
 */
class Behaviour(unlocked: Collection<String>, w: Float, h: Float, seed: Long, val size: Float = 96f) {
    private val rnd = Random(seed)
    private val auto = listOf(Move.BOUNCE, Move.WALK, Move.RUN, Move.SLEEP, Move.HIDE)
        .filter { it.name.lowercase() in unlocked }.ifEmpty { listOf(Move.BOUNCE) }
    private var a = 0f
    private var b = 0f
    private val per get() = 2 * (a + b)

    var t = 0f; private set
    var move = Move.BOUNCE; private set
    var durationMs = 0L; private set
    var stateNo = 0; private set
    private var elapsedMs = 0L
    private var target = 0f
    private var faceLeft = false
    private var wasSleeping = false
    private var dragX = 0f
    private var dragY = 0f

    init {
        setSize(w, h)
        t = a / 2
        next(Move.BOUNCE)
    }

    private fun setSize(w: Float, h: Float) {
        a = (w - size).coerceAtLeast(1f)
        b = (h - size).coerceAtLeast(1f)
    }

    fun pos(t: Float = this.t): Pair<Float, Float> {
        val u = ((t % per) + per) % per
        return when {
            u <= a -> u to b
            u <= a + b -> a to b - (u - a)
            u <= 2 * a + b -> a - (u - a - b) to 0f
            else -> 0f to u - 2 * a - b
        }
    }

    fun edge(t: Float = this.t): String {
        val (x, y) = pos(t)
        return when {
            y >= b -> "bottom"
            y <= 0f -> "top"
            x >= a -> "right"
            else -> "left"
        }
    }

    private fun onSide() = edge() == "right" || edge() == "left"

    /** Nearest perimeter t for a top-left point. */
    fun project(x: Float, y: Float): Float {
        val cx = x.coerceIn(0f, a)
        val cy = y.coerceIn(0f, b)
        val d = listOf(b - cy, a - cx, cy, cx) // bottom, right, top, left
        return when (d.indexOf(d.min())) {
            0 -> cx
            1 -> a + (b - cy)
            2 -> a + b + (a - cx)
            else -> 2 * a + b + cy
        }
    }

    // Mostly stay on the edge you are on; now and then cross to the other one. Sides are only passed through
    // (at run speed), so time splits roughly bottom 60 / top 25 / side 15 (see out/s4/behaviour-sim.log).
    private fun pickTarget() {
        val toTop = when (edge()) {
            "bottom" -> rnd.nextFloat() < CROSS_UP
            "top" -> rnd.nextFloat() >= CROSS_DOWN
            else -> false
        }
        target = if (toTop) a + b + rnd.nextFloat() * a else rnd.nextFloat() * a
    }

    private fun next(forced: Move? = null) {
        val prev = move
        var m = forced ?: when {
            (prev == Move.WALK || prev == Move.RUN) && onSide() -> prev
            prev == Move.FLINCH && wasSleeping -> Move.BOUNCE
            else -> auto[rnd.nextInt(auto.size)]
        }
        if (forced == null && onSide() && m != Move.WALK && m != Move.RUN) {
            auto.firstOrNull { it == Move.WALK || it == Move.RUN }?.let { m = it }
        }
        if ((m == Move.WALK || m == Move.RUN) && (prev != m || abs(target - t) < 0.5f)) pickTarget()
        move = m
        durationMs = when (m) {
            Move.HIDE -> 3000L
            Move.FLINCH -> 400L
            else -> 3000L + rnd.nextLong(9001L)
        }
        elapsedMs = 0
        stateNo++
    }

    fun step(dtMs: Long) {
        if (move == Move.DRAG) return
        elapsedMs += dtMs
        if (move == Move.WALK || move == Move.RUN) {
            val speed = if (move == Move.RUN || onSide()) RUN_DP_S else WALK_DP_S
            var left = speed * dtMs / 1000f
            repeat(4) {
                if (left <= 0f) return@repeat
                val fwd = ((target - t) % per + per) % per
                val dir = if (fwd <= per / 2) 1f else -1f
                val dist = if (dir > 0) fwd else per - fwd
                val x0 = pos().first
                val go = min(left, dist)
                t = ((t + dir * go) % per + per) % per
                left -= go
                val dx = pos().first - x0
                if (dx != 0f) faceLeft = dx < 0f
                if (go >= dist) pickTarget()
            }
        }
        if (elapsedMs >= durationMs) next()
    }

    fun frame(): Frame {
        if (move == Move.DRAG) return Frame(dragX, dragY, "front", faceLeft, move, elapsedMs)
        var (x, y) = pos()
        val pose = when (move) {
            Move.WALK -> "side"
            Move.RUN -> if ((elapsedMs / 150) % 2 == 0L) "side" else "walk"
            Move.SLEEP -> "lying"
            Move.HIDE -> "back"
            else -> "front"
        }
        if (move == Move.HIDE) {
            // slide 70% out past the nearest edge, back in at the end
            val k = 0.7f * size * min(1f, min(elapsedMs, durationMs - elapsedMs).coerceAtLeast(0L) / 300f)
            when (edge()) {
                "bottom" -> y += k
                "top" -> y -= k
                "right" -> x += k
                else -> x -= k
            }
        }
        return Frame(x, y, pose, faceLeft, move, elapsedMs)
    }

    fun tap() {
        if (move == Move.DRAG) return
        wasSleeping = move == Move.SLEEP
        next(Move.FLINCH)
    }

    fun drag(x: Float, y: Float) {
        if (move != Move.DRAG) { move = Move.DRAG; stateNo++ }
        dragX = x
        dragY = y
    }

    fun release() {
        if (move != Move.DRAG) return
        t = project(dragX, dragY)
        wasSleeping = false
        move = Move.BOUNCE
        next()
    }

    /** Fold / rotation: new screen, pet jumps to the nearest edge. */
    fun resize(w: Float, h: Float) {
        val (x, y) = pos()
        setSize(w, h)
        t = project(x, y)
        if (move == Move.WALK || move == Move.RUN) pickTarget()
    }

    companion object {
        const val WALK_DP_S = 40f
        const val RUN_DP_S = 120f
        // tuning knobs for the edge time split
        const val CROSS_UP = 0.10f
        const val CROSS_DOWN = 0.22f
    }
}
