package kr.dot.catchpet.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LifeTest {
    @Test fun landingStaysOnOrAboveTheFloorAndSettles() {
        var t = 0f
        while (t < Life.LAND_MS + 200f) {
            val p = Life.land(t)
            assertTrue("below the floor at $t: ${p.dy}", p.dy <= 0f)
            t += 5f
        }
        assertEquals(Pose.REST, Life.land(Life.LAND_MS))
    }

    @Test fun idleStaysSmall() {
        var t = 0f
        while (t < 60_000f) {
            val p = Life.idle(t, 0.37f)
            assertTrue(p.dy in -0.09f..0f)
            assertTrue(abs(p.daz) <= 1.5f + 1e-4f)
            assertTrue(p.sy in 0.85f..1.1f)
            t += 7f
        }
    }

    @Test fun pendulumIsCappedAndDiesDown() {
        val d = Dangle { 0.5f }
        d.kick(0.5f)                                   // a huge shove
        repeat(200) { d.step(16f, 0f, Dangle.CAP_CRITTER); assertTrue(abs(d.rot) <= Dangle.CAP_CRITTER + 1e-4f) }
        repeat(2000) { d.step(16f, 0f, Dangle.CAP_CRITTER) }
        assertTrue("still swinging: ${d.rot}", abs(d.rot) < 0.5f)
    }

    @Test fun turningThePhoneSwingsIt() {
        val d = Dangle { 0.5f }
        var rel = 0f
        repeat(15) { rel += 4f; d.step(16f, rel, Dangle.CAP_CRITTER) }   // the thing slides right against the claw
        var peak = 0f
        repeat(70) { d.step(16f, rel, Dangle.CAP_CRITTER); peak = maxOf(peak, abs(d.rot)) }   // watch one swing period
        assertTrue("no swing: $peak", peak in 4f..Dangle.CAP_CRITTER)
    }
}

class DropTest {
    @Test fun aNearMissFallsHigherThanAClearOne() {
        val p = 0.55f; val lift = 200f
        val near = Claw.dropLiftFor(0.56f, p, lift)
        val clear = Claw.dropLiftFor(0.99f, p, lift)
        assertTrue("near $near vs clear $clear", near > clear)
        assertTrue(near <= lift * 0.9f + 1e-3f)
        assertTrue(clear >= lift * 0.25f - 1e-3f)
    }

    @Test fun shortLiftsStillShowTheSlide() {
        assertEquals(20f, Claw.dropLiftFor(0.99f, 0.5f, 40f), 1e-3f)   // half the lift when it is shorter than the slide
        assertEquals(0f, Claw.dropLiftFor(0.9f, 0.5f, 0f), 1e-3f)
    }
}

class ClawFeelTest {
    private val toMask: (Float, Float) -> Pair<Int, Int> = { x, y -> x.toInt() to y.toInt() }

    @Test fun digsInAndStopsAtTheBodyWidth() {
        val b = BooleanArray(90 * 400)
        for (y in 140..199) for (x in 30..60) b[y * 90 + x] = true
        val m = Mask(90, 400, b, Mask.Kind.CAT)
        val c = Claw(90f, 400f, 4f) { 0.99f }
        c.aim(0.5f); c.release()
        var contactY = -1f; var p = -1f
        repeat(2000) {
            c.step(16f, listOf(m), toMask)
            if (c.st == Claw.St.GRIP && contactY < 0f) { contactY = c.y; p = c.result!!.p }
            if (c.st == Claw.St.UP) return@repeat
        }
        assertTrue("dug in: contact $contactY", c.st != Claw.St.GRIP)
        assertEquals(p, chance(m, 45, c.result!!.hitRow!!), 1e-4f)   // digging does not move the touch row / chance
        assertTrue("prongs stop on the body: ${c.bodyW}", c.bodyW > 25f)
    }
}
