package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r13 Part D: PAPER, CANDLE and FLIP are gone; every save that names them still decodes to the look it showed. */
class RemovedThemesTest {
    private val candle = Migrations.CANDLE_LOOK

    /** A stored config of version [v] with [theme] and the given raw JSON parts. */
    private fun saved(v: Int, theme: String, edits: String? = null, my: String? = null, extra: JSONObject.() -> Unit = {}): AppConfig =
        ConfigCodec.decode(JSONObject(ConfigCodec.encode(AppConfig())).apply {
            put("v", v); put("theme", theme)
            edits?.let { put("themeEdits", JSONObject(it)) }
            my?.let { put("myThemes", JSONArray(it)) }
            extra()
        }.toString())

    @Test fun namesMapToTheirReplacements() {
        assertEquals(Theme.DAYPAPER, saved(11, "PAPER").theme)
        assertEquals(Theme.MOODLIGHT, saved(11, "CANDLE").theme)
        assertEquals(Theme.IPHONE, saved(11, "FLIP").theme)
        assertEquals(Theme.IPHONE, saved(9, "FLIP").theme)
    }

    @Test fun candleBecomesMoodlightWithTheCandleEditKeptAsMyTheme() {
        val c = saved(11, "CANDLE")
        assertEquals(mapOf(Theme.MOODLIGHT to candle), c.themeEdits)
        assertEquals(listOf(MyTheme("촛불", Theme.MOODLIGHT, candle)), c.myThemes)
        assertEquals("촛불", c.customFrom)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))   // stable after the first read
    }

    @Test fun anEditOnCandleIsLaidOverTheCandleLook() {
        val c = saved(11, "CANDLE", edits = """{"CANDLE": {"accent": 4281558579}}""")
        assertEquals(candle.copy(accent = 0xFF336633), c.themeEdits[Theme.MOODLIGHT])
        assertEquals(MyTheme("촛불", Theme.MOODLIGHT, candle.copy(accent = 0xFF336633)), c.myThemes.single())
    }

    @Test fun v10CandleKeepsOneMyThemeNamedCandle() {
        // v10: the migration turns the current edit into a 내 테마 — the 촛불 one, not a second "내 테마 1"
        val c = saved(10, "CANDLE", edits = """{"CANDLE": {"radius": 0.1}}""")
        assertEquals(listOf(MyTheme("촛불", Theme.MOODLIGHT, candle.copy(radius = 0.1f))), c.myThemes)
        assertEquals("촛불", c.customFrom)
    }

    @Test fun takenCandleNameGetsTheNextName() {
        val c = saved(11, "CANDLE", my = """[{"name": "촛불", "base": "SKY", "edit": {"radius": 0.1}}]""")
        assertEquals(listOf("촛불", "내 테마 2"), c.myThemes.map { it.name })
        assertEquals("내 테마 2", c.customFrom)
    }

    @Test fun myThemesOnRemovedBasesMove() {
        val c = saved(11, "IPHONE", my = """[
            {"name": "a", "base": "PAPER", "edit": {"accent": 4281558579}},
            {"name": "b", "base": "CANDLE", "edit": {"radius": 0.1}},
            {"name": "c", "base": "FLIP", "edit": {"plate": 4280295456}}]""")
        assertEquals(
            listOf(
                MyTheme("a", Theme.DAYPAPER, ThemeEdit(accent = 0xFF336633)),
                MyTheme("b", Theme.MOODLIGHT, candle.copy(radius = 0.1f)),
                MyTheme("c", Theme.IPHONE, ThemeEdit(plate = 0xFF202020)),
            ),
            c.myThemes,
        )
        assertEquals(Theme.IPHONE, c.theme)
        assertNull(c.customFrom)
        assertTrue(c.themeEdits.isEmpty())
    }

    @Test fun paperEditTakesTheDaypaperKeyAndDaypaperOwnEditIsKept() {
        // v10 could hold both; the current theme's (PAPER) wins the key, DAYPAPER's own edit becomes a 내 테마
        val c = saved(10, "PAPER", edits = """{"PAPER": {"accent": 4281558579}, "DAYPAPER": {"radius": 0.1}}""")
        assertEquals(Theme.DAYPAPER, c.theme)
        assertEquals(mapOf(Theme.DAYPAPER to ThemeEdit(accent = 0xFF336633)), c.themeEdits)
        assertEquals(
            setOf(ThemeEdit(accent = 0xFF336633), ThemeEdit(radius = 0.1f)),
            c.myThemes.filter { it.base == Theme.DAYPAPER }.map { it.edit }.toSet(),
        )
        assertEquals(c.myThemes.first { it.edit == ThemeEdit(accent = 0xFF336633) }.name, c.customFrom)
    }

    @Test fun paperEditsAreSanitisedForTheLightPage() {
        // PAPER was dark: a dark page pick is floored like any DAYPAPER page
        val e = saved(11, "PAPER", edits = """{"PAPER": {"bg": [4278190080]}}""").themeEdits.getValue(Theme.DAYPAPER)
        assertTrue(e.bg!![0] != 0xFF000000)
    }

    @Test fun flipDigitalClocksBecomeTheFlipFace() {
        val boards = JSONArray().put(ConfigCodecBoards.json(Board("a", "a", Layout.TWO, listOf(listOf(CardType.CLOCK), listOf(CardType.CLOCK)),
            clockFace = FaceId.DIGITAL, options = mapOf("1:0" to SlotOptions(face = FaceId.ANALOG)))))
            .put(ConfigCodecBoards.json(Board("b", "b", Layout.TWO, listOf(listOf(CardType.CLOCK), listOf(CardType.CLOCK)),
                clockFace = FaceId.ANALOG, options = mapOf("0:0" to SlotOptions(face = FaceId.DIGITAL)))))
        val c = saved(11, "FLIP") {
            put("boards", boards)
            put("myClocks", JSONArray(listOf("DIGITAL", "FLIP", "ANALOG")))
            put("widgetOptions", JSONObject().put("0:0", JSONObject().put("face", "DIGITAL")))
        }
        assertEquals(listOf(FaceId.FLIP, FaceId.ANALOG), c.myClocks)
        assertEquals(FaceId.FLIP, c.boards[0].clockFace)
        assertEquals(FaceId.ANALOG, c.boards[0].options["1:0"]?.face)
        assertEquals(FaceId.ANALOG, c.boards[1].clockFace)
        assertEquals(FaceId.FLIP, c.boards[1].options["0:0"]?.face)
        assertEquals(FaceId.FLIP, c.widgetOptions["0:0"]?.face)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun otherThemesLeaveClockFacesAlone() {
        val c = saved(11, "IPHONE") { put("myClocks", JSONArray(listOf("DIGITAL", "FLIP"))) }
        assertEquals(listOf(FaceId.DIGITAL, FaceId.FLIP), c.myClocks)
    }

    @Test fun flipEditMovesToIphone() {
        val c = saved(11, "FLIP", edits = """{"FLIP": {"plate": 4280295456}}""")
        assertEquals(mapOf(Theme.IPHONE to ThemeEdit(plate = 0xFF202020)), c.themeEdits)
    }
}

/** Board JSON as the codec writes it (through a full encode, so the test never re-implements the format). */
private object ConfigCodecBoards {
    fun json(b: Board): JSONObject = JSONObject(ConfigCodec.encode(AppConfig(boards = listOf(b)))).getJSONArray("boards").getJSONObject(0)
}
