package kr.dot.sb.data

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.random.Random

enum class MemoryKind { TODAY, NEAR, ANY }

/** Date-only ranking; decoding and the bounded EXIF checks belong to PhotoIndex. */
object MemorySelection {
    /** Year of the anniversary of [day] nearest to [today] (across New Year). */
    private fun nearestYear(day: LocalDate, today: LocalDate): Int {
        val md = MonthDay.from(day)
        return (today.year - 1..today.year + 1).filter { md.isValidYear(it) }
            .minBy { abs(ChronoUnit.DAYS.between(today, md.atYear(it))) }
    }

    fun distance(day: LocalDate, today: LocalDate): Int =
        abs(ChronoUnit.DAYS.between(today, MonthDay.from(day).atYear(nearestYear(day, today)))).toInt()

    /** Years back, counted to the nearest anniversary: a 2025-12-30 photo seen on 2026-01-03 is 0 years (not a memory). */
    fun years(day: LocalDate, today: LocalDate): Int = nearestYear(day, today) - day.year

    fun kind(day: LocalDate, today: LocalDate): MemoryKind = when {
        years(day, today) !in 1..15 -> MemoryKind.ANY
        MonthDay.from(day) == MonthDay.from(today) -> MemoryKind.TODAY
        distance(day, today) <= 7 -> MemoryKind.NEAR
        else -> MemoryKind.ANY
    }

    /** Stable sorting after shuffling randomizes only ties; ANY also retries dated but unreadable candidates. */
    fun <T> candidates(items: List<T>, today: LocalDate, kind: MemoryKind, random: Random = Random.Default, date: (T) -> LocalDate): List<T> {
        val pool = if (kind == MemoryKind.ANY) items else items.filter { this.kind(date(it), today) == kind }
        return pool.shuffled(random).let { shuffled ->
            if (kind == MemoryKind.NEAR) shuffled.sortedBy { distance(date(it), today) } else shuffled
        }
    }
}
