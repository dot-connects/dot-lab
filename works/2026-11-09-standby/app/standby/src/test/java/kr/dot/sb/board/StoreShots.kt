package kr.dot.sb.board

import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.CardType.*
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.Texture
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.shot
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Play store candidates (operator 2026-11-19: "최대한 다양성"): every layout, every theme, a different card mix per
 * screen, landscape and portrait, a few with a font / card texture → build/shots/<theme>/store/<name>-<device>.png.
 * Not a regression matrix.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StoreShots(private val name: String, private val c: Case) {
    @get:Rule val rule = createComposeRule()

    /** [font] = the board clock's font (its theme default otherwise); [texture] = a card texture edit. */
    data class Case(
        val board: Board, val theme: Theme = Theme.IPHONE, val key: String = "full", val device: Device = Device.BAR_L,
        val font: FontId? = null, val texture: Texture? = null,
    )

    @Test fun shot() {
        val s = BoardShots.stateOf(c.key, c.board)
        val face = c.board.clockFace
        val styles = c.font?.let { f -> mapOf(face to Themes.of(c.theme).clockDefaults(face).copy(font = f, weight = f.defaultWeight)) } ?: emptyMap()
        val edits = c.texture?.let { mapOf(c.theme to ThemeEdit(cardTexture = it, textureAmt = 0.7f)) } ?: emptyMap()
        val config = AppConfig(
            theme = c.theme, ddays = Fixtures.ddays, clockCardFace = face, styles = styles, themeEdits = edits,
            appsCard = SlotOptions(apps = (0 until 4).map { "app.$it" }),
        ).let { it.copy(myClocks = (it.myClocks + face).distinct()) }
        rule.shot("store", name, c.device, c.theme, s.night) {
            androidx.compose.runtime.CompositionLocalProvider(kr.dot.sb.ui.cards.LocalAppLookup provides Fixtures.appLookup) {
                kr.dot.sb.ui.theme.ProvideTheme(config) { BoardView(c.board, s, config, StandbyActions.NONE) }
            }
        }
    }

    companion object {
        private fun b(id: String, layout: Layout, face: FaceId, vararg slots: List<CardType>) =
            Board(id, id, layout, slots.toList(), clockFace = face)

        private val P = Device.BAR_P

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // landscape: every layout, a different card mix each
            "s02-two-analog-weather" to Case(b("s02", Layout.TWO, FaceId.ANALOG, listOf(CLOCK), listOf(WEATHER))),
            "s03-big2-clock-agenda-weather" to Case(b("s03", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK, WEATHER), listOf(AGENDA), listOf(WEATHER))),
            "s04-two2-dday-timer-music" to Case(b("s04", Layout.TWO2, FaceId.DIGITAL, listOf(DDAY), listOf(TIMER), listOf(MUSIC))),
            "s05-four-weather-calendar-timer-apps" to Case(b("s05", Layout.FOUR, FaceId.DIGITAL, listOf(WEATHER), listOf(CALENDAR), listOf(TIMER), listOf(APPS))),
            "s06-three-memories-calendar-sleep" to Case(b("s06", Layout.THREE, FaceId.DIGITAL, listOf(MEMORIES), listOf(CALENDAR), listOf(SLEEP)), key = "playing"),
            "s07-two-memories-flip" to Case(b("s07", Layout.TWO, FaceId.FLIP, listOf(MEMORIES), listOf(CLOCK)), Theme.DAYPAPER),
            "s08-big2-neon" to Case(b("s08", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK), listOf(WEATHER), listOf(CALENDAR)), Theme.NEON),
            "s09-four-sky" to Case(b("s09", Layout.FOUR, FaceId.ANALOG, listOf(CLOCK), listOf(WEATHER), listOf(AGENDA), listOf(DDAY)), Theme.SKY),
            "s10-three-radio" to Case(b("s10", Layout.THREE, FaceId.DIGITAL, listOf(CLOCK), listOf(MUSIC), listOf(WEATHER)), Theme.RADIO),
            "s11-two-night-sleep" to Case(b("s11", Layout.TWO, FaceId.DIGITAL, listOf(CLOCK), listOf(SLEEP)), key = "playing-night"),
            "s12-big2-terminal" to Case(b("s12", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK), listOf(TIMER), listOf(WEATHER)), Theme.TERMINAL),
            "s13-two2-moodlight" to Case(b("s13", Layout.TWO2, FaceId.ANALOG, listOf(WEATHER), listOf(DDAY), listOf(CLOCK)), Theme.MOODLIGHT),
            "s14-four-notif" to Case(b("s14", Layout.FOUR, FaceId.DIGITAL, listOf(CLOCK), listOf(NOTIF), listOf(WEATHER), listOf(TIMER))),
            "s15-two-hangul-agenda" to Case(b("s15", Layout.TWO, FaceId.HANGUL, listOf(CLOCK), listOf(AGENDA)), Theme.DAYPAPER),
            "s16-four-alarm-apps" to Case(b("s16", Layout.FOUR, FaceId.LED, listOf(CLOCK), listOf(ALARM), listOf(APPS), listOf(MEMORIES))),
            // operator 11-19 round 2: tall media, the calendar, the round heavy font on grain cards (the operator's own look)
            "l01-three-round-music-calendar" to Case(b("l01", Layout.THREE, FaceId.DIGITAL, listOf(CLOCK), listOf(MUSIC), listOf(CALENDAR)), font = FontId.ROUNDED_HEAVY, texture = Texture.GRAIN),
            "l02-three-weather-music-dday" to Case(b("l02", Layout.THREE, FaceId.DIGITAL, listOf(WEATHER), listOf(MUSIC), listOf(DDAY)), texture = Texture.GRAIN),
            "l03-three-calendar-music-weather-paper" to Case(b("l03", Layout.THREE, FaceId.DIGITAL, listOf(CALENDAR), listOf(MUSIC), listOf(WEATHER)), Theme.DAYPAPER),
            "l04-two2-timer-sleep-calendar" to Case(b("l04", Layout.TWO2, FaceId.DIGITAL, listOf(TIMER), listOf(SLEEP), listOf(CALENDAR)), key = "playing"),
            "l05-big2-relief-weather-dday" to Case(b("l05", Layout.BIG2, FaceId.RELIEF, listOf(CLOCK), listOf(WEATHER), listOf(DDAY)), Theme.SKY),
            "l06-four-round-grain" to Case(b("l06", Layout.FOUR, FaceId.DIGITAL, listOf(CLOCK), listOf(TIMER), listOf(CALENDAR), listOf(WEATHER)), font = FontId.ROUNDED_HEAVY, texture = Texture.GRAIN),
            "l07-three-music-radio" to Case(b("l07", Layout.THREE, FaceId.FLIP, listOf(MUSIC), listOf(CLOCK), listOf(AGENDA)), Theme.RADIO),
            "l08-two-calendar-neon" to Case(b("l08", Layout.TWO, FaceId.DIGITAL, listOf(CALENDAR), listOf(WEATHER)), Theme.NEON),
            // portrait (the phone standing up)
            "p01-big2-round-music-weather" to Case(b("p01", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK), listOf(MUSIC), listOf(WEATHER)), device = P, font = FontId.ROUNDED_HEAVY, texture = Texture.GRAIN),
            "p02-two-flip-calendar" to Case(b("p02", Layout.TWO, FaceId.FLIP, listOf(CLOCK), listOf(CALENDAR)), device = P),
            "p03-four-mix" to Case(b("p03", Layout.FOUR, FaceId.DIGITAL, listOf(WEATHER), listOf(CALENDAR), listOf(TIMER), listOf(DDAY)), device = P),
            "p04-two2-agenda-sleep-memories" to Case(b("p04", Layout.TWO2, FaceId.DIGITAL, listOf(AGENDA), listOf(SLEEP), listOf(MEMORIES)), device = P, key = "playing"),
            "p05-big2-neon-music-calendar" to Case(b("p05", Layout.BIG2, FaceId.DIGITAL, listOf(CLOCK), listOf(MUSIC), listOf(CALENDAR)), Theme.NEON, device = P),
            "p06-two-memories-weather-paper" to Case(b("p06", Layout.TWO, FaceId.ANALOG, listOf(MEMORIES), listOf(WEATHER)), Theme.DAYPAPER, device = P),
            "p07-big2-analog-sky" to Case(b("p07", Layout.BIG2, FaceId.ANALOG, listOf(CLOCK), listOf(WEATHER), listOf(AGENDA)), Theme.SKY, device = P),
            "p08-two-night-sleep" to Case(b("p08", Layout.TWO, FaceId.DIGITAL, listOf(CLOCK), listOf(SLEEP)), device = P, key = "playing-night"),
        ).map { (n, c) -> arrayOf(n, c) }
    }
}
