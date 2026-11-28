package kr.dot.catchpet.billing

import android.content.Context

/** Buying a pet sheet. Before release only the operator's debug token unlocks it (Play Billing comes later). */
object Purchase {
    /** Both looks (fur-ball + figurine) from one purchase, Play product "sheet_both" (spec-release 10). */
    const val PRICE_WON = 700

    /** The detail screen's make button (mockup v5.2 screen 5): free uses while there are any, else the price. [freeLeft] -1 = not heard yet. */
    fun makeLabel(freeLeft: Int, dev: Boolean): String = when {
        dev -> "펫 만들기 · 개발판 무료"
        freeLeft > 0 -> "펫 만들기 · 무료 ${freeLeft}장"
        freeLeft < 0 -> "펫 만들기 · 무료"
        else -> "펫 만들기 · ${PRICE_WON}원"
    }
    private const val KEY = "debug_purchase"

    fun locked(debugToken: String?): Boolean = debugToken.isNullOrBlank()

    // release builds never read a saved debug token (plan-release Task 6): only dev builds unlock with it
    fun token(ctx: Context): String? = if (!kr.dot.catchpet.BuildConfig.DEBUG) null else
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString(KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: kr.dot.catchpet.BuildConfig.DEBUG_PURCHASE.takeIf { it.isNotEmpty() }   // dev builds: free sheets

    /** Blank clears the token. */
    fun saveToken(ctx: Context, s: String) {
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().putString(KEY, s.trim()).apply()
    }
}
