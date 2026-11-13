package kr.dot.catchpet.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kr.dot.catchpet.game.Dangle
import kr.dot.catchpet.game.Life
import kr.dot.catchpet.game.Pose
import kr.dot.catchpet.pet.Frames
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A strip of critter poses (hop, contact squash, hanging struggle, landing frames) for a look by eye. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PoseShotTest {
    @Test fun strip() {
        val ctx = RuntimeEnvironment.getApplication()
        val pic = Frames.asset(ctx, Critters.ids(ctx).first(), "dir0")!!.let(Frames::trim)
        val d = Dangle { 0.5f }
        repeat(10) { d.step(16f, 0f, 12f) }
        val (sr, sdx, ssq) = d.struggle(1f)
        val poses = listOf(
            "rest" to Pose.REST,
            "hop" to Life.idle(180f, 0f),
            "grip" to Pose(sx = 1.06f, sy = 0.90f),
            "hang" to Pose(dx = sdx, rot = 9f + sr, sx = 0.96f - ssq * 0.5f, sy = 1.05f + ssq, pivot = 0.3f),
            "land0" to Life.land(10f),
            "land200" to Life.land(220f),
            "shake" to Life.land(760f),
        )
        val cell = 200
        val b = Bitmap.createBitmap(cell * poses.size, 320, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(0xFF9FD07F.toInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val line = Paint().apply { color = 0x66000000; strokeWidth = 2f }
        for ((i, np) in poses.withIndex()) {
            val h = 150f; val w = h * pic.width / pic.height
            val cx = i * cell + cell / 2f; val feet = 260f
            cv.drawLine(i * cell.toFloat(), feet, (i + 1) * cell.toFloat(), feet, line)
            val r = RectF(cx - w / 2, feet - h, cx + w / 2, feet)
            Posed.draw(cv, pic, r, np.second, paint)
            if (np.first == "land0") Posed.dust(cv, r, 120f)
        }
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/poses.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
