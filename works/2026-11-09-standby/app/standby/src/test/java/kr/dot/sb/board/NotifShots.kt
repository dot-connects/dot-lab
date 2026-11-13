package kr.dot.sb.board

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.THEMES
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.Permissions
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The notification card (spec-r9 §1, mockup tab 알림) → standby/build/shots/<theme>/notif/. One slot per tier
 * (A flip FOUR, B fold THREE, D fold TWO, E fold ONE, S bar FOUR), closed and open ("open-*" taps a row first), plus
 * night, empty, hidden-content and no-access. "many" = twelve conversations, so rows overflow into "+N".
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotifShots(private val name: String, private val device: Device, private val board: Board, private val stateKey: String, private val theme: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        val s = stateOf(stateKey)
        // "open": tap the 배송 완료 row (tier A has no names: tap the count) and capture before the 10 s idle close
        val open: (ComposeContentTestRule.() -> Unit)? = if (!stateKey.startsWith("open")) null else {
            {
                mainClock.autoAdvance = false
                val row = onAllNodesWithText("배송 완료")
                (if (row.fetchSemanticsNodes().isNotEmpty()) row else onAllNodesWithText(s.notifications.size.toString()))[0].performClick()
                mainClock.advanceTimeBy(400)
            }
        }
        rule.shot("notif", name, device, theme, s.night, open) { BoardView(board, s, AppConfig(theme = theme, ddays = Fixtures.ddays), StandbyActions.NONE) }
    }

    companion object {
        fun stateOf(key: String): StandbyState = when (key.removePrefix("open").removePrefix("-")) {
            "night" -> Fixtures.full(1f)
            "many" -> Fixtures.full().copy(notifications = Fixtures.manyNotifs())
            "empty" -> Fixtures.full().copy(notifications = emptyList())
            "hidden" -> Fixtures.full().copy(notifications = Fixtures.hiddenNotifs())
            "noaccess" -> Fixtures.full().copy(notifications = emptyList(), permissions = Permissions(notifications = false))
            else -> Fixtures.full()
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{4}-{0}-{1}")
        fun cases(): List<Array<Any>> = THEMES.flatMap { th -> base().map { it + th } }

        private fun base(): List<Array<Any>> {
            val n = CardType.NOTIF
            val one = Board("n-one", "one", Layout.ONE, listOf(listOf(n)))
            val two = Board("n-two", "two", Layout.TWO, listOf(listOf(CardType.CLOCK), listOf(n)))
            val three = Board("n-three", "three", Layout.THREE, listOf(listOf(CardType.CLOCK), listOf(n), listOf(CardType.WEATHER)))
            val four = Board("n-four", "four", Layout.FOUR, listOf(listOf(CardType.CLOCK), listOf(n), listOf(CardType.WEATHER), listOf(CardType.MUSIC)))
            val out = mutableListOf<Array<Any>>()
            for (k in listOf("full", "many", "open-many")) {
                out += arrayOf("E-one-$k", Device.FOLD_COVER_L, one, k)
                out += arrayOf("D-two-$k", Device.FOLD_COVER_L, two, k)
                out += arrayOf("B-three-$k", Device.FOLD_COVER_L, three, k)
                out += arrayOf("S-four-$k", Device.BAR_L, four, k)
                out += arrayOf("A-four-$k", Device.FLIP, four, k)
            }
            for (d in listOf(Device.FOLD_COVER_P, Device.BAR_L)) out += arrayOf("D-two-many", d, two, "many")
            out += arrayOf("D-two-night", Device.FOLD_COVER_L, two, "night")
            out += arrayOf("D-two-open-night", Device.FOLD_COVER_L, two, "open-night")
            out += arrayOf("D-two-empty", Device.FOLD_COVER_L, two, "empty")
            out += arrayOf("D-two-hidden", Device.FOLD_COVER_L, two, "hidden")
            out += arrayOf("D-two-open-hidden", Device.FOLD_COVER_L, two, "open-hidden")
            out += arrayOf("four-noaccess", Device.FOLD_COVER_L, four, "noaccess")
            return out
        }
    }
}
