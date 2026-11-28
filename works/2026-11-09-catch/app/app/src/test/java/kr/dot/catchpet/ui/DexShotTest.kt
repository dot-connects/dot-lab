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
        Dex.savePreview(ctx, dog.id, dot(0xFFF0D0A0.toInt()), "png", "fluff")
        Dex.savePreview(ctx, dog.id, dot(0xFFB0C0F0.toInt()), "png", "figure")
        Dex.saveCard(ctx, dog.id, "이불 위에서 혀 내밀고 웃다 뽑힘. 아직도 웃는 중", listOf("해맑음", "혀빼꼼"))
        val cat = Dex.list(ctx).first { it.kind == "cat" }
        Dex.setFav(ctx, cat.id, true)
        var screen by androidx.compose.runtime.mutableStateOf("dex")
        rule.setContent {
            when (screen) {
                "dex" -> DexScreen({}, {}, {})
                "detail" -> DetailScreen(dog.id, {}) {}
                "fresh" -> DetailScreen(cat.id, {}) {}
                else -> PetScreen {}
            }
        }
        for (s in listOf("dex", "detail", "fresh", "pet")) {
            screen = s
            rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/outputs/roborazzi/${if (s == "pet") "petsettings" else s}.png")
        }
        Dex.setFav(ctx, cat.id, false)
    }
}
