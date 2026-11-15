package kr.dot.catchpet.pet

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kr.dot.catchpet.MainActivity

/**
 * Optional (operator 11-15): tells the floating pet where the keyboard's top edge is, so it stands above it instead
 * of covering what is being typed. An overlay gets no keyboard signal of its own; the accessibility window list names
 * the keyboard window. Only window changes are read, no screen text (same as 눈금 KeyboardWatch).
 */
class KeyboardWatch : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // switched on right after our "켜러 가기": bring the pet screen back instead of leaving the user in Settings
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong(ASKED_AT, 0L) < 10 * 60_000L) {
            p.edit().remove(ASKED_AT).apply()
            runCatching {
                startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN_PET, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        PetService.keyboard(ime?.let { Rect().also(it::getBoundsInScreen).top })
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        PetService.keyboard(null)
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "catchpet"
        private const val ASKED_AT = "kbAskedAt"

        /**
         * Straight to our own switch (operator 11-15 "too fiddly"): the service's details page where the phone has
         * one (AOSP falls back to the list by itself), else the accessibility list with our row highlighted.
         */
        fun openSettings(ctx: Context) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(ASKED_AT, System.currentTimeMillis()).apply()
            val cn = ComponentName(ctx, KeyboardWatch::class.java).flattenToString()
            val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, cn)
            val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", cn)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", cn) })
            for (i in listOf(details, list)) {
                if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
            }
        }

        /** Not from Play: Android 13+ greys our switch until 앱 정보 ⋮ → 제한된 설정 허용. */
        fun sideloaded(ctx: Context): Boolean = runCatching {
            ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName != "com.android.vending"
        }.getOrDefault(true)

        fun openAppInfo(ctx: Context) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
