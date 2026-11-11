@file:OptIn(ExperimentalTextApi::class)

package kr.dot.sb.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kr.dot.sb.R
import kr.dot.sb.model.FontId

/**
 * Bundled OFL fonts (licences in assets/licenses). Variable fonts: one Font per requested weight, cached.
 * Words on StandBy surfaces and in settings use [ui] (Pretendard: Inter-like Latin + Hangul), so Samsung's
 * user-selected system font never leaks in. Clock digits use [clock]; widget numerals use [rounded].
 */
object Fonts {
    private val cache = HashMap<String, FontFamily>()

    private fun variable(res: Int, weight: Int, key: String): FontFamily = cache.getOrPut("$key-$weight") {
        val w = weight.coerceIn(1, 1000)
        FontFamily(Font(res, FontWeight(w.coerceIn(100, 900)), variationSettings = FontVariation.Settings(FontVariation.weight(w))))
    }

    /** Pretendard at the given weight. */
    fun ui(weight: Int = 500): FontFamily = variable(R.font.pretendard, weight, "ui")

    /**
     * M PLUS Rounded 1c (SF Pro Rounded stand-in: widget numerals "19", "74°"). Latin/digits subset, static 500/700/800;
     * other weights resolve to the nearest. No Hangul: mixed text goes through [kr.dot.sb.ui.cards.Txt] `num` spans.
     */
    val rounded: FontFamily by lazy {
        FontFamily(
            Font(R.font.mplus_rounded_500, FontWeight.Medium),
            Font(R.font.mplus_rounded_700, FontWeight.Bold),
            Font(R.font.mplus_rounded_800, FontWeight.ExtraBold),
        )
    }

    /**
     * Instrument Serif (theme B numerals and serif clock): one static 400 cut, Latin/digits subset, proportional digits.
     * No Hangul: mixed text goes through `num` spans like [rounded].
     */
    val instrument: FontFamily by lazy { FontFamily(Font(R.font.instrument_serif, FontWeight.Normal)) }
    val instrumentItalic: FontFamily by lazy { FontFamily(Font(R.font.instrument_serif_italic, FontWeight.Normal)) }

    /** IBM Plex Mono Medium (TERMINAL digits): one static 500 cut. */
    val plexMono: FontFamily by lazy { FontFamily(Font(R.font.plex_mono_500, FontWeight.Medium)) }

    /** Nunito 800/900 (theme C widget numerals): every request resolves to one of the two heavy cuts. */
    val nunitoHeavy: FontFamily by lazy {
        FontFamily(
            Font(R.font.nunito, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
            Font(R.font.nunito, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
        )
    }

    /** The clock family for a font choice at a weight. Stencil and Rails are Inter; their cut/outline is drawn by the clock renderer. */
    fun clock(id: FontId, weight: Int = id.defaultWeight): FontFamily = when (id) {
        FontId.BOLD, FontId.LIGHT, FontId.STENCIL, FontId.RAILS, FontId.THIN -> variable(R.font.inter, weight, "inter")
        FontId.ROUNDED, FontId.ROUNDED_HEAVY -> variable(R.font.nunito, weight, "nunito")
        FontId.SERIF, FontId.SERIF_HEAVY -> variable(R.font.source_serif, weight, "serif")
        FontId.SLAB -> variable(R.font.roboto_slab, weight, "slab")
        // Sofia Sans Extra Condensed (digits subset): the iPhone StandBy Digital face, no artificial squeeze needed
        // THEME is resolved by StandbyTheme.digits; Sofia is only the theme-less fallback
        FontId.CONDENSED, FontId.THEME -> variable(R.font.sofia_xcond, weight, "sofia")
    }
}
