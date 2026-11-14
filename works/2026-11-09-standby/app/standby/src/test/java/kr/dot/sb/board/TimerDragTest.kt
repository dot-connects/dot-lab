package kr.dot.sb.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 11-14 #10: a drag that starts on the ring sets the minutes; a vertical swipe elsewhere still flips the stack. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimerDragTest {
    @get:Rule val rule = createComposeRule()
    private val set = mutableListOf<Int>()
    private val actions = object : StandbyActions { override fun timerSet(id: String, minutes: Int) { set += minutes } }
    private val ring = "타이머 링: 돌려서 시간 정하기"

    private fun show() {
        RuntimeEnvironment.setQualifiers("w751dp-h475dp-land-xhdpi")
        val b = Board("b", "b", Layout.ONE, listOf(listOf(CardType.TIMER, CardType.WEATHER)))
        rule.setContent { BoardView(b, Fixtures.full().copy(timers = emptyMap()), AppConfig(), actions) }
    }

    @Test fun dragOnTheRingSetsMinutes() {
        show()
        // from 12 o'clock on the ring round to 3 o'clock = 15 min
        rule.onNodeWithContentDescription(ring).performTouchInput {
            val r = width / 2f * 0.95f
            swipe(Offset(centerX + 1f, centerY - r), Offset(centerX + r, centerY), 400)
        }
        rule.waitForIdle()
        assertEquals(15, set.last())
    }

    @Test fun swipeOffTheRingFlipsTheStack() {
        show()
        // the right half of the card (the digits column), not the ring
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.8f, height * 0.8f), Offset(width * 0.8f, height * 0.2f), 300) }
        rule.waitForIdle()
        assertEquals(emptyList<Int>(), set)
        rule.onNodeWithContentDescription(ring).assertDoesNotExist()
    }
}
