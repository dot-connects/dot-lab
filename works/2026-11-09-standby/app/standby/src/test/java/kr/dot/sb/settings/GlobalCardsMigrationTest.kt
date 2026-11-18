package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.AppSource
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.SlotOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Codec v17 (operator 2026-11-18): one 앱 바로가기 setting and one clock-card face, taken from the first old card. */
class GlobalCardsMigrationTest {
    private fun board(id: String, apps: String) =
        """{"id":"$id","layout":"ONE","slots":[["APPS"]],"options":{"0:0":{"apps":["$apps"],"appSource":"RECENT"}}}"""

    @Test fun widgetsLeftWinsOverBoards() {
        val json = """{"v":16,"widgetLeft":["APPS"],"widgetRight":["CLOCK"],"widgetOptions":{"0:0":{"apps":["com.w"]},"1:0":{"face":"FLIP"}},
            "boards":[${board("a", "com.a")},${board("b", "com.b")}]}"""
        val c = ConfigCodec.decode(json)
        assertEquals(listOf("com.w"), c.appsCard.apps)
        assertEquals(FaceId.FLIP, c.clockCardFace)
        assertTrue(c.widgetOptions.isEmpty())
        assertTrue(c.boards.all { it.options.isEmpty() })
    }

    @Test fun firstBoardWhenWidgetsHaveNone() {
        val json = """{"v":16,"widgetLeft":["CALENDAR"],"widgetRight":["WEATHER"],"boards":[${board("a", "com.a")},${board("b", "com.b")}]}"""
        val c = ConfigCodec.decode(json)
        assertEquals(SlotOptions(apps = listOf("com.a"), appSource = AppSource.RECENT), c.appsCard)
        assertNull(c.clockCardFace)
    }

    @Test fun globalFieldsRoundTrip() {
        val c = AppConfig(appsCard = SlotOptions(apps = listOf("com.x"), appSource = AppSource.FREQUENT, appCount = 6), clockCardFace = FaceId.ANALOG)
        val back = ConfigCodec.decode(ConfigCodec.encode(c))
        assertEquals(c.appsCard, back.appsCard)
        assertEquals(FaceId.ANALOG, back.clockCardFace)
    }

    @Test fun firstBoardClockKeepsItsInheritedFace() {
        val json = """{"v":16,"widgetLeft":[],"widgetRight":[],"boards":[
            {"id":"a","layout":"ONE","clockFace":"WALL","slots":[["WEATHER","CLOCK"]]},
            {"id":"b","layout":"ONE","slots":[["CLOCK"]],"options":{"0:0":{"face":"FLIP"}}}
        ]}"""
        val c = ConfigCodec.decode(json)
        assertEquals(FaceId.WALL, c.clockCardFace)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun firstWidgetsClockKeepsDigitalInsteadOfTakingALaterFace() {
        val json = """{"v":16,"widgetLeft":["CLOCK"],"widgetRight":["CLOCK"],
            "widgetOptions":{"1:0":{"face":"FLIP"}},"boards":[]}"""
        assertEquals(FaceId.DIGITAL, ConfigCodec.decode(json).clockCardFace)
    }

    @Test fun missingWidgetsSideKeepsTheDefaultAnalogBeforeBoardFaces() {
        val json = """{"v":16,"widgetRight":["WEATHER"],"boards":[
            {"id":"a","layout":"ONE","clockFace":"FLIP","slots":[["CLOCK"]]}
        ]}"""
        assertEquals(FaceId.ANALOG, ConfigCodec.decode(json).clockCardFace)
    }

    @Test fun currentSaveDistinguishesMissingAndNullClockFaces() {
        assertEquals(FaceId.ANALOG, ConfigCodec.decode("""{"v":17}""").clockCardFace)
        assertEquals(FaceId.ANALOG, ConfigCodec.decode("""{"v":17,"clockCardFace":"UNKNOWN"}""").clockCardFace)
        val c = AppConfig(clockCardFace = null)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }
}
