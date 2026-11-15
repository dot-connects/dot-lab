package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.board.BoardView
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
 * plan-r13 C6: the pull-down panel and the alarm card's 빠른 알람 sheet / 직접 정하기 setter on a 4-up board, on the
 * extra screens (Fold inner portrait, narrow cover, 300 dp Flip) → build/shots/iphone/small/. A slot too small for the
 * card's buttons shows the card alone.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SmallScreenShots(private val device: Device) {
    @get:Rule val rule = createComposeRule()

    private val board = Board("card", "ALARM", Layout.FOUR, listOf(listOf(CardType.ALARM), listOf(CardType.WEATHER), listOf(CardType.CALENDAR), listOf(CardType.MUSIC)))

    @Test fun panel() {
        rule.mainClock.autoAdvance = false
        rule.shot("small", "panel", device, Theme.IPHONE) {
            RootImpl(Fixtures.full(), AppConfig(startPage = 0, ddays = Fixtures.ddays), StandbyActions.NONE, Modifier, { _, _ -> }, RootStart(bar = true))
        }
    }

    @Test fun quick() = rule.shot("small", "alarm-sheet", device, Theme.IPHONE, act = { open() }) { BoardView(board, Fixtures.full(), AppConfig(), object : StandbyActions {}) }

    @Test fun setter() = rule.shot("small", "alarm-setter", device, Theme.IPHONE, act = {
        if (open()) { onNodeWithText("직접 정하기").performClick(); waitForIdle() }
    }) { BoardView(board, Fixtures.full(), AppConfig(), object : StandbyActions {}) }

    private fun open(): Boolean {
        if (rule.onAllNodesWithText("빠른 알람").fetchSemanticsNodes().isEmpty()) return false
        rule.onNodeWithText("빠른 알람").performClick()
        rule.waitForIdle()
        return true
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = Device.entries.filter { it.extra }.map { arrayOf<Any>(it) }
    }
}
