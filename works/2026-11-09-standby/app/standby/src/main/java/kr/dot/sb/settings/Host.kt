package kr.dot.sb.settings

import androidx.compose.runtime.Composable
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId

/** Config edits go through this: `update { it.copy(...) }`. */
typealias Update = ((AppConfig) -> AppConfig) -> Unit

interface Nav {
    fun push(route: String)
    fun back()
}

/** Everything the screens need from the device, as plain values so render tests can fix it. */
class SettingsEnv(
    val foldable: Boolean = true,
    val granted: Map<Perm, Boolean> = Perm.entries.associateWith { false },
    val onPerm: (Perm) -> Unit = {},
    /** Live ambient light in lux, null = no sensor / no reading yet. */
    val lux: Float? = null,
    val onGlowTest: () -> Unit = {},
    val licenseNames: List<String> = emptyList(),
    val licenseText: (String) -> String = { "" },
    val versionName: String = "0.1.0",
) {
    val missing: List<Perm> get() = Perm.entries.filter { granted[it] != true }
}

object Routes {
    const val HOME = "home"
    const val BOARDS = "boards"
    const val CLOCKS = "clocks"
    const val WIDGETS = "widgets"
    const val NIGHT = "night"
    const val ALARM = "alarm"
    const val DATA = "data"
    const val PERMS = "perms"
    const val ABOUT = "about"
    fun board(id: String) = "board:$id"
    fun clock(face: FaceId) = "clock:${face.name}"
}

/** One route = one screen. Unknown routes (a board that was deleted, a stale saved route) fall back to Home. */
@Composable
fun RouteHost(route: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv) = kr.dot.sb.ui.theme.ProvideTheme(config.theme) {
    val back = { nav.back() }
    when {
        route == Routes.BOARDS -> BoardsScreen(config, update, nav, back)
        route.startsWith("board:") -> {
            val id = route.removePrefix("board:")
            if (config.boards.any { it.id == id }) BoardEditorScreen(id, config, update, nav, back) else HomeScreen(config, nav, env, update)
        }
        route == Routes.CLOCKS -> ClockListScreen(config, nav, back)
        route.startsWith("clock:") -> {
            val face = runCatching { FaceId.valueOf(route.removePrefix("clock:")) }.getOrNull()
            if (face != null) ClockStyleScreen(face, config, update, back) else HomeScreen(config, nav, env, update)
        }
        route == Routes.WIDGETS -> WidgetsScreen(config, update, back)
        route == Routes.NIGHT -> NightScreen(config, update, env, back)
        route == Routes.ALARM -> AlarmScreen(config, update, env, back)
        route == Routes.DATA -> DataScreen(config, update, back)
        route == Routes.PERMS -> PermsScreen(env, back)
        route == Routes.ABOUT -> AboutScreen(env, back)
        else -> HomeScreen(config, nav, env, update)
    }
}
