package kr.dot.sb.platform

import kr.dot.sb.data.AlarmTakeover
import kr.dot.sb.data.ChargeGate
import kr.dot.sb.data.GlowFire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r15 C1, C2, C4: the pure decisions behind the alarm takeover, the charge gate and the pre-alarm glow. */
class R15BehaviourTest {
    // ---- C1 ----

    @Test fun audioAloneNeverOpensTheTakeover() {
        // our own focus timer tone (USAGE_ALARM) while no clock-app alarm notification exists
        assertNull(AlarmTakeover.pick<String>(ringing = null, clock = null, audio = true))
    }

    @Test fun audioWithAClockNotificationOpensIt() {
        assertEquals("clock", AlarmTakeover.pick(ringing = null, clock = "clock", audio = true))
    }

    @Test fun aClockNotificationWithoutAudioOrRingingStaysQuiet() {
        // the "next alarm" notice of the clock app
        assertNull(AlarmTakeover.pick(ringing = null, clock = "clock", audio = false))
    }

    @Test fun aRingingNotificationOpensItWithoutAudio() {
        assertEquals("ringing", AlarmTakeover.pick(ringing = "ringing", clock = "clock", audio = false))
    }

    // ---- C2 ----

    private fun still(x: Float, y: Float, z: Float, n: Int = 20) = List(n) { floatArrayOf(x, y, z) }

    @Test fun uprightStandStillStarts() {
        assertTrue(ChargeGate.ok(still(0.2f, 9.7f, 0.3f), ChargeGate.Opts()))
    }

    @Test fun flatFaceUpStillStarts() {
        assertTrue(ChargeGate.ok(still(0f, 0f, 9.8f), ChargeGate.Opts()))
    }

    @Test fun faceDownDoesNotStart() {
        assertEquals(ChargeGate.Posture.FACE_DOWN, ChargeGate.posture(still(0f, 0f, -9.8f), ChargeGate.Opts()))
    }

    @Test fun movingDoesNotStart() {
        val shaky = List(20) { i -> if (i % 2 == 0) floatArrayOf(0f, 9.8f, 0f) else floatArrayOf(2f, 11f, 1f) }
        assertEquals(ChargeGate.Posture.MOVING, ChargeGate.posture(shaky, ChargeGate.Opts()))
    }

    @Test fun landscapeOnlyNeedsGravityAlongX() {
        val opts = ChargeGate.Opts(landscapeOnly = true)
        assertTrue(ChargeGate.ok(still(9.6f, 1f, 0.5f), opts))
        assertTrue(ChargeGate.ok(still(-9.6f, 1f, 0.5f), opts))
        assertEquals(ChargeGate.Posture.NOT_LANDSCAPE, ChargeGate.posture(still(0.5f, 9.7f, 0.3f), opts))
        assertEquals(ChargeGate.Posture.NOT_LANDSCAPE, ChargeGate.posture(still(0f, 0f, 9.8f), opts))
    }

    @Test fun noSamplesIsNotStill() {
        // the caller starts as before when the whole window had no sample (sensor asleep)
        assertEquals(ChargeGate.Posture.NONE, ChargeGate.posture(emptyList(), ChargeGate.Opts()))
        assertFalse(ChargeGate.ok(emptyList(), ChargeGate.Opts()))
    }

    // ---- C4 ----

    @Test fun glowFiresOnlyForTheAlarmItWasArmedFor() {
        assertTrue(GlowFire.valid(stored = 1_000L, next = 1_000L, nextIsOurs = false))
        assertFalse(GlowFire.valid(stored = 1_000L, next = 2_000L, nextIsOurs = false)) // moved later
        assertFalse(GlowFire.valid(stored = 1_000L, next = null, nextIsOurs = false)) // deleted or turned off
        assertFalse(GlowFire.valid(stored = 0L, next = 1_000L, nextIsOurs = false)) // never armed
        assertTrue(GlowFire.valid(stored = 1_000L, next = 5L, nextIsOurs = true)) // a sibling flavor's glow hides it
    }
}
