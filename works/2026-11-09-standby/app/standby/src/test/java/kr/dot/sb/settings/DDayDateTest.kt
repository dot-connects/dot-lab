package kr.dot.sb.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** plan-r13 C2: the D-day wheels keep the day inside the month when the month or year moves. */
class DDayDateTest {
    @Test fun dayKeptWhenItFits() = assertEquals(LocalDate.of(2026, 3, 15), ddayDate(2026, 3, 15))

    @Test fun jan31ToFebruaryIs28() = assertEquals(LocalDate.of(2026, 2, 28), ddayDate(2026, 2, 31))

    @Test fun leapYearKeepsFeb29() = assertEquals(LocalDate.of(2028, 2, 29), ddayDate(2028, 2, 31))

    @Test fun feb29ToCommonYearIs28() = assertEquals(LocalDate.of(2027, 2, 28), ddayDate(2027, 2, 29))

    @Test fun day31ToThirtyDayMonthIs30() = assertEquals(LocalDate.of(2026, 4, 30), ddayDate(2026, 4, 31))
}
