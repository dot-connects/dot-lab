package kr.dot.sb.platform

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kr.dot.sb.data.AlarmActions
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.NotifItem
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class AlarmSource(
    val label: String,
    val snooze: PendingIntent?,
    val stop: PendingIntent?,
    val open: PendingIntent?,
)

/** In-process hand-over from [Listener] (always bound once access is granted) to the state holder and the glow screen. */
object ListenerBus {
    val alarm = MutableStateFlow<AlarmSource?>(null)
    val live = MutableStateFlow<LiveActivity?>(null)
    val notifications = MutableSharedFlow<BigNotification>(extraBufferCapacity = 4, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val contentIntents = ConcurrentHashMap<String, PendingIntent>()
    /** The phone's shade for the notification cards (newest first, redacted). */
    val shade = MutableStateFlow<List<NotifItem>>(emptyList())

    /** The connected listener (null while access is off or it is rebinding); cancelling goes through it. */
    @Volatile internal var listener: WeakReference<Listener>? = null

    /** Re-read the shade (a StandBy session starting re-checks the lock-screen settings). */
    fun refreshShade() { listener?.get()?.publishShade() }

    /** Remove notifications from the phone, as a swipe in the system shade does. */
    fun cancel(keys: List<String>) {
        val l = listener?.get() ?: return
        runCatching { l.cancelNotifications(keys.toTypedArray()) }
    }

    /**
     * Fire a notification's tap intent the way the system shade does. Since Android 14 a sender must opt in to lend
     * its foreground state; without it the app (in the background) may not open. Then, like the shade, auto-cancel.
     */
    fun open(ctx: Context, key: String, pi: PendingIntent): Boolean {
        val opts = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
        val sbn = listener?.get()?.let { runCatching { it.getActiveNotifications(arrayOf(key)) }.getOrNull()?.firstOrNull() }
        val sent = runCatching { pi.send(ctx, 0, null, null, null, null, opts) }.isSuccess
        if (sent && sbn != null && sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) cancel(listOf(key))
        return sent
    }

    @Volatile private var fromNotification: AlarmSource? = null
    @Volatile private var fromAudio = false

    fun setNotificationAlarm(a: AlarmSource?) { fromNotification = a; recompute() }
    fun setAudioAlarm(on: Boolean) { fromAudio = on; recompute() }

    private fun recompute() {
        alarm.value = fromNotification ?: if (fromAudio) AlarmSource("알람", null, null, null) else null
    }

    fun snooze(ctx: Context) = fire(ctx, alarm.value?.snooze)
    fun stop(ctx: Context) = fire(ctx, alarm.value?.stop)

    /** Fire the clock app's own action; without one, open the clock app so the user can finish it there. */
    private fun fire(ctx: Context, pi: PendingIntent?) {
        if (pi != null && runCatching { pi.send() }.isSuccess) return
        val open = alarm.value?.open
        if (open != null && runCatching { open.send() }.isSuccess) return
        ctx.packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { ctx.startActivity(it) } }
    }

    /** Picks the snooze/stop actions out of a notification's action list by title (Korean and English variants). */
    fun alarmFrom(label: String, actions: List<Pair<String, PendingIntent?>>, open: PendingIntent?) = AlarmSource(
        label,
        actions.firstOrNull { AlarmActions.isSnooze(it.first) }?.second,
        actions.firstOrNull { AlarmActions.isStop(it.first) }?.second,
        open,
    )
}

object Channels {
    const val GLOW = "glow"
    const val SLEEP = "sleep"
    const val CHARGE = "charge"

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(GLOW, "무드등", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        nm.createNotificationChannel(NotificationChannel(CHARGE, "충전 시작", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        nm.createNotificationChannel(NotificationChannel(SLEEP, "수면 소리", NotificationManager.IMPORTANCE_LOW))
    }
}
