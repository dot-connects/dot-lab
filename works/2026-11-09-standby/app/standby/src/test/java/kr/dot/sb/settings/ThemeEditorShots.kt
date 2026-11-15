package kr.dot.sb.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.Device
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.ui.theme.withMyThemes
import kr.dot.sb.ui.theme.withThemeEdit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * plan-r11 A7 → standby/build/shots/theme-editor/: the editor for 무드등, 하늘, 터미널; StandBy with an edited 무드등
 * (day, sunset, night = the unedited night, flow at 09:30); a board with one card's own plate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeEditorShots : SettingsShotKit() {
    private fun path(name: String) = "build/shots/theme-editor/$name.png"

    /** 무드등 edited: glass cards, the glow moved right, a lilac glow; no radius edit (the night must equal the unedited one). */
    private val mood = ThemeEdit(plateStyle = PlateStyle.GLASS, glowAt = 0.82f to 0.35f, glow = 0xFFAA88FE, glowStrength = 1f)

    private fun moodCfg(e: ThemeEdit = mood) = AppConfig(theme = Theme.MOODLIGHT, ddays = Fixtures.ddays, startPage = 0).withThemeEdit(Theme.MOODLIGHT, e)

    private fun editor(name: String, c: AppConfig, after: androidx.compose.ui.test.junit4.ComposeContentTestRule.() -> Unit = {}) =
        snap("../theme-editor/$name", tallest, c, route = Routes.THEME, after = after)

    @Test fun editorMoodlight() = editor("editor-moodlight", moodCfg(mood.copy(flow = listOf(0xFFFF9C56, 0xFFAA5A2E, 0xFFAA88FE))).withMyThemes(listOf(MyTheme("보라 무드", Theme.MOODLIGHT, mood)))) {
        onAllNodesWithText("낮").let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick()
    }
    @Test fun editorSky() = editor("editor-sky", AppConfig(theme = Theme.SKY))
    @Test fun editorTerminal() = editor("editor-terminal", AppConfig(theme = Theme.TERMINAL))
    /** A preset background: the note and [테마 기본으로 바꾸기] instead of the controls. */
    @Test fun editorPreset() = editor("editor-preset", AppConfig(theme = Theme.MOODLIGHT, boardBg = kr.dot.sb.model.BoardBg.AURORA))
    /** [색 조합 바꾸기] pressed once on 기본. */
    @Test fun editorShuffle() = snap("../theme-editor/editor-shuffle", phone, AppConfig(), route = Routes.THEME) { onNodeWithText("색 조합 바꾸기").performScrollTo().performClick() }

    private fun board(name: String, c: AppConfig, night: Float = 0f, now: LocalDateTime = Fixtures.now) {
        val d = Device.FOLD_COVER_L
        RuntimeEnvironment.setQualifiers(d.qualifiers)
        rule.mainClock.autoAdvance = false
        val s = Fixtures.full(night).copy(now = now).let { it.copy(media = it.media?.copy(playing = false)) }
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { RootImpl(s, c, StandbyActions.NONE, Modifier.fillMaxSize(), { _, _ -> }, RootStart()) } }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path("$name-${d.label}"))
    }

    @Test fun boardMoodlight() = board("board-moodlight", moodCfg())
    @Test fun boardMoodlightSunset() = board("board-moodlight-sunset", moodCfg(mood.copy(glowShape = GlowShape.SUNSET, plateStyle = PlateStyle.OUTLINE)))
    @Test fun boardMoodlightNight() = board("board-moodlight-night", moodCfg(), night = 1f)
    @Test fun boardMoodlightNightUnedited() = board("board-moodlight-night-unedited", AppConfig(theme = Theme.MOODLIGHT, ddays = Fixtures.ddays, startPage = 0), night = 1f)
    /** 카드 색 (A6): 모듈러 with a yellow weather card and a dark red agenda card; text fitted on each plate. */
    private val plated = AppConfig(ddays = Fixtures.ddays, startPage = 3).let { c ->
        c.copy(boards = c.boards.map { b ->
            if (b.id != "modular") b else b.copy(options = b.options + ("0:0" to SlotOptions(plate = 0xFFFFD74B)) + ("1:0" to SlotOptions(plate = 0xFFC0392B)))
        })
    }

    @Test fun boardCardPlate() = board("board-card-plate", plated)
    @Test fun boardCardPlateNight() = board("board-card-plate-night", plated, night = 1f)
    /** The board editor with a card row's 설정 open on 카드 색. */
    @Test fun boardEditorCardPlate() = snap("../theme-editor/board-editor-card-plate", tall, plated, route = Routes.board("modular")) {
        onAllNodesWithText("설정")[0].performScrollTo().performClick()
    }

    @Test fun boardMoodlightFlow() = board(
        "board-moodlight-flow-0930", moodCfg(mood.copy(flow = listOf(0xFFFF9C56, 0xFF4D9BEF, 0xFFAA88FE))), now = Fixtures.now.withHour(9).withMinute(30),
    )
}
