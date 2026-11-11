package kr.dot.sb.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import kr.dot.sb.Fixtures
import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.StandbyActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** r8: the swipe that pulls the brightness bar down keeps sliding it, without lifting the finger. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w751dp-h475dp-land-xhdpi")
class BrightnessSwipeTest {
    @get:Rule val rule = createComposeRule()

    private class Rec : StandbyActions {
        val set = mutableListOf<Float>()
        override fun setBrightness(value: Float) { set += value }
    }

    private fun swipe(dxFraction: Float): Rec {
        val rec = Rec()
        rule.setContent { RootImpl(Fixtures.full(), AppConfig(dayBrightness = 0.5f), rec, Modifier, { _, _ -> }, RootStart()) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput {
            val x = centerX
            down(Offset(x, 4f))
            for (i in 1..6) moveTo(Offset(x, 4f + i * 20f))
            for (i in 1..10) moveTo(Offset(x + width * dxFraction * i / 10f, 124f))
            up()
        }
        rule.waitForIdle()
        return rec
    }

    @Test fun pullDownThenRightBrightens() {
        val rec = swipe(0.3f)
        assertTrue(rec.set.isNotEmpty())
        assertTrue("last ${rec.set.last()}", rec.set.last() > 0.8f)
    }

    @Test fun pullDownThenLeftDims() {
        val rec = swipe(-0.2f)
        assertTrue("last ${rec.set.last()}", rec.set.last() < 0.3f)
    }

    @Test fun pullDownAloneKeepsTheLevel() {
        val rec = swipe(0f)
        assertEquals(emptyList<Float>(), rec.set)
    }
}
