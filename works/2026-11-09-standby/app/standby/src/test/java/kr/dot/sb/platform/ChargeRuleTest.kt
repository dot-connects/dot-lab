package kr.dot.sb.platform

import android.os.BatteryManager
import kr.dot.sb.data.ChargeGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Automatic-surface eligibility and orientation without an Android runtime. */
class ChargeRuleTest {
    @Test fun automaticRuleCoversEveryOptionAndChargerCombination() {
        val chargers = listOf(0, BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS)
        val anyCharger = listOf("충전 아님", null, null, null)
        val wirelessOnly = listOf("충전 아님", "유선 충전이라 건너뜀", "유선 충전이라 건너뜀", null)
        for (on in listOf(false, true)) {
            for (wireless in listOf(false, true)) {
                val expected = if (!on) List(4) { "옵션 꺼짐" } else if (wireless) wirelessOnly else anyCharger
                assertEquals("on=$on wireless=$wireless", expected, chargers.map { ChargeRules.allowed(on, it, wireless) })
            }
        }
    }

    @Test fun movingPortraitDoesNotConfirmLandscape() {
        val samples = List(20) { i -> floatArrayOf(0.5f, if (i % 2 == 0) 8f else 12f, 0f) }
        assertEquals(ChargeGate.Posture.MOVING, ChargeGate.posture(samples, ChargeGate.Opts(landscapeOnly = true)))
        assertFalse(ChargeGate.landscape(samples))
    }

    @Test fun movingLandscapeConfirmsEitherLandscapeDirection() {
        for (direction in listOf(-1f, 1f)) {
            val samples = List(20) { i -> floatArrayOf(direction * (if (i % 2 == 0) 8f else 12f), 0.5f, 0f) }
            assertEquals(ChargeGate.Posture.MOVING, ChargeGate.posture(samples, ChargeGate.Opts(landscapeOnly = true)))
            assertTrue(ChargeGate.landscape(samples))
        }
    }

    @Test fun missingSamplesNeverConfirmLandscape() {
        assertFalse(ChargeGate.landscape(emptyList()))
    }
}
