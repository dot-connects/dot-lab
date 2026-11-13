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

/** The things StandBy needs the user to grant, in the order the Permissions screen lists them (spec §4 "Setup rows"). */
enum class Perm(val ko: String, val why: String, val button: String) {
    DREAM("화면 보호기로 선택", "충전 중 화면이 꺼질 때 스탠바이가 뜨게 해요", "선택하기"),
    NOTIFICATIONS("알림 접근 허용", "큰 알림, 음악 재생, 알람을 읽어요", "허용하기"),
    POST("알림 보내기 허용", "무드등과 알람 화면을 잠금 화면 위로 띄우는 데 필요해요", "허용하기"),
    CALENDAR("캘린더 접근 허용", "오늘 일정 카드에 써요", "허용하기"),
    PHOTOS("사진 접근 허용", "사진 화면과 추억 카드에 써요", "허용하기"),
    FULL_SCREEN("전체 화면 알림 허용", "잠금 화면 위에 알람과 무드등을 띄워요", "허용하기"),
    EXACT_ALARM("정확한 알람 허용", "알람 전에 미리 밝히기에 필요해요", "허용하기"),
    BATTERY("배터리 최적화 제외", "밤새 켜져 있어도 앱이 멈추지 않게 해요", "설정 열기"),
}

object Perms {
    fun granted(ctx: Context): Map<Perm, Boolean> {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return mapOf(
            Perm.DREAM to (runCatching { Settings.Secure.getString(ctx.contentResolver, "screensaver_components") }.getOrNull()?.contains(ctx.packageName) == true),
            Perm.NOTIFICATIONS to (ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)),
            Perm.POST to has(Manifest.permission.POST_NOTIFICATIONS),
            Perm.CALENDAR to has(Manifest.permission.READ_CALENDAR),
            Perm.PHOTOS to has(Manifest.permission.READ_MEDIA_IMAGES),
            Perm.FULL_SCREEN to (nm?.canUseFullScreenIntent() == true),
            Perm.EXACT_ALARM to (am?.canScheduleExactAlarms() == true),
            Perm.BATTERY to (pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true),
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
        Perm.FULL_SCREEN -> listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(ctx)), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.EXACT_ALARM -> listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(ctx)), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.BATTERY -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.CALENDAR, Perm.PHOTOS -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
        Perm.POST -> listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))
    }

    fun runtimePermission(p: Perm): String? = when (p) {
        Perm.POST -> Manifest.permission.POST_NOTIFICATIONS
        Perm.CALENDAR -> Manifest.permission.READ_CALENDAR
        Perm.PHOTOS -> Manifest.permission.READ_MEDIA_IMAGES
        else -> null
    }

    fun open(activity: Activity, p: Perm) {
        for (i in intents(activity, p)) if (runCatching { activity.startActivity(i) }.isSuccess) return
        Toast.makeText(activity, "설정 화면을 열 수 없어요", Toast.LENGTH_LONG).show()
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
