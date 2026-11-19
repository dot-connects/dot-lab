package kr.dot.wherepark

import kr.dot.wherepark.core.*
import org.junit.Assert.*
import org.junit.Test

class PaletteTest {
    /** Widgets fall back to the defaults, so they must pass the same checks as a shuffle (DOT draws its own mic, no accent). */
    @Test fun defaultsReadable() {
        for (s in Shape.values()) {
            val st = Palette.default(s)
            assertTrue("$s fg", Contrast.ratio(st.fg, st.bg) >= 4.5); assertTrue("$s mu", Contrast.ratio(st.mu, st.bg) >= 4.5)
            assertTrue("$s fl1", Contrast.ratio(0xFFFFFFFF.toInt(), st.fl1) >= 4.5); assertTrue("$s fl2", Contrast.ratio(0xFFFFFFFF.toInt(), st.fl2) >= 4.5)
            if (s != Shape.DOT) assertTrue("$s acc", Contrast.ratio(st.acc, st.bg) >= (if (s == Shape.POSTER) 3.0 else 1.6))
        }
    }

    @Test fun shuffleReadableAndVaried() {
        val r = java.util.Random(7)
        for (s in Shape.values()) {
            var prev: Style? = null
            repeat(200) {
                val st = Palette.shuffle(s, prev, r)
                assertTrue(Contrast.ratio(st.fg, st.bg) >= 4.5); assertTrue(Contrast.ratio(st.mu, st.bg) >= 3.0)
                assertTrue(Contrast.ratio(0xFFFFFFFF.toInt(), st.fl1) >= 4.5); assertTrue(Contrast.ratio(0xFFFFFFFF.toInt(), st.fl2) >= 4.5)
                assertTrue(Contrast.ratio(st.acc, st.bg) >= 1.6)
                if (s == Shape.DOT) assertEquals("Doto", st.font) else {
                    assertNotEquals("Doto", st.font)
                    prev?.let { assertNotEquals(it.font, st.font); assertNotEquals(it.material, st.material); assertNotEquals(it.family, st.family) }
                }
                prev = st
            }
        }
    }
}
