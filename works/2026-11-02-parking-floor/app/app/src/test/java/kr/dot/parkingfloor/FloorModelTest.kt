package kr.dot.parkingfloor

import kr.dot.parkingfloor.FloorModel.Pair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorModelTest {
    @Test
    fun defaultsPlaceB1AndB2() {
        assertEquals(-1, FloorModel.estimate(-4.1, emptyList()).floor)
        assertEquals(-2, FloorModel.estimate(-7.2, emptyList()).floor)
        assertEquals(0, FloorModel.estimate(-0.6, emptyList()).floor)
        assertEquals(1, FloorModel.estimate(4.2, emptyList()).floor)
    }

    @Test
    fun runnerUpIsTheNeighbourOnTheLeaningSide() {
        // B2 sits at 7.3 m by default, B3 at 10.6: 8.5 m is B2 leaning toward B3
        val e = FloorModel.estimate(-8.5, emptyList())
        assertEquals(-2, e.floor)
        assertEquals(-3, e.alt)
    }

    @Test
    fun oneConfirmationLearnsTheBuilding() {
        // DSR 2026-11-03: 2F at 6.34 m, 3F measured at 13.25 m. Fixed shape calls 3F something higher.
        val learned = listOf(Pair(6.34, 1))
        assertEquals(2, FloorModel.estimate(13.25, learned).floor)
    }

    @Test
    fun learningOnlyUsesTheSameDirection() {
        val above = listOf(Pair(6.34, 1))
        assertEquals(-2, FloorModel.estimate(-7.2, above).floor)
    }

    @Test
    fun twoLevelsFitFirstStepSeparately() {
        // Wonhee 2026-11-02 style: deep first step, then about 4.5 m
        val pairs = listOf(Pair(-5.9, -1), Pair(-10.4, -2))
        val shape = FloorModel.shape(pairs, below = true)
        assertEquals(5.9, shape.first, 0.01)
        assertEquals(4.5, shape.step, 0.01)
        assertEquals(-3, FloorModel.estimate(-14.8, pairs).floor)
    }

    @Test
    fun tallTowerAtThreePointThree() {
        // Honors-ville 43F to B2 is 44 levels over 144.3 m; as one place that's 3.28 m a level
        val pairs = listOf(Pair(-3.3, -1))
        assertEquals(-2, FloorModel.estimate(-6.5, pairs).floor)
    }

    @Test
    fun confidenceDropsBetweenLevels() {
        val sure = FloorModel.estimate(-7.3, emptyList()).confidence
        val torn = FloorModel.estimate(-8.95, emptyList()).confidence
        assertTrue(sure > 0.9)
        assertTrue(torn < 0.2)
    }

    @Test
    fun stepStaysPhysical() {
        val shape = FloorModel.shape(listOf(Pair(-3.0, -1), Pair(-3.1, -2)), below = true)
        assertTrue(shape.step >= 2.4)
    }
}
