package kr.dot.sb.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.groupOn
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.Border
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Texture
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.shotPath
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.ui.theme.ThemeBackground
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.drawnTheme
import kr.dot.sb.ui.theme.withThemeEdit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * plan-r13 A3 effects only (build/shots/<theme>/effects/): each texture on one board, border thin/thick, glass/rule/
 * outline on IPHONE, the three card fonts with a KS X 1001-heavy notification, text glow, glow2, and one edit with
 * texture + border + text glow at night 0, 0.3 (reduced) and 1 (absent). Built-in themes are covered by the
 * existing board/clock shots (baseline verify).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeEffectShots(private val name: String, private val theme: Theme, private val night: Float) {
    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        if (!groupOn("effects")) return
        val d = Device.FOLD_COVER_L
        RuntimeEnvironment.setQualifiers(d.qualifiers)
        val cfg = AppConfig(theme = theme, ddays = Fixtures.ddays).withThemeEdit(theme, edit(name))
        val board = Board("fx", "FX", Layout.FOUR, listOf(listOf(CardType.NOTIF), listOf(CardType.WEATHER), listOf(CardType.CALENDAR), listOf(CardType.TIMER)))
        var s = Fixtures.full(night)
        s = s.copy(timers = mapOf("fx-3" to s.timers.values.first()))
        // glow2 is a token (no edit field): the page is drawn from MOODLIGHT's tokens with a second, smaller yellow glow
        val page = if (name == "glow2") Themes.edited(Themes.MOODLIGHT, Themes.MOODLIGHT.tokens.let {
            it.copy(bg = it.bg.copy(glow2 = Color(0x38FFD27A), glow2At = Offset(.5f, 1f), glow2Size = Offset(.45f, .4f)))
        }) else cfg.drawnTheme()
        rule.setContent { Box(Modifier.fillMaxSize()) { ThemeBackground(page, night); BoardView(board, s, cfg, StandbyActions.NONE) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(shotPath(theme, "effects", name, d))
    }

    companion object {
        private val NIGHT_EDIT = ThemeEdit(bgTexture = Texture.LINEN, cardTexture = Texture.GRAIN, textureAmt = 0.8f, border = Border.THICK, textGlow = 0.7f)

        fun edit(name: String): ThemeEdit = when {
            name.startsWith("tex-") -> Texture.valueOf(name.removePrefix("tex-").uppercase()).let { ThemeEdit(bgTexture = it, cardTexture = it, textureAmt = 0.6f) }
            name == "border-thin" -> ThemeEdit(border = Border.THIN)
            name == "border-thick" -> ThemeEdit(border = Border.THICK, borderColor = 0xFFFFAD5C)
            name.startsWith("shape-") -> ThemeEdit(plateStyle = PlateStyle.valueOf(name.removePrefix("shape-").uppercase()))
            name == "glow-text" -> ThemeEdit(textGlow = 0.8f)
            name == "glow-halo" -> ThemeEdit(plateStyle = PlateStyle.OUTLINE, textGlow = 1f)
            name == "glow-fill" -> ThemeEdit(plateStyle = PlateStyle.FILL, textGlow = 0.6f)
            name.startsWith("night-") -> NIGHT_EDIT
            else -> ThemeEdit()
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> =
            Texture.entries.filter { it != Texture.NONE }.map { arrayOf<Any>("tex-${it.name.lowercase()}", Theme.IPHONE, 0f) } +
                listOf("border-thin", "border-thick", "shape-glass", "shape-rule", "shape-outline", "glow-text")
                    .map { arrayOf<Any>(it, Theme.IPHONE, 0f) } +
                listOf(
                    arrayOf<Any>("glow-halo", Theme.TERMINAL, 0f), // OUTLINE picked on TERMINAL: the halo on a non-glowing theme
                    arrayOf<Any>("glow-fill", Theme.NEON, 0f), // NEON on FILL: a clear plate made visible, text shadow
                    arrayOf<Any>("glow2", Theme.MOODLIGHT, 0f),
                    arrayOf<Any>("night-0", Theme.IPHONE, 0f),
                    arrayOf<Any>("night-0.3", Theme.IPHONE, 0.3f),
                    arrayOf<Any>("night-1", Theme.IPHONE, 1f),
                )
    }
}
