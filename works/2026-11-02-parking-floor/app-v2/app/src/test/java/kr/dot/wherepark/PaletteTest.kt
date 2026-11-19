package kr.dot.wherepark

import kr.dot.wherepark.core.*
import org.junit.Assert.*
import org.junit.Test

class PaletteTest {
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
