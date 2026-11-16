package kr.dot.sb.clock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.LumeView
import kr.dot.sb.model.SquareNumerals
import kr.dot.sb.model.Theme
import kr.dot.sb.shotPath
import kr.dot.sb.ui.clock.Relief
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.state.StandbyState
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
 * r11 Part C: the three Relief faces (IPHONE): 아이스 and 코얼 dials, the 야광 view, night, and the M card slot, on the
 * Fold cover; 아이스 also on the Flip cover and a bar phone held upright (layout at the extremes).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReliefShots {
    @get:Rule val rule = createComposeRule()
    private val theme = Theme.IPHONE
    private val faces = listOf(FaceId.RELIEF, FaceId.RELIEF_SQUARE, FaceId.RELIEF_DIGITAL)

    private fun base(f: FaceId): ClockStyle = Themes.of(theme).clockDefaults(f)

    private fun shots(device: Device, items: List<Pair<String, @Composable () -> Unit>>) {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        var current by mutableStateOf(items.first().second)
        rule.setContent { ProvideTheme(theme) { Box(Modifier.fillMaxSize()) { current() } } }
        for ((name, content) in items) {
            current = content
            rule.waitForIdle()
            rule.onRoot().captureRoboImage(shotPath(theme, "clock", name, device))
        }
    }

    private fun face(f: FaceId, style: ClockStyle = base(f), state: StandbyState = Fixtures.full()): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            ThemeBackground(Themes.of(theme), state.night)
            ClockFace(f, style, state, Modifier.fillMaxSize())
        }
    }

    private fun slot(f: FaceId, style: ClockStyle = base(f)): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ClockFace(f, style, Fixtures.full(), Modifier.size(337.dp, 419.dp), inSlot = true)
        }
    }

    private fun n(f: FaceId) = f.name.lowercase().replace('_', '-')

    @Test fun foldCover() = shots(Device.FOLD_COVER_L, faces.flatMap { f ->
        listOf(
            "${n(f)}-ice" to face(f),
            "${n(f)}-coal" to face(f, base(f).copy(faceColor = Relief.COAL.dial)),
            "${n(f)}-lume" to face(f, base(f).copy(lumeView = LumeView.LUME)),
            "${n(f)}-night" to face(f, base(f).copy(lumeView = LumeView.NIGHT), Fixtures.full(night = 1f)),
            "${n(f)}-night-day" to face(f, state = Fixtures.full(night = 1f)),
            "${n(f)}-slot-m" to slot(f),
        )
    } + listOf(
        "relief-index" to face(FaceId.RELIEF, base(FaceId.RELIEF).copy(analogNumbers = AnalogNumbers.NONE, faceColor = Relief.DIALS[5].dial)),
        "relief-digital-12h" to face(FaceId.RELIEF_DIGITAL, base(FaceId.RELIEF_DIGITAL).copy(h24 = false, ghost = false, faceColor = Relief.DIALS[4].dial)),
        "relief-digital-nodate" to face(FaceId.RELIEF_DIGITAL, base(FaceId.RELIEF_DIGITAL).copy(showInfo = false, faceColor = 0xFF2B3A55)),
    ))

    /** 입체 네모's three numeral styles on 아이스 and 코얼, and the cut-out ones on a tall slot. */
    @Test fun squareNumerals() = shots(Device.FOLD_COVER_L, SquareNumerals.entries.flatMap { n ->
        val sq = base(FaceId.RELIEF_SQUARE).copy(squareNumerals = n)
        val tag = n.name.lowercase()
        listOf(
            "relief-square-num-$tag-ice" to face(FaceId.RELIEF_SQUARE, sq),
            "relief-square-num-$tag-coal" to face(FaceId.RELIEF_SQUARE, sq.copy(faceColor = Relief.COAL.dial)),
        )
    } + listOf(
        "relief-square-num-sandwich-tall" to sized(FaceId.RELIEF_SQUARE, 170, 419, base(FaceId.RELIEF_SQUARE).copy(squareNumerals = SquareNumerals.SANDWICH)),
        "relief-square-num-sandwich-night" to face(FaceId.RELIEF_SQUARE, base(FaceId.RELIEF_SQUARE).copy(squareNumerals = SquareNumerals.SANDWICH), Fixtures.full(night = 1f)),
    ))

    /** 입체 네모 follows the slot's shape: a square card, a tall slot and a small Flip card (just above compact). */
    @Test fun squareSlots() = shots(Device.FOLD_COVER_L, listOf(
        "relief-square-slot-sq" to sized(FaceId.RELIEF_SQUARE, 300, 300),
        "relief-square-slot-tall" to sized(FaceId.RELIEF_SQUARE, 170, 419),
        "relief-square-slot-xs" to sized(FaceId.RELIEF_SQUARE, 130, 130),
    ))

    private fun sized(f: FaceId, w: Int, h: Int, style: ClockStyle = base(f)): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ClockFace(f, style, Fixtures.full(), Modifier.size(w.dp, h.dp), inSlot = true)
        }
    }

    @Test fun flipCover() = shots(Device.FLIP, faces.map { f -> "${n(f)}-ice" to face(f) })

    @Test fun barPortrait() = shots(Device.BAR_P, faces.map { f -> "${n(f)}-ice" to face(f) })

    /** r14 A4: 입체 디지털 upright (hours over minutes) on four dials, night, and a 12 h one-digit hour without engraved segments. */
    @Test fun digitalPortrait() = FaceId.RELIEF_DIGITAL.let { f ->
        shots(Device.FOLD_COVER_P, listOf("ice" to 0, "cream" to 2, "sky" to 4, "coal" to 7).map { (tag, i) -> "${n(f)}-port-$tag" to face(f, base(f).copy(faceColor = Relief.DIALS[i].dial)) } + listOf(
            "${n(f)}-port-night" to face(f, state = Fixtures.full(night = 1f)),
            "${n(f)}-port-12h-noghost" to face(f, base(f).copy(h24 = false, ghost = false)),
        ))
    }
}
