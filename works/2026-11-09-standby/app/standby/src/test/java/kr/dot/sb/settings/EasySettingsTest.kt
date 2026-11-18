package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r13 Part B pure logic: the permission walk, the widget fit rules, the inverted night switch, the lux scale. */
class EasySettingsTest {
    // ---- B1 permission walk ----

    @Test fun walkOpensEachMissingPermissionOnceInOrder() {
        val missing = listOf(Perm.DREAM, Perm.POST, Perm.BATTERY)
        var w = PermWalk().advance(null, missing)
        assertEquals(Perm.DREAM, w.current)
        w = w.advance(Perm.DREAM, missing) // DREAM still missing (the user came back without granting): not asked again
        assertEquals(Perm.POST, w.current)
        w = w.advance(Perm.POST, listOf(Perm.DREAM, Perm.BATTERY))
        assertEquals(Perm.BATTERY, w.current)
        w = w.advance(Perm.BATTERY, listOf(Perm.DREAM))
        assertNull(w.current)
        assertTrue(w.done)
    }

    @Test fun aSecondAdvanceForTheSameStepIsIgnored() {
        val missing = listOf(Perm.POST, Perm.CALENDAR, Perm.BATTERY)
        val w = PermWalk().advance(null, missing).advance(Perm.POST, missing)
        assertEquals(Perm.CALENDAR, w.current)
        assertEquals(w, w.advance(Perm.POST, missing)) // the runtime callback and onResume both report POST
    }

    @Test fun nothingMissingEndsAtOnce() {
        val w = PermWalk().advance(null, emptyList())
        assertTrue(w.done)
        assertNull(w.current)
        assertEquals(w, w.advance(null, listOf(Perm.DREAM))) // a finished walk stays finished
    }

    @Test fun nextSkipsWhatWasAsked() {
        assertEquals(Perm.BATTERY, PermWalk.next(listOf(Perm.DREAM, Perm.BATTERY), setOf(Perm.DREAM)))
        assertNull(PermWalk.next(listOf(Perm.DREAM), setOf(Perm.DREAM)))
    }

    @Test fun walkStartsOnlyOnAFreshInstall() {
        assertTrue(PermWalk.starts(fresh = true, done = false, storedVersion = null))
        assertTrue(PermWalk.starts(fresh = true, done = false, storedVersion = 11)) // restored from a backup: still walks
        assertFalse(PermWalk.starts(fresh = false, done = false, storedVersion = null)) // an update without a saved config
        assertFalse(PermWalk.starts(fresh = true, done = false, storedVersion = 10)) // a v10 config = the app was used before
        assertFalse(PermWalk.starts(fresh = true, done = true, storedVersion = null))
    }

    /** plan-r15 F8 처음 설정으로: the first run opens again on an old install, until it is done. */
    @Test fun resetBringsTheFirstRunBackOnce() {
        assertTrue(PermWalk.starts(fresh = false, done = false, storedVersion = 10, again = true))
        assertFalse(PermWalk.starts(fresh = false, done = true, storedVersion = 15, again = true))
    }

    /** plan-r15 F10: the pull-down nudge comes first; a seen tip never comes back; the tent gets only its own. */
    @Test fun tipsNeverRepeat() {
        val clock = TipContext(Pages.CLOCK)
        assertEquals(Tip.PULL_DOWN, Tips.pick(AppConfig(), clock))
        val seen = AppConfig(tipsSeen = setOf(Tip.PULL_DOWN.name))
        assertEquals(Tip.CLOCK_SWIPE, Tips.pick(seen, clock))
        assertEquals(Tip.CLOCK_HOLD, Tips.pick(seen.copy(tipsSeen = seen.tipsSeen + Tip.CLOCK_SWIPE.name), clock))
        assertNull(Tips.pick(AppConfig(tipsSeen = Tip.entries.map { it.name }.toSet()), clock))
        assertEquals(Tip.TENT, Tips.pick(AppConfig(), TipContext(-1, tent = true)))
        assertNull(Tips.pick(AppConfig(tipsSeen = setOf(Tip.TENT.name)), TipContext(-1, tent = true)))
    }

    /** plan-r15 F6: one 밤 감지 slider; the exit threshold always follows at enter + 2 lx (never at or below enter). */
    @Test fun nightDetectKeepsExitAboveEnter() {
        val c = NightDetect.set(AppConfig(nightEnterLux = 3f, nightExitLux = 40f), DarkLevel.of(10f))
        assertEquals(10f, c.nightEnterLux, 0.05f)
        assertEquals(c.nightEnterLux + 2f, c.nightExitLux, 1e-4f)
    }

    // ---- B5 night notification switch (shown inverted, stored meaning unchanged) ----

    @Test fun nightWakeSwitchIsTheInverseOfQuiet() {
        assertFalse(NightWake.shown(AppConfig(nightNotifQuiet = true)))
        assertTrue(NightWake.shown(AppConfig(nightNotifQuiet = false)))
        assertFalse(NightWake.shown(AppConfig(nightNotifQuiet = false, displayOff = DisplayOff.NEVER))) // shown off when always on
        assertEquals(false, NightWake.set(AppConfig(nightNotifQuiet = true), true).nightNotifQuiet)
        assertEquals(true, NightWake.set(AppConfig(nightNotifQuiet = false), false).nightNotifQuiet)
    }

    // ---- B4 어두움 정도: lux shown on a 1–20 log scale, stored as lux ----

    @Test fun darknessLevelMapsLuxBothWays() {
        assertEquals(20f, DarkLevel.of(DarkLevel.LO), 1e-4f) // darkest threshold = 20
        assertEquals(1f, DarkLevel.of(DarkLevel.HI), 1e-4f)
        for (lux in listOf(1f, 2.5f, 5f, 12f, 20f, 40f)) assertEquals(lux, DarkLevel.lux(DarkLevel.of(lux)), 0.06f)
        assertTrue(DarkLevel.of(3f) > DarkLevel.of(8f)) // darker room = higher level
    }
}
