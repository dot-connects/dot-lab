package kr.dot.sb.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import kr.dot.sb.data.PomodoroTimer
import kr.dot.sb.data.TimerAction
import kr.dot.sb.data.TimerEffects
import kr.dot.sb.data.Timers
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.theme.baseTheme
import java.time.LocalTime

/**
 * Timer app notifications (r15 §6): an ongoing notification per slot while a timer runs or is paused, and when a
 * countdown ends with StandBy closed, a heads-up end notification plus the chosen alert ×3. Notification tag = slot id.
 */
class DeviceTimerEffects(ctx: Context) : TimerEffects {
    private val app = ctx.applicationContext
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    override fun schedule(id: String, endAt: Long?) {
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = TimerReceiver.pi(app, id, TimerReceiver.END)
        am.cancel(pi)
        if (endAt == null) return
        // exact only once the user allowed 알람 (SCHEDULE_EXACT_ALARM is off by default on Android 14; the timer card
        // links to the page); the inexact fallback can be many minutes late in Doze
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAt, pi)
    }

    override fun ongoing(id: String, t: PomodoroTimer, now: Long) {
        val s = t.state(now)
        if (!s.running && s.leftSec >= s.totalSec) { nm.cancel(id, ONGOING); return }
        Channels.ensure(app)
        val focus = s.mode == TimerMode.FOCUS
        val word = if (focus) "집중" else "휴식"
        val b = base(id, Channels.TIMER, focus)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            // ponytail: the bar is drawn at each change (start, pause, end), not every second; the header countdown is live
            .setProgress(s.totalSec, s.leftSec, false)
            // swiped away (Android 14 lets users dismiss ongoing ones): posted again
            .setDeleteIntent(TimerReceiver.pi(app, id, TimerReceiver.REPOST))
        val left = t.leftMs(now)
        if (s.running && left > 0) {
            val end = LocalTime.now().plusSeconds(s.leftSec.toLong())
            b.setContentTitle(word)
                .setContentText("%d:%02d에 %s".format(end.hour, end.minute, if (focus) "휴식" else "집중"))
                .setWhen(System.currentTimeMillis() + left).setShowWhen(true)
                .setUsesChronometer(true).setChronometerCountDown(true)
                // gone at 0 instead of counting below it while a late (inexact) end alarm is still on its way
                .setTimeoutAfter(left)
                .addAction(action(id, TimerAction.PAUSE, "멈춤"))
        } else if (s.running) {
            b.setContentTitle(word).setContentText("시간이 다 됐어요").setShowWhen(false) // the end alarm has not fired yet
        } else {
            b.setContentTitle("멈춤 · %02d:%02d 남음".format(s.leftSec / 60, s.leftSec % 60))
                .setContentText("$word ${s.totalSec / 60}분")
                .setShowWhen(false)
                .addAction(action(id, TimerAction.START, "계속"))
        }
        nm.notify(id, ONGOING, b.addAction(action(id, TimerAction.RESET, "초기화")).build())
    }

    override fun ended(id: String, t: PomodoroTimer) {
        Channels.ensure(app)
        val c = ConfigStore.flow(app).value
        val snap = t.snap()
        val breakNext = t.mode == TimerMode.BREAK // finishIfDone already switched to the next mode
        val b = base(id, Channels.TIMER_END, !breakNext)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDeleteIntent(TimerReceiver.pi(app, id, TimerAction.OFF.name))
        if (breakNext) b.setContentTitle("집중 ${snap.focusSec / 60}분이 끝났어요").setContentText("잠깐 쉬어요 · 휴식 ${snap.breakSec / 60}분")
            .addAction(action(id, TimerAction.START, "휴식 시작"))
        else b.setContentTitle("휴식 끝 · 다시 집중할까요?").addAction(action(id, TimerAction.START, "집중 시작"))
        nm.notify(id, END, b.addAction(action(id, TimerAction.OFF, "끄기")).build())
        // the sound comes from the player (the channel is silent): same tones as the card, same silent/vibrate/DND rules
        DeviceTimerAlerts.shared(app).play(c.timerAlert, falling = !breakNext, times = 3)
    }

    override fun clearEnd(id: String) {
        DeviceTimerAlerts.shared(app).stop()
        nm.cancel(id, END)
    }

    private fun base(id: String, channel: String, focus: Boolean): Notification.Builder {
        val th = ConfigStore.flow(app).value.baseTheme().tokens
        return Notification.Builder(app, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setSubText("타이머")
            .setColor((if (focus) th.accent else th.roles.green).toArgb())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open())
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        app, 14, Intent(app, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(id: String, a: TimerAction, label: String) =
        Notification.Action.Builder(null, label, TimerReceiver.pi(app, id, a.name)).build()

    companion object {
        const val ONGOING = 7101
        const val END = 7102
    }
}

/** End alarm and notification buttons. The slot id rides in the data URI, so each slot gets its own PendingIntents. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.schemeSpecificPart ?: return
        val now = SystemClock.elapsedRealtime()
        if (intent.action == END) {
            val visible = StandbyPresence.activity || StandbyPresence.dream
            // keep the process unfrozen while the alert (~7 s for 3 rounds) plays on its thread
            if (Timers.onAlarm(context, id, now, visible)) goAsync().let { p -> Handler(Looper.getMainLooper()).postDelayed({ p.finish() }, 8_000) }
            return
        }
        if (intent.action == REPOST) { Timers.repost(context, id, now); return }
        TimerAction.entries.firstOrNull { it.name == intent.action }?.let { Timers.act(context, id, it, now) }
    }

    companion object {
        const val END = "END"
        const val REPOST = "REPOST"
        fun pi(ctx: Context, id: String, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, TimerReceiver::class.java).setAction(action).setData(Uri.fromParts("timer", id, null)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
