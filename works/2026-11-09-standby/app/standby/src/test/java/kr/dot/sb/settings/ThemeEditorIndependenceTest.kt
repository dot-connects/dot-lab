package kr.dot.sb.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Operator 11-15: 카드 진하기 set, then a new 강조 색 — the alpha must stay (each editor row is independent). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeEditorIndependenceTest : SettingsShotKit() {
    @Test fun alphaSurvivesAccentPick() {
        RuntimeEnvironment.setQualifiers(tallest)
        var cfg = AppConfig(theme = Theme.IPHONE)
        rule.setContent {
            var c by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(cfg) }
            SettingsTheme(c.clockStyle(FaceId.DIGITAL).color.c()) {
                RouteHost(Routes.THEME_CUSTOM, c, { f -> c = f(c); cfg = c }, nav, env)
            }
        }
        rule.waitForIdle()
        // the first slider of the 카드 tab is 카드 진하기 (모서리 comes after it); the tabs are under 더 꾸미기 (F9)
        rule.onNodeWithText("더 꾸미기").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("카드").performScrollTo().performClick()
        rule.waitForIdle()
        val sliders = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        sliders[0].performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.4f) }
        rule.waitForIdle()
        val alpha = cfg.themeEdits[Theme.IPHONE]?.plateAlpha
        assertNotNull("slider saved no alpha", alpha)
        rule.onNodeWithText("글자").performScrollTo().performClick()
        rule.waitForIdle()
        // a swatch of 강조 색: the clickable dots right under its title
        val title = rule.onNodeWithText("강조 색").performScrollTo().fetchSemanticsNode().boundsInRoot
        val dots = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top > title.bottom && it.boundsInRoot.top < title.bottom + 200f && it.boundsInRoot.width < 200f }
            .sortedBy { it.boundsInRoot.left }
        val target = dots[2]
        rule.onAllNodes(SemanticsMatcher("id") { it.id == target.id })[0].performClick()
        rule.waitForIdle()
        assertNotNull("accent pick saved nothing", cfg.themeEdits[Theme.IPHONE]?.accent)
        assertEquals(alpha, cfg.themeEdits[Theme.IPHONE]?.plateAlpha)
    }
    /** Review r13 H1: after a "어디서 시작할까요" chip, a slider writes the new theme's edit, never the old one's. */
    @Test fun editAfterStartChipLandsOnTheNewTheme() {
        RuntimeEnvironment.setQualifiers(tallest)
        var cfg = AppConfig(theme = Theme.IPHONE)
        rule.setContent {
            var c by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(cfg) }
            SettingsTheme(c.clockStyle(FaceId.DIGITAL).color.c()) {
                RouteHost(Routes.THEME_CUSTOM, c, { f -> c = f(c); cfg = c }, nav, env)
            }
        }
        rule.waitForIdle()
        // the 카드 rows are composed under IPHONE first, then the chip switches the theme under them
        rule.onNodeWithText("더 꾸미기").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("카드").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(Theme.MOODLIGHT.ko).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Theme.MOODLIGHT, cfg.theme)
        val sliders = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        sliders[0].performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.13f) }
        rule.waitForIdle()
        assertNotNull("the slider saved nothing on the new theme", cfg.themeEdits[Theme.MOODLIGHT])
        assertEquals(setOf(Theme.MOODLIGHT), cfg.themeEdits.keys)
    }
}
