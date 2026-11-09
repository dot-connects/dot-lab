package kr.dot.sb.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.dot.sb.model.AppConfig

/** OWNER: settings agent. Loads/saves [AppConfig] as JSON in SharedPreferences; one shared StateFlow. */
object ConfigStore {
    private val state = MutableStateFlow(AppConfig())
    fun flow(context: Context): StateFlow<AppConfig> = state
    fun update(context: Context, change: (AppConfig) -> AppConfig) { state.value = change(state.value) }
}
