package kr.dot.sb.settings

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.FaceId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders every settings screen. Long pages use a tall viewport so one PNG shows the whole page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsShots {
    @get:Rule val rule = createComposeRule()

    private val phone = "w411dp-h914dp-port-xxhdpi"
    private val tall = "w411dp-h1700dp-port-xhdpi"
    private val fold = "w932dp-h704dp-land-xhdpi"
    private val foldTall = "w932dp-h1500dp-land-xhdpi"

    private val nav = object : Nav { override fun push(route: String) {}; override fun back() {} }
    private val env = SettingsEnv(
        foldable = true,
        granted = mapOf(Perm.DREAM to false, Perm.NOTIFICATIONS to true, Perm.CALENDAR to true, Perm.PHOTOS to true, Perm.FULL_SCREEN to true, Perm.EXACT_ALARM to false, Perm.BATTERY to false),
        lux = 2.4f,
        licenseNames = listOf("LICENSE-robotoslab.txt", "OFL-inter.txt", "OFL-nunito.txt", "OFL-pretendard.txt", "OFL-sourceserif4.txt"),
        licenseText = { "Copyright 2020 The Inter Project Authors\nThis Font Software is licensed under the SIL Open Font License, Version 1.1." },
        calendars = {
            listOf(
                kr.dot.sb.data.CalendarInfo(1, "com.google", "me@example.com", "내 일정", 0xFF4D9BEF), kr.dot.sb.data.CalendarInfo(2, "com.google", "me@example.com", "대한민국 공휴일", 0xFF6FDC86),
                kr.dot.sb.data.CalendarInfo(3, "LOCAL", "", "가족", 0xFFFF9C56),
            )
        },
        photoFolders = { listOf("Camera" to 1240, "Screenshots" to 312, "여행" to 86, "KakaoTalk" to 54) },
        chargeTrail = { listOf("11월 12일 23:12 · 무선 충전 → 켜짐", "11월 11일 08:05 · 케이블 충전 → 켜짐") },
        io = kotlinx.coroutines.Dispatchers.Unconfined,
    )
    private val bar = SettingsEnv(phone = Phone.BAR, foldable = false, granted = Perm.entries.associateWith { true })

    private fun snap(name: String, qualifiers: String, config: AppConfig = AppConfig(), route: String, e: SettingsEnv = env, after: ComposeContentTestRule.() -> Unit = {}) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        rule.setContent {
            var cfg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(config) }
            SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) {
                RouteHost(route, cfg, { f -> cfg = f(cfg) }, nav, e)
            }
        }
        rule.waitForIdle()
        rule.after()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/settings/$name.png")
    }

    @Test fun home() = snap("home", phone, route = Routes.HOME)
    @Test fun homeFold() = snap("home-fold", fold, route = Routes.HOME)
    @Test fun homePaper() = snap("home-paper", phone, AppConfig(theme = kr.dot.sb.model.Theme.PAPER), route = Routes.HOME)
    @Test fun homeBar() = snap("home-bar", phone, route = Routes.HOME, e = bar)
    @Test fun homeMoodlight() = snap("home-moodlight", phone, AppConfig(theme = kr.dot.sb.model.Theme.MOODLIGHT), route = Routes.HOME)
    @Test fun theme() = snap("theme", tall, route = Routes.THEME)
    @Test fun themeFold() = snap("theme-fold", fold, route = Routes.THEME)
    @Test fun homeLook() = snap("home-look", tall, AppConfig(theme = kr.dot.sb.model.Theme.DAYPAPER, boardBg = kr.dot.sb.model.BoardBg.SOLID, textSize = kr.dot.sb.model.TextSize.LARGE), route = Routes.THEME)
    @Test fun themeNight() = snap("theme-night", phone, route = Routes.THEME) { onNodeWithText("밤").performClick() }
    /** r10 preview pattern: scrolled past the full preview, the 96 dp mini overlays the top. */
    @Test fun themeMini() = snap("theme-mini", "w411dp-h560dp-port-xxhdpi", route = Routes.THEME) {
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1400f) }
    }
    /** Pages on/off: 사진 and 음악 switched off (dimmed, gone from 처음 보이는 페이지 and the preview). */
    private val someOff = AppConfig().let { c -> c.copy(pagesOff = setOf("photos"), boards = c.boards.map { if (it.id == "music") it.copy(on = false) else it }) }
    @Test fun pages() = snap("pages", tall, someOff, route = Routes.PAGES)
    @Test fun pagesFold() = snap("pages-fold", foldTall, someOff, route = Routes.PAGES)
    @Test fun pagesAdd() = snap("pages-add", phone, BoardOps.delete(AppConfig(), 0), route = Routes.PAGES) { onNodeWithText("페이지 추가").performScrollTo().performClick() }
    @Test fun start() = snap("start", phone, someOff.copy(startPage = 5), route = Routes.START)
    @Test fun widgets() = snap("widgets", tall, route = Routes.WIDGETS)
    @Test fun widgetsFold() = snap("widgets-fold", fold, route = Routes.WIDGETS)
    private val folders = AppConfig(photoSource = kr.dot.sb.model.PhotoSource.FOLDERS, photoFolders = listOf("Camera", "여행"))
    @Test fun photos() = snap("photos", tall, folders, route = Routes.PHOTOS)
    @Test fun photosFold() = snap("photos-fold", foldTall, folders, route = Routes.PHOTOS)
    @Test fun boardEditor() = snap("board-editor", tall, route = Routes.board("desk"))
    @Test fun boardEditorFold() = snap("board-editor-fold", foldTall, route = Routes.board("modular"))
    @Test fun boardPicker() = snap("board-picker", phone, route = Routes.board("desk")) { onAllNodesWithText("카드 더하기")[0].performScrollTo().performClick() }
    private val tallest = "w411dp-h3000dp-port-xhdpi"
    @Test fun clocks() = snap("clocks", tallest, route = Routes.CLOCKS)
    @Test fun clocksFold() = snap("clocks-fold", "w932dp-h2000dp-port-xhdpi", route = Routes.CLOCKS)
    /** Scrolled past the full preview: the mini carries the face name + 꾸미기 / 빼기. */
    @Test fun clocksMini() = snap("clocks-mini", phone, route = Routes.CLOCKS) {
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1600f) }
    }
    /** A gallery clock that is not in 내 시계: [내 시계에 넣기]. */
    @Test fun clocksBrowse() = snap("clocks-browse", phone, route = Routes.CLOCKS) { onNodeWithText("레트로").performScrollTo().performClick(); onNodeWithText("전광판").performScrollTo().performClick() }
    @Test fun clockLed() = snap("clock-led", tall, route = Routes.clock(FaceId.LED))
    @Test fun clockHangul() = snap("clock-hangul", tall, route = Routes.clock(FaceId.HANGUL))
    @Test fun clockAnalog() = snap("clock-analog", tallest, route = Routes.clock(FaceId.ANALOG)) { onNodeWithText("자세히").performScrollTo().performClick() }
    @Test fun clockColorBlock() = snap("clock-colorblock", tall, route = Routes.clock(FaceId.COLOR_BLOCK))
    /** 색 조합 바꾸기 pressed once: a new combo, [이전 조합] enabled. */
    @Test fun clockShuffle() = snap("clock-shuffle", tall, route = Routes.clock(FaceId.DIGITAL)) { onNodeWithText("색 조합 바꾸기").performClick() }
    @Test fun clockSolarFold() = snap("clock-solar-fold", foldTall, route = Routes.clock(FaceId.SOLAR))
    @Test fun notif() = snap("notif", phone, route = Routes.NOTIF)
    @Test fun notifFold() = snap("notif-fold", fold, route = Routes.NOTIF)
    @Test fun night() = snap("night", tall, route = Routes.NIGHT)
    @Test fun nightFold() = snap("night-fold", foldTall, route = Routes.NIGHT)
    @Test fun nightNever() = snap("night-never", phone, AppConfig(displayOff = DisplayOff.NEVER), route = Routes.NIGHT)
    @Test fun nightLight() = snap("night-light", phone, AppConfig(theme = kr.dot.sb.model.Theme.DAYPAPER, nightMode = kr.dot.sb.model.NightMode.OFF), route = Routes.NIGHT)
    @Test fun onoff() = snap("onoff", tall, route = Routes.ONOFF) { onNodeWithText("자세히").performScrollTo().performClick() }
    @Test fun onoffFold() = snap("onoff-fold", foldTall, AppConfig(displayOff = DisplayOff.NEVER), route = Routes.ONOFF)
    @Test fun sound() = snap("sound", phone, route = Routes.SOUND) { onNodeWithText("파도").performClick() }
    @Test fun soundFold() = snap("sound-fold", fold, route = Routes.SOUND)
    @Test fun alarm() = snap("alarm", tall, route = Routes.ALARM)
    @Test fun alarmFold() = snap("alarm-fold", foldTall, route = Routes.ALARM)
    @Test fun alarmBar() = snap("alarm-bar", tall, AppConfig(alarmScreen = false), route = Routes.ALARM, e = bar)
    @Test fun foldScreen() = snap("fold", tall, route = Routes.FOLD)
    @Test fun foldScreenFold() = snap("fold-fold", foldTall, AppConfig(onUnfold = kr.dot.sb.model.UnfoldAction.MOOD_LIGHT, moodAutoOffMin = 10), route = Routes.FOLD)
    @Test fun weather() = snap("weather", phone, route = Routes.WEATHER) { onNodeWithText("자세히").performClick() }
    @Test fun weatherFold() = snap("weather-fold", fold, route = Routes.WEATHER) { onNodeWithText("자세히").performClick(); onNodeWithText("도시 더하기").performClick() }
    @Test fun cardInfo() = snap("cardinfo", tall, AppConfig(excludedCalendars = setOf("com.google|me@example.com|대한민국 공휴일")), route = Routes.CARD_INFO)
    @Test fun cardInfoFold() = snap("cardinfo-fold", foldTall, route = Routes.CARD_INFO, e = SettingsEnv(granted = mapOf(Perm.CALENDAR to false)))
    @Test fun perms() = snap("perms", tall, route = Routes.PERMS)
    @Test fun permsFold() = snap("perms-fold", fold, route = Routes.PERMS)
    @Test fun about() = snap("about", phone, route = Routes.ABOUT) { onNodeWithText("Inter").performClick() }
    @Test fun aboutFold() = snap("about-fold", fold, route = Routes.ABOUT)
}
