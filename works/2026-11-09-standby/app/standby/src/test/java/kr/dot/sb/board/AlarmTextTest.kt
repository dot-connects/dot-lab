package kr.dot.sb.board

import kr.dot.sb.model.QuickAlarm
import kr.dot.sb.ui.cards.alarmLine
import kr.dot.sb.ui.cards.alarmTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/** 11-14 #8 alarm card text: day word + weekday + time left, and the quick-alarm targets. */
class AlarmTextTest {
    private val now = LocalDateTime.of(2026, 11, 9, 21, 41) // Monday

    @Test fun tomorrowWithHoursAndMinutes() = assertEquals("내일 화요일 · 8시간 49분 뒤", alarmLine(now, LocalDateTime.of(2026, 11, 10, 6, 30)))
    @Test fun todayMinutesOnly() = assertEquals("오늘 월요일 · 19분 뒤", alarmLine(now, LocalDateTime.of(2026, 11, 9, 22, 0)))
    @Test fun wholeHoursDropTheMinutes() = assertEquals("오늘 월요일 · 2시간 뒤", alarmLine(now, LocalDateTime.of(2026, 11, 9, 23, 41)))
    @Test fun laterDaysShowTheDateAndDays() = assertEquals("11월 12일 목요일 · 2일 8시간 뒤", alarmLine(now, LocalDateTime.of(2026, 11, 12, 6, 0)))
    @Test fun underAMinute() = assertEquals("오늘 월요일 · 곧 울려요", alarmLine(now.withSecond(10), LocalDateTime.of(2026, 11, 9, 21, 41, 50)))
    @Test fun secondsRoundDown() = assertEquals("오늘 월요일 · 1분 뒤", alarmLine(now, LocalDateTime.of(2026, 11, 9, 21, 42, 59)))

    @Test fun bigTime12And24() {
        assertEquals("오전" to "6:30", alarmTime(LocalDateTime.of(2026, 11, 10, 6, 30), use24h = false))
        assertEquals("오후" to "12:05", alarmTime(LocalDateTime.of(2026, 11, 10, 12, 5), use24h = false))
        assertEquals(null to "06:30", alarmTime(LocalDateTime.of(2026, 11, 10, 6, 30), use24h = true))
    }

    @Test fun quickAlarmTargets() {
        assertEquals(LocalDateTime.of(2026, 11, 9, 22, 1), QuickAlarm(minutes = 20).target(now))
        assertEquals(LocalDateTime.of(2026, 11, 9, 22, 41), QuickAlarm(minutes = 60).target(now))
        // a clock time is its next occurrence: 7:00 at 21:41 is tomorrow, at 05:00 it is today
        assertEquals(LocalDateTime.of(2026, 11, 10, 7, 0), QuickAlarm(at = LocalTime.of(7, 0)).target(now))
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 0), QuickAlarm(at = LocalTime.of(7, 0)).target(now.withHour(5)))
    }

    @Test fun quickAlarmLabels() {
        assertEquals(listOf("+5분", "+30분", "+1시간"), QuickAlarm.PRESETS.map { it.label })
        assertEquals("+1시간 30분", QuickAlarm(minutes = 90).label)
    }

    @Test fun setTimeStartsOnTheNextHalfHour() {
        assertEquals(LocalTime.of(22, 0), QuickAlarm.nextHalfHour(now))
        assertEquals(LocalTime.of(22, 30), QuickAlarm.nextHalfHour(now.withHour(22).withMinute(0)))
        assertEquals(LocalTime.of(0, 0), QuickAlarm.nextHalfHour(now.withHour(23).withMinute(45)))
        // the set time is its next occurrence: 06:30 at 21:41 is tomorrow
        assertEquals(LocalDateTime.of(2026, 11, 10, 6, 30), QuickAlarm(at = LocalTime.of(6, 30)).target(now))
    }

    @Test fun wheelsWrapBothWays() {
        val start = kr.dot.sb.ui.cards.wheelStart(7, 24)
        assertEquals(7, kr.dot.sb.ui.cards.wheelValue(start, 24))
        assertEquals(23, kr.dot.sb.ui.cards.wheelValue(kr.dot.sb.ui.cards.wheelStart(0, 24) - 1, 24))   // above 00 is 23
        assertEquals(0, kr.dot.sb.ui.cards.wheelValue(kr.dot.sb.ui.cards.wheelStart(59, 60) + 1, 60))   // below 59 is 00
        assertEquals(59, kr.dot.sb.ui.cards.wheelValue(-1, 60))
    }
}
