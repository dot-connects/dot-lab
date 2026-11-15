package kr.dot.sb.settings

import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.LumeView
import kr.dot.sb.model.SquareNumerals
import kr.dot.sb.ui.theme.clockStyle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** r11 Part C: the three Relief faces and their options survive a save; older saves read with the defaults. */
class ReliefCodecTest {
    private fun round(c: AppConfig) = ConfigCodec.decode(ConfigCodec.encode(c))

    @Test fun newFacesAndOptionsRoundTrip() {
        val d = AppConfig()
        val styles = mapOf(
            FaceId.RELIEF to d.clockStyle(FaceId.RELIEF).copy(faceColor = 0xFF2A2C2F, accentColor = 0xFFF08A3C, lumeView = LumeView.LUME, sweep = true, analogNumbers = AnalogNumbers.NONE),
            FaceId.RELIEF_SQUARE to d.clockStyle(FaceId.RELIEF_SQUARE).copy(lumeView = LumeView.NIGHT, squareNumerals = SquareNumerals.SANDWICH),
            FaceId.RELIEF_DIGITAL to d.clockStyle(FaceId.RELIEF_DIGITAL).copy(h24 = false, ghost = false, showInfo = false),
        )
        val c = d.copy(styles = d.styles + styles, myClocks = listOf(FaceId.RELIEF, FaceId.RELIEF_SQUARE, FaceId.RELIEF_DIGITAL))
        assertEquals(c, round(c))
        // stored by name
        val json = ConfigCodec.encode(c)
        assertTrue("RELIEF" in json && "RELIEF_SQUARE" in json && "RELIEF_DIGITAL" in json && "\"NIGHT\"" in json && "\"SANDWICH\"" in json)
    }

    @Test fun defaultsAreDayTickPhoneHoursGhostOn() {
        val s = ClockStyle.default(FaceId.RELIEF_DIGITAL)
        assertEquals(LumeView.DAY, s.lumeView)
        assertEquals(false, s.sweep)
        assertEquals(null, s.h24)
        assertEquals(true, s.ghost)
        assertEquals(SquareNumerals.THIN, ClockStyle.default(FaceId.RELIEF_SQUARE).squareNumerals)
    }

    @Test fun anOlderSaveWithoutTheFieldsReadsTheDefaults() {
        // a style saved before r11 (no lumeView/sweep/h24/ghost) for the digital face
        val c = AppConfig(styles = mapOf(FaceId.DIGITAL to ClockStyle.default(FaceId.DIGITAL).copy(color = 0xFF123456)))
        val o = JSONObject(ConfigCodec.encode(c))
        val st = o.getJSONObject("styles").getJSONObject("DIGITAL")
        listOf("lumeView", "sweep", "h24", "ghost", "squareNumerals").forEach { st.remove(it) }
        val back = ConfigCodec.decode(o.toString()).styles.getValue(FaceId.DIGITAL)
        assertEquals(LumeView.DAY, back.lumeView)
        assertEquals(false, back.sweep)
        assertEquals(null, back.h24)
        assertEquals(true, back.ghost)
        assertEquals(SquareNumerals.THIN, back.squareNumerals)
        assertEquals(0xFF123456, back.color)
    }

    @Test fun unknownValuesFallBack() {
        val c = AppConfig(styles = mapOf(FaceId.RELIEF to ClockStyle.default(FaceId.RELIEF)))
        val o = JSONObject(ConfigCodec.encode(c))
        o.getJSONObject("styles").getJSONObject("RELIEF").put("lumeView", "GLOWY").put("h24", "maybe").put("squareNumerals", "BLINKING")
        val back = ConfigCodec.decode(o.toString()).styles.getValue(FaceId.RELIEF)
        assertEquals(LumeView.DAY, back.lumeView)
        assertEquals(null, back.h24)
        assertEquals(SquareNumerals.THIN, back.squareNumerals)
        // a face id from a newer app is dropped, not a crash
        val o2 = JSONObject(ConfigCodec.encode(AppConfig())).put("myClocks", JSONArray(listOf("RELIEF_DIGITAL", "NEWER_FACE")))
        assertEquals(listOf(FaceId.RELIEF_DIGITAL), ConfigCodec.decode(o2.toString()).myClocks)
    }
}
