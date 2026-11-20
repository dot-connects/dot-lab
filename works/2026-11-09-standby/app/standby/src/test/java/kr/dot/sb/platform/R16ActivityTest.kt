package kr.dot.sb.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
import kr.dot.sb.settings.ConfigStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** plan-r16 T4 (operator Q1): any screen-off ends the session; covered on the lock screen keeps it; the flag follows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class R16ActivityTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() {
        ConfigStore.forgetMemory()
        ConfigStore.update(ctx) { it.copy(nightMode = NightMode.OFF, displayOff = DisplayOff.NEVER) }
        @Suppress("DEPRECATION")
        ctx.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }

    private fun build(extra: String) = Robolectric.buildActivity(
        StandbyActivity::class.java, Intent(ctx, StandbyActivity::class.java).putExtra(extra, true),
    ).setup()

    private fun interactive(on: Boolean) = shadowOf(ctx.getSystemService(PowerManager::class.java)).setIsInteractive(on)
    private fun locked(on: Boolean) = shadowOf(ctx.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(on)
    private fun keepsOn(a: StandbyActivity) = a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    @Test fun screenOffEndsTheSession() {
        val c = build(StandbyActivity.EXTRA_TILE)
        assertTrue(keepsOn(c.get()))
        interactive(false); locked(true)
        c.pause().stop()
        assertTrue(c.get().isFinishing)
    }

    @Test fun coveredOnTheLockScreenKeepsIt() {
        val c = build(StandbyActivity.EXTRA_TILE)
        interactive(true); locked(true)
        c.pause().stop()
        assertFalse(c.get().isFinishing)
    }

    @Test fun unlockedAndGoneEndsIt() {
        val c = build(StandbyActivity.EXTRA_TILE)
        interactive(true); locked(false)
        c.pause().stop()
        assertTrue(c.get().isFinishing)
    }

    @Test fun thePreviewNeverHoldsTheFlag() {
        val c = build(StandbyActivity.EXTRA_SHOW_NOW)
        assertFalse(keepsOn(c.get()))
    }
}
