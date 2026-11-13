package kr.dot.sb.clock

import kr.dot.sb.ui.clock.hangulTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 한글 시계 words (plan-r10 Task 8): native hours in 12 h, sino hours in 24 h, sino minutes, "정각" on the hour. */
class HangulTimeTest {
    private fun t(h: Int, m: Int, use24: Boolean) = hangulTime(h, m, use24)

    @Test fun twelveHour() {
        assertEquals(Triple("오전", "열두 시", "정각"), t(0, 0, false))
        assertEquals(Triple("오전", "열두 시", "오 분"), t(0, 5, false))
        assertEquals(Triple("오전", "아홉 시", "사십일 분"), t(9, 41, false))
        assertEquals(Triple("오후", "열두 시", "정각"), t(12, 0, false))
        assertEquals(Triple("오후", "한 시", "삼십 분"), t(13, 30, false))
        assertEquals(Triple("오후", "열한 시", "오십구 분"), t(23, 59, false))
    }

    @Test fun twentyFourHour() {
        assertEquals(Triple(null, "영 시", "정각"), t(0, 0, true))
        assertEquals(Triple(null, "영 시", "오 분"), t(0, 5, true))
        assertEquals(Triple(null, "구 시", "사십일 분"), t(9, 41, true))
        assertEquals(Triple(null, "십이 시", "정각"), t(12, 0, true))
        assertEquals(Triple(null, "십삼 시", "삼십 분"), t(13, 30, true))
        assertEquals(Triple(null, "이십삼 시", "오십구 분"), t(23, 59, true))
    }

    @Test fun tensAndTeens() {
        assertEquals("십 분", t(1, 10, false).third)
        assertEquals("십일 분", t(1, 11, false).third)
        assertEquals("이십 분", t(1, 20, false).third)
        assertEquals("열 시", t(10, 0, false).second)
        assertEquals("두 시", t(14, 0, false).second)
        assertEquals("이십 시", t(20, 0, true).second)
    }
}
