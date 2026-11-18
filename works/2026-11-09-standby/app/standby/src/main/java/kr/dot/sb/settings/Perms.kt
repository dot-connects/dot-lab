package kr.dot.sb.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kr.dot.sb.data.UsageApps
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.AppSource
import kr.dot.sb.model.CardType
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.platform.StandbyDream

/**
 * The things StandBy needs the user to grant, mockup `PERMS` order (r10 checklist). Photos need none (plan-r15
 * B1). plan-r15 F3/F7: [ko] names the effect, [why] says what you get, [need] is the [PermLine] at the feature.
 */
enum class Perm(val ko: String, val why: String, val button: String, val need: String) {
    DREAM("충전하면 자동으로 켜기", "화면 보호기에서 Stand Screen을 골라 주세요.", "켜러 가기", "충전할 때 자동으로 켜지려면 허용이 필요해요"),
    NOTIFICATIONS("알림 읽기", "알림, 듣는 음악, 알람을 화면에 보여줘요. 알림 내용은 폰 밖으로 나가지 않아요.", "허용하기", "알림을 보려면 허용이 필요해요"),
    POST("알람 화면 보여주기", "알람이 울릴 때 큰 화면을 띄워요.", "허용하기", "알람 화면을 띄우려면 허용이 필요해요"),
    FULL_SCREEN("화면이 꺼져 있을 때도 바로 켜기", "화면이 꺼져 있어도 알람 화면과 무드등이 바로 켜져요.", "허용하기", "화면이 꺼져 있을 때 켜지려면 허용이 필요해요"),
    EXACT_ALARM("타이머가 제때 끝나게", "집중 타이머와 미리 밝히기가 정한 시간에 딱 맞춰 움직여요.", "허용하기", "제때 알리려면 허용이 필요해요"),
    CALENDAR("캘린더", "오늘 일정과 달력 카드에 일정을 보여줘요.", "허용하기", "일정을 보려면 허용이 필요해요"),
    BATTERY("밤새 꺼지지 않게 하기", "밤새 켜 두어도 앱이 멈추지 않아요.", "켜러 가기", "밤새 켜 두려면 허용이 필요해요"),
    USAGE("최근 쓴 앱 보기", "앱 바로가기 카드에 최근에 쓰거나 자주 쓴 앱을 보여줘요. 사용 기록은 폰 밖으로 나가지 않아요.", "허용하기", "최근 쓴 앱을 보려면 허용이 필요해요"),
    ;

    companion object {
        /**
         * plan-r15 B4/F3 step 3: what "충전하면 자동으로 켜기" needs, checked in this order (screen saver, then the
         * listener that sees the plug-in, then the two that let it open over a dark screen).
         */
        val CHARGE_START = listOf(DREAM, NOTIFICATIONS, POST, FULL_SCREEN)
    }
}

object Perms {
    /** Usage access is listed only while an app card takes its apps from usage history. */
    fun forConfig(config: AppConfig): List<Perm> {
        fun usesUsage(slots: List<List<CardType>>, options: Map<String, SlotOptions>) = slots.withIndex().any { (s, stack) ->
            stack.withIndex().any { (j, card) -> card == CardType.APPS && (options["$s:$j"]?.appSource ?: AppSource.PICKED) != AppSource.PICKED }
        }
        val usage = config.boards.any { usesUsage(it.slots, it.options) } ||
            usesUsage(listOf(config.widgetLeft, config.widgetRight), config.widgetOptions)
        return Perm.entries.filter { it != Perm.USAGE || usage }
    }

    fun granted(ctx: Context): Map<Perm, Boolean> {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return mapOf(
            Perm.DREAM to (runCatching { Settings.Secure.getString(ctx.contentResolver, "screensaver_components") }.getOrNull()
                ?.split(',')?.any { ComponentName.unflattenFromString(it.trim()) == ComponentName(ctx, StandbyDream::class.java) } == true),
            Perm.NOTIFICATIONS to (ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)),
            Perm.POST to has(Manifest.permission.POST_NOTIFICATIONS),
            Perm.CALENDAR to has(Manifest.permission.READ_CALENDAR),
            Perm.FULL_SCREEN to (nm?.canUseFullScreenIntent() == true),
            Perm.EXACT_ALARM to (am?.canScheduleExactAlarms() == true),
            Perm.BATTERY to (pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true),
            Perm.USAGE to UsageApps.granted(ctx),
        )
    }

    private fun pkg(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    /** Screen saver page: the public action first, then the AOSP activity One UI hides behind it (same as the probe app). */
    private fun dreamIntents() = listOf(
        Intent(Settings.ACTION_DREAM_SETTINGS),
        Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DreamSettingsActivity")),
    )

    /** Intent that opens the system page for [p]; null for runtime permissions (requested in-app). */
    fun intents(ctx: Context, p: Perm): List<Intent> = when (p) {
        Perm.DREAM -> dreamIntents()
        Perm.NOTIFICATIONS -> listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        Perm.USAGE -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg(ctx)), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        Perm.FULL_SCREEN -> listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(ctx)), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.EXACT_ALARM -> listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(ctx)), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.BATTERY -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.CALENDAR -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.POST -> listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
    }

    /** Asked in-app; empty = a system page. */
    fun runtimePermissions(p: Perm): List<String> = when (p) {
        Perm.POST -> listOf(Manifest.permission.POST_NOTIFICATIONS)
        Perm.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
        else -> emptyList()
    }

    /** The system page for [p]; false (after a toast) when none opens. */
    fun open(activity: Activity, p: Perm): Boolean {
        for (i in intents(activity, p)) if (runCatching { activity.startActivity(i) }.isSuccess) return true
        Toast.makeText(activity, "설정 화면이 안 열려요. 폰 설정에서 Stand Screen을 찾아 주세요.", Toast.LENGTH_LONG).show()
        return false
    }

    /** Fold or Flip: has a hinge sensor. The mood-light settings only make sense there. */
    fun isFoldable(ctx: Context): Boolean = ctx.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")

    /**
     * Fold, Flip or bar phone, for the preview's 커버|안쪽 toggle.
     * ponytail: Samsung model prefix (SM-F7xx = Flip); any other hinge phone counts as a Fold. Read the display sizes
     * (WindowManager) if a non-Samsung flip shows up.
     */
    fun phone(ctx: Context): Phone = when {
        !isFoldable(ctx) -> Phone.BAR
        android.os.Build.MODEL.startsWith("SM-F7") -> Phone.FLIP
        else -> Phone.FOLD
    }
}

/**
 * plan-r13 B1 [차례로 켜기]: one pass over the missing permissions, each opened once. [advance] is called with the step
 * that just finished (null to start); a report for any other step is a repeat (runtime callback and onResume both fire)
 * and is ignored.
 */
data class PermWalk(val asked: Set<Perm> = emptySet(), val current: Perm? = null, val done: Boolean = false) {
    fun advance(from: Perm?, missing: List<Perm>): PermWalk {
        if (done || from != current) return this
        val n = next(missing, asked) ?: return copy(current = null, done = true)
        return copy(asked = asked + n, current = n)
    }

    companion object {
        fun next(missing: List<Perm>, asked: Set<Perm>): Perm? = missing.firstOrNull { it !in asked }

        /**
         * Whether the first run (plan-r15 F3) opens: fresh install only (an update, or a config saved before r13, v <= 10,
         * never), once. [again] = 정보 › 처음 설정으로 asked for it (F8): it opens whatever the install looks like, until done.
         */
        fun starts(fresh: Boolean, done: Boolean, storedVersion: Int?, again: Boolean = false): Boolean =
            !done && (again || (fresh && (storedVersion == null || storedVersion > 10)))
    }
}
