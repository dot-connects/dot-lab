package kr.dot.catchpet.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
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
        assertEquals(0.9f, chance(m, 45, 100), 1e-4f)
        assertEquals(0.2f, chance(m, 60, 100), 1e-4f)
        assertEquals(0f, chance(m, 80, 100), 1e-4f)
    }

    private fun run(claw: Claw, targets: List<Mask>, ms: Int = 20000): List<Claw.St> {
        val seen = mutableListOf(claw.st)
        repeat(ms / 16) {
            claw.step(16f, targets, toMask)
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
        c.aim(0.1f)
        assertEquals(0.5f, c.x, 1e-6f)          // aiming is ignored once the claw drops
        c.next()
        assertEquals(Claw.St.AIM, c.st)
        assertNull(c.result)
    }
}

class CapsuleTest {
    @Test fun wrapsDropsRollsAndLeaves() {
        val c = Capsule(100f, 80f, floorY = 600f, screenW = 300f)
        var t = 0f
        while (c.phase == Capsule.Phase.WRAP && t < 2000f) { c.step(16f); t += 16f }
        assertEquals(Capsule.R, c.r, 1e-3f)
        t = 0f
        while (c.phase == Capsule.Phase.DROP && t < 5000f) { c.step(16f); t += 16f; assertTrue(c.y <= 600f - c.r + 1e-3f) }
        assertEquals(Capsule.Phase.ROLL, c.phase)
        t = 0f
        while (c.phase == Capsule.Phase.ROLL && t < 5000f) { c.step(16f); t += 16f }
        assertEquals(Capsule.Phase.GONE, c.phase)
    }
}
