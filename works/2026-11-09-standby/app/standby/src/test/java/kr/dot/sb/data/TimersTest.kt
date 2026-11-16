package kr.dot.sb.data

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.TimerMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Timer app notifications: the shared timer store, its end alarm decision and the notification buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimersTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private class Fake : TimerEffects {
        val log = mutableListOf<String>()
        override fun schedule(id: String, endAt: Long?) { log += "schedule $id $endAt" }
        override fun ongoing(id: String, t: PomodoroTimer, now: Long) { log += "ongoing $id ${t.state(now)}" }
        override fun ended(id: String, t: PomodoroTimer) { log += "ended $id ${t.mode}" }
        override fun clearEnd(id: String) { log += "clearEnd $id" }
    }

    private val fake = Fake()
    private val id = "slot-1"
    private val min = 60_000L

    @Before fun setUp() {
        ConfigStore.forgetMemory()
        ConfigStore.update(ctx) { it.copy(focusMinutes = 25, breakMinutes = 5) }
        ctx.getSharedPreferences("timers", Context.MODE_PRIVATE).edit().clear().commit()
        Timers.forgetMemory()
        Timers.effects = { fake }
        SystemClock.setCurrentTimeMillis(1_000 * min) // the test times (0 .. 40 min) lie in this boot's past
    }

    @After fun tearDown() { Timers.forgetMemory(); Timers.effects = { kr.dot.sb.platform.DeviceTimerEffects(it) } }

    /** Starts slot [id] at [at] the way a holder does. */
    private fun start(at: Long): PomodoroTimer = Timers.get(ctx, id).also { it.toggle(at); Timers.save(ctx, id, at) }

    @Test fun endTimeIsStartPlusTimeLeftAndNullWhilePaused() {
        val t = PomodoroTimer(25 * 60, 5 * 60)
        assertNull(t.endAt())
        t.toggle(1_000)
        assertEquals(1_000 + 25 * min, t.endAt())
        t.toggle(1_000 + 5 * min) // pause with 20 min left
        assertNull(t.endAt())
        t.toggle(100 * min) // resume
        assertEquals(100 * min + 20 * min, t.endAt())
    }

    @Test fun snapRoundTripsThroughText() {
        val s = TimerSnap(TimerMode.BREAK, true, 123_456, 9_876_543_210, 1500, 300)
        assertEquals(s, TimerSnap.decode(s.encode()))
        assertNull(TimerSnap.decode("junk"))
    }

    @Test fun storeSurvivesProcessDeath() {
        start(1_000)
        Timers.forgetMemory() // the process died; the receiver or the next holder loads it again
        val t = Timers.all(ctx)[id]!!
        assertTrue(t.running)
        assertEquals(1_000 + 25 * min, t.endAt())
        assertEquals(TimerMode.FOCUS, t.mode)
    }

    @Test fun storeFromBeforeARebootIsDropped() {
        start(1_000)
        ctx.getSharedPreferences("timers", Context.MODE_PRIVATE).edit().putInt("_boot", 999).commit()
        Timers.forgetMemory()
        assertTrue(Timers.all(ctx).isEmpty())
    }

    @Test fun saveArmsTheEndAlarmAndTheOngoingNotification() {
        start(1_000)
        assertEquals("schedule $id ${1_000 + 25 * min}", fake.log[0])
        assertTrue(fake.log[1].startsWith("ongoing $id"))
    }

    @Test fun alarmWhileStandbyShowsLeavesItToTheHolderButReArms() {
        start(0)
        fake.log.clear()
        assertFalse(Timers.onAlarm(ctx, id, 25 * min, visible = true))
        // re-armed a few seconds on: if StandBy closes before its holder ends the timer, the closed path still does
        assertEquals(listOf("schedule $id ${25 * min + 5_000}"), fake.log)
        assertTrue(Timers.all(ctx)[id]!!.running)
        assertTrue(Timers.onAlarm(ctx, id, 25 * min + 5_000, visible = false))
        assertEquals("ended $id BREAK", fake.log.last())
    }

    @Test fun reArmedAlarmAfterTheHolderEndedItDoesNothing() {
        val t = start(0)
        Timers.onAlarm(ctx, id, 25 * min, visible = true)
        t.finishIfDone(25 * min + 50) // the holder's own end check
        fake.log.clear()
        assertFalse(Timers.onAlarm(ctx, id, 25 * min + 5_000, visible = false))
        assertEquals(emptyList<String>(), fake.log)
    }

    @Test fun stoppedFullTimerTakesTheConfigLengthsRunningKeepsItsOwn() {
        Timers.get(ctx, id)
        ConfigStore.update(ctx) { it.copy(focusMinutes = 30, breakMinutes = 10) }
        assertEquals(30 * 60, Timers.all(ctx)[id]!!.state(0).totalSec)
        val t = start(0)
        assertEquals(30 * min, t.endAt())
        ConfigStore.update(ctx) { it.copy(focusMinutes = 40, breakMinutes = 15) }
        assertEquals(30 * 60, Timers.all(ctx)[id]!!.state(min).totalSec) // running: its own total
        Timers.act(ctx, id, TimerAction.PAUSE, min)
        assertEquals(30 * 60, Timers.all(ctx)[id]!!.state(min).totalSec) // paused: still its own
        Timers.act(ctx, id, TimerAction.START, 2 * min)
        Timers.onAlarm(ctx, id, 31 * min, visible = false)
        assertEquals(15 * 60, Timers.all(ctx)[id]!!.state(31 * min).totalSec) // ended: the break now uses the config
        Timers.act(ctx, id, TimerAction.RESET, 32 * min)
        assertEquals(40 * 60, Timers.all(ctx)[id]!!.state(32 * min).totalSec)
    }

    @Test fun startTimeFromAPreviousBootResetsThatTimer() {
        start(SystemClock.elapsedRealtime() + 60 * min) // elapsedRealtime of a boot that ran longer than this one
        Timers.forgetMemory()
        val t = Timers.all(ctx)[id]!!
        assertFalse(t.running)
        assertEquals(TimerMode.FOCUS, t.mode)
        assertEquals(25 * min, t.leftMs(0))
    }

    @Test fun alarmWhileClosedEndsTheTimerWithHeadsUpOnce() {
        start(0)
        val before = Timers.changed.value
        fake.log.clear()
        assertTrue(Timers.onAlarm(ctx, id, 25 * min, visible = false))
        val t = Timers.all(ctx)[id]!!
        assertEquals(TimerMode.BREAK, t.mode)
        assertFalse(t.running)
        assertEquals("ended $id BREAK", fake.log.last())
        assertEquals("schedule $id null", fake.log.first())
        assertEquals(before + 1, Timers.changed.value)
        // a second delivery (or the holder's own end check) does not alert again
        assertFalse(Timers.onAlarm(ctx, id, 25 * min + 100, visible = false))
        assertEquals(1, fake.log.count { it.startsWith("ended") })
    }

    @Test fun alarmAfterAPauseDoesNothing() {
        start(0)
        Timers.act(ctx, id, TimerAction.PAUSE, 10 * min)
        assertFalse(Timers.onAlarm(ctx, id, 25 * min, visible = false))
    }

    @Test fun buttonsPauseResumeResetAndStartNext() {
        start(0)
        Timers.act(ctx, id, TimerAction.PAUSE, 10 * min)
        val t = Timers.all(ctx)[id]!!
        assertFalse(t.running)
        assertEquals(15 * min, t.leftMs(10 * min))
        assertTrue(fake.log.contains("schedule $id null"))

        Timers.act(ctx, id, TimerAction.START, 20 * min) // 계속
        assertEquals(35 * min, t.endAt())
        Timers.act(ctx, id, TimerAction.START, 21 * min) // twice: still running, same end
        assertEquals(35 * min, t.endAt())

        Timers.onAlarm(ctx, id, 35 * min, visible = false)
        Timers.act(ctx, id, TimerAction.START, 36 * min) // 휴식 시작
        assertEquals(TimerMode.BREAK, t.mode)
        assertEquals(36 * min + 5 * min, t.endAt())

        Timers.act(ctx, id, TimerAction.RESET, 37 * min)
        assertEquals(TimerMode.FOCUS, t.mode)
        assertFalse(t.running)
        assertEquals(25 * min, t.leftMs(37 * min))
        // persisted, not just in memory
        Timers.forgetMemory()
        assertEquals(TimerSnap(TimerMode.FOCUS, false, 25 * min, 36 * min, 1500, 300), Timers.all(ctx)[id]!!.snap())
    }

    @Test fun offOnlyStopsTheAlert() {
        start(0)
        Timers.onAlarm(ctx, id, 25 * min, visible = false)
        fake.log.clear()
        val before = Timers.changed.value
        Timers.act(ctx, id, TimerAction.OFF, 26 * min)
        assertEquals(listOf("clearEnd $id"), fake.log)
        assertEquals(before, Timers.changed.value)
        assertFalse(Timers.all(ctx)[id]!!.running)
    }
}
