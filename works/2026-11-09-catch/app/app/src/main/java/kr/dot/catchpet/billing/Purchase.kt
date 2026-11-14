package kr.dot.catchpet.billing

import android.content.Context

/** Buying a pet sheet. Before release only the operator's debug token unlocks it (Play Billing comes later). */
object Purchase {
    const val PRICE_WON = 500
    private const val KEY = "debug_purchase"

    fun locked(debugToken: String?): Boolean = debugToken.isNullOrBlank()

    fun token(ctx: Context): String? =
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString(KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: kr.dot.catchpet.BuildConfig.DEBUG_PURCHASE.takeIf { it.isNotEmpty() }   // dev builds: free sheets

    /** Blank clears the token. */
    fun saveToken(ctx: Context, s: String) {
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().putString(KEY, s.trim()).apply()
    }
}
