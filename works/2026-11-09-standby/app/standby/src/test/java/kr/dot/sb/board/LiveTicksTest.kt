package kr.dot.sb.board

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.LiveTicks
import kr.dot.sb.ui.state.LocalLiveTicks
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

/** r11 perf item 8 (timer ticks outside StandbyState) and item 6 (media buttons named, disabled when they cannot act). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LiveTicksTest {
    @get:Rule val rule = createComposeRule()

    @Test fun timerCardReadsTheTicksNotTheState() {
        RuntimeEnvironment.setQualifiers(Device.FOLD_COVER_L.qualifiers)
        val board = Board("b", "", Layout.ONE, listOf(listOf(CardType.TIMER)))
        val ticks = mutableStateOf(LiveTicks(timers = mapOf("b-0" to TimerState(leftSec = 12 * 60 + 5, running = true))))
        val state = Fixtures.full().copy(timers = mapOf("b-0" to TimerState(leftSec = 3 * 60, running = true)))
        rule.setContent { CompositionLocalProvider(LocalLiveTicks provides ticks) { BoardView(board, state, AppConfig(), StandbyActions.NONE) } }
        rule.onNodeWithText("12:05", substring = true).assertExists()
        ticks.value = LiveTicks(timers = mapOf("b-0" to TimerState(leftSec = 12 * 60 + 4, running = true)))
        rule.waitForIdle()
        rule.onNodeWithText("12:04", substring = true).assertExists()
    }

    @Test fun mediaButtonsAreNamedButtons() {
        RuntimeEnvironment.setQualifiers(Device.FOLD_COVER_L.qualifiers)
        val board = Board("b", "", Layout.TWO, listOf(listOf(CardType.MUSIC), listOf(CardType.CLOCK)))
        val s = Fixtures.full().let { it.copy(media = it.media!!.copy(canPrev = false, art = null)) }
        rule.setContent { BoardView(board, s, AppConfig(), StandbyActions.NONE) }
        rule.onNodeWithContentDescription("일시정지").assertExists()
        rule.onNodeWithContentDescription("다음 곡").assertExists()
        rule.onNodeWithContentDescription("이전 곡").assertIsNotEnabled()
        val role = rule.onNodeWithContentDescription("다음 곡").fetchSemanticsNode().config[SemanticsProperties.Role]
        assertEquals(androidx.compose.ui.semantics.Role.Button, role)
    }

    @Test fun timerResetIsNamedReset() {
        RuntimeEnvironment.setQualifiers(Device.FOLD_COVER_L.qualifiers)
        val board = Board("b", "", Layout.ONE, listOf(listOf(CardType.TIMER)))
        val s = Fixtures.full().copy(timers = mapOf("b-0" to TimerState(leftSec = 3 * 60, running = true)))
        rule.setContent { BoardView(board, s, AppConfig(), StandbyActions.NONE) }
        rule.onNodeWithContentDescription("타이머 처음으로").assertExists()
        rule.onNodeWithContentDescription("일시정지").assertExists()
        assertEquals(0, rule.onAllNodesWithContentDescription("재생").fetchSemanticsNodes().size)
    }
}
