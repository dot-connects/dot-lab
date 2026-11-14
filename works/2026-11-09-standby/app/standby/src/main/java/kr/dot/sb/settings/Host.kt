package kr.dot.sb.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId

/** Config edits go through this: `update { it.copy(...) }`. */
typealias Update = ((AppConfig) -> AppConfig) -> Unit

interface Nav {
    fun push(route: String)
    fun back()
    /** Swap the current screen for [route] without a back step (a page's settings -> the page swiped to). */
    fun replace(route: String) { back(); push(route) }
}

/** Everything the screens need from the device, as plain values so render tests can fix it. */
class SettingsEnv(
    val foldable: Boolean = true,
    /** Fold / Flip / bar: what the preview's 커버|안쪽 toggle draws (none on a bar phone). */
    val phone: Phone = Phone.FOLD,
    val granted: Map<Perm, Boolean> = Perm.entries.associateWith { false },
    val onPerm: (Perm) -> Unit = {},
    /** Live ambient light in lux, null = no sensor / no reading yet. */
    val lux: Float? = null,
    val onGlowTest: () -> Unit = {},
    val licenseNames: List<String> = emptyList(),
    val licenseText: (String) -> String = { "" },
    val versionName: String = "0.1.0",
    /** Phone widget (r9 D6): pick a provider, bind it, run its configure screen; [done] gets the id (never on cancel). */
    val pickWidget: (done: (Int) -> Unit) -> Unit = {},
    /** The bound widget's own settings screen (its configure activity). */
    val configureWidget: (Int) -> Unit = {},
    /** "앱 · 위젯" for a bound id, null when it is gone. */
    val widgetLabel: (Int) -> String? = { null },
    /** Every calendar on the phone (카드에 쓰는 정보 picker); null = no calendar permission. */
    val calendars: () -> List<kr.dot.sb.data.CalendarInfo>? = { null },
    /** Albums with their photo counts (사진 고르기). */
    val photoFolders: () -> List<Pair<String, Int>> = { emptyList() },
    /** 수면 소리: play [SleepSound] for 3 s. */
    val playSample: (kr.dot.sb.model.SleepSound) -> Unit = {},
    /** Recent charger-start log lines (켜짐과 꺼짐 자세히). */
    val chargeTrail: () -> List<String> = { emptyList() },
    /** Where the slow lookups above run (shots pass Dispatchers.Unconfined so they load inline). */
    val io: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.IO,
    /** Today (D-day count and the new D-day's default date); shots pin it so their PNGs do not change per day. */
    val today: () -> java.time.LocalDate = { java.time.LocalDate.now() },
) {
    val missing: List<Perm> get() = Perm.entries.filter { granted[it] != true }
}

/** The env for components deep in a screen ([PermLine], the preview bar); [RouteHost] provides it. */
val LocalSettingsEnv = compositionLocalOf { SettingsEnv() }

object Routes {
    const val HOME = "home"
    const val THEME = "theme"
    const val PAGES = "pages"
    const val START = "start"
    const val WIDGETS = "widgets"
    const val PHOTOS = "photos"
    const val CLOCKS = "clocks"
    const val NOTIF = "notif"
    const val NIGHT = "night"
    const val ONOFF = "onoff"
    const val SOUND = "sound"
    const val ALARM = "alarm"
    const val FOLD = "fold"
    const val WEATHER = "weather"
    const val CARD_INFO = "cardinfo"
    const val PERMS = "perms"
    const val ABOUT = "about"
    /** 카드 (11-14): the card list; [card] = one card's settings. */
    const val CARDS = "cards"
    fun card(t: kr.dot.sb.model.CardType) = "card:${t.name}"
    fun board(id: String) = "board:$id"
    fun clock(face: FaceId) = "clock:${face.name}"
    /** Options of the phone widget at stack [index] of [slot] (also opened by a long press on the card). */
    fun widget(board: String, slot: Int, index: Int) = "widget:$board:$slot:$index"
}

/** One route = one screen. Unknown routes (a board that was deleted, a stale saved route) fall back to Home. */
@Composable
fun RouteHost(route: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv) = kr.dot.sb.ui.theme.ProvideTheme(config) {
    // the page-settings screens share one preview bar state, so 커버/세로/밤 survive a swipe to the next page's settings
    val pageBar = androidx.compose.runtime.saveable.rememberSaveable(saver = PreviewState.Saver) { PreviewState() }
    CompositionLocalProvider(LocalSettingsEnv provides env, LocalPageBar provides pageBar) { Route(route, config, update, nav, env) }
}

@Composable
private fun Route(route: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv) {
    val back = { nav.back() }
    when {
        route == Routes.THEME -> ThemeScreen(config, update, back)
        route == Routes.PAGES -> PagesScreen(config, update, nav, back)
        route == Routes.START -> StartPageScreen(config, update, back)
        route == Routes.WIDGETS -> WidgetsScreen(config, update, nav, back)
        route == Routes.PHOTOS -> PhotosScreen(config, update, nav, env, back)
        route.startsWith("board:") -> {
            val id = route.removePrefix("board:")
            if (config.boards.any { it.id == id }) BoardEditorScreen(id, config, update, nav, env, back) else HomeScreen(config, nav, env)
        }
        route.startsWith("widget:") -> WidgetOptionsScreen(route, config, update, env, back) ?: HomeScreen(config, nav, env)
        route == Routes.CLOCKS -> ClockScreen(config, update, nav, back)
        route.startsWith("clock:") -> {
            val face = runCatching { FaceId.valueOf(route.removePrefix("clock:")) }.getOrNull()
            if (face != null) ClockStyleScreen(face, config, update, back) else HomeScreen(config, nav, env)
        }
        route == Routes.NOTIF -> NotifScreen(config, update, back)
        route == Routes.NIGHT -> NightScreen(config, update, nav, env, back)
        route == Routes.ONOFF -> OnOffScreen(config, update, env, back)
        route == Routes.SOUND -> SoundScreen(config, update, env, back)
        route == Routes.ALARM -> AlarmScreen(config, update, nav, env, back)
        route == Routes.FOLD -> FoldScreen(config, update, env, back)
        route == Routes.WEATHER -> WeatherScreen(config, update, back)
        route == Routes.CARD_INFO -> CardInfoScreen(config, update, env, back)
        route == Routes.PERMS -> PermsScreen(env, back)
        route == Routes.ABOUT -> AboutScreen(env, back)
        route == Routes.CARDS -> CardsScreen(config, nav, env, back)
        route.startsWith("card:") -> {
            val t = runCatching { kr.dot.sb.model.CardType.valueOf(route.removePrefix("card:")) }.getOrNull()
            if (t != null) CardScreen(t, config, update, env, back) else HomeScreen(config, nav, env)
        }
        else -> HomeScreen(config, nav, env)
    }
}
