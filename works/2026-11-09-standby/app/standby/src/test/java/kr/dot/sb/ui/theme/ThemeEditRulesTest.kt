package kr.dot.sb.ui.theme

import androidx.compose.ui.graphics.Color
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** plan-r11 A3 (contrast warnings, relative to the theme's own default) and A4 (flow over the day). */
class ThemeEditRulesTest {
    private fun warn(th: Theme, e: ThemeEdit): List<Contrast.Warning> {
        val base = Themes.of(th).tokens
        return Contrast.warnings(base.with(e.sanitized(th)), base)
    }

    @Test fun noEditNoWarningInAnyTheme() {
        // RADIO's accent is under 3:1 by choice: relative floors never flag the theme's own look
        Theme.entries.forEach { th -> assertEquals(th.name, emptyList<Contrast.Warning>(), Contrast.warnings(Themes.of(th).tokens, Themes.of(th).tokens)) }
        Theme.entries.forEach { th -> assertEquals(th.name, emptyList<Contrast.Warning>(), warn(th, ThemeEdit(radius = 0.1f))) }
    }

    @Test fun darkInkOnADarkPageWarns() {
        val w = warn(Theme.IPHONE, ThemeEdit(ink = 0xFF303030))
        assertTrue(Contrast.Warning.INK in w); assertTrue(Contrast.Warning.INK2 in w)
    }

    @Test fun dimAccentWarns() {
        assertEquals(listOf(Contrast.Warning.ACCENT), warn(Theme.MOODLIGHT, ThemeEdit(accent = 0xFF3A2C21)))
    }

    @Test fun lightPlateUnderLightInkWarns() {
        assertTrue(Contrast.Warning.INK in warn(Theme.IPHONE, ThemeEdit(plate = 0xFFE0E0E0)))
    }

    // ---- A4 flow
    private val three = listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF)

    @Test fun flowOverTheDay() {
        assertNull(flowColor(three, LocalTime.of(5, 59)))
        assertEquals(Color(0xFFFF0000), flowColor(three, LocalTime.of(6, 0)))
        // 09:30 = halfway 06:00 → 13:00, linear in sRGB
        flowColor(three, LocalTime.of(9, 30))!!.let { assertEquals(0.5f, it.red, 0.01f); assertEquals(0.5f, it.green, 0.01f); assertEquals(0f, it.blue, 0.01f) }
        assertEquals(Color(0xFF00FF00), flowColor(three, LocalTime.of(13, 0)))
        flowColor(three, LocalTime.of(17, 0))!!.let { assertEquals(0.5f, it.green, 0.01f); assertEquals(0.5f, it.blue, 0.01f) }
        flowColor(three, LocalTime.of(20, 59))!!.let { assertTrue(it.blue > 0.99f && it.green < 0.01f) }
        assertNull(flowColor(three, LocalTime.of(21, 0)))
    }

    @Test fun twoStopFlow() {
        val two = listOf(0xFF000000, 0xFFFFFFFF)
        assertEquals(Color(0xFF000000), flowColor(two, LocalTime.of(6, 0)))
        assertEquals(0.5f, flowColor(two, LocalTime.of(13, 30))!!.red, 0.01f)
        assertNull(flowColor(listOf(0xFF000000), LocalTime.of(12, 0)))
    }
}
