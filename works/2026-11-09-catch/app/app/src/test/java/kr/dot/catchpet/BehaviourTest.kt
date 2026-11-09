package kr.dot.catchpet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BehaviourTest {
    private val w = 412f
    private val h = 915f

    /** Distance from a top-left point to the perimeter rectangle of the pet's window. */
    private fun offPerimeter(b: Behaviour, x: Float, y: Float): Float {
        val a = w - b.size
        val bb = h - b.size
        val inside = x in 0f..a && y in 0f..bb
        val dx = max(max(-x, x - a), 0f)
        val dy = max(max(-y, y - bb), 0f)
        return if (inside) min(min(x, a - x), min(y, bb - y)) else max(dx, dy)
    }

    /** Runs `seconds` at 50 ms steps, checks the invariants, returns the state log. */
    private fun simulate(name: String, labels: List<String>, seed: Long, seconds: Long = 60, lines: Boolean = true): List<String> {
        val unlocked = PetFile.moves(labels)
        val allowed = unlocked.filter { it != "flinch" }.map { Move.valueOf(it.uppercase()) }.toSet()
        val b = Behaviour(unlocked, w, h, seed)
        val log = mutableListOf("## $name labels=$labels unlocked=$unlocked seed=$seed screen=${w.toInt()}x${h.toInt()}dp")
        val seen = mutableSetOf<Move>()
        val edgeMs = mutableMapOf<String, Long>()
        var no = -1
        var startMs = 0L
        fun line(nowMs: Long) {
            val f = b.frame()
            log += "t=%6.2fs %-6s dur=%5.2fs edge=%-6s at=(%5.1f,%5.1f) pose=%s".format(
                nowMs / 1000f, b.move.name.lowercase(), b.durationMs / 1000f, b.edge(), f.x, f.y, f.pose)
        }
        var now = 0L
        while (now <= seconds * 1000) {
            if (b.stateNo != no) {
                no = b.stateNo
                startMs = now
                if (lines) line(now)
                assertTrue("${b.move} not unlocked", b.move in allowed)
                assertTrue("duration ${b.durationMs}", b.durationMs in 3000L..12000L)
                if (b.move != Move.WALK && b.move != Move.RUN && Move.WALK in allowed) {
                    assertTrue("rests on a side at $now", b.edge() == "bottom" || b.edge() == "top")
                }
            }
            val f = b.frame()
            seen += b.move
            val e = b.edge().let { if (it == "left" || it == "right") "side" else it }
            edgeMs[e] = (edgeMs[e] ?: 0L) + 50
            val off = offPerimeter(b, f.x, f.y)
            if (b.move == Move.HIDE) assertTrue("hide off by $off", off <= 0.7f * b.size + 0.01f)
            else assertTrue("${b.move} off perimeter by $off at $now", off < 0.01f)
            b.step(50)
            now += 50
            if (b.stateNo != no) assertTrue(now - startMs in 3000L..12050L)
        }
        log += "time on edge: " + listOf("bottom", "top", "side").joinToString(" ") { "$it=%.0f%%".format(100f * (edgeMs[it] ?: 0L) / (now - 50)) }
        log += "states seen: ${seen.map { it.name.lowercase() }.sorted()}"
        return log
    }

    @Test fun simulationOnePoseAndAllPoses() {
        val one = simulate("one pose", listOf("front"), 1)
        val all = simulate("all poses", PetFile.LABELS, 1)
        assertTrue(one.last().endsWith("[bounce]"))
        val long = simulate("all poses, 10 min (state lines omitted)", PetFile.LABELS, 1, 600, lines = false)
        assertTrue(long.last().endsWith("[bounce, hide, run, sleep, walk]"))
        val out = File(System.getProperty("user.dir"), "../../out/s4").apply { mkdirs() }
        File(out, "behaviour-sim.log").writeText((one + "" + all + "" + long).joinToString("\n") + "\n")
    }

    @Test fun tapFlinchesAndWakesASleeper() {
        val b = Behaviour(PetFile.moves(listOf("lying")), w, h, 3)
        while (b.move != Move.SLEEP) b.step(50)
        b.tap()
        assertEquals(Move.FLINCH, b.move)
        assertEquals("front", b.frame().pose)
        b.step(400)
        assertEquals(Move.BOUNCE, b.move)
    }

    @Test fun dragSnapsToNearestEdgeAndResizeKeepsPetOnScreen() {
        val b = Behaviour(PetFile.moves(listOf("front")), w, h, 2)
        b.drag(150f, 20f)
        assertEquals(Move.DRAG, b.move)
        b.release()
        assertEquals("top", b.edge())
        assertEquals(150f, b.frame().x, 0.01f)
        b.resize(915f, 412f) // rotate
        val (x, y) = b.pos()
        assertTrue(x in 0f..(915f - b.size) && y in 0f..(412f - b.size))
        assertFalse(abs(y) > 0.01f && abs(y - (412f - b.size)) > 0.01f && x > 0.01f && abs(x - (915f - b.size)) > 0.01f)
    }
}
