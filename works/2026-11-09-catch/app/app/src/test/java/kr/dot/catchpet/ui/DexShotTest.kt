package kr.dot.catchpet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.catchpet.dex.Dex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Renders the dex and a real thing's detail to build/outputs/roborazzi for a look (no device here). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DexShotTest {
    @get:Rule val rule = createComposeRule()

    private fun dot(color: Int): ByteArray {
        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        Canvas(b).drawOval(30f, 40f, 270f, 380f, Paint().apply { this.color = color })
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun dexAndDetail() {
        val ctx = RuntimeEnvironment.getApplication()
        Dex.root(ctx).deleteRecursively()
        val dog = Dex.add(ctx, "dog", dot(0xFFC08850.toInt()), null, "front")
        Dex.add(ctx, "critter", null, "monggeul", "dir0")
        Dex.add(ctx, "critter", null, "monggeul", "dir2")
        Dex.add(ctx, "cat", dot(0xFF555555.toInt()), null, "front")
        val lost = Dex.add(ctx, "critter", null, "nope-missing", "dir0")
        org.junit.Assert.assertNotNull("placeholder", posePicture(ctx, lost, "front"))
        Dex.savePreview(ctx, dog.id, dot(0xFFF0D0A0.toInt()), "png")
        var screen by androidx.compose.runtime.mutableStateOf("dex")
        rule.setContent { if (screen == "dex") DexScreen({}, {}, {}) else DetailScreen(dog.id, {}) {} }
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/dex.png")
        screen = "detail"
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/detail.png")
    }
}
