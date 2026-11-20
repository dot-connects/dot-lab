package kr.dot.sb.platform

import kr.dot.sb.data.StartEvent
import kr.dot.sb.data.StartEvent.JOB
import kr.dot.sb.data.StartEvent.POWER_CONNECTED
import kr.dot.sb.data.StartEvent.SCREEN_OFF
import kr.dot.sb.data.StartEvent.UNPLUG_CONFIRMED
import kr.dot.sb.data.StartEvent.USER_PRESENT
import kr.dot.sb.data.StartPolicy
import kr.dot.sb.data.StartState
import org.junit.Assert.assertEquals
import org.junit.Test

/** plan-r16 T1: StandBy starts on a plug-in (once) or on the screen going off after use, never after a system wake. */
class StartPolicyTest {
    private fun row(s: StartState, e: StartEvent, alive: Boolean, launch: Boolean, after: StartState) =
        assertEquals("$s $e alive=$alive", after to launch, StartPolicy.next(s, e, alive))

    @Test fun table() {
        val idle = StartState()
        row(idle, USER_PRESENT, false, false, StartState(used = true))
        row(StartState(used = true), SCREEN_OFF, false, true, idle)
        row(StartState(used = true), SCREEN_OFF, true, false, idle)
        row(idle, SCREEN_OFF, false, false, idle)
        row(idle, POWER_CONNECTED, false, true, StartState(plugHandled = true))
        row(idle, POWER_CONNECTED, true, false, StartState(plugHandled = true))
        row(StartState(plugHandled = true), POWER_CONNECTED, false, false, StartState(plugHandled = true))
        row(StartState(plugHandled = true), UNPLUG_CONFIRMED, false, false, idle)
        row(idle, JOB, false, true, StartState(plugHandled = true))
        row(StartState(plugHandled = true), JOB, false, false, StartState(plugHandled = true))
    }

    /** Runs [events] from [s0] with no session alive in between; returns how many launches were asked for. */
    private fun launches(vararg events: StartEvent, s0: StartState = StartState(plugHandled = true)): Int {
        var s = s0
        var n = 0
        for (e in events) { val (ns, l) = StartPolicy.next(s, e, sessionAlive = false); s = ns; if (l) n++ }
        return n
    }

    @Test fun systemWakeThenTimeoutStartsNothing() = assertEquals(0, launches(SCREEN_OFF))

    @Test fun unlockThenOffStartsOnceOnly() = assertEquals(1, launches(USER_PRESENT, SCREEN_OFF, SCREEN_OFF))

    @Test fun chargerFlapStartsOnce() = assertEquals(1, launches(POWER_CONNECTED, POWER_CONNECTED, s0 = StartState()))

    @Test fun connectThenJobStartsOnce() = assertEquals(1, launches(POWER_CONNECTED, JOB, s0 = StartState()))

    @Test fun unplugThenConnectStartsAgain() = assertEquals(1, launches(UNPLUG_CONFIRMED, POWER_CONNECTED))

    /** Exit swipe (no unlock) leaves the lock screen, which times out: nothing comes back. */
    @Test fun exitSwipeThenLockScreenTimeoutStartsNothing() = assertEquals(0, launches(SCREEN_OFF, SCREEN_OFF))

    /** plan-r16 Q1: our own off ends the session; the next wake is the lock screen and its timeout starts nothing. */
    @Test fun ownOffThenWakeAndTimeoutStartsNothing() = assertEquals(0, launches(POWER_CONNECTED, SCREEN_OFF, SCREEN_OFF, s0 = StartState()) - 1)
}
