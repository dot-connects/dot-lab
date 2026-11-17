package kr.dot.catchpet.seg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelsTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255

    // 11-17: a transparent cut-out made the image model paint a smeared background instead of the green key
    @Test fun onWhiteMakesEveryPixelOpaqueOverWhite() {
        val out = Pixels.onWhite(intArrayOf(argb(0, 0, 0, 0), argb(255, 10, 20, 30), argb(128, 255, 0, 0)))
        assertEquals(argb(255, 255, 255, 255), out[0])
        assertEquals(argb(255, 10, 20, 30), out[1])
        assertEquals(255, out[2] ushr 24)
        assertEquals(255, r(out[2]))
        assertTrue("half red over white is pink, got ${g(out[2])}", g(out[2]) in 120..135)
    }

    @Test fun fillTakesTheHoleColourFromAroundItNotFromInsideIt() {
        // 9 x 9: green wall all round, a dark cat in the middle 5 x 5 (unknown)
        val w = 9; val h = 9
        val wall = argb(255, 40, 200, 60); val cat = argb(255, 20, 20, 20)
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 2..6 && y in 2..6) cat else wall }
        val known = FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 2..6 && y in 2..6) 0f else 1f }
        val out = Pixels.fill(px, known, w, h)
        val mid = out[4 * w + 4]
        assertEquals(255, mid ushr 24)
        assertTrue("centre takes the wall, got ${r(mid)},${g(mid)},${b(mid)}", abs(g(mid) - 200) <= 2 && abs(r(mid) - 40) <= 2)
        assertEquals(wall, out[0])   // known pixels stay as they were
    }

    @Test fun fillBlendsTwoSidesAcrossTheHole() {
        // left half red, right half blue, the middle column unknown: it ends up between the two
        val w = 5; val h = 1
        val red = argb(255, 255, 0, 0); val blue = argb(255, 0, 0, 255)
        val px = intArrayOf(red, red, 0, blue, blue)
        val known = floatArrayOf(1f, 1f, 0f, 1f, 1f)
        val mid = Pixels.fill(px, known, w, h)[2]
        assertTrue("purple-ish, got ${r(mid)},${b(mid)}", r(mid) in 100..155 && b(mid) in 100..155)
    }

    @Test fun fillWithNothingKnownKeepsThePixels() {
        val px = intArrayOf(argb(255, 1, 2, 3), argb(255, 4, 5, 6))
        assertEquals(px.toList(), Pixels.fill(px, floatArrayOf(0f, 0f), 2, 1).toList())
    }
}
