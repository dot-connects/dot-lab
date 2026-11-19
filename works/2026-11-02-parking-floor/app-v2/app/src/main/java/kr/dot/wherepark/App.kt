package kr.dot.wherepark

import android.app.Application
import kr.dot.wherepark.family.Push

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.createChannels(this)
    }
}
