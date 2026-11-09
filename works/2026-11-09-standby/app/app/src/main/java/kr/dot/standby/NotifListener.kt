package kr.dot.standby

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Logs clock-app notifications (the moment an alarm rings shows up as a posted alarm notification)
 * and active media sessions (what the music page will be able to show and control).
 */
class NotifListener : NotificationListenerService() {
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> logSessions(list) }

    override fun onListenerConnected() {
        EventLog.write(this, "listener", "connected")
        val msm = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, NotifListener::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsChanged, me)
        logSessions(msm.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged)
        EventLog.write(this, "listener", "disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!interesting(sbn)) return
        EventLog.write(this, "notif_posted", describe(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!interesting(sbn)) return
        EventLog.write(this, "notif_removed", describe(sbn))
    }

    private fun interesting(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        return "clock" in sbn.packageName || n.category == Notification.CATEGORY_ALARM ||
            n.fullScreenIntent != null
    }

    private fun describe(sbn: StatusBarNotification): String {
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE) ?: ""
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""
        return "pkg=${sbn.packageName} id=${sbn.id} cat=${n.category} channel=${n.channelId} " +
            "ongoing=${sbn.isOngoing} fullScreen=${n.fullScreenIntent != null} title=$title text=$text"
    }

    private fun logSessions(list: List<MediaController>?) {
        val s = list.orEmpty().joinToString(" | ") { c ->
            val meta = c.metadata
            val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val art = meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
                meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) != null
            val st = when (c.playbackState?.state) {
                PlaybackState.STATE_PLAYING -> "playing"
                PlaybackState.STATE_PAUSED -> "paused"
                null -> "none"
                else -> "state${c.playbackState?.state}"
            }
            "${c.packageName} $st title=$title artist=$artist art=$art actions=${c.playbackState?.actions ?: 0}"
        }
        EventLog.write(this, "media_sessions", s.ifEmpty { "none" })
    }
}
