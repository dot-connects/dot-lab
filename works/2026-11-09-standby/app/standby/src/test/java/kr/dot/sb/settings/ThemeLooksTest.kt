package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.theme.Look
import kr.dot.sb.ui.theme.StripPick
import kr.dot.sb.ui.theme.deleteMyTheme
import kr.dot.sb.ui.theme.renameMyTheme
import kr.dot.sb.ui.theme.saveMyTheme
import kr.dot.sb.ui.theme.switchFromPanel
import kr.dot.sb.ui.theme.switchLook
import kr.dot.sb.ui.theme.withThemeEdit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r13 A1/A2: the v11 migrations, one switch function, the selected strip tile, save/rename/delete of 내 테마. */
class ThemeLooksTest {
    /** A save written by r12 (v10) with [theme], raw [edits] and [my]. */
    private fun v10(theme: Theme, edits: String, my: String? = null): String =
        JSONObject(ConfigCodec.encode(AppConfig(theme = theme))).apply {
            put("v", 10)
            put("themeEdits", JSONObject(edits))
            my?.let { put("myThemes", JSONArray(it)) }
        }.toString()

    @Test fun v10EditsBecomeMyThemesAndNeonGlowBecomesTextGlow() {
        val c = ConfigCodec.decode(v10(
            Theme.NEON,
            """{"NEON": {"glowStrength": 0.7, "accent": 4294956875}, "TERMINAL": {"glowStrength": 0.3}, "MOODLIGHT": {"glowStrength": 0.4}}""",
            """[{"name": "n", "base": "NEON", "edit": {"glowStrength": 0.2}}]""",
        ))
        val neon = ThemeEdit(textGlow = 0.7f, accent = 4294956875)
        assertEquals(
            listOf(
                MyTheme("n", Theme.NEON, ThemeEdit(textGlow = 0.2f)),
                // in Theme.entries order; 무드등's glowStrength is its glow, not a halo: kept
                MyTheme("내 테마 2", Theme.MOODLIGHT, ThemeEdit(glowStrength = 0.4f)),
                MyTheme("내 테마 3", Theme.NEON, neon),
                MyTheme("내 테마 4", Theme.TERMINAL, ThemeEdit(textGlow = 0.3f)),
            ),
            c.myThemes,
        )
        // the current look stays applied, the other themes' entries are cleared
        assertEquals(mapOf(Theme.NEON to neon), c.themeEdits)
        assertEquals("내 테마 3", c.customFrom)
    }

    @Test fun v10EditEqualToAMyThemeReusesIt() {
        val c = ConfigCodec.decode(v10(Theme.IPHONE, """{"IPHONE": {"radius": 0.1}}""", """[{"name": "mine", "base": "IPHONE", "edit": {"radius": 0.1}}]"""))
        assertEquals(listOf(MyTheme("mine", Theme.IPHONE, ThemeEdit(radius = 0.1f))), c.myThemes)
        assertEquals(mapOf(Theme.IPHONE to ThemeEdit(radius = 0.1f)), c.themeEdits)
        assertEquals("mine", c.customFrom)
    }

    @Test fun v11DecodeIsANoOp() {
        val c = AppConfig(
            theme = Theme.IPHONE,
            themeEdits = mapOf(Theme.IPHONE to ThemeEdit(radius = 0.1f), Theme.NEON to ThemeEdit(glowStrength = 0.7f)),
        )
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun aFreshConfigNeverMigrates() {
        assertEquals(AppConfig(), ConfigCodec.decode(null))
        assertEquals(AppConfig(), ConfigCodec.decode(ConfigCodec.encode(AppConfig())))
        // a v10 save without edits gains nothing
        val old = JSONObject(ConfigCodec.encode(AppConfig())).put("v", 10).toString()
        assertEquals(AppConfig(), ConfigCodec.decode(old))
    }

    @Test fun switchLookClearsEveryOtherEntry() {
        val c = AppConfig(theme = Theme.IPHONE).withThemeEdit(Theme.IPHONE, ThemeEdit(radius = 0.1f)).withThemeEdit(Theme.PAPER, ThemeEdit(accent = 0xFF30CEDA))
        val paper = c.switchLook(Look.Builtin(Theme.PAPER))
        assertEquals(Theme.PAPER, paper.theme)
        assertTrue(paper.themeEdits.isEmpty())
        assertNull(paper.customFrom)
        val m = MyTheme("m", Theme.SKY, ThemeEdit(radius = 0.15f))
        val sky = c.copy(myThemes = listOf(m)).switchLook(Look.Mine(m))
        assertEquals(Theme.SKY, sky.theme)
        assertEquals(mapOf(Theme.SKY to m.edit), sky.themeEdits)
        assertEquals("m", sky.customFrom)
        // back to a built-in drops the customFrom and the edit
        val back = sky.switchLook(Look.Builtin(Theme.SKY))
        assertTrue(back.themeEdits.isEmpty()); assertNull(back.customFrom)
    }

    @Test fun stripPickThreeCases() {
        val m = MyTheme("m", Theme.IPHONE, ThemeEdit(radius = 0.1f))
        val base = AppConfig(theme = Theme.IPHONE, myThemes = listOf(MyTheme("other", Theme.PAPER, m.edit), m))
        assertEquals(Look.Builtin(Theme.IPHONE), StripPick.of(base))
        assertEquals(Look.Mine(m), StripPick.of(base.withThemeEdit(Theme.IPHONE, m.edit)))
        assertEquals(StripPick.Custom, StripPick.of(base.withThemeEdit(Theme.IPHONE, ThemeEdit(radius = 0.05f))))
    }

    @Test fun saveOverwritesTheSameNameInPlace() {
        val e = ThemeEdit(accent = 0xFF30CEDA)
        val list = listOf("a", "b", "c").map { MyTheme(it, Theme.PAPER, ThemeEdit(radius = 0.1f)) }
        val c = AppConfig(theme = Theme.IPHONE, myThemes = list).withThemeEdit(Theme.IPHONE, e)
        val over = c.saveMyTheme("b")
        assertEquals(listOf(list[0], MyTheme("b", Theme.IPHONE, e), list[2]), over.myThemes)
        assertEquals("b", over.customFrom)
        val added = c.saveMyTheme("new")
        assertEquals(list + MyTheme("new", Theme.IPHONE, e), added.myThemes)
        assertEquals("new", added.customFrom)
    }

    @Test fun renameAndDeleteFollowCustomFrom() {
        val a = MyTheme("a", Theme.PAPER, ThemeEdit(radius = 0.1f))
        val b = MyTheme("b", Theme.IPHONE, ThemeEdit(radius = 0.12f))
        val c = AppConfig(myThemes = listOf(a, b), customFrom = "b")
        assertEquals("b", c.renameMyTheme(a, "x").customFrom)
        val renamed = c.renameMyTheme(b, "z")
        assertEquals(listOf(a, b.copy(name = "z")), renamed.myThemes)
        assertEquals("z", renamed.customFrom)
        assertEquals("z", renamed.deleteMyTheme(a).customFrom)
        val gone = renamed.deleteMyTheme(b.copy(name = "z"))
        assertEquals(listOf(a), gone.myThemes)
        assertNull(gone.customFrom)
    }

    @Test fun panelSavesAnUnsavedEditFirst() {
        val e = ThemeEdit(radius = 0.1f)
        val c = AppConfig(theme = Theme.IPHONE).withThemeEdit(Theme.IPHONE, e)
        val p = c.switchFromPanel(Theme.PAPER)
        assertEquals(listOf(MyTheme("내 테마 1", Theme.IPHONE, e)), p.myThemes)
        assertEquals(Theme.PAPER, p.theme)
        assertTrue(p.themeEdits.isEmpty())
        // an edit already saved is not saved twice
        assertEquals(listOf(MyTheme("내 테마 1", Theme.IPHONE, e)), p.switchLook(Look.Mine(p.myThemes[0])).switchFromPanel(Theme.NEON).myThemes)
    }
}
