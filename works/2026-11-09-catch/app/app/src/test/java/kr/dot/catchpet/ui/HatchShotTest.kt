package kr.dot.catchpet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The making capsule mid-hatch and the paid button states, to build/outputs/roborazzi for a look. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HatchShotTest {
    @get:Rule val rule = createComposeRule()

    private fun pet() = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawOval(40f, 40f, 260f, 280f, Paint().apply { color = 0xFFC08850.toInt() })
    }.asImageBitmap()

    @Test fun hatchFrames() {
        val pic = pet()
        var opened by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BookPage(sky = 0.37f) {
                Row { HatchTile(pic, opened, onOpened = {}, modifier = Modifier.size(170.dp).padding(8.dp)) }
            }
        }
        rule.mainClock.advanceTimeBy(4_700)   // rocking, all three cracks out
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/hatch-cracks.png")
        opened = true
        rule.mainClock.advanceTimeBy(420)     // lid in the air, the pet jumping out
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/hatch-pop.png")
    }

    @Test fun payButtons() {
        rule.setContent {
            BookPage(sky = 0.37f) {
                Column(Modifier.width(360.dp)) {
                    PayButton("700원", kept = false, waiting = false) {}
                    PayButton(null, kept = false, waiting = false) {}
                    PayButton("700원", kept = true, waiting = false) {}
                    PayButton("700원", kept = false, waiting = true) {}
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/pay-buttons.png")
    }
}
