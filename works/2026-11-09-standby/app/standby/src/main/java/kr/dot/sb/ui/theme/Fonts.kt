@file:OptIn(ExperimentalTextApi::class)

package kr.dot.sb.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kr.dot.sb.R
import kr.dot.sb.model.CardFont
import kr.dot.sb.model.FontId
import kr.dot.sb.model.TextWeight

/**
 * Bundled OFL fonts (licences in assets/licenses). Variable fonts: one Font per requested weight, cached.
 * Words on StandBy surfaces and in settings use [ui] (Pretendard: Inter-like Latin + Hangul), so Samsung's
 * user-selected system font never leaks in. Clock digits use [clock]; widget numerals use [rounded].
 */
object Fonts {
    private val cache = HashMap<String, FontFamily>()

    private fun variable(res: Int, weight: Int, key: String): FontFamily = cache.getOrPut("$key-$weight") {
        val w = weight.coerceIn(1, 1000)
        // Compose applies variations through one shared Paint; Paint.setFontVariationSettings returns early when the
        // string equals the previous one, even for another font, so e.g. Pretendard 700 loaded right after Inter 700
        // kept Pretendard's default weight (load-order dependent). An axis no font has ("ZKEY", dropped by Android)
        // with a per-family value makes every family's string unique.
        val unique = FontVariation.Setting("ZKEY", cache.size.toFloat())
        FontFamily(Font(res, FontWeight(w.coerceIn(100, 900)), variationSettings = FontVariation.Settings(FontVariation.weight(w), unique)))
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

    /** IBM Plex Mono Medium (TERMINAL digits and card Latin): one static 500 cut. */
    val plexMono: FontFamily by lazy { FontFamily(Font(R.font.plex_mono_500, FontWeight.Medium)) }

    /**
     * Card words in [font] (plan-r13 A3). GOTHIC = [ui] at [weight]; the others are single-weight subsets of their
     * own full originals (res/font/card_*.ttf, never the clock subsets), one family each.
     */
    fun cardFamily(font: CardFont, weight: Int): FontFamily = when (font) {
        CardFont.GOTHIC -> ui(weight)
        CardFont.SERIF -> cardSerif
        CardFont.ROUND -> cardRound
        CardFont.PEN -> cardPen
    }

    private val cardSerif: FontFamily by lazy { FontFamily(Font(R.font.card_serif, FontWeight.Bold)) }
    private val cardRound: FontFamily by lazy { FontFamily(Font(R.font.card_round, FontWeight.Normal)) }
    private val cardPen: FontFamily by lazy { FontFamily(Font(R.font.card_pen, FontWeight.Normal)) }

    /** The clock family for a font choice at a weight. Stencil and Rails are Inter; their cut/outline is drawn by the clock renderer. */
    fun clock(id: FontId, weight: Int = id.defaultWeight): FontFamily = when (id) {
        FontId.BOLD, FontId.LIGHT, FontId.STENCIL, FontId.RAILS, FontId.THIN -> variable(R.font.inter, weight, "inter")
        FontId.ROUNDED, FontId.ROUNDED_HEAVY -> variable(R.font.nunito, weight, "nunito")
        FontId.SERIF, FontId.SERIF_HEAVY -> variable(R.font.source_serif, weight, "serif")
        FontId.SLAB -> variable(R.font.roboto_slab, weight, "slab")
        // Sofia Sans Extra Condensed (digits subset): the iPhone StandBy Digital face, no artificial squeeze needed
        // THEME is resolved by StandbyTheme.digits; Sofia is only the theme-less fallback
        FontId.CONDENSED, FontId.THEME -> variable(R.font.sofia_xcond, weight, "sofia")
        FontId.OUTFIT -> variable(R.font.outfit, weight, "outfit")
        FontId.BIG_SHOULDERS -> variable(R.font.big_shoulders, weight, "bigshoulders")
        FontId.FREDOKA -> variable(R.font.fredoka, weight, "fredoka")
        // static single cuts: the variation setting is ignored
        FontId.BAGEL -> variable(R.font.bagel_fat_one, weight, "bagel")
        FontId.TITAN -> variable(R.font.titan_one, weight, "titan")
        FontId.OSWALD -> variable(R.font.oswald, weight, "oswald")
        FontId.JOST -> variable(R.font.jost, weight, "jost")
        FontId.PLAYFAIR -> variable(R.font.playfair, weight, "playfair")
        FontId.PLEX_MONO -> plexMono
        FontId.BARLOW -> variable(R.font.barlow_condensed_600, weight, "barlow")
        // Hangul (한글 시계): Gowun Batang ships its 700 cut only (each cut is 8 MB); Pretendard is the UI font
        FontId.HANGUL_SERIF -> variable(R.font.gowun_batang_700, weight, "gowun")
        FontId.HANGUL_GOTHIC -> variable(R.font.pretendard, weight, "ui")
        FontId.HANGUL_ROUND -> variable(R.font.jua, weight, "jua")
        FontId.HANGUL_PEN -> variable(R.font.nanum_pen, weight, "nanumpen")
    }
}

/** Cap height / em of each card font (OS/2 sCapHeight, printed by tools/subset_fonts.py); Txt's capBox reads it. */
val CardFont.cap: Float
    get() = when (this) {
        CardFont.GOTHIC -> 0.707f // Pretendard
        CardFont.SERIF -> 0.780f
        CardFont.ROUND -> 0.711f
        CardFont.PEN -> 0.674f
    }

/** Text weight on [font]: Pretendard (the only variable card font) moves LIGHT -200 / BOLD +200, clamped 100..900. */
fun cardFontWeight(font: CardFont, tw: TextWeight, w: Int): Int = if (font != CardFont.GOTHIC) w else when (tw) {
    TextWeight.LIGHT -> (w - 200).coerceIn(100, 900)
    TextWeight.NORMAL -> w
    TextWeight.BOLD -> (w + 200).coerceIn(100, 900)
}
