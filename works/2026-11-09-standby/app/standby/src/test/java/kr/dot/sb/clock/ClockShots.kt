package kr.dot.sb.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockLayout
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Numerals
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.clock.StyledTime
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Ink
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import kr.dot.sb.THEMES
import kr.dot.sb.shotPath
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.Themes
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * Clock render matrix → standby/build/shots/clock/. One test per device; each test swaps the content and captures,
 * because a compose rule takes one setContent per test.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ClockShots(private val theme: Theme) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = THEMES.map { arrayOf<Any>(it) }
    }

    @get:Rule val rule = createComposeRule()

    private val faces = listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.WORLD, FaceId.SOLAR, FaceId.FLOAT, FaceId.DESK, FaceId.PHOTOS_TIME)

    private fun shots(device: Device, items: List<Pair<String, @Composable () -> Unit>>) {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        var current by mutableStateOf(items.first().second)
        rule.setContent { ProvideTheme(theme) { Box(Modifier.fillMaxSize().background(Color.Black)) { current() } } }
        for ((name, content) in items) {
            current = content
            rule.waitForIdle()
            rule.onRoot().captureRoboImage(shotPath(theme, "clock", name, device))
        }
    }

    private fun face(f: FaceId, state: StandbyState = Fixtures.full(), style: ClockStyle = Themes.of(theme).clockDefaults(f)): @Composable () -> Unit = {
        if (f == FaceId.PHOTOS_TIME) {
            // The face places the time itself on the whole photo screen (review C1). Placeholder photo = flat blue,
            // night-mapped like a photo (review C7: the screens layer must pass fallbacks through the same filter).
            Box(Modifier.fillMaxSize().background(Ink.of(state.night, Color.White).mapped(Color(0xFF3F6D8C)))) {
                ClockFace(f, style, state, Modifier.fillMaxSize())
            }
        } else ClockFace(f, style, state, Modifier.fillMaxSize())
    }

    private fun matrix(device: Device) = shots(device, faces.map { f -> "${f.name.lowercase()}-day" to face(f) })

    @Test fun foldCoverLand() = shots(Device.FOLD_COVER_L, faces.map { "${it.name.lowercase()}-day" to face(it) } + faces.map { "${it.name.lowercase()}-night" to face(it, Fixtures.full(night = 1f)) })
    @Test fun foldCoverPort() = matrix(Device.FOLD_COVER_P)
    @Test fun foldInner() = matrix(Device.FOLD_INNER)
    @Test fun barLand() = matrix(Device.BAR_L)
    @Test fun barPort() = matrix(Device.BAR_P)
    @Test fun flip() = matrix(Device.FLIP)

    @Test fun styleMatrix() {
        val dig = ClockStyle.default(FaceId.DIGITAL)
        val flt = ClockStyle.default(FaceId.FLOAT)
        val ana = ClockStyle.default(FaceId.ANALOG)
        val sol = ClockStyle.default(FaceId.SOLAR)
        val items = FontId.entries.map { "font-${it.name.lowercase()}" to face(FaceId.DIGITAL, style = dig.copy(font = it, weight = it.defaultWeight)) } +
            Effect.entries.map { "fx-${it.name.lowercase()}" to face(FaceId.FLOAT, style = flt.copy(effect = it)) } +
            listOf(
                "fx-glow-night" to face(FaceId.FLOAT, Fixtures.full(night = 1f), flt.copy(effect = Effect.GLOW)),
                "digital-rainbow" to face(FaceId.DIGITAL, style = dig.copy(effect = Effect.RAINBOW)),
                "digital-glass" to face(FaceId.DIGITAL, style = dig.copy(effect = Effect.GLASS)),
                "digital-stencil-glow" to face(FaceId.DIGITAL, style = dig.copy(font = FontId.STENCIL, weight = 800, effect = Effect.GLOW)),
                "analog-roman" to face(FaceId.ANALOG, style = ana.copy(analogNumbers = AnalogNumbers.ROMAN)),
                "analog-four" to face(FaceId.ANALOG, style = ana.copy(analogNumbers = AnalogNumbers.FOUR, secondColor = 0xFFE9893A)),
                "analog-twelve" to face(FaceId.ANALOG, style = ana.copy(analogNumbers = AnalogNumbers.TWELVE)),
                "analog-rails" to face(FaceId.ANALOG, style = ana.copy(font = FontId.RAILS, weight = 800, widthPct = 100)),
                "solar-arab" to face(FaceId.SOLAR, style = sol.copy(numerals = Numerals.ARAB)),
                "digital-center" to face(FaceId.DIGITAL, style = dig.copy(layout = ClockLayout.CENTER)),
                "digital-twolines" to face(FaceId.DIGITAL, style = dig.copy(layout = ClockLayout.TWO_LINES)),
                "digital-10-58" to face(FaceId.DIGITAL, Fixtures.full().copy(now = LocalDateTime.of(2026, 11, 9, 10, 58))),
                "digital-20-08-24h" to face(FaceId.DIGITAL, Fixtures.full().copy(now = LocalDateTime.of(2026, 11, 9, 20, 8), use24h = true)),
                "float-10-58" to face(FaceId.FLOAT, Fixtures.full().copy(now = LocalDateTime.of(2026, 11, 9, 10, 58))),
                "float-20-08-24h" to face(FaceId.FLOAT, Fixtures.full().copy(now = LocalDateTime.of(2026, 11, 9, 20, 8), use24h = true)),
                "world-24h" to face(FaceId.WORLD, Fixtures.full().copy(use24h = true)),
                "desk-empty" to face(FaceId.DESK, StandbyState(now = Fixtures.now)),
                "styled-time" to styled(dig),
                "float-1-05" to face(FaceId.FLOAT, Fixtures.full().copy(now = LocalDateTime.of(2026, 11, 9, 13, 5))),
                "world-london" to face(FaceId.WORLD, Fixtures.full().copy(worldCity = City("런던", 51.5072, -0.1276), worldZoneId = "Europe/London")),
            ) + ClockStyle.SOLAR_SETS.mapIndexed { i, c ->
                "solar-set$i" to face(FaceId.SOLAR, style = sol.copy(bgColor = c[0], bgColor2 = c[1], color = c[2], color2 = c[3]))
            }
        shots(Device.FOLD_COVER_L, items.map { (n, c) -> "style-$n" to c })
    }

    /**
     * Review X1 target: Digital hero cap ≥ 60 % H for one-digit hours on the Fold cover (landscape). Measured on the
     * render: rows holding accent-blue pixels left of the side column.
     */
    @Test fun digitalHeroSize() {
        // the 60 % blue-digit target is theme A's (iPhone frame); B and C size their own hero
        org.junit.Assume.assumeTrue(theme == Theme.IPHONE)
        RuntimeEnvironment.setQualifiers(Device.FOLD_COVER_L.qualifiers)
        rule.setContent { ProvideTheme(theme) { Box(Modifier.fillMaxSize().background(Color.Black)) { face(FaceId.DIGITAL)() } } }
        rule.waitForIdle()
        val path = shotPath(theme, "clock", "hero-size-check")
        rule.onRoot().captureRoboImage(path)
        val bmp = android.graphics.BitmapFactory.decodeFile(path)
        var top = -1; var bottom = -1
        for (y in 0 until bmp.height) for (x in 0 until (bmp.width * 0.62f).toInt()) {
            val c = bmp.getPixel(x, y)
            if (android.graphics.Color.blue(c) > 200 && android.graphics.Color.red(c) in 90..150) { if (top < 0) top = y; bottom = y; break }
        }
        val pct = (bottom - top + 1).toFloat() / bmp.height
        println("digital hero cap = ${"%.1f".format(pct * 100)} % H")
        org.junit.Assert.assertTrue("hero cap $pct < 0.60", pct >= 0.60f)
    }

    private fun styled(st: ClockStyle): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StyledTime("18:24", st, Ink.of(0f, Color(st.color)), Modifier.size(300.dp, 120.dp), effects = false)
        }
    }

    private fun slot(f: FaceId, w: Int, h: Int): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ClockFace(f, Themes.of(theme).clockDefaults(f), Fixtures.full(), Modifier.size(w.dp, h.dp), inSlot = true)
        }
    }

    /** The same faces inside board slots (S square, XS, tall) to check inSlot fitting. */
    @Test fun inSlot() {
        val sizes = listOf("m" to (337 to 419), "xs" to (152 to 152), "wide" to (337 to 199))
        val items = listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.SOLAR, FaceId.FLOAT).flatMap { f ->
            sizes.map { (n, wh) -> "slot-${f.name.lowercase()}-$n" to slot(f, wh.first, wh.second) }
        }
        shots(Device.FOLD_COVER_L, items)
    }
}
