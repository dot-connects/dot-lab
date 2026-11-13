package kr.dot.sb.settings

import kr.dot.sb.model.FaceId.ANALOG
import kr.dot.sb.model.FaceId.DIGITAL
import kr.dot.sb.model.FaceId.FLIP
import kr.dot.sb.model.FaceId.HANGUL
import org.junit.Assert.assertEquals
import org.junit.Test

class MyClocksTest {
    private val my = listOf(DIGITAL, FLIP, HANGUL)

    @Test fun addAppendsOnceOnly() {
        assertEquals(my + ANALOG, MyClocks.add(my, ANALOG))
        assertEquals(my, MyClocks.add(my, FLIP))
    }

    @Test fun removeKeepsTheLastClock() {
        assertEquals(listOf(DIGITAL, HANGUL), MyClocks.remove(my, FLIP))
        assertEquals(my, MyClocks.remove(my, ANALOG))
        assertEquals(listOf(FLIP), MyClocks.remove(listOf(FLIP), FLIP))
    }

    @Test fun moveClampsTheTarget() {
        assertEquals(listOf(FLIP, HANGUL, DIGITAL), MyClocks.move(my, 0, 2))
        assertEquals(listOf(HANGUL, DIGITAL, FLIP), MyClocks.move(my, 2, -5))
        assertEquals(listOf(FLIP, HANGUL, DIGITAL), MyClocks.move(my, 0, 99))
        assertEquals(my, MyClocks.move(my, 7, 0))
    }

    @Test fun pageFollowsTheFaceElseClampsTheIndex() {
        assertEquals(2, MyClocks.page(my, HANGUL, 0))
        assertEquals(1, MyClocks.page(listOf(DIGITAL, HANGUL), FLIP, 1))   // shown face removed: stay on that slot
        assertEquals(1, MyClocks.page(listOf(DIGITAL, HANGUL), FLIP, 2))   // ... clamped when the list got shorter
        assertEquals(0, MyClocks.page(my, null, 0))
    }
}
