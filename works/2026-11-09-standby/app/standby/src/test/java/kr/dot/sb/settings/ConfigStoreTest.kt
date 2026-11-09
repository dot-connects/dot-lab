package kr.dot.sb.settings

import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.model.NightMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigStoreTest {
    @Test fun updateSurvivesProcessRestart() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ConfigStore.forgetMemory()
        ConfigStore.update(ctx) { it.copy(startPage = 5, nightMode = NightMode.OFF) }
        ConfigStore.forgetMemory() // new process: nothing in memory, only SharedPreferences
        val c = ConfigStore.flow(ctx).value
        assertEquals(5, c.startPage)
        assertEquals(NightMode.OFF, c.nightMode)
    }
}
