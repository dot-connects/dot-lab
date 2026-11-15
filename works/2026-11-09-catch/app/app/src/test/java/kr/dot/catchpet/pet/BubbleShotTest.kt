package kr.dot.catchpet.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The thought cloud (bubble C) on the floor, on the left wall, and empty, for a look by eye (build/outputs/roborazzi/bubble-*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BubbleShotTest {
    @Test fun bubbles() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val apps = listOf("카카오톡" to Color.YELLOW, "YouTube" to Color.RED, "카메라" to Color.GREEN, "지도" to Color.BLUE, "메모" to Color.MAGENTA)
            .map { (n, c) -> PetApps.App(n, n, ColorDrawable(c)) }
        for ((name, case) in listOf("floor" to (apps to PetBrain.Zone.FLOOR), "left" to (apps.take(3) to PetBrain.Zone.LEFT), "empty" to (emptyList<PetApps.App>() to PetBrain.Zone.RIGHT))) {
            val v = AppBubble(ctx, case.first, case.second) {}
            v.measure(View.MeasureSpec.makeMeasureSpec(v.wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.hPx, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, v.wPx, v.hPx)
            val b = Bitmap.createBitmap(v.wPx, v.hPx, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); c.drawColor(0xFF7A86C4.toInt()); v.draw(c)
            File("build/outputs/roborazzi").mkdirs()
            File("build/outputs/roborazzi/bubble-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
