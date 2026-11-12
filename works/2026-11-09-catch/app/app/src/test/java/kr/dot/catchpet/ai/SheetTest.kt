package kr.dot.catchpet.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun keyGreenClearsGreenKeepsWhiteAndSoftensTheEdge() {
        assertEquals(0, Sheet.keyGreen(argb(255, 0, 255, 0)) ushr 24)
        assertEquals(argb(255, 255, 255, 255), Sheet.keyGreen(argb(255, 255, 255, 255)))
        assertEquals(argb(255, 200, 120, 90), Sheet.keyGreen(argb(255, 200, 120, 90)))   // warm fur stays
        val edge = Sheet.keyGreen(argb(255, 100, 140, 90))   // k = 40
        val a = edge ushr 24; val g = (edge shr 8) and 255
        assertTrue("alpha $a", a in 1..254)
        assertTrue("green $g", g < 140)
        assertEquals(100, (edge shr 16) and 255)
    }
}
