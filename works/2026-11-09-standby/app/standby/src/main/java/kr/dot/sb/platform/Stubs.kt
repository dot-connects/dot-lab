package kr.dot.sb.platform

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.service.dreams.DreamService
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity

// OWNER: platform agent. Replace each stub with its own file (StandbyDream.kt, Listener.kt …) and delete this file.

/** Screen saver hosting StandbyRoot in a ComposeView. */
class StandbyDream : DreamService()

/** Manual / preview StandBy over the lock screen. */
class StandbyActivity : ComponentActivity()

/** Media sessions, alarm detection and actions, big notifications, progress notifications. */
class Listener : NotificationListenerService()

/** Fold inner-screen mood light over the lock screen. */
class GlowActivity : ComponentActivity()

class GlowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

/** Foreground media-playback service generating sleep noise with AudioTrack (no audio assets). */
class SleepService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
