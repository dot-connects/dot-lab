package kr.dot.standby

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Overnight logger. Records hinge angle, display changes, power/screen/dream broadcasts, the next alarm,
 * alarm-usage audio starting and stopping, and battery every 5 minutes.
 * No wake lock: gaps while the CPU sleeps are part of what we measure.
 */
class LogService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastAngle = -999f
    private val lastDisplay = HashMap<Int, String>()
    private var alarmAudioOn = false

    private val hinge = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val a = e.values[0]
            // hinge is an on-change sensor: log every reported value, the 11-09 run only showed 0 and 90
            if (a != lastAngle) {
                lastAngle = a
                currentAngle = a
                log("hinge", "angle=${"%.1f".format(a)}")
            }
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val displays = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = logDisplay("display_added", id)
        override fun onDisplayRemoved(id: Int) = log("display_removed", "id=$id")
        override fun onDisplayChanged(id: Int) {
            // adaptive refresh (48/60/120 Hz) fires many changes; log only when something else changed
            val d = getSystemService(DisplayManager::class.java).getDisplay(id) ?: return
            val line = Probe.display(this@LogService, d)
            val key = line.replace(Regex(" hz=\\d+"), "")
            if (lastDisplay[id] == key) return
            lastDisplay[id] = key
            log("display_changed", line)
        }
    }

    private val broadcasts = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val extra = when (i.action) {
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> Probe.battery(c)
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> Probe.nextAlarm(c)
                // exported for vendor alarm broadcasts: log primitive extras only, never unparcel arbitrary objects
                else -> runCatching {
                    i.extras?.keySet()?.joinToString(" ") { k ->
                        when (val v = i.extras?.get(k)) {
                            is Number, is Boolean, is String -> "$k=${v.toString().take(80)}"
                            else -> k
                        }
                    }
                }.getOrNull() ?: ""
            }
            log("broadcast", "${i.action} $extra")
        }
    }

    private val audio = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val usages = configs.map { Probe.usageName(it.audioAttributes.usage) }
            val alarm = "ALARM" in usages
            if (alarm != alarmAudioOn) {
                alarmAudioOn = alarm
                log(if (alarm) "alarm_audio_start" else "alarm_audio_stop", "playing=${usages.joinToString("+")}")
            } else {
                log("audio", "playing=${usages.joinToString("+").ifEmpty { "none" }}")
            }
        }
    }

    private val batteryTick = object : Runnable {
        override fun run() {
            log("battery", Probe.battery(this@LogService))
            handler.postDelayed(this, 5 * 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, App.CH_LOG)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("StandBy 측정 기록 중")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        log("service_start", Probe.battery(this) + " " + Probe.nextAlarm(this))
        Probe.allDisplays(this).forEach { log("display_now", it) }

        val sm = getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sensor == null) log("hinge", "no hinge sensor") else sm.registerListener(hinge, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        getSystemService(DisplayManager::class.java).registerDisplayListener(displays, handler)

        val filter = IntentFilter().apply {
            listOf(
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT,
                Intent.ACTION_DREAMING_STARTED, Intent.ACTION_DREAMING_STOPPED,
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
                // unofficial clock-app broadcasts; logged only if a vendor still sends them
                "com.samsung.sec.android.clockpackage.alarm.ALARM_ALERT",
                "com.samsung.sec.android.clockpackage.alarm.ALARM_STOPPED_IN_ALERT",
                "com.android.deskclock.ALARM_ALERT",
                "com.google.android.deskclock.action.ALARM_ALERT",
            ).forEach(::addAction)
        }
        registerReceiver(broadcasts, filter, RECEIVER_EXPORTED)

        getSystemService(AudioManager::class.java).registerAudioPlaybackCallback(audio, handler)
        handler.postDelayed(batteryTick, 5 * 60_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(batteryTick)
        getSystemService(SensorManager::class.java).unregisterListener(hinge)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displays)
        runCatching { unregisterReceiver(broadcasts) }
        getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(audio)
        log("service_stop", Probe.battery(this))
        super.onDestroy()
    }

    private fun logDisplay(kind: String, id: Int) {
        val d = getSystemService(DisplayManager::class.java).getDisplay(id)
        log(kind, if (d == null) "id=$id gone" else Probe.display(this, d))
    }

    private fun log(kind: String, detail: String) = EventLog.write(this, kind, detail)

    companion object {
        @Volatile var running = false
        @Volatile var currentAngle = Float.NaN
    }
}
