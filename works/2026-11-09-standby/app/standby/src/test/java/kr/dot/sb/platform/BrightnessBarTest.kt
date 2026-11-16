package kr.dot.sb.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.Theme
import kr.dot.sb.settings.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import java.time.Duration

/** r14 B7: a bar edit reaches the window, is saved, and the next evaluate keeps it; the bar's perceptual curve. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrightnessBarTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val looper get() = shadowOf(Looper.getMainLooper())

    private class FakeHost : HostCallbacks {
        val set = mutableListOf<Float>()
        override fun finish() {}
        override fun setBrightness(b: Float) { set += b }
        override fun unlockThen(block: () -> Unit) = block()
        override fun openGlow() {}
    }

    @Before fun reset() = ConfigStore.forgetMemory()

    @Test fun dayEditIsKeptAfterSave() {
        ConfigStore.update(ctx) { it.copy(dayBrightness = 0.3f, nightMode = NightMode.OFF, displayOff = DisplayOff.NEVER) }
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false)
        h.start(); looper.idle()
        h.setUserBrightness(0.8f)
        val w = Brightness.fromBar(0.8f)
        assertEquals(w, host.set.last(), 1e-6f)
        looper.idleFor(Duration.ofMillis(600)) // flush: the config change runs evaluate again
        assertEquals(w, ConfigStore.flow(ctx).value.dayBrightness!!, 1e-6f)
        assertEquals(w, host.set.last(), 1e-6f)
        h.stop()
    }

    @Test fun lightThemeNightEditIsKeptAfterSave() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_LIGHT)
        val sm = shadowOf(ctx.getSystemService(SensorManager::class.java))
        sm.addSensor(sensor)
        // 종이 is light: night applies under mode OFF once the room is dark (forced night)
        ConfigStore.update(ctx) { it.copy(theme = Theme.DAYPAPER, nightMode = NightMode.OFF, displayOff = DisplayOff.NEVER, nightMaxBrightness = 0.2f, dayBrightness = 0.9f) }
        val host = FakeHost()
        val h = StandbyStateHolder(ctx, host, ownRotation = false)
        h.start(); looper.idle()
        sm.sendSensorEventToListeners(SensorEventBuilder.newBuilder().setSensor(sensor).setValues(floatArrayOf(0f)).setTimestamp(0).build())
        looper.idleFor(Duration.ofSeconds(7))
        assertEquals(0.2f, host.set.last(), 1e-6f) // night cap applies
        h.setUserBrightness(0.6f)
        val w = Brightness.fromBar(0.6f)
        assertEquals(w, host.set.last(), 1e-6f)
        looper.idleFor(Duration.ofMillis(600))
        val c = ConfigStore.flow(ctx).value
        assertEquals(w, c.nightMaxBrightness, 1e-6f)
        assertEquals(0.9f, c.dayBrightness!!, 1e-6f)
        assertEquals(w, host.set.last(), 1e-6f)
        h.stop()
    }

    @Test fun curveRoundTripsAndIsMonotonic() {
        var last = -1f
        for (i in 0..100) {
            val p = i / 100f
            val v = Brightness.fromBar(p)
            assertTrue(v > last); last = v
            assertEquals(p, Brightness.toBar(v), 1e-4f)
        }
        assertEquals(Brightness.MIN, Brightness.fromBar(0f), 1e-6f)
        assertEquals(1f, Brightness.fromBar(1f), 1e-6f)
        assertTrue(Brightness.fromBar(0.5f) < 0.3f) // middle of the bar is well under half the window value
    }

    @Test fun autoOffAndBelowMinGiveAFiniteBar() {
        assertEquals(Brightness.toBar(0.5f), Brightness.toBar(Brightness.AUTO), 1e-6f)
        assertEquals(0f, Brightness.toBar(Brightness.OFF), 1e-6f)
        assertEquals(0f, Brightness.toBar(0f), 1e-6f)
        assertEquals(0f, Brightness.toBar(Float.NaN), 1e-6f)
    }
}
