package kr.dot.sb.platform

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.TimerAlert
import kr.dot.sb.settings.ConfigStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** r15 §5: a countdown that ends asks for the chosen alert once (3 rounds), rising after focus, falling after a break. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerAlertTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val looper get() = shadowOf(Looper.getMainLooper())

    private object Host : HostCallbacks {
        override fun finish() {}
        override fun setBrightness(b: Float) {}
        override fun unlockThen(block: () -> Unit) = block()
        override fun openGlow() {}
    }

    @Before fun reset() = ConfigStore.forgetMemory()

    @Test fun endOfFocusThenBreakEachAskOnce() {
        ConfigStore.update(ctx) { it.copy(focusMinutes = 1, breakMinutes = 1, timerAlert = TimerAlert.RISING, displayOff = DisplayOff.NEVER) }
        val calls = mutableListOf<Triple<TimerAlert, Boolean, Int>>()
        val h = StandbyStateHolder(ctx, Host, ownRotation = false, alerts = { a, falling, times -> calls += Triple(a, falling, times) })
        h.start(); looper.idle()
        val id = "alert-test"
        h.timerToggle(id)
        looper.idleFor(Duration.ofSeconds(30))
        assertEquals(emptyList<Any>(), calls)
        looper.idleFor(Duration.ofSeconds(35))
        assertEquals(listOf(Triple(TimerAlert.RISING, false, 3)), calls)
        h.timerToggle(id) // the break
        looper.idleFor(Duration.ofSeconds(65))
        assertEquals(listOf(Triple(TimerAlert.RISING, false, 3), Triple(TimerAlert.RISING, true, 3)), calls)
        h.stop()
    }

    @Test fun vibrationRepeatsThePatternOnTheSoundsBeat() {
        // [on, off, on] every 1500 ms, 3 times: leading 0 delay, then the gap to the next round's start
        assertArrayEquals(longArrayOf(0, 90, 80, 90, 1240, 90, 80, 90, 1240, 90, 80, 90), TimerSound.waveform(longArrayOf(90, 80, 90), 1500, 3))
        assertArrayEquals(longArrayOf(0, 400), TimerSound.waveform(longArrayOf(400), 2100, 1))
    }
}
