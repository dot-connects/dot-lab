package kr.dot.sb.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.sb.Device
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Presets
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every clickable on a board is at least 48 × 48 dp: presets + each card filling every layout, full and empty data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TouchTargetTest {
    @get:Rule val rule = createComposeRule()

    private fun check(device: Device) {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        val boards = Presets.boards() + CardType.entries.flatMap { c -> Layout.entries.map { l -> Board("${c.name}-${l.name}", "", l, List(l.slots) { listOf(c) }) } }
        val cases = boards.flatMap { b -> listOf("full", "noperm", "emptyA").map { b to it } }
        var case by mutableStateOf(cases.first())
        rule.setContent { BoardView(case.first, BoardShots.stateOf(case.second, case.first), AppConfig(), StandbyActions.NONE) }
        val bad = sortedSetOf<String>()
        for (c in cases) {
            case = c
            rule.waitForIdle()
            val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
            for (n in nodes) {
                val w = n.size.width / rule.density.density
                val h = n.size.height / rule.density.density
                if (w < 47.5f || h < 47.5f) {
                    val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    bad += "${c.first.id}/${c.second}: ${w.toInt()}x${h.toInt()} dp '$text'"
                }
            }
        }
        assertEquals("", bad.joinToString("\n"))
    }

    @Test fun foldCover() = check(Device.FOLD_COVER_L)

    @Test fun flip() = check(Device.FLIP)
}
