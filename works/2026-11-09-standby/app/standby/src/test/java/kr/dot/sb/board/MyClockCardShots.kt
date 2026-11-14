package kr.dot.sb.board

import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Theme
import kr.dot.sb.shot
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 내 시계 (11-14): clock cards draw the user's own style for their face as-is (font, colours), the hero and the second,
 * smaller clock alike. IPHONE theme, Fold cover.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MyClockCardShots {
    @get:Rule val rule = createComposeRule()

    private fun board(face: FaceId, styled: kr.dot.sb.model.ClockStyle, name: String) {
        val b = Board("mine", "내 시계", Layout.TWO, listOf(listOf(CardType.CLOCK), listOf(CardType.CLOCK)), clockFace = face)
        val config = AppConfig(theme = Theme.IPHONE, styles = mapOf(face to styled))
        rule.shot("board", name, Device.FOLD_COVER_L, Theme.IPHONE) { BoardView(b, Fixtures.full(), config, StandbyActions.NONE) }
    }

    @Test fun digital() = board(
        FaceId.DIGITAL,
        Themes.of(Theme.IPHONE).clockDefaults(FaceId.DIGITAL).copy(font = FontId.LIGHT, weight = FontId.LIGHT.defaultWeight, color = 0xFF6FDC86),
        "myclock-digital",
    )

    @Test fun flip() = board(
        FaceId.FLIP,
        Themes.of(Theme.IPHONE).clockDefaults(FaceId.FLIP).copy(plateColor = 0xFF2A4A7A, digitColor = 0xFFFFD45C),
        "myclock-flip",
    )
}
