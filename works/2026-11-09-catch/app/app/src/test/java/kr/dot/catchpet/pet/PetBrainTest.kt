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
        return PetBrain(zone, w = 400f, h = 800f, baseFloor = 700f, size = 48f) { r.nextFloat() }
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
        assertTrue(b.act in listOf(Act.SMILE, Act.HOP, Act.WIGGLE))   // a tap picks one of three (operator 11-15)
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
            assertTrue("act ${it.act}", it.act in setOf(Act.IDLE, Act.CLIMB, Act.SLEEP, Act.SMILE))
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

    @Test fun ponderingStandsStillUntilTheCloudCloses() {
        val b = brain()
        b.ponder(true)
        val x = b.x
        run(b, 20_000f) { assertEquals(Act.PONDER, it.act); assertEquals(x, it.x, 0.01f) }
        b.ponder(false)
        assertEquals(Act.IDLE, b.act)
    }

    @Test fun anotherPetKeepsThePlace() {
        val b = brain(Zone.LEFT)
        b.grab(10f, 300f); b.drop(10f, 300f)
        val c = PetBrain(Zone.RIGHT, w = 400f, h = 800f, baseFloor = 700f, size = 48f) { 0.5f }
        c.takeOver(b)
        assertEquals(Zone.LEFT, c.zone)
        assertEquals(300f, c.y, 0.01f)
    }

    @Test fun aThrowBouncesOffTheWallAndLandsDizzy() {
        val b = brain()
        b.grab(200f, 300f)
        b.fling(200f, 300f, vx = 2f, vy = -0.5f)
        var bounced = false
        run(b, 3000f) { if (it.x >= 400f - 24f - 0.01f) bounced = true }
        assertTrue("hit the right wall", bounced)
        assertTrue(b.x in 24f..376f)
        assertEquals(Zone.FLOOR, b.zone)
        assertEquals(700f, b.y, 0.01f)
    }

    @Test fun upHighTakesAFloorPetToAWallAboveTheKeyboardLine() {
        val b = brain()
        b.upHigh(true)
        assertTrue(b.zone == Zone.LEFT || b.zone == Zone.RIGHT)
        run(b, 30_000f) { assertTrue("whole pet stays above 60%", it.y + 24f <= 800f * 0.6f + 0.01f) }
        b.upHigh(false)
        assertEquals(Zone.FLOOR, b.zone)
    }

    @Test fun upHighDropInTheMiddleSticksToTheNearerWall() {
        val b = brain()
        b.upHigh(true)
        b.grab(150f, 600f)
        b.drop(150f, 600f)
        assertEquals(Zone.LEFT, b.zone)
        assertTrue(b.y <= 480f)
    }

    @Test fun aTapOnAFlyingPetLetsItLand() {
        val b = brain()
        b.grab(200f, 300f)
        b.fling(200f, 300f, vx = 1f, vy = 0f)
        b.tap(); b.ponder(true)
        assertEquals(Act.FALL, b.act)
        run(b, 3000f)
        assertEquals(700f, b.y, 0.01f)
    }

    @Test fun switchingAppsDoesNotWakeASleeper() {
        val b = brain()
        run(b, 10_000f, quiet = 180_000f)
        assertEquals(Act.SLEEP, b.act)
        b.upHigh(true)
        assertEquals(Act.SLEEP, b.act)
    }

    @Test fun aThrownPetLandingWhileUpHighGoesBackUp() {
        val b = brain()
        b.upHigh(true)
        b.grab(200f, 300f)
        b.fling(200f, 300f, vx = 0f, vy = 0f)
        run(b, 6000f)
        assertTrue(b.zone == Zone.LEFT || b.zone == Zone.RIGHT)
    }

    @Test fun droppedNearAWallSlidesIn() {
        val b = brain()
        b.grab(40f, 300f)
        b.drop(40f, 300f)
        assertEquals(Zone.LEFT, b.zone)
        assertEquals(40f - 24f, b.slideOffset(), 0.01f)   // from where the box's middle was
        run(b, 200f)
        assertEquals(0f, b.slideOffset(), 0.01f)
    }
}
