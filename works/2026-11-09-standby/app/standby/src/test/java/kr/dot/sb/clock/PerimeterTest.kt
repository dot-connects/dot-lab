package kr.dot.sb.clock

import kr.dot.sb.ui.clock.perimeterPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot

/** Rect-dial numerals (review C6): 12/3/6/9 on the axes, equal spacing along the perimeter. */
class PerimeterTest {
    private val a = 300f; private val b = 170f; private val r = 30f

    @Test fun axes() {
        listOf(0f to (0f to -b), 0.25f to (a to 0f), 0.5f to (0f to b), 0.75f to (-a to 0f)).forEach { (t, p) ->
            val (x, y) = perimeterPoint(a, b, r, t)
            assertEquals("x at $t", p.first, x, 0.01f)
            assertEquals("y at $t", p.second, y, 0.01f)
        }
    }

    @Test fun symmetric() {
        for (i in 1..2) {
            val (x1, y1) = perimeterPoint(a, b, r, i / 12f)
            val (x2, y2) = perimeterPoint(a, b, r, (12 - i) / 12f)
            assertEquals(x1, -x2, 0.01f); assertEquals(y1, y2, 0.01f)
        }
        // 1 and 2 sit apart from 12 and from each other (no "11 12 1" bunching)
        val p0 = perimeterPoint(a, b, r, 0f); val p1 = perimeterPoint(a, b, r, 1 / 12f); val p2 = perimeterPoint(a, b, r, 2 / 12f)
        val d01 = hypot(p1.first - p0.first, p1.second - p0.second)
        val d12 = hypot(p2.first - p1.first, p2.second - p1.second)
        org.junit.Assert.assertTrue("$d01 $d12", d01 > 100f && d12 > 100f)
    }
}
