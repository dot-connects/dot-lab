package kr.dot.catchpet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The 집기 button ready / pressed / busy on grass, 3x, for a look by eye (build/outputs/roborazzi/button.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonShotTest {
    @Test fun button() {
        val s = 3f
        val b = Bitmap.createBitmap((3 * 110 * s).toInt(), (150 * s).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(0xFF8CC86F.toInt()); cv.scale(s, s)
        for ((i, st) in listOf(true to false, true to true, false to false).withIndex())
            ClawButton.draw(cv, 55f + i * 110f, 60f, 29f, 14f, 32f, ready = st.first, pressed = st.second)
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/button.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
