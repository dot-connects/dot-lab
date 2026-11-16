package kr.dot.sb.board

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A swipe deletes a real notification: only with 밀어서 지우기 on (r15 §3), only leftwards, and a tap or a scroll never
 * does. Off, sideways drags belong to the page; on, the card keeps them. On turns itself off after 30 s without a
 * dismiss, when the screen goes off (lifecycle stop) and when the card leaves the screen.
 */
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

    private class Owner : LifecycleOwner {
        val reg = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = reg
    }

    private val owner = Owner()
    private var away by mutableStateOf(false)
    /** Sideways drag the page (the card's parent) received, in px. */
    private var paged = 0f

    private fun show(state: StandbyState = Fixtures.full()): Rec {
        val rec = Rec()
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                // the parent stands in for the page: it turns on whatever sideways drag the card leaves
                Box(Modifier.size(700.dp, 460.dp).draggable(rememberDraggableState { paged += it }, Orientation.Horizontal)) {
                    Box(Modifier.offset(x = if (away) 2000.dp else 0.dp).size(340.dp, 420.dp)) { CardView(CardType.NOTIF, state, AppConfig(), rec) }
                }
            }
        }
        rule.waitForIdle()
        return rec
    }

    private fun toggle() { rule.onNodeWithContentDescription("밀어서 지우기").performClick(); rule.waitForIdle() }

    private fun assertOn(on: Boolean) =
        rule.onNodeWithContentDescription("밀어서 지우기").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (on) "켜짐" else "꺼짐"))

    private fun swipeMomLeft() {
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeLeft(durationMillis = 400) }
        rule.waitForIdle()
    }

    @Test fun offByDefaultSideSwipeTurnsThePageAndClearsNothing() {
        val rec = show()
        assertOn(false)
        swipeMomLeft()
        assertEquals(emptyList<List<String>>(), rec.cleared)
        assertTrue("page got the drag", paged < -50f)
    }

    @Test fun onRowSwipedLeftPastAThirdIsClearedAndThePageStays() {
        val rec = show()
        toggle()
        assertOn(true)
        swipeMomLeft()
        assertEquals(listOf(listOf("k1")), rec.cleared)
        assertEquals(emptyList<String>(), rec.tapped)
        assertEquals(0f, paged)
    }

    @Test fun onTheCardKeepsSideDragsOffItsRowsToo() {
        show()
        toggle()
        rule.onNodeWithText("알림 ${Fixtures.full().notifications.size}", useUnmergedTree = true).performTouchInput { swipeLeft(durationMillis = 400) }
        rule.waitForIdle()
        assertEquals(0f, paged)
    }

    @Test fun onRightSwipeAndShortSwipeSnapBack() {
        val rec = show()
        toggle()
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeRight(durationMillis = 400) }
        rule.onNodeWithText("엄마", useUnmergedTree = true).performTouchInput { swipeLeft(startX = centerX, endX = centerX - width * 0.15f, durationMillis = 500) }
        rule.waitForIdle()
        assertEquals(emptyList<List<String>>(), rec.cleared)
    }

    @Test fun verticalScrollClearsNothing() {
        val rec = show()
        toggle()
        rule.onNodeWithText("배송 완료", useUnmergedTree = true).performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(emptyList<List<String>>(), rec.cleared)
        assertEquals(emptyList<String>(), rec.tapped)
    }

    @Test fun rowTapOpensCardPlateTapOpensAppNoClearAll() {
        val rec = show()
        rule.onNodeWithText("모두 지우기").assertDoesNotExist()
        rule.onNodeWithText("엄마").performClick() // closed row: opens the card (r9), not the app
        rule.waitForIdle()
        assertEquals(emptyList<String>(), rec.tapped)
        rule.onNodeWithText("엄마").performClick() // open plate: the app
        rule.waitForIdle()
        rule.onNodeWithText("모두 지우기").assertDoesNotExist()
        assertEquals(listOf("k1"), rec.tapped)
        assertEquals(emptyList<List<String>>(), rec.cleared)
    }

    @Test fun pinnedNotificationNeverClears() {
        val rec = show(Fixtures.full().let { s -> s.copy(notifications = s.notifications.map { it.copy(clearable = false) }) })
        toggle()
        swipeMomLeft()
        assertEquals(emptyList<List<String>>(), rec.cleared)
    }

    @Test fun secondTapTurnsItOff() {
        show()
        toggle(); toggle()
        assertOn(false)
    }

    @Test fun thirtySecondsWithoutADismissTurnsItOffADismissRestartsTheWait() {
        show()
        rule.mainClock.autoAdvance = false
        toggle()
        rule.mainClock.advanceTimeBy(20_000)
        assertOn(true)
        rule.mainClock.autoAdvance = true
        swipeMomLeft() // a dismiss: 30 s from here
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(25_000)
        assertOn(true)
        rule.mainClock.advanceTimeBy(6_000)
        assertOn(false)
    }

    @Test fun screenOffTurnsItOff() {
        show()
        toggle()
        owner.reg.currentState = Lifecycle.State.CREATED // ON_STOP: the screen went off
        rule.waitForIdle()
        owner.reg.currentState = Lifecycle.State.RESUMED
        rule.waitForIdle()
        assertOn(false)
    }

    @Test fun leavingTheScreenTurnsItOff() {
        show()
        toggle()
        away = true
        rule.waitForIdle()
        away = false
        rule.waitForIdle()
        assertOn(false)
    }
}
