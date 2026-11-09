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
 * user-selected system font never leaks in. Clock digits use [clock].
 */
object Fonts {
    private val cache = HashMap<String, FontFamily>()

    private fun variable(res: Int, weight: Int, key: String): FontFamily = cache.getOrPut("$key-$weight") {
        val w = weight.coerceIn(1, 1000)
        FontFamily(Font(res, FontWeight(w.coerceIn(100, 900)), variationSettings = FontVariation.Settings(FontVariation.weight(w))))
    }

    /** Pretendard at the given weight. */
    fun ui(weight: Int = 500): FontFamily = variable(R.font.pretendard, weight, "ui")

    /** The clock family for a font choice at a weight. Stencil and Rails are Inter; their cut/outline is drawn by the clock renderer. */
    fun clock(id: FontId, weight: Int = id.defaultWeight): FontFamily = when (id) {
        FontId.BOLD, FontId.LIGHT, FontId.STENCIL, FontId.RAILS, FontId.THIN -> variable(R.font.inter, weight, "inter")
        FontId.ROUNDED, FontId.ROUNDED_HEAVY -> variable(R.font.nunito, weight, "nunito")
        FontId.SERIF, FontId.SERIF_HEAVY -> variable(R.font.source_serif, weight, "serif")
        FontId.SLAB -> variable(R.font.roboto_slab, weight, "slab")
        FontId.CONDENSED -> variable(R.font.oswald, weight, "oswald")
    }
}
