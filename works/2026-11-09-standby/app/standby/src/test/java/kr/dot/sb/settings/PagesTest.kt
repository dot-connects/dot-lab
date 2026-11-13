package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.PAGE_PHOTOS
import kr.dot.sb.model.PAGE_WIDGETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagesTest {
    private val c = AppConfig() // widgets, photos, clock + modular, desk, music, bed, book

    @Test fun visibleKeepsTheFullOrder() {
        assertEquals(listOf("widgets", "photos", "clock", "modular", "desk", "music", "bed", "book"), Pages.visible(c).map { it.id })
        assertEquals((0..7).toList(), Pages.visible(c).map { it.index })
    }

    @Test fun offPagesLeaveThePagerButKeepTheirIndex() {
        val off = Pages.setOn(Pages.setOn(c, PageRef(1), false), Pages.all(c)[4], false) // photos + desk off
        assertEquals(listOf("widgets", "clock", "modular", "music", "bed", "book"), Pages.visible(off).map { it.id })
        assertEquals(listOf(0, 2, 3, 5, 6, 7), Pages.visible(off).map { it.index })
        assertEquals(setOf(PAGE_PHOTOS), off.pagesOff)
        assertEquals(c, Pages.setOn(Pages.setOn(off, PageRef(1), true), Pages.all(off)[4], true))
    }

    @Test fun theClockPageCannotBeSwitchedOff() {
        assertEquals(c, Pages.setOn(c, PageRef(Pages.CLOCK), false))
    }

    @Test fun startFollowsVisiblePagesAndFallsBackToTheClock() {
        assertEquals(3, Pages.start(c.copy(startPage = 3)))               // modular, everything on
        val noWidgets = c.copy(pagesOff = setOf(PAGE_WIDGETS))
        assertEquals(2, Pages.start(noWidgets.copy(startPage = 3)))       // one page fewer in front
        assertEquals(1, Pages.start(noWidgets.copy(startPage = 0)))       // start page is off -> clock
        assertEquals(2, Pages.start(c.copy(startPage = 42)))              // deleted -> clock
        assertNull(Pages.position(noWidgets, 0))
    }

    @Test fun deskIsNeverDeletedOtherBoardsAre() {
        val desk = c.boards.indexOfFirst { it.id == BoardOps.DESK }
        assertEquals(c, BoardOps.delete(c, desk))
        assertEquals(c.boards.size - 1, BoardOps.delete(c, 0).boards.size)
    }

    @Test fun deletedPresetsCanBeAddedAgain() {
        assertTrue(BoardOps.missingPresets(c).isEmpty())
        val gone = BoardOps.delete(c, 0)
        val again = BoardOps.missingPresets(gone).single()
        assertEquals("modular", again.id)
        assertEquals(c.boards.map { it.id }.toSet(), BoardOps.addPreset(gone, again).boards.map { it.id }.toSet())
    }
}
