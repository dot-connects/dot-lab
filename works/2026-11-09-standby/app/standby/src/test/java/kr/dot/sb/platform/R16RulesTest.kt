package kr.dot.sb.platform

import kr.dot.sb.data.DisplayOffMachine
import kr.dot.sb.data.LuxMedian
import kr.dot.sb.data.StopRule
import kr.dot.sb.data.keepScreenOn
import kr.dot.sb.model.DisplayOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r16 pure rules (section 0 decisions applied): keep-screen-on, stop rule, idle ceiling, lux settle, glow cap. */
class R16RulesTest {
    // ---- T3 (Q1: no armed state) ----

    @Test fun keepScreenOnOnlyWhileLitAndNotAPreview() {
        assertTrue(keepScreenOn(off = false, showNow = false))
        assertFalse(keepScreenOn(off = true, showNow = false))
        assertFalse(keepScreenOn(off = false, showNow = true))
        assertFalse(keepScreenOn(off = true, showNow = true))
    }

    // ---- T4 (Q1: any screen-off ends the session) ----

    private fun stop(interactive: Boolean, locked: Boolean, glow: Boolean = false, config: Boolean = false) =
        StopRule.onStop(interactive = interactive, keyguardLocked = locked, glowCovering = glow, changingConfig = config)

    @Test fun stopRuleTable() {
        assertEquals(StopRule.KEEP, stop(interactive = false, locked = true, config = true))
        assertEquals(StopRule.KEEP, stop(interactive = false, locked = true, glow = true))
        assertEquals(StopRule.FINISH, stop(interactive = false, locked = true)) // side key or our own off: lock screen next
        assertEquals(StopRule.FINISH, stop(interactive = false, locked = false))
        assertEquals(StopRule.KEEP, stop(interactive = true, locked = true)) // alarm, call or the dream stub on top
        assertEquals(StopRule.FINISH, stop(interactive = true, locked = false)) // home, an app opened
    }

    // ---- T5 / T6 / Q4 ----

    @Test fun after20sGoesOffWithNoTouchInBetween() {
        val m = DisplayOffMachine(); m.touch(0)
        assertFalse(m.evaluate(19_999, DisplayOff.AFTER_20S, 500f, 3f, 8f))
        assertTrue(m.evaluate(20_000, DisplayOff.AFTER_20S, 500f, 3f, 8f))
    }

    @Test fun autoWithoutALuxReadingFollowsTheTwentySecondRule() {
        val m = DisplayOffMachine(); m.touch(0)
        assertFalse(m.evaluate(19_999, DisplayOff.AUTO, null, 3f, 8f))
        assertTrue(m.evaluate(20_000, DisplayOff.AUTO, null, 3f, 8f))
    }

    @Test fun autoInABrightRoomGoesOffAfterTenMinutesAndLightDoesNotWakeIt() {
        val m = DisplayOffMachine(); m.touch(0)
        assertFalse(m.evaluate(20_000, DisplayOff.AUTO, 500f, 3f, 8f))
        assertEquals(DisplayOffMachine.BRIGHT_IDLE_MS - 20_000, m.idleLeftMs(20_000, DisplayOff.AUTO))
        assertFalse(m.evaluate(DisplayOffMachine.BRIGHT_IDLE_MS - 1, DisplayOff.AUTO, 500f, 3f, 8f))
        assertTrue(m.evaluate(DisplayOffMachine.BRIGHT_IDLE_MS, DisplayOff.AUTO, 500f, 3f, 8f))
        assertTrue(m.evaluate(DisplayOffMachine.BRIGHT_IDLE_MS + 1_000, DisplayOff.AUTO, 500f, 3f, 8f))
        m.touch(700_000)
        assertFalse(m.evaluate(700_001, DisplayOff.AUTO, 500f, 3f, 8f))
    }

    @Test fun autoDarkOffStillWakesWhenTheLightComesOn() {
        val m = DisplayOffMachine(); m.touch(0)
        assertTrue(m.evaluate(20_000, DisplayOff.AUTO, 1f, 3f, 8f))
        assertFalse(m.evaluate(21_000, DisplayOff.AUTO, 9f, 3f, 8f))
    }

    @Test fun neverStaysOn() {
        val m = DisplayOffMachine(); m.touch(0)
        assertFalse(m.evaluate(DisplayOffMachine.BRIGHT_IDLE_MS * 3, DisplayOff.NEVER, 500f, 3f, 8f))
    }

    @Test fun luxValueSettlesWithoutANewEvent() {
        val l = LuxMedian()
        l.add(0, 200f); l.add(300, 50f); l.add(600, 2f)
        assertEquals(50f, l.value(600)!!, 0f)
        assertEquals(2f, l.value(2_000)!!, 0f) // the sensor went quiet: the newest reading, not a stale middle one
        assertNull(LuxMedian().value(0))
    }

    // ---- T8 ----

    @Test fun glowCaps() {
        assertEquals(10, GlowCap.minutes(GlowSource.ALARM, 20))
        assertEquals(30, GlowCap.minutes(GlowSource.PRE_ALARM, 20))
        assertNull(GlowCap.minutes(GlowSource.UNFOLD, 20))
        assertNull(GlowCap.minutes(GlowSource.TEST, 20))
    }

    // ---- T7 (heat cap; Q3: night default unchanged) ----

    @Test fun heatCapLeavesSystemAutoAlone() {
        val c = kr.dot.sb.model.AppConfig(heatProtect = true)
        assertEquals(Brightness.AUTO, Brightness.applied(Brightness.AUTO, c, hot = true), 0f)
        assertEquals(Brightness.HOT_MAX, Brightness.applied(0.8f, c, hot = true), 0f)
        assertEquals(0.3f, kr.dot.sb.model.AppConfig().nightMaxBrightness, 0f)
    }
}
