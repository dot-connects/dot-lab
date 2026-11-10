package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The brightness bar over the clocks page, day and night → standby/build/shots/brightness/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BrightnessBarShots {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, night: Float, day: Float?) {
        rule.mainClock.autoAdvance = false
        rule.shot("brightness", name, Device.FOLD_COVER_L, Theme.IPHONE) {
            RootImpl(Fixtures.full(night), AppConfig(dayBrightness = day, nightMaxBrightness = 0.3f), StandbyActions.NONE, Modifier, { _, _ -> }, RootStart(bar = true))
        }
    }

    @Test fun day() = shot("bar-day", 0f, 0.7f)
    @Test fun night() = shot("bar-night", 1f, null)
}
