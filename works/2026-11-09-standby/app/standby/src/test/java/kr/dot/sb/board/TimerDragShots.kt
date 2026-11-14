package kr.dot.sb.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Theme
import kr.dot.sb.shotPath
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.ProvideTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 11-14 #10: the timer ring held mid-drag at 25 min (IPHONE, Fold cover, the timer card alone and in a half slot). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimerDragShots {
    @get:Rule val rule = createComposeRule()

    private fun held(layout: Layout, name: String) {
        RuntimeEnvironment.setQualifiers(Device.FOLD_COVER_L.qualifiers)
        val slots = if (layout == Layout.ONE) listOf(listOf(CardType.TIMER)) else listOf(listOf(CardType.TIMER), listOf(CardType.WEATHER))
        val b = Board("b", "b", layout, slots)
        rule.setContent { ProvideTheme(Theme.IPHONE) { BoardView(b, Fixtures.full().copy(timers = emptyMap()), AppConfig(), object : StandbyActions {}) } }
        rule.onNodeWithContentDescription("타이머 링: 돌려서 시간 정하기").performTouchInput {
            val r = width / 2f * 0.95f
            val a = Math.toRadians(150.0) // 25 min
            down(Offset(centerX + 1f, centerY - r))
            moveTo(Offset(centerX + r * 0.7f, centerY - r * 0.7f))
            moveTo(Offset(centerX + (r * Math.sin(a)).toFloat(), centerY - (r * Math.cos(a)).toFloat()))
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(shotPath(Theme.IPHONE, "board", name, Device.FOLD_COVER_L))
        rule.onNodeWithContentDescription("타이머 링: 돌려서 시간 정하기").performTouchInput { up() }
    }

    @Test fun wide() = held(Layout.ONE, "timer-drag-25")
    @Test fun half() = held(Layout.TWO, "timer-drag-25-M")
}
