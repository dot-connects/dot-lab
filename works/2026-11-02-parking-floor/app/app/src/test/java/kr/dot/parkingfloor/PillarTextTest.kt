package kr.dot.parkingfloor

import kr.dot.parkingfloor.PillarText.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PillarTextTest {
    @Test
    fun skipsTheFloorMarkAndKeepsTheCode() {
        assertEquals("C-12", PillarText.best(listOf(Block("B2", 900), Block("C-12", 400)), "B2"))
    }

    @Test
    fun readsHangulAndMissingHyphen() {
        assertEquals("가-07", PillarText.best(listOf(Block("가07", 300)), "B3"))
        assertEquals("D-3", PillarText.best(listOf(Block("d 3", 300)), null))
    }

    @Test
    fun biggerPaintWins() {
        assertEquals("A-5", PillarText.best(listOf(Block("A5", 900), Block("주차 N0 12 안내", 100)), "B1"))
    }

    @Test
    fun nothingCodeLikeIsNull() {
        assertNull(PillarText.best(listOf(Block("EXIT", 500), Block("B1", 400)), "B1"))
    }
}
