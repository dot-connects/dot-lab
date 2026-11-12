package kr.dot.sb.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Notification card (spec-r9 §1): a row opens it; 10 s without a touch or "접기" closes it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class NotifCardTest {
    @get:Rule val rule = createComposeRule()

    private fun open() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.size(340.dp, 420.dp)) { CardView(CardType.NOTIF_LIST, Fixtures.full(), AppConfig(), StandbyActions.NONE) } }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("접기").assertDoesNotExist()
        rule.onNodeWithText("엄마").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("접기").assertExists()
    }

    @Test fun rowTapOpens() = open()

    @Test fun tenSecondsIdleCloses() {
        open()
        rule.mainClock.advanceTimeBy(9_700)
        rule.onNodeWithText("접기").assertExists()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("접기").assertDoesNotExist()
    }

    @Test fun foldCloses() {
        open()
        rule.onNodeWithText("접기").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("접기").assertDoesNotExist()
    }
}
