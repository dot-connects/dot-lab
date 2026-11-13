package kr.dot.sb.clock

import java.time.LocalTime
import kr.dot.sb.ui.clock.barPosition
import kr.dot.sb.ui.clock.rimDot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GaugeGeometryTest {
    @Test fun barUsesTheWholeDay() {
        assertEquals(0f, barPosition(LocalTime.MIDNIGHT), 0f)
        assertEquals(0.5f, barPosition(LocalTime.NOON), 0f)
        assertTrue(barPosition(LocalTime.of(23, 59)) < 1f)
        assertTrue(barPosition(LocalTime.of(23, 59)) > 0.999f)
    }

    @Test fun rimStartsAtTopCentreAndSpreadsEvenly() {
        val dots = (0 until 60).map { rimDot(it, 780f, 360f, 18f, 44f) }
        assertEquals(390f, dots[0].first, 0.01f)
        assertEquals(18f, dots[0].second, 0.01f)
        assertTrue(dots.all { it.first in 18f..762f && it.second in 18f..342f })
        val steps = dots.indices.map { i ->
            val a = dots[i]; val b = dots[(i + 1) % dots.size]
            hypot(b.first - a.first, b.second - a.second)
        }
        assertTrue(steps.max() - steps.min() < 2f)
    }
}
