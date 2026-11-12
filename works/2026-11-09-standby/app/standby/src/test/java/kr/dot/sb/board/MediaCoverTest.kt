package kr.dot.sb.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.cards.MEDIA_CONTROLS_MS
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Media cover (square slot): a tap shows the controls for 3 s, a button restarts the 3 s, a tap while they show opens
 * Now Playing — on the board. Inside Now Playing (✕ shown) that second tap hides them instead (Task 19c as built).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class MediaCoverTest {
    @get:Rule val rule = createComposeRule()

    private class Rec : StandbyActions {
        var np = 0
        var play = 0
        override fun openNowPlaying() { np++ }
        override fun mediaPlayPause() { play++ }
    }

    private fun show(close: (() -> Unit)? = null): Rec {
        val rec = Rec()
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.size(340.dp, 340.dp)) { CardView(CardType.MUSIC, Fixtures.full(), AppConfig(), rec, close = close) } }
        rule.mainClock.advanceTimeBy(100)
        controls(false)
        return rec
    }

    /** The picture away from every button (right of the transport, under the title row). */
    private fun tapPicture() {
        rule.onRoot().performTouchInput { click(Offset(width * 0.9f, height * 0.4f)) }
        rule.mainClock.advanceTimeBy(50)
    }

    private fun controls(shown: Boolean) =
        if (shown) rule.onNodeWithText("Ditto").assertExists() else rule.onNodeWithText("Ditto").assertDoesNotExist()

    @Test fun tapRevealsControlsForThreeSeconds() {
        show()
        tapPicture()
        controls(true)
        rule.mainClock.advanceTimeBy(MEDIA_CONTROLS_MS - 300)
        controls(true)
        rule.mainClock.advanceTimeBy(600) // 3 s + the 200 ms fade
        controls(false)
    }

    @Test fun buttonRestartsTheThreeSeconds() {
        val rec = show()
        tapPicture()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onRoot().performTouchInput { click(center) } // play / pause
        assertEquals(1, rec.play)
        rule.mainClock.advanceTimeBy(MEDIA_CONTROLS_MS - 300) // 4.7 s after the reveal
        controls(true)
        rule.mainClock.advanceTimeBy(600)
        controls(false)
        assertEquals(0, rec.np)
    }

    @Test fun secondTapOpensNowPlaying() {
        val rec = show()
        tapPicture()
        assertEquals(0, rec.np)
        tapPicture()
        assertEquals(1, rec.np)
    }

    @Test fun insideNowPlayingSecondTapHides() {
        val rec = show(close = {})
        tapPicture()
        tapPicture()
        rule.mainClock.advanceTimeBy(400)
        controls(false)
        assertEquals(0, rec.np)
    }
}
