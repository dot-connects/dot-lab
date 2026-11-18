package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Theme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** plan-r14 A1: 둥근 굵은 글씨 (FLOAT) leaves; a v11 save reads it as 디지털 everywhere. */
class FloatMigrationTest {
    private val float = ClockStyle(FontId.ROUNDED_HEAVY, 950, sizePct = 90, color = 0xFF4D9BEF)

    /** [c] as a v11 save (before FLOAT left), optionally under a removed theme name. */
    private fun v11(c: AppConfig, theme: String? = null): String = JSONObject(ConfigCodec.encode(c)).apply {
        put("v", 11)
        remove("appsCard"); remove("clockCardFace")
        // The current encoder no longer writes the per-card fields a v11 save contained.
        put("widgetOptions", JSONObject("""{"0:0":{"face":"FLOAT"}}"""))
        getJSONArray("boards").getJSONObject(1).put("options", JSONObject("""{"0:0":{"face":"FLOAT"}}"""))
        theme?.let { put("theme", it) }
    }.toString()

    private fun saved(myClocks: List<FaceId>, styles: Map<FaceId, ClockStyle>): AppConfig {
        val d = AppConfig()
        return d.copy(
            myClocks = myClocks, styles = styles,
            boards = d.boards.mapIndexed { i, b ->
                when (i) {
                    0 -> b.copy(clockFace = FaceId.FLOAT)
                    1 -> b.copy(clockFace = FaceId.ANALOG)
                    else -> b
                }
            },
            clockCardFace = FaceId.FLOAT,
        )
    }

    @Test fun floatStyleBecomesTheDigitalLookWhenTheUserHadNoDigital() {
        val c = ConfigCodec.decode(v11(saved(listOf(FaceId.ANALOG, FaceId.FLOAT), mapOf(FaceId.FLOAT to float))))
        assertEquals(listOf(FaceId.ANALOG, FaceId.DIGITAL), c.myClocks)
        assertEquals(float.copy(accentMiddle = true, overlap = true), c.styles[FaceId.DIGITAL])
        assertNull(c.styles[FaceId.FLOAT])
        assertEquals(FaceId.DIGITAL, c.boards[0].clockFace)
        assertEquals(FaceId.DIGITAL, c.clockCardFace)
        assertEquals(FaceId.ANALOG, c.boards[1].clockFace)
        val boardFirst = JSONObject(v11(saved(listOf(FaceId.ANALOG, FaceId.FLOAT), mapOf(FaceId.FLOAT to float)))).apply {
            put("widgetLeft", org.json.JSONArray())
            put("widgetRight", org.json.JSONArray())
            getJSONArray("boards").remove(0)
        }
        assertEquals(FaceId.DIGITAL, ConfigCodec.decode(boardFirst.toString()).clockCardFace)
    }

    @Test fun floatStyleIsDroppedWhenTheUserHadADigitalClock() {
        // DIGITAL in 내 시계, untouched: it keeps the theme's digital look
        val inList = ConfigCodec.decode(v11(saved(listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.FLOAT), mapOf(FaceId.FLOAT to float))))
        assertEquals(listOf(FaceId.DIGITAL, FaceId.ANALOG), inList.myClocks)
        assertEquals(emptyMap<FaceId, ClockStyle>(), inList.styles)
        assertEquals(FaceId.DIGITAL, inList.boards[0].clockFace)
        // DIGITAL styled (not in 내 시계): the user's digital edit wins
        val own = AppConfig().styles + (FaceId.DIGITAL to float.copy(font = FontId.SLAB, weight = 600))
        val styled = ConfigCodec.decode(v11(saved(listOf(FaceId.FLOAT), own + (FaceId.FLOAT to float))))
        assertEquals(listOf(FaceId.DIGITAL), styled.myClocks)
        assertEquals(own, styled.styles)
    }

    @Test fun flipThemeUserWithFloatKeepsADigitalClock() {
        // v10 FLIP theme: its DIGITAL becomes FLIP first, then FLOAT becomes DIGITAL with the float look
        val c = ConfigCodec.decode(v11(saved(listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.FLOAT), mapOf(FaceId.FLOAT to float)), theme = "FLIP"))
        assertEquals(Theme.IPHONE, c.theme)
        assertEquals(listOf(FaceId.FLIP, FaceId.ANALOG, FaceId.DIGITAL), c.myClocks)
        assertEquals(float.copy(accentMiddle = true, overlap = true), c.styles[FaceId.DIGITAL])
        assertEquals(FaceId.DIGITAL, c.boards[0].clockFace)
    }

    @Test fun migrationIsIdempotent() {
        for (s in listOf(saved(listOf(FaceId.ANALOG, FaceId.FLOAT), mapOf(FaceId.FLOAT to float)), saved(listOf(FaceId.DIGITAL, FaceId.FLOAT), mapOf(FaceId.FLOAT to float)))) {
            val once = Migrations.floatToDigital(s)
            assertEquals(once, Migrations.floatToDigital(once))
            val read = ConfigCodec.decode(v11(s))
            assertEquals(read, ConfigCodec.decode(ConfigCodec.encode(read)))
        }
    }
}
