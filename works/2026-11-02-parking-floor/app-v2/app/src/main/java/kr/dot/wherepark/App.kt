package kr.dot.wherepark

import android.app.Application
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.family.Push
import kr.dot.wherepark.ui.ListenModel
import kr.dot.wherepark.widget.WidgetReceiver
import kr.dot.wherepark.widget.WidgetSpecs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.createChannels(this)
        WidgetRefresh.hook = WidgetReceiver::refreshAll
        ListenModel.styleOf = WidgetSpecs::styleFor
    }
}
