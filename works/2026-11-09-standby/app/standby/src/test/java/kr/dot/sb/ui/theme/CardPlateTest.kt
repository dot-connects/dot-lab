package kr.dot.sb.ui.theme

import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r11 A6 (round 4/5): a card's own plate keeps its text readable, measured on the plate, not the page. */
class CardPlateTest {
    /** The card's [Ink] as CardView builds it: tokens fitted on the plate, secondary text composited over it. */
    private fun ink(th: Theme, plate: Long): Pair<Ink, androidx.compose.ui.graphics.Color> {
        val base = Themes.of(th)
        val card = base.tokens.cardPlate(plate)
        return Ink.of(0f, null, Themes.edited(base, card.tokens), over = card.over) to card.over
    }

    private fun assertReads(th: Theme, plate: Long) {
        val (i, over) = ink(th, plate)
        val name = "$th #%08X".format(plate)
        assertTrue("$name ink ${Contrast.ratio(i.ink, over)}", Contrast.ratio(i.ink, over) >= 4.5f)
        assertTrue("$name ink2 ${Contrast.ratio(i.ink2, over)}", Contrast.ratio(i.ink2, over) >= 3f)
        assertTrue("$name accent text", Contrast.ratio(i.tokens.accentText, over) >= 4.5f)
        i.tokens.roles.all.forEach { assertTrue("$name role", Contrast.ratio(it, over) >= 3f) }
    }

    @Test fun yellowOnIphone() = assertReads(Theme.IPHONE, 0xFFFFD74B)

    @Test fun darkRedOnIphone() {
        assertReads(Theme.IPHONE, 0xFFC0392B)   // luminance ≈ 0.14
        assertReads(Theme.IPHONE, 0xFFD04545)   // ≈ 0.2, right at the black/white crossover
    }

    @Test fun navyOnDaypaper() = assertReads(Theme.DAYPAPER, 0xFF1B2A4A)

    @Test fun everySwatchOnEveryPlateTheme() {
        for (th in Theme.entries) {
            val st = Themes.of(th).tokens.plateStyle
            if (st != PlateStyle.FILL && st != PlateStyle.GLASS) continue
            ClockStyle.SWATCHES.forEach { assertReads(th, it) }
        }
    }

    @Test fun skyKeepsTranslucentCards() {
        val card = Themes.SKY.tokens.cardPlate(0xFFFD6890)
        assertEquals(ThemeTokens.GLASS_MAX, card.tokens.plate.alpha, 0.002f)
        assertTrue(card.tokens.plate2.alpha < 1f)
    }

    @Test fun noOverIsTodaysInk() {
        val a = Ink.of(0.3f, null, Themes.MOODLIGHT)
        val b = Ink.of(0.3f, null, Themes.MOODLIGHT, over = null)
        assertEquals(a, b)
    }
}
