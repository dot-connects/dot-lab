package kr.dot.sb.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r11 A5 사진에서 색 뽑기: median cut on synthetic pixel arrays (no Android needed). */
class PhotoPaletteTest {
    private fun px(vararg parts: Pair<Int, Long>): IntArray = parts.flatMap { (n, c) -> List(n) { c.toInt() } }.toIntArray()
    private fun near(a: Long, b: Color, tol: Float = 0.04f) =
        Color(a).let { kotlin.math.abs(it.red - b.red) < tol && kotlin.math.abs(it.green - b.green) < tol && kotlin.math.abs(it.blue - b.blue) < tol }

    @Test fun darkMutedVividAndLightAccent() {
        val p = PhotoPalette.pick(px(600 to 0xFF141A22, 300 to 0xFFE07020, 100 to 0xFFFF9FC8))
        assertTrue("bg = the dark muted box", near(p.bg, Color(0xFF141A22)))
        assertTrue("vivid = the orange", near(p.vivid!!, Color(0xFFE07020)))
        assertTrue("accent = the light saturated pink", near(p.accent!!, Color(0xFFFF9FC8)))
        assertTrue("lightest = the pink", near(p.lightest, Color(0xFFFF9FC8)))
    }

    @Test fun greyPhotoHasNoAccent() {
        val p = PhotoPalette.pick(px(500 to 0xFF202020, 500 to 0xFF9A9A9A))
        assertTrue(near(p.bg, Color(0xFF202020)))
        assertNull(p.accent)
    }

    @Test fun allSaturatedFallsBackToTheDarkestForBg() {
        val p = PhotoPalette.pick(px(500 to 0xFF0030C0, 500 to 0xFFC03000))
        assertTrue(near(p.bg, Color(0xFF0030C0)))
        assertNotNull(p.vivid)
    }

    @Test fun emptyAndTransparentPixelsNeverThrow() {
        PhotoPalette.pick(IntArray(0))
        val p = PhotoPalette.pick(px(10 to 0x00FFFFFF, 3 to 0xFF101010))
        assertEquals(Color(0xFF101010).red, Color(p.bg).red, 0.01f)
    }

    @Test fun eightBoxesAtMost() {
        val many = IntArray(4096) { i -> (0xFF000000 or ((i * 2654435761L) and 0xFFFFFF)).toInt() }
        assertTrue(PhotoPalette.boxes(many).size <= 8)
    }
}
