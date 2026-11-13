package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.THEMES
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pull-down panel over the widgets page in every theme (+ IPHONE night), and light themes on a dark board
 * background (Task 20: the dark twin) → build/shots/<theme>/brightness/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BrightnessBarShots(private val name: String, private val night: Float, private val bar: Boolean, private val bg: BoardBg, private val device: Device, private val th: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        rule.mainClock.autoAdvance = false
        rule.shot("brightness", name, device, th, night) {
            val cfg = AppConfig(theme = th, dayBrightness = 0.7f, nightMaxBrightness = 0.3f, boardBg = bg, startPage = 0, ddays = Fixtures.ddays)
            RootImpl(Fixtures.full(night), cfg, StandbyActions.NONE, Modifier, { _, _ -> }, RootStart(bar = bar))
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{5}-{0}-{4}")
        fun cases(): List<Array<Any>> = THEMES.flatMap { th ->
            val day = listOf(Device.FOLD_COVER_L, Device.FLIP).map { arrayOf<Any>("panel-day", 0f, true, BoardBg.THEME, it) }
            val night = if (th == Theme.IPHONE) listOf(arrayOf<Any>("panel-night", 1f, true, BoardBg.THEME, Device.FOLD_COVER_L)) else emptyList()
            val dark = if (th == Theme.DAYPAPER || th == Theme.RADIO) listOf(
                arrayOf<Any>("bg-graphite", 0f, false, BoardBg.GRAPHITE, Device.FOLD_COVER_L),
                arrayOf<Any>("bg-graphite-panel", 0f, true, BoardBg.GRAPHITE, Device.FOLD_COVER_P),
            ) else emptyList()
            (day + night + dark).map { it + th }
        }
    }
}
