package kr.dot.wherepark

import kr.dot.wherepark.core.Ask
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.SpotParser
import kr.dot.wherepark.core.chip
import kr.dot.wherepark.core.floorOfWire
import kr.dot.wherepark.core.label
import kr.dot.wherepark.core.restAfterFloor
import kr.dot.wherepark.core.wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The 60 rows of mockups/v5-listen/index.html, column "바뀐 뒤 (목표)": input -> floor chip, zone/number chip, sentence, ask. */
@RunWith(Parameterized::class)
class SpotParserTest(val no: Int, val raw: String, val floor: String?, val chip: String?, val sentence: String, val ask: Ask) {
    companion object {
        private val N = Ask.NONE
        private val BN = Ask.BASEMENT
        @JvmStatic @Parameterized.Parameters(name = "{0}: {1}")
        fun rows(): List<Array<Any?>> = listOf(
            arrayOf(1, "지하2층에 a랑b 나오자마자", "B2", null, "지하 2층, A랑 B 나오자마자", N),
            arrayOf(2, "h1 b타워 3층 계단 옆에 있어", "3층", null, "H1 B타워 3층 계단 옆", BN),
            arrayOf(3, "지하 2층 C구역 37번", "B2", "C 37", "지하 2층 C구역 37번", N),
            arrayOf(4, "비투 에이 십이", "B2", "A 12", "B2 A 12", N),
            arrayOf(5, "지하3층 엘리베이터 앞", "B3", null, "지하 3층 엘리베이터 앞", N),
            arrayOf(6, "B1 마트 입구 쪽", "B1", null, "B1 마트 입구 쪽", N),
            arrayOf(7, "지하 1층 하늘색 구역 기둥 옆", "B1", null, "지하 1층 하늘색 구역 기둥 옆", N),
            arrayOf(8, "2층 동문 출구 가까운 데", "2층", null, "2층 동문 출구 가까운 데", BN),
            arrayOf(9, "옥상 주차장 맨 끝", "옥상", null, "옥상 주차장 맨 끝", N),
            arrayOf(10, "지하4층 D 15 근데 기둥 뒤", "B4", "D 15", "지하 4층 D 15, 기둥 뒤", N),
            arrayOf(11, "b2 가 7", "B2", "가 7", "B2 가 7", N),
            arrayOf(12, "지하 2층 101동 앞", "B2", null, "지하 2층 101동 앞", N),
            arrayOf(13, "3번 출구 나와서 지하 1층 왼쪽", "B1", null, "3번 출구 나와서 지하 1층 왼쪽", N),
            arrayOf(14, "지하 2층 장애인 주차 옆 자리", "B2", null, "지하 2층 장애인 주차 옆 자리", N),
            arrayOf(15, "a동 지하 3층 엘리베이터 2호기 앞", "B3", null, "A동 지하 3층 엘리베이터 2호기 앞", N),
            arrayOf(16, "지하 1층 이마트 쪽 빨간 기둥", "B1", null, "지하 1층 이마트 쪽 빨간 기둥", N),
            arrayOf(17, "지하5층 E구역 이백십이번", "B5", "E 212", "지하 5층 E구역 212번", N),
            arrayOf(18, "1층 정문 경비실 옆", "1층", null, "1층 정문 경비실 옆", BN),
            arrayOf(19, "지하 2층 충전소 3번 자리", "B2", "3번", "지하 2층 충전소 3번 자리", N),
            arrayOf(20, "p3 블루존 22", "P3", "22", "P3 블루존 22", N),
            arrayOf(21, "지하 3층 아니 2층 B 14", "B2", "B 14", "지하 2층 B 14", N),
            arrayOf(22, "흰차 지하 1층 C 5", "B1", "C 5", "지하 1층 C 5", N),
            arrayOf(23, "지하 이층 에이 나오자마자 오른쪽", "B2", null, "지하 2층 A 나오자마자 오른쪽", N),
            arrayOf(24, "지하 2층 g7 기둥 앞에 댔어", "B2", "G7 기둥", "지하 2층 G7 기둥 앞", N),
            arrayOf(25, "에이치원 비타워 삼층 계단 옆", "3층", null, "H1 B타워 3층 계단 옆", BN),
            arrayOf(26, "지하 2층 3다시7 기둥", "B2", "3-7 기둥", "지하 2층 3-7 기둥", N),
            arrayOf(27, "비투 에이 다시 칠", "B2", "A-7", "B2 A-7", N),
            arrayOf(28, "지하 1층 십이 다시 삼 기둥 앞", "B1", "12-3 기둥", "지하 1층 12-3 기둥 앞", N),
            arrayOf(29, "B2 3-12", "B2", "3-12", "B2 3-12", N),
            arrayOf(30, "지하 3층 C-15 기둥 옆", "B3", "C-15 기둥", "지하 3층 C-15 기둥 옆", N),
            arrayOf(31, "2.5층 엘리베이터 앞", "2.5층", null, "2.5층 엘리베이터 앞", N),
            arrayOf(32, "이점오층 계단 쪽", "2.5층", null, "2.5층 계단 쪽", N),
            arrayOf(33, "지하 1.5층", "B1.5", null, "지하 1.5층", N),
            arrayOf(34, "M층 출구 옆", "M층", null, "M층 출구 옆", N),
            arrayOf(35, "중2층 화장실 앞", "중2층", null, "중2층 화장실 앞", N),
            arrayOf(36, "지상 3층 D 8", "3층", "D 8", "지상 3층 D 8", N),
            arrayOf(37, "제2주차장 지하 1층 A 5", "B1", "A 5", "제2주차장 지하 1층 A 5", N),
            arrayOf(38, "주차타워 5층", "5층", null, "주차타워 5층", BN),
            arrayOf(39, "L2 남쪽 출구", "L2", null, "L2 남쪽 출구", N),
            arrayOf(40, "G층 입구", "G층", null, "G층 입구", N),
            arrayOf(41, "지하 2층 E 17 아니다 E 18", "B2", "E 18", "지하 2층 E 18", N),
            arrayOf(42, "비원 비 이십삼", "B1", "B 23", "B1 B 23", N),
            arrayOf(43, "지하 이층 비구역 칠번", "B2", "B 7", "지하 2층 B구역 7번", N),
            arrayOf(44, "4층 사번 기둥", "4층", "4번 기둥", "4층 4번 기둥", BN),
            arrayOf(45, "지하 일층 일 다시 일", "B1", "1-1", "지하 1층 1-1", N),
            arrayOf(46, "C 37 지하 2층", "B2", "C 37", "C 37 지하 2층", N),
            arrayOf(47, "이 층 엘리베이터 옆", "2층", null, "2층 엘리베이터 옆", BN),
            arrayOf(48, "지 칠 기둥", null, "G7 기둥", "G7 기둥", N),
            arrayOf(49, "디 구", null, "D 9", "D 9", N),
            arrayOf(50, "지하 2층 이 구역 이 번", "B2", "E 2", "지하 2층 E구역 2번", Ask.ZONE_2),
            arrayOf(51, "세 번째 칸 지하 1층", "B1", null, "세 번째 칸 지하 1층", N),
            arrayOf(52, "1층이랑 2층 사이 계단", null, null, "1층이랑 2층 사이 계단", Ask.WHICH_FLOOR),
            arrayOf(53, "지하 2층 아니 3층 아니 2층", "B2", null, "지하 2층", N),
            arrayOf(54, "엘베 내려서 바로 앞", null, null, "엘베 내려서 바로 앞", N),
            arrayOf(55, "3시까지 무료 지하 1층", "B1", null, "3시까지 무료 지하 1층", N),
            arrayOf(56, "노랑 12", null, "노랑 12", "노랑 12", N),
            arrayOf(57, "에이 일공삼", null, "A 103", "A 103", N),
            arrayOf(58, "지하 주차장 2층", "B2", null, "지하 주차장 2층", N),
            arrayOf(59, "37번 칸 B2", "B2", "37번", "37번 칸 B2", N),
            arrayOf(60, "출구 2번 쪽 지하 1층", "B1", null, "출구 2번 쪽 지하 1층", N),
        )
    }

    @Test fun parses() {
        val r = SpotParser.parse(raw, listOf("흰 차", "회색 차"))
        val s = r.spot!!
        assertEquals("floor", floor, s.floor?.label())
        assertEquals("chip", chip, s.chip())
        assertEquals("sentence", sentence, s.memo)
        assertEquals("ask", ask, r.ask)
        if (no == 22) assertEquals("흰 차", r.carName)
    }
}

/** Cases from the 1.1.0 table that still hold (fields only; the sentence rule replaced the old leftover memo). */
@RunWith(Parameterized::class)
class SpotParserOldTest(val raw: String, val floor: String?, val zone: String?, val number: Int?, val pillar: String?) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0}")
        fun rows(): List<Array<Any?>> = listOf(
            arrayOf("지하 2층 C구역 37번", "B2", "C", 37, null),
            arrayOf("지하 이층 씨구역 삼십칠번", "B2", "C", 37, null),
            arrayOf("지하 이 층 씨 삼십칠", "B2", "C", 37, null),
            arrayOf("지하2층 C37", "B2", "C", 37, null),
            arrayOf("B2 C 37", "B2", "C", 37, null),
            arrayOf("비투 씨 37", "B2", "C", 37, null),
            arrayOf("비2 C-37", "B2", "C", 37, null),
            arrayOf("비원 에이 12", "B1", "A", 12, null),
            arrayOf("비쓰리", "B3", null, null, null),
            arrayOf("지하 일층", "B1", null, null, null),
            arrayOf("지하 3층 37번 C구역", "B3", "C", 37, null),
            arrayOf("C구역 37번 지하 2층", "B2", "C", 37, null),
            arrayOf("2층 C구역", "2층", "C", null, null),
            arrayOf("이층", "2층", null, null, null),
            arrayOf("지하 2층 시구역", "B2", "C", null, null),
            arrayOf("지하 2층 비구역", "B2", "B", null, null),
            arrayOf("지하 3층 이 구역", "B3", "E", null, null),
            arrayOf("지하 3층 디구역 5번", "B3", "D", 5, null),
            arrayOf("지하 2층 에프 7", "B2", "F", 7, null),
            arrayOf("지하 2층 가구역 3번", "B2", "가", 3, null),
            arrayOf("기둥 가 3", null, null, null, "가3"),
            arrayOf("가-3 기둥 지하 1층", "B1", null, null, "가-3"),
            arrayOf("지하 2층 삼칠번", "B2", null, 37, null),
            arrayOf("지하 2층 서른일곱 번", "B2", null, 37, null),
            arrayOf("지하 2층 C구역 37번 아니 38번", "B2", "C", 38, null),
            arrayOf("음 지하 2층이고 C구역이야", "B2", "C", null, null),
            arrayOf("지하 4층 3번 출구 쪽", "B4", null, null, null),
            arrayOf("지하 2층 파란 구역", "B2", "파랑", null, null),
            arrayOf("옥상", "옥상", null, null, null),
            arrayOf("지하 이십층", null, null, null, null),
        )
    }

    @Test fun parses() {
        val s = SpotParser.parse(raw).spot!!
        assertEquals(floor, s.floor?.label()); assertEquals(zone, s.zone); assertEquals(number, s.number); assertEquals(pillar, s.pillar)
    }
}

class SpotParserTestExtras {
    @Test fun empty() = assertNull(SpotParser.parse("  ").spot)

    @Test fun carName() {
        val r = SpotParser.parse("회색 차 지하 4층 A12", listOf("흰 차", "회색 차"))
        assertEquals("회색 차", r.carName)
        assertEquals("B4", r.spot?.floor?.label()); assertEquals("A", r.spot?.zone); assertEquals(12, r.spot?.number)
        assertEquals("지하 4층 A12", r.spot?.memo)
    }

    @Test fun askOptions() {
        assertEquals(listOf<Floor?>(Floor.Under(2)), SpotParser.parse("2층 C구역").options.map { it.floor })
        assertEquals(listOf<String?>("2"), SpotParser.parse("지하 3층 이 구역").options.map { it.zone })
        assertEquals(listOf<Floor?>(Floor.Above(1), Floor.Above(2)), SpotParser.parse("1층이랑 2층 사이 계단").options.map { it.floor })
        assertEquals(emptyList<Spot>(), SpotParser.parse("지하 2층").options)
    }

    @Test fun memoCappedAt80() = assertEquals(80, SpotParser.parse("지하 2층 " + "엘리베이터 ".repeat(20)).spot!!.memo!!.length)

    @Test fun floorWire() {
        val all = listOf(Floor.Under(2), Floor.Under(1, half = true), Floor.Above(2), Floor.Above(2, half = true), Floor.Roof,
            Floor.Named("M"), Floor.Named("MZ2"), Floor.Named("G"), Floor.Named("L2"), Floor.Named("P3"))
        assertEquals(listOf("B2", "B1.5", "2F", "2.5F", "ROOF", "M", "MZ2", "G", "L2", "P3"), all.map { it.wire() })
        assertEquals(listOf("B2", "B1.5", "2층", "2.5층", "옥상", "M층", "중2층", "G층", "L2", "P3"), all.map { it.label() })
        all.forEach { assertEquals(it, floorOfWire(it.wire())) }
        listOf("B0", "B10", "0F", "M2", "MZ3", "L0", "P", "x", "B1.6", "roof").forEach { assertNull(it, floorOfWire(it)) }
        assertNull(floorOfWire(null))
    }

    @Test fun restAfterFloor() {
        assertEquals("A랑 B 나오자마자", Spot(Floor.Under(2), null, null, null, "지하 2층, A랑 B 나오자마자").restAfterFloor())
        assertEquals("H1 B타워 계단 옆", Spot(Floor.Above(3), null, null, null, "H1 B타워 3층 계단 옆").restAfterFloor())
        assertEquals("주차장 맨 끝", Spot(Floor.Roof, null, null, null, "옥상 주차장 맨 끝").restAfterFloor())
        assertEquals("C 37", Spot(Floor.Under(2), "C", 37, null, "C 37 지하 2층").restAfterFloor())
        assertEquals("엘리베이터 옆", Spot(Floor.Under(2), "C", 37, null, "엘리베이터 옆").restAfterFloor())   // pre-1.1.1 memo
        assertEquals("", Spot(Floor.Under(2), null, null, null, "B2").restAfterFloor())
    }
}
