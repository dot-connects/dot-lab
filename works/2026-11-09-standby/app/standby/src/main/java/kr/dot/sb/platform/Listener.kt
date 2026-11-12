package kr.dot.sb.platform

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
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
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kr.dot.sb.data.AlarmActions
import kr.dot.sb.data.LiveTimes
import kr.dot.sb.data.MoodLatch
import kr.dot.sb.data.NotifKind
import kr.dot.sb.data.NotifPrivacy
import kr.dot.sb.data.NotifRules
import kr.dot.sb.data.Reveal
import kr.dot.sb.data.ShadeRules
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.NotifItem
import java.lang.ref.WeakReference

/**
 * Alarm detection (Samsung Clock notification + USAGE_ALARM audio), big notifications, live-activity sources and the
 * shade list of the notification cards. Event driven: no timers, no polling. Media sessions are looked up through this
 * component by MediaSource.
 */
class Listener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var alarmKey: String? = null
    private val live = LinkedHashMap<String, Pair<Long, LiveActivity>>()
    private var audioAlarm = false
    /** Shade icons, decoded once per notification ("key|kind") or app ("pkg:"); pruned to what is listed. */
    private val icons = HashMap<String, ImageBitmap?>()
    private val labels = HashMap<String, String>()

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

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                // let the battery state settle (the plug type arrives a moment later on some chargers)
                Intent.ACTION_POWER_CONNECTED -> {
                    MoodLatch.done = false // a new charge may light the unfold mood light again
                    handler.postDelayed({ ChargeStart.onPowerConnected(this@Listener) }, 1500)
                }
                // after the keyguard has locked and StandBy (if it was up) has stopped
                Intent.ACTION_SCREEN_OFF -> handler.postDelayed({ ChargeStart.onScreenOff(this@Listener) }, 1500)
                Intent.ACTION_SCREEN_ON -> ChargeStart.onScreenOn(this@Listener)
                else -> ChargeStart.rearmNow(c)
            }
        }
    }

    private val nextAlarm = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = GlowScheduler.reschedule(c)
    }

    override fun onListenerConnected() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        ListenerBus.listener = WeakReference(this)
        var prev: AlarmSource? = null
        s.launch {
            ListenerBus.alarm.collect { a ->
                // alarm just started ringing
                if (prev == null && a != null && ConfigStore.flow(this@Listener).value.alarmGlow) GlowReceiver.showIfInner(this@Listener, GlowSource.ALARM)
                prev = a
            }
        }
        s.launch { ConfigStore.flow(this@Listener).map { it.glowStartMinBefore }.distinctUntilChanged().collect { GlowScheduler.reschedule(this@Listener) } }
        // the shade depends on two settings: 탭할 때만 미리보기 (redaction) and the theme (colour icon vs mono glyph)
        s.launch {
            ConfigStore.flow(this@Listener).map { it.previewOnTapOnly to Themes.of(it.theme).tokens.monoIcons }.distinctUntilChanged().drop(1)
                .collect { publishShade() }
        }
        getSystemService(AudioManager::class.java).registerAudioPlaybackCallback(audio, handler)
        registerReceiver(nextAlarm, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), RECEIVER_NOT_EXPORTED)
        // this service stays bound, so it hears every plug-in at once (the charging job alone can lag by minutes)
        registerReceiver(power, IntentFilter(Intent.ACTION_POWER_CONNECTED).apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }, RECEIVER_NOT_EXPORTED)
        val rm0 = runCatching { currentRanking }.getOrNull()
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it, rm0) }
        publishShade()
    }

    override fun onListenerDisconnected() {
        runCatching { getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(audio) }
        runCatching { unregisterReceiver(nextAlarm) }
        runCatching { unregisterReceiver(power) }
        handler.removeCallbacksAndMessages(null)
        scope?.cancel(); scope = null
        if (ListenerBus.listener?.get() === this) ListenerBus.listener = null
        ListenerBus.shade.value = emptyList()
        icons.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        handle(sbn, rankingMap)
        publishShade()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ListenerBus.contentIntents.remove(sbn.key)
        if (sbn.key == alarmKey) { alarmKey = null; ListenerBus.setNotificationAlarm(null) }
        if (live.remove(sbn.key) != null) publishLive()
        publishShade()
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
                // the previous post of this key carries the step times; a newly reached step is stamped now
                val a = liveOf(sbn, max, reveal(sbn, rank.takeIf { ranked }, lockScreen(), false))
                live[sbn.key] = sbn.postTime to LiveTimes.stamp(live[sbn.key]?.second, a, System.currentTimeMillis())
                publishLive()
            }
            NotifKind.BIG -> if (ConfigStore.flow(this).value.showNotifications) big(sbn, reveal(sbn, rank.takeIf { ranked }, lockScreen(), false))
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

    // ---- lock-screen privacy (one rule for every surface: NotifPrivacy) ----------------------------------------------

    private class LockScreen(val secure: Boolean, val showOnLock: Boolean, val allowPrivate: Boolean)

    /** The user's lock-screen settings; an unreadable "show content" counts as off (the strict answer). */
    private fun lockScreen(): LockScreen {
        fun on(key: String, unreadable: Boolean) = runCatching { Settings.Secure.getInt(contentResolver, key, 1) != 0 }.getOrDefault(unreadable)
        // a device policy (work/MDM) can switch either one off regardless of the user's own toggles
        val policy = runCatching {
            getSystemService(android.app.admin.DevicePolicyManager::class.java).getKeyguardDisabledFeatures(null)
        }.getOrDefault(0)
        return LockScreen(
            secure = getSystemService(KeyguardManager::class.java).isDeviceSecure,
            showOnLock = on("lock_screen_show_notifications", true) &&
                policy and android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS == 0,
            allowPrivate = on("lock_screen_allow_private_notifications", false) &&
                policy and android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS == 0,
        )
    }

    private fun reveal(sbn: StatusBarNotification, rank: Ranking?, lock: LockScreen, tapOnly: Boolean): Reveal {
        val n = sbn.notification
        return NotifPrivacy.reveal(
            visibility = n.visibility,
            // no ranking, or another profile (work) whose own lock-screen settings we can't read: the strict answer
            channelOverride = when {
                sbn.user != android.os.Process.myUserHandle() -> Notification.VISIBILITY_PRIVATE
                rank == null -> Notification.VISIBILITY_PRIVATE
                else -> rank.lockscreenVisibilityOverride
            },
            secure = lock.secure, showOnLock = lock.showOnLock, allowPrivate = lock.allowPrivate,
            hasPublic = n.publicVersion != null, tapOnly = tapOnly,
        )
    }

    /** Title and text through [NotifPrivacy.text]: only what [r] allows ever leaves this method. */
    private fun textOf(sbn: StatusBarNotification, r: Reveal, app: String, big: Boolean): Pair<String, String> {
        fun t(x: android.os.Bundle?) = (x?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: x?.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        fun b(x: android.os.Bundle?) = ((if (big) x?.getCharSequence(Notification.EXTRA_BIG_TEXT) else null) ?: x?.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val x = sbn.notification.extras
        val p = sbn.notification.publicVersion?.extras
        return when (r) {
            Reveal.FULL -> NotifPrivacy.text(r, app, t(x), b(x), null, null)
            Reveal.PUBLIC -> NotifPrivacy.text(r, app, null, null, t(p), b(p))
            Reveal.HIDDEN, Reveal.NONE -> NotifPrivacy.text(r, app, null, null, null, null)
        }
    }

    private fun liveOf(sbn: StatusBarNotification, max: Int, r: Reveal): LiveActivity {
        val n = sbn.notification
        val x = when (r) {
            Reveal.FULL -> n.extras
            Reveal.PUBLIC -> n.publicVersion!!.extras
            Reveal.HIDDEN, Reveal.NONE -> null // progress only, no text
        }
        val p = if (max > 0) ((x ?: n.extras).getInt(Notification.EXTRA_PROGRESS).toFloat() / max).coerceIn(0f, 1f) else null
        val pct = p?.let { "${(it * 100).toInt()}%" }
        if (x == null) return LiveActivity(sbn.key, appLabel(sbn.packageName), "", pct.orEmpty(), "", p)
        return LiveActivity(
            sbn.key, appLabel(sbn.packageName), x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            pct ?: x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(), p,
        )
    }

    private fun big(sbn: StatusBarNotification, r: Reveal) {
        if (r == Reveal.NONE) return
        val n = sbn.notification
        val app = appLabel(sbn.packageName)
        // a hidden notification gets no text at all in the state (not merely flagged), and no sender photo
        val hiddenByApp = r == Reveal.HIDDEN
        val (sender, text) = if (hiddenByApp) "" to "" else textOf(sbn, r, app, big = true)
        val icon: Bitmap? = runCatching {
            ((if (r == Reveal.FULL) n.getLargeIcon()?.loadDrawable(this) else null) ?: packageManager.getApplicationIcon(sbn.packageName)).toBitmap(192, 192)
        }.getOrNull()
        ListenerBus.contentIntents[sbn.key] = n.contentIntent ?: return
        ListenerBus.notifications.tryEmit(
            BigNotification(
                sbn.key, app, sender, text, icon?.asImageBitmap(),
                plateColor = plateOf(n),
                hidden = ConfigStore.flow(this).value.previewOnTapOnly || hiddenByApp,
                postedAtEpochMs = sbn.postTime,
            ),
        )
    }

    // ---- shade cards -----------------------------------------------------------------------------------------------

    /** Rebuild the notification cards' list from the active notifications. Runs on post/remove only. */
    internal fun publishShade() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val cfg = ConfigStore.flow(this).value
        val lock = lockScreen()
        val mono = Themes.of(cfg.theme).tokens.monoIcons
        val rank = Ranking()
        val used = HashSet<String>()
        val items = active.sortedByDescending { it.postTime }.asSequence().mapNotNull { sbn ->
            val n = sbn.notification
            val x = n.extras
            val r = rank.takeIf { ranking?.getRanking(sbn.key, it) == true }
            val tmpl = x.getString(Notification.EXTRA_TEMPLATE).orEmpty()
            val listed = ShadeRules.include(
                pkg = sbn.packageName, own = packageName, category = n.category, ongoing = sbn.isOngoing,
                foreground = n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
                groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                fullScreen = n.fullScreenIntent != null,
                isMedia = tmpl.endsWith("MediaStyle") || x.containsKey("android.mediaSession"),
                importance = r?.importance ?: NotificationManager.IMPORTANCE_DEFAULT,
                hiddenFromShade = r != null && r.suppressedVisualEffects and NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST != 0,
            )
            val rv = if (listed) reveal(sbn, r, lock, cfg.previewOnTapOnly) else Reveal.NONE
            if (rv == Reveal.NONE) return@mapNotNull null
            val app = appLabel(sbn.packageName)
            val (title, text) = textOf(sbn, rv, app, big = true)
            n.contentIntent?.let { ListenerBus.contentIntents[sbn.key] = it }
            NotifItem(
                key = sbn.key, appLabel = app, packageName = sbn.packageName, title = title, text = text,
                postedAtEpochMs = sbn.postTime,
                icon = if (mono) null else colourIcon(sbn, avatar = rv == Reveal.FULL, used),
                glyph = if (mono) glyphIcon(sbn, used) else null,
                plateColor = plateOf(n), hidden = rv == Reveal.HIDDEN, clearable = sbn.isClearable,
            )
        }.take(MAX_SHADE).toList()
        icons.keys.retainAll(used)
        ListenerBus.shade.value = items
    }

    /** Sender photo (only when the content may show: a photo is content too), else the app icon. */
    private fun colourIcon(sbn: StatusBarNotification, avatar: Boolean, used: MutableSet<String>): ImageBitmap? {
        if (avatar && sbn.notification.getLargeIcon() != null) {
            val k = "${sbn.key}|a"
            used += k
            val bmp = icons.getOrPut(k) { decode { sbn.notification.getLargeIcon()?.loadDrawable(this)?.toBitmap(ICON_PX, ICON_PX) } }
            if (bmp != null) return bmp
        }
        val k = "pkg:${sbn.packageName}"
        used += k
        return icons.getOrPut(k) { decode { packageManager.getApplicationIcon(sbn.packageName).toBitmap(ICON_PX, ICON_PX) } }
    }

    /** The status-bar icon: an alpha mask the paper theme tints with its ink. */
    private fun glyphIcon(sbn: StatusBarNotification, used: MutableSet<String>): ImageBitmap? {
        val k = "${sbn.key}|g"
        used += k
        return icons.getOrPut(k) { decode { sbn.notification.smallIcon?.loadDrawable(this)?.toBitmap(ICON_PX, ICON_PX) } }
    }

    private inline fun decode(f: () -> Bitmap?): ImageBitmap? = runCatching { f()?.asImageBitmap() }.getOrNull()

    private fun plateOf(n: Notification): Long = if (n.color != 0) (n.color.toLong() or 0xFF000000L) and 0xFFFFFFFFL else 0xFF34C759

    private fun appLabel(pkg: String) = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private companion object {
        /** ponytail: newest 24 only (a group chat can keep 50); the cards never show more than a screenful anyway. */
        const val MAX_SHADE = 24
        /** Icons draw at most ~60 dp; 96 px is sharp on xhdpi and light (37 KB each). */
        const val ICON_PX = 96
    }
}
