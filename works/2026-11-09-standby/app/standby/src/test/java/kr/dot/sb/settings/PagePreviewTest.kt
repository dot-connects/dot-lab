package kr.dot.sb.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.PAGE_PHOTOS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 11-14: a page's own settings preview lists every page in pager order and opens on its own page; a swipe that settles
 * on another page replaces the route with that page's settings (no back step).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PagePreviewTest {
    @get:Rule val rule = createComposeRule()

    private class Rec : Nav {
        val log = mutableListOf<String>()
        override fun push(route: String) { log += "push $route" }
        override fun back() { log += "back" }
        override fun replace(route: String) { log += "replace $route" }
    }

    @Test
    fun ownPageFirstEvenWhenOff() {
        val c = AppConfig(pagesOff = setOf(PAGE_PHOTOS))
        val p = pagePreview(c, 1, Slide("사진") {}, Rec())
        assertEquals(listOf("위젯", "사진", "시계"), p.slides.take(3).map { it.name })
        assertEquals(1, p.initial)
        assertEquals(listOf(Routes.WIDGETS, Routes.PHOTOS, Routes.CLOCKS), p.slides.take(3).map { it.route })
        val board = c.boards.first()
        assertEquals(Routes.board(board.id), p.slides[3].route)
    }

    private fun swipe(route: String, left: Boolean): List<String> {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-port-xxhdpi")
        val nav = Rec()
        rule.setContent { SettingsTheme(SK.ok) { RouteHost(route, AppConfig(), {}, nav, SettingsEnv()) } }
        rule.onNodeWithText("옆으로 넘기면 다른 페이지 설정").assertExists()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).onFirst()
            .performTouchInput { if (left) swipeLeft() else swipeRight() }
        rule.waitForIdle()
        return nav.log
    }

    @Test fun swipeOnPhotosOpensClock() = assertEquals(listOf("replace ${Routes.CLOCKS}"), swipe(Routes.PHOTOS, left = true))

    @Test fun swipeBackOnPhotosOpensWidgets() = assertEquals(listOf("replace ${Routes.WIDGETS}"), swipe(Routes.PHOTOS, left = false))
}
