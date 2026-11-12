package kr.dot.catchpet.ai

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Cuts the two real AI pose sheets made on 2026-11-12 (out/ai/sheet) and checks every cell holds the creature. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetRealTest {
    private fun sheet(name: String) =
        // Gradle runs unit tests from the module dir (app/app); the sheets live in works/2026-11-09-catch/out/ai/sheet
        File("../../out/ai/sheet/$name.png").also { assertTrue("missing ${it.absolutePath}", it.isFile) }

    @Test fun realSheetsGiveSixFilledCells() {
        for (name in listOf("cat", "dog")) {
            val bmp = BitmapFactory.decodeFile(sheet(name).path)
            val cells = Sheet.cells(bmp)
            assertEquals(setOf("front", "smile", "walkA", "walkB", "sleep", "curl"), cells.keys)
            for ((k, c) in cells) {
                val px = IntArray(c.width * c.height).also { c.getPixels(it, 0, c.width, 0, 0, c.width, c.height) }
                val opaque = px.count { (it ushr 24) > 200 }.toFloat() / px.size
                val green = px.count { (it ushr 24) > 200 && ((it shr 8) and 255) > 200 && ((it shr 16) and 255) < 90 }
                assertTrue("$name/$k opaque share $opaque", opaque in 0.15f..0.95f)
                assertEquals("$name/$k keeps pure green pixels", 0, green)
                // the crop keeps a margin: the outer rows/columns stay (mostly) transparent, so no limb is cut off
                val edge = (0 until c.width).count { (px[it] ushr 24) > 200 } + (0 until c.width).count { (px[(c.height - 1) * c.width + it] ushr 24) > 200 }
                assertTrue("$name/$k touches the crop edge: $edge px", edge < c.width / 4)
            }
        }
    }
}
