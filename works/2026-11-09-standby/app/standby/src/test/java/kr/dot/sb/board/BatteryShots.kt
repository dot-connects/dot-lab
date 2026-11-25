package kr.dot.sb.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Fixtures
import kr.dot.sb.data.ChargeKind
import kr.dot.sb.groupOn
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Theme
import kr.dot.sb.shotPath
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.state.BatteryInfo
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.ThemeBackground
import kr.dot.sb.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 배터리 card at the mockup's six slot sizes (battery-card.html round 3), one sheet per state:
 * build/shots/<theme>/battery/<state>[-night].png. `-Pshot.group=battery` to run only these.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BatteryShots {
    @get:Rule val rule = createComposeRule()

    private val z = java.time.ZoneId.systemDefault()
    private fun ms(minAgo: Long) = Fixtures.now.minusMinutes(minAgo).atZone(z).toInstant().toEpochMilli()
    private fun curve(from: Int, to: Int, minutes: Long) = (0..minutes step 3).map { m -> ms(minutes - m) to (from + (to - from) * (1 - Math.pow(1 - m / minutes.toDouble(), 1.6))).toInt() }

    private val states = mapOf(
        "charging" to kr.dot.sb.settings.Sample.sampleBattery(Fixtures.now),
        "protect" to BatteryInfo(85, ChargeKind.PROTECT, 29, curve = curve(31, 85, 130)),
        "full" to BatteryInfo(100, ChargeKind.FULL, 28, fullSince = ms(32), curve = curve(40, 100, 120)),
        "slow" to BatteryInfo(12, ChargeKind.SLOW, 30, 4.5f, leftMs = 220 * 60_000L, curve = curve(6, 12, 20)),
        "unplugged" to BatteryInfo(46, ChargeKind.UNPLUGGED, 27),
        "hot" to BatteryInfo(54, ChargeKind.SUPER, 41, 25f, leftMs = 38 * 60_000L, curve = curve(30, 54, 25)),
    )

    private fun sheet(name: String, theme: Theme, night: Float = 0f) {
        if (!groupOn("battery")) return
        RuntimeEnvironment.setQualifiers("w1100dp-h900dp-land-mdpi")
        val state = Fixtures.full(night).copy(battery = states.getValue(name))
        val config = AppConfig(theme = theme)
        rule.setContent {
            ProvideTheme(theme) {
                Box(Modifier.fillMaxSize()) {
                    ThemeBackground(Themes.of(theme), night)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf(140 to 120, 270 to 200, 198 to 253, 218 to 419).forEach { (w, h) ->
                                Box(Modifier.size(w.dp, h.dp)) { CardView(CardType.BATTERY, state, config, StandbyActions.NONE) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf(338 to 419, 695 to 419).forEach { (w, h) ->
                                Box(Modifier.size(w.dp, h.dp)) { CardView(CardType.BATTERY, state, config, StandbyActions.NONE) }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(shotPath(theme, "battery", if (night > 0f) "$name-night" else name))
    }

    @Test fun charging() = sheet("charging", Theme.IPHONE)
    @Test fun protect() = sheet("protect", Theme.IPHONE)
    @Test fun full() = sheet("full", Theme.IPHONE)
    @Test fun slow() = sheet("slow", Theme.IPHONE)
    @Test fun unplugged() = sheet("unplugged", Theme.IPHONE)
    @Test fun hot() = sheet("hot", Theme.IPHONE)
    @Test fun chargingNight() = sheet("charging", Theme.IPHONE, night = 1f)
    @Test fun chargingPaper() = sheet("charging", Theme.DAYPAPER)
    @Test fun chargingSky() = sheet("charging", Theme.SKY)
    @Test fun chargingTerminal() = sheet("charging", Theme.TERMINAL)
}
