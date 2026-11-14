package kr.dot.sb.board

import androidx.compose.ui.test.junit4.createComposeRule
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
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 11-14 #8: the alarm card's 빠른 알람 sheet and its 직접 정하기 setter, in the shortest slot that has buttons (Sw). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AlarmSheetShots {
    @get:Rule val rule = createComposeRule()

    private val board = Board("card", "ALARM", Layout.FOUR, listOf(listOf(CardType.ALARM), listOf(CardType.WEATHER), listOf(CardType.CALENDAR), listOf(CardType.MUSIC)))
    private val live = object : StandbyActions {}

    @Test fun sheet() = rule.shot("board", "alarm-sheet", Device.FOLD_COVER_L, Theme.IPHONE, act = { onNodeWithText("빠른 알람").performClick(); waitForIdle() }) {
        BoardView(board, Fixtures.full(), AppConfig(), live)
    }

    @Test fun setter() = rule.shot("board", "alarm-setter", Device.FOLD_COVER_L, Theme.IPHONE, act = {
        onNodeWithText("빠른 알람").performClick(); waitForIdle(); onNodeWithText("직접 정하기").performClick(); waitForIdle()
    }) { BoardView(board, Fixtures.full(), AppConfig(), live) }
}
