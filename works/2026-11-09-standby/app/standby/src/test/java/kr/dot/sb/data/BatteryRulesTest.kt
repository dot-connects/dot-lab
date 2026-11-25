package kr.dot.sb.data

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** 배터리 card: power units, the speed label thresholds, protection, the time-to-full text. */
class BatteryRulesTest {
    private val charging = BatteryManager.BATTERY_STATUS_CHARGING
    private val notCharging = BatteryManager.BATTERY_STATUS_NOT_CHARGING

    @Test fun wattsNormaliseUnits() {
        // 1.5 A at 4.0 V = 6 W, whichever unit the phone reports
        assertEquals(6f, BatteryRules.watts(1_500_000, 4000), 0.01f) // µA, mV
        assertEquals(6f, BatteryRules.watts(1500, 4000), 0.01f) // mA, mV
        assertEquals(6f, BatteryRules.watts(-1_500_000, 4000), 0.01f) // negative = charging on some phones
        assertEquals(6f, BatteryRules.watts(1500, 4), 0.01f) // V
        assertEquals(6f, BatteryRules.watts(1_500_000, 4_000_000), 0.01f) // µV
    }

    @Test fun wattsUnknown() {
        assertEquals(0f, BatteryRules.watts(0, 4000), 0f)
        assertEquals(0f, BatteryRules.watts(Int.MIN_VALUE, 4000), 0f)
        assertEquals(0f, BatteryRules.watts(1500, 0), 0f)
    }

    @Test fun speedThresholds() {
        fun k(w: Float, pct: Int = 50) = BatteryRules.kind(true, pct, charging, w)
        assertEquals(ChargeKind.SLOW, k(4.5f))
        assertEquals(ChargeKind.SLOW, k(7.4f))
        assertEquals(ChargeKind.CHARGING, k(7.5f))
        assertEquals(ChargeKind.CHARGING, k(9.9f))
        assertEquals(ChargeKind.FAST, k(10f))
        assertEquals(ChargeKind.FAST, k(20f))
        assertEquals(ChargeKind.SUPER, k(20.5f))
        // near full the current drops on its own: not "느린"
        assertEquals(ChargeKind.CHARGING, k(4.5f, pct = 92))
        // no reading: plain 충전
        assertEquals(ChargeKind.CHARGING, k(0f))
        assertEquals(ChargeKind.UNPLUGGED, BatteryRules.kind(false, 50, BatteryManager.BATTERY_STATUS_DISCHARGING, 0f))
    }

    @Test fun protectAndFull() {
        assertEquals(ChargeKind.PROTECT, BatteryRules.kind(true, 85, notCharging, 0f))
        assertEquals(ChargeKind.PROTECT, BatteryRules.kind(true, 85, BatteryManager.BATTERY_STATUS_FULL, 0f))
        // still charging at 85: not protection
        assertEquals(ChargeKind.FAST, BatteryRules.kind(true, 85, charging, 15f))
        // not charging well below the band (weak charger, heat): not protection
        assertEquals(ChargeKind.CHARGING, BatteryRules.kind(true, 40, notCharging, 0f))
        assertEquals(ChargeKind.FULL, BatteryRules.kind(true, 100, BatteryManager.BATTERY_STATUS_FULL, 0f))
        assertEquals(ChargeKind.UNPLUGGED, BatteryRules.kind(false, 85, notCharging, 0f))
    }

    @Test fun leftText() {
        assertEquals("1시간 12분", BatteryRules.left(72 * 60_000L))
        assertEquals("2시간", BatteryRules.left(120 * 60_000L))
        assertEquals("45분", BatteryRules.left(45 * 60_000L))
        assertEquals("1분", BatteryRules.left(10_000L)) // rounded up, never "0분"
        assertEquals("1시간", BatteryRules.left(59 * 60_000L + 30_000L))
        assertNull(BatteryRules.left(-1))
        assertNull(BatteryRules.left(0))
        assertNull(BatteryRules.left(null))
    }

    @Test fun fullAtClock() {
        val now = LocalDateTime.of(2026, 11, 25, 15, 28)
        assertEquals("16:40", BatteryRules.fullAt(now, 72 * 60_000L))
        assertEquals("00:13", BatteryRules.fullAt(LocalDateTime.of(2026, 11, 25, 23, 58), 15 * 60_000L)) // past midnight
    }

    @Test fun wattText() {
        assertEquals("15", BatteryRules.wattText(15.2f))
        assertEquals("4.5", BatteryRules.wattText(4.5f))
        assertEquals("7", BatteryRules.wattText(7f))
    }

    @Test fun curveOncePerMinuteResetOnUnplug() {
        BatteryLog.record(0, 0, false)
        BatteryLog.record(0, 20, true)
        BatteryLog.record(30_000, 21, true) // same minute: no point
        val c = BatteryLog.record(60_000, 22, true)
        assertEquals(listOf(0L to 20, 60_000L to 22), c)
        assertEquals(emptyList<Pair<Long, Int>>(), BatteryLog.record(90_000, 22, false))
        repeat(BatteryLog.MAX + 5) { BatteryLog.record(it * 60_000L, 30, true) }
        assertEquals(BatteryLog.MAX, BatteryLog.record(Long.MAX_VALUE / 2, 31, true).size)
        BatteryLog.record(0, 0, false)
    }
}
