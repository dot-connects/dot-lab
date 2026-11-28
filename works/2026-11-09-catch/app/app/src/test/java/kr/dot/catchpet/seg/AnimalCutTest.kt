package kr.dot.catchpet.seg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalCutTest {
    // a 40 x 40 crop that is also the whole touch frame
    private val n = 40
    private fun grid(f: (Int, Int) -> Float) = FloatArray(n * n) { f(it % n, it / n) }
    private fun cat(x: Int, y: Int) = x in 10..24 && y in 10..29
    private fun combine(touch: FloatArray, conf: FloatArray, seed: FloatArray = floatArrayOf(0.4f, 0.5f)) =
        AnimalCut.combine(touch, n, n, 0, 0, n, n, conf, n, n, seed)
    private fun at(a: FloatArray, x: Int, y: Int) = a[y * n + x]

    // kitten_9: DeepLab paints the hand next to the cat (0.6, under SURE); the touch takes the cat only
    @Test fun aHandDeepLabCallsCatIsDroppedWhenTheTouchLeavesItOut() {
        val touch = grid { x, y -> if (cat(x, y)) 1f else 0f }
        val conf = grid { x, y -> if (cat(x, y)) 0.9f else if (x in 25..34 && y in 15..22) 0.6f else 0f }
        val a = combine(touch, conf)!!
        assertEquals(1f, at(a, 17, 20), 1e-6f)
        assertEquals(0f, at(a, 30, 18), 1e-6f)
    }

    // src02 / src06: a sure blob on the sofa away from the cat is a different piece and goes
    @Test fun sofaBleedNotConnectedToTheSeedIsTrimmed() {
        val touch = grid { x, y -> if (cat(x, y)) 1f else 0f }
        val conf = grid { x, y -> if (x in 30..38 && y in 30..38) 0.95f else 0.5f }
        val a = combine(touch, conf)!!
        assertEquals(0f, at(a, 34, 34), 1e-6f)
        assertEquals(1f, at(a, 17, 20), 1e-6f)
    }

    // src05: one touch point took the body only; DeepLab's sure head joins it
    @Test fun sureDeepLabFillsWhatTheTouchMissed() {
        val touch = grid { x, y -> if (x in 10..24 && y in 16..29) 1f else 0f }
        val conf = grid { x, y -> if (x in 12..22 && y in 8..15) 0.9f else 0f }
        val a = combine(touch, conf)!!
        assertEquals(1f, at(a, 17, 11), 1e-6f)
    }

    @Test fun emptyOrBoxFillingOutputFallsBack() {
        assertNull(combine(FloatArray(n * n), FloatArray(n * n)))
        assertNull(combine(grid { _, _ -> 1f }, FloatArray(n * n)))
        assertNull(AnimalCut.combine(FloatArray(4), 2, 2, 0, 0, 2, 2, FloatArray(4), 2, 2, floatArrayOf(0.5f, 0.5f)))
    }

    @Test fun theSeedOffThePieceTakesTheNearestPiece() {
        val touch = grid { x, y -> if (cat(x, y)) 1f else 0f }
        val a = combine(touch, FloatArray(n * n), seed = floatArrayOf(0.9f, 0.1f))!!
        assertEquals(1f, at(a, 17, 20), 1e-6f)
    }

    @Test fun edgesAreSoft() {
        val a = combine(grid { x, y -> if (cat(x, y)) 1f else 0f }, FloatArray(n * n))!!
        assertTrue(at(a, 10, 20) in 0.1f..0.9f)
    }

    @Test fun seedUsesTheClawOnlyWhereDeepLabSeesTheAnimal() {
        val conf = grid { x, y -> if (cat(x, y)) 0.9f else 0f }
        val inner = floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f)
        val onCat = floatArrayOf(0.3f, 0.5f)
        assertTrue(AnimalCut.seed(conf, n, n, inner, onCat) === onCat)
        // claw on the cushion: the strongest cat point instead
        val s = AnimalCut.seed(conf, n, n, inner, floatArrayOf(0.75f, 0.75f))
        assertTrue(cat((s[0] * n).toInt(), (s[1] * n).toInt()))
        // nothing seen: the box centre
        val c = AnimalCut.seed(FloatArray(n * n), n, n, inner, null)
        assertEquals(0.5f, c[0], 1e-6f); assertEquals(0.5f, c[1], 1e-6f)
    }
}
