package kr.dot.sb.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.theme.clockStyle
import kr.dot.sb.model.FaceId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * plan-r13 C6: settings on short and narrow screens → build/shots/settings/small/<name>-<screen>.png. The weather sheet
 * gets a simulated keyboard (IME insets dispatched to the view, the keyboard drawn as a grey block).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsSmallShots(private val label: String, private val q: String) : SettingsShotKit() {
    private val custom = AppConfig(theme = Theme.MOODLIGHT)

    @Test fun home() = small("home", route = Routes.HOME)
    @Test fun theme() = small("theme", route = Routes.THEME)
    @Test fun customBg() = small("custom-bg", custom, Routes.THEME_CUSTOM)
    @Test fun customCard() = small("custom-card", custom, Routes.THEME_CUSTOM) { onNodeWithText("카드").performScrollTo().performClick() }
    @Test fun customText() = small("custom-text", custom, Routes.THEME_CUSTOM) { onNodeWithText("글자").performScrollTo().performClick() }
    @Test fun cards() = small("cards", route = Routes.CARDS)
    @Test fun weatherKeyboard() = small("weather-keyboard", route = Routes.CARDS, keyboard = true) { onNodeWithText("날씨").performScrollTo().performClick() }
    @Test fun ddaySheet() = small("dday-sheet", route = Routes.card(kr.dot.sb.model.CardType.DDAY)) { onNodeWithText("2026년 11월 9일").performScrollTo().performClick() }

    @Test fun appPicker() = small("app-picker", withApps, Routes.apps("desk", 0, 0))

    private fun small(name: String, config: AppConfig = AppConfig(), route: String, keyboard: Boolean = false, after: ComposeContentTestRule.() -> Unit = {}) {
        RuntimeEnvironment.setQualifiers(q)
        var view: android.view.View? = null
        var kb by mutableStateOf(0.dp)
        rule.setContent {
            view = LocalView.current
            var cfg by remember { mutableStateOf(config) }
            Box(Modifier.fillMaxSize()) {
                SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) { RouteHost(route, cfg, { f -> cfg = f(cfg) }, nav, env) }
                if (kb > 0.dp) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(kb).background(Color(0xFF3A3B40))) // literal-ok: test keyboard
            }
        }
        rule.waitForIdle()
        rule.after()
        rule.waitForIdle()
        if (keyboard) {
            val cfg = RuntimeEnvironment.getApplication().resources.configuration
            val h = cfg.screenHeightDp * if (cfg.screenWidthDp > cfg.screenHeightDp) 0.55f else 0.4f
            val px = (h * RuntimeEnvironment.getApplication().resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px)).setVisible(WindowInsetsCompat.Type.ime(), true).build()
            rule.runOnIdle { ViewCompat.dispatchApplyWindowInsets(view!!, insets); kb = h.dp }
            rule.waitForIdle()
        }
        rule.onRoot().captureRoboImage("build/shots/settings/small/$name-$label.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("land568", "w568dp-h320dp-land-xxhdpi"),
            arrayOf("land780", "w780dp-h360dp-land-xxhdpi"),
            arrayOf("fold-inner-port", "w673dp-h841dp-port-xhdpi"),
            arrayOf("cover-narrow", "w360dp-h800dp-port-xxhdpi"),
        )
    }
}
