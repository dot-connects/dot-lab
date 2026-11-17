package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.THEMES
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Mood
import kr.dot.sb.model.MoodEffect
import kr.dot.sb.model.Theme
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.shot
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.screens.TentClock
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * plan-r15 E7/E8 → build/shots/<theme>/screens/: the outer-screen clock while the mood light is on (alone and with its
 * pull-down panel: brightness, volume, themes, then the mood rows), and the inner mood light as one flat colour.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TentShots(private val name: String, private val device: Device, private val th: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        rule.mainClock.autoAdvance = false
        val cfg = AppConfig(theme = th, onUnfold = UnfoldAction.MOOD_LIGHT, mood = Mood(0.7f, 0xFFB48CFFL, MoodEffect.BREATHE, 0.4f), ddays = Fixtures.ddays)
        rule.shot("screens", name, device, th, if (name == "mood-light") 0f else 1f) {
            when (name) {
                "mood-light" -> RootImpl(Fixtures.full().copy(unfolded = true), cfg, StandbyActions.NONE, Modifier, { _, _ -> }, RootStart())
                else -> TentClock(Fixtures.full(1f), cfg, 0.4f, {}, {}, {}, startBar = name == "tent-panel")
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{2}-{0}-{1}")
        fun cases(): List<Array<Any>> = THEMES.filter { it == Theme.IPHONE }.ifEmpty { THEMES.take(1) }.flatMap { th ->
            listOf(
                arrayOf<Any>("tent-clock", Device.FOLD_COVER_L, th),
                arrayOf<Any>("tent-panel", Device.FOLD_COVER_L, th),
                arrayOf<Any>("tent-panel", Device.FOLD_COVER_P, th),
                arrayOf<Any>("mood-light", Device.FOLD_INNER, th),
            )
        }
    }
}
