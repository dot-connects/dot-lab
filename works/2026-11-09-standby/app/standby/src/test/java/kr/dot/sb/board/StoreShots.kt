package kr.dot.sb.board

import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.CardType.*
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Play store candidates (operator 2026-11-19: "최대한 다양성"): every layout, every theme, a different card mix per
 * screen, on the bar phone in landscape → build/shots/<theme>/store/<name>-bar-land.png. Not a regression matrix.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StoreShots(private val name: String, private val board: Board, private val theme: Theme, private val key: String) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        val s = BoardShots.stateOf(key, board)
        val config = AppConfig(
            theme = theme, ddays = Fixtures.ddays, clockCardFace = board.clockFace,
            appsCard = SlotOptions(apps = (0 until 4).map { "app.$it" }),
        ).let { it.copy(myClocks = (it.myClocks + board.clockFace).distinct()) }
        rule.shot("store", name, Device.BAR_L, theme, s.night) {
            androidx.compose.runtime.CompositionLocalProvider(kr.dot.sb.ui.cards.LocalAppLookup provides Fixtures.appLookup) {
                BoardView(board, s, config, StandbyActions.NONE)
            }
        }
    }

    companion object {
        private fun b(id: String, layout: Layout, face: FaceId, vararg slots: List<CardType>) =
            Board(id, id, layout, slots.toList(), clockFace = face)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("s02-two-analog-weather", b("s02", Layout.TWO, FaceId.ANALOG, listOf(CLOCK), listOf(WEATHER)), Theme.IPHONE, "full"),
            arrayOf("s03-big2-clock-agenda-weather", b("s03", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK, WEATHER), listOf(AGENDA), listOf(WEATHER)), Theme.IPHONE, "full"),
            arrayOf("s04-two2-dday-timer-music", b("s04", Layout.TWO2, FaceId.DIGITAL, listOf(DDAY), listOf(TIMER), listOf(MUSIC)), Theme.IPHONE, "full"),
            arrayOf("s05-four-weather-calendar-timer-apps", b("s05", Layout.FOUR, FaceId.DIGITAL, listOf(WEATHER), listOf(CALENDAR), listOf(TIMER), listOf(APPS)), Theme.IPHONE, "full"),
            arrayOf("s06-three-memories-calendar-sleep", b("s06", Layout.THREE, FaceId.DIGITAL, listOf(MEMORIES), listOf(CALENDAR), listOf(SLEEP)), Theme.IPHONE, "playing"),
            arrayOf("s07-two-memories-flip", b("s07", Layout.TWO, FaceId.FLIP, listOf(MEMORIES), listOf(CLOCK)), Theme.DAYPAPER, "full"),
            arrayOf("s08-big2-neon", b("s08", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK), listOf(WEATHER), listOf(CALENDAR)), Theme.NEON, "full"),
            arrayOf("s09-four-sky", b("s09", Layout.FOUR, FaceId.ANALOG, listOf(CLOCK), listOf(WEATHER), listOf(AGENDA), listOf(DDAY)), Theme.SKY, "full"),
            arrayOf("s10-three-radio", b("s10", Layout.THREE, FaceId.DIGITAL, listOf(CLOCK), listOf(MUSIC), listOf(WEATHER)), Theme.RADIO, "full"),
            arrayOf("s11-two-night-sleep", b("s11", Layout.TWO, FaceId.DIGITAL, listOf(CLOCK), listOf(SLEEP)), Theme.IPHONE, "playing-night"),
            arrayOf("s12-big2-terminal", b("s12", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK), listOf(TIMER), listOf(WEATHER)), Theme.TERMINAL, "full"),
            arrayOf("s13-two2-moodlight", b("s13", Layout.TWO2, FaceId.ANALOG, listOf(WEATHER), listOf(DDAY), listOf(CLOCK)), Theme.MOODLIGHT, "full"),
            arrayOf("s14-four-notif", b("s14", Layout.FOUR, FaceId.DIGITAL, listOf(CLOCK), listOf(NOTIF), listOf(WEATHER), listOf(TIMER)), Theme.IPHONE, "full"),
            arrayOf("s15-two-hangul-agenda", b("s15", Layout.TWO, FaceId.HANGUL, listOf(CLOCK), listOf(AGENDA)), Theme.DAYPAPER, "full"),
            arrayOf("s16-four-alarm-apps", b("s16", Layout.FOUR, FaceId.LED, listOf(CLOCK), listOf(ALARM), listOf(APPS), listOf(MEMORIES)), Theme.IPHONE, "full"),
        )
    }
}
