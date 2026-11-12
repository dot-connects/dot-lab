package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.screens.BAR_HIDE_MS
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pull-down panel (spec-r9 §1): closes 4 s after the last touch; a theme preview tap sets the theme. Pull-and-slide: [BrightnessSwipeTest]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class TopPanelTest {
    @get:Rule val rule = createComposeRule()

    private class Rec : StandbyActions {
        val themes = mutableListOf<Theme>()
        override fun setTheme(theme: Theme) { themes += theme }
    }

    private val chip = "theme-${Theme.entries[1].name}"

    private fun show(): Rec {
        val rec = Rec()
        rule.mainClock.autoAdvance = false
        rule.setContent { RootImpl(Fixtures.full(), AppConfig(theme = Theme.entries[0]), rec, Modifier, { _, _ -> }, RootStart(bar = true)) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(chip).assertExists()
        return rec
    }

    @Test fun hidesFourSecondsAfterOpening() {
        show()
        rule.mainClock.advanceTimeBy(BAR_HIDE_MS - 300)
        rule.onNodeWithTag(chip).assertExists()
        rule.mainClock.advanceTimeBy(600) // 4 s + the 200 ms fade
        rule.onNodeWithTag(chip).assertDoesNotExist()
    }

    @Test fun themeTapSetsTheThemeAndRestartsTheHide() {
        val rec = show()
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag(chip).performClick()
        assertEquals(listOf(Theme.entries[1]), rec.themes)
        rule.mainClock.advanceTimeBy(BAR_HIDE_MS - 300) // 6.7 s after opening: still up, the tap restarted the 4 s
        rule.onNodeWithTag(chip).assertExists()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag(chip).assertDoesNotExist()
    }
}
