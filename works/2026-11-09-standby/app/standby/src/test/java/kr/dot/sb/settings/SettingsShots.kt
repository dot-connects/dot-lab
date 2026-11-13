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
    )

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
    @Test fun homeLook() = snap("home-look", tall, AppConfig(theme = kr.dot.sb.model.Theme.DAYPAPER, boardBg = kr.dot.sb.model.BoardBg.SOLID, textSize = kr.dot.sb.model.TextSize.LARGE), route = Routes.THEME)
    @Test fun homeBar() = snap("home-bar", phone, route = Routes.HOME, e = SettingsEnv(phone = Phone.BAR, foldable = false, granted = Perm.entries.associateWith { true }))
    @Test fun themeNight() = snap("theme-night", phone, route = Routes.THEME) { onNodeWithText("밤").performClick() }
    /** r10 preview pattern: scrolled past the full preview, the 96 dp mini overlays the top. */
    @Test fun themeMini() = snap("theme-mini", "w411dp-h560dp-port-xxhdpi", route = Routes.THEME) {
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1400f) }
    }
    @Test fun homeMoodlight() = snap("home-moodlight", phone, AppConfig(theme = kr.dot.sb.model.Theme.MOODLIGHT), route = Routes.HOME)
    @Test fun boards() = snap("boards", tall, route = Routes.BOARDS)
    @Test fun boardEditor() = snap("board-editor", tall, route = Routes.board("desk"))
    @Test fun boardEditorFold() = snap("board-editor-fold", foldTall, route = Routes.board("modular"))
    @Test fun boardPicker() = snap("board-picker", phone, route = Routes.board("desk")) { onAllNodesWithText("카드 더하기")[0].performScrollTo().performClick() }
    @Test fun clocks() = snap("clocks", tall, route = Routes.CLOCKS)
    @Test fun clockDigital() = snap("clock-digital", tall, route = Routes.clock(FaceId.DIGITAL))
    @Test fun clockAnalog() = snap("clock-analog", tall, route = Routes.clock(FaceId.ANALOG))
    @Test fun clockSolarFold() = snap("clock-solar-fold", foldTall, route = Routes.clock(FaceId.SOLAR))
    @Test fun widgets() = snap("widgets", phone, route = Routes.WIDGETS)
    @Test fun night() = snap("night", tall, route = Routes.NIGHT)
    @Test fun nightNever() = snap("night-never", phone, AppConfig(displayOff = DisplayOff.NEVER), route = Routes.NIGHT)
    @Test fun alarm() = snap("alarm", phone, route = Routes.ALARM)
    @Test fun data() = snap("data", tall, route = Routes.DATA)
    @Test fun perms() = snap("perms", tall, route = Routes.PERMS)
    @Test fun about() = snap("about", phone, route = Routes.ABOUT) { onNodeWithText("Inter").performClick() }
}
