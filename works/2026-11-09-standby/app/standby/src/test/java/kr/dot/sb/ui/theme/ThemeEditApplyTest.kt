package kr.dot.sb.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** plan-r11 A2: an edit is applied in one place (ThemeTokens.with via drawnTheme), only on the theme background. */
class ThemeEditApplyTest {
    private fun cfg(th: Theme, e: ThemeEdit, bg: BoardBg = BoardBg.THEME) = AppConfig(theme = th, boardBg = bg).withThemeEdit(th, e)
    private fun long(c: Color) = (c.value shr 32).toLong() and 0xFFFFFFFFL

    @Test fun noEditIsTheBaseInstance() {
        val c = AppConfig(theme = Theme.MOODLIGHT)
        assertSame(Themes.MOODLIGHT, c.drawnTheme())
        assertSame(Themes.MOODLIGHT, c.drawnTheme(LocalTime.of(9, 30)))
    }

    @Test fun presetBackgroundIgnoresTheEdit() {
        val c = cfg(Theme.MOODLIGHT, ThemeEdit(accent = 0xFF30CEDA), BoardBg.AURORA)
        assertSame(c.baseTheme(), c.drawnTheme())
    }

    @Test fun baseThemeNeverHasTheEdit() {
        val c = cfg(Theme.DAYPAPER, ThemeEdit(bg = listOf(0xFF101010)))
        assertSame(Themes.DAYPAPER, c.baseTheme())
        assertTrue("light theme stays light", c.drawnTheme().tokens.light)
        // the page pick was floored to a light colour
        assertTrue(c.drawnTheme().tokens.bg.base.let { Hsl.toHsl(it)[2] } >= 0.59f)
    }

    @Test fun sameEditSameInstance() {
        val c = cfg(Theme.MOODLIGHT, ThemeEdit(radius = 0.15f))
        assertSame(c.drawnTheme(), c.drawnTheme())
    }

    @Test fun glowShapes() {
        val sunset = cfg(Theme.MOODLIGHT, ThemeEdit(glowShape = GlowShape.SUNSET)).drawnTheme().tokens.bg
        assertEquals(Offset(0.5f, 1.15f), sunset.glowAt); assertEquals(Offset(1.1f, 0.8f), sunset.glowSize)
        val twin = cfg(Theme.MOODLIGHT, ThemeEdit(glowShape = GlowShape.TWIN, glowAt = 0.8f to 0.25f)).drawnTheme().tokens.bg
        assertEquals(Offset(0.8f, 0.25f), twin.glowAt)
        assertEquals(0.2f, twin.glow2At!!.x, 1e-5f); assertEquals(0.75f, twin.glow2At!!.y, 1e-5f)
        val round = cfg(Theme.MOODLIGHT, ThemeEdit(glowAt = 0.7f to 0.6f)).drawnTheme().tokens.bg
        assertEquals(Offset(0.7f, 0.6f), round.glowAt); assertEquals(null, round.glow2At)
    }

    @Test fun glowColourKeepsTheThemeAlphaTimesStrength() {
        val base = Themes.MOODLIGHT.tokens.bg.glow!!
        val g = cfg(Theme.MOODLIGHT, ThemeEdit(glow = 0xFF30CEDA)).drawnTheme().tokens.bg.glow!!
        assertEquals(base.alpha, g.alpha, 0.002f)
        assertEquals(Color(0xFF30CEDA).copy(alpha = g.alpha), g)
        val strong = cfg(Theme.MOODLIGHT, ThemeEdit(glowStrength = 1f)).drawnTheme().tokens.bg.glow!!
        assertEquals((base.alpha * 2f).coerceAtMost(1f), strong.alpha, 0.002f)
    }

    @Test fun plateAlphaIsAbsoluteAndGlassCaps() {
        val t = cfg(Theme.SKY, ThemeEdit(plateAlpha = 0.8f)).drawnTheme().tokens
        assertEquals(0.8f, t.plate.alpha, 0.002f); assertEquals(0.8f, t.plate2.alpha, 0.002f)
        val g = cfg(Theme.MOODLIGHT, ThemeEdit(plateStyle = PlateStyle.GLASS)).drawnTheme().tokens
        assertEquals(PlateStyle.GLASS, g.plateStyle)
        assertTrue(g.plate.alpha <= 0.55f + 1e-3f && g.plate2.alpha <= 0.55f + 1e-3f)
    }

    @Test fun plateEditSetsBothPlates() {
        val t = cfg(Theme.IPHONE, ThemeEdit(plate = 0xFF203040)).drawnTheme().tokens
        assertEquals(Color(0xFF203040), t.plate)
        assertTrue("plate2 lighter on a dark theme", t.plate2.let { Hsl.toHsl(it)[2] } > Hsl.toHsl(t.plate)[2])
    }

    @Test fun accentEditReachesTextAndClockDefaults() {
        val c = cfg(Theme.IPHONE, ThemeEdit(accent = 0xFF4D9BEF))
        val t = c.drawnTheme()
        assertEquals(Color(0xFF4D9BEF), t.tokens.accent)
        t.tokens.surfaces.forEach { assertTrue(Contrast.ratio(t.tokens.accentText, it) >= 4.5f) }
        assertEquals(0xFF4D9BEF, c.clockStyle(FaceId.DIGITAL).color)
    }

    @Test fun inkOnlyEditKeepsAHandSetAccentText() {
        val t = cfg(Theme.CANDLE, ThemeEdit(ink = 0xFFF0E0D0)).drawnTheme().tokens
        assertEquals(Themes.CANDLE.tokens.accentText, t.accentText)
    }

    @Test fun rolesFollowTheThemesRule() {
        val neon = cfg(Theme.NEON, ThemeEdit(ink = 0xFFFFD74B, accent = 0xFF6FDC86)).drawnTheme().tokens
        assertEquals(Roles.mono(Color(0xFF6FDC86), Color(0xFFFFD74B)), neon.roles)
        // the ink-tinted progress track follows the ink
        assertEquals(Color(0xFFFFD74B).copy(alpha = Themes.NEON.tokens.plate2.alpha), neon.plate2)
        val paper = cfg(Theme.PAPER, ThemeEdit(accent = 0xFF6FDC86)).drawnTheme().tokens
        assertNotEquals(Roles.mono(paper.accent, paper.ink), paper.roles)
    }

    @Test fun neonHaloAndPaperRule() {
        assertEquals(2f, cfg(Theme.NEON, ThemeEdit(glowStrength = 1f)).drawnTheme().tokens.halo, 1e-4f)
        assertEquals(Color(0xFFFF453A), cfg(Theme.PAPER, ThemeEdit(rule = 0xFFFF453A)).drawnTheme().tokens.rule)
        assertEquals(null, Themes.PAPER.tokens.rule)
    }

    @Test fun flowChangesTheBackgroundButNotClockDefaults() {
        val c = cfg(Theme.MOODLIGHT, ThemeEdit(flow = listOf(0xFFFF9C56, 0xFFFFFFFF, 0xFF4D9BEF), accent = 0xFF30CEDA))
        val morning = c.drawnTheme(LocalTime.of(9, 30))
        val evening = c.drawnTheme(LocalTime.of(19, 0))
        assertNotEquals(long(morning.tokens.bg.glow!!), long(evening.tokens.bg.glow!!))
        assertEquals(morning.clockDefaults(FaceId.ANALOG), evening.clockDefaults(FaceId.ANALOG))
        assertEquals(c.drawnTheme().clockDefaults(FaceId.DIGITAL), morning.clockDefaults(FaceId.DIGITAL))
        // the same 10-minute bucket is the same instance
        assertSame(morning, c.drawnTheme(LocalTime.of(9, 39)))
        // SKY: the flow is the top stop
        val sky = cfg(Theme.SKY, ThemeEdit(flow = listOf(0xFFFF0000, 0xFF0000FF))).drawnTheme(LocalTime.of(6, 0)).tokens.bg
        assertEquals(Color(0xFFFF0000), sky.stops[0]); assertEquals(Themes.SKY.tokens.bg.stops[1], sky.stops[1])
    }

    @Test fun styleRebaseKeepsUserPicksAndMovesDefaults() {
        val base = AppConfig(theme = Theme.IPHONE)
        val d = base.clockStyle(FaceId.DIGITAL)
        // the user changed the font (an edit) but left the colour on the theme default; ANALOG got a pink hand
        val a = base.clockStyle(FaceId.ANALOG)
        val c = base.copy(styles = mapOf(FaceId.DIGITAL to d.copy(font = FontId.SLAB), FaceId.ANALOG to a.copy(handColor = 0xFFFD6890, font = FontId.SLAB)))
        val e = c.withThemeEdit(Theme.IPHONE, ThemeEdit(accent = 0xFF4D9BEF))
        assertEquals(0xFF4D9BEF, e.styles.getValue(FaceId.DIGITAL).color)            // untouched default follows
        assertEquals(FontId.SLAB, e.styles.getValue(FaceId.DIGITAL).font)
        assertEquals(0xFFFD6890, e.styles.getValue(FaceId.ANALOG).handColor)         // the user's pick stays
        // a pink digit colour survives a radius edit
        val pink = c.copy(styles = mapOf(FaceId.DIGITAL to d.copy(color = 0xFFFD6890)))
        assertEquals(0xFFFD6890, pink.withThemeEdit(Theme.IPHONE, ThemeEdit(radius = 0.15f)).styles.getValue(FaceId.DIGITAL).color)
        // clearing the edit moves it back; the style equal to the default again is dropped
        val back = e.withThemeEdit(Theme.IPHONE, null)
        assertEquals(d.color, back.styles.getValue(FaceId.DIGITAL).color)
    }

    @Test fun boardBgChangeRebasesToo() {
        val c = cfg(Theme.IPHONE, ThemeEdit(accent = 0xFF4D9BEF))
        val s = c.copy(styles = mapOf(FaceId.DIGITAL to c.clockStyle(FaceId.DIGITAL).copy(font = FontId.SLAB)))
        val preset = s.withBoardBg(BoardBg.AURORA)
        assertEquals(Themes.IPHONE.clockDefaults(FaceId.DIGITAL).color, preset.styles.getValue(FaceId.DIGITAL).color)
        assertEquals(0xFF4D9BEF, preset.withBoardBg(BoardBg.THEME).styles.getValue(FaceId.DIGITAL).color)
    }

    @Test fun myThemeAppliesOnTheThemeBackground() {
        val m = MyTheme("내 테마 1", Theme.SKY, ThemeEdit(radius = 0.18f))
        val c = AppConfig(theme = Theme.IPHONE, boardBg = BoardBg.MIDNIGHT).withMyThemes(listOf(m)).applyMyTheme(m)
        assertEquals(Theme.SKY, c.theme); assertEquals(BoardBg.THEME, c.boardBg)
        assertEquals(0.18f, c.drawnTheme().tokens.radiusRatio, 1e-4f)
    }

    @Test fun emptyEditClearsTheEntry() {
        val c = cfg(Theme.IPHONE, ThemeEdit(radius = 0.1f)).withThemeEdit(Theme.IPHONE, ThemeEdit())
        assertTrue(c.themeEdits.isEmpty())
    }

    @Test fun nightOfAnEditWithoutRadiusIsTheUneditedNight() {
        val e = ThemeEdit(plateStyle = PlateStyle.GLASS, glowAt = 0.8f to 0.4f, glowShape = GlowShape.SUNSET, accent = 0xFF30CEDA, ink = 0xFFF0F0F0)
        val n = cfg(Theme.MOODLIGHT, e).drawnTheme().tokens.night()
        val u = Themes.MOODLIGHT.tokens.night()
        assertEquals(u.bg, n.bg); assertEquals(u.ink, n.ink); assertEquals(u.plate, n.plate); assertEquals(u.plate2, n.plate2)
        assertEquals(u.plateStyle, n.plateStyle); assertEquals(u.radiusRatio, n.radiusRatio, 0f); assertEquals(u.roles, n.roles)
    }
}
