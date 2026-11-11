package kr.dot.sb.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.THEMES
import kr.dot.sb.model.Theme
import kr.dot.sb.shotPath
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Presets
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/**
 * Screens render matrix → standby/build/shots/screens/. The clock is driven by hand (autoAdvance off) so the
 * notification sequence can be shot mid-way and slideshow/Ken Burns stay still.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenShots(private val name: String, private val device: Device, private val key: String, private val page: Int, private val theme: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        rule.mainClock.autoAdvance = false
        val night = key.endsWith("-night")
        val k = key.removeSuffix("-night")
        val state = stateOf(k, if (night) 1f else 0f)
        val start = when (k) {
            "edit" -> RootStart(edit = true)
            "sheet" -> RootStart(edit = true, sheet = true)
            "sheet-solar" -> RootStart(edit = true, sheet = true, face = 3)
            "np", "np-open" -> RootStart(nowPlaying = true)
            else -> RootStart()
        }
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                RootImpl(state, AppConfig(startPage = page, theme = theme), StandbyActions.NONE, Modifier.fillMaxSize(), { _, _ -> }, start)
            }
        }
        rule.mainClock.advanceTimeBy(if (k == "sheet-solar" || k == "sheet") 400 else if (k == "notif" || k == "notif-hidden") 2_200 else 32)
        rule.waitForIdle()
        if (k == "np-open") { // the cover's controls (a portrait screen shows the tall body either way)
            rule.onRoot().performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
        }
        rule.onRoot().captureRoboImage(shotPath(theme, "screens", name, device))
    }

    companion object {
        private fun stateOf(key: String, night: Float): StandbyState {
            val s = Fixtures.full(night)
            val posted = s.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val notif = BigNotification("n1", "메시지", "엄마", "저녁 먹었어? 내일 김장하러 올 수 있니", null, postedAtEpochMs = posted)
            val live = LiveActivity("l1", "배달의민족", "배달 중", "12분", "가게에서 음식을 픽업했어요", 0.5f)
            return when (key) {
                "alarm" -> s.copy(alarm = AlarmRinging("알람"), notification = notif)
                "notif" -> s.copy(notification = notif, live = live)
                "notif-hidden" -> s.copy(notification = notif.copy(hidden = true))
                "live" -> s.copy(live = live, liveExpanded = true)
                "pills" -> s.copy(live = live)
                "np", "np-open" -> s
                "off" -> s.copy(displayOff = true)
                "photos-perm" -> s.copy(photos = null)
                "photos-empty" -> s.copy(photos = emptyList())
                "shift" -> s.copy(shiftX = 6, shiftY = -6)
                else -> s.copy(media = s.media?.copy(playing = false))
            }
        }

        private val L = Device.FOLD_COVER_L
        private val P = Device.FOLD_COVER_P
        private val CLOCKS = 2

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{4}-{0}-{1}")
        fun cases(): List<Array<Any>> = THEMES.flatMap { th -> base().map { it + th } }

        private fun base(): List<Array<Any>> {
            val pages = listOf("widgets" to 0, "photos" to 1, "clocks" to CLOCKS) +
                Presets.boards().mapIndexed { i, b -> "board-${b.id}" to 3 + i }
            val out = mutableListOf<Array<Any>>()
            for ((n, p) in pages) {
                for (d in Device.entries) out += arrayOf(n, d, "page", p)
                out += arrayOf("$n-night", L, "page-night", p)
            }
            for (o in listOf("alarm", "notif", "notif-hidden", "live", "np", "np-open", "pills")) {
                for (d in Device.entries) out += arrayOf(o, d, o, CLOCKS)
                out += arrayOf("$o-night", L, "$o-night", CLOCKS)
            }
            out += arrayOf("alarm-night", P, "alarm-night", CLOCKS)
            out += arrayOf("edit", L, "edit", CLOCKS)
            out += arrayOf("sheet", L, "sheet", CLOCKS)
            out += arrayOf("sheet", P, "sheet", CLOCKS)
            out += arrayOf("sheet-night", L, "sheet-night", CLOCKS)
            out += arrayOf("sheet-solar", L, "sheet-solar", CLOCKS)
            out += arrayOf("off", L, "off", CLOCKS)
            out += arrayOf("photos-perm", L, "photos-perm", 1)
            out += arrayOf("photos-empty", P, "photos-empty", 1)
            return out
        }
    }
}
