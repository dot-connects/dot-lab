package kr.dot.sb.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.dot.sb.model.AppConfig

/** OWNER: settings agent. Loads/saves [AppConfig] as JSON in SharedPreferences; one shared StateFlow. */
object ConfigStore {
    private const val PREFS = "standby_config"
    private const val KEY = "json"
    /** plan-r15 C8: the last save that decoded back, and the raw text of a save that did not (kept to look at later). */
    internal const val BACKUP = "json_backup"
    internal const val BROKEN = "json_broken"

    private val state = MutableStateFlow(AppConfig())
    @Volatile private var loaded = false
    /** This session started from defaults after a failed decode: its saves must not overwrite the good backup. */
    private var noBackup = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            state.value = load(context)
            loaded = true
        }
    }

    /** The saved config; a save that does not decode = its backup (written back), else the defaults. */
    private fun load(context: Context): AppConfig {
        val p = prefs(context)
        val raw = p.getString(KEY, null)
        if (raw.isNullOrBlank()) return AppConfig()
        ConfigCodec.decodeOrNull(raw)?.let { return it }
        val backup = p.getString(BACKUP, null)
        val restored = backup?.let { ConfigCodec.decodeOrNull(it) }
        val e = p.edit().putString(BROKEN, raw)
        if (restored != null) e.putString(KEY, backup) else noBackup = true
        e.apply()
        android.util.Log.w("ConfigStore", if (restored != null) "config unreadable, backup restored" else "config unreadable, defaults")
        return restored ?: AppConfig()
    }

    fun flow(context: Context): StateFlow<AppConfig> { ensureLoaded(context); return state }

    fun update(context: Context, change: (AppConfig) -> AppConfig) {
        ensureLoaded(context)
        synchronized(this) {
            val next = change(state.value)
            if (next == state.value) return
            state.value = next
            val json = ConfigCodec.encode(next)
            val e = prefs(context).edit().putString(KEY, json)
            if (!noBackup && ConfigCodec.decodeOrNull(json) != null) e.putString(BACKUP, json)
            e.apply()
        }
    }

    /** "v" of the saved config, null = nothing saved yet (plan-r13 B1: a config from before r13 means the app was used). */
    fun storedVersion(context: Context): Int? =
        prefs(context).getString(KEY, null)?.let { runCatching { org.json.JSONObject(it).optInt("v") }.getOrNull() }

    /** Test seam: forget the in-memory copy so the next call reloads from SharedPreferences (a process restart). */
    internal fun forgetMemory() { synchronized(this) { loaded = false; noBackup = false; state.value = AppConfig() } }
}
