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
    /** 앱 고르기 (plan-r14 C1): the phone's launcher apps, sorted by label; slow, called on [io]. */
    val launcherApps: () -> List<kr.dot.sb.ui.cards.AppEntry> = { emptyList() },
    /** plan-r13 B1: a fresh install opens 권한 first; [나중에] ends that ([onPermLater]). */
    val permWalkOffer: Boolean = false,
    /** [차례로 켜기]: every missing permission in turn. */
    val onPermWalk: () -> Unit = {},
    val onPermLater: () -> Unit = {},
    /** Every calendar on the phone (카드에 쓰는 정보 picker); null = no calendar permission. */
    val calendars: () -> List<kr.dot.sb.data.CalendarInfo>? = { null },
    /** plan-r15 B1: the picked folders read through the shared photo index (names, counts, broken); slow, on [io]. */
    val photoFolders: (List<String>) -> List<kr.dot.sb.data.PhotoFolder> = { f -> f.map { kr.dot.sb.data.PhotoFolder(it, it, null, false) } },
    /** 폴더 고르기: the system folder picker; the picked tree is added to the config. */
    val pickFolder: () -> Unit = {},
    /** 빼기: the folder leaves the config and its grant is released. */
    val removeFolder: (String) -> Unit = {},
    /** 수면 소리: play [SleepSound] for 3 s. */
    val playSample: (kr.dot.sb.model.SleepSound) -> Unit = {},
    /** Recent charger-start log lines (켜짐과 꺼짐 자세히). */
    val chargeTrail: () -> List<String> = { emptyList() },
    /** plan-r15 measurement log (personal build, 정보): charge-start grants and paths, posture samples, fold postures. */
    val measureLog: () -> List<String> = { emptyList() },
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
    /** r13 사용자 지정 (the strip's ＋). */
    const val THEME_CUSTOM = "theme_custom"
    /** r14 B5: 사용자 지정 in 고치기 mode for the 내 테마 [name]. */
    fun themeEdit(name: String) = "theme_custom:$name"
    const val PAGES = "pages"
    const val WIDGETS = "widgets"
    const val PHOTOS = "photos"
    const val CLOCKS = "clocks"
    /** plan-r15 E5: 모든 시계 and 여러 시계 돌려 보기 under 시계. */
    const val CLOCKS_ALL = "clocks_all"
    const val CLOCKS_ROTATE = "clocks_rotate"
    const val NOTIF = "notif"
    const val NIGHT = "night"
    const val ONOFF = "onoff"
    const val SOUND = "sound"
    const val ALARM = "alarm"
    const val FOLD = "fold"
    const val PERMS = "perms"
    const val ABOUT = "about"
    /** 카드 (11-14): the card list; [card] = one card's settings. */
    const val CARDS = "cards"
    fun card(t: kr.dot.sb.model.CardType) = "card:${t.name}"
    fun board(id: String) = "board:$id"
    fun clock(face: FaceId) = "clock:${face.name}"
    /** 앱 고르기 of the 앱 바로가기 card at stack [index] of [slot] (also opened by a tap on the empty card in StandBy). */
    fun apps(board: String, slot: Int, index: Int) = "apps:$board:$slot:$index"
}

/** One route = one screen. Unknown routes (a board that was deleted, a stale saved route) fall back to Home. */
@Composable
fun RouteHost(route: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv) = kr.dot.sb.ui.theme.ProvideTheme(config, androidx.compose.runtime.remember { java.time.LocalTime.now() }) {
    // the page-settings screens share one preview bar state, so 커버/세로/밤 survive a swipe to the next page's settings
    val pageBar = androidx.compose.runtime.saveable.rememberSaveable(saver = PreviewState.Saver) { PreviewState() }
    CompositionLocalProvider(LocalSettingsEnv provides env, LocalPageBar provides pageBar) {
        // one state per route: nav.replace to the next board's settings must not reuse this one's remembered state
        androidx.compose.runtime.key(route) { Route(route, config, update, nav, env) }
    }
}

@Composable
private fun Route(route: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv) {
    val back = { nav.back() }
    when {
        route == Routes.THEME -> ThemeScreen(config, update, nav, back)
        route == Routes.THEME_CUSTOM -> CustomThemeScreen(config, update, back)
        route.startsWith("theme_custom:") -> CustomThemeScreen(config, update, back, editing = route.removePrefix("theme_custom:"))
        route == Routes.PAGES -> PagesScreen(config, update, nav, back)
        route == Routes.WIDGETS -> WidgetsScreen(config, update, nav, back)
        route == Routes.PHOTOS -> PhotosScreen(config, update, nav, env, back)
        route.startsWith("board:") -> {
            val id = route.removePrefix("board:")
            if (config.boards.any { it.id == id }) BoardEditorScreen(id, config, update, nav, env, back) else HomeScreen(config, nav, env)
        }
        route.startsWith("apps:") -> AppPickerScreen(route, config, update, env, back) ?: HomeScreen(config, nav, env)
        route == Routes.CLOCKS -> ClockScreen(config, update, nav, back)
        route == Routes.CLOCKS_ALL -> AllClocksScreen(config, update, back)
        route == Routes.CLOCKS_ROTATE -> RotateClocksScreen(config, update, back)
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
        route == Routes.PERMS -> PermsScreen(env, back)
        route == Routes.ABOUT -> AboutScreen(env, back)
        route == Routes.CARDS -> CardsScreen(config, update, nav, env, back)
        route.startsWith("card:") -> {
            val t = runCatching { kr.dot.sb.model.CardType.valueOf(route.removePrefix("card:")) }.getOrNull()
            if (t != null) CardScreen(t, config, update, env, back) else HomeScreen(config, nav, env)
        }
        else -> HomeScreen(config, nav, env)
    }
}
