package kr.dot.catchpet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.game.CapClose
import kr.dot.catchpet.pet.Frames
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The felt catch parts (mockups/next7v4) frame by frame, 2x, for a look by eye next to the mockup
 * (build/outputs/roborazzi/felt-claw.png): claw idle / shut with the middle prong in front of the capsule, the capsule
 * opening, the lid snapping, a hanging tilt, the pop, the button ready / pressed / busy, the felt pills, and the world.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClawShotTest {
    @Test fun felt() {
        val ctx = RuntimeEnvironment.getApplication()
        FeltArt.load(ctx.resources); FeltArt.loadPano(ctx.resources)
        val pic = Frames.asset(ctx, Critters.ids(ctx).first(), "dir0")!!.let(Frames::trim)
        val s = 2f
        val cell = 130f
        val b = Bitmap.createBitmap((cell * 7 * s).toInt(), (560 * s).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(0xFF8E8E8E.toInt()); cv.scale(s, s)
        FeltArt.rail(cv, cell * 7, 12f)
        val cc = CapClose(300f)
        val k = FeltArt.CapLook()
        // capsule clock per frame (-1 = none), claw close, capsule angle, popped
        val frames = listOf(Triple(-1f, 0f, 0f), Triple(60f, 1f, 0f), Triple(300f, 1f, 0f), Triple(cc.lidAt + 120f, 1f, 0f),
            Triple(cc.lidEnd + 60f, 1f, 0f), Triple(cc.end + 100f, 1f, 7f), Triple(cc.end + 100f, 0.5f, -10f))
        for ((i, f) in frames.withIndex()) {
            val (capT, close, ang) = f
            val cx = cell * i + cell / 2
            val tipY = 170f
            val top = tipY - FeltArt.CLAW_TIP
            FeltArt.string(cv, cx, 12f, top + 6f)
            FeltArt.claw(cv, cx, top, close, 0f, front = false)
            if (capT >= 0f) {
                val r = Math.toRadians(ang.toDouble())
                k.x = cx - Math.sin(r).toFloat() * 30f; k.y = tipY + Math.cos(r).toFloat() * 30f; k.rot = ang
                k.color = CapColors.CAP_COLORS[i % CapColors.CAP_COLORS.size]; k.content = pic
                val (ps, pdy, pa) = cc.pop(capT); k.popS = ps; k.popDy = pdy; k.popA = pa
                k.lid = cc.lid(capT); val q = cc.squash(capT); k.sx = q; k.sy = 2f - q
                k.showContent = capT >= cc.fillAt + cc.fillMs
                if (i == 6) { k.topDx = 20f; k.topDy = -40f; k.topRot = 80f; k.topA = 0.5f; k.botDx = -6f; k.botDy = 50f; k.botRot = -15f; k.botA = 0.7f; k.showContent = false }
                else { k.topDx = 0f; k.topDy = 0f; k.topRot = 0f; k.topA = 1f; k.botDx = 0f; k.botDy = 0f; k.botRot = 0f; k.botA = 1f }
                FeltArt.capsule(cv, k)
            }
            FeltArt.claw(cv, cx, top, close, 0f, front = true)
            // the dropped capsule with its floor shadow, squashed on a bounce
            k.x = cx; k.y = 300f; k.rot = 0f; k.lid = 0f; k.popS = 1f; k.popDy = 0f; k.popA = 1f; k.showContent = true
            k.topDx = 0f; k.topDy = 0f; k.topRot = 0f; k.topA = 1f; k.botDx = 0f; k.botDy = 0f; k.botRot = 0f; k.botA = 1f
            k.fromBottom = true; k.floor = 345f + i * 3f; k.sx = 1f + 0.03f * i; k.sy = 1f - 0.04f * i
            FeltArt.capsule(cv, k)
            k.fromBottom = false; k.floor = Float.NaN
        }
        for ((i, st) in listOf(true to false, true to true, false to false).withIndex())
            FeltArt.button(cv, 14f + i * 124f, 370f, ready = st.first, pressed = st.second)
        FeltArt.text.textSize = 20f
        val r = RectF(400f, 380f, 560f, 424f)
        FeltArt.pill(cv, r, FeltArt.SUN, radius = 16f)
        FeltArt.text.color = FeltArt.INK; cv.drawText("밤톨 뽑았다!", r.centerX(), r.centerY() + 7f, FeltArt.text)
        r.set(580f, 380f, 740f, 424f); FeltArt.pill(cv, r, FeltArt.PAPER, radius = 16f)
        cv.drawText("흔들려서 빠졌다", r.centerX(), r.centerY() + 7f, FeltArt.text)
        FeltArt.text.textSize = 15f
        r.set(760f, 387f, 860f, 417f); FeltArt.pill(cv, r, FeltArt.CREAM); cv.drawText("도감 8", r.centerX(), r.centerY() + 5.5f, FeltArt.text)
        cv.save(); cv.clipRect(0f, 460f, cell * 7, 560f)
        FeltArt.pano(cv, cell * 7, 100f, 0f, 2000f, 520f)
        cv.restore()
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/felt-claw.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
