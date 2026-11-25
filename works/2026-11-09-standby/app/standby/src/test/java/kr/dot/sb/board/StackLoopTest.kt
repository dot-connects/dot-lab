package kr.dot.sb.board

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.board.SlotStack
import kr.dot.sb.ui.board.LastCard
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Operator 11-25: a card stack loops — past the last card comes the first, before the first comes the last. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class StackLoopTest {
    @get:Rule val rule = createComposeRule()
    private val cards = listOf(CardType.BATTERY, CardType.ALARM, CardType.CALENDAR)
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun show() = rule.setContent {
        SlotStack(cards, Fixtures.full(), AppConfig(), StandbyActions.NONE,
            Modifier.size(300.dp, 300.dp).testTag("stack"), slotId = "loop", rememberCard = true)
    }

    private fun swipe(up: Boolean) {
        rule.onNodeWithTag("stack").performTouchInput { if (up) swipeUp() else swipeDown() }
        rule.waitForIdle()
    }

    @Test fun backFromTheFirstCard_isTheLastCard() {
        show()
        swipe(up = false)
        assertEquals(LastCard.keyOf(CardType.CALENDAR), LastCard.read(ctx, "loop"))
    }

    @Test fun pastTheLastCard_isTheFirstCard() {
        show()
        repeat(3) { swipe(up = true) }
        assertEquals(LastCard.keyOf(CardType.BATTERY), LastCard.read(ctx, "loop"))
    }
}
