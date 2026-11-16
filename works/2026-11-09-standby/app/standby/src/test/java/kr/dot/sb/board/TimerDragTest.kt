package kr.dot.sb.board

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
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
import kr.dot.sb.ui.state.TimerState
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
    private var switched = 0
    private var toggled = 0
    private var boardLong = 0
    private val actions = object : StandbyActions {
        override fun timerSet(id: String, minutes: Int) { set += minutes }
        override fun timerSwitch(id: String) { switched++ }
        override fun timerToggle(id: String) { toggled++ }
    }
    private val ring = "타이머 링: 돌려서 시간 정하기"

    private fun show() {
        RuntimeEnvironment.setQualifiers("w751dp-h475dp-land-xhdpi")
        val b = Board("b", "b", Layout.ONE, listOf(listOf(CardType.TIMER, CardType.WEATHER)))
        rule.setContent { BoardView(b, Fixtures.full().copy(timers = emptyMap()), AppConfig(), actions) }
    }

    /** The board as StandbyRoot's BoardPage has it: a long press anywhere opens the page settings. */
    private fun page(qualifiers: String, layout: Layout, running: Boolean) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val slots = List(layout.slots) { if (it == 0) listOf(CardType.TIMER, CardType.WEATHER) else listOf(CardType.WEATHER) }
        val b = Board("b", "b", layout, slots)
        // a stack: the card id is slot-card
        val s = Fixtures.full().copy(timers = mapOf("b-0-0" to TimerState(leftSec = 600, totalSec = 1500, running = running)))
        rule.setContent {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { boardLong++ }) }) { BoardView(b, s, AppConfig(), actions) }
        }
    }

    private val wide = "w751dp-h475dp-land-xhdpi"
    private val flip = "w400dp-h362dp-land-xhdpi"

    @Test fun longPressInsideAStoppedRingSwitchesAndTheBoardDoesNotSee() {
        page(wide, Layout.ONE, running = false)
        rule.onNodeWithContentDescription(ring).performTouchInput { longClick(center) }
        rule.waitForIdle()
        assertEquals(1, switched)
        assertEquals(0, boardLong)
        assertEquals(emptyList<Int>(), set)
    }

    @Test fun longPressOnTheBandSwitchesWithoutSettingMinutes() {
        page(wide, Layout.ONE, running = false)
        rule.onNodeWithContentDescription(ring).performTouchInput { longClick(Offset(centerX, centerY - width / 2f * 0.93f)) }
        rule.waitForIdle()
        assertEquals(1, switched)
        assertEquals(0, boardLong)
        assertEquals(emptyList<Int>(), set)
    }

    @Test fun longPressOnARunningRingDoesNotSwitch() {
        page(wide, Layout.ONE, running = true)
        rule.onRoot().performTouchInput { longClick(Offset(width * 0.25f, height * 0.5f)) }
        rule.waitForIdle()
        assertEquals(0, switched)
    }

    @Test fun smallRingTapTogglesLongPressSwitchesNeverBoth() {
        page(flip, Layout.FOUR, running = false)
        rule.onNodeWithContentDescription(ring, substring = true).performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals(1, toggled)
        rule.onNodeWithContentDescription(ring, substring = true).performTouchInput { longClick(center) }
        rule.waitForIdle()
        assertEquals(1, toggled)
        assertEquals(1, switched)
        assertEquals(0, boardLong)
    }

    @Test fun smallRunningRingLongPressIsNotATap() {
        page(flip, Layout.FOUR, running = true)
        rule.onNodeWithContentDescription("일시정지", substring = true).performTouchInput { longClick(center) }
        rule.waitForIdle()
        assertEquals(0, toggled)
        assertEquals(0, switched)
    }

    @Test fun swipeFromInsideAStoppedRingStillFlipsTheStack() {
        page(wide, Layout.ONE, running = false)
        rule.onNodeWithContentDescription(ring).performTouchInput { swipe(Offset(centerX, centerY + height * 0.2f), Offset(centerX, centerY - height * 0.4f), 200) }
        rule.waitForIdle()
        assertEquals(0, switched)
        rule.onNodeWithContentDescription(ring).assertDoesNotExist()
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
