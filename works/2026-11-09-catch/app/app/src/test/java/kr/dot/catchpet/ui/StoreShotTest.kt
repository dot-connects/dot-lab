package kr.dot.catchpet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File

/**
 * Play store screenshots from the real screens (store/listing.md "찍을 화면"); run with -PstoreShots so the
 * buttons read like the Play build. Pictures: the AI pets in works/2026-11-09-catch/out/ai.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StoreShotTest {
    @get:Rule val rule = createComposeRule()
    private val out = "build/outputs/roborazzi/store"

    private fun png(path: String): ByteArray {
        val b = BitmapFactory.decodeFile(File("../../out/ai/$path").path)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
    private fun jpg(path: String) = File("../../out/ai/$path").readBytes()

    private fun seed(): Dex.Entry {
        // no live Worker from a test: /quota would overwrite the counts below with whatever is deployed
        System.setProperty("http.proxyHost", "127.0.0.1"); System.setProperty("http.proxyPort", "9")
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val ctx = RuntimeEnvironment.getApplication()
        Dex.root(ctx).deleteRecursively()
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().putBoolean("camera_off", true).putBoolean("intro_done", true)
            .putInt("q_preview", 10).putInt("q_preview_total", 10).putInt("q_sheet", 5).putInt("q_sheet_total", 5).commit()
        val cat = Dex.add(ctx, "cat", png("black-cat-ai.jpg"), null, "front", name = "까망이")
        Dex.savePreview(ctx, cat.id, jpg("black-cat-ai.jpg"), "jpg", "fluff")
        Dex.savePreview(ctx, cat.id, jpg("black-cat-fig.jpg"), "jpg", "figure")
        val dog = Dex.add(ctx, "dog", png("white-dog-ai.jpg"), null, "front", name = "솜이")
        Dex.savePreview(ctx, dog.id, jpg("white-dog-ai.jpg"), "jpg", "fluff")
        for (c in listOf("monggeul", "meongsom", "dalsom", "bamtol", "nyangsom")) Dex.add(ctx, "critter", null, c, "front")
        return cat
    }

    @Test fun catchScreen() {
        seed()
        val sink = object : CatchSink {
            override fun onCaught(kind: String, png: Bitmap?, critterId: String?, label: String, cap: Int) = Caught(true, "몽글")
        }
        rule.mainClock.autoAdvance = false   // the catch screen animates forever: step the clock by hand
        rule.setContent { CatchScreen(sink, NoCritters, 7) {} }
        repeat(4) { rule.mainClock.advanceTimeBy(500); Thread.sleep(500) }
        rule.onRoot().captureRoboImage("$out/catch.png")
    }

    @Test fun dexScreen() {
        seed()
        rule.setContent { DexScreen({}, {}, {}) }
        repeat(3) { rule.waitForIdle(); Thread.sleep(1500) }; rule.waitForIdle()
        rule.onRoot().captureRoboImage("$out/dex.png")
    }

    @Test fun detailScreen() {
        val cat = seed()
        rule.setContent { DetailScreen(cat.id, {}) {} }
        rule.waitForIdle(); Thread.sleep(2000); rule.waitForIdle()
        rule.onRoot().captureRoboImage("$out/detail.png")
    }
}
