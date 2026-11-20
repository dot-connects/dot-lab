package kr.dot.sb.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.core.ExperimentalWindowApi
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kr.dot.sb.settings.Perm
import kr.dot.sb.settings.Perms

/**
 * plan-r15 Order step 2: the measurement log for the operator's real-phone round (no adb). Separate from the charge
 * trail (8 lines): the B4 grant matrix, the charge-start path and decision, ChargeGate sample summaries and fold
 * posture changes (+ the E7 rear-display spike). Personal build only; the public build writes nothing.
 */
object Measure {
    private const val KEY = "measure"
    private const val MAX = 200
    val on: Boolean get() = kr.dot.sb.BuildConfig.FLAVOR == "personal"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("charge_start", Context.MODE_PRIVATE)

    fun log(ctx: Context, line: String) {
        if (!on) return
        synchronized(this) {
            val t = java.time.LocalDateTime.now().withNano(0).toString().substring(5).replace('T', ' ')
            prefs(ctx).edit().putString(KEY, (listOf("$t  $line") + lines(ctx)).take(MAX).joinToString("\n")).apply()
        }
    }

    fun lines(ctx: Context): List<String> = prefs(ctx).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }

    /** Automatic-start options and battery status at the decision, including protection-charge pauses. */
    fun charge(ctx: Context): String {
        val cfg = kr.dot.sb.settings.ConfigStore.flow(ctx).value
        val battery = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return "opt=${if (cfg.startOnCharge) 1 else 0} wl=${if (cfg.chargeWirelessOnly) 1 else 0} land=${if (cfg.startLandscapeOnly) 1 else 0} status=$status"
    }

    /** The grants a charge start depends on (B4), plus the system's screen-saver switches where readable. */
    fun grants(ctx: Context): String {
        val g = Perms.granted(ctx)
        fun yn(p: Perm) = if (g[p] == true) "O" else "X"
        fun secure(k: String) = runCatching { Settings.Secure.getInt(ctx.contentResolver, k) }.getOrNull()?.toString() ?: "?"
        return "listener=${yn(Perm.NOTIFICATIONS)} post=${yn(Perm.POST)} fsi=${yn(Perm.FULL_SCREEN)} saver_off=${yn(Perm.SCREEN_SAVER)}" +
            " ss_on=${secure("screensaver_enabled")} ss_sleep=${secure("screensaver_activate_on_sleep")}"
    }
}

/**
 * Fold posture probe while an activity is started (personal build): FoldingFeature state, hinge angle, the display the
 * activity is on, and (E7 spike) the rear-display WindowArea capabilities at each change. Log only.
 */
object PostureProbe {
    @OptIn(ExperimentalWindowApi::class)
    fun attach(a: ComponentActivity, where: String) {
        if (!Measure.on) return
        val sm = a.getSystemService(SensorManager::class.java)
        val hinge = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return // not a foldable: nothing to measure
        var angle: Float? = null
        var areas = "?"
        var posture = "?"
        var last = ""
        fun write() {
            val cfg = a.resources.configuration
            val line = "$where 자세 · $posture · 경첩 ${angle?.let { "%.0f°".format(it) } ?: "?"} · " +
                "display ${a.display?.displayId ?: -1} ${cfg.screenWidthDp}x${cfg.screenHeightDp}dp sw${cfg.smallestScreenWidthDp} · 뒷화면 $areas"
            if (line != last) { last = line; Measure.log(a, line) }
        }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { angle = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
        }
        a.lifecycleScope.launch {
            a.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sm.registerListener(l, hinge, SensorManager.SENSOR_DELAY_NORMAL)
                try {
                    launch {
                        runCatching { WindowAreaController.getOrCreate() }.getOrNull()?.windowAreaInfos
                            ?.catch { e -> areas = "오류 ${e.javaClass.simpleName}"; write() }
                            ?.collect { infos -> areas = describe(infos); write() }
                    }
                    WindowInfoTracker.getOrCreate(a).windowLayoutInfo(a)
                        .catch { e -> posture = "오류 ${e.javaClass.simpleName}"; write() }
                        .collect { info ->
                            val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                            // no folding feature in this window = the cover screen (or the inner one fully closed over)
                            posture = f?.let { "${if (it.state == FoldingFeature.State.HALF_OPENED) "반쯤 열림" else "평평"} ${it.orientation} sep=${it.isSeparating}" } ?: "경첩 안 보임"
                            write()
                        }
                } finally {
                    sm.unregisterListener(l)
                }
            }
        }
    }

    @OptIn(ExperimentalWindowApi::class)
    private fun describe(infos: List<WindowAreaInfo>): String {
        if (infos.isEmpty()) return "없음"
        return infos.joinToString("; ") { i ->
            val present = i.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status
            val transfer = i.getCapability(WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA)?.status
            "${i.type} present=$present transfer=$transfer"
        }
    }
}
