package kr.dot.tripbook

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * kr.dot.tripbook/making: while the AI editor lays out a book (2-4 min) the person may leave the app.
 *  start(title)      -> foreground service with an ongoing "책 만드는 중" notification: the process (and the Dart polling
 *                       loop) stays alive behind the home screen. Asks for POST_NOTIFICATIONS once on Android 13+.
 *  done(title, book) -> service off, a "책이 다 됐어요" notification; a tap opens that book (extra "book")
 *  stop()            -> service off, nothing shown (the person is looking at the app, or it failed)
 *  takeLaunch()      -> the book id the app was opened for from a notification, once
 * Swiping the app away ends the service too: the server keeps the job and the app resumes it on the next start.
 */
object Making {
    const val CHANNEL = "making"
    const val ONGOING = 41
    const val DONE = 42
    const val EXTRA_BOOK = "book"
    var launchBook: String? = null

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "책 만들기", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "책을 만드는 동안과 다 됐을 때 알려요"
                setSound(null, null)
            })
        }
    }

    fun openIntent(c: Context, book: String?): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (book != null) putExtra(EXTRA_BOOK, book)
        }
        return PendingIntent.getActivity(c, book?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun handler(activity: Activity) = MethodChannel.MethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
        val c = activity.applicationContext
        when (call.method) {
            "start" -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
                val i = Intent(c, MakingService::class.java).putExtra("title", call.argument<String>("title") ?: "")
                try {
                    c.startForegroundService(i)
                } catch (_: Exception) {
                    // not allowed right now: the making screen keeps working while it is in front
                }
                result.success(null)
            }
            "done" -> {
                c.stopService(Intent(c, MakingService::class.java))
                ensureChannel(c)
                val n = Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_book)
                    .setContentTitle(call.argument<String>("head") ?: "책이 다 됐어요")
                    .setContentText(call.argument<String>("title") ?: "")
                    .setContentIntent(openIntent(c, call.argument<String>("book")))
                    .setAutoCancel(true)
                    .build()
                try {
                    c.getSystemService(NotificationManager::class.java).notify(DONE, n)
                } catch (_: SecurityException) {
                    // notifications refused: the shelf shows the finished book anyway
                }
                result.success(null)
            }
            "stop" -> {
                c.stopService(Intent(c, MakingService::class.java))
                result.success(null)
            }
            "takeLaunch" -> {
                result.success(launchBook)
                launchBook = null
            }
            else -> result.notImplemented()
        }
    }
}

class MakingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Making.ensureChannel(this)
        val title = intent?.getStringExtra("title").orEmpty()
        val n = Notification.Builder(this, Making.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("책 만드는 중")
            .setContentText(if (title.isEmpty()) "다 되면 알려 드릴게요" else "$title · 다 되면 알려 드릴게요")
            .setContentIntent(Making.openIntent(this, null))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(Making.ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(Making.ONGOING, n)
        return START_NOT_STICKY
    }

    // swiped away: the Dart side is gone with the activity, so an ongoing notification would lie
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }
}
