package kr.dot.sb.platform

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kr.dot.sb.ui.state.PhoneControls
import kr.dot.sb.ui.state.Ringer
import kotlin.math.roundToInt

/**
 * The 배터리 card's phone controls: sound mode and ring volume (AudioManager), 방해 금지 (NotificationManager), network
 * type (no SSID: that needs location). Every write is runCatching: without policy access a phone throws on 무음 / 방해 금지.
 */
object Phone {
    private fun audio(ctx: Context) = ctx.getSystemService(AudioManager::class.java)
    private fun notif(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun read(ctx: Context): PhoneControls {
        val a = audio(ctx)
        val n = notif(ctx)
        val ringer = when (a.ringerMode) { AudioManager.RINGER_MODE_VIBRATE -> Ringer.VIBRATE; AudioManager.RINGER_MODE_SILENT -> Ringer.SILENT; else -> Ringer.SOUND }
        val max = a.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        val caps = ctx.getSystemService(ConnectivityManager::class.java).let { it.getNetworkCapabilities(it.activeNetwork) }
        val net = when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "와이파이"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "모바일 데이터"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "유선"
            else -> null
        }
        return PhoneControls(
            ringer, a.getStreamVolume(AudioManager.STREAM_RING) / max.toFloat(),
            dnd = n.currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN },
            dndAccess = n.isNotificationPolicyAccessGranted, network = net,
        )
    }

    fun setRinger(ctx: Context, r: Ringer) {
        val mode = when (r) { Ringer.SOUND -> AudioManager.RINGER_MODE_NORMAL; Ringer.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE; Ringer.SILENT -> AudioManager.RINGER_MODE_SILENT }
        runCatching { audio(ctx).ringerMode = mode }
    }

    /** Never 0: a ring volume of 0 flips the phone to vibrate (the sound mode row does that). */
    fun setVolume(ctx: Context, v: Float) {
        val a = audio(ctx)
        val max = a.getStreamMaxVolume(AudioManager.STREAM_RING)
        runCatching { a.setStreamVolume(AudioManager.STREAM_RING, (v.coerceIn(0f, 1f) * max).roundToInt().coerceIn(1, max), 0) }
    }

    fun setDnd(ctx: Context, on: Boolean) {
        runCatching { notif(ctx).setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL) }
    }
}
