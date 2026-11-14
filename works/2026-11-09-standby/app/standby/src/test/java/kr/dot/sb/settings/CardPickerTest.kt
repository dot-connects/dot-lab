package kr.dot.sb.settings

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 카드 고르기 (operator 11-14 "카드 추가가 안돼"): a tap on a tile's picture picks it, not only on its label; in add mode an
 * added card is marked and inert, 완료 closes. NATIVE graphics: legacy mode does not hit-test inside the scaled preview.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardPickerTest {
    @get:Rule val rule = createComposeRule()

    private fun sheet(current: CardPick?, taken: Set<CardPick>, picked: MutableList<CardPick>, done: () -> Unit = {}) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-port-xxhdpi")
        val c = AppConfig()
        rule.setContent {
            SettingsTheme(SK.ok) {
                CardPickerSheet("카드 고르기", 337, 419, c, Sample.state(c), null, FaceId.DIGITAL, current, taken, cardFaces(c, emptyList()), onDismiss = done, onPick = { picked += it })
            }
        }
    }

    /** The tile = the clickable node that merges its label (the label sits under the picture). */
    private fun tile(name: String) = rule.onAllNodes(hasClickAction() and hasText(name, substring = true)).onFirst()

    @Test
    fun tapOnThePicturePicks() {
        val picked = mutableListOf<CardPick>()
        sheet(null, emptySet(), picked)
        // the media tile (play/skip buttons of its own): tap its picture's middle, not the label under it
        tile(CardType.MUSIC.ko).performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, centerY - 20.dp.toPx())) }
        rule.waitForIdle()
        assertEquals(listOf(CardPick(CardType.MUSIC)), picked)
    }

    @Test
    fun addedCardIsMarkedAndInert() {
        val picked = mutableListOf<CardPick>()
        var closed = false
        sheet(null, setOf(CardPick(CardType.WEATHER)), picked) { closed = true }
        rule.onNodeWithText("추가됨").assertExists()
        tile(CardType.WEATHER.ko).performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 30.dp.toPx())) }
        rule.waitForIdle()
        assertEquals(emptyList<CardPick>(), picked)
        rule.onNodeWithText("완료").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(true, closed)
    }
}
