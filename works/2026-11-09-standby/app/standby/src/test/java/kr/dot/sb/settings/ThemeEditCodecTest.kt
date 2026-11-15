package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.theme.PlateStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r11 A1: theme edits, my themes and the per-card plate survive the codec; garbage is sanitised, never thrown. */
class ThemeEditCodecTest {
    private val full = ThemeEdit(
        bg = listOf(0xFF101010, 0xFF202020, 0xFF303030), glow = 0xFFAA5A2E, glowStrength = 0.7f, glowAt = 0.8f to 0.3f,
        glowShape = GlowShape.TWIN, plate = 0xFF2B2119, plateAlpha = 0.4f, plateStyle = PlateStyle.GLASS, radius = 0.12f,
        accent = 0xFFFFD74B, ink = 0xFFF0F0F0, flow = listOf(0xFFFF9C56, 0xFFAA88FE, 0xFF4D9BEF),
    )

    private fun edited(): AppConfig {
        val d = AppConfig()
        val board = d.boards[0].let { it.copy(options = it.options + ("0:0" to SlotOptions(plate = 0xFFFFD74B))) }
        return d.copy(
            boards = listOf(board) + d.boards.drop(1),
            themeEdits = mapOf(Theme.MOODLIGHT to full, Theme.PAPER to ThemeEdit(rule = 0xFFD8B46A, ink = 0xFFECE4D4)),
            myThemes = listOf(MyTheme("내 테마 1", Theme.MOODLIGHT, full), MyTheme("밤바다", Theme.SKY, ThemeEdit(bg = listOf(0xFF001020, 0xFF102040, 0xFF301830)))),
        )
    }

    @Test fun roundTripKeepsEveryField() {
        val c = edited()
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun oldSaveDecodesEmptyAndReencodesIdentically() {
        val old = ConfigCodec.encode(AppConfig())
        assertFalse(old.contains("themeEdits") || old.contains("myThemes") || old.contains("\"plate\""))
        val back = ConfigCodec.decode(old)
        assertTrue(back.themeEdits.isEmpty() && back.myThemes.isEmpty())
        assertEquals(old, ConfigCodec.encode(back))
    }

    @Test fun garbageIsSanitisedPerEntry() {
        val json = JSONObject(ConfigCodec.encode(AppConfig())).apply {
            put("themeEdits", JSONObject("""{
                "MOODLIGHT": {"glowStrength": "NaN", "plateAlpha": -3, "radius": 9, "glowAt": [2, -1], "bg": [], "flow": [1],
                              "glowShape": "HEART", "plateStyle": "RULE", "accent": 4294956875},
                "PAPER": {"plate": 4278190080, "rule": 4292392042},
                "NEON": "not an object",
                "MARS": {"ink": 4294967295},
                "SKY": {"bg": "x", "radius": "y"},
                "FLIP": {"bg": [4278190080, "oops"]}
            }"""))
            put("myThemes", org.json.JSONArray("""[{"name":"ok","base":"SKY","edit":{"radius":0.1}}, {"name":"bad","base":"PLUTO","edit":{}}, 7, {"base":"FLIP","edit":{}}]"""))
        }.toString()
        val c = ConfigCodec.decode(json)
        val m = c.themeEdits.getValue(Theme.MOODLIGHT)
        assertNull(m.glowStrength)                    // NaN dropped
        assertEquals(0.05f, m.plateAlpha)             // clamped
        assertEquals(0.2f, m.radius)
        assertEquals(1f to 0f, m.glowAt)
        assertNull(m.bg); assertNull(m.flow)          // wrong lengths
        assertNull(m.glowShape)                       // unknown name
        assertNull(m.plateStyle)                      // not offered by 무드등
        assertEquals(0xFFFFD74B, m.accent)
        assertEquals(ThemeEdit(rule = 4292392042), c.themeEdits[Theme.PAPER])   // plate never on PAPER
        assertFalse(Theme.NEON in c.themeEdits)       // malformed entry dropped alone
        assertFalse(Theme.SKY in c.themeEdits)        // became empty: removed
        assertFalse(Theme.FLIP in c.themeEdits)       // a bad stop drops the list, then the edit is empty
        assertEquals(null, ThemeEdit(bg = listOf(0xFF000000, 0xFF111111)).sanitized(Theme.SKY).bg)   // 하늘 needs its 3 stops
        assertEquals(listOf(MyTheme("ok", Theme.SKY, ThemeEdit(radius = 0.1f)), MyTheme("내 테마", Theme.FLIP, ThemeEdit())), c.myThemes)
        // a sanitised config re-encodes to itself
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun encodeSanitisesToo() {
        val c = AppConfig().copy(themeEdits = mapOf(Theme.IPHONE to ThemeEdit(radius = Float.NaN), Theme.SKY to ThemeEdit(radius = 5f)))
        val back = ConfigCodec.decode(ConfigCodec.encode(c))
        assertEquals(mapOf(Theme.SKY to ThemeEdit(radius = 0.2f)), back.themeEdits)
    }

    @Test fun editJsonRoundTrip() {
        // the editor's undo slot stores one edit as JSON (Saver)
        assertEquals(full, ConfigCodec.decodeEdit(ConfigCodec.encodeEdit(full), Theme.MOODLIGHT))
        assertEquals(ThemeEdit(), ConfigCodec.decodeEdit("garbage", Theme.MOODLIGHT))
    }
}
