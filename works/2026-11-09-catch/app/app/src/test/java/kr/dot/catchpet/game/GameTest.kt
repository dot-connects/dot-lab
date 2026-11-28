package kr.dot.catchpet.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI

class MaskTest {
    private fun block(x0: Int, y0: Int, x1: Int, y1: Int, w: Int = 30, h: Int = 30, kind: Mask.Kind = Mask.Kind.CAT): Mask {
        val b = BooleanArray(w * h)
        for (y in y0..y1) for (x in x0..x1) b[y * w + x] = true
        return Mask(w, h, b, kind)
    }

    @Test fun firstRowInColumn() {
        val m = block(5, 10, 6, 11)
        assertEquals(10, m.firstRowInColumn(5))
        assertEquals(10, m.firstRowInColumn(8))      // within the default ±2 columns
        assertNull(m.firstRowInColumn(20))
    }

    @Test fun spanInBandAndBox() {
        val m = block(5, 10, 6, 11)
        assertEquals(5..6, m.spanInBand(10, 18))
        assertNull(m.spanInBand(0, 5))
        assertEquals(Box(5, 10, 6, 11), m.box)
        assertEquals(4, m.area)
    }

    @Test fun ellipseAreaIsAboutPiOverFourOfTheBox() {
        val e = Mask.ellipseIn(90, 160, Box(10, 20, 69, 119), Mask.Kind.CRITTER)
        val want = 60 * 100 * PI / 4
        assertTrue("area ${e.area} vs $want", kotlin.math.abs(e.area - want) / want < 0.1)
    }

    @Test fun iouOfSameAndDisjoint() {
        val a = block(0, 0, 4, 4); val b = block(10, 10, 14, 14)
        assertEquals(1f, iou(a, a), 1e-6f)
        assertEquals(0f, iou(a, b), 1e-6f)
    }
}

class TrackerTest {
    @Test fun holdsLastMaskForFourMisses() {
        val b = BooleanArray(4); b[0] = true
        val m = Mask(2, 2, b, Mask.Kind.DOG)
        val t = Tracker(4)
        val got = listOf(m, null, null, null, null, null).map { t.update(it) }
        assertEquals(listOf(m, m, m, m, m, null), got)
    }

    @Test fun emptyMaskCountsAsAMiss() {
        val t = Tracker(1)
        val full = Mask(1, 1, booleanArrayOf(true), Mask.Kind.CAT)
        val empty = Mask(1, 1, booleanArrayOf(false), Mask.Kind.CAT)
        assertSame(full, t.update(full))
        assertSame(full, t.update(empty))
        assertNull(t.update(empty))
    }
}

class ClawTest {
    // a 90x160 grid mapped 1:1 onto a 90x160 "screen" so screen px == mask px
    private val toMask: (Float, Float) -> Pair<Int, Int> = { x, y -> x.toInt() to y.toInt() }
    private fun body(x0: Int, x1: Int, y0: Int, y1: Int, kind: Mask.Kind = Mask.Kind.CAT): Mask {
        val b = BooleanArray(90 * 160)
        for (y in y0..y1) for (x in x0..x1) b[y * 90 + x] = true
        return Mask(90, 160, b, kind)
    }

    @Test fun chanceCentreEdgeOutside() {
        val m = body(30, 60, 100, 140)
        assertEquals(0.80f, chance(m, 45, 100), 1e-4f)
        assertEquals(0.35f, chance(m, 60, 100), 1e-4f)
        assertEquals(0f, chance(m, 80, 100), 1e-4f)
    }

    private fun run(claw: Claw, targets: List<Mask>, ms: Int = 20000): List<Claw.St> = run(claw, ms) { targets }

    /** targets(st) gives the frame's targets for the claw's current state (things moving mid-grab). */
    private fun run(claw: Claw, ms: Int = 20000, targets: (Claw.St) -> List<Mask>): List<Claw.St> {
        val seen = mutableListOf(claw.st)
        repeat(ms / 16) {
            claw.step(16f, targets(claw.st), toMask)
            if (claw.st != seen.last()) seen += claw.st
            if (claw.st == Claw.St.CARRY) claw.handedOff()
        }
        return seen
    }

    @Test fun topmostThingIsGrabbed() {
        val low = body(30, 60, 120, 150, Mask.Kind.DOG)
        val high = body(30, 60, 90, 110, Mask.Kind.PERSON)
        val c = Claw(90f, 160f, 4f) { 0.01f }
        c.aim(0.5f); c.release()
        run(c, listOf(low, high))
        assertSame(high, c.result!!.target)
    }

    @Test fun lowRollWinsAndCarries() {
        val c = Claw(90f, 160f, 4f) { 0.05f }
        c.aim(0.5f); c.release()
        val seen = run(c, listOf(body(30, 60, 100, 140)))
        assertTrue(c.result!!.win)
        assertEquals(listOf(Claw.St.DOWN, Claw.St.GRIP, Claw.St.UP, Claw.St.CARRY, Claw.St.DONE), seen)
    }

    @Test fun highRollSlipsAndFalls() {
        val c = Claw(90f, 400f, 4f) { 0.95f }
        val m = body(30, 60, 140, 159)
        // screen 400 px tall, grid rows map 1:1 -> the body sits at rows 140..159, far enough below to lift 70 px
        c.aim(0.5f); c.release()
        val seen = run(c, listOf(m))
        assertFalse(c.result!!.win)
        assertTrue("went through FALL: $seen", Claw.St.FALL in seen)
        assertEquals(Claw.St.DONE, c.st)
    }

    @Test fun shakingWhileGrippingHalvesTheChance() {
        val still = body(30, 60, 100, 140)
        val moved = body(52, 82, 100, 140)                     // centre moved past its half width -> full cut
        val c = Claw(90f, 160f, 4f) { 0.99f }
        c.aim(0.5f); c.release()
        run(c) { if (it == Claw.St.GRIP) listOf(moved) else listOf(still) }
        assertEquals(chance(still, 45, 100) * 0.5f, c.result!!.p, 1e-3f)
    }

    @Test fun aWinThatLeavesTheClawSlips() {
        val m = body(30, 60, 140, 159)
        val away = body(70, 89, 140, 159)
        val c = Claw(90f, 400f, 4f) { 0f }                      // roll 0 always wins at the grip
        c.aim(0.5f); c.release()
        val seen = run(c) { if (it == Claw.St.UP || it == Claw.St.FALL) listOf(away) else listOf(m) }
        assertTrue("went through FALL: $seen", Claw.St.FALL in seen)
        assertFalse(c.result!!.win)
        assertTrue(c.result!!.slipped)
        assertFalse(Claw.St.CARRY in seen)
    }

    @Test fun aWinStillUnderTheClawCarries() {
        val m = body(30, 60, 140, 159)
        val c = Claw(90f, 400f, 4f) { 0f }
        c.aim(0.5f); c.release()
        val seen = run(c, listOf(m))
        assertTrue(Claw.St.CARRY in seen)
        assertFalse(c.result!!.slipped)
    }

    @Test fun nothingBelowIsAMiss() {
        val c = Claw(90f, 160f, 4f) { 0f }
        c.aim(0.5f); c.release()
        run(c, emptyList())
        assertNull(c.result!!.target)
        assertFalse(c.result!!.win)
        assertEquals(Claw.St.DONE, c.st)
    }

    @Test fun releaseOnlyFromAimAndNextGoesBackToAim() {
        val c = Claw(90f, 160f, 4f) { 0f }
        c.release(); c.release()
        assertEquals(Claw.St.DOWN, c.st)
        c.aim(0.7f)
        assertEquals(Claw.HOME_X, c.x, 1e-6f)   // aiming is ignored once the claw drops
        c.next()
        assertEquals(Claw.St.AIM, c.st)
        assertNull(c.result)
    }
}

class CapsuleTest {
    @Test fun hangsDropsBouncesRollsAndLeaves() {
        val c = Capsule(100f, 80f, floorY = 600f, screenW = 300f)
        c.step(16f)
        assertEquals(Capsule.Phase.HANG, c.phase)
        c.hang(120f, 90f, 5f)
        assertEquals(120f, c.x, 1e-3f)
        c.drop()
        var t = 0f
        while (c.phase == Capsule.Phase.DROP && t < 5000f) { c.step(16f); t += 16f; assertTrue(c.y <= 600f - Capsule.R + 1e-3f) }
        assertEquals(3, c.bounces)
        assertEquals(Capsule.Phase.ROLL, c.phase)
        t = 0f
        while (c.phase == Capsule.Phase.ROLL && t < 5000f) { c.step(16f); t += 16f }
        assertEquals(Capsule.Phase.GONE, c.phase)
    }
}

/** The felt show must never jump a frame (operator 11-28 "뚝뚝 끊긴다"): positions are continuous in time. */
class ShowTest {
    private val toMask: (Float, Float) -> Pair<Int, Int> = { x, y -> x.toInt() to y.toInt() }
    private fun body(x0: Int, x1: Int, y0: Int, y1: Int): Mask {
        val b = BooleanArray(90 * 400)
        for (y in y0..y1) for (x in x0..x1) b[y * 90 + x] = true
        return Mask(90, 400, b, Mask.Kind.CAT)
    }

    /** No 16 ms frame of a round changes the claw's speed abruptly, across every state change (a jump would). */
    private fun noJumps(roll: Float, targets: (Claw.St) -> List<Mask>, hold: Float = 0f) {
        val c = Claw(90f, 400f, 4f) { roll }
        c.aim(0.5f); c.release()
        var prev = c.y
        var v = 0f
        var held = 0f
        val seen = mutableListOf(c.st)
        repeat(1000) {
            if (c.st == Claw.St.UP && held < hold) { c.hold = true; held += 16f } else c.hold = false
            if (c.st == Claw.St.FALL) c.fallHold = 300f
            c.step(16f, targets(c.st), toMask)
            if (c.st != seen.last()) seen += c.st
            // a teleport shows as a spike in the frame-to-frame speed; eased motion changes it a little each frame
            val dv = c.y - prev
            assertTrue("jerk ${dv - v} px in ${c.st} ($seen)", abs(dv - v) <= 6f)
            v = dv; prev = c.y
            if (c.st == Claw.St.CARRY) c.handedOff()
        }
        assertEquals(Claw.St.DONE, c.st)
    }

    @Test fun aWinMovesSmoothly() = noJumps(0f, { listOf(body(30, 60, 300, 380)) }, hold = 900f)
    @Test fun aLostRollMovesSmoothly() = noJumps(0.99f, { listOf(body(30, 60, 300, 380)) })
    @Test fun airMovesSmoothly() = noJumps(0f, { emptyList() })
    @Test fun aSlipMovesSmoothly() {
        val m = body(30, 60, 300, 380); val away = body(70, 89, 300, 380)
        noJumps(0f, { if (it == Claw.St.UP || it == Claw.St.FALL) listOf(away) else listOf(m) })
    }

    @Test fun theDescentIsSlowAndHeavy() {
        val c = Claw(90f, 400f, 4f) { 0f }
        c.aim(0.5f); c.release()
        var t = 0f
        while (c.st == Claw.St.DOWN && t < 5000f) { c.step(16f, listOf(body(30, 60, 300, 380)), toMask); t += 16f }
        // mockup: 650 ms + 2.4 ms per px of travel (here about 245 px), sine-eased
        assertTrue("descent took $t ms", t in 1150f..1450f)
    }

    @Test fun theLidShutsWithoutAJump() {
        val cc = CapClose(300f)
        var prev = cc.lid(0f)
        assertEquals(CapClose.LID_OPEN, prev, 1e-4f)
        var t = 0f
        while (t < cc.end + 50f) { t += 1f; val l = cc.lid(t); assertTrue("lid jumped at $t", abs(l - prev) < 1.5f); prev = l }
        assertEquals(0f, prev, 1e-4f)
        assertEquals(1f, cc.squash(cc.end), 1e-4f)
        val (s, dy, a) = cc.pop(240f)
        assertEquals(1f, s, 1e-4f); assertEquals(0f, dy, 1e-4f); assertEquals(1f, a, 1e-4f)
    }

    @Test fun theHopStartsAtTheCapsuleAndFadesOnTheBody() {
        val a = SlipShow.hop(0f, 100f, 200f, 0.25f, 40f, 300f, 0.36f)
        assertEquals(100f, a[0], 1e-3f); assertEquals(200f, a[1], 1e-3f); assertEquals(0.25f, a[2], 1e-3f); assertEquals(1f, a[3], 1e-3f)
        val b = SlipShow.hop(1f, 100f, 200f, 0.25f, 40f, 300f, 0.36f)
        assertEquals(40f, b[0], 1e-3f); assertEquals(300f, b[1], 1e-2f); assertEquals(0.36f, b[2], 1e-3f); assertEquals(0f, b[3], 1e-3f)
        assertEquals(0f, SlipShow.shudder(0f), 1e-4f)
    }
}
