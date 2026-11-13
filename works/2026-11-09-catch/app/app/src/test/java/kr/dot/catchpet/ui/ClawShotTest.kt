package kr.dot.catchpet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.pet.Frames
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The claw on a critter, frame by frame (descend, spread, hug, lifted), near and far critter, for a look by eye. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClawShotTest {
    @Test fun hug() {
        val ctx = RuntimeEnvironment.getApplication()
        val pic = Frames.asset(ctx, Critters.ids(ctx).first(), "dir0")!!.let(Frames::trim)
        val scale = 2f   // dp -> px
        val cell = 170f
        val frames = listOf("idle" to 0, "touch" to 1, "spread" to 2, "hug" to 3, "lift" to 4)
        val b = Bitmap.createBitmap((cell * frames.size * 2 * scale).toInt(), (330 * scale).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(0xFF9FD07F.toInt()); cv.scale(scale, scale)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var col = 0
        for (h in floatArrayOf(92f, 46f)) {
            val w = h * pic.width / pic.height
            for ((_, step) in frames) {
                val cx = col * cell + cell / 2; col++
                val feet = 300f
                val body = RectF(cx - w / 2, feet - h, cx + w / 2, feet)
                val dig = minOf(0.3f * h, 26f)
                var hubY = body.top - 44f + dig
                var arms = ClawArt.idle(1f)
                var lift = 0f
                when (step) {
                    0 -> { hubY = 60f; arms = ClawArt.idle(1f) }
                    1 -> { hubY = body.top - 44f; arms = ClawArt.idle(1f) }
                    2 -> arms = ClawArt.on(ClawArt.Body(w / 2, body.top - hubY, h), 1f)
                    3 -> arms = ClawArt.on(ClawArt.Body(w / 2, body.top - hubY, h), 0f)
                    4 -> { arms = ClawArt.on(ClawArt.Body(w / 2, body.top - hubY, h), 0f); lift = -90f }
                }
                cv.save()
                if (step == 4) cv.rotate(4f, cx, 20f)
                ClawArt.back(cv, cx, hubY + lift, 20f, arms)
                val r = RectF(body); r.offset(0f, lift)
                if (step == 4) { cv.save(); cv.scale(0.96f, 1.05f, r.centerX(), r.top + r.height() * 0.6f) }
                cv.drawBitmap(pic, null, r, paint)
                if (step == 4) cv.restore()
                ClawArt.front(cv, cx, hubY + lift, arms)
                cv.restore()
            }
        }
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/claw.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
