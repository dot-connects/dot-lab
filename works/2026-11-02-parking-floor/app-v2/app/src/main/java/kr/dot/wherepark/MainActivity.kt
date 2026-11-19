package kr.dot.wherepark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.SyncEngine
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.family.Push

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("어디댔어") }
    }

    /** Pull when the app opens and (re)register the push token; with no token the 15-minute pull takes over. */
    override fun onResume() {
        super.onResume()
        val app = applicationContext
        Thread {
            WidgetRefresh.request(app)  // re-reads the wallpaper colours (no broadcast for wallpaper changes)
            Push.start(app)
            if (FamilyStore.joined(app)) SyncEngine.pull(app)
        }.start()
    }
}
