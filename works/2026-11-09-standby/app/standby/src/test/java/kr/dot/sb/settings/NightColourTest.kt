package kr.dot.sb.settings

import androidx.compose.ui.graphics.Color
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Night ignores the theme (candle colours everywhere), and the D4 safety net keeps user colours readable. */
class NightColourTest {
    private fun set(i: Ink) = listOf(i.ink, i.ink2, i.acc, i.plate, i.plate2, i.weekday, i.sun, i.green, i.blue, i.lilac, i.bg)

    @Test fun fullNightIsTheSameInEveryTheme() {
        val ref = set(Ink.of(1f, Color(0xFF76A6F3), Themes.of(Theme.IPHONE)))
        for (th in listOf(Theme.PAPER, Theme.SKY, Theme.DAYPAPER, Theme.NEON)) {
            assertEquals(th.name, ref, set(Ink.of(1f, Color(0xFFFF00AA), Themes.of(th))))
        }
    }

    @Test fun lightThemeStepsToBlackAtHalfNight() {
        val t = Themes.of(Theme.DAYPAPER)
        assertEquals(t.tokens.bg.base, Ink.of(0.49f, null, t).bg)
        assertEquals(Color.Black, Ink.of(0.5f, null, t).bg)
    }

    @Test fun paleUserColourOnDaypaperFallsBackToInk() {
        val ink = Ink.of(0f, null, Themes.of(Theme.DAYPAPER))
        val city = ink.accent(Color(0xFFF0F0F0))
        assertTrue(Contrast.ratio(city, ink.bg) >= 3f)
    }
}
