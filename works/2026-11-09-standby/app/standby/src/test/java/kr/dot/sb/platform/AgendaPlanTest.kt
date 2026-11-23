package kr.dot.sb.platform

import java.time.LocalDate
import kr.dot.sb.ui.cards.planAgenda
import kr.dot.sb.ui.state.AgendaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaPlanTest {
    private val d = LocalDate.of(2026, 11, 23)
    private fun t(n: String, done: Boolean = false) = AgendaItem("09:00", n, 0, d, done = done)
    private fun u(n: String) = AgendaItem("08:00", n, 0, d.plusDays(1))

    @Test fun noTodayMeansUpcomingOnly() {
        val p = planAgenda(listOf(u("a"), u("b"), u("c")), d, 2.9f, true)
        assertEquals(emptyList<AgendaItem>(), p.today)
        assertEquals(listOf("a", "b"), p.up.map { it.title })
    }

    @Test fun roomyCardShowsBothSections() {
        val p = planAgenda(listOf(t("x"), t("y"), u("a"), u("b")), d, 7f, true)
        assertEquals(listOf("x", "y"), p.today.map { it.title })
        assertEquals(listOf("a", "b"), p.up.map { it.title })
        assertEquals(0, p.more)
    }

    @Test fun doneRowsGoFirstThenUpcomingKeepsOneRow() {
        val items = listOf(t("d", done = true), t("x"), t("y"), t("z"), u("a"), u("b"))
        val p = planAgenda(items, d, 6f, true)
        assertEquals(listOf("x", "y", "z"), p.today.map { it.title })
        assertEquals(1, p.up.size)
    }

    @Test fun liveRowsFoldIntoMoreWhenTooTight() {
        val items = (1..6).map { t("e$it") } + u("a")
        val p = planAgenda(items, d, 3f, true)
        assertEquals(1, p.today.size)
        assertEquals(5, p.more)
        assertEquals(0, p.up.size)
    }
}
