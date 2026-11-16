package kr.dot.sb.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kr.dot.sb.platform.DeviceTimerEffects
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.TimerMode

/** One timer card's persisted state ([PomodoroTimer.snap]); [startedAt] is elapsedRealtime. */
data class TimerSnap(val mode: TimerMode, val running: Boolean, val leftMs: Long, val startedAt: Long, val focusSec: Int, val breakSec: Int) {
    fun encode() = "${mode.name},$running,$leftMs,$startedAt,$focusSec,$breakSec"

    companion object {
        fun decode(s: String): TimerSnap? = runCatching {
            val p = s.split(',')
            TimerSnap(TimerMode.valueOf(p[0]), p[1].toBooleanStrict(), p[2].toLong(), p[3].toLong(), p[4].toInt(), p[5].toInt())
        }.getOrNull()
    }
}

/** The phone side of a timer change: [DeviceTimerEffects] on the phone, a fake in tests. */
interface TimerEffects {
    /** Arms the end alarm of [id] at elapsedRealtime [endAt]; null cancels it. */
    fun schedule(id: String, endAt: Long?)
    /** Posts or refreshes the ongoing notification; removes it when [t] is stopped at full length. */
    fun ongoing(id: String, t: PomodoroTimer, now: Long)
    /** The countdown ended while StandBy was not showing: heads-up notification + the alert ×3. */
    fun ended(id: String, t: PomodoroTimer)
    /** Stops the end alert and removes the end notification. */
    fun clearEnd(id: String)
}

/** Notification buttons: [START] = 계속 / 휴식 시작 / 집중 시작. */
enum class TimerAction { PAUSE, START, RESET, OFF }

/**
 * The one copy of every slot's timer, shared by the state holders, the end alarm and the notification buttons
 * (all on the main thread). Persisted in SharedPreferences, so a process death in between loses nothing; the holder
 * republishes on [changed] when a change came from outside it.
 */
object Timers {
    private const val BOOT = "_boot"
    private const val RETRY_MS = 5_000L
    /** Bumped after a change made outside any holder (notification button, end alarm). */
    val changed = MutableStateFlow(0)
    @Volatile var effects: (Context) -> TimerEffects = { DeviceTimerEffects(it) }
    private var map: HashMap<String, PomodoroTimer>? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("timers", Context.MODE_PRIVATE)
    private fun boot(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, 0)

    /** Every timer; one stopped at full length takes the current 집중/휴식 lengths (a settings change = the next start). */
    @Synchronized fun all(ctx: Context): HashMap<String, PomodoroTimer> {
        val m = map ?: load(ctx).also { map = it }
        val c = ConfigStore.flow(ctx).value
        for (t in m.values) t.lengths(c.focusMinutes * 60, c.breakMinutes * 60)
        return m
    }

    private fun load(ctx: Context): HashMap<String, PomodoroTimer> {
        val p = prefs(ctx)
        val m = HashMap<String, PomodoroTimer>()
        // elapsedRealtime restarts at boot: countdowns stored before a reboot mean nothing
        if (p.getInt(BOOT, -1) != boot(ctx)) { p.edit().clear().putInt(BOOT, boot(ctx)).apply(); return m }
        val now = SystemClock.elapsedRealtime()
        for ((k, v) in p.all) if (k != BOOT) (v as? String)?.let(TimerSnap::decode)?.let {
            // a start time ahead of now is from an earlier boot (BOOT_COUNT missed it): that timer starts over
            m[k] = PomodoroTimer.of(it).apply { if (it.startedAt > now) reset() }
        }
        return m
    }

    fun get(ctx: Context, id: String): PomodoroTimer = all(ctx).getOrPut(id) {
        val c = ConfigStore.flow(ctx).value
        PomodoroTimer(c.focusMinutes * 60, c.breakMinutes * 60)
    }

    /** The ongoing notification was swiped away: posted again while [id] runs or is paused. */
    fun repost(ctx: Context, id: String, now: Long) { all(ctx)[id]?.let { effects(ctx).ongoing(id, it, now) } }

    /** After [id] changed: persist it, re-arm its end alarm, refresh its ongoing notification. */
    fun save(ctx: Context, id: String, now: Long) {
        val t = all(ctx)[id] ?: return
        prefs(ctx).edit().putString(id, t.snap().encode()).apply()
        val fx = effects(ctx)
        fx.schedule(id, t.endAt())
        fx.ongoing(id, t, now)
    }

    /** A notification button. Idempotent: [TimerAction.START] on a running timer does nothing. */
    fun act(ctx: Context, id: String, a: TimerAction, now: Long) {
        effects(ctx).clearEnd(id)
        val t = all(ctx)[id] ?: return
        when (a) {
            TimerAction.PAUSE -> if (t.running) t.toggle(now)
            TimerAction.START -> if (!t.running) t.toggle(now)
            TimerAction.RESET -> t.reset()
            TimerAction.OFF -> return
        }
        save(ctx, id, now)
        changed.value++
    }

    /**
     * The end alarm of [id]. StandBy showing ([visible]): its holder ends the timer and alerts in the card; the alarm
     * is re-armed [RETRY_MS] on in case StandBy closes first (a holder's end cancels it). Otherwise the timer ends
     * here, with the heads-up and the alert. True when it ended here.
     */
    fun onAlarm(ctx: Context, id: String, now: Long, visible: Boolean): Boolean {
        val t = all(ctx)[id] ?: return false
        if (visible) { if (t.running) effects(ctx).schedule(id, now + RETRY_MS); return false }
        if (!t.finishIfDone(now)) return false // reset, paused or already ended by a holder
        save(ctx, id, now)
        effects(ctx).ended(id, t)
        changed.value++
        return true
    }

    /** Tests: drop the in-memory copy (the next read loads the preferences). */
    @Synchronized internal fun forgetMemory() { map = null }
}
