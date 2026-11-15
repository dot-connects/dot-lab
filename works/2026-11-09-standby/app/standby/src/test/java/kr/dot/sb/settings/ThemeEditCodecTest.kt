package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BgKind
import kr.dot.sb.model.Border
import kr.dot.sb.model.CardFont
import kr.dot.sb.model.TextWeight
import kr.dot.sb.model.Texture
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
            themeEdits = mapOf(Theme.MOODLIGHT to full, Theme.DAYPAPER to ThemeEdit(rule = 0xFFD8B46A, ink = 0xFFECE4D4)),
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
        assertEquals(PlateStyle.RULE, m.plateStyle)   // r13: every card shape on every theme
        assertEquals(0xFFFFD74B, m.accent)
        assertEquals(ThemeEdit(plate = 4278190080, rule = 4292392042), c.themeEdits[Theme.DAYPAPER])   // r13: plate on a RULE theme too; PAPER reads as DAYPAPER
        assertFalse(Theme.NEON in c.themeEdits)       // malformed entry dropped alone
        assertFalse(Theme.SKY in c.themeEdits)        // became empty: removed
        assertFalse(Theme.IPHONE in c.themeEdits)       // a bad stop drops the list, then the edit is empty
        assertEquals(listOf(0xFF000000, 0xFF111111), ThemeEdit(bg = listOf(0xFF000000, 0xFF111111)).sanitized(Theme.SKY).bg)   // r13: any stop count
        assertEquals(listOf(MyTheme("ok", Theme.SKY, ThemeEdit(radius = 0.1f)), MyTheme("내 테마", Theme.IPHONE, ThemeEdit())), c.myThemes)
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

    /** plan-r13 A2: every new field set. */
    private val r13 = ThemeEdit(
        bg = listOf(0xFFE0D8C8), bgKind = BgKind.GLOW, plate = 0xFF2B2119, rule = 0xFFD8B46A, plateStyle = PlateStyle.RULE,
        border = Border.THICK, borderColor = 0xFF808080, bgTexture = Texture.LINEN, cardTexture = Texture.FROST, textureAmt = 0.3f,
        textFont = CardFont.PEN, textWeight = TextWeight.BOLD, textGlow = 0.6f,
    )

    @Test fun r13FieldsRoundTrip() {
        val c = AppConfig(theme = Theme.DAYPAPER, themeEdits = mapOf(Theme.DAYPAPER to r13), myThemes = listOf(MyTheme("a", Theme.DAYPAPER, r13)), customFrom = "a")
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        assertEquals(r13, ConfigCodec.decodeEdit(ConfigCodec.encodeEdit(r13), Theme.DAYPAPER))
        // a customFrom naming no 내 테마 is dropped
        assertNull(ConfigCodec.decode(ConfigCodec.encode(c.copy(customFrom = "gone"))).customFrom)
    }

    @Test fun r13GarbageReadsAsNull() {
        val json = JSONObject(ConfigCodec.encode(AppConfig())).apply {
            put("themeEdits", JSONObject("""{"PAPER": {"bgKind": "STRIPES", "border": 7, "borderColor": "x", "bgTexture": "WOOD", "cardTexture": null,
                "textureAmt": "NaN", "textFont": "COMIC", "textWeight": [1], "textGlow": {"a": 1}, "accent": 4294956875}}"""))
        }.toString()
        assertEquals(mapOf(Theme.DAYPAPER to ThemeEdit(accent = 4294956875)), ConfigCodec.decode(json).themeEdits)
    }

    @Test fun r13Clamps() {
        assertEquals(ThemeEdit(textureAmt = 0.1f, textGlow = 1f), ThemeEdit(textureAmt = 0f, textGlow = 3f).sanitized(Theme.IPHONE))
        assertEquals(ThemeEdit(textureAmt = 0.8f, textGlow = 0f), ThemeEdit(textureAmt = 0.95f, textGlow = -1f).sanitized(Theme.IPHONE))
        assertEquals(ThemeEdit(), ThemeEdit(textureAmt = Float.NaN, textGlow = Float.POSITIVE_INFINITY).sanitized(Theme.IPHONE))
        for (th in Theme.entries) for (ps in PlateStyle.entries) {
            val e = ThemeEdit(plateStyle = ps, plate = 0xFF102030, rule = 0xFF405060, bg = listOf(0xFF203040))
            assertEquals("$th $ps", e.copy(bg = null), e.sanitized(th).copy(bg = null))
            assertEquals("$th keeps 1 stop", 1, e.sanitized(th).bg?.size)
        }
        // light themes keep their page floor
        assertTrue(ThemeEdit(bg = listOf(0xFF000000)).sanitized(Theme.DAYPAPER).bg!![0] != 0xFF000000)
    }
}
