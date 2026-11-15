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
import androidx.compose.ui.test.performSemanticsAction
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
 * plan-r11 A7 / r13 A4 → standby/build/shots/theme-editor/: 사용자 지정 (3 tabs, phone and landscape, guard); StandBy with an edited 무드등
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

    private fun editor(name: String, c: AppConfig, q: String = tallest, screen: Boolean = false, after: androidx.compose.ui.test.junit4.ComposeContentTestRule.() -> Unit = {}) =
        snap("../theme-editor/$name", q, c, route = Routes.THEME_CUSTOM, screen = screen, after = after)

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.tab(name: String) { onNodeWithText(name).performScrollTo().performClick() }

    /** Scrolls the rows by [px]: the page's first vertical scroller, or on a two-pane page ([pane]) the last (the right pane). */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.scrollRows(px: Float, pane: Boolean = false) =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .let { it[if (pane) it.fetchSemanticsNodes().size - 1 else 0] }
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, px) }

    private val land780 = "w780dp-h360dp-land-xxhdpi"
    private val land568 = "w568dp-h320dp-land-xxhdpi"
    /** 무드등 edited (glass, lilac glow, linen page, a border), saved as 보라 무드 and then changed: "저장 안 함". */
    private val custom = moodCfg(mood.copy(bgTexture = kr.dot.sb.model.Texture.LINEN, border = kr.dot.sb.model.Border.THIN, borderColor = 0xFFAA88FE))
        .withMyThemes(listOf(MyTheme("보라 무드", Theme.MOODLIGHT, mood))).copy(customFrom = "보라 무드")

    @Test fun editorMoodlight() = editor("editor-moodlight", moodCfg(mood.copy(flow = listOf(0xFFFF9C56, 0xFFAA5A2E, 0xFFAA88FE))).withMyThemes(listOf(MyTheme("보라 무드", Theme.MOODLIGHT, mood)))) {
        onAllNodesWithText("낮").let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick()
    }
    @Test fun customCard() = editor("custom-card", custom) { tab("카드") }
    @Test fun customText() = editor("custom-text", custom) { tab("글자") }
    /** 명조 on 종이: 글자 두께 is off with its reason. */
    @Test fun customTextPaper() = editor("custom-text-paper", AppConfig(theme = Theme.DAYPAPER)) { tab("글자") }
    @Test fun customGradient() = editor("custom-gradient", AppConfig().withThemeEdit(Theme.IPHONE, ThemeEdit(bgKind = kr.dot.sb.model.BgKind.GRADIENT)))
    /** Phone, scrolled past the preview: the mini on top, the tab row pinned under it. */
    @Test fun customScrolled() = editor("custom-scrolled", custom, phone) { tab("카드"); scrollRows(1400f) }
    @Test fun customLand780() = editor("custom-land780", custom, land780) { tab("카드") }
    @Test fun customLand780Scrolled() = editor("custom-land780-scrolled", custom, land780) { tab("카드"); scrollRows(900f, pane = true) }
    @Test fun customLand568() = editor("custom-land568", custom, land568)
    /** 568 x 320: no mini (too short), only the tab row pinned. */
    @Test fun customLand568Scrolled() = editor("custom-land568-scrolled", custom, land568) { scrollRows(900f) }
    /** A start chip while the look is saved nowhere: the guard. */
    @Test fun customGuard() = editor("custom-guard", custom, phone, screen = true) { onNodeWithText("네온").performScrollTo().performClick() }
    @Test fun editorSky() = editor("editor-sky", AppConfig(theme = Theme.SKY))
    @Test fun editorTerminal() = editor("editor-terminal", AppConfig(theme = Theme.TERMINAL))
    /** A preset background: the note and [테마 기본으로 바꾸기] instead of the controls. */
    @Test fun editorPreset() = editor("editor-preset", AppConfig(theme = Theme.MOODLIGHT, boardBg = kr.dot.sb.model.BoardBg.AURORA))
    /** [색 조합 바꾸기] pressed once on 기본. */
    @Test fun editorShuffle() = editor("editor-shuffle", AppConfig(), phone) { onNodeWithText("색 조합 바꾸기").performScrollTo().performClick() }
    /** The theme strip while the look is saved nowhere: ＋ 사용자 지정 marked "저장 안 함". */
    @Test fun themeUnsaved() = snap("../theme-editor/theme-unsaved", phone, custom, route = Routes.THEME)

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
