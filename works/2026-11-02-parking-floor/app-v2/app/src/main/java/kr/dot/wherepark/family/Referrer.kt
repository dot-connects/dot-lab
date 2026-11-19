package kr.dot.wherepark.family

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kr.dot.wherepark.data.Prefs

/** Play Install Referrer `code=` read once on first run (sideloaded installs have none; they use the link or the pasted code). */
object Referrer {
    private const val DONE = "wp_referrer_done"
    private const val CODE = "wp_referrer_code"

    /** Onboarding (Task 10) reads this; null when there is none or it was already used. */
    fun pendingCode(ctx: Context): String? = Prefs.sp(ctx).getString(CODE, null)
    fun consume(ctx: Context) = Prefs.put(ctx, CODE, null)

    fun parse(referrer: String?): String? =
        JoinCode.fromText(Uri.parse("?" + (referrer ?: "")).getQueryParameter("code"))

    fun captureOnce(ctx: Context) {
        if (Prefs.sp(ctx).getString(DONE, null) != null) return
        val client = InstallReferrerClient.newBuilder(ctx).build()
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            Prefs.put(ctx, CODE, parse(client.installReferrer.installReferrer))
                            Prefs.put(ctx, DONE, "1")
                        } else if (responseCode == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED) {
                            Prefs.put(ctx, DONE, "1")  // no Play: never ask again; other errors retry next launch
                        }
                    } catch (_: Exception) {
                    } finally { client.endConnection() }
                }
                override fun onInstallReferrerServiceDisconnected() {}
            })
        } catch (_: Exception) {
        }
    }
}
