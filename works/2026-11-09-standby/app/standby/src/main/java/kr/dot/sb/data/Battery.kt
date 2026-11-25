package kr.dot.sb.data

import android.os.BatteryManager
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the battery card says about the charge (mockup battery-card.html round 3, its label line). */
enum class ChargeKind(val ko: String) {
    UNPLUGGED("배터리"), CHARGING("충전"), FAST("고속 충전"), SUPER("초고속 충전"), SLOW("느린 충전"),
    PROTECT("배터리 보호"), FULL("완충");

    val plugged: Boolean get() = this != UNPLUGGED
    /** Still filling (not stopped by protection, not full). */
    val filling: Boolean get() = plugged && this != PROTECT && this != FULL
}

/** The battery card's rules, pure so they run in JVM tests. */
object BatteryRules {
    /**
     * Charge power in W from BATTERY_PROPERTY_CURRENT_NOW and EXTRA_VOLTAGE. Devices disagree on units: current is µA by
     * the docs but mA on some phones, voltage mV but V or µV on a few. 0 = unknown (no reading, Int.MIN_VALUE).
     * ponytail: unit by magnitude (|I| ≥ 10 000 = µA), so a µA trickle under 10 mA reads as amps; it only happens near full.
     */
    fun watts(currentNow: Int, voltage: Int): Float {
        if (currentNow == 0 || currentNow == Int.MIN_VALUE || voltage <= 0) return 0f
        val c = abs(currentNow.toLong())
        val amps = if (c >= 10_000) c / 1e6 else c / 1e3
        val volts = when {
            voltage >= 100_000 -> voltage / 1e6
            voltage >= 100 -> voltage / 1e3
            else -> voltage.toDouble()
        }
        return (amps * volts).toFloat()
    }

    /** Slow charge: under this while not near full. */
    const val SLOW_W = 7.5f
    const val NEAR_FULL = 90

    /**
     * [status] = EXTRA_STATUS. ponytail: Samsung's battery protection is read as "plugged, 80–99 %, and the phone says
     * not charging / full"; One UI does not publish the setting. A heat pause in that band reads as protection too.
     */
    fun kind(plugged: Boolean, pct: Int, status: Int, watts: Float): ChargeKind = when {
        !plugged -> ChargeKind.UNPLUGGED
        pct >= 100 -> ChargeKind.FULL
        pct in 80..99 && (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) -> ChargeKind.PROTECT
        watts <= 0f -> ChargeKind.CHARGING
        watts < SLOW_W && pct < NEAR_FULL -> ChargeKind.SLOW
        watts < 10f -> ChargeKind.CHARGING
        watts <= 20f -> ChargeKind.FAST
        else -> ChargeKind.SUPER
    }

    /** "1시간 12분", "2시간", "45분" (minutes rounded up); null when the phone does not know (-1, 0). */
    fun left(ms: Long?): String? {
        if (ms == null || ms <= 0) return null
        val m = ((ms + 59_999) / 60_000).toInt()
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "${r}분"
            r == 0 -> "${h}시간"
            else -> "${h}시간 ${r}분"
        }
    }

    /** Clock time the battery is full: "16:40" (24 h, a future time must not be ambiguous). */
    fun fullAt(now: LocalDateTime, ms: Long): String = now.plusSeconds((ms + 999) / 1000).let { "%02d:%02d".format(it.hour, it.minute) }

    /** "15" W, "4.5" W under 10. */
    fun wattText(w: Float): String = if (w < 10f) String.format(java.util.Locale.US, "%.1f", w).removeSuffix(".0") else w.roundToInt().toString()
}

/**
 * The charge curve: (epoch ms, %) once a minute since the charger went in, kept in memory while the app runs (it
 * survives dream restarts, not a killed process). Cleared on unplug.
 */
object BatteryLog {
    const val MAX = 600
    private val pts = ArrayList<Pair<Long, Int>>()
    /** When it reached 100 % on this charge (null = not full). */
    var fullSince: Long? = null
        private set

    @Synchronized
    fun record(nowMs: Long, pct: Int, plugged: Boolean): List<Pair<Long, Int>> {
        if (!plugged) { pts.clear(); fullSince = null; return emptyList() }
        if (pct >= 100) { if (fullSince == null) fullSince = nowMs } else fullSince = null
        if (pts.isEmpty() || nowMs - pts.last().first >= 60_000) {
            pts += nowMs to pct
            if (pts.size > MAX) pts.removeAt(0)
        }
        return pts.toList()
    }
}
