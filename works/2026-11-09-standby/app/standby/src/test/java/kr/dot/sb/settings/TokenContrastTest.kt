package kr.dot.sb.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.ThemeTokens
import kr.dot.sb.ui.theme.Themes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast floors for every theme and its candle night (plan-r9 Task 3): floors only, never hex equality, so a
 * deliberate colour change passes as long as it stays readable. Gradients and glows are checked at every stop and
 * translucent plates composited over each (ThemeTokens.surfaces).
 */
class TokenContrastTest {
    /** RADIO's #E8641C shape accent is below 3:1 on its page by operator choice (2026-11-11, D12); its text accent passes. */
    private val shapeAccentWaived = setOf(Theme.RADIO)

    private fun cases(): List<Pair<String, ThemeTokens>> =
        Theme.entries.flatMap { th -> Themes.of(th).tokens.let { listOf(th.name to it, "${th.name}/night" to it.night()) } }

    private fun floor(name: String, what: String, ratio: Float, min: Float, fails: MutableList<String>) {
        if (ratio < min) fails += "$name $what ${"%.2f".format(ratio)} < $min"
    }

    @Test
    fun everyThemeMeetsItsFloors() {
        val fails = mutableListOf<String>()
        for ((name, t) in cases()) {
            val plates = t.surfaces
            val pages = t.bg.stops + (t.bg.glow?.let { g -> t.bg.stops.map { g.compositeOver(it) } } ?: emptyList())
            for (s in plates) {
                floor(name, "ink/surface", Contrast.ratio(t.ink, s), 4.5f, fails)
                floor(name, "ink2/surface", Contrast.ratio(t.ink.copy(alpha = t.ink2).compositeOver(s), s), 3f, fails)
                floor(name, "accentText/surface", Contrast.ratio(t.accentText, s), 4.5f, fails)
                t.roles.all.forEach { floor(name, "role/surface", Contrast.ratio(it, s), 3f, fails) }
            }
            if (Theme.valueOf(name.substringBefore('/')) !in shapeAccentWaived || name.endsWith("/night")) {
                pages.forEach { floor(name, "accent/page", Contrast.ratio(t.accent, it), 3f, fails) }
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test
    fun nightHasNoRed() {
        for ((name, t) in cases().filter { it.first.endsWith("/night") }) {
            (listOf(t.ink, t.accent, t.accentText) + t.roles.all).forEach { c ->
                assertTrue("$name $c is red", !(c.red > 0.6f && c.green < 0.35f && c.blue < 0.35f))
            }
        }
    }

    @Test
    fun rgbaReadsCssOrder() {
        assertTrue(ThemeTokens.rgba(0xFFFFFF24) == Color.White.copy(alpha = 0x24 / 255f))
    }
}
