package kr.dot.sb.board

import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.board.BoardGrid
import org.junit.Assert.assertEquals
import org.junit.Test

/** design-system §3.3 slot table (Fold cover) and the §5.1 hero rule. */
class BoardGridTest {
    private fun sizes(l: Layout, w: Float, h: Float) =
        BoardGrid.slots(l, w, h).map { Math.round(it.w) to Math.round(it.h) }

    @Test fun foldCoverLandscape() {
        assertEquals(listOf(695 to 419), sizes(Layout.ONE, 751f, 475f))
        assertEquals(listOf(338 to 419, 338 to 419), sizes(Layout.TWO, 751f, 475f))
        assertEquals(listOf(405 to 419, 270 to 200, 270 to 200), sizes(Layout.BIG2, 751f, 475f))
        assertEquals(listOf(270 to 200, 270 to 200, 405 to 419), sizes(Layout.TWO2, 751f, 475f))
        assertEquals(List(3) { 218 to 419 }, sizes(Layout.THREE, 751f, 475f))
    }

    @Test fun foldCoverPortraitTransposesAndCentresTheCameraHole() {
        val s = BoardGrid.slots(Layout.BIG2, 475f, 751f, cutY = 40f)
        assertEquals(48f, s[0].y, 0.01f)
        assertEquals(751f - 48f, s[2].y + s[2].h, 0.01f) // bottom margin mirrors the top
        assertEquals(listOf(419 to 381, 200 to 254, 200 to 254), s.map { Math.round(it.w) to Math.round(it.h) })
        assertEquals(s[1].y, s[2].y, 0.01f) // small slots side by side under the big one
        val four = BoardGrid.slots(Layout.FOUR, 360f, 780f)
        assertEquals(four[0].y, four[1].y, 0.01f) // reading order survives the transpose
        assertEquals(true, four[1].x > four[0].x && four[2].y > four[0].y)
    }

    @Test fun heroPrefersTheLargestClockLikeThenARealClock() {
        val four = BoardGrid.slots(Layout.FOUR, 751f, 475f)
        assertEquals(1, BoardGrid.hero(four, listOf(CardType.DDAY, CardType.CLOCK, CardType.CALENDAR, CardType.AGENDA)))
        val big2 = BoardGrid.slots(Layout.BIG2, 751f, 475f)
        assertEquals(0, BoardGrid.hero(big2, listOf(CardType.CLOCK, CardType.TIMER, CardType.USAGE)))
        assertEquals(-1, BoardGrid.hero(big2, listOf(CardType.MEMORIES, CardType.WEATHER, CardType.CALENDAR)))
    }
}
