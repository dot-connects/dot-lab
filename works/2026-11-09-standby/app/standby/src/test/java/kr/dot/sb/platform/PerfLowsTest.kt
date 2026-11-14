package kr.dot.sb.platform

import kr.dot.sb.data.Debounce
import org.junit.Assert.assertEquals
import org.junit.Test

/** plan-r11 Part B. B1: the shade rebuild waits 300 ms after the last change, but a burst still publishes within 1 s. */
class PerfLowsTest {
    @Test fun trailingDebounceWithMaxWait() {
        val d = Debounce(300, 1_000)
        assertEquals(300L, d.request(0))      // first change: 300 ms later
        assertEquals(500L, d.request(200))    // another: pushed to 300 ms after it
        assertEquals(1_000L, d.request(800))  // a burst: never past 1 s after the first
        assertEquals(1_000L, d.request(950))
        d.fired()
        assertEquals(1_400L, d.request(1_100)) // a new burst starts its own window
    }

    /** B4: the calendar is queried rarely, so events that ended since the query drop out at each minute by now. */
    @Test fun endedEventsDropOutWithoutAQuery() {
        val d = java.time.LocalDate.of(2026, 11, 9)
        fun item(t: String, endH: Int?, day: java.time.LocalDate = d) =
            kr.dot.sb.ui.state.AgendaItem(t, t, 0, day, end = endH?.let { day.atTime(it, 0) })
        val all = listOf(item("a", 10), item("b", 22), item("allday", null), item("tomorrow", 9, d.plusDays(1)))
        val (agenda, upcoming) = kr.dot.sb.data.Agenda.visible(all, d.atTime(21, 41))
        assertEquals(listOf("b", "allday"), agenda.map { it.title })
        assertEquals(listOf("b", "allday", "tomorrow"), upcoming.map { it.title })
        // six left today: the agenda shows five
        val many = (0 until 6).map { item("e$it", 23) }
        assertEquals(5, kr.dot.sb.data.Agenda.visible(many, d.atTime(21, 41)).first.size)
    }

    /** B5: 250 ms chunks into a ≥ 500 ms buffer; the fade ramps per sample and chunks join without a step. */
    @Test fun sleepChunksAndRamp() {
        assertEquals(kr.dot.sb.data.NoiseGen.RATE / 4, kr.dot.sb.data.NoiseGen.CHUNK)
        assertEquals(kr.dot.sb.data.NoiseGen.RATE, kr.dot.sb.data.NoiseGen.bufferBytes(1_000)) // 500 ms of 16-bit mono
        assertEquals(40_000, kr.dot.sb.data.NoiseGen.bufferBytes(40_000))                    // a bigger device minimum wins
        val n = 100
        assertEquals(1f, kr.dot.sb.data.NoiseGen.ramp(0, n, 1f, 0.5f), 1e-6f)
        assertEquals(0.505f, kr.dot.sb.data.NoiseGen.ramp(n - 1, n, 1f, 0.5f), 1e-6f)
        // the next chunk starts where this one was heading
        assertEquals(0.5f, kr.dot.sb.data.NoiseGen.ramp(0, n, 0.5f, 0f), 1e-6f)
    }
}
