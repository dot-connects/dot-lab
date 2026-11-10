package kr.dot.sb.board

import androidx.compose.ui.test.junit4.createComposeRule
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
 * Notification cards (mockup r6, variants 1 쌓기 and 2 목록) → standby/build/shots/<theme>/notif/. Clock + card on a
 * TWO board (fold cover both ways, bar, night), both cards in small FOUR slots, and the empty, hidden-content and
 * no-access states.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotifShots(private val name: String, private val device: Device, private val board: Board, private val stateKey: String, private val theme: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() = rule.shot("notif", name, device, theme) {
        BoardView(board, stateOf(stateKey), AppConfig(theme = theme), StandbyActions.NONE)
    }

    companion object {
        fun stateOf(key: String): StandbyState = when (key) {
            "night" -> Fixtures.full(1f)
            "empty" -> Fixtures.full().copy(notifications = emptyList())
            "hidden" -> Fixtures.full().copy(notifications = Fixtures.hiddenNotifs())
            "noaccess" -> Fixtures.full().copy(notifications = emptyList(), permissions = Permissions(notifications = false))
            else -> Fixtures.full()
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{4}-{0}-{1}")
        fun cases(): List<Array<Any>> = THEMES.flatMap { th -> base().map { it + th } }

        private fun base(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (card in listOf(CardType.NOTIF_STACK, CardType.NOTIF_LIST)) {
                val id = if (card == CardType.NOTIF_STACK) "stack" else "list"
                val two = Board("n-$id", id, Layout.TWO, listOf(listOf(CardType.CLOCK), listOf(card)))
                for (d in listOf(Device.FOLD_COVER_L, Device.FOLD_COVER_P, Device.BAR_L)) out += arrayOf("$id-two", d, two, "full")
                out += arrayOf("$id-two-night", Device.FOLD_COVER_L, two, "night")
                out += arrayOf("$id-two-empty", Device.FOLD_COVER_L, two, "empty")
                out += arrayOf("$id-two-hidden", Device.FOLD_COVER_L, two, "hidden")
            }
            val four = Board("n-four", "four", Layout.FOUR, listOf(listOf(CardType.CLOCK), listOf(CardType.NOTIF_STACK), listOf(CardType.WEATHER), listOf(CardType.NOTIF_LIST)))
            for (d in listOf(Device.FOLD_COVER_L, Device.FOLD_COVER_P, Device.BAR_L, Device.FLIP)) out += arrayOf("four", d, four, "full")
            out += arrayOf("four-night", Device.FOLD_COVER_L, four, "night")
            out += arrayOf("four-noaccess", Device.FOLD_COVER_L, four, "noaccess")
            return out
        }
    }
}
