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
import kr.dot.sb.model.AppConfig
import kr.dot.sb.settings.ConfigStore

/** Which StandBy host is on screen right now (same process as the job, so a plain flag is enough). */
object StandbyPresence {
    @Volatile var dream = false
    @Volatile var activity = false
    /** A StandbyActivity exists (maybe stopped behind the lock screen); it comes back by itself on the next wake. */
    @Volatile var alive = false
    /** When a StandBy surface last hid with the screen off (elapsed ms); that screen-off was StandBy's own. */
    @Volatile var lastHiddenMs = 0L
    /** plan-r15 C6: the charge-started StandbyActivity, so the mood light over it can end it on the side key. */
    @Volatile var chargeActivity: java.lang.ref.WeakReference<android.app.Activity>? = null
}

/** Why plugging in did or did not bring StandBy up; the order of the checks is the order of the `when`. */
enum class ChargeVerdict(val ko: String) {
    LAUNCH("띄움"), OPTION_OFF("옵션 꺼짐"), NOT_CHARGING("충전 아님"), NOT_WIRELESS("유선 충전이라 건너뜀"),
    ALREADY_SHOWING("이미 떠 있음"), USING_PHONE("폰 사용 중이라 건너뜀"),
    NO_NOTIFICATIONS("알림 권한 없음"), NO_FULL_SCREEN("전체 화면 알림 권한 없음"),
}

object ChargeRules {
    /**
     * The one charger rule for every way StandBy starts (plug-in, screen off/on, the system screen saver): a charger is
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
        dreamRunning: Boolean, activityFront: Boolean, notificationsOk: Boolean, fullScreenOk: Boolean,
        pluggedOk: Boolean = true,
    ): ChargeVerdict = when {
        !optionOn -> ChargeVerdict.OPTION_OFF
        !charging -> ChargeVerdict.NOT_CHARGING
        !pluggedOk -> ChargeVerdict.NOT_WIRELESS
        dreamRunning || activityFront -> ChargeVerdict.ALREADY_SHOWING
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
 * JobScheduler has no "not charging" constraint, so a job re-armed while still plugged in would run again at once.
 * After each run the next job therefore waits [REARM_MS]; an unplug (dream/holder, [rearmNow]) re-arms it without
 * the wait, so the usual plug-in, unplug, plug-in cycle is never delayed.
 * ponytail: unplug while nothing of ours is alive, then plug in again within [REARM_MS] of the last run = up to
 * [REARM_MS] late. The upgrade is a dynamic power receiver inside the (already bound) notification listener.
 */
object ChargeStart {
    const val EXTRA = "kr.dot.sb.CHARGE_START"
    @Volatile private var lastLaunchMs = 0L
    private const val JOB_ID = 4101
    private const val NOTIF_ID = 8
    private const val REARM_MS = 20 * 60_000L

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

    /** The charger came out: the next plug-in must not wait for the rearm delay. */
    fun rearmNow(ctx: Context) {
        lastLaunchMs = 0L // an unplug ends the one-minute double-fire guard, so a quick re-plug starts again
        if (on(ctx)) arm(ctx, 0, replace = true)
    }

    fun cancelNotification(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)

    /**
     * Sticky ACTION_BATTERY_CHANGED EXTRA_PLUGGED (0 = no power source). r14 D: "is a charger there" is this, not
     * BatteryManager.isCharging, which reads false at the 85 % battery protection stop and while a pad starts up.
     */
    internal fun plugged(ctx: Context): Int =
        ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

    /** ms since the last launch notification, for the trail ("–" when none in this process). */
    internal fun sinceLaunch(): String = lastLaunchMs.takeIf { it > 0 }?.let { "${System.currentTimeMillis() - it}ms" } ?: "–"

    // ---- on-device trail: the last few attempts, shown in settings (the operator has no adb) ----
    private const val LOG_KEY = "trail"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("charge_start", Context.MODE_PRIVATE)
    fun log(ctx: Context, line: String) {
        val t = java.time.LocalTime.now().withNano(0)
        val old = trail(ctx)
        prefs(ctx).edit().putString(LOG_KEY, (listOf("$t  $line") + old).take(8).joinToString("\n")).apply()
    }
    fun trail(ctx: Context): List<String> = prefs(ctx).getString(LOG_KEY, "").orEmpty().lines().filter { it.isNotBlank() }

    /** The notification listener (always bound) heard the charger go in: start now, no job delay. */
    fun onPowerConnected(ctx: Context) { manualOff = false; fire(ctx, "충전기 연결") }

    /**
     * While charging, StandBy comes back right after the phone's screen goes off from normal use (timeout, double tap
     * or side key: Android does not say which, operator 2026-11-13: all three start StandBy).
     * StandBy keeps the screen on, so a screen-off while StandBy is showing is the side key: the next wake shows the
     * phone's own lock screen (operator 2026-11-13, replaces r9's "StandBy again on the next wake"). StandBy returns at
     * the next screen-off from normal use or the next plug-in.
     */
    /**
     * Called the moment the screen goes off (not after the settle delay), while StandBy still counts as showing: a
     * side-key press on StandBy sets [manualOff] before any wake can be seen. Returns whether the screen-off was ours.
     */
    fun screenOffNow(): Boolean {
        val own = StandbyPresence.activity || StandbyPresence.dream || GlowPresence.covering ||
            android.os.SystemClock.elapsedRealtime() - StandbyPresence.lastHiddenMs < 4_000L
        if (own) manualOff = true
        return own
    }

    /** The settled screen-off ([own] = [screenOffNow] at the moment it went off). */
    fun onScreenOff(ctx: Context, own: Boolean) {
        if (plugged(ctx) == 0) return // plan-r15 C7: no charger, nothing to start (no re-arm, no log line)
        if (own || screenOffNow()) { log(ctx, "화면 꺼짐 · StandBy에서 끔 → 다음 화면 꺼짐까지 폰 화면"); return }
        manualOff = false
        fire(ctx, "화면 꺼짐", force = true)
    }

    /**
     * operator 2026-11-19: after the side key on StandBy, nothing brings StandBy back while the screen stays on (the
     * charging job, a re-arm, the wake itself); only the next screen-off from normal use or a new plug-in decides again.
     * ponytail: process memory only; if the process dies in between, the next wake may show StandBy as before
     */
    @Volatile private var manualOff = false

    /** The screen woke on the lock screen while charging and no StandBy is waiting behind it. */
    fun onScreenOn(ctx: Context) {
        if (plugged(ctx) == 0) return // plan-r15 C7
        if (StandbyPresence.alive || StandbyPresence.dream) return
        if (manualOff) { log(ctx, "화면 켜짐 · 직접 껐음 → 폰 화면"); return }
        fire(ctx, "화면 켜짐", force = true)
    }

    private fun verdict(ctx: Context, plugged: Int, cfg: AppConfig = ConfigStore.flow(ctx).value): ChargeVerdict = ChargeRules.decide(
        optionOn = cfg.startOnCharge,
        charging = plugged != 0,
        interactive = ctx.getSystemService(PowerManager::class.java).isInteractive,
        locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        dreamRunning = StandbyPresence.dream,
        activityFront = StandbyPresence.activity,
        notificationsOk = ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        fullScreenOk = ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        pluggedOk = ChargeRules.allowed(cfg.startOnCharge, plugged, cfg.chargeWirelessOnly) == null,
    )

    /**
     * The system screen saver starts on any charger after a screen timeout, so it obeys the same rule as the plug-in
     * start: wrong charger = [onWrong] now; 가로로 놓였을 때만 켜기 and the phone not in landscape = [onWrong] after about
     * a second of accelerometer samples. Main thread.
     */
    fun checkDream(ctx: Context, onWrong: (String) -> Unit, onOk: () -> Unit) {
        val cfg = ConfigStore.flow(ctx).value
        ChargeRules.allowed(cfg.startOnCharge, plugged(ctx), cfg.chargeWirelessOnly)?.let { onWrong(it); return }
        if (!(cfg.chargeWirelessOnly && cfg.startLandscapeOnly)) { onOk(); return }
        val sm = ctx.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || acc == null) { onWrong("가로 확인 불가 · 센서 없음"); return }
        val window = ArrayList<FloatArray>()
        val handler = Handler(Looper.getMainLooper())
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { window.add(e.values.copyOf(3)) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (!sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME, handler)) {
            sm.unregisterListener(l)
            onWrong("가로 확인 불가 · 센서 등록 실패"); return
        }
        handler.postDelayed({
            sm.unregisterListener(l)
            val current = ConfigStore.flow(ctx).value
            val reason = ChargeRules.allowed(current.startOnCharge, plugged(ctx), current.chargeWirelessOnly)
            if (reason != null) onWrong(reason)
            else if (current.chargeWirelessOnly && current.startLandscapeOnly && !ChargeGate.landscape(window)) {
                onWrong(if (window.isEmpty()) "가로 확인 불가 · 샘플 없음" else "가로 아님이라 닫음")
            } else onOk()
        }, 1_200L)
    }

    /** plan-r15 C2: one posture window at a time (the listener and the job both see one plug-in). Main thread only. */
    private var sampling = false
    /** The job's window is running: nothing may re-arm (replace) its JOB_ID until the job itself re-arms at the end. */
    private var jobWindow = false
    private var cancelJobWindow: (() -> Unit)? = null

    internal fun stopJob() { cancelJobWindow?.invoke() }
    private const val WINDOW_MS = 15_000L
    private const val STEP_MS = 2_000L

    /**
     * A trigger (plug-in, screen off/on, the charging job). [jobDone] = the job path: true = a posture window runs
     * and re-arms + finishes the job at its end (onStartJob returns this); false = already re-armed, job over.
     */
    internal fun fire(ctx: Context, source: String = "충전 작업", force: Boolean = false, jobDone: (() -> Unit)? = null): Boolean {
        val path = if (jobDone != null) "job" else "listener"
        fun record(line: String) { Measure.log(ctx, "$source · $path · $line · ${Measure.charge(ctx)}") }
        if (!on(ctx)) { record(ChargeVerdict.OPTION_OFF.ko); return false }
        // first, so a failure below cannot leave us unarmed; never while the job's own window runs (C2)
        if (jobDone == null && !jobWindow) arm(ctx, REARM_MS, replace = true)
        fun skip(): Boolean { if (jobDone != null) arm(ctx, REARM_MS, replace = true); return false }
        if (manualOff) { record("직접 껐음 → 다음 화면 꺼짐까지 안 띄움"); return skip() } // Measure only: the 8-line trail would fill with job ticks
        // the listener and the job both see one plug-in; the second one within a minute does nothing
        if (!force && System.currentTimeMillis() - lastLaunchMs < 60_000L) { record("이미 띄움"); return skip() }
        if (sampling) { record("자세 확인 중이라 건너뜀"); return skip() }
        val battery = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        // heat protection starts at 42 °C: the log shows how warm a charge start really is
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { " · %.1f°C".format(it / 10f) }.orEmpty()
        val v = verdict(ctx, plugged)
        log(ctx, "$source · ${v.ko}$temp")
        if (Measure.on) record("${v.ko} · plugged=$plugged · ${Measure.grants(ctx)}")
        if (v != ChargeVerdict.LAUNCH) return skip()
        return gate(ctx, source, force, jobDone)
    }

    /**
     * plan-r15 C2: start only once the phone lies still and not face down (+ landscape with 가로로 놓였을 때만 켜기):
     * the last second of accelerometer samples, checked every [STEP_MS] for up to [WINDOW_MS]. The decision and the
     * double-fire guard are taken again at the still sample (someone may have unlocked meanwhile). No sample at all
     * in the window (sensor asleep) = start only when landscape confirmation is not required.
     */
    private fun gate(ctx: Context, source: String, force: Boolean, jobDone: (() -> Unit)?): Boolean {
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
            val again = !force && System.currentTimeMillis() - lastLaunchMs < 60_000L
            when {
                v != ChargeVerdict.LAUNCH -> record("$reason · ${v.ko}")
                again -> record("$reason · 이미 띄움")
                cfg.chargeWirelessOnly && cfg.startLandscapeOnly -> record("가로 확인 불가 · $reason → 건너뜀")
                else -> { record("$reason → 띄움"); launch(app) }
            }
        }
        if (sm == null || acc == null) { withoutSamples("센서 없음"); return skip(app, jobDone) }
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
        if (jobDone != null) jobWindow = true
        fun cleanUp() {
            sm.unregisterListener(l)
            handler.removeCallbacksAndMessages(null)
            sampling = false
            runCatching { if (wl?.isHeld == true) wl.release() }
            if (jobDone != null) { jobWindow = false; cancelJobWindow = null }
        }
        fun end() {
            cleanUp()
            if (jobDone != null) { arm(app, REARM_MS, replace = true); jobDone() }
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
                    val again = !force && System.currentTimeMillis() - lastLaunchMs < 60_000L
                    if (v == ChargeVerdict.LAUNCH && !again) {
                        record("자세 ${if (noSamples) "샘플 없음" else p.ko} → 띄움")
                        launch(app)
                    } else record("자세 확인 뒤 ${if (again) "이미 띄움" else v.ko}")
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
            return skip(app, jobDone)
        }
        if (jobDone != null) cancelJobWindow = { cleanUp() }
        handler.postDelayed(step, 1_000L)
        return jobDone != null
    }

    private fun skip(ctx: Context, jobDone: (() -> Unit)?): Boolean { if (jobDone != null) arm(ctx, REARM_MS, replace = true); return false }

    /** Post the full-screen intent that brings StandbyActivity up over the lock screen. */
    private fun launch(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        lastLaunchMs = System.currentTimeMillis()
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
    // plan-r15 C2: true while the posture window runs; the window re-arms and then calls jobFinished
    override fun onStartJob(params: JobParameters): Boolean = ChargeStart.fire(this, jobDone = { jobFinished(params, false) })
    override fun onStopJob(params: JobParameters): Boolean { ChargeStart.stopJob(); return false }
}

/** BOOT_COMPLETED / MY_PACKAGE_REPLACED: persisted jobs normally survive, this is the belt for the cases they do not. */
class ChargeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ChargeStart.sync(context)
}
