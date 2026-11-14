package kr.dot.sb.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * State below [ProvideTheme] (the settings preview's pager page, the theme strip's scroll) must survive a theme switch.
 * It used to skip the provider when the theme equalled the outer one (기본 = the default), so switching to or from 기본
 * changed the composition's shape and reset everything remembered below it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProvideThemeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun rememberedStateSurvivesThemeSwitches() {
        var config by mutableStateOf(AppConfig(theme = Theme.PAPER))
        val seen = mutableListOf<Any>()
        rule.setContent { ProvideTheme(config) { LocalTheme.current; seen += remember { Any() } } }
        for (th in listOf(Theme.IPHONE, Theme.NEON, Theme.IPHONE, Theme.PAPER)) {
            config = config.withTheme(th)
            rule.waitForIdle()
        }
        assertEquals("one remembered object across every switch", 1, seen.distinct().size)
    }
}
