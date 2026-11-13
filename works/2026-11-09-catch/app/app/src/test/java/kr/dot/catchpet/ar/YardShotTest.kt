package kr.dot.catchpet.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import kr.dot.catchpet.camera.Yard
import kr.dot.catchpet.seg.ScreenMap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Renders the camera-off yard with the real scene critters to build/outputs/roborazzi for a look (no device here). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YardShotTest {
    @Test fun yard() {
        val ctx = RuntimeEnvironment.getApplication()
        val w = 1080; val h = 2340
        val crit = Critters(ctx, scene = true, rnd = Random(3))
        val map = ScreenMap(w, h, 0, w.toFloat(), h.toFloat())
        crit.masks(map)
        for ((name, pal) in listOf("day" to Yard.DAY, "dusk" to Yard.DUSK, "night" to Yard.NIGHT)) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            Yard.back(cv, Yard.View(w.toFloat(), h.toFloat(), 0f, 0f, 1f, pal))
            crit.draw(cv, map, null, 0f)
            File("build/outputs/roborazzi").mkdirs()
            File("build/outputs/roborazzi/yard-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
