package kr.dot.wherepark.ui

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface

/** Numeral faces from assets/fonts by Style.font name; variable files get their weight through font variation settings. */
object AssetFonts {
    private val FILES = mapOf(
        "Inter Tight" to "InterTight-Variable.ttf",
        "Big Shoulders Display" to "BigShouldersDisplay-Variable.ttf",
        "IBM Plex Mono" to "IBMPlexMono-Bold.ttf",
        "Playfair Display" to "PlayfairDisplay-Variable.ttf",
        "Chakra Petch" to "ChakraPetch-Bold.ttf",
        "Doto" to "Doto-Variable.ttf",
    )
    private val cache = HashMap<String, FontFamily>()

    fun has(font: String): Boolean = font in FILES

    fun typeface(ctx: Context, font: String, weight: Int): android.graphics.Typeface {
        val file = FILES[font] ?: FILES.getValue("Inter Tight")
        val b = android.graphics.Typeface.Builder(ctx.assets, "fonts/$file")
        if (file.contains("Variable")) b.setFontVariationSettings("'wght' $weight")
        return b.build()
    }

    fun family(ctx: Context, font: String, weight: Int): FontFamily = synchronized(cache) {
        cache.getOrPut("$font/$weight") { FontFamily(Typeface(typeface(ctx, font, weight))) }
    }
}
