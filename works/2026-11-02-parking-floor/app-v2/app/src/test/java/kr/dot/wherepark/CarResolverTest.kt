package kr.dot.wherepark

import kr.dot.wherepark.core.*
import org.junit.Assert.*
import org.junit.Test

class CarResolverTest {
    val a = Car("c_a", "흰 차", 0, null, 0); val b = Car("c_b", "회색 차", 0, null, 1)
    @Test fun explicitWins() = assertEquals("c_b", CarResolver.resolve("c_b", "c_a", "흰 차", listOf(a, b), "c_a")?.id)
    @Test fun btNext() = assertEquals("c_a", CarResolver.resolve(null, "c_a", "회색 차", listOf(a, b), "c_b")?.id)
    @Test fun spokenNext() = assertEquals("c_b", CarResolver.resolve(null, null, "회색 차", listOf(a, b), "c_a")?.id)
    @Test fun lastUsed() = assertEquals("c_b", CarResolver.resolve(null, null, null, listOf(a, b), "c_b")?.id)
    @Test fun firstByOrder() = assertEquals("c_a", CarResolver.resolve(null, null, null, listOf(b, a), null)?.id)
    @Test fun unknownIdFallsThrough() = assertEquals("c_a", CarResolver.resolve("c_gone", null, null, listOf(a, b), null)?.id)
    @Test fun none() = assertNull(CarResolver.resolve(null, null, null, emptyList(), null))
}
