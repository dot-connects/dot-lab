package kr.dot.standby

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.os.BatteryManager
import android.os.PowerManager
import android.view.Display
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Snapshots of device state, as one-line strings for the log and the screen. */
object Probe {
    private val hm = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun display(ctx: Context, d: Display): String {
        val m = ctx.createDisplayContext(d).resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val ratio = maxOf(w, h).toFloat() / minOf(w, h)
        val cut = d.cutout?.let { c ->
            "cut=L${c.safeInsetLeft}/T${c.safeInsetTop}/R${c.safeInsetRight}/B${c.safeInsetBottom}"
        } ?: "cut=none"
        return "id=${d.displayId} name=${d.name} state=${stateName(d.state)} ${w}x$h " +
            "ratio=${"%.2f".format(ratio)} dpi=${m.densityDpi} dp=${(w / m.density).toInt()}x${(h / m.density).toInt()} " +
            "rot=${d.rotation * 90} hz=${"%.0f".format(d.refreshRate)} $cut"
    }

    fun allDisplays(ctx: Context): List<String> =
        ctx.getSystemService(DisplayManager::class.java).displays.map { display(ctx, it) }

    private fun stateName(s: Int): String = when (s) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        Display.STATE_VR -> "VR"
        else -> "UNKNOWN($s)"
    }

    fun battery(ctx: Context): String {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "battery=?"
        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val plug = when (b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            0 -> "none"
            else -> "other"
        }
        val currentMa = ctx.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        val thermal = ctx.getSystemService(PowerManager::class.java).currentThermalStatus
        return "level=$level% temp=${temp}C plug=$plug current=${currentMa}mA thermal=$thermal"
    }

    fun nextAlarm(ctx: Context): String {
        val info = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return "nextAlarm=none"
        val owner = info.showIntent?.creatorPackage ?: "?"
        return "nextAlarm=${hm.format(Instant.ofEpochMilli(info.triggerTime))} owner=$owner"
    }

    fun usageName(u: Int): String = when (u) {
        AudioAttributes.USAGE_ALARM -> "ALARM"
        AudioAttributes.USAGE_MEDIA -> "MEDIA"
        AudioAttributes.USAGE_NOTIFICATION -> "NOTIFICATION"
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "RINGTONE"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "VOICE"
        AudioAttributes.USAGE_ASSISTANT -> "ASSISTANT"
        AudioAttributes.USAGE_GAME -> "GAME"
        else -> "usage$u"
    }
}
