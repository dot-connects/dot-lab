package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.screens.BANNER_MS
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/** New-notification banner (spec-r9 §1): 5 s then gone, swipe up dismisses, tap opens the board's notification card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class BannerTest {
    @get:Rule val rule = createComposeRule()

    private class Rec : StandbyActions {
        val dismissed = mutableListOf<String>()
        val tapped = mutableListOf<String>()
        override fun notificationDismiss(key: String) { dismissed += key }
        override fun notificationTap(key: String) { tapped += key }
    }

    private fun show(): Rec {
        val rec = Rec()
        val now = Fixtures.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = Fixtures.full().copy(notification = BigNotification("b1", "카카오톡", "민수", "도착했어", null, postedAtEpochMs = now))
        val plain = listOf(CardType.CLOCK)
        val config = AppConfig(widgetLeft = plain, widgetRight = plain, startPage = 0, boards = listOf(Board("n", "알림", Layout.ONE, listOf(listOf(CardType.NOTIF_LIST)))))
        rule.mainClock.autoAdvance = false
        rule.setContent { RootImpl(state, config, rec, Modifier, { _, _ -> }, RootStart()) }
        rule.mainClock.advanceTimeBy(500) // slide-in
        rule.onNodeWithText("민수").assertExists()
        return rec
    }

    @Test fun hidesAfterFiveSeconds() {
        val rec = show()
        rule.mainClock.advanceTimeBy(BANNER_MS - 700)
        assertEquals(emptyList<String>(), rec.dismissed)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(listOf("b1"), rec.dismissed)
    }

    @Test fun swipeUpDismisses() {
        val rec = show()
        val t0 = rule.mainClock.currentTime
        rule.onNodeWithText("민수").performTouchInput { swipeUp(durationMillis = 200) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf("b1"), rec.dismissed)
        assertTrue("dismissed by the swipe, not the timer", rule.mainClock.currentTime - t0 < BANNER_MS)
    }

    @Test fun tapOpensTheBoardsNotificationCard() {
        val rec = show()
        rule.onNodeWithText("민수").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf("b1"), rec.dismissed) // the root dismisses the banner …
        assertEquals(emptyList<String>(), rec.tapped) // … and opens the card instead of the app
        rule.onNodeWithText("접기").assertExists()
    }
}
