package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.Layout
import kr.dot.sb.platform.WidgetFit
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
        val missing = listOf(Perm.POST, Perm.CALENDAR, Perm.PHOTOS)
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

    // ---- B10 widget fit ----

    @Test fun resizableWidgetsUseTheirResizeMinimum() {
        assertEquals(110f, WidgetFit.min(250f, 110f, resizable = true))
        assertEquals(250f, WidgetFit.min(250f, 110f, resizable = false))
        assertEquals(250f, WidgetFit.min(250f, 0f, resizable = true)) // unset
        assertEquals(250f, WidgetFit.min(250f, 300f, resizable = true)) // larger than minWidth: ignored
    }

    @Test fun aWidgetFitsWhenItsMinimumFitsOneOfTheSlotSizes() {
        val slots = listOf(WidgetFit.Size(330f, 200f), WidgetFit.Size(200f, 330f))
        assertTrue(WidgetFit.fits(WidgetFit.Size(300f, 180f), slots))
        assertTrue(WidgetFit.fits(WidgetFit.Size(180f, 300f), slots))
        assertFalse(WidgetFit.fits(WidgetFit.Size(300f, 300f), slots))
        assertTrue(WidgetFit.fits(WidgetFit.Size(330f, 200f), slots)) // exactly the slot
        assertFalse(WidgetFit.fits(WidgetFit.Size(10f, 10f), emptyList()))
    }

    @Test fun configureOnlyWhenRequired() {
        assertFalse(WidgetFit.needsConfigure(hasConfigure = false, optional = false, reconfigurable = false))
        assertTrue(WidgetFit.needsConfigure(hasConfigure = true, optional = false, reconfigurable = false))
        assertTrue(WidgetFit.needsConfigure(hasConfigure = true, optional = true, reconfigurable = false)) // could never be set later
        assertTrue(WidgetFit.needsConfigure(hasConfigure = true, optional = false, reconfigurable = true))
        assertFalse(WidgetFit.needsConfigure(hasConfigure = true, optional = true, reconfigurable = true))
    }

    @Test fun resizableFillsFixedSitsAtItsOwnSize() {
        assertEquals(WidgetFit.Placement(300f, 200f, 1f), WidgetFit.place(300f, 200f, 120f, 80f, resizeW = true, resizeH = true))
        assertEquals(WidgetFit.Placement(120f, 80f, 1f), WidgetFit.place(300f, 200f, 120f, 80f, resizeW = false, resizeH = false))
        assertEquals(WidgetFit.Placement(300f, 80f, 1f), WidgetFit.place(300f, 200f, 120f, 80f, resizeW = true, resizeH = false))
    }

    @Test fun neverLaidOutBelowTheMinimumScaledInstead() {
        // portrait slot narrower than the minimum: laid out at 250 wide, drawn at 200/250
        assertEquals(WidgetFit.Placement(250f, 300f, 0.8f), WidgetFit.place(200f, 300f, 250f, 110f, resizeW = true, resizeH = true))
        val p = WidgetFit.place(100f, 100f, 200f, 400f, resizeW = false, resizeH = false)
        assertEquals(0.25f, p.scale, 1e-6f) // the tighter axis wins
    }

    @Test fun slotSizesAreTheLandscapeAndPortraitSlotLessTheCardInset() {
        val s = WidgetFit.slotSizes(Layout.TWO, 0, PreviewDevice.BAR.w.toFloat(), PreviewDevice.BAR.h.toFloat(), cover = false)
        assertEquals(2, s.size)
        val land = LayoutGeo.slots(Layout.TWO, 780f, 360f)[0]
        val port = LayoutGeo.slots(Layout.TWO, 360f, 780f)[0]
        assertEquals(land.w - 2 * WidgetFit.inset(land.w, land.h), s[0].w, 1e-3f)
        assertEquals(port.h - 2 * WidgetFit.inset(port.w, port.h), s[1].h, 1e-3f)
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
