package kr.dot.sb.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** All-day calendar instances: yesterday's (END = today 00:00 UTC) must not take today's agenda rows. */
class AgendaAllDayTest {
    private val today = LocalDate.of(2026, 11, 23)
    private fun utcMidnight(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun yesterdaysAllDayIsOver() = assertFalse(Agenda.allDayLive(utcMidnight(today), today))

    @Test fun todaysAllDayIsLive() = assertTrue(Agenda.allDayLive(utcMidnight(today.plusDays(1)), today))

    @Test fun multiDayRunningThroughTodayIsLive() = assertTrue(Agenda.allDayLive(utcMidnight(today.plusDays(3)), today))

    // Samsung-style local-midnight storage (KST = UTC+9): today's all-day row runs 22 15:00Z..23 15:00Z
    private fun kstMidnight(d: LocalDate) = d.atStartOfDay(java.time.ZoneOffset.ofHours(9)).toInstant().toEpochMilli()

    @Test fun localStoredTodayIsLive() = assertTrue(Agenda.allDayLive(kstMidnight(today.plusDays(1)), today))

    @Test fun localStoredYesterdayIsOver() = assertFalse(Agenda.allDayLive(kstMidnight(today), today))

    @Test fun localStoredDateIsToday() = org.junit.Assert.assertEquals(today, Agenda.allDayDate(kstMidnight(today)))

    @Test fun utcStoredDateIsToday() = org.junit.Assert.assertEquals(today, Agenda.allDayDate(utcMidnight(today)))
}
