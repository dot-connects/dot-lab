package kr.dot.standby

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(filesDir, "crash.log").writeText(error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LOG, "기록 중", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_GLOW, "무드등 테스트", NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val CH_LOG = "log"
        const val CH_GLOW = "glow"
    }
}
