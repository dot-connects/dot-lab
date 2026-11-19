package kr.dot.sb.data

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySelectionTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun kindsByDateAndYearRange() {
        assertEquals(MemoryKind.TODAY, MemorySelection.kind(LocalDate.of(2023, 11, 18), today))
        assertEquals(MemoryKind.NEAR, MemorySelection.kind(LocalDate.of(2020, 11, 25), today))
        assertEquals(MemoryKind.NEAR, MemorySelection.kind(LocalDate.of(2020, 11, 11), today))
        assertEquals(MemoryKind.ANY, MemorySelection.kind(LocalDate.of(2020, 11, 26), today)) // 8 days off
        assertEquals(MemoryKind.ANY, MemorySelection.kind(LocalDate.of(2026, 11, 18), today)) // this year: not a memory
        assertEquals(MemoryKind.ANY, MemorySelection.kind(LocalDate.of(2010, 11, 18), today)) // 16 years ago
    }

    @Test fun nearWrapsTheYearEnd() {
        val jan2 = LocalDate.of(2027, 1, 2)
        assertEquals(MemoryKind.NEAR, MemorySelection.kind(LocalDate.of(2022, 12, 29), jan2))
    }

    @Test fun yearsCountToTheNearestAnniversary() {
        val jan3 = LocalDate.of(2026, 1, 3)
        assertEquals(MemoryKind.ANY, MemorySelection.kind(LocalDate.of(2025, 12, 30), jan3)) // 4 days ago, not a memory
        assertEquals(1, MemorySelection.years(LocalDate.of(2024, 12, 30), jan3))
        assertEquals(2, MemorySelection.years(LocalDate.of(2024, 1, 2), LocalDate.of(2025, 12, 29)))
    }

    @Test fun nearestNearDayComesFirst() {
        val days = listOf(LocalDate.of(2021, 11, 24), LocalDate.of(2022, 11, 19), LocalDate.of(2019, 11, 13))
        val ranked = MemorySelection.candidates(days, today, MemoryKind.NEAR, Random(1)) { it }
        assertEquals(LocalDate.of(2022, 11, 19), ranked.first())
        assertEquals(3, ranked.size)
    }

    @Test fun todayPoolHoldsOnlyTheSameDay() {
        val days = listOf(LocalDate.of(2021, 11, 18), LocalDate.of(2022, 11, 19), LocalDate.of(2024, 3, 1))
        assertEquals(listOf(LocalDate.of(2021, 11, 18)), MemorySelection.candidates(days, today, MemoryKind.TODAY, Random(1)) { it })
        assertEquals(3, MemorySelection.candidates(days, today, MemoryKind.ANY, Random(1)) { it }.size)
    }
}
