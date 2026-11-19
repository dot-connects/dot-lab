package kr.dot.wherepark

import kr.dot.wherepark.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ParkRulesTest {
    val z = ZoneId.of("Asia/Seoul")
    fun t(s: String) = java.time.LocalDateTime.parse(s).atZone(z).toInstant().toEpochMilli()
    fun p(id: Long, at: Long) = Park(id, "c_a", Spot(Floor.Under(2), "C", 37, null, null), at, "m", "아빠", null, false, ParkState.PARKED)
    @Test fun sameIdOverwrites() = assertTrue(ParkRules.accept(p(1, 200), p(1, 100)))
    @Test fun newerWins() = assertTrue(ParkRules.accept(p(1, 100), p(2, 200)))
    @Test fun olderLoses() = assertFalse(ParkRules.accept(p(2, 200), p(1, 100)))
    @Test fun today() = assertEquals("14:32", ParkRules.whenLabel(t("2026-11-19T14:32"), t("2026-11-19T15:00")))
    @Test fun yesterday() = assertEquals("어제 19:05", ParkRules.whenLabel(t("2026-11-18T19:05"), t("2026-11-19T09:00")))
    @Test fun older() = assertEquals("11월 16일", ParkRules.whenLabel(t("2026-11-16T14:32"), t("2026-11-19T15:00")))
}
