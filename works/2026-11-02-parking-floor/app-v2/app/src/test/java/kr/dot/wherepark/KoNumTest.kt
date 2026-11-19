package kr.dot.wherepark

import kr.dot.wherepark.core.KoNum
import kr.dot.wherepark.core.Letter
import kr.dot.wherepark.core.Num
import org.junit.Assert.assertEquals
import org.junit.Test

class KoNumTest {
    private fun n(s: String) = KoNum.normalize(s)

    @Test fun sino() = assertEquals(listOf(Num(37, korean = true)), n("삼십칠"))
    @Test fun native() = assertEquals(listOf(Num(37, korean = true)), n("서른일곱"))
    @Test fun twenty() = assertEquals(listOf(Num(20, korean = true)), n("이십"))
    @Test fun digitsRead() = assertEquals(
        listOf(Num(3, korean = true, digitsRead = true), Num(7, korean = true, digitsRead = true)), n("삼칠"))
    @Test fun bTwo() = assertEquals(listOf(Letter('B', "비"), Num(2)), n("비투"))
    @Test fun syllableC() { assertEquals(listOf(Letter('C', "씨")), n("씨")); assertEquals(listOf(Letter('C', "시")), n("시")) }
    @Test fun letters() {
        assertEquals('D', (n("디").single() as Letter).c)
        assertEquals('F', (n("에프").single() as Letter).c)
        assertEquals('A', (n("에이").single() as Letter).c)
    }
    @Test fun lone이() = assertEquals(listOf(Num(2, korean = true, alt = 'E')), n("이"))
    @Test fun arabic() = assertEquals(listOf(Num(37)), n("37"))
    @Test fun latinJoined() = assertEquals(listOf(Letter('C'), Num(37)), n("c-37"))
}
