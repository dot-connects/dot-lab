package kr.dot.sb.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A swipe deletes a real notification: only a deliberate sideways swipe may, and a tap or a scroll never does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class NotifGestureTest {
    @get:Rule val rule = createComposeRule()

    private class Rec : StandbyActions {
        val cleared = mutableListOf<List<String>>()
        val tapped = mutableListOf<String>()
        override fun notificationClear(keys: List<String>) { cleared += keys }
        override fun notificationTap(key: String) { tapped += key }
    }

    private fun show(card: CardType, state: StandbyState = Fixtures.full()): Rec {
        val rec = Rec()
        rule.setContent { Box(Modifier.size(340.dp, 420.dp)) { CardView(card, state, AppConfig(), rec) } }
        rule.waitForIdle()
        return rec
    }

    @Test fun listRowSwipedPastAThirdIsCleared() {
        val rec = show(CardType.NOTIF_LIST)
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeLeft(durationMillis = 400) }
        rule.waitForIdle()
        assertEquals(listOf(listOf("k1")), rec.cleared)
        assertEquals(emptyList<String>(), rec.tapped)
    }

    @Test fun shortSwipeSnapsBack() {
        val rec = show(CardType.NOTIF_LIST)
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeRight(startX = centerX, endX = centerX + width * 0.15f, durationMillis = 500) }
        rule.waitForIdle()
        assertEquals(emptyList<List<String>>(), rec.cleared)
    }

    @Test fun verticalScrollClearsNothing() {
        val rec = show(CardType.NOTIF_LIST)
        rule.onNodeWithText("배송 완료", useUnmergedTree = true).performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(emptyList<List<String>>(), rec.cleared)
        assertEquals(emptyList<String>(), rec.tapped)
    }

    @Test fun rowTapOpensCardPlateTapOpensAppClearAllTakesEveryClearableKey() {
        val rec = show(CardType.NOTIF_LIST, Fixtures.full().let { s -> s.copy(notifications = s.notifications.mapIndexed { i, n -> if (i == 2) n.copy(clearable = false) else n }) })
        rule.onNodeWithText("엄마").performClick() // closed row: opens the card (r9), not the app
        rule.waitForIdle()
        assertEquals(emptyList<String>(), rec.tapped)
        rule.onNodeWithText("엄마").performClick() // open plate: the app
        rule.onNodeWithText("모두 지우기").performClick()
        rule.waitForIdle()
        assertEquals(listOf("k1"), rec.tapped)
        assertEquals(listOf(listOf("k1", "k2", "k4", "k5")), rec.cleared)
    }

    @Test fun pinnedNotificationNeverClears() {
        val rec = show(CardType.NOTIF_LIST, Fixtures.full().let { s -> s.copy(notifications = s.notifications.map { it.copy(clearable = false) }) })
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeLeft(durationMillis = 400) }
        rule.waitForIdle()
        assertEquals(emptyList<List<String>>(), rec.cleared)
    }

    @Test fun stackFrontCardSwipesAway() {
        val rec = show(CardType.NOTIF_STACK)
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeRight(durationMillis = 400) }
        rule.waitForIdle()
        assertEquals(listOf(listOf("k1")), rec.cleared)
    }
}
