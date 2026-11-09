package kr.dot.sb.platform

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kr.dot.sb.data.AlarmActions
import kr.dot.sb.data.NotifKind
import kr.dot.sb.data.NotifRules
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity

/**
 * Alarm detection (Samsung Clock notification + USAGE_ALARM audio), big notifications and live-activity sources.
 * Event driven: no timers, no polling. Media sessions are looked up through this component by MediaSource.
 */
class Listener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var alarmKey: String? = null
    private val live = LinkedHashMap<String, Pair<Long, LiveActivity>>()
    private var audioAlarm = false

    private val audio = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val on = configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
            if (on == audioAlarm) return
            audioAlarm = on
            handler.removeCallbacks(audioOff)
            if (on) ListenerBus.setAudioAlarm(true) else handler.postDelayed(audioOff, 3000) // silent > 3 s = over
        }
    }
    private val audioOff = Runnable { if (!audioAlarm) ListenerBus.setAudioAlarm(false) }

    private val nextAlarm = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = GlowScheduler.reschedule(c)
    }

    override fun onListenerConnected() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        var prev: AlarmSource? = null
        s.launch {
            ListenerBus.alarm.collect { a ->
                if (prev == null && a != null) GlowReceiver.showIfInner(this@Listener) // alarm just started ringing
                prev = a
            }
        }
        s.launch { ConfigStore.flow(this@Listener).map { it.glowStartMinBefore }.distinctUntilChanged().collect { GlowScheduler.reschedule(this@Listener) } }
        getSystemService(AudioManager::class.java).registerAudioPlaybackCallback(audio, handler)
        registerReceiver(nextAlarm, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), RECEIVER_NOT_EXPORTED)
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it, null) }
    }

    override fun onListenerDisconnected() {
        runCatching { getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(audio) }
        runCatching { unregisterReceiver(nextAlarm) }
        handler.removeCallbacksAndMessages(null)
        scope?.cancel(); scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = handle(sbn, rankingMap)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ListenerBus.contentIntents.remove(sbn.key)
        if (sbn.key == alarmKey) { alarmKey = null; ListenerBus.setNotificationAlarm(null) }
        if (live.remove(sbn.key) != null) publishLive()
    }

    private fun handle(sbn: StatusBarNotification, rm: RankingMap?) {
        val n = sbn.notification
        val x = n.extras
        val tmpl = x.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val rank = Ranking()
        val ranked = rm?.getRanking(sbn.key, rank) == true
        val max = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val kind = NotifRules.classify(
            pkg = sbn.packageName, own = packageName, category = n.category, ongoing = sbn.isOngoing,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            importance = if (ranked) rank.importance else 3,
            fullScreen = n.fullScreenIntent != null,
            hasProgress = (max > 0 && !x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) || tmpl.endsWith("ProgressStyle"),
            isMedia = tmpl.endsWith("MediaStyle") || x.containsKey("android.mediaSession"),
            secret = n.visibility == Notification.VISIBILITY_SECRET,
            passesFilter = !ranked || rank.matchesInterruptionFilter(),
        )
        when (kind) {
            NotifKind.ALARM -> alarm(sbn)
            NotifKind.LIVE -> {
                live[sbn.key] = sbn.postTime to liveOf(sbn, max)
                publishLive()
            }
            NotifKind.BIG -> if (ConfigStore.flow(this).value.showNotifications) big(sbn)
            NotifKind.IGNORE -> {}
        }
    }

    private fun alarm(sbn: StatusBarNotification) {
        val n = sbn.notification
        val actions = n.actions.orEmpty().map { it.title.toString() to it.actionIntent }
        val src = ListenerBus.alarmFrom(
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.ifBlank { null } ?: "알람",
            actions, n.fullScreenIntent ?: n.contentIntent,
        )
        // a ringing alarm shows a snooze button or a full-screen intent; the "next alarm" notice has neither
        if (n.fullScreenIntent == null && src.snooze == null && actions.none { AlarmActions.isSnooze(it.first) }) return
        alarmKey = sbn.key
        ListenerBus.setNotificationAlarm(src)
    }

    private fun publishLive() {
        ListenerBus.live.value = live.values.maxByOrNull { it.first }?.second
    }

    private fun liveOf(sbn: StatusBarNotification, max: Int): LiveActivity {
        val x = sbn.notification.extras
        val p = if (max > 0) (x.getInt(Notification.EXTRA_PROGRESS).toFloat() / max).coerceIn(0f, 1f) else null
        return LiveActivity(
            sbn.key, appLabel(sbn.packageName), x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            p?.let { "${(it * 100).toInt()}%" } ?: x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(), p,
        )
    }

    private fun big(sbn: StatusBarNotification) {
        val n = sbn.notification
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        // PRIVATE content on a locked phone: only what the app allows in its public version
        val private = n.visibility == Notification.VISIBILITY_PRIVATE && locked
        val pub = n.publicVersion
        val x = if (private && pub != null) pub.extras else n.extras
        val hiddenByApp = private && pub == null
        val sender = (x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: x.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val icon: Bitmap? = runCatching {
            (n.getLargeIcon()?.loadDrawable(this) ?: packageManager.getApplicationIcon(sbn.packageName)).toBitmap(192, 192)
        }.getOrNull()
        ListenerBus.contentIntents[sbn.key] = n.contentIntent ?: return
        ListenerBus.notifications.tryEmit(
            BigNotification(
                sbn.key, appLabel(sbn.packageName), sender, text, icon?.asImageBitmap(),
                plateColor = if (n.color != 0) (n.color.toLong() or 0xFF000000L) and 0xFFFFFFFFL else 0xFF34C759,
                hidden = ConfigStore.flow(this).value.previewOnTapOnly || hiddenByApp,
                postedAtEpochMs = sbn.postTime,
            ),
        )
    }

    private fun appLabel(pkg: String) =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}
