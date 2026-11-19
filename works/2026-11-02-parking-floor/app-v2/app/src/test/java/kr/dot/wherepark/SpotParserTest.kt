package kr.dot.wherepark

import kr.dot.wherepark.core.Ambiguity
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.SpotParser
import kr.dot.wherepark.core.label
import kr.dot.wherepark.core.wire
import kr.dot.wherepark.core.floorOfWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Rows 1..34 of docs/archive/2026-11-19-parking-voice-research/03-voice-widget-settings-ux.md (table = source of truth). */
@RunWith(Parameterized::class)
class SpotParserTest(
    val raw: String, val floor: String?, val zone: String?, val number: Int?,
    val pillar: String?, val memo: String?, val amb: Ambiguity,
) {
    companion object {
        private val N = Ambiguity.NONE
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0}")
        fun rows(): List<Array<Any?>> = listOf(
            arrayOf("지하 2층 C구역 37번", "B2", "C", 37, null, null, N),
            arrayOf("지하 이층 씨구역 삼십칠번", "B2", "C", 37, null, null, N),
            arrayOf("지하 이 층 씨 삼십칠", "B2", "C", 37, null, null, N),
            arrayOf("지하2층 C37", "B2", "C", 37, null, null, N),
            arrayOf("B2 C 37", "B2", "C", 37, null, null, N),
            arrayOf("비투 씨 37", "B2", "C", 37, null, null, N),
            arrayOf("비2 C-37", "B2", "C", 37, null, null, N),
            arrayOf("비원 에이 12", "B1", "A", 12, null, null, N),
            arrayOf("비쓰리", "B3", null, null, null, null, N),
            arrayOf("지하 일층", "B1", null, null, null, null, N),
            arrayOf("지하 3층 37번 C구역", "B3", "C", 37, null, null, N),
            arrayOf("C구역 37번 지하 2층", "B2", "C", 37, null, null, N),
            arrayOf("2층 C구역", "2F", "C", null, null, null, Ambiguity.FLOOR_SIGN),
            arrayOf("이층", "2F", null, null, null, null, Ambiguity.FLOOR_SIGN),
            arrayOf("지하 2층 시구역", "B2", "C", null, null, null, N),
            arrayOf("지하 2층 비구역", "B2", "B", null, null, null, N),
            arrayOf("지하 3층 이 구역", "B3", "E", null, null, null, Ambiguity.ZONE_E_OR_2),
            arrayOf("지하 3층 디구역 5번", "B3", "D", 5, null, null, N),
            arrayOf("지하 2층 에프 7", "B2", "F", 7, null, null, N),
            arrayOf("지하 2층 가구역 3번", "B2", "가", 3, null, null, N),
            arrayOf("기둥 가 3", null, null, null, "가-3", null, N),
            arrayOf("가-3 기둥 지하 1층", "B1", null, null, "가-3", null, N),
            arrayOf("가삼", null, "가", 3, null, null, Ambiguity.JOINED),
            arrayOf("지하 2층 삼칠번", "B2", null, 37, null, null, N),
            arrayOf("지하 2층 서른일곱 번", "B2", null, 37, null, null, N),
            arrayOf("지하 2층 C구역 37번 아니 38번", "B2", "C", 38, null, null, N),
            arrayOf("음 지하 2층이고 C구역이야", "B2", "C", null, null, null, N),
            arrayOf("지하 2층 엘리베이터 옆", "B2", null, null, null, "엘리베이터 옆", N),
            arrayOf("지하 4층 3번 출구 쪽", "B4", null, null, null, "3번 출구 쪽", N),
            arrayOf("지하 2층 파란 구역", "B2", "파랑", null, null, null, N),
            arrayOf("P2", "B2", null, null, null, null, Ambiguity.P_FLOOR),
            arrayOf("옥상", "옥상", null, null, null, null, N),
            arrayOf("지하 이십층", null, null, null, null, null, N),
            arrayOf("", null, null, null, null, null, N),
        )
    }

    @Test fun parses() {
        val r = SpotParser.parse(raw, listOf("흰 차", "회색 차")); val s = r.spot
        if (floor == null && zone == null && pillar == null && memo == null) { assertNull(s); return }
        assertEquals(floor, s?.floor?.label()); assertEquals(zone, s?.zone); assertEquals(number, s?.number)
        assertEquals(pillar, s?.pillar); assertEquals(memo, s?.memo); assertEquals(amb, r.ambiguity)
    }
}

class SpotParserTestExtras {
    @Test fun carName() {
        val r = SpotParser.parse("회색 차 지하 4층 A12", listOf("흰 차", "회색 차"))
        assertEquals("회색 차", r.carName)
        assertEquals("B4", r.spot?.floor?.label()); assertEquals("A", r.spot?.zone); assertEquals(12, r.spot?.number)
        assertNull(r.spot?.memo)
    }
    @Test fun floorSignOptions() {
        val r = SpotParser.parse("2층 C구역")
        assertEquals(listOf<Floor?>(Floor.Under(2), Floor.Above(2)), r.options.map { it.floor })
        assertEquals(Floor.Above(2), r.spot?.floor)
    }
    @Test fun zoneOptions() {
        val r = SpotParser.parse("지하 3층 이 구역")
        assertEquals(listOf<String?>("E", "2"), r.options.map { it.zone })
    }
    @Test fun pFloor() {
        val r = SpotParser.parse("P2")
        assertEquals(listOf<Floor?>(Floor.Under(2)), r.options.map { it.floor })
    }
    @Test fun floorWire() {
        assertEquals("B2", Floor.Under(2).wire()); assertEquals("2F", Floor.Above(2).wire()); assertEquals("ROOF", Floor.Roof.wire())
        assertEquals(Floor.Under(2), floorOfWire("B2")); assertEquals(Floor.Above(3), floorOfWire("3F"))
        assertEquals(Floor.Roof, floorOfWire("ROOF")); assertNull(floorOfWire("B0")); assertNull(floorOfWire(null)); assertNull(floorOfWire("x"))
        assertEquals("옥상", Floor.Roof.label())
    }
}
