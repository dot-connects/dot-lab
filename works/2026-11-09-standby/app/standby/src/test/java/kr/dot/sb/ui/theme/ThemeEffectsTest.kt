package kr.dot.sb.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kr.dot.sb.model.BgKind
import kr.dot.sb.model.Border
import kr.dot.sb.model.CardFont
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.TextWeight
import kr.dot.sb.model.Texture
import kr.dot.sb.model.ThemeEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** plan-r13 A3: ThemeTokens.with for the r13 fields, and the pure rules the drawing reads. */
class ThemeEffectsTest {
    private fun near(a: Color, b: Color) {
        listOf(a.red to b.red, a.green to b.green, a.blue to b.blue, a.alpha to b.alpha).forEach { (x, y) -> assertEquals(y, x, 0.01f) }
    }

    @Test fun solidIsOneStopWithoutGlow() {
        val t = ThemeTokens.MOODLIGHT.with(ThemeEdit(bgKind = BgKind.SOLID))
        assertEquals(listOf(ThemeTokens.MOODLIGHT.bg.base), t.bg.stops)
        assertNull(t.bg.glow)
        val picked = ThemeTokens.SKY.with(ThemeEdit(bgKind = BgKind.SOLID, bg = listOf(0xFF203040)))
        assertEquals(listOf(Color(0xFF203040)), picked.bg.stops)
    }

    @Test fun gradientFillsThreeStopsFromTheBase() {
        val base = Color(0xFF203040)
        val dark = ThemeTokens.IPHONE.with(ThemeEdit(bgKind = BgKind.GRADIENT, bg = listOf(0xFF203040)))
        assertEquals(3, dark.bg.stops.size)
        near(dark.bg.stops[1], Hsl.shift(base, 0.07f))
        near(dark.bg.stops[2], Hsl.shift(base, 0.14f))
        assertNull(dark.bg.glow)
        // light themes go darker
        val light = ThemeTokens.DAYPAPER.with(ThemeEdit(bgKind = BgKind.GRADIENT))
        near(light.bg.stops[2], Hsl.shift(ThemeTokens.DAYPAPER.bg.base, -0.10f))
        // three picked stops are kept
        val three = listOf(0xFF102030, 0xFF405060, 0xFF708090)
        assertEquals(three.map { Color(it) }, ThemeTokens.IPHONE.with(ThemeEdit(bgKind = BgKind.GRADIENT, bg = three)).bg.stops)
    }

    @Test fun glowFollowsTheStrengthRule() {
        // no theme glow: the accent at 0.35 x 2 x strength
        val iphone = ThemeTokens.IPHONE.with(ThemeEdit(bgKind = BgKind.GLOW))
        near(iphone.bg.glow!!, ThemeTokens.IPHONE.accent.copy(alpha = 0.35f))
        assertEquals(0.7f, ThemeTokens.IPHONE.with(ThemeEdit(bgKind = BgKind.GLOW, glowStrength = 1f)).bg.glow!!.alpha, 0.01f)
        // the theme's glow alpha, the picked colour
        val mood = ThemeTokens.MOODLIGHT.with(ThemeEdit(bgKind = BgKind.GLOW, glow = 0xFF30CEDA, glowStrength = 0.25f))
        near(mood.bg.glow!!, Color(0xFF30CEDA).copy(alpha = ThemeTokens.MOODLIGHT.bg.glow!!.alpha * 0.5f))
        assertEquals(1, mood.bg.stops.size)
    }

    @Test fun twinUsesTheFirstColourEvenWhenTheThemeHasASecond() {
        val two = ThemeTokens.CANDLE.copy(bg = ThemeTokens.CANDLE.bg.copy(glow2 = Color(0xFFFFD27A), glow2At = Offset(.5f, 1f)))
        val t = two.with(ThemeEdit(glowShape = GlowShape.TWIN))
        assertNull(t.bg.glow2)
        assertEquals(Offset(0.5f, 1f - 1.15f), t.bg.glow2At)
        // untouched shape keeps it
        assertEquals(Color(0xFFFFD27A), two.with(ThemeEdit(accent = 0xFF30CEDA)).bg.glow2)
    }

    @Test fun clearPlateThemesGetAVisiblePlate() {
        for (tk in listOf(ThemeTokens.PAPER, ThemeTokens.NEON, ThemeTokens.TERMINAL)) {
            val fill = tk.with(ThemeEdit(plateStyle = PlateStyle.FILL))
            near(fill.plate, Hsl.shift(tk.bg.base, 0.06f).copy(alpha = 0.85f))
            assertEquals(0.45f, tk.with(ThemeEdit(plateStyle = PlateStyle.GLASS)).plate.alpha, 0.001f)
            // the edit's own colour and 진하기 win
            val own = tk.with(ThemeEdit(plateStyle = PlateStyle.FILL, plate = 0xFF336699, plateAlpha = 0.5f))
            near(own.plate, Color(0xFF336699).copy(alpha = 0.5f))
        }
        // CANDLE's faint FILL plate is its look: unchanged
        val candle = ThemeTokens.CANDLE.with(ThemeEdit(plateStyle = PlateStyle.FILL))
        assertEquals(ThemeTokens.CANDLE.plate, candle.plate)
        // RULE/OUTLINE picked keep the plate as it was
        assertEquals(ThemeTokens.NEON.plate, ThemeTokens.NEON.with(ThemeEdit(plateStyle = PlateStyle.RULE)).plate)
    }

    @Test fun textGlowFollowsTheDrawnPlateStyle() {
        // NEON's own halo, no text shadow
        assertEquals(1f, ThemeTokens.NEON.haloAmt, 0f)
        assertNull(ThemeTokens.NEON.textShadow)
        assertEquals(0f, ThemeTokens.TERMINAL.haloAmt, 0f)
        // set: halo at 2 x g on OUTLINE whatever the digit style
        assertEquals(1f, ThemeTokens.NEON.with(ThemeEdit(textGlow = 0.5f)).haloAmt, 1e-4f)
        assertEquals(0.6f, ThemeTokens.TERMINAL.with(ThemeEdit(textGlow = 0.3f)).haloAmt, 1e-4f)
        assertNull(ThemeTokens.TERMINAL.with(ThemeEdit(textGlow = 0.3f)).textShadow)
        assertEquals(0.8f, ThemeTokens.IPHONE.with(ThemeEdit(plateStyle = PlateStyle.OUTLINE, textGlow = 0.4f)).haloAmt, 1e-4f)
        // NEON on 판: a text shadow, no halo
        val neonFill = ThemeTokens.NEON.with(ThemeEdit(plateStyle = PlateStyle.FILL, textGlow = 0.5f))
        assertEquals(0f, neonFill.haloAmt, 0f)
        assertEquals(0.5f, neonFill.textShadow!!, 0f)
        assertEquals(0.4f, ThemeTokens.IPHONE.with(ThemeEdit(textGlow = 0.4f)).textShadow!!, 0f)
        // 0 = off everywhere
        assertEquals(0f, ThemeTokens.NEON.with(ThemeEdit(textGlow = 0f)).haloAmt, 0f)
        assertNull(ThemeTokens.IPHONE.with(ThemeEdit(textGlow = 0f)).textShadow)
        assertNull(ThemeTokens.IPHONE.textShadow)
    }

    @Test fun editFieldsReachTheTokens() {
        val t = ThemeTokens.IPHONE.with(
            ThemeEdit(
                bgTexture = Texture.LINEN, cardTexture = Texture.FROST, textureAmt = 0.3f, textFont = CardFont.PEN,
                textWeight = TextWeight.BOLD, border = Border.THICK, borderColor = 0xFF808080,
            ),
        )
        assertEquals(Texture.LINEN, t.bgTexture); assertEquals(Texture.FROST, t.cardTexture); assertEquals(0.3f, t.textureAmt, 0f)
        assertEquals(CardFont.PEN, t.cardFont); assertEquals(TextWeight.BOLD, t.textWeight)
        assertEquals(Border.THICK, t.border); assertEquals(Color(0xFF808080), t.borderColor)
        // an empty edit changes nothing new
        val e = ThemeTokens.IPHONE.with(ThemeEdit())
        assertEquals(Texture.NONE, e.bgTexture); assertEquals(CardFont.GOTHIC, e.cardFont); assertEquals(Border.NONE, e.border)
        assertNull(e.textGlow)
    }

    @Test fun effectsFadeWithTheDuskRampAndAreGoneAtNight() {
        assertEquals(1f, effectFade(0f), 0f)
        assertEquals(0.7f, effectFade(0.3f), 1e-6f)
        assertEquals(0f, effectFade(0.5f), 0f)
        assertEquals(0f, effectFade(1f), 0f)
    }

    @Test fun weightOffsetIsPretendardOnly() {
        assertEquals(400, cardFontWeight(CardFont.GOTHIC, TextWeight.LIGHT, 600))
        assertEquals(900, cardFontWeight(CardFont.GOTHIC, TextWeight.BOLD, 800))
        assertEquals(100, cardFontWeight(CardFont.GOTHIC, TextWeight.LIGHT, 200))
        assertEquals(600, cardFontWeight(CardFont.GOTHIC, TextWeight.NORMAL, 600))
        assertEquals(600, cardFontWeight(CardFont.SERIF, TextWeight.BOLD, 600))
    }
}
