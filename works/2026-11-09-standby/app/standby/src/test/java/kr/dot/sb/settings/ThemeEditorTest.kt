package kr.dot.sb.settings

import androidx.compose.runtime.saveable.SaverScope
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.PhotoPalette
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.ui.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.random.Random

/** plan-r11 A5: the per-theme control table, the theme shuffle, the undo slot's Saver, 사진에서 색 뽑기. */
class ThemeEditorTest {
    /** A value for [c] that differs from the theme's own. */
    private fun sample(c: Control, th: Theme): ThemeEdit {
        val tk = Themes.of(th).tokens
        return when (c) {
            is Control.Colour -> ThemeEditor.withColour(c.field, ThemeEdit(), if (ThemeEditor.token(c.field, tk) == 0xFF123456) 0xFF654321 else 0xFF123456, tk)
            is Control.Slider -> ThemeEditor.withSlider(c.field, ThemeEdit(), if (ThemeEditor.slider(c.field, ThemeEdit(), tk) > c.range.start + 0.05f) c.range.start else c.range.endInclusive)
            is Control.Shape -> ThemeEdit(glowShape = GlowShape.SUNSET)
            is Control.CardStyle -> ThemeEdit(plateStyle = c.offer.first { it != tk.plateStyle })
            is Control.Flow -> ThemeEdit(flow = listOf(0xFF102030, 0xFF405060))
            is Control.GlowPad -> ThemeEdit(glowAt = 0.95f to 0.05f)
        }.sanitized(th)
    }

    @Test fun everyThemeHasControlsAndEveryControlChangesTheLook() {
        for (th in Theme.entries) {
            val cs = ThemeEditor.controls(th)
            assertTrue("$th has controls", cs.isNotEmpty())
            assertEquals("$th: one row per field", cs.size, cs.map { it.field }.distinct().size)
            val tk = Themes.of(th).tokens
            for (c in cs) {
                val e = sample(c, th)
                assertNotEquals("$th ${c.label}: the sample survives sanitising", ThemeEdit(), e)
                assertNotEquals("$th ${c.label} changes the tokens", tk, tk.with(e, LocalTime.of(9, 30)))
            }
        }
    }

    @Test fun tableMatchesThePlan() {
        fun f(th: Theme) = ThemeEditor.controls(th).map { it.field }.toSet()
        assertEquals(setOf(EditField.INK, EditField.ACCENT, EditField.GLOW_STRENGTH), f(Theme.NEON))
        assertEquals(setOf(EditField.INK, EditField.ACCENT), f(Theme.TERMINAL))
        assertEquals(setOf(EditField.BG, EditField.INK, EditField.RULE, EditField.ACCENT), f(Theme.PAPER))
        assertTrue(EditField.PLATE !in f(Theme.PAPER))
        assertEquals(11, ThemeEditor.controls(Theme.MOODLIGHT).size)
        assertTrue(f(Theme.SKY).containsAll(listOf(EditField.SKY_TOP, EditField.SKY_MID, EditField.SKY_BOTTOM, EditField.FLOW)))
        // only these three offer 시간 따라 흐름
        assertEquals(setOf(Theme.MOODLIGHT, Theme.CANDLE, Theme.SKY), Theme.entries.filter { EditField.FLOW in f(it) }.toSet())
    }

    @Test fun shuffleNeverWarns() {
        for (th in Theme.entries) {
            val tk = Themes.of(th).tokens
            var changed = 0
            for (seed in 0 until 300) {
                val e = ThemeShuffle.next(th, tk, ThemeEdit(), Random(seed))
                assertEquals("$th seed $seed", emptyList<Contrast.Warning>(), Contrast.warnings(tk.with(e), tk))
                assertNull("$th: shuffle never draws the flow", e.flow)
                if (e != ThemeEdit()) changed++
            }
            assertTrue("$th: $changed / 300 changed", changed >= 285)
        }
    }

    @Test fun shuffleKeepsTheNonColourFields() {
        val cur = ThemeEdit(radius = 0.15f, plateStyle = PlateStyle.GLASS, glowShape = GlowShape.TWIN, flow = listOf(0xFF102030, 0xFF405060))
        val e = ThemeShuffle.next(Theme.MOODLIGHT, Themes.MOODLIGHT.tokens, cur, Random(7))
        assertEquals(cur.radius, e.radius); assertEquals(cur.plateStyle, e.plateStyle); assertEquals(cur.glowShape, e.glowShape); assertEquals(cur.flow, e.flow)
        assertNotEquals(cur, e)
    }

    @Test fun undoSlotSurvivesTheSaver() {
        val saver = UndoSlot.saver(Theme.MOODLIGHT)
        val scope = SaverScope { true }
        for (u in listOf(UndoSlot.NONE, UndoSlot(true, null), UndoSlot(true, ThemeEdit(accent = 0xFF30CEDA, glowAt = 0.2f to 0.7f, plateStyle = PlateStyle.OUTLINE)))) {
            val saved = with(saver) { scope.save(u) }!!
            assertEquals(u, saver.restore(saved))
        }
    }

    @Test fun photoFillsOnlyTheThemesFields() {
        val p = PhotoPalette.Picked(bg = 0xFF141A22, vivid = 0xFFE07020, accent = 0xFFFF9FC8, lightest = 0xFFFF9FC8)
        val term = ThemeEditor.fromPhoto(Theme.TERMINAL, ThemeEdit(), p)
        assertNull(term.bg); assertNull(term.plate); assertNull(term.glow)
        val mood = ThemeEditor.fromPhoto(Theme.MOODLIGHT, ThemeEdit(), p)
        assertEquals(listOf(0xFF141A22), mood.bg); assertEquals(0xFFE07020, mood.glow)
        // the result reads: ink ≥ 4.5 on every surface
        val tk = Themes.MOODLIGHT.tokens.with(mood)
        tk.surfaces.forEach { assertTrue(Contrast.ratio(tk.ink, it) >= 4.5f) }
        // a light theme takes the lightest colour, floored light
        val day = ThemeEditor.fromPhoto(Theme.DAYPAPER, ThemeEdit(), p.copy(lightest = 0xFF404040))
        assertTrue(Themes.DAYPAPER.tokens.with(day).light)
    }
}
