package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardOpsTest {
    private val c = AppConfig() // 7 preset boards, pages 3..9

    @Test fun deleteKeepsStartOnTheSameBoard() {
        val ids = c.boards.map { it.id }
        val after = BoardOps.delete(c.copy(startPage = 6), 1) // start = 4th board, delete the 2nd
        assertEquals(5, after.startPage)
        assertEquals(ids[3], after.boards[after.startPage - 3].id)
    }

    @Test fun deletingTheStartBoardFallsBackToClocks() {
        assertEquals(2, BoardOps.delete(c.copy(startPage = 4), 1).startPage)
    }

    @Test fun moveFollowsTheStartBoard() {
        val id = c.boards[2].id
        val after = BoardOps.move(c.copy(startPage = 5), 2, -1)
        assertEquals(id, after.boards[after.startPage - 3].id)
        assertEquals(c, BoardOps.move(c, 0, -1)) // edge: no-op
    }

    @Test fun duplicateShiftsLaterStart() {
        assertEquals(7, BoardOps.duplicate(c.copy(startPage = 6), 0).startPage)
        assertEquals(c.boards.size + 1, BoardOps.duplicate(c, 0).boards.map { it.id }.toSet().size)
    }

    @Test fun relayoutKeepsCardsAndPads() {
        val b = c.boards.first { it.layout == Layout.FOUR }
        val smaller = BoardOps.withLayout(b, Layout.TWO)
        assertEquals(b.slots.take(2), smaller.slots)
        assertEquals(4, BoardOps.withLayout(smaller, Layout.FOUR).slots.size)
    }

    @Test fun flipCoverDisablesThreeColumnsOnly() {
        assertTrue(LayoutGeo.disabled(Layout.THREE, 400f, 362f))
        assertFalse(LayoutGeo.disabled(Layout.FOUR, 400f, 362f))
        assertFalse(LayoutGeo.disabled(Layout.THREE, 932f, 704f))
    }
}
