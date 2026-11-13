package kr.dot.sb.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AppConfig
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * r10 preview pattern: the mini preview only overlays the list, so scrolling never jumps. Scrolls a previewed page down
 * and back up in 10 dp steps; the position must move monotonically, the mini shows once the full preview is gone and
 * hides again at the top, on the real 시계 screen (the longest previewed page).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageScrollTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun scrollIsMonotonicAcrossTheMiniPreview() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-port-xxhdpi")
        val config = AppConfig()
        rule.setContent {
            SettingsTheme(SK.ok) {
                RouteHost(Routes.CLOCKS, config, {}, object : Nav { override fun push(route: String) {}; override fun back() {} }, SettingsEnv())
            }
        }
        val list = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst() // the page list; board slides have their own
        fun pos() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun miniShown() = rule.onAllNodes(SemanticsMatcher("mini") { n -> n.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "미리보기로 올라가기" }).fetchSemanticsNodes().isNotEmpty()
        val step = with(rule.density) { 10.dp.toPx() }

        assertTrue("mini hidden at the top", !miniShown())
        var last = pos()
        var sawMini = false
        repeat(80) {
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
            rule.waitForIdle()
            val now = pos()
            assertTrue("down: $now < $last", now >= last)
            last = now
            sawMini = sawMini || miniShown()
        }
        assertTrue("scrolled past the preview", last > 0f)
        assertTrue("mini shows once the preview is gone", sawMini)
        repeat(80) {
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -step) }
            rule.waitForIdle()
            val now = pos()
            assertTrue("up: $now > $last", now <= last)
            last = now
        }
        assertTrue("back at the top", last == 0f)
        assertTrue("mini hidden again", !miniShown())
    }
}
