package kr.dot.sb.platform

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kr.dot.sb.R
import kr.dot.sb.data.ChargeGate
import kr.dot.sb.data.StartEvent
import kr.dot.sb.data.StartPolicy
import kr.dot.sb.data.StartState
import kr.dot.sb.model.AppConfig
import kr.dot.sb.settings.ConfigStore

/** Whether StandBy is on screen right now (same process as the job, so a plain flag is enough). */
object StandbyPresence {
    @Volatile var activity = false
    /** A StandbyActivity session exists (maybe stopped under our mood light). */
    @Volatile var alive = false
    /** plan-r16 T4: the StandbyActivity session (any start mode), so the mood light over it can end it on the side key. */
    @Volatile var session: java.lang.ref.WeakReference<android.app.Activity>? = null
}

/** Why plugging in did or did not bring StandBy up; the order of the checks is the order of the `when`. */
enum class ChargeVerdict(val ko: String) {
    LAUNCH("띄움"), OPTION_OFF("옵션 꺼짐"), NOT_CHARGING("충전 아님"), NOT_WIRELESS("유선 충전이라 건너뜀"),
    ALREADY_SHOWING("이미 떠 있음"), USING_PHONE("폰 사용 중이라 건너뜀"),
    NO_NOTIFICATIONS("알림 권한 없음"), NO_FULL_SCREEN("전체 화면 알림 권한 없음"),
}

object ChargeRules {
    /**
     * The one charger rule for every way StandBy starts (plug-in, screen off after use): a charger is
     * there ([plugged] = EXTRA_PLUGGED, 0 = none) and, with 무선 충전일 때만, it is a wireless pad.
     */
    fun chargerOk(plugged: Int, wirelessOnly: Boolean): Boolean =
        plugged != 0 && (!wirelessOnly || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS)

    /** Null allows an automatic surface; manual starts do not use this rule. */
    fun allowed(startOnCharge: Boolean, plugged: Int, wirelessOnly: Boolean): String? = when {
        !startOnCharge -> ChargeVerdict.OPTION_OFF.ko
        plugged == 0 -> ChargeVerdict.NOT_CHARGING.ko
        !chargerOk(plugged, wirelessOnly) -> ChargeVerdict.NOT_WIRELESS.ko
        else -> null
    }

    fun decide(
        optionOn: Boolean, charging: Boolean, interactive: Boolean, locked: Boolean,
        sessionAlive: Boolean, notificationsOk: Boolean, fullScreenOk: Boolean,
        pluggedOk: Boolean = true,
    ): ChargeVerdict = when {
        !optionOn -> ChargeVerdict.OPTION_OFF
        !charging -> ChargeVerdict.NOT_CHARGING
        !pluggedOk -> ChargeVerdict.NOT_WIRELESS
        sessionAlive -> ChargeVerdict.ALREADY_SHOWING
        interactive && !locked -> ChargeVerdict.USING_PHONE // unlocked with the screen on: someone is using the phone
        !notificationsOk -> ChargeVerdict.NO_NOTIFICATIONS
        !fullScreenOk -> ChargeVerdict.NO_FULL_SCREEN
        else -> ChargeVerdict.LAUNCH
    }
}

/**
 * Start StandBy when the charger goes in, without a service. Android starts a dream only when a lit screen times
 * out while charging, so a plug-in with the screen off would show nothing. A persisted charging-constraint job is
 * woken by the system when power arrives (that ends Doze); it posts the same full-screen-intent notification the
 * mood light uses, which is the only way to put an activity over the lock screen from the background.
 *
 * plan-r16 T1: [StartPolicy] decides whether an event may start StandBy at all (plug-in once per plug session, screen
 * off after use). The job is armed only by [sync] and a confirmed unplug, so it never runs again while still plugged.
 */
object ChargeStart {
    const val EXTRA = "kr.dot.sb.CHARGE_START"
    private const val JOB_ID = 4101
    private const val NOTIF_ID = 8
    private const val HANDLED_KEY = "plugHandled"
    private const val USED_KEY = "used"
    /** Last ACTION_SCREEN_ON (elapsedRealtime), to tell a phone with no lock from a real unlock. */
    @Volatile var screenOnAt = 0L

    private fun on(ctx: Context) = ConfigStore.flow(ctx).value.startOnCharge

    private fun arm(ctx: Context, delayMs: Long, replace: Boolean) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (!replace && js.getPendingJob(JOB_ID) != null) return
        val b = JobInfo.Builder(JOB_ID, ComponentName(ctx, ChargeJobService::class.java)).setRequiresCharging(true).setPersisted(true)
        if (delayMs > 0) b.setMinimumLatency(delayMs)
        js.schedule(b.build())
    }

    /** App start, boot, package update, option change: make the job exist iff the option is on. */
    fun sync(ctx: Context) {
        if (on(ctx)) arm(ctx, 0, replace = false) else ctx.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    /**
     * One start event through [StartPolicy]. [sessionAlive] for SCREEN_OFF is what the listener saw when the screen
     * went off (a session finishing on that screen-off still counts). Main thread.
     */
    fun onEvent(ctx: Context, e: StartEvent, sessionAlive: Boolean = StandbyPresence.alive, jobDone: (() -> Unit)? = null): Boolean {
        val p = prefs(ctx)
        // r16b: `used` is persisted too: an app update or process death between unlock and screen-off lost it (operator 11-20)
        val s = StartState(p.getBoolean(USED_KEY, false), p.getBoolean(HANDLED_KEY, false))
        val (n, launch) = StartPolicy.next(s, e, sessionAlive)
        if (n != s) p.edit().putBoolean(USED_KEY, n.used).putBoolean(HANDLED_KEY, n.plugHandled).apply()
        if (e == StartEvent.UNPLUG_CONFIRMED && on(ctx)) arm(ctx, 0, replace = true)
        // plan-r15 C7: a screen-off with no charger has nothing to start (no log line)
        if (e == StartEvent.SCREEN_OFF && plugged(ctx) == 0) return false
        fun b(v: Boolean) = if (v) 1 else 0
        val source = "start 판단 · $e · used=${b(s.used)} handled=${b(s.plugHandled)}"
        if (!launch) {
            if (e != StartEvent.USER_PRESENT) Measure.log(ctx, "$source → 안 띄움${if (sessionAlive) " · 세션 있음" else ""}")
            return false
        }
        return fire(ctx, source, jobDone)
    }

    fun cancelNotification(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)

    /**
     * Sticky ACTION_BATTERY_CHANGED EXTRA_PLUGGED (0 = no power source). r14 D: "is a charger there" is this, not
     * BatteryManager.isCharging, which reads false at the 85 % battery protection stop and while a pad starts up.
     */
    internal fun plugged(ctx: Context): Int =
        ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

    // ---- on-device trail: the last few attempts, shown in settings (the operator has no adb) ----
    private const val LOG_KEY = "trail"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("charge_start", Context.MODE_PRIVATE)
    fun log(ctx: Context, line: String) {
        val t = java.time.LocalTime.now().withNano(0)
        val old = trail(ctx)
        prefs(ctx).edit().putString(LOG_KEY, (listOf("$t  $line") + old).take(8).joinToString("\n")).apply()
    }
    fun trail(ctx: Context): List<String> = prefs(ctx).getString(LOG_KEY, "").orEmpty().lines().filter { it.isNotBlank() }

    private fun verdict(ctx: Context, plugged: Int, cfg: AppConfig = ConfigStore.flow(ctx).value): ChargeVerdict = ChargeRules.decide(
        optionOn = cfg.startOnCharge,
        charging = plugged != 0,
        interactive = ctx.getSystemService(PowerManager::class.java).isInteractive,
        locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        sessionAlive = StandbyPresence.alive,
        notificationsOk = ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        fullScreenOk = ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        pluggedOk = ChargeRules.allowed(cfg.startOnCharge, plugged, cfg.chargeWirelessOnly) == null,
    )

    /** plan-r15 C2: one posture window at a time (the listener and the job both see one plug-in). Main thread only. */
    private var sampling = false
    private var cancelJobWindow: (() -> Unit)? = null

    internal fun stopJob() { cancelJobWindow?.invoke() }
    private const val WINDOW_MS = 15_000L
    private const val STEP_MS = 2_000L

    /**
     * A start [StartPolicy] allowed. [jobDone] = the job path: true = a posture window runs and finishes the job at its
     * end (onStartJob returns this); false = job over.
     */
    private fun fire(ctx: Context, source: String, jobDone: (() -> Unit)? = null): Boolean {
        val path = if (jobDone != null) "job" else "listener"
        fun record(line: String) { Measure.log(ctx, "$source · $path · $line · ${Measure.charge(ctx)}") }
        if (!on(ctx)) { record(ChargeVerdict.OPTION_OFF.ko); return false }
        if (sampling) { record("자세 확인 중이라 건너뜀"); return false }
        val battery = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        // heat protection starts at 42 °C: the log shows how warm a charge start really is
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { " · %.1f°C".format(it / 10f) }.orEmpty()
        val v = verdict(ctx, plugged)
        log(ctx, "$source · ${v.ko}$temp")
        if (Measure.on) record("${v.ko} · plugged=$plugged · ${Measure.grants(ctx)}")
        if (v != ChargeVerdict.LAUNCH) return false
        return gate(ctx, source, jobDone)
    }

    /**
     * plan-r15 C2: start only once the phone lies still and not face down (+ landscape with 가로로 놓였을 때만 켜기):
     * the last second of accelerometer samples, checked every [STEP_MS] for up to [WINDOW_MS]. The decision is taken
     * again at the still sample (someone may have unlocked meanwhile). No sample at all
     * in the window (sensor asleep) = start only when landscape confirmation is not required.
     */
    private fun gate(ctx: Context, source: String, jobDone: (() -> Unit)?): Boolean {
        val app = ctx.applicationContext
        val sm = app.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fun record(line: String) {
            log(app, "$source · $line")
            Measure.log(app, "$source · ${if (jobDone != null) "job" else "listener"} · $line · ${Measure.charge(app)}")
        }
        fun withoutSamples(reason: String) {
            val cfg = ConfigStore.flow(app).value
            val v = verdict(app, plugged(app), cfg)
            when {
                v != ChargeVerdict.LAUNCH -> record("$reason · ${v.ko}")
                cfg.chargeWirelessOnly && cfg.startLandscapeOnly -> record("가로 확인 불가 · $reason → 건너뜀")
                else -> { record("$reason → 띄움"); launch(app) }
            }
        }
        if (sm == null || acc == null) { withoutSamples("센서 없음"); return false }
        val samples = ArrayDeque<Pair<Long, FloatArray>>()
        var any = false
        val handler = Handler(Looper.getMainLooper())
        // listener path: hold the CPU for the window (the job path is kept awake by JobScheduler)
        val wl = if (jobDone == null) runCatching {
            app.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "standby:chargeGate").apply { acquire(WINDOW_MS + 2_000L) }
        }.getOrNull() else null
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                any = true
                val now = SystemClock.elapsedRealtime()
                samples.addLast(now to e.values.copyOf(3))
                while (samples.isNotEmpty() && now - samples.first().first > 1_000L) samples.removeFirst()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        val t0 = SystemClock.elapsedRealtime()
        sampling = true
        fun cleanUp() {
            sm.unregisterListener(l)
            handler.removeCallbacksAndMessages(null)
            sampling = false
            runCatching { if (wl?.isHeld == true) wl.release() }
            if (jobDone != null) cancelJobWindow = null
        }
        fun end() {
            cleanUp()
            jobDone?.invoke()
        }
        val step = object : Runnable {
            override fun run() {
                val el = SystemClock.elapsedRealtime() - t0
                val window = samples.map { it.second }
                val cfg = ConfigStore.flow(app).value
                val opts = ChargeGate.Opts(landscapeOnly = cfg.chargeWirelessOnly && cfg.startLandscapeOnly)
                val p = ChargeGate.posture(window, opts)
                ChargeGate.stats(window)?.let { s ->
                    Measure.log(app, "자세 ${el / 1000}s · ${p.ko} · x=%.1f y=%.1f z=%.1f sd=%.2f n=%d".format(s.x, s.y, s.z, s.std, s.n))
                }
                val noSamples = !any && el >= WINDOW_MS
                if (p == ChargeGate.Posture.STILL || (noSamples && !opts.landscapeOnly)) {
                    // re-check at the still sample: unlocked, unplugged or already up since the trigger = nothing
                    val v = verdict(app, plugged(app), cfg)
                    if (v == ChargeVerdict.LAUNCH) {
                        record("자세 ${if (noSamples) "샘플 없음" else p.ko} → 띄움")
                        launch(app)
                    } else record("자세 확인 뒤 ${v.ko}")
                    end()
                    return
                }
                if (el >= WINDOW_MS) { record("자세 ${p.ko} → 건너뜀"); end(); return }
                handler.postDelayed(this, STEP_MS)
            }
        }
        if (!sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME, handler)) {
            cleanUp()
            withoutSamples("센서 등록 실패")
            return false
        }
        if (jobDone != null) cancelJobWindow = { cleanUp() }
        handler.postDelayed(step, 1_000L)
        return jobDone != null
    }

    /** Post the full-screen intent that brings StandbyActivity up over the lock screen. */
    private fun launch(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        Channels.ensure(ctx)
        val open = PendingIntent.getActivity(
            ctx, 13, Intent(ctx, StandbyActivity::class.java).putExtra(EXTRA, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIF_ID,
            Notification.Builder(ctx, Channels.CHARGE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(2 * 60_000L)
                .build(),
        )
    }
}

class ChargeJobService : JobService() {
    // plan-r15 C2: true while the posture window runs; the window calls jobFinished. Never re-armed here (plan-r16 T1).
    override fun onStartJob(params: JobParameters): Boolean = ChargeStart.onEvent(this, StartEvent.JOB, jobDone = { jobFinished(params, false) })
    override fun onStopJob(params: JobParameters): Boolean { ChargeStart.stopJob(); return false }
}

/** BOOT_COMPLETED / MY_PACKAGE_REPLACED: persisted jobs normally survive, this is the belt for the cases they do not. */
class ChargeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // r16b: an unplug while powered off was never seen; a boot starts a fresh plug session
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ChargeStart.onEvent(context, StartEvent.UNPLUG_CONFIRMED)
        ChargeStart.sync(context)
    }
}
