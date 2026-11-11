package kr.dot.sb.board

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Presets
import kr.dot.sb.THEMES
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.MediaKind
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
 * Board render matrix → standby/build/shots/board/. One case per PNG (a compose rule takes one setContent per test).
 * Cases: presets × 6 devices (day), presets on the Fold cover at night, every card alone per size class, empty states.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardShots(private val name: String, private val device: Device, private val board: Board, private val stateKey: String, private val theme: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        val s = stateOf(stateKey, board)
        // "open": tap the slot so the media cover shows its controls (captured before the 3 s hide)
        val open: (ComposeContentTestRule.() -> Unit)? = if (!stateKey.startsWith("open")) null else {
            { mainClock.autoAdvance = false; onRoot().performClick(); mainClock.advanceTimeBy(400) }
        }
        rule.shot("board", name, device, theme, s.night, open) { BoardView(board, s, AppConfig(theme = theme), StandbyActions.NONE) }
    }

    companion object {
        private fun video(s: StandbyState) =
            s.copy(media = s.media!!.copy(appLabel = "YouTube", kind = MediaKind.VIDEO, title = "비 오는 밤 카페 재즈 3시간", artist = "재즈 채널", art = Fixtures.photo))

        /** States are built inside the sandbox (Bitmap needs Robolectric), parameters only carry a key. */
        fun stateOf(key: String, b: Board): StandbyState = when (key.removePrefix("open").removePrefix("-")) {
            "night" -> Fixtures.full(1f)
            "noart" -> Fixtures.full().let { it.copy(media = it.media!!.copy(art = null)) }
            "emptyA", "emptyAnight" -> Fixtures.full(if (key == "emptyAnight") 1f else 0f).let {
                it.copy(media = null, weather = null, agenda = null, usage = it.usage!!.copy(offline = true, fetchedAtEpochSec = it.usage!!.fetchedAtEpochSec - 4 * 3600))
            }
            "noperm" -> Fixtures.full().copy(agenda = null, upcoming = emptyList(), permissions = Permissions(calendar = false))
            "emptyB" -> Fixtures.full().let {
                it.copy(media = null, usage = null, memory = null, permissions = Permissions(photos = false), weather = it.weather!!.copy(offline = true))
            }
            else -> Fixtures.full()
        }.let { if (it.media != null && b.slots.any { CardType.VIDEO in it }) video(it) else it }

        /** Layouts that put slot 0 into each size class (§3.3): L / M / S wide / S tall on the Fold cover, XS on the Flip. */
        private val CLASSES = listOf(
            Triple("L", Layout.ONE, Device.FOLD_COVER_L),
            Triple("M", Layout.TWO, Device.FOLD_COVER_L),
            Triple("Sw", Layout.FOUR, Device.FOLD_COVER_L),
            Triple("St", Layout.THREE, Device.FOLD_COVER_L),
            Triple("XS", Layout.FOUR, Device.FLIP),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{4}-{0}-{1}")
        fun cases(): List<Array<Any>> = THEMES.flatMap { th -> (base() + if (th == Theme.IPHONE) nightCards() else emptyList()).map { it + th } }

        /** r9: night ignores the theme (candle), so every card at night is shot once, under IPHONE, on every device class. */
        private fun nightCards(): List<Array<Any>> = CardType.entries.flatMap { t ->
            CLASSES.map { (cls, layout, d) -> arrayOf<Any>("card-${t.name.lowercase()}-$cls-night", d, Board("card", t.name, layout, listOf(listOf(t))), "night") }
        }

        private fun base(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (b in Presets.boards()) {
                for (d in Device.entries) out += arrayOf("preset-${b.id}", d, b, "full")
                out += arrayOf("night-${b.id}", Device.FOLD_COVER_L, b, "night")
            }
            for (t in CardType.entries) for ((cls, layout, d) in CLASSES) {
                val b = Board("card", t.name, layout, listOf(listOf(t)))
                out += arrayOf("card-${t.name.lowercase()}-$cls", d, b, "full")
            }
            // media: the cover with its controls up, and both shapes without art (light themes must still read)
            for (t in listOf(CardType.MUSIC, CardType.VIDEO)) {
                val one = Board("card", t.name, Layout.ONE, listOf(listOf(t)))
                out += arrayOf("card-${t.name.lowercase()}-L-open", Device.FOLD_COVER_L, one, "open")
                out += arrayOf("card-${t.name.lowercase()}-L-open-night", Device.FOLD_COVER_L, one, "open-night")
            }
            // clocks, weather, calendar (spec §1): a big Fold-inner slot and portrait halves on top of the size classes
            for (t in listOf(CardType.CLOCK, CardType.ANALOG, CardType.WEATHER, CardType.CALENDAR)) {
                out += arrayOf("card-${t.name.lowercase()}-L", Device.FOLD_INNER, Board("card", t.name, Layout.ONE, listOf(listOf(t))), "full")
                out += arrayOf("card-${t.name.lowercase()}-M", Device.FOLD_COVER_P, Board("card", t.name, Layout.TWO, listOf(listOf(t))), "full")
            }
            // narrow tall weather (SMALL) + square calendar on the bar portrait; calendar without permission
            out += arrayOf("small-weather-calendar", Device.BAR_P, Board("small", "small", Layout.BIG2, listOf(listOf(CardType.CLOCK), listOf(CardType.WEATHER), listOf(CardType.CALENDAR))), "full")
            out += arrayOf("card-calendar-M-noperm", Device.FOLD_COVER_L, Board("card", "CALENDAR", Layout.TWO, listOf(listOf(CardType.CALENDAR))), "noperm")
            out += arrayOf("card-calendar-Sw-noperm", Device.FOLD_COVER_L, Board("card", "CALENDAR", Layout.FOUR, listOf(listOf(CardType.CALENDAR))), "noperm")
            out += arrayOf("card-music-M-noart", Device.FOLD_COVER_L, Board("card", "MUSIC", Layout.TWO, listOf(listOf(CardType.MUSIC))), "noart")
            out += arrayOf("card-music-L-open-noart", Device.FOLD_COVER_L, Board("card", "MUSIC", Layout.ONE, listOf(listOf(CardType.MUSIC))), "open-noart")
            val e1 = Board("empty1", "empty", Layout.FOUR, listOf(listOf(CardType.MUSIC), listOf(CardType.USAGE), listOf(CardType.AGENDA), listOf(CardType.WEATHER)))
            val e2 = Board("empty2", "empty", Layout.FOUR, listOf(listOf(CardType.VIDEO), listOf(CardType.USAGE), listOf(CardType.MEMORIES), listOf(CardType.WEATHER)))
            for (d in listOf(Device.FOLD_COVER_L, Device.FOLD_INNER)) {
                out += arrayOf("empty-a", d, e1, "emptyA")
                out += arrayOf("empty-b", d, e2, "emptyB")
            }
            out += arrayOf("empty-a-night", Device.FOLD_COVER_L, e1, "emptyAnight")
            // Two clocks on one board (§5.1): hero keeps accent digits, the second is white / white hands.
            val c = CardType.CLOCK; val a = CardType.ANALOG
            val dual = listOf(
                Board("dual-clock-analog", "dual", Layout.TWO, listOf(listOf(c), listOf(a))),
                Board("dual-analog-clock", "dual", Layout.BIG2, listOf(listOf(a), listOf(c), listOf(CardType.WEATHER))),
                Board("dual-float", "dual", Layout.BIG2, listOf(listOf(c), listOf(c), listOf(CardType.TIMER)), clockFace = kr.dot.sb.model.FaceId.FLOAT),
                Board("dual-solar", "dual", Layout.TWO2, listOf(listOf(c), listOf(CardType.DDAY), listOf(c)), clockFace = kr.dot.sb.model.FaceId.SOLAR),
            )
            for (b in dual) {
                out += arrayOf(b.id, Device.FOLD_COVER_L, b, "full")
                out += arrayOf(b.id, Device.FOLD_COVER_P, b, "full")
            }
            return out
        }
    }
}
