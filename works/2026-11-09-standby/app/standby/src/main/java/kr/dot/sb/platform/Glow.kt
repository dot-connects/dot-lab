package kr.dot.sb.platform

import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.dot.sb.model.NightMode
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.settings.SettingsActivity

/**
 * Pre-alarm glow: our own exact alarm N minutes before the user's next alarm. It is a real alarm clock entry
 * (the probe confirmed setAlarmClock may start a full-screen intent), so [nextAlarmMs] hides it again.
 * ponytail: if the user moves their alarm later while ours is armed, ours still fires at the old time until the
 * next alarm-clock change re-arms it; re-reading the real time would need the Clock app's private data.
 */
object GlowScheduler {
    private const val PRE = "kr.dot.sb.GLOW_PRE"
    private const val PREFS = "sb_glow"

    private fun firePi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 10, Intent(ctx, GlowReceiver::class.java).setAction(PRE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ours(ctx: Context, info: AlarmManager.AlarmClockInfo) = info.showIntent?.creatorPackage == ctx.packageName

    /** The user's real next alarm (ours is hidden: its real time was stored when it was armed). */
    fun nextAlarmMs(ctx: Context): Long? {
        val info = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
        if (!ours(ctx, info)) return info.triggerTime
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("real", 0L).takeIf { it > 0 }
    }

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val mins = ConfigStore.flow(ctx).value.glowStartMinBefore
        val info = am.nextAlarmClock
        if (info != null && ours(ctx, info) && mins > 0) return // already armed
        am.cancel(firePi(ctx))
        if (mins <= 0 || info == null || !am.canScheduleExactAlarms()) return
        val at = info.triggerTime - mins * 60_000L
        if (at <= System.currentTimeMillis()) return
        val show = PendingIntent.getActivity(ctx, 11, Intent(ctx, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("real", info.triggerTime).apply()
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), firePi(ctx))
    }
}

class GlowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showIfInner(context)
    }

    companion object {
        /** Fold inner screen = a hinge phone whose active display is >= 600 dp (spec M2: folded = cover alarm only). */
        fun isInner(ctx: Context): Boolean =
            ctx.resources.configuration.smallestScreenWidthDp >= 600 &&
                ctx.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

        /** Full-screen-intent notification: the only way to light a screen over the lock screen (confirmed by the probe). */
        fun showIfInner(ctx: Context) { if (isInner(ctx)) post(ctx) }

        fun post(ctx: Context) {
            Channels.ensure(ctx)
            val open = PendingIntent.getActivity(
                ctx, 12, Intent(ctx, GlowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
            )
            val n = Notification.Builder(ctx, Channels.GLOW)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("무드등")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(10 * 60_000L)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(7, n)
        }
    }
}

/** Warm full-screen light on the Fold inner screen over the lock screen; plain Views, no Compose (light). */
class GlowActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var ramp: ValueAnimator? = null
    private lateinit var glow: View
    private var night = false
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val cfg = ConfigStore.flow(this).value
        val top = cfg.glowColor.toInt() or (0xFF shl 24)
        val bottom = Color.rgb((Color.red(top) * .45f).toInt(), (Color.green(top) * .3f).toInt(), (Color.blue(top) * .25f).toInt())
        glow = View(this).apply {
            alpha = 0f
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(glow, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { finish() } // tap = back to the board
        }
        if (ListenerBus.alarm.value != null) root.addView(buttons(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(48, 48, 48, 96) })
        setContentView(root)

        // night target comes from the first lux reading (spec M2: night 0.35, day 0.9); no reading in 400 ms = day
        val sm = getSystemService(SensorManager::class.java)
        val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                sm.unregisterListener(this)
                night = cfg.nightMode != NightMode.OFF && e.values[0] <= cfg.nightEnterLux
                startRamp(cfg.glowRampSec)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (light != null) {
            sm.registerListener(l, light, SensorManager.SENSOR_DELAY_FASTEST)
            handler.postDelayed({ sm.unregisterListener(l); startRamp(cfg.glowRampSec) }, 400)
        } else startRamp(cfg.glowRampSec)
        handler.postDelayed({ finish() }, 10 * 60_000L) // M2: until Stop/Snooze or 10 min

        var seen = false
        lifecycleScope.launch {
            ListenerBus.alarm.collect { a -> if (a != null) seen = true else if (seen) finish() }
        }
    }

    private fun startRamp(sec: Int) {
        if (started) return
        started = true
        val target = if (night) 0.35f else 0.9f
        val cap = ConfigStore.flow(this).value.nightMaxBrightness
        window.attributes = window.attributes.apply { screenBrightness = if (night) cap else 0.8f }
        ramp = ValueAnimator.ofFloat(0f, target).apply {
            duration = sec.coerceAtLeast(1) * 1000L
            interpolator = LinearInterpolator()
            addUpdateListener { glow.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun buttons() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        fun pill(text: String, bg: Int, fg: Int, onClick: () -> Unit) = TextView(this@GlowActivity).apply {
            this.text = text; textSize = 22f; setTextColor(fg); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(bg); cornerRadius = 200f }
            setOnClickListener { onClick(); finish() }
        }
        val lp = { LinearLayout.LayoutParams(0, 220, 1f).apply { setMargins(16, 0, 16, 0) } }
        addView(pill("다시 알림", Color.parseColor("#F2A65E"), Color.BLACK) { ListenerBus.snooze(this@GlowActivity) }, lp())
        addView(pill("중단", Color.parseColor("#35508F"), Color.WHITE) { ListenerBus.stop(this@GlowActivity) }, lp())
    }

    override fun onDestroy() {
        ramp?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
