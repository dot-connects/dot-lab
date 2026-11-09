package kr.dot.sb.settings

import kr.dot.sb.model.City

/** Short built-in city list (weather location and world clock). No geocoding call: lat/lon are fixed here. */
object Cities {
    val KOREA = listOf(
        City("서울", 37.5665, 126.9780), City("부산", 35.1796, 129.0756), City("대구", 35.8714, 128.6014),
        City("인천", 37.4563, 126.7052), City("광주", 35.1595, 126.8526), City("대전", 36.3504, 127.3845),
        City("울산", 35.5384, 129.3114), City("세종", 36.4800, 127.2890), City("수원", 37.2636, 127.0286),
        City("성남", 37.4200, 127.1265), City("고양", 37.6584, 126.8320), City("용인", 37.2411, 127.1776),
        City("창원", 35.2272, 128.6811), City("청주", 36.6424, 127.4890), City("전주", 35.8242, 127.1480),
        City("천안", 36.8151, 127.1139), City("포항", 36.0190, 129.3435), City("제주", 33.4996, 126.5312),
        City("춘천", 37.8813, 127.7298), City("강릉", 37.7519, 128.8761), City("원주", 37.3422, 127.9202),
        City("목포", 34.8118, 126.3922), City("여수", 34.7604, 127.6622), City("안동", 36.5684, 128.7294),
        City("김해", 35.2285, 128.8890), City("평택", 36.9921, 127.1129), City("속초", 38.2070, 128.5918),
        City("경주", 35.8562, 129.2247), City("군산", 35.9676, 126.7369),
    )
    val WORLD = listOf(
        City("도쿄", 35.6762, 139.6503), City("베이징", 39.9042, 116.4074), City("싱가포르", 1.3521, 103.8198),
        City("방콕", 13.7563, 100.5018), City("두바이", 25.2048, 55.2708), City("파리", 48.8566, 2.3522),
        City("런던", 51.5074, -0.1278), City("뉴욕", 40.7128, -74.0060), City("로스앤젤레스", 34.0522, -118.2437),
        City("호놀룰루", 21.3069, -157.8583), City("시드니", -33.8688, 151.2093),
    )
    val ALL = KOREA + WORLD

    /** Empty query shows a short suggestion list; otherwise names containing the query. */
    fun search(query: String, limit: Int = 8): List<City> {
        val q = query.trim()
        return if (q.isEmpty()) (KOREA.take(5) + WORLD.take(3)).take(limit) else ALL.filter { it.name.contains(q) }.take(limit)
    }
}
