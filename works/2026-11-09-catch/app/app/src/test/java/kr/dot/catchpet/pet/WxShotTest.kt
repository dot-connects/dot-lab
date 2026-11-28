package kr.dot.catchpet.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.ui.PetScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Phase C for a look by eye: the settings weather switch, and a critter / a photo pet mid-act with the bubble. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WxShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun settingsSwitch() {
        val ctx = RuntimeEnvironment.getApplication()
        Dex.root(ctx).deleteRecursively()
        val e = Dex.add(ctx, "critter", null, "monggeul", "dir0")
        PetPrefs.prefs(ctx).edit().putString("pet", e.id).putBoolean("wx_on", true).putString("wx_state", "rain")
            .putLong("wx_at", System.currentTimeMillis()).apply()
        rule.setContent { PetScreen {} }
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        rule.onNodeWithText("지금 받기").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/wx-settings.png")
    }

    @Test fun midAct() {
        val ctx = RuntimeEnvironment.getApplication()
        val d = ctx.resources.displayMetrics.density
        val w = 1080; val h = 900
        val shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(shot); c.drawColor(0xFF9FB7D9.toInt())
        val rain = Weather.of("rain")!!
        // a critter: frames from the bake, 56 dp box, body as tall as its front picture
        val zip = java.util.zip.ZipFile(File("../../wx3d/monggeul.zip"))
        fun f(kind: String, n: Int) = (0 until n).map { i -> zip.getInputStream(zip.getEntry("monggeul-rain-$kind-%02d.webp".format(i))).use(BitmapFactory::decodeStream)!! }
        val front = BitmapFactory.decodeFile(File("src/main/assets/critters/monggeul/front.webp").path)!!
        val px = (56 * d).toInt()
        val pet = Rect(200, 640, 200 + px, 640 + px)
        val look = PetLook(Frames.CELLS.associateWith { Frames.trim(front) })
        val act = WxActView(ctx, rain, f("act", 26), f("loop", 8), pet, look.frontHeight(px.toFloat()))
        val p = act.params()
        c.save(); c.translate(p.x.toFloat(), p.y.toFloat()); act.drawAt(c, 30 * 1000L / WxPack.FPS); c.restore()
        bubble(c, rain.line, Rect(pet.left, p.y, pet.right, p.y + act.hPx), w, h)
        // a photo pet: its picture plus the 2D prop
        for ((i, key) in listOf("rain", "cold", "dust", "sun", "snow", "hot").withIndex()) {
            val r = Rect(520 + (i % 3) * 180, 180 + (i / 3) * 360, 520 + (i % 3) * 180 + px, 180 + (i / 3) * 360 + px)
            c.drawOval(r.left + px * 0.12f, r.top + px * 0.08f, r.right - px * 0.12f, r.bottom.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC08850.toInt() })
            val v = WxActView(ctx, Weather.of(key)!!, emptyList(), emptyList(), r, 0f)
            val q = v.params()
            c.save(); c.translate(q.x.toFloat(), q.y.toFloat()); v.drawAt(c, 1500); c.restore()
            if (i == 0) bubble(c, Weather.of(key)!!.line, r, w, h)
        }
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/wx-act.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun bubble(c: Canvas, line: String, over: Rect, w: Int, h: Int) {
        val ctx = RuntimeEnvironment.getApplication()
        val t = TalkBubble(ctx, line, PetBrain.Zone.FLOOR, 6000L) {}
        val q = t.params(over, w, h)
        t.measure(View.MeasureSpec.makeMeasureSpec(t.wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(t.hPx, View.MeasureSpec.EXACTLY))
        t.layout(0, 0, t.wPx, t.hPx)
        c.save(); c.translate(q.x.toFloat(), q.y.toFloat()); t.draw(c); c.restore()
    }
}
