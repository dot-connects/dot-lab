package kr.dot.sb.board

import kr.dot.sb.data.PomodoroTimer
import kr.dot.sb.ui.cards.ringMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 11-14 #10: dragging the timer ring like a Time Timer (full circle = 60 min, 12 o'clock at the top, y down). */
class TimerRingTest {
    @Test fun quartersOfTheCircle() {
        assertEquals(15, ringMinutes(1f, 0f, prev = 10))   // 3 o'clock
        assertEquals(30, ringMinutes(0f, 1f, prev = 10))   // 6 o'clock
        assertEquals(45, ringMinutes(-1f, 0f, prev = 40))  // 9 o'clock
    }

    @Test fun snapsToTheNearestMinute() {
        // 25 min = 150°: x = sin, y = -cos
        val a = Math.toRadians(151.0)
        assertEquals(25, ringMinutes(Math.sin(a).toFloat(), (-Math.cos(a)).toFloat(), prev = 20))
    }

    @Test fun twelveOClockHoldsTheEndYouCameFrom() {
        // coming round from the full side, passing 12 stays at 60; from the start side it stays at 1
        assertEquals(60, ringMinutes(0f, -1f, prev = 58))
        assertEquals(60, ringMinutes(0.2f, -1f, prev = 59))  // a little past 12: still full, no jump to 2
        assertEquals(1, ringMinutes(0f, -1f, prev = 2))
        assertEquals(1, ringMinutes(-0.2f, -1f, prev = 1))   // a little before 12 from the start: no jump to 58
    }

    @Test fun clampedOneToSixty() {
        for (deg in 0 until 360 step 7) {
            val a = Math.toRadians(deg.toDouble())
            val m = ringMinutes(Math.sin(a).toFloat(), (-Math.cos(a)).toFloat(), prev = 30)
            assert(m in 1..60) { "$deg° -> $m" }
        }
    }

    @Test fun stoppedSetsTheFocusLengthPausedSetsTheTimeLeft() {
        val t = PomodoroTimer(25 * 60, 5 * 60)
        t.set(40 * 60)
        assertEquals(40 * 60, t.state(0).totalSec)
        assertEquals(40 * 60, t.state(0).leftSec)
        t.toggle(0); t.toggle(60_000) // ran a minute, paused at 39:00
        t.set(10 * 60)
        assertEquals(10 * 60, t.state(60_000).leftSec)
        assertEquals(40 * 60, t.state(60_000).totalSec)
        assertFalse(t.state(60_000).running)
        t.toggle(60_000) // running: a set is ignored
        t.set(5 * 60)
        assertEquals(10 * 60, t.state(60_000).leftSec)
    }
}
