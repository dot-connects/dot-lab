package kr.dot.sb.platform

import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
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
import kr.dot.sb.data.HeatRule
import kr.dot.sb.data.MoodLatch
import kr.dot.sb.data.MoodPhase
import kr.dot.sb.data.MoodTimer
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

/** Who asked for the glow: only the unfold mood light has the auto-off timer (r10). Stored in the intent as its name. */
enum class GlowSource {
    UNFOLD, TEST, PRE_ALARM, ALARM;

    companion object {
        const val EXTRA = "source"
        fun of(i: Intent?): GlowSource = i?.getStringExtra(EXTRA)?.let { s -> entries.firstOrNull { it.name == s } } ?: UNFOLD
    }
}

class GlowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showIfInner(context, GlowSource.PRE_ALARM)
    }

    companion object {
        /** Fold inner screen = a hinge phone whose active display is >= 600 dp (spec M2: folded = cover alarm only). */
        fun isInner(ctx: Context): Boolean =
            ctx.resources.configuration.smallestScreenWidthDp >= 600 &&
                ctx.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

        /** Full-screen-intent notification: the only way to light a screen over the lock screen (confirmed by the probe). */
        fun showIfInner(ctx: Context, source: GlowSource) { if (isInner(ctx) && charging(ctx)) post(ctx, source) } // unplugged = StandBy is not running: no glow

        private fun charging(ctx: Context): Boolean =
            ctx.getSystemService(BatteryManager::class.java).isCharging

        private val lastPostAt = HashMap<GlowSource, Long>() // main thread only (receivers, dream)

        /**
         * One glow per unfold: the dream's config change and its detach both ask for it (spec M1); debounced per
         * [source], one PendingIntent per source so a pre-alarm does not reuse the unfold intent's extras. An unfold
         * after the mood light's timer ran out stays dark until the phone is folded again ([MoodLatch]).
         */
        fun post(ctx: Context, source: GlowSource) {
            if (source == GlowSource.UNFOLD && MoodLatch.done) return
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - (lastPostAt[source] ?: Long.MIN_VALUE / 2) < 5_000) return
            lastPostAt[source] = now
            Channels.ensure(ctx)
            val open = PendingIntent.getActivity(
                ctx, 20 + source.ordinal,
                Intent(ctx, GlowActivity::class.java).putExtra(GlowSource.EXTRA, source.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
    private var lightSm: SensorManager? = null
    private var lightL: SensorEventListener? = null
    /** Ramp level (0..target) times the auto-off fade [moodK] = the glow's alpha. */
    private var level = 0f
    private var moodK = 1f
    /** Unfold mood light auto-off (r10): minutes (0 = off) and the start of the current run; a ringing alarm cancels it. */
    private var moodMin = 0
    private var moodFrom = 0L
    /** Window brightness before the heat cap; null until the ramp starts. */
    private var baseBrightness: Float? = null
    private var hot = false
    private val batteryRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } ?: return
            val h = HeatRule.next(t, hot)
            if (h != hot) { hot = h; applyBrightness() }
        }
    }
    private val moodTick = Runnable { moodStep() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val cfg = ConfigStore.flow(this).value
        if (GlowSource.of(intent) == GlowSource.UNFOLD) moodMin = cfg.moodAutoOffMin
        moodFrom = android.os.SystemClock.elapsedRealtime()
        registerReceiver(batteryRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_NOT_EXPORTED)
        val top = cfg.glowColor.toInt() or (0xFF shl 24)
        val bottom = Color.rgb((Color.red(top) * .45f).toInt(), (Color.green(top) * .3f).toInt(), (Color.blue(top) * .25f).toInt())
        glow = View(this).apply {
            alpha = 0f
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(glow, FrameLayout.LayoutParams(-1, -1))
            // tap = back to the board; during the auto-off fade a tap restarts the timer instead
            setOnClickListener { if (moodK < 1f) { moodFrom = android.os.SystemClock.elapsedRealtime(); moodStep() } else finish() }
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
            lightSm = sm; lightL = l
            sm.registerListener(l, light, SensorManager.SENSOR_DELAY_FASTEST)
            handler.postDelayed({ sm.unregisterListener(l); startRamp(cfg.glowRampSec) }, 400)
        } else startRamp(cfg.glowRampSec)
        if (ListenerBus.alarm.value != null) handler.postDelayed({ finish() }, 10 * 60_000L) // M2: until Stop/Snooze or 10 min (plain mood light: until tapped)

        var seen = false
        lifecycleScope.launch {
            ListenerBus.alarm.collect { a -> if (a != null) { seen = true; cancelMood() } else if (seen) finish() }
        }
        moodStep()
    }

    /** One auto-off step: sleep until the fade starts, then step the fade, then black and gone. */
    private fun moodStep() {
        handler.removeCallbacks(moodTick)
        val el = android.os.SystemClock.elapsedRealtime() - moodFrom
        when (val p = MoodTimer.phase(el, moodMin)) {
            MoodPhase.On -> {
                moodK = 1f; render()
                if (moodMin > 0) handler.postDelayed(moodTick, moodMin * 60_000L - MoodTimer.FADE_MS - el)
            }
            is MoodPhase.Fade -> { moodK = p.alpha; render(); handler.postDelayed(moodTick, 250) }
            MoodPhase.Done -> {
                MoodLatch.done = true
                moodK = 0f; render()
                finish()
            }
        }
    }

    private fun cancelMood() { moodMin = 0; moodStep() }

    private fun render() { glow.alpha = level * moodK }

    private fun applyBrightness() {
        val b = baseBrightness ?: return
        window.attributes = window.attributes.apply { screenBrightness = Brightness.applied(b, ConfigStore.flow(this@GlowActivity).value, hot) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // the pre-alarm / alarm glow takes over an open unfold mood light: no auto-off then
        val s = GlowSource.of(intent)
        if (s == GlowSource.PRE_ALARM || s == GlowSource.ALARM) cancelMood()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.smallestScreenWidthDp < 600) MoodLatch.done = false // folded
    }

    private fun startRamp(sec: Int) {
        if (started) return
        started = true
        val target = if (night) 0.35f else 0.9f
        val cap = ConfigStore.flow(this).value.nightMaxBrightness
        baseBrightness = if (night) cap else 0.8f
        applyBrightness()
        ramp = ValueAnimator.ofFloat(0f, target).apply {
            duration = sec.coerceAtLeast(1) * 1000L
            interpolator = LinearInterpolator()
            addUpdateListener { level = it.animatedValue as Float; render() }
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
        lightL?.let { lightSm?.unregisterListener(it) }
        runCatching { unregisterReceiver(batteryRx) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
