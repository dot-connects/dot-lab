package kr.dot.sb.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.dot.sb.model.AppConfig

/** OWNER: settings agent. Loads/saves [AppConfig] as JSON in SharedPreferences; one shared StateFlow. */
object ConfigStore {
    private const val PREFS = "standby_config"
    private const val KEY = "json"

    private val state = MutableStateFlow(AppConfig())
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            state.value = ConfigCodec.decode(prefs(context).getString(KEY, null))
            loaded = true
        }
    }

    fun flow(context: Context): StateFlow<AppConfig> { ensureLoaded(context); return state }

    fun update(context: Context, change: (AppConfig) -> AppConfig) {
        ensureLoaded(context)
        synchronized(this) {
            val next = change(state.value)
            if (next == state.value) return
            state.value = next
            prefs(context).edit().putString(KEY, ConfigCodec.encode(next)).apply()
        }
    }

    /** Test seam: forget the in-memory copy so the next call reloads from SharedPreferences (a process restart). */
    internal fun forgetMemory() { synchronized(this) { loaded = false; state.value = AppConfig() } }
}
