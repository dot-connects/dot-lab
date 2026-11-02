package kr.dot.parkingfloor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorMathTest {
    @Test fun lowerPressureIsHigher() {
        assertTrue(FloorMath.heightM(1012.0, 1013.0) > 0)
        assertTrue(FloorMath.heightM(1014.0, 1013.0) < 0)
    }

    @Test fun oneHectopascalIsAboutEightMetres() {
        assertEquals(8.4, FloorMath.heightM(1012.0, 1013.0), 0.3)
    }

    @Test fun goingDownFromFirstFloorPassesNoZeroFloor() {
        assertEquals(-1, FloorMath.estimate(0, -3.3))
        assertEquals(-2, FloorMath.estimate(0, -6.6))
        assertEquals(0, FloorMath.estimate(-1, 3.3))
    }

    @Test fun tieRoundsUp() {
        assertEquals(1, FloorMath.estimate(0, 1.65))
        assertEquals(0, FloorMath.estimate(0, -1.65))
    }

    @Test fun labels() {
        assertEquals("1층", FloorMath.labelOf(0))
        assertEquals("2층", FloorMath.labelOf(1))
        assertEquals("B1", FloorMath.labelOf(-1))
        assertEquals("B3", FloorMath.labelOf(-3))
    }

    @Test fun labelRoundTrip() {
        FloorMath.FLOORS.forEach { assertEquals(it, FloorMath.indexOf(FloorMath.labelOf(it))) }
        assertEquals(76, FloorMath.FLOORS.size)
    }

    @Test fun median() {
        assertEquals(2.0, FloorMath.median(listOf(3.0, 1.0, 2.0)), 0.0)
        assertEquals(2.5, FloorMath.median(listOf(4.0, 1.0, 2.0, 3.0)), 0.0)
    }

    @Test fun csvEscapes() {
        assertEquals("plain", FloorMath.csv("plain"))
        assertEquals("\"a,b\"", FloorMath.csv("a,b"))
        assertEquals("\"a\"\"b\"", FloorMath.csv("a\"b"))
    }
}
