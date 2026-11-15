package kr.dot.sb.settings

import androidx.compose.runtime.saveable.SaverScope
import kr.dot.sb.model.BgKind
import kr.dot.sb.model.Border
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.Texture
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.ui.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.random.Random

/** plan-r13 A4: the universal control list and its tabs, row visibility, the theme shuffle, the undo slot's Saver. */
class ThemeEditorTest {
    /** A value for [c] that differs from what [th] draws. */
    private fun sample(c: Control, th: Theme): ThemeEdit {
        val tk = Themes.of(th).tokens
        return when (c) {
            is Control.Colour -> ThemeEditor.withColour(c.field, ThemeEdit(), if (ThemeEditor.token(c.field, tk) == 0xFF123456) 0xFF654321 else 0xFF123456, tk)
            is Control.Slider -> ThemeEditor.withSlider(c.field, ThemeEdit(), if (ThemeEditor.slider(c.field, ThemeEdit(), tk) > c.range.start + 0.05f) c.range.start else c.range.endInclusive)
            is Control.Choice, is Control.Grain -> ThemeEditor.withChoice(c.field, ThemeEdit(), if (ThemeEditor.choice(c.field, tk) == 1) 2 else 1)
            is Control.Flow -> ThemeEdit(flow = listOf(0xFF102030, 0xFF405060))
            is Control.GlowPad -> ThemeEdit(glowAt = 0.95f to 0.05f)
        }.sanitized(th)
    }

    @Test fun tabsCoverEveryFieldExactlyOnce() {
        for (th in Theme.entries) {
            val cs = ThemeEditor.controls(th)
            assertEquals("$th: every field, once", EditField.entries.toList(), cs.map { it.field })
            // tab order: 배경 rows, then 카드, then 글자 (the list is shown filtered, so it never interleaves)
            assertEquals("$th tabs in order", cs.map { it.tab }.sorted(), cs.map { it.tab })
        }
        fun tab(f: EditField) = ThemeEditor.controls(Theme.IPHONE).first { it.field == f }.tab
        assertEquals(EditTab.BG, tab(EditField.BG_TEXTURE))
        assertEquals(EditTab.CARD, tab(EditField.RULE))
        assertEquals(EditTab.CARD, tab(EditField.CARD_TEXTURE))
        assertEquals(EditTab.TEXT, tab(EditField.TEXT_GLOW))
    }

    @Test fun everyVisibleControlChangesTheLook() {
        for (th in Theme.entries) {
            val tk = Themes.of(th).tokens
            for (c in ThemeEditor.controls(th).filter { ThemeEditor.visible(it, tk) }) {
                val e = sample(c, th)
                assertNotEquals("$th ${c.label}: the sample survives sanitising", ThemeEdit(), e)
                assertNotEquals("$th ${c.label} changes the tokens", tk, tk.with(e, LocalTime.of(9, 30)))
            }
        }
    }

    @Test fun rowsFollowTheDrawnKindAndShape() {
        fun shown(th: Theme, e: ThemeEdit) = ThemeEditor.controls(th).filter { ThemeEditor.visible(it, Themes.of(th).tokens.with(e)) }.map { it.field }.toSet()
        val glows = setOf(EditField.GLOW, EditField.GLOW_STRENGTH, EditField.GLOW_SHAPE, EditField.GLOW_AT)
        val grads = setOf(EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM)
        // 기본 = solid: no glow, no stops, 바탕/배경 색 shown
        shown(Theme.IPHONE, ThemeEdit()).let { assertTrue(EditField.BG in it); assertTrue(it.intersect(glows + grads).isEmpty()) }
        // any kind on any theme
        shown(Theme.IPHONE, ThemeEdit(bgKind = BgKind.GLOW)).let { assertTrue(it.containsAll(glows)); assertTrue(EditField.BG in it) }
        shown(Theme.IPHONE, ThemeEdit(bgKind = BgKind.GRADIENT)).let { assertTrue(it.containsAll(grads)); assertFalse(EditField.BG in it) }
        shown(Theme.MOODLIGHT, ThemeEdit(bgKind = BgKind.SOLID)).let { assertTrue(it.intersect(glows).isEmpty()) }
        assertTrue(shown(Theme.SKY, ThemeEdit()).containsAll(grads))
        // 구분선 색 only for 구분선; 카드 색 / 진하기 only for a filled plate; 테두리 색 once a border (or the outline) is drawn
        assertTrue(EditField.RULE in shown(Theme.DAYPAPER, ThemeEdit()))
        assertFalse(EditField.RULE in shown(Theme.IPHONE, ThemeEdit()))
        assertTrue(EditField.RULE in shown(Theme.IPHONE, ThemeEdit(plateStyle = PlateStyle.RULE)))
        assertFalse(EditField.PLATE in shown(Theme.NEON, ThemeEdit()))
        assertTrue(EditField.PLATE_ALPHA in shown(Theme.NEON, ThemeEdit(plateStyle = PlateStyle.GLASS)))
        assertFalse(EditField.BORDER_COLOR in shown(Theme.IPHONE, ThemeEdit()))
        assertTrue(EditField.BORDER_COLOR in shown(Theme.IPHONE, ThemeEdit(border = Border.THICK)))
        assertTrue(EditField.BORDER_COLOR in shown(Theme.NEON, ThemeEdit()))
    }

    @Test fun gradientStopsStartFromWhatIsDrawn() {
        val tk = Themes.IPHONE.tokens
        val e = ThemeEditor.withColour(EditField.GRAD_MID, ThemeEdit(bgKind = BgKind.GRADIENT), 0xFF223344, tk)
        val drawn = tk.with(ThemeEdit(bgKind = BgKind.GRADIENT)).bg.stops.map { with(ThemeEditor) { it.long() } }
        assertEquals(listOf(drawn[0], 0xFF223344, drawn[2]), e.bg)
        // a solid look has no stops to set
        assertEquals(ThemeEdit(), ThemeEditor.withColour(EditField.GRAD_TOP, ThemeEdit(), 0xFF223344, tk))
    }

    @Test fun shuffleFieldsFollowTheBgKind() {
        val bgFields = setOf(EditField.BG, EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM, EditField.GLOW)
        assertEquals(setOf(EditField.BG), ThemeShuffle.fields(BgKind.SOLID).toSet().intersect(bgFields))
        assertEquals(setOf(EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM), ThemeShuffle.fields(BgKind.GRADIENT).toSet().intersect(bgFields))
        assertEquals(setOf(EditField.BG, EditField.GLOW), ThemeShuffle.fields(BgKind.GLOW).toSet().intersect(bgFields))
        for (k in BgKind.entries) {
            val f = ThemeShuffle.fields(k)
            assertTrue(f.containsAll(listOf(EditField.PLATE, EditField.BORDER_COLOR, EditField.RULE, EditField.ACCENT, EditField.INK)))
            // only colour fields
            assertTrue("$k: $f", f.all { ThemeEditor.controls(Theme.IPHONE).first { c -> c.field == it } is Control.Colour })
        }
        // applied: a solid 기본 gets one page colour and no glow; a gradient keeps three stops; 무드등 gets a glow
        val solid = ThemeShuffle.next(Theme.IPHONE, Themes.IPHONE.tokens, ThemeEdit(), Random(3))
        assertEquals(1, solid.bg?.size); assertNull(solid.glow)
        val grad = ThemeShuffle.next(Theme.IPHONE, Themes.IPHONE.tokens, ThemeEdit(bgKind = BgKind.GRADIENT), Random(3))
        assertEquals(3, grad.bg?.size); assertNull(grad.glow)
        val glow = ThemeShuffle.next(Theme.MOODLIGHT, Themes.MOODLIGHT.tokens, ThemeEdit(), Random(3))
        assertEquals(1, glow.bg?.size); assertTrue(glow.glow != null)
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
        val cur = ThemeEdit(
            radius = 0.15f, plateStyle = PlateStyle.GLASS, glowShape = GlowShape.TWIN, flow = listOf(0xFF102030, 0xFF405060),
            bgKind = BgKind.GLOW, border = Border.THIN, bgTexture = Texture.LINEN, textureAmt = 0.3f,
        )
        val e = ThemeShuffle.next(Theme.MOODLIGHT, Themes.MOODLIGHT.tokens, cur, Random(7))
        assertEquals(cur.radius, e.radius); assertEquals(cur.plateStyle, e.plateStyle); assertEquals(cur.glowShape, e.glowShape); assertEquals(cur.flow, e.flow)
        assertEquals(cur.bgKind, e.bgKind); assertEquals(cur.border, e.border); assertEquals(cur.bgTexture, e.bgTexture); assertEquals(cur.textureAmt, e.textureAmt)
        assertNotEquals(cur, e)
    }

    @Test fun undoSlotSurvivesTheSaver() {
        val saver = UndoSlot.SAVER
        val scope = SaverScope { true }
        val slots = listOf(
            UndoSlot.NONE, UndoSlot(true, Theme.MOODLIGHT, null, null),
            UndoSlot(true, Theme.NEON, ThemeEdit(accent = 0xFF30CEDA, glowAt = 0.2f to 0.7f, plateStyle = PlateStyle.OUTLINE, textGlow = 0.4f), "내 테마 1"),
        )
        for (u in slots) {
            val saved = with(saver) { scope.save(u) }!!
            assertEquals(u, saver.restore(saved))
        }
        // r12 slots (an edit alone, or "-") are dropped
        assertEquals(UndoSlot.NONE, saver.restore("-"))
        assertEquals(UndoSlot.NONE, saver.restore(ConfigCodec.encodeEdit(ThemeEdit(accent = 0xFF30CEDA))))
    }
}
