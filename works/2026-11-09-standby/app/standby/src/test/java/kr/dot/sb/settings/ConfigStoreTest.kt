package kr.dot.sb.settings

import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.model.NightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs get() = ctx.getSharedPreferences("standby_config", android.content.Context.MODE_PRIVATE)

    @Before fun clean() {
        prefs.edit().clear().commit()
        ConfigStore.forgetMemory()
    }

    @Test fun updateSurvivesProcessRestart() {
        ConfigStore.update(ctx) { it.copy(startPage = 5, nightMode = NightMode.OFF) }
        ConfigStore.forgetMemory() // new process: nothing in memory, only SharedPreferences
        val c = ConfigStore.flow(ctx).value
        assertEquals(5, c.startPage)
        assertEquals(NightMode.OFF, c.nightMode)
    }

    // ---- plan-r15 C8: a save that does not decode never costs the user their settings ----

    @Test fun aBrokenSaveRestoresTheBackupAndKeepsTheRawText() {
        ConfigStore.update(ctx) { it.copy(startPage = 4) }
        prefs.edit().putString("json", "{\"v\":13,\"theme\":").commit() // cut off mid-write
        ConfigStore.forgetMemory()
        assertEquals(4, ConfigStore.flow(ctx).value.startPage)
        assertEquals("{\"v\":13,\"theme\":", prefs.getString(ConfigStore.BROKEN, null))
        // written back: the next process reads it directly
        ConfigStore.forgetMemory()
        assertEquals(4, ConfigStore.flow(ctx).value.startPage)
    }

    @Test fun aSessionFromDefaultsAfterAFailureNeverOverwritesTheBackup() {
        // a save from before backups existed, broken: defaults for this session
        prefs.edit().putString("json", "not json").commit()
        ConfigStore.forgetMemory()
        assertEquals(kr.dot.sb.model.AppConfig(), ConfigStore.flow(ctx).value)
        ConfigStore.update(ctx) { it.copy(startPage = 7) }
        assertNull(prefs.getString(ConfigStore.BACKUP, null))
        assertEquals("not json", prefs.getString(ConfigStore.BROKEN, null))
        // a backup that is there but unreadable is not overwritten by such a session either
        prefs.edit().putString("json", "not json").putString(ConfigStore.BACKUP, "{broken").commit()
        ConfigStore.forgetMemory()
        ConfigStore.update(ctx) { it.copy(startPage = 2) }
        assertEquals("{broken", prefs.getString(ConfigStore.BACKUP, null))
    }

    @Test fun nothingSavedIsNotAFailure() {
        assertEquals(kr.dot.sb.model.AppConfig(), ConfigStore.flow(ctx).value)
        assertNull(prefs.getString(ConfigStore.BROKEN, null))
        ConfigStore.update(ctx) { it.copy(startPage = 3) }
        assertEquals(3, ConfigCodec.decode(prefs.getString(ConfigStore.BACKUP, null)).startPage)
    }
}
