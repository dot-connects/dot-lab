package kr.dot.sb.board

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
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

/** 11-14 #7: tap a clock card → arrows; the right arrow steps through 내 시계; 3 s later the pick is saved to that card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FaceArrowsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tapStepAndSaveAfterThreeSeconds() {
        RuntimeEnvironment.setQualifiers("w751dp-h475dp-land-xhdpi")
        val saved = mutableListOf<String>()
        val actions = object : StandbyActions {
            override fun setCardFace(boardId: String, slot: Int, index: Int, face: FaceId) { saved += "$boardId $slot:$index $face" }
        }
        val config = AppConfig(myClocks = listOf(FaceId.DIGITAL, FaceId.FLIP, FaceId.WALL))
        val b = Board("b1", "b", Layout.ONE, listOf(listOf(CardType.CLOCK)))
        rule.mainClock.autoAdvance = false
        rule.setContent { BoardView(b, Fixtures.full(), config, actions) }
        rule.onRoot().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("디지털 · 1 / 3").assertExists()
        rule.onNode(androidx.compose.ui.test.SemanticsMatcher("다음 시계") { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label == "다음 시계" })
            .performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("플립 시계 · 2 / 3").assertExists()
        rule.mainClock.advanceTimeBy(2_500)
        assertEquals(emptyList<String>(), saved) // still browsing
        rule.mainClock.advanceTimeBy(600)
        assertEquals(listOf("b1 0:0 FLIP"), saved)
        rule.onNodeWithText("플립 시계 · 2 / 3").assertDoesNotExist()
    }
}
