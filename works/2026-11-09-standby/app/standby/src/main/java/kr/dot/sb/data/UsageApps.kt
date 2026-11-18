package kr.dot.sb.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kr.dot.sb.model.AppSource

data class Usage(val pkg: String, val lastUsed: Long, val foregroundMs: Long)

object UsageApps {
    /** Rank merged usage records without touching Android services. */
    fun rank(stats: List<Usage>, source: AppSource, launchable: Set<String>, exclude: Set<String>, n: Int): List<String> {
        if (source == AppSource.PICKED) return emptyList()
        val merged = stats.filter { it.pkg in launchable && it.pkg !in exclude }.groupBy { it.pkg }
            .map { (pkg, entries) -> Usage(pkg, entries.maxOf { it.lastUsed }, entries.sumOf { it.foregroundMs }) }
        val ranked = when (source) {
            AppSource.RECENT -> merged.filter { it.lastUsed != 0L }.sortedByDescending { it.lastUsed }
            AppSource.FREQUENT -> merged.filter { it.foregroundMs != 0L }
                .sortedWith(compareByDescending<Usage> { it.foregroundMs }.thenByDescending { it.lastUsed })
            AppSource.PICKED -> emptyList()
        }
        return ranked.take(n).map { it.pkg }
    }

    fun granted(ctx: Context): Boolean = try {
        ctx.getSystemService(AppOpsManager::class.java)
            ?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, ctx.applicationInfo.uid, ctx.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: SecurityException) {
        false
    }

    /** Last 7 days (operator 2026-11-18), launchable apps only. Blocking: call off the main thread. */
    fun load(ctx: Context, source: AppSource, n: Int): List<String> {
        if (!granted(ctx)) return emptyList()
        return try {
            val now = System.currentTimeMillis()
            val stats = ctx.getSystemService(UsageStatsManager::class.java)
                ?.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7 * 24 * 60 * 60 * 1000L, now).orEmpty()
                .map { Usage(it.packageName, it.lastTimeUsed, it.totalTimeInForeground) }
            val pm = ctx.packageManager
            val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.toSet()
            val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            rank(stats, source, launchable, setOfNotNull(ctx.packageName, home, "com.android.systemui"), n)
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
