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
    /** plan-r15 F1 지금 보기: open Stand Screen now (the tile path and its low-battery guard). */
    val onShowNow: () -> Unit = {},
    /** plan-r15 F3: ask these [Perm]s in this order, each missing one once (first-run step 3, 자동으로 켜기·끄기). */
    val onPermWalkOf: (List<Perm>) -> Unit = {},
    /** plan-r15 F3: the first run finished or was skipped (it never opens again by itself). */
    val onFirstRunDone: () -> Unit = {},
    /** plan-r15 F8 처음 설정으로: defaults back, folder grants released, the first run opens again. */
    val onReset: () -> Unit = {},
    /** plan-r15 F12: this week's events of every calendar (the picker's counts); null = no calendar permission. Slow, on [io]. */
    val weekEvents: () -> List<kr.dot.sb.data.WeekEvent>? = { null },
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
    /** Legacy browse route, now opening the add screen. */
    const val CLOCKS_ALL = "clocks_all"
    const val CLOCKS_ROTATE = "clocks_rotate"
    /** plan-r15 F3: the four first-run steps. */
    const val FIRST_RUN = "first_run"
    /** plan-r15 F1 알림과 알람 (the old 알림 and 알람 screens and the Fold alarm rows). */
    const val ALERTS = "alerts"
    /** plan-r15 F10 팁. */
    const val TIPS = "tips"
    /** Old routes (a saved stack, StandBy's links): 알림 / 알람 open [ALERTS], 폴드 opens [ONOFF], 카드 opens [PAGES]. */
    const val NOTIF = "notif"
    const val ALARM = "alarm"
    const val FOLD = "fold"
    const val CARDS = "cards"
    const val CARD_LIST = "cards-list"
    const val NIGHT = "night"
    /** 자동으로 켜기·끄기 (plan-r15 F1b: with 펼쳤을 때 on a Fold). */
    const val ONOFF = "onoff"
    const val SOUND = "sound"
    const val PERMS = "perms"
    /** 정보 (plan-r15 F8). */
    const val ABOUT = "about"
    /** One card type's global settings. */
    fun card(t: kr.dot.sb.model.CardType) = "card:${t.name}"
    fun board(id: String) = "board:$id"
    fun clock(face: FaceId) = "clock:${face.name}"
    /** Legacy per-instance route, resolved to the current global app-card settings. */
    fun apps(board: String, slot: Int, index: Int) = "apps:$board:$slot:$index"
}

/** One route = one screen. Unknown routes (a board that was deleted, a stale saved route) fall back to Home. */
@Composable
fun RouteHost(route: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv) = kr.dot.sb.ui.theme.ProvideTheme(config, androidx.compose.runtime.remember { java.time.LocalTime.now() }) {
    // the page-settings screens share one 낮|밤 state, so it survives a swipe to the next page's settings
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
        route.startsWith("apps:") -> {
            val target = legacyCardRoute(route, config)
            if (target != null) Route(target, config, update, nav, env) else HomeScreen(config, nav, env)
        }
        route == Routes.CLOCKS -> ClockScreen(config, update, nav, back)
        route == Routes.CLOCKS_ALL || route == Routes.CLOCKS_ROTATE -> RotateClocksScreen(config, update, back)
        route.startsWith("clock:") -> {
            val face = runCatching { FaceId.valueOf(route.removePrefix("clock:")) }.getOrNull()
            if (face != null) ClockStyleScreen(face, config, update, back) else HomeScreen(config, nav, env)
        }
        route == Routes.FIRST_RUN -> FirstRunScreen(config, update, env)
        route == Routes.ALERTS || route == Routes.NOTIF || route == Routes.ALARM -> AlertsScreen(config, update, env, back)
        route == Routes.NIGHT -> NightScreen(config, update, nav, env, back)
        route == Routes.ONOFF -> OnOffScreen(config, update, nav, env, back)
        route == Routes.FOLD -> FoldScreen(config, update, env, back)
        route == Routes.SOUND -> CardScreen(kr.dot.sb.model.CardType.SLEEP, config, update, nav, env, back)
        route == Routes.PERMS -> PermsScreen(config, env, back)
        route == Routes.ABOUT -> AboutScreen(config, nav, env, back)
        route == Routes.TIPS -> TipsScreen(config, update, back)
        route == Routes.CARDS -> PagesScreen(config, update, nav, back)
        route == Routes.CARD_LIST -> CardListScreen(config, nav, back)
        route.startsWith("card:") -> {
            val t = runCatching { kr.dot.sb.model.CardType.valueOf(route.removePrefix("card:")) }.getOrNull()
            when {
                t == null -> HomeScreen(config, nav, env)
                t == kr.dot.sb.model.CardType.APPS -> AppPickerScreen(config, update, env, back)
                cardRoute(t) != route -> Route(cardRoute(t), config, update, nav, env)
                else -> CardScreen(t, config, update, nav, env, back)
            }
        }
        else -> HomeScreen(config, nav, env)
    }
}
