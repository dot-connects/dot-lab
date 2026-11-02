package kr.dot.parkingfloor

import android.app.Application
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(filesDir, "crash.log").writeText(error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }
    }
}
