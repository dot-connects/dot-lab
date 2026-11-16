package kr.dot.sb.board

import kr.dot.sb.data.PomodoroTimer
import kr.dot.sb.ui.cards.RingTouch
import kr.dot.sb.ui.cards.ringMinutes
import kr.dot.sb.ui.cards.ringTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kr.dot.sb.ui.state.TimerMode
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

    @Test fun ringBandDragsWhenSettableInsideTapsOutsideNothing() {
        // r15: on the band (0.93 r ± 0.15) a stopped timer drags; inside the circle a tap toggles; outside nothing
        assertEquals(RingTouch.DRAG, ringTouch(0.93f, settable = true, tap = true))
        assertEquals(RingTouch.DRAG, ringTouch(1.05f, settable = true, tap = true))
        assertEquals(RingTouch.TAP, ringTouch(0.3f, settable = true, tap = true))
        assertEquals(RingTouch.TAP, ringTouch(0.93f, settable = false, tap = true)) // running: the whole circle toggles
        assertEquals(RingTouch.NONE, ringTouch(1.2f, settable = true, tap = true))
        assertEquals(RingTouch.HOLD, ringTouch(0.3f, settable = true, tap = false)) // rings with buttons: only a long press
        assertEquals(RingTouch.NONE, ringTouch(0.3f, settable = false, tap = false)) // running: the page keeps it
        assertEquals(RingTouch.NONE, ringTouch(0.93f, settable = false, tap = false))
    }

    @Test fun switchModeFlipsToTheFullOtherLengthOnlyWhenNotRunning() {
        val t = PomodoroTimer(25 * 60, 5 * 60)
        assertTrue(t.switchMode())
        assertEquals(TimerMode.BREAK, t.mode)
        assertEquals(5 * 60, t.state(0).leftSec)
        assertEquals(5 * 60, t.state(0).totalSec)
        t.toggle(0); t.toggle(60_000) // paused at 4:00 of the break: a switch would wipe the time left
        assertFalse(t.switchMode())
        assertEquals(TimerMode.BREAK, t.mode)
        assertEquals(4 * 60, t.state(60_000).leftSec)
        t.toggle(60_000) // running: ignored
        assertFalse(t.switchMode())
        assertTrue(t.state(60_000).running)
        t.toggle(300_000) // paused at 0:00... (finished) is switchable again
        assertEquals(0, t.state(300_000).leftSec)
    }
}
