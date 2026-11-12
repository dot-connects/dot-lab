package kr.dot.sb.platform

import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.screens.barFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plug-in launch decision and window brightness rules (pure JVM). */
class ChargeBrightnessTest {
    private fun decide(
        on: Boolean = true, charging: Boolean = true, interactive: Boolean = false, locked: Boolean = true,
        dream: Boolean = false, activity: Boolean = false, notif: Boolean = true, fsi: Boolean = true,
    ) = ChargeRules.decide(on, charging, interactive, locked, dream, activity, notif, fsi)

    @Test fun screenOffLockedLaunches() = assertEquals(ChargeVerdict.LAUNCH, decide())
    @Test fun screenOnButLockedLaunches() = assertEquals(ChargeVerdict.LAUNCH, decide(interactive = true, locked = true))
    @Test fun screenOffAndUnlockedLaunches() = assertEquals(ChargeVerdict.LAUNCH, decide(interactive = false, locked = false))
    @Test fun usingTheUnlockedPhoneSkips() = assertEquals(ChargeVerdict.USING_PHONE, decide(interactive = true, locked = false))
    @Test fun optionOffSkips() = assertEquals(ChargeVerdict.OPTION_OFF, decide(on = false))
    @Test fun notChargingSkips() = assertEquals(ChargeVerdict.NOT_CHARGING, decide(charging = false))
    @Test fun dreamOrActivityAlreadyShowingSkips() {
        assertEquals(ChargeVerdict.ALREADY_SHOWING, decide(dream = true))
        assertEquals(ChargeVerdict.ALREADY_SHOWING, decide(activity = true))
    }
    @Test fun missingPermissionsSkip() {
        assertEquals(ChargeVerdict.NO_NOTIFICATIONS, decide(notif = false))
        assertEquals(ChargeVerdict.NO_FULL_SCREEN, decide(fsi = false))
    }

    @Test fun wiredSkipsOnlyWhenWirelessOnly() {
        assertEquals(ChargeVerdict.NOT_WIRELESS, ChargeRules.decide(true, true, false, true, false, false, true, true, pluggedOk = false))
        assertEquals(ChargeVerdict.NOT_CHARGING, ChargeRules.decide(true, false, false, true, false, false, true, true, pluggedOk = false))
    }

    @Test fun clampKeepsTheBarOffZeroAndNaN() {
        assertEquals(Brightness.MIN, Brightness.clamp(0f))
        assertEquals(Brightness.MIN, Brightness.clamp(-3f))
        assertEquals(Brightness.MIN, Brightness.clamp(Float.NaN))
        assertEquals(1f, Brightness.clamp(4f))
        assertEquals(0.4f, Brightness.clamp(0.4f))
    }

    @Test fun targetPicksOffThenNightThenDay() {
        val c = AppConfig(nightMaxBrightness = 0.25f, dayBrightness = 0.7f)
        assertEquals(Brightness.OFF, Brightness.target(off = true, night = true, c = c))
        assertEquals(0.25f, Brightness.target(off = false, night = true, c = c))
        assertEquals(0.7f, Brightness.target(off = false, night = false, c = c))
    }

    @Test fun dayNullFollowsSystemAuto() =
        assertEquals(Brightness.AUTO, Brightness.target(off = false, night = false, c = AppConfig(dayBrightness = null)))

    @Test fun editLandsInTheValueThatAppliesNow() {
        val c = AppConfig(nightMaxBrightness = 0.3f, dayBrightness = null)
        assertEquals(0.8f, Brightness.store(c, night = false, v = 0.8f).dayBrightness)
        assertEquals(0.3f, Brightness.store(c, night = false, v = 0.8f).nightMaxBrightness)
        assertEquals(0.5f, Brightness.store(c, night = true, v = 0.5f).nightMaxBrightness)
        assertEquals(null, Brightness.store(c, night = true, v = 0.5f).dayBrightness)
        assertEquals(Brightness.MIN, Brightness.store(c, night = true, v = 0f).nightMaxBrightness)
    }

    @Test fun barShowsStoredValueOrMiddleWhileAuto() {
        assertEquals(0.5f, Brightness.shown(night = false, c = AppConfig(dayBrightness = null)))
        assertEquals(0.9f, Brightness.shown(night = false, c = AppConfig(dayBrightness = 0.9f)))
        assertEquals(0.3f, Brightness.shown(night = true, c = AppConfig(nightMaxBrightness = 0.3f)))
    }

    @Test fun barFractionMapsFingerAlongTheBar() {
        // 800 dp wide: bar is 480 dp (cap), from x=160 to x=640
        assertEquals(0f, barFraction(100f, 800f))
        assertEquals(0.5f, barFraction(400f, 800f))
        assertEquals(1f, barFraction(700f, 800f))
        // 400 dp wide: bar is 240 dp from x=80 to x=320
        assertEquals(0.25f, barFraction(140f, 400f), 1e-6f)
    }

    @Test fun heatCapsOnlyTheAppliedWindowValue() {
        val c = AppConfig(nightMaxBrightness = 0.25f, dayBrightness = 0.7f)
        assertEquals(0.35f, Brightness.target(off = false, night = false, c = c, hot = true))
        assertEquals(0.35f, Brightness.target(off = false, night = false, c = c.copy(dayBrightness = null), hot = true)) // AUTO
        assertEquals(0.25f, Brightness.target(off = false, night = true, c = c, hot = true)) // already lower
        assertEquals(Brightness.OFF, Brightness.target(off = true, night = false, c = c, hot = true))
        assertEquals(0.7f, Brightness.target(off = false, night = false, c = c.copy(heatProtect = false), hot = true))
        assertEquals(0.35f, Brightness.applied(0.9f, c, hot = true))
        assertEquals(0.9f, Brightness.applied(0.9f, c, hot = false))
    }
}
