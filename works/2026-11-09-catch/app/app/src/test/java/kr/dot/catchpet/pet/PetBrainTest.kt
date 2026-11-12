package kr.dot.catchpet.pet

import kr.dot.catchpet.pet.PetBrain.Act
import kr.dot.catchpet.pet.PetBrain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetBrainTest {
    private fun brain(zone: Zone = Zone.FLOOR, seed: Int = 1): PetBrain {
        val r = Random(seed)
        return PetBrain(zone, w = 400f, h = 800f, floorY = 700f, size = 48f) { r.nextFloat() }
    }

    private fun run(b: PetBrain, ms: Float, quiet: Float = 0f, night: Boolean = false, charging: Boolean = false, each: (PetBrain) -> Unit = {}) {
        var t = 0f
        while (t < ms) { b.step(16f, quiet + t, night, charging); each(b); t += 16f }
    }

    @Test fun longQuietSpellSleeps() {
        val b = brain()
        run(b, 10_000f, quiet = 180_000f)
        assertEquals(Act.SLEEP, b.act)
    }

    @Test fun shortQuietSpellDoesNotSleep() {
        val b = brain()
        run(b, 20_000f, quiet = 0f) { assertTrue(it.act != Act.SLEEP) }
    }

    @Test fun nightSleepsOnlyAfterSettling() {
        val b = brain()
        run(b, 10_000f, quiet = 0f, night = true) { assertTrue("slept right after a touch", it.act != Act.SLEEP) }
        val c = brain()
        run(c, 10_000f, quiet = 30_000f, night = true)
        assertEquals(Act.SLEEP, c.act)
    }

    @Test fun tapWakesASleeper() {
        val b = brain()
        run(b, 10_000f, quiet = 180_000f)
        assertEquals(Act.SLEEP, b.act)
        b.tap()
        assertEquals(Act.SMILE, b.act)
        assertEquals("smile", b.frame(0f))
    }

    @Test fun dropNearLeftSticksToTheWall() {
        val b = brain()
        b.grab(200f, 300f); assertEquals(Act.HANG, b.act)
        b.drop(40f, 300f)
        assertEquals(Zone.LEFT, b.zone)
        assertEquals(90f, b.rotationDeg(), 0f)
        assertEquals(0f, b.x, 0f)
    }

    @Test fun dropInTheMiddleFallsToTheFloor() {
        val b = brain()
        b.grab(200f, 240f); b.drop(200f, 240f)
        assertEquals(Act.FALL, b.act)
        run(b, 5_000f)
        assertEquals(Zone.FLOOR, b.zone)
        assertEquals(700f, b.y, 0f)
    }

    @Test fun walkingStaysOnScreen() {
        val b = brain(seed = 7)
        run(b, 60_000f) { assertTrue("x=${it.x}", it.x in 24f..376f) }
    }

    @Test fun climbingStaysOnTheWall() {
        val b = brain(Zone.RIGHT, seed = 3)
        run(b, 60_000f) {
            assertTrue("y=${it.y}", it.y in 160f..652f)
            assertTrue("act ${it.act}", it.act in setOf(Act.IDLE, Act.CLIMB, Act.SLEEP, Act.SMILE, Act.DANCE))
        }
    }

    @Test fun sleepingOnAWallCurlsUp() {
        val b = brain(Zone.LEFT)
        run(b, 10_000f, quiet = 180_000f)
        assertEquals("curl", b.frame(0f))
        val f = brain(Zone.FLOOR)
        run(f, 10_000f, quiet = 180_000f)
        assertEquals("sleep", f.frame(0f))
    }

    @Test fun doubleTapDancesThenCarriesOn() {
        val b = brain()
        b.doubleTap()
        assertEquals(Act.DANCE, b.act)
        run(b, 3_100f)
        assertTrue(b.act != Act.DANCE)
    }
}
