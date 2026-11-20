package kr.dot.sb.platform

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
import kr.dot.sb.settings.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** plan-r16 T2/T3/T6 wiring: unplug debounce, keep-screen-on follows the off state, no lux = the 20 s rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class R16HolderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val looper get() = shadowOf(Looper.getMainLooper())

    private class FakeHost : HostCallbacks {
        var finished = 0
        val keep = mutableListOf<Boolean>()
        val bright = mutableListOf<Float>()
        override fun finish() { finished++ }
        override fun setBrightness(b: Float) { bright += b }
        override fun setKeepScreenOn(on: Boolean) { keep += on }
        override fun unlockThen(block: () -> Unit) = block()
        override fun openGlow() {}
    }

    @Suppress("DEPRECATION")
    private fun plugged(p: Int) = ctx.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, p))

    private fun unplug() = ctx.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))

    @Before fun reset() {
        ConfigStore.forgetMemory()
        ConfigStore.update(ctx) { it.copy(nightMode = NightMode.OFF, displayOff = DisplayOff.NEVER) }
        plugged(BatteryManager.BATTERY_PLUGGED_WIRELESS)
    }

    @Test fun aPadFlapKeepsTheSession() {
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false)
        h.start(); looper.idle()
        unplug()
        looper.idleFor(Duration.ofMillis(3_100)) // power is back by the time the check runs
        assertEquals(0, host.finished)
        h.stop()
    }

    @Test fun aRealUnplugEndsItOnce() {
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false)
        h.start(); looper.idle()
        plugged(0); unplug()
        looper.idleFor(Duration.ofMillis(2_900))
        assertEquals(0, host.finished)
        looper.idleFor(Duration.ofMillis(300))
        assertEquals(1, host.finished)
        h.stop()
    }

    @Test fun after20sClearsKeepScreenOnAndATouchSetsItAgain() {
        ConfigStore.update(ctx) { it.copy(displayOff = DisplayOff.AFTER_20S) }
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false)
        h.start(); looper.idle()
        assertEquals(listOf(true), host.keep)
        looper.idleFor(Duration.ofSeconds(21))
        assertEquals(listOf(true, false), host.keep)
        assertEquals(Brightness.OFF, host.bright.last(), 0f)
        h.touch()
        assertEquals(listOf(true, false, true), host.keep)
        h.stop()
    }

    @Test fun autoWithoutALightSensorGoesOffAt20s() {
        ConfigStore.update(ctx) { it.copy(displayOff = DisplayOff.AUTO) }
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false)
        h.start(); looper.idle()
        looper.idleFor(Duration.ofSeconds(21))
        assertEquals(listOf(true, false), host.keep)
        h.stop()
    }

    @Test fun thePreviewNeverKeepsTheScreenOn() {
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false, showNow = true)
        h.start(); looper.idle()
        h.touch()
        looper.idleFor(Duration.ofSeconds(30))
        assertFalse(true in host.keep)
        h.stop()
    }
}
