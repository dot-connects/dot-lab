package kr.dot.sb.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.THEMES
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.ThemeBackground
import kr.dot.sb.ui.theme.ThemedDigits
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.themedSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Page, plates and hero digits of every theme (Task 5 primitives), day and night → build/shots/<theme>/primitives/. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PrimitivesShots(private val name: String, private val night: Float, private val bg: BoardBg, private val th: Theme) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() = rule.shot("primitives", name, Device.FOLD_COVER_L, th) {
        val t = Themes.of(th)
        val ink = Ink.of(night, null, t)
        val shape = RoundedCornerShape(26.dp)
        val custom = bg != BoardBg.THEME
        ThemeBackground(t, night, bg = bg, photo = Fixtures.photo)
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.weight(1.5f).fillMaxHeight().themedSurface(t, ink, shape, custom).padding(24.dp)) {
                ThemedDigits("9:41", t, ink, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f).themedSurface(t, ink, shape, custom).padding(16.dp)) {
                    ThemedDigits("9:41", t, ink, Modifier.fillMaxSize(), twoLines = true)
                }
                Box(Modifier.weight(1f).themedSurface(t, ink, shape, custom).padding(16.dp)) {
                    ThemedDigits("18°", t, ink, Modifier.fillMaxSize(), color = ink.ink)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{3}-{0}")
        fun cases(): List<Array<Any>> {
            val base = listOf<Array<Any>>(arrayOf("day", 0f, BoardBg.THEME), arrayOf("dusk", 0.5f, BoardBg.THEME), arrayOf("night", 1f, BoardBg.THEME))
            val bgs = listOf(BoardBg.GRAPHITE, BoardBg.MIDNIGHT, BoardBg.AMBER, BoardBg.AURORA, BoardBg.PHOTO_BLUR)
                .map { arrayOf<Any>("bg-" + it.name.lowercase(), 0f, it) }
            return THEMES.flatMap { th -> (base + if (th == Theme.IPHONE) bgs else emptyList()).map { it + th } }
        }
    }
}
