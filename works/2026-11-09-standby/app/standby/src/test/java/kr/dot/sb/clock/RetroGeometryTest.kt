package kr.dot.sb.clock

import kr.dot.sb.ui.clock.DotGlyphs
import kr.dot.sb.ui.clock.SevenSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RetroGeometryTest {
    @Test fun everyDigitHasBothGeometries() {
        for (digit in '0'..'9') {
            assertNotNull(SevenSegments[digit])
            assertNotNull(DotGlyphs[digit])
            assertEquals(7, DotGlyphs.getValue(digit).size)
        }
        assertEquals("abcdefg".toSet(), SevenSegments.getValue('8').toSet())
        assertEquals(setOf('b', 'c'), SevenSegments.getValue('1').toSet())
    }
}
